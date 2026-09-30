"""Run real gateway/product processes against an isolated MySQL fixture.

Requires packaged jars, JAVA_HOME, mysql client and MYSQL_USER/MYSQL_PWD.
Creates/drops only its own ec_load_<uuid> database. No application DB is used.
"""
import importlib.util
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time
from urllib.request import urlopen
from urllib.error import HTTPError, URLError
import uuid

ROOT = Path(__file__).resolve().parents[1]


def module(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / "scripts" / filename)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def wait_url(url, process, timeout=150):
    deadline = time.monotonic()+timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("Service exited before readiness; inspect drill logs")
        try:
            with urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except (HTTPError, URLError, TimeoutError):
            pass
        time.sleep(1)
    raise RuntimeError("Service readiness timed out")


def stop(process):
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=20)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=10)


def main():
    backup = module("backup", "mysql-backup.py")
    load = module("load", "load-check.py")
    java = str(Path(os.environ["JAVA_HOME"]) / "bin" / ("java.exe" if os.name == "nt" else "java"))
    jars = {s: ROOT / s / "target" / (s+"-0.0.1-SNAPSHOT.jar") for s in ("product-service", "api-gateway")}
    if not all(p.exists() for p in jars.values()):
        raise RuntimeError("Package product-service and api-gateway first")
    database = "ec_load_"+uuid.uuid4().hex
    ports = {s: port() for s in ("product", "gateway", "product_metrics", "gateway_metrics")}
    if len(set(ports.values())) != 4:
        raise RuntimeError("Port allocation collision; rerun")
    evidence = ROOT / ".deployment-evidence"
    evidence.mkdir(exist_ok=True)
    processes, streams = [], []
    env = {**os.environ, "SPRING_CLOUD_CONFIG_ENABLED":"false", "CONFIG_SERVER_IMPORT":"classpath:/application-container.properties", "SPRING_CONFIG_IMPORT":"classpath:/application-container.properties", "EUREKA_CLIENT_ENABLED":"false",
           "SPRING_CLOUD_DISCOVERY_ENABLED":"false", "MANAGEMENT_TRACING_ENABLED":"false",
           "SPRING_PROFILES_ACTIVE":"container", "SERVER_ADDRESS":"127.0.0.1", "MANAGEMENT_SERVER_ADDRESS":"127.0.0.1",
           "PRODUCT_CACHE_ENABLED":"false", "JWT_SECRET":secrets.token_hex(32), "INTERNAL_SERVICE_KEY":secrets.token_hex(32),
           "SPRING_DATASOURCE_USERNAME":os.environ["MYSQL_USER"], "SPRING_DATASOURCE_PASSWORD":os.environ["MYSQL_PWD"],
           "SPRING_DATASOURCE_URL":"jdbc:mysql://"+os.getenv("MYSQL_HOST","127.0.0.1")+":"+os.getenv("MYSQL_PORT","3306")+"/"+database,
           "PRODUCT_SERVICE_URI":"http://127.0.0.1:"+str(ports["product"]),
           "SPRING_JPA_HIBERNATE_DDL_AUTO":"validate", "SPRING_FLYWAY_ENABLED":"true", "SPRING_FLYWAY_BASELINE_ON_MIGRATE":"false"}

    def start(service, prefix):
        stream = (evidence / (prefix+"-capacity.log")).open("ab")
        streams.append(stream)
        process = subprocess.Popen([java,"-Xms128m","-Xmx384m","-jar",str(jars[service])], cwd=ROOT,
            env={**env,"SERVER_PORT":str(ports[prefix]),"MANAGEMENT_SERVER_PORT":str(ports[prefix+"_metrics"])},
            stdout=stream,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0)
        processes.append(process)
        wait_url(f"http://127.0.0.1:{ports[prefix+'_metrics']}/actuator/health/readiness",process)
        return process

    backup.sql("CREATE DATABASE "+backup.ident(database)+" CHARACTER SET utf8mb4")
    try:
        product = start("product-service","product")
        gateway = start("api-gateway","gateway")
        catalog = f"http://127.0.0.1:{ports['gateway']}/api/v1/products?page=0&size=20"
        with urlopen(catalog,timeout=10) as response:
            before = json.load(response)
        results = [load.run(catalog,c,15,1000,0.01) for c in (1,5,10)]
        scrapes = {}
        for prefix in ("product","gateway"):
            with urlopen(f"http://127.0.0.1:{ports[prefix+'_metrics']}/actuator/prometheus",timeout=5) as response:
                text = response.read().decode()
                scrapes[prefix] = "http_server_requests_seconds_count" in text
            try:
                with urlopen(f"http://127.0.0.1:{ports[prefix]}/actuator/prometheus",timeout=5) as response:
                    assert response.status != 200, "Metrics exposed on business port"
            except HTTPError as error:
                assert error.code in (401,403,404)
        stop(product)
        try:
            with urlopen(catalog,timeout=10) as response:
                raise AssertionError("Catalog unexpectedly succeeded with product service stopped")
        except HTTPError as error:
            assert error.code >= 500
        started = time.monotonic()
        start("product-service","product")
        wait_url(catalog,gateway)
        restart_seconds=time.monotonic()-started
        with urlopen(catalog,timeout=10) as response:
            after = json.load(response)
        assert before == after, "Catalog changed across service restart"
        # Real seeded application rows and Flyway history also survive dump/restore.
        import tempfile
        with tempfile.TemporaryDirectory(prefix="ecommerce-catalog-backup-") as temp:
            dump=Path(temp)/"catalog.sql"
            backup.backup(database,dump)
            restored=backup.restore_check(dump)
        report = {"scope":"local gateway -> product-service -> MySQL; seeded catalog; Redis/discovery disabled; no checkout/provider capacity claim",
                  "stages":results,"metrics_scrapes":scrapes,"restart_preserved_catalog":True,
                  "restart_seconds":restart_seconds,"catalog_restore":restored,
                  "passed":all(s["passed"] for s in results) and all(scrapes.values())}
        (evidence/"local-capacity.json").write_text(json.dumps(report,indent=2))
        print(json.dumps(report,indent=2))
        return 0 if report["passed"] else 1
    finally:
        for process in reversed(processes):
            stop(process)
        for stream in streams:
            stream.close()
        backup.sql("DROP DATABASE "+backup.ident(database))


if __name__ == "__main__":
    raise SystemExit(main())

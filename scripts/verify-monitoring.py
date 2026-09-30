"""Exercise local Prometheus -> Alertmanager -> HTTP receiver firing and resolution.

Uses synthetic metrics and shortened alert delays; production timings are covered
by promtool tests. Sends no email, SMS or external webhooks.
"""
import argparse
import importlib.util
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
from urllib.request import urlopen
import os

ROOT = Path(__file__).resolve().parents[1]


def port():
    with socket.socket() as s:
        s.bind(("127.0.0.1",0))
        return s.getsockname()[1]


class Exporter(BaseHTTPRequestHandler):
    stale = 1
    def log_message(self,*args):
        pass
    def do_GET(self):
        body=f'ecommerce_operations_stale{{queue="drill",application="drill"}} {Exporter.stale}\n'.encode()
        self.send_response(200)
        self.send_header("Content-Type","text/plain; version=0.0.4")
        self.end_headers()
        self.wfile.write(body)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("--prometheus",required=True)
    p.add_argument("--alertmanager",required=True)
    a=p.parse_args()
    spec=importlib.util.spec_from_file_location("receiver",ROOT/"infra/monitoring/alert-receiver.py")
    receiver=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(receiver)
    sink=ThreadingHTTPServer(("127.0.0.1",0),receiver.Handler)
    exporter=ThreadingHTTPServer(("127.0.0.1",0),Exporter)
    for server in (sink,exporter):
        threading.Thread(target=server.serve_forever,daemon=True).start()
    procs=[]
    try:
        with tempfile.TemporaryDirectory(prefix="ecommerce-monitoring-") as temp:
            path=Path(temp)
            pm,am=port(),port()
            alertconfig=json.loads((ROOT/"infra/monitoring/alertmanager.yml").read_text())
            alertconfig["route"].update(group_wait="1s",group_interval="1s")
            alertconfig["receivers"][0]["webhook_configs"][0]["url"]=f"http://127.0.0.1:{sink.server_port}/alerts"
            (path/"am.yml").write_text(json.dumps(alertconfig))
            rules=json.loads((ROOT/"infra/monitoring/alerts.yml").read_text())
            for rule in rules["groups"][0]["rules"]:
                rule["for"]="3s"
            (path/"rules.yml").write_text(json.dumps(rules))
            cfg={"global":{"scrape_interval":"1s","evaluation_interval":"1s"},"rule_files":[(path/"rules.yml").as_posix()],
                 "alerting":{"alertmanagers":[{"static_configs":[{"targets":[f"127.0.0.1:{am}"]}]}]},
                 "scrape_configs":[{"job_name":"drill","static_configs":[{"targets":[f"127.0.0.1:{exporter.server_port}"]}]}]}
            (path/"prom.yml").write_text(json.dumps(cfg))
            evidence=ROOT/".deployment-evidence"
            evidence.mkdir(exist_ok=True)
            with (evidence/"monitoring-drill.log").open("wb") as log:
                commands=[ [a.alertmanager,"--config.file="+str(path/"am.yml"),f"--web.listen-address=127.0.0.1:{am}","--cluster.listen-address=","--storage.path="+str(path/"am")],
                           [a.prometheus,"--config.file="+str(path/"prom.yml"),f"--web.listen-address=127.0.0.1:{pm}","--storage.tsdb.path="+str(path/"prom")] ]
                try:
                    for command in commands:
                        procs.append(subprocess.Popen(command,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW if os.name=="nt" else 0))
                    for status in ("firing","resolved"):
                        deadline=time.monotonic()+90
                        while time.monotonic()<deadline:
                            if any(p.poll() is not None for p in procs):
                                raise RuntimeError("Monitoring process exited; inspect drill log")
                            with receiver.lock:
                                seen=any(e=={"name":"EcommerceStalledWork","status":status} for e in receiver.events)
                            if seen:
                                break
                            time.sleep(1)
                        else:
                            raise RuntimeError("No delivered "+status+" alert")
                        Exporter.stale=0
                    report={"passed":True,"scope":"synthetic metric, local receiver, shortened delay; no external on-call delivery",
                            "firing_delivered":True,"resolution_delivered":True}
                    (evidence/"monitoring-drill.json").write_text(json.dumps(report,indent=2))
                    print(json.dumps(report,indent=2))
                finally:
                    for process in reversed(procs):
                        process.terminate()
                        try:process.wait(timeout=10)
                        except subprocess.TimeoutExpired:process.kill();process.wait(timeout=10)
    finally:
        for server in (sink,exporter):
            server.shutdown()
            server.server_close()


if __name__=="__main__":
    main()

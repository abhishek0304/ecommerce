"""Restore drill with synthetic data in uniquely named disposable schemas only."""
import importlib.util
import json
from pathlib import Path
import tempfile
import time
import uuid

root = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("backup", root / "scripts/mysql-backup.py")
backup = importlib.util.module_from_spec(spec)
spec.loader.exec_module(backup)
results = []
for module in ["user-service/user-service", "product-service", "cart-service", "order-service", "notification-service"]:
    database = "ec_verify_" + uuid.uuid4().hex
    backup.sql("CREATE DATABASE " + backup.ident(database) + " CHARACTER SET utf8mb4")
    try:
        for migration in sorted((root / module / "src/main/resources/db/migration").glob("V*.sql")):
            backup.sql(migration.read_text(encoding="utf-8"), database)
        backup.sql("CREATE TABLE restore_probe (id BIGINT PRIMARY KEY, amount DECIMAL(19,2), body LONGTEXT, payload VARBINARY(100));"
                   "INSERT INTO restore_probe VALUES (1,1234.50,'Unicode: नमस्ते; newline\\nsecond',X'00FF'),(2,NULL,'',NULL);", database)
        with tempfile.TemporaryDirectory(prefix="ecommerce-restore-") as temp:
            dump = Path(temp) / "fixture.sql"
            backup.backup(database, dump)
            evidence = backup.restore_check(dump)
            # Tampering must be detected before another restore is attempted.
            with dump.open("ab") as stream:
                stream.write(b"\n-- modified\n")
            try:
                backup.restore_check(dump)
                raise AssertionError("Corrupt backup was accepted")
            except RuntimeError as error:
                assert "checksum" in str(error)
            results.append({"service": module, **evidence, "tamper_rejected": True})
    finally:
        backup.sql("DROP DATABASE " + backup.ident(database))
destination = root / ".deployment-evidence/mysql-restore-drill.json"
destination.parent.mkdir(exist_ok=True)
destination.write_text(json.dumps({"epoch": int(time.time()), "scope": "synthetic isolated MySQL schemas; not production data", "results": results}, indent=2))
print(json.dumps(results, indent=2))

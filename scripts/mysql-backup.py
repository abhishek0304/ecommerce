"""MySQL logical backup and isolated restore verification. Never restores over a source DB.

Requires mysql/mysqldump on PATH and MYSQL_USER, MYSQL_PWD in the environment.
Writers must be paused for source-content comparisons, including background workers.
Artifacts contain customer data: use an encrypted, access-controlled destination.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid


def ident(value):
    if not re.fullmatch(r"[A-Za-z0-9_]+", value):
        raise ValueError("Unsafe database/table identifier")
    return "`" + value + "`"


def client(binary):
    if not os.environ.get("MYSQL_USER") or "MYSQL_PWD" not in os.environ:
        raise RuntimeError("Set MYSQL_USER and MYSQL_PWD securely in the process environment")
    return [binary, "--protocol=TCP", "--connect-timeout=5", "--host=" + os.getenv("MYSQL_HOST", "127.0.0.1"),
            "--port=" + os.getenv("MYSQL_PORT", "3306"), "--user=" + os.environ["MYSQL_USER"],
            "--default-character-set=utf8mb4"]


def sql(statement, database=None):
    command = client("mysql") + ["--batch", "--skip-column-names"]
    if database:
        ident(database)
        command += [database]
    result = subprocess.run(command, input=(statement.rstrip().rstrip(";") + ";\n").encode(), capture_output=True)
    if result.returncode:
        # Do not echo statements, data or provider details from SQL diagnostics.
        code = re.search(rb"ERROR (\d+)", result.stderr)
        raise RuntimeError("mysql failed (code " + (code[1].decode() if code else "unknown") + "); inspect database availability and permissions")
    return result.stdout.decode("utf-8")


def inventory(database):
    """Deterministic row digests without returning customer data in the report.

    Intended for bounded restore drills; rows are collected in memory per table.
    Production-scale drills should use chunked primary-key checksums instead.
    """
    tables = sql("SHOW FULL TABLES WHERE Table_type='BASE TABLE'", database)
    result = {}
    for line in tables.splitlines():
        table = line.split("\t")[0]
        columns = [line.split("\t")[0] for line in sql("SHOW COLUMNS FROM " + ident(table), database).splitlines()]
        # HEX distinguishes embedded tabs/newlines; N and H distinguish NULL from an empty value.
        expressions = ["IF(" + ident(c) + " IS NULL,'N',CONCAT('H',HEX(" + ident(c) + ")))" for c in columns]
        try:
            rows = sql("SET time_zone='+00:00'; SELECT " + ",".join(expressions) + " FROM " + ident(table), database).splitlines()
        except RuntimeError as error:
            raise RuntimeError(f"Inventory failed for {table} ({len(columns)} columns): {error}") from None
        digest = hashlib.sha256("\n".join(sorted(rows)).encode()).hexdigest()
        ddl = sql("SHOW CREATE TABLE " + ident(table), database)
        result[table] = {"rows": len(rows), "sha256": digest, "schema_sha256": hashlib.sha256(ddl.encode()).hexdigest()}
    return result


def backup(database, destination):
    ident(database)
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    manifest = destination.with_suffix(destination.suffix + ".json")
    if destination.exists() or manifest.exists():
        raise RuntimeError("Refusing to overwrite a backup or manifest")
    before = inventory(database)
    args = client("mysqldump") + ["--single-transaction", "--quick", "--routines", "--events",
        "--triggers", "--hex-blob", "--no-tablespaces", "--set-gtid-purged=OFF", "--column-statistics=0", database]
    start = time.monotonic()
    with destination.open("xb") as output:
        done = subprocess.run(args, stdout=output, stderr=subprocess.PIPE)
    if done.returncode:
        raise RuntimeError("mysqldump failed; backup is incomplete (no success manifest written)")
    after = inventory(database)
    if before != after:
        changed = [t for t in set(before) | set(after) if before.get(t) != after.get(t)]
        raise RuntimeError("Source changed during backup in tables " + ", ".join(changed) + "; pause writers and retry with a new file")
    metadata = {"database": database, "created_epoch": int(time.time()), "seconds": time.monotonic()-start,
                "sha256": hashlib.sha256(destination.read_bytes()).hexdigest(), "tables": after}
    manifest.write_text(json.dumps(metadata, indent=2), encoding="utf-8")
    return metadata


def restore_check(source):
    source = Path(source)
    meta = json.loads(source.with_suffix(source.suffix + ".json").read_text(encoding="utf-8"))
    if hashlib.sha256(source.read_bytes()).hexdigest() != meta["sha256"]:
        raise RuntimeError("Backup checksum mismatch; refusing restore")
    target = "ec_restore_" + uuid.uuid4().hex
    started = time.monotonic()
    sql("CREATE DATABASE " + ident(target) + " CHARACTER SET utf8mb4")
    try:
        # Accept only trusted dumps produced by this tool: SQL can contain executable objects.
        # Restore on an isolated MySQL instance/account for production backup drills.
        with source.open("rb") as stream:
            done = subprocess.run(client("mysql") + [target], stdin=stream, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if done.returncode:
            raise RuntimeError("Restore failed")
        actual = inventory(target)
        if actual != meta["tables"]:
            raise RuntimeError("Restored table content differs from backup manifest")
        return {"verified": True, "tables": len(actual), "rows": sum(t["rows"] for t in actual.values()),
                "restore_seconds": time.monotonic()-started, "backup_sha256": meta["sha256"]}
    finally:
        # This unique database was created successfully above; no caller-supplied drop target.
        sql("DROP DATABASE " + ident(target))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    b = commands.add_parser("backup")
    b.add_argument("--database", required=True)
    b.add_argument("--output", required=True)
    b.add_argument("--writers-paused", required=True, action="store_true")
    r = commands.add_parser("restore-check")
    r.add_argument("--backup", required=True)
    args = parser.parse_args()
    try:
        result = backup(args.database, args.output) if args.action == "backup" else restore_check(args.backup)
        print(json.dumps(result, indent=2))
    except (RuntimeError, ValueError, OSError) as error:
        raise SystemExit(str(error))

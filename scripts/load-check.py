"""Bounded read-only catalog load check; exits nonzero when thresholds fail.

No account creation, checkout, payments or external-provider calls. This measures
the selected endpoint and concurrency only, not overall commerce capacity.
"""
import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import math
from pathlib import Path
import threading
import time
from urllib.request import urlopen
from urllib.parse import urlparse


def run(url, concurrency, seconds, p95_ms, error_limit):
    stop = time.monotonic() + seconds
    samples = []
    guard = threading.Lock()

    def worker():
        local = []
        while time.monotonic() < stop:
            start = time.monotonic()
            passed = False
            try:
                with urlopen(url, timeout=5) as response:
                    payload = json.loads(response.read(2_000_000))
                    passed = response.status == 200 and isinstance(payload, dict) and isinstance(payload.get("content"), list)
            except Exception:
                pass
            local.append(((time.monotonic()-start)*1000, passed))
            time.sleep(0.02)  # bounded closed-loop workload, not an unbounded request flood
        with guard:
            samples.extend(local)

    started = time.monotonic()
    with ThreadPoolExecutor(max_workers=concurrency) as pool:
        list(pool.map(lambda _: worker(), range(concurrency)))
    elapsed = time.monotonic()-started
    latencies = sorted(s[0] for s in samples)
    errors = sum(not s[1] for s in samples)
    percentile = latencies[max(0, math.ceil(len(latencies)*0.95)-1)] if latencies else None
    ratio = errors/len(samples) if samples else 1
    return {"url": url, "concurrency": concurrency, "elapsed_seconds": elapsed, "requests": len(samples),
            "errors": errors, "error_ratio": ratio, "p95_ms": percentile, "requests_per_second": len(samples)/elapsed,
            "thresholds": {"p95_ms": p95_ms, "error_ratio": error_limit},
            "passed": bool(samples) and ratio <= error_limit and percentile <= p95_ms}


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--base-url", default="http://localhost:8081")
    p.add_argument("--concurrency", type=int, default=5)
    p.add_argument("--seconds", type=int, default=30)
    p.add_argument("--p95-ms", type=float, default=1000)
    p.add_argument("--error-limit", type=float, default=0.01)
    p.add_argument("--output", default=".deployment-evidence/catalog-load.json")
    a = p.parse_args()
    u = urlparse(a.base_url)
    if u.scheme not in ("http", "https") or not u.hostname or u.username or u.password or u.query or u.fragment:
        p.error("Use an HTTP(S) origin without credentials, query or fragment")
    if not 1 <= a.concurrency <= 50 or not 1 <= a.seconds <= 300 or a.p95_ms <= 0 or not 0 <= a.error_limit <= 1:
        p.error("Concurrency 1–50, duration 1–300 seconds and valid positive thresholds required")
    result = run(a.base_url.rstrip("/")+"/api/v1/products?page=0&size=20", a.concurrency, a.seconds, a.p95_ms, a.error_limit)
    destination = Path(a.output)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))
    raise SystemExit(0 if result["passed"] else 1)

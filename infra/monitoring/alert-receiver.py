"""Local alert delivery evidence sink. Replace with an on-call receiver in production."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from collections import deque
import json
import os
import threading

events = deque(maxlen=100)
lock = threading.Lock()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_GET(self):
        if self.path not in ("/health", "/alerts"):
            self.send_error(404)
            return
        with lock:
            body = json.dumps(list(events)).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_POST(self):
        if self.path != "/alerts":
            self.send_error(404)
            return
        try:
            size = int(self.headers.get("Content-Length", "0"))
            if not 0 < size <= 1048576:
                raise ValueError()
            payload = json.loads(self.rfile.read(size))
            received = [{"name": a.get("labels", {}).get("alertname"), "status": a.get("status")}
                        for a in payload["alerts"]]
            with lock:
                events.extend(received)
            self.send_response(200)
            self.end_headers()
        except (ValueError, KeyError, TypeError):
            self.send_error(400)


if __name__ == "__main__":
    ThreadingHTTPServer((os.getenv("ALERT_BIND", "0.0.0.0"), int(os.getenv("ALERT_PORT", "8080"))), Handler).serve_forever()

"""Local regression tests for load-check failure reporting, with no application calls."""
import importlib.util
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import threading
import time
import unittest

spec=importlib.util.spec_from_file_location("load",Path(__file__).with_name("load-check.py"))
load=importlib.util.module_from_spec(spec)
spec.loader.exec_module(load)


class Handler(BaseHTTPRequestHandler):
    status=200
    body=b'{"content":[]}'
    delay=0
    def log_message(self,*args):
        pass
    def do_GET(self):
        time.sleep(self.delay)
        self.send_response(self.status)
        self.send_header("Content-Type","application/json")
        self.end_headers()
        self.wfile.write(self.body)


class LoadChecks(unittest.TestCase):
    def setUp(self):
        Handler.status,Handler.body,Handler.delay=200,b'{"content":[]}',0
        self.server=ThreadingHTTPServer(("127.0.0.1",0),Handler)
        threading.Thread(target=self.server.serve_forever,daemon=True).start()
        self.url=f"http://127.0.0.1:{self.server.server_port}/api/v1/products"

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()

    def test_valid_catalog_passes(self):
        result=load.run(self.url,2,1,1000,0.01)
        self.assertTrue(result["passed"])
        self.assertGreater(result["requests"],0)

    def test_http_errors_fail(self):
        Handler.status=503
        self.assertFalse(load.run(self.url,1,1,1000,0.01)["passed"])

    def test_success_status_with_wrong_body_fails(self):
        Handler.body=b'{"error":"unavailable"}'
        self.assertFalse(load.run(self.url,1,1,1000,0.01)["passed"])

    def test_slow_success_fails_latency_threshold(self):
        Handler.delay=0.05
        self.assertFalse(load.run(self.url,1,1,10,0.01)["passed"])


if __name__=="__main__":
    unittest.main()

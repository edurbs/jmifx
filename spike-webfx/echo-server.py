#!/usr/bin/env python3
"""Same-origin probe server for the jmifx wasm HTTP spike.

Serves the TeaVM output directory as static files and echoes POST /echo
back as JSON, so the wasm app can POST to its own origin (no CORS).

Usage: python3 spike-webfx/echo-server.py   (from the repo root; port 8090)
"""
import json
import os
import sys
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

PORT = 8090
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                    "build", "generated", "teavm", "wasm-gc")


class ProbeHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def do_POST(self):
        if self.path != "/echo":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length).decode("utf-8")
        payload = json.dumps({"status": "ok", "echo": body,
                              "sawContentType": self.headers.get("Content-Type")}).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, fmt, *args):
        sys.stderr.write("[echo-server] " + fmt % args + "\n")


if __name__ == "__main__":
    print(f"serving {ROOT} on http://localhost:{PORT}")
    ThreadingHTTPServer(("localhost", PORT), ProbeHandler).serve_forever()

#!/usr/bin/env python3
"""Read-only single-file HTTP server. Bind to a specific RFC1918 IPv4 address."""
import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import ipaddress
import json
from pathlib import Path
from curator import LIMIT, validate


def private_address(value):
    address = ipaddress.IPv4Address(value)
    return any(address in ipaddress.IPv4Network(net) for net in
               ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16', '127.0.0.0/8'))


def handler(pool):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if not private_address(self.client_address[0]):
                self.send_error(403)
                return
            if self.path != '/pool.json':
                self.send_error(404)
                return
            try:
                with pool.open('rb') as stream:
                    raw = stream.read(LIMIT + 1)
                if len(raw) > LIMIT:
                    raise ValueError('too large')
                body = json.loads(raw)
                if body['schema_version'] != 2:
                    raise ValueError('schema')
                validate(body['entries'])
            except (OSError, ValueError, KeyError, TypeError):
                self.send_error(503)
                return
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(raw)))
            self.send_header('Cache-Control', 'no-cache')
            self.end_headers()
            self.wfile.write(raw)
    return Handler


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--bind', required=True)
    p.add_argument('--port', type=int, default=8766)
    p.add_argument('--pool', type=Path, required=True)
    args = p.parse_args()
    if not private_address(args.bind) or args.bind == '127.0.0.0':
        p.error('bind must be a specific RFC1918 or loopback IPv4 address')
    server = ThreadingHTTPServer((args.bind, args.port), handler(args.pool))
    server.serve_forever()


if __name__ == '__main__':
    main()

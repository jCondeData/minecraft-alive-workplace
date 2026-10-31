#!/usr/bin/env python3
"""Local caching Maven proxy: /h/<host>/<path> -> https://<host>/<path>, big files fetched with parallel range requests."""
import os, subprocess, threading, sys, hashlib
from concurrent.futures import ThreadPoolExecutor
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler

CACHE = '/root/mproxy-cache'
CHUNK = 32768
POOL = ThreadPoolExecutor(128)
locks = {}
glock = threading.Lock()

def curl(args, timeout):
    try:
        return subprocess.run(['curl', '-sS', '-L', '--max-time', str(timeout)] + args, capture_output=True, timeout=timeout + 5)
    except subprocess.TimeoutExpired:
        return None

def head(url):
    for _ in range(6):
        r = curl(['-I', '-o', '/dev/null', '-w', '%{http_code} %header{content-length}', url], 30)
        if r and r.returncode == 0:
            parts = r.stdout.decode().split()
            code = int(parts[0]) if parts else 0
            size = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else -1
            if code in (200, 404, 403, 410):
                return code, size
    return 0, -1

def chunk(url, s, e, path):
    for _ in range(40):
        r = curl(['-r', f'{s}-{e}', '-o', path, url], 25)
        if r and r.returncode == 0 and os.path.exists(path) and os.path.getsize(path) == e - s + 1:
            return True
    return False

def fetch(url, dest):
    code, size = head(url)
    if code != 200:
        return code
    tmp = dest + '.part'
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    if size < 0 or size <= CHUNK * 2:
        for _ in range(20):
            r = curl(['-f', '-o', tmp, url], 60)
            if r and r.returncode == 0 and (size < 0 or os.path.getsize(tmp) == size):
                os.replace(tmp, dest); return 200
        return 0
    n = (size + CHUNK - 1) // CHUNK
    d = tmp + '.d'; os.makedirs(d, exist_ok=True)
    futs = [POOL.submit(chunk, url, i * CHUNK, min(size, (i + 1) * CHUNK) - 1, f'{d}/{i}') for i in range(n)]
    if not all(f.result() for f in futs):
        return 0
    with open(tmp, 'wb') as o:
        for i in range(n):
            with open(f'{d}/{i}', 'rb') as f: o.write(f.read())
    subprocess.run(['rm', '-rf', d])
    os.replace(tmp, dest)
    print('fetched', url, size, flush=True)
    return 200

class H(BaseHTTPRequestHandler):
    def log_message(self, *a): pass
    def handle_one(self, body):
        p = self.path
        if not p.startswith('/h/'):
            self.send_response(404); self.end_headers(); return
        rest = p[3:]
        url = 'https://' + rest
        dest = os.path.join(CACHE, rest.split('?')[0])
        neg = dest + '.404'
        if not os.path.exists(dest) and not os.path.exists(neg):
            with glock: lk = locks.setdefault(dest, threading.Lock())
            with lk:
                if not os.path.exists(dest) and not os.path.exists(neg):
                    code = fetch(url, dest)
                    if code in (404, 403, 410):
                        os.makedirs(os.path.dirname(neg), exist_ok=True); open(neg, 'w').close()
                    elif code != 200:
                        self.send_response(502); self.end_headers(); return
        if os.path.exists(neg):
            self.send_response(404); self.end_headers(); return
        size = os.path.getsize(dest)
        self.send_response(200)
        self.send_header('Content-Length', str(size)); self.end_headers()
        if body:
            with open(dest, 'rb') as f: self.wfile.write(f.read())
    def do_GET(self): self.handle_one(True)
    def do_HEAD(self): self.handle_one(False)

ThreadingHTTPServer(('127.0.0.1', 8765), H).serve_forever()

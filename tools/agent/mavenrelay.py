#!/usr/bin/env python3
"""A local Maven relay for cloud containers where Gradle can't download (lanes 2026-10-28: two runs lost to it).

In the cloud sessions Maven Central answers Gradle with HTTP 429 and maven.fabricmc.net trickles at a few KB/s (or
stalls: mercury-0.4.3.jar never finished), so `./gradlew` can't even configure. This serves
http://127.0.0.1:8899/<host>/<path> from https://<host>/<path>, fetched with curl (which resumes a stalled transfer):

  - Maven Central comes from Google's mirror of it (no 429);
  - a jar from maven.fabricmc.net comes from SpongePowered's proxy of it (fast), but only when its sha1 equals the
    .sha1 fabricmc publishes: Sponge holds older builds under some fabric-api version numbers, and those break the
    game at start (a fabric-resource-loader mixin fails on Language); otherwise from fabricmc itself, resuming.
    Poms and everything else always come from fabricmc: Sponge's stale poms name other module versions (its
    fabric-loot-api-v2 3.0.15 pulls fabric-resource-loader-v0 2.0.0, the same mixin failure);
  - every file is cached under ~/.cache/mavenrelay, a 404 is remembered.

    python3 tools/agent/mavenrelay.py --install   # writes ~/.gradle/init.d/mavenrelay.gradle (every https Maven repo
                                                  # through the relay) and long HTTP timeouts in ~/.gradle/gradle.properties
    nohup python3 tools/agent/mavenrelay.py > /tmp/mavenrelay.log 2>&1 &
    ./gradlew --max-workers=1 build               # as usual; the first run takes ~10 minutes to fill the cache

Container-only: nothing here touches the repository's build files. `--uninstall` removes the init script.
"""
import hashlib
import http.server
import os
import pathlib
import subprocess
import sys
import threading
import time

PORT = 8899
CACHE = pathlib.Path.home() / ".cache" / "mavenrelay"
CENTRAL = "maven-central.storage-download.googleapis.com"
MIRRORS = {"repo.maven.apache.org": CENTRAL, "repo1.maven.org": CENTRAL}
FABRIC = "maven.fabricmc.net"
SPONGE = "repo.spongepowered.org/repository/maven-public"
INIT = pathlib.Path.home() / ".gradle" / "init.d" / "mavenrelay.gradle"
PROPS = pathlib.Path.home() / ".gradle" / "gradle.properties"
INIT_TEXT = """// Container-only (tools/agent/mavenrelay.py): every https Maven repository through the local relay.
def relay = { RepositoryHandler repos ->
    repos.withType(MavenArtifactRepository).configureEach { r ->
        def u = r.url.toString()
        if (u.startsWith('https://')) { r.url = 'http://127.0.0.1:%d/' + u.substring(8); r.allowInsecureProtocol = true }
    }
}
beforeSettings { s -> relay(s.pluginManagement.repositories) }
settingsEvaluated { s -> relay(s.pluginManagement.repositories); relay(s.dependencyResolutionManagement.repositories) }
allprojects { p -> relay(p.repositories); p.buildscript { relay(repositories) } }
""" % PORT
TIMEOUTS = ["systemProp.org.gradle.internal.http.socketTimeout=3600000",
            "systemProp.org.gradle.internal.http.connectionTimeout=120000"]

_locks = {}
_guard = threading.Lock()


def curl_one(base, path, dest):
    """Fetches https://<base><path> into dest, resuming a stalled transfer for up to 90 minutes; dest or None."""
    tmp = dest.with_name(dest.name + ".part")
    deadline = time.time() + 5400
    while time.time() < deadline:
        r = subprocess.run(["curl", "-sS", "-L", "-C", "-", "--speed-limit", "20000", "--speed-time", "10",
                            "--max-time", "300", "-o", str(tmp), "-w", "%{http_code}", f"https://{base}{path}"],
                           capture_output=True, text=True)
        code = r.stdout.strip()
        if (r.returncode == 0 and code in ("200", "206")) or code == "416":
            tmp.replace(dest)
            return dest
        if code in ("404", "403", "410"):
            tmp.unlink(missing_ok=True)
            return None
        time.sleep(1)
    return None


def published_sha1(path):
    r = subprocess.run(["curl", "-sS", "--max-time", "60", "-w", "\n%{http_code}", f"https://{FABRIC}{path}.sha1"],
                       capture_output=True, text=True)
    lines = r.stdout.strip().split("\n")
    return lines[0].strip()[:40] if len(lines) > 1 and lines[-1] == "200" else None


def fetch(host, path):
    dest = CACHE / host / path.lstrip("/")
    missing = dest.with_name(dest.name + ".404")
    if dest.exists():
        return dest
    if missing.exists():
        return None
    with _guard:
        lock = _locks.setdefault(str(dest), threading.Lock())
    with lock:
        if dest.exists():
            return dest
        dest.parent.mkdir(parents=True, exist_ok=True)
        if host == FABRIC and path.endswith(".jar"):
            want = published_sha1(path)
            if want and curl_one(SPONGE, path, dest):
                if hashlib.sha1(dest.read_bytes()).hexdigest() == want:
                    return dest
                dest.unlink()
        if curl_one(MIRRORS.get(host, host), path, dest):
            return dest
        missing.touch()
        return None


class Handler(http.server.BaseHTTPRequestHandler):
    def serve(self, body):
        parts = self.path.split("/", 2)
        found = fetch(parts[1], "/" + parts[2]) if len(parts) == 3 and parts[1] else None
        if not found:
            self.send_response(404)
            self.send_header("Content-Length", "0")
            self.end_headers()
            return
        self.send_response(200)
        self.send_header("Content-Length", str(found.stat().st_size))
        self.end_headers()
        if body:
            with open(found, "rb") as f:
                while chunk := f.read(1 << 16):
                    self.wfile.write(chunk)

    def do_GET(self):
        self.serve(True)

    def do_HEAD(self):
        self.serve(False)

    def log_message(self, *args):
        pass


def install():
    INIT.parent.mkdir(parents=True, exist_ok=True)
    INIT.write_text(INIT_TEXT)
    have = PROPS.read_text() if PROPS.exists() else ""
    add = [t for t in TIMEOUTS if t.split("=")[0] not in have]
    if add:
        PROPS.write_text(have + ("" if have.endswith("\n") or not have else "\n") + "\n".join(add) + "\n")
    print(f"wrote {INIT}; start the relay: nohup python3 {sys.argv[0]} > /tmp/mavenrelay.log 2>&1 &")


if __name__ == "__main__":
    if "--install" in sys.argv:
        install()
    elif "--uninstall" in sys.argv:
        INIT.unlink(missing_ok=True)
    else:
        CACHE.mkdir(parents=True, exist_ok=True)
        http.server.ThreadingHTTPServer(("127.0.0.1", PORT), Handler).serve_forever()

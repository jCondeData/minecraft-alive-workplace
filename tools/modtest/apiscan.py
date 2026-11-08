#!/usr/bin/env python3
"""Which of another mod's classes, methods and fields our compiled code uses are missing from a given jar of that mod
(lane-e-1108-1314, B1059: the mod compiles against Cobblemon 1.7.3, so a call 1.8.1 dropped only shows up as a
NoSuchMethodError when that line runs). Reads the constant pools with javap; a member counts as present by name and
descriptor, or by name and argument count when its descriptor names Minecraft types (our classes use Mojang names,
the jar intermediary ones), searching the jar's superclasses too.

    ./gradlew :1.21.1:classes :1.21.1:compattestClasses   # (any build does it)
    python3 tools/modtest/apiscan.py versions/1.21.1/build/classes/java/main <cobblemon-1.8.1.jar>
    python3 tools/modtest/apiscan.py versions/1.21.1/build/classes/java/compattest <jar> [--prefix com/cobblemon]

The Cobblemon 1.8.1 jar is maven.modrinth:cobblemon:gBW3vLC7 in ~/.gradle/caches/modules-2 after a -Pcobblemon18=true
run. Prints one line per missing reference with the classes that use it; nothing below the count means all present.
"""
import re, subprocess, sys, zipfile, os, collections, shutil, tempfile
args = [a for a in sys.argv[1:]]
PREFIX = "com/cobblemon"
if "--prefix" in args:
    i = args.index("--prefix"); PREFIX = args[i + 1].replace(".", "/"); del args[i:i + 2]
if len(args) != 2:
    sys.exit(__doc__)
classes_dir, jar = args
JAVAP = shutil.which("javap") or "javap"
TMP = tempfile.mkdtemp()
refs = collections.defaultdict(set)  # (owner, name, desc, kind) -> users
files = []
for root, _, fs in os.walk(classes_dir):
    for f in fs:
        if f.endswith(".class"): files.append(os.path.join(root, f))
P = re.escape(PREFIX)
ref_re = re.compile(r"= (Methodref|InterfaceMethodref|Fieldref)\s+#\d+\.#\d+\s+// (" + P + r"/[\w/$]+)\.(\"?[\w<>$]+\"?):(\S+)")
cls_re = re.compile(r"= Class\s+#\d+\s+// (" + P + r"/[\w/$]+)")
for i in range(0, len(files), 200):
    out = subprocess.run([JAVAP, "-v", "-p"] + files[i:i+200], capture_output=True, text=True).stdout
    cur = None
    for line in out.splitlines():
        if line.startswith("Classfile "): cur = line.split("/aliveworkplace/")[-1]
        m = ref_re.search(line)
        if m: refs[(m.group(2), m.group(3).strip('"'), m.group(4), m.group(1))].add(cur)
        m = cls_re.search(line)
        if m: refs[(m.group(1), None, None, "Class")].add(cur)
z = zipfile.ZipFile(jar)
names = set(z.namelist())
cache = {}
def members(owner):
    if owner in cache: return cache[owner]
    path = owner + ".class"
    if path not in names: cache[owner] = None; return None
    tmp = os.path.join(TMP, "x.class")
    open(tmp, "wb").write(z.read(path))
    out = subprocess.run([JAVAP, "-s", "-p", tmp], capture_output=True, text=True).stdout
    ms, sup = [], []
    lines = out.splitlines()
    for j, l in enumerate(lines):
        if l.strip().startswith("descriptor:"):
            decl = lines[j-1]
            nm = re.search(r"([\w$<>]+)\(", decl) or re.search(r"([\w$]+);\s*$", decl)
            n = nm.group(1) if nm else "?"
            if n == owner.split("/")[-1].split("$")[-1] or ("$" in owner and n == owner.split("/")[-1]): n = "<init>"
            ms.append((n, l.split(":",1)[1].strip()))
    hdr = [l for l in lines if " class " in l or " interface " in l]
    if hdr:
        h = hdr[0]
        while "<" in h: h = re.sub(r"<[^<>]*>", "", h)
        hdr = [h]
        for kw in ("extends", "implements"):
            m = re.search(kw + r" ([\w.$<>, ?]+?)(?: implements|\{|$)", hdr[0])
            if m: sup += [s.strip().split("<")[0].replace(".", "/") for s in m.group(1).split(",")]
    cache[owner] = (ms, sup); return cache[owner]
def arity(desc):
    inner = desc[1:desc.index(")")]
    return len(re.findall(r"\[*(?:L[^;]+;|[ZBCSIJFD])", inner))
def find(owner, name, desc, depth=0):
    m = members(owner)
    if m is None: return None
    ms, sup = m
    for n, d in ms:
        if n == name:
            if desc is None or "net/minecraft" in desc or "com/mojang" in desc:
                if desc is None or (desc.startswith("(") and d.startswith("(") and arity(d) == arity(desc)) or not desc.startswith("("): return True
            elif d == desc: return True
    if depth < 6:
        for s in sup:
            if s.startswith(PREFIX) and find(s, name, desc, depth+1): return True
            if not s.startswith(PREFIX): return "external"  # inherited from Kotlin/Java/MC: can't tell
    return False
missing = []
for (owner, name, desc, kind), users in sorted(refs.items(), key=lambda x: str(x[0])):
    if kind == "Class":
        if owner + ".class" not in names: missing.append(("CLASS", owner, "", "", users))
        continue
    r = find(owner, name, desc)
    if r is None: missing.append(("NOCLASS", owner, name, desc, users))
    elif r is False: missing.append(("MEMBER", owner, name, desc, users))
print(f"{len(refs)} references checked")
for k, o, n, d, u in missing:
    print(k, o, n, d, "<-", ", ".join(sorted(u))[:200])
shutil.rmtree(TMP, ignore_errors=True)
sys.exit(1 if missing else 0)

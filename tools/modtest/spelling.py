#!/usr/bin/env python3
"""American English for every sentence a player reads (owner, 2026-10-26; ROADMAP 36.19).

  spelling.py [repo]           check en_us.json values, the questbook, docs/guide/ and docs/wiki/; exit 1 with one line per hit
  spelling.py --fix [repo]     rewrite them in American spelling (values only; keys, ids and `code` stay)

Used by langcheck.py --spelling, which the build runs (checkSpelling in build.gradle.kts). A word is British when it is
in WORDS, or when one of the RULES turns it into an American word; ALLOW lists the words the rules would catch but that
are American too ("rise", "promise", "acre", ...). Keys, registry ids, saved fields, class names and file names are
never changed (jewellers_workshop and DefencePage stay): in markdown, `code spans` and fenced blocks are skipped, and
words joined to "_" or "/" or written in CamelCase are ids, not prose.
"""
import json
import re
import sys
from pathlib import Path

# Whole words (lowercase) and their American spelling. Inflections are listed where the rules below don't make them.
WORDS = {
    "grey": "gray", "greys": "grays", "greyed": "grayed", "greying": "graying", "greyish": "grayish",
    "greyer": "grayer", "greyness": "grayness",
    "defence": "defense", "defences": "defenses", "defenceless": "defenseless",
    "offence": "offense", "offences": "offenses", "pretence": "pretense",
    "licence": "license", "licences": "licenses", "licenced": "licensed",
    "practise": "practice", "practised": "practiced", "practises": "practices", "practising": "practicing",
    "programme": "program", "programmes": "programs",
    "storey": "story", "storeys": "stories", "storeyed": "storied",
    "kerb": "curb", "kerbs": "curbs", "kerbed": "curbed", "tyre": "tire", "tyres": "tires",
    "aluminium": "aluminum", "draught": "draft", "draughts": "drafts", "draughty": "drafty",
    "mould": "mold", "moulds": "molds", "moulded": "molded", "moulding": "molding", "mouldy": "moldy",
    "smoulder": "smolder", "smoulders": "smolders", "smouldering": "smoldering", "smouldered": "smoldered",
    "moult": "molt", "moulting": "molting",
    "cheque": "check", "cheques": "checks",
    "plough": "plow", "ploughs": "plows", "ploughed": "plowed", "ploughing": "plowing",
    "ploughman": "plowman", "ploughshare": "plowshare",
    "catalogue": "catalog", "catalogues": "catalogs", "catalogued": "cataloged", "cataloguing": "cataloging",
    "analogue": "analog",
    "jewellery": "jewelry", "jeweller": "jeweler", "jewellers": "jewelers", "jeweller's": "jeweler's",
    "jewellers'": "jewelers'",
    "marvellous": "marvelous", "marvellously": "marvelously",
    "woollen": "woolen", "woollens": "woolens",
    "fulfil": "fulfill", "fulfils": "fulfills", "fulfilment": "fulfillment",
    "enrol": "enroll", "enrols": "enrolls", "enrolment": "enrollment",
    "instil": "instill", "instils": "instills", "distil": "distill", "distils": "distills",
    "skilful": "skillful", "skilfully": "skillfully", "wilful": "willful", "wilfully": "willfully",
    "instalment": "installment", "instalments": "installments",
    "judgement": "judgment", "judgements": "judgments",
    "acknowledgement": "acknowledgment", "acknowledgements": "acknowledgments",
    "ageing": "aging", "whilst": "while", "learnt": "learned", "spelt": "spelled", "dreamt": "dreamed",
    "manoeuvre": "maneuver", "manoeuvres": "maneuvers", "manoeuvred": "maneuvered",
    "artefact": "artifact", "artefacts": "artifacts", "aeroplane": "airplane", "aeroplanes": "airplanes",
    "pyjamas": "pajamas", "moustache": "mustache", "cosy": "cozy", "cosier": "cozier", "cosiest": "coziest",
    "cosiness": "coziness", "sulphur": "sulfur", "sulphurous": "sulfurous", "mediaeval": "medieval",
    "encyclopaedia": "encyclopedia", "sceptic": "skeptic", "sceptical": "skeptical", "scepticism": "skepticism",
    "waggon": "wagon", "waggons": "wagons", "despatch": "dispatch", "despatched": "dispatched",
    "speciality": "specialty", "specialities": "specialties", "maths": "math",
    "yoghurt": "yogurt", "chilli": "chili", "chillies": "chilies",
    "enquiry": "inquiry", "enquiries": "inquiries", "enquire": "inquire", "enquired": "inquired",
    "gaol": "jail", "gaoler": "jailer",
    "axeman": "axman",
}

# Stems spelled -our in Britain and -or in America; any word starting with one is caught (colourful, honourable,
# favourite, armoury, neighbourhood, labourer, savoury ...).
OUR = ["col", "hon", "arm", "neighb", "fav", "behavi", "flav", "harb", "lab", "rum", "vig", "val", "splend", "od",
       "hum", "parl", "savi", "endeav", "cand", "clam", "ferv", "rig", "sav", "ard", "tum", "demean", "arb", "vap",
       "glam"]
OUR_NOT = {"glamour", "glamourous"}   # "glamour" is American too

# Verbs whose last l doubles in Britain only (travelled, cancelling, labelled, modelling, fuelled, jeweller ...).
DOUBLE_L = ["travel", "cancel", "label", "model", "level", "fuel", "signal", "jewel", "counsel", "quarrel", "total",
            "channel", "tunnel", "funnel", "dial", "duel", "panel", "pencil", "shovel", "swivel", "marvel", "revel",
            "grovel", "chisel", "enamel", "equal", "barrel", "gravel", "unravel", "ravel", "bevel", "kennel",
            "trial", "spiral", "rival", "carol", "pedal", "tassel", "tinsel", "yodel", "snorkel", "libel", "squirrel",
            "initial", "cudgel", "dishevel", "drivel", "hostel", "jewel", "medal", "pummel",
            "sentinel", "shrivel", "stencil", "towel", "vial", "victual"]
DOUBLE_L_END = ("led", "ling", "ler", "lers", "lings")

# -re words that are -er in America (centre, theatre, metre ...). Ochre stays: Minecraft's own Ochre Froglight.
RE_WORDS = ["centre", "theatre", "metre", "litre", "fibre", "lustre", "sabre", "sceptre", "sombre", "spectre",
            "meagre", "calibre", "mitre", "goitre", "lacklustre", "kilometre", "centimetre", "millimetre",
            "amphitheatre", "epicentre", "accoutre", "louvre", "reconnoitre", "saltpetre", "titre"]

# -ise / -isation / -yse: caught by shape, minus the words that are American too.
ISE = re.compile(r"^(.*[^s])is(e|es|ed|ing|er|ers|ation|ations|able|ational)$")
YSE = re.compile(r"^(anal|paral|catal|electrol|hydrol|dial)ys(e|es|ed|ing|er|ers)$")
ALLOW = {
    "rise", "arise", "sunrise", "moonrise", "uprise", "wise", "likewise", "otherwise", "clockwise", "lengthwise",
    "crosswise", "edgewise", "streetwise", "noise", "poise", "promise", "exercise", "surprise", "advertise",
    "compromise", "expertise", "merchandise", "chastise", "enterprise", "despise", "paradise", "premise",
    "precise", "concise", "disguise", "supervise", "televise", "revise", "devise", "franchise", "improvise",
    "comprise", "raise", "praise", "appraise", "cruise", "bruise", "advise", "anise", "mortise", "treatise",
    "circumcise", "excise", "incise", "demise", "guise", "reprise", "apprise", "surmise", "valise", "chemise",
    "braise", "liaise", "turquoise", "tortoise", "porpoise", "cerise", "remise", "imprecise", "upraise",
    "mise", "miser", "noiseless", "counterwise", "fanwise", "stepwise", "pairwise", "piecewise", "sidewise",
    "slantwise", "anticlockwise", "unwise", "overwise", "dispraise", "reraise", "chaise", "marquise", "elise",
    "trellise", "portcullise", "louise", "denise", "kaiser", "raiser", "fundraise", "hoise", "vise", "visa", "bise", "prise",
    "precis", "artise", "noisemaker", "cerise", "frise", "paise", "valise", "geise", "reise",
    "irise", "crise", "devis", "supervision", "revision", "division", "provision", "televison",
}

# Words the rules would wrongly catch; never reported.
NOT_BRITISH = {"ochre", "ochres", "acre", "acres", "massacre", "mediocre", "genre", "lucre", "timbre", "macabre",
               "cadre", "padre", "hour", "four", "pour", "tour", "your", "flour", "sour", "devour",
               "contour", "detour", "dour", "glamour", "armorer", "armorers"}


def american(word):
    """The American spelling of word (any case), or None when it isn't British."""
    low = word.lower()
    if low in NOT_BRITISH or low in ALLOW:
        return None
    new = WORDS.get(low)
    if new is None:
        for stem in OUR:
            if low.startswith(stem + "our") and low not in OUR_NOT:
                new = stem + "or" + low[len(stem) + 3:]
                break
    if new is None:
        for stem in DOUBLE_L:
            for end in DOUBLE_L_END:
                if low == stem + end:
                    new = stem + end[1:]
    if new is None:
        for w in RE_WORDS:
            stem = w[:-2]
            forms = {w: stem + "er", w + "s": stem + "ers", w + "d": stem + "ered", w[:-1] + "ing": stem + "ering"}
            if low in forms:
                new = forms[low]
    if new is None:
        m = YSE.match(low)
        if m:
            new = m.group(1) + "yz" + m.group(2)
    if new is None:
        m = ISE.match(low)
        if m and (m.group(1) + "ise") not in ALLOW and low not in ALLOW:
            end = m.group(2)
            new = m.group(1) + "iz" + end
    if new is None or new == low:
        return None
    return match_case(word, new)


def match_case(old, new):
    if old.isupper() and len(old) > 1:
        return new.upper()
    if old[:1].isupper():
        return new[:1].upper() + new[1:]
    return new


# A word in prose: letters with inner apostrophes ("grey-green" is two). Words touching _ / : { digits, a file name
# (smiths-draught.md) or CamelCase are ids or code.
WORD = re.compile(r"(?<![\w/_.$#:{])[A-Za-z]+(?:'[a-z]+)?(?![\w/_]|\.\w|-[\w-]*\.\w)")


def prose_words(text):
    for m in WORD.finditer(text):
        w = m.group(0)
        if re.search(r"[a-z][A-Z]", w):     # CamelCase: a class or id
            continue
        yield m


def fix_text(text):
    """(text in American spelling, [(british, american)])."""
    hits = []
    out, last = [], 0
    for m in prose_words(text):
        w = m.group(0)
        base, apo = (w.split("'", 1) + [""])[:2]
        new = american(w) if "'" in w and american(w) else None
        if new is None:
            nb = american(base)
            if nb is not None:
                new = nb + ("'" + apo if apo else "")
        if new is not None:
            hits.append((w, new))
            out.append(text[last:m.start()])
            out.append(new)
            last = m.end()
    out.append(text[last:])
    return "".join(out), hits


CODE = re.compile(r"```.*?```|`[^`\n]*`|\]\([^)]*\)|<[^>\n]*>|https?://\S+", re.S)

# Other mods' own names, kept as their mods write them (like vanilla's): Create's Mechanical Plough.
OTHER_NAMES = re.compile(r"\b(?:[Mm]echanical [Pp]lough|harvester and plough)\b")


def keep_spans(text, spans, fixer):
    """fixer over the text between the matches of spans; the matches stay as they are."""
    hits, out, last = [], [], 0
    for m in spans.finditer(text):
        t, h = fixer(text[last:m.start()])
        out += [t, m.group(0)]
        hits += h
        last = m.end()
    t, h = fixer(text[last:])
    out.append(t)
    return "".join(out), hits + h


def fix_prose(text):
    return keep_spans(text, OTHER_NAMES, fix_text)


def fix_markdown(text):
    """Like fix_prose, but code spans, fenced blocks, link targets, HTML tags and URLs stay as they are."""
    return keep_spans(text, CODE, fix_prose)


STRING = re.compile(r'"((?:[^"\\\n]|\\.)*)"')


def fix_snbt(text):
    """fix_prose inside each quoted string of a questbook .snbt file (its text; keys and ids aren't prose)."""
    hits = []

    def one(m):
        new, h = fix_prose(m.group(1))
        hits.extend(h)
        return '"' + new + '"'
    return STRING.sub(one, text), hits


def lang_files(root):
    return sorted(root.glob("src/*/resources/assets/*/lang/en_us.json"))


def doc_files(root):
    return sorted(list((root / "docs/guide").glob("*.md")) + list((root / "docs/wiki").glob("*.md")))


def questbook_files(root):
    return sorted(root.glob("src/*/resources/*/questbook/*.snbt"))


def check(root, fix=False):
    """[(file, where, british, american)] for every British word left; with fix, rewrites them first."""
    found = []
    for p in lang_files(root):
        src = p.read_text(encoding="utf-8")
        d = json.loads(src)
        changed = False
        for k, v in d.items():
            if not isinstance(v, str):
                continue
            new, hits = fix_prose(v)
            found += [(p, k, b, a) for b, a in hits]
            if hits and fix:
                d[k] = new
                changed = True
        if changed:
            p.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    for p in doc_files(root) + questbook_files(root):
        src = p.read_text(encoding="utf-8")
        new, hits = (fix_snbt if p.suffix == ".snbt" else fix_markdown)(src)
        found += [(p, "", b, a) for b, a in hits]
        if hits and fix:
            p.write_text(new, encoding="utf-8")
    return found


def main(argv):
    fix = "--fix" in argv
    args = [a for a in argv if not a.startswith("--")]
    root = Path(args[0] if args else ".").resolve()
    found = check(root, fix)
    for p, k, b, a in found:
        print(f"{p.relative_to(root)}{': ' + k if k else ''}: \"{b}\" is British, write \"{a}\"")
    if fix:
        print(f"fixed {len(found)} word(s)")
        return 0
    return 1 if found else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))

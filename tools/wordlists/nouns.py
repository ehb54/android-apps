"""Noun frequency lists from public-domain texts, turned into Quiz sample files.

Steps, each writing into a list folder (e.g. grimm/):

  count  <list> <gutenberg-id>...   counts.tsv: nouns by frequency, with spread and an example
  enrich <list> [top]               draft.tsv: top nouns plus article, plural and English from
                                    Wikidata, and emoji candidates from Unicode's German names
  build  <list> <title> <outdir>    from the hand-reviewed nouns.tsv: "<title> nouns.xlsx"
                                    (tabs: nouns, articles, plurals) and "<title> pictures.zip"

Run through run.sh, which provides spaCy and the other tools. See README.md.
"""
import collections
import csv
import io
import json
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

CACHE = Path("cache")
USER_AGENT = "android-apps-wordlists/0.1 (https://github.com/ehb54/android-apps)"
CLDR_RELEASE = "release-49"
NOTO_RELEASE = "v2026-09-24-unicode18_0"
CHUNK_TOKENS = 1000          # spread is counted over chunks of this many tokens
MIN_RANGE = 3                # a noun must appear in at least this many chunks
PICTURE_SIZE = 256           # pixels, square
ARTICLES = {"Q499327": "der", "Q1775415": "die", "Q1775461": "das"}


def fetch(url, name):
    """Downloads url into the cache once and returns the cached path."""
    path = CACHE / name
    if not path.exists():
        CACHE.mkdir(exist_ok=True)
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=120) as response:
            path.write_bytes(response.read())
        time.sleep(1)
    return path


def read_tsv(path):
    with open(path, encoding="utf-8", newline="") as f:
        return list(csv.DictReader(f, delimiter="\t"))


def write_tsv(path, rows, fields):
    with open(path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fields, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


# --- count -------------------------------------------------------------------

def gutenberg_text(book):
    """The body of a Gutenberg plain-text book, without its header, footer and markup."""
    raw = fetch(f"https://www.gutenberg.org/cache/epub/{book}/pg{book}.txt", f"pg{book}.txt").read_text("utf-8")
    start = re.search(r"\*\*\* START OF.*\*\*\*", raw)
    end = re.search(r"\*\*\* END OF.*\*\*\*", raw)
    body = raw[start.end() if start else 0:end.start() if end else len(raw)]
    body = re.sub(r"\[Illustration[^\]]*\]", " ", body)
    return body.replace("~", "")


def count(name, books):
    import spacy
    nlp = spacy.load("de_core_news_md", disable=["parser", "ner"])
    nlp.add_pipe("sentencizer")
    paragraphs = [p.strip() for book in books for p in re.split(r"\n\s*\n", gutenberg_text(book)) if p.strip()]
    # Old spellings and forms the tagger leaves as they are, mapped to today's lemma.
    spelling_file = Path(name) / "spelling.tsv"
    spelling = {r["found"]: r["lemma"] for r in read_tsv(spelling_file)} if spelling_file.exists() else {}

    counts, chunks, examples = collections.Counter(), collections.defaultdict(set), {}
    tokens = 0
    for doc in nlp.pipe((re.sub(r"\s+", " ", p) for p in paragraphs), batch_size=64, n_process=8):
        for sentence in doc.sents:
            for token in sentence:
                tokens += 1
                lemma = spelling.get(token.lemma_, token.lemma_)
                if token.pos_ != "NOUN" or not re.fullmatch(r"[A-ZÄÖÜ][a-zäöüß]+", lemma):
                    continue
                counts[lemma] += 1
                chunks[lemma].add(tokens // CHUNK_TOKENS)
                if lemma not in examples and len(sentence.text) <= 160:
                    examples[lemma] = sentence.text
    total_chunks = tokens // CHUNK_TOKENS + 1
    rows = [{"lemma": lemma, "count": n, "range": len(chunks[lemma]), "example": examples.get(lemma, "")}
            for lemma, n in counts.most_common() if len(chunks[lemma]) >= MIN_RANGE]
    Path(name).mkdir(exist_ok=True)
    write_tsv(Path(name) / "counts.tsv", rows, ["lemma", "count", "range", "example"])
    print(f"{tokens} tokens in {total_chunks} chunks; {len(counts)} noun lemmas, "
          f"{len(rows)} in at least {MIN_RANGE} chunks -> {name}/counts.tsv")


# --- enrich ------------------------------------------------------------------

WIKIDATA_QUERY = """
SELECT ?lemma ?gender ?plural ?gloss WHERE {
  VALUES ?lemma { %s }
  ?lexeme dct:language wd:Q188; wikibase:lexicalCategory wd:Q1084; wikibase:lemma ?lemma.
  OPTIONAL { ?lexeme wdt:P5185 ?gender. }
  OPTIONAL { ?lexeme ontolex:lexicalForm ?form.
             ?form wikibase:grammaticalFeature wd:Q146786, wd:Q131105; ontolex:representation ?plural. }
  OPTIONAL { ?lexeme ontolex:sense ?sense. ?sense skos:definition ?gloss. FILTER(LANG(?gloss) = "en") }
}"""


def wikidata(lemmas):
    """Article(s), plural(s) and English glosses for German nouns from Wikidata lexemes (CC0)."""
    found = collections.defaultdict(lambda: {"article": [], "plural": [], "english": []})
    for start in range(0, len(lemmas), 80):
        values = " ".join(f'"{lemma}"@de' for lemma in lemmas[start:start + 80])
        url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode(
            {"query": WIKIDATA_QUERY % values, "format": "json"})
        request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        with urllib.request.urlopen(request, timeout=120) as response:
            bindings = json.load(response)["results"]["bindings"]
        for b in bindings:
            entry = found[b["lemma"]["value"]]
            for key, value in (("article", ARTICLES.get(b.get("gender", {}).get("value", "").rsplit("/", 1)[-1])),
                               ("plural", b.get("plural", {}).get("value")),
                               ("english", b.get("gloss", {}).get("value"))):
                if value and value not in entry[key]:
                    entry[key].append(value)
        time.sleep(2)
    return found


def emoji_names():
    """German emoji names from Unicode CLDR: emoji -> (spoken name, keywords)."""
    url = f"https://raw.githubusercontent.com/unicode-org/cldr/{CLDR_RELEASE}/common/annotations/de.xml"
    root = ET.parse(fetch(url, f"cldr-{CLDR_RELEASE}-de.xml")).getroot()
    names = {}
    for a in root.iter("annotation"):
        name, keywords = names.get(a.get("cp"), ("", []))
        if a.get("type") == "tts":
            name = a.text.strip()
        else:
            keywords = [k.strip() for k in a.text.split("|")]
        names[a.get("cp")] = (name, keywords)
    return names


def emoji_candidates(lemma, names):
    """Emoji whose German name is the noun, then emoji with the noun as a keyword."""
    exact = [cp for cp, (name, _) in names.items() if name.lower() == lemma.lower()]
    keyword = [cp for cp, (name, kw) in names.items() if cp not in exact and lemma.lower() in (k.lower() for k in kw)]
    return " ".join(exact + keyword[:3])


def enrich(name, top):
    rows = read_tsv(Path(name) / "counts.tsv")[:top]
    found = wikidata([r["lemma"] for r in rows])
    names = emoji_names()
    draft = []
    for rank, row in enumerate(rows, 1):
        info = found.get(row["lemma"], {"article": [], "plural": [], "english": []})
        draft.append({
            "rank": rank, "lemma": row["lemma"], "count": row["count"], "range": row["range"],
            "article": "/".join(info["article"]), "plural": "/".join(info["plural"]),
            "english": "; ".join(info["english"][:4]), "emoji": emoji_candidates(row["lemma"], names),
            "example": row["example"],
        })
    write_tsv(Path(name) / "draft.tsv", draft,
              ["rank", "lemma", "count", "range", "article", "plural", "english", "emoji", "example"])
    print(f"{len(draft)} nouns; Wikidata found {sum(r['lemma'] in found for r in rows)}, "
          f"emoji candidates for {sum(bool(d['emoji']) for d in draft)} -> {name}/draft.tsv")


# --- build -------------------------------------------------------------------

def noto_picture(emoji):
    """The Noto Emoji picture for an emoji, PNG bytes resized to PICTURE_SIZE."""
    from PIL import Image
    code = "_".join(f"{ord(c):x}" for c in emoji if ord(c) != 0xFE0F)
    url = f"https://raw.githubusercontent.com/googlefonts/noto-emoji/{NOTO_RELEASE}/2D/png/512/emoji_u{code}.png"
    try:
        path = fetch(url, f"noto-emoji_u{code}.png")
    except urllib.error.HTTPError as e:
        sys.exit(f"no Noto Emoji picture for {emoji} ({e.code}): {url}")
    image = Image.open(path).convert("RGBA")
    image = image.resize((PICTURE_SIZE, PICTURE_SIZE), Image.LANCZOS)
    out = io.BytesIO()
    image.save(out, "PNG", optimize=True)
    return f"emoji_u{code}.png", out.getvalue()


def build(name, title, outdir):
    from openpyxl import Workbook
    nouns = read_tsv(Path(name) / "nouns.tsv")
    counted = {r["lemma"] for r in read_tsv(Path(name) / "counts.tsv")}
    for n in nouns:
        if n["lemma"] not in counted or n["article"] not in ("der", "die", "das") or not n["english"]:
            sys.exit(f"check nouns.tsv: {n}")
        n["word"] = f"{n['article']} {n['lemma']}"

    book = Workbook()
    tabs = [
        (f"{title} nouns", [(n["word"], n["english"]) for n in nouns]),
        (f"{title} articles", [(n["lemma"], n["article"]) for n in nouns]),
        (f"{title} plurals", [(n["word"], f"die {n['plural']}") for n in nouns if n["plural"]]),
    ]
    book.remove(book.active)
    for tab, rows in tabs:
        sheet = book.create_sheet(tab)
        for row in rows:
            sheet.append(row)
    book.save(Path(outdir) / f"{title} nouns.xlsx")

    noto_license = fetch(f"https://raw.githubusercontent.com/googlefonts/noto-emoji/{NOTO_RELEASE}/LICENSE",
                         f"noto-emoji-{NOTO_RELEASE}-LICENSE")
    with zipfile.ZipFile(Path(outdir) / f"{title} pictures.zip", "w", zipfile.ZIP_DEFLATED) as z:
        lines = []
        for n in nouns:
            if n["emoji"]:
                file, png = noto_picture(n["emoji"])
                z.writestr(file, png)
                lines.append(f"!{file}\n{n['word']}\n")
        z.writestr(f"{title} pictures.qzx", "".join(lines))
        z.writestr("CREDITS.txt",
                   "Pictures: Noto Emoji by Google (https://github.com/googlefonts/noto-emoji), "
                   f"release {NOTO_RELEASE}, Apache License 2.0 (LICENSE-Noto-Emoji.txt), "
                   f"resized to {PICTURE_SIZE} x {PICTURE_SIZE} pixels.\n")
        z.write(noto_license, "LICENSE-Noto-Emoji.txt")
    print(f"{len(nouns)} nouns, {sum(len(rows) for _, rows in tabs)} cards in 3 tabs, "
          f"{sum(bool(n['emoji']) for n in nouns)} pictures -> {outdir}")


if __name__ == "__main__":
    command, args = sys.argv[1], sys.argv[2:]
    if command == "count":
        count(args[0], [int(book) for book in args[1:]])
    elif command == "enrich":
        enrich(args[0], int(args[1]) if len(args) > 1 else 250)
    elif command == "build":
        build(*args)
    else:
        sys.exit(__doc__)

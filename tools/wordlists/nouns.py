"""Noun frequency lists from public-domain texts, turned into Quiz sample files.

Steps, each writing into a list folder (e.g. grimm/):

  count  <list> [gutenberg-id...]   counts.tsv: nouns by frequency, with spread and an example;
                                    the books are <list>/books.tsv unless given
  enrich <list> [top] [reference]   draft.tsv: top nouns (by keyness against a reference list,
                                    if given) plus article, plural and English from Wikidata,
                                    and emoji candidates from Unicode's German names
  select <list>                     nouns.tsv: the reviewed nouns (review.tsv) the list's rules
                                    (list.json) take, in draft order
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
EXAMPLES = 5000              # counts.tsv keeps example sentences for this many top nouns
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
    """Writes to a temporary file first, so a failure never leaves a half-written TSV."""
    temporary = Path(f"{path}.tmp")
    with open(temporary, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fields, delimiter="\t", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)
    temporary.replace(path)


# --- count -------------------------------------------------------------------

def gutenberg_text(book):
    """The body of a Gutenberg plain-text book, without its header, footer and markup."""
    raw = fetch(f"https://www.gutenberg.org/cache/epub/{book}/pg{book}.txt", f"pg{book}.txt").read_text("utf-8")
    start = re.search(r"\*\*\* START OF.*\*\*\*", raw)
    end = re.search(r"\*\*\* END OF.*\*\*\*", raw)
    body = raw[start.end() if start else 0:end.start() if end else len(raw)]
    body = re.sub(r"\[Illustration[^\]]*\]", " ", body)
    return body.replace("~", "")


def old_spelling_rules():
    """Regexes for oldspelling.tsv: pre-1901 and pre-1996 spellings to today's.
    A leading or trailing * in `old` (and `new`) matches any letters there; rules
    are case-sensitive, so each capitalization needed has its own line."""
    rules = []
    for row in read_tsv(Path("oldspelling.tsv")):
        old, new = row["old"], row["new"]
        core_old, core_new = old.strip("*"), new.strip("*")
        before = r"(?<![\wäöüß])" if not old.startswith("*") else r"(?<=[\wäöüß])"
        after = r"(?![\wäöüß])" if not old.endswith("*") else r"(?=[\wäöüß])"
        if old.startswith("*") and old.endswith("*"):
            before, after = "", ""
        rules.append((re.compile(before + re.escape(core_old) + after), core_new))
    return rules


def modernize(text, rules):
    for pattern, replacement in rules:
        text = pattern.sub(replacement, text)
    return text


def count(name, books):
    import os
    import spacy
    nlp = spacy.load("de_core_news_md", disable=["parser", "ner"])
    nlp.add_pipe("sentencizer")
    if not books:
        books = [int(r["id"]) for r in read_tsv(Path(name) / "books.tsv")]
    rules = old_spelling_rules()
    # Lemmas the tagger gets wrong (old datives, old-spelling dictionary entries),
    # mapped to today's lemma: lemmas.tsv for every list, <list>/spelling.tsv for one.
    spelling = {r["found"]: r["lemma"] for r in read_tsv(Path("lemmas.tsv"))}
    spelling_file = Path(name) / "spelling.tsv"
    if spelling_file.exists():
        spelling.update({r["found"]: r["lemma"] for r in read_tsv(spelling_file)})

    paragraphs = [(book, modernize(re.sub(r"\s+", " ", p), rules))
                  for book in books for p in re.split(r"\n\s*\n", gutenberg_text(book)) if p.strip()]
    counts, chunks, in_books, examples = (collections.Counter(), collections.defaultdict(set),
                                          collections.defaultdict(set), {})
    tokens = 0
    docs = nlp.pipe((p for _, p in paragraphs), batch_size=64, n_process=min(32, os.cpu_count() or 1))
    for (book, _), doc in zip(paragraphs, docs):
        for sentence in doc.sents:
            for token in sentence:
                tokens += 1
                lemma = spelling.get(token.lemma_, token.lemma_)
                if token.pos_ != "NOUN" or not re.fullmatch(r"[A-ZÄÖÜ][a-zäöüß]+", lemma):
                    continue
                counts[lemma] += 1
                chunks[lemma].add(tokens // CHUNK_TOKENS)
                in_books[lemma].add(book)
                if lemma not in examples and len(sentence.text) <= 160:
                    examples[lemma] = sentence.text
    min_books = min(MIN_RANGE, len(books))
    rows = [{"lemma": lemma, "count": n, "range": len(chunks[lemma]), "books": len(in_books[lemma]),
             "example": ""}
            for lemma, n in counts.most_common()
            if len(chunks[lemma]) >= MIN_RANGE and len(in_books[lemma]) >= min_books]
    # Examples only where review looks (they would make big lists' files huge).
    for row in rows[:EXAMPLES]:
        row["example"] = examples.get(row["lemma"], "")
    Path(name).mkdir(exist_ok=True)
    write_tsv(Path(name) / "counts.tsv", rows, ["lemma", "count", "range", "books", "example"])
    stats = {"books": len(books), "tokens": tokens, "nouns": sum(counts.values())}
    (Path(name) / "stats.json").write_text(json.dumps(stats, indent=1) + "\n")
    print(f"{len(books)} books, {tokens} tokens, {stats['nouns']} nouns; {len(counts)} noun lemmas, "
          f"{len(rows)} in at least {MIN_RANGE} chunks and {min_books} books -> {name}/counts.tsv")


def keyness(name, reference):
    """Log-likelihood (Dunning G2) of each noun in this list against a reference
    list: how much more this domain uses it than general German does."""
    import math
    own = read_tsv(Path(name) / "counts.tsv")
    ref = {r["lemma"]: int(r["count"]) for r in read_tsv(Path(reference) / "counts.tsv")}
    n1 = json.loads((Path(name) / "stats.json").read_text())["nouns"]
    n2 = json.loads((Path(reference) / "stats.json").read_text())["nouns"]
    for row in own:
        a, b = int(row["count"]), ref.get(row["lemma"], 0)
        e1, e2 = n1 * (a + b) / (n1 + n2), n2 * (a + b) / (n1 + n2)
        g2 = 2 * (a * math.log(a / e1) + (b * math.log(b / e2) if b else 0))
        row["keyness"] = round(g2 if a / n1 > b / n2 else -g2, 1)
        row["reference"] = b
    return sorted(own, key=lambda r: -r["keyness"])


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


def sparql(url, attempts=5):
    """Results of a Wikidata query, retrying the service's frequent 5xx and timeouts."""
    for attempt in range(attempts):
        try:
            request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
            with urllib.request.urlopen(request, timeout=120) as response:
                return json.load(response)["results"]["bindings"]
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as e:
            if isinstance(e, urllib.error.HTTPError) and e.code < 500 and e.code != 429:
                raise
            if attempt == attempts - 1:
                raise
            time.sleep(10 * 2 ** attempt)
    return []


def wikidata(lemmas):
    """Article(s), plural(s) and English glosses for German nouns from Wikidata lexemes (CC0)."""
    found = collections.defaultdict(lambda: {"article": [], "plural": [], "english": []})
    for start in range(0, len(lemmas), 80):
        values = " ".join(f'"{lemma}"@de' for lemma in lemmas[start:start + 80])
        url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode(
            {"query": WIKIDATA_QUERY % values, "format": "json"})
        bindings = sparql(url)
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


def enrich(name, top, reference=None):
    """The top nouns by count, or with a reference list by keyness against it."""
    if reference:
        rows = [r for r in keyness(name, reference) if r["keyness"] > 0][:top]
    else:
        rows = read_tsv(Path(name) / "counts.tsv")[:top]
    found = wikidata([r["lemma"] for r in rows])
    names = emoji_names()
    draft = []
    for rank, row in enumerate(rows, 1):
        info = found.get(row["lemma"], {"article": [], "plural": [], "english": []})
        draft.append({
            "rank": rank, "lemma": row["lemma"], "count": row["count"], "range": row["range"],
            "books": row.get("books", ""), "keyness": row.get("keyness", ""), "reference": row.get("reference", ""),
            "article": "/".join(info["article"]), "plural": "/".join(info["plural"]),
            "english": "; ".join(info["english"][:4]), "emoji": emoji_candidates(row["lemma"], names),
            "example": row["example"],
        })
    write_tsv(Path(name) / "draft.tsv", draft,
              ["rank", "lemma", "count", "range", "books", "keyness", "reference",
               "article", "plural", "english", "emoji", "example"])
    print(f"{len(draft)} nouns; Wikidata found {sum(r['lemma'] in found for r in rows)}, "
          f"emoji candidates for {sum(bool(d['emoji']) for d in draft)} -> {name}/draft.tsv")


# --- select ------------------------------------------------------------------

def select(name):
    """nouns.tsv: the first `size` nouns in draft order that review.tsv keeps,
    after the rules in list.json. Lists the undecided nouns that stand in the
    way (the next review batch) instead of writing anything."""
    rules = json.loads((Path(name) / "list.json").read_text())
    review = {r["lemma"]: r for r in read_tsv(Path(name) / "review.tsv")}
    excluded = {}
    for other in rules.get("exclude_lists", []):
        excluded.update({r["lemma"]: f"in the {other} list" for r in read_tsv(Path(other) / "nouns.tsv")})
    for group, lemmas in rules.get("exclude_lemmas", {}).items():
        excluded.update({lemma: group for lemma in lemmas})
    min_books = rules.get("min_books", 0)

    kept, undecided, skipped = [], [], collections.Counter()
    for row in read_tsv(Path(name) / "draft.tsv"):
        if len(kept) + len(undecided) == rules["size"]:
            break
        lemma = row["lemma"]
        if lemma in excluded:
            skipped[excluded[lemma]] += 1
        elif row.get("books") and int(row["books"]) < min_books:
            skipped[f"in fewer than {min_books} books"] += 1
        elif lemma not in review:
            undecided.append(lemma)
        elif review[lemma]["decision"] == "drop":
            skipped["dropped in review"] += 1
        else:
            kept.append(review[lemma])
    if undecided:
        sys.exit(f"{len(undecided)} undecided nouns; review them into {name}/review.tsv:\n" + " ".join(undecided))
    if len(kept) < rules["size"]:
        sys.exit(f"only {len(kept)} nouns; enrich {name} with more than {sum(1 for _ in open(Path(name) / 'draft.tsv')) - 1}")
    for field in rules.get("unique", []):
        values = [k[field] for k in kept if k[field]]
        repeated = sorted({v for v in values if values.count(v) > 1})
        if repeated:
            sys.exit(f"repeated {field}: {repeated}")
    fields = ["lemma", "article", "plural", "english", "emoji"]
    write_tsv(Path(name) / "nouns.tsv", [{f: k[f] for f in fields} for k in kept], fields)
    print(f"{len(kept)} nouns ({sum(bool(k['plural']) for k in kept)} with plurals, "
          f"{sum(bool(k['emoji']) for k in kept)} with pictures); skipped: {dict(skipped)} -> {name}/nouns.tsv")


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

    if not any(n["emoji"] for n in nouns):
        print(f"{len(nouns)} nouns, {sum(len(rows) for _, rows in tabs)} cards in 3 tabs, no pictures -> {outdir}")
        return
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
        enrich(args[0], int(args[1]) if len(args) > 1 else 250, args[2] if len(args) > 2 else None)
    elif command == "select":
        select(args[0])
    elif command == "build":
        build(*args)
    else:
        sys.exit(__doc__)

"""Noun and verb frequency lists from public-domain texts, turned into Quiz sample files.

Steps, each reading and writing files in a list folder (e.g. core/):

  count  <list> [gutenberg-id...]   counts.tsv (nouns) and verb-counts.tsv (verbs): lemmas by
                                    frequency, with spread and an example; verbs also with the
                                    forms seen (present, past, participle, perfect auxiliary);
                                    the books are <list>/books.tsv unless given
  enrich <list> [top] [reference]   draft.tsv: top nouns (by keyness against a reference list,
                                    if given) plus article, plural and English from Wikidata,
                                    and emoji candidates from Unicode's German names
  enrich-verbs <list> [top] [reference]
                                    verb-draft.tsv: the same for verbs (English from Wikidata,
                                    forms from the books)
  pending <list> nouns|verbs <dir> [batch] [extra]
                                    the entries `select` would need decided next (plus `extra`
                                    as a margin for drops), as review batch files
                                    <dir>/<list>-<kind>-NN.tsv (draft rows)
  check  nouns|verbs <reviewed> <batch>
                                    checks one reviewed batch file against its batch
  merge  <list> nouns|verbs <file>...
                                    adds reviewed batch files to review.tsv / verb-review.tsv,
                                    checking each row
  select <list>                     nouns.tsv: the reviewed nouns (review.tsv) the list's rules
                                    (list.json) take, in draft order
  select-verbs <list>               verbs.tsv: the same for verbs (verb-review.tsv, and the
                                    "verbs" rules in list.json)
  build  <list> <title> <outdir>    "<title> nouns.xlsx" (tabs: nouns, articles, plurals) and
                                    "<title> pictures.zip" from nouns.tsv, and "<title> verbs.xlsx"
                                    (tabs: verbs, verb forms) from verbs.tsv

Run through run.sh, which provides spaCy and the other tools (pending, check
and merge need only Python). See README.md and REVIEW.md.
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
MIN_RANGE = 3                # a lemma must appear in at least this many chunks
EXAMPLES = 5000              # the counts keep example sentences for this many top lemmas
PICTURE_SIZE = 256           # pixels, square
ARTICLES = {"Q499327": "der", "Q1775415": "die", "Q1775461": "das"}

# What differs between nouns and verbs: the files in a list folder, the
# review columns and the columns of the selected list.
KINDS = {
    "nouns": {
        "counts": "counts.tsv", "draft": "draft.tsv", "review": "review.tsv", "selected": "nouns.tsv",
        "review_fields": ["lemma", "decision", "article", "plural", "english", "emoji", "note"],
        "selected_fields": ["lemma", "article", "plural", "english", "emoji"],
    },
    "verbs": {
        "counts": "verb-counts.tsv", "draft": "verb-draft.tsv", "review": "verb-review.tsv",
        "selected": "verbs.tsv",
        "review_fields": ["lemma", "decision", "infinitive", "english", "forms", "note"],
        "selected_fields": ["lemma", "infinitive", "english", "forms"],
    },
}
FORMS = ["present", "past", "participle", "perfect"]


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


def rules_for(name, kind):
    """The list's rules for nouns (list.json itself) or verbs (its "verbs" entry)."""
    rules = json.loads((Path(name) / "list.json").read_text())
    return rules.get("verbs", {}) if kind == "verbs" else rules


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


class Tally:
    """Counts of one kind of lemma: occurrences, the chunks and books they are
    in, and the first short sentence each appears in."""

    def __init__(self):
        self.counts = collections.Counter()
        self.chunks = collections.defaultdict(set)
        self.books = collections.defaultdict(set)
        self.examples = {}

    def add(self, lemma, position, book, sentence):
        self.counts[lemma] += 1
        self.chunks[lemma].add(position // CHUNK_TOKENS)
        self.books[lemma].add(book)
        if lemma not in self.examples and len(sentence) <= 160:
            self.examples[lemma] = sentence

    def rows(self, min_books):
        """Lemmas in enough chunks and books, most frequent first; examples only
        where review looks (they would make big lists' files huge)."""
        rows = [{"lemma": lemma, "count": n, "range": len(self.chunks[lemma]), "books": len(self.books[lemma]),
                 "example": ""}
                for lemma, n in self.counts.most_common()
                if len(self.chunks[lemma]) >= MIN_RANGE and len(self.books[lemma]) >= min_books]
        for row in rows[:EXAMPLES]:
            row["example"] = self.examples.get(row["lemma"], "")
        return rows


def top_forms(counter, n=2):
    """The most common forms with their counts, e.g. "fängt an 812; fing an 3"."""
    return "; ".join(f"{form} {k}" for form, k in counter.most_common(n))


def count(name, books):
    import os
    import spacy
    # The parser finds separable verb particles ("fing ... an" is anfangen) and
    # perfect auxiliaries; it also splits the sentences.
    nlp = spacy.load("de_core_news_md", disable=["ner"])
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
    nouns, verbs = Tally(), Tally()
    forms = collections.defaultdict(lambda: collections.defaultdict(collections.Counter))
    tokens = 0
    docs = nlp.pipe((p for _, p in paragraphs), batch_size=64, n_process=min(48, os.cpu_count() or 1))
    for (book, _), doc in zip(paragraphs, docs):
        for sentence in doc.sents:
            particles = {t.head.i: t.lower_ for t in sentence
                         if t.dep_ == "svp" and t.head.pos_ in ("VERB", "AUX") and t.is_alpha}
            for token in sentence:
                tokens += 1
                if token.pos_ == "NOUN":
                    lemma = spelling.get(token.lemma_, token.lemma_)
                    if re.fullmatch(r"[A-ZÄÖÜ][a-zäöüß]+", lemma):
                        nouns.add(lemma, tokens, book, sentence.text)
                elif token.pos_ in ("VERB", "AUX"):
                    particle = particles.get(token.i, "")
                    base = token.lemma_.lower()
                    if token.tag_ == "VVIZU" and base == token.lower_:
                        # A zu-infinitive the lemmatizer left alone (anzubringen):
                        # drop the zu after the particle (anbringen; zuzunehmen -> zunehmen).
                        base = re.sub(r"^(.+?)zu(?=..)", r"\1", base, count=1)
                    lemma = particle + spelling.get(base, base)
                    lemma = spelling.get(lemma, lemma)
                    # Infinitives end in -n (gehen, wandern, tun).
                    if not re.fullmatch(r"[a-zäöüß]+n", lemma):
                        continue
                    verbs.add(lemma, tokens, book, sentence.text)
                    tally_forms(token, particle, forms[lemma])
    min_books = min(MIN_RANGE, len(books))
    noun_rows, verb_rows = nouns.rows(min_books), verbs.rows(min_books)
    for row in verb_rows:
        seen = forms[row["lemma"]]
        row.update({form: top_forms(seen[form]) for form in FORMS})
    Path(name).mkdir(exist_ok=True)
    write_tsv(Path(name) / "counts.tsv", noun_rows, ["lemma", "count", "range", "books", "example"])
    write_tsv(Path(name) / "verb-counts.tsv", verb_rows, ["lemma", "count", "range", "books"] + FORMS + ["example"])
    stats = {"books": len(books), "tokens": tokens, "nouns": sum(nouns.counts.values()),
             "verbs": sum(verbs.counts.values())}
    (Path(name) / "stats.json").write_text(json.dumps(stats, indent=1) + "\n")
    print(f"{len(books)} books, {tokens} tokens, {stats['nouns']} nouns, {stats['verbs']} verbs; "
          f"{len(noun_rows)} noun and {len(verb_rows)} verb lemmas in at least {MIN_RANGE} chunks and "
          f"{min_books} books -> {name}/counts.tsv, {name}/verb-counts.tsv")


def tally_forms(token, particle, seen):
    """Notes the principal-part forms a verb token shows, as hints for review:
    third person singular present and past (with the particle after it), the
    participle, and the auxiliary (haben or sein) a participle goes with."""
    morph = token.morph
    if morph.get("VerbForm") == ["Fin"] and morph.get("Person") == ["3"] and morph.get("Number") == ["Sing"] \
            and morph.get("Mood") == ["Ind"]:
        tense = {"Pres": "present", "Past": "past"}.get("".join(morph.get("Tense")))
        if tense:
            seen[tense][token.lower_ + (f" {particle}" if particle else "")] += 1
    elif morph.get("VerbForm") == ["Part"]:
        seen["participle"][token.lower_] += 1
        head = token.head
        if token.dep_ == "oc" and head.pos_ == "AUX" and head.lemma_ in ("haben", "sein"):
            seen["perfect"][head.lemma_] += 1


def keyness(name, reference, kind):
    """Log-likelihood (Dunning G2) of each lemma in this list against a reference
    list: how much more this domain uses it than general German does."""
    import math
    counts = KINDS[kind]["counts"]
    own = read_tsv(Path(name) / counts)
    ref = {r["lemma"]: int(r["count"]) for r in read_tsv(Path(reference) / counts)}
    n1 = json.loads((Path(name) / "stats.json").read_text())[kind]
    n2 = json.loads((Path(reference) / "stats.json").read_text())[kind]
    for row in own:
        a, b = int(row["count"]), ref.get(row["lemma"], 0)
        e1, e2 = n1 * (a + b) / (n1 + n2), n2 * (a + b) / (n1 + n2)
        g2 = 2 * (a * math.log(a / e1) + (b * math.log(b / e2) if b else 0))
        row["keyness"] = round(g2 if a / n1 > b / n2 else -g2, 1)
        row["reference"] = b
    return sorted(own, key=lambda r: -r["keyness"])


# --- enrich ------------------------------------------------------------------

NOUN_QUERY = """
SELECT ?lemma ?gender ?plural ?gloss WHERE {
  VALUES ?lemma { %s }
  ?lexeme dct:language wd:Q188; wikibase:lexicalCategory wd:Q1084; wikibase:lemma ?lemma.
  OPTIONAL { ?lexeme wdt:P5185 ?gender. }
  OPTIONAL { ?lexeme ontolex:lexicalForm ?form.
             ?form wikibase:grammaticalFeature wd:Q146786, wd:Q131105; ontolex:representation ?plural. }
  OPTIONAL { ?lexeme ontolex:sense ?sense. ?sense skos:definition ?gloss. FILTER(LANG(?gloss) = "en") }
}"""

VERB_QUERY = """
SELECT ?lemma ?gloss WHERE {
  VALUES ?lemma { %s }
  ?lexeme dct:language wd:Q188; wikibase:lexicalCategory wd:Q24905; wikibase:lemma ?lemma.
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


def wikidata(lemmas, query=NOUN_QUERY):
    """Article(s), plural(s) and English glosses for German nouns, or English
    glosses for verbs, from Wikidata lexemes (CC0)."""
    found = collections.defaultdict(lambda: {"article": [], "plural": [], "english": []})
    for start in range(0, len(lemmas), 80):
        values = " ".join(f'"{lemma}"@de' for lemma in lemmas[start:start + 80])
        url = "https://query.wikidata.org/sparql?" + urllib.parse.urlencode(
            {"query": query % values, "format": "json"})
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


def ranked(name, top, reference, kind):
    """The top lemmas by count, or with a reference list by keyness against it."""
    if reference:
        return [r for r in keyness(name, reference, kind) if r["keyness"] > 0][:top]
    return read_tsv(Path(name) / KINDS[kind]["counts"])[:top]


def enrich(name, top, reference=None):
    rows = ranked(name, top, reference, "nouns")
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


def enrich_verbs(name, top, reference=None):
    rows = ranked(name, top, reference, "verbs")
    found = wikidata([r["lemma"] for r in rows], VERB_QUERY)
    draft = []
    for rank, row in enumerate(rows, 1):
        info = found.get(row["lemma"], {"english": []})
        draft.append({
            "rank": rank, "lemma": row["lemma"], "count": row["count"], "range": row["range"],
            "books": row.get("books", ""), "keyness": row.get("keyness", ""), "reference": row.get("reference", ""),
            "english": "; ".join(info["english"][:4]), **{form: row[form] for form in FORMS},
            "example": row["example"],
        })
    write_tsv(Path(name) / "verb-draft.tsv", draft,
              ["rank", "lemma", "count", "range", "books", "keyness", "reference", "english"] + FORMS + ["example"])
    print(f"{len(draft)} verbs; Wikidata found {sum(r['lemma'] in found for r in rows)} -> {name}/verb-draft.tsv")


# --- review ------------------------------------------------------------------

def walk(name, kind, extra=0):
    """Goes down the draft as select does: the decided entries the rules take,
    the undecided ones in the way, and why others were skipped. With `extra`,
    goes that many entries past the list's size."""
    files, rules = KINDS[kind], rules_for(name, kind)
    review = {r["lemma"]: r for r in read_tsv(Path(name) / files["review"])} \
        if (Path(name) / files["review"]).exists() else {}
    excluded = {}
    for other in rules.get("exclude_lists", []):
        excluded.update({r["lemma"]: f"in the {other} list" for r in read_tsv(Path(other) / files["selected"])})
    for group, lemmas in rules.get("exclude_lemmas", {}).items():
        excluded.update({lemma: group for lemma in lemmas})
    min_books = rules.get("min_books", 0)

    kept, undecided, skipped = [], [], collections.Counter()
    for row in read_tsv(Path(name) / files["draft"]):
        if len(kept) + len(undecided) == rules["size"] + extra:
            break
        lemma = row["lemma"]
        if lemma in excluded:
            skipped[excluded[lemma]] += 1
        elif row.get("books") and int(row["books"]) < min_books:
            skipped[f"in fewer than {min_books} books"] += 1
        elif lemma not in review:
            undecided.append(row)
        elif review[lemma]["decision"] == "drop":
            skipped["dropped in review"] += 1
        else:
            kept.append(review[lemma])
    return rules, kept, undecided, skipped


def pending(name, kind, outdir, batch=250, extra=0):
    """The undecided draft rows `select` stops at, in review batches; `extra`
    more, as a margin for the ones review will drop."""
    _, _, undecided, _ = walk(name, kind, extra)
    fields = list(read_tsv(Path(name) / KINDS[kind]["draft"])[0].keys())
    Path(outdir).mkdir(parents=True, exist_ok=True)
    files = []
    for start in range(0, len(undecided), batch):
        path = Path(outdir) / f"{name}-{kind}-{start // batch + 1:02d}.tsv"
        write_tsv(path, undecided[start:start + batch], fields)
        files.append(path.name)
    print(f"{len(undecided)} undecided {kind} -> {len(files)} batches in {outdir}: {' '.join(files)}")


def check_review_row(row, kind):
    """Problems with one reviewed row, as text (empty if none)."""
    problems = []
    if row["decision"] not in ("keep", "drop"):
        problems.append("decision must be keep or drop")
    elif row["decision"] == "drop":
        if not row["note"]:
            problems.append("a drop needs its reason in note")
    elif kind == "nouns":
        if row["article"] not in ("der", "die", "das"):
            problems.append("article must be der, die or das")
        if not row["english"]:
            problems.append("english is empty")
    else:
        if not row["infinitive"] or not row["english"]:
            problems.append("infinitive and english are needed")
        if len([part for part in row["forms"].split(",") if part.strip()]) != 3:
            problems.append("forms must be three parts: present, past, perfect")
    return "; ".join(problems)


def check(kind, reviewed, batch):
    """Checks a reviewed batch file against the batch it answers, without
    merging: one row per batch lemma in the same order, every row valid."""
    fields = KINDS[kind]["review_fields"]
    with open(reviewed, encoding="utf-8", newline="") as f:
        header = f.readline().rstrip("\r\n").split("\t")
    if header != fields:
        sys.exit(f"{reviewed}: the header must be exactly: " + "\t".join(fields))
    rows = read_tsv(reviewed)
    wanted = [r["lemma"] for r in read_tsv(batch)]
    got = [r["lemma"] for r in rows]
    errors = []
    if got != wanted:
        missing, extra = [w for w in wanted if w not in got], [g for g in got if g not in wanted]
        errors.append(f"lemmas differ from {batch} (missing: {missing[:20]}, extra: {extra[:20]}, "
                      f"or the order differs)")
    for line, row in enumerate(rows, 2):
        problem = check_review_row({f: (row.get(f) or "").strip() for f in fields}, kind)
        if problem:
            errors.append(f"line {line} {row['lemma']}: {problem}")
    if errors:
        sys.exit(f"{reviewed}:\n" + "\n".join(errors))
    print(f"{reviewed}: {len(rows)} rows OK ({sum(r['decision'] == 'keep' for r in rows)} kept)")


def merge(name, kind, files):
    """Adds reviewed batch files to the list's review file. Refuses rows with
    problems, lemmas reviewed twice, and lemmas the draft does not have."""
    review_path = Path(name) / KINDS[kind]["review"]
    fields = KINDS[kind]["review_fields"]
    review = read_tsv(review_path) if review_path.exists() else []
    known = {r["lemma"] for r in review}
    draft = {r["lemma"] for r in read_tsv(Path(name) / KINDS[kind]["draft"])}
    new, errors = [], []
    for file in files:
        for line, row in enumerate(read_tsv(file), 2):
            row = {f: (row.get(f) or "").strip() for f in fields}
            problem = check_review_row(row, kind)
            if row["lemma"] in known:
                problem = "reviewed already"
            elif row["lemma"] not in draft:
                problem = "not in the draft"
            if problem:
                errors.append(f"{file}:{line} {row['lemma']}: {problem}")
            else:
                new.append(row)
                known.add(row["lemma"])
    if errors:
        sys.exit("nothing merged:\n" + "\n".join(errors))
    write_tsv(review_path, review + new, fields)
    print(f"{len(new)} {kind} reviewed ({sum(r['decision'] == 'keep' for r in new)} kept) -> {review_path}")


def select(name, kind="nouns"):
    """nouns.tsv or verbs.tsv: the first `size` entries in draft order that the
    review keeps, after the rules in list.json. Lists the undecided entries that
    stand in the way (the next review batch) instead of writing anything."""
    rules, kept, undecided, skipped = walk(name, kind)
    files = KINDS[kind]
    if undecided:
        sys.exit(f"{len(undecided)} undecided {kind}; review them into {name}/{files['review']} "
                 f"(`pending` writes them as batches):\n" + " ".join(r["lemma"] for r in undecided))
    if len(kept) < rules["size"]:
        drafted = sum(1 for _ in open(Path(name) / files["draft"])) - 1
        sys.exit(f"only {len(kept)} {kind}; enrich {name} with more than {drafted}")
    for field in rules.get("unique", []):
        values = [k[field] for k in kept if k[field]]
        repeated = sorted({v for v in values if values.count(v) > 1})
        if repeated:
            sys.exit(f"repeated {field}: {repeated}")
    fields = files["selected_fields"]
    write_tsv(Path(name) / files["selected"], [{f: k[f] for f in fields} for k in kept], fields)
    if kind == "nouns":
        detail = (f"{sum(bool(k['plural']) for k in kept)} with plurals, "
                  f"{sum(bool(k['emoji']) for k in kept)} with pictures")
    else:
        detail = f"{sum(k['infinitive'].startswith('sich ') for k in kept)} reflexive"
    print(f"{len(kept)} {kind} ({detail}); skipped: {dict(skipped)} -> {name}/{files['selected']}")


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


def workbook(path, tabs):
    from openpyxl import Workbook
    book = Workbook()
    book.remove(book.active)
    for tab, rows in tabs:
        sheet = book.create_sheet(tab)
        for row in rows:
            sheet.append(row)
    book.save(path)


def build(name, title, outdir):
    if (Path(name) / "nouns.tsv").exists():
        build_nouns(name, title, outdir)
    if (Path(name) / "verbs.tsv").exists():
        build_verbs(name, title, outdir)


def build_nouns(name, title, outdir):
    nouns = read_tsv(Path(name) / "nouns.tsv")
    counted = {r["lemma"] for r in read_tsv(Path(name) / "counts.tsv")}
    for n in nouns:
        if n["lemma"] not in counted or n["article"] not in ("der", "die", "das") or not n["english"]:
            sys.exit(f"check nouns.tsv: {n}")
        n["word"] = f"{n['article']} {n['lemma']}"

    tabs = [
        (f"{title} nouns", [(n["word"], n["english"]) for n in nouns]),
        (f"{title} articles", [(n["lemma"], n["article"]) for n in nouns]),
        (f"{title} plurals", [(n["word"], f"die {n['plural']}") for n in nouns if n["plural"]]),
    ]
    workbook(Path(outdir) / f"{title} nouns.xlsx", tabs)

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


def build_verbs(name, title, outdir):
    verbs = read_tsv(Path(name) / "verbs.tsv")
    counted = {r["lemma"] for r in read_tsv(Path(name) / "verb-counts.tsv")}
    for v in verbs:
        if v["lemma"] not in counted or check_review_row({**v, "decision": "keep", "note": ""}, "verbs"):
            sys.exit(f"check verbs.tsv: {v}")
    tabs = [
        (f"{title} verbs", [(v["infinitive"], v["english"]) for v in verbs]),
        (f"{title} verb forms", [(v["infinitive"], v["forms"]) for v in verbs]),
    ]
    workbook(Path(outdir) / f"{title} verbs.xlsx", tabs)
    print(f"{len(verbs)} verbs, {sum(len(rows) for _, rows in tabs)} cards in 2 tabs -> {outdir}")


if __name__ == "__main__":
    command, args = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else ("", [])
    if command == "count":
        count(args[0], [int(book) for book in args[1:]])
    elif command in ("enrich", "enrich-verbs"):
        step = enrich if command == "enrich" else enrich_verbs
        step(args[0], int(args[1]) if len(args) > 1 else 250, args[2] if len(args) > 2 else None)
    elif command == "pending":
        pending(args[0], args[1], args[2], int(args[3]) if len(args) > 3 else 250,
                int(args[4]) if len(args) > 4 else 0)
    elif command == "check":
        check(args[0], args[1], args[2])
    elif command == "merge":
        merge(args[0], args[1], args[2:])
    elif command == "select":
        select(args[0])
    elif command == "select-verbs":
        select(args[0], "verbs")
    elif command == "build":
        build(*args)
    else:
        sys.exit(__doc__)

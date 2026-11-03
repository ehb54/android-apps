"""Chooses a list's books from Project Gutenberg's catalog into <list>/books.tsv.

Usage: choose_books.py <list> [catalog.csv]
Without a catalog file, downloads Gutenberg's current pg_catalog.csv into
cache/. The catalog grows, so a later run can pick more books than the
committed books.tsv; the committed file is what a list was counted from.

Each list's filters are in LISTS below; add an entry to start a new list.
Plain Python (no spaCy), so it also runs outside Docker.
"""
import collections
import csv
import re
import sys
import urllib.request
from pathlib import Path

CATALOG_URL = "https://www.gutenberg.org/cache/epub/feeds/pg_catalog.csv"
PER_AUTHOR = 3  # at most this many books by one author, lowest Gutenberg numbers first,
SINGLE_AUTHOR = {"goethe"}  # except in these lists, which are one author's works


def author(row):
    return re.sub(r",\s*\d.*$", "", row["Authors"].split(";")[0].strip())


def born(row):
    match = re.search(r"(\d{4})\??-", row["Authors"].split(";")[0])
    return int(match.group(1)) if match else None


def german_text(row):
    return row["Language"] == "de" and row["Type"] == "Text"


def has(field, pattern):
    return lambda row: re.search(pattern, row[field]) is not None


def not_in(*ids):
    return lambda row: row["Text#"] not in ids


def verse(row):
    return has("Bookshelves", r"Plays|Poetry|DE Lyrik|DE Drama|Picture Books")(row)


def fairy_tales(row):
    """Fairy tales, folk tales and legends in prose, translated ones included."""
    return german_text(row) and not verse(row) and (
        row["Text#"] == "77905"  # the only German Grimm text (#20050 and #20051 are audiobooks)
        or has("Title", r"Märchen|Mährchen|Sagen\b|Sagenbuch")(row)
        or has("Subjects", r"Fairy tales|Folklore|Legends|Tales --")(row)) and not_in(
        "7810",  # Hebel, calendar stories
        "14225",  # Achleitner, a crime novel
        "14962",  # Gjellerup, a novel
        "26694",  # Greinz, a humorous story
        "35794", "45189",  # Guerber, readers for learners
        "39175", "61615",  # Remizov, Bjørnson: literary stories
        "44868", "53856", "67261", "68594",  # Krauss, Schäfer, Spiess, Holz: studies
        "52483", "66770",  # local description
        "52517",  # Kayser, a biblical story
        "58520", "67897",  # Ewald, nature stories
        "68862",  # de Coster, a novel
    )(row)


LISTS = {
    # General reference list: German-literature prose, about 1870-1930.
    "core": [
        german_text,
        lambda r: "German Literature" in r["Bookshelves"],
        has("Bookshelves", r"Novels|DE Prosa|Short Stories"),
        lambda r: not has("Bookshelves", r"Plays|Poetry|DE Lyrik|DE Drama|Children|Kinderbuch|History -|"
                                         r"Philosophy|Essays|Biographies|Religion|Science|Psychiatry|Sachbuch")(r),
        lambda r: born(r) is not None and 1840 <= born(r) <= 1900,
        lambda r: not has("Title", r"Band [2-9]|Teil [2-9]|Zweiter|Dritter|Vierter")(r),
    ],
    # History non-fiction: political, military and cultural history; no translations.
    "history": [
        german_text,
        has("Bookshelves", r"History -"),
        lambda r: not has("Bookshelves", r"Novels|Plays|Poetry|DE Lyrik|DE Drama|Short Stories|Humour|Travel|"
                                         r"Art|Science|Nature|Archaeology|Health|Mathematics|How To|Music|"
                                         r"Psychiatry")(r),
        lambda r: not has("Subjects", r"Nature conservation|Natural history|Painters|Ethnology|Art\b")(r),
        lambda r: not has("Authors", r"Landesverein|Macaulay|Hedin|Gibbon|Carlyle|Prescott|Motley|Thiers|"
                                     r"Guizot")(r),
        lambda r: born(r) is None or born(r) >= 1750,
    ],
    # Fairy tales, folk tales and legends; grew out of the Grimm-only pilot list.
    "fairytales": [fairy_tales],
    # Goethe's prose: novels, stories, travel and campaign writing; no plays, poems or letters.
    "goethe": [
        german_text,
        has("Authors", r"^Goethe, Johann Wolfgang"),
        has("Bookshelves", r"DE Prosa|DE Sachbuch"),
        lambda r: not verse(r),
    ],
    # Children's books, fiction only, other than fairy tales.
    "children": [
        german_text,
        has("Bookshelves", r"DE Kinderbuch|Children"),
        lambda r: not verse(r) and not fairy_tales(r),
        lambda r: not has("Bookshelves", r"Science|Nature|Mathematics|Language Education|DE Sachbuch|"
                                         r"Biographies|How To")(r),
        not_in(
            "6341",  # E.T.A. Hoffmann, Nachtstücke: tales for adults
            "7511",  # the same Heidi as #7500
            "28279",  # sex education
            "31281",  # riddles
            "35794", "45189",  # Guerber, readers for learners
        ),
    ],
}


def main():
    name = sys.argv[1]
    catalog = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("cache/pg_catalog.csv")
    if not catalog.exists():
        catalog.parent.mkdir(exist_ok=True)
        urllib.request.urlretrieve(CATALOG_URL, catalog)
    rows = list(csv.DictReader(open(catalog, encoding="utf-8")))
    picked = [r for r in rows if all(test(r) for test in LISTS[name])]
    per_author = collections.defaultdict(list)
    for r in sorted(picked, key=lambda r: int(r["Text#"])):
        if name in SINGLE_AUTHOR or len(per_author[author(r)]) < PER_AUTHOR:
            per_author[author(r)].append(r)
    chosen = sorted((r for books in per_author.values() for r in books), key=lambda r: int(r["Text#"]))
    Path(name).mkdir(exist_ok=True)
    with open(Path(name) / "books.tsv", "w", encoding="utf-8", newline="") as f:
        writer = csv.writer(f, delimiter="\t", lineterminator="\n")
        writer.writerow(["id", "author", "title"])
        for r in chosen:
            writer.writerow([r["Text#"], author(r), re.sub(r"\s+", " ", r["Title"])[:100]])
    print(f"{len(picked)} candidates, {len(chosen)} chosen from {len(per_author)} authors -> {name}/books.tsv")


if __name__ == "__main__":
    main()

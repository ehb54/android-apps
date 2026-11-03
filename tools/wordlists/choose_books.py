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
PER_AUTHOR = 3  # at most this many books by one author, lowest Gutenberg numbers first


def author(row):
    return re.sub(r",\s*\d.*$", "", row["Authors"].split(";")[0].strip())


def born(row):
    match = re.search(r"(\d{4})\??-", row["Authors"].split(";")[0])
    return int(match.group(1)) if match else None


def german_text(row):
    return row["Language"] == "de" and row["Type"] == "Text"


def has(field, pattern):
    return lambda row: re.search(pattern, row[field]) is not None


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
    # The only German Grimm text on Gutenberg (#20050 and #20051 are audiobooks).
    "grimm": [german_text, lambda r: r["Text#"] == "77905"],
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
        if len(per_author[author(r)]) < PER_AUTHOR:
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

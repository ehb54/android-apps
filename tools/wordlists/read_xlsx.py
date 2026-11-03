"""Reads an .xlsx with the standard library only.
Usage: read_xlsx.py <file.xlsx> summary            -> per-tab overview
       read_xlsx.py <file.xlsx> dump <outdir>       -> <outdir>/<tab>.tsv per tab"""
import os
import re
import sys
import zipfile
import xml.etree.ElementTree as ET

NS = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main",
      "r": "http://schemas.openxmlformats.org/officeDocument/2006/relationships",
      "rel": "http://schemas.openxmlformats.org/package/2006/relationships"}


def col_index(ref):
    letters = re.match(r"[A-Z]+", ref).group(0)
    n = 0
    for ch in letters:
        n = n * 26 + ord(ch) - 64
    return n - 1


def text_of(node):
    return "".join(t.text or "" for t in node.iter(f"{{{NS['m']}}}t"))


def read(path):
    z = zipfile.ZipFile(path)
    shared = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall("m:si", NS):
            shared.append(text_of(si))
    rels = {r.get("Id"): r.get("Target") for r in ET.fromstring(z.read("xl/_rels/workbook.xml.rels")).findall("rel:Relationship", NS)}
    tabs = []
    for sheet in ET.fromstring(z.read("xl/workbook.xml")).find("m:sheets", NS):
        target = rels[sheet.get(f"{{{NS['r']}}}id")].lstrip("/")
        target = target if target.startswith("xl/") else "xl/" + target
        rows = []
        for row in ET.fromstring(z.read(target)).iter(f"{{{NS['m']}}}row"):
            cells = {}
            for c in row.findall("m:c", NS):
                kind, v = c.get("t"), c.find("m:v", NS)
                if kind == "s" and v is not None:
                    value = shared[int(v.text)]
                elif kind == "inlineStr":
                    value = text_of(c)
                else:
                    value = v.text if v is not None else ""
                cells[col_index(c.get("r"))] = value
            width = max(cells) + 1 if cells else 0
            rows.append([cells.get(i, "") for i in range(width)])
        tabs.append((sheet.get("name"), rows))
    return tabs


def main():
    tabs = read(sys.argv[1])
    if sys.argv[2] == "summary":
        for name, rows in tabs:
            filled = [r for r in rows if any(x.strip() for x in r)]
            both = [r for r in filled if len(r) >= 2 and r[0].strip() and r[1].strip()]
            extra = [r for r in filled if any(x.strip() for x in r[2:])]
            print(f"== {name!r}: {len(filled)} non-empty rows, {len(both)} with A and B, "
                  f"{len(filled) - len(both)} missing a side, {len(extra)} with columns past B")
            for r in filled[:4]:
                print("   ", r[:4])
            for r in [r for r in filled if r not in both][:4]:
                print("    one side empty:", r[:4])
    else:
        out = sys.argv[3]
        os.makedirs(out, exist_ok=True)
        for name, rows in tabs:
            with open(os.path.join(out, name + ".tsv"), "w", encoding="utf-8") as f:
                for r in rows:
                    f.write("\t".join(x.replace("\t", " ").replace("\n", " ") for x in r) + "\n")


main()

"""Writes a minimal but complete .xlsx (one tab per list, inline strings) with
the standard library: the Quiz app's "German starter" sample.
Usage: make_german_starter.py <out.xlsx>"""
import sys
import zipfile
from xml.sax.saxutils import escape

TABS = {
    "Numbers": [("eins", "one"), ("zwei", "two"), ("drei", "three"), ("vier", "four"), ("fünf", "five"),
                ("sechs", "six"), ("sieben", "seven"), ("acht", "eight"), ("neun", "nine"), ("zehn", "ten")],
    "Colors": [("rot", "red"), ("blau", "blue"), ("grün", "green"), ("gelb", "yellow"),
               ("schwarz", "black"), ("weiß", "white"), ("braun", "brown"), ("grau", "gray")],
}
MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
PKG = "http://schemas.openxmlformats.org/package/2006/relationships"
SHEET_TYPE = REL + "/worksheet"


def cell(ref, text):
    return f'<c r="{ref}" t="inlineStr"><is><t>{escape(text)}</t></is></c>'


with zipfile.ZipFile(sys.argv[1], "w", zipfile.ZIP_DEFLATED) as z:
    overrides = "".join(
        f'<Override PartName="/xl/worksheets/sheet{i}.xml" '
        f'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>'
        for i in range(1, len(TABS) + 1))
    z.writestr("[Content_Types].xml",
               '<?xml version="1.0" encoding="UTF-8"?>'
               '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
               '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
               '<Default Extension="xml" ContentType="application/xml"/>'
               '<Override PartName="/xl/workbook.xml" '
               'ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>'
               + overrides + '</Types>')
    z.writestr("_rels/.rels",
               f'<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="{PKG}">'
               f'<Relationship Id="rId1" Type="{REL}/officeDocument" Target="xl/workbook.xml"/></Relationships>')
    sheets = "".join(f'<sheet name="{escape(name)}" sheetId="{i}" r:id="rId{i}"/>'
                     for i, name in enumerate(TABS, 1))
    z.writestr("xl/workbook.xml",
               f'<?xml version="1.0" encoding="UTF-8"?><workbook xmlns="{MAIN}" xmlns:r="{REL}">'
               f'<sheets>{sheets}</sheets></workbook>')
    rels = "".join(f'<Relationship Id="rId{i}" Type="{SHEET_TYPE}" Target="worksheets/sheet{i}.xml"/>'
                   for i in range(1, len(TABS) + 1))
    z.writestr("xl/_rels/workbook.xml.rels",
               f'<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="{PKG}">{rels}</Relationships>')
    for i, rows in enumerate(TABS.values(), 1):
        data = "".join(f'<row r="{n}">{cell(f"A{n}", a)}{cell(f"B{n}", b)}</row>'
                       for n, (a, b) in enumerate(rows, 1))
        z.writestr(f"xl/worksheets/sheet{i}.xml",
                   f'<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="{MAIN}">'
                   f'<sheetData>{data}</sheetData></worksheet>')

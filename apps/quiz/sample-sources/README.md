# Sources of the Quiz samples

The sample files and where each comes from. The small ones are built into the
app (`../src/main/assets/samples/`); the long word lists are online only
(`../samples/`). Both are listed in `../samples/catalog.json`, offered on the
app's Sample quizzes screen, and on the download page. After changing any of
them, regenerate the catalog (see `../DESIGN.md`).

| Sample | Made by |
|---|---|
| `Amino acids.qzx`, `Chemical symbols.qzx`, `French words.qzx` | written by hand |
| `Shapes.zip` | `python3 make_shapes.py Shapes.zip` (pure Python; draws the pictures) |
| `German starter.xlsx` | `python3 make_german_starter.py "German starter.xlsx"` |
| `Core …`, `History …`, `Fairy tales …`, `Goethe …`, `Children's books …` (nouns and verbs `.xlsx`, pictures `.zip`) | the word-list pipeline in `tools/wordlists/` (see its README) |

Both scripts reproduce the committed samples exactly. `tools/wordlists/read_xlsx.py`
prints a summary of any `.xlsx` (tabs, rows, rows missing a side).

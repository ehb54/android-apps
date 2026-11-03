# Sources of the Quiz samples

The files in `../src/main/assets/samples/` (offered by "Add sample quizzes" and
on the download page) and where each comes from:

| Sample | Made by |
|---|---|
| `Amino acids.qzx`, `Chemical symbols.qzx`, `French words.qzx` | written by hand |
| `Shapes.zip` | `python3 make_shapes.py Shapes.zip` (pure Python; draws the pictures) |
| `German starter.xlsx` | `python3 make_german_starter.py "German starter.xlsx"` |
| `Grimm …`, `Core …`, `History …` (`.xlsx` and pictures `.zip`) | the word-list pipeline in `tools/wordlists/` (see its README) |

Both scripts reproduce the committed samples exactly. `tools/wordlists/read_xlsx.py`
prints a summary of any `.xlsx` (tabs, rows, rows missing a side).

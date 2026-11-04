# Pictures for the word lists

Makes original pictures for the word lists' picture cards (picture → German),
generated locally on a Mac with Apple Silicon, to replace the Noto Emoji
pictures the lists use now. A picture is made for every noun and verb that one
picture can show plainly; the rest keep their emoji, if any.

Everything needed to remake or extend the pictures is kept: the plan (one
prompt per word, or why a word gets none), every picture's model, settings,
prompt and seed, and the pick per word. The images themselves are not
committed: `plan.tsv` and `picks.tsv` are the source of truth, and any picked
picture can be regenerated from its row. `PLAN.md` is the guide for writing a
plan.

`pictures.py` does the work. It runs in a Python venv in this folder
(`.venv/`, git-ignored, made by `setup.sh` from `requirements.txt`), through
`run.sh`, which reads `tools/local.env` for the machine's settings:

| Setting in `tools/local.env` | What |
|---|---|
| `PICTURES_PYTHON` | Python 3.11 or newer to make the venv with (default `python3`) |
| `HF_HOME` | Hugging Face cache for the model weights (about 9–12 GB per model) |
| `TMPDIR`, `PIP_CACHE_DIR`, `XDG_CACHE_HOME` | optional: where temp files and caches go |
| `HF_XET_HIGH_PERFORMANCE=1` | optional: more parallel download connections |

## A list's folder

| File | What | Made by |
|---|---|---|
| `plan.tsv` | every noun and verb of the list: `picturable` (yes, scene, no), the prompt, and a note (why not, or which near-synonyms the prompt keeps apart) | by hand (`PLAN.md`), checked by `merge` |
| `raw/<model>/` | generated pictures `<kind>-<word>-<seed>.png` and `log.tsv` (model, weights and revision, mflux version, steps, guidance, size, seed, full prompt, seconds) | `generate` (git-ignored) |
| `picks.tsv` | the picture chosen per word: file, prompt, seed, decision, note | by hand, from contact sheets |

## Steps

Every step skips work already done, so a long run can be stopped and started
again.

1. `tools/pictures/run.sh download <model>...` fetches a model's weights at the
   pinned revision (`MODELS` in `pictures.py`) into `HF_HOME`.
2. **Plan by hand**, following `PLAN.md`.
   `run.sh pending <list> work/plan` writes the list's words that `plan.tsv`
   does not have yet as batch files (rank, lemma, kind, card, English gloss,
   emoji, verb forms, example sentence from the books); plan each batch into a
   file with the plan columns, check it with `run.sh check <planned> <batch>`,
   and add it with `run.sh merge <list> <files>`. Batches can be planned in
   parallel (by people or AI agents); `work/` (git-ignored) is the place for them.
3. `run.sh generate <list> <model> <words> [seeds] [size]` makes the pictures:
   every planned word (`all`) or the words in a file, seeds `1-4` by default.
   Run long batches in the background with `nohup caffeinate -i`.
4. `run.sh sheet <list> <html> <words> <model>[:<seeds>]...` writes a
   self-contained contact sheet: one row per word with its card, gloss and
   prompt, one column per model and seed, pictures at card size (256 px).

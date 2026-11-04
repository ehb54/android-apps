# Pictures for the word lists

Makes original pictures for the word lists' picture cards (picture → German),
generated locally on a Mac with Apple Silicon, to replace the Noto Emoji
pictures the lists use now. A picture is made for every noun and verb that one
picture can show plainly; nouns without one keep their emoji, if any.

Everything needed to remake or extend the pictures is kept: the plan (one
prompt per word, or why a word gets none), every picture's model, settings,
prompt and seed, and the pick per word. The images themselves are not
committed: `plan.tsv` and `picks.tsv` are the source of truth, and `restore`
regenerates any picked picture from its row. `PLAN.md` is the guide for
writing a plan.

`pictures.py` does the work. It runs in a Python venv in this folder
(`.venv/`, git-ignored, made by `setup.sh` from `requirements.txt`) through
`run.sh`, which reads `tools/local.env` for the machine's settings:

| Setting in `tools/local.env` | What |
|---|---|
| `PICTURES_PYTHON` | Python 3.11 or newer to make the venv with (default `python3`) |
| `HF_HOME` | Hugging Face cache for the model weights (8–12 GB per model) |
| `TMPDIR`, `PIP_CACHE_DIR`, `XDG_CACHE_HOME` | optional: where temp files and caches go |
| `HF_HUB_DISABLE_XET=1` | optional: plain HTTPS downloads, which were about three times faster than the default xet transfer on one slow link (its high-performance mode flooded the link) |

## Models

Compared in a pilot of 40 words × 4 seeds at 768 px on an M5 Max (MLX 0.32,
mflux 0.24, 8-bit weights from mflux-community, which keep the original
license). The pictures are published on the download site, so the license
must allow redistributing outputs; all three are Apache 2.0.

| Model | Weights | Steps | Time per picture | Pilot |
|---|---|---|---|---|
| FLUX.2 [klein] 4B (Black Forest Labs) | `mflux-community/flux2-klein-4b-mflux-q8` | 4 | 3.3 s | nearly as good on objects and 5× faster than ERNIE; colourful, no lettering on Zeitung or Uhr; more anatomy slips (a sixth finger, two hands holding two pens) and less of the action in verbs |
| Z-Image-Turbo (Tongyi-MAI) | `mflux-community/z-image-turbo-mflux-q8` | 9 | 9.4 s | crisp icons, but some prompts turn into black silhouettes or line art, pseudo-text on Zeitung and digits on Uhr, and the four seeds look almost alike |
| ERNIE-Image-Turbo (Baidu) | `mflux-community/ernie-image-turbo-mflux-q8` | 8 | 15.7 s | **recommended**: best on verbs and scenes (a whole person writing, eating at a table, getting up), no lettering; fails on stellen; 5× slower than klein |

Not compared: SDXL (OpenRAIL++-M: its use restrictions travel with the
outputs, and mflux does not run it), FLUX.2 [klein] 9B (FLUX Non-Commercial
License), FLUX 3 [klein] (gated, under Black Forest Labs' own "FLUX
Kommunity" license rather than Apache 2.0), Qwen-Image (Apache 2.0, but 20B
parameters: far more than flat icons need).
The model, its weights revision and settings are in `MODELS` in `pictures.py`;
each picture's are also in its log row.

## A list's folder

| File | What | Made by |
|---|---|---|
| `plan.tsv` | every noun and verb of the list: `picturable` (yes, scene, no), the prompt, and a note (why not, or which near-synonyms the prompt keeps apart) | by hand (`PLAN.md`), checked by `merge` |
| `pilot.txt` | the 40 words of the model pilot | by hand |
| `raw/<model>/` | generated pictures `<kind>-<word>-<seed>.png` and `log.tsv` (model, weights and revision, mflux version, steps, guidance, size, seed, full prompt, seconds) | `generate` (git-ignored) |
| `picks.tsv` | **the decision per pictured word**: `pick` (the file on the card, with its full prompt and seed), `retry` or `drop`, and a note | by hand from contact sheets, through `pick` |

## Steps

Every step skips work already done, so a long run can be stopped and started
again.

1. `tools/pictures/run.sh download <model>...` fetches a model's weights at the
   pinned revision into `HF_HOME`.
2. **Plan by hand**, following `PLAN.md`.
   `run.sh pending <list> work/plan` writes the list's words that `plan.tsv`
   does not have yet as batch files (rank, lemma, kind, card, English gloss,
   emoji, verb forms, example sentence from the books); plan each batch into a
   file with the plan columns, check it with `run.sh check <planned> <batch>`,
   and add it with `run.sh merge <list> <files>`. Batches can be planned in
   parallel (by people or AI agents); `work/` (git-ignored) is the place for
   them. Read the whole plan afterwards: batches planned apart disagree on
   near-synonyms (who gets Arzt or Doktor) and on borderline rules.
3. `run.sh generate <list> <model> <words> [seeds] [size]` makes the pictures
   of every planned word (`all`) or of the words in a file (`pilot.txt`),
   seeds `1-4` and the model's size by default. Run long batches in the
   background with `nohup caffeinate -i`; the Mac's speed drops when it gets
   hot, and a stopped run continues where it left off.
4. **Review.** `run.sh mosaic <list> <png> <words> <start> <count> <model>[:<seeds>]...`
   draws a contact sheet as one PNG (for a quick look, or for an AI agent that
   reads images); `run.sh sheet <list> <html> <words> <model>[:<seeds>]...`
   writes a self-contained HTML page for people: one row per word with its
   card, gloss and prompt, one column per model and seed, the pictures as the
   card will show them (white margins trimmed, 256 px). Record decisions in a
   TSV with the columns `lemma kind file decision note` (`file` is
   `<model>/<picture>` for a pick) and add them with
   `run.sh pick <list> <files>`, which fills in the prompt and seed from the
   log. For a `retry`, rewrite the word's prompt in `plan.tsv`, generate it
   with new seeds (`5-8`), and decide again.
5. `run.sh restore <list>` regenerates picked pictures that `raw/` does not
   have (on another Mac, or after `raw/` was cleared). Pictures come out the
   same with the same mflux and MLX versions; across versions they may differ
   slightly.
6. `tools/wordlists/run.sh build <list> <Title> ../../apps/quiz/samples`
   (see `../wordlists/README.md`) puts the picked pictures into
   `<Title> pictures.zip` (nouns, with emoji where a noun has no pick) and
   `<Title> verb pictures.zip` (picture → infinitive): white margins trimmed,
   256 px, WebP. The zips' `CREDITS.txt` names the model and its license.

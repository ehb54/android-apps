"""Original pictures for the word lists, generated locally on Apple Silicon (MLX, mflux).

Steps, each resumable (work already done is skipped). <list> is a word list
(tools/wordlists/<list>/); this tool's files for it are in <list>/ here.

  download <model>...               a model's weights at the pinned revision, into the
                                    Hugging Face cache (HF_HOME)
  pending <list> <dir> [batch]      the list's nouns and verbs that plan.tsv does not have
                                    yet, as plan batch files <dir>/<list>-<kind>-NN.tsv
  check  <planned> <batch>          checks one planned batch file against its batch
  merge  <list> <file>...           adds planned batch files to <list>/plan.tsv, checking
                                    each row
  generate <list> <model> <words> [seeds] [size]
                                    pictures of the planned words (yes and scene) into
                                    <list>/raw/<model>/<kind>-<word>-<seed>-<hash>.png (hash of
                                    the prompt, so a new prompt makes new pictures), seeds `1-4`
                                    by default, at the model's size; each picture's model,
                                    revision, settings, prompt and seed go to
                                    <list>/raw/<model>/log.tsv
  sheet  <list> <html> <words> <model>[:<seeds>]...
                                    a contact sheet: one row per word, one column per
                                    model and seed, pictures at card size (256 px) in the page
  mosaic <list> <png> <words> <start> <count> <model>[:<seeds>]...
                                    the same as one PNG, for `count` words from `start`
                                    (for a quick look, or review by an AI agent)

  pick   <list> <decisions>...      adds review decisions (lemma, kind, file, decision, note;
                                    decision pick, retry or drop) to <list>/picks.tsv
  restore <list>                    regenerates picked pictures missing from raw/, from
                                    picks.tsv (words.py build needs them)

<words> is `all` (every planned yes and scene), or a file with one word per line
as `<kind> <lemma>` (noun Haus, verb laufen); `#` starts a comment.

Run through run.sh, which sets up the environment from tools/local.env. See
README.md, and PLAN.md for writing the plan.
"""
import base64
import datetime
import hashlib
import io
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORDLISTS = HERE.parent / "wordlists"
sys.path.insert(0, str(WORDLISTS))
from words import read_tsv, write_tsv  # noqa: E402  (the word lists' TSV helpers)

STYLE = "simple flat illustration, plain white background, no text or letters, centered subject"
PLAN_FIELDS = ["lemma", "kind", "picturable", "prompt", "note"]
BATCH_FIELDS = ["rank", "lemma", "kind", "card", "english", "emoji", "forms", "example"]
# Per kind: the word list's selected file, its counts (for the example sentence),
# and what the card answers.
KINDS = {
    "noun": {"selected": "nouns.tsv", "counts": "counts.tsv",
             "card": lambda r: f"{r['article']} {r['lemma']}"},
    "verb": {"selected": "verbs.tsv", "counts": "verb-counts.tsv",
             "card": lambda r: r["infinitive"]},
}

# The models compared in step 1: the original release (`repo`, its license), and the
# weights used here (`weights` at `revision`): mflux-community's 8-bit conversions,
# which keep the original license. `mflux` is the mflux model name; steps and
# guidance are each model's fast settings (its distilled default); size is the
# pixel size generated (square), which the cards trim and scale to CARD_SIZE.
MODELS = {
    "klein": {
        "name": "FLUX.2 [klein] 4B", "mflux": "flux2-klein-4b",
        "repo": "black-forest-labs/FLUX.2-klein-4B", "license": "Apache-2.0",
        "weights": "mflux-community/flux2-klein-4b-mflux-q8",
        "revision": "755dc213d6767d4a4eae9291dd3f6e829454177e",
        "steps": 4, "guidance": 1.0, "size": 768,
    },
    "zimage": {
        "name": "Z-Image-Turbo", "mflux": "z-image-turbo",
        "repo": "Tongyi-MAI/Z-Image-Turbo", "license": "Apache-2.0",
        "weights": "mflux-community/z-image-turbo-mflux-q8",
        "revision": "4ccff28917346aa9daae49cd3c477cf5260a242c",
        "steps": 9, "guidance": None, "size": 768,
    },
    "ernie": {
        "name": "ERNIE-Image-Turbo", "mflux": "ernie-image-turbo",
        "repo": "baidu/ERNIE-Image-Turbo", "license": "Apache-2.0",
        "weights": "mflux-community/ernie-image-turbo-mflux-q8",
        "revision": "1bf0886002924e3b2af5febac79726728a1d4657",
        "steps": 8, "guidance": 1.0, "size": 768,
    },
}


# --- download ----------------------------------------------------------------

def weights_path(model, download=False):
    """The local snapshot of a model's weights (downloaded first if asked)."""
    from huggingface_hub import snapshot_download
    m = MODELS[model]
    return Path(snapshot_download(repo_id=m["weights"], revision=m["revision"],
                                  local_files_only=not download))


def download(models):
    for model in models:
        m = MODELS[model]
        path = weights_path(model, download=True)
        print(f"{m['name']} ({m['weights']} @ {m['revision'][:10]}) -> {path}", flush=True)


# --- plan --------------------------------------------------------------------

def list_words(name):
    """The list's nouns, then its verbs, as plan batch rows."""
    words = []
    for kind, k in KINDS.items():
        examples = {r["lemma"]: r["example"] for r in read_tsv(WORDLISTS / name / k["counts"])}
        for rank, row in enumerate(read_tsv(WORDLISTS / name / k["selected"]), 1):
            words.append({"rank": rank, "lemma": row["lemma"], "kind": kind, "card": k["card"](row),
                          "english": row["english"], "emoji": row.get("emoji", ""),
                          "forms": row.get("forms", ""), "example": examples.get(row["lemma"], "")})
    return words


def read_plan(name):
    path = HERE / name / "plan.tsv"
    return read_tsv(path) if path.exists() else []


def pending(name, outdir, batch=250):
    planned = {(r["kind"], r["lemma"]) for r in read_plan(name)}
    Path(outdir).mkdir(parents=True, exist_ok=True)
    files = []
    for kind in KINDS:
        todo = [w for w in list_words(name) if w["kind"] == kind and (kind, w["lemma"]) not in planned]
        for start in range(0, len(todo), batch):
            path = Path(outdir) / f"{name}-{kind}s-{start // batch + 1:02d}.tsv"
            write_tsv(path, todo[start:start + batch], BATCH_FIELDS)
            files.append(path.name)
    print(f"{len(files)} batches in {outdir}: {' '.join(files)}")


def check_plan_row(row):
    """Problems with one plan row, as text (empty if none)."""
    problems = []
    if row["kind"] not in KINDS:
        problems.append("kind must be noun or verb")
    if row["picturable"] not in ("yes", "scene", "no"):
        problems.append("picturable must be yes, scene or no")
    elif row["picturable"] == "no":
        if row["prompt"]:
            problems.append("a no has no prompt")
        if not row["note"]:
            problems.append("a no needs its reason in note")
    else:
        words = len(row["prompt"].split())
        if not 3 <= words <= 40:
            problems.append(f"prompt must be 3-40 words, not {words}")
        if any(s in row["prompt"].lower() for s in ("white background", "flat illustration", "no text")):
            problems.append("prompt repeats the shared style")
    return "; ".join(problems)


def clean(row):
    return {f: " ".join((row.get(f) or "").split()) for f in PLAN_FIELDS}


def check(planned, batch):
    """Checks a planned batch file against the batch it answers, without merging."""
    with open(planned, encoding="utf-8", newline="") as f:
        header = f.readline().rstrip("\r\n").split("\t")
    if header != PLAN_FIELDS:
        sys.exit(f"{planned}: the header must be exactly: " + "\t".join(PLAN_FIELDS))
    rows = [clean(r) for r in read_tsv(planned)]
    wanted = [(r["kind"], r["lemma"]) for r in read_tsv(batch)]
    got = [(r["kind"], r["lemma"]) for r in rows]
    errors = []
    if got != wanted:
        missing, extra = [w for w in wanted if w not in got], [g for g in got if g not in wanted]
        errors.append(f"lemmas differ from {batch} (missing: {missing[:20]}, extra: {extra[:20]}, "
                      f"or the order differs)")
    for line, row in enumerate(rows, 2):
        problem = check_plan_row(row)
        if problem:
            errors.append(f"line {line} {row['lemma']}: {problem}")
    if errors:
        sys.exit(f"{planned}:\n" + "\n".join(errors))
    counts = {p: sum(r["picturable"] == p for r in rows) for p in ("yes", "scene", "no")}
    print(f"{planned}: {len(rows)} rows OK {counts}")


def merge(name, files):
    """Adds planned batch files to plan.tsv, kept in list order. Refuses rows with
    problems, words planned twice, words the list does not have, and a prompt
    used twice within a kind."""
    plan = read_plan(name)
    known = {(r["kind"], r["lemma"]) for r in plan}
    order = {(w["kind"], w["lemma"]): i for i, w in enumerate(list_words(name))}
    new, errors = [], []
    for file in files:
        for line, row in enumerate(read_tsv(file), 2):
            row = clean(row)
            key = (row["kind"], row["lemma"])
            problem = check_plan_row(row)
            if key in known:
                problem = "planned already"
            elif key not in order:
                problem = "not in the list"
            if problem:
                errors.append(f"{file}:{line} {row['lemma']}: {problem}")
            else:
                new.append(row)
                known.add(key)
    prompts = {}
    for row in plan + new:
        if row["prompt"]:
            other = prompts.setdefault((row["kind"], row["prompt"].lower()), row["lemma"])
            if other != row["lemma"]:
                errors.append(f"{row['lemma']} and {other} share a prompt")
    if errors:
        sys.exit("nothing merged:\n" + "\n".join(errors))
    (HERE / name).mkdir(exist_ok=True)
    plan = sorted(plan + new, key=lambda r: order[(r["kind"], r["lemma"])])
    write_tsv(HERE / name / "plan.tsv", plan, PLAN_FIELDS)
    counts = {p: sum(r["picturable"] == p for r in new) for p in ("yes", "scene", "no")}
    print(f"{len(new)} words planned {counts} -> {name}/plan.tsv ({len(plan)} of {len(order)})")


# --- generate ----------------------------------------------------------------

LOG_FIELDS = ["file", "kind", "lemma", "model", "repo", "revision", "mflux", "steps", "guidance",
              "size", "seed", "prompt", "seconds", "time"]
CARD_SIZE = 256              # pixels, square: what the quiz zips carry


def slug(lemma):
    """An ASCII file name part for a lemma (Schlüssel -> Schluessel)."""
    for a, b in (("ä", "ae"), ("ö", "oe"), ("ü", "ue"), ("Ä", "Ae"), ("Ö", "Oe"), ("Ü", "Ue"), ("ß", "ss")):
        lemma = lemma.replace(a, b)
    return "".join(c if c.isascii() and (c.isalnum() or c == "-") else "_" for c in lemma)


def picture_name(kind, lemma, seed, prompt):
    """<kind>-<word>-<seed>-<prompt hash>.png: a changed prompt makes new files,
    so generate never mistakes an old picture for the new prompt's."""
    return f"{kind}-{slug(lemma)}-{seed}-{hashlib.sha1(prompt.encode()).hexdigest()[:6]}.png"


def seed_range(spec):
    first, _, last = spec.partition("-")
    return list(range(int(first), int(last or first) + 1))


def chosen_words(name, words):
    """The plan rows for `words` (`all` or a word file), in plan order."""
    plan = [r for r in read_plan(name) if r["picturable"] in ("yes", "scene")]
    if words == "all":
        return plan
    wanted = []
    for line in Path(words).read_text(encoding="utf-8").splitlines():
        line = line.split("#")[0].strip()
        if line:
            kind, _, lemma = line.partition(" ")
            wanted.append((kind, lemma.strip()))
    by_key = {(r["kind"], r["lemma"]): r for r in read_plan(name)}
    missing = [w for w in wanted if w not in by_key or not by_key[w]["prompt"]]
    if missing:
        sys.exit(f"{words}: not planned with a prompt: {missing}")
    return [by_key[w] for w in wanted]


def full_prompt(prompt):
    return f"{prompt}, {STYLE}"


def load_model(model):
    from mflux.models.common.resolution.config_resolution import ConfigResolution
    config = ConfigResolution.resolve_restricted(None, MODELS[model]["mflux"])
    path = str(weights_path(model))
    if model == "klein":
        from mflux.models.flux2.variants.txt2img.flux2_klein import Flux2Klein
        return Flux2Klein(model_config=config, model_path=path)
    if model == "zimage":
        from mflux.models.z_image.variants.z_image import ZImage
        return ZImage(model_config=config, model_path=path)
    from mflux.models.ernie_image.variants.txt2img.ernie_image import ErnieImage
    return ErnieImage(model_config=config, model_path=path)


def make_picture(pipeline, model, prompt, seed, size):
    """Generates one picture; returns the PIL image and the seconds it took."""
    m = MODELS[model]
    start = time.time()
    image = pipeline.generate_image(seed=seed, prompt=prompt, num_inference_steps=m["steps"],
                                    width=size, height=size, guidance=m["guidance"]).image
    return image, time.time() - start


def save_picture(name, model, row, seed, size, prompt, image, seconds):
    """Writes a generated picture into raw/<model>/ (never half-written) and logs it."""
    import importlib.metadata
    m = MODELS[model]
    outdir = HERE / name / "raw" / model
    outdir.mkdir(parents=True, exist_ok=True)
    file = picture_name(row["kind"], row["lemma"], seed, prompt)
    temporary = outdir / f"{file}.tmp"
    image.save(temporary, "PNG")
    temporary.replace(outdir / file)
    entry = {"file": file, "kind": row["kind"], "lemma": row["lemma"], "model": m["name"],
             "repo": m["weights"], "revision": m["revision"], "mflux": importlib.metadata.version("mflux"),
             "steps": m["steps"], "guidance": "" if m["guidance"] is None else m["guidance"], "size": size,
             "seed": seed, "prompt": prompt, "seconds": f"{seconds:.1f}",
             "time": datetime.datetime.now().isoformat(timespec="seconds")}
    log_path = outdir / "log.tsv"
    new_log = not log_path.exists()
    with open(log_path, "a", encoding="utf-8", newline="") as f:
        if new_log:
            f.write("\t".join(LOG_FIELDS) + "\n")
        f.write("\t".join(str(entry[k]).replace("\t", " ") for k in LOG_FIELDS) + "\n")
    return file


def generate(name, model, words, seeds="1-4", size=None):
    m = MODELS[model]
    size = size or m["size"]
    outdir = HERE / name / "raw" / model
    todo = [(row, seed) for row in chosen_words(name, words) for seed in seed_range(seeds)
            if not (outdir / picture_name(row["kind"], row["lemma"], seed, full_prompt(row["prompt"]))).exists()]
    print(f"{m['name']}: {len(todo)} pictures to make -> {outdir}", flush=True)
    if not todo:
        return
    pipeline = load_model(model)
    for done, (row, seed) in enumerate(todo, 1):
        prompt = full_prompt(row["prompt"])
        image, seconds = make_picture(pipeline, model, prompt, seed, size)
        file = save_picture(name, model, row, seed, size, prompt, image, seconds)
        print(f"{done}/{len(todo)} {file} {seconds:.1f} s", flush=True)


# --- picks -------------------------------------------------------------------

PICK_FIELDS = ["lemma", "kind", "file", "prompt", "seed", "decision", "note"]
DECISION_FIELDS = ["lemma", "kind", "file", "decision", "note"]
# pick: `file` (<model>/<picture>) goes on the card. retry: no good picture yet;
# rewrite the plan's prompt and generate the word again. drop: no picture (the
# word keeps its emoji, if any).
DECISIONS = ("pick", "retry", "drop")
CARD_QUALITY = 82            # WebP quality of the card pictures


def read_picks(name):
    path = HERE / name / "picks.tsv"
    return read_tsv(path) if path.exists() else []


def logged(name, model):
    """Each generated picture's log row, by file name (the latest, if made twice)."""
    path = HERE / name / "raw" / model / "log.tsv"
    return {r["file"]: r for r in read_tsv(path)} if path.exists() else {}


def pick(name, files):
    """Adds review decisions to picks.tsv, replacing earlier ones for the same
    word. A pick's prompt and seed are taken from the generation log."""
    plan = {(r["kind"], r["lemma"]): r for r in read_plan(name)}
    picks = {(r["kind"], r["lemma"]): r for r in read_picks(name)}
    logs, errors, new = {}, [], 0
    for file in files:
        for line, row in enumerate(read_tsv(file), 2):
            row = {f: " ".join((row.get(f) or "").split()) for f in DECISION_FIELDS}
            key, where = (row["kind"], row["lemma"]), f"{file}:{line} {row['lemma']}"
            problem = ""
            entry = {**row, "prompt": "", "seed": ""}
            if key not in plan or plan[key]["picturable"] == "no":
                problem = "not planned for a picture"
            elif row["decision"] not in DECISIONS:
                problem = f"decision must be one of {', '.join(DECISIONS)}"
            elif row["decision"] == "pick":
                model, _, picture = row["file"].partition("/")
                log = logs.setdefault(model, logged(name, model)).get(picture) if model in MODELS else None
                if not log or not (HERE / name / "raw" / row["file"]).exists():
                    problem = f"{row['file']} is not a generated picture (<model>/<picture>)"
                elif (log["kind"], log["lemma"]) != key:
                    problem = f"{row['file']} is a picture of {log['lemma']}"
                else:
                    entry.update(prompt=log["prompt"], seed=log["seed"])
            elif row["file"]:
                problem = "only a pick names a file"
            elif not row["note"]:
                problem = f"a {row['decision']} needs its reason in note"
            if problem:
                errors.append(f"{where}: {problem}")
            else:
                picks[key] = entry
                new += 1
    if errors:
        sys.exit("nothing recorded:\n" + "\n".join(errors))
    order = {key: i for i, key in enumerate(plan)}
    rows = sorted(picks.values(), key=lambda r: order[(r["kind"], r["lemma"])])
    write_tsv(HERE / name / "picks.tsv", rows, PICK_FIELDS)
    counts = {d: sum(r["decision"] == d for r in rows) for d in DECISIONS}
    undecided = sum(p["picturable"] != "no" for p in plan.values()) - len(rows)
    print(f"{new} decisions -> {name}/picks.tsv {counts}, {undecided} planned words undecided")


def restore(name):
    """Regenerates the picked pictures that raw/ does not have (on another
    machine, or after raw/ was cleared) from the prompt and seed in picks.tsv."""
    missing = [r for r in read_picks(name)
               if r["decision"] == "pick" and not (HERE / name / "raw" / r["file"]).exists()]
    print(f"{len(missing)} picked pictures to regenerate", flush=True)
    for model in sorted({r["file"].split("/")[0] for r in missing}):
        pipeline = load_model(model)
        for r in [r for r in missing if r["file"].startswith(f"{model}/")]:
            image, seconds = make_picture(pipeline, model, r["prompt"], int(r["seed"]), MODELS[model]["size"])
            print(save_picture(name, model, r, int(r["seed"]), MODELS[model]["size"], r["prompt"], image, seconds),
                  flush=True)


def card_files(name):
    """The picked pictures as the quiz zips carry them: {kind: {lemma: (file
    name, bytes)}}, WebP at CARD_SIZE, and the credits text for them. Used by
    tools/wordlists/words.py build."""
    cards, models = {kind: {} for kind in KINDS}, set()
    for r in read_picks(name):
        if r["decision"] != "pick":
            continue
        path = HERE / name / "raw" / r["file"]
        if not path.exists():
            sys.exit(f"{path} is missing: run tools/pictures/run.sh restore {name}")
        out = io.BytesIO()
        card_picture(path).save(out, "WEBP", quality=CARD_QUALITY, method=6)
        cards[r["kind"]][r["lemma"]] = (f"{r['kind']}-{slug(r['lemma'])}.webp", out.getvalue())
        models.add(r["file"].split("/")[0])
    credits = "".join(
        f"Pictures: generated with {MODELS[m]['name']} (https://huggingface.co/{MODELS[m]['repo']}, "
        f"{MODELS[m]['license']} license; MLX 8-bit weights {MODELS[m]['weights']} at revision "
        f"{MODELS[m]['revision'][:10]}) using mflux, from prompts written for this list. Prompts, "
        f"seeds and the pick per word: tools/pictures/{name}/ in https://github.com/ehb54/android-apps.\n"
        for m in sorted(models))
    return cards, credits


# --- contact sheets ----------------------------------------------------------

def card_picture(path, size=CARD_SIZE, margin=0.06):
    """The picture as it goes on a card, as an RGB PIL image: the white margin
    around the subject trimmed to `margin` of the side, square, `size` pixels."""
    from PIL import Image, ImageChops
    with Image.open(path) as image:
        image = image.convert("RGB")
    # Not white: any channel below 236 (faint off-white haze at the edges counts as white).
    r, g, b = image.split()
    mask = ImageChops.darker(ImageChops.darker(r, g), b).point(lambda v: 255 if v < 236 else 0)
    box = mask.getbbox() or (0, 0, image.width, image.height)
    side = max(box[2] - box[0], box[3] - box[1])
    side = min(round(side * (1 + 2 * margin)), max(image.size))
    cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
    left = round(min(max(cx - side / 2, 0), image.width - side))
    top = round(min(max(cy - side / 2, 0), image.height - side))
    square = Image.new("RGB", (side, side), "white")
    square.paste(image.crop((max(left, 0), max(top, 0), left + side, top + side)), (max(-left, 0), max(-top, 0)))
    return square.resize((size, size), Image.LANCZOS)


def data_url(image, quality=85):
    out = io.BytesIO()
    image.save(out, "WEBP", quality=quality)
    return "data:image/webp;base64," + base64.b64encode(out.getvalue()).decode()


def sheet(name, html, words, columns, title=None, intro=""):
    """Writes a self-contained HTML contact sheet. `columns` are `model` or
    `model:1-4`; a missing picture leaves an empty cell."""
    import html as h
    rows = chosen_words(name, words)
    info = {(w["kind"], w["lemma"]): w for w in list_words(name)}
    cols = []
    for column in columns:
        model, _, seeds = column.partition(":")
        cols += [(model, seed) for seed in seed_range(seeds or "1-4")]
    shown = CARD_SIZE if len(cols) <= 4 else 168          # displayed size; the page holds 256 px
    head = "".join(f"<th>{h.escape(MODELS[m]['name'])}<br>seed {s}</th>" for m, s in cols)
    body = []
    for row in rows:
        w = info[(row["kind"], row["lemma"])]
        cells = []
        for model, seed in cols:
            path = HERE / name / "raw" / model / picture_name(row["kind"], row["lemma"], seed, full_prompt(row["prompt"]))
            cells.append(f'<td><img src="{data_url(card_picture(path))}" width="{shown}" '
                         f'height="{shown}" alt="{h.escape(row["lemma"])} {seed}"></td>'
                         if path.exists() else "<td></td>")
        body.append(f'<tr><th class="word"><b>{h.escape(w["card"])}</b><br>{h.escape(w["english"])}'
                    f'<br><small>{row["kind"]} {w["rank"]} · {row["picturable"]}</small>'
                    f'<p>{h.escape(row["prompt"])}</p></th>{"".join(cells)}</tr>')
    title = title or f"{name} pictures"
    Path(html).write_text(f"""<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>{h.escape(title)}</title>
<style>
body {{ font: 14px system-ui, sans-serif; margin: 16px; background: #f4f4f4; color: #222; }}
table {{ border-collapse: collapse; }}
th, td {{ border: 1px solid #ccc; padding: 4px; vertical-align: top; background: #fff; }}
thead th {{ position: sticky; top: 0; background: #eee; }}
th.word {{ width: 220px; text-align: left; font-weight: normal; }}
th.word b {{ font-size: 18px; }}
th.word p {{ color: #555; font-size: 12px; margin: 6px 0 0; }}
img {{ display: block; }}
img:hover {{ transform: scale(1.5); position: relative; z-index: 1; box-shadow: 0 2px 12px #0005; }}
</style></head><body>
<h1>{h.escape(title)}</h1>
{intro}<p>{len(rows)} words. Pictures as the card shows them (white margins trimmed, {CARD_SIZE} px;
hover to enlarge). Every prompt ends with: <i>{h.escape(STYLE)}</i>.</p>
<table><thead><tr><th>word</th>{head}</tr></thead><tbody>
{chr(10).join(body)}
</tbody></table></body></html>
""", encoding="utf-8")
    print(f"{len(rows)} words x {len(cols)} columns -> {html}")


def mosaic(name, png, words, columns, tile=200, start=0, count=None):
    """Writes a contact sheet as one PNG (for a quick look, or review by an AI
    agent that reads images): one row per word, labelled with its card and
    gloss, one column per model and seed."""
    from PIL import Image, ImageDraw, ImageFont
    rows = chosen_words(name, words)[start:None if count is None else start + count]
    font = ImageFont.load_default(size=15)
    ascii_label = str.maketrans({"ä": "ae", "ö": "oe", "ü": "ue", "Ä": "Ae", "Ö": "Oe", "Ü": "Ue", "ß": "ss"})
    info = {(w["kind"], w["lemma"]): w for w in list_words(name)}
    cols = []
    for column in columns:
        model, _, seeds = column.partition(":")
        cols += [(model, seed) for seed in seed_range(seeds or "1-4")]
    label = 170
    sheet = Image.new("RGB", (label + tile * len(cols), tile * len(rows)), "white")
    draw = ImageDraw.Draw(sheet)
    for y, row in enumerate(rows):
        w = info[(row["kind"], row["lemma"])]
        # The built-in font has no umlauts.
        draw.text((6, y * tile + 8), f"{start + y + 1}. {w['card']}".translate(ascii_label), fill="black", font=font)
        draw.text((6, y * tile + 28), w["english"][:22], fill="#555", font=font)
        for x, (model, seed) in enumerate(cols):
            path = HERE / name / "raw" / model / picture_name(row["kind"], row["lemma"], seed, full_prompt(row["prompt"]))
            if path.exists():
                sheet.paste(card_picture(path, tile - 4), (label + x * tile + 2, y * tile + 2))
            draw.text((label + x * tile + 6, y * tile + 4), f"{model[0]}{seed}", fill="red", font=font)
        draw.line([(0, y * tile), (sheet.width, y * tile)], fill="#bbb")
    sheet.save(png)
    print(f"{len(rows)} words x {len(cols)} columns -> {png}")


if __name__ == "__main__":
    command, args = (sys.argv[1], sys.argv[2:]) if len(sys.argv) > 1 else ("", [])
    if command == "download" and args and all(a in MODELS for a in args):
        download(args)
    elif command == "pending" and len(args) in (2, 3):
        pending(args[0], args[1], int(args[2]) if len(args) > 2 else 250)
    elif command == "check" and len(args) == 2:
        check(*args)
    elif command == "merge" and len(args) >= 2:
        merge(args[0], args[1:])
    elif command == "generate" and 3 <= len(args) <= 5 and args[1] in MODELS:
        generate(args[0], args[1], args[2], args[3] if len(args) > 3 else "1-4",
                 int(args[4]) if len(args) > 4 else None)
    elif command == "pick" and len(args) >= 2:
        pick(args[0], args[1:])
    elif command == "restore" and len(args) == 1:
        restore(args[0])
    elif command == "sheet" and len(args) >= 4:
        sheet(args[0], args[1], args[2], args[3:])
    elif command == "mosaic" and len(args) >= 6:
        mosaic(args[0], args[1], args[2], args[5:], start=int(args[3]), count=int(args[4]))
    else:
        sys.exit(__doc__)

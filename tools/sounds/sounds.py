#!/usr/bin/env python3
"""Sounds for the quizzes' cards: each text, spoken (see README.md).

A language's sounds (`de`): `texts de` collects every German text on the
sample quizzes, once; `make de all` makes a sound for each text not made yet,
trying voices in turn until Whisper is sure of one; `collect de all` puts the
picked sounds in de/raw/sounds/ with de/raw/sounds.tsv. The pilot (`core`)
compared the voices on 52 words of a word list.

  texts <lang>                              <lang>/texts.tsv: the texts and their keys
  make <lang> <words> [takes]               generate, hear, retake and pick, in turn
  collect <lang> <words>                    raw/sounds/: the picked sound per text
  cards <lang> <words>                      raw/cards/: those sounds as the quizzes carry
                                            them (MP3)
  samples <lang> <dir>                      adds the sounds to the sample quizzes in <dir>
                                            (picture quizzes speak the word after the answer)
                                            and makes the listening and dictation quizzes

  generate <list> <engine> <words> [seed]   raw/<engine>/<kind>-<lemma>.wav, log.tsv
  wikimedia <list> <words>                  raw/wikimedia/: recordings via Wikidata
  hear <list> <words> [sources]             raw/<source>/heard.tsv: what Whisper hears;
                                            score.tsv: its chance of writing the word
  pick <list> <words> [sources]             raw/picks.tsv: per word, the source
                                            Whisper is surest of
  retake <list> <words> [engines] [takes]   more takes of the words picked below
                                            SURE a piece; the best take stays
  choose <list> <picks.tsv>                 <list>/chosen.tsv: sounds chosen by ear
                                            (a page's picks) over Whisper's
  page <list> <words> [sources]             raw/listen/: trimmed, evened copies
                                            and pilot.html

<words> is "all" or a file of lines: "<lang> <key>" for a language's texts,
"noun Haus" / "verb gehen" for a word list (core/pilot.txt: a noun is spoken
with its article, "das Haus."). Every step skips work already done.
"""

import argparse
import contextlib
import csv
import functools
import gc
import hashlib
import html
import importlib.metadata
import io
import json
import re
import shutil
import subprocess
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
import wave
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORDLISTS = HERE.parent / "wordlists"
REPO = HERE.parent.parent
# Where the quizzes are whose texts get sounds (`texts`): built in, and online.
DECKS = [REPO / "apps/quiz/src/main/assets/samples", REPO / "apps/quiz/samples"]
VOICES = HERE / "voices"
USER_AGENT = "android-apps-sounds/0.1 (https://github.com/ehb54/android-apps)"

# The sounds may be published (download site, Play), so a model's license
# must allow redistributing what it makes.
ENGINES = {
    "piper-thorsten": {
        "runner": "piper",
        "voice": "de_DE-thorsten-high",
        "license": "Piper (GPL engine); voice trained on Thorsten-Voice, CC0",
    },
    "piper-kerstin": {
        "runner": "piper",
        "voice": "de_DE-kerstin-low",
        "license": "Piper (GPL engine); voice trained on Kerstin, CC0",
    },
    "qwen3-design": {
        "runner": "mlx",
        "model": "mlx-community/Qwen3-TTS-12Hz-1.7B-VoiceDesign-bf16",
        "lang_code": "German",
        "instruct": (
            "A clear, warm female voice speaking standard High German "
            "(Hochdeutsch) slowly and distinctly, like a language teacher "
            "saying a single word."
        ),
        "license": "Qwen3-TTS, Apache 2.0",
    },
    "chatterbox": {
        "runner": "mlx",
        "model": "mlx-community/chatterbox-fp16",
        "lang_code": "de",
        "license": "Chatterbox Multilingual (Resemble AI), MIT",
    },
    "omnivoice": {
        "runner": "mlx",
        "model": "mlx-community/OmniVoice-bfloat16",
        "lang_code": "de",
        "license": "OmniVoice, Apache 2.0",
    },
    "kugelaudio": {
        "runner": "mlx",
        "model": "kugelaudio/kugelaudio-0-open",
        "lang_code": "de",
        # One of the model's pre-encoded voices (voices/voices.json): "clear"
        # (young female); "default" (narrator) puts "für"/"find" before most
        # words. Needs mlx-audio patched (patches/) and torch to read the .pt.
        "voice": "clear",
        "license": "KugelAudio, MIT (7B: needs a large Mac)",
    },
    "kugelaudio-cfg8": {
        "runner": "mlx",
        "model": "kugelaudio/kugelaudio-0-open",
        "lang_code": "de",
        "voice": "clear",
        "cfg_scale": 8.0,  # stronger text guidance than the default 3.0
        "license": "KugelAudio, MIT (7B: needs a large Mac)",
    },
}
WIKIMEDIA = "wikimedia"
NOUN, VERB = "Q1084", "Q24905"  # Wikidata lexical categories
# Per language: the voices `make` makes every text with (any voice that is the
# surest for some texts belongs here: eb, after the trial); the voices
# `retake` makes more takes with (every voice is seeded, Piper too); and
# Whisper's language. Another language needs its own voices in ENGINES (Piper
# and Qwen3-TTS have many) and a line here.
LANGUAGES = {
    "de": {
        "whisper": "de",
        "engines": ["qwen3-design", "piper-thorsten", "omnivoice", "chatterbox"],
        "retake": ["qwen3-design", "piper-thorsten", "omnivoice", "chatterbox"],
    },
}


def lang_of(list_name):
    """A list's language: a language's own folder (with texts.tsv) is named
    after it; the word lists are German."""
    return list_name if (HERE / list_name / "texts.tsv").exists() else "de"


# Sources `pick` leaves out unless named.
NOT_PICKED = {
    WIKIMEDIA: "says the bare word, without the article, and each speaker needs a credit",
    "piper-kerstin": "16 kHz 'low' voice: poor in the pilot",
    "kugelaudio": "dropped after the pilot: best for 1 word of 52, stray sounds before "
                  "words, and it needs a 17 GB Mac and a patched mlx-audio",
    "kugelaudio-cfg8": "worse than CFG 3 to the ear: odd sounds, music in some",
}


# The language of each side of each quiz in DECKS, by quiz name (first match
# wins): "de" German, "en" English, "fr" French, "" neither (a picture, a
# sound, a symbol). `texts <lang>` collects the sides in that language, and a
# quiz no rule matches stops it, so a new quiz is never left out unnoticed.
QUIZ_SIDES = [
    (r" verb forms$", ("de", "de")),  # sein = ist, war, ist gewesen
    (r" articles$", ("de", "de")),  # Hund = der
    (r" plurals$", ("de", "de")),  # der Hund = die Hunde
    (r" nouns$", ("de", "en")),
    (r" verbs$", ("de", "en")),
    (r" pictures( ERNIE)?$", ("", "de")),  # also "Core verb pictures ERNIE"
    (r" listening ERNIE$", ("", "")),  # a sound (with its text, read from the pictures quiz) -> a picture
    (r" dictation$", ("", "de")),  # a sound -> its text
    (r"^(Numbers|Colors)$", ("de", "en")),  # German starter
    (r"^French words$", ("en", "fr")),
    (r"^(Amino acids|Chemical symbols)$", ("en", "")),
    (r"^Morse code$", ("", "")),
    (r"^Shapes$", ("", "en")),
]


def read_tsv(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))


def text_key(text):
    """A text's file name: readable, and unique however a file system treats
    case or accents ("Essen" and "essen" are different texts)."""
    slug = re.sub(r"\W+", "_", text).strip("_")[:40] or "_"
    return f"{slug}-{hashlib.sha1(text.encode('utf-8')).hexdigest()[:8]}"


def spoken(text):
    """What a model is given to say: the text, ending in a full stop."""
    return text if text[-1:] in ".!?" else text + "."


def xlsx_tabs(path):
    """[(tab name, [(side A, side B), ...])] of a workbook, as the app reads
    it (`engine/Spreadsheet.kt`): the first two columns, rows with both."""
    m = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
    r_id = "{http://schemas.openxmlformats.org/officeDocument/2006/relationships}id"
    z = zipfile.ZipFile(path)
    shared = []
    if "xl/sharedStrings.xml" in z.namelist():
        for si in ET.fromstring(z.read("xl/sharedStrings.xml")).findall(m + "si"):
            shared.append("".join(t.text or "" for t in si.iter(m + "t")))
    rels = {r.get("Id"): r.get("Target")
            for r in ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))}
    tabs = []
    for sheet in ET.fromstring(z.read("xl/workbook.xml")).find(m + "sheets"):
        target = rels[sheet.get(r_id)].lstrip("/")
        target = target if target.startswith("xl/") else "xl/" + target
        cards = []
        for row in ET.fromstring(z.read(target)).iter(m + "row"):
            cells = {}
            for c in row.findall(m + "c"):
                v = c.find(m + "v")
                if c.get("t") == "s" and v is not None:
                    value = shared[int(v.text)]
                elif c.get("t") == "inlineStr":
                    value = "".join(t.text or "" for t in c.iter(m + "t"))
                else:
                    value = v.text if v is not None else ""
                letters = re.match(r"[A-Z]+", c.get("r")).group(0)
                cells[sum((ord(ch) - 64) * 26 ** i for i, ch in enumerate(reversed(letters))) - 1] = value
            a, b = cells.get(0, "").strip(), cells.get(1, "").strip()
            if a and b:
                cards.append((a, b))
        tabs.append((sheet.get("name"), cards))
    return tabs


def qzx_cards(text):
    lines = [line.strip() for line in text.splitlines()]
    return [(lines[i], lines[i + 1]) for i in range(0, len(lines) - 1, 2)]


def quizzes():
    """(file, quiz name, cards) for every quiz in DECKS."""
    for folder in DECKS:
        for path in sorted(folder.iterdir()):
            if path.suffix == ".xlsx":
                for name, cards in xlsx_tabs(path):
                    yield path.name, name, cards
            elif path.suffix == ".qzx":
                yield path.name, path.stem, qzx_cards(path.read_text(encoding="utf-8"))
            elif path.suffix == ".zip":
                z = zipfile.ZipFile(path)
                inner = [n for n in z.namelist() if n.endswith(".qzx")]
                if inner:
                    yield path.name, path.stem, qzx_cards(z.read(inner[0]).decode("utf-8"))


def texts(args):
    """Writes <lang>/texts.tsv: every text in that language on a side of a
    quiz in DECKS, once, with its key (file name) and the quizzes it is in.
    Run it again when quizzes change: the sounds of texts already made are
    kept, so only new texts are made."""
    found = {}
    for file, name, cards in quizzes():
        sides = next((langs for pattern, langs in QUIZ_SIDES if re.search(pattern, name)), None)
        if sides is None:
            sys.exit(f"{file}: no QUIZ_SIDES rule for the quiz {name!r}; add one")
        for side, lang in enumerate(sides):
            if lang != args.lang:
                continue
            for card in cards:
                # A sound for after the answer (`samples`) is no part of the text.
                text = unicodedata.normalize("NFC", " ".join(SOUND_MARK.sub("", card[side]).split()))
                if text and not text.startswith("!"):  # "!name.png" is a file
                    found.setdefault(text, []).append(name)
    folder = HERE / args.lang
    folder.mkdir(exist_ok=True)
    out = folder / "texts.tsv"
    old = {r["text"] for r in read_tsv(out)} if out.exists() else set()
    with open(out, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n", quoting=csv.QUOTE_NONE, escapechar="\\")
        w.writerow(["key", "text", "quizzes"])
        for text in sorted(found, key=str.casefold):
            w.writerow([text_key(text), text, "; ".join(sorted(set(found[text])))])
    cards = sum(len(q) for q in found.values())
    print(f"{args.lang}: {len(found)} texts on {cards} card sides; "
          f"{len(set(found) - old)} new, {len(old - set(found))} no longer in a quiz; wrote {out}")


def read_words(list_name, words_arg):
    """(kind, lemma, text, english) for each word, in the order given: from a
    language's texts.tsv (kind = the language, lemma = the text's key,
    english = its quizzes), or from a word list's nouns and verbs. words_arg
    is "all", a file of "<kind> <lemma>" lines, or such a list already read."""
    if isinstance(words_arg, list):
        return words_arg
    texts_tsv = HERE / list_name / "texts.tsv"
    if texts_tsv.exists():
        rows = {r["key"]: r for r in read_tsv(texts_tsv)}
        if words_arg == "all":
            keys = list(rows)
        else:
            keys = [line.split("#", 1)[0].split()[-1]
                    for line in Path(words_arg).read_text(encoding="utf-8").splitlines()
                    if line.split("#", 1)[0].strip()]
        return [(list_name, k, spoken(rows[k]["text"]), rows[k]["quizzes"]) for k in keys]
    folder = WORDLISTS / list_name
    nouns = {r["lemma"]: r for r in read_tsv(folder / "nouns.tsv")}
    verbs = {r["lemma"]: r for r in read_tsv(folder / "verbs.tsv")}
    if words_arg == "all":
        wanted = [("noun", w) for w in nouns] + [("verb", w) for w in verbs]
    else:
        wanted = []
        for line in Path(words_arg).read_text(encoding="utf-8").splitlines():
            line = line.split("#", 1)[0].strip()
            if line:
                kind, lemma = line.split(None, 1)
                wanted.append((kind, lemma))
    words = []
    for kind, lemma in wanted:
        if kind == "noun":
            row = nouns[lemma]
            text = f"{row['article']} {lemma}." if row["article"] else f"{lemma}."
        elif kind == "verb":
            row = verbs[lemma]
            text = f"{row['infinitive'] or lemma}."
        else:
            sys.exit(f"unknown kind {kind!r} for {lemma}")
        words.append((kind, lemma, text, row["english"]))
    return words


def wav_seconds(path):
    try:
        with wave.open(str(path)) as w:
            return w.getnframes() / w.getframerate()
    except (wave.Error, EOFError):
        return 0.0


def append_log(path, row):
    new = not path.exists()
    with open(path, "a", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(row), delimiter="\t")
        if new:
            writer.writeheader()
        writer.writerow(row)


def piper_synth(cfg):
    import onnxruntime
    from piper import PiperVoice

    voice = cfg["voice"]
    onnx = VOICES / f"{voice}.onnx"
    if not onnx.exists():
        VOICES.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            [sys.executable, "-m", "piper.download_voices", voice, "--download-dir", str(VOICES)],
            check=True,
        )
    version = "piper-tts {} onnxruntime {}".format(
        importlib.metadata.version("piper-tts"), importlib.metadata.version("onnxruntime")
    )

    def synth(text, path, seed):
        # Piper's noise comes from onnxruntime, which seeds it when a model
        # loads, so a fresh load per sound (about 1 s) makes a text and seed
        # give the same sound every time. (Sounds made before this were unseeded.)
        onnxruntime.set_seed(seed)
        pv = PiperVoice.load(str(onnx))
        with wave.open(str(path), "wb") as wf:
            pv.synthesize_wav(text, wf)

    return synth, voice, version


def mlx_synth(cfg):
    import mlx.core as mx
    from mlx_audio.tts.generate import generate_audio
    from mlx_audio.tts.utils import load_model

    model = load_model(cfg["model"])
    version = "mlx-audio {} mlx {}".format(
        importlib.metadata.version("mlx-audio"), importlib.metadata.version("mlx")
    )
    voice = None
    if "voice" in cfg:
        # mlx-audio fetches only the weights; the voice file comes separately
        from huggingface_hub import hf_hub_download

        voice = hf_hub_download(cfg["model"], f"voices/{cfg['voice']}.pt")

    def synth(text, path, seed):
        mx.random.seed(seed)
        kwargs = {}
        if "instruct" in cfg:
            kwargs["instruct"] = cfg["instruct"]
        if voice:
            kwargs["voice"] = voice
        if "cfg_scale" in cfg:
            kwargs["cfg_scale"] = cfg["cfg_scale"]
        with contextlib.redirect_stdout(io.StringIO()):  # it prints its settings per call
            generate_audio(
                text=text,
                model=model,
                lang_code=cfg["lang_code"],
                file_prefix=str(path.with_suffix("")),
                audio_format="wav",
                join_audio=True,
                verbose=False,
                **kwargs,
            )

    return synth, cfg["model"], version


def generate(args):
    cfg = ENGINES[args.engine]
    words = read_words(args.list, args.words)
    out = HERE / args.list / "raw" / args.engine
    out.mkdir(parents=True, exist_ok=True)
    todo = [w for w in words if not (out / f"{w[0]}-{w[1]}.wav").exists()]
    print(f"{args.engine}: {len(words) - len(todo)} done, {len(todo)} to make")
    if not todo:
        return
    synth, model, version = (piper_synth if cfg["runner"] == "piper" else mlx_synth)(cfg)
    for i, (kind, lemma, text, _) in enumerate(todo, 1):
        path = out / f"{kind}-{lemma}.wav"
        start = time.time()
        synth(text, path, args.seed)
        seconds = time.time() - start
        length = wav_seconds(path)
        print(f"  {i}/{len(todo)} {text:<24} {seconds:5.1f} s to make, {length:4.2f} s long")
        append_log(out / "log.tsv", made_row(path, kind, lemma, text, args.engine, model, version,
                                             args.seed, seconds, length))


def made_row(path, kind, lemma, text, engine, model, version, seed, seconds, length):
    """A log.tsv row: what made a sound, so it can be made again."""
    cfg = ENGINES[engine]
    return {
        "file": path.name, "kind": kind, "lemma": lemma, "text": text,
        "engine": engine, "model": model, "version": version,
        "settings": json.dumps({k: v for k, v in cfg.items() if k not in ("runner", "license")},
                               ensure_ascii=False),
        "seed": seed, "seconds": f"{seconds:.2f}", "length": f"{length:.2f}",
    }


def http_json(url, data=None):
    req = urllib.request.Request(url, data=data, headers={
        "User-Agent": USER_AGENT, "Accept": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.load(r)


def fetch(url, tries=6):
    """Download a file, waiting as long as the server asks on HTTP 429."""
    for attempt in range(tries):
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except urllib.error.HTTPError as err:
            if err.code != 429 or attempt == tries - 1:
                raise
            wait = int(err.headers.get("Retry-After") or 0) or 10 * (attempt + 1)
            print(f"    rate-limited; waiting {wait} s")
            time.sleep(wait)


def wikimedia(args):
    """Fetch one recording per word that Wikidata's German lexemes link to
    (pronunciation audio, P443), else Commons' File:De-<lemma>.ogg."""
    words = read_words(args.list, args.words)
    out = HERE / args.list / "raw" / WIKIMEDIA
    out.mkdir(parents=True, exist_ok=True)
    values = " ".join('"{}"@de'.format(lemma.replace('"', "")) for _, lemma, _, _ in words)
    query = f"""
SELECT ?lemma ?cat ?audio WHERE {{
  VALUES ?lemma {{ {values} }}
  ?lex dct:language wd:Q188 ; wikibase:lemma ?lemma ; wikibase:lexicalCategory ?cat .
  {{ ?lex ontolex:lexicalForm ?form . ?form ontolex:representation ?lemma ; wdt:P443 ?audio . }}
  UNION {{ ?lex wdt:P443 ?audio . }}
}}"""
    result = http_json("https://query.wikidata.org/sparql?format=json&query="
                       + urllib.parse.quote(query))
    found = {}
    for b in result["results"]["bindings"]:
        kind = {NOUN: "noun", VERB: "verb"}.get(b["cat"]["value"].rsplit("/", 1)[-1])
        title = "File:" + urllib.parse.unquote(b["audio"]["value"].rsplit("/", 1)[-1])
        found.setdefault((kind, b["lemma"]["value"]), set()).add(title)
    # One file per word: Wikidata's link first (sorted, so the choice is
    # stable), else the Wiktionary-style name on Commons.
    choice = {}
    for kind, lemma, _, _ in words:
        titles = sorted(found.get((kind, lemma), ()))
        choice[(kind, lemma)] = (titles[0], "wikidata") if titles else (f"File:De-{lemma}.ogg", "commons name")
    infos = {}
    titles = [t for t, _ in choice.values()]
    for i in range(0, len(titles), 50):
        r = http_json("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode({
            "action": "query", "format": "json", "prop": "imageinfo",
            "iiprop": "url|extmetadata", "iiextmetadatafilter": "LicenseShortName|Artist",
            "titles": "|".join(titles[i:i + 50])}))
        norm = {n["to"]: n["from"] for n in r["query"].get("normalized", [])}
        for p in r["query"]["pages"].values():
            if "imageinfo" in p:
                infos[norm.get(p["title"], p["title"])] = p["imageinfo"][0]
    got = 0
    for kind, lemma, _, _ in words:
        title, how = choice[(kind, lemma)]
        info = infos.get(title)
        if not info:
            print(f"  {kind} {lemma}: none")
            continue
        url = info["url"]
        path = out / f"{kind}-{lemma}{Path(urllib.parse.urlparse(url).path).suffix.lower()}"
        if not path.exists():
            path.write_bytes(fetch(url))
            meta = info.get("extmetadata", {})
            artist = re.sub(r"<[^>]+>", "", meta.get("Artist", {}).get("value", "")).strip()
            append_log(out / "log.tsv", {
                "file": path.name, "kind": kind, "lemma": lemma, "commons": title, "found by": how,
                "license": meta.get("LicenseShortName", {}).get("value", ""),
                "artist": artist, "url": info.get("descriptionurl", url),
            })
            time.sleep(1.0)  # Wikimedia answers 429 to faster downloads
        got += 1
        print(f"  {kind} {lemma}: {title} ({how})")
    print(f"{WIKIMEDIA}: {got} of {len(words)} words have a recording")


RATE = 24000  # Hz, mono: what the evened copies are
TRIM_DB = 40  # the ends quieter than this below a sound's loudest 10 ms are cut
KEEP_MS, FADE_MS = 30, 5  # kept before and after the word; fade at each cut
TARGET_LUFS = -14.0  # loudness every sound is evened to: loud, for a phone
PEAK_CEILING = -1.0  # dBFS: the limiter keeps every sample at or below it
ATTACK_MS, RELEASE_MS = 5, 50  # how fast the limiter turns down, and back up
EVENING = (f"{RATE} Hz mono; ends below {TRIM_DB} dB under the loudest 10 ms cut, "
           f"{KEEP_MS} ms kept, {FADE_MS} ms fades; one gain to {TARGET_LUFS:g} LUFS; "
           f"peaks limited to {PEAK_CEILING:g} dBFS ({ATTACK_MS} ms attack, {RELEASE_MS} ms release)")


def decode(path):
    """A sound as float samples, mono, at RATE."""
    import numpy as np

    r = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(path),
                        "-f", "f32le", "-ac", "1", "-ar", str(RATE), "-"],
                       capture_output=True, check=True)
    return np.frombuffer(r.stdout, dtype=np.float32).copy()


def trim(x):
    """Cut the quiet ends: everything before the first and after the last
    10 ms frame within TRIM_DB of the loudest, keeping KEEP_MS either side.
    Relative to each sound, so quiet recordings and a noise floor trim alike.
    (ffmpeg 5's silenceremove dropped whole words that start loud.)"""
    import numpy as np

    frame = RATE // 100
    n = len(x) // frame
    if n == 0:
        return x
    rms = np.sqrt(np.mean(x[:n * frame].reshape(n, frame) ** 2, axis=1))
    db = 20 * np.log10(np.maximum(rms, 1e-9))
    loud = np.nonzero(db > max(db.max() - TRIM_DB, -90))[0]
    keep = RATE * KEEP_MS // 1000
    x = x[max(0, loud[0] * frame - keep):min(len(x), (loud[-1] + 1) * frame + keep)].copy()
    fade = min(RATE * FADE_MS // 1000, len(x) // 2)
    if fade:
        ramp = np.linspace(0, 1, fade, dtype=np.float32)
        x[:fade] *= ramp
        x[-fade:] *= ramp[::-1]
    return x


def loudness(x):
    """(integrated loudness in LUFS, true peak in dBFS) of samples, measured
    by ffmpeg on ten copies in a row: EBU R128 measures in 400 ms blocks, and
    a single word may be shorter than that."""
    import numpy as np

    r = subprocess.run(
        ["ffmpeg", "-hide_banner", "-nostats", "-f", "f32le", "-ar", str(RATE), "-ac", "1",
         "-i", "pipe:", "-af", "ebur128=peak=true", "-f", "null", "-"],
        input=np.tile(x, 10).tobytes(), capture_output=True, check=True)
    err = r.stderr.decode("utf-8", "replace")
    summary = err[err.rfind("Summary:"):]
    lufs = re.search(r"I:\s+(-?[\d.]+|-inf) LUFS", summary).group(1)
    peak = re.search(r"Peak:\s+(-?[\d.]+|-inf) dBFS", summary).group(1)
    return float(lufs), float(peak)


def limit(x, ceiling_db):
    """A peak limiter: turns down only the moments that would pass the
    ceiling, so no sample does, and leaves the rest alone. The gain starts
    falling ATTACK_MS before a peak (it looks ahead) and climbs back over
    RELEASE_MS after, smoothly, so the sound is not clipped or distorted.
    Returns (samples, most it turned down in dB)."""
    import numpy as np

    ceiling = 10 ** (ceiling_db / 20)
    need = np.minimum(1.0, ceiling / np.maximum(np.abs(x), 1e-12))  # gain each sample needs
    if need.min() >= 1.0:
        return x, 0.0
    a = max(1, RATE * ATTACK_MS // 1000)
    h = a // 2
    # Hold: the least gain needed from a samples before to a after. Then smooth
    # over 2h+1 <= a samples: every value averaged near a peak is <= its need.
    held = np.lib.stride_tricks.sliding_window_view(
        np.pad(need, a, constant_values=1.0), 2 * a + 1).min(axis=1)
    smooth = np.convolve(np.pad(held, h, mode="edge"), np.ones(2 * h + 1) / (2 * h + 1), mode="valid")
    # Release: rise back to full gain no faster than over RELEASE_MS.
    step = 1000.0 / (RATE * RELEASE_MS)
    gain = smooth.copy()
    for i in range(1, len(gain)):
        gain[i] = min(smooth[i], gain[i - 1] + step)
    gain = np.minimum(gain, need)  # rounding guard: never above what a sample needs
    return (x * gain).astype(np.float32), float(-20 * np.log10(gain.min()))


def even(src, dst):
    """Trim the quiet ends, raise or lower the sound by one fixed gain to
    TARGET_LUFS, then limit the peaks that would pass PEAK_CEILING (`limit`).
    Returns (LUFS before, gain in dB, most limited in dB). (ffmpeg's one-pass
    loudnorm left some short words 30 dB too quiet.)"""
    import numpy as np

    x = trim(decode(src))
    lufs, _ = loudness(x)
    if lufs < -60:  # silence: leave it, Whisper's chance will be low
        print(f"    {src.parent.name}/{src.name}: {lufs:g} LUFS, nearly silent; left as is")
        gain = 0.0
    else:
        gain = TARGET_LUFS - lufs
    y, limited = limit(x * np.float32(10 ** (gain / 20)), PEAK_CEILING)
    with wave.open(str(dst), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes((np.clip(y, -1, 1) * 32767).round().astype("<i2").tobytes())
    return lufs, gain, limited


def evened(args):
    """raw/listen/<source>/<kind>-<lemma>.wav for every word each source has
    (made when missing or older than the source file): {source: {stem: path}}."""
    words = read_words(args.list, args.words)
    raw = HERE / args.list / "raw"
    if args.sources:
        sources = args.sources.split(",")
    else:
        sources = [s for s in [WIKIMEDIA, *ENGINES] if (raw / s).is_dir()]
    listen = raw / "listen"
    result = {}
    for src in sources:
        (listen / src).mkdir(parents=True, exist_ok=True)
        # Copies evened another way are made again.
        stamp, log = listen / src / "evened.txt", listen / src / "evened.tsv"
        redo = not stamp.exists() or stamp.read_text(encoding="utf-8").strip() != EVENING
        if redo:
            log.unlink(missing_ok=True)
        files = {p.stem: p for p in (raw / src).iterdir() if p.suffix in (".wav", ".ogg", ".mp3", ".flac")}
        result[src] = {}
        for kind, lemma, _, _ in words:
            stem = f"{kind}-{lemma}"
            if stem in files:
                dst = listen / src / f"{stem}.wav"
                if redo or not dst.exists() or dst.stat().st_mtime < files[stem].stat().st_mtime:
                    lufs, gain, limited = even(files[stem], dst)
                    append_log(log, {"file": stem, "lufs before": f"{lufs:.1f}",
                                     "gain": f"{gain:+.1f}", "limited": f"{limited:.1f}"})
                result[src][stem] = dst
        stamp.write_text(EVENING + "\n", encoding="utf-8")
    return words, raw, result


def bare(text):
    """A word as compared after hearing: lower case, no punctuation or article."""
    t = re.sub(r"[^\w\s]", " ", text.casefold().replace("ß", "ss")).split()
    if len(t) > 1 and t[0] in ("der", "die", "das"):
        t = t[1:]
    return " ".join(t)


HEAR_MODEL = "mlx-community/whisper-large-v3-turbo-asr-fp16"


def said_by(src, kind, lemma, text):
    """What a source's sound says: Wikimedia's recordings are the bare word."""
    return f"{lemma}." if src == WIKIMEDIA else text


def whisper_chance(model, lang="de"):
    """chance(path, text): the probability that Whisper, transcribing the
    sound in the language, writes exactly the text and stops (summed over the
    spellings it may use: capital or not, full stop or not, commas or not). Sounds of the
    same word compare directly; a word said unclearly, or wrongly, or with
    more after it scores low."""
    import mlx.core as mx
    from mlx_audio.stt.models.whisper.audio import N_FRAMES, pad_or_trim

    tok = model.get_tokenizer(language=lang, task="transcribe")
    prefix = list(tok.sot_sequence_including_notimestamps)

    def spellings(text):
        t = text.rstrip(".")
        forms = {t, t[:1].upper() + t[1:]}
        forms |= {f.replace(",", "") for f in forms}
        return sorted({f + p for f in forms for p in (".", "")})

    def chance(path, text):
        mel, _ = model._prepare_audio(str(path))
        audio = model.embed_audio(pad_or_trim(mel, N_FRAMES, axis=-2).astype(model.dtype)[None])
        logps = []
        for spelling in spellings(text):
            target = tok.encode(" " + spelling) + [tok.eot]
            logits = model.logits(mx.array([prefix + target[:-1]]), audio).astype(mx.float32)
            logp = logits - mx.logsumexp(logits, axis=-1, keepdims=True)
            at = mx.arange(len(prefix) - 1, len(prefix) - 1 + len(target))
            logps.append(logp[0, at, mx.array(target)].sum())
        return mx.exp(mx.logsumexp(mx.stack(logps))).item()

    return chance


def still_good(tsv, have):
    """The files a heard.tsv or score.tsv has rows for, after dropping the
    rows of sounds evened again since it was written."""
    if not tsv.exists():
        return set()
    rows = read_tsv(tsv)
    written = tsv.stat().st_mtime
    keep = [r for r in rows if r["file"] not in have or have[r["file"]].stat().st_mtime < written]
    if len(keep) < len(rows):
        tsv.unlink()
        for r in keep:
            append_log(tsv, r)
    return {r["file"] for r in keep}


def hear(args):
    """Whisper on each evened sound, into raw/<source>/: heard.tsv, what it
    writes down (a word heard as another was likely said wrong), and
    score.tsv, its chance of writing exactly the word (`whisper_chance`)."""
    from mlx_audio.stt.utils import load_model

    words, raw, sounds = evened(args)
    lang = LANGUAGES[lang_of(args.list)]["whisper"]
    transcribe = getattr(args, "heard", True)  # `make` only scores, which is what picks use
    model = chance = None
    for src, have in sounds.items():
        heard_tsv, score_tsv = raw / src / "heard.tsv", raw / src / "score.tsv"
        heard_done, score_done = still_good(heard_tsv, have), still_good(score_tsv, have)
        if not transcribe:
            heard_done = set(have)
        right, heard_now, chances = 0, 0, []
        for kind, lemma, text, _ in words:
            stem = f"{kind}-{lemma}"
            if stem not in have or (stem in heard_done and stem in score_done):
                continue
            model = model or load_model(HEAR_MODEL)
            said = said_by(src, kind, lemma, text)
            if stem not in heard_done:
                r = model.generate(str(have[stem]), language=lang, verbose=None)  # None: no progress bar
                heard = (r.text if hasattr(r, "text") else str(r)).strip()
                same = bare(heard) == bare(said)
                right += same
                heard_now += 1
                append_log(heard_tsv, {"file": stem, "said": said, "heard": heard,
                                       "same": "yes" if same else "no", "model": HEAR_MODEL})
            if stem not in score_done:
                chance = chance or whisper_chance(model, lang)
                p = chance(have[stem], said)
                chances.append(p)
                append_log(score_tsv, {"file": stem, "said": said, "chance": f"{p:.4f}",
                                       "model": HEAR_MODEL})
        if heard_now:
            print(f"{src}: {right} of {heard_now} heard as the word")
        if chances:
            print(f"{src}: {len(chances)} scored, average chance {average(chances):.0%}")


def average(values):
    return sum(values) / len(values) if values else 0.0


def scores(raw, src):
    """{stem: chance} from raw/<source>/score.tsv."""
    path = raw / src / "score.tsv"
    return {r["file"]: float(r["chance"]) for r in read_tsv(path)} if path.exists() else {}


def candidates(raw, args):
    """The sources `pick` chooses from: the ones named, else every source
    made except NOT_PICKED."""
    if args.sources:
        return args.sources.split(",")
    return [s for s in ENGINES if (raw / s).is_dir() and s not in NOT_PICKED]


def whisper_picks(raw, words, sources):
    """{stem: [(chance, source), ...]}, surest first."""
    have = {src: scores(raw, src) for src in sources}
    ranked = {}
    for kind, lemma, _, _ in words:
        stem = f"{kind}-{lemma}"
        ranked[stem] = sorted(((s[stem], src) for src, s in have.items() if stem in s), reverse=True)
    return ranked


DOUBT = 0.5  # the page marks a chance below this
DOUBT_SURE = 0.8  # a pick less sure than this a piece needs an ear (`pick`)
LISTEN_LIMITED = 4.0  # dB: the page flags sounds limited this much, to check by ear


RETAKE_ENGINES = "qwen3-design,piper-thorsten,omnivoice,chatterbox"  # each seed makes another take


def best_chance(ranked, stem):
    return ranked[stem][0][0] if ranked.get(stem) else 0.0


def read_chosen(list_name):
    """{stem: row} from <list>/chosen.tsv: the sounds chosen over the one
    Whisper is surest of, by ear ("ear": a listening page's picks) or from
    what Whisper wrote down for each sound ("transcript"). `pick` follows
    them and `retake` leaves those texts alone; `choose` adds to it."""
    path = HERE / list_name / "chosen.tsv"
    return {f"{r['kind']}-{r['lemma']}": r for r in read_tsv(path)} if path.exists() else {}


SURE = 0.9  # per piece (`sureness`): a text's best sound below this gets more work


@functools.lru_cache(maxsize=None)
def pieces(text):
    """How many pieces (tokens) Whisper writes the text in, with its end."""
    return len(whisper_tokenizer().encode(" " + text, add_special_tokens=False)) + 1


@functools.lru_cache(maxsize=1)
def whisper_tokenizer():
    from huggingface_hub import snapshot_download
    from transformers import AutoTokenizer

    return AutoTokenizer.from_pretrained(snapshot_download(HEAR_MODEL, allow_patterns=["*.json", "*.txt"]))


def sureness(chance, text):
    """Whisper's chance per piece of the text (the geometric mean). A long
    text's chance is spread over more pieces: a verb's three forms heard
    exactly score about 45%, but 95% a piece. So gates compare this with
    SURE; picks compare sounds of one text, where chance ranks the same."""
    return chance ** (1 / pieces(text)) if chance > 0 else 0.0


def some(items, n=20):
    """At most n items for a message, and how many more there are."""
    items = list(items)
    return ", ".join(items[:n]) + (f" and {len(items) - n} more" if len(items) > n else "") or "none"


def retake(args):
    """For each text whose pick Whisper is less sure of than `below` a piece
    (default SURE), makes more takes (seeds 1 to 1 + takes) with each engine
    named, into raw/<engine>/takes/<kind>-<lemma>~<seed>.wav (with log.tsv
    and score.tsv there), and makes the take Whisper is surest of that
    engine's sound for the word: raw/<engine>/<kind>-<lemma>.wav, its log.tsv
    row then giving that seed. Then picks again. Run it again with more takes
    for the texts still below: the takes made already are kept."""
    from mlx_audio.stt.utils import load_model

    raw = HERE / args.list / "raw"
    lang = LANGUAGES[lang_of(args.list)]["whisper"]
    engines = args.engines.split(",")
    below = getattr(args, "below", None) or SURE
    args.sources = None
    args.sources = ",".join(candidates(raw, args))
    hear(args)
    words = read_words(args.list, args.words)
    ranked = whisper_picks(raw, words, args.sources.split(","))
    weak = [(k, l, t) for k, l, t, _ in words if sureness(best_chance(ranked, f"{k}-{l}"), t) < below]
    chosen = read_chosen(args.list)
    if any(f"{k}-{l}" in chosen for k, l, _ in weak):
        print(f"leaving alone {sum(f'{k}-{l}' in chosen for k, l, _ in weak)} texts in chosen.tsv")
        weak = [(k, l, t) for k, l, t in weak if f"{k}-{l}" not in chosen]
    print(f"{len(weak)} texts picked below {below:.0%} a piece: "
          + some(f"{t.rstrip('.')} ({sureness(best_chance(ranked, f'{k}-{l}'), t):.0%})" for k, l, t in weak))
    if not weak:
        return

    logs = {}  # engine: {main file name: log.tsv row}

    def log_rows(e):
        if e not in logs:
            log = raw / e / "log.tsv"
            logs[e] = {r["file"]: r for r in read_tsv(log)} if log.exists() else {}
        return logs[e]

    def main_seed(e, stem):
        return int(log_rows(e).get(f"{stem}.wav", {}).get("seed") or 1)

    # Make the takes, one engine at a time (the models are large).
    for e in engines:
        takes = raw / e / "takes"
        takes.mkdir(parents=True, exist_ok=True)
        rows = log_rows(e)
        todo = []
        for k, l, t in weak:
            stem = f"{k}-{l}"
            main, seed = raw / e / f"{stem}.wav", main_seed(e, stem)
            first = takes / f"{stem}~{seed}.wav"
            if main.exists() and not first.exists():  # the take made first, as it was
                shutil.copy2(main, first)
                if f"{stem}.wav" in rows:
                    append_log(takes / "log.tsv", {**rows[f"{stem}.wav"], "file": first.name})
            todo += [(k, l, t, s) for s in range(1, args.takes + 2)
                     if not (takes / f"{stem}~{s}.wav").exists()]
        if not todo:
            continue
        print(f"{e}: {len(todo)} takes to make")
        cfg = ENGINES[e]
        synth, model, version = (piper_synth if cfg["runner"] == "piper" else mlx_synth)(cfg)
        for i, (k, l, t, s) in enumerate(todo, 1):
            path = takes / f"{k}-{l}~{s}.wav"
            start = time.time()
            synth(t, path, s)
            seconds, length = time.time() - start, wav_seconds(path)
            print(f"  {i}/{len(todo)} {t:<24} seed {s}: {seconds:4.1f} s to make, {length:4.2f} s long")
            append_log(takes / "log.tsv", made_row(path, k, l, t, e, model, version, s, seconds, length))
        del synth
        gc.collect()

    # Score every take, evened like the rest, and keep the best per engine.
    chance = whisper_chance(load_model(HEAR_MODEL), lang)
    for e in engines:
        takes, listen = raw / e / "takes", raw / "listen" / e / "takes"
        listen.mkdir(parents=True, exist_ok=True)
        score_tsv = takes / "score.tsv"
        got = {r["file"]: float(r["chance"]) for r in read_tsv(score_tsv)} if score_tsv.exists() else {}
        made = {r["file"]: r for r in read_tsv(takes / "log.tsv")} if (takes / "log.tsv").exists() else {}
        promoted, rescored = {}, {}
        for k, l, t in weak:
            stem = f"{k}-{l}"
            for path in sorted(takes.glob(f"{glob_escape(stem)}~*.wav")):
                if path.stem not in got:
                    even(path, listen / path.name)
                    got[path.stem] = chance(listen / path.name, said_by(e, k, l, t))
                    append_log(score_tsv, {"file": path.stem, "said": t, "chance": f"{got[path.stem]:.4f}",
                                           "model": HEAR_MODEL})
            mine = sorted(((got[f"{stem}~{s}"], s) for s in range(1, args.takes + 2)
                           if f"{stem}~{s}" in got), reverse=True)
            if not mine:
                continue
            (best, seed), was = mine[0], main_seed(e, stem)
            had = dict((s_, c_) for c_, s_ in mine).get(was, 0.0)
            if seed == was:
                print(f"  {e} {t.rstrip('.')}: take {seed} stays ({sureness(best, t):.0%} a piece); best of {len(mine)}")
                continue
            # The take becomes the main sound, and its evened copy (made after
            # it, so not made again) the main one; its score follows below.
            shutil.copyfile(takes / f"{stem}~{seed}.wav", raw / e / f"{stem}.wav")
            lufs, gain, limited = even(takes / f"{stem}~{seed}.wav", raw / "listen" / e / f"{stem}.wav")
            append_log(raw / "listen" / e / "evened.tsv", {"file": stem, "lufs before": f"{lufs:.1f}",
                                                           "gain": f"{gain:+.1f}", "limited": f"{limited:.1f}"})
            rescored[stem] = {"file": stem, "said": said_by(e, k, l, t), "chance": f"{best:.4f}",
                              "model": HEAR_MODEL}
            if f"{stem}~{seed}.wav" in made:
                promoted[f"{stem}.wav"] = {**made[f"{stem}~{seed}.wav"], "file": f"{stem}.wav"}
            print(f"  {e} {t.rstrip('.')}: take {seed} ({sureness(best, t):.0%} a piece) replaces take {was} ({sureness(had, t):.0%}); "
                  f"best of {len(mine)}")
        if promoted:  # the main sounds' log.tsv rows now give the seeds kept
            log = raw / e / "log.tsv"
            rewrite_tsv(log, [promoted.get(r["file"], r) for r in read_tsv(log)])
            logs.pop(e, None)
        if rescored:  # written after the evened copies, so `hear` keeps these scores
            tsv = raw / e / "score.tsv"
            rows = read_tsv(tsv) if tsv.exists() else []
            have = {r["file"] for r in rows}
            rewrite_tsv(tsv, [rescored.get(r["file"], r) for r in rows]
                        + [r for s, r in rescored.items() if s not in have])
    pick(args)


def rewrite_tsv(path, rows):
    """Writes the rows anew, in one go (`append_log` opens the file per row)."""
    path.unlink(missing_ok=True)
    if not rows:
        return
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=list(dict.fromkeys(k for r in rows for k in r)),
                                delimiter="\t", restval="", lineterminator="\n")
        writer.writeheader()
        writer.writerows(rows)


def glob_escape(text):
    """A file name as a glob pattern that matches only itself."""
    return re.sub(r"([*?[])", r"[\1]", text)


def pick(args):
    """Scores what is missing, then writes raw/picks.tsv: for each word, the
    source Whisper is surest says it (or the one in chosen.tsv), and the
    runner-up."""
    raw = HERE / args.list / "raw"
    args.sources = ",".join(candidates(raw, args))
    hear(args)
    words = read_words(args.list, args.words)
    ranked = whisper_picks(raw, words, args.sources.split(","))
    out = raw / "picks.tsv"
    out.unlink(missing_ok=True)
    chosen = read_chosen(args.list)
    counts, doubtful, followed, unmade = {}, [], {}, []
    for kind, lemma, text, _ in words:
        r, by, c = ranked[f"{kind}-{lemma}"], "", chosen.get(f"{kind}-{lemma}")
        if c and any(src == c["voice"] for _, src in r):
            r, by = sorted(r, key=lambda x: x[1] != c["voice"]), c["by"]  # the chosen first
            followed[by] = followed.get(by, 0) + 1
        elif c:
            unmade.append(f"{text.rstrip('.')} ({c['voice']})")
        r = r + [(0.0, "")] * 2
        (p, src), (p2, src2) = r[0], r[1]
        sure = sureness(p, text)
        append_log(out, {"kind": kind, "lemma": lemma, "text": text, "source": src,
                         "chance": f"{p:.4f}", "sure": f"{sure:.4f}",
                         "next": src2, "next chance": f"{p2:.4f}", "chosen": by})
        counts[src] = counts.get(src, 0) + 1
        if sure < DOUBT_SURE and by != "ear":
            doubtful.append(f"{text.rstrip('.')} ({src} {sure:.0%} a piece)")
    print(f"from {args.sources}:")
    for src, n in sorted(counts.items(), key=lambda x: -x[1]):
        print(f"  {src or 'none'}: {n}")
    if followed:
        print("as chosen in chosen.tsv: " + ", ".join(f"{n} by {b}" for b, n in sorted(followed.items())))
    if unmade:
        print(f"chosen, but no such sound to pick: {some(unmade)}")
    print(f"{len(doubtful)} below {DOUBT_SURE:.0%} a piece, to listen to: {some(doubtful)}")
    print(f"wrote {out}")


def choose(args):
    """Adds the picks in a TSV with the columns kind, lemma and best (a
    listening page's "Copy picks") to <list>/chosen.tsv, with by "ear" unless
    the TSV has a by column (and why). Rows without a best are skipped; a text
    chosen again takes the new choice, except that one chosen by ear keeps it
    over any other kind. The choice notes the take (seed) it was made of.
    Then picks and collects again (for a language)."""
    raw = HERE / args.list / "raw"
    texts = {f"{k}-{l}": t for k, l, t, _ in read_words(args.list, "all")}
    old, new, seeds = read_chosen(args.list), {}, {}
    for r in read_tsv(Path(args.picks)):
        if not r.get("best"):
            continue
        stem, voice = f"{r['kind']}-{r['lemma']}", r["best"]
        if stem not in texts:
            sys.exit(f"{stem}: not a text of {args.list}")
        if not (raw / voice / f"{stem}.wav").exists():
            sys.exit(f"{stem}: {voice} has no sound for it")
        if voice not in seeds:
            log = raw / voice / "log.tsv"
            seeds[voice] = {x["file"]: x.get("seed", "") for x in read_tsv(log)} if log.exists() else {}
        by = r.get("by") or "ear"
        if old.get(stem, {}).get("by") == "ear" and by != "ear":
            continue
        new[stem] = {"kind": r["kind"], "lemma": r["lemma"], "text": texts[stem], "voice": voice,
                     "seed": seeds[voice].get(f"{stem}.wav", ""), "by": by, "why": r.get("why", "")}
    changed = sum(1 for s, r in new.items() if old.get(s, {}).get("voice") != r["voice"] or s not in old)
    rewrite_tsv(HERE / args.list / "chosen.tsv",
                [new.pop(s, r) for s, r in old.items()] + list(new.values()))
    print(f"{len(read_chosen(args.list))} texts in {args.list}/chosen.tsv ({changed} new or changed)")
    if (HERE / args.list / "texts.tsv").exists():
        pick(argparse.Namespace(list=args.list, words="all", sources=None, heard=False))
        collect(argparse.Namespace(list=args.list, words="all"))
    else:
        print(f"then: pick {args.list} <words>, collect {args.list} <words>")


def make(args):
    """A sound for every text of a language that has none yet. Each of the
    language's voices (LANGUAGES) makes and scores every text, one voice
    after another, and after each the picks and de/raw/sounds/ are brought up
    to date, so the first voice's pass already gives every text a sound. Then
    the texts whose pick Whisper is less SURE of get more takes. Stop it any
    time; run it again and it carries on where it stopped."""
    lang = args.list
    if lang not in LANGUAGES or not (HERE / lang / "texts.tsv").exists():
        sys.exit(f"{lang}: run `texts {lang}` first, and give it a LANGUAGES entry")
    conf = LANGUAGES[lang]
    words = read_words(lang, args.words)
    started = time.time()

    def pick_and_collect(sources):
        pick(argparse.Namespace(list=lang, words=words, sources=",".join(sources), heard=False))
        collect(argparse.Namespace(list=lang, words=words))

    for i, engine in enumerate(conf["engines"]):
        print(f"== {engine}: {len(words)} texts ({time.time() - started:.0f} s so far)", flush=True)
        generate(argparse.Namespace(list=lang, engine=engine, words=words, seed=1))
        gc.collect()  # let the voice model go before Whisper loads
        hear(argparse.Namespace(list=lang, words=words, sources=engine, heard=False))
        pick_and_collect(conf["engines"][:i + 1])
    print(f"== retakes ({time.time() - started:.0f} s so far)", flush=True)
    retake(argparse.Namespace(list=lang, words=words, engines=",".join(conf["retake"]),
                              takes=args.takes, heard=False))
    collect(argparse.Namespace(list=lang, words=words))
    print(f"== done in {time.time() - started:.0f} s", flush=True)


def collect(args):
    """Copies each text's picked sound, evened, to raw/sounds/<stem>.wav and
    writes raw/sounds.tsv: the file, the text as on the cards, what was said,
    which voice and take, Whisper's chance, the license, and the quizzes."""
    import filecmp

    raw = HERE / args.list / "raw"
    words = read_words(args.list, args.words)
    texts_tsv = HERE / args.list / "texts.tsv"
    original = {r["key"]: r["text"] for r in read_tsv(texts_tsv)} if texts_tsv.exists() else {}
    picks = {f"{r['kind']}-{r['lemma']}": r for r in read_tsv(raw / "picks.tsv")}
    chosen = read_chosen(args.list)
    seeds, retaken = {}, []
    out = raw / "sounds"
    out.mkdir(exist_ok=True)
    rows, copied, missing = [], 0, []
    for kind, lemma, text, quizzes in words:
        stem = f"{kind}-{lemma}"
        p = picks.get(stem)
        if not p or not p["source"]:
            missing.append(text.rstrip("."))
            continue
        src = raw / "listen" / p["source"] / f"{stem}.wav"
        dst = out / f"{stem}.wav"
        if not dst.exists() or not filecmp.cmp(src, dst, shallow=True):
            shutil.copy2(src, dst)
            copied += 1
        if p["source"] not in seeds:
            log = raw / p["source"] / "log.tsv"
            seeds[p["source"]] = {r["file"]: r.get("seed", "") for r in read_tsv(log)} if log.exists() else {}
        seed = seeds[p["source"]].get(f"{stem}.wav", "")
        c = chosen.get(stem, {})
        if c.get("voice") == p["source"] and c.get("seed", "") != seed:
            retaken.append(f"{text.rstrip('.')} ({p['source']} take {c.get('seed') or 1}, now {seed or 1})")
        rows.append([dst.name, original.get(lemma, text.rstrip(".")), text, p["source"], seed,
                     p["chance"], p.get("sure", ""), p.get("chosen", ""),
                     ENGINES.get(p["source"], {}).get("license", ""), quizzes])
    with open(raw / "sounds.tsv", "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, delimiter="\t", lineterminator="\n")
        w.writerow(["file", "text", "said", "voice", "seed", "chance", "sure", "chosen", "license", "quizzes"])
        w.writerows(rows)
    print(f"{len(rows)} sounds in {out} ({copied} copied now); {len(missing)} texts without one: "
          f"{some(missing)}")
    if retaken:
        print(f"chosen, but that voice's sound is another take now (check by ear): {some(retaken)}")


CARD_RATE = 32000  # Hz: an MPEG-1 MP3, which every player reads
CARD_KBPS = 40  # about 4 KB a word; 32 dulls German s, sch and z


def card_format():
    version = subprocess.run(["ffmpeg", "-version"], capture_output=True, text=True, check=True).stdout.split("\n")[0]
    return (f"MP3 (MPEG-1 Layer III), {CARD_RATE} Hz mono, {CARD_KBPS} kbit/s CBR, no tags, "
            f"from raw/sounds/ by {version}")


def cards(args):
    """raw/cards/<stem>.mp3: each text's picked sound (raw/sounds/, evened) as
    the quizzes carry it, an MP3 of about 4 KB a word, the same bytes every
    time. raw/cards/made.tsv notes the sound each was made from (its SHA-1:
    `collect` keeps a sound's date when it copies it), so a sound picked anew
    is made again, as is everything when the settings change."""
    raw = HERE / args.list / "raw"
    words = read_words(args.list, args.words)
    out = raw / "cards"
    out.mkdir(parents=True, exist_ok=True)
    how, stamp, log = card_format(), out / "made.txt", out / "made.tsv"
    if not stamp.exists() or stamp.read_text(encoding="utf-8").strip() != how:
        for old in out.glob("*.mp3"):
            old.unlink()
        log.unlink(missing_ok=True)
    made_from = {r["file"]: r["sound sha1"] for r in read_tsv(log)} if log.exists() else {}
    made, missing = 0, []
    for kind, lemma, text, _ in words:
        stem = f"{kind}-{lemma}"
        src, dst = raw / "sounds" / f"{stem}.wav", out / f"{stem}.mp3"
        if not src.exists():
            missing.append(text.rstrip("."))
            continue
        sha1 = hashlib.sha1(src.read_bytes()).hexdigest()
        if dst.exists() and made_from.get(dst.name) == sha1:
            continue
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(src), "-ac", "1", "-ar", str(CARD_RATE),
                        "-c:a", "libmp3lame", "-b:a", f"{CARD_KBPS}k", "-write_xing", "0", "-id3v2_version", "0",
                        "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(dst)],
                       check=True)
        made_from[dst.name] = sha1
        made += 1
    rewrite_tsv(log, [{"file": f, "sound sha1": h} for f, h in sorted(made_from.items())])
    stamp.write_text(how + "\n", encoding="utf-8")
    print(f"{made} made now, {len(made_from)} in {out}; {len(missing)} texts without a sound: {some(missing)}")


def card_files(lang, texts):
    """For tools/wordlists' build: ({text: (file name, MP3 bytes)} for those of
    `texts` (as the cards show them) that have a card sound (`cards`), and the
    credits for them: which voices said how many, with their licenses."""
    keys = {r["text"]: r["key"] for r in read_tsv(HERE / lang / "texts.tsv")}
    raw = HERE / lang / "raw"
    picked = {r["file"].rsplit(".", 1)[0]: r for r in read_tsv(raw / "sounds.tsv")}
    files, voices = {}, {}
    for text in texts:
        stem = f"{lang}-{keys[text]}" if text in keys else None
        path = raw / "cards" / f"{stem}.mp3"
        if stem and path.exists() and stem in picked:
            files[text] = (path.name, path.read_bytes())
            voices[picked[stem]["voice"]] = voices.get(picked[stem]["voice"], 0) + 1
    credits = ("Sounds: German speech made with these voices, one per word, the one speech recognition "
               "(Whisper large-v3-turbo) was surest of, or one picked by ear "
               "(https://github.com/ehb54/android-apps, tools/sounds):\n"
               + "".join(f"- {ENGINES[v]['license']}: {n}\n" for v, n in sorted(voices.items(), key=lambda x: -x[1]))
               + f"Encoded as MP3, {CARD_RATE} Hz mono, {CARD_KBPS} kbit/s.\n")
    return files, credits


# Picture quizzes whose sounds also make quizzes of their own (eb, 2026-11-13:
# sound quizzes, starting with the picture lists): listening (hear the word,
# pick its picture; the word shows after the answer) and dictation (hear it,
# type it). Every picture quiz in a language also speaks the word after the answer.
HEARD = {
    "Core pictures ERNIE": ("Core listening ERNIE", "Core dictation"),
    "Core verb pictures ERNIE": ("Core verb listening ERNIE", "Core verb dictation"),
}
# A sound for after the answer at the end of a side ("die Hand !de-die_Hand-1a2b3c4d.mp3"), as Quiz reads it.
SOUND_MARK = re.compile(r" !(\S+\.(?:mp3|m4a|aac|ogg|wav|flac))$")
ZIP_DATE = (1980, 1, 1, 0, 0, 0)  # as tools/wordlists writes them: the same content gives the same zip
PICTURE_EXTENSIONS = {"png", "jpg", "jpeg", "webp", "gif", "bmp"}  # as Quiz reads them (model/Media.kt)


def write_zip(path, entries):
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for name, data in entries:
            info = zipfile.ZipInfo(name, date_time=ZIP_DATE)
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)


def samples(args):
    """Adds sounds to the sample quizzes in args.dir, as tools/wordlists built
    them: in every picture quiz whose answers are in the language (QUIZ_SIDES),
    each answer gets its sound for after the answer, and the quizzes in HEARD
    give a listening and a dictation quiz besides. Makes the card sounds it
    needs (`cards`). The pictures are kept as they are and sounds added before
    are replaced, so it can run again, after every build of the word lists."""
    lang, folder = args.list, Path(args.dir)
    texts_tsv = {r["text"]: r["key"] for r in read_tsv(HERE / lang / "texts.tsv")}
    every = {f"{k}-{l}": (k, l, t, q) for k, l, t, q in read_words(lang, "all")}
    for path in sorted(folder.glob("*.zip")):
        z = zipfile.ZipFile(path)
        names = z.namelist()
        inner = next((n for n in names if n.endswith(".qzx")), None)
        sides = next((langs for pattern, langs in QUIZ_SIDES if re.search(pattern, path.stem)), None)
        if inner is None or sides != ("", lang):
            continue
        cards_ = [(a, SOUND_MARK.sub("", b)) for a, b in qzx_cards(z.read(inner).decode("utf-8"))]
        if not all(a.startswith("!") and a[1:] in names and a[1:].rsplit(".", 1)[-1] in PICTURE_EXTENSIONS
                   for a, _ in cards_):
            continue  # not picture -> word (a dictation quiz made here before)
        answers = [unicodedata.normalize("NFC", " ".join(b.split())) for _, b in cards_]
        wanted = [every[f"{lang}-{texts_tsv[t]}"] for t in answers if t in texts_tsv]
        cards(argparse.Namespace(list=lang, words=wanted))
        files, sound_credits = card_files(lang, answers)
        old_sounds = {m.group(1) for _, b in qzx_cards(z.read(inner).decode("utf-8")) if (m := SOUND_MARK.search(b))}
        credits = z.read("CREDITS.txt").decode("utf-8").split("Sounds: ")[0] if "CREDITS.txt" in names else ""
        pictures = [(n, z.read(n)) for n in names if n not in old_sounds and n not in (inner, "CREDITS.txt")
                    and n.rsplit(".", 1)[-1] in PICTURE_EXTENSIONS]
        rest = [(n, z.read(n)) for n in names if n not in old_sounds and n not in (inner, "CREDITS.txt")
                and n.rsplit(".", 1)[-1] not in PICTURE_EXTENSIONS]
        sounds = list(dict.fromkeys(files[t] for t in answers if t in files))
        qzx = "".join(f"{a}\n{b}{' !' + files[t][0] if t in files else ''}\n"
                      for (a, b), t in zip(cards_, answers))
        write_zip(path, pictures + sounds + [(inner, qzx), ("CREDITS.txt", credits + sound_credits)] + rest)
        print(f"{path.name}: {len(files)} of {len(answers)} answers spoken after the answer")
        if path.stem not in HEARD:
            continue
        listening, dictation = HEARD[path.stem]
        heard = [(a, t, files[t][0]) for (a, _), t in zip(cards_, answers) if t in files]
        used = {a[1:] for a, _, _ in heard}
        write_zip(folder / f"{listening}.zip",
                  [(n, d) for n, d in pictures if n in used] + sounds
                  + [(f"{listening}.qzx", "".join(f"!{s} {t}\n{a}\n" for a, t, s in heard)),
                     ("CREDITS.txt", credits + sound_credits)])
        write_zip(folder / f"{dictation}.zip",
                  sounds + [(f"{dictation}.qzx", "".join(f"!{s}\n{t}\n" for _, t, s in heard)),
                            ("CREDITS.txt", sound_credits)])
        print(f"  {listening}.zip, {dictation}.zip: {len(heard)} cards")


def page(args):
    words, raw, sounds = evened(args)
    listen = raw / "listen"
    columns = []
    for src, files in sounds.items():
        log = read_tsv(raw / src / "log.tsv") if (raw / src / "log.tsv").exists() else []
        made = [float(r["seconds"]) for r in log if r.get("seconds")]
        licenses = sorted({r["license"] for r in log if r.get("license")})
        heard = {r["file"]: r for r in read_tsv(raw / src / "heard.tsv")} if (raw / src / "heard.tsv").exists() else {}
        chance = scores(raw, src)
        evlog = raw / "listen" / src / "evened.tsv"
        limited = {r["file"]: float(r["limited"]) for r in read_tsv(evlog)} if evlog.exists() else {}
        seeds = {r["file"].rsplit(".", 1)[0]: r.get("seed", "") for r in log}
        takes_tsv = raw / src / "takes" / "score.tsv"
        ntakes = {}
        for r in read_tsv(takes_tsv) if takes_tsv.exists() else []:
            stem_ = r["file"].rsplit("~", 1)[0]
            ntakes[stem_] = ntakes.get(stem_, 0) + 1
        have = {stem: (f"{src}/{p.name}", heard.get(stem), chance.get(stem), limited.get(stem, 0.0),
                       (seeds.get(stem), ntakes.get(stem, 0)))
                for stem, p in files.items()}
        note = ENGINES[src]["license"] if src in ENGINES else "recordings; " + ", ".join(licenses)
        if made:
            note += f"; {sum(made) / len(made):.1f} s to make a word here"
        if heard:
            same = sum(1 for stem in files if heard.get(stem, {}).get("same") == "yes")
            note += f"; Whisper heard {same} of {len(files)} as the word"
        if chance:
            note += f", average chance {average(list(chance.values())):.0%}"
        columns.append((src, note, have))
    picked_from = [src for src in sounds if src not in NOT_PICKED]
    chosen = read_chosen(args.list)
    ranked = whisper_picks(raw, words, picked_from)
    wins = {}
    for r in ranked.values():
        if r:
            wins[r[0][1]] = wins.get(r[0][1], 0) + 1

    e = html.escape
    head = "".join(
        f'<th scope="col">{e(src)}<small>{e(note)}'
        f'{f"; Whisper picks it for {wins[src]}" if src in wins else ""}'
        f'{"; not picked: " + NOT_PICKED[src] if src in NOT_PICKED else ""}</small></th>'
        for src, note, _ in columns)
    rows = []
    for kind, lemma, text, english in words:
        stem = f"{kind}-{lemma}"
        best = ranked[stem][0][1] if ranked[stem] else ""
        cells = []
        for src, _, have in columns:
            if stem in have:
                path, h, p, lim, (seed, n) = have[stem]
                heard = ""
                if h:
                    heard = f'<small class="heard{"" if h["same"] == "yes" else " miss"}">heard: {e(h["heard"])}</small>'
                if p is not None:
                    su = sureness(p, text)
                    heard += (f'<small class="heard{" miss" if su < DOUBT_SURE else ""}">'
                              f'Whisper: {su:.0%} a piece (chance {p:.0%})</small>')
                if n > 1:
                    heard += f'<small class="heard">take {e(str(seed))}, the best of {n}</small>'
                if lim >= LISTEN_LIMITED:
                    heard += f'<small class="heard miss">peaks limited {lim:.1f} dB</small>'
                mark = '<small class="wtag">Whisper\'s pick</small>' if src == best else ""
                if chosen.get(stem, {}).get("voice") == src:
                    mark += f'<small class="wtag">chosen ({e(chosen[stem]["by"])})</small>'
                cells.append(
                    f'<td{" class=wpick" if src == best else ""}>'
                    f'<audio controls preload="none" src="{e(path)}"></audio>{heard}{mark}'
                    f'<label><input type="radio" name="{e(stem)}" value="{e(src)}"> best</label></td>')
            else:
                cells.append('<td class="none">none</td>')
        rows.append(
            f'<tr data-word="{e(stem)}" data-whisper="{e(best)}"><th scope="row">'
            f'<button class="play" title="Play the row">▶</button>'
            f' {e(text.rstrip("."))}<small>{e(kind)} · {e(english)}</small></th>{"".join(cells)}</tr>')
    # Named after the word file (core/pilot.txt: pilot.html), each with its own marks.
    name = Path(args.words).stem if isinstance(args.words, str) and args.words != "all" else "all"
    out = listen / f"{name}.html"
    key = "sound-pilot-picks" if name == "pilot" else f"sound-picks-{args.list}-{name}"
    out.write_text(PAGE.format(
        title=e(f"Sounds: {args.list}, {name}"), count=len(words), head=head,
        rows="\n".join(rows), key=json.dumps(key)), encoding="utf-8")
    print(f"wrote {out} ({len(words)} words × {len(columns)} sources)")


PAGE = """<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{title}</title>
<style>
:root {{ --bg: #fbfaf7; --fg: #1d1d1b; --muted: #6b6a66; --line: #e3e0d8; --pick: #e8f1e4; --miss: #b3261e; --accent: #2f5fa7; }}
@media (prefers-color-scheme: dark) {{
  :root {{ --bg: #1b1b1a; --fg: #ecebe6; --muted: #9a9890; --line: #34332f; --pick: #263424; --miss: #f2847b; --accent: #8fb4ee; }}
}}
body {{ background: var(--bg); color: var(--fg); font: 15px/1.4 -apple-system, system-ui, sans-serif; margin: 16px; }}
h1 {{ font-size: 20px; margin: 0 0 4px; }}
p {{ color: var(--muted); margin: 0 0 12px; max-width: 70ch; }}
.bar {{ display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }}
.wrap {{ overflow-x: auto; }}
table {{ border-collapse: collapse; }}
th, td {{ border-bottom: 1px solid var(--line); padding: 6px 8px; text-align: left; vertical-align: top; }}
thead th {{ position: sticky; top: 0; background: var(--bg); font-size: 13px; max-width: 220px; }}
th small {{ display: block; color: var(--muted); font-weight: normal; font-size: 12px; }}
tbody th {{ white-space: nowrap; font-size: 16px; }}
audio {{ width: 210px; height: 32px; display: block; }}
label {{ font-size: 12px; color: var(--muted); }}
td.none {{ color: var(--muted); font-size: 12px; }}
.heard {{ display: block; font-size: 12px; color: var(--muted); }}
.heard.miss {{ color: var(--miss); font-weight: 600; }}
td.picked {{ background: var(--pick); }}
td.wpick {{ box-shadow: inset 3px 0 0 var(--accent); }}
.wtag {{ display: block; font-size: 12px; font-weight: 600; color: var(--accent); }}
button {{ font: inherit; cursor: pointer; }}
.play {{ border: 1px solid var(--line); background: none; color: var(--fg); border-radius: 4px; padding: 0 6px; }}
</style></head><body>
<h1>{title}</h1>
<p>{count} words. Every sound is trimmed and evened to the same loudness. ▶ plays a row
from left to right. Mark the best per word, then copy the picks as TSV.
Sounds whose peaks the limiter turned down by 4 dB or more are flagged: listen for a squashed sound.
"Whisper" is how sure Whisper (speech recognition) is of writing exactly the word for that
sound, per piece of the text (the bar is 90%; red below 80%), with its chance for the whole
text; "heard" is what it writes down. The source it is surest of is marked "Whisper's pick",
and a sound chosen over it (chosen.tsv) "chosen", by ear or from what Whisper wrote down.</p>
<div class="bar"><button id="copy">Copy picks</button><span id="tally"></span></div>
<div class="wrap"><table>
<thead><tr><th scope="col">word</th>{head}</tr></thead>
<tbody>
{rows}
</tbody></table></div>
<script>
const KEY = {key};
let picks = {{}};
try {{ picks = JSON.parse(localStorage.getItem(KEY) || "{{}}"); }} catch (e) {{}}
function save() {{ try {{ localStorage.setItem(KEY, JSON.stringify(picks)); }} catch (e) {{}} }}
function show() {{
  const counts = {{}};
  let agree = 0;
  document.querySelectorAll("tbody tr").forEach(tr => {{
    const p = picks[tr.dataset.word];
    tr.querySelectorAll("td").forEach(td => {{
      const r = td.querySelector("input");
      td.classList.toggle("picked", !!r && r.value === p);
      if (r) r.checked = r.value === p;
    }});
    if (p) {{ counts[p] = (counts[p] || 0) + 1; agree += p === tr.dataset.whisper; }}
  }});
  document.getElementById("tally").textContent = Object.keys(counts).length
    ? "Picked: " + Object.entries(counts).sort((a, b) => b[1] - a[1]).map(([k, v]) => k + " " + v).join(" · ")
      + " · same as Whisper's pick: " + agree + " of " + Object.values(counts).reduce((a, b) => a + b, 0)
    : "Nothing picked yet";
}}
document.addEventListener("change", e => {{
  if (e.target.type === "radio") {{ picks[e.target.name] = e.target.value; save(); show(); }}
}});
document.addEventListener("click", async e => {{
  if (!e.target.classList.contains("play")) return;
  for (const a of e.target.closest("tr").querySelectorAll("audio")) {{
    document.querySelectorAll("audio").forEach(x => x.pause());
    a.currentTime = 0;
    await a.play().catch(() => {{}});
    await new Promise(r => {{ a.onended = r; a.onpause = r; }});
    await new Promise(r => setTimeout(r, 250));
  }}
}});
document.getElementById("copy").onclick = () => {{
  const lines = ["kind\\tlemma\\tbest\\twhisper"];
  document.querySelectorAll("tbody tr").forEach(tr => {{
    const [kind, ...rest] = tr.dataset.word.split("-");
    lines.push([kind, rest.join("-"), picks[tr.dataset.word] || "", tr.dataset.whisper].join("\\t"));
  }});
  navigator.clipboard.writeText(lines.join("\\n") + "\\n").then(
    () => document.getElementById("copy").textContent = "Copied",
    () => alert(lines.join("\\n")));
}};
show();
</script></body></html>
"""


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    g = sub.add_parser("generate")
    g.add_argument("list")
    g.add_argument("engine", choices=ENGINES)
    g.add_argument("words")
    g.add_argument("seed", nargs="?", type=int, default=1)
    t = sub.add_parser("texts")
    t.add_argument("lang")
    w = sub.add_parser("wikimedia")
    w.add_argument("list")
    w.add_argument("words")
    for name in ("hear", "pick", "page"):
        p = sub.add_parser(name)
        p.add_argument("list")
        p.add_argument("words")
        p.add_argument("sources", nargs="?", help="comma-separated; default: every source made")
    m = sub.add_parser("make")
    m.add_argument("list", help="a language with texts.tsv, e.g. de")
    m.add_argument("words", nargs="?", default="all")
    m.add_argument("takes", nargs="?", type=int, default=4, help="retakes beyond the first (default 4)")
    c = sub.add_parser("collect")
    c.add_argument("list")
    c.add_argument("words", nargs="?", default="all")
    cs = sub.add_parser("cards")
    cs.add_argument("list")
    cs.add_argument("words", nargs="?", default="all")
    sa = sub.add_parser("samples")
    sa.add_argument("list", help="a language with texts.tsv, e.g. de")
    sa.add_argument("dir", help="the sample quizzes, e.g. ../../apps/quiz/samples")
    r = sub.add_parser("retake")
    r.add_argument("list")
    r.add_argument("words")
    r.add_argument("engines", nargs="?", default=RETAKE_ENGINES, help="comma-separated engines")
    r.add_argument("takes", nargs="?", type=int, default=4, help="takes beyond the first (default 4)")
    r.add_argument("--below", type=float, default=SURE,
                   help=f"retake texts picked less sure than this a piece (default {SURE})")
    r.set_defaults(heard=False)  # picks need only the scores; writing down every sound is slow
    ch = sub.add_parser("choose")
    ch.add_argument("list")
    ch.add_argument("picks", help="TSV with kind, lemma, best (and by, why): a page's Copy picks")
    args = ap.parse_args()
    {"generate": generate, "texts": texts, "wikimedia": wikimedia, "hear": hear, "pick": pick, "retake": retake, "make": make, "collect": collect,
     "choose": choose, "cards": cards, "samples": samples, "page": page}[args.cmd](args)


if __name__ == "__main__":
    main()

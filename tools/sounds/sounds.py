#!/usr/bin/env python3
"""Sounds for the word lists' cards: each German word, spoken.

Pilot stage (see README.md): makes the words of a list's pilot with several
voice models, fetches the recordings Wikidata links for comparison, and
writes a page to listen to them side by side.

  generate <list> <engine> <words> [seed]   raw/<engine>/<kind>-<lemma>.wav, log.tsv
  wikimedia <list> <words>                  raw/wikimedia/: recordings via Wikidata
  hear <list> <words> [sources]             raw/<source>/heard.tsv: what Whisper hears;
                                            score.tsv: its chance of writing the word
  pick <list> <words> [sources]             raw/picks.tsv: per word, the source
                                            Whisper is surest of
  retake <list> <words> [engines] [takes]   more takes of the words picked below
                                            RETAKE_BELOW; the best take stays
  page <list> <words> [sources]             raw/listen/: trimmed, evened copies
                                            and pilot.html

<words> is a file of "noun Haus" / "verb gehen" lines (core/pilot.txt) or
"all". A noun is spoken with its article ("das Haus."), a verb as its
infinitive ("gehen."). Every step skips files already made.
"""

import argparse
import csv
import gc
import html
import importlib.metadata
import json
import re
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import wave
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORDLISTS = HERE.parent / "wordlists"
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
# Sources `pick` leaves out unless named.
NOT_PICKED = {
    WIKIMEDIA: "says the bare word, without the article, and each speaker needs a credit",
    "piper-kerstin": "16 kHz 'low' voice: poor in the pilot",
    "kugelaudio": "dropped after the pilot: best for 1 word of 52, stray sounds before "
                  "words, and it needs a 17 GB Mac and a patched mlx-audio",
    "kugelaudio-cfg8": "worse than CFG 3 to the ear: odd sounds, music in some",
}


def read_tsv(path):
    with open(path, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f, delimiter="\t", quoting=csv.QUOTE_NONE))


def read_words(list_name, words_arg):
    """(kind, lemma, text, english) for each word, in the order given."""
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
    from piper import PiperVoice

    voice = cfg["voice"]
    onnx = VOICES / f"{voice}.onnx"
    if not onnx.exists():
        VOICES.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            [sys.executable, "-m", "piper.download_voices", voice, "--download-dir", str(VOICES)],
            check=True,
        )
    pv = PiperVoice.load(str(onnx))
    version = "piper-tts " + importlib.metadata.version("piper-tts")

    def synth(text, path, seed):
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


def whisper_chance(model):
    """chance(path, text): the probability that Whisper, transcribing the
    sound as German, writes exactly the text and stops (summed over the
    spellings it may use: capital or not, full stop or not). Sounds of the
    same word compare directly; a word said unclearly, or wrongly, or with
    more after it scores low."""
    import mlx.core as mx
    from mlx_audio.stt.models.whisper.audio import N_FRAMES, pad_or_trim

    tok = model.get_tokenizer(language="de", task="transcribe")
    prefix = list(tok.sot_sequence_including_notimestamps)

    def spellings(text):
        t = text.rstrip(".")
        return sorted({f + p for f in (t, t[:1].upper() + t[1:]) for p in (".", "")})

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
    model = chance = None
    for src, have in sounds.items():
        heard_tsv, score_tsv = raw / src / "heard.tsv", raw / src / "score.tsv"
        heard_done, score_done = still_good(heard_tsv, have), still_good(score_tsv, have)
        right, heard_now, chances = 0, 0, []
        for kind, lemma, text, _ in words:
            stem = f"{kind}-{lemma}"
            if stem not in have or (stem in heard_done and stem in score_done):
                continue
            model = model or load_model(HEAR_MODEL)
            said = said_by(src, kind, lemma, text)
            if stem not in heard_done:
                r = model.generate(str(have[stem]), language="de", verbose=None)  # None: no progress bar
                heard = (r.text if hasattr(r, "text") else str(r)).strip()
                same = bare(heard) == bare(said)
                right += same
                heard_now += 1
                append_log(heard_tsv, {"file": stem, "said": said, "heard": heard,
                                       "same": "yes" if same else "no", "model": HEAR_MODEL})
            if stem not in score_done:
                chance = chance or whisper_chance(model)
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


DOUBT = 0.5  # a pick Whisper gives less than this chance needs an ear
LISTEN_LIMITED = 4.0  # dB: the page flags sounds limited this much, to check by ear


RETAKE_BELOW = 0.8  # a word whose pick has less chance gets more takes (`retake`)
RETAKE_ENGINES = "qwen3-design,omnivoice"  # seeded (MLX), so each take differs


def retake(args):
    """For each word whose pick Whisper gives less than RETAKE_BELOW, makes
    more takes (seeds 1 to 1 + takes) with each engine named, into
    raw/<engine>/takes/<kind>-<lemma>~<seed>.wav (with log.tsv and
    score.tsv there), and makes the take Whisper is surest of that engine's
    sound for the word: raw/<engine>/<kind>-<lemma>.wav, its log.tsv row
    then giving that seed. Then picks again."""
    from mlx_audio.stt.utils import load_model

    raw = HERE / args.list / "raw"
    engines = args.engines.split(",")
    for e in engines:
        if ENGINES[e]["runner"] != "mlx":
            sys.exit(f"{e}: only MLX engines take a seed, so only they make different takes")
    args.sources = None
    args.sources = ",".join(candidates(raw, args))
    hear(args)
    words = read_words(args.list, args.words)
    ranked = whisper_picks(raw, words, args.sources.split(","))
    weak = [(k, l, t) for k, l, t, _ in words
            if not ranked[f"{k}-{l}"] or ranked[f"{k}-{l}"][0][0] < RETAKE_BELOW]
    print(f"{len(weak)} words picked below {RETAKE_BELOW:.0%}: "
          + (", ".join(f"{t.rstrip('.')} ({ranked[f'{k}-{l}'][0][0]:.0%})" for k, l, t in weak) or "none"))
    if not weak:
        return

    def main_seed(e, stem):
        log = raw / e / "log.tsv"
        rows = {r["file"]: r for r in read_tsv(log)} if log.exists() else {}
        return int(rows.get(f"{stem}.wav", {}).get("seed") or 1)

    # Make the takes, one engine at a time (the models are large).
    for e in engines:
        takes = raw / e / "takes"
        takes.mkdir(parents=True, exist_ok=True)
        log = raw / e / "log.tsv"
        rows = {r["file"]: r for r in read_tsv(log)} if log.exists() else {}
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
        synth, model, version = mlx_synth(ENGINES[e])
        for k, l, t, s in todo:
            path = takes / f"{k}-{l}~{s}.wav"
            start = time.time()
            synth(t, path, s)
            seconds, length = time.time() - start, wav_seconds(path)
            print(f"  {t:<24} seed {s}: {seconds:4.1f} s to make, {length:4.2f} s long")
            append_log(takes / "log.tsv", made_row(path, k, l, t, e, model, version, s, seconds, length))
        del synth
        gc.collect()

    # Score every take, evened like the rest, and keep the best per engine.
    chance = whisper_chance(load_model(HEAR_MODEL))
    for e in engines:
        takes, listen = raw / e / "takes", raw / "listen" / e / "takes"
        listen.mkdir(parents=True, exist_ok=True)
        score_tsv = takes / "score.tsv"
        got = {r["file"]: float(r["chance"]) for r in read_tsv(score_tsv)} if score_tsv.exists() else {}
        made = {r["file"]: r for r in read_tsv(takes / "log.tsv")} if (takes / "log.tsv").exists() else {}
        for k, l, t in weak:
            stem = f"{k}-{l}"
            for path in sorted(takes.glob(f"{stem}~*.wav")):
                if path.stem not in got:
                    even(path, listen / path.name)
                    got[path.stem] = chance(listen / path.name, said_by(e, k, l, t))
                    append_log(score_tsv, {"file": path.stem, "said": t, "chance": f"{got[path.stem]:.4f}",
                                           "model": HEAR_MODEL})
            mine = sorted(((c, int(f.rsplit("~", 1)[1])) for f, c in got.items() if f.startswith(stem + "~")),
                          reverse=True)
            if not mine:
                continue
            (best, seed), was = mine[0], main_seed(e, stem)
            had = dict((s_, c_) for c_, s_ in mine).get(was, 0.0)
            if seed == was:
                print(f"  {e} {l}: take {seed} stays ({best:.0%}); best of {len(mine)}")
                continue
            # A fresh copy (new time), so it is evened and scored again as the main sound.
            shutil.copyfile(takes / f"{stem}~{seed}.wav", raw / e / f"{stem}.wav")
            log = raw / e / "log.tsv"
            rows = read_tsv(log)
            rows = [({**made[f"{stem}~{seed}.wav"], "file": f"{stem}.wav"}
                     if r["file"] == f"{stem}.wav" and f"{stem}~{seed}.wav" in made else r) for r in rows]
            log.unlink()
            for r in rows:
                append_log(log, r)
            print(f"  {e} {l}: take {seed} ({best:.0%}) replaces take {was} ({had:.0%}); best of {len(mine)}")
    pick(args)


def pick(args):
    """Scores what is missing, then writes raw/picks.tsv: for each word, the
    source Whisper is surest says it, and the runner-up."""
    raw = HERE / args.list / "raw"
    args.sources = ",".join(candidates(raw, args))
    hear(args)
    words = read_words(args.list, args.words)
    ranked = whisper_picks(raw, words, args.sources.split(","))
    out = raw / "picks.tsv"
    out.unlink(missing_ok=True)
    counts, doubtful = {}, []
    for kind, lemma, text, _ in words:
        r = ranked[f"{kind}-{lemma}"] + [(0.0, "")] * 2
        (p, src), (p2, src2) = r[0], r[1]
        append_log(out, {"kind": kind, "lemma": lemma, "text": text, "source": src,
                         "chance": f"{p:.4f}", "next": src2, "next chance": f"{p2:.4f}"})
        counts[src] = counts.get(src, 0) + 1
        if p < DOUBT:
            doubtful.append(f"{text.rstrip('.')} ({src} {p:.0%})")
    print(f"from {args.sources}:")
    for src, n in sorted(counts.items(), key=lambda x: -x[1]):
        print(f"  {src or 'none'}: {n}")
    print(f"{len(doubtful)} below {DOUBT:.0%}, to listen to: {', '.join(doubtful) or 'none'}")
    print(f"wrote {out}")


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
                    heard += f'<small class="heard{" miss" if p < DOUBT else ""}">Whisper\'s chance: {p:.0%}</small>'
                if n > 1:
                    heard += f'<small class="heard">take {e(str(seed))}, the best of {n}</small>'
                if lim >= LISTEN_LIMITED:
                    heard += f'<small class="heard miss">peaks limited {lim:.1f} dB</small>'
                mark = '<small class="wtag">Whisper\'s pick</small>' if src == best else ""
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
    out = listen / "pilot.html"
    out.write_text(PAGE.format(
        title=e(f"Sound pilot: {args.list}"), count=len(words), head=head,
        rows="\n".join(rows)), encoding="utf-8")
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
"Whisper's chance" is how likely Whisper (speech recognition) is to write exactly the word
for that sound; the source it is surest of is marked "Whisper's pick".</p>
<div class="bar"><button id="copy">Copy picks</button><span id="tally"></span></div>
<div class="wrap"><table>
<thead><tr><th scope="col">word</th>{head}</tr></thead>
<tbody>
{rows}
</tbody></table></div>
<script>
const KEY = "sound-pilot-picks";
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
    w = sub.add_parser("wikimedia")
    w.add_argument("list")
    w.add_argument("words")
    for name in ("hear", "pick", "page"):
        p = sub.add_parser(name)
        p.add_argument("list")
        p.add_argument("words")
        p.add_argument("sources", nargs="?", help="comma-separated; default: every source made")
    r = sub.add_parser("retake")
    r.add_argument("list")
    r.add_argument("words")
    r.add_argument("engines", nargs="?", default=RETAKE_ENGINES, help="comma-separated MLX engines")
    r.add_argument("takes", nargs="?", type=int, default=4, help="takes beyond the first (default 4)")
    args = ap.parse_args()
    {"generate": generate, "wikimedia": wikimedia, "hear": hear, "pick": pick, "retake": retake,
     "page": page}[args.cmd](args)


if __name__ == "__main__":
    main()

# Sounds for the word lists

Makes a sound for each word of the word lists (`../wordlists/`): the German
word spoken, so a quiz can ask sound → word (hear it, type it), word → sound,
or sound ↔ picture with the lists' pictures (`../pictures/`). A noun is
spoken with its article ("das Haus."), so the sound also teaches the gender;
a verb as its infinitive.

**Stage: pilot.** 52 core words (the 40 of the picture pilot plus 12
pronunciation traps, `core/pilot.txt`) made with each candidate and set
beside the recordings Wikimedia has, to choose a source before a full run.

`sounds.py` does the work. It runs in a Python venv in this folder (`.venv/`,
git-ignored, made by `setup.sh` from `requirements.txt`) through `run.sh`,
which reads `tools/local.env` for the machine's settings:

| Setting in `tools/local.env` | What |
|---|---|
| `SOUNDS_PYTHON` | Python 3.11 or newer to make the venv with (default `python3`) |
| `HF_HOME` | Hugging Face cache for the model weights (1–5 GB per model; KugelAudio 15 GB) |
| `TMPDIR`, `PIP_CACHE_DIR`, `XDG_CACHE_HOME` | optional: where temp files and caches go |

`ffmpeg` must be on the PATH: it decodes every sound and measures its loudness for evening.

## Sources

The sounds are published on the download site (and maybe Play), so a model's
license must allow redistributing what it makes. Ruled out for that reason:
Voxtral TTS (CC BY-NC 4.0), XTTS-v2 (Coqui Public Model License), Fish Audio
S2 (non-commercial). Kokoro has no German.

Pilot on an M1 Pro (16 GB): piper-tts 1.8.0, mlx-audio 0.6.4, MLX 0.32.4.
Whisper large-v3-turbo checks each sound (`hear`). "Heard" is how many of the
52 it wrote down as the word; "chance" is the average chance that it writes
exactly the word (see below); "picked" is how many words it is surest of with
that source (`pick`). Whisper is a screen, not a verdict: it also slips on one
word with no context (it turns near-silence into "Vielen Dank"), and it judges
clarity, not how pleasant a voice is.

| Source | License | Time per word | Heard | Chance | Picked | Notes |
|---|---|---|---|---|---|---|
| `wikimedia`: recordings Wikidata links (P443), else `File:De-<lemma>.ogg` | CC BY-SA 3.0/4.0, one CC BY 3.0: credit each speaker | download | 46 | 67% | — | all 52 found; two speakers made 42 of them; bare words, no article; one says Restaurant twice |
| `piper-thorsten`: Piper `de_DE-thorsten-high` | voice trained on Thorsten-Voice (CC0) | 0.2 s (CPU) | 46 | 74% | 15 | one male voice; Gasse heard as Kasse, Mädchen as Mietchen |
| `piper-kerstin`: Piper `de_DE-kerstin-low` | voice trained on Kerstin (CC0) | 0.05 s | 12 | 10% | — | 16 kHz "low" quality: not usable |
| `qwen3-design`: Qwen3-TTS 1.7B VoiceDesign, bf16 | Apache 2.0 | 0.9 s | 50 | 83% | 30 | voice described in words (`instruct`); missed Nacht and Büro |
| `chatterbox`: Chatterbox Multilingual, fp16 | MIT | 1.7 s | 35 | 38% | 2 | default voice is English: an English accent comes through (Apfel as "Upflow") |
| `omnivoice`: OmniVoice, bf16 | Apache 2.0 | 1.8 s | 45 | 56% | 4 | several misses are near-silence |
| `kugelaudio`: KugelAudio 7B, bf16, voice `clear` | MIT | 8.4 s (M5 Max) | 26 | 30% | 1 | made on the M5 Max (17 GB of memory); needs the patch below; good at sentences, weak on single words: often a stray sound before the word ("jeder Apfel") or a verb heard as "den"; but the only clear Gasse (90%) |
| `kugelaudio-cfg8`: the same at CFG 8 | MIT | 8.7 s (M5 Max) | 23 | 23% | — | stronger text guidance; no better; not picked |

### Whisper's chance and pick

`hear` gives each sound a chance: the probability that Whisper, transcribing
it as German, writes exactly the word and then stops, summed over the
spellings it may use (capital or not, full stop or not). It is computed from
Whisper's own token probabilities, by feeding it the expected text. A word said
unclearly or wrongly, or followed by more sound, scores low. The chance compares
sounds of the same word, so `pick` takes, for each word, the source with the
highest chance. It leaves out `NOT_PICKED` in `sounds.py`: Wikimedia (bare
words, credits), Kerstin and the second KugelAudio setting. A pick below 50% needs an ear. Scoring takes about
0.7 s per sound on the M1 Pro.

In the pilot, about 10 of the 52 picks win by under two points, which is noise: on
such close calls the pick flips with small changes to trimming or level. Picks
with a high chance stay put. Whisper is best at ruling sounds out.

### Evening

Every sound Whisper scores and every sound on the page is an evened copy
(`raw/listen/<source>/`, made by `even`; `evened.txt` there records how, and
copies made another way are made again):

1. Decoded to mono at 24 kHz.
2. The quiet ends cut: everything before the first and after the last 10 ms
   that is within 40 dB of the sound's loudest 10 ms, keeping 30 ms either side,
   with 5 ms fades. The cut is relative to each sound, so a quiet recording or
   a noise floor trims the same way.
3. One gain to -14 LUFS integrated loudness (EBU R128): loud, as phones want.
   Loudness is measured on ten copies of the sound in a row, because R128
   measures in 400 ms blocks and many words are shorter.
4. A peak limiter (`limit`) turns down only the moments that would pass
   -1 dBFS, so nothing clips. It looks ahead 5 ms and recovers over 50 ms;
   everything else in the sound is left as it was. `evened.tsv` beside the
   copies records each sound's loudness before, its gain and how far it was
   limited. The page flags 4 dB or more, to check by ear for a squashed sound.

At -14 LUFS, 51 of the 52 picks are turned up (by as much as 15 dB) and none
clips. Most need little limiting (12 none, 19 under 2 dB), but 11 need more
than 4 dB and 5 more than 6 dB. Thorsten's sounds come out of Piper peaking at
full scale, and a few words have a sharp plosive. Limiting that deep also takes
some loudness away, so those picks end up at -15 to -17.5 LUFS. At -16 LUFS
the limiting would be about 2 dB less.

The first version used ffmpeg's `silenceremove` and one-pass `loudnorm`. On
these short sounds `loudnorm` left some words 30 dB too quiet (down to -51
LUFS), and `silenceremove` (ffmpeg 5.0) dropped a whole recording that starts
loud. ffmpeg now only decodes and measures.

### KugelAudio needs a patch

mlx-audio 0.6.4's KugelAudio port turns every word into seconds of made-up
babble: it passes the diffusion timesteps as integers, which zeroes the
timestep embedding. It also ignores the `voice` argument. The M5 Max run fixed
both in `patches/mlx-audio-0.6.4-kugelaudio.patch`, against the venv's
`site-packages/`: float timesteps, and upstream's voice prompt from
`voices/<voice>.pt`, which needs `torch` to read. To make KugelAudio sounds
again, apply it (`patch -p1 -d .venv/lib/python3.*/site-packages <
patches/mlx-audio-0.6.4-kugelaudio.patch`) and `pip install torch` in the venv.
The other engines don't need either. `requirements.txt` stays without torch
for now; it needs the patch anyway. The timestep bug is filed upstream as
[mlx-audio issue 1129](https://github.com/Neywa-Labs/mlx-audio/issues/1129)
(mlx-audio moved from Blaizzy/mlx-audio to Neywa-Labs/mlx-audio). Until a
fixed release is out, KugelAudio needs the patch.

Not tried yet: a German reference voice for the cloning models (Qwen3-TTS
Base, Chatterbox), for example a Thorsten-Voice recording (CC0), which may
remove Chatterbox's accent.

## A list's folder

| File | What | Made by |
|---|---|---|
| `pilot.txt` | the words of the pilot (`noun Haus`, `verb gehen`) | by hand |
| `raw/<source>/` | the sounds `<kind>-<lemma>.wav` (Wikimedia: `.ogg`), `log.tsv` (model, settings, seed, text, time; Wikimedia: file, license, speaker, page), `heard.tsv` and `score.tsv` | `generate`, `wikimedia`, `hear` (git-ignored) |
| `raw/picks.tsv` | per word, the source Whisper is surest of, and the runner-up | `pick` (git-ignored) |
| `raw/listen/` | every sound evened (see Evening), and `pilot.html` to compare them | `hear`, `pick`, `page` (git-ignored) |

## Steps

Every step skips work already done.

1. `tools/sounds/run.sh generate <list> <engine> <words> [seed]` makes each
   word with one engine (`ENGINES` in `sounds.py`); `<words>` is a file like
   `core/pilot.txt` (relative to `tools/sounds`) or `all`. Models download
   into `HF_HOME` on first use; Piper voices into `voices/` (git-ignored).
2. `run.sh wikimedia <list> <words>` fetches one recording per word, at most
   one download a second (Wikimedia answers faster ones with HTTP 429).
3. `run.sh hear <list> <words>` has Whisper write down each sound and score
   its chance. `run.sh pick <list> <words>` does the same, then writes
   `raw/picks.tsv` and lists the picks below 50%.
4. `run.sh page <list> <words>` writes `raw/listen/pilot.html`. Open it
   through a local web server, e.g.
   `python3 -m http.server 8765 --directory tools/sounds/core/raw/listen`,
   then `http://127.0.0.1:8765/pilot.html`. ▶ plays a row. Each sound shows
   what Whisper heard and its chance, and Whisper's pick has a blue stripe.
   Mark the best per word; *Copy picks* gives yours beside Whisper's as TSV.

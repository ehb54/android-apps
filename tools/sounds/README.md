# Sounds for the quizzes

Makes a sound for every text on the sample quizzes' cards in a language:
each German text spoken, once, however many quizzes it is on. What the
sounds become is open: sound → word or sound ↔ picture quizzes, or a sound
to play beside a card's text. A noun card says "der Hund", so its sound also
teaches the gender.

**Stage: German.** `de/` holds the German texts of all sample quizzes
(21,364 texts on 39,922 card sides) and makes their sounds; see *A
language's sounds*. The voices were chosen in a pilot first: 52 core words
(`core/pilot.txt`) made with each candidate voice and set beside the
recordings Wikimedia has (the sections from *Sources* to *KugelAudio needs a
patch*).

`sounds.py` does the work. It runs in a Python venv in this folder (`.venv/`,
git-ignored, made by `setup.sh` from `requirements.txt`) through `run.sh`,
which reads `tools/local.env` for the machine's settings:

| Setting in `tools/local.env` | What |
|---|---|
| `SOUNDS_PYTHON` | Python 3.11 or newer to make the venv with (default `python3`) |
| `HF_HOME` | Hugging Face cache for the model weights (1–5 GB per model; KugelAudio 15 GB) |
| `TMPDIR`, `PIP_CACHE_DIR`, `XDG_CACHE_HOME` | optional: where temp files and caches go |

`ffmpeg` must be on the PATH: it decodes every sound and measures its loudness for evening.

## A language's sounds

```
tools/sounds/run.sh texts de          # de/texts.tsv: every German text, once
tools/sounds/run.sh make de all       # a sound for each text without one
tools/sounds/run.sh collect de all    # (make ends with it) de/raw/sounds/ + sounds.tsv
```

- **Texts.** `texts de` reads every quiz in the app's built-in samples and
  the online ones (`DECKS`). `QUIZ_SIDES` in `sounds.py` gives the language
  of each side of each quiz by its name: a noun quiz is German then English,
  an articles quiz is German on both sides ("Hund" = "der"), a picture quiz
  is a picture then German. A quiz no rule matches stops the step, so a new
  quiz is never left out unnoticed. Each text is written once into
  `de/texts.tsv` (committed), with its key and the quizzes it is on.
- **Keys.** A text's file name is its key: the text made safe for a file name,
  plus 8 characters of its SHA-1 (`der_Hund-1a2b3c4d`). The key comes from
  the text alone, so it never changes, and a file system that ignores case
  still keeps "Essen" and "essen" apart. Sound files are `de-<key>.wav`.
- **Updating.** When quizzes change, run `texts de`, then `make de all`.
  Texts that already have a sound are skipped, so only new texts are made.
  Texts no longer on any quiz keep their sounds.
- **Another language** needs voices for it in `ENGINES` (Piper and Qwen3-TTS
  have many languages), a `LANGUAGES` entry (its voices in order, and
  Whisper's language code), its sides in `QUIZ_SIDES`, and then `texts <lang>`
  and `make <lang> all`.

### How `make` picks a voice

Every voice makes every text, and the sound Whisper is surest of wins
(eb: a voice that is the best for any text belongs in, and don't stop at a
good-enough sound):

1. Qwen3-TTS, Thorsten (Piper), OmniVoice and Chatterbox (`LANGUAGES`),
   one after another, each making and scoring every text. After each voice,
   `pick` and `collect` run, so after the first voice every text already has
   a sound in `de/raw/sounds/`, and each later voice can only improve it.
2. `retake` makes four more takes, with other seeds, with the MLX voices
   (Qwen3-TTS, OmniVoice, Chatterbox; Piper takes no seed) of each text whose
   pick is below the bar, and keeps each voice's best take.
3. `pick` takes each text's surest sound; `collect` copies it, evened, to
   `de/raw/sounds/` and writes `de/raw/sounds.tsv`: the file, the text, what
   was said, voice, seed, Whisper's chance and sureness, license, quizzes.

KugelAudio is not among them: its 7B model needs a Mac with more than 16 GB.

**The bar** is Whisper's sureness: its chance per piece of the text (the
geometric mean over the tokens it writes the text in), at least 90%
(`SURE`). The chance itself can't be the bar here. A long text spreads it
over more pieces: Whisper wrote "führt hinauf, führte hinauf, hat
hinaufgeführt" exactly, with a chance of 41%, but 95% a piece. Real errors
score low either way: "Feld" heard as "Failed" (0%), "Formel" as "Fahne"
(0%), "streckt" as "strickt" (0%), "Doktor" written "Dr." (15% a piece).

**Time.** Whisper takes 0.7 s per sound on an M1 Pro, however short the word:
its encoder always reads 30 s, and neither a shorter window (its scores
change completely) nor batching (no faster) helps. With making, a text takes
about 2 s with Qwen3-TTS, 1 s with Thorsten and 2.5 s each with OmniVoice and
Chatterbox, so about 2 days for the 21,364 German texts on the M1 Pro, plus
the retakes. `make` can be stopped and run again; it carries on.

A trial on 120 random German texts showed why every voice is worth trying.
Qwen3-TTS failed outright on some easy words ("die Stellung", "Doktor": 0%,
where another voice had over 90%). Thorsten beat it on 33 of the 60 texts it
did badly, OmniVoice beat both on 19 of 44, and Chatterbox was best for 2 of
36. The quizzes' texts are harder than the pilot's common words: rare words
("Sold", "Bückling"), bare nouns with no article, and verb-form lists.
Qwen3-TTS's average chance was 63% on them, against 85% in the pilot.

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
| `piper-thorsten`: Piper `de_DE-thorsten-high` | voice trained on Thorsten-Voice (CC0) | 0.2 s (CPU) | 46 | 74% | 14 | one male voice; Gasse heard as Kasse, Mädchen as Mietchen |
| `piper-kerstin`: Piper `de_DE-kerstin-low` | voice trained on Kerstin (CC0) | 0.05 s | 12 | 10% | — | 16 kHz "low" quality: not usable |
| `qwen3-design`: Qwen3-TTS 1.7B VoiceDesign, bf16 | Apache 2.0 | 0.9 s | 50 | 85% | 31 | voice described in words (`instruct`); missed Nacht and Büro; Gasse and aufstehen are retakes |
| `chatterbox`: Chatterbox Multilingual, fp16 | MIT | 1.7 s | 35 | 38% | 2 | default voice is English: an English accent comes through (Apfel as "Upflow") |
| `omnivoice`: OmniVoice, bf16 | Apache 2.0 | 1.8 s | 45 | 57% | 5 | several misses are near-silence; Gasse and aufstehen are retakes |
| `kugelaudio`: KugelAudio 7B, bf16, voice `clear` | MIT | 8.4 s (M5 Max) | 26 | 30% | — | dropped (see below); made on the M5 Max (17 GB of memory); needs the patch below; good at sentences, weak on single words: often a stray sound before the word ("jeder Apfel") or a verb heard as "den"; but the only clear Gasse (90%) |
| `kugelaudio-cfg8`: the same at CFG 8 | MIT | 8.7 s (M5 Max) | 23 | 23% | — | stronger text guidance; to the ear always worse or odd, music in some |

### Whisper's chance and pick

`hear` gives each sound a chance: the probability that Whisper, transcribing
it as German, writes exactly the word and then stops, summed over the
spellings it may use (capital or not, full stop or not). It is computed from
Whisper's own token probabilities, by feeding it the expected text. A word said
unclearly or wrongly, or followed by more sound, scores low. The chance compares
sounds of the same word, so `pick` takes, for each word, the source with the
highest chance. It leaves out `NOT_PICKED` in `sounds.py`: Wikimedia (bare
words, credits), Kerstin and KugelAudio. A pick below 50% needs an ear. Scoring takes about
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

### KugelAudio is dropped

eb listened to the pilot and agreed with Whisper: KugelAudio's stray sounds
before words are obvious, and CFG 8 changes the sound, very much on some
words, but always for the worse or oddly (music in some). KugelAudio says Gasse
best, but it is the pick for 1 word in 52. For the full list of about 4,000
words that would be about 9 hours on the M5 Max, plus the patch and torch,
for roughly 80 better words. So `pick` leaves it out (`NOT_PICKED`); its
sounds stay on the listening page. Without it, two pilot words have a best
pick under 80%: Gasse (OmniVoice, 43%) and aufstehen (Thorsten, 72%). More
takes of such words on the laptop are the cheaper fix (`retake`, below).

### More takes of weak words

An MLX model makes a different take of a word with each seed. `retake` finds
the words whose pick Whisper gives less than 80% (`RETAKE_BELOW`). For each,
it makes takes with seeds 1 to 5 with Qwen3-TTS and OmniVoice
(`RETAKE_ENGINES`; Piper takes no seed). It scores every take, evened like
the rest, and makes the best take that engine's sound for the word. The
engine's `log.tsv` then gives that take's seed, so it can be made again. All
takes stay in `raw/<engine>/takes/` (`<kind>-<lemma>~<seed>.wav`, with their
own `log.tsv` and `score.tsv`), and the page says which take a sound is. Then
it picks again.

In the pilot it took under a minute and fixed both words: Gasse went to
95% with Qwen3-TTS take 2 (its first take had 14%), and aufstehen to 85% with
OmniVoice take 2 (Qwen3-TTS take 3 had 85% too). A best take of five is
Whisper's choice, so listen to the retakes: one could suit Whisper better than
an ear.

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

## A list's or a language's folder

| File | What | Made by |
|---|---|---|
| `pilot.txt` | the words of the pilot (`noun Haus`, `verb gehen`) | by hand |
| `texts.tsv` | a language's texts: key, text, quizzes | `texts` |
| `raw/<source>/` | the sounds `<kind>-<lemma>.wav` (Wikimedia: `.ogg`), `log.tsv` (model, settings, seed, text, time; Wikimedia: file, license, speaker, page), `heard.tsv` and `score.tsv` | `generate`, `wikimedia`, `hear` (git-ignored) |
| `raw/picks.tsv` | per word, the source Whisper is surest of, and the runner-up | `pick` (git-ignored) |
| `raw/<engine>/takes/` | every take of a weak word, `<kind>-<lemma>~<seed>.wav`, with `log.tsv` and `score.tsv` | `retake` (git-ignored) |
| `raw/listen/` | every sound evened (see Evening), and `pilot.html` to compare them | `hear`, `pick`, `page` (git-ignored) |
| `raw/sounds/`, `raw/sounds.tsv` | the picked sound per text, and what it is | `collect` (git-ignored) |

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
   `raw/picks.tsv` and lists the picks below 50%. `run.sh retake <list>
   <words> [engines] [takes]` makes more takes of the words picked below 80%
   (see above), keeps the best and picks again.
4. `run.sh page <list> <words>` writes `raw/listen/pilot.html`. Open it
   through a local web server, e.g.
   `python3 -m http.server 8765 --directory tools/sounds/core/raw/listen`,
   then `http://127.0.0.1:8765/pilot.html`. ▶ plays a row. Each sound shows
   what Whisper heard and its chance, and Whisper's pick has a blue stripe.
   Mark the best per word; *Copy picks* gives yours beside Whisper's as TSV.

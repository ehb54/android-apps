# Quiz: design note

Quiz is a flash-card app ported from a terminal quiz program first written in
1980 (C, on a Kaypro) and last revised in 1993 (C++). The original's source is
not in this repository; this note records its rules, which the app keeps, and
what changed for a phone.

## The idea

Minimal UI, and recall that is checked rather than assumed: you type the
answer, one miss wipes out a card's progress, and a session cannot be left
until every missed card has been answered right on the first try.

## Rules kept from the original

**Cards and directions.** A card has side A and side B. A quiz runs A → B
(shown A, type B) or B → A, and each direction keeps its own statistics.

**Statistics per card and direction** (`model/Model.kt`, `Stats`):
- `asked`: times asked.
- `rightFirst`: consecutive right-first-try answers. Any miss resets it to 0.
- `wrong`: misses, capped at 3 and never decaying.
- **level** = `rightFirst - wrong`. Past misses raise the bar: a card missed
  three times needs three more straight rights than one never missed.

**Threshold.** A session asks only cards whose level is below the threshold
(default 1). Threshold 0 asks every card.

**A session** (`engine/Session.kt`):
1. The due cards in the chosen range are asked, shuffled unless shuffle is off.
2. A card stays on screen until answered right. Each wrong attempt is a miss.
3. When it is finally answered, the card's stats are updated: right on the
   first try raises `rightFirst`; anything else resets it and adds a miss.
4. Cards missed in a round are asked again in the next round, until a round
   has no misses. Then the session ends.

Because a card answered right on the first try leaves the session, its level
rises by at most one per session: reaching level 3 takes three separate
sessions, which spaces out the practice.

**Show answer** (typing `!` in the original) shows the answer, counts as a
miss, and hides it again before you type it.

**Timer.** With a timer set, an answer slower than the limit is a miss even
when right. Response times (shortest, average, longest) are reported at the end.
On the phone, a timed session can be paused (see Speed mode, Pausing).

**Confusion penalty.** At the end of a session, any card whose answer you typed
for a different card also gets a miss, since you confused the two.

**Range.** A session can be limited to cards *from*–*to*, by position in the list.

**Files.** `.qzx` is the original export format: plain text, one line for
side A and one for side B, card after card. A trailing side A without a side B
is dropped, as the original import did.

## What changed for the phone

- **Answers match forgivingly:** letter case, extra spaces and Unicode
  normalization form are ignored, ä/ö/ü/ß may be typed as ae/oe/ue/ss, curly
  apostrophes count as straight ones, and keyboard autocorrect is off. Other
  accents still matter (cafe is not café). The original required an exact
  match. Everything else still counts: an answer listing several meanings
  ("to become, get") must be typed whole, commas included. This is
  deliberate; do not change it to accept any one meaning.
- **No leaving a session.** There is no quit button, and Back only says how
  many cards are left. The session is saved after every answer and reopens on
  the next launch, even after Android has closed the app.
- **Cards per session.** Since a session cannot be left, starting a long quiz
  by accident must not mean 300 cards. Without a range, a session asks at most
  *cards per session* due cards (default 20, set per quiz, or "all"), taking
  the first ones in list order; learned cards drop out, so successive sessions
  work down the list. An explicit range still asks every due card in it.
- **Testing escape hatch.** In debug builds, long-pressing the round counter
  offers to leave the session (answered cards keep their results; no confusion
  penalty). Release builds never have it; `ESCAPE_HATCH` in `ui/AskingScreen.kt`.
- **A sample library, picked one by one.** The menu's Sample quizzes screen
  lists every sample on the download site (`samples/catalog.json` next to the
  files, under the Quiz page), whether it is added here, and buttons to add,
  update or remove each one. A few small samples are built into the app and
  still work offline; the rest (the long word lists) are downloaded when added,
  checked against the catalog's fingerprint, and kept in the app's cache.
  Offline, the screen shows the catalog as last fetched. Each added sample quiz
  remembers its source (the file, or `file#tab` for a workbook tab) and a
  fingerprint of its contents, so it is never added twice, and when the site
  has a different version it shows "update available": Update replaces the
  cards and keeps the quiz's name, settings and progress on unchanged cards.
  Removing a sample deletes the quiz (after asking); it can be added again.
  This is the app's only network use (the INTERNET permission): it fetches
  public files and sends nothing about the user (for Play's Data safety form).
- **Pictures and sounds instead of commands.** A side starting with `!` ran a
  program in the original (for example a molecule viewer). Here a side can be
  a picture or a sound from the phone (see Sounds below). Neither can be
  typed, so typing asks a card only in the direction whose answer is text.
  Pictures are scaled to at most 1600 pixels, turned upright and stored in the
  app.
- **Zip files carry pictures and sounds:** a `.zip` holds one `.qzx` file plus
  the files, and a side written `!name.png` is the picture of that name,
  `!name.mp3` the sound (by the extension). Export writes `.qzx` when a quiz
  has neither and `.zip` when it has either.
- **Excel workbooks** (`.xlsx`, for example a Google Sheet downloaded on the
  phone): each tab becomes its own quiz, named after the tab, with side A from
  the first column and side B from the second. Rows missing either side are
  left out, and a tab with no complete rows gives no quiz (`engine/Spreadsheet.kt`).
- **One user.** The original kept statistics per user name; the phone has one.
- **Editing.** Cards are added, edited (statistics kept) and deleted on the
  phone; quizzes are created, renamed, reset and deleted.

## Match mode

A way to practise a quiz, chosen with the **Match** chip in the mode row (Type,
Pick, Match); it has no direction (`engine/Match.kt`, `ui/MatchScreen.kt`).
Decisions made with eb:

- **Blocks.** The session's cards are shown a block at a time, *Pairs per block*
  (default 6, 3 to 10) as two columns of tiles: side A on the left, side B on
  the right, each column shuffled. Tap one tile on each side to pair them. When
  a block is cleared, the next one appears: 24 cards at 6 a block is 4 blocks.
- **Same rules as typing.** A card paired without ever being in a wrong pair
  is right the first time. A wrong pair is a miss for **both** of its cards,
  which also covers the original's confusion penalty. Missed cards come back in
  the next round, a block at a time, until a round has no misses; the
  threshold, range and cards-per-session settings work as in typed sessions.
- **Its own level.** Each card keeps a separate match record (`statsMatch`):
  recognizing a pair is easier than typing an answer, so matching never makes a
  card learned for A → B or B → A. The card list shows the level of the
  selected mode, and Reset statistics clears all three.
- **Fillers.** A block short of cards (the end of a round, or a small retry
  round) is topped up with other cards of the quiz that are not still to be
  asked, so the last pair is never a giveaway. Fillers are not scored, but a
  wrong pair with one still counts as a miss for the scored card.
- **Same content, either way.** Two cards showing the same thing (two side Bs
  reading "time") may be paired either way round.
- **Pictures and sounds** are tiles like text, on either side, since nothing
  is typed. A sound tile has a button that plays it; a tap elsewhere on the
  tile picks it, so listening never makes a pair.
- **No leaving**, as in typed sessions: Back only says how many cards are left,
  and the session (block, pairs made, misses) is saved after every pair and
  resumes on the next launch. The debug-only long press on the round counter
  leaves a match session too; an unfinished block is not scored.

## Pick mode

The **Pick** chip: one card at a time, as in typed sessions, but the answer is
picked from a few choices instead of typed (`engine/Session.kt`, which runs
both, and `ui/PickScreen.kt`). Decisions made with eb:

- **Choices.** *Choices* per card (default 4, 2 to 8): the card's own answer and
  other cards' answers from the whole quiz, in random order, never two showing
  the same thing, so exactly one is right. A small quiz offers fewer.
- **Direction** as in typing: A → B shows side A and offers side Bs.
- **Stay until right.** A wrong choice is marked and cannot be picked again;
  the card stays until the right one is picked, counts as missed, and comes back
  next round. The timer, threshold, range and cards-per-session settings work as
  in typed sessions.
- **Confusion penalty.** A wrong choice is another card's answer, so that card
  gets a miss at the end of the session, as when its answer is typed.
- **Its own levels**, one per direction (`statsPickAToB`, `statsPickBToA`):
  picking from a few choices is easier than typing, so it never makes a card
  learned for typing or for Match.
- **Pictures and sounds** can be prompts and choices; a card whose answer is a
  picture or a sound is asked in Pick mode but not in typed mode. A sound
  choice plays from its button and is picked by a tap elsewhere on it.
- **Tiles for many pictures** (eb, 2026-11-05): more than four choices that
  are all pictures or sounds sit two to a row, at the same picture size, so
  eight take four rows. Text choices keep the whole width, since long answers
  would wrap at half of it.

## Speed mode

The **Speed** chip (eb, 2026-11-05: "a pick mode that goes on forever until
quit ... the goal here is speed of recognition"): picking as in Pick mode, a
level at a time, until you stop (`engine/Speed.kt`, `ui/SpeedScreen.kt`).
Decisions made with eb:

- **Levels go up by themselves.** A run takes a fixed set of cards: the range,
  or else the first *cards per session* cards. Level N asks those whose speed
  level is below N, as Pick would with threshold N, with the same rules (a
  wrong choice is ruled out, missed cards come back in rounds, confusion
  penalty at the level's end). When a level is done, level N + 1 follows,
  asking every card again. There is no threshold setting in this mode.
- **Its own level per card**, one per direction (`statsSpeedAToB`,
  `statsSpeedBToA`), since its levels rise many times in one sitting, plus
  each card's latest time to a right first pick (`speedMillisAToB/BToA`). The
  card list shows both ("level 3 · 1.2 s"), and the quiz page names the three
  slowest cards. Pick's own levels are untouched.
- **A run continues where the last one got to** (eb, 2026-11-05: "one always
  wants to get to higher levels"). The quiz keeps the level being played, per
  direction (`Quiz.speedAToB/BToA`, a `SpeedPlace`), from a level's first
  right pick and at each level's end, so there is no Save: *Stop* keeps it.
  The quiz page then offers *Continue level N*, which plays level N from its
  start, asking every card below N: a missed card drops below level 0, and
  without this a new run would start at level 1 with only the missed cards,
  one *Go on* per level until they caught up. Without a place yet (or when no
  card is below it), a run starts one level above the lowest card. The place
  also keeps that level's previous average and median, for the comparison
  and the tightened time limit. *Reset statistics* forgets it.
- **Between levels, a results screen**, so reading it is not timed: right first
  time, misses, average and median time (with the level before's), fastest and
  slowest, the three slowest cards, and cards marked missed by confusion. Times
  are of right first picks in the level's first round. Then *Go on to level N*
  or *Stop*.
- **An exit**, unlike the other modes: *Stop* in the top bar shows the level
  so far, and *Done* ends the run. Back asks first. Picks are saved as they
  happen; a level left unfinished gets no confusion penalty.
- **Speed is measured; pressure is optional.** The *Timer* setting limits
  every level (too slow is a miss). *Tighten the time limit* makes each level's
  limit 1.5 times the level before's median, rounded up to a tenth of a second,
  never above the timer.
- No due counts: the quiz list line leaves Speed out, and its chips show no
  numbers.
- **Pausing** (eb, 2026-11-05: "does the timing stop if another app (e.g. a
  call) gets focus? is there some way to pause?"). The clock is a `Stopwatch`
  (`engine/Stopwatch.kt`) that counts only while running. In any timed session
  (Speed, or Pick and Type with the timer on) the top bar has a pause button,
  and the app pauses by itself when it leaves the screen (`ui/Away.kt`: a call
  answered, Home, the app switcher, the screen turning off; on the desktop,
  the window losing focus). While paused, the card is hidden behind *Resume*
  (`ui/Paused.kt`), so a pause is no time to study it; time before the pause
  still counts, time paused does not, and a sound prompt plays again on
  Resume, its clock starting after it. A timed session opened again after the
  app was closed waits for Resume too. The clock also stops while the Stop
  question is up. A call that only shows as a banner does not take the app off
  the screen, so it does not pause it; the button is for that.

## Sounds

Added 2026-11-04 (eb: "keep separate for now. add support for sound"; see the
study below). A side is text or a file, and the file is a picture or a sound by
its extension (`model/Media.kt`). The engine's rules were already about files,
not pictures: a file can't be typed, and two tiles or choices showing the same
file belong together. So sound needed no new rules, only storage, playing and
screens. In stored quizzes the fields are still named `imageA`/`imageB`.

- **Formats:** MP3, AAC (`.m4a` or raw ADTS), Ogg Vorbis, WAV and FLAC, all of
  which Android 8 and 9 play. A sound is stored as it is, under the extension
  its first bytes show (`data/Sounds.kt`), so a file that isn't sound is
  refused when it is added, not when it fails to play.
- **Where sounds come from:** zips (`!name.mp3`), and in the card editor
  *Use a sound*, which picks an audio file. No recording: that would need the
  microphone permission (a Data safety answer on Play).
- **Playing** (`ui/SoundPlayer.kt`, the only file that uses Android's media
  classes): one sound at a time, ducking other
  apps' audio briefly. A prompt that is a sound plays by itself when the card
  shows, and its large button stops it or plays it again. The answer timer
  starts when that first playing ends, so a long sound doesn't count against
  you. Sounds stop when the app goes to the background, and the volume keys set
  media volume.
- **On the desktop** (its own `ui/SoundPlayer.kt`) sounds play through Java
  Sound, which comes with the Java runtime but reads only uncompressed sound:
  WAV plays (the Morse sample included); MP3, AAC, Ogg Vorbis and FLAC are
  stored and exported as on the phone, but report that only WAV plays. Playing
  them there would take a decoder library per format, or JavaFX.
- **A card can be two files** (a sound and a picture): typing can't ask it,
  Pick and Match can. The editor no longer limits a card to one picture.
- **Sample:** *Morse code*, 26 letters as tones made by `tools/samples/morse.py`
  (no outside source to credit; 13 KB). Hear the letter and type it; see the
  letter and pick its sound; pair sounds with letters.

## Due counts

The quiz list shows what is due in each mode, Type and Pick both ways (→ is
A → B, ← is B → A): "14 cards · Type →14 ←3 · Pick →2 ←✓1 · Match ✓2". On the
quiz page the mode chips carry the counts for the current direction, and the
direction chips carry the selected mode's count for each direction, so every
count is one glance away without extra rows (chosen with eb).

- **A check carries a level.** When nothing is due, "✓2" says every card that
  mode and direction can ask is at level 2 or more (`Quiz.progress`, the lowest
  level among them). So a quiz cleared at threshold 1 reads differently from one
  cleared at 2, though both are done at their threshold.
- **Nothing to ask, nothing shown.** A direction no card can be asked in is left
  out of the list line (pictures on side A: "Type →6", with no ←), and its chip
  shows just its label. Picking and matching can ask any card with both sides.
- **Cards per level.** Between the direction chips and the threshold, one line
  counts the cards at each level for the selected mode and direction:
  "Level 2: 10 · 3: 4" (`Quiz.levels`). Once everything is at level 2, it shows
  what raising the threshold to 3 would ask (10 cards). Levels can be negative,
  since a card's misses are subtracted (see the level rule above).
- Counts and levels follow the range; due counts also follow the threshold. The
  cards-per-session cap only limits what the Start button takes.

## On the desktop

eb asked for Quiz on macOS (14 and newer, Intel and Apple silicon), with Windows
and Linux too, keeping the Android app as it was: the desktop app runs alongside
it rather than replacing it.

- Same rules, screens and files: the desktop app is built from the Android app's
  code. A quiz exported on one opens on the other; progress is not synced.
- Quizzes live in the system's application data folder: macOS
  `~/Library/Application Support/Quiz`, Windows `%APPDATA%\Quiz`, Linux
  `~/.local/share/quiz`.
- Escape is Back, with the same rule: a running session cannot be left, and
  closing the window keeps it for next time, as on the phone.
- Wording that speaks of tapping has desktop text in `desktop/quiz/src/main/res`.
- Only WAV sounds play on the desktop (see Sounds).
- A timed session pauses when the window loses focus, as the phone's does when
  the app leaves the screen (see Speed mode, Pausing).

## Several fields per card: studied, not chosen

eb asked (2026-11-04) why a card can't hold text A, text B, a picture and a
sound together, and be quizzed in any pairing (sound to picture, picture to
text, text to text, and so on), even when some cards lack some fields. Three
designs were compared on user experience, dataset upkeep and code:

- **Separate (chosen for now):** quizzes stay two-sided. A word list with
  pictures and sounds becomes several quizzes (picture/word, sound/word,
  word/meaning).
- **Pairings:** one quiz of multi-field records; the quiz page picks a prompt
  field and an answer field.
- **Views:** a deck of multi-field records, where each pair of fields shows up
  as its own quiz and behaves like today's two-sided quiz (Anki's notes and
  card templates work this way).

What we found:

- **Holes are easy in any design:** a pairing asks only the cards that have
  both fields, and typing also needs a text answer. That is the rule already
  used for pictures.
- **Pairings was dropped.** Four choices before a session starts, up to 24
  level records per card (5 today), due counts that can't all be shown, and
  every engine keyed by field pair instead of direction.
- **Separate or Views turns on dataset upkeep.** Generated lists are fixed
  once in the generator and rebuilt, so duplicate quizzes cost little.
  Hand-built picture and sound datasets drift across sibling quizzes, which
  Views avoids by keeping one record per item.
- **Views can come later without loss:** a two-sided quiz is a two-field deck
  with one view. Keeping levels in a map keyed by mode and direction, instead
  of five named fields, would make that step (and any new mode) cheaper.

## Code map

Quiz is three modules. The rules, file formats and storage are plain Kotlin in
`shared/quiz/` (package paths below, under `src/main/kotlin/io/github/ehb54/quiz/`),
used by the Android app (`apps/quiz/`) and the desktop app (`desktop/quiz/`). The
screens and view model are written once, in `apps/quiz/`; the desktop app compiles
them unchanged (see CLAUDE.md, Desktop apps).

| Path | What |
|---|---|
| `shared/quiz`: `model/Model.kt` | `Card`, `Stats`, `Quiz`, `QuizSettings`: data and the level/due rules |
| `shared/quiz`: `engine/Session.kt` | Session state machine: start, answer, show answer, rounds, confusion penalty; also pick mode |
| `shared/quiz`: `engine/Match.kt` | Match sessions: blocks of tile pairs, rounds, scoring, fillers |
| `shared/quiz`: `engine/Speed.kt` | Speed runs: levels, their results, the tightening time limit, continuing |
| `shared/quiz`: `engine/Stopwatch.kt` | The answer clock, which stops while paused |
| `shared/quiz`: `engine/Answers.kt` | Forgiving answer comparison |
| `shared/quiz`: `engine/QuizFiles.kt` | `.qzx` and `.zip` reading and writing |
| `shared/quiz`: `engine/Spreadsheet.kt` | `.xlsx` reading |
| `shared/quiz`: `engine/Samples.kt` | The sample catalog: its format, and building it from the sample files |
| `shared/quiz`: `data/QuizStore.kt` | JSON files in app storage, written atomically |
| `shared/quiz`: `model/Media.kt` | Whether a side's file is a picture or a sound |
| `shared/quiz`: `data/Sounds.kt` | Recognizing and storing sounds |
| `shared/quiz`: `data/SampleLibrary.kt` | Fetching the catalog and sample files from the download site, with an offline cache |
| `apps/quiz`: `data/Images.kt` | Storing and loading pictures (Android's BitmapFactory; `desktop/quiz` has its own, with Skia) |
| `apps/quiz`: `ui/SoundPlayer.kt` | Playing sounds (Android's media classes live here only; `desktop/quiz` has its own, with Java Sound) |
| `apps/quiz`: `ui/Paused.kt`, `ui/Away.kt` | Pausing a timed session; noticing the app has left the screen (`desktop/quiz` has its own `ui/Away.kt`: the window losing focus) |
| `apps/quiz`: `ui/QuizViewModel.kt` | Screen state; every change is saved on one disk thread, in order |
| `apps/quiz`: `ui/*Screen.kt`, `ui/CardEditor.kt` | Compose screens (`ui/SpeedScreen.kt`: Speed mode's results between levels) |
| `desktop/quiz`: `Main.kt` | The desktop window; Escape is Back |
| `apps/quiz/src/main/assets/samples/` | Samples built into the app (small; they work offline) |
| `apps/quiz/samples/` | Online-only samples and `catalog.json`, which lists both kinds; published to the download site |

Tests follow the code: the rules' tests in `shared/quiz/src/test/`, the Android
UI tests (Robolectric) and `SampleCatalogTest` in `apps/quiz/src/test/`, and a
desktop UI test plus the picture and sound tests in `desktop/quiz/src/test/`.

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
- **Pictures instead of commands.** A side starting with `!` ran a program in
  the original (for example a molecule viewer). Here a side can be a picture
  from the phone. A picture can only be shown, never typed, so a card with a
  picture is asked only in the direction where the picture is the prompt.
  Pictures are scaled to at most 1600 pixels, turned upright and stored in the
  app.
- **Zip files carry pictures:** a `.zip` holds one `.qzx` file plus the image
  files, and a side written `!name.png` is the picture of that name. Export
  writes `.qzx` when a quiz has no pictures and `.zip` when it does.
- **Excel workbooks** (`.xlsx`, for example a Google Sheet downloaded on the
  phone): each tab becomes its own quiz, named after the tab, with side A from
  the first column and side B from the second. Rows missing either side are
  left out, and a tab with no complete rows gives no quiz (`engine/Spreadsheet.kt`).
- **One user.** The original kept statistics per user name; the phone has one.
- **Editing.** Cards are added, edited (statistics kept) and deleted on the
  phone; quizzes are created, renamed, reset and deleted.

## Match mode

A second way to practise a quiz, chosen with the **Match** chip next to A → B
and B → A (`engine/Match.kt`, `ui/MatchScreen.kt`). Decisions made with eb:

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
- **Pictures** are tiles like text, on either side, since nothing is typed.
- **No leaving**, as in typed sessions: Back only says how many cards are left,
  and the session (block, pairs made, misses) is saved after every pair and
  resumes on the next launch. The debug-only long press on the round counter
  leaves a match session too; an unfinished block is not scored.

## Code map

| Path | What |
|---|---|
| `model/Model.kt` | `Card`, `Stats`, `Quiz`, `QuizSettings`: data and the level/due rules |
| `engine/Session.kt` | Session state machine: start, answer, show answer, rounds, confusion penalty |
| `engine/Match.kt` | Match sessions: blocks of tile pairs, rounds, scoring, fillers |
| `engine/Answers.kt` | Forgiving answer comparison |
| `engine/QuizFiles.kt` | `.qzx` and `.zip` reading and writing |
| `engine/Samples.kt` | The sample catalog: its format, and building it from the sample files |
| `data/QuizStore.kt` | JSON files in app storage, written atomically |
| `data/Images.kt` | Storing and loading pictures |
| `data/SampleLibrary.kt` | Fetching the catalog and sample files from the download site, with an offline cache |
| `ui/QuizViewModel.kt` | Screen state; every change is saved on one disk thread, in order |
| `ui/*Screen.kt`, `ui/CardEditor.kt` | Compose screens |
| `src/main/assets/samples/` | Samples built into the app (small; they work offline) |
| `samples/` | Online-only samples and `catalog.json`, which lists both kinds; published to the download site |

The engine and file code is plain Kotlin with JVM unit tests. `SessionRulesUiTest`,
`ResumeUiTest`, `MatchUiTest`, `MatchResumeUiTest` and the `Sample*UiTest`s drive
the real screens on Robolectric; the UI tests read the sample library from
`samples/` instead of the site.

`samples/catalog.json` is generated by the app's own code, so its fingerprints
are the ones the app records. `SampleCatalogTest` fails when it no longer matches
the sample files; after adding or changing a sample, regenerate it with
`tools/ci-build.sh :apps:quiz:testDebugUnitTest --tests '*SampleCatalogTest*' -PwriteCatalog=true`
(or the same arguments to `tools/docker-build.sh` / `tools/remote-build.sh`; the
remote build does not copy the file back, so fetch it with scp). The app sees a
change only after the next push to `main` deploys the site.

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
- **Samples are added once.** Each sample quiz remembers which sample it came
  from and a fingerprint of its contents. Adding samples again skips those
  already here and offers to replace any the app has a newer version of,
  keeping the quiz's name, settings and progress on unchanged cards.
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

## Code map

| Path | What |
|---|---|
| `model/Model.kt` | `Card`, `Stats`, `Quiz`, `QuizSettings`: data and the level/due rules |
| `engine/Session.kt` | Session state machine: start, answer, show answer, rounds, confusion penalty |
| `engine/Answers.kt` | Forgiving answer comparison |
| `engine/QuizFiles.kt` | `.qzx` and `.zip` reading and writing |
| `data/QuizStore.kt` | JSON files in app storage, written atomically |
| `data/Images.kt` | Storing and loading pictures |
| `ui/QuizViewModel.kt` | Screen state; every change is saved on one disk thread, in order |
| `ui/*Screen.kt`, `ui/CardEditor.kt` | Compose screens |
| `src/main/assets/samples/` | Sample quizzes (menu: Add sample quizzes; also on the download page) |

The engine and file code is plain Kotlin with JVM unit tests. `SessionRulesUiTest`
and `ResumeUiTest` drive the real screens on Robolectric.

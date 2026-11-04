# Reviewing a word list

The guide for the hand review of `words.py` drafts, whether a person or an AI
agent does it. Each reviewed word becomes quiz cards in both directions
(German → English and English → German) for people learning German, so a
card must be correct and its answer must be the only right one.

The tagger and Wikidata both make mistakes. Wikidata's English is mostly the
wrong sense, and the verb forms in a draft are only what the tagger saw (often
wrong). Treat both as hints and decide from your own knowledge of German.

## Input and output

A review batch (`words.py pending`) is a TSV of draft rows: rank, lemma,
counts, `books` (how many books use it), keyness for domain lists, Wikidata's
hints, emoji candidates (nouns) or forms seen (verbs), and an example sentence
from the books (in modernized spelling).

Write one output row per input row, same lemmas, same order, as a TSV with
exactly the header shown below (tabs between columns, UTF-8). Check it with
`python3 words.py check nouns|verbs <your file> <batch file>`; `words.py merge`
runs the same checks and refuses the whole file if one row is wrong.

- `lemma`: copied unchanged. It is the key into the counts; never correct it
  here. If it is wrong, drop the row and say what it should be.
- `decision`: `keep` or `drop`.
- `note`: for a drop, the reason (required). For a keep, empty or a short remark.

## Nouns

Header: `lemma	decision	article	plural	english	emoji	note`

- `article`: `der`, `die` or `das`, for the sense the books use (der See is a
  lake, die See the sea: follow the example sentence).
- `plural`: without its article, in today's spelling (`Häuser`). Empty when the
  noun is not normally used in the plural (Wasser, Mut, Gold).
- `english`: one short gloss for the sense the books use, 1–3 words, lowercase
  unless English capitalizes it (God, Christmas), no article, no commas, no
  brackets. It must be the only noun in the list with that gloss, or the
  English → German card has two right answers. Prefer a distinct accurate word
  (Pferd horse, Ross steed, Gaul nag; Weg way, Pfad path, Gasse alley). When
  there is none, keep the more common noun and drop the other with
  `duplicates <noun>`.
- `emoji`: one emoji only when it plainly shows the noun (🏠 Haus, 🍎 Apfel),
  not an association (no 💡 for Idee, no ❤️ for Liebe). Each emoji at most once
  in a list. Usually empty.
- Keep: ordinary nouns of standard German, including old-fashioned ones still
  in a modern dictionary, compounds, nationalities (Franzose), and nouns made
  from adjectives or participles that dictionaries list as nouns (der Beamte,
  plural Beamten; der Verwandte, plural Verwandten). Lexicalized -chen/-lein
  words are nouns in their own right: Mädchen, Märchen, Fräulein, Kaninchen,
  Brötchen, Eichhörnchen.
- Drop, with the reason: names of people, places or works (`name`); dialect
  or Low German (`dialect`); diminutives of another noun (`diminutive`);
  abbreviations (`abbreviation`); tagger artifacts such as verbs, adjectives or
  words cut short (`tagger artifact`); inflected or old-spelling forms
  (`wrong lemma (Tür)`); words that only occur in one tale or book, or only in
  one idiom (`book-specific`, `only in 'mein Lebtag'`); obsolete words no
  learner needs (`obsolete coin`); Gutenberg markup (Footnote, Fußnote);
  words that are slurs or offensive today (Neger, Zigeuner, Mohr, Krüppel:
  `offensive today`).

## Verbs

Header: `lemma	decision	infinitive	english	forms	note`

- `infinitive`: what the card shows: the lemma, with `sich ` in front when the
  verb is reflexive in the sense the books use (`sich freuen`, `sich setzen`).
  It must not repeat another verb's infinitive.
- `english`: one short gloss without "to" (`begin`, `be glad`), 1–3 words,
  lowercase, no commas or brackets, the only verb in the list with that gloss.
  Prefer a distinct word (gehen go, laufen run, wandern hike, schreiten stride);
  when there is none, keep the more common verb and drop the other with
  `duplicates <verb>`.
- `forms`: the principal parts as a learner says them: third person singular
  present, third person singular past, and the perfect with its auxiliary,
  separated by `, ` and in today's spelling:
  - `geht, ging, ist gegangen`
  - `fängt an, fing an, hat angefangen` (separable: particle at the end)
  - `freut sich, freute sich, hat sich gefreut` (reflexive)
  - `kann, konnte, hat gekonnt` (modal); `lässt, ließ, hat gelassen`
  - Where both auxiliaries occur, use the one for the sense the books use
    (`fährt, fuhr, ist gefahren`).
- Keep: verbs of standard German, including old-fashioned ones still in a
  modern dictionary, separable and inseparable compounds, modal verbs, and
  sein, haben, werden.
- Drop, with the reason: inflected forms the tagger took for an infinitive
  (`aßen`, `gegessen`, `sollten`: `wrong lemma (essen)`); old spellings
  (`thun`: `wrong lemma (tun)`); dialect (`dialect`); participles used only as
  adjectives (`adjective`); archaic verbs no modern dictionary has (`archaic`);
  non-verbs (`tagger artifact`).

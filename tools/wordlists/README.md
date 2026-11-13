# Word lists from public-domain texts

Builds noun and verb frequency lists from public-domain texts, a general one
and one per domain (history, fairy tales, Goethe, children's books), and turns
them into Quiz sample files: a nouns `.xlsx` with tabs for nouns (German →
English), articles (noun → der/die/das) and plurals, a `.zip` of picture cards
where an emoji fits, a verbs `.xlsx` with tabs for verbs (German → English)
and verb forms (infinitive → present, past, perfect), and for a list with its
own pictures (`../pictures/`) two more `.zip` files of picture cards, nouns
and verbs.

Everything needed to grow a list is kept: the full counts, every review
decision (including drops and why), each list's rules and its book-selection
filters. `REVIEW.md` is the review guide.

`words.py` does the work. Its spaCy steps run in Docker (`run.sh`, image from
`Dockerfile`) on a Docker host, and downloads are cached in `cache/`
(git-ignored); `choose_books.py` and the review steps (`pending`, `merge`) need
only Python.

## A list's folder

| File | What | Made by |
|---|---|---|
| `books.tsv` | Gutenberg numbers of the books counted | `choose_books.py` |
| `counts.tsv`, `verb-counts.tsv`, `stats.json` | every noun and verb with count, range, books, examples; verbs also with the forms the tagger saw | `count` |
| `draft.tsv`, `verb-draft.tsv` | top nouns and verbs in rank order, with Wikidata's hints and emoji candidates (nouns) | `enrich`, `enrich-verbs` |
| `review.tsv`, `verb-review.tsv` | **every review decision**: `keep` with what the cards need, or `drop` with the reason | by hand (`REVIEW.md`) |
| `list.json` | the list's rules: ranking, size, minimum books, lists and lemma groups to exclude, fields that must be unique; verb rules under `verbs` | by hand |
| `nouns.tsv`, `verbs.tsv` | the nouns and verbs that go into the quizzes | `select`, `select-verbs` |
| `spelling.tsv` | optional extra lemma fixes for this list only | by hand |

The review files and `list.json` are the source of truth; `nouns.tsv` and
`verbs.tsv` are derived.

## Steps

1. `python3 tools/wordlists/choose_books.py <list>` picks the books from
   Gutenberg's catalog with that list's filters (in the script), at most 3 per
   author. Add filters there to start a new list.
2. `tools/wordlists/run.sh count <list>`
   Downloads the books, modernizes old spelling with the shared
   `oldspelling.tsv` *before* tagging (Thür → Tür, Theil → Teil, Geheimniß →
   Geheimnis, daß → dass, Cultur → Kultur; rules are case-sensitive and `*`
   marks "more letters here"), tags and parses every word with spaCy's German
   model (`de_core_news_md`), fixes lemmas the tagger gets wrong with the shared
   `lemmas.tsv` (old datives such as Volke, dictionary entries in old spelling
   such as Beschluß, verbs that lost an umlaut such as mussen) and the list's
   optional `spelling.tsv`, and counts nouns and verbs. The parser joins
   separable verbs to their particle ("fing ... an" counts as anfangen) and
   notes, as hints for review, the present, past and participle forms and the
   perfect auxiliary each verb shows. A word must occur in at least 3 chunks of
   1000 words and 3 books.
3. `tools/wordlists/run.sh enrich <list> <how many> [reference]` and
   `enrich-verbs` (same arguments)
   Take the top nouns (verbs) and add article, plural and English (verbs:
   English) from Wikidata lexemes (CC0), and for nouns emoji candidates whose
   German name (Unicode CLDR) is the noun. With a reference list (for domains:
   `core`), words are ranked by **keyness** instead of count: log-likelihood
   (Dunning G²) of how much more the domain uses the word than the reference
   does, so a history list shows Regierung and Flotte rather than Mann and Frau
   again.
4. **Review by hand**, following `REVIEW.md`. The tagger and Wikidata both make
   mistakes, and Wikidata's English is mostly the wrong sense, so this step is
   not optional. `python3 words.py pending <list> nouns|verbs <dir> [batch]`
   writes the entries the list still needs decided as batch files (draft
   rows); review each into a file with the review columns, then
   `python3 words.py merge <list> nouns|verbs <files>` checks every row and adds
   them to `review.tsv` / `verb-review.tsv`. Batches can be reviewed in
   parallel (by people or AI agents); `work/` (git-ignored) is the place for them.
5. `tools/wordlists/run.sh select <list>` (`select-verbs` for verbs) writes
   `nouns.tsv` (`verbs.tsv`): the first `size` entries in draft order that the
   review keeps and the rules allow. If it meets entries nobody has decided
   yet, it lists them instead: run `pending` for the next batches. It also
   refuses repeated values in the `unique` fields (no two nouns or verbs with
   the same English gloss, or English → German cards become ambiguous; no
   picture or infinitive twice).
6. `tools/wordlists/run.sh build <list> <Title> ../../apps/quiz/samples`
   Writes `<Title> nouns.xlsx`, `<Title> pictures.zip` if any noun has a
   picture (the nouns' Noto Emoji, resized, with their Apache 2.0 license
   inside the zip), and `<Title> verbs.xlsx`. A list with its own pictures
   (`../pictures/<list>/picks.tsv`, see `../pictures/README.md`) also gets
   `<Title> pictures <model>.zip` and `<Title> verb pictures <model>.zip`
   (`Core pictures ERNIE.zip`). Each zip has a credits file, and the same
   pictures always give the same zip. These are
   online-only samples: the app downloads them from the site when added. The
   picture zips come out without sounds: on the machine with the sounds, run
   `tools/sounds/run.sh samples de ../../apps/quiz/samples` (see
   `../sounds/README.md`) to put them back and remake the listening and
   dictation quizzes. Then regenerate `apps/quiz/samples/catalog.json` (see
   `apps/quiz/DESIGN.md`), or `SampleCatalogTest` fails.

### Growing a list

Raise `size` in `list.json`, run `enrich` with enough nouns to cover it (rank
order and earlier decisions do not change), run `select` to get the undecided
batch, review that batch into `review.tsv`, and repeat until `select` writes
`nouns.tsv`. Verbs work the same way with the `-verbs` steps. Then `build`.
Domain lists exclude the core list's nouns and verbs, so they change when the
core list grows: finish the core review before a domain's. Coverage of the noun uses in the core corpus:
top 200: 37%, 1000: 59%, 2000: 68%, 3000: 73%, 5000: 78%. Past about 2000 the
nouns occur in fewer books; a `min_books` rule keeps out single-novel words.

From a laptop, run the Docker steps on a remote Docker host the same way
`tools/remote-build.sh` does: rsync the checkout there, run `run.sh`, and copy
the list folder back. Counting all five lists (about 670 books, 43 million
words) took 45 minutes on a 64-core host, downloads included; `enrich` takes
a few minutes per thousand words (Wikidata is queried politely).

## Lists

| List | Books | Rules (`list.json`) |
|---|---|---|
| `core/` | 365 German-literature novels and stories by 242 authors born 1840–1900 (written about 1870–1930); no plays, poetry, children's books, history or non-fiction shelves. 22 million words. | By count: the general reference list. 3000 nouns, 1000 verbs. |
| `history/` | 184 books from Gutenberg's history shelves by 157 authors born from 1750; no novels, plays, poetry, travel, art, science or nature shelves, no translations (Macaulay, Gibbon, ...), no Saxon nature-conservation magazine. 14 million words. | By keyness against `core`, excluding its words. 1000 nouns in at least 15 of the 184 books and 300 verbs in at least 25 (keeps out naval and other single-topic terms; 25 was too few nouns for 1000); no months. |
| `fairytales/` | 45 books of fairy tales, folk tales and legends in prose by 34 authors: Grimm (#77905, the only German Grimm text), Hauff, Bechstein, Andersen, Musäus, Brentano, Storm, E.T.A. Hoffmann, Asbjørnsen, Rübezahl, and Estonian, Japanese, Turkish, Icelandic and Arabian tales in translation. 2.5 million words. Grew out of the Grimm-only pilot (its review decisions are kept). | By keyness against `core`, excluding its words. 1000 nouns, 300 verbs, each in at least 4 books. |
| `goethe/` | Goethe's prose in 21 Gutenberg volumes: Werther, Wilhelm Meisters Lehrjahre and Wanderjahre, Die Wahlverwandtschaften, Novelle, Unterhaltungen deutscher Ausgewanderten, Italienische Reise, Briefe aus der Schweiz, Kampagne in Frankreich, Belagerung von Mainz. No plays, poems or letters. 0.9 million words. | By keyness against `core`, excluding its words. 976 nouns (every reviewed keep: the corpus has no more nouns in at least 3 volumes) and 300 verbs. |
| `children/` | 58 children's books by 42 authors (fiction; no fairy tales, non-fiction or verse): Spyri, Storm, Sapper, Siebe, Olfers, Bonsels, Lessing's fables, and Alice, Tom Sawyer and Nils Holgersson in translation. 2.7 million words. | By keyness against `core`, excluding its words. 1000 nouns, 300 verbs, each in at least 4 books. |

## Licenses

- Source texts: public domain. Only counts and our own reviewed data are kept.
- Wikidata: CC0. Unicode CLDR emoji names: Unicode License.
- Noto Emoji pictures: Apache License 2.0; the license and a credits note
  travel inside each picture zip.
- The lists' own pictures (`../pictures/`): generated with an Apache 2.0
  model, named in each zip's credits file.
- English glosses: written during review (Wikidata's rarely fit the domain).

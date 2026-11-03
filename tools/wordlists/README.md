# Word lists from public-domain texts

Builds noun frequency lists from public-domain texts, a general one and one
per domain (fairy tales, history, ...), and turns them into Quiz sample files:
an `.xlsx` with tabs for nouns (German → English), articles (noun →
der/die/das) and plurals, plus a `.zip` of picture cards where an emoji fits.

The lists are pilots of 200 nouns each. Everything needed to make them longer
is kept: the full counts, every review decision (including drops and why),
each list's rules and its book-selection filters.

Everything except `choose_books.py` runs in Docker (`run.sh`, image from
`Dockerfile`) on a Docker host; downloads are cached in `cache/` (git-ignored).

## A list's folder

| File | What | Made by |
|---|---|---|
| `books.tsv` | Gutenberg numbers of the books counted | `choose_books.py` |
| `counts.tsv`, `stats.json` | every noun with count, range, books, examples | `count` |
| `draft.tsv` | top nouns in rank order, with Wikidata's article/plural/English and emoji candidates | `enrich` |
| `review.tsv` | **every review decision**: `keep` with article, plural, English, emoji, or `drop` with the reason | by hand |
| `list.json` | the list's rules: ranking, size, minimum books, lists and lemma groups to exclude, fields that must be unique | by hand |
| `nouns.tsv` | the nouns that go into the quiz | `select` |
| `spelling.tsv` | optional extra lemma fixes for this list only | by hand |

`review.tsv` and `list.json` are the source of truth; `nouns.tsv` is derived.

## Steps

1. `python3 tools/wordlists/choose_books.py <list>` picks the books from
   Gutenberg's catalog with that list's filters (in the script), at most 3 per
   author. Add filters there to start a new list.
2. `tools/wordlists/run.sh count <list>`
   Downloads the books, modernizes old spelling with the shared
   `oldspelling.tsv` *before* tagging (Thür → Tür, Theil → Teil, Geheimniß →
   Geheimnis, daß → dass, Cultur → Kultur; rules are case-sensitive and `*`
   marks "more letters here"), tags every word with spaCy's German model
   (`de_core_news_md`), fixes lemmas the tagger gets wrong with the shared
   `lemmas.tsv` (old datives such as Volke, dictionary entries in old spelling
   such as Beschluß) and the list's optional `spelling.tsv`, and counts nouns.
   A noun must occur in at least 3 chunks of 1000 words and 3 books.
3. `tools/wordlists/run.sh enrich <list> <how many> [reference]`
   Takes the top nouns and adds article, plural and English from Wikidata
   lexemes (CC0) and emoji candidates whose German name (Unicode CLDR) is the
   noun. With a reference list (for domains: `core`), nouns are ranked by
   **keyness** instead of count: log-likelihood (Dunning G²) of how much more
   the domain uses the noun than the reference does, so a history list shows
   Regierung and Flotte rather than Mann and Frau again.
4. **Review by hand** into `review.tsv`. The tagger and Wikidata both make
   mistakes, and Wikidata's English is mostly the wrong sense, so this step is
   not optional: fix lemmas, pick one article, write the plural without its
   article (empty for mass nouns such as Wasser), give one short English gloss
   that suits the domain, and keep an emoji only when it clearly shows that
   noun. Drop names, dialect, diminutives, abbreviations and tagger artifacts,
   with the reason in `note`.
5. `tools/wordlists/run.sh select <list>` writes `nouns.tsv`: the first `size`
   nouns in draft order that the review keeps and the rules allow. If it meets
   nouns nobody has decided yet, it lists them instead: that is the next
   review batch. It also refuses repeated values in the `unique` fields (no two
   nouns with the same English gloss, or English → German cards become
   ambiguous; no picture twice).
6. `tools/wordlists/run.sh build <list> <Title> ../../apps/quiz/samples`
   Writes `<Title> nouns.xlsx` and, if any noun has a picture, `<Title>
   pictures.zip` (Noto Emoji pictures, resized, with their Apache 2.0 license
   and a credits file inside the zip). These are online-only samples: the app
   downloads them from the site when added. Then regenerate
   `apps/quiz/samples/catalog.json` (see `apps/quiz/DESIGN.md`), or
   `SampleCatalogTest` fails.

### Growing a list

Raise `size` in `list.json`, run `enrich` with enough nouns to cover it (rank
order and earlier decisions do not change), run `select` to get the undecided
batch, review that batch into `review.tsv`, and repeat until `select` writes
`nouns.tsv`. Then `build`. Domain lists exclude the core list's nouns, so they
change when the core list grows. Coverage of the noun uses in the core corpus:
top 200: 37%, 1000: 59%, 2000: 68%, 3000: 73%, 5000: 78%. Past about 2000 the
nouns occur in fewer books; a `min_books` rule keeps out single-novel words.

From a laptop, run the Docker steps on a remote Docker host the same way
`tools/remote-build.sh` does: rsync the checkout there, run `run.sh`, and copy
the list folder back. A list of a few hundred books takes 10–20 minutes the
first time (polite downloads) and about as long again to tag.

## Lists

| List | Books | Rules (`list.json`) |
|---|---|---|
| `grimm/` | Gutenberg #77905, *Deutsche Märchen gesammelt durch die Brüder Grimm* (1921 edition, about 110,000 words) | By count. Counted before the shared spelling files existed; its `spelling.tsv` covers its few old forms. |
| `core/` | 365 German-literature novels and stories by 242 authors born 1840–1900 (written about 1870–1930); no plays, poetry, children's books, history or non-fiction shelves. 22 million words. | By count: the general reference list. |
| `history/` | 184 books from Gutenberg's history shelves by 157 authors born from 1750; no novels, plays, poetry, travel, art, science or nature shelves, no translations (Macaulay, Gibbon, ...), no Saxon nature-conservation magazine. 14 million words. | By keyness against `core`. Excludes the core list's nouns, the months, and nouns in fewer than 25 of the 184 books (naval and other single-topic terms). |

## Licenses

- Source texts: public domain. Only counts and our own reviewed data are kept.
- Wikidata: CC0. Unicode CLDR emoji names: Unicode License.
- Noto Emoji pictures: Apache License 2.0; the license and a credits note
  travel inside each picture zip.
- English glosses: written during review (Wikidata's rarely fit the domain).

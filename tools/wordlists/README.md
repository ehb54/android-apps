# Word lists from public-domain texts

Builds noun frequency lists for one domain (fairy tales, history, lectures, ...)
from public-domain texts, and turns them into Quiz sample files: an `.xlsx`
with tabs for nouns (German → English), articles (noun → der/die/das) and
plurals, plus a `.zip` of picture cards where an emoji fits the noun.

Everything runs in Docker (`run.sh`, image from `Dockerfile`) on a Docker host;
downloads are cached in `cache/` (git-ignored).

## Steps for one list (here `grimm`)

1. `tools/wordlists/run.sh count grimm 77905`
   Downloads the Gutenberg book(s) by number, tags every word with spaCy's
   German model (`de_core_news_md`), and counts nouns by their dictionary form.
   Writes `grimm/counts.tsv`: count, *range* (in how many 1000-word chunks the
   noun occurs, so one long passage cannot dominate), and an example sentence.
   Nouns in fewer than 3 chunks are dropped. An optional `grimm/spelling.tsv`
   (`found` → `lemma`) maps old spellings and forms the tagger leaves alone
   (Schloß, Türe, Walde) to today's lemma before counting, so they count together.
2. `tools/wordlists/run.sh enrich grimm 250`
   Takes the top nouns and adds article, plural and English glosses from
   Wikidata lexemes (CC0), plus emoji candidates whose German name (Unicode
   CLDR) is the noun. Writes `grimm/draft.tsv`.
3. **Review by hand** into `grimm/nouns.tsv` (columns `lemma article plural english
   emoji`, in frequency order). The tagger and Wikidata both make mistakes and
   old texts use old spelling, so this step is not optional: fix lemmas, use
   modern spelling, pick one article, write the plural without its article (empty
   for mass nouns such as Wasser), give one short English gloss that suits the
   domain (no two nouns with the same gloss, or English → German cards become
   ambiguous), drop names, dialect, diminutives and tale-specific words, and keep
   an emoji only when it clearly shows that noun (no emoji twice). `nouns.tsv` is
   the source of truth and is committed; `build` checks it against `counts.tsv`.
4. `tools/wordlists/run.sh build grimm Grimm ../../apps/quiz/src/main/assets/samples`
   Writes `Grimm nouns.xlsx` and `Grimm pictures.zip` (Noto Emoji pictures,
   resized, with their Apache 2.0 license and a credits file inside the zip).

From a laptop, run the steps on a remote Docker host the same way
`tools/remote-build.sh` does: rsync the checkout there, run `run.sh`, and copy
the list folder back.

## Lists

| List | Source | Notes |
|---|---|---|
| `grimm/` | Gutenberg #77905, *Deutsche Märchen gesammelt durch die Brüder Grimm* (1921 edition, about 92,000 words) | 1901 spelling: only ß differs from today. |

## Licenses

- Source texts: public domain. Only counts and our own reviewed data are kept.
- Wikidata: CC0. Unicode CLDR emoji names: Unicode License.
- Noto Emoji pictures: Apache License 2.0; the license and a credits note
  travel inside each picture zip.
- English glosses: Wikidata where it has them; otherwise written during review.

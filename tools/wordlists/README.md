# Word lists from public-domain texts

Builds noun frequency lists from public-domain texts, a general one and one
per domain (fairy tales, history, ...), and turns them into Quiz sample files:
an `.xlsx` with tabs for nouns (German → English), articles (noun →
der/die/das) and plurals, plus a `.zip` of picture cards where an emoji fits.

Everything runs in Docker (`run.sh`, image from `Dockerfile`) on a Docker host;
downloads are cached in `cache/` (git-ignored).

## Steps for one list

1. **Choose the books** into `<list>/books.tsv` (`id author title`, Gutenberg
   numbers). Picked from Gutenberg's catalog (`pg_catalog.csv`) by shelf,
   subject and author era, at most 3 books per author; the criteria for each
   list are under *Lists* below.
2. `tools/wordlists/run.sh count <list>`
   Downloads the books, modernizes old spelling with the shared
   `oldspelling.tsv` *before* tagging (Thür → Tür, Theil → Teil, Geheimniß →
   Geheimnis, daß → dass, Cultur → Kultur; rules are case-sensitive and
   `*` marks "more letters here"), tags every word with spaCy's German model
   (`de_core_news_md`), maps lemmas the tagger gets wrong with the shared
   `lemmas.tsv` (old datives such as Volke, dictionary entries in old spelling
   such as Beschluß) and an optional `<list>/spelling.tsv`, and counts nouns.
   Writes `<list>/counts.tsv` (count; *range*, the number of 1000-word chunks
   with the noun; the number of books with it; example sentences for the top
   1000) and `<list>/stats.json`. A noun must occur in at least 3 chunks and 3
   books.
3. `tools/wordlists/run.sh enrich <list> 300 [reference]`
   Takes the top nouns and adds article, plural and English glosses from
   Wikidata lexemes (CC0), plus emoji candidates whose German name (Unicode
   CLDR) is the noun. Writes `<list>/draft.tsv`. With a reference list (for
   domains: `core`), nouns are ranked by **keyness** instead of count:
   log-likelihood (Dunning G²) of how much more the domain uses the noun than
   the reference does, so a history list shows Regierung and Flotte rather
   than Mann and Frau again.
4. **Review by hand** into `<list>/nouns.tsv` (columns `lemma article plural
   english emoji`, in draft order). The tagger and Wikidata both make mistakes,
   and Wikidata's English is mostly the wrong sense, so this step is not
   optional: fix lemmas, pick one article, write the plural without its article
   (empty for mass nouns such as Wasser), give one short English gloss that
   suits the domain (no two nouns with the same gloss, or English → German
   cards become ambiguous), drop names, dialect, diminutives, abbreviations and
   tagger artifacts, and keep an emoji only when it clearly shows that noun (no
   emoji twice). Domain lists also drop nouns already in the core list.
   `nouns.tsv` is the source of truth and is committed; `build` checks it
   against `counts.tsv`.
5. `tools/wordlists/run.sh build <list> <Title> ../../apps/quiz/src/main/assets/samples`
   Writes `<Title> nouns.xlsx` and, if any noun has a picture, `<Title>
   pictures.zip` (Noto Emoji pictures, resized, with their Apache 2.0 license
   and a credits file inside the zip).

From a laptop, run the steps on a remote Docker host the same way
`tools/remote-build.sh` does: rsync the checkout there, run `run.sh`, and copy
the list folder back. A list of a few hundred books takes 10–20 minutes the
first time (polite downloads) and about as long again to tag.

## Lists

| List | Source | Review notes |
|---|---|---|
| `grimm/` | Gutenberg #77905, *Deutsche Märchen gesammelt durch die Brüder Grimm* (1921 edition, about 110,000 words) | Ranked by count. Counted before the shared spelling files existed; `grimm/spelling.tsv` covers its few old forms. |
| `core/` | 365 German-literature novels and stories by 242 authors born 1840–1900 (written about 1870–1930), at most 3 per author; no plays, poetry, children's books, history or non-fiction shelves. 22 million words. | Ranked by count: the general reference list. |
| `history/` | 184 books from Gutenberg's history shelves by 157 authors born from 1750, at most 3 per author; no novels, plays, poetry, travel, art, science or nature shelves, no translations (Macaulay, Gibbon, ...) and no Saxon nature-conservation magazine. 14 million words. | Ranked by keyness against `core`. Dropped: nouns in the core list, the months, and nouns in fewer than 25 of the 184 books (naval and other single-topic terms). |

## Licenses

- Source texts: public domain. Only counts and our own reviewed data are kept.
- Wikidata: CC0. Unicode CLDR emoji names: Unicode License.
- Noto Emoji pictures: Apache License 2.0; the license and a credits note
  travel inside each picture zip.
- English glosses: written during review (Wikidata's rarely fit the domain).

# Planning the pictures for a word list

The guide for writing a list's `plan.tsv`, whether a person or an AI agent
does it. Each planned word becomes a picture card asked only one way,
picture → German (nouns: `das Haus`; verbs: the infinitive), and a picture tile
in Match mode. So a picture must show the sense the list uses, and a learner
who sees it must have only one right answer in the list.

## Input and output

A plan batch (`pictures.py pending`) is a TSV of the list's words: `rank`
(position in `nouns.tsv` or `verbs.tsv`), `lemma`, `kind` (`noun` or `verb`),
`card` (what the learner types), the reviewed `english` gloss, the `emoji` the
list uses now (nouns), `forms` (verbs) and an `example` sentence from the books.
The gloss and the example decide the sense: der See is a lake, das Schloss a
castle (gloss `castle`), not a lock.

Write one output row per input row, same lemmas, same order, as a TSV with
exactly this header (tabs between columns, UTF-8):

`lemma	kind	picturable	prompt	note`

- `lemma`, `kind`: copied unchanged.
- `picturable`:
  - `yes`: one thing, plant, animal or person on its own shows the word
    plainly (Haus, Apfel, Hund, Arzt; verbs: one figure doing an action that
    needs nothing else, such as schlafen or springen).
  - `scene`: the word needs a few elements or a small setting to be
    recognized (Bahnhof, Hochzeit, Ernte, Ufer; verbs with an object, such as
    a person writing a letter at a desk). Still one clear focus.
  - `no`: no picture shows it plainly, or the picture could not be told
    apart from another word of the same kind in the list (see below).
- `prompt`: for `yes` and `scene`, what the picture shows, in English (below).
  Empty for `no`.
- `note`: for `no`, the reason (required): `abstract`, `relational` (Vater,
  Freund: a picture shows a man, not the relation), `duplicates <lemma>`
  (another word of the list gets the picture), `sensitive` (below), or a
  short reason of your own. For `yes` and `scene`, empty or a short remark;
  name the near-synonyms the prompt keeps apart as `near: Pfad, Gasse`.

## One right answer

Nouns and verbs are separate quizzes, so only words of the same kind compete.
Check every word against the whole list (`nouns.tsv` or `verbs.tsv` of the
list), not just the batch:

- **Near-synonyms** (Weg, Pfad, Gasse, Straße; Pferd, Ross, Gaul; Zimmer,
  Stube, Kammer, Raum; gehen, laufen, schreiten; sehen, schauen, blicken):
  give each one a picture only if the prompt shows a difference a learner sees
  at once (Pfad: a narrow footpath through a meadow; Gasse: a narrow alley
  between old town houses; Straße: a street with houses and a pavement). If no
  plain difference exists, one of them gets the picture and the others get
  `no` with `duplicates <lemma>`: the everyday word a learner would name first
  (Arzt over Doktor, Ziege over Bock, Friedhof over Kirchhof, aufstehen over
  sich erheben), or the one the list already shows with an emoji; failing
  both, the most frequent (lowest rank). Never let two words of a kind share
  one prompt.
- **Kinds of a thing** (Rose and Blume, Eiche and Baum, Zylinder and Hut) may
  each have a picture when the kind is plain at a glance; the general word then
  shows an ordinary, unremarkable one. A kind that looks like the general word
  (Schimmel, Bock, Glastür, Haustür) gets `duplicates <general word>`.
- **Part and whole, thing and place** (Blitz and Gewitter, Baum and Wald,
  Wanderer and Wanderung): the more concrete word gets the picture shown close
  up; the other gets a picture only if it is clearly something else (Wald: many
  trees, seen from outside), or `no`. The seasons are time words and all get
  `no` (a winter scene is Schnee, a spring one Blüte).
- **People**: a figure shows a man, a woman or a child. Mann, Frau, Kind,
  Junge, Mädchen get pictures; Herr, Dame, Weib, Mensch, Person, Leute and
  kinship and relation words (Vater, Mutter, Sohn, Freund, Nachbar, Gast)
  usually get `no`, since a picture of a man is "der Mann". Roles that a
  picture shows plainly by dress or tools get pictures (Arzt, Soldat, Koch,
  König, Bauer, Braut), unless another role in the list looks the same
  (König, Kaiser, Fürst: keep the one a crown makes plain).
- **Emotions and states** (Angst, Furcht, Schreck; Freude, Glück; Zorn, Wut)
  are nearly always `no`: a face shows one of a group, never which word.

## Abstract words

Mark `no` any word that no picture shows plainly, rather than forcing an
association: no hourglass for Zeit, no heart for Liebe, no question mark for
Frage, no skull for Tod. This is the same rule as for emoji in
`../wordlists/REVIEW.md`. Time words (Tag, Jahr, Stunde, Augenblick), measures,
relations, thoughts and speech (Wort, Gedanke, Rede) are `no`. Verbs of being,
having, thinking, wanting and saying (sein, haben, werden, können, glauben,
meinen, wissen, sagen, scheinen) are `no`, and so are verbs whose action looks
like another verb's (sprechen and reden; legen, stellen and setzen, unless the
picture makes the difference plain).

## Writing a prompt

- English, 5–30 words, describing what is drawn: the subject and the one or
  two features that pin its sense (`a calm blue lake with reeds along the shore
  and low hills behind`; `a medieval stone castle with towers and a flag`).
- Do not repeat the shared style; it is added to every prompt:
  "simple flat illustration, plain white background, no text or letters,
  centered subject".
- No text in the picture: nothing that invites lettering (signs, labels,
  book titles, banknote numbers). For things made of writing (Brief, Zeitung,
  Buch) describe them so the models draw lines, not letters (`a folded
  newspaper with grey columns and a photo`).
- Verbs show one person or animal doing the action at its clearest moment
  (`a person running, mid-stride, arms swinging`; `a dog digging a hole in the
  ground`). Use "a person" unless the verb needs more; keep the figure neutral.
- People: adults unless the word is about a child; women for female nouns
  (Lehrerin); ordinary, neutral clothes unless the role needs a costume.
  Name each person's look rather than writing "a person": "a woman with long
  blond hair", "an older man with grey hair", "a red-haired farmer". Left to
  itself a model draws the same dark-haired young man every time. Vary hair,
  age and, where the word allows, gender from one word to the next.
- `sensitive` (`no`): nationalities and peoples (Franzose, Jude), God, saints
  and religious rites (Gott, Heilige, Taufe), death, killing, executions,
  wounds and anything gory, and anything that would need a stereotype to be
  recognized. Church buildings and objects (Kirche, Kreuz, Altar), clergy shown
  by dress (Pfarrer, Mönch, Nonne), a person praying (beten), and fairy-tale
  figures (Hexe, Fee, Zwerg, Engel, Teufel) are fine.
- Plurals are not pictured as such: the card answers with the singular.

# The Professor's Thesis

A Cobblemon story arc: the village's professor writes a thesis and needs a player for the field work: three catches
made where and when the theory says, a Pokémon shown grown, and a battle against a rival's best. The thesis is
published as a book with the player as co-author. ROADMAP 31.21 (`arcs/professors_thesis.json`). Builds on the arc
engine (31.4), quests (31.2), reputation and honors (31.11), the Pokémon Professor (29.21), research (29.11) and
the trainers (28.x). Part of the 1.5 expansion (behind `storyArcs`).

Roadmap items: 31.21

## What a player sees

- **Who and when**: only with Cobblemon (without it the arc file doesn't load). In a Town (or bigger) that has the
  Pokémon Professor, or a Scholar at Expert or above, 6% a day once the arc cooldown is over. The patron is the
  Professor; without one, the village's best Scholar at Expert or above, called "Professor Alder" for the arc.
- **The topic**, one of six, picked at the start; the professor says it in chat ("I'm writing my thesis on Life in the
  Cold. Will you help me with the field work?") and the chronicle notes it:
  - **Life in the Cold**: an Ice type in a snowy biome, a Water type in a frozen ocean or river, any Pokémon above
    y=150;
  - **Fire and Stone**: a Fire type in a desert or badlands, a Rock type in stony or jagged peaks, a Ground type
    below y=0;
  - **Creatures of the Night**: a Dark type at night, a Ghost type at night, a Psychic type on a full-moon night;
  - **The Forest's Web**: a Bug type in a jungle, a Grass type in a forest, a Flying type in a meadow or cherry grove;
  - **Sea and Shore**: a Water type in a warm (or lukewarm) ocean, one in a deep ocean, one on a beach;
  - **Living Fossils**: a fossil revived by a Fossil Scientist, a Dragon type, a Rock type in badlands.
- **1. Field Notes**: three quests, one per catch ("Catch an Ice type in a snowy biome"). A capture counts where the
  ball landed, at that moment (night is 13000 to 23000 ticks into the day; a full moon is moon phase 0; "above y=150"
  is higher than 150, "below y=0" lower than 0). Each catch counts for one quest only, the first open one it matches;
  chat says "Snorunt goes in the field notes: Catch an Ice type in a snowy biome." Each catch is written down: species,
  biome, place, day and catcher.
- **2. A Closer Look**: right-click the professor while carrying, in the party, one of the thesis' catches that has
  evolved since (the same Pokémon, a new species), or any Pokémon at friendship 200 or more. Otherwise they say what
  they want to see.
- **3. Peer Review**: beat the Trainer Leader of another village with a hall within caravan range (2048 blocks; the
  nearest that has one, as its hall last saw it). The quest names the village ("Beat the Trainer Leader of
  Thornfield"). With none, the rival scholar **Thessaly** comes to the hall: a named Master trainer (tier 5) who
  battles through the trainer engine like any village trainer; beaten, she says the thesis stands and leaves in a puff.
  The thesis' own village's Leader doesn't count.
- **The ending**: every helper gets the thesis, a written book named "Professor Alder: Life in the Cold": a cover page
  (topic, author, co-author), a page per catch (species, biome, coordinates, day, catcher) and a closing page; 3 Rare
  Candies and an Ability Capsule (`cobblemon:rare_candy`, `cobblemon:ability_capsule`; 4 emeralds for each one missing);
  +150 standing and the honor **Co-author**. The village gets a free research level: with the Pokémon Professor as
  patron, the Pokédex tree's topic in progress or its next one not at its top; otherwise the scholars' topic in
  progress, or the next one open. The chronicle notes the thesis and the research.

## How it works

- `story/ProfessorsThesis` (core): the six topics (`TOPICS`), the arc effect `professors_thesis` with the steps
  `begin` (patron and topic; no patron: the arc ends quietly), `field_notes` (posts the three catch quests),
  `peer_review` (the Leader's village in `extra.leader_hall`, or Thessaly spawned, `extra.thessaly`) and `ending`;
  the objectives `catch`, `show` and `battle_leader`; the trigger condition `thesis_patron`. Catches are kept in the
  arc's `extra.catches`, so they survive a restart.
- **`catch`**: `{"type": "catch", "text": <lang key>, "types": ["ice"], "biomes": ["minecraft:desert", "#minecraft:is_badlands"],
  "time": "day" | "night" | "full_moon", "above": 150, "below": 0, "fossil": false}`; every field is optional (none:
  any). `ProfessorsThesis.onCatch` is called by `compat/cobblemon/CobblemonThesis` on Cobblemon's `POKEMON_CAPTURED`
  and by `CobblemonFossils.revive` (the Fossil Scientist) with `fossil` set.
- **`show`**: `{"type": "show", "friendship": 200}`, checked on a right-click of the professor (the role
  `professor`) through the extension point `ProfessorsThesis.Party`, filled by `CobblemonThesis` with the party.
- **`battle_leader`**: `{"type": "battle_leader"}`; `Trainers.battleOver` (a player's win) calls
  `ProfessorsThesis.onTrainerWon`. Known villages' Leaders come from `Caravans.Data.leader` (written by each hall's
  round), or the village's census when loaded.
- Tests and the showcase may set `ProfessorsThesis.FORCE_TOPIC`.

## Items, blocks, jobs, commands

- No new item, block or job. The thesis is a vanilla written book with a custom name; the rewards are Cobblemon's own
  items by id. Thessaly is a plain villager with the Trainer job at Master, named and tagged
  (`aliveworkplace_thessaly`).
- `/workplace story start professors_thesis` and the other story commands work on it as on every arc.

## Switches

- No switch of its own: it runs under `storyArcs`, `arcCooldownDays`, `arcsAtOnce` and `disabledArcs`
  (`professors_thesis`) like every arc, and only with Cobblemon installed.

## Saved data

- Saved with every arc (`aliveworkplace_stories`), in the arc's `extra` tag: `topic`, `catches` (each: `uuid`,
  `species`, `name`, `biome`, `pos`, `day`, `by`), `leader_hall` or `thessaly`. The patron is the arc's role
  `professor`. Nothing new for other saves.

## Decisions

- 31.21: the six topics are built in code (their catches are `catch` objectives with a lang line each) and the
  `field_notes` step posts them as three quests, so each catch is its own quest and they can be done in any order.
- 31.21: a catch counts where the Poké Ball landed; a revived fossil where the player stands. "Warm ocean" takes the
  lukewarm oceans too; "snowy biome" is the vanilla snowy biomes listed in the topic (snowy plains, taiga, slopes and
  beach, ice spikes, frozen and jagged peaks, grove, the frozen river and oceans).
- 31.21: "evolved" means the same Pokémon (its id) caught for the thesis is now another species.
- 31.21: the trigger needs the patron to exist (`thesis_patron`: the Professor or an Expert Scholar), not just any
  Scholar, since a lower Scholar couldn't be the patron. A Scholar away from the desk still counts.
- 31.21: Thessaly is the trainer job at Master (not a Leader, so the home village's Leader doesn't take her
  challenges); she leaves when beaten (a loss can be retried) or when the thesis is published. Today a thesis
  stopped (`/workplace story stop`) or switched off (`disabledArcs`) during Peer Review leaves her standing by the
  hall (a bug; failing tests on the tests/stadium-thesis-1028 branch).
- 31.21: every helper gets a copy of the book and the rewards; one who is offline gets them dropped on the hall.
- 31.21: a reward item Cobblemon lacks is paid as 4 emeralds.
- 31.21: the free research level is the topic in progress, else the first open one in the tree's order.

## Known limits

- A village within caravan range counts as having a Leader if its hall's last round wrote one, or (loaded) one lives
  there; a village never loaded since its Leader came isn't known.
- A Pokémon caught in a biome a pack adds counts only where a topic's list or tag names it.

## Proof

- Compat GameTests (with Cobblemon), `ThesisCompatTests`: catches by type, biome and height, each once
  (`catchesCountByTypeBiomeAndHeight`); by night, full moon and depth (`catchesCountByTimeAndDepth`); `show` refuses an
  unevolved catch and takes one evolved or at friendship 200 (`showAnEvolvedCatchOrAFriend`); `battle_leader` counts
  another village's Leader, not the home one's (`beatTheLeaderOfAnotherVillage`); Thessaly comes, is Master, leaves
  beaten; the book lists the catches and the co-author; candies, capsule, Co-author, research
  (`thessalyComesAndTheBookIsPublished`).
- GameTests, `ProfessorsThesisGameTests`: without Cobblemon the arc isn't loaded and a catch counts nothing
  (`withoutCobblemonTheArcNeverStarts`); the objectives read and write back, the fossil and badlands rules, emeralds
  for missing rewards (`theObjectivesReadAndMatch`).
- QA compat GameTests, `QaThesis1028CompatTests`: the patron and the trigger from both sides (a Journeyman Scholar
  isn't the patron, an Expert is, a Master beats an Expert, a Village doesn't meet the trigger, 6% a day; the request
  and the chronicle line) (`thePatronAndTheTriggerFromBothSides`); the Forest's Web, Sea and Shore and Living Fossils
  with their near misses (`theForestTheSeaAndTheFossils`); y=151 and y=-1 count, y=150 and y=0 don't, night from 13000
  to 23000 on any day, the full moon only at night, Fire and Stone's places (`heightAndTimeEdgesFromBothSides`); every
  sentence the co-author reads, a loss leaving Thessaly there, +150 reputation (`everySentenceTheCoAuthorReads`); the
  book's pages word for word (`theBooksPagesNameSpeciesPlaceAndDay`); the thesis and Thessaly through a save
  (`theThesisAndThessalySurviveASave`). Failing tests on the tests/stadium-thesis-1028 branch
  (until fixed): Thessaly stays when the thesis is stopped or switched off.
- Showcase scene `professors_thesis` (Cobblemon): the request, a catch counting, the published book.

# Story arcs: the engine

Now and then a village lives through a tale of its own: a story arc, told chapter by chapter, with quests for the
players, named foes, builds out in the land and an ending. This page is the engine every arc runs on; the arcs
themselves have their own pages (the Bandit King, the Sickness, the Lost Caravan, the Professor's Thesis, the reforms).
Part of the 1.5 expansion: switched on when Milestone 31 is complete.

Roadmap items: 31.4

## What a player sees

- **A chapter begins.** Everyone in and near the village (its radius plus 64 blocks) reads the arc's name, the
  chapter's number and name in gold ("The Test of Three, chapter 2: Apples wanted") and its intro lines; the chronicle
  writes the same under a new Story kind (a book). A player far away hears nothing.
- **Quests.** A chapter's quests go up in the journal's Story tab, never on the hall's daily board. A chapter is done
  when all its quests are, or any one of them (the rest come down), or only the ones it names, as the arc's file says.
  The next chapter may wait some days; a chapter with a time limit fails when it runs out, and its failure runs (a
  message, a village mood, the arc ending badly).
- **The Story tab** shows the arc's title and the day of the tale, each chapter so far (done ones ticked, the current
  one "Happening now" with its quests, a waiting one "Begins on day N"), and "Also unfolding: ..." for a side arc
  beside it. With none: "No story is unfolding here".
- **The villagers** talk of the running chapter as village news.
- **Talk.** A `talk` step asks the player to right-click the villager the arc named ("Talk to Ilse"); they say their
  line. A villager of the village who isn't cast doesn't count, and neither does one another arc (or the same arc in
  another village) cast in a part of the same name; a `kill` of a `role:` foe counts only the one this arc cast too.
- **Foes and builds.** A spawned foe has its name, gear, extra health and a boss bar, and keeps near its spot. A build
  out in the land goes up only once a player comes within 96 blocks of it, on natural ground and plants only (never on
  a floor or road someone laid), and only once; a map can lead there.
- **The end.** "The tale of X has come to its end." (or "... has ended badly."), the ending's rewards, and the village
  waits `arcCooldownDays` before its next arc.

## How it works

- Arcs are data: `data/<namespace>/arcs/<id>.json`, one per file: a `trigger` (conditions, a chance a day, or an event),
  `roles` (villagers cast in parts), the `chapters` in order and an `ending`. A file that can't be read is skipped with a
  log line; the others load.
- A chapter: `name`, `intro`, `on_start` effects, `quests` (ids or written inline), `complete` (`all`, `any` or a list
  of quest ids), `needs_flags`, `delay_days`, `time_limit_days` with `on_fail`, and `chatter`.
- Effects: `announce`, `chronicle`, `place`, `spawn`, `give_map`, `village_mood`, `festival` (the next day, through the
  festivals), `flag` (for some days, which the village keeps after the arc ends, or for good), `start_quest`, `end_arc`,
  and any quest reward.
- One main arc runs in a village at a time; side arcs (the reforms) run beside it. The hall's round moves arcs on and
  rolls for a new one: it needs no main arc running there, `arcCooldownDays` passed since the last ended (8: an arc that
  ended on day 10 keeps the next away on day 17, and one may start on day 18), and fewer than `arcsAtOnce` main arcs on
  the whole server (side arcs aren't counted). A `threat` arc doesn't start in a village At peace or at threat level 0.
- Every 40 ticks the engine checks waiting builds and spawned foes against the players' positions: a build is placed
  once a player is near; a foe comes once a player is near its spot, and one that's gone (its chunk unloaded and lost)
  is put back at its spot.
- An arc whose file was removed ends quietly at its next round, with a log line. With `storyArcs` off a running arc
  (main or side) ends quietly at its next round; its builds stay, its foes leave in a puff.

## Switches

| Key | Default | What it does |
|---|---|---|
| `storyArcs` | on (with Milestone 31) | Off: no arc starts, a running one ends quietly at its next round |
| `arcCooldownDays` | 8 | Days between two arcs in one village (also before a village's first) |
| `arcsAtOnce` | 3 | Main arcs running at once on the whole server; side arcs aren't counted; 0: none |
| `disabledArcs` | empty | Arc ids that never start (not even by command); a running one ends at its next round. File only |

## Saved data

- With each dimension, in `aliveworkplace_stories`, per hall: the running `arc` and `side_arcs` (the chapter, the day
  it began, flags, cast roles, spawned foes and their spots, placed builds and where, the chapter's quests and
  helpers), `last_arc_day` and `village_flags`. Every field has a default, and an arc carries on after a restart at any
  tick. The full list is in `docs/design/M31.md`.

## Items, blocks, jobs, commands

- `/workplace story start <arc>` (the full id or its path alone), `/workplace story next` (the running chapter is done
  now; on the last one the tale ends) and `/workplace story stop`, operators only, in the village the command runs in.
- The hall's Story tab (the journal).
- Data: `data/<namespace>/arcs/<id>.json`, `data/<namespace>/arc_spawns/<id>.json` (spawn groups).

## Decisions

- A build waits for a player within 96 blocks, so its chunk is loaded and nothing has to generate.
- A village's first arc waits `arcCooldownDays` from the day the engine first sees its hall, so a live server doesn't
  get an arc in every village on the day 1.5 lands.
- A `flag` with days outlives its arc in the village (the Bandit King's ending's ten quiet days).

## Known limits

- When the same arc runs in two villages, killing one village's spawned `role:` foe also counts for the other
  village's `kill` step while that village's own foe hasn't spawned yet (B724, found by QA on 2026-10-31; its failing
  test waits on the branch tests/village-verify-1031 for the fix).

## Proof

GameTests: `StoryArcGameTests` (the test arc from start to end with time skips, its missed time limit, a save and
reload mid-chapter with its foe put back, a removed file, a far build waiting for a player, `talk`, the cap,
`storyArcs` off, the commands), `QaStoryArcEngine1031GameTests` (the cooldown on day 7 and day 8, the cap across villages
with a side arc not counted and shown on the Story tab, a foe waiting for a player and coming with the 40-tick check, a
far build never over paving and once across a reload, who hears an announcement, the `any` and named-list chapter endings, the flag, festival, extra quest and chronicle effects, the commands' edges),
`QaDisabledArcsGameTests`, `QaThreatArcsPeace1031GameTests`, `QaStoryArcsPeace1031GameTests`,
`QaRefusedArcMobs1031GameTests`, `QaHallStoryGameTests`, `QaStoryArcEngineBug1031Tests`, `StoryArcCastGameTests`
(a `talk` or `role:` kill counts only the one this arc cast in this village, across a reload) and
`QaVillage1031VerifyGameTests` (a villager tagged by hand as the part is no stand-in). Showcase scene `story_arc`.

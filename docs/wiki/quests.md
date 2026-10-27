# Quests: the engine and the journal

A village's hall posts quests for players: bring things, defeat monsters, go somewhere, wait out a few days. The hall's
Quests page is a journal of them, and one quest at a time can be tracked on screen. ROADMAP 31.2 (the quest engine) and
31.3 (the journal, tracking and quest maps). Personal requests (31.9), bounties (31.13) and story arcs (31.4) are quests
too and show in the same journal. Part of the 1.5 expansion: switched on when Milestone 31 is complete.

Roadmap items: 31.2, 31.3

## What a player sees

- **The journal.** The hall's Quests button (or the Village Ledger, from anywhere) opens it, with four tabs: Village,
  Personal, Story and Bounties. Each quest says who asked ("Asked by Ana"), one line per step with its progress
  ("Bring 2 Apple (0/2)"), the reward ("Reward: 3 emeralds") and the days left ("Up for 3 more days"; "Up for 1 more
  day" on its last day).
- **Handing in.** A click hands in what a `bring` step still needs, up to what is still wanted: with 3 apples for a
  quest that wants 1 more, 1 is taken and the other 2 stay with you. The things go into the village's store. A quest is
  paid once, to the player who finished it, with a "Quest done" message and a chronicle line; clicking again pays
  nothing.
- **Tracking.** A click on a quest with nothing to hand in, or a shift-click on any, tracks it ("Tracking: ...") or
  stops ("No longer tracking: ..."). One tracked quest per player: tracking another replaces it. A tracked quest is a
  yellow bar at the top of that player's screen only ("The Long Walk: Wait 1 day (0/1)"), follows the progress, comes
  back after a restart, and goes when the quest is finished, comes down or its hall is broken. On a shared quest every
  player keeps their own bar.
- **The command.** `/workplace quests` lists the open quests of the nearest village, each with [Track] or [Untrack];
  `/workplace quests untrack` stops tracking.
- **Places and maps.** A `reach` step counts within its radius, measured flat and including the edge (a radius of 4
  counts at 4 blocks, not at 5 or on the 3-by-3 diagonal; spectators don't count). A quest's place (a structure, a biome
  or a point) is looked up once, when the quest goes up. If none of its options is found the board takes it down
  ("The board took down “Find the Lost Tower”: nobody knew the way") and posts another. A `map` reward is a map with a
  red X on the place, named "Map to the ..." ("Map to the marked place" for a point).
- **Time.** A quest is up for its `days`; it comes down at its due tick and pays nothing after. A `wait` step finishes
  after exactly its days.

## How it works

- Quests are data: `data/<namespace>/quests/<group>/<id>.json`. A file that can't be read (bad syntax, `days` 0, no
  objectives, a broken point) is skipped with one log line naming it; the others still load, and `/reload` reads them
  again.
- The hall's daily round posts quests in the morning and counts their steps; kills and places are counted as they
  happen.
- A broken hall's quests go with it: a hand-in returns nothing and a kill counts for nothing.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageQuests` | on | Off: no new quests are posted; open ones can still be tracked, counted and handed in |

## Saved data

- With each dimension: `aliveworkplace_stories`, the open quests keyed by hall, with each step's progress, who helped,
  the dates, who tracks them and the place found. Every field has a default.

## Items, blocks, jobs, commands

- The Village Hall's Quests button and the Village Ledger open the journal.
- `/workplace quests` and `/workplace quests untrack`.
- Data: `data/<namespace>/quests/<group>/<id>.json`.

## Decisions

- A place is looked up once, when the quest goes up, so a journal never waits on a search; a quest whose place can't
  be found is taken down rather than posted with nowhere to go.
- One tracked quest per player keeps the bar to one line.

## Known limits

- Step lines name a thing in the singular whatever the count ("Defeat 2 Zombie", "Bring 5 Apple"): B568.

## Proof

GameTests: `QuestEngineGameTests`, `QuestJournalGameTests`, and `QaQuestJournalGameTests` (out of the suite until B572; the hall's Quests button and the
Ledger, hand-ins to the goal and once, the boundaries of days, ticks and radius, save and reload, two players, the hall
broken, the command, the switch off, broken files, places and maps, every sentence).

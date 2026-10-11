# The reforms as stories

With story arcs on, each of the eight edicts' reforms is told as a short story of the same name: a villager of the
fitting trade asks you for it, M30's three steps follow as its quests, and the ending leaves something lasting in the
village before the edict is reformed for good. ROADMAP 31.22 (`arcs/<name>.json`, `story/ReformStories`). Builds on
edicts and reforms (30.1 to 30.10), the arc engine (31.4) and quests (31.2). Part of the 1.5 expansion (behind
`storyArcs`).

Roadmap items: 31.22

## What a player sees

- **The ask**: when an edict with a reform is proclaimed (or at the hall's next round), a villager of the fitting trade
  is cast and the story's first chapter is told in chat and the chronicle. Its quest is to talk to them (right-click);
  they say why the edict hurts and what would fix it.
- **The three steps**: M30's steps, with M30's items, counts and pay (a quarter more a rank), one a chapter on the
  Story tab. The next step goes up the morning after the last, as in M30. Every chapter has its chatter.
- **The ending**, then the edict reformed (its boost stays, its cost goes, fireworks over the hall, the REFORM line in
  the chronicle), in that village only:

| Story | Edict | Who asks | Steps | Ending |
|---|---|---|---|---|
| The Shift Bell | Long Shifts | the Toolsmith, else any smith (Weaponsmith, Armorer) | 24 clocks, 128 gold, 300 bread | a bell on a spruce post beside the hall, on natural ground; it rings at the start (2000) and end (9000) of every work day |
| The Common Granary | Free Bread | a Farmer | 512 wheat, 128 hay, 48 barrels | a harvest supper at the hall the next evening (11000): everyone eats from the store (bread first) and counts it a festival; no fireworks |
| The Midwives | Large Families | the Nurse, else a mother | 48 honey bottles, 192 white wool, 64 golden carrots | a midwife, a new villager, comes: a Nurse at Journeyman |
| The Watchful Gate | Open Gates | a Guard | 192 iron, 256 arrows, slay 40 | an old soldier, a new villager, comes: a Guard at Expert, in iron armour with an iron sword |
| The Festival Fund | Festival Season | the Bard, else a Cheerful villager | 24 cakes, 256 fireworks, 48 note blocks | the next festival is a grand one: twice the fireworks, and its good mood lasts a day longer |
| The Fair Ledger | Tithe | a Librarian or a Scholar | 24 book and quills, 192 gold, a battle (else slay 32) | a written book, "The Fair Ledger of <village>", with the treasury's takings for each of the last 7 days, for the hall's owner |
| The Lamplighters | Curfew | a Porter, else anyone | 192 lanterns, 96 glowstone, slay 32 | two Street Lamp blueprints for whoever finished it, and a chronicle line for the first lamplit night |
| The Militia Drill | Conscription | a Guard | 32 iron swords, 32 shields, slay 48 | drill day: at noon the next day every grown villager spars at the village's Training Dummies until about 8400; with none, the guards spar on the square; then a chronicle line |

- The Fair Ledger's battle step is offered beside its fallback (slay 32); either finishes it. Without Cobblemon or a
  trainer only the fallback goes up.
- The bell rings only while it stands: break it and it stays silent.

## How it works

- `Reforms.round` asks `ReformStories.tells` for each edict in force: with `storyArcs` on and the reform's `arc` naming
  a loaded arc, the story starts (or is already running) and M30's step stays off the quest page.
- Reform arcs are `side` arcs with `trigger.chance` 0: they never start by themselves, don't count against
  `arcsAtOnce` or the arc cooldown, and run beside a village's main arc.
- Roles take alternatives in order, split by `|`: `job:<profession>`, `any`, `mother` (a villager who is a mother of a
  villager here) and `trait:<trait>` (31.22 added the last three forms).
- The ending runs the arc effect `reform` (`step`: `shift_bell`, `harvest_supper`, `midwife`, `old_soldier`,
  `grand_festival`, `fair_ledger`, `street_lamps`, `drill_day`), then the new reward `reform_edict` (`edict`: an edict
  id), which calls `Reforms.reform`, the same reform M30's last step makes. A pack can use `reform_edict` in any quest
  or arc.
- What an ending leaves for later is kept in the village's flags (`reform.bell`, `reform.supper`, `reform.grand`,
  `reform.grand_day`, `reform.lamplit`, `reform.drill`), saved with the village's stories, and acted on every second.
- The bell post is the template `story/shift_bell` (3 x 6 x 3, `tools/blueprints/reforms.py`), set on the first spot
  3 to 10 blocks from the hall with flat dirt, sand, gravel or stone under its 3 x 3 footing and air above.

## Switches

- `storyArcs` off: no reform story starts, a running one ends quietly at the hall's next round, and M30's own steps go
  up on the quest page as before.
- `disabledArcs` with a reform story's name: that reform keeps M30's steps.
- `edicts` off: no reforms at all.

## Saved data

- The village's flags in `aliveworkplace_stories` (above); the hall's `takings` (the treasury's takings by day, the
  last 8 days; empty in halls from before). Nothing renamed.

## Items, blocks, jobs, commands

- No new items or blocks. The Fair Ledger is a written book; the lamps are Street Lamp blueprints.

## Decisions

- A village that has begun M30's plain steps (one is up) finishes them as plain steps; the story only starts on a
  reform not begun. With nobody to ask (no Toolsmith nor smith, say), the plain steps go up instead.
- The drill with no dummies: the guards alone, sparring on the hall's square (the spec's "the guards alone").
- The work day is the villagers' WORK hours (2000 to 9000); a bell late by up to 1200 ticks (a sleeping server) still
  rings once.

## Known limits

- Turning `storyArcs` off mid-story starts the plain steps from the first.

## Proof

- GameTests: `ReformStoryGameTests` (one per reform, storyArcs off, nobody to ask).
- Showcase scene `reform_story`: The Shift Bell, from the Toolsmith's ask to the bell ringing.

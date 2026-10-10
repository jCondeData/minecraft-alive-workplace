# Heart events and life stories

ROADMAP 31.7. Builds on friendship (31.5) and gifts (31.6). Part of the 1.5 expansion: switched on when Milestone 31
is complete.

Roadmap items: 31.7

## What a player sees

At 2, 4, 6, 8 and 10 hearts a villager has something to tell you. The next time you're within 8 blocks while they're
off work, they walk up, face you and tell it: three to five lines over their head, one every 3 seconds, each also in
your chat in grey ("Dara: I learned to walk on these paths. I know which roofs leak, and which hens bite.") so it can
be read again. Walk away halfway and they stop; next time they start again from the first line. Told to the end, it
adds 20 friendship, the village's chronicle gets a line ("Dara told Jesse about growing up in Thornholm", with a pink
tulip) and it becomes part of their **life story**.

**Shift-click** someone with a name on the Village Hall's list for their life story page: your hearts with them (and
whether they have something to tell you now, or at how many hearts they will), their name day, their family (parents
and the children of theirs living in the village), their partner (married to, courting, or who they lost), and the
story so far: one book per event anyone was told, with who heard it. A plain click still finds them (or offers a job).

This update ships the 2-heart set, **Where I come from**, in four variants:

| Event | Told by | It says |
|---|---|---|
| `born_here` | villagers born in the village | where they were born, and names both parents |
| `traveller` | travellers hired at an inn | the road, the inn, and the day they were hired |
| `before_hall` | everyone else | the village before it had a hall and a name |
| `back_from_grave` | anyone brought back from a grave | what they remember of it |

## How it works

- **When it starts**: every 10 ticks the mod looks at each player. The nearest villager within 8 blocks who is
  awake, off work (`Chatter.offWork`: no work line over their head, not working, hiding or fleeing) and has something
  to tell that player starts. A player listens to one villager at a time.
- **Walking up**: they walk to within about 2 blocks, looking at the player, and stay by them to the end. If they
  can't get within 3.5 blocks in 5 seconds (a fence, a river), they tell it from where they stand.
- **Stopping**: the player more than 8 blocks away, dead or in spectator; the villager asleep, at work (a work line,
  or their work, panic or hide activity), dead; `heartEvents` or `friendship` switched off. Nothing of a half-told
  event is kept, and a telling isn't saved: after a restart they start again.
- **Which event**: the lowest heart level the player has reached and hasn't been told an event of. One event per
  level and player: of the events for that level whose conditions hold, the one with the most conditions (the story
  that fits them most closely), then the first by id. Someone at 5 hearts who was never near tells their 2-heart
  event first, then their 4-heart one.
- **Who**: named villagers in a village with a hall (the ones that keep friendships).
- **Events are data**, `data/<namespace>/heart_events/<id>.json`:

```json
{
  "hearts": 2,
  "when": {"born": true, "revived": false},
  "lines": ["heart_event.aliveworkplace.born_here.1", "heart_event.aliveworkplace.born_here.2",
            "heart_event.aliveworkplace.born_here.3", "heart_event.aliveworkplace.born_here.4"],
  "chronicle": "chronicle.aliveworkplace.heart_event.born_here",
  "story": "life_story.aliveworkplace.born_here"
}
```

  `hearts` is 1 to 10; `lines` are three to five lang keys; `chronicle` and `story` are the lang keys of the
  chronicle's line and the life story's. `when` is optional; every condition in it must hold:

| Condition | Holds when |
|---|---|
| `"born": true/false` | they were born in the village (their parents are on record) |
| `"hired": true/false` | they were hired from an inn |
| `"revived": true/false` | they were brought back from a grave (since this update) |
| `"jobs": [ids]` | their profession is one of these (a job family) |
| `"married": true/false`, `"courting": true/false` | they have a partner, married or not yet |
| `"widowed": true/false` | they lost a partner (since this update) and are with nobody now |
| `"parent": true/false` | a child of theirs lives in the village |
| `"trait": "cheerful"` | they have that trait |
| `"mood": ["hungry", "no_bed"]` | their mood has any of these reasons now (the names of `mood.aliveworkplace.reason.*`) |
| `"rank": "town"` | their village is at least a `hamlet`, `village`, `town` or `city` |

  Every lang key gets the same arguments: `%1$s` the villager, `%2$s` the player, `%3$s` the village, `%4$s` their
  mother, `%5$s` their father, `%6$s` their partner (or the one they lost), `%7$s` their children ("Ana, Cal and
  Finn"), `%8$s` the day they were hired ("day 12"). What isn't known reads "nobody" (the day: "a day nobody wrote
  down"). A data pack replaces one of our events by using its id, or adds its own; `"enabled": false` switches one
  off; a broken file (no `hearts`, two lines, an unknown condition, trait or rank) is skipped with a warning naming
  it, and the rest load.

## Switches

`heartEvents` (true, on once 1.5 is complete). Off: nobody starts telling and a telling under way stops; what was
told stays, and the life story page still opens. With `friendship` off there are no hearts, so nothing is told, and
the page shows no hearts.

## Saved data

- On the villager, in the `friendship` attachment (31.5), per player: `told`, the ids of the events told to the end
  (default: none).
- New attachments on villagers, all absent by default: `revived` (true once `Graves.revive` brings them back; absent
  on villagers revived before this update), `late_partner` (the id and name of the partner they lost, kept by
  `Couples.onDeath`; absent on those widowed before), `hired_day` (the day a traveller was hired at an inn; absent on
  those hired before, who say "a day nobody wrote down").
- The chronicle's new kind `FRIEND`.

## Items, blocks, jobs, commands

None.

## Decisions

- Villagers have no recorded gender, so the chronicle says "Dara told Jesse how they came to Thornholm" where the
  roadmap's example says "she" (31.7, lane c).
- One event per heart level and player; between events that fit, the one with the most conditions wins, then the
  first by id. So a pack's story for a widowed farmer is told instead of our general one, not as well (31.7, lane c).
- "Here before the hall" is everyone with no parents on record, not hired and not revived: there is no record of who
  stood where before a hall was placed, and villagers from before families were kept get this story too (31.7,
  lane c).
- Someone brought back from a grave tells that story whatever else is true of them (31.7, lane c).
- The traveller's story needs the day they were hired, which nothing kept: the new `hired_day` (31.7, lane c).
- A villager who can't reach the player tells it from where they stand after 5 seconds, rather than never (31.7,
  lane c).
- The page opens on a shift-click for villagers with a name only (the others keep no friendship and have nothing to
  show); the plain click keeps what it did (31.7, lane c).
- The personal request (31.9) gets its part of the page through `LifeStory.section`; until it exists the page shows
  no line for it (31.7, lane c).
- Small talk (`Chatter`) leaves a villager alone while they walk up and tell (31.7, lane c).

## Known limits

- `parent` and the children argument go by the names on the children's records, so two villagers with the same name
  in one village share their children on the page.
- The life story page shows up to 27 events (18 once another feature adds a section).
- A villager whose event file was removed by a data pack keeps the id in `told`, but the page shows no line for it,
  and they may tell another event for that heart level.

## Proof

`HeartEventGameTests` (9 tests): each of the four variants told for its facts line by line (through the inn's own
hiring and the grave's own revival) and nothing under 2 hearts; once per player and two players kept apart; walking
off and coming back; a villager with her AI on walking up and telling it in the level's own time; the chronicle's
line; the life story page through real clicks on the hall's list; every condition of the data format; broken files
skipped and named (one from a real data pack file); the switch off, going to work or dying halfway; a save and
reload halfway and afterwards, and old saves. Showcase scene `heart_event`.

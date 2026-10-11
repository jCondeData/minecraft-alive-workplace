# Reputation and titles

Each player has a standing in each village, earned by deeds and lost by harm, and with enough of it a title: Friend,
Hero, Lord. ROADMAP 31.11; builds on the quest engine (31.2), story arcs (31.4), friendship and gifts (31.5, 31.6),
personal requests (31.9) and lairs (32.3). Part of the 1.5 expansion: switched on when Milestone 31 is complete.

Roadmap items: 31.11

## What a player sees

Do things for a village and it remembers. A line above your hotbar says so each time ("+10 standing in Thornholm
(now 60)"), and at 50 the whole server reads, in gold and with a fanfare:

> Jesse is now a Friend of Thornholm!

At 300 you are a **Hero** and at 1000, once the village is a Town or bigger, a **Lord**. The village's chronicle
keeps a line for each ("Jesse became a Hero of the village", with a golden helmet).

Your title goes before what you say in chat: the one you hold in the village you are standing in, else your best
one anywhere.

> <Jesse> [Hero of Thornholm] anyone seen my horse?

Harm costs standing. Hit a villager and you lose a little, kill one and you lose a lot; drop under a threshold and
the title is gone. Only you are told ("You are no longer a Hero of Thornholm. They know you as a Friend now."), and
when you win it back only you are told again: the server hears of each title once.

Hover the name tag at the top of the Village Hall's screen for **your standing** there, your **honours** and the
three players the village thinks most of. `/workplace standing` lists your standing, title and honours in every
village, wherever you are.

**Honours** are names a story gives those who saw it through: Kingslayer, Healer of the village, Wayfinder,
Co-author. They are kept for good and listed with your standing.

## How it works

Standing is a number per player per village, kept by the village's hall. It can go below 0.

| Earned | Standing |
|---|---|
| Finishing one of the hall's daily quests | +10 |
| Finishing a bounty | +40 |
| A villager's own request done (every helper) | +25 |
| A story chapter finished (everyone who helped in it) | +50 |
| A story's ending | what its file says (150 in ours) |
| Breaking up a bandit camp or a lair (whoever brought its chief down) | +40 |
| Fighting in a raid: the third raider you kill in it | +20, once a raid |
| Coming to a festival (being in the village while it is on) | +5, once a festival day |
| A gift the villager doesn't dislike | +2, at most +10 a day per village |

| Lost | Standing |
|---|---|
| Hitting a villager | -10 |
| Killing a villager | -150 |
| Killing an iron golem in the village | -100 |
| Killing a guard | -200 |

A killing blow costs the kill, not the hit as well. A blow a shield or a guard's block stopped costs nothing. A
quest that belongs to a story counts through its chapter, not as a daily quest on top. Raiders are ours (monsters or
any raider culture's) and vanilla's pillagers while their raid is on; a straggler after the raid counts for nothing.

**Titles.** Stranger under 50, Friend from 50, Hero from 300, Lord from 1000 in a village that is at least a Town.
With 1000 in a smaller village you stay a Hero; the hall's round looks again and you become Lord the day it is a
Town. If the Town shrinks, its Lords are Heroes again until it grows back.

**Telling.** A title you reach for the first time in a village is told to every player on the server, with a sound,
and written in that village's chronicle as a TITLE line. Reaching it again after losing it, and losing one, are told
only to you. An honour is told to you and written in the chronicle.

**Chat.** The server's chat decorator puts "[Hero of Thornholm]" before the text of your message. If you stand in a
village where you hold a title, that one shows, even when you hold a better one elsewhere; anywhere else (or in a
village where you are a Stranger) your best shows: the higher title, then the more standing. A Stranger everywhere
has plain chat.

**For quest and arc files.** Two rewards: `{"type": "reputation", "points": 30, "who": "helpers"}` and
`{"type": "honour", "id": "wayfinder", "who": "helpers"}`. `who` is `finisher` (the default), `helpers` (everyone
credited with progress) or, in an arc's effects, `chapter_helpers` (those of its last chapter). Negative points take
standing away. An honour id is lower-case letters, digits and `_`; ours are named in the lang file
(`honour.aliveworkplace.kingslayer`, `honour.aliveworkplace.healer`, `honour.aliveworkplace.wayfinder`,
`honour.aliveworkplace.co_author`), and a pack names its own the same way (unnamed, it reads as its id).

## Switches

| Key | Default | What it does |
|---|---|---|
| `reputation` | on (with 1.5) | Standing and titles. Off: nothing is earned or lost, nobody holds a title, nothing is told, and chat, the hall's tooltip and the command show none of it. Saved standings stay and are there again when it's back on. |
| `titlesInChat` | on (with 1.5) | The title before a chat line. Off: chat lines are left alone; titles still count and are told. |

## Saved data

Per hall, in `aliveworkplace_stories`:

- `standings`: one entry per player with `player`, `name`, `points`, `title` (the one they hold), `announced` (the
  titles already told to the server), `honours`, `gift_day` and `gift_points` (the day's +10 from gifts),
  `festival_day` (the last festival day they came to), `raid` and `raid_kills` (the raid they last fought in).
  Absent (a save from before 1.5): nobody has a standing yet.
- `village_name`: the village's name as last seen with its hall loaded, so lists and the chat can name a village
  that isn't loaded. Absent: read from the hall the next time it is.

Breaking the hall drops its standings with the rest of the village's stories.

## Items, blocks, jobs, commands

- `/workplace standing`: your standing, title and honours in every village. Any player may run it.
- A new chronicle kind, TITLE, drawn as a golden helmet.
- No new items, blocks or jobs.

## Decisions

- **Anyone may become Lord, by deeds alone** (31.11, the default until the owner says): the hall's owner or not,
  1000 standing in a Town makes a Lord, and a village can have several. The owner's call is open (ROADMAP Notes).
- **The title goes before the message's text, after the name** (31.11, lane c): the spec asks for Fabric's message
  decorator, which can change what a player said but not the "<name>" in front of it. The line reads
  "<Jesse> [Hero of Thornholm] ...". Putting it before the name would need the server to re-send chat as its own
  messages, which loses the signature players' clients check.
- **Each title is told to the server once per player per village** (31.11, lane c): winning one back is told only
  to the player, so a player hovering around a threshold doesn't fill everyone's chat.
- **Standing can go below 0** (31.11, lane c): 150 for a killing with nothing to lose it from would cost nothing
  otherwise.
- **A gift earns standing unless the villager dislikes it** (31.11, lane c): a gift they hate is no favour to
  the village.
- **A festival counts once a festival day, for being in the village while it is on** (31.11, lane c), at any
  moment of it, not only at the feast.
- **The third raider killed in a raid pays, once per raid** (31.11: "fighting in a raid (3 raiders killed)").
- **A villager's own request pays each helper its `reputation` reward as its finisher** (31.11, lane c), since
  requests already pay every helper in turn.

## Known limits

- The title shows after the name in chat, not before it (see Decisions). Vanilla marks a decorated chat line as
  modified by the server, as it does for every mod that decorates chat.
- The Lord's Town rule is read when the hall's chunk is loaded; while it isn't, a Lord stays one and a Hero waits.
- A player who is away when a title or an honour comes is not told when they come back; the server was told and
  the chronicle has the line.
- Standings go with the hall when it is broken.
- A festival called on a server where `festivals` is off gives nothing, as there is none.
- The showcase scene has not been filmed locally; GitHub films it after the push.
- Not tested: a chat line typed in a real client (the tests call the server's chat decorator, which is what a typed
  line goes through), a vanilla pillager raid, and the sound.

## Proof

- `ReputationGameTests`: `ReputationGameTests.theThresholdsAndTheLordsTownRule`,
  `ReputationGameTests.aNewTitleIsToldOnceAndALostOneOnlyToThePlayer`,
  `ReputationGameTests.questsBountiesAndTheirRewards`, `ReputationGameTests.aPersonalRequestPaysEveryHelper`,
  `ReputationGameTests.anArcsChapterAndEndingPayItsHelpers`,
  `ReputationGameTests.breakingUpACampEarnsFortyForWhoKilledTheChief`,
  `ReputationGameTests.fightingInARaidEarnsTwentyAtTheThirdRaider`, `ReputationGameTests.aFestivalAndGifts`,
  `ReputationGameTests.harmToTheVillageCostsStanding`, `ReputationGameTests.theTitleGoesBeforeAChatLine`,
  `ReputationGameTests.theHallTooltipAndTheCommand`, `ReputationGameTests.aSaveAndReloadKeepsStandings` and
  `ReputationGameTests.theSwitchesOff`.
- Showcase scenes: `titles`

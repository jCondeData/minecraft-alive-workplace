# Personal requests

A villager who is your friend may ask you for help with something of their own: a level before the festival, a home,
a taste of home, the tools of their trade. ROADMAP 31.9; builds on friendship (31.5), the quest engine (31.2) and the
quest journal (31.3). Part of the 1.5 expansion: switched on when Milestone 31 is complete.

Roadmap items: 31.9

## What a player sees

Now and then a villager you have **3 hearts** or more with walks up to you and asks for something, over their head
and in your chat in grey:

> Dara: Jesse, the festival's nearly on us, and I'd dearly love to stand there having made Journeyman. Trade with me
> a while, or spare me a Bottle o' Enchanting. Would you?

The next chat line says what they want, what it earns and has two buttons:

> Dara wants to make Journeyman before the next festival. For 6 emeralds and their friendship. **[I'll help]** **[Not now]**

**[Not now]** costs nothing; they say "Another time, then" and may ask again another day. **[I'll help]** opens the
request. From then on it shows:

- in the quest journal at the Village Hall, on the new **Personal** tab (a poppy), with who asked, its objective and
  progress, the reward, the days left ("Before the next festival (4 days)") and who is helping;
- on their **life-story page** (shift-click them on the hall's list), as a line of its own above their story;
- on the hall's **tooltip** for them: "Dara wants to make Journeyman before the next festival".

Other players with 3 hearts with that villager can join from the Personal tab (a click), and anyone who has the
hearts and hands something in is a helper too. When it's done, **every helper** gets the reward and a heart and a
half of friendship, the villager thanks them, and the chronicle gets a line: "Jesse helped Dara: Next level before
the festival".

Miss the deadline and every helper loses 30 friendship with them, they are glum for a day ("Let down: nobody helped
in time" in their mood) and they ask nobody for anything for 3 days.

The six requests:

| Request | Who asks | Done when | Deadline | Pays |
|---|---|---|---|---|
| **Next level before the festival** | a worker below Master | they reach their next level (trading gives experience as in vanilla; so does a Bottle o' Enchanting as a Gift) | before the next festival | 6 emeralds |
| **A home of my own** | someone with no home, or a tier I one | they sleep in a bed of their own in a finished home of tier II or better | 12 days | 8 emeralds |
| **A taste of home** | anyone, by their villager type | you bring it: plains a pumpkin pie, desert a rabbit stew, savanna 4 cooked mutton, taiga 16 sweet berries, snowy 4 baked potatoes, swamp a mushroom stew, jungle 8 cookies | 3 days | 4 emeralds |
| **The tools of my trade** | eight jobs | you bring it: Miner a diamond pickaxe, Lumberjack a diamond axe, Farmer a diamond hoe, Fisherman a fishing rod with Luck of the Sea, Guard a diamond sword, Cartographer a spyglass, Netherworker a potion of Fire Resistance, Sifter a diamond shovel | 5 days | 5 emeralds |
| **Help me train** (Cobblemon) | a Trainer | you beat them in battle on three different days | 7 days | friendship |
| **A Pokémon friend** (Cobblemon) | a worker whose job Pokémon help with, who has none yet | at dawn, a Pokémon of a type that helps their job is pastured within 16 blocks of their workstation | 5 days | friendship |

Things to bring are handed in from the Personal tab, as on the Village tab (a click hands in what you carry). Tools
go into the chest by the villager's workstation; with no chest there, and for a taste of home when they have no
workstation, the villager takes it themselves.

## How it works

**Who asks, and when.** In the hall's round (every 30 seconds), the first round of each day rolls once: a 25%
chance that someone in the village asks today. On a hit, the village waits for the first moment that day when a
villager who may ask has a friend within 24 blocks, and picks one of those villagers at random. A villager may ask
when they are a named adult of the village, awake, not in the middle of telling a heart event, have no request open
or asked, and weren't let down in the last 3 days. Their friend is the player within 24 blocks with the most
friendship, at least 3 hearts (300 points). If the day ends with nobody near, nobody asks that day.

**What they ask.** Of the request files (below) whose conditions hold for that villager and that player and whose
objective can be asked of that villager, one is drawn by weight (the highest `priority` first). A villager with
nothing to ask (a Master in a fine home, of a job with no tool on the list...) is skipped and another is tried.

**The asking.** They walk up to the player; within 3.5 blocks (or after 10 seconds, from where they stand) they say
their line over their head and the chat gets it with the two buttons, which run `/workplace quest accept <id>` and
`/workplace quest decline <id>`. Only the player who was asked can answer. An offer nobody answers lapses when the
day ends, when the player leaves or when the game restarts, at no cost. While a villager has something to ask, they
don't chat or start a heart event.

**Helping.** An accepted request is an open quest of the village with the giver `villager`. The one who accepted is
its first helper. Another player with 3 hearts with that villager joins by clicking it on the Personal tab, by
[I'll help], or by moving it on (handing in, winning the battle). A player with fewer hearts can't.

**Objectives.** `bring` hand-ins work as for the hall's quests. The objectives about the villager are looked at in
the hall's round: their level (`level_up`), whether they sleep in a home of their own of the right tier (`home`),
and, on the first round of each day ("at dawn"), whether a partner Pokémon is pastured by their workstation
(`partner_pokemon`). A win against the Trainer (`beat_giver`) counts when the battle ends, once a day.

**Done.** Every helper in the game gets the reward (each their own emeralds) and 150 friendship (a favour, once a
day per villager); a helper who is away gets the friendship. The villager says their thanks, and the chronicle gets
a line of the Friend kind.

**Deadlines.** A number of days, or "before the next festival": the morning of the village's next festival
(`Festivals.nextDay`), and the one after when that is under 2 days away, so there is always time. With festivals
switched off it is the file's number of days. A request still open when it's due is missed: 30 friendship off every
helper, a mood reason worth 10 points for one day, and no asking up to and including the third day after.

**Request files.** `data/<namespace>/quests/personal/<id>.json`, a quest file with `"giver": "villager"`:

    {
      "giver": "villager",
      "name": {"translate": "request.aliveworkplace.level_up.title"},
      "text": "request.aliveworkplace.level_up",
      "deadline": "festival",
      "days": 6,
      "weight": 10,
      "conditions": [{"type": "hearts_at_least", "hearts": 3}, {"type": "level_below", "level": 5}],
      "objectives": [{"type": "level_up"}],
      "rewards": [{"type": "money", "emeralds": 6, "rank_factor": false}]
    }

`text` is the lang key their lines hang off: `.ask`, `.wants` and `.thanks`, each given the villager (`%1$s`), the
player (`%2$s`) and what is asked for (`%3$s`). `deadline` is `festival` or `days` (the default). The conditions on
the villager and the player:

| Condition | Holds when |
|---|---|
| `hearts_at_least` (`hearts`) | the player has that many hearts with the villager |
| `job` (`jobs`) | their profession is one of the list |
| `job_family` (`family`) | their job is of that job family (`building`, `mining`, `land`...) |
| `level_below` (`level`) | they have a job and their level is under it (5 is Master) |
| `villager_type` (`types`) | their villager type is one of the list (`minecraft:plains`, `minecraft:snow`...) |
| `home_tier_below` (`tier`) | their home's tier is under it (0 with no bed, or a bed in no finished building) |
| `no_bed` | they have no bed |

In a quest file with another giver none of these ever holds, so a mistake never posts a quest on the hall's board.

The objectives: `level_up`; `home` (`tier`, 2); `beat_giver` (`days`); `partner_pokemon` (`radius`, 16); and `bring`
with `"by": "villager_type"` or `"by": "job"` and `options`, a map from villager type or profession to an item and a
count. An item may name what it must carry: `minecraft:fishing_rod[enchantment=minecraft:luck_of_the_sea]`,
`minecraft:potion[effect=minecraft:fire_resistance]` (a plain, long or strong potion; not a splash potion).

## Switches

| Key | Default | What it does |
|---|---|---|
| `personalRequests` | on (with 1.5) | Villagers ask their friends for help. Off: nobody asks and unanswered offers lapse; requests already accepted stay and can be finished or missed. |
| `friendship` | on (with 1.5) | Requests go by hearts, so with friendship off nobody asks. |

## Saved data

- On an open quest (`aliveworkplace_stories`): `villager` (who asked; absent: not a personal request), `text` (the
  lang key of their lines; absent: none), `festival` (due before the next festival; absent: false) and `mark` (the
  last day a `beat_giver` win counted; absent: never). A quest saved before 1.5 has none and reads as before.
- Per hall, in the same file: `request_day` (the day of the last morning roll; absent: none made),
  `request_pending` (the roll hit and nobody has asked yet; absent: false) and `request_dawn` (the day of the last
  dawn look; absent: none).
- On the villager, `request_let_down`: `quiet_day` (the last day they ask nobody) and `glum_until` (the game time
  their glum mood ends). Absent: nothing was ever missed.
- An unanswered offer is not saved.

## Items, blocks, jobs, commands

- `/workplace quest accept <id>` and `/workplace quest decline <id>`: what [I'll help] and [Not now] run. Any
  player may run them; they only answer a request that was asked of that player (accept also joins an open one).
- The request files `aliveworkplace:personal/level_up`, `aliveworkplace:personal/home`,
  `aliveworkplace:personal/taste_of_home`, `aliveworkplace:personal/tools_of_my_trade`,
  `aliveworkplace:personal/help_me_train` and `aliveworkplace:personal/pokemon_friend`.
- No new items, blocks or jobs.

## Decisions

- **The roll is once a morning, the pick waits for a friend to be near** (31.9, lane c): the spec says "each
  morning one such villager in the village is picked, a 25% chance", and a villager only qualifies with a player
  nearby. Rolling once and then waiting through the day means a player who comes home at noon can still be asked,
  and the chance stays one in four a day.
- **"Before the next festival" is never under 2 days** (31.9, lane c): a festival tomorrow would make the request
  impossible, so the deadline is then the festival after.
- **Every helper gets the emeralds**, not only the one who finished it (31.9: "everyone who helped gets the
  friendship", and the Done-when's "pays every helper"); the level and home requests have no finisher at all.
- **A home request is asked by anyone under tier II** (31.9, lane c): the spec's "no bed of their own, or a tier I
  home" is written as `home_tier_below` 2, which also covers a bed in a house no builder put up.
- **A hand-in with no chest by the workstation goes into the villager's own hands** (31.9, lane c), not the hall's
  store: it was asked for by them.
- **The two Pokémon requests are plain quest files with the `cobblemon` condition** (31.9 and the compat rules):
  the battle hook is the trainers' own, the pasture count goes through the `PokemonPartners` extension point, and
  without Cobblemon neither is offered.
- **Offers aren't saved** (31.9, lane c), like a heart event half told: after a restart they may simply ask again.

## Known limits

- One request per villager at a time, and one villager per village asks per day at most.
- A villager whose chunk isn't loaded can't finish a level or home request until it is; the deadline still runs.
- If the villager dies or leaves, the request stays in the journal until its deadline and then comes down with no
  penalty.
- If the villager isn't loaded when a request is finished (a hand-in from the hall while they're away), the helpers
  get the reward but not the friendship, and the item goes into the hall's store.
- A request by a villager who was let down can still be joined and finished; only their asking pauses.
- "At dawn" is the hall's first round of the day, which is at dawn only when the village is loaded then.
- The same request can be asked again on a later day.
- The showcase scene has not been filmed locally; GitHub films it after the push.
- Not tested: a real battle played to the end for Help me train (the test server can't play one; the test calls the
  trainers' battle-over hook), and the asking on a server with players in several dimensions.

## Proof

- `PersonalRequestGameTests`: `PersonalRequestGameTests.aThreeHeartVillagerAsksAndATwoHeartOneDoesnt`,
  `PersonalRequestGameTests.theyWalkUpAndAskWithBothButtons`, `PersonalRequestGameTests.acceptAndDecline`,
  `PersonalRequestGameTests.theNextLevelPaysEveryHelper`, `PersonalRequestGameTests.aHomeOfTheirOwn`,
  `PersonalRequestGameTests.aTasteOfHomeByVillagerType`, `PersonalRequestGameTests.theToolsOfTheirTradeByJob`,
  `PersonalRequestGameTests.thePokemonRequestsNeedCobblemon`,
  `PersonalRequestGameTests.aMissedDeadlineCostsThirtyAndThreeQuietDays`,
  `PersonalRequestGameTests.beforeTheNextFestival`, `PersonalRequestGameTests.everyConditionReadsTheGiver`,
  `PersonalRequestGameTests.aSaveAndReloadKeepsARequest` and `PersonalRequestGameTests.theSwitchOff`.
- With Cobblemon: `PersonalRequestCompatTests.helpMeTrainTakesWinsOnThreeDays` and
  `PersonalRequestCompatTests.aPokemonFriendIsJudgedAtDawn`.
- Showcase scenes: `personal_request`

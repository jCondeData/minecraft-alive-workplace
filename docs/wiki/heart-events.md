# Heart events and life stories

ROADMAP 31.7 and 31.8. Builds on friendship (31.5) and gifts (31.6). Part of the 1.5 expansion: switched on when
Milestone 31 is complete.

Roadmap items: 31.7, 31.8

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

There are five events, each in several variants; a villager tells the variant that fits them.

**2 hearts, Where I come from:**

| Event | Told by | It says |
|---|---|---|
| `born_here` | villagers born in the village | where they were born, and names both parents |
| `traveller` | travellers hired at an inn | the road, the inn, and the day they were hired |
| `before_hall` | everyone else | the village before it had a hall and a name |
| `back_from_grave` | anyone brought back from a grave | what they remember of it |

**4 hearts, My work**, one per job family (the families are the gift tastes' twelve, [Gifts](gifts.md)):

| Event | Told by | It says |
|---|---|---|
| `work_building` | Builder, Carpenter, Mason, Tinkerer, Leatherworker | the first wall they raised, and the one that fell |
| `work_mining` | Miner, Armorer, Toolsmith, Weaponsmith, Sifter, Netherworker | the day their lamp went out underground |
| `work_land` | Lumberjack, Orchard Keeper, Farmer, Florist, Beekeeper, Composter | the year the harvest failed |
| `work_animals` | Shepherd, Butcher, Rancher | the foal they raised |
| `work_water` | Fisherman | the fish that got away |
| `work_kitchen` | Chef | the dish that made them a cook |
| `work_learning` | Scholar, Teacher, Librarian, Cartographer | the book that changed their mind |
| `work_healing` | Nurse, Cleric, Undertaker | the patient they couldn't save |
| `work_arms` | Guard, Fletcher | their first raid |
| `work_trade` | Shopkeeper, Innkeeper, Ferryman, Postman, Porter | the worst bargain of their life |
| `work_music` | Bard | a song nobody sings any more |
| `work_pokemon` | Trainer, Trainer Leader, Move Tutor, Ball Smith, Pokémon Trader, Fossil Scientist | their first partner Pokémon |
| `work_none` | the jobless, nitwits, and jobs of no family | they haven't found their place, and ask what you think |

Someone without a trade names the free workstation nearest the hall and the job it gives ("Nobody works at the Loom
near the hall. Could you see me as the village's next Shepherd?"); with none free they say every bench is taken, and
a nitwit says no bench would have them.

**6 hearts, What keeps me up at night**, from their life now. The first that fits, in this order:

| Event | Told when | It says |
|---|---|---|
| `night_hunger` | they are hungry, or the village's store holds under 16 meals | they lie awake counting meals |
| `night_no_bed` | they have no bed of their own | they sleep where there's room |
| `night_raids` | the village was raided in the last 5 days, or a bandit camp preys on it | they lie awake listening |
| `night_illness` | they are ill, or their partner, a parent or a child in the village is | the sickness in their house |
| `night_lonely` | they have no partner, and no other villager is within 6 blocks | how quiet their evenings are |
| `night_you` | none of these | they worry about you and your travels |

**8 hearts, The people I love.** The first that fits, in this order:

| Event | Told by | It says |
|---|---|---|
| `love_married` | the married | their partner by name, and the wedding day ("We were married on day 12") |
| `love_courting` | those courting | who they have their eye on |
| `love_parent` | those with a child in the village | their children by name |
| `love_mourning` | the widowed | the partner they lost, by name |
| `love_parents` | those alone who were born in the village | their mother and father by name |
| `love_village` | everyone else | "the village is my family" |

**10 hearts, What I dream of.** The first that fits, in this order:

| Event | Told by | It says |
|---|---|---|
| `dream_content` | a happy, married Master | "I have everything I wanted" |
| `dream_master` | anyone with a trade below Master | to be a Master of their trade |
| `dream_nether` | a Master Miner, Armorer, Toolsmith, Weaponsmith or Sifter | to see the Nether |
| `dream_sea` | a Master Fisherman, Ferryman, Cartographer, Postman or Porter | to see the sea |
| `dream_house` | anyone else whose home is below tier III (or who has none) | a finer house |
| `dream_city` | anyone else whose village is below a City | the village a City |
| `dream_festival` | everyone else | a festival in their honour |

When the 10-heart event is told to the end they give you a **keepsake**, once: an item named after them, with two
lines of lore (what it was to them, and "A keepsake from Dara of Thornholm"), and a line in your chat ("Dara gave you
a keepsake: Dara's Lucky Pick"). With a full inventory it lands at your feet. Each villager gives each player theirs
once.

| Job family | Keepsake | Item |
|---|---|---|
| building and crafting | "<Name>'s Trowel" | iron shovel, Efficiency II |
| mining and smithing | "<Name>'s Lucky Pick" | iron pickaxe, Fortune I |
| the land | "<Name>'s Grandmother's Seeds" | 4 torchflower seeds |
| animals and water | "<Name>'s Old Rod" | fishing rod, Luck of the Sea II |
| the kitchen | "<Name>'s Secret Recipe" | a signed book with their pumpkin pie recipe, and 2 pumpkin pies |
| learning | "<Name>'s Annotated Atlas" | enchanted book, Mending |
| healing | "<Name>'s Remedy" | golden apple |
| arms | "<Name>'s Old Shield" | shield, Unbreaking II |
| trade and travel | "<Name>'s Spyglass" | spyglass |
| music | "<Name>'s Favourite Record" | music disc Otherside |
| Pokémon | "<Name>'s First Poké Ball" | Cobblemon's Premier Ball (a snowball without Cobblemon) |
| no trade yet | "<Name>'s Pressed Flower" | cornflower |

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
  that fits them most closely), then the one with the highest `weight`, then the first by id. Someone at 5 hearts who
  was never near tells their 2-heart event first, then their 4-heart one. The facts are read when the telling
  starts: someone hungry today tells of hunger, even if the store fills tomorrow.
- **The keepsake** is given when an event of 10 hearts is told to the end, if that villager hasn't given that player
  one yet (`Keepsakes`). The item goes by their job family at that moment (`JobFamilies`, fixed in code; a job no
  family lists counts as "no trade yet"). The Premier Ball is looked up by its id, `cobblemon:premier_ball`, so
  Cobblemon stays optional.
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
  chronicle's line and the life story's; `weight` (optional, 0) puts one event before another that asks as many
  conditions. `when` is optional; every condition in it must hold:

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
| `"rank_below": "city"` | their village is below that rank |
| `"trade": true/false` | they have a job (not jobless, not a nitwit) |
| `"level": 5`, `"level_below": 5` | their job level is at least, or below, this (5 is a Master) |
| `"home_tier_below": 3` | their home's tier is below this (no bed, or a bed in no finished house, is tier 0) |
| `"hungry": true/false` | they are hungry, or the village's store holds under 16 meals |
| `"no_bed": true/false` | they have no bed of their own |
| `"raided": true/false` | a raid in the last 5 days, or a bandit camp preying on the village |
| `"ill": true/false` | they are ill, or their partner, a parent or a child of theirs in the village is |
| `"lonely": true/false` | no partner, and no other villager within 6 blocks |
| `"happy": true/false` | their mood reads happy (75 or more) |

  Every lang key gets the same arguments: `%1$s` the villager, `%2$s` the player, `%3$s` the village, `%4$s` their
  mother, `%5$s` their father, `%6$s` their partner (or the one they lost), `%7$s` their children ("Ana, Cal and
  Finn"), `%8$s` the day they were hired ("day 12"), `%9$s` their wedding day ("day 12"), `%10$s` what someone
  without a trade says of the village's free workstations (a whole sentence). What isn't known reads "nobody" (the
  day: "a day nobody wrote down"). A data pack replaces one of our events by using its id, or adds its own;
  `"enabled": false` switches one off; a broken file (no `hearts`, two lines, an unknown condition, trait or rank, a
  condition that isn't `true` or `false`) is skipped with a warning naming it, and the rest load.

## Switches

`heartEvents` (true, on once 1.5 is complete). Off: nobody starts telling and a telling under way stops; what was
told stays, and the life story page still opens. A keepsake not yet given waits: it comes with the 10-heart event
once the switch is on again. With `friendship` off there are no hearts, so nothing is told, and
the page shows no hearts.

## Saved data

- On the villager, in the `friendship` attachment (31.5), per player: `told`, the ids of the events told to the end
  (default: none), and `keepsake`, whether they gave that player their keepsake (default: not given, so friendships
  saved before 31.8 read as before).
- New attachments on villagers, all absent by default: `revived` (true once `Graves.revive` brings them back; absent
  on villagers revived before this update), `late_partner` (the id and name of the partner they lost, kept by
  `Couples.onDeath`; absent on those widowed before), `hired_day` (the day a traveller was hired at an inn; absent on
  those hired before, who say "a day nobody wrote down").
- The chronicle's new kind `FRIEND`.

## Items, blocks, jobs, commands

No new items: the twelve keepsakes are vanilla items (and Cobblemon's Premier Ball) with a name and lore, listed
under "What a player sees". They work as the item they are: the Trowel digs, the Remedy can be eaten, the Record
plays.

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
- "Animals and water" has two work stories, as the roadmap says "the foal they raised, or the fish that got away":
  the Fisherman tells of the fish, the Shepherd, Butcher and Rancher of the foal. All four give the Old Rod (31.8,
  lane c).
- The roadmap doesn't say which worry, love or dream is told when several fit. They go in the order of the tables
  above: for worries and loves the roadmap's own order; for dreams, "everything I wanted" first, then Master for
  anyone below it, then the Nether or the sea for Masters of those jobs, then the house, the City, and the festival
  for whoever has all of that. So the festival is the dream of a Master in a grand house in a City (31.8, lane c).
- "To see the Nether or the sea (by job)": the Nether for those whose trade comes out of the ground (not the
  Netherworker, who has seen it), the sea for those of the water and the roads. Other Masters dream of the house, the
  City or the festival (31.8, lane c).
- "To be a Master" is for villagers with a trade; the jobless and nitwits dream of a house, a City or a festival
  (31.8, lane c).
- The wedding day comes from the couple's record (`Couples.Partner.since`), not the chronicle, which keeps only 100
  lines (design note 13 of M31; 31.8, lane c).
- "The village's nearest free workstation" is the one nearest the hall, as `VillageHalls.freeStations` orders them
  (31.8, lane c).
- Without Cobblemon the "First Poké Ball" is a snowball, the nearest vanilla thing to a white ball; with Cobblemon
  it is the Premier Ball, found by id (31.8, lane c).
- A keepsake dropped for a full inventory is dropped as the player's own item, not as a block drop, so vanilla's
  game rule that switches block drops off can't lose it (31.8, lane c).
- A condition written as anything but `true` or `false` (say `"hungry": "yes"`) makes the file broken; before it
  silently read as `false` (31.8, lane c).

## Known limits

- `parent` and the children argument go by the names on the children's records, so two villagers with the same name
  in one village share their children on the page.
- The life story page shows up to 27 events (18 once another feature adds a section).
- A villager whose event file was removed by a data pack keeps the id in `told`, but the page shows no line for it,
  and they may tell another event for that heart level.
- The life story's lines are written with today's facts: "Loves Odo, married on day 12" names whoever is their
  partner (or late partner) now.
- `lonely` looks at the moment the telling starts: someone single who happens to stand alone tells of loneliness.
- A village with no store (no chest by a smoker or a Storehouse) holds 0 meals, so its villagers' worry is hunger.
- The keepsake goes by the villager's job at 10 hearts, not the job they had when they told of their work.

## Proof

`HeartEventGameTests` (9 tests): each of the four variants told for its facts line by line (through the inn's own
hiring and the grave's own revival) and nothing under 2 hearts; once per player and two players kept apart; walking
off and coming back; a villager with her AI on walking up and telling it in the level's own time; the chronicle's
line; the life story page through real clicks on the hall's list; every condition of the data format; broken files
skipped and named (one from a real data pack file); the switch off, going to work or dying halfway; a save and
reload halfway and afterwards, and old saves. Showcase scene `heart_event` (with the 10-heart keepsake).

`HeartEventSetGameTests` (7 tests, 31.8): every job of every family tells its family's work story at 4 hearts and
nothing at 3, the jobless about the nearest free workstation; each worry for its facts (hunger both ways, a raid 5
days ago but not 6, illness of self, child, parent and partner) and which comes first; each love for its facts, with
names and the wedding day; each dream for its facts (level, job, home tier, rank, mood); each family's keepsake with
its item, name, enchantment and lore, and the recipe book's pages; once per player through a save, a data pack's new
10-heart event, a full inventory and the switch off; the shipped set, every sentence's text, the families against the
taste files, and the new conditions. `KeepsakeCompatTests` (with Cobblemon): the Premier Ball. Showcase scene
`heart_events_work` (a villager of each family beginning their work story).

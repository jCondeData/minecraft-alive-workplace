# Legends

Rare named villagers with powers, who come to a village that has earned them. Also the Gifted: about one villager in
thirty with a rare gift. Part of 1.3 (Legends): switched on in 1.0 with every other expansion.

Roadmap items: 29.2, 29.3, 29.4, 29.5, 29.6, 29.7, 29.8, 29.9, 29.10, 29.12, 29.13, 29.14, 29.15, 29.16, 29.17, 29.18,
29.19, 29.20, 29.21, 29.22, 29.23, 33.23, B136, B345

## What a player sees

Now and then a village gains a Legend: one named villager, a Master of their trade, wearing their own outfit over
their trade's, their name in gold over their head and a soft sparkle every few seconds. Their coming is announced.

Nothing about them is secret. The hall's Legends page (the nether star in the page row) lists the village's own
Legends first, then every other Legend as a card: its rarity, how it comes, each thing the village must have with how
far it has got ("Kinds of meal in the store: 5 of 8"), the luxury it likes and its powers.
Each thing has a tick when it is met (exactly met counts) and a cross when it isn't, and a card with every tick says
"This village has all they need: they may come". A Legendary one already living in another village says who and
where ("Wren lives in Thornholm"); a Rare one settled elsewhere can still come here, so its card doesn't. The page's
header and every Mythic card show the Mythic line ("Mythic Legends: 0 of 1, as a Town").

On the hall's list of villagers the village's Legends come first, name and title in gold, with their rarity, each
power, and each need with a tick, or a cross once it has gone unmet a day, plus "On strike since day N" in red. When a
Legend lacks only one thing, "What next?" says so ("The Grand Chef could come: Kinds of meal in the store: 7 of 8");
not when two things are missing, nor once nothing is. Villagers chat about the Legend living with them, a Legend
guest, and a Legend on strike, and a Legend talks about themself.

| Legend | Rarity | Comes | The village must have | Brings |
|---|---|---|---|---|
| Master Architect | Legendary | visits the inn | Town rank, buildings in 3 styles | builders nearby twice as fast; a grand rebuild of a house |
| Pathfinder | Rare | found at a ruined portal; or born | a Cartographer who is an Expert (level 4) or higher | far expeditions; leads the player on one |
| Old Sage | Rare | found in a hermit's hut; or born | 5 research levels | riddles, then a research tree of his own |
| Golem Smith | Legendary | a strange mood (a Master Tinkerer) | 4 iron golems | a forge that makes golems which work |
| Seer | Rare | the Chapel at midnight under a full moon; or born | a finished Chapel | foretells raids; blesses weddings |
| Merchant Prince | Legendary | found by a shipwreck | 500 emeralds through the treasury, 3 caravan routes | a bank in the hall, trade fairs, better caravan pay; with 1.7, every price in caravan range on the board, goods sold where they pay most, half the spread |
| Grand Chef | Rare | a strange mood (a Master Chef); the inn; or born | 8 kinds of meal in the store | banquets; a faster kitchen |
| Bard Laureate | Rare | a festival 30 villagers come to; or born | (the crowd) | the village's anthem; work songs |
| Beastmaster | Rare | found caged at a pillager outpost; or born | 10 animals by the herders | war dogs for the guards; bred horses |
| Pokémon Professor (Cobblemon) | Legendary | visits the inn | 25 pastured Pokémon of 10 types | hints about your Pokémon; a village Pokédex |
| Pokémon Ranger (Cobblemon) | Rare | visits the hall | an Alpha Pokémon within 96 blocks | calms Alphas; befriends wild Pokémon |
| Founder | Mythic | a strange mood, at the first rise to City | City rank for the first time | a statue; a Founder's Wagon every week; with 1.7, colonies from Town rank, four settlers, half the wait, his blessing and statue in each colony |

## How it works

**Legends are data.** One file each in `data/aliveworkplace/legends/`: rarity, title and lore, what the village must
have, the ways they come, their powers. A file with an unknown condition or power fails as a whole (and is logged), so
a typo never makes a Legend come for free. The two Pokémon Legends load only with Cobblemon.

**Rarity (29.3).** A Rare Legend comes once to each village; a Legendary one once to each world; a Mythic one as
many as the villages' ranks allow (`mythicLegendCap`: by default a Town holds 1 and a City 2). A full one says so
("A Town can hold 1 Mythic Legend, and this one has 1."). The server keeps one
record of every Legend settled anywhere. A Legend holds their slot alive, as a zombie villager, or dead in a grave;
dead with no grave they fall 7 days later and the slot is free again.

**Four ways to come.**
- *Visit* (29.8): once a day a village with no guest may get one, at the inn, the market, a festival, the Chapel or
  the hall, one time in four by default. A guest stays up to 3 days and leaves on the evening of the third, once no
  player is within 24 blocks (a guest whose needs are all met that evening settles instead). The same Legend doesn't
  visit again until 7 days after the day they came.
- *Found* (29.9): at a ruined portal, a pillager outpost or a shipwreck, for a player who owns (or is a friend of
  the owner of) a qualifying hall within 1500 blocks. A small camp is set down with the Legend in it; each structure
  is used once.
- *Born* (29.7): when the child of two schooled Masters grows up, one time in 20 they are a Rare Legend of a
  parent's trade; otherwise one time in 4 they are Gifted.
- *Strange mood* (29.10): once a day in a happy village a Master may be seized (one time in 8). Only a Master:
  a Novice to an Expert never is. Never during a raid or a festival, and never two at once in one village. They claim
  their workstation ("Taken by a strange mood" in purple over their head) and ask for three rare materials, one of
  each, in a chest by it within 3 days; the village's players are told and the materials go on the requests board.
  All three in on the last day still counts; two of the three never does, however many of each. Bring them and they
  make a Masterwork (a made-up name, lore naming the maker, the village, the day and the materials) and become the
  Legend. It goes to the player who brought the most, or into the chest when nobody did or their pack is full. A
  Masterwork in an item frame in the village is worth 3 beauty; in a chest, none. Fail and they sulk for 7 days
  ("Sulking": 30 less mood, half pace), a material that comes late makes nothing, and the village has no strange
  mood for 10 days.

**Needs and strikes (29.5).** A settled Legend needs a home of their own (their bed in a finished tier III
building), their luxury once every 7 days, and a happy village (average mood 60 or more). A need unmet 2 days
running starts a strike, but not in the first 3 days. On strike their powers stop and they picket by the hall with
what they want in red over their head. The day every need is met they go back to work. Legends never leave.
Their luxury is an item tag, `aliveworkplace:luxury/<kind>` (B136): wine is a honey bottle, Cider, Berry Wine or
Vintage Wine; jewels an amethyst shard, an emerald, an Amethyst Ring, an Emerald Brooch or a Gold Circlet; books a
book, an enchanted book, the Village Gazette or an Illuminated Book; clothes leather armor, Work Clothes, Fine Clothes or Noble
Robes. The vanilla stand-ins stay, so a village without 1.8's luxury trades still keeps its Legends.

**The Gifted (29.6, 29.7).** One villager in `giftedChance` (30) has a gift, decided by who they are, so every
existing villager already has theirs and nothing is migrated: Prodigy (learns three times as fast), Iron Will (never
panics), Silver Tongue (trades 20% cheaper), Night Owl (works at night, builders and miners too, once they have a trade), Lucky, Hardy (never ill, twice the health),
Beloved (neighbors are happier), Born Leader (workers of their trade nearby 10% faster).

- Prodigy with the Clever trait still learns three times as fast, not more.
- Silver Tongue's 20% shows on the trade screen with any player (on top of Hero of the Village), at least 1 emerald
  off and never below 1: a 10-emerald trade costs 8, a 2-emerald one 1, a 1-emerald one stays 1. It comes back after a
  restock.
- Night Owl: grown workers only (not the jobless, nitwits or children), builders and miners included; they work from
  dusk to dawn and sleep from mid-morning to mid-afternoon. The schedule is set again after a save and reload.
- Hardy: exactly twice a villager's health, never ill. Beloved: +5 mood, "a beloved neighbor, <name>", to everyone
  whose bed is within 16 blocks of theirs (17 is too far). Born Leader: workers of their own trade within 16 blocks
  work 10% faster, held under `maxWorkPace` and the Legends' own cap.
- Born Gifted (29.7): only a child of two schooled Masters can grow up a Legend (1 in 20) or else Gifted (1 in 4),
  rolled once when they grow up; the chronicle says "Wren, child of Dara and Tom, has grown up Gifted: Prodigy". The
  inn's travelers are Gifted 1 in 10, shown on the hire screen ("Gifted: twice the usual price") at twice the price.
  With `giftedChance` 0 nobody grows up or is hired Gifted.

## Switches

| Key | Default | What it does |
|---|---|---|
| `legends` | on from 1.0 | Off: none come; settled Legends stay as ordinary Masters and come back when it is on again |
| `legendNeeds` | on from 1.0 | Off: no needs and no strikes |
| `legendSites` | on from 1.0 | Off: no Legends found at ruins, outposts or shipwrecks |
| `strangeMoods` | on from 1.0 | Off: no strange moods, and a mood already on is called off |
| `giftedChance` | 30 (0 to 1000) | One villager in this many is Gifted; 0: nobody is, and nothing is erased |
| `mythicLegendCap` | 0, 0, 1, 2 | Mythic Legends by rank (Hamlet, Village, Town, City); in the file only |

## Saved data

- With the overworld: `aliveworkplace_legends`, the server's record (each Legend's villager, dimension, hall, rarity,
  the day they settled and the day they fell) and the structures already used for a found Legend.
- On the Legend: the attachment `legend` (which Legend, guest or settled, their hall, the day they came, how many
  days each need has gone unmet, the day a strike began: -1 for none). Every field has a default.
- On a Gifted villager: the attachment `gifted` only when it overrides the roll (the born, the inn's travelers).
- On a Master in a strange mood: the attachment `strange_mood`.
- On the hall: the guest in the village, `treasuryTotal` (starts at 0 in an old hall), the Founder's fields.
- On a colony's order (33.23): `blessed`, whether the Founder lived in the mother village when it was given (false in
  an order saved before).

## Items, blocks, jobs, commands

- Job: Legend (`aliveworkplace:legend`, `entity.minecraft.villager.legend`), for a Legend without a trade of their
  own (the Old Sage, the Seer, the Merchant Prince, the Founder, the two Pokémon Legends): no workstation, always a
  Master. The others keep their trade (the Master Architect is a Builder, the Grand Chef a Chef).
- Item: Founder's Wagon (`aliveworkplace:founders_wagon`), the Founder's weekly gift.
- Legend ids: `aliveworkplace:master_architect`, `aliveworkplace:pathfinder`, `aliveworkplace:old_sage`,
  `aliveworkplace:golem_smith`, `aliveworkplace:seer`, `aliveworkplace:merchant_prince`, `aliveworkplace:grand_chef`,
  `aliveworkplace:bard_laureate`, `aliveworkplace:beastmaster`, `aliveworkplace:pokemon_professor`,
  `aliveworkplace:pokemon_ranger`, `aliveworkplace:founder`.
- Gift ids: `aliveworkplace:prodigy`, `aliveworkplace:iron_will`, `aliveworkplace:silver_tongue`,
  `aliveworkplace:night_owl`, `aliveworkplace:lucky`, `aliveworkplace:hardy`, `aliveworkplace:beloved`,
  `aliveworkplace:born_leader`.
- Texts: `legend.aliveworkplace.founder.lore` (and one per Legend), `gifted.aliveworkplace.prodigy.desc`.
- Command (operators): `/workplace legend list|make <id>|clear`.

## Decisions

From the design note `docs/design/M29.md`, section 7 (the owner may change any):

- The chance of a gift is a threshold on who the villager is, not a fresh roll: raising it keeps everyone already
  Gifted, and changing it never swaps one gift for another.
- A villager who becomes a Legend keeps their gift; guests and found Legends arrive without one.
- With `legends` off the people stay: settled Legends live on as ordinary Masters and are restored as they were.
- An unknown condition or power fails the whole Legend file, so a typo never makes a Legend come for free.
- An existing City gets the Founder's mood once after the update, rather than never.
- 29.3: Rare is one of each per village, Legendary one of each per world.
- 29.4: what a village must do for a Legend is never a secret (Terraria-style cards on the hall).
- B345: at a found Legend's camp the castaway's cooked meal and the Old Sage's answer are taken from either hand: with
  the main hand empty, the off hand's meal or answer is handed over. An off-hand item that isn't the answer (a torch, a
  shield) leaves it an empty-handed click, so the Sage asks his riddle again and counts no miss.

## Known limits

- Legends never leave, even on strike, so a Legendary one stays in the first village that earned them.
- The found way needs a player to walk into the right kind of structure; nothing is searched while nobody is online.
- The Pokémon Professor and Ranger don't exist without Cobblemon; Alphas need Cobblemon 1.8.
- The Pathfinder's file asked for a Cartographer of level 3 (Journeyman) until B96; it now asks for an Expert
  (level 4), as roadmap 29.13 says. A Pathfinder who has already come stays.

## Legends of the realm (33.23)

Two Legends gain a power each with 1.7's colonies and trade:

- **The Founder** (`lead_colonies`: `rank` town, `settlers` 4, `cooldown` 50): the village he lives in may found
  colonies from Town rank instead of City, sends four settlers instead of two (more jobless first, then workers it has
  three or more of; never a guard, a Legend or a child) and waits half as long between colonies (4 days of 7, rounded).
  An order given while he lives there carries his blessing: the colony's chronicle reads "Founded on day 12 with the
  Founder's blessing, by settlers from Thornholm", the mother's "…, with the Founder's blessing", and his statue (M29's
  build) goes up beside the colony's camp when the settlers arrive.
- **The Merchant Prince** (`trade_reach`: `spread` 50): his village's Prices tab compares every village within caravan
  range, not only its partners; its caravans take goods for sale only to whichever partner pays most for
  them; and its board sells at 105% of what it pays instead of 110% (after Common Coin's points). His prices are 33.2's.

Without the Legend settled (or on strike), nothing changes. A guest who settles has their powers from that moment
(a Seer settled at dawn foretells that dawn).

## Proof

GameTests: `LegendEngineGameTests` (6), `LegendSlotsGameTests` (6), `LegendsHallGameTests` (3),
`LegendNeedsGameTests` (9), `LegendGuestsGameTests` (8), `LegendSitesGameTests` (7), `StrangeMoodGameTests` (6),
`GiftedGameTests` (6), `GiftedBornGameTests` (9), and one class per Legend: `MasterArchitectGameTests`,
`PathfinderGameTests`, `OldSageGameTests`, `GolemSmithGameTests` (with `GolemHaulerFightGameTests`, 4: a hauler back at work
after a fight, B329; `GolemHaulerRouteGameTests`, 2: a hauler whose nearest Storehouse is walled in gives it up after
10 seconds without headway and takes its load to the next nearest, B450, and one whose nearest Drop Box is walled in
fetches from the next, B456; `QaHaulerGiveUpGameTests`, 4 (QA): a given-up Storehouse, and a given-up Drop Box, is left alone for the
2,400-tick wait even once the way is open and used again after it, a hauler saved and loaded right after giving up keeps its whole load and
gets it home, and one whose only Storehouse is walled in puts its load back into the Drop Box and is free again, B463; `QaSentryGameTests`, 3: a Wall Sentry keeps its post and 200 health through a save and load
and walks back to it, a Nether route is refused and a blank map takes the post, each with its line, a foe 10 blocks
from the post is left alone), `SeerGameTests`, `MerchantPrinceGameTests`,
`GrandChefGameTests`, `BardLaureateGameTests`, `BeastmasterGameTests`, `FounderGameTests`,
`PokemonProfessorGameTests`, `PokemonRangerGameTests`, `ProfessorCompatTests`, `RangerCompatTests`,
`RealmLegendsGameTests` (5, 33.23); `QaStageSevenGameTests` (the camp clicks through real packets, both hands, B345).
QA (lane d): `QaLegendRecordGameTests` (12, 29.3 and 29.5): a Rare per kind per village, a Legendary per world
(any dimension), the Mythic caps at every rank from both sides, a fallen Legend's slot free on day 7 not day 6 and the
line written once, the record's save and old-save defaults, many rounds on one day counting one unmet day, the 3 days'
grace, a strike partly met going on, the `legendNeeds` and Legends switches, every record and strike sentence.
QA (lane d, 29.2, 29.4, 29.6): `QaLegendEngineGameTests` (6): a malformed Legend file fails alone (no rarity, an
unknown rarity, job, way or luxury, a pace below 1, a typo in a way's conditions) while a minimal one loads; the Old
Sage keeps the Legend job as a Master where a jobless farmer is reset; a pace power reaches a builder exactly 8 blocks
away but not 9, and never a farmer; a dead Legend's aura is gone by the next 200-tick refresh; condition numbers one
short and exactly met, and a guest Legend never counts as settled; the Legends page's Mythic line at each rank.
QA (29.4): `QaLegendsHallGameTests` (7): conditions one short, exactly met and over (99 cents isn't an emerald;
"1 of 1"), the "What next?" tip at one missing but not two or none, "lives in" only for a Legendary taken elsewhere,
every way a Legend comes in English, a need unmet 1 day crossed and 0 days ticked with two Legends first on the hall's
list, and all six Legend chatter lines with names filled in.
QA (29.8): `QaLegendGuestsGameTests` (6): a guest still there a tick before the third evening and gone at it, with the
leaving line in English; every need met that evening settles them instead; the 7 days from the day they came (day 11
no, day 12 yes for a day-5 visit); a way's chance of 0 never and 1 always (over 1 read as 1); a Legendary settled in
another village keeps them away while a Rare there doesn't; the terms screen's lines in English ("2 days left of my
stay", "My last day here", a Legend with no powers).
`QaGiftedGameTests` (7): `giftedChance` 1 gifts everyone and 10 about one in ten, keeping everyone Gifted at 30; a
malformed gift file fails; a `gifted` attachment of "none" or a removed gift makes nobody Gifted; the hall's gold line
for each of the four; Silver Tongue on 5, 20 and 64 emeralds (4, 16, 52) and gone with `giftedChance` 0; the Night
Owl's hours at every edge; the night schedule for a miner, never for a child, a nitwit or the jobless, kept through a
save and dropped when the gift goes.

Showcase scenes: `legend`, `legend_announce`, `legends_hall`, `legend_guest`, `legend_sites`, `legend_strike`,
`strange_mood`, `gifted`, `gifted_born`, `research_trees`, `legend_architect`, `legend_pathfinder`, `legend_sage`,
`legend_golem_smith`, `legend_seer`, `legend_merchant_prince`, `legend_grand_chef`, `legend_bard`,
`legend_beastmaster`, `legend_professor`, `legend_ranger`, `legend_founder`, `founder_colony`, `merchant_prince_board`.

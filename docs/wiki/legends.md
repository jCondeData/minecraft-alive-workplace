# Legends

Rare named villagers with powers, who come to a village that has earned them. Also the Gifted: about one villager in
thirty with a rare gift. Part of 1.3 (Legends): off until that expansion is finished.

Roadmap items: 29.2, 29.3, 29.4, 29.5, 29.6, 29.7, 29.8, 29.9, 29.10, 29.12, 29.13, 29.14, 29.15, 29.16, 29.17, 29.18,
29.19, 29.20, 29.21, 29.22, 29.23

## What a player sees

Now and then a village gains a Legend: one named villager, a Master of their trade, wearing their own outfit over
their trade's, their name in gold over their head and a soft sparkle every few seconds. Their coming is announced.

Nothing about them is secret. The hall's Legends page (the nether star in the page row) lists the village's own
Legends first, then every other Legend as a card: its rarity, how it comes, each thing the village must have with how
far it has got ("Kinds of meal in the store: 5 of 8"), the luxury it likes and its powers.

| Legend | Rarity | Comes | The village must have | Brings |
|---|---|---|---|---|
| Master Architect | Legendary | visits the inn | Town rank, buildings in 3 styles | builders nearby twice as fast; a grand rebuild of a house |
| Pathfinder | Rare | found at a ruined portal; or born | a Cartographer of level 3 or higher | far expeditions; leads the player on one |
| Old Sage | Rare | found in a hermit's hut; or born | 5 research levels | riddles, then a research tree of his own |
| Golem Smith | Legendary | a strange mood (a Master Tinkerer) | 4 iron golems | a forge that makes golems which work |
| Seer | Rare | the Chapel at midnight under a full moon; or born | a finished Chapel | foretells raids; blesses weddings |
| Merchant Prince | Legendary | found by a shipwreck | 500 emeralds through the treasury, 3 caravan routes | a bank in the hall, trade fairs, better caravan pay |
| Grand Chef | Rare | a strange mood (a Master Chef); the inn; or born | 8 kinds of meal in the store | banquets; a faster kitchen |
| Bard Laureate | Rare | a festival 30 villagers come to; or born | (the crowd) | the village's anthem; work songs |
| Beastmaster | Rare | found caged at a pillager outpost; or born | 10 animals by the herders | war dogs for the guards; bred horses |
| Pokémon Professor (Cobblemon) | Legendary | visits the inn | 25 pastured Pokémon of 10 types | hints about your Pokémon; a village Pokédex |
| Pokémon Ranger (Cobblemon) | Rare | visits the hall | an Alpha Pokémon within 96 blocks | calms Alphas; befriends wild Pokémon |
| Founder | Mythic | a strange mood, at the first rise to City | City rank for the first time | a statue; a Founder's Wagon every week |

## How it works

**Legends are data.** One file each in `data/aliveworkplace/legends/`: rarity, title and lore, what the village must
have, the ways they come, their powers. A file with an unknown condition or power fails as a whole (and is logged), so
a typo never makes a Legend come for free. The two Pokémon Legends load only with Cobblemon.

**Rarity (29.3).** A Rare Legend comes once to each village; a Legendary one once to each world; a Mythic one as
many as the villages' ranks allow (`mythicLegendCap`: by default a Town holds 1 and a City 2). The server keeps one
record of every Legend settled anywhere. A Legend holds their slot alive, as a zombie villager, or dead in a grave;
dead with no grave they fall 7 days later and the slot is free again.

**Four ways to come.**
- *Visit* (29.8): once a day a village with no guest may get one, at the inn, the market, a festival, the Chapel or
  the hall, one time in four by default. A guest stays up to 3 days; the same Legend doesn't visit again for 7.
- *Found* (29.9): at a ruined portal, a pillager outpost or a shipwreck, for a player who owns (or is a friend of
  the owner of) a qualifying hall within 1500 blocks. A small camp is set down with the Legend in it; each structure
  is used once.
- *Born* (29.7): when the child of two schooled Masters grows up, one time in 20 they are a Rare Legend of a
  parent's trade; otherwise one time in 4 they are Gifted.
- *Strange mood* (29.10): once a day in a happy village a Master may be seized (one time in 8). They stand at
  their workstation and ask for three rare materials within 3 days. Bring them and they make a Masterwork and become
  the Legend; fail and they sulk for 7 days.

**Needs and strikes (29.5).** A settled Legend needs a home of their own (their bed in a finished tier III
building), their luxury once every 7 days, and a happy village (average mood 60 or more). A need unmet 2 days
running starts a strike, but not in the first 3 days. On strike their powers stop and they picket by the hall with
what they want in red over their head. The day every need is met they go back to work. Legends never leave.

**The Gifted (29.6, 29.7).** One villager in `giftedChance` (30) has a gift, decided by who they are, so every
existing villager already has theirs and nothing is migrated: Prodigy (learns three times as fast), Iron Will (never
panics), Silver Tongue (trades 20% cheaper), Night Owl (works at night), Lucky, Hardy (never ill, twice the health),
Beloved (neighbours are happier), Born Leader (workers of their trade nearby 10% faster).

## Switches

| Key | Default | What it does |
|---|---|---|
| `legends` | on from 1.3 | Off: none come; settled Legends stay as ordinary Masters and come back when it is on again |
| `legendNeeds` | on from 1.3 | Off: no needs and no strikes |
| `legendSites` | on from 1.3 | Off: no Legends found at ruins, outposts or shipwrecks |
| `strangeMoods` | on from 1.3 | Off: no strange moods |
| `giftedChance` | 30 (0 to 1000) | One villager in this many is Gifted; 0: nobody is, and nothing is erased |
| `mythicLegendCap` | 0, 0, 1, 2 | Mythic Legends by rank (Hamlet, Village, Town, City); in the file only |

## Saved data

- With the overworld: `aliveworkplace_legends`, the server's record (each Legend's villager, dimension, hall, rarity,
  the day they settled and the day they fell) and the structures already used for a found Legend.
- On the Legend: the attachment `legend` (which Legend, guest or settled, their hall, the day they came, how many
  days each need has gone unmet, the day a strike began: -1 for none). Every field has a default.
- On a Gifted villager: the attachment `gifted` only when it overrides the roll (the born, the inn's travellers).
- On a Master in a strange mood: the attachment `strange_mood`.
- On the hall: the guest in the village, `treasuryTotal` (starts at 0 in an old hall), the Founder's fields.

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

## Known limits

- Legends never leave, even on strike, so a Legendary one stays in the first village that earned them.
- The found way needs a player to walk into the right kind of structure; nothing is searched while nobody is online.
- The Pokémon Professor and Ranger don't exist without Cobblemon; Alphas need Cobblemon 1.8.
- The Pathfinder's file asks for a Cartographer of level 3 (Journeyman), while roadmap 29.13 and the test's name say
  Expert. Reported as a possible bug when this page was written (2026-10-09); the page states what the file does.
- The luxuries Legends take are stand-ins for now (honey bottles, amethyst shards and emeralds, books, leather
  armour), through item tags, until the luxury jobs of 1.8 make the real ones.

## Proof

GameTests: `LegendEngineGameTests` (6), `LegendSlotsGameTests` (6), `LegendsHallGameTests` (3),
`LegendNeedsGameTests` (9), `LegendGuestsGameTests` (8), `LegendSitesGameTests` (7), `StrangeMoodGameTests` (6),
`GiftedGameTests` (6), `GiftedBornGameTests` (9), and one class per Legend: `MasterArchitectGameTests`,
`PathfinderGameTests`, `OldSageGameTests`, `GolemSmithGameTests`, `SeerGameTests`, `MerchantPrinceGameTests`,
`GrandChefGameTests`, `BardLaureateGameTests`, `BeastmasterGameTests`, `FounderGameTests`,
`PokemonProfessorGameTests`, `PokemonRangerGameTests`, `ProfessorCompatTests`, `RangerCompatTests`.

Showcase scenes: `legend`, `legend_announce`, `legends_hall`, `legend_guest`, `legend_sites`, `legend_strike`,
`strange_mood`, `gifted`, `gifted_born`, `research_trees`, `legend_architect`, `legend_pathfinder`, `legend_sage`,
`legend_golem_smith`, `legend_seer`, `legend_merchant_prince`, `legend_grand_chef`, `legend_bard`,
`legend_beastmaster`, `legend_professor`, `legend_ranger`, `legend_founder`.

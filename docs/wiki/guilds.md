# Guilds

A Master of a trade, given a Guild Charter, leads that trade's guild. Once the village has built a Guildhall, the
guild's perks reach every member. Part of 1.4 (Edicts and civic items): off until that expansion is finished.

Roadmap items: 30.17, 30.18, 30.19, 30.20, B78

## What a player sees

Craft a Guild Charter (3 paper, an emerald, a gold ingot and red dye) and sneak-right-click a Master villager with
it. They become the Guild Master of their trade's guild, and everyone is told ("Ada is now the Guild Master of the
Builders' Guild in Thornholm!"). The guild's perks wait until a builder finishes a Guildhall in the village; then
"The Builders' Guild is founded in its Guildhall" and the perks are in force. The Book of Edicts shows the village's
guilds on its last row, with pages when there are more than the row holds.

If the charter can't be used you are told why: the villager isn't a Master yet, their trade has no guild, the
village is only a Hamlet, it already has that guild, or it has all the guilds its rank allows.

| Guild | Members | Perks |
|---|---|---|
| Builders' Guild | Builders, Carpenters, Masons, Dyers | 15% faster; up to 5 idle builders help at a build (not 3) |
| Miners' Guild | Miners, Sifters, Netherworkers | 15% faster; pickaxes and nether gear wear half as fast |
| Smiths' Guild | Armorers, Toolsmiths, Weaponsmiths, Tinkerers, Ball Smiths | 15% faster; an ingot mends a third (not a quarter) |
| Woodsmen's Guild | Lumberjacks, Fletchers, Fishermen | 15% faster; axes and fishing rods wear half as fast |
| Harvest Guild | Farmers, Orchard Keepers, Florists, Beekeepers, Composters, Chefs | 15% faster; farms and orchard rounds reach 24 blocks (not 16) |
| Herders' Guild | Shepherds, Butchers, Ranchers | 15% faster; every herd may be 4 bigger |
| Scholars' Guild | Scholars, Teachers, Librarians, Cartographers | 15% faster; research costs a quarter less |
| Healers' Guild | Nurses, Clerics, Undertakers | 15% faster; the ill get well in two days (not three); nurses and undertakers look 48 blocks out |
| Merchants' Guild | Shopkeepers, Innkeepers, Ferrymen, Postmen, Porters | 15% faster; travellers a quarter cheaper to hire; porters carry 3 more stacks |
| Wardens' Guild | Guards | Train on the dummies up to Master (not Expert); hit 10% harder |
| Trainers' Guild (with Cobblemon) | Trainers, Trainer Leaders, Move Tutors, Pokémon Traders, Fossil Scientists | Lessons and revivals a fifth cheaper; trainers rank up a quarter faster |

## How it works

**Guilds are data.** One file each in `data/aliveworkplace/guilds/`: the name, an icon, the trades and the perks. A
data pack adds its own or switches one of ours off with `"enabled": false`. The Trainers' Guild only loads with
Cobblemon installed.

**How many.** One guild per trade, and `guildsPerRank` guilds per rank above Hamlet: with the default of 1, a
Village has 1, a Town 2 and a City 3. With the setting at its highest (4) a City holds 12, which is why the book's
guild row has pages (B78).

**Chartered, then founded.** A charter makes the guild; a finished Guildhall (any build whose id starts with
"guildhall") founds it. Each guild needs a Guildhall of its own: the oldest chartered guild without one claims the
next one finished. While a guild's Guildhall is no longer finished (a creeper, say), its perks wait.

**Perks reach members only**: villagers of the guild's trades who live in that village. The 15% goes through the one
pace rule, so it shares the cap with every other bonus.

**Succession.** When the Guild Master dies, the most experienced member takes over and the village is told. With no
member left the guild is dissolved.

## Switches

| Key | Default | What it does |
|---|---|---|
| `guilds` | on from 1.4 | Off: charters are refused and perks are off; guilds stay saved |
| `guildsPerRank` | 1 (1 to 4) | Guilds a village may have per rank above Hamlet |

## Saved data

- On the hall: `guilds`, the list of charters (which guild, its master, its Guildhall once founded); empty by
  default.
- On the Guild Master: the attachment `guild_master` (which guild they lead); absent on everyone else.

## Items, blocks, jobs, commands

- Item: Guild Charter (`aliveworkplace:guild_charter`, `item.aliveworkplace.guild_charter`), stacks to 16, one used
  per guild.
- Guild ids: `aliveworkplace:builders`, `aliveworkplace:miners`, `aliveworkplace:smiths`, `aliveworkplace:woodsmen`,
  `aliveworkplace:harvest`, `aliveworkplace:herders`, `aliveworkplace:scholars`, `aliveworkplace:healers`,
  `aliveworkplace:merchants`, `aliveworkplace:wardens`, `aliveworkplace:trainers`.
- Texts: `guild.aliveworkplace.builders` and `guild.aliveworkplace.builders.perk` (and the same for each guild);
  refusals such as `message.aliveworkplace.guild.not_master` and `message.aliveworkplace.guild.hamlet`.
- Building: the Guildhall blueprint, in the Blueprint Table.
- No commands.

## Decisions

- 30.17 (design note `docs/design/M30.md`): a guild's perks apply only to its members, and only once it is founded
  in a Guildhall of its own; one guild per trade, and one per rank above Hamlet.
- 30.17: when a Guild Master dies the most experienced member takes over, so a guild outlives its master.
- 30.17: the Builders' Guild's "5 helpers" needed an effect type the roadmap hadn't listed (the helpers at a build);
  it was added to the shared effect toolbox instead of being written into the builder.
- 30.20: the Trainers' Guild file carries a load condition (Cobblemon installed), so without Cobblemon it isn't
  there at all, and the guild code never touches Cobblemon.
- B78: the guild row pages (six guilds show without a page button, a seventh adds one), because a City can hold up
  to 12 with `guildsPerRank` at 4.

## Known limits

- A villager must be a Master (level 5) of their trade to lead a guild.
- A Hamlet can have no guild.
- A guild whose Guildhall is no longer a finished building loses its perks until it is finished again.
- With `guilds` off nothing is erased: guilds and masters come back when it is switched on again.

## Proof

GameTests: `GuildGameTests` (6: loading, every refusal and the grant, the master kept over a reload, perks waiting
for the Guildhall, five helpers at one build), `GuildPerkGameTests` (5), `GuildHarvestGameTests` (6),
`GuildServiceGameTests` (5), `GuildTrainersCompatTests`, `EdictBookGameTests.everyGuildShowsOnTheGuildRow`,
`EdictBookGameTests.qaSixGuildsHaveNoPageButtonAndSevenPage`.

Showcase scenes: `guildhall` (charters and the Guildhall), `guilds` (the other guilds at work).

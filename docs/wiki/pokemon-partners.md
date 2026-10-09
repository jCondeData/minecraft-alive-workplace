# Pokémon partners

With Cobblemon, Pokémon kept in a Pasture Block near a villager's workstation help with the job when their type
suits it: the work goes faster, and (from 1.2) you see them doing it.

Roadmap items: 28.3, 28.4, 28.5, 28.6

## What a player sees

Put a Pasture Block within 16 blocks of a workstation and leave Pokémon in it. If their type suits the job, each one
cuts the time the work takes by 15%, up to three helpers. The line over the villager's head says who is helping
("· with Machop"), and sneak-right-clicking a worker shows how much faster they are.

| Job | Helpful types |
|---|---|
| Builder; Carpenter, Mason | Fighting, Rock, Steel |
| Miner | Ground, Rock, Steel |
| Lumberjack | Grass, Bug, Fighting |
| Orchard Keeper | Grass, Bug, Flying |
| Farmer | Grass, Ground, Water |
| Fisherman | Water, Ice |
| Armorer | Fire, Steel (one more stack of ore a trip each) |
| Toolsmith, Ball Smith | Steel, Fire |
| Weaponsmith | Steel, Fighting |
| Fletcher | Flying, Bug |
| Nurse | Fairy, Normal, Psychic |
| Porter | Fighting, Normal (each carries 3 more stacks a trip) |
| Chef | Fire, Normal |
| Fossil Scientist | Rock, Psychic |
| Guard | Fighting, Dragon: they fight beside the guard |
| Postman | Flying: air mail goes straight to far mailboxes |

**Partner shows (1.2).** At moments of the work, one of the worker's pastured Pokémon walks to the spot, does
something you can see, and walks back: a Machop carries planks to the build and punches blocks home, a Geodude
carries stone, a Squirtle waters the farmer's field (the farmland really gets wet), a Charmander breathes fire into
the blast furnace, a Pidgey takes the air mail up and lands back empty-handed, a Ralts sends a pink pulse over the
nurse's patient, a Combee circles the hive.

## How it works

**Counting helpers.** Every 100 ticks a worker's partners are counted: Pokémon in a pasture within `partnerRadius`
of the workstation whose type is on the job's list. The time a job takes is multiplied by 1 minus 15% per helper,
never below 40% of the usual time, and that factor goes through the one pace rule with every other bonus (so the
shared cap of `maxWorkPace` holds). A worker has room for 3 helpers, one more for each level of the village's
Kinship research.

**Core never touches Cobblemon.** The jobs ask an extension point for "partners near this workstation"; the
Cobblemon side fills it in only when Cobblemon is installed. Without Cobblemon there are simply none, and the mod
runs the same.

**Shows are data (28.3).** One file each in `data/aliveworkplace/partner_shows/`: the jobs, the
types, the cue (a named moment of the job, such as "fetch" or "cook"), what the Pokémon carries, its animation,
particles, sound, how long it lasts (10 to 400 ticks) and a small effect. What it carries is a display that follows
it; it is removed when the show ends, and a display whose show was cut off by a restart is removed when its chunk
loads.

**Careful with your Pokémon.** A pastured Pokémon is never untethered from its pasture. A show never touches a
Pokémon that is in battle, ridden or on a shoulder, nor its held item, friendship, moves or stats.

**Budget.** One show per worker every 200 ticks, at most 6 running per dimension, and none with no player within 48
blocks.

## Switches

| Key | Default | What it does |
|---|---|---|
| `partnerRadius` | 16 (4 to 48) | How close to a workstation a pasture must be |
| `partnerShows` | on from 1.2 | Off: partners still speed the work, but aren't seen doing it |
| `maxWorkPace` | 200 (100 to 400) | The cap partners share with every other speed bonus |

## Saved data

Nothing on the villager: partners are counted again every 100 ticks. Each helping Pokémon counts its working days
on a counter in its own saved data (used by the Festival Cup's Workers' Cup, see [The Festival Cup](festival-cup.md);
never twice the same day). A show's carried display is a temporary entity tagged `aliveworkplace_show`.

## Items, blocks, jobs, commands

- Block: Cobblemon's own Pasture Block. The mod adds none for this.
- Works for every job in the table above; the shows cover those and more (Beekeeper, Florist, Sifter, Composter,
  Scholar, Teacher, Tinkerer, Rancher, Netherworker, Cartographer, and the Pokémon jobs).
- Works alongside Cobbleworkers: the same Pokémon can work the pasture for it and help the villager.
- No commands.

## Decisions

- 28.3: the speed bonus comes from the count of helpers, whether or not a show plays; `partnerShows` only switches
  what you see.
- 28.3: a pastured Pokémon is never untethered and nothing about it is changed.
- 30.2: partners are one bonus under the shared pace cap, and five jobs that used to count partners twice (Sifter,
  Beekeeper, Florist, the explorer's search, Composter) now count them once.
- Each type carries only its own materials: Fighting types wood, Rock types stone, Steel types iron parts.

## Known limits

- Needs Cobblemon; without it this page describes nothing in the game.
- A Pokémon walks only as far as its pasture lets it; work farther away gets no show (the speed bonus still counts).
- No show plays with no player within 48 blocks.

## Proof

GameTests: `PartnerShowsGameTests` (9: every show file loads, each type carries only its own materials, a malformed
file says what is wrong, a cut-off show's display is removed), `QaPartnerShowsGameTests`, `PaceGameTests` (10). With
Cobblemon: `PartnerShowsCompatTests` (2), `PartnersAtWorkCompatTests` (10), `PartnersForgeCompatTests` (11),
`PartnersAllCompatTests` (12), `PaceCompatTests.aSifterWithTwoPartnersTakesSeventyPercent`.

Showcase scenes: `partners_engine`, `partners_land` (building and the land), `partners_forge` (post, forge and
kitchen), `partners_all` (everyone else), `pace` (a builder past the cap), `guard_pokemon`.

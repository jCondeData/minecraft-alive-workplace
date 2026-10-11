# Wonders

A Wonder is a village's life's work: a huge build raised in four stages by every builder in the village at once, fed by
goods from every job, drawn up by the Master Architect and needing a second Legend. Each of the eight Wonders changes
what its village or its realm can do while it stands. Part of 2.0 (Wonders): the design note and the first Wonder's
building (the Great Cathedral) exist so far (see Known limits); the full plan is the design note, docs/design/M35.md.

Roadmap items: 35.1, 35.9

## What a player sees

Nothing yet: no Wonder can be started in game. The design note describes what is coming: a Wonders tab on the Village
Hall listing each Wonder with its requirements ticked or crossed; Draw up the plans (32 emeralds) for a Wonder Plan
with a see-through preview; a Foundation Stone handed to a builder; a crew of up to 12 builders on 8 × 8 columns, with
scaffolding where they can't reach; goods from every job, dedicated at the stone stage by stage; and a finished Wonder
announced to the server, celebrated with a festival, and its powers on while at least 90% of it stands.

| Wonder | Rank | Legend (stand-in) | Other requirements | Powers |
|---|---|---|---|---|
| Great Cathedral | Town | the Seer (a Master Cleric) | 3 finished Chapels | Undertakers revive anywhere in the village for free; blessed weddings; beauty |
| Lighthouse | Town | the Merchant Prince (3 caravan routes) | on the coast | caravans reach twice as far in half the time; ferry fares halved; raid warnings |
| Great Library | Town | the Old Sage (a Master Scholar) | 20 research levels | a second research tier |
| Great Wall | Town | the Golem Smith (4 iron golems and a Master Tinkerer) | a finished Gatehouse | raids gather outside it; golem sentries on the walk |
| Observatory | Town | the Seer (a Master Cleric) | none | the Sky Chart; the Coming days page |
| Hanging Gardens | Town | the Grand Chef (a Master Chef, 8 kinds of meal) | none | a quarter more harvest across the realm; faster births |
| Colossus | City | the Founder (City rank) | none | more mood across the realm; more and better travellers; beauty |
| Stadium (with Cobblemon) | Town | the Pokémon Professor (25 pastured Pokémon of 10 types) | Cobblemon | Stadium Days with visiting trainers; hosts the Festival Cup |

Every Wonder also needs the Master Architect (stand-in: a Master builder).

**The Great Cathedral** (35.9) is drawn: a Gothic cathedral on a cross plan, 39 blocks across the transepts, 55 long,
its two west towers 44 high under stone spires reaching 52; about 14,250 blocks. A deep west door under three stepped
pointed arches, a rose window of white, light blue, yellow and red glass over it; a nave of five bays with flying
buttresses over the aisles and tall lancet windows; transepts with gable windows over their doors; a choir ending in a
rounded apse; open belfries with three bells. Inside: pews either side of a red runner, banners and lanterns on the
polished andesite piers, an altar of gold and candles, three side chapels with lecterns, and a sacristy with a brewing
stand. It goes up in four stages, each leaving the site tidy: I *The Foundations and the West Front*, II *The Nave*,
III *The Transepts and the Choir*, IV *The Towers and the Rose Window*. Its goods: I 128 bread (Chef), 8 iron pickaxes
(Toolsmith), 16 gold ingots (Armorer); II 64 white candles (Dyer), 32 honeycomb (Beekeeper), 64 white wool (Shepherd);
III 16 books (Librarian), 12 potions of healing (Cleric), 64 small flowers (Florist); IV 32 chiseled stone bricks
(Mason), 16 cakes (Chef), 64 bone meal (Composter). It can't be built in game until the rest of Wonders is.

## How it works

The game side isn't built yet. Each stage's parts will be ordinary builds of the builder, pointing back to their Wonder
and using the Foundation Stone as their bench, so the chests near the stone are the stockpile.

What exists is each Wonder's building and data file. A Wonder is drawn whole in `tools/blueprints/wonders.py`, stage by
stage; the kit's `wonder_stages()` saves each stage as parts of at most 48 a side (`structure/wonder/<id>/stage_<n>_<k>`,
with their offsets from the Wonder's corner in the data file) and writes `aliveworkplace:great_cathedral`'s data file
(`data/aliveworkplace/wonders/great_cathedral.json`) with those parts, so the two always agree. A stage only adds
blocks: where an earlier stage left a block, a later part holds nothing (not air), so no builder ever takes down what
the stage before finished. Stage I's parts reach over the whole footprint, so its clearing makes room for the rest.
Each stage is checked as it will stand, on the stages before it. The parts are hidden from the Blueprint Table, like
research builds. Requirements use the same condition
toolbox as Legends; powers are looked up per village and switch off when the Wonder is damaged. The design note has the
data format, the condition and power types, and every fallback while a linked expansion is off.

## Switches

None yet. The design note plans eight (wonders, wonderCaps, wonderLegends, wonderServerLimit, disabledWonders,
wonderCrew, builderScaffolding, wonderStanding), all off until 2.0 is finished, like every expansion's.

## Saved data

None yet. Planned: a new saved file of Wonders per dimension, and two optional fields on a build site (the Wonder it
belongs to, and its scaffolding). Nothing existing is renamed, so old worlds load unchanged.

## Items, blocks, jobs, commands

None yet (the Great Cathedral's data file and building ship, but nothing reads them until the rest of Wonders lands).
Planned: the Wonder Plan (an item), the Foundation Stone (a block), and a command listing every Wonder on the
server.

## Decisions

- The Great Cathedral's stages are cut into five parts (35.9): stage I in two (the whole floor and the west front), the
  others in one each, every part at most 48 a side; the towers' upper half and the spires (to 52) are a part starting
  14 up.
- A stage's round rose opening and the nave's open east end wait for the next stage (35.9): the rose's ring is built
  with the nave (II) and its glass with the towers (IV), as the roadmap splits them; nothing hangs in the air between
  stages.
- A Wonder's parts hide from the Blueprint Table (35.9), so a player can't build one part on its own.

- One Legend can serve two Wonders (the Seer for the Cathedral and the Observatory): a requirement only checks that the
  Legend lives in the village.
- The Master Architect requirement is added to every Wonder by the mod, so a server owner's own Wonder needs it too.
- Builders' scaffolding comes with 2.0, for every build (an owner question in the design note).

## Known limits

- Only the design note (35.1) and the Great Cathedral's design (35.9) exist. Nothing of Wonders can be started in game
  yet; the other seven designs come with their items.

## Proof

The design note, docs/design/M35.md. `WonderBlueprintGameTests` checks every Wonder's design: every part loads and is at
most 48 a side, its name, description and stage names have their lines, the parts aren't in the Blueprint Table, no
part sets air over an earlier stage's block, the block count is within 10% of the roadmap's (14,000 for the
Cathedral), and it uses no retired job block, no calcite and nothing a player can't get.

Showcase scenes: wonder_gallery (each Wonder placed whole, from the front and from behind).

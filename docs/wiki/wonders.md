# Wonders

A Wonder is a village's life's work: a huge build raised in four stages by every builder in the village at once, fed by
goods from every job, drawn up by the Master Architect and needing a second Legend. Each of the eight Wonders changes
what its village or its realm can do while it stands. Part of 2.0 (Wonders): only the design is written so far (see
Known limits); the full plan is the design note, docs/design/M35.md.

Roadmap items: 35.1

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

## How it works

Not built yet. Each stage's parts will be ordinary builds of the builder, pointing back to their Wonder and using the
Foundation Stone as their bench, so the chests near the stone are the stockpile. Requirements use the same condition
toolbox as Legends; powers are looked up per village and switch off when the Wonder is damaged. The design note has the
data format, the condition and power types, and every fallback while a linked expansion is off.

## Switches

None yet. The design note plans eight (wonders, wonderCaps, wonderLegends, wonderServerLimit, disabledWonders,
wonderCrew, builderScaffolding, wonderStanding), all off until 2.0 is finished, like every expansion's.

## Saved data

None yet. Planned: a new saved file of Wonders per dimension, and two optional fields on a build site (the Wonder it
belongs to, and its scaffolding). Nothing existing is renamed, so old worlds load unchanged.

## Items, blocks, jobs, commands

None yet. Planned: the Wonder Plan (an item), the Foundation Stone (a block), and a command listing every Wonder on the
server.

## Decisions

- One Legend can serve two Wonders (the Seer for the Cathedral and the Observatory): a requirement only checks that the
  Legend lives in the village.
- The Master Architect requirement is added to every Wonder by the mod, so a server owner's own Wonder needs it too.
- Builders' scaffolding comes with 2.0, for every build (an owner question in the design note).

## Known limits

- Only the design note (35.1) exists. Nothing of Wonders is in game yet.

## Proof

The design note, docs/design/M35.md. No GameTests or showcase scenes yet.

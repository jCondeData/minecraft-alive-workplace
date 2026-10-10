# Caravans you can see

ROADMAP 33.7 (part of 1.7, "From village to realm"; off until 1.7 is released). When a caravan leaves or arrives in a
village you are standing in, you see it: a carter leading two pack llamas. It builds on
[caravans that trade](caravan-trade.md).

Roadmap items: 33.7

## What a player sees

**A caravan leaving.** By the Storehouse stands a carter, a villager in the porter's outfit whose name reads
"Thornholm's caravan", holding two llamas on leads. Each llama carries a chest and wears a carpet in the village's
colour. They walk off toward the village the caravan is going to, and at the edge of your village they are gone.

**A caravan arriving.** The sending village's carter ("Ashford's caravan", in Ashford's colour) comes in at the edge
of your village from the side Ashford lies on and walks to the Storehouse. There the llamas stand still for six
seconds while the goods are unloaded: you hear the llamas' chests and the Storehouse's chest open and close. Then the
carter leads them back out the way they came, and at the edge they are gone.

**The colour** is the base colour of the village's [Village Banner](village-hall.md) when it has one. A village
without a banner gets one of the sixteen colours, picked from where its hall stands, so it always wears the same.

**They are only a sight.** Right-clicking the carter or a llama does nothing: no trade, no ride, no look in the
chests, no feeding. Nothing can hurt them, they pick nothing up, and when they go they leave nothing behind.

## How it works

- **The goods don't ride on the llamas.** They travel the saved way, exactly as before: taken from the Storehouse's
  chests when the caravan leaves, on the caravans' list while it's on the road, put into the other village's chests
  when it arrives. So nothing can be lost or stolen on the way, and the goods that arrive are the same whether anyone
  watched or not.
- **Only where someone can see it.** A party is shown when a player (not a spectator) is within 96 blocks of the
  village's hall at the moment the caravan leaves or arrives. Otherwise nothing is spawned at all.
- **Where they walk.** They load and unload at the free spot nearest the Storehouse (the nearest one under open sky,
  when the Storehouse is indoors). The edge is found by going from there toward the other village, over the ground,
  until the village's radius from its hall is reached (or the loaded world ends).
- **One party per village at a time.** A second caravan leaving or arriving while one is out walking moves its goods
  as usual and shows nobody.
- **Two minutes at most.** A party that hasn't finished its walk after 2 minutes (walled in, a river in the way) is
  taken away where it stands, in a puff of smoke.
- **Not villagers of the village.** The carter has no villager's mind: he never looks for a workstation, a bed or a
  partner, has no trades, and isn't counted in the hall's villagers, its jobless or its families. The llamas only
  follow their leads: they don't stroll, panic, spit or breed.
- **A restart.** The carter and llamas carry a tag. Any that a stopped server saved are taken away the moment they
  load again, like the ferry's ride boats.
- **The hall taken away, or the switch turned off:** a party out walking there is taken away.

## Switches

| Key | Default | What it does |
|---|---|---|
| `visibleCaravans` | on from 1.7 (off until then) | Off: no carter or llamas are shown, and any out walking are taken away. The goods travel exactly the same. |
| `villageBanners` | on | Off: every village's caravans wear the colour picked from its hall's position. |

## Saved data

Nothing of the party is saved: it is a sight, and one found in a save is removed.

On the caravans' file (`aliveworkplace_caravans`, per dimension), per village, optional:

- `colour`: the base colour of the village's Village Banner as its hall's last round saw it, so its caravans wear it
  in villages far from where its hall is loaded. Absent in older saves and for a village without a banner: the colour
  is then picked from the hall's position.

## Items, blocks, jobs, commands

None new. The carter wears the outfit of the porter (`aliveworkplace:porter`) but has no job; the llamas wear
vanilla carpets and chests. Their tag is `aliveworkplace_caravan`. The carter's name is
`entity.aliveworkplace.caravan_carter`.

## Decisions

All 33.7, lane a, 2026-10-10 (the full list is in ROADMAP's Notes):

- An arriving party walks back out to the edge after unloading, rather than vanishing at the Storehouse.
- An arriving party is shown when the goods arrive (the hall's round that unloads them), so the bread is in the chest
  a few seconds before the llamas reach the Storehouse. Showing it earlier would have needed the goods to wait for
  the walk, and then they would no longer arrive the same with the switch off.
- A caravan that comes home with unsold goods is the home village's own: its carter wears the home name and colour.
- "Within 96 blocks" is measured from the village's hall.
- A click does nothing at all, not even the villager's head shake.
- In GameTests the sights are off unless a test turns them on, like the Pokémon partners' shows: a party would walk
  through the tests next door.

## Known limits

- The party walks in a straight line toward the other village, not along the road between the villages (27.17).
- The carter doesn't open doors. If the only spot by the Storehouse is indoors behind a closed door, the party waits
  there and is taken away after two minutes.
- With two caravans in the same village within two minutes you see only the first.
- No test listens for the chest sounds, and nobody has heard them in play yet: the showcase GIF has no sound.
- No scene films a party being removed after a restart; the GameTest covers it.

## Proof

- `CaravanSightGameTests` (7): `aLeavingCaravanIsSeenWalkingOffAndIsGone` (the carter's outfit and name, two llamas
  on leads with chests and the village's carpet, gone at the edge with nothing left on the ground);
  `anArrivingCaravanComesInUnloadsAndGoesBack` (in from the edge, the banner's colour, the llamas standing while
  unloading, back out); `nothingIsSeenWithNoPlayerNear` (100 blocks away nobody, 90 blocks a party);
  `aSavedAndReloadedCarterIsTakenAway`; `theGoodsAreTheSameWithTheSwitchOnOrOff`;
  `onePartyPerVillageAndGoneAfterTwoMinutes`; `thePartyNeverTradesTakesAJobOrBreeds` (also: not counted by the hall,
  no ride, no hay, no harm, no bread picked up).
- `CaravanTradeGameTests` and `VillageHallGameTests.caravansCarryWhatAnotherVillageNeeds` pass unchanged.

Showcase scenes: `caravan`

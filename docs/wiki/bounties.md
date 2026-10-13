# The bounty board, bandit captains and rogue Pokémon

Villages post bounties on named bandit captains; players take them, hunt the captain at his hideout and hand in his
insignia for the reward; with Cobblemon, half the posters are on a rogue Pokémon instead. ROADMAP 31.13 and 31.14; builds on the quest journal (31.3), the arc engine's `place` (31.4), lairs
(32.3) and reputation (31.11). Part of the 1.5 expansion: switched on when Milestone 31 is complete.

Roadmap items: 31.13, 31.14

## What a player sees

- **Bounties are posted** by a village of Village rank or more: at most 2 open, at most one every 3 days, each up for
  7 days. Players near the village are told in gold ("Thornholm posts a bounty: Grimwald the Red, last seen 160 blocks
  north-east. Reward: 15 emeralds."), and the chronicle gets a BOUNTY line.
- **The Bounty Board** (a block: a paper between two planks, three planks under them, two sticks at the bottom
  corners) is a notice board on two posts. Its face shows one poster for each open bounty of its village, up to four.
  A right-click opens the **Bounties** tab of the nearest hall's journal; the tab is in the hall's journal anyway.
- **Taking a bounty**: click it in the tab. You get a **Wanted Poster** (its tooltip: his name, his quirk, where he was
  last seen and the reward) and a map with a red X on his hideout. Each player gets them once per bounty.
- **The hideout** is the bandit camp, 120 to 200 blocks from the hall, with his own banner on a spruce pole by the
  fire. It is built (by the arc engine's `place`) when a player first comes within 96 blocks, with him and a band of 2
  or 3 bandits in it.
- **Quirks**, one per captain: Ironhide (full iron armor, 20 more health), Fleetfoot (Speed II), Crossbow Ace (a
  pillager with a Multishot, Quick Charge II crossbow), Beast Rider (on a ravager), Night Stalker (invisible at night
  until he's hit), Warlord (a band of 5), Hexer (an evoker).
- **The claim**: he drops a **Captain's Insignia** when he dies. Hand it in at a Bounty Board or the Village Hall
  (right-click with it), or with the tab's hand-in button: 12 emeralds times the rank factor, once, to whoever hands it
  in, plus a bounty's standing (+40). The chronicle says who. A second insignia of the same bounty pays nothing.
- **Expiry**: an unclaimed bounty comes down after 7 days and its band leaves in a puff of smoke.
- **Lairs**: a captain holding a lair (32.3) near a village of Village rank is posted too, with his name and his lair's
  place; when he falls his bounty is paid straight to his killer.
- **Gossip**: villagers say where a wanted captain was seen ("They say Grimwald the Red was seen 160 blocks
  north-east."), or a rogue ("Big as a cart, and twice as angry.").

### Rogue Pokémon (31.14, Cobblemon)

- **The second kind of poster.** With Cobblemon installed, half the bounties a village posts are on a **rogue**: a
  wild Pokémon grown huge and mean, roaming within 24 blocks of a spot 150 to 250 blocks from the hall. Without
  Cobblemon this kind is never posted. Its poster reads "Wanted: Rogue Ursaring", "A huge, mean wild Pokémon, level 50.
  Beat it in battle or catch it", where it was last seen and the reward.
- **Its species** comes from the biome round its spot: forests Ursaring, Scyther, Pinsir; plains and meadows Tauros,
  Kangaskhan, Rapidash; mountains and peaks Onix, Golem, Aggron; deserts and badlands Sandslash, Krookodile, Flygon;
  swamps and jungles Tangrowth, Toxicroak, Swampert; beaches and stony shores Kingler, Crawdaunt, Barbaracle; snowy
  biomes Mamoswine, Abomasnow, Beartic; savannas and taigas Donphan, Rhydon, Lycanroc. A spot with none of these biomes
  (an ocean) is passed over for another; after 8 tries the village posts a captain instead.
- **Its level** is 35 at Village rank, 50 at Town and 65 at City; with Radical Cobblemon Trainers, the level cap of the
  player who comes near first, plus 5. It is **half again as big** (Cobblemon's size setting) and named "Rogue
  Ursaring". On Cobblemon 1.8 it is one of Cobblemon's **Alphas** instead.
- **It appears** when a player first comes within 96 blocks of its spot, and stays (it never despawns; if it's lost,
  it comes back the next time a player is near). It **glows for the players who took its bounty**, for them only, while
  they're within 32 blocks.
- **The claim**: beat it in a battle or catch it, and the bounty (16 emeralds times the rank factor: 20 in a Village,
  24 in a Town, 28 in a City) goes to that player straight away, once, with a bounty's standing (+40); a catcher keeps
  the Pokémon, its name with it. The chronicle says who ("Jesse caught Rogue Onix and was paid 24 emeralds.").
- **Expiry**: an unclaimed rogue bounty comes down after 7 days and the rogue wanders off in a puff.

## How it works

- `bounty/Bounties` holds it all. The hall's round (`VillageHallBlockEntity.serverTick`) takes down bounties past their
  7 days, posts a lair's captain, then (if `Bounties.mayPost`) a new captain.
- **Names**: `data/<namespace>/bounties/captains.json` has `first_names` (24) and `epithets` (16); a captain is one
  of each. An epithet can be translated with the lang key `bounty.aliveworkplace.epithet.<epithet in snake case>`.
- **Quirks**: `bounties/quirks/<id>.json`: `entity`, `gear` (slot to item), `enchantments` (on the main hand item),
  `effects` (`id`, `amplifier`; for good), `health` (added), `mount`, `band` (0: 2 or 3), `night_invisible`. Its name
  is the lang key `bounty.<namespace>.quirk.<id>`.
- **The hideout**: `ArcEffects.spot` picks the column (a ring of 120 to 200 blocks), `ArcEffects.build` builds
  `aliveworkplace:camp/bandit_camp` on clear natural ground near it when `ArcEffects.near` sees a player; then the
  banner and `Bounties.raise` (the captain at the chief's spot, the band round the fire).
- **Posters**: boards are remembered when placed or clicked; their `posters` block state (0 to 4) follows the open
  bounties of their nearest hall.
- **Reward**: fixed when posted, `12 × VillageRanks.questRewardFactor(rank)` (a rogue: 16 ×). Paid in emeralds, or
  in CobbleDollars (100 a emerald) where that mod keeps the money.
- **Rogues**: `bounty/Rogues`. The hall's round posts a rogue instead of a captain half the time when
  `Rogues.available()` (the switch, the board's switch, and a filling for the extension point `story/RoguePokemon`).
  Biome groups are `data/<namespace>/bounties/rogues/<id>.json`: `biomes` (a string or a list of biome ids and
  `#tags`) and `species`. A biome named in a group wins over a group that only has one of its tags; groups are read in
  id order. A species the filling says Cobblemon doesn't have is skipped (logged at debug level).
- `compat/cobblemon/CobblemonRogues` fills `RoguePokemon`: it makes the rogue with Cobblemon's `PokemonProperties`
  (`alpha=true` when `PokemonFeatures.ALPHAS` is available, else its size modifier at 1.5), nicknames it, stores the
  bounty's id in the Pokémon's persistent data (`aliveworkplace_rogue_bounty`) and gives it a despawner that never
  despawns. It listens to `BATTLE_VICTORY` (a player's win over a wild actor holding the rogue) and `POKEMON_CAPTURED`,
  and calls `Rogues.beaten`, which pays once and forgets the bounty. A caught rogue loses the id.
- **The glow** is per player: every 10 ticks `Rogues.glow` sends each taker within 32 blocks the rogue's entity flags
  with the glowing bit set (a vanilla `ClientboundSetEntityDataPacket`), and the real flags once they walk away.
- `Rogues.check` (every 40 ticks, from `Bounties.check`) spawns the rogue once a player is within 96 blocks and the
  chunks it roams in have their entities loaded (so a rogue there is never doubled), and walks it back if it strays
  more than 32 blocks.

## Items, blocks, jobs, commands

- Block `aliveworkplace:bounty_board` (states `facing`, `posters` 0 to 4).
- Items `aliveworkplace:wanted_poster` and `aliveworkplace:captains_insignia`.
- New chronicle kind BOUNTY; new chatter topic `bounty`; the journal's Bounties tab is live.

## Switches

- `bountyBoard` (on with Milestone 31): off, nothing new is posted; open bounties can still be taken and paid.
- `rogueBounties` (on with Milestone 31): off, no rogue is posted; open rogue bounties still pay. Never without Cobblemon.

## Saved data

`aliveworkplace_bounties` (per dimension, new): `bounties` (each: `id`, `hall`, `first`, `epithet`, `quirk`, `posted`,
`reward`, `lair`, `captain`, `band`, `fallen`, `killer`, `takers`, and its `hideout`: `spot`, `placed`, `rotation`,
`skipped`, `origin`; a rogue's also `rogue`, its species, and `rogue_level`), `last` (the day each village last posted), `claimed` (paid bounties) and `boards`. Every field has a
default; a world without the file starts with none.

## Decisions

- 31.13: the poster's lines are its lore, written when it is taken (the place and reward don't change after).
- 31.13: the reward is fixed when the bounty is posted, so a village that ranks up doesn't change a poster's promise.
- 31.13: a lair captain's bounty doesn't count towards the 2 open or the 3 days, and is paid on his death (he has no
  insignia: his lair already drops its chest).
- 31.13: the insignia can only be handed in at the village that posted it.
- 31.13: a Night Stalker who has been hurt stays seen for good, even after he heals.
- 31.14: a rogue is paid when beaten or caught, to whoever does it, whether or not they took its bounty (as a lair
  captain is paid to his killer); taking it gives the poster, the map and the glow.
- 31.14: the rogue's level comes from the rank when posted; with Radical Cobblemon Trainers it follows the level cap of
  the nearest player when it appears, and the poster shows the level it was spawned at.
- 31.14: "spawned with Cobblemon's own spawning" is read as made by Cobblemon's own `PokemonProperties` (a true wild
  Pokémon, battleable and catchable) at the spot, rather than waiting for Cobblemon's spawner to roll that species.
- 31.14: bounty rewards are paid in CobbleDollars where that mod keeps the money (the 31.13 insignia paid 0 dollars
  with CobbleDollars installed; fixed with this item).
- 31.13: the 32.21 hook (no captains' bounties in a village at peace or at threat level 0) waits for 32.21.

## Known limits

- The At peace / threat level 0 rule (no captains' bounties in a quiet village, 32.21) isn't applied: such a village
  still posts them (bug B134).
- A hideout with no clear natural ground within 32 blocks of its spot isn't built; its bounty stays up and comes down
  after 7 days.

## Known limits (31.14)

- A rogue knocked out outside a battle (by a mob, or killed by a player where Cobblemon allows it) pays nothing; it
  comes back the next time a player is near while its bounty is up.
- The glow is refreshed every half second; if the rogue catches fire or the like, the glow can flicker off for a moment.

## Proof

- GameTests: `BountyGameTests`: posting rules (`postedFromVillageRankTwoAtMostThreeDaysApart`), one test per quirk,
  the hideout built once a player is near (`theHideoutIsBuiltOnceAPlayerIsNear`), taking a bounty, the insignia paying
  once (`theInsigniaDropsAndPaysOnce`), expiry with the band, the board's posters, save and load, the switch off.
- GameTests: `RogueBountyGameTests` (the eight groups load and a biome finds its group; never posted without Cobblemon,
  saved and loaded, nothing spawned). Compat GameTests: `RogueCompatTests` (species, level and name by biome and rank,
  size or Alpha, through a save; a win pays once; a capture pays once and the catcher keeps it, named; the glow for
  takers within 32 blocks only).
- Showcase scene `bounty_rogue` (Cobblemon): the rogue's poster in the Bounties tab, the glowing Rogue Ursaring, the win paid.
- Showcase scene `bounty`: the board with two posters, the poster in the Bounties tab, the captain and his band, the claim.

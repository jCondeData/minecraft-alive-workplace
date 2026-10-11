# The bounty board and bandit captains

Villages post bounties on named bandit captains; players take them, hunt the captain at his hideout and hand in his
insignia for the reward. ROADMAP 31.13; builds on the quest journal (31.3), the arc engine's `place` (31.4), lairs
(32.3) and reputation (31.11). Part of the 1.5 expansion: switched on when Milestone 31 is complete.

Roadmap items: 31.13

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
- **Quirks**, one per captain: Ironhide (full iron armour, 20 more health), Fleetfoot (Speed II), Crossbow Ace (a
  pillager with a Multishot, Quick Charge II crossbow), Beast Rider (on a ravager), Night Stalker (invisible at night
  until he's hit), Warlord (a band of 5), Hexer (an evoker).
- **The claim**: he drops a **Captain's Insignia** when he dies. Hand it in at a Bounty Board or the Village Hall
  (right-click with it), or with the tab's hand-in button: 12 emeralds times the rank factor, once, to whoever hands it
  in, plus a bounty's standing (+40). The chronicle says who. A second insignia of the same bounty pays nothing.
- **Expiry**: an unclaimed bounty comes down after 7 days and its band leaves in a puff of smoke.
- **Lairs**: a captain holding a lair (32.3) near a village of Village rank is posted too, with his name and his lair's
  place; when he falls his bounty is paid straight to his killer.
- **Gossip**: villagers say where a wanted captain was seen ("They say Grimwald the Red was seen 160 blocks
  north-east.").

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
- **Reward**: fixed when posted, `12 × VillageRanks.questRewardFactor(rank)`.

## Items, blocks, jobs, commands

- Block `aliveworkplace:bounty_board` (states `facing`, `posters` 0 to 4).
- Items `aliveworkplace:wanted_poster` and `aliveworkplace:captains_insignia`.
- New chronicle kind BOUNTY; new chatter topic `bounty`; the journal's Bounties tab is live.

## Switches

- `bountyBoard` (on with Milestone 31): off, nothing new is posted; open bounties can still be taken and paid.

## Saved data

`aliveworkplace_bounties` (per dimension, new): `bounties` (each: `id`, `hall`, `first`, `epithet`, `quirk`, `posted`,
`reward`, `lair`, `captain`, `band`, `fallen`, `killer`, `takers`, and its `hideout`: `spot`, `placed`, `rotation`,
`skipped`, `origin`), `last` (the day each village last posted), `claimed` (paid bounties) and `boards`. Every field has a
default; a world without the file starts with none.

## Decisions

- 31.13: the poster's lines are its lore, written when it is taken (the place and reward don't change after).
- 31.13: the reward is fixed when the bounty is posted, so a village that ranks up doesn't change a poster's promise.
- 31.13: a lair captain's bounty doesn't count towards the 2 open or the 3 days, and is paid on his death (he has no
  insignia: his lair already drops its chest).
- 31.13: the insignia can only be handed in at the village that posted it.
- 31.13: a Night Stalker who has been hurt stays seen for good, even after he heals.
- 31.13: the 32.21 hook (no captains' bounties in a village at peace or at threat level 0) waits for 32.21.

## Known limits

- The At peace / threat level 0 rule (32.21) is not built: 32.21 isn't on `main` yet.
- A hideout with no clear natural ground within 32 blocks of its spot isn't built; its bounty stays up and comes down
  after 7 days.

## Proof

- GameTests: `BountyGameTests`: posting rules (`postedFromVillageRankTwoAtMostThreeDaysApart`), one test per quirk,
  the hideout built once a player is near (`theHideoutIsBuiltOnceAPlayerIsNear`), taking a bounty, the insignia paying
  once (`theInsigniaDropsAndPaysOnce`), expiry with the band, the board's posters, save and load, the switch off.
- Showcase scene `bounty`: the board with two posters, the poster in the Bounties tab, the captain and his band, the claim.

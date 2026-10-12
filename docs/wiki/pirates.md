# Drowned pirates

ROADMAP 32.8. Part of Milestone 32 (1.6), so it is **off for players until the milestone is finished**
(`Expansions.M32`); GameTests and the showcase run with it on. The pirates are a raider culture (see
[Raids and threats](raids.md)) whose lair (see [Lairs and the Defense page](lairs.md)) is a ship on the sea.

Roadmap items: 32.8

## What a player sees

- **A ship at anchor.** By a Town or a City on the coast (an ocean or a beach within 48 blocks of the hall), drowned
  pirates may anchor their **Pirate Ship** 40 to 70 blocks out, on water at least 5 deep, with its bow to the village:
  a two-masted dark oak sloop with black sails, a raised forecastle, a crow's nest on the mainmast, gunports down both
  sides, the anchor chain going into the sea, the captain's cabin at the stern with the loot chest inside, and the
  **Jolly Roger** (a black banner with the white skull) flying over the quarterdeck. Chat says so ("Black sails off
  Thornholm: Captain Silas Brine has dropped anchor 52 blocks south with his drowned crew...").
- **Captain Silas Brine.** A drowned in a deep red leather coat and a black cap, with a trident and 40 more health,
  his name over his head. He stays in his cabin. His crew: drowned with tridents (40%), drowned (30%) and skeleton
  gunners in black leather caps with Flame bows (30%), all called "Drowned pirate".
- **They come ashore.** On a raid night the crew wades in from the water off **the village's beach nearest the ship**,
  never from the land side, so a sea wall or a harbor gate is what holds them. They raid by night only: at dawn they
  go back into the sea (with a splash).
- **The ship burns and sinks.** When the captain falls, his chest is put on a 3 x 3 raft of spruce logs on the water
  between the ship and the village (past the bow, or off a side if the bow's water is taken), and the ship burns and
  sinks over ten seconds, from the mastheads down, with smoke, flames and bubbles. Only the ship's own blocks go: a
  block that isn't the ship's any more (one a player put on deck, or a ship's block a player changed) stays where it
  was. The sea comes back where the hull was. The crew at the ship scatters and no new lair comes for five days.
- **The chest** (`aliveworkplace:chests/pirate_ship`): gold, emeralds, a trident, nautilus shells, often gold nuggets,
  fish, prismarine shards, a map or an enchanted book, now and then a diamond, and one time in ten a heart of the sea.

## How it works

- The culture is the data file `raider_cultures/drowned_pirates.json`: `where` (`coast`, Town and up), weight 30,
  arrival `shore`, hours `night`, the roster, the captain, the lair `camp/pirate_ship` and the loot. On a coastal Town
  the bandits (weight 10) can still come instead: the pirates are three times as likely.
- **The coast is looked at once a day** for each hall (`Conditions.coastToday`): the land doesn't change.
- **A lair on the sea** (`threat/Ships`). A culture's `lair` with a `waterline` (the template's y of the sea's top
  block: 2 for the ship, so it draws three blocks) anchors on water instead of making camp on land: `near` and `far`
  (40 and 70 blocks from the hall), `depth` (5: under every part of the ship as it would lie), `sinks` (it burns and
  sinks when its captain falls) and `captain_at` (where in the template the captain stands: his cabin). The template
  leaves everything round the hull out, so the sea stays; only the hold is dry.
- **Where the crew lands**: of the dry ground at the water's edge in the village (from 8 blocks above the hall to 24
  below it), the spot nearest the ship; they appear in the water up to three blocks off it, towards the ship.
- **Gear with components.** A culture's gear may give an item its components as `/give` takes them
  (`minecraft:bow[enchantments={levels:{'minecraft:flame':1}}]`, `minecraft:leather_helmet[dyed_color={rgb:1908001},unbreakable={}]`).
  The caps are unbreakable so the sun never burns the gunners or the captain through them.
- **A captain with one name.** A culture's captain may have `name` (the lang key of the one name he always has) instead
  of a list of `names`: Captain Silas Brine is always Silas Brine.
- **Strength.** 8 when the ship anchors, 1 more a day, at most 14; at most 8 stand at the ship.

## Switches

| Key | Default | What it does |
|---|---|---|
| `raiderCultures` | `drowned_pirates: true` | false: no pirate ship anchors, and one at anchor sends no raids. |

## Saved data

- `aliveworkplace_ships` (per dimension, new): where each village's ship lies (its structure, origin, turn and the
  sea's height) and the ships sinking now with the time each began, so a restart mid-sinking carries on.
- The lair itself is saved as every lair is (`aliveworkplace_bandit_camps`, 32.3).

## Items, blocks, jobs, commands

- Raider culture `aliveworkplace:drowned_pirates`.
- Structure `aliveworkplace:camp/pirate_ship` (9 x 20 x 27), drawn in `tools/blueprints/bandits.py` (`pirate_ship`).
- Loot table `aliveworkplace:chests/pirate_ship`.

## Decisions

- "40 to 70 blocks out" is measured from the hall, as other lairs' distances are (32.8).
- The captain is always "Captain Silas Brine" (the roadmap names him), not one of twenty names.
- Strength 8, +1 a day, at most 14, and weight 30: the roadmap gives no numbers; a little smaller than the warband.
- The crew's drowned are left as the game makes them (they head for the water by day); the gunners and the captain
  wear unbreakable caps so the sun doesn't kill the lair.

## Known limits

- The gunners' burning arrows don't light roofs yet: that comes with village fires (32.16).
- A ship whose ground is unloaded when its captain falls (he can't be killed there) or mid-sinking goes on sinking
  when it is loaded again, all at once if the ten seconds are past.

## Proof

- `PirateGameTests.aShipAnchorsAndItsCrewWadesAshore`: the three-to-one rule by a coastal Town; the ship on the water
  with its bow to the village, its hull in the sea, its hold dry and the sea round it untouched; Captain Silas Brine
  in his coat in his cabin, his crew of 8, the gunners' Flame bows and caps; the loot chest; the crew appears off the
  beach nearest the ship; a save and load; at dawn they go back into the sea.
- `PirateGameTests.theCaptainFallsAndTheShipSinks`: the chest on a log raft on the water at once; the masts down
  halfway and the hull still there; then every ship block gone, the sea back, the block the test put on deck still
  there; the chronicle line.
- `PirateGameTests.aSinkingShipCarriesOnAfterARestartAndTheSwitches`: a chest taken before the captain fell (no raft);
  a save and load mid-sinking; the lines a player reads; the config switch, the expansion gate, a Village and an inland
  Town.
- `PirateGameTests.whereAShipMayAnchor`: deep enough water under the whole ship, not over the beach, the bow's turn.
- Showcase scene `pirates`: the ship at anchor off a Town at dusk, the crew wading ashore, the ship sinking.

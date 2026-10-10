# The Colony Charter

ROADMAP 33.8 (part of 1.7, "From village to realm"; off until 1.7 is released). A City can found a sister village.
This page is the first step: buying the Colony Charter at the hall and choosing, on its map, where the colony goes.
Sending the settlers (33.9) and the colony's first day (33.10) come next and get their own pages.

Roadmap items: 33.8

## What a player sees

**The Colonies tab.** The minecart on the [Village Hall](village-hall.md)'s screen opens the Trade page
([the price board](price-board.md)); its row of tabs now ends with **Colonies** (a filled map). The tab shows:

- **Buy a Colony Charter**: what it costs (32 emeralds), who pays what ("The treasury pays 20 emeralds, you pay 12
  emeralds"), and, when the village can't found a colony now, the reason in red.
- **Colonies founded: 1 of 3**, and how long until the next one may set out.
- The colony on the road, if there is one, and under these every colony the village has founded, with its day and
  where it lies.

**The charter.** A map-shaped item with a red wax seal. Its tooltip names the village it belongs to ("Of Thornholm:
founds its colony"), and once a spot is chosen, where it is: "The spot: 612 blocks north-east of Thornholm".

**The map.** Right-click the air with the charter and its map opens: the 2,048 blocks round the hall, north up.
Land the server has loaded is drawn like a vanilla map; the rest is plain parchment. Every village with a hall has a
banner (the charter's own village a red one in the middle, the others white), and pointing at a banner names the
village. Two dashed circles are the **ring** a colony may go in, 256 and 1,024 blocks from the hall; a small dashed
circle round every other hall is the 128 blocks a colony must keep from it. Pointing anywhere says how far and which
way the place is and whether a colony can go there. **A click chooses the spot**: a red cross marks it, and the line
under the map says "The colony will go 612 blocks north-east of Thornholm." A click on a place that won't do says why
and leaves the last spot where it was.

**On the ground.** Instead of the map, walk to the place and right-click the ground with the charter: the spot is
where you stand.

**The colony's name.** Rename the charter in an anvil: its tooltip then says "The colony will be called Newbrook".

**"What next?"** A village that could found a colony now (a City, nothing on the road, the wait over, under its
limit) gets the tip "Found a colony".

## How it works

- **Who may buy:** the hall's owner, their friends and operators, as for every realm and colony action. A hall
  nobody owns sells no charter.
- **The price** is 32 emeralds. The treasury pays what it has in whole emeralds, up to the price; the buyer pays the
  rest (in CobbleDollars with the pack). In creative mode the rest is free, and the tab and the message say so. If
  the buyer can't pay the rest, nothing is taken from anyone.
- **What stops the sale**, each with its own sentence: the village is below the colony rank (a City, see the
  switches); it has a colony on the road; it founded one less than `colonyCooldownDays` days ago; it has founded
  `coloniesPerVillage` already.
- **A good spot** is 256 to 1,024 blocks from the hall (measured flat, so the ring is round), in the hall's own
  dimension, and at least 128 blocks from every other village's hall. "Every other village" is every hall the
  caravans know, which is every hall that has had its first round (about half a minute after it is placed).
- **The map** is 128 by 128 pixels, 16 blocks a pixel (a vanilla map's scale 4). The server draws it when the
  charter is used, from the chunks it has loaded at that moment: it loads and generates nothing for it, and never
  touches the world off its own thread. The client only receives the colours and the villages' positions.
- **A click** sends the server the block column under the pointer; the server checks it again and answers. A click
  lands within one map pixel (16 blocks) of the place pointed at, whatever size the map is drawn.
- **The spot's height** is where you stood for a click on the ground. For a click on the map it is the ground's
  height if the server has that place loaded, and the hall's height otherwise; the settlers look for open ground
  nearby when they arrive (33.10).
- **Far from home** the charter is taken at its word: it still works 800 blocks out, where its hall isn't loaded.
  When its hall's place is loaded and no hall stands there, it says "Thornholm has no Village Hall any more" and
  chooses nothing.

## Switches

| Key | Default | What it does |
|---|---|---|
| `colonies` | on from 1.7 (off until then) | Off: no Colonies tab, no charters sold, a charter chooses no spot, and "What next?" never suggests a colony. |
| `colonyRank` | `city` | The rank a village needs to buy a charter: `hamlet`, `village`, `town` or `city`. In the file only (not on the settings screen). Anything else reads as `city`. |
| `colonyCooldownDays` | 7 (0 to 60) | Days a village waits after founding a colony before the next. |
| `coloniesPerVillage` | 3 (0 to 16) | Colonies one village may found in all. |

## Saved data

- On the charter (item component `aliveworkplace:colony_charter`): `hall` (dimension and position), `name` (the
  village's name when it was bought), and `spot` (absent until one is chosen). The colony's name is the item's
  ordinary custom name.
- A new server-wide file, `aliveworkplace_realms` (kept with the overworld's data), which the rest of 1.7 fills. Its
  colonies part, all empty in a world that never had it:
  - `colonies.orders`: colonies getting ready or on the road: `mother`, `spot`, `name` (optional), `state`
    (default `gathering`), `leaves`, `arrives` and `cost` (each default 0). Nothing writes these before 33.9.
  - `colonies.founded`: `mother`, `hall`, `day`. Nothing writes these before 33.10.
  - `cooldowns`: per mother village, `lastDay` (default -1) and `founded` (default 0).

## Items, blocks, jobs, commands

- Item `aliveworkplace:colony_charter` (does not stack). In the creative tab it is blank and says where charters
  come from; only the Colonies tab binds one to a village.
- The Trade page's tab `COLONIES`.
- Packets `colony_charter_open`, `colony_charter_choose`, `colony_charter_answer`.

## Decisions

All 33.8, lane a, 2026-10-10 (also in ROADMAP's Notes):

- `colonyRank` is a word, so it is in the config file only: the settings screen shows switches and numbers.
- The treasury pays in whole emeralds; its hundredths stay in the treasury.
- A charter is sold only while the village could found a colony now. That keeps a player from paying 32 emeralds
  for a charter they can't use for a week.
- The limits are counted from the realms file, which 33.9 and 33.10 write: until those land, nothing in the game
  puts a colony on the road or founds one, so only the rank and the money can stop a sale.
- The charter can't be re-bound by right-clicking another hall, unlike the Ledger: it was paid for by one village.
  Right-clicking a hall with it is left for 33.9 (sending the settlers).
- The spot can be changed as often as you like until the settlers are sent.
- The ring's edges (exactly 256 and 1,024 blocks, exactly 128 from another hall) count as allowed.
- In GameTests colonies are off unless a test turns them on, like caravan sights: other tests count the Trade
  page's tabs and the "What next?" tips.

## Known limits

- The charter does nothing more yet: no settlers leave until 33.9, and no colony is founded until 33.10.
- A hall placed less than a round ago isn't on the map yet and doesn't keep a colony 128 blocks away.
- The map shows only what the server has loaded when you open it: far from players that is parchment. It isn't
  redrawn while it is open.
- A village's banner on the map is red or white, not its Village Banner's colours.
- The map has no zoom, and its text isn't checked for clipping at every GUI scale the way the City Plan's is.
- A charter whose hall was removed still works where the hall's place isn't loaded; it is refused when the settlers
  are sent (33.9).
- With CobbleDollars, the split payment (treasury in emeralds' worth, buyer in dollars) has no compat test yet.
- The map has been seen in the real client only in the showcase scene (filmed locally on 2026-10-10, GUI scale 2,
  five checks passed), on a flat test world: nobody has played with it on real terrain or at other GUI scales yet.

## Proof

- `ColonyCharterGameTests` (11): `theCharterIsRefusedBelowCityAndSoldToACity`,
  `theTreasuryPaysWhatItHasAndThePlayerTheRest` (also: refused without the money, a stranger, a hall nobody owns),
  `aGoodSpotIsKeptAndBadOnesRefused` (too near, too far, near another hall, the map's corner, the ring's edges, the
  ground click, the tooltip's "612 blocks north-east"), `theThreeLimitsStopTheSale`,
  `switchedOffNothingIsSoldOrChosen`, `theCharterOfARemovedHallFoundsNothing`,
  `saveAndReloadKeepTheSpotAndTheRecords`, `renamingInAnAnvilNamesTheColony`,
  `whatNextSuggestsAColonyOnceACity`, `theMapShowsLoadedLandAndEveryVillageWithoutLoadingAnything`,
  `aClickOnTheMapLandsWithin16BlocksOfThePlace`.
- `ConfigGameTests` and `ExpansionGateGameTests` count the three new settings.

Showcase scenes: `colony_charter`

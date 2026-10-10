# The price board

ROADMAP 33.2 to 33.4 (part of 1.7, "From village to realm"; off until 1.7 is released).

## What a player sees

The minecart in the Village Hall's divider opens the **Trade** page. Its first row is a row of tabs. **Routes** is the
trade routes page as it always was. **Prices** is the price board: every trade good by its icon, 28 of them (25
without Cobblemon). On each icon a **gold star** means the village is known for the good, a **red mark** means it's
short of it, and a small arrow in the corner shows how the price moved since yesterday (green up, red down, a grey bar
for steady). The open tab has a green bar under it.

Hovering a good shows:

- the bundle ("A bundle of 16");
- "We sell" (what the village asks for a bundle) and "We pay" (what it gives for one);
- "Up from 1.2 emeralds yesterday", "Down from ..." or "Steady since yesterday";
- "Thornholm is known for it" or "Thornholm is short of it";
- the village on a trade route where the good is dearest ("Dearer in Ashford: 1.4 emeralds") and the one where it's
  cheapest ("Cheaper in Ridgeway: 0.72 emeralds"). Without routes it says there's nothing to compare with.

The hall's name icon gains a line: "Known for: Timber, Wool. Short of: Bread". Villagers talk about it too: a good that
sells well in a village on a route, a glut at home, and something dear. A Village Ledger opens the hall from afar, and
the minecart there reaches the Trade page the same way.

Nothing can be bought or sold on the board yet: that's 33.5.

## How it works

- **The numbers** come from the hall's daily count (33.2, `trade/Economy`): known for and short of (up to 3 each, never
  both), and a price for every good that moves a third of the way toward its target each dawn, kept between half and
  twice its base. They are kept on the village's entry in the caravans' saved list, so a village that isn't loaded
  keeps its last prices and can still be compared with.
- **Two prices.** "We pay" is the good's price there. "We sell" is 10% over it, rounded to the cent (33.5's rule).
- **The arrow** compares today's price with yesterday's, which the count keeps beside it.
- **Dearer and cheaper** look only at villages with a trade route to or from this one that have a price for the good.
  The dearest is named only if it pays more than this village, the cheapest only if it pays less.
- **Money** reads in emeralds to the cent without needless zeros ("0.88 emeralds", "1.4 emeralds", "1 emerald"). With
  CobbleDollars installed it reads in CobbleDollars (100 to the emerald by default).
- **Before the first count** every good stands at its base price, steady, with no star or mark, and the Prices tab says
  the count is still to come.
- **The marks** are drawn by the hall's own screen over the icon (`textures/gui/trade_marks.png`). The icon carries
  which marks it has, and the tooltip says the same in words.
- **Villagers' talk** (`trade/TradeTalk`, said through `people/Chatter` like other village news):
  - "sells well": a good the village is known for costs more in a village on a route; they name the one where the gap
    is biggest;
  - "glut": a good it's known for is under its base price here;
  - "dear": a good is over its base price and the village is short of it, or it's a fifth or more over its base.
- **The tabs** Pacts, Realm and Colonies are kept for later items (33.8 to 33.20) and stay hidden until they land.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageEconomy` | on from 1.7 (off until then) | Off: nothing is counted, the minecart opens the trade routes page as before (no tabs), the name icon has no "Known for" line and nobody talks of trade. The last prices stay saved for when it's back on. |
| `villagerChatter` | on | Off: villagers say nothing, trade talk included. |

## Saved data

Nothing new. The board reads what 33.2 saves on each village's entry in the caravans' list (`aliveworkplace_caravans`):
`knownFor`, `shortOf`, `prices` (each with `cents`, `yesterday`, `nudge`), `priceDay` and `demand`. An entry saved
before 1.7 has none of them and shows every good at its base price.

## Items, blocks, jobs, commands

None of its own. The page opens from the Village Hall's minecart button and through the Village Ledger.

## Decisions

- **The tabs sit in the right half of the first row, Routes first** (33.4, lane a): the Routes tab is the old routes
  page's title in its old place, so the routes page looks and is found as before. The design note had them further
  left.
- **The minecart opens on Routes** (33.4, lane a): the routes page stays one click away; Prices is the tab beside it.
- **Off means the old page** (33.4, lane a): with `villageEconomy` off the hall looks exactly as it did before 1.7.
- **A good's name keeps its capital in talk** ("Our Timber sells well in Ashford"): the server can't lower-case a
  translated name.
- **The treasury isn't on the page yet**: who may collect changes with 33.5, which adds it.

## Known limits

- The Prices tab shows 36 goods. A data pack that adds nine or more of its own pushes the last ones (by `order`) off
  the page.
- A village that has never been counted (no hall round since 1.7) is left out of "dearer" and "cheaper".
- A good whose file was removed stays in the saved lists until the next count but is shown nowhere.

## Proof

- GameTests, `PriceBoardGameTests`: the page with a starred, a marked and an arrowed good and the name icon's line;
  the dearest and cheapest village on the routes; the hall's own count showing on the board; the economy switched off;
  a save and reload; no count yet and no goods at all; the nine lines villagers say; the Ledger from afar; how prices
  read.
- GameTests that still pass unchanged and reach the routes page as a tab: `HallPagesGameTests`,
  `VillageBannerGameTests`, `MerchantPrinceGameTests`.
- Showcase scenes: `price_board` (the page, Timber's tooltip, the name icon) and `hall_pages` (the routes page, now the
  Routes tab).

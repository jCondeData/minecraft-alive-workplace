# The Pokémon Trader

Needs Cobblemon. ROADMAP 28.x (the trader), 28.23 (its own trade screen), 43.2 (the screen in the Village Hall's look).

Roadmap items: 28.23, B312, 43.2

## What a player sees

A villager working at a Trade Board becomes a Pokémon Trader. Right-click one with an empty hand and their trade screen
opens, in the Village Hall's look (43.2): a dark panel with a slate-blue edge, the trader's name in gold at the top and
their book (hover it for the rules of the day) in a framed cell beside it. On the left, a dark list of the day's offers,
one row each: the Pokémon in a framed cell, its name and level in white with a gold ★ if it's shiny, what the trader
wants for it on a gray-blue line under it ("For: any Water, Lv. 20+", or for a special request "For: a Pikachu line,
Lv. 25+") and the ball it comes in at the row's end. On the right, "Your party" in gold over two rows of three framed
cells, and under a thin line what to do next. Click a row (anywhere on it) to pick that offer: its row and cell turn
gold; your Pokémon that won't do are grayed out. Click one that fits and its cell turns gold while the screen asks you
to click again; the second click makes the trade. If a trader has nothing to offer, the list says "No offers today.
Come back tomorrow!". A hint along the bottom says to hover a Pokémon for its details. While the screen is open the trader stays by you and faces you,
as a vanilla villager does at its trade screen, so he can't walk out of reach and close it mid-trade (B312).

## How it works

- Offers are the same all day for everyone and change every in-game day (seeded by the trader and the day). A Novice
  has 1 offer (levels 5 to 15) and a Master 3 (levels 50 to 70, a shiny one time in ten); Experts and Masters add a
  special request for one particular evolution line. The screen shows up to four cards, the most a trader ever has.
  Higher tiers offer later forms: Novices and Apprentices a line's first form, Journeymen an evolved or single-stage
  Pokémon, Experts and Masters only final forms. Only a Master's regular offers are ever shiny (the special request is
  shiny one time in four).
- A Pokémon fits when it is tradeable, of the wanted type (or evolution line) and at the wanted level or higher.
- Each player makes one trade per trader per in-game day (another player can still trade with him that day; you can
  again the next day). The trader gains 6 experience per trade.
- Never offered or asked for: legendary, mythical, Ultra Beast or Paradox Pokémon. Each offer wants a type the offered
  Pokémon doesn't have; any of your Pokémon's types counts (a Water/Ground fits "any Ground"). An untradeable Pokémon
  is refused ("can't be traded").
- A pick waiting for its second click is dropped if that Pokémon leaves your party first, or if the day turns and the
  offers change; clicking again and again trades only once.
- The Pokémon you get has the trader as its original trainer, like an in-game NPC trade. A held item comes back to
  you. Your Pokémon leaves the party before the trader's joins, so even a full party of six trades, and the new one
  takes its place (the PC is only a fallback if the party can't take it).
- The screen closes if you walk more than 8 blocks away, or the trader dies or falls asleep. It won't open during a
  battle.
- The screen (28.23, 43.2) is drawn with the Village Hall's `TableStyle` (no texture of its own since 43.2:
  `textures/gui/pokemon_trader.png` is gone). `PokemonTradeView` holds the layout (a fixed 300x174 panel, which fits
  the smallest screen), and `PokemonTradeView.offerAt` turns a click anywhere on a row into a click on its offer's
  cell. The server decides every click; the screen only draws them.

## Switches

None of its own. Without Cobblemon, a right-click says Pokémon Traders need Cobblemon.

## Saved data

On the trader: the day each player last traded (`POKEMON_TRADES`, default none) and the number of trades made
(`POKEMON_TRADE_COUNT`, default 0). The offers aren't saved: they are worked out again from the trader and the day.

## Items, blocks, jobs, commands

The Trade Board (workstation), the Pokémon Trader profession.

## Decisions

- 28.23 (owner, 2026-10-05): its own screen instead of a chest of icons, like the Village Hall's (30.4a). The two-click
  confirm stays, shown as a pressed-in button and a line of text.
- 43.2 (owner, 2026-11-08, Milestone 43): the screen uses the Village Hall's look like every other screen. The offers
  became list rows (as the list menus of 42.1) and the waiting Pokémon's cell turns gold instead of pressing in; the
  panel stays a fixed size so the server's button positions stay put.

## Known limits

- The "no offers" state only shows if Cobblemon has no Pokémon to offer (an empty species list).

## Proof

GameTests: `PokemonTradeScreenCompatTests` (opens from a right-click and trades, refuses what the player can't pay,
remembers the day's trade after a save and load, `tradeScreenHoldsTheTrader` keeps him by the player), `CobblemonCompatTests.traderSwapsPokemonOnceADay`,
`traderOffersChangeDaily`, `expertTradersMakeASpecialRequest`. QA: `QaTraderOffers1107dCompatTests` (no legends offered or asked for, every tier's counts and levels, a special request takes
the whole line, a second type fits, untradeable refused, a full party trades with the trader as original trainer, one trade a day per
player, later forms at higher tiers, only Masters' offers shiny). `QaPokemonTradeScreenCompatTests` (the cards match the
real offers with name, level, ball, shiny mark and cost; the level boundary from both sides and the held item coming back;
a Pokémon leaving the party between the clicks; click spam and the used-up screen; yesterday's offer refused after the day
turns; walking away and a dead trader closing the screen; every sentence through en_us.json).
`PokemonTradeLayoutGameTests` (43.2: every button inside the panel and no two cells overlapping, a click anywhere on a
row picks that row and nowhere else does, the status lines' room, the panel fits a 320x240 screen, the hint's text).
Showcase scene `trader`.

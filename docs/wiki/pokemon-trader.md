# The Pokémon Trader

Needs Cobblemon. ROADMAP 28.x (the trader), 28.23 (its own trade screen).

Roadmap items: 28.23

## What a player sees

A villager working at a Trade Board becomes a Pokémon Trader. Right-click one with an empty hand and their trade screen
opens (sneak to see their ordinary item trades instead). On the left, the day's offers, one card each: the Pokémon on
its button with its name, level and a gold ★ if it's shiny, the ball it comes in at the card's right end, and what the
trader wants for it ("For: any Water, Lv. 20+", or for a special request "For: a Pikachu line, Lv. 25+"). On the right,
your party. Click a card to pick that offer; your Pokémon that won't do are greyed out. Click one that fits and it
presses in while the screen asks you to click again; the second click makes the trade. If a trader has nothing to offer,
the panel says "No offers today. Come back tomorrow!".

## How it works

- Offers are the same all day for everyone and change every in-game day (seeded by the trader and the day). A Novice
  has 1 offer (levels 5 to 15) and a Master 3 (levels 50 to 70, a shiny one time in ten); Experts and Masters add a
  special request for one particular evolution line. The screen shows up to four cards, the most a trader ever has.
- A Pokémon fits when it is tradeable, of the wanted type (or evolution line) and at the wanted level or higher.
- Each player makes one trade per trader per in-game day. The trader gains 6 experience per trade.
- A held item comes back to you. If your party is full afterwards, the new Pokémon goes to your PC.
- The screen closes if you walk more than 8 blocks away, or the trader dies or falls asleep. It won't open during a
  battle.
- The screen (28.23) is drawn like the Village Hall's: textures/gui/pokemon_trader.png, from tools/textures/art/gui.py.
  The server lays out the buttons and decides every click; the screen only draws them.

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

## Known limits

- The "no offers" state only shows if Cobblemon has no Pokémon to offer (an empty species list).

## Proof

GameTests: `PokemonTradeScreenCompatTests` (opens from a right-click and trades, refuses what the player can't pay,
remembers the day's trade after a save and load), `CobblemonCompatTests.traderSwapsPokemonOnceADay`,
`traderOffersChangeDaily`, `expertTradersMakeASpecialRequest`. Showcase scene `trader`.

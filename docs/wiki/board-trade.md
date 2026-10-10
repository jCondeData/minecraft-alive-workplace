# Trading at the board

ROADMAP 33.5 (part of 1.7, "From village to realm"; off until 1.7 is released). It builds on
[the price board](price-board.md).

## What a player sees

On the hall's **Trade** page, the **Prices** tab is a market. Every good's tooltip ends with what the village can
spare ("Bundles to spare in the Storehouse: 3", or "None to spare (the Storehouse keeps 16 of everything back)") and
what a click will do:

- **You carry a bundle of the good** (16 logs for Timber): "Click: sell a bundle (you carry 40). Shift-click: all it
  will take", and "Right-click: buy a bundle".
- **You don't**: "Click: buy a bundle. Shift-click: all you can".

After a click, the first line of that good's tooltip says what came of it: "You sell 32 × Timber to Thornholm for 2
emeralds. Its treasury can't pay for more.", or why nothing happened ("Thornholm has no bundle of Timber to spare: its
Storehouse keeps 16 of everything back."). The same line shows above the hotbar.

A **gold nugget** in the page's first row is the village's **treasury**: what it holds to the cent ("Treasury: 12.4
emeralds"), its cap, and "Click to collect its whole emeralds", or "Only Jesse and their friends can collect it" for
everyone else.

Without a Storehouse (with a chest by it), the Prices tab's own icon and every good say "The market needs a Storehouse
with a chest", and a click says the same.

In a **protected** village, a stranger who right-clicks the hall gets this page with the Prices tab and the treasury
nugget, and nothing else: no way back to the hall's screen, no Routes tab. So anyone can trade anywhere.

## How it works

- **Selling to the village.** A bundle (the good's `bundle` count, of any of its items, mixed if need be) leaves your
  inventory for the Storehouses' chests, and the treasury pays the good's price ("We pay").
- **Buying from it.** A bundle comes out of the Storehouses' chests and you pay "We sell", 10% over the price, into
  the treasury (up to the treasury's cap; what would go over it is lost, as with any other earnings).
- **16 back.** Like caravans, the chests keep 16 of every item: 31 oak logs spare no bundle, 32 spare one. Each item
  keeps its own 16 (20 oak and 20 birch logs spare 8, not a bundle). A bundle may be made of several of the good's
  items; the good's first item (its icon) goes first.
- **The price moves 2% a bundle**, a cent at least: down when a bundle is sold to the village, up when one is bought
  from it, never below half or above twice the good's base price. A shift-click pays each bundle at the price the one
  before left. It's that day's price: the dawn move goes on from wherever trading left it, and the arrow still
  compares with yesterday.
- **Limits, and what the tooltip says.** The village never pays more than its treasury holds ("Its treasury can't pay
  for more") and never takes what its chests have no room for ("Its Storehouse has no room for more"); a bundle that
  only half fits isn't taken at all. A buyer is stopped by what the chests can spare, by what they can pay ("You can't
  pay 1.14 emeralds for a bundle of Timber") and, on a shift-click, by a full inventory.
- **Money.** With CobbleDollars a trade is to the cent (100 CobbleDollars to the emerald by default) and no emeralds
  change hands. With emeralds a trade settles in **whole emeralds** against the treasury's cents: the seller is paid
  down and the buyer charged up to the whole emerald, and the difference stays with the village. A sale that wouldn't
  come to one emerald isn't made ("... comes to less than a whole emerald here: shift-click to sell several at
  once"), so nobody gives goods away for nothing. A shift-click is settled once for all its bundles: two bundles at
  0.8 and 0.78 pay 1 emerald.
- **What counts as the good.** Its items, but no worn tools or armour, and potions only if they heal (Instant Health
  or Regeneration), so a water bottle isn't a remedy. Stacks keep what they are: a healing potion sold reaches the
  chest as a healing potion. The Remedies icon is a healing potion.
- **Who collects the treasury.** Strangers' trades draw on it, so with the village economy on it's for the hall's
  owner, their friends (`/workplace friend add`) and operators, in every village, protected or not; a hall nobody owns
  stays open to all. It is collected as before on the hall's name icon, or on the gold nugget of the Trade page. The
  name icon's line reads "Treasury: 3 emeralds (only Jesse and their friends can collect it)" for the others.
- **Strangers in a protected village** get the board from the right click itself (`VillageProtection`), whatever
  they hold: a Village Ledger isn't bound and a Name Tag doesn't rename the village. Sneaking with a block in hand is
  still a refused placement.
- **Before the hall's first round** (a hall placed seconds ago, not yet on the caravans' list) the board says it
  isn't open yet. A good that has no price yet trades at its base price.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageEconomy` | on from 1.7 (off until then) | Off: no Trade page, so no trading; a stranger's right click on a protected hall is refused as before; and anyone who can open a hall collects its treasury, as before 1.7. |
| `villageTreasury` | on | Off: the village puts by no takings; the board still pays from and into whatever the treasury holds. |
| `villageProtection` | on | Off: no village keeps anyone out, so everyone gets the whole hall. Who may collect doesn't depend on it. |

## Saved data

Nothing new. A trade changes the hall's `treasury` (and `treasuryTotal` for what it earns), and on the village's entry
in the caravans' list (`aliveworkplace_caravans`) the good's `cents` and `nudge` (the day's 2% steps, sales negative;
back to 0 at dawn), both saved since 33.2.

## Items, blocks, jobs, commands

None of its own. It needs a Village Hall and a Storehouse with at least one chest by it.

## Decisions

- **Every good can be sold and bought, not only what the village is short of or known for** (33.5, lane a): those are
  where it pays, since the price already says so, and the design note's table reads "every good ... click to trade".
- **One click, two meanings** (33.5, lane a): the menu has no hand, so "with the goods" means carrying at least a
  bundle and "empty-handed" means not; the tooltip says which a click will do. A right click always buys, so someone
  carrying Timber can still buy more.
- **Whole emeralds, and no sale under one** (33.1's rounding rule; the refusal is lane a's): paid down, charged up,
  the difference to the village. Selling one 0.8-emerald bundle for nothing would be a trap, so it's refused with a
  hint to sell several at once.
- **The treasury nugget sits in slot 3 of the page's row**, on every tab, between the way back and the tabs.
- **The collecting rule follows `villageEconomy`** (33.1: "waits for the M33 flag"): until 1.7 nothing changes.
- **Healing potions only, and no worn tools** (33.3's note on Remedies; worn tools are lane a's): otherwise water
  bottles and nearly broken swords would empty a treasury.
- **Earnings stop at the treasury's cap**, like the takings and the Merchant Prince's caravan pay.

## Known limits

- The Storehouse keeps 16 of every item, so things that don't stack (tools, potions) are sold by the village only
  from the 17th on.
- With emeralds, buying one cheap bundle at a time costs more than buying several at once (each click is charged up
  to the whole emerald).
- In very large modded storage (drawers), what the village can spare may be undercounted: each slot counts as 64 at
  most.
- A shift-click in a full inventory still buys the first bundle; what doesn't fit drops at your feet.
- Feuds (25% dearer for a rival, 33.20) and the realm's Common Coin (a smaller spread, 33.16) aren't built yet.

## Proof

- GameTests, `BoardTradeGameTests` (9): a sale pays from the treasury and fills the chest; a purchase takes from the
  chest and pays in; the 16 back; the treasury limit and the under-an-emerald refusal; a bundle moves the price 2%
  (with a save and reload, and the dawn after); no room in the chests; no Storehouse, no chest, and the chest broken
  with the page open; water bottles and worn swords; the treasury on the page and who collects, with the economy
  switched off.
- `ProtectionSpecGameTests.aStrangerTradesAtTheBoardAndNothingElse`: in a protected village a stranger's right click
  opens the Prices tab alone; they trade, but can't collect, start a route, leave the page, bind a Ledger or rename
  the village; with the economy off the hall stays shut; in an open village a stranger can no longer collect.
- Compat suite, `BoardTradeCompatTests`: with CobbleDollars a sale pays 150 for 1.5 emeralds and a purchase costs 162,
  to the cent, and an account that can't pay buys nothing.
- `PriceBoardGameTests` still passes; one expectation changed: a good's tooltip now ends with the trading lines (in
  that test's village, "The market needs a Storehouse with a chest").
- Showcase scene: `board_trade` (the treasury before, a bundle of Timber bought, the treasury after).

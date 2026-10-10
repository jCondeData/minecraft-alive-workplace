# Caravans that trade

ROADMAP 33.6 (part of 1.7, "From village to realm"; off until 1.7 is released). It builds on
[the price board](price-board.md) and [trading at the board](board-trade.md).

Roadmap items: 33.6

## What a player sees

A trade route used to send the other village only what its workers were waiting for, free. It still does. Now the
same caravan also **sells**: it takes up to 2 more stacks of goods your village is **known for** (the gold stars on
the Prices tab) to a village that is **short of** them, or that pays at least 10% more for them than yours does.

On the hall's **Trade** page, the **Routes** tab says so for every village in range:

- with the route on: "Our caravan sells there:" and a line for each stack, "Timber ×2 for 2.57 emeralds" (two
  bundles, and what the other village's treasury would pay for them today);
- without a route: "A caravan would sell there:" and the same lines, so you can see which route would pay before you
  open it.

The tab's own icon explains it: "It also takes up to 2 stacks of what we're known for to sell where they're short of
it or pay at least 10% more; their treasury pays ours at their board's price".

When the caravan arrives, the chronicles tell the rest:

- yours: "Sold 32 Timber to Ashford for 2.57 emeralds", and the emeralds are in your treasury (the gold nugget on the
  Trade page);
- theirs: "Bought 32 Timber from Thornholm for 2.57 emeralds".

If the other village's treasury can't pay for everything, it buys what it can and the rest comes home: theirs says
"Our treasury couldn't pay for 16 × Oak Log: Thornholm's caravan took them home", and when the caravan is back yours
says "Our caravan came home from Ashford with 16 × Oak Log unsold". The logs are in your Storehouse again.

## How it works

- **What goes.** After the free goods (up to 4 stacks, as before), up to 2 stacks to sell. Each is whole bundles of
  one item of a good your village is known for, as many as fit one stack and as your Storehouses' chests can spare
  over the 16 of every item they keep back. Timber is 16 logs a bundle, so a stack is up to 4 bundles of one kind of
  log; 48 oak logs spare 2.
- **Where.** Only to a village that is short of the good, or whose board pays at least 10% more for a bundle than
  yours. Goods the other village is short of go first, then those it pays most for.
- **Never what they wait for.** An item the other village's workers are waiting for travels free, as it always did;
  the caravan doesn't sell them more of it.
- **The sale.** On arrival the other village's treasury pays yours, bundle by bundle, at its board's price ("We
  pay"). Each bundle moves both boards 2%: its price down, as if the goods had been sold at its board, and yours up,
  as if they had been bought at yours. So two bundles at 1.3 earn 1.3 + 1.27 = 2.57 emeralds.
- **No rounding between villages.** Treasuries keep hundredths of an emerald, so the sale is exact (in CobbleDollars
  with the pack, the same sums read in CobbleDollars). Your treasury stops at its cap, as with all its earnings.
- **What it can't pay for** goes home in a caravan of its own, which takes the road's time again and unloads into
  your Storehouse.
- **No room in their Storehouse.** Those bundles wait on the road and try again at each of that village's rounds;
  they're paid for when they go in.
- **Your village isn't loaded when the caravan arrives.** The sale still happens; your treasury is paid and your
  chronicle written the next time your hall is loaded, dated the day of the sale.

## Switches

| Key | Default | What it does |
|---|---|---|
| `villageEconomy` | on from 1.7 (off until then) | Off: caravans carry only what the other village waits for, as before, and the routes page shows no sales; goods for sale already on the road come home unsold. |

## Saved data

On the caravans' file (`aliveworkplace_caravans`, per dimension), all optional, so older worlds load unchanged:

- a caravan on the road may carry `sale` (a list of {`good`, `stacks`}) and `back` (it is bringing unsold goods home);
- `sales`: sales a village's caravans made while its hall wasn't loaded ({`seller`, `buyer`, `good`, `items`,
  `cents`, `day`}), booked at its next round.

## Items, blocks, jobs, commands

None new. It uses the Village Hall, the Storehouse and its chests.

## Decisions

- The 2 stacks to sell are on top of the 4 free ones.
- Prices read to the cent ("2.57 emeralds"), as everywhere on the Trade page since 33.4.
- Both boards move when the goods are sold, not when they're loaded.
- With the buying village's hall taken away while the caravan is out, the goods for sale turn round for home. With
  the selling village's hall gone, there's nobody to pay: the goods are unloaded where they arrived.
- The full list is in ROADMAP's Notes, 2026-10-10 (33.6).

## Known limits

- A caravan sells one item of a good per stack (a stack of oak logs, not oak and birch mixed).
- The Routes tab's lines are worked out from what the Storehouses hold now; after today's caravan has left they show
  what the next one would take.
- Nothing walks the road yet: the carter and llamas come with 33.7.
- Other players' villages pay like any other until pacts arrive (33.18).
- A Merchant Prince's caravan pay (29.17) is unchanged; 33.23 folds it into these prices.

## Proof

- `CaravanTradeGameTests` (7): the sale between two halls with the Routes tab's lines and both chronicles; what the
  other village can't pay for coming home; what goes (known for, short of or 10% dearer, whole bundles, 16 kept,
  never what they wait for, two stacks at most); save and reload with a caravan on the road, and a save from before
  1.7; the economy off (before loading and mid-journey); no room at the other end and a seller that's gone; the
  buying hall taken away mid-journey.
- `VillageHallGameTests.caravansCarryWhatAnotherVillageNeeds` passes unchanged.
- Showcase scene `caravan_trade`: the Routes tab with its earnings, then the chronicle line.

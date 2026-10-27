# Trade pacts and alliances

ROADMAP 33.18 (part of 1.7, "From village to realm"; switched on in 1.0 with every other expansion). Villages of different owners deal
through offers: a **trade pact** lets caravans run between them, paid for at board prices; an **alliance** is a pact
whose villages also ride to each other's raids.

Roadmap items: 33.18, B582, B595

## What a player sees

**The Pacts tab.** The minecart on the [Village Hall](village-hall.md)'s screen opens the Trade page; its row of tabs
has **Pacts** (a book and quill) between Prices and Realm. The tab lists, in this order:

- each offer waiting for this village: **Accept a trade pact with X** (or an alliance) and **Decline the offer from X**,
  with who offered it and how long is left;
- each pact and alliance: a click on it turns **gifts** on or off for your side, and **End** next to it ends it;
- every other player's village within caravan range (2,048 blocks, 4,096 with the [Lighthouse](wonders.md)) that has
  no pact yet: **Offer X a trade pact** and **Offer X an alliance**; a village you have a trade pact with gets **Offer X
  an alliance** (B582).

The tab lists only what pressing will accept: an offer is refused as "beyond caravan range" only past the same range
the tab lists by, the offering village's own, written as "2,048" or "4,096" (B595).

**Offers.** An offer reaches the other owner as a letter (in their mailbox, their hands, or waiting at the post office)
and as the Accept and Decline line on their village's Pacts tab. It lapses after 3 days; the one who offered hears of a
decline or a lapse, and their chronicle notes it.

**Routes.** From 1.7, a caravan route (Routes tab) to another player's village needs a pact: without one the village's
line says so and a click does nothing. Your own and your friends' villages need none. Routes set up before 1.7 become
trade pacts when the world loads, so nothing stops.

**Paying.** A pact caravan's cargo of what the other village waits for is paid for at the receiving village's board
price (a bundle's price, part bundles in proportion), as far as its treasury goes; the seller books it like a caravan
sale. With **gifts** on, your caravans ask nothing.

**Alliances.** When an allied village (or a village in an ally's realm) is raided, allies within 1,024 blocks with at
least two guards send relief riders (as in [realm relief](realms.md)); no treasury pays for them; both owners hear of
it.

**Ending.** Either side ends a trade pact at once (its routes both ways stop) or an alliance with a day's notice; the
other owner gets a letter, and both chronicles note it.

## How it works

`realm/Pacts.java` keeps the pacts and offers and settles them each second (lapsed offers, alliances whose notice ran
out, and once per world the old routes). `Pacts.mayRoute` is asked by the Routes tab before it starts a route;
`Caravans.payPact` charges the receiving village when a pact caravan unloads; `Relief.call` adds allies' bands after
the realm's own. Owners are compared with `Realms.ownerOf` (the hall's, or as last seen) and friends count as one owner.

## Switches

| Switch | Default | What it does |
|---|---|---|
| `tradePacts` | on (with 1.7) | Routes to another player's village need a pact; the Pacts tab; pact caravans are paid for. Off: routes need no pact, no tab; pacts stay saved. |
| `alliances` | on (with 1.7) | Alliances can be offered and send riders. Off: none offered, no riders; alliances stay saved. |

## Saved data

`aliveworkplace_pacts` (with the overworld): the pacts (both villages, alliance or not, the notice's end, each side's
gifts), the offers waiting, and whether old routes were made pacts. A world from before 1.7 has none of it and starts
empty.

## Items, blocks, jobs, commands

None new: the Pacts tab on the Trade page, letters through the post.

## Decisions

- Wanted items that are no trade good travel free even on a pact: they have no board price.
- Allies' riders cost nothing: neither their own village's treasury nor a realm treasury pays for them; the raided
  village's own realm pays for its own bands as in 33.17.
- Offering an alliance to a trade-pact partner is allowed: accepting turns the pact into an alliance.
- Caravan range for pacts is the offering village's own caravan range, twice as far with the Lighthouse (35.12), the
  same range its Routes tab and Pacts tab list neighbors by (B595).

## Known limits

- The tab shows up to 36 lines; with many neighbors some offers to make are left off.

## Proof

`PactGameTests`: `anAcceptedPactAllowsRoutesAndPays` (no route without a pact; the letter; Accept on the tab; the
cargo paid, not with gifts; a pact ends at once and its route stops), `aDeclinedOrLapsedOfferChangesNothing`,
`anOldRouteLoadsAsAPact`, `anAllysRaidBringsRelief`, `endingAnAllianceTakesADay` (with save and reload),
`theSwitches`.

QA: `QaPactsRealmEdictsGameTests`: the tab's Decline, End (a pact at once, an alliance with "Ends in 24 hours") and
gifts buttons, the Routes tab's refusal without a pact, gifts on one side only, cargo paid as far as the treasury goes
and booked by the seller, crossed offers, a lapse told, caravan range and the allies' 1,024 blocks from both sides
(`anAllyRidesExactly1024BlocksAndNotABlockMore`), the switches off and back on, the old routes made pacts only between
strangers and only once.

`PactOfferFixesGameTests`: `aTradePactPartnerCanBeOfferedAnAllianceOnThePactsTab` (B582: the tab's alliance offer to a
pact partner, accepted without stopping its routes) and `anOfferButtonTheTabShowsWithTheLighthouseIsntRefusedAsTooFar`
(B595: with the Lighthouse a village 3,000 blocks off is listed and its offer made; "2,048" and "4,096" in the refusals).

Showcase scene `pacts`.

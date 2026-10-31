# Feuds and war

ROADMAP 33.20 (part of 1.7, "From village to realm"; switched on in 1.0 with every other expansion). An owner can fall out with another
player's village: a **feud** ends trade between them and makes each side's board unfriendly to the other's people. On a
server with PvP on, a feud can become a **war**, in which guards fight the rival owners, and nothing else.

Roadmap items: 33.20

## What a player sees

**Declaring.** The [Village Hall](village-hall.md)'s minecart opens the Trade page; its **Pacts** tab (see
[Trade pacts and alliances](pacts.md)) lists **Declare a feud on X** (an iron sword) for every other player's village
within caravan range (2,048 blocks). If that village is in a realm, the feud is with the whole realm; if yours is, your
whole realm is on your side.

**What a feud does.**
- A letter tells the other owner (they also hear it in chat if online).
- Every trade pact, alliance and offer between the two sides ends, and so do the caravan routes; no new route or pact
  can be made while the feud stands.
- Each side's price board pays the rival's owners (and their friends) 25% less for their goods and charges them 25%
  more. The Prices tab shows them their own prices and says why.
- Villagers grumble about the rivals ("I won't buy a thing from Thornholm's people.").
- Both chronicles note it (an iron sword line).

**The tab's feud line.** Each feud shows as **Feud with X**, with how long it has left to fade, or the war's days and
the guards each side has lost. In the last hour these read "Fades in less than an hour without a war" and "At war: less
than an hour left" (B637, B672), not "0 days and 0 hours". Next to it: **Offer X peace** (a white banner), or
**Accept peace with X** once they have offered, and for the side that declared, **Raise the feud with X to war**.

**Peace.** Either side offers peace; a letter goes to the other owner, who accepts on their tab. The feud (and any war)
ends and both chronicles say so. A feud without a war **fades after 7 days** (7 days after its war, if it had one).

**War.** Only when the server allows PvP (`pvp=true` in server.properties) and `feudWars` is on. The side that declared
may raise a feud to war once it has stood a day; a letter tells the other side. The war starts at the **next dawn**
and lasts **3 days**, or until peace is accepted. During it:
- each side's guards treat the other side's owners and their friends as foes while they are inside the guards' own
  village (within 64 blocks of its hall), and go for them;
- those players may fight the enemy village's guards, even in a protected village;
- nothing else changes: blocks, chests, other villagers, golems, animals and the treasury stay protected as before;
- relief riders and allies answer raids only, never a war.

When the war ends, both chronicles record the guards each side lost ("Guards lost: ours 1, theirs 0"); no treasury
changes hands. With PvP off (or `feudWars` off) the war button stays on the tab, grayed, and its tooltip says why it
can't be pressed; pressing it says so too.

## How it works

`realm/Feuds.java` keeps the feuds (`aliveworkplace_feuds`) and settles them each second (wars that ran out are
recorded, feuds fade, and with `feuds` off they end quietly). `Guards.isFoe(entity, guard)` asks `Feuds.warFoe`;
`VillageProtection.mayHurt` lets an enemy player hurt only a guard of a warring village (`Feuds.mayFight`); the price
board (`Board.sell`, `Board.buy`, `TradePage.goodIcon`) asks `Feuds.boardPercent`; `Pacts.mayRoute` refuses routes
between feuding sides; the chatter topic `feud` has three lines. A guard's village is the nearest hall; a side is a
village, or every village of its realm.

## Switches

| Switch | Default | What it does |
|---|---|---|
| `feuds` | on (with 1.7) | Feuds can be declared. Off: none, the Declare buttons go, and existing feuds end quietly. |
| `feudWars` | on (with 1.7) | Feuds can become wars on a PvP server. Off: no wars, even with PvP on. |

## Saved data

`aliveworkplace_feuds` (with the overworld): each feud's two villages (who declared, on whom), when it began, its war's
start and end, whether the war is over, the guards each side lost, and who offered peace. A world from before 1.7 has
none and starts empty.

## Items, blocks, jobs, commands

None new: buttons on the Pacts tab, letters through the post, a chatter topic and a chronicle kind (`FEUD`).

## Decisions

- No tribute when a war ends (the owner's open call; default none): only the guards lost are recorded.
- A side's "people" are its villages' owners and their friends; other players are neutral and pay the usual prices.
- One war per feud: once fought, only peace (or fading) is left.
- An operator, or anyone who rules the guard's own village, is never the guards' foe.

## Known limits

- With many neighbors and feuds the tab can run out of room and leave some buttons off.

## Proof

`FeudGameTests`: `aFeudStopsRoutesAndMovesBothBoards` (the tab's Declare button, the letter, pact and routes end, both
boards 125%/75% for the rival and not for a neutral player, the price tooltip, chronicles, the chatter topic),
`peaceEndsAFeudAndOneFadesAfterAWeek`, `warIsRefusedWithPvpOff` (too soon, PvP off with the button's reason, feudWars
off, the other side; then war from the next dawn and its letter), `aWarsGuardsFightTheEnemyOwnerOnly` (foe only in war,
only the enemy owner, only in the village; the enemy's attack hurts a guard but not a farmer; no block broken, no chest
opened; no relief riders; a fallen guard counted; PvP off mid-war; the war ends after 3 days with both chronicles and
no treasury moved), `feudsSaveAndTheSwitches`. QA: `QaFeudLastHourBugTests` (4: the war and fading lines at 500, 1, 999, 1000, 23999 and 24000 ticks left, B637/B672). `QaHoursLeftGameTests.theWarButtonsHoursLeftCountAPartHourAsAWholeOne` (the war button 500, 1500 and 1000 ticks short of a day: "1 hour", "2 hours", "1 hour" left). `QaFeudGameTests` (9, lane d): the war button and the Realm tab's vote line at 1, 999, 1000 and 1001 ticks left, who may feud whom (not your own or a friend's village; an ally's alliance ends), a feud on a realm's village is with the whole realm, peace offered and accepted once, a feud fading at exactly 7 days and a war ending on time, the tab's lines, the save round trip and every feud sentence. Showcase scene `feud`.

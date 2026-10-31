# Founding a realm

ROADMAP 33.11 (part of 1.7, "From village to realm"; switched on in 1.0 with every other expansion). A Town or better founds a realm with
itself as the capital and gathers its owner's other villages into it: a Barony, then a County, a Duchy and a Kingdom.
The realm's treasury, research, relief riders and edicts (33.13 on) build on this page.

Roadmap items: 33.11, 33.12, 33.13, 33.14, 33.15, 33.16, 33.17, 33.22, 33.22a

## What a player sees

**The Realm tab.** The minecart on the [Village Hall](village-hall.md)'s screen opens the Trade page
([the price board](price-board.md)); its row of tabs has **Realm** (a gold helmet, a crown) between Pacts and
Colonies. Everyone who may open the hall may read it.

- **Not in a realm:** one button, **Found a realm**: what it takes ("A Town founds the Barony of Thornholm, with
  itself as the capital"), the four ranks, and, when the village can't, why in red ("Thornholm must be a Town to found
  a realm; it is a Village."). A click founds it: "Thornholm has founded the Barony of Thornholm!", fireworks over the
  hall, and a line in the chronicle.
- **In a realm**, the first row holds:
  - the realm's **title** ("the Barony of Thornholm"): its rank, how many villages of how many it may hold, the
    capital, and what the next rank takes. Click it with a Name Tag named in an anvil to rename the realm: "the Barony
    of Eldmark". The Name Tag is used up, as on a mob.
  - the realm's **arms**: a white banner ("No arms yet") until someone clicks it with a banner; then that banner.
    A Village Banner works too. The banner is not used up.
  - **The realm treasury** (an emerald block, 33.13), beside the arms: what it holds of its cap ("Holds 12.30 of 256
    emeralds"), the levy, what it pays for, then **the last 20 movements**, newest first, each with its day and reason
    ("Day 12: +0.30 Levy from Ashford", "Day 12: +3.00 Given by Alex", "Day 13: -1.25 Realm research"; in green, out in
    red). **Anyone** may give: click it with emeralds to give one, shift-click to give the stack; with CobbleDollars in
    the pack, a click with an empty hand gives 100 CobbleDollars (shift-click: 6,400) from your balance. Only the
    capital's owner, their friends and operators **take**: right-click for an emerald, shift-right-click for a stack
    (paid in CobbleDollars with the pack). A stranger's right-click: "Only Rowan and their friends can take from the
    treasury of the Barony of Thornholm."
  - **The levy** (a hopper, 33.13): "Levy: 10%". At the capital its owner (their friends, operators) clicks it to step
    through 0, 5, 10 and 20%; the other members' owners online read "The levy of the Barony of Thornholm is now 20%."
    On a member's tab it only shows the levy.
  - **Leave the realm** (a barrier), or at the capital **Give up the crown**.
- The rows under it list the **members**, the capital first with a gold helmet, the others with the realm's banner:
  each one's rank, villagers and wellbeing, how far and which way it is from this hall, its owner and the day it
  joined. At the capital, its owner (their friends, operators) can **click a member to let it go**.
- After the members, at the capital and for those who rule it, every village they may add: a lime banner, **"Add
  Ashford"**, with its rank, where it is and its owner. One click adds it: "Ashford has joined the Barony of
  Thornholm."

**Other players' villages (33.12).** After the villages to add, the capital's tab lists, for those who rule it, the
villages within reach that belong to another player (not a friend): a book and quill, **"Invite Ashford"**, with its
rank, where it is and its owner. A click sends that owner a letter ("Rowan, who rules the Barony of Thornholm, invites
Ashford to join the realm...") the way a colony's letter goes: into their mailbox if they have one, into their hands if
they're online, otherwise it waits for them at any Postal Desk. They're also told in chat if online. The capital's tab
then shows "Ashford: invited", with the days and hours left. On **Ashford's** Realm tab (outside a realm), under Found a
realm, each open invitation is a pair of buttons: **Join the Barony of Thornholm** (the realm's banner: who invites,
its size, where the capital is, the time left) and **Decline**. Joining works like being added (its colonies come
too); declining or not answering within **3 days** leaves it out. The capital's owner hears either way, and its
chronicle notes the invitation, a decline or a lapse.

**Choosing the capital (33.12).** On a member's tab, **Put Ashford forward as capital** (a bell): any member that's a
Town or better, by its owner (their friends, operators). If one player owns every member, the capital moves at once.
Otherwise a **vote** runs for a day: every member's owner online hears of it, and every member's tab shows it ("Vote:
move the capital to Ashford?", the count so far, the hours left) with **Vote for Ashford** and **Vote to keep
Thornholm**. A player's vote counts once for every member they own; they may change it until the day is up. Then the
move needs more villages for it than against: a village that didn't vote counts for the current capital, and a tie
keeps it ("The vote is over, villages for Ashford: 1, against: 1. The capital stays at Thornholm."). The proposer's
villages start out for it. A realm has one vote at a time. When the capital moves, the realm keeps everything (its
id, name, arms, rank, and later its treasury, research and edicts), fireworks go up over the new capital, every
member's owner online reads "The capital of the County of Eldmark moves to Ashford: ..." and every loaded member's
chronicle notes it. From then on the realm's decisions (adding, inviting, letting go, the name, the arms, and the levy,
research and edicts of later items) are for whoever rules the new capital. Anyone may still leave their own village
out at any time.

**Rank ups.** When the members make a higher rank, fireworks go up over the capital's hall, every member's owner who
is online reads "Rejoice! the Barony of Thornholm has risen to the County of Thornholm.", every loaded member's
chronicle notes it, and the realm takes its new title everywhere.

**The hall's name icon** says "Capital of the County of Thornholm" at the capital, "In the County of Thornholm" in a
member.

**"What next?"** A Town or better with an owner, in no realm, gets the tip "Found a realm".

## How it works

- **Who may do what** (the design note's rules): founding, adding, letting go, renaming and the arms need
  the right to rule the **capital** (the hall's owner, their friends, operators). Leaving needs the right to rule **that
  member**. A hall nobody owns founds nothing.
- **Which villages can be added:** in the capital's dimension, within 2,048 blocks of the capital's hall (measured
  flat), in no realm, its hall still there, and owned by the player or by someone who has made the player a friend
  (`/workplace friend add`). Another player's village that isn't a friend's is invited instead (33.12).
- **Colonies join their mother's realm by themselves:** a colony founded while its mother is in a realm joins on
  arrival (33.10), and when a village founds a realm or is added to one, the colonies it founded (and theirs) join
  with it, while there is room and they are in the same dimension.
- **The ranks**, counted from the members' ranks:

  | Rank | Villages | And |
  |---|---|---|
  | Barony | 1 or more | (a realm is founded a Barony) |
  | County | 3 | the capital a Town or better |
  | Duchy | 5 | the capital a City |
  | Kingdom | 8 | the capital and one more member a City |

  The count runs whenever a member joins or leaves and in every member's hall round, so a capital that becomes a City
  raises the realm. A rank lower just changes the title, without fireworks.
- **At most `realmMaxMembers` villages** (12). Adding past it is refused ("... already holds 12 villages, as many as a
  realm may.").
- **A broken member hall** takes its village out of the realm; the capital's owner is told if online. **A broken
  capital** passes the crown to the biggest member that's a Town or better (by rank, then villagers); the realm's
  title follows the new capital (unless it was renamed). With no Town left, the realm ends and every member is free.
  The capital giving up the crown does the same.
- **Villages that aren't loaded** are never loaded for the realm. Every hall's round keeps what it saw of its village
  (name, owner, rank, villagers, wellbeing) in the realms file, and the tab, the rank count and the candidates read
  that. A village that hasn't had a round yet is "Not counted yet" and counts as a Hamlet.
- **The arms** are kept on the realm for the realm's other parts: 33.17's relief riders paint their shields with them
  (`Realms.arms`, the same painting as guards' shields in the village's colors), and the realm's map banners and
  caravans use them in later items. A realm founded by a village with a Village Banner's colors starts with them.

- **Invitations and votes** are settled every five seconds while realms are on: an invitation lapses after 3 days
  (72,000 ticks) and a vote ends after a day (24,000 ticks), both by the overworld's clock. A vote whose village has
  left the realm, become the capital already, or dropped below a Town is void. A realm that ends takes its invitations
  and vote with it; a village that joins any realm loses its other invitations; a broken hall loses its invitations.
- **The levy (33.13)** is taken in each member's hall round, from the day's takings
  ([the village treasury](village-hall.md), `Treasury.round`) before the rest goes into the village's own treasury:
  ten workers in a well-kept Hamlet take in 3 emeralds a day, so at 10% the realm gets 0.30 and the village 2.70.
  Days the hall missed (up to three) are levied together. Rounded down to the hundredth of an emerald. A levy of 0
  takes nothing and writes no movement.
- **The treasury's cap** is by the realm's rank: 256 emeralds for a Barony, 512 for a County, 1,024 for a Duchy and
  2,048 for a Kingdom. A levy that doesn't fit stays with the village; a gift that doesn't fit is cut to what does
  ("The treasury of ... is full: it holds 256 emeralds." when nothing fits). A rank down keeps what it holds above the
  new cap; it just takes no more until it's under.
- **Spending (33.13 on):** `RealmTreasury.spend(server, realm, cents, reason)` pays out in hundredths of an emerald and
  writes the reason in the movements; false (and nothing paid) if the treasury holds less. Realm research, relief
  riders, a member's colony short of money (from a Duchy), edicts and M35's Wonders pay through it.
- **The realm's state moves with the capital** because it lives on the realm, not on the capital village. Anything a
  later item keeps per village can follow the move through `Realms.onCapitalMoved`.

## Switches

| Key | Default | What it does |
|---|---|---|
| `realms` | on from 1.0 | Off: no Realm tab, nothing founded, added, invited, joined or voted on, no name icon line or tip, and the arms aren't used; invitations don't lapse and votes don't end. Realms, invitations and votes stay saved and come back when it is turned on. A broken hall still leaves its realm. |
| `realmMaxMembers` | 12 (2 to 32) | Villages one realm may hold. |
| `realmRelief` | on (with 1.7) | Raided realm villages get riders from the capital and near members (33.17). |

## Saved data

In the server-wide file `aliveworkplace_realms` (kept with the overworld's data), next to the colonies' part, all
empty in a world that never had them:

- `realms`: a list of `id` (UUID), `name` (a component, absent: the capital's name), `capital` (dimension and
  position), `members` (a list of `hall` and `joined` day, the capital first), `arms` (absent, or `base` color and
  `patterns`), `rank` (`barony`, `county`, `duchy`, `kingdom`: the rank last counted, so a rank up is celebrated once)
  and `founded` (day). 33.13 adds `levy` (percent, default 10), `treasury` (hundredths of an emerald, default 0) and
  `ledger` (the last 20 movements, oldest first: `day`, `cents`, negative going out, and `reason`, a component),
  so an older file's realms load with the default levy and an empty treasury.
- `known`: what each hall's last round saw: `hall`, `name`, `owner` (absent: none), `ownerName`, `rank`,
  `villagers`, `wellbeing` (percent), `seen` (game time). Forgotten when the hall is broken.

- `invites` (33.12): `realm` (id), `village` (dimension and position), `owner` (UUID), `sent` (game time).
- `votes` (33.12): `realm` (id), `candidate` (dimension and position), `started` (game time) and `ballots` (a list of
  `hall` and `yes`).
- `laws` (33.22): one per realm that proclaimed a realm edict: `realm` (id), `edicts` (the realm edicts in force:
  `id` and the `day` proclaimed), `reforms` (as a hall's: `id`, `step`, `reformed`, `nextDay`), `counts` (each reform's
  count so far, by edict id), `lastRaidDay`, `lastDay` (the realm's last dawn round) and `festivalDay` (the last realm
  festival counted), each -1 by default. Absent in older files: a realm with no edicts.
- `studies` (33.15): one per realm with research: `realm` (id), `research` (the realm topics' `levels`, the `current`
  one, its `progress` and whether it is `paid`, the same shape as a hall's research) and `capital` (the capital's
  scholars' levels as its last round saw them, read by members while it isn't loaded). Absent in older files: a realm
  starts with no realm research.

## Items, blocks, jobs, commands

- The Trade page's tab `REALM`.
- Chronicle kind `REALM` (a gold helmet): founded, joined, left, let go, a member lost, rank ups, renamed, new arms,
  the crown passed, the realm ended.
- 33.12's letters are ordinary written books (the mail's letters), sent through the post office's parcels.
- No new item, block, job or command (`/workplace realm info|disband` for operators is in the design note and was not
  built).

## Decisions

All 33.11, lane a, 2026-10-11 (also in ROADMAP's Notes):

- A realm of one is already a Barony: the spec names a just-founded realm "the Barony of Thornholm". "Barony (2
  villages)" is read as the rank a realm of two holds, so the first rank up is to a County; the founding itself has
  fireworks too.
- "That they or a friend own" means the village's owner is the player, or has made the player a friend
  (`Friends.mayDirect`, the same rule as builders' orders); operators don't add other players' villages.
- A plain click (nothing held) on the title or the arms does nothing; their tooltips say what to click with.
- Letting a member go is a plain click, as the design note's mock-up says; no confirmation.
- The capital "leaving" gives up the crown, which passes as it does when the capital's hall is broken.
- A renamed realm keeps its name when the crown passes.
- In GameTests realms are off unless a test turns them on, like colonies: other tests count the Trade page's tabs
  and the "What next?" tips.

## Decisions (33.12)

Lane a, 2026-10-11 (also in ROADMAP's Notes):

- A vote is one ballot per member village, cast by its owner (not their friends or operators); a player owning several
  members votes for all of them with one click. The proposer's villages start out for the move.
- "If one player owns every member": whoever may put the village forward (that owner, their friends, operators) moves
  it at once.
- 33.11, written before the realm's treasury, research and edicts (33.13 on): everything a realm has is kept on its
  record in `aliveworkplace_realms`, so it moves with the capital by itself; what a later item keeps per village
  follows `Realms.onCapitalMoved`. The GameTest checks the realm's name, arms, rank and members move.
- The invitation's line on the tab is two buttons (Join, Decline), not one line with a right click, so it works by plain
  clicks like the rest of the tab.
- The capital's owner who invites a friend's or their own village through the invitation path just adds it, as before.
- An invited village that isn't loaded is invited by what its last round saw (owner, rank), like adding.

## Decisions (33.13)

Lane a, 2026-10-11 (also in ROADMAP's Notes):

- The treasury, levy and movements are kept on the realm's record, so they move with the capital and never need it
  loaded. A realm that ends (its capital lost with no Town to take the crown) loses what its treasury held.
- Taking is a right-click (one emerald; shift: a stack of 64), so a left click stays giving, like the hall's own
  treasury collect. With CobbleDollars, giving with an empty hand charges your balance an emerald's (or a stack's)
  worth; emeralds in hand still work.
- A levy that doesn't fit under the cap stays with the village rather than being lost.
- The capital's owner steps the levy with one click (0, 5, 10, 20, then 0 again), the way other counters on the hall
  step; each change is told to the other members' owners online.
- Movements name villages and players by their names at the time (kept as text), so they read the same after a
  rename.

## The Royal Hall (33.14)

**What a player sees.** On the capital's Realm tab, where a member's tab has "Put forward as capital", a lectern
button: **Royal Hall**. Its lines say what it does ("Finished within 32 blocks of the capital's hall, it doubles what
the realm treasury holds (the Royal Hall II: three times)"), what stands now, and for the capital's owner (friends and
operators too) "Click with a Blank Blueprint in your inventory to draw it". A click takes one Blank Blueprint (none in
creative) and gives the Royal Hall blueprint in the capital's style ("Drawn: Royal Hall. Have a builder put it up within
32 blocks of Thornholm's hall"); with a Royal Hall standing it draws the **Royal Hall II**; with the II standing it says
so. The treasury's tooltip adds "The Royal Hall by the capital: 2 times the cap" (or 3), and its "Holds x of y" counts
the raised cap. On the village map a Royal Hall is marked as a hall.

**The builds** (`tools/blueprints/realm.py`, renders as drawn and in the five styles in `docs/media/royal-hall-front.png`,
`-back.png`, `royal-hall-2-front.png`, `-back.png`):
- Royal Hall (`realm/royal_hall`, 19 x 16 x 25): a stone great hall (15 x 17) with pilasters and buttresses, tall
  windows four to a side, a deepslate gable roof front to back with a rose window in the front gable, a gabled porch
  with the realm's red-and-gold banners; inside a red runner to a two-step dais with the seat of state (a dark oak
  throne on gold) under banners, banners down both walls, benches, a minstrels' gallery over the door (ladder), beams
  with hanging lanterns; behind the dais the vault, a lower annex with four chests.
- Royal Hall II (`realm/royal_hall_2`, 27 x 22 x 25, the upgrade on the same origin): a four-floor keep tower with a
  crenellated top and the realm's banner on a pole, and a treasury wing with chests along both walls, a counting table
  and barred windows, both through doors from the hall's east bays.

**How it works.** `RoyalHalls.multiplier` looks at the build site manager's finished buildings whose origin is within
32 blocks of the capital's hall: a Royal Hall II (any style, `BlueprintStyles.base`) gives 3, a Royal
Hall 2, else 1, and 1 with realms off. `RealmTreasury.cap(server, realm)` is the rank's cap times that, used by the levy,
gifts and the icon. Nothing new is saved: the finished-buildings list is already saved, so a hall taken down, or a
capital moved to another village, changes the cap at once. `realm/` blueprints are kept out of the Blueprint Table
(`BlueprintLibrary.isWorldgenPiece`), like `research/`.

**Decisions (33.14).**
- The cap goes by the finished-buildings list instead of a saved flag on the realm, so it can't drift from what
  stands (a hall broken or replaced) and older saves need nothing.
- "Within 32 blocks" is measured to the build's origin (its front corner), as the village map and other building
  checks do.
- The drawn blueprint is in the capital's style (the first of its finished buildings' styles that the hall comes in),
  as the colony's starter builds are.
- A treasury already over a lowered cap (the hall came down) keeps what it holds; the levy and gifts stop until it is
  under the cap again.
- The Colony Camp's flag is a plain white banner: the realm's arms can't go on a placed template's banner without new
  code in the arrival, left for the arms work.

## Shared research (33.15)

**What a player sees.** In a realm, a member's research screen (sneak-right-click a Scholar) shows each topic of the
scholars' tree at the higher of its own level and the capital's; where the capital knows more, the topic's tooltip
says **From the capital: Drill Journeyman** in aqua, and a topic the capital has fully researched can't be taken up
again ("Drill is already fully known, from the capital."). The member's guards hit harder with the capital's Drill, its
markets get the capital's Commerce, and so on: every bonus that asks `Research.at` gets the shared level. The member's
own levels are unchanged and only they count toward its rank.

A member's scholars with nothing chosen at home (and no Legend's tree to help with) work on the capital's current
topic at half pace ("helping the capital with Swift Hands Novice"), or on the realm topic while the capital has none.

The capital's research screen has a **Realm** tab (a gold helmet) at the right end of its bottom row. It shows the
realm topics: the realm's title and treasury in the top icon, then each topic with its description, level, its price
("Next level: 16× Paper, 2× Book, and 8 emeralds from the realm treasury") and what it needs first. The capital's
owner, their friends and operators click one to choose it ("The realm's scholars will research Royal Roads next.");
others are told "Only the capital's owner and their friends choose the realm's research." The capital's scholars take
it up when they have nothing of their own: they pay the paper and books from their chests and the village store and
the emeralds from the realm treasury (a movement "Realm research: Royal Roads"); while the treasury is short they
wait ("waits for 16 emeralds from the realm treasury"). A finished level is told to every member's owner online
("the Barony of Thornholm: the realm's scholars finished Royal Roads Novice. Caravans between the realm's villages
arrive 25% sooner a level") and written in the capital's chronicle.

The realm's eight topics (33.16; they replaced 33.15's six interim ones). Every level costs 16 paper, 8 emeralds from
the realm treasury and 4,800 research points, and levels after the first 2 books as well:

| Topic | Levels | Each level | Needs |
|---|---|---|---|
| Royal Roads (a dirt path) | 2 | caravans between the realm's villages arrive 25% sooner (`caravan_speed` 25) | |
| Common Coin (a gold nugget) | 2 | the board's spread 3 points narrower: it sells at 107%, then 104% of what it pays (`board_spread` 3) | Royal Roads I |
| Royal Post (a feather) | 1 | parcels between mailboxes in the realm's villages go out at noon as well as at dawn (`noon_post`) | Royal Roads I |
| Ferry Charter (an oak boat) | 1 | Travel Tickets between posts in the realm's villages cost half, at least an emerald, for members' owners and their friends (`ticket_discount` 50) | Royal Post I |
| Levies (iron horse armor) | 2 | relief bands one rider bigger per village (`relief_riders` 1; 33.17's bands read it) | |
| Charters (a map) | 2 | a Colony Charter costs a quarter less (32, 24, 16 emeralds), and the settlers bring 16 more of each supply (`colony_charter`) | Royal Roads II |
| Concord (a bell) | 2 | wellbeing 5% higher in every member village (`wellbeing` 5); the pace it makes still tops out at 25% faster | Levies I |
| Granaries (a hay bale) | 1 | at dawn a member whose store holds under 16 meals is sent up to 32 by caravan from the member with the fullest store, which keeps 16; this caravan needs no route and isn't one of the three (`granary`) | Concord I, Royal Roads II |

A save that had chosen one of the interim topics drops it quietly (paid or not) and the realm chooses again; levels of
the interim topics stay in the file but count for nothing.

**How it works.** `realm/RealmResearch` reads `data/<ns>/realm_research/<topic>.json` on every data load (`/reload`
too): `icon` (an item, default a book), `name` and `description` (components), `levels` (1 to 10, default 1),
`points`, `paper`, `books` and `emeralds` (a level each; `books_from_level`, default 1, is the first level that costs
books), `needs` (topic id to level) and `effect`, one effect of the
[edicts' toolbox](edicts.md) (`work_pace`, `market_traders`, `sickness`, `food_use`, `raid_chance`, `xp`, `wellbeing`,
`loot_luck`, `flag`...) that counts once a level. A pack's topic is kept by its full id, ours by path. A broken file is
logged and skipped; one that needs a topic that isn't there is skipped too.

- `Research.at` asks `Research.SHARING`, which `RealmResearch.shared` fills: the capital's levels come live from its
  hall while its chunk is loaded, else from `studies[].capital`, kept by the capital's round.
- The realm topics' effects join the edicts' sum of every member hall (`CivicEffects.EXTRA`), so every system that
  reads the edicts' effects (and the research trees' `TreeEffects`) sees them. They are summed again when a topic
  finishes, on a reload, and at least every 5 seconds (a member joining or leaving).
- The capital's scholar: their own topic first, then the realm topic, then a Legend's tree. A member's: their own,
  then a Legend's tree, then the capital. A member's help fills the capital's topic up to its last point, never
  past it: the capital's scholar finishes it at their desk (the chat line and Architecture's blueprint come from there).
- The `realms` switch off: nothing is shared, no Realm tab, no realm effects; the realm research stays saved.
- 33.16's own effect types (`realm/RealmEffects`) are read through `RealmResearch.effects(server, realm, type)`, which
  needs no loaded hall: `caravan_speed` by `Caravans.trip` (both villages of one realm), `board_spread` by
  `TradePage.sellPercent`, `noon_post` by `PostOffice.noon` (the overworld's noon; the mailbox's village and the
  recipient's mailbox's village of one realm), `ticket_discount` by `TravelNetwork.fare(server, from, to, player)`,
  `relief_riders` by `RealmResearch.reliefRiders` (and `RealmResearch.level(level, hall, "levies")`), `colony_charter`
  by `Colonies.cost` and `Settlers.load`, and `granary` by `RealmResearch.granaries` at each overworld dawn. Concord
  uses the toolbox's `wellbeing`, so it goes through the edicts' sum like 33.15's effects.

## Relief in a raid (33.17)

**What a player sees.** When a village of a realm is raided (our night raids and bandit raids, a siege of Milestone 32,
or a vanilla pillager raid at it), the realm sends help. The capital always sends a band; every other member in the
same dimension within 1,024 blocks with 2 guards or more sends one too. A band is one rider for every 3 guards the
village has (at least 1, at most 3, plus one per level of the realm's Levies research), 6 riders at most in all. After
a ride of a tick per 4 blocks (at least 30 seconds) they come on saddled horses to the village's edge on their home's
side; when the capital itself is raided they muster before its finished Royal Hall. Each is named for their home
("Ashford's guard"), wears iron and carries a shield painted with the realm's arms (plain without arms). Players in
the village are told "2 guards from Ashford ride in to help Brookfield!".

They fight as guards anywhere in the raided village. When the raid is over they stay a minute more (until dawn at the
latest), then ride off with their horses in a puff. Each rider costs the realm treasury an emerald, paid when the band
sets out (a movement "2 riders from Ashford to help Brookfield"); a member's band the treasury can't pay stays home,
and the capital's comes anyway. While its riders are away, the sender's hall's Guards icon says **2 guards away
helping Brookfield**; the sender's chronicle notes "2 guards rode to help Brookfield in a raid" and the raided
village's "2 guards from Ashford rode in to help against the raid".

**How it works.** `guard/Relief` watches each second: a realm member whose chunk is loaded and that is raided
(`VillageRaids.raided`: a raid in `ThreatData`, which sieges are too, or a vanilla raid) calls `Relief.call` once a
raid; anything else may call it as well. Guards are counted among the villagers round the sender's hall (not
mercenaries or riders); a member that isn't loaded counts none, so only the capital comes from an unloaded village.
Riders are guards on a stay like mercenaries (`MERCENARY_UNTIL`, until dawn) with a saved `relief_rider` attachment
(the village helped, their home); each tick after the raid they cut their stay to a minute. Their horses carry the tag
`aliveworkplace_relief_horse` and go when nobody rides them. Levies (33.16) is read as the realm research topic
`levies` through `RealmResearch`; until it exists it adds nothing.

- The `realmRelief` switch (on with 1.7) off, or `realms` off: nobody comes.
- Riders don't count as the village's own (no beds, no babies, not hired mercenaries).

## Realm edicts (33.22)

**What a player sees.** The capital's **Book of Edicts** (the hall's lectern button, or a Village Ledger used while
sneaking) gets a third row: the **realm slots**. A Barony or County has 1, a Duchy 2, a Kingdom 3, and a finished Royal
Hall II by the capital adds one more; the others show a gray dye saying what opens them ("Opens when the realm is a
Duchy, or with the Royal Hall II"). The header says "Realm edicts: 1 in force of 2 edicts (the Duchy of Thornholm)".
Below the village's eight edicts the capital's list has the six **realm edicts**, each with its boost (green), its cost
(red), its reform and the count across the realm ("Across the realm: 7 of 12 caravans between members"). As with a
village's edicts, click one once to choose it and again to proclaim it; click one in force to lift it, which is refused
until it has stood 3 days. Only the capital's owner, their friends and operators may; a member's own Book shows the
same row to read ("The capital's Book of Edicts can lift it") and lists no realm edicts. A village's book can't
proclaim one ("Common Market is a realm edict: the capital proclaims it in its Book of Edicts."). Proclaiming, lifting,
a lapse and a reform are told to every member's owner online and written in every loaded member's chronicle.
The Book's tooltips are wrapped to fit the screen (33.22a): the hall's screen breaks any line wider than half the screen
(120 to 240 GUI pixels) between words, keeping its colors, so a long description such as the Royal Highway's never
runs off the edge.

Under the Common Market the realm's own people (the owner of a member, their friends and operators) buy at the board's
price, and strangers still pay the 10% spread (33.22a). The board charges each buyer their own price, the Prices tab's
first line tells each viewer theirs ("Under the Common Market the realm's people buy at that price too; strangers pay
10% more"), and "You can't pay …" names the price that buyer would have been charged.

| Edict | Boost | Cost (from the realm treasury where it's money) | Reform: the capital's quest, and the count |
|---|---|---|---|
| Royal Highway | caravans between members carry 2 more stacks and arrive 25% sooner | an emerald a day for each route between members | Pave the highway: 128 stone bricks; 12 caravans between members |
| Common Market | every member's board pays the members' average price; the realm's people buy without the spread | the levy 5 points higher | Weights and measures: 8 gold ingots, then 16 paper; 20 board trades in members |
| Levy of Arms | every member sends relief, wherever it is, in bands twice as big (12 riders at most) | each member pays 2 emeralds a day into the realm treasury | Arm the levy: 12 iron swords; 3 raids beaten with relief |
| Realm Festival | every member holds its festival on the capital's day; the good mood lasts 3 days | 10 emeralds a member for each festival | Lanterns for the square: 32 lanterns; 3 realm festivals held |
| Crown's Peace | no feud can be declared on a member; bandits never camp within 128 blocks of one | every member's takings 10% lower | Clear the roads: 16 monsters cleared round any member; 7 days without a raid on any member |
| Royal Charter | members found colonies from Town rank and wait half as long (7 days: 4) | each colony costs the realm treasury 32 emeralds more | Draft the charter: 16 paper, then 4 books; 2 colonies grown to Village rank |

The reform's steps go up one a day on the capital's quest page (a book and quill, like a village's reform), and the
count is kept while the edict is in force (it starts again when it is proclaimed again). When both are done the realm
has reformed it for good: fireworks over the capital, "the Barony of Thornholm has reformed the realm edict Royal
Highway with Pave the highway", and from then on the boost stays and the cost is gone, also if it's lifted and
proclaimed again.

**How it works.** `realm/RealmEdicts`: the six are ordinary edict files with `"scope": "realm"`
(`aliveworkplace:royal_highway`, `aliveworkplace:common_market`, `aliveworkplace:levy_of_arms`,
`aliveworkplace:realm_festival`, `aliveworkplace:crowns_peace`, `aliveworkplace:royal_charter`); `Edicts.all` lists
only the village's, `Edicts.realm` these. A reform may carry a `count` (`what` and `times`): `caravans_between_members`
(a caravan sent between members), `board_trades` (a player's trade at a member's board), `raids_beaten_with_relief`
(a raid on a member that riders set out for is over and the hall stands), `realm_festivals` (a member's feast, once a
day), `days_without_raid` (days since it was proclaimed or a member was last seen raided) and
`colonies_at_village_rank` (colonies founded by members that are a Village or more now). The realm's dawn round pays
the Royal Highway's upkeep (one route each way counts once, sister routes too); an upkeep the treasury can't pay makes
the edict lapse ("the realm treasury hadn't the 1.00 emeralds of its upkeep"), and a realm that lost a slot loses its
newest. The members' dues come out of each member's own treasury at its morning takings, as far as it has them and
the realm treasury has room. The Realm Festival's share is paid when a member plans its festival; without it there is
no festival and the village is disappointed. The Royal Charter's share is checked before a charter is sold and paid
with it. Effect types (`caravan_carry` (named apart from the guilds' `carry`), `caravan_speed`, `realm_upkeep`, `member_dues`, `common_board`, `member_spread`,
`levy`, `relief_reach`, `relief_band`, `festival_together`, `festival_mood_days`, `realm_festival_cost`, `no_feuds`,
`no_bandit_camps`, `takings`, `colony_rank`, `colony_cooldown`, `colony_cost`) are read by the system each changes.

- `realms` or `villageEdicts` off: realm edicts do nothing and none is proclaimed; those in force stay saved.
- A new capital (33.12) takes them, with their reforms; the old capital's steps come down at its next round.

## Known limits

- Bands still on the road and the hall's "away" line are kept in memory only: a restart
  mid-ride loses the band (already paid) and the village, if still raided, calls again; riders who have arrived are
  saved and still leave on time. A rider's horse that dies drops its saddle.
- Granaries only move meals between members whose halls are loaded at dawn and in one dimension; Concord, like the
  33.15 effects, is off with the `edicts` switch off (the other seven topics aren't).
- Realm research effects reach the edicts' toolbox, so with the `edicts` switch off they are off too (the scholars'
  levels are still shared). A `work_pace` realm topic would be named "the ... edict" in a worker's pace line; the mod's
  topics don't use it.
- Only the scholars' tree is shared, not the Legends' trees.
- The members' villagers and wellbeing are as their last round saw them (every half minute while loaded); a village
  far from players shows its last numbers.
- Fireworks go up only if the capital is loaded at the rank up (a rank up counted in a far member's round has its
  chat line but no fireworks nobody would see).
- More than 27 members and candidates together don't fit the tab (with the default 12 members, at most 15
  candidates show, nearest first).
- Votes and invitations are counted by the overworld's clock; a vote's last hours show rounded up.
- The invitation letter is written in the server's language (English), like the settlers' letter.
- Realm edicts: the Levy of Arms' "wherever it is" still needs a member's hall loaded to count its guards (an unloaded
  member sends no band, as before). The capital's quest page shows 4 reform steps at most, the village's first. A
  common board's price moves the village's own price when traded.

## Proof
- QA: `QaRealmFoundingGameTests` (13, 33.11-33.13): a friend founding from the tab; the 2,048-block reach on a line and a
  diagonal, one dimension; 12 villages even by invitation; each rank from both sides and the rise told to every owner;
  the crown passing with the treasury, or given up by leaving; two realms inviting one village; the time left in
  singular and plural; a candidate's hall broken mid-vote; the 5% and 20% levies; who may give and take; the cap
  following the rank; the treasury with realms off.
- QA: `QaHoursLeftGameTests.theVotesHoursLeftCountAPartHourAsAWholeOne`: a capital vote 1500, 1001, 1000 and 24000 ticks
  from its end reads "About 2 hours", "2 hours", "1 hour" and "24 hours left" on the Realm tab (a part hour is a whole one).
- `RealmTopicsGameTests` (9, 33.16): `theRealmRowShowsTheEight` (the eight, their prices at levels 1 and 2, icons,
  tooltips and needs on the capital's Realm row, an old save's interim topic dropped), `royalRoadsSpeedCaravans` (I and
  II, realms off), `commonCoinNarrowsTheSpread` (I and II), `royalPostDeliversAtNoon` (not without it, the apples in
  the mailbox), `ferryCharterHalvesTickets` (owner, friend, at least an emerald, a stranger, realms off),
  `leviesCountRiders` (I and II, realms off), `chartersCheapenColonies` (32, 24, 16; the treasury's share; the
  settlers' load), `concordRaisesWellbeing` (I and II, the 25% pace top), `granariesFeedTheHungry` (not without it,
  32 by caravan from the fullest store, no route taken, not to a member with enough).
- QA: `QaRealmResearchGameTests` (11, 33.15-33.16): every topic refused before what it needs (and from a member's
  hall), then open once it's known; 2.99 of 3 emeralds in the realm treasury refused with nothing taken, exactly 3
  paid, the villages' own treasuries never touched; a member keeps its own higher level, gets the capital's higher one,
  its rank unchanged, and loses the shared levels (and, within 5 seconds, the realm topics' effects) on leaving; an idle
  member scholar at half pace on the realm topic, not with a topic of its own; a member's help fills the capital's topic
  to its last point without finishing it; a topic part-way through a save; a real `/reload` keeping the levels, the
  chosen topic and its effects; every topic's effect missing in a village of the same owner outside the realm (caravans,
  the board, the noon post through the post office, tickets, relief riders, wellbeing, charters); the ticket floor (10,
  2, 1 emeralds); a member's charter cheaper too; Granaries at exactly 16 meals (nothing) and 15 (32 from the fullest
  store, not the capital's smaller one).
- `ReliefGameTests` (3, 33.17): `aRaidBringsTheRealmsRiders` (two bands after exactly 600 ticks, 3 emeralds paid,
  mounted, named, the realm's arms, the home's side, saved and loaded, a zombie killed by a rider, gone with their horses after the
  raid, the hall line and both chronicles), `noRealmNoSwitchNoMoney` (no realm, `realmRelief` off, an empty treasury
  brings only the capital's band, once a raid, band sizes), `aVanillaRaidCallsThemToo`.
- `QaReliefGameTests` (7, QA lane d, 33.17): the reach counts at it and not one block past, hired mercenaries don't
  count as guards, a raided capital is helped by its members and never by itself, one relief (and one payment) a raid
  and a new one for the next, `realmRelief` turned on mid-raid calls at the next check, a raid over before the band
  comes sends it home after a minute, every line with 1 and 2 riders and a fallen rider's horse gone, a rider loaded
  mid-relief still goes home after the raid.
- `RealmBuildsGameTests` (33.14): `theColonyCampPlacesWhole`, `theCapitalDrawsTheRoyalHall` (the tab's button, refused
  without a Blank Blueprint, the drawn line, hidden from the table, a hall on the map, the II next, nothing after),
  `theRoyalHallRaisesTheCap` (40 blocks off: no; styled: twice, gifts up to 512; II: three times; realms off; taken
  down), `buildsTheRoyalHall`, `buildsTheRoyalHallII` (a builder with the materials stocked).
  `ColonyArrivalGameTests.settlersArriveAndFoundTheColony` checks the hall stands on the Colony Camp's platform under
  its awning.

- `RealmGameTests` (9): `foundARealmAndAddOnesOwnVillages` (through the tab, a friend's village too, the members'
  lines, the name icons), `onlyATownFoundsARealm` (the refusal, a stranger, the "What next?" tip),
  `cantAddAStrangersVillage` (also too far, the cap, added twice, a stranger ruling our realm),
  `leaveAndLetGo`, `ranksAreCountedWithTheRankUp` (the four ranks, fireworks, the chat line, the title, a rank down, the
  hall's round), `theCapitalsHallBroken` (a member's hall, the crown passing twice, the realm ending),
  `nameArmsAndSaveAndReload`, `coloniesJoinTheirMothersRealm`, `theSwitchOff`.
- `ConfigGameTests` and `ExpansionGateGameTests` count the two new settings.
- `RealmInviteGameTests` (7, 33.12, two owners): `anAcceptedInvitationJoins` (the Invite button, the letter in hand,
  the chat line, Join on the neighbor's tab, decisions stay with the capital, leaving), `aDeclinedOrLapsedInvitationDoesnt`
  (Decline from the tab, the letter waiting at the post office, the lapse at 3 days, a late answer refused),
  `aVoteWithAMajorityMovesTheCapital` (Put forward on the tab, the vote's line and buttons, 2 against 1, the realm's
  name, arms and rank moving, fireworks, the chronicle in every member, the new capital's owner ruling),
  `aTieKeepsTheCapital` (also a Village refused, a stranger, an outsider, changing a vote, not voting counted for the
  capital), `oneOwnerMovesTheCapitalAtOnce`, `invitesAndVotesSurviveSaveAndReload`, `theSwitchOff`.

- `RealmTreasuryGameTests` (6, 33.13): `tenWorkersPayATenth` (0.3 of 3 emeralds at 10%, two days, none out of the
  realm, the tab's two icons), `theLevyAtZero` (the levy button 10, 20, 0; nothing paid at 0; a stranger and a member's
  tab can't change it; realms off), `theCapHolds` (the four caps, a levy and a gift past the cap),
  `aStrangerGivesButCantTake` (click, shift-click, the wrong item, a right-click refused; the owner takes one and a
  stack), `theMovementsAreListed` (newest first with day and reason, a spend, only 20 kept), `itSurvivesSaveAndReload`
  (and an older file's realm).

- `RealmResearchGameTests` (3, 33.15): `membersGuardsUseTheCapitalsDrill` (Drill III from the capital: 30% harder,
  `Research.at`, own levels and rank unchanged, "From the capital" on the screen, no taking it up again, the capital's
  round keeps its levels, realms off), `idleMemberScholarHelpsTheCapital` (half pace, twice; not an unpaid topic; not
  with work of their own; not out of the realm), `aRealmTopicIsPaidFromTheTreasuryAndFinishes` (the test datapack's
  topic on the Realm tab, a stranger refused, an empty treasury and missing paper wait, paid with its movement,
  finished with the chat line, the effect in every member, not in the rank, a reload of the topics, a save of the realm
  file, realms off and on).

- `RealmEdictGameTests` (9, 33.22): `theCapitalsBookShowsRealmSlots` (the six, slots by rank, the capital's header,
  free and locked slots, two clicks proclaim, the second finds no slot, the member's book reads it and lists none, a
  village refused, lifted only after 3 days), `royalHighway` (25% sooner, 2 more stacks between members only, an
  emerald a day for the route, a lapse when the treasury is empty, the reform's quest and 12 caravans, then no upkeep),
  `commonMarket` (the average price on both boards and the tooltip, no spread for the owner, 10% for a stranger, a 15%
  levy, two steps a day apart and 20 trades, the levy back to 10), `levyOfArms` (bands of 2 and 4, the dues, three
  raids beaten counted as they end, no dues after), `realmFestival` (the capital's day, no festival without the realm's
  10 emeralds, paid, 3 days of good mood, one realm festival a day, free after the reform), `crownsPeace` (a feud
  refused, bandit camps within 128 blocks, takings 10% lower, 16 kills round a member count at the capital, a raid
  starts the 7 days again, whole takings after, a feud allowed once lifted), `royalCharter` (a Town may, 4 days' wait,
  refused without the realm's 32 emeralds, paid with it, two colonies at Village rank, nothing after),
  `savedAndMovedWithTheCapital` (save and reload, an older file, a new capital keeps them), `switchedOff` (`realms` and
  `villageEdicts` off).

- QA: `QaPactsRealmEdictsGameTests` (33.22, 33.22a): `aRealmEdictStandsThreeDaysNotTwo` (the Book refuses on day 2, lifts on day 3), `onlyTheCapitalsRulersProclaimAndOnlyOnce` (another member's owner refused, the capital owner's friend may, not at a member, not twice), `aDuchyHasTwoSlotsAndTheRoyalHallIIAddsOne` (2 slots, a third with the II, the newest lapsing when it comes down, a Kingdom with the II holding four), `theHighwaysUpkeepIsAnEmeraldARouteAndExactMoneyPays` (each way counts once, a non-member route not at all; exactly enough pays, one hundredth short lapses and takes nothing), `aReformOutlivesLiftingAndAnUnreformedCountStartsAgain`, `aRealmEdictReachesAJoinerAndLeavesWithALeaver`, `theCommonMarketsAverageAndItsPeople` (the average leaving out a member without a price, another member's owner and a friend at the board's price, a stranger and outsiders 10% more, lifted back), `theLevysDuesGoAsFarAsTheMoneyAndTheRoom`, `theCrownsPeaceReaches128BlocksNotOneMore`, `everyPactAndRealmEdictSentenceIsTranslatedInAmericanEnglish`.

Showcase scenes: `realm`, `realm_invite`, `realm_treasury`, `realm_research`, `realm_edicts`

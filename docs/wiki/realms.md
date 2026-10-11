# Founding a realm

ROADMAP 33.11 (part of 1.7, "From village to realm"; off until 1.7 is released). A Town or better founds a realm with
itself as the capital and gathers its owner's other villages into it: a Barony, then a County, a Duchy and a Kingdom.
The realm's treasury, research, relief riders and edicts (33.13 on) build on this page.

Roadmap items: 33.11, 33.12, 33.13, 33.14, 33.15, 33.17

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
  (`Realms.arms`, the same painting as guards' shields in the village's colours), and the realm's map banners and
  caravans use them in later items. A realm founded by a village with a Village Banner's colours starts with them.

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
| `realms` | on from 1.7 (off until then) | Off: no Realm tab, nothing founded, added, invited, joined or voted on, no name icon line or tip, and the arms aren't used; invitations don't lapse and votes don't end. Realms, invitations and votes stay saved and come back when it is turned on. A broken hall still leaves its realm. |
| `realmMaxMembers` | 12 (2 to 32) | Villages one realm may hold. |
| `realmRelief` | on (with 1.7) | Raided realm villages get riders from the capital and near members (33.17). |

## Saved data

In the server-wide file `aliveworkplace_realms` (kept with the overworld's data), next to the colonies' part, all
empty in a world that never had them:

- `realms`: a list of `id` (UUID), `name` (a component, absent: the capital's name), `capital` (dimension and
  position), `members` (a list of `hall` and `joined` day, the capital first), `arms` (absent, or `base` colour and
  `patterns`), `rank` (`barony`, `county`, `duchy`, `kingdom`: the rank last counted, so a rank up is celebrated once)
  and `founded` (day). 33.13 adds `levy` (percent, default 10), `treasury` (hundredths of an emerald, default 0) and
  `ledger` (the last 20 movements, oldest first: `day`, `cents`, negative going out, and `reason`, a component),
  so an older file's realms load with the default levy and an empty treasury.
- `known`: what each hall's last round saw: `hall`, `name`, `owner` (absent: none), `ownerName`, `rank`,
  `villagers`, `wellbeing` (percent), `seen` (game time). Forgotten when the hall is broken.

- `invites` (33.12): `realm` (id), `village` (dimension and position), `owner` (UUID), `sent` (game time).
- `votes` (33.12): `realm` (id), `candidate` (dimension and position), `started` (game time) and `ballots` (a list of
  `hall` and `yes`).
- `studies` (33.15): one per realm with research: `realm` (id), `research` (the realm topics' `levels`, the `current`
  one, its `progress` and whether it is `paid`, the same shape as a hall's research) and `capital` (the capital's
  scholars' levels as its last round saw them, read by members while it isn't loaded). Absent in older files: a realm
  starts with no realm research.

## Items, blocks, jobs, commands

- The Trade page's tab `REALM`.
- Chronicle kind `REALM` (a gold helmet): founded, joined, left, let go, a member lost, rank ups, renamed, new arms,
  the crown passed, the realm ended.
- 33.12's letters are ordinary written books (the mail's letters), sent through the post office's parcels.
- No new item, block, job or command (`/workplace realm info|disband` for operators is in the design note, not built
  yet).

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
- The realm's treasury, research and edicts don't exist yet (33.13 on). Everything a realm has is kept on its record in
  `aliveworkplace_realms`, so it moves with the capital by itself; 33.13 on should keep their state there too (or follow
  `Realms.onCapitalMoved`). The GameTest checks the realm's name, arms, rank and members move.
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
("Next level: 24× Paper, 4× Book, and 16 emeralds from the realm treasury") and what it needs first. The capital's
owner, their friends and operators click one to choose it ("The realm's scholars will research Royal Charter next.");
others are told "Only the capital's owner and their friends choose the realm's research." The capital's scholars take
it up when they have nothing of their own: they pay the paper and books from their chests and the village store and
the emeralds from the realm treasury (a movement "Realm research: Royal Charter"); while the treasury is short they
wait ("waits for 16 emeralds from the realm treasury"). A finished level is told to every member's owner online
("the Barony of Thornholm: the realm's scholars finished Royal Charter Novice. One more trader on market days in every
member village a level") and written in the capital's chronicle.

The mod's realm topics:

| Topic | Levels | A level costs | Each level | Needs |
|---|---|---|---|---|
| The King's Peace | 2 | 24 paper, 4 books, 16 emeralds, 4,800 points | night raids 15% less likely (`raid_chance` 0.85) | |
| Royal Charter | 2 | 24 paper, 4 books, 24 emeralds, 4,800 points | one more market-day trader (`market_traders` 1) | |
| Crown Granaries | 2 | 16 paper, 2 books, 16 emeralds, 4,800 points | villagers eat 5% less (`food_use` -5) | |
| Royal Physicians | 2 | 24 paper, 6 books, 24 emeralds, 7,200 points | illness a fifth less likely (`sickness` -20) | The King's Peace I |
| Court of Scholars | 3 | 32 paper, 6 books, 32 emeralds, 7,200 points | 10% more XP (`xp` 10) | Royal Charter I |
| Realm Feasts | 2 | 24 paper, 4 books, 32 emeralds, 7,200 points | wellbeing 5 points higher (`wellbeing` 5) | Crown Granaries I |

**How it works.** `realm/RealmResearch` reads `data/<ns>/realm_research/<topic>.json` on every data load (`/reload`
too): `icon` (an item, default a book), `name` and `description` (components), `levels` (1 to 10, default 1),
`points`, `paper`, `books` and `emeralds` (a level each), `needs` (topic id to level) and `effect`, one effect of the
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

## Known limits

- No edicts yet (33.21, 33.22). Bands still on the road and the hall's "away" line are kept in memory only: a restart
  mid-ride loses the band (already paid) and the village, if still raided, calls again; riders who have arrived are
  saved and still leave on time. A rider's horse that dies drops its saddle. `relief` hasn't been filmed yet (GitHub
  films it after the push).
- Realm research effects reach the edicts' toolbox, so with the `edicts` switch off they are off too (the scholars'
  levels are still shared). A `work_pace` realm topic would be named "the ... edict" in a worker's pace line; the mod's
  topics don't use it.
- Only the scholars' tree is shared, not the Legends' trees. `realm_research` hasn't been filmed yet (GitHub films it
  after the push).
- `realm_treasury` hasn't been filmed yet (GitHub films it after the push). Nor has `realm_builds` (33.14).
- The members' villagers and wellbeing are as their last round saw them (every half minute while loaded); a village
  far from players shows its last numbers.
- Fireworks go up only if the capital is loaded at the rank up (a rank up counted in a far member's round has its
  chat line but no fireworks nobody would see).
- More than 27 members and candidates together don't fit the tab (with the default 12 members, at most 15
  candidates show, nearest first).
- The rank-up fireworks and the tab have been seen only in the showcase scene's checks; it hasn't been filmed yet
  (GitHub films it after the push). The same goes for `realm_invite` (33.12).
- Votes and invitations are counted by the overworld's clock; a vote's last hours show rounded up.
- The invitation letter is written in the server's language (English), like the settlers' letter.

## Proof
- `ReliefGameTests` (3, 33.17): `aRaidBringsTheRealmsRiders` (two bands after exactly 600 ticks, 3 emeralds paid,
  mounted, named, the realm's arms, the home's side, saved and loaded, a zombie killed by a rider, gone with their horses after the
  raid, the hall line and both chronicles), `noRealmNoSwitchNoMoney` (no realm, `realmRelief` off, an empty treasury
  brings only the capital's band, once a raid, band sizes), `aVanillaRaidCallsThemToo`.
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
  the chat line, Join on the neighbour's tab, decisions stay with the capital, leaving), `aDeclinedOrLapsedInvitationDoesnt`
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

Showcase scenes: `realm`, `realm_invite`, `realm_treasury`, `realm_research`

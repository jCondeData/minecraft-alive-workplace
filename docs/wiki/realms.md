# Founding a realm

ROADMAP 33.11 (part of 1.7, "From village to realm"; off until 1.7 is released). A Town or better founds a realm with
itself as the capital and gathers its owner's other villages into it: a Barony, then a County, a Duchy and a Kingdom.
The realm's treasury, research, relief riders and edicts (33.13 on) build on this page.

Roadmap items: 33.11

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
  - **Leave the realm** (a barrier), or at the capital **Give up the crown**.
- The rows under it list the **members**, the capital first with a gold helmet, the others with the realm's banner:
  each one's rank, villagers and wellbeing, how far and which way it is from this hall, its owner and the day it
  joined. At the capital, its owner (their friends, operators) can **click a member to let it go**.
- After the members, at the capital and for those who rule it, every village they may add: a lime banner, **"Add
  Ashford"**, with its rank, where it is and its owner. One click adds it: "Ashford has joined the Barony of
  Thornholm."

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
  (`/workplace friend add`). Another player's village that isn't a friend's waits for invitations (33.12).
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

## Switches

| Key | Default | What it does |
|---|---|---|
| `realms` | on from 1.7 (off until then) | Off: no Realm tab, nothing founded, added or joined, no name icon line or tip, and the arms aren't used. Realms stay saved and come back when it is turned on. A broken hall still leaves its realm. |
| `realmMaxMembers` | 12 (2 to 32) | Villages one realm may hold. |

## Saved data

In the server-wide file `aliveworkplace_realms` (kept with the overworld's data), next to the colonies' part, all
empty in a world that never had them:

- `realms`: a list of `id` (UUID), `name` (a component, absent: the capital's name), `capital` (dimension and
  position), `members` (a list of `hall` and `joined` day, the capital first), `arms` (absent, or `base` colour and
  `patterns`), `rank` (`barony`, `county`, `duchy`, `kingdom`: the rank last counted, so a rank up is celebrated once)
  and `founded` (day). Later items add their keys with defaults.
- `known`: what each hall's last round saw: `hall`, `name`, `owner` (absent: none), `ownerName`, `rank`,
  `villagers`, `wellbeing` (percent), `seen` (game time). Forgotten when the hall is broken.

## Items, blocks, jobs, commands

- The Trade page's tab `REALM`.
- Chronicle kind `REALM` (a gold helmet): founded, joined, left, let go, a member lost, rank ups, renamed, new arms,
  the crown passed, the realm ended.
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

## Known limits

- Another player's village (not a friend's) can't join yet: invitations and the capital vote are 33.12.
- No treasury, levy, research, relief riders or edicts yet (33.13 to 33.17, 33.21, 33.22); the arms are stored for them.
- The members' villagers and wellbeing are as their last round saw them (every half minute while loaded); a village
  far from players shows its last numbers.
- Fireworks go up only if the capital is loaded at the rank up (a rank up counted in a far member's round has its
  chat line but no fireworks nobody would see).
- More than 27 members and candidates together don't fit the tab (with the default 12 members, at most 15
  candidates show, nearest first).
- The rank-up fireworks and the tab have been seen only in the showcase scene's checks; it hasn't been filmed yet
  (GitHub films it after the push).

## Proof

- `RealmGameTests` (9): `foundARealmAndAddOnesOwnVillages` (through the tab, a friend's village too, the members'
  lines, the name icons), `onlyATownFoundsARealm` (the refusal, a stranger, the "What next?" tip),
  `cantAddAStrangersVillage` (also too far, the cap, added twice, a stranger ruling our realm),
  `leaveAndLetGo`, `ranksAreCountedWithTheRankUp` (the four ranks, fireworks, the chat line, the title, a rank down, the
  hall's round), `theCapitalsHallBroken` (a member's hall, the crown passing twice, the realm ending),
  `nameArmsAndSaveAndReload`, `coloniesJoinTheirMothersRealm`, `theSwitchOff`.
- `ConfigGameTests` and `ExpansionGateGameTests` count the two new settings.

Showcase scenes: `realm`

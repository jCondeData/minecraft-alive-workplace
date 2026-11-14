# Friendship

ROADMAP 31.5. Part of the 1.5 expansion: switched on when Milestone 31 is complete. Gifts ([Gifts](gifts.md)),
heart events and keepsakes ([Heart events](heart-events.md)) and personal requests ([Personal requests](personal-requests.md))
all add to the points kept here, and the hearts' rewards (cheaper trades, festival gifts, letters) are in
[Reputation and titles](reputation.md) (31.12).

Roadmap items: 31.5

## What a player sees

Every named villager in a village with a Village Hall keeps a friendship with each player: 0 to 1000 points, shown as
ten hearts of 100 points each. Look at one within 6 blocks and the action bar shows their name and your hearts, for
example "Dara ♥♥♥♡♡♡♡♡♡♡" (red for full hearts, gray for empty). When your points rise, hearts puff over them.

You earn points by doing things for a villager: trading with them, finishing a quest they posted, handing in what
they are waiting for, killing a monster that just hurt them, being at their wedding or their festival, or doing a
personal request of theirs. Hitting them costs points. Hearts never fade, so nobody has to visit to keep them.

Open the Village Hall and click a villager (the hall's tooltip): it shows "Your hearts", their name day and their two
best friends among the players. Players two and three never see each other's points; each player has their own.

## How it works

- **Who keeps friendships**: a villager that is alive, has a custom name (a name tag, or a name the mod gave them) and
  lives in a village with a hall. Unnamed villagers, and named ones outside a hall's village, keep none: nothing is
  added, no hearts are shown, a Gift is turned down.
- **Points** stay within 0 to 1000. A heart is 100 points, so 0 to 99 is no hearts and 1000 is ten. Points are per
  player, per villager.
- **Favors**. Most count once a day per player and villager (the world's day, the same count the chronicle uses): a
  second one the same day adds nothing, the first one after the day turns counts again.

| Favor | Points | Limit | What counts |
|---|---|---|---|
| Trade | +5 | once a day | Completing a trade with them (any villager that keeps a friendship). |
| Hall quest done | +40 | once a day | You finish a quest of the hall's village (giver `hall`) that they posted. |
| Hand-in | +10 | once a day | You hand in something towards a quest they posted, or something they are waiting for on a requests board (the Storehouse's board too). One daily count covers both. |
| Defended | +15 | any number of times | You kill a monster that hurt them within the last 10 seconds (200 ticks, exactly; one tick later is too late). Every villager it hurt in that time counts it. |
| Wedding | +30 | once a day | You are inside the village's area when two villagers marry: both count it. |
| Festival | +10 | once a day | While a festival is on in the village, you are in its area; every villager who came to that day's festival counts it. |
| Personal request | +150 | once a day | A personal request of theirs done ([Personal requests](personal-requests.md)). |

- **A hit costs 50.** A player hurting them costs 50 points, at most once a minute (1200 ticks: a hit 1199 ticks after
  the last one costs nothing, one at 1200 costs again). Hits when you have no points with them cost nothing and do not
  start the minute. A hit the villager's armor or a shield blocked does not count.
- **Missed deadlines**: a personal request missed costs each helper 30 points ([Personal requests](personal-requests.md)).
- **Quest reward `friendship`**: quests can pay friendship as a reward: `points`, `to` (`giver` by default, the poster;
  or a villager's name in the village, an arc's role) and `who` (`finisher` by default, or `helpers` for everyone
  credited with progress, online or not).
- **Other features add points** through the same entry point: gifts (+80 to −40, tripled on the name day), heart
  events (+20 each).
- **What hearts unlock** is in [Reputation and titles](reputation.md) (31.12): cheaper trades from 4 hearts (5% off,
  5% more a heart, 30% at most from hearts), their family's festival gift at 6 hearts, a first baby named after you at
  8, a weekly letter and parcel at 10. Heart events come at 2, 4, 6, 8 and 10 hearts ([Heart events](heart-events.md))
  and personal requests at 3 hearts.
- **Looking**: every 10 ticks (twice a second) the server casts one ray along each player's look, up to 6 blocks,
  stopped by any block in the way. The nearest named villager it touches shows its hearts. A player standing where
  the chunks along the ray aren't loaded sees nothing, and the check never loads a chunk (B93).
- **The hall's tooltip** adds three lines for a named villager: "Your hearts: ♥♥♥♡♡♡♡♡♡♡", the name day line from
  [Gifts](gifts.md), and "Best friends: Jesse, Sam", the two players with the most points (only those with points
  above 0, best first, ties by name).

Player-facing sentences (American English, `en_us.json`):

- Action bar: `message.aliveworkplace.friendship.hearts` is "%s %s": the villager's name in white, then the hearts.
- Hall tooltip: `screen.aliveworkplace.hall.your_hearts` is "Your hearts: %s", and `screen.aliveworkplace.hall.best_friends`
  is "Best friends: %s".
- The config tooltip, `aliveworkplace.config.friendship.tooltip`: "Named villagers keep a friendship with each player,
  ten hearts shown when you look at them: trades, quests, help in a fight and weddings raise it, hitting them lowers
  it. Off: no points and no hearts shown; friendships stay saved."

## Switches

| Switch | Default | What it does |
|---|---|---|
| `friendship` | on once 1.5 is complete | Off: no points move (favors, hits, gifts, heart events, rewards all add nothing), no hearts are shown in the action bar or the hall's tooltip, and cheaper trades from hearts fall away. What is saved stays and comes back when switched on again. |

The expansion gate (`Expansions`, group M31) decides the default and forces it off until Milestone 31 is complete.
There is no game rule.

## Saved data

- A named villager's `friendship` attachment (`registry/ModAttachments`), saved with the villager. Absent until
  someone earns a point or a field is first written. It holds `players`: player UUID to a record. Reads as nobody
  having any points when absent.
- Each player's record: `name` (their last known name, shown under Best friends; default empty), `points` (default 0),
  `favours` (a map from favor id, such as `trade` or `hand_in`, to the day it last counted; default none),
  `hit_tick` (the game time of the last hit that cost points; default -1: none).
- The same record also holds fields other features own, each with a default so older saves read cleanly: `gift_day`,
  `gift_week` (-1) and `gifts_week` (0) for gifts; `told` (none) and `keepsake` (false) for heart events; `festival_gift`
  and `parcel_week` (-1) for the friends' perks.
- Not saved: the list of monsters that lately hurt a villager (it lasts ten seconds, and a restart clears it).
- A villager saved before friendship existed reads as nobody having any points. Registry ids and field names are never
  renamed.

## Items, blocks, jobs, commands

None of its own. The quest reward type is `friendship` (a data file field of a quest, see Quests).

## Decisions

- Hearts never fade, so a player is never punished for being away, and the live server needs no daily upkeep (31.5,
  owner's spec).
- Only named villagers in a hall's village keep friendships: a name marks who the villager is to the player, and the
  hall makes the village a place that remembers (31.5).
- Defending is the only favor with no daily limit, so protecting a village in a raid keeps paying (31.5).
- The look-up never loads a chunk: it was reading chunks from disk on the server thread for every player each check
  (B93).
- Hits are free at 0 points and do not start the minute, so nobody can be pushed to 0 and then be hit for nothing
  (code, `Friendship.hit`).

## Known limits

- Points are only added to named villagers in a village with a hall. Players are told nothing in chat when a favor
  counts, only hearts puff over the villager.
- No decay, no way to lose a friend's heart events once told, and no game rule: the one switch is `friendship`.
- Differences from the roadmap spec, so nobody trusts the spec text over the code: the config default follows the
  expansion gate (off until 1.5 is complete) rather than a plain `true`; the daily hand-in favor is shared between
  hall quests and the requests board; a hit at 0 points costs nothing; the wedding and festival favors go to every
  player inside the village's area, not to those who stood at the ceremony.

## Proof

`FriendshipGameTests` (6 tests): `tradesQuestsAndHandInsAreFavoursOnceADay`, `defendingHerCountsAndHittingHerCosts`
(monsters, wedding, festival, request, and the hit), `twoPlayersAreKeptApart`, `friendshipIsSavedOnTheVillager` (a
save and reload of the attachment, and an old save with sparse fields), `switchedOffNothingMovesOrShows`, and
`theLookUpNeverLoadsAChunk`.

`QaFriendshipGameTests` (QA, 5 tests, written from the spec): `aHitCostsAgainAtExactlyOneMinute` (1199 and 1200
ticks), `pointsStayBetweenZeroAndAThousand`, `aDailyFavourCountsAgainOnTheNextDaysFirstTick`,
`theHeartsShowWithinSixBlocksOnlyAndOnlyForTheNamed` (5.5 blocks against 7, and an unnamed villager) and
`aMonsterKilledOnTheTenthSecondCountsAndNotAfter` (200 and 201 ticks).

Showcase scene `friendship`: a trade, a quest done for Dara, the hearts in the action bar and the hall's tooltip.

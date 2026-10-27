# The weekly leaderboard

ROADMAP 33.19 (part of 1.7, "From village to realm"; switched on in 1.0 with every other expansion). Once a week the whole server hears
how the players' villages did: the biggest, the richest, the happiest, the one with most Legends and the greatest
realm. Winners get a line in their chronicle and a ribbon on their hall for a week.

Roadmap items: 33.19

## What a player sees

**The announcement.** Every Sunday at 19:00 server time, everyone online gets the week's leaderboard in chat: a gold
title, then one line per board with the top three, each with the village, its owner and the number:

- **Biggest**: villagers ("1. Thornholm (Rowan) 24 villagers").
- **Richest**: emeralds the village earned that week: its daily takings, what players paid at its price board, and its
  caravans' sales.
- **Happiest**: the average wellbeing of the week, from every round of its hall ("90% wellbeing").
- **Most Legends**: its Legends (read "Most Masters", counting master villagers, while Legends are switched off).
- **Greatest realm**: the realm with the most member villages, then the most villagers ("2 villages, 35 villagers").

A board nobody is on reads "no village yet" (or "no realm yet").

**The winners.** The first of each board gets a chronicle line, "Won the week's leaderboard: Biggest village, with 24
villagers", and its hall's name icon (the realm's capital for the Greatest realm) wears a blue rosette ribbon in its
top-right corner for a week, with a line "Ribbon of the week: ..." in its tooltip.

**Leaving a village off.** The Trade page (the hall's minecart) has a toggle in its top row, a gold ingot "On the
weekly leaderboard"; the owner (or a friend, or an operator) clicks it to leave the village off ("Left off the weekly
leaderboard", a sheet of paper) and clicks again to put it back.

**Any time.** `/workplace leaderboard` shows the standings so far this week, in the same lines.

**A missed week.** If the server was down when the board fell due, it is read out when the server next starts, once,
with a title that says so. Lines read out while nobody was online wait for the next player.

## How it works

`realm/Leaderboard.java` checks every 10 seconds whether the last Sunday 19:00 (`leaderboardDay`, `leaderboardHour`,
in the server's own time zone) has passed since the week began; if so it reads the boards out, gives the ribbons and
starts a new week. Real time was chosen over 7 in-game days while the owner decides (ROADMAP 33.19).

Only villages with an owner are listed, and not those left off. The numbers are what the halls' rounds last saw, so a
village that isn't loaded counts at its last-known values: villagers from the realm records, the week's earnings and
wellbeing from its own tally. A village with nothing on a board (no earnings, no Legends) isn't listed there. A village
whose hall had no round this week counts at its last-known wellbeing.

Ties go to the bigger village (more villagers), then the name. For realms: members, then villagers. A realm is listed
under its capital's owner and is left off with its capital.

Earnings are counted as they go into the treasury: the daily takings (after the realm's levy), the price board's sales
to players, and caravan sales when they are booked. A winner whose hall isn't loaded gets its chronicle line at its
next round. A new week sets earnings and wellbeing back to naught; Legends and Masters stay as last counted.

## Switches

| Switch | Default | What it does |
|---|---|---|
| `weeklyLeaderboard` | on (with 1.7) | The weekly announcement, the ribbons, the Trade page toggle and the counting. Off: nothing counted or announced, the command says it's off; what was counted stays saved, and a week that passes while off isn't read out later. |
| `leaderboardDay` | `sunday` | The day it is read out, `monday` to `sunday`. In the file only. |
| `leaderboardHour` | 19 (0 to 23) | The hour, server time. |

## Saved data

In `aliveworkplace_realms`, under `leaderboard` (a world from before 1.7 has none of it and starts empty: the first week
begins at the first check):

- `tallies`: per village (`hall`), `earned` (hundredths of an emerald this week, 0), `wellbeing` (the sum of the
  rounds' percent, 0) and `rounds` (0), `legends` and `masters` (as last counted, 0);
- `leftOff`: the villages their owners left off (none);
- `ribbons`: per win the village, `board`, `value`, `until` (real time, ms), the chronicle `day` and whether it was
  `noted` (false);
- `held`: lines read out while nobody was online (none);
- `weekStarted`: when this week began (real time, ms; 0 for never).

## Items, blocks, jobs, commands

- `/workplace leaderboard`: the standings so far this week (anyone may use it).
- The ribbon is the sixth mark of `textures/gui/trade_marks.png`, drawn by `tools/textures/art/gui.py`.

## Decisions

- Real week, Sunday 19:00 server time, until the owner chooses between a real week and 7 in-game days (ROADMAP 33.19's
  default).
- Most Legends counts Masters while Legends (1.3) are switched off, as the item says.
- Lines nobody was online to hear are kept and read out to the next players online, so a week isn't lost to an empty
  server.
- Switching the leaderboard off lets the weeks pass silently, so turning it back on doesn't read out a stale week.

## Known limits

- Only the top three of each board are read out; `/workplace leaderboard` shows the same three.
- A village whose hall was never loaded since 1.7 has no tally: it is on Biggest (from the realm records) but has no
  earnings yet.

## Proof

`LeaderboardGameTests`: `threeHallsComeOutInOrder` (every board's line and order with a set clock, the chronicles, the
ribbon on the name icon, the command, nothing announced twice, the ribbon gone after a week),
`aVillageLeftOffIsntListed` (the Trade page toggle, a stranger refused, an unowned village not listed),
`aTieGoesToTheBiggerVillage` (and the takings counted), `aMissedWeekIsAnnouncedOnce`,
`theWeeksNumbersSurviveSaveAndReload`, `theSwitchOff`. Showcase scenes: `leaderboard`.
QA: `QaRealmMapBoardGameTests`: `qaWeekBoundaryToTheSecond` (18:59:59 nothing, 19:00:00 read out, nothing more until
the next Sunday 19:00:00), `qaTheDayAndHourSettings` (Wednesday 08:00, the command's line), `qaTopThreeAndTiesGoToTheBigger`
(a fourth village not read out; a three-way tie in the bigger villages' order whichever was counted first),
`qaGreatestRealmByMembersThenVillagers`.

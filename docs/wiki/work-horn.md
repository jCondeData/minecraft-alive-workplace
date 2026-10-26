# The Work Horn

A brass-banded horn that calls a rush: everyone in the village works 50% faster for 5 minutes, once a day, and is
worn out until dawn after. Part of 1.4 (Edicts and civic items): switched on in 1.0 with every other expansion.

Roadmap items: 30.11

## What a player sees

Craft a Work Horn from a goat horn, a gold ingot and an emerald (shapeless). Hold it up in a village, as you would a
goat horn, until it sounds (a second and a half): the village answers ("Oakvale answers the horn: 12 villagers rush to
work!") and every grown villager works 50% faster for 5 minutes, with sparks over the rushing workers now and then.
Their status line and the hall's list name "the Work Horn's rush" among the reasons for their pace. When the rush ends
they are worn out: 10 less happy ("worn out") until dawn. Villagers talk about the rush while it lasts.

A village answers once a day: a second blow that day is refused with "The village has already answered the horn
today." Away from any Village Hall the horn says "No village hears the horn here: blow it near a Village Hall." In a
village someone owns, only the owner, their friends and operators can call a rush; anyone else is told so and nothing
happens. The Book of Edicts' last row shows the horn: ready, or rushing with the minutes left, or used today.

## How it works

The rush is a source of the one pace rule (`work/Pace`, 30.2): 1/1.5 of the usual work time from the tick the horn
sounds for 6000 ticks, and the usual time on the tick after. Like every other bonus it stops at the `maxWorkPace` cap,
so a rush under Long Shifts with partners, a guild and a tonic can't pass it. Children don't rush.

The hall keeps the day the horn was last blown and when the rush ends. Each grown villager who answered keeps when
they are worn out until (the next dawn after the rush ends) and is 10 less happy from the rush's end to then.

## Switches

| Key | Default | What it does |
|---|---|---|
| `workHorns` | on | Off: the horn only sounds; no rush, nobody worn out |
| `maxWorkPace` | 200 (100 to 400) | The cap the rush shares with every other speed bonus |

## Saved data

On the hall: `hornDay` (the day the horn was last blown; none by default) and `rushUntil` (the game time the rush
ends; 0). On each villager who answered: the attachment `worn_out` (the rush's end and the dawn after it).

## Items, blocks, jobs, commands

- Item: Work Horn (`aliveworkplace:work_horn`), crafted shapeless from a goat horn, a gold ingot and an emerald.
- Texts: `item.aliveworkplace.work_horn`, `message.aliveworkplace.work_horn.rush`, `message.aliveworkplace.work_horn.used`,
  `mood.aliveworkplace.reason.worn_out`.

## Decisions

- 30.11: a rush is a pace source like any other, so the cap holds; the cost is the worn-out mood until dawn, so a
  rush a day is a choice, not free speed.
- 30.11: operators may call a rush in any village, as they may proclaim edicts; the refusal names only the owner and
  their friends.

## Known limits

- A villager who walks into the village during a rush rushes too (the rush is the village's), but only those there
  when the horn sounded are worn out after.
- The rush reaches villagers within the hall's radius only.

## Proof

GameTests: `WorkHornGameTests` (7: the pace during and after the rush, once a day, worn out until dawn, the cap, a
stranger's horn in an owned village, the console's rush, a rush over a save, `workHorns` off), `QaEdictsGameTests` (the rush's last tick). Showcase scene `work_horn`.

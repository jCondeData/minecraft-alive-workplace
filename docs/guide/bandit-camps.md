# Bandit camps

```infobox
type: Threat
Who: a Bandit Chief in iron and three or four of his men (pillagers and vindicators)
Needs: a village of Village rank or more
Where: 80 to 104 blocks from the Village Hall, on dry, fairly flat, open ground
Chance a day: 12%, at most one camp per village
While it stands: the night raids come from the camp, twice as often, and the village feels less safe
Break it up: kill the chief
Peace after: 5 days without a new camp
Setting: Bandit Camps (on)
categories: Defense
related: raids.md, defence.md, threat-level.md, ranks.md
```

When your village reaches **Village** [rank](ranks.md), it becomes worth robbing. Now and then a band of **bandits**
makes camp out beyond it: tents round a fire, a **Bandit Chief** in iron armor and three or four of his men.

## When a camp goes up

Everyone nearby gets the news: "Smoke rises beyond Oakvale: bandits have made camp to the north. Until their chief
falls, they'll raid the village at night." The camp stands 80 to 104 blocks from the hall, on dry, open ground. The
hall's guards icon shows where it is.

While it stands:

- the village's night [raids](raids.md) come from the camp, bandits instead of monsters, and **twice as often**;
- the villagers **feel less safe**, which pulls down the village's [wellbeing](village-needs.md) and so the pace of
  everyone's work.

## Breaking it up

Kill the **chief** and the camp is broken up: his band scatters, and the chest in his tent is yours. It holds things
like emeralds, iron and gold, bread, arrows, a crossbow or an iron axe, and with luck a diamond, a golden apple, a saddle
or a name tag. The chronicle remembers who brought him down, and no new camp comes for five days.

Take help along: raise a Rally Banner and your guards follow you out, or hire three mercenaries at the hall for the
night (see [Threats and defense](defence.md)). Guards stay at home otherwise: the camp is far beyond their patrol.

## How it interacts

- **Fed by:** the village's [rank](ranks.md). A Hamlet never gets a camp.
- **Affects:** [raids](raids.md) (from the camp, twice as often), [wellbeing](village-needs.md) (less safe) and so the
  [work pace](work-pace.md) of the whole village.
- **Turned down or off:** the [Threat Level](threat-level.md) setting makes camps rarer or more frequent, and at 0 none
  come; on **Peaceful** none come; the [Bandit Camps](settings-threats.md) switch turns them off. With
  [Monster Raids](settings-threats.md) off, a camp may still go up but no raids come from it.
- **On demand.** Operators can set a camp down by the nearest hall with `/workplace threat lair bandits`, and clear it
  away again with `/workplace threat clear` (see [/workplace threat](command-threat.md)).

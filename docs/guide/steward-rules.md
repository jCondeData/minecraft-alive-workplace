# The Steward's rules

```infobox
type: Mechanic
expansion: M27
image: https://jcondedata.github.io/minecraft-alive-workplace/steward_rules/still-1.jpg
caption: His first wish of the day, over his head
Checked: each morning, after his rounds
Wishes a day: up to 8, the most urgent first
Read from: the same numbers as the hall's What next? tips
See them: /workplace steward explain
Server packs: may add their own rules
categories: Steward, Village Hall
related: steward.md, stewards-desk.md, command-steward-explain.md, village-needs.md
```

What the [Steward](steward.md) wants comes from a set of rules: "when the village is like this, do that". Each morning
he checks every rule against the village, ranks the ones that hold into up to 8 wishes, and turns them into proposals
on [his desk](stewards-desk.md). A rule that was just carried out rests for a day or a few before it's tried again, and
some only apply once the village reaches a rank.

He reads the same numbers as the [Village Hall](village-hall.md)'s **What next?** tips (beds, food, guards, beauty...),
so the hall and the Steward never disagree. See [Village needs](village-needs.md).

## What he asks for, and when

**Homes** (in Homes zones):
- Villagers without a bed and a home whose next tier sleeps more: **upgrade it first**, before any new house.
- 1 bed short in a Hamlet: a **Starter Cottage**. 2 short: a **Stone House**. 3 short, from a Village: a **Terrace**.
  4 short in a Town with no inn: an **Inn** (in Market).
- More than half the grown-ups in plain homes and nobody without a bed: upgrade their homes.

**Storage and market** (in Market): no Storehouse with 3 or more villagers: a **Storehouse**; its chests 80% full: the
next tier of it. A Village with no stall: a **Market Stall**. A Town with no square: a **Market Square**, for
[market days](market-days.md).

**Food** (in Farms): food short and no Berry Farm: a **Berry Farm**; from a Village, a **Ranch** too.

**Care and learning** (in Civic): 2 or more ill and no nurse: a **Clinic** (a **Healing Center** from a Village). A
death in the last 30 days in a village of 8 or more: a **Graveyard**. 3 or more children: a **Schoolhouse**. 6 or more
villagers and no scholar: a **Library**. A couple courting: a **Chapel**, where weddings are held. With Cobblemon, from
a Village: a **Pokémon Center**.

**Safety** (in Defenses): guards short: a **Lookout Tower**, and in a Town a **Barracks**. Raided lately, or bandits
nearby: [a wall](walls.md).

**Beauty** (in Gardens): beauty under 3 with 5 or more villagers: a **Well**, a **Park Bench**, a **Fountain** and a
**Gazebo**, in that order, one each. 2 or more beds in the dark: **Street Lamps** by the darkest homes' doors.

**Workplaces** (in Workshops): workers with no workstation, or a job the village wants with no free block, get their
trade's building: a Builder's Workshop, a Smithy, a Kitchen, a Post Office, a Mason's Yard and so on, one for every job.

**Jobs and research**: anyone jobless gets work; idle scholars get a topic
([Jobs and research](steward-jobs-and-research.md)).

**Things only you can do**: with nobody able to build, he asks you to "place a Blueprint Table and give a villager the
job"; workers who lost their workstation need new ones placed; with 6 or more villagers and no Scholar's Desk, he asks
for one.

## How it fits with the rest

- **What feeds them**: beds, food in store, guards, the sick, children, couples, beauty, the store's fill, research,
  the village's [rank](ranks.md) and raids ([Raids](raids.md), [Bandit camps](bandit-camps.md)).
- **What they lead to**: proposals on the desk; in **Run the village** mode, builds that start by themselves.
- **See why**: `/workplace steward explain` prints every rule with its numbers and whether it held
  ([the command](command-steward-explain.md)).

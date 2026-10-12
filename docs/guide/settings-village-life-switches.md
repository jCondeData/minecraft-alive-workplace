# Village life switches

```infobox
type: Setting
image: https://jcondedata.github.io/minecraft-alive-workplace/config/still-2.jpg
caption: The settings screen's switches
Villager Names: on
Villager Traits: on
Moods: on
Sickness: on
Weddings: on
Villager Chatter: on
Population Cap: 40 villagers (0 to 500)
Builder Repairs: on
Builder Paths: on
categories: Settings, Village life
related: settings.md, village-life.md, names.md, moods.md
```

Each part of village life has its own switch. Everything starts on; turn one off and villages simply go without it,
with nothing else to look after. On the settings screen they're on/off buttons, each with a tooltip. See
[Settings](settings.md) for where to change them.

## The switches

| Setting | Normally | When it's on |
|---|---|---|
| **Villager Names** | on | villagers in a village with a Village Hall get [names](names.md) |
| **Villager Traits** | on | villagers have [traits](traits.md) (diligent, lazy, nimble...) that change how they work |
| **Moods** | on | villagers in a village with a hall have [moods](moods.md) that change how fast they work |
| **Sickness** | on | villagers in a village with a hall fall [ill](sickness.md) now and then; a Nurse cures them |
| **Weddings** | on | villagers [court, marry](couples-and-weddings.md) (a wedding at the bell) and mourn |
| **Villager Chatter** | on | villagers near a player now and then say something about their day, over their heads ([chatter](chatter.md)) |
| **Builder Repairs** | on | idle builders repair the buildings they finished when blocks go missing |
| **Builder Paths** | on | builders lay a dirt path from each finished building to the village's bell or Village Hall |

## Population Cap

Normally **40**. A village with a Village Hall stops having [babies](babies-and-growth.md) at this many villagers, and
each [rank](ranks.md) above Hamlet lets it grow ten bigger (a City's cap is 70 at the default). At **0**, villages don't
grow at all.

## How they interact

- **Moods** feed the [work pace](work-pace.md): happy villagers work faster, unhappy ones slower. With Moods off,
  nobody is sped up or slowed down by their mood.
- **Sickness** slows the ill to half pace and makes them a little less happy; off, nobody falls ill.
- **Weddings** off means no couples, so no married couple goes first in line for a baby; babies still come when the
  village has a free bed and food.
- **Builder Repairs** keep walls and houses whole after [raids](raids.md) and creepers. **Builder Paths** link every new
  building to the middle of the village. See [The Builder and blueprints](builder.md).
- **Population Cap** works with the village's beds, food and [needs](village-needs.md): a village needs all of them to
  grow.

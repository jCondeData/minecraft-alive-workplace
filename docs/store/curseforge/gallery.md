# Gallery images for CurseForge

Every image here is a real in-game capture from the nightly showcase (https://jcondedata.github.io/minecraft-alive-workplace/),
filmed at commit 2ce0771 (the 0.138.0 content). Download each from its URL and upload it in the project's Gallery, in
this order, with the title and description given. Stills are 960×540 JPEGs; GIFs are 480×270.

The showcase is filmed again every night, so a URL shows that night's run: download the files when you upload them,
and look at each one first.

## Headline jobs (one GIF each)

| # | Title | Description | URL | Size |
|---|---|---|---|---|
| 1 | Builders at work | Three builders put up the Market Stall, the Starter Cottage and the Lookout Tower from the materials in their chests. | https://jcondedata.github.io/minecraft-alive-workplace/builders/anim.gif | 1.3 MB |
| 2 | Miner | A miner digs out a marked quarry from the top down and carries everything back to the chests. | https://jcondedata.github.io/minecraft-alive-workplace/quarry/anim.gif | 0.8 MB |
| 3 | Lumberjack | A lumberjack fells four trees and plants a sapling where each one stood. | https://jcondedata.github.io/minecraft-alive-workplace/forest/anim.gif | 0.6 MB |
| 4 | Orchard Keeper | An orchard keeper picks berries, cocoa and (with Cobblemon) apricorns, leaving the plants to grow again. | https://jcondedata.github.io/minecraft-alive-workplace/orchard/anim.gif | 0.6 MB |
| 5 | Guard | A guard in armor fights off three husks, then spars with a Training Dummy. | https://jcondedata.github.io/minecraft-alive-workplace/guard/anim.gif | 0.9 MB |
| 6 | Trainer (Cobblemon) | A battle with the village's Master trainer, whose lead Mega Evolves (with Mega Showdown installed). | https://jcondedata.github.io/minecraft-alive-workplace/battle/anim.gif | 4.8 MB |

Optional extra GIF: **Farmer**, "A farmer harvests a field and plants it straight back."
https://jcondedata.github.io/minecraft-alive-workplace/farm/anim.gif (0.6 MB; the field is small in the frame, which is
why the orchard keeper is the pick above).

## Stills

| # | Title | Description | URL |
|---|---|---|---|
| 7 | The starter builds | The Market Stall, the Starter Cottage and the Lookout Tower, finished, with the builders' Blueprint Tables in front. | https://jcondedata.github.io/minecraft-alive-workplace/builders/still-3.jpg |
| 8 | See it before it's built | Hold a placed blueprint and the rest of the building shows as see-through blocks while the builder works. | https://jcondedata.github.io/minecraft-alive-workplace/preview/still-2.jpg |
| 9 | What the chests are short of | A placed blueprint's tooltip: its size, its style, where it stands and how many items the builder's chests are still short of. | https://jcondedata.github.io/minecraft-alive-workplace/missing/still-2.jpg |
| 10 | The Blueprint Table | Every blueprint on the server, with exactly what it needs. Upload your own .litematic and .schem files here. | https://jcondedata.github.io/minecraft-alive-workplace/table/still-1.jpg |
| 11 | Styles | The Stone House II built in the Cherry style. Any blueprint can be built in Stonework, Sandstone, Dark Oak or Cherry. | https://jcondedata.github.io/minecraft-alive-workplace/styles/still-2.jpg |
| 12 | Cavalry | Guards ride a saddled horse, donkey, mule or camel on patrol. | https://jcondedata.github.io/minecraft-alive-workplace/extras/still-2.jpg |
| 13 | The Village Hall | Everyone in the village at a glance: job, level, traits, what they're doing and what they need. | https://jcondedata.github.io/minecraft-alive-workplace/hall/still-3.jpg |
| 14 | A village with a builder's workshop | Newly generated villages can come with a builder's workshop. | https://jcondedata.github.io/minecraft-alive-workplace/village/still-3.jpg |
| 15 | The Settler's Wagon | No village nearby? Two settlers make camp with a wagon of supplies, and one of them is your builder. | https://jcondedata.github.io/minecraft-alive-workplace/camp/still-2.jpg |
| 16 | Workers at their workstations | Workers in their job outfits, each by their workstation. | https://jcondedata.github.io/minecraft-alive-workplace/staff/still-2.jpg |
| 17 | Pokémon partners (Cobblemon) | A Bulbasaur from a nearby pasture helps the orchard keeper; Pokémon whose type suits the job make it go faster. | https://jcondedata.github.io/minecraft-alive-workplace/orchard/still-2.jpg |
| 18 | Fighting beside the guard (Cobblemon) | Fighting and Dragon types from a nearby pasture join the guard's fights, here a Machop and a Dratini. | https://jcondedata.github.io/minecraft-alive-workplace/guard_pokemon/still-1.jpg |

## Before uploading

- **The GIFs start with a dark "Loading terrain..." frame** (a tenth of a second or two; the guard GIF has two such
  frames). On the page it's a blink, but if CurseForge uses the first frame as the thumbnail it shows a dark screen.
  Drop it before uploading, for example with ImageMagick:
  `magick anim.gif -coalesce -delete 0 -layers Optimize anim-trimmed.gif` (for the guard GIF, `-delete 0-1`).
  A later session can also cut it from the showcase itself.
- **The trainer GIF is 4.8 MB.** If CurseForge refuses it for size, use the still instead:
  https://jcondedata.github.io/minecraft-alive-workplace/battle/still-2.jpg ("A battle with the village's Master
  trainer").
- Every URL above answered 200 (image/gif or image/jpeg) on 2026-10-02.

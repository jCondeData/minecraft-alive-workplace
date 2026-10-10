# Alive Workplace wiki

How everything in the mod works, in plain words: written for the owner first, then for the lanes that build it
(ROADMAP 22.9). One page per feature or system.

## Pages

**Villagers and their work**
- [Villager jobs](villager-jobs.md): how a villager gets a job, what every job shares, and the list of all jobs.
- [Builder](builder.md): the villager who builds blueprints.
- [Pokémon partners](pokemon-partners.md): pastured Pokémon that help at work, and are seen doing it (Cobblemon).
- [The Pokémon Trader](pokemon-trader.md): the day's Pokémon offers and the trader's own trade screen (Cobblemon).
- [Gifts](gifts.md): Gift Wrap, the Gift, what villagers think of it, name days and taste files (31.6).
- [Heart events and life stories](heart-events.md): what villagers tell their friends, the life story page and
  event files (31.7).
- [The price board](price-board.md): the hall's Trade page, what a village is known for and short of, and its prices (33.4).
- [Trading at the board](board-trade.md): selling to a village and buying from it on the Prices tab, the treasury on the page and who collects it (33.5).
- [Caravans that trade](caravan-trade.md): what a caravan takes to sell, how the other village pays, what comes home, and the Routes tab's earnings (33.6).

**The village**
- [The Village Hall](village-hall.md): the village at a glance, its needs, ranks, calendar, treasury and pages.
- [Edicts](edicts.md): laws with a boost, a cost and a reform (1.4).
- [Guilds](guilds.md): Guild Charters, Guildhalls and the eleven guilds (1.4).
- [Classes](classes.md): Peasant to Noble, what each needs and gives, and the Vintner (1.8).
- [Elders](elders.md): elders, their passing, and the Evergreen Charm that keeps a good one (1.8).
- [The Festival Cup](festival-cup.md): its champions, the Cup banner and the holders' pride (Cobblemon, 1.2).
- [Raids and threats](raids.md): raider cultures as data, the threat clock, raids that survive a restart (32.2).
- [Lairs and the Defence page](lairs.md): a camp for every culture, named captains, the camp's strength, the hall's
  Defence page (32.3).

**Villages that build themselves**
- [Steward](steward.md): the City Plan, the Steward, his rules and his desk (1.1).
- [Roads and walls](roads-and-walls.md): streets, lamps, bridges, roads between villages and walls (1.1).

**Legends**
- [Legends](legends.md): the twelve Legends, how they come, their needs, and the Gifted (1.3).

**For server owners**
- [Config switches](config-switches.md): every setting and game rule with its default, and the expansion gate.

## Who keeps it true

- **The lane that builds or changes a feature updates its page in the same commit** (`docs/agent/sessions.md`, the
  build loop's step 5), and adds the roadmap id to the page's "Roadmap items:" line.
- **The QA lane checks it**: for every item it verifies, it reads the page against the behaviour its tests proved
  and fixes the page or files a bug. When nothing is waiting it backfills one missing page per run, oldest milestone
  first, and removes those ids from the baseline.
- **`tools/modtest/wikicheck.py` fails the build** (`./gradlew build` runs it as `checkWiki`) when a page names
  something that no longer exists, or a ticked player-visible item has no page.

## Page format

Plain markdown, plain words, short paragraphs. A page starts with a title, one or two sentences on what the feature
is (and the expansion it belongs to), then this line, followed by a blank line:

    Roadmap items: 30.17, 30.18, B78

It lists the roadmap ids the page covers, separated by commas ("none" when there are none). Then these eight
sections, in this order, each as `## Name` (a number in front, `## 1. Name`, is fine):

1. **What a player sees**: a few sentences, the way the player would tell it.
2. **How it works**: short paragraphs; numbers with their reasons.
3. **Switches**: every config key and game rule, its default, what it does. As a table whose first column is the
   key in backticks.
4. **Saved data**: every saved field and its default (what an older save without it reads as).
5. **Items, blocks, jobs, commands**: with their ids.
6. **Decisions**: what was chosen and why, with the roadmap id (and who decided it). Only decisions that were
   really made: from the ROADMAP, a design note or the code's own comments.
7. **Known limits**: what it doesn't do, and anything not built or not tested yet, said plainly.
8. **Proof**: the GameTests and showcase scenes that show it works.

Write "none" under a section that has nothing; don't leave one out.

### Names the build checks

Write a name in backticks, in one of these forms, and `wikicheck.py` makes sure it still exists. A page that names
something that was renamed or removed fails the build until the page is fixed.

| Write | It must be |
|---|---|
| `supplyRadius`, `workplaceBuildDelay`, `traderOffersChangeDaily` (a lowerCamelCase word) | a config key (`WorkplaceConfig`), a game rule (`ModGameRules`) or a GameTest method. Not checked in the Saved data section, where such a word is a saved field |
| the first column of a table in the Switches section | a config key or a game rule |
| `aliveworkplace:guild_charter` (an id with our namespace) | an id the code registers (an item, block, job...) or a data file `data/aliveworkplace/<kind>/<id>.json` |
| `item.aliveworkplace.guild_charter` (lowercase, three or more parts with dots) | a key in `lang/en_us.json` |
| `GuildGameTests` (a word ending in Tests) | a test class; `GuildGameTests.guildsLoadFromData` must be a test in it |
| a lowercase word after "Showcase scenes:" in the Proof section, such as `guilds` | a scene in `tools/showcase/scenes.py` |

Anything else in backticks (saved fields such as `builder_job`, class names such as `BuildSite`, commands, file
paths) isn't checked, so prefer the checked forms: an item as `aliveworkplace:blueprint`, not as its bare name.

The checker also makes sure that every page has the eight sections and the "Roadmap items:" line, that every id on
that line is in ROADMAP.md, that this index links every page, and that
[Config switches](config-switches.md) lists every config key and game rule.

### Every player-visible item has a page

A ticked roadmap item that a player can see (ticked with `sessions.py done <id> --review`, so marked "review: ..."
or later "approved <date>") must be on some page's "Roadmap items:" line. Work with nothing to see ("approved auto")
needs no page.

Items ticked before the wiki existed and not covered yet are listed in `tools/modtest/wiki_baseline.txt`. That list
only shrinks: the QA lane's backfill writes a page, adds the ids to its line and runs
`python3 tools/modtest/wikicheck.py --prune`. Never add an id to the baseline to get a build green; write the page.

Run the check yourself with `python3 tools/modtest/wikicheck.py` (under a second), and its own tests with
`python3 tools/modtest/test_wikicheck.py`.

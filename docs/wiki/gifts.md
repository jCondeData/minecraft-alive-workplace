# Gifts

ROADMAP 31.6. Builds on friendship (31.5). Part of the 1.5 expansion: switched on when Milestone 31 is complete.

## What a player sees

Paper, string and any dye make four **Gift Wrap**. Gift Wrap and any one item on the crafting grid make a **Gift**: a
red box with a gold bow whose tooltip says "From Jesse" (whoever took it off the grid) and never what's inside.
Right-click a named villager with it. They unwrap it (a paper rustle, bits of the item flying), say what they think
over their head and in your chat ("Cake! You remembered."), hearts puff if they liked it, they shake their head if
they didn't, and your hearts with them move. A second gift the same day, or a third the same week, is turned down
with a line and stays in your hand. A villager without a name shakes their head.

## How it works

- **Points** by taste band: loved +80, liked +45, neutral +20 (an item no taste file names), disliked −20, hated −40.
  Friendship stays within 0 to 1000.
- **Limits**: one gift a day and two a week, per player and villager. Days are the world's days (the same count the
  chronicle uses); weeks are seven days from the world's first day. A gift that can't move the points (a hated one
  at no hearts) still uses up the day.
- **Name day**: every villager has one every 28 days, worked out from their UUID, so it never changes and costs
  nothing to keep. That day a gift's points count three times (a hated one costs three times as much, too) and they
  add "And on my name day, too!". The hall's list shows "Name day: today (a gift counts three times)", "tomorrow" or
  "in N days" under your hearts.
- **Where the item goes**: the supply chests by the villager's workstation; else the village's store (the chests by
  its kitchens and Storehouses); else the villager's own pockets; and only if those are full too, the ground at
  their feet. The wrapping is used up.
- **Bottle o' Enchanting**: liked unless a taste file says otherwise, and a villager with a trade learns from it
  (15 XP, the bottle used up). Someone without a trade just keeps it.
- **Who takes gifts**: named villagers in a village with a hall (the ones that keep friendships). Others shake
  their head and the Gift stays in your hand. In creative the Gift isn't used up.
- **The recipe** is a special one, like vanilla's map cloning: exactly one Gift Wrap and one other item (not Gift
  Wrap, not a Gift). One item is taken from the stack on the grid, with everything on it (name, enchantments, wear).
  Nothing is left on the grid: a wrapped Milk Bucket keeps its bucket.
- **Tastes** are data, `data/<namespace>/villager_tastes/<id>.json`: `for` (`"everyone"`, `{"job": id}`,
  `{"family": [ids]}` or `{"trait": name}`) and `loved`, `liked`, `disliked`, `hated` lists of items or `#tags`.
  The most specific file that names the item wins: job, family, trait, everyone; between two as specific as each
  other, the kinder band. Within one file an item in two bands counts for the first of loved, liked, disliked,
  hated. Ids of mods that aren't installed are kept and never match (the Pokémon file names Cobblemon's items).
  A broken file is skipped with a warning naming it; `"enabled": false` switches one off.

The first set (ids under `aliveworkplace:`):

| File | For | Loves | Likes | Dislikes | Hates |
|---|---|---|---|---|---|
| `everyone` | everyone | cake, pumpkin pie, golden apples, diamonds | bread, cookies, honey bottles, emeralds, small flowers | dirt, gravel, cobblestone, bones | rotten flesh, spider eyes, poisonous potatoes, pufferfish |
| `building` | Builder, Carpenter, Mason, Tinkerer, Leatherworker | blueprints, spyglasses | bricks, glass, lanterns, planks | | |
| `mining` | Miner, Armorer, Toolsmith, Weaponsmith, Sifter, Netherworker | amethyst shards, gold ingots, netherite scrap | iron ingots, coal, raw copper | | |
| `land` | Lumberjack, Orchard Keeper, Farmer, Florist, Beekeeper, Composter | golden carrots, honeycomb, sunflowers | apples, bone meal, wheat seeds, saplings | | |
| `animals` | Shepherd, Butcher, Rancher, Fisherman | saddles, name tags | wheat, hay bales, cod, salmon, leads | | |
| `kitchen` | Chef | glow berries, golden carrots | eggs, milk buckets, sugar, cocoa beans, raw meat | | |
| `learning` | Scholar, Teacher, Librarian, Cartographer | enchanted books, written books, filled maps | books, paper, feathers, ink sacs, compasses | | |
| `healing` | Nurse, Cleric, Undertaker | glistering melon slices, ghast tears, totems of undying | potions, honey bottles, golden carrots | | |
| `arms` | Guard, Fletcher | shields, crossbows, diamond swords | arrows, flint, iron ingots | | |
| `trade` | Shopkeeper, Innkeeper, Ferryman, Postman, Porter | emerald blocks, filled maps, saddles | paper, boats, lanterns | | |
| `music` | Bard | music discs, goat horns | note blocks, amethyst shards | | |
| `pokemon` | Trainer, Trainer Leader, Move Tutor, Ball Smith, Pokémon Trader, Fossil Scientist | Rare Candy, Ultra Ball | Poké Ball, Exp. Candy S, berries | | |
| `no_trade` | the jobless, nitwits | emeralds | bread, beds | | |
| `trait/glutton` | Glutton | any food | | | |
| `trait/frugal` | Frugal | emeralds, gold ingots | | cake | |
| `trait/cheerful` | Cheerful | flowers, music discs | | | |
| `trait/lazy` | Lazy | beds | | tools | |
| `trait/diligent` | Diligent | tools | | | |
| `trait/clever` | Clever | books, clocks | | | |
| `trait/strong` | Strong | cooked beef, iron blocks | | | |
| `trait/nimble` | Nimble | sugar, rabbit's feet | | | |

## Switches

None of its own. `friendship` (on once 1.5 is complete) off: a Gift is handed back unopened, nothing is saved and
the hall shows no name day. The two recipes stay.

## Saved data

- On the villager, in the `friendship` attachment (31.5), per player: `gift_day` (the day of the last gift taken,
  default −1), `gift_week` (the week the count is for, default −1), `gifts_week` (gifts taken that week, default 0).
  Villagers saved before gifts read with those defaults and take a gift straight away.
- On the Gift item, the `aliveworkplace:gift` component: `item` (the wrapped stack) and `from` (a player's name,
  default "").

## Items, blocks, jobs, commands

Items `aliveworkplace:gift_wrap` and `aliveworkplace:gift`; recipes `aliveworkplace:gift_wrap` (shapeless) and
`aliveworkplace:gift` (special), each with its recipe-book unlock. Textures: `tools/textures/art/items.py`
(`gift_wrap`, `gift`).

## Decisions

- Jobs in more than one family (none today) or files as specific as each other: the kinder band wins, so adding a
  data pack never makes a villager like something less by accident (31.6, lane c).
- "Bricks" and "lanterns" are read generously: the Bricks block and the Brick item, lanterns and soul lanterns;
  "golden apples" include the enchanted one; Clever's "books" are every book a bookshelf takes (31.6, lane c).
- A villager who is named but lives in no village with a hall keeps no friendship (31.5), so they turn a Gift down
  like an unnamed one, with their own line (31.6, lane c).
- With no chest and full pockets the item drops at their feet rather than being lost (31.6, lane c).
- The Gift recipe is special (the spec's "like vanilla's map cloning"), and Minecraft never lists special recipes
  on the recipe book's pages; it has the same unlock advancement as our other recipes, and Gift Wrap's recipe shows
  in the book. The README gives the Gift's recipe in words (31.6, lane c).

## Known limits

- The Gift's "From" is the player who took it off the crafting grid; a Gift made by a Crafter block has none.
- A villager's line names the item by its item name ("Cake! You remembered."), without "a" or "an".

## Proof

`GiftGameTests` (12 tests): the recipes and the kept item, the tooltip, each band's points through a real
right-click, the limits and their lines with a save and reload, the name day, where the item goes, the Bottle o'
Enchanting, unnamed villagers, `friendship` off, the most-specific rule, a data pack's files, and the whole first
set. Showcase scene `gifts`.

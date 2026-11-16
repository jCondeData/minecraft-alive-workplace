# Copperline: the new look for every screen

The owner, 2026-11-16: "I love the new UI ... push the new UI to be built." Copperline replaces the Village Hall look
(`TableStyle`, Milestones 42-44) on every screen the mod opens. Built by Milestones 47 and 48 (ROADMAP), and 44 is
redone in it. Designed by a Fable subagent on 2026-11-15 (it never saw the old screens), then redone in Minecraft's own
font. The owner's mockup page (private): https://claude.ai/artifact/8MFEQZ66U4hxEGCcDcw6N9

## What's here

- `boards/`: the mockups, each 427x240 GUI pixels (GUI scale 2) shown at 3x. `System.png` (foundations) and
  `Components.png` (every part in every state) are the spec; the rest are screens:
  `Main.png` Village Hall Overview, `HallPeople.png`, `HallMap.png`, `Move1-3.png` the move wizard, `Storehouse.png`,
  `Blueprints.png` the Blueprint Book, `Trainer.png`, `VillagerCard.png` (hover card, pinned card, toast, tooltip),
  `Chronicle.png`, `Settings.png`, `Compact.png` (the Hall at 320x180, GUI scale 3), `AltLedger.png` (the parchment
  variant "Ledger": not chosen, kept for reference).
- `sprites/`: our own art at 1x. `ui/` the 16 nine-slices (window, plaque, panel, well (+focus, error), tooltip, toast,
  button normal/hover/pressed/off, copper normal/hover/pressed, danger); `ui-ledger/` the Ledger set; `icons.png`,
  `glyphs.png` (8 px status glyphs), `portraits.png`, `critters.png`, `map.png`, `sheet.png` (the whole sprite sheet).
  They are a starting point: redraw or touch up with the pixel-art skills (CLAUDE.md, Skills) on the palette below.
- `aw.css`: the mockups' tokens and classes (colors, sizes, states), for reference only; the game draws with Java.
- Not here on purpose: the mockups' font files (a copy of Mojang's font). In game, text is the game's own font.

## The rules (from the System board)

- **One shell for every screen.** A copper **plaque** overhangs the window's top edge and names the screen; under it a
  **lintel** bar (where you are, coins, close); a left **rail** (Hall sections, wizard steps or filters; icons only on the
  map and at GUI scale 3); the **stage**; a bottom **whisper line** that explains the hovered thing in plain words, with
  key hints on the right (`Esc close`).
- **Copper is rationed:** one main (copper) button per screen, plus the mark on the selected rail entry or row.
  Everything else is neutral deepslate.
- **Every status is a glyph + a word + a color** (never color alone): good Verdigris, bad Redstone, info Lapis, rare
  Amethyst, warning Lantern.
- **Text:** the game font only, two sizes (1x body, 2x titles and big numbers), shadow on titles, button labels,
  tooltips, toasts and text over the world; no shadow on panel body text. Cut-off text ends with "...".
- **Spacing:** a 4 px unit; corners are one cut pixel (2 px outline on windows, 1 px on parts).
- **Small screens:** at 320x180 the rail turns into a two-column icon grid and the whisper line shortens.

## Palette (hex, and what each is for)

| Name | Hex | Use |
|---|---|---|
| Void | `#0B0C10` | outlines, the deepest shadow |
| Deepslate 900 | `#16171D` | window background, wells |
| Deepslate 800 | `#22242E` | panels, cards |
| Deepslate 700 | `#2E313D` | raised parts, hover |
| Deepslate 600 | `#3D4150` | pressed, dividers |
| Slate Line | `#50556A` | lines, disabled outline |
| Tuff | `#A3A8B8` | second text |
| Quartz | `#F2EFE6` | main text |
| Copper | `#E0864A` | the plaque, the one main button, the selected mark |
| Copper Light | `#F7B47C` | copper highlight |
| Copper Dark | `#9C4F2B` | copper shadow |
| Lantern | `#FFD25E` | hover and focus, warnings |
| Verdigris | `#4FC3A1` | good |
| Redstone | `#FF6B5E` | bad, refused |
| Lapis | `#7FA8FF` | info |
| Amethyst | `#C59BFF` | rare |

Body text on panels meets WCAG AA; disabled text is about 4:1.

## Build order

What to draw first: the nine-slices in `sprites/ui/`, then the ten Hall section icons and the 8 px glyphs; rows, tabs,
bars and toggles are flat fills in code. With those, the shared pieces (`TableStyle`/`ChoiceTableScreen` and the
Hall's shell) switch every screen already in the Hall look at once (47.1), and each screen is then laid out like its
board.

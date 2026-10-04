# CurseForge kit

Everything needed to put Alive Workplace on CurseForge, ready to paste. Nothing here has been published, and the
Markdown hasn't been previewed in CurseForge's own editor yet.

## The files and where they go

| File | Where it goes on CurseForge |
| --- | --- |
| `summary.txt` | The project's **Summary** field. Plain text, 136 characters. |
| `description.md` | The project's **Description**. Set the editor to Markdown if it asks, then paste the whole file. Its images load from the showcase page. |
| `changelog-0.138.0.md` | The **Changelog** box when uploading the 0.138.0 file (changelog type: Markdown). |
| `gallery.md` | The project's **Gallery** (Images): a title and a description for each image, in upload order, with each image's URL. |
| Icon | The project's **avatar/logo**: `src/main/resources/assets/aliveworkplace/icon.png` (see below). |

## Settings to choose

- **Name**: Alive Workplace. Minecraft's brand rules forbid leading with "Minecraft".
- **Class**: Mods (Minecraft: Java Edition). Categories are his pick.
- **The file**: `alive-workplace-0.138.0+1.21.1.jar` from the GitHub release
  (https://github.com/jCondeData/minecraft-alive-workplace/releases/tag/v0.138.0). Display name: "Alive Workplace
  0.138.0".
- **Release type**: Beta, to match GitHub, where every 0.x version is a pre-release.
- **Game version**: 1.21.1. **Mod loader**: Fabric. Java 21, if the form lists Java versions.
- **Environment**: client and server, both required. The mod must be on the server and in every player's game.
- **Dependencies** (Related Projects):
  - Fabric API: **Required dependency** (CurseForge downloads it along with the mod).
  - Cobblemon: **Optional dependency**. CobbleDollars, Radical Cobblemon Trainers and Mega Showdown can be listed as
    optional too: the mod uses each when it's there.
- **License**: see the open questions.

## The icon

`src/main/resources/assets/aliveworkplace/icon.png` is 128×128: the blueprint on a clipboard, in pixel art. If
CurseForge wants it bigger, scale it up without smoothing so the pixels stay sharp, from the repository root:

```
python3 -c "from PIL import Image; Image.open('src/main/resources/assets/aliveworkplace/icon.png').resize((512, 512), Image.NEAREST).save('icon-512.png')"
```

## Decisions (digest-1004-1253, 2026-10-04; the owner asked the digest to decide)

1. **AI disclosure: yes, one honest line.** Add at the end of the description, before the links:

   > Alive Workplace is designed and directed by Jesse, and written with the help of Claude, Anthropic's AI.

   No image needs a disclaimer (every picture is a real in-game capture), but saying it up front costs nothing and
   keeps the page safe if either site tightens its rules (Modrinth's are stricter).
2. **License: GPL-3.0** in CurseForge's list (the mod declares GPL-3.0-or-later in `fabric.mod.json`, and the
   MineColonies-inspired parts need GPL-3 compatibility). The repository's `LICENSE` file now holds the GPL-3.0 text
   (it held the GPL-2 text by mistake; replaced with the owner's yes, 2026-10-04).
3. **Icon:** the clipboard icon, scaled to 512 with the command above.
4. **Guide Book line:** keep it. The page goes live with 1.0, which has the Guide Book and the recipe-book recipes.
5. **Release type:** Beta while the version is 0.x; Release from 1.0.

## Good to know

- The description's images are linked from the showcase page, which is filmed again every night, so the page always
  shows the latest run (and would show a broken shot if a scene broke one night). For pictures that never change,
  upload the gallery first, then swap each image URL in the description for the CurseForge copy (right-click the
  image in the gallery, copy its address).
- The showcase GIFs start with a split second of the dark "Loading terrain..." screen; `gallery.md` says how to cut
  it before uploading.
- The trainer GIF is 4.8 MB; `gallery.md` has a still to use instead if CurseForge refuses it.

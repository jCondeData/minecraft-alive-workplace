# pxlib

A copy of the `scripts/` folder of the owner's **minecraft-pixel-art** skill (drawing library, lint, previews), so the
texture recipes in `../art/` build without the skill installed. Update it by copying the skill's `scripts/*.py` and
`palettes.json` over these files.

- `python3 tools/textures/pxlib/lint.py audit src/main/resources/assets/aliveworkplace/textures` ranks every texture.
- `python3 tools/textures/pxlib/preview.py block|item|villager <png> ...` renders a preview sheet (into a temp folder,
  never into `src/`).

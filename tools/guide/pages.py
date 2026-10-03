"""The Guide Book's pictures (ROADMAP 26.2a): for each page of the book, the in-game screenshot it shows and the part of
it to keep. Screenshots come from the screenshot scenes (tools/screenshots/run.sh, SCENE=<name>), as the nightly
showcase films them: https://jcondedata.github.io/minecraft-alive-workplace/<scene>/still-<n>.jpg.

Each entry: page id -> (scene, still, crop). The crop is (x, y, width) in the 960 x 540 still; the height follows at
16:9. None keeps the whole frame. The page list and order are in GuidePages.java; the words in en_us.json.
"""

PAGES = {
    # Getting started
    "welcome": ("builders", 3, None),
    "camp": ("camp", 1, (235, 140, 480)),
    "jobs": ("stations", 1, (0, 135, 720)),
    "status": ("preview", 3, None),
    # Builders
    "table": ("staff", 2, (40, 230, 480)),
    "place": ("preview", 1, None),
    "stock": ("missing", 2, None),
    "build": ("preview", 2, None),
    "styles": ("style_menu", 1, None),
    "library": ("table", 1, None),
    "shapes": ("shapes", 1, None),
    # Your village
    "hall": ("hall", 1, None),
    "people": ("hall", 3, (300, 40, 640)),
    "requests": ("hall", 4, (280, 20, 680)),
    "storehouse": ("porter", 3, (180, 100, 480)),
    "villages": ("village", 3, None),
    # Gathering jobs
    "miner": ("quarry", 2, (290, 140, 420)),
    "lumberjack": ("forest", 2, (330, 90, 560)),
    "farmer": ("farm", 2, (300, 110, 500)),
    "orchard": ("orchard", 3, (330, 170, 420)),
    "fisher": ("extras", 1, (300, 190, 400)),
    "beekeeper": ("beekeeper", 2, (215, 150, 420)),
    # Making, carrying, trading
    "carpenter": ("carpenter", 2, (220, 210, 480)),
    "chef": ("chef", 2, (237, 190, 480)),
    "mail": ("mail", 1, None),
    "shop": ("shop", 1, None),
    "ferry": ("extras", 3, (280, 140, 480)),
    # Keeping the village safe
    "guard": ("guard", 1, (282, 220, 400)),
    "training": ("guard", 4, (260, 190, 600)),
    "nurse": ("nurse", 2, (295, 200, 480)),
    # Pokémon jobs (with Cobblemon)
    "trainer": ("battle", 2, None),
    "tutor": ("tutor", 1, None),
    "ball_smith": ("smith", 1, (150, 160, 600)),
    "trader": ("trader", 1, None),
    "fossil": ("fossil", 2, (182, 200, 480)),
}

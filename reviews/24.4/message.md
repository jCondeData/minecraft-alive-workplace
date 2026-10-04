Review 24.4: Screens at GUI scales 2 and 4
What changed: every screen was filmed at GUI scale 2 and 4 (Blueprint Table, Village Hall and its pages, storehouse/requests, mailbox, shop, Shop Counter, research). Two clipping bugs fixed: the Shop Counter title ran off its panel (now "Goods on top, prices below"), and in the Blueprint Table the size line touched the selection box and 3-digit material counts touched the next icon. Item counts now read "28× Spruce Planks" everywhere (hall requests, research costs, shop log), like the builder already did. New: a vanilla-style settings screen (Mod Menu Configure button) for every config option, each with a tooltip.
Please judge: 1) Is "Goods on top, prices below" a clear title for the Shop Counter? 2) Are the settings labels clear (e.g. "Chest Reach", "Population Cap", "Daily Takings")?
Tester: every scene check passed at both scales (11 scenes); a new harness check fails any scene whose screen title is wider than its panel. ConfigGameTests and WordsGameTests pass (6/6). Not tested: Mod Menu itself in a real client (the screen is opened directly by the bot).
Scene: SCENE=<table|hall|hall_pages|hall_quests|porter|mail|shop|counter|scholar|config> GUI_SCALE=4 tools/screenshots/run.sh
Reply: approve 24.4 · veto 24.4: why · change 24.4: what

(from lane-b-1004-0332, 2026-10-04 04:50Z; not yet sent)

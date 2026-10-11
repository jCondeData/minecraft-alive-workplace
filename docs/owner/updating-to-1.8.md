# Updating your server to 1.8

1.8 gives every villager a class (Peasant, Artisan, Burgher, Noble), an age and a family name. Your server already has
villages, so the update switches all of that on by itself the first time 1.8 runs. We rehearsed it on a copy of a real
pack world before release ([the rehearsal's report](rehearsal-1.8.md)). Still, back up first. It takes five minutes and
it's the only way back if something goes wrong.

## Before you update: back up the world

1. **Tell the players** the server is going down for a few minutes, then **stop the server** (type `stop` in the
   console, or press Stop on your host's panel). Wait until it says it has stopped. Copying a world while it runs can
   give you a broken copy.
2. **Copy the world folder.** It's called `world` (or whatever `level-name` says in `server.properties`), next to the
   `mods` folder. On a host's panel, use its **Backups** button if it has one, or its file manager: download `world` as a
   zip. On your own computer, copy the folder somewhere else and add the date to the copy's name, e.g.
   `world-backup-2026-10-17`.
3. **Copy the old mod jar too** (`mods/alive-workplace-0.x.x+1.21.1.jar`) and the `config/aliveworkplace.json` file, into
   the same backup place. With those three you can always go back to exactly where you were.
4. **Check the backup.** The zip or folder should be about as big as the original world (often hundreds of MB). If it's
   tiny, something went wrong: don't go on.

## The update

5. In `mods/`, **delete the old** `alive-workplace-…jar` and **put in the new 1.8 jar**. Keep only one Alive Workplace
   jar in the folder.
6. **Start the server.** It's normal for the first start to take a little longer.

## If something goes wrong

Stop the server, delete the `world` folder, put your backup copy back under the same name, put the old jar back in
`mods/` (and take the 1.8 jar out), and start the server. Everything is as it was the moment you stopped it. Then
tell me what you saw.

If you only want the classes off (everything else from 1.8 stays), set `"villageClasses": false` in
`config/aliveworkplace.json` and restart: nothing is lost, and switching it back on later picks up where it left off.

## What your villagers do in the first week

**The first two minutes.** Each village with a hall sorts its people into classes, 16 villagers every 30 seconds, so
even a big City is done in about a minute and a half. Each household gets the best class its home, food and the
village's services already earn (at most Burgher: Nobles have to earn it). Children take their parents' class. Every
grown-up gets an age and every family a family name. Players near a village see one chat line, and the hall's
chronicle gets an entry like "The people of Thornholm found their places: 14 peasants, 6 artisans, 2 burghers".
Players far away don't see anything happen at all.

**Nothing you built or earned changes.** Nobody loses their job, even if their new class is too low for it (they keep
working; only new hires need the class). Village ranks stay. The treasury takes in at least what it did before.
Build sites, quarries, Pokémon in daycare, fossils being revived, parcels in the post and every chest stay exactly as
they were. In the rehearsal, all of this was checked against the world before the update.

**Days 1 to 7: settling in.** The hall's Classes page says "Settling in: 5 days left" and shows what each class will
need. During the week:
- nobody drops a class;
- villagers start asking for luxuries (wine, clothes, jewellery, books), but going without them doesn't count against
  them yet;
- no elder retires;
- households whose homes and food are good enough can rise a class after 2 good days running.

This is the week to give your Artisans and Burghers what they'll need: a Vintner, a Tailor, a Jeweller or a Printer
(villagers take these jobs at a cauldron, loom, stonecutter or cartography table), and better homes (the new
Artisan's House, Burgher's Townhouse and Noble's Manor are in the Blueprint Table).

**After day 7.** The normal rules apply: a household that goes 3 days without what its class needs drops one class,
luxuries count, and higher classes pay more tax. Villagers wear their class over their work clothes, so you can see
who has risen.

**Weeks later.** Villagers grow old. Their ages were spread out at the update, so the first elders appear about three
weeks after it, one at a time, not all at once. An elder hands their job to an apprentice before they retire.

## Commands, if you want to look

- `/workplace classes status` near a hall: whether it's still sorting, settling in or settled, and how many of each
  class it has.
- `/workplace classes reseed` sorts a village again from scratch, with a new settling-in week (operators only).

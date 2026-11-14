# Riding looks right, and the scenes are clean

How villagers look when they ride something, and the pass over the filmed scenes for clipping, floating and
z-fighting (Milestone 24, "Everything looks finished"). The riding itself (fishers in boats, ferry rides, cavalry) is
older; this page is about how it looks.

Roadmap items: 24.3

## What a player sees

**Villagers sit when they ride.** A guard on horseback (or on a donkey, mule or camel) sits in the saddle with their
legs forward, the way a player does. A ferryman or a fisher sits in the boat at the oars. Before, a villager stood on
the horse's back and looked sunk in the boat. A villager in a minecart sits in it the same way.

**The ferry floats.** When a Travel Ticket starts a ferry ride while you stand or swim in water, the boat is put on
top of the water, not under it, and you and the ferryman sit in it.

**The scenes are clean.** The staff, extras (boat, horse, ferry) and village scenes were filmed and every still was
checked: no clipping, floating or z-fighting in the game itself.

## How it works

- **The seat.** The game gives villagers no riding height, so their feet went where a player's hips go.
  `VillagerSeatMixin` (in `mixin/`) gives every villager a player's riding offset: their hips 0.6 blocks above their
  feet, half that for a baby villager. It is only for villagers: a wandering trader or a witch sits as vanilla has it.
- **The pose.** The villager model has no riding pose of its own. The client's `VillagerModelMixin` turns both legs
  forward and a little outward while a villager is a passenger, as a player's legs are when riding. The model's parts
  are shared by every villager drawn, so the legs' sideways turn is put back for villagers who aren't riding.
- **The ferry boat.** `FerryRides` launches the ride's boat where the player stands; when that spot is in water, it
  climbs to the top of the water and puts the boat there. The ferryman is seated first, at the oars (`Boats`), so he
  rows the boat, player and all.
- **The village scene's camera.** The screenshot bot's fixed camera spot for each workshop was sometimes inside the
  next house, so the still showed only a log. The bot now tries the four diagonals at a few distances and heights,
  the south-east first (the old spot), and takes the first spot in air whose line of sight reaches the workshop. This
  is a fix to the filming, not to the game.

## Switches

none

## Saved data

none

## Items, blocks, jobs, commands

none

## Decisions

- **A villager sits exactly where a player would** (the seat is a player's own riding offset), so riders line up with
  vanilla's saddles, boats and minecarts without a number per mount. Riding looked right and was approved on
  2026-09-29 (24.3).
- **Only villagers sit this way**: wandering traders and witches share the model but not the seat, so their pose is
  left alone too (the mixins' own comments).
- **24.3 found nothing to fix in the game**: the review package showed the scenes clean, with the village scene's
  camera fixed; the owner approved it on 2026-10-04.

## Known limits

- The check was by eye, on the stills of the staff, extras and village scenes at GUI scale 2. Scenes added since are
  checked by the nightly showcase, not by a second 24.3 pass.
- The sitting pose is drawn by the client. No GameTest can see it; only the stills show it.

## Proof

- `RidingSpecGameTests.villagersSitInAMinecartLikeAPlayerAndTradersAreUnchanged`: a villager's seat is a player's,
  the villager sits at that height in a minecart, and a wandering trader in a boat sits where vanilla puts it.
- `RidingSpecGameTests.theFerrymanRowsTowardsTheFarShore`: a ride started by a player in the water puts them in the
  boat with a villager at the oars, rowed toward the far shore.
- `RidingSpecGameTests.aGuardRidesASaddledCamel` and `RidingSpecGameTests.aFisherRowsOutBeforeFishing`: the guard and
  the fisher actually get on what they ride.
- Showcase scenes: `extras` (a fisher in a boat, a guard on horseback, the ferry ride), `staff`, `village`.

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ships;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane D, 2026-11-21) for B1317, from ROADMAP 32.8 ("what sank below the waterline turns to sea"): five seconds after
 * a pirate ship has sunk, every spot of it below the waterline is still sea water (a source, not water running down),
 * nothing above the waterline is water, and the ground round it is left as it was. The earlier tests
 * ({@link QaShipsMutants1116dBugTests}) sink one ship, bow west, in deep water; these turn the ship the other ways,
 * run it aground on a shoal and a sandbar that rises above the waterline, and let it sink while nobody watched (its
 * ground loaded again long after the sinking began, so it goes down all at once).
 */
public class QaShipsSinking1121dGameTests {
	private static final String AREA = "aliveworkplace_test:tall_area";
	private static final ResourceLocation SHIP = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "camp/pirate_ship");
	/** drowned_pirates.json's sea: the waterline at template y 2. */
	private static final Culture.Sea SEA = new Culture.Sea(2, 40, 70, 5, true);
	/** The sea's top block (helper y). */
	private static final int WATER_TOP = 7;
	private static final BlockPos MIDDLE = new BlockPos(15, WATER_TOP, 15);

	/**
	 * A walled pool over the whole area, water y 2 to 7 and air above; columns where {@code bed} gives a height stand in
	 * stone up to it (a shoal under the waterline, or a sandbar above it).
	 */
	private static void sea(GameTestHelper helper, java.util.function.IntBinaryOperator bed) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				int top = bed.applyAsInt(x, z);
				for (int y = 2; y <= 26; y++) {
					BlockState block;
					if (x == 0 || x == 29 || z == 0 || z == 29) {
						block = y <= WATER_TOP + 2 ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState();
					} else if (y <= top) {
						block = Blocks.STONE.defaultBlockState();
					} else {
						block = y <= WATER_TOP ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
					}
					level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), block, 2);
				}
			}
		}
	}

	/**
	 * The pirate ship anchored in the middle with its bow toward {@code toward} (a village hall far off that way), then sunk
	 * as when its captain falls; checked five seconds after the sinking ends. {@code unwatched}: just after the sinking
	 * begins, it is saved as if its ground were unloaded for a long while (began long ago), so the next tick takes it all down at once.
	 */
	private static void sinks(GameTestHelper helper, BlockPos toward, java.util.function.IntBinaryOperator bed, boolean unwatched, String what) {
		sea(helper, bed);
		ServerLevel level = helper.getLevel();
		BlockPos at = helper.absolutePos(MIDDLE);
		BlockPos hall = at.offset(toward);
		Map<BlockPos, BlockState> outside = new HashMap<>();
		Ships.Placed[] ship = {null};
		Map<BlockPos, BlockState> blocks = new HashMap<>();
		long[] sunk = {0};
		Leftovers.after(helper, () -> Ships.sailAway(level, hall)); // (nothing left anchored under a hall that isn't there)
		helper.runAfterDelay(2, () -> {
			StructureTemplate template = level.getStructureManager().get(SHIP).orElseThrow();
			ship[0] = Ships.place(level, hall, at, SHIP, template, SEA);
			helper.assertValueEqual(ship[0].waterTop(), helper.absolutePos(MIDDLE).getY(), "setup: the ship's waterline");
			blocks.putAll(Ships.blocks(level, ship[0]));
			helper.assertTrue(blocks.size() > 100, "setup: the ship has " + blocks.size() + " blocks");
			for (int x = 1; x < 29; x++) {
				for (int z = 1; z < 29; z++) {
					for (int y = 2; y <= 26; y++) {
						BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
						if (!blocks.containsKey(p)) {
							outside.put(p, level.getBlockState(p));
						}
					}
				}
			}
			Ships.sink(level, hall);
			sunk[0] = level.getGameTime();
			helper.assertTrue(Ships.of(level, hall).isEmpty() && Ships.sinking(level).stream().anyMatch(s -> s.ship().equals(ship[0])),
				"setup: the ship isn't sinking");
		});
		if (unwatched) {
			helper.runAfterDelay(3, () -> {
				Ships.Data data = Ships.Data.get(level);
				CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
				ListTag going = tag.getList("sinking", Tag.TAG_COMPOUND);
				int found = 0;
				for (int i = 0; i < going.size(); i++) {
					CompoundTag c = going.getCompound(i);
					if (c.getLong("origin") == ship[0].origin().asLong()) {
						c.putLong("began", level.getGameTime() - 10 * Ships.SINK_TICKS);
						found++;
					}
				}
				helper.assertValueEqual(found, 1, "setup: the sinking ship in the saved data");
				data.read(tag);
			});
		}
		int end = unwatched ? 3 + 2 : 2 + Ships.SINK_TICKS + 1;
		helper.runAfterDelay(end + 100, () -> {
			helper.assertTrue(Ships.sinking(level).stream().noneMatch(s -> s.ship().equals(ship[0])), "the ship is still sinking (" + what + ")");
			int waterTop = ship[0].waterTop();
			List<String> notSea = new ArrayList<>();
			List<String> wetAbove = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
				BlockPos p = e.getKey();
				BlockState now = level.getBlockState(p);
				if (p.getY() <= waterTop) {
					if (!(now.is(Blocks.WATER) && now.getFluidState().isSource())) {
						notSea.add(helper.relativePos(p) + " " + now + " (was " + e.getValue() + ")");
					}
				} else if (level.getFluidState(p).is(FluidTags.WATER)) {
					wetAbove.add(helper.relativePos(p) + " " + now);
				}
			}
			helper.assertTrue(notSea.isEmpty(), what + ": five seconds after the ship sank " + notSea.size()
				+ " spots of it below the waterline aren't still sea: " + notSea.subList(0, Math.min(6, notSea.size())));
			helper.assertTrue(wetAbove.isEmpty(), what + ": " + wetAbove.size() + " spots of the ship above the waterline are water: "
				+ wetAbove.subList(0, Math.min(6, wetAbove.size())));
			List<String> changed = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : outside.entrySet()) {
				BlockPos p = e.getKey();
				BlockState now = level.getBlockState(p);
				boolean wasWet = e.getValue().getFluidState().is(FluidTags.WATER);
				if (p.getY() > waterTop && level.getFluidState(p).is(FluidTags.WATER)) {
					changed.add(helper.relativePos(p) + " water above the waterline: " + now);
				} else if (!wasWet && !e.getValue().isAir() && !now.equals(e.getValue()) && !now.is(Blocks.SPRUCE_LOG) && !now.is(Blocks.CHEST)) {
					changed.add(helper.relativePos(p) + " " + e.getValue() + " -> " + now); // the ground round it (not the raft)
				} else if (wasWet && e.getValue().getFluidState().isSource() && !now.is(Blocks.SPRUCE_LOG)
						&& !(now.is(Blocks.WATER) && now.getFluidState().isSource())) {
					changed.add(helper.relativePos(p) + " sea round the ship -> " + now);
				}
			}
			helper.assertTrue(changed.isEmpty(), what + ": " + changed.size() + " spots round the ship changed: " + changed.subList(0, Math.min(6, changed.size())));
			helper.succeed();
		});
	}

	private static int deep(int x, int z) {
		return 1;
	}

	/** The ship turned bow east (the other way from the earlier tests) sinks to still sea. */
	//$ gametest_ticks_batch AREA '420' '"qa1121dSinkEast"'
	@GameTest(template = AREA, timeoutTicks = 420, batch = "qa1121dSinkEast")
	public void qaABowEastShipSinksToStillSea(GameTestHelper helper) {
		sinks(helper, new BlockPos(200, 0, 0), QaShipsSinking1121dGameTests::deep, false, "bow east");
	}

	/** The ship turned bow south, lying along z, sinks to still sea. */
	//$ gametest_ticks_batch AREA '420' '"qa1121dSinkSouth"'
	@GameTest(template = AREA, timeoutTicks = 420, batch = "qa1121dSinkSouth")
	public void qaABowSouthShipSinksToStillSea(GameTestHelper helper) {
		sinks(helper, new BlockPos(0, 0, 200), QaShipsSinking1121dGameTests::deep, false, "bow south");
	}

	/**
	 * Aground: the west end of the ship over a shoal whose top (y 6) is above the hold's floor, and its stern over a
	 * sandbar rising above the waterline (y 9). Every ship spot under the waterline still ends sea, none above it is water,
	 * and the shoal and sandbar round the ship stay as they were.
	 */
	//$ gametest_ticks_batch AREA '420' '"qa1121dSinkAground"'
	@GameTest(template = AREA, timeoutTicks = 420, batch = "qa1121dSinkAground")
	public void qaAShipAgroundOnAShoalAndASandbarSinksOnlyBelowTheWaterline(GameTestHelper helper) {
		sinks(helper, new BlockPos(-200, 0, 0), (x, z) -> x <= 4 ? WATER_TOP + 2 : x <= 11 ? WATER_TOP - 1 : 1, false, "aground");
	}

	/** Sunk while nobody watched: its ground loaded again long after the sinking began, so it all goes down in one tick. */
	//$ gametest_ticks_batch AREA '300' '"qa1121dSinkUnwatched"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qa1121dSinkUnwatched")
	public void qaAShipThatSankWhileUnloadedStillEndsStillSea(GameTestHelper helper) {
		sinks(helper, new BlockPos(200, 0, 0), QaShipsSinking1121dGameTests::deep, true, "unwatched");
	}
}

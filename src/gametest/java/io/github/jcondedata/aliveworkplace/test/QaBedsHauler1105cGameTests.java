package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (2026-11-05) of the fixes for B925 and B944, written from the bugs' text.
 * <p>
 * B925: a villager without a bed who is already upstairs in one Terrace bedroom at bedtime, both its beds someone
 * else's, goes down his ladder, takes a free bed next door and sleeps in it. Variants: the mirror case (up in the west
 * bedroom, the east one free), one of the two free beds taken just before bedtime (he takes the other), a save and
 * reload while he walks there, and both homes full (he stays homeless and takes nobody's bed).
 * <p>
 * B944: a loaded Hauler Golem that starts right beside a two-wide lava trench a block below the ground steps away from
 * it, goes round and stores its whole load, never touching the lava. Variants: the trench on its other side (Storehouse
 * to the west), a trench running the other way, a mixed load counted item by item, and a save and reload mid-walk.
 */
public class QaBedsHauler1105cGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final int TOP = QaHaulerWaterVerify1102GameTests.TOP;

	// --- B925: the Terrace's beds ---------------------------------------------------------------------------------

	/** The Terrace placed in the test, with its four beds sorted west to east (0, 1 the west home; 2, 3 the east one). */
	private record Terrace(BoundingBox box, List<BlockPos> beds) {
	}

	private static Terrace terrace(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("terrace")).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
		house.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(3042L), 2);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(house.getSize()).offset(-1, -1, -1));
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 16, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(Comparator.comparingInt(BlockPos::getX)).toList();
		helper.assertTrue(beds.size() == 4, "setup: the Terrace has " + beds.size() + " beds");
		return new Terrace(box, beds);
	}

	/** Someone else's claim on {@code bed} (its only ticket taken). */
	private static void take(GameTestHelper helper, BlockPos bed) {
		helper.getLevel().getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
	}

	private static boolean free(GameTestHelper helper, BlockPos bed) {
		return helper.getLevel().getPoiManager().exists(bed, h -> h.is(PoiTypes.HOME))
			&& helper.getLevel().getPoiManager().getFreeTickets(bed) > 0;
	}

	/** A homeless villager on the floor of the bedroom of {@code near} (the nearest open floor block to that bed). */
	private static Villager upstairsBy(GameTestHelper helper, Terrace t, BlockPos near) {
		ServerLevel level = helper.getLevel();
		BlockPos stand = null;
		for (BlockPos p : BlockPos.betweenClosed(t.box().minX(), near.getY(), t.box().minZ(), t.box().maxX(), near.getY(), t.box().maxZ())) {
			boolean open = level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
				&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
			if (open && p.distManhattan(near) <= 3 && (stand == null || p.distManhattan(near) < stand.distManhattan(near))) {
				stand = p.immutable();
			}
		}
		helper.assertTrue(stand != null, "setup: no floor in the bedroom by " + helper.relativePos(near).toShortString());
		Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(stand));
		villager.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
		helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).isEmpty(), "setup: the villager has a home already");
		return villager;
	}

	private static String where(GameTestHelper helper, Villager v) {
		return "at " + helper.relativePos(v.blockPosition()).toShortString() + ", home " + v.getBrain().getMemory(MemoryModuleType.HOME)
			.map(g -> helper.relativePos(g.pos()).toShortString()).orElse("none") + ", asleep " + v.isSleeping();
	}

	private static void sleepsIn(GameTestHelper helper, Villager v, List<BlockPos> wanted, String what) {
		helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> wanted.contains(g.pos())).isPresent(),
			what + ": hasn't claimed one of " + wanted.stream().map(b -> helper.relativePos(b).toShortString()).toList() + "; " + where(helper, v));
		helper.assertTrue(v.isSleeping() && v.getSleepingPos().filter(wanted::contains).isPresent(), what + ": not asleep in it; " + where(helper, v));
	}

	/** B925 mirrored: up in the west bedroom (both its beds taken), he goes down and sleeps in the east home's free bed. */
	//$ gametest_ticks_batch HUGE '2400' '"qa1105cTerraceMirror"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1105cTerraceMirror")
	public void qaUpInTheWestBedroomTakesAFreeBedInTheEastHome(GameTestHelper helper) {
		Terrace t = terrace(helper);
		take(helper, t.beds().get(0));
		take(helper, t.beds().get(1));
		Villager v = upstairsBy(helper, t, t.beds().get(1));
		helper.setDayTime(13000);
		List<BlockPos> east = t.beds().subList(2, 4);
		helper.succeedWhen(() -> sleepsIn(helper, v, east, "homeless, up in the west bedroom, the east home free"));
	}

	/**
	 * B925 with one of the two free beds next door taken by someone else just as night falls: he still takes the other
	 * one (never the taken one) and sleeps in it.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1105cTerraceLastBed"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1105cTerraceLastBed")
	public void qaOneFreeBedNextDoorTakenAtNightfallHeTakesTheOther(GameTestHelper helper) {
		Terrace t = terrace(helper);
		take(helper, t.beds().get(2));
		take(helper, t.beds().get(3));
		Villager v = upstairsBy(helper, t, t.beds().get(2));
		helper.setDayTime(13000);
		take(helper, t.beds().get(0)); // just before he can claim it
		helper.assertFalse(free(helper, t.beds().get(0)), "setup: the west home's first bed is still free");
		helper.succeedWhen(() -> sleepsIn(helper, v, List.of(t.beds().get(1)), "one free bed left next door"));
	}

	/**
	 * B925 with a save and reload of the villager while he is on his way (has claimed a bed next door, not asleep yet,
	 * moved off his starting block): the reloaded villager still gets there and sleeps in the bed he claimed.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1105cTerraceReload"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1105cTerraceReload")
	public void qaSavedAndReloadedOnTheWayToTheFreeBedStillSleepsInIt(GameTestHelper helper) {
		Terrace t = terrace(helper);
		take(helper, t.beds().get(2));
		take(helper, t.beds().get(3));
		Villager first = upstairsBy(helper, t, t.beds().get(2));
		BlockPos start = first.blockPosition();
		helper.setDayTime(13000);
		List<BlockPos> west = t.beds().subList(0, 2);
		Villager[] v = {first};
		boolean[] reloaded = {false};
		BlockPos[] claimed = {null};
		helper.onEachTick(() -> {
			if (reloaded[0] || v[0].isSleeping()) {
				return;
			}
			var home = v[0].getBrain().getMemory(MemoryModuleType.HOME).filter(g -> west.contains(g.pos()));
			if (home.isPresent() && v[0].blockPosition().distManhattan(start) >= 2) {
				claimed[0] = home.get().pos();
				CompoundTag tag = v[0].saveWithoutId(new CompoundTag());
				v[0].discard();
				Villager copy = EntityType.VILLAGER.create(helper.getLevel());
				helper.assertTrue(copy != null, "a villager to load into");
				copy.load(tag);
				helper.getLevel().addFreshEntity(copy);
				v[0] = copy;
				reloaded[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "never saw him on his way to a claimed west bed to reload him; " + where(helper, v[0]));
			sleepsIn(helper, v[0], List.of(claimed[0]), "reloaded on his way next door");
		});
	}

	/**
	 * Both homes full (all four beds someone else's): the villager up in the east bedroom stays homeless all night, takes
	 * nobody's bed and sleeps in none, and every bed keeps its owner's claim.
	 */
	//$ gametest_ticks_batch HUGE '1400' '"qa1105cTerraceFull"'
	@GameTest(template = HUGE, timeoutTicks = 1400, batch = "qa1105cTerraceFull")
	public void qaBothHomesFullHeStaysHomelessAndStealsNoBed(GameTestHelper helper) {
		Terrace t = terrace(helper);
		for (BlockPos bed : t.beds()) {
			take(helper, bed);
		}
		Villager v = upstairsBy(helper, t, t.beds().get(2));
		helper.setDayTime(13000);
		helper.onEachTick(() -> {
			if (v.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> t.beds().contains(g.pos())).isPresent()
				|| v.getSleepingPos().filter(t.beds()::contains).isPresent()) {
				helper.fail("every bed is someone else's, yet he took one: " + where(helper, v) + " on tick " + helper.getTick());
			}
		});
		helper.runAtTickTime(1200, () -> {
			for (BlockPos bed : t.beds()) {
				helper.assertFalse(free(helper, bed), "bed " + helper.relativePos(bed).toShortString() + " lost its owner's claim");
			}
			helper.assertTrue(v.isAlive(), "the villager died: " + where(helper, v));
			helper.succeed();
		});
	}

	// --- B944: a loaded hauler beside sunken lava ------------------------------------------------------------------

	/**
	 * A loaded hauler starting at {@code start}, a two-wide lava trench a block below the ground over {@code xs}..
	 * {@code xe}, {@code zs}..{@code ze}, and its only Storehouse at {@code store} with its chest at {@code chest}: no tick
	 * in lava, on fire or hurt, and every item of {@code load} stored (nothing carried, nothing dropped). With
	 * {@code reload}, the golem is saved and reloaded once it has walked 3 blocks.
	 */
	private static void trench(GameTestHelper helper, int xs, int xe, int zs, int ze, BlockPos start, BlockPos store, BlockPos chest,
			Map<Item, Integer> load, boolean reload, String what) {
		QaHaulerWaterVerify1102GameTests.setup(helper);
		for (int x = xs; x <= xe; x++) {
			for (int z = zs; z <= ze; z++) {
				QaHaulerWaterVerify1102GameTests.liquid(helper, x, z, 1, 1, Blocks.LAVA);
			}
		}
		helper.setBlock(store, ModBlocks.STOREHOUSE);
		helper.setBlock(chest, Blocks.CHEST);
		IronGolem[] golem = {QaHaulerWaterVerify1102GameTests.hauler(helper, start)};
		ModAttachments.HAULER_LOAD.set(golem[0], load);
		float health = golem[0].getHealth();
		BlockPos from = golem[0].blockPosition();
		boolean[] done = {!reload};
		helper.onEachTick(() -> {
			IronGolem g = golem[0];
			if (g.isInLava() || g.isOnFire() || g.getHealth() < health || !g.isAlive()) {
				helper.fail(what + ": the hauler went into the lava: in lava " + g.isInLava() + ", on fire " + g.isOnFire() + ", health "
					+ g.getHealth() + " of " + health + " at " + helper.relativeVec(g.position()) + " on tick " + helper.getTick());
			}
			if (!done[0] && g.blockPosition().distManhattan(from) >= 3 && QaHaulerWaterVerify1102GameTests.carried(g) > 0) {
				golem[0] = QaHaulerWaterVerify1102GameTests.reloaded(helper, g);
				done[0] = true;
				helper.assertTrue(HaulerGolems.load(golem[0]).equals(load), what + ": the reloaded golem carries " + HaulerGolems.load(golem[0]));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(done[0], what + ": the golem never walked off to be reloaded, at " + helper.relativeVec(golem[0].position()));
			QaHaulerWaterVerify1102GameTests.delivered(helper, golem[0], chest, load, what);
		});
	}

	/** Mirror of B944: the hauler starts just east of the trench, its Storehouse to the west. */
	//$ gametest_ticks_batch HUGE '1600' '"qa1105cLavaEast"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1105cLavaEast")
	public void qaAHaulerStartingEastOfASunkenLavaTrenchStoresItsLoadWest(GameTestHelper helper) {
		trench(helper, 12, 13, 7, 17, new BlockPos(15, TOP - 1, 12), new BlockPos(4, TOP, 12), new BlockPos(3, TOP, 12),
			Map.of(Items.OAK_LOG, 64), false, "starting just east of a sunken two-wide lava trench");
	}

	/** B944 turned a quarter: the trench runs east-west just south of the hauler, its Storehouse beyond it. */
	//$ gametest_ticks_batch HUGE '1600' '"qa1105cLavaNorth"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1105cLavaNorth")
	public void qaAHaulerStartingBesideAnEastWestLavaTrenchStoresItsLoad(GameTestHelper helper) {
		trench(helper, 7, 17, 12, 13, new BlockPos(12, TOP - 1, 10), new BlockPos(12, TOP, 20), new BlockPos(12, TOP, 21),
			Map.of(Items.OAK_LOG, 64), false, "starting just north of a sunken east-west lava trench");
	}

	/** B944 with a mixed load: every log, cobblestone and diamond reaches the Storehouse chest, counted item by item. */
	//$ gametest_ticks_batch HUGE '1600' '"qa1105cLavaMixed"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1105cLavaMixed")
	public void qaAHaulerBesideASunkenLavaTrenchStoresAMixedLoadFullyCounted(GameTestHelper helper) {
		Map<Item, Integer> load = new LinkedHashMap<>();
		load.put(Items.OAK_LOG, 64);
		load.put(Items.COBBLESTONE, 40);
		load.put(Items.DIAMOND, 3);
		trench(helper, 12, 13, 7, 17, new BlockPos(10, TOP - 1, 12), new BlockPos(20, TOP, 12), new BlockPos(21, TOP, 12),
			load, false, "a mixed load from beside a sunken lava trench");
	}

	/** B944 with a save and reload of the golem once it has stepped away from the trench: the load still arrives whole. */
	//$ gametest_ticks_batch HUGE '1800' '"qa1105cLavaReload"'
	@GameTest(template = HUGE, timeoutTicks = 1800, batch = "qa1105cLavaReload")
	public void qaAHaulerReloadedAfterSteppingAwayFromSunkenLavaStillStoresItsLoad(GameTestHelper helper) {
		trench(helper, 12, 13, 7, 17, new BlockPos(10, TOP - 1, 12), new BlockPos(20, TOP, 12), new BlockPos(21, TOP, 12),
			Map.of(Items.OAK_LOG, 64), true, "reloaded after stepping away from a sunken lava trench");
	}
}

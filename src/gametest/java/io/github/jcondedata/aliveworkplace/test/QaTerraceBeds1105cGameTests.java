package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane C, 2026-11-05) for B925 (and B304 around it), written from the bug's words: "a villager without a bed who is
 * already upstairs in the Terrace's east bedroom at bedtime (both its beds someone else's) ... Expected: he goes down his
 * ladder, takes a free bed next door and sleeps in it." These cover the mirror (west bedroom), no free bed anywhere, one
 * free bed and two homeless, the free bed broken on his way, his villager saved and loaded mid-walk, a villager with his
 * own bed upstairs, and B304's homeless villager downstairs in the west home.
 */
public class QaTerraceBeds1105cGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HOUSE = new BlockPos(8, 2, 8);

	/** The Terrace placed at {@link #HOUSE}: its origin and its four beds' heads, west to east (0-1 west home, 2-3 east). */
	private record Terrace(BlockPos origin, BoundingBox box, List<BlockPos> beds) {
	}

	private static Terrace terrace(GameTestHelper helper, long seed) {
		ServerLevel level = helper.getLevel();
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("terrace")).orElseThrow();
		BlockPos origin = helper.absolutePos(HOUSE);
		house.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(seed), 2);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(house.getSize()).offset(-1, -1, -1));
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 16, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(java.util.Comparator.comparingInt(BlockPos::getX)).toList();
		helper.assertTrue(beds.size() == 4, "setup: the Terrace has " + beds.size() + " beds");
		return new Terrace(origin, box, beds);
	}

	/** Takes the bed's POI ticket for someone who isn't in the test (someone else's bed). */
	private static void taken(GameTestHelper helper, BlockPos bed) {
		helper.getLevel().getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
	}

	/** An open floor block on the level of {@code bed} (or any block), nearest it within 3 blocks: not a bed or a ladder. */
	private static BlockPos inBedroomOf(GameTestHelper helper, Terrace t, BlockPos bed) {
		ServerLevel level = helper.getLevel();
		BlockPos stand = null;
		for (BlockPos p : BlockPos.betweenClosed(t.box().minX(), bed.getY(), t.box().minZ(), t.box().maxX(), bed.getY(), t.box().maxZ())) {
			boolean open = level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
				&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP);
			if (open && !level.getBlockState(p).is(net.minecraft.world.level.block.Blocks.LADDER) && p.distManhattan(bed) <= 3 && (stand == null || p.distManhattan(bed) < stand.distManhattan(bed))) {
				stand = p.immutable();
			}
		}
		helper.assertTrue(stand != null, "setup: no floor in the bedroom of " + helper.relativePos(bed).toShortString());
		return stand;
	}

	private static Villager spawnAt(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(at));
		villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		return villager;
	}

	private static Optional<BlockPos> home(Villager v) {
		return v.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos);
	}

	private static String where(GameTestHelper helper, Villager v) {
		return "at " + helper.relativePos(v.blockPosition()).toShortString() + ", home " + home(v).map(p -> helper.relativePos(p).toShortString())
			+ ", asleep " + v.getSleepingPos().map(p -> helper.relativePos(p).toShortString()) + ", activity " + v.getBrain().getActiveNonCoreActivity();
	}

	private static String beds(GameTestHelper helper, Terrace t) {
		return "beds " + t.beds().stream().map(b -> helper.relativePos(b).toShortString()).toList();
	}

	/**
	 * B925's mirror: a villager with no bed is already up in the Terrace's WEST bedroom at bedtime, both its beds someone
	 * else's; the east home's two beds are free. He claims one of them and sleeps in it.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1105cMirror"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1105cMirror")
	public void qaAHomelessVillagerUpInTheWestBedroomTakesAFreeBedInTheEastHome(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Terrace t = terrace(helper, 1105L);
		t.beds().subList(0, 2).forEach(b -> taken(helper, b));
		List<BlockPos> free = t.beds().subList(2, 4);
		Villager villager = spawnAt(helper, inBedroomOf(helper, t, t.beds().get(1)));
		helper.setDayTime(13000);
		helper.succeedWhen(() -> {
			helper.assertTrue(home(villager).filter(free::contains).isPresent(),
				beds(helper, t) + "; the homeless villager up in the west bedroom hasn't claimed a free east bed: " + where(helper, villager));
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(free::contains).isPresent(),
				"not asleep in the east home: " + where(helper, villager));
		});
	}

	/**
	 * Every bed of the Terrace is someone else's and two homeless villagers stand in the east bedroom at bedtime: neither
	 * claims a bed that is taken, and neither ever lies down in one, all through the night's first 1,500 ticks.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qa1105cAllFull"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qa1105cAllFull")
	public void qaWithEveryBedTakenAHomelessVillagerUpstairsStealsNone(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Terrace t = terrace(helper, 1106L);
		t.beds().forEach(b -> taken(helper, b));
		BlockPos stand = inBedroomOf(helper, t, t.beds().get(2));
		List<Villager> villagers = List.of(spawnAt(helper, stand), spawnAt(helper, stand));
		helper.setDayTime(13000);
		helper.onEachTick(() -> {
			for (Villager v : villagers) {
				helper.assertFalse(home(v).filter(t.beds()::contains).isPresent(), "a homeless villager took a bed that is someone else's: " + where(helper, v));
				helper.assertFalse(v.isSleeping(), "a homeless villager lay down in a bed: " + where(helper, v));
			}
		});
		helper.runAtTickTime(1500, () -> {
			for (Villager v : villagers) {
				helper.assertTrue(v.isAlive(), "a villager died: " + where(helper, v));
			}
			helper.succeed();
		});
	}

	/**
	 * Only one bed in the Terrace is free (the west home's first) and two homeless villagers stand in the east bedroom at
	 * bedtime: exactly one of them claims it and sleeps in it; the other claims nothing that is taken and lies in no bed.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1105cOneFreeTwoHomeless"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1105cOneFreeTwoHomeless")
	public void qaOneFreeBedNextDoorAndTwoHomelessUpstairsGoesToExactlyOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Terrace t = terrace(helper, 1107L);
		BlockPos free = t.beds().get(0);
		t.beds().subList(1, 4).forEach(b -> taken(helper, b));
		BlockPos stand = inBedroomOf(helper, t, t.beds().get(2));
		Villager a = spawnAt(helper, stand);
		Villager b = spawnAt(helper, stand);
		helper.setDayTime(13000);
		helper.onEachTick(() -> {
			helper.assertFalse(home(a).isPresent() && home(a).equals(home(b)), "both villagers claimed the same bed: " + where(helper, a) + " / " + where(helper, b));
			for (Villager v : List.of(a, b)) {
				helper.assertFalse(home(v).filter(p -> !p.equals(free) && t.beds().contains(p)).isPresent(), "a villager took someone else's bed: " + where(helper, v));
				helper.assertFalse(v.getSleepingPos().filter(p -> !p.equals(free)).isPresent(), "a villager lay down in someone else's bed: " + where(helper, v));
			}
		});
		helper.succeedWhen(() -> {
			Villager sleeper = a.getSleepingPos().filter(free::equals).isPresent() ? a : b;
			Villager other = sleeper == a ? b : a;
			helper.assertTrue(home(sleeper).filter(free::equals).isPresent() && sleeper.getSleepingPos().filter(free::equals).isPresent(),
				beds(helper, t) + "; nobody is asleep in the one free bed " + helper.relativePos(free).toShortString() + ": "
					+ where(helper, a) + " / " + where(helper, b));
			helper.assertFalse(other.isSleeping(), "the second villager is lying down too: " + where(helper, other));
			helper.assertFalse(home(other).isPresent(), "the second villager has a home though no bed was left: " + where(helper, other));
		});
	}

	/**
	 * The only free bed (west home) is broken right after the homeless villager up in the east bedroom claims it, while he
	 * is on his way: nothing goes wrong, he lets the missing bed go and never lies down anywhere.
	 */
	//$ gametest_ticks_batch HUGE '1800' '"qa1105cBedBroken"'
	@GameTest(template = HUGE, timeoutTicks = 1800, batch = "qa1105cBedBroken")
	public void qaTheFreeBedBrokenWhileHeGoesToItIsLetGo(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Terrace t = terrace(helper, 1108L);
		BlockPos free = t.beds().get(0);
		t.beds().subList(1, 4).forEach(b -> taken(helper, b));
		Villager villager = spawnAt(helper, inBedroomOf(helper, t, t.beds().get(2)));
		helper.setDayTime(13000);
		AtomicInteger brokenAt = new AtomicInteger(-1);
		AtomicInteger tick = new AtomicInteger();
		helper.onEachTick(() -> {
			int now = tick.incrementAndGet();
			if (brokenAt.get() < 0 && home(villager).filter(free::equals).isPresent()) {
				helper.assertFalse(villager.isSleeping(), "setup: he was already asleep when the bed was to be broken");
				level.destroyBlock(free, false);
				brokenAt.set(now);
			}
			if (brokenAt.get() >= 0) {
				helper.assertFalse(villager.isSleeping(), "he lay down after his bed was broken: " + where(helper, villager));
			}
			if (brokenAt.get() < 0 && now > 1200) {
				helper.fail("setup: the homeless villager never claimed the free bed " + helper.relativePos(free).toShortString() + ": " + where(helper, villager));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(brokenAt.get() >= 0 && tick.get() > brokenAt.get() + 200, "waiting for the bed to be broken and 200 ticks after");
			helper.assertTrue(level.getBlockState(free).isAir(), "the bed is still there");
			helper.assertFalse(home(villager).filter(free::equals).isPresent(), "he still holds the broken bed as his home: " + where(helper, villager));
			helper.assertTrue(villager.isAlive() && !villager.isSleeping(), "alive and not asleep: " + where(helper, villager));
		});
	}

	/**
	 * The homeless villager up in the east bedroom claims a free west bed; on his way (once he is off the bedroom floor, or
	 * 200 ticks after his claim) he is saved and loaded again, as when the chunk unloads or the server restarts. The loaded
	 * villager keeps the bed and sleeps in it.
	 */
	//$ gametest_ticks_batch HUGE '2600' '"qa1105cReload"'
	@GameTest(template = HUGE, timeoutTicks = 2600, batch = "qa1105cReload")
	public void qaSavedAndLoadedOnHisWayHeStillSleepsInTheBedNextDoor(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Terrace t = terrace(helper, 1109L);
		t.beds().subList(2, 4).forEach(b -> taken(helper, b));
		List<BlockPos> free = t.beds().subList(0, 2);
		int bedY = t.beds().get(0).getY();
		AtomicReference<Villager> current = new AtomicReference<>(spawnAt(helper, inBedroomOf(helper, t, t.beds().get(2))));
		AtomicReference<BlockPos> claimed = new AtomicReference<>();
		AtomicInteger claimedAt = new AtomicInteger(-1);
		AtomicInteger tick = new AtomicInteger();
		AtomicReference<String> reloaded = new AtomicReference<>();
		helper.setDayTime(13000);
		helper.onEachTick(() -> {
			int now = tick.incrementAndGet();
			Villager v = current.get();
			if (reloaded.get() != null || v.isSleeping()) {
				return;
			}
			Optional<BlockPos> h = home(v).filter(free::contains);
			if (h.isPresent() && claimedAt.get() < 0) {
				claimedAt.set(now);
				claimed.set(h.get());
			}
			if (claimedAt.get() >= 0 && (v.getY() < bedY - 0.5 || now > claimedAt.get() + 200)) {
				CompoundTag tag = v.saveWithoutId(new CompoundTag());
				String was = where(helper, v);
				v.discard();
				Villager loaded = EntityType.VILLAGER.create(level);
				loaded.load(tag);
				loaded.setUUID(UUID.randomUUID());
				level.addFreshEntity(loaded);
				current.set(loaded);
				reloaded.set(was);
			}
		});
		helper.succeedWhen(() -> {
			Villager v = current.get();
			helper.assertTrue(reloaded.get() != null, "waiting for the claim and the reload: " + where(helper, v));
			helper.assertTrue(home(v).filter(claimed.get()::equals).isPresent(),
				"after the reload (was " + reloaded.get() + ") he lost the bed " + helper.relativePos(claimed.get()).toShortString() + ": " + where(helper, v));
			helper.assertTrue(v.isSleeping() && v.getSleepingPos().filter(claimed.get()::equals).isPresent(),
				"after the reload (was " + reloaded.get() + ") not asleep in his bed: " + where(helper, v));
		});
	}

	/**
	 * A villager whose bed is the east home's first stands in the east bedroom at bedtime, with the west home's beds free:
	 * he isn't moved next door; he keeps his own bed all night and sleeps in it.
	 */
	//$ gametest_ticks_batch HUGE '1200' '"qa1105cOwnBedUpstairs"'
	@GameTest(template = HUGE, timeoutTicks = 1200, batch = "qa1105cOwnBedUpstairs")
	public void qaAVillagerWithHisOwnBedUpstairsIsNotMovedNextDoor(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Terrace t = terrace(helper, 1110L);
		BlockPos own = t.beds().get(2);
		taken(helper, own);
		taken(helper, t.beds().get(3));
		Villager villager = spawnAt(helper, inBedroomOf(helper, t, t.beds().get(3)));
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), own));
		helper.setDayTime(13000);
		helper.onEachTick(() -> {
			helper.assertTrue(home(villager).filter(own::equals).isPresent(), "his home changed: " + where(helper, villager));
			helper.assertFalse(villager.getSleepingPos().filter(p -> !p.equals(own)).isPresent(), "asleep in a bed not his: " + where(helper, villager));
		});
		helper.succeedWhen(() -> helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(own::equals).isPresent(),
			"not asleep in his own bed " + helper.relativePos(own).toShortString() + ": " + where(helper, villager)));
	}

	/**
	 * B304 still works, the other way round: a villager with no bed stands downstairs in the WEST home's kitchen by its
	 * ladder at bedtime, the west beds someone else's: he claims a free bed of the east home, climbs to it and sleeps.
	 */
	//$ gametest_ticks_batch HUGE '1800' '"qa1105cDownstairsWest"'
	@GameTest(template = HUGE, timeoutTicks = 1800, batch = "qa1105cDownstairsWest")
	public void qaAHomelessVillagerDownstairsInTheWestHomeTakesAFreeEastBed(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		Terrace t = terrace(helper, 1111L);
		t.beds().subList(0, 2).forEach(b -> taken(helper, b));
		List<BlockPos> free = t.beds().subList(2, 4);
		// blueprint (4, 1, 5): the west kitchen's carpet by its ladder (5, 1-5, 7), the mirror of TerraceSleepGameTests' east
		// kitchen spot (8, 1, 5) across the party wall (x 6)
		BlockPos kitchen = t.origin().offset(4, 1, 5);
		helper.assertTrue(helper.getLevel().getBlockState(t.origin().offset(5, 1, 7)).is(net.minecraft.world.level.block.Blocks.LADDER)
				&& helper.getLevel().getBlockState(kitchen).is(net.minecraft.tags.BlockTags.WOOL_CARPETS) && helper.getLevel().getBlockState(kitchen.above()).isAir(),
			"setup: the west kitchen isn't as the blueprint has it at " + helper.relativePos(kitchen).toShortString());
		Villager villager = spawnAt(helper, kitchen);
		helper.setDayTime(13000);
		helper.succeedWhen(() -> {
			helper.assertTrue(home(villager).filter(free::contains).isPresent(),
				beds(helper, t) + "; the homeless villager in the west kitchen hasn't claimed a free east bed: " + where(helper, villager));
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(free::contains).isPresent(),
				"not asleep in the east home: " + where(helper, villager));
		});
	}
}

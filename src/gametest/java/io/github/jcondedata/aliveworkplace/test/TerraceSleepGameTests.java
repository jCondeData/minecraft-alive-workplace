package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * B304 (the owner, 2026-10-22: in the Terrace a villager stood on a chest at night in a bedroom whose beds weren't his,
 * while free beds stood in the home next door). The Terrace is two homes under one roof, each with its bedroom up a
 * ladder. A villager going to bed took the ladder nearest him, which could be the other home's: up there, his bed behind
 * the party wall, vanilla's walking found no way to it, and after a minute it gave the bed up. And a villager without
 * a bed never got one of the Terrace's: vanilla only claims a bed it can walk to, and it can't walk up a ladder. Now he
 * climbs the ladder that reaches his bed's room, and a free bed up a ladder can be claimed.
 */
public class TerraceSleepGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HOUSE = new BlockPos(8, 2, 8);

	/** The Terrace placed at {@link #HOUSE}: its origin and its four beds' heads, west to east. */
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
		helper.assertTrue(beds.size() == 4 && beds.stream().allMatch(b -> b.getY() == origin.getY() + 6),
			"setup: the Terrace's beds are " + beds.stream().map(helper::relativePos).toList());
		return new Terrace(origin, box, beds);
	}

	/** A villager standing in the east home's kitchen (blueprint 8, 1, 5), by its ladder. */
	private static Villager inEastKitchen(GameTestHelper helper, Terrace t) {
		BlockPos at = t.origin().offset(8, 1, 5);
		Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(at));
		villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		return villager;
	}

	private static String where(GameTestHelper helper, Villager v) {
		return "at " + helper.relativePos(v.blockPosition()).toShortString() + ", home "
			+ v.getBrain().getMemory(MemoryModuleType.HOME).map(g -> helper.relativePos(g.pos()).toShortString()) + ", walking to "
			+ v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString())
			+ ", activity " + v.getBrain().getActiveNonCoreActivity();
	}

	/**
	 * The west home's villager comes home through the east home (he stands in its kitchen, at the foot of its ladder) at
	 * bedtime: he goes round to his own home's ladder, climbs it and sleeps in his bed, which he keeps.
	 */
	//$ gametest_ticks_batch AREA '900' '"terraceSleepOwn"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "terraceSleepOwn")
	public void aVillagerOfTheWestHomeClimbsItsLadderNotTheEastOnesAndSleeps(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Terrace t = terrace(helper, 304L);
		BlockPos bed = t.beds().get(1);
		Villager villager = inEastKitchen(helper, t);
		level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		helper.setDayTime(13000);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> g.pos().equals(bed)).isPresent(),
				"the villager gave his bed up: " + where(helper, villager));
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(bed::equals).isPresent(),
				"not asleep in his bed " + helper.relativePos(bed).toShortString() + ": " + where(helper, villager));
		});
	}

	/**
	 * A villager with no bed stands in the east home's kitchen at bedtime; the east home's beds are someone else's, the
	 * west home's are free. He claims one of the west home's beds, climbs to it and sleeps there.
	 */
	//$ gametest_ticks_batch AREA '1200' '"terraceSleepFree"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "terraceSleepFree")
	public void aVillagerWithoutABedTakesAFreeOneNextDoorAndSleeps(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Terrace t = terrace(helper, 3041L);
		for (BlockPos taken : t.beds().subList(2, 4)) {
			level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(taken), taken, 1); // someone else's
		}
		List<BlockPos> free = t.beds().subList(0, 2);
		Villager villager = inEastKitchen(helper, t);
		helper.setDayTime(13000);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> free.contains(g.pos())).isPresent(),
				"the villager hasn't claimed a free bed of the west home: " + where(helper, villager));
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(free::contains).isPresent(),
				"not asleep in the west home: " + where(helper, villager));
		});
	}
}

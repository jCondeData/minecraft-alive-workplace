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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * B258: a jobless villager who picked a workstation just before dusk walked on to it at bedtime instead of to bed; from
 * the Stone House's attic (the barrel downstairs, no way down that vanilla can walk) it stood by its bed all night, and a
 * minute of "can't reach" made vanilla give the bed up. Now the walk to the workstation ends when the villager rests.
 */
public class AtticBedGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HOUSE = new BlockPos(8, 2, 8);

	/** In the attic at dusk with the barrel downstairs picked as a workstation: asleep in the attic bed within 10 seconds. */
	//$ gametest_ticks_batch AREA '200' '"atticBed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "atticBed")
	public void aVillagerWalkingToAWorkstationAtDuskGoesToBedInstead(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("stone_house")).orElseThrow();
		BlockPos origin = helper.absolutePos(HOUSE);
		house.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(258L), 2);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(house.getSize()).offset(-1, -1, -1));
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 16, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted().toList();
		helper.assertTrue(beds.size() == 2 && beds.get(1).getY() > beds.get(0).getY(), "setup: the Stone House's beds are " + beds);
		BlockPos atticBed = beds.get(1);
		BlockPos barrel = origin.offset(2, 1, 2);
		helper.assertTrue(level.getBlockState(barrel).is(Blocks.BARREL), "setup: no barrel downstairs at " + barrel);

		Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(origin.offset(2, 6, 3)));
		villager.moveTo(origin.getX() + 2.5, origin.getY() + 6, origin.getZ() + 3.5, 0, 0);
		level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(atticBed), atticBed, 1);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), atticBed));
		// the barrel picked as a workstation, as a jobless villager picks one by day; it stands where the walk to it ends
		// from up here (the attic floor nearest the barrel, two blocks from the bed), as in the run that showed B258
		level.getPoiManager().take(h -> h.is(PoiTypes.FISHERMAN), (h, p) -> p.equals(barrel), barrel, 1);
		villager.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE, GlobalPos.of(level.dimension(), barrel));
		helper.setDayTime(13000);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> g.pos().equals(atticBed)).isPresent(),
				"the villager gave the attic bed up");
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(atticBed::equals).isPresent(),
				"not asleep in the attic bed: at " + villager.blockPosition().toShortString() + ", walking to "
					+ villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition().toShortString())
					+ " (the barrel " + barrel.toShortString() + "), workstation picked " + villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE)
					+ ", activity " + villager.getBrain().getActiveNonCoreActivity());
		});
	}
}

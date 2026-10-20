package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.ArrayList;
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
 * B255: two villagers of one house come home through its one door at the same moment. The one behind used to press the
 * one in front against the open door's leaf, both stuck on the doorstep, and after a minute vanilla gave one of them's
 * bed up. Now the one further from the door gives way, and both are through it soon after.
 */
public class DoorJamGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	/** The Stone House's origin: its door (blueprint 5,1,1) faces the area's z=0 side, its doorstep is (13, 2, 8). */
	private static final BlockPos HOUSE = new BlockPos(8, 2, 8);

	/** Both villagers stand pressed together on the doorstep at bedtime; both are through it within 20 seconds and keep their beds. */
	//$ gametest_ticks_batch AREA '400' '"doorJam"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "doorJam")
	public void twoVillagersThroughOneDoorBothGetToBed(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("stone_house")).orElseThrow();
		BlockPos origin = helper.absolutePos(HOUSE);
		house.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(255L), 2);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(house.getSize()).offset(-1, -1, -1));
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 16, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted().toList();
		helper.assertTrue(beds.size() == 2 && beds.get(0).getY() < beds.get(1).getY(), "setup: the Stone House has " + beds.size() + " beds");
		// the jam as it came about on the Stone House's doorstep (blueprint 5,0,0, the villagers standing on it): the one for
		// the attic a little back and to the side, the one for the ground-floor bed in front of it, against the door
		BlockPos step = origin.offset(5, 1, 0);
		double[][] spots = {{0.5125, 0.93}, {0.7, 0.3}};
		List<Villager> sleepers = new ArrayList<>();
		for (int i = 0; i < beds.size(); i++) {
			BlockPos bed = beds.get(i);
			Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(step));
			villager.moveTo(step.getX() + spots[i][0], step.getY(), step.getZ() + spots[i][1], 0, 0);
			level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed), bed, 1);
			villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
			sleepers.add(villager);
		}
		helper.setDayTime(13000);
		// past the door (blueprint z 1) and on the house's floor: through, not stuck on the doorstep. Whether the attic
		// sleeper then gets into bed is BedLadders' (and the B258 note); this test is the door.
		int inside = origin.getZ() + 2;
		helper.succeedWhen(() -> {
			for (Villager villager : sleepers) {
				helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).map(g -> box.isInside(g.pos())).orElse(false),
					"a villager gave the bed up: at " + helper.relativePos(villager.blockPosition()));
				helper.assertTrue(box.isInside(villager.blockPosition()) && villager.getBlockZ() >= inside,
					"not through the door yet: at " + helper.relativePos(villager.blockPosition()) + ", walking to "
						+ villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()))
						+ ", can't reach since " + villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE) + " (now " + level.getGameTime()
						+ "), the others at " + sleepers.stream().filter(o -> o != villager).map(o -> helper.relativePos(o.blockPosition())).toList());
			}
		});
	}
}

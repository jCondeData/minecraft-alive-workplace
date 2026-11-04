package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.List;
import net.minecraft.core.BlockPos;
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
 * QA (lane C, 2026-11-04) bug test for B304's leftover: a villager without a bed who is already upstairs in the wrong
 * bedroom of the Terrace at bedtime (the owner's Aldous) never takes the free beds next door that night: the bed claim
 * (B304's fix) only finds a bed up a ladder from the floor the villager stands on. Fails on main until that's fixed.
 */
public class QaTerraceUpstairs1104c2BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * B304 (the owner's Aldous: "a villager ... stands at night in a narrow room/hall with no bed ... while free beds stand
	 * in the house next door. Expected: ... one without a claimed bed takes a free one nearby, and never idles ... in a
	 * bedless room"): a villager with no bed is already upstairs in the Terrace's east bedroom at bedtime, both its beds
	 * someone else's; the west home's two beds are free. He claims one of them and sleeps in it before the night is out.
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qa1104c2TerraceUpstairs"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qa1104c2TerraceUpstairs")
	public void qaAHomelessVillagerAlreadyUpInTheWrongBedroomTakesAFreeBedNextDoor(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("terrace")).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
		house.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(3042L), 2);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(house.getSize()).offset(-1, -1, -1));
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 16, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(java.util.Comparator.comparingInt(BlockPos::getX)).toList();
		helper.assertTrue(beds.size() == 4, "setup: the Terrace has " + beds.size() + " beds");
		for (BlockPos taken : beds.subList(2, 4)) {
			level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(taken), taken, 1); // someone else's
		}
		List<BlockPos> free = beds.subList(0, 2);
		// a floor block in the east bedroom: on the beds' level, nearest the east home's first bed, not a bed or a ladder
		BlockPos stand = null;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), beds.get(2).getY(), box.minZ(), box.maxX(), beds.get(2).getY(), box.maxZ())) {
			boolean open = level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
				&& level.getBlockState(p.below()).isFaceSturdy(level, p.below(), net.minecraft.core.Direction.UP);
			if (open && p.distManhattan(beds.get(2)) <= 3 && (stand == null || p.distManhattan(beds.get(2)) < stand.distManhattan(beds.get(2)))) {
				stand = p.immutable();
			}
		}
		helper.assertTrue(stand != null, "setup: no floor in the east bedroom");
		Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(stand));
		villager.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
		helper.setDayTime(13000);
		BlockPos from = stand;
		String setup = "started at " + helper.relativePos(from).toShortString() + ", beds " + beds.stream().map(b -> helper.relativePos(b).toShortString()).toList();
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> free.contains(g.pos())).isPresent(), setup + "; " +
				"the homeless villager up in the east bedroom hasn't claimed a free bed of the west home: at "
					+ helper.relativePos(villager.blockPosition()).toShortString() + ", home " + villager.getBrain().getMemory(MemoryModuleType.HOME));
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(free::contains).isPresent(),
				setup + "; not asleep in the west home: at " + helper.relativePos(villager.blockPosition()).toShortString());
		});
	}
}

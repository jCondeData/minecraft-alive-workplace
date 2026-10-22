package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * B304 with a furniture mod's beds: the Terrace furnished with Handcrafted's fancy beds (as the live pack furnishes it). A
 * villager without a bed in the east home's kitchen, the east home's beds someone else's, claims a free fancy bed of the
 * west home, goes up that home's ladder and sleeps in it.
 */
public class TerraceSleepCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HOUSE = new BlockPos(8, 2, 8);

	//$ gametest_ticks_batch HUGE_AREA '1200' '"terrace_fancy_beds"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "terrace_fancy_beds")
	public void aVillagerWithoutABedTakesAFreeFancyBedNextDoorAndSleeps(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		long day = level.getDayTime();
		long game = level.getGameTime();
		Block fancy = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("handcrafted:dark_oak_fancy_bed"));
		helper.assertTrue(fancy instanceof BedBlock && fancy.defaultBlockState().is(BlockTags.BEDS), "Handcrafted's fancy bed isn't a bed");
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("terrace")).orElseThrow();
		BlockPos origin = helper.absolutePos(HOUSE);
		house.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(304L), 2);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(house.getSize()).offset(-1, -1, -1));
		// every vanilla bed of the Terrace becomes a fancy bed, each half in place (no shape updates, so neither half breaks)
		List<BlockPos> halves = new ArrayList<>();
		BlockPos.betweenClosedStream(box).filter(p -> level.getBlockState(p).getBlock() instanceof BedBlock).forEach(p -> halves.add(p.immutable()));
		helper.assertTrue(halves.size() == 8, "setup: the Terrace has " + halves.size() + " bed halves, not 8");
		for (BlockPos p : halves) {
			BlockState was = level.getBlockState(p);
			level.setBlock(p, fancy.defaultBlockState().setValue(BedBlock.FACING, was.getValue(BedBlock.FACING)).setValue(BedBlock.PART,
				was.getValue(BedBlock.PART)), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 16, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(Comparator.comparingInt(BlockPos::getX)).toList();
		helper.assertTrue(beds.size() == 4 && beds.stream().allMatch(b -> level.getBlockState(b).is(fancy)),
			"setup: the Terrace's homes are " + beds.stream().map(b -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(b).getBlock())).toList());
		for (BlockPos taken : beds.subList(2, 4)) {
			level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(taken), taken, 1); // someone else's
		}
		List<BlockPos> free = beds.subList(0, 2);
		BlockPos kitchen = origin.offset(8, 1, 5);
		Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(kitchen));
		villager.moveTo(kitchen.getX() + 0.5, kitchen.getY(), kitchen.getZ() + 0.5, 0, 0);
		long now = level.getDayTime() % 24000;
		level.setDayTime((now <= 13000 ? level.getDayTime() / 24000 : level.getDayTime() / 24000 + 1) * 24000 + 13000);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> free.contains(g.pos())).isPresent(),
				"the villager hasn't claimed a free fancy bed of the west home: at " + helper.relativePos(villager.blockPosition()).toShortString()
					+ ", home " + villager.getBrain().getMemory(MemoryModuleType.HOME).map(g -> helper.relativePos(g.pos()).toShortString()));
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(free::contains).isPresent(),
				"not asleep in a fancy bed of the west home: at " + helper.relativePos(villager.blockPosition()).toShortString()
					+ ", activity " + villager.getBrain().getActiveNonCoreActivity());
			villager.discard();
			level.setDayTime(day + level.getGameTime() - game);
		});
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane D, 2026-11-09, B209's sweep of old worlds (CampCleanup: "the hall's round looks for bandit camps SWEEP_FROM to
 * SWEEP_TO blocks out"): a camp whose fire is exactly SWEEP_FROM or exactly SWEEP_TO blocks from the hall is found, and
 * one a block inside SWEEP_FROM or outside SWEEP_TO isn't. The camp's fire is 12 east and 16 south of the hall: 20 blocks.
 * A local mutation run of CampCleanup (6 camp test classes) let "from" become "past" (far &lt;= FROM²) survive.
 */
public class QaCampSweep1109dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** The camp's fire: 12 and 16 blocks from the hall, 20 in all. */
	private static final BlockPos FIRE = new BlockPos(13, 2, 17);
	/** The bandit camp blueprint's spot for its fire (as B209GameTests). */
	private static final BlockPos FIRE_SPOT = new BlockPos(7, 1, 6);

	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		BanditCamps.forget(level);
		Leftovers.after(helper, () -> {
			CampCleanup.RUINS = false;
			CampCleanup.SWEEP_FROM = Lairs.NEAR - 16;
			CampCleanup.SWEEP_TO = Lairs.FAR + 16;
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
		});
		return hall;
	}

	/** A camp left from before B209 (unturned, no record), its village to be swept; the area's roof over it opened. */
	private static void oldCamp(GameTestHelper helper, BlockPos hall, BlockPos middle) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(Lairs.BANDIT_CAMP).orElseThrow();
		BlockPos origin = middle.subtract(FIRE_SPOT);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(Rotation.NONE), RandomSource.create(41), 2);
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, 1, -9), middle.offset(9, 40, 9))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		unswept(level, hall);
	}

	private static void unswept(ServerLevel level, BlockPos hall) {
		Lairs.Data data = Lairs.Data.get(level);
		CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
		tag.putBoolean("b209", true);
		ListTag list = new ListTag();
		CompoundTag at = new CompoundTag();
		at.putLong("hall", hall.asLong());
		list.add(at);
		tag.put("unswept", list);
		data.read(tag);
	}

	/** Sweeps with the given ring; whether the camp was found. */
	private static boolean found(GameTestHelper helper, BlockPos hall, int from, int to) {
		ServerLevel level = helper.getLevel();
		CampCleanup.SWEEP_FROM = from;
		CampCleanup.SWEEP_TO = to;
		unswept(level, hall);
		CampCleanup.sweep(level, hall);
		return !CampCleanup.clearing(level).isEmpty();
	}

	//$ gametest_ticks_batch AREA '300' '"qaCampSweep1109dEdges"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCampSweep1109dEdges")
	public void aCampExactlyOnTheSweepsInnerOrOuterEdgeIsFoundAndOneBlockBeyondIsNot(GameTestHelper helper) {
		BlockPos hall = village(helper);
		BlockPos middle = helper.absolutePos(FIRE);
		helper.runAfterDelay(2, () -> {
			oldCamp(helper, hall, middle);
			helper.assertTrue(middle.getX() - hall.getX() == 12 && middle.getZ() - hall.getZ() == 16, "setup: the fire isn't 12 and 16 from the hall");
			helper.assertTrue(!found(helper, hall, 21, 30), "a camp 20 blocks from the hall was swept with the sweep starting at 21");
			helper.assertTrue(!found(helper, hall, 0, 19), "a camp 20 blocks from the hall was swept with the sweep ending at 19");
			helper.assertTrue(found(helper, hall, 0, 20), "a camp exactly at the sweep's outer edge (20) wasn't found");
			helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
				helper.assertTrue(CampCleanup.clearing(helper.getLevel()).isEmpty(), "the first clearing never ended");
				oldCamp(helper, hall, middle);
				helper.assertTrue(found(helper, hall, 20, 30), "a camp exactly at the sweep's inner edge (20) wasn't found");
				helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
					helper.assertTrue(CampCleanup.clearing(helper.getLevel()).isEmpty(), "the second clearing never ended");
					helper.succeed();
				});
			});
		});
	}
}

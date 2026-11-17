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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane D, 2026-11-17, the first mutation pass on threat/CampCleanup (lines 126-414, 8 mutants, against the 11 camp
 * test classes): B209's sweep of old worlds takes down a bandit camp left standing "with no living foe of ours near"
 * (ROADMAP B209: "no living tagged foe near"). A cow grazing by the camp, or a wild pillager that is no band's, is no
 * foe of ours: the camp is still found and comes down. The mutant {@code m.isAlive() || m instanceof Enemy && ...}
 * (CampCleanup:413, any living mob near read as the camp's guard, so an old camp with a sheep near it stood for good)
 * survived every camp class.
 */
public class QaCampCleanupMutants1117dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** The camp's fire: 12 and 16 blocks from the hall, 20 in all (as QaCampSweep1109dGameTests). */
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
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains("qa_camp_1117d")).forEach(Mob::discard);
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

	private static Mob neighbour(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos at) {
		Mob mob = helper.spawnWithNoFreeWill(type, at);
		mob.addTag("qa_camp_1117d");
		mob.setPersistenceRequired();
		return mob;
	}

	/**
	 * An old bandit camp with a cow by it and a wild pillager (no band's tag) near: neither is a foe of ours, so the
	 * hall's sweep finds the camp at once and it comes down, fire and all; both of them are left alone.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaCampSweepNeighbours1117d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCampSweepNeighbours1117d")
	public void anOldCampWithACowAndAWildPillagerNearStillComesDown(GameTestHelper helper) {
		BlockPos hall = village(helper);
		BlockPos middle = helper.absolutePos(FIRE);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			oldCamp(helper, hall, middle);
			helper.assertTrue(level.getBlockState(middle).getBlock() instanceof CampfireBlock, "setup: no campfire at the camp's middle");
			Mob cow = neighbour(helper, EntityType.COW, new BlockPos(3, 2, 3));
			Mob pillager = neighbour(helper, EntityType.PILLAGER, new BlockPos(4, 2, 26));
			helper.assertTrue(cow.distanceToSqr(middle.getCenter()) < 32 * 32 && pillager.distanceToSqr(middle.getCenter()) < 32 * 32,
				"setup: the cow or the pillager isn't within the sweep's 32 blocks of the fire");
			CampCleanup.SWEEP_FROM = 0;
			CampCleanup.SWEEP_TO = 30;
			CampCleanup.sweep(level, hall);
			helper.assertTrue(CampCleanup.clearing(level).size() == 1,
				"an old camp with only a cow and a wild pillager (no band's) near wasn't found by the sweep: clearing " + CampCleanup.clearing(level));
			helper.runAfterDelay(CampCleanup.CLEAR_TICKS + 10, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the old camp's clearing never ended");
				helper.assertTrue(!(level.getBlockState(middle).getBlock() instanceof CampfireBlock), "the old camp's fire still stands: " + level.getBlockState(middle));
				helper.assertTrue(cow.isAlive() && pillager.isAlive(), "the camp's clearing hurt the cow or the wild pillager");
				helper.succeed();
			});
		});
	}
}

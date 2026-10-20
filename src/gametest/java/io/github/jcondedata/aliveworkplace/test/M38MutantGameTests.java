package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;

/**
 * Tests for M38 (2.2) code that last night's mutation run (GitHub run 39394481465) showed no test covered: a bandit
 * camp's clearing saved with an odd turn (B209, B225) and who counts as off work for a gathering (the festivals and the
 * Mega Shrine's crowd).
 */
public class M38MutantGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	/** Ticks till a broken camp is all gone, with a little to spare. */
	private static final int GONE = CampCleanup.DELAY + CampCleanup.CLEAR_TICKS + 10;

	/**
	 * A camp coming down whose save holds a turn out of range (4, as a corrupted or hand-edited file might): it is read as
	 * a turn not yet known, so the clearing finds its turn again and the camp still comes all the way down, no crash.
	 */
	//$ gametest_ticks_batch AREA '400' '"m38OddTurn"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "m38OddTurn")
	public void m38AClearingSavedWithAnOddTurnStillComesDown(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		BanditCamps.forget(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
		});
		for (int x = 2; x <= 28; x++) {
			for (int z = 2; z <= 28; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.GRASS_BLOCK);
			}
		}
		BlockPos ground = helper.absolutePos(new BlockPos(15, 2, 15));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, ground) != null, "no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			helper.assertTrue(helper.getBounds().inflate(2).contains(lair.pos().getCenter()), "the camp isn't in the area: " + lair.pos());
			helper.assertTrue(campfires(helper) > 0, "the camp has no fire to look for");
			if (level.getEntity(lair.captain()) instanceof LivingEntity chief) {
				chief.kill();
			}
			helper.assertTrue(CampCleanup.clearing(level).size() == 1, "the camp isn't coming down: " + CampCleanup.clearing(level));
			Lairs.Data data = Lairs.Data.get(level);
			CompoundTag tag = data.save(new CompoundTag(), level.registryAccess());
			ListTag going = tag.getList("clearing", Tag.TAG_COMPOUND);
			helper.assertTrue(going.size() == 1, "the clearing isn't saved: " + going);
			going.getCompound(0).putInt("rotation", 4);
			data.read(tag);
			helper.assertTrue(CampCleanup.clearing(level).size() == 1, "the clearing was lost on reading: " + CampCleanup.clearing(level));
			int read = CampCleanup.clearing(level).get(0).rotation();
			helper.assertTrue(read == -1, "a saved turn of 4 is read as " + read + ", not as unknown (-1)");
			helper.runAfterDelay(GONE, () -> {
				helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended: " + CampCleanup.clearing(level));
				int left = campfires(helper);
				helper.assertTrue(left == 0, left + " of the camp's fires still stand");
				helper.succeed();
			});
		});
	}

	/** The camp's fires (and any fire) in the area. */
	private static int campfires(GameTestHelper helper) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(1, 1, 1), new BlockPos(29, 16, 29))) {
			if (helper.getBlockState(p).is(BlockTags.CAMPFIRES)) {
				n++;
			}
		}
		return n;
	}

	/** An adult villager made to do {@code activity} now (its brain set, as its schedule would). */
	private static Villager doing(GameTestHelper helper, BlockPos at, Activity activity) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		if (activity == Activity.WORK) {
			v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(at.east())));
		}
		v.getBrain().setActiveActivityIfPossible(activity);
		helper.assertTrue(v.getBrain().isActive(activity), "the villager isn't at " + activity + ": " + v.getBrain().getActiveNonCoreActivity());
		return v;
	}

	/**
	 * Only a villager idle, meeting or playing is off work: one at WORK or REST isn't (and so isn't pulled to a festival's
	 * square or a Mega Shrine's crowd); one at PLAY or IDLE is.
	 */
	//$ gametest SMALL
	@GameTest(template = SMALL)
	public void m38OnlyIdleMeetingOrPlayingIsOffWork(GameTestHelper helper) {
		Villager working = doing(helper, new BlockPos(1, 2, 1), Activity.WORK);
		Villager resting = doing(helper, new BlockPos(3, 2, 1), Activity.REST);
		Villager idle = doing(helper, new BlockPos(1, 2, 3), Activity.IDLE);
		Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		child.setBaby(true);
		child.refreshBrain(helper.getLevel());
		child.getBrain().setActiveActivityIfPossible(Activity.PLAY);
		helper.assertTrue(child.getBrain().isActive(Activity.PLAY), "the child isn't playing: " + child.getBrain().getActiveNonCoreActivity());
		helper.assertTrue(!Festivals.offWork(working), "a villager at WORK counts as off work");
		helper.assertTrue(!Festivals.offWork(resting), "a villager at REST counts as off work");
		helper.assertTrue(Festivals.offWork(idle), "an idle villager doesn't count as off work");
		helper.assertTrue(Festivals.offWork(child), "a playing child doesn't count as off work");
		helper.succeed();
	}

	/** A gathering's call (a festival's square, a Grand Chef's banquet) doesn't pull a villager away from work or from bed. */
	//$ gametest SMALL
	@GameTest(template = SMALL)
	public void m38AGatheringDoesntPullAVillagerFromWork(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos square = helper.absolutePos(new BlockPos(1, 2, 1)).offset(20, 0, 20); // (well past the square's reach)
		for (Activity activity : new Activity[] {Activity.WORK, Activity.REST}) {
			Villager v = doing(helper, new BlockPos(activity == Activity.WORK ? 1 : 3, 2, 1), activity);
			v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			helper.assertTrue(!Festivals.gatherAt(level, v, square), "a villager at " + activity + " far off is at the gathering");
			helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(),
				"a villager at " + activity + " was sent to the gathering: " + v.getBrain().getMemory(MemoryModuleType.WALK_TARGET));
		}
		Villager idle = doing(helper, new BlockPos(1, 2, 3), Activity.IDLE);
		idle.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		Festivals.gatherAt(level, idle, square);
		helper.assertTrue(idle.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent(), "an idle villager wasn't sent to the gathering");
		helper.succeed();
	}
}

package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-05) for B215, from its fix's spec: the trainer waits at the pedestal until every villager off work has been
 * at the crowd's spot, "at most MegaShrines.WAIT_TICKS = 600 ticks from the gathering's first tick, and never past the
 * show's last 100 ticks", and the show credits everyone who came plus everyone within 12 blocks. MegaShrineCompatTests
 * proves the wait and the last call at 50 ticks left; these prove both caps from both sides, in real ticks for the wait:
 * still waiting just before 600 ticks, held just after; still waiting 101 ticks before the hours end, held at exactly 100.
 * And who is credited when the cap holds the show: one off work who never came but stands within 12 blocks of the
 * pedestal is, one who never came and stands beyond 12 isn't.
 */
public class QaMegaShrineWaitCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** The shrine's origin: its pedestal at (5, 4, 8), its trainer's spot at (5, 4, 7), its crowd's spot at (5, 2, 1). */
	private static final BlockPos ORIGIN = new BlockPos(2, 1, 5);
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	/** A finished shrine with its pedestal, the festival's show hours begun 100 ticks ago; all of it undone after. */
	private static void shrine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.MEGA_SHRINE.id(), placement, UUID.randomUUID());
		level.setBlockAndUpdate(io.github.jcondedata.aliveworkplace.hall.Arenas.world(placement, MegaShrines.PEDESTAL_SPOT),
			net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(MegaShrines.PEDESTAL).defaultBlockState());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		Festivals.forget();
		MegaShrines.forget();
		MegaShrines.forgetShows(level);
		level.setDayTime((Chronicle.day(level) - 1) * 24000L + Festivals.START + 100);
		PartnerShowsCompatTests.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			MegaBracelets.ENABLED = true;
			Festivals.forget();
			MegaShrines.forget();
			MegaShrines.forgetShows(level);
			level.setDayTime(time);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4))) {
				v.discard();
			}
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, boolean trainer) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		if (trainer) {
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(3));
		}
		v.setNoAi(true);
		return v;
	}

	/** An idle villager (off work, never coming) held still at {@code at}, relative to the test area. */
	private static Villager stander(GameTestHelper helper, Vec3 at) {
		Villager v = villager(helper, new BlockPos(5, 2, 2), false);
		v.setNoGravity(true);
		v.moveTo(helper.absoluteVec(at));
		return v;
	}

	private static boolean saw(Villager v, long today) {
		return Long.valueOf(today).equals(ModAttachments.MEGA_SHOW_DAY.get(v));
	}

	/**
	 * The wait's cap, in real ticks: with one villager off work who never comes, the trainer at the pedestal still waits
	 * 590 ticks after the gathering's first tick, and holds the show by 610. Who is credited then: the one at the crowd's
	 * spot, and one who never came but stands within 12 blocks of the pedestal; not the one who never came and stands
	 * beyond 12.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaMegaShrineWaitCap"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaMegaShrineWaitCap")
	public void theTrainerWaitsAtMostWaitTicksForTheCrowd(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, new BlockPos(5, 4, 7), true); // at the pedestal already
		Villager crowd = villager(helper, new BlockPos(5, 2, 2), false); // at the crowd's spot
		Villager never = stander(helper, new Vec3(5.5, 2, -12.5)); // off work, 13.5 from the crowd's spot, 20 from the pedestal
		Villager by = stander(helper, new Vec3(9.5, 2, 12.5)); // off work, 12.3 from the crowd's spot, within 12 of the pedestal
		BlockPos pedestal = MegaShrines.pedestal(level, hall).orElseThrow();
		net.minecraft.world.phys.AABB watch = new net.minecraft.world.phys.AABB(pedestal).inflate(12);
		helper.assertTrue(!never.getBoundingBox().intersects(watch) && by.getBoundingBox().intersects(watch),
			"setup: the one who never came is within 12 of the pedestal, or the one by it isn't");
		long today = Chronicle.day(level);
		helper.assertTrue(MegaShrines.tick(level, hall), "the shrine didn't take the festival's crowd");
		helper.assertTrue(!saw(crowd, today), "the show was held before the crowd had come");
		long first = level.getGameTime();
		helper.runAfterDelay(MegaShrines.WAIT_TICKS - 10, () -> {
			MegaShrines.tick(level, hall);
			helper.assertTrue(!saw(crowd, today), "the show was held " + (level.getGameTime() - first) + " ticks into the wait, before "
				+ MegaShrines.WAIT_TICKS + ", with one still to come");
			helper.runAfterDelay(20, () -> {
				MegaShrines.tick(level, hall);
				long waited = level.getGameTime() - first;
				helper.assertTrue(saw(crowd, today), "the trainer still waits " + waited + " ticks after the gathering began (at most " + MegaShrines.WAIT_TICKS + ")");
				helper.assertTrue(saw(by, today) && MegaShrines.mood(level, by) == MegaShrines.MOOD,
					"one off work within 12 blocks of the pedestal missed the show: mood " + MegaShrines.mood(level, by));
				helper.assertTrue(!saw(never, today) && MegaShrines.mood(level, never) == 0,
					"one who never came and stands beyond 12 blocks got the show's mood: " + MegaShrines.mood(level, never));
				helper.succeed();
			});
		});
	}

	/**
	 * The last call, from both sides: one villager still on the way, the wait itself not over (the gathering began just
	 * now). At 101 ticks before the show's hours end the trainer still waits; at exactly 100 the show is held.
	 */
	//$ gametest_batch AREA '"qaMegaShrineLastCall"'
	@GameTest(template = AREA, batch = "qaMegaShrineLastCall")
	public void theLastCallHoldsTheShowAtExactly100TicksLeft(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, new BlockPos(5, 4, 7), true);
		Villager crowd = villager(helper, new BlockPos(5, 2, 2), false);
		stander(helper, new Vec3(5.5, 2, -12.5)); // still on the way, off work
		long today = Chronicle.day(level);
		long base = (today - 1) * 24000L + Festivals.START + MegaShrines.SHOW_TICKS;
		level.setDayTime(base - 100 - 1);
		helper.assertTrue(MegaShrines.tick(level, hall), "the shrine didn't take the festival's crowd 101 ticks before the show's end");
		helper.assertTrue(!saw(crowd, today), "the show was held 101 ticks before its hours end, with one still on the way and the wait not over");
		level.setDayTime(base - 100);
		MegaShrines.tick(level, hall);
		helper.assertTrue(saw(crowd, today), "the show wasn't held at its last call, 100 ticks before its hours end");
		helper.succeed();
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-10-31) sweep of 32.21's promise across the non-command paths that bring foes to a village: "threat level 0:
 * no raids, lairs, sieges or disasters at all"; "At peace: no raids, lairs or disasters there". The daily rolls
 * (raids, lairs, disasters, hexes, bounties, threat arcs' start) all ask {@code Threats.quiet}; the Bandit King's storm
 * (31.16) starts a raid every second night through {@code VillageRaids.start}, which doesn't. These tests take a
 * running Bandit King arc to its storm (as {@link QaBanditKingAllies1031GameTests} does) and then quiet the village:
 * the storm's raid night must bring nobody, and the same night with the village not quiet (the control) it does.
 */
public class QaQuietVillageSweep1031GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final long DAY = VillageNeeds.DAY;

	private static void open(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** A hall with 8 villagers and the Bandit King's arc at its storm (chapter 3) since today. */
	private static VillageHallBlockEntity storm(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> Arcs.stopAll(level, hall));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, hall);
		for (int i = 0; i < 8; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3 + i % 4, 2, 9 + i / 4));
			v.setNoAi(true);
		}
		long day = Chronicle.day(level) + 1;
		level.setDayTime((day - 1) * DAY + 1000);
		Arcs.Arc arc = Arcs.find("bandit_king").orElse(null);
		helper.assertTrue(arc != null, "the Bandit King's arc didn't load");
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && s.chapter == 0, "the arc didn't start");
		s.mobs.forEach(m -> m.dead = true);
		for (ArcState.ArcPlace p : s.places) {
			if (p.key.equals(BanditKingStory.FIRST_CAMP) || p.key.startsWith(BanditKingStory.CAMP)) {
				p.spot = helper.absolutePos(new BlockPos(26, 2, 26));
				p.placed = 1;
			}
		}
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 1 && s.started, "chapter 2 didn't begin: " + s.chapter);
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started && s.chapterDay == day, "the storm didn't begin today: " + s.chapter + ", day " + s.chapterDay);
		s.place(BanditKingStory.FORT).spot = helper.absolutePos(new BlockPos(22, 2, 22));
		return entity;
	}

	/** Dusk-past on the storm's {@code night}th night (1 = its first), the storm having begun on {@code day}. */
	private static void night(ServerLevel level, long day, int night) {
		level.setDayTime((day - 2 + night) * DAY + 13000);
	}

	//$ gametest_ticks_batch HUGE '60' '"qaQuietKingsRaidAtPeace"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaQuietKingsRaidAtPeace")
	public void qaKingsStormRaidSkipsAVillageAtPeace(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		int threat = Threats.LEVEL;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		try {
			VillageHalls.RADIUS = 8;
			Arcs.NEAR = 0;
			Threats.LEVEL = 2;
			open(helper);
			VillageHallBlockEntity entity = storm(helper);
			VillageRaids.ENABLED = true;
			VillageRaids.callOff(level, hall);
			entity.setAtPeace(true, Chronicle.day(level));
			helper.assertTrue(Threats.quiet(level, hall), "the village isn't quiet At peace");
			long day = Chronicle.day(level);
			night(level, day, 1);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(),
				"the Bandit King's storm raid came to a village set At peace (" + VillageRaids.raiders(level, hall).size() + " raiders)");
			// The control: the storm's third night, peace lifted, the King's raiders come.
			VillageRaids.callOff(level, hall);
			entity.setAtPeace(false, Chronicle.day(level));
			night(level, day, 3); // (the King raids every second night: the first, the third...)
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isPresent(), "control: no King's raid the storm's third night with peace lifted");
			VillageRaids.callOff(level, hall);
			helper.succeed();
		} finally {
			VillageRaids.callOff(level, hall);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
			VillageRaids.ENABLED = raids;
			Threats.LEVEL = threat;
		}
	}

	//$ gametest_ticks_batch HUGE '60' '"qaQuietKingsRaidLevel0"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaQuietKingsRaidLevel0")
	public void qaKingsStormRaidNeverComesAtThreatLevelZero(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		boolean raids = VillageRaids.ENABLED;
		int threat = Threats.LEVEL;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		try {
			VillageHalls.RADIUS = 8;
			Arcs.NEAR = 0;
			Threats.LEVEL = 2;
			open(helper);
			storm(helper);
			VillageRaids.ENABLED = true;
			VillageRaids.callOff(level, hall);
			Threats.LEVEL = 0; // (the owner turned threats off after the arc began)
			long day = Chronicle.day(level);
			night(level, day, 1);
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(),
				"the Bandit King's storm raid came at threat level 0 (" + VillageRaids.raiders(level, hall).size() + " raiders)");
			// The control: the storm's third night at threat level 1, the King's raiders come.
			VillageRaids.callOff(level, hall);
			Threats.LEVEL = 1;
			night(level, day, 3); // (the King raids every second night: the first, the third...)
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isPresent(), "control: no King's raid the storm's third night at threat level 1");
			VillageRaids.callOff(level, hall);
			helper.succeed();
		} finally {
			VillageRaids.callOff(level, hall);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
			VillageRaids.ENABLED = raids;
			Threats.LEVEL = threat;
		}
	}
}

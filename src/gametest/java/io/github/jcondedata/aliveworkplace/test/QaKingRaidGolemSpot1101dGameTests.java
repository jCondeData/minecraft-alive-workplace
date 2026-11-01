package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
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
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-d-1101-1310), from the specs, killing two survivors of the 2026-11-01 nightly mutation run:
 * <ol>
 * <li>31.16 Bandit King: "every second night of the storm the King's raiders come: the village's bandit raid". A village
 * already fighting a raid on a storm night gets no second raid on top of it, and that night isn't spent: once the raid
 * ends the same night, the King's raiders come (BanditKingStory:503, {@code ||} to {@code &&}).</li>
 * <li>29.15 Golem Smith: a new golem stands "beside the Smith with room for a golem". In a corridor three blocks wide,
 * the only place a golem (1.4 wide) fits is its middle; a forged golem never stands half in a wall
 * (GolemSmith:343, {@code +0.5} to {@code -0.5}).</li>
 * </ol>
 * (The third survivor in this area, CampGround:292 {@code y < top} to {@code y <= top}, is equivalent: past the loop
 * the height is capped at {@code top} either way.)
 */
public class QaKingRaidGolemSpot1101dGameTests implements FabricGameTest {
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

	/** A hall with 8 villagers and the Bandit King's arc at its storm (chapter 3) since today (as QaThreatFixes1101GameTests). */
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
		helper.assertTrue(arc != null, "setup: expected the Bandit King's arc to be loaded, found none");
		ArcState s = Arcs.start(level, hall, arc);
		helper.assertTrue(s != null && s.chapter == 0, "setup: expected the Bandit King's arc to start at chapter 0");
		s.mobs.forEach(m -> m.dead = true);
		for (ArcState.ArcPlace p : s.places) {
			if (p.key.equals(BanditKingStory.FIRST_CAMP) || p.key.startsWith(BanditKingStory.CAMP)) {
				p.spot = helper.absolutePos(new BlockPos(26, 2, 26));
				p.placed = 1;
			}
		}
		Arcs.next(level, hall);
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started && s.chapterDay == day,
			"setup: expected the storm (index 2) begun on day " + day + ", got index " + s.chapter + ", started " + s.started + ", day " + s.chapterDay);
		s.place(BanditKingStory.FORT).spot = helper.absolutePos(new BlockPos(22, 2, 22));
		return entity;
	}

	/**
	 * The storm's first night, with a plain raid already on the village: the King's raid doesn't come on top of it (the
	 * raid on is the same one, its size unchanged); called off that same night, the next story tick brings the King's.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaKingsRaidWaitsForTheRaidOn1101d"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaKingsRaidWaitsForTheRaidOn1101d")
	public void kingsRaidWaitsForTheRaidAlreadyOnThenComesTheSameNight(GameTestHelper helper) {
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
			long day = Chronicle.day(level);
			level.setDayTime((day - 1) * DAY + 13000); // the storm's first night, past dusk
			VillageRaids.Raid first = VillageRaids.start(level, hall, 4, 0);
			helper.assertTrue(first != null && VillageRaids.active(hall).isPresent(), "setup: expected a plain raid on the village, got " + first);
			int before = VillageRaids.raiders(level, hall).size();
			for (int tick = 0; tick < 3; tick++) {
				level.setDayTime(level.getDayTime() + 40);
				BanditKingStory.tick(level);
				VillageRaids.Raid now = VillageRaids.active(hall).orElse(null);
				helper.assertTrue(now != null && now.equals(first),
					"expected the raid already on to stay the only raid on a storm night (tick " + tick + "), it is now " + now + " (was " + first + ")");
				helper.assertTrue(VillageRaids.raiders(level, hall).size() == before,
					"expected no King's raiders on top of a raid already on (tick " + tick + "): " + before + " raiders before, "
						+ VillageRaids.raiders(level, hall).size() + " now");
			}
			VillageRaids.callOff(level, hall);
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "setup: expected the plain raid called off");
			level.setDayTime(level.getDayTime() + 40);
			helper.assertTrue(Chronicle.day(level) == day && level.getDayTime() % 24000 >= VillageRaids.DUSK,
				"setup: expected still the storm's first night");
			BanditKingStory.tick(level);
			helper.assertTrue(VillageRaids.active(hall).isPresent(),
				"expected the King's raid the same storm night once the raid already on had ended (the night wasn't his yet), saw none");
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

	/**
	 * A Smith in a corridor three blocks wide (walls 3 high at x 4 and x 8, open along z): a golem is 1.4 blocks wide,
	 * so it fits only down the middle (x 6). The forged golem stands there, with no block in its way.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaGolemInACorridor1101d"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaGolemInACorridor1101d")
	public void aForgedGolemInANarrowCorridorStandsDownItsMiddle(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int z = 0; z <= 14; z++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(4, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(8, y, z), Blocks.STONE_BRICKS);
			}
		}
		for (int x = 5; x <= 7; x++) {
			for (int z = 0; z <= 14; z++) {
				helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, 1, z))).isFaceSturdy(level, helper.absolutePos(new BlockPos(x, 1, z)), net.minecraft.core.Direction.UP),
					"setup: expected a solid floor at y 1");
			}
		}
		IronGolem golem = GolemSmith.build(level, helper.absolutePos(new BlockPos(6, 2, 7)), GolemSmith.Role.SENTRY);
		helper.assertTrue(golem != null, "expected a golem to fit down the corridor's middle (x 6), none was forged");
		Leftovers.after(helper, golem::discard);
		BlockPos at = golem.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
		helper.assertTrue(level.noCollision(golem, golem.getBoundingBox()),
			"the forged golem stands partly inside a wall: at " + at + ", box " + golem.getBoundingBox().move(-helper.absolutePos(BlockPos.ZERO).getX(), 0, 0));
		helper.assertTrue(at.getX() == 6 && at.getY() == 2, "expected the golem down the corridor's middle (x 6, y 2), it stands at " + at);
		helper.succeed();
	}

	/**
	 * B628 (29.15: "a new golem stands on solid ground beside the Smith, never on a roof"): a Smith under a low roof (2
	 * blocks of room 6 blocks round) forges nothing and takes nothing, and no golem stands anywhere above; a 3 by 3 hole
	 * cut in the roof beside him makes room, and the next forging puts the golem on the ground in it and takes the cost.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaSmithRoofHole1101d"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaSmithRoofHole1101d")
	public void aSmithUnderALowRoofWaitsThenForgesOnTheGroundWhenRoomIsMade(GameTestHelper helper) {
		QaGolemSmithEdgesGameTests.setUp(helper);
		io.github.jcondedata.aliveworkplace.legend.Legend legend = QaGolemSmithEdgesGameTests.staged(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 4; x <= 16; x++) {
			for (int z = 2; z <= 14; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.OAK_PLANKS);
			}
		}
		Villager smith = QaGolemSmithEdgesGameTests.smith(helper, legend);
		QaGolemSmithEdgesGameTests.villagers(helper, 4, 18);
		BlockPos table = new BlockPos(10, 2, 10);
		net.minecraft.world.Container chest = helper.getBlockEntity(table.east());
		chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_BLOCK, 4));
		chest.setItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CARVED_PUMPKIN, 1));
		chest.setItem(2, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST, 1));
		BlockPos hall = helper.absolutePos(new BlockPos(3, 2, 3));
		IronGolem none = GolemSmith.forgeOne(level, hall, smith);
		helper.assertTrue(none == null, "expected no golem with no room under the roof, one was forged at "
			+ (none == null ? "" : none.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO))));
		helper.assertTrue(level.getEntitiesOfClass(IronGolem.class, helper.getBounds().inflate(32), g -> true).isEmpty(),
			"expected no golem anywhere, roof or ground, after a forging with no room");
		helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.IRON_BLOCK) == 4 && chest.countItem(net.minecraft.world.item.Items.CARVED_PUMPKIN) == 1
			&& chest.countItem(net.minecraft.world.item.Items.CHEST) == 1,
			"expected nothing taken when no golem was forged: iron " + chest.countItem(net.minecraft.world.item.Items.IRON_BLOCK) + "/4, pumpkin "
				+ chest.countItem(net.minecraft.world.item.Items.CARVED_PUMPKIN) + "/1, chest " + chest.countItem(net.minecraft.world.item.Items.CHEST) + "/1");
		for (int x = 10; x <= 12; x++) {
			for (int z = 5; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.AIR);
			}
		}
		IronGolem golem = GolemSmith.forgeOne(level, hall, smith);
		helper.assertTrue(golem != null, "expected a golem once a 3 by 3 hole beside the Smith made room, none was forged");
		BlockPos at = golem.blockPosition().subtract(helper.absolutePos(BlockPos.ZERO));
		helper.assertTrue(at.equals(new BlockPos(11, 2, 6)), "expected the golem on the ground in the hole (11, 2, 6), it stands at " + at);
		helper.assertTrue(level.noCollision(golem, golem.getBoundingBox()), "the golem stands partly inside the roof at " + at);
		helper.assertTrue(chest.countItem(net.minecraft.world.item.Items.IRON_BLOCK) == 0 && chest.countItem(net.minecraft.world.item.Items.CARVED_PUMPKIN) == 0,
			"expected the golem's cost taken once it stood: iron left " + chest.countItem(net.minecraft.world.item.Items.IRON_BLOCK)
				+ ", pumpkins left " + chest.countItem(net.minecraft.world.item.Items.CARVED_PUMPKIN));
		helper.succeed();
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Words;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane C, 2026-11-04): which raid a raider belongs to, at every edge of the rule (B849, B856; wiki raids.md, "Two
 * raided villages side by side keep their raids apart: a hall's round counts only its own raid's raiders, not a raider
 * standing within its reach but nearer another village with a raid under way"). Nightly mutation run left
 * VillageRaids.own's height reach {@code <= 48} widened to {@code <= 49} alive.
 * <p>
 * Village A has a hall (rel 29,2,0) and a raid under way; the raider stands within A's reach, 2411 squared from A's hall
 * (29 across, 27 up, 29 along). Village B has a raid under way (a raid record: its hall lies outside the test area) and
 * its hall is nearer the raider. With the hall radius 12, a village's reach is 44 blocks each way across and 48 up and
 * down. What a player sees is A's own round (the hall's raid check) and call-off: when the raider is B's, A's round
 * finds none of its own raiders left and the village has fought its raid off (the chat line, the chronicle, the past
 * attack); when it is A's, A's raid goes on, and the raider leaves with A's raiders at dawn or goes when A's raid is
 * called off. The below-B height edge (49 below) and the call-off side of it are QaRaidVertical1103dGameTests'.
 */
public class QaRaidOwnership1104cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BANDITS = AliveWorkplace.id("bandits");
	static final BlockPos REL_HALL = new BlockPos(29, 2, 0);
	static final BlockPos REL_RAIDER = new BlockPos(0, 29, 29);
	static final long NIGHT = 18000;
	static final long MORNING = 1000;

	/** Clears leftovers, sets the radius 12 and the time of day; puts them back, and the raid records, afterwards. */
	static ServerLevel setUp(GameTestHelper helper, long dayTime, BlockPos relHall, List<BlockPos> halls) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box.inflate(64), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			for (BlockPos hall : halls) {
				ThreatData.get(level).end(hall);
				ThreatData.get(level).forgetPast(hall);
				VillageRaids.forget(hall);
				// a hall outside this area is never cleared and became a later batch's village (B918)
				level.removeBlock(hall, false);
			}
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		VillageHalls.RADIUS = 12;
		level.setDayTime((level.getDayTime() / 24000) * 24000 + dayTime);
		helper.setBlock(relHall, ModBlocks.VILLAGE_HALL);
		helper.assertTrue(Threats.get(BANDITS).isPresent(), "setup: expected bandits.json loaded");
		return level;
	}

	static void raid(GameTestHelper helper, BlockPos hall, List<BlockPos> halls) {
		ServerLevel level = helper.getLevel();
		halls.add(hall);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).begin(new ThreatData.Under(hall, BANDITS, 3, level.getGameTime() - 2000));
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: expected a raid under way at " + hall.toShortString());
	}

	static Husk raider(GameTestHelper helper, BlockPos rel) {
		Husk husk = helper.spawn(EntityType.HUSK, rel);
		husk.setNoAi(true);
		husk.addTag(VillageRaids.TAG);
		return husk;
	}

	/** A logged-in player whose chat lines are kept in {@code heard}, standing at {@code rel}. */
	static ServerPlayer listener(GameTestHelper helper, BlockPos rel, List<String> heard) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa_raid_1104c"), false);
		ServerPlayer p = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				if (!overlay) {
					heard.add(message.getString());
				}
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, p, cookie);
		Leftovers.after(helper, () -> server.getPlayerList().remove(p));
		p.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(rel);
		p.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return p;
	}

	static List<String> raidLines(GameTestHelper helper, BlockPos hall) {
		List<String> lines = new ArrayList<>();
		if (helper.getLevel().getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
			for (Chronicle.Entry entry : e.chronicle()) {
				if (entry.kind() == Chronicle.Kind.RAID) {
					lines.add(entry.text().getString());
				}
			}
		}
		return lines;
	}

	static void tick(ServerLevel level, BlockPos hall) {
		VillageRaids.tick(level, hall, 0, 0, 0, day -> { });
	}

	/** The raider is the other village's: A's round finds none of its own left, and A has fought its raid off. */
	static void assertFoughtOff(GameTestHelper helper, BlockPos hallA, BlockPos hallB, Husk husk, List<String> heard, String where) {
		ServerLevel level = helper.getLevel();
		tick(level, hallA);
		helper.assertTrue(ThreatData.get(level).raid(hallA).isEmpty(),
			"a raider " + where + " (within that village's reach and nearer its hall) still kept this village's raid going in its round");
		List<ThreatData.Past> past = ThreatData.get(level).past(hallA);
		helper.assertTrue(past.size() == 1 && !past.get(0).fled() && past.get(0).fell() == 3 && past.get(0).came() == 3,
			"expected this village's past attacks to show the raid fought off (3 came, 3 fell): " + past);
		String won = Words.counted("chronicle.aliveworkplace.raid_won", 3, 3).getString();
		helper.assertTrue(raidLines(helper, hallA).equals(List.of(won)), "expected the chronicle to read '" + won + "' once: " + raidLines(helper, hallA));
		if (heard != null) {
			String line = Component.translatable("message.aliveworkplace.raid.won", VillageHalls.name(level, hallA)).getString();
			helper.assertTrue(heard.stream().filter(line::equals).count() == 1, "expected the player in the village to read '" + line + "' once: " + heard);
		}
		helper.assertTrue(husk.isAlive() && !husk.isRemoved(), "this village's round sent away the other village's raider " + where);
		tick(level, hallB);
		helper.assertTrue(ThreatData.get(level).raid(hallB).isPresent(), "the other village's raid ended in its round with its raider " + where);
	}

	/** The raider is this village's: A's round at night keeps the raid going, and nothing is booked yet. */
	static void assertStillOn(GameTestHelper helper, BlockPos hallA, Husk husk, String where) {
		ServerLevel level = helper.getLevel();
		tick(level, hallA);
		helper.assertTrue(ThreatData.get(level).raid(hallA).isPresent(),
			"this village's round ended its raid as fought off though its raider stands " + where + " (out of the other village's reach)");
		helper.assertTrue(ThreatData.get(level).past(hallA).isEmpty(), "expected no past attack while the raid goes on: " + ThreatData.get(level).past(hallA));
		helper.assertTrue(raidLines(helper, hallA).isEmpty(), "expected no raid line in the chronicle yet: " + raidLines(helper, hallA));
		helper.assertTrue(husk.isAlive() && !husk.isRemoved(), "this village's night round sent away its own raider");
	}

	private static BlockPos[] two(GameTestHelper helper, long dayTime, int dx, int dy, int dz, List<BlockPos> halls) {
		ServerLevel level = setUp(helper, dayTime, REL_HALL, halls);
		BlockPos hallA = helper.absolutePos(REL_HALL);
		BlockPos at = helper.absolutePos(REL_RAIDER);
		BlockPos hallB = at.offset(dx, dy, dz);
		raid(helper, hallA, halls);
		raid(helper, hallB, halls);
		helper.assertTrue(at.distSqr(hallA) == 2411, "setup: expected the raider 2411 squared from the first hall: " + at.distSqr(hallA));
		helper.assertTrue(at.distSqr(hallB) < at.distSqr(hallA), "setup: expected the raider nearer the second hall");
		return new BlockPos[] {hallA, hallB};
	}

	private static Husk ours(GameTestHelper helper, BlockPos hallA) {
		Husk husk = raider(helper, REL_RAIDER);
		helper.assertTrue(VillageRaids.raiders(helper.getLevel(), hallA).contains(husk), "setup: expected the raider within the first village's reach");
		return husk;
	}

	/** 48 blocks above another raided village's hall: the top of its reach, so the raider is that raid's. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwn48Above_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwn48Above_1104c")
	public void aRaider48BlocksAboveAnotherRaidedHallIsThatRaidsAndThisVillageHasFoughtItsRaidOff(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		BlockPos[] h = two(helper, NIGHT, 0, -48, 0, halls);
		List<String> heard = new ArrayList<>();
		listener(helper, new BlockPos(27, 2, 2), heard);
		Husk husk = ours(helper, h[0]);
		helper.assertTrue(VillageRaids.raiders(helper.getLevel(), h[1]).contains(husk), "setup: expected the raider within the second village's reach");
		assertFoughtOff(helper, h[0], h[1], husk, heard, "48 blocks above another raided village's hall");
		helper.succeed();
	}

	/** 49 blocks above: out of the other village's reach, so the raider is this raid's; at dawn it leaves with it. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwn49Above_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwn49Above_1104c")
	public void aRaider49BlocksAboveAnotherRaidedHallIsThisRaidsKeepsItGoingAndLeavesWithItAtDawn(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		BlockPos[] h = two(helper, NIGHT, 0, -49, 0, halls);
		ServerLevel level = helper.getLevel();
		Husk husk = ours(helper, h[0]);
		helper.assertFalse(VillageRaids.raiders(level, h[1]).contains(husk), "setup: expected the raider out of the second village's reach");
		assertStillOn(helper, h[0], husk, "49 blocks above another raided village's hall");
		level.setDayTime((level.getDayTime() / 24000) * 24000 + 24000 + MORNING);
		tick(level, h[0]);
		helper.assertTrue(ThreatData.get(level).raid(h[0]).isEmpty(), "expected this village's raid over at dawn: " + ThreatData.get(level).raid(h[0]));
		helper.assertTrue(husk.isRemoved(), "this village's raider (49 above the other raided hall) didn't leave with its raid at dawn");
		List<ThreatData.Past> past = ThreatData.get(level).past(h[0]);
		helper.assertTrue(past.size() == 1 && past.get(0).fled() && past.get(0).fell() == 2,
			"expected the past attack to show the raiders fled with 1 of 3 still about (2 fell): " + past);
		String fled = Component.translatable("chronicle.aliveworkplace.raid_fled").getString();
		helper.assertTrue(raidLines(helper, h[0]).equals(List.of(fled)), "expected the chronicle to read '" + fled + "' once: " + raidLines(helper, h[0]));
		helper.assertTrue(ThreatData.get(level).raid(h[1]).isPresent(), "the other village's raid ended with this one's dawn");
		helper.succeed();
	}

	/** 44 blocks across (the hall radius 12 + 32) from another raided hall: the edge of its reach, that raid's. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwnX44_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwnX44_1104c")
	public void aRaiderAtTheEdgeOfAnotherRaidedVillagesReachAcrossIsThatRaids(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		BlockPos[] h = two(helper, NIGHT, -44, 0, 0, halls);
		Husk husk = ours(helper, h[0]);
		helper.assertTrue(VillageRaids.raiders(helper.getLevel(), h[1]).contains(husk), "setup: expected the raider within the second village's reach");
		assertFoughtOff(helper, h[0], h[1], husk, null, "44 blocks east of another raided village's hall");
		helper.succeed();
	}

	/** 45 blocks across: one past the other village's reach, so it is this raid's and goes when this raid is called off. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwnX45_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwnX45_1104c")
	public void aRaiderOnePastAnotherRaidedVillagesReachAcrossIsThisRaidsAndGoesWhenCalledOff(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		BlockPos[] h = two(helper, NIGHT, -45, 0, 0, halls);
		ServerLevel level = helper.getLevel();
		Husk husk = ours(helper, h[0]);
		helper.assertFalse(VillageRaids.raiders(level, h[1]).contains(husk), "setup: expected the raider out of the second village's reach");
		assertStillOn(helper, h[0], husk, "45 blocks east of another raided village's hall");
		helper.assertTrue(VillageRaids.callOff(level, h[0]), "setup: expected this village's raid called off");
		helper.assertTrue(husk.isRemoved(), "a raider 45 blocks east of another raided village's hall (out of its reach) stayed when this village's raid was called off");
		helper.assertTrue(ThreatData.get(level).raid(h[1]).isPresent(), "the other village's raid ended with this one's call-off");
		helper.succeed();
	}

	/** 44 blocks along the other axis (north of the other hall): within its reach, that raid's. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwnZ44_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwnZ44_1104c")
	public void aRaider44BlocksNorthOfAnotherRaidedHallIsThatRaids(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		BlockPos[] h = two(helper, NIGHT, 0, 0, 44, halls);
		Husk husk = ours(helper, h[0]);
		helper.assertTrue(VillageRaids.raiders(helper.getLevel(), h[1]).contains(husk), "setup: expected the raider within the second village's reach");
		assertFoughtOff(helper, h[0], h[1], husk, null, "44 blocks north of another raided village's hall");
		helper.succeed();
	}

	/** 45 blocks along: out of the other village's reach, this raid's; its round keeps the raid going. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwnZ45_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwnZ45_1104c")
	public void aRaider45BlocksNorthOfAnotherRaidedHallIsThisRaids(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		BlockPos[] h = two(helper, NIGHT, 0, 0, 45, halls);
		Husk husk = ours(helper, h[0]);
		helper.assertFalse(VillageRaids.raiders(helper.getLevel(), h[1]).contains(husk), "setup: expected the raider out of the second village's reach");
		assertStillOn(helper, h[0], husk, "45 blocks north of another raided village's hall");
		helper.succeed();
	}

	/**
	 * Exactly as far from both halls (10 blocks each, both within reach): the other hall isn't nearer, so the raider is
	 * still this raid's: this village's round keeps its raid going, and calling it off sends the raider away.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwnTie_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwnTie_1104c")
	public void aRaiderAsFarFromBothRaidedHallsIsStillThisRaids(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = setUp(helper, NIGHT, new BlockPos(2, 2, 2), halls);
		BlockPos hallA = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos hallB = helper.absolutePos(new BlockPos(2, 2, 22));
		raid(helper, hallA, halls);
		raid(helper, hallB, halls);
		Husk husk = raider(helper, new BlockPos(2, 2, 12));
		helper.assertTrue(husk.blockPosition().distSqr(hallA) == husk.blockPosition().distSqr(hallB), "setup: expected the raider as far from both halls");
		assertStillOn(helper, hallA, husk, "as far from another raided village's hall as from its own");
		helper.assertTrue(VillageRaids.callOff(level, hallA), "setup: expected this village's raid called off");
		helper.assertTrue(husk.isRemoved(), "a raider as far from both raided halls stayed when the first village's raid was called off");
		helper.succeed();
	}

	/** One block nearer the other raided hall than the tie: that raid's, and this village has fought its raid off. */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwnTiePlus1_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwnTiePlus1_1104c")
	public void aRaiderOneBlockNearerTheOtherRaidedHallIsThatRaids(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		setUp(helper, NIGHT, new BlockPos(2, 2, 2), halls);
		BlockPos hallA = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos hallB = helper.absolutePos(new BlockPos(2, 2, 22));
		raid(helper, hallA, halls);
		raid(helper, hallB, halls);
		Husk husk = raider(helper, new BlockPos(2, 2, 13));
		assertFoughtOff(helper, hallA, hallB, husk, null, "one block nearer another raided village's hall (9 blocks, 11 from its own)");
		helper.succeed();
	}

	/**
	 * The other village's raid is over: a raider standing where it would be that raid's (48 above its hall) is this one's
	 * again: this village's round keeps its raid going, and at dawn the raider leaves with it.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidOwnOtherEnded_1104c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidOwnOtherEnded_1104c")
	public void aRaiderNearAVillageWhoseRaidIsOverIsThisRaidsAndLeavesWithItAtDawn(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		BlockPos[] h = two(helper, NIGHT, 0, -48, 0, halls);
		ServerLevel level = helper.getLevel();
		ThreatData.get(level).end(h[1]); // the other village's raid is over
		Husk husk = ours(helper, h[0]);
		assertStillOn(helper, h[0], husk, "48 blocks above another village's hall whose raid is over");
		level.setDayTime((level.getDayTime() / 24000) * 24000 + 24000 + MORNING);
		tick(level, h[0]);
		helper.assertTrue(husk.isRemoved(), "this village's raider didn't leave with its raid at dawn");
		List<ThreatData.Past> past = ThreatData.get(level).past(h[0]);
		helper.assertTrue(past.size() == 1 && past.get(0).fled(), "expected the past attack to show the raiders fled: " + past);
		helper.succeed();
	}
}

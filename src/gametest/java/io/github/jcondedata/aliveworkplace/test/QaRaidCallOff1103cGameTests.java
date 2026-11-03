package io.github.jcondedata.aliveworkplace.test;

import com.mojang.authlib.GameProfile;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Risings;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane C, 2026-11-03), verifying B849 (calling off a village's raid sends away only its own raiders,
 * {@code VillageRaids.own}: not those nearer another village with a raid under way) from 32.21's text ("its raiders go
 * at once ... the village is told. Nothing goes into the list of past attacks") at the boundaries
 * {@link QaRisingsVerify1102dGameTests#aNeighbourVillagesCallOffLeavesTheOtherRaidsRaidersAndItGoesOn} doesn't reach:
 * <ul>
 * <li>a raider nearer a neighbour hall with no raid under way is this raid's, so it goes;</li>
 * <li>a raider exactly as near both halls is not "nearer another village", so it goes;</li>
 * <li>after the call-off, the neighbour's raid still ends as fought off when its own raiders fall, and only it is
 *     booked;</li>
 * <li>{@code /workplace threat end}'s words: the operator's and the players', naming the called-off village only;</li>
 * <li>both raids saved and loaded before the call-off: the neighbour's raider still stays.</li>
 * </ul>
 */
public class QaRaidCallOff1103cGameTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_test:huge_area";
	private static final java.util.concurrent.atomic.AtomicInteger NAMES = new java.util.concurrent.atomic.AtomicInteger();
	static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");

	/** Clears leftovers; puts back the day, the radius and the halls' raids afterwards. Halls are named "Alpha", "Beta"... */
	static void setUp(GameTestHelper helper, BlockPos... halls) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Risings.forget();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box.inflate(48), m -> m instanceof Husk || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, box.inflate(4), e -> true).forEach(ItemEntity::discard);
			Risings.forget();
			for (BlockPos rel : halls) {
				BlockPos hall = helper.absolutePos(rel);
				ThreatData.get(level).end(hall);
				ThreatData.get(level).forgetPast(hall);
				VillageRaids.forget(hall);
			}
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		VillageHalls.RADIUS = 12;
		level.setDayTime((level.getDayTime() / 24000) * 24000 + 1000); // morning: the desert raiders' hours (until noon)
		String[] names = {"Alpha", "Beta", "Gamma"};
		for (int i = 0; i < halls.length; i++) {
			helper.setBlock(halls[i], ModBlocks.VILLAGE_HALL);
			if (level.getBlockEntity(helper.absolutePos(halls[i])) instanceof VillageHallBlockEntity entity) {
				entity.setCustomName(Component.literal(names[i]));
			}
			ThreatData.get(level).forgetPast(helper.absolutePos(halls[i]));
		}
	}

	static BlockPos raid(GameTestHelper helper, BlockPos rel, long ago) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(rel);
		ThreatData.get(level).begin(new ThreatData.Under(hall, DESERT, 3, level.getGameTime() - ago));
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: expected a desert raid under way at " + rel.toShortString());
		return hall;
	}

	static Husk raider(GameTestHelper helper, BlockPos at) {
		Husk husk = helper.spawn(EntityType.HUSK, at);
		husk.setNoAi(true);
		husk.addTag(VillageRaids.TAG);
		return husk;
	}

	static long raidLines(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity
			? entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.RAID).count() : -1;
	}

	/** An operator's command source at {@code at}, keeping what it's told. */
	static CommandSourceStack op(ServerLevel level, BlockPos at, List<String> said) {
		CommandSource out = new CommandSource() {
			@Override
			public void sendSystemMessage(Component message) {
				said.add(message.getString());
			}

			@Override
			public boolean acceptsSuccess() {
				return true;
			}

			@Override
			public boolean acceptsFailure() {
				return true;
			}

			@Override
			public boolean shouldInformAdmins() {
				return false;
			}
		};
		return new CommandSourceStack(out, Vec3.atCenterOf(at), Vec2.ZERO, level, 2, "test", Component.literal("test"), level.getServer(), null);
	}

	/** A player on the server at {@code rel}, keeping every chat line they're shown. */
	static ServerPlayer listener(GameTestHelper helper, BlockPos rel, List<String> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "qaCallOff" + NAMES.incrementAndGet()), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			// (displayClientMessage, which Chat.chat calls, ends here too: catching both counted each line twice)
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message.getString());
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.moveTo(Vec3.atCenterOf(helper.absolutePos(rel)));
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		seen.clear();
		return player;
	}

	/** As a save and a load of the world would do to the threats' saved data. */
	static void reloadThreats(ServerLevel level) {
		try {
			CompoundTag saved = ThreatData.get(level).save(new CompoundTag(), level.registryAccess());
			Method load = ThreatData.class.getDeclaredMethod("load", CompoundTag.class, HolderLookup.Provider.class);
			load.setAccessible(true);
			ThreatData loaded = (ThreatData) load.invoke(null, saved, level.registryAccess());
			level.getDataStorage().set(ThreatData.NAME, loaded);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("setup: couldn't save and load the threats: " + e, e);
		}
	}

	/**
	 * Only the first village has a raid; the second hall stands beside it with none. A raider stands 4 blocks from the
	 * second hall (31 from the first): no other raid is under way, so it is the first raid's and goes when it is called off.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidCallOffQuietNeighbour1103c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidCallOffQuietNeighbour1103c")
	public void aRaiderNearerANeighbourHallWithNoRaidGoesWhenTheRaidIsCalledOff(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(27, 2, 27);
		setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, relA, 100);
		BlockPos hallB = helper.absolutePos(relB);
		Husk near = raider(helper, new BlockPos(5, 2, 5));
		Husk far = raider(helper, new BlockPos(24, 2, 24));
		helper.assertTrue(ThreatData.get(level).raid(hallB).isEmpty(), "setup: expected no raid at the second village");
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(far), "setup: expected the far raider within the first village's reach");
		helper.assertTrue(VillageRaids.callOff(level, hallA), "setup: expected the first village's raid called off");
		helper.assertTrue(near.isRemoved(), "the raider beside the raided hall stayed after its raid was called off");
		helper.assertTrue(far.isRemoved(), "a raider nearer a neighbour hall with no raid under way (so this raid's) stayed after the raid was called off: "
			+ far.blockPosition().toShortString());
		helper.assertTrue(VillageRaids.raiders(level, hallA).isEmpty(), "raiders still about after the call-off: " + VillageRaids.raiders(level, hallA).size());
		helper.succeed();
	}

	/**
	 * Two raided villages 26 blocks apart along x; a raider stands exactly half way (13 blocks from each). It isn't nearer
	 * the other village, so the first village's call-off sends it away; the second village's raid goes on.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidCallOffHalfWay1103c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidCallOffHalfWay1103c")
	public void aRaiderHalfWayBetweenTwoRaidedVillagesGoesWithTheCalledOffRaid(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(28, 2, 2);
		setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, relA, 100);
		BlockPos hallB = raid(helper, relB, 100);
		Husk half = raider(helper, new BlockPos(15, 2, 2));
		Husk theirs = raider(helper, new BlockPos(26, 2, 2));
		helper.assertTrue(half.blockPosition().distSqr(hallA) == half.blockPosition().distSqr(hallB),
			"setup: expected the raider as near both halls, at " + half.blockPosition().toShortString());
		helper.assertTrue(VillageRaids.callOff(level, hallA), "setup: expected the first village's raid called off");
		helper.assertTrue(half.isRemoved(), "a raider as near the called-off village as the other (not nearer the other) stayed after the call-off");
		helper.assertTrue(!theirs.isRemoved() && theirs.isAlive(), "the second village's own raider (2 blocks from its hall) was sent away");
		helper.assertTrue(ThreatData.get(level).raid(hallB).isPresent(), "the second village's raid ended at the neighbour's call-off");
		helper.succeed();
	}

	/**
	 * Two raided villages; the first's raid is called off. Then the second's own raider falls, and at its next round the
	 * second village's raid ends as fought off: one past attack for it (3 came, 3 fell, not fled) and a chronicle line;
	 * the called-off village books nothing (32.21: "nothing goes into the list of past attacks") and gets no line of a
	 * raid fought off or fled.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidCallOffThenWon1103c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidCallOffThenWon1103c")
	public void afterANeighboursCallOffTheOtherRaidStillEndsAsFoughtOffWhenItsRaidersFall(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(27, 2, 27);
		setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, relA, 100);
		BlockPos hallB = raid(helper, relB, 100);
		long linesA = raidLines(level, hallA);
		long linesB = raidLines(level, hallB);
		raider(helper, new BlockPos(6, 2, 6));
		Husk ours = raider(helper, new BlockPos(22, 2, 22));
		helper.assertTrue(VillageRaids.callOff(level, hallA), "setup: expected the first village's raid called off");
		helper.assertTrue(ours.isAlive(), "setup: the second village's raider was sent away by the neighbour's call-off");
		VillageRaids.tick(level, hallB, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallB).isPresent(), "the second village's raid ended while its raider was still about");
		ours.kill();
		VillageRaids.tick(level, hallB, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallB).isEmpty(), "the second village's raid didn't end at its round after its last raider fell");
		List<ThreatData.Past> pastB = ThreatData.get(level).past(hallB);
		helper.assertTrue(pastB.size() == 1, "expected one past attack booked at the second village, found " + pastB);
		ThreatData.Past p = pastB.get(0);
		helper.assertTrue(!p.fled() && p.came() == 3 && p.fell() == 3 && p.culture().equals(DESERT),
			"expected the second village's raid booked as fought off (3 came, 3 fell, not fled, desert), booked " + p);
		helper.assertTrue(raidLines(level, hallB) == linesB + 1, "expected one raid line in the second village's chronicle, lines before "
			+ linesB + ", after " + raidLines(level, hallB));
		helper.assertTrue(ThreatData.get(level).past(hallA).isEmpty(), "the called-off raid was booked in the first village's past attacks: " + ThreatData.get(level).past(hallA));
		helper.assertTrue(raidLines(level, hallA) == linesA, "the called-off village got a raid line in its chronicle (as fought off or fled): before "
			+ linesA + ", after " + raidLines(level, hallA));
		helper.succeed();
	}

	/**
	 * {@code /workplace threat end} at the first of two raided villages: the operator is told the first village's raid
	 * is over, a player standing between them reads "The raid on Alpha is over: the raiders are gone." once and nothing
	 * about Beta (no call-off, no fought off, no fled), and Beta's raid and raider stay.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidCallOffWords1103c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidCallOffWords1103c")
	public void threatEndTellsOnlyTheCalledOffVillageAndLeavesTheNeighbour(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(27, 2, 27);
		setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, relA, 100);
		BlockPos hallB = raid(helper, relB, 100);
		Husk theirs = raider(helper, new BlockPos(6, 2, 6));
		Husk ours = raider(helper, new BlockPos(22, 2, 22));
		List<String> seen = new ArrayList<>();
		listener(helper, new BlockPos(14, 2, 14), seen);
		List<String> said = new ArrayList<>();
		level.getServer().getCommands().performPrefixedCommand(op(level, hallA.east(), said), "workplace threat end");
		helper.assertTrue(said.contains("Alpha: the raid and the disaster under way are over."), "the operator was told: " + said);
		helper.assertTrue(ThreatData.get(level).raid(hallA).isEmpty(), "the first village's raid is still under way after /workplace threat end");
		helper.assertTrue(theirs.isRemoved(), "the first village's raider stayed after /workplace threat end");
		helper.assertTrue(!ours.isRemoved() && ThreatData.get(level).raid(hallB).isPresent(), "/workplace threat end at Alpha sent away Beta's raider or ended its raid");
		long alpha = seen.stream().filter(s -> s.equals("The raid on Alpha is over: the raiders are gone.")).count();
		helper.assertTrue(alpha == 1, "expected the player told once that the raid on Alpha is over, was shown: " + seen);
		helper.assertTrue(seen.stream().noneMatch(s -> s.contains("Beta")), "the player was told something of Beta, whose raid goes on: " + seen);
		helper.assertTrue(seen.stream().noneMatch(s -> s.contains("fought off") || s.contains("have fled")),
			"a call-off was told as a raid fought off or fled: " + seen);
		helper.succeed();
	}

	/**
	 * Both raids are saved and loaded (as a restart does) before the first village's raid is called off: the second's
	 * raider, within the first's reach but nearer its own hall, still stays, and the second raid is the same one after
	 * another save and load.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaRaidCallOffReload1103c"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRaidCallOffReload1103c")
	public void aCallOffAfterASaveAndLoadStillLeavesTheNeighboursRaiders(GameTestHelper helper) {
		BlockPos relA = new BlockPos(2, 2, 2);
		BlockPos relB = new BlockPos(27, 2, 27);
		setUp(helper, relA, relB);
		ServerLevel level = helper.getLevel();
		BlockPos hallA = raid(helper, relA, 100);
		BlockPos hallB = raid(helper, relB, 100);
		long began = ThreatData.get(level).raid(hallB).orElseThrow().began();
		Husk theirs = raider(helper, new BlockPos(6, 2, 6));
		Husk ours = raider(helper, new BlockPos(22, 2, 22));
		reloadThreats(level);
		helper.assertTrue(ThreatData.get(level).raid(hallA).isPresent() && ThreatData.get(level).raid(hallB).isPresent(),
			"setup: a raid under way was lost in the save and load: " + ThreatData.get(level).raids());
		helper.assertTrue(VillageRaids.callOff(level, hallA), "the first village's raid couldn't be called off after a save and load");
		helper.assertTrue(theirs.isRemoved(), "the first village's own raider stayed after the call-off");
		helper.assertTrue(!ours.isRemoved() && ours.isAlive(), "after a save and load the neighbour's call-off sent away the second village's raider");
		reloadThreats(level);
		helper.assertTrue(ThreatData.get(level).raid(hallA).isEmpty(), "the called-off raid came back after a save and load");
		helper.assertTrue(ThreatData.get(level).raid(hallB).map(r -> r.began() == began).orElse(false),
			"the second village's raid wasn't the same one after the call-off and a save and load: " + ThreatData.get(level).raid(hallB));
		VillageRaids.tick(level, hallB, 0, 0, 0, day -> { });
		helper.assertTrue(ThreatData.get(level).raid(hallB).isPresent(), "the second village's raid ended at its round with its raider still about");
		helper.succeed();
	}
}

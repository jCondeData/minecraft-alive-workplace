package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.test.RealmGameTests.Land;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-d-1123-1309), written from docs/wiki/feuds.md (ROADMAP 33.20), for what the earlier feud tests leave out:
 * the guards' reach at exactly 64 blocks and one past; the war's first and last tick; a rival owner's friend (a foe,
 * and the rival's prices) and someone who also rules the guard's village or an operator (never a foe); the declaring
 * side's guards fighting too, their fallen counted for that side; golems and animals kept safe in a war; peace, feudWars
 * off and feuds off mid-war standing the guards down; a war kept through a save and reload; a stranger speaking for a
 * village; caravan range at exactly 2,048 blocks and one past; the sentences the page quotes, word for word.
 */
public class QaFeuds1123dGameTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private record Two(Land land, ServerPlayer them, List<Component> theirs) {
	}

	/** Village 0 the owner's (who declares), village 1 the neighbour's; pacts and feuds on and empty, PvP on; all put back after. */
	private static Two two(GameTestHelper helper) {
		Land land = RealmGameTests.land(helper, VillageRanks.Rank.VILLAGE, VillageRanks.Rank.VILLAGE);
		for (int i = 0; i < 2; i++) {
			land.entity(i).setCustomName(Component.literal("Feudqa " + land.hall(i).getX() + " " + land.hall(i).getZ()));
		}
		MinecraftServer server = land.level().getServer();
		boolean pacts = Pacts.ENABLED;
		boolean alliances = Pacts.ALLIANCES;
		boolean feuds = Feuds.ENABLED;
		boolean wars = Feuds.WARS;
		boolean pvp = server.isPvpAllowed();
		Pacts.ENABLED = true;
		Pacts.ALLIANCES = true;
		Feuds.ENABLED = true;
		Feuds.WARS = true;
		server.setPvpAllowed(true);
		Pacts.Data.get(server).clear();
		Pacts.Data.get(server).setMigrated(true);
		Feuds.Data.get(server).clear();
		Leftovers.after(helper, () -> {
			Pacts.ENABLED = pacts;
			Pacts.ALLIANCES = alliances;
			Feuds.ENABLED = feuds;
			Feuds.WARS = wars;
			server.setPvpAllowed(pvp);
			Pacts.Data.get(server).clear();
			Feuds.Data.get(server).clear();
			land.entity(0).setProtected(false);
			land.entity(1).setProtected(false);
			Caravans.Data data = Caravans.Data.get(land.level());
			data.remove(land.hall(0));
			data.remove(land.hall(1));
		});
		List<Component> theirs = new ArrayList<>();
		ServerPlayer them = RealmGameTests.listener(helper, theirs, "neighbour");
		land.entity(1).setOwner(them.getUUID(), "neighbour");
		Realms.round(land.level(), land.hall(1), land.entity(1), 6);
		Caravans.Data data = Caravans.Data.get(land.level());
		data.setWants(land.hall(0), Component.literal(land.name(0)), List.of());
		data.setWants(land.hall(1), Component.literal(land.name(1)), List.of());
		return new Two(land, them, theirs);
	}

	private static long now(MinecraftServer server) {
		return server.overworld().getGameTime();
	}

	/** A feud declared by village 0 on village 1 two days ago, its war from {@code starts} to {@code ends}. */
	private static void war(Land land, long starts, long ends) {
		MinecraftServer server = land.level().getServer();
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now(server) - 2 * 24000L, starts, ends, false, 0, 0, 0));
	}

	/** A war begun a moment ago, three days long. */
	private static void atWar(Land land) {
		long now = now(land.level().getServer());
		war(land, now - 1, now - 1 + Feuds.WAR_DAYS * 24000L);
	}

	private static void stand(ServerPlayer player, BlockPos at) {
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
	}

	/** A player whose permission level is {@code level[0]}: 2 or more makes them an operator. */
	private static ServerPlayer operator(GameTestHelper helper, int[] level) {
		net.minecraft.server.level.ServerLevel world = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "enemy-op"), false);
		ServerPlayer player = new ServerPlayer(world.getServer(), world, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public int getPermissionLevel() {
				return level[0];
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		world.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		Leftovers.after(helper, () -> world.getServer().getPlayerList().remove(player));
		return player;
	}

	/** A guard of village {@code i}, kept still. */
	private static Villager guard(Land land, int i) {
		Villager guard = ReliefGameTests.guards(land, i, 1).get(0);
		guard.setNoAi(true);
		return guard;
	}

	/** "Inside the guards' own village (within 64 blocks of its hall)": a foe at 64 blocks, not at 65, either way along x and z. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1123dReach"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1123dReach")
	public void theGuardsGoForTheEnemyUpToSixtyFourBlocksAndNoFurther(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		ServerPlayer enemy = land.owner();
		Villager guard = guard(land, 1);
		atWar(land);
		helper.assertTrue(VillageHalls.RADIUS == 64, "the village's reach isn't 64 blocks: " + VillageHalls.RADIUS);
		BlockPos hall = land.hall(1);
		stand(enemy, hall.offset(0, 0, 1));
		helper.assertTrue(Guards.isFoe(enemy, guard), "setup: the enemy owner at the hall isn't a foe");
		int[][] ways = {{1, 0}, {0, 1}, {0, -1}};
		for (int[] w : ways) {
			stand(enemy, hall.offset(64 * w[0], 0, 64 * w[1]));
			helper.assertTrue(Guards.isFoe(enemy, guard), "the enemy owner 64 blocks from the hall (" + w[0] + ", " + w[1] + ") isn't a foe");
			stand(enemy, hall.offset(65 * w[0], 0, 65 * w[1]));
			helper.assertTrue(!Guards.isFoe(enemy, guard), "the enemy owner 65 blocks from the hall (" + w[0] + ", " + w[1] + ") is still a foe");
		}
		helper.succeed();
	}

	/** The war starts at its dawn, not a tick before, and is over at its end, not a tick after. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1123dTicks"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1123dTicks")
	public void theWarRunsFromItsDawnToItsEndToTheTick(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer enemy = land.owner();
		Villager guard = guard(land, 1);
		stand(enemy, land.hall(1).offset(0, 0, 1));
		long now = now(server);
		war(land, now + 1, now + 1 + 3 * 24000L);
		helper.assertTrue(!Guards.isFoe(enemy, guard), "a foe a tick before the war's dawn");
		war(land, now, now + 3 * 24000L);
		helper.assertTrue(Guards.isFoe(enemy, guard), "not a foe at the war's dawn");
		// (the test world's clock may be younger than 3 days: a war that began a tick ago stands for one 3 days old)
		war(land, now - 1, now + 1);
		helper.assertTrue(Guards.isFoe(enemy, guard), "not a foe in the war's last tick");
		war(land, now - 1, now);
		helper.assertTrue(!Guards.isFoe(enemy, guard), "still a foe when the war's 3 days are over");
		helper.succeed();
	}

	/**
	 * "A side's people are its villages' owners and their friends": the enemy owner's friend is a foe, may fight the
	 * guard and pays the rival's prices. "Anyone who rules the guard's own village" (a friend of both owners) and "an
	 * operator" are never the guards' foe.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1123dFriends"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1123dFriends")
	public void theEnemysFriendIsAFoeButNotOneWhoRulesTheGuardsVillageOrAnOperator(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer enemy = land.owner();
		ServerPlayer pal = RealmGameTests.listener(helper, new ArrayList<>(), "enemy-pal");
		int[] level = {0};
		ServerPlayer op = operator(helper, level);
		Friends friends = Friends.get(server);
		Leftovers.after(helper, () -> {
			friends.remove(enemy.getUUID(), pal.getUUID());
			friends.remove(two.them().getUUID(), pal.getUUID());
			friends.remove(enemy.getUUID(), op.getUUID());
		});
		land.entity(1).setProtected(true);
		Villager guard = guard(land, 1);
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), enemy).ok(), "couldn't declare");
		friends.add(enemy.getUUID(), pal.getUUID(), "enemy-pal");
		// a feud: the enemy's friend pays the rival's prices at village 1's board
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(1), pal, true) == 125 && Feuds.boardPercent(land.level(), land.hall(1), pal, false) == 75,
			"the enemy owner's friend at the rival's board: " + Feuds.boardPercent(land.level(), land.hall(1), pal, true) + "/" + Feuds.boardPercent(land.level(), land.hall(1), pal, false));
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(0), pal, true) == 100, "the friend's prices moved at their friend's own board");
		atWar(land);
		BlockPos stand = land.hall(1).offset(0, 0, 1);
		stand(pal, stand);
		stand(enemy, stand);
		helper.assertTrue(Guards.isFoe(pal, guard), "the enemy owner's friend isn't the guards' foe in the war");
		helper.assertTrue(VillageProtection.mayHurt(land.level(), pal, guard), "the enemy owner's friend may not fight the guard in the war");
		// a friend of both owners rules the guard's village: never a foe, and pays the usual price
		friends.add(two.them().getUUID(), pal.getUUID(), "enemy-pal");
		helper.assertTrue(!Guards.isFoe(pal, guard), "someone who rules the guard's own village is its foe");
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(1), pal, true) == 100, "someone who rules the village pays the rival's price there");
		// an operator on the enemy's side is never the guards' foe
		friends.add(enemy.getUUID(), op.getUUID(), "enemy-op");
		stand(op, stand);
		level[0] = 0;
		helper.assertTrue(Guards.isFoe(op, guard), "setup: the enemy owner's other friend isn't a foe");
		level[0] = 2;
		helper.assertTrue(op.hasPermissions(2), "setup: no operator");
		helper.assertTrue(!Guards.isFoe(op, guard), "an operator is the guards' foe");
		helper.succeed();
	}

	/** "Each side's guards": the declaring side's guard goes for the rival owner in its own village, and one that falls counts as ours for the declarer. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1123dBothSides"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1123dBothSides")
	public void theDeclarersGuardsFightTheRivalOwnerToo(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer rival = two.them();
		land.entity(0).setProtected(true);
		List<Villager> guards = ReliefGameTests.guards(land, 0, 2);
		guards.forEach(g -> g.setNoAi(true));
		Villager guard = guards.get(0);
		BlockPos stand = land.hall(0).offset(0, 0, 1);
		stand(rival, stand);
		stand(land.owner(), stand);
		atWar(land);
		helper.assertTrue(Guards.isFoe(rival, guard), "the declarer's guard doesn't go for the rival owner in its own village");
		helper.assertTrue(!Guards.isFoe(land.owner(), guard), "the declarer's guard goes for its own owner");
		helper.assertTrue(VillageProtection.mayHurt(land.level(), rival, guard), "the rival owner may not fight the declarer's guard");
		Villager fallen = guards.get(1);
		fallen.die(land.level().damageSources().playerAttack(rival));
		fallen.discard();
		Feuds.Feud f = Feuds.feud(server, land.at(0), land.at(1));
		helper.assertTrue(f.lostBy() == 1 && f.lostOn() == 0, "the declarer's fallen guard wasn't counted for the declarer: " + f);
		helper.succeed();
	}

	/** "Nothing else changes": in a war the enemy owner still can't hurt the village's iron golem or its animals. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1123dKept"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1123dKept")
	public void inAWarGolemsAndAnimalsStayProtected(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		ServerPlayer enemy = land.owner();
		land.entity(1).setProtected(true);
		Villager guard = guard(land, 1);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(13, 2, 8));
		golem.setNoAi(true);
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(11, 2, 8));
		cow.setNoAi(true);
		stand(enemy, land.hall(1).offset(0, 0, 1));
		atWar(land);
		helper.assertTrue(VillageProtection.mayHurt(land.level(), enemy, guard), "setup: the enemy may not fight the guard in the war");
		helper.assertTrue(!VillageProtection.mayHurt(land.level(), enemy, golem), "the enemy owner may hurt the village's iron golem in a war");
		helper.assertTrue(!VillageProtection.mayHurt(land.level(), enemy, cow), "the enemy owner may hurt the village's animals in a war");
		helper.succeed();
	}

	/** Mid-war, feudWars off, then feuds off, stand the guards down (and back up when on again); accepted peace ends the war for good. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1123dStandDown"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1123dStandDown")
	public void peaceOrASwitchOffMidWarStandsTheGuardsDown(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer enemy = land.owner();
		land.entity(1).setProtected(true);
		Villager guard = guard(land, 1);
		stand(enemy, land.hall(1).offset(0, 0, 1));
		atWar(land);
		helper.assertTrue(Guards.isFoe(enemy, guard) && VillageProtection.mayHurt(land.level(), enemy, guard), "setup: no war");
		Feuds.WARS = false;
		helper.assertTrue(!Guards.isFoe(enemy, guard), "a foe with feudWars off");
		helper.assertTrue(!VillageProtection.mayHurt(land.level(), enemy, guard), "the enemy may fight the guard with feudWars off");
		Feuds.WARS = true;
		helper.assertTrue(Guards.isFoe(enemy, guard), "not a foe again with feudWars back on");
		Feuds.ENABLED = false;
		helper.assertTrue(!Guards.isFoe(enemy, guard) && !VillageProtection.mayHurt(land.level(), enemy, guard), "the war goes on with feuds off");
		Feuds.ENABLED = true;
		helper.assertTrue(Guards.isFoe(enemy, guard), "setup: the war didn't come back with feuds on (not settled yet)");
		helper.assertTrue(Feuds.offerPeace(land.level(), land.hall(1), land.at(0), two.them()).ok(), "the rival couldn't offer peace");
		helper.assertTrue(Guards.isFoe(enemy, guard), "peace offered but not accepted ended the war");
		helper.assertTrue(Feuds.acceptPeace(land.level(), land.hall(0), land.at(1), enemy).ok(), "couldn't accept peace");
		helper.assertTrue(!Guards.isFoe(enemy, guard), "a foe after peace was accepted");
		helper.assertTrue(!VillageProtection.mayHurt(land.level(), enemy, guard), "the enemy may still fight the guard after peace");
		helper.assertTrue(Feuds.boardPercent(land.level(), land.hall(1), enemy, true) == 100, "the board still charges the rival after peace");
		Feuds.settle(server);
		helper.assertTrue(!Guards.isFoe(enemy, guard), "the war came back after settling");
		helper.succeed();
	}

	/** A war saved and loaded mid-way is still on, with its days and the guards lost; its guards still fight. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1123dReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1123dReload")
	public void aWarSavedAndLoadedMidWayGoesOn(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer enemy = land.owner();
		Villager guard = guard(land, 1);
		stand(enemy, land.hall(1).offset(0, 0, 1));
		long now = now(server);
		Feuds.Feud before = new Feuds.Feud(land.at(0), land.at(1), now - 3 * 24000L, now - 1, now + 2 * 24000L, false, 1, 2, 2);
		Feuds.Data data = Feuds.Data.get(server);
		data.put(before);
		CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
		data.clear();
		helper.assertTrue(!Guards.isFoe(enemy, guard), "setup: a foe with no feud");
		for (Feuds.Feud f : Feuds.Data.load(tag, server.registryAccess()).feuds()) {
			data.put(f);
		}
		Feuds.Feud after = Feuds.feud(server, land.at(0), land.at(1));
		helper.assertTrue(before.equals(after), "the war changed in a save and reload: " + after);
		helper.assertTrue(Guards.isFoe(enemy, guard), "the guards stopped fighting after a reload mid-war");
		Feuds.settle(server);
		Feuds.Feud settled = Feuds.feud(server, land.at(0), land.at(1));
		helper.assertTrue(settled != null && !settled.warDone() && settled.peace() == 2, "settling after the reload ended the war early or forgot the peace offer: " + settled);
		helper.succeed();
	}

	/** A player who doesn't own or befriend village 0 can't declare, offer, accept or raise a war for it; the feud stands as it was. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1123dStranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1123dStranger")
	public void aStrangerCantSpeakForAVillage(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		ServerPlayer stranger = RealmGameTests.listener(helper, new ArrayList<>(), "stranger");
		String notYours = "Only the village's owner or their friends can speak for it against " + land.name(1) + ".";
		Pacts.Result declare = Feuds.declare(land.level(), land.hall(0), land.hall(1), stranger);
		helper.assertTrue(!declare.ok() && declare.message().getString().equals(notYours), "a stranger declared: " + declare.message().getString());
		helper.assertTrue(!Feuds.feuding(server, land.at(0), land.at(1)), "a feud from a stranger");
		helper.assertTrue(Feuds.declare(land.level(), land.hall(0), land.hall(1), land.owner()).ok(), "the owner couldn't declare");
		Feuds.Data.get(server).put(new Feuds.Feud(land.at(0), land.at(1), now(server) - Feuds.WAR_AFTER));
		Pacts.Result war = Feuds.raiseWar(land.level(), land.hall(0), land.at(1), stranger);
		helper.assertTrue(!war.ok() && war.message().getString().equals(notYours), "a stranger raised the war: " + war.message().getString());
		Pacts.Result offer = Feuds.offerPeace(land.level(), land.hall(0), land.at(1), stranger);
		helper.assertTrue(!offer.ok() && offer.message().getString().equals(notYours), "a stranger offered peace: " + offer.message().getString());
		helper.assertTrue(Feuds.offerPeace(land.level(), land.hall(1), land.at(0), two.them()).ok(), "the rival couldn't offer peace");
		Pacts.Result accept = Feuds.acceptPeace(land.level(), land.hall(0), land.at(1), stranger);
		helper.assertTrue(!accept.ok() && accept.message().getString().equals(notYours), "a stranger accepted peace: " + accept.message().getString());
		Feuds.Feud f = Feuds.feud(server, land.at(0), land.at(1));
		helper.assertTrue(f != null && !f.warRaised() && f.peace() == 2, "the feud changed: " + f);
		helper.succeed();
	}

	/** "Within caravan range (2,048 blocks)": a village exactly 2,048 blocks away can be feuded with; one 2,049 away can't. */
	//$ gametest_ticks_batch AREA '100' '"qaFeud1123dRange"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFeud1123dRange")
	public void aFeudReachesExactlyCaravanRange(GameTestHelper helper) {
		Two two = two(helper);
		Land land = two.land();
		MinecraftServer server = land.level().getServer();
		RealmData realms = RealmData.get(server);
		helper.assertTrue(Caravans.RANGE == 2048, "caravan range isn't 2,048 blocks: " + Caravans.RANGE);
		BlockPos atRange = land.hall(0).offset(0, 0, -2048);
		BlockPos past = land.hall(0).offset(0, 0, -2049);
		GlobalPos inside = GlobalPos.of(land.level().dimension(), atRange);
		GlobalPos outside = GlobalPos.of(land.level().dimension(), past);
		Leftovers.after(helper, () -> {
			realms.forgetKnown(inside);
			realms.forgetKnown(outside);
		});
		long now = now(server);
		realms.putKnown(new RealmData.Known(inside, Component.literal("Feudqa Rim"), Optional.of(two.them().getUUID()), "neighbour", "village", 6, 50, now));
		realms.putKnown(new RealmData.Known(outside, Component.literal("Feudqa Beyond"), Optional.of(two.them().getUUID()), "neighbour", "village", 6, 50, now));
		Pacts.Result far = Feuds.declare(land.level(), land.hall(0), past, land.owner());
		String name = Feuds.sideName(server, outside).getString();
		helper.assertTrue(!far.ok() && far.message().getString().equals(name + " is more than 2048 blocks away, beyond caravan range."), "2,049 blocks away: " + far.message().getString());
		helper.assertTrue(!Feuds.feuding(server, land.at(0), outside), "a feud beyond caravan range");
		Pacts.Result rim = Feuds.declare(land.level(), land.hall(0), atRange, land.owner());
		helper.assertTrue(rim.ok(), "exactly 2,048 blocks away was refused: " + rim.message().getString());
		helper.assertTrue(Feuds.feuding(server, land.at(0), inside), "no feud at exactly caravan range");
		helper.succeed();
	}

	/** The sentences docs/wiki/feuds.md quotes, word for word from en_us.json (American English). */
	//$ gametest_ticks_batch AREA '20' '"qaFeud1123dWords"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "qaFeud1123dWords")
	public void theSentencesThePageQuotesReadWordForWord(GameTestHelper helper) {
		String[][] lines = {
			{"screen.aliveworkplace.feud.declare", "Declare a feud on Thornholm", "Thornholm"},
			{"screen.aliveworkplace.feud.feud", "Feud with Thornholm", "Thornholm"},
			{"screen.aliveworkplace.feud.offer_peace", "Offer Thornholm peace", "Thornholm"},
			{"screen.aliveworkplace.feud.accept_peace", "Accept peace with Thornholm", "Thornholm"},
			{"screen.aliveworkplace.feud.raise_war", "Raise the feud with Thornholm to war", "Thornholm"},
			{"screen.aliveworkplace.feud.fades.within_hour", "Fades in less than an hour without a war", null},
			{"screen.aliveworkplace.feud.at_war.within_hour", "At war: less than an hour left", null},
		};
		for (String[] l : lines) {
			String got = (l[2] == null ? Component.translatable(l[0]) : Component.translatable(l[0], l[2])).getString();
			helper.assertTrue(got.equals(l[1]), l[0] + ": \"" + got + "\", the page says \"" + l[1] + "\"");
		}
		String lost = Component.translatable("chronicle.aliveworkplace.feud.war_over", "Thornholm", 1, 0).getString();
		helper.assertTrue(lost.endsWith("Guards lost: ours 1, theirs 0"), "the war's end: " + lost);
		String grumble = Component.translatable("chatter.aliveworkplace.feud.0", "Thornholm").getString();
		helper.assertTrue(grumble.startsWith("I won't buy a thing from Thornholm's people."), "the grumble: " + grumble);
		for (int i = 0; i < 3; i++) {
			String line = Component.translatable("chatter.aliveworkplace.feud." + i, "Thornholm").getString();
			helper.assertTrue(line.contains("Thornholm") && !line.startsWith("chatter."), "chatter line " + i + ": " + line);
		}
		String pvp = Component.translatable("screen.aliveworkplace.feud.war_pvp").getString();
		helper.assertTrue(pvp.startsWith("Can't be pressed") && pvp.contains("PvP off"), "the grayed button's reason: " + pvp);
		helper.succeed();
	}
}

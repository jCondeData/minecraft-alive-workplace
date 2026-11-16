package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.HomeVillages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

/**
 * QA (lane b, 2026-11-16, session qa-lane-b-1116-1538), from the spec, not the code. B1318 and the
 * {@code /workplace unlock} command's own words: a questbook reward "unlocks it in the player's own village, wherever
 * they stand when a questbook reward runs it; the server-wide set only when no village is theirs and none is where they
 * stand". The nightly mutation run found nothing claimed the reward from ANOTHER DIMENSION than the home village (the
 * mutant that unlocks in the dimension the player stands in survived). Same dimension far away and another player's
 * village are {@link QuestRewardVillageGameTests}; here: the Nether, and no village at all.
 */
public class QaUnlockFor1116bGameTests {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation COTTAGE_2 = StarterBlueprints.STARTER_COTTAGE_2.id();

	private static void unlocks(GameTestHelper helper) {
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = was);
	}

	/** The server-wide set (Tables far from any hall) without {@code id} after the test, so later villages aren't seeded with it. */
	private static void forgetWild(MinecraftServer server, ResourceLocation id) {
		try {
			Class<?> wild = Class.forName("io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks$Wild");
			java.lang.reflect.Method get = wild.getDeclaredMethod("get", MinecraftServer.class);
			get.setAccessible(true);
			Object set = get.invoke(null, server);
			java.lang.reflect.Field ids = wild.getDeclaredField("ids");
			ids.setAccessible(true);
			((Set<?>) ids.get(set)).remove(id);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("couldn't reset the server-wide unlocks: " + e);
		}
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	/** The questbook's reward, as FTB Quests runs it: {@code /workplace unlock <player> <id>} from the server, at op level. */
	private static void reward(MinecraftServer server, ServerPlayer player, ResourceLocation id) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),
			"workplace unlock " + player.getGameProfile().getName() + " " + id);
	}

	/**
	 * The owner of an overworld village claims "Build a Starter Cottage"'s reward standing in the Nether: Starter Cottage
	 * II goes on their village's Table (the overworld hall), they are told "The village's library has a new blueprint:
	 * Starter Cottage II, and it goes into no server-wide set (which every later village would take in).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaUnlockFor1116bNether"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaUnlockFor1116bNether")
	public void aRewardClaimedInTheNetherReachesTheOverworldHomeVillage(GameTestHelper helper) {
		unlocks(helper);
		VillageHallBlockEntity entity = QaQuestbookClaims1116eGameTests.hall(helper);
		MinecraftServer server = helper.getLevel().getServer();
		Leftovers.after(helper, () -> forgetWild(server, COTTAGE_2));
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			ServerLevel nether = server.getLevel(Level.NETHER);
			helper.assertTrue(nether != null, "setup: no Nether on this server");
			BlockPos hall = entity.getBlockPos();
			helper.assertTrue(BlueprintUnlocks.state(level, hall, COTTAGE_2) == BlueprintUnlocks.State.LOCKED, "setup: Starter Cottage II isn't locked in a new village");
			helper.assertTrue(BlueprintUnlocks.state(level, null, COTTAGE_2) == BlueprintUnlocks.State.LOCKED, "setup: the server-wide set has Starter Cottage II");
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaUfN"));
			ServerPlayer owner = c.player();
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			owner.teleportTo(nether, hall.getX() + 0.5, 100, hall.getZ() + 0.5, 0f, 0f);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.mc.Players.level(owner) == nether, "setup: the owner isn't in the Nether: "
				+ io.github.jcondedata.aliveworkplace.mc.Players.level(owner).dimension());
			helper.assertTrue(HomeVillages.of(owner).equals(java.util.Optional.of(GlobalPos.of(Level.OVERWORLD, hall))),
				"from the Nether the owner's home village reads as " + HomeVillages.of(owner) + ", the hall is " + hall + " in the overworld");
			c.seen().clear();
			reward(server, owner, COTTAGE_2);
			helper.assertTrue(BlueprintUnlocks.state(level, hall, COTTAGE_2) == BlueprintUnlocks.State.UNLOCKED,
				"the owner claimed the reward in the Nether and Starter Cottage II isn't on the overworld village's Table: " + BlueprintUnlocks.state(level, hall, COTTAGE_2));
			helper.assertTrue(BlueprintUnlocks.state(level, null, COTTAGE_2) == BlueprintUnlocks.State.LOCKED,
				"the reward claimed in the Nether went into the server-wide set (every later village takes it in)");
			String line = "The village's library has a new blueprint: " + Blueprints.displayName(COTTAGE_2).getString();
			helper.assertTrue(texts(c.seen()).contains(line), "the owner should be told '" + line + "', saw " + texts(c.seen()));
			LOGGER.info("qa unlockFor 1116b: reward claimed in {} reached the hall at {} in the overworld; server-wide set untouched", nether.dimension().location(), hall);
			owner.teleportTo(level, hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 0.5, 0f, 0f);
			helper.succeed();
		});
	}

	/**
	 * A player with no village of their own, none ranked with and none where they stand ("the server-wide set only when no
	 * village is theirs and none is where they stand"), claims the reward in the Nether: it goes into the server-wide set,
	 * and they are told.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaUnlockFor1116bNoHome"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaUnlockFor1116bNoHome")
	public void aRewardClaimedWithNoVillageGoesToTheServerWideSet(GameTestHelper helper) {
		unlocks(helper);
		Leftovers.clear(helper);
		Leftovers.players(helper);
		MinecraftServer server = helper.getLevel().getServer();
		Leftovers.after(helper, () -> forgetWild(server, COTTAGE_2));
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			ServerLevel nether = server.getLevel(Level.NETHER);
			helper.assertTrue(BlueprintUnlocks.state(level, null, COTTAGE_2) == BlueprintUnlocks.State.LOCKED, "setup: the server-wide set has Starter Cottage II");
			QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaUfW"));
			ServerPlayer player = c.player();
			BlockPos spot = helper.absolutePos(new BlockPos(2, 2, 2));
			player.teleportTo(nether, spot.getX() + 0.5, 100, spot.getZ() + 0.5, 0f, 0f);
			VillageHalls.forgetNearest();
			helper.assertTrue(HomeVillages.of(player).isEmpty(), "setup: a new player has a home village: " + HomeVillages.of(player));
			c.seen().clear();
			reward(server, player, COTTAGE_2);
			helper.assertTrue(BlueprintUnlocks.state(level, null, COTTAGE_2) == BlueprintUnlocks.State.UNLOCKED,
				"a player with no village claimed the reward and Starter Cottage II isn't in the server-wide set: " + BlueprintUnlocks.state(level, null, COTTAGE_2));
			String line = "The village's library has a new blueprint: " + Blueprints.displayName(COTTAGE_2).getString();
			helper.assertTrue(texts(c.seen()).contains(line), "the player should be told '" + line + "', saw " + texts(c.seen()));
			LOGGER.info("qa unlockFor 1116b: reward with no village went into the server-wide set");
			player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0f, 0f);
			helper.succeed();
		});
	}
}

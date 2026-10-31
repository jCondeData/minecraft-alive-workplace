package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * QA (2026-10-31), from the spec: the Lost Caravan (ROADMAP 31.20) crossed with the threat settings (32.21). Through a
 * real caravan sent by the sending hall's round on a 320-block road, {@code lostCaravanChance} forced to 1 in 1:
 * <ul>
 * <li>the quiet-village cases (threat level 0, At peace, Peaceful: no caravan goes missing) are B725's, in
 * QaLostCaravanQuietBugTests on tests/lost-caravan-quiet-1031;</li>
 * <li>32.21 Peaceful: a Lost Caravan running there still
 * ends (Tobin freed and walked home, no bandit needed);</li>
 * <li>31.20 "reaches either village": Tobin brought to the receiving village joins it at Journeyman, both chronicles
 * say he settled there, +100 friendship, Wayfinder in both;</li>
 * <li>31.20 "with 4 bandits and a captain on guard": counted, and still five while the cage is shut.</li>
 * </ul>
 */
public class QaLostCaravanThreats1031GameTests implements FabricGameTest {
	static final String AREA = QaLostCaravanGameTests.AREA;
	private static final BlockPos STOREHOUSE = new BlockPos(5, 2, 2);
	private static final BlockPos STORE_CHEST = new BlockPos(5, 2, 4);

	/** Two villages 320 blocks apart (QaLostCaravanGameTests.begin), a Storehouse with 128 logs at Thornholm; settings restored after. */
	static BlockPos[] road(GameTestHelper helper) {
		BlockPos[] ab = QaLostCaravanGameTests.begin(helper);
		ServerLevel level = helper.getLevel();
		int threat = Threats.LEVEL;
		Difficulty difficulty = level.getDifficulty();
		ChunkPos far = new ChunkPos(ab[1]); // (Ashford's hall stays loaded: an unloaded hall is never At peace)
		level.setChunkForced(far.x, far.z, true);
		Leftovers.after(helper, () -> {
			level.setChunkForced(far.x, far.z, false);
			Threats.LEVEL = threat;
			level.getServer().setDifficulty(difficulty, true);
			for (BlockPos hall : ab) {
				if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
					e.setAtPeace(false, Chronicle.day(level));
				}
			}
		});
		Threats.LEVEL = 2;
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, net.minecraft.world.level.block.Blocks.CHEST);
		Container ours = helper.getBlockEntity(STORE_CHEST);
		ours.setItem(0, new ItemStack(Items.OAK_LOG, 64));
		ours.setItem(1, new ItemStack(Items.OAK_LOG, 64));
		return ab;
	}

	/** Ashford waits for 32 logs; Thornholm has a route there (after the Storehouse's point of interest is filed). */
	static Caravans.Data route(GameTestHelper helper, BlockPos a, BlockPos b) {
		Caravans.Data data = Caravans.Data.get(helper.getLevel());
		data.setWants(a, Component.literal("Thornholm"), List.of());
		data.setWants(b, Component.literal("Ashford"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		helper.assertTrue(data.toggleRoute(a, b), "setup: no route");
		return data;
	}

	/** Today's caravan from Thornholm, by its hall's round: true if it went missing (not on the road, the Lost Caravan running). */
	static boolean send(GameTestHelper helper, Caravans.Data data, BlockPos a, BlockPos b) {
		ServerLevel level = helper.getLevel();
		Arcs.stopAll(level, a);
		Arcs.stopAll(level, b);
		data.clearRoad(a);
		data.sent(a, -1);
		Caravans.round(level, a, null);
		boolean onRoad = data.onTheRoad().stream().anyMatch(s -> s.from().equals(a) && s.to().equals(b));
		ArcState s = Arcs.running(level, a);
		boolean lost = s != null && s.id.equals(LostCaravanStory.ARC.toString());
		helper.assertTrue(onRoad != lost, "the caravan is " + (onRoad ? "both on the road and lost" : "neither on the road nor lost: no caravan left? " + data.onTheRoad()));
		return lost;
	}

	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanPeacefulEnds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanPeacefulEnds")
	public void onPeacefulARunningStoryStillEnds(GameTestHelper helper) {
		BlockPos[] ab = road(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b); // (started on Normal: a save from before the switch)
		level.getServer().setDifficulty(Difficulty.PEACEFUL, true);
		helper.assertTrue(Arcs.next(level, a) && s.chapter == 1, "not in chapter 2");
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		Arcs.NEAR = 24;
		QaLostCaravanGameTests.cage(helper, s);
		s.places.stream().filter(p -> !p.key.equals(LostCaravanStory.STOCKADE)).forEach(p -> p.skipped = true);
		BlockPos door = helper.absolutePos(QaLostCaravanGameTests.DOOR);
		me.teleportTo(door.getX() + 0.5, door.getY(), door.getZ() - 2.5);
		ArcEffects.check(level); // the guards would come now; on Peaceful they're refused
		helper.assertTrue(Arcs.next(level, a) && s.chapter == 2, "not in chapter 3: " + s.chapter);
		Villager tobin = QaLostCaravanGameTests.freed(helper, s, me);
		QaLostCaravanGameTests.walkHome(helper, tobin, me);
		helper.assertTrue(Arcs.running(level, a) == null, "on Peaceful the story didn't end when Tobin came home (chapter " + s.chapter + ")");
		helper.assertTrue(tobin.isAlive() && ModAttachments.HEAD_START.getOrElse(tobin, 1) == LostCaravanStory.JOURNEYMAN, "on Peaceful Tobin didn't join at Journeyman");
		tobin.discard();
		helper.succeed();
	}

	// ---- reaching the receiving village ------------------------------------------------------------------------------

	/** The text of the hall's chronicle line {@code key}, or null. */
	private static String line(ServerLevel level, BlockPos hall, String key) {
		return ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle().stream().map(Chronicle.Entry::text)
			.filter(t -> t.getContents() instanceof TranslatableContents c && c.getKey().equals(key)).map(Component::getString).findFirst().orElse(null);
	}

	/**
	 * "This chapter is done when Tobin reaches either village" and "Tobin joins the village he reached at Journeyman":
	 * walked to Ashford (the receiving village), the story ends; he's the village's at Journeyman, both chronicles say he
	 * settled in Ashford, he's +100 friendship with the helper, and the helper is +150 and a Wayfinder in both villages.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLostCaravanReceivingVillage"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLostCaravanReceivingVillage")
	public void tobinWalkedToTheReceivingVillageJoinsIt(GameTestHelper helper) {
		BlockPos[] ab = road(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		QaLostCaravanGameTests.toTheCaptive(helper, a, s);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.SURVIVAL);
		Villager tobin = QaLostCaravanGameTests.freed(helper, s, me);
		String name = tobin.getCustomName().getString();
		int friendship = Friendship.points(tobin, me.getUUID());
		int atA = Reputation.points(level, a, me.getUUID());
		int atB = Reputation.points(level, b, me.getUUID());

		// the two of them, 13 blocks apart, by Ashford's hall (320 blocks from Thornholm's), once its forced chunk is loaded
		// for entities (as it is round a player who walked there)
		me.teleportTo(b.getX() + 3.5, b.getY(), b.getZ() + 3.5);
		when(helper, () -> level.isPositionEntityTicking(b), 150, "setup: Ashford never loaded", () -> {
			tobin.teleportTo(b.getX() + 12.5, b.getY(), b.getZ() + 12.5);
			helper.assertTrue(tobin.level() == level && tobin.isAlive(), "setup: Tobin didn't get to Ashford");
			helper.runAfterDelay(5, () -> {
				LostCaravanStory.tick(level);
				helper.assertTrue(Arcs.running(level, a) == null, "the story didn't end when Tobin reached the receiving village (chapter " + s.chapter + "; Tobin at " + tobin.blockPosition().subtract(b)
					+ " from Ashford's hall, alive " + tobin.isAlive() + ", found " + (LostCaravanStory.carter(level, s) != null) + ", leader " + LostCaravanStory.leader(s) + ")");
				helper.assertTrue(tobin.isAlive() && tobin.getTags().stream().noneMatch(t -> t.startsWith(Arcs.TAG) || t.startsWith("aliveworkplace_role_")),
					"he's still the arc's: " + tobin.getTags());
				helper.assertTrue(ModAttachments.HEAD_START.getOrElse(tobin, 1) == LostCaravanStory.JOURNEYMAN, "he doesn't start at Journeyman");
				String settled = name + ", the carter the raiders took, came home and settled in Ashford.";
				helper.assertTrue(settled.equals(line(level, a, "chronicle.aliveworkplace.lost_caravan.ending")),
					"Thornholm's chronicle: " + line(level, a, "chronicle.aliveworkplace.lost_caravan.ending") + " (expected: " + settled + ")");
				helper.assertTrue(settled.equals(line(level, b, "chronicle.aliveworkplace.lost_caravan.ending")),
					"Ashford's chronicle: " + line(level, b, "chronicle.aliveworkplace.lost_caravan.ending") + " (expected: " + settled + ")");
				int gained = Friendship.points(tobin, me.getUUID()) - friendship;
				helper.assertTrue(gained == LostCaravanStory.FRIENDSHIP, "friendship with Tobin rose by " + gained + ", not 100");
				int upA = Reputation.points(level, a, me.getUUID()) - atA;
				int upB = Reputation.points(level, b, me.getUUID()) - atB;
				helper.assertTrue(upA >= 150 && upB >= 150, "standing rose by " + upA + " at Thornholm and " + upB + " at Ashford, not 150");
				helper.assertTrue(Reputation.honours(level, a, me.getUUID()).contains("wayfinder") && Reputation.honours(level, b, me.getUUID()).contains("wayfinder"),
					"no Wayfinder in both: " + Reputation.honours(level, a, me.getUUID()) + " / " + Reputation.honours(level, b, me.getUUID()));
				AliveWorkplace.LOG.info("QA Lost Caravan threats: {} settled in Ashford, +{} friendship, +{}/+{} standing", name, gained, upA, upB);
				tobin.discard();
				helper.succeed();
			});
		});
	}

	/** Runs {@code then} on the first tick {@code ready} holds, failing with {@code why} after {@code ticks}. */
	private static void when(GameTestHelper helper, java.util.function.BooleanSupplier ready, int ticks, String why, Runnable then) {
		if (ready.getAsBoolean()) {
			then.run();
		} else {
			helper.assertTrue(ticks > 0, why);
			helper.runAfterDelay(1, () -> when(helper, ready, ticks - 1, why, then));
		}
	}

	// ---- the guard ---------------------------------------------------------------------------------------------------

	/**
	 * "Locked in the cage, with 4 bandits and a captain on guard": once a player comes to the stockade, the arc's mobs are
	 * one Captain and four Bandits (no more, no fewer, by kind and by name), and they stay five through more checks while
	 * the cage is shut and Tobin waits inside.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLostCaravanGuard"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLostCaravanGuard")
	public void theStockadeIsGuardedByFourBanditsAndACaptain(GameTestHelper helper) {
		BlockPos[] ab = road(helper);
		BlockPos a = ab[0];
		BlockPos b = ab[1];
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(Difficulty.NORMAL, true);
		ArcState s = QaLostCaravanGameTests.lose(helper, a, b);
		helper.assertTrue(Arcs.next(level, a) && s.chapter == 1, "not in chapter 2");
		Arcs.NEAR = 24;
		QaLostCaravanGameTests.cage(helper, s);
		s.places.stream().filter(p -> !p.key.equals(LostCaravanStory.STOCKADE)).forEach(p -> p.skipped = true);
		ServerPlayer me = helper.makeMockServerPlayerInLevel();
		me.setGameMode(GameType.CREATIVE); // (so the guard doesn't kill the test player)
		BlockPos door = helper.absolutePos(QaLostCaravanGameTests.DOOR);
		me.teleportTo(door.getX() + 0.5, door.getY(), door.getZ() - 6.5);
		ArcEffects.check(level);
		guard(helper, s, "the stockade's guard");
		helper.assertTrue(Arcs.next(level, a) && s.chapter == 2, "not in chapter 3: " + s.chapter);
		LostCaravanStory.tick(level); // Tobin is caged
		Villager tobin = LostCaravanStory.carter(level, s);
		helper.assertTrue(tobin != null && LostCaravanStory.leader(s) == null, "Tobin isn't caged and waiting");
		for (int i = 0; i < 3; i++) {
			ArcEffects.check(level);
			LostCaravanStory.tick(level);
		}
		guard(helper, s, "with the cage shut");
		helper.succeed();
	}

	private static void guard(GameTestHelper helper, ArcState s, String when) {
		ServerLevel level = helper.getLevel();
		int captains = 0;
		int bandits = 0;
		int vindicators = 0;
		int pillagers = 0;
		for (ArcState.ArcMob m : s.mobs) {
			Entity e = m.uuid == null ? null : level.getEntity(m.uuid);
			if (!(e instanceof Mob mob) || !mob.isAlive()) {
				continue;
			}
			String name = mob.getCustomName() == null ? "" : mob.getCustomName().getString();
			if (name.equals("Captain")) {
				captains++;
			} else if (name.equals("Bandit")) {
				bandits++;
			}
			if (mob instanceof Vindicator) {
				vindicators++;
			} else if (mob instanceof Pillager) {
				pillagers++;
			}
		}
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48);
		int about = level.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && m.getTags().contains(Arcs.MOB_TAG)).size();
		helper.assertTrue(captains == 1 && bandits == 4, when + ": " + captains + " captains and " + bandits + " bandits, not 1 and 4 (" + s.mobs.size() + " arc mobs)");
		helper.assertTrue(about == 5, when + ": " + about + " story mobs about, not 5");
		AliveWorkplace.LOG.info("QA Lost Caravan threats: {}: {} captain, {} bandits ({} vindicators, {} pillagers)", when, captains, bandits, vindicators, pillagers);
	}
}

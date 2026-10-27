package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * QA for Friendship (ROADMAP 31.5), written from the spec: the boundaries the feature's own tests don't reach. The
 * hit's once-a-minute rule at 1199 and 1200 ticks, points held to 0 and 1000, a daily favour on the last tick of a day
 * and the first of the next, and the look at 5.5 blocks against 7 and at an unnamed villager.
 */
public class QaFriendshipGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	private static void village(GameTestHelper helper, java.util.function.BiConsumer<Villager, ServerPlayer> then) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		ServerLevel level = helper.getLevel();
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.setDayTime(dayTime);
			CivicEffects.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager dara = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		dara.setNoAi(true);
		dara.setCustomName(Component.literal("Dara"));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(1, 2, 3)));
		player.moveTo(pos.x, pos.y, pos.z, 0, 0);
		helper.runAfterDelay(2, () -> {
			CivicEffects.forget();
			then.accept(dara, player);
		});
	}

	private static void setHitTick(Villager v, UUID player, long tick) {
		Friendship.Data data = Friendship.of(v);
		ModAttachments.FRIENDSHIP.set(v, data.with(player, data.bond(player).withHit(tick)));
	}

	private static void trade(Villager villager, ServerPlayer player) {
		villager.setTradingPlayer(player);
		villager.notifyTrade(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD), 12, 1, 0.05f));
		villager.setTradingPlayer(null);
	}

	/** A hit 1199 ticks after the last one that cost points costs nothing; at 1200 it costs 50 again. */
	//$ gametest_ticks_batch AREA '1300' '"qaFriendHitMinute"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaFriendHitMinute")
	public void aHitCostsAgainAtExactlyOneMinute(GameTestHelper helper) {
		village(helper, (dara, player) -> {
			// (the last hit's tick must be a real one: wait until the world is over a minute old)
			long wait = Math.max(1, Friendship.HIT_EVERY + 1 - helper.getLevel().getGameTime());
			helper.runAfterDelay(wait, () -> {
			UUID me = player.getUUID();
			long now = helper.getLevel().getGameTime();
			Friendship.add(dara, player, 300);
			setHitTick(dara, me, now - (Friendship.HIT_EVERY - 1));
			int early = Friendship.hit(dara, player);
			helper.assertTrue(early == 0 && Friendship.points(dara, me) == 300,
				"a hit 1199 ticks after the last cost " + early + ", points " + Friendship.points(dara, me));
			setHitTick(dara, me, now - Friendship.HIT_EVERY);
			int onTime = Friendship.hit(dara, player);
			helper.assertTrue(onTime == -Friendship.HIT_COST && Friendship.points(dara, me) == 250,
				"a hit 1200 ticks after the last: " + onTime + ", points " + Friendship.points(dara, me));
			int again = Friendship.hit(dara, player);
			helper.assertTrue(again == 0 && Friendship.points(dara, me) == 250, "a second hit the same tick cost " + again);
			helper.succeed();
	});
		});
	}

	/** Points never go above 1000 or below 0: ten full hearts at the top, a hit at 30 points leaves 0, not less. */
	//$ gametest_ticks_batch AREA '60' '"qaFriendClamp"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFriendClamp")
	public void pointsStayBetweenZeroAndAThousand(GameTestHelper helper) {
		village(helper, (dara, player) -> {
			UUID me = player.getUUID();
			int up = Friendship.add(dara, player, 1500);
			helper.assertTrue(up == 1000 && Friendship.points(dara, me) == 1000, "1500 points added: " + up + ", now " + Friendship.points(dara, me));
			helper.assertTrue(Friendship.favour(dara, player, Friendship.Favour.REQUEST) == 0 && Friendship.points(dara, me) == 1000,
				"a favour at 1000 went over: " + Friendship.points(dara, me));
			String row = Friendship.heartRow(Friendship.points(dara, me)).getString();
			helper.assertTrue(row.equals("♥".repeat(10)), "the hearts at 1000: " + row);
			Friendship.add(dara, player, -970);
			helper.assertTrue(Friendship.points(dara, me) == 30, "after taking 970: " + Friendship.points(dara, me));
			int hit = Friendship.hit(dara, player);
			helper.assertTrue(hit == -30 && Friendship.points(dara, me) == 0, "a hit at 30 points: " + hit + ", now " + Friendship.points(dara, me));
			int down = Friendship.add(dara, player, -500);
			helper.assertTrue(down == 0 && Friendship.points(dara, me) == 0, "500 taken at 0: " + down + ", now " + Friendship.points(dara, me));
			String empty = Friendship.heartRow(0).getString();
			helper.assertTrue(empty.equals("♡".repeat(10)), "the hearts at 0: " + empty);
			helper.succeed();
		});
	}

	/** A trade on the last tick of a day counts; another that day doesn't; the first tick of the next day counts again. */
	//$ gametest_ticks_batch AREA '60' '"qaFriendDayTurn"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFriendDayTurn")
	public void aDailyFavourCountsAgainOnTheNextDaysFirstTick(GameTestHelper helper) {
		village(helper, (dara, player) -> {
			ServerLevel level = helper.getLevel();
			UUID me = player.getUUID();
			long dayStart = (level.getDayTime() / VillageNeeds.DAY + 5) * VillageNeeds.DAY;
			level.setDayTime(dayStart - 1);
			trade(dara, player);
			trade(dara, player);
			helper.assertTrue(Friendship.points(dara, me) == 5, "two trades on the day's last tick: " + Friendship.points(dara, me));
			level.setDayTime(dayStart);
			trade(dara, player);
			helper.assertTrue(Friendship.points(dara, me) == 10, "a trade on the next day's first tick: " + Friendship.points(dara, me));
			level.setDayTime(dayStart + VillageNeeds.DAY - 1);
			trade(dara, player);
			helper.assertTrue(Friendship.points(dara, me) == 10, "a second trade later that day: " + Friendship.points(dara, me));
			// Each daily favour is kept on its own: a festival the same day still counts once.
			helper.assertTrue(Friendship.favour(dara, player, Friendship.Favour.FESTIVAL) == 10
					&& Friendship.favour(dara, player, Friendship.Favour.FESTIVAL) == 0 && Friendship.points(dara, me) == 20,
				"the festival beside the trade: " + Friendship.points(dara, me));
			// Defending her has no daily limit.
			int d1 = Friendship.favour(dara, player, Friendship.Favour.DEFENDED);
			int d2 = Friendship.favour(dara, player, Friendship.Favour.DEFENDED);
			int d3 = Friendship.favour(dara, player, Friendship.Favour.DEFENDED);
			helper.assertTrue(d1 == 15 && d2 == 15 && d3 == 15 && Friendship.points(dara, me) == 65,
				"defending her three times in a day: " + Friendship.points(dara, me));
			helper.succeed();
		});
	}

	/** The action bar shows her hearts when she's 5.5 blocks off and nothing at 7; an unnamed villager close by shows nothing. */
	//$ gametest_ticks_batch AREA '60' '"qaFriendLookRange"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFriendLookRange")
	public void theHeartsShowWithinSixBlocksOnlyAndOnlyForTheNamed(GameTestHelper helper) {
		village(helper, (dara, player) -> {
			Friendship.add(dara, player, 300);
			Vec3 eye = player.getEyePosition();
			// facing +x (yaw -90), level
			player.moveTo(player.getX(), player.getY(), player.getZ(), -90, 0);
			player.setYHeadRot(-90);
			double feetY = eye.y - 1.2;
			dara.moveTo(eye.x + 5.5, feetY, eye.z, 0, 0);
			Component near = Friendship.lookLine(player);
			helper.assertTrue(near != null && near.getString().contains("Dara") && near.getString().contains("♥♥♥♡♡♡♡♡♡♡"),
				"looking at Dara 5.5 blocks off: " + (near == null ? "nothing" : near.getString()));
			dara.moveTo(eye.x + 7, feetY, eye.z, 0, 0);
			Component far = Friendship.lookLine(player);
			helper.assertTrue(far == null, "looking at Dara 7 blocks off: " + (far == null ? "" : far.getString()));
			dara.moveTo(eye.x + 3, feetY, eye.z, 0, 0);
			dara.setCustomName(null);
			Component unnamed = Friendship.lookLine(player);
			helper.assertTrue(unnamed == null, "an unnamed villager 3 blocks off showed " + (unnamed == null ? "" : unnamed.getString()));
			helper.succeed();
		});
	}

	/** A monster that hurt her killed exactly 10 seconds (200 ticks) later still counts; one killed at 201 ticks doesn't. */
	//$ gametest_ticks_batch AREA '300' '"qaFriendDefendWindow"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaFriendDefendWindow")
	public void aMonsterKilledOnTheTenthSecondCountsAndNotAfter(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long[] hurtAt = {-1};
		Object[] scene = new Object[3];
		// (registered before the test's first tick: a tick runnable added from inside one breaks the test's tick loop)
		helper.onEachTick(() -> {
			if (hurtAt[0] < 0) {
				return;
			}
			Villager dara = (Villager) scene[0];
			ServerPlayer player = (ServerPlayer) scene[1];
			Zombie[] z = (Zombie[]) scene[2];
			UUID me = player.getUUID();
			long elapsed = level.getGameTime() - hurtAt[0];
			if (elapsed == Friendship.DEFEND_WINDOW && z[0].isAlive()) {
				z[0].setInvulnerable(false);
				z[0].hurt(level.damageSources().playerAttack(player), 1000f);
				helper.assertTrue(!z[0].isAlive() && Friendship.points(dara, me) == 15,
					"a zombie that hurt her killed 200 ticks later: " + Friendship.points(dara, me));
			} else if (elapsed == Friendship.DEFEND_WINDOW + 1 && z[1].isAlive()) {
				z[1].setInvulnerable(false);
				z[1].hurt(level.damageSources().playerAttack(player), 1000f);
				helper.assertTrue(!z[1].isAlive() && Friendship.points(dara, me) == 15,
					"a zombie that hurt her killed " + elapsed + " ticks later counted: " + Friendship.points(dara, me));
				helper.succeed();
			}
		});
		village(helper, (dara, player) -> {
			Zombie onTime = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 7));
			Zombie late = helper.spawn(EntityType.ZOMBIE, new BlockPos(7, 2, 7));
			for (Zombie z : new Zombie[] {onTime, late}) {
				z.setNoAi(true);
				z.setInvulnerable(true);
				dara.invulnerableTime = 0;
				dara.hurt(level.damageSources().mobAttack(z), 0.5f);
			}
			dara.heal(20f);
			scene[0] = dara;
			scene[1] = player;
			scene[2] = new Zombie[] {onTime, late};
			hurtAt[0] = level.getGameTime();
		});
	}
}

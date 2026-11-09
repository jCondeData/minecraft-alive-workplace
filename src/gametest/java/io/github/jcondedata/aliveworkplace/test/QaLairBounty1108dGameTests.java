package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * QA, lane D (2026-11-08), from 31.13's spec: "a standing lair's captain is posted as a bounty too (his name and his
 * lair's place; paid to his killer when he falls)", and "taking a bounty gives a Wanted Poster and a map". Last
 * night's-style mutation of Bounties (8 mutants, the bounty classes) left these alive: lairBroken's "&&" as "||" (a
 * lair falling paid every lair bounty), and give()'s "!" dropped (a poster and map for a player with a full bag
 * vanished). No test posted a lair's bounty or killed its captain before.
 */
public class QaLairBounty1108dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);

	/** A Town-ranked hall (radius 20) with bounties on but none posted by chance; everything put back afterwards. */
	private static BlockPos town(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		VillageRaids.forget();
		Bounties.forget(level);
		int was = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean lairs = Lairs.ENABLED;
		boolean bounties = Bounties.ENABLED;
		boolean auto = Bounties.AUTO;
		int threat = Threats.LEVEL;
		VillageHalls.RADIUS = 20;
		Lairs.ENABLED = true;
		Bounties.ENABLED = true;
		Bounties.AUTO = false;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(64), m -> !(m instanceof Villager)
				&& (m.getTags().contains(Lairs.TAG) || m.getTags().contains(VillageRaids.TAG) || m.getTags().contains(Lairs.CAPTAIN_TAG))).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(16)).forEach(ItemEntity::discard);
			ThreatData.get(level).end(hall);
			Lairs.forget(level);
			VillageRaids.forget();
			BanditCamps.forget(level);
			Bounties.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = was;
			Lairs.ENABLED = lairs;
			Bounties.ENABLED = bounties;
			Bounties.AUTO = auto;
			Threats.LEVEL = threat;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.TOWN);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		level.setDayTime(44 * 24000L + 2000);
		return hall;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Leftovers.after(helper, () -> {
			player.getInventory().clearContent();
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
		return player;
	}

	/** The bandits' lair by the town, and the bounty the hall's round posts on its captain. */
	private record Posted(Lairs.Lair lair, Mob captain, Bounties.Bounty bounty) {
	}

	private static Posted lairAndBounty(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), Threats.BANDITS);
		helper.assertTrue(lair != null, "setup: no bandits' lair by the town");
		Mob captain = level.getEntity(lair.captain()) instanceof Mob m ? m : null;
		helper.assertTrue(captain != null && captain.isAlive(), "setup: the lair has no captain");
		Bounties.round(level, hall);
		List<Bounties.Bounty> open = Bounties.open(level, hall);
		Bounties.Bounty b = open.stream().filter(x -> x.lair).findFirst().orElse(null);
		helper.assertTrue(b != null && lair.captain().equals(b.captain), "the round posted no bounty on the lair's captain: " + open.size() + " open");
		helper.assertTrue(b.reward == Math.round(Bounties.REWARD * VillageRanks.questRewardFactor(VillageRanks.Rank.TOWN)),
			"a Town's lair bounty pays 12 emeralds times 1.5, not " + b.reward);
		Bounties.round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).stream().filter(x -> x.lair).count() == 1, "a second round posted his bounty twice");
		return new Posted(lair, captain, b);
	}

	/**
	 * The lair's captain killed by a player: that player is paid the bounty (18 emeralds in a Town) once and it comes
	 * down; another lair bounty open at the same hall, on a captain still standing, stays up and pays nobody.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLairBounty1108dPaid"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLairBounty1108dPaid")
	public void theLairsCaptainPaysHisKillerAndOnlyHisOwnBountyComesDown(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Posted p = lairAndBounty(helper, hall);
		ArcState.ArcPlace place = new ArcState.ArcPlace("hideout", hall.offset(0, 0, 150), List.of(Bounties.HIDEOUT.toString()), 0);
		Bounties.Bounty other = new Bounties.Bounty(UUID.randomUUID(), hall, -1, -1, AliveWorkplace.id("lair"), place, p.bounty().posted, 18, true);
		other.captain = UUID.randomUUID();
		Bounties.Data.get(level).bounties.put(other.id, other);
		ServerPlayer killer = player(helper, p.captain().blockPosition().north(2));
		ServerPlayer bystander = player(helper, hall.above().east(2));
		for (int i = 0; i < 20 && p.captain().isAlive(); i++) {
			p.captain().invulnerableTime = 0;
			p.captain().hurt(level.damageSources().playerAttack(killer), 1000f);
		}
		helper.assertTrue(!p.captain().isAlive(), "setup: the captain didn't fall");
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair still stands with its captain dead");
		int paid = killer.getInventory().countItem(Items.EMERALD);
		helper.assertTrue(paid == p.bounty().reward, "his killer was paid " + paid + " emeralds, not the bounty's " + p.bounty().reward);
		helper.assertTrue(bystander.getInventory().countItem(Items.EMERALD) == 0, "a player who didn't kill him was paid");
		List<Bounties.Bounty> open = Bounties.open(level, hall);
		helper.assertTrue(open.stream().noneMatch(x -> x.id.equals(p.bounty().id)) && Bounties.Data.get(level).claimed.contains(p.bounty().id),
			"his bounty is still up after he fell");
		helper.assertTrue(open.stream().anyMatch(x -> x.id.equals(other.id)) && !Bounties.Data.get(level).claimed.contains(other.id),
			"another lair's bounty came down when this lair fell (open: " + open.size() + ")");
		helper.assertTrue(killer.getInventory().countItem(Items.EMERALD) == paid, "paid more than once");
		helper.succeed();
	}

	/**
	 * Taking a bounty with a full bag: the Wanted Poster and the hideout map aren't lost; they land at the player's feet
	 * as items to pick up.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLairBounty1108dFullBag"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLairBounty1108dFullBag")
	public void aPlayerWithAFullBagGetsThePosterAndMapAtTheirFeet(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = Bounties.post(level, hall, AliveWorkplace.id("fleetfoot"), 0, 0, hall.offset(160, 0, 0));
		helper.assertTrue(b != null, "setup: no bounty posted");
		ServerPlayer me = player(helper, hall.above().south(3));
		for (int i = 0; i < me.getInventory().items.size(); i++) {
			me.getInventory().items.set(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		me.getInventory().offhand.set(0, new ItemStack(Items.COBBLESTONE, 64));
		helper.assertTrue(Bounties.take(me, level, b), "taking the bounty failed");
		AABB feet = me.getBoundingBox().inflate(4);
		List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, feet);
		helper.assertTrue(dropped.stream().anyMatch(e -> e.getItem().is(ModItems.WANTED_POSTER)),
			"the Wanted Poster vanished: a full bag and nothing dropped (" + dropped.size() + " items by the player)");
		helper.assertTrue(dropped.stream().anyMatch(e -> e.getItem().is(Items.FILLED_MAP)), "the hideout map vanished: a full bag and nothing dropped");
		helper.succeed();
	}
}

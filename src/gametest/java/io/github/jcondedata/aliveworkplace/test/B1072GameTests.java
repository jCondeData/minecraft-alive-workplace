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
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * B1072: a mob that converts in vanilla gets a new UUID, and lairs and bounties keep their captain by his UUID. The desert
 * raiders' Pharaoh, a husk, drowns into a zombie and then a drowned: the drowned is still the lair's captain, killing
 * him breaks the lair up, and the bounty on him is paid to his killer.
 */
public class B1072GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");

	/** A Town-ranked hall with lairs and bounties on (none posted by chance); everything put back afterwards. */
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
		level.setDayTime(49 * 24000L + 1000);
		return hall;
	}

	/** The desert raiders' tomb by the town, and its Pharaoh drowned twice over: a husk, a zombie, then a drowned. */
	private static Drowned drownedPharaoh(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
		helper.assertTrue(lair != null && lair.culture().equals(DESERT), "setup: no desert raiders' lair by the town: " + lair);
		Husk pharaoh = level.getEntity(lair.captain()) instanceof Husk h ? h : null;
		helper.assertTrue(pharaoh != null && pharaoh.isAlive(), "setup: the lair's captain is no husk: " + level.getEntity(lair.captain()));
		Zombie zombie = pharaoh.convertTo(EntityType.ZOMBIE, true);
		helper.assertTrue(zombie != null && !pharaoh.isAlive(), "setup: the husk didn't turn into a zombie");
		helper.assertTrue(Lairs.near(level, hall).map(l -> l.captain().equals(zombie.getUUID())).orElse(false),
			"the zombie the husk turned into isn't the lair's captain: " + Lairs.near(level, hall).map(Lairs.Lair::captain).orElse(null));
		Drowned drowned = zombie.convertTo(EntityType.DROWNED, true);
		helper.assertTrue(drowned != null && !zombie.isAlive(), "setup: the zombie didn't turn into a drowned");
		helper.assertTrue(drowned.getTags().contains(Lairs.CAPTAIN_TAG), "the drowned lost the captain's tag: " + drowned.getTags());
		Lairs.Lair now = Lairs.near(level, hall).orElse(null);
		helper.assertTrue(now != null && now.captain().equals(drowned.getUUID()), "the drowned isn't the lair's captain: " + now);
		helper.assertTrue(now.pos().equals(lair.pos()) && now.strength() == lair.strength() && now.name() == lair.name() && now.day() == lair.day(),
			"the lair changed more than its captain: " + lair + " -> " + now);
		return drowned;
	}

	/** The Pharaoh drowned into a drowned and then killed: his lair is broken up, as if he had fallen a husk. */
	//$ gametest_ticks_batch AREA '100' '"b1072Lair"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b1072Lair")
	public void aLairCaptainWhoDrownsStillEndsTheLairWhenKilled(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Drowned drowned = drownedPharaoh(helper, hall);
		// The hall's round before he is killed: the lair still has its captain, so it stands (it isn't broken up as leaderless).
		Lairs.round(level, hall, false);
		helper.assertTrue(Lairs.near(level, hall).isPresent(), "the hall's round broke the lair up while its drowned captain stood");
		drowned.kill();
		helper.assertTrue(!drowned.isAlive(), "setup: the drowned didn't die");
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair still stands with its drowned captain dead");
		helper.assertTrue(Lairs.resting(level, hall) > 0, "the lair wasn't broken up (the village isn't resting from it)");
		helper.succeed();
	}

	/** The bounty on the Pharaoh follows him into the drowned: a player who kills the drowned is paid it. */
	//$ gametest_ticks_batch AREA '100' '"b1072Bounty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b1072Bounty")
	public void aBountyCaptainWhoDrownsStillPaysHisKiller(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
		helper.assertTrue(lair != null, "setup: no desert raiders' lair by the town");
		Bounties.round(level, hall);
		Bounties.Bounty b = Bounties.open(level, hall).stream().filter(x -> x.lair).findFirst().orElse(null);
		helper.assertTrue(b != null && lair.captain().equals(b.captain), "setup: no bounty posted on the lair's captain");
		UUID husk = lair.captain();
		Mob pharaoh = (Mob) level.getEntity(husk);
		Drowned drowned = pharaoh.convertTo(EntityType.DROWNED, true);
		helper.assertTrue(drowned != null, "setup: the husk didn't turn into a drowned");
		helper.assertTrue(drowned.getUUID().equals(b.captain), "the bounty still names the husk, not the drowned he became");
		Bounties.round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).stream().filter(x -> x.lair).count() == 1, "the round posted the drowned captain's bounty a second time");
		ServerPlayer killer = helper.makeMockServerPlayerInLevel();
		killer.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> {
			killer.getInventory().clearContent();
			level.getServer().getPlayerList().remove(killer);
		});
		for (int i = 0; i < 20 && drowned.isAlive(); i++) {
			drowned.invulnerableTime = 0;
			drowned.hurt(level.damageSources().playerAttack(killer), 1000f);
		}
		helper.assertTrue(!drowned.isAlive(), "setup: the drowned didn't fall");
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the lair still stands with its drowned captain dead");
		int paid = killer.getInventory().countItem(Items.EMERALD);
		helper.assertTrue(paid == b.reward, "his killer was paid " + paid + " emeralds, not the bounty's " + b.reward);
		helper.assertTrue(Bounties.Data.get(level).claimed.contains(b.id), "his bounty wasn't claimed");
		helper.succeed();
	}

	/** A mob nobody tracks converting leaves the lairs and bounties as they were. */
	//$ gametest_ticks_batch AREA '100' '"b1072Others"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b1072Others")
	public void aMobNobodyTracksConvertingChangesNoLair(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
		helper.assertTrue(lair != null, "setup: no desert raiders' lair by the town");
		Zombie stranger = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
		Drowned drowned = stranger.convertTo(EntityType.DROWNED, true);
		helper.assertTrue(drowned != null, "setup: the zombie didn't turn into a drowned");
		helper.assertTrue(Lairs.near(level, hall).map(l -> l.captain().equals(lair.captain())).orElse(false),
			"a stranger's conversion changed the lair's captain: " + Lairs.near(level, hall));
		drowned.discard();
		helper.succeed();
	}
}

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
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane-e-1109-0115, saves) for B1072: a lair captain who turned in vanilla (the desert raiders' Pharaoh, a husk,
 * drowning into a zombie) takes the old one's place in the lair and the bounty on him. B1072's own tests kill him in the
 * same tick; these put a restart in between: the lairs' and bounties' saved data written and read back, and the zombie
 * himself saved and loaded as his chunk would be. He is still the captain afterwards, killing him breaks the lair up and
 * pays his bounty once, and the hall's rounds after the load neither break the lair up early nor post him again.
 */
public class QaConvertedCaptainSaves1109eGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");

	/** The husk Pharaoh drowned into a zombie, then a restart: the lair's captain is the zombie, and his death ends it. */
	//$ gametest_ticks_batch AREA '100' '"qaCaptainSaves1109eLair"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCaptainSaves1109eLair")
	public void aDrownedCaptainStillEndsTheLairAfterARestart(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
		helper.assertTrue(lair != null, "setup: no desert raiders' lair by the town");
		Husk pharaoh = level.getEntity(lair.captain()) instanceof Husk h ? h : null;
		helper.assertTrue(pharaoh != null, "setup: the lair's captain is no husk");
		Lairs.Data.get(level).setDirty(false); // as just after an autosave: the swap must mark the lairs to be written
		Zombie zombie = pharaoh.convertTo(EntityType.ZOMBIE, true);
		helper.assertTrue(zombie != null, "setup: the husk didn't turn");
		helper.assertTrue(Lairs.Data.get(level).isDirty(), "the new captain was noted but the lairs aren't marked to be saved: the next save loses him");
		restart(level);
		Zombie loaded = reload(level, zombie);
		helper.assertTrue(loaded.getUUID().equals(zombie.getUUID()) && loaded.getTags().contains(Lairs.CAPTAIN_TAG),
			"the zombie reloaded lost the captain's tag: " + loaded.getTags());
		Lairs.Lair now = Lairs.near(level, hall).orElse(null);
		helper.assertTrue(now != null && now.captain().equals(loaded.getUUID()), "after the restart the lair's captain isn't the zombie: " + now);
		Lairs.round(level, hall, false);
		helper.assertTrue(Lairs.near(level, hall).isPresent(), "the hall's round after the restart broke the lair up while its captain stood");
		loaded.kill();
		helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the reloaded zombie captain died and the lair still stands");
		helper.assertTrue(Lairs.resting(level, hall) > 0, "the lair wasn't broken up (the village isn't resting from it)");
		helper.succeed();
	}

	/** The bounty on the Pharaoh follows him into the zombie, through a restart: his killer is paid once, nobody posts him again. */
	//$ gametest_ticks_batch AREA '100' '"qaCaptainSaves1109eBounty"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCaptainSaves1109eBounty")
	public void aDrownedCaptainsBountyIsPaidOnceAfterARestart(GameTestHelper helper) {
		BlockPos hall = town(helper);
		ServerLevel level = helper.getLevel();
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), DESERT);
		helper.assertTrue(lair != null, "setup: no desert raiders' lair by the town");
		Bounties.round(level, hall);
		Bounties.Bounty b = Bounties.open(level, hall).stream().filter(x -> x.lair).findFirst().orElse(null);
		helper.assertTrue(b != null && lair.captain().equals(b.captain), "setup: no bounty posted on the lair's captain");
		Mob pharaoh = (Mob) level.getEntity(lair.captain());
		Bounties.Data.get(level).setDirty(false); // as just after an autosave
		Zombie zombie = pharaoh.convertTo(EntityType.ZOMBIE, true);
		helper.assertTrue(zombie != null, "setup: the husk didn't turn");
		helper.assertTrue(Bounties.Data.get(level).isDirty(), "the bounty's new captain was noted but the bounties aren't marked to be saved");
		restart(level);
		Zombie loaded = reload(level, zombie);
		Bounties.Bounty after = Bounties.open(level, hall).stream().filter(x -> x.lair).findFirst().orElse(null);
		helper.assertTrue(after != null && after.id.equals(b.id) && loaded.getUUID().equals(after.captain),
			"after the restart the bounty doesn't name the zombie: " + (after == null ? "no bounty" : after.captain + " vs " + loaded.getUUID()));
		Bounties.round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).stream().filter(x -> x.lair).count() == 1, "the round after the restart posted the captain a second time");
		ServerPlayer killer = helper.makeMockServerPlayerInLevel();
		killer.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> {
			killer.getInventory().clearContent();
			level.getServer().getPlayerList().remove(killer);
		});
		for (int i = 0; i < 20 && loaded.isAlive(); i++) {
			loaded.invulnerableTime = 0;
			loaded.hurt(level.damageSources().playerAttack(killer), 1000f);
		}
		helper.assertTrue(!loaded.isAlive(), "setup: the zombie didn't fall");
		int paid = killer.getInventory().countItem(Items.EMERALD);
		helper.assertTrue(paid == after.reward, "his killer was paid " + paid + " emeralds, not the bounty's " + after.reward);
		helper.assertTrue(Bounties.Data.get(level).claimed.contains(b.id) && Bounties.open(level, hall).stream().noneMatch(x -> x.lair),
			"his bounty wasn't claimed and taken down");
		helper.succeed();
	}

	// ---- helpers ---------------------------------------------------------------------------------------------------

	/** The lairs' and bounties' saved data written and read back, as a restart does. */
	private static void restart(ServerLevel level) {
		Lairs.Data.get(level).roundTrip(level.registryAccess());
		CompoundTag bounties = Bounties.Data.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(Bounties.Data.NAME, Bounties.Data.load(bounties, level.registryAccess()));
	}

	/** {@code zombie} saved and loaded again as his chunk would be: same UUID, tags and all. */
	private static Zombie reload(ServerLevel level, Zombie zombie) {
		CompoundTag tag = new CompoundTag();
		zombie.saveWithoutId(tag);
		zombie.discard();
		Zombie copy = EntityType.ZOMBIE.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no zombie made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		return copy;
	}

	/** A Town-ranked hall with lairs and bounties on (none posted by chance); everything put back afterwards (as B1072GameTests). */
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
}

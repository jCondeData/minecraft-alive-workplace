package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Risings;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-d-1123-1009): our foes against a mob blocklist (Cobbleverse's MobsBeGone refuses pillagers, vindicators,
 * husks, ravagers and nearly every vanilla mob at {@code ServerLevel.addEntity}), from the spec of B208 and
 * docs/wiki/lairs.md: "every foe (a lair's captain and band, a raid's raiders and extras, the Bandit King and his
 * fort's men, a bounty's band, the risen) goes in through {@code HostileSpawns.add}, which checks the result ... with
 * the switch on, the mob is put in through the entity manager directly; off, or still refused, it's left out and
 * logged" (config-switches.md, {@code forceThreatSpawns}). The existing refusal tests cover camps, raids, night attacks,
 * war camps, tombs, outposts, covens, the Warlord, arcs and sentries; these take the foes they leave out: a bounty
 * captain with his mount and band, the Bandit King and the bandits his horn calls, and the Pharaoh's risen dead.
 */
public class QaSpawnRefusals1123dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos CAMP = new BlockPos(18, 2, 18);

	/** The world refusing every hostile mob as MobsBeGone does, with {@code forceThreatSpawns} as given; all put back at the end. */
	private static void refuseHostiles(GameTestHelper helper, boolean force) {
		ServerLevel level = helper.getLevel();
		HostileSpawns.forget();
		HostileSpawns.FORCE = force;
		HostileSpawns.REFUSED_FOR_TESTS = e -> e instanceof Enemy;
		Leftovers.after(helper, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE = true;
			HostileSpawns.forget();
		});
		// The refusal is real: a pillager added the usual way is refused and gone.
		Pillager plain = EntityType.PILLAGER.create(level);
		plain.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(2, 2, 2)));
		helper.assertTrue(!level.addFreshEntity(plain) && plain.isRemoved(), "setup: the world didn't refuse a pillager as MobsBeGone does");
	}

	private static boolean in(ServerLevel level, Entity e) {
		return e != null && e.isAlive() && !e.isRemoved() && level.getEntity(e.getUUID()) == e;
	}

	private static List<Mob> tagged(GameTestHelper helper, String tag) {
		return helper.getLevel().getEntitiesOfClass(Mob.class, helper.getBounds().inflate(16), m -> m.isAlive() && m.getTags().contains(tag));
	}

	// ---- a bounty's captain and band ---------------------------------------------------------------------------------

	/** A Village-rank hall with its quests quiet and the bounties forgotten; the settings put back at the end. */
	private static BlockPos village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean enabled = Bounties.ENABLED;
		int threat = Threats.LEVEL;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		Bounties.forget(level);
		AABB box = helper.getBounds().inflate(32);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box, m -> m.getTags().contains(Bounties.TAG) || m.getTags().contains(Bounties.CAPTAIN_TAG)).forEach(Mob::discard);
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Bounties.ENABLED = enabled;
			Threats.LEVEL = threat;
			Bounties.forget(level);
			level.setDayTime(time);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		return helper.absolutePos(HALL);
	}

	private static Bounties.Bounty bounty(GameTestHelper helper, BlockPos hall, String quirk) {
		ResourceLocation id = AliveWorkplace.id(quirk);
		helper.assertTrue(Bounties.quirks().containsKey(id), "setup: the quirk file didn't load: " + Bounties.quirks().keySet());
		return Bounties.post(helper.getLevel(), hall, id, 0, 0, hall.offset(160, 0, 0));
	}

	/**
	 * Spec (lairs.md, forceThreatSpawns): a bounty's band goes in through HostileSpawns and, refused with the switch on, is
	 * placed directly. The Beast Rider (a vindicator on a ravager) and his band, all refused by the blocklist: he stands
	 * by his fire, named, riding his ravager, every one of his band in the world and persistent, still there 100 ticks
	 * later, and the log names the bounty hideout and forceThreatSpawns.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaRefusedBounty1123d"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaRefusedBounty1123d")
	public void aRefusedBountyCaptainHisMountAndBandAllStandWhenForced(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = bounty(helper, hall, "beast_rider");
		refuseHostiles(helper, true);
		helper.assertTrue(Bounties.raise(level, b, helper.absolutePos(CAMP), helper.absolutePos(CAMP.north(4))),
			"the Beast Rider didn't stand though forceThreatSpawns is on; last warning: " + HostileSpawns.lastWarning());
		Entity captain = level.getEntity(b.captain);
		helper.assertTrue(captain instanceof Mob m && in(level, m) && m.isPersistenceRequired() && m.getTags().contains(Bounties.CAPTAIN_TAG),
			"the captain isn't in the world, persistent and tagged: " + captain);
		helper.assertTrue(captain.getCustomName() != null && !captain.getCustomName().getString().isBlank(), "the captain has no name: " + captain.getCustomName());
		helper.assertTrue(captain.getVehicle() instanceof Ravager r && in(level, r), "the Beast Rider isn't riding a ravager in the world: " + captain.getVehicle());
		helper.assertTrue(b.band.size() >= 3, "the band (his ravager and 2 or 3 men) has " + b.band.size());
		List<UUID> band = List.copyOf(b.band);
		for (UUID id : band) {
			Entity man = level.getEntity(id);
			helper.assertTrue(man instanceof Mob m && in(level, m) && m.isPersistenceRequired(), "a band member isn't in the world: " + id + " -> " + man);
		}
		String warning = HostileSpawns.lastWarning();
		helper.assertTrue(warning.contains("bounty hideout") && warning.contains("forceThreatSpawns"), "no clear log line: " + warning);
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(in(level, level.getEntity(b.captain)), "the captain is gone after 100 ticks");
			for (UUID id : band) {
				helper.assertTrue(in(level, level.getEntity(id)), "a band member is gone after 100 ticks: " + id);
			}
			helper.succeed();
		});
	}

	/**
	 * Spec: with forceThreatSpawns off a refused foe is "left out and logged". A bounty captain the blocklist refuses
	 * doesn't stand: raise says so, no captain is recorded (nothing a player could be told is there), no mob of the
	 * bounty is left in the world, and the log says the hideout's mob was refused and forceThreatSpawns is off.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaRefusedBountyOff1123d"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaRefusedBountyOff1123d")
	public void withForcingOffARefusedBountyCaptainLeavesNobodyAndSaysWhy(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = bounty(helper, hall, "beast_rider");
		refuseHostiles(helper, false);
		helper.assertFalse(Bounties.raise(level, b, helper.absolutePos(CAMP), helper.absolutePos(CAMP.north(4))),
			"raise says the Beast Rider stands, refused with forceThreatSpawns off");
		helper.assertTrue(b.captain == null, "a captain who never came into the world is recorded: " + b.captain);
		helper.assertTrue(b.band.isEmpty(), "a band is recorded for a captain who never came: " + b.band);
		List<Mob> left = tagged(helper, Bounties.TAG);
		helper.assertTrue(left.isEmpty(), "mobs of the bounty are in the world: " + left);
		String warning = HostileSpawns.lastWarning();
		helper.assertTrue(warning.contains("bounty hideout") && warning.contains("forceThreatSpawns is off"), "no clear log line: " + warning);
		helper.succeed();
	}

	// ---- the Bandit King and his horn --------------------------------------------------------------------------------

	private static void kingCleanup(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(32);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box, m -> m.getTags().contains(BanditFort.TAG) || m.getTags().contains(BanditKing.TAG)).forEach(Mob::discard);
			BanditFort.tick(level);
		});
	}

	/**
	 * Spec (lairs.md: "the Bandit King and his fort's men" go through HostileSpawns; 31.16: "at half health he blows a
	 * goat horn and 4 bandits come out of the keep, once"). With every hostile mob refused and forcing on, the King takes
	 * his place, persistent, with his 150 health; brought to half health his horn still brings 4 bandits, each in the
	 * world, and all of them are still there 60 ticks later.
	 */
	//$ gametest_ticks_batch HUGE '120' '"qaRefusedKing1123d"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "qaRefusedKing1123d")
	public void aRefusedBanditKingStandsAndHisHornStillCallsFour(GameTestHelper helper) {
		Leftovers.clear(helper);
		kingCleanup(helper);
		ServerLevel level = helper.getLevel();
		refuseHostiles(helper, true);
		BlockPos at = helper.absolutePos(new BlockPos(10, 2, 10));
		Mob king = BanditKing.spawn(level, at, at, helper.absolutePos(new BlockPos(14, 2, 10)));
		helper.assertTrue(king != null && in(level, king), "no King in the world though forceThreatSpawns is on; last warning: " + HostileSpawns.lastWarning());
		helper.assertTrue(king.isPersistenceRequired() && king.getTags().contains(BanditKing.TAG), "the King isn't persistent and tagged: " + king.getTags());
		helper.assertTrue(Math.abs(king.getMaxHealth() - BanditKing.HEALTH) < 0.01, "the King's max health is " + king.getMaxHealth());
		king.setNoAi(true);
		king.setHealth((float) (BanditKing.HEALTH / 2));
		BanditFort.tick(level);
		List<Mob> called = tagged(helper, BanditKing.CALLED_TAG);
		helper.assertTrue(called.size() == BanditKing.CALLED, "the horn called " + called.size() + " bandits into the world, not " + BanditKing.CALLED
			+ "; last warning: " + HostileSpawns.lastWarning());
		for (Mob man : called) {
			helper.assertTrue(in(level, man) && man.isPersistenceRequired(), "a called bandit isn't in the world and persistent: " + man);
		}
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(in(level, king), "the King is gone after 60 ticks");
			helper.assertTrue(called.stream().allMatch(m -> in(level, m)), "a called bandit is gone after 60 ticks");
			helper.succeed();
		});
	}

	/**
	 * Spec: off, a refused foe is left out and logged. The King refused with forcing off: no King (spawn says null), no
	 * crowned vindicator left anywhere, and the log names the Bandit King and forceThreatSpawns.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaRefusedKingOff1123d"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaRefusedKingOff1123d")
	public void withForcingOffARefusedBanditKingIsLeftOutAndLogged(GameTestHelper helper) {
		Leftovers.clear(helper);
		kingCleanup(helper);
		ServerLevel level = helper.getLevel();
		refuseHostiles(helper, false);
		BlockPos at = helper.absolutePos(new BlockPos(10, 2, 10));
		Mob king = BanditKing.spawn(level, at, at, helper.absolutePos(new BlockPos(14, 2, 10)));
		helper.assertTrue(king == null, "spawn handed back a King the world refused: " + king + (king != null ? " in the world " + in(level, king) : ""));
		helper.assertTrue(tagged(helper, BanditKing.TAG).isEmpty(), "a Bandit King is in the world: " + tagged(helper, BanditKing.TAG));
		BanditFort.tick(level); // (no tracked King to tick)
		String warning = HostileSpawns.lastWarning();
		helper.assertTrue(warning.contains("Bandit King") && warning.contains("forceThreatSpawns is off"), "no clear log line: " + warning);
		helper.succeed();
	}

	// ---- the Pharaoh's risen dead ------------------------------------------------------------------------------------

	private static Culture desert(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(AliveWorkplace.id("desert_raiders"));
		helper.assertTrue(culture.isPresent(), "setup: expected desert_raiders.json loaded, found none");
		return culture.get();
	}

	/** A Pharaoh (a raiser) and a husk beside him, both put in before the blocklist starts refusing. */
	private static Husk pharaohAndHusk(GameTestHelper helper) {
		Leftovers.clear(helper);
		Risings.forget();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(32);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Husk || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Risings.forget();
		});
		Husk pharaoh = helper.spawn(EntityType.HUSK, new BlockPos(4, 2, 4));
		pharaoh.setNoAi(true);
		pharaoh.addTag(VillageRaids.TAG);
		Risings.mark(pharaoh, desert(helper).captain().orElseThrow());
		helper.assertTrue(pharaoh.getTags().contains(Risings.RAISER_TAG), "setup: expected the Pharaoh marked as a raiser");
		Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(10, 2, 10));
		husk.setNoAi(true);
		husk.addTag(VillageRaids.TAG);
		return husk;
	}

	private static List<Husk> risen(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Husk.class, helper.getBounds().inflate(2), h -> h.isAlive() && h.getTags().contains(Risings.RISEN_TAG));
	}

	/**
	 * Spec (lairs.md: "... the risen" go through HostileSpawns; 32.9: a husk that falls near the Pharaoh rises once more
	 * after the delay). With every hostile mob refused and forcing on, a husk killed beside the Pharaoh still rises: one
	 * risen husk in the world, and still there 40 ticks later.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaRefusedRisen1123d"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaRefusedRisen1123d")
	public void aRefusedRisenHuskStillRisesWhenForced(GameTestHelper helper) {
		Husk husk = pharaohAndHusk(helper);
		ServerLevel level = helper.getLevel();
		refuseHostiles(helper, true);
		husk.kill();
		helper.assertTrue(Risings.pending() == 1, "setup: the husk isn't waiting to rise: " + Risings.pending());
		helper.runAfterDelay(80, () -> {
			List<Husk> up = risen(helper);
			helper.assertTrue(up.size() == 1, "expected one risen husk in the world though forceThreatSpawns is on, got " + up.size()
				+ "; last warning: " + HostileSpawns.lastWarning());
			helper.assertTrue(in(level, up.get(0)), "the risen husk isn't in the world");
			helper.assertTrue(HostileSpawns.lastWarning().contains("risen dead"), "no log line names the risen dead: " + HostileSpawns.lastWarning());
			Husk again = up.get(0);
			helper.runAfterDelay(40, () -> {
				helper.assertTrue(in(level, again), "the risen husk is gone 40 ticks later");
				helper.succeed();
			});
		});
	}

	/**
	 * Spec: off, left out and logged. Refused with forcing off, the husk killed beside the Pharaoh doesn't rise, nothing
	 * is left waiting to rise (it isn't tried again and again), and the log names the risen dead and forceThreatSpawns.
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaRefusedRisenOff1123d"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaRefusedRisenOff1123d")
	public void withForcingOffARefusedRisenHuskStaysDeadAndIsLogged(GameTestHelper helper) {
		Husk husk = pharaohAndHusk(helper);
		refuseHostiles(helper, false);
		husk.kill();
		helper.assertTrue(Risings.pending() == 1, "setup: the husk isn't waiting to rise: " + Risings.pending());
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(risen(helper).isEmpty(), "a refused husk rose with forceThreatSpawns off: " + risen(helper));
			helper.assertTrue(Risings.pending() == 0, "the refused husk is still waiting to rise: " + Risings.pending());
			String warning = HostileSpawns.lastWarning();
			helper.assertTrue(warning.contains("risen dead") && warning.contains("forceThreatSpawns is off"), "no clear log line: " + warning);
			helper.succeed();
		});
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Seer;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-11-04): independent checks of three threat fixes, written from the spec and the wiki.
 * <ul>
 * <li>B907 (32.5, wiki sieges.md: "the other raiders follow up the same ladder while the gate is still shut. Once the
 * gate is open, one still on its way to a ladder turns and goes in by the gate instead"): a follower close to the
 * ladder's foot, a restart right as the gate breaks, and the lead climber already up its ladder. Uses
 * {@link QaSiegeLadderBreach1103dGameTests}' yard.</li>
 * <li>B915 (wiki raids.md: "Every raider belongs to exactly one raid: a village's reach is measured from where the raider
 * stands (its feet), RADIUS + 32 across and 48 blocks up or down from the hall"): 48 against 49 above and below, for a
 * tall Ravager and a short Silverfish, and a shared raider killed. Uses {@link QaRaidOwnership1104cGameTests}' setup.</li>
 * <li>B919 (wiki raids.md: "a Seer 'sees raids in visions, 2 days before they come', the Lighthouse's keeper 'watches the
 * sea from the lighthouse'"): each lookout's own words on the Defense page, with its own number of days.</li>
 * </ul>
 */
public class QaThreatFixes1104dGameTests {
	static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos SEER_HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation LIGHTHOUSE = AliveWorkplace.id("lighthouse");
	private static final ResourceLocation PIRATES = AliveWorkplace.id("drowned_pirates");
	/** Day 151 begins here: far from any other test's days. */
	private static final long BASE = 150 * VillageNeeds.DAY;

	// ---------------------------------------------------------------- B907: siege ladders once the gate is open

	/** Follows the follower after the gate is broken: never more than two blocks up the ladder, in by the gate within 400 ticks. */
	static void climbsNoMoreAndGoesInByTheGate(GameTestHelper helper, QaSiegeLadderBreach1103dGameTests.Yard yard, Mob follower, long tick,
		long opened, BlockPos foot, double[] highest, String what) {
		if (QaSiegeLadderBreach1103dGameTests.upTheLadder(follower, foot)) {
			highest[0] = Math.max(highest[0], follower.getY() - foot.getY());
		}
		helper.assertTrue(highest[0] <= 2.0, String.format(Locale.ROOT, "%s climbed the ladder %.1f blocks, %d ticks after the gate was broken open "
			+ "(it should turn for the gate): ", what, highest[0], tick - opened) + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower)
			+ "; ladder foot " + QaSiegeLadderBreach1103dGameTests.rel(helper, foot));
		helper.assertTrue(!Ladders.over(yard.level(), yard.hall(), follower.getUUID()), what + " went over the wall by the ladder after the gate was "
			+ "broken open: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
		if (QaSiegeLadderBreach1103dGameTests.inside(helper, follower) && Sieges.through(yard.level(), yard.hall(), follower.getUUID())) {
			helper.succeed();
		} else if (tick - opened > 400) {
			helper.fail(what + " isn't in through the gate 400 ticks after it was broken open: " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower)
				+ "; ladder foot " + QaSiegeLadderBreach1103dGameTests.rel(helper, foot));
		}
	}

	/**
	 * A restart as the gate goes: the follower is walking to the ladder when the gate is broken open, and the server
	 * forgets what the siege directors and ladders keep in memory (as a restart does; the siege and its rungs are saved),
	 * and the follower's walk with it. Back up, with the gate open, nobody sends it up the ladder again: it climbs no more
	 * than two blocks and goes in by the gate.
	 */
	//$ gametest_ticks_batch HUGE '1500' '"qa1104dLadderRestart"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1104dLadderRestart")
	public void afterARestartAsTheGateOpensTheFollowerGoesInByTheGate(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeLadderBreach1103dGameTests.yard(helper);
		Mob follower = QaSiegeLadderBreach1103dGameTests.raiders(helper, yard)[1];
		ThreatData.Siege siege = yard.siege(helper);
		long[] opened = {-1};
		BlockPos[] foot = {null};
		double[] highest = {0};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (opened[0] < 0) {
				if (QaSiegeLadderBreach1103dGameTests.standing(siege) && QaSiegeLadderBreach1103dGameTests.onLadderDuty(yard, follower) && follower.onGround()) {
					BlockPos at = QaSiegeLadderBreach1103dGameTests.foot(siege);
					double dx = follower.getX() - (at.getX() + 0.5);
					double dz = follower.getZ() - (at.getZ() + 0.5);
					if (dx * dx + dz * dz > 4 && follower.getY() < at.getY() + 0.5) {
						foot[0] = at;
						opened[0] = tick;
						QaSiegeLadderBreach1103dGameTests.breakTheGate(yard, siege);
						Sieges.forget();
						Ladders.forget();
						follower.getNavigation().stop();
						helper.assertTrue(QaSiegeLadderBreach1103dGameTests.standing(yard.siege(helper)), "the rungs weren't kept with the saved siege over the restart");
					}
				} else if (tick > 1100) {
					helper.fail("the follower was never sent up a standing ladder while still on the ground (the test would prove nothing): "
						+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, follower));
				}
				return;
			}
			climbsNoMoreAndGoesInByTheGate(helper, yard, follower, tick, opened[0], foot[0], highest, "the follower after the restart");
		});
	}

	/**
	 * The lead climber, already up its ladder when the gate is broken open, isn't left hanging: as the wiki says of
	 * climbers ("they climb it, cross the wall's top and drop inside"), it is over the wall by the ladder and inside the
	 * yard, alive, within 300 ticks.
	 */
	//$ gametest_ticks_batch HUGE '1500' '"qa1104dLadderLeadClimber"'
	@GameTest(template = HUGE, timeoutTicks = 1500, batch = "qa1104dLadderLeadClimber")
	public void theLeadClimberUpItsLadderWhenTheGateOpensGetsOverAndInside(GameTestHelper helper) {
		QaSiegeLadderBreach1103dGameTests.Yard yard = QaSiegeLadderBreach1103dGameTests.yard(helper);
		Mob[] mobs = QaSiegeLadderBreach1103dGameTests.raiders(helper, yard);
		Mob climber = mobs[0];
		ThreatData.Siege siege = yard.siege(helper);
		long[] opened = {-1};
		BlockPos[] foot = {null};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (opened[0] < 0) {
				BlockPos at = QaSiegeLadderBreach1103dGameTests.foot(siege);
				if (at != null && QaSiegeLadderBreach1103dGameTests.upTheLadder(climber, at)) {
					foot[0] = at;
					opened[0] = tick;
					QaSiegeLadderBreach1103dGameTests.breakTheGate(yard, siege);
				} else if (tick > 1100 || Ladders.over(yard.level(), yard.hall(), climber.getUUID())) {
					helper.fail("the climber was never seen up its ladder before it was over (the test would prove nothing): rungs " + siege.ladders.size()
						+ "; climber " + QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber));
				}
				return;
			}
			if (QaSiegeLadderBreach1103dGameTests.inside(helper, climber) && Ladders.over(yard.level(), yard.hall(), climber.getUUID())) {
				helper.succeed();
			} else if (tick - opened[0] > 300) {
				helper.fail("the climber up its ladder when the gate was broken isn't over the wall and inside 300 ticks later: "
					+ QaSiegeLadderBreach1103dGameTests.state(helper, yard, climber) + "; ladder foot " + QaSiegeLadderBreach1103dGameTests.rel(helper, foot[0])
					+ ", rungs " + siege.ladders.size());
			}
		});
	}

	// ---------------------------------------------------------------- B915: one reach rule, one raid per raider

	private static Mob tagged(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos rel) {
		Mob mob = helper.spawn(type, rel);
		mob.setNoAi(true);
		mob.addTag(VillageRaids.TAG);
		return mob;
	}

	/**
	 * The reach goes from the raider's feet, whatever its height: a Ravager (2.2 tall), a Husk and a Silverfish (0.3 tall)
	 * are within the reach of a hall 48 blocks above or below their feet and out of it at 49, and the raid a raider
	 * belongs to ({@link VillageRaids#raidOf}) follows the same edge.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dRaidReachFeet"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dRaidReachFeet")
	public void theReachIsMeasuredFromTheFeetAboveAndBelowForTallAndShortRaiders(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, QaRaidOwnership1104cGameTests.REL_HALL, halls);
		// (the setup's own hall has no raid: only the raid records below are under way)
		List<Mob> mobs = List.of(tagged(helper, EntityType.RAVAGER, new BlockPos(4, 2, 14)), tagged(helper, EntityType.HUSK, new BlockPos(12, 2, 14)),
			tagged(helper, EntityType.SILVERFISH, new BlockPos(20, 2, 14)));
		for (Mob mob : mobs) {
			String name = mob.getType().getDescription().getString() + String.format(Locale.ROOT, " (%.2f tall)", mob.getBbHeight());
			BlockPos feet = mob.blockPosition();
			for (int dy : new int[] {48, -48}) {
				helper.assertTrue(VillageRaids.raiders(level, feet.offset(0, dy, 0)).contains(mob),
					"a " + name + " with its feet " + Math.abs(dy) + " blocks " + (dy > 0 ? "below" : "above") + " a hall isn't counted in its reach");
			}
			for (int dy : new int[] {49, -49}) {
				helper.assertFalse(VillageRaids.raiders(level, feet.offset(0, dy, 0)).contains(mob),
					"a " + name + " with its feet " + Math.abs(dy) + " blocks " + (dy > 0 ? "below" : "above") + " a hall is counted in its reach");
			}
		}
		// The raid a raider belongs to: a raid 49 above its feet doesn't own it; one 48 above does.
		Mob ravager = mobs.get(0);
		BlockPos out = ravager.blockPosition().above(49);
		QaRaidOwnership1104cGameTests.raid(helper, out, halls);
		helper.assertTrue(VillageRaids.raidOf(level, ravager) == null, "a raid on a hall 49 above the Ravager's feet owns it: " + VillageRaids.raidOf(level, ravager));
		BlockPos in = ravager.blockPosition().above(48).east(1);
		QaRaidOwnership1104cGameTests.raid(helper, in, halls);
		ThreatData.Under owner = VillageRaids.raidOf(level, ravager);
		helper.assertTrue(owner != null && owner.hall().equals(in), "the Ravager 48 below a raided hall isn't that raid's: " + (owner == null ? "none" : owner.hall().toShortString()));
		helper.succeed();
	}

	/**
	 * A raider within reach of village A (its hall 29 across, 27 down, 29 along) and nearer B's hall, straight above or
	 * below it by {@code dy}. Both villages are raided; B's raid has no other raider, A's none either. Then the rounds:
	 * exactly one raid is still under way, the one that owns the raider.
	 */
	private static void exactlyOneRaid(GameTestHelper helper, EntityType<? extends Mob> type, int dy, boolean bOwns) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, QaRaidOwnership1104cGameTests.REL_HALL, halls);
		BlockPos hallA = helper.absolutePos(QaRaidOwnership1104cGameTests.REL_HALL);
		BlockPos at = helper.absolutePos(QaRaidOwnership1104cGameTests.REL_RAIDER);
		BlockPos hallB = at.offset(0, dy, 0);
		QaRaidOwnership1104cGameTests.raid(helper, hallA, halls);
		QaRaidOwnership1104cGameTests.raid(helper, hallB, halls);
		Mob mob = tagged(helper, type, QaRaidOwnership1104cGameTests.REL_RAIDER);
		String where = String.format(Locale.ROOT, "a %s (%.2f tall) with its feet %d blocks %s the second raided hall", mob.getType().getDescription().getString(),
			mob.getBbHeight(), Math.abs(dy), dy > 0 ? "below" : "above");
		helper.assertTrue(mob.blockPosition().equals(at), "setup: the raider's feet aren't where the test put them: " + mob.blockPosition().toShortString());
		helper.assertTrue(at.distSqr(hallB) < at.distSqr(hallA), "setup: expected the raider nearer the second hall");
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(mob), "setup: expected " + where + " within the first village's reach");
		helper.assertTrue(VillageRaids.raiders(level, hallB).contains(mob) == bOwns,
			where + (bOwns ? " isn't" : " is") + " counted among the second village's raiders");
		ThreatData.Under owner = VillageRaids.raidOf(level, mob);
		BlockPos expected = bOwns ? hallB : hallA;
		helper.assertTrue(owner != null && owner.hall().equals(expected), where + " belongs to the raid on " + (owner == null ? "none" : owner.hall().toShortString())
			+ ", expected " + expected.toShortString());
		QaRaidOwnership1104cGameTests.tick(level, hallA);
		QaRaidOwnership1104cGameTests.tick(level, hallB);
		boolean aOn = ThreatData.get(level).raid(hallA).isPresent();
		boolean bOn = ThreatData.get(level).raid(hallB).isPresent();
		helper.assertTrue(mob.isAlive() && !mob.isRemoved(), "a round sent away " + where);
		helper.assertTrue(aOn != bOn, where + ": after both villages' rounds, first raid on " + aOn + ", second on " + bOn + " (expected exactly one)");
		helper.assertTrue(bOn == bOwns, where + ": the wrong raid is still under way (second on " + bOn + ")");
		BlockPos over = bOwns ? hallA : hallB;
		List<ThreatData.Past> past = ThreatData.get(level).past(over);
		helper.assertTrue(past.size() == 1 && !past.get(0).fled(), "expected the raid without the raider fought off: " + past);
		helper.succeed();
	}

	/** A Ravager (2.2 tall: its head reaches into 48) with its feet 49 below the second hall: the first raid's only. */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dRaidRavager49Below"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dRaidRavager49Below")
	public void aTallRavager49BelowTheSecondRaidedHallKeepsOnlyTheFirstRaidGoing(GameTestHelper helper) {
		exactlyOneRaid(helper, EntityType.RAVAGER, 49, false);
	}

	/** A Silverfish (0.3 tall) with its feet 48 below the second hall: within its reach and nearer, the second raid's only. */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dRaidSilverfish48Below"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dRaidSilverfish48Below")
	public void aShortSilverfish48BelowTheSecondRaidedHallKeepsOnlyTheSecondRaidGoing(GameTestHelper helper) {
		exactlyOneRaid(helper, EntityType.SILVERFISH, 48, true);
	}

	/** A Ravager with its feet 48 above the second hall: the top of its reach, the second raid's only. */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dRaidRavager48Above"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dRaidRavager48Above")
	public void aTallRavager48AboveTheSecondRaidedHallKeepsOnlyTheSecondRaidGoing(GameTestHelper helper) {
		exactlyOneRaid(helper, EntityType.RAVAGER, -48, true);
	}

	/** A Silverfish with its feet 49 above the second hall: out of its reach, the first raid's only. */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dRaidSilverfish49Above"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dRaidSilverfish49Above")
	public void aShortSilverfish49AboveTheSecondRaidedHallKeepsOnlyTheFirstRaidGoing(GameTestHelper helper) {
		exactlyOneRaid(helper, EntityType.SILVERFISH, -49, false);
	}

	/**
	 * Two raided villages and one raider between them, within both reaches and nearer the second hall (48 below it); the
	 * first village has a raider of its own by its hall. While the shared raider lives both raids go on; once it is killed,
	 * the second village's raid ends, fought off, and the first goes on (its own raider still about).
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dRaidSharedKilled"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dRaidSharedKilled")
	public void killingTheRaiderBetweenTwoRaidedVillagesEndsOnlyItsOwnRaid(GameTestHelper helper) {
		List<BlockPos> halls = new ArrayList<>();
		ServerLevel level = QaRaidOwnership1104cGameTests.setUp(helper, QaRaidOwnership1104cGameTests.NIGHT, QaRaidOwnership1104cGameTests.REL_HALL, halls);
		BlockPos hallA = helper.absolutePos(QaRaidOwnership1104cGameTests.REL_HALL);
		BlockPos at = helper.absolutePos(QaRaidOwnership1104cGameTests.REL_RAIDER);
		BlockPos hallB = at.above(48);
		QaRaidOwnership1104cGameTests.raid(helper, hallA, halls);
		QaRaidOwnership1104cGameTests.raid(helper, hallB, halls);
		Mob shared = tagged(helper, EntityType.HUSK, QaRaidOwnership1104cGameTests.REL_RAIDER);
		Mob ownA = tagged(helper, EntityType.HUSK, new BlockPos(26, 2, 3));
		helper.assertTrue(VillageRaids.raiders(level, hallA).contains(shared) && VillageRaids.raiders(level, hallB).contains(shared),
			"setup: expected the shared raider within both villages' reach");
		helper.assertFalse(VillageRaids.raiders(level, hallB).contains(ownA), "setup: the first village's own raider is within the second's reach");
		QaRaidOwnership1104cGameTests.tick(level, hallA);
		QaRaidOwnership1104cGameTests.tick(level, hallB);
		helper.assertTrue(ThreatData.get(level).raid(hallA).isPresent() && ThreatData.get(level).raid(hallB).isPresent(),
			"with each village's raider alive, a raid ended: first on " + ThreatData.get(level).raid(hallA).isPresent() + ", second on "
				+ ThreatData.get(level).raid(hallB).isPresent());
		shared.kill();
		helper.assertTrue(!shared.isAlive(), "setup: the shared raider didn't die");
		QaRaidOwnership1104cGameTests.tick(level, hallA);
		QaRaidOwnership1104cGameTests.tick(level, hallB);
		helper.assertTrue(ThreatData.get(level).raid(hallB).isEmpty(), "the second village's raid goes on after its only raider was killed");
		List<ThreatData.Past> pastB = ThreatData.get(level).past(hallB);
		helper.assertTrue(pastB.size() == 1 && !pastB.get(0).fled(), "expected the second village's raid fought off: " + pastB);
		helper.assertTrue(ThreatData.get(level).raid(hallA).isPresent(), "the first village's raid ended though its own raider is still about");
		helper.assertTrue(ThreatData.get(level).past(hallA).isEmpty(), "the first village booked an attack while its raid goes on: " + ThreatData.get(level).past(hallA));
		helper.assertTrue(ownA.isAlive() && !ownA.isRemoved(), "the first village's own raider was sent away at night");
		helper.succeed();
	}

	// ---------------------------------------------------------------- B919: each lookout's own words on the Defense page

	private record Watched(ServerLevel level, BlockPos hall, Villager seer) {
	}

	/** A hall and a settled Seer-kind Legend that sees {@code days} days ahead (null: the real Seer), the only Legend loaded. */
	private static Watched seerVillage(GameTestHelper helper, Integer days) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(SEER_HALL);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			ThreatData.get(level).forgetClock(hall);
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.setDayTime(time);
			new WorkplaceConfig().apply();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		Legend seer = Legends.get(Seer.ID).orElse(null);
		helper.assertTrue(seer != null, "seer.json not loaded");
		if (days != null) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1104d_seer_" + days);
			seer = Legends.read(id, com.google.gson.JsonParser.parseString("{\"rarity\": \"rare\", \"job\": \"aliveworkplace:legend\","
				+ " \"title\": \"legend.aliveworkplace.seer.title\", \"lore\": \"legend.aliveworkplace.seer.lore\","
				+ " \"names\": [\"legend.aliveworkplace.seer.name.1\"], \"conditions\": [], \"arrive\": [], \"needs\": {\"luxury\": \"jewels\"},"
				+ " \"powers\": [{\"type\": \"foretell\", \"warning_days\": " + days + "}]}").getAsJsonObject());
			helper.assertTrue(seer != null, "setup: the test Seer didn't read");
		}
		Legends.setForTest(Map.of(seer.id(), seer));
		helper.setBlock(SEER_HALL, ModBlocks.VILLAGE_HALL);
		ThreatData.get(level).forgetClock(hall);
		Villager s = helper.spawn(EntityType.VILLAGER, SEER_HALL.east(2));
		s.setNoAi(true);
		Legends.make(level, s, seer, "test");
		return new Watched(level, hall, s);
	}

	private static List<String> page(ItemStack next) {
		List<String> all = new ArrayList<>();
		all.add(next.getHoverName().getString());
		all.addAll(QaLaneD1104GameTests.lore(next));
		return all;
	}

	private static void seerLine(GameTestHelper helper, Integer days, String after) {
		Watched v = seerVillage(helper, days);
		List<String> page = page(DefencePage.next(v.level(), v.hall()));
		String line = v.seer().getName().getString() + " sees raids in visions, " + after + " before they come";
		helper.assertTrue(page.equals(List.of("No attack seen coming", line)), "the Defense page with only a Seer (" + (days == null ? 2 : days)
			+ " days' warning) should read [No attack seen coming, " + line + "]: " + page);
		helper.assertTrue(page.stream().noneMatch(l -> l.contains("tower") || l.contains("aliveworkplace.")), "the page: " + page);
		helper.succeed();
	}

	/** The real Seer (warning_days 2): the page reads "<Seer> sees raids in visions, 2 days before they come", word for word. */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dSeerTwoDays"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dSeerTwoDays")
	public void theSeersDefenseLineReadsTwoDaysWordForWord(GameTestHelper helper) {
		seerLine(helper, null, "2 days");
	}

	/** A Seer that sees one day ahead: "a day before they come", never "1 days". */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dSeerOneDay"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dSeerOneDay")
	public void aSeerWhoSeesOneDayAheadReadsADayBefore(GameTestHelper helper) {
		seerLine(helper, 1, "a day");
	}

	/** A Seer that sees three days ahead: the page gives its own number, 3, not the default 2. */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dSeerThreeDays"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dSeerThreeDays")
	public void aSeerWhoSeesThreeDaysAheadReadsThreeDays(GameTestHelper helper) {
		seerLine(helper, 3, "3 days");
	}

	/** The Seer gone (dead): the page no longer names it as the lookout, it says there is none. */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dSeerGone"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dSeerGone")
	public void withTheSeerGoneThePageSaysNoLookout(GameTestHelper helper) {
		Watched v = seerVillage(helper, null);
		String name = v.seer().getName().getString();
		helper.assertTrue(page(DefencePage.next(v.level(), v.hall())).get(0).equals("No attack seen coming"), "setup: the Seer isn't the lookout");
		v.seer().kill();
		List<String> page = page(DefencePage.next(v.level(), v.hall()));
		helper.assertTrue(page.equals(List.of("No lookout", "The horn is the only warning: build a Lookout Tower and post a guard by it")),
			"the Defense page with the Seer dead: " + page);
		helper.assertTrue(page.stream().noneMatch(l -> l.contains(name)), "the page still names the dead Seer: " + page);
		helper.succeed();
	}

	/**
	 * The Lighthouse's keeper as the only lookout, with pirates rolled for tomorrow night and not yet told: the page reads
	 * "The keeper of &lt;Village&gt; Lighthouse watches the sea from the lighthouse: an attack from the sea is seen a day
	 * ahead", not the tower's line nor the Seer's.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qa1104dLighthouseWatch"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qa1104dLighthouseWatch")
	public void theLighthouseKeepersDefenseLineIsTheSeaWatch(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		BlockPos hallRel = new BlockPos(15, 2, 15);
		BlockPos hall = helper.absolutePos(hallRel);
		Leftovers.after(helper, () -> {
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(hallRel, ModBlocks.VILLAGE_HALL);
		for (int x = 2; x <= 28; x++) {
			for (int z = 24; z <= 28; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
			}
		}
		helper.assertTrue(Threats.get(PIRATES).isPresent(), "setup: no drowned pirates");
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(LIGHTHOUSE, hall, UUID.randomUUID(), "Jesse", 3);
		manager.finish(site.id(), 7);
		Leftovers.after(helper, () -> {
			manager.remove(site.id());
			Wonders.forgetPowers();
		});
		ThreatData data = ThreatData.get(level);
		level.setDayTime(BASE + 12100);
		data.forgetClock(hall);
		data.rolled(hall, 151, new ThreatData.Attack(152, PIRATES, Math.PI / 2, 13600));
		List<String> page = page(DefencePage.next(level, hall));
		String line = "The keeper of " + VillageHalls.name(level, hall).getString() + " Lighthouse watches the sea from the lighthouse: an attack from the sea is seen a day ahead";
		helper.assertTrue(page.equals(List.of("No attack seen coming", line)), "the Defense page with the Lighthouse watching should read "
			+ "[No attack seen coming, " + line + "]: " + page);
		helper.assertTrue(page.stream().noneMatch(l -> l.contains("tower") || l.contains("visions") || l.contains("aliveworkplace.")), "the page: " + page);
		helper.succeed();
	}
}

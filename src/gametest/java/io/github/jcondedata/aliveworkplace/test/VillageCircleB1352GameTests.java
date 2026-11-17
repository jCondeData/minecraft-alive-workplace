package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.printer.PrinterWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B1352, B555's rest: lookups that built their own box from {@link VillageHalls#RADIUS} now take the village's circle. In
 * a 16-block village a villager 14 blocks out on both axes stands in the box's corner but outside the circle: the research
 * trees' "villagers" counter, the Steward's builders, the press's Scholar and a raid's targets leave him out, while one by
 * the hall is counted. Each test runs alone in its batch; the villagers have no AI, so they stay where they are put.
 */
public class VillageCircleB1352GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/** A Village Hall at {@link #HALL} with a 16-block village, everything near it cleared and put back afterwards. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return helper.absolutePos(HALL);
	}

	/** A villager by the hall, inside the village. */
	private static Villager inside(GameTestHelper helper, String name) {
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos at = hall.offset(2, 0, 2);
		helper.assertTrue(VillageHalls.contains(hall, at), "staging: " + name + " isn't in the village");
		return QaVillageCircleGameTests.at(helper, at, name);
	}

	/** A villager in the box's corner: inside the square round the hall, outside the village's circle. */
	private static Villager corner(GameTestHelper helper, String name) {
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos at = hall.offset(14, 0, 14);
		helper.assertTrue(VillageHalls.area(hall).contains(at.getCenter()), "staging: " + name + " isn't in the square");
		helper.assertTrue(!VillageHalls.contains(hall, at), "staging: " + name + " is in the village");
		return QaVillageCircleGameTests.at(helper, at, name);
	}

	private static Villager as(Villager v, VillagerProfession job) {
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(1));
		v.setVillagerXp(1);
		return v;
	}

	/** A Builder with his own bench beside him. */
	private static Villager builder(GameTestHelper helper, Villager v) {
		BlockPos bench = v.blockPosition().offset(1, 0, 0);
		helper.getLevel().setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		Builders.employ(helper.getLevel(), v, bench);
		return as(v, ModVillagers.BUILDER);
	}

	/** The research trees' "villagers" counter counts the grown villager by the hall, not the one in the box's corner. */
	//$ gametest_ticks_batch AREA '100' '"b1352Research"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b1352Research")
	public void theVillagersCounterLeavesOutTheBoxsCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Villager in = inside(helper, "Counted");
		Villager out = corner(helper, "Stranger");
		int count = ResearchTrees.count(level, hall, "villagers");
		helper.assertTrue(count == 1, "the villagers counter is " + count + ", not the 1 villager in the village");
		in.discard();
		out.discard();
		helper.succeed();
	}

	/** The Steward's desk hands a proposal to a builder of the village, never to one in the box's corner. */
	//$ gametest_ticks_batch AREA '100' '"b1352Steward"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b1352Steward")
	public void theStewardsBuildersLeaveOutTheBoxsCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), hall.offset(-8, 0, 2), Rotation.NONE, Mirror.NONE);
		StewardDesk.Proposal proposal = new StewardDesk.Proposal(1, AliveWorkplace.id("qa_rule"), false, AliveWorkplace.id("well"), at, "", "", "",
			List.of(), 0L, 0, false, List.of(), "");
		Villager out = builder(helper, corner(helper, "Stranger"));
		Optional<StewardDesk.Builder> found = StewardDesk.builderFor(level, hall, proposal, true);
		helper.assertTrue(found.isEmpty(), "the builder in the box's corner, outside the village, was given its proposal");
		Villager in = builder(helper, inside(helper, "Mason"));
		found = StewardDesk.builderFor(level, hall, proposal, true);
		helper.assertTrue(found.isPresent() && found.get().villager() == in, "the builder by the hall wasn't given the proposal: "
			+ found.map(b -> b.villager().getName().getString()));
		in.discard();
		out.discard();
		helper.succeed();
	}

	/** A press by the hall prints books only for a Scholar of the village, not one in the box's corner. */
	//$ gametest_ticks_batch AREA '100' '"b1352Printer"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "b1352Printer")
	public void thePressLeavesOutAScholarInTheBoxsCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		BlockPos press = hall.offset(-2, 0, 0);
		helper.runAfterDelay(2, () -> { // the hall's point of interest is filed after the tick it was placed in
			VillageHalls.forgetNearest();
			helper.assertTrue(VillageHalls.nearest(level, press).equals(Optional.of(hall)), "staging: the press finds no hall");
			Villager out = as(corner(helper, "Stranger"), ModVillagers.SCHOLAR);
			helper.assertTrue(!PrinterWork.hasScholar(level, press), "a Scholar in the box's corner, outside the village, counts as its Scholar");
			Villager in = as(inside(helper, "Reader"), ModVillagers.SCHOLAR);
			helper.assertTrue(PrinterWork.hasScholar(level, press), "the Scholar by the hall doesn't count as the village's");
			in.discard();
			out.discard();
			helper.succeed();
		});
	}

	/** A raid's raiders go for the villager by the hall, never for the one in the box's corner. */
	//$ gametest_ticks_batch AREA '120' '"b1352Raid"'
	@GameTest(template = AREA, timeoutTicks = 120, batch = "b1352Raid")
	public void aRaidsTargetsLeaveOutTheBoxsCorner(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Lairs.forget(level);
		VillageRaids.forget();
		long time = level.getDayTime();
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		HostileSpawns.forget();
		Leftovers.after(helper, () -> {
			HostileSpawns.forget();
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			VillageRaids.forget();
			Lairs.forget(level);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			level.setDayTime(time);
		});
		Villager in = inside(helper, "Target");
		Villager out = corner(helper, "Stranger");
		helper.runAfterDelay(2, () -> {
			level.setDayTime(39 * 24000L + 15000);
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0, Double.NaN, Threats.on(Threats.MONSTERS).orElseThrow(), RandomSource.create(1352));
			helper.assertTrue(raid != null && raid.raiders() > 0, "no raid: " + raid);
			List<Mob> raiders = VillageRaids.raiders(level, hall);
			helper.assertTrue(raiders.size() > 1, "staging: too few raiders to tell: " + raiders.size());
			helper.assertTrue(raiders.stream().noneMatch(m -> m.getTarget() == out),
				"a raider went for the villager in the box's corner, outside the village");
			helper.assertTrue(raiders.stream().allMatch(m -> m.getTarget() == in),
				"not every raider went for the villager by the hall: " + raiders.stream().map(m -> String.valueOf(m.getTarget())).toList());
			in.discard();
			out.discard();
			helper.succeed();
		});
	}
}

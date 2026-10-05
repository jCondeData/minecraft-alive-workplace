package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * ROADMAP 27.12: the Steward's rules for care, learning, safety, beauty and the market. Each new condition holding and
 * not holding, and each shipped rule in a village staged to need it (ranked among the rules of 27.12, so the village's
 * other wants, which come first, don't crowd them out of the morning's 8 wishes).
 */
public class StewardCivicRulesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final Set<String> RULES = Set.of("clinic_for_the_ill", "healing_center_for_the_ill", "graveyard", "schoolhouse", "library",
		"chapel", "lookout_tower", "barracks", "street_lamps", "well", "park_bench", "fountain", "gazebo", "market_square");

	private static BlockPos hall(GameTestHelper helper, VillageRanks.Rank rank) {
		return hall(helper, rank, HALL);
	}

	private static BlockPos hall(GameTestHelper helper, VillageRanks.Rank rank, BlockPos at) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> helper.getLevel().removeBlock(hall, false));
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(rank);
		return hall;
	}

	private static List<Villager> villagers(GameTestHelper helper, int n) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + (i % 9) * 2, 2, 19 + (i / 9) * 2));
			v.setNoAi(true);
			out.add(v);
		}
		return out;
	}

	private static List<Villager> children(GameTestHelper helper, int n) {
		List<Villager> out = new ArrayList<>();
		for (int i = 0; i < n; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + 2 * i, 2, 3));
			v.setNoAi(true);
			v.setAge(-24000);
			out.add(v);
		}
		return out;
	}

	/** A villager working at {@code job}, his workstation remembered (as the census counts workers). */
	private static Villager worker(GameTestHelper helper, VillagerProfession job, int x) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 6));
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(x, 2, 7))));
		return v;
	}

	private static BlockPos bed(GameTestHelper helper, int x, int z) {
		helper.setBlock(new BlockPos(x, 2, z), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(new BlockPos(x, 2, z + 1), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(new BlockPos(x, 2, z + 1));
	}

	private static void built(GameTestHelper helper, String blueprint, int x) {
		BuildSiteManager sites = BuildSiteManager.get(helper.getLevel());
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(new BlockPos(x, 2, 2)), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		sites.recordFinished(AliveWorkplace.id(blueprint), placement, UUID.randomUUID());
	}

	private static StewardConditions.Check check(GameTestHelper helper, BlockPos hall, String json) {
		StewardRules.Rule rule = StewardRules.read(AliveWorkplace.id("test/civic"), com.google.gson.JsonParser.parseString(
			"{\"when\": [" + json + "], \"do\": {\"assign_jobs\": {}}, \"priority\": 1, \"why\": \"x\"}"));
		return rule.when().get(0).test(StewardConditions.Facts.of(helper.getLevel(), hall));
	}

	private static void holds(GameTestHelper helper, BlockPos hall, String json, boolean held, long value) {
		StewardConditions.Check c = check(helper, hall, json);
		helper.assertTrue(c.held() == held && c.value() == value, json + ": expected " + (held ? "held" : "not held") + " with " + value
			+ ", got " + c.held() + " with " + c.value() + " (" + c.shown().getString() + ")");
	}

	private static List<StewardWishes.Wish> wishes(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		List<StewardRules.Rule> ours = StewardRules.all().stream().filter(r -> RULES.contains(r.id().getPath())).toList();
		return StewardWishes.rank(ours, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level));
	}

	private static List<String> ids(List<StewardWishes.Wish> wishes) {
		return wishes.stream().map(w -> w.rule().getPath()).toList();
	}

	private static StewardWishes.Wish builds(GameTestHelper helper, List<StewardWishes.Wish> wishes, String rule, String blueprint, String zone) {
		Optional<StewardWishes.Wish> wish = wishes.stream().filter(w -> w.rule().getPath().equals(rule)).findFirst();
		helper.assertTrue(wish.isPresent(), "no " + rule + " wish: " + ids(wishes));
		StewardRules.Effect e = wish.get().effect();
		helper.assertTrue(e.kind() == StewardRules.Kind.BUILD && e.blueprint().equals(Optional.of(AliveWorkplace.id(blueprint)))
			&& e.zone().equals(Optional.of(zone)), rule + " doesn't build a " + blueprint + " in " + zone + ": " + e);
		helper.assertTrue(StewardWishes.plotFor(wish.get()).map(r -> r.zoneKind().equals(zone)).orElse(false), rule + ": no plot asked in " + zone);
		helper.assertTrue(!wish.get().reason().getString().contains("steward.aliveworkplace"), rule + ": its why isn't translated");
		return wish.get();
	}

	private static void absent(GameTestHelper helper, List<StewardWishes.Wish> wishes, String... rules) {
		for (String rule : rules) {
			helper.assertTrue(!ids(wishes).contains(rule), rule + " shouldn't hold: " + ids(wishes));
		}
	}

	// ---- the conditions ----

	//$ gametest_ticks_batch AREA '40' '"civicGuardsShort"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicGuardsShort")
	public void guardsShortHoldsWithoutAGuardAndNotWithOne(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		holds(helper, hall, "{\"guards_short\": {}}", false, 0); // nobody to guard
		villagers(helper, 3);
		holds(helper, hall, "{\"guards_short\": {}}", true, 1);
		worker(helper, ModVillagers.GUARD, 4);
		holds(helper, hall, "{\"guards_short\": {}}", false, 0);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicNoWorker"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicNoWorker")
	public void noWorkerHoldsUntilSomeoneHasThatJob(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		holds(helper, hall, "{\"no_worker\": {\"profession\": \"aliveworkplace:nurse\"}}", true, 0);
		worker(helper, ModVillagers.NURSE, 4);
		holds(helper, hall, "{\"no_worker\": {\"profession\": \"aliveworkplace:nurse\"}}", false, 1);
		holds(helper, hall, "{\"no_worker\": {\"profession\": \"aliveworkplace:scholar\"}}", true, 0);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicRaided"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicRaided")
	public void raidedWithinCountsDaysFromTheHallsLastRaid(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall);
		long today = Chronicle.day(helper.getLevel());
		holds(helper, hall, "{\"raided_within\": {\"days\": 7}}", false, 0); // never raided
		entity.setLastRaidDay(today - 2);
		holds(helper, hall, "{\"raided_within\": {\"days\": 7}}", true, 2);
		entity.setLastRaidDay(today - 10);
		holds(helper, hall, "{\"raided_within\": {\"days\": 7}}", false, 10);
		holds(helper, hall, "{\"raided_within\": {\"days\": 30}}", true, 10);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '60' '"civicBandits"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "civicBandits")
	public void banditCampNearHoldsWhileACampPreysOnTheVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET, new BlockPos(1, 2, 1));
		Leftovers.after(helper, () -> {
			BanditCamps.forget(level);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(16), m -> m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
		});
		BanditCamps.forget(level);
		holds(helper, hall, "{\"bandit_camp_near\": {}}", false, 0);
		helper.runAfterDelay(2, () -> {
			BanditCamps.Camp camp = BanditCamps.found(level, hall, helper.absolutePos(new BlockPos(12, 1, 12)));
			helper.assertTrue(camp != null, "no camp");
			holds(helper, hall, "{\"bandit_camp_near\": {}}", true, (long) Math.sqrt(camp.pos().distSqr(hall)));
			BanditCamps.forget(level);
			holds(helper, hall, "{\"bandit_camp_near\": {}}", false, 0);
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '40' '"civicIll"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicIll")
	public void illCountsTheIllVillagers(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		List<Villager> people = villagers(helper, 3);
		Sickness.fallIll(helper.getLevel(), people.get(0));
		holds(helper, hall, "{\"ill\": {\"at_least\": 2}}", false, 1);
		Sickness.fallIll(helper.getLevel(), people.get(1));
		holds(helper, hall, "{\"ill\": {\"at_least\": 2}}", true, 2);
		helper.assertTrue(VillageAdvice.ill(helper.getLevel(), hall) == 2, "the ill tip counts otherwise");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '100' '"civicDarkBeds"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "civicDarkBeds")
	public void darkBedsCountsBedsWithoutLight(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		bed(helper, 3, 18);
		bed(helper, 14, 18); // 13 blocks from the glowstone below: still dark
		helper.runAfterDelay(3, () -> {
			holds(helper, hall, "{\"dark_beds\": {\"at_least\": 2}}", true, 2);
			helper.setBlock(new BlockPos(3, 2, 21), Blocks.GLOWSTONE);
			helper.succeedWhen(() -> holds(helper, hall, "{\"dark_beds\": {\"at_least\": 2}}", false, 1)); // once the light has spread
		});
	}

	//$ gametest_ticks_batch AREA '40' '"civicBeauty"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicBeauty")
	public void beautyBelowReadsTheDecorationsPoints(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		holds(helper, hall, "{\"beauty_below\": {\"points\": 3}}", true, 0);
		built(helper, "well", 2); // 2 points
		holds(helper, hall, "{\"beauty_below\": {\"points\": 3}}", true, 2);
		built(helper, "park_bench", 14); // 1 more
		holds(helper, hall, "{\"beauty_below\": {\"points\": 3}}", false, 3);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicChildren"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicChildren")
	public void childrenAtLeastCountsOnlyChildren(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 4);
		children(helper, 2);
		holds(helper, hall, "{\"children_at_least\": {\"n\": 3}}", false, 2);
		children(helper, 1);
		holds(helper, hall, "{\"children_at_least\": {\"n\": 3}}", true, 3);
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicCourting"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicCourting")
	public void courtingCouplesCountsCouplesNotYetMarried(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		List<Villager> people = villagers(helper, 2);
		Villager a = people.get(0);
		Villager b = people.get(1);
		holds(helper, hall, "{\"courting_couples\": {}}", false, 0);
		long today = Chronicle.day(helper.getLevel());
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), today, false));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), today, false));
		holds(helper, hall, "{\"courting_couples\": {}}", true, 1);
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), today, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), today, true));
		holds(helper, hall, "{\"courting_couples\": {}}", false, 0); // married now
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicDied"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicDied")
	public void diedWithinReadsTheChroniclesDeaths(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		holds(helper, hall, "{\"died_within\": {\"days\": 30}}", false, 0);
		Chronicle.record(helper.getLevel(), hall, Chronicle.Kind.BIRTH, Component.literal("a birth"));
		holds(helper, hall, "{\"died_within\": {\"days\": 30}}", false, 0); // not a death
		Chronicle.record(helper.getLevel(), hall, Chronicle.Kind.DEATH, Component.literal("a death"));
		holds(helper, hall, "{\"died_within\": {\"days\": 30}}", true, 1);
		helper.succeed();
	}

	// ---- the rules ----

	//$ gametest_ticks_batch AREA '40' '"civicClinic"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicClinic")
	public void twoIllAndNoNurseGetAClinicThenAHealingCenterFromAVillage(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		List<Villager> people = villagers(helper, 3);
		Sickness.fallIll(helper.getLevel(), people.get(0));
		holds(helper, hall, "{\"ill\": {}}", true, 1);
		absent(helper, wishes(helper, hall), "clinic_for_the_ill");
		Sickness.fallIll(helper.getLevel(), people.get(1));
		builds(helper, wishes(helper, hall), "clinic_for_the_ill", "clinic", "civic");
		absent(helper, wishes(helper, hall), "healing_center_for_the_ill");
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(VillageRanks.Rank.VILLAGE);
		builds(helper, wishes(helper, hall), "healing_center_for_the_ill", "healing_center", "civic");
		absent(helper, wishes(helper, hall), "clinic_for_the_ill");
		worker(helper, ModVillagers.NURSE, 4); // a nurse sees to them
		absent(helper, wishes(helper, hall), "clinic_for_the_ill", "healing_center_for_the_ill");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicGraveyard"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicGraveyard")
	public void aDeathInAVillageOfEightGetsAGraveyard(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 8);
		absent(helper, wishes(helper, hall), "graveyard");
		Chronicle.record(helper.getLevel(), hall, Chronicle.Kind.DEATH, Component.literal("a death"));
		builds(helper, wishes(helper, hall), "graveyard", "graveyard", "civic");
		built(helper, "graveyard", 2);
		absent(helper, wishes(helper, hall), "graveyard");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicSchool"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicSchool")
	public void threeChildrenGetASchoolhouse(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		children(helper, 3);
		builds(helper, wishes(helper, hall), "schoolhouse", "schoolhouse", "civic");
		built(helper, "schoolhouse_2", 2); // any tier counts
		absent(helper, wishes(helper, hall), "schoolhouse");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicLibrary"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicLibrary")
	public void sixVillagersAndNoScholarGetALibrary(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 5);
		absent(helper, wishes(helper, hall), "library");
		villagers(helper, 1);
		builds(helper, wishes(helper, hall), "library", "library", "civic");
		worker(helper, ModVillagers.SCHOLAR, 4);
		absent(helper, wishes(helper, hall), "library");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicChapel"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicChapel")
	public void aCourtingCoupleGetsAChapel(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		List<Villager> people = villagers(helper, 2);
		absent(helper, wishes(helper, hall), "chapel");
		long today = Chronicle.day(helper.getLevel());
		ModAttachments.PARTNER.set(people.get(0), new Couples.Partner(people.get(1).getUUID(), people.get(1).getDisplayName(), today, false));
		ModAttachments.PARTNER.set(people.get(1), new Couples.Partner(people.get(0).getUUID(), people.get(0).getDisplayName(), today, false));
		builds(helper, wishes(helper, hall), "chapel", "chapel", "civic");
		built(helper, "chapel", 2);
		absent(helper, wishes(helper, hall), "chapel");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicDefences"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicDefences")
	public void guardsShortGetALookoutTowerAndATownABarracksFirst(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 3);
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		builds(helper, wishes, "lookout_tower", "lookout_tower", "defences");
		absent(helper, wishes, "barracks");
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		wishes = wishes(helper, hall);
		builds(helper, wishes, "barracks", "barracks", "defences");
		helper.assertTrue(ids(wishes).indexOf("barracks") < ids(wishes).indexOf("lookout_tower"), "the barracks isn't first in a town: " + ids(wishes));
		worker(helper, ModVillagers.GUARD, 4);
		absent(helper, wishes(helper, hall), "barracks", "lookout_tower");
		helper.succeed();
	}

	/**
	 * Runs {@code then} once the village's dark beds are exactly {@code dark}: the light engine works off the server
	 * thread, so a few ticks after a light is placed a bed by it can still read dark on a busy machine (CI run 652).
	 * Fails after {@code ticks}.
	 */
	private static void onceDark(GameTestHelper helper, BlockPos hall, List<BlockPos> dark, int ticks, Runnable then) {
		List<BlockPos> now = VillageAdvice.darkBeds(helper.getLevel(), hall);
		if (now.equals(dark)) {
			then.run();
			return;
		}
		helper.assertTrue(ticks > 0, "the light never settled: dark beds " + now + ", expected " + dark);
		helper.runAfterDelay(1, () -> onceDark(helper, hall, dark, ticks - 1, then));
	}

	//$ gametest_ticks_batch AREA '100' '"civicLamps"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "civicLamps")
	public void twoDarkBedsGetAStreetLampByTheDarkestBed(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		BlockPos lit = bed(helper, 3, 17);
		helper.setBlock(new BlockPos(3, 2, 19), Blocks.GLOWSTONE);
		BlockPos far = bed(helper, 12, 17); // far enough from the glowstone to stay dark
		onceDark(helper, hall, List.of(far), 60, () -> {
			absent(helper, wishes(helper, hall), "street_lamps"); // one bed in the dark
			BlockPos dark = bed(helper, 18, 17);
			helper.runAfterDelay(3, () -> {
				List<StewardWishes.Wish> wishes = wishes(helper, hall);
				StewardWishes.Wish lamp = builds(helper, wishes, "street_lamps", "street_lamp", "homes");
				List<BlockPos> darkBeds = VillageAdvice.darkBeds(helper.getLevel(), hall);
				helper.assertTrue(darkBeds.size() == 2 && !darkBeds.contains(lit) && darkBeds.contains(dark), "dark beds: " + darkBeds);
				Optional<Plots.Request> request = StewardWishes.plotFor(helper.getLevel(), hall, lamp);
				helper.assertTrue(request.isPresent() && darkBeds.get(0).equals(request.get().near()),
					"the lamp's plot isn't looked for by the darkest bed: " + request + ", dark beds " + darkBeds);
				helper.assertTrue(StewardWishes.plotFor(lamp).map(r -> r.near() == null).orElse(false), "a plot without the village has a place to be near");
				helper.succeed();
			});
		});
	}

	//$ gametest_ticks_batch AREA '40' '"civicGardens"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicGardens")
	public void anUglyVillageOfFiveGetsAWellABenchAFountainAndAGazeboInThatOrder(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 4);
		absent(helper, wishes(helper, hall), "well", "park_bench", "fountain", "gazebo");
		villagers(helper, 1);
		List<StewardWishes.Wish> wishes = wishes(helper, hall);
		builds(helper, wishes, "well", "well", "gardens");
		builds(helper, wishes, "park_bench", "park_bench", "gardens");
		builds(helper, wishes, "fountain", "fountain", "gardens");
		builds(helper, wishes, "gazebo", "gazebo", "gardens");
		List<String> order = ids(wishes).stream().filter(Set.of("well", "park_bench", "fountain", "gazebo")::contains).toList();
		helper.assertTrue(order.equals(List.of("well", "park_bench", "fountain", "gazebo")), "not in that order: " + order);
		built(helper, "well", 2); // each once: the well is done, beauty 2 still under 3
		wishes = wishes(helper, hall);
		absent(helper, wishes, "well");
		builds(helper, wishes, "park_bench", "park_bench", "gardens");
		built(helper, "park_bench", 14); // beauty 3: pretty enough
		absent(helper, wishes(helper, hall), "well", "park_bench", "fountain", "gazebo");
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '40' '"civicMarket"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "civicMarket")
	public void aTownWithoutAMarketSquareGetsOne(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.VILLAGE);
		absent(helper, wishes(helper, hall), "market_square");
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		builds(helper, wishes(helper, hall), "market_square", "market_square", "market");
		built(helper, "market_square", 2);
		absent(helper, wishes(helper, hall), "market_square");
		helper.succeed();
	}

	// ---- saving, and bad files ----

	//$ gametest_ticks_batch 'FabricGameTest.EMPTY_STRUCTURE' '20' '"civicSave"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 20, batch = "civicSave")
	public void aLampWishKeepsItsPlaceAfterSavingAndOldWishesLoad(GameTestHelper helper) {
		StewardRules.Rule rule = StewardRules.all().stream().filter(r -> r.id().getPath().equals("street_lamps")).findFirst().orElse(null);
		helper.assertTrue(rule != null && rule.effect().near().equals(Optional.of(StewardRules.Effect.NEAR_DARK_BEDS)), "street_lamps: " + rule);
		StewardWishes.Wish wish = new StewardWishes.Wish(rule.id(), rule.effect(), rule.priority(), rule.why(), List.of(2L));
		Tag saved = StewardWishes.Wish.CODEC.encodeStart(NbtOps.INSTANCE, wish).getOrThrow();
		StewardWishes.Wish loaded = StewardWishes.Wish.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
		helper.assertTrue(loaded.equals(wish), "the wish changed on reload: " + loaded);
		// a wish saved before 27.12 has no "near": it loads with none
		net.minecraft.nbt.CompoundTag old = (net.minecraft.nbt.CompoundTag) saved.copy();
		old.getCompound("do").remove("near");
		helper.assertTrue(StewardWishes.Wish.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().effect().near().isEmpty(), "an old wish didn't load");
		// a rule naming a place nobody knows is skipped with a warning
		boolean refused = false;
		try {
			StewardRules.read(AliveWorkplace.id("test/bad_near"), com.google.gson.JsonParser.parseString(
				"{\"when\": [{\"no_builder\": {}}], \"do\": {\"build\": {\"blueprint\": \"aliveworkplace:street_lamp\", \"zone\": \"homes\", \"near\": \"moon\"}}, \"priority\": 1, \"why\": \"x\"}"));
		} catch (StewardRules.BadRule e) {
			refused = e.getMessage().contains("near");
		}
		helper.assertTrue(refused, "a bad near was taken");
		helper.succeed();
	}
}

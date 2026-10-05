package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardJobs;
import io.github.jcondedata.aliveworkplace.city.StewardResearch;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ScholarWork;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 27.9: the Steward's {@code assign_jobs} and {@code research}. A village on flat grass round a hall in the middle
 * of a huge test area, counted within {@link #RADIUS} blocks so nothing from the areas around it counts. Jobless
 * villagers get the jobs the gap order asks for (builder, farmer, guards; then porter, scholar, the nearest); a nitwit
 * and a child get nothing; a Berry Farm's shared composter gets an Orchard Keeper; Ask me first makes one proposal, Run
 * the village gives the jobs; research picks Fortification after a raid, Medicine after a sickness, never a topic that
 * isn't available; all of it survives save and reload.
 */
public class StewardJobsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final int RADIUS = 14;
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final ResourceLocation JOBS_RULE = AliveWorkplace.id("jobs");
	private static final ResourceLocation RESEARCH_RULE = AliveWorkplace.id("research");

	/** Flat grass, the hall; the village counted within {@link #RADIUS}. */
	private static ServerLevel ground(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return level;
	}

	private static Villager jobless(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true); // stays put: vanilla's own job search mustn't race the Steward
		return v;
	}

	private static VillagerProfession job(StewardJobs.Job job) {
		return job.job().orElseThrow();
	}

	private static Optional<StewardJobs.Job> jobOf(StewardJobs.Plan plan, Villager v) {
		return plan.jobs().stream().filter(j -> j.villager().equals(v.getUUID())).findFirst();
	}

	private static void expect(GameTestHelper helper, StewardJobs.Plan plan, Villager v, VillagerProfession profession, BlockPos station, String what) {
		Optional<StewardJobs.Job> j = jobOf(plan, v);
		helper.assertTrue(j.isPresent(), what + ": no job in " + plan);
		helper.assertTrue(job(j.get()) == profession && j.get().station().equals(helper.absolutePos(station)),
			what + ": got " + j.get() + ", wanted " + profession + " at " + helper.absolutePos(station));
	}

	/** No builder, food short, guards short: the three nearest the hall become builder, farmer and guard, in that order. */
	//$ gametest_ticks_batch AREA '40' '"jobsGapsFirst"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "jobsGapsFirst")
	public void threeJoblessGetBuilderFarmerAndGuard(GameTestHelper helper) {
		int before = VillageHalls.RADIUS;
		try {
			VillageHalls.RADIUS = RADIUS;
			ServerLevel level = ground(helper);
			BlockPos grindstone = new BlockPos(11, 2, 13); // the nearest block to all three: the order, not the distance, decides
			BlockPos composter = new BlockPos(6, 2, 22);
			BlockPos table = new BlockPos(24, 2, 24);
			helper.setBlock(grindstone, Blocks.GRINDSTONE);
			helper.setBlock(composter, Blocks.COMPOSTER);
			helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
			Villager first = jobless(helper, new BlockPos(12, 2, 15));
			Villager second = jobless(helper, new BlockPos(10, 2, 15));
			Villager third = jobless(helper, new BlockPos(8, 2, 15));
			BlockPos hall = helper.absolutePos(HALL);
			StewardJobs.Plan plan = StewardJobs.plan(level, hall);
			helper.assertTrue(plan.jobs().size() == 3, "jobs: " + plan.jobs());
			expect(helper, plan, first, ModVillagers.BUILDER, table, "no builder");
			expect(helper, plan, second, VillagerProfession.FARMER, composter, "food short");
			expect(helper, plan, third, ModVillagers.GUARD, grindstone, "guards short");
			List<StewardJobs.Job> given = StewardJobs.give(level, null, plan.jobs());
			helper.assertTrue(given.size() == 3, "given: " + given);
			helper.assertTrue(first.getVillagerData().getProfession() == ModVillagers.BUILDER, "first is " + first.getVillagerData().getProfession());
			helper.assertTrue(second.getVillagerData().getProfession() == VillagerProfession.FARMER, "second is " + second.getVillagerData().getProfession());
			helper.assertTrue(third.getVillagerData().getProfession() == ModVillagers.GUARD, "third is " + third.getVillagerData().getProfession());
			helper.assertTrue(StewardJobs.give(level, null, plan.jobs()).isEmpty(), "the same jobs given twice");
		} finally {
			VillageHalls.RADIUS = before;
		}
		helper.succeed();
	}

	/** A builder, a guard and food in store: a porter at the free Storehouse, a scholar while research is idle, then the nearest block's job. */
	//$ gametest_ticks_batch AREA '40' '"jobsGapsThen"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "jobsGapsThen")
	public void thenPorterScholarAndTheNearest(GameTestHelper helper) {
		int before = VillageHalls.RADIUS;
		try {
			VillageHalls.RADIUS = RADIUS;
			ServerLevel level = ground(helper);
			BlockPos table = new BlockPos(24, 2, 24);
			helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
			Villager builder = jobless(helper, table.north());
			Builders.employ(level, builder, helper.absolutePos(table));
			BlockPos post = new BlockPos(24, 2, 6);
			helper.setBlock(post, Blocks.GRINDSTONE);
			Villager guard = jobless(helper, post.north());
			Stations.assign(level, guard, helper.absolutePos(post), ModVillagers.GUARD);
			BlockPos storehouse = new BlockPos(5, 2, 23); // within RADIUS of the hall (POIs are counted by distance)
			helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
			helper.setBlock(storehouse.east(), Blocks.CHEST);
			if (helper.getBlockEntity(storehouse.east()) instanceof Container chest) {
				chest.setItem(0, new ItemStack(Items.BREAD, 64));
			}
			BlockPos lectern = new BlockPos(6, 2, 5);
			BlockPos fletching = new BlockPos(11, 2, 13); // the nearest to all three
			helper.setBlock(lectern, Blocks.LECTERN);
			helper.setBlock(fletching, Blocks.FLETCHING_TABLE);
			Villager first = jobless(helper, new BlockPos(12, 2, 15));
			Villager second = jobless(helper, new BlockPos(10, 2, 15));
			Villager third = jobless(helper, new BlockPos(8, 2, 15));
			BlockPos hall = helper.absolutePos(HALL);
			helper.assertTrue(VillageHalls.census(level, hall).food() >= 64, "setup: no food in store");
			StewardJobs.Plan plan = StewardJobs.plan(level, hall);
			helper.assertTrue(plan.jobs().size() == 3, "jobs: " + plan.jobs());
			expect(helper, plan, first, ModVillagers.PORTER, storehouse, "a free Storehouse");
			expect(helper, plan, second, ModVillagers.SCHOLAR, lectern, "research idle (the lectern's other job)");
			expect(helper, plan, third, VillagerProfession.FLETCHER, fletching, "the nearest");
		} finally {
			VillageHalls.RADIUS = before;
		}
		helper.succeed();
	}

	/** A nitwit and a child get no job, even with a free block beside them. */
	//$ gametest_ticks_batch AREA '40' '"jobsNitwit"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "jobsNitwit")
	public void aNitwitAndAChildGetNothing(GameTestHelper helper) {
		int before = VillageHalls.RADIUS;
		try {
			VillageHalls.RADIUS = RADIUS;
			ServerLevel level = ground(helper);
			helper.setBlock(new BlockPos(12, 2, 12), Blocks.COMPOSTER);
			helper.setBlock(new BlockPos(18, 2, 18), Blocks.GRINDSTONE);
			Villager nitwit = jobless(helper, new BlockPos(12, 2, 15));
			nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
			Villager child = jobless(helper, new BlockPos(18, 2, 15));
			child.setAge(-24000);
			StewardJobs.Plan plan = StewardJobs.plan(level, helper.absolutePos(HALL));
			helper.assertTrue(plan.jobs().isEmpty(), "jobs for a nitwit or a child: " + plan.jobs());
			helper.assertTrue(!StewardJobs.wantsJob(nitwit) && !StewardJobs.wantsJob(child), "a nitwit or a child wants a job");
		} finally {
			VillageHalls.RADIUS = before;
		}
		helper.succeed();
	}

	/** The composter in a Berry Farm with no Orchard Keeper: he's picked there, and the farmer the food wants is handed to 27.11. */
	//$ gametest_ticks_batch AREA '40' '"jobsShared"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "jobsShared")
	public void anOrchardKeeperAtTheBerryFarmsComposter(GameTestHelper helper) {
		int before = VillageHalls.RADIUS;
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement farm = new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(new BlockPos(3, 2, 3)),
			Rotation.NONE, Mirror.NONE);
		try {
			VillageHalls.RADIUS = RADIUS;
			ground(helper);
			BuildSiteManager.get(level).recordFinished(AliveWorkplace.id("berry_farm"), farm, helper.makeMockServerPlayerInLevel().getUUID());
			BlockPos composter = new BlockPos(6, 2, 6); // inside the farm (11 by 9 from 3,3)
			helper.setBlock(composter, Blocks.COMPOSTER);
			helper.setBlock(new BlockPos(24, 2, 24), ModBlocks.BLUEPRINT_TABLE);
			Villager builder = jobless(helper, new BlockPos(24, 2, 23));
			Builders.employ(level, builder, helper.absolutePos(new BlockPos(24, 2, 24)));
			Villager keeper = jobless(helper, new BlockPos(12, 2, 15));
			BlockPos hall = helper.absolutePos(HALL);
			StewardJobs.Plan plan = StewardJobs.plan(level, hall);
			expect(helper, plan, keeper, ModVillagers.ORCHARD_KEEPER, composter, "the Berry Farm's composter");
			helper.assertTrue(plan.wanted().contains(VillagerProfession.FARMER), "the farmer with no free composter not wanted: " + plan.wanted());
			helper.assertTrue(StewardJobs.give(level, null, plan.jobs()).size() == 1, "not given");
			helper.assertTrue(keeper.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER, "he is " + keeper.getVillagerData().getProfession());
			// The farm has its keeper now: a second composter in it is anyone's.
			helper.setBlock(new BlockPos(8, 2, 6), Blocks.COMPOSTER);
			Villager next = jobless(helper, new BlockPos(10, 2, 15));
			StewardJobs.Plan again = StewardJobs.plan(level, hall);
			expect(helper, again, next, VillagerProfession.FARMER, new BlockPos(8, 2, 6), "a second composter once the farm has its keeper");
		} finally {
			VillageHalls.RADIUS = before;
			BuildSiteManager.get(level).forgetFinished(farm);
		}
		helper.succeed();
	}

	/** A village with a Steward appointed by the hall's owner, a builder at work and the morning's jobs wish. */
	private record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	private static Village village(GameTestHelper helper, StewardWishes.Wish... wishes) {
		ServerLevel level = ground(helper);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BlockPos table = new BlockPos(24, 2, 24);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = jobless(helper, table.north());
		Builders.employ(level, builder, helper.absolutePos(table));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		entity.setStewardWishes(new StewardWishes.State(StewardWishes.day(level), List.of(wishes), Map.of()));
		return new Village(level, hall, owner, steward);
	}

	private static StewardWishes.Wish jobsWish() {
		return new StewardWishes.Wish(JOBS_RULE, new StewardRules.Effect(StewardRules.Kind.ASSIGN_JOBS, Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty()), 75, "steward.aliveworkplace.why.jobless", List.of(1L));
	}

	private static StewardWishes.Wish researchWish() {
		return new StewardWishes.Wish(RESEARCH_RULE, new StewardRules.Effect(StewardRules.Kind.RESEARCH, Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty()), 40, "steward.aliveworkplace.why.research", List.of(3L));
	}

	/** Ask me first: the morning's jobs are one proposal, given on Approve (the wish carried out); nothing changes before. */
	//$ gametest_ticks_batch AREA '40' '"jobsAsk"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "jobsAsk")
	public void askMeFirstMakesOneProposal(GameTestHelper helper) {
		int before = VillageHalls.RADIUS;
		StewardJobs.WorkplaceWanted hook = StewardJobs.WORKPLACE_WANTED;
		List<VillagerProfession> wanted = new ArrayList<>();
		try {
			VillageHalls.RADIUS = RADIUS;
			StewardJobs.WORKPLACE_WANTED = (l, h, p) -> wanted.add(p);
			Village v = village(helper, jobsWish());
			helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
			helper.setBlock(new BlockPos(11, 2, 13), Blocks.FLETCHING_TABLE);
			Villager dara = jobless(helper, new BlockPos(12, 2, 15));
			Villager bram = jobless(helper, new BlockPos(10, 2, 15));
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			StewardDesk.plan(v.level(), v.steward(), v.hall()); // a second second: still one proposal
			List<StewardDesk.Proposal> proposals = StewardDesk.of(v.level(), v.hall()).proposals();
			helper.assertTrue(proposals.size() == 1 && proposals.get(0).isJobs() && proposals.get(0).jobs().size() == 2, "proposals: " + proposals);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE, "a job given before Approve");
			helper.assertTrue(wanted.contains(ModVillagers.GUARD), "guards short with no free grindstone, not handed to 27.11: " + wanted);
			String name = proposals.get(0).name().getString();
			helper.assertTrue(name.equals("Give 2 villagers jobs"), "its name: " + name);
			String line = StewardJobs.line(v.level(), v.hall(), proposals.get(0).jobs()).getString();
			helper.assertTrue(line.startsWith("Give 2 villagers jobs: ") && line.contains("Farmer at the Composter ") && line.contains(" blocks ") && line.contains("; "),
				"its line: " + line);
			helper.assertTrue(StewardDesk.approve(v.level(), v.hall(), v.owner(), proposals.get(0).id()) == StewardDesk.Outcome.STARTED, "not approved");
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.FARMER, "Dara is " + dara.getVillagerData().getProfession());
			helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.FLETCHER, "Bram is " + bram.getVillagerData().getProfession());
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "the proposal stayed");
			helper.assertTrue(StewardWishes.of(v.level(), v.hall()).used(JOBS_RULE).times() == 1, "the wish not carried out");
			helper.assertTrue(StewardWishes.of(v.level(), v.hall()).wishes().isEmpty(), "the wish still wished");
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "planned again the same morning");
		} finally {
			VillageHalls.RADIUS = before;
			StewardJobs.WORKPLACE_WANTED = hook;
		}
		helper.succeed();
	}

	/** Run the village: the jobs are given with no click; Rest gives none. */
	//$ gametest_ticks_batch AREA '40' '"jobsRun"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "jobsRun")
	public void runTheVillageGivesTheJobsAndRestNone(GameTestHelper helper) {
		int before = VillageHalls.RADIUS;
		boolean selfRun = StewardDesk.SELF_RUN;
		try {
			VillageHalls.RADIUS = RADIUS;
			StewardDesk.SELF_RUN = true;
			Village v = village(helper, jobsWish());
			helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
			Villager dara = jobless(helper, new BlockPos(12, 2, 15));
			v.entity().setPlan(v.entity().plan().withMode(CityPlan.Mode.REST));
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE, "a job given while resting");
			helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "Run refused");
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.FARMER, "Dara is " + dara.getVillagerData().getProfession());
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "a proposal left in Run the village");
			helper.assertTrue(StewardWishes.of(v.level(), v.hall()).used(JOBS_RULE).times() == 1, "the wish not carried out");
			helper.assertTrue(v.entity().chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.PLANS
				&& e.text().getString().contains("gave out work")), "not in the chronicle");
		} finally {
			VillageHalls.RADIUS = before;
			StewardDesk.SELF_RUN = selfRun;
		}
		helper.succeed();
	}

	private static Research.State levels(Map<Research.Topic, Integer> levels) {
		Map<String, Integer> out = new java.util.HashMap<>();
		levels.forEach((t, n) -> out.put(t.key(), n));
		return new Research.State(out, Optional.empty(), 0, false);
	}

	private static Optional<Research.Topic> pick(Research.State state, StewardResearch.Village village) {
		return StewardResearch.pick(state, village, Optional.empty()).map(StewardResearch.Pick::topic);
	}

	/** The topic order, each only when available. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void researchTopicsInTheOrderAndOnlyWhenAvailable(GameTestHelper helper) {
		StewardResearch.Village calm = new StewardResearch.Village(100, 0, false, 0);
		Research.State open = levels(Map.of(Research.Topic.DRILL, 1, Research.Topic.HEARTH, 1, Research.Topic.SWIFT_HANDS, 1));
		helper.assertTrue(pick(open, new StewardResearch.Village(2, 3, true, 90)).equals(Optional.of(Research.Topic.FORTIFICATION)),
			"after a raid: " + pick(open, new StewardResearch.Village(2, 3, true, 90)));
		helper.assertTrue(pick(open, new StewardResearch.Village(7, 0, false, 0)).equals(Optional.of(Research.Topic.SWIFT_HANDS)),
			"a raid 7 days ago still counted");
		helper.assertTrue(pick(open, new StewardResearch.Village(100, 2, true, 90)).equals(Optional.of(Research.Topic.MEDICINE)), "after a sickness");
		helper.assertTrue(pick(open, new StewardResearch.Village(100, 1, true, 90)).equals(Optional.of(Research.Topic.GREEN_THUMB)), "food short");
		helper.assertTrue(pick(open, new StewardResearch.Village(100, 0, false, 80)).equals(Optional.of(Research.Topic.LOGISTICS)), "store full");
		helper.assertTrue(pick(open, new StewardResearch.Village(100, 0, false, 79)).equals(Optional.of(Research.Topic.SWIFT_HANDS)), "store 79%");
		helper.assertTrue(pick(Research.State.EMPTY, calm).equals(Optional.of(Research.Topic.SWIFT_HANDS)), "first of the rest");
		Research.State swiftDone = levels(Map.of(Research.Topic.SWIFT_HANDS, 3));
		helper.assertTrue(pick(swiftDone, calm).equals(Optional.of(Research.Topic.HEARTH)), "Hearth after Swift Hands");
		Research.State both = levels(Map.of(Research.Topic.SWIFT_HANDS, 3, Research.Topic.HEARTH, 2));
		helper.assertTrue(pick(both, calm).equals(Optional.of(Research.Topic.KINSHIP)), "Kinship last");
		// Never a topic that isn't available: no Drill, no Fortification; no Hearth, no Medicine, Green Thumb; nothing done, no Logistics.
		Research.State none = Research.State.EMPTY;
		StewardResearch.Village everything = new StewardResearch.Village(1, 5, true, 100);
		helper.assertTrue(pick(none, everything).equals(Optional.of(Research.Topic.SWIFT_HANDS)), "an unavailable topic: " + pick(none, everything));
		Research.State all = levels(Map.of(Research.Topic.SWIFT_HANDS, 3, Research.Topic.HEARTH, 2, Research.Topic.KINSHIP, 2));
		helper.assertTrue(pick(all, calm).isEmpty(), "picked past the end: " + pick(all, calm));
		for (Research.Topic t : Research.Topic.values()) {
			for (StewardResearch.Village village : List.of(calm, everything)) {
				pick(levels(Map.of(t, t.maxLevel)), village).ifPresent(p ->
					helper.assertTrue(levels(Map.of(t, t.maxLevel)).available(p), "picked " + p + ", not available"));
			}
		}
		// A rule naming its topic gets it while it's available.
		helper.assertTrue(StewardResearch.pick(none, calm, Optional.of(Research.Topic.HEARTH)).map(StewardResearch.Pick::topic)
			.equals(Optional.of(Research.Topic.HEARTH)), "the rule's own topic");
		helper.assertTrue(StewardResearch.pick(none, calm, Optional.of(Research.Topic.MEDICINE)).map(StewardResearch.Pick::topic)
			.equals(Optional.of(Research.Topic.SWIFT_HANDS)), "the rule's topic taken while unavailable");
		helper.succeed();
	}

	/** A scholar idle after a raid: Ask me first proposes Fortification; Run the village sets the scholars on it; Medicine after a sickness. */
	//$ gametest_ticks_batch AREA '40' '"researchSteward"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "researchSteward")
	public void theScholarsNextTopicAfterARaidAndASickness(GameTestHelper helper) {
		int before = VillageHalls.RADIUS;
		boolean selfRun = StewardDesk.SELF_RUN;
		try {
			VillageHalls.RADIUS = RADIUS;
			StewardDesk.SELF_RUN = true;
			Village v = village(helper, researchWish());
			Villager scholar = jobless(helper, new BlockPos(6, 2, 6));
			v.entity().setResearch(levels(Map.of(Research.Topic.DRILL, 1, Research.Topic.HEARTH, 1)));
			v.entity().setLastRaidDay(Chronicle.day(v.level()) - 2);
			ScholarWork.IDLE.idle(v.level(), scholar, v.hall());
			List<StewardDesk.Proposal> proposals = StewardDesk.of(v.level(), v.hall()).proposals();
			helper.assertTrue(proposals.size() == 1 && proposals.get(0).researchTopic().equals(Optional.of(Research.Topic.FORTIFICATION)),
				"after a raid: " + proposals);
			helper.assertTrue(v.entity().research().currentTopic() == null, "chosen before Approve");
			helper.assertTrue(StewardDesk.approve(v.level(), v.hall(), v.owner(), proposals.get(0).id()) == StewardDesk.Outcome.STARTED, "not approved");
			helper.assertTrue(v.entity().research().currentTopic() == Research.Topic.FORTIFICATION, "researching " + v.entity().research().currentTopic());
			helper.assertTrue(StewardWishes.of(v.level(), v.hall()).used(RESEARCH_RULE).times() == 1, "the wish not carried out");
			// The next morning, in Run the village, two ill and no raid: Medicine, chosen by himself.
			v.entity().setResearch(levels(Map.of(Research.Topic.DRILL, 1, Research.Topic.HEARTH, 1)));
			v.entity().setLastRaidDay(-100);
			v.entity().setStewardWishes(new StewardWishes.State(StewardWishes.day(v.level()), List.of(researchWish()), Map.of()));
			v.entity().setStewardDesk(StewardDesk.State.EMPTY);
			helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "Run refused");
			for (int i = 0; i < 2; i++) {
				Villager ill = jobless(helper, new BlockPos(20 + i, 2, 8));
				io.github.jcondedata.aliveworkplace.registry.ModAttachments.ILL_SINCE.set(ill, v.level().getGameTime());
			}
			ScholarWork.IDLE.idle(v.level(), scholar, v.hall());
			helper.assertTrue(v.entity().research().currentTopic() == Research.Topic.MEDICINE, "after a sickness: " + v.entity().research().currentTopic());
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "a proposal left in Run the village");
		} finally {
			VillageHalls.RADIUS = before;
			StewardDesk.SELF_RUN = selfRun;
		}
		helper.succeed();
	}

	/** The jobs and research proposals and the days they were planned survive save and reload. */
	//$ gametest_ticks_batch AREA '40' '"jobsSave"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "jobsSave")
	public void jobsAndResearchProposalsSurviveSaveAndReload(GameTestHelper helper) {
		int before = VillageHalls.RADIUS;
		try {
			VillageHalls.RADIUS = RADIUS;
			Village v = village(helper, jobsWish(), researchWish());
			helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
			jobless(helper, new BlockPos(12, 2, 15));
			Villager scholar = jobless(helper, new BlockPos(6, 2, 6));
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			ScholarWork.IDLE.idle(v.level(), scholar, v.hall());
			StewardDesk.State saved = StewardDesk.of(v.level(), v.hall());
			helper.assertTrue(saved.proposals().size() == 2 && saved.proposals().stream().anyMatch(StewardDesk.Proposal::isJobs)
				&& saved.proposals().stream().anyMatch(p -> p.researchTopic().isPresent()), "proposals: " + saved.proposals());
			helper.assertTrue(saved.jobsDay() == StewardWishes.day(v.level()) && saved.researchDay() == StewardWishes.day(v.level()), "days not noted");
			CompoundTag tag = v.entity().saveWithoutMetadata(v.level().registryAccess());
			v.entity().setStewardDesk(StewardDesk.State.EMPTY);
			v.entity().loadWithComponents(tag, v.level().registryAccess());
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).equals(saved), "not the same after reload: " + StewardDesk.of(v.level(), v.hall()));
			// A desk saved before 27.9 loads with no jobs or research and their days unset.
			CompoundTag old = StewardDesk.State.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, StewardDesk.State.EMPTY).getOrThrow() instanceof CompoundTag c
				? c : new CompoundTag();
			old.remove("jobs_day");
			old.remove("research_day");
			StewardDesk.State loaded = StewardDesk.State.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
			helper.assertTrue(loaded.jobsDay() == -1 && loaded.researchDay() == -1, "old desk: " + loaded);
		} finally {
			VillageHalls.RADIUS = before;
		}
		helper.succeed();
	}

	/** Every new sentence is in en_us.json, and none says "(s)". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyJobsAndResearchSentenceIsTranslated(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> keys = new ArrayList<>(List.of("steward.aliveworkplace.jobs.someone", "steward.aliveworkplace.jobs.job",
			"steward.aliveworkplace.jobs.title.one", "steward.aliveworkplace.jobs.title", "steward.aliveworkplace.jobs.line",
			"steward.aliveworkplace.research.title", "steward.aliveworkplace.research.why.named", "steward.aliveworkplace.research.why.raid",
			"steward.aliveworkplace.research.why.ill", "steward.aliveworkplace.research.why.food", "steward.aliveworkplace.research.why.store",
			"steward.aliveworkplace.research.why.next", "screen.aliveworkplace.desk.research_cost", "message.aliveworkplace.steward.desk.approved_jobs",
			"message.aliveworkplace.steward.desk.approved_research", "message.aliveworkplace.steward.desk.outcome.jobs_taken",
			"message.aliveworkplace.steward.desk.outcome.researching", "message.aliveworkplace.steward.research.chosen",
			"chronicle.aliveworkplace.plans_jobs", "chronicle.aliveworkplace.plans_research"));
		for (String key : keys) {
			helper.assertTrue(lang.has(key), "untranslated: " + key);
			helper.assertTrue(!lang.getOrDefault(key).contains("(s)"), "\"(s)\" in " + key);
		}
		helper.assertTrue(StewardJobs.title(1).getString().equals("Give 1 villager a job"), "one: " + StewardJobs.title(1).getString());
		helper.assertTrue(StewardJobs.title(3).getString().equals("Give 3 villagers jobs"), "three: " + StewardJobs.title(3).getString());
		for (StewardJobs.Gap gap : StewardJobs.Gap.values()) {
			helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.getKey(gap.profession()) != null, "gap " + gap);
		}
		helper.succeed();
	}
}

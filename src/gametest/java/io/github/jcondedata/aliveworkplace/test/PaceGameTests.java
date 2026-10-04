package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.craft.CrafterWork;
import io.github.jcondedata.aliveworkplace.explore.ExplorerWork;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mine.MinerWork;
import io.github.jcondedata.aliveworkplace.nether.NetherworkerWork;
import io.github.jcondedata.aliveworkplace.nether.Netherworkers;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.people.Traits.Trait;
import io.github.jcondedata.aliveworkplace.ranch.RancherWork;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ScholarWork;
import io.github.jcondedata.aliveworkplace.school.TeacherWork;
import io.github.jcondedata.aliveworkplace.work.Pace;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.component.ItemLore;

/**
 * One pace, one cap (ROADMAP 30.2): every bonus multiplies up to {@code maxWorkPace}, penalties come after it, a
 * worker's level stays outside it, and each job family takes its pace from {@link Pace}. (The stack with five Pokémon
 * partners, and the sifter's two partners counted once, are in the compat tests: partners need Cobblemon.)
 */
public class PaceGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final float EPSILON = 1e-6f;

	/** The rule itself: bonuses stop at 100 / maxWorkPace of the usual time, penalties multiply after; the setting is clamped to 100-400. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void bonusesStopAtTheCapAndPenaltiesComeAfter(GameTestHelper helper) {
		helper.assertTrue(Pace.combine(0.2f, 1f, 0.5f) == 0.5f, "capped: " + Pace.combine(0.2f, 1f, 0.5f));
		helper.assertTrue(Pace.combine(0.2f, 2f, 0.5f) == 1f, "capped, then ill: " + Pace.combine(0.2f, 2f, 0.5f));
		helper.assertTrue(Pace.combine(0.8f, 1f, 0.5f) == 0.8f, "under the cap: " + Pace.combine(0.8f, 1f, 0.5f));
		helper.assertTrue(Pace.combine(1f, 1.15f, 0.5f) == 1.15f, "only a penalty: " + Pace.combine(1f, 1.15f, 0.5f));
		int was = Pace.MAX_PERCENT;
		try {
			helper.assertTrue(Pace.cap() == 0.5f, "default cap: " + Pace.cap());
			Pace.MAX_PERCENT = 300;
			helper.assertTrue(Pace.cap() == 100f / 300f, "300: " + Pace.cap());
			Pace.MAX_PERCENT = 50;
			helper.assertTrue(Pace.cap() == 1f, "under 100 counts as 100: " + Pace.cap());
			Pace.MAX_PERCENT = 1000;
			helper.assertTrue(Pace.cap() == 0.25f, "over 400 counts as 400: " + Pace.cap());
		} finally {
			Pace.MAX_PERCENT = was;
		}
		helper.assertTrue(io.github.jcondedata.aliveworkplace.WorkplaceConfig.parse("{}").maxWorkPace == 200, "default 200");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.WorkplaceConfig.parse("{\"maxWorkPace\": 50}").maxWorkPace == 100, "clamped to 100");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.WorkplaceConfig.parse("{\"maxWorkPace\": 900}").maxWorkPace == 400, "clamped to 400");
		for (String id : java.util.List.of("partners", "well_kept", "swift_hands", "diligent", "happy", "craftsmanship", "expeditions",
				"ill", "unhappy", "lazy", "badly_kept")) {
			helper.assertTrue(Pace.sources().stream().anyMatch(s -> s.id().equals(id)), "no source " + id);
		}
		helper.succeed();
	}

	/**
	 * Every bonus there is without Pokémon (a village kept 100%, Swift Hands III, Diligent, happy) multiplies to its exact
	 * product under the cap; with the cap lower than that the pace is exactly the cap, and ill exactly half of it. The
	 * status line and the hall's list say so, with the sources.
	 */
	//$ gametest_ticks_batch AREA '100' '"paceStack"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "paceStack")
	public void everyBonusStacksUpToTheCapAndIllnessHalvesIt(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager builder = keptVillage(helper, Map.of("swift_hands", 3));
		helper.runAfterDelay(5, () -> {
			keepWell(helper);
			Villager happy = diligentAndHappy(helper, builder);
			float product = 0.8f * VillageNeeds.swiftHands(3) * (1f / 1.1f) * 0.93f;
			Pace.Breakdown pace = Pace.of(happy);
			helper.assertTrue(pace.faster().size() == 4 && pace.slower().isEmpty() && !pace.capped(), "sources: " + pace);
			helper.assertTrue(Math.abs(pace.factor() - product) < 1e-5f, "under the cap: " + pace.factor() + " vs " + product);

			int was = Pace.MAX_PERCENT;
			try {
				Pace.MAX_PERCENT = 150; // a cap of 2/3, above the stack's 0.59
				helper.assertTrue(Pace.factor(happy) == 100f / 150f, "at the cap: " + Pace.factor(happy));
				helper.assertTrue(BuilderLevels.delay(300, happy) == 200, "300 ticks at the cap: " + BuilderLevels.delay(300, happy));
				String status = BuilderLevels.describe(happy).getString();
				helper.assertTrue(status.endsWith(" · 50% faster: at the cap (a well-kept village, Swift Hands, diligent, a happy mood)"),
					"status: " + status);

				ModAttachments.ILL_SINCE.set(happy, level.getGameTime());
				Moods.forget();
				float ill = Pace.factor(happy);
				// Ill: the mood drops by 20 but stays happy; the bonuses still pass the cap, then the time doubles.
				helper.assertTrue(Math.abs(ill - 2f * 100f / 150f) < EPSILON, "ill at the cap: " + ill);
				helper.assertTrue(BuilderLevels.delay(300, happy) == 400, "300 ticks ill: " + BuilderLevels.delay(300, happy));
				String illStatus = BuilderLevels.describe(happy).getString();
				helper.assertTrue(illStatus.contains("25% slower: at the cap (") && illStatus.endsWith("; held back by being ill)"), "ill status: " + illStatus);
				hallLine(helper, level, happy, "Works 25% slower: at the cap (");
			} finally {
				Pace.MAX_PERCENT = was;
			}
			helper.succeed();
		});
	}

	/** A worker's level is their normal pace, not a bonus: a Master's 40% share applies outside the cap. */
	//$ gametest_ticks_batch AREA '100' '"paceLevel"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "paceLevel")
	public void aMastersLevelStaysOutsideTheCap(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager builder = keptVillage(helper, Map.of());
		helper.runAfterDelay(5, () -> {
			keepWell(helper);
			builder.setVillagerData(builder.getVillagerData().setLevel(5));
			int was = Pace.MAX_PERCENT;
			try {
				Pace.MAX_PERCENT = 120; // the cap (0.83) is above a well-kept village's 0.8
				helper.assertTrue(Pace.factor(builder) == 100f / 120f, "capped: " + Pace.factor(builder));
				helper.assertTrue(BuilderLevels.delay(120, builder) == 40, "a Master at the cap: " + BuilderLevels.delay(120, builder));
				String status = BuilderLevels.describe(builder).getString();
				helper.assertTrue(status.contains("150% faster than a novice") && status.endsWith(" · 20% faster: at the cap (a well-kept village)"),
					"status: " + status);
				hallLine(helper, helper.getLevel(), builder, "Works 20% faster: at the cap (a well-kept village)");
			} finally {
				Pace.MAX_PERCENT = was;
			}
			helper.succeed();
		});
	}

	/** Builders (and everyone timed by BuilderLevels.delay): the level's share, then the pace (ill: twice the time). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void buildersTakeTheirPaceFromPace(GameTestHelper helper) {
		Villager builder = worker(helper, ModVillagers.BUILDER);
		builder.setVillagerData(builder.getVillagerData().setLevel(3)); // 70%
		helper.assertTrue(BuilderLevels.delay(100, builder) == 70, "well: " + BuilderLevels.delay(100, builder));
		ill(helper, builder);
		helper.assertTrue(BuilderLevels.delay(100, builder) == 140, "ill: " + BuilderLevels.delay(100, builder));
		helper.assertTrue(BuilderLevels.describe(builder).getString().endsWith(" · 50% slower (held back by being ill)"),
			"status: " + BuilderLevels.describe(builder).getString());
		helper.succeed();
	}

	/** Miners: digging a block takes the level's share at their pace. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void minersTakeTheirPaceFromPace(GameTestHelper helper) {
		Villager miner = worker(helper, ModVillagers.MINER);
		helper.assertTrue(MinerWork.digTicks(miner, 30) == 30, "well: " + MinerWork.digTicks(miner, 30));
		ill(helper, miner);
		helper.assertTrue(MinerWork.digTicks(miner, 30) == 60, "ill: " + MinerWork.digTicks(miner, 30));
		helper.succeed();
	}

	/** Crafters: Craftsmanship is a named bonus in their pace (and only theirs), and sickness doubles the time on top. */
	//$ gametest_ticks_batch AREA '100' '"paceCrafter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "paceCrafter")
	public void craftersTakeTheirPaceFromPace(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager builder = keptVillage(helper, Map.of("craftsmanship", 2));
		helper.runAfterDelay(5, () -> {
			Villager mason = near(helper, VillagerProfession.MASON);
			keep(helper, 0.5f);
			// 10 crafts: 80 ticks, Craftsmanship II takes 30% off: 56
			helper.assertTrue(CrafterWork.craftTicks(mason, 10) == 56, "Craftsmanship II: " + CrafterWork.craftTicks(mason, 10));
			helper.assertTrue(Pace.describe(mason).getString().equals("43% faster (Craftsmanship)"), "mason: " + Pace.describe(mason).getString());
			helper.assertTrue(BuilderLevels.delay(100, builder) == 100, "a builder gets no Craftsmanship: " + BuilderLevels.delay(100, builder));
			ill(helper, mason);
			helper.assertTrue(CrafterWork.craftTicks(mason, 10) == 112, "ill: " + CrafterWork.craftTicks(mason, 10));
			helper.succeed();
		});
	}

	/** Explorers and netherworkers: searches, rests and trips at their pace, Expeditions included. */
	//$ gametest_ticks_batch AREA '100' '"paceExplorer"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "paceExplorer")
	public void explorersTakeTheirPaceFromPace(GameTestHelper helper) {
		Leftovers.clear(helper);
		keptVillage(helper, Map.of("expeditions", 2));
		helper.runAfterDelay(5, () -> {
			Villager explorer = near(helper, VillagerProfession.CARTOGRAPHER);
			Villager netherworker = near(helper, ModVillagers.NETHERWORKER);
			keep(helper, 0.5f);
			int search = ExplorerWork.SEARCH_TICKS;
			int rest = ExplorerWork.REST_TICKS;
			int trip = Netherworkers.TRIP_TICKS;
			int netherRest = NetherworkerWork.REST_TICKS;
			try {
				ExplorerWork.SEARCH_TICKS = 100;
				ExplorerWork.REST_TICKS = 1200;
				Netherworkers.TRIP_TICKS = 6000;
				NetherworkerWork.REST_TICKS = 1200;
				// Expeditions II: 40% off
				helper.assertTrue(ExplorerWork.searchTicks(explorer) == 60, "search: " + ExplorerWork.searchTicks(explorer));
				helper.assertTrue(ExplorerWork.restTicks(explorer) == 720, "rest: " + ExplorerWork.restTicks(explorer));
				helper.assertTrue(Netherworkers.tripTicks(netherworker) == 3600, "trip: " + Netherworkers.tripTicks(netherworker));
				helper.assertTrue(NetherworkerWork.restTicks(netherworker) == 720, "nether rest: " + NetherworkerWork.restTicks(netherworker));
				ill(helper, explorer);
				ill(helper, netherworker);
				helper.assertTrue(ExplorerWork.searchTicks(explorer) == 120, "ill search: " + ExplorerWork.searchTicks(explorer));
				helper.assertTrue(ExplorerWork.restTicks(explorer) == 1440, "ill rest: " + ExplorerWork.restTicks(explorer));
				helper.assertTrue(Netherworkers.tripTicks(netherworker) == 7200, "ill trip: " + Netherworkers.tripTicks(netherworker));
				helper.assertTrue(NetherworkerWork.restTicks(netherworker) == 1440, "ill nether rest: " + NetherworkerWork.restTicks(netherworker));
			} finally {
				ExplorerWork.SEARCH_TICKS = search;
				ExplorerWork.REST_TICKS = rest;
				Netherworkers.TRIP_TICKS = trip;
				NetherworkerWork.REST_TICKS = netherRest;
			}
			helper.succeed();
		});
	}

	/** Teachers: a round of class teaches at their pace (ill: half as much). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void teachersTakeTheirPaceFromPace(GameTestHelper helper) {
		Villager teacher = worker(helper, ModVillagers.TEACHER);
		helper.assertTrue(TeacherWork.lesson(teacher) == 20, "well: " + TeacherWork.lesson(teacher));
		ill(helper, teacher);
		helper.assertTrue(TeacherWork.lesson(teacher) == 10, "ill: " + TeacherWork.lesson(teacher));
		helper.succeed();
	}

	/** Scholars: research points at their pace (ill: half as many). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void scholarsTakeTheirPaceFromPace(GameTestHelper helper) {
		Villager scholar = worker(helper, ModVillagers.SCHOLAR);
		helper.assertTrue(ScholarWork.progress(scholar) == 20, "well: " + ScholarWork.progress(scholar));
		ill(helper, scholar);
		helper.assertTrue(ScholarWork.progress(scholar) == 10, "ill: " + ScholarWork.progress(scholar));
		helper.succeed();
	}

	/** Ranchers: a wild horse calms at their pace (ill: half as fast). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void ranchersTakeTheirPaceFromPace(GameTestHelper helper) {
		Villager rancher = worker(helper, ModVillagers.RANCHER);
		helper.assertTrue(RancherWork.calming(rancher) == 20, "well: " + RancherWork.calming(rancher));
		ill(helper, rancher);
		helper.assertTrue(RancherWork.calming(rancher) == 10, "ill: " + RancherWork.calming(rancher));
		helper.succeed();
	}

	// --- Helpers --------------------------------------------------------------------------------------------------

	/** A villager with {@code job} (not in the world, so nothing nearby changes its pace). */
	private static Villager worker(GameTestHelper helper, VillagerProfession job) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5, 2, 5)));
		v.setVillagerData(v.getVillagerData().setProfession(job));
		return v;
	}

	/** A villager with {@code job} standing in this test's village (not added to the world). */
	private static Villager near(GameTestHelper helper, VillagerProfession job) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(8, 2, 8)));
		v.setVillagerData(v.getVillagerData().setProfession(job));
		return v;
	}

	private static void ill(GameTestHelper helper, Villager villager) {
		ModAttachments.ILL_SINCE.set(villager, helper.getLevel().getGameTime());
	}

	/** A Village Hall with this research, a builder at a bench near it; the hall's first round runs in the next tick. */
	private static Villager keptVillage(GameTestHelper helper, Map<String, Integer> research) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageNeeds.forget();
			Research.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos bench = new BlockPos(5, 2, 5);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		builder.setNoAi(true);
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(helper.getLevel(), builder, helper.absolutePos(bench),
			ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		helper.runAfterDelay(1, () -> {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity hall) {
				hall.setResearch(new Research.State(research, Optional.empty(), 0, false));
			}
		});
		return builder;
	}

	/** The village kept 100% (everyone fed, housed, safe and lit) until the hall's next round. */
	private static void keepWell(GameTestHelper helper) {
		keep(helper, 1f);
	}

	/** The village kept {@code wellbeing} until the hall's next round (0.5: the usual pace). */
	private static void keep(GameTestHelper helper, float wellbeing) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, wellbeing));
		VillageNeeds.forget();
		Research.forget();
		Moods.forget();
	}

	/** A diligent, cheerful copy of {@code builder} (same job and bench), fed, with a bed: happy. Traits and moods on. */
	private static Villager diligentAndHappy(GameTestHelper helper, Villager builder) {
		boolean traits = Traits.ENABLED;
		boolean moods = Moods.ENABLED;
		Traits.ENABLED = true;
		Moods.ENABLED = true;
		Leftovers.after(helper, () -> {
			Traits.ENABLED = traits;
			Moods.ENABLED = moods;
		});
		ServerLevel level = helper.getLevel();
		Villager v = EntityType.VILLAGER.create(level);
		v.setUUID(withTraits(Set.of(Trait.DILIGENT, Trait.CHEERFUL)));
		v.moveTo(builder.position());
		v.setVillagerData(builder.getVillagerData());
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, builder.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow());
		v.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(3, 2, 3))));
		ModAttachments.LAST_MEAL.set(v, level.getGameTime());
		Moods.Mood mood = Moods.of(v);
		helper.assertTrue(mood != null && mood.score() >= Moods.HAPPY, "not happy: " + mood);
		return v;
	}

	/** A UUID whose owner has exactly {@code traits} (in any order). */
	static UUID withTraits(Set<Trait> traits) {
		RandomSource random = RandomSource.create(traits.hashCode());
		for (int i = 0; i < 200000; i++) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			if (Set.copyOf(Traits.of(id)).equals(traits)) {
				return id;
			}
		}
		throw new IllegalStateException("nobody is " + traits);
	}

	/** The hall's list shows {@code villager}'s pace line, starting {@code start}. */
	private static void hallLine(GameTestHelper helper, ServerLevel level, Villager villager, String start) {
		ItemLore lore = VillageHallScreen.person(level, helper.absolutePos(HALL), villager).get(DataComponents.LORE);
		helper.assertTrue(lore != null && lore.lines().stream().map(Component::getString).anyMatch(l -> l.startsWith(start)),
			"hall list: " + (lore == null ? "none" : lore.lines().stream().map(Component::getString).toList()));
	}
}

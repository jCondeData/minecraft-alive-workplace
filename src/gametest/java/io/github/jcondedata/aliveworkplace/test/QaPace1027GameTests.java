package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.explore.ExplorerWork;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
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
import io.github.jcondedata.aliveworkplace.sift.SifterWork;
import io.github.jcondedata.aliveworkplace.work.Pace;
import io.github.jcondedata.aliveworkplace.work.Partners;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.component.ItemLore;

/**
 * QA for ROADMAP 30.2 ("One pace, one cap"), written from the spec and docs/design/M30.md's pace rule:
 * {@code time = base × levelShare × max(100 / maxWorkPace, Π bonuses) × Π penalties}, the status reading
 * {@code round((1 / factor - 1) × 100)} percent and "at the cap" when the bonuses hit it.
 *
 * Big bonuses use a stand-in: a named source (the Legend's, or the partners' with two names) swapped for one test's
 * own villagers only, synchronously, and put back in a finally block, as {@code TonicGameTests} does; Pokémon and
 * Legends need their mods or set-ups. Every other villager reads the real source.
 */
public class QaPace1027GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final float EPSILON = 1e-5f;
	private static final int[] CAPS = {100, 200, 300, 400};

	/**
	 * A bonus far past every cap (a tenth of the time) is held at exactly 100 / maxWorkPace at each of 100, 200, 300 and
	 * 400; a novice builder's 300-tick step takes 300, 150, 100 and 75; the status says "at the cap" each time, and at 100
	 * the worker is at the usual pace.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEachCapHoldsAHugeBonusExactly(GameTestHelper helper) {
		Villager builder = worker(helper, ModVillagers.BUILDER);
		int[] delays = {300, 150, 100, 75};
		String[] totals = {"at the usual pace", "100% faster", "200% faster", "300% faster"};
		int was = Pace.MAX_PERCENT;
		try {
			withBonus(Pace.LEGEND, Set.of(builder), 0.1f, () -> {
				for (int i = 0; i < CAPS.length; i++) {
					Pace.MAX_PERCENT = CAPS[i];
					Pace.Breakdown pace = Pace.of(builder);
					helper.assertTrue(pace.factor() == 100f / CAPS[i] && pace.capped(), CAPS[i] + ": " + pace);
					helper.assertTrue(BuilderLevels.delay(300, builder) == delays[i], CAPS[i] + " delay: " + BuilderLevels.delay(300, builder));
					String line = Pace.describe(builder).getString();
					helper.assertTrue(line.equals(totals[i] + ": at the cap (a Legend nearby)"), CAPS[i] + " status: " + line);
					String status = BuilderLevels.describe(builder).getString();
					helper.assertTrue(status.endsWith(" · " + totals[i] + ": at the cap (a Legend nearby)"), CAPS[i] + " sneak status: " + status);
				}
			});
		} finally {
			Pace.MAX_PERCENT = was;
		}
		helper.succeed();
	}

	/**
	 * {@code maxWorkPace}: the bounds 100 and 400 are kept, anything outside (99, 401, negative, absurdly large) goes to
	 * the nearest bound, a missing one takes the default 200, an in-between value (250) is kept and gives a cap of
	 * 0.4; the settings screen's range is 100 to 400.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheConfigKeepsItsBoundsAndClampsBothWays(GameTestHelper helper) {
		String[] given = {"100", "400", "99", "401", "-50", "99999999999", "250"};
		int[] wanted = {100, 400, 100, 400, 100, 400, 250};
		for (int i = 0; i < given.length; i++) {
			int got = WorkplaceConfig.parse("{\"maxWorkPace\": " + given[i] + "}").maxWorkPace;
			helper.assertTrue(got == wanted[i], "maxWorkPace " + given[i] + " read as " + got + ", wanted " + wanted[i]);
		}
		helper.assertTrue(WorkplaceConfig.parse("{}").maxWorkPace == 200, "missing: " + WorkplaceConfig.parse("{}").maxWorkPace);
		WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get("maxWorkPace");
		helper.assertTrue(range != null && range.min() == 100 && range.max() == 400, "range: " + range);
		int was = Pace.MAX_PERCENT;
		try {
			Pace.MAX_PERCENT = 250;
			helper.assertTrue(Pace.cap() == 0.4f, "250: " + Pace.cap());
			Pace.MAX_PERCENT = Integer.MIN_VALUE;
			helper.assertTrue(Pace.cap() == 1f, "far under: " + Pace.cap());
		} finally {
			Pace.MAX_PERCENT = was;
		}
		helper.succeed();
	}

	/**
	 * The same capped worker, ill, takes exactly twice the capped time at every cap (half the pace): 600, 300, 200 and 150
	 * of 300 ticks; at the default it is back at the usual pace, still "at the cap", held back by being ill.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAnIllCappedWorkerIsAtExactlyHalfTheCappedPace(GameTestHelper helper) {
		Villager builder = worker(helper, ModVillagers.BUILDER);
		ill(helper, builder);
		int[] delays = {600, 300, 200, 150};
		int was = Pace.MAX_PERCENT;
		try {
			withBonus(Pace.LEGEND, Set.of(builder), 0.1f, () -> {
				for (int i = 0; i < CAPS.length; i++) {
					Pace.MAX_PERCENT = CAPS[i];
					Pace.Breakdown pace = Pace.of(builder);
					helper.assertTrue(Math.abs(pace.factor() - 2f * 100f / CAPS[i]) < EPSILON && pace.capped(), CAPS[i] + " ill: " + pace);
					helper.assertTrue(BuilderLevels.delay(300, builder) == delays[i], CAPS[i] + " ill delay: " + BuilderLevels.delay(300, builder));
				}
				Pace.MAX_PERCENT = 200;
				String line = Pace.describe(builder).getString();
				helper.assertTrue(line.equals("at the usual pace: at the cap (a Legend nearby; held back by being ill)"), "ill status: " + line);
				Pace.MAX_PERCENT = 400;
				line = Pace.describe(builder).getString();
				helper.assertTrue(line.equals("100% faster: at the cap (a Legend nearby; held back by being ill)"), "ill status at 400: " + line);
			});
		} finally {
			Pace.MAX_PERCENT = was;
		}
		helper.succeed();
	}

	/**
	 * A single bonus under the cap is its own factor, not clipped and not "at the cap" (0.8: "25% faster"; 1/1.62: "62%
	 * faster"); a bonus exactly at the cap is "at the cap"; the same 62% bonus under a lower cap (160) is clipped to it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaABonusUnderTheCapIsNotClippedAndOneAtItIs(GameTestHelper helper) {
		Villager builder = worker(helper, ModVillagers.BUILDER);
		int was = Pace.MAX_PERCENT;
		try {
			Pace.MAX_PERCENT = 200;
			withBonus(Pace.LEGEND, Set.of(builder), 0.8f, () -> {
				Pace.Breakdown pace = Pace.of(builder);
				helper.assertTrue(pace.factor() == 0.8f && !pace.capped(), "0.8: " + pace);
				helper.assertTrue(Pace.describe(builder).getString().equals("25% faster (a Legend nearby)"), "0.8 status: " + Pace.describe(builder).getString());
				helper.assertTrue(BuilderLevels.delay(100, builder) == 80, "0.8 delay: " + BuilderLevels.delay(100, builder));
			});
			withBonus(Pace.LEGEND, Set.of(builder), 1f / 1.62f, () -> {
				helper.assertTrue(!Pace.of(builder).capped(), "62%: " + Pace.of(builder));
				helper.assertTrue(Pace.describe(builder).getString().equals("62% faster (a Legend nearby)"), "62% status: " + Pace.describe(builder).getString());
				Pace.MAX_PERCENT = 160; // a cap of 0.625, above 1/1.62
				helper.assertTrue(Pace.factor(builder) == 0.625f && Pace.of(builder).capped(), "62% at 160: " + Pace.of(builder));
				helper.assertTrue(Pace.describe(builder).getString().equals("60% faster: at the cap (a Legend nearby)"),
					"62% at 160 status: " + Pace.describe(builder).getString());
				Pace.MAX_PERCENT = 200;
			});
			withBonus(Pace.LEGEND, Set.of(builder), 0.5f, () -> {
				Pace.Breakdown pace = Pace.of(builder);
				helper.assertTrue(pace.factor() == 0.5f && pace.capped(), "exactly at the cap: " + pace);
				helper.assertTrue(Pace.describe(builder).getString().equals("100% faster: at the cap (a Legend nearby)"),
					"exactly at the cap status: " + Pace.describe(builder).getString());
			});
		} finally {
			Pace.MAX_PERCENT = was;
		}
		helper.succeed();
	}

	/**
	 * Lazy alone is a penalty, never "at the cap" ("9% slower (held back by being lazy)"); with illness it multiplies
	 * (2.2, "55% slower"); with a huge bonus both multiply the capped half (1.1, "9% slower: at the cap").
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaLazyAndIllStackAfterTheCap(GameTestHelper helper) {
		boolean traits = Traits.ENABLED;
		int was = Pace.MAX_PERCENT;
		Traits.ENABLED = true;
		try {
			Pace.MAX_PERCENT = 200;
			Villager lazy = worker(helper, ModVillagers.BUILDER);
			lazy.setUUID(PaceGameTests.withTraits(Set.of(Trait.LAZY)));
			Pace.Breakdown pace = Pace.of(lazy);
			helper.assertTrue(Math.abs(pace.factor() - 1.1f) < EPSILON && !pace.capped(), "lazy: " + pace);
			helper.assertTrue(Pace.describe(lazy).getString().equals("9% slower (held back by being lazy)"), "lazy status: " + Pace.describe(lazy).getString());
			ill(helper, lazy);
			helper.assertTrue(Math.abs(Pace.factor(lazy) - 2.2f) < EPSILON, "lazy and ill: " + Pace.of(lazy));
			helper.assertTrue(Pace.describe(lazy).getString().equals("55% slower (held back by being ill, being lazy)"),
				"lazy and ill status: " + Pace.describe(lazy).getString());
			withBonus(Pace.LEGEND, Set.of(lazy), 0.1f, () -> {
				helper.assertTrue(Math.abs(Pace.factor(lazy) - 1.1f) < EPSILON && Pace.of(lazy).capped(), "capped, lazy and ill: " + Pace.of(lazy));
				helper.assertTrue(BuilderLevels.delay(1000, lazy) == 1100, "capped, lazy and ill delay: " + BuilderLevels.delay(1000, lazy));
				helper.assertTrue(Pace.describe(lazy).getString().equals("9% slower: at the cap (a Legend nearby; held back by being ill, being lazy)"),
					"capped, lazy and ill status: " + Pace.describe(lazy).getString());
			});
		} finally {
			Traits.ENABLED = traits;
			Pace.MAX_PERCENT = was;
		}
		helper.succeed();
	}

	/**
	 * In a badly kept village (wellbeing 0), a hungry, bedless, lazy, ill worker with a bonus past the cap: every penalty
	 * multiplies the capped half (0.5 × 2 × 1.15 × 1.1 × 1.25), and the hall's list names each one.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPace1027Penalties"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPace1027Penalties")
	public void qaEveryPenaltyMultipliesTheCappedPaceInAVillage(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager builder = village(helper);
		helper.runAfterDelay(5, () -> {
			boolean traits = Traits.ENABLED;
			boolean moods = Moods.ENABLED;
			int was = Pace.MAX_PERCENT;
			Traits.ENABLED = true;
			Moods.ENABLED = true;
			try {
				Pace.MAX_PERCENT = 200;
				keep(helper, 0f);
				ServerLevel level = helper.getLevel();
				Villager v = copy(helper, builder, Set.of(Trait.LAZY));
				ModAttachments.LAST_MEAL.set(v, level.getGameTime() - 10L * 24000L); // hungry, and no bed
				ill(helper, v);
				Moods.forget();
				Moods.Mood mood = Moods.of(v);
				helper.assertTrue(mood != null && mood.score() < Moods.UNHAPPY, "not unhappy: " + mood);
				withBonus(Pace.LEGEND, Set.of(v), 0.1f, () -> {
					Pace.Breakdown pace = Pace.of(v);
					float wanted = 0.5f * 2f * 1.15f * 1.1f * 1.25f;
					helper.assertTrue(pace.capped() && Math.abs(pace.factor() - wanted) < EPSILON, "every penalty: " + pace.factor() + " vs " + wanted + " " + pace);
					helper.assertTrue(pace.slower().size() == 4, "penalties: " + pace.slower().stream().map(p -> p.source().id()).toList());
					String line = Pace.describe(v).getString();
					String expected = "37% slower: at the cap (a Legend nearby; held back by being ill, an unhappy mood, being lazy, a badly kept village)";
					helper.assertTrue(line.equals(expected), "status: " + line);
					hallLine(helper, level, v, "Works " + expected);
				});
			} finally {
				Traits.ENABLED = traits;
				Moods.ENABLED = moods;
				Pace.MAX_PERCENT = was;
			}
			helper.succeed();
		});
	}

	/**
	 * A content worker in a village kept at the usual pace (wellbeing 0.5, no research) has nothing changing their pace:
	 * factor 1, no pace part in the sneak-right-click status, no "Works ..." line in the hall's list.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPace1027Neutral"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPace1027Neutral")
	public void qaANeutralWorkerReadsNoPaceLine(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager builder = village(helper);
		helper.runAfterDelay(5, () -> {
			boolean traits = Traits.ENABLED;
			boolean moods = Moods.ENABLED;
			Traits.ENABLED = true;
			Moods.ENABLED = true;
			try {
				keep(helper, 0.5f);
				Villager v = copy(helper, builder, Set.of(Trait.NIMBLE)); // walking speed is not work pace
				Moods.Mood mood = Moods.of(v);
				helper.assertTrue(mood != null && mood.score() >= Moods.UNHAPPY && mood.score() < Moods.HAPPY, "not content: " + mood);
				Pace.Breakdown pace = Pace.of(v);
				helper.assertTrue(pace.factor() == 1f && pace.isEmpty() && !pace.capped(), "neutral: " + pace);
				helper.assertTrue(Pace.describe(v) == null, "a pace line: " + Pace.describe(v));
				String status = BuilderLevels.describe(v).getString();
				// The level's own "0% faster than a novice" ends the line: no " · N% faster/slower (...)" pace part after it.
				helper.assertTrue(status.endsWith("faster than a novice") && !status.contains("slower") && !status.contains("usual pace") && !status.contains("cap"),
					"status: " + status);
				ItemLore lore = VillageHallScreen.person(helper.getLevel(), helper.absolutePos(HALL), v).get(DataComponents.LORE);
				List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
				helper.assertTrue(lines.stream().noneMatch(l -> l.startsWith("Works ")), "hall list: " + lines);
			} finally {
				Traits.ENABLED = traits;
				Moods.ENABLED = moods;
			}
			helper.succeed();
		});
	}

	/**
	 * Two partners (0.7 between them) counted once: a sifter's 60-tick sift takes 42 (70%, not 49%), an explorer's
	 * 100-tick search 70; the status names them, "43% faster (Machop and Geodude from the pasture)". (Without Cobblemon
	 * the partners' own factor is 1, so the stand-in is the only partner input.)
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTwoPartnersCountOnceForSiftsAndSearches(GameTestHelper helper) {
		Villager sifter = worker(helper, ModVillagers.SIFTER);
		Villager explorer = worker(helper, VillagerProfession.CARTOGRAPHER);
		helper.assertTrue(Partners.factor(sifter) == 1f && SifterWork.siftTicks(sifter) == 60, "no partners: " + SifterWork.siftTicks(sifter));
		int search = ExplorerWork.SEARCH_TICKS;
		int was = Pace.MAX_PERCENT;
		try {
			Pace.MAX_PERCENT = 200;
			ExplorerWork.SEARCH_TICKS = 100;
			Pace.Source partners = new Pace.Source(Pace.PARTNERS.id(), Pace.Kind.BONUS,
				v -> v == sifter || v == explorer ? 1f - 2 * Partners.PER_PARTNER : Pace.PARTNERS.factor().of(v),
				v -> v == sifter || v == explorer ? Component.translatable("pace.aliveworkplace.source.partners", "Machop and Geodude")
					: Pace.PARTNERS.label().of(v));
			Pace.register(partners);
			try {
				helper.assertTrue(SifterWork.siftTicks(sifter) == 42, "sifter with two partners: " + SifterWork.siftTicks(sifter));
				helper.assertTrue(ExplorerWork.searchTicks(explorer) == 70, "explorer search with two partners: " + ExplorerWork.searchTicks(explorer));
				String line = Pace.describe(sifter).getString();
				helper.assertTrue(line.equals("43% faster (Machop and Geodude from the pasture)"), "sifter status: " + line);
			} finally {
				Pace.register(Pace.PARTNERS);
			}
			helper.assertTrue(Pace.sources().stream().filter(s -> s.id().equals("partners")).allMatch(s -> s == Pace.PARTNERS), "partners not put back");
		} finally {
			ExplorerWork.SEARCH_TICKS = search;
			Pace.MAX_PERCENT = was;
		}
		helper.succeed();
	}

	/**
	 * Work timed without the level (lessons, research, calming, rests and trips) obeys the same cap: with a huge bonus a
	 * teacher, scholar and rancher make 40 instead of 20 at the default, 80 at 400 and 20 at 100; an explorer's rest and a
	 * netherworker's trip and rest take half, a quarter, and all of their time.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaLessonsResearchRestsAndTripsObeyTheCap(GameTestHelper helper) {
		Villager teacher = worker(helper, ModVillagers.TEACHER);
		Villager scholar = worker(helper, ModVillagers.SCHOLAR);
		Villager rancher = worker(helper, ModVillagers.RANCHER);
		Villager explorer = worker(helper, VillagerProfession.CARTOGRAPHER);
		Villager netherworker = worker(helper, ModVillagers.NETHERWORKER);
		int rest = ExplorerWork.REST_TICKS;
		int trip = Netherworkers.TRIP_TICKS;
		int netherRest = NetherworkerWork.REST_TICKS;
		int was = Pace.MAX_PERCENT;
		try {
			ExplorerWork.REST_TICKS = 1200;
			Netherworkers.TRIP_TICKS = 6000;
			NetherworkerWork.REST_TICKS = 1200;
			int[] progress = {20, 40, 60, 80};
			int[] rests = {1200, 600, 400, 300};
			int[] trips = {6000, 3000, 2000, 1500};
			withBonus(Pace.LEGEND, Set.of(teacher, scholar, rancher, explorer, netherworker), 0.1f, () -> {
				for (int i = 0; i < CAPS.length; i++) {
					Pace.MAX_PERCENT = CAPS[i];
					String at = CAPS[i] + ": ";
					helper.assertTrue(TeacherWork.lesson(teacher) == progress[i], at + "lesson " + TeacherWork.lesson(teacher));
					helper.assertTrue(ScholarWork.progress(scholar) == progress[i], at + "research " + ScholarWork.progress(scholar));
					helper.assertTrue(RancherWork.calming(rancher) == progress[i], at + "calming " + RancherWork.calming(rancher));
					helper.assertTrue(ExplorerWork.restTicks(explorer) == rests[i], at + "explorer rest " + ExplorerWork.restTicks(explorer));
					helper.assertTrue(NetherworkerWork.restTicks(netherworker) == rests[i], at + "nether rest " + NetherworkerWork.restTicks(netherworker));
					helper.assertTrue(Netherworkers.tripTicks(netherworker) == trips[i], at + "trip " + Netherworkers.tripTicks(netherworker));
				}
			});
		} finally {
			ExplorerWork.REST_TICKS = rest;
			Netherworkers.TRIP_TICKS = trip;
			NetherworkerWork.REST_TICKS = netherRest;
			Pace.MAX_PERCENT = was;
		}
		helper.succeed();
	}

	/**
	 * The level's share is outside the cap at every level: at maxWorkPace 400 a capped builder's 400-tick step takes 100,
	 * 85, 70, 55 and 40 from Novice to Master; an ill capped Master at the default takes 160 (400 × 0.4 × 0.5 × 2), and the
	 * pace part of the status leaves the level out.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheLevelMultipliesOutsideTheCapAtEveryLevel(GameTestHelper helper) {
		Villager builder = worker(helper, ModVillagers.BUILDER);
		int[] wanted = {100, 85, 70, 55, 40};
		int was = Pace.MAX_PERCENT;
		try {
			withBonus(Pace.LEGEND, Set.of(builder), 0.1f, () -> {
				Pace.MAX_PERCENT = 400;
				for (int lvl = 1; lvl <= 5; lvl++) {
					builder.setVillagerData(builder.getVillagerData().setLevel(lvl));
					helper.assertTrue(BuilderLevels.delay(400, builder) == wanted[lvl - 1], "level " + lvl + ": " + BuilderLevels.delay(400, builder));
					helper.assertTrue(Pace.describe(builder).getString().equals("300% faster: at the cap (a Legend nearby)"),
						"level " + lvl + " pace: " + Pace.describe(builder).getString());
				}
				Pace.MAX_PERCENT = 200;
				ill(helper, builder);
				helper.assertTrue(BuilderLevels.delay(400, builder) == 160, "ill Master: " + BuilderLevels.delay(400, builder));
			});
		} finally {
			Pace.MAX_PERCENT = was;
		}
		helper.succeed();
	}

	// --- Helpers --------------------------------------------------------------------------------------------------

	/**
	 * Runs {@code body} with {@code original} giving {@code factor} to {@code who} only (everyone else reads the real
	 * source), then puts the real source back.
	 */
	private static void withBonus(Pace.Source original, Set<Villager> who, float factor, Runnable body) {
		Pace.register(new Pace.Source(original.id(), original.kind(),
			v -> who.contains(v) ? factor : original.factor().of(v), original.label()));
		try {
			body.run();
		} finally {
			Pace.register(original);
		}
	}

	/**
	 * A villager with {@code job} and no village (not added to the world): it stands 200 blocks above the test, out of
	 * reach of every Village Hall another test places, whose upkeep, mood and edicts are real pace sources.
	 */
	private static Villager worker(GameTestHelper helper, VillagerProfession job) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(5, 200, 5)));
		helper.assertTrue(VillageHalls.nearest(helper.getLevel(), v.blockPosition()).isEmpty(), "a Village Hall in reach of " + v.blockPosition());
		v.setVillagerData(v.getVillagerData().setProfession(job));
		return v;
	}

	private static void ill(GameTestHelper helper, Villager villager) {
		ModAttachments.ILL_SINCE.set(villager, helper.getLevel().getGameTime());
	}

	/** A Village Hall with no research and a builder employed at a bench near it (villager at y = 2). */
	private static Villager village(GameTestHelper helper) {
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
		return builder;
	}

	/** The village kept {@code wellbeing} until the hall's next round (0.5: the usual pace, 0: the worst). */
	private static void keep(GameTestHelper helper, float wellbeing) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, wellbeing));
		VillageNeeds.forget();
		Research.forget();
		Moods.forget();
	}

	/** A copy of {@code builder} (same job and bench, not in the world) with exactly {@code traits}. */
	private static Villager copy(GameTestHelper helper, Villager builder, Set<Trait> traits) {
		Villager v = EntityType.VILLAGER.create(helper.getLevel());
		v.setUUID(PaceGameTests.withTraits(traits));
		v.moveTo(builder.position());
		v.setVillagerData(builder.getVillagerData());
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, builder.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElseThrow());
		return v;
	}

	private static void hallLine(GameTestHelper helper, ServerLevel level, Villager villager, String expected) {
		ItemLore lore = VillageHallScreen.person(level, helper.absolutePos(HALL), villager).get(DataComponents.LORE);
		List<String> lines = lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
		helper.assertTrue(lines.contains(expected), "hall list: " + lines);
	}
}

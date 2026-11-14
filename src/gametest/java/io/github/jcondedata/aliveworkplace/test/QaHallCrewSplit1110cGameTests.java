package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA for B1110 (written from the spec): a job chosen on the Village Hall while every builder is busy splits a crew. With
 * two busy builds, one led by a lone builder and one with a lead and helper(s), the new job takes a helper, never the
 * lone builder (even when the lone builder stands nearer); the moved builder really works the new job while both old
 * builds go on; splits in a row never leave a build with nobody on it, and once only lone builders are left the request
 * waits and says so plainly; the refusal texts word for word.
 */
public class QaHallCrewSplit1110cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation HUT_2 = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut_2");

	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	// finished huts to upgrade (5x4x5 each), along the west side
	private static final BlockPos H1 = new BlockPos(2, 2, 2);
	private static final BlockPos H2 = new BlockPos(2, 2, 9);
	private static final BlockPos H3 = new BlockPos(2, 2, 16);
	// a finished wall to repair (4 long), south-west
	private static final BlockPos WALL = new BlockPos(2, 2, 25);
	// build A (lead + helpers) north-east, build B (one builder only) south-east
	private static final BlockPos SITE_A = new BlockPos(22, 2, 2);
	private static final BlockPos SITE_B = new BlockPos(22, 2, 22);
	// A's lead and helpers' benches, along x 18
	private static final BlockPos A_LEAD_BENCH = new BlockPos(18, 2, 2);
	private static final BlockPos A_LEAD_CHEST = new BlockPos(18, 2, 4);
	private static final BlockPos A_LEAD = new BlockPos(17, 2, 3);
	private static final BlockPos[] A_HELP_BENCH = {new BlockPos(18, 2, 7), new BlockPos(18, 2, 11)};
	private static final BlockPos[] A_HELP_CHEST = {new BlockPos(18, 2, 9), new BlockPos(18, 2, 13)};
	private static final BlockPos[] A_HELP = {new BlockPos(17, 2, 8), new BlockPos(17, 2, 12)};
	// B's lone builder: his bench right by the huts and the wall, so he is the nearest builder to every job
	private static final BlockPos B_LEAD_BENCH = new BlockPos(9, 2, 4);
	private static final BlockPos B_LEAD_CHEST = new BlockPos(9, 2, 6);
	private static final BlockPos B_LEAD = new BlockPos(10, 2, 5);

	private record Village(ServerLevel level, ServerPlayer player, Villager aLead, List<Villager> aHelpers, Villager bLead, UUID a, UUID b,
						   BlueprintData.Placement aAt, BlueprintData.Placement bAt) {
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	private static Villager builder(GameTestHelper helper, ServerPlayer player, BlockPos bench, BlockPos chest, BlockPos at) {
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chest, Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(v, new Employer(player.getUUID(), player.getGameProfile().getName()));
		return v;
	}

	private static void stock(GameTestHelper helper, BuildSite site, BlockPos chestPos) {
		BuildPlan plan = site.plan(helper.getLevel());
		helper.assertTrue(plan != null, "no test hut blueprint");
		Container chest = helper.getBlockEntity(chestPos);
		int slot = 0;
		for (Map.Entry<Item, Integer> need : plan.materials().entrySet()) {
			chest.setItem(slot++, new ItemStack(need.getKey(), Math.min(64, need.getValue())));
		}
	}

	private static void finishedHut(GameTestHelper helper, BlockPos rel, UUID owner, List<BlueprintData.Placement> jobs) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(rel);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement p = placement(helper, rel);
		BuildSiteManager.get(level).recordFinished(HUT, p, owner);
		jobs.add(p);
	}

	/**
	 * The village: build A with its lead and {@code helpers} helpers (each with two lanterns by its own bench, for a
	 * hut's level II), build B with one builder only, standing nearest to every job; the given huts finished.
	 */
	private static Village village(GameTestHelper helper, int helpers, List<BlueprintData.Placement> jobs, BlockPos... huts) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean helps = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		VillageHalls.RADIUS = 24;
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement aAt = placement(helper, SITE_A);
		BlueprintData.Placement bAt = placement(helper, SITE_B);
		List<BlueprintData.Placement> mine = new ArrayList<>(List.of(aAt, bAt));
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(helps, level.getServer());
			for (BuildSite site : List.copyOf(sites.all())) {
				if (mine.contains(site.placement())) {
					sites.remove(site.id());
				}
			}
			for (BlueprintData.Placement p : mine) {
				sites.forgetFinished(p);
			}
			level.getServer().getPlayerList().remove(player);
		});
		for (BlockPos hut : huts) {
			finishedHut(helper, hut, player.getUUID(), jobs);
		}
		mine.addAll(jobs);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager aLead = builder(helper, player, A_LEAD_BENCH, A_LEAD_CHEST, A_LEAD);
		BuildSite a = Builders.start(level, aLead, player, HUT, aAt);
		stock(helper, a, A_LEAD_CHEST);
		Villager bLead = builder(helper, player, B_LEAD_BENCH, B_LEAD_CHEST, B_LEAD);
		BuildSite b = Builders.start(level, bLead, player, HUT, bAt);
		stock(helper, b, B_LEAD_CHEST);
		List<Villager> mates = new ArrayList<>();
		for (int i = 0; i < helpers; i++) {
			Villager mate = builder(helper, player, A_HELP_BENCH[i], A_HELP_CHEST[i], A_HELP[i]);
			ModAttachments.BUILDER_JOB.set(mate, new BuilderJob(a.id(), true)); // joins A's crew, as recruit does
			a.seen(mate.getUUID(), level.getGameTime());
			Container chest = helper.getBlockEntity(A_HELP_CHEST[i]);
			chest.setItem(0, new ItemStack(Items.LANTERN, 2));
			mates.add(mate);
		}
		return new Village(level, player, aLead, mates, bLead, a.id(), b.id(), aAt, bAt);
	}

	private static Optional<BuildSite> siteOn(ServerLevel level, BlueprintData.Placement placement) {
		return BuildSiteManager.get(level).all().stream().filter(s -> s.placement().equals(placement) && !s.isDone()).findFirst();
	}

	/** {@code who} still leads the site {@code id}: the site is open and not queued, and his own job is that site, as lead. */
	private static void assertLeads(GameTestHelper helper, ServerLevel level, Villager who, UUID id, String what) {
		BuildSite site = BuildSiteManager.get(level).get(id);
		helper.assertTrue(site != null && !site.isQueued() && who.getUUID().equals(site.builder()),
			what + " lost its builder: " + (site == null ? "gone" : site.builder() + (site.isQueued() ? " (queued)" : "")));
		BuilderJob job = ModAttachments.BUILDER_JOB.get(who);
		helper.assertTrue(job != null && !job.helper() && job.siteId().equals(id), what + "'s builder was moved: " + job);
	}

	/** The open site on {@code placement} is led (not queued) by {@code who}, whose job it is. */
	private static BuildSite assertWorks(GameTestHelper helper, ServerLevel level, Villager who, BlueprintData.Placement placement, String what) {
		BuildSite site = siteOn(level, placement).orElse(null);
		helper.assertTrue(site != null && !site.isQueued() && who.getUUID().equals(site.builder()),
			what + " isn't led by the moved helper: " + (site == null ? "no site" : site.builder() + (site.isQueued() ? " (queued)" : "")));
		BuilderJob job = ModAttachments.BUILDER_JOB.get(who);
		helper.assertTrue(job != null && !job.helper() && job.siteId().equals(site.id()), what + ": the moved helper's job: " + job);
		return site;
	}

	/** A site goes on: some of it built, or finished and recorded. */
	private static void assertGoesOn(GameTestHelper helper, ServerLevel level, UUID id, ResourceLocation structure, BlueprintData.Placement at, String what) {
		BuildSite site = BuildSiteManager.get(level).get(id);
		if (site == null) {
			helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream()
				.anyMatch(f -> f.structure().equals(structure) && f.placement().equals(at)), what + " is gone but not finished");
			return;
		}
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null && site.progress(plan) > 0, what + " hasn't gone on: " + site.stage() + " " + (plan == null ? "no plan" : site.progress(plan)));
	}

	/**
	 * B is the lone builder's and he stands nearest the hut; A has a lead and a helper. Picking the hut's upgrade on the
	 * Upgrades page takes A's helper, never B's only builder; the helper then builds the level II while A and B both go on.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaSplit1110cLone"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaSplit1110cLone")
	public void theSplitTakesTheHelperNeverTheLoneBuilderEvenWhenNearer(GameTestHelper helper) {
		List<BlueprintData.Placement> jobs = new ArrayList<>();
		Village v = village(helper, 1, jobs, H1);
		BlueprintData.Placement hut = jobs.get(0);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = VillageHallScreen.forTest(v.player(), helper.absolutePos(HALL));
			int tab = HallPages.slot(UpgradesPage.PAGE);
			helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the upgrades page has no tab: " + tab);
			menu.press(tab, v.player());
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.BRICKS), "the hut's row: " + menu.icon(VillageHallScreen.FIRST_ROW));
			menu.press(VillageHallScreen.FIRST_ROW, v.player());
			assertLeads(helper, v.level(), v.bLead(), v.b(), "B (one builder only)");
			assertLeads(helper, v.level(), v.aLead(), v.a(), "A");
			BuildSite job = assertWorks(helper, v.level(), v.aHelpers().get(0), hut, "the upgrade");
			helper.assertTrue(job.structure().equals(HUT_2), "the job isn't the hut's level II: " + job.structure());
			UUID jobId = job.id();
			helper.succeedWhen(() -> {
				assertGoesOn(helper, v.level(), jobId, HUT_2, hut, "the upgrade");
				assertGoesOn(helper, v.level(), v.a(), HUT, v.aAt(), "A");
				assertGoesOn(helper, v.level(), v.b(), HUT, v.bAt(), "B");
				BuilderJob bJob = ModAttachments.BUILDER_JOB.get(v.bLead());
				helper.assertTrue(BuildSiteManager.get(v.level()).get(v.b()) == null || (bJob != null && bJob.siteId().equals(v.b())), "B's builder left B: " + bJob);
			});
		});
	}

	/**
	 * A has a lead and two helpers, B one builder only. Two upgrades in a row each take one of A's helpers; the third,
	 * with only lone builders left, splits nobody: it waits in a queue and every build still has its builder.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaSplit1110cRow"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaSplit1110cRow")
	public void splitsInARowNeverLeaveABuildWithNobody(GameTestHelper helper) {
		List<BlueprintData.Placement> jobs = new ArrayList<>();
		Village v = village(helper, 2, jobs, H1, H2, H3);
		helper.runAfterDelay(2, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			UpgradesPage.Outcome first = UpgradesPage.order(v.level(), hall, v.player(), jobs.get(0));
			helper.assertTrue(first == UpgradesPage.Outcome.SPLIT, "the first upgrade: " + first);
			UpgradesPage.Outcome second = UpgradesPage.order(v.level(), hall, v.player(), jobs.get(1));
			helper.assertTrue(second == UpgradesPage.Outcome.SPLIT, "the second upgrade: " + second);
			UpgradesPage.Outcome third = UpgradesPage.order(v.level(), hall, v.player(), jobs.get(2));
			helper.assertTrue(third == UpgradesPage.Outcome.QUEUED, "the third upgrade, only lone builders left: " + third);
			assertLeads(helper, v.level(), v.aLead(), v.a(), "A");
			assertLeads(helper, v.level(), v.bLead(), v.b(), "B");
			BuildSite one = siteOn(v.level(), jobs.get(0)).orElse(null);
			BuildSite two = siteOn(v.level(), jobs.get(1)).orElse(null);
			helper.assertTrue(one != null && two != null && !one.isQueued() && !two.isQueued(), "the first two upgrades aren't both under way");
			List<UUID> mates = v.aHelpers().stream().map(Villager::getUUID).toList();
			helper.assertTrue(mates.contains(one.builder()) && mates.contains(two.builder()) && !one.builder().equals(two.builder()),
				"the two upgrades aren't led by A's two helpers: " + one.builder() + ", " + two.builder());
			for (Villager mate : v.aHelpers()) {
				BuilderJob job = ModAttachments.BUILDER_JOB.get(mate);
				helper.assertTrue(job != null && !job.helper() && (job.siteId().equals(one.id()) || job.siteId().equals(two.id())),
					"a helper isn't on its own upgrade: " + job);
			}
			BuildSite third3 = siteOn(v.level(), jobs.get(2)).orElse(null);
			helper.assertTrue(third3 != null && third3.isQueued(), "the third upgrade isn't waiting in a queue");
			helper.assertTrue(third3.builder().equals(v.aLead().getUUID()) || third3.builder().equals(v.bLead().getUUID())
				|| mates.contains(third3.builder()), "the third upgrade waits for nobody in the village: " + third3.builder());
			// every open, unqueued build has a lead whose own job is that build
			List<Villager> all = new ArrayList<>(v.aHelpers());
			all.add(v.aLead());
			all.add(v.bLead());
			for (BuildSite site : List.of(BuildSiteManager.get(v.level()).get(v.a()), BuildSiteManager.get(v.level()).get(v.b()), one, two)) {
				boolean manned = all.stream().anyMatch(w -> w.getUUID().equals(site.builder())
					&& ModAttachments.BUILDER_JOB.get(w) != null && ModAttachments.BUILDER_JOB.get(w).siteId().equals(site.id()));
				helper.assertTrue(manned, "a build was left with nobody on it: " + site.structure() + " at " + site.placement().origin());
			}
			UUID oneId = one.id();
			UUID twoId = two.id();
			helper.succeedWhen(() -> {
				assertGoesOn(helper, v.level(), v.a(), HUT, v.aAt(), "A");
				assertGoesOn(helper, v.level(), v.b(), HUT, v.bAt(), "B");
				assertGoesOn(helper, v.level(), oneId, HUT_2, jobs.get(0), "the first upgrade");
				assertGoesOn(helper, v.level(), twoId, HUT_2, jobs.get(1), "the second upgrade");
			});
		});
	}

	/**
	 * Two busy builds, each with one builder only (A's lead, B's lead): no crew has a helper to spare, so an upgrade
	 * moves nobody and waits; both builds keep their builder and go on.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaSplit1110cTwoLone"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaSplit1110cTwoLone")
	public void twoLoneBuildersAreNeverSplit(GameTestHelper helper) {
		List<BlueprintData.Placement> jobs = new ArrayList<>();
		Village v = village(helper, 0, jobs, H1);
		helper.runAfterDelay(2, () -> {
			UpgradesPage.Outcome outcome = UpgradesPage.order(v.level(), helper.absolutePos(HALL), v.player(), jobs.get(0));
			helper.assertTrue(outcome == UpgradesPage.Outcome.QUEUED, "the outcome with two lone builders: " + outcome);
			assertLeads(helper, v.level(), v.aLead(), v.a(), "A");
			assertLeads(helper, v.level(), v.bLead(), v.b(), "B");
			BuildSite job = siteOn(v.level(), jobs.get(0)).orElse(null);
			helper.assertTrue(job != null && job.isQueued(), "the upgrade isn't waiting in a queue");
			helper.succeedWhen(() -> {
				assertGoesOn(helper, v.level(), v.a(), HUT, v.aAt(), "A");
				assertGoesOn(helper, v.level(), v.b(), HUT, v.bAt(), "B");
			});
		});
	}

	/**
	 * The Repairs page's Yes, with B's lone builder nearest the wall and A's lead and helper busy: A's helper repairs the
	 * wall (the hole is filled), B's builder never leaves B, and A goes on with its lead.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaSplit1110cRepair"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaSplit1110cRepair")
	public void aRepairTakesTheHelperNeverTheLoneBuilder(GameTestHelper helper) {
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		List<BlueprintData.Placement> jobs = new ArrayList<>();
		Village v = village(helper, 1, jobs);
		Upkeep.ENABLED = true;
		Upkeep.ASK = true;
		ServerLevel level = v.level();
		for (int x = 0; x < 4; x++) {
			helper.setBlock(WALL.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_split_1110c_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(WALL).getX());
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(WALL), new Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(WALL.offset(1, 0, 0), Blocks.AIR);
		BlueprintData.Placement at = placement(helper, WALL);
		BuildSiteManager manager = BuildSiteManager.get(level);
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
			for (BuildSite site : List.copyOf(manager.all())) {
				if (site.placement().equals(at)) {
					manager.remove(site.id());
				}
			}
			manager.forgetFinished(at);
		});
		manager.recordFinished(wall, at, v.player().getUUID());
		manager.setRepairAsk(at, new RepairAsks.Ask(RepairAsks.State.ASKED, 1));
		Container chest = helper.getBlockEntity(A_HELP_CHEST[0]);
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 4));
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = VillageHallScreen.forTest(v.player(), helper.absolutePos(HALL));
			menu.press(HallPages.slot(RepairsPage.PAGE), v.player());
			helper.assertTrue(!menu.icon(VillageHallScreen.FIRST_ROW).isEmpty(), "the wall isn't listed");
			menu.press(VillageHallScreen.FIRST_ROW, v.player());
			helper.assertTrue(!menu.icon(RepairsPage.YES).isEmpty(), "no Yes button on the wall's view");
			menu.press(RepairsPage.YES, v.player());
			BuildSite repair = RepairAsks.repairOf(level, at);
			helper.assertTrue(repair != null && v.aHelpers().get(0).getUUID().equals(repair.builder()),
				"A's helper isn't repairing the wall: " + (repair == null ? "no repair" : repair.builder()
					+ (v.bLead().getUUID().equals(repair.builder()) ? " (B's only builder)" : "")));
			assertLeads(helper, level, v.bLead(), v.b(), "B (one builder only)");
			assertLeads(helper, level, v.aLead(), v.a(), "A");
			helper.succeedWhen(() -> {
				helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL.offset(1, 0, 0));
				assertGoesOn(helper, level, v.a(), HUT, v.aAt(), "A");
				assertGoesOn(helper, level, v.b(), HUT, v.bAt(), "B");
			});
		});
	}

	/** The words a player reads when no builder can be spared (and when one is), word for word, in American English. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theSplitAndRefusalTextsReadPlainly(GameTestHelper helper) {
		Language lang = Language.getInstance();
		String[][] expected = {
			{"message.aliveworkplace.upgrades.queued", "Every Builder is busy and none can be spared from a crew: %s will build the %s over the %s after the current build"},
			{"message.aliveworkplace.repair_answer.waiting", "Yes, but every builder is busy and none can be spared from a crew: the %s is repaired as soon as one is free."},
			{"message.aliveworkplace.upgrades.split", "%s leaves the crew on the %s to build the %s over the %s"},
			{"message.aliveworkplace.repair_answer.split", "Yes: %s leaves the crew on the %s to repair the %s."},
		};
		for (String[] e : expected) {
			helper.assertTrue(lang.has(e[0]), "missing text: " + e[0]);
			String got = lang.getOrDefault(e[0]);
			helper.assertTrue(got.equals(e[1]), e[0] + " reads \"" + got + "\"");
			for (String british : new String[] {"cancelled", "colour", "armour", "defence", "grey", "neighbour", "organis"}) {
				helper.assertTrue(!got.toLowerCase().contains(british), e[0] + " isn't American English: " + got);
			}
		}
		String filled = Component.translatable("message.aliveworkplace.upgrades.queued", "Ann", "Hut II", "Hut").getString();
		helper.assertTrue(filled.equals("Every Builder is busy and none can be spared from a crew: Ann will build the Hut II over the Hut after the current build"),
			"the filled-in refusal: " + filled);
		String waiting = Component.translatable("message.aliveworkplace.repair_answer.waiting", "Wall").getString();
		helper.assertTrue(waiting.equals("Yes, but every builder is busy and none can be spared from a crew: the Wall is repaired as soon as one is free."),
			"the filled-in repair refusal: " + waiting);
		helper.succeed();
	}
}

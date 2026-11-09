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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
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
 * B1110: a job chosen on the Village Hall's Upgrades or Repairs page while every builder is busy splits a crew. With a
 * lead and a helper on build A, the helper leaves A for the new job and A goes on with its lead (through the pages'
 * own buttons, and through a save and reload); with one builder only, busy on A, nothing is taken off A and the player
 * is told plainly that none can be spared.
 */
public class HallCrewSplitGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation HUT_2 = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut_2");

	// --- the Upgrades page's village: a finished hut (x 3-7, z 3-7) to upgrade, build A (a new hut at x 3-7, z 10-14)
	private static final BlockPos UP_HALL = new BlockPos(15, 2, 11);
	private static final BlockPos UP_HUT = new BlockPos(3, 2, 3);
	private static final BlockPos UP_A = new BlockPos(3, 2, 10);
	private static final BlockPos UP_LEAD_BENCH = new BlockPos(13, 2, 3);
	private static final BlockPos UP_LEAD_CHEST = new BlockPos(13, 2, 5);
	private static final BlockPos UP_LEAD = new BlockPos(12, 2, 4);
	private static final BlockPos UP_HELPER_BENCH = new BlockPos(13, 2, 13);
	private static final BlockPos UP_HELPER_CHEST = new BlockPos(13, 2, 15);
	private static final BlockPos UP_HELPER = new BlockPos(12, 2, 14);

	// --- the Repairs page's village: a finished wall with a hole, build A (a new hut at x 9-13, z 1-5)
	private static final BlockPos RE_HALL = new BlockPos(14, 2, 14);
	private static final BlockPos RE_WALL = new BlockPos(6, 2, 8);
	private static final BlockPos RE_A = new BlockPos(9, 2, 1);
	private static final BlockPos RE_LEAD_BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos RE_LEAD_CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos RE_LEAD = new BlockPos(3, 2, 3);
	private static final BlockPos RE_HELPER_BENCH = new BlockPos(2, 2, 11);
	private static final BlockPos RE_HELPER_CHEST = new BlockPos(2, 2, 13);
	private static final BlockPos RE_HELPER = new BlockPos(3, 2, 12);

	/** The village: the player who hired both builders, the lead on build A, the helper (null: only one builder), and the job's placement. */
	private record Crew(ServerLevel level, ServerPlayer player, Villager lead, Villager helper, UUID a, BlueprintData.Placement aAt, BlueprintData.Placement job) {
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	/** A Builder at {@code bench}, hired by {@code player}, with a chest beside the bench. */
	private static Villager builder(GameTestHelper helper, ServerPlayer player, BlockPos bench, BlockPos chest, BlockPos at) {
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chest, Blocks.CHEST);
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		Builders.employ(helper.getLevel(), v, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(v, new Employer(player.getUUID(), player.getGameProfile().getName()));
		return v;
	}

	/**
	 * The common part: rules and radius set (and put back afterwards), the hall, the lead at work on build A (a test hut,
	 * its materials in the lead's chest) and, if {@code withHelper}, a second builder who joined A as its helper.
	 */
	private static Crew crew(GameTestHelper helper, BlockPos hall, BlockPos leadBench, BlockPos leadChest, BlockPos leadAt, BlockPos helperBench,
							 BlockPos helperChest, BlockPos helperAt, BlockPos aOrigin, BlueprintData.Placement job, boolean withHelper) {
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean helps = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		VillageHalls.RADIUS = 16;
		helper.setDayTime(2000); // working hours
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement a = placement(helper, aOrigin);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(helps, level.getServer());
			for (BuildSite site : List.copyOf(sites.all())) {
				if (site.placement().equals(a) || site.placement().equals(job)) {
					sites.remove(site.id());
				}
			}
			sites.forgetFinished(a);
			sites.forgetFinished(job);
			level.getServer().getPlayerList().remove(player);
		});
		helper.setBlock(hall, ModBlocks.VILLAGE_HALL);
		Villager lead = builder(helper, player, leadBench, leadChest, leadAt);
		BuildSite site = Builders.start(level, lead, player, HUT, a);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		Container chest = helper.getBlockEntity(leadChest);
		int slot = 0;
		for (Map.Entry<Item, Integer> need : plan.materials().entrySet()) {
			chest.setItem(slot++, new ItemStack(need.getKey(), Math.min(64, need.getValue())));
		}
		Villager mate = null;
		if (withHelper) {
			mate = builder(helper, player, helperBench, helperChest, helperAt);
			BuildSite joined = Builders.recruit(level, mate);
			helper.assertTrue(joined == site, "setup: the second builder didn't join build A as its helper: " + joined);
		}
		return new Crew(level, player, lead, mate, site.id(), a, job);
	}

	private static Crew upgrades(GameTestHelper helper, boolean withHelper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement hut = placement(helper, UP_HUT);
		BlockPos origin = helper.absolutePos(UP_HUT);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		Crew c = crew(helper, UP_HALL, UP_LEAD_BENCH, UP_LEAD_CHEST, UP_LEAD, UP_HELPER_BENCH, UP_HELPER_CHEST, UP_HELPER, UP_A, hut, withHelper);
		BuildSiteManager.get(level).recordFinished(HUT, hut, c.player().getUUID());
		if (withHelper) {
			Container chest = helper.getBlockEntity(UP_HELPER_CHEST);
			chest.setItem(0, new ItemStack(Items.LANTERN, 2)); // the level II's one lantern, from the helper's own bench
		}
		return c;
	}

	private static Crew repairs(GameTestHelper helper, boolean withHelper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		Upkeep.ENABLED = true; // builderRepairs on, and asking first, as in a player's game
		Upkeep.ASK = true;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
		});
		for (int x = 0; x < 4; x++) {
			helper.setBlock(RE_WALL.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "crew_split_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(RE_WALL).getX());
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(RE_WALL), new Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(RE_WALL.offset(1, 0, 0), Blocks.AIR); // knocked out
		BlueprintData.Placement placement = placement(helper, RE_WALL);
		Crew c = crew(helper, RE_HALL, RE_LEAD_BENCH, RE_LEAD_CHEST, RE_LEAD, RE_HELPER_BENCH, RE_HELPER_CHEST, RE_HELPER, RE_A, placement, withHelper);
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(wall, placement, c.player().getUUID());
		manager.setRepairAsk(placement, new RepairAsks.Ask(RepairAsks.State.ASKED, 1)); // the builder asked: it waits on the page
		if (withHelper) {
			Container chest = helper.getBlockEntity(RE_HELPER_CHEST);
			chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 4));
		}
		return c;
	}

	private static ChoiceMenu openPage(GameTestHelper helper, ServerPlayer player, BlockPos hall, String page) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, helper.absolutePos(hall));
		int tab = HallPages.slot(page);
		helper.assertTrue(tab >= VillageHallScreen.PAGE_ROW, "the " + page + " page has no tab: " + tab);
		menu.press(tab, player);
		return menu;
	}

	/** The open site on {@code placement}, if any. */
	private static Optional<BuildSite> siteOn(Crew c, BlueprintData.Placement placement) {
		return BuildSiteManager.get(c.level()).all().stream().filter(s -> s.placement().equals(placement) && !s.isDone()).findFirst();
	}

	/** The helper now leads the job's site, and is no longer counted on A's crew. */
	private static void assertHelperMoved(GameTestHelper helper, Crew c, Villager mate) {
		BuildSite job = siteOn(c, c.job()).orElse(null);
		helper.assertTrue(job != null && !job.isQueued() && mate.getUUID().equals(job.builder()),
			"the helper doesn't lead the new job: " + (job == null ? "no site" : job.builder() + (job.isQueued() ? " (queued)" : "")));
		BuilderJob own = ModAttachments.BUILDER_JOB.get(mate);
		helper.assertTrue(own != null && !own.helper() && own.siteId().equals(job.id()), "the helper's job: " + own);
		BuildSite a = BuildSiteManager.get(c.level()).get(c.a());
		helper.assertTrue(a != null && !a.helpers(c.level().getGameTime()).contains(mate.getUUID()) && a.claim(mate.getUUID()) == null,
			"the helper still counts on A's crew");
	}

	/** A keeps its lead: the lead's job is still A (a lead, not a helper), A isn't queued, and nobody else leads it. */
	private static void assertAKeepsItsLead(GameTestHelper helper, Crew c, Villager lead) {
		BuildSite a = BuildSiteManager.get(c.level()).get(c.a());
		helper.assertTrue(a != null && !a.isQueued() && lead.getUUID().equals(a.builder()), "A lost its lead: " + (a == null ? "gone" : a.builder()));
		BuilderJob job = ModAttachments.BUILDER_JOB.get(lead);
		helper.assertTrue(job != null && !job.helper() && job.siteId().equals(c.a()), "the lead's job: " + job);
	}

	/** A goes on: the lead places blocks on it (or has finished it). */
	private static void assertAGoesOn(GameTestHelper helper, Crew c, Villager lead) {
		BuildSite a = BuildSiteManager.get(c.level()).get(c.a());
		if (a == null) {
			helper.assertTrue(BuildSiteManager.get(c.level()).finishedIn(c.level()).stream()
				.anyMatch(f -> f.structure().equals(HUT) && f.placement().equals(c.aAt())), "A is gone but not finished");
			return;
		}
		BuildPlan plan = a.plan(c.level());
		helper.assertTrue(plan != null && a.progress(plan) > 0, "A hasn't gone on: " + a.stage() + " " + (plan == null ? "no plan" : a.progress(plan)));
		BuilderJob job = ModAttachments.BUILDER_JOB.get(lead);
		helper.assertTrue(job != null && job.siteId().equals(c.a()), "the lead left A: " + job);
	}

	private static Villager reload(GameTestHelper helper, ServerLevel level, Villager old) {
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "a builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "a builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		return (Villager) loaded;
	}

	// --- Upgrades -----------------------------------------------------------------------------------------------------

	/** Every builder busy on A (lead and helper): the hut's row on the Upgrades page sends the helper; A goes on with its lead. */
	//$ gametest_ticks_batch AREA '1200' '"crewSplitUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "crewSplitUpgrade")
	public void anUpgradeTakesTheHelperOffABusyCrew(GameTestHelper helper) {
		Crew c = upgrades(helper, true);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, c.player(), UP_HALL, UpgradesPage.PAGE);
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.BRICKS), "the hut's row: " + menu.icon(VillageHallScreen.FIRST_ROW));
			menu.press(VillageHallScreen.FIRST_ROW, c.player());
			assertHelperMoved(helper, c, c.helper());
			assertAKeepsItsLead(helper, c, c.lead());
			helper.assertTrue(siteOn(c, c.job()).map(BuildSite::structure).orElse(null) != null
				&& siteOn(c, c.job()).get().structure().equals(HUT_2), "the job isn't the hut's level II");
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.SCAFFOLDING), "the row doesn't show it under way: " + menu.icon(VillageHallScreen.FIRST_ROW));
			helper.succeedWhen(() -> {
				assertAGoesOn(helper, c, c.lead());
				BuilderJob job = ModAttachments.BUILDER_JOB.get(c.helper());
				helper.assertTrue(job == null || !job.helper(), "the helper went back to helping A: " + job);
			});
		});
	}

	/** One builder only, busy on A: nothing is taken off A; the upgrade waits for him and the page says none can be spared. */
	//$ gametest_ticks_batch AREA '200' '"crewSplitUpgradeAlone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "crewSplitUpgradeAlone")
	public void anUpgradeWithTheOnlyBuilderBusySaysNoneCanBeSpared(GameTestHelper helper) {
		Crew c = upgrades(helper, false);
		helper.runAfterDelay(2, () -> {
			UpgradesPage.Outcome outcome = UpgradesPage.order(c.level(), helper.absolutePos(UP_HALL), c.player(), c.job());
			helper.assertTrue(outcome == UpgradesPage.Outcome.QUEUED, "the outcome: " + outcome);
			assertAKeepsItsLead(helper, c, c.lead());
			BuildSite job = siteOn(c, c.job()).orElse(null);
			helper.assertTrue(job != null && job.isQueued() && c.lead().getUUID().equals(job.builder()), "the upgrade isn't waiting for the only builder");
			String says = Language.getInstance().getOrDefault("message.aliveworkplace.upgrades.queued");
			helper.assertTrue(says.contains("none can be spared"), "the message doesn't say so plainly: " + says);
			helper.succeedWhen(() -> assertAGoesOn(helper, c, c.lead()));
		});
	}

	/** The split survives a save and reload: the helper still leads the upgrade, the lead still A, and A goes on. */
	//$ gametest_ticks_batch AREA '1200' '"crewSplitReload"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "crewSplitReload")
	public void aSplitCrewSurvivesAReload(GameTestHelper helper) {
		Crew c = upgrades(helper, true);
		helper.runAfterDelay(2, () -> {
			UpgradesPage.Outcome outcome = UpgradesPage.order(c.level(), helper.absolutePos(UP_HALL), c.player(), c.job());
			helper.assertTrue(outcome == UpgradesPage.Outcome.SPLIT, "the outcome: " + outcome);
			var registries = c.level().registryAccess();
			c.level().getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(c.level()).save(new CompoundTag(), registries), registries));
			Villager lead = reload(helper, c.level(), c.lead());
			Villager mate = reload(helper, c.level(), c.helper());
			assertHelperMoved(helper, c, mate);
			assertAKeepsItsLead(helper, c, lead);
			helper.succeedWhen(() -> assertAGoesOn(helper, c, lead));
		});
	}

	// --- Repairs ------------------------------------------------------------------------------------------------------

	/** Every builder busy on A: Yes on the Repairs page sends the helper, who repairs the wall; A goes on with its lead. */
	//$ gametest_ticks_batch AREA '1200' '"crewSplitRepair"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "crewSplitRepair")
	public void aRepairTakesTheHelperOffABusyCrew(GameTestHelper helper) {
		Crew c = repairs(helper, true);
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = openPage(helper, c.player(), RE_HALL, RepairsPage.PAGE);
			helper.assertTrue(!menu.icon(VillageHallScreen.FIRST_ROW).isEmpty(), "the wall isn't listed");
			menu.press(VillageHallScreen.FIRST_ROW, c.player());
			helper.assertTrue(!menu.icon(RepairsPage.YES).isEmpty(), "no Yes button on the wall's view");
			menu.press(RepairsPage.YES, c.player());
			BuildSite repair = RepairAsks.repairOf(c.level(), c.job());
			helper.assertTrue(repair != null && c.helper().getUUID().equals(repair.builder()), "the helper isn't repairing the wall: "
				+ (repair == null ? "no repair" : repair.builder()));
			assertHelperMoved(helper, c, c.helper());
			assertAKeepsItsLead(helper, c, c.lead());
			helper.succeedWhen(() -> {
				helper.assertBlockPresent(Blocks.OAK_PLANKS, RE_WALL.offset(1, 0, 0));
				assertAGoesOn(helper, c, c.lead());
			});
		});
	}

	/** One builder only, busy on A: Yes is kept, nothing is taken off A, and the answer says no builder can be spared. */
	//$ gametest_ticks_batch AREA '200' '"crewSplitRepairAlone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "crewSplitRepairAlone")
	public void aRepairWithTheOnlyBuilderBusySaysNoneCanBeSpared(GameTestHelper helper) {
		Crew c = repairs(helper, false);
		helper.runAfterDelay(2, () -> {
			RepairAsks.Outcome outcome = RepairAsks.yes(c.player(), helper.absolutePos(RE_WALL)); // what the page's Yes calls
			helper.assertTrue(outcome == RepairAsks.Outcome.WAITING, "the outcome: " + outcome);
			helper.assertTrue(RepairAsks.repairOf(c.level(), c.job()) == null, "a repair started with no builder free");
			RepairAsks.Ask ask = BuildSiteManager.get(c.level()).repairAsk(c.job());
			helper.assertTrue(ask != null && ask.state() == RepairAsks.State.YES, "the yes wasn't kept: " + ask);
			assertAKeepsItsLead(helper, c, c.lead());
			String says = Language.getInstance().getOrDefault("message.aliveworkplace.repair_answer.waiting");
			helper.assertTrue(says.contains("none can be spared"), "the message doesn't say so plainly: " + says);
			helper.succeedWhen(() -> assertAGoesOn(helper, c, c.lead()));
		});
	}
}

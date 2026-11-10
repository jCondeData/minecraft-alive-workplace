package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CraftJobs;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-11-10, fourth run): verifying B1142 past its own test. Its tests check that a job is NAMED for every
 * modded crafting recipe (CraftJobs.maker non-null); Milestone 46's promise is that the thing is still MADE ("Nothing
 * that is made today may become unmakable"), and that the player reads the right job. So, with the owner's Create port:
 * the named job really makes Create's parts for a builder through its own work loop (the Gearbox from casings, shafts
 * and planks, a chain through the cogwheels; Fluid Pipes from copper by the Tinkerer, by the ingot rule), the
 * Storehouse's details line and the builder's line name that job when none works nearby, and a Create part no crafting
 * table makes (the Precision Mechanism, sequenced assembly only) still reads "No job makes or gathers this".
 */
public class QaCreateMakers1110e4CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos WORKBENCH = new BlockPos(14, 2, 2);
	private static final BlockPos ROW = new BlockPos(6, 2, 9);

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id));
	}

	private static Item item(String id) {
		return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
	}

	/** Which job the player is told makes each of these, by the spec's rules (no tag, no rule fits: the Carpenter). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void createPartsGoToTheJobTheRulesName(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(block("create:gearbox") != Blocks.AIR, "Create isn't loaded");
		// the cogwheel is a shaft and planks: planks make it the Carpenter's
		helper.assertTrue(CraftJobs.maker(level, item("create:cogwheel")) == CraftJobs.Job.CARPENTER,
			"the cogwheel (a shaft and planks) is made by " + CraftJobs.maker(level, item("create:cogwheel")));
		// the gearbox is a casing and cogwheels: no rule fits, so it stays with the Carpenter who made it before 46.1
		helper.assertTrue(CraftJobs.maker(level, item("create:gearbox")) == CraftJobs.Job.CARPENTER,
			"the gearbox is made by " + CraftJobs.maker(level, item("create:gearbox")));
		// fluid pipes are copper sheets and a copper ingot: ingots make them the Tinkerer's
		helper.assertTrue(CraftJobs.maker(level, item("create:fluid_pipe")) == CraftJobs.Job.TINKERER,
			"the fluid pipe (copper ingot and sheets) is made by " + CraftJobs.maker(level, item("create:fluid_pipe")));
		// sequenced assembly only: no crafting, stonecutting or kiln recipe, so no job, as before 46.1
		Item mechanism = item("create:precision_mechanism");
		helper.assertTrue(Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.CRAFTING, mechanism).isEmpty()
			&& Crafting.recipesFor(level.getRecipeManager(), level.registryAccess(), Crafting.Kind.STONECUTTING, mechanism).isEmpty(), "the pack added a table recipe for the Precision Mechanism");
		helper.assertTrue(CraftJobs.maker(level, mechanism) == null, "the Precision Mechanism is made by " + CraftJobs.maker(level, mechanism));
		helper.succeed();
	}

	/**
	 * A builder short of two Create Gearboxes, with two Andesite Casings, eight Shafts and eight oak planks in his chest:
	 * the Carpenter makes the cogwheels and then the gearboxes, brings them, and the build finishes with both.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qa_create_gearbox_1110e4"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_create_gearbox_1110e4")
	public void theCarpenterMakesGearboxesFromCasingsShaftsAndPlanksForABuilder(GameTestHelper helper) {
		ServerLevel level = prepare(helper);
		Block gearbox = block("create:gearbox");
		helper.assertTrue(gearbox != Blocks.AIR, "Create isn't loaded");
		ResourceLocation id = spaced(helper, gearbox, 2, "gearbox");
		Container chest = builderChest(helper);
		chest.setItem(0, new ItemStack(item("create:andesite_casing"), 2));
		chest.setItem(1, new ItemStack(item("create:shaft"), 8));
		chest.setItem(2, new ItemStack(Items.OAK_PLANKS, 8));
		BuildSite site = startBuilder(helper, id);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the gearboxes' build");
		Villager carpenter = hire(helper, ModBlocks.CARPENTERS_BENCH, ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.status() + " missing " + site.missing()
				+ "; the Carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + "; chest " + chest.countItem(item("create:andesite_casing"))
				+ " casings, " + chest.countItem(item("create:shaft")) + " shafts, " + chest.countItem(item("create:cogwheel")) + " cogwheels");
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) wrong after the build");
			helper.assertBlockPresent(gearbox, ROW);
			helper.assertBlockPresent(gearbox, ROW.offset(2, 0, 0));
			helper.assertTrue(chest.countItem(item("create:andesite_casing")) == 0, "the casings should be used up: " + chest.countItem(item("create:andesite_casing")));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) >= 2, "the gearboxes weren't the Carpenter's: he made "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
		});
	}

	/**
	 * A builder short of three Create Fluid Pipes, with two Copper Sheets and a Copper Ingot (one craft makes four): the
	 * Tinkerer, the job the rules name, makes them at his bench, and the build finishes with them; one spare pipe is left.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qa_create_pipes_1110e4"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_create_pipes_1110e4")
	public void theTinkererMakesFluidPipesFromCopperForABuilder(GameTestHelper helper) {
		ServerLevel level = prepare(helper);
		Block pipe = block("create:fluid_pipe");
		helper.assertTrue(pipe != Blocks.AIR, "Create isn't loaded");
		ResourceLocation id = spaced(helper, pipe, 3, "pipes");
		Container chest = builderChest(helper);
		chest.setItem(0, new ItemStack(item("create:copper_sheet"), 2));
		chest.setItem(1, new ItemStack(Items.COPPER_INGOT, 1));
		BuildSite site = startBuilder(helper, id);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no plan for the pipes' build");
		Villager tinkerer = hire(helper, ModBlocks.TINKERS_BENCH, ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.status() + " missing " + site.missing()
				+ "; the Tinkerer made " + ModAttachments.ITEMS_CRAFTED.getOrElse(tinkerer, 0) + "; chest " + chest.countItem(item("create:copper_sheet"))
				+ " sheets, " + chest.countItem(Items.COPPER_INGOT) + " ingots");
			for (int i = 0; i < 3; i++) {
				helper.assertBlockPresent(pipe, ROW.offset(2 * i, 0, 0));
			}
			helper.assertTrue(chest.countItem(item("create:copper_sheet")) == 0 && chest.countItem(Items.COPPER_INGOT) == 0,
				"the copper should be used up: " + chest.countItem(item("create:copper_sheet")) + " sheets, " + chest.countItem(Items.COPPER_INGOT) + " ingots");
			helper.assertTrue(chest.countItem(item("create:fluid_pipe")) == 1, "one spare pipe should be in the chest, not " + chest.countItem(item("create:fluid_pipe")));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(tinkerer, 0) >= 3, "the pipes weren't the Tinkerer's: he made "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(tinkerer, 0));
		});
	}

	/**
	 * The same Gearbox chain with the Carpenter saved and loaded again (the server restarting) once he has crafted the
	 * first step: he finishes the job from where it was saved, the build finishes, and two casings never make more than two
	 * Gearboxes (none doubled in his bag, the chest or on the ground).
	 */
	//$ gametest_ticks_batch AREA '4000' '"qa_create_gearbox_reload_1110e4"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qa_create_gearbox_reload_1110e4")
	public void aCarpenterReloadedMidChainFinishesTheGearboxesOnce(GameTestHelper helper) {
		ServerLevel level = prepare(helper);
		Block gearbox = block("create:gearbox");
		ResourceLocation id = spaced(helper, gearbox, 2, "gearbox_reload");
		Container chest = builderChest(helper);
		chest.setItem(0, new ItemStack(item("create:andesite_casing"), 2));
		chest.setItem(1, new ItemStack(item("create:shaft"), 8));
		chest.setItem(2, new ItemStack(Items.OAK_PLANKS, 8));
		BuildSite site = startBuilder(helper, id);
		Villager[] carpenter = {hire(helper, ModBlocks.CARPENTERS_BENCH, ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER)};
		boolean[] reloaded = {false};
		helper.onEachTick(() -> {
			var job = ModAttachments.CRAFTER_JOB.get(carpenter[0]);
			if (!reloaded[0] && job != null && job.phase() == io.github.jcondedata.aliveworkplace.craft.CrafterJob.Phase.CRAFTING && job.step() >= 1) {
				reloaded[0] = true;
				carpenter[0] = QaCarriedPartsGone1107eCompatTests.reload(helper, carpenter[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloaded[0], "the Carpenter never got to his second step to be reloaded there");
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building after the reload: " + site.status() + " missing "
				+ site.missing() + "; job " + ModAttachments.CRAFTER_JOB.get(carpenter[0]));
			helper.assertBlockPresent(gearbox, ROW);
			helper.assertBlockPresent(gearbox, ROW.offset(2, 0, 0));
			Item box = item("create:gearbox");
			int loose = 0;
			for (net.minecraft.world.entity.item.ItemEntity e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(8),
					e -> e.getItem().is(box))) {
				loose += e.getItem().getCount();
			}
			int extra = chest.countItem(box) + carpenter[0].getInventory().countItem(box) + loose;
			helper.assertTrue(extra == 0, "two casings made more than two Gearboxes: " + extra + " more (chest " + chest.countItem(box) + ", bag "
				+ carpenter[0].getInventory().countItem(box) + ", loose " + loose + ")");
		});
	}

	/**
	 * With no Carpenter in the village, a builder short of a Gearbox (casings and cogwheels in his chest) reads "Hire a
	 * Carpenter to make Gearbox(es)", not that nobody makes them, and nobody takes his casing.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qa_create_hire_1110e4"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qa_create_hire_1110e4")
	public void withNoCarpenterTheBuilderShortOfAGearboxSaysHireOne(GameTestHelper helper) {
		ServerLevel level = prepare(helper);
		Block gearbox = block("create:gearbox");
		ResourceLocation id = spaced(helper, gearbox, 1, "hire");
		Container chest = builderChest(helper);
		chest.setItem(0, new ItemStack(item("create:andesite_casing"), 1));
		chest.setItem(1, new ItemStack(item("create:cogwheel"), 4));
		BuildSite site = startBuilder(helper, id);
		helper.runAfterDelay(200, () -> builderLine(helper, site));
		helper.runAfterDelay(1100, () -> {
			helper.assertTrue(chest.countItem(item("create:andesite_casing")) == 1, "the casing was taken with no Carpenter");
			String text = builderLine(helper, site);
			// a modded item with no plural in our lang is named in the singular rather than guessed (Words.itemName)
			helper.assertTrue("Hire a Carpenter to make Gearboxes".equals(text) || "Hire a Carpenter to make Gearbox".equals(text), "the builder's line: " + text + " (missing " + site.missing() + ")");
			helper.succeed();
		});
	}

	/**
	 * The Storehouse's details line for Create's parts: the job to hire when none works near (the Carpenter for a
	 * Gearbox, the Tinkerer for a Fluid Pipe), the Carpenter by name once one does, and "No job makes or gathers this" for
	 * the Precision Mechanism no crafting table makes.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa_create_store_1110e4"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_create_store_1110e4")
	public void theStorehouseNamesWhoMakesCreatesParts(GameTestHelper helper) {
		ServerLevel level = prepare(helper);
		BlockPos at = helper.absolutePos(BENCH);
		helper.assertTrue(text(CraftJobs.storeLines(level, at, 16, item("create:gearbox"))).equals("Hire a Carpenter to make it"),
			"the Gearbox's line: " + text(CraftJobs.storeLines(level, at, 16, item("create:gearbox"))));
		helper.assertTrue(text(CraftJobs.storeLines(level, at, 16, item("create:fluid_pipe"))).equals("Hire a Tinkerer to make it"),
			"the Fluid Pipe's line: " + text(CraftJobs.storeLines(level, at, 16, item("create:fluid_pipe"))));
		helper.assertTrue(text(CraftJobs.storeLines(level, at, 16, item("create:precision_mechanism"))).equals("No job makes or gathers this"),
			"the Precision Mechanism's line: " + text(CraftJobs.storeLines(level, at, 16, item("create:precision_mechanism"))));
		Villager carpenter = hire(helper, ModBlocks.CARPENTERS_BENCH, ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		carpenter.setCustomName(Component.literal("Ada"));
		helper.assertTrue(text(CraftJobs.storeLines(level, at, 16, item("create:gearbox"))).equals("Made by: the Carpenter (Ada)"),
			"the Gearbox's line with Ada the Carpenter near: " + text(CraftJobs.storeLines(level, at, 16, item("create:gearbox"))));
		helper.assertTrue(text(CraftJobs.storeLines(level, at, 16, item("create:fluid_pipe"))).equals("Hire a Tinkerer to make it"),
			"a Carpenter near doesn't make Fluid Pipes: " + text(CraftJobs.storeLines(level, at, 16, item("create:fluid_pipe"))));
		helper.succeed();
	}

	// --- helpers ---------------------------------------------------------------------------------------------------

	private static String text(List<Component> lines) {
		return String.join(" | ", lines.stream().map(Component::getString).toList());
	}

	private static String builderLine(GameTestHelper helper, BuildSite site) {
		Component line = JobShortages.line(helper.getLevel(), site.id(), site.missing(), helper.absolutePos(BENCH));
		return line == null ? null : line.getString();
	}

	private static ServerLevel prepare(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		FurnicrafterCompatTests.ownStoreOnly(helper);
		ServerLevel level = helper.getLevel();
		int radius = Village.RADIUS;
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = radius);
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		return level;
	}

	private static Container builderChest(GameTestHelper helper) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		return helper.getBlockEntity(CHEST);
	}

	private static BuildSite startBuilder(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ROW),
			Rotation.NONE, Mirror.NONE));
		helper.assertTrue(site != null, "the build didn't start");
		return site;
	}

	private static Villager hire(GameTestHelper helper, Block station, net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
			net.minecraft.world.entity.npc.VillagerProfession profession) {
		helper.setBlock(WORKBENCH, station);
		Villager worker = helper.spawn(EntityType.VILLAGER, WORKBENCH.south());
		Jobs.employ(helper.getLevel(), worker, helper.absolutePos(WORKBENCH), poi, profession);
		return worker;
	}

	/** A blueprint of {@code n} {@code block}s, one block of air between each, drawn where it's to be built and taken away. */
	private static ResourceLocation spaced(GameTestHelper helper, Block block, int n, String what) {
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < n; i++) {
			helper.setBlock(ROW.offset(2 * i, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_compat",
			"qa_makers_" + what + "_" + Long.toHexString(level.getGameTime()) + "_" + helper.absolutePos(ROW).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ROW), new Vec3i(2 * n - 1, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int i = 0; i < n; i++) {
			helper.setBlock(ROW.offset(2 * i, 0, 0), Blocks.AIR);
		}
		return id;
	}
}

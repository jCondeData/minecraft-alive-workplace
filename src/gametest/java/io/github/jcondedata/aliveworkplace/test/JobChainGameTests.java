package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterBenches;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 37.14a: the Crafter is retired and each job that crafts for builders works the way it did, for its own kind:
 * the whole chain planned before anything is taken (a Carpenter's barrels from logs: planks, then slabs, then the
 * barrels; a Mason's brick stairs from clay: fired, laid, cut), what's short said on the builder's shortage line, a big
 * order a bagful at a time, each step made in turn and the job saved on the villager so a chain half made carries on
 * after a reload. A Crafter from an old save becomes jobless. Each test has its own batch: they share the jobs' claims.
 * The chair through the whole chain and the Furnicrafter's piece are the compat suite's (CrafterFurnitureCompatTests).
 */
public class JobChainGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** One step: a builder short of oak slabs, the Carpenter cuts them from the builder's spare planks. */
	//$ gametest_ticks_batch AREA '2400' '"jobChainOneStep"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "jobChainOneStep")
	public void theCarpenterMakesTheSlabsTheBuilderNeeds(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.OAK_SLAB, new ItemStack(Items.OAK_PLANKS, 3));
		Villager carpenter = carpenter(helper, STATION);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_SLAB) == 3, "the three spare slabs should be in the chest, not " + chest.countItem(Items.OAK_SLAB));
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0, "the planks should be used up, " + chest.countItem(Items.OAK_PLANKS) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 6, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0));
		});
	}

	/** A single making: a builder short of three oak planks, with one oak log, gets them (the Carpenter fetches the one log). */
	//$ gametest_ticks_batch AREA '2400' '"jobChainOneLog"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "jobChainOneLog")
	public void theCarpenterFetchesTheOneLogItNeeds(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.OAK_PLANKS, new ItemStack(Items.OAK_LOG, 1));
		carpenter(helper, STATION);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0, "the log should be used, " + chest.countItem(Items.OAK_LOG) + " left");
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 1, "the one spare plank should be in the chest, not " + chest.countItem(Items.OAK_PLANKS));
		});
	}

	/** Two builders short at once: one Carpenter serves both, each in their own chest. */
	//$ gametest_ticks_batch AREA '3600' '"jobChainTwoAskers"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "jobChainTwoAskers")
	public void oneCarpenterServesTwoBuilders(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(4, 2, 9), Blocks.OAK_SLAB, new ItemStack(Items.OAK_PLANKS, 3));
		Asker b = asker(helper, new BlockPos(14, 2, 14), new BlockPos(14, 2, 12), new BlockPos(10, 2, 14), Blocks.SPRUCE_SLAB,
			new ItemStack(Items.SPRUCE_PLANKS, 3));
		carpenter(helper, STATION);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			assertBuilt(helper, b);
			helper.assertTrue(((Container) helper.getBlockEntity(a.chest())).countItem(Items.SPRUCE_SLAB) == 0, "spruce slabs went to the wrong builder");
			helper.assertTrue(((Container) helper.getBlockEntity(b.chest())).countItem(Items.OAK_SLAB) == 0, "oak slabs went to the wrong builder");
		});
	}

	/** Three steps: barrels the builder needs, made by the Carpenter from logs (planks, slabs, barrels), and the build finishes. */
	//$ gametest_ticks_batch AREA '3600' '"jobChainCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "jobChainCarpenter")
	public void theCarpenterMakesBarrelsFromLogsInThreeSteps(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		Crafting.Plan plan = Crafting.chain(level, Crafting.Kind.CRAFTING, Items.BARREL, 3, Map.of(Items.OAK_LOG, 6L));
		helper.assertTrue(plan != null && plan.steps().size() == 3, "barrels from logs should be three steps: " + plan);
		helper.assertTrue(plan.steps().get(0).out().is(Items.OAK_PLANKS) && plan.steps().get(1).out().is(Items.OAK_SLAB)
			&& plan.steps().get(2).out().is(Items.BARREL), "the steps should go planks, slabs, barrel: " + plan.steps());
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = carpenter(helper, STATION);
		AtomicReference<String> stepLine = new AtomicReference<>();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			var entry = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(carpenter, helper.getLevel().getGameTime());
			Component line = entry == null ? null : entry.line();
			if (stepLine.get() == null && job != null && job.phase() == CrafterJob.Phase.CRAFTING && line != null && line.getString().contains("step 2 of 3")) {
				stepLine.set(line.getString());
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0, "the logs should be used up, " + chest.countItem(Items.OAK_LOG) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 3, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + " barrels");
			helper.assertTrue(stepLine.get() != null && stepLine.get().equals("making 3 Barrel (step 2 of 3)"),
				"the carpenter's line never said the step it was at: " + stepLine.get());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "the finished job is still saved on the carpenter");
		});
	}

	/**
	 * A Mason's chain through the kiln: brick stairs from clay, the balls fired into bricks in the furnace by the
	 * stonecutter, laid into brick blocks, cut into stairs, with a coal for every eight fired, all for the builder.
	 */
	//$ gametest_ticks_batch AREA '3600' '"jobChainMason"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "jobChainMason")
	public void theMasonFiresLaysAndCutsBrickStairsFromClay(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		Crafting.Plan plan = Crafting.chain(level, Crafting.Kind.KILN, Items.BRICK_STAIRS, 3, Map.of(Items.CLAY_BALL, 12L));
		helper.assertTrue(plan != null && plan.steps().size() == 3 && plan.steps().get(0).out().is(Items.BRICK) && plan.steps().get(1).out().is(Items.BRICKS)
			&& plan.steps().get(2).out().is(Items.BRICK_STAIRS), "brick stairs from clay should go bricks, brick blocks, stairs: " + plan);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BRICK_STAIRS, new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 2));
		helper.setBlock(STATION, Blocks.STONECUTTER);
		helper.setBlock(STATION.east(), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 0, "the clay should be used up, " + chest.countItem(Items.CLAY_BALL) + " left");
			helper.assertTrue(chest.countItem(Items.COAL) == 0, "the fuel should be burnt, " + chest.countItem(Items.COAL) + " coal back in the chest");
			helper.assertTrue(chest.countItem(Items.BRICK) == 0 && chest.countItem(Items.BRICKS) == 0, "half-made bricks came back: "
				+ chest.countItem(Items.BRICK) + " bricks, " + chest.countItem(Items.BRICKS) + " brick blocks");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == 3, "the mason made " + ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0));
		});
	}

	/**
	 * A raw material short: the Carpenter takes nothing (no logs sawn into planks nobody can use), and the builder's
	 * shortage line says what the chain lacks ("short of 4 Oak Log to make 3 Barrel").
	 */
	//$ gametest_ticks_batch AREA '1200' '"jobChainLacks"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "jobChainLacks")
	public void aShortRawMaterialIsNamedOnTheBuildersLineAndNothingIsTaken(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 2));
		Villager carpenter = carpenter(helper, STATION);
		helper.runAfterDelay(600, () -> {
			ServerLevel level = helper.getLevel();
			Component lack = JobShortages.lackOf(a.site().id(), Items.BARREL, level.getGameTime());
			helper.assertTrue(lack != null && lack.getString().equals("short of 4 Oak Log to make 3 Barrel"),
				"the builder's line should say the barrels are short of oak logs: " + (lack == null ? null : lack.getString()));
			Component line = JobShortages.line(level, a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals("Can't be made yet: short of 4 Oak Log to make 3 Barrel"),
				"the builder's shortage line: " + (line == null ? null : line.getString()));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 2, "the logs should stay in the chest, " + chest.countItem(Items.OAK_LOG) + " there");
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0 && chest.countItem(Items.OAK_SLAB) == 0, "nothing should be half made");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "the carpenter is carrying " + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "the carpenter took a job it can't finish");
			helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) != null, "the build can't have finished without its barrels");
			helper.succeed();
		});
	}

	/**
	 * What no job here makes stays unmade and is named: a builder short of oak stairs with only a Mason in the village
	 * (who cuts stone, not boards) is told "Nobody here makes: Oak Stairs" once the jobs have had their look.
	 */
	//$ gametest_ticks_batch AREA '1400' '"jobChainNobody"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "jobChainNobody")
	public void whatNoJobMakesIsNamedOnTheBuildersLine(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.OAK_STAIRS, new ItemStack(Items.OAK_LOG, 4));
		helper.setBlock(STATION, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(a.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder should be waiting: " + a.site().status());
			JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing()); // the builder starts waiting about now
		});
		helper.runAfterDelay(1000, () -> {
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals("Nobody here makes: Oak Stairs"), "the builder's line: " + (line == null ? null : line.getString()));
			helper.assertTrue(((Container) helper.getBlockEntity(a.chest())).countItem(Items.OAK_LOG) == 4, "something took the logs");
			helper.succeed();
		});
	}

	/** Saved and loaded mid-chain: the Carpenter carries on from the step it was at, and the build finishes. */
	//$ gametest_ticks_batch AREA '4000' '"jobChainReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "jobChainReload")
	public void aChainHalfMadeCarriesOnAfterAReload(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		AtomicReference<Villager> carpenter = new AtomicReference<>(carpenter(helper, STATION));
		AtomicReference<CrafterJob> saved = new AtomicReference<>();
		helper.onEachTick(() -> {
			if (saved.get() != null) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter.get());
			if (job != null && job.phase() == CrafterJob.Phase.CRAFTING && job.step() >= 1) {
				saved.set(job);
				Villager copy = reload(helper.getLevel(), carpenter.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.phase() == job.phase() && after.step() == job.step() && after.target() == job.target()
					&& after.count() == job.count() && after.steps().size() == job.steps().size() && after.takes().equals(job.takes())
					&& after.deliverTo().equals(job.deliverTo()) && after.claim().equals(job.claim()), "the job after a reload: " + after + ", saved " + job);
				helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(copy).count(Items.OAK_PLANKS) > 0,
					"the planks made before the reload should still be in the bag");
				helper.assertTrue(copy.getVillagerData().getProfession() == ModVillagers.CARPENTER, "the carpenter lost the job in the reload");
				carpenter.set(copy);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(saved.get() != null, "the carpenter never got to the second step");
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0, "the logs should be used up, " + chest.countItem(Items.OAK_LOG) + " left");
			helper.assertTrue(chest.countItem(Items.BARREL) == 0, "no barrels left over: " + chest.countItem(Items.BARREL));
		});
	}

	/**
	 * An old save with a Crafter at its workbench, a chain in hand and planks in its bag loads clean: the Crafter is jobless
	 * the moment it ticks, the planks are dropped at its feet, the job is let go, the workbench is free and nobody takes it;
	 * the profession and the block are still registered by their ids.
	 */
	//$ gametest_ticks_batch AREA '200' '"jobChainOldCrafter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "jobChainOldCrafter")
	public void anOldSavesCrafterLoadsJobless(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.CRAFTER).toString().equals("aliveworkplace:crafter"),
			"the Crafter's id changed: " + BuiltInRegistries.VILLAGER_PROFESSION.getKey(ModVillagers.CRAFTER));
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(ModBlocks.CRAFTERS_WORKBENCH).toString().equals("aliveworkplace:crafters_workbench"),
			"the workbench's id changed");
		helper.setBlock(STATION, ModBlocks.CRAFTERS_WORKBENCH);
		Villager old = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(level, old, helper.absolutePos(STATION), ModVillagers.CRAFTERS_WORKBENCH_POI, ModVillagers.CRAFTER);
		old.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(STATION)));
		// the chain an old Crafter had in hand: planks from logs, slabs, a barrel (written out: no planning needed)
		Crafting.Plan plan = new Crafting.Plan(Items.BARREL, 1, List.of(
			new Crafting.Step(Map.of(Items.OAK_LOG, 1), new ItemStack(Items.OAK_PLANKS, 4), 2),
			new Crafting.Step(Map.of(Items.OAK_PLANKS, 3), new ItemStack(Items.OAK_SLAB, 6), 1),
			new Crafting.Step(Map.of(Items.OAK_PLANKS, 6, Items.OAK_SLAB, 2), new ItemStack(Items.BARREL), 1)),
			Map.of(Items.OAK_LOG, 2), Map.of(Items.BARREL, 1));
		ModAttachments.CRAFTER_JOB.set(old, CrafterJob.of(helper.absolutePos(CHEST), null, plan, List.of(), "").withPhase(CrafterJob.Phase.CRAFTING).nextStep());
		ModAttachments.BUILDER_BAG.getOrCreate(old).addAll(Items.OAK_PLANKS, 8);
		Villager loaded = reload(level, old); // saved and loaded in the same tick: it never ran as a Crafter here
		helper.assertTrue(loaded.getVillagerData().getProfession() == ModVillagers.CRAFTER, "the old save's villager should load as a Crafter");
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(loaded.isAlive(), "the old Crafter didn't survive loading");
			helper.assertTrue(loaded.getVillagerData().getProfession() == VillagerProfession.NONE, "the old Crafter is still a " + loaded.getVillagerData().getProfession());
			helper.assertTrue(loaded.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "the old Crafter still holds a job site");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(loaded) == null, "the old Crafter's chain is still saved on it");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(loaded).isEmpty(), "the old Crafter still carries " + ModAttachments.BUILDER_BAG.getOrCreate(loaded).stacks());
			int planks = level.getEntitiesOfClass(ItemEntity.class, loaded.getBoundingBox().inflate(4)).stream()
				.filter(e -> e.getItem().is(Items.OAK_PLANKS)).mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(planks == 8, "the old Crafter's eight planks should lie at its feet, " + planks + " there");
			helper.assertBlockPresent(ModBlocks.CRAFTERS_WORKBENCH, STATION);
			var holder = level.getPoiManager().getType(helper.absolutePos(STATION));
			helper.assertTrue(holder.isPresent() && !ModVillagers.CRAFTER.acquirableJobSite().test(holder.get()), "a villager could still take a Crafter's Workbench");
			BlockPos bench = helper.absolutePos(STATION);
			helper.assertTrue(level.getPoiManager().findAll(h -> h.is(ModVillagers.CRAFTERS_WORKBENCH_POI), p -> p.equals(bench), bench, 1,
				net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE).findAny().isPresent(), "the workbench wasn't let go");
			helper.succeed();
		});
	}

	/** Short of a single thing: four oak planks from nothing are short of one log, and that one is named. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void beingShortOfJustOneThingIsStillNamed(GameTestHelper helper) {
		Map<net.minecraft.world.item.Item, Integer> lacks = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.OAK_PLANKS, 4, Map.of());
		helper.assertTrue(lacks.size() == 1 && lacks.values().iterator().next() == 1,
			"four oak planks from nothing should be short of one log: " + lacks);
		helper.succeed();
	}

	/** The lack line lists each short thing with its count, a comma between them ("short of 2 Oak Log, 1 Stick to make 4 Barrel"). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theLackLineNamesEveryShortThingWithACommaBetween(GameTestHelper helper) {
		Map<net.minecraft.world.item.Item, Integer> lack = new java.util.LinkedHashMap<>();
		lack.put(Items.OAK_LOG, 2);
		lack.put(Items.STICK, 1);
		String line = JobShortages.lackLine(lack, 4, Items.BARREL).getString();
		helper.assertTrue(line.equals("short of 2 Oak Log, 1 Stick to make 4 Barrel"), "the lack line reads \"" + line + "\"");
		helper.succeed();
	}

	/** A furniture mod's bench (by recipe type id, from a crafter_benches file) is used only while it stands by the workbench. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aBenchsRecipesOnlyWhileTheBenchIsThere(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(CrafterBenches.all().containsKey(ResourceLocation.withDefaultNamespace("smelting")),
			"the test datapack's bench (smelting at a furnace) wasn't loaded: " + CrafterBenches.all());
		BlockPos station = helper.absolutePos(new BlockPos(0, 1, 0));
		helper.setBlock(new BlockPos(0, 1, 0), ModBlocks.CRAFTERS_WORKBENCH);
		helper.assertTrue(Crafting.chain(level, Items.GLASS, 4, Map.of(Items.SAND, 4L), CrafterBenches.usableAt(level, station)) == null,
			"glass was planned without a furnace in the workshop");
		helper.setBlock(new BlockPos(1, 1, 0), Blocks.FURNACE);
		Crafting.Plan plan = Crafting.chain(level, Items.GLASS, 4, Map.of(Items.SAND, 4L), CrafterBenches.usableAt(level, station));
		helper.assertTrue(plan != null && plan.takes().equals(Map.of(Items.SAND, 4)), "glass with the furnace there: " + plan);
		helper.succeed();
	}

	/**
	 * The shipped bench file for CobbleFurnies' Furni Crafter (37.15) is left out while CobbleFurnies isn't installed (its
	 * recipe type isn't registered), and a Furni Crafter's block id near the workbench opens nothing; the test bench loads.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCobbleFurniesBenchIsIgnoredWithoutCobbleFurnies(GameTestHelper helper) {
		helper.assertTrue(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("cobblefurnies"), "CobbleFurnies is loaded in the plain suite");
		ResourceLocation type = ResourceLocation.fromNamespaceAndPath("cobblefurnies", "furni_crafting");
		helper.assertTrue(helper.getLevel().getServer().getResourceManager()
				.getResource(ResourceLocation.fromNamespaceAndPath("aliveworkplace", CrafterBenches.FOLDER + "/cobblefurnies.json")).isPresent(),
			"the CobbleFurnies bench file isn't shipped");
		helper.assertTrue(!CrafterBenches.all().containsKey(type), "the CobbleFurnies bench loaded without CobbleFurnies: " + CrafterBenches.all());
		helper.assertTrue(CrafterBenches.all().containsKey(ResourceLocation.withDefaultNamespace("smelting")), "the test bench should still load: " + CrafterBenches.all());
		helper.succeed();
	}

	// --- helpers ---------------------------------------------------------------------------------------------------

	private static void prepare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
	}

	/** A builder at {@code bench} with {@code items} in the chest at {@code chest}, building a row of three {@code block}. */
	private static Asker asker(GameTestHelper helper, BlockPos bench, BlockPos chest, BlockPos origin, Block block, ItemStack... items) {
		ServerLevel level = helper.getLevel();
		ResourceLocation id = row(helper, origin, block);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chest, Blocks.CHEST);
		Container container = helper.getBlockEntity(chest);
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, bench.south());
		Builders.employ(level, builder, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, builder, null, id, new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin),
			Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + id);
		}
		return new Asker(builder, site, plan, chest);
	}

	/** A blueprint of three {@code block} in a row, drawn where it's to be built (and taken away again). */
	private static ResourceLocation row(GameTestHelper helper, BlockPos origin, Block block) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test",
			"chain_" + BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime()) + "_" + origin.getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	private static Villager carpenter(GameTestHelper helper, BlockPos at) {
		helper.setBlock(at, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, at.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(at), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return carpenter;
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	private static Villager reload(ServerLevel level, Villager villager) {
		CompoundTag tag = new CompoundTag();
		villager.saveWithoutId(tag);
		villager.discard();
		Villager copy = EntityType.VILLAGER.create(level);
		if (copy == null) {
			throw new GameTestAssertException("no villager made");
		}
		copy.load(tag);
		level.addFreshEntity(copy);
		io.github.jcondedata.aliveworkplace.work.Village.forget(copy);
		return copy;
	}
}

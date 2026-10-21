package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.Chains;
import io.github.jcondedata.aliveworkplace.craft.CrafterBenches;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.Crafters;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
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
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.AABB;

/**
 * Every job its own crafting (ROADMAP 37.14a, the retired Crafter's whole chains moved into the jobs that craft for the
 * builders): a Carpenter makes barrels (vanilla's stand-in for a chair: planks and slabs) from logs in three steps, a
 * Mason fires cobblestone and cuts stone brick stairs, each step in turn and the job saved after each, so a chain half
 * made carries on after a reload. Nothing is taken when a raw material is short, and the builder's line says what's
 * short. Another worker's ladders come from the Storehouse. An old save's Crafter loads jobless. Each test has its own
 * batch: they share the crafters' claims and the config switch.
 */
public class JobChainGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STATION = new BlockPos(14, 2, 2);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** One step: a builder short of oak slabs, the carpenter cuts them from the builder's spare planks. */
	//$ gametest_ticks_batch AREA '2400' '"chainOneStep"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "chainOneStep")
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
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "the job is still saved after it was delivered");
		});
	}

	/** A single making: a builder short of three oak planks, with one oak log, gets them (the carpenter fetches the one log). */
	//$ gametest_ticks_batch AREA '2400' '"chainOneLog"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "chainOneLog")
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

	/**
	 * The Carpenter's three-step chain to a chair (vanilla's stand-in, the barrel: six planks and two slabs): logs sawn into
	 * planks, planks cut into slabs, then the barrels, each step its own in the saved job and over the carpenter's head,
	 * and the build finishes.
	 */
	//$ gametest_ticks_batch AREA '3600' '"chainCarpenter"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "chainCarpenter")
	public void theCarpenterMakesBarrelsFromLogsInThreeSteps(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		Crafting.Plan plan = Crafting.chain(level, Crafting.Kind.CRAFTING, Items.BARREL, 3, Map.of(Items.OAK_LOG, 6L));
		helper.assertTrue(plan != null && plan.steps().size() == 3, "barrels from logs should be three steps: " + plan);
		helper.assertTrue(plan.steps().get(0).out().is(Items.OAK_PLANKS) && plan.steps().get(1).out().is(Items.OAK_SLAB)
			&& plan.steps().get(2).out().is(Items.BARREL), "the steps should go planks, slabs, barrel: " + plan.steps());
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = carpenter(helper, STATION);
		List<Integer> stepsSeen = new ArrayList<>();
		AtomicReference<String> line = new AtomicReference<>("");
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			if (job != null && job.target() == Items.BARREL && job.phase() == CrafterJob.Phase.CRAFTING && !stepsSeen.contains(job.step())) {
				stepsSeen.add(job.step());
			}
			var status = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(carpenter, helper.getLevel().getGameTime());
			if (status != null && status.line().getString().contains("step 2 of 3")) {
				line.set(status.line().getString());
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0, "the logs should be used up, " + chest.countItem(Items.OAK_LOG) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) == 3, "the carpenter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + " barrels");
			helper.assertTrue(stepsSeen.containsAll(List.of(0, 1, 2)), "the saved job went through steps " + stepsSeen + ", not 0, 1 and 2");
			helper.assertTrue(line.get().contains("Oak Slab"), "the line over the carpenter's head at step 2: \"" + line.get() + "\"");
		});
	}

	/**
	 * A Mason's chain: a builder short of stone brick stairs, with cobblestone and coal: the mason fires the cobblestone
	 * into stone in the furnace by the stonecutter, then cuts the stairs, step after step; the coal is burnt, not delivered.
	 */
	//$ gametest_ticks_batch AREA '3600' '"chainMason"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "chainMason")
	public void theMasonFiresAndCutsStoneBrickStairsStepByStep(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.STONE_BRICK_STAIRS, new ItemStack(Items.COBBLESTONE, 3),
			new ItemStack(Items.COAL, 1));
		helper.setBlock(STATION, Blocks.STONECUTTER);
		helper.setBlock(STATION.east(2), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		AtomicInteger steps = new AtomicInteger();
		List<Integer> stepsSeen = new ArrayList<>();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(mason);
			if (job != null && job.target() == Items.STONE_BRICK_STAIRS) {
				steps.set(Math.max(steps.get(), job.steps().size()));
				if (job.phase() == CrafterJob.Phase.CRAFTING && !stepsSeen.contains(job.step())) {
					stepsSeen.add(job.step());
				}
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(steps.get() >= 2 && stepsSeen.size() >= 2, "the mason's saved job had " + steps.get() + " steps, made " + stepsSeen);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0, "the cobblestone should be used, " + chest.countItem(Items.COBBLESTONE) + " left");
			helper.assertTrue(chest.countItem(Items.COAL) == 0, "the coal should be burnt, " + chest.countItem(Items.COAL) + " in the chest");
		});
	}

	/**
	 * "Then other workers' needs": an armorer waiting for three ladders gets them from the carpenter, made from sticks that
	 * lie only in the Storehouse's chest (none by the crafting bench), and delivered to the chest by the armorer's furnace.
	 */
	//$ gametest_ticks_batch AREA '2400' '"chainOtherWorker"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "chainOtherWorker")
	public void theCarpenterMakesAnotherWorkersLaddersFromTheStorehouse(GameTestHelper helper) {
		prepare(helper);
		helper.setBlock(new BlockPos(2, 2, 12), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(3, 2, 12), Blocks.CHEST);
		Container store = helper.getBlockEntity(new BlockPos(3, 2, 12));
		store.setItem(0, new ItemStack(Items.STICK, 7));
		BlockPos furnace = new BlockPos(14, 2, 12);
		helper.setBlock(furnace, Blocks.BLAST_FURNACE);
		helper.setBlock(new BlockPos(13, 2, 12), Blocks.CHEST);
		Villager armorer = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 11));
		Jobs.employ(helper.getLevel(), armorer, helper.absolutePos(furnace), PoiTypes.ARMORER, VillagerProfession.ARMORER);
		helper.onEachTick(() -> {
			if (helper.getLevel().getGameTime() % 20 == 0) {
				io.github.jcondedata.aliveworkplace.work.Requests.post(armorer, new ItemStack(Items.LADDER), 3, Component.literal("ladders"),
					io.github.jcondedata.aliveworkplace.work.Requests.exactly(Items.LADDER));
			}
		});
		carpenter(helper, STATION);
		helper.succeedWhen(() -> {
			Container theirs = helper.getBlockEntity(new BlockPos(13, 2, 12));
			helper.assertTrue(theirs.countItem(Items.LADDER) == 3, "the armorer's chest holds " + theirs.countItem(Items.LADDER) + " ladders, "
				+ store.countItem(Items.STICK) + " sticks left in the Storehouse's chest");
		});
	}

	/** Short of a single thing: four oak planks from nothing are short of one log, and that one is named. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void beingShortOfJustOneThingIsStillNamed(GameTestHelper helper) {
		Map<Item, Integer> lacks = Crafting.lacking(helper.getLevel(), Crafting.Kind.CRAFTING, Items.OAK_PLANKS, 4, Map.of());
		helper.assertTrue(lacks.size() == 1 && lacks.values().iterator().next() == 1,
			"four oak planks from nothing should be short of one log: " + lacks);
		helper.succeed();
	}

	/** The "short of ..." line lists each short thing with its count, a comma between them. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theLackLineNamesEveryShortThingWithACommaBetween(GameTestHelper helper) {
		Map<Item, Integer> lack = new java.util.LinkedHashMap<>();
		lack.put(Items.OAK_LOG, 2);
		lack.put(Items.STICK, 1);
		String line = Chains.lackLine(lack, 4, Items.BARREL).getString();
		helper.assertTrue(line.equals("short of 2 Oak Log, 1 Stick to make 4 Barrel"), "the lack line reads \"" + line + "\"");
		helper.succeed();
	}

	/**
	 * A raw material short: the carpenter takes nothing (no logs sawn into planks nobody can use), and the builder's own
	 * line says what's short ("short of 1 Oak Log to make 3 Barrel"), as does the carpenter's.
	 */
	//$ gametest_ticks_batch AREA '1200' '"chainLacks"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "chainLacks")
	public void aShortRawMaterialIsNamedInTheBuildersLineAndNothingIsWasted(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 2));
		Villager carpenter = carpenter(helper, STATION);
		helper.runAfterDelay(600, () -> {
			ServerLevel level = helper.getLevel();
			Component lack = Chains.builderLack(a.builder(), level.getGameTime());
			helper.assertTrue(lack != null && lack.getString().contains("Oak Log") && lack.getString().contains("Barrel"),
				"the builder's line should say it's short of oak logs for the barrels: " + (lack == null ? null : lack.getString()));
			BuilderStatusSync.Status status = BuilderStatusSync.status(level, a.site(), a.builder());
			helper.assertTrue(status != null && status.line().getString().contains("Oak Log"), "the builder's overhead line reads \""
				+ (status == null ? null : status.line().getString()) + "\"");
			var own = io.github.jcondedata.aliveworkplace.work.WorkerStatus.get(carpenter, helper.getLevel().getGameTime());
			helper.assertTrue(own != null && own.line().getString().contains("Oak Log"), "the carpenter's line reads \""
				+ (own == null ? null : own.line().getString()) + "\"");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 2, "the logs should stay in the chest, " + chest.countItem(Items.OAK_LOG) + " there");
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0 && chest.countItem(Items.OAK_SLAB) == 0, "nothing should be half made");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "the carpenter is carrying " + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "the carpenter took a job it can't finish");
			helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) != null, "the build can't have finished without its barrels");
			helper.succeed();
		});
	}

	/** Two builders short at once: one carpenter serves both, each in their own chest. */
	//$ gametest_ticks_batch AREA '3600' '"chainTwoAskers"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "chainTwoAskers")
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

	/** Saved and loaded mid-chain: the carpenter carries on from the step it was at, and the build finishes. */
	//$ gametest_ticks_batch AREA '4000' '"chainReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "chainReload")
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
				carpenter.set(copy);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(saved.get() != null, "the carpenter never got to the second step");
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0, "the logs should be used up, " + chest.countItem(Items.OAK_LOG) + " left");
			helper.assertTrue(chest.countItem(Items.BARREL) == 0, "no barrels left over: " + chest.countItem(Items.BARREL));
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter.get(), 0) == 3, "the carpenter made "
				+ ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter.get(), 0) + " barrels after the reload");
		});
	}

	/** Config crafters off: the carpenter still makes the slabs, in one go as before 2.1, and saves no job. */
	//$ gametest_ticks_batch AREA '2400' '"chainSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "chainSwitchOff")
	public void withTheSwitchOffJobsCraftInOneGo(GameTestHelper helper) {
		WorkplaceConfig.parse("{\"crafters\": false}").apply(); // (before prepare: applying a config resets the village's reach)
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		prepare(helper);
		helper.assertTrue(!Chains.ENABLED, "crafters false in the file left the chains on");
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.OAK_SLAB, new ItemStack(Items.OAK_PLANKS, 3));
		Villager carpenter = carpenter(helper, STATION);
		AtomicReference<Boolean> savedOne = new AtomicReference<>(false);
		helper.onEachTick(() -> {
			if (ModAttachments.CRAFTER_JOB.get(carpenter) != null) {
				savedOne.set(true);
			}
		});
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			helper.assertTrue(!savedOne.get(), "a job was saved with the switch off");
			new WorkplaceConfig().apply();
			helper.assertTrue(Chains.ENABLED, "the default should be on in GameTests");
		});
	}

	/**
	 * An old save with a Crafter (37.14 era: the job in hand saved, planks in the bag, the workbench taken) loads clean:
	 * the villager becomes jobless, the job is forgotten, the bag is dropped where they stand (nothing lost), the
	 * workbench is free again and nobody takes it; and the workbench is no longer crafted or in the creative tab.
	 */
	//$ gametest_ticks_batch AREA '200' '"chainOldCrafter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "chainOldCrafter")
	public void anOldSavesCrafterLoadsJobless(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		BlockPos workbench = new BlockPos(8, 2, 8);
		helper.setBlock(workbench, ModBlocks.CRAFTERS_WORKBENCH);
		Villager old = helper.spawn(EntityType.VILLAGER, workbench.south());
		Jobs.employ(level, old, helper.absolutePos(workbench), ModVillagers.CRAFTERS_WORKBENCH_POI, ModVillagers.CRAFTER);
		helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(workbench)) == 0, "setup: the workbench isn't taken");
		Crafting.Plan plan = Crafting.chain(level, Crafting.Kind.CRAFTING, Items.BARREL, 1, Map.of(Items.OAK_LOG, 4L));
		ModAttachments.CRAFTER_JOB.set(old, CrafterJob.of(helper.absolutePos(CHEST), null, plan, List.of(), "").withPhase(CrafterJob.Phase.CRAFTING).nextStep());
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(old);
		bag.addAll(Items.OAK_PLANKS, 8);
		Villager loaded = reload(level, old); // (in the same tick: the Crafter never ticked before the save)
		helper.assertTrue(loaded.getVillagerData().getProfession() == ModVillagers.CRAFTER && ModAttachments.CRAFTER_JOB.get(loaded) != null,
			"setup: the save didn't hold a Crafter with a job");
		var tab = ModItems.TAB;
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(level.enabledFeatures(), true, level.registryAccess()));
		helper.assertTrue(tab.getDisplayItems().stream().noneMatch(s -> s.is(ModBlocks.CRAFTERS_WORKBENCH.asItem())), "the workbench is in the creative tab");
		helper.assertTrue(Recipes(level).noneMatch(h -> h.value().getResultItem(level.registryAccess()).is(ModBlocks.CRAFTERS_WORKBENCH.asItem())),
			"the workbench can still be crafted");
		var poi = level.getPoiManager().getType(helper.absolutePos(workbench));
		helper.assertTrue(poi.isPresent() && !ModVillagers.CRAFTER.acquirableJobSite().test(poi.get()), "a villager could take the workbench as a Crafter");
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(loaded.isAlive(), "the old Crafter is gone: " + loaded.getRemovalReason());
			helper.assertTrue(loaded.getVillagerData().getProfession() != ModVillagers.CRAFTER, "still a Crafter after loading");
			helper.assertTrue(loaded.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> !g.pos().equals(helper.absolutePos(workbench))).orElse(true),
				"still works at the workbench");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(loaded) == null, "the Crafter's job is still saved");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(loaded).isEmpty(), "still carrying " + ModAttachments.BUILDER_BAG.getOrCreate(loaded).stacks());
			int planks = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(workbench)).inflate(6), e -> e.getItem().is(Items.OAK_PLANKS))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(planks == 8, "the bag's 8 planks should be dropped, " + planks + " found");
			helper.assertTrue(level.getPoiManager().getFreeTickets(helper.absolutePos(workbench)) == 1, "the workbench is still taken");
			helper.succeed();
		});
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

	/** The shipped bench file for CobbleFurnies' Furni Crafter is left out while CobbleFurnies isn't installed. */
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

	private static java.util.stream.Stream<net.minecraft.world.item.crafting.RecipeHolder<?>> Recipes(ServerLevel level) {
		return level.getRecipeManager().getRecipes().stream();
	}

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
			"chain_" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime())
				+ "_" + origin.getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	private static Villager carpenter(GameTestHelper helper, BlockPos bench) {
		helper.setBlock(bench, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, bench.south());
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(bench), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		return carpenter;
	}

	private static void assertBuilt(GameTestHelper helper, Asker a) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(BuildSiteManager.get(level).get(a.site().id()) == null,
			"still building: status=" + a.site().status() + " missing=" + a.site().missing());
		List<BlockPos> unfinished = a.plan().unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " block(s) wrong after the build");
	}

	static Villager reload(ServerLevel level, Villager villager) {
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

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.craft.CrafterBenches;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.Crafters;
import io.github.jcondedata.aliveworkplace.craft.Crafting;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The Crafter (ROADMAP 37.14): a villager at a Crafter's Workbench who makes what the builders are short of, through
 * whole recipe chains (a barrel from logs: planks, then slabs, then the barrel), from the builders' chests, and puts it
 * in their chests. Each test has its own batch: they share the crafters' claims and the config switch.
 */
public class CrafterGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos WORKBENCH = new BlockPos(14, 2, 2);

	private record Asker(Villager builder, BuildSite site, BuildPlan plan, BlockPos chest) {
	}

	/** One step: a builder short of oak slabs, the crafter cuts them from the builder's spare planks. */
	//$ gametest_ticks_batch AREA '2400' '"crafterOneStep"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "crafterOneStep")
	public void theCrafterMakesTheSlabsTheBuilderNeeds(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.OAK_SLAB, new ItemStack(Items.OAK_PLANKS, 3));
		Villager crafter = crafter(helper, WORKBENCH);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_SLAB) == 3, "the three spare slabs should be in the chest, not " + chest.countItem(Items.OAK_SLAB));
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0, "the planks should be used up, " + chest.countItem(Items.OAK_PLANKS) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) == 6, "the crafter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0));
		});
	}

	/** Three steps: barrels the builder needs, made from logs (planks, then slabs, then the barrels), and the build finishes. */
	//$ gametest_ticks_batch AREA '3600' '"crafterChain"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "crafterChain")
	public void theCrafterMakesBarrelsFromLogsForTheBuilder(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		Crafting.Plan plan = Crafting.chain(level, Items.BARREL, 3, Map.of(Items.OAK_LOG, 6L), t -> true);
		helper.assertTrue(plan != null && plan.steps().size() == 3, "barrels from logs should be three steps: " + plan);
		helper.assertTrue(plan.steps().get(0).out().is(Items.OAK_PLANKS) && plan.steps().get(1).out().is(Items.OAK_SLAB)
			&& plan.steps().get(2).out().is(Items.BARREL), "the steps should go planks, slabs, barrel: " + plan.steps());
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager crafter = crafter(helper, WORKBENCH);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0, "the logs should be used up, " + chest.countItem(Items.OAK_LOG) + " left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) == 3, "the crafter made " + ModAttachments.ITEMS_CRAFTED.getOrElse(crafter, 0) + " barrels");
		});
	}

	/** A raw material short: the crafter says what it lacks and takes nothing (no logs sawn into planks nobody can use). */
	//$ gametest_ticks_batch AREA '1200' '"crafterLacks"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "crafterLacks")
	public void theCrafterSaysWhatItLacksAndWastesNothing(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 2));
		Villager crafter = crafter(helper, WORKBENCH);
		helper.runAfterDelay(600, () -> {
			Component lack = Crafters.lacking(crafter);
			helper.assertTrue(lack != null && lack.getString().contains("Oak Log") && lack.getString().contains("Barrel"),
				"the crafter should say it's short of oak logs for the barrels: " + (lack == null ? null : lack.getString()));
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 2, "the logs should stay in the chest, " + chest.countItem(Items.OAK_LOG) + " there");
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0 && chest.countItem(Items.OAK_SLAB) == 0, "nothing should be half made");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(crafter).isEmpty(), "the crafter is carrying " + ModAttachments.BUILDER_BAG.getOrCreate(crafter).stacks());
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(crafter) == null, "the crafter took a job it can't finish");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) != null, "the build can't have finished without its barrels");
			helper.succeed();
		});
	}

	/** Two builders short at once: one crafter serves both, each in their own chest. */
	//$ gametest_ticks_batch AREA '3600' '"crafterTwoAskers"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "crafterTwoAskers")
	public void oneCrafterServesTwoBuilders(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(4, 2, 9), Blocks.OAK_SLAB, new ItemStack(Items.OAK_PLANKS, 3));
		Asker b = asker(helper, new BlockPos(14, 2, 14), new BlockPos(14, 2, 12), new BlockPos(10, 2, 14), Blocks.SPRUCE_SLAB,
			new ItemStack(Items.SPRUCE_PLANKS, 3));
		crafter(helper, WORKBENCH);
		helper.succeedWhen(() -> {
			assertBuilt(helper, a);
			assertBuilt(helper, b);
			helper.assertTrue(((Container) helper.getBlockEntity(a.chest())).countItem(Items.SPRUCE_SLAB) == 0, "spruce slabs went to the wrong builder");
			helper.assertTrue(((Container) helper.getBlockEntity(b.chest())).countItem(Items.OAK_SLAB) == 0, "oak slabs went to the wrong builder");
		});
	}

	/** Saved and loaded mid-chain: the crafter carries on from the step it was at, and the build finishes. */
	//$ gametest_ticks_batch AREA '4000' '"crafterReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "crafterReload")
	public void aChainHalfMadeCarriesOnAfterAReload(GameTestHelper helper) {
		prepare(helper);
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		AtomicReference<Villager> crafter = new AtomicReference<>(crafter(helper, WORKBENCH));
		AtomicReference<CrafterJob> saved = new AtomicReference<>();
		helper.onEachTick(() -> {
			if (saved.get() != null) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(crafter.get());
			if (job != null && job.phase() == CrafterJob.Phase.CRAFTING && job.step() >= 1) {
				saved.set(job);
				Villager copy = reload(helper.getLevel(), crafter.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.phase() == job.phase() && after.step() == job.step() && after.target() == job.target()
					&& after.count() == job.count() && after.steps().size() == job.steps().size() && after.takes().equals(job.takes())
					&& after.deliverTo().equals(job.deliverTo()) && after.claim().equals(job.claim()), "the job after a reload: " + after + ", saved " + job);
				helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(copy).count(Items.OAK_PLANKS) > 0,
					"the planks made before the reload should still be in the bag");
				crafter.set(copy);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(saved.get() != null, "the crafter never got to the second step");
			assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0, "the logs should be used up, " + chest.countItem(Items.OAK_LOG) + " left");
			helper.assertTrue(chest.countItem(Items.BARREL) == 0, "no barrels left over: " + chest.countItem(Items.BARREL));
		});
	}

	/** Config crafters off: no villager takes the workbench, and a Crafter already hired makes nothing. */
	//$ gametest_ticks_batch AREA '1200' '"crafterSwitchOff"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "crafterSwitchOff")
	public void withTheSwitchOffTheCrafterMakesNothing(GameTestHelper helper) {
		prepare(helper);
		WorkplaceConfig.parse("{\"crafters\": false}").apply();
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		helper.assertTrue(!Crafters.ENABLED, "crafters false in the file left the Crafter on");
		Asker a = asker(helper, BENCH, CHEST, new BlockPos(6, 2, 9), Blocks.OAK_SLAB, new ItemStack(Items.OAK_PLANKS, 3));
		Villager crafter = crafter(helper, WORKBENCH);
		BlockPos free = new BlockPos(10, 2, 2);
		helper.setBlock(free, ModBlocks.CRAFTERS_WORKBENCH);
		helper.runAfterDelay(20, () -> {
			var holder = helper.getLevel().getPoiManager().getType(helper.absolutePos(free));
			helper.assertTrue(holder.isPresent() && !ModVillagers.CRAFTER.acquirableJobSite().test(holder.get()),
				"a jobless villager could take a Crafter's Workbench with the switch off");
		});
		helper.runAfterDelay(600, () -> {
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 3 && chest.countItem(Items.OAK_SLAB) == 0,
				"the crafter worked with the switch off: " + chest.countItem(Items.OAK_PLANKS) + " planks, " + chest.countItem(Items.OAK_SLAB) + " slabs");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(crafter) == null, "the crafter took a job with the switch off");
			new WorkplaceConfig().apply();
			helper.assertTrue(Crafters.ENABLED, "the default should be on in GameTests");
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
			"crafter_" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath() + "_" + Long.toHexString(level.getGameTime())
				+ "_" + origin.getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(origin), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(origin.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	private static Villager crafter(GameTestHelper helper, BlockPos workbench) {
		helper.setBlock(workbench, ModBlocks.CRAFTERS_WORKBENCH);
		Villager crafter = helper.spawn(EntityType.VILLAGER, workbench.south());
		Jobs.employ(helper.getLevel(), crafter, helper.absolutePos(workbench), ModVillagers.CRAFTERS_WORKBENCH_POI, ModVillagers.CRAFTER);
		return crafter;
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

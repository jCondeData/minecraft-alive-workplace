package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-24), from docs/wiki/builder.md section 3 ("Switches") and B119, not from the code: builder switches no
 * test turned yet (workplaceBuilderOwnership off, workplaceLevelGround at its top of 8, workplaceFoundationDepth 0 with
 * every item counted) and the Blueprint Table's own list for the M38 and M39 builds, which B119's tests don't cover.
 */
public class QaBuilderSwitchesGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final int LAWN = 3;
	private static final BlockPos BENCH = new BlockPos(2, LAWN + 1, 2);
	private static final BlockPos CHEST = new BlockPos(2, LAWN + 1, 3);

	/**
	 * workplaceBuilderOwnership off: a hired builder takes a placed blueprint from a player who is neither its employer
	 * nor a friend (on, the same blueprint is refused: the switch, not something else, makes the difference).
	 */
	//$ gametest_batch BIG '"qaSwitchOwnership"'
	@GameTest(template = BIG, batch = "qaSwitchOwnership")
	public void ownershipOffLetsAStrangerGiveTheBuilderABlueprint(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean was = level.getGameRules().getBoolean(ModGameRules.BUILDER_OWNERSHIP);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(was, level.getServer()));
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(new BlockPos(2, 2, 2)));
		@SuppressWarnings("removal")
		ServerPlayer boss = helper.makeMockServerPlayerInLevel();
		@SuppressWarnings("removal")
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		ModAttachments.BUILDER_EMPLOYER.set(villager, new Employer(boss.getUUID(), "boss"));
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)), Rotation.NONE, Mirror.NONE))));

		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		Builders.assign(stranger, villager, blueprint);
		helper.assertTrue(Builders.activeSite(level, villager) == null && blueprint.getCount() == 1, "with ownership on, a stranger's blueprint was taken");

		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(false, level.getServer());
		Builders.assign(stranger, villager, blueprint);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null, "with workplaceBuilderOwnership off, the hired builder still refused a stranger's blueprint");
		helper.assertTrue(site.structure().equals(TEST_HUT), "the build started isn't the stranger's: " + site.structure());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		helper.succeed();
	}

	/**
	 * workplaceLevelGround at its top, 8: a pit 8 blocks out from the hut (the edge of the levelled ring) is filled, one
	 * 9 blocks out is left; the hut is finished.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qaSwitchLevelGroundEight"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qaSwitchLevelGroundEight")
	public void levelGroundEightLevelsEightBlocksOutAndNoFurther(GameTestHelper helper) {
		ServerLevel level = start(helper, true);
		int margin = level.getGameRules().getInt(ModGameRules.LEVEL_GROUND);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(margin, level.getServer()));
		level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(8, level.getServer());
		lawn(helper);
		BlockPos origin = new BlockPos(12, LAWN + 1, 10); // the hut's footprint: x 12..16, z 10..14
		BlockPos in = new BlockPos(16 + 8, LAWN, 12); // 8 out east
		BlockPos out = new BlockPos(12 - 9, LAWN, 12); // 9 out west
		for (BlockPos pit : List.of(in, out)) {
			helper.setBlock(pit, Blocks.AIR);
		}
		Villager builder = builder(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement);
		helper.assertTrue(site != null, "the hut didn't start");
		UUID id = site.id();
		BuildPlan plan = site.plan(level);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the hut isn't finished: " + site.stage() + ", " + site.status());
			helper.assertTrue(plan.unfinished(level).isEmpty(), "the hut isn't as drawn");
			helper.assertTrue(!helper.getBlockState(in).isAir(), "the pit 8 blocks out wasn't filled with workplaceLevelGround 8");
			helper.assertTrue(helper.getBlockState(out).isAir(), "the pit 9 blocks out was filled with workplaceLevelGround 8: " + helper.getBlockState(out));
		});
	}

	/**
	 * workplaceFoundationDepth 0 ("no foundations"): the hut over a dip two blocks deep under three of its columns is built
	 * from a chest with nothing put into the dip, and the chest gives exactly the hut's own blocks, none for a foundation.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaSwitchFoundationZero"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaSwitchFoundationZero")
	public void foundationDepthZeroPutsNothingIntoTheDip(GameTestHelper helper) {
		ServerLevel level = start(helper, false);
		int depth = level.getGameRules().getInt(ModGameRules.FOUNDATION_DEPTH);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH).set(depth, level.getServer()));
		level.getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH).set(0, level.getServer());
		lawn(helper);
		BlockPos origin = new BlockPos(12, LAWN + 1, 12);
		List<BlockPos> dip = new ArrayList<>();
		for (int x = 13; x <= 15; x++) {
			for (int y = LAWN - 1; y <= LAWN; y++) {
				BlockPos p = new BlockPos(x, y, 14);
				helper.setBlock(p, Blocks.AIR);
				dip.add(p);
			}
		}
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		ItemStack[] stock = {new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.TORCH, 2),
			new ItemStack(Items.OAK_DOOR, 2), new ItemStack(Items.DIRT, 64)};
		for (int i = 0; i < stock.length; i++) {
			chest.setItem(i, stock[i].copy());
		}
		Villager builder = builder(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement);
		helper.assertTrue(site != null, "the hut didn't start");
		UUID id = site.id();
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan.steps(BuildPlan.Stage.FOUNDATION).isEmpty(), "with workplaceFoundationDepth 0 the plan still has "
			+ plan.steps(BuildPlan.Stage.FOUNDATION).size() + " foundation block(s)");
		long cobble = plan.steps(BuildPlan.Stage.STRUCTURE).stream().filter(s -> s.state().is(Blocks.COBBLESTONE)).count();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the hut isn't finished: " + site.stage() + ", " + site.status() + ", missing " + site.missing());
			helper.assertTrue(plan.unfinished(level).isEmpty(), "the hut isn't as drawn");
			for (BlockPos p : dip) {
				helper.assertTrue(helper.getBlockState(p).isAir(), "something went into the dip at " + p.toShortString() + " with no foundations: " + helper.getBlockState(p));
			}
			int used = 64 - chest.countItem(Items.COBBLESTONE) - ModAttachments.BUILDER_BAG.getOrCreate(builder).count(Items.COBBLESTONE);
			helper.assertValueEqual(used, (int) cobble, "cobblestone used (the hut's own walls and floor)");
			helper.assertValueEqual(chest.countItem(Items.DIRT) + ModAttachments.BUILDER_BAG.getOrCreate(builder).count(Items.DIRT), 64, "dirt left");
		});
	}

	/**
	 * B119's rule for the expansions its own tests don't check: with every gate closed, as on a player's server, the
	 * Blueprint Table's list holds neither the Mega Shrine (M38, villageMegas) nor the Train Station (M39, villageTrains),
	 * both still load by id, and builds of no expansion stay listed.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaSwitchTableGates"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaSwitchTableGates")
	public void theTableHidesTheM38AndM39BuildsWhileTheirExpansionsAreClosed(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertFalse(Expansions.M38 || Expansions.M39, "M38 or M39 is complete: this test's closed half no longer applies");
		List<ResourceLocation> gated = List.of(StarterBlueprints.MEGA_SHRINE.id(), StarterBlueprints.TRAIN_STATION.id());
		List<String> problems = new ArrayList<>();
		ExpansionGateGameTests.asPlayer(() -> {
			List<ResourceLocation> listed = TableServer.listing(server).stream().map(e -> e.id()).toList();
			for (ResourceLocation id : gated) {
				if (listed.contains(id)) {
					problems.add(id + " is on the Table with its expansion closed");
				}
				if (StarterBlueprints.offered(id)) {
					problems.add(id + " is offered with its expansion closed");
				}
				if (BlueprintLibrary.get(server, id).isEmpty()) {
					problems.add(id + " no longer loads by id");
				}
			}
			if (!listed.contains(StarterBlueprints.STARTER_COTTAGE.id()) || !listed.contains(StarterBlueprints.WELL.id())) {
				problems.add("a build of no expansion left the Table");
			}
		});
		helper.assertTrue(Expansions.openForTests, "the gates weren't reopened");
		helper.assertTrue(StarterBlueprints.offered(StarterBlueprints.MEGA_SHRINE.id()), "the Mega Shrine isn't offered with M38 open");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static ServerLevel start(GameTestHelper helper, boolean free) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		boolean wasFree = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(wasFree, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		return level;
	}

	private static void lawn(GameTestHelper helper) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, LAWN - 1, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, LAWN, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	private static Villager builder(GameTestHelper helper) {
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, LAWN + 1, 3));
		Builders.employ(helper.getLevel(), villager, helper.absolutePos(BENCH));
		return villager;
	}
}

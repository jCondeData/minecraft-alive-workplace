package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.RepairAsks;
import io.github.jcondedata.aliveworkplace.build.Scaffolds;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA (2026-10-26), from the spec, not the code: docs/wiki/builder.md section 3 ("Switches"), docs/wiki/scaffolding.md,
 * docs/guide/builder.md and docs/guide/scan-tool.md. Boundary values and switches no test turned yet:
 * workplaceFoundationDepth at 1 and 2 against a dip two deep, a blueprint switched to leave the ground ("Building in a
 * hillside?") with workplaceLevelGround at 8, builderScaffolding turned off while scaffolding stands, a scan of a hollow
 * room built over solid ground, two scans saved under one name, repairAsk turned off while a question waits, and
 * builderRepairs off with repairAsk on.
 */
public class QaBuilderSwitchEdgesGameTests implements FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final ResourceLocation PILLAR = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_pillar");
	private static final int LAWN = 3;
	private static final BlockPos BENCH = new BlockPos(2, LAWN + 1, 2);

	/**
	 * workplaceFoundationDepth 1 ("How deep to fill under a build on uneven ground"): under a dip two deep, the block
	 * right under the hut's floor is filled and the one below it is left; the hut is finished as drawn.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaEdgeFoundationOne"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaEdgeFoundationOne")
	public void foundationDepthOneFillsOnlyTheTopOfATwoDeepDip(GameTestHelper helper) {
		foundationDip(helper, 1);
	}

	/** workplaceFoundationDepth 2: the same dip two deep is filled to its bottom. */
	//$ gametest_ticks_batch HUGE '5000' '"qaEdgeFoundationTwo"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaEdgeFoundationTwo")
	public void foundationDepthTwoFillsATwoDeepDipToTheBottom(GameTestHelper helper) {
		foundationDip(helper, 2);
	}

	private static void foundationDip(GameTestHelper helper, int depthRule) {
		ServerLevel level = start(helper, true);
		int depth = level.getGameRules().getInt(ModGameRules.FOUNDATION_DEPTH);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH).set(depth, level.getServer()));
		level.getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH).set(depthRule, level.getServer());
		lawn(helper);
		BlockPos origin = new BlockPos(12, LAWN + 1, 12); // the hut's footprint: x 12..16, z 12..16
		List<BlockPos> top = new ArrayList<>();
		List<BlockPos> bottom = new ArrayList<>();
		for (int x = 13; x <= 15; x++) {
			BlockPos up = new BlockPos(x, LAWN, 14);
			helper.setBlock(up, Blocks.AIR);
			helper.setBlock(up.below(), Blocks.AIR);
			top.add(up);
			bottom.add(up.below());
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
			for (BlockPos p : top) {
				helper.assertTrue(!helper.getBlockState(p).isAir(), "with workplaceFoundationDepth " + depthRule + " the block right under the floor at "
					+ p.toShortString() + " wasn't filled");
			}
			for (BlockPos p : bottom) {
				boolean filled = !helper.getBlockState(p).isAir();
				helper.assertTrue(filled == (depthRule >= 2), "with workplaceFoundationDepth " + depthRule + " the block two under the floor at "
					+ p.toShortString() + " is " + helper.getBlockState(p));
			}
		});
	}

	/**
	 * Guide: "Building in a hillside? Right-click the air with a placed blueprint to switch off the leveling for that
	 * build." A blueprint so switched, handed to a builder with workplaceLevelGround at 8, leaves a pit and a mound right
	 * beside the hut as they were; the hut is finished.
	 */
	//$ gametest_ticks_batch HUGE '5000' '"qaEdgeHillside"'
	@GameTest(template = HUGE, timeoutTicks = 5000, batch = "qaEdgeHillside")
	public void aHillsideBlueprintLeavesTheGroundWithLevelGroundAtEight(GameTestHelper helper) {
		ServerLevel level = start(helper, true);
		int margin = level.getGameRules().getInt(ModGameRules.LEVEL_GROUND);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(margin, level.getServer()));
		level.getGameRules().getRule(ModGameRules.LEVEL_GROUND).set(8, level.getServer());
		lawn(helper);
		BlockPos origin = new BlockPos(12, LAWN + 1, 10); // x 12..16, z 10..14
		BlockPos pit = new BlockPos(17, LAWN, 12); // 1 out east
		BlockPos mound = new BlockPos(10, LAWN + 1, 12); // 2 out west
		helper.setBlock(pit, Blocks.AIR);
		helper.setBlock(mound, Blocks.DIRT);
		Villager builder = builder(helper);
		@SuppressWarnings("removal")
		ServerPlayer boss = helper.makeMockServerPlayerInLevel();
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(boss.getUUID(), "boss"));
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
		BlueprintData data = BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE))).withLevelGround(false);
		blueprint.set(ModComponents.BLUEPRINT, data);
		Builders.assign(boss, builder, blueprint);
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null, "the builder didn't take the hillside blueprint");
		helper.assertTrue(!site.levelGround(), "the site levels the ground though the blueprint was switched to leave it");
		UUID id = site.id();
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(id));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the hut isn't finished: " + site.stage() + ", " + site.status());
			helper.assertTrue(site.plan(level).unfinished(level).isEmpty(), "the hut isn't as drawn");
			helper.assertTrue(helper.getBlockState(pit).isAir(), "the pit beside a hillside build was filled: " + helper.getBlockState(pit));
			helper.assertTrue(helper.getBlockState(mound).is(Blocks.DIRT), "the mound beside a hillside build was dug: " + helper.getBlockState(mound));
		});
	}

	/**
	 * builderScaffolding turned off while the builder's scaffolding stands up the pillar (scaffolding.md: it "takes it
	 * down ... and the scaffolding goes back into its chests"; config: "Off: those blocks are skipped"): the build still
	 * ends, no scaffold is left standing, and all 64 come back (chest and bag).
	 */
	//$ gametest_ticks_batch BIG '12000' '"qaEdgeScaffoldOffMid"'
	@GameTest(template = BIG, timeoutTicks = 12000, batch = "qaEdgeScaffoldOffMid")
	public void scaffoldingSwitchedOffMidBuildLeavesNoneStandingAndLosesNone(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean was = Scaffolds.ENABLED;
		Scaffolds.ENABLED = true;
		Leftovers.after(helper, () -> Scaffolds.ENABLED = was);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(3, 2, 3);
		BlockPos chestAt = new BlockPos(3, 2, 5);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 14));
		chest.setItem(1, new ItemStack(Items.POLISHED_ANDESITE, 25));
		chest.setItem(2, new ItemStack(Items.SCAFFOLDING, 64));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		Builders.employ(level, villager, helper.absolutePos(bench));
		BuildSite site = Builders.start(level, villager, null, PILLAR,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(11, 2, 11)), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the pillar didn't start");
		}
		UUID id = site.id();
		int[] most = {0};
		long[] offAt = {-1};
		helper.onEachTick(() -> {
			most[0] = Math.max(most[0], site.scaffolds().size());
			if (offAt[0] < 0 && site.scaffolds().size() >= 3) {
				Scaffolds.ENABLED = false;
				offAt[0] = helper.getTick();
				LOGGER.info("qa scaffold off mid-build: switched off at tick {} with {} scaffold(s) up", offAt[0], site.scaffolds().size());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(offAt[0] >= 0, "no scaffolding went up before the switch (most " + most[0] + ")");
			helper.assertTrue(BuildSiteManager.get(level).get(id) == null, "the pillar never ended after builderScaffolding went off: stage "
				+ site.stage() + ", " + site.status() + ", " + site.scaffolds().size() + " scaffold(s) recorded");
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(21, 17, 21)))) {
				if (level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
					throw new GameTestAssertException("scaffolding left standing at " + helper.relativePos(p) + " after builderScaffolding went off");
				}
			}
			int back = chest.countItem(Items.SCAFFOLDING) + QaScaffoldingGameTests.bagCount(villager);
			helper.assertValueEqual(back, 64, "scaffolding back in the chest and bag");
		});
	}

	/**
	 * Scan Tool guide ("Builders build it like any other blueprint") and the builder guide ("The builder clears the ground
	 * first"): a hollow cobblestone room, scanned with real clicks, built over a solid block of dirt comes out hollow,
	 * its shell whole and its inside clear.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"qaEdgeHollowScan"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "qaEdgeHollowScan")
	public void aScannedHollowRoomIsBuiltHollowOverSolidGround(GameTestHelper helper) {
		ServerLevel l = start(helper, true);
		BlockPos src = new BlockPos(18, 2, 18);
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 3, 4)) {
			helper.setBlock(src.offset(p), shell(p) ? Blocks.COBBLESTONE : Blocks.AIR);
		}
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SCAN_TOOL));
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 1));
		QaStageThreeGameTests.click(helper, player, src);
		QaStageThreeGameTests.click(helper, player, src.offset(4, 3, 4));
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the scan said " + QaStageThreeGameTests.last(seen));
		ResourceLocation id = scanned(player).get(0);

		BlockPos origin = new BlockPos(9, 2, 6);
		for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 3, 4)) {
			helper.setBlock(origin.offset(p), Blocks.DIRT);
		}
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 2));
		Builders.employ(l, builder, helper.absolutePos(new BlockPos(2, 2, 2)));
		BuildSite site = Builders.start(l, builder, null, id,
			new BlueprintData.Placement(l.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE));
		if (site == null) {
			throw new GameTestAssertException("the builder didn't take the scanned blueprint " + id);
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(l).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			List<String> wrong = new ArrayList<>();
			for (BlockPos p : BlockPos.betweenClosed(0, 0, 0, 4, 3, 4)) {
				Block there = helper.getBlockState(origin.offset(p)).getBlock();
				Block expected = shell(p) ? Blocks.COBBLESTONE : Blocks.AIR;
				if (there != expected) {
					wrong.add(p.toShortString() + "=" + there);
				}
			}
			helper.assertTrue(wrong.isEmpty(), "the scanned hollow room built over dirt is wrong at " + wrong.size() + " spot(s): " + wrong);
		});
	}

	/**
	 * Scan Tool guide: "Rename the Scan Tool in an anvil first, and the build is called that" and "What you save goes
	 * into the Blueprint Table's library": two different builds saved under the same name are both kept, the first
	 * blueprint still the first build (a second save doesn't overwrite it).
	 */
	//$ gametest_batch BIG '"qaEdgeScanSameName"'
	@GameTest(template = BIG, batch = "qaEdgeScanSameName")
	public void twoScansUnderOneNameKeepBothBuilds(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = new BlockPos(4, 2, 4);
		BlockPos b = new BlockPos(5, 2, 4);
		helper.setBlock(a, Blocks.STONE);
		helper.setBlock(b, Blocks.STONE);
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, seen);
		ItemStack tool = new ItemStack(ModItems.SCAN_TOOL);
		String name = "QA Twin " + Long.toHexString(level.getGameTime() % 0xfffff);
		tool.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		player.setItemInHand(InteractionHand.MAIN_HAND, tool);
		player.getInventory().add(new ItemStack(ModItems.BLANK_BLUEPRINT, 2));
		scan(helper, player, a, b);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the first scan said " + QaStageThreeGameTests.last(seen));
		ResourceLocation first = scanned(player).get(0);
		helper.setBlock(a, Blocks.OAK_PLANKS);
		helper.setBlock(b, Blocks.OAK_PLANKS);
		scan(helper, player, a, b);
		helper.assertTrue(QaStageThreeGameTests.last(seen).equals("message.aliveworkplace.scan.saved"), "the second scan said " + QaStageThreeGameTests.last(seen));
		List<ResourceLocation> ids = scanned(player);
		helper.assertValueEqual(ids.size(), 2, "blueprints in the inventory");
		helper.assertTrue(!ids.get(0).equals(ids.get(1)), "both scans were saved as the same blueprint " + ids.get(0));
		ResourceLocation second = ids.get(0).equals(first) ? ids.get(1) : ids.get(0);
		helper.assertTrue(blocks(level, first).equals(List.of(Blocks.STONE, Blocks.STONE)), "the first scan " + first + " now holds " + blocks(level, first));
		helper.assertTrue(blocks(level, second).equals(List.of(Blocks.OAK_PLANKS, Blocks.OAK_PLANKS)), "the second scan " + second + " holds " + blocks(level, second));
		LOGGER.info("qa scan same name: {} and {}", first, second);
		helper.succeed();
	}

	/**
	 * Config: repairAsk "off: they repair by themselves". Turned off while the builder's question about a hole waits
	 * unanswered, the builder's next look repairs the hole without waiting for a Yes.
	 */
	//$ gametest_ticks_batch BIG '1600' '"qaEdgeAskOffMid"'
	@GameTest(template = BIG, timeoutTicks = 1600, batch = "qaEdgeAskOffMid")
	public void repairAskTurnedOffWhileAQuestionWaitsRepairsByThemselves(GameTestHelper helper) {
		Wall w = wall(helper, true, true);
		helper.runAfterDelay(2, () -> {
			look(helper, w);
			RepairAsks.Ask ask = BuildSiteManager.get(helper.getLevel()).repairAsk(w.placement());
			helper.assertTrue(ask != null && ask.state() == RepairAsks.State.ASKED, "the wall wasn't asked about with repairAsk on: " + ask);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(w.builder()), "a repair started before anyone said yes");
			Upkeep.ASK = false;
		});
		helper.runAfterDelay(6, () -> look(helper, w));
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_PLANKS, WALL_AT.offset(1, 0, 0));
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(w.builder()), "still repairing");
		});
	}

	/**
	 * Config: builderRepairs "Idle Builders repair buildings they finished when blocks go missing"; off (with repairAsk
	 * still on) they neither repair nor ask: no question is put on the list, and the hole and the chest stay as they are.
	 */
	//$ gametest_ticks_batch BIG '400' '"qaEdgeRepairsOffAskOn"'
	@GameTest(template = BIG, timeoutTicks = 400, batch = "qaEdgeRepairsOffAskOn")
	public void builderRepairsOffAsksNothingAndLeavesTheHole(GameTestHelper helper) {
		Wall w = wall(helper, false, true);
		helper.runAfterDelay(2, () -> {
			look(helper, w);
			RepairAsks.Ask ask = BuildSiteManager.get(helper.getLevel()).repairAsk(w.placement());
			helper.assertTrue(ask == null, "with builderRepairs off the builder still asked about the hole: " + ask);
			helper.assertFalse(ModAttachments.BUILDER_JOB.has(w.builder()), "a repair started with builderRepairs off");
		});
		helper.runAtTickTime(300, () -> {
			helper.assertBlockPresent(Blocks.AIR, WALL_AT.offset(1, 0, 0));
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).repairAsk(w.placement()) == null, "a question turned up later with builderRepairs off");
			helper.assertValueEqual(w.chest().countItem(Items.OAK_PLANKS), 8, "planks in the chest");
			helper.succeed();
		});
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private static final BlockPos HALL_AT = new BlockPos(14, 2, 14);
	private static final BlockPos WALL_AT = new BlockPos(6, 2, 6);

	private record Wall(Villager builder, BlueprintData.Placement placement, Container chest) {
	}

	/** As RepairAskGameTests' stage: a hall, a hired builder with 8 planks, and the owner's finished wall with a hole. */
	@SuppressWarnings("removal")
	private static Wall wall(GameTestHelper helper, boolean repairs, boolean ask) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		VillageHalls.RADIUS = 16;
		Upkeep.ENABLED = repairs;
		Upkeep.ASK = ask;
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Upkeep.ENABLED = enabled;
			Upkeep.ASK = asks;
			level.getServer().getPlayerList().remove(owner);
		});
		helper.setBlock(HALL_AT, ModBlocks.VILLAGE_HALL);
		for (int x = 0; x < 4; x++) {
			helper.setBlock(WALL_AT.offset(x, 0, 0), Blocks.OAK_PLANKS);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_edge_wall_" + Long.toHexString(level.getGameTime())
			+ "_" + helper.absolutePos(WALL_AT).getX());
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(WALL_AT), new Vec3i(4, 1, 1), false, Blocks.STRUCTURE_VOID);
		helper.setBlock(WALL_AT.offset(1, 0, 0), Blocks.AIR);
		BlockPos chestAt = new BlockPos(2, 2, 4);
		helper.setBlock(chestAt, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestAt);
		chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 8));
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(WALL_AT), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(id, placement, owner.getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		return new Wall(builder, placement, chest);
	}

	/** The builder looks over their buildings now, as at their next round. */
	private static void look(GameTestHelper helper, Wall w) {
		if (!ModAttachments.BUILDER_JOB.has(w.builder())) {
			Upkeep.look(helper.getLevel(), w.builder());
		}
	}

	private static void scan(GameTestHelper helper, ServerPlayer player, BlockPos from, BlockPos to) {
		QaStageThreeGameTests.click(helper, player, from);
		QaStageThreeGameTests.click(helper, player, to);
		player.setShiftKeyDown(true);
		QaStageThreeGameTests.clickAir(helper, player);
		player.setShiftKeyDown(false);
	}

	private static List<Block> blocks(ServerLevel level, ResourceLocation id) {
		Blueprint bp = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no blueprint " + id));
		return bp.blocks().stream().filter(e -> !e.state().isAir()).map(e -> e.state().getBlock()).toList();
	}

	/** The scanned blueprints' ids in the player's inventory, in slot order. */
	private static List<ResourceLocation> scanned(ServerPlayer player) {
		List<ResourceLocation> out = new ArrayList<>();
		for (ItemStack s : player.getInventory().items) {
			if (s.is(ModItems.BLUEPRINT)) {
				out.add(BlueprintItem.data(s).orElseThrow().structure());
			}
		}
		if (out.isEmpty()) {
			throw new GameTestAssertException("the scan gave no blueprint");
		}
		return out;
	}

	private static boolean shell(BlockPos p) {
		return p.getX() == 0 || p.getX() == 4 || p.getY() == 0 || p.getY() == 3 || p.getZ() == 0 || p.getZ() == 4;
	}

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

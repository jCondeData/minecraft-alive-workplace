package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * In-world tests: a real villager on a real (headless) server builds real blueprints.
 * Run with {@code ./gradlew runGameTest}. Every test must end in success; CI fails otherwise.
 */
public class BuilderGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	// Test areas: relative y=1 is the smooth-stone floor layer, so things stand at y=2.
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final BlockPos HUT_ORIGIN = new BlockPos(6, 2, 6);

	private record Setup(ServerLevel level, Villager villager, BuildSite site, BuildPlan plan) {
	}

	// --- building --------------------------------------------------------------------------

	@GameTest(template = AREA, timeoutTicks = 2400)
	public void buildsHutFromChestMaterials(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.OAK_PLANKS) == 0, "planks left in chest: " + chest.countItem(Items.OAK_PLANKS));
			helper.assertTrue(chest.countItem(ModItems.BLUEPRINT) == 1, "blueprint was not returned to the chest");
			helper.assertTrue(!s.villager().hasAttached(ModAttachments.BUILDER_JOB), "villager still has a job");
		});
	}

	/** A builder who finishes the test hut sells its upgrade (test_hut_2) from then on, and says so. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void builderSellsTheUpgradeOfWhatTheyBuilt(GameTestHelper helper) {
		ResourceLocation upgrade = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut_2");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.upgradeOf(TEST_HUT).equals(upgrade), "test_hut's upgrade should be test_hut_2");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.upgradeOf(upgrade).getPath().equals("test_hut_3"), "test_hut_2's upgrade should be test_hut_3");
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.assertFalse(io.github.jcondedata.aliveworkplace.build.UpgradeOffers.sells(s.villager(), upgrade), "the builder sold the upgrade before building the hut");
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.build.UpgradeOffers.sells(s.villager(), upgrade), "the builder doesn't sell the hut's upgrade");
			// Building it again doesn't add a second trade; there is no test_hut_3 to sell.
			helper.assertTrue(io.github.jcondedata.aliveworkplace.build.UpgradeOffers.offer(helper.getLevel(), s.villager(), TEST_HUT).isEmpty(), "offered the upgrade twice");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.build.UpgradeOffers.offer(helper.getLevel(), s.villager(), upgrade).isEmpty(), "offered an upgrade that doesn't exist");
		});
	}

	/** Once the hut is up, the builder levels the ground around it: a mound and a rock dug away, a hole filled. */
	@GameTest(template = AREA, timeoutTicks = 3000)
	public void builderLevelsTheGroundAroundABuild(GameTestHelper helper) {
		BlockPos mound = new BlockPos(5, 2, 8);
		BlockPos rock = new BlockPos(12, 2, 7);
		BlockPos hole = new BlockPos(8, 1, 11);
		BlockPos crate = new BlockPos(12, 2, 10);
		helper.setBlock(mound, Blocks.DIRT);
		helper.setBlock(mound.above(), Blocks.GRASS_BLOCK);
		helper.setBlock(rock, Blocks.STONE);
		helper.setBlock(hole, Blocks.AIR);
		helper.setBlock(crate, Blocks.OAK_PLANKS); // something built: stays
		ItemStack[] materials = java.util.stream.Stream.concat(java.util.Arrays.stream(hutMaterials()),
			java.util.stream.Stream.of(new ItemStack(Items.DIRT, 4))).toArray(ItemStack[]::new);
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, materials);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertBlockPresent(Blocks.AIR, mound);
			helper.assertBlockPresent(Blocks.AIR, mound.above());
			helper.assertBlockPresent(Blocks.AIR, rock);
			helper.assertBlockPresent(Blocks.DIRT, hole);
			helper.assertBlockPresent(Blocks.OAK_PLANKS, crate);
		});
	}

	/** A blueprint switched to "leave the ground" (right-click the air with it): the mound and the hole stay. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void blueprintCanLeaveTheGroundAsItIs(GameTestHelper helper) {
		// The switch on the item.
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack stack = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(TEST_HUT, null);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
		stack.use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertFalse(io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(player.getMainHandItem()).orElseThrow().levelGround(),
			"right-clicking the air didn't switch levelling off");

		BlockPos mound = new BlockPos(5, 2, 8);
		BlockPos hole = new BlockPos(8, 1, 11);
		helper.setBlock(mound, Blocks.DIRT);
		helper.setBlock(hole, Blocks.AIR);
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		s.site().setLevelGround(false);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertBlockPresent(Blocks.DIRT, mound);
			helper.assertBlockPresent(Blocks.AIR, hole);
		});
	}

	/**
	 * Upgrades: Starter Cottage II clicked onto a finished Starter Cottage lines up with it, and a builder only takes
	 * the old roof off and builds the new storey — the ground floor stays, furniture and all.
	 */
	// BIG_AREA: the upgraded cottage is 14 tall, and whatever sticks out of a test area stays in the world for the next
	// batch built on that spot (an old upper storey hanging over the next test's cottage stranded its builder on it).
	@GameTest(template = BIG_AREA, timeoutTicks = 14000, batch = "cottage_upgrade")
	public void builderUpgradesAFinishedCottage(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos originRel = new BlockPos(8, 2, 8);
		BlueprintData.Placement placement = placement(helper, originRel, Rotation.NONE);
		level.getStructureManager().get(StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow()
			.placeInWorld(level, helper.absolutePos(originRel), helper.absolutePos(originRel), new StructurePlaceSettings(), level.getRandom(), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.STARTER_COTTAGE.id(), placement, java.util.UUID.randomUUID());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.baseOf(StarterBlueprints.STARTER_COTTAGE_2.id())
			.equals(Optional.of(StarterBlueprints.STARTER_COTTAGE.id())), "starter_cottage_2 should upgrade starter_cottage");

		// Clicking the upgrade onto the cottage lines it up exactly.
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack upgrade = BlueprintItem.create(StarterBlueprints.STARTER_COTTAGE_2.id(), StarterBlueprints.STARTER_COTTAGE_2.size());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, upgrade);
		BlockPos wall = helper.absolutePos(originRel.offset(1, 1, 3));
		upgrade.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(wall), net.minecraft.core.Direction.WEST, wall, false)));
		helper.assertTrue(BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement).equals(Optional.of(placement)),
			"the upgrade didn't line up: " + BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement));

		// Stock only what the upgrade still needs, then build it.
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		var report = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level,
			new BlueprintData(StarterBlueprints.STARTER_COTTAGE_2.id(), Optional.of(StarterBlueprints.STARTER_COTTAGE_2.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> stacks = new java.util.ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(stacks.size() <= 27, "the upgrade needs " + stacks.size() + " stacks: more than a chest");
		ItemStack[] needed = stacks.toArray(ItemStack[]::new);
		Setup s = setup(helper, StarterBlueprints.STARTER_COTTAGE_2.id(), originRel, Rotation.NONE, needed);
		int total = s.plan().steps(BuildPlan.Stage.STRUCTURE).size() + s.plan().steps(BuildPlan.Stage.DECORATION).size();
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertBlockPresent(Blocks.RED_BED, originRel.offset(7, 1, 6));
			helper.assertBlockPresent(Blocks.LIGHT_BLUE_BED, originRel.offset(2, 6, 7));
			helper.assertTrue(s.site().placed() < total * 0.8, "placed " + s.site().placed() + " of " + total + " blocks: the ground floor should have been kept");
		});
	}

	/**
	 * An upgrade that grows sideways: Starter Cottage III clicked onto a finished Cottage II lines up with it, and a
	 * builder clears the mound where the new wing goes, builds the wing and its roof terrace, and knocks the doorway
	 * through — the two storeys already there stay.
	 */
	@GameTest(template = BIG_AREA, timeoutTicks = 14000, batch = "cottage_wing")
	public void builderGrowsACottageSideways(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos originRel = new BlockPos(3, 2, 6);
		BlueprintData.Placement placement = placement(helper, originRel, Rotation.NONE);
		level.getStructureManager().get(StarterBlueprints.STARTER_COTTAGE_2.id()).orElseThrow()
			.placeInWorld(level, helper.absolutePos(originRel), helper.absolutePos(originRel), new StructurePlaceSettings(), level.getRandom(), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.STARTER_COTTAGE_2.id(), placement, java.util.UUID.randomUUID());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.upgradeOf(StarterBlueprints.STARTER_COTTAGE_2.id())
			.equals(StarterBlueprints.STARTER_COTTAGE_3.id()), "starter_cottage_3 should upgrade starter_cottage_2");
		// Where the wing goes: a mound of dirt and a stray block of cobblestone.
		for (int x = 13; x <= 15; x++) {
			for (int z = 8; z <= 10; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
		helper.setBlock(new BlockPos(16, 2, 12), Blocks.COBBLESTONE);

		// Clicking the upgrade onto the cottage's west wall lines it up exactly.
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack upgrade = BlueprintItem.create(StarterBlueprints.STARTER_COTTAGE_3.id(), StarterBlueprints.STARTER_COTTAGE_3.size());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, upgrade);
		BlockPos wall = helper.absolutePos(originRel.offset(1, 1, 3));
		upgrade.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(wall), net.minecraft.core.Direction.WEST, wall, false)));
		helper.assertTrue(BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement).equals(Optional.of(placement)),
			"the upgrade didn't line up: " + BlueprintItem.data(player.getMainHandItem()).flatMap(BlueprintData::placement));

		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		var report = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level,
			new BlueprintData(StarterBlueprints.STARTER_COTTAGE_3.id(), Optional.of(StarterBlueprints.STARTER_COTTAGE_3.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> stacks = new java.util.ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(stacks.size() <= 27, "the upgrade needs " + stacks.size() + " stacks: more than a chest");
		Setup s = setup(helper, StarterBlueprints.STARTER_COTTAGE_3.id(), originRel, Rotation.NONE, stacks.toArray(ItemStack[]::new));
		int total = s.plan().steps(BuildPlan.Stage.STRUCTURE).size() + s.plan().steps(BuildPlan.Stage.DECORATION).size();
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertBlockPresent(Blocks.AIR, originRel.offset(12, 1, 2)); // where the mound was
			helper.assertBlockPresent(Blocks.AIR, originRel.offset(9, 1, 4)); // the doorway through
			helper.assertBlockPresent(Blocks.SMOKER, originRel.offset(14, 1, 6));
			helper.assertBlockPresent(Blocks.OAK_DOOR, originRel.offset(13, 1, 3)); // the wing's own door
			helper.assertBlockPresent(Blocks.LANTERN, originRel.offset(16, 2, 2));
			helper.assertBlockPresent(Blocks.RED_BED, originRel.offset(7, 1, 6));
			helper.assertBlockPresent(Blocks.LIGHT_BLUE_BED, originRel.offset(2, 6, 7));
			helper.assertTrue(s.site().placed() < total * 0.4, "placed " + s.site().placed() + " of " + total + " blocks: the cottage should have been kept");
		});
	}

	/**
	 * The village as one: the builder's chest is empty, but a miner in the village has the materials in theirs — the
	 * builder walks over and takes them.
	 */
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "village_share")
	public void builderTakesMaterialsFromAnotherWorkersChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		BlockPos minersBench = new BlockPos(14, 2, 2);
		BlockPos minersChest = new BlockPos(14, 2, 4);
		helper.setBlock(minersBench, ModBlocks.MINERS_BENCH);
		helper.setBlock(minersChest, Blocks.CHEST);
		fill(helper.getBlockEntity(minersChest), hutMaterials());
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(helper.getLevel(), miner, helper.absolutePos(minersBench));
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0;
		});
	}

	/** Workers hired by different players who aren't friends keep to their own chests: the builder waits. */
	@GameTest(template = AREA, timeoutTicks = 400, batch = "village_strangers")
	public void workersOfDifferentPlayersDontShare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		BlockPos minersBench = new BlockPos(14, 2, 2);
		BlockPos minersChest = new BlockPos(14, 2, 4);
		helper.setBlock(minersBench, ModBlocks.MINERS_BENCH);
		helper.setBlock(minersChest, Blocks.CHEST);
		fill(helper.getBlockEntity(minersChest), hutMaterials());
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(helper.getLevel(), miner, helper.absolutePos(minersBench));
		miner.setAttached(ModAttachments.BUILDER_EMPLOYER, new io.github.jcondedata.aliveworkplace.build.Employer(java.util.UUID.randomUUID(), "Bea"));
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE);
		s.villager().setAttached(ModAttachments.BUILDER_EMPLOYER, new io.github.jcondedata.aliveworkplace.build.Employer(java.util.UUID.randomUUID(), "Al"));
		helper.assertFalse(io.github.jcondedata.aliveworkplace.work.Village.sharesWith(helper.getLevel(), s.villager(), miner), "strangers' workers share");
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder should wait, not " + s.site().status());
			Container chest = helper.getBlockEntity(minersChest);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 25, "the builder took from a stranger's miner");
			io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0;
			helper.succeed();
		});
	}

	/** The Storehouse's board lists what the builder is missing, and a click hands over the player's into the builder's chest. */
	@GameTest(template = AREA, timeoutTicks = 600, batch = "storehouse_board")
	public void storehouseBoardShowsWhatTheBuilderIsMissing(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		// Everything but the cobblestone.
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH));
		BlockPos storehouse = new BlockPos(14, 2, 2);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "the builder should be waiting, not " + s.site().status());
			net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.getInventory().add(new ItemStack(Items.COBBLESTONE, 40));
			io.github.jcondedata.aliveworkplace.work.ChoiceMenu board = io.github.jcondedata.aliveworkplace.store.StorehouseBoard.boardForTest(player,
				helper.absolutePos(storehouse));
			int slot = -1;
			for (int i = 0; i < io.github.jcondedata.aliveworkplace.work.ChoiceMenu.SIZE; i++) {
				if (board.icon(i).is(Items.COBBLESTONE)) {
					slot = i;
				}
			}
			helper.assertTrue(slot >= 0, "the board doesn't show the missing cobblestone");
			helper.assertTrue(board.icon(slot).getCount() == 25, "the board asks for " + board.icon(slot).getCount() + " cobblestone");
			board.press(slot, player);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 25, "the builder's chest got " + chest.countItem(Items.COBBLESTONE));
			helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 15, "the player kept " + player.getInventory().countItem(Items.COBBLESTONE));
			io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0;
			helper.succeed();
		});
	}

	/** Crafters plan with the game's recipes, and further down: stairs from planks from logs, fences from logs too. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void craftersPlanWithTheGamesRecipes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var CRAFTING = io.github.jcondedata.aliveworkplace.craft.Crafting.Kind.CRAFTING;
		var CUTTING = io.github.jcondedata.aliveworkplace.craft.Crafting.Kind.STONECUTTING;
		var stairs = io.github.jcondedata.aliveworkplace.craft.Crafting.plan(level, CRAFTING, Items.OAK_STAIRS, 10, Map.of(Items.OAK_PLANKS, 30L));
		helper.assertTrue(stairs != null && stairs.takes().equals(Map.of(Items.OAK_PLANKS, 18)) && stairs.makes().equals(Map.of(Items.OAK_STAIRS, 12)),
			"stairs from planks: " + stairs);
		var fromLogs = io.github.jcondedata.aliveworkplace.craft.Crafting.plan(level, CRAFTING, Items.OAK_STAIRS, 10, Map.of(Items.OAK_LOG, 10L));
		helper.assertTrue(fromLogs != null && fromLogs.takes().equals(Map.of(Items.OAK_LOG, 5))
			&& fromLogs.makes().equals(Map.of(Items.OAK_STAIRS, 12, Items.OAK_PLANKS, 2)), "stairs from logs: " + fromLogs);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.craft.Crafting.plan(level, CRAFTING, Items.OAK_STAIRS, 10, Map.of(Items.OAK_PLANKS, 5L)) == null,
			"five planks can't make ten stairs");
		var fences = io.github.jcondedata.aliveworkplace.craft.Crafting.plan(level, CRAFTING, Items.SPRUCE_FENCE, 12, Map.of(Items.SPRUCE_LOG, 20L));
		helper.assertTrue(fences != null && fences.makes().getOrDefault(Items.SPRUCE_FENCE, 0) == 12 && fences.takes().keySet().equals(java.util.Set.of(Items.SPRUCE_LOG))
			&& fences.takes().get(Items.SPRUCE_LOG) <= 6, "fences from logs (planks and sticks on the way): " + fences);
		var sticks = io.github.jcondedata.aliveworkplace.craft.Crafting.plan(level, CRAFTING, Items.STICK, 4, Map.of(Items.BIRCH_PLANKS, 2L));
		helper.assertTrue(sticks != null && sticks.takes().equals(Map.of(Items.BIRCH_PLANKS, 2)), "sticks from any planks: " + sticks);
		var bricks = io.github.jcondedata.aliveworkplace.craft.Crafting.plan(level, CUTTING, Items.STONE_BRICK_STAIRS, 8, Map.of(Items.STONE, 10L));
		helper.assertTrue(bricks != null && bricks.takes().equals(Map.of(Items.STONE, 8)), "stone brick stairs cut from stone: " + bricks);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.craft.Crafting.plan(level, CUTTING, Items.STONE_BRICKS, 8, Map.of(Items.COBBLESTONE, 64L)) == null,
			"cobblestone doesn't cut into stone bricks");
		helper.succeed();
	}

	/** A builder short of a door: the village's carpenter makes doors from the builder's spare planks and brings them. */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "carpenter")
	public void carpenterMakesTheDoorTheBuilderNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		// 55 planks for the hut and 6 to spare, but no door.
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, new ItemStack(Items.COBBLESTONE, 25), new ItemStack(Items.OAK_PLANKS, 61),
			new ItemStack(Items.TORCH));
		BlockPos bench = new BlockPos(14, 2, 2);
		helper.setBlock(bench, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 3));
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(bench),
			io.github.jcondedata.aliveworkplace.registry.ModVillagers.CARPENTERS_BENCH_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.CARPENTER);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.OAK_DOOR) == 2, "the two spare doors should be in the chest, not " + chest.countItem(Items.OAK_DOOR));
			helper.assertTrue(carpenter.getAttachedOrElse(ModAttachments.ITEMS_CRAFTED, 0) == 3, "the carpenter made " + carpenter.getAttachedOrElse(ModAttachments.ITEMS_CRAFTED, 0));
			io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0;
		});
	}

	/** A builder short of stone bricks: the village's (vanilla) mason cuts them from the builder's stone at the stonecutter. */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "mason")
	public void masonCutsTheStoneBricksTheBuilderNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(12, 2, 12);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.STONE_BRICKS);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "bricks_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(src), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.AIR);
		}
		Setup s = setup(helper, wall, HUT_ORIGIN, Rotation.NONE, new ItemStack(Items.STONE, 3));
		BlockPos cutter = new BlockPos(14, 2, 2);
		helper.setBlock(cutter, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 3));
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, mason, helper.absolutePos(cutter), net.minecraft.world.entity.ai.village.poi.PoiTypes.MASON,
			VillagerProfession.MASON);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(mason.getAttachedOrElse(ModAttachments.ITEMS_CRAFTED, 0) == 3, "the mason cut " + mason.getAttachedOrElse(ModAttachments.ITEMS_CRAFTED, 0));
			io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0;
		});
	}

	/** A builder short of sand and glass: the mason crushes cobblestone into sand and fires glass in the furnace by the stonecutter. */
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "mason_glass")
	public void masonCrushesSandAndFiresGlassForTheBuilder(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(12, 2, 12);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.GLASS);
			helper.setBlock(src.offset(x, 0, 1), Blocks.SAND);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "glassy_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(src), new Vec3i(3, 1, 2), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.AIR);
			helper.setBlock(src.offset(x, 0, 1), Blocks.AIR);
		}
		Setup s = setup(helper, wall, HUT_ORIGIN, Rotation.NONE, new ItemStack(Items.COBBLESTONE, 6), new ItemStack(Items.COAL, 1));
		BlockPos cutter = new BlockPos(14, 2, 2);
		helper.setBlock(cutter, Blocks.STONECUTTER);
		helper.setBlock(new BlockPos(16, 2, 2), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 3));
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, mason, helper.absolutePos(cutter), net.minecraft.world.entity.ai.village.poi.PoiTypes.MASON,
			VillagerProfession.MASON);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(mason.getAttachedOrElse(ModAttachments.ITEMS_CRAFTED, 0) == 6, "the mason made " + mason.getAttachedOrElse(ModAttachments.ITEMS_CRAFTED, 0));
		});
	}

	/** A builder short of white concrete and red wool: the village's leatherworker hardens the powder and dyes the wool. */
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "dyer")
	public void dyerMakesTheConcreteAndRedWoolTheBuilderNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(12, 2, 12);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.WHITE_CONCRETE);
			helper.setBlock(src.offset(x, 0, 1), Blocks.RED_WOOL);
		}
		ResourceLocation wall = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "coloured_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(wall).fillFromWorld(level, helper.absolutePos(src), new Vec3i(3, 1, 2), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.AIR);
			helper.setBlock(src.offset(x, 0, 1), Blocks.AIR);
		}
		Setup s = setup(helper, wall, HUT_ORIGIN, Rotation.NONE, new ItemStack(Items.WHITE_CONCRETE_POWDER, 3), new ItemStack(Items.WHITE_WOOL, 3),
			new ItemStack(Items.POPPY, 3));
		BlockPos cauldron = new BlockPos(14, 2, 2);
		helper.setBlock(cauldron, Blocks.CAULDRON);
		Villager dyer = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 3));
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, dyer, helper.absolutePos(cauldron), net.minecraft.world.entity.ai.village.poi.PoiTypes.LEATHERWORKER,
			VillagerProfession.LEATHERWORKER);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(dyer.getAttachedOrElse(ModAttachments.ITEMS_CRAFTED, 0) == 6, "the dyer made " + dyer.getAttachedOrElse(ModAttachments.ITEMS_CRAFTED, 0));
			io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0;
		});
	}

	/** A builder waiting for stripped logs: the village's lumberjack strips them from the logs in its chest. */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "stripped_logs")
	public void lumberjackStripsLogsForTheBuilder(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(12, 2, 12);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.STRIPPED_OAK_LOG);
		}
		ResourceLocation posts = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "posts_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(posts).fillFromWorld(level, helper.absolutePos(src), new Vec3i(3, 1, 1), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(src.offset(x, 0, 0), Blocks.AIR);
		}
		Setup s = setup(helper, posts, HUT_ORIGIN, Rotation.NONE);
		BlockPos block = new BlockPos(14, 2, 2);
		BlockPos chestPos = new BlockPos(14, 2, 4);
		helper.setBlock(block, ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(chestPos, Blocks.CHEST);
		Container chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(Items.IRON_AXE));
		chest.setItem(1, new ItemStack(Items.OAK_LOG, 5));
		Villager lumberjack = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 3));
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(level, lumberjack, helper.absolutePos(block),
			io.github.jcondedata.aliveworkplace.registry.ModVillagers.CHOPPING_BLOCK_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.LUMBERJACK);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(chest.countItem(Items.OAK_LOG) == 2, "3 of the 5 logs should have been stripped, " + chest.countItem(Items.OAK_LOG) + " left");
			io.github.jcondedata.aliveworkplace.work.Village.RADIUS = 0;
		});
	}

	/** A blueprint with a pool: the builder pours the water from a bucket and keeps the empty bucket. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void builderPoursWaterFromABucket(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(MaterialRules.classify(Blocks.WATER.defaultBlockState()) == MaterialRules.Kind.DECORATION, "still water should be poured");
		helper.assertTrue(MaterialRules.classify(Blocks.WATER.defaultBlockState().setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3))
			== MaterialRules.Kind.SKIP, "flowing water should be left to flow");
		helper.assertTrue(MaterialRules.requirements(Blocks.LAVA.defaultBlockState(), null).equals(List.of(new MaterialRules.Requirement(Items.LAVA_BUCKET, 1))),
			"lava costs a lava bucket");

		// The blueprint: a 3x2x3 cobblestone basin with still water in the middle, captured from the world.
		BlockPos src = new BlockPos(12, 2, 12);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(src.offset(x, 0, z), Blocks.COBBLESTONE);
				helper.setBlock(src.offset(x, 1, z), x == 1 && z == 1 ? Blocks.WATER : Blocks.COBBLESTONE);
			}
		}
		ResourceLocation pool = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "pool_" + Long.toHexString(level.getGameTime()));
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate template = level.getStructureManager().getOrCreate(pool);
		template.fillFromWorld(level, helper.absolutePos(src), new Vec3i(3, 2, 3), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(src.offset(x, 1, z), Blocks.AIR);
				helper.setBlock(src.offset(x, 0, z), Blocks.AIR);
			}
		}

		Setup s = setup(helper, pool, HUT_ORIGIN, Rotation.NONE, new ItemStack(Items.COBBLESTONE, 17), new ItemStack(Items.WATER_BUCKET));
		BlockPos middle = HUT_ORIGIN.offset(1, 1, 1);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(helper.getBlockState(middle).getFluidState().isSource(), "no still water in the middle: " + helper.getBlockState(middle));
			Container chest = helper.getBlockEntity(CHEST);
			int buckets = chest.countItem(Items.BUCKET) + s.villager().getAttachedOrCreate(ModAttachments.BUILDER_BAG).count(Items.BUCKET);
			helper.assertTrue(buckets == 1, "the empty bucket went missing (" + buckets + ")");
			helper.assertTrue(chest.countItem(Items.WATER_BUCKET) == 0, "the water bucket wasn't used");
		});
	}

	/**
	 * A blueprint with an item frame and an armor stand (captured with a diamond in the frame and a helmet on
	 * the stand): built turned a quarter, both go up empty, turned with the build, paid for from the chest.
	 */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void builderPutsUpFramesAndStandsEmpty(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos src = new BlockPos(12, 2, 12);
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 2; y++) {
				helper.setBlock(src.offset(x, y, 0), Blocks.COBBLESTONE);
			}
		}
		net.minecraft.world.entity.decoration.ItemFrame frame = new net.minecraft.world.entity.decoration.ItemFrame(level,
			helper.absolutePos(src.offset(1, 1, 1)), Direction.SOUTH);
		frame.setItem(new ItemStack(Items.DIAMOND));
		level.addFreshEntity(frame);
		net.minecraft.world.entity.decoration.ArmorStand stand = EntityType.ARMOR_STAND.create(level);
		BlockPos standAt = helper.absolutePos(src.offset(0, 0, 1));
		stand.moveTo(standAt.getX() + 0.5, standAt.getY(), standAt.getZ() + 0.5, 0, 0);
		stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		level.addFreshEntity(stand);
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "gallery_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(src), new Vec3i(3, 2, 2), true, Blocks.STRUCTURE_VOID);
		frame.discard();
		stand.discard();
		for (int x = 0; x < 3; x++) {
			for (int y = 0; y < 2; y++) {
				helper.setBlock(src.offset(x, y, 0), Blocks.AIR);
			}
		}

		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		helper.assertTrue(blueprint.entities().size() == 2, "entities in the blueprint: " + blueprint.entities());
		helper.assertTrue(blueprint.entities().stream().noneMatch(e -> e.nbt().contains("Item") || e.nbt().contains("ArmorItems")),
			"contents were copied into the blueprint");
		Blueprint again = Blueprint.fromStructureNbt(id, io.github.jcondedata.aliveworkplace.blueprint.io.BlueprintFiles.toStructureNbt(blueprint),
			net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup());
		helper.assertTrue(again.entities().size() == 2, "entities lost when saved and read back");

		Setup s = setup(helper, id, HUT_ORIGIN, Rotation.CLOCKWISE_90, new ItemStack(Items.COBBLESTONE, 6), new ItemStack(Items.ITEM_FRAME),
			new ItemStack(Items.ARMOR_STAND));
		helper.assertTrue(s.plan().entities().size() == 2, "entities in the plan");
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			List<net.minecraft.world.entity.decoration.ItemFrame> frames = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class,
				new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(17, 8, 17));
			frames.removeIf(f -> f == frame);
			helper.assertTrue(frames.size() == 1, frames.size() + " item frames");
			helper.assertTrue(frames.get(0).getItem().isEmpty(), "the frame came with its diamond");
			helper.assertTrue(frames.get(0).getDirection() == Direction.WEST, "the frame faces " + frames.get(0).getDirection() + ", not west");
			List<net.minecraft.world.entity.decoration.ArmorStand> stands = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand.class,
				new net.minecraft.world.phys.AABB(helper.absolutePos(new BlockPos(0, 0, 0))).expandTowards(17, 8, 17), e -> e != stand);
			helper.assertTrue(stands.size() == 1, stands.size() + " armor stands");
			helper.assertTrue(stands.get(0).getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).isEmpty(), "the stand came with its helmet");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.ITEM_FRAME) == 0 && chest.countItem(Items.ARMOR_STAND) == 0, "not paid for from the chest");
		});
	}

	/** With levelling turned off, the ground around a build stays as it was. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void levellingCanBeTurnedOff(GameTestHelper helper) {
		BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(helper.getLevel(), TEST_HUT).orElseThrow(),
			placement(helper, HUT_ORIGIN, Rotation.NONE), helper.getLevel(), 12, 0);
		helper.assertTrue(plan.steps(BuildPlan.Stage.LANDSCAPE).isEmpty(), "landscaping planned with a margin of 0");
		helper.setBlock(new BlockPos(5, 2, 8), Blocks.DIRT);
		BuildPlan levelled = BuildPlan.create(BlueprintLibrary.get(helper.getLevel(), TEST_HUT).orElseThrow(),
			placement(helper, HUT_ORIGIN, Rotation.NONE), helper.getLevel(), 12, 2);
		helper.assertTrue(levelled.steps(BuildPlan.Stage.LANDSCAPE).stream().anyMatch(st -> st.pos().equals(helper.absolutePos(new BlockPos(5, 2, 8)))),
			"the mound isn't in the landscaping plan");
		helper.assertTrue(levelled.steps(BuildPlan.Stage.LANDSCAPE).stream().noneMatch(st -> st.pos().getY() > helper.absolutePos(HUT_ORIGIN).getY() + 3),
			"landscaping above the build's height");
		helper.succeed();
	}

	/** Regression: a builder standing where the floor goes must step aside instead of freezing. */
	@GameTest(template = AREA, timeoutTicks = 700)
	public void builderStandingInTheFootprintStepsAside(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		s.villager().teleportTo(helper.absolutePos(HUT_ORIGIN.offset(2, 0, 2)).getX() + 0.5,
			helper.absolutePos(HUT_ORIGIN).getY(), helper.absolutePos(HUT_ORIGIN.offset(2, 0, 2)).getZ() + 0.5);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " block(s) were skipped");
		});
	}

	/**
	 * Regression for the first playtest (jungle): an overgrown site with ferns, tall grass and a bush,
	 * and a mob that will not move off a floor spot. The builder must clear, shoo and finish.
	 */
	@GameTest(template = AREA, timeoutTicks = 1500)
	public void buildsOnAnOvergrownSiteWithAMobInTheWay(GameTestHelper helper) {
		for (int x = 5; x <= 11; x++) {
			for (int z = 5; z <= 11; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				BlockPos plant = new BlockPos(x, 2, z);
				switch ((x * 7 + z * 3) % 4) {
					case 0 -> helper.setBlock(plant, Blocks.FERN);
					case 1 -> helper.setBlock(plant, Blocks.SHORT_GRASS);
					case 2 -> {
						helper.setBlock(plant, Blocks.LARGE_FERN.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
						helper.setBlock(plant.above(), Blocks.LARGE_FERN.defaultBlockState().setValue(net.minecraft.world.level.block.DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
					}
					default -> {
					}
				}
			}
		}
		helper.setBlock(HUT_ORIGIN.offset(2, 1, 2), Blocks.JUNGLE_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
		net.minecraft.world.entity.animal.Cow cow = helper.spawn(EntityType.COW, HUT_ORIGIN.offset(1, 0, 3));
		cow.setNoAi(true); // a Pokémon that just sits there
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " block(s) were skipped");
		});
	}

	/**
	 * A build placed two blocks above the ground gets a foundation: every column under its floor is
	 * filled down to the ground — except columns where the ground is already higher.
	 */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void fillsAFoundationUnderAFloatingBuild(GameTestHelper helper) {
		BlockPos origin = new BlockPos(6, 4, 6); // hut floor at y=4, ground (smooth stone) at y=1: y=2..3 to fill
		for (int z = 6; z <= 10; z++) {
			helper.setBlock(new BlockPos(6, 2, z), Blocks.STONE); // the x=6 row already has ground up to y=3
			helper.setBlock(new BlockPos(6, 3, z), Blocks.STONE);
		}
		// floor 25 + 20 columns × 2 + 5 spare = 70 cobblestone (a chest slot holds 64)
		ItemStack[] materials = {new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.COBBLESTONE, 6),
			new ItemStack(Items.OAK_PLANKS, 55), new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH)};
		Setup s = setup(helper, TEST_HUT, origin, Rotation.NONE, materials);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			for (int x = 7; x <= 10; x++) {
				for (int z = 6; z <= 10; z++) {
					for (int y = 2; y <= 3; y++) {
						BlockPos p = new BlockPos(x, y, z);
						helper.assertTrue(helper.getBlockState(p).is(Blocks.COBBLESTONE), "no foundation at " + p + ": " + helper.getBlockState(p));
					}
				}
			}
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 5, "expected exactly 5 cobblestone left, found " + chest.countItem(Items.COBBLESTONE));
		});
	}

	@GameTest(template = AREA, timeoutTicks = 1500, batch = "foundation_off")
	public void foundationsCanBeTurnedOff(GameTestHelper helper) {
		var rule = helper.getLevel().getGameRules().getRule(ModGameRules.FOUNDATION_DEPTH);
		rule.set(0, helper.getLevel().getServer());
		Setup s = setup(helper, TEST_HUT, new BlockPos(6, 4, 6), Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(helper.getBlockState(new BlockPos(8, 3, 8)).isAir(), "foundation built although workplaceFoundationDepth=0");
			rule.set(12, helper.getLevel().getServer());
		});
	}

	@GameTest(template = AREA, timeoutTicks = 3000)
	public void waitsForMaterialsThenBuilds(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE);
		AtomicBoolean supplied = new AtomicBoolean(false);
		helper.runAfterDelay(200, () -> {
			helper.assertTrue(s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS, "expected WAITING_FOR_MATERIALS, was " + s.site().status());
			int planks = s.site().missing().getOrDefault(Items.OAK_PLANKS, 0);
			helper.assertTrue(planks == 55, "expected 55 planks missing, got " + s.site().missing());
			helper.assertTrue(s.plan().unfinished(s.level()).size() == s.plan().placeableCount(), "blocks were placed without materials");
			fill(helper.getBlockEntity(CHEST), hutMaterials());
			supplied.set(true);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(supplied.get(), "materials not supplied yet");
			assertBuilt(helper, s);
		});
	}

	@GameTest(template = AREA, timeoutTicks = 2400)
	public void clearsWhateverIsInTheWay(GameTestHelper helper) {
		// Junk inside the future hut: interior (must end up empty), a wall spot and a roof spot.
		helper.setBlock(HUT_ORIGIN.offset(2, 0, 2), Blocks.DIRT); // floor spot (cobblestone wanted)
		helper.setBlock(HUT_ORIGIN.offset(2, 1, 2), Blocks.DIRT); // interior (air wanted)
		helper.setBlock(HUT_ORIGIN.offset(1, 2, 1), Blocks.DIRT); // interior (air wanted)
		helper.setBlock(HUT_ORIGIN.offset(0, 1, 2), Blocks.STONE); // wall (planks wanted)
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.DIRT) == 3, "expected the 3 cleared dirt in the chest, found " + chest.countItem(Items.DIRT));
		});
	}

	@GameTest(template = AREA, timeoutTicks = 2400, batch = "rotation")
	public void buildsRotatedBlueprints(GameTestHelper helper) {
		// Clockwise 90: template (x, z) -> (-z, x). Origin (12,2,6) puts the hut at x 8..12, z 6..10.
		Setup s = setup(helper, TEST_HUT, new BlockPos(12, 2, 6), Rotation.CLOCKWISE_90, hutMaterials());
		BlockPos doorRel = new BlockPos(12, 3, 8); // template door (2,1,0) -> (0,1,2) + origin
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			BlockState door = helper.getBlockState(doorRel);
			helper.assertTrue(door.is(Blocks.OAK_DOOR) && door.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER, "no door at " + doorRel + ": " + door);
			helper.assertTrue(door.getValue(DoorBlock.FACING) == Direction.WEST, "door should face west after rotating, faces " + door.getValue(DoorBlock.FACING));
		});
	}

	@GameTest(template = AREA, timeoutTicks = 600)
	public void cancellingReturnsTheBlueprint(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.runAfterDelay(120, () -> Builders.cancel(s.level(), s.site()));
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "site still exists");
			helper.assertTrue(!s.villager().hasAttached(ModAttachments.BUILDER_JOB), "villager still has the job");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(ModItems.BLUEPRINT) == 1, "blueprint not returned");
		});
	}

	@GameTest(template = AREA, timeoutTicks = 1200, batch = "free_materials")
	public void freeMaterialsGameruleNeedsNoChest(GameTestHelper helper) {
		helper.getLevel().getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, helper.getLevel().getServer());
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE);
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.getLevel().getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, helper.getLevel().getServer());
		});
	}

	// --- the builds we ship must actually be buildable --------------------------------------

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsStarterCottage(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.STARTER_COTTAGE);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 6000, batch = "starter_builds")
	public void buildsMarketStall(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.MARKET_STALL);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsLookoutTower(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.LOOKOUT_TOWER);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsHealingCenter(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.HEALING_CENTER);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsSupplyShop(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.SUPPLY_SHOP);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsStorehouse(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.STOREHOUSE);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsBerryFarm(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.BERRY_FARM);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "starter_builds")
	public void buildsResearchLab(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.RESEARCH_LAB);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_2")
	public void buildsTerrace(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.TERRACE);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_3")
	public void buildsSchoolhouse(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.SCHOOLHOUSE);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_3")
	public void buildsLibrary(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.LIBRARY);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_3")
	public void buildsRanch(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.RANCH);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_3")
	public void buildsApiaryGarden(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.APIARY_GARDEN);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_3")
	public void buildsFlowerShop(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.FLOWER_SHOP);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_3")
	public void buildsGraveyard(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.GRAVEYARD);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_2")
	public void buildsTownHall(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.TOWN_HALL);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_2")
	public void buildsInn(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.INN);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "starter_builds_4")
	public void buildsStoneHouse(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.STONE_HOUSE);
	}

	/** The whole Stone House III from bare ground (it's deeper than a starter build, so it starts further back). */
	@GameTest(template = BIG_AREA, timeoutTicks = 40000, batch = "starter_builds_5")
	public void buildsStoneHouseIII(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.STONE_HOUSE_3, new BlockPos(9, 2, 8));
	}

	/** A blueprint in another style is built like any other: the Stone House in dark oak and deepslate. */
	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "styles")
	public void buildsAStyledStoneHouse(GameTestHelper helper) {
		buildStarter(helper, new StarterBlueprints.Entry(io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE.id(),
			"dark_oak"), StarterBlueprints.STONE_HOUSE.size()));
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "defences")
	public void buildsPalisade(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.PALISADE);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "defences")
	public void buildsPalisadeGate(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.PALISADE_GATE);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "defences")
	public void buildsStoneWall(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.STONE_WALL);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 20000, batch = "defences")
	public void buildsWallTower(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.WALL_TOWER);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 30000, batch = "defences")
	public void buildsGatehouse(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.GATEHOUSE);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "decorations")
	public void buildsWell(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.WELL_2);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 6000, batch = "decorations")
	public void buildsStreetLamp(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.STREET_LAMP);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 6000, batch = "decorations")
	public void buildsParkBench(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.PARK_BENCH);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "decorations")
	public void buildsFountain(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.FOUNTAIN);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 12000, batch = "decorations")
	public void buildsGazebo(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.GAZEBO);
	}

	@GameTest(template = BIG_AREA, timeoutTicks = 20000, batch = "decorations")
	public void buildsMarketSquare(GameTestHelper helper) {
		buildStarter(helper, StarterBlueprints.MARKET_SQUARE);
	}

	// --- deconstruction ---------------------------------------------------------------------

	/** Sneak-given blueprint: the builder takes the hut down and puts exactly its blocks in the chest. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void takesABuildingDownAndReturnsTheBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate hut = level.getStructureManager().get(TEST_HUT).orElseThrow();
		BlockPos origin = helper.absolutePos(HUT_ORIGIN);
		hut.placeInWorld(level, origin, origin, new net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings(),
			level.getRandom(), net.minecraft.world.level.block.Block.UPDATE_ALL);
		BlockPos foreign = HUT_ORIGIN.offset(0, 1, 2);
		helper.setBlock(foreign, Blocks.STONE); // not what the blueprint says: must be left alone
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE);
		s.site().setDeconstruction();
		BuildPlan plan = s.site().plan(level);
		helper.assertTrue(plan.isDeconstruction(), "site should be taking the hut down");
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(s.site().id()) == null, "still taking it down: " + s.site().stage()
				+ " " + Math.round(s.site().progress(plan) * 100) + "%");
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) still standing");
			helper.assertBlockPresent(Blocks.STONE, foreign);
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 25 && chest.countItem(Items.OAK_PLANKS) == 54
					&& chest.countItem(Items.OAK_DOOR) == 1 && chest.countItem(Items.TORCH) == 1 && chest.countItem(ModItems.BLUEPRINT) == 1,
				"chest has " + chest.countItem(Items.COBBLESTONE) + " cobblestone, " + chest.countItem(Items.OAK_PLANKS) + " planks, "
					+ chest.countItem(Items.OAK_DOOR) + " door, " + chest.countItem(Items.TORCH) + " torch, " + chest.countItem(ModItems.BLUEPRINT) + " blueprint");
		});
	}

	// --- permissions -------------------------------------------------------------------------

	/** A builder takes orders from its employer and their friends only (operators aside). */
	@GameTest(template = AREA)
	public void buildersOnlyTakeOrdersFromTheirEmployerAndFriends(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		net.minecraft.server.level.ServerPlayer boss = helper.makeMockServerPlayerInLevel();
		net.minecraft.server.level.ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(stranger, villager), "an unhired builder takes anyone's orders");
		villager.setAttached(ModAttachments.BUILDER_EMPLOYER, new io.github.jcondedata.aliveworkplace.build.Employer(boss.getUUID(), "boss"));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(boss, villager), "the employer must be obeyed");
		helper.assertFalse(io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(stranger, villager), "a stranger must not give orders");

		// A stranger's blueprint is refused and stays in their hand.
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
		blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT,
			BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement(helper, HUT_ORIGIN, Rotation.NONE))));
		Builders.assign(stranger, villager, blueprint);
		helper.assertTrue(Builders.activeSite(level, villager) == null && blueprint.getCount() == 1, "the stranger's blueprint was taken");

		io.github.jcondedata.aliveworkplace.build.Friends friends = io.github.jcondedata.aliveworkplace.build.Friends.get(level.getServer());
		friends.add(boss.getUUID(), stranger.getUUID(), "friend");
		try {
			helper.assertTrue(io.github.jcondedata.aliveworkplace.build.Friends.mayCommand(stranger, villager), "a friend may give orders");
			Builders.assign(stranger, villager, blueprint);
			BuildSite site = Builders.activeSite(level, villager);
			helper.assertTrue(site != null, "the friend's blueprint should start a build");
			helper.assertTrue(Builders.isOwnerOrOp(boss, site), "the employer may cancel a friend's build for their builder");
		} finally {
			friends.remove(boss.getUUID(), stranger.getUUID());
		}
		helper.succeed();
	}

	// --- build queue -----------------------------------------------------------------------

	/** A second blueprint handed to a busy builder waits its turn, then gets built. */
	@GameTest(template = AREA, timeoutTicks = 4800)
	public void buildsQueuedBlueprintsInOrder(GameTestHelper helper) {
		ItemStack[] twoHuts = {new ItemStack(Items.COBBLESTONE, 50), new ItemStack(Items.OAK_PLANKS, 64), new ItemStack(Items.OAK_PLANKS, 46),
			new ItemStack(Items.OAK_DOOR, 2), new ItemStack(Items.TORCH, 2)};
		Setup first = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, twoHuts);
		BuildSite second = Builders.enqueue(first.level(), first.villager(), null, TEST_HUT, placement(helper, new BlockPos(11, 2, 0), Rotation.NONE));
		BuildPlan secondPlan = second.plan(first.level());
		helper.assertTrue(second.isQueued(), "second site should be queued");
		helper.assertTrue(Builders.activeSite(first.level(), first.villager()) == first.site(), "the first site should stay active");
		AtomicBoolean startedEarly = new AtomicBoolean(false);
		helper.onEachTick(() -> {
			if (BuildSiteManager.get(first.level()).get(first.site().id()) != null && (second.placed() > 0 || !second.isQueued())) {
				startedEarly.set(true);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertFalse(startedEarly.get(), "the queued build started before the first one was finished");
			assertBuilt(helper, first);
			assertBuilt(helper, new Setup(first.level(), first.villager(), second, secondPlan));
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(ModItems.BLUEPRINT) == 2, "both blueprints should be back in the chest");
		});
	}

	@GameTest(template = AREA)
	public void cancellingAQueuedBuildKeepsTheCurrentOne(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		BuildSite queued = Builders.enqueue(s.level(), s.villager(), null, TEST_HUT, placement(helper, new BlockPos(11, 2, 0), Rotation.NONE));
		helper.assertTrue(Builders.queue(s.level(), s.villager()).size() == 1, "queue should hold one site");
		Builders.cancel(s.level(), queued);
		helper.assertTrue(BuildSiteManager.get(s.level()).get(queued.id()) == null, "cancelled site still exists");
		helper.assertTrue(Builders.activeSite(s.level(), s.villager()) == s.site(), "cancelling a queued build must not stop the current one");
		Container chest = helper.getBlockEntity(CHEST);
		helper.assertTrue(chest.countItem(ModItems.BLUEPRINT) == 1, "the queued blueprint should be handed back");
		helper.succeed();
	}

	// --- crews -------------------------------------------------------------------------------

	// Close to the lead's bench (2,2,2): the neighbouring test's bench is ~22 blocks along, and must be further away.
	private static final BlockPos HELPER_BENCH = new BlockPos(5, 2, 2);
	private static final BlockPos HELPER = new BlockPos(5, 2, 3);

	/** An idle builder with a bench nearby pitches in; the build finishes correctly and both earn XP. */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "crews")
	public void idleBuildersHelpNearbyBuilds(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		s.level().getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, s.level().getServer());
		helper.setBlock(HELPER_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager mate = helper.spawn(EntityType.VILLAGER, HELPER);
		Builders.employ(s.level(), mate, helper.absolutePos(HELPER_BENCH));
		int mateXp = mate.getVillagerXp();
		AtomicBoolean helped = new AtomicBoolean(false);
		helper.onEachTick(() -> helped.compareAndSet(false, Builders.isHelping(mate)));
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			helper.assertTrue(s.site().skipped() == 0, s.site().skipped() + " block(s) were skipped");
			helper.assertTrue(helped.get(), "the idle builder never joined in");
			int byMate = s.site().placedBy(mate.getUUID(), false);
			int byLead = s.site().placedBy(s.villager().getUUID(), false);
			helper.assertTrue(byMate >= 5, "the helper placed only " + byMate + " block(s), the lead " + byLead);
			helper.assertFalse(Builders.isHelping(mate), "the helper should stop once the build is done");
			helper.assertTrue(mate.getAttachedOrCreate(ModAttachments.BUILDER_BAG).isEmpty(), "the helper kept materials");
		});
	}

	/** Handing a helper its own blueprint takes it off helping. */
	@GameTest(template = AREA, batch = "crews")
	public void aHelperGivenItsOwnBuildLeavesTheCrew(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		s.level().getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, s.level().getServer());
		helper.setBlock(HELPER_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager mate = helper.spawn(EntityType.VILLAGER, HELPER);
		Builders.employ(s.level(), mate, helper.absolutePos(HELPER_BENCH));
		// Sites left over from tests that ran earlier in this spot (their builders are gone) would compete for the helper.
		for (BuildSite other : new java.util.ArrayList<>(io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(s.level()).all())) {
			if (other != s.site() && (other.builder() == null || s.level().getEntity(other.builder()) == null)) {
				io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(s.level()).remove(other.id());
			}
		}
		BuildSite joined = Builders.recruit(s.level(), mate);
		helper.assertTrue(joined == s.site(), "the idle builder should join the nearby build; joined the one at "
			+ (joined == null ? "none" : joined.bench()));
		BuildSite own = Builders.start(s.level(), mate, null, TEST_HUT, placement(helper, new BlockPos(11, 2, 0), Rotation.NONE));
		helper.assertFalse(Builders.isHelping(mate), "still helping after getting its own build");
		helper.assertTrue(Builders.activeSite(s.level(), mate) == own, "its own build should be active");
		helper.assertTrue(own.bench() != null && own.bench().equals(helper.absolutePos(HELPER_BENCH)), "site should remember the bench");
		helper.succeed();
	}

	// --- keeping work loaded ----------------------------------------------------------------

	/** A build keeps its chunks loaded while the player who ordered it is online, and not otherwise. */
	@GameTest(template = AREA)
	public void workStaysLoadedWhileTheOwnerIsOnline(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		net.minecraft.server.level.ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BuildSite site = Builders.start(level, builder, owner, TEST_HUT, placement(helper, HUT_ORIGIN, Rotation.NONE));
		net.minecraft.world.level.ChunkPos siteChunk = new net.minecraft.world.level.ChunkPos(site.placement().origin());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.work.KeepLoaded.chunksToKeep(level).contains(siteChunk), "the site's chunk is not kept loaded");
		BuildSite orphan = Builders.start(level, helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 3)), null, TEST_HUT,
			placement(helper, new BlockPos(11, 2, 11), Rotation.NONE));
		helper.assertTrue(orphan.owner() != null, "setup");
		helper.assertFalse(level.getServer().getPlayerList().getPlayer(orphan.owner()) != null, "setup: the second site's owner is offline");
		helper.succeed();
	}

	// --- builder levels --------------------------------------------------------------------

	/** Building earns XP; crossing a threshold levels the builder up and unlocks the next blueprint for sale. */
	@GameTest(template = AREA, timeoutTicks = 2400)
	public void buildingLevelsTheBuilderUp(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.assertTrue(s.villager().getVillagerData().getLevel() == 1, "builder should start as a novice");
		helper.succeedWhen(() -> {
			assertBuilt(helper, s);
			Villager v = s.villager();
			// 85 blocks placed (/5 = 17 XP) + 10 for finishing, on top of the 1 XP employ() gives.
			helper.assertTrue(v.getVillagerXp() >= 20, "only " + v.getVillagerXp() + " XP after a build");
			helper.assertTrue(v.getVillagerData().getLevel() == 2, "builder is level " + v.getVillagerData().getLevel() + ", expected 2");
			boolean sellsStall = v.getOffers().stream().anyMatch(o -> BlueprintItem.data(o.getResult())
				.map(d -> d.structure().equals(StarterBlueprints.MARKET_STALL.id())).orElse(false));
			helper.assertTrue(sellsStall, "a level 2 builder should sell the Market Stall");
		});
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void higherLevelBuildersWorkFaster(GameTestHelper helper) {
		helper.assertTrue(BuilderLevels.delay(8, 1) == 8, "novice delay " + BuilderLevels.delay(8, 1));
		for (int level = 2; level <= 5; level++) {
			helper.assertTrue(BuilderLevels.delay(8, level) < BuilderLevels.delay(8, level - 1) || BuilderLevels.delay(8, level) == 1,
				"level " + level + " is not faster than level " + (level - 1));
		}
		helper.assertTrue(BuilderLevels.delay(8, 5) == 3, "master delay " + BuilderLevels.delay(8, 5));
		helper.assertTrue(BuilderLevels.delay(0, 5) == 0, "a delay of 0 must stay 0");
		helper.assertTrue(BuilderLevels.speedBonus(1) == 0 && BuilderLevels.speedBonus(5) == 150, "speed bonus " + BuilderLevels.speedBonus(5));
		helper.succeed();
	}

	private void buildStarter(GameTestHelper helper, StarterBlueprints.Entry entry) {
		buildStarter(helper, entry, new BlockPos(9, 2, 9));
	}

	private void buildStarter(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin) {
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id())
			.orElseThrow(() -> new GameTestAssertException("missing starter blueprint " + entry.id()));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper, origin, Rotation.NONE));
		// Stock barrels with exactly what the plan says it needs.
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			int left = e.getValue();
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				stock.add(new ItemStack(e.getKey(), n));
				left -= n;
			}
		}
		// (not at CHEST: setup() puts an empty chest there)
		BlockPos[] barrels = {new BlockPos(1, 2, 4), new BlockPos(3, 2, 4), new BlockPos(4, 2, 4), new BlockPos(5, 2, 4)};
		for (int i = 0; i < barrels.length; i++) {
			helper.setBlock(barrels[i], Blocks.BARREL);
			BaseContainerBlockEntity barrel = helper.getBlockEntity(barrels[i]);
			for (int slot = 0; slot < barrel.getContainerSize() && i * 27 + slot < stock.size(); slot++) {
				barrel.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.assertTrue(stock.size() <= barrels.length * 27, "test needs more barrels for " + entry.id());
		Setup s = setup(helper, entry.id(), origin, Rotation.NONE);
		helper.succeedWhen(() -> assertBuilt(helper, s));
	}

	// --- pure logic ------------------------------------------------------------------------

	/** Every starter build has an upgrade that keeps most of it (so a builder only builds what's new). */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starterUpgradesKeepMostOfTheirBase(GameTestHelper helper) {
		int upgrades = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.ALL) {
			Optional<ResourceLocation> baseId = io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.baseOf(entry.id());
			if (baseId.isEmpty()) {
				helper.assertTrue(BlueprintLibrary.get(helper.getLevel(), io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades.upgradeOf(entry.id())).isPresent(),
					entry.id() + " has no upgrade");
				continue;
			}
			upgrades++;
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), baseId.get()).orElseThrow();
			Blueprint upgrade = BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow();
			helper.assertTrue(upgrade.size().getX() >= base.size().getX() && upgrade.size().getY() >= base.size().getY() && upgrade.size().getZ() >= base.size().getZ(),
				entry.id() + " is smaller than " + baseId.get());
			Map<BlockPos, BlockState> up = new java.util.HashMap<>();
			upgrade.blocks().forEach(e -> up.put(e.pos(), e.state()));
			long solid = base.blocks().stream().filter(e -> !e.state().isAir()).count();
			long kept = base.blocks().stream().filter(e -> !e.state().isAir() && e.state().equals(up.get(e.pos()))).count();
			helper.assertTrue(kept >= solid * 0.6, entry.id() + " keeps only " + kept + " of " + baseId.get() + "'s " + solid + " blocks");
		}
		helper.assertTrue(upgrades == 24, "expected 24 starter upgrades, found " + upgrades);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void starterBlueprintsMatchTheirDeclaredSizesAndAreBuildable(GameTestHelper helper) {
		List<StarterBlueprints.Entry> all = new ArrayList<>(StarterBlueprints.ALL);
		all.addAll(StarterBlueprints.DECORATIONS);
		all.addAll(StarterBlueprints.DEFENCES);
		all.add(StarterBlueprints.TOWN_HALL);
		for (StarterBlueprints.Entry entry : all) {
			Optional<Blueprint> blueprint = BlueprintLibrary.get(helper.getLevel(), entry.id());
			helper.assertTrue(blueprint.isPresent(), "missing " + entry.id());
			helper.assertTrue(blueprint.get().size().equals(entry.size()),
				entry.id() + " is " + blueprint.get().size() + " but StarterBlueprints says " + entry.size());
			List<String> unbuildable = blueprint.get().blocks().stream()
				.map(Blueprint.Entry::state)
				.filter(st -> !st.isAir() && !MaterialRules.isSecondaryHalf(st) && MaterialRules.classify(st) == MaterialRules.Kind.SKIP)
				.map(BlockState::toString).distinct().collect(Collectors.toList());
			helper.assertTrue(unbuildable.isEmpty(), entry.id() + " contains blocks builders cannot place: " + unbuildable);
		}
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pottedPlantsCostAPotAndThePlant(GameTestHelper helper) {
		List<MaterialRules.Requirement> poppy = MaterialRules.requirements(Blocks.POTTED_POPPY.defaultBlockState(), null);
		helper.assertTrue(poppy.equals(List.of(new MaterialRules.Requirement(Items.FLOWER_POT, 1), new MaterialRules.Requirement(Items.POPPY, 1))),
			"potted poppy costs " + poppy);
		helper.assertTrue(MaterialRules.classify(Blocks.POTTED_CACTUS.defaultBlockState()) == MaterialRules.Kind.DECORATION, "potted plants go in the decoration pass");
		helper.assertTrue(MaterialRules.requirements(Blocks.FLOWER_POT.defaultBlockState(), null).size() == 1, "an empty pot costs just the pot");
		helper.succeed();
	}

	/** Blueprint block data can carry looks (sign text) but never free items or mobs. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blueprintDataNeverHandsOutItems(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(0, 1, 0), Blocks.CHEST);
		helper.setBlock(new BlockPos(1, 1, 0), Blocks.OAK_SIGN);
		BlockPos chestPos = helper.absolutePos(new BlockPos(0, 1, 0));
		BlockPos signPos = helper.absolutePos(new BlockPos(1, 1, 0));
		CompoundTag chestData = new CompoundTag();
		net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
		CompoundTag diamond = new CompoundTag();
		diamond.putString("id", "minecraft:diamond");
		diamond.putInt("count", 64);
		items.add(diamond);
		chestData.put("Items", items);
		chestData.putString("CustomName", "\"Loot\"");
		CompoundTag chestOut = io.github.jcondedata.aliveworkplace.build.BlockEntityData.sanitize(level, chestPos, level.getBlockState(chestPos),
			level.getBlockEntity(chestPos), chestData);
		helper.assertTrue(chestOut != null && chestOut.getAllKeys().equals(java.util.Set.of("CustomName")), "chest data should keep only its name: " + chestOut);

		CompoundTag signData = new CompoundTag();
		CompoundTag front = new CompoundTag();
		front.putString("color", "black");
		signData.put("front_text", front);
		CompoundTag hidden = new CompoundTag();
		hidden.put("stash", diamond.copy());
		CompoundTag cow = new CompoundTag();
		cow.putString("id", "minecraft:cow");
		hidden.put("pet", cow);
		signData.put("extra", hidden);
		CompoundTag signOut = io.github.jcondedata.aliveworkplace.build.BlockEntityData.sanitize(level, signPos, level.getBlockState(signPos),
			level.getBlockEntity(signPos), signData);
		helper.assertTrue(signOut != null && signOut.contains("front_text"), "sign text should be kept: " + signOut);
		helper.assertTrue(!signOut.getCompound("extra").contains("stash") && !signOut.getCompound("extra").contains("pet"),
			"nested items and mobs should be removed: " + signOut);
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void blueprintPlacementRoundTrips(GameTestHelper helper) {
		Vec3i size = new Vec3i(9, 10, 7);
		ResourceLocation dim = helper.getLevel().dimension().location();
		BlockPos anchor = new BlockPos(100, 64, -50);
		for (Direction front : Direction.Plane.HORIZONTAL) {
			Rotation rotation = BlueprintItem.rotationFacing(front);
			helper.assertTrue(rotation.rotate(Direction.NORTH) == front, "rotationFacing(" + front + ") is wrong");
			BlueprintData.Placement p = BlueprintItem.placementAt(dim, size, anchor, rotation);
			helper.assertTrue(BlueprintItem.anchorWorld(p, size).equals(anchor), "anchor does not round-trip for " + front);
		}
		// Facing north (player looking south), the build extends away from the player (+Z) and is centred on the click.
		BlueprintData.Placement north = BlueprintItem.placementAt(dim, size, anchor, Rotation.NONE);
		helper.assertTrue(north.origin().equals(anchor.offset(-4, 0, 0)), "unexpected origin " + north.origin());
		helper.succeed();
	}

	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void buildSitesSurviveSaveAndLoad(GameTestHelper helper) {
		BuildSite site = new BuildSite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "Jesse", TEST_HUT,
			new BlueprintData.Placement(helper.getLevel().dimension().location(), new BlockPos(1, 2, 3), Rotation.CLOCKWISE_180, Mirror.NONE));
		BuildPlan plan = site.plan(helper.getLevel());
		helper.assertTrue(plan != null, "test hut blueprint missing");
		site.advance();
		site.defer();
		site.markPlaced();
		CompoundTag saved = site.save();
		BuildSite loaded = BuildSite.load(saved);
		helper.assertTrue(loaded != null, "failed to load");
		helper.assertValueEqual(loaded.save(), saved, "round-tripped site");
		helper.succeed();
	}

	// --- helpers ---------------------------------------------------------------------------

	// --- preview and status --------------------------------------------------------------------

	/** The see-through preview packet carries exactly the blocks a builder places, and survives the network codec. */
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void previewCarriesTheBlocksToBuild(GameTestHelper helper) {
		Blueprint blueprint = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.STARTER_COTTAGE.id()).orElseThrow();
		PreviewNetworking.Data sent = PreviewNetworking.build(blueprint);
		io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
		net.minecraft.network.RegistryFriendlyByteBuf buf = new net.minecraft.network.RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
		PreviewNetworking.Data.CODEC.encode(buf, sent);
		PreviewNetworking.Data data = PreviewNetworking.Data.CODEC.decode(buf);
		helper.assertTrue(data.complete() && data.id().equals(blueprint.id()), "preview header wrong: " + data.id() + " complete=" + data.complete());

		Map<BlockPos, BlockState> wanted = new java.util.HashMap<>();
		for (Blueprint.Entry e : blueprint.blocks()) {
			if (MaterialRules.classify(e.state()) != MaterialRules.Kind.SKIP || MaterialRules.isSecondaryHalf(e.state())) {
				wanted.put(e.pos(), e.state());
			}
		}
		int[] b = data.blocks();
		helper.assertTrue(b.length == wanted.size() * 4, "preview has " + b.length / 4 + " blocks, expected " + wanted.size());
		boolean doorTop = false;
		for (int i = 0; i < b.length; i += 4) {
			BlockPos pos = new BlockPos(b[i], b[i + 1], b[i + 2]);
			BlockState state = net.minecraft.world.level.block.Block.stateById(data.palette().get(b[i + 3]));
			helper.assertTrue(state == wanted.get(pos), "preview block at " + pos + " is " + state + ", blueprint has " + wanted.get(pos));
			doorTop |= state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
		}
		helper.assertTrue(doorTop, "the preview should include the top half of the door");
		helper.succeed();
	}

	/** What floats above a builder's head: the build's name, its percentage and progress. */
	/** With paths on, a builder who finishes the hut lays a dirt path from its door to the village bell. */
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "builderLaysAPathToTheBell")
	public void builderLaysAPathToTheBell(GameTestHelper helper) {
		boolean paths = io.github.jcondedata.aliveworkplace.build.Paths.ENABLED;
		io.github.jcondedata.aliveworkplace.build.Paths.ENABLED = true;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.build.Paths.ENABLED = paths);
		for (int x = 0; x < 17; x++) {
			for (int z = 0; z < 17; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		helper.setBlock(new BlockPos(15, 2, 1), Blocks.BELL);
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null, "still building");
			helper.assertTrue(!s.villager().hasAttached(ModAttachments.PATH), "still laying the path: "
				+ s.villager().getAttachedOrElse(ModAttachments.PATH, java.util.List.of()).size() + " to go");
			int path = 0;
			for (int x = 0; x < 17; x++) {
				for (int z = 0; z < 17; z++) {
					path += helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.DIRT_PATH) ? 1 : 0;
				}
			}
			helper.assertTrue(path >= 4, path + " blocks of path");
			helper.assertTrue(helper.getBlockState(new BlockPos(15, 1, 2)).is(Blocks.DIRT_PATH)
				|| helper.getBlockState(new BlockPos(14, 1, 1)).is(Blocks.DIRT_PATH) || helper.getBlockState(new BlockPos(15, 1, 0)).is(Blocks.DIRT_PATH)
				|| helper.getBlockState(new BlockPos(16, 1, 1)).is(Blocks.DIRT_PATH), "the path doesn't reach the bell");
		});
	}

	@GameTest(template = AREA, timeoutTicks = 1200)
	public void builderStatusShowsBuildAndProgress(GameTestHelper helper) {
		Setup s = setup(helper, TEST_HUT, HUT_ORIGIN, Rotation.NONE, hutMaterials());
		helper.succeedWhen(() -> {
			BuilderStatusSync.Status status = BuilderStatusSync.status(s.level(), s.site(), s.villager());
			helper.assertTrue(status != null, "no status for a working builder");
			helper.assertTrue(status.entityId() == s.villager().getId(), "status is for the wrong entity");
			helper.assertTrue(status.progress() >= 0.5f, "progress still " + status.progress());
			if (!(status.title().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents title)
				|| !title.getKey().equals("message.aliveworkplace.overhead.title")
				|| !java.util.Objects.equals(title.getArgs()[1], Math.round(status.progress() * 100))) {
				throw new GameTestAssertException("unexpected title: " + status.title());
			}
		});
	}

	// --- what's still missing ----------------------------------------------------------------

	/** A placed blueprint knows what the chests by the nearest bench are short of; blocks already in place don't count. */
	@GameTest(template = AREA)
	public void placedBlueprintKnowsWhatsMissing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		BlueprintData.Placement placement = placement(helper, HUT_ORIGIN, Rotation.NONE);
		BlueprintData data = BlueprintItem.data(BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5))).orElseThrow().withPlacement(Optional.of(placement));
		helper.runAfterDelay(2, () -> { // the bench becomes a point of interest a moment after it's placed
			BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(level, TEST_HUT).orElseThrow(), placement);
			Map<Item, Integer> need = plan.materials();
			Container chest = helper.getBlockEntity(CHEST);
			int slot = 0;
			for (Map.Entry<Item, Integer> e : need.entrySet()) {
				if (e.getKey() != Items.OAK_PLANKS) {
					slot = stock(chest, slot, e.getKey(), e.getValue());
				}
			}
			var report = io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level, data).orElseThrow();
			helper.assertTrue(report.bench().equals(Optional.of(helper.absolutePos(BENCH))) && report.chests() == 1, "bench and chests: " + report);
			helper.assertTrue(report.missing().size() == 1, "only planks should be missing: " + report.missing());
			int planks = missing(report, Items.OAK_PLANKS);
			helper.assertTrue(planks == need.get(Items.OAK_PLANKS), "planks missing " + planks + " of " + need.get(Items.OAK_PLANKS));

			// A plank already where the blueprint wants one doesn't have to come from the chests.
			BuildPlan.Step plankStep = plan.steps(BuildPlan.Stage.STRUCTURE).stream().filter(s -> s.state().is(Blocks.OAK_PLANKS)).findFirst().orElseThrow();
			level.setBlockAndUpdate(plankStep.pos(), plankStep.state());
			int after = missing(io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level, data).orElseThrow(), Items.OAK_PLANKS);
			helper.assertTrue(after == planks - 1, "a plank in place still counted: " + after);

			stock(chest, slot, Items.OAK_PLANKS, planks);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.check(level, data).orElseThrow().missing().isEmpty(),
				"everything is in the chest");

			// The item carries the report for its tooltip.
			ItemStack stack = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
			stack.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT, data);
			io.github.jcondedata.aliveworkplace.build.BlueprintSupplies.tick(level, stack, (int) ((40 - level.getGameTime() % 40) % 40));
			var carried = stack.get(io.github.jcondedata.aliveworkplace.registry.ModComponents.SUPPLY_REPORT);
			helper.assertTrue(carried != null && carried.missing().isEmpty(), "the item has no report: " + carried);
			helper.succeed();
		});
	}

	private static int stock(Container chest, int slot, Item item, int count) {
		while (count > 0) {
			int n = Math.min(count, item.getDefaultMaxStackSize());
			chest.setItem(slot++, new ItemStack(item, n));
			count -= n;
		}
		return slot;
	}

	private static int missing(io.github.jcondedata.aliveworkplace.blueprint.SupplyReport report, Item item) {
		return report.missing().stream().filter(m -> m.item() == item).mapToInt(io.github.jcondedata.aliveworkplace.blueprint.SupplyReport.Missing::count).sum();
	}

	private static Setup setup(GameTestHelper helper, ResourceLocation structure, BlockPos originRel, Rotation rotation, ItemStack... chestItems) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		// Tests in a batch run side by side: keep finished builders from wandering into other tests' builds.
		// The crew tests (their own batch) turn helping back on.
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		fill(helper.getBlockEntity(CHEST), chestItems);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, villager, null, structure, placement(helper, originRel, rotation));
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("blueprint not found: " + structure);
		}
		return new Setup(level, villager, site, plan);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos originRel, Rotation rotation) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(originRel), rotation, Mirror.NONE);
	}

	private static void assertBuilt(GameTestHelper helper, Setup s) {
		helper.assertTrue(BuildSiteManager.get(s.level()).get(s.site().id()) == null,
			"still building: stage=" + s.site().stage() + " status=" + s.site().status() + " progress=" + Math.round(s.site().progress(s.plan()) * 100)
				+ "% missing=" + s.site().missing() + " builder: alive=" + s.villager().isAlive() + " at " + helper.relativePos(s.villager().blockPosition())
				+ " activity=" + s.villager().getBrain().getActiveNonCoreActivity() + " sleeping=" + s.villager().isSleeping()
				+ " jobsite=" + s.villager().getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
				+ " time=" + (s.level().getDayTime() % 24000) + " running=" + s.villager().getBrain().getRunningBehaviors());
		List<BlockPos> unfinished = s.plan().unfinished(s.level());
		if (!unfinished.isEmpty()) {
			String sample = unfinished.stream().limit(5)
				.map(p -> helper.relativePos(p) + " (blueprint " + p.subtract(s.site().placement().origin()).toShortString() + ")=" + s.level().getBlockState(p))
				.collect(Collectors.joining(", "));
			throw new GameTestAssertException(unfinished.size() + " block(s) wrong after the build, e.g. " + sample);
		}
	}

	private static ItemStack[] hutMaterials() {
		return new ItemStack[]{
			new ItemStack(Items.COBBLESTONE, 25),
			new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR),
			new ItemStack(Items.TORCH)
		};
	}

	private static void fill(Container container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
	}
}

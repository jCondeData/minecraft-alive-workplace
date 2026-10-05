package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA for ROADMAP 23.8, "Placing a build feels good", written from the spec: rotating and mirroring before placing, the
 * ghost preview shows exactly where the build goes, a cancelled site gives every material back (nothing lost, nothing
 * doubled, even when the chest is full or the cancel is sent twice), and sloped ground gets a foundation.
 */
public class QaPlacingGameTests implements FabricGameTest {
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final String BUILD_AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/**
	 * A player clicks the ground, then sneak-clicks to turn it three times and flips it: at every rotation and mirror,
	 * the ghost box is exactly the box the build plan fills, every block of the plan lies inside it, and four turns bring
	 * it back where it started.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyRotationAndMirrorBuildsInsideTheGhost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, TEST_HUT).orElseThrow();
		Vec3i size = blueprint.size();
		@SuppressWarnings("removal")
		Player player = helper.makeMockServerPlayerInLevel();
		ItemStack stack = BlueprintItem.create(TEST_HUT, null);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos ground = helper.absolutePos(new BlockPos(1, 0, 1));
		player.setShiftKeyDown(false);
		InteractionResult first = click(player, ground);
		helper.assertTrue(first.consumesAction(), "clicking the ground with a blueprint did nothing: " + first);
		BlueprintData.Placement start = BlueprintItem.data(player.getMainHandItem()).orElseThrow().placement().orElseThrow();
		BlockPos frontMiddle = BlueprintItem.anchorWorld(start, size);
		helper.assertValueEqual(frontMiddle, ground.above(), "the front middle of the ghost isn't where the player clicked");
		for (boolean mirror : new boolean[] {false, true}) {
			if (mirror) {
				BlueprintData now = BlueprintItem.data(player.getMainHandItem()).orElseThrow();
				player.getMainHandItem().set(ModComponents.BLUEPRINT, BlueprintItem.mirrored(now, true));
			}
			for (int turn = 0; turn < 4; turn++) {
				BlueprintData.Placement placement = BlueprintItem.data(player.getMainHandItem()).orElseThrow().placement().orElseThrow();
				String what = "turn " + turn + (mirror ? " mirrored" : "") + " (" + placement.rotation() + ", " + placement.mirror() + ")";
				helper.assertValueEqual(BlueprintItem.anchorWorld(placement, size), frontMiddle, what + ": the front middle moved");
				BoundingBox ghost = BlueprintOutline.bounds(placement, size);
				BuildPlan plan = BuildPlan.create(blueprint, placement);
				helper.assertValueEqual(plan.bounds(), ghost, what + ": the plan's box vs the ghost");
				for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
					for (BuildPlan.Step step : plan.steps(stage)) {
						helper.assertTrue(ghost.isInside(step.pos()), what + ": " + stage + " step at " + step.pos() + " is outside the ghost " + ghost);
					}
				}
				// Sneak-click: turn it a quarter where it stands.
				player.setShiftKeyDown(true);
				click(player, ground);
				player.setShiftKeyDown(false);
				BlueprintData.Placement turned = BlueprintItem.data(player.getMainHandItem()).orElseThrow().placement().orElseThrow();
				helper.assertValueEqual(turned.rotation(), placement.rotation().getRotated(Rotation.CLOCKWISE_90), what + ": sneak-click didn't turn it a quarter");
				helper.assertValueEqual(turned.mirror(), placement.mirror(), what + ": turning lost the mirror");
			}
			BlueprintData.Placement around = BlueprintItem.data(player.getMainHandItem()).orElseThrow().placement().orElseThrow();
			helper.assertValueEqual(around.rotation(), start.rotation(), (mirror ? "mirrored: " : "") + "four turns didn't come back");
		}
		helper.succeed();
	}

	/**
	 * Cancelled mid-build with the supply chest stuffed full: what the builder carried can't go back in the chest, so it
	 * must land on the ground, not vanish. Cancelling the same site a second time (the command sent twice) gives
	 * nothing more. Every material ends up built, in the chest or on the ground, exactly as many as there were, and
	 * exactly one blueprint comes back.
	 */
	//$ gametest_ticks_batch BUILD_AREA '4000' '"qa_placing_cancel_full"'
	@GameTest(template = BUILD_AREA, timeoutTicks = 4000, batch = "qa_placing_cancel_full")
	public void cancellingWithAFullChestLosesAndDoublesNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(4, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Map<Item, Integer> stock = new LinkedHashMap<>();
		stock.put(Items.COBBLESTONE, 25);
		stock.put(Items.OAK_PLANKS, 55);
		stock.put(Items.OAK_DOOR, 1);
		stock.put(Items.TORCH, 1);
		Container chest = helper.getBlockEntity(CHEST);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : stock.entrySet()) {
			chest.setItem(slot++, new ItemStack(e.getKey(), e.getValue()));
		}
		@SuppressWarnings("removal")
		Player owner = helper.makeMockServerPlayerInLevel();
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(6, 2, 6)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, villager, owner, TEST_HUT, placement);
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		boolean[] done = {false};
		helper.onEachTick(() -> {
			if (done[0] || site.placed() < 8 || ModAttachments.BUILDER_BAG.getOrCreate(villager).isEmpty()) {
				return;
			}
			Map<Item, Integer> carried = new LinkedHashMap<>();
			for (Item item : stock.keySet()) {
				carried.put(item, ModAttachments.BUILDER_BAG.getOrCreate(villager).count(item));
			}
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).isEmpty()) {
					chest.setItem(i, new ItemStack(Items.STICK, 64));
				}
			}
			String cmd = "workplace cancel " + site.id();
			level.getServer().getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(2), cmd);
			level.getServer().getCommands().performPrefixedCommand(owner.createCommandSourceStack().withPermission(2), cmd);
			done[0] = true;
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the cancelled site is still there");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(villager).isEmpty(), "the builder still carries "
				+ ModAttachments.BUILDER_BAG.getOrCreate(villager));
			Map<Item, Integer> built = built(level, plan.bounds());
			Map<Item, Integer> dropped = dropped(helper);
			for (Map.Entry<Item, Integer> e : stock.entrySet()) {
				int inChest = count(chest, e.getKey());
				int inWorld = built.getOrDefault(e.getKey(), 0);
				int onGround = dropped.getOrDefault(e.getKey(), 0);
				int inBag = ModAttachments.BUILDER_BAG.getOrCreate(villager).count(e.getKey());
				helper.assertValueEqual(inChest + inWorld + onGround + inBag, e.getValue(), e.getKey() + ": chest " + inChest + " + built " + inWorld
					+ " + on the ground " + onGround + " + bag " + inBag + " (carried " + carried.get(e.getKey()) + " when cancelled)");
			}
			int blueprints = countBlueprints(owner, chest) + dropped.getOrDefault(ModItems.BLUEPRINT, 0);
			helper.assertValueEqual(blueprints, 1, "blueprints back after cancelling twice");
			helper.succeed();
		});
		helper.succeedWhen(() -> helper.assertTrue(done[0], "never cancelled (placed " + site.placed() + ", stage " + site.stage() + ")"));
	}

	/**
	 * Placed by the player's click (turned to face east, then flipped) half on a one-block ledge and half over a drop:
	 * the builder finishes it, every corner of the floor stands on something (the foundation filled the drop), and no
	 * hut block lands outside the ghost box the player was shown.
	 */
	//$ gametest_ticks_batch BUILD_AREA '6000' '"qa_placing_slope"'
	@GameTest(template = BUILD_AREA, timeoutTicks = 6000, batch = "qa_placing_slope")
	public void aTurnedFlippedHutOnASlopeStandsOnItsFoundationInsideTheGhost(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(2, new ItemStack(Items.OAK_PLANKS, 64));
		chest.setItem(3, new ItemStack(Items.OAK_PLANKS, 64));
		chest.setItem(4, new ItemStack(Items.OAK_DOOR, 2));
		chest.setItem(5, new ItemStack(Items.TORCH, 4));
		chest.setItem(6, new ItemStack(Items.DIRT, 64));
		Blueprint blueprint = BlueprintLibrary.get(level, TEST_HUT).orElseThrow();
		Vec3i size = blueprint.size();

		@SuppressWarnings("removal")
		Player player = helper.makeMockServerPlayerInLevel();
		// Looking west, so the front faces east (toward the player).
		player.setYRot(90f);
		player.setYHeadRot(90f);
		ItemStack stack = BlueprintItem.create(TEST_HUT, null);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		// Clicked on top of a ledge block at y = 2, so the hut's floor is at y = 3.
		BlockPos clicked = helper.absolutePos(new BlockPos(10, 2, 4));
		level.setBlockAndUpdate(clicked, Blocks.STONE.defaultBlockState());
		click(player, clicked);
		BlueprintData data = BlueprintItem.data(player.getMainHandItem()).orElseThrow();
		data = BlueprintItem.mirrored(data, true);
		player.getMainHandItem().set(ModComponents.BLUEPRINT, data);
		BlueprintData.Placement placement = data.placement().orElseThrow();
		helper.assertValueEqual(placement.rotation(), Rotation.CLOCKWISE_90, "facing after clicking while looking west");
		BoundingBox ghost = BlueprintOutline.bounds(placement, size);
		// The slope: the two columns nearest the front stand on a ledge (y = 2), the rest over a one-block drop to the floor.
		for (int x = ghost.minX(); x <= ghost.maxX(); x++) {
			for (int z = ghost.minZ(); z <= ghost.maxZ(); z++) {
				BlockPos below = new BlockPos(x, ghost.minY() - 1, z);
				level.setBlockAndUpdate(below, x >= ghost.maxX() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
			}
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		InteractionResult given = Builders.assign((ServerPlayer) player, villager, player.getMainHandItem());
		helper.assertTrue(given.consumesAction(), "handing the blueprint over didn't start anything: " + given);
		BuildSite site = Builders.activeSite(level, villager);
		helper.assertTrue(site != null, "no site after handing the blueprint over");
		helper.assertValueEqual(site.placement(), placement, "the site isn't where the ghost was");
		BuildPlan plan = site.plan(level);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.status());
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) of the hut wrong");
			for (int x = ghost.minX(); x <= ghost.maxX(); x++) {
				for (int z = ghost.minZ(); z <= ghost.maxZ(); z++) {
					BlockPos floor = new BlockPos(x, ghost.minY(), z);
					if (!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()) {
						helper.assertTrue(!level.getBlockState(floor.below()).isAir(), "the floor at " + floor + " floats: nothing under it");
					}
				}
			}
			// Nothing of the hut outside the ghost box (at or above its floor).
			BoundingBox around = ghost.inflatedBy(3);
			for (BlockPos p : BlockPos.betweenClosed(around.minX(), ghost.minY(), around.minZ(), around.maxX(), around.maxY(), around.maxZ())) {
				BlockState s = level.getBlockState(p);
				if (!ghost.isInside(p) && (s.is(Blocks.OAK_PLANKS) || s.is(Blocks.OAK_DOOR) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.TORCH))) {
					helper.fail("a hut block " + s + " at " + p + " is outside the ghost " + ghost);
				}
			}
		});
	}

	private static InteractionResult click(Player player, BlockPos ground) {
		ItemStack held = player.getMainHandItem();
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false);
		return held.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	/** What the hut's blocks standing in {@code box} cost, by item. */
	private static Map<Item, Integer> built(ServerLevel level, BoundingBox box) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() - 4, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (p.getY() < box.minY() && !s.is(Blocks.COBBLESTONE) && !s.is(Blocks.OAK_PLANKS)) {
				continue;
			}
			Item item = s.is(Blocks.WALL_TORCH) || s.is(Blocks.TORCH) ? Items.TORCH
				: s.is(Blocks.OAK_DOOR) ? (s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? Items.OAK_DOOR : null)
				: s.is(Blocks.COBBLESTONE) || s.is(Blocks.OAK_PLANKS) ? s.getBlock().asItem() : null;
			if (item != null) {
				out.merge(item, 1, Integer::sum);
			}
		}
		return out;
	}

	private static Map<Item, Integer> dropped(GameTestHelper helper) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(48);
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			out.merge(e.getItem().getItem(), e.getItem().getCount(), Integer::sum);
		}
		return out;
	}

	private static int count(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	private static int countBlueprints(Player owner, Container chest) {
		int n = 0;
		for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
			if (owner.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
				n += owner.getInventory().getItem(i).getCount();
			}
		}
		return n + count(chest, ModItems.BLUEPRINT);
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 23.8, placing a build feels good: a placed blueprint flips where it stands; a cancelled build gives every
 * material back (what it placed stays, what the builder carried goes back to its chests) and its blueprint still placed
 * there, so handing it back carries on and clicking the ground moves it.
 */
public class PlacingGameTests implements FabricGameTest {
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final String BUILD_AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	/** Mirroring a blueprint that is already placed flips it there: same front middle, same facing, now mirrored. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void mirroringAPlacedBlueprintFlipsItWhereItStands(GameTestHelper helper) {
		Vec3i size = new Vec3i(7, 5, 4);
		BlockPos anchor = helper.absolutePos(new BlockPos(1, 2, 1));
		for (Rotation rotation : Rotation.values()) {
			BlueprintData.Placement placed = BlueprintItem.placementAt(helper.getLevel().dimension().location(), size, anchor, rotation);
			BlueprintData data = new BlueprintData(TEST_HUT, Optional.of(size), Optional.of(placed));
			BlueprintData flipped = BlueprintItem.mirrored(data, true);
			helper.assertTrue(flipped.mirrored(), "not marked mirrored");
			BlueprintData.Placement now = flipped.placement().orElseThrow();
			helper.assertValueEqual(now.mirror(), Mirror.FRONT_BACK, rotation + ": placement mirror");
			helper.assertValueEqual(now.rotation(), rotation, rotation + ": facing");
			helper.assertValueEqual(BlueprintItem.anchorWorld(now, size), anchor, rotation + ": front middle moved");
			BoundingBox before = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(placed, size);
			BoundingBox after = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(now, size);
			helper.assertTrue(before.getXSpan() == after.getXSpan() && before.getZSpan() == after.getZSpan() && before.minY() == after.minY(),
				rotation + ": footprint changed from " + before + " to " + after);
			// And back again.
			BlueprintData.Placement back = BlueprintItem.mirrored(flipped, false).placement().orElseThrow();
			helper.assertValueEqual(back, placed, rotation + ": unmirrored");
		}
		// Not placed yet: only the choice changes.
		BlueprintData loose = BlueprintItem.mirrored(new BlueprintData(TEST_HUT, Optional.of(size), Optional.empty()), true);
		helper.assertTrue(loose.mirrored() && loose.placement().isEmpty(), "an unplaced blueprint got a placement: " + loose);
		helper.succeed();
	}

	/**
	 * Cancelled halfway: every material is accounted for (in the chest or built), the builder carries nothing, and the
	 * owner gets the blueprint back still placed there. Handed back, it carries on to the end with what's left.
	 */
	//$ gametest_ticks_batch BUILD_AREA '4000' '"placing_cancel"'
	@GameTest(template = BUILD_AREA, timeoutTicks = 4000, batch = "placing_cancel")
	public void aCancelledBuildGivesEveryMaterialBack(GameTestHelper helper) {
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
		BuildSite first = Builders.start(level, villager, owner, TEST_HUT, placement);
		BuildPlan plan = first.plan(level);
		helper.assertTrue(plan != null, "no test hut blueprint");
		BuildSite[] second = {null};
		boolean[] checked = {false};
		helper.onEachTick(() -> {
			if (second[0] != null || first.placed() < 12) {
				return;
			}
			int carried = ModAttachments.BUILDER_BAG.getOrCreate(villager).count(Items.OAK_PLANKS);
			Builders.cancel(level, first);
			helper.assertTrue(BuildSiteManager.get(level).get(first.id()) == null, "the cancelled site is still there");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(villager).isEmpty(), "the builder still carries "
				+ ModAttachments.BUILDER_BAG.getOrCreate(villager));
			// Every material is either in the chest or built.
			Map<Item, Integer> built = built(level, plan.bounds());
			for (Map.Entry<Item, Integer> e : stock.entrySet()) {
				int inChest = count(chest, e.getKey());
				int inWorld = built.getOrDefault(e.getKey(), 0);
				helper.assertValueEqual(inChest + inWorld, e.getValue(), e.getKey() + " in the chest (" + inChest + ") and built (" + inWorld
					+ "), the builder having carried " + carried + " planks when cancelled");
			}
			// The blueprint comes back placed where it was.
			ItemStack blueprint = findBlueprint(owner, chest);
			helper.assertTrue(!blueprint.isEmpty(), "the blueprint didn't come back");
			BlueprintData data = BlueprintItem.data(blueprint).orElseThrow();
			helper.assertValueEqual(data.placement(), Optional.of(placement), "the returned blueprint's placement");
			// Handed back: it carries on there.
			second[0] = Builders.start(level, villager, owner, data.structure(), data.placement().get());
			checked[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(checked[0], "never cancelled (placed " + first.placed() + ")");
			helper.assertTrue(BuildSiteManager.get(level).get(second[0].id()) == null, "still building after handing it back: " + second[0].status());
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) wrong after carrying on");
			for (Item item : stock.keySet()) {
				helper.assertValueEqual(count(chest, item), 0, item + " left in the chest after the whole hut");
			}
		});
	}

	/** What the hut's blocks standing in {@code box} cost, by item. */
	private static Map<Item, Integer> built(ServerLevel level, BoundingBox box) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(p);
			Item item = s.is(Blocks.WALL_TORCH) ? Items.TORCH
				: s.is(Blocks.OAK_DOOR) ? (s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? Items.OAK_DOOR : null)
				: s.is(Blocks.COBBLESTONE) || s.is(Blocks.OAK_PLANKS) ? s.getBlock().asItem() : null;
			if (item != null) {
				out.merge(item, 1, Integer::sum);
			}
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

	private static ItemStack findBlueprint(Player owner, Container chest) {
		for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
			if (owner.getInventory().getItem(i).is(ModItems.BLUEPRINT)) {
				return owner.getInventory().getItem(i);
			}
		}
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(ModItems.BLUEPRINT)) {
				return chest.getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}
}

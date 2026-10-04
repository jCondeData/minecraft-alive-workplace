package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.MaterialList;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponents;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * 23.4: the material list you can take away. The test hut needs 25 cobblestone, 55 oak planks, an oak door and a torch;
 * written next to a Blueprint Table whose chest holds some of it, the book lists what's still to bring.
 */
public class MaterialListGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final Vec3i HUT_SIZE = new Vec3i(5, 4, 5);
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);

	/** Not placed: the whole list, nothing taken off, and it agrees with the builder's own plan. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void anUnplacedBlueprintListsEverythingItNeeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData data = BlueprintItem.data(BlueprintItem.create(TEST_HUT, HUT_SIZE)).orElseThrow();
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
		helper.assertTrue(list.bench().isEmpty(), "an unplaced list has a bench: " + list.bench());
		Map<Item, Integer> need = need(list);
		helper.assertTrue(need.equals(Map.of(Items.COBBLESTONE, 25, Items.OAK_PLANKS, 55, Items.OAK_DOOR, 1, Items.TORCH, 1)), "the list: " + need);
		helper.assertTrue(list.lines().stream().allMatch(l -> l.toBring() == l.need()), "something taken off: " + list.lines());
		helper.assertTrue(list.lines().get(0).item() == Items.OAK_PLANKS, "not biggest first: " + list.lines());
		BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(level, TEST_HUT).orElseThrow(),
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HUT), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(need.equals(plan.materials()), "the list " + need + " isn't the builder's plan " + plan.materials());
		List<String> pages = MaterialList.pages(Component.literal("Test Hut"), list).stream().map(Component::getString).toList();
		helper.assertTrue(pages.get(0).contains("It needs 82 items of 4 kinds."), "the first page: " + pages.get(0));
		helper.assertTrue(pages.get(1).startsWith("☐ 55 × Oak Planks\n☐ 25 × Cobblestone"), "the list page: " + pages.get(1));
		helper.succeed();
	}

	/** Placed by a Blueprint Table whose chest holds 10 cobblestone and all the planks: only the rest is to bring. */
	//$ gametest_ticks_batch AREA '40' '"materialList"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "materialList")
	public void aPlacedBlueprintTakesOffWhatTheChestsHold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData data = placed(helper);
		helper.runAfterDelay(2, () -> { // the table becomes a point of interest a moment after it's placed
			BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
			helper.assertTrue(list.bench().equals(Optional.of(helper.absolutePos(TABLE))), "the bench: " + list.bench());
			Map<Item, Integer> toBring = list.lines().stream().collect(Collectors.toMap(BlueprintSupplies.Line::item, BlueprintSupplies.Line::toBring));
			helper.assertTrue(toBring.equals(Map.of(Items.COBBLESTONE, 15, Items.OAK_PLANKS, 0, Items.OAK_DOOR, 1, Items.TORCH, 1)), "to bring: " + toBring);
			helper.assertTrue(list.toBring() == 17, "17 items to bring, not " + list.toBring());
			helper.assertTrue(list.lines().get(list.lines().size() - 1).item() == Items.OAK_PLANKS, "what's all there isn't last: " + list.lines());
			List<String> pages = MaterialList.pages(Component.literal("Test Hut"), list).stream().map(Component::getString).toList();
			helper.assertTrue(pages.get(0).contains("Still to bring: 17 items of 3 kinds."), "the first page: " + pages.get(0));
			helper.assertTrue(pages.get(1).equals("☐ 15 × Cobblestone (of 25)\n☐ 1 × Oak Door\n☐ 1 × Torch\n☑ Oak Planks"), "the list page: " + pages.get(1));
			helper.succeed();
		});
	}

	/** The player's way in: blueprint in the main hand, a Book and Quill in the other, right-click: a written book. */
	//$ gametest_ticks_batch AREA '40' '"materialList"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "materialList")
	public void rightClickingWithABookAndQuillWritesTheList(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData data = placed(helper);
		helper.runAfterDelay(2, () -> {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			ItemStack blueprint = BlueprintItem.create(TEST_HUT, HUT_SIZE);
			blueprint.set(ModComponents.BLUEPRINT, data);
			player.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
			player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WRITABLE_BOOK, 2));
			blueprint.use(level, player, InteractionHand.MAIN_HAND);
			helper.assertTrue(player.getOffhandItem().is(Items.WRITABLE_BOOK) && player.getOffhandItem().getCount() == 1,
				"one Book and Quill should be used up: " + player.getOffhandItem());
			ItemStack book = findBook(player);
			helper.assertTrue(book != null, "no written book in the inventory");
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content != null && content.pages().size() == 2, "pages: " + (content == null ? null : content.pages().size()));
			helper.assertTrue(content.pages().get(1).raw().getString().startsWith("☐ 15 × Cobblestone (of 25)"), "page 2: " + content.pages().get(1).raw().getString());
			helper.assertTrue(BlueprintItem.data(player.getMainHandItem()).equals(Optional.of(data)), "the blueprint changed: " + player.getMainHandItem());

			// The last Book and Quill becomes the list in the hand that held it.
			blueprint.use(level, player, InteractionHand.MAIN_HAND);
			helper.assertTrue(player.getOffhandItem().is(Items.WRITTEN_BOOK), "the last Book and Quill should become the list: " + player.getOffhandItem());
			helper.succeed();
		});
	}

	/** Without a Book and Quill, right-clicking does what it always did (levelling on or off), and no book appears. */
	//$ gametest_ticks_batch AREA '40' '"materialList"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "materialList")
	public void withoutABookNothingIsWritten(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData data = placed(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, HUT_SIZE);
		blueprint.set(ModComponents.BLUEPRINT, data);
		player.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BOOK));
		blueprint.use(level, player, InteractionHand.MAIN_HAND);
		helper.assertTrue(findBook(player) == null, "a list was written without a Book and Quill");
		helper.assertTrue(player.getOffhandItem().is(Items.BOOK), "the plain book changed: " + player.getOffhandItem());
		helper.assertTrue(BlueprintItem.data(blueprint).orElseThrow().levelGround() != data.levelGround(), "levelling didn't switch as before");
		helper.succeed();
	}

	/** With free materials on, nothing has to be brought: the list says so. */
	//$ gametest_ticks_batch AREA '40' '"materialListFree"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "materialListFree")
	public void withFreeMaterialsNothingIsToBring(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData data = placed(helper);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer()));
		helper.runAfterDelay(2, () -> {
			BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
			helper.assertTrue(list.toBring() == 0 && list.lines().size() == 4, "free materials: " + list.lines());
			String first = MaterialList.pages(Component.literal("Test Hut"), list).get(0).getString();
			helper.assertTrue(first.contains("Everything it needs is in the chests."), "the first page: " + first);
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
			helper.succeed();
		});
	}

	private static BlueprintData placed(GameTestHelper helper) {
		// Another batch's test may have left free materials on (they run in batches of their own).
		helper.getLevel().getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, helper.getLevel().getServer());
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
		chest.setItem(1, new ItemStack(Items.OAK_PLANKS, 55));
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(HUT),
			Rotation.NONE, Mirror.NONE);
		return BlueprintItem.data(BlueprintItem.create(TEST_HUT, HUT_SIZE)).orElseThrow().withPlacement(Optional.of(placement));
	}

	private static Map<Item, Integer> need(BlueprintSupplies.Checklist list) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		list.lines().forEach(l -> out.put(l.item(), l.need()));
		return out;
	}

	private static ItemStack findBook(ServerPlayer player) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(Items.WRITTEN_BOOK)) {
				return player.getInventory().getItem(i);
			}
		}
		return null;
	}
}

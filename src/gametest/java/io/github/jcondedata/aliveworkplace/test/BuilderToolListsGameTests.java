package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.Shapes;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StylePicker;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.shop.PriceTagItem;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * The builder tools on the list screen (ROADMAP 42.4), through the player's own clicks (a right-click with the tool, as
 * the client sends it, then the chest-click packets the list screen sends): the Shape Planner (its shapes and blocks as
 * icon cells in their rows, each size a row with - and +), the style picker (a row per style with Choose, Mirror) and the
 * Price Tag (a row per size of step with - and +, Done). Each click is the chest click it was. The Shop and the Ferry
 * (CobbleDollars) are ShopListMenusCompatTests.
 */
public class BuilderToolListsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** A right-click with what's in the main hand (sneaking or not), as a client sends it: the list screen it opens. */
	private static ChoiceMenu use(GameTestHelper helper, ServerPlayer player, boolean sneak) {
		player.setShiftKeyDown(sneak);
		player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
		player.setShiftKeyDown(false);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "a right-click didn't open the screen: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.getType() == ModBlocks.CHOICE_LIST_MENU, "opened as " + menu.getType() + ", not the list");
		var view = ModBlocks.CHOICE_LIST_MENU.create(7, player.getInventory());
		helper.assertTrue(view instanceof ChoiceView && view.slots.size() == menu.slots.size(), "the client's copy is " + view);
		return menu;
	}

	/** A click on {@code slot}, as the list screen sends it (shift: the shift-click). */
	private static void click(ServerPlayer p, ChoiceMenu menu, int slot, boolean shift) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0,
			shift ? ClickType.QUICK_MOVE : ClickType.PICKUP, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static ChoiceList.Mark mark(GameTestHelper helper, ChoiceMenu menu, int slot, ChoiceList.Kind kind) {
		ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(mark != null && mark.kind() == kind, "slot " + slot + " isn't a " + kind + ": " + mark + " " + menu.icon(slot));
		return mark;
	}

	private static Shapes.Settings settings(ServerPlayer p) {
		return p.getMainHandItem().getOrDefault(ModComponents.SHAPE, Shapes.Settings.DEFAULT);
	}

	/**
	 * The Shape Planner: a shape row with the seven shapes as icon cells (the box gold), a block row with the blocks carried
	 * as icon cells, a row per size with - and + in it, solid or hollow, the size, and Draw; the count at the top. Clicks
	 * pick the sphere and the block, + and shift-+ grow the width by 1 and 5, the hollow row switches; Draw without a Blank
	 * Blueprint is refused and with one draws the blueprint.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theShapePlannerIsAList(GameTestHelper helper) {
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer p = client.player();
		p.getInventory().setItem(9, new ItemStack(Items.STONE_BRICKS, 64));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		ChoiceMenu menu = use(helper, p, false);
		int[] kinds = ChoiceList.cells(menu.icon(Shapes.SHAPE_ROW));
		mark(helper, menu, Shapes.SHAPE_ROW, ChoiceList.Kind.ROW);
		helper.assertTrue(kinds.length == Shapes.Kind.values().length && kinds[0] == Shapes.FIRST_KIND, "the shape row's cells: " + Arrays.toString(kinds));
		helper.assertTrue(mark(helper, menu, Shapes.FIRST_KIND, ChoiceList.Kind.CELL).on() && !mark(helper, menu, Shapes.FIRST_KIND + 3, ChoiceList.Kind.CELL).on(),
			"the box isn't the gold cell");
		int sphere = Shapes.FIRST_KIND + Shapes.Kind.SPHERE.ordinal();
		click(p, menu, sphere, false);
		helper.assertTrue(settings(p).shape() == Shapes.Kind.SPHERE && mark(helper, menu, sphere, ChoiceList.Kind.CELL).on()
			&& !mark(helper, menu, Shapes.FIRST_KIND, ChoiceList.Kind.CELL).on(), "the sphere wasn't picked: " + settings(p));
		helper.assertTrue(JobListMenusGameTests.key(menu.icon(Shapes.SHAPE_ROW).getHoverName()).equals("screen.aliveworkplace.shapes.shape_row"), "the shape row's name");
		// The block row: the stone bricks carried, a cell
		int[] blocks = ChoiceList.cells(menu.icon(Shapes.BLOCK_ROW));
		helper.assertTrue(blocks.length >= 1 && blocks[0] == Shapes.FIRST_MATERIAL && menu.icon(Shapes.FIRST_MATERIAL).is(Items.STONE_BRICKS)
			&& !mark(helper, menu, Shapes.FIRST_MATERIAL, ChoiceList.Kind.CELL).on(), "the block row: " + Arrays.toString(blocks));
		helper.assertTrue(JobListMenusGameTests.key(menu.icon(Shapes.BLOCK_ROW).getHoverName()).equals("screen.aliveworkplace.shapes.block_none"), "no block yet");
		click(p, menu, Shapes.FIRST_MATERIAL, false);
		helper.assertTrue(settings(p).material().isPresent() && mark(helper, menu, Shapes.FIRST_MATERIAL, ChoiceList.Kind.CELL).on()
			&& JobListMenusGameTests.key(menu.icon(Shapes.BLOCK_ROW).getHoverName()).equals("screen.aliveworkplace.shapes.block_row"), "the block wasn't picked");
		// The width's row with - and +: a click grows it by 1, a shift-click by 5, - takes one off
		helper.assertTrue(Arrays.equals(ChoiceList.cells(menu.icon(Shapes.WIDTH)), new int[]{Shapes.WIDTH - 1, Shapes.WIDTH + 1})
			&& mark(helper, menu, Shapes.WIDTH + 1, ChoiceList.Kind.CELL).button().getString().equals("+"), "the width's row");
		int w = settings(p).width();
		click(p, menu, Shapes.WIDTH + 1, false);
		click(p, menu, Shapes.WIDTH + 1, true);
		click(p, menu, Shapes.WIDTH - 1, false);
		helper.assertTrue(settings(p).width() == w + 5 && menu.icon(Shapes.WIDTH).getCount() == w + 5, "the width is " + settings(p).width() + ", not " + (w + 5));
		helper.assertTrue(ChoiceList.cells(menu.icon(Shapes.HEIGHT)).length == 2 && ChoiceList.cells(menu.icon(Shapes.DEPTH)).length == 2, "height and depth rows");
		// Solid or hollow: the whole row is the switch
		boolean hollow = settings(p).hollow();
		mark(helper, menu, Shapes.HOLLOW, ChoiceList.Kind.ROW);
		click(p, menu, Shapes.HOLLOW, false);
		helper.assertTrue(settings(p).hollow() != hollow, "the hollow row didn't switch");
		// The count at the top, the hint at the foot; the size row
		ChoiceList.Info info = JobListMenusGameTests.info(helper, menu);
		helper.assertTrue(JobListMenusGameTests.key(info.money()).startsWith("screen.aliveworkplace.shapes.count")
			&& JobListMenusGameTests.key(info.hint()).equals("screen.aliveworkplace.shapes.list_hint"), "the planner's own line: " + info);
		mark(helper, menu, Shapes.INFO, ChoiceList.Kind.ROW);
		// Draw is a cell in the Draw row: refused without a Blank Blueprint, as before
		helper.assertTrue(Arrays.equals(ChoiceList.cells(menu.icon(Shapes.DRAW_ROW)), new int[]{Shapes.DRAW}), "the Draw row");
		click(p, menu, Shapes.DRAW, false);
		helper.assertTrue(client.seen().stream().anyMatch(c -> JobListMenusGameTests.key(c).equals("message.aliveworkplace.shapes.no_blank")),
			"no Blank Blueprint wasn't said: " + client.seen());
		p.getInventory().setItem(10, new ItemStack(ModItems.BLANK_BLUEPRINT));
		menu = use(helper, p, false);
		click(p, menu, Shapes.DRAW, false);
		helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 0 && p.getInventory().items.stream().anyMatch(s -> s.getItem() instanceof BlueprintItem),
			"no blueprint drawn: " + client.seen());
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "the screen stayed open after Draw");
		helper.succeed();
	}

	/**
	 * The style picker: the blueprint's name at the top, Mirror first, a row per style with Choose (as drawn gold, Chosen);
	 * a click on dark oak restyles the blueprint in hand and turns its row gold; Mirror flips it (gold, Flip back) and back.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theStylePickerIsAList(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STONE_HOUSE.id(), StarterBlueprints.STONE_HOUSE.size()));
		ChoiceMenu menu = use(helper, p, true);
		ChoiceList.Info info = JobListMenusGameTests.info(helper, menu);
		helper.assertTrue(info.money() != null && !info.money().getString().isEmpty()
			&& JobListMenusGameTests.key(info.hint()).equals("screen.aliveworkplace.styles.list_hint"), "the picker's own line: " + info);
		ChoiceList.Mark drawn = mark(helper, menu, StylePicker.slot(0), ChoiceList.Kind.ROW);
		helper.assertTrue(drawn.on() && JobListMenusGameTests.key(drawn.button()).equals("screen.aliveworkplace.styles.chosen"), "as drawn isn't chosen: " + drawn);
		int dark = StylePicker.slot(3);
		helper.assertTrue(menu.icon(dark).is(Items.DARK_OAK_LOG), "the third style: " + menu.icon(dark));
		ChoiceList.Mark row = mark(helper, menu, dark, ChoiceList.Kind.ROW);
		helper.assertTrue(!row.on() && JobListMenusGameTests.key(row.button()).equals("screen.aliveworkplace.styles.button_choose"), "dark oak's row: " + row);
		click(p, menu, dark, false);
		helper.assertTrue(BlueprintItem.data(p.getMainHandItem()).orElseThrow().structure().equals(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE.id(), "dark_oak")),
			"not restyled");
		helper.assertTrue(mark(helper, menu, dark, ChoiceList.Kind.ROW).on() && !mark(helper, menu, StylePicker.slot(0), ChoiceList.Kind.ROW).on(), "dark oak isn't gold");
		ChoiceList.Mark mirror = mark(helper, menu, StylePicker.MIRROR, ChoiceList.Kind.ROW);
		helper.assertTrue(!mirror.on() && JobListMenusGameTests.key(mirror.button()).equals("screen.aliveworkplace.styles.button_mirror"), "Mirror's row: " + mirror);
		click(p, menu, StylePicker.MIRROR, false);
		mirror = mark(helper, menu, StylePicker.MIRROR, ChoiceList.Kind.ROW);
		helper.assertTrue(BlueprintItem.data(p.getMainHandItem()).orElseThrow().mirrored() && mirror.on()
			&& JobListMenusGameTests.key(mirror.button()).equals("screen.aliveworkplace.styles.button_unmirror"), "not mirrored: " + mirror);
		click(p, menu, StylePicker.MIRROR, false);
		helper.assertTrue(!BlueprintItem.data(p.getMainHandItem()).orElseThrow().mirrored(), "Flip back didn't");
		helper.succeed();
	}

	/**
	 * The Price Tag: "No price yet" at the top, the price's row, a row per size of step with its - and + as cells, Done;
	 * +100 and +10 make 110 (at the top and in the row), -1000 rubs it out, Done closes the screen.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void thePriceTagIsAList(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG, 4));
		ChoiceMenu menu = use(helper, p, false);
		ChoiceList.Info info = JobListMenusGameTests.info(helper, menu);
		helper.assertTrue(JobListMenusGameTests.key(info.money()).equals("screen.aliveworkplace.price_tag.none")
			&& JobListMenusGameTests.key(info.hint()).equals("screen.aliveworkplace.price_tag.list_hint"), "the tag's own line: " + info);
		mark(helper, menu, PriceTagItem.PRICE_SLOT, ChoiceList.Kind.ROW);
		int first = PriceTagItem.FIRST_STEP_SLOT;
		for (int i = 0; i < 4; i++) {
			int[] cells = ChoiceList.cells(menu.icon(PriceTagItem.FIRST_STEP_ROW + i));
			mark(helper, menu, PriceTagItem.FIRST_STEP_ROW + i, ChoiceList.Kind.ROW);
			helper.assertTrue(Arrays.equals(cells, new int[]{first + i, first + 8 - i}), "step row " + i + ": " + Arrays.toString(cells));
		}
		ChoiceList.Mark plus100 = mark(helper, menu, first + 7, ChoiceList.Kind.CELL);
		helper.assertTrue(plus100.button().getString().equals("+100") && mark(helper, menu, first + 1, ChoiceList.Kind.CELL).button().getString().equals("-100"),
			"the hundreds' cells: " + plus100);
		click(p, menu, first + 7, false); // +100
		click(p, menu, first + 6, false); // +10
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == 110, "the tag says " + p.getMainHandItem().getHoverName().getString());
		helper.assertTrue(menu.icon(PriceTagItem.PRICE_SLOT).getHoverName().getString().contains("110"), "the price's row doesn't say 110");
		info = JobListMenusGameTests.info(helper, menu);
		helper.assertTrue(!JobListMenusGameTests.key(info.money()).equals("screen.aliveworkplace.price_tag.none"), "the top still says no price: " + info);
		click(p, menu, first, false); // -1000: nothing left
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == -1
			&& JobListMenusGameTests.key(JobListMenusGameTests.info(helper, menu).money()).equals("screen.aliveworkplace.price_tag.none"), "not rubbed out");
		click(p, menu, PriceTagItem.DONE_SLOT, false);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "Done didn't close the screen");
		helper.assertTrue(p.getMainHandItem().getCount() == 4, "the tags in hand changed: " + p.getMainHandItem());
		helper.succeed();
	}
}

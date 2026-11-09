package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
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
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * QA (lane b, 2026-11-09) for ROADMAP 42.4, the builder tools as list screens: the Shape Planner, the style picker and
 * the Price Tag. Written from the spec (and docs/wiki/list-screen.md, the guide pages): every row through the player's
 * own right-click and chest-click packets, the boundaries (sizes at 1 and {@link Shapes#MAX}, prices at nothing),
 * the empty block list, forged clicks on rows that aren't buttons or slots off the screen, the new sentences in
 * American English, and that what was chosen survives the item being saved and loaded.
 */
public class QaBuilderToolLists1109bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static ChoiceMenu use(GameTestHelper helper, ServerPlayer player, boolean sneak) {
		player.setShiftKeyDown(sneak);
		player.gameMode.useItem(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
		player.setShiftKeyDown(false);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "a right-click didn't open the screen: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.getType() == ModBlocks.CHOICE_LIST_MENU, "opened as " + menu.getType() + ", not the list");
		return menu;
	}

	private static void click(ServerPlayer p, ChoiceMenu menu, int slot, boolean shift) {
		click(p, menu, slot, 0, shift ? ClickType.QUICK_MOVE : ClickType.PICKUP);
	}

	private static void click(ServerPlayer p, ChoiceMenu menu, int slot, int button, ClickType type) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, button,
			type, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	private static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	private static Shapes.Settings settings(ServerPlayer p) {
		return p.getMainHandItem().getOrDefault(ModComponents.SHAPE, Shapes.Settings.DEFAULT);
	}

	private static ItemStack reload(GameTestHelper helper, ItemStack stack) {
		var reg = helper.getLevel().registryAccess();
		return ItemStack.parse(reg, stack.save(reg)).orElseThrow();
	}

	/**
	 * Every sentence the three tools' list screens add is in en_us.json, says something, and is American English; the
	 * steps' rows read "Steps of 1,000", "Steps of 100", "Steps of 10", "Steps of 1".
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyNewBuilderToolSentenceIsInAmericanEnglish(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> keys = List.of("screen.aliveworkplace.price_tag.steps", "screen.aliveworkplace.price_tag.list_hint", "screen.aliveworkplace.price_tag.none",
			"screen.aliveworkplace.price_tag.done", "screen.aliveworkplace.styles.button_choose", "screen.aliveworkplace.styles.button_mirror",
			"screen.aliveworkplace.styles.button_unmirror", "screen.aliveworkplace.styles.list_hint", "screen.aliveworkplace.styles.chosen",
			"screen.aliveworkplace.shapes.shape_row", "screen.aliveworkplace.shapes.block_row", "screen.aliveworkplace.shapes.block_none",
			"screen.aliveworkplace.shapes.button_draw", "screen.aliveworkplace.shapes.list_hint", "screen.aliveworkplace.shapes.count",
			"screen.aliveworkplace.shapes.count.one", "screen.aliveworkplace.shapes.no_blocks");
		List<String> british = List.of("colour", "grey", "cancelled", "armour", "defence", "favourite", "centre", "behaviour", "organis", "realis", "travell");
		for (String k : keys) {
			helper.assertTrue(lang.has(k), "missing from en_us.json: " + k);
			String v = lang.getOrDefault(k);
			helper.assertTrue(!v.isBlank() && !v.equals(k), k + " says nothing");
			for (String b : british) {
				helper.assertTrue(!v.toLowerCase(Locale.ROOT).contains(b), k + " isn't American English (" + b + "): " + v);
			}
		}
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG));
		ChoiceMenu menu = use(helper, p, false);
		String[] want = {"Steps of 1,000", "Steps of 100", "Steps of 10", "Steps of 1"};
		for (int i = 0; i < 4; i++) {
			String said = menu.icon(PriceTagItem.FIRST_STEP_ROW + i).getHoverName().getString();
			helper.assertTrue(said.equals(want[i]), "step row " + i + " says '" + said + "', not '" + want[i] + "'");
		}
		helper.succeed();
	}

	/**
	 * The Shape Planner's sizes stop at 1 and at {@link Shapes#MAX}: shift-+ past the top stays at the top, - and shift-- at
	 * 1 stay at 1; the size's row shows the number; a 1x1x1 solid box counts "1 block" (the .one sentence) at the top.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void shapeSizesStopAtOneAndAtTheMost(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.getInventory().setItem(9, new ItemStack(Items.STONE_BRICKS, 64));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		ChoiceMenu menu = use(helper, p, false);
		for (int i = 0; i < 10; i++) {
			click(p, menu, Shapes.WIDTH + 1, true);
		}
		helper.assertTrue(settings(p).width() == Shapes.MAX, "the width went to " + settings(p).width() + ", not " + Shapes.MAX);
		click(p, menu, Shapes.WIDTH + 1, false);
		helper.assertTrue(settings(p).width() == Shapes.MAX, "+ at the most made it " + settings(p).width());
		for (int slot : new int[]{Shapes.WIDTH, Shapes.HEIGHT, Shapes.DEPTH}) {
			for (int i = 0; i < 10; i++) {
				click(p, menu, slot - 1, true);
			}
			click(p, menu, slot - 1, false);
			helper.assertTrue(menu.icon(slot).getCount() == 1, "the row at " + slot + " shows " + menu.icon(slot).getCount());
		}
		Shapes.Settings s = settings(p);
		helper.assertTrue(s.width() == 1 && s.height() == 1 && s.depth() == 1, "not 1x1x1: " + s);
		if (s.hollow()) {
			click(p, menu, Shapes.HOLLOW, false);
		}
		click(p, menu, Shapes.FIRST_MATERIAL, false);
		ChoiceList.Info info = JobListMenusGameTests.info(helper, menu);
		helper.assertTrue(key(info.money()).equals("screen.aliveworkplace.shapes.count.one"), "one block isn't counted as one: " + info.money());
		helper.assertTrue(info.money().getString().startsWith("1 block of"), "the top says " + info.money().getString());
		helper.succeed();
	}

	/**
	 * With no blocks in the bag the block row says so (its second line, the barrier as its one cell); a click on the
	 * barrier picks nothing, and Draw with a Blank Blueprint but no block is refused with "no block" and keeps the blank.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void shapePlannerWithNoBlocksRefusesToDraw(GameTestHelper helper) {
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer p = client.player();
		p.getInventory().setItem(10, new ItemStack(ModItems.BLANK_BLUEPRINT));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		ChoiceMenu menu = use(helper, p, false);
		ChoiceList.Mark row = ChoiceList.mark(menu.icon(Shapes.BLOCK_ROW));
		helper.assertTrue(row != null && row.kind() == ChoiceList.Kind.ROW && key(row.second()).equals("screen.aliveworkplace.shapes.no_blocks"),
			"the empty block row: " + row);
		int[] cells = ChoiceList.cells(menu.icon(Shapes.BLOCK_ROW));
		helper.assertTrue(cells.length == 1 && menu.icon(cells[0]).is(Items.BARRIER), "the empty row's cells: " + Arrays.toString(cells));
		click(p, menu, cells[0], false);
		helper.assertTrue(settings(p).material().isEmpty(), "the barrier picked a block: " + settings(p));
		click(p, menu, Shapes.DRAW, false);
		helper.assertTrue(client.seen().stream().anyMatch(c -> key(c).equals("message.aliveworkplace.shapes.no_block")), "no block wasn't said: " + client.seen());
		helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1, "the blank was used up");
		helper.succeed();
	}

	/**
	 * Forged clicks on the Shape Planner change nothing and crash nothing: slots off the screen, the rows that only hold
	 * cells (shape, block, Draw, the screen's line, a size's row), a hotbar swap and a throw on +, a stale state id.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void forgedShapePlannerClicksAreIgnored(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.getInventory().setItem(9, new ItemStack(Items.STONE_BRICKS, 64));
		p.getInventory().setItem(10, new ItemStack(ModItems.BLANK_BLUEPRINT));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		ChoiceMenu menu = use(helper, p, false);
		Shapes.Settings before = settings(p);
		for (int slot : new int[]{-1, ChoiceMenu.SIZE + 36, 999, Short.MAX_VALUE, Shapes.SHAPE_ROW, Shapes.BLOCK_ROW, Shapes.DRAW_ROW, Shapes.LIST_INFO,
			Shapes.WIDTH, Shapes.HEIGHT, Shapes.DEPTH, Shapes.INFO}) {
			click(p, menu, slot, false);
			click(p, menu, slot, true);
		}
		click(p, menu, Shapes.WIDTH + 1, 0, ClickType.SWAP);
		click(p, menu, Shapes.WIDTH + 1, 0, ClickType.THROW);
		click(p, menu, Shapes.DRAW, 0, ClickType.CLONE);
		helper.assertTrue(settings(p).equals(before), "a forged click changed the planner: " + settings(p) + " from " + before);
		helper.assertTrue(p.containerMenu == menu, "a forged click closed the screen");
		helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 1 && p.getInventory().countItem(Items.STONE_BRICKS) == 64,
			"a forged click took items");
		helper.assertTrue(p.getMainHandItem().is(ModItems.SHAPE_PLANNER) && p.containerMenu.getCarried().isEmpty(), "the planner left the hand");
		helper.succeed();
	}

	/**
	 * Every shape's cell picks that shape (its cell gold, the others not, the shape row's name with it); an arch has no
	 * solid-or-hollow row, and a forged click where it was doesn't switch it.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void everyShapeCellPicksItsShape(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		ChoiceMenu menu = use(helper, p, false);
		Shapes.Kind[] kinds = Shapes.Kind.values();
		for (int k = kinds.length - 1; k >= 0; k--) {
			click(p, menu, Shapes.FIRST_KIND + k, false);
			helper.assertTrue(settings(p).shape() == kinds[k], "the cell for " + kinds[k] + " picked " + settings(p).shape());
			for (int j = 0; j < kinds.length; j++) {
				ChoiceList.Mark m = ChoiceList.mark(menu.icon(Shapes.FIRST_KIND + j));
				helper.assertTrue(m != null && m.kind() == ChoiceList.Kind.CELL && m.on() == (j == k), "cell " + j + " gold " + (m != null && m.on()) + " with " + kinds[k]);
			}
			Component name = menu.icon(Shapes.SHAPE_ROW).getHoverName();
			helper.assertTrue(key(name).equals("screen.aliveworkplace.shapes.shape_row")
				&& name.getContents() instanceof TranslatableContents t && t.getArgs().length == 1
				&& t.getArgs()[0] instanceof Component arg && key(arg).equals("shape.aliveworkplace." + kinds[k].id()), "the shape row's name: " + name);
		}
		click(p, menu, Shapes.FIRST_KIND + Shapes.Kind.ARCH.ordinal(), false);
		boolean hollow = settings(p).hollow();
		helper.assertTrue(ChoiceList.mark(menu.icon(Shapes.HOLLOW)) == null, "an arch still shows solid-or-hollow: " + menu.icon(Shapes.HOLLOW));
		click(p, menu, Shapes.HOLLOW, false);
		helper.assertTrue(settings(p).hollow() == hollow, "a click where the hollow row was switched it for an arch");
		helper.succeed();
	}

	/**
	 * What the planner was set to is kept on the planner (saved and loaded) and shown again when it reopens; Draw makes a
	 * blueprint of exactly that size that keeps it through a save and load.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void shapePlannerChoicesSurviveSaveAndReachTheBlueprint(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.getInventory().setItem(9, new ItemStack(Items.STONE_BRICKS, 64));
		p.getInventory().setItem(10, new ItemStack(ModItems.BLANK_BLUEPRINT));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		ChoiceMenu menu = use(helper, p, false);
		click(p, menu, Shapes.FIRST_KIND + Shapes.Kind.PYRAMID.ordinal(), false);
		click(p, menu, Shapes.FIRST_MATERIAL, false);
		click(p, menu, Shapes.WIDTH + 1, false);
		click(p, menu, Shapes.WIDTH + 1, false);
		click(p, menu, Shapes.HEIGHT - 1, false);
		Shapes.Settings chosen = settings(p);
		p.closeContainer();
		p.setItemInHand(InteractionHand.MAIN_HAND, reload(helper, p.getMainHandItem()));
		helper.assertTrue(settings(p).equals(chosen), "the planner forgot: " + settings(p) + " != " + chosen);
		menu = use(helper, p, false);
		helper.assertTrue(ChoiceList.mark(menu.icon(Shapes.FIRST_KIND + Shapes.Kind.PYRAMID.ordinal())).on()
			&& ChoiceList.mark(menu.icon(Shapes.FIRST_MATERIAL)).on(), "the reopened screen doesn't show the pyramid and the block gold");
		helper.assertTrue(menu.icon(Shapes.WIDTH).getCount() == chosen.width() && menu.icon(Shapes.HEIGHT).getCount() == chosen.height(), "the size rows");
		click(p, menu, Shapes.DRAW, false);
		ItemStack drawn = p.getInventory().items.stream().filter(s -> s.getItem() instanceof BlueprintItem).findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(!drawn.isEmpty(), "Draw drew nothing");
		BlueprintData data = BlueprintItem.data(reload(helper, drawn)).orElseThrow();
		Vec3i size = data.size().orElseThrow();
		helper.assertTrue(size.getX() == chosen.width() && size.getY() == chosen.height() && size.getZ() == chosen.depth(),
			"the blueprint is " + size + ", not " + chosen);
		helper.succeed();
	}

	/**
	 * The style picker: the first row (as drawn) and the last style row are both choosable and turn gold; choosing as
	 * drawn after a style puts the blueprint back; the "Now ..." sentence is said; a forged click on the screen's own
	 * line, the empty slots past the last style and off the screen change nothing; the style and the mirror survive a save.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void stylePickerFirstAndLastRowsAndSave(GameTestHelper helper) {
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer p = client.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STONE_HOUSE.id(), StarterBlueprints.STONE_HOUSE.size()));
		ChoiceMenu menu = use(helper, p, true);
		int styles = Math.min(20, BlueprintStyles.all().size());
		helper.assertTrue(styles >= 1, "no styles");
		int last = StylePicker.slot(styles);
		BlueprintStyles.Style lastStyle = BlueprintStyles.all().get(styles - 1);
		ChoiceList.Mark m = ChoiceList.mark(menu.icon(last));
		helper.assertTrue(m != null && m.kind() == ChoiceList.Kind.ROW && !m.on() && key(m.button()).equals("screen.aliveworkplace.styles.button_choose"),
			"the last style's row: " + m);
		click(p, menu, last, false);
		helper.assertTrue(BlueprintItem.data(p.getMainHandItem()).orElseThrow().structure().equals(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE.id(), lastStyle.name())),
			"the last row didn't restyle");
		helper.assertTrue(ChoiceList.mark(menu.icon(last)).on() && key(ChoiceList.mark(menu.icon(last)).button()).equals("screen.aliveworkplace.styles.chosen"),
			"the last row isn't Chosen");
		helper.assertTrue(client.seen().stream().anyMatch(c -> key(c).equals("message.aliveworkplace.styles.chosen")), "Now ... wasn't said: " + client.seen());
		click(p, menu, StylePicker.MIRROR, false);
		BlueprintData before = BlueprintItem.data(p.getMainHandItem()).orElseThrow();
		int info = -1;
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			if (ChoiceList.info(menu.icon(slot)) != null) {
				info = slot;
			}
		}
		for (int slot : new int[]{info, StylePicker.slot(styles + 1), ChoiceMenu.SIZE - 1, -1, 500}) {
			if (slot >= 0 && slot < ChoiceMenu.SIZE && ChoiceList.mark(menu.icon(slot)) != null && slot != info) {
				continue; // (a real row there: not a forged click)
			}
			click(p, menu, slot, false);
		}
		helper.assertTrue(BlueprintItem.data(p.getMainHandItem()).orElseThrow().equals(before), "a forged click changed the blueprint");
		ItemStack saved = reload(helper, p.getMainHandItem());
		BlueprintData after = BlueprintItem.data(saved).orElseThrow();
		helper.assertTrue(after.mirrored() && after.structure().equals(before.structure()), "the style or mirror was lost in a save: " + after);
		p.setItemInHand(InteractionHand.MAIN_HAND, saved);
		p.closeContainer();
		menu = use(helper, p, true);
		helper.assertTrue(ChoiceList.mark(menu.icon(last)).on() && !ChoiceList.mark(menu.icon(StylePicker.slot(0))).on()
			&& key(ChoiceList.mark(menu.icon(StylePicker.MIRROR)).button()).equals("screen.aliveworkplace.styles.button_unmirror"), "the reopened picker");
		click(p, menu, StylePicker.slot(0), false);
		helper.assertTrue(BlueprintItem.data(p.getMainHandItem()).orElseThrow().structure().equals(StarterBlueprints.STONE_HOUSE.id()),
			"as drawn didn't put it back: " + BlueprintItem.data(p.getMainHandItem()).orElseThrow().structure());
		helper.assertTrue(ChoiceList.mark(menu.icon(StylePicker.slot(0))).on() && !ChoiceList.mark(menu.icon(last)).on(), "as drawn isn't gold again");
		helper.succeed();
	}

	/**
	 * The style picker is only for the blueprint in hand: once it's gone (swapped for stone) the screen isn't valid and a
	 * click on a style changes nothing on the item now in hand.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void stylePickerClosesWhenTheBlueprintLeavesTheHand(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STONE_HOUSE.id(), StarterBlueprints.STONE_HOUSE.size()));
		ChoiceMenu menu = use(helper, p, true);
		ItemStack blueprint = p.getMainHandItem();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE));
		helper.assertTrue(!menu.stillValid(p), "the picker is still valid without the blueprint");
		click(p, menu, StylePicker.slot(3), false);
		helper.assertTrue(p.getMainHandItem().is(Items.STONE) && p.getMainHandItem().getComponents().get(ModComponents.BLUEPRINT) == null,
			"the stone got a blueprint");
		helper.assertTrue(BlueprintItem.data(blueprint).orElseThrow().structure().equals(StarterBlueprints.STONE_HOUSE.id()), "the blueprint away from the hand changed");
		helper.succeed();
	}

	/**
	 * The Price Tag's boundaries: - with no price stays "No price yet", the rows that hold the steps (and the price's row)
	 * aren't buttons, +1000 then -1 is 999 (shown at the top and in the row), every tag of the stack carries the price
	 * through a save and load, and the screen goes when the tag leaves the hand.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void priceTagBoundariesAndSave(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PRICE_TAG, 3));
		ChoiceMenu menu = use(helper, p, false);
		int first = PriceTagItem.FIRST_STEP_SLOT;
		for (int i = 0; i < 4; i++) {
			click(p, menu, first + i, false); // every - with nothing to take
		}
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == -1
			&& key(JobListMenusGameTests.info(helper, menu).money()).equals("screen.aliveworkplace.price_tag.none"), "- made a price from nothing");
		for (int i = 0; i < 4; i++) {
			click(p, menu, PriceTagItem.FIRST_STEP_ROW + i, false);
		}
		click(p, menu, PriceTagItem.PRICE_SLOT, false);
		click(p, menu, -1, false);
		click(p, menu, 777, false);
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == -1 && p.containerMenu == menu, "a row or a forged click set a price");
		click(p, menu, first + 8, false); // +1000
		click(p, menu, first + 3, false); // -1
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == 999, "the tag says " + PriceTagItem.dollars(p.getMainHandItem()));
		ChoiceList.Info info = JobListMenusGameTests.info(helper, menu);
		// (the top says how it's paid: dollars with CobbleDollars, else the emeralds; the price's row says both)
		String row = menu.icon(PriceTagItem.PRICE_SLOT).getHoverName().getString();
		helper.assertTrue(row.contains("999") && row.contains(info.money().getString()) && !key(info.money()).equals("screen.aliveworkplace.price_tag.none"),
			"999 isn't shown: top '" + info.money().getString() + "', row '" + row + "'");
		helper.assertTrue(p.getMainHandItem().getCount() == 3, "the stack changed size");
		ItemStack saved = reload(helper, p.getMainHandItem());
		helper.assertTrue(PriceTagItem.dollars(saved) == 999 && saved.getCount() == 3, "the price was lost in a save: " + saved);
		ItemStack one = saved.split(1);
		helper.assertTrue(PriceTagItem.dollars(one) == 999, "a tag taken off the stack has no price");
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER));
		helper.assertTrue(!menu.stillValid(p), "the tag's screen is still valid without the tag");
		helper.succeed();
	}
}

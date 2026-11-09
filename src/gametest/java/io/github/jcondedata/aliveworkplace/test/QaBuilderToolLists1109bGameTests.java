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

	// --- Added by lane-b-1109-1237 (second QA pass): what the first pass left open ------------------------------------

	/** A right-click with what's in {@code hand} (sneaking or not), as a client sends it: the list screen it opens. */
	private static ChoiceMenu use(GameTestHelper helper, ServerPlayer player, boolean sneak, InteractionHand hand) {
		player.setShiftKeyDown(sneak);
		player.gameMode.useItem(player, helper.getLevel(), player.getItemInHand(hand), hand);
		player.setShiftKeyDown(false);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu menu && menu.getType() == ModBlocks.CHOICE_LIST_MENU,
			"a right-click with the " + hand + " didn't open the list: " + player.containerMenu);
		return (ChoiceMenu) player.containerMenu;
	}

	/** Every translatable in {@code c} (its arguments too) has an English line. */
	private static void allTranslated(GameTestHelper helper, Component c, String what) {
		if (c == null) {
			return;
		}
		if (c.getContents() instanceof TranslatableContents t) {
			// (a key with a fallback, such as a blueprint's made-up name, reads its fallback: fine)
			helper.assertTrue(Language.getInstance().has(t.getKey()) || t.getFallback() != null, what + ": no en_us line for " + t.getKey());
			for (Object arg : t.getArgs()) {
				if (arg instanceof Component inner) {
					allTranslated(helper, inner, what);
				}
			}
		}
		for (Component sibling : c.getSiblings()) {
			allTranslated(helper, sibling, what);
		}
	}

	/** Every name, second line, price, button and top line on the screen resolves to English. */
	private static void everyLineReads(GameTestHelper helper, ChoiceMenu menu, String what) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (icon.isEmpty()) {
				continue;
			}
			allTranslated(helper, icon.getHoverName(), what + " slot " + slot);
			ChoiceList.Mark m = ChoiceList.mark(icon);
			if (m != null) {
				allTranslated(helper, m.second(), what + " slot " + slot);
				allTranslated(helper, m.price(), what + " slot " + slot);
				allTranslated(helper, m.button(), what + " slot " + slot);
			}
			ChoiceList.Info info = ChoiceList.info(icon);
			if (info != null) {
				allTranslated(helper, info.money(), what + " top");
				allTranslated(helper, info.hint(), what + " hint");
			}
		}
	}

	/** Sets the planner's size with its - and + cells from where it is now (as a player clicks them). */
	private static void sizeTo(ServerPlayer p, ChoiceMenu menu, int w, int h, int d) {
		int[] want = {w, h, d};
		int[] rows = {Shapes.WIDTH, Shapes.HEIGHT, Shapes.DEPTH};
		for (int i = 0; i < 3; i++) {
			for (int guard = 0; guard < 80; guard++) {
				Shapes.Settings s = settings(p);
				int now = i == 0 ? s.width() : i == 1 ? s.height() : s.depth();
				if (now == want[i]) {
					break;
				}
				click(p, menu, now < want[i] ? rows[i] + 1 : rows[i] - 1, false);
			}
		}
	}

	private static String top(GameTestHelper helper, ChoiceMenu menu) {
		return JobListMenusGameTests.info(helper, menu).money().getString();
	}

	/**
	 * Solid and hollow counts, as the top of the screen promises ("how many blocks the shape takes"): a solid 3x3x3 box is
	 * 27 blocks, hollow it's the 26 round the middle, a 2x2x2 box has no inside so hollow is still 8. The Draw sentence
	 * says the same count; in survival Draw takes one Blank Blueprint of three, and in creative it needs none. Every
	 * sentence read on the way is a real English line.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void solidAndHollowCountsMatchTheDrawnBlueprint(GameTestHelper helper) {
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer p = client.player();
		p.getInventory().setItem(9, new ItemStack(Items.STONE_BRICKS, 64));
		p.getInventory().setItem(10, new ItemStack(ModItems.BLANK_BLUEPRINT, 3));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		ChoiceMenu menu = use(helper, p, false);
		click(p, menu, Shapes.FIRST_MATERIAL, false);
		sizeTo(p, menu, 3, 3, 3);
		if (!settings(p).hollow()) {
			click(p, menu, Shapes.HOLLOW, false);
		}
		helper.assertTrue(top(helper, menu).equals("26 blocks of Stone Bricks"), "a hollow 3x3x3 box: the top says '" + top(helper, menu) + "'");
		click(p, menu, Shapes.HOLLOW, false);
		helper.assertTrue(!settings(p).hollow() && top(helper, menu).equals("27 blocks of Stone Bricks"), "a solid 3x3x3 box: the top says '" + top(helper, menu) + "'");
		helper.assertTrue(menu.icon(Shapes.INFO).getHoverName().getString().equals("3 × 3 × 3"), "the size row: " + menu.icon(Shapes.INFO).getHoverName().getString());
		everyLineReads(helper, menu, "the planner");
		click(p, menu, Shapes.DRAW, false);
		Component drawn = client.seen().stream().filter(c -> key(c).startsWith("message.aliveworkplace.shapes.drawn")).reduce((a, b) -> b).orElse(null);
		helper.assertTrue(drawn != null, "nothing said on Draw: " + client.seen());
		allTranslated(helper, drawn, "the Draw sentence");
		helper.assertTrue(drawn.getString().startsWith("Drew ") && drawn.getString().contains(": 27 blocks of Stone Bricks."), "the Draw sentence: " + drawn.getString());
		helper.assertTrue(p.getInventory().countItem(ModItems.BLANK_BLUEPRINT) == 2, "Draw took " + (3 - p.getInventory().countItem(ModItems.BLANK_BLUEPRINT)) + " blanks, not 1");
		// 2x2x2: nothing inside to hollow out
		menu = use(helper, p, false);
		sizeTo(p, menu, 2, 2, 2);
		click(p, menu, Shapes.HOLLOW, false);
		helper.assertTrue(settings(p).hollow() && top(helper, menu).equals("8 blocks of Stone Bricks"), "a hollow 2x2x2 box: '" + top(helper, menu) + "'");
		// creative: no blank needed, none taken
		p.closeContainer();
		p.getInventory().setItem(10, ItemStack.EMPTY);
		p.setGameMode(GameType.CREATIVE);
		int blueprints = (int) p.getInventory().items.stream().filter(s -> s.getItem() instanceof BlueprintItem).count();
		menu = use(helper, p, false);
		click(p, menu, Shapes.DRAW, false);
		helper.assertTrue(p.getInventory().items.stream().filter(s -> s.getItem() instanceof BlueprintItem).count() == blueprints + 1,
			"creative Draw without a blank drew nothing: " + client.seen().get(client.seen().size() - 1).getString());
		helper.succeed();
	}

	/**
	 * The block row offers "the building blocks you carry": not sand (it falls) nor a chest, a block in the off hand is
	 * offered, no block is offered twice, and with more kinds than fit the row still holds only real cells that each
	 * pick their block.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theBlockRowOffersOnlyBuildingBlocksCarried(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.getInventory().setItem(9, new ItemStack(Items.SAND, 64));
		p.getInventory().setItem(10, new ItemStack(Items.CHEST, 4));
		p.getInventory().setItem(11, new ItemStack(Items.TORCH, 16));
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		ChoiceMenu menu = use(helper, p, false);
		ChoiceList.Mark row = ChoiceList.mark(menu.icon(Shapes.BLOCK_ROW));
		helper.assertTrue(row != null && key(row.second()).equals("screen.aliveworkplace.shapes.no_blocks"), "sand, a chest and torches were offered: " + row
			+ " cells " + Arrays.toString(ChoiceList.cells(menu.icon(Shapes.BLOCK_ROW))));
		p.closeContainer();
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COBBLESTONE, 32));
		p.getInventory().setItem(12, new ItemStack(Items.COBBLESTONE, 64)); // the same block again
		menu = use(helper, p, false);
		int[] cells = ChoiceList.cells(menu.icon(Shapes.BLOCK_ROW));
		helper.assertTrue(cells.length == 1 && menu.icon(cells[0]).is(Items.COBBLESTONE), "the off hand's cobblestone, once: " + Arrays.toString(cells)
			+ " " + (cells.length > 0 ? menu.icon(cells[0]) : ""));
		p.closeContainer();
		List<net.minecraft.world.item.Item> many = List.of(Items.STONE, Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.BRICKS, Items.OAK_PLANKS,
			Items.SPRUCE_PLANKS, Items.BIRCH_PLANKS, Items.DEEPSLATE_BRICKS, Items.MOSSY_STONE_BRICKS, Items.SANDSTONE, Items.END_STONE);
		for (int i = 0; i < many.size(); i++) {
			p.getInventory().setItem(13 + i, new ItemStack(many.get(i), 8));
		}
		menu = use(helper, p, false);
		cells = ChoiceList.cells(menu.icon(Shapes.BLOCK_ROW));
		helper.assertTrue(cells.length >= 2 && cells.length <= 9, "the block row with 13 kinds carried has " + cells.length + " cells");
		java.util.Set<net.minecraft.world.item.Item> seen = new java.util.HashSet<>();
		for (int cell : cells) {
			ItemStack icon = menu.icon(cell);
			ChoiceList.Mark m = ChoiceList.mark(icon);
			helper.assertTrue(m != null && m.kind() == ChoiceList.Kind.CELL && !icon.isEmpty() && seen.add(icon.getItem())
				&& (icon.is(Items.COBBLESTONE) || many.contains(icon.getItem())), "cell " + cell + ": " + icon + " " + m);
		}
		int lastCell = cells[cells.length - 1];
		net.minecraft.world.item.Item lastItem = menu.icon(lastCell).getItem();
		click(p, menu, lastCell, false);
		helper.assertTrue(settings(p).material().map(id -> id.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(lastItem))).orElse(false)
			&& ChoiceList.mark(menu.icon(lastCell)).on(), "the last cell didn't pick " + lastItem + ": " + settings(p));
		helper.assertTrue(menu.icon(Shapes.BLOCK_ROW).getHoverName().getString().equals("Block: " + lastItem.getDescription().getString()),
			"the block row says " + menu.icon(Shapes.BLOCK_ROW).getHoverName().getString());
		helper.succeed();
	}

	/**
	 * The Shape Planner held in the off hand: a right-click with it opens the list, and the clicks change the planner in
	 * the off hand, not the stack in the main hand (whose blocks are offered).
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void theShapePlannerWorksFromTheOffHand(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE_BRICKS, 64));
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.SHAPE_PLANNER));
		ChoiceMenu menu = use(helper, p, false, InteractionHand.OFF_HAND);
		click(p, menu, Shapes.FIRST_KIND + Shapes.Kind.CONE.ordinal(), false);
		click(p, menu, Shapes.WIDTH + 1, true);
		int[] cells = ChoiceList.cells(menu.icon(Shapes.BLOCK_ROW));
		helper.assertTrue(cells.length == 1 && menu.icon(cells[0]).is(Items.STONE_BRICKS), "the main hand's bricks aren't offered: " + Arrays.toString(cells));
		click(p, menu, cells[0], false);
		Shapes.Settings s = p.getOffhandItem().getOrDefault(ModComponents.SHAPE, Shapes.Settings.DEFAULT);
		helper.assertTrue(s.shape() == Shapes.Kind.CONE && s.width() == Shapes.Settings.DEFAULT.width() + 5 && s.material().isPresent(),
			"the off hand's planner: " + s);
		helper.assertTrue(p.getMainHandItem().is(Items.STONE_BRICKS) && p.getMainHandItem().getCount() == 64
			&& p.getMainHandItem().get(ModComponents.SHAPE) == null, "the main hand changed: " + p.getMainHandItem());
		helper.assertTrue(ChoiceList.mark(menu.icon(Shapes.FIRST_KIND + Shapes.Kind.CONE.ordinal())).on()
			&& menu.icon(Shapes.WIDTH).getCount() == s.width(), "the screen doesn't follow the off hand's planner");
		helper.succeed();
	}

	/**
	 * The style picker keeps the mirror when a style is chosen (Mirror and the styles are separate choices): mirrored, then
	 * dark oak, is a mirrored dark oak, Flip back still on Mirror's row. Choosing the chosen style again changes nothing
	 * and leaves exactly one gold row; the top keeps the blueprint's name through every choice.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void choosingAStyleKeepsTheMirrorAndTheName(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(StarterBlueprints.STONE_HOUSE.id(), StarterBlueprints.STONE_HOUSE.size()));
		ChoiceMenu menu = use(helper, p, true);
		String name = top(helper, menu);
		helper.assertTrue(!name.isBlank() && !name.contains("aliveworkplace"), "the top: " + name);
		click(p, menu, StylePicker.MIRROR, false);
		int dark = -1;
		for (int i = 1; i <= Math.min(20, BlueprintStyles.all().size()); i++) {
			if (menu.icon(StylePicker.slot(i)).getHoverName().getString().equals("Dark Oak")) {
				dark = StylePicker.slot(i);
			}
		}
		helper.assertTrue(dark >= 0, "no Dark Oak row");
		click(p, menu, dark, false);
		BlueprintData data = BlueprintItem.data(p.getMainHandItem()).orElseThrow();
		helper.assertTrue(data.structure().equals(BlueprintStyles.styled(StarterBlueprints.STONE_HOUSE.id(), "dark_oak")), "not dark oak: " + data.structure());
		helper.assertTrue(data.mirrored(), "choosing a style undid the mirror");
		helper.assertTrue(key(ChoiceList.mark(menu.icon(StylePicker.MIRROR)).button()).equals("screen.aliveworkplace.styles.button_unmirror"),
			"Mirror's row forgot it's mirrored");
		click(p, menu, dark, false); // the chosen one again
		BlueprintData again = BlueprintItem.data(p.getMainHandItem()).orElseThrow();
		helper.assertTrue(again.structure().equals(data.structure()) && again.mirrored(), "choosing the chosen style changed it: " + again);
		int gold = 0;
		for (int i = 0; i <= Math.min(20, BlueprintStyles.all().size()); i++) {
			ChoiceList.Mark m = ChoiceList.mark(menu.icon(StylePicker.slot(i)));
			if (m != null && m.on()) {
				gold++;
				helper.assertTrue(StylePicker.slot(i) == dark && key(m.button()).equals("screen.aliveworkplace.styles.chosen"), "row " + i + " is gold: " + m);
			}
		}
		helper.assertTrue(gold == 1, gold + " gold style rows");
		helper.assertTrue(top(helper, menu).equals(name), "the top changed from '" + name + "' to '" + top(helper, menu) + "'");
		everyLineReads(helper, menu, "the style picker");
		helper.succeed();
	}

	/**
	 * A Wonder Plan's picker has one row saying a Wonder keeps its own blocks (no Choose, no other styles) and Mirror;
	 * a click on that row changes nothing, Mirror mirrors it.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void aWonderPlansPickerHasOneRowAndMirror(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		ItemStack plan = new ItemStack(ModItems.WONDER_PLAN);
		plan.set(ModComponents.BLUEPRINT, new BlueprintData(StarterBlueprints.STONE_HOUSE.id(), java.util.Optional.of(StarterBlueprints.STONE_HOUSE.size()),
			java.util.Optional.empty()));
		p.setItemInHand(InteractionHand.MAIN_HAND, plan);
		ChoiceMenu menu = use(helper, p, true);
		ChoiceList.Mark row = ChoiceList.mark(menu.icon(StylePicker.slot(0)));
		helper.assertTrue(row != null && row.kind() == ChoiceList.Kind.ROW && row.button() == null
			&& menu.icon(StylePicker.slot(0)).getHoverName().getString().equals("A Wonder keeps its own blocks"), "the Wonder's row: " + row + " "
			+ menu.icon(StylePicker.slot(0)).getHoverName().getString());
		for (int i = 1; i <= 20; i++) {
			helper.assertTrue(ChoiceList.mark(menu.icon(StylePicker.slot(i))) == null, "a style row " + i + " on a Wonder: " + menu.icon(StylePicker.slot(i)));
		}
		everyLineReads(helper, menu, "the Wonder's picker");
		click(p, menu, StylePicker.slot(0), false);
		click(p, menu, StylePicker.slot(1), false);
		helper.assertTrue(BlueprintItem.data(p.getMainHandItem()).orElseThrow().structure().equals(StarterBlueprints.STONE_HOUSE.id()), "the Wonder was restyled");
		click(p, menu, StylePicker.MIRROR, false);
		helper.assertTrue(BlueprintItem.data(p.getMainHandItem()).orElseThrow().mirrored()
			&& key(ChoiceList.mark(menu.icon(StylePicker.MIRROR)).button()).equals("screen.aliveworkplace.styles.button_unmirror"), "Mirror on a Wonder");
		helper.succeed();
	}

	/**
	 * The Price Tag at its ends: a tag renamed in an anvil to "250" opens at 250 and +1 makes 251; one renamed to a
	 * billion (the most a tag holds) keeps it after +1000 and +1 (no overflow, not rubbed out), and -1 takes one off;
	 * -1 at 1 leaves "No price yet". Every line on the screen is English.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void thePriceTagAtItsEnds(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		ItemStack tag = new ItemStack(ModItems.PRICE_TAG);
		tag.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("250"));
		p.setItemInHand(InteractionHand.MAIN_HAND, tag);
		ChoiceMenu menu = use(helper, p, false);
		int first = PriceTagItem.FIRST_STEP_SLOT;
		helper.assertTrue(menu.icon(PriceTagItem.PRICE_SLOT).getHoverName().getString().contains("250")
			&& !key(JobListMenusGameTests.info(helper, menu).money()).equals("screen.aliveworkplace.price_tag.none"), "the anvil's 250 isn't shown");
		everyLineReads(helper, menu, "the Price Tag");
		click(p, menu, first + 5, false); // +1
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == 251, "250 +1 is " + PriceTagItem.dollars(p.getMainHandItem()));
		p.closeContainer();
		p.getMainHandItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("1000000000"));
		menu = use(helper, p, false);
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == 1_000_000_000L, "a billion isn't a price: " + p.getMainHandItem().getHoverName().getString());
		click(p, menu, first + 8, false); // +1000
		click(p, menu, first + 5, false); // +1
		long now = PriceTagItem.dollars(p.getMainHandItem());
		helper.assertTrue(now == 1_000_000_000L, "+1000 and +1 at the most made it " + now + " ('" + p.getMainHandItem().getHoverName().getString() + "')");
		helper.assertTrue(!key(JobListMenusGameTests.info(helper, menu).money()).equals("screen.aliveworkplace.price_tag.none"), "the top lost the price at the most");
		click(p, menu, first + 3, false); // -1
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == 999_999_999L, "-1 from the most: " + PriceTagItem.dollars(p.getMainHandItem()));
		p.closeContainer();
		p.getMainHandItem().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("1"));
		menu = use(helper, p, false);
		click(p, menu, first + 3, false); // -1
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == -1
			&& key(JobListMenusGameTests.info(helper, menu).money()).equals("screen.aliveworkplace.price_tag.none"), "1 - 1 isn't no price");
		helper.succeed();
	}

	/**
	 * A Price Tag in the off hand: a right-click with it opens the list, +10 prices the off hand's tags (the whole stack),
	 * the main hand's tag keeps its own price, and closing and reopening shows the price again.
	 */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void thePriceTagWorksFromTheOffHand(GameTestHelper helper) {
		ServerPlayer p = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		ItemStack main = new ItemStack(ModItems.PRICE_TAG);
		main.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("5"));
		p.setItemInHand(InteractionHand.MAIN_HAND, main);
		p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.PRICE_TAG, 2));
		ChoiceMenu menu = use(helper, p, false, InteractionHand.OFF_HAND);
		helper.assertTrue(key(JobListMenusGameTests.info(helper, menu).money()).equals("screen.aliveworkplace.price_tag.none"),
			"the off hand's screen shows the main hand's price: " + top(helper, menu));
		click(p, menu, PriceTagItem.FIRST_STEP_SLOT + 6, false); // +10
		helper.assertTrue(PriceTagItem.dollars(p.getOffhandItem()) == 10 && p.getOffhandItem().getCount() == 2, "the off hand's tags: " + p.getOffhandItem());
		helper.assertTrue(PriceTagItem.dollars(p.getMainHandItem()) == 5, "the main hand's tag changed to " + PriceTagItem.dollars(p.getMainHandItem()));
		click(p, menu, PriceTagItem.DONE_SLOT, false);
		helper.assertTrue(p.containerMenu == p.inventoryMenu, "Done didn't close");
		menu = use(helper, p, false, InteractionHand.OFF_HAND);
		helper.assertTrue(menu.icon(PriceTagItem.PRICE_SLOT).getHoverName().getString().contains("10"), "reopened: " + menu.icon(PriceTagItem.PRICE_SLOT).getHoverName().getString());
		helper.succeed();
	}
}

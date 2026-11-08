package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

/**
 * The list screen (ROADMAP 42.1): a menu opened with {@code ChoiceMenu.openList} is the list menu type, its client copy
 * is a ChoiceView with the server's slots, its buttons are still chest clicks, the marks that say what each icon is
 * (row, tab, page arrow, the menu's own line) reach the client with the icon, and a menu can lay itself out again while
 * it's open. The Move Tutor on it is tested with Cobblemon (TutorListMenuCompatTests).
 */
public class ChoiceListGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	/** {@code icon} sent to a client and read back, as the slot sync does. */
	private static ItemStack sent(GameTestHelper helper, ItemStack icon) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
		ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, icon);
		return ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
	}

	/** A list menu opens on its own type; the client's copy is a ChoiceView of it, slot for slot; a click runs its button. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void listMenuOpensOnItsOwnTypeAndClicksStayChestClicks(GameTestHelper helper) {
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		AtomicInteger pressed = new AtomicInteger();
		ChoiceMenu menu = ChoiceMenu.openList(player, Component.literal("list"), p -> true, m -> {
			m.button(0, ChoiceList.tab(new ItemStack(Items.EMERALD), true), null);
			m.button(18, ChoiceList.row(new ItemStack(Items.BOOK), Component.literal("Fire"), Component.literal("4 emeralds"),
				Component.literal("Teach"), false, false), p -> pressed.incrementAndGet());
		});
		helper.assertTrue(menu != null && player.containerMenu == menu, "the list menu didn't open: " + player.containerMenu);
		helper.assertTrue(menu.getType() == ModBlocks.CHOICE_LIST_MENU, "the list opened as " + menu.getType());
		AbstractContainerMenu view = ModBlocks.CHOICE_LIST_MENU.create(3, player.getInventory());
		helper.assertTrue(view instanceof ChoiceView && view.getType() == ModBlocks.CHOICE_LIST_MENU, "the client's copy is " + view);
		helper.assertTrue(view.slots.size() == menu.slots.size(), view.slots.size() + " slots on the client, " + menu.slots.size() + " on the server");
		for (int i = 0; i < ChoiceMenu.SIZE; i++) {
			helper.assertTrue(view.slots.get(i).getContainerSlot() == i, "slot " + i + " shows button " + view.slots.get(i).getContainerSlot());
		}
		// The client's click, as the list screen sends it: the chest click on the row's slot.
		player.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, menu.getStateId(), 18, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
		helper.assertValueEqual(pressed.get(), 1, "presses of the row's button");
		helper.assertTrue(player.containerMenu.getCarried().isEmpty() && menu.icon(18).is(Items.BOOK), "the click moved the icon");
		view.slots.get(18).set(new ItemStack(Items.BOOK));
		view.clicked(18, 0, ClickType.PICKUP, player);
		helper.assertTrue(view.getCarried().isEmpty() && view.slots.get(18).getItem().is(Items.BOOK), "a click moved the icon on the client");
		player.closeContainer();
		helper.succeed();
	}

	/** Every mark reaches the client with its icon: kind, texts (translated there, colors kept), gray and chosen. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void listMarksTravelWithTheIcon(GameTestHelper helper) {
		ItemStack row = sent(helper, ChoiceList.row(new ItemStack(Items.BOOK),
			Component.literal("Fire").withStyle(ChatFormatting.RED), Component.translatable("message.aliveworkplace.money.emeralds", 4),
			Component.translatable("message.aliveworkplace.tutor.button_teach"), false, true));
		ChoiceList.Mark mark = ChoiceList.mark(row);
		helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.ROW, "the row's mark: " + mark);
		helper.assertTrue(mark.second().getString().equals("Fire") && mark.second().getStyle().getColor() != null
			&& mark.second().getStyle().getColor().getValue() == ChatFormatting.RED.getColor(), "the second line lost its text or color: " + mark.second());
		helper.assertTrue(mark.button().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
			&& t.getKey().equals("message.aliveworkplace.tutor.button_teach"), "the button's label isn't translated on the client: " + mark.button());
		helper.assertTrue(mark.price() != null && !mark.off() && mark.on(), "price, gray or chosen lost: " + mark);
		helper.assertTrue(row.is(Items.BOOK), "the icon changed");

		ChoiceList.Mark off = ChoiceList.mark(sent(helper, ChoiceList.row(new ItemStack(Items.PAPER), null, Component.literal("Expert tutors and up"), null, true, false)));
		helper.assertTrue(off != null && off.off() && !off.on() && off.button() == null && off.second() == null, "a gray row without a button: " + off);

		ChoiceList.Mark tab = ChoiceList.mark(sent(helper, ChoiceList.tab(new ItemStack(Items.EGG), true)));
		helper.assertTrue(tab != null && tab.kind() == ChoiceList.Kind.TAB && tab.on(), "the open tab: " + tab);
		ChoiceList.Mark next = ChoiceList.mark(sent(helper, ChoiceList.page(new ItemStack(Items.ARROW), true)));
		helper.assertTrue(next != null && next.kind() == ChoiceList.Kind.PAGE && next.on(), "the next page arrow: " + next);

		ItemStack infoIcon = sent(helper, ChoiceList.info(new ItemStack(Items.BOOK), Component.literal("40 emeralds"), Component.literal("Pick one"),
			List.of(Component.literal("Move"), Component.translatable("message.aliveworkplace.tutor.column_type"), Component.literal("Price"))));
		ChoiceList.Info info = ChoiceList.info(infoIcon);
		helper.assertTrue(info != null && info.money().getString().equals("40 emeralds") && info.hint().getString().equals("Pick one"),
			"the menu's own line: " + info);
		helper.assertTrue(info.columns().size() == 3 && info.columns().get(1).getString().equals("Type"), "the column headings: " + info.columns());
		helper.assertTrue(ChoiceList.mark(infoIcon).kind() == ChoiceList.Kind.INFO && ChoiceList.info(row) == null, "info read from a row");

		// An icon without a mark is a plain row on the list screen; other custom data is kept beside a mark.
		helper.assertTrue(ChoiceList.mark(new ItemStack(Items.STONE)) == null, "a plain icon has a mark");
		ItemStack marked = new ItemStack(Items.EMERALD);
		io.github.jcondedata.aliveworkplace.trade.TradePage.mark(marked, io.github.jcondedata.aliveworkplace.trade.TradePage.STAR);
		ChoiceList.row(marked, null, null, null, false, false);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.trade.TradePage.marks(marked).contains(io.github.jcondedata.aliveworkplace.trade.TradePage.STAR)
			&& ChoiceList.mark(marked) != null, "a list mark wiped the icon's other marks");
		helper.succeed();
	}

	/** A menu's tick check runs while it's open (so it can lay itself out again), and stops when it closes. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void listMenuChecksEveryTickWhileOpen(GameTestHelper helper) {
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		AtomicInteger checks = new AtomicInteger();
		ChoiceMenu menu = ChoiceMenu.openList(player, Component.literal("list"), p -> true, m -> m.everyTick(checks::incrementAndGet));
		helper.assertTrue(menu != null, "the list didn't open");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(checks.get() > 0, "the check never ran while the menu was open");
			player.closeContainer();
			int then = checks.get();
			helper.runAfterDelay(5, () -> {
				helper.assertValueEqual(checks.get(), then, "checks after the menu closed");
				helper.succeed();
			});
		});
	}
}

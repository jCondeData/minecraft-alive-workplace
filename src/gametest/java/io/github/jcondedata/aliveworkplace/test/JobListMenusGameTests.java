package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.gem.GemBeds;
import io.github.jcondedata.aliveworkplace.gem.GemGrowers;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.LegendText;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.work.ChoiceList;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.ChoiceView;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Money;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * The job menus on the list screen (ROADMAP 42.3), through the player's own clicks (the interact packet a right-click
 * sends, then the chest-click packets the list screen sends): the Gem Grower's orders, a traveler's hire screen at the
 * inn, a Legend guest's terms and the Research screen open on the list menu type; each row says what the chest's icon
 * said, and every click and refusal is the chest's. The Cobblemon ones (Ball Smith, Berry Breeder, Habitat Keeper) are
 * JobListMenusCompatTests.
 */
public class JobListMenusGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";

	/** A right-click (sneaking or not) with an empty hand on {@code villager}, as a client sends it: the list it opens. */
	static ChoiceMenu open(GameTestHelper helper, ServerPlayer player, Villager villager, boolean sneak) {
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.moveTo(villager.getX() + 1.5, villager.getY(), villager.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND,
			new Vec3(0, villager.getBbHeight() / 2, 0)));
		if (player.containerMenu == player.inventoryMenu) {
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(villager, sneak, InteractionHand.MAIN_HAND));
		}
		player.setShiftKeyDown(false);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "a right-click didn't open the screen: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.getType() == ModBlocks.CHOICE_LIST_MENU, "opened as " + menu.getType() + ", not the list");
		var view = ModBlocks.CHOICE_LIST_MENU.create(7, player.getInventory());
		helper.assertTrue(view instanceof ChoiceView && view.slots.size() == menu.slots.size(), "the client's copy is " + view);
		return menu;
	}

	/** A left click on {@code slot}, as the list screen sends it (the chest click on that slot). */
	static void click(ServerPlayer p, ChoiceMenu menu, int slot) {
		p.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId, p.containerMenu.getStateId(), slot, 0, ClickType.PICKUP,
			ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
	}

	static ChoiceList.Mark row(GameTestHelper helper, ChoiceMenu menu, int slot) {
		ChoiceList.Mark mark = ChoiceList.mark(menu.icon(slot));
		helper.assertTrue(mark != null && mark.kind() == ChoiceList.Kind.ROW, "slot " + slot + " isn't a list row: " + mark + " " + menu.icon(slot));
		return mark;
	}

	static ChoiceList.Info info(GameTestHelper helper, ChoiceMenu menu) {
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ChoiceList.Info info = ChoiceList.info(menu.icon(slot));
			if (info != null) {
				return info;
			}
		}
		throw new AssertionError("the menu has no line of its own (money, hint, headings)");
	}

	static String key(Component c) {
		return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
	}

	private static boolean colored(Component c, ChatFormatting color) {
		return c != null && c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == color.getColor();
	}

	private static boolean said(List<Component> seen, String key) {
		return seen.stream().anyMatch(c -> key(c).equals(key));
	}

	/**
	 * The Gem Grower's orders: "Keeps every bed" at the top, a row per bed (what it's planted with) with Pick; a click
	 * picks the first bed (gold, Unpick; the top "Keeps the picked beds"), a second unpicks it. A stranger is refused.
	 */
	//$ gametest_batch AREA '"jobListGem"'
	@GameTest(template = AREA, batch = "jobListGem")
	public void gemGrowerOrdersAsAList(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager grower = GemGrowerGameTests.grower(helper);
		grower.setNoAi(true);
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		ChoiceMenu menu = open(helper, player, grower, true);
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(key(info.money()).equals("screen.aliveworkplace.gem_grower.mode_every") && info.columns().size() == 3
			&& key(info.hint()).equals("screen.aliveworkplace.gem_grower.list_hint"), "her own line: " + info);
		int slot = GemGrowers.FIRST_BED_SLOT;
		helper.assertTrue(!GemBeds.beds().isEmpty(), "no gem beds");
		ChoiceList.Mark mark = row(helper, menu, slot);
		helper.assertTrue(!mark.on() && key(mark.button()).equals("screen.aliveworkplace.orders.button_pick")
			&& key(mark.second()).equals("screen.aliveworkplace.gem_grower.off"), "the first bed's row: " + mark);
		click(player, menu, slot);
		helper.assertTrue(GemGrowers.orders(grower).equals(List.of(GemBeds.beds().get(0).name())), "orders after the click: " + GemGrowers.orders(grower));
		mark = row(helper, menu, slot);
		helper.assertTrue(mark.on() && key(mark.button()).equals("screen.aliveworkplace.orders.button_unpick")
			&& key(mark.second()).equals("screen.aliveworkplace.gem_grower.on"), "the picked bed's row: " + mark);
		helper.assertTrue(key(info(helper, menu).money()).equals("screen.aliveworkplace.gem_grower.mode_only"), "the top after a pick");
		click(player, menu, slot);
		helper.assertTrue(GemGrowers.orders(grower).isEmpty() && !row(helper, menu, slot).on(), "a second click didn't unpick: " + GemGrowers.orders(grower));
		player.closeContainer();
		QaStageFiveGameTests.Client stranger = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		stranger.player().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		stranger.player().moveTo(grower.getX() + 1.5, grower.getY(), grower.getZ(), 90f, 0f);
		stranger.player().connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(grower, true, InteractionHand.MAIN_HAND,
			new Vec3(0, grower.getBbHeight() / 2, 0)));
		helper.assertTrue(!(stranger.player().containerMenu instanceof ChoiceMenu) && said(stranger.seen(), "message.aliveworkplace.gem_grower.not_yours"),
			"a stranger wasn't refused: " + stranger.player().containerMenu);
		helper.succeed();
	}

	/**
	 * A traveler's hire screen: the traveler's row (how long they stay), Hire with the price in red for a player with 10
	 * emeralds and the money at the top; a click is refused with the price and the balance; 10 more emeralds turn the
	 * price green while the screen is open, and a click hires them and closes it.
	 */
	//$ gametest_ticks_batch AREA '100' '"jobListInn"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "jobListInn")
	public void innHireAsAList(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager guest = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		guest.setNoAi(true);
		guest.setVillagerData(guest.getVillagerData().setProfession(VillagerProfession.NITWIT));
		ModAttachments.TRAVELLER.set(guest, new Traveller(level.getGameTime(), 3));
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		player.getInventory().setItem(9, new ItemStack(Items.EMERALD, 10)); // out of the hand the right-click empties
		ChoiceMenu menu = open(helper, player, guest, false);
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(info.money() != null && info.money().getString().equals(Money.balance(player).getString())
			&& key(info.hint()).equals("screen.aliveworkplace.inn.hire_hint"), "the money at the top and the hint: " + info);
		ChoiceList.Mark who = row(helper, menu, Innkeepers.INFO_SLOT);
		helper.assertTrue(who.button() == null && key(who.second()).startsWith("screen.aliveworkplace.inn.staying"), "the traveler's row: " + who);
		ChoiceList.Mark hire = row(helper, menu, Innkeepers.HIRE_SLOT);
		helper.assertTrue(key(hire.button()).equals("screen.aliveworkplace.inn.button_hire") && colored(hire.price(), ChatFormatting.RED),
			"Hire with 10 emeralds isn't red: " + hire);
		click(player, menu, Innkeepers.HIRE_SLOT);
		helper.assertTrue(Innkeepers.isTraveller(guest) && player.containerMenu == menu && said(client.seen(), "message.aliveworkplace.inn.cant_afford"),
			"too little money: hired, or not told why");
		// 10 more emeralds while the screen is open (as a payment would): the price turns green with no click
		player.getInventory().setItem(10, new ItemStack(Items.EMERALD, 10));
		helper.runAfterDelay(3, () -> {
			ChoiceList.Mark now = row(helper, menu, Innkeepers.HIRE_SLOT);
			helper.assertTrue(colored(now.price(), ChatFormatting.GREEN), "20 emeralds didn't turn the price green while open: " + now + "; emeralds "
				+ player.getInventory().countItem(Items.EMERALD));
			helper.assertTrue(info(helper, menu).money().getString().equals(Money.balance(player).getString()), "the money at the top didn't follow");
			click(player, menu, Innkeepers.HIRE_SLOT);
			helper.assertTrue(!Innkeepers.isTraveller(guest) && player.containerMenu == player.inventoryMenu
				&& player.getInventory().countItem(Items.EMERALD) == 4, "not hired, or the screen stayed open; emeralds " + player.getInventory().countItem(Items.EMERALD));
			helper.succeed();
		});
	}

	/**
	 * A Legend guest's terms: four rows (who they are with their rarity, what they bring, how many wants are met, how long
	 * they stay) with no buttons, every line in the rows' tooltips as before; the days at the top, the settle hint at the
	 * foot. A click on a row changes nothing.
	 */
	//$ gametest_batch AREA '"jobListLegend"'
	@GameTest(template = AREA, batch = "jobListLegend")
	public void legendTermsAsAList(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Legend legend = Legends.all().stream().filter(l -> l.luxury().isPresent()).findFirst().orElse(null);
		helper.assertTrue(legend != null, "no Legend with a luxury: " + Legends.all().size());
		Villager guest = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		guest.setNoAi(true);
		long day = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
		ModAttachments.LEGEND.set(guest, new LegendData(legend.id(), "", true, Optional.empty(), day, day + 2, Map.of(LegendText.HOME, 1), -1, -1, "visit"));
		helper.assertTrue(LegendGuests.isGuest(guest), "not a guest");
		ServerPlayer player = QaStageFiveGameTests.client(helper, GameType.SURVIVAL).player();
		ChoiceMenu menu = open(helper, player, guest, false);
		ChoiceList.Info info = info(helper, menu);
		helper.assertTrue(key(info.money()).equals("screen.aliveworkplace.legend_terms.days_left") && key(info.hint()).equals("screen.aliveworkplace.legend_terms.settle_hint"),
			"the terms' own line: " + info);
		for (int slot : new int[]{LegendGuests.WHO_SLOT, LegendGuests.BRINGS_SLOT, LegendGuests.WANTS_SLOT, LegendGuests.DAYS_SLOT}) {
			ChoiceList.Mark m = row(helper, menu, slot);
			helper.assertTrue(m.button() == null && m.second() != null && !m.second().getString().isEmpty(), "slot " + slot + "'s row: " + m);
		}
		helper.assertTrue(row(helper, menu, LegendGuests.WHO_SLOT).second().getString().equals(LegendText.rarityLine(legend).getString()), "who: the rarity");
		ChoiceList.Mark wants = row(helper, menu, LegendGuests.WANTS_SLOT);
		helper.assertTrue(wants.second().getString().equals(Component.translatable("screen.aliveworkplace.legend_terms.met", 2, 3).getString())
			&& colored(wants.second(), ChatFormatting.YELLOW), "two of three wants met (no home yet): " + wants.second());
		var lore = menu.icon(LegendGuests.WANTS_SLOT).get(net.minecraft.core.component.DataComponents.LORE).lines();
		helper.assertTrue(lore.size() == 4, "the wants' tooltip lost its ticks and crosses: " + lore);
		ItemStack before = menu.icon(LegendGuests.WANTS_SLOT).copy();
		click(player, menu, LegendGuests.WANTS_SLOT);
		helper.assertTrue(player.containerMenu == menu && ItemStack.matches(before, menu.icon(LegendGuests.WANTS_SLOT)) && LegendGuests.isGuest(guest),
			"a click on a terms row did something");
		helper.succeed();
	}

	/**
	 * The Research screen: what's under way at the top ("Nothing being researched"), a row per topic (level, next level's
	 * price, Research); Architecture (locked) gray with no button, the Great Library's rows after the scholars' topics.
	 * A click on Architecture is refused with the reason; a click on Swift Hands chooses it (gold, Under way; the top says so).
	 */
	//$ gametest_ticks_batch BIG '100' '"jobListResearch"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "jobListResearch")
	public void researchAsAList(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Research.forget();
		});
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos hallAt = new BlockPos(14, 2, 14);
		BlockPos desk = new BlockPos(8, 2, 8);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		helper.setBlock(desk, ModBlocks.SCHOLARS_DESK);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Jobs.employ(level, scholar, helper.absolutePos(desk), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		scholar.setNoAi(true);
		QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
		ServerPlayer player = client.player();
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hallAt));
		helper.runAfterDelay(2, () -> {
			ChoiceMenu menu = open(helper, player, scholar, true);
			ChoiceList.Info info = info(helper, menu);
			helper.assertTrue(key(info.money()).equals("screen.aliveworkplace.research.nothing_short") && info.columns().size() == 3
				&& key(info.hint()).equals("screen.aliveworkplace.research.list_hint"), "the research's own line: " + info);
			int swift = ResearchScreen.TOPIC_SLOTS[Research.Topic.SWIFT_HANDS.ordinal()];
			int architecture = ResearchScreen.TOPIC_SLOTS[Research.Topic.ARCHITECTURE.ordinal()];
			ChoiceList.Mark s = row(helper, menu, swift);
			helper.assertTrue(!s.off() && !s.on() && key(s.button()).equals("screen.aliveworkplace.research.button_research") && s.price() != null
				&& key(s.second()).equals("screen.aliveworkplace.research.level"), "Swift Hands' row: " + s);
			ChoiceList.Mark a = row(helper, menu, architecture);
			helper.assertTrue(a.off() && a.button() == null, "Architecture (locked) isn't a gray row without a button: " + a);
			ChoiceList.Mark library = row(helper, menu, ResearchScreen.LIBRARY_HEADER);
			helper.assertTrue(library.order() > 0 && library.off() && row(helper, menu, ResearchScreen.LIBRARY_SLOTS[0]).order() > 0,
				"the Great Library's rows aren't after the scholars' topics, or open without it: " + library);
			for (int tab : ResearchScreen.TAB_SLOTS) {
				helper.assertTrue(menu.icon(tab).isEmpty(), "a tab with no Legend tree and no realm at " + tab);
			}
			click(player, menu, architecture);
			helper.assertTrue(hall.research().currentTopic() == null && said(client.seen(), "message.aliveworkplace.research.locked"),
				"Architecture chosen before its needs, or not told why");
			click(player, menu, swift);
			helper.assertTrue(hall.research().currentTopic() == Research.Topic.SWIFT_HANDS, "Swift Hands not chosen: " + hall.research());
			s = row(helper, menu, swift);
			helper.assertTrue(s.on() && key(s.button()).equals("screen.aliveworkplace.research.button_under_way"), "Swift Hands under way: " + s);
			helper.assertTrue(key(info(helper, menu).money()).equals("screen.aliveworkplace.research.now"), "the top: " + info(helper, menu).money());
			helper.succeed();
		});
	}
}

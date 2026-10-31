package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.HallSections;
import io.github.jcondedata.aliveworkplace.hall.HallSections.Section;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1027-2153): adversarial checks of ROADMAP 30.4a, the Village Hall's own drawn screen, as it stands after
 * Milestone 40's sidebar. Written from the item's words ("the whole hall screen, not just the Book of Edicts page",
 * "keep every existing feature"): every button of every section, and the page it opens, stays on the hall's own menu
 * type; every sentence on them is translated, filled in and in American English; click kinds a chest screen allows but
 * the hall's buttons must not (swap, throw, clone, collect, drag, shift-clicks on the player's own slots, clicks
 * outside the window, unknown sidebar numbers) move nothing and change no page; the screen closes when the hall is
 * broken or the player walks off and survives both without errors; two players at once each keep their own page and
 * their own remembered section; and the Ledger's remote screen keeps working from afar until the hall is gone.
 * (The drawn background at GUI scales 2-4 is the client's; the hall scenes film it.)
 */
public class QaHallCustomScreenGameTests {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final Pattern BRITISH = Pattern.compile(
		"(?i)\\b(defence|colour|armour|grey|cancelled|jeweller|centre|honour|favour|neighbour|organis|realis|travell)");

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static void hall(GameTestHelper helper) {
		Leftovers.clear(helper);
		HallSections.forget();
		Leftovers.after(helper, HallSections::forget);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	/** Opens the hall as a player does: a right-click on the block, standing next to it. */
	private static ChoiceMenu openByHand(GameTestHelper helper, ServerPlayer player) {
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		helper.useBlock(HALL, player);
		helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the hall didn't open: " + player.containerMenu);
		ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
		helper.assertTrue(menu.getType() == ModBlocks.VILLAGE_HALL_MENU, "a right-click opened the hall as " + menu.getType());
		return menu;
	}

	/** What the player carries, slot by slot. */
	private static String carriedItems(ServerPlayer player) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack st = player.getInventory().getItem(i);
			if (!st.isEmpty()) {
				out.append(i).append('=').append(st).append(' ');
			}
		}
		return out.toString();
	}

	/** Every sentence on the screen now: each shown button's name and lines. */
	private static List<String> words(ChoiceMenu menu) {
		List<String> out = new ArrayList<>();
		for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
			ItemStack icon = menu.icon(slot);
			if (icon.isEmpty() || icon.has(DataComponents.HIDE_TOOLTIP)) {
				continue;
			}
			out.add(icon.getHoverName().getString());
			ItemLore lore = icon.get(DataComponents.LORE);
			if (lore != null) {
				lore.lines().forEach(line -> out.add(line.getString()));
			}
		}
		return out;
	}

	private static void checkWords(GameTestHelper helper, String where, List<String> words) {
		for (String w : words) {
			helper.assertFalse(w.contains("aliveworkplace."), where + ": an untranslated line: " + w);
			helper.assertFalse(w.matches(".*%(\\d+\\$)?[sd].*"), where + ": an unfilled argument: " + w);
			helper.assertFalse(BRITISH.matcher(w).find(), where + ": not American English: " + w);
		}
	}

	/**
	 * "The whole hall screen, not just the Book of Edicts page": opened by a right-click, every button of every section
	 * is clicked; the page it opens (or the section it stays on) is still the hall's own menu type, never a chest; every
	 * sentence on each section and page is translated, filled in and American; the sidebar's own labels exist in en_us.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaHallCustomEveryButton"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaHallCustomEveryButton")
	public void everyButtonStaysOnTheHallsScreen(GameTestHelper helper) {
		hall(helper);
		Villager villager = (Villager) helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		villager.setNoAi(true);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			Language lang = Language.getInstance();
			for (Section s : Section.values()) {
				helper.assertTrue(lang.has("screen.aliveworkplace.hall.section." + s.id()), "no label for the sidebar's " + s);
				helper.assertTrue(lang.has("screen.aliveworkplace.hall.section." + s.id() + ".about"), "no line about the sidebar's " + s);
				String label = lang.getOrDefault("screen.aliveworkplace.hall.section." + s.id());
				String about = lang.getOrDefault("screen.aliveworkplace.hall.section." + s.id() + ".about");
				checkWords(helper, "sidebar " + s, List.of(label, about));
			}
			ChoiceMenu menu = openByHand(helper, player);
			Set<String> pressed = new LinkedHashSet<>();
			int pages = 0;
			for (Section section : Section.values()) {
				helper.assertTrue(menu.clickMenuButton(player, section.ordinal()), "the sidebar didn't take " + section);
				checkWords(helper, section.toString(), words(menu));
				for (int slot : new ArrayList<>(HallSections.slots(menu, section))) {
					String name = menu.icon(slot).getHoverName().getString();
					menu.clicked(slot, 0, ClickType.PICKUP, player);
					pressed.add(section + ":" + slot);
					if (player.containerMenu != menu) {
						// a button that opens a screen of its own: it must be the hall's too (or simply close)
						helper.assertTrue(player.containerMenu == player.inventoryMenu
								|| player.containerMenu.getType() == ModBlocks.VILLAGE_HALL_MENU,
							section + ": '" + name + "' opened a " + player.containerMenu.getType() + " instead of the hall's screen");
						if (player.containerMenu instanceof ChoiceMenu other) {
							checkWords(helper, section + " > " + name, words(other));
						}
						player.closeContainer();
						menu = openByHand(helper, player);
					} else {
						if (HallSections.slots(menu, Section.OVERVIEW).isEmpty()) {
							pages++;
						}
						checkWords(helper, section + " > " + name, words(menu));
					}
					helper.assertTrue(menu.clickMenuButton(player, section.ordinal()), "the sidebar didn't bring back " + section + " after '" + name + "'");
				}
			}
			helper.assertTrue(pressed.size() >= 20 && pages >= 5, "too little of the hall was reached: " + pressed.size() + " buttons, " + pages + " pages");
			helper.assertTrue(player.containerMenu.getType() == ModBlocks.VILLAGE_HALL_MENU && player.containerMenu.getCarried().isEmpty(),
				"after every button: " + player.containerMenu.getType() + ", carrying " + player.containerMenu.getCarried());
			helper.succeed();
		});
	}

	/**
	 * Clicks a chest allows but the hall's buttons must ignore: swapping a button with a hotbar slot, throwing it, cloning,
	 * collecting, dragging, shift-clicking or picking up the player's own slots, clicks outside the window and sidebar
	 * numbers no section has. No item moves, nothing drops, no page opens, the section stays.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallCustomForged"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallCustomForged")
	public void forgedClickKindsMoveNothing(GameTestHelper helper) {
		hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			player.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
			player.getInventory().setItem(9, new ItemStack(Items.OAK_PLANKS, 7));
			ChoiceMenu menu = openByHand(helper, player);
			helper.assertTrue(menu.clickMenuButton(player, Section.LAWS.ordinal()), "the sidebar didn't take Laws");
			helper.assertTrue(menu.icon(VillageHallScreen.BOOK).is(Items.LECTERN), "no lectern in Laws: " + menu.icon(VillageHallScreen.BOOK));
			List<String> before = words(menu);
			int hotbar0 = ChoiceMenu.SIZE + 27;
			int inv9 = ChoiceMenu.SIZE;
			ClickType[] kinds = {ClickType.SWAP, ClickType.THROW, ClickType.CLONE, ClickType.PICKUP_ALL, ClickType.QUICK_CRAFT};
			for (ClickType kind : kinds) {
				for (int button : new int[]{0, 1, 40}) {
					menu.clicked(VillageHallScreen.BOOK, button, kind, player);
				}
			}
			for (int slot : new int[]{hotbar0, inv9}) {
				menu.clicked(slot, 0, ClickType.PICKUP, player);
				menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
				menu.clicked(slot, 0, ClickType.SWAP, player);
				menu.clicked(slot, 1, ClickType.THROW, player);
			}
			for (int slot : new int[]{-999, -1, ChoiceMenu.SIZE + 36, 1000}) {
				menu.clicked(slot, 0, ClickType.PICKUP, player);
				menu.clicked(slot, 0, ClickType.QUICK_MOVE, player);
			}
			for (int id : new int[]{-1, Integer.MIN_VALUE}) {
				helper.assertFalse(menu.clickMenuButton(player, id), "the sidebar took the number " + id);
			}
			helper.assertTrue(player.containerMenu == menu && HallSections.current(menu) == Section.LAWS,
				"a forged click changed the screen: " + HallSections.current(menu));
			helper.assertTrue(words(menu).equals(before), "a forged click changed the page: " + words(menu));
			helper.assertTrue(menu.icon(VillageHallScreen.BOOK).is(Items.LECTERN) && menu.getCarried().isEmpty(),
				"the lectern moved: " + menu.icon(VillageHallScreen.BOOK) + ", carrying " + menu.getCarried());
			helper.assertTrue(player.getInventory().getItem(0).is(Items.DIRT) && player.getInventory().getItem(0).getCount() == 5
					&& player.getInventory().getItem(9).is(Items.OAK_PLANKS) && player.getInventory().getItem(9).getCount() == 7,
				"the player's items moved: " + player.getInventory().getItem(0) + ", " + player.getInventory().getItem(9));
			int lecterns = player.getInventory().countItem(Items.LECTERN);
			helper.assertTrue(lecterns == 0, "the player got " + lecterns + " lecterns from the screen");
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(8));
			helper.assertTrue(dropped.isEmpty(), "items fell out of the screen: " + dropped);
			// a plain click still works: the lectern opens the Book in the same screen
			menu.clicked(VillageHallScreen.BOOK, 0, ClickType.PICKUP, player);
			helper.assertTrue(player.containerMenu == menu && menu.icon(EdictBook.HEADER).is(Items.LECTERN)
				&& HallSections.slots(menu, Section.OVERVIEW).isEmpty(), "a real click on the lectern didn't open the Book");
			helper.succeed();
		});
	}

	/**
	 * The screen stays open within reach and closes when the player walks off (8 blocks from the hall's center) or the
	 * hall is broken; clicks a stale screen still sends after the hall is gone throw nothing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallCustomGone"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallCustomGone")
	public void walkingOffOrBreakingTheHallClosesIt(GameTestHelper helper) {
		hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			ChoiceMenu menu = openByHand(helper, player);
			helper.assertTrue(menu.stillValid(player), "the screen isn't valid next to the hall");
			player.teleportTo(hall.getX() + 0.5 + 7.5, hall.getY() + 0.5, hall.getZ() + 0.5);
			helper.assertTrue(menu.stillValid(player), "the screen closed 7.5 blocks away");
			player.teleportTo(hall.getX() + 0.5 + 9, hall.getY() + 0.5, hall.getZ() + 0.5);
			helper.assertFalse(menu.stillValid(player), "the screen stays open 9 blocks away");
			player.teleportTo(hall.getX() + 0.5 + 30, hall.getY() + 0.5, hall.getZ() + 0.5);
			helper.assertFalse(menu.stillValid(player), "the screen stays open 30 blocks away");

			ChoiceMenu again = openByHand(helper, player);
			helper.assertTrue(again.clickMenuButton(player, Section.CALENDAR.ordinal()), "the sidebar didn't take Calendar");
			String had = carriedItems(player); // a new player's guide book, nothing else
			helper.setBlock(HALL, Blocks.AIR);
			helper.assertFalse(again.stillValid(player), "the screen stays open with the hall broken");
			try {
				again.clickMenuButton(player, Section.PEOPLE.ordinal());
				for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
					String name = again.icon(slot).getHoverName().getString();
					again.clicked(slot, 0, ClickType.PICKUP, player);
					helper.assertTrue(carriedItems(player).equals(had), "a stale click on button " + slot + " ('" + name
						+ "') of a broken hall gave the player " + carriedItems(player));
				}
			} catch (RuntimeException e) {
				helper.fail("a click on the screen of a broken hall threw " + e);
			}
			helper.assertTrue(carriedItems(player).equals(had) && again.getCarried().isEmpty(), "a stale click gave the player items: "
				+ carriedItems(player) + " (had " + had + "), carrying " + again.getCarried());
			player.closeContainer();
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "the stale screen didn't close");
			helper.succeed();
		});
	}

	/**
	 * Two players at the hall at once: each has their own screen; one's sidebar clicks and pages don't move the other's;
	 * each one's last section is their own (a new visitor still starts on the Overview).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallCustomTwoPlayers"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallCustomTwoPlayers")
	public void twoPlayersKeepTheirOwnPages(GameTestHelper helper) {
		hall(helper);
		ServerPlayer alice = player(helper);
		ServerPlayer bob = player(helper);
		helper.runAfterDelay(5, () -> {
			helper.assertFalse(alice.getUUID().equals(bob.getUUID()), "the two test players share an id");
			ChoiceMenu a = openByHand(helper, alice);
			ChoiceMenu b = openByHand(helper, bob);
			helper.assertTrue(a != b, "the two players share one screen");
			List<String> bobSaw = words(b);
			helper.assertTrue(a.clickMenuButton(alice, Section.DEFENSE.ordinal()), "Alice's sidebar didn't take Defense");
			helper.assertTrue(HallSections.current(b) == Section.OVERVIEW && words(b).equals(bobSaw),
				"Alice's click moved Bob's screen to " + HallSections.current(b));
			a.clicked(VillageHallScreen.BOOK, 0, ClickType.PICKUP, alice);
			helper.assertTrue(!HallSections.slots(b, Section.OVERVIEW).isEmpty() && words(b).equals(bobSaw),
				"Alice's lectern click opened the Book on Bob's screen");
			helper.assertTrue(b.clickMenuButton(bob, Section.TRADE.ordinal()) && HallSections.current(b) == Section.TRADE,
				"Bob's sidebar didn't take Trade");
			alice.closeContainer();
			bob.closeContainer();

			ServerPlayer carol = player(helper);
			ChoiceMenu c = openByHand(helper, carol);
			helper.assertTrue(HallSections.current(c) == Section.OVERVIEW, "a new visitor opened on " + HallSections.current(c));
			carol.closeContainer();
			ChoiceMenu a2 = openByHand(helper, alice);
			helper.assertTrue(HallSections.current(a2) == Section.DEFENSE, "Alice came back to " + HallSections.current(a2) + ", not Defense");
			ChoiceMenu b2 = openByHand(helper, bob);
			helper.assertTrue(HallSections.current(b2) == Section.TRADE, "Bob came back to " + HallSections.current(b2) + ", not Trade");
			helper.succeed();
		});
	}

	/**
	 * The Ledger's screen from afar: the hall's own screen, valid 30 blocks off, its sidebar and pages work there, and it
	 * closes once the hall is broken (and the Ledger then says the hall is out of reach instead of opening anything).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHallCustomLedger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHallCustomLedger")
	public void theLedgersScreenWorksFromAfar(GameTestHelper helper) {
		hall(helper);
		ServerPlayer player = player(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
			VillageLedgerItem.bind(level, player, ledger, hall);
			player.setItemInHand(InteractionHand.MAIN_HAND, ledger);
			player.teleportTo(hall.getX() + 30.5, hall.getY(), hall.getZ() + 0.5);
			ledger.use(level, player, InteractionHand.MAIN_HAND);
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu, "the Ledger opened nothing: " + player.containerMenu);
			ChoiceMenu menu = (ChoiceMenu) player.containerMenu;
			helper.assertTrue(menu.getType() == ModBlocks.VILLAGE_HALL_MENU && menu.stillValid(player),
				"the Ledger's screen: " + menu.getType() + ", valid " + menu.stillValid(player));
			helper.assertTrue(menu.clickMenuButton(player, Section.QUESTS.ordinal()) && HallSections.current(menu) == Section.QUESTS,
				"the sidebar didn't work from afar");
			menu.clicked(VillageHallScreen.QUESTS, 0, ClickType.PICKUP, player);
			helper.assertTrue(player.containerMenu == menu && HallSections.slots(menu, Section.OVERVIEW).isEmpty(),
				"the quests page didn't open from afar");
			checkWords(helper, "quests from afar", words(menu));
			helper.assertTrue(menu.clickMenuButton(player, Section.LAWS.ordinal()), "the sidebar didn't take Laws from afar");
			menu.clicked(VillageHallScreen.BOOK, 0, ClickType.PICKUP, player);
			helper.assertTrue(player.containerMenu == menu && menu.icon(EdictBook.HEADER).is(Items.LECTERN), "the Book didn't open from afar");
			helper.assertTrue(menu.stillValid(player), "the Ledger's screen closed after its clicks");

			helper.setBlock(HALL, Blocks.AIR);
			helper.assertFalse(menu.stillValid(player), "the Ledger's screen stays open with the hall broken");
			player.closeContainer();
			ledger.use(level, player, InteractionHand.MAIN_HAND);
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "the Ledger of a broken hall opened " + player.containerMenu);
			String tooFar = Component.translatable("message.aliveworkplace.ledger.too_far", "Oakvale").getString();
			helper.assertFalse(tooFar.contains("aliveworkplace.") || tooFar.contains("%"), "the Ledger's refusal: " + tooFar);
			checkWords(helper, "the Ledger's refusal", List.of(tooFar));
			helper.succeed();
		});
	}
}

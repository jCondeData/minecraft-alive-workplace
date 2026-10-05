package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemLore;

/**
 * The Book of Edicts (ROADMAP 30.4): the lectern button in the hall's slot 9 opens it; clicking an edict twice proclaims
 * it and clicking it in force lifts it (refused before its days are up); the slots go by rank (free, or locked with the
 * rank that opens them); a stranger's click is refused; a Village Ledger used while sneaking opens the Book; the hall's
 * name icon lists the edicts in force; the people list pages at 25 with its arrows in slots 45 and 53.
 */
public class EdictBookGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final String LONG_SHIFTS = AliveWorkplace.id("long_shifts").toString();

	/** The owner opens the Book from the hall, clicks Long Shifts twice to proclaim it, can't lift it early, then lifts it. */
	//$ gametest_ticks_batch AREA '100' '"edictBookClicks"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictBookClicks")
	public void proclaimAndLiftByClicks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer owner = player(helper);
		VillageHallBlockEntity entity = hall(helper);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			entity.setRank(VillageRanks.Rank.HAMLET);
			entity.setEdicts(List.of());
			ChoiceMenu menu = VillageHallScreen.forTest(owner, hall);
			ItemStack book = menu.icon(VillageHallScreen.BOOK);
			helper.assertTrue(book.is(Items.LECTERN) && book.getHoverName().getString().equals("Book of Edicts"), "slot 9: " + book);
			click(menu, VillageHallScreen.BOOK, owner);
			helper.assertTrue(menu.icon(EdictBook.HEADER).is(Items.LECTERN)
				&& menu.icon(EdictBook.HEADER).getHoverName().getString().startsWith("Book of Edicts: "), "the Book's header: " + menu.icon(EdictBook.HEADER));
			helper.assertTrue(lore(menu.icon(EdictBook.HEADER)).contains("0 in force of 1 edict"), "header lore: " + lore(menu.icon(EdictBook.HEADER)));
			helper.assertTrue(menu.icon(EdictBook.SLOTS[0]).is(Items.PAPER)
				&& menu.icon(EdictBook.SLOTS[0]).getHoverName().getString().equals("A free slot"), "a Hamlet's slot: " + menu.icon(EdictBook.SLOTS[0]));

			int at = find(menu, "Long Shifts");
			helper.assertTrue(at >= 0, "Long Shifts isn't listed");
			List<String> lines = lore(menu.icon(at));
			helper.assertTrue(lines.contains("Everyone works 20% faster; every grown villager is 10 less happy."), "description: " + lines);
			helper.assertTrue(lines.contains("+ everyone works 20% faster"), "boost: " + lines);
			helper.assertTrue(lines.contains("- every grown villager is 10 less happy (long shifts)"), "cost: " + lines);
			helper.assertTrue(lines.contains("Click twice to proclaim it"), "hint: " + lines);
			helper.assertTrue(color(menu.icon(at), "+ everyone works 20% faster") == 0x55FF55, "the boost isn't green");
			helper.assertTrue(color(menu.icon(at), "- every grown villager is 10 less happy (long shifts)") == 0xFF5555, "the cost isn't red");

			click(menu, at, owner);
			helper.assertTrue(entity.edicts().isEmpty(), "one click proclaimed it");
			helper.assertTrue(lore(menu.icon(at)).contains("Click again to proclaim it"), "not armed: " + lore(menu.icon(at)));
			click(menu, at, owner);
			helper.assertTrue(entity.edicts().size() == 1 && entity.edicts().get(0).id().equals(LONG_SHIFTS), "not proclaimed: " + entity.edicts());
			long today = Chronicle.day(level);
			ItemStack held = menu.icon(EdictBook.SLOTS[0]);
			helper.assertTrue(held.is(Items.CLOCK) && held.getHoverName().getString().equals("Long Shifts"), "the slot: " + held);
			helper.assertTrue(lore(held).contains("In force since day " + today + " (0 days)"), "since: " + lore(held));
			helper.assertTrue(lore(held).contains("Can be lifted in 3 days, from day " + (today + 3)), "lift in: " + lore(held));

			// Too soon to lift: still in force.
			click(menu, EdictBook.SLOTS[0], owner);
			helper.assertTrue(entity.edicts().size() == 1, "lifted before its days were up");
			// The hall's name icon lists it.
			ChoiceMenu main = VillageHallScreen.forTest(owner, hall);
			List<String> name = lore(main.icon(VillageHallScreen.NAME));
			helper.assertTrue(name.contains("Edicts in force:") && name.contains(" Long Shifts"), "name icon: " + name);

			// Five days on, a click on the slot lifts it.
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, today - 5)));
			click(main, VillageHallScreen.BOOK, owner);
			helper.assertTrue(lore(main.icon(EdictBook.SLOTS[0])).contains("Click to lift it"), "liftable: " + lore(main.icon(EdictBook.SLOTS[0])));
			helper.assertTrue(lore(main.icon(find(main, "Long Shifts"))).contains("Click to lift it"), "the list's entry: liftable");
			click(main, EdictBook.SLOTS[0], owner);
			helper.assertTrue(entity.edicts().isEmpty(), "not lifted: " + entity.edicts());
			helper.assertTrue(main.icon(EdictBook.SLOTS[0]).is(Items.PAPER), "the slot is free again");
			// Back to the hall's screen.
			click(main, EdictBook.BACK, owner);
			helper.assertTrue(main.icon(VillageHallScreen.BOOK).is(Items.LECTERN), "back didn't return to the hall");
			helper.succeed();
		});
	}

	/** A Hamlet has one slot and three locked ones; a Town three and one locked for a City. */
	//$ gametest_ticks_batch AREA '100' '"edictBookSlots"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictBookSlots")
	public void lockedSlotsSayWhichRank(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = player(helper);
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			entity.setEdicts(List.of());
			entity.setRank(VillageRanks.Rank.HAMLET);
			ChoiceMenu menu = EdictBook.forTest(player, hall);
			String[] opens = {null, "Opens when the village is a Village", "Opens when the village is a Town", "Opens when the village is a City"};
			for (int i = 1; i < 4; i++) {
				ItemStack slot = menu.icon(EdictBook.SLOTS[i]);
				helper.assertTrue(slot.is(Items.GRAY_DYE) && slot.getHoverName().getString().equals("Locked") && lore(slot).contains(opens[i]),
					"a Hamlet's slot " + i + ": " + slot + " " + lore(slot));
			}
			entity.setRank(VillageRanks.Rank.TOWN);
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(helper.getLevel()))));
			menu = EdictBook.forTest(player, hall);
			helper.assertTrue(menu.icon(EdictBook.SLOTS[0]).is(Items.CLOCK), "Long Shifts in the first slot");
			helper.assertTrue(menu.icon(EdictBook.SLOTS[1]).is(Items.PAPER) && menu.icon(EdictBook.SLOTS[2]).is(Items.PAPER), "a Town's free slots");
			helper.assertTrue(lore(menu.icon(EdictBook.SLOTS[3])).contains(opens[3]), "a Town's fourth: " + lore(menu.icon(EdictBook.SLOTS[3])));
			helper.assertTrue(lore(menu.icon(EdictBook.HEADER)).contains("1 in force of 3 edicts"), "header: " + lore(menu.icon(EdictBook.HEADER)));
			// The last row holds the civic items: the Work Horn first (30.11), the Cradle (30.12), the rest kept free (glass) for the others and guilds.
			helper.assertTrue(menu.icon(EdictBook.HORN).is(ModItems.WORK_HORN), "the last row's horn: " + menu.icon(EdictBook.HORN));
			// Then the Cradle (30.12): no cradle near a bed in this village.
			helper.assertTrue(menu.icon(EdictBook.CRADLE).is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.CRADLE.asItem())
				&& lore(menu.icon(EdictBook.CRADLE)).stream().anyMatch(l -> l.startsWith("No Cradle near a bed")), "the last row's cradle: " + lore(menu.icon(EdictBook.CRADLE)));
			// Then the village's colours (30.13): none set here, so the Village Banner and how to set them.
			helper.assertTrue(menu.icon(EdictBook.BANNER).is(ModItems.VILLAGE_BANNER)
				&& menu.icon(EdictBook.BANNER).getHoverName().getString().equals("No colours yet"), "the last row's banner: " + menu.icon(EdictBook.BANNER));
			// Then the guilds (30.17): none chartered here, so the Guild Charter and how to charter one; the rest kept free.
			helper.assertTrue(menu.icon(EdictBook.FIRST_GUILD).is(ModItems.GUILD_CHARTER)
				&& menu.icon(EdictBook.FIRST_GUILD).getHoverName().getString().equals("No guilds yet"), "the last row's guilds: " + menu.icon(EdictBook.FIRST_GUILD));
			for (int x = 4; x < 9; x++) {
				helper.assertTrue(menu.icon(EdictBook.RESERVED_ROW * 9 + x).is(Items.LIGHT_GRAY_STAINED_GLASS_PANE), "the last row, slot " + x);
			}
			helper.succeed();
		});
	}

	/** Someone not the owner's friend can read the Book but clicks change nothing. */
	//$ gametest_ticks_batch AREA '100' '"edictBookStranger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictBookStranger")
	public void strangersClickIsRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer stranger = player(helper);
		VillageHallBlockEntity entity = hall(helper);
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			entity.setOwner(UUID.randomUUID(), "Jesse");
			entity.setRank(VillageRanks.Rank.VILLAGE);
			entity.setEdicts(List.of(new Edicts.InForce(LONG_SHIFTS, Chronicle.day(helper.getLevel()) - 10)));
			ChoiceMenu menu = EdictBook.forTest(stranger, hall);
			int drill = find(menu, "Builders' Drill");
			helper.assertTrue(drill >= 0, "the test pack's edict isn't listed");
			click(menu, drill, stranger);
			helper.assertTrue(!lore(menu.icon(drill)).contains("Click again to proclaim it"), "a stranger armed an edict");
			click(menu, drill, stranger);
			helper.assertTrue(entity.edicts().size() == 1, "a stranger proclaimed: " + entity.edicts());
			click(menu, EdictBook.SLOTS[0], stranger);
			helper.assertTrue(entity.edicts().size() == 1, "a stranger lifted Long Shifts");
			helper.assertTrue(Component.translatable("message.aliveworkplace.edict.not_allowed", "Jesse", "Oakvale").getString()
				.equals("Only Jesse and their friends can proclaim edicts in Oakvale."), "the refusal's words");
			helper.succeed();
		});
	}

	/** A bound Village Ledger used while sneaking opens the Book; without sneaking, the hall's screen; the tooltip says so. */
	//$ gametest_ticks_batch AREA '100' '"edictBookLedger"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictBookLedger")
	public void ledgerSneakOpensTheBook(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = player(helper);
		hall(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ItemStack ledger = new ItemStack(ModItems.VILLAGE_LEDGER);
			VillageLedgerItem.bind(level, player, ledger, hall);
			player.setItemInHand(InteractionHand.MAIN_HAND, ledger);
			List<Component> tooltip = new ArrayList<>();
			ledger.getItem().appendHoverText(ledger, Item.TooltipContext.of(level), tooltip, TooltipFlag.NORMAL);
			helper.assertTrue(tooltip.stream().anyMatch(c -> c.getString().equals("Sneak and right-click to open the Book of Edicts")),
				"tooltip: " + tooltip);

			player.setShiftKeyDown(true);
			ledger.use(level, player, InteractionHand.MAIN_HAND);
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu m && m.icon(EdictBook.HEADER).is(Items.LECTERN)
				&& m.icon(EdictBook.SLOTS[0]).is(Items.PAPER), "sneaking didn't open the Book: " + player.containerMenu);
			player.closeContainer();
			player.setShiftKeyDown(false);
			ledger.use(level, player, InteractionHand.MAIN_HAND);
			helper.assertTrue(player.containerMenu instanceof ChoiceMenu m && m.icon(VillageHallScreen.BOOK).is(Items.LECTERN),
				"without sneaking, not the hall's screen: " + player.containerMenu);
			player.closeContainer();
			helper.succeed();
		});
	}

	/**
	 * B78: a City's 12 guilds all reach the Book's last row. Six fit the row as they are; more page five at a time, the
	 * row's last slot turning the page (and back to the first after the last), and the page holds while edicts are clicked.
	 */
	//$ gametest_ticks_batch AREA '100' '"edictBookGuildPages"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "edictBookGuildPages")
	public void everyGuildShowsOnTheGuildRow(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = player(helper);
		VillageHallBlockEntity entity = hall(helper);
		Leftovers.after(helper, () -> {
			entity.setGuilds(List.of());
			Guilds.forget();
		});
		helper.runAfterDelay(5, () -> {
			BlockPos hall = helper.absolutePos(HALL);
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			entity.setRank(VillageRanks.Rank.CITY);
			entity.setEdicts(List.of());
			List<ResourceLocation> ids = Guilds.all().keySet().stream().sorted().toList();
			helper.assertTrue(ids.size() >= 7, "the test needs more than six guilds: " + ids);
			long day = Chronicle.day(helper.getLevel());

			// Six guilds fill the row with no page button.
			List<Guilds.Charter> six = new ArrayList<>();
			for (int i = 0; i < 6; i++) {
				six.add(new Guilds.Charter(ids.get(i), UUID.randomUUID(), "Master " + i, day, Optional.empty()));
			}
			entity.setGuilds(six);
			Guilds.forget();
			ChoiceMenu menu = EdictBook.forTest(player, hall);
			for (int i = 0; i < 6; i++) {
				String name = Guilds.get(ids.get(i)).name().getString();
				helper.assertTrue(menu.icon(EdictBook.FIRST_GUILD + i).getHoverName().getString().equals(name),
					"six guilds, slot " + i + ": " + menu.icon(EdictBook.FIRST_GUILD + i) + " not " + name);
			}

			// Twelve (a City's most): five per page, the last slot turns the page.
			List<Guilds.Charter> twelve = new ArrayList<>();
			List<String> names = new ArrayList<>();
			for (int i = 0; i < 12; i++) {
				ResourceLocation id = ids.get(i % ids.size());
				twelve.add(new Guilds.Charter(id, UUID.randomUUID(), "Master " + i, day, Optional.empty()));
				names.add(Guilds.get(id).name().getString() + "|Guild Master: Master " + i);
			}
			entity.setGuilds(twelve);
			Guilds.forget();
			menu = EdictBook.forTest(player, hall);
			String[] shown = {"Showing guilds 1 to 5 of 12", "Showing guilds 6 to 10 of 12", "Showing guilds 11 to 12 of 12"};
			List<String> seen = new ArrayList<>();
			for (int page = 0; page < 3; page++) {
				ItemStack more = menu.icon(EdictBook.MORE_GUILDS);
				helper.assertTrue(more.is(Items.ARROW) && more.getHoverName().getString().equals("More guilds"), "page " + page + "'s button: " + more);
				helper.assertTrue(lore(more).equals(List.of(shown[page], page < 2 ? "Click to see the next ones" : "Click to go back to the first ones")),
					"page " + page + "'s button: " + lore(more));
				for (int x = 0; x < EdictBook.GUILDS_PER_PAGE; x++) {
					ItemStack icon = menu.icon(EdictBook.FIRST_GUILD + x);
					if (icon.is(Items.LIGHT_GRAY_STAINED_GLASS_PANE)) {
						helper.assertTrue(page == 2 && x >= 2, "an empty guild slot on page " + page + ", slot " + x);
						continue;
					}
					seen.add(icon.getHoverName().getString() + "|" + lore(icon).get(0));
				}
				if (page == 1) {
					// Clicking an edict keeps the page.
					int drill = find(menu, "Builders' Drill");
					click(menu, drill, player);
					helper.assertTrue(lore(menu.icon(EdictBook.MORE_GUILDS)).contains(shown[1]), "an edict click turned the guild page back");
				}
				click(menu, EdictBook.MORE_GUILDS, player);
			}
			helper.assertTrue(seen.equals(names), "every guild once, in order: " + seen + " not " + names);
			helper.assertTrue(lore(menu.icon(EdictBook.MORE_GUILDS)).contains(shown[0]), "after the last page, back to the first: " + lore(menu.icon(EdictBook.MORE_GUILDS)));
			helper.assertTrue(lore(EdictBook.forTest(player, hall, 2).icon(EdictBook.MORE_GUILDS)).contains(shown[2]), "opened at the third page");
			// Turning pages proclaims and charters nothing.
			helper.assertTrue(entity.edicts().isEmpty() && entity.guilds().size() == 12, "the page button changed the village: " + entity.edicts());
			helper.succeed();
		});
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 1.5, hall.getY(), hall.getZ() + 0.5);
		return player;
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			CivicEffects.forget();
			Moods.forget();
		});
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	private static void click(ChoiceMenu menu, int slot, ServerPlayer player) {
		menu.clicked(slot, 0, ClickType.PICKUP, player);
	}

	private static int find(ChoiceMenu menu, String name) {
		for (int s = EdictBook.FIRST_EDICT; s < EdictBook.END_EDICTS; s++) {
			if (menu.icon(s).getHoverName().getString().equals(name)) {
				return s;
			}
		}
		return -1;
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static int color(ItemStack stack, String line) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return -1;
		}
		for (Component c : lore.lines()) {
			if (c.getString().equals(line) && c.getStyle().getColor() != null) {
				return c.getStyle().getColor().getValue();
			}
		}
		return -1;
	}
}

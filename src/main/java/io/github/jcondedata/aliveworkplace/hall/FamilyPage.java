package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.printer.FamilyTree;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * A villager's Family page (ROADMAP 34.21), opened from the Family button on their page in the hall: their
 * grandparents, parents, partner, brothers and sisters, children and grandchildren from the village's Book of Families,
 * the dead marked with a cross. A click on anyone opens their own family; the Printer button prints the family's tree.
 *
 * <p>Rows: the top row is Back, the person, and Print; then grandparents and parents, partner and brothers and sisters,
 * children, grandchildren. Each group starts with a label; anyone past the row's room is counted on it.
 */
public final class FamilyPage {
	/** The Family button on a villager's page (the life story's top row). */
	public static final int BUTTON = 8;
	/** Where the page's parts sit. */
	public static final int WHO = 4;
	public static final int PRINT = 8;
	public static final int GRANDPARENTS = 9;
	public static final int PARENTS = 14;
	public static final int PARTNER = 18;
	public static final int SIBLINGS = 21;
	public static final int CHILDREN = 27;
	public static final int GRANDCHILDREN = 36;

	/** The button that opens {@code villager}'s Family page. */
	public static ItemStack button(Villager villager) {
		return VillageHallScreen.icon(Items.FLOWERING_AZALEA, Component.translatable("screen.aliveworkplace.family.button"), ChatFormatting.GREEN,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.family.button_hint", villager.getDisplayName()), ChatFormatting.GRAY));
	}

	/** Lays out the Family page of {@code who} (someone in the book); Back runs {@code back}. */
	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, UUID who, Runnable back) {
		menu.clearButtons();
		menu.button(0, VillageHallScreen.icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> back.run());
		if (level.getEntity(who) instanceof Villager living) {
			FamilyBook.write(level, hall, living); // the round may not have reached them yet
		}
		FamilyBook.Book book = FamilyBook.book(level, hall);
		FamilyBook.Person me = book == null ? null : book.get(who);
		if (me == null) {
			menu.button(WHO, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.family.not_in_book"), ChatFormatting.GRAY), null);
			return;
		}
		List<Component> about = new ArrayList<>(lore(level, me));
		about.add(VillageHallScreen.line("screen.aliveworkplace.family.dead_marked", ChatFormatting.DARK_GRAY));
		about.add(VillageHallScreen.line("screen.aliveworkplace.family.click_anyone", ChatFormatting.DARK_GRAY));
		menu.button(WHO, VillageHallScreen.icon(Items.OAK_SAPLING, Component.translatable("screen.aliveworkplace.family.title", FamilyBook.hallName(me)),
			ChatFormatting.GOLD, about.toArray(Component[]::new)), null);
		menu.button(PRINT, printButton(level, hall, book, me), p -> print(menu, level, hall, book, me, p, back));
		List<FamilyBook.Person> parents = FamilyBook.parents(book, me);
		List<ItemStack> parentIcons = new ArrayList<>();
		List<FamilyBook.Person> parentPeople = new ArrayList<>(parents);
		for (FamilyBook.Person parent : parents) {
			parentIcons.add(person(level, parent));
		}
		if (parents.isEmpty()) {
			// A record from before the book (34.21): the parents' names only.
			Families.Parents named = level.getEntity(who) instanceof Villager living ? Families.parents(living) : null;
			if (named != null) {
				for (Component name : List.of(named.mother(), named.father())) {
					parentIcons.add(VillageHallScreen.icon(Items.PAPER, name.copy(), ChatFormatting.WHITE,
						VillageHallScreen.line("screen.aliveworkplace.family.name_only", ChatFormatting.DARK_GRAY)));
					parentPeople.add(null);
				}
			}
		}
		group(menu, level, hall, back, GRANDPARENTS, 4, Items.OAK_LOG, "screen.aliveworkplace.family.grandparents", FamilyBook.grandparents(book, me));
		label(menu, PARENTS, Items.STRIPPED_OAK_LOG, "screen.aliveworkplace.family.parents", parentIcons.size(), 2);
		for (int i = 0; i < Math.min(2, parentIcons.size()); i++) {
			FamilyBook.Person parent = parentPeople.get(i);
			menu.button(PARENTS + 1 + i, parentIcons.get(i), parent == null ? null : p -> open(menu, level, hall, parent.id(), back));
		}
		FamilyBook.Person partner = book.get(me.partner());
		group(menu, level, hall, back, PARTNER, 1, Items.POPPY, "screen.aliveworkplace.family.partner", partner == null ? List.of() : List.of(partner));
		group(menu, level, hall, back, SIBLINGS, 5, Items.OAK_LEAVES, "screen.aliveworkplace.family.siblings", FamilyBook.siblings(book, me));
		group(menu, level, hall, back, CHILDREN, 8, Items.OAK_SAPLING, "screen.aliveworkplace.family.children", FamilyBook.children(book, me.id()));
		group(menu, level, hall, back, GRANDCHILDREN, 8, Items.WHEAT_SEEDS, "screen.aliveworkplace.family.grandchildren", FamilyBook.grandchildren(book, me));
	}

	private static void open(ChoiceMenu menu, ServerLevel level, BlockPos hall, UUID who, Runnable back) {
		render(menu, level, hall, who, back);
		menu.broadcastChanges();
	}

	/** A label at {@code slot} and up to {@code room} people after it. */
	private static void group(ChoiceMenu menu, ServerLevel level, BlockPos hall, Runnable back, int slot, int room, Item item, String key,
							  List<FamilyBook.Person> people) {
		label(menu, slot, item, key, people.size(), room);
		for (int i = 0; i < Math.min(room, people.size()); i++) {
			FamilyBook.Person person = people.get(i);
			menu.button(slot + 1 + i, person(level, person), p -> open(menu, level, hall, person.id(), back));
		}
	}

	private static void label(ChoiceMenu menu, int slot, Item item, String key, int count, int room) {
		List<Component> lore = new ArrayList<>();
		if (count == 0) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.family.none_known", ChatFormatting.DARK_GRAY));
		} else if (count > room) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.family.more", count - room), ChatFormatting.GRAY));
		}
		menu.button(slot, VillageHallScreen.icon(item, Component.translatable(key), ChatFormatting.YELLOW, lore.toArray(Component[]::new)), null);
	}

	/** Someone in the book: a name tag for the living, an egg for a baby, a skull and a cross for the dead. */
	public static ItemStack person(ServerLevel level, FamilyBook.Person person) {
		boolean baby = !person.dead() && level.getEntity(person.id()) instanceof Villager v && v.isBaby();
		Item item = person.dead() ? Items.SKELETON_SKULL : baby ? Items.EGG : Items.NAME_TAG;
		Component name = person.dead() ? Component.translatable("screen.aliveworkplace.family.dead_name", FamilyBook.hallName(person)) : FamilyBook.hallName(person);
		List<Component> lore = new ArrayList<>(lore(level, person));
		lore.add(VillageHallScreen.line("screen.aliveworkplace.family.click_family", ChatFormatting.DARK_GRAY));
		return VillageHallScreen.icon(item, name, person.dead() ? ChatFormatting.GRAY : ChatFormatting.WHITE, lore.toArray(Component[]::new));
	}

	/** Their trade, the day they were born or grew up, the day they died. */
	static List<Component> lore(ServerLevel level, FamilyBook.Person person) {
		List<Component> lore = new ArrayList<>();
		Component trade = trade(person.trade());
		if (trade != null) {
			lore.add(VillageHallScreen.line(trade, ChatFormatting.GRAY));
		}
		lore.add(VillageHallScreen.line(Component.translatable(person.bornHere() ? "screen.aliveworkplace.family.born" : "screen.aliveworkplace.family.grown",
			person.born()), ChatFormatting.GRAY));
		if (person.dead()) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.family.died", person.died()), ChatFormatting.DARK_GRAY));
		}
		return lore;
	}

	/** "Farmer" for a trade's id, or null for none (or one no longer in the game). */
	@Nullable
	public static Component trade(String id) {
		ResourceLocation key = id.isEmpty() ? null : ResourceLocation.tryParse(id);
		if (key == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(key)) {
			return null;
		}
		VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(key);
		return io.github.jcondedata.aliveworkplace.work.Stations.name(profession);
	}

	/** The village's Printer (34.11), or null: who prints a family's tree. */
	@Nullable
	public static Villager printer(ServerLevel level, BlockPos hall) {
		if (!io.github.jcondedata.aliveworkplace.printer.Printers.ENABLED) {
			return null;
		}
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && io.github.jcondedata.aliveworkplace.printer.Printers.isPrinter(v))
			.stream().findFirst().orElse(null);
	}

	private static ItemStack printButton(ServerLevel level, BlockPos hall, FamilyBook.Book book, FamilyBook.Person me) {
		Villager printer = printer(level, hall);
		Component title = FamilyBook.familyTitle(book, me);
		Component price = Money.describe((long) FamilyBook.TREE_EMERALDS * Money.DOLLARS_PER_EMERALD, FamilyBook.TREE_EMERALDS);
		return printer == null
			? VillageHallScreen.icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.family.print", title), ChatFormatting.GRAY,
				VillageHallScreen.line("screen.aliveworkplace.family.print_no_printer", ChatFormatting.RED))
			: VillageHallScreen.icon(Items.WRITTEN_BOOK, Component.translatable("screen.aliveworkplace.family.print", title), ChatFormatting.GREEN,
				VillageHallScreen.line(Component.translatable("screen.aliveworkplace.family.print_by", printer.getDisplayName(), price), ChatFormatting.GRAY));
	}

	/** The Printer prints {@code me}'s family's tree for the price, into the player's hands. */
	private static void print(ChoiceMenu menu, ServerLevel level, BlockPos hall, FamilyBook.Book book, FamilyBook.Person me, ServerPlayer player, Runnable back) {
		Villager printer = printer(level, hall);
		if (printer == null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.family.no_printer").withStyle(ChatFormatting.YELLOW));
			level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
			return;
		}
		long dollars = (long) FamilyBook.TREE_EMERALDS * Money.DOLLARS_PER_EMERALD;
		if (!Money.charge(player, dollars, FamilyBook.TREE_EMERALDS)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.family.cant_pay", Money.describe(dollars, FamilyBook.TREE_EMERALDS))
				.withStyle(ChatFormatting.YELLOW));
			level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
			return;
		}
		ItemStack tree = FamilyTree.print(level, hall, book, me, printer);
		if (!player.getInventory().add(tree)) {
			player.drop(tree, false);
		}
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.family.printed", printer.getDisplayName(), FamilyBook.familyTitle(book, me))
			.withStyle(ChatFormatting.GREEN));
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, SoundSource.NEUTRAL, 0.8f, 1f);
		render(menu, level, hall, me.id(), back);
		menu.broadcastChanges();
	}

	private FamilyPage() {
	}
}

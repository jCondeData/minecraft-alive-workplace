package io.github.jcondedata.aliveworkplace.printer;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.FamilyPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * A family's tree, printed by the village's Printer (ROADMAP 34.21) for {@link FamilyBook#TREE_EMERALDS} emeralds: a
 * plain written book (design note M34: not one of the Printer's own papers), written from the hall's Book of Families.
 * The cover names the family, the village and the day, and counts the people and generations; then everyone of the
 * family, generation by generation, {@link #PER_PAGE} to a page: their name (a cross for the dead), generation and
 * trade, parents, partner and their days.
 */
public final class FamilyTree {
	/** People to a page. */
	public static final int PER_PAGE = 2;
	/** Pages a written book holds at most (vanilla's limit). */
	public static final int MAX_PAGES = 100;

	/** The tree of {@code person}'s family, as the hall's book has it today, printed by {@code printer}. */
	public static ItemStack print(ServerLevel level, BlockPos hall, FamilyBook.Book book, FamilyBook.Person person, Villager printer) {
		List<Component> pages = pages(level, hall, book, person);
		String title = FamilyBook.bookTitle(book, person).getString();
		if (title.length() > WrittenBookContent.TITLE_MAX_LENGTH) {
			title = title.substring(0, WrittenBookContent.TITLE_MAX_LENGTH);
		}
		List<Filterable<Component>> filtered = new ArrayList<>();
		for (Component page : pages) {
			filtered.add(Filterable.passThrough(page));
		}
		ItemStack book1 = new ItemStack(Items.WRITTEN_BOOK);
		book1.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), printer.getName().getString(), 0, filtered, true));
		return book1;
	}

	/** The book's pages: the cover, then the family two to a page. */
	public static List<Component> pages(ServerLevel level, BlockPos hall, FamilyBook.Book book, FamilyBook.Person person) {
		List<FamilyBook.Person> members = person.family() > 0 ? FamilyBook.members(book, person.family()) : List.of(person);
		int generations = 0;
		for (FamilyBook.Person m : members) {
			generations = Math.max(generations, FamilyBook.generation(book, m));
		}
		List<Component> pages = new ArrayList<>();
		pages.add(Component.translatable("book.aliveworkplace.family_tree.cover", FamilyBook.bookTitle(book, person).copy().withStyle(ChatFormatting.BOLD),
			VillageHalls.name(level, hall), Chronicle.day(level),
			Words.counted("book.aliveworkplace.family_tree.people", members.size(), members.size()),
			Words.counted("book.aliveworkplace.family_tree.generations", generations, generations)));
		MutableComponent page = null;
		int onPage = 0;
		for (int i = 0; i < members.size(); i++) {
			if (pages.size() >= MAX_PAGES) {
				break;
			}
			if (page == null) {
				page = Component.empty();
			}
			if (onPage > 0) {
				page.append(Component.literal("\n\n"));
			}
			page.append(entry(book, members.get(i)));
			onPage++;
			if (onPage == PER_PAGE) {
				pages.add(page);
				page = null;
				onPage = 0;
			}
		}
		if (page != null && pages.size() < MAX_PAGES) {
			pages.add(page);
		}
		return pages;
	}

	/** One person: "Bram Ashby †", "Generation 1 · Farmer", "Child of Ann and Odo", "Partner: Ilse Corwen", "Day 3 to day 40". */
	static Component entry(FamilyBook.Book book, FamilyBook.Person p) {
		MutableComponent out = Component.empty();
		Component name = FamilyBook.fullName(p);
		out.append((p.dead() ? Component.translatable("screen.aliveworkplace.family.dead_name", name) : name.copy()).withStyle(ChatFormatting.BOLD));
		Component trade = FamilyPage.trade(p.trade());
		int generation = FamilyBook.generation(book, p);
		out.append(Component.literal("\n")).append(trade != null
			? Component.translatable("book.aliveworkplace.family_tree.generation_trade", generation, trade)
			: Component.translatable("book.aliveworkplace.family_tree.generation", generation));
		List<FamilyBook.Person> parents = FamilyBook.parents(book, p);
		if (!parents.isEmpty()) {
			List<Component> names = new ArrayList<>();
			for (FamilyBook.Person parent : parents) {
				names.add(parent.name());
			}
			out.append(Component.literal("\n")).append(Component.translatable("book.aliveworkplace.family_tree.child_of", FamilyBook.list(names)));
		}
		FamilyBook.Person partner = book.get(p.partner());
		if (partner != null) {
			out.append(Component.literal("\n")).append(Component.translatable("book.aliveworkplace.family_tree.partner", FamilyBook.fullName(partner)));
		}
		out.append(Component.literal("\n")).append(p.dead()
			? Component.translatable("book.aliveworkplace.family_tree.days", p.born(), p.died())
			: Component.translatable("book.aliveworkplace.family_tree.since", p.born()));
		return out;
	}

	private FamilyTree() {
	}
}

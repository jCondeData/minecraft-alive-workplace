package io.github.jcondedata.aliveworkplace.build;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * A material list you can take away (roadmap 23.4): a blueprint written out as a book, a checklist of everything it
 * needs with what the chests by its Blueprint Table hold already taken off (like Create's Schematicannon checklist).
 * Written by holding a Book and Quill in the other hand and right-clicking with the blueprint.
 */
public final class MaterialList {
	/** Lines on one page: a page holds 14, and a long name wraps to two. */
	static final int PER_PAGE = 7;
	/** Vanilla's limit on a written book's title. */
	private static final int TITLE_MAX = 32;
	/** Vanilla's limit on a written book's pages. */
	private static final int PAGES_MAX = 100;

	/** The book for {@code data}, or empty for an unknown (or too big) blueprint. */
	public static Optional<ItemStack> write(ServerLevel level, BlueprintData data, String author) {
		return BlueprintSupplies.checklist(level, data).map(list -> book(Blueprints.displayName(data.structure()), list, author));
	}

	/** The pages for {@code list}: a heading page, then the lines, still-to-bring first. */
	public static List<Component> pages(Component name, BlueprintSupplies.Checklist list) {
		return pages(name, list, null);
	}

	/**
	 * B279: with an {@code intro}, the list of a build under way (what it says goes under the heading), always counted
	 * against what the builder can get at, so it never asks for the blueprint to be placed.
	 */
	public static List<Component> pages(Component name, BlueprintSupplies.Checklist list, @org.jetbrains.annotations.Nullable Component intro) {
		List<Component> pages = new ArrayList<>();
		MutableComponent first = Component.empty()
			.append(Component.translatable("book.aliveworkplace.material_list.heading", name).withStyle(ChatFormatting.BOLD))
			.append("\n\n");
		if (intro != null) {
			first.append(intro);
			first.append("\n\n");
		}
		if (list.bench().isPresent() || intro != null) {
			if (list.bench().isPresent()) {
				BlockPos b = list.bench().get();
				first.append(Component.translatable("book.aliveworkplace.material_list.from_chests", b.getX(), b.getY(), b.getZ()));
				first.append("\n\n");
			}
			first.append(list.toBring() == 0
				? Component.translatable("book.aliveworkplace.material_list.all_there").withStyle(ChatFormatting.DARK_GREEN)
				: itemsOfKinds("book.aliveworkplace.material_list.to_bring", list.toBring(), list.lines().stream().filter(l -> l.toBring() > 0).count()));
		} else {
			first.append(itemsOfKinds("book.aliveworkplace.material_list.everything", list.lines().stream().mapToInt(BlueprintSupplies.Line::need).sum(),
				list.lines().size()));
			first.append("\n\n");
			first.append(Component.translatable("book.aliveworkplace.material_list.place_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
		pages.add(first);
		MutableComponent page = null;
		int onPage = 0;
		for (BlueprintSupplies.Line line : list.lines()) {
			if (pages.size() >= PAGES_MAX) {
				break;
			}
			if (page == null || onPage == PER_PAGE) {
				page = Component.empty();
				pages.add(page);
				onPage = 0;
			} else {
				page.append("\n");
			}
			page.append(line(line));
			onPage++;
		}
		return pages;
	}

	/** "☐ 25 × Cobblestone", or "☑ Oak Door (in the chests)" when the chests hold all of it. */
	static Component line(BlueprintSupplies.Line line) {
		// 37.13: a furniture piece several mods make is "Spruce Chair (any furniture mod)", each mod's piece on hover
		Component name = io.github.jcondedata.aliveworkplace.furnish.FurnitureEquivalents.name(line.item());
		if (line.toBring() == 0) {
			return Component.translatable("book.aliveworkplace.material_list.done", name).withStyle(ChatFormatting.DARK_GREEN);
		}
		if (line.toBring() < line.need()) {
			return Component.translatable("book.aliveworkplace.material_list.partly", line.toBring(), name, line.need());
		}
		return Component.translatable("book.aliveworkplace.material_list.todo", line.toBring(), name);
	}

	private static ItemStack book(Component name, BlueprintSupplies.Checklist list, String author) {
		return book(name, list, null, author);
	}

	static ItemStack book(Component name, BlueprintSupplies.Checklist list, @org.jetbrains.annotations.Nullable Component intro, String author) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		String title = Component.translatable("book.aliveworkplace.material_list.title", name).getString();
		List<Filterable<Component>> pages = pages(name, list, intro).stream().map(Filterable::passThrough).toList();
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
			Filterable.passThrough(title.length() > TITLE_MAX ? title.substring(0, TITLE_MAX) : title), author, 0, pages, true));
		return book;
	}

	private MaterialList() {
	}

	/** "3 items of 2 kinds", "1 item of 1 kind" ({@code .one}) or "3 items of 1 kind" ({@code .one_kind}). */
	private static Component itemsOfKinds(String key, long items, long kinds) {
		return Component.translatable(items == 1 ? key + ".one" : kinds == 1 ? key + ".one_kind" : key, items, kinds);
	}
}

package io.github.jcondedata.aliveworkplace.craft;

import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * A Librarian making the bookish things a builder nearby is waiting for — books, bookshelves, lecterns, paper — with the
 * game's recipes (and enchanting the village's gear in between, see {@code scribe/EnchantWork}).
 */
public class ScribeWork extends CrafterWork {
	static final Set<Item> MAKES = Set.of(Items.BOOK, Items.BOOKSHELF, Items.CHISELED_BOOKSHELF, Items.LECTERN, Items.PAPER, Items.WRITABLE_BOOK);

	public ScribeWork() {
		super(Crafting.Kind.CRAFTING, "scribe_crafting", "crafter", false);
	}

	@Override
	protected boolean checkExtraStartConditions(net.minecraft.server.level.ServerLevel level, net.minecraft.world.entity.npc.Villager villager) {
		return super.checkExtraStartConditions(level, villager)
			&& (isBusy(villager) || !io.github.jcondedata.aliveworkplace.scribe.EnchantWork.isBusy(villager));
	}

	@Override
	protected boolean wants(Item item) {
		return MAKES.contains(item);
	}
}

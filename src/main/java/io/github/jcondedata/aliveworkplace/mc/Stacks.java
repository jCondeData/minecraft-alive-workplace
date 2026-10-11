package io.github.jcondedata.aliveworkplace.mc;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/** Item stacks written as text, the way {@code /give} reads them (the components' syntax changes between versions). */
public final class Stacks {
	/**
	 * The stack {@code text} names: an item id, maybe with components ({@code minecraft:leather_helmet[dyed_color={rgb:1908001}]});
	 * empty when it can't be read.
	 */
	public static ItemStack parse(HolderLookup.Provider registries, String text) {
		try {
			ItemParser.ItemResult result = new ItemParser(registries).parse(new StringReader(text));
			return new ItemStack(result.item(), 1, result.components());
		} catch (CommandSyntaxException e) {
			return ItemStack.EMPTY;
		}
	}

	private Stacks() {
	}
}

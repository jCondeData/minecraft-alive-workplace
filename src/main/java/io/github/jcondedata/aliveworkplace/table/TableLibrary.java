package io.github.jcondedata.aliveworkplace.table;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The Blueprint Table as a library (36.13): a player stores the blueprints they bought in the table and takes one back
 * out whenever they like, so a blueprint never has to sit in a chest. What's stored is the table's own
 * ({@link BlueprintTableBlockEntity}): anyone at the table sees it, it's saved with the table and dropped when it breaks.
 */
public final class TableLibrary {
	/** Whether {@code stack} can be stored: a blueprint for a build, or a Wonder Plan (not a Blank Blueprint). */
	public static boolean storable(ItemStack stack) {
		return stack.is(ModItems.BLUEPRINT) && BlueprintItem.data(stack).isPresent()
			|| stack.is(ModItems.WONDER_PLAN) && WonderPlanItem.plan(stack).isPresent();
	}

	@Nullable
	public static BlueprintTableBlockEntity at(ServerPlayer player, BlockPos table) {
		return player.level().getBlockEntity(table) instanceof BlueprintTableBlockEntity entity ? entity : null;
	}

	/** What the screen lists: each stored blueprint and how many of it the table holds. */
	public static List<TablePayloads.Stored> stored(@Nullable BlueprintTableBlockEntity table) {
		List<TablePayloads.Stored> out = new ArrayList<>();
		if (table != null) {
			for (BlueprintTableBlockEntity.Group g : table.groups()) {
				out.add(new TablePayloads.Stored(g.stack(), g.count()));
			}
		}
		return out;
	}

	/**
	 * Stores the blueprint in the player's main hand, or with {@code all} every blueprint in their inventory, in the
	 * table. Returns how many went in (and tells the player); what doesn't fit in a full table stays with them.
	 */
	public static int store(ServerPlayer player, BlockPos table, boolean all) {
		BlueprintTableBlockEntity shelf = TableServer.atTable(player, table) ? at(player, table) : null;
		if (shelf == null) {
			return 0;
		}
		Inventory inventory = player.getInventory();
		List<Integer> slots = new ArrayList<>();
		if (all) {
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				if (storable(inventory.getItem(i))) {
					slots.add(i);
				}
			}
		} else if (storable(inventory.getSelected())) {
			slots.add(inventory.selected);
		}
		if (slots.isEmpty()) {
			Chat.actionBar(player, Component.translatable(all ? "message.aliveworkplace.table.store_none" : "message.aliveworkplace.table.store_not_held")
				.withStyle(ChatFormatting.YELLOW));
			return 0;
		}
		int stored = 0;
		Component last = Component.empty();
		boolean full = false;
		for (int slot : slots) {
			ItemStack stack = inventory.getItem(slot);
			while (!stack.isEmpty()) {
				if (!shelf.put(stack)) {
					full = true;
					break;
				}
				last = stack.getHoverName();
				stack.shrink(1);
				stored++;
			}
			inventory.setItem(slot, stack.isEmpty() ? ItemStack.EMPTY : stack);
			if (full) {
				break;
			}
		}
		if (stored > 0) {
			player.level().playSound(null, table, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 1f, 1f);
		}
		if (full) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.store_full", BlueprintTableBlockEntity.CAPACITY)
				.withStyle(ChatFormatting.YELLOW));
		} else {
			Chat.actionBar(player, stored == 1 ? Component.translatable("message.aliveworkplace.table.stored", last)
				: Component.translatable("message.aliveworkplace.table.stored_many", stored));
		}
		TableServer.open(player, table);
		return stored;
	}

	/**
	 * Takes one blueprint like {@code sample} out of the table into the player's inventory. Refused (it stays in the
	 * table) when the inventory has no room or the table doesn't hold one.
	 */
	public static boolean takeOut(ServerPlayer player, BlockPos table, ItemStack sample) {
		BlueprintTableBlockEntity shelf = TableServer.atTable(player, table) ? at(player, table) : null;
		if (shelf == null || sample.isEmpty()) {
			return false;
		}
		if (player.getInventory().getFreeSlot() < 0) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.take_out_full").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		ItemStack out = shelf.remove(sample);
		if (out.isEmpty()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.not_stored").withStyle(ChatFormatting.YELLOW));
			TableServer.open(player, table);
			return false;
		}
		Component name = out.getHoverName();
		if (!player.getInventory().add(out)) {
			shelf.put(out); // no room after all: back on the shelf
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.take_out_full").withStyle(ChatFormatting.YELLOW));
			return false;
		}
		player.level().playSound(null, table, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1f, 1f);
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.table.took_out", name));
		TableServer.open(player, table);
		return true;
	}

	private TableLibrary() {
	}
}

package io.github.jcondedata.aliveworkplace.table;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Blueprint Table's own shelf (36.13): the bought blueprints a player stored in it, one stack of one per blueprint,
 * in the order they went in. They belong to the table: saved with it, dropped when it is broken. A Table placed before
 * 36.13 gets one of these the first time it is used, with nothing stored.
 */
public class BlueprintTableBlockEntity extends BlockEntity {
	/** Most blueprints one table holds. */
	public static final int CAPACITY = 200;

	private final List<ItemStack> stored = new ArrayList<>();

	public BlueprintTableBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.BLUEPRINT_TABLE_ENTITY, pos, state);
	}

	/** A blueprint and how many of it are stored ({@code stack} is one of them, for its name and icon). */
	public record Group(ItemStack stack, int count) {
	}

	/** Blueprints stored, in all. */
	public int count() {
		return stored.size();
	}

	public boolean full() {
		return stored.size() >= CAPACITY;
	}

	/** What's stored, alike blueprints together (same item, same components), in the order the first of each went in. */
	public List<Group> groups() {
		List<ItemStack> samples = new ArrayList<>();
		List<Integer> counts = new ArrayList<>();
		for (ItemStack stack : stored) {
			int at = -1;
			for (int i = 0; i < samples.size(); i++) {
				if (ItemStack.isSameItemSameComponents(samples.get(i), stack)) {
					at = i;
					break;
				}
			}
			if (at < 0) {
				samples.add(stack.copyWithCount(1));
				counts.add(1);
			} else {
				counts.set(at, counts.get(at) + 1);
			}
		}
		List<Group> out = new ArrayList<>();
		for (int i = 0; i < samples.size(); i++) {
			out.add(new Group(samples.get(i), counts.get(i)));
		}
		return out;
	}

	/** Stores one of {@code stack} (a copy). False when the table is full. */
	public boolean put(ItemStack stack) {
		if (stack.isEmpty() || full()) {
			return false;
		}
		stored.add(stack.copyWithCount(1));
		setChanged();
		return true;
	}

	/** Takes out the last-stored blueprint like {@code sample}, or empty if none is stored. */
	public ItemStack remove(ItemStack sample) {
		for (int i = stored.size() - 1; i >= 0; i--) {
			if (ItemStack.isSameItemSameComponents(stored.get(i), sample)) {
				ItemStack out = stored.remove(i);
				setChanged();
				return out;
			}
		}
		return ItemStack.EMPTY;
	}

	/** Everything stored, emptied out of the table (for dropping it when the table is broken). */
	public List<ItemStack> takeAll() {
		List<ItemStack> out = new ArrayList<>(stored);
		stored.clear();
		setChanged();
		return out;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		stored.clear();
		ListTag list = Nbt.getList(tag, "stored", Tag.TAG_COMPOUND); // absent on a table saved before 36.13: nothing stored
		for (int i = 0; i < list.size(); i++) {
			ItemStack.parse(registries, Nbt.compoundAt(list, i)).filter(s -> !s.isEmpty()).ifPresent(s -> stored.add(s.copyWithCount(1)));
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ListTag list = new ListTag();
		for (ItemStack stack : stored) {
			list.add(stack.save(registries));
		}
		tag.put("stored", list);
	}
}

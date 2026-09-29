package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A Drop Box's 27 slots: whatever's put in goes to the storehouse with the next porter. Workers never take from it as a
 * supply chest ({@link io.github.jcondedata.aliveworkplace.work.PrivateContainer}); only porters empty it.
 */
public class DropBoxBlockEntity extends BaseContainerBlockEntity implements io.github.jcondedata.aliveworkplace.work.PrivateContainer {
	public static final int SIZE = 27;

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

	public DropBoxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.DROP_BOX_ENTITY, pos, state);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("block.aliveworkplace.drop_box");
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> items) {
		this.items = items;
	}

	@Override
	protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
		return ChestMenu.threeRows(id, inventory, this);
	}

	@Override
	public int getContainerSize() {
		return SIZE;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(tag, items, registries);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
	}
}

package io.github.jcondedata.aliveworkplace.shop;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.PrivateContainer;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * A shop's price list: nine columns, the top slot is what one sale hands over (item and amount), the
 * slot under it is what that costs. The goods themselves come from the chests near the counter.
 */
public class ShopCounterBlockEntity extends BaseContainerBlockEntity implements PrivateContainer {
	public static final int COLUMNS = 9;

	private NonNullList<ItemStack> items = NonNullList.withSize(COLUMNS * 2, ItemStack.EMPTY);
	@Nullable
	private UUID owner;
	private String ownerName = "";

	public ShopCounterBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.SHOP_COUNTER_ENTITY, pos, state);
	}

	@Nullable
	public UUID owner() {
		return owner;
	}

	public String ownerName() {
		return ownerName;
	}

	public void setOwner(UUID owner, String name) {
		this.owner = owner;
		this.ownerName = name;
		setChanged();
	}

	/** What one sale in {@code column} hands over (empty if the column is unused). */
	public ItemStack goods(int column) {
		return items.get(column);
	}

	/** What a sale in {@code column} costs. */
	public ItemStack price(int column) {
		return items.get(column + COLUMNS);
	}

	@Override
	protected Component getDefaultName() {
		return Component.translatable("container.aliveworkplace.shop_counter");
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
		return new ChestMenu(MenuType.GENERIC_9x2, id, inventory, this, 2);
	}

	@Override
	public int getContainerSize() {
		return COLUMNS * 2;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		items = NonNullList.withSize(COLUMNS * 2, ItemStack.EMPTY);
		ContainerHelper.loadAllItems(tag, items, registries);
		owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
		ownerName = tag.getString("ownerName");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		if (owner != null) {
			tag.putUUID("owner", owner);
		}
		tag.putString("ownerName", ownerName);
	}
}

package io.github.jcondedata.aliveworkplace.mail;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A player's mailbox: 27 slots of mail that arrived for them, and who it belongs to. */
public class MailboxBlockEntity extends BaseContainerBlockEntity implements ExtendedScreenHandlerFactory<BlockPos> {
	public static final int SIZE = 27;

	private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
	@Nullable
	private UUID owner;
	private String ownerName = "";

	public MailboxBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.MAILBOX_ENTITY, pos, state);
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

	/** Puts mail in, filling partial stacks first. Returns what didn't fit. */
	public ItemStack receive(ItemStack stack) {
		ItemStack rest = stack.copy();
		for (int i = 0; i < SIZE && !rest.isEmpty(); i++) {
			ItemStack here = items.get(i);
			if (!here.isEmpty() && ItemStack.isSameItemSameComponents(here, rest) && here.getCount() < here.getMaxStackSize()) {
				int move = Math.min(rest.getCount(), here.getMaxStackSize() - here.getCount());
				here.grow(move);
				rest.shrink(move);
			}
		}
		for (int i = 0; i < SIZE && !rest.isEmpty(); i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, rest.copy());
				rest = ItemStack.EMPTY;
			}
		}
		setChanged();
		return rest;
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide) {
			BlockState state = getBlockState();
			boolean hasMail = !isEmpty();
			if (state.hasProperty(MailboxBlock.HAS_MAIL) && state.getValue(MailboxBlock.HAS_MAIL) != hasMail) {
				level.setBlock(worldPosition, state.setValue(MailboxBlock.HAS_MAIL, hasMail), 3);
			}
		}
	}

	@Override
	protected Component getDefaultName() {
		return ownerName.isEmpty() ? Component.translatable("block.aliveworkplace.mailbox")
			: Component.translatable("container.aliveworkplace.mailbox", ownerName);
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
		return new MailboxMenu(id, inventory, this);
	}

	@Override
	public BlockPos getScreenOpeningData(ServerPlayer player) {
		return worldPosition;
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

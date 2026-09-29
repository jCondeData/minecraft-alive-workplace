package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Who a Storehouse belongs to: whoever placed it (or had a builder build it). Village storehouses belong to nobody. */
public class StorehouseBlockEntity extends BlockEntity {
	@Nullable
	private UUID owner;
	private String ownerName = "";
	/** Stock orders: item to how many to keep in the store (see {@link StockOrders}). */
	private java.util.Map<net.minecraft.world.item.Item, Integer> orders = new java.util.LinkedHashMap<>();

	public StorehouseBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.STOREHOUSE_ENTITY, pos, state);
	}

	/** The owner as an employer (the porter working here answers to them), or null for a village storehouse. */
	@Nullable
	public Employer owner() {
		return owner == null ? null : new Employer(owner, ownerName);
	}

	public void setOwner(UUID owner, String name) {
		this.owner = owner;
		this.ownerName = name;
		setChanged();
	}

	public java.util.Map<net.minecraft.world.item.Item, Integer> orders() {
		return java.util.Collections.unmodifiableMap(orders);
	}

	public void setOrders(java.util.Map<net.minecraft.world.item.Item, Integer> orders) {
		this.orders = new java.util.LinkedHashMap<>(orders);
		setChanged();
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		owner = Nbt.hasUuid(tag, "owner") ? Nbt.getUuid(tag, "owner") : null;
		ownerName = Nbt.getString(tag, "ownerName");
		orders = new java.util.LinkedHashMap<>();
		for (net.minecraft.nbt.Tag t : Nbt.getList(tag, "orders", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
			CompoundTag order = (CompoundTag) t;
			net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(Nbt.getString(order, "item"));
			if (id != null && net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id)) {
				orders.put(Lookup.value(net.minecraft.core.registries.BuiltInRegistries.ITEM, id), Math.max(1, Nbt.getInt(order, "keep")));
			}
		}
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (owner != null) {
			Nbt.putUuid(tag, "owner", owner);
			tag.putString("ownerName", ownerName);
		}
		if (!orders.isEmpty()) {
			net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
			orders.forEach((item, keep) -> {
				CompoundTag order = new CompoundTag();
				order.putString("item", net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
				order.putInt("keep", keep);
				list.add(order);
			});
			tag.put("orders", list);
		}
	}
}

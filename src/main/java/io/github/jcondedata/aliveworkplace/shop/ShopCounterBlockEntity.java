package io.github.jcondedata.aliveworkplace.shop;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
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

	/** How many sales the log keeps. */
	public static final int LOG_SIZE = 20;

	/** One sale: on which in-game day, to whom, what, and for how many CobbleDollars or which items. */
	public record Sale(long day, String buyer, ItemStack goods, long dollars, ItemStack paid) {
	}

	private NonNullList<ItemStack> items = NonNullList.withSize(COLUMNS * 2, ItemStack.EMPTY);
	@Nullable
	private UUID owner;
	private String ownerName = "";
	private final java.util.ArrayDeque<Sale> sales = new java.util.ArrayDeque<>();
	private long totalDollars;
	private int totalSales;

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

	/** Notes a sale in the log (newest first, the oldest drop off). */
	public void logSale(Sale sale) {
		sales.addFirst(sale);
		while (sales.size() > LOG_SIZE) {
			sales.removeLast();
		}
		totalSales++;
		totalDollars += sale.dollars();
		setChanged();
	}

	/** The most recent sales, newest first. */
	public java.util.List<Sale> sales() {
		return java.util.List.copyOf(sales);
	}

	public int totalSales() {
		return totalSales;
	}

	public long totalDollars() {
		return totalDollars;
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
		owner = Nbt.hasUuid(tag, "owner") ? Nbt.getUuid(tag, "owner") : null;
		ownerName = Nbt.getString(tag, "ownerName");
		sales.clear();
		net.minecraft.nbt.ListTag log = Nbt.getList(tag, "sales", net.minecraft.nbt.Tag.TAG_COMPOUND);
		for (int i = 0; i < log.size(); i++) {
			CompoundTag sale = Nbt.compoundAt(log, i);
			sales.addLast(new Sale(Nbt.getLong(sale, "day"), Nbt.getString(sale, "buyer"),
				ItemStack.parseOptional(registries, Nbt.getCompound(sale, "goods")), Nbt.getLong(sale, "dollars"),
				ItemStack.parseOptional(registries, Nbt.getCompound(sale, "paid"))));
		}
		totalSales = Nbt.getInt(tag, "totalSales");
		totalDollars = Nbt.getLong(tag, "totalDollars");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		ContainerHelper.saveAllItems(tag, items, registries);
		if (owner != null) {
			Nbt.putUuid(tag, "owner", owner);
		}
		tag.putString("ownerName", ownerName);
		net.minecraft.nbt.ListTag log = new net.minecraft.nbt.ListTag();
		for (Sale sale : sales) {
			CompoundTag entry = new CompoundTag();
			entry.putLong("day", sale.day());
			entry.putString("buyer", sale.buyer());
			entry.put("goods", sale.goods().saveOptional(registries));
			entry.putLong("dollars", sale.dollars());
			entry.put("paid", sale.paid().saveOptional(registries));
			log.add(entry);
		}
		tag.put("sales", log);
		tag.putInt("totalSales", totalSales);
		tag.putLong("totalDollars", totalDollars);
	}
}

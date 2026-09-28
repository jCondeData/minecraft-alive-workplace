package io.github.jcondedata.aliveworkplace.store;

import io.github.jcondedata.aliveworkplace.build.Employer;
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

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
		ownerName = tag.getString("ownerName");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (owner != null) {
			tag.putUUID("owner", owner);
			tag.putString("ownerName", ownerName);
		}
	}
}

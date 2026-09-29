package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** A Village Hall's name for its village (from a Name Tag, or the hall item's anvil name); none: a made-up one. */
public class VillageHallBlockEntity extends BlockEntity implements Nameable {
	@Nullable
	private Component name;
	/** How the village is doing, from the last round (not saved: the first tick counts again). */
	@Nullable
	private VillageNeeds.Needs needs;

	public VillageHallBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.VILLAGE_HALL_ENTITY, pos, state);
	}

	@Nullable
	@Override
	public Component getCustomName() {
		return name;
	}

	@Override
	public Component getName() {
		return name != null ? name : VillageHalls.madeUpName(worldPosition);
	}

	/** How the village was doing at the last round, or null before the first. */
	@Nullable
	public VillageNeeds.Needs needs() {
		return needs;
	}

	/** Every {@link VillageNeeds#CHECK_EVERY} ticks (and on the first): the hungry fed, the village counted. */
	public static void serverTick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, VillageHallBlockEntity hall) {
		if (level instanceof net.minecraft.server.level.ServerLevel server
			&& (hall.needs == null || Math.floorMod(level.getGameTime() + pos.hashCode(), VillageNeeds.CHECK_EVERY) == 0)) {
			hall.needs = VillageNeeds.check(server, pos);
		}
	}

	public void setCustomName(@Nullable Component name) {
		this.name = name;
		setChanged();
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		name = tag.contains("CustomName", 8) ? parseCustomNameSafe(tag.getString("CustomName"), registries) : null;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (name != null) {
			tag.putString("CustomName", Component.Serializer.toJson(name, registries));
		}
	}

	@Override
	protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
		super.applyImplicitComponents(input);
		name = input.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		builder.set(DataComponents.CUSTOM_NAME, name);
	}

	@SuppressWarnings("deprecation")
	@Override
	public void removeComponentsFromTag(CompoundTag tag) {
		tag.remove("CustomName");
	}
}

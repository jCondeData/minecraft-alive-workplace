package io.github.jcondedata.aliveworkplace.grave;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Who lies in a grave: the villager as they were when they died (job, level, trades, name), to bring back. */
public class GraveBlockEntity extends BlockEntity {
	private CompoundTag villager = new CompoundTag();
	@Nullable
	private Component name;
	private String profession = "minecraft:none";
	private int villagerLevel = 1;
	private long died;

	public GraveBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.GRAVE_ENTITY, pos, state);
	}

	public void fill(CompoundTag villager, Component name, String profession, int level, long died) {
		this.villager = villager;
		this.name = name;
		this.profession = profession;
		this.villagerLevel = level;
		this.died = died;
		setChanged();
	}

	public CompoundTag villager() {
		return villager;
	}

	public Component name() {
		return name != null ? name : Component.translatable("entity.minecraft.villager");
	}

	public String profession() {
		return profession;
	}

	public int villagerLevel() {
		return villagerLevel;
	}

	public long died() {
		return died;
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		villager = Nbt.getCompound(tag, "villager");
		name = Nbt.has(tag, "name", 8) ? parseCustomNameSafe(Nbt.getString(tag, "name"), registries) : null;
		profession = tag.contains("profession") ? Nbt.getString(tag, "profession") : "minecraft:none";
		villagerLevel = Math.max(1, Nbt.getInt(tag, "level"));
		died = Nbt.getLong(tag, "died");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.put("villager", villager);
		if (name != null) {
			tag.putString("name", Component.Serializer.toJson(name, registries));
		}
		tag.putString("profession", profession);
		tag.putInt("level", villagerLevel);
		tag.putLong("died", died);
	}
}

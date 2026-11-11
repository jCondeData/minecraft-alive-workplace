package io.github.jcondedata.aliveworkplace.inn;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Shop Counters an Innkeeper has been seen working, and which Innkeeper, one list per dimension (B1203, B1205): a
 * Shop Counter is an inn while an Innkeeper works it, and the village has to know that while he is out past its edge,
 * asleep or in an unloaded chunk. {@link Innkeepers#counters} checks a remembered counter against its Innkeeper while
 * he is loaded, and a counter is forgotten when another trade is seen working it or its Innkeeper is gone for good
 * (killed, discarded, turned into a zombie villager). Older worlds have none saved and learn them as the Innkeepers work.
 */
public final class InnCounters extends SavedData {
	public static final String NAME = "aliveworkplace_inn_counters";
	private static final String COUNTERS = "counters";
	private static final String POS = "pos";
	private static final String KEEPER = "keeper";
	/** counter → its Innkeeper */
	private final Long2ObjectLinkedOpenHashMap<UUID> counters = new Long2ObjectLinkedOpenHashMap<>();

	public static InnCounters get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(InnCounters::new, InnCounters::load, null), NAME);
	}

	/** The Innkeeper last seen working the counter at {@code pos}, or null. */
	@Nullable
	public UUID keeper(BlockPos pos) {
		return counters.get(pos.asLong());
	}

	/** {@code keeper} works the counter at {@code pos}. */
	public void remember(BlockPos pos, UUID keeper) {
		if (!keeper.equals(counters.put(pos.asLong(), keeper))) {
			setDirty();
		}
	}

	/** Someone else (or nobody) works the counter at {@code pos} now. */
	public void forget(BlockPos pos) {
		if (counters.remove(pos.asLong()) != null) {
			setDirty();
		}
	}

	/** A villager left the level: an Innkeeper gone for good (not just unloaded with his chunk) leaves his counters. */
	public static void onGone(Entity entity, ServerLevel level) {
		Entity.RemovalReason reason = entity.getRemovalReason();
		if (entity instanceof Villager && reason != null && reason.shouldDestroy()) {
			InnCounters data = get(level);
			if (data.counters.values().removeIf(entity.getUUID()::equals)) {
				data.setDirty();
			}
		}
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Long2ObjectMap.Entry<UUID> e : counters.long2ObjectEntrySet()) {
			CompoundTag one = new CompoundTag();
			one.putLong(POS, e.getLongKey());
			Nbt.putUuid(one, KEEPER, e.getValue());
			list.add(one);
		}
		tag.put(COUNTERS, list);
		return tag;
	}

	public static InnCounters load(CompoundTag tag, HolderLookup.Provider registries) {
		InnCounters data = new InnCounters();
		ListTag list = Nbt.getList(tag, COUNTERS, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag one = Nbt.compoundAt(list, i);
			if (Nbt.has(one, POS, Tag.TAG_ANY_NUMERIC) && Nbt.hasUuid(one, KEEPER)) {
				data.counters.put(Nbt.getLong(one, POS), Nbt.getUuid(one, KEEPER));
			}
		}
		return data;
	}
}

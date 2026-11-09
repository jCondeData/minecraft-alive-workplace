package io.github.jcondedata.aliveworkplace.move;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The moves a village has confirmed on the Hall's Move page (ROADMAP 45.2), one per dimension, waiting for a Mover (45.3)
 * to take them: which building (its blueprint's origin and id), its new lowest corner and its turn. One move per building:
 * a second confirm replaces the first. Kept from 0.153.0 on: an older world starts with none, and an entry that doesn't
 * read is skipped, never the whole list.
 */
public final class PendingMoves extends SavedData {
	public static final String NAME = "aliveworkplace_pending_moves";

	/**
	 * A confirmed move: the village's {@code hall}, the building at {@code from} (its placement's origin) built from
	 * {@code structure}, put up again with its lowest corner at {@code to}, turned by {@code rotation}; {@code by} confirmed it.
	 */
	public record Move(BlockPos hall, BlockPos from, ResourceLocation structure, BlockPos to, Rotation rotation, @Nullable UUID by) {
	}

	private final List<Move> moves = new ArrayList<>();

	public static PendingMoves get(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(PendingMoves::new, PendingMoves::load, null), NAME);
	}

	/** Every move waiting, oldest first. */
	public List<Move> all() {
		return List.copyOf(moves);
	}

	/** The moves waiting in the village round {@code hall}. */
	public List<Move> of(BlockPos hall) {
		return moves.stream().filter(m -> m.hall().equals(hall)).toList();
	}

	/** The move waiting for the building whose origin is {@code from}, if any. */
	public Optional<Move> forBuilding(BlockPos from) {
		return moves.stream().filter(m -> m.from().equals(from)).findFirst();
	}

	/** Adds {@code move}, in place of any move already waiting for the same building. */
	public void put(Move move) {
		moves.removeIf(m -> m.from().equals(move.from()));
		moves.add(move);
		setDirty();
	}

	/** Calls off the move waiting for the building at {@code from}; whether there was one. */
	public boolean remove(BlockPos from) {
		boolean removed = moves.removeIf(m -> m.from().equals(from));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	@Override
	public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
		ListTag list = new ListTag();
		for (Move m : moves) {
			CompoundTag t = new CompoundTag();
			t.putLong("hall", m.hall().asLong());
			t.putLong("from", m.from().asLong());
			t.putString("structure", m.structure().toString());
			t.putLong("to", m.to().asLong());
			t.putString("rotation", m.rotation().name());
			if (m.by() != null) {
				Nbt.putUuid(t, "by", m.by());
			}
			list.add(t);
		}
		tag.put("moves", list);
		return tag;
	}

	public static PendingMoves load(CompoundTag tag, HolderLookup.Provider registries) {
		PendingMoves out = new PendingMoves();
		ListTag list = Nbt.getList(tag, "moves", Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = Nbt.compoundAt(list, i);
			ResourceLocation structure = ResourceLocation.tryParse(Nbt.getString(t, "structure"));
			if (structure == null || !t.contains("from") || !t.contains("to") || !t.contains("hall")) {
				continue; // a move that doesn't read is dropped, the rest kept
			}
			Rotation rotation;
			try {
				rotation = Rotation.valueOf(Nbt.getString(t, "rotation"));
			} catch (IllegalArgumentException e) {
				rotation = Rotation.NONE;
			}
			UUID by = Nbt.hasUuid(t, "by") ? Nbt.getUuid(t, "by") : null;
			out.moves.add(new Move(BlockPos.of(Nbt.getLong(t, "hall")), BlockPos.of(Nbt.getLong(t, "from")), structure,
				BlockPos.of(Nbt.getLong(t, "to")), rotation, by));
		}
		return out;
	}
}

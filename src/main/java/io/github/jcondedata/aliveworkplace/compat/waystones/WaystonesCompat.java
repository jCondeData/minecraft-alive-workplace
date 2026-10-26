//? if waystones {
package io.github.jcondedata.aliveworkplace.compat.waystones;

import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import java.util.Optional;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneStyle;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// The Waystones integration (ROADMAP 41.1): the only code that touches Waystones' classes. It fills
// VillageWaystones.EXTENSION, so a plaza's waystone (set down by a builder as a plain block) becomes a working waystone
// of the mod's, named after the village. Uses Waystones' public API (WaystonesAPI.getWaystoneAt / placeWaystone,
// MutableWaystone.setName) and, to save and send a new name, what its own rename message does (WaystoneManagerImpl
// marked dirty, WaystoneSyncManager.sendWaystoneUpdateToAll).
public final class WaystonesCompat {
	// The Waystones versions this was tested with (the pack's 21.1.37).
	public static final String TESTED = ">=21.1.30 <21.2";

	public static void init() {
		VillageWaystones.EXTENSION.register(VillageWaystones.MOD, new VillageWaystones.Stones() {
			@Override
			public boolean raise(ServerLevel level, BlockPos pos, String name) {
				return WaystonesCompat.raise(level, pos, name);
			}

			@Override
			public Optional<String> nameAt(ServerLevel level, BlockPos pos) {
				return working(level, pos).map(w -> w.getName().getString());
			}
		});
	}

	static Optional<Waystone> working(ServerLevel level, BlockPos pos) {
		// (the waystone's own position may read as its upper half: the upper half's block entity shares the waystone)
		return WaystonesAPI.getWaystoneAt(level, pos).filter(Waystone::isValid)
			.filter(w -> w.getPos().equals(pos) || w.getPos().equals(pos.above()));
	}

	static boolean raise(ServerLevel level, BlockPos pos, String name) {
		BlockState lower = level.getBlockState(pos);
		BlockState upper = level.getBlockState(pos.above());
		if (!VillageWaystones.isStone(lower)) {
			return false;
		}
		Optional<Waystone> waystone = working(level, pos);
		if (waystone.isEmpty()) {
			// A stone set down as a block isn't known to the mod yet: placed again through its API, which registers it,
			// then the blueprint's own states put back (same block: the block entity and its waystone stay).
			WaystoneStyle style = WaystoneStyles.getStyle(lower.getBlock());
			if (style == null) {
				style = WaystoneStyles.DEFAULT;
			}
			waystone = WaystonesAPI.placeWaystone(level, pos, style);
			level.setBlock(pos, lower, Block.UPDATE_CLIENTS);
			if (upper.is(lower.getBlock())) {
				level.setBlock(pos.above(), upper, Block.UPDATE_CLIENTS);
			}
			waystone = waystone.filter(Waystone::isValid).or(() -> working(level, pos));
		}
		if (waystone.isEmpty()) {
			return false;
		}
		Waystone w = waystone.get();
		boolean moved = !w.getPos().equals(pos);
		if ((moved || !w.getName().getString().equals(name)) && w instanceof MutableWaystone mutable) {
			mutable.setName(Component.literal(name));
			mutable.setPos(pos); // travellers step out at the stone's foot
			WaystoneManagerImpl.get(level.getServer()).setDirty();
			WaystoneSyncManager.sendWaystoneUpdateToAll(level.getServer(), w);
		}
		return true;
	}

	private WaystonesCompat() {
	}
}
//?}

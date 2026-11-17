//? if waystones {
package io.github.jcondedata.aliveworkplace.compat.waystones;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import java.util.Optional;
import net.blay09.mods.waystones.api.MutableWaystone;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneStyle;
import net.blay09.mods.waystones.api.WaystoneStyles;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.blay09.mods.waystones.core.WaystoneImpl;
import net.blay09.mods.waystones.core.WaystoneManagerImpl;
import net.blay09.mods.waystones.core.WaystoneSyncManager;
import net.blay09.mods.waystones.network.message.TeleportEffectMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

// The Waystones integration (ROADMAP 41.1, 41.2, 41.3): the only code that touches Waystones' classes. It fills
// VillageWaystones.EXTENSION, so a plaza's waystone (set down by a builder as a plain block) becomes a working waystone
// of the mod's, named after the village. Uses Waystones' public API (WaystonesAPI.getWaystoneAt / placeWaystone,
// MutableWaystone.setName, WaystonesAPI.isWaystoneActivated for discovery) and, to save and send a new name, what its own rename message does (WaystoneManagerImpl
// marked dirty, WaystoneSyncManager.sendWaystoneUpdateToAll). A Mover moves a stone the Silk Touch way (B1310:
// WaystoneBlockEntityBase.setSilkTouched, then initializeFromExisting, called by name: Balm isn't on the compile
// classpath), so it stays the same waystone.
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

			@Override
			public boolean standing(ServerLevel level, BlockPos pos) {
				return known(level, pos).isPresent();
			}

			@Override
			public boolean discovered(ServerPlayer player, ServerLevel level, BlockPos pos) {
				return known(level, pos).map(w -> WaystonesAPI.isWaystoneActivated(player, w)).orElse(false);
			}

			@Override
			public void warp(ServerLevel level, BlockPos pos) {
				WaystonesCompat.warp(level, pos);
			}

			@Override
			public void lifting(ServerLevel level, BlockPos pos) {
				WaystonesCompat.lifting(level, pos);
			}

			@Override
			public void setDown(ServerLevel level, BlockPos pos) {
				WaystonesCompat.setDown(level, pos);
			}
		});
	}

	// B1310: a Mover taking a village's stone down. Broken, a waystone is taken off the mod's list along with every
	// player's discovery of it (WaystoneBlockBase.onRemove -> WaystoneManagerImpl.removeWaystone and
	// PlayerWaystoneManager.removeKnownWaystone); picked up with Silk Touch it is kept instead, marked transient, and
	// players keep it. The Mover lifts it the Silk Touch way (WaystoneBlockEntityBase.setSilkTouched): both halves,
	// whichever comes down first.
	static void lifting(ServerLevel level, BlockPos pos) {
		for (BlockPos p : new BlockPos[] {pos, pos.above()}) {
			BlockEntity stone = level.getBlockEntity(p);
			if (STONE_ENTITY.isInstance(stone)) {
				call(stone, "setSilkTouched", new Class<?>[] {boolean.class}, true);
			}
		}
	}

	// B1310: the stone put up again with its packed data, which names its waystone (the block entity's saved "UUID"): it
	// is that waystone again, standing here, as a silk-touched stone placed again is (WaystoneBlockEntityBase
	// .initializeFromExisting: its spot, no longer transient). The upper half takes it from the lower one
	// (initializeFromBase).
	static void setDown(ServerLevel level, BlockPos pos) {
		BlockEntity lower = level.getBlockEntity(pos);
		if (!STONE_ENTITY.isInstance(lower)) {
			return;
		}
		if (!valid(level, pos)) {
			CompoundTag data = lower.saveWithoutMetadata(level.registryAccess());
			if (data.hasUUID("UUID")) {
				WaystoneManagerImpl.get(level.getServer()).getWaystoneById(data.getUUID("UUID"))
					.filter(w -> w instanceof WaystoneImpl)
					.ifPresent(w -> {
						call(lower, "initializeFromExisting", new Class<?>[] {ServerLevelAccessor.class, WaystoneImpl.class, ItemStack.class}, level, w, ItemStack.EMPTY);
						WaystoneSyncManager.sendWaystoneUpdateToAll(level.getServer(), w);
					});
			}
		}
		BlockEntity upper = level.getBlockEntity(pos.above());
		if (valid(level, pos) && STONE_ENTITY.isInstance(upper) && !valid(level, pos.above())) {
			call(upper, "initializeFromBase", new Class<?>[] {STONE_ENTITY}, lower);
		}
	}

	// Whether the stone's block entity at pos holds a waystone of the mod's (WaystonesAPI.getWaystoneAt asks it).
	private static boolean valid(ServerLevel level, BlockPos pos) {
		return WaystonesAPI.getWaystoneAt(level, pos).filter(Waystone::isValid).isPresent();
	}

	// Waystones' block entity extends Balm's, which isn't on this mod's compile classpath (only in the game): its own
	// methods (Waystones' names, never remapped) are called by name. A Waystones without them logs a warning, and the
	// stone is placed as a new waystone the way it was before B1310.
	private static final Class<?> STONE_ENTITY = stoneEntity();

	private static Class<?> stoneEntity() {
		try {
			return Class.forName("net.blay09.mods.waystones.block.entity.WaystoneBlockEntityBase");
		} catch (ClassNotFoundException e) {
			return Void.class; // nothing is one
		}
	}

	private static void call(BlockEntity entity, String name, Class<?>[] types, Object... args) {
		try {
			STONE_ENTITY.getMethod(name, types).invoke(entity, args);
		} catch (ReflectiveOperationException e) {
			AliveWorkplace.LOG.warn("Waystones: couldn't call {} on a waystone; a moved stone may become a new waystone", name, e);
		}
	}

	static Optional<Waystone> working(ServerLevel level, BlockPos pos) {
		// (the waystone's own position may read as its upper half: the upper half's block entity shares the waystone)
		return WaystonesAPI.getWaystoneAt(level, pos).filter(Waystone::isValid)
			.filter(w -> w.getPos().equals(pos) || w.getPos().equals(pos.above()));
	}

	// The waystone at pos (41.2): from its block where the chunk is loaded, else from the mod's saved list (Waystones takes
	// a broken stone off that list, WaystoneBlockBase.onRemove -> WaystoneManagerImpl.removeWaystone).
	static Optional<Waystone> known(ServerLevel level, BlockPos pos) {
		if (level.isLoaded(pos)) {
			return working(level, pos);
		}
		return WaystoneManagerImpl.get(level.getServer()).getWaystones()
			.filter(w -> w.getDimension().equals(level.dimension()) && (w.getPos().equals(pos) || w.getPos().equals(pos.above())))
			.filter(Waystone::isValid).findFirst();
	}

	// A caravan or the settlers going through the stone at pos (41.3): what WaystoneTeleportManager.performTeleport does
	// for a teleport, the vanilla portal-travel sound at the mod's volume and the mod's own TeleportEffectMessage (its
	// client draws the burst of portal particles) to the players tracking the spot. Sent as a plain custom payload (the
	// type is registered by the mod, through Balm), so nothing here needs Balm on the classpath.
	static void warp(ServerLevel level, BlockPos pos) {
		level.playSound(null, pos, SoundEvents.PORTAL_TRAVEL, SoundSource.PLAYERS, 0.05f, 1f);
		try {
			ClientboundCustomPayloadPacket packet = new ClientboundCustomPayloadPacket(new TeleportEffectMessage(pos));
			for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false)) {
				player.connection.send(packet);
			}
		} catch (RuntimeException e) {
			// (a Waystones without the message: the same burst drawn from here)
			level.sendParticles(ParticleTypes.PORTAL, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 128, 1.5, 1.5, 1.5, 0.5);
		}
	}

	static boolean raise(ServerLevel level, BlockPos pos, String name) {
		BlockState lower = level.getBlockState(pos);
		BlockState upper = level.getBlockState(pos.above());
		if (!VillageWaystones.isStone(lower)) {
			return false;
		}
		Optional<Waystone> waystone = working(level, pos);
		if (waystone.isEmpty()) {
			// B1310: a stone a Mover put up names the waystone it was (its packed data): that one again, not a new one
			setDown(level, pos);
			waystone = working(level, pos);
		}
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
		if ((moved || w.isTransient() || !w.getName().getString().equals(name)) && w instanceof MutableWaystone mutable) {
			mutable.setName(Component.literal(name));
			mutable.setPos(pos); // travellers step out at the stone's foot
			mutable.setTransient(false); // B1310: a stone lifted by a Mover and standing again (put up before its data bound it)
			WaystoneManagerImpl.get(level.getServer()).setDirty();
			WaystoneSyncManager.sendWaystoneUpdateToAll(level.getServer(), w);
		}
		return true;
	}

	private WaystonesCompat() {
	}
}
//?}

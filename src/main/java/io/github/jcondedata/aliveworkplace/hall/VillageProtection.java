package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlock;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A protected village (the Village Hall's setting, off unless its owner turns it on): inside the hall's area —
 * {@link VillageHalls#RADIUS} blocks across the map from the hall, at any height — only the hall's owner, their friends
 * ({@code /workplace friend add}) and operators may break or place blocks, open chests and other blocks, empty buckets,
 * or hurt the villagers, golems, animals, armor stands and item frames (by hand, or with arrows, tridents and potions),
 * and only they may open the hall's screen with a Village Ledger. Anyone may still come in, open doors and gates, press
 * buttons, ring the bell, use a crafting table, trade with the villagers and open their own mailbox; with the village
 * economy on, right-clicking the hall gives them its price board and nothing else, so they can trade there (33.5). The owner is
 * whoever placed the hall (a hall from before this, or placed by a machine, is claimed by the first player to turn its
 * protection on). {@code villageProtection: false} in the config turns the setting off on the whole server.
 */
public final class VillageProtection {
	/** Whether halls may protect their villages at all (the config's {@code villageProtection}). */
	public static boolean ENABLED = true;
	/** The protected halls that are loaded (kept by the halls' block entities), so a click needn't search the map. */
	private static final Set<GlobalPos> PROTECTED = ConcurrentHashMap.newKeySet();

	public static void init() {
		Platform.get().allowBreakBlock((level, player, pos, state) -> mayChange(level, player, pos));
		Platform.get().onUseBlock((player, level, hand, hit) -> {
			BlockState state = level.getBlockState(hit.getBlockPos());
			boolean placing = player.isSecondaryUseActive() && !player.getItemInHand(hand).isEmpty();
			if (level.isClientSide() || (open(state) && !placing)) {
				return InteractionResult.PASS;
			}
			// A stranger at the hall (33.5): the price board alone, whatever they hold, so they can trade there
			if (!placing && hand == net.minecraft.world.InteractionHand.MAIN_HAND && state.is(io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL)
				&& io.github.jcondedata.aliveworkplace.trade.TradePage.shown() && level instanceof ServerLevel server && player instanceof ServerPlayer stranger
				&& keeper(server, player, hit.getBlockPos()).isPresent()) {
				io.github.jcondedata.aliveworkplace.trade.TradePage.openForStranger(stranger, hit.getBlockPos());
				return InteractionResult.SUCCESS;
			}
			return mayChange(level, player, hit.getBlockPos()) ? InteractionResult.PASS : InteractionResult.FAIL;
		});
		Platform.get().allowUseItem((player, level, hand) ->
			!(player.getItemInHand(hand).getItem() instanceof BucketItem) || mayChange(level, player, player.blockPosition()));
		Platform.get().allowAttackEntity((player, level, entity) -> !kept(entity) || mayChange(level, player, entity.blockPosition()));
		// Arrows, tridents, thrown potions and the like: the player who threw or shot them (their hits in melee are above)
		Platform.get().allowDamage((entity, source, amount) -> !(source.getEntity() instanceof Player player) || source.getDirectEntity() == player
			|| !kept(entity) || mayChange(entity.level(), player, entity.blockPosition()));
		Platform.get().onUseEntity((player, level, hand, entity, hit) ->
			(entity instanceof HangingEntity || entity instanceof ArmorStand) && !mayChange(level, player, entity.blockPosition())
				? InteractionResult.FAIL : InteractionResult.PASS);
	}

	/** Blocks anyone may use in a protected village. */
	static boolean open(BlockState state) {
		return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock
			|| state.getBlock() instanceof ButtonBlock || state.getBlock() instanceof LeverBlock || state.getBlock() instanceof BellBlock
			|| state.getBlock() instanceof CraftingTableBlock || state.getBlock() instanceof MailboxBlock;
	}

	/** What a protected village keeps others from hurting. */
	static boolean kept(Entity entity) {
		return entity instanceof AbstractVillager || entity instanceof IronGolem || entity instanceof Animal
			|| entity instanceof ArmorStand || entity instanceof HangingEntity;
	}

	/**
	 * Whether {@code player} may change things at {@code pos}; if not, they're told whose village it is. On a client it's
	 * always yes (the server decides, and puts back what the client guessed).
	 */
	public static boolean mayChange(Level level, Player player, BlockPos pos) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return true;
		}
		Optional<BlockPos> hall = keeper(server, player, pos);
		if (hall.isEmpty()) {
			return true;
		}
		String owner = server.getBlockEntity(hall.get()) instanceof VillageHallBlockEntity entity ? entity.ownerName() : "";
		Chat.actionBar(serverPlayer, Component.translatable("message.aliveworkplace.protection.denied", VillageHalls.name(server, hall.get()), owner)
			.withStyle(ChatFormatting.RED));
		return false;
	}

	/** The protected hall whose area {@code pos} is in and that keeps {@code player} out, if any. */
	public static Optional<BlockPos> keeper(ServerLevel level, Player player, BlockPos pos) {
		if (!ENABLED || player.hasPermissions(2)) {
			return Optional.empty();
		}
		if (PROTECTED.isEmpty()) {
			return Optional.empty();
		}
		long radiusSqr = (long) VillageHalls.RADIUS * VillageHalls.RADIUS;
		return PROTECTED.stream()
			.filter(g -> g.dimension() == level.dimension())
			.map(GlobalPos::pos)
			.filter(h -> {
				long dx = h.getX() - pos.getX();
				long dz = h.getZ() - pos.getZ();
				return dx * dx + dz * dz <= radiusSqr;
			})
			.filter(h -> level.isLoaded(h) && level.getBlockEntity(h) instanceof VillageHallBlockEntity entity && entity.isProtected()
				&& !mayBuild(level, entity, player))
			.findFirst();
	}

	/**
	 * A protected hall whose area takes in {@code pos} and whose owner isn't {@code owner} (nor counts them a friend), if
	 * any: where the Steward of {@code owner}'s village never builds (27.19). Works with protection switched off too,
	 * since that switch is about players.
	 */
	public static Optional<BlockPos> foreignKeeper(ServerLevel level, @org.jetbrains.annotations.Nullable UUID owner, BlockPos pos) {
		long radiusSqr = (long) VillageHalls.RADIUS * VillageHalls.RADIUS;
		return PROTECTED.stream()
			.filter(g -> g.dimension() == level.dimension())
			.map(GlobalPos::pos)
			.filter(h -> {
				long dx = h.getX() - pos.getX();
				long dz = h.getZ() - pos.getZ();
				return dx * dx + dz * dz <= radiusSqr;
			})
			.filter(h -> level.isLoaded(h) && level.getBlockEntity(h) instanceof VillageHallBlockEntity entity && entity.isProtected()
				&& entity.owner() != null && !entity.owner().equals(owner)
				&& (owner == null || !Friends.get(level.getServer()).mayDirect(entity.owner(), owner)))
			.findFirst();
	}

	/** Keeps the list of loaded protected halls up to date (from the hall's block entity). */
	static void mark(Level level, BlockPos hall, boolean on) {
		if (level.isClientSide()) {
			return;
		}
		GlobalPos where = GlobalPos.of(level.dimension(), hall.immutable());
		if (on) {
			PROTECTED.add(where);
		} else {
			PROTECTED.remove(where);
		}
	}

	/** Whether {@code player} is the hall's owner, one of their friends, or an operator. */
	public static boolean mayBuild(ServerLevel level, VillageHallBlockEntity hall, Player player) {
		UUID owner = hall.owner();
		return owner == null || owner.equals(player.getUUID()) || player.hasPermissions(2)
			|| Friends.get(level.getServer()).mayDirect(owner, player.getUUID());
	}

	/**
	 * Whether {@code player} rules the village (design note M33): the hall's owner, one of their friends, or an operator,
	 * in every village, protected or not. Unlike {@link #mayBuild}, a hall nobody owns grants it to no one.
	 */
	public static boolean mayRule(ServerLevel level, VillageHallBlockEntity hall, Player player) {
		UUID owner = hall.owner();
		return owner != null && (owner.equals(player.getUUID()) || player.hasPermissions(2) || Friends.get(level.getServer()).mayDirect(owner, player.getUUID()));
	}

	/**
	 * Turns the village's protection on or off, if {@code player} may: the hall's owner, or an operator; a hall nobody
	 * owns yet becomes {@code player}'s. What to tell them.
	 */
	public static Component toggle(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Component.empty();
		}
		if (!ENABLED) {
			return Component.translatable("message.aliveworkplace.protection.disabled").withStyle(ChatFormatting.GRAY);
		}
		if (entity.owner() == null) {
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		} else if (!entity.owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
			return Component.translatable("message.aliveworkplace.protection.not_owner", entity.ownerName()).withStyle(ChatFormatting.RED);
		}
		entity.setProtected(!entity.isProtected());
		Chronicle.record(level, hall, Chronicle.Kind.PROTECTION, Component.translatable(entity.isProtected()
			? "chronicle.aliveworkplace.protected" : "chronicle.aliveworkplace.unprotected", player.getDisplayName()), true);
		return Component.translatable(entity.isProtected() ? "message.aliveworkplace.protection.on" : "message.aliveworkplace.protection.off",
			VillageHalls.name(level, hall)).withStyle(entity.isProtected() ? ChatFormatting.GREEN : ChatFormatting.YELLOW);
	}

	private VillageProtection() {
	}
}

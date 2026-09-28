package io.github.jcondedata.aliveworkplace.orchard;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonOrchard;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.AreaJobs;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Orchards: a Field Marker given to an Orchard Keeper marks ground they keep planted — sweet berry bushes, and with
 * Cobblemon apricorn trees and berry plants — from what's in the chests by the Fruit Basket, in a grid, and they pick
 * what grows there even beyond the 16 blocks they'd look on their own.
 */
public final class Orchards {
	private static final boolean COBBLEMON = FabricLoader.getInstance().isModLoaded("cobblemon");
	/** Blocks between plants: 2 for bushes and berry plants, 3 for apricorn trees. */
	static final int SPACING = 2;
	static final int SPACING_TREES = 3;

	public static boolean isKeeper(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER;
	}

	@Nullable
	public static BoundingBox orchard(Villager villager) {
		FieldJob job = villager.getAttached(ModAttachments.ORCHARD);
		return job == null ? null : job.box();
	}

	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack) {
		return AreaJobs.assign(player, villager, stack, ModAttachments.ORCHARD, "orchard_area");
	}

	/** Gives the keeper the orchard. Also used by tests. */
	public static void start(Villager villager, BoundingBox box) {
		villager.setAttached(ModAttachments.ORCHARD, new FieldJob(box));
	}

	public static void release(ServerLevel level, Villager villager, @Nullable Player player) {
		AreaJobs.release(level, villager, player, ModAttachments.ORCHARD);
	}

	public static void onDeath(ServerLevel level, Villager villager) {
		AreaJobs.onDeath(level, villager, ModAttachments.ORCHARD);
	}

	public static void sendStatus(Player player, Villager villager) {
		BoundingBox box = orchard(villager);
		if (box != null) {
			AreaJobs.sendStatus(player, villager, box, "orchard_area", Component.translatable("message.aliveworkplace.orchard_area.counts",
				villager.getAttachedOrElse(ModAttachments.FRUIT_PICKED, 0), villager.getAttachedOrElse(ModAttachments.SAPLINGS_PLANTED, 0)));
		}
	}

	// --- planting ------------------------------------------------------------------------------

	/** Something an orchard keeper plants: sweet berries, and with Cobblemon apricorn seeds and berries. */
	public static boolean isSeed(ItemStack stack) {
		return !stack.isEmpty() && stack.getItem() instanceof BlockItem
			&& (stack.is(Items.SWEET_BERRIES) || COBBLEMON && CobblemonOrchard.isSeed(stack));
	}

	private static int spacing(ItemStack seed) {
		return COBBLEMON && CobblemonOrchard.growsIntoTree(seed) ? SPACING_TREES : SPACING;
	}

	/** One of each kind of seed the keeper carries, the kind they have most of first. */
	static java.util.List<ItemStack> seedsCarried(BuilderBag bag) {
		java.util.Map<net.minecraft.world.item.Item, ItemStack> kinds = new java.util.LinkedHashMap<>();
		for (ItemStack stack : bag.stacks()) {
			if (isSeed(stack)) {
				kinds.putIfAbsent(stack.getItem(), stack.copyWithCount(1));
			}
		}
		java.util.List<ItemStack> out = new java.util.ArrayList<>(kinds.values());
		out.sort(java.util.Comparator.comparingInt((ItemStack s) -> bag.count(s.getItem())).reversed());
		return out;
	}

	/** The next empty spot for {@code seed} in the orchard's grid, nearest to {@code near}; empty if it's full. */
	static Optional<BlockPos> nextSpot(ServerLevel level, BoundingBox orchard, ItemStack seed, BlockPos near) {
		if (!(seed.getItem() instanceof BlockItem item)) {
			return Optional.empty();
		}
		BlockState planted = item.getBlock().defaultBlockState();
		int spacing = spacing(seed);
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int x = orchard.minX(); x <= orchard.maxX(); x += spacing) {
			for (int z = orchard.minZ(); z <= orchard.maxZ(); z += spacing) {
				BlockPos spot = ground(level, orchard, x, z);
				if (spot == null || !planted.canSurvive(level, spot) || !level.getBlockState(spot.above()).isAir()) {
					continue;
				}
				double d = spot.distSqr(near);
				if (d < bestDistance) {
					bestDistance = d;
					best = spot;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** The free spot on the ground in this column: open space with something solid right under it (null if taken). */
	@Nullable
	private static BlockPos ground(ServerLevel level, BoundingBox orchard, int x, int z) {
		for (int y = orchard.maxY() + 3; y >= orchard.minY() - 2; y--) {
			BlockPos p = new BlockPos(x, y, z);
			BlockState at = level.getBlockState(p);
			if (!at.isAir() && !(at.canBeReplaced() && at.getFluidState().isEmpty())) {
				if (!level.getBlockState(p.above()).isAir()) {
					return null; // a plant or a tree already stands here
				}
				continue;
			}
			if (level.getBlockState(p.below()).blocksMotion()) {
				return p;
			}
		}
		return null;
	}

	/** Plants one {@code seed} at {@code spot} the way a player would (so Cobblemon's berry plants get set up right). */
	static boolean plant(ServerLevel level, Villager villager, BuilderBag bag, ItemStack seed, BlockPos spot) {
		if (!(seed.getItem() instanceof BlockItem item) || !bag.has(seed.getItem(), 1)) {
			return false;
		}
		BlockPos ground = spot.below();
		BlockPlaceContext context = new BlockPlaceContext(level, null, InteractionHand.MAIN_HAND, seed.copyWithCount(1),
			new BlockHitResult(Vec3.atCenterOf(ground).add(0, 0.5, 0), Direction.UP, ground, false));
		if (!item.place(context).consumesAction()) {
			return false;
		}
		bag.remove(seed.getItem(), 1);
		level.playSound(null, spot, SoundEvents.SWEET_BERRY_BUSH_PLACE, SoundSource.BLOCKS, 0.8f, 1f);
		villager.setAttached(ModAttachments.SAPLINGS_PLANTED, villager.getAttachedOrElse(ModAttachments.SAPLINGS_PLANTED, 0) + 1);
		return true;
	}

	private Orchards() {
	}
}

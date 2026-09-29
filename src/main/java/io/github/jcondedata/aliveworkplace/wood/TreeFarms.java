package io.github.jcondedata.aliveworkplace.wood;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.work.AreaJobs;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Tree farms: a Field Marker given to a lumberjack marks ground they keep planted with saplings from the chests (in a
 * grid, far enough apart for the trees to grow; dark oak in 2 × 2 squares), and they fell the trees that grow there
 * even when it's further from the Chopping Block than they'd look on their own.
 */
public final class TreeFarms {
	/** Farthest a tree farm can be from the Chopping Block. */
	public static final int MAX_DISTANCE = 48;
	/** Blocks between saplings: 3 for one-block trunks, 4 for 2 × 2 ones. */
	static final int SPACING = 3;
	static final int SPACING_2X2 = 4;

	public static boolean isLumberjack(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.LUMBERJACK;
	}

	@Nullable
	public static BoundingBox farm(Villager villager) {
		FieldJob job = ModAttachments.TREE_FARM.get(villager);
		return job == null ? null : job.box();
	}

	/** Trees that only grow from four saplings in a square (dark oak; modded ones named like it aren't guessed). */
	static boolean needsFour(Block sapling) {
		return sapling == Blocks.DARK_OAK_SAPLING;
	}

	/** Player right-clicked a lumberjack while holding a Field Marker. */
	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack) {
		return AreaJobs.assign(player, villager, stack, ModAttachments.TREE_FARM, "tree_farm");
	}

	/** Gives the lumberjack the tree farm. Also used by tests. */
	public static void start(Villager villager, BoundingBox box) {
		ModAttachments.TREE_FARM.set(villager, new FieldJob(box));
	}

	/** Stops planting the farm: the marker goes to {@code player} (or the chests). The trees stay. */
	public static void release(ServerLevel level, Villager villager, @Nullable Player player) {
		AreaJobs.release(level, villager, player, ModAttachments.TREE_FARM);
	}

	public static void onDeath(ServerLevel level, Villager villager) {
		AreaJobs.onDeath(level, villager, ModAttachments.TREE_FARM);
	}

	public static void sendStatus(Player player, Villager villager) {
		BoundingBox box = farm(villager);
		if (box != null) {
			AreaJobs.sendStatus(player, villager, box, "tree_farm", Component.translatable("message.aliveworkplace.tree_farm.counts",
				ModAttachments.TREES_FELLED.getOrElse(villager, 0), ModAttachments.SAPLINGS_PLANTED.getOrElse(villager, 0)));
		}
	}

	// --- planting ------------------------------------------------------------------------------

	/** The sapling the lumberjack would plant next: the kind they carry most of. */
	@Nullable
	static Block saplingToPlant(BuilderBag bag) {
		Block best = null;
		int most = 0;
		for (ItemStack stack : bag.stacks()) {
			if (stack.is(ItemTags.SAPLINGS) && stack.getItem() instanceof BlockItem item) {
				int count = bag.count(stack.getItem());
				int needed = needsFour(item.getBlock()) ? 4 : 1;
				if (count >= needed && count > most) {
					most = count;
					best = item.getBlock();
				}
			}
		}
		return best;
	}

	/**
	 * The next empty spot in the farm for {@code sapling}: the ground in a grid (3 apart, or 4 for 2 × 2 trees whose
	 * square must be free), with dirt or grass under it and nothing growing there yet. Empty if the farm is full.
	 */
	static Optional<BlockPos> nextSpot(ServerLevel level, BoundingBox farm, Block sapling, BlockPos near) {
		boolean four = needsFour(sapling);
		int spacing = four ? SPACING_2X2 : SPACING;
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int x = farm.minX(); x <= farm.maxX() - (four ? 1 : 0); x += spacing) {
			for (int z = farm.minZ(); z <= farm.maxZ() - (four ? 1 : 0); z += spacing) {
				BlockPos spot = ground(level, farm, x, z);
				if (spot == null || !free(level, spot, sapling) || four && !(free(level, spot.east(), sapling)
					&& free(level, spot.south(), sapling) && free(level, spot.east().south(), sapling))) {
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

	/** Where a sapling would stand in this column of the farm: the highest spot with dirt or grass under it. */
	@Nullable
	private static BlockPos ground(ServerLevel level, BoundingBox farm, int x, int z) {
		for (int y = farm.maxY() + 3; y >= farm.minY() - 2; y--) {
			BlockPos p = new BlockPos(x, y, z);
			if (level.getBlockState(p.below()).is(BlockTags.DIRT)) {
				return p;
			}
		}
		return null;
	}

	private static boolean free(ServerLevel level, BlockPos spot, Block sapling) {
		BlockState at = level.getBlockState(spot);
		return (at.isAir() || at.canBeReplaced() && at.getFluidState().isEmpty()) && sapling.defaultBlockState().canSurvive(level, spot)
			&& level.getBlockState(spot.above()).isAir();
	}

	/** Plants {@code sapling} at {@code spot} (and the other three of a 2 × 2 square), taking them from the bag. */
	static boolean plant(ServerLevel level, Villager villager, BuilderBag bag, Block sapling, BlockPos spot) {
		Item item = sapling.asItem();
		BlockPos[] square = needsFour(sapling) ? new BlockPos[]{spot, spot.east(), spot.south(), spot.east().south()} : new BlockPos[]{spot};
		if (!bag.has(item, square.length)) {
			return false;
		}
		for (BlockPos p : square) {
			level.setBlockAndUpdate(p, sapling.defaultBlockState());
		}
		bag.remove(item, square.length);
		level.playSound(null, spot, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.8f, 1f);
		ModAttachments.SAPLINGS_PLANTED.set(villager, ModAttachments.SAPLINGS_PLANTED.getOrElse(villager, 0) + square.length);
		return true;
	}

	private TreeFarms() {
	}
}

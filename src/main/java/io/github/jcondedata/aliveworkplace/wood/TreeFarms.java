package io.github.jcondedata.aliveworkplace.wood;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
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
		FieldJob job = villager.getAttached(ModAttachments.TREE_FARM);
		return job == null ? null : job.box();
	}

	/** Trees that only grow from four saplings in a square (dark oak; modded ones named like it aren't guessed). */
	static boolean needsFour(Block sapling) {
		return sapling == Blocks.DARK_OAK_SAPLING;
	}

	/** Player right-clicked a lumberjack while holding a Field Marker. */
	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack) {
		ServerLevel level = player.serverLevel();
		if (!Friends.mayCommand(player, villager)) {
			Employer employer = villager.getAttached(ModAttachments.BUILDER_EMPLOYER);
			tell(player, Component.translatable("message.aliveworkplace.not_your_builder", villager.getDisplayName(),
				employer != null ? employer.name() : "?", player.getGameProfile().getName()), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		FieldData data = FieldMarkerItem.data(stack);
		Optional<BoundingBox> area = data.area();
		if (area.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.field.not_marked"), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		if (!data.dimension().get().equals(level.dimension().location())) {
			tell(player, Component.translatable("message.aliveworkplace.assign.wrong_dimension"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		Optional<BlockPos> block = Builders.benchPos(villager);
		if (block.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.tree_farm.no_block"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		BoundingBox box = area.get();
		double distance = Math.sqrt(box.getCenter().distSqr(block.get()));
		if (distance > MAX_DISTANCE) {
			tell(player, Component.translatable("message.aliveworkplace.assign.too_far", (int) distance, MAX_DISTANCE), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		FieldJob old = villager.getAttached(ModAttachments.TREE_FARM);
		if (old != null && !player.getAbilities().instabuild) {
			if (!player.getInventory().add(markerFor(level, old))) {
				player.drop(markerFor(level, old), false);
			}
		}
		Friends.hire(player, villager);
		start(villager, box);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		tell(player, Component.translatable("message.aliveworkplace.tree_farm.started", villager.getDisplayName(), box.getXSpan(), box.getZSpan(),
			SupplyContainers.RADIUS), ChatFormatting.GREEN);
		return InteractionResult.SUCCESS;
	}

	/** Gives the lumberjack the tree farm. Also used by tests. */
	public static void start(Villager villager, BoundingBox box) {
		villager.setAttached(ModAttachments.TREE_FARM, new FieldJob(box));
	}

	/** Stops planting the farm: the marker goes to {@code player} (or the chests). The trees stay. */
	public static void release(ServerLevel level, Villager villager, @Nullable Player player) {
		FieldJob job = villager.getAttached(ModAttachments.TREE_FARM);
		if (job == null) {
			return;
		}
		ItemStack marker = markerFor(level, job);
		if (player == null || !player.getInventory().add(marker)) {
			BlockPos block = Builders.benchPos(villager).orElse(villager.blockPosition());
			ItemStack rest = SupplyContainers.insert(level, SupplyContainers.find(level, block, null), marker);
			if (!rest.isEmpty()) {
				Block.popResource(level, block.above(), rest);
			}
		}
		villager.removeAttached(ModAttachments.TREE_FARM);
	}

	public static void onDeath(ServerLevel level, Villager villager) {
		FieldJob job = villager.getAttached(ModAttachments.TREE_FARM);
		if (job != null) {
			Block.popResource(level, villager.blockPosition(), markerFor(level, job));
		}
	}

	public static void sendStatus(Player player, Villager villager) {
		BoundingBox box = farm(villager);
		if (box == null) {
			return;
		}
		MutableComponent text = Component.empty();
		text.append(Component.translatable("message.aliveworkplace.tree_farm.header", villager.getDisplayName(), box.getXSpan(), box.getZSpan(),
			box.minX(), box.minY(), box.minZ()).withStyle(ChatFormatting.GOLD));
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.tree_farm.counts", villager.getAttachedOrElse(ModAttachments.TREES_FELLED, 0),
			villager.getAttachedOrElse(ModAttachments.SAPLINGS_PLANTED, 0)).withStyle(ChatFormatting.GRAY));
		text.append(Component.literal("\n  "));
		text.append(BuilderLevels.describe(villager).copy().withStyle(ChatFormatting.DARK_AQUA));
		String stop = "/workplace cancel " + villager.getUUID();
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.tree_farm.stop").withStyle(style -> style
			.withColor(ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, stop))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.tree_farm.stop_hover")))));
		player.sendSystemMessage(text);
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
		villager.setAttached(ModAttachments.SAPLINGS_PLANTED, villager.getAttachedOrElse(ModAttachments.SAPLINGS_PLANTED, 0) + square.length);
		return true;
	}

	static ItemStack markerFor(ServerLevel level, FieldJob job) {
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		BoundingBox box = job.box();
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(level.dimension().location()),
			Optional.of(new BlockPos(box.minX(), box.minY(), box.minZ())), Optional.of(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))));
		return marker;
	}

	private static void tell(Player player, Component message, ChatFormatting color) {
		player.sendSystemMessage(message.copy().withStyle(color));
	}

	private TreeFarms() {
	}
}

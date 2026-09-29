package io.github.jcondedata.aliveworkplace.farm;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.mc.Rules;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Giving vanilla Farmers a field to look after: hand-over, status, stopping. While a farmer has a field
 * their vanilla routine is paused (see {@link #vanillaMayRun}) except when the field needs nothing.
 */
public final class Fields {
	/** A field must be within this many blocks of the farmer's composter. */
	public static final int MAX_DISTANCE = 48;

	public static void init() {
		// Saved farmers load their field after the brain is built: give them the longer shift now.
		Platform.get().onEntityLoad((entity, level) -> {
			if (entity instanceof Villager villager && ModAttachments.FARM_FIELD.has(villager) && isFarmer(villager)
				&& villager.getBrain().getSchedule() != ModVillagers.BUILDER_SCHEDULE) {
				villager.refreshBrain(level);
			}
		});
	}

	public static boolean isFarmer(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.FARMER;
	}

	public static boolean hasField(Villager villager) {
		return ModAttachments.FARM_FIELD.has(villager);
	}

	/** Vanilla farmer behaviour runs when there is no field, or the field needs nothing right now. */
	public static boolean vanillaMayRun(Villager villager) {
		return !hasField(villager) || FieldWork.isResting(villager);
	}

	/** Player right-clicked a farmer while holding a Field Marker. */
	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack) {
		ServerLevel level = Players.level(player);
		if (!Friends.mayCommand(player, villager)) {
			Employer employer = ModAttachments.BUILDER_EMPLOYER.get(villager);
			tell(player, Component.translatable("message.aliveworkplace.not_your_builder", villager.getDisplayName(),
				employer != null ? employer.name() : "?", player.getGameProfile().getName()), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		FieldData data = FieldMarkerItem.data(stack);
		Optional<BoundingBox> area = data.area();
		boolean adopted = false;
		if (area.isEmpty() && data.first().isEmpty()) {
			// A blank marker: the farmer takes on the farm by their composter (a village's own field).
			Optional<BlockPos> composter = Builders.benchPos(villager);
			if (composter.isEmpty()) {
				tell(player, Component.translatable("message.aliveworkplace.field.no_composter"), ChatFormatting.RED);
				return InteractionResult.CONSUME;
			}
			area = farmNear(level, composter.get());
			if (area.isEmpty()) {
				tell(player, Component.translatable("message.aliveworkplace.field.no_farm", FARM_SEARCH), ChatFormatting.YELLOW);
				return InteractionResult.CONSUME;
			}
			data = new FieldData(Optional.of(Ids.of(level.dimension())), Optional.empty(), Optional.empty());
			adopted = true;
		}
		if (area.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.field.not_marked"), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		if (!data.dimension().get().equals(Ids.of(level.dimension()))) {
			tell(player, Component.translatable("message.aliveworkplace.assign.wrong_dimension"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		Optional<BlockPos> composter = Builders.benchPos(villager);
		if (composter.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace.field.no_composter"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		BoundingBox box = area.get();
		double distance = Math.sqrt(box.getCenter().distSqr(composter.get()));
		if (distance > MAX_DISTANCE) {
			tell(player, Component.translatable("message.aliveworkplace.assign.too_far", (int) distance, MAX_DISTANCE), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		FieldJob old = ModAttachments.FARM_FIELD.get(villager);
		if (old != null && !old.adopted() && !player.getAbilities().instabuild) {
			give(player, markerFor(level, old)); // swapping fields: the old marker comes back
		}
		Friends.hire(player, villager);
		ModAttachments.NO_AUTO_FARM.remove(villager);
		start(level, villager, box);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		tell(player, Component.translatable(adopted ? "message.aliveworkplace.field.adopted" : "message.aliveworkplace.field.started",
			villager.getDisplayName(), box.getXSpan(), box.getZSpan(), SupplyContainers.RADIUS), ChatFormatting.GREEN);
		return InteractionResult.SUCCESS;
	}

	/** How far from the composter a blank marker looks for the farm. */
	public static final int FARM_SEARCH = 16;

	/**
	 * The farm by a composter, for a blank Field Marker: the farmland nearest to it (within {@link #FARM_SEARCH} blocks)
	 * and all the farmland joined to that, across the water channels between the rows too, kept within
	 * {@link FieldData#MAX_SIDE} a side. Empty if there's no farmland near.
	 */
	public static Optional<BoundingBox> farmNear(ServerLevel level, BlockPos composter) {
		BlockPos start = null;
		for (BlockPos p : BlockPos.betweenClosed(composter.offset(-FARM_SEARCH, -4, -FARM_SEARCH), composter.offset(FARM_SEARCH, 4, FARM_SEARCH))) {
			if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.FarmBlock
				&& (start == null || p.distSqr(composter) < start.distSqr(composter))) {
				start = p.immutable();
			}
		}
		if (start == null) {
			return Optional.empty();
		}
		int half = FieldData.MAX_SIDE / 2;
		BoundingBox limit = new BoundingBox(start.getX() - half + 1, start.getY() - 2, start.getZ() - half + 1,
			start.getX() + half, start.getY() + 2, start.getZ() + half);
		java.util.Set<BlockPos> seen = new java.util.HashSet<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		seen.add(start);
		queue.add(start);
		int minX = start.getX(), minY = start.getY(), minZ = start.getZ(), maxX = minX, maxY = minY, maxZ = minZ;
		while (!queue.isEmpty() && seen.size() < 4096) {
			BlockPos p = queue.poll();
			if (level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.FarmBlock) {
				minX = Math.min(minX, p.getX());
				minY = Math.min(minY, p.getY());
				minZ = Math.min(minZ, p.getZ());
				maxX = Math.max(maxX, p.getX());
				maxY = Math.max(maxY, p.getY());
				maxZ = Math.max(maxZ, p.getZ());
			}
			for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				for (int dy = -1; dy <= 1; dy++) {
					BlockPos n = p.relative(d).above(dy);
					if (!limit.isInside(n) || seen.contains(n)) {
						continue;
					}
					net.minecraft.world.level.block.state.BlockState st = level.getBlockState(n);
					if (st.getBlock() instanceof net.minecraft.world.level.block.FarmBlock || dy == 0 && st.getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
						seen.add(n);
						queue.add(n);
					}
				}
			}
		}
		return Optional.of(new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ));
	}

	/** How often (ticks) a village farmer without a field looks at the farm by their composter. */
	public static final int ADOPT_EVERY = 200;

	/**
	 * A village farmer with no field takes on the farm by their composter by themselves (see {@link #farmNear}) once
	 * there's a chest near the composter for the harvest — unless the gamerule is off or a player stopped them before.
	 * Returns true when they did.
	 */
	static boolean adoptOwnFarm(ServerLevel level, Villager villager) {
		if (!isFarmer(villager) || hasField(villager) || ModAttachments.NO_AUTO_FARM.getOrElse(villager, false)
			|| !Rules.on(level, io.github.jcondedata.aliveworkplace.registry.ModGameRules.VILLAGE_FARMS)) {
			return false;
		}
		Optional<BlockPos> composter = Builders.benchPos(villager);
		if (composter.isEmpty() || SupplyContainers.find(level, composter.get(), null).isEmpty()) {
			return false;
		}
		Optional<BoundingBox> farm = farmNear(level, composter.get());
		if (farm.isEmpty()) {
			return false;
		}
		ModAttachments.FARM_FIELD.set(villager, new FieldJob(farm.get(), true));
		// The longer shift of a farmer with a field (the brain itself is left alone: this runs while it ticks).
		villager.getBrain().setSchedule(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER_SCHEDULE);
		return true;
	}

	/** Gives the farmer the field. Also used by tests. */
	public static void start(ServerLevel level, Villager villager, BoundingBox box) {
		ModAttachments.FARM_FIELD.set(villager, new FieldJob(box));
		villager.refreshBrain(level);
	}

	/** Stops tending the field: the bag and hoe go to the chests, the marker to {@code player} (or the chests). */
	public static void release(ServerLevel level, Villager villager, Player player) {
		FieldJob job = ModAttachments.FARM_FIELD.get(villager);
		if (job == null) {
			return;
		}
		BlockPos composter = Builders.benchPos(villager).orElse(villager.blockPosition());
		List<BlockPos> supplies = SupplyContainers.find(level, composter, null);
		BuilderBag bag = ModAttachments.BUILDER_BAG.getOrCreate(villager);
		for (ItemStack stack : bag.takeAll()) {
			store(level, supplies, composter, stack);
		}
		ItemStack tool = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!tool.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			store(level, supplies, composter, tool);
		}
		if (job.adopted()) {
			ModAttachments.NO_AUTO_FARM.set(villager, true); // stopped for good: they don't take it on again
		} else {
			ItemStack marker = markerFor(level, job);
			if (player == null || !player.getInventory().add(marker)) {
				store(level, supplies, composter, marker);
			}
		}
		ModAttachments.FARM_FIELD.remove(villager);
		villager.refreshBrain(level);
	}

	public static void onDeath(ServerLevel level, Villager villager) {
		FieldJob job = ModAttachments.FARM_FIELD.get(villager);
		if (job != null && !job.adopted()) {
			store(level, List.of(), villager.blockPosition(), markerFor(level, job));
		}
	}

	public static void sendStatus(Player player, Villager villager) {
		FieldJob job = ModAttachments.FARM_FIELD.get(villager);
		if (job == null) {
			return;
		}
		BoundingBox box = job.box();
		MutableComponent text = Component.empty();
		text.append(Component.translatable("message.aliveworkplace.field.header", villager.getDisplayName(), box.getXSpan(), box.getZSpan(),
			box.minX(), box.minY(), box.minZ()).withStyle(ChatFormatting.GOLD));
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.field.state." + (FieldWork.isResting(villager) ? "resting" : "tending"))
			.withStyle(ChatFormatting.GRAY));
		text.append(Component.literal(" · ").withStyle(ChatFormatting.DARK_GRAY));
		text.append(Component.translatable("message.aliveworkplace.field.counts", ModAttachments.FARM_HARVESTED.getOrElse(villager, 0))
			.withStyle(ChatFormatting.GRAY));
		text.append(Component.literal("\n  "));
		text.append(BuilderLevels.describe(villager).copy().withStyle(ChatFormatting.DARK_AQUA));
		String stop = "/workplace cancel " + villager.getUUID();
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.field.stop").withStyle(style -> style
			.withColor(ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, stop))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.field.stop_hover")))));
		Chat.system(player, text);
	}

	private static ItemStack markerFor(ServerLevel level, FieldJob job) {
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		BoundingBox box = job.box();
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())),
			Optional.of(new BlockPos(box.minX(), box.minY(), box.minZ())), Optional.of(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))));
		return marker;
	}

	private static void give(Player player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	private static void store(ServerLevel level, List<BlockPos> supplies, BlockPos near, ItemStack stack) {
		ItemStack rest = SupplyContainers.insert(level, supplies, stack);
		if (!rest.isEmpty()) {
			ItemEntity item = new ItemEntity(level, near.getX() + 0.5, near.getY() + 1.1, near.getZ() + 0.5, rest);
			item.setDefaultPickUpDelay();
			level.addFreshEntity(item);
		}
	}

	private static void tell(Player player, Component message, ChatFormatting color) {
		Chat.system(player, message.copy().withStyle(color));
	}

	private Fields() {
	}
}

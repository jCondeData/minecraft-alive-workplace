package io.github.jcondedata.aliveworkplace.farm;

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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
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
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager && villager.hasAttached(ModAttachments.FARM_FIELD) && isFarmer(villager)
				&& villager.getBrain().getSchedule() != ModVillagers.BUILDER_SCHEDULE) {
				villager.refreshBrain(level);
			}
		});
	}

	public static boolean isFarmer(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == VillagerProfession.FARMER;
	}

	public static boolean hasField(Villager villager) {
		return villager.hasAttached(ModAttachments.FARM_FIELD);
	}

	/** Vanilla farmer behaviour runs when there is no field, or the field needs nothing right now. */
	public static boolean vanillaMayRun(Villager villager) {
		return !hasField(villager) || FieldWork.isResting(villager);
	}

	/** Player right-clicked a farmer while holding a Field Marker. */
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
		FieldJob old = villager.getAttached(ModAttachments.FARM_FIELD);
		if (old != null && !player.getAbilities().instabuild) {
			give(player, markerFor(level, old)); // swapping fields: the old marker comes back
		}
		Friends.hire(player, villager);
		start(level, villager, box);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		tell(player, Component.translatable("message.aliveworkplace.field.started", villager.getDisplayName(), box.getXSpan(), box.getZSpan(),
			SupplyContainers.RADIUS), ChatFormatting.GREEN);
		return InteractionResult.SUCCESS;
	}

	/** Gives the farmer the field. Also used by tests. */
	public static void start(ServerLevel level, Villager villager, BoundingBox box) {
		villager.setAttached(ModAttachments.FARM_FIELD, new FieldJob(box));
		villager.refreshBrain(level);
	}

	/** Stops tending the field: the bag and hoe go to the chests, the marker to {@code player} (or the chests). */
	public static void release(ServerLevel level, Villager villager, Player player) {
		FieldJob job = villager.getAttached(ModAttachments.FARM_FIELD);
		if (job == null) {
			return;
		}
		BlockPos composter = Builders.benchPos(villager).orElse(villager.blockPosition());
		List<BlockPos> supplies = SupplyContainers.find(level, composter, null);
		BuilderBag bag = villager.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		for (ItemStack stack : bag.takeAll()) {
			store(level, supplies, composter, stack);
		}
		ItemStack tool = villager.getItemBySlot(EquipmentSlot.MAINHAND);
		if (!tool.isEmpty()) {
			villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			store(level, supplies, composter, tool);
		}
		ItemStack marker = markerFor(level, job);
		if (player == null || !player.getInventory().add(marker)) {
			store(level, supplies, composter, marker);
		}
		villager.removeAttached(ModAttachments.FARM_FIELD);
		villager.refreshBrain(level);
	}

	public static void onDeath(ServerLevel level, Villager villager) {
		FieldJob job = villager.getAttached(ModAttachments.FARM_FIELD);
		if (job != null) {
			store(level, List.of(), villager.blockPosition(), markerFor(level, job));
		}
	}

	public static void sendStatus(Player player, Villager villager) {
		FieldJob job = villager.getAttached(ModAttachments.FARM_FIELD);
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
		text.append(Component.translatable("message.aliveworkplace.field.counts", villager.getAttachedOrElse(ModAttachments.FARM_HARVESTED, 0))
			.withStyle(ChatFormatting.GRAY));
		text.append(Component.literal("\n  "));
		text.append(BuilderLevels.describe(villager).copy().withStyle(ChatFormatting.DARK_AQUA));
		String stop = "/workplace cancel " + villager.getUUID();
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace.field.stop").withStyle(style -> style
			.withColor(ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, stop))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace.field.stop_hover")))));
		player.sendSystemMessage(text);
	}

	private static ItemStack markerFor(ServerLevel level, FieldJob job) {
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		BoundingBox box = job.box();
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(level.dimension().location()),
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
		player.sendSystemMessage(message.copy().withStyle(color));
	}

	private Fields() {
	}
}

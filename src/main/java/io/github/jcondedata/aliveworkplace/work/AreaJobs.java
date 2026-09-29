package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.farm.FieldMarkerItem;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.Optional;
import io.github.jcondedata.aliveworkplace.platform.Attachment;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * An area a worker looks after, marked with a Field Marker and handed to them (a lumberjack's tree farm, an orchard
 * keeper's orchard): giving it, taking it back, the status line and the marker that comes back. The texts come from
 * lang keys under {@code message.aliveworkplace.<kind>.}: {@code no_block}, {@code started}, {@code header},
 * {@code stop}, {@code stop_hover}.
 */
public final class AreaJobs {
	/** Farthest an area can be from the worker's workstation. */
	public static final int MAX_DISTANCE = 48;

	/** Player right-clicked the worker while holding a Field Marker: the marked area becomes theirs to look after. */
	public static InteractionResult assign(ServerPlayer player, Villager villager, ItemStack stack, Attachment<FieldJob> type, String kind) {
		ServerLevel level = Players.level(player);
		if (!Friends.mayCommand(player, villager)) {
			Employer employer = ModAttachments.BUILDER_EMPLOYER.get(villager);
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
		if (!data.dimension().get().equals(Ids.of(level.dimension()))) {
			tell(player, Component.translatable("message.aliveworkplace.assign.wrong_dimension"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		Optional<BlockPos> station = Builders.benchPos(villager);
		if (station.isEmpty()) {
			tell(player, Component.translatable("message.aliveworkplace." + kind + ".no_block"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		BoundingBox box = area.get();
		double distance = Math.sqrt(box.getCenter().distSqr(station.get()));
		if (distance > MAX_DISTANCE) {
			tell(player, Component.translatable("message.aliveworkplace.assign.too_far", (int) distance, MAX_DISTANCE), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		FieldJob old = type.get(villager);
		if (old != null && !player.getAbilities().instabuild) {
			ItemStack back = markerFor(level, old);
			if (!player.getInventory().add(back)) {
				player.drop(back, false);
			}
		}
		Friends.hire(player, villager);
		type.set(villager, new FieldJob(box));
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		level.playSound(null, villager, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		tell(player, Component.translatable("message.aliveworkplace." + kind + ".started", villager.getDisplayName(), box.getXSpan(), box.getZSpan(),
			SupplyContainers.RADIUS), ChatFormatting.GREEN);
		return InteractionResult.SUCCESS;
	}

	/** Takes the area back: the marker goes to {@code player} (or the chests by the workstation). What grew there stays. */
	public static void release(ServerLevel level, Villager villager, @Nullable Player player, Attachment<FieldJob> type) {
		FieldJob job = type.get(villager);
		if (job == null) {
			return;
		}
		ItemStack marker = markerFor(level, job);
		if (player == null || !player.getInventory().add(marker)) {
			BlockPos station = Builders.benchPos(villager).orElse(villager.blockPosition());
			ItemStack rest = SupplyContainers.insert(level, SupplyContainers.find(level, station, null), marker);
			if (!rest.isEmpty()) {
				Block.popResource(level, station.above(), rest);
			}
		}
		type.remove(villager);
	}

	/** The marker drops where the worker died. */
	public static void onDeath(ServerLevel level, Villager villager, Attachment<FieldJob> type) {
		FieldJob job = type.get(villager);
		if (job != null) {
			Block.popResource(level, villager.blockPosition(), markerFor(level, job));
		}
	}

	/** The area, what the worker has done ({@code counts}), their level and a button to stop. */
	public static void sendStatus(Player player, Villager villager, BoundingBox box, String kind, Component counts) {
		MutableComponent text = Component.empty();
		text.append(Component.translatable("message.aliveworkplace." + kind + ".header", villager.getDisplayName(), box.getXSpan(), box.getZSpan(),
			box.minX(), box.minY(), box.minZ()).withStyle(ChatFormatting.GOLD));
		text.append(Component.literal("\n  "));
		text.append(counts.copy().withStyle(ChatFormatting.GRAY));
		text.append(Component.literal("\n  "));
		text.append(BuilderLevels.describe(villager).copy().withStyle(ChatFormatting.DARK_AQUA));
		String stop = "/workplace cancel " + villager.getUUID();
		text.append(Component.literal("\n  "));
		text.append(Component.translatable("message.aliveworkplace." + kind + ".stop").withStyle(style -> style
			.withColor(ChatFormatting.RED).withUnderlined(true)
			.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, stop))
			.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("message.aliveworkplace." + kind + ".stop_hover")))));
		Chat.system(player, text);
	}

	public static ItemStack markerFor(ServerLevel level, FieldJob job) {
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		BoundingBox box = job.box();
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())),
			Optional.of(new BlockPos(box.minX(), box.minY(), box.minZ())), Optional.of(new BlockPos(box.maxX(), box.maxY(), box.maxZ()))));
		return marker;
	}

	private static void tell(Player player, Component message, ChatFormatting color) {
		Chat.system(player, message.copy().withStyle(color));
	}

	private AreaJobs() {
	}
}

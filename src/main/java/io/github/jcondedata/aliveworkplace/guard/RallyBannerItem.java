package io.github.jcondedata.aliveworkplace.guard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A Rally Banner: sneak-right-click guards with it to enlist them (again to let one go), then right-click the air to
 * raise it — while it's raised and anywhere in your inventory, the enlisted guards leave their posts, follow you and fight
 * whatever goes for you or you go for (see {@link Escorts}). Right-click again to lower it: they go back to their posts.
 */
public class RallyBannerItem extends Item {
	public static final int MAX_GUARDS = 12;

	/** The guards a banner calls, and whether it's raised. */
	public record Rally(List<UUID> guards, boolean raised) {
		public static final Rally EMPTY = new Rally(List.of(), false);
		public static final Codec<Rally> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.listOf().optionalFieldOf("guards", List.of()).forGetter(Rally::guards),
			Codec.BOOL.optionalFieldOf("raised", false).forGetter(Rally::raised)
		).apply(i, Rally::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Rally> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
	}

	public RallyBannerItem(Properties properties) {
		super(properties.component(ModComponents.RALLY, Rally.EMPTY));
	}

	public static Rally rally(ItemStack stack) {
		return stack.getOrDefault(ModComponents.RALLY, Rally.EMPTY);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return rally(stack).raised() || super.isFoil(stack);
	}

	/** Right-click the air: raise the banner, or lower it. */
	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResultHolder.success(stack);
		}
		Rally rally = rally(stack);
		if (rally.guards().isEmpty()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.rally.no_guards").withStyle(ChatFormatting.YELLOW));
			return InteractionResultHolder.fail(stack);
		}
		boolean raised = !rally.raised();
		stack.set(ModComponents.RALLY, new Rally(rally.guards(), raised));
		if (raised) {
			level.playSound(null, player.blockPosition(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 0.5f, 1.3f);
			Chat.actionBar(player, io.github.jcondedata.aliveworkplace.work.Words.counted("message.aliveworkplace.rally.raised", rally.guards().size(), rally.guards().size()).withStyle(ChatFormatting.GOLD));
		} else {
			level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 0.8f);
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.rally.lowered").withStyle(ChatFormatting.GRAY));
		}
		return InteractionResultHolder.success(stack);
	}

	/** Enlists {@code guard} under the banner, or lets them go if they're on it already. */
	public static InteractionResult enlist(ServerPlayer player, Villager guard, ItemStack banner) {
		if (!Friends.mayCommand(player, guard)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.rally.not_yours", guard.getDisplayName()).withStyle(ChatFormatting.RED));
			return InteractionResult.FAIL;
		}
		Rally rally = rally(banner);
		List<UUID> guards = new ArrayList<>(rally.guards());
		if (guards.remove(guard.getUUID())) {
			banner.set(ModComponents.RALLY, new Rally(List.copyOf(guards), rally.raised() && !guards.isEmpty()));
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.rally.released", guard.getDisplayName()));
			return InteractionResult.SUCCESS;
		}
		if (guards.size() >= MAX_GUARDS) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.rally.full", MAX_GUARDS).withStyle(ChatFormatting.YELLOW));
			return InteractionResult.FAIL;
		}
		guards.add(guard.getUUID());
		banner.set(ModComponents.RALLY, new Rally(List.copyOf(guards), rally.raised()));
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.rally.enlisted", guard.getDisplayName(), guards.size())
			.withStyle(ChatFormatting.GREEN));
		guard.playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Rally rally = rally(stack);
		tooltip.add(Component.translatable("tooltip.aliveworkplace.rally.guards", rally.guards().size(), MAX_GUARDS).withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable(rally.raised() ? "tooltip.aliveworkplace.rally.raised" : "tooltip.aliveworkplace.rally.lowered")
			.withStyle(rally.raised() ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		tooltip.add(Component.translatable("tooltip.aliveworkplace.rally.how").withStyle(ChatFormatting.DARK_GRAY));
	}
}

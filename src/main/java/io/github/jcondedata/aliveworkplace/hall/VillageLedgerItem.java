package io.github.jcondedata.aliveworkplace.hall;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * A Village Ledger: right-click a Village Hall with it to bind it to that village, then right-click the air anywhere
 * in the same dimension to open the hall's screen — its people, requests, builds, quests and chronicle — without
 * walking back to the hall (the hall's chunk has to be loaded, so within a few hundred blocks).
 */
public class VillageLedgerItem extends Item {
	/** The hall a ledger is bound to, and the village's name then (for the tooltip). */
	public record Ledger(GlobalPos hall, Component name) {
		public static final Codec<Ledger> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("hall").forGetter(Ledger::hall),
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Ledger::name)
		).apply(i, Ledger::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Ledger> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
	}

	public VillageLedgerItem(Properties properties) {
		super(properties);
	}

	/** Binds {@code stack} to the hall at {@code hall} (called by the hall block when it's right-clicked with a ledger). */
	public static void bind(ServerLevel level, ServerPlayer player, ItemStack stack, net.minecraft.core.BlockPos hall) {
		Component name = VillageHalls.name(level, hall);
		stack.set(ModComponents.LEDGER, new Ledger(GlobalPos.of(level.dimension(), hall.immutable()), name));
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.ledger.bound", name).withStyle(ChatFormatting.GREEN));
		level.playSound(null, hall, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.success(stack);
		}
		Ledger ledger = stack.get(ModComponents.LEDGER);
		if (ledger == null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.ledger.unbound").withStyle(ChatFormatting.YELLOW));
			return InteractionResultHolder.fail(stack);
		}
		if (!ledger.hall().dimension().equals(level.dimension()) || !server.isLoaded(ledger.hall().pos())
			|| !server.getBlockState(ledger.hall().pos()).is(ModBlocks.VILLAGE_HALL)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.ledger.too_far", ledger.name()).withStyle(ChatFormatting.YELLOW));
			return InteractionResultHolder.fail(stack);
		}
		VillageHallScreen.openRemote(serverPlayer, ledger.hall().pos());
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1.1f);
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Ledger ledger = stack.get(ModComponents.LEDGER);
		tooltip.add(ledger == null ? Component.translatable("tooltip.aliveworkplace.ledger.unbound").withStyle(ChatFormatting.GRAY)
			: Component.translatable("tooltip.aliveworkplace.ledger.bound", ledger.name()).withStyle(ChatFormatting.AQUA));
	}
}

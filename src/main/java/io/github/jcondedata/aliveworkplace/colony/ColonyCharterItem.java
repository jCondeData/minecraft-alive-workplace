package io.github.jcondedata.aliveworkplace.colony;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * The Colony Charter (ROADMAP 33.8): bought on a City's Colonies tab ({@link Colonies#buy}) and bound from the start
 * to that village's hall, as a Village Ledger is bound to its own. Right-clicked in the air it opens its map
 * ({@link Colonies#open}), where a click chooses the spot the colony goes; right-clicked on the ground it chooses the
 * spot where the player stands. The spot is kept on the charter and named in its tooltip ("612 blocks north-east of
 * Thornholm"); a charter renamed in an anvil names the colony ({@link #colonyName}).
 */
public class ColonyCharterItem extends Item {
	/** What a charter carries: its mother village's hall, the village's name when it was bought, and the spot once chosen. */
	public record Charter(GlobalPos hall, Component name, Optional<BlockPos> spot) {
		public static final Codec<Charter> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("hall").forGetter(Charter::hall),
			ComponentSerialization.CODEC.fieldOf("name").forGetter(Charter::name),
			BlockPos.CODEC.optionalFieldOf("spot").forGetter(Charter::spot)
		).apply(i, Charter::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Charter> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

		public Charter withSpot(BlockPos spot) {
			return new Charter(hall, name, Optional.of(spot.immutable()));
		}
	}

	public ColonyCharterItem(Properties properties) {
		super(properties);
	}

	/** A charter of the village whose hall is at {@code hall}, no spot chosen yet. */
	public static ItemStack of(ServerLevel level, BlockPos hall) {
		ItemStack stack = new ItemStack(ModItems.COLONY_CHARTER);
		stack.set(ModComponents.COLONY_CHARTER, new Charter(GlobalPos.of(level.dimension(), hall.immutable()), VillageHalls.name(level, hall), Optional.empty()));
		return stack;
	}

	/** The name the charter gives its colony: what it was renamed to in an anvil, or null (one is made up on arrival). */
	@Nullable
	public static Component colonyName(ItemStack stack) {
		return stack.get(DataComponents.CUSTOM_NAME);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
			Colonies.open(serverPlayer, hand);
		}
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (level.getBlockState(context.getClickedPos()).is(ModBlocks.VILLAGE_HALL)) {
			return InteractionResult.PASS; // the hall is where the settlers are sent from (Settlers.send, through the hall block), never a spot
		}
		if (level instanceof ServerLevel && context.getPlayer() instanceof ServerPlayer player) {
			Colonies.Chosen chosen = Colonies.choose(player, context.getItemInHand(), player.blockPosition());
			io.github.jcondedata.aliveworkplace.mc.Chat.actionBar(player, chosen.message());
		}
		return InteractionResult.sidedSuccess(level.isClientSide());
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.addAll(lines(stack));
	}

	/** The charter's tooltip lines under its name. */
	public static List<Component> lines(ItemStack stack) {
		Charter charter = stack.get(ModComponents.COLONY_CHARTER);
		if (charter == null) {
			return List.of(Component.translatable("tooltip.aliveworkplace.colony_charter.unbound").withStyle(ChatFormatting.GRAY));
		}
		List<Component> out = new java.util.ArrayList<>();
		out.add(Component.translatable("tooltip.aliveworkplace.colony_charter.bound", charter.name()).withStyle(ChatFormatting.AQUA));
		if (charter.spot().isPresent()) {
			out.add(Component.translatable("tooltip.aliveworkplace.colony_charter.spot", VillageHallScreen.where(charter.hall().pos(), charter.spot().get()),
				charter.name()).withStyle(ChatFormatting.RED));
		} else {
			out.add(Component.translatable("tooltip.aliveworkplace.colony_charter.no_spot").withStyle(ChatFormatting.GRAY));
			out.add(Component.translatable("tooltip.aliveworkplace.colony_charter.no_spot_ground").withStyle(ChatFormatting.GRAY));
		}
		Component named = colonyName(stack);
		out.add(named == null ? Component.translatable("tooltip.aliveworkplace.colony_charter.rename").withStyle(ChatFormatting.DARK_GRAY)
			: Component.translatable("tooltip.aliveworkplace.colony_charter.named", named.getString()).withStyle(ChatFormatting.GOLD));
		return out;
	}
}

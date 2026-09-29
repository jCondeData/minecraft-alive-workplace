package io.github.jcondedata.aliveworkplace.guard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A Patrol Map: right-click the ground at up to {@link #MAX_POINTS} places to mark a route (sneak-right-click the
 * ground to start over), then sneak-right-click a guard with it — by day they walk the route, point to point, instead of
 * wandering round their post (at night they stay by the post as always). The map keeps its route for the next guard;
 * sneak-right-clicking a guard with a blank map takes their route away.
 */
public class PatrolMapItem extends Item {
	public static final int MAX_POINTS = 8;
	/** How far from their post a guard walks a route. */
	public static final int MAX_DISTANCE = 64;

	/** The route marked on a map: the points, in order, in one dimension. */
	public record Route(Optional<ResourceLocation> dimension, List<BlockPos> points) {
		public static final Route EMPTY = new Route(Optional.empty(), List.of());
		public static final Codec<Route> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(Route::dimension),
			BlockPos.CODEC.listOf().optionalFieldOf("points", List.of()).forGetter(Route::points)
		).apply(i, Route::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Route> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);
	}

	public PatrolMapItem(Properties properties) {
		super(properties.component(ModComponents.PATROL, Route.EMPTY));
	}

	public static Route route(ItemStack stack) {
		return stack.getOrDefault(ModComponents.PATROL, Route.EMPTY);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (context.getLevel().isClientSide || player == null) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = context.getItemInHand();
		if (player.isShiftKeyDown()) {
			stack.set(ModComponents.PATROL, Route.EMPTY);
			player.displayClientMessage(Component.translatable("message.aliveworkplace.patrol.cleared"), true);
			return InteractionResult.SUCCESS;
		}
		Route route = route(stack);
		ResourceLocation dim = context.getLevel().dimension().location();
		List<BlockPos> points = route.dimension().map(dim::equals).orElse(true) ? new ArrayList<>(route.points()) : new ArrayList<>();
		if (points.size() >= MAX_POINTS) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.patrol.full", MAX_POINTS).withStyle(ChatFormatting.YELLOW), true);
			return InteractionResult.FAIL;
		}
		BlockPos point = context.getClickedPos().above();
		points.add(point);
		stack.set(ModComponents.PATROL, new Route(Optional.of(dim), List.copyOf(points)));
		player.displayClientMessage(Component.translatable("message.aliveworkplace.patrol.point", points.size(), point.getX(), point.getY(), point.getZ()), true);
		context.getLevel().playSound(null, point, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 0.6f, 1.2f);
		return InteractionResult.SUCCESS;
	}

	/** Hands the map's route to {@code guard} (or, with a blank map, takes theirs away). */
	public static InteractionResult giveTo(ServerPlayer player, Villager guard, ItemStack map) {
		Route route = route(map);
		if (route.points().isEmpty()) {
			guard.removeAttached(ModAttachments.PATROL_ROUTE);
			player.displayClientMessage(Component.translatable("message.aliveworkplace.patrol.taken", guard.getDisplayName()), true);
			return InteractionResult.SUCCESS;
		}
		if (!route.dimension().map(player.level().dimension().location()::equals).orElse(false)) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.patrol.elsewhere").withStyle(ChatFormatting.YELLOW), true);
			return InteractionResult.FAIL;
		}
		guard.setAttached(ModAttachments.PATROL_ROUTE, route.points());
		player.displayClientMessage(Component.translatable("message.aliveworkplace.patrol.given", guard.getDisplayName(), route.points().size())
			.withStyle(ChatFormatting.GREEN), true);
		guard.playSound(SoundEvents.VILLAGER_YES, 1f, 1f);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		Route route = route(stack);
		tooltip.add(Component.translatable("tooltip.aliveworkplace.patrol.points", route.points().size(), MAX_POINTS).withStyle(ChatFormatting.AQUA));
		tooltip.add(Component.translatable("tooltip.aliveworkplace.patrol.how").withStyle(ChatFormatting.GRAY));
	}
}

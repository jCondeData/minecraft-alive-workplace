package io.github.jcondedata.aliveworkplace.city;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageLedgerItem;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
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
 * The City Plan (ROADMAP 27.2): right-click a Village Hall to bind it to that village, as a Village Ledger binds; its
 * tooltip names the village. Right-clicked in the air it opens the plan screen (27.3) and says which zone you stand in.
 */
public class CityPlanItem extends Item {
	public CityPlanItem(Properties properties) {
		super(properties);
	}

	/** Binds {@code stack} to the hall at {@code hall} (called by the hall block when it's right-clicked with a plan). */
	public static void bind(ServerLevel level, ServerPlayer player, ItemStack stack, BlockPos hall) {
		Component name = VillageHalls.name(level, hall);
		stack.set(ModComponents.CITY_PLAN_HALL, new VillageLedgerItem.Ledger(GlobalPos.of(level.dimension(), hall.immutable()), name));
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.city_plan.bound", name).withStyle(ChatFormatting.GREEN));
		level.playSound(null, hall, SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResultHolder.success(stack);
		}
		VillageLedgerItem.Ledger bound = stack.get(ModComponents.CITY_PLAN_HALL);
		if (bound == null) {
			Chat.actionBar(serverPlayer, Component.translatable("message.aliveworkplace.city_plan.unbound").withStyle(ChatFormatting.YELLOW));
			return InteractionResultHolder.fail(stack);
		}
		BlockPos hall = bound.hall().pos();
		if (!bound.hall().dimension().equals(level.dimension()) || !server.isLoaded(hall) || !server.getBlockState(hall).is(ModBlocks.VILLAGE_HALL)
			|| !(server.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			Chat.actionBar(serverPlayer, Component.translatable("message.aliveworkplace.ledger.too_far", bound.name()).withStyle(ChatFormatting.YELLOW));
			return InteractionResultHolder.fail(stack);
		}
		Component village = VillageHalls.name(server, hall);
		Optional<CityPlan.Zone> zone = entity.plan().zoneAt(hall, player.blockPosition());
		Component message = zone.map(z -> Component.translatable("message.aliveworkplace.city_plan.in_zone", z.name(),
				CityZones.get(z.kind()).map(CityZones.Kind::title).orElse(Component.literal(z.kind())), village))
			.orElseGet(() -> Component.translatable("message.aliveworkplace.city_plan.no_zone", village, entity.plan().zones().size()));
		Chat.actionBar(serverPlayer, message.copy().withStyle(ChatFormatting.AQUA));
		CityPlans.open(serverPlayer, entity); // the plan screen (27.3)
		return InteractionResultHolder.success(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		VillageLedgerItem.Ledger bound = stack.get(ModComponents.CITY_PLAN_HALL);
		tooltip.add(bound == null ? Component.translatable("tooltip.aliveworkplace.city_plan.unbound").withStyle(ChatFormatting.GRAY)
			: Component.translatable("tooltip.aliveworkplace.city_plan.bound", bound.name()).withStyle(ChatFormatting.AQUA));
	}
}

package io.github.jcondedata.aliveworkplace.mail;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Courier routes for postmen: handing over Delivery Notes, listing and ending routes. */
public final class Postmen {
	public static final int MAX_ROUTES = 4;

	public static boolean isPostman(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.POSTMAN;
	}

	public static List<RouteData> routes(Villager villager) {
		return villager.getAttachedOrElse(ModAttachments.COURIER_ROUTES, List.of());
	}

	/** Player gave a postman a Delivery Note: add its route, or (a blank note) end all routes. */
	public static InteractionResult assign(ServerPlayer player, Villager postman, ItemStack note) {
		if (!Friends.mayCommand(player, postman)) {
			Employer employer = postman.getAttached(ModAttachments.BUILDER_EMPLOYER);
			tell(player, Component.translatable("message.aliveworkplace.not_your_builder", postman.getDisplayName(),
				employer != null ? employer.name() : "?", player.getGameProfile().getName()), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		RouteData route = DeliveryNoteItem.data(note);
		List<RouteData> routes = new ArrayList<>(routes(postman));
		if (!route.isComplete()) {
			if (routes.isEmpty()) {
				tell(player, Component.translatable("message.aliveworkplace.route.incomplete"), ChatFormatting.YELLOW);
				return InteractionResult.CONSUME;
			}
			for (RouteData old : routes) {
				ItemStack back = new ItemStack(ModItems.DELIVERY_NOTE);
				back.set(ModComponents.ROUTE, old);
				if (!player.getInventory().add(back)) {
					player.drop(back, false);
				}
			}
			postman.removeAttached(ModAttachments.COURIER_ROUTES);
			tell(player, Component.translatable("message.aliveworkplace.route.cleared", postman.getDisplayName(), routes.size()), ChatFormatting.GREEN);
			return InteractionResult.SUCCESS;
		}
		BlockPos desk = Builders.benchPos(postman).orElse(null);
		if (desk == null) {
			tell(player, Component.translatable("message.aliveworkplace.route.no_desk"), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		if (!route.from().get().closerThan(desk, PostOffice.ROUND) || !route.to().get().closerThan(desk, PostOffice.ROUND)) {
			tell(player, Component.translatable("message.aliveworkplace.route.too_far", PostOffice.ROUND), ChatFormatting.RED);
			return InteractionResult.CONSUME;
		}
		if (routes.size() >= MAX_ROUTES) {
			tell(player, Component.translatable("message.aliveworkplace.route.full", postman.getDisplayName(), MAX_ROUTES), ChatFormatting.YELLOW);
			return InteractionResult.CONSUME;
		}
		Friends.hire(player, postman);
		routes.add(route);
		postman.setAttached(ModAttachments.COURIER_ROUTES, List.copyOf(routes));
		if (!player.getAbilities().instabuild) {
			note.shrink(1);
		}
		postman.level().playSound(null, postman, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		tell(player, Component.translatable("message.aliveworkplace.route.added", postman.getDisplayName()), ChatFormatting.GREEN);
		return InteractionResult.SUCCESS;
	}

	public static void sendStatus(Player player, Villager postman) {
		List<RouteData> routes = routes(postman);
		MutableComponent text = Component.translatable("message.aliveworkplace.route.header", postman.getDisplayName(),
			postman.getAttachedOrElse(ModAttachments.MAIL_DELIVERED, 0), routes.size()).withStyle(ChatFormatting.GOLD);
		for (RouteData r : routes) {
			BlockPos f = r.from().orElse(BlockPos.ZERO);
			BlockPos t = r.to().orElse(BlockPos.ZERO);
			text.append(Component.literal("\n  "));
			text.append(Component.translatable("message.aliveworkplace.route.line", f.getX(), f.getY(), f.getZ(), t.getX(), t.getY(), t.getZ(),
				r.filter().isEmpty() ? Component.translatable("tooltip.aliveworkplace.route.everything")
					: Component.literal(r.filter().stream().map(i -> i.getDescription().getString()).reduce((a, b) -> a + ", " + b).orElse("")))
				.withStyle(ChatFormatting.GRAY));
		}
		if (!routes.isEmpty()) {
			text.append(Component.literal("\n  "));
			text.append(Component.translatable("message.aliveworkplace.route.how_to_end").withStyle(ChatFormatting.DARK_GRAY));
		}
		player.sendSystemMessage(text);
	}

	private static void tell(Player player, Component message, ChatFormatting color) {
		player.sendSystemMessage(message.copy().withStyle(color));
	}

	private Postmen() {
	}
}

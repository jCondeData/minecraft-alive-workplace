package io.github.jcondedata.aliveworkplace.smith;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * Orders for a Ball Smith: sneak-right-click them to pick which balls they make. With none picked they make whatever
 * their chests have the makings for (taking turns); with some picked, only those.
 */
public final class BallSmiths {
	/** First slot of the ball buttons (the top row explains). */
	public static final int FIRST_BALL_SLOT = 9;
	private static final int INFO_SLOT = 4;

	public static boolean isSmith(Villager villager) {
		return !villager.isBaby() && villager.getVillagerData().getProfession() == ModVillagers.BALL_SMITH;
	}

	/** The balls this smith has been asked for; empty means anything. */
	public static List<ResourceLocation> orders(Villager villager) {
		return ModAttachments.BALL_ORDERS.getOrElse(villager, List.of());
	}

	/** Whether the smith should make this ball. */
	public static boolean wants(Villager villager, ItemStack ball) {
		List<ResourceLocation> orders = orders(villager);
		return orders.isEmpty() || orders.contains(BuiltInRegistries.ITEM.getKey(ball.getItem()));
	}

	/** Switches making {@code ball} on or off. */
	public static void toggle(Villager villager, Item ball) {
		ResourceLocation id = BuiltInRegistries.ITEM.getKey(ball);
		List<ResourceLocation> orders = new ArrayList<>(orders(villager));
		if (!orders.remove(id)) {
			orders.add(id);
		}
		if (orders.isEmpty()) {
			ModAttachments.BALL_ORDERS.remove(villager);
		} else {
			ModAttachments.BALL_ORDERS.set(villager, List.copyOf(orders));
		}
	}

	/** Opens the orders screen (owner, friends and ops only; the first to give orders hires the smith). */
	public static void openOrders(ServerPlayer player, Villager villager) {
		if (!Friends.mayCommand(player, villager)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.ball_smith.not_yours", villager.getDisplayName())
				.withStyle(ChatFormatting.RED));
			return;
		}
		Friends.hire(player, villager);
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.ball_smith.orders", villager.getDisplayName()),
			p -> villager.isAlive() && p.isAlive() && p.distanceTo(villager) <= 8,
			menu -> render(menu, villager));
	}

	/** The same screen, not shown to anyone (tests). */
	public static ChoiceMenu ordersMenuForTest(ServerPlayer player, Villager villager) {
		return ChoiceMenu.detached(player, menu -> render(menu, villager));
	}

	private static void render(ChoiceMenu menu, Villager villager) {
		menu.clearButtons();
		ItemStack info = new ItemStack(Items.BOOK);
		info.set(DataComponents.CUSTOM_NAME, Component.translatable("screen.aliveworkplace.ball_smith.info")
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
		info.set(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable(orders(villager).isEmpty() ? "screen.aliveworkplace.ball_smith.anything" : "screen.aliveworkplace.ball_smith.only")
				.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GRAY)))));
		menu.button(INFO_SLOT, info, null);
		int tier = BuilderLevels.level(villager);
		int slot = FIRST_BALL_SLOT;
		for (BallRecipes.BallRecipe recipe : kinds((ServerLevel) villager.level())) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			Item ball = recipe.result().getItem();
			boolean on = orders(villager).contains(BuiltInRegistries.ITEM.getKey(ball));
			ItemStack icon = new ItemStack(ball);
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, on);
			List<Component> lore = new ArrayList<>();
			lore.add(Component.translatable(on ? "screen.aliveworkplace.ball_smith.on" : "screen.aliveworkplace.ball_smith.off")
				.withStyle(style -> style.withItalic(false).withColor(on ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
			if (recipe.tier() > tier) {
				lore.add(Component.translatable("screen.aliveworkplace.ball_smith.needs_level",
					Component.translatable("merchant.level." + recipe.tier())).withStyle(style -> style.withItalic(false).withColor(ChatFormatting.YELLOW)));
			}
			icon.set(DataComponents.LORE, new ItemLore(lore));
			menu.button(slot++, icon, p -> {
				toggle(villager, ball);
				render(menu, villager);
			});
		}
	}

	/** One recipe per kind of ball, easiest first. */
	static List<BallRecipes.BallRecipe> kinds(ServerLevel level) {
		Map<Item, BallRecipes.BallRecipe> byBall = new LinkedHashMap<>();
		for (BallRecipes.BallRecipe recipe : BallRecipes.all(level)) {
			byBall.merge(recipe.result().getItem(), recipe, (a, b) -> a.tier() <= b.tier() ? a : b);
		}
		List<BallRecipes.BallRecipe> out = new ArrayList<>(byBall.values());
		out.sort(java.util.Comparator.comparingInt(BallRecipes.BallRecipe::tier));
		return out;
	}

	private BallSmiths() {
	}
}

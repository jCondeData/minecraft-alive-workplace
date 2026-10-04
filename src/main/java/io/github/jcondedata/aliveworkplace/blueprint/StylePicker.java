package io.github.jcondedata.aliveworkplace.blueprint;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * The style screen (sneak-right-click the air with a blueprint): the blueprint as drawn and in every style there is;
 * click one and the blueprint in hand is built in that style (see {@link BlueprintStyles}).
 */
public final class StylePicker {
	static final int INFO = 4;
	/** Mirror the build (left to right). */
	public static final int MIRROR = 8;
	/** The first style's slot: as drawn, then the styles along the row (and the next). */
	public static final int FIRST = 19;

	public static void open(ServerPlayer player, InteractionHand hand) {
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.styles.title"),
			p -> p.isAlive() && p.getItemInHand(hand).is(ModItems.BLUEPRINT), menu -> render(menu, player, hand));
	}

	/** The same screen, not shown to anyone (tests). */
	public static ChoiceMenu forTest(ServerPlayer player, InteractionHand hand) {
		return ChoiceMenu.detached(player, menu -> render(menu, player, hand));
	}

	/** The slot of the {@code i}th choice (0: as drawn). */
	public static int slot(int i) {
		int row = i / 7;
		return FIRST + row * 9 + i % 7;
	}

	static void render(ChoiceMenu menu, ServerPlayer player, InteractionHand hand) {
		menu.clearButtons();
		ItemStack stack = player.getItemInHand(hand);
		BlueprintData data = BlueprintItem.data(stack).orElse(null);
		if (data == null) {
			return;
		}
		ResourceLocation base = BlueprintStyles.base(data.structure());
		String current = BlueprintStyles.styleOf(data.structure()).orElse("");
		menu.button(INFO, icon(Items.PAPER, Blueprints.displayName(base), ChatFormatting.GOLD,
			List.of(line(Component.translatable("screen.aliveworkplace.styles.how"), ChatFormatting.GRAY))), null);
		menu.button(MIRROR, choice(Items.GLASS_PANE, Component.translatable(data.mirrored() ? "screen.aliveworkplace.styles.mirrored"
			: "screen.aliveworkplace.styles.mirror"), data.mirrored()), p -> mirror(menu, p, hand));
		menu.divider(1);
		List<BlueprintStyles.Style> styles = BlueprintStyles.all();
		menu.button(slot(0), choice(Items.SPRUCE_LOG, Component.translatable("style.aliveworkplace.as_drawn"), current.isEmpty()),
			p -> choose(menu, p, hand, ""));
		for (int i = 0; i < styles.size() && i < 20; i++) {
			BlueprintStyles.Style style = styles.get(i);
			menu.button(slot(i + 1), choice(style.iconItem(), style.title(), current.equals(style.name())), p -> choose(menu, p, hand, style.name()));
		}
	}

	private static void choose(ChoiceMenu menu, ServerPlayer player, InteractionHand hand, String style) {
		ItemStack stack = player.getItemInHand(hand);
		BlueprintItem.data(stack).ifPresent(data -> {
			ResourceLocation now = BlueprintStyles.styled(data.structure(), style);
			stack.set(ModComponents.BLUEPRINT, data.withStructure(now));
			player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1.2f);
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.styles.chosen", Blueprints.displayName(now))
				.withStyle(ChatFormatting.GREEN));
			render(menu, player, hand);
		});
	}

	private static void mirror(ChoiceMenu menu, ServerPlayer player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlueprintItem.data(stack).ifPresent(data -> {
			boolean flip = !data.mirrored();
			BlueprintData flipped = BlueprintItem.mirrored(data, flip);
			if (flipped.placement().isPresent() && flipped.size().isPresent() && player.level() instanceof net.minecraft.server.level.ServerLevel level) {
				BlueprintOutline.show(level, player, flipped.placement().get(), flipped.size().get());
			}
			stack.set(ModComponents.BLUEPRINT, flipped);
			player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.9f);
			Chat.actionBar(player, Component.translatable(flip ? "message.aliveworkplace.styles.mirrored" : "message.aliveworkplace.styles.unmirrored")
				.withStyle(ChatFormatting.GREEN));
			render(menu, player, hand);
		});
	}

	private static ItemStack choice(Item item, Component name, boolean chosen) {
		List<Component> lore = new ArrayList<>();
		lore.add(line(Component.translatable(chosen ? "screen.aliveworkplace.styles.chosen" : "screen.aliveworkplace.styles.choose"),
			chosen ? ChatFormatting.GREEN : ChatFormatting.GRAY));
		ItemStack icon = icon(item, name, chosen ? ChatFormatting.GREEN : ChatFormatting.WHITE, lore);
		if (chosen) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
	}

	private static ItemStack icon(Item item, Component name, ChatFormatting color, List<Component> lore) {
		ItemStack icon = new ItemStack(item);
		icon.set(DataComponents.CUSTOM_NAME, line(name, color));
		icon.set(DataComponents.LORE, new ItemLore(lore));
		return io.github.jcondedata.aliveworkplace.mc.Tooltips.nameAndLoreOnly(icon);
	}

	private static Component line(Component text, ChatFormatting color) {
		return text.copy().withStyle(style -> style.withItalic(false).withColor(color));
	}

	private StylePicker() {
	}
}

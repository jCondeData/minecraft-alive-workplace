package io.github.jcondedata.aliveworkplace.shop;

import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A price in money rather than items: right-click the tag to set the price with buttons (or rename it in an anvil to a
 * number, "250") and put it in a Shop Counter's price row. With CobbleDollars that's 250 CobbleDollars; without, it's paid in emeralds at the configured rate
 * (rounded up, at least one).
 */
public class PriceTagItem extends Item {
	/** Largest price a tag can hold (well past anything sensible, short of overflowing). */
	static final long MAX = 1_000_000_000L;

	public PriceTagItem(Properties properties) {
		super(properties);
	}

	/** The price written on the tag in CobbleDollars, or -1 if it isn't a tag with a number on it. */
	public static long dollars(ItemStack stack) {
		if (!stack.is(ModItems.PRICE_TAG)) {
			return -1;
		}
		Component name = stack.get(DataComponents.CUSTOM_NAME);
		if (name == null) {
			return -1;
		}
		String digits = name.getString().replaceAll("[^0-9]", "");
		if (digits.isEmpty() || digits.length() > 10) {
			return -1;
		}
		long value = Long.parseLong(digits);
		return value > 0 && value <= MAX ? value : -1;
	}

	/** Writes {@code price} on the tag (as renaming it in an anvil would); 0 or less rubs it out. */
	public static void setPrice(ItemStack tag, long price) {
		if (price <= 0) {
			tag.remove(DataComponents.CUSTOM_NAME);
		} else {
			tag.set(DataComponents.CUSTOM_NAME, Component.literal(String.valueOf(Math.min(price, MAX))));
		}
	}

	// --- the price screen: right-click the tag -------------------------------------------------------

	/** The buttons, left to right on the middle row: taking off or adding on. */
	static final int[] STEPS = {-1000, -100, -10, -1, 0, 1, 10, 100, 1000};
	/** Slot of the first button (the price itself sits in the middle of the row). */
	public static final int FIRST_STEP_SLOT = 18;
	static final int DONE_SLOT = 40;

	@Override
	public net.minecraft.world.InteractionResultHolder<ItemStack> use(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
																	  net.minecraft.world.InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			io.github.jcondedata.aliveworkplace.work.ChoiceMenu.open(serverPlayer, Component.translatable("screen.aliveworkplace.price_tag.title"),
				p -> p.isAlive() && p.getItemInHand(hand).is(ModItems.PRICE_TAG), menu -> render(menu, serverPlayer, hand));
		}
		return net.minecraft.world.InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** The same screen, not shown to anyone (tests). */
	public static io.github.jcondedata.aliveworkplace.work.ChoiceMenu menuForTest(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.InteractionHand hand) {
		return io.github.jcondedata.aliveworkplace.work.ChoiceMenu.detached(player, menu -> render(menu, player, hand));
	}

	private static void render(io.github.jcondedata.aliveworkplace.work.ChoiceMenu menu, net.minecraft.server.level.ServerPlayer player,
							   net.minecraft.world.InteractionHand hand) {
		menu.clearButtons();
		long price = Math.max(0, dollars(player.getItemInHand(hand)));
		ItemStack shown = new ItemStack(ModItems.PRICE_TAG);
		shown.set(DataComponents.CUSTOM_NAME, (price > 0
			? Component.translatable("screen.aliveworkplace.price_tag.price", price, Money.describe(price, emeralds(price)))
			: Component.translatable("screen.aliveworkplace.price_tag.none")).copy().withStyle(style -> style.withItalic(false).withColor(ChatFormatting.GOLD)));
		menu.button(4, shown.copy(), null);
		for (int i = 0; i < STEPS.length; i++) {
			int step = STEPS[i];
			if (step == 0) {
				menu.button(FIRST_STEP_SLOT + i, shown.copy(), null);
				continue;
			}
			ItemStack icon = new ItemStack(step < 0 ? net.minecraft.world.item.Items.RED_DYE : net.minecraft.world.item.Items.LIME_DYE);
			icon.set(DataComponents.CUSTOM_NAME, Component.literal((step > 0 ? "+" : "") + step)
				.withStyle(style -> style.withItalic(false).withColor(step < 0 ? ChatFormatting.RED : ChatFormatting.GREEN)));
			menu.button(FIRST_STEP_SLOT + i, icon, p -> {
				ItemStack tag = p.getItemInHand(hand);
				if (tag.is(ModItems.PRICE_TAG)) {
					setPrice(tag, Math.max(0, Math.max(0, dollars(tag)) + step));
					render(menu, player, hand);
				}
			});
		}
		ItemStack done = new ItemStack(net.minecraft.world.item.Items.PAPER);
		done.set(DataComponents.CUSTOM_NAME, Component.translatable("screen.aliveworkplace.price_tag.done")
			.withStyle(style -> style.withItalic(false).withColor(ChatFormatting.WHITE)));
		menu.button(DONE_SLOT, done, net.minecraft.server.level.ServerPlayer::closeContainer);
	}

	/** The same price in emeralds, for servers without CobbleDollars (and the vanilla trade screen). */
	public static int emeralds(long dollars) {
		return (int) Math.max(1, Math.min(64, (dollars + Money.DOLLARS_PER_EMERALD - 1) / Money.DOLLARS_PER_EMERALD));
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		long dollars = dollars(stack);
		if (dollars > 0) {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.price_tag.price", Money.describe(dollars, emeralds(dollars)))
				.withStyle(ChatFormatting.GREEN));
		} else {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.price_tag.how").withStyle(ChatFormatting.GRAY));
		}
		tooltip.add(Component.translatable("tooltip.aliveworkplace.price_tag.use").withStyle(ChatFormatting.DARK_GRAY));
	}
}

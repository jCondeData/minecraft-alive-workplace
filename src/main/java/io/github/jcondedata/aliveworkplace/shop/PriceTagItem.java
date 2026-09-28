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
 * A price in money rather than items: rename the tag in an anvil to a number ("250") and put it in a Shop Counter's
 * price row. With CobbleDollars that's 250 CobbleDollars; without, it's paid in emeralds at the configured rate
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

package io.github.jcondedata.aliveworkplace.people;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The Evergreen Charm (ROADMAP 34.19a): a gold-and-green leaf pendant. Sneak-right-click an elder with it and, if they
 * have a good trait, they become ageless and never pass of old age ({@link LifeStages#offer}); one charm, one villager.
 * The tooltip says who takes it.
 */
public class EvergreenCharmItem extends Item {
	public EvergreenCharmItem(Properties properties) {
		super(properties);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
		lines.add(Component.translatable("tooltip.aliveworkplace.evergreen_charm.use").withStyle(ChatFormatting.GRAY));
		lines.add(Component.translatable("tooltip.aliveworkplace.evergreen_charm.who", LifeStages.HAPPY_MOOD, LifeStages.HAPPY_DAYS).withStyle(ChatFormatting.DARK_GREEN));
	}
}

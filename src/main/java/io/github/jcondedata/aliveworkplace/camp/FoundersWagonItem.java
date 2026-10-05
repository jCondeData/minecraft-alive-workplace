package io.github.jcondedata.aliveworkplace.camp;

import io.github.jcondedata.aliveworkplace.legend.Founder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The Founder's Wagon (ROADMAP 29.23), the Founder's gift to the hall's owner every week: a Settler's Wagon whose camp
 * also brings a Village Hall named "New &lt;village&gt;" and blueprints in the mother village's styles (both kept on the
 * wagon: {@code village} and {@code styles}, see {@link Founder#stockCamp}).
 */
public class FoundersWagonItem extends SettlersWagonItem {
	public FoundersWagonItem(Properties properties) {
		super(properties);
	}

	@Override
	protected void afterCamp(ServerLevel level, ServerPlayer player, BoundingBox box, ItemStack wagon) {
		Founder.stockCamp(level, player, box, wagon);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		String village = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString("village");
		tooltip.add(Component.translatable("tooltip.aliveworkplace.founders_wagon").withStyle(ChatFormatting.GRAY));
		if (!village.isEmpty()) {
			tooltip.add(Component.translatable("tooltip.aliveworkplace.founders_wagon.from", village).withStyle(ChatFormatting.GOLD));
		}
	}
}

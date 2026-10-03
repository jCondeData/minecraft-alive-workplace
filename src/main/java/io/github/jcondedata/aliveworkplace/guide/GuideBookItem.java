package io.github.jcondedata.aliveworkplace.guide;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * The Guide Book (the owner, ROADMAP 26.2a): right-click it to read how the mod works, page by page, each page an in-game
 * screenshot with a few short steps. Every player is given one the first time they join (the hidden advancement
 * {@code aliveworkplace:guide_book}); a lost one is crafted from a book and wheat. The book itself is read on the client.
 */
public class GuideBookItem extends Item {
	/** Opens the book's screen; the client sets it (a dedicated server has no screens). */
	public static Runnable open = () -> {
	};

	public GuideBookItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level.isClientSide) {
			open.run();
			player.playSound(SoundEvents.BOOK_PAGE_TURN, 1f, 1f);
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
		tooltip.add(Component.translatable("tooltip.aliveworkplace.guide_book").withStyle(ChatFormatting.GRAY));
	}
}

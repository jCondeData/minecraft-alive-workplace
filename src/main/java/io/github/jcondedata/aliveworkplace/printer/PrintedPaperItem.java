package io.github.jcondedata.aliveworkplace.printer;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * The Printer's papers (ROADMAP 34.11): the Village Gazette and the Illuminated Book. Our own items, so a luxury file
 * can name them, that carry vanilla's written-book content and open in vanilla's book screen on a right-click. The
 * Printer writes them as they're made ({@link Gazette#write}); a blank one (bought from a Printer, or out of the
 * creative tab) is written where its buyer stands, that day, the moment it's bought or first read.
 */
public class PrintedPaperItem extends Item {
	private final boolean illuminated;

	public PrintedPaperItem(Properties properties, boolean illuminated) {
		super(properties);
		this.illuminated = illuminated;
	}

	@Override
	public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (level instanceof ServerLevel server && player instanceof ServerPlayer reader) {
			if (!Gazette.written(stack)) {
				Gazette.write(server, player.blockPosition(), stack);
			}
			reader.containerMenu.broadcastChanges(); // the reader's own copy has the pages before the screen opens
			reader.connection.send(new ClientboundOpenBookPacket(hand));
		}
		player.awardStat(Stats.ITEM_USED.get(this));
		return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
	}

	/** Bought from a Printer: today's, from the village the buyer stands in. */
	@Override
	public void onCraftedBy(ItemStack stack, Level level, Player player) {
		if (level instanceof ServerLevel server && !Gazette.written(stack)) {
			Gazette.write(server, player.blockPosition(), stack);
		}
	}

	/** Its title once printed ("The Oakbrook Gazette"). */
	@Override
	public Component getName(ItemStack stack) {
		String village = Gazette.village(stack);
		if (Gazette.written(stack) && !village.isEmpty()) {
			return Component.translatable(illuminated ? "book.aliveworkplace.illuminated.title" : "book.aliveworkplace.gazette.title", village);
		}
		return super.getName(stack);
	}

	@Override
	public boolean isFoil(ItemStack stack) {
		return illuminated || super.isFoil(stack);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null) {
			lines.add(Component.translatable(illuminated ? "tooltip.aliveworkplace.illuminated_book.blank" : "tooltip.aliveworkplace.gazette.blank")
				.withStyle(ChatFormatting.GRAY));
			return;
		}
		lines.add(Component.translatable("book.aliveworkplace.gazette.author", Gazette.village(stack)).withStyle(ChatFormatting.GRAY));
		long day = Gazette.day(stack);
		if (day >= 0) {
			lines.add(Component.translatable("tooltip.aliveworkplace.gazette.day", day).withStyle(ChatFormatting.GRAY));
		}
		if (!illuminated) {
			lines.add(Component.translatable("tooltip.aliveworkplace.gazette.use").withStyle(ChatFormatting.DARK_GREEN));
		}
	}
}

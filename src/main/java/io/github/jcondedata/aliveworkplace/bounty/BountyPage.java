package io.github.jcondedata.aliveworkplace.bounty;

import static io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.icon;
import static io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.line;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/** The Bounties tab of the hall's journal (31.13): the open bounties, a click takes one, and a button hands an insignia in. */
public final class BountyPage {
	/** The first slot of the bounties. */
	public static final int ROW = 20;
	/** The hand-in button. */
	public static final int HAND_IN = 49;

	/** The journal opened at its Bounties tab from a Bounty Board (the nearest hall's). */
	public static void openAtBoard(ServerPlayer player, ServerLevel level, BlockPos board) {
		BlockPos hall = VillageHalls.nearest(level, board).orElse(null);
		if (hall == null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.bounty.no_hall").withStyle(ChatFormatting.YELLOW));
			return;
		}
		ChoiceMenu.openHall(player, Component.translatable("screen.aliveworkplace.hall.title", VillageHalls.name(level, hall)),
			p -> p.isAlive() && level.getBlockState(board).is(ModBlocks.BOUNTY_BOARD) && p.position().distanceToSqr(Vec3.atCenterOf(board)) <= 64,
			menu -> QuestJournal.render(menu, level, hall, player, QuestJournal.Tab.BOUNTIES));
	}

	/** Fills the tab. */
	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		List<Bounties.Bounty> open = Bounties.open(level, hall);
		if (open.isEmpty()) {
			Component why = !Bounties.ENABLED ? Component.translatable("screen.aliveworkplace.journal.bounties.off")
				: VillageRanks.of(level, hall).ordinal() < VillageRanks.Rank.VILLAGE.ordinal() ? Component.translatable("screen.aliveworkplace.journal.bounties.rank")
				: Component.translatable("screen.aliveworkplace.journal.bounties.none.about");
			menu.button(22, icon(Items.PAPER, Component.translatable("screen.aliveworkplace.journal.bounties.none"), ChatFormatting.GRAY,
				line(why, ChatFormatting.DARK_GRAY)), null);
		}
		long today = Chronicle.day(level);
		for (int i = 0; i < Math.min(open.size(), 7); i++) {
			Bounties.Bounty b = open.get(i);
			List<Component> lore = new ArrayList<>(Bounties.lines(level, b));
			lore.add(line(b.fallen ? Component.translatable("screen.aliveworkplace.journal.bounties.fallen")
				: Component.translatable("screen.aliveworkplace.hall.quest_days", Math.max(1, b.daysLeft(today))), b.fallen ? ChatFormatting.GOLD : ChatFormatting.DARK_GRAY));
			lore.add(line(b.takers.contains(viewer.getUUID()) ? Component.translatable("screen.aliveworkplace.journal.bounties.taken")
				: Component.translatable("screen.aliveworkplace.journal.bounties.take"), ChatFormatting.YELLOW));
			ItemStack stack = icon(new ItemStack(ModItems.WANTED_POSTER), b.name(level).copy(), ChatFormatting.RED, lore.toArray(Component[]::new));
			menu.button(ROW + i, stack, p -> {
				Bounties.take(p, level, b);
				QuestJournal.render(menu, level, hall, p, QuestJournal.Tab.BOUNTIES);
				menu.broadcastChanges();
			});
		}
		menu.button(HAND_IN, icon(new ItemStack(ModItems.CAPTAINS_INSIGNIA), Component.translatable("screen.aliveworkplace.journal.bounties.hand_in"), ChatFormatting.GOLD,
			line("screen.aliveworkplace.journal.bounties.hand_in.about", ChatFormatting.GRAY)), p -> {
			Bounties.claimAny(p, level, hall);
			QuestJournal.render(menu, level, hall, p, QuestJournal.Tab.BOUNTIES);
			menu.broadcastChanges();
		});
	}

	private BountyPage() {
	}
}

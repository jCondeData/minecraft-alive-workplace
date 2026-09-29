package io.github.jcondedata.aliveworkplace.research;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/** The research screen (sneak-right-click a Scholar): the village's research tree; click a topic to research it next. */
public final class ResearchScreen {
	static final int INFO = 4;
	/** Where the topics go, in {@link Research.Topic} order. */
	public static final int[] TOPIC_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33};

	public static void open(ServerPlayer player, Villager scholar) {
		ServerLevel level = player.serverLevel();
		BlockPos hall = VillageHalls.nearest(level, scholar.blockPosition()).orElse(null);
		if (hall == null) {
			player.displayClientMessage(Component.translatable("message.aliveworkplace.scholar.state.no_hall").withStyle(ChatFormatting.YELLOW), true);
			return;
		}
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.research.title", VillageHalls.name(level, hall)),
			p -> p.isAlive() && scholar.isAlive() && p.distanceToSqr(scholar) < 64, menu -> render(menu, level, hall));
	}

	/** The same screen, not shown to anyone (tests). */
	public static ChoiceMenu forTest(ServerPlayer player, BlockPos hall) {
		return ChoiceMenu.detached(player, menu -> render(menu, player.serverLevel(), hall));
	}

	static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall) {
		menu.clearButtons();
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		Research.State state = entity.research();
		Research.Topic current = state.currentTopic();
		List<Component> about = new ArrayList<>();
		if (current == null) {
			about.add(line(Component.translatable("screen.aliveworkplace.research.nothing"), ChatFormatting.YELLOW));
		} else {
			int next = state.level(current) + 1;
			about.add(line(Component.translatable("screen.aliveworkplace.research.now",
				Component.translatable("research.aliveworkplace.level", current.title(), BuilderLevels.levelName(next)),
				Math.round(100f * state.progress() / current.points(next))), ChatFormatting.GREEN));
			if (!state.paid()) {
				about.add(line(Component.translatable("screen.aliveworkplace.research.unpaid", Research.describe(current.cost(next))), ChatFormatting.YELLOW));
			}
		}
		about.add(line(Component.translatable("screen.aliveworkplace.research.how"), ChatFormatting.DARK_GRAY));
		menu.button(INFO, icon(Items.ENCHANTED_BOOK, Component.translatable("screen.aliveworkplace.research.info"), ChatFormatting.GOLD, about), null);
		menu.divider(1);
		Research.Topic[] topics = Research.Topic.values();
		for (int i = 0; i < topics.length; i++) {
			Research.Topic topic = topics[i];
			menu.button(TOPIC_SLOTS[i], topicIcon(state, topic), p -> {
				Research.State now = entity.research();
				Research.Topic busy = now.currentTopic();
				if (busy != null && now.paid()) {
					p.displayClientMessage(Component.translatable("message.aliveworkplace.research.busy", busy.title()).withStyle(ChatFormatting.YELLOW), true);
				} else if (!now.available(topic)) {
					p.displayClientMessage(Component.translatable("message.aliveworkplace.research.locked", topic.title()).withStyle(ChatFormatting.YELLOW), true);
				} else {
					entity.setResearch(now.choose(topic));
					level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
					p.displayClientMessage(Component.translatable("message.aliveworkplace.research.chosen", topic.title()).withStyle(ChatFormatting.GREEN), true);
				}
				render(menu, level, hall);
				menu.broadcastChanges();
			});
		}
	}

	static ItemStack topicIcon(Research.State state, Research.Topic topic) {
		int lvl = state.level(topic);
		List<Component> lore = new ArrayList<>();
		lore.add(line(topic.effect(), ChatFormatting.GRAY));
		lore.add(line(Component.translatable("screen.aliveworkplace.research.level", lvl, topic.maxLevel), ChatFormatting.GRAY));
		if (lvl >= topic.maxLevel) {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.complete"), ChatFormatting.GREEN));
		} else {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.cost", Research.describe(topic.cost(lvl + 1))), ChatFormatting.GRAY));
			for (var need : topic.needs().entrySet()) {
				if (state.level(need.getKey()) < need.getValue()) {
					lore.add(line(Component.translatable("screen.aliveworkplace.research.needs",
						Component.translatable("research.aliveworkplace.level", need.getKey().title(), BuilderLevels.levelName(need.getValue()))),
						ChatFormatting.RED));
				}
			}
			if (topic == state.currentTopic()) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.current"), ChatFormatting.GREEN));
			} else if (state.available(topic)) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.click"), ChatFormatting.YELLOW));
			}
		}
		ItemStack icon = icon(topic.icon, topic.title().copy(), lvl >= topic.maxLevel ? ChatFormatting.GREEN : ChatFormatting.WHITE, lore);
		icon.setCount(Math.max(1, lvl));
		if (topic == state.currentTopic()) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
	}

	private static ItemStack icon(net.minecraft.world.item.Item item, Component name, ChatFormatting color, List<Component> lore) {
		ItemStack icon = new ItemStack(item);
		icon.set(DataComponents.CUSTOM_NAME, line(name, color));
		icon.set(DataComponents.LORE, new ItemLore(lore));
		icon.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE);
		return icon;
	}

	private static Component line(Component text, ChatFormatting color) {
		return text.copy().withStyle(style -> style.withItalic(false).withColor(color));
	}

	private ResearchScreen() {
	}
}

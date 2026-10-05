package io.github.jcondedata.aliveworkplace.research;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.jetbrains.annotations.Nullable;

/**
 * The research screen (sneak-right-click a Scholar): the village's research tree; click a topic to research it next.
 * While a Legend with a research tree of their own lives in the village (29.11), the bottom row holds a tab for the
 * scholars' tree and one for each Legend's; sneak-right-clicking such a Legend opens their tab.
 */
public final class ResearchScreen {
	static final int INFO = 4;
	/** Where the topics go, in {@link Research.Topic} order (a Legend's tree: in its file's order). */
	public static final int[] TOPIC_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
	/** The tabs: the scholars' tree first, then each Legend's tree in the village (shown only with one at least). */
	public static final int[] TAB_SLOTS = {45, 46, 47, 48, 49, 50, 51, 52, 53};

	/** Which tab a screen shows: null for the scholars' tree. */
	public static final class View {
		@Nullable
		ResourceLocation tree;

		@Nullable
		public ResourceLocation tree() {
			return tree;
		}
	}

	public static void open(ServerPlayer player, Villager scholar) {
		open(player, scholar, new View());
	}

	/** A Legend's own tree (sneak-right-click them); their research needs a hall too. */
	public static void openForLegend(ServerPlayer player, Villager legend) {
		LegendData data = ModAttachments.LEGEND.get(legend);
		List<ResearchTree> trees = data == null ? List.of() : ResearchTrees.of(data.id());
		if (trees.isEmpty()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.research.no_tree", legend.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return;
		}
		View view = new View();
		view.tree = trees.get(0).id();
		open(player, legend, view);
	}

	private static void open(ServerPlayer player, Villager villager, View view) {
		ServerLevel level = Players.level(player);
		BlockPos hall = VillageHalls.nearest(level, villager.blockPosition()).orElse(null);
		if (hall == null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.scholar.state.no_hall").withStyle(ChatFormatting.YELLOW));
			return;
		}
		ChoiceMenu.open(player, Component.translatable("screen.aliveworkplace.research.title", VillageHalls.name(level, hall)),
			p -> p.isAlive() && villager.isAlive() && p.distanceToSqr(villager) < 64, menu -> render(menu, level, hall, view));
	}

	/** The same screen, not shown to anyone (tests). */
	public static ChoiceMenu forTest(ServerPlayer player, BlockPos hall) {
		return forTest(player, hall, new View());
	}

	/** The same screen on the tab {@code view} keeps (tests read it to see which tab is shown). */
	public static ChoiceMenu forTest(ServerPlayer player, BlockPos hall, View view) {
		return ChoiceMenu.detached(player, menu -> render(menu, Players.level(player), hall, view));
	}

	static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, View view) {
		menu.clearButtons();
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return;
		}
		List<ResearchTree> trees = ResearchTrees.inVillage(level, hall);
		ResearchTree tree = trees.stream().filter(t -> t.id().equals(view.tree)).findFirst().orElse(null);
		if (tree == null) {
			view.tree = null;
			renderScholars(menu, level, hall, entity, view);
		} else {
			renderTree(menu, level, hall, entity, view, tree);
		}
		if (trees.isEmpty()) {
			return;
		}
		List<Component> mainLore = new ArrayList<>();
		mainLore.add(line(Component.translatable("screen.aliveworkplace.research.tab.scholars.about"), ChatFormatting.GRAY));
		if (tree != null) {
			mainLore.add(line(Component.translatable("screen.aliveworkplace.research.tab.click"), ChatFormatting.YELLOW));
		}
		ItemStack main = icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.research.tab.scholars"),
			tree == null ? ChatFormatting.GREEN : ChatFormatting.WHITE, mainLore);
		if (tree == null) {
			main.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		menu.button(TAB_SLOTS[0], main, p -> show(menu, level, hall, view, null));
		for (int i = 0; i < trees.size() && i + 1 < TAB_SLOTS.length; i++) {
			ResearchTree t = trees.get(i);
			List<Component> lore = new ArrayList<>();
			lore.add(line(Component.translatable("screen.aliveworkplace.research.tab.legend", legendTitle(t)), ChatFormatting.GRAY));
			if (t != tree) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.tab.click"), ChatFormatting.YELLOW));
			}
			ItemStack tab = icon(t.icon(), t.name(), t == tree ? ChatFormatting.GREEN : ChatFormatting.WHITE, lore);
			if (t == tree) {
				tab.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(TAB_SLOTS[i + 1], tab, p -> show(menu, level, hall, view, t.id()));
		}
	}

	private static void show(ChoiceMenu menu, ServerLevel level, BlockPos hall, View view, @Nullable ResourceLocation tree) {
		view.tree = tree;
		render(menu, level, hall, view);
		menu.broadcastChanges();
	}

	static Component legendTitle(ResearchTree tree) {
		return Legends.get(tree.legend()).map(Legend::titleText).orElse(Component.literal(tree.legend().toString()));
	}

	private static void renderScholars(ChoiceMenu menu, ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, View view) {
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
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.research.busy", busy.title()).withStyle(ChatFormatting.YELLOW));
				} else if (!now.available(topic)) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.research.locked", topic.title()).withStyle(ChatFormatting.YELLOW));
				} else {
					entity.setResearch(now.choose(topic));
					level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.research.chosen", topic.title()).withStyle(ChatFormatting.GREEN));
				}
				render(menu, level, hall, view);
				menu.broadcastChanges();
			});
		}
	}

	private static void renderTree(ChoiceMenu menu, ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, View view, ResearchTree tree) {
		Research.State state = entity.research();
		Optional<ResearchTrees.Current> current = ResearchTrees.current(state, tree);
		Component legend = legendTitle(tree);
		List<Component> about = new ArrayList<>();
		if (current.isEmpty()) {
			about.add(line(Component.translatable("screen.aliveworkplace.research.nothing"), ChatFormatting.YELLOW));
		} else {
			ResearchTree.Topic topic = current.get().topic();
			int next = ResearchTrees.level(state, tree, topic) + 1;
			about.add(line(Component.translatable("screen.aliveworkplace.research.now",
				Component.translatable("research.aliveworkplace.level", topic.name(), BuilderLevels.levelName(next)),
				Math.round(100f * current.get().progress() / topic.points(next))), ChatFormatting.GREEN));
			if (!current.get().paid()) {
				about.add(line(Component.translatable("screen.aliveworkplace.research.tree_unpaid", Research.describe(topic.cost(next))), ChatFormatting.YELLOW));
			}
			if (!ResearchTrees.legendWorking(level, hall, tree)) {
				about.add(line(Component.translatable("screen.aliveworkplace.research.tree_waits", legend), ChatFormatting.YELLOW));
			}
		}
		about.add(line(Component.translatable("screen.aliveworkplace.research.tree_how", legend), ChatFormatting.DARK_GRAY));
		menu.button(INFO, icon(tree.icon(), tree.name(), ChatFormatting.GOLD, about), null);
		menu.divider(1);
		List<ResearchTree.Topic> topics = tree.topics();
		for (int i = 0; i < topics.size() && i < TOPIC_SLOTS.length; i++) {
			ResearchTree.Topic topic = topics.get(i);
			menu.button(TOPIC_SLOTS[i], treeTopicIcon(level, hall, state, tree, topic), p -> {
				Research.State now = entity.research();
				Optional<Component> why = ResearchTrees.whyNot(level, hall, now, tree, topic);
				if (why.isPresent()) {
					Chat.actionBar(p, why.get().copy().withStyle(ChatFormatting.YELLOW));
				} else {
					entity.setResearch(ResearchTrees.choose(now, tree, topic));
					level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.research.tree_chosen", legend, topic.name()).withStyle(ChatFormatting.GREEN));
				}
				render(menu, level, hall, view);
				menu.broadcastChanges();
			});
		}
	}

	static ItemStack treeTopicIcon(ServerLevel level, BlockPos hall, Research.State state, ResearchTree tree, ResearchTree.Topic topic) {
		int lvl = ResearchTrees.level(state, tree, topic);
		Optional<ResearchTrees.Current> current = ResearchTrees.current(state, tree);
		boolean isCurrent = current.isPresent() && current.get().topic() == topic;
		List<Component> lore = new ArrayList<>();
		if (!topic.description().getString().isEmpty()) {
			lore.add(line(topic.description(), ChatFormatting.GRAY));
		}
		lore.add(line(Component.translatable("screen.aliveworkplace.research.level", lvl, topic.levels()), ChatFormatting.GRAY));
		Optional<ResearchTree.Topic> rival = ResearchTrees.takenRival(state, tree, topic);
		if (lvl >= topic.levels()) {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.complete"), ChatFormatting.GREEN));
		} else if (rival.isPresent()) {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.exclusive_taken", rival.get().name()), ChatFormatting.RED));
		} else {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.cost", Research.describe(topic.cost(lvl + 1))), ChatFormatting.GRAY));
			for (var need : topic.needs().entrySet()) {
				ResearchTree.Topic other = tree.topic(need.getKey()).orElse(null);
				if (other != null && ResearchTrees.level(state, tree, other) < need.getValue()) {
					lore.add(line(Component.translatable("screen.aliveworkplace.research.needs",
						Component.translatable("research.aliveworkplace.level", other.name(), BuilderLevels.levelName(need.getValue()))), ChatFormatting.RED));
				}
			}
			if (topic.unlock().isPresent() && ResearchTrees.waitsForCounter(level, hall, topic)) {
				ResearchTree.Unlock unlock = topic.unlock().get();
				lore.add(line(Component.translatable("screen.aliveworkplace.research.unlock", unlock.at(), ResearchTrees.counterLabel(unlock.counter()),
					ResearchTrees.count(level, hall, unlock.counter())), ChatFormatting.RED));
			}
			List<ResearchTree.Topic> rivals = tree.rivals(topic);
			if (!rivals.isEmpty()) {
				MutableComponent names = Component.empty();
				for (int i = 0; i < rivals.size(); i++) {
					if (i > 0) {
						names.append(", ");
					}
					names.append(rivals.get(i).name());
				}
				lore.add(line(Component.translatable("screen.aliveworkplace.research.exclusive", names), ChatFormatting.GOLD));
			}
			if (isCurrent) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.current"), ChatFormatting.GREEN));
			} else if (ResearchTrees.whyNot(level, hall, state, tree, topic).isEmpty()) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.click"), ChatFormatting.YELLOW));
			}
		}
		ItemStack icon = icon(topic.icon(), topic.name(), lvl >= topic.levels() ? ChatFormatting.GREEN : rival.isPresent() ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE, lore);
		icon.setCount(Math.max(1, lvl));
		if (isCurrent) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
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
		return io.github.jcondedata.aliveworkplace.mc.Tooltips.nameAndLoreOnly(icon);
	}

	private static Component line(Component text, ChatFormatting color) {
		return text.copy().withStyle(style -> style.withItalic(false).withColor(color));
	}

	private ResearchScreen() {
	}
}

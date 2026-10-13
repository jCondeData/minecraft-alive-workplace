package io.github.jcondedata.aliveworkplace.research;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendData;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmResearch;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
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
import net.minecraft.server.MinecraftServer;
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
	public static final int[] TOPIC_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43}; // (the third row since Ramparts, 32.6)
	/** The tabs: the scholars' tree first, then each Legend's tree in the village (shown only with one at least). */
	public static final int[] TAB_SLOTS = {45, 46, 47, 48, 49, 50, 51, 52, 53};
	/** The Great Library's row (35.14), in place of the divider on the scholars' tab: its title, then its topics. */
	public static final int LIBRARY_HEADER = 9;
	public static final int[] LIBRARY_SLOTS = {10, 11, 12, 13, 14, 15};
	/** The second tier's topics, in {@link #LIBRARY_SLOTS} order. */
	public static final List<Research.Topic> LIBRARY_TOPICS = java.util.Arrays.stream(Research.Topic.values())
		.filter(Research.Topic::inSecondTier).toList();
	/** A realm capital's Realm tab, the last of the bottom row (33.15): the realm topics. */
	public static final int REALM_TAB = TAB_SLOTS[TAB_SLOTS.length - 1];

	/** Which tab a screen shows: null for the scholars' tree. */
	public static final class View {
		@Nullable
		ResourceLocation tree;
		boolean realm;

		@Nullable
		public ResourceLocation tree() {
			return tree;
		}

		/** Whether the Realm tab is shown (33.15). */
		public boolean realm() {
			return realm;
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
		boolean capital = RealmResearch.isCapital(level, hall) && !RealmResearch.all().isEmpty();
		if (!capital) {
			view.realm = false;
		}
		if (view.realm) {
			view.tree = null;
			tree = null;
			renderRealm(menu, level, hall, view);
		} else if (tree == null) {
			view.tree = null;
			renderScholars(menu, level, hall, entity, view);
		} else {
			renderTree(menu, level, hall, entity, view, tree);
		}
		if (trees.isEmpty() && !capital) {
			return;
		}
		List<Component> mainLore = new ArrayList<>();
		mainLore.add(line(Component.translatable("screen.aliveworkplace.research.tab.scholars.about"), ChatFormatting.GRAY));
		boolean onScholars = tree == null && !view.realm;
		if (!onScholars) {
			mainLore.add(line(Component.translatable("screen.aliveworkplace.research.tab.click"), ChatFormatting.YELLOW));
		}
		ItemStack main = icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.research.tab.scholars"),
			onScholars ? ChatFormatting.GREEN : ChatFormatting.WHITE, mainLore);
		if (onScholars) {
			main.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		menu.button(TAB_SLOTS[0], main, p -> show(menu, level, hall, view, null));
		if (capital) {
			List<Component> realmLore = new ArrayList<>();
			realmLore.add(line(Component.translatable("screen.aliveworkplace.research.tab.realm.about"), ChatFormatting.GRAY));
			if (!view.realm) {
				realmLore.add(line(Component.translatable("screen.aliveworkplace.research.tab.click"), ChatFormatting.YELLOW));
			}
			ItemStack realmTab = icon(Items.GOLDEN_HELMET, Component.translatable("screen.aliveworkplace.research.tab.realm"),
				view.realm ? ChatFormatting.GREEN : ChatFormatting.WHITE, realmLore);
			if (view.realm) {
				realmTab.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(REALM_TAB, realmTab, p -> {
				view.realm = true;
				view.tree = null;
				render(menu, level, hall, view);
				menu.broadcastChanges();
			});
		}
		for (int i = 0; i < trees.size() && i + 2 < TAB_SLOTS.length; i++) {
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
		view.realm = false;
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
				about.add(line(Component.translatable("screen.aliveworkplace.research.unpaid", Research.describe(current.cost(next, level, entity))), ChatFormatting.YELLOW));
			}
		}
		about.add(line(Component.translatable("screen.aliveworkplace.research.how"), ChatFormatting.DARK_GRAY));
		menu.button(INFO, icon(Items.ENCHANTED_BOOK, Component.translatable("screen.aliveworkplace.research.info"), ChatFormatting.GOLD, about), null);
		menu.divider(1);
		renderLibrary(menu, level, hall, entity, view, state);
		Research.Topic[] topics = Research.Topic.values();
		for (int i = 0; i < topics.length && i < TOPIC_SLOTS.length; i++) {
			Research.Topic topic = topics[i];
			if (!topic.inFirstTier()) {
				continue; // the Great Library's own topics have only its row (35.14)
			}
			int fromCapital = RealmResearch.fromCapital(level, hall, state, topic);
			menu.button(TOPIC_SLOTS[i], topicIcon(state, topic, topic.cost(state.level(topic) + 1, level, entity), fromCapital), p -> {
				Research.State now = entity.research();
				Research.Topic busy = now.currentTopic();
				if (RealmResearch.fromCapital(level, hall, now, topic) >= topic.maxLevel) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.research.from_capital_done", topic.title()).withStyle(ChatFormatting.AQUA));
				} else if (busy != null && now.paid()) {
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

	/**
	 * The Great Library's row (35.14): its second tier, open while a finished, standing Great Library is the village's;
	 * until then every topic reads "Needs the Great Library" and a click says so.
	 */
	private static void renderLibrary(ChoiceMenu menu, ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, View view, Research.State state) {
		boolean open = Research.secondTier(level, hall);
		List<Component> about = new ArrayList<>();
		if (open) {
			about.add(line(Component.translatable("screen.aliveworkplace.research.library.open"), ChatFormatting.GREEN));
		} else {
			about.add(line(Component.translatable("screen.aliveworkplace.research.library.locked"), ChatFormatting.RED));
			about.add(line(Component.translatable("screen.aliveworkplace.research.library.how"), ChatFormatting.GRAY));
		}
		about.add(line(Component.translatable("screen.aliveworkplace.research.library.price"), ChatFormatting.DARK_GRAY));
		menu.button(LIBRARY_HEADER, icon(open ? Items.ENCHANTING_TABLE : Items.BOOKSHELF, Component.translatable("screen.aliveworkplace.research.library"),
			open ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GRAY, about), null);
		for (int i = 0; i < LIBRARY_TOPICS.size() && i < LIBRARY_SLOTS.length; i++) {
			Research.Topic topic = LIBRARY_TOPICS.get(i);
			int next = Math.max(topic.maxLevel, state.level(topic)) + 1;
			menu.button(LIBRARY_SLOTS[i], libraryIcon(state, topic, topic.cost(next, level, entity), open), p -> {
				Research.State now = entity.research();
				Research.Topic busy = now.currentTopic();
				if (!Research.secondTier(level, hall)) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.research.needs_library", libraryTitle(topic)).withStyle(ChatFormatting.YELLOW));
				} else if (busy != null && now.paid()) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.research.busy", busy.title()).withStyle(ChatFormatting.YELLOW));
				} else if (now.level(topic) < topic.maxLevel || !now.available(topic, true)) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.research.locked", libraryTitle(topic)).withStyle(ChatFormatting.YELLOW));
				} else {
					entity.setResearch(now.choose(topic));
					level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.research.chosen", libraryTitle(topic)).withStyle(ChatFormatting.GREEN));
				}
				render(menu, level, hall, view);
				menu.broadcastChanges();
			});
		}
	}

	/** A topic's name in the Great Library's row: "Swift Hands IV–V" for the first tier's topic, else its own. */
	public static Component libraryTitle(Research.Topic topic) {
		return topic.inFirstTier() ? Component.translatable("screen.aliveworkplace.research.library." + topic.key()) : topic.title();
	}

	/** A second-tier topic's icon (35.14), {@code open} while the Great Library stands; {@code price}: its next level's. */
	static ItemStack libraryIcon(Research.State state, Research.Topic topic, Research.Cost price, boolean open) {
		int lvl = state.level(topic);
		boolean isCurrent = topic == state.currentTopic() && topic.libraryLevel(lvl + 1);
		List<Component> lore = new ArrayList<>();
		lore.add(line(topic.inFirstTier() ? Component.translatable("research.aliveworkplace." + topic.key() + ".library_effect") : topic.effect(), ChatFormatting.GRAY));
		lore.add(line(Component.translatable("screen.aliveworkplace.research.level", lvl, topic.top()), ChatFormatting.GRAY));
		if (!open) {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.library.needs"), ChatFormatting.RED));
			if (topic.libraryLevel(lvl)) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.library.paused"), ChatFormatting.YELLOW));
			}
		} else if (lvl >= topic.top()) {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.complete"), ChatFormatting.GREEN));
		} else {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.cost", Research.describe(price)), ChatFormatting.GRAY));
			if (lvl < topic.maxLevel) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.needs",
					Component.translatable("research.aliveworkplace.level", topic.title(), BuilderLevels.levelName(topic.maxLevel))), ChatFormatting.RED));
			} else if (isCurrent) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.current"), ChatFormatting.GREEN));
			} else if (state.available(topic, true)) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.click"), ChatFormatting.YELLOW));
			}
		}
		ItemStack icon = icon(topic.inFirstTier() ? Items.RABBIT_FOOT : topic.icon, libraryTitle(topic).copy(),
			!open ? ChatFormatting.DARK_GRAY : lvl >= topic.top() ? ChatFormatting.GREEN : ChatFormatting.LIGHT_PURPLE, lore);
		icon.setCount(Math.max(1, lvl - topic.maxLevel));
		if (isCurrent) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
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

	/** The Realm tab (33.15): the realm topics, worked by the capital's scholars and paid from the realm treasury. */
	private static void renderRealm(ChoiceMenu menu, ServerLevel level, BlockPos hall, View view) {
		RealmData.Realm realm = RealmResearch.realm(level, hall);
		if (realm == null) {
			return;
		}
		MinecraftServer server = level.getServer();
		Research.State state = RealmResearch.state(server, realm);
		RealmResearch.Topic current = RealmResearch.current(state);
		List<Component> about = new ArrayList<>();
		if (current == null) {
			about.add(line(Component.translatable("screen.aliveworkplace.research.nothing"), ChatFormatting.YELLOW));
		} else {
			int next = RealmResearch.level(state, current) + 1;
			about.add(line(Component.translatable("screen.aliveworkplace.research.now",
				Component.translatable("research.aliveworkplace.level", current.name(), BuilderLevels.levelName(next)),
				Math.round(100f * state.progress() / current.points())), ChatFormatting.GREEN));
			if (!state.paid()) {
				about.add(line(Component.translatable("screen.aliveworkplace.research.realm.unpaid", realmCost(current, next)), ChatFormatting.YELLOW));
			}
		}
		about.add(line(Component.translatable("screen.aliveworkplace.research.realm.treasury", RealmTreasury.emeralds(realm.treasury())), ChatFormatting.GRAY));
		about.add(line(Component.translatable("screen.aliveworkplace.research.realm.how"), ChatFormatting.DARK_GRAY));
		menu.button(INFO, icon(Items.GOLDEN_HELMET, Realms.title(server, realm), ChatFormatting.GOLD, about), null);
		menu.divider(1);
		List<RealmResearch.Topic> topics = new ArrayList<>(RealmResearch.all());
		for (int i = 0; i < topics.size() && i < TOPIC_SLOTS.length; i++) {
			RealmResearch.Topic topic = topics.get(i);
			menu.button(TOPIC_SLOTS[i], realmTopicIcon(state, topic), p -> {
				Realms.Result result = RealmResearch.choose(level, hall, p, topic);
				Chat.actionBar(p, result.message().copy().withStyle(result.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
				if (result.ok()) {
					level.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
				}
				render(menu, level, hall, view);
				menu.broadcastChanges();
			});
		}
	}

	/** "4× Paper, 1× Book and 8 emeralds from the realm treasury". */
	static Component realmCost(RealmResearch.Topic topic, int lvl) {
		return topic.items(lvl).isEmpty()
			? Component.translatable("screen.aliveworkplace.research.realm.emeralds", topic.emeralds())
			: Component.translatable("screen.aliveworkplace.research.realm.price", Research.describe(topic.items(lvl)), topic.emeralds());
	}

	static ItemStack realmTopicIcon(Research.State state, RealmResearch.Topic topic) {
		int lvl = RealmResearch.level(state, topic);
		boolean isCurrent = RealmResearch.current(state) == topic;
		List<Component> lore = new ArrayList<>();
		if (!topic.description().getString().isEmpty()) {
			lore.add(line(topic.description(), ChatFormatting.GRAY));
		}
		lore.add(line(Component.translatable("screen.aliveworkplace.research.level", lvl, topic.levels()), ChatFormatting.GRAY));
		if (lvl >= topic.levels()) {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.complete"), ChatFormatting.GREEN));
		} else {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.cost", realmCost(topic, lvl + 1)), ChatFormatting.GRAY));
			for (var need : topic.needs().entrySet()) {
				RealmResearch.Topic other = RealmResearch.get(need.getKey()).orElse(null);
				if (other != null && RealmResearch.level(state, other) < need.getValue()) {
					lore.add(line(Component.translatable("screen.aliveworkplace.research.needs",
						Component.translatable("research.aliveworkplace.level", other.name(), BuilderLevels.levelName(need.getValue()))), ChatFormatting.RED));
				}
			}
			if (isCurrent) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.current"), ChatFormatting.GREEN));
			} else if (RealmResearch.whyNot(state, topic).isEmpty()) {
				lore.add(line(Component.translatable("screen.aliveworkplace.research.realm.click"), ChatFormatting.YELLOW));
			}
		}
		ItemStack icon = icon(topic.icon(), topic.name(), lvl >= topic.levels() ? ChatFormatting.GREEN : ChatFormatting.WHITE, lore);
		icon.setCount(Math.max(1, lvl));
		if (isCurrent) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
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

	/** {@code price}: what its next level costs in the village (a founded Scholars' Guild: a quarter less, 30.19). */
	static ItemStack topicIcon(Research.State state, Research.Topic topic, Research.Cost price) {
		return topicIcon(state, topic, price, 0);
	}

	/** {@code fromCapital}: the capital's level where a realm member gets a higher one from it (33.15), else 0. */
	static ItemStack topicIcon(Research.State state, Research.Topic topic, Research.Cost price, int fromCapital) {
		int lvl = Math.min(topic.maxLevel, Math.max(state.level(topic), fromCapital)); // (Swift Hands IV and V show in the Great Library's row, 35.14)
		List<Component> lore = new ArrayList<>();
		lore.add(line(topic.effect(), ChatFormatting.GRAY));
		lore.add(line(Component.translatable("screen.aliveworkplace.research.level", lvl, topic.maxLevel), ChatFormatting.GRAY));
		if (fromCapital > 0) {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.from_capital",
				Component.translatable("research.aliveworkplace.level", topic.title(), BuilderLevels.levelName(fromCapital))), ChatFormatting.AQUA));
		}
		if (lvl >= topic.maxLevel) {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.complete"), ChatFormatting.GREEN));
		} else {
			lore.add(line(Component.translatable("screen.aliveworkplace.research.cost", Research.describe(price)), ChatFormatting.GRAY));
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
		if (topic == state.currentTopic() && lvl < topic.maxLevel) {
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

package io.github.jcondedata.aliveworkplace.hall;

import static io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.icon;
import static io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.line;

import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Places;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.QuestTracker;
import io.github.jcondedata.aliveworkplace.story.Rewards;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The quest journal (ROADMAP 31.3): the hall's Quests page with four tabs along the top, Village (the daily quests),
 * Personal (31.9), Story (31.4) and Bounties (31.13); a tab whose part hasn't landed yet says so in grey. Each quest shows
 * who asked, a line per objective with its progress, the reward and the days left. A click hands in (a {@code bring}) or
 * tracks (anything else); a shift-click always toggles Track ({@link QuestTracker}). Shown while milestone 31's gate is
 * open; while it's shut the hall shows today's page as before.
 */
public final class QuestJournal {
	public enum Tab {
		VILLAGE(2, Items.MAP, true),
		PERSONAL(3, Items.POPPY, false),
		STORY(5, Items.WRITTEN_BOOK, true),
		BOUNTIES(6, Items.CROSSBOW, false);

		public final int slot;
		final Item item;
		/** Whether its part of the milestone has landed (Personal 31.9 and Bounties 31.13 haven't yet). */
		public final boolean landed;

		Tab(int slot, Item item, boolean landed) {
			this.slot = slot;
			this.item = item;
			this.landed = landed;
		}

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	public static void render(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer, Tab tab) {
		menu.clearButtons();
		menu.button(0, icon(Items.ARROW, Component.translatable("screen.aliveworkplace.hall.back"), ChatFormatting.WHITE), p -> {
			VillageHallScreen.render(menu, level, hall, 0);
			menu.broadcastChanges();
		});
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		menu.button(4, icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.journal.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, line(Component.translatable("screen.aliveworkplace.journal.about"), ChatFormatting.GRAY),
			line(Component.translatable("screen.aliveworkplace.hall.quests_done", entity == null ? 0 : entity.questsDone()), ChatFormatting.GRAY)), null);
		for (Tab t : Tab.values()) {
			List<Component> lore = new ArrayList<>();
			lore.add(line("screen.aliveworkplace.journal.tab." + t.key() + ".about", ChatFormatting.GRAY));
			if (!t.landed) {
				lore.add(line("screen.aliveworkplace.journal.not_yet", ChatFormatting.DARK_GRAY));
			}
			ItemStack tabIcon = icon(t.item, Component.translatable("screen.aliveworkplace.journal.tab." + t.key()),
				t == tab ? ChatFormatting.YELLOW : t.landed ? ChatFormatting.WHITE : ChatFormatting.GRAY, lore.toArray(Component[]::new));
			if (t == tab) {
				tabIcon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(t.slot, tabIcon, p -> {
				render(menu, level, hall, p, t);
				menu.broadcastChanges();
			});
		}
		menu.divider(1);
		if (!tab.landed) {
			menu.button(22, icon(Items.PAPER, Component.translatable("screen.aliveworkplace.journal.empty"), ChatFormatting.GRAY,
				line("screen.aliveworkplace.journal.not_yet", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		if (tab == Tab.STORY) {
			renderStory(menu, level, hall, viewer);
			return;
		}
		List<Quest> quests = Stories.open(level, hall).stream().filter(q -> q.giver.equals("hall") && !q.done()).toList();
		if (quests.isEmpty()) {
			menu.button(22, icon(Items.PAPER, Component.translatable("screen.aliveworkplace.hall.no_quests"), ChatFormatting.GRAY), null);
		}
		for (int i = 0; i < Math.min(quests.size(), VillageHallScreen.QUEST_SLOTS.length); i++) {
			Quest quest = quests.get(i);
			menu.button(VillageHallScreen.QUEST_SLOTS[i], questIcon(level, quest, viewer), click(menu, level, hall, quest, Tab.VILLAGE));
		}
		// The reform steps of the edicts in force, in the row below (30.5), as on today's page.
		List<VillageQuests.Quest> reforms = entity == null ? List.of() : Reforms.shown(entity);
		int[] slots = VillageHallScreen.reformSlots(reforms.size());
		for (int i = 0; i < slots.length; i++) {
			VillageQuests.Quest quest = reforms.get(i);
			menu.button(slots[i], VillageHallScreen.reformIcon(quest, viewer), VillageHallScreen.handIn(menu, level, hall, quest));
		}
	}

	/** A click hands in (a {@code bring}) or tracks; a shift-click toggles Track. */
	private static Consumer<ServerPlayer> click(ChoiceMenu menu, ServerLevel level, BlockPos hall, Quest quest, Tab tab) {
		return p -> {
			Objectives.Objective now = quest.currentObjective();
			if (!menu.shiftClicked() && now instanceof Objectives.Bring bring) {
				int given = VillageQuests.handIn(p, hall, quest.id);
				if (given <= 0) {
					Chat.actionBar(p, Component.translatable("message.aliveworkplace.quest.nothing", Objectives.icon(bring.item()).getDescription())
						.withStyle(ChatFormatting.YELLOW));
				} else {
					level.playSound(null, p.blockPosition(), SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 0.8f, 1f);
				}
			} else {
				toggle(p, level, hall, quest);
			}
			render(menu, level, hall, p, tab);
			menu.broadcastChanges();
		};
	}

	/** The row the Story tab shows the chapters so far in, and the slots of the current chapter's quests. */
	public static final int CHAPTER_ROW = 18;
	public static final int[] STORY_QUEST_SLOTS = {38, 40, 42};

	/**
	 * The Story tab (31.4): the tale running here (or a side story), its chapters so far, done ones ticked, the one
	 * running with its quests below (a click hands in or tracks, as on the Village tab) and the days it has left.
	 */
	static void renderStory(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		io.github.jcondedata.aliveworkplace.story.ArcState main = io.github.jcondedata.aliveworkplace.story.Arcs.running(level, hall);
		List<io.github.jcondedata.aliveworkplace.story.ArcState> sides = io.github.jcondedata.aliveworkplace.story.Arcs.side(level, hall);
		io.github.jcondedata.aliveworkplace.story.ArcState s = main != null ? main : sides.isEmpty() ? null : sides.get(0);
		ResourceLocation id = s == null ? null : ResourceLocation.tryParse(s.id);
		io.github.jcondedata.aliveworkplace.story.Arcs.Arc arc = id == null ? null : io.github.jcondedata.aliveworkplace.story.Arcs.get(id).orElse(null);
		if (arc == null) {
			menu.button(22, icon(Items.BOOK, Component.translatable("screen.aliveworkplace.journal.story.none"), ChatFormatting.GRAY,
				line("screen.aliveworkplace.journal.story.none.about", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		long today = Chronicle.day(level);
		List<Component> about = new ArrayList<>();
		about.add(line(Component.translatable("screen.aliveworkplace.journal.story.day", today - s.beganDay + 1), ChatFormatting.GRAY));
		for (io.github.jcondedata.aliveworkplace.story.ArcState other : sides) {
			if (other != s) {
				ResourceLocation oid = ResourceLocation.tryParse(other.id);
				(oid == null ? java.util.Optional.<io.github.jcondedata.aliveworkplace.story.Arcs.Arc>empty() : io.github.jcondedata.aliveworkplace.story.Arcs.get(oid))
					.ifPresent(a -> about.add(line(Component.translatable("screen.aliveworkplace.journal.story.side", a.name()), ChatFormatting.DARK_AQUA)));
			}
		}
		ItemStack title = icon(Items.WRITTEN_BOOK, arc.name().copy(), ChatFormatting.GOLD, about.toArray(Component[]::new));
		menu.button(CHAPTER_ROW, title, null);
		int last = Math.min(s.chapter, arc.chapters().size() - 1);
		int first = Math.max(0, last - 6);
		List<Quest> open = Stories.open(level, hall).stream().filter(q -> s.id.equals(q.arc) && !q.done()).toList();
		for (int i = first; i <= last; i++) {
			io.github.jcondedata.aliveworkplace.story.Arcs.Chapter c = arc.chapters().get(i);
			Component name = Component.translatable("screen.aliveworkplace.journal.story.chapter", i + 1, c.name());
			ItemStack stack;
			if (i < s.chapter) {
				stack = icon(Items.ENCHANTED_BOOK, name.copy(), ChatFormatting.GREEN, line("screen.aliveworkplace.journal.story.done", ChatFormatting.DARK_GREEN));
			} else if (!s.started) {
				stack = icon(Items.CLOCK, name.copy(), ChatFormatting.GRAY,
					line(Component.translatable("screen.aliveworkplace.journal.story.begins", s.nextChapterDay), ChatFormatting.DARK_GRAY));
			} else {
				List<Component> lore = new ArrayList<>();
				lore.add(line("screen.aliveworkplace.journal.story.now", ChatFormatting.YELLOW));
				for (Quest q : open) {
					Objectives.Objective o = q.currentObjective();
					if (o != null) {
						lore.add(line(Component.translatable("screen.aliveworkplace.journal.objective", o.line(), q.progress[q.current()], o.need()), ChatFormatting.WHITE));
					}
				}
				if (c.timeLimitDays() > 0) {
					lore.add(line(Component.translatable("screen.aliveworkplace.hall.quest_days", Math.max(1, s.chapterDay + c.timeLimitDays() - today)),
						ChatFormatting.DARK_GRAY));
				}
				stack = icon(Items.WRITABLE_BOOK, name.copy(), ChatFormatting.YELLOW, lore.toArray(Component[]::new));
				stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
			}
			menu.button(CHAPTER_ROW + 1 + (i - first), stack, null);
		}
		for (int i = 0; i < Math.min(open.size(), STORY_QUEST_SLOTS.length); i++) {
			Quest quest = open.get(i);
			menu.button(STORY_QUEST_SLOTS[i], questIcon(level, quest, viewer), click(menu, level, hall, quest, Tab.STORY));
		}
	}

	/** Tracks or untracks {@code quest} for {@code player}, saying so above the hotbar. */
	public static void toggle(ServerPlayer player, ServerLevel level, BlockPos hall, Quest quest) {
		boolean on = QuestTracker.toggle(player, level, hall, quest.id);
		Chat.actionBar(player, Component.translatable(on ? "message.aliveworkplace.quest.tracking" : "message.aliveworkplace.quest.untracked", quest.title())
			.withStyle(on ? ChatFormatting.AQUA : ChatFormatting.GRAY));
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8f, 1f);
	}

	/** One quest: what stands for its current objective, who asked, a line per objective, the reward, the days left. */
	public static ItemStack questIcon(ServerLevel level, Quest quest, ServerPlayer viewer) {
		Objectives.Objective now = quest.currentObjective();
		Item item = itemFor(now);
		List<Component> lore = new ArrayList<>();
		if (!quest.poster.isEmpty()) {
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.quest_by", quest.poster), ChatFormatting.GRAY));
		}
		int current = quest.current();
		for (int i = 0; i < quest.objectives.size(); i++) {
			Objectives.Objective o = quest.objectives.get(i);
			if (quest.progress[i] >= o.need()) {
				lore.add(line(Component.translatable("screen.aliveworkplace.journal.objective_done", o.line()), ChatFormatting.DARK_GREEN));
			} else {
				lore.add(line(Component.translatable("screen.aliveworkplace.journal.objective", o.line(), quest.progress[i], o.need()),
					i == current ? ChatFormatting.WHITE : ChatFormatting.GRAY));
			}
		}
		for (Component reward : rewards(quest)) {
			lore.add(line(reward, ChatFormatting.GREEN));
		}
		if (quest.due >= 0) {
			long days = Math.max(1, (quest.due - level.getGameTime() + 23999) / 24000);
			lore.add(line(Component.translatable("screen.aliveworkplace.hall.quest_days", days), ChatFormatting.DARK_GRAY));
		}
		boolean tracked = QuestTracker.isTracked(viewer, quest.id);
		if (tracked) {
			lore.add(line("screen.aliveworkplace.journal.tracked", ChatFormatting.AQUA));
		}
		if (now instanceof Objectives.Bring bring) {
			int have = 0;
			for (int i = 0; i < viewer.getInventory().getContainerSize(); i++) {
				ItemStack s = viewer.getInventory().getItem(i);
				if (Objectives.matches(bring.item(), s)) {
					have += s.getCount();
				}
			}
			int left = bring.count() - quest.progress[current];
			lore.add(line(have > 0 ? Component.translatable("screen.aliveworkplace.hall.quest_hand_in", Math.min(have, left))
				: Component.translatable("screen.aliveworkplace.hall.quest_none_on_you"), have > 0 ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
		} else {
			lore.add(line(tracked ? "screen.aliveworkplace.journal.click_untrack" : "screen.aliveworkplace.journal.click_track", ChatFormatting.YELLOW));
		}
		lore.add(line(tracked ? "screen.aliveworkplace.journal.shift_untrack" : "screen.aliveworkplace.journal.shift_track", ChatFormatting.DARK_GRAY));
		int count = now == null ? 1 : Math.max(1, Math.min(64, now.need() - quest.progress[current]));
		ItemStack icon = icon(new ItemStack(item, count), quest.title().copy(), ChatFormatting.GOLD, lore.toArray(Component[]::new));
		if (tracked) {
			icon.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return icon;
	}

	/** The item that stands for an objective. */
	static Item itemFor(@org.jetbrains.annotations.Nullable Objectives.Objective o) {
		if (o instanceof Objectives.Bring b) {
			return Objectives.icon(b.item());
		}
		if (o instanceof Objectives.Kill) {
			return Items.IRON_SWORD;
		}
		if (o instanceof Objectives.Battle) {
			return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball")).orElse(Items.TARGET);
		}
		if (o instanceof Objectives.Wait) {
			return Items.CLOCK;
		}
		if (o instanceof Objectives.Reach) {
			return Items.COMPASS;
		}
		if (o instanceof Objectives.Talk) {
			return Items.BELL;
		}
		return Items.PAPER;
	}

	/** What a quest pays, a line each: money, items, a map. */
	static List<Component> rewards(Quest quest) {
		List<Component> out = new ArrayList<>();
		int emeralds = quest.emeralds();
		if (emeralds > 0) {
			out.add(Component.translatable("screen.aliveworkplace.hall.quest_reward", Money.describe((long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds)));
		}
		for (Rewards.Reward r : quest.rewards) {
			if (r instanceof Rewards.ItemReward item) {
				out.add(Component.translatable("screen.aliveworkplace.journal.reward_item", item.count(),
					io.github.jcondedata.aliveworkplace.mc.Lookup.value(BuiltInRegistries.ITEM, item.item()).getDescription()));
			} else if (r instanceof Rewards.MapReward map) {
				out.add(Component.translatable("screen.aliveworkplace.journal.reward_map", Places.name(map.place())));
			}
		}
		return out;
	}

	/**
	 * {@code /workplace quests}: the quests of the village {@code player} is nearest (and the one they track, if it's
	 * elsewhere), a line each with its current objective and a clickable [Track] or [Untrack].
	 */
	public static List<Component> chatList(ServerPlayer player) {
		List<Component> out = new ArrayList<>();
		ServerLevel level = io.github.jcondedata.aliveworkplace.mc.Players.level(player);
		List<QuestTracker.Found> list = new ArrayList<>();
		VillageHalls.nearest(level, player.blockPosition()).ifPresent(hall -> Stories.open(level, hall).stream()
			.filter(q -> q.giver.equals("hall") && !q.done()).forEach(q -> list.add(new QuestTracker.Found(level, hall, q))));
		QuestTracker.tracked(player).filter(f -> list.stream().noneMatch(o -> o.quest().id.equals(f.quest().id))).ifPresent(list::add);
		if (list.isEmpty()) {
			out.add(Component.translatable("command.aliveworkplace.quests.none").withStyle(ChatFormatting.GRAY));
			return out;
		}
		out.add(Component.translatable("command.aliveworkplace.quests.header").withStyle(ChatFormatting.GOLD));
		for (QuestTracker.Found f : list) {
			Quest q = f.quest();
			boolean tracked = QuestTracker.isTracked(player, q.id);
			int i = Math.max(0, q.current());
			Objectives.Objective o = q.objectives.get(i);
			Component button = Component.translatable(tracked ? "command.aliveworkplace.quests.untrack" : "command.aliveworkplace.quests.track")
				.withStyle(s -> s.withColor(tracked ? ChatFormatting.GRAY : ChatFormatting.AQUA)
					.withClickEvent(new net.minecraft.network.chat.ClickEvent(net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
						"/workplace quests " + (tracked ? "untrack" : "track " + q.id)))
					.withHoverEvent(new net.minecraft.network.chat.HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
						Component.translatable(tracked ? "command.aliveworkplace.quests.untrack_hover" : "command.aliveworkplace.quests.track_hover"))));
			out.add(Component.translatable("command.aliveworkplace.quests.line", q.title(), VillageHalls.name(f.level(), f.hall()), o.line(), q.progress[i],
				o.need(), button).withStyle(ChatFormatting.WHITE));
		}
		return out;
	}

	/** {@code /workplace quests track <id>}: finds the open quest by id in {@code player}'s dimension and tracks it. */
	public static boolean trackById(ServerPlayer player, java.util.UUID id) {
		ServerLevel level = io.github.jcondedata.aliveworkplace.mc.Players.level(player);
		for (BlockPos hall : Stories.halls(level)) {
			for (Quest q : Stories.open(level, hall)) {
				if (q.id.equals(id)) {
					QuestTracker.track(player, level, hall, id);
					Chat.chat(player, Component.translatable("message.aliveworkplace.quest.tracking", q.title()).withStyle(ChatFormatting.AQUA));
					return true;
				}
			}
		}
		return false;
	}

	private QuestJournal() {
	}
}

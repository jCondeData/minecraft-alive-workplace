package io.github.jcondedata.aliveworkplace.story;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The quest engine (ROADMAP 31.2): the open quests of every village, saved per dimension in {@code aliveworkplace_stories}
 * and keyed by hall. The hall's board posts one quest a morning from the {@code hall} quest files (the highest priority
 * that can be posted, then by weight), up to {@link VillageQuests#MAX_OPEN}. Quests move on events (hand-ins, kills,
 * battles) and in the hall's round (waits, deadlines), never by a scan each tick. Quests saved on the hall before 1.5
 * are moved in, with their progress, the first time the engine sees the hall ({@link #migrate}).
 */
public final class Stories {
	/** Config {@code villageQuests}: when off, no new quests go up; open ones stay and can still be finished. */
	public static boolean ENABLED = true;

	/** The hall's round: old quests move in, waits count, overdue quests come down and a new one goes up in the morning. */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		Data data = Data.get(level);
		migrate(level, hall, entity);
		long now = level.getGameTime();
		Entry e = data.entry(hall);
		boolean changed = e.quests.removeIf(q -> q.due >= 0 && now >= q.due);
		long today = Chronicle.day(level);
		changed |= e.moods.removeIf(m -> m.until < today);
		for (Quest q : List.copyOf(e.quests)) {
			int i = q.current();
			if (i >= 0 && q.objectives.get(i) instanceof Objectives.Wait wait) {
				int days = (int) Math.min(wait.days(), (now - q.posted) / VillageNeeds.DAY);
				if (days > q.progress[i]) {
					q.progress[i] = days;
					changed = true;
					if (q.done()) {
						finish(level, hall, entity, q, q.last == null ? null : level.getServer().getPlayerList().getPlayer(q.last));
					}
				}
			}
		}
		long day = level.getDayTime() / VillageNeeds.DAY;
		if (ENABLED && daily(e).size() < VillageQuests.MAX_OPEN && level.getDayTime() % VillageNeeds.DAY < 3000 && entity.lastQuestDay() < day) {
			Quest quest = make(level, hall, VillageRanks.questRewardFactor(entity.rank()), level.random);
			if (quest != null) {
				e.quests.add(quest);
				entity.setLastQuestDay(day);
				changed = true;
				announce(level, hall, quest);
			}
		}
		if (changed) {
			data.setDirty();
		}
	}

	/** The open quests of the hall's board (giver {@code hall}). */
	static List<Quest> daily(Entry e) {
		return e.quests.stream().filter(q -> q.giver.equals("hall")).toList();
	}

	/** Puts an already resolved and located quest up in the village round {@code hall} (arcs, tests). */
	public static void post(ServerLevel level, BlockPos hall, Quest quest) {
		Data data = Data.get(level);
		data.entry(hall).quests.add(quest);
		data.setDirty();
	}

	/** The halls of {@code level} the engine keeps quests for. */
	public static List<BlockPos> halls(ServerLevel level) {
		return List.copyOf(Data.get(level).halls.keySet());
	}

	/** Every open quest of the village round {@code hall} (old ones moved in first). */
	public static List<Quest> open(ServerLevel level, BlockPos hall) {
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			migrate(level, hall, entity);
		}
		Entry e = Data.get(level).halls.get(hall);
		return e == null ? List.of() : List.copyOf(e.quests);
	}

	/**
	 * A new quest for the board round {@code hall} (not posted): the {@code hall} files whose conditions hold and whose
	 * objectives can be asked for now, the highest priority first, then one by weight. Null when none can.
	 */
	@Nullable
	public static Quest make(ServerLevel level, BlockPos hall, float rankFactor, RandomSource random) {
		VillageHalls.Census census = VillageHalls.census(level, hall);
		List<Villager> people = census.workers();
		String someone = people.isEmpty() ? "" : people.get(random.nextInt(people.size())).getDisplayName().getString();
		TreeMap<Integer, List<QuestFiles.QuestFile>> byPriority = new TreeMap<>(Comparator.reverseOrder());
		for (QuestFiles.QuestFile f : QuestFiles.all()) {
			if (f.giver().equals("hall") && f.weight() > 0) {
				byPriority.computeIfAbsent(f.priority(), k -> new ArrayList<>()).add(f);
			}
		}
		for (List<QuestFiles.QuestFile> group : byPriority.values()) {
			List<Quest> can = new ArrayList<>();
			List<Integer> weights = new ArrayList<>();
			int total = 0;
			for (QuestFiles.QuestFile f : group) {
				Quest q = resolve(level, hall, census, f, rankFactor, random, someone);
				if (q != null) {
					can.add(q);
					weights.add(f.weight());
					total += f.weight();
				}
			}
			// One by weight; a quest whose place can't be found (31.3) is withdrawn and the next option drawn.
			while (total > 0) {
				int roll = random.nextInt(total);
				for (int i = 0; i < can.size(); i++) {
					roll -= weights.get(i);
					if (roll < 0) {
						Quest located = locate(level, hall, can.get(i));
						if (located != null) {
							return located;
						}
						Chronicle.atHall(level, hall, Chronicle.Kind.QUEST, Component.translatable("chronicle.aliveworkplace.quest_withdrawn", can.get(i).title()));
						total -= weights.get(i);
						can.remove(i);
						weights.remove(i);
						break;
					}
				}
			}
		}
		return null;
	}

	/**
	 * {@code quest} with its places looked up (31.3): each {@code reach} objective's and {@code map} reward's place, once,
	 * from the hall, and saved with the quest. The same place named twice is looked up once. Null when one can't be found.
	 */
	@Nullable
	public static Quest locate(ServerLevel level, BlockPos hall, Quest quest) {
		Map<List<Places.Option>, Places.Place> found = new java.util.HashMap<>();
		boolean changed = false;
		List<Objectives.Objective> objectives = new ArrayList<>();
		for (Objectives.Objective o : quest.objectives) {
			if (o instanceof Objectives.Reach reach && !reach.place().located()) {
				Places.Place p = found.computeIfAbsent(reach.place().options(), k -> Places.locate(level, hall, reach.place()));
				if (p == null) {
					return null;
				}
				objectives.add(new Objectives.Reach(p, reach.radius()));
				changed = true;
			} else {
				objectives.add(o);
			}
		}
		List<Rewards.Reward> rewards = new ArrayList<>();
		for (Rewards.Reward r : quest.rewards) {
			if (r instanceof Rewards.MapReward map && !map.place().located()) {
				Places.Place p = found.computeIfAbsent(map.place().options(), k -> Places.locate(level, hall, map.place()));
				if (p == null) {
					return null;
				}
				rewards.add(new Rewards.MapReward(p));
				changed = true;
			} else {
				rewards.add(r);
			}
		}
		return !changed ? quest : new Quest(quest.id, quest.file, quest.giver, quest.name, quest.poster, quest.posted, quest.due, objectives,
			quest.progress, rewards);
	}

	/** {@code file} as a quest going up now, or null when its conditions don't hold or an objective can't be asked for. */
	@Nullable
	static Quest resolve(ServerLevel level, BlockPos hall, VillageHalls.Census census, QuestFiles.QuestFile file, float rankFactor, RandomSource random,
						 String someone) {
		for (Condition c : file.conditions()) {
			if (!c.met(level, hall)) {
				return null;
			}
		}
		Objectives.Context context = new Objectives.Context(level, hall, census, random, someone);
		List<Objectives.Objective> objectives = new ArrayList<>();
		for (Objectives.Objective o : file.objectives()) {
			Objectives.Objective r = o.resolve(context);
			if (r == null) {
				return null;
			}
			objectives.add(r);
		}
		List<Rewards.Reward> rewards = file.rewards().stream().map(r -> r.resolve(objectives, rankFactor)).toList();
		long now = level.getGameTime();
		return new Quest(UUID.randomUUID(), file.id(), file.giver(), file.name(), context.poster, now, now + file.days() * VillageNeeds.DAY,
			objectives, new int[objectives.size()], rewards);
	}

	private static void announce(ServerLevel level, BlockPos hall, Quest quest) {
		Component text = Component.translatable("message.aliveworkplace.quest.posted", VillageHalls.name(level, hall), quest.title())
			.withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(hall.getCenter()) < (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
			Chat.chat(player, text);
		}
	}

	/**
	 * {@code player} hands in what they carry towards the {@code bring} the quest {@code id} is on (up to what's left).
	 * Returns how many went in, or -1 when the engine has no such quest.
	 */
	public static int handIn(ServerPlayer player, BlockPos hall, UUID id) {
		ServerLevel level = Players.level(player);
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return -1;
		}
		migrate(level, hall, entity);
		Entry e = Data.get(level).halls.get(hall);
		Quest quest = e == null ? null : e.quests.stream().filter(q -> q.id.equals(id)).findFirst().orElse(null);
		if (quest == null) {
			return -1;
		}
		int index = quest.current();
		if (index < 0 || !(quest.objectives.get(index) instanceof Objectives.Bring bring) || !mayHelp(level, hall, quest, player)) {
			return 0;
		}
		int left = bring.count() - quest.progress[index];
		List<BlockPos> chests = bring.station().map(p -> SupplyContainers.find(level, p, null)).filter(l -> !l.isEmpty())
			.orElseGet(() -> VillageNeeds.store(level, hall));
		Inventory inventory = player.getInventory();
		int given = 0;
		for (int i = 0; i < inventory.getContainerSize() && given < left; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!Objectives.matches(bring.item(), stack)) {
				continue;
			}
			ItemStack part = stack.split(Math.min(stack.getCount(), left - given));
			given += part.getCount();
			ItemStack rest = chests.isEmpty() ? part : SupplyContainers.insert(level, chests, part);
			if (!rest.isEmpty()) {
				Block.popResource(level, hall.above(), rest);
			}
		}
		if (given > 0) {
			inventory.setChanged();
			Friendship.onHandIn(level, hall, quest, player); // 31.5: the one who asked counts it a favour
			progress(level, hall, entity, quest, index, given, player);
		}
		return given;
	}

	/** A player killed something: it counts towards the first matching quest of the nearest village, and "anywhere" ones. */
	public static void onKill(ServerLevel level, LivingEntity killed, ServerPlayer player) {
		Optional<BlockPos> near = VillageHalls.nearest(level, killed.blockPosition());
		near.ifPresent(hall -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
				migrate(level, hall, entity);
			}
		});
		Data data = Data.get(level);
		for (Map.Entry<BlockPos, Entry> en : new ArrayList<>(data.halls.entrySet())) {
			boolean here = near.isPresent() && near.get().equals(en.getKey());
			for (Quest q : List.copyOf(en.getValue().quests)) {
				int i = q.current();
				if (i >= 0 && q.objectives.get(i) instanceof Objectives.Kill kill && (here || kill.anywhere()) && kill.matches(killed)
					&& level.getBlockEntity(en.getKey()) instanceof VillageHallBlockEntity entity && mayHelp(level, en.getKey(), q, player)) {
					progress(level, en.getKey(), entity, q, i, 1, player);
					break;
				}
			}
		}
	}

	/** A player beat one of the village's trainers: it counts towards the village's first battle quest. */
	public static void onBattle(ServerLevel level, BlockPos where, ServerPlayer player) {
		VillageHalls.nearest(level, where).ifPresent(hall -> {
			if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
				return;
			}
			migrate(level, hall, entity);
			Entry e = Data.get(level).halls.get(hall);
			for (Quest q : e == null ? List.<Quest>of() : List.copyOf(e.quests)) {
				int i = q.current();
				if (i >= 0 && q.objectives.get(i) instanceof Objectives.Battle && mayHelp(level, hall, q, player)) {
					progress(level, hall, entity, q, i, 1, player);
					return;
				}
			}
		});
	}

	/** Whether {@code player} may move {@code quest} on: not a one-time quest they've already finished in this village. */
	static boolean mayHelp(ServerLevel level, BlockPos hall, Quest quest, ServerPlayer player) {
		boolean repeatable = QuestFiles.get(quest.file).map(QuestFiles.QuestFile::repeatable).orElse(true);
		return repeatable || !Data.get(level).entry(hall).done.getOrDefault(player.getUUID(), Set.of()).contains(quest.file.toString());
	}

	static void progress(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Quest quest, int index, int amount, @Nullable ServerPlayer player) {
		quest.progress[index] = Math.min(quest.objectives.get(index).need(), quest.progress[index] + amount);
		if (player != null) {
			quest.helpers.merge(player.getUUID(), amount, Integer::sum);
			quest.last = player.getUUID();
		}
		Data.get(level).setDirty();
		if (quest.done()) {
			finish(level, hall, entity, quest, player);
		}
		QuestTracker.changed(level.getServer(), quest.id);
	}

	/** Takes {@code quest} down and pays it: money and items to {@code player}, the rest to the village. */
	static void finish(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, Quest quest, @Nullable ServerPlayer player) {
		Data data = Data.get(level);
		Entry e = data.entry(hall);
		if (!e.quests.remove(quest)) {
			return;
		}
		data.setDirty();
		QuestTracker.changed(level.getServer(), quest.id); // its bar goes
		entity.questDone();
		if (player != null) {
			e.done.computeIfAbsent(player.getUUID(), k -> new java.util.LinkedHashSet<>()).add(quest.file.toString());
		}
		e.finished.add(quest.file.toString());
		Chronicle.atHall(level, hall, Chronicle.Kind.QUEST, Component.translatable("chronicle.aliveworkplace.quest",
			player != null ? player.getDisplayName() : Component.translatable("chronicle.aliveworkplace.someone"), quest.title()));
		for (Rewards.Reward r : quest.rewards) {
			r.give(level, hall, player, quest);
		}
		Friendship.onQuestDone(level, hall, quest, player); // 31.5: the poster's friendship
		if (player != null) {
			int emeralds = quest.emeralds();
			Chat.chat(player, (emeralds > 0
				? Component.translatable("message.aliveworkplace.quest.done", quest.title(), Money.describe((long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds))
				: Component.translatable("message.aliveworkplace.quest.done_plain", quest.title())).withStyle(ChatFormatting.GREEN));
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.3f);
		}
	}

	/** Whether anyone has finished the quest file {@code file} in the village round {@code hall}. */
	public static boolean finished(ServerLevel level, BlockPos hall, ResourceLocation file) {
		Entry e = Data.get(level).halls.get(hall);
		return e != null && e.finished.contains(file.toString());
	}

	/** How much happier the quests' {@code village_mood} rewards make {@code villager} today. */
	public static int mood(ServerLevel level, Villager villager) {
		Data data = Data.get(level);
		if (data.halls.isEmpty()) {
			return 0;
		}
		long today = Chronicle.day(level);
		return VillageHalls.nearest(level, villager.blockPosition()).map(data.halls::get)
			.map(e -> e.moods.stream().filter(m -> m.until >= today).mapToInt(m -> m.points).sum()).orElse(0);
	}

	/**
	 * Moves the quests saved on the hall before 1.5 into the engine (design note M31, "The hall's old daily quests move
	 * into the engine"): each daily quest whose id isn't there yet becomes the file it came from, its progress, poster
	 * and posting time kept, its reward (already scaled) as money without the rank factor, due {@link VillageQuests#LASTS}
	 * after it went up. Reform steps stay on the hall ({@code Reforms} keeps them). Matching by id makes it safe to repeat.
	 */
	public static void migrate(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (entity.quests().stream().noneMatch(VillageQuests.Quest::daily)) {
			return;
		}
		Data data = Data.get(level);
		Entry e = data.entry(hall);
		Set<UUID> have = new HashSet<>();
		e.quests.forEach(q -> have.add(q.id));
		List<VillageQuests.Quest> keep = new ArrayList<>();
		for (VillageQuests.Quest old : entity.quests()) {
			if (!old.daily()) {
				keep.add(old);
				continue;
			}
			if (have.add(old.id())) {
				e.quests.add(moved(old));
			}
		}
		data.setDirty();
		entity.setQuests(keep);
		AliveWorkplace.LOG.debug("Moved the old quests of the hall at {} into the quest engine", hall);
	}

	/** One old-style daily quest as an engine quest. */
	static Quest moved(VillageQuests.Quest old) {
		Objectives.Objective objective;
		String file;
		switch (old.kind()) {
			case SLAY -> {
				objective = new Objectives.Kill("monster", old.count(), false);
				file = "daily/slay";
			}
			case BATTLE -> {
				objective = new Objectives.Battle(old.count());
				file = "daily/battle";
			}
			default -> {
				if (old.deliverTo().isPresent()) {
					objective = new Objectives.Bring(old.item(), old.count(), "station", old.deliverTo());
					file = "daily/worker_request";
				} else {
					objective = new Objectives.Bring(old.item(), old.count(), "hall", Optional.empty());
					String path = ResourceLocation.parse(old.item()).getPath();
					file = old.item().equals("minecraft:bread") ? "daily/food"
						: QuestFiles.get(AliveWorkplace.id("daily/want_" + path)).isPresent() ? "daily/want_" + path : "daily/legacy";
				}
			}
		}
		return new Quest(old.id(), AliveWorkplace.id(file), "hall", Optional.empty(), old.poster(), old.posted(), old.posted() + VillageQuests.LASTS,
			List.of(objective), new int[] {Math.min(old.progress(), objective.need())}, List.of(new Rewards.Money_(old.reward(), 0, 0, false)));
	}

	/** The hall's quests as the hall's page shows them (each by its current objective). */
	public static List<VillageQuests.Quest> view(List<Quest> quests) {
		List<VillageQuests.Quest> out = new ArrayList<>();
		for (Quest q : quests) {
			int i = q.current();
			if (i < 0) {
				continue;
			}
			Objectives.Objective o = q.objectives.get(i);
			VillageQuests.Kind kind;
			String item = "minecraft:air";
			Optional<BlockPos> to = Optional.empty();
			if (o instanceof Objectives.Bring b) {
				kind = VillageQuests.Kind.BRING;
				item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(Objectives.icon(b.item())).toString();
				to = b.station();
			} else if (o instanceof Objectives.Kill) {
				kind = VillageQuests.Kind.SLAY;
			} else if (o instanceof Objectives.Battle) {
				kind = VillageQuests.Kind.BATTLE;
			} else {
				continue;
			}
			long shownPosted = q.due >= 0 ? q.due - VillageQuests.LASTS : q.posted;
			out.add(new VillageQuests.Quest(q.id, kind, item, o.need(), q.progress[i], q.emeralds(), shownPosted, q.poster, to));
		}
		return out;
	}

	/** One village's stories. */
	static final class Entry {
		final List<Quest> quests = new ArrayList<>();
		/** Player → quest files they finished here. */
		final Map<UUID, Set<String>> done = new LinkedHashMap<>();
		/** Quest files anyone finished here. */
		final Set<String> finished = new java.util.LinkedHashSet<>();
		final List<Mood> moods = new ArrayList<>();
	}

	record Mood(int points, long until, Component reason) {
	}

	/** {@code aliveworkplace_stories}: one per dimension. */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_stories";
		final Map<BlockPos, Entry> halls = new LinkedHashMap<>();
		/** The quest each player tracks (31.3); only the overworld's is used, so it survives a change of dimension. */
		final Map<UUID, QuestTracker.Track> tracked = new LinkedHashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		Entry entry(BlockPos hall) {
			return halls.computeIfAbsent(hall.immutable(), k -> new Entry());
		}

		/** Drops a hall's stories (its hall was broken). */
		public void remove(BlockPos hall) {
			if (halls.remove(hall) != null) {
				setDirty();
			}
		}

		/** Notes that someone finished the quest file {@code file} in the village round {@code hall} (for {@code quest_done}). */
		public void noteFinished(BlockPos hall, ResourceLocation file) {
			entry(hall).finished.add(file.toString());
			setDirty();
		}

		void addMood(BlockPos hall, int points, long until, Component reason) {
			Entry e = entry(hall);
			e.moods.add(new Mood(points, until, reason));
			setDirty();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			tag.putInt("version", 1);
			ListTag list = new ListTag();
			for (Map.Entry<BlockPos, Entry> en : halls.entrySet()) {
				Entry e = en.getValue();
				CompoundTag h = new CompoundTag();
				h.putLong("hall", en.getKey().asLong());
				ListTag quests = new ListTag();
				e.quests.forEach(q -> quests.add(q.save()));
				h.put("quests", quests);
				CompoundTag done = new CompoundTag();
				e.done.forEach((player, files) -> {
					ListTag l = new ListTag();
					files.forEach(f -> l.add(StringTag.valueOf(f)));
					done.put(player.toString(), l);
				});
				h.put("done", done);
				ListTag finished = new ListTag();
				e.finished.forEach(f -> finished.add(StringTag.valueOf(f)));
				h.put("finished", finished);
				ListTag moods = new ListTag();
				for (Mood m : e.moods) {
					CompoundTag t = new CompoundTag();
					t.putInt("points", m.points);
					t.putLong("until_day", m.until);
					t.putString("reason", Rewards.json(m.reason).toString());
					moods.add(t);
				}
				h.put("mood_boosts", moods);
				list.add(h);
			}
			tag.put("halls", list);
			if (!tracked.isEmpty()) {
				ListTag tracks = new ListTag();
				tracked.forEach((player, t) -> tracks.add(t.save(player)));
				tag.put("tracked", tracks);
			}
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag list = Nbt.getList(tag, "halls", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag h = Nbt.compoundAt(list, i);
				Entry e = data.entry(BlockPos.of(Nbt.getLong(h, "hall")));
				ListTag quests = Nbt.getList(h, "quests", Tag.TAG_COMPOUND);
				for (int j = 0; j < quests.size(); j++) {
					try {
						e.quests.add(Quest.load(Nbt.compoundAt(quests, j)));
					} catch (RuntimeException ex) {
						AliveWorkplace.LOG.warn("Dropping an open quest that can't be read: {}", ex.getMessage());
					}
				}
				CompoundTag done = Nbt.getCompound(h, "done");
				for (String player : Nbt.keys(done)) {
					ListTag l = Nbt.getList(done, player, Tag.TAG_STRING);
					Set<String> files = new java.util.LinkedHashSet<>();
					for (int j = 0; j < l.size(); j++) {
						files.add(Nbt.stringAt(l, j));
					}
					e.done.put(UUID.fromString(player), files);
				}
				ListTag finished = Nbt.getList(h, "finished", Tag.TAG_STRING);
				for (int j = 0; j < finished.size(); j++) {
					e.finished.add(Nbt.stringAt(finished, j));
				}
				ListTag moods = Nbt.getList(h, "mood_boosts", Tag.TAG_COMPOUND);
				for (int j = 0; j < moods.size(); j++) {
					CompoundTag t = Nbt.compoundAt(moods, j);
					e.moods.add(new Mood(Nbt.getInt(t, "points"), Nbt.getLong(t, "until_day"),
						Rewards.text(com.google.gson.JsonParser.parseString(Nbt.getString(t, "reason")))));
				}
			}
			ListTag tracks = Nbt.getList(tag, "tracked", Tag.TAG_COMPOUND);
			for (int i = 0; i < tracks.size(); i++) {
				CompoundTag t = Nbt.compoundAt(tracks, i);
				data.tracked.put(Nbt.getUuid(t, "player"), QuestTracker.Track.load(t));
			}
			return data;
		}
	}

	private Stories() {
	}
}

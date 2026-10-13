package io.github.jcondedata.aliveworkplace.wonder;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jetbrains.annotations.Nullable;

/**
 * A Wonder's goods (ROADMAP 35.7): besides its blocks, each stage asks for goods from the village's jobs (the feast for
 * the crew, their tools, what the rites need, its treasures), as the lines of its file ({@link Wonder.Good}). A line is
 * in when the stockpile (the chests within {@link SupplyContainers#RADIUS} of the Foundation Stone) and what players
 * handed in at the stone ({@link WonderSite#delivered}) hold its count together. A stage starts only when every line is
 * in: then, by day (at night it waits for the morning), it is dedicated ({@link #dedicate}): the goods are taken, the
 * chronicle says so, the village gathers ({@link Dedications}) and the stage's build sites go down.
 *
 * <p>The village sees to the lines: each one still short is posted on the requests board for the stone
 * ({@link Requests#postAt}), porters carry matching goods to the stockpile before anything else ({@link #needs}),
 * crafters make what's short ({@link #orders}), and the hall's daily quest asks for the line furthest behind
 * ({@link #shortest}). Anyone can hand goods in, at the stone or on the board ({@link #handIn}). Cancelling hands back
 * what was handed in for a stage not yet dedicated ({@link #handBack}); what lies in the stockpile's chests stays there.
 * Nothing here is saved but the site's {@code dedicated} and {@code delivered}: the posts come back on the next check.
 *
 * <p>35.7a/b (the owner's change): most lines are things found by exploring ({@link Wonder.Good#found}), one of each, which
 * no crafter makes and the daily quest leaves out; each has a rare worker who finds it ({@link Wonder.Helper}): while
 * they're in the village their search goes on in play time ({@link #search}, saved as {@link WonderManager#searches}),
 * and when it's done the line is in, brought by them. A save from before a file changed gets back what was handed in for
 * lines that no longer take it ({@link #migrate}).
 */
public final class WonderGoods {
	/** The key of a goods line's post on the requests board. */
	static final String POST = "goods_";

	/**
	 * One goods line of the current stage: the line, how many it needs, how many were handed in and how many lie in the
	 * stockpile, and (35.7b) whether its helper brought it (then it's in, whatever lies in the chests).
	 */
	public record Line(int index, Wonder.Good good, int need, int handed, int stocked, boolean brought) {
		public Line(int index, Wonder.Good good, int need, int handed, int stocked) {
			this(index, good, need, handed, stocked, false);
		}

		/** How many are in (at most what's needed). */
		public int in() {
			return brought ? need : Math.min(need, handed + stocked);
		}

		/** How many are still to bring. */
		public int left() {
			return need - in();
		}

		public boolean full() {
			return left() <= 0;
		}
	}

	// ---------------------------------------------------------------- what a line takes

	/** Whether {@code stack} counts for {@code good}: its item (and potion, for a potion line), or one of its tag. */
	public static boolean matches(Wonder.Good good, ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (good.item().isPresent()) {
			if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(good.item().get())) {
				return false;
			}
		} else if (good.tag().isEmpty() || !stack.is(TagKey.create(Registries.ITEM, good.tag().get()))) {
			return false;
		}
		if (good.potion().isPresent()) {
			PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
			return contents != null && contents.potion().map(h -> h.is(good.potion().get())).orElse(false);
		}
		return true;
	}

	/** {@link #matches} as a test (a plain item line as an exact item, so the board can count it in the store). */
	public static Predicate<ItemStack> accepts(Wonder.Good good) {
		if (good.item().isPresent() && good.potion().isEmpty()) {
			return Requests.exactly(Lookup.value(BuiltInRegistries.ITEM, good.item().get()));
		}
		return stack -> matches(good, stack);
	}

	/** One of what {@code good} takes: its item (a potion of its kind), or its tag's first item. */
	public static ItemStack icon(Wonder.Good good) {
		Item item = good.item().map(id -> Lookup.value(BuiltInRegistries.ITEM, id))
			.orElseGet(() -> good.tag().flatMap(t -> Lookup.tag(BuiltInRegistries.ITEM, TagKey.create(Registries.ITEM, t)))
				.flatMap(set -> set.stream().findFirst()).map(Holder::value).orElse(Items.PAPER));
		Optional<Holder.Reference<Potion>> potion = good.potion().flatMap(p -> Lookup.holder(BuiltInRegistries.POTION, ResourceKey.create(Registries.POTION, p)));
		return potion.isPresent() ? PotionContents.createItemStack(item, potion.get()) : new ItemStack(item);
	}

	/**
	 * What {@code good} is called: "Bread", "Potion of Healing", or a tag's name ("Flowers"). Things whose own name says
	 * too little (35.7a) say which: "Dune Armor Trim Template", "Netherite Upgrade Template", "Music Disc: Lena Raine - Pigstep".
	 */
	public static Component what(Wonder.Good good) {
		if (good.tag().isPresent() && good.item().isEmpty()) {
			ResourceLocation tag = good.tag().get();
			return Component.translatableWithFallback("goods.aliveworkplace.tag." + tag.getNamespace() + "." + tag.getPath(), "#" + tag);
		}
		ResourceLocation id = good.item().get();
		String path = id.getPath();
		if (path.endsWith(TRIM)) {
			return Component.translatable("goods.aliveworkplace.template",
				Component.translatable("trim_pattern." + id.getNamespace() + "." + path.substring(0, path.length() - TRIM.length())));
		}
		if (path.equals("netherite_upgrade_smithing_template")) {
			return Component.translatable("goods.aliveworkplace.template", Component.translatable("upgrade.minecraft.netherite_upgrade"));
		}
		if (path.startsWith(DISC)) {
			return Component.translatable("goods.aliveworkplace.music_disc", Component.translatable("jukebox_song." + id.getNamespace() + "." + path.substring(DISC.length())));
		}
		return icon(good).getHoverName();
	}

	private static final String TRIM = "_armor_trim_smithing_template";
	private static final String DISC = "music_disc_";

	/** The job that makes {@code good}, by its villagers' name ("Chef"). */
	public static Component job(Wonder.Good good) {
		VillagerProfession job = profession(good.job());
		return Component.translatable("entity.minecraft.villager." + (job == null ? good.job().getPath() : job.name()));
	}

	@Nullable
	static VillagerProfession profession(ResourceLocation id) {
		return BuiltInRegistries.VILLAGER_PROFESSION.containsKey(id) ? Lookup.value(BuiltInRegistries.VILLAGER_PROFESSION, id) : null;
	}

	/** Whether the village round {@code hall} has a grown villager of {@code job} (a Chef, for the bread). */
	public static boolean hasMaker(ServerLevel level, BlockPos hall, ResourceLocation job) {
		VillagerProfession profession = profession(job);
		return profession != null && !level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
			v -> v.isAlive() && !v.isBaby() && v.getVillagerData().getProfession() == profession).isEmpty();
	}

	// ---------------------------------------------------------------- the lines

	/** The goods lines of {@code site}'s current stage (empty once finished, or for a Wonder no longer loaded). */
	public static List<Wonder.Good> goods(WonderSite site) {
		Wonder wonder = Wonders.loaded().get(site.wonder());
		if (wonder == null || site.done() || site.stage() < 1 || site.stage() > wonder.stages().size()) {
			return List.of();
		}
		return wonder.stages().get(site.stage() - 1).goods();
	}

	/** Copies of everything in the stockpile round {@code stone}. */
	static List<ItemStack> stockpile(ServerLevel level, BlockPos stone) {
		List<ItemStack> out = new ArrayList<>();
		for (BlockPos chest : SupplyContainers.find(level, stone, null)) {
			for (ItemStack stack : SupplyContainers.peekMatching(level, chest, s -> true)) {
				out.add(stack.copy());
			}
		}
		return out;
	}

	/**
	 * Where {@code site}'s current stage's goods stand: each line with what was handed in and what the stockpile holds
	 * for it (a stack counts for the first line that takes it, so two lines never count the same goods).
	 */
	public static List<Line> lines(ServerLevel level, WonderSite site) {
		List<Wonder.Good> goods = goods(site);
		if (goods.isEmpty()) {
			return List.of();
		}
		int[] handed = new int[goods.size()];
		for (WonderSite.Handed h : site.delivered()) {
			if (h.line() >= 0 && h.line() < goods.size() && matches(goods.get(h.line()), stackOf(h))) {
				handed[h.line()] += h.count(); // (35.7b: goods handed in for a line its file has since changed don't count)
			}
		}
		List<WonderManager.Search> searches = WonderManager.get(level).searches(site.id());
		List<ItemStack> stock = site.stone().map(s -> stockpile(level, s)).orElse(List.of());
		List<Line> out = new ArrayList<>();
		for (int i = 0; i < goods.size(); i++) {
			Wonder.Good good = goods.get(i);
			int want = Math.max(0, good.count() - handed[i]);
			int stocked = 0;
			for (ItemStack stack : stock) {
				if (stocked >= want) {
					break;
				}
				if (matches(good, stack)) {
					int n = Math.min(stack.getCount(), want - stocked);
					stack.shrink(n);
					stocked += n;
				}
			}
			out.add(new Line(i, good, good.count(), handed[i], stocked, brought(good, i, searches)));
		}
		return out;
	}

	/** Whether {@code good} (line {@code index}) was brought by its helper: their search for it has gone on long enough. */
	static boolean brought(Wonder.Good good, int index, List<WonderManager.Search> searches) {
		if (!good.found() || good.helper().isEmpty() || good.item().isEmpty()) {
			return false;
		}
		long need = (long) good.helper().get().days() * DAY;
		return search(searches, index, good.item().get()).map(s -> s.ticks() >= need).orElse(false);
	}

	private static Optional<WonderManager.Search> search(List<WonderManager.Search> searches, int index, ResourceLocation item) {
		return searches.stream().filter(s -> s.line() == index && s.item().equals(item)).findFirst();
	}

	/** By day: from dawn till dusk (a dedication waits for the morning). */
	public static boolean isDay(ServerLevel level) {
		return Math.floorMod(level.getDayTime(), 24000L) < 12000L;
	}

	/**
	 * {@code site}'s stage waits for its goods (WonderStages' round, once a second): the lines still short go on the
	 * requests board, and once every line is in, by day, the stage is dedicated. Returns whether it was.
	 */
	static boolean check(ServerLevel level, WonderSite site) {
		if (site.started() || !site.goingUp() || site.stone().isEmpty() || site.placement().isEmpty()) {
			return false;
		}
		site = migrate(level, site);
		List<Line> lines = lines(level, site);
		post(level, site, lines);
		return lines.stream().allMatch(Line::full) && isDay(level) && dedicate(level, site);
	}

	/** Each line of {@code site}'s stage still short is posted on the requests board for its stone; the others come down. */
	static void post(ServerLevel level, WonderSite site, List<Line> lines) {
		BlockPos stone = site.stone().orElseThrow();
		Component name = Wonders.name(site.wonder());
		int stage = site.stage();
		UUID id = site.id();
		for (Line line : lines) {
			String key = POST + line.index();
			if (line.full()) {
				Requests.clearAt(level, stone, key);
				continue;
			}
			int index = line.index();
			Wonder.Good good = line.good();
			Requests.postAt(level, stone, key, icon(good), line.left(), what(good), accepts(good),
				new Requests.ForBlock(name, good.found() ? hint(good) : job(good), (player, request) -> handIn(player, id, stage, index, true), good.found()));
		}
	}

	// ---------------------------------------------------------------- found goods and their helpers (35.7a, 35.7b)

	/** A day of a helper's search, in ticks of play. */
	public static final long DAY = 24000L;

	/**
	 * Where {@code good} is found, for the stone and the board: "in buried treasure, by a shipwreck's or an ocean ruin's
	 * map" ({@code goods.aliveworkplace.hint.<namespace>.<item>}), or "by exploring" for an item with no hint of its own.
	 */
	public static Component hint(Wonder.Good good) {
		ResourceLocation id = good.item().or(good::tag).orElse(ResourceLocation.withDefaultNamespace("air"));
		return Component.translatableWithFallback("goods.aliveworkplace.hint." + id.getNamespace() + "." + id.getPath(),
			Component.translatable("goods.aliveworkplace.hint.default").getString());
	}

	/** Who {@code helper} is, by name: a Legend's title ("Pathfinder"), "Master Netherworker", or the condition's own line. */
	public static Component helperName(ServerLevel level, BlockPos hall, Wonder.Helper helper) {
		if (helper.condition() instanceof io.github.jcondedata.aliveworkplace.rules.LegendSettled legend) {
			return io.github.jcondedata.aliveworkplace.rules.LegendSettled.title(legend.id());
		}
		if (helper.condition() instanceof io.github.jcondedata.aliveworkplace.rules.JobLevel job) {
			return Component.translatable("goods.aliveworkplace.helper.job", Component.translatable("merchant.level." + job.level()),
				io.github.jcondedata.aliveworkplace.rules.JobLevel.profession(job.job()));
		}
		return helper.condition().progress(level, hall).line();
	}

	/** How many whole days {@code line}'s helper has searched for it so far. */
	public static int searchedDays(ServerLevel level, WonderSite site, Line line) {
		return line.good().item().flatMap(item -> search(WonderManager.get(level).searches(site.id()), line.index(), item))
			.map(s -> (int) Math.min(Integer.MAX_VALUE, s.ticks() / DAY)).orElse(0);
	}

	/**
	 * The helpers search ({@code ticks} of the WonderStages round): for each found line of {@code site}'s waiting stage
	 * still short, whose helper is in the village (looked at while the village is loaded; remembered while it isn't), the
	 * search goes on while any player is on the server: time in play, not time with the server empty. Once it has gone on
	 * for the helper's days, the line is in, brought by them: the owner is told, the chronicle says so and the posts update.
	 * A line players fill themselves keeps its search for if they take their goods out again.
	 */
	public static void search(ServerLevel level, WonderSite site, int ticks) {
		if (!Wonders.ENABLED || site.started() || !site.goingUp() || site.stone().isEmpty() || site.placement().isEmpty()) {
			return;
		}
		if (goods(site).stream().noneMatch(g -> g.found() && g.helper().isPresent())) {
			return;
		}
		WonderManager manager = WonderManager.get(level);
		List<WonderManager.Search> before = manager.searches(site.id());
		boolean loaded = level.isLoaded(site.hall());
		boolean playing = !level.getServer().getPlayerList().getPlayers().isEmpty();
		List<WonderManager.Search> now = new ArrayList<>();
		List<Wonder.Good> brought = new ArrayList<>();
		for (Line line : lines(level, site)) {
			Wonder.Good good = line.good();
			if (!good.found() || good.helper().isEmpty() || good.item().isEmpty()) {
				continue;
			}
			ResourceLocation item = good.item().get();
			WonderManager.Search was = search(before, line.index(), item).orElse(new WonderManager.Search(line.index(), item, 0L, false));
			if (line.full()) {
				now.add(was);
				continue;
			}
			boolean seen = loaded ? good.helper().get().condition().met(level, site.hall()) : was.seen();
			long need = (long) good.helper().get().days() * DAY;
			long searched = Math.min(need, was.ticks() + (seen && playing ? ticks : 0));
			now.add(new WonderManager.Search(line.index(), item, searched, seen));
			if (was.ticks() < need && searched >= need) {
				brought.add(good);
			}
		}
		manager.setSearches(site.id(), now);
		WonderSite current = manager.site(site.id()).orElseThrow();
		for (Wonder.Good good : brought) {
			Component who = helperName(level, site.hall(), good.helper().get());
			Component name = Wonders.name(site.wonder());
			if (loaded) {
				Chronicle.atHall(level, site.hall(), Chronicle.Kind.WONDER, Component.translatable("chronicle.aliveworkplace.wonder.brought", who, what(good), name));
			}
			ServerPlayer owner = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
			if (owner != null) {
				Chat.system(owner, Component.translatable("message.aliveworkplace.wonder.goods.brought", who, what(good), name).withStyle(ChatFormatting.GOLD));
				owner.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.2f);
			}
		}
		if (!brought.isEmpty()) {
			post(level, current, lines(level, current));
		}
	}

	/**
	 * A save from before its file changed (35.7a: the old goods of farm produce became found goods and workshop goods):
	 * what was handed in for a line that no longer takes it moves to a line that does, or else goes into the stockpile's
	 * chests (dropped by the stone when they're full), and the owner is told. Returns the site as it is now.
	 */
	static WonderSite migrate(ServerLevel level, WonderSite site) {
		List<Wonder.Good> goods = goods(site);
		if (site.delivered().isEmpty() || goods.isEmpty()) {
			return site;
		}
		List<WonderSite.Handed> kept = new ArrayList<>();
		List<ItemStack> back = new ArrayList<>();
		boolean changed = false;
		for (WonderSite.Handed h : site.delivered()) {
			ItemStack one = stackOf(h);
			if (h.line() >= 0 && h.line() < goods.size() && matches(goods.get(h.line()), one)) {
				kept.add(h);
				continue;
			}
			changed = true;
			int other = -1;
			for (int i = 0; i < goods.size() && other < 0; i++) {
				if (matches(goods.get(i), one)) {
					other = i;
				}
			}
			if (other >= 0) {
				kept.add(new WonderSite.Handed(other, h.item(), goods.get(other).potion(), h.count()));
			} else if (!one.isEmpty()) {
				for (int left = h.count(); left > 0; ) {
					int n = Math.min(left, one.getMaxStackSize());
					back.add(one.copyWithCount(n));
					left -= n;
				}
			}
		}
		if (!changed) {
			return site;
		}
		WonderManager manager = WonderManager.get(level);
		manager.setDelivered(site.id(), kept);
		BlockPos stone = site.stone().orElse(site.hall());
		List<BlockPos> chests = SupplyContainers.find(level, stone, null);
		for (ItemStack stack : back) {
			ItemStack rest = chests.isEmpty() ? stack : SupplyContainers.insert(level, chests, stack);
			if (!rest.isEmpty()) {
				Containers.dropItemStack(level, stone.getX() + 0.5, stone.getY() + 1, stone.getZ() + 0.5, rest);
			}
		}
		if (!back.isEmpty()) {
			int n = back.stream().mapToInt(ItemStack::getCount).sum();
			Chronicle.atHall(level, site.hall(), Chronicle.Kind.WONDER, Component.translatable("chronicle.aliveworkplace.wonder.goods_changed", Wonders.name(site.wonder()), n));
			ServerPlayer owner = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
			if (owner != null) {
				Chat.system(owner, Component.translatable("message.aliveworkplace.wonder.goods.changed", Wonders.name(site.wonder()), n).withStyle(ChatFormatting.YELLOW));
			}
		}
		return manager.site(site.id()).orElseThrow();
	}

	// ---------------------------------------------------------------- handing in

	/**
	 * {@code player} hands in goods for line {@code index} of the Wonder {@code id}'s stage {@code stage}: from the hand
	 * held, or the whole inventory, up to what the line still lacks. They're kept by the stone ({@link WonderSite#delivered})
	 * till the dedication takes them, or a cancel hands them back. Returns how many were taken (0: not this stage any
	 * more, nothing that fits, the line is in, or Wonders are off).
	 */
	public static int handIn(ServerPlayer player, UUID id, int stage, int index, boolean wholeInventory) {
		ServerLevel level = Players.level(player);
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.site(id).orElse(null);
		if (!Wonders.ENABLED || site == null || site.stage() != stage || site.started() || !site.goingUp()) {
			return 0;
		}
		List<Line> lines = lines(level, site);
		if (index < 0 || index >= lines.size()) {
			return 0;
		}
		Line line = lines.get(index);
		int left = line.left();
		List<WonderSite.Handed> delivered = new ArrayList<>(site.delivered());
		Inventory inventory = player.getInventory();
		int taken = 0;
		List<ItemStack> from = new ArrayList<>();
		if (wholeInventory) {
			from.add(player.getMainHandItem());
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				ItemStack stack = inventory.getItem(i);
				if (stack != player.getMainHandItem()) {
					from.add(stack);
				}
			}
		} else {
			from.add(player.getMainHandItem());
		}
		for (ItemStack stack : from) {
			if (taken >= left) {
				break;
			}
			if (!matches(line.good(), stack)) {
				continue;
			}
			int n = Math.min(stack.getCount(), left - taken);
			record(delivered, index, line.good(), stack, n);
			stack.shrink(n);
			taken += n;
		}
		if (taken > 0) {
			inventory.setChanged();
			manager.setDelivered(id, delivered);
			level.playSound(null, site.stone().orElse(player.blockPosition()), SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 0.9f);
			post(level, manager.site(id).orElseThrow(), lines(level, manager.site(id).orElseThrow()));
		}
		return taken;
	}

	/** Adds {@code n} of {@code stack} to {@code delivered} for line {@code index}, with the same kind's entry if there is one. */
	private static void record(List<WonderSite.Handed> delivered, int index, Wonder.Good good, ItemStack stack, int n) {
		ResourceLocation item = BuiltInRegistries.ITEM.getKey(stack.getItem());
		Optional<ResourceLocation> potion = good.potion();
		for (int i = 0; i < delivered.size(); i++) {
			WonderSite.Handed h = delivered.get(i);
			if (h.line() == index && h.item().equals(item) && h.potion().equals(potion)) {
				delivered.set(i, new WonderSite.Handed(index, item, potion, h.count() + n));
				return;
			}
		}
		delivered.add(new WonderSite.Handed(index, item, potion, n));
	}

	/**
	 * Goods brought to the stone by a villager with no stockpile chest to put them in (a porter): kept by the stone like a
	 * player's. Returns what didn't count (the line already in, or not this stage's).
	 */
	public static ItemStack deliver(ServerLevel level, UUID id, int index, ItemStack stack) {
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.site(id).orElse(null);
		if (site == null || site.started() || !site.goingUp()) {
			return stack;
		}
		List<Line> lines = lines(level, site);
		if (index < 0 || index >= lines.size() || !matches(lines.get(index).good(), stack)) {
			return stack;
		}
		int n = Math.min(stack.getCount(), lines.get(index).left());
		if (n <= 0) {
			return stack;
		}
		List<WonderSite.Handed> delivered = new ArrayList<>(site.delivered());
		record(delivered, index, lines.get(index).good(), stack, n);
		manager.setDelivered(id, delivered);
		ItemStack rest = stack.copy();
		rest.shrink(n);
		return rest;
	}

	/**
	 * {@code player} right-clicked the Foundation Stone at {@code stone} holding {@code stack}: if it's one of the waiting
	 * stage's goods, it's handed in (up to what its line lacks) and the stone says how the line stands. Returns false when
	 * the stack is no good of this stage (the stone then shows its status).
	 */
	public static boolean handInAtStone(ServerPlayer player, BlockPos stone, ItemStack stack) {
		ServerLevel level = Players.level(player);
		Optional<WonderSite> found = WonderStages.atStone(level, stone);
		if (!Wonders.ENABLED || found.isEmpty() || found.get().started() || !found.get().goingUp() || stack.isEmpty()) {
			return false;
		}
		WonderSite site = found.get();
		List<Line> lines = lines(level, site);
		Line fits = null;
		for (Line line : lines) {
			if (matches(line.good(), stack)) {
				if (!line.full()) {
					fits = line;
					break;
				}
				if (fits == null) {
					fits = line;
				}
			}
		}
		if (fits == null) {
			return false;
		}
		if (fits.full()) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.wonder.goods.full", Wonders.name(site.wonder()), what(fits.good()))
				.withStyle(ChatFormatting.YELLOW));
			return true;
		}
		int given = handIn(player, site.id(), site.stage(), fits.index(), false);
		Line now = lines(level, WonderManager.get(level).site(site.id()).orElseThrow()).get(fits.index());
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.wonder.goods.handed", given, what(fits.good()), Wonders.name(site.wonder()),
			now.in(), now.need()).withStyle(ChatFormatting.GREEN));
		return true;
	}

	// ---------------------------------------------------------------- the dedication

	/**
	 * Dedicates {@code site}'s current stage if every line is in and it's day: the goods are taken (what was handed in
	 * first, then from the stockpile's chests, exactly what each line needs), the chronicle and the owner are told, the
	 * village gathers at the stone ({@link Dedications}) and the stage's build sites go down for its lead. Returns whether
	 * it was dedicated.
	 */
	public static boolean dedicate(ServerLevel level, WonderSite site) {
		if (!Wonders.ENABLED || site.started() || !site.goingUp() || site.stone().isEmpty() || site.placement().isEmpty() || !isDay(level)) {
			return false;
		}
		Wonder wonder = Wonders.loaded().get(site.wonder());
		List<Line> lines = lines(level, site);
		if (wonder == null || !lines.stream().allMatch(Line::full)) {
			return false;
		}
		BlockPos stone = site.stone().get();
		List<BlockPos> chests = SupplyContainers.find(level, stone, null);
		for (Line line : lines) {
			int fromChests = line.brought() ? 0 : line.need() - Math.min(line.need(), line.handed()); // (35.7b: what a helper brought is in already)
			take(level, chests, line.good(), fromChests);
		}
		WonderManager manager = WonderManager.get(level);
		manager.setDelivered(site.id(), List.of());
		manager.setSearches(site.id(), List.of());
		manager.setDedicated(site.id(), true);
		Requests.clearAt(level, stone);
		Wonder.Stage stage = wonder.stages().get(site.stage() - 1);
		Chronicle.atHall(level, site.hall(), Chronicle.Kind.WONDER, Component.translatable("chronicle.aliveworkplace.wonder.dedicated",
			site.stage(), stage.nameText(), wonder.nameText()));
		ServerPlayer owner = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
		if (owner != null) {
			Chat.system(owner, Component.translatable("message.aliveworkplace.wonder.dedicated", site.stage(), wonder.stages().size(), wonder.nameText(),
				stage.nameText()).withStyle(ChatFormatting.GOLD));
		}
		WonderSite now = manager.site(site.id()).orElseThrow();
		Dedications.begin(level, now);
		WonderStages.startStage(level, now, now.builder().orElse(null));
		return true;
	}

	/** Takes exactly {@code count} of what {@code good} takes out of {@code chests} (fewer if they don't hold that many); returns how many. */
	static int take(ServerLevel level, List<BlockPos> chests, Wonder.Good good, int count) {
		return takeStacks(level, chests, good, count).stream().mapToInt(ItemStack::getCount).sum();
	}

	/** Takes exactly {@code count} of what {@code good} takes out of {@code chests} (fewer if they don't hold that many), as the stacks taken. */
	public static List<ItemStack> takeStacks(ServerLevel level, List<BlockPos> chests, Wonder.Good good, int count) {
		List<ItemStack> out = new ArrayList<>();
		int left = count;
		Predicate<ItemStack> test = stack -> matches(good, stack);
		for (BlockPos chest : chests) {
			while (left > 0) {
				List<ItemStack> got = SupplyContainers.takeMatching(level, chest, test, 1);
				if (got.isEmpty()) {
					break;
				}
				for (ItemStack stack : got) {
					int n = Math.min(left, stack.getCount());
					left -= n;
					out.add(stack.split(n));
					if (!stack.isEmpty()) {
						ItemStack rest = SupplyContainers.insert(level, List.of(chest), stack);
						if (!rest.isEmpty()) {
							rest = SupplyContainers.insert(level, chests, rest);
						}
						if (!rest.isEmpty()) {
							Containers.dropItemStack(level, chest.getX() + 0.5, chest.getY() + 1, chest.getZ() + 0.5, rest);
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * How many of what {@code need} takes a porter may carry from {@code chest}: all of it in the storehouse ({@code job}
	 * null); from a worker's chests what their job doesn't keep ({@code Porters.keeps}), and gear (tools, potions) only
	 * from the job that makes the line's goods.
	 */
	public static int spare(ServerLevel level, BlockPos chest, Need need, @Nullable VillagerProfession job, boolean furnaceNear) {
		List<ItemStack> held = SupplyContainers.peekMatching(level, chest, need.accepts());
		int total = held.stream().mapToInt(ItemStack::getCount).sum();
		if (total == 0 || job == null) {
			return total;
		}
		ItemStack sample = held.get(0);
		if (sample.getMaxStackSize() <= 1) {
			return job == profession(need.good().job()) ? total : 0;
		}
		int keep = io.github.jcondedata.aliveworkplace.store.Porters.keeps(job, sample, furnaceNear);
		return keep == io.github.jcondedata.aliveworkplace.store.Porters.ALL ? 0 : Math.max(0, total - keep);
	}

	/** The goods handed in for {@code site}'s stage, as stacks (a potion line's as potions of its kind). */
	public static List<ItemStack> handedStacks(WonderSite site) {
		List<ItemStack> out = new ArrayList<>();
		for (WonderSite.Handed h : site.delivered()) {
			ItemStack one = stackOf(h);
			if (one.isEmpty()) {
				continue;
			}
			for (int left = h.count(); left > 0; ) {
				int n = Math.min(left, one.getMaxStackSize());
				out.add(one.copyWithCount(n));
				left -= n;
			}
		}
		return out;
	}

	/** One of what {@code h} handed in (a potion of its kind), or empty for an item no longer known. */
	static ItemStack stackOf(WonderSite.Handed h) {
		Item item = Lookup.value(BuiltInRegistries.ITEM, h.item());
		if (item == Items.AIR) {
			return ItemStack.EMPTY;
		}
		Optional<Holder.Reference<Potion>> potion = h.potion().flatMap(p -> Lookup.holder(BuiltInRegistries.POTION, ResourceKey.create(Registries.POTION, p)));
		return potion.isPresent() ? PotionContents.createItemStack(item, potion.get()) : new ItemStack(item);
	}

	/**
	 * A cancel (35.7): the goods handed in for {@code site}'s stage not yet dedicated go back, to its owner if online,
	 * else onto the ground by the stone. Returns them (empty for a dedicated stage: those were taken).
	 */
	static List<ItemStack> handBack(ServerLevel level, WonderSite site) {
		if (site.started() || site.delivered().isEmpty()) {
			return List.of();
		}
		List<ItemStack> back = handedStacks(site);
		ServerPlayer owner = site.owner().map(o -> level.getServer().getPlayerList().getPlayer(o)).orElse(null);
		BlockPos at = site.stone().orElse(site.hall());
		for (ItemStack stack : back) {
			ItemStack given = stack.copy();
			if (owner == null || !owner.getInventory().add(given)) {
				if (owner != null) {
					owner.drop(given, false);
				} else {
					Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, given);
				}
			}
		}
		if (owner != null && !back.isEmpty()) {
			Chat.system(owner, Component.translatable("message.aliveworkplace.wonder.goods.back", back.stream().mapToInt(ItemStack::getCount).sum(),
				Wonders.name(site.wonder())).withStyle(ChatFormatting.YELLOW));
		}
		return back;
	}

	// ---------------------------------------------------------------- the village sees to it

	/** A goods line a porter could carry for: the Wonder, its stone, the line, what it takes and how many are short. */
	public record Need(UUID wonder, BlockPos stone, int line, Wonder.Good good, int count) {
		public Predicate<ItemStack> accepts() {
			return stack -> matches(good, stack);
		}
	}

	/** The Wonders waiting for goods in the village of the hall nearest {@code pos}. */
	static List<WonderSite> waiting(ServerLevel level, BlockPos pos) {
		if (!Wonders.ENABLED) {
			return List.of();
		}
		List<WonderSite> out = new ArrayList<>();
		for (WonderSite site : WonderManager.get(level).sites()) {
			if (site.goingUp() && !site.started() && site.stone().isPresent() && site.placement().isPresent()
				&& site.hall().closerThan(pos, VillageHalls.RADIUS)) {
				out.add(site);
			}
		}
		return out;
	}

	/** The goods lines short at the Wonders of the village round {@code pos} (a porter's storehouse), first line first. */
	public static List<Need> needs(ServerLevel level, BlockPos pos) {
		List<Need> out = new ArrayList<>();
		for (WonderSite site : waiting(level, pos)) {
			for (Line line : lines(level, site)) {
				if (!line.full()) {
					out.add(new Need(site.id(), site.stone().get(), line.index(), line.good(), line.left()));
				}
			}
		}
		return out;
	}

	/** A short line a crafter could make, as a stock order for the stockpile: a plain item and how many are short. */
	public record Order(UUID wonder, BlockPos stone, int line, Item item, int count) {
	}

	/**
	 * The goods lines short at the Wonders of the village round {@code pos} (a crafter's workstation) that name one plain
	 * item (not a tag or a potion), as stock orders for the stockpile.
	 */
	public static List<Order> orders(ServerLevel level, BlockPos pos) {
		List<Order> out = new ArrayList<>();
		for (Need need : needs(level, pos)) {
			if (need.good().item().isPresent() && need.good().potion().isEmpty() && !need.good().found()) { // (35.7a: found goods are explored for, not made)
				out.add(new Order(need.wonder(), need.stone(), need.line(), Lookup.value(BuiltInRegistries.ITEM, need.good().item().get()), need.count()));
			}
		}
		return out;
	}

	/**
	 * What the hall's daily quest asks for (35.7): the line furthest behind (the least of it in, for its size) of the
	 * village's Wonder waiting for goods, as an item id or {@code #tag} and how many are short, with the stone. Potion lines
	 * are left to the clerics (a quest's item can't name a potion's kind). Empty when nothing's short.
	 */
	public static Optional<Wanted> shortest(ServerLevel level, BlockPos hall) {
		Wanted best = null;
		double bestShare = 2;
		for (WonderSite site : waiting(level, hall)) {
			if (!site.hall().equals(hall)) {
				continue;
			}
			for (Line line : lines(level, site)) {
				Wonder.Good good = line.good();
				if (line.full() || good.potion().isPresent() || good.found()) {
					continue; // (35.7a: a found line is a week's search, not a day's quest: it stays on the board and the stone)
				}
				double share = (double) line.in() / line.need();
				if (best == null || share < bestShare || share == bestShare && line.left() > best.count()) {
					String item = good.item().map(ResourceLocation::toString).orElseGet(() -> "#" + good.tag().orElseThrow());
					best = new Wanted(site.id(), site.stone().get(), line.index(), item, line.left());
					bestShare = share;
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/** A line the daily quest asks for: the Wonder, its stone, the line, the item (or {@code #tag}) and how many. */
	public record Wanted(UUID wonder, BlockPos stone, int line, String item, int count) {
	}

	/** The lines of {@code lines} that still need bringing, the furthest behind first (the stone's screen shows all of them). */
	static List<Line> furthestBehind(List<Line> lines) {
		return lines.stream().filter(l -> !l.full()).sorted(Comparator.comparingDouble((Line l) -> (double) l.in() / l.need())).toList();
	}

	private WonderGoods() {
	}
}

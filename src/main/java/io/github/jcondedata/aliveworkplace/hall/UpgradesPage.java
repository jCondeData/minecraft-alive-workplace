package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The Village Hall's "Upgrades" page (ROADMAP 36.14): the village's finished builds that have a next level (a Stone House
 * with a Stone House II, see {@link BlueprintUpgrades}), nearest the hall first, each with its level, what the next level
 * costs and whether its space is free. A click on a free one sends the village's Builder to build the next level over it,
 * the same way every upgrade is built (the Steward's, a bought upgrade blueprint): a build site of the next level on the
 * old one's spot, where only what changed gets built and the beds, job blocks and chests already there stay.
 *
 * <p>The space check counts only what the next level adds: the spots its blueprint fills that the old level doesn't. Air
 * there, or what the next level puts there anyway, is fine; natural ground and growth (grass, dirt, stone, sand, trees,
 * plants, water) is fine too, as the Builder clears it as part of the job. Anything built (a wall, a fence, a path, a
 * chest, another build) blocks it, and the row says what is in the way and where. Nothing is saved here: the order is
 * the build site, saved with the others.
 */
public final class UpgradesPage {
	public static final String PAGE = "upgrades";
	/** Builds on the page at most: the four rows under the divider, less the last slot when there are more. */
	public static final int SHOWN = ChoiceMenu.SIZE - VillageHallScreen.FIRST_ROW;
	/** Materials named on a row; the rest are counted in the total. */
	static final int MATERIALS = 3;

	/** What's in the way of an upgrade: the block, where, and the other build it belongs to (null: a block of no build). */
	public record Blocker(BlockPos pos, BlockState state, @Nullable Component building) {
	}

	/**
	 * A finished build with a next level: the build, the next level's blueprint, what the next level costs (materials by
	 * family, most first) and in all, natural blocks the Builder would clear, what blocks it (null: the space is free),
	 * and whether a Builder is already on it.
	 */
	public record Upgrade(BuildSiteManager.Finished built, ResourceLocation next, List<Map.Entry<Item, Integer>> cost, int total, int toClear,
						  @Nullable Blocker blocker, boolean building) {
		public boolean free() {
			return blocker == null && !building;
		}
	}

	/** What a click on a build came to. */
	public enum Outcome {
		STARTED, QUEUED, BLOCKED, BUILDING, NOT_ALLOWED, NO_BUILDER, GONE;

		public boolean ok() {
			return this == STARTED || this == QUEUED;
		}
	}

	@Nullable
	private static HallPages.Page page;

	/** Adds the page, as one of the mod's own spare pages (its tab gives way to other mods' pages when the row is full). */
	public static void init() {
		page = HallPages.registerSpare(PAGE, UpgradesPage::tab, UpgradesPage::header, UpgradesPage::fill);
	}

	/** The village's finished builds with a next level, nearest the hall first, each checked. */
	public static List<Upgrade> upgrades(ServerLevel level, BlockPos hall) {
		List<BuildSiteManager.Finished> built = new ArrayList<>(VillageAdvice.upgradable(level, hall));
		built.sort(Comparator.comparingDouble(f -> f.placement().origin().distSqr(hall)));
		List<Upgrade> out = new ArrayList<>();
		for (BuildSiteManager.Finished f : built) {
			check(level, f).ifPresent(out::add);
		}
		return out;
	}

	/** The upgrade of the finished build on {@code placement}, if it's still there and still has a next level. */
	public static Optional<Upgrade> at(ServerLevel level, BlockPos hall, BlueprintData.Placement placement) {
		for (BuildSiteManager.Finished f : VillageAdvice.upgradable(level, hall)) {
			if (f.placement().equals(placement)) {
				return check(level, f);
			}
		}
		return Optional.empty();
	}

	/** The next level of {@code built}: its cost, its space and whether it's under way. Empty if a blueprint is missing. */
	static Optional<Upgrade> check(ServerLevel level, BuildSiteManager.Finished built) {
		ResourceLocation next = BlueprintUpgrades.upgradeOf(built.structure());
		Optional<Blueprint> base = BlueprintLibrary.get(level, built.structure());
		Optional<Blueprint> up = BlueprintLibrary.get(level, next);
		if (base.isEmpty() || up.isEmpty()) {
			return Optional.empty();
		}
		BlueprintData.Placement placement = built.placement();
		boolean building = false;
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (!site.isDone() && site.placement().equals(placement)) {
				building = true;
				break;
			}
		}
		// the cost: what the next level needs more of than the old one (grouped as the builder's lists group them)
		Map<Item, Integer> before = grouped(BuildPlan.create(base.get(), placement).materials());
		Map<Item, Integer> after = grouped(BuildPlan.create(up.get(), placement).materials());
		Map<Item, Integer> cost = new LinkedHashMap<>();
		after.forEach((item, n) -> {
			int more = n - before.getOrDefault(item, 0);
			if (more > 0) {
				cost.put(item, more);
			}
		});
		int total = cost.values().stream().mapToInt(Integer::intValue).sum();
		List<Map.Entry<Item, Integer>> sorted = cost.entrySet().stream().sorted(Map.Entry.<Item, Integer>comparingByValue().reversed())
			.map(e -> Map.entry(e.getKey(), e.getValue())).toList();
		Space space = space(level, built, base.get(), up.get());
		return Optional.of(new Upgrade(built, next, sorted, total, space.toClear(), space.blocker(), building));
	}

	private static Map<Item, Integer> grouped(Map<Item, Integer> materials) {
		Map<Item, Integer> out = new LinkedHashMap<>();
		materials.forEach((item, n) -> out.merge(MaterialFamilies.key(item), n, Integer::sum));
		return out;
	}

	private record Space(@Nullable Blocker blocker, int toClear) {
	}

	private record Other(BoundingBox box, Component name) {
	}

	/**
	 * The space check: every spot the next level's blueprint fills that the old level leaves empty (or doesn't reach),
	 * lowest first. Air, or the block the next level wants there, is fine; natural ground and growth is fine (counted, the
	 * Builder clears it); anything else blocks, and so does any solid block of another build.
	 */
	static Space space(ServerLevel level, BuildSiteManager.Finished built, Blueprint base, Blueprint up) {
		BlueprintData.Placement placement = built.placement();
		Map<BlockPos, BlockState> old = new HashMap<>();
		for (Blueprint.Entry entry : base.blocks()) {
			old.put(world(placement, entry.pos()), entry.state());
		}
		List<Other> others = others(level, placement);
		List<Blueprint.Entry> added = new ArrayList<>();
		for (Blueprint.Entry entry : up.blocks()) {
			BlockState was = old.get(world(placement, entry.pos()));
			// the old level's own blocks are the upgrade's to change, and a room left empty in both (with what the
			// villagers put in it since) isn't something the next level adds
			if (was == null || was.isAir() && !entry.state().isAir()) {
				added.add(entry);
			}
		}
		added.sort(Comparator.comparingInt((Blueprint.Entry e) -> e.pos().getY()).thenComparingInt(e -> e.pos().getX()).thenComparingInt(e -> e.pos().getZ()));
		int toClear = 0;
		for (Blueprint.Entry entry : added) {
			BlockPos pos = world(placement, entry.pos());
			if (!level.isLoaded(pos)) {
				continue; // never load a chunk to draw a page; the Builder looks again when he gets there
			}
			BlockState there = level.getBlockState(pos);
			if (there.isAir()) {
				continue;
			}
			BlockState wanted = MaterialRules.transformed(entry.state(), placement.mirror(), placement.rotation());
			if (MaterialRules.matches(there, wanted)) {
				continue;
			}
			Other other = null;
			for (Other o : others) {
				if (o.box().isInside(pos)) {
					other = o;
					break;
				}
			}
			boolean loose = there.getBlock() instanceof LiquidBlock || there.canBeReplaced() && there.getFluidState().isEmpty();
			if (other != null && !loose) {
				return new Space(new Blocker(pos, there, other.name()), toClear);
			}
			if (other == null && !natural(there)) {
				return new Space(new Blocker(pos, there, null), toClear);
			}
			toClear++;
		}
		return new Space(null, toClear);
	}

	private static BlockPos world(BlueprintData.Placement placement, BlockPos local) {
		return placement.origin().offset(net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(local,
			placement.mirror(), placement.rotation(), BlockPos.ZERO));
	}

	/** The village's other builds, finished or going up, with their outlines (not the one on {@code placement}). */
	private static List<Other> others(ServerLevel level, BlueprintData.Placement placement) {
		List<Other> out = new ArrayList<>();
		BuildSiteManager sites = BuildSiteManager.get(level);
		int reach = VillageHalls.RADIUS + 64;
		for (BuildSiteManager.Finished f : sites.finishedNear(level, placement.origin(), reach)) {
			if (!f.placement().equals(placement)) {
				BlueprintLibrary.get(level, f.structure()).ifPresent(b -> out.add(new Other(BlueprintOutline.bounds(f.placement(), b.size()),
					Blueprints.displayName(f.structure()))));
			}
		}
		for (BuildSite site : sites.all()) {
			if (!site.isDone() && !site.placement().equals(placement) && site.placement().dimension().equals(Ids.of(level.dimension()))
				&& site.placement().origin().distSqr(placement.origin()) <= (double) reach * reach) {
				BlueprintLibrary.get(level, site.structure()).ifPresent(b -> out.add(new Other(BlueprintOutline.bounds(site.placement(), b.size()),
					Blueprints.displayName(site.structure()))));
			}
		}
		return out;
	}

	/**
	 * Natural ground and growth the Builder clears for an upgrade: what the Steward's sites clear (grass, dirt, stone, sand,
	 * gravel, logs, leaves that grew, plants), water and lava, ice and ores.
	 */
	public static boolean natural(BlockState state) {
		return state.isAir() || StewardSafety.natural(state) || state.getBlock() instanceof LiquidBlock || state.is(BlockTags.ICE)
			|| state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES) || state.is(BlockTags.COPPER_ORES) || state.is(BlockTags.GOLD_ORES)
			|| state.is(BlockTags.REDSTONE_ORES) || state.is(BlockTags.LAPIS_ORES) || state.is(BlockTags.DIAMOND_ORES) || state.is(BlockTags.EMERALD_ORES);
	}

	// --- ordering it ----------------------------------------------------------------------------------------------

	/**
	 * {@code player} orders the next level of the build on {@code placement}: if they may build in the village and its
	 * space is free, the nearest free Builder (or the one with the shortest queue) builds it over the old level.
	 */
	public static Outcome order(ServerLevel level, BlockPos hall, ServerPlayer player, BlueprintData.Placement placement) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Outcome.GONE;
		}
		if (!VillageProtection.mayBuild(level, entity, player)) {
			return Outcome.NOT_ALLOWED;
		}
		Optional<Upgrade> found = at(level, hall, placement);
		if (found.isEmpty()) {
			return Outcome.GONE;
		}
		Upgrade upgrade = found.get();
		if (upgrade.building()) {
			return Outcome.BUILDING;
		}
		if (upgrade.blocker() != null) {
			return Outcome.BLOCKED;
		}
		Optional<Blueprint> next = BlueprintLibrary.get(level, upgrade.next());
		if (next.isEmpty()) {
			return Outcome.GONE;
		}
		Optional<Villager> builder = builderFor(level, hall, BlueprintOutline.bounds(placement, next.get().size()).getCenter());
		if (builder.isEmpty()) {
			return Outcome.NO_BUILDER;
		}
		Villager villager = builder.get();
		UUID owner = entity.owner() != null ? entity.owner() : player.getUUID();
		String ownerName = entity.owner() != null ? entity.ownerName() : player.getGameProfile().getName();
		boolean busy = Builders.activeSite(level, villager) != null;
		if (busy) {
			Builders.enqueue(level, villager, owner, ownerName, upgrade.next(), placement);
		} else {
			Builders.start(level, villager, owner, ownerName, upgrade.next(), placement);
		}
		Chronicle.record(level, hall, Chronicle.Kind.PLANS, Component.translatable("chronicle.aliveworkplace.upgrade_ordered",
			player.getDisplayName(), villager.getDisplayName(), Blueprints.displayName(upgrade.next()), Blueprints.displayName(upgrade.built().structure())));
		return busy ? Outcome.QUEUED : Outcome.STARTED;
	}

	/** The village's Builder for a build centred on {@code centre}: the nearest idle one whose bench reaches it, else the one with the shortest queue. */
	static Optional<Villager> builderFor(ServerLevel level, BlockPos hall, BlockPos centre) {
		Villager best = null;
		int bestQueue = Integer.MAX_VALUE;
		List<Villager> builders = level.getEntities(EntityType.VILLAGER, new AABB(hall).inflate(VillageHalls.RADIUS),
				v -> v.isAlive() && Builders.isBuilder(v) && Builders.benchPos(v).isPresent()).stream()
			.sorted(Comparator.comparingDouble(v -> v.distanceToSqr(centre.getX(), centre.getY(), centre.getZ()))).toList();
		for (Villager v : builders) {
			BlockPos bench = Builders.benchPos(v).orElseThrow();
			if (!io.github.jcondedata.aliveworkplace.work.Reach.inReach(level, bench, centre, Builders.MAX_SITE_DISTANCE)) {
				continue;
			}
			int queue = Builders.activeSite(level, v) == null ? 0 : 1 + Builders.queue(level, v).size();
			if (queue <= Builders.MAX_QUEUE && queue < bestQueue) {
				best = v;
				bestQueue = queue;
			}
		}
		return Optional.ofNullable(best);
	}

	// --- The page -------------------------------------------------------------------------------------------------

	static ItemStack tab(ServerLevel level, BlockPos hall) {
		int count = VillageAdvice.upgradable(level, hall).size(); // the tab only counts: the space is checked on the page
		ItemStack stack = new ItemStack(Items.BRICKS, Math.max(1, Math.min(64, count)));
		return VillageHallScreen.icon(stack, Component.translatable("screen.aliveworkplace.upgrades"), ChatFormatting.GOLD,
			VillageHallScreen.line(Words.counted("screen.aliveworkplace.upgrades.count", count, count), count > 0 ? ChatFormatting.WHITE : ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.upgrades.hint", ChatFormatting.DARK_GRAY));
	}

	static ItemStack header(ServerLevel level, BlockPos hall) {
		return VillageHallScreen.icon(Items.BRICKS, Component.translatable("screen.aliveworkplace.upgrades.title", VillageHalls.name(level, hall)),
			ChatFormatting.GOLD, VillageHallScreen.line("screen.aliveworkplace.upgrades.about", ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.upgrades.space", ChatFormatting.DARK_GRAY));
	}

	/** The page: a button per build with a next level, nearest first; with more than fit, the last slot says how many more. */
	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer viewer) {
		List<Upgrade> upgrades = upgrades(level, hall);
		if (upgrades.isEmpty()) {
			menu.button(VillageHallScreen.FIRST_ROW + 4, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.upgrades.none"),
				ChatFormatting.GRAY, VillageHallScreen.line("screen.aliveworkplace.upgrades.none_hint", ChatFormatting.DARK_GRAY)), null);
			return;
		}
		int fit = upgrades.size() > SHOWN ? SHOWN - 1 : SHOWN;
		int slot = VillageHallScreen.FIRST_ROW;
		for (Upgrade upgrade : upgrades.subList(0, Math.min(fit, upgrades.size()))) {
			BlueprintData.Placement placement = upgrade.built().placement();
			menu.button(slot++, icon(level, hall, upgrade), p -> pick(menu, level, hall, placement, p));
		}
		if (upgrades.size() > fit) {
			menu.button(ChoiceMenu.SIZE - 1, VillageHallScreen.icon(Items.PAPER, Component.translatable("screen.aliveworkplace.upgrades.more", upgrades.size() - fit),
				ChatFormatting.GRAY), null);
		}
	}

	/** A build as the page shows it: its name, its level and the next, where, the cost, and the space (free, or what's in the way). */
	public static ItemStack icon(ServerLevel level, BlockPos hall, Upgrade upgrade) {
		ResourceLocation id = upgrade.built().structure();
		int tier = BlueprintUpgrades.tier(id);
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.upgrades.level", roman(tier), roman(tier + 1),
			Blueprints.displayName(upgrade.next())), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line(VillageHallScreen.where(hall, upgrade.built().placement().origin()), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line(cost(upgrade), ChatFormatting.WHITE));
		Item item;
		ChatFormatting color;
		if (upgrade.building()) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.upgrades.building", ChatFormatting.AQUA));
			item = Items.SCAFFOLDING;
			color = ChatFormatting.AQUA;
		} else if (upgrade.blocker() != null) {
			lore.add(VillageHallScreen.line(blocked(upgrade.blocker()), ChatFormatting.RED));
			item = Items.BARRIER;
			color = ChatFormatting.RED;
		} else {
			lore.add(VillageHallScreen.line(upgrade.toClear() > 0
				? Words.counted("screen.aliveworkplace.upgrades.free_clear", upgrade.toClear(), upgrade.toClear())
				: Component.translatable("screen.aliveworkplace.upgrades.free"), ChatFormatting.GREEN));
			lore.add(VillageHallScreen.line("screen.aliveworkplace.upgrades.click", ChatFormatting.DARK_GRAY));
			item = Items.BRICKS;
			color = ChatFormatting.GREEN;
		}
		return VillageHallScreen.icon(item, Blueprints.displayName(id).copy(), color, lore.toArray(Component[]::new));
	}

	/** "Costs 120 blocks: 64 Cobblestone, 30 Oak Planks, 12 Glass", or that it costs nothing more. */
	static Component cost(Upgrade upgrade) {
		if (upgrade.cost().isEmpty()) {
			return Component.translatable("screen.aliveworkplace.upgrades.cost.none");
		}
		MutableComponent list = Component.empty();
		List<Map.Entry<Item, Integer>> shown = upgrade.cost().subList(0, Math.min(MATERIALS, upgrade.cost().size()));
		for (int i = 0; i < shown.size(); i++) {
			if (i > 0) {
				list.append(Component.literal(", "));
			}
			list.append(Component.translatable("screen.aliveworkplace.upgrades.item", shown.get(i).getValue(), shown.get(i).getKey().getDescription()));
		}
		if (upgrade.cost().size() > MATERIALS) {
			return Component.translatable("screen.aliveworkplace.upgrades.cost.more", upgrade.total(), list);
		}
		return Words.counted("screen.aliveworkplace.upgrades.cost", upgrade.total(), upgrade.total(), list);
	}

	/** "In the way: Oak Fence at 12 64 -30", or "In the way: the Smithy's Stone Bricks at ...". */
	public static Component blocked(Blocker blocker) {
		BlockPos p = blocker.pos();
		Component block = blocker.state().getBlock().getName();
		return blocker.building() != null
			? Component.translatable("screen.aliveworkplace.upgrades.blocked_by", blocker.building(), block, p.getX(), p.getY(), p.getZ())
			: Component.translatable("screen.aliveworkplace.upgrades.blocked", block, p.getX(), p.getY(), p.getZ());
	}

	/** I, II, III ... X; past that the number. */
	static String roman(int n) {
		String[] numerals = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 1 && n <= numerals.length ? numerals[n - 1] : Integer.toString(n);
	}

	/** A build picked: its next level ordered, or why not; the page again, the row now under way. */
	static void pick(ChoiceMenu menu, ServerLevel level, BlockPos hall, BlueprintData.Placement placement, ServerPlayer p) {
		Optional<Upgrade> before = at(level, hall, placement);
		Outcome outcome = order(level, hall, p, placement);
		Component next = before.map(u -> Blueprints.displayName(u.next())).orElse(Component.empty());
		Component old = before.map(u -> Blueprints.displayName(u.built().structure())).orElse(Component.empty());
		Component text = switch (outcome) {
			case STARTED -> Component.translatable("message.aliveworkplace.upgrades.started", builderName(level, placement), next, old);
			case QUEUED -> Component.translatable("message.aliveworkplace.upgrades.queued", builderName(level, placement), next, old);
			case BLOCKED -> Component.translatable("message.aliveworkplace.upgrades.blocked", next,
				before.map(u -> u.blocker() != null ? blocked(u.blocker()) : Component.empty()).orElse(Component.empty()));
			case BUILDING -> Component.translatable("message.aliveworkplace.upgrades.building", next);
			case NOT_ALLOWED -> Component.translatable("message.aliveworkplace.upgrades.not_allowed");
			case NO_BUILDER -> Component.translatable("message.aliveworkplace.upgrades.no_builder", next);
			case GONE -> Component.translatable("message.aliveworkplace.upgrades.gone");
		};
		Chat.actionBar(p, text.copy().withStyle(outcome.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
		level.playSound(null, p.blockPosition(), outcome.ok() ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8f, 1f);
		backToList(menu, level, hall, p);
	}

	/** The name of the Builder now set to the site on {@code placement}. */
	private static Component builderName(ServerLevel level, BlueprintData.Placement placement) {
		for (BuildSite site : BuildSiteManager.get(level).all()) {
			if (!site.isDone() && site.placement().equals(placement) && site.builder() != null
				&& level.getEntity(site.builder()) instanceof Villager v) {
				return v.getDisplayName();
			}
		}
		return Component.translatable("message.aliveworkplace.status.builder");
	}

	private static void backToList(ChoiceMenu menu, ServerLevel level, BlockPos hall, ServerPlayer p) {
		HallPages.Page shown = page;
		if (shown == null) {
			for (HallPages.Page each : HallPages.all()) {
				if (each.id().equals(PAGE)) {
					shown = each;
				}
			}
		}
		if (shown != null) {
			VillageHallScreen.renderPage(menu, level, hall, shown, p);
		} else {
			VillageHallScreen.showList(menu, level, hall, 0);
		}
		menu.broadcastChanges();
	}

	private UpgradesPage() {
	}
}

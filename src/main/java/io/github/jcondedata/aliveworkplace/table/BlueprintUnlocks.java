package io.github.jcondedata.aliveworkplace.table;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.wonder.WonderBlueprints;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * The Blueprint Table as a collection (ROADMAP 26.10). Each village (its Village Hall) keeps the builds its library has
 * unlocked; the Table hands out only those. The rest show greyed out with how to get them, and the rare ones
 * ({@code rare.json}, the builder-only grander homes, the Wonders) as a dark "???" with nothing about them sent.
 *
 * <ul>
 * <li>Every village starts with {@code data/<ns>/blueprint_unlocks/starting.json}. A village from before 26.10 is
 * seeded on first use with everything it has standing or under construction.</li>
 * <li>Builds unlock when bought from any villager or trader, when a builder of the village levels up
 * ({@link #PER_LEVEL} each time), with {@code /workplace unlock} (the questbook's rewards), and when a Blueprint (found
 * in a chest, or had any other way) or a Wonder Plan is used on the Table.</li>
 * <li>Imports, uploads, scans and any build not of this mod are always unlocked; expansion gates still apply on top.</li>
 * <li>A Table far from any hall uses one server-wide set (seeded into the first hall that asks).</li>
 * </ul>
 */
public final class BlueprintUnlocks implements ResourceManagerReloadListener {
	/** The {@code blueprintUnlocks} switch; off, every build is unlocked as before 26.10. */
	public static volatile boolean ENABLED = true;
	/** Builds a village unlocks each time one of its builders levels up. */
	public static final int PER_LEVEL = 3;
	public static final String FOLDER = "blueprint_unlocks";

	/** Where a build stands in a village's library. The ordinals are what {@link TablePayloads.Entry#state()} carries. */
	public enum State {
		UNLOCKED, LOCKED, HIDDEN,
		/** A Wonder the village knows of: named and previewed, but raised from its Wonder Plan, not handed out. */
		VIEW_ONLY
	}

	private static final ResourceLocation STARTER = StarterBlueprints.STARTER_COTTAGE.id();
	private static volatile Set<ResourceLocation> starting = Set.of(STARTER);
	private static volatile Set<ResourceLocation> rare = Set.of();

	/** The levels a builder sells a build at ({@code ModTrades}): "Sold by a Journeyman Builder". */
	private static final Map<ResourceLocation, Integer> BUILDER_SALES = Map.of(
		StarterBlueprints.MARKET_STALL.id(), 2, StarterBlueprints.LOOKOUT_TOWER.id(), 3,
		StarterBlueprints.HEALING_CENTER.id(), 4, StarterBlueprints.SUPPLY_SHOP.id(), 5);
	/** Sold by the villager whose job it is for ({@code ModTrades}, the luxury workshops' Journeymen). */
	private static final Set<ResourceLocation> VILLAGER_SALES = ids(Stream.of(
		Stream.of(StarterBlueprints.STOREHOUSE, StarterBlueprints.RESEARCH_LAB, StarterBlueprints.BERRY_FARM, StarterBlueprints.BERRY_NURSERY,
			StarterBlueprints.CAMP_KITCHEN, StarterBlueprints.HABITAT_GARDEN, StarterBlueprints.GEM_GROTTO, StarterBlueprints.DAYCARE,
			StarterBlueprints.ARENA, StarterBlueprints.WINERY, StarterBlueprints.TAILORS_SHOP, StarterBlueprints.PRINT_SHOP,
			StarterBlueprints.JEWELLERS_WORKSHOP),
		StarterBlueprints.POKEMON_CENTER_LOOKS.stream()).flatMap(s -> s));
	/**
	 * A questbook reward ({@code tools/questbook/chapters.py}): {@code /workplace unlock}. The home and hall quests unlock
	 * the next level; generate.py adds the builds the story's main line asks for that start locked, and its check fails
	 * if a quest unlocks a build missing here.
	 */
	public static final Set<ResourceLocation> QUESTBOOK = ids(Stream.of(StarterBlueprints.STARTER_COTTAGE_2, StarterBlueprints.STARTER_COTTAGE_3,
		StarterBlueprints.STONE_HOUSE_2, StarterBlueprints.STONE_HOUSE_3, StarterBlueprints.TERRACE_2,
		StarterBlueprints.VILLAGE_HALL_2, StarterBlueprints.VILLAGE_HALL_3,
		StarterBlueprints.MARKET_SQUARE, StarterBlueprints.BARRACKS, StarterBlueprints.GATEHOUSE, StarterBlueprints.GUILDHALL,
		StarterBlueprints.MANOR));
	/** The mod's own builds: the only ones that can be locked. */
	private static final Set<ResourceLocation> MANAGED = ids(Stream.of(StarterBlueprints.ALL, StarterBlueprints.ONE_TIER, StarterBlueprints.WORKPLACES,
		StarterBlueprints.COBBLEMON_ONLY, StarterBlueprints.COBBLEMON_WORKPLACES, StarterBlueprints.JOB_BUILDS, StarterBlueprints.DEFENCES,
		StarterBlueprints.DECORATIONS, StarterBlueprints.MEGA_ONLY, StarterBlueprints.SOLD_ONLY).flatMap(List::stream));

	private static Set<ResourceLocation> ids(Stream<StarterBlueprints.Entry> entries) {
		return entries.map(StarterBlueprints.Entry::id).collect(java.util.stream.Collectors.toUnmodifiableSet());
	}

	public static void init() {
		Platform.get().onDataReload(AliveWorkplace.id(FOLDER), new BlueprintUnlocks());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Set<ResourceLocation> start = read(manager, "starting");
		starting = start.isEmpty() ? Set.of(STARTER) : start;
		rare = read(manager, "rare");
	}

	/** Every {@code <ns>:blueprint_unlocks/<name>.json}'s {@code blueprints}, together. */
	private static Set<ResourceLocation> read(ResourceManager manager, String name) {
		Set<ResourceLocation> out = new LinkedHashSet<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().equals(FOLDER + "/" + name + ".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				JsonElement json = JsonParser.parseReader(reader);
				if (json instanceof JsonObject object && object.has("blueprints")) {
					for (JsonElement id : object.getAsJsonArray("blueprints")) {
						ResourceLocation parsed = ResourceLocation.tryParse(id.getAsString());
						if (parsed != null) {
							out.add(parsed);
						} else {
							AliveWorkplace.LOG.warn("{}: '{}' isn't a blueprint id", e.getKey(), id.getAsString());
						}
					}
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return Collections.unmodifiableSet(out);
	}

	/** The builds every village starts with. */
	public static Set<ResourceLocation> starting() {
		return starting;
	}

	/** Whether {@code id} can be locked at all: one of the mod's own builds (any style of it) or a Wonder. */
	public static boolean managed(ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.base(id);
		return MANAGED.contains(base) || WonderBlueprints.wonderOf(base).isPresent();
	}

	/** Whether a locked {@code id} shows as "???": in {@code rare.json}, a builder-only grander home, or a Wonder. */
	public static boolean rare(ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.base(id);
		return rare.contains(base) || BlueprintLibrary.soldOnly(base) || WonderBlueprints.wonderOf(base).isPresent();
	}

	// --- the village's set -------------------------------------------------------------------

	/** The hall whose library a Table (or anything else) at {@code pos} uses, or null for the server-wide set. */
	@Nullable
	public static BlockPos village(ServerLevel level, BlockPos pos) {
		return VillageHalls.nearest(level, pos).orElse(null);
	}

	/** What the village round {@code hall} (null: no village) has unlocked, seeding an old village first. */
	public static Set<ResourceLocation> unlocked(ServerLevel level, @Nullable BlockPos hall) {
		if (hall != null && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			seed(level, hall, entity);
			return entity.blueprintUnlocks();
		}
		return Collections.unmodifiableSet(Wild.get(level.getServer()).ids);
	}

	/**
	 * A village's first use of its library (a save from before 26.10, or a new hall): the starting set, every build of
	 * the mod finished round the hall or being built there, and what the server-wide set had.
	 */
	private static void seed(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		if (entity.blueprintUnlocksSeeded()) {
			return;
		}
		Set<ResourceLocation> ids = new LinkedHashSet<>(starting);
		BuildSiteManager sites = BuildSiteManager.get(level);
		int radius = VillageHalls.RADIUS;
		for (BuildSiteManager.Finished f : sites.finishedNear(level, hall, radius)) {
			ids.add(BlueprintStyles.base(f.structure()));
		}
		ResourceLocation dimension = Ids.of(level.dimension());
		for (BuildSite site : sites.all()) {
			if (site.placement().dimension().equals(dimension) && site.placement().origin().distSqr(hall) <= (double) radius * radius) {
				ids.add(BlueprintStyles.base(site.structure()));
			}
		}
		ids.addAll(Wild.get(level.getServer()).ids);
		ids.removeIf(id -> !managed(id));
		entity.seedBlueprintUnlocks(ids);
	}

	/** Where {@code id} stands in the library of the village round {@code hall} (null: no village). */
	public static State state(ServerLevel level, @Nullable BlockPos hall, ResourceLocation id) {
		if (!ENABLED || !managed(id)) {
			return State.UNLOCKED;
		}
		ResourceLocation base = BlueprintStyles.base(id);
		boolean open = starting.contains(base) || unlocked(level, hall).contains(base);
		if (WonderBlueprints.wonderOf(base).isPresent()) {
			return open ? State.VIEW_ONLY : State.HIDDEN;
		}
		return open ? State.UNLOCKED : rare(base) ? State.HIDDEN : State.LOCKED;
	}

	/**
	 * Unlocks {@code id} in the village round {@code hall} (null: the server-wide set) and tells {@code tell} so ("The
	 * village's library has a new blueprint: Smithy II"). Whether it was locked.
	 */
	public static boolean unlock(ServerLevel level, @Nullable BlockPos hall, ResourceLocation id, Collection<ServerPlayer> tell) {
		State state = state(level, hall, id);
		if (state != State.LOCKED && state != State.HIDDEN) {
			return false;
		}
		ResourceLocation base = BlueprintStyles.base(id);
		if (hall != null && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.unlockBlueprint(base);
		} else {
			Wild wild = Wild.get(level.getServer());
			wild.ids.add(base);
			wild.setDirty();
		}
		Component line = Component.translatable("message.aliveworkplace.blueprint_unlocks.new", Blueprints.displayName(base)).withStyle(ChatFormatting.GOLD);
		for (ServerPlayer player : tell) {
			Chat.chat(player, line);
		}
		return true;
	}

	/** {@link #unlock} in the village nearest {@code pos}, telling {@code player} (if any). */
	public static boolean unlockAt(ServerLevel level, BlockPos pos, ResourceLocation id, @Nullable ServerPlayer player) {
		return unlock(level, village(level, pos), id, player == null ? List.of() : List.of(player));
	}

	// --- ways to unlock ----------------------------------------------------------------------

	/** A villager's or trader's sale (any merchant: builders, market-day traders): a Blueprint bought unlocks its build. */
	public static void onSale(ServerLevel level, BlockPos merchant, ItemStack sold, @Nullable ServerPlayer buyer) {
		if (ENABLED) {
			BlueprintItem.data(sold).ifPresent(d -> unlockAt(level, merchant, d.structure(), buyer));
		}
	}

	/** A Blueprint (found in a chest or had any other way) or a Wonder Plan used on a Table: its build unlocks there. */
	public static boolean useOnTable(ServerLevel level, BlockPos table, ItemStack stack, ServerPlayer player) {
		if (!ENABLED) {
			return false;
		}
		Optional<ResourceLocation> id = BlueprintItem.data(stack).map(d -> d.structure())
			.or(() -> io.github.jcondedata.aliveworkplace.wonder.WonderPlanItem.plan(stack).map(p -> WonderBlueprints.idOf(p.wonder())));
		return id.isPresent() && unlockAt(level, table, id.get(), player);
	}

	/** A builder of the village levelled up: the next {@link #PER_LEVEL} locked builds (not the rare ones) unlock. */
	public static void onLevelUp(Villager villager) {
		if (!ENABLED || villager.getVillagerData().getProfession() != ModVillagers.BUILDER || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		BlockPos hall = village(level, villager.blockPosition());
		BlockPos centre = hall != null ? hall : villager.blockPosition();
		List<ServerPlayer> near = level.getPlayers(p -> p.blockPosition().distSqr(centre) <= (double) VillageHalls.RADIUS * VillageHalls.RADIUS);
		for (ResourceLocation id : nextForLevelUp(level, hall)) {
			unlock(level, hall, id, near);
		}
	}

	/** What a builder's next level-up unlocks: the locked builds the Table lists, lowest tier first, then by name. */
	public static List<ResourceLocation> nextForLevelUp(ServerLevel level, @Nullable BlockPos hall) {
		List<ResourceLocation> locked = new ArrayList<>();
		for (ResourceLocation id : BlueprintLibrary.list(level.getServer(), false)) {
			if (state(level, hall, id) == State.LOCKED) {
				locked.add(id);
			}
		}
		locked.sort(Comparator.comparingInt(BlueprintUpgrades::tier).thenComparing(ResourceLocation::toString));
		return locked.subList(0, Math.min(PER_LEVEL, locked.size()));
	}

	/** The rare builds the Table lists only as "???" (or by name once unlocked): the builder-only homes and the Wonders. */
	public static List<ResourceLocation> rareExtras(MinecraftServer server) {
		List<ResourceLocation> out = new ArrayList<>();
		for (StarterBlueprints.Entry e : StarterBlueprints.SOLD_ONLY) {
			if (StarterBlueprints.offered(e.id())) {
				out.add(e.id());
			}
		}
		for (var wonder : io.github.jcondedata.aliveworkplace.wonder.Wonders.all()) {
			out.add(WonderBlueprints.idOf(wonder.id()));
		}
		return out;
	}

	/** The lang key of the line that says how to get {@code id} ("Sold by a Journeyman Builder"). */
	public static String hint(ResourceLocation id) {
		ResourceLocation base = BlueprintStyles.base(id);
		String key = "screen.aliveworkplace.table.hint.";
		if (WonderBlueprints.wonderOf(base).isPresent()) {
			return key + "wonder";
		}
		// A rare build the questbook's main line unlocks (the Market Square) says so, still as a "???".
		if (QUESTBOOK.contains(base) && Platform.get().isModLoaded("ftbquests") && !BUILDER_SALES.containsKey(base)) {
			return key + "questbook";
		}
		if (rare(base)) {
			return key + "rare";
		}
		Integer level = BUILDER_SALES.get(base);
		if (level != null) {
			return key + "builder." + level;
		}
		if (BlueprintUpgrades.tier(base) >= 2) {
			return key + "upgrade";
		}
		if (VILLAGER_SALES.contains(base)) {
			return key + "villager";
		}
		if (StarterBlueprints.DECORATIONS.stream().anyMatch(e -> e.id().equals(base))
			|| StarterBlueprints.ALL.stream().anyMatch(e -> e.id().equals(base))) {
			return key + "market";
		}
		return key + "chests";
	}

	/** The unlocks of Tables far from any hall: one set for the server, saved with the overworld. */
	static final class Wild extends SavedData {
		static final String NAME = "aliveworkplace_blueprint_unlocks";
		final Set<ResourceLocation> ids = new LinkedHashSet<>();

		static Wild get(MinecraftServer server) {
			return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(Wild::new, Wild::load, null), NAME);
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			ids.forEach(id -> list.add(StringTag.valueOf(id.toString())));
			tag.put("ids", list);
			return tag;
		}

		static Wild load(CompoundTag tag, HolderLookup.Provider registries) {
			Wild wild = new Wild();
			ListTag list = Nbt.getList(tag, "ids", Tag.TAG_STRING);
			for (int i = 0; i < list.size(); i++) {
				ResourceLocation id = ResourceLocation.tryParse(Nbt.stringAt(list, i));
				if (id != null) {
					wild.ids.add(id);
				}
			}
			return wild;
		}
	}

	private BlueprintUnlocks() {
	}
}

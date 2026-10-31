package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLooks;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.work.Extension;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * A waystone by every Village Hall (ROADMAP 41.1). With the Waystones mod installed (and {@code villageWaystones} on),
 * the Steward proposes the Waystone Plaza ({@link #PLAZA}) once the village has a hall: a small paved plaza round the
 * mod's own waystone block, drawn in each village culture's style ({@link #LOOKS}: plains as drawn, the others as
 * looks) and built by the village's builder like any build. The stone is named after the village: when the builder
 * sets it down ({@link #placed}), at each hall round ({@link #round}: the hall's name may have come from an anvil while
 * the stone's chunk slept), and the moment the hall is renamed ({@link #renamed}). A stone that's broken is a hole in a
 * finished build, which the builder's upkeep puts back like any other; the new stone is registered and named again.
 *
 * <p>Everything that touches the Waystones mod is the {@link #EXTENSION}, filled by {@code compat/waystones} only when
 * the mod is installed: without it nothing here is offered and nothing runs. The waystone blocks themselves are known
 * by id ({@link #isStone}: {@code waystones:*waystone}), so the core never loads a Waystones class.
 *
 * <p>The waystone network (ROADMAP 41.2): each stone a village raises is kept ({@link Data}, {@link #stoneOf}), and the
 * hall's Map lists the owner's villages that have one ({@link #network}, {@link #mapLines}), with distance and direction,
 * marked discovered by the player looking or not, or broken.
 *
 * <p>Travel by waystone (ROADMAP 41.3): between two villages whose stones both stand ({@link #linked}), a trade caravan
 * walks to its village's stone and steps out at the other's ({@link Caravans}, {@link CaravanSights}), and settlers for a
 * colony go through to the village whose stone stands nearest the spot and walk on from there
 * ({@code colony/Settlers}), with the mod's own teleport sound and particles ({@link #warp}). A stone found broken when
 * they step out: they walk the road as before.
 */
public final class VillageWaystones {
	/** The config's {@code villageWaystones}. */
	public static boolean ENABLED = true;
	/** The Waystones mod's id. */
	public static final String MOD = "waystones";
	/** The plaza, plains as drawn. */
	public static final ResourceLocation PLAZA = AliveWorkplace.id("waystone_plaza");
	/** The plaza's looks besides plains (blueprint looks, {@code looks/<culture>/waystone_plaza}): one per village culture. */
	public static final List<String> LOOKS = List.of("desert", "savanna", "snowy", "taiga");

	/** What the Waystones mod does for the village (compat/waystones). */
	public interface Stones {
		/**
		 * Makes the waystone whose lower half is at {@code pos} a working waystone of the mod's (registered, so players
		 * can discover it and travel to it) named {@code name}. True when it is one now.
		 */
		boolean raise(ServerLevel level, BlockPos pos, String name);

		/** The name of the working waystone whose lower half is at {@code pos}, or empty when there's none there. */
		Optional<String> nameAt(ServerLevel level, BlockPos pos);

		/**
		 * Whether a working waystone of the mod's stands with its lower half at {@code pos} (41.2): asked of the block
		 * where its chunk is loaded, of the mod's saved list of waystones where it isn't. False once the stone is broken.
		 */
		boolean standing(ServerLevel level, BlockPos pos);

		/** Whether {@code player} has discovered (activated) the working waystone whose lower half is at {@code pos} (41.2). */
		boolean discovered(ServerPlayer player, ServerLevel level, BlockPos pos);

		/**
		 * The mod's own teleport sound and particles at the working waystone whose lower half is at {@code pos} (41.3):
		 * a caravan or the settlers going through it. Nothing by default.
		 */
		default void warp(ServerLevel level, BlockPos pos) {
		}
	}

	public static final Extension<Stones> EXTENSION = new Extension<>("village waystones");

	/** Whether the plaza is offered: Waystones installed and {@code villageWaystones} on. */
	public static boolean offered() {
		return ENABLED && EXTENSION.present();
	}

	/** Whether {@code id} is the plaza in any culture's look. */
	public static boolean isPlaza(ResourceLocation id) {
		return BlueprintLooks.base(id).equals(PLAZA);
	}

	/** The village culture at {@code hall}: the vanilla village its biome grows ({@code plains} when none). */
	public static String culture(ServerLevel level, BlockPos hall) {
		var biome = level.getBiome(hall);
		if (biome.is(BiomeTags.HAS_VILLAGE_DESERT)) {
			return "desert";
		}
		if (biome.is(BiomeTags.HAS_VILLAGE_SAVANNA)) {
			return "savanna";
		}
		if (biome.is(BiomeTags.HAS_VILLAGE_SNOWY)) {
			return "snowy";
		}
		if (biome.is(BiomeTags.HAS_VILLAGE_TAIGA)) {
			return "taiga";
		}
		return "plains";
	}

	/** The plaza in {@code culture}'s look. */
	public static ResourceLocation plazaFor(String culture) {
		return LOOKS.contains(culture) ? BlueprintLooks.look(PLAZA, culture) : PLAZA;
	}

	/** The plazas the Steward tries at {@code hall}: the village's own culture first, then the others where it doesn't fit. */
	public static List<ResourceLocation> forVillage(ServerLevel level, BlockPos hall) {
		List<ResourceLocation> out = new ArrayList<>();
		out.add(plazaFor(culture(level, hall)));
		BlueprintLooks.of(PLAZA).stream().filter(l -> !out.contains(l)).forEach(out::add);
		return List.copyOf(out);
	}

	/** A waystone block's lower half (the mod's waystone in any of its styles, found by id). */
	public static boolean isStone(BlockState state) {
		ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
		return id.getNamespace().equals(MOD) && id.getPath().endsWith("waystone")
			&& state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
	}

	/** The village's name, as its stone carries it. */
	public static String stoneName(ServerLevel level, BlockPos hall) {
		return VillageHalls.name(level, hall).getString();
	}

	/** A builder set a block down: a waystone is made a working one named after the nearest village. */
	public static void placed(ServerLevel level, BlockPos pos, BlockState state) {
		if (!EXTENSION.present() || !isStone(state)) {
			return;
		}
		VillageHalls.nearest(level, pos).ifPresent(hall -> raise(level, pos, hall));
	}

	/** The hall was renamed: its stones take the new name now. */
	public static void renamed(ServerLevel level, BlockPos hall) {
		if (EXTENSION.present()) {
			round(level, hall);
		}
	}

	/** The hall's round: each stone of the village's plazas is a working waystone with the village's name. */
	public static void round(ServerLevel level, BlockPos hall) {
		if (!EXTENSION.present()) {
			return;
		}
		for (BlockPos stone : stones(level, hall)) {
			raise(level, stone, hall);
		}
		if (ownPlazas(level, hall).isEmpty()) {
			// the village's own plaza is gone, not only its stone: the village is off the network (41.2). A neighbor's
			// plaza within the hall radius doesn't keep it on (B528).
			Data.get(level).forget(hall);
		}
	}

	private static void raise(ServerLevel level, BlockPos stone, BlockPos hall) {
		String name = stoneName(level, hall);
		if (EXTENSION.call(s -> s.raise(level, stone, name), false)) {
			Data.get(level).put(hall, stone);
		}
	}

	/**
	 * Where the village's plaza stones stand that are there now: the waystone spots of its plazas (finished, or going up)
	 * whose nearest hall is {@code hall}, holding a waystone.
	 */
	public static List<BlockPos> stones(ServerLevel level, BlockPos hall) {
		List<BlockPos> out = new ArrayList<>();
		List<io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement> seen = new ArrayList<>();
		for (Plaza plaza : ownPlazas(level, hall)) {
			if (seen.contains(plaza.placement()) || !level.isLoaded(plaza.placement().origin())) {
				continue;
			}
			seen.add(plaza.placement());
			Optional<Blueprint> blueprint = BlueprintLibrary.get(level, plaza.structure());
			if (blueprint.isEmpty()) {
				continue;
			}
			BuildPlan plan = BuildPlan.create(blueprint.get(), plaza.placement());
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					if (isStone(step.state()) && isStone(level.getBlockState(step.pos())) && !out.contains(step.pos())) {
						out.add(step.pos());
					}
				}
			}
		}
		return out;
	}

	/**
	 * The village's own plazas: those round {@code hall}, finished or going up, loaded or not, whose nearest hall is
	 * {@code hall} (a neighbor village's plaza within the radius is the neighbor's, B528).
	 */
	private static List<Plaza> ownPlazas(ServerLevel level, BlockPos hall) {
		List<Plaza> out = new ArrayList<>();
		for (Plaza plaza : plazas(level, hall)) {
			if (VillageHalls.nearest(level, plaza.placement().origin()).map(hall::equals).orElse(false)) {
				out.add(plaza);
			}
		}
		return out;
	}

	/** The plazas round {@code hall}, finished or going up, loaded or not (their nearest hall not yet checked). */
	private static List<Plaza> plazas(ServerLevel level, BlockPos hall) {
		BuildSiteManager manager = BuildSiteManager.get(level);
		List<Plaza> plazas = new ArrayList<>();
		for (BuildSiteManager.Finished f : manager.finishedNear(level, hall, VillageHalls.RADIUS)) {
			if (isPlaza(f.structure())) {
				plazas.add(new Plaza(f.structure(), f.placement()));
			}
		}
		for (BuildSite site : manager.all()) {
			if (isPlaza(site.structure()) && site.placement().origin().distSqr(hall) <= (double) VillageHalls.RADIUS * VillageHalls.RADIUS) {
				plazas.add(new Plaza(site.structure(), site.placement()));
			}
		}
		return plazas;
	}

	// ---- travel by waystone (41.3) ----------------------------------------------------------------------------------------

	/**
	 * Whether caravans and settlers between the villages round {@code from} and {@code to} go by waystone (41.3): Waystones
	 * installed, {@code villageWaystones} on, and the stone each village raised standing now (broken or missing: they walk).
	 */
	public static boolean linked(ServerLevel level, BlockPos from, BlockPos to) {
		return offered() && !from.equals(to) && standing(level, from) && standing(level, to);
	}

	/** The villages of this dimension that have raised a waystone (41.3), their halls in no particular order. */
	public static List<BlockPos> villages(ServerLevel level) {
		return Data.get(level).halls();
	}

	/** The mod's teleport sound and particles at the stone of the village round {@code hall}, where it's loaded (41.3). */
	public static void warp(ServerLevel level, BlockPos hall) {
		stoneOf(level, hall).filter(level::isLoaded).ifPresent(stone -> EXTENSION.call(s -> {
			s.warp(level, stone);
			return true;
		}, false));
	}

	/**
	 * Where a traveler stands to step into the stone at {@code stone} (41.3): the nearest open spot beside it (footing, two
	 * blocks of room) nearest {@code toward}, else the stone's own spot.
	 */
	public static BlockPos beside(ServerLevel level, BlockPos stone, BlockPos toward) {
		BlockPos best = null;
		for (int r = 1; r <= 2 && best == null; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy : new int[] {0, -1, 1}) {
						BlockPos p = stone.offset(dx, dy, dz);
						if (open(level, p) && (best == null || p.distSqr(toward) < best.distSqr(toward))) {
							best = p;
						}
					}
				}
			}
		}
		return best != null ? best : stone;
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		if (!level.isLoaded(pos) || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
			return false;
		}
		for (BlockPos p : List.of(pos, pos.above())) {
			BlockState state = level.getBlockState(p);
			if (!state.getCollisionShape(level, p).isEmpty() || !state.getFluidState().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	// ---- the waystone network (41.2) -----------------------------------------------------------------------------------

	/** Lines the hall's Map lists at most, besides its heading: more villages are counted on one last line. */
	public static final int LISTED = 6;

	/** Where a village of the network stands with its waystone, for the player looking. */
	public enum State {
		/** The stone stands and the player has discovered it. */
		DISCOVERED,
		/** The stone stands; the player hasn't used it yet. */
		UNDISCOVERED,
		/** The plaza is there but its stone isn't: broken (the builder's upkeep puts it back). */
		BROKEN
	}

	/** A village with a waystone: its hall, name, stone (lower half), how it stands, and whether it's the village looked from. */
	public record Village(BlockPos hall, Component name, BlockPos stone, State state, boolean here) {
	}

	/** The stone the village round {@code hall} last raised (its lower half), loaded or not: empty when it never had one. */
	public static Optional<BlockPos> stoneOf(ServerLevel level, BlockPos hall) {
		return Data.get(level).stone(hall);
	}

	/** Whether the stone the village round {@code hall} raised stands now as a working waystone (false without Waystones). */
	public static boolean standing(ServerLevel level, BlockPos hall) {
		return stoneOf(level, hall).map(stone -> EXTENSION.call(s -> s.standing(level, stone), false)).orElse(false);
	}

	/**
	 * The waystone network seen from the hall at {@code hall} (41.2): the villages of the hall's owner (the viewer's own when
	 * the hall has none) in this dimension that have raised a waystone, this one first, then nearest first, each marked
	 * discovered or not by {@code viewer}, or broken. Unloaded villages count as their last hall round saw them.
	 */
	public static List<Village> network(ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer) {
		if (!EXTENSION.present()) {
			return List.of();
		}
		UUID owner = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e && e.owner() != null ? e.owner()
			: viewer == null ? null : viewer.getUUID();
		Map<BlockPos, Component> villages = new LinkedHashMap<>();
		villages.put(hall, VillageHalls.name(level, hall));
		if (owner != null) {
			for (RealmData.Known known : RealmData.get(level.getServer()).knownVillages()) {
				BlockPos at = known.hall().pos();
				if (known.hall().dimension().equals(level.dimension()) && known.owner().map(owner::equals).orElse(false) && !villages.containsKey(at)) {
					villages.put(at, known.name());
				}
			}
		}
		List<Village> out = new ArrayList<>();
		for (Map.Entry<BlockPos, Component> v : villages.entrySet()) {
			BlockPos at = v.getKey();
			if (level.isLoaded(at) && !(level.getBlockEntity(at) instanceof VillageHallBlockEntity)) {
				continue; // the hall is gone
			}
			Optional<BlockPos> stone = stoneOf(level, at);
			if (stone.isEmpty()) {
				continue;
			}
			BlockPos s = stone.get();
			State state = !EXTENSION.call(x -> x.standing(level, s), false) ? State.BROKEN
				: viewer != null && EXTENSION.call(x -> x.discovered(viewer, level, s), false) ? State.DISCOVERED : State.UNDISCOVERED;
			Component name = level.isLoaded(at) ? VillageHalls.name(level, at) : v.getValue();
			out.add(new Village(at, name, s, state, at.equals(hall)));
		}
		out.sort(Comparator.comparing((Village v) -> !v.here()).thenComparingDouble(v -> v.stone().distSqr(hall)));
		return out;
	}

	/**
	 * The hall's Map lines for the network (41.2): a heading, then a line for each village ("Ferncross, 120 blocks
	 * north-east: discovered", green; "not discovered yet", yellow; "its waystone is broken", red), at most {@link #LISTED},
	 * the rest counted. "None yet" when no village has a stone; nothing at all without Waystones.
	 */
	public static List<Component> mapLines(ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer) {
		if (!EXTENSION.present()) {
			return List.of();
		}
		List<Village> network = network(level, hall, viewer);
		List<Component> lines = new ArrayList<>();
		if (network.isEmpty()) {
			lines.add(VillageHallScreen.line("screen.aliveworkplace.hall.waystones_none", ChatFormatting.DARK_GRAY));
			return lines;
		}
		lines.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.waystones", network.size()), ChatFormatting.AQUA));
		for (int i = 0; i < Math.min(LISTED, network.size()); i++) {
			Village v = network.get(i);
			Component where = v.here() ? Component.translatable("screen.aliveworkplace.hall.waystone_here") : VillageHallScreen.where(hall, v.stone());
			String key = "screen.aliveworkplace.hall.waystone." + v.state().name().toLowerCase(Locale.ROOT);
			ChatFormatting color = switch (v.state()) {
				case DISCOVERED -> ChatFormatting.GREEN;
				case UNDISCOVERED -> ChatFormatting.YELLOW;
				case BROKEN -> ChatFormatting.RED;
			};
			lines.add(VillageHallScreen.line(Component.translatable(key, v.name(), where), color));
		}
		if (network.size() > LISTED) {
			lines.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.hall.waystones_more", network.size() - LISTED), ChatFormatting.GRAY));
		}
		return lines;
	}

	/**
	 * The stone each village raised (41.2), one dimension's: kept so the hall's Map can list villages whose chunks aren't
	 * loaded, and a broken stone stays on the list as broken. A village leaves it when its plaza is gone. New in 41.2:
	 * older worlds start empty and fill at the next hall rounds.
	 */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_village_waystones";
		private final Long2LongOpenHashMap stones = new Long2LongOpenHashMap();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		/** The stone kept for the village round {@code hall}. */
		public Optional<BlockPos> stone(BlockPos hall) {
			return stones.containsKey(hall.asLong()) ? Optional.of(BlockPos.of(stones.get(hall.asLong()))) : Optional.empty();
		}

		/** The halls of the villages kept (41.3). */
		public List<BlockPos> halls() {
			List<BlockPos> out = new ArrayList<>();
			for (long h : stones.keySet().toLongArray()) {
				out.add(BlockPos.of(h));
			}
			return out;
		}

		void put(BlockPos hall, BlockPos stone) {
			if (!stones.containsKey(hall.asLong()) || stones.get(hall.asLong()) != stone.asLong()) {
				stones.put(hall.asLong(), stone.asLong());
				setDirty();
			}
		}

		/** Takes the village round {@code hall} off the network (also for tests). */
		public void forget(BlockPos hall) {
			if (stones.containsKey(hall.asLong())) {
				stones.remove(hall.asLong());
				setDirty();
			}
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			long[] halls = new long[stones.size()];
			long[] spots = new long[stones.size()];
			int i = 0;
			for (Long2LongMap.Entry e : stones.long2LongEntrySet()) { // (one pass: both arrays in the same order)
				halls[i] = e.getLongKey();
				spots[i++] = e.getLongValue();
			}
			tag.putLongArray("halls", halls);
			tag.putLongArray("stones", spots);
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			long[] halls = Nbt.getLongArray(tag, "halls");
			long[] stones = Nbt.getLongArray(tag, "stones");
			for (int i = 0; i < Math.min(halls.length, stones.length); i++) {
				data.stones.put(halls[i], stones[i]);
			}
			return data;
		}
	}

	private record Plaza(ResourceLocation structure, io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement placement) {
	}

	private VillageWaystones() {
	}
}

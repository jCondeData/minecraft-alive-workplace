package io.github.jcondedata.aliveworkplace.hall;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLooks;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

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
	}

	private static void raise(ServerLevel level, BlockPos stone, BlockPos hall) {
		String name = stoneName(level, hall);
		EXTENSION.call(s -> s.raise(level, stone, name), false);
	}

	/**
	 * Where the village's plaza stones stand that are there now: the waystone spots of its plazas (finished, or going up)
	 * whose nearest hall is {@code hall}, holding a waystone.
	 */
	public static List<BlockPos> stones(ServerLevel level, BlockPos hall) {
		List<BlockPos> out = new ArrayList<>();
		BuildSiteManager manager = BuildSiteManager.get(level);
		List<io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement> seen = new ArrayList<>();
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
		for (Plaza plaza : plazas) {
			if (seen.contains(plaza.placement()) || !level.isLoaded(plaza.placement().origin())) {
				continue;
			}
			seen.add(plaza.placement());
			if (!VillageHalls.nearest(level, plaza.placement().origin()).map(hall::equals).orElse(false)) {
				continue;
			}
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

	private record Plaza(ResourceLocation structure, io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement placement) {
	}

	private VillageWaystones() {
	}
}

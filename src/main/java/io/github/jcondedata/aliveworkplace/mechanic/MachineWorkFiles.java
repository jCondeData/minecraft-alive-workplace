package io.github.jcondedata.aliveworkplace.mechanic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * The machine-work files (ROADMAP 39.3, design note M39): {@code data/<ns>/machine_work/<id>.json}, one per machine a
 * village job works. A datapack can add or replace them; a file whose {@code requires} mod isn't loaded, or whose machine
 * block, job, recipe type or made items don't exist, is skipped quietly (so without Create there are none).
 */
public final class MachineWorkFiles implements ResourceManagerReloadListener {
	static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(AliveWorkplace.MOD_ID, "machine_work");

	/** Where a machine's input goes or its output comes out: an offset from the machine, the block expected there, and
	 * whether items are dropped there (or picked up from the floor there) instead of put into an inventory. A feed spot
	 * may also take a fluid ({@code fluid}, {@code fluid_mb} millibuckets), poured into its tank. */
	public record Spot(BlockPos at, @Nullable Block block, boolean drop, @Nullable Fluid fluid, int fluidMb) {
	}

	/** What the village wants made and how many the store should keep. */
	public record Make(Item item, int keep) {
	}

	/** One machine-work file. */
	public record Machine(ResourceLocation id, Block machine, ResourceLocation job, ResourceLocation recipeType, Spot feed,
			Spot collect, List<Make> makes, int batch, float minSpeed) {
		/** Whether the store should get more of {@code item}. */
		public boolean makes(Item item) {
			return makes.stream().anyMatch(m -> m.item() == item);
		}
	}

	private static volatile List<Machine> files = List.of();

	public static void init() {
		Platform.get().onDataReload(ID, new MachineWorkFiles());
	}

	/** Every machine-work file loaded, by id. */
	public static List<Machine> all() {
		return files;
	}

	/** The file for the machine {@code state} is, or null. */
	@Nullable
	public static Machine forMachine(BlockState state) {
		for (Machine m : files) {
			if (state.is(m.machine())) {
				return m;
			}
		}
		return null;
	}

	/** The files whose machines the job {@code job} (a profession id) works. */
	public static List<Machine> forJob(ResourceLocation job) {
		return files.stream().filter(m -> m.job().equals(job)).toList();
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, Machine> out = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("machine_work", p -> p.getPath().endsWith(".json")).entrySet()) {
			String path = e.getKey().getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),
				path.substring("machine_work/".length(), path.length() - ".json".length()));
			try (Reader reader = e.getValue().openAsReader()) {
				Machine m = read(id, JsonParser.parseReader(reader).getAsJsonObject());
				if (m != null) {
					out.put(id, m);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.debug("Skipping machine work {}: {}", e.getKey(), ex.toString());
			}
		}
		files = List.copyOf(out.values());
		if (!files.isEmpty()) {
			AliveWorkplace.LOG.info("Villagers work {} kinds of machine", files.size());
		}
	}

	/** Reads one file; null when it's for a mod that isn't here or names something that doesn't exist. */
	@Nullable
	static Machine read(ResourceLocation id, JsonObject json) {
		if (json.has("requires") && !Platform.get().isModLoaded(json.get("requires").getAsString())) {
			return null;
		}
		Block machine = block(json.get("machine").getAsString());
		ResourceLocation job = ResourceLocation.parse(json.get("job").getAsString());
		ResourceLocation type = ResourceLocation.parse(json.get("recipe_type").getAsString());
		if (machine == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job) || !BuiltInRegistries.RECIPE_TYPE.containsKey(type)) {
			return null;
		}
		List<Make> makes = new ArrayList<>();
		for (JsonElement el : json.getAsJsonArray("makes")) {
			JsonObject o = el.getAsJsonObject();
			ResourceLocation item = ResourceLocation.parse(o.get("item").getAsString());
			if (BuiltInRegistries.ITEM.containsKey(item)) {
				makes.add(new Make(BuiltInRegistries.ITEM.get(item), o.has("keep") ? o.get("keep").getAsInt() : 64));
			}
		}
		if (makes.isEmpty()) {
			return null;
		}
		return new Machine(id, machine, job, type, spot(json.getAsJsonObject("feed")), spot(json.getAsJsonObject("collect")),
			List.copyOf(makes), Math.max(1, json.has("batch") ? json.get("batch").getAsInt() : 16),
			json.has("min_speed") ? json.get("min_speed").getAsFloat() : 1f);
	}

	private static Spot spot(@Nullable JsonObject json) {
		if (json == null) {
			return new Spot(BlockPos.ZERO, null, false, null, 0);
		}
		BlockPos at = BlockPos.ZERO;
		if (json.has("at")) {
			JsonArray a = json.getAsJsonArray("at");
			at = new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
		}
		Fluid fluid = null;
		if (json.has("fluid")) {
			ResourceLocation f = ResourceLocation.parse(json.get("fluid").getAsString());
			fluid = BuiltInRegistries.FLUID.containsKey(f) ? BuiltInRegistries.FLUID.get(f) : null;
			if (fluid == Fluids.EMPTY) {
				fluid = null;
			}
		}
		return new Spot(at, json.has("block") ? block(json.get("block").getAsString()) : null,
			json.has("drop") && json.get("drop").getAsBoolean(), fluid, json.has("fluid_mb") ? json.get("fluid_mb").getAsInt() : 1000);
	}

	@Nullable
	private static Block block(String id) {
		ResourceLocation rl = ResourceLocation.parse(id);
		return BuiltInRegistries.BLOCK.containsKey(rl) ? BuiltInRegistries.BLOCK.get(rl) : null;
	}

	private MachineWorkFiles() {
	}
}

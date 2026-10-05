package io.github.jcondedata.aliveworkplace.hall;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * Services nearby (ROADMAP 34.3, {@code docs/design/M34.md}): what a village offers its homes, read from
 * {@code data/<namespace>/services/<id>.json} (ours: chapel, school, clinic, library, market, tavern; a data pack replaces
 * one by path, adds its own, or switches ours off with {@code "enabled": false}). A service is given by a worker of one
 * of its {@code jobs} (where their job site is) or a finished build of one of its {@code blueprints} (styled builds and
 * upgrades count as their base), and reaches the homes within its {@code range} of it ({@code -1}: the whole village).
 *
 * <p>The hall works out where its services are once a day (the census's workers, the builds from
 * {@link BuildSiteManager#finishedNear}) and keeps the list (hall NBT {@code services}, {@code servicesDay}); the
 * {@code services} class need checks each home against that list, so a worker who quits stops counting at the next
 * dawn. No area scans per household.
 */
public final class Services implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("services");
	public static final String FOLDER = "services";

	/** A service a village can have. */
	public record Service(ResourceLocation id, Component name, List<ResourceLocation> jobs, List<ResourceLocation> blueprints, int range,
						  ResourceLocation icon) {
		public boolean villageWide() {
			return range < 0;
		}
	}

	/** Where one service was found at the hall's last daily count, and how far it reaches from there (-1: everywhere). */
	public record Found(ResourceLocation id, BlockPos pos, int range) {
		public static final Codec<Found> CODEC = RecordCodecBuilder.create(i -> i.group(
			ResourceLocation.CODEC.fieldOf("id").forGetter(Found::id),
			BlockPos.CODEC.fieldOf("pos").forGetter(Found::pos),
			Codec.INT.optionalFieldOf("range", 48).forGetter(Found::range)
		).apply(i, Found::new));

		public boolean reaches(@Nullable BlockPos home) {
			return range < 0 || home != null && pos.distSqr(home) <= (double) range * range;
		}
	}

	/** The hook {@link ClassNeeds#services} is set to: the services on the hall's list today that reach {@code home}. */
	public static final ClassNeeds.ServiceList HOOK = Services::reaching;

	private static volatile Map<ResourceLocation, Service> services = Map.of();
	/** How many times any hall has worked out its list (tests check it's at most once a day). */
	private static int countings;

	public static void init() {
		Platform.get().onDataReload(ID, new Services());
		ClassNeeds.services = HOOK;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(files(manager));
	}

	/** The service files {@code manager} holds (the top data pack's of each), by service id; an unreadable one is skipped with a warning. */
	public static Map<ResourceLocation, JsonElement> files(ResourceManager manager) {
		Map<ResourceLocation, JsonElement> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				String path = e.getKey().getPath();
				files.put(ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring(FOLDER.length() + 1, path.length() - ".json".length())),
					JsonParser.parseReader(reader));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping service {}: {}", e.getKey(), ex.getMessage());
			}
		}
		return files;
	}

	/** Reads every service file; a broken one is skipped with a warning naming it, one switched off is left out. */
	public static void load(Map<ResourceLocation, JsonElement> files) {
		Map<ResourceLocation, Service> out = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, JsonElement> e : new TreeMap<>(files).entrySet()) {
			try {
				Service s = read(e.getKey(), e.getValue());
				if (s != null) {
					out.put(s.id(), s);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping service {}: {}", e.getKey(), ex.getMessage());
			}
		}
		services = Map.copyOf(out);
	}

	/** Reads one service; null if it is switched off ({@code "enabled": false}); throws naming the bad field. */
	@Nullable
	public static Service read(ResourceLocation id, JsonElement json) {
		JsonObject o = GsonHelper.convertToJsonObject(json, "service");
		if (!GsonHelper.getAsBoolean(o, "enabled", true)) {
			return null;
		}
		Component name = o.has("name")
			? ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, o.get("name")).getOrThrow(m -> new JsonSyntaxException("name: " + m))
			: Component.translatable("service." + id.getNamespace() + "." + id.getPath());
		List<ResourceLocation> jobs = ids(o, "jobs");
		List<ResourceLocation> blueprints = ids(o, "blueprints");
		if (jobs.isEmpty() && blueprints.isEmpty()) {
			throw new JsonSyntaxException("expected \"jobs\" or \"blueprints\"");
		}
		int range = GsonHelper.getAsInt(o, "range", 48);
		if (range < -1) {
			throw new JsonSyntaxException("range: " + range + " is below -1 (the whole village)");
		}
		ResourceLocation icon = ClassNeeds.id(GsonHelper.getAsString(o, "icon", "minecraft:bell"), "icon");
		return new Service(id, name, jobs, blueprints, range, icon);
	}

	private static List<ResourceLocation> ids(JsonObject o, String field) {
		List<ResourceLocation> out = new ArrayList<>();
		for (JsonElement e : GsonHelper.getAsJsonArray(o, field, new com.google.gson.JsonArray())) {
			String text = GsonHelper.convertToString(e, field);
			ResourceLocation id = ResourceLocation.tryParse(text.indexOf(':') >= 0 ? text : AliveWorkplace.MOD_ID + ":" + text);
			if (id == null) {
				throw new JsonSyntaxException(field + ": not an id: " + text);
			}
			out.add(id);
		}
		return List.copyOf(out);
	}

	/** The services there are, by id. */
	public static Map<ResourceLocation, Service> all() {
		return services;
	}

	/** How many times a hall has worked out its list since the game started (tests). */
	public static int countings() {
		return countings;
	}

	/** The hall's round: works today's list out, if it hasn't yet, from the census's workers (no extra entity lookup). */
	public static void round(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, List<Villager> workers) {
		if (SocialClasses.ENABLED) {
			listOf(level, hall, entity, Chronicle.day(level), workers);
		}
	}

	/** The hall's service list for {@code today}, worked out first if it was last worked out on another day. */
	public static List<Found> listOf(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long today) {
		return listOf(level, hall, entity, today, null);
	}

	private static List<Found> listOf(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long today, @Nullable List<Villager> workers) {
		if (entity.servicesDay() != today) {
			entity.setServices(find(level, hall, workers != null ? workers
				: level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)), today);
		}
		return entity.services();
	}

	/** Where the village's services are now: each worker of a service's job at their job site, each finished build of its blueprints. */
	public static List<Found> find(ServerLevel level, BlockPos hall, List<Villager> villagers) {
		countings++;
		Map<ResourceLocation, Service> all = services;
		List<Found> out = new ArrayList<>();
		if (all.isEmpty()) {
			return out;
		}
		for (Villager v : villagers) {
			VillagerProfession job = v.getVillagerData().getProfession();
			if (v.isBaby() || job == VillagerProfession.NONE) {
				continue;
			}
			GlobalPos site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
			if (site == null || !site.dimension().equals(level.dimension())) {
				continue;
			}
			ResourceLocation jobId = BuiltInRegistries.VILLAGER_PROFESSION.getKey(job);
			for (Service s : all.values()) {
				if (s.jobs().contains(jobId)) {
					out.add(new Found(s.id(), site.pos(), s.range()));
				}
			}
		}
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			ResourceLocation root = ClassNeeds.root(f.structure());
			for (Service s : all.values()) {
				for (ResourceLocation b : s.blueprints()) {
					if (ClassNeeds.root(b).equals(root)) {
						out.add(new Found(s.id(), f.placement().origin(), s.range()));
						break;
					}
				}
			}
		}
		return out;
	}

	/** The services on the hall's list today that reach {@code home} (ids); none outside a village with a hall. */
	public static Set<ResourceLocation> reaching(ServerLevel level, BlockPos hall, @Nullable BlockPos home) {
		return reaching(level, hall, home, Chronicle.day(level));
	}

	/** {@link #reaching} on {@code today} (tests count several dawns). */
	public static Set<ResourceLocation> reaching(ServerLevel level, BlockPos hall, @Nullable BlockPos home, long today) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Set.of();
		}
		Map<ResourceLocation, Service> all = services;
		Set<ResourceLocation> out = new LinkedHashSet<>();
		for (Found f : listOf(level, hall, entity, today)) {
			if (all.containsKey(f.id()) && f.reaches(home)) {
				out.add(f.id());
			}
		}
		return out;
	}

	private Services() {
	}
}

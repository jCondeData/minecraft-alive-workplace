package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.school.Schools;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

/**
 * The Legends (M29): named villagers, one file each in {@code data/<ns>/legends/}, read when data packs load so server
 * owners can add their own. A file that fails (an unknown condition or power type, a bad field) is logged with its name
 * and skipped; the rest still load. A file whose {@code requires} names a mod that isn't installed is skipped quietly.
 * {@code legends} in the config switches it all off: nothing loads, nothing ticks, and Legends already settled stay
 * as ordinary Masters of their trade (their attachment is kept for when it's switched back on).
 */
public final class Legends implements ResourceManagerReloadListener {
	public static final String FOLDER = "legends";
	public static final Set<String> WAYS = Set.of("visit", "found", "born", "inspired");
	public static final Set<String> LUXURIES = Set.of("wine", "jewels", "books", "clothes");
	public static boolean ENABLED = true;
	/** Mythic Legends a village may hold, by rank: Hamlet, Village, Town, City (config {@code mythicLegendCap}). */
	public static volatile int[] MYTHIC_CAP = {0, 0, 1, 2};

	private static Map<ResourceLocation, Legend> legends = Map.of();

	public static void init() {
		Platform.get().onDataReload(AliveWorkplace.id("legends"), new Legends());
		LegendSlots.init();
		LegendLook.init();
		LegendsPage.init();
		StrangeMoods.init();
		Platform.get().onServerTick(LegendSites::tick);
		GolemSmith.init(); // the Golem Smith's golems (29.15)
		Platform.get().onPlayerLeave(player -> Pathfinder.forgetOffer(player.getUUID()));
		Platform.get().allowBreakBlock((level, player, pos, state) -> !(level instanceof net.minecraft.server.level.ServerLevel server)
			|| LegendSites.onBreak(server, player, pos, state));
	}

	public static Collection<Legend> all() {
		return ENABLED ? legends.values() : List.of();
	}

	public static Optional<Legend> get(ResourceLocation id) {
		return ENABLED ? Optional.ofNullable(legends.get(id)) : Optional.empty();
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		reload(manager);
	}

	/** Reads every Legend file again (also tests, after switching {@link #ENABLED}). */
	public static void reload(ResourceManager manager) {
		Map<ResourceLocation, Legend> out = new LinkedHashMap<>();
		if (ENABLED) {
			for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
				ResourceLocation file = e.getKey();
				String path = file.getPath().substring(FOLDER.length() + 1, file.getPath().length() - ".json".length());
				ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
				try (Reader reader = e.getValue().openAsReader()) {
					Legend legend = read(id, JsonParser.parseReader(reader).getAsJsonObject());
					if (legend != null) {
						out.put(id, legend);
					}
				} catch (Exception ex) {
					AliveWorkplace.LOG.warn("Skipping Legend {}: {}", file, ex.getMessage());
				}
			}
		}
		legends = java.util.Collections.unmodifiableMap(out);
		LegendPowers.forget();
		AliveWorkplace.LOG.info("Legends: {}", ENABLED ? legends.keySet() : "switched off");
	}

	/** Reads one Legend file; null when it needs a mod that isn't installed. Throws for a bad file. */
	@Nullable
	public static Legend read(ResourceLocation id, JsonObject json) {
		if (json.has("requires")) {
			for (JsonElement mod : json.getAsJsonArray("requires")) {
				if (!Platform.get().isModLoaded(mod.getAsString())) {
					return null;
				}
			}
		}
		Rarity rarity = Rarity.parse(string(json, "rarity"));
		ResourceLocation job = ResourceLocation.tryParse(string(json, "job"));
		if (job == null || !BuiltInRegistries.VILLAGER_PROFESSION.containsKey(job)) {
			throw new IllegalArgumentException("unknown job '" + string(json, "job") + "'");
		}
		List<String> names = new ArrayList<>();
		for (JsonElement n : json.has("names") ? json.getAsJsonArray("names") : new JsonArray()) {
			names.add(n.getAsString());
		}
		List<JsonObject> arrive = new ArrayList<>();
		for (JsonElement a : json.has("arrive") ? json.getAsJsonArray("arrive") : new JsonArray()) {
			JsonObject way = a.getAsJsonObject();
			if (!way.has("way") || !WAYS.contains(way.get("way").getAsString())) {
				throw new IllegalArgumentException("unknown way " + way.get("way"));
			}
			arrive.add(way);
		}
		Optional<String> luxury = Optional.empty();
		if (json.has("needs") && json.getAsJsonObject("needs").has("luxury")) {
			String l = json.getAsJsonObject("needs").get("luxury").getAsString();
			if (!LUXURIES.contains(l)) {
				throw new IllegalArgumentException("unknown luxury '" + l + "'");
			}
			luxury = Optional.of(l);
		}
		Optional<ResourceLocation> outfit = Optional.empty();
		if (json.has("outfit")) {
			outfit = Optional.ofNullable(ResourceLocation.tryParse(json.get("outfit").getAsString()));
			if (outfit.isEmpty()) {
				throw new IllegalArgumentException("bad outfit");
			}
		}
		return new Legend(id, rarity, job, string(json, "title"), string(json, "lore"), List.copyOf(names),
			Conditions.parseAll(json.has("conditions") ? json.getAsJsonArray("conditions") : new JsonArray()), List.copyOf(arrive), luxury,
			Powers.parseAll(json.has("powers") ? json.getAsJsonArray("powers") : new JsonArray()),
			json.has("masterwork") ? json.getAsJsonObject("masterwork") : null, outfit);
	}

	private static String string(JsonObject json, String field) {
		if (!json.has(field)) {
			throw new IllegalArgumentException("missing '" + field + "'");
		}
		return json.get(field).getAsString();
	}

	/** Puts loaded Legends in place (tests). */
	public static void setForTest(Map<ResourceLocation, Legend> map) {
		legends = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(map));
		LegendPowers.forget();
	}

	/** The Legend {@code villager} is, while Legends are on. */
	public static Optional<Legend> of(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		return data == null ? Optional.empty() : get(data.id());
	}

	/**
	 * Makes {@code villager} the Legend {@code legend}, settled in the village they stand in: a Master of the Legend's
	 * trade with every level's trades (through {@link Schools#headStart}); they keep their own name.
	 */
	public static void make(ServerLevel level, Villager villager, Legend legend, String way) {
		VillagerProfession job = Lookup.value(BuiltInRegistries.VILLAGER_PROFESSION, legend.job());
		if (villager.getVillagerData().getProfession() != job || villager.getVillagerData().getLevel() < VillagerData.MAX_VILLAGER_LEVEL) {
			villager.setVillagerData(villager.getVillagerData().setProfession(job).setLevel(1));
			villager.setVillagerXp(0);
			villager.setOffers(null); // the new trade's trades, from Novice up
			ModAttachments.HEAD_START.set(villager, VillagerData.MAX_VILLAGER_LEVEL);
			Schools.headStart(villager);
		}
		Optional<BlockPos> hall = VillageHalls.nearest(level, villager.blockPosition());
		ModAttachments.LEGEND.set(villager, LegendData.settled(legend.id(), "", hall, level.getDayTime() / 24000L, way));
		LegendPowers.seen(villager);
		LegendLook.update(villager);
		LegendRecord.get(level).settled(legend.id(), villager.getUUID(), level.dimension(), hall, legend.rarity(), villager.getName().getString(),
			io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
		LegendSlots.announce(level, hall.orElse(null), villager, legend, false);
	}

	/** {@code villager} is no longer a Legend (they stay a Master of their trade). */
	public static void clear(Villager villager) {
		ModAttachments.LEGEND.remove(villager);
		if (villager.level() instanceof ServerLevel level) {
			LegendRecord.get(level).forget(villager.getUUID());
			LegendLook.update(villager);
		}
		LegendPowers.forget();
	}

	/**
	 * Every 200 ticks (10 seconds) of a Legend's life: they join their dimension's list for the auras, and sparkle; every
	 * second, a Legend on strike shows what they want over their head (29.5).
	 */
	public static void tick(Villager villager) {
		if (ENABLED && villager.tickCount % 20 == 0 && ModAttachments.LEGEND.has(villager)) {
			LegendNeeds.tick(villager);
			Seer.tick(villager); // the Seer's motes at night, and the Chapel by day (29.16)
			BardLaureate.tick(villager); // the Bard Laureate's work songs (29.19)
		}
		if (ENABLED && ModAttachments.PATHFINDER.has(villager)) {
			Pathfinder.tick(villager); // an expedition with a player (29.13), every 5th tick
		}
		if (ENABLED && villager.tickCount % 20 == 0 && ModAttachments.STRANGE_MOOD.has(villager)) {
			StrangeMoods.tick(villager); // a strange mood or a sulk (29.10)
		}
		if (ENABLED && villager.tickCount % 200 == 0 && ModAttachments.LEGEND.has(villager)) {
			LegendPowers.seen(villager);
			LegendSlots.onRecord(villager);
			LegendLook.sparkle(villager);
			GrandRebuild.tick(villager);
			KeepsToPower.tick(villager); // the Merchant Prince keeps to the hall and the square (29.17)
		}
	}

	/**
	 * The Legend {@code id} settled in the village round {@code hall} with their powers working (not on strike), if any:
	 * who M35's Wonders ask for (the Master Architect, 29.12).
	 */
	public static Optional<Villager> in(ServerLevel level, BlockPos hall, ResourceLocation id) {
		return LegendPowers.settled(level).stream()
			.filter(a -> a.legend().id().equals(id))
			.filter(a -> a.data().hall().map(hall::equals).orElseGet(() -> VillageHalls.nearest(level, a.villager().blockPosition()).map(hall::equals).orElse(false)))
			.map(LegendPowers.Active::villager).findFirst();
	}

	/**
	 * How many days ahead the village round {@code hall} is warned of attacks of {@code kind} ({@code raid}, a warband, a
	 * disaster: M32's sources, 32.14) by a settled Legend who foretells (the Seer, 29.16: two days); 0 without one.
	 */
	public static int foretold(ServerLevel level, BlockPos hall, String kind) {
		return ENABLED ? Seer.warningDays(level, hall, kind) : 0;
	}

	/**
	 * How many times as often Legends visit the village round {@code hall} as guests (29.8): the {@code legend_visits}
	 * effects in force there (M30's Open Gates edict), 1 without.
	 */
	public static float visitFactor(ServerLevel level, BlockPos hall) {
		return io.github.jcondedata.aliveworkplace.hall.CivicEffects.of(level, hall).legendVisits();
	}

	/** Whether {@code legend} may come to the village round {@code hall} (29.3's rarity rules; see {@link LegendSlots#whyNot}). */
	public static boolean canCome(ServerLevel level, BlockPos hall, Legend legend) {
		return LegendSlots.whyNot(level, hall, legend, null).isEmpty();
	}

	private Legends() {
	}
}

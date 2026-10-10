package io.github.jcondedata.aliveworkplace.threat;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The threat engine (ROADMAP 32.2): the raider cultures, as data. It reads {@code data/<ns>/raider_cultures/<id>.json}
 * ({@link Culture}) on every load and {@code /reload}; a file that can't be read is logged and skipped. It picks the
 * culture that comes to a village ({@link #pick}), makes and outfits its raiders ({@link #create}, {@link #outfit}),
 * keeps the raids under way and the threat clock in {@link ThreatData}, and holds two lists other systems add to: what
 * changes a village's chance of an attack ({@link #addFactor}) and what a culture's {@code tactics} do
 * ({@link #registerTactic}). {@code guard/VillageRaids} runs the raids themselves and {@code guard/BanditCamps} the
 * bandits' lair; both call in here.
 *
 * <p>Config {@code raiderCultures} switches each culture ({@code villageRaids} still switches {@link #MONSTERS} and
 * {@code banditCamps} {@link #BANDITS}): one switched off is never picked.
 */
public final class Threats implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = AliveWorkplace.id("raider_cultures");
	public static final String FOLDER = "raider_cultures";
	/** Today's two raids, the first two cultures. */
	public static final ResourceLocation MONSTERS = AliveWorkplace.id("monsters");
	public static final ResourceLocation BANDITS = AliveWorkplace.id("bandits");
	/** The tag every raider carries (the same as {@code VillageRaids.TAG}): whoever has it is a foe to guards. */
	public static final String RAIDER_TAG = "aliveworkplace_raider";
	/**
	 * The cultures the config switches off: {@code raiderCultures} entries set to false ({@code monsters}, or
	 * {@code pack:id}), and {@code monsters} or {@code bandits} when {@code villageRaids} or {@code banditCamps} is off.
	 */
	public static Set<String> DISABLED = Set.of();

	private static Map<ResourceLocation, Culture> cultures = Map.of();
	private static final Map<ResourceLocation, Factor> FACTORS = new ConcurrentHashMap<>();
	private static final Map<String, Tactic> TACTICS = new ConcurrentHashMap<>();

	/** Something that makes an attack on a village likelier (above 1) or rarer (below 1; 0: none comes). */
	@FunctionalInterface
	public interface Factor {
		float of(ServerLevel level, BlockPos hall);
	}

	/**
	 * What one of a culture's {@code tactics} does in a raid. The engine calls it for the cultures that name it; a tactic
	 * nobody registered is skipped.
	 */
	public interface Tactic {
		/** The raid has begun: {@code raiders} are in the world, at their gathering point. */
		default void begin(ServerLevel level, BlockPos hall, Culture culture, List<Mob> raiders) {
		}

		/** The hall's round while the raid lasts, with the raiders still about. */
		default void round(ServerLevel level, BlockPos hall, Culture culture, List<Mob> raiders) {
		}

		/** The raid is over: fought off, or ({@code fled}) the last raiders left at the end of their hours. */
		default void end(ServerLevel level, BlockPos hall, Culture culture, boolean fled) {
		}
	}

	public static void init() {
		Platform.get().onDataReload(ID, new Threats());
		Platform.get().onServerStarting(server -> ThreatData.serverStarting());
		// What already changed the chance of a raid: Curfew and the other civic effects (30.9), and research (29.11).
		addFactor(AliveWorkplace.id("civic"), (level, hall) -> io.github.jcondedata.aliveworkplace.hall.CivicEffects.of(level, hall).raids());
		addFactor(AliveWorkplace.id("research"), io.github.jcondedata.aliveworkplace.research.TreeEffects::raidChance);
	}

	// The cultures.

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		reload(manager);
	}

	/** Reads the cultures from the datapacks, and lists any new one in the config. */
	public static void reload(ResourceManager manager) {
		Map<ResourceLocation, String> files = new TreeMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")).entrySet()) {
			try (Reader reader = e.getValue().openAsReader()) {
				StringBuilder text = new StringBuilder();
				char[] buf = new char[4096];
				for (int n; (n = reader.read(buf)) > 0; ) {
					text.append(buf, 0, n);
				}
				files.put(e.getKey(), text.toString());
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping raider culture {}: {}", e.getKey(), ex.toString());
			}
		}
		cultures = load(files);
		AliveWorkplace.LOG.info("Raider cultures: {}", cultures.keySet());
		List<String> keys = new ArrayList<>();
		cultures.keySet().forEach(id -> keys.add(key(id)));
		WorkplaceConfig.listCultures(Platform.get().configDir(), keys);
	}

	/**
	 * Reads every file ({@code data/ns/raider_cultures/name.json} → its text) into cultures by id, in the order of their
	 * ids; one that can't be read is logged, naming it, and skipped.
	 */
	public static Map<ResourceLocation, Culture> load(Map<ResourceLocation, String> files) {
		Map<ResourceLocation, Culture> out = new TreeMap<>();
		files.forEach((file, text) -> {
			String path = file.getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
				path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length()));
			try {
				out.put(id, Culture.read(id, JsonParser.parseString(text).getAsJsonObject()));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping raider culture {}: {}", file, ex.getMessage() == null ? ex.toString() : ex.getMessage());
			}
		});
		return Collections.unmodifiableMap(new LinkedHashMap<>(out));
	}

	/** Every culture loaded, in the order of their ids. */
	public static Collection<Culture> all() {
		return cultures.values();
	}

	public static Optional<Culture> get(ResourceLocation id) {
		return Optional.ofNullable(cultures.get(id));
	}

	/** Replaces the loaded cultures (tests; {@link #reload} puts the datapacks' back). */
	public static void setForTest(Map<ResourceLocation, Culture> map) {
		cultures = Collections.unmodifiableMap(new LinkedHashMap<>(map));
	}

	/** How the config names a culture: ours by its path ({@code monsters}), a pack's by its whole id. */
	public static String key(ResourceLocation id) {
		return id.getNamespace().equals(AliveWorkplace.MOD_ID) ? id.getPath() : id.toString();
	}

	/** Whether the config leaves the culture {@code id} on. */
	public static boolean enabled(ResourceLocation id) {
		return !DISABLED.contains(key(id)) && !DISABLED.contains(id.toString());
	}

	/** The culture {@code id}, if it's loaded and switched on. */
	public static Optional<Culture> on(ResourceLocation id) {
		return get(id).filter(c -> enabled(c.id()));
	}

	/** Whether {@code culture} is switched on and its {@code where} holds for the village of {@code villagers} round {@code hall}. */
	public static boolean fits(ServerLevel level, BlockPos hall, Culture culture, IntSupplier villagers) {
		return enabled(culture.id()) && culture.where().test(level, hall, villagers);
	}

	/**
	 * The culture that comes to the village of {@code villagers} round {@code hall} from nowhere in particular: one of
	 * those switched on, without a lair (a culture with a lair raids only from its lair) and whose {@code where} fits, by
	 * weight. Empty when none fits.
	 */
	public static Optional<Culture> pick(ServerLevel level, BlockPos hall, int villagers, RandomSource random) {
		List<Culture> fitting = new ArrayList<>();
		for (Culture culture : cultures.values()) {
			if (!culture.needsLair() && fits(level, hall, culture, () -> villagers)) {
				fitting.add(culture);
			}
		}
		return byWeight(fitting, random);
	}

	/** One of {@code candidates}, each as often as its weight. */
	public static Optional<Culture> byWeight(List<Culture> candidates, RandomSource random) {
		int total = candidates.stream().mapToInt(Culture::weight).sum();
		if (total <= 0) {
			return Optional.empty();
		}
		int roll = random.nextInt(total);
		for (Culture culture : candidates) {
			roll -= culture.weight();
			if (roll < 0) {
				return Optional.of(culture);
			}
		}
		return Optional.of(candidates.get(candidates.size() - 1));
	}

	// The chance of an attack.

	/**
	 * What the village round {@code hall} multiplies its chance of an attack by: every registered factor together (1
	 * when none has anything to say).
	 */
	public static float chanceFactor(ServerLevel level, BlockPos hall) {
		float factor = 1f;
		for (Factor f : FACTORS.values()) {
			factor *= Math.max(0f, f.of(level, hall));
		}
		return factor;
	}

	/** Adds (or replaces) a factor of {@link #chanceFactor}: an edict, a research topic, a wonder. */
	public static void addFactor(ResourceLocation id, Factor factor) {
		FACTORS.put(id, factor);
	}

	public static void removeFactor(ResourceLocation id) {
		FACTORS.remove(id);
	}

	// Tactics.

	/** Teaches the engine the tactic {@code name} ({@code ram_gates}, {@code ladders}, …). */
	public static void registerTactic(String name, Tactic tactic) {
		TACTICS.put(name, tactic);
	}

	public static void unregisterTactic(String name) {
		TACTICS.remove(name);
	}

	/** The tactics of {@code culture} the engine knows, in the file's order; the others are skipped. */
	public static List<Tactic> tactics(Culture culture) {
		List<Tactic> known = new ArrayList<>();
		for (String name : culture.tactics()) {
			Tactic tactic = TACTICS.get(name);
			if (tactic != null) {
				known.add(tactic);
			}
		}
		return known;
	}

	// Raiders.

	/** A mob for {@code member}, not yet in the world; null if the game can't make one. */
	@Nullable
	public static Mob create(ServerLevel level, Culture.Member member) {
		return create(level, member.entity());
	}

	@Nullable
	public static Mob create(ServerLevel level, ResourceLocation entity) {
		Entity made = BuiltInRegistries.ENTITY_TYPE.getOptional(entity).map(type -> type.create(level)).orElse(null);
		if (made instanceof Mob mob) {
			return mob;
		}
		if (made != null) {
			made.discard();
		}
		return null;
	}

	/**
	 * Makes {@code mob} (already through {@code finalizeSpawn}) one of {@code culture}'s raiders: it never despawns, keeps
	 * out of vanilla's pillager raids, carries {@link #RAIDER_TAG} and its role's tag, is called what the culture calls
	 * its raiders, and wears {@code member}'s gear.
	 */
	public static void outfit(ServerLevel level, Mob mob, Culture culture, Culture.Member member) {
		mob.setPersistenceRequired();
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false); // (finalizeSpawn lets a raider join vanilla's raids again)
		}
		mob.addTag(RAIDER_TAG);
		mob.addTag(member.role().tag());
		culture.name().ifPresent(name -> mob.setCustomName(Component.translatable(name)));
		equip(level, mob, member.gear());
	}

	/** Puts {@code gear} (slot → item id or {@link Culture#OMINOUS_BANNER}) on {@code mob}; it never drops. */
	public static void equip(ServerLevel level, Mob mob, Map<EquipmentSlot, String> gear) {
		gear.forEach((slot, item) -> {
			ItemStack stack = item.equals(Culture.OMINOUS_BANNER)
				? Raid.getLeaderBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN))
				: BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(item)).map(ItemStack::new).orElse(ItemStack.EMPTY);
			if (!stack.isEmpty()) {
				mob.setItemSlot(slot, stack);
				mob.setDropChance(slot, 0f);
			}
		});
	}

	/** The role of a raider (from its tag); {@link Culture.Role#MELEE} for one without. */
	public static Culture.Role role(Mob mob) {
		for (Culture.Role role : Culture.Role.values()) {
			if (mob.getTags().contains(role.tag())) {
				return role;
			}
		}
		return Culture.Role.MELEE;
	}

	// The clock.

	/** The attacks the clock has set for the village round {@code hall}, soonest first (a warning reads them, 32.14). */
	public static List<ThreatData.Attack> clock(ServerLevel level, BlockPos hall) {
		return ThreatData.get(level).attacks(hall);
	}

	/** The next attack the clock has set for the village round {@code hall}, if any. */
	public static Optional<ThreatData.Attack> next(ServerLevel level, BlockPos hall) {
		return clock(level, hall).stream().findFirst();
	}

	private Threats() {
	}
}

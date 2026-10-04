package io.github.jcondedata.aliveworkplace.work;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Partners at work (ROADMAP 28.3): a job calls {@link #cue} at a moment of its work, and one of the worker's pastured
 * Pokémon of a type the show names walks to that spot (as far as its pasture lets it), does the show and walks back.
 * Shows are data, {@code data/<namespace>/partner_shows/<name>.json}:
 * <pre>
 * { "jobs": ["aliveworkplace:builder"], "types": ["fighting"], "cue": "fetch",
 *   "carry": "from_work",            // or an item id; left out: carries nothing
 *   "animation": "physical",         // physical, special or cry
 *   "particles": "minecraft:crit",   // a vanilla particle, or a Cobblemon effect such as cobblemon:impact_water
 *   "sound": "minecraft:entity.player.attack.strong",
 *   "ticks": 40,                     // how long the show itself lasts, 10 to 400
 *   "effect": "none" }               // none, hydrate_farmland, smoke or sparks
 * </pre>
 * What it carries is a vanilla Item Display that follows the Pokémon by teleport (never riding it), tagged
 * {@link #DISPLAY_TAG}; it goes when the show ends, and a display whose show was cut off (a restart, an unloaded chunk)
 * is removed when its chunk loads. A pastured Pokémon is never untethered, and a show never touches a Pokémon in
 * battle, ridden or on a shoulder, nor its held item, friendship, moves or stats. Budget: one show per worker every
 * {@link #WORKER_COOLDOWN} ticks, {@link #MAX_PER_LEVEL} running per level, none with no player within
 * {@link #PLAYER_RANGE} blocks. Config {@code partnerShows}.
 */
public final class PartnerShows implements ResourceManagerReloadListener {
	public static final ResourceLocation ID = AliveWorkplace.id("partner_shows");
	public static final String DISPLAY_TAG = "aliveworkplace_show";
	/** Config switch {@code partnerShows} (off in gametests unless a test turns it on). */
	public static boolean ENABLED = true;
	public static final int WORKER_COOLDOWN = 200;
	public static final int MAX_PER_LEVEL = 6;
	public static final int PLAYER_RANGE = 48;
	/** How long a partner may take to walk there, or back, before the show goes on without it. */
	static final int WALK_LIMIT = 200;
	/** Within this many blocks of the spot, the partner has arrived. */
	static final double ARRIVED = 2.0;
	/** Within this many blocks of where it started, the partner is back. */
	static final double HOME = 3.0;

	/** What a show does to the world at its spot, from a small toolbox. */
	public enum Effect {
		NONE, HYDRATE_FARMLAND, SMOKE, SPARKS
	}

	/** The marker for "carry what the worker is handling". */
	static final String FROM_WORK = "from_work";

	/** One show, as its data file says. */
	public record Show(ResourceLocation name, Set<ResourceLocation> jobs, Set<String> types, String cue, @Nullable String carry,
					   @Nullable String animation, @Nullable ResourceLocation particles, @Nullable ResourceLocation sound, int ticks, Effect effect) {
	}

	private enum Phase {
		GO, PLAY, BACK
	}

	/** A show on, for one partner. */
	private static final class Running {
		final Show show;
		final ServerLevel level;
		final Entity pokemon;
		final UUID worker;
		final Vec3 workerAt;
		final Vec3 start;
		final BlockPos spot;
		@Nullable Display.ItemDisplay display;
		Phase phase = Phase.GO;
		int phaseTicks;

		Running(Show show, ServerLevel level, Entity pokemon, Villager worker, BlockPos spot) {
			this.show = show;
			this.level = level;
			this.pokemon = pokemon;
			this.worker = worker.getUUID();
			this.workerAt = worker.position();
			this.start = pokemon.position();
			this.spot = spot;
		}
	}

	private static volatile List<Show> shows = List.of();
	private static final List<Running> RUNNING = new ArrayList<>();
	private static final Map<Villager, Long> LAST_CUE = new WeakHashMap<>();
	private static final Set<UUID> DISPLAYS = new HashSet<>();

	public static void init() {
		Platform.get().onDataReload(ID, new PartnerShows());
		Platform.get().onServerTick(PartnerShows::tick);
		Platform.get().onEntityLoad((entity, level) -> {
			// A display whose show was cut off (the server stopped, the chunk unloaded mid-show) has no show any more.
			if (entity instanceof Display.ItemDisplay && entity.getTags().contains(DISPLAY_TAG) && !DISPLAYS.contains(entity.getUUID())) {
				entity.discard();
			}
		});
	}

	/** The shows loaded from data. */
	public static List<Show> shows() {
		return shows;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		List<Show> out = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : manager.listResources("partner_shows", p -> p.getPath().endsWith(".json")).entrySet()) {
			ResourceLocation file = e.getKey();
			ResourceLocation name = ResourceLocation.fromNamespaceAndPath(file.getNamespace(),
				file.getPath().substring("partner_shows/".length(), file.getPath().length() - ".json".length()));
			try (Reader reader = e.getValue().openAsReader()) {
				out.add(parse(name, JsonParser.parseReader(reader).getAsJsonObject()));
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Alive Workplace: skipping partner show {}: {}", file, ex.getMessage());
			}
		}
		shows = List.copyOf(out);
		AliveWorkplace.LOG.info("Alive Workplace: {} partner shows", shows.size());
	}

	/** Reads one show file; throws {@link IllegalArgumentException} saying what's wrong. */
	public static Show parse(ResourceLocation name, JsonObject json) {
		Set<ResourceLocation> jobs = new LinkedHashSet<>();
		for (JsonElement job : array(json, "jobs")) {
			jobs.add(id(job.getAsString(), "jobs"));
		}
		Set<String> types = new LinkedHashSet<>();
		for (JsonElement type : array(json, "types")) {
			types.add(type.getAsString().toLowerCase(Locale.ROOT));
		}
		if (jobs.isEmpty() || types.isEmpty()) {
			throw new IllegalArgumentException("needs at least one job and one type");
		}
		if (!json.has("cue") || json.get("cue").getAsString().isBlank()) {
			throw new IllegalArgumentException("no cue");
		}
		String cue = json.get("cue").getAsString();
		String carry = json.has("carry") ? json.get("carry").getAsString() : null;
		if (carry != null && !carry.equals(FROM_WORK) && BuiltInRegistries.ITEM.getOptional(id(carry, "carry")).isEmpty()) {
			throw new IllegalArgumentException("carry: no item " + carry);
		}
		String animation = json.has("animation") ? json.get("animation").getAsString() : null;
		if (animation != null && !Set.of("physical", "special", "cry").contains(animation)) {
			throw new IllegalArgumentException("animation: " + animation + " (physical, special or cry)");
		}
		ResourceLocation particles = json.has("particles") ? id(json.get("particles").getAsString(), "particles") : null;
		if (particles != null && !particles.getNamespace().equals("cobblemon")
			&& !(BuiltInRegistries.PARTICLE_TYPE.getOptional(particles).orElse(null) instanceof SimpleParticleType)) {
			throw new IllegalArgumentException("particles: " + particles + " isn't a plain particle");
		}
		ResourceLocation sound = json.has("sound") ? id(json.get("sound").getAsString(), "sound") : null;
		int ticks = json.has("ticks") ? json.get("ticks").getAsInt() : 40;
		if (ticks < 10 || ticks > 400) {
			throw new IllegalArgumentException("ticks: " + ticks + " (10 to 400)");
		}
		Effect effect;
		try {
			effect = json.has("effect") ? Effect.valueOf(json.get("effect").getAsString().toUpperCase(Locale.ROOT)) : Effect.NONE;
		} catch (IllegalArgumentException ex) {
			throw new IllegalArgumentException("effect: " + json.get("effect").getAsString() + " (none, hydrate_farmland, smoke or sparks)");
		}
		return new Show(name, Set.copyOf(jobs), Set.copyOf(types), cue, carry, animation, particles, sound, ticks, effect);
	}

	private static JsonArray array(JsonObject json, String key) {
		if (!json.has(key) || !json.get(key).isJsonArray()) {
			throw new IllegalArgumentException("no " + key + " list");
		}
		return json.getAsJsonArray(key);
	}

	private static ResourceLocation id(String text, String field) {
		ResourceLocation id = ResourceLocation.tryParse(text);
		if (id == null) {
			throw new IllegalArgumentException(field + ": not an id: " + text);
		}
		return id;
	}

	/** Cues the shows for {@code cue} at {@code pos}; see {@link #cue(Villager, String, BlockPos, ItemStack)}. */
	public static boolean cue(Villager worker, String cue, BlockPos pos) {
		return cue(worker, cue, pos, ItemStack.EMPTY);
	}

	/**
	 * {@code worker} has reached the moment {@code cue} of their job at {@code pos}, handling {@code handling} (what a
	 * {@code from_work} show carries). One of their pastured partners of a type a matching show names does that show,
	 * if the budget allows. True if a show started.
	 */
	public static boolean cue(Villager worker, String cue, BlockPos pos, ItemStack handling) {
		if (!ENABLED || !(worker.level() instanceof ServerLevel level) || shows.isEmpty() || !PokemonPartners.EXTENSION.present()) {
			return refuse("off, no shows or no Cobblemon");
		}
		ResourceLocation job = BuiltInRegistries.VILLAGER_PROFESSION.getKey(worker.getVillagerData().getProfession());
		List<Show> matching = shows.stream().filter(s -> s.cue().equals(cue) && s.jobs().contains(job)).toList();
		if (matching.isEmpty()) {
			return refuse("no show for " + job + " at " + cue);
		}
		long now = level.getGameTime();
		Long last = LAST_CUE.get(worker);
		if (last != null && now - last < WORKER_COOLDOWN) {
			return refuse("this worker had a show " + (now - last) + " ticks ago");
		}
		if (RUNNING.stream().filter(r -> r.level == level).count() >= MAX_PER_LEVEL) {
			return refuse(MAX_PER_LEVEL + " shows already on");
		}
		if (level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, PLAYER_RANGE, false) == null) {
			return refuse("no player within " + PLAYER_RANGE + " blocks");
		}
		BlockPos station = worker.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension())
			.map(GlobalPos::pos).orElse(worker.blockPosition());
		int partners = 0;
		for (Show show : matching) {
			Item carry = carried(show, handling);
			List<PokemonPartners.Fighter> helpers = PokemonPartners.EXTENSION.call(
				p -> p.fighters(level, station, Partners.RADIUS, show.types(), Partners.MAX), List.<PokemonPartners.Fighter>of());
			for (PokemonPartners.Fighter helper : helpers) {
				Entity pokemon = helper.entity();
				partners++;
				if (busy(pokemon) || !PokemonPartners.EXTENSION.call(p -> p.canPerform(pokemon), false)) {
					continue;
				}
				BlockPos spot = PokemonPartners.EXTENSION.call(p -> p.reachable(pokemon, pos), pokemon.blockPosition());
				Running run = new Running(show, level, pokemon, worker, spot);
				if (carry != null) {
					run.display = display(level, pokemon, new ItemStack(carry));
				}
				PokemonPartners.EXTENSION.run(p -> p.walkTo(pokemon, spot, 1.0));
				RUNNING.add(run);
				LAST_CUE.put(worker, now);
				lastRefusal = "";
				return true;
			}
		}
		return refuse(partners == 0 ? "no pastured partner of the show's types near the workstation" : partners + " partners, all busy");
	}

	@Nullable
	private static Item carried(Show show, ItemStack handling) {
		if (show.carry() == null) {
			return null;
		}
		if (show.carry().equals(FROM_WORK)) {
			return handling.isEmpty() ? null : handling.getItem();
		}
		return BuiltInRegistries.ITEM.getOptional(ResourceLocation.tryParse(show.carry())).orElse(null);
	}

	private static String lastRefusal = "";

	private static boolean refuse(String why) {
		lastRefusal = why;
		return false;
	}

	/** Why the last cue started no show ("" if it started one): for tests and the debug log. */
	public static String lastRefusal() {
		return lastRefusal;
	}

	/** Whether {@code pokemon} is in a show now. */
	public static boolean busy(Entity pokemon) {
		return RUNNING.stream().anyMatch(r -> r.pokemon == pokemon);
	}

	/** How many shows are on in {@code level}. */
	public static int running(ServerLevel level) {
		return (int) RUNNING.stream().filter(r -> r.level == level).count();
	}

	/** The display a show's partner is carrying, if any. */
	public static Optional<Display.ItemDisplay> carrying(Entity pokemon) {
		return RUNNING.stream().filter(r -> r.pokemon == pokemon && r.display != null).map(r -> r.display).findFirst();
	}

	/** Ends every show at once (tests; a level unloading). */
	public static void stopAll() {
		for (Running run : RUNNING) {
			end(run);
		}
		RUNNING.clear();
	}

	private static Display.ItemDisplay display(ServerLevel level, Entity pokemon, ItemStack stack) {
		Display.ItemDisplay display = EntityType.ITEM_DISPLAY.create(level);
		if (display == null) {
			return null;
		}
		CompoundTag tag = new CompoundTag();
		tag.putInt(Display.TAG_POS_ROT_INTERPOLATION_DURATION, 2);
		CompoundTag transformation = new CompoundTag();
		transformation.put("translation", floats(0, 0, 0));
		transformation.put("left_rotation", floats(0, 0, 0, 1));
		transformation.put("scale", floats(0.6f, 0.6f, 0.6f));
		transformation.put("right_rotation", floats(0, 0, 0, 1));
		tag.put(Display.TAG_TRANSFORMATION, transformation);
		display.load(tag);
		display.getSlot(0).set(stack);
		display.addTag(DISPLAY_TAG);
		display.setNoGravity(true);
		Vec3 at = above(pokemon);
		display.moveTo(at.x, at.y, at.z, pokemon.getYRot(), 0f);
		DISPLAYS.add(display.getUUID()); // before it's added: adding it fires the load event, which removes strays
		level.addFreshEntity(display);
		return display;
	}

	private static ListTag floats(float... values) {
		ListTag list = new ListTag();
		for (float v : values) {
			list.add(FloatTag.valueOf(v));
		}
		return list;
	}

	private static Vec3 above(Entity pokemon) {
		return pokemon.position().add(0, pokemon.getBbHeight() + 0.25, 0);
	}

	private static void tick(MinecraftServer server) {
		if (RUNNING.isEmpty()) {
			return;
		}
		for (Iterator<Running> it = RUNNING.iterator(); it.hasNext(); ) {
			Running run = it.next();
			if (!step(run)) {
				end(run);
				it.remove();
			}
		}
	}

	/** One tick of a show; false when it's over. */
	private static boolean step(Running run) {
		Entity pokemon = run.pokemon;
		if (!pokemon.isAlive() || pokemon.isRemoved() || !PokemonPartners.EXTENSION.call(p -> p.canPerform(pokemon), false)) {
			return false; // recalled, in a battle, picked up: the show stops there
		}
		run.phaseTicks++;
		if (run.display != null) {
			Vec3 at = above(pokemon);
			run.display.teleportTo(at.x, at.y, at.z);
		}
		switch (run.phase) {
			case GO -> {
				boolean there = pokemon.position().distanceToSqr(Vec3.atBottomCenterOf(run.spot)) <= ARRIVED * ARRIVED;
				if (there || run.phaseTicks >= WALK_LIMIT) {
					// There, or Cobblemon's brain ignored the walk: the show plays where it stands, facing the worker.
					run.phase = Phase.PLAY;
					run.phaseTicks = 0;
					face(pokemon, run.workerAt);
					if (run.show.animation() != null) {
						PokemonPartners.EXTENSION.run(p -> p.animate(run.level, pokemon, run.show.animation()));
					}
					apply(run.level, run.show.effect(), there ? run.spot : pokemon.blockPosition());
				} else if (run.phaseTicks % 20 == 0) {
					PokemonPartners.EXTENSION.run(p -> p.walkTo(pokemon, run.spot, 1.0)); // the brain may have sent it elsewhere
				}
			}
			case PLAY -> {
				if (run.phaseTicks % 10 == 1) {
					particles(run.level, run.show.particles(), pokemon);
					sound(run.level, run.show.sound(), pokemon);
				}
				if (run.phaseTicks >= run.show.ticks()) {
					run.phase = Phase.BACK;
					run.phaseTicks = 0;
					if (run.display != null) {
						DISPLAYS.remove(run.display.getUUID());
						run.display.discard(); // set down at the spot
						run.display = null;
					}
					PokemonPartners.EXTENSION.run(p -> p.walkTo(pokemon, BlockPos.containing(run.start), 1.0));
				}
			}
			case BACK -> {
				if (pokemon.position().distanceToSqr(run.start) <= HOME * HOME) {
					return false; // back where it was
				}
				if (run.phaseTicks % 20 == 0) {
					PokemonPartners.EXTENSION.run(p -> p.walkTo(pokemon, BlockPos.containing(run.start), 1.0));
				}
				return run.phaseTicks < WALK_LIMIT;
			}
		}
		return true;
	}

	private static void end(Running run) {
		if (run.phase == Phase.BACK && run.pokemon.isAlive() && run.pokemon.position().distanceToSqr(run.start) > HOME * HOME) {
			PokemonPartners.EXTENSION.run(p -> p.goHome(run.pokemon)); // didn't make it back in time: off to its pasture
		}
		if (run.display != null) {
			DISPLAYS.remove(run.display.getUUID());
			run.display.discard();
			run.display = null;
		}
	}

	private static void face(Entity pokemon, Vec3 target) {
		double dx = target.x - pokemon.getX();
		double dz = target.z - pokemon.getZ();
		float yaw = (float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90f;
		pokemon.setYRot(yaw);
		pokemon.setYHeadRot(yaw);
	}

	private static void particles(ServerLevel level, @Nullable ResourceLocation id, Entity pokemon) {
		if (id == null) {
			return;
		}
		Vec3 at = pokemon.position().add(0, pokemon.getBbHeight() * 0.6, 0);
		if (PokemonPartners.EXTENSION.call(p -> p.effect(level, id, at), false)) {
			return;
		}
		if (BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null) instanceof SimpleParticleType type) {
			level.sendParticles(type, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
		}
	}

	private static void sound(ServerLevel level, @Nullable ResourceLocation id, Entity pokemon) {
		if (id == null) {
			return;
		}
		SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(id).orElse(null);
		if (sound != null) {
			level.playSound(null, pokemon.getX(), pokemon.getY(), pokemon.getZ(), sound, SoundSource.NEUTRAL, 0.8f, 1f);
		}
	}

	/** The toolbox: what a show does to the world at {@code at}. */
	public static void apply(ServerLevel level, Effect effect, BlockPos at) {
		switch (effect) {
			case NONE -> {
			}
			case HYDRATE_FARMLAND -> {
				for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, -1, -1), at.offset(1, 0, 1))) {
					BlockState state = level.getBlockState(p);
					if (state.getBlock() instanceof FarmBlock && state.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
						level.setBlock(p, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 2);
					}
				}
				level.sendParticles(ParticleTypes.SPLASH, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5, 20, 1.0, 0.1, 1.0, 0.1);
			}
			case SMOKE -> spread(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, 4);
			case SPARKS -> spread(level, ParticleTypes.ELECTRIC_SPARK, at, 12);
		}
	}

	private static void spread(ServerLevel level, ParticleOptions particle, BlockPos at, int count) {
		level.sendParticles(particle, at.getX() + 0.5, at.getY() + 0.8, at.getZ() + 0.5, count, 0.3, 0.3, 0.3, 0.01);
	}

	/** For tests: the shows loaded so far, replaced (a show list without a reload). */
	public static void setShows(List<Show> list) {
		shows = List.copyOf(list);
	}

	/** For tests: lets {@code worker} be cued again at once. */
	public static void forget(Villager worker) {
		LAST_CUE.remove(worker);
	}

	private PartnerShows() {
	}
}

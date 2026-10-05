package io.github.jcondedata.aliveworkplace.hall;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Edicts (ROADMAP 30.3, docs/design/M30.md "Rules for edicts"): laws a village's owner proclaims at the hall, each a
 * trade-off of {@link CivicEffects}. Loaded from {@code data/<namespace>/edicts/<id>.json}; a data pack adds its own or
 * switches one of ours off with a file at the same path holding {@code "enabled": false}. Ours take their texts from
 * the lang file, a pack's may be plain strings.
 *
 * <p>A village keeps one edict in force per rank (Hamlet 1 ... City 4), each at least {@link #MIN_DAYS} days; edicts
 * that exclude each other can't be in force together; a village that drops a rank loses its newest. Proclaiming and
 * lifting are told to the players in the village and go in the chronicle. Who may: {@link VillageProtection#mayBuild}.
 */
public final class Edicts implements ResourceManagerReloadListener {
	private static final ResourceLocation ID = AliveWorkplace.id("edicts");
	public static final String FOLDER = "edicts";
	/** {@code villageEdicts} in the config: off, edicts can't be proclaimed and have no effect (those in force stay saved). */
	public static boolean ENABLED = true;
	/** {@code edictMinDays} in the config: days an edict stays before it can be lifted. */
	public static int MIN_DAYS = 3;

	/**
	 * One edict: its id (the file's), icon, texts, order on the page, its boost and cost, what it excludes, and its
	 * reform (30.5, {@link Reforms}; empty: it can't be reformed).
	 */
	public record Edict(ResourceLocation id, ResourceLocation icon, Component name, Component description, int order,
						List<CivicEffects.Effect> boost, List<CivicEffects.Effect> cost, List<ResourceLocation> excludes,
						Optional<Reforms.Reform> reform) {
		/** An edict without a reform. */
		public Edict(ResourceLocation id, ResourceLocation icon, Component name, Component description, int order,
					 List<CivicEffects.Effect> boost, List<CivicEffects.Effect> cost, List<ResourceLocation> excludes) {
			this(id, icon, name, description, order, boost, cost, excludes, Optional.empty());
		}

		/** Every effect it has while in force. */
		public List<CivicEffects.Effect> effects() {
			return effects(false);
		}

		/** Every effect it has while in force, reformed or not: reformed, the reform's effects take the cost's place. */
		public List<CivicEffects.Effect> effects(boolean reformed) {
			List<CivicEffects.Effect> all = new ArrayList<>(boost);
			all.addAll(reformed ? reform.map(Reforms.Reform::effects).orElse(cost) : cost);
			return all;
		}

		/** Whether this edict and {@code other} can't be in force together (either one naming the other is enough). */
		public boolean excludes(Edict other) {
			return excludes.contains(other.id()) || other.excludes().contains(id);
		}
	}

	/** An edict in force in a village: its id and the day it was proclaimed ({@link Chronicle#day}). Saved on the hall. */
	public record InForce(String id, long day) {
		public static final Codec<InForce> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(InForce::id),
			Codec.LONG.fieldOf("day").forGetter(InForce::day)
		).apply(i, InForce::new));
	}

	/** An id as a file or a player writes it: ours may leave out the namespace ("long_shifts"). */
	public static final Codec<ResourceLocation> ID_CODEC = Codec.STRING.xmap(Edicts::id, Edicts::shortId);

	private record Body(ResourceLocation icon, Component name, Component description, int order, List<CivicEffects.Effect> boost,
						List<CivicEffects.Effect> cost, List<ResourceLocation> excludes, Optional<Reforms.Reform> reform) {
	}

	private static final MapCodec<Body> BODY = RecordCodecBuilder.mapCodec(i -> i.group(
		ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.withDefaultNamespace("paper")).forGetter(Body::icon),
		ComponentSerialization.CODEC.fieldOf("name").forGetter(Body::name),
		ComponentSerialization.CODEC.optionalFieldOf("description", Component.empty()).forGetter(Body::description),
		Codec.INT.optionalFieldOf("order", 100).forGetter(Body::order),
		CivicEffects.CODEC.listOf().optionalFieldOf("boost", List.of()).forGetter(Body::boost),
		CivicEffects.CODEC.listOf().optionalFieldOf("cost", List.of()).forGetter(Body::cost),
		ID_CODEC.listOf().optionalFieldOf("excludes", List.of()).forGetter(Body::excludes),
		Reforms.Reform.CODEC.optionalFieldOf("reform").forGetter(Body::reform)
	).apply(i, Body::new));

	private static volatile Map<ResourceLocation, Edict> edicts = Map.of();
	/** Bumped whenever the edicts or the switch change, so the halls sum their effects again. */
	private static volatile int generation;

	public static void init() {
		Platform.get().onDataReload(ID, new Edicts());
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		load(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json")));
	}

	/** Reads every edict file (the top one per path, as the resource manager gives them); a broken file is skipped. */
	public static void load(Map<ResourceLocation, Resource> files) {
		List<Edict> found = new ArrayList<>();
		for (Map.Entry<ResourceLocation, Resource> e : files.entrySet()) {
			String path = e.getKey().getPath();
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(),
				path.substring(FOLDER.length() + 1, path.length() - ".json".length()));
			try (Reader reader = e.getValue().openAsReader()) {
				Edict edict = read(id, JsonParser.parseReader(reader));
				if (edict != null) {
					found.add(edict);
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping edict {}: {}", e.getKey(), ex.getMessage());
			}
		}
		set(found);
	}

	/** Reads one edict; null if it is switched off ({@code "enabled": false}); throws naming the bad field. */
	@Nullable
	public static Edict read(ResourceLocation id, JsonElement json) {
		JsonObject object = json.getAsJsonObject();
		if (object.has("enabled") && !object.get("enabled").getAsBoolean()) {
			return null;
		}
		Body body = BODY.codec().parse(JsonOps.INSTANCE, object).getOrThrow(IllegalArgumentException::new);
		return new Edict(id, body.icon(), body.name(), body.description(), body.order(), body.boost(), body.cost(), body.excludes(), body.reform());
	}

	static void set(List<Edict> found) {
		found.sort(Comparator.comparingInt(Edict::order).thenComparing(e -> e.id().toString()));
		Map<ResourceLocation, Edict> out = new LinkedHashMap<>();
		found.forEach(e -> out.put(e.id(), e));
		edicts = Collections.unmodifiableMap(out);
		generation++;
	}

	/** Turns edicts on or off (the config). */
	public static void setEnabled(boolean on) {
		if (ENABLED != on) {
			ENABLED = on;
			generation++;
		}
	}

	static int generation() {
		return generation;
	}

	/** Every edict there is, in their order on the page. */
	public static List<Edict> all() {
		return List.copyOf(edicts.values());
	}

	public static Optional<Edict> get(ResourceLocation id) {
		return Optional.ofNullable(edicts.get(id));
	}

	/** The edict a player or a file names: "long_shifts" or "aliveworkplace:long_shifts". */
	public static Optional<Edict> find(String text) {
		ResourceLocation id = ResourceLocation.tryParse(text.indexOf(':') < 0 ? AliveWorkplace.MOD_ID + ":" + text : text);
		return id == null ? Optional.empty() : get(id);
	}

	/** "long_shifts" becomes ours, "pack:rest" stays the pack's. */
	public static ResourceLocation id(String text) {
		return text.indexOf(':') < 0 ? AliveWorkplace.id(text) : ResourceLocation.parse(text);
	}

	/** Our ids without the namespace ("long_shifts"), a pack's in full. */
	public static String shortId(ResourceLocation id) {
		return id.getNamespace().equals(AliveWorkplace.MOD_ID) ? id.getPath() : id.toString();
	}

	/** Edicts a village of this rank may keep in force: Hamlet 1, Village 2, Town 3, City 4. */
	public static int slots(VillageRanks.Rank rank) {
		return rank.ordinal() + 1;
	}

	/**
	 * The effects of the edicts in force (the ones still loaded), with the edict's name as their source; those the
	 * village has reformed ({@code reformed} ids) without their cost.
	 */
	static CivicEffects.Sum sum(List<InForce> inForce, java.util.Set<String> reformed) {
		List<CivicEffects.Active> active = new ArrayList<>();
		for (InForce f : inForce) {
			get(ResourceLocation.tryParse(f.id())).ifPresent(edict -> edict.effects(reformed.contains(f.id()))
				.forEach(e -> active.add(new CivicEffects.Active(e, edict.name()))));
		}
		return new CivicEffects.Sum(active);
	}

	/** An edict's name, or its id if no file has it any more. */
	public static Component name(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id);
		return rl == null ? Component.literal(id) : get(rl).map(Edict::name).orElse(Component.literal(id));
	}

	/** What came of a proclamation or a lifting: whether it happened, what to tell whoever asked, who was told. */
	public record Result(boolean done, Component message, List<ServerPlayer> told) {
		static Result no(Component message) {
			return new Result(false, message.copy().withStyle(ChatFormatting.RED), List.of());
		}
	}

	/** {@code by} (null: an operator's console) proclaims {@code edict} in the village of the hall at {@code hall}. */
	public static Result proclaim(ServerLevel level, BlockPos hall, @Nullable Player by, Edict edict) {
		if (!ENABLED) {
			return Result.no(Component.translatable("message.aliveworkplace.edict.disabled"));
		}
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Result.no(Component.translatable("message.aliveworkplace.edict.no_hall"));
		}
		Component village = VillageHalls.name(level, hall);
		if (by != null && !VillageProtection.mayBuild(level, entity, by)) {
			return Result.no(Component.translatable("message.aliveworkplace.edict.not_allowed", entity.ownerName(), village));
		}
		List<InForce> inForce = entity.edicts();
		for (InForce f : inForce) {
			if (f.id().equals(edict.id().toString())) {
				return Result.no(Component.translatable("message.aliveworkplace.edict.already", edict.name(), village));
			}
			Optional<Edict> other = get(ResourceLocation.tryParse(f.id()));
			if (other.isPresent() && edict.excludes(other.get())) {
				return Result.no(Component.translatable("message.aliveworkplace.edict.excluded", edict.name(), other.get().name()));
			}
		}
		int slots = slots(entity.rank());
		if (inForce.size() >= slots) {
			return Result.no(Component.translatable("message.aliveworkplace.edict.no_slot", village, entity.rank().title(),
				Words.counted("message.aliveworkplace.edict.count", slots, slots)));
		}
		List<InForce> next = new ArrayList<>(inForce);
		next.add(new InForce(edict.id().toString(), Chronicle.day(level)));
		entity.setEdicts(next);
		Chronicle.record(level, hall, Chronicle.Kind.EDICT, Component.translatable("chronicle.aliveworkplace.edict.proclaimed", edict.name()), true);
		Reforms.round(level, hall, entity);
		level.playSound(null, hall, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1f, 1f);
		Component told = Component.translatable("message.aliveworkplace.edict.proclaimed", village, edict.name(), edict.description())
			.withStyle(ChatFormatting.GOLD);
		return new Result(true, told, announce(level, hall, told));
	}

	/** {@code by} (null: an operator's console) lifts the edict {@code id} in the village of the hall at {@code hall}. */
	public static Result lift(ServerLevel level, BlockPos hall, @Nullable Player by, String id) {
		if (!ENABLED) {
			return Result.no(Component.translatable("message.aliveworkplace.edict.disabled"));
		}
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return Result.no(Component.translatable("message.aliveworkplace.edict.no_hall"));
		}
		Component village = VillageHalls.name(level, hall);
		Component name = name(id);
		InForce found = entity.edicts().stream().filter(f -> f.id().equals(id)).findFirst().orElse(null);
		if (found == null) {
			return Result.no(Component.translatable("message.aliveworkplace.edict.not_in_force", name, village));
		}
		if (by != null && !VillageProtection.mayBuild(level, entity, by)) {
			return Result.no(Component.translatable("message.aliveworkplace.edict.not_allowed_lift", entity.ownerName(), village));
		}
		long from = found.day() + MIN_DAYS;
		if (Chronicle.day(level) < from) {
			return Result.no(Words.counted("message.aliveworkplace.edict.too_soon", MIN_DAYS, name, MIN_DAYS, from));
		}
		List<InForce> next = new ArrayList<>(entity.edicts());
		next.remove(found);
		entity.setEdicts(next);
		Chronicle.record(level, hall, Chronicle.Kind.EDICT, Component.translatable("chronicle.aliveworkplace.edict.lifted", name), true);
		Component told = Component.translatable("message.aliveworkplace.edict.lifted", village, name).withStyle(ChatFormatting.GOLD);
		return new Result(true, told, announce(level, hall, told));
	}

	/** A village that dropped a rank loses its newest edicts until they fit its slots (told and chronicled). */
	public static void fit(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity) {
		int slots = slots(entity.rank());
		while (entity.edicts().size() > slots) {
			List<InForce> next = new ArrayList<>(entity.edicts());
			InForce newest = next.remove(next.size() - 1);
			entity.setEdicts(next);
			Component name = name(newest.id());
			Chronicle.record(level, hall, Chronicle.Kind.EDICT,
				Component.translatable("chronicle.aliveworkplace.edict.lapsed", name, entity.rank().title()), true);
			announce(level, hall, Component.translatable("message.aliveworkplace.edict.lapsed", VillageHalls.name(level, hall), entity.rank().title(),
				Words.counted("message.aliveworkplace.edict.count", slots, slots), name).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Tells the players in the village; returns who was told. */
	private static List<ServerPlayer> announce(ServerLevel level, BlockPos hall, Component message) {
		double r = (double) VillageHalls.RADIUS * VillageHalls.RADIUS;
		List<ServerPlayer> players = level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r);
		for (ServerPlayer player : players) {
			Chat.chat(player, message);
		}
		io.github.jcondedata.aliveworkplace.people.Moods.forget();
		return players;
	}

	private Edicts() {
	}
}

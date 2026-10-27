package io.github.jcondedata.aliveworkplace.bounty;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * The bounty board and bandit captains (ROADMAP 31.13). From Village rank a village posts a bounty on a named bandit
 * captain while it has fewer than {@link #MAX_OPEN} open, at most one every {@link #EVERY_DAYS} days; each stays up
 * {@link #LIFE_DAYS} days. His hideout, the bandit camp with his own banner by the fire, is placed by the arc engine's
 * {@code place} ({@link ArcEffects#spot}, {@link ArcEffects#build}) 120 to 200 blocks out, and built with him and his
 * band in it once a player comes within {@code Arcs.NEAR} blocks. Taking a bounty (the Bounties tab of the hall's
 * journal, also opened by a Bounty Board) gives a Wanted Poster and a map to the hideout. He drops his Captain's
 * Insignia; handed in at the board or the hall it pays {@link #REWARD} emeralds times the rank factor, once. An
 * unclaimed bounty comes down after {@link #LIFE_DAYS} days and the band leaves in a puff. A standing lair's captain
 * (32.3) is posted too, and paid to his killer when he falls. A village set At peace or at threat level 0 posts none
 * ({@link #quiet}, B134). Config {@code bountyBoard}. With Cobblemon, half the
 * posters are on a rogue Pokémon instead ({@link Rogues}, 31.14).
 *
 * <p>Names come from {@code data/<namespace>/bounties/captains.json} ({@code first_names}, {@code epithets}), quirks
 * from {@code bounties/quirks/<id>.json} (the mob, its gear and enchantments, effects, extra health, mount, band size,
 * and whether he is invisible at night until hit).
 */
public final class Bounties implements ResourceManagerReloadListener {
	public static final String FOLDER = "bounties";
	/** Config {@code bountyBoard}. */
	public static boolean ENABLED = true;
	/** Villages post by themselves (off in GameTests: they post with {@link #post}). */
	public static boolean AUTO = true;
	public static final int MAX_OPEN = 2;
	public static final int EVERY_DAYS = 3;
	public static final int LIFE_DAYS = 7;
	public static final int REWARD = 12;
	public static final int NEAR = 120;
	public static final int FAR = 200;
	public static final int CHECK_EVERY = 40;
	public static final ResourceLocation HIDEOUT = AliveWorkplace.id("camp/bandit_camp");
	/** Every captain, band member and mount of a bounty carries this tag. */
	public static final String TAG = "aliveworkplace_bounty";
	public static final String CAPTAIN_TAG = "aliveworkplace_bounty_captain";
	/** A Night Stalker who has been hit (seen for good). */
	public static final String SEEN_TAG = "aliveworkplace_bounty_seen";
	/** Where the camp's fire and the chief stand in the camp's template (as {@code Lairs}). */
	static final BlockPos FIRE = new BlockPos(7, 1, 6);
	static final BlockPos CHIEF = new BlockPos(7, 1, 9);
	static final BlockPos POLE = new BlockPos(9, 1, 6);
	private static final Block[] BANNERS = {Blocks.RED_BANNER, Blocks.BLACK_BANNER, Blocks.GRAY_BANNER, Blocks.BROWN_BANNER,
		Blocks.PURPLE_BANNER, Blocks.ORANGE_BANNER, Blocks.GREEN_BANNER, Blocks.BLUE_BANNER};

	/** A quirk, as read from its file. */
	public record Quirk(ResourceLocation id, ResourceLocation entity, Map<EquipmentSlot, ResourceLocation> gear,
						Map<ResourceLocation, Integer> enchant, List<Effect> effects, double health, Optional<ResourceLocation> mount, int band,
						boolean nightInvisible) {
		public Component name() {
			return Component.translatable("bounty." + id.getNamespace() + ".quirk." + id.getPath());
		}
	}

	public record Effect(ResourceLocation id, int amplifier) {
	}

	private static List<String> firstNames = List.of("Grimwald");
	private static List<String> epithets = List.of("the Red");
	private static Map<ResourceLocation, Quirk> quirks = Map.of();

	public static void init() {
		Platform.get().onDataReload(AliveWorkplace.id(FOLDER), new Bounties());
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % CHECK_EVERY == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					check(level);
				}
			}
			if (server.getTickCount() % Rogues.GLOW_EVERY == 0) {
				for (ServerLevel level : server.getAllLevels()) {
					Rogues.glow(level); // a rogue glows for its bounty's takers (31.14)
				}
			}
		});
		Lairs.onBroken(Bounties::lairBroken);
	}

	public static List<String> firstNames() {
		return firstNames;
	}

	public static List<String> epithets() {
		return epithets;
	}

	public static Map<ResourceLocation, Quirk> quirks() {
		return quirks;
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<ResourceLocation, Quirk> read = new LinkedHashMap<>();
		Map<ResourceLocation, Rogues.Group> rogues = new LinkedHashMap<>();
		for (Map.Entry<ResourceLocation, Resource> e : new java.util.TreeMap<>(manager.listResources(FOLDER, p -> p.getPath().endsWith(".json"))).entrySet()) {
			String path = e.getKey().getPath().substring(FOLDER.length() + 1, e.getKey().getPath().length() - ".json".length());
			try (Reader reader = e.getValue().openAsReader()) {
				JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
				if (path.equals("captains")) {
					List<String> first = new ArrayList<>();
					List<String> epi = new ArrayList<>();
					GsonHelper.getAsJsonArray(json, "first_names").forEach(n -> first.add(n.getAsString()));
					GsonHelper.getAsJsonArray(json, "epithets").forEach(n -> epi.add(n.getAsString()));
					if (!first.isEmpty() && !epi.isEmpty()) {
						firstNames = List.copyOf(first);
						epithets = List.copyOf(epi);
					}
				} else if (path.startsWith("quirks/")) {
					ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring("quirks/".length()));
					read.put(id, readQuirk(id, json));
				} else if (path.startsWith("rogues/")) {
					ResourceLocation id = ResourceLocation.fromNamespaceAndPath(e.getKey().getNamespace(), path.substring("rogues/".length()));
					rogues.put(id, Rogues.read(id, json));
				}
			} catch (Exception ex) {
				AliveWorkplace.LOG.warn("Skipping bounty file {}: {}", e.getKey(), ex.getMessage());
			}
		}
		quirks = java.util.Collections.unmodifiableMap(read);
		Rogues.setGroups(rogues);
	}

	public static Quirk readQuirk(ResourceLocation id, JsonObject json) {
		ResourceLocation entity = ResourceLocation.parse(GsonHelper.getAsString(json, "entity", "minecraft:vindicator"));
		if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)) {
			throw new IllegalArgumentException("no such entity: " + entity);
		}
		Map<EquipmentSlot, ResourceLocation> gear = new EnumMap<>(EquipmentSlot.class);
		if (json.has("gear")) {
			for (Map.Entry<String, JsonElement> g : json.getAsJsonObject("gear").entrySet()) {
				ResourceLocation item = ResourceLocation.parse(g.getValue().getAsString());
				if (!BuiltInRegistries.ITEM.containsKey(item)) {
					throw new IllegalArgumentException("no such item: " + item);
				}
				gear.put(EquipmentSlot.byName(g.getKey()), item);
			}
		}
		Map<ResourceLocation, Integer> enchant = new LinkedHashMap<>();
		if (json.has("enchantments")) {
			json.getAsJsonObject("enchantments").entrySet().forEach(en -> enchant.put(ResourceLocation.parse(en.getKey()), en.getValue().getAsInt()));
		}
		List<Effect> effects = new ArrayList<>();
		if (json.has("effects")) {
			for (JsonElement el : json.getAsJsonArray("effects")) {
				JsonObject o = el.getAsJsonObject();
				ResourceLocation effect = ResourceLocation.parse(GsonHelper.getAsString(o, "id"));
				if (!BuiltInRegistries.MOB_EFFECT.containsKey(effect)) {
					throw new IllegalArgumentException("no such effect: " + effect);
				}
				effects.add(new Effect(effect, GsonHelper.getAsInt(o, "amplifier", 0)));
			}
		}
		Optional<ResourceLocation> mount = json.has("mount") ? Optional.of(ResourceLocation.parse(GsonHelper.getAsString(json, "mount"))) : Optional.empty();
		return new Quirk(id, entity, gear, enchant, effects, GsonHelper.getAsDouble(json, "health", 0), mount,
			Math.max(0, GsonHelper.getAsInt(json, "band", 0)), GsonHelper.getAsBoolean(json, "night_invisible", false));
	}

	// ---- A bounty -------------------------------------------------------------------------------------------------

	/** One bounty: the captain's name and quirk, his hideout, when it went up, and how it stands. */
	public static final class Bounty {
		public final UUID id;
		public final BlockPos hall;
		public final int first;
		public final int epithet;
		public final ResourceLocation quirk;
		public final ArcState.ArcPlace hideout;
		public final long posted;
		public final int reward;
		/** A standing lair's captain (32.3): his UUID is the lair's; first and epithet are -1. */
		public final boolean lair;
		@Nullable
		public UUID captain;
		public final List<UUID> band = new ArrayList<>();
		public boolean fallen;
		@Nullable
		public UUID killer;
		public final Set<UUID> takers = new LinkedHashSet<>();
		/** A rogue Pokémon's species (31.14), or null for a captain; the rogue's UUID is {@link #captain}. */
		@Nullable
		public ResourceLocation rogue;
		/** A rogue's level. */
		public int rogueLevel;

		public Bounty(UUID id, BlockPos hall, int first, int epithet, ResourceLocation quirk, ArcState.ArcPlace hideout, long posted, int reward, boolean lair) {
			this.id = id;
			this.hall = hall.immutable();
			this.first = first;
			this.epithet = epithet;
			this.quirk = quirk;
			this.hideout = hideout;
			this.posted = posted;
			this.reward = reward;
			this.lair = lair;
		}

		/** "Grimwald the Red" (a lair's captain: his lair's name for him). */
		public Component name(ServerLevel level) {
			if (rogue != null) {
				return Component.translatable("bounty.aliveworkplace.rogue_name", io.github.jcondedata.aliveworkplace.story.RoguePokemon.nameOf(rogue));
			}
			if (lair) {
				return Lairs.all(level).stream().filter(l -> l.captain().equals(captain)).findFirst().map(Lairs::captainName)
					.orElseGet(() -> Component.translatable("entity.aliveworkplace.raider_captain"));
			}
			return name(first, epithet);
		}

		public static Component name(int first, int epithet) {
			String f = firstNames.get(Math.floorMod(first, firstNames.size()));
			String e = epithets.get(Math.floorMod(epithet, epithets.size()));
			return Component.translatable("bounty.aliveworkplace.captain_name", f,
				Component.translatableWithFallback("bounty.aliveworkplace.epithet." + e.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]+", "_"), e));
		}

		public Component quirkName() {
			if (rogue != null) {
				return Component.translatable("bounty.aliveworkplace.quirk.rogue", rogueLevel);
			}
			return lair ? Component.translatable("bounty.aliveworkplace.quirk.lair")
				: Component.translatable("bounty." + quirk.getNamespace() + ".quirk." + quirk.getPath());
		}

		public long daysLeft(long today) {
			return Math.max(0, posted + LIFE_DAYS - today);
		}

		CompoundTag save() {
			CompoundTag c = new CompoundTag();
			Nbt.putUuid(c, "id", id);
			c.putLong("hall", hall.asLong());
			c.putInt("first", first);
			c.putInt("epithet", epithet);
			c.putString("quirk", quirk.toString());
			c.putLong("posted", posted);
			c.putInt("reward", reward);
			c.putBoolean("lair", lair);
			if (captain != null) {
				Nbt.putUuid(c, "captain", captain);
			}
			ListTag b = new ListTag();
			band.forEach(u -> b.add(StringTag.valueOf(u.toString())));
			c.put("band", b);
			c.putBoolean("fallen", fallen);
			if (killer != null) {
				Nbt.putUuid(c, "killer", killer);
			}
			ListTag t = new ListTag();
			takers.forEach(u -> t.add(StringTag.valueOf(u.toString())));
			c.put("takers", t);
			if (rogue != null) {
				c.putString("rogue", rogue.toString());
				c.putInt("rogue_level", rogueLevel);
			}
			CompoundTag h = new CompoundTag();
			h.putLong("spot", hideout.spot.asLong());
			h.putInt("placed", hideout.placed);
			h.putInt("rotation", hideout.rotation);
			h.putBoolean("skipped", hideout.skipped);
			if (hideout.origin != null) {
				h.putLong("origin", hideout.origin.asLong());
			}
			c.put("hideout", h);
			return c;
		}

		static Bounty load(CompoundTag c) {
			CompoundTag h = Nbt.getCompound(c, "hideout");
			ArcState.ArcPlace place = new ArcState.ArcPlace("hideout", BlockPos.of(Nbt.getLong(h, "spot")), List.of(HIDEOUT.toString()), Nbt.getInt(h, "rotation"));
			place.placed = Nbt.getInt(h, "placed");
			place.skipped = Nbt.getBoolean(h, "skipped");
			if (Nbt.has(h, "origin", Tag.TAG_LONG)) {
				place.origin = BlockPos.of(Nbt.getLong(h, "origin"));
			}
			ResourceLocation quirk = ResourceLocation.tryParse(Nbt.getString(c, "quirk"));
			Bounty b = new Bounty(Nbt.getUuid(c, "id"), BlockPos.of(Nbt.getLong(c, "hall")), Nbt.getInt(c, "first"), Nbt.getInt(c, "epithet"),
				quirk == null ? AliveWorkplace.id("ironhide") : quirk, place, Nbt.getLong(c, "posted"),
				Nbt.has(c, "reward", Tag.TAG_INT) ? Nbt.getInt(c, "reward") : REWARD, Nbt.getBoolean(c, "lair"));
			if (Nbt.hasUuid(c, "captain")) {
				b.captain = Nbt.getUuid(c, "captain");
			}
			ListTag band = Nbt.getList(c, "band", Tag.TAG_STRING);
			for (int i = 0; i < band.size(); i++) {
				b.band.add(UUID.fromString(Nbt.stringAt(band, i)));
			}
			b.fallen = Nbt.getBoolean(c, "fallen");
			if (Nbt.hasUuid(c, "killer")) {
				b.killer = Nbt.getUuid(c, "killer");
			}
			ListTag takers = Nbt.getList(c, "takers", Tag.TAG_STRING);
			for (int i = 0; i < takers.size(); i++) {
				b.takers.add(UUID.fromString(Nbt.stringAt(takers, i)));
			}
			if (Nbt.has(c, "rogue", Tag.TAG_STRING)) {
				b.rogue = ResourceLocation.tryParse(Nbt.getString(c, "rogue"));
				b.rogueLevel = Nbt.getInt(c, "rogue_level");
			}
			return b;
		}
	}

	// ---- Posting ---------------------------------------------------------------------------------------------------

	/** The open bounties of the village round {@code hall}, oldest first. */
	public static List<Bounty> open(ServerLevel level, BlockPos hall) {
		return Data.get(level).bounties.values().stream().filter(b -> b.hall.equals(hall)).toList();
	}

	/**
	 * Whether the village round {@code hall} is quiet (B134, 32.21): set At peace, the threat level 0, or Peaceful. No new
	 * bounty is posted there (a captain's, a lair's or a rogue's); the open ones stay until they lapse or are paid.
	 */
	public static boolean quiet(ServerLevel level, BlockPos hall) {
		return Threats.quiet(level, hall);
	}

	/**
	 * Whether the village round {@code hall} may post a bounty today: on, Village rank or more, not {@link #quiet}, fewer
	 * than 2 open, 3 days since the last.
	 */
	public static boolean mayPost(ServerLevel level, BlockPos hall) {
		if (!ENABLED || VillageRanks.of(level, hall).ordinal() < VillageRanks.Rank.VILLAGE.ordinal() || quiet(level, hall)) {
			return false;
		}
		Data data = Data.get(level);
		if (open(level, hall).stream().filter(b -> !b.lair).count() >= MAX_OPEN) {
			return false;
		}
		Long last = data.last.get(hall);
		return last == null || Chronicle.day(level) - last >= EVERY_DAYS;
	}

	/**
	 * The hall's round: bounties past their days come down, a lair's captain is posted, and (by itself) a new captain;
	 * nothing new in a {@link #quiet} village.
	 */
	public static void round(ServerLevel level, BlockPos hall) {
		long today = Chronicle.day(level);
		for (Bounty b : List.copyOf(open(level, hall))) {
			if (today >= b.posted + LIFE_DAYS) {
				takeDown(level, b);
			}
		}
		if (!ENABLED || VillageRanks.of(level, hall).ordinal() < VillageRanks.Rank.VILLAGE.ordinal() || quiet(level, hall)) {
			return;
		}
		Lairs.near(level, hall).ifPresent(lair -> {
			if (open(level, hall).stream().noneMatch(b -> b.lair && lair.captain().equals(b.captain))) {
				postLair(level, hall, lair);
			}
		});
		if (AUTO && mayPost(level, hall)) {
			// with Cobblemon, half the posters are on a rogue Pokémon (31.14); a captain if no spot round has one
			if (!Rogues.available() || !level.random.nextBoolean() || Rogues.post(level, hall, level.random) == null) {
				post(level, hall, level.random);
			}
		}
	}

	/** Posts a bounty on a new captain if the village may; null if it may not or there's nowhere for his hideout. */
	@Nullable
	public static Bounty post(ServerLevel level, BlockPos hall, net.minecraft.util.RandomSource random) {
		if (!mayPost(level, hall) || quirks.isEmpty()) {
			return null;
		}
		JsonObject rule = new JsonObject();
		com.google.gson.JsonArray ring = new com.google.gson.JsonArray();
		ring.add(NEAR);
		ring.add(FAR);
		rule.add("ring", ring);
		BlockPos spot = ArcEffects.spot(level, hall, new ArcState(AliveWorkplace.id("bounty").toString()), rule);
		if (spot == null) {
			return null;
		}
		List<ResourceLocation> ids = new ArrayList<>(quirks.keySet());
		return post(level, hall, ids.get(random.nextInt(ids.size())), random.nextInt(firstNames.size()), random.nextInt(epithets.size()), spot);
	}

	/** Posts a bounty on the captain {@code first epithet} with {@code quirk}, his hideout to be built at {@code spot}. */
	public static Bounty post(ServerLevel level, BlockPos hall, ResourceLocation quirk, int first, int epithet, BlockPos spot) {
		Data data = Data.get(level);
		long today = Chronicle.day(level);
		int reward = Math.round(REWARD * VillageRanks.questRewardFactor(VillageRanks.of(level, hall)));
		Bounty b = new Bounty(UUID.randomUUID(), hall, first, epithet, quirk,
			new ArcState.ArcPlace("hideout", spot, List.of(HIDEOUT.toString()), level.random.nextInt(4)), today, reward, false);
		data.bounties.put(b.id, b);
		data.last.put(hall.immutable(), today);
		data.setDirty();
		Component name = b.name(level);
		Component where = VillageHallScreen.where(hall, spot);
		for (ServerPlayer player : players(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.bounty.posted", VillageHalls.name(level, hall), name, where, b.reward)
				.withStyle(ChatFormatting.GOLD));
		}
		Chronicle.record(level, hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.posted", name, b.quirkName(), where));
		refreshBoards(level);
		return b;
	}

	/** A standing lair's captain posted: his name and his lair's place. */
	static Bounty postLair(ServerLevel level, BlockPos hall, Lairs.Lair lair) {
		Data data = Data.get(level);
		ArcState.ArcPlace place = new ArcState.ArcPlace("hideout", lair.pos(), List.of(HIDEOUT.toString()), 0);
		place.placed = 1; // it stands already
		Bounty b = new Bounty(UUID.randomUUID(), hall, -1, -1, AliveWorkplace.id("lair"), place, Chronicle.day(level),
			Math.round(REWARD * VillageRanks.questRewardFactor(VillageRanks.of(level, hall))), true);
		b.captain = lair.captain();
		data.bounties.put(b.id, b);
		data.setDirty();
		Chronicle.record(level, hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.posted", b.name(level), b.quirkName(),
			VillageHallScreen.where(hall, lair.pos())));
		refreshBoards(level);
		return b;
	}

	/** The bounty comes down: its band leaves in a puff. */
	public static void takeDown(ServerLevel level, Bounty b) {
		Data data = Data.get(level);
		data.bounties.remove(b.id);
		data.setDirty();
		Rogues.forget(b.id);
		if (!b.lair) {
			for (Mob mob : band(level, b)) {
				level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
				mob.discard();
			}
		}
		Component name = b.name(level);
		for (ServerPlayer player : players(level, b.hall)) {
			Chat.chat(player, Component.translatable(b.rogue != null ? "message.aliveworkplace.bounty.rogue_expired" : "message.aliveworkplace.bounty.expired", name)
				.withStyle(ChatFormatting.GRAY));
		}
		Chronicle.record(level, b.hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.expired", name));
		refreshBoards(level);
	}

	/** The captain, his band and his mount, wherever they are loaded near his hideout. */
	public static List<Mob> band(ServerLevel level, Bounty b) {
		Set<UUID> ours = new LinkedHashSet<>(b.band);
		if (b.captain != null) {
			ours.add(b.captain);
		}
		List<Mob> out = new ArrayList<>(level.getEntitiesOfClass(Mob.class, new AABB(b.hideout.spot).inflate(64, 32, 64),
			m -> m.isAlive() && ours.contains(m.getUUID())));
		for (UUID u : ours) {
			if (level.getEntity(u) instanceof Mob m && m.isAlive() && !out.contains(m)) {
				out.add(m);
			}
		}
		return out;
	}

	// ---- The hideout -----------------------------------------------------------------------------------------------

	/** Every {@link #CHECK_EVERY} ticks: a waiting hideout with a player near is built and manned; Night Stalkers hide. */
	public static void check(ServerLevel level) {
		Data data = Data.get(level);
		if (data.bounties.isEmpty()) {
			refreshBoards(level);
			return;
		}
		ArcState state = new ArcState(AliveWorkplace.id("bounty").toString());
		for (Bounty b : List.copyOf(data.bounties.values())) {
			if (b.lair) {
				continue;
			}
			if (b.rogue != null) {
				Rogues.check(level, b);
				continue;
			}
			ArcState.ArcPlace p = b.hideout;
			if (p.waiting() && ArcEffects.near(level, p.spot) && level.isLoaded(p.spot)) {
				if (ArcEffects.build(level, state, p)) {
					data.setDirty();
					if (!p.skipped && p.origin != null) {
						Rotation rotation = Rotation.values()[Math.floorMod(p.rotation, 4)];
						StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
						BlockPos fire = p.origin.offset(StructureTemplate.calculateRelativePosition(settings, FIRE));
						banner(level, b, p.origin.offset(StructureTemplate.calculateRelativePosition(settings, POLE)));
						raise(level, b, p.origin.offset(StructureTemplate.calculateRelativePosition(settings, CHIEF)), fire);
					}
				}
			}
			if (b.captain != null && quirk(b).map(Quirk::nightInvisible).orElse(false)
				&& level.getEntity(b.captain) instanceof Mob captain && captain.isAlive()) {
				stalk(level, captain);
			}
		}
		refreshBoards(level);
	}

	/** A Night Stalker is invisible at night until he's hit (then seen for good). */
	public static void stalk(ServerLevel level, Mob captain) {
		if (captain.getTags().contains(SEEN_TAG) || captain.getHealth() < captain.getMaxHealth()) {
			captain.addTag(SEEN_TAG);
			captain.removeEffect(MobEffects.INVISIBILITY);
			return;
		}
		long time = Math.floorMod(level.getDayTime(), 24000L);
		if (!level.dimensionType().hasFixedTime() && time >= 13000 && time < 23000) { // night, from the clock (the sky's darkness lags a tick)
			captain.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, CHECK_EVERY * 3, 0, false, false));
		} else {
			captain.removeEffect(MobEffects.INVISIBILITY);
		}
	}

	/** His own banner on a pole by the fire: a fence post with a banner of his colour on top, on the first open ground round {@code at}. */
	static void banner(ServerLevel level, Bounty b, BlockPos at) {
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
						continue;
					}
					for (int dy = -1; dy <= 2; dy++) {
						BlockPos ground = at.offset(dx, dy, dz);
						if (level.getBlockState(ground).canBeReplaced() && level.getBlockState(ground.above()).canBeReplaced()
							&& level.getBlockState(ground.below()).isFaceSturdy(level, ground.below(), net.minecraft.core.Direction.UP)) {
							level.setBlock(ground, Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
							level.setBlock(ground.above(), BANNERS[Math.floorMod(b.first + b.epithet, BANNERS.length)].defaultBlockState(), 3);
							return;
						}
					}
				}
			}
		}
	}

	public static Optional<Quirk> quirk(Bounty b) {
		return Optional.ofNullable(quirks.get(b.quirk));
	}

	/** The captain (with his quirk) at {@code at} and his band round {@code fire}; true if he stands. */
	public static boolean raise(ServerLevel level, Bounty b, BlockPos at, BlockPos fire) {
		Quirk quirk = quirk(b).orElse(null);
		if (quirk == null) {
			AliveWorkplace.LOG.warn("Bounty on {}: no quirk {}; nobody comes", b.name(level).getString(), b.quirk);
			return false;
		}
		Mob captain = create(level, quirk.entity(), stand(level, at), fire);
		if (captain == null) {
			return false;
		}
		captain.addTag(CAPTAIN_TAG);
		captain.setCustomName(b.name(level));
		captain.setCustomNameVisible(true);
		quirk.gear().forEach((slot, item) -> {
			ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(item));
			net.minecraft.core.Registry<Enchantment> enchantments = Lookup.registry(level.registryAccess(), Registries.ENCHANTMENT);
			if (slot == EquipmentSlot.MAINHAND) {
				quirk.enchant().forEach((id, lvl) -> Lookup.holder(enchantments,
					ResourceKey.create(Registries.ENCHANTMENT, id)).ifPresent(h -> stack.enchant(h, lvl)));
			}
			captain.setItemSlot(slot, stack);
			captain.setDropChance(slot, 0f);
		});
		io.github.jcondedata.aliveworkplace.threat.Threats.captainHealth(captain, AliveWorkplace.id("bounty_captain"), quirk.health());
		for (Effect e : quirk.effects()) {
			Lookup.holder(BuiltInRegistries.MOB_EFFECT, ResourceKey.create(Registries.MOB_EFFECT, e.id()))
				.ifPresent(h -> captain.addEffect(new MobEffectInstance(h, MobEffectInstance.INFINITE_DURATION, e.amplifier(), false, true)));
		}
		if (!io.github.jcondedata.aliveworkplace.threat.HostileSpawns.add(level, captain, "bounty hideout at " + at.toShortString())) {
			return false; // (said in the log: B208)
		}
		b.captain = captain.getUUID();
		if (quirk.mount().isPresent()) {
			Mob mount = create(level, quirk.mount().get(), captain.blockPosition(), fire);
			if (mount != null && io.github.jcondedata.aliveworkplace.threat.HostileSpawns.add(level, mount, "bounty hideout at " + at.toShortString())) {
				captain.startRiding(mount, true);
				b.band.add(mount.getUUID());
			}
		}
		int size = quirk.band() > 0 ? quirk.band() : 2 + level.random.nextInt(2);
		for (int i = 0; i < size; i++) {
			double angle = i * 2.399963;
			BlockPos spot = stand(level, fire.offset((int) Math.round(Math.cos(angle) * 3), 0, (int) Math.round(Math.sin(angle) * 3)));
			Mob man = create(level, i % 2 == 0 ? BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.VINDICATOR) : BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.PILLAGER), spot, fire);
			if (man != null) {
				man.setCustomName(Component.translatable("entity.aliveworkplace.bandit"));
				if (io.github.jcondedata.aliveworkplace.threat.HostileSpawns.add(level, man, "bounty hideout at " + at.toShortString())) {
					b.band.add(man.getUUID());
				}
			}
		}
		if (quirk.nightInvisible()) {
			stalk(level, captain);
		}
		Data.get(level).setDirty();
		AliveWorkplace.LOG.info("Bounty captain {} ({}) at his hideout {} with a band of {}", b.name(level).getString(), b.quirk, at, size);
		return true;
	}

	@Nullable
	private static Mob create(ServerLevel level, ResourceLocation type, BlockPos at, BlockPos home) {
		Entity made = BuiltInRegistries.ENTITY_TYPE.getOptional(type).map(t -> t.create(level)).orElse(null);
		if (!(made instanceof Mob mob)) {
			if (made != null) {
				made.discard();
			}
			return null;
		}
		mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
		}
		if (mob instanceof PathfinderMob pathfinder) {
			pathfinder.restrictTo(home, 14);
		}
		return mob;
	}

	private static BlockPos stand(ServerLevel level, BlockPos at) {
		BlockPos p = at;
		for (int i = 0; i < 4 && !level.getBlockState(p).getCollisionShape(level, p).isEmpty(); i++) {
			p = p.above();
		}
		return p;
	}

	// ---- Taking, the kill and the claim ----------------------------------------------------------------------------

	/** {@code player} takes the bounty: a Wanted Poster and a map to the hideout (once each). False if they had it. */
	public static boolean take(ServerPlayer player, ServerLevel level, Bounty b) {
		if (!b.takers.add(player.getUUID())) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.bounty.have", b.name(level)).withStyle(ChatFormatting.YELLOW));
			return false;
		}
		Data.get(level).setDirty();
		give(player, poster(level, b));
		give(player, map(level, b));
		Chat.chat(player, Component.translatable("message.aliveworkplace.bounty.taken", b.name(level), VillageHallScreen.where(b.hall, b.hideout.spot))
			.withStyle(ChatFormatting.GOLD));
		level.playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
		return true;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		if (!player.getInventory().add(stack)) {
			player.drop(stack, false);
		}
	}

	/** The Wanted Poster: his name, quirk, where he was last seen and the reward, in its tooltip. */
	public static ItemStack poster(ServerLevel level, Bounty b) {
		ItemStack stack = new ItemStack(ModItems.WANTED_POSTER);
		CompoundTag tag = new CompoundTag();
		Nbt.putUuid(tag, "bounty", b.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.wanted_poster.of", b.name(level)));
		stack.set(DataComponents.LORE, new ItemLore(lines(level, b)));
		return stack;
	}

	/** The poster's lines (also the journal's). */
	public static List<Component> lines(ServerLevel level, Bounty b) {
		List<Component> out = new ArrayList<>();
		out.add(plain(Component.translatable("tooltip.aliveworkplace.wanted_poster.name", b.name(level)), ChatFormatting.WHITE));
		out.add(plain(Component.translatable("tooltip.aliveworkplace.wanted_poster.quirk", b.quirkName()), ChatFormatting.RED));
		out.add(plain(Component.translatable("tooltip.aliveworkplace.wanted_poster.seen", VillageHallScreen.where(b.hall, b.hideout.spot)), ChatFormatting.GRAY));
		out.add(plain(Component.translatable("tooltip.aliveworkplace.wanted_poster.reward", b.reward), ChatFormatting.GREEN));
		return out;
	}

	private static Component plain(Component c, ChatFormatting color) {
		return c.copy().withStyle(s -> s.withItalic(false).withColor(color));
	}

	/** A map to his hideout, marked with a red X. */
	public static ItemStack map(ServerLevel level, Bounty b) {
		BlockPos pos = b.hideout.spot;
		ItemStack map = MapItem.create(level, pos.getX(), pos.getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, pos, "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.hideout_map", b.name(level)));
		return map;
	}

	/** The Captain's Insignia of bounty {@code b}. */
	public static ItemStack insignia(ServerLevel level, Bounty b) {
		ItemStack stack = new ItemStack(ModItems.CAPTAINS_INSIGNIA);
		CompoundTag tag = new CompoundTag();
		Nbt.putUuid(tag, "bounty", b.id);
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		stack.set(DataComponents.LORE, new ItemLore(List.of(
			plain(Component.translatable("tooltip.aliveworkplace.captains_insignia.of", b.name(level)), ChatFormatting.GRAY),
			plain(Component.translatable("tooltip.aliveworkplace.captains_insignia.hint", VillageHalls.name(level, b.hall)), ChatFormatting.DARK_GRAY))));
		return stack;
	}

	@Nullable
	public static UUID bountyOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		CompoundTag tag = data.copyTag();
		return Nbt.hasUuid(tag, "bounty") ? Nbt.getUuid(tag, "bounty") : null;
	}

	/** Every death: a bounty's captain drops his insignia; a lair captain's bounty is paid on {@link #lairBroken}. */
	public static void onDeath(ServerLevel level, LivingEntity entity, DamageSource source) {
		if (!entity.getTags().contains(CAPTAIN_TAG)) {
			return;
		}
		Data data = Data.get(level);
		for (Bounty b : data.bounties.values()) {
			if (entity.getUUID().equals(b.captain) && !b.fallen) {
				b.fallen = true;
				b.killer = source.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
				data.setDirty();
				entity.spawnAtLocation(insignia(level, b));
				Chronicle.record(level, b.hall, Chronicle.Kind.BOUNTY, source.getEntity() instanceof ServerPlayer p
					? Component.translatable("chronicle.aliveworkplace.bounty.fell_to", b.name(level), p.getDisplayName())
					: Component.translatable("chronicle.aliveworkplace.bounty.fell", b.name(level)));
				return;
			}
		}
	}

	/** A lair whose captain is posted falls: the bounty is paid to his killer. */
	static void lairBroken(ServerLevel level, Lairs.Lair lair, @Nullable Entity by) {
		Data data = Data.get(level);
		for (Bounty b : List.copyOf(data.bounties.values())) {
			if (b.lair && lair.captain().equals(b.captain)) {
				Component name = Lairs.captainName(lair);
				data.bounties.remove(b.id);
				data.claimed.add(b.id);
				data.setDirty();
				if (by instanceof ServerPlayer player) {
					pay(player, b.reward);
					io.github.jcondedata.aliveworkplace.story.Reputation.add(level, b.hall, player, io.github.jcondedata.aliveworkplace.story.Reputation.BOUNTY);
					Chat.chat(player, Component.translatable("message.aliveworkplace.bounty.paid", b.reward, name).withStyle(ChatFormatting.GREEN));
					Chronicle.record(level, b.hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.claimed", player.getDisplayName(), name, b.reward));
				}
				refreshBoards(level);
			}
		}
	}

	/** The result of handing an insignia in. */
	public enum Claim { PAID, ALREADY, NOT_HERE, NONE }

	/**
	 * {@code player} hands in the insignia in {@code stack} at the village round {@code hall}: {@link #REWARD} emeralds
	 * times the rank factor, once, to whoever hands it in; the chronicle says who.
	 */
	public static Claim claim(ServerPlayer player, ServerLevel level, BlockPos hall, ItemStack stack) {
		UUID id = bountyOf(stack);
		Data data = Data.get(level);
		if (id == null) {
			return Claim.NONE;
		}
		Bounty b = data.bounties.get(id);
		if (b == null) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.bounty.already").withStyle(ChatFormatting.YELLOW));
			return Claim.ALREADY;
		}
		if (!b.hall.equals(hall)) {
			Chat.actionBar(player, Component.translatable("message.aliveworkplace.bounty.not_here", VillageHalls.name(level, b.hall)).withStyle(ChatFormatting.YELLOW));
			return Claim.NOT_HERE;
		}
		Component name = b.name(level);
		stack.shrink(1);
		data.bounties.remove(id);
		data.claimed.add(id);
		data.setDirty();
		pay(player, b.reward);
		io.github.jcondedata.aliveworkplace.story.Reputation.add(level, hall, player, io.github.jcondedata.aliveworkplace.story.Reputation.BOUNTY);
		Chat.chat(player, Component.translatable("message.aliveworkplace.bounty.paid", b.reward, name).withStyle(ChatFormatting.GREEN));
		Chronicle.record(level, hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.claimed", player.getDisplayName(), name, b.reward));
		level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, 1f);
		refreshBoards(level);
		return Claim.PAID;
	}

	/** Pays a bounty: {@code emeralds}, or as many CobbleDollars' worth where that mod keeps the money. */
	static void pay(ServerPlayer player, int emeralds) {
		Money.pay(player, (long) emeralds * Money.DOLLARS_PER_EMERALD, emeralds);
	}

	/** Hands in the first insignia {@code player} carries. */
	public static Claim claimAny(ServerPlayer player, ServerLevel level, BlockPos hall) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(ModItems.CAPTAINS_INSIGNIA)) {
				return claim(player, level, hall, stack);
			}
		}
		Chat.actionBar(player, Component.translatable("message.aliveworkplace.bounty.no_insignia").withStyle(ChatFormatting.YELLOW));
		return Claim.NONE;
	}

	// ---- Gossip and boards -----------------------------------------------------------------------------------------

	/** What villagers say about a captain posted here ("Grimwald the Red was seen 160 blocks north-east"), or null. */
	@Nullable
	public static Component gossip(ServerLevel level, BlockPos hall, int variant) {
		List<Bounty> open = open(level, hall).stream().filter(b -> !b.fallen).toList();
		if (open.isEmpty()) {
			return null;
		}
		Bounty b = open.get(level.random.nextInt(open.size()));
		return Component.translatable((b.rogue != null ? "chatter.aliveworkplace.bounty.rogue." : "chatter.aliveworkplace.bounty.") + variant, b.name(level), VillageHallScreen.where(hall, b.hideout.spot));
	}

	/** A board was placed (or loaded with a click): it is known and shows the posters of its village. */
	public static void boardPlaced(ServerLevel level, BlockPos pos) {
		Data data = Data.get(level);
		if (data.boards.add(pos.immutable())) {
			data.setDirty();
		}
		refresh(level, pos);
	}

	public static void boardRemoved(ServerLevel level, BlockPos pos) {
		Data data = Data.get(level);
		if (data.boards.remove(pos)) {
			data.setDirty();
		}
	}

	/** How many posters a board near {@code pos} shows: the open bounties of its village, up to 4. */
	public static int posters(ServerLevel level, BlockPos pos) {
		return VillageHalls.nearest(level, pos).map(hall -> Math.min(4, open(level, hall).size())).orElse(0);
	}

	public static void refreshBoards(ServerLevel level) {
		for (BlockPos pos : List.copyOf(Data.get(level).boards)) {
			if (level.isLoaded(pos)) {
				refresh(level, pos);
			}
		}
	}

	private static void refresh(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (!state.is(ModBlocks.BOUNTY_BOARD)) {
			boardRemoved(level, pos);
			return;
		}
		int n = posters(level, pos);
		if (state.getValue(BountyBoardBlock.POSTERS) != n) {
			level.setBlock(pos, state.setValue(BountyBoardBlock.POSTERS, n), 3);
		}
	}

	static List<ServerPlayer> players(ServerLevel level, BlockPos hall) {
		double r = VillageHalls.RADIUS + 32;
		return level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r);
	}

	/** Forgets every bounty and board (tests). */
	public static void forget(ServerLevel level) {
		Data data = Data.get(level);
		data.bounties.clear();
		data.last.clear();
		data.claimed.clear();
		data.boards.clear();
		data.setDirty();
		Rogues.forget();
	}

	// ---- Saved -----------------------------------------------------------------------------------------------------

	public static final class Data extends SavedData {
		public static final String NAME = "aliveworkplace_bounties";
		public final Map<UUID, Bounty> bounties = new LinkedHashMap<>();
		/** The day each village last posted. */
		public final Map<BlockPos, Long> last = new LinkedHashMap<>();
		/** Bounties paid (an insignia of one is worth nothing more). */
		public final Set<UUID> claimed = new LinkedHashSet<>();
		public final Set<BlockPos> boards = new LinkedHashSet<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			bounties.values().forEach(b -> list.add(b.save()));
			tag.put("bounties", list);
			ListTag l = new ListTag();
			last.forEach((hall, day) -> {
				CompoundTag c = new CompoundTag();
				c.putLong("hall", hall.asLong());
				c.putLong("day", day);
				l.add(c);
			});
			tag.put("last", l);
			ListTag c = new ListTag();
			claimed.forEach(u -> c.add(StringTag.valueOf(u.toString())));
			tag.put("claimed", c);
			tag.putLongArray("boards", boards.stream().mapToLong(BlockPos::asLong).toArray());
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag list = Nbt.getList(tag, "bounties", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				try {
					Bounty b = Bounty.load(Nbt.compoundAt(list, i));
					data.bounties.put(b.id, b);
				} catch (Exception e) {
					AliveWorkplace.LOG.warn("Skipping a saved bounty: {}", e.getMessage());
				}
			}
			ListTag l = Nbt.getList(tag, "last", Tag.TAG_COMPOUND);
			for (int i = 0; i < l.size(); i++) {
				CompoundTag c = Nbt.compoundAt(l, i);
				data.last.put(BlockPos.of(Nbt.getLong(c, "hall")), Nbt.getLong(c, "day"));
			}
			ListTag c = Nbt.getList(tag, "claimed", Tag.TAG_STRING);
			for (int i = 0; i < c.size(); i++) {
				data.claimed.add(UUID.fromString(Nbt.stringAt(c, i)));
			}
			for (long p : Nbt.getLongArray(tag, "boards")) {
				data.boards.add(BlockPos.of(p));
			}
			return data;
		}
	}

	private Bounties() {
	}
}

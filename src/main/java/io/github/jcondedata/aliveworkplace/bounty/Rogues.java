package io.github.jcondedata.aliveworkplace.bounty;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.RoguePokemon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * Rogue Pokémon bounties (ROADMAP 31.14), the board's second kind of poster: a wild Pokémon grown huge and mean roams
 * within {@link #ROAM} blocks of a spot {@link #NEAR} to {@link #FAR} blocks from the hall. Its species comes from the
 * biome round the spot, by the files {@code data/<namespace>/bounties/rogues/<id>.json} ({@code biomes}: biome ids and
 * {@code #tags}; {@code species}); a species Cobblemon doesn't have is skipped. Its level is 35 at Village rank, 50 at
 * Town and 65 at City. It's spawned (by {@link RoguePokemon}, so only with Cobblemon) once a player comes near, named
 * "Rogue Ursaring", and glows for the players who took its bounty while they're within {@link #GLOW} blocks. Beaten in
 * battle or caught, it pays {@link #REWARD} emeralds times the rank factor to that player, once. Config
 * {@code rogueBounties}.
 */
public final class Rogues {
	/** Config {@code rogueBounties}. */
	public static boolean ENABLED = true;
	public static final int NEAR = 150;
	public static final int FAR = 250;
	public static final int ROAM = 24;
	public static final int GLOW = 32;
	public static final int REWARD = 16;
	public static final int GLOW_EVERY = 10;
	/** Every rogue carries this tag (vanilla saves it). */
	public static final String TAG = "aliveworkplace_rogue";

	/** A biome group, as read from its file. */
	public record Group(ResourceLocation id, List<ResourceLocation> biomes, List<TagKey<Biome>> tags, List<ResourceLocation> species) {
	}

	private static Map<ResourceLocation, Group> groups = Map.of();
	/** Who sees each rogue glow now (bounty id → players), so it's turned off when they walk away. Not saved. */
	private static final Map<UUID, Set<UUID>> glowing = new HashMap<>();

	public static Map<ResourceLocation, Group> groups() {
		return groups;
	}

	static void setGroups(Map<ResourceLocation, Group> read) {
		groups = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(read));
	}

	/** A group's file: {@code biomes} (a string or a list of biome ids and {@code #tags}) and {@code species}. */
	public static Group read(ResourceLocation id, JsonObject json) {
		List<ResourceLocation> biomes = new ArrayList<>();
		List<TagKey<Biome>> tags = new ArrayList<>();
		JsonElement b = json.get("biomes");
		List<String> entries = new ArrayList<>();
		if (b != null && b.isJsonArray()) {
			b.getAsJsonArray().forEach(e -> entries.add(e.getAsString()));
		} else if (b != null) {
			entries.add(b.getAsString());
		}
		for (String e : entries) {
			if (e.startsWith("#")) {
				tags.add(TagKey.create(Registries.BIOME, ResourceLocation.parse(e.substring(1))));
			} else {
				biomes.add(ResourceLocation.parse(e));
			}
		}
		List<ResourceLocation> species = new ArrayList<>();
		GsonHelper.getAsJsonArray(json, "species").forEach(s -> species.add(ResourceLocation.parse(s.getAsString())));
		if (species.isEmpty() || (biomes.isEmpty() && tags.isEmpty())) {
			throw new IllegalArgumentException("a rogue group needs biomes and species");
		}
		return new Group(id, List.copyOf(biomes), List.copyOf(tags), List.copyOf(species));
	}

	/** Whether rogues are posted at all: on, the board on, and Cobblemon there. */
	public static boolean available() {
		return ENABLED && Bounties.ENABLED && RoguePokemon.present();
	}

	/** The rogue's level for a village of {@code rank}: 35 at Village, 50 at Town, 65 at City (Hamlets post none). */
	public static int level(VillageRanks.Rank rank) {
		return switch (rank) {
			case HAMLET, VILLAGE -> 35;
			case TOWN -> 50;
			case CITY -> 65;
		};
	}

	/** The group of the biome at {@code pos}: one naming the biome itself first, then one with a tag of it; null if none. */
	@Nullable
	public static Group group(ServerLevel level, BlockPos pos) {
		return group(level.getBiome(pos));
	}

	/** The group of {@code biome}: one naming it first, then one with a tag of it; null if none. */
	@Nullable
	public static Group group(Holder<Biome> biome) {
		ResourceLocation id = biome.unwrapKey().map(k -> k.location()).orElse(null);
		for (Group g : groups.values()) {
			if (id != null && g.biomes().contains(id)) {
				return g;
			}
		}
		for (Group g : groups.values()) {
			for (TagKey<Biome> tag : g.tags()) {
				if (biome.is(tag)) {
					return g;
				}
			}
		}
		return null;
	}

	/** The species of {@code group} Cobblemon has, in the file's order. */
	public static List<ResourceLocation> species(Group group) {
		List<ResourceLocation> out = new ArrayList<>();
		for (ResourceLocation s : group.species()) {
			if (RoguePokemon.exists(s)) {
				out.add(s);
			} else {
				AliveWorkplace.LOG.debug("Rogue group {}: Cobblemon has no {}; skipped", group.id(), s);
			}
		}
		return out;
	}

	/** The column the biome is read at: the spot at the hall's height (or sea level, if higher), loaded or not. */
	static BlockPos biomeAt(ServerLevel level, BlockPos hall, BlockPos spot) {
		return new BlockPos(spot.getX(), Math.max(hall.getY(), level.getSeaLevel()), spot.getZ());
	}

	/** A species for a rogue at {@code spot}, from its biome's group; null if the biome has none (or none Cobblemon has). */
	@Nullable
	public static ResourceLocation speciesAt(ServerLevel level, BlockPos hall, BlockPos spot, RandomSource random) {
		Group g = group(level, biomeAt(level, hall, spot));
		if (g == null) {
			return null;
		}
		List<ResourceLocation> s = species(g);
		return s.isEmpty() ? null : s.get(random.nextInt(s.size()));
	}

	// ---- Posting ---------------------------------------------------------------------------------------------------

	/** Posts a rogue if the village may (and Cobblemon is there): a spot 150 to 250 blocks out whose biome has rogues; null if none. */
	@Nullable
	public static Bounties.Bounty post(ServerLevel level, BlockPos hall, RandomSource random) {
		if (!available() || !Bounties.mayPost(level, hall)) {
			return null;
		}
		JsonObject rule = new JsonObject();
		com.google.gson.JsonArray ring = new com.google.gson.JsonArray();
		ring.add(NEAR);
		ring.add(FAR);
		rule.add("ring", ring);
		for (int tries = 0; tries < 8; tries++) {
			BlockPos spot = ArcEffects.spot(level, hall, new ArcState(AliveWorkplace.id("bounty").toString()), rule);
			if (spot == null) {
				return null;
			}
			ResourceLocation species = speciesAt(level, hall, spot, random);
			if (species != null) {
				return post(level, hall, species, spot);
			}
		}
		return null;
	}

	/** Posts a bounty on a rogue {@code species} roaming round {@code spot}, its level by the village's rank. */
	public static Bounties.Bounty post(ServerLevel level, BlockPos hall, ResourceLocation species, BlockPos spot) {
		Bounties.Data data = Bounties.Data.get(level);
		long today = Chronicle.day(level);
		VillageRanks.Rank rank = VillageRanks.of(level, hall);
		int reward = Math.round(REWARD * VillageRanks.questRewardFactor(rank));
		ArcState.ArcPlace place = new ArcState.ArcPlace("hideout", spot, List.of(Bounties.HIDEOUT.toString()), 0);
		place.placed = 1; // nothing is built for a rogue
		Bounties.Bounty b = new Bounties.Bounty(UUID.randomUUID(), hall, -1, -1, AliveWorkplace.id("rogue"), place, today, reward, false);
		b.rogue = species;
		b.rogueLevel = level(rank);
		data.bounties.put(b.id, b);
		data.last.put(hall.immutable(), today);
		data.setDirty();
		Component name = b.name(level);
		Component where = VillageHallScreen.where(hall, spot);
		for (ServerPlayer player : Bounties.players(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.bounty.posted", VillageHalls.name(level, hall), name, where, b.reward)
				.withStyle(ChatFormatting.GOLD));
		}
		Chronicle.record(level, hall, Chronicle.Kind.BOUNTY, Component.translatable("chronicle.aliveworkplace.bounty.posted", name, b.quirkName(), where));
		Bounties.refreshBoards(level);
		return b;
	}

	// ---- The rogue -------------------------------------------------------------------------------------------------

	/** The rogue of {@code b}, if it's loaded and alive. */
	@Nullable
	public static Entity rogue(ServerLevel level, Bounties.Bounty b) {
		Entity e = b.captain == null ? null : level.getEntity(b.captain);
		return e != null && e.isAlive() ? e : null;
	}

	/** Every {@link Bounties#CHECK_EVERY} ticks: with a player near and its ground loaded, the rogue is there (spawned again if gone) and stays round its spot. */
	static void check(ServerLevel level, Bounties.Bounty b) {
		Entity rogue = rogue(level, b);
		BlockPos spot = b.hideout.spot;
		if (rogue == null) {
			if (b.fallen || !ArcEffects.near(level, spot) || !groundLoaded(level, spot)) {
				return;
			}
			spawn(level, b, nearest(level, spot));
			return;
		}
		double far = ROAM + 8;
		if (rogue instanceof PathfinderMob mob && mob.distanceToSqr(spot.getCenter()) > far * far) {
			mob.getNavigation().moveTo(spot.getX() + 0.5, rogue.getY(), spot.getZ() + 0.5, 1.0);
		}
	}

	/** Whether the chunks the rogue may roam in have their entities loaded (so a rogue there is found, never doubled). */
	static boolean groundLoaded(ServerLevel level, BlockPos spot) {
		for (int dx = -ROAM - 8; dx <= ROAM + 8; dx += 16) {
			for (int dz = -ROAM - 8; dz <= ROAM + 8; dz += 16) {
				if (!level.areEntitiesLoaded(ChunkPos.asLong(spot.offset(dx, 0, dz)))) {
					return false;
				}
			}
		}
		return true;
	}

	@Nullable
	private static ServerPlayer nearest(ServerLevel level, BlockPos spot) {
		ServerPlayer best = null;
		double d = Double.MAX_VALUE;
		for (ServerPlayer p : level.players()) {
			double dd = p.distanceToSqr(spot.getCenter());
			if (!p.isSpectator() && dd < d) {
				d = dd;
				best = p;
			}
		}
		return best;
	}

	/**
	 * Spawns the rogue of {@code b} on the ground at its spot (with Radical Cobblemon Trainers its level follows
	 * {@code near}'s level cap); null without Cobblemon. It stays (never despawns) and roams within {@link #ROAM} blocks.
	 */
	@Nullable
	public static Entity spawn(ServerLevel level, Bounties.Bounty b, @Nullable ServerPlayer near) {
		if (b.rogue == null) {
			return null;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.hideout.spot);
		Component name = b.name(level);
		Entity e = RoguePokemon.EXTENSION.call(r -> r.spawn(level, at, b.rogue, b.rogueLevel, name, b.id, near), null);
		if (e == null) {
			return null;
		}
		e.addTag(TAG);
		e.addTag(Bounties.TAG);
		if (e instanceof Mob mob) {
			mob.setPersistenceRequired();
		}
		if (e instanceof PathfinderMob mob) {
			mob.restrictTo(b.hideout.spot, ROAM);
		}
		b.captain = e.getUUID();
		int lvl = RoguePokemon.EXTENSION.call(r -> r.level(e), 0);
		if (lvl > 0) {
			b.rogueLevel = lvl;
		}
		Bounties.Data.get(level).setDirty();
		AliveWorkplace.LOG.info("Rogue bounty: {} (level {}) roams round {}", name.getString(), b.rogueLevel, b.hideout.spot);
		return e;
	}

	// ---- The glow --------------------------------------------------------------------------------------------------

	/** Every {@link #GLOW_EVERY} ticks: each rogue glows for the players who took its bounty within {@link #GLOW} blocks, for them only. */
	public static void glow(ServerLevel level) {
		Bounties.Data data = Bounties.Data.get(level);
		for (Bounties.Bounty b : data.bounties.values()) {
			if (b.rogue == null) {
				continue;
			}
			Entity rogue = rogue(level, b);
			Set<UUID> before = glowing.getOrDefault(b.id, Set.of());
			Set<UUID> now = new LinkedHashSet<>();
			if (rogue != null) {
				for (UUID u : b.takers) {
					if (level.getPlayerByUUID(u) instanceof ServerPlayer p && !p.isSpectator() && p.distanceToSqr(rogue) <= (double) GLOW * GLOW) {
						now.add(u);
						send(p, rogue, true);
					}
				}
				for (UUID u : before) {
					if (!now.contains(u) && level.getPlayerByUUID(u) instanceof ServerPlayer p) {
						send(p, rogue, false);
					}
				}
			}
			if (now.isEmpty()) {
				glowing.remove(b.id);
			} else {
				glowing.put(b.id, now);
			}
		}
	}

	/** A rogue bounty came down: nobody sees it glow any more. */
	static void forget(UUID bounty) {
		glowing.remove(bounty);
	}

	/** The players the rogue of {@code b} glows for now. */
	public static Set<UUID> glowingFor(Bounties.Bounty b) {
		return Set.copyOf(glowing.getOrDefault(b.id, Set.of()));
	}

	/** The rogue's flags as everyone else sees them, with the glow bit on (or as they are) for {@code player} only. */
	private static void send(ServerPlayer player, Entity e, boolean glow) {
		byte flags = 0;
		if (e.isOnFire()) {
			flags |= 1;
		}
		if (e.isShiftKeyDown()) {
			flags |= 2;
		}
		if (e.isSprinting()) {
			flags |= 8;
		}
		if (e.isSwimming()) {
			flags |= 16;
		}
		if (e.isInvisible()) {
			flags |= 32;
		}
		if (glow || e.isCurrentlyGlowing()) {
			flags |= 64;
		}
		player.connection.send(new ClientboundSetEntityDataPacket(e.getId(), List.of(new SynchedEntityData.DataValue<>(0, EntityDataSerializers.BYTE, flags))));
	}

	// ---- Beaten or caught ------------------------------------------------------------------------------------------

	/**
	 * The rogue of bounty {@code id} was beaten in battle by {@code player}, or caught by them ({@code caught}): the
	 * bounty pays them once (a catcher keeps the Pokémon). False if there's no such open rogue bounty (paid already).
	 */
	public static boolean beaten(MinecraftServer server, UUID id, ServerPlayer player, boolean caught) {
		for (ServerLevel level : server.getAllLevels()) {
			Bounties.Data data = Bounties.Data.get(level);
			Bounties.Bounty b = data.bounties.get(id);
			if (b == null || b.rogue == null) {
				continue;
			}
			Component name = b.name(level);
			b.fallen = true;
			b.killer = player.getUUID();
			data.bounties.remove(id);
			data.claimed.add(id);
			data.setDirty();
			glowing.remove(id);
			Bounties.pay(player, b.reward);
			Reputation.add(level, b.hall, player, Reputation.BOUNTY);
			Chat.chat(player, Component.translatable(caught ? "message.aliveworkplace.bounty.rogue_caught" : "message.aliveworkplace.bounty.rogue_beaten",
				name, b.reward).withStyle(ChatFormatting.GREEN));
			Chronicle.record(level, b.hall, Chronicle.Kind.BOUNTY, Component.translatable(caught ? "chronicle.aliveworkplace.bounty.rogue_caught"
				: "chronicle.aliveworkplace.bounty.rogue_beaten", player.getDisplayName(), name, b.reward));
			player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8f, 1.2f);
			Bounties.refreshBoards(level);
			return true;
		}
		return false;
	}

	/** Forgets who sees which rogue glow (tests). */
	public static void forget() {
		glowing.clear();
	}

	private Rogues() {
	}
}

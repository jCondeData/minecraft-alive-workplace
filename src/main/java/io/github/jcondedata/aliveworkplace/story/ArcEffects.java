package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * What a story arc's chapters do (ROADMAP 31.4): {@code announce}, {@code chronicle} (a STORY line), {@code place} (a
 * template at a spot found by a rule, built once a player comes near), {@code spawn} (named mobs with gear, extra health
 * and a boss bar, kept home), {@code give_map}, {@code festival}, {@code flag}, {@code start_quest}, {@code end_arc},
 * {@code chatter}, and every reward of the quest toolbox ({@link Rewards}: {@code village_mood}, {@code money},
 * {@code item}, {@code treasury}...). Waiting placements and spawned mobs are checked every {@link Arcs#CHECK_EVERY}
 * ticks with one distance check per player ({@link #check}).
 */
public final class ArcEffects {
	/** Builds an arc has placed since the server started (tests). */
	public static final AtomicInteger PLACED = new AtomicInteger();
	/** How far round a mob its boss bar shows. */
	static final int BAR_RANGE = 48;
	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
	private static final Set<String> RULES = Set.of("ring", "road", "biome", "after", "offset");

	/** Checks an effect when its arc is read; throws {@link IllegalArgumentException} naming the bad field. */
	static void validate(JsonObject json) {
		String type = GsonHelper.getAsString(json, "type", "");
		switch (type) {
			case "announce" -> Rewards.text(json.get("text"));
			case "chronicle" -> Rewards.text(json.get("line"));
			case "place" -> {
				key(json);
				if (!json.has("template") && !json.has("stages")) {
					throw new IllegalArgumentException("place: needs 'template' or 'stages'");
				}
				for (String t : templates(json)) {
					if (ResourceLocation.tryParse(t) == null) {
						throw new IllegalArgumentException("place: bad template '" + t + "'");
					}
				}
				JsonObject rule = GsonHelper.getAsJsonObject(json, "rule");
				if (rule.keySet().stream().noneMatch(RULES::contains)) {
					throw new IllegalArgumentException("place: 'rule' needs one of " + RULES);
				}
				if (rule.has("after") && !rule.has("distance")) {
					throw new IllegalArgumentException("place: 'after' needs 'distance'");
				}
			}
			case "spawn" -> {
				if (json.has("group")) {
					ResourceLocation id = Rewards.id(json, "group");
					if (Arcs.group(id) == null) {
						throw new IllegalArgumentException("spawn: no spawn group '" + id + "'");
					}
				} else {
					validateSpawn(json);
				}
			}
			case "give_map" -> GsonHelper.getAsString(json, "at");
			case "festival", "chatter" -> {
			}
			case "flag" -> GsonHelper.getAsString(json, "flag");
			case "start_quest" -> {
				if (!json.has("quest")) {
					throw new IllegalArgumentException("start_quest: missing 'quest'");
				}
				if (json.get("quest").isJsonObject()) {
					JsonObject q = json.getAsJsonObject("quest").deepCopy();
					if (!q.has("giver")) {
						q.addProperty("giver", "arc");
					}
					QuestFiles.read(AliveWorkplace.id("arc/inline"), q);
				}
			}
			case "end_arc" -> {
				String ending = GsonHelper.getAsString(json, "ending", "failed");
				if (!Set.of("done", "failed", "quiet").contains(ending)) {
					throw new IllegalArgumentException("end_arc: 'ending' must be done, failed or quiet");
				}
			}
			default -> {
				try {
					Rewards.parse(json); // every quest reward
				} catch (IllegalArgumentException e) {
					throw new IllegalArgumentException(e.getMessage().startsWith("unknown reward type") ? "unknown effect type '" + type + "'" : e.getMessage(), e);
				}
			}
		}
	}

	/** Checks one mob written out for {@code spawn}. */
	static void validateSpawn(JsonObject json) {
		String entity = GsonHelper.getAsString(json, "entity", "");
		if (EntityType.byString(entity).isEmpty()) {
			throw new IllegalArgumentException("spawn: unknown entity '" + entity + "'");
		}
		if (json.has("gear")) {
			for (Map.Entry<String, JsonElement> g : json.getAsJsonObject("gear").entrySet()) {
				EquipmentSlot.byName(g.getKey());
				ResourceLocation item = ResourceLocation.tryParse(g.getValue().getAsString());
				if (item == null || !BuiltInRegistries.ITEM.containsKey(item)) {
					throw new IllegalArgumentException("spawn: unknown item '" + g.getValue().getAsString() + "'");
				}
			}
		}
		if (json.has("name")) {
			Rewards.text(json.get("name"));
		}
		if (GsonHelper.getAsDouble(json, "health", 0) < 0) {
			throw new IllegalArgumentException("spawn: 'health' below 0");
		}
	}

	private static String key(JsonObject json) {
		String key = GsonHelper.getAsString(json, "key", "");
		if (key.isEmpty() || key.contains("#")) {
			throw new IllegalArgumentException("place: missing or bad 'key'");
		}
		return key;
	}

	private static List<String> templates(JsonObject json) {
		List<String> out = new ArrayList<>();
		if (json.has("stages")) {
			json.getAsJsonArray("stages").forEach(e -> out.add(e.getAsString()));
		} else {
			out.add(GsonHelper.getAsString(json, "template"));
		}
		return out;
	}

	/** The players in and near the village round {@code hall}: those who hear its news. */
	public static List<ServerPlayer> audience(ServerLevel level, BlockPos hall) {
		double r = VillageHalls.RADIUS + 64;
		return level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r);
	}

	/** Runs {@code effects} for the arc {@code s} in the village round {@code hall}; an {@code end_arc} ends it afterwards. */
	static void run(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arcs.Arc arc, List<JsonObject> effects) {
		for (JsonObject json : effects) {
			try {
				run(level, hall, e, s, arc, json);
			} catch (RuntimeException ex) {
				AliveWorkplace.LOG.warn("Story arc {}: effect {} failed: {}", s.id, GsonHelper.getAsString(json, "type", "?"), ex.toString());
			}
		}
		Stories.Data.get(level).setDirty(); // (an end_arc among them is acted on by the caller, once they've all run)
	}

	private static void run(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arcs.Arc arc, JsonObject json) {
		long today = Chronicle.day(level);
		switch (GsonHelper.getAsString(json, "type")) {
			case "announce" -> {
				Component text = Rewards.text(json.get("text"));
				for (ServerPlayer player : audience(level, hall)) {
					Chat.chat(player, text.copy().withStyle(ChatFormatting.YELLOW));
				}
			}
			case "chronicle" -> Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Rewards.text(json.get("line")));
			case "place" -> place(level, hall, s, json);
			case "spawn" -> spawnEffect(level, hall, s, json);
			case "give_map" -> {
				ArcState.ArcPlace p = s.place(GsonHelper.getAsString(json, "at"));
				if (p != null) {
					for (ServerPlayer player : audience(level, hall)) {
						ItemStack map = Places.map(level, Places.point(p.spot));
						if (!player.getInventory().add(map)) {
							player.drop(map, false);
						}
					}
				}
			}
			case "festival" -> {
				if (Festivals.ENABLED && level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
					Festivals.schedule(level, hall, entity, today + 1);
				}
			}
			case "flag" -> {
				int days = GsonHelper.getAsInt(json, "days", 0);
				String flag = GsonHelper.getAsString(json, "flag");
				s.flags.put(flag, days > 0 ? today + days - 1 : ArcState.FOREVER);
				if (days > 0) {
					e.villageFlags.put(flag, today + days - 1); // outlives the arc (an ending's "no camps for 10 days")
				}
			}
			case "start_quest" -> {
				JsonElement q = json.get("quest");
				Arcs.QuestRef ref;
				if (q.isJsonObject()) {
					JsonObject o = q.getAsJsonObject().deepCopy();
					if (!o.has("giver")) {
						o.addProperty("giver", "arc");
					}
					ResourceLocation id = ResourceLocation.fromNamespaceAndPath(arc.id().getNamespace(), "arc/" + arc.id().getPath() + "/extra");
					ref = new Arcs.QuestRef(id, QuestFiles.read(id, o));
				} else {
					ref = new Arcs.QuestRef(ResourceLocation.parse(q.getAsString()), null);
				}
				Arcs.quest(level, hall, s, ref);
			}
			case "end_arc" -> s.endWith = GsonHelper.getAsString(json, "ending", "failed");
			case "chatter" -> {
				if (json.has("lines")) {
					json.getAsJsonArray("lines").forEach(l -> s.chatter.add(l.getAsString()));
				}
			}
			default -> {
				ServerPlayer finisher = s.last == null ? null : level.getServer().getPlayerList().getPlayer(s.last);
				Rewards.parse(json).resolve(List.of(), 1f).give(level, hall, finisher);
			}
		}
	}

	// ---- place -----------------------------------------------------------------------------------------------------

	/** {@code place}: the spot (or {@code count} spots) chosen now by the rule; each is built once a player comes near. */
	static void place(ServerLevel level, BlockPos hall, ArcState s, JsonObject json) {
		String key = key(json);
		int count = Math.max(1, GsonHelper.getAsInt(json, "count", 1));
		JsonObject rule = GsonHelper.getAsJsonObject(json, "rule");
		for (int i = 0; i < count; i++) {
			String k = i == 0 ? key : key + "#" + (i + 1);
			BlockPos spot = spot(level, hall, s, rule);
			if (spot == null) {
				AliveWorkplace.LOG.info("Story arc {}: no spot found for '{}' ({}); it isn't built", s.id, k, rule);
				ArcState.ArcPlace p = new ArcState.ArcPlace(k, hall, templates(json), 0);
				p.skipped = true;
				s.places.add(p);
				continue;
			}
			s.places.add(new ArcState.ArcPlace(k, spot, templates(json), level.random.nextInt(4)));
		}
		doneFlags(level, s);
	}

	/** The column a rule picks: a ring round the hall, a share along a caravan road, a biome, past another place, an offset. */
	@Nullable
	static BlockPos spot(ServerLevel level, BlockPos hall, ArcState s, JsonObject rule) {
		var random = level.random;
		if (rule.has("ring")) {
			JsonArray ring = rule.getAsJsonArray("ring");
			int near = ring.get(0).getAsInt();
			int far = Math.max(near, ring.get(1).getAsInt());
			double angle = random.nextDouble() * Math.PI * 2;
			int distance = near + random.nextInt(far - near + 1);
			return hall.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
		}
		if (rule.has("offset")) {
			JsonArray o = rule.getAsJsonArray("offset");
			return hall.offset(o.get(0).getAsInt(), 0, o.get(o.size() - 1).getAsInt());
		}
		if (rule.has("road")) {
			float share = rule.get("road").getAsFloat();
			Set<BlockPos> routes = Caravans.Data.get(level).routesFrom(hall);
			BlockPos to = routes.stream().min(java.util.Comparator.comparingDouble(p -> p.distSqr(hall)))
				.orElseGet(() -> Caravans.neighbours(level, hall).stream().map(Caravans.Village::hall).findFirst().orElse(null));
			if (to == null) {
				return null;
			}
			return new BlockPos((int) Math.round(hall.getX() + (to.getX() - hall.getX()) * share), hall.getY(),
				(int) Math.round(hall.getZ() + (to.getZ() - hall.getZ()) * share));
		}
		if (rule.has("biome")) {
			JsonObject b = new JsonObject();
			b.add("biome", rule.get("biome"));
			Places.Place p = Places.locate(level, hall, Places.read(b));
			return p == null ? null : p.found().orElse(null);
		}
		if (rule.has("after")) {
			ArcState.ArcPlace other = s.place(rule.get("after").getAsString());
			if (other == null || other.skipped) {
				return null;
			}
			JsonArray d = rule.getAsJsonArray("distance");
			int near = d.get(0).getAsInt();
			int far = Math.max(near, d.get(d.size() - 1).getAsInt());
			double angle = random.nextDouble() * Math.PI * 2;
			int distance = near + random.nextInt(far - near + 1);
			return other.spot.offset((int) Math.round(Math.cos(angle) * distance), 0, (int) Math.round(Math.sin(angle) * distance));
		}
		return null;
	}

	/** Builds the next stage of {@code p} (its first: on clear natural ground near its spot); false if it must wait. */
	static boolean build(ServerLevel level, ArcState s, ArcState.ArcPlace p) {
		ResourceLocation id = ResourceLocation.tryParse(p.stages.get(p.placed));
		StructureTemplate template = id == null ? null : level.getStructureManager().get(id).orElse(null);
		if (template == null) {
			AliveWorkplace.LOG.warn("Story arc {}: no template {} for '{}'; it isn't built", s.id, p.stages.get(p.placed), p.key);
			p.skipped = true;
			doneFlags(level, s);
			return true;
		}
		Rotation rotation = Rotation.values()[Math.floorMod(p.rotation, 4)];
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
		BlockPos origin = p.origin;
		if (origin == null) {
			Vec3i size = template.getSize(rotation);
			BoundingBox box = null;
			for (BlockPos column : tries(p.spot)) {
				if (!level.isLoaded(column)) {
					continue;
				}
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
				BlockPos o = template.getZeroPositionWithTransform(ground.offset(-size.getX() / 2, 0, -size.getZ() / 2), Mirror.NONE, rotation);
				BoundingBox b = template.getBoundingBox(settings, o);
				if (!level.isLoaded(new BlockPos(b.minX(), b.minY(), b.minZ())) || !level.isLoaded(new BlockPos(b.maxX(), b.minY(), b.maxZ()))) {
					return false; // wait for the rest to load
				}
				if (natural(level, b)) {
					origin = o;
					box = b;
					break;
				}
			}
			if (origin == null) {
				AliveWorkplace.LOG.info("Story arc {}: no clear natural ground near {} for '{}'; it isn't built", s.id, p.spot, p.key);
				p.skipped = true;
				doneFlags(level, s);
				return true;
			}
			for (BlockPos q : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (!level.getBlockState(q).isAir()) {
					level.removeBlock(q, false); // the grass, a bush, a sapling
				}
			}
			for (int x = box.minX(); x <= box.maxX(); x++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					for (int y = box.minY(); y > box.minY() - 4; y--) { // no floating ground
						BlockPos q = new BlockPos(x, y, z);
						if (!level.getBlockState(q).canBeReplaced()) {
							break;
						}
						level.setBlock(q, Blocks.DIRT.defaultBlockState(), 2);
					}
				}
			}
			p.origin = origin;
			p.spot = new BlockPos((box.minX() + box.maxX()) / 2, box.minY() + 1, (box.minZ() + box.maxZ()) / 2);
		}
		template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
		p.placed++;
		p.nextDay = Chronicle.day(level) + 1;
		PLACED.incrementAndGet();
		AliveWorkplace.LOG.info("Story arc {} built {} ({} of {}) at {}", s.id, p.stages.get(p.placed - 1), p.placed, p.stages.size(), p.spot);
		doneFlags(level, s);
		return true;
	}

	/** The spot, then columns round it out to 32 blocks. */
	private static List<BlockPos> tries(BlockPos spot) {
		List<BlockPos> out = new ArrayList<>();
		out.add(spot);
		for (int r = 8; r <= 32; r += 8) {
			for (int i = 0; i < 8; i++) {
				double a = Math.PI / 4 * i;
				out.add(spot.offset((int) Math.round(Math.cos(a) * r), 0, (int) Math.round(Math.sin(a) * r)));
			}
		}
		return out;
	}

	/** Only natural ground (and plants) under the footprint, and only air or plants above it. */
	static boolean natural(ServerLevel level, BoundingBox box) {
		for (BlockPos q : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(q);
			if (!state.getFluidState().isEmpty()) {
				return false;
			}
			boolean plant = state.isAir() || state.canBeReplaced() || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS)
				|| state.is(BlockTags.LEAVES) || state.is(BlockTags.REPLACEABLE_BY_TREES);
			boolean ground = state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD)
				|| state.is(BlockTags.TERRACOTTA) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY) || state.is(Blocks.SNOW_BLOCK);
			if (!(plant || q.getY() == box.minY() && ground)) {
				return false;
			}
		}
		return true;
	}

	/** Sets {@code <key>_done} for each key whose places are all built (or given up). */
	private static void doneFlags(ServerLevel level, ArcState s) {
		Map<String, Boolean> done = new java.util.LinkedHashMap<>();
		for (ArcState.ArcPlace p : s.places) {
			String base = p.key.contains("#") ? p.key.substring(0, p.key.indexOf('#')) : p.key;
			done.merge(base, !p.waiting(), Boolean::logicalAnd);
		}
		done.forEach((k, v) -> {
			if (v) {
				s.flags.put(k + "_done", ArcState.FOREVER);
			}
		});
		Stories.Data.get(level).setDirty();
	}

	/** The places with key {@code at} ({@code camp} also names {@code camp#2}...). */
	static List<ArcState.ArcPlace> places(ArcState s, String at) {
		return s.places.stream().filter(p -> p.key.equals(at) || p.key.startsWith(at + "#")).toList();
	}

	// ---- spawn -----------------------------------------------------------------------------------------------------

	/** {@code spawn}: the mobs are noted now, and each comes once a player is near its spot (and back if it's lost). */
	static void spawnEffect(ServerLevel level, BlockPos hall, ArcState s, JsonObject json) {
		List<JsonObject> specs = new ArrayList<>();
		if (json.has("group")) {
			List<JsonObject> group = Arcs.group(Rewards.id(json, "group"));
			for (JsonObject g : group == null ? List.<JsonObject>of() : group) {
				JsonObject spec = g.deepCopy();
				if (!spec.has("at") && json.has("at")) {
					spec.add("at", json.get("at"));
				}
				specs.add(spec);
			}
		} else {
			specs.add(json);
		}
		for (JsonObject spec : specs) {
			String entity = GsonHelper.getAsString(spec, "entity");
			String key = GsonHelper.getAsString(spec, "key", ResourceLocation.parse(entity).getPath());
			String role = GsonHelper.getAsString(spec, "role", key);
			Component name = spec.has("name") ? Rewards.text(spec.get("name")) : Lookup.value(BuiltInRegistries.ENTITY_TYPE, ResourceLocation.parse(entity)).getDescription();
			s.roles.putIfAbsent(role, new ArcState.Role(Optional.empty(), name));
			int count = Math.max(1, GsonHelper.getAsInt(spec, "count", 1));
			String at = GsonHelper.getAsString(spec, "at", "");
			List<String> where = new ArrayList<>();
			if (at.isEmpty()) {
				where.add("");
			} else {
				places(s, at).forEach(p -> where.add(p.key));
			}
			for (String w : where) {
				for (int i = 0; i < count; i++) {
					BlockPos spot = w.isEmpty() ? offset(hall, spec, level) : null;
					s.mobs.add(new ArcState.ArcMob(key, w, spec, spot));
				}
			}
		}
	}

	private static BlockPos offset(BlockPos base, JsonObject spec, ServerLevel level) {
		if (spec.has("offset")) {
			JsonArray o = spec.getAsJsonArray("offset");
			return base.offset(o.get(0).getAsInt(), 0, o.get(o.size() - 1).getAsInt());
		}
		return base.offset(level.random.nextInt(7) - 3, 0, level.random.nextInt(7) - 3);
	}

	static String arcTag(String arc) {
		ResourceLocation id = ResourceLocation.tryParse(arc);
		return Arcs.TAG + "_" + (id == null ? arc : id.getPath().replace('/', '.'));
	}

	public static String roleTag(String role) {
		return "aliveworkplace_role_" + role;
	}

	/** Spawns {@code m} at its spot, named, geared, with its extra health, tagged to the arc and kept home. */
	@Nullable
	static Mob spawn(ServerLevel level, ArcState s, ArcState.ArcMob m) {
		JsonObject spec = m.spec;
		Entity created = EntityType.byString(GsonHelper.getAsString(spec, "entity")).map(t -> t.create(level)).orElse(null);
		if (!(created instanceof Mob mob)) {
			if (created != null) {
				created.discard();
			}
			AliveWorkplace.LOG.warn("Story arc {}: {} isn't a mob; it isn't spawned", s.id, GsonHelper.getAsString(spec, "entity"));
			m.dead = true;
			return null;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, m.spot);
		mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		if (mob.isBaby() && !GsonHelper.getAsBoolean(spec, "baby", false)) {
			mob.setBaby(false);
		}
		mob.setPersistenceRequired();
		String role = GsonHelper.getAsString(spec, "role", m.key);
		for (String tag : List.of(Arcs.TAG, Arcs.MOB_TAG, arcTag(s.id), roleTag(role))) {
			mob.addTag(tag);
		}
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
		}
		ArcState.Role r = s.roles.get(role);
		Component name = spec.has("name") ? Rewards.text(spec.get("name")) : r != null ? r.name() : null;
		if (name != null) {
			mob.setCustomName(name);
			mob.setCustomNameVisible(true);
		}
		if (spec.has("gear")) {
			for (Map.Entry<String, JsonElement> g : spec.getAsJsonObject("gear").entrySet()) {
				EquipmentSlot slot = EquipmentSlot.byName(g.getKey());
				Item item = Lookup.value(BuiltInRegistries.ITEM, ResourceLocation.parse(g.getValue().getAsString()));
				mob.setItemSlot(slot, new ItemStack(item));
				mob.setDropChance(slot, 0f);
			}
		}
		double health = GsonHelper.getAsDouble(spec, "health", 0);
		var attribute = mob.getAttribute(Attributes.MAX_HEALTH);
		if (health > 0 && attribute != null) {
			attribute.addPermanentModifier(new AttributeModifier(AliveWorkplace.id("story_health"), health, AttributeModifier.Operation.ADD_VALUE));
			mob.setHealth(mob.getMaxHealth());
		}
		mob.restrictTo(at, GsonHelper.getAsInt(spec, "restrict", 12));
		m.uuid = mob.getUUID(); // (before it joins: onLoad must know it)
		m.spot = at;
		s.roles.put(role, new ArcState.Role(Optional.of(m.uuid), name != null ? name : mob.getDisplayName()));
		level.addFreshEntityWithPassengers(mob);
		Stories.Data.get(level).setDirty();
		return mob;
	}

	// ---- the 40-tick check -----------------------------------------------------------------------------------------

	/** Whether a player (not a spectator) is within {@link Arcs#NEAR} blocks of {@code pos}, across the ground. */
	static boolean near(ServerLevel level, BlockPos pos) {
		double r = (double) Arcs.NEAR * Arcs.NEAR;
		for (ServerPlayer player : level.players()) {
			double dx = player.getX() - (pos.getX() + 0.5);
			double dz = player.getZ() - (pos.getZ() + 0.5);
			if (!player.isSpectator() && dx * dx + dz * dz <= r) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Every {@link Arcs#CHECK_EVERY} ticks: a waiting place is built once a player is near (and the next stage at the next
	 * dawn), a mob whose spot is ready comes once a player is near and its chunk is loaded, a lost one is put back, the
	 * rest are kept home and their boss bars follow them.
	 */
	public static void check(ServerLevel level) {
		Stories.Data data = Stories.Data.get(level);
		if (data.halls.isEmpty()) {
			return;
		}
		long today = Chronicle.day(level);
		for (Stories.Entry e : List.copyOf(data.halls.values())) {
			for (ArcState s : Arcs.running(e)) {
				for (ArcState.ArcPlace p : s.places) {
					if (p.waiting() && (p.placed == 0 || today >= p.nextDay) && near(level, p.spot) && level.isLoaded(p.spot)) {
						build(level, s, p);
						data.setDirty();
					}
				}
				for (ArcState.ArcMob m : s.mobs) {
					if (!m.dead) {
						checkMob(level, s, m, data);
					}
				}
			}
		}
	}

	private static void checkMob(ServerLevel level, ArcState s, ArcState.ArcMob m, Stories.Data data) {
		if (m.spot == null) {
			ArcState.ArcPlace p = s.place(m.at);
			if (p == null || p.skipped || p.placed == 0) {
				return; // its place isn't built yet
			}
			m.spot = offset(p.spot, m.spec, level);
			data.setDirty();
		}
		Entity found = m.uuid == null ? null : level.getEntity(m.uuid);
		if (found instanceof LivingEntity living && living.isAlive()) {
			if (found instanceof Mob mob && !mob.hasRestriction()) {
				mob.restrictTo(m.spot, GsonHelper.getAsInt(m.spec, "restrict", 12)); // a chunk reload forgets it
			}
			bar(level, m, living);
			return;
		}
		if (!near(level, m.spot) || !level.isPositionEntityTicking(m.spot)) {
			dropBar(m.uuid);
			return;
		}
		// Gone (unloaded and lost, or never spawned): one of its kind still about is it; else it's put back at its spot.
		String role = GsonHelper.getAsString(m.spec, "role", m.key);
		int keep = GsonHelper.getAsInt(m.spec, "restrict", 12) + 16;
		Set<UUID> claimed = new java.util.HashSet<>();
		s.mobs.forEach(o -> {
			if (o.uuid != null && o != m) {
				claimed.add(o.uuid);
			}
		});
		List<Mob> stray = level.getEntitiesOfClass(Mob.class, new AABB(m.spot).inflate(keep, 16, keep),
			o -> o.isAlive() && o.getTags().contains(arcTag(s.id)) && o.getTags().contains(roleTag(role)) && !claimed.contains(o.getUUID()));
		if (!stray.isEmpty()) {
			dropBar(m.uuid);
			m.uuid = stray.get(0).getUUID();
			data.setDirty();
			return;
		}
		dropBar(m.uuid);
		spawn(level, s, m);
	}

	private static void bar(ServerLevel level, ArcState.ArcMob m, LivingEntity mob) {
		if (!GsonHelper.getAsBoolean(m.spec, "boss_bar", false)) {
			return;
		}
		ServerBossEvent bar = BARS.computeIfAbsent(mob.getUUID(), k -> new ServerBossEvent(mob.getDisplayName(), BossEvent.BossBarColor.RED,
			BossEvent.BossBarOverlay.PROGRESS));
		bar.setName(mob.getDisplayName());
		bar.setProgress(Math.max(0f, Math.min(1f, mob.getHealth() / mob.getMaxHealth())));
		for (ServerPlayer player : level.players()) {
			boolean in = player.distanceToSqr(mob) <= (double) BAR_RANGE * BAR_RANGE;
			if (in && !bar.getPlayers().contains(player)) {
				bar.addPlayer(player);
			} else if (!in && bar.getPlayers().contains(player)) {
				bar.removePlayer(player);
			}
		}
	}

	/** The boss bar of a mob, or null with none (tests). */
	@Nullable
	public static ServerBossEvent bar(UUID mob) {
		return BARS.get(mob);
	}

	static void dropBar(@Nullable UUID mob) {
		ServerBossEvent bar = mob == null ? null : BARS.remove(mob);
		if (bar != null) {
			bar.removeAllPlayers();
		}
	}

	/** The arc ended: its mobs leave in a puff (those loaded; others go when they load), its people are let go. */
	static void dismiss(ServerLevel level, ArcState s) {
		for (ArcState.ArcMob m : s.mobs) {
			dropBar(m.uuid);
			Entity mob = m.uuid == null ? null : level.getEntity(m.uuid);
			if (mob != null && mob.isAlive()) {
				level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
				mob.discard();
			}
		}
		for (Map.Entry<String, ArcState.Role> r : s.roles.entrySet()) {
			Entity who = r.getValue().who().map(level::getEntity).orElse(null);
			if (who instanceof Villager v) {
				v.removeTag(roleTag(r.getKey()));
				v.removeTag(arcTag(s.id));
			}
		}
	}

	/** An arc mob loads that no running arc knows (its arc ended while it was away): it's gone. */
	static void onLoad(ServerLevel level, Entity entity) {
		if (!entity.getTags().contains(Arcs.MOB_TAG)) {
			return;
		}
		for (Stories.Entry e : Stories.Data.get(level).halls.values()) {
			for (ArcState s : Arcs.running(e)) {
				for (ArcState.ArcMob m : s.mobs) {
					if (entity.getUUID().equals(m.uuid) || !m.dead && entity.getTags().contains(arcTag(s.id))) {
						return; // its own, or a lost one the next check takes back
					}
				}
			}
		}
		entity.discard();
	}

	// ---- roles and talk --------------------------------------------------------------------------------------------

	/** Who plays the villager role {@code key}: a newcomer, any villager of the village, or one of a job. Null if nobody. */
	@Nullable
	static Villager cast(ServerLevel level, BlockPos hall, Arcs.Arc arc, String key, Arcs.RoleSpec spec) {
		Villager v;
		if (spec.villager().equals("new")) {
			v = EntityType.VILLAGER.create(level);
			if (v == null) {
				return null;
			}
			BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(level.random.nextInt(5) - 2, 0, 2 + level.random.nextInt(3)));
			v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
			v.setCustomName(spec.name());
			v.setPersistenceRequired();
			v.addTag(Arcs.TAG);
			v.addTag(arcTag(arc.id().toString()));
			v.addTag(roleTag(key));
			level.addFreshEntity(v);
			return v;
		}
		List<Villager> all = level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), x -> x.isAlive() && !x.isBaby()
			&& x.getTags().stream().noneMatch(t -> t.startsWith("aliveworkplace_role_")));
		if (spec.villager().startsWith("job:")) {
			ResourceLocation job = ResourceLocation.tryParse(spec.villager().substring(4));
			all = all.stream().filter(x -> BuiltInRegistries.VILLAGER_PROFESSION.getKey(x.getVillagerData().getProfession()).equals(job)).toList();
		}
		if (all.isEmpty()) {
			return null;
		}
		v = all.get(level.random.nextInt(all.size()));
		v.addTag(Arcs.TAG);
		v.addTag(arcTag(arc.id().toString()));
		v.addTag(roleTag(key));
		return v;
	}

	/** A right-click on a villager an arc's {@code talk} names: it counts, and they say their line. */
	public static InteractionResult onTalk(Player player, Level world, InteractionHand hand, Entity entity) {
		if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND
			|| !entity.getTags().contains(Arcs.TAG)) {
			return InteractionResult.PASS;
		}
		Stories.Data data = Stories.Data.get(level);
		for (Map.Entry<BlockPos, Stories.Entry> en : List.copyOf(data.halls.entrySet())) {
			for (Quest q : List.copyOf(en.getValue().quests)) {
				int i = q.current();
				if (q.arc != null && i >= 0 && q.objectives.get(i) instanceof Objectives.Talk talk && entity.getTags().contains(roleTag(talk.role()))
					&& level.getBlockEntity(en.getKey()) instanceof VillageHallBlockEntity hall) {
					Component name = entity.getDisplayName();
					Chat.chat(sp, talk.says().map(key -> Component.translatable("message.aliveworkplace.arc.says", name, Component.translatable(key)))
						.orElseGet(() -> Component.translatable("message.aliveworkplace.arc.talked", name)).withStyle(ChatFormatting.WHITE));
					if (entity instanceof Villager v) {
						v.playSound(net.minecraft.sounds.SoundEvents.VILLAGER_YES, 0.8f, 1f);
					}
					Stories.progress(level, en.getKey(), hall, q, i, 1, sp);
					return InteractionResult.SUCCESS;
				}
			}
		}
		return InteractionResult.PASS;
	}

	private ArcEffects() {
	}
}

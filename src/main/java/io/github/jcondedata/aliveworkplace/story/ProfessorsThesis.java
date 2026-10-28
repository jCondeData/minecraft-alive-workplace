package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.PokemonProfessor;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchTree;
import io.github.jcondedata.aliveworkplace.research.ResearchTrees;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import io.github.jcondedata.aliveworkplace.work.Extension;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The Professor's Thesis (ROADMAP 31.21), a Cobblemon arc ({@code arcs/professors_thesis.json}, loaded only with
 * Cobblemon). Its patron is the village's Pokémon Professor (29.21), or else its best Scholar at Expert or above, called
 * "Professor <name>". The thesis topic is one of {@link #TOPICS}; chapter 1 posts its three {@code catch} quests, chapter
 * 2 a {@code show}, chapter 3 a {@code battle_leader} against the Trainer Leader of a village within caravan range, or
 * the rival scholar Thessaly, who comes to the hall for it. The ending publishes the thesis as a written book listing
 * the catches, with the helpers as co-authors, pays Rare Candies and an Ability Capsule, and gives the village a free
 * research level.
 *
 * <p>Cobblemon's events (captures, a Fossil Scientist's revivals) come in through {@link #onCatch} from
 * {@code compat/cobblemon}; the player's party through {@link Party#EXTENSION}. Without Cobblemon the arc file doesn't
 * load and nothing here runs. Every catch is kept in the arc's {@link ArcState#extra}, so a restart keeps them.
 */
public final class ProfessorsThesis {
	public static final ResourceLocation ARC = AliveWorkplace.id("professors_thesis");
	public static final String PROFESSOR = "professor";
	public static final String THESSALY_TAG = "aliveworkplace_thessaly";
	/** A Pokémon at this friendship or more may be shown in chapter 2. */
	public static final int FRIENDSHIP = 200;
	public static final int RARE_CANDIES = 3;
	/** Emeralds paid in place of each reward item Cobblemon doesn't have. */
	public static final int EMERALDS_PER_MISSING = 4;
	public static final ResourceLocation RARE_CANDY = ResourceLocation.fromNamespaceAndPath("cobblemon", "rare_candy");
	public static final ResourceLocation ABILITY_CAPSULE = ResourceLocation.fromNamespaceAndPath("cobblemon", "ability_capsule");
	/** {@link ArcState#extra} keys. */
	static final String TOPIC = "topic";
	static final String CATCHES = "catches";
	static final String LEADER_HALL = "leader_hall";
	static final String THESSALY = "thessaly";
	/** The topic every thesis picks (tests and the showcase); null: one of the six at random. */
	@Nullable
	public static String FORCE_TOPIC;

	/** Cobblemon's side: the player's party. Filled in {@code compat/cobblemon}. */
	public interface Party {
		Extension<Party> EXTENSION = new Extension<>("the Professor's Thesis");

		List<Mon> party(ServerPlayer player);
	}

	/** One Pokémon of a party: its id (kept through evolution), species and friendship. */
	public record Mon(UUID uuid, ResourceLocation species, int friendship) {
	}

	/** One catch, as Cobblemon reports it: the Pokémon, its types (lower case), where and when, and whether it was a revived fossil. */
	public record CatchInfo(UUID uuid, ResourceLocation species, Component name, List<String> types, BlockPos pos, Holder<Biome> biome,
							long dayTime, int moonPhase, long day, boolean fossil) {
		public static CatchInfo of(ServerLevel level, BlockPos pos, UUID uuid, ResourceLocation species, Component name, List<String> types, boolean fossil) {
			return new CatchInfo(uuid, species, name, List.copyOf(types), pos.immutable(), level.getBiome(pos), level.getDayTime(), level.getMoonPhase(),
				Chronicle.day(level), fossil);
		}

		public boolean night() {
			long t = Math.floorMod(dayTime, 24000L);
			return t >= 13000 && t < 23000;
		}
	}

	/** One thesis topic: its lang key and the three catches it asks for. */
	public record Topic(String id, List<JsonObject> catches) {
		public Component name() {
			return Component.translatable("arc.aliveworkplace.professors_thesis.topic." + id);
		}
	}

	public static final List<Topic> TOPICS = List.of(
		new Topic("cold", List.of(
			catchJson("cold.1", List.of("ice"), List.of("minecraft:snowy_plains", "minecraft:snowy_taiga", "minecraft:snowy_slopes", "minecraft:snowy_beach",
				"minecraft:ice_spikes", "minecraft:frozen_peaks", "minecraft:jagged_peaks", "minecraft:grove", "minecraft:frozen_river", "minecraft:frozen_ocean",
				"minecraft:deep_frozen_ocean"), "", null, null, false),
			catchJson("cold.2", List.of("water"), List.of("minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:frozen_river"), "", null, null, false),
			catchJson("cold.3", List.of(), List.of(), "", 150, null, false))),
		new Topic("fire", List.of(
			catchJson("fire.1", List.of("fire"), List.of("minecraft:desert", "#minecraft:is_badlands"), "", null, null, false),
			catchJson("fire.2", List.of("rock"), List.of("minecraft:stony_peaks", "minecraft:jagged_peaks"), "", null, null, false),
			catchJson("fire.3", List.of("ground"), List.of(), "", null, 0, false))),
		new Topic("night", List.of(
			catchJson("night.1", List.of("dark"), List.of(), "night", null, null, false),
			catchJson("night.2", List.of("ghost"), List.of(), "night", null, null, false),
			catchJson("night.3", List.of("psychic"), List.of(), "full_moon", null, null, false))),
		new Topic("forest", List.of(
			catchJson("forest.1", List.of("bug"), List.of("#minecraft:is_jungle"), "", null, null, false),
			catchJson("forest.2", List.of("grass"), List.of("#minecraft:is_forest"), "", null, null, false),
			catchJson("forest.3", List.of("flying"), List.of("minecraft:meadow", "minecraft:cherry_grove"), "", null, null, false))),
		new Topic("sea", List.of(
			catchJson("sea.1", List.of("water"), List.of("minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean"), "", null, null, false),
			catchJson("sea.2", List.of("water"), List.of("#minecraft:is_deep_ocean"), "", null, null, false),
			catchJson("sea.3", List.of("water"), List.of("#minecraft:is_beach"), "", null, null, false))),
		new Topic("fossils", List.of(
			catchJson("fossils.1", List.of(), List.of(), "", null, null, true),
			catchJson("fossils.2", List.of("dragon"), List.of(), "", null, null, false),
			catchJson("fossils.3", List.of("rock"), List.of("#minecraft:is_badlands"), "", null, null, false))));

	private static JsonObject catchJson(String text, List<String> types, List<String> biomes, String time, @Nullable Integer above, @Nullable Integer below, boolean fossil) {
		JsonObject o = new JsonObject();
		o.addProperty("type", "catch");
		o.addProperty("text", "quest.aliveworkplace.catch." + text);
		JsonArray t = new JsonArray();
		types.forEach(t::add);
		o.add("types", t);
		JsonArray b = new JsonArray();
		biomes.forEach(b::add);
		o.add("biomes", b);
		o.addProperty("time", time);
		if (above != null) {
			o.addProperty("above", above);
		}
		if (below != null) {
			o.addProperty("below", below);
		}
		o.addProperty("fossil", fossil);
		return o;
	}

	public static Optional<Topic> topic(String id) {
		return TOPICS.stream().filter(t -> t.id().equals(id)).findFirst();
	}

	public static void init() {
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> onUse(player, level, hand, entity));
	}

	static void validate(String step) {
		if (!List.of("begin", "field_notes", "peer_review", "ending").contains(step)) {
			throw new IllegalArgumentException("professors_thesis: 'step' must be begin, field_notes, peer_review or ending");
		}
	}

	/** One running thesis: its village and state. */
	public record Running(BlockPos hall, Stories.Entry entry, ArcState state) {
	}

	public static List<Running> running(ServerLevel level) {
		List<Running> out = new ArrayList<>();
		for (Map.Entry<BlockPos, Stories.Entry> en : List.copyOf(Stories.Data.get(level).halls.entrySet())) {
			ArcState s = en.getValue().arc;
			if (s != null && s.id.equals(ARC.toString())) {
				out.add(new Running(en.getKey(), en.getValue(), s));
			}
		}
		return out;
	}

	// ---- the patron ------------------------------------------------------------------------------------------------

	/** The village's Pokémon Professor, else its best Scholar at Expert or above; null with neither. */
	@Nullable
	public static Villager patron(ServerLevel level, BlockPos hall) {
		VillageHalls.Census census = VillageHalls.census(level, hall);
		List<Villager> all = new ArrayList<>(census.workers());
		all.addAll(census.jobless());
		for (Villager v : all) {
			if (PokemonProfessor.isProfessor(v)) {
				return v;
			}
		}
		Villager best = null;
		for (Villager v : all) { // (a Scholar away from the desk is still one)
			if (!v.isBaby() && v.getVillagerData().getProfession() == ModVillagers.SCHOLAR && BuilderLevels.level(v) >= 4
				&& (best == null || BuilderLevels.level(v) > BuilderLevels.level(best))) {
				best = v;
			}
		}
		return best;
	}

	/** "Professor Alder" for a Scholar; the Pokémon Professor's own name for them. */
	public static Component professorName(Villager patron) {
		Component name = patron.hasCustomName() ? patron.getCustomName() : patron.getDisplayName();
		return PokemonProfessor.isProfessor(patron) ? name.copy()
			: Component.translatable("arc.aliveworkplace.professors_thesis.professor", name);
	}

	/** The arc's trigger condition {@code thesis_patron}: the village has a Pokémon Professor or a Scholar at Expert or above. */
	public static final class PatronCondition implements Condition {
		@Override
		public String type() {
			return "thesis_patron";
		}

		@Override
		public Progress progress(ServerLevel level, BlockPos hall) {
			return Progress.of("thesis_patron", patron(level, hall) != null ? 1 : 0, 1);
		}
	}

	// ---- the effects -----------------------------------------------------------------------------------------------

	/** An arc effect {@code professors_thesis}: {@code begin}, {@code field_notes}, {@code peer_review} or {@code ending}. */
	static void effect(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arcs.Arc arc, String step) {
		switch (step) {
			case "begin" -> begin(level, hall, s);
			case "field_notes" -> fieldNotes(level, hall, e, s, arc);
			case "peer_review" -> peerReview(level, hall, s);
			case "ending" -> ending(level, hall, s);
			default -> {
			}
		}
	}

	/** The patron is chosen and a topic picked (unless a test picked one). No patron: the arc ends quietly. */
	static void begin(ServerLevel level, BlockPos hall, ArcState s) {
		Villager patron = patron(level, hall);
		if (patron == null) {
			s.endWith = "quiet";
			return;
		}
		s.roles.put(PROFESSOR, new ArcState.Role(Optional.of(patron.getUUID()), professorName(patron)));
		if (!s.extra.contains(TOPIC)) {
			s.extra.putString(TOPIC, FORCE_TOPIC != null ? FORCE_TOPIC : TOPICS.get(level.random.nextInt(TOPICS.size())).id());
		}
		Topic topic = topic(Nbt.getString(s.extra, TOPIC)).orElse(TOPICS.get(0));
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.arc.says", professorName(patron),
				Component.translatable("arc.aliveworkplace.professors_thesis.request", topic.name())).withStyle(ChatFormatting.WHITE));
		}
		Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.professors_thesis.topic", professorName(patron), topic.name()));
		Stories.Data.get(level).setDirty();
	}

	/** The topic's three catch quests go up. */
	static void fieldNotes(ServerLevel level, BlockPos hall, Stories.Entry e, ArcState s, Arcs.Arc arc) {
		Topic topic = topic(Nbt.getString(s.extra, TOPIC)).orElse(TOPICS.get(0));
		for (int i = 0; i < topic.catches().size(); i++) {
			JsonObject q = new JsonObject();
			ResourceLocation id = AliveWorkplace.id("arc/professors_thesis/" + topic.id() + "_" + (i + 1));
			q.addProperty("giver", "arc");
			JsonObject name = new JsonObject();
			name.addProperty("translate", "arc.aliveworkplace.professors_thesis.field_note");
			q.add("name", name);
			JsonArray objectives = new JsonArray();
			objectives.add(topic.catches().get(i));
			q.add("objectives", objectives);
			Arcs.QuestRef ref = new Arcs.QuestRef(id, QuestFiles.read(id, q));
			Quest posted = Arcs.quest(level, hall, s, ref);
			if (posted != null) {
				s.quests.add(new ArcState.ChapterQuest(posted.id, id.toString()));
			}
		}
		Stories.Data.get(level).setDirty();
	}

	/** Chapter 3: the nearest village within caravan range with a Trainer Leader, or else Thessaly comes to the hall. */
	static void peerReview(ServerLevel level, BlockPos hall, ArcState s) {
		BlockPos rival = leaderVillage(level, hall);
		if (rival != null) {
			s.extra.putLong(LEADER_HALL, rival.asLong());
			Stories.Data.get(level).setDirty();
			return;
		}
		Villager thessaly = thessaly(level, s);
		if (thessaly == null) {
			thessaly = spawnThessaly(level, hall, s);
		}
		if (thessaly != null) {
			for (ServerPlayer player : ArcEffects.audience(level, hall)) {
				Chat.chat(player, Component.translatable("message.aliveworkplace.arc.says", thessaly.getDisplayName(),
					Component.translatable("arc.aliveworkplace.professors_thesis.thessaly_arrives")).withStyle(ChatFormatting.WHITE));
			}
		}
	}

	/** The hall of the nearest village within caravan range that has a Trainer Leader, or null. */
	@Nullable
	public static BlockPos leaderVillage(ServerLevel level, BlockPos hall) {
		for (Caravans.Village v : Caravans.neighbours(level, hall)) {
			Caravans.Leader known = Caravans.Data.get(level).leader(v.hall()); // (as the village's last round wrote it, loaded or not)
			if (known != null && known.leader() || level.isLoaded(v.hall()) && level.getBlockEntity(v.hall()) instanceof VillageHallBlockEntity
				&& anyLeader(VillageHalls.census(level, v.hall()))) {
				return v.hall();
			}
		}
		return null;
	}

	private static boolean anyLeader(VillageHalls.Census census) {
		return census.workers().stream().anyMatch(Trainers::isLeader) || census.jobless().stream().anyMatch(Trainers::isLeader);
	}

	/** The rival scholar Thessaly: a Master trainer, named, standing by the hall until she's beaten or the arc ends. */
	@Nullable
	static Villager spawnThessaly(ServerLevel level, BlockPos hall, ArcState s) {
		Villager v = EntityType.VILLAGER.create(level);
		if (v == null) {
			return null;
		}
		BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(2, 0, 2));
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		v.setVillagerData(v.getVillagerData().setType(VillagerType.PLAINS).setProfession(ModVillagers.TRAINER).setLevel(5));
		v.setVillagerXp(250);
		v.setCustomName(Component.translatable("entity.aliveworkplace.thessaly"));
		v.setCustomNameVisible(true);
		v.setPersistenceRequired();
		v.addTag(Arcs.TAG);
		v.addTag(ArcEffects.arcTag(s.id));
		v.addTag(THESSALY_TAG);
		Nbt.putUuid(s.extra, THESSALY, v.getUUID()); // before she joins the level, so onLoad knows her
		level.addFreshEntity(v);
		Stories.Data.get(level).setDirty();
		return v;
	}

	@Nullable
	public static Villager thessaly(ServerLevel level, ArcState s) {
		return Nbt.hasUuid(s.extra, THESSALY) && level.getEntity(Nbt.getUuid(s.extra, THESSALY)) instanceof Villager v ? v : null;
	}

	/** Thessaly leaves in a puff (beaten, or the arc is over). */
	static void dismissThessaly(ServerLevel level, ArcState s) {
		Villager v = thessaly(level, s);
		if (v != null) {
			level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, v.getX(), v.getY() + 1, v.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
			v.discard();
		}
	}

	/**
	 * A Thessaly loads whom no running arc remembers (B663: her thesis ended while she was away, or in a world saved
	 * before the arc's end sent her off): she's gone. True when she was.
	 */
	static boolean onLoad(ServerLevel level, Entity entity) {
		if (!entity.getTags().contains(THESSALY_TAG)) {
			return false;
		}
		for (Stories.Entry e : Stories.Data.get(level).halls.values()) {
			for (ArcState s : Arcs.running(e)) {
				if (Nbt.hasUuid(s.extra, THESSALY) && Nbt.getUuid(s.extra, THESSALY).equals(entity.getUUID())) {
					return false;
				}
			}
		}
		entity.discard();
		return true;
	}

	// ---- catches ---------------------------------------------------------------------------------------------------

	/**
	 * {@code player} caught (or had a Fossil Scientist revive) a Pokémon: it counts towards the first open catch quest of a
	 * running thesis it matches, and is written down for the book. True when it counted.
	 */
	public static boolean onCatch(ServerLevel level, ServerPlayer player, CatchInfo info) {
		for (Running r : running(level)) {
			ArcState s = r.state();
			for (Quest q : List.copyOf(r.entry().quests)) {
				int i = q.current();
				if (i < 0 || !s.id.equals(q.arc) || !(q.objectives.get(i) instanceof Catch c) || !c.matches(level, info)
					|| !(level.getBlockEntity(r.hall()) instanceof VillageHallBlockEntity entity) || !Stories.mayHelp(level, r.hall(), q, player)) {
					continue;
				}
				CompoundTag t = new CompoundTag();
				Nbt.putUuid(t, "uuid", info.uuid());
				t.putString("species", info.species().toString());
				t.putString("name", info.name().getString());
				t.putString("biome", info.biome().unwrapKey().map(k -> k.location().toString()).orElse(""));
				t.putLong("pos", info.pos().asLong());
				t.putLong("day", info.day());
				t.putString("by", player.getGameProfile().getName());
				ListTag list = Nbt.getList(s.extra, CATCHES, Tag.TAG_COMPOUND);
				list.add(t);
				s.extra.put(CATCHES, list);
				Chat.chat(player, Component.translatable("message.aliveworkplace.professors_thesis.caught", info.name(), c.line()).withStyle(ChatFormatting.GREEN));
				Stories.Data.get(level).setDirty();
				Stories.progress(level, r.hall(), entity, q, i, 1, player);
				return true;
			}
		}
		return false;
	}

	/** One catch written down: species, its name, biome, place, day and catcher. */
	public record Caught(UUID uuid, ResourceLocation species, String name, String biome, BlockPos pos, long day, String by) {
	}

	public static List<Caught> catches(ArcState s) {
		List<Caught> out = new ArrayList<>();
		ListTag list = Nbt.getList(s.extra, CATCHES, Tag.TAG_COMPOUND);
		for (int i = 0; i < list.size(); i++) {
			CompoundTag t = Nbt.compoundAt(list, i);
			out.add(new Caught(Nbt.getUuid(t, "uuid"), ResourceLocation.parse(Nbt.getString(t, "species")), Nbt.getString(t, "name"), Nbt.getString(t, "biome"),
				BlockPos.of(Nbt.getLong(t, "pos")), Nbt.getLong(t, "day"), Nbt.getString(t, "by")));
		}
		return out;
	}

	/**
	 * {@code catch}: one Pokémon of these types (any, when none), in one of these biomes (ids, or {@code #tags}; any when
	 * none), at a time ({@code day}, {@code night}, {@code full_moon} or any), above or below a height, or a fossil
	 * revived by a Fossil Scientist. Its line is the lang key {@code text}.
	 */
	public record Catch(String text, List<String> types, List<String> biomes, String time, int above, int below, boolean fossil) implements Objectives.Objective {
		static final int NONE = Integer.MIN_VALUE;

		static Catch read(JsonObject json) {
			List<String> types = new ArrayList<>();
			if (json.has("types")) {
				json.getAsJsonArray("types").forEach(t -> types.add(t.getAsString().toLowerCase(java.util.Locale.ROOT)));
			}
			List<String> biomes = new ArrayList<>();
			if (json.has("biomes")) {
				json.getAsJsonArray("biomes").forEach(t -> biomes.add(t.getAsString()));
			}
			String time = GsonHelper.getAsString(json, "time", "");
			if (!List.of("", "day", "night", "full_moon").contains(time)) {
				throw new IllegalArgumentException("catch: 'time' must be day, night or full_moon");
			}
			return new Catch(GsonHelper.getAsString(json, "text", "quest.aliveworkplace.catch"), List.copyOf(types), List.copyOf(biomes), time,
				json.has("above") ? GsonHelper.getAsInt(json, "above") : NONE, json.has("below") ? GsonHelper.getAsInt(json, "below") : NONE,
				GsonHelper.getAsBoolean(json, "fossil", false));
		}

		public boolean matches(ServerLevel level, CatchInfo info) {
			if (fossil && !info.fossil()) {
				return false;
			}
			if (!types.isEmpty() && info.types().stream().noneMatch(types::contains)) {
				return false;
			}
			if (above != NONE && info.pos().getY() <= above || below != NONE && info.pos().getY() >= below) {
				return false;
			}
			switch (time) {
				case "day" -> {
					if (info.night()) {
						return false;
					}
				}
				case "night" -> {
					if (!info.night()) {
						return false;
					}
				}
				case "full_moon" -> {
					if (!info.night() || info.moonPhase() != 0) {
						return false;
					}
				}
				default -> {
				}
			}
			if (biomes.isEmpty()) {
				return true;
			}
			for (String b : biomes) {
				if (b.startsWith("#")) {
					ResourceLocation tag = ResourceLocation.tryParse(b.substring(1));
					if (tag != null && info.biome().is(TagKey.create(Registries.BIOME, tag))) {
						return true;
					}
				} else if (info.biome().unwrapKey().map(k -> k.location().toString().equals(b)).orElse(false)) {
					return true;
				}
			}
			return false;
		}

		@Override
		public String type() {
			return "catch";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable(text);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("text", text);
			JsonArray t = new JsonArray();
			types.forEach(t::add);
			o.add("types", t);
			JsonArray b = new JsonArray();
			biomes.forEach(b::add);
			o.add("biomes", b);
			o.addProperty("time", time);
			if (above != NONE) {
				o.addProperty("above", above);
			}
			if (below != NONE) {
				o.addProperty("below", below);
			}
			o.addProperty("fossil", fossil);
			return o;
		}
	}

	// ---- show ------------------------------------------------------------------------------------------------------

	/** {@code show}: show the professor (right-click them) one of the thesis' catches evolved, or any Pokémon at friendship 200+. */
	public record Show(int friendship) implements Objectives.Objective {
		static Show read(JsonObject json) {
			return new Show(GsonHelper.getAsInt(json, "friendship", FRIENDSHIP));
		}

		@Override
		public String type() {
			return "show";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return Component.translatable("quest.aliveworkplace.show", friendship);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("friendship", friendship);
			return o;
		}
	}

	/** Which of {@code party} the professor would accept: a catch of {@code s} that has evolved, or one at {@code friendship}+. */
	@Nullable
	public static Mon shown(ArcState s, List<Mon> party, int friendship) {
		List<Caught> caught = catches(s);
		for (Mon m : party) {
			if (caught.stream().anyMatch(c -> c.uuid().equals(m.uuid()) && !c.species().equals(m.species())) || m.friendship() >= friendship) {
				return m;
			}
		}
		return null;
	}

	/** A right-click on the thesis' professor while a {@code show} is open: the party is looked at. */
	public static InteractionResult onUse(Player player, Level world, InteractionHand hand, Entity entity) {
		if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || !(entity instanceof Villager v)) {
			return InteractionResult.PASS;
		}
		return show(level, sp, v) ? InteractionResult.SUCCESS : InteractionResult.PASS;
	}

	/** {@code player} shows {@code professor} their party. True when a thesis' show was open with them (whatever came of it). */
	public static boolean show(ServerLevel level, ServerPlayer player, Villager professor) {
		for (Running r : running(level)) {
			ArcState s = r.state();
			ArcState.Role role = s.roles.get(PROFESSOR);
			if (role == null || role.who().isEmpty() || !role.who().get().equals(professor.getUUID())) {
				continue;
			}
			for (Quest q : List.copyOf(r.entry().quests)) {
				int i = q.current();
				if (i < 0 || !s.id.equals(q.arc) || !(q.objectives.get(i) instanceof Show show)
					|| !(level.getBlockEntity(r.hall()) instanceof VillageHallBlockEntity entity) || !Stories.mayHelp(level, r.hall(), q, player)) {
					continue;
				}
				Mon m = shown(s, Party.EXTENSION.call(p -> p.party(player), List.<Mon>of()), show.friendship());
				if (m == null) {
					Chat.chat(player, Component.translatable("message.aliveworkplace.arc.says", role.name(),
						Component.translatable("arc.aliveworkplace.professors_thesis.show_more", show.friendship())).withStyle(ChatFormatting.WHITE));
					return true;
				}
				Chat.chat(player, Component.translatable("message.aliveworkplace.arc.says", role.name(),
					Component.translatable("arc.aliveworkplace.professors_thesis.shown")).withStyle(ChatFormatting.WHITE));
				level.playSound(null, professor.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
				Stories.progress(level, r.hall(), entity, q, i, 1, player);
				return true;
			}
		}
		return false;
	}

	// ---- battle_leader ---------------------------------------------------------------------------------------------

	/** {@code battle_leader}: beat the Trainer Leader of another village (or Thessaly, who comes when there's none). */
	public record BattleLeader(String village) implements Objectives.Objective {
		static BattleLeader read(JsonObject json) {
			return new BattleLeader(GsonHelper.getAsString(json, "village", ""));
		}

		@Override
		public String type() {
			return "battle_leader";
		}

		@Override
		public int need() {
			return 1;
		}

		@Override
		public Component line() {
			return village.isEmpty() ? Component.translatable("quest.aliveworkplace.battle_leader")
				: village.equals("@thessaly") ? Component.translatable("quest.aliveworkplace.battle_leader.thessaly")
				: Component.translatable("quest.aliveworkplace.battle_leader.at", village);
		}

		@Override
		public JsonObject json() {
			JsonObject o = new JsonObject();
			o.addProperty("type", type());
			o.addProperty("village", village);
			return o;
		}

		@Override
		public Objectives.Objective resolve(Objectives.Context context) {
			if (!village.isEmpty()) {
				return this;
			}
			ArcState s = Arcs.running(context.level, context.hall);
			if (s == null) {
				return this;
			}
			if (s.extra.contains(LEADER_HALL)) {
				return new BattleLeader(VillageHalls.name(context.level, BlockPos.of(Nbt.getLong(s.extra, LEADER_HALL))).getString());
			}
			return Nbt.hasUuid(s.extra, THESSALY) ? new BattleLeader("@thessaly") : this;
		}
	}

	/**
	 * {@code player} won a battle against the trainer {@code trainer}: Thessaly, or a Trainer Leader of a village other than
	 * the thesis' own, counts for an open {@code battle_leader}. Thessaly leaves once beaten. True when it counted.
	 */
	public static boolean onTrainerWon(ServerLevel level, Villager trainer, ServerPlayer player) {
		boolean isThessaly = trainer.getTags().contains(THESSALY_TAG);
		if (!isThessaly && !Trainers.isLeader(trainer)) {
			return false;
		}
		BlockPos trainerHall = VillageHalls.nearest(level, trainer.blockPosition()).orElse(null);
		for (Running r : running(level)) {
			ArcState s = r.state();
			boolean counts = isThessaly ? Nbt.hasUuid(s.extra, THESSALY) && Nbt.getUuid(s.extra, THESSALY).equals(trainer.getUUID())
				: trainerHall != null && !trainerHall.equals(r.hall());
			if (!counts) {
				continue;
			}
			for (Quest q : List.copyOf(r.entry().quests)) {
				int i = q.current();
				if (i < 0 || !s.id.equals(q.arc) || !(q.objectives.get(i) instanceof BattleLeader)
					|| !(level.getBlockEntity(r.hall()) instanceof VillageHallBlockEntity entity) || !Stories.mayHelp(level, r.hall(), q, player)) {
					continue;
				}
				if (isThessaly) {
					Chat.chat(player, Component.translatable("message.aliveworkplace.arc.says", trainer.getDisplayName(),
						Component.translatable("arc.aliveworkplace.professors_thesis.thessaly_leaves")).withStyle(ChatFormatting.WHITE));
					dismissThessaly(level, s);
				}
				Stories.progress(level, r.hall(), entity, q, i, 1, player);
				return true;
			}
		}
		return false;
	}

	// ---- the ending ------------------------------------------------------------------------------------------------

	/** The thesis is published: the book and the rewards to each helper, a free research level to the village. */
	static void ending(ServerLevel level, BlockPos hall, ArcState s) {
		dismissThessaly(level, s);
		Topic topic = topic(Nbt.getString(s.extra, TOPIC)).orElse(TOPICS.get(0));
		ArcState.Role role = s.roles.get(PROFESSOR);
		Component professor = role != null ? role.name() : Component.translatable("chronicle.aliveworkplace.someone");
		Villager patron = role != null && role.who().isPresent() && level.getEntity(role.who().get()) instanceof Villager v ? v : null;
		java.util.Collection<UUID> who = Reputation.paid("helpers", s);
		for (UUID id : who) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
			List<ItemStack> gifts = new ArrayList<>();
			gifts.add(book(level, s, professor, topic, player != null ? player.getGameProfile().getName() : ""));
			gifts.addAll(rewards());
			for (ItemStack g : gifts) {
				if (player == null || !player.getInventory().add(g)) {
					net.minecraft.world.level.block.Block.popResource(level, player != null ? player.blockPosition() : hall.above(), g);
				}
			}
			if (player != null) {
				Chat.chat(player, Component.translatable("message.aliveworkplace.professors_thesis.published", professor, topic.name()).withStyle(ChatFormatting.GOLD));
			}
		}
		Component research = freeResearch(level, hall, patron);
		Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.professors_thesis.published", professor, topic.name()));
		if (research != null) {
			Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Component.translatable("chronicle.aliveworkplace.professors_thesis.research", research));
		}
	}

	/** 3 Rare Candies and an Ability Capsule, by item id; emeralds for each one Cobblemon doesn't have. */
	public static List<ItemStack> rewards() {
		List<ItemStack> out = new ArrayList<>();
		int emeralds = 0;
		Optional<Item> candy = BuiltInRegistries.ITEM.getOptional(RARE_CANDY);
		Optional<Item> capsule = BuiltInRegistries.ITEM.getOptional(ABILITY_CAPSULE);
		if (candy.isPresent()) {
			out.add(new ItemStack(candy.get(), RARE_CANDIES));
		} else {
			emeralds += RARE_CANDIES * EMERALDS_PER_MISSING;
		}
		if (capsule.isPresent()) {
			out.add(new ItemStack(capsule.get()));
		} else {
			emeralds += EMERALDS_PER_MISSING;
		}
		if (emeralds > 0) {
			out.add(new ItemStack(Items.EMERALD, emeralds));
		}
		return out;
	}

	/** The published thesis: "<Professor>: <topic>", the catches (species, place and day), the co-author. */
	public static ItemStack book(ServerLevel level, ArcState s, Component professor, Topic topic, String coAuthor) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		Component title = Component.translatable("item.aliveworkplace.thesis.title", professor, topic.name());
		String plain = title.getString();
		List<Filterable<Component>> pages = new ArrayList<>();
		pages.add(Filterable.passThrough(Component.translatable("item.aliveworkplace.thesis.page.cover", topic.name(), professor,
			coAuthor.isEmpty() ? Component.translatable("chronicle.aliveworkplace.someone") : Component.literal(coAuthor))));
		for (Caught c : catches(s)) {
			ResourceLocation biome = ResourceLocation.tryParse(c.biome());
			Component place = biome == null ? Component.literal("?") : Component.translatable("biome." + biome.getNamespace() + "." + biome.getPath());
			pages.add(Filterable.passThrough(Component.translatable("item.aliveworkplace.thesis.page.catch", c.name(), place,
				c.pos().getX(), c.pos().getY(), c.pos().getZ(), c.day(), c.by())));
		}
		pages.add(Filterable.passThrough(Component.translatable("item.aliveworkplace.thesis.page.end", professor)));
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
			Filterable.passThrough(plain.length() > WrittenBookContent.TITLE_MAX_LENGTH ? plain.substring(0, WrittenBookContent.TITLE_MAX_LENGTH) : plain),
			professor.getString(), 0, pages, true));
		book.set(DataComponents.CUSTOM_NAME, title.copy().withStyle(s2 -> s2.withItalic(false)));
		return book;
	}

	/**
	 * A free research level for the village: with the Pokémon Professor as patron, the Pokédex tree's topic in progress or
	 * its next open one; otherwise the scholars' topic in progress, or their next open one. Returns its name, or null.
	 */
	@Nullable
	static Component freeResearch(ServerLevel level, BlockPos hall, @Nullable Villager patron) {
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return null;
		}
		Research.State state = entity.research();
		if (patron != null && PokemonProfessor.isProfessor(patron)) {
			ResearchTree tree = ResearchTrees.get(PokemonProfessor.TREE).orElse(null);
			if (tree != null) {
				ResearchTree.Topic topic = ResearchTrees.current(state, tree).map(ResearchTrees.Current::topic).orElse(null);
				if (topic == null) {
					topic = tree.topics().stream().filter(t -> ResearchTrees.level(state, tree, t) < t.levels()).findFirst().orElse(null);
				}
				if (topic != null) {
					Research.State next = ResearchTrees.finish(ResearchTrees.choose(state, tree, topic), tree);
					entity.setResearch(next);
					entity.setChanged();
					return topic.name();
				}
			}
		}
		Research.Topic topic = state.currentTopic();
		Research.State base = state;
		if (topic == null) {
			for (Research.Topic t : Research.Topic.values()) {
				if (state.available(t)) {
					topic = t;
					base = state.choose(t);
					break;
				}
			}
		}
		if (topic == null) {
			return null;
		}
		entity.setResearch(base.finish());
		entity.setChanged();
		return topic.title();
	}

	private ProfessorsThesis() {
	}
}

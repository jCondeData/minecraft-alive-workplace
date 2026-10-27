package io.github.jcondedata.aliveworkplace.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.herb.HerbBlock;
import io.github.jcondedata.aliveworkplace.herb.Herbs;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.nurse.Nurses;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.work.PhysicianWork;
import io.github.jcondedata.aliveworkplace.work.Words;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The Sickness, II: the Grey Cough (ROADMAP 31.18), what {@code arcs/the_sickness.json} can't say in effects alone. Three
 * villagers fall ill with the Grey Cough ({@link Sickness#GREY}), which passes on at night ({@link #spread}) and which
 * no nurse's remedy or Physician's Remedy cures. The village's healer (its Nurse, else its Physician, else Doctor Wren, a
 * travelling physician who comes to the hall the next morning) names the three far herbs (31.17) whose biomes are nearest and gives a map to each patch; with 3 of
 * each handed in, the healer brews the Three-Herb Tonic over half a day and takes it round. Without the cure in 15 days
 * the cough runs its course. Everything it keeps is in the arc's flags and roles, so it carries on after a restart.
 *
 * <p>Some flags carry a number in their name ({@code brew_from.<day time>}): the arc's flags hold days, and these hold
 * more than that ({@link #stamp}).
 */
public final class SicknessStory {
	public static final ResourceLocation ARC = AliveWorkplace.id("the_sickness");
	/** The healer's role: the village's Nurse, else its Physician (32.18), else Doctor Wren. */
	public static final String HEALER = "healer";
	/** Doctor Wren's mob key. */
	public static final String WREN = "wren";
	/** Flags. */
	public static final String HEALER_TOLD = "healer_told";
	public static final String HERBS_GIVEN = "herbs_given";
	public static final String BREWED = "brewed";
	public static final String CURED = "cured";
	static final String SPREAD_NIGHT = "spread_night";
	/** Stamps: the day Wren comes, the day time the brew began, the day the cough began to run its course. */
	static final String WREN_DUE = "wren_due";
	static final String BREW_FROM = "brew_from";
	static final String COURSE = "course";
	/** Name flags: {@code ill.<uuid>} for each who caught it, {@code given.<herb>} for each herb handed in, {@code maps.<uuid>}. */
	static final String ILL = "ill.";
	static final String GIVEN = "given.";
	static final String MAPS = "maps.";
	/** The herb patches' place keys: this, then the herb's path. */
	static final String HERB_PLACE = "herb_";

	/** How many fall ill when it begins, how many herbs the cure takes and how many of each. */
	public static final int FIRST_ILL = 3;
	public static final int HERBS = 3;
	public static final int EACH = 3;
	/** The night's pass-on: within this many blocks of where they work or sleep, at this chance. */
	public static final int NEAR = 8;
	public static final float CHANCE = 0.4f;
	/** The brew takes half a day; the cough runs its course after 15 days without the cure, everyone well within 3. */
	public static final long BREW = VillageNeeds.DAY / 2;
	public static final int COURSE_AFTER_DAYS = 15;
	public static final int COURSE_DAYS = 3;
	public static final int COURSE_MOOD = -15;
	public static final int COURSE_MOOD_DAYS = 5;
	/** Friendship for each gatherer with each villager who was ill. */
	public static final int FRIENDSHIP = 100;
	/** Night falls (day time in the day) for the pass-on. */
	static final long NIGHT = 13000;
	/** How long the healer may take to walk to one patient before stepping over (day-time ticks). */
	static final long WALK_LIMIT = 400;

	/** Where a herb's biome is nearest: a lookup within {@link Places#MAX_BLOCKS}, or null. Tests force the answers. */
	public interface Locator {
		@Nullable
		BlockPos find(ServerLevel level, BlockPos from, HerbBlock herb);
	}

	public static Locator LOCATOR = SicknessStory::nearestBiome;

	private static final Map<UUID, String> LAST_USE = new HashMap<>();
	/** The healer's walk to one patient: who, since when (not saved: a restart starts the walk again). */
	private static final Map<UUID, Map.Entry<UUID, Long>> WALKS = new HashMap<>();

	public static void init() {
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> {
			// One click can come as two packets (at a point, then plain): the story acts on the first.
			String click = entity.getUUID() + "@" + level.getGameTime();
			if (click.equals(LAST_USE.get(player.getUUID()))) {
				return InteractionResult.SUCCESS;
			}
			InteractionResult result = onUse(player, level, hand, entity);
			if (result != InteractionResult.PASS) {
				LAST_USE.put(player.getUUID(), click);
			}
			return result;
		});
		Platform.get().onServerTick(server -> {
			if (server.getTickCount() % Arcs.CHECK_EVERY == 13) {
				for (ServerLevel level : server.getAllLevels()) {
					tick(level);
				}
			}
		});
	}

	static void validate(String step) {
		if (!List.of("outbreak", "herbs", "brew", "ending").contains(step)) {
			throw new IllegalArgumentException("sickness: 'step' must be outbreak, herbs, brew or ending");
		}
	}

	/** One running Sickness arc: its village and state. */
	public record Running(BlockPos hall, Stories.Entry entry, ArcState state) {
	}

	/** The Sickness arcs running in {@code level}. */
	public static List<Running> running(ServerLevel level) {
		List<Running> out = new ArrayList<>();
		for (Map.Entry<BlockPos, Stories.Entry> en : Stories.Data.get(level).halls.entrySet()) {
			ArcState s = en.getValue().arc;
			if (s != null && s.id.equals(ARC.toString())) {
				out.add(new Running(en.getKey(), en.getValue(), s));
			}
		}
		return out;
	}

	@Nullable
	public static Running running(ServerLevel level, BlockPos hall) {
		return running(level).stream().filter(r -> r.hall().equals(hall)).findFirst().orElse(null);
	}

	/** Whether a Sickness arc runs in the village {@code villager} lives in (else a Grey Cough lifts at the next round). */
	public static boolean coughing(ServerLevel level, Villager villager) {
		for (Running r : running(level)) {
			if (VillageHalls.area(r.hall()).contains(villager.position())) {
				return true;
			}
		}
		return false;
	}

	// ---- stamps ----------------------------------------------------------------------------------------------------

	/** Sets the stamp {@code name} to {@code value} (in place of any before). */
	static void stamp(ArcState s, String name, long value) {
		s.flags.keySet().removeIf(k -> k.startsWith(name + "."));
		s.flags.put(name + "." + value, ArcState.FOREVER);
	}

	/** The stamp {@code name}'s value, or -1 when it isn't set. */
	public static long stamp(ArcState s, String name) {
		for (String k : s.flags.keySet()) {
			if (k.startsWith(name + ".")) {
				try {
					return Long.parseLong(k.substring(name.length() + 1));
				} catch (NumberFormatException e) {
					return -1;
				}
			}
		}
		return -1;
	}

	/** The villagers who caught the Grey Cough in this arc. */
	public static List<UUID> caught(ArcState s) {
		List<UUID> out = new ArrayList<>();
		for (String k : s.flags.keySet()) {
			if (k.startsWith(ILL)) {
				try {
					out.add(UUID.fromString(k.substring(ILL.length())));
				} catch (IllegalArgumentException ignored) {
					// (not one of ours)
				}
			}
		}
		return out;
	}

	private static void caught(ArcState s, Villager v) {
		s.flags.put(ILL + v.getUUID(), ArcState.FOREVER);
	}

	// ---- telling ---------------------------------------------------------------------------------------------------

	private static void tell(ServerLevel level, BlockPos hall, String key, ChatFormatting colour, Object... args) {
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			Chat.chat(player, Words.countedFirst("message.aliveworkplace.sickness." + key, args).withStyle(colour));
		}
	}

	private static void chronicle(ServerLevel level, BlockPos hall, String key, Object... args) {
		Chronicle.atHall(level, hall, Chronicle.Kind.STORY, Words.countedFirst("chronicle.aliveworkplace.sickness." + key, args));
	}

	private static void says(ServerPlayer player, Entity healer, String key, Object... args) {
		Chat.chat(player, Component.translatable("message.aliveworkplace.arc.says", healer.getDisplayName(),
			Component.translatable("arc.aliveworkplace.the_sickness." + key, args)).withStyle(ChatFormatting.WHITE));
	}

	/** "A, B and C". */
	static Component list(List<Component> names) {
		if (names.isEmpty()) {
			return Component.empty();
		}
		MutableComponent out = names.get(0).copy();
		for (int i = 1; i < names.size(); i++) {
			out = Component.translatable(i == names.size() - 1 ? "message.aliveworkplace.sickness.and" : "message.aliveworkplace.sickness.comma", out, names.get(i));
		}
		return out;
	}

	// ---- the village -----------------------------------------------------------------------------------------------

	/** The village's grown villagers who could catch the cough: alive, not the arc's own (Doctor Wren). */
	public static List<Villager> villagers(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby() && !Arcs.isArcEntity(v));
	}

	/** The coughing villagers of the village round {@code hall} (those loaded). */
	public static List<Villager> coughers(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && Sickness.isGrey(v));
	}

	/** How many may cough at once: half the village. */
	public static int cap(ServerLevel level, BlockPos hall) {
		return villagers(level, hall).size() / 2;
	}

	/** Where {@code v} works and sleeps (in this dimension); where they stand, if neither is known. */
	static List<BlockPos> haunts(ServerLevel level, Villager v) {
		List<BlockPos> out = new ArrayList<>();
		for (MemoryModuleType<GlobalPos> m : List.of(MemoryModuleType.HOME, MemoryModuleType.JOB_SITE)) {
			v.getBrain().getMemory(m).filter(g -> g.dimension() == level.dimension()).ifPresent(g -> out.add(g.pos()));
		}
		if (out.isEmpty()) {
			out.add(v.blockPosition());
		}
		return out;
	}

	/** Whether {@code other} worked or slept within {@link #NEAR} blocks of the coughing {@code cougher}. */
	static boolean near(ServerLevel level, Villager cougher, Villager other) {
		List<BlockPos> theirs = new ArrayList<>(haunts(level, cougher));
		theirs.add(cougher.blockPosition());
		for (BlockPos a : haunts(level, other)) {
			for (BlockPos b : theirs) {
				if (a.distSqr(b) <= NEAR * NEAR) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * A night's pass-on: each coughing villager passes it to one villager who worked or slept within {@link #NEAR} blocks
	 * of them, at {@link #CHANCE}, never past half the village. Returns how many caught it.
	 */
	public static int spread(ServerLevel level, BlockPos hall, ArcState s, RandomSource random) {
		List<Villager> all = villagers(level, hall);
		int cap = all.size() / 2;
		List<Villager> coughing = all.stream().filter(Sickness::isGrey).toList();
		int ill = coughing.size();
		int caught = 0;
		for (Villager c : coughing) {
			if (ill >= cap) {
				break;
			}
			if (random.nextFloat() >= CHANCE) {
				continue;
			}
			List<Villager> near = all.stream().filter(v -> v != c && !Sickness.isIll(v)
				&& !io.github.jcondedata.aliveworkplace.legend.Gifted.noIllness(v) && near(level, c, v)).toList();
			if (near.isEmpty()) {
				continue;
			}
			Villager v = near.get(random.nextInt(near.size()));
			if (Sickness.greyCough(level, v)) {
				caught(s, v);
				ill++;
				caught++;
			}
		}
		if (caught > 0) {
			Stories.Data.get(level).setDirty();
			chronicle(level, hall, "spread", caught);
		}
		return caught;
	}

	// ---- the healer ------------------------------------------------------------------------------------------------

	/**
	 * Whether {@code v} is a healer a village can have: a Nurse, or 32.18's Physician (while config {@code physicians} is
	 * on and the disasters expansion is open, as for the rest of the Physician's work).
	 */
	public static boolean isHealerJob(Villager v) {
		return Nurses.isNurse(v) || isPhysicianHealer(v);
	}

	private static boolean isPhysicianHealer(Villager v) {
		return PhysicianWork.ENABLED && Disasters.live() && PhysicianWork.isPhysician(v);
	}

	/** The village's healer by job: its Nurse nearest the hall, else its Physician nearest the hall (B135), or null. */
	@Nullable
	static Villager findHealer(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && isHealerJob(v) && !Arcs.isArcEntity(v)).stream()
			.min(Comparator.<Villager, Boolean>comparing(v -> !Nurses.isNurse(v))
				.thenComparingDouble(v -> v.distanceToSqr(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5))).orElse(null);
	}

	/** The arc's healer, if they're about. */
	@Nullable
	public static Villager healer(ServerLevel level, ArcState s) {
		ArcState.Role role = s.roles.get(HEALER);
		Entity found = role == null ? null : role.who().map(level::getEntity).orElse(null);
		return found instanceof Villager v && v.isAlive() ? v : null;
	}

	/**
	 * A villager died: when it's Doctor Wren (B618), his part in the arc is over (as for any arc mob that dies, so he isn't
	 * silently put back at the hall); the story's next round sends for someone to take the healer's part.
	 */
	public static void onDeath(ServerLevel level, Villager villager) {
		if (villager.getTags().contains(Arcs.MOB_TAG) && villager.getTags().contains(ArcEffects.roleTag(HEALER))) {
			Arcs.onDeath(level, villager);
		}
	}

	/** Whether Doctor Wren is the healer (or coming); a Wren who was killed isn't (B618: another is sent for). */
	static boolean wren(ArcState s) {
		return s.mobs.stream().anyMatch(m -> m.key.equals(WREN) && !m.dead) || stamp(s, WREN_DUE) >= 0;
	}

	/**
	 * Picks the healer: the village's Nurse, else its Physician; with neither, Doctor Wren comes to the hall the next
	 * morning (and with one, he isn't sent for).
	 */
	static void pickHealer(ServerLevel level, BlockPos hall, ArcState s) {
		Villager found = findHealer(level, hall);
		if (found != null) {
			s.roles.put(HEALER, new ArcState.Role(Optional.of(found.getUUID()), found.getDisplayName().copy()));
			return;
		}
		s.roles.put(HEALER, new ArcState.Role(Optional.empty(), Component.translatable("entity.aliveworkplace.doctor_wren")));
		stamp(s, WREN_DUE, Chronicle.day(level) + 1);
		tell(level, hall, "wren_sent", ChatFormatting.YELLOW);
	}

	/** Doctor Wren comes: a villager in a cleric's robe, by the hall, until the arc is over. */
	@Nullable
	static Villager wrenArrives(ServerLevel level, BlockPos hall, ArcState s) {
		s.flags.keySet().removeIf(k -> k.startsWith(WREN_DUE + "."));
		JsonObject spec = JsonParser.parseString("{\"entity\": \"minecraft:villager\", \"key\": \"" + WREN + "\", \"role\": \"" + HEALER
			+ "\", \"name\": {\"translate\": \"entity.aliveworkplace.doctor_wren\"}, \"restrict\": 10}").getAsJsonObject();
		ArcState.ArcMob m = new ArcState.ArcMob(WREN, "", spec, hall.offset(2, 0, 2));
		s.mobs.add(m);
		Stories.Data.get(level).setDirty();
		tell(level, hall, "wren_arrives", ChatFormatting.GREEN);
		chronicle(level, hall, "wren_arrives");
		if (!level.isLoaded(m.spot)) {
			return null; // (he's put at the hall once a player is near: the arc's mob check)
		}
		Villager wren = ArcEffects.spawn(level, s, m) instanceof Villager v ? v : null;
		if (wren != null) {
			dress(wren);
			level.sendParticles(ParticleTypes.CLOUD, wren.getX(), wren.getY() + 0.5, wren.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		}
		return wren;
	}

	/** Doctor Wren wears a cleric's robe, and keeps it without a workstation. */
	static void dress(Villager wren) {
		if (wren.getVillagerData().getProfession() != VillagerProfession.CLERIC) {
			wren.setVillagerData(wren.getVillagerData().setProfession(VillagerProfession.CLERIC).setLevel(3));
			wren.setVillagerXp(70);
		}
	}

	/** Whether {@code villager} is a Sickness arc's healer brewing the cure or taking it round now (their work waits). */
	public static boolean busy(ServerLevel level, Villager villager) {
		for (Running r : running(level)) {
			ArcState s = r.state();
			ArcState.Role role = s.roles.get(HEALER);
			if (s.chapter == 2 && s.started && !s.flags.containsKey(CURED) && stamp(s, COURSE) < 0
				&& role != null && role.who().isPresent() && role.who().get().equals(villager.getUUID())) {
				return true;
			}
		}
		return false;
	}

	// ---- the effects -----------------------------------------------------------------------------------------------

	/** An arc effect {@code sickness}: {@code outbreak}, {@code herbs}, {@code brew} or {@code ending}. */
	static void effect(ServerLevel level, BlockPos hall, ArcState s, String step) {
		switch (step) {
			case "outbreak" -> outbreak(level, hall, s, level.random);
			case "herbs" -> herbs(level, hall, s);
			case "brew" -> {
				stamp(s, BREW_FROM, level.getDayTime());
				Villager healer = healer(level, s);
				if (healer != null) {
					tell(level, hall, "brewing", ChatFormatting.YELLOW, healer.getDisplayName());
				}
			}
			case "ending" -> ending(level, hall, s);
			default -> {
			}
		}
	}

	/** The Grey Cough comes: three villagers fall ill (never past half the village); the healer is chosen. */
	static void outbreak(ServerLevel level, BlockPos hall, ArcState s, RandomSource random) {
		pickHealer(level, hall, s);
		Villager healer = healer(level, s);
		List<Villager> all = new ArrayList<>(villagers(level, hall));
		int n = Math.min(FIRST_ILL, Math.max(1, all.size() / 2));
		List<Villager> well = new ArrayList<>(all.stream().filter(v -> v != healer && !Sickness.isIll(v)
			&& !io.github.jcondedata.aliveworkplace.legend.Gifted.noIllness(v)).toList());
		net.minecraft.Util.shuffle(well, random);
		List<Component> names = new ArrayList<>();
		for (Villager v : well) {
			if (names.size() >= n) {
				break;
			}
			if (Sickness.greyCough(level, v)) {
				caught(s, v);
				names.add(v.getDisplayName());
			}
		}
		Stories.Data.get(level).setDirty();
		tell(level, hall, "outbreak", ChatFormatting.RED, list(names));
		chronicle(level, hall, "outbreak", list(names));
		ArcState.Role role = s.roles.get(HEALER);
		if (role != null && role.who().isPresent()) {
			tell(level, hall, "see_healer", ChatFormatting.YELLOW, role.name());
		}
	}

	/** The herbs the cure takes, in the order the healer named them (nearest first). */
	public static List<HerbBlock> herbs(ArcState s) {
		List<HerbBlock> out = new ArrayList<>();
		for (ArcState.ArcPlace p : s.places) {
			if (p.key.startsWith(HERB_PLACE) && !p.stages.isEmpty()) {
				HerbBlock herb = Herbs.byId(p.stages.get(0).substring(ArcEffects.HERB_STAGE.length()));
				if (herb != null) {
					out.add(herb);
				}
			}
		}
		return out;
	}

	static String path(HerbBlock herb) {
		return BuiltInRegistries.BLOCK.getKey(herb).getPath();
	}

	@Nullable
	static BlockPos nearestBiome(ServerLevel level, BlockPos from, HerbBlock herb) {
		var tag = Herbs.biomes(herb);
		var found = level.findClosestBiome3d(h -> h.is(tag), from, Places.MAX_BLOCKS, 32, 64);
		return found == null ? null : found.getFirst();
	}

	/**
	 * The healer names the cure's herbs: the three whose biomes are nearest the village (looked up once, within
	 * {@link Places#MAX_BLOCKS}; a herb whose biome isn't found is passed over for the next), a patch of each is to be
	 * planted there, and everyone near gets a map to each.
	 */
	static void herbs(ServerLevel level, BlockPos hall, ArcState s) {
		if (!herbs(s).isEmpty()) {
			return; // (looked up already)
		}
		record Found(HerbBlock herb, BlockPos at, double distance) {
		}
		List<Found> found = new ArrayList<>();
		for (HerbBlock herb : Herbs.all()) {
			BlockPos at = LOCATOR.find(level, hall, herb);
			if (at != null) {
				double dx = at.getX() - hall.getX();
				double dz = at.getZ() - hall.getZ();
				found.add(new Found(herb, at, dx * dx + dz * dz));
			}
		}
		found.sort(Comparator.comparingDouble(Found::distance));
		for (Found f : found.subList(0, Math.min(HERBS, found.size()))) {
			JsonObject json = new JsonObject();
			json.addProperty("herb", BuiltInRegistries.BLOCK.getKey(f.herb()).toString());
			json.addProperty("key", HERB_PLACE + path(f.herb()));
			JsonObject rule = new JsonObject();
			JsonArray offset = new JsonArray();
			offset.add(f.at().getX() - hall.getX());
			offset.add(f.at().getZ() - hall.getZ());
			rule.add("offset", offset);
			json.add("rule", rule);
			ArcEffects.herbPatch(level, hall, s, json);
		}
		List<HerbBlock> herbs = herbs(s);
		Villager healer = healer(level, s);
		Component who = healer != null ? healer.getDisplayName() : roleName(s);
		if (herbs.isEmpty()) {
			// No herb's biome within reach: the healer makes do with what grows at home.
			s.flags.put(HERBS_GIVEN, ArcState.FOREVER);
			tell(level, hall, "no_herbs", ChatFormatting.YELLOW, who);
			chronicle(level, hall, "no_herbs", who);
			return;
		}
		Component names = list(herbs.stream().map(h -> (Component) h.getName()).toList());
		tell(level, hall, "herbs" + howMany(herbs.size()), ChatFormatting.YELLOW, who, names, EACH); // B617: the words say how many
		chronicle(level, hall, "herbs" + howMany(herbs.size()), who, names);
		for (ServerPlayer player : ArcEffects.audience(level, hall)) {
			giveMaps(level, s, player);
		}
		Stories.Data.get(level).setDirty();
	}

	/**
	 * The key suffix for a sentence that says how many far herbs were named (B617): none for three, {@code .two} or
	 * {@code .one} when fewer biomes were within reach.
	 */
	static String howMany(int herbs) {
		return herbs == 1 ? ".one" : herbs == 2 ? ".two" : "";
	}

	static Component roleName(ArcState s) {
		ArcState.Role role = s.roles.get(HEALER);
		return role != null ? role.name() : Component.translatable("entity.aliveworkplace.doctor_wren");
	}

	/** A map to each herb patch, once for each player. */
	static void giveMaps(ServerLevel level, ArcState s, ServerPlayer player) {
		if (s.flags.containsKey(MAPS + player.getUUID())) {
			return;
		}
		s.flags.put(MAPS + player.getUUID(), ArcState.FOREVER);
		for (ArcState.ArcPlace p : s.places) {
			if (p.key.startsWith(HERB_PLACE) && !p.skipped) {
				ItemStack map = Places.map(level, Places.point(p.spot));
				if (!player.getInventory().add(map)) {
					player.drop(map, false);
				}
			}
		}
	}

	// ---- talking to the healer -------------------------------------------------------------------------------------

	/**
	 * A right-click on a Sickness arc's healer: the first tells them of the cough (the herbs are named), then the herbs
	 * are handed in, 3 of each at a time; while brewing they say how far along it is. Sneak to trade or be treated; a Gift, tonic, Remedy or Guild Charter in hand is given instead (B343).
	 */
	public static InteractionResult onUse(Player player, Level world, InteractionHand hand, Entity entity) {
		if (!(world instanceof ServerLevel level) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND
			|| player.isShiftKeyDown() || !(entity instanceof Villager villager) || givenToVillagers(held(player))) {
			return InteractionResult.PASS;
		}
		for (Running r : running(level)) {
			ArcState s = r.state();
			ArcState.Role role = s.roles.get(HEALER);
			if (role == null || role.who().isEmpty() || !role.who().get().equals(villager.getUUID()) || !s.started || stamp(s, COURSE) >= 0) {
				continue;
			}
			villager.playSound(SoundEvents.VILLAGER_YES, 0.8f, 1f);
			switch (s.chapter) {
				case 0 -> {
					says(sp, villager, "healer_told");
					s.flags.put(HEALER_TOLD, ArcState.FOREVER);
					s.last = sp.getUUID();
					Stories.Data.get(level).setDirty();
					Arcs.advance(level, r.hall(), r.entry(), s); // the herbs are named now
					giveMaps(level, s, sp);
				}
				case 1 -> handIn(level, r, sp, villager);
				case 2 -> {
					if (s.flags.containsKey(BREWED)) {
						says(sp, villager, "taking_round", coughers(level, r.hall()).size());
					} else {
						says(sp, villager, "brewing", Math.round(brewed(level, s) * 100));
					}
				}
				default -> {
					return InteractionResult.PASS;
				}
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** What a click on a villager uses: the main hand's item, or with the main hand empty the off hand's (B341). */
	static ItemStack held(Player player) {
		return player.getMainHandItem().isEmpty() ? player.getOffhandItem() : player.getMainHandItem();
	}

	/**
	 * B343: an item a player gives a villager with a right-click (a Gift, a tonic, a Physician's Remedy, a Guild Charter)
	 * is for that use, not for the story: its own listener, registered after this one, takes the click. Anything else
	 * (a sword, a map, an empty hand) still talks to the healer, the herbs coming from the bag.
	 */
	static boolean givenToVillagers(ItemStack held) {
		return held.is(ModItems.GIFT) || held.is(ModItems.GUILD_CHARTER) || held.getItem() instanceof io.github.jcondedata.aliveworkplace.people.TonicItem
			|| io.github.jcondedata.aliveworkplace.people.Tonics.of(held) != null || io.github.jcondedata.aliveworkplace.people.RemedyItem.isRemedy(held);
	}

	/** {@code player} hands the healer what they carry of the herbs still wanted, 3 of each at a time. */
	static void handIn(ServerLevel level, Running r, ServerPlayer player, Villager healer) {
		ArcState s = r.state();
		List<Component> took = new ArrayList<>();
		for (HerbBlock herb : herbs(s)) {
			if (s.flags.containsKey(GIVEN + path(herb)) || player.getInventory().countItem(herb.asItem()) < EACH) {
				continue;
			}
			int left = EACH;
			for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
				ItemStack stack = player.getInventory().getItem(i);
				if (stack.is(herb.asItem())) {
					int take = Math.min(left, stack.getCount());
					stack.shrink(take);
					left -= take;
				}
			}
			s.flags.put(GIVEN + path(herb), ArcState.FOREVER);
			s.helpers.merge(player.getUUID(), 1, Integer::sum); // a gatherer: thanked at the end
			s.chapterHelpers.merge(player.getUUID(), 1, Integer::sum);
			s.last = player.getUUID();
			took.add(herb.getName());
		}
		List<Component> wanted = herbs(s).stream().filter(h -> !s.flags.containsKey(GIVEN + path(h))).map(h -> (Component) h.getName()).toList();
		giveMaps(level, s, player);
		if (took.isEmpty()) {
			says(player, healer, "still_wanted", EACH, list(wanted));
			return;
		}
		Stories.Data.get(level).setDirty();
		chronicle(level, r.hall(), "herbs_given", player.getDisplayName(), list(took));
		if (!wanted.isEmpty()) {
			says(player, healer, "thanks_more", list(took), EACH, list(wanted));
			return;
		}
		says(player, healer, "thanks_all" + howMany(herbs(s).size()));
		s.flags.put(HERBS_GIVEN, ArcState.FOREVER);
		Arcs.advance(level, r.hall(), r.entry(), s); // the brew begins
	}

	// ---- the brew and the cure round -------------------------------------------------------------------------------

	/** How far the brew is along, 0 to 1. */
	public static float brewed(ServerLevel level, ArcState s) {
		long from = stamp(s, BREW_FROM);
		if (from < 0) {
			return 0f;
		}
		return Math.max(0f, Math.min(1f, (level.getDayTime() - from) / (float) BREW));
	}

	/** Where the healer brews: their brewing stand (their workstation), or by the hall for Doctor Wren. */
	static BlockPos brewSpot(ServerLevel level, BlockPos hall, Villager healer) {
		Optional<GlobalPos> site = healer.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension());
		if (site.isPresent() && !Arcs.isArcEntity(healer)) {
			return site.get().pos();
		}
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.offset(2, 0, 2));
	}

	static void walk(Villager v, BlockPos to, int close) {
		v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(to, 0.6f, close));
	}

	/** The brew, then the round: every 40 ticks while the third chapter runs. */
	static void brewTick(ServerLevel level, Running r) {
		ArcState s = r.state();
		Villager healer = healer(level, s);
		if (!s.flags.containsKey(BREWED)) {
			float done = brewed(level, s);
			if (done >= 1f) {
				s.flags.put(BREWED, ArcState.FOREVER);
				Stories.Data.get(level).setDirty();
				Component who = healer != null ? healer.getDisplayName() : roleName(s);
				tell(level, r.hall(), "brewed", ChatFormatting.GREEN, who);
				chronicle(level, r.hall(), "brewed", who);
			} else if (healer != null) {
				BlockPos spot = brewSpot(level, r.hall(), healer);
				if (healer.blockPosition().distSqr(spot) > 9) {
					walk(healer, spot, 2);
				}
				WorkerStatus.set(healer, Component.translatable("message.aliveworkplace.sickness.status.brewing"), done,
					Component.translatable("message.aliveworkplace.sickness.status.brewing_line").withStyle(ChatFormatting.GRAY));
				level.sendParticles(ParticleTypes.EFFECT, spot.getX() + 0.5, spot.getY() + 1.1, spot.getZ() + 0.5, 4, 0.2, 0.2, 0.2, 0.01);
				return;
			} else {
				return;
			}
		}
		round(level, r, healer);
	}

	/** The cure round: the healer walks to each coughing villager in turn and gives them the tonic; when none is left, done. */
	static void round(ServerLevel level, Running r, @Nullable Villager healer) {
		ArcState s = r.state();
		List<Villager> left = coughers(level, r.hall());
		if (left.isEmpty()) {
			if (healer != null) {
				healer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				WALKS.remove(healer.getUUID());
			}
			s.flags.put(CURED, ArcState.FOREVER);
			Stories.Data.get(level).setDirty();
			Arcs.advance(level, r.hall(), r.entry(), s); // the ending
			return;
		}
		if (healer == null) {
			return;
		}
		if (!healer.getMainHandItem().is(ModItems.THREE_HERB_TONIC)) {
			stow(level, healer);
			healer.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.THREE_HERB_TONIC));
			healer.setDropChance(EquipmentSlot.MAINHAND, 0f);
		}
		int ill = Math.max(left.size(), caught(s).size());
		WorkerStatus.set(healer, Component.translatable("message.aliveworkplace.sickness.status.round"), 1f - left.size() / (float) ill,
			Component.translatable("message.aliveworkplace.sickness.status.round_line", left.size()).withStyle(ChatFormatting.GRAY));
		Villager patient = left.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(healer))).orElseThrow();
		long now = level.getDayTime();
		Map.Entry<UUID, Long> walk = WALKS.get(healer.getUUID());
		if (walk == null || !walk.getKey().equals(patient.getUUID())) {
			walk = Map.entry(patient.getUUID(), now);
			WALKS.put(healer.getUUID(), walk);
		}
		if (healer.distanceToSqr(patient) > 2.5 * 2.5) {
			if (now - walk.getValue() < WALK_LIMIT) {
				walk(healer, patient.blockPosition(), 1);
				return;
			}
			healer.teleportTo(patient.getX() + 1, patient.getY(), patient.getZ()); // (a long way round: they step over)
		}
		give(level, healer, patient);
		WALKS.remove(healer.getUUID());
	}

	/**
	 * What the healer holds before taking the tonic in hand (a Physician's Remedy) goes back to the chest by their
	 * workstation, or is dropped at their feet: the round doesn't swallow it.
	 */
	static void stow(ServerLevel level, Villager healer) {
		ItemStack held = healer.getMainHandItem();
		if (held.isEmpty() || held.is(ModItems.THREE_HERB_TONIC)) {
			return;
		}
		healer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		Optional<GlobalPos> site = healer.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.dimension() == level.dimension());
		ItemStack rest = site.isPresent() ? SupplyContainers.insert(level, SupplyContainers.find(level, site.get().pos(), null), held) : held;
		if (!rest.isEmpty()) {
			healer.spawnAtLocation(rest);
		}
	}

	/** The healer gives {@code patient} the tonic: the cough is gone. */
	public static void give(ServerLevel level, Villager healer, Villager patient) {
		healer.swing(InteractionHand.MAIN_HAND);
		healer.getLookControl().setLookAt(patient);
		Sickness.recover(level, patient);
		level.playSound(null, patient.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.NEUTRAL, 0.8f, 1f);
		level.sendParticles(ParticleTypes.HEART, patient.getX(), patient.getY() + patient.getBbHeight() + 0.3, patient.getZ(), 4, 0.3, 0.2, 0.3, 0);
	}

	// ---- the endings -----------------------------------------------------------------------------------------------

	/** Everyone well: friendship from each who was ill for each gatherer, and the chronicle thanks them. */
	static void ending(ServerLevel level, BlockPos hall, ArcState s) {
		for (Villager v : coughers(level, hall)) {
			Sickness.recover(level, v);
		}
		List<Component> gatherers = new ArrayList<>();
		for (UUID who : s.helpers.keySet()) {
			ServerPlayer online = level.getServer().getPlayerList().getPlayer(who);
			if (online != null) {
				gatherers.add(online.getDisplayName());
			}
			for (UUID ill : caught(s)) {
				if (level.getEntity(ill) instanceof Villager v && v.isAlive()) {
					io.github.jcondedata.aliveworkplace.story.Friendship.add(v, who, online != null ? online.getGameProfile().getName() : "", FRIENDSHIP);
				}
			}
		}
		Component names = gatherers.isEmpty() ? Component.translatable("chronicle.aliveworkplace.someone_mid") : list(gatherers); // mid-sentence (B611)
		tell(level, hall, "cured", ChatFormatting.GREEN, caught(s).size());
		chronicle(level, hall, "cured", caught(s).size(), names);
	}

	/** 15 days without the cure: the cough runs its course; moods drop, and everyone is well within 3 days. */
	static void startCourse(ServerLevel level, Running r) {
		ArcState s = r.state();
		stamp(s, COURSE, Chronicle.day(level));
		new Rewards.VillageMood(COURSE_MOOD, COURSE_MOOD_DAYS, Component.translatable("mood.aliveworkplace.reason.grey_cough")).give(level, r.hall(), null);
		Villager healer = healer(level, s);
		if (healer != null) {
			healer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		}
		Stories.Data.get(level).setDirty();
		tell(level, r.hall(), "course", ChatFormatting.RED, COURSE_DAYS);
		chronicle(level, r.hall(), "course");
	}

	/** The day {@code v} is well again once the cough runs its course: 1 to 3 days after it began to. */
	static long wellOn(long courseDay, UUID v) {
		return courseDay + 1 + Math.floorMod(v.hashCode(), COURSE_DAYS);
	}

	static void courseTick(ServerLevel level, Running r) {
		ArcState s = r.state();
		long from = stamp(s, COURSE);
		long today = Chronicle.day(level);
		for (Villager v : coughers(level, r.hall())) {
			if (today >= wellOn(from, v.getUUID())) {
				Sickness.recover(level, v);
			}
		}
		if (today >= from + COURSE_DAYS) {
			for (Villager v : coughers(level, r.hall())) {
				Sickness.recover(level, v);
			}
			chronicle(level, r.hall(), "course_over");
			Arcs.Arc arc = Arcs.get(ARC).orElse(null);
			if (arc != null) {
				Arcs.failed(level, r.hall(), r.entry(), s, arc);
			} else {
				Arcs.end(level, r.hall(), r.entry(), s, null);
			}
		}
	}

	// ---- the 40-tick check -----------------------------------------------------------------------------------------

	/**
	 * Every 40 ticks: the story ends quietly if {@code villagerSickness} was switched off (everyone well); after 15 days
	 * without the cure the cough runs its course; Doctor Wren comes when due; the night's pass-on; the brew and the round.
	 */
	public static void tick(ServerLevel level) {
		for (Running r : running(level)) {
			ArcState s = r.state();
			if (!Sickness.ENABLED) {
				for (Villager v : coughers(level, r.hall())) {
					Sickness.recover(level, v);
				}
				Villager healer = healer(level, s);
				if (healer != null && healer.getMainHandItem().is(ModItems.THREE_HERB_TONIC)) {
					healer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
				}
				Arcs.end(level, r.hall(), r.entry(), s, null);
				continue;
			}
			long today = Chronicle.day(level);
			if (stamp(s, COURSE) >= 0) {
				courseTick(level, r);
				continue;
			}
			if (!s.flags.containsKey(CURED) && today >= s.beganDay + COURSE_AFTER_DAYS) {
				startCourse(level, r);
				continue;
			}
			long due = stamp(s, WREN_DUE);
			if (due >= 0 && today >= due) {
				wrenArrives(level, r.hall(), s);
			}
			Villager healer = healer(level, s);
			if (healer != null && Arcs.isArcEntity(healer)) {
				dress(healer);
			} else if (healer == null && !wren(s) && ArcEffects.near(level, r.hall()) && level.isLoaded(r.hall())) {
				pickHealer(level, r.hall(), s); // the healer is gone: another Nurse or Physician, or Doctor Wren
			}
			if (!s.flags.containsKey(BREWED) && level.getDayTime() % VillageNeeds.DAY >= NIGHT && !s.flag(SPREAD_NIGHT, today)) {
				s.flags.put(SPREAD_NIGHT, today);
				Stories.Data.get(level).setDirty();
				spread(level, r.hall(), s, level.random);
			}
			if (s.chapter == 2 && s.started) {
				brewTick(level, r);
			}
		}
	}

	private SicknessStory() {
	}
}

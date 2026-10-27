package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Lairs (ROADMAP 32.3): the camp a raider culture makes out beyond a village, for every culture whose file has a
 * {@code lair} (the bandits' camp was the first, and {@code guard/BanditCamps} now calls in here). Now and then one of
 * the cultures that fit the village makes camp {@link #NEAR} to {@link #FAR} blocks from its hall, on dry, fairly flat,
 * open ground: its structure, its captain and his band. At most one lair stands by a village; while it stands the
 * village's raids come from it. Kill the captain and the lair is broken up: the band scatters, its chests (the culture's
 * loot table) are yours, the chronicle remembers who did it, and none comes for {@link #REST_DAYS} days. The camp itself
 * then comes down, its chests spilling their loot, or stays as ruins ({@link CampCleanup}, B209).
 *
 * <p>Every lair has:
 * <ul>
 * <li><b>a captain with a name</b> from his culture's list ({@code captain.names}: {@link #NAMES} lines in the lang
 * file, with {@code captain.title} before it: "Chief Harl Ashgrave"), picked when the lair is founded, shown over his
 * head and in every message and chronicle line about the lair;</li>
 * <li><b>a strength</b>: the culture's {@code lair.strength} at first, {@code growth} more each day up to {@code max}.
 * A raid takes its raiders from it (never more than the raid's usual size): they are {@code out} till the raid ends,
 * those alive when it ends rejoin and the dead are gone. The band standing at the lair is the strength at home, at most
 * {@code home_max}; one of them killed at the lair is one less too.</li>
 * </ul>
 *
 * <p>Saved per dimension as {@code aliveworkplace_bandit_camps} (the bandit camps' file, name kept). A camp saved before
 * 32.3 loads as a {@code bandits} lair: its strength the culture's, its captain's name picked from his UUID.
 *
 * <p>While Milestone 32 isn't finished ({@link #live}) a lair is the bandit camp as it was: an unnamed chief, three or
 * four men, no strength.
 */
public final class Lairs {
	/** Whether cultures other than the bandits (who have their own switch, {@code banditCamps}) make camp by chance. */
	public static boolean ENABLED = true;
	/** The tag everyone of a lair carries; the bandits keep theirs as well. */
	public static final String TAG = "aliveworkplace_lair";
	public static final String CAPTAIN_TAG = "aliveworkplace_lair_captain";
	public static final String BANDIT_TAG = "aliveworkplace_bandit";
	public static final String BANDIT_CHIEF_TAG = "aliveworkplace_bandit_chief";
	/** The bandits' camp, the lair of a culture whose file is gone. */
	public static final ResourceLocation BANDIT_CAMP = AliveWorkplace.id("camp/bandit_camp");
	public static final int NEAR = 80;
	public static final int FAR = 104;
	/** Days after a lair is broken up before another comes. */
	public static final int REST_DAYS = 5;
	/** The chance a day that raiders make camp near a village that could have a lair. */
	public static final float DAILY_CHANCE = 0.12f;
	/** How far from the lair its band keeps. */
	public static final int KEEP = 14;
	/** Names in a captain's list: {@code <names>.1} to {@code <names>.20}. */
	public static final int NAMES = 20;
	/** A lair's strength when its culture's file doesn't say (the bandits'). */
	public static final int DEFAULT_STRENGTH = 6;
	/** The captain's extra health, when his culture's file is gone. */
	static final double CAPTAIN_HEALTH = 36;

	/** In the bandit camp's blueprint: the fire, where the chief stands (in his tent) and where his men do. */
	static final BlockPos FIRE = new BlockPos(7, 1, 6);
	static final BlockPos CHIEF_SPOT = new BlockPos(7, 1, 9);
	static final List<BlockPos> BAND_SPOTS = List.of(new BlockPos(4, 1, 7), new BlockPos(10, 1, 7), new BlockPos(7, 1, 3), new BlockPos(6, 1, 4),
		new BlockPos(8, 1, 4), new BlockPos(3, 1, 6), new BlockPos(11, 1, 6), new BlockPos(8, 1, 3));

	/** The lines of the bandits, for a world whose {@code bandits.json} is gone. */
	private static final Culture.Keys BANDIT_MESSAGES = new Culture.Keys(Optional.empty(), Map.of(
		"camp", "message.aliveworkplace.bandits.camp", "broken", "message.aliveworkplace.bandits.broken_up"));
	private static final Culture.Keys BANDIT_CHRONICLE = new Culture.Keys(Optional.empty(), Map.of(
		"camp", "chronicle.aliveworkplace.bandit_camp", "broken", "chronicle.aliveworkplace.bandit_camp_broken",
		"broken_by", "chronicle.aliveworkplace.bandit_camp_broken_by"));

	/**
	 * A lair: where it stands, the village it preys on, its captain, the day it was made, whose it is, its
	 * {@code strength} (raiders out included), the day that last grew, how many are {@code out} on a raid now, how many
	 * the last raid {@code lost}, and which of his culture's names the captain has.
	 */
	public record Lair(BlockPos pos, BlockPos hall, UUID captain, long day, ResourceLocation culture, int strength, long grown, int out, int lost, int name) {
		/** The band at the lair now: the strength less those out raiding. */
		public int home() {
			return Math.max(0, strength - out);
		}

		Lair with(int strength, long grown, int out, int lost) {
			return new Lair(pos, hall, captain, day, culture, Math.max(0, strength), grown, Math.max(0, out), Math.max(0, lost), name);
		}
	}

	/** Told when a lair is broken up ({@code by}: who brought the captain down, if anyone did). */
	@FunctionalInterface
	public interface Broken {
		void broken(ServerLevel level, Lair lair, @Nullable Entity by);
	}

	private static final List<Broken> BROKEN = new CopyOnWriteArrayList<>();

	/** Adds a listener for lairs broken up (story arcs count them, bounties pay for them). */
	public static void onBroken(Broken listener) {
		BROKEN.add(listener);
	}

	/**
	 * Whether lairs are all they are in 1.6: named captains, a strength that raids use up, the Defence page. False while
	 * Milestone 32 isn't finished (open in GameTests and the showcase).
	 */
	public static boolean live() {
		return Expansions.on(Expansions.M32);
	}

	// Reading.

	/** The lair preying on the village round {@code hall}, if any. */
	public static Optional<Lair> near(ServerLevel level, BlockPos hall) {
		return Optional.ofNullable(Data.get(level).lairs.get(hall));
	}

	/** The lair of {@code culture} preying on the village round {@code hall}, if it's that culture's. */
	public static Optional<Lair> of(ServerLevel level, BlockPos hall, ResourceLocation culture) {
		return near(level, hall).filter(lair -> lair.culture().equals(culture));
	}

	/** Every lair in the dimension. */
	public static List<Lair> all(ServerLevel level) {
		return List.copyOf(Data.get(level).lairs.values());
	}

	/** The days left before another lair may come to the village round {@code hall} (0: one may). */
	public static int resting(ServerLevel level, BlockPos hall) {
		Rest rest = Data.get(level).broken.get(hall);
		return rest == null ? 0 : (int) Math.max(0, rest.days() - (Chronicle.day(level) - rest.day()));
	}

	/**
	 * Whether the captain of {@code lair} has a name of his own (his culture lists names, or gives him the one he always
	 * has, and lairs are {@link #live}).
	 */
	public static boolean named(Lair lair) {
		return live() && Threats.get(lair.culture()).flatMap(Culture::captain).map(Culture.Captain::hasName).orElse(false);
	}

	/**
	 * What the captain of {@code lair} is called: his title and name ("Chief Harl Ashgrave") when his culture lists names;
	 * else his title alone, the Bandit Chief for the bandits, a Captain for anyone else.
	 */
	public static Component captainName(Lair lair) {
		Optional<Culture.Captain> captain = Threats.get(lair.culture()).flatMap(Culture::captain);
		Optional<Component> title = captain.flatMap(Culture.Captain::title).map(key -> (Component) Component.translatable(key));
		if (named(lair)) {
			Component name = captain.get().names().<Component>map(names -> Component.translatable(names + "." + (Math.floorMod(lair.name(), NAMES) + 1)))
				.orElseGet(() -> Component.translatable(captain.get().name().get()));
			return title.<Component>map(t -> Component.translatable("threat.aliveworkplace.captain", t, name)).orElse(name);
		}
		if (lair.culture().equals(Threats.BANDITS)) {
			return Component.translatable("entity.aliveworkplace.bandit_chief");
		}
		return title.orElseGet(() -> Component.translatable("entity.aliveworkplace.raider_captain"));
	}

	/** What a culture is called ("Bandits"): the lang key {@code threat.<namespace>.<path>.name}. */
	public static Component cultureName(ResourceLocation culture) {
		return Component.translatable("threat." + culture.getNamespace() + "." + culture.getPath() + ".name");
	}

	/** What a culture's lair is called ("Bandit camp"): the lang key {@code threat.<namespace>.<path>.lair}. */
	public static Component lairName(ResourceLocation culture) {
		return Component.translatable("threat." + culture.getNamespace() + "." + culture.getPath() + ".lair");
	}

	// The hall's round.

	/**
	 * The hall's round. A lair whose captain is gone is broken up; one that stands keeps its band home, grows with the
	 * days and musters the band it should have. With none, and none broken up lately, one of the cultures that fit the
	 * village may make camp ({@code bandits}: whether the bandits may, their own switch).
	 */
	public static void round(ServerLevel level, BlockPos hall, boolean bandits) {
		round(level, hall, bandits, level.random);
	}

	/** As {@link #round(ServerLevel, BlockPos, boolean)}, rolling with {@code random}. */
	public static void round(ServerLevel level, BlockPos hall, boolean bandits, RandomSource random) {
		Data data = Data.get(level);
		CampCleanup.sweep(level, hall); // camps broken up before B209, still standing
		Lair lair = data.lairs.get(hall);
		boolean quiet = Threats.quiet(level, hall);
		if (!quiet) {
			QUIET.remove(quietKey(level, hall));
		}
		if (lair != null) {
			// (a captain who leads the raid under way is at the village, maybe out of loaded ground: his death is told by onDeath)
			boolean leading = leads(level, lair) && ThreatData.get(level).raid(hall).map(r -> r.culture().equals(lair.culture())).orElse(false);
			if (!leading && level.isPositionEntityTicking(lair.pos())) {
				Entity captain = level.getEntity(lair.captain());
				if (!(captain instanceof LivingEntity living) || !living.isAlive()) {
					// A captain who fell was told at once by onDeath, which broke the lair up; one gone without dying while
					// the village is quiet was taken by Peaceful (the game removes monsters), not brought down: the camp,
					// leaderless, packs up and leaves in peace now rather than being counted as broken up (B364).
					if (quiet) {
						leave(level, lair);
					} else {
						breakUp(level, lair, null);
					}
					return;
				}
			}
			if (quiet) {
				// Nothing comes to a quiet village (32.21): a lair standing by it leaves at the next dawn.
				if (Chronicle.day(level) > quietSince(level, hall)) {
					leave(level, lair);
					return;
				}
			} else if (live()) {
				if (lair.out() > 0 && ThreatData.get(level).raid(hall).isEmpty()) {
					data.put(lair.with(lair.strength(), lair.grown(), 0, lair.lost())); // a raid that was never ended: nobody is out any more
				}
				dawn(level, hall, Chronicle.day(level));
			}
			if (level.isPositionEntityTicking(lair.pos())) {
				keepHome(level, data.lairs.get(hall));
			}
			return;
		}
		if (resting(level, hall) > 0 || quiet) {
			return; // (nothing makes camp by a quiet village, 32.21)
		}
		if (io.github.jcondedata.aliveworkplace.story.Stories.villageFlag(level, hall, "no_bandit_camps")) {
			bandits = false; // the Bandit King's fall keeps the bandits off for a while (31.16)
		}
		Optional<ResourceLocation> comer = comer(level, hall, bandits, random);
		if (comer.isEmpty()) {
			return;
		}
		int rounds = (int) Math.max(1, VillageNeeds.DAY / VillageNeeds.CHECK_EVERY);
		if (random.nextFloat() < dailyChance(level, hall) / rounds) {
			settle(level, hall, comer.get(), random);
		}
	}

	/**
	 * The lair of {@code culture} makes camp by the village round {@code hall} now, where its culture's file says (a lair
	 * on the sea anchors on deep water, 32.8; any other makes camp on dry ground); null when there was nowhere to put it.
	 */
	@Nullable
	public static Lair settle(ServerLevel level, BlockPos hall, ResourceLocation culture, RandomSource random) {
		Optional<Culture.Lair> spec = Threats.get(culture).flatMap(Culture::lair);
		StructureTemplate ship = spec.flatMap(Culture.Lair::sea).isPresent() ? level.getStructureManager().get(spec.get().structure()).orElse(null) : null;
		BlockPos site = spec.flatMap(Culture.Lair::sea).isEmpty()
			? site(level, hall, spec.map(Culture.Lair::near).orElse(NEAR), spec.map(Culture.Lair::far).orElse(FAR), random, spec.map(Culture.Lair::wet).orElse(false))
			: ship == null ? null : Ships.site(level, hall, spec.get().sea().get(), ship, random);
		if (site != null && culture.equals(Threats.BANDITS) && io.github.jcondedata.aliveworkplace.realm.RealmEdicts.peaceNear(level, site)) {
			return null; // the Crown's Peace (33.22): no bandit camp near a member
		}
		return site == null ? null : found(level, hall, site, culture);
	}

	/** The day each quiet village (by dimension and hall) was first seen quiet, when it isn't At peace (kept in memory). */
	private static final Map<String, Long> QUIET = new java.util.concurrent.ConcurrentHashMap<>();

	private static String quietKey(ServerLevel level, BlockPos hall) {
		return level.dimension().location() + "|" + hall.asLong();
	}

	/**
	 * The day the village round {@code hall} became quiet: the day its owner set it At peace, else the first day its
	 * round found it quiet (the threat level 0, or Peaceful). A lair by it leaves at the dawn after.
	 */
	static long quietSince(ServerLevel level, BlockPos hall) {
		if (level.getBlockEntity(hall) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity e && e.atPeace() && e.peaceSince() >= 0) {
			return e.peaceSince();
		}
		return QUIET.computeIfAbsent(quietKey(level, hall), k -> Chronicle.day(level));
	}

	/**
	 * The lair leaves the village in peace (32.21; an operator's {@code /workplace threat clear}): its band and captain go,
	 * its portal goes out, its ship sails off, and the village is told. Unlike a lair broken up it leaves no rest, and
	 * no one is credited with it.
	 */
	public static void leave(ServerLevel level, Lair lair) {
		Data data = Data.get(level);
		data.lairs.remove(lair.hall());
		data.setDirty();
		forgetEverywhere(level, lair);
		CampCleanup.broken(level, lair); // the camp is packed up (B209)
		Piglins.putOut(level, lair);
		List<Mob> going = new ArrayList<>(band(level, lair));
		if (level.getEntity(lair.captain()) instanceof Mob captain && !going.contains(captain)) {
			going.add(captain);
		}
		for (Mob mob : going) {
			level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			mob.discard();
		}
		if (Threats.get(lair.culture()).flatMap(Culture::lair).flatMap(Culture.Lair::sea).isPresent()) {
			Ships.sailAway(level, lair.hall());
		}
		if (Covens.hexes(lair.culture())) {
			Covens.lift(level, lair.hall()); // the hex lifts with the coven gone
		}
		Component name = VillageHalls.name(level, lair.hall());
		for (ServerPlayer player : players(level, lair.hall())) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.lair.left", lairName(lair.culture()), name).withStyle(ChatFormatting.GREEN));
		}
		Chronicle.record(level, lair.hall(), Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.lair.left", lairName(lair.culture())));
		AliveWorkplace.LOG.info("{} left the village hall at {} in peace", lair.culture(), lair.hall());
	}

	/**
	 * Whose lair would come to the village round {@code hall}: one of the cultures with a lair that are switched on and
	 * whose {@code where} fits, by weight ({@code bandits}: whether the bandits are among them; the others while
	 * {@link #ENABLED}). Without their file the bandits still come to a village of Village rank or more, as they did.
	 */
	public static Optional<ResourceLocation> comer(ServerLevel level, BlockPos hall, boolean bandits, RandomSource random) {
		List<Culture> fitting = new ArrayList<>();
		for (Culture culture : Threats.all()) {
			// (while Milestone 32 isn't finished a lair is the bandit camp, 32.3's gate: no other culture makes camp by chance)
			boolean on = culture.id().equals(Threats.BANDITS) ? bandits : ENABLED && live();
			if (on && culture.needsLair() && Threats.fits(level, hall, culture, () -> VillageHalls.census(level, hall).villagers())) {
				fitting.add(culture);
			}
		}
		Optional<ResourceLocation> picked = Threats.byWeight(fitting, random).map(Culture::id);
		if (picked.isEmpty() && bandits && Threats.get(Threats.BANDITS).isEmpty() && Threats.enabled(Threats.BANDITS)
			&& VillageRanks.of(level, hall).ordinal() >= VillageRanks.Rank.VILLAGE.ordinal()) {
			return Optional.of(Threats.BANDITS);
		}
		return picked;
	}

	/**
	 * The chance a day that raiders make camp near the village round {@code hall}: {@link #DAILY_CHANCE}, times the
	 * village's {@code bandit_camps} effects (Open Gates, 30.7: twice; reformed by The Watchful Gate: as usual).
	 */
	public static float dailyChance(ServerLevel level, BlockPos hall) {
		io.github.jcondedata.aliveworkplace.hall.CivicEffects.Sum effects = io.github.jcondedata.aliveworkplace.hall.CivicEffects.of(level, hall);
		return Math.min(1f, DAILY_CHANCE * effects.banditCamps() * effects.raids() * Threats.scale(level, hall)); // the threat level and At peace (32.21)
	}

	/**
	 * A new day at the lair by the village round {@code hall}: its strength grows by its culture's {@code growth} for each
	 * day since it last grew, up to {@code max} (the hall's round calls this with today).
	 */
	public static void dawn(ServerLevel level, BlockPos hall, long day) {
		Data data = Data.get(level);
		Lair lair = data.lairs.get(hall);
		if (lair == null || day <= lair.grown()) {
			return;
		}
		Culture.Lair spec = Threats.get(lair.culture()).flatMap(Culture::lair).orElse(null);
		int strength = lair.strength();
		if (spec != null && spec.growth() > 0 && strength < most(spec)) {
			strength = (int) Math.min(most(spec), strength + spec.growth() * (day - lair.grown()));
		}
		data.put(lair.with(strength, day, lair.out(), lair.lost()));
	}

	/**
	 * The most a lair of {@code spec} grows to: its culture's {@code max}, a quarter more when the threat level is hard
	 * (32.21, B383: the band is bigger, so its raids are too).
	 */
	public static int most(Culture.Lair spec) {
		return Threats.more(spec.max());
	}

	// Raids.

	/**
	 * How many of a raid of {@code wanted} the lair can send: all of them while lairs aren't {@link #live}, else no more
	 * than the band it has at home.
	 */
	public static int raidSize(Lair lair, int wanted) {
		return live() ? Math.min(wanted, lair.home()) : wanted;
	}

	/** {@code raiders} of the lair by the village round {@code hall} have left on a raid: they're out, and gone from the lair. */
	public static void sent(ServerLevel level, BlockPos hall, int raiders) {
		Data data = Data.get(level);
		Lair lair = data.lairs.get(hall);
		if (lair == null || !live() || raiders <= 0) {
			return;
		}
		Lair now = lair.with(lair.strength(), lair.grown(), Math.min(lair.strength(), lair.out() + raiders), lair.lost());
		data.put(now);
		if (level.isPositionEntityTicking(now.pos())) {
			muster(level, now);
		}
	}

	/**
	 * The raid from the lair of {@code culture} by the village round {@code hall} is over and {@code survivors} of its
	 * raiders lived: they rejoin, the dead are gone.
	 */
	public static void back(ServerLevel level, BlockPos hall, ResourceLocation culture, int survivors) {
		Data data = Data.get(level);
		Lair lair = data.lairs.get(hall);
		if (lair == null || !live() || !lair.culture().equals(culture) || lair.out() <= 0) {
			return;
		}
		int alive = Math.max(0, Math.min(lair.out(), survivors));
		Lair now = lair.with(lair.strength() - lair.out() + alive, lair.grown(), 0, lair.out() - alive);
		data.put(now);
		if (level.isPositionEntityTicking(now.pos())) {
			muster(level, now);
		}
	}

	/** Sets the strength of the lair by the village round {@code hall} (commands and tests). */
	public static void setStrength(ServerLevel level, BlockPos hall, int strength) {
		Data data = Data.get(level);
		Lair lair = data.lairs.get(hall);
		if (lair != null) {
			data.put(lair.with(strength, lair.grown(), Math.min(lair.out(), Math.max(0, strength)), lair.lost()));
		}
	}

	// The captain at the head of a raid (32.7).

	/**
	 * Whether the captain of {@code lair} leads its raids himself: his culture's {@code captain.leads} rank is reached
	 * by the village it preys on (the Warlord at a City), and lairs are {@link #live}.
	 */
	public static boolean leads(ServerLevel level, Lair lair) {
		return live() && Threats.get(lair.culture()).flatMap(Culture::captain).map(c -> c.leadsAt(VillageRanks.of(level, lair.hall()))).orElse(false);
	}

	/**
	 * The captain of {@code lair} goes with the raid, from {@code at}: he leaves his lair's ground and is one of the
	 * raiders (tagged as they are) till the raid ends. Null if he isn't about (his lair's ground isn't loaded).
	 */
	@Nullable
	public static Mob lead(ServerLevel level, Lair lair, BlockPos at) {
		if (!(level.getEntity(lair.captain()) instanceof Mob captain) || !captain.isAlive()) {
			return null;
		}
		if (captain instanceof PathfinderMob pathfinder) {
			pathfinder.clearRestriction();
		}
		captain.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		captain.addTag(Threats.RAIDER_TAG);
		captain.addTag(Culture.Role.MELEE.tag());
		return captain;
	}

	/** Whether {@code mob} is a lair's captain out leading a raid. */
	public static boolean leading(Mob mob) {
		return mob.getTags().contains(CAPTAIN_TAG) && mob.getTags().contains(Threats.RAIDER_TAG);
	}

	/**
	 * The raid {@code captain} led is over and he lived: he goes back to his lair (when it still stands; else he is
	 * gone, as the others at dawn).
	 */
	public static void comeHome(ServerLevel level, Mob captain) {
		captain.removeTag(Threats.RAIDER_TAG);
		captain.removeTag(Culture.Role.MELEE.tag());
		captain.setTarget(null);
		for (Lair lair : Data.get(level).lairs.values()) {
			if (lair.captain().equals(captain.getUUID())) {
				BlockPos at = level.isLoaded(lair.pos()) ? standNear(level, lair.pos(), 0) : lair.pos().above();
				captain.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
				if (captain instanceof PathfinderMob pathfinder) {
					pathfinder.restrictTo(lair.pos(), KEEP);
				}
				return;
			}
		}
		captain.discard();
	}

	// Founding.

	/** Somewhere out beyond the village for a lair: loaded, dry, fairly flat and open; null if there's nowhere. */
	@Nullable
	public static BlockPos site(ServerLevel level, BlockPos hall, int near, int far, RandomSource random) {
		return site(level, hall, near, far, random, false);
	}

	/** As {@link #site(ServerLevel, BlockPos, int, int, RandomSource)}; {@code wet}: water may lie there (32.11, a swamp's Coven Hut). */
	@Nullable
	public static BlockPos site(ServerLevel level, BlockPos hall, int near, int far, RandomSource random, boolean wet) {
		for (int tries = 0; tries < 12; tries++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int distance = near + random.nextInt(far - near + 1);
			BlockPos column = hall.offset((int) (Math.cos(angle) * distance), 0, (int) (Math.sin(angle) * distance));
			if (!level.isLoaded(column) || !level.isLoaded(column.offset(16, 0, 16)) || !level.isLoaded(column.offset(-16, 0, -16))) {
				continue;
			}
			BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
			if (Math.abs(ground.getY() - hall.getY()) > 32 || !fits(level, ground, wet)) {
				continue;
			}
			if (!VillageHalls.nearest(level, ground).map(other -> other.distSqr(ground) > (double) near * near / 2).orElse(true)) {
				continue; // too close to another village
			}
			return ground;
		}
		return null;
	}

	/** The ground round {@code ground} is flat enough, dry and open for a camp. */
	static boolean fits(ServerLevel level, BlockPos ground) {
		return fits(level, ground, false);
	}

	/** As {@link #fits(ServerLevel, BlockPos)}; {@code wet}: shallow water is no bar (a swamp's pools). */
	static boolean fits(ServerLevel level, BlockPos ground, boolean wet) {
		return fits(level, ground, wet, 7, 6);
	}

	/**
	 * As {@link #fits(ServerLevel, BlockPos, boolean)} over a footprint {@code halfX} and {@code halfZ} blocks each way
	 * from {@code ground} (a camp's is 7 by 6; the Bandit King's fort, 31.15, is wider): no more uneven or blocked
	 * columns than a camp allows for its size.
	 */
	public static boolean fits(ServerLevel level, BlockPos ground, boolean wet, int halfX, int halfZ) {
		int allowed = (2 * halfX + 1) * (2 * halfZ + 1) * 20 / 195; // a camp: 20 of its 195 columns
		int uneven = 0;
		int blocked = 0;
		for (int dx = -halfX; dx <= halfX; dx++) {
			for (int dz = -halfZ; dz <= halfZ; dz++) {
				BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground.offset(dx, 0, dz)).below();
				if (wet ? !level.getFluidState(top.below(2)).isEmpty() : !level.getFluidState(top).isEmpty() || !level.getFluidState(top.above()).isEmpty()) {
					return false; // (wet: no deeper than two blocks)
				}
				if (Math.abs(top.getY() - ground.getY()) > 1) {
					uneven++;
				}
				if (!level.getBlockState(ground.offset(dx, 1, dz)).canBeReplaced() || !level.getBlockState(ground.offset(dx, 2, dz)).canBeReplaced()) {
					blocked++;
				}
			}
		}
		return uneven <= allowed && blocked <= allowed;
	}

	/**
	 * Sets the lair of {@code culture} down with its middle at {@code ground} (a ship: at anchor on the sea in that column), its captain and his band in it, for the
	 * village round {@code hall} (in place of any lair it had); null if the lair's structure is missing or its captain
	 * can't be made.
	 */
	@Nullable
	public static Lair found(ServerLevel level, BlockPos hall, BlockPos ground, ResourceLocation culture) {
		Optional<Culture> file = Threats.get(culture);
		Optional<Culture.Lair> spec = file.flatMap(Culture::lair);
		ResourceLocation structure = spec.map(Culture.Lair::structure).orElse(BANDIT_CAMP);
		StructureTemplate template = level.getStructureManager().get(structure).orElse(null);
		if (template == null) {
			return null;
		}
		boolean camp = structure.equals(BANDIT_CAMP);
		Optional<Culture.Sea> sea = spec.flatMap(Culture.Lair::sea);
		Rotation rotation;
		BlockPos origin;
		if (sea.isPresent()) {
			// A ship (32.8): at anchor on the sea, its bow to the village; the sea round its hull stays.
			Ships.Placed ship = Ships.place(level, hall, ground, structure, template, sea.get());
			rotation = ship.rotation();
			origin = ship.origin();
		} else {
			rotation = Rotation.getRandom(level.random);
			var size = template.getSize(rotation);
			origin = template.getZeroPositionWithTransform(ground.offset(-size.getX() / 2, 0, -size.getZ() / 2), Mirror.NONE, rotation);
			// (getZeroPositionWithTransform turns the corner so the rotated lair covers the same box)
		}
		StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
		BoundingBox box = template.getBoundingBox(settings, origin);
		CampGround land = null;
		if (sea.isEmpty()) {
			land = CampGround.take(level, box, rotation.ordinal()); // the land as it was, for when the camp goes (B225)
			clearAndPlace(level, template, origin, settings, box);
		}
		Optional<BlockPos> captainSpot = spec.flatMap(Culture.Lair::captainAt);
		// Its chests hold the culture's own loot.
		file.flatMap(Culture::loot).ifPresent(loot -> {
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity chest && chest.getLootTable() != null) {
					chest.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, loot), level.random.nextLong());
				}
			}
		});
		// The middle: the camp's fire; of any other lair, the middle of its floor (one whose captain has his spot: of the
		// floor he stands on, a ship's deck).
		BlockPos middle = origin.offset(StructureTemplate.calculateRelativePosition(settings,
			camp ? FIRE : new BlockPos(template.getSize().getX() / 2, captainSpot.map(BlockPos::getY).orElse(1), template.getSize().getZ() / 2)));
		// A quarter more when the threat level is hard (32.21): a raid comes from the band, so the band is bigger (B383).
		int strength = Threats.more(spec.map(Culture.Lair::strength).orElse(DEFAULT_STRENGTH));
		Lair lair = new Lair(middle, hall.immutable(), new UUID(0, 0), Chronicle.day(level), culture, strength, Chronicle.day(level), 0, 0,
			level.random.nextInt(NAMES));
		BlockPos captainAt = camp ? standOn(level, origin.offset(StructureTemplate.calculateRelativePosition(settings, CHIEF_SPOT)))
			: captainSpot.map(spot -> origin.offset(StructureTemplate.calculateRelativePosition(settings, spot))).orElseGet(() -> standNear(level, middle, 0));
		Mob captain = spawn(level, captainAt, lair, true, 0);
		if (captain == null) {
			// No camp without its captain: one recorded without him was "broken up" at the next round, by nobody (B208).
			AliveWorkplace.LOG.warn("{} made no camp near the village hall at {}: its captain could not spawn", culture, hall);
			return null;
		}
		lair = new Lair(lair.pos(), lair.hall(), captain.getUUID(), lair.day(), culture, strength, lair.grown(), 0, 0, lair.name());
		int band = live() ? Math.min(strength, spec.map(Culture.Lair::homeMax).orElse(8)) : 3 + level.random.nextInt(2);
		for (int i = 0; i < band; i++) {
			BlockPos at = camp && i < BAND_SPOTS.size() ? standOn(level, origin.offset(StructureTemplate.calculateRelativePosition(settings, BAND_SPOTS.get(i))))
				: standNear(level, middle, i + 1);
			spawn(level, at, lair, false, i);
		}
		Data data = Data.get(level);
		data.put(lair);
		if (land != null) {
			data.grounds.put(lair.pos(), land);
		}
		boolean named = named(lair);
		Component captainName = captainName(lair);
		Component where = VillageHallScreen.where(hall, middle);
		for (ServerPlayer player : players(level, hall)) {
			Chat.chat(player, message(lair, "camp", named, VillageHalls.name(level, hall), where, captainName).withStyle(ChatFormatting.RED));
		}
		Chronicle.record(level, hall, Chronicle.Kind.RAID, chronicle(lair, "camp", named, where, captainName));
		AliveWorkplace.LOG.info("{} made camp at {} near the village hall at {}", culture, middle, hall);
		return lair;
	}

	/** Clears the ground of a lair on land (the grass, a bush), fills under it so nothing floats, and sets it down. */
	public static void clearAndPlace(ServerLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings, BoundingBox box) {
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY() + 1, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!level.getBlockState(p).isAir()) {
				level.removeBlock(p, false); // the grass, a bush, a sapling
			}
		}
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				for (int y = box.minY(); y > box.minY() - 4; y--) { // no floating ground
					BlockPos p = new BlockPos(x, y, z);
					if (!level.getBlockState(p).canBeReplaced()) {
						break;
					}
					level.setBlock(p, Blocks.DIRT.defaultBlockState(), 2);
				}
			}
		}
		template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
	}

	/**
	 * One of the lair's band (the {@code index}-th of its culture's roster, in turn), or its captain (his culture's mob,
	 * gear and extra health; a vindicator in iron when the file is gone), staying round the lair.
	 */
	@Nullable
	static Mob spawn(ServerLevel level, BlockPos at, Lair lair, boolean captain, int index) {
		Optional<Culture> culture = Threats.get(lair.culture());
		Optional<Culture.Captain> chief = captain ? culture.flatMap(Culture::captain) : Optional.empty();
		Culture.Member member = captain || culture.isEmpty() ? null : culture.get().roster().get(index % culture.get().roster().size());
		Mob mob = chief.isPresent() ? Threats.create(level, chief.get().entity())
			: member != null ? Threats.create(level, member)
			: (captain || index % 2 == 0 ? EntityType.VINDICATOR : EntityType.PILLAGER).create(level);
		if (mob == null) {
			return null;
		}
		mob.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		mob.setPersistenceRequired();
		mob.addTag(TAG);
		Piglins.keep(mob); // a lair's piglins never zombify (32.10)
		boolean bandits = lair.culture().equals(Threats.BANDITS);
		if (bandits) {
			mob.addTag(BANDIT_TAG);
		}
		if (mob instanceof Raider raider) {
			raider.setCanJoinRaid(false);
		}
		if (captain) {
			mob.addTag(CAPTAIN_TAG);
			if (bandits) {
				mob.addTag(BANDIT_CHIEF_TAG);
			}
			mob.setCustomName(captainName(lair));
			mob.setCustomNameVisible(named(lair));
			if (chief.isPresent()) {
				Threats.equip(level, mob, chief.get().gear());
				Risings.mark(mob, chief.get()); // the Pharaoh raises the fallen (32.9)
			} else {
				mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
				mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
				mob.setDropChance(EquipmentSlot.HEAD, 0f);
				mob.setDropChance(EquipmentSlot.CHEST, 0f);
			}
			// (the modifier keeps the bandit chief's id: a chief saved before 32.3 has it)
			Threats.captainHealth(mob, AliveWorkplace.id("bandit_chief"), chief.map(Culture.Captain::health).orElse(CAPTAIN_HEALTH));
		} else {
			Optional<String> name = culture.flatMap(Culture::name);
			if (name.isPresent()) {
				mob.setCustomName(Component.translatable(name.get()));
			} else if (bandits) {
				mob.setCustomName(Component.translatable("entity.aliveworkplace.bandit"));
			}
			if (member != null) {
				Threats.equip(level, mob, member.gear());
			}
		}
		if (mob instanceof PathfinderMob pathfinder) {
			pathfinder.restrictTo(lair.pos(), KEEP);
		}
		if (!HostileSpawns.add(level, mob, lair.culture() + " lair by the village hall at " + lair.hall().toShortString())) {
			return null; // (said in the log: B208)
		}
		return mob;
	}

	// The band.

	/** The band keeps to its lair (a chunk reload forgets it), the captain carries his name, and the band is mustered. */
	static void keepHome(ServerLevel level, Lair lair) {
		for (Mob mob : band(level, lair)) {
			if (mob instanceof PathfinderMob pathfinder && !pathfinder.hasRestriction()) {
				pathfinder.restrictTo(lair.pos(), KEEP);
			}
		}
		if (live()) {
			if (named(lair) && level.getEntity(lair.captain()) instanceof Mob captain && !captainName(lair).equals(captain.getCustomName())) {
				captain.setCustomName(captainName(lair)); // a chief from before 32.3 takes his name
				captain.setCustomNameVisible(true);
			}
			muster(level, lair);
		}
	}

	/**
	 * Makes the band standing at the lair what it should be: its strength at home, at most its culture's {@code home_max}.
	 * Those missing (grown, or back from a raid) turn up by the fire; those too many (out on a raid) are gone from it.
	 */
	static void muster(ServerLevel level, Lair lair) {
		int most = Threats.get(lair.culture()).flatMap(Culture::lair).map(Culture.Lair::homeMax).orElse(8);
		int want = Math.min(lair.home(), most);
		List<Mob> men = new ArrayList<>(band(level, lair));
		men.removeIf(m -> m.getUUID().equals(lair.captain()));
		for (int i = men.size(); i < want; i++) {
			spawn(level, standNear(level, lair.pos(), i + 1), lair, false, i);
		}
		for (int i = men.size() - 1; i >= want; i--) {
			men.get(i).discard();
		}
	}

	/**
	 * A war party at the lair (32.13): the band turns out to meet it, {@code wave} at a time from the tents until all its
	 * strength is spent (each one killed at the lair is one less of it), and goes for the nearest of {@code foes}. At a
	 * City the captain leaves his fire and fights at the front. Returns how many turned out now.
	 */
	public static int turnOut(ServerLevel level, Lair lair, int wave, List<? extends LivingEntity> foes) {
		List<Mob> men = new ArrayList<>(band(level, lair));
		Mob captain = null;
		for (Mob m : men) {
			if (m.getUUID().equals(lair.captain())) {
				captain = m;
			}
		}
		if (captain != null) {
			men.remove(captain);
		}
		int want = Math.min(wave, lair.home());
		int spawned = 0;
		for (int i = men.size(); i < want; i++) {
			Mob mob = spawn(level, standNear(level, lair.pos(), i + 1), lair, false, i);
			if (mob != null) {
				men.add(mob);
				spawned++;
			}
		}
		for (Mob m : men) {
			if ((m.getTarget() == null || !m.getTarget().isAlive()) && !foes.isEmpty()) {
				m.setTarget(nearestOf(m, foes));
			}
		}
		if (captain != null && !foes.isEmpty()
				&& io.github.jcondedata.aliveworkplace.hall.VillageRanks.of(level, lair.hall()) == io.github.jcondedata.aliveworkplace.hall.VillageRanks.Rank.CITY) {
			if (captain instanceof PathfinderMob pathfinder) {
				pathfinder.clearRestriction();
			}
			if (captain.getTarget() == null || !captain.getTarget().isAlive()) {
				captain.setTarget(nearestOf(captain, foes));
			}
		}
		return spawned;
	}

	private static LivingEntity nearestOf(Mob mob, List<? extends LivingEntity> foes) {
		LivingEntity best = null;
		for (LivingEntity foe : foes) {
			if (best == null || mob.distanceToSqr(foe) < mob.distanceToSqr(best)) {
				best = foe;
			}
		}
		return best;
	}

	/** Sets the days none comes to the village round {@code hall}, counted from today (a war party's 10, 32.13). */
	public static void rest(ServerLevel level, BlockPos hall, int days) {
		Data data = Data.get(level);
		data.broken.put(hall.immutable(), new Rest(Chronicle.day(level), days));
		data.setDirty();
	}

	/** The lair's band about now, its captain among them (not those out on a raid). */
	public static List<Mob> band(ServerLevel level, Lair lair) {
		boolean bandits = lair.culture().equals(Threats.BANDITS);
		return level.getEntitiesOfClass(Mob.class, new AABB(lair.pos()).inflate(48, 24, 48),
			m -> m.isAlive() && (m.getTags().contains(TAG) || bandits && m.getTags().contains(BANDIT_TAG)) && !m.getTags().contains(Threats.RAIDER_TAG));
	}

	/** Every death: the captain falling breaks up his lair; one of the band killed at the lair is one less of its strength. */
	public static void onDeath(ServerLevel level, LivingEntity entity, DamageSource source) {
		boolean captain = entity.getTags().contains(CAPTAIN_TAG) || entity.getTags().contains(BANDIT_CHIEF_TAG);
		if (!captain && !entity.getTags().contains(TAG) && !entity.getTags().contains(BANDIT_TAG)) {
			return;
		}
		Data data = Data.get(level);
		for (Lair lair : List.copyOf(data.lairs.values())) {
			if (lair.captain().equals(entity.getUUID())) {
				breakUp(level, lair, source.getEntity());
				return;
			}
		}
		if (captain || !live() || entity.getTags().contains(Threats.RAIDER_TAG)) {
			return; // (a raider's death is counted when the raid ends)
		}
		Lair nearest = null;
		for (Lair lair : data.lairs.values()) {
			double far = lair.pos().distSqr(entity.blockPosition());
			if (far <= 48 * 48 && (nearest == null || far < nearest.pos().distSqr(entity.blockPosition()))) {
				nearest = lair;
			}
		}
		if (nearest != null && nearest.home() > 0) {
			data.put(nearest.with(nearest.strength() - 1, nearest.grown(), nearest.out(), nearest.lost()));
		}
	}

	/** What the village knew of a lair that is gone: its scouts' report of where it stood (B209). */
	private static void forgetEverywhere(ServerLevel level, Lair lair) {
		io.github.jcondedata.aliveworkplace.guard.Scouts.forgetLair(level, lair.hall());
	}

	/** The lair is broken up: the band scatters, the village is told, the chronicle remembers who did it. */
	static void breakUp(ServerLevel level, Lair lair, @Nullable Entity by) {
		Data data = Data.get(level);
		data.lairs.remove(lair.hall());
		data.broken.put(lair.hall(), new Rest(Chronicle.day(level), REST_DAYS));
		data.setDirty();
		forgetEverywhere(level, lair);
		CampCleanup.broken(level, lair); // its camp comes down, or stays as ruins (B209)
		// A lair with a portal: it goes out; the Warlord's band left in the Overworld turns zombified (32.10).
		Piglins.fallen(level, lair);
		for (Mob mob : band(level, lair)) {
			level.sendParticles(ParticleTypes.POOF, mob.getX(), mob.getY() + 0.5, mob.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
			mob.discard();
		}
		// A ship burns and sinks, its chest left on a raft (32.8).
		if (Threats.get(lair.culture()).flatMap(Culture::lair).flatMap(Culture.Lair::sea).map(Culture.Sea::sinks).orElse(false)) {
			Ships.sink(level, lair.hall());
		}
		boolean named = named(lair);
		Component captainName = captainName(lair);
		Component who = by instanceof ServerPlayer || by instanceof net.minecraft.world.entity.npc.Villager ? by.getDisplayName() : null;
		Component name = VillageHalls.name(level, lair.hall());
		for (ServerPlayer player : players(level, lair.hall())) {
			Chat.chat(player, message(lair, "broken", named, name, captainName).withStyle(ChatFormatting.GREEN));
		}
		Chronicle.record(level, lair.hall(), Chronicle.Kind.RAID, who != null
			? chronicle(lair, "broken_by", named, who, captainName)
			: chronicle(lair, "broken", named, captainName));
		level.playSound(null, lair.hall(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1f);
		BROKEN.forEach(listener -> listener.broken(level, lair, by));
	}

	// Words.

	/**
	 * A chat line about {@code lair}: its culture's {@code messages} key for {@code event} ({@code <event>_named} when
	 * the captain has a name, which is then the last of {@code args}), or the plain line every lair has.
	 */
	public static net.minecraft.network.chat.MutableComponent message(Lair lair, String event, boolean named, Object... args) {
		Culture.Keys keys = Threats.get(lair.culture()).map(Culture::messages).orElse(lair.culture().equals(Threats.BANDITS) ? BANDIT_MESSAGES : Culture.Keys.NONE);
		String which = named ? event + "_named" : event;
		return Component.translatable(keys.key(which, "message.aliveworkplace.lair." + which), args);
	}

	/** A chronicle line about {@code lair}, as {@link #message}, from its culture's {@code chronicle} keys. */
	public static net.minecraft.network.chat.MutableComponent chronicle(Lair lair, String event, boolean named, Object... args) {
		Culture.Keys keys = Threats.get(lair.culture()).map(Culture::chronicle).orElse(lair.culture().equals(Threats.BANDITS) ? BANDIT_CHRONICLE : Culture.Keys.NONE);
		String which = named ? event + "_named" : event;
		return Component.translatable(keys.key(which, "chronicle.aliveworkplace.lair." + which), args);
	}

	private static List<ServerPlayer> players(ServerLevel level, BlockPos hall) {
		double r = VillageHalls.RADIUS + FAR;
		return level.getPlayers(p -> p.blockPosition().distSqr(hall) <= r * r);
	}

	private static BlockPos standOn(ServerLevel level, BlockPos at) {
		BlockPos p = at;
		for (int i = 0; i < 4 && !level.getBlockState(p).canBeReplaced(); i++) {
			p = p.above();
		}
		return p;
	}

	/** Open ground a few blocks from the lair's middle ({@code index} picks the side, so a band doesn't stand in a heap). */
	private static BlockPos standNear(ServerLevel level, BlockPos middle, int index) {
		for (int tries = 0; tries < 16; tries++) {
			double angle = (index + tries) * 2.399963; // the golden angle: each next one well away from the last
			int out = 2 + (index + tries) % 3;
			BlockPos column = middle.offset((int) Math.round(Math.cos(angle) * out), 0, (int) Math.round(Math.sin(angle) * out));
			for (int dy = 2; dy >= -2; dy--) {
				BlockPos p = column.above(dy);
				if (level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
					&& level.getFluidState(p).isEmpty() && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
					&& !level.getBlockState(p.below()).is(Blocks.CAMPFIRE)) {
					return p;
				}
			}
		}
		return standOn(level, middle.above());
	}

	/** Puts {@code lair} in the dimension's list as it is, with no camp built (tests: a lair far off). */
	public static void putForTest(ServerLevel level, Lair lair) {
		Data.get(level).put(lair);
	}

	/** Forgets every lair and every rest (tests). */
	public static void forget(ServerLevel level) {
		Data data = Data.get(level);
		data.lairs.clear();
		data.broken.clear();
		data.clearing.clear();
		data.unswept.clear();
		data.grounds.clear();
		data.setDirty();
		QUIET.clear();
		Ships.forget(level);
	}

	/** A village's rest after a lair by it was broken up: the day it was, and how many days none comes. */
	public record Rest(long day, int days) {
	}

	/** The lairs of one dimension, and when each village last broke one up. Saved under the bandit camps' old name. */
	public static final class Data extends SavedData {
		public static final String NAME = "aliveworkplace_bandit_camps";
		final Map<BlockPos, Lair> lairs = new LinkedHashMap<>();
		final Map<BlockPos, Rest> broken = new LinkedHashMap<>();
		/** The broken camps coming down (B209). */
		final List<CampCleanup.Clearing> clearing = new ArrayList<>();
		/** The villages whose camps broken up before B209 may still stand, not yet swept for them ({@link CampCleanup#sweep}). */
		final java.util.Set<BlockPos> unswept = new java.util.LinkedHashSet<>();
		/** The ground each standing lair on land covered before it was set down, by its middle (B225; none for older lairs). */
		final Map<BlockPos, CampGround> grounds = new LinkedHashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		void put(Lair lair) {
			lairs.put(lair.hall(), lair);
			setDirty();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for (Lair lair : lairs.values()) {
				CompoundTag c = new CompoundTag();
				c.putLong("pos", lair.pos().asLong());
				c.putLong("hall", lair.hall().asLong());
				Nbt.putUuid(c, "chief", lair.captain());
				c.putLong("day", lair.day());
				c.putString("culture", lair.culture().toString());
				c.putInt("strength", lair.strength());
				c.putLong("grown", lair.grown());
				c.putInt("out", lair.out());
				c.putInt("lost", lair.lost());
				c.putInt("name", lair.name());
				list.add(c);
			}
			tag.put("camps", list);
			ListTag cleared = new ListTag();
			for (Map.Entry<BlockPos, Rest> e : broken.entrySet()) {
				CompoundTag c = new CompoundTag();
				c.putLong("hall", e.getKey().asLong());
				c.putLong("day", e.getValue().day());
				c.putInt("rest", e.getValue().days());
				cleared.add(c);
			}
			tag.put("broken_up", cleared);
			ListTag going = new ListTag();
			for (CampCleanup.Clearing c : clearing) {
				going.add(c.write());
			}
			tag.put("clearing", going);
			ListTag swept = new ListTag();
			for (BlockPos hall : unswept) {
				CompoundTag c = new CompoundTag();
				c.putLong("hall", hall.asLong());
				swept.add(c);
			}
			tag.put("unswept", swept);
			ListTag lands = new ListTag();
			for (Map.Entry<BlockPos, CampGround> e : grounds.entrySet()) {
				if (lairs.values().stream().noneMatch(lair -> lair.pos().equals(e.getKey()))) {
					continue; // (its lair is gone some other way: nothing will take it down)
				}
				CompoundTag c = e.getValue().write();
				c.putLong("pos", e.getKey().asLong());
				lands.add(c);
			}
			tag.put("grounds", lands);
			tag.putBoolean("b209", true);
			return tag;
		}

		static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			data.read(tag);
			return data;
		}

		/**
		 * Replaces what this holds with what {@code tag} holds (a load). A camp saved before 32.3 has only {@code pos},
		 * {@code hall}, {@code chief} and {@code day}: it is a {@code bandits} lair of its culture's strength that last
		 * grew the day it was made, with nobody out, and its captain's name picked from his UUID (so it stays the same);
		 * a rest without {@code rest} lasts {@link #REST_DAYS} days. A file saved before B209 (no {@code b209}) has no
		 * camps coming down, and every village with a rest is to be swept for the camps it broke up then.
		 */
		public void read(CompoundTag tag) {
			lairs.clear();
			broken.clear();
			clearing.clear();
			unswept.clear();
			grounds.clear();
			ListTag list = Nbt.getList(tag, "camps", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag c = Nbt.compoundAt(list, i);
				if (!Nbt.hasUuid(c, "chief")) {
					continue;
				}
				UUID captain = Nbt.getUuid(c, "chief");
				long day = Nbt.getLong(c, "day");
				ResourceLocation parsed = Nbt.has(c, "culture", Tag.TAG_STRING) ? ResourceLocation.tryParse(Nbt.getString(c, "culture")) : null;
				ResourceLocation culture = parsed == null ? Threats.BANDITS : parsed;
				int strength = Nbt.has(c, "strength", Tag.TAG_INT) ? Nbt.getInt(c, "strength")
					: Threats.get(culture).flatMap(Culture::lair).map(Culture.Lair::strength).orElse(DEFAULT_STRENGTH);
				Lair lair = new Lair(BlockPos.of(Nbt.getLong(c, "pos")), BlockPos.of(Nbt.getLong(c, "hall")), captain, day, culture, Math.max(0, strength),
					Nbt.has(c, "grown", Tag.TAG_LONG) ? Nbt.getLong(c, "grown") : day,
					Math.max(0, Nbt.getInt(c, "out")), Math.max(0, Nbt.getInt(c, "lost")),
					Nbt.has(c, "name", Tag.TAG_INT) ? Nbt.getInt(c, "name") : Math.floorMod(captain.hashCode(), NAMES));
				lairs.put(lair.hall(), lair);
			}
			ListTag cleared = Nbt.getList(tag, "broken_up", Tag.TAG_COMPOUND);
			for (int i = 0; i < cleared.size(); i++) {
				CompoundTag c = Nbt.compoundAt(cleared, i);
				broken.put(BlockPos.of(Nbt.getLong(c, "hall")), new Rest(Nbt.getLong(c, "day"), Nbt.has(c, "rest", Tag.TAG_INT) ? Nbt.getInt(c, "rest") : REST_DAYS));
			}
			ListTag going = Nbt.getList(tag, "clearing", Tag.TAG_COMPOUND);
			for (int i = 0; i < going.size(); i++) {
				CampCleanup.Clearing c = CampCleanup.Clearing.read(Nbt.compoundAt(going, i));
				if (c != null) {
					clearing.add(c);
				}
			}
			ListTag lands = Nbt.getList(tag, "grounds", Tag.TAG_COMPOUND); // (none before B225)
			for (int i = 0; i < lands.size(); i++) {
				CompoundTag c = Nbt.compoundAt(lands, i);
				CampGround ground = CampGround.read(c);
				if (ground != null) {
					grounds.put(BlockPos.of(Nbt.getLong(c, "pos")), ground);
				}
			}
			if (Nbt.getBoolean(tag, "b209")) {
				ListTag swept = Nbt.getList(tag, "unswept", Tag.TAG_COMPOUND);
				for (int i = 0; i < swept.size(); i++) {
					unswept.add(BlockPos.of(Nbt.getLong(Nbt.compoundAt(swept, i), "hall")));
				}
			} else {
				unswept.addAll(broken.keySet()); // (a world from before B209: its broken camps may still stand)
			}
			setDirty();
		}

		/** Saves this and reads it back, as a restart does (tests). */
		public void roundTrip(HolderLookup.Provider registries) {
			read(save(new CompoundTag(), registries));
		}
	}

	private Lairs() {
	}
}

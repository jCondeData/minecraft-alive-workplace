package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * A day's warning (ROADMAP 32.14). The threat clock (32.2) sets each attack at the dusk before it; a village with a
 * <b>source</b> hears of it then:
 * <ul>
 * <li>a guard on the night watch whose Guard Post is within {@link #REACH} blocks of a finished Lookout Tower or Wall
 * Tower (any style or tier): who comes and from which side;</li>
 * <li>a Scout posted there (not out on a mission): the same, and how many;</li>
 * <li>whatever other systems add to {@link #SOURCES}: M29's Seer ({@code Seer#warningSource}, two days ahead: with a
 * Seer in the village the clock is rolled two nights ahead at the Seer's dawn, B133) and M35's Lighthouse.</li>
 * </ul>
 * A source sees attacks up to {@link Source#days} days ahead (a guard or scout: 1); the attack is told as soon as a
 * source sees it.
 *
 * <p>When warned, players in the village and the hall's owner wherever they are online are told at once, the chronicle
 * has it, and the guards icon and the Defence page show the attack and its side. On the day, from {@link #NOON}, the gates
 * shut and the portcullis drops ({@link Gates}, {@link Sieges#brace}); at dusk the guards take battle stations
 * ({@link BattleStations}) and the villagers go in before the horn. The bars go up again after the day if no siege
 * came to take them over. Without a source the horn is the only warning, as before, and What next? says to build a
 * Lookout Tower and post a guard by it. The warning is saved on the hall's clock ({@link ThreatData#warned}).
 */
public final class Warnings {
	/** How far a guard's post may be from a tower for the guard to see raids coming. */
	public static final int REACH = 24;
	/** From this time of the warned day the gates shut and the portcullis drops. */
	public static final long NOON = 6000;

	/** Who saw them coming: the name shown, whether they counted the raiders (a scout), and how many days ahead they see (1: a day's warning). */
	public record Source(Component who, boolean counts, int days) {
		public Source(Component who, boolean counts) {
			this(who, counts, 1);
		}
	}

	/** Sources other systems add (M35's Lighthouse): given the level and the hall, one if the village has it. */
	public static final List<BiFunction<ServerLevel, BlockPos, Optional<Source>>> SOURCES = new CopyOnWriteArrayList<>();

	private static Set<ResourceLocation> towerIds() {
		return Set.of(StarterBlueprints.LOOKOUT_TOWER.id(), StarterBlueprints.LOOKOUT_TOWER_2.id(), StarterBlueprints.LOOKOUT_TOWER_3.id(), StarterBlueprints.LOOKOUT_TOWER_4.id(),
			StarterBlueprints.WALL_TOWER.id());
	}

	/** Whether {@code structure} is a Lookout Tower or a Wall Tower, in any style or tier. */
	public static boolean isTower(ResourceLocation structure) {
		Set<ResourceLocation> towers = towerIds();
		ResourceLocation base = BlueprintStyles.base(structure);
		for (int i = 0; i < 4; i++) {
			if (towers.contains(base)) {
				return true;
			}
			Optional<ResourceLocation> lower = BlueprintUpgrades.baseOf(base);
			if (lower.isEmpty()) {
				return false;
			}
			base = lower.get();
		}
		return false;
	}

	/** The middle of every finished Lookout Tower and Wall Tower of the village round {@code hall}. */
	public static List<BlockPos> towers(ServerLevel level, BlockPos hall) {
		List<BlockPos> out = new ArrayList<>();
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS)) {
			if (!isTower(f.structure())) {
				continue;
			}
			BlockPos middle = BlueprintLibrary.get(level, f.structure())
				.map(b -> new BlockPos(b.size().getX() / 2, 0, b.size().getZ() / 2)).orElse(BlockPos.ZERO);
			out.add(f.placement().origin().offset(StructureTemplate.transform(middle, f.placement().mirror(), f.placement().rotation(), BlockPos.ZERO)));
		}
		return out;
	}

	/** Whether {@code guard}'s post is within {@link #REACH} blocks of one of {@code towers}. */
	static boolean byATower(Villager guard, List<BlockPos> towers) {
		Optional<BlockPos> post = Builders.benchPos(guard);
		return post.isPresent() && towers.stream().anyMatch(t -> flat(t, post.get()) <= (double) REACH * REACH);
	}

	/** The village's source of warning, if it has one: a scout by a tower first (they count), then a guard, then {@link #SOURCES}. */
	public static Optional<Source> source(ServerLevel level, BlockPos hall) {
		return source(level, hall, 0);
	}

	/** As {@link #source(ServerLevel, BlockPos)}, among the sources that see {@code ahead} days ahead (0: tonight). */
	public static Optional<Source> source(ServerLevel level, BlockPos hall, int ahead) {
		List<BlockPos> towers = ahead <= 1 ? towers(level, hall) : List.of();
		if (!towers.isEmpty()) {
			List<Villager> watch = new ArrayList<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
				v -> v.isAlive() && !v.isBaby() && Guards.isGuard(v) && !Scouts.out(v) && byATower(v, towers)));
			watch.sort(Comparator.<Villager>comparingInt(v -> Scouts.isScout(v) ? 0 : 1).thenComparing(Villager::getUUID));
			if (!watch.isEmpty()) {
				Villager first = watch.get(0);
				return Optional.of(new Source(first.getName(), Scouts.isScout(first)));
			}
		}
		for (BiFunction<ServerLevel, BlockPos, Optional<Source>> other : SOURCES) {
			Optional<Source> found = other.apply(level, hall);
			if (found.isPresent() && found.get().days() >= ahead) {
				return found;
			}
		}
		return Optional.empty();
	}

	/** The attack the village round {@code hall} was warned of and that is still on its clock, if any. */
	public static Optional<ThreatData.Attack> warned(ServerLevel level, BlockPos hall) {
		ThreatData data = ThreatData.get(level);
		long day = data.warned(hall);
		return day < Chronicle.day(level) ? Optional.empty() : data.attacks(hall).stream().filter(a -> a.day() == day).findFirst();
	}

	/** Whether it is the day of a warned attack on the village round {@code hall}, from noon on: the gates stay shut. */
	public static boolean ready(ServerLevel level, BlockPos hall) {
		return VillageRaids.ENABLED && ThreatData.get(level).warned(hall) == Chronicle.day(level) && level.getDayTime() % VillageNeeds.DAY >= NOON;
	}

	/** Whether it is the evening of a warned attack (from dusk) with no raid yet: guards to their stations, villagers in. */
	public static boolean evening(ServerLevel level, BlockPos hall) {
		return ready(level, hall) && level.getDayTime() % VillageNeeds.DAY >= VillageRaids.DUSK && ThreatData.get(level).raid(hall).isEmpty();
	}

	/**
	 * The hall's round: an attack on the clock not yet warned of is told if the village has a source; on the warned day,
	 * from noon, the portcullis drops (the gates are {@link Gates#round}'s); from dusk the villagers keep in; after the
	 * day, bars no siege took over are drawn up.
	 */
	public static void round(ServerLevel level, BlockPos hall, int villagers, int guards) {
		warn(level, hall, villagers, guards);
		ThreatData data = ThreatData.get(level);
		if (ready(level, hall)) {
			if (Sieges.siege(level, hall).isEmpty()) {
				Sieges.brace(level, hall);
			}
			if (evening(level, hall)) {
				keepIn(level, hall);
			}
		} else if (!data.braced(hall).isEmpty() && Sieges.siege(level, hall).isEmpty()) {
			Sieges.raise(level, hall, true);
		}
	}

	/**
	 * Tells the village round {@code hall} of the next attack on its clock, once, if it has a source. Returns whether it
	 * was told now.
	 */
	public static boolean warn(ServerLevel level, BlockPos hall, int villagers, int guards) {
		ThreatData data = ThreatData.get(level);
		long today = Chronicle.day(level);
		Optional<ThreatData.Attack> next = data.attacks(hall).stream().filter(a -> a.day() >= today).findFirst();
		if (next.isEmpty() || data.warned(hall) >= next.get().day()) {
			return false;
		}
		ThreatData.Attack attack = next.get();
		Optional<Source> source = source(level, hall, (int) Math.min(Integer.MAX_VALUE, attack.day() - today));
		if (source.isEmpty()) {
			return false;
		}
		data.warned(hall, attack.day());
		int count = source.get().counts() ? VillageRaids.expected(level, hall, villagers, guards, attack.culture()) : 0;
		Component message = message(level, hall, attack, count);
		List<ServerPlayer> told = new ArrayList<>(level.getPlayers(p -> flat(p.blockPosition(), hall) <= (double) (VillageHalls.RADIUS + 32) * (VillageHalls.RADIUS + 32)));
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.owner() != null) {
			ServerPlayer owner = level.getServer().getPlayerList().getPlayer(entity.owner());
			if (owner != null && !told.contains(owner)) {
				told.add(owner);
			}
		}
		for (ServerPlayer player : told) {
			Chat.chat(player, message.copy().withStyle(ChatFormatting.GOLD));
		}
		level.playSound(null, hall, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1f, 0.6f);
		Chronicle.atHall(level, hall, Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.warning", source.get().who(), who(level, hall, attack), side(hall, attack)));
		return true;
	}

	/**
	 * "Smoke on the north road: Warlord Harl's band will strike Oakvale tomorrow night." With {@code count} above 0 (a
	 * scout's), how many come; two days or more ahead (the Seer's, B133), "A vision of the north road: ... in 2 days."
	 */
	public static Component message(ServerLevel level, BlockPos hall, ThreatData.Attack attack, int count) {
		Component name = VillageHalls.name(level, hall);
		long ahead = attack.day() - Chronicle.day(level);
		String when = (io.github.jcondedata.aliveworkplace.wonder.LighthousePowers.fromTheSea(attack) ? "sea_" : "") // sails, not smoke (35.12)
			+ (ahead >= 2 ? "later" : ahead == 1 ? "tomorrow" : "tonight");
		if (ahead >= 2) {
			return count > 0
				? Component.translatable("message.aliveworkplace.warning." + when + "_count", side(hall, attack), who(level, hall, attack), name, count, ahead)
				: Component.translatable("message.aliveworkplace.warning." + when, side(hall, attack), who(level, hall, attack), name, ahead);
		}
		return count > 0
			? Component.translatable("message.aliveworkplace.warning." + when + "_count", side(hall, attack), who(level, hall, attack), name, count)
			: Component.translatable("message.aliveworkplace.warning." + when, side(hall, attack), who(level, hall, attack), name);
	}

	/** Who comes: "Warlord Harl's band" when a named captain's lair sends them, else the culture's name. */
	public static Component who(ServerLevel level, BlockPos hall, ThreatData.Attack attack) {
		Optional<Lairs.Lair> lair = Lairs.of(level, hall, attack.culture()).filter(Lairs::named);
		return lair.isPresent() ? Component.translatable("message.aliveworkplace.warning.band", Lairs.captainName(lair.get()))
			: Lairs.cultureName(attack.culture());
	}

	/** The side they come from ("north", "south-east"...). */
	public static Component side(BlockPos hall, ThreatData.Attack attack) {
		return new VillageRaids.Night(true, attack.angle(), attack.at()).side(hall);
	}

	/** The evening of a warned attack: the villagers (not the guards) go in, as at the bell. */
	static void keepIn(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		for (Villager villager : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !Guards.isGuard(v))) {
			villager.getBrain().setMemory(MemoryModuleType.HEARD_BELL_TIME, now);
		}
	}

	/** The side of the hall the warned attack comes from, as a direction (the battle stations' side). */
	static net.minecraft.core.Direction out(ThreatData.Attack attack) {
		Vec3 to = new Vec3(Math.cos(attack.angle()), 0, Math.sin(attack.angle()));
		return net.minecraft.core.Direction.getNearest(to.x, 0, to.z);
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	private Warnings() {
	}
}

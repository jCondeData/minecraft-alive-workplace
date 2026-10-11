package io.github.jcondedata.aliveworkplace.guard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmResearch;
import io.github.jcondedata.aliveworkplace.realm.RealmTreasury;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.realm.RoyalHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import org.jetbrains.annotations.Nullable;

/**
 * Relief in a raid (ROADMAP 33.17): when a realm village is raided (our night raids and bandit raids, a vanilla pillager
 * raid at it, and the sieges of Milestone 32, which are raids of a culture that lays siege), the other members send
 * help. The capital always sends its band; every other member in the same dimension within {@link #RANGE} blocks with at
 * least {@link #MIN_GUARDS} guards sends one too. A band is one rider per {@link #PER_GUARDS} guards (1 to
 * {@link #MAX_BAND}, plus one for each level of the realm's Levies research), {@link #MAX_RIDERS} riders at most in all.
 * They ride a tick per {@link #BLOCKS_PER_TICK} blocks (at least {@link #MIN_RIDE} ticks) and arrive on horseback at the
 * village's edge on their home's side (at the capital: before its finished Royal Hall), named "Ashford's guard", their
 * shields painted with the realm's arms. They fight as guards (they are guards on a stay, like mercenaries) until the
 * raid is over and {@link #LINGER} ticks more, or dawn at the latest, then ride off and are gone with their horses.
 *
 * <p>Each rider costs the realm treasury an emerald, paid when the band sets out; a member's band that can't be paid
 * stays home, while the capital's comes anyway. The sender's hall says "2 guards away helping Ashford"; both chronicles
 * note the help. Config {@code realmRelief}.
 *
 * <p>What is saved: the riders carry {@link ModAttachments#RELIEF_RIDER} (where they help, where from) and
 * {@link ModAttachments#MERCENARY_UNTIL} (dawn), so after a reload they still leave when the raid is over; their horses
 * carry {@link #HORSE_TAG} and go when nobody rides them. Bands still on the road and the "away" lines are kept in memory
 * only: a restart mid-ride loses the band (already paid for) and the village, if still raided, calls again.
 */
public final class Relief {
	public static boolean ENABLED = true;
	/** How far (blocks) a member other than the capital rides to help. */
	public static int RANGE = 1024;
	/** A member other than the capital sends help with at least this many guards. */
	public static final int MIN_GUARDS = 2;
	/** One rider for every this many guards. */
	public static final int PER_GUARDS = 3;
	/** Most riders in one village's band (before Levies). */
	public static final int MAX_BAND = 3;
	/** Most riders coming to one raid. */
	public static final int MAX_RIDERS = 6;
	/** The shortest ride, in ticks (30 seconds). */
	public static long MIN_RIDE = 600;
	/** Blocks ridden per tick. */
	public static final int BLOCKS_PER_TICK = 4;
	/** How long they stay after the raid is over. */
	public static long LINGER = 1200;
	/** How far from the hall the village's edge is, where they arrive. */
	public static int EDGE = 40;
	/** What a rider costs the realm treasury, in hundredths of an emerald. */
	public static final long COST_CENTS = 100;
	/** The tag a rider's horse carries. */
	public static final String HORSE_TAG = "aliveworkplace_relief_horse";
	/** The realm research topic that adds one rider per village a level (33.16, Levies). */
	public static final String LEVIES = "levies";

	/** Saved on a rider: the hall of the village they help, and their home's hall. */
	public record Rider(BlockPos target, GlobalPos home) {
		public static final Codec<Rider> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("target").forGetter(Rider::target),
			GlobalPos.CODEC.fieldOf("home").forGetter(Rider::home)
		).apply(i, Rider::new));
	}

	/** A band on the road to {@code target}: {@code riders} from {@code home}, arriving at game time {@code arrive}. */
	public record Band(GlobalPos target, GlobalPos home, Component homeName, int riders, long sent, long arrive, boolean capital) {
	}

	private static final List<Band> ON_THE_ROAD = new ArrayList<>();
	/** The villages relief was called to during their raid now (called once a raid), with when. */
	private static final Map<GlobalPos, Long> CALLED = new HashMap<>();
	/** Who is away helping where (for the sender's hall line). */
	private static final Map<GlobalPos, GlobalPos> AWAY = new HashMap<>();

	private Relief() {
	}

	public static void init() {
		Platform.get().onServerTick(Relief::serverTick);
	}

	/** Every server tick: bands due arrive; each second, raided realm villages call for help. */
	static void serverTick(MinecraftServer server) {
		if (!ON_THE_ROAD.isEmpty()) {
			for (Iterator<Band> it = ON_THE_ROAD.iterator(); it.hasNext(); ) {
				Band band = it.next();
				ServerLevel level = server.getLevel(band.target().dimension());
				if (level == null) {
					it.remove();
				} else if (level.getGameTime() >= band.arrive()) {
					it.remove();
					arrive(level, band);
				}
			}
		}
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		watch(server);
		if (server.getTickCount() % 100 == 0) {
			for (ServerLevel level : server.getAllLevels()) {
				// a horse whose rider fell, or who was left behind, rides off too
				for (AbstractHorse horse : level.getEntities(EntityTypeTest.forClass(AbstractHorse.class), h -> h.getTags().contains(HORSE_TAG) && !h.isVehicle())) {
					poof(level, horse);
					horse.discard();
				}
			}
		}
	}

	/** Each second: a raided realm member that hasn't called yet calls; one no longer raided may call again next time. */
	static void watch(MinecraftServer server) {
		if (!ENABLED) {
			return;
		}
		java.util.Set<GlobalPos> watched = new java.util.LinkedHashSet<>();
		if (Realms.ENABLED) {
			for (RealmData.Realm realm : RealmData.get(server).realms()) {
				watched.addAll(Realms.members(realm));
			}
		}
		if (io.github.jcondedata.aliveworkplace.realm.Pacts.ENABLED && io.github.jcondedata.aliveworkplace.realm.Pacts.ALLIANCES) { // 33.18: allied villages too
			for (io.github.jcondedata.aliveworkplace.realm.Pacts.Pact pact : io.github.jcondedata.aliveworkplace.realm.Pacts.Data.get(server).pacts()) {
				if (pact.alliance()) {
					watched.add(pact.a());
					watched.add(pact.b());
				}
			}
		}
		{
			for (GlobalPos member : watched) {
				ServerLevel level = server.getLevel(member.dimension());
				if (level == null || !level.isLoaded(member.pos())) {
					continue;
				}
				if (VillageRaids.raided(level, member.pos(), member.pos())) {
					if (!CALLED.containsKey(member) && riders(level, member.pos()).isEmpty()) {
						call(level, member.pos());
					}
				} else {
					CALLED.remove(member);
				}
			}
		}
		AWAY.entrySet().removeIf(e -> away(server, e.getKey()) == 0);
	}

	/**
	 * The village round {@code hall} is raided: its realm sends help (once a raid). Returns how many riders set out (0
	 * outside a realm, with realms or relief off, or when it already called). M32's sieges and our raids are seen by the
	 * watch each second; anything else that knows of a raid may call this too.
	 */
	public static int call(ServerLevel level, BlockPos hall) {
		if (!ENABLED) {
			return 0;
		}
		RealmData.Realm realm = Realms.ENABLED ? Realms.realmOf(level, hall) : null;
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		MinecraftServer server = level.getServer();
		List<GlobalPos> allies = allies(server, at, realm);
		if (realm == null && allies.isEmpty() || CALLED.containsKey(at)) {
			return 0;
		}
		long now = level.getGameTime();
		CALLED.put(at, now);
		Component targetName = VillageHalls.name(level, hall);
		int total = realm == null ? 0 : realmBands(level, hall, at, realm, targetName, now);
		// 33.18: allies (and their realms) within RANGE send their own bands, at their own cost, and both owners hear
		for (GlobalPos home : allies) {
			if (total >= MAX_RIDERS || !home.dimension().equals(level.dimension()) || home.pos().distSqr(hall) > (double) RANGE * RANGE) {
				continue;
			}
			int guards = guards(server, home);
			if (guards < MIN_GUARDS) {
				continue;
			}
			int riders = Math.min(band(guards, 0), MAX_RIDERS - total);
			Component homeName = Realms.name(server, home);
			long ride = Math.max(MIN_RIDE, (long) Math.ceil(Math.sqrt(home.pos().distSqr(hall)) / BLOCKS_PER_TICK));
			ON_THE_ROAD.add(new Band(at, home, homeName, riders, now, now + ride, false));
			AWAY.put(home, at);
			total += riders;
			Chronicle.atHall(level, home.pos(), Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.relief_sent_ally", riders, targetName));
			io.github.jcondedata.aliveworkplace.realm.Pacts.tell(server, home, null,
				Component.translatable("message.aliveworkplace.pact.ally_riders_sent", homeName, riders, targetName).withStyle(net.minecraft.ChatFormatting.GOLD));
			io.github.jcondedata.aliveworkplace.realm.Pacts.tell(server, at, null,
				Component.translatable("message.aliveworkplace.pact.ally_riders_coming", riders, homeName, targetName).withStyle(net.minecraft.ChatFormatting.GREEN));
		}
		return total;
	}

	/** The villages that answer {@code at}'s raid as allies (33.18): its allies and its realm's, and their realms' members, not its own realm's. */
	static List<GlobalPos> allies(MinecraftServer server, GlobalPos at, @org.jetbrains.annotations.Nullable RealmData.Realm realm) {
		java.util.Set<GlobalPos> ours = new java.util.LinkedHashSet<>();
		ours.add(at);
		if (realm != null) {
			ours.addAll(Realms.members(realm));
		}
		java.util.Set<GlobalPos> out = new java.util.LinkedHashSet<>();
		for (GlobalPos v : ours) {
			for (GlobalPos ally : io.github.jcondedata.aliveworkplace.realm.Pacts.allies(server, v)) {
				out.add(ally);
				RealmData.Realm theirs = Realms.ENABLED ? RealmData.get(server).realmOf(ally) : null;
				if (theirs != null) {
					out.addAll(Realms.members(theirs));
				}
			}
		}
		out.removeAll(ours);
		return List.copyOf(out);
	}

	/** The realm's bands for its member {@code hall} (33.17); returns how many riders set out. */
	private static int realmBands(ServerLevel level, BlockPos hall, GlobalPos at, RealmData.Realm realm, Component targetName, long now) {
		MinecraftServer server = level.getServer();
		int levies = leviesBonus(server, realm);
		int total = 0;
		List<GlobalPos> homes = new ArrayList<>(Realms.members(realm));
		homes.remove(realm.capital());
		homes.add(0, realm.capital()); // the capital first: it comes even when the treasury is empty
		for (GlobalPos home : homes) {
			if (home.equals(at) || total >= MAX_RIDERS) {
				continue;
			}
			boolean capital = home.equals(realm.capital());
			boolean sameWorld = home.dimension().equals(level.dimension());
			double distance = sameWorld ? Math.sqrt(home.pos().distSqr(hall)) : RANGE;
			if (!capital && (!sameWorld || distance > RANGE)) {
				continue;
			}
			int guards = guards(server, home);
			if (!capital && guards < MIN_GUARDS) {
				continue;
			}
			int riders = Math.min(band(guards, levies), MAX_RIDERS - total);
			Component homeName = Realms.name(server, home);
			boolean paid = RealmTreasury.spend(server, realm, riders * COST_CENTS,
				Component.translatable("message.aliveworkplace.realm.relief_ledger", riders, homeName, targetName));
			if (!paid && !capital) {
				continue; // the treasury is empty: only the capital's band comes
			}
			long ride = Math.max(MIN_RIDE, (long) Math.ceil(distance / BLOCKS_PER_TICK));
			ON_THE_ROAD.add(new Band(at, home, homeName, riders, now, now + ride, capital));
			AWAY.put(home, at);
			total += riders;
			ServerLevel homeLevel = server.getLevel(home.dimension());
			if (homeLevel != null) {
				Chronicle.atHall(homeLevel, home.pos(), Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.relief_sent", riders, targetName));
			}
		}
		return total;
	}

	/** A band's riders for a village with {@code guards} guards and the realm's Levies at {@code levies}. */
	public static int band(int guards, int levies) {
		return Math.max(1, Math.min(MAX_BAND, guards / PER_GUARDS)) + Math.max(0, levies);
	}

	/**
	 * Levies (33.16): one more rider per village for each level of the realm research topic {@code aliveworkplace:levies}
	 * (0 until a pack or the mod ships it).
	 */
	public static int leviesBonus(MinecraftServer server, RealmData.Realm realm) {
		return RealmResearch.get(LEVIES).map(t -> RealmResearch.level(RealmResearch.state(server, realm), t)).orElse(0);
	}

	/** The guards of the village at {@code home} (its own: not mercenaries or riders, nearer its hall than any other); 0 when it isn't loaded. */
	static int guards(MinecraftServer server, GlobalPos home) {
		ServerLevel level = server.getLevel(home.dimension());
		if (level == null || !level.isLoaded(home.pos())) {
			return 0;
		}
		// a guard counts for the hall nearest them, where two villages' areas overlap
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(home.pos()),
			v -> v.isAlive() && Guards.isGuard(v) && !Mercenaries.isMercenary(v)
				&& VillageHalls.nearest(level, v.blockPosition()).map(home.pos()::equals).orElse(false)).size();
	}

	/** The band reaches the village: riders on horseback at its edge on their home's side, or before the capital's Royal Hall. */
	static void arrive(ServerLevel level, Band band) {
		BlockPos hall = band.target().pos();
		BlockPos muster = muster(level, band);
		Direction side = Direction.getNearest(hall.getX() - band.home().pos().getX(), 0, hall.getZ() - band.home().pos().getZ());
		Direction along = side.getAxis() == Direction.Axis.Y ? Direction.EAST : side.getClockWise();
		long now = level.getGameTime();
		long dawn = (24000 - level.getDayTime() % 24000) % 24000;
		long until = now + Math.max(LINGER, dawn);
		VillageBanners.Colours arms = Realms.arms(level.getServer(), band.target());
		for (int i = 0; i < band.riders(); i++) {
			BlockPos spot = ground(level, muster.relative(along, 2 * i - band.riders() + 1), muster.getY());
			Horse horse = EntityType.HORSE.create(level);
			Villager rider = EntityType.VILLAGER.create(level);
			if (horse == null || rider == null) {
				continue;
			}
			float yaw = side.getOpposite().toYRot() + 180f;
			horse.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0f);
			horse.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
			horse.setAge(0);
			horse.setTamed(true);
			horse.equipSaddle(new ItemStack(Items.SADDLE), null);
			horse.addTag(HORSE_TAG);
			horse.setPersistenceRequired();
			rider.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0f);
			rider.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
			rider.setVillagerData(rider.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(3));
			rider.setVillagerXp(70); // keeps the profession without a guard post
			rider.refreshBrain(level); // a guard's brain: fighting in its CORE activity
			ModAttachments.MERCENARY_UNTIL.set(rider, until);
			ModAttachments.RELIEF_RIDER.set(rider, new Rider(hall.immutable(), band.home()));
			rider.setCustomName(Component.translatable("entity.aliveworkplace.relief_rider", band.homeName()));
			rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			rider.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			rider.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			ItemStack shield = new ItemStack(Items.SHIELD);
			if (arms != null) {
				VillageBanners.paint(shield, arms);
			}
			rider.setItemSlot(EquipmentSlot.OFFHAND, shield);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				rider.setDropChance(slot, 0f);
			}
			level.addFreshEntity(horse);
			level.addFreshEntity(rider);
			if (!Cavalry.ride(rider, horse)) {
				rider.startRiding(horse, true);
			}
			Guards.updateHealth(rider);
			level.sendParticles(ParticleTypes.CLOUD, spot.getX() + 0.5, spot.getY() + 0.5, spot.getZ() + 0.5, 8, 0.4, 0.3, 0.4, 0.02);
		}
		level.playSound(null, muster, SoundEvents.RAID_HORN.value(), SoundSource.NEUTRAL, 0.8f, 1.6f);
		Component name = VillageHalls.name(level, hall);
		Chronicle.atHall(level, hall, Chronicle.Kind.RAID, Component.translatable("chronicle.aliveworkplace.relief_came", band.riders(), band.homeName()));
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= (double) (VillageHalls.RADIUS + 32) * (VillageHalls.RADIUS + 32))) {
			Chat.chat(player, Component.translatable("message.aliveworkplace.relief.arrived", band.riders(), band.homeName(), name).withStyle(ChatFormatting.GREEN));
		}
	}

	/** Where a band gathers: before the capital's finished Royal Hall when it is the capital that's raided, else the edge on the home's side. */
	static BlockPos muster(ServerLevel level, Band band) {
		BlockPos hall = band.target().pos();
		RealmData.Realm realm = Realms.realmOf(level, hall);
		if (realm != null && realm.capital().equals(band.target())) {
			for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, hall, RoyalHalls.RANGE)) {
				var base = BlueprintStyles.base(f.structure());
				if (base.equals(StarterBlueprints.ROYAL_HALL.id()) || base.equals(StarterBlueprints.ROYAL_HALL_2.id())) {
					// the front is the template's z=0 side: a few blocks out from it
					return ground(level, f.placement().origin().relative(f.placement().rotation().rotate(Direction.NORTH), 4), hall.getY());
				}
			}
		}
		double dx = band.home().pos().getX() - hall.getX();
		double dz = band.home().pos().getZ() - hall.getZ();
		double length = Math.sqrt(dx * dx + dz * dz);
		if (length < 1 || !band.home().dimension().equals(band.target().dimension())) {
			dx = 0;
			dz = -1;
			length = 1;
		}
		return ground(level, hall.offset((int) Math.round(dx / length * EDGE), 0, (int) Math.round(dz / length * EDGE)), hall.getY());
	}

	/** Open ground in {@code column}: the surface, unless that is far above or below {@code nearY} (a roof, a cliff), then a spot near that height. */
	static BlockPos ground(ServerLevel level, BlockPos column, int nearY) {
		BlockPos top = VillageRaids.surface(level, column);
		if (Math.abs(top.getY() - nearY) <= 16) {
			return top;
		}
		for (int y = nearY + 8; y >= nearY - 8; y--) {
			BlockPos p = column.atY(y);
			if (io.github.jcondedata.aliveworkplace.work.Walker.canStand(level, p)) {
				return p;
			}
		}
		return top;
	}

	// ---- the riders -------------------------------------------------------------------------------------------------

	public static boolean isRider(Villager villager) {
		return ModAttachments.RELIEF_RIDER.has(villager);
	}

	/** The riders helping the village round {@code hall} now. */
	public static List<Villager> riders(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall).inflate(EDGE), v -> {
			Rider r = ModAttachments.RELIEF_RIDER.get(v);
			return v.isAlive() && r != null && r.target().equals(hall);
		});
	}

	/** Every server tick of a villager (before {@link Mercenaries#tick}): a rider stays a minute past the raid, then rides off with their horse. */
	static void tick(Villager villager) {
		Rider rider = ModAttachments.RELIEF_RIDER.get(villager);
		if (rider == null || !(villager.level() instanceof ServerLevel level)) {
			return;
		}
		Long until = ModAttachments.MERCENARY_UNTIL.get(villager);
		long now = level.getGameTime();
		if (until == null) {
			return;
		}
		if (now % 20 == 0 && until > now + LINGER && !VillageRaids.raided(level, rider.target(), rider.target())) {
			until = now + LINGER; // the raid is over: a minute more
			ModAttachments.MERCENARY_UNTIL.set(villager, until);
		}
		if (now >= until && villager.getVehicle() instanceof AbstractHorse horse && horse.getTags().contains(HORSE_TAG)) {
			poof(level, horse);
			horse.discard(); // Mercenaries.tick sends the rider off this same tick
		}
	}

	private static void poof(ServerLevel level, net.minecraft.world.entity.Entity e) {
		level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.5, e.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
	}

	// ---- the sender's hall ------------------------------------------------------------------------------------------

	/** Riders from {@code home} on the road or at the village they help. */
	static int away(MinecraftServer server, GlobalPos home) {
		GlobalPos target = AWAY.get(home);
		if (target == null) {
			return 0;
		}
		int n = ON_THE_ROAD.stream().filter(b -> b.home().equals(home)).mapToInt(Band::riders).sum();
		ServerLevel level = server.getLevel(target.dimension());
		if (level != null && level.isLoaded(target.pos())) {
			n += (int) riders(level, target.pos()).stream().filter(v -> ModAttachments.RELIEF_RIDER.get(v).home().equals(home)).count();
		}
		return n;
	}

	/** The hall's guards line: "2 guards away helping Ashford", or null. */
	@Nullable
	public static Component hallLine(ServerLevel level, BlockPos hall) {
		GlobalPos home = GlobalPos.of(level.dimension(), hall);
		GlobalPos target = AWAY.get(home);
		int n = away(level.getServer(), home);
		if (target == null || n <= 0) {
			return null;
		}
		return Component.translatable("screen.aliveworkplace.hall.relief_away", n, Realms.name(level.getServer(), target));
	}

	/** The bands on the road to the village round {@code hall} (tests, the showcase). */
	public static List<Band> onTheRoad(ServerLevel level, BlockPos hall) {
		GlobalPos at = GlobalPos.of(level.dimension(), hall);
		return ON_THE_ROAD.stream().filter(b -> b.target().equals(at)).toList();
	}

	/** Forgets bands on the road, calls and away lines (tests). */
	public static void forget() {
		ON_THE_ROAD.clear();
		CALLED.clear();
		AWAY.clear();
	}
}

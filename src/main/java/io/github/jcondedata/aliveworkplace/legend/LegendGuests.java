package io.github.jcondedata.aliveworkplace.legend;

import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * Legends who visit (ROADMAP 29.8, Terraria's guests): once a day, at each place's own time, a village with no Legend
 * guest may get one, a Legend whose {@code visit} way names that place, whose conditions the village meets, whose slot
 * is free (29.3) and who hasn't visited in {@link #COOLDOWN_DAYS} days, with the way's chance ({@link #CHANCE} by default,
 * times {@link Legends#visitFactor}). The places:
 * <ul>
 *   <li>{@code inn}: in the morning, instead of that day's traveller ({@code Innkeepers.tend}: an Innkeeper, a free bed);</li>
 *   <li>{@code market}: with the traders on market day ({@code MarketDays.hold});</li>
 *   <li>{@code festival}: at the fireworks of a festival whose crowd reaches the way's {@code crowd} ({@code Festivals});</li>
 *   <li>{@code chapel}: at midnight under a full moon, at the village's finished Chapel (the hall's round);</li>
 *   <li>{@code hall}: in the morning, beside the Village Hall (the hall's round).</li>
 * </ul>
 * A guest is announced (29.3), stays up to {@link #STAY_DAYS} days and takes no job. Right-clicked, they show their
 * terms ({@link #openTerms}). They settle by themselves the round every need is met: they claim the free bed in a tier III
 * home, take their trade as a Master (a free workstation of it if there is one) and go in the chronicle. If not, they
 * leave on the third evening out of sight, and the chronicle says what they missed. Everything is saved: the guest's
 * {@code LEGEND} attachment and the hall's {@link State}.
 */
public final class LegendGuests {
	/** Days before the same Legend may visit the same village again. */
	public static final int COOLDOWN_DAYS = 7;
	/** Days a guest stays (they leave on the evening of the last one). */
	public static final int STAY_DAYS = 3;
	/** A visit way's chance when its file names none. */
	public static final float CHANCE = 0.25f;
	/** The morning, for the inn and the hall (day time). */
	public static final long MORNING_FROM = 1000;
	public static final long MORNING_TO = 6000;
	/** Midnight, for the chapel: wide enough for a hall round to fall in it (day time). */
	public static final long MIDNIGHT_FROM = 17500;
	public static final long MIDNIGHT_TO = 19000;
	/** The evening a guest leaves from (day time). */
	public static final long EVENING = 12000;
	/** A guest leaves only with no player this close. */
	public static final int OUT_OF_SIGHT = 24;

	/** The guest staying now: who, which Legend, and their last day (a {@link Chronicle#day}). */
	public record Guest(UUID villager, ResourceLocation id, long lastDay) {
		public static final Codec<Guest> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.CODEC.fieldOf("villager").forGetter(Guest::villager),
			ResourceLocation.CODEC.fieldOf("id").forGetter(Guest::id),
			Codec.LONG.optionalFieldOf("last_day", 0L).forGetter(Guest::lastDay)
		).apply(i, Guest::new));
	}

	/**
	 * The hall's guest fields: {@code legendVisits} (a Legend's id to the day it last came), {@code legendGuest} (the guest
	 * staying now) and {@code legendRolled} (a place to the day it last rolled for a guest). All absent in older halls.
	 */
	public record State(Map<String, Long> visits, Optional<Guest> guest, Map<String, Long> rolled) {
		public static final State EMPTY = new State(Map.of(), Optional.empty(), Map.of());
		private static final Codec<Map<String, Long>> DAYS = Codec.unboundedMap(Codec.STRING, Codec.LONG);

		public static State load(CompoundTag tag) {
			Map<String, Long> visits = tag.contains("legendVisits") ? DAYS.parse(NbtOps.INSTANCE, tag.get("legendVisits")).result().orElse(Map.of()) : Map.of();
			Optional<Guest> guest = tag.contains("legendGuest") ? Guest.CODEC.parse(NbtOps.INSTANCE, tag.get("legendGuest")).result() : Optional.empty();
			Map<String, Long> rolled = tag.contains("legendRolled") ? DAYS.parse(NbtOps.INSTANCE, tag.get("legendRolled")).result().orElse(Map.of()) : Map.of();
			return new State(Map.copyOf(visits), guest, Map.copyOf(rolled));
		}

		public void save(CompoundTag tag) {
			if (!visits.isEmpty()) {
				DAYS.encodeStart(NbtOps.INSTANCE, visits).result().ifPresent(t -> tag.put("legendVisits", t));
			}
			guest.flatMap(g -> Guest.CODEC.encodeStart(NbtOps.INSTANCE, g).result()).ifPresent(t -> tag.put("legendGuest", t));
			if (!rolled.isEmpty()) {
				DAYS.encodeStart(NbtOps.INSTANCE, rolled).result().ifPresent(t -> tag.put("legendRolled", t));
			}
		}

		public State withGuest(Optional<Guest> g) {
			return new State(visits, g, rolled);
		}

		public State visited(ResourceLocation id, long day) {
			Map<String, Long> out = new HashMap<>(visits);
			out.put(id.toString(), day);
			return new State(Map.copyOf(out), guest, rolled);
		}

		public State rolledOn(String place, long day) {
			Map<String, Long> out = new HashMap<>(rolled);
			out.put(place, day);
			return new State(visits, guest, Map.copyOf(out));
		}

		/** The day {@code id} last visited, or empty. */
		public Optional<Long> lastVisit(ResourceLocation id) {
			return Optional.ofNullable(visits.get(id.toString()));
		}
	}

	@Nullable
	private static VillageHallBlockEntity hall(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity ? entity : null;
	}

	/** Whether {@code villager} is a Legend visiting as a guest (Legends on). */
	public static boolean isGuest(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		return Legends.ENABLED && data != null && data.guest();
	}

	/** The guest staying in the village round {@code hall} now (loaded and still a guest), or null. */
	@Nullable
	public static Villager guest(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = hall(level, hall);
		if (entity == null || entity.legendGuests().guest().isEmpty()) {
			return null;
		}
		Entity e = level.getEntity(entity.legendGuests().guest().get().villager());
		return e instanceof Villager v && v.isAlive() && isGuest(v) ? v : null;
	}

	/** The visit ways of {@code legend} to {@code place}. */
	static List<JsonObject> ways(Legend legend, String place) {
		return legend.ways("visit").stream().filter(w -> place.equals(w.has("place") ? w.get("place").getAsString() : "inn")).toList();
	}

	/** The chance a visit {@code way} has in the village round {@code hall}: its own (1 in 4 by default) times {@link Legends#visitFactor}. */
	public static float chance(ServerLevel level, BlockPos hall, JsonObject way) {
		float base = way.has("chance") ? way.get("chance").getAsFloat() : CHANCE;
		return Math.max(0f, Math.min(1f, base * Legends.visitFactor(level, hall)));
	}

	/**
	 * The Legends who may visit the village round {@code hall} at {@code place} on {@code today}: a visit way there (a
	 * festival's crowd big enough), every condition met, the slot free and no visit in the last {@link #COOLDOWN_DAYS} days.
	 */
	public static List<Legend> candidates(ServerLevel level, BlockPos hall, String place, long today) {
		VillageHallBlockEntity entity = hall(level, hall);
		List<Legend> out = new ArrayList<>();
		if (entity == null) {
			return out;
		}
		for (Legend legend : Legends.all()) {
			List<JsonObject> ways = ways(legend, place);
			if (ways.isEmpty()) {
				continue;
			}
			if ("festival".equals(place) && ways.stream().noneMatch(w -> !w.has("crowd") || entity.festivalCrowd() >= w.get("crowd").getAsInt())) {
				continue;
			}
			Optional<Long> last = entity.legendGuests().lastVisit(legend.id());
			if (last.isPresent() && today - last.get() < COOLDOWN_DAYS) {
				continue;
			}
			boolean met = true;
			for (Condition c : legend.conditions()) {
				met &= c.met(level, hall);
			}
			if (met && LegendSlots.whyNot(level, hall, legend, null).isEmpty()) {
				out.add(legend);
			}
		}
		return out;
	}

	/**
	 * The day's roll at {@code place} for the village round {@code hall}: with no guest staying and someone who may come,
	 * once a day, each in turn comes with their way's chance; the first who does arrives near {@code at}. Returns them, or null.
	 */
	@Nullable
	public static Villager visit(ServerLevel level, BlockPos hall, String place, BlockPos at, RandomSource random) {
		VillageHallBlockEntity entity = hall(level, hall);
		if (!Legends.ENABLED || entity == null) {
			return null;
		}
		long today = Chronicle.day(level);
		if (staying(level, hall, entity, today)) {
			return null;
		}
		if (entity.legendGuests().rolled().getOrDefault(place, -1L) == today) {
			return null;
		}
		List<Legend> candidates = candidates(level, hall, place, today);
		if (candidates.isEmpty()) {
			return null;
		}
		entity.setLegendGuests(entity.legendGuests().rolledOn(place, today));
		for (Legend legend : candidates) {
			float chance = ways(legend, place).stream().map(w -> chance(level, hall, w)).max(Float::compare).orElse(0f);
			if (random.nextFloat() < chance) {
				return come(level, hall, legend, place, at, random);
			}
		}
		return null;
	}

	/** Whether a guest is staying (one who is gone, unloaded past their last day or no longer a guest, is let go). */
	static boolean staying(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, long today) {
		Optional<Guest> g = entity.legendGuests().guest();
		if (g.isEmpty()) {
			return false;
		}
		Entity e = level.getEntity(g.get().villager());
		if (e == null ? today > g.get().lastDay() + 1 : !(e instanceof Villager v) || !v.isAlive() || !isGuest(v)) {
			entity.setLegendGuests(entity.legendGuests().withGuest(Optional.empty()));
			return false;
		}
		return true;
	}

	/** {@code legend} comes to the village round {@code hall} as a guest, near {@code at}: announced, and staying {@link #STAY_DAYS} days. */
	@Nullable
	public static Villager come(ServerLevel level, BlockPos hall, Legend legend, String place, BlockPos at, RandomSource random) {
		return come(level, hall, legend, at, random, "visit:" + place);
	}

	/** As {@link #come(ServerLevel, BlockPos, Legend, String, BlockPos, RandomSource)}, with the way they came by ({@code found:<site>}, 29.9). */
	@Nullable
	public static Villager come(ServerLevel level, BlockPos hall, Legend legend, BlockPos at, RandomSource random, String way) {
		VillageHallBlockEntity entity = hall(level, hall);
		BlockPos spot = standingSpot(level, at);
		Villager guest = entity == null || spot == null ? null : EntityType.VILLAGER.create(level);
		if (guest == null) {
			return null;
		}
		guest.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, random.nextFloat() * 360f, 0f);
		guest.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, null);
		VillagerType[] types = {VillagerType.PLAINS, VillagerType.DESERT, VillagerType.SAVANNA, VillagerType.SNOW, VillagerType.TAIGA,
			VillagerType.JUNGLE, VillagerType.SWAMP};
		// A guest takes no job: a nitwit until they settle (then a Master of the Legend's trade).
		guest.setVillagerData(guest.getVillagerData().setType(types[random.nextInt(types.length)]).setProfession(VillagerProfession.NITWIT));
		String name = legend.names().isEmpty() ? "" : legend.names().get(random.nextInt(legend.names().size()));
		guest.setCustomName(name.isEmpty() ? legend.titleText() : Component.translatable(name));
		guest.setPersistenceRequired();
		long today = Chronicle.day(level);
		long lastDay = today + STAY_DAYS - 1;
		ModAttachments.LEGEND.set(guest, new LegendData(legend.id(), name, true, Optional.of(hall), level.getDayTime() / VillageNeeds.DAY, lastDay,
			Map.of(), -1, -1, way));
		level.addFreshEntityWithPassengers(guest);
		LegendLook.update(guest);
		entity.setLegendGuests(entity.legendGuests().withGuest(Optional.of(new Guest(guest.getUUID(), legend.id(), lastDay))).visited(legend.id(), today));
		level.sendParticles(ParticleTypes.END_ROD, guest.getX(), guest.getY() + 1.0, guest.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
		level.playSound(null, guest.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5f, 1f);
		LegendSlots.announce(level, hall, guest, legend, true);
		refresh(level, hall, guest);
		return guest;
	}

	@Nullable
	private static BlockPos standingSpot(ServerLevel level, BlockPos at) {
		for (int r = 1; r <= 6; r++) {
			for (BlockPos p : BlockPos.betweenClosed(at.offset(-r, -2, -r), at.offset(r, 2, r))) {
				if (level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
					&& level.getBlockState(p).getCollisionShape(level, p).isEmpty()
					&& level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty() && level.getFluidState(p).isEmpty()) {
					return p.immutable();
				}
			}
		}
		return null;
	}

	/** The village's finished Chapel (the {@code chapel} blueprint): where its guests stand, or empty. */
	public static Optional<BlockPos> chapel(ServerLevel level, BlockPos hall) {
		return BuildSiteManager.get(level).finishedNear(level, hall, VillageHalls.RADIUS).stream()
			.filter(f -> BlueprintStyles.base(f.structure()).equals(StarterBlueprints.CHAPEL.id()))
			.map(f -> BlueprintOutline.bounds(f.placement(), StarterBlueprints.CHAPEL.size()))
			.map(box -> new BlockPos(box.getCenter().getX(), box.minY() + 1, box.getCenter().getZ()))
			.findFirst();
	}

	/**
	 * The hall's round: the Chapel's full-moon midnight and the hall's morning roll for a guest, and the guest staying now
	 * settles (every need met) or, from the evening of their last day and out of sight, leaves.
	 */
	public static void round(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = hall(level, hall);
		if (!Legends.ENABLED || entity == null) {
			return;
		}
		long time = level.getDayTime() % VillageNeeds.DAY;
		if (time >= MORNING_FROM && time < MORNING_TO) {
			visit(level, hall, "hall", hall, level.random);
		}
		if (level.getMoonPhase() == 0 && time >= MIDNIGHT_FROM && time < MIDNIGHT_TO) {
			chapel(level, hall).ifPresent(at -> visit(level, hall, "chapel", at, level.random));
		}
		LegendSites.arrive(level, hall);
		OldSage.round(level, hall); // the hermit's hut, the Iron Pact's golems (29.14)
		if (time >= MORNING_FROM && time < MORNING_TO) {
			GolemSmith.round(level, hall); // the Golem Smith's golems (29.15)
		}
		tend(level, hall);
	}

	/** The guest staying in the village round {@code hall}: settles when every need is met, else leaves when their stay is over. */
	public static void tend(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = hall(level, hall);
		if (entity == null || !staying(level, hall, entity, Chronicle.day(level))) {
			return;
		}
		Villager guest = guest(level, hall);
		if (guest == null) {
			return; // staying, but out of loaded land
		}
		LegendData data = refresh(level, hall, guest);
		if (data == null) {
			return;
		}
		if (data.unmet().isEmpty()) {
			settle(level, hall, guest);
		} else if (stayOver(level, data) && level.getNearestPlayer(guest, OUT_OF_SIGHT) == null) {
			leave(level, hall, guest);
		}
	}

	/** Whether a guest's stay is over: the evening of their last day, or later. */
	public static boolean stayOver(ServerLevel level, LegendData data) {
		long today = Chronicle.day(level);
		return today > data.lastDay() || today == data.lastDay() && level.getDayTime() % VillageNeeds.DAY >= EVENING;
	}

	/** Which of a guest's needs the village meets now, in the order the terms show them. */
	public static Map<String, Boolean> needs(ServerLevel level, BlockPos hall, Legend legend) {
		Map<String, Boolean> met = new LinkedHashMap<>();
		Optional<BlockPos> bed = freeHome(level, hall);
		met.put(LegendText.HOME, bed.isPresent());
		legend.luxury().ifPresent(kind -> met.put(LegendText.LUXURY, hasLuxury(level, hall, bed, kind)));
		met.put(LegendText.HAPPY, LegendNeeds.happy(level, hall));
		return met;
	}

	/** Checks the guest's needs now and writes them on their data (a need unmet: 1), for the terms and the hall. */
	@Nullable
	public static LegendData refresh(ServerLevel level, BlockPos hall, Villager guest) {
		LegendData data = ModAttachments.LEGEND.get(guest);
		Legend legend = data == null ? null : Legends.get(data.id()).orElse(null);
		if (legend == null || !data.guest()) {
			return null;
		}
		Map<String, Integer> unmet = new LinkedHashMap<>();
		needs(level, hall, legend).forEach((need, met) -> {
			if (!met) {
				unmet.put(need, 1);
			}
		});
		LegendData now = data.checkedOn(Chronicle.day(level), unmet, -1, data.lastLuxury());
		ModAttachments.LEGEND.set(guest, now);
		return now;
	}

	/**
	 * A free bed in a home of their own: in a finished building of tier III or higher round the hall, unclaimed, with no
	 * villager's bed in that building.
	 */
	public static Optional<BlockPos> freeHome(ServerLevel level, BlockPos hall) {
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), p -> true, hall, VillageHalls.RADIUS, PoiManager.Occupancy.HAS_SPACE)
			.map(BlockPos::immutable).sorted(java.util.Comparator.comparingDouble(p -> p.distSqr(hall))).toList();
		List<BlockPos> taken = null;
		for (BlockPos bed : beds) {
			Homes.Building building = Homes.building(level, bed).orElse(null);
			if (building == null || building.home().tier() < LegendNeeds.HOME_TIER) {
				continue;
			}
			if (taken == null) {
				taken = new ArrayList<>();
				for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
					BlockPos theirs = VillageNeeds.bed(level, v);
					if (theirs != null) {
						taken.add(theirs);
					}
				}
			}
			BoundingBox box = building.box();
			if (taken.stream().noneMatch(box::isInside)) {
				return Optional.of(bed);
			}
		}
		return Optional.empty();
	}

	/** Their luxury is to be had: in a chest of the home waiting for them, or in the village store. */
	static boolean hasLuxury(ServerLevel level, BlockPos hall, Optional<BlockPos> bed, String kind) {
		var tag = LegendNeeds.luxuryTag(kind);
		if (bed.isPresent()) {
			Optional<Homes.Building> building = Homes.building(level, bed.get());
			if (building.isPresent() && SupplyContainers.firstMatching(level, SupplyContainers.inside(level, building.get().box()), s -> s.is(tag)) != null) {
				return true;
			}
		}
		return SupplyContainers.firstMatching(level, VillageNeeds.store(level, hall), s -> s.is(tag)) != null;
	}

	/**
	 * The guest settles: they claim the free bed in the tier III home, become a Master of the Legend's trade (taking a free
	 * workstation of it if there is one) and go on the record and in the chronicle.
	 */
	public static boolean settle(ServerLevel level, BlockPos hall, Villager guest) {
		LegendData data = ModAttachments.LEGEND.get(guest);
		Legend legend = data == null ? null : Legends.get(data.id()).orElse(null);
		Optional<BlockPos> bed = freeHome(level, hall);
		if (legend == null || !data.guest() || bed.isEmpty()) {
			return false;
		}
		level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(bed.get()), bed.get(), 1);
		guest.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed.get()));
		Legends.make(level, guest, legend, "visit");
		LegendData settled = ModAttachments.LEGEND.get(guest);
		ModAttachments.LEGEND.set(guest, new LegendData(settled.id(), data.name(), false, Optional.of(hall), settled.since(), -1, Map.of(), -1, -1,
			data.way()));
		VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(legend.job());
		if (job != null && job != VillagerProfession.NONE && job != VillagerProfession.NITWIT) {
			Optional<BlockPos> station = level.getPoiManager().take(job.heldJobSite(), (h, p) -> true, hall, VillageHalls.RADIUS);
			station.ifPresent(s -> {
				guest.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), s));
				io.github.jcondedata.aliveworkplace.work.JobSiteTickets.hold(level, guest);
			});
		}
		guest.refreshBrain(level);
		VillageHallBlockEntity entity = hall(level, hall);
		if (entity != null) {
			entity.setLegendGuests(entity.legendGuests().withGuest(Optional.empty()));
		}
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, guest.getX(), guest.getY() + 1.2, guest.getZ(), 30, 0.4, 0.6, 0.4, 0.15);
		level.playSound(null, guest.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.5f, 1f);
		return true;
	}

	/** The guest leaves (their stay is over and nobody's watching): the chronicle says what they missed. */
	public static void leave(ServerLevel level, BlockPos hall, Villager guest) {
		LegendData data = ModAttachments.LEGEND.get(guest);
		Legend legend = data == null ? null : Legends.get(data.id()).orElse(null);
		if (legend != null) {
			Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.legend.left",
				guest.getDisplayName(), legend.titleText(), LegendNeeds.wants(legend, data)));
		}
		VillageHallBlockEntity entity = hall(level, hall);
		if (entity != null) {
			entity.setLegendGuests(entity.legendGuests().withGuest(Optional.empty()));
		}
		level.sendParticles(ParticleTypes.POOF, guest.getX(), guest.getY() + 0.5, guest.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
		ModAttachments.LEGEND.remove(guest);
		guest.discard();
	}

	// The terms screen.

	public static final int WHO_SLOT = 10;
	public static final int BRINGS_SLOT = 12;
	public static final int WANTS_SLOT = 14;
	public static final int DAYS_SLOT = 16;

	/** A guest's terms, right-clicked: who they are, what they would bring, what they want (ticks and crosses), days left. */
	public static void openTerms(ServerPlayer player, Villager guest) {
		ChoiceMenu.open(player, guest.getDisplayName(), p -> p.isAlive() && guest.isAlive() && isGuest(guest) && p.distanceToSqr(guest) < 64,
			menu -> render(menu, guest));
	}

	/** The same screen, not shown to anyone (tests). */
	public static ChoiceMenu termsMenuForTest(ServerPlayer player, Villager guest) {
		return ChoiceMenu.detached(player, menu -> render(menu, guest));
	}

	/** Days left of a guest's stay, today included. */
	public static long daysLeft(ServerLevel level, LegendData data) {
		return Math.max(1, data.lastDay() - Chronicle.day(level) + 1);
	}

	private static void render(ChoiceMenu menu, Villager guest) {
		menu.clearButtons();
		ServerLevel level = (ServerLevel) guest.level();
		LegendData data = ModAttachments.LEGEND.get(guest);
		Legend legend = data == null ? null : Legends.get(data.id()).orElse(null);
		if (legend == null) {
			return;
		}
		LegendData now = data.hall().map(h -> refresh(level, h, guest)).orElse(data);
		LegendData shown = now != null ? now : data;
		List<Component> who = new ArrayList<>();
		who.add(LegendText.rarityLine(legend));
		who.add(legend.loreText().copy().withStyle(ChatFormatting.GRAY));
		menu.button(WHO_SLOT, icon(Items.NETHER_STAR, LegendText.name(guest.getDisplayName(), legend), who), null);
		List<Component> brings = new ArrayList<>(LegendText.powerLines(legend, false));
		if (brings.isEmpty()) {
			brings.add(Component.translatable("screen.aliveworkplace.legend_terms.brings_nothing").withStyle(ChatFormatting.GRAY));
		}
		menu.button(BRINGS_SLOT, icon(Items.ENCHANTED_BOOK, Component.translatable("screen.aliveworkplace.legend_terms.brings")
			.withStyle(ChatFormatting.YELLOW), brings), null);
		List<Component> wants = new ArrayList<>(LegendText.needLines(legend, shown));
		wants.add(Component.translatable("screen.aliveworkplace.legend_terms.settle_hint").withStyle(ChatFormatting.GRAY));
		menu.button(WANTS_SLOT, icon(Items.WRITABLE_BOOK, Component.translatable("screen.aliveworkplace.legend_terms.wants")
			.withStyle(ChatFormatting.AQUA), wants), null);
		long left = daysLeft(level, shown);
		menu.button(DAYS_SLOT, icon(Items.CLOCK, Component.translatable(left == 1 ? "screen.aliveworkplace.legend_terms.last_day"
				: "screen.aliveworkplace.legend_terms.days_left", left).withStyle(ChatFormatting.LIGHT_PURPLE),
			List.of(Component.translatable("screen.aliveworkplace.legend_terms.leaves", shown.lastDay()).withStyle(ChatFormatting.GRAY))), null);
	}

	private static ItemStack icon(net.minecraft.world.item.Item item, Component name, List<Component> lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name));
		stack.set(DataComponents.LORE, new ItemLore(lore.stream().map(LegendGuests::plain).toList()));
		return stack;
	}

	private static Component plain(Component text) {
		return text.copy().withStyle(style -> style.withItalic(false));
	}

	private LegendGuests() {
	}
}

package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Scouts (ROADMAP 32.12): a guard handed a spyglass (in the off hand, like the other kinds' items) is a {@link Guards.Kind#SCOUT}.
 * From the Defence page, "Scout the lair" sends the village's scout out by day: they walk (or ride, if in the saddle,
 * {@link Cavalry}) to within {@link #SIGHT} blocks of the lair, raise the spyglass for {@link #LOOK_TICKS} ticks (its
 * sound) and come home to their post. Back home the lair is known ({@link #known}): the Defence page shows its exact place,
 * strength and captain, and the scout leaves a <b>Scouting Report</b> in a chest by their post: a filled map between
 * the village and the lair with a red X on the lair, named for it, its tooltip listing the culture, captain and strength.
 * A scout not back within {@link #DAY} ticks is lost (the chronicle says so). A lair's band ignores a scout more than
 * {@link #UNSEEN} blocks off. When raiders flee at dawn, a scout of the village trails them home: the lair becomes known
 * without anyone being sent.
 *
 * <p>Missions and what each village knows are saved per dimension in {@code aliveworkplace_scouts}; a mission resumes
 * after a restart where it stood.
 */
public final class Scouts {
	/** How near the lair a scout goes before raising the spyglass. */
	public static final int SIGHT = 24;
	/** How long the scout looks through the spyglass. */
	public static final int LOOK_TICKS = 60;
	/** A scout not home within a day is lost. */
	public static final long DAY = 24000;
	/** A lair's band doesn't go for a scout farther off than this. */
	public static final double UNSEEN = 8;
	/** The longest leg of a scout's walk (villagers' paths can't reach far). */
	static final int LEG = 16;
	/** How near their post a scout is home. */
	static final double HOME = 3.5;
	static final float PACE = 0.8f;

	public enum Phase { OUT, LOOK, BACK }

	/** A scout out on a mission: who, from which village, to which lair, since when, what they're at, and their name. */
	public record Mission(UUID scout, BlockPos hall, BlockPos lair, long started, Phase phase, long lookUntil, boolean seen, String name) {
		Mission with(Phase phase, long lookUntil, boolean seen) {
			return new Mission(scout, hall, lair, started, phase, lookUntil, seen, name);
		}
	}

	/** What a village knows of its lair: where it is, the day it learnt it, whether by trailing raiders, and who found it. */
	public record Known(BlockPos lair, long day, boolean trailed, String by) {
	}

	/** Why a scout was or wasn't sent. */
	public enum Sent {
		SENT, NO_LAIR, NIGHT, NO_SCOUT, ALREADY_OUT;

		public String key() {
			return "message.aliveworkplace.scout." + name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Each walking scout's leg: not saved (a restart picks a new one). */
	private static final Map<UUID, Leg> LEGS = new HashMap<>();

	private static final class Leg {
		BlockPos waypoint;
		int retry;
		BlockPos lastPos;
		long lastMoved;
	}

	public static void init() {
		Platform.get().onServerTick(Scouts::tick);
	}

	// Who is a scout.

	public static boolean isSpyglass(ItemStack stack) {
		return !stack.isEmpty() && stack.is(Items.SPYGLASS);
	}

	public static boolean isScout(Villager villager) {
		return Guards.isGuard(villager) && Guards.kind(villager) == Guards.Kind.SCOUT;
	}

	/** The village's scouts (in its area, alive), nearest the hall first. */
	public static List<Villager> scouts(ServerLevel level, BlockPos hall) {
		List<Villager> scouts = new ArrayList<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && isScout(v)));
		scouts.sort(Comparator.comparingDouble(v -> v.distanceToSqr(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5)));
		return scouts;
	}

	/** Whether {@code villager} is out on a mission. */
	public static boolean out(Villager villager) {
		return villager.level() instanceof ServerLevel level && Data.get(level).missions.containsKey(villager.getUUID());
	}

	/** The mission of the village round {@code hall} under way, if any. */
	public static Optional<Mission> mission(ServerLevel level, BlockPos hall) {
		return Data.get(level).missions.values().stream().filter(m -> m.hall().equals(hall)).findFirst();
	}

	/** The mission {@code scout} is on, if any. */
	public static Optional<Mission> missionOf(ServerLevel level, UUID scout) {
		return Optional.ofNullable(Data.get(level).missions.get(scout));
	}

	/** What the village round {@code hall} knows of the lair by it now: nothing if it hasn't been found, or has moved. */
	public static Optional<Known> known(ServerLevel level, BlockPos hall) {
		Known known = Data.get(level).known.get(hall);
		return Lairs.near(level, hall).filter(lair -> known != null && lair.pos().equals(known.lair())).map(lair -> known);
	}

	/**
	 * A lair's band (anyone with its tag) doesn't go for a scout more than {@link #UNSEEN} blocks off: what
	 * {@code LivingEntity.canAttack} asks (the mixin), so no target goal or sensor picks the scout, and one that had them lets go.
	 */
	public static boolean unseen(LivingEntity attacker, LivingEntity target) {
		return target instanceof Villager villager && (attacker.getTags().contains(Lairs.TAG) || attacker.getTags().contains(Lairs.BANDIT_TAG))
			&& attacker.distanceToSqr(target) > UNSEEN * UNSEEN && isScout(villager);
	}

	// Sending one.

	/** "Scout the lair" (the Defence page): the village's nearest free scout sets out, by day, if a lair stands. */
	public static Sent send(ServerLevel level, BlockPos hall) {
		Optional<Lairs.Lair> lair = Lairs.near(level, hall);
		if (lair.isEmpty()) {
			return Sent.NO_LAIR;
		}
		if (mission(level, hall).isPresent()) {
			return Sent.ALREADY_OUT;
		}
		if (Math.floorMod(level.getDayTime(), DAY) >= 12000) {
			return Sent.NIGHT;
		}
		Villager scout = scouts(level, hall).stream().filter(v -> !out(v) && !v.isBaby()).findFirst().orElse(null);
		if (scout == null) {
			return Sent.NO_SCOUT;
		}
		if (scout.isSleeping()) {
			scout.stopSleeping();
		}
		Data.get(level).put(new Mission(scout.getUUID(), hall, lair.get().pos(), level.getGameTime(), Phase.OUT, 0, false,
			scout.getDisplayName().getString()));
		LEGS.remove(scout.getUUID());
		Chronicle.atHall(level, hall, Chronicle.Kind.SIGHTING, Component.translatable("chronicle.aliveworkplace.scout.sent",
			scout.getDisplayName(), Lairs.lairName(lair.get().culture())));
		return Sent.SENT;
	}

	/** {@link #send} for a player on the Defence page: tells them what came of it. */
	public static void send(ServerPlayer player, ServerLevel level, BlockPos hall) {
		Sent sent = send(level, hall);
		Optional<Mission> mission = mission(level, hall);
		Component lair = Lairs.near(level, hall).map(l -> Lairs.lairName(l.culture())).orElse(Component.empty());
		Component scout = Component.literal(mission.map(Mission::name).orElse(""));
		Chat.actionBar(player, Component.translatable(sent.key(), scout, lair)
			.withStyle(sent == Sent.SENT ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
	}

	// The trip.

	private static void tick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			Data data = Data.get(level);
			if (data.missions.isEmpty()) {
				continue;
			}
			for (Mission mission : List.copyOf(data.missions.values())) {
				tick(level, mission);
			}
		}
	}

	/** One tick of a mission (public for tests that drive it). */
	public static void tick(ServerLevel level, Mission mission) {
		long now = level.getGameTime();
		Villager scout = level.getEntity(mission.scout()) instanceof Villager v && v.isAlive() ? v : null;
		if (scout == null) {
			// Out of loaded ground, or fallen: lost once the day is out.
			if (now - mission.started() > DAY) {
				lose(level, mission, null);
			}
			return;
		}
		if (!isScout(scout)) {
			// The spyglass taken off them: no scout any more, the mission is called off.
			Data.get(level).remove(mission.scout());
			LEGS.remove(mission.scout());
			return;
		}
		if (now - mission.started() > DAY) {
			lose(level, mission, scout);
			return;
		}
		if (scout.isSleeping()) {
			scout.stopSleeping();
		}
		Optional<Lairs.Lair> lair = Lairs.near(level, mission.hall()).filter(l -> l.pos().equals(mission.lair()));
		switch (mission.phase()) {
			case OUT -> {
				if (lair.isEmpty()) {
					Data.get(level).put(mission.with(Phase.BACK, 0, false)); // the camp is gone: home
					return;
				}
				if (flat(scout.blockPosition(), mission.lair()) <= (SIGHT - 2) * (SIGHT - 2)) {
					raise(level, scout, mission);
					return;
				}
				walk(level, scout, mission.lair());
				status(scout, Component.translatable("message.aliveworkplace.guard.state.scouting", Lairs.lairName(lair.get().culture())));
			}
			case LOOK -> {
				scout.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
				scout.getNavigation().stop();
				scout.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(mission.lair().above()));
				scout.getLookControl().setLookAt(mission.lair().getX() + 0.5, mission.lair().getY() + 1.5, mission.lair().getZ() + 0.5);
				if (now >= mission.lookUntil()) {
					lower(scout);
					level.playSound(null, scout.blockPosition(), SoundEvents.SPYGLASS_STOP_USING, SoundSource.NEUTRAL, 1f, 1f);
					Data.get(level).put(mission.with(Phase.BACK, 0, lair.isPresent()));
					LEGS.remove(scout.getUUID());
				}
			}
			case BACK -> {
				BlockPos home = Builders.benchPos(scout).orElse(mission.hall());
				if (flat(scout.blockPosition(), home) <= HOME * HOME && Math.abs(scout.getY() - home.getY()) < 4) {
					home(level, scout, mission);
					return;
				}
				walk(level, scout, home);
				status(scout, Component.translatable("message.aliveworkplace.guard.state.scout_home"));
			}
		}
	}

	/** At the lookout: the spyglass up (into the main hand, where a villager shows what they hold), its sound. */
	private static void raise(ServerLevel level, Villager scout, Mission mission) {
		scout.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		scout.getNavigation().stop();
		ItemStack off = scout.getItemBySlot(EquipmentSlot.OFFHAND);
		if (isSpyglass(off)) {
			scout.setItemSlot(EquipmentSlot.OFFHAND, scout.getItemBySlot(EquipmentSlot.MAINHAND));
			scout.setItemSlot(EquipmentSlot.MAINHAND, off);
		}
		level.playSound(null, scout.blockPosition(), SoundEvents.SPYGLASS_USE, SoundSource.NEUTRAL, 1f, 1f);
		Data.get(level).put(mission.with(Phase.LOOK, level.getGameTime() + LOOK_TICKS, false));
		Lairs.near(level, mission.hall()).ifPresent(l ->
			status(scout, Component.translatable("message.aliveworkplace.guard.state.scout_looking", Lairs.lairName(l.culture()))));
	}

	/** The spyglass back in the off hand, the weapon in the main one. */
	private static void lower(Villager scout) {
		ItemStack main = scout.getItemBySlot(EquipmentSlot.MAINHAND);
		if (isSpyglass(main)) {
			scout.setItemSlot(EquipmentSlot.MAINHAND, scout.getItemBySlot(EquipmentSlot.OFFHAND));
			scout.setItemSlot(EquipmentSlot.OFFHAND, main);
		}
	}

	/** Where a scout's walk stands: their leg, walk target and activity (tests' failure messages). */
	public static String describe(Villager scout) {
		Leg leg = LEGS.get(scout.getUUID());
		return "leg " + (leg == null ? null : leg.waypoint) + ", walk target " + scout.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
			.map(t -> t.getTarget().currentBlockPosition()).orElse(null) + ", can't reach " + scout.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).orElse(null)
			+ ", path done " + scout.getNavigation().isDone() + ", activity " + scout.getBrain().getActiveNonCoreActivity().orElse(null);
	}

	/** Walks toward {@code to} a leg at a time, on the ground; stuck for 10 s, the next leg goes off to one side. */
	private static void walk(ServerLevel level, Villager scout, BlockPos to) {
		Leg leg = LEGS.computeIfAbsent(scout.getUUID(), k -> new Leg());
		long now = level.getGameTime();
		BlockPos at = scout.blockPosition();
		if (leg.lastPos == null || leg.lastPos.distSqr(at) > 4) {
			leg.lastPos = at;
			leg.lastMoved = now;
		}
		boolean stuck = now - leg.lastMoved > 200;
		if (leg.waypoint == null || flat(at, leg.waypoint) <= 9 || stuck) {
			leg.waypoint = waypoint(level, at, to, stuck ? (int) (now / 200 % 2 == 0 ? 6 : -6) : 0);
			leg.lastMoved = now;
			leg.lastPos = at;
			scout.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		}
		leg.retry = Walker.requestWalk(scout, leg.waypoint, PACE, 1, leg.retry);
	}

	/** The next stop from {@code from} toward {@code to}: at most {@link #LEG} blocks on, on the ground, {@code aside} to one side. */
	public static BlockPos waypoint(ServerLevel level, BlockPos from, BlockPos to, int aside) {
		double dx = to.getX() - from.getX();
		double dz = to.getZ() - from.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		int x = to.getX();
		int z = to.getZ();
		if (distance > LEG) {
			x = from.getX() + (int) Math.round(dx / distance * LEG - dz / distance * aside);
			z = from.getZ() + (int) Math.round(dz / distance * LEG + dx / distance * aside);
		} else if (distance <= 2) {
			return to;
		}
		// Never loads ground for the walk: on ground not loaded, the stop keeps the scout's height (they can't path
		// there anyway, and the next leg looks again once it is).
		BlockPos flat = new BlockPos(x, from.getY(), z);
		if (!level.isLoaded(flat)) {
			return flat;
		}
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	private static void status(Villager scout, Component line) {
		WorkerStatus.set(scout, GuardCombat.title(scout), -1f, line.copy().withStyle(ChatFormatting.AQUA));
	}

	/** Home: the lair is known, the report goes in a chest by the post (or at the scout's feet), the chronicle says so. */
	private static void home(ServerLevel level, Villager scout, Mission mission) {
		Data data = Data.get(level);
		data.remove(mission.scout());
		LEGS.remove(scout.getUUID());
		lower(scout);
		scout.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		Optional<Lairs.Lair> lair = Lairs.near(level, mission.hall()).filter(l -> l.pos().equals(mission.lair()));
		if (!mission.seen() || lair.isEmpty()) {
			Chronicle.atHall(level, mission.hall(), Chronicle.Kind.SIGHTING, Component.translatable("chronicle.aliveworkplace.scout.gone", scout.getDisplayName()));
			tell(level, mission.hall(), Component.translatable("message.aliveworkplace.scout.gone", scout.getDisplayName()).withStyle(ChatFormatting.YELLOW));
			return;
		}
		long day = Chronicle.day(level);
		data.know(mission.hall(), new Known(lair.get().pos(), day, false, scout.getDisplayName().getString()));
		ItemStack report = report(level, mission.hall(), lair.get(), scout.getDisplayName(), day);
		BlockPos post = Builders.benchPos(scout).orElse(scout.blockPosition());
		ItemStack rest = SupplyContainers.insert(level, SupplyContainers.find(level, post, null), report);
		if (!rest.isEmpty()) {
			level.addFreshEntity(new ItemEntity(level, scout.getX(), scout.getY() + 0.5, scout.getZ(), rest));
		}
		Chronicle.atHall(level, mission.hall(), Chronicle.Kind.SIGHTING, Component.translatable("chronicle.aliveworkplace.scout.back",
			scout.getDisplayName(), Lairs.lairName(lair.get().culture())));
		tell(level, mission.hall(), Component.translatable("message.aliveworkplace.scout.back", scout.getDisplayName(),
			Lairs.lairName(lair.get().culture())).withStyle(ChatFormatting.GREEN));
		level.playSound(null, scout.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1f, 1f);
	}

	/** Not back within the day: lost. One still out there alive is gone for good. */
	private static void lose(ServerLevel level, Mission mission, @Nullable Villager scout) {
		Data.get(level).remove(mission.scout());
		LEGS.remove(mission.scout());
		Component name = Component.literal(mission.name());
		if (scout != null) {
			scout.discard();
		}
		Chronicle.atHall(level, mission.hall(), Chronicle.Kind.SIGHTING, Component.translatable("chronicle.aliveworkplace.scout.lost", name));
		tell(level, mission.hall(), Component.translatable("message.aliveworkplace.scout.lost", name).withStyle(ChatFormatting.RED));
	}

	private static void tell(ServerLevel level, BlockPos hall, Component message) {
		int range = VillageHalls.RADIUS + 32;
		for (ServerPlayer player : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= (double) range * range)) {
			Chat.chat(player, message);
		}
	}

	/**
	 * When raiders flee at dawn ({@code VillageRaids}), a scout of the village trails them home: the lair of {@code culture}
	 * becomes known. True if it did.
	 */
	public static boolean trail(ServerLevel level, BlockPos hall, net.minecraft.resources.ResourceLocation culture) {
		if (!Lairs.live()) {
			return false;
		}
		Optional<Lairs.Lair> lair = Lairs.of(level, hall, culture);
		if (lair.isEmpty() || known(level, hall).isPresent()) {
			return false;
		}
		Villager scout = scouts(level, hall).stream().filter(v -> !out(v)).findFirst().orElse(null);
		if (scout == null) {
			return false;
		}
		Data.get(level).know(hall, new Known(lair.get().pos(), Chronicle.day(level), true, scout.getDisplayName().getString()));
		Chronicle.atHall(level, hall, Chronicle.Kind.SIGHTING, Component.translatable("chronicle.aliveworkplace.scout.trailed",
			scout.getDisplayName(), Lairs.lairName(culture)));
		tell(level, hall, Component.translatable("message.aliveworkplace.scout.trailed", scout.getDisplayName(), Lairs.lairName(culture))
			.withStyle(ChatFormatting.GREEN));
		return true;
	}

	// The report.

	/**
	 * The Scouting Report: a filled map like an explorer map, between the village and the lair (the smallest scale that
	 * shows both), the village's mark and a red X on the lair; named for the lair; the culture, captain and strength in
	 * its tooltip.
	 */
	public static ItemStack report(ServerLevel level, BlockPos hall, Lairs.Lair lair, Component by, long day) {
		int mx = (hall.getX() + lair.pos().getX()) / 2;
		int mz = (hall.getZ() + lair.pos().getZ()) / 2;
		ItemStack map = MapItem.create(level, mx, mz, scale(hall, lair.pos()), true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, hall, "village", MapDecorationTypes.PLAINS_VILLAGE);
		MapItemSavedData.addTargetDecoration(map, lair.pos(), "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.MAP_COLOR, new MapItemColor(0x9C3B2A));
		map.set(DataComponents.ITEM_NAME, Component.translatable("item.aliveworkplace.scouting_report", Lairs.lairName(lair.culture())));
		map.set(DataComponents.LORE, new ItemLore(List.of(
			lore(Component.translatable("item.aliveworkplace.scouting_report_lore.culture", Lairs.cultureName(lair.culture())), ChatFormatting.GRAY),
			lore(Component.translatable("item.aliveworkplace.scouting_report_lore.captain", Lairs.captainName(lair)), ChatFormatting.GOLD),
			lore(Component.translatable("item.aliveworkplace.scouting_report_lore.strength", lair.strength()), ChatFormatting.RED),
			lore(Component.translatable("item.aliveworkplace.scouting_report_lore.by", by, day), ChatFormatting.DARK_GRAY))));
		return map;
	}

	private static Component lore(Component text, ChatFormatting color) {
		return text.copy().withStyle(style -> style.withItalic(false).withColor(color));
	}

	/** The smallest map scale (1 to 4) whose square, where the game puts it, holds both the village and the lair. */
	static byte scale(BlockPos hall, BlockPos lair) {
		double mx = (hall.getX() + lair.getX()) / 2.0;
		double mz = (hall.getZ() + lair.getZ()) / 2.0;
		for (byte scale = 1; scale < 4; scale++) {
			int size = 128 * (1 << scale);
			int cx = (int) Math.floor((mx + 64.0) / size) * size + size / 2 - 64;
			int cz = (int) Math.floor((mz + 64.0) / size) * size + size / 2 - 64;
			int half = size / 2 - 8;
			if (Math.abs(hall.getX() - cx) <= half && Math.abs(lair.getX() - cx) <= half && Math.abs(hall.getZ() - cz) <= half
				&& Math.abs(lair.getZ() - cz) <= half) {
				return scale;
			}
		}
		return 4;
	}

	/** Forgets every mission and what every village knows (tests). */
	public static void forget(ServerLevel level) {
		Data data = Data.get(level);
		data.missions.clear();
		data.known.clear();
		data.setDirty();
		LEGS.clear();
	}

	/** Missions under way and what each village knows of its lair, per dimension. */
	public static final class Data extends SavedData {
		public static final String NAME = "aliveworkplace_scouts";
		final Map<UUID, Mission> missions = new LinkedHashMap<>();
		final Map<BlockPos, Known> known = new LinkedHashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		void put(Mission mission) {
			missions.put(mission.scout(), mission);
			setDirty();
		}

		void remove(UUID scout) {
			if (missions.remove(scout) != null) {
				setDirty();
			}
		}

		void know(BlockPos hall, Known what) {
			known.put(hall, what);
			setDirty();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for (Mission m : missions.values()) {
				CompoundTag c = new CompoundTag();
				Nbt.putUuid(c, "scout", m.scout());
				c.putLong("hall", m.hall().asLong());
				c.putLong("lair", m.lair().asLong());
				c.putLong("started", m.started());
				c.putString("phase", m.phase().name());
				c.putLong("look_until", m.lookUntil());
				c.putBoolean("seen", m.seen());
				c.putString("name", m.name());
				list.add(c);
			}
			tag.put("missions", list);
			ListTag knownList = new ListTag();
			for (Map.Entry<BlockPos, Known> e : known.entrySet()) {
				CompoundTag c = new CompoundTag();
				c.putLong("hall", e.getKey().asLong());
				c.putLong("lair", e.getValue().lair().asLong());
				c.putLong("day", e.getValue().day());
				c.putBoolean("trailed", e.getValue().trailed());
				c.putString("by", e.getValue().by());
				knownList.add(c);
			}
			tag.put("known", knownList);
			return tag;
		}

		static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			data.read(tag);
			return data;
		}

		/** Replaces what this holds with what {@code tag} holds; anything missing has its default. */
		public void read(CompoundTag tag) {
			missions.clear();
			known.clear();
			ListTag list = Nbt.getList(tag, "missions", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag c = Nbt.compoundAt(list, i);
				if (!Nbt.hasUuid(c, "scout")) {
					continue;
				}
				Phase phase;
				try {
					phase = Phase.valueOf(Nbt.getString(c, "phase"));
				} catch (IllegalArgumentException e) {
					phase = Phase.OUT;
				}
				Mission m = new Mission(Nbt.getUuid(c, "scout"), BlockPos.of(Nbt.getLong(c, "hall")), BlockPos.of(Nbt.getLong(c, "lair")),
					Nbt.getLong(c, "started"), phase, Nbt.getLong(c, "look_until"), Nbt.getBoolean(c, "seen"), Nbt.getString(c, "name"));
				missions.put(m.scout(), m);
			}
			ListTag knownList = Nbt.getList(tag, "known", Tag.TAG_COMPOUND);
			for (int i = 0; i < knownList.size(); i++) {
				CompoundTag c = Nbt.compoundAt(knownList, i);
				known.put(BlockPos.of(Nbt.getLong(c, "hall")), new Known(BlockPos.of(Nbt.getLong(c, "lair")), Nbt.getLong(c, "day"),
					Nbt.getBoolean(c, "trailed"), Nbt.getString(c, "by")));
			}
			setDirty();
		}

		/** Saves this and reads it back, as a restart does (tests). */
		public void roundTrip(HolderLookup.Provider registries) {
			read(save(new CompoundTag(), registries));
		}
	}

	private Scouts() {
	}
}

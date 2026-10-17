package io.github.jcondedata.aliveworkplace.guard;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.WildPokemon;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Den Wardens (ROADMAP 38.3, design note M38): a guard posted from the hall's Defense page to a raid den the village
 * knows ({@link RaidDens}). By day the warden walks there (a leg at a time, like a scout) and stands watch, shooing wild
 * Pokémon that crowd the crystal; at dusk they walk back to their post, and the guards' own day takes over. When a
 * watched den glows again the chronicle says so, once. A player granted Raid Dens' {@code join_raid} or a tier
 * advancement within {@link #NEAR} blocks of a watched den gets the warden's cheer: one of the mod's cheer items, once
 * per raid (the list is cleared when the den glows again). Recalled, the guard goes back to normal duty.
 */
public final class DenWardens {
	/** Switched by {@code denWardens} (behind M38's gate). */
	public static boolean ENABLED = false;
	/** A warden stands this close to the den (blocks, flat). */
	static final double WATCH = 3.0;
	/** Home is reached this close to the post. */
	static final double HOME = 3.5;
	/** A player this close to a watched den when they join a raid gets the cheer. */
	public static final double NEAR = 16;
	/** Wild Pokémon this close to the crystal are shooed off. */
	static final double CROWD = 6;
	static final float PACE = 0.7f;
	static final List<String> CHEERS = List.of("cheer_attack", "cheer_defense", "cheer_heal");

	private static final Map<UUID, Leg> LEGS = new HashMap<>();

	private static final class Leg {
		BlockPos waypoint;
		int retry;
	}

	private DenWardens() {
	}

	public static void init() {
		Platform.get().onServerTick(DenWardens::tick);
	}

	/** The den {@code guard} is posted to, if any. */
	public static Optional<BlockPos> postOf(ServerLevel level, UUID guard) {
		return Optional.ofNullable(Data.get(level).wardens.get(guard));
	}

	/** The warden of the den at {@code den}, if one is posted. */
	public static Optional<UUID> wardenOf(ServerLevel level, BlockPos den) {
		return Data.get(level).wardens.entrySet().stream().filter(e -> e.getValue().equals(den)).map(Map.Entry::getKey).findFirst();
	}

	/** Whether {@code guard} is the den's for now: posted, and it's day or they're still walking home. GuardPatrol leaves them be. */
	public static boolean busy(Villager guard) {
		if (!ENABLED || !(guard.level() instanceof ServerLevel level)) {
			return false;
		}
		BlockPos den = Data.get(level).wardens.get(guard.getUUID());
		if (den == null) {
			return false;
		}
		return !level.isNight() || flat(guard.blockPosition(), home(level, guard)) > HOME * HOME;
	}

	/** Posts the village's nearest free guard to the den: what to tell the player. */
	public static Component post(ServerLevel level, BlockPos hall, BlockPos den) {
		if (wardenOf(level, den).isPresent()) {
			return Component.translatable("message.aliveworkplace.warden.already").withStyle(ChatFormatting.YELLOW);
		}
		Data data = Data.get(level);
		List<Villager> free = new ArrayList<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
			v -> v.isAlive() && !v.isBaby() && Guards.isGuard(v) && !Scouts.out(v) && !Mercenaries.isMercenary(v) && !data.wardens.containsKey(v.getUUID())));
		if (free.isEmpty()) {
			return Component.translatable("message.aliveworkplace.warden.none").withStyle(ChatFormatting.YELLOW);
		}
		free.sort(Comparator.comparingDouble(v -> v.distanceToSqr(Vec3.atCenterOf(den))));
		Villager guard = free.get(0);
		data.wardens.put(guard.getUUID(), den.immutable());
		data.setDirty();
		LEGS.remove(guard.getUUID());
		return Component.translatable("message.aliveworkplace.warden.posted", guard.getDisplayName(), VillageHallScreen.where(hall, den)).withStyle(ChatFormatting.GREEN);
	}

	/** Recalls the den's warden to normal duty: what to tell the player. */
	public static Component recall(ServerLevel level, BlockPos den) {
		Optional<UUID> warden = wardenOf(level, den);
		if (warden.isEmpty()) {
			return Component.translatable("message.aliveworkplace.warden.no_warden").withStyle(ChatFormatting.YELLOW);
		}
		Entity guard = level.getEntity(warden.get());
		release(level, warden.get(), guard instanceof Villager v ? v : null);
		Component name = guard == null ? Component.translatable("message.aliveworkplace.warden.the_warden") : guard.getDisplayName();
		return Component.translatable("message.aliveworkplace.warden.recalled", name).withStyle(ChatFormatting.GREEN);
	}

	private static void release(ServerLevel level, UUID id, Villager guard) {
		Data data = Data.get(level);
		data.wardens.remove(id);
		data.setDirty();
		LEGS.remove(id);
		if (guard != null) {
			guard.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		}
	}

	private static void tick(MinecraftServer server) {
		if (!ENABLED) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Data data = Data.get(level);
			if (data.wardens.isEmpty() || level.getGameTime() % 10 != 0) {
				continue;
			}
			for (Map.Entry<UUID, BlockPos> e : List.copyOf(data.wardens.entrySet())) {
				if (level.getEntity(e.getKey()) instanceof Villager guard) {
					tick(level, guard, e.getValue());
				}
			}
		}
	}

	/** One look at a warden (public for tests that drive it): to the den by day, home at dusk. */
	public static void tick(ServerLevel level, Villager guard, BlockPos den) {
		if (!guard.isAlive() || !Guards.isGuard(guard) || RaidDens.near(level, den).stream().noneMatch(d -> d.pos().equals(den))) {
			release(level, guard.getUUID(), guard); // no guard any more, or the den is gone
			return;
		}
		if (GuardCombat.isFighting(guard)) {
			return;
		}
		if (!level.isNight()) {
			if (guard.isSleeping()) {
				guard.stopSleeping();
			}
			if (flat(guard.blockPosition(), den) > WATCH * WATCH) {
				walk(level, guard, stand(den, home(level, guard)));
				status(guard, "message.aliveworkplace.guard.state.to_den");
				return;
			}
			guard.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			guard.getNavigation().stop();
			guard.getLookControl().setLookAt(den.getX() + 0.5, den.getY() + 0.5, den.getZ() + 0.5);
			status(guard, "message.aliveworkplace.guard.state.watching_den");
			shoo(level, guard, den);
			return;
		}
		BlockPos home = home(level, guard);
		if (flat(guard.blockPosition(), home) > HOME * HOME) {
			walk(level, guard, home);
			status(guard, "message.aliveworkplace.guard.state.from_den");
		} else {
			LEGS.remove(guard.getUUID());
		}
	}

	/** Where a warden stands: two blocks off the crystal, on the side toward home. */
	static BlockPos stand(BlockPos den, BlockPos home) {
		return den.offset(2 * Integer.signum(home.getX() - den.getX()), 0, 2 * Integer.signum(home.getZ() - den.getZ()));
	}

	/** Where a warden goes at dusk: their Guard Post, or the hall. */
	static BlockPos home(ServerLevel level, Villager guard) {
		return Builders.benchPos(guard).or(() -> VillageHalls.nearest(level, guard.blockPosition())).orElse(guard.blockPosition());
	}

	/** Wild Pokémon crowding the crystal are nudged off it. */
	static void shoo(ServerLevel level, Villager guard, BlockPos den) {
		if (!WildPokemon.EXTENSION.present()) {
			return;
		}
		AABB box = new AABB(den).inflate(CROWD);
		for (Entity e : level.getEntities((Entity) null, box, e -> WildPokemon.EXTENSION.call(w -> w.isWild(e), false))) {
			Vec3 away = e.position().subtract(Vec3.atCenterOf(den)).multiply(1, 0, 1);
			Vec3 push = away.lengthSqr() < 1.0e-4 ? new Vec3(0.4, 0, 0) : away.normalize().scale(0.4);
			e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.1, push.z));
			e.hurtMarked = true;
			guard.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		}
	}

	/** A watched den glowed again: the chronicle of the village of its hall says so, and the cheers start over. */
	public static void glowed(ServerLevel level, RaidDens.Den den) {
		Data data = Data.get(level);
		if (data.cheered.remove(den.pos().asLong()) != null) {
			data.setDirty();
		}
		if (!ENABLED || wardenOf(level, den.pos()).isEmpty()) {
			return;
		}
		RaidDens.hallNear(level, den.pos()).ifPresent(hall -> Chronicle.atHall(level, hall, Chronicle.Kind.SIGHTING,
			Component.translatable("chronicle.aliveworkplace.warden.glows", RaidDens.typeName(den.type()), VillageHallScreen.where(hall, den.pos()))));
	}

	/** A player was granted an advancement: a raid joined by a watched den earns the warden's cheer, once a raid. */
	public static void advancementDone(ServerPlayer player, ResourceLocation id) {
		if (!ENABLED || !id.getNamespace().equals(RaidDens.DEN.getNamespace())
			|| !(id.getPath().equals("join_raid") || RaidDens.TIERS.contains(id.getPath()))
			|| !(player.level() instanceof ServerLevel level)) {
			return;
		}
		Data data = Data.get(level);
		for (Map.Entry<UUID, BlockPos> e : data.wardens.entrySet()) {
			BlockPos den = e.getValue();
			if (player.position().distanceToSqr(Vec3.atCenterOf(den)) > NEAR * NEAR) {
				continue;
			}
			Set<UUID> had = data.cheered.computeIfAbsent(den.asLong(), k -> new HashSet<>());
			if (!had.add(player.getUUID())) {
				return; // once a raid
			}
			data.setDirty();
			String pick = CHEERS.get(player.getRandom().nextInt(CHEERS.size()));
			Optional<Item> cheer = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(RaidDens.DEN.getNamespace(), pick));
			if (cheer.isEmpty()) {
				return;
			}
			ItemStack stack = new ItemStack(cheer.get());
			Entity warden = level.getEntity(e.getKey());
			Component name = warden == null ? Component.translatable("message.aliveworkplace.warden.the_warden") : warden.getDisplayName();
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.warden.cheer", name, stack.getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE));
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
			return;
		}
	}

	private static void walk(ServerLevel level, Villager guard, BlockPos to) {
		Leg leg = LEGS.computeIfAbsent(guard.getUUID(), k -> new Leg());
		BlockPos at = guard.blockPosition();
		if (leg.waypoint == null || flat(at, leg.waypoint) <= 9 || flat(leg.waypoint, to) > flat(at, to) + 1) {
			leg.waypoint = Scouts.waypoint(level, at, to, 0);
		}
		leg.retry = Walker.requestWalk(guard, leg.waypoint, PACE, 2, leg.retry);
	}

	private static void status(Villager guard, String key) {
		WorkerStatus.set(guard, GuardCombat.title(guard), -1f, Component.translatable(key).withStyle(ChatFormatting.AQUA));
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/** The wardens of a level and who had a den's cheer this raid ({@code aliveworkplace_den_wardens}). */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_den_wardens";
		final Map<UUID, BlockPos> wardens = new LinkedHashMap<>();
		final Map<Long, Set<UUID>> cheered = new HashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			wardens.forEach((guard, den) -> {
				CompoundTag w = new CompoundTag();
				Nbt.putUuid(w, "guard", guard);
				w.putLong("den", den.asLong());
				ListTag had = new ListTag();
				cheered.getOrDefault(den.asLong(), Set.of()).forEach(p -> had.add(StringTag.valueOf(p.toString())));
				w.put("cheered", had);
				list.add(w);
			});
			tag.put("wardens", list);
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag list = Nbt.getList(tag, "wardens", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag w = Nbt.compoundAt(list, i);
				if (!Nbt.hasUuid(w, "guard")) {
					continue;
				}
				BlockPos den = BlockPos.of(Nbt.getLong(w, "den"));
				data.wardens.put(Nbt.getUuid(w, "guard"), den);
				ListTag had = Nbt.getList(w, "cheered", Tag.TAG_STRING);
				for (int j = 0; j < had.size(); j++) {
					try {
						data.cheered.computeIfAbsent(den.asLong(), k -> new HashSet<>()).add(UUID.fromString(Nbt.stringAt(had, j)));
					} catch (IllegalArgumentException ignored) {
						// not a player id
					}
				}
			}
			return data;
		}
	}
}

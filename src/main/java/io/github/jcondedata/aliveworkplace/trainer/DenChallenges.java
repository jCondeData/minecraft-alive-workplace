package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.RaidDenLoot;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;

/**
 * Village trainers challenge the raid dens (ROADMAP 38.4, design note M38). Every {@link #EVERY_DAYS} days a village
 * whose best trainer is a Trainer Leader or a Master sends them against an active den it knows ({@link RaidDens}): up
 * to tier {@link #LEADER_MAX} for a Leader, {@link #MASTER_MAX} for a Master, never one a player is at (within
 * {@link #PLAYER_NEAR} blocks). The server can't play a raid with no player in it, so the challenge is settled at once,
 * like a Stadium bout: the trainer's rank against the den's tier. A win rolls the den's own loot table
 * ({@code cobblemonraiddens:raid/tier/<tier>}) into the village store, sets the den inactive (its own reset brings it
 * back) and writes the chronicle; a loss sends the trainer home tired for a day (not sent again until then). Players in
 * the village or by the den are told as it happens, so they can go and watch the den go dark.
 */
public final class DenChallenges {
	/** Switched by {@code denChallenges} (behind M38's gate). */
	public static boolean ENABLED = false;
	/** Days between a village's challenges ({@code denChallengeDays}). */
	public static int EVERY_DAYS = 3;
	public static final int LEADER_MAX = 3;
	public static final int MASTER_MAX = 3; // tier 4 and up are for players (owner, 2026-10-18)
	public static final double PLAYER_NEAR = 8;
	/** A lost challenge leaves the trainer tired this long. */
	public static final long TIRED = 24000;
	/** Villages are looked at this often. */
	static final int LOOK_EVERY = 600;

	public enum Result { WON, LOST, NO_TRAINER, NO_DEN }

	private DenChallenges() {
	}

	public static void init() {
		Platform.get().onLevelTick(DenChallenges::tick);
	}

	static void tick(ServerLevel level) {
		if (!ENABLED || level.getGameTime() % LOOK_EVERY != 0 || !RaidDens.live() || level.isNight()) {
			return;
		}
		Data data = Data.get(level);
		long day = Chronicle.day(level);
		Set<BlockPos> halls = new LinkedHashSet<>();
		for (RaidDens.Den den : RaidDens.Sites.get(level).dens()) {
			if (den.active()) {
				RaidDens.hallNear(level, den.pos()).ifPresent(halls::add);
			}
		}
		for (BlockPos hall : halls) {
			Long last = data.last.get(hall.asLong());
			if (last != null && day - last < EVERY_DAYS) {
				continue;
			}
			Result result = challenge(level, hall, level.getRandom().nextFloat(), level.getRandom());
			if (result == Result.WON || result == Result.LOST) {
				data.last.put(hall.asLong(), day);
				data.setDirty();
			}
		}
	}

	/** The village's best free trainer who may challenge a den: a Leader or a Master, not tired; the highest rank first. */
	public static Optional<Villager> champion(ServerLevel level, BlockPos hall) {
		long now = level.getGameTime();
		Data data = Data.get(level);
		return level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && Trainers.isTrainer(v)
				&& (Trainers.isLeader(v) || Trainers.tier(v) >= 5) && data.tired.getOrDefault(v.getUUID(), 0L) <= now)
			.stream().max(Comparator.comparingInt(Trainers::tier).thenComparing(v -> v.getUUID()));
	}

	/** The highest den tier {@code trainer} may take on. */
	public static int maxTier(Villager trainer) {
		return Trainers.tier(trainer) >= 5 ? MASTER_MAX : LEADER_MAX;
	}

	/** The den {@code trainer} would challenge: active, within their reach, no player at it; the highest tier, then the nearest. */
	public static Optional<RaidDens.Den> target(ServerLevel level, BlockPos hall, Villager trainer) {
		int max = maxTier(trainer);
		return RaidDens.near(level, hall).stream()
			.filter(d -> d.active() && d.tier() >= 1 && d.tier() <= max && !playerAt(level, d.pos()))
			.max(Comparator.comparingInt(RaidDens.Den::tier).thenComparing(d -> -d.pos().distSqr(hall)));
	}

	static boolean playerAt(ServerLevel level, BlockPos den) {
		Vec3 at = Vec3.atCenterOf(den);
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && player.position().distanceToSqr(at) <= PLAYER_NEAR * PLAYER_NEAR) {
				return true;
			}
		}
		return false;
	}

	/** The chance a trainer of {@code tier} beats a den of {@code denTier}: 45% at one tier over it, 15% more a tier. */
	public static float chance(int tier, int denTier) {
		return Math.max(0.1f, Math.min(0.9f, 0.45f + 0.15f * (tier - denTier - 1)));
	}

	/**
	 * The village round {@code hall} challenges a den now: {@code roll} (0 to 1) settles it against {@link #chance}, and
	 * {@code random} rolls the loot (both fixed in tests).
	 */
	public static Result challenge(ServerLevel level, BlockPos hall, float roll, RandomSource random) {
		Optional<Villager> found = champion(level, hall);
		if (found.isEmpty()) {
			return Result.NO_TRAINER;
		}
		Villager trainer = found.get();
		Optional<RaidDens.Den> target = target(level, hall, trainer);
		if (target.isEmpty()) {
			return Result.NO_DEN;
		}
		RaidDens.Den den = target.get();
		Component denName = Component.translatable("message.aliveworkplace.den_challenge.den", den.tier(), RaidDens.typeName(den.type()));
		tell(level, hall, den.pos(), Component.translatable("message.aliveworkplace.den_challenge.off", Trainers.title(trainer), denName,
			io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.where(hall, den.pos())).withStyle(ChatFormatting.AQUA));
		ModAttachments.TRAINER_BATTLES.set(trainer, ModAttachments.TRAINER_BATTLES.getOrElse(trainer, 0) + 1);
		boolean won = roll < chance(Trainers.tier(trainer), den.tier());
		BuilderLevels.addXp(level, trainer, Trainers.XP_PER_BATTLE + (won ? Trainers.XP_FOR_WIN : 0), null);
		if (!won) {
			Data data = Data.get(level);
			data.tired.put(trainer.getUUID(), level.getGameTime() + TIRED);
			data.setDirty();
			Chronicle.atHall(level, hall, Chronicle.Kind.CUP, Component.translatable("chronicle.aliveworkplace.den_challenge.lost", Trainers.title(trainer), denName));
			tell(level, hall, den.pos(), Component.translatable("message.aliveworkplace.den_challenge.lost", Trainers.title(trainer), denName).withStyle(ChatFormatting.YELLOW));
			return Result.LOST;
		}
		int count = 0;
		List<ItemStack> loot = loot(level, den, random);
		List<BlockPos> store = VillageNeeds.store(level, hall);
		for (ItemStack stack : loot) {
			count += stack.getCount();
			ItemStack rest = SupplyContainers.insert(level, store, stack);
			if (!rest.isEmpty()) {
				level.addFreshEntity(new ItemEntity(level, hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 0.5, rest));
			}
		}
		quiet(level, den.pos());
		RaidDens.near(level, hall); // the den reads resting now
		Chronicle.atHall(level, hall, Chronicle.Kind.CUP, Component.translatable("chronicle.aliveworkplace.den_challenge.won", Trainers.title(trainer), denName, count));
		tell(level, hall, den.pos(), Component.translatable("message.aliveworkplace.den_challenge.won", Trainers.title(trainer), denName, count).withStyle(ChatFormatting.GOLD));
		return Result.WON;
	}

	/** The den's own tier loot table, rolled. */
	public static List<ItemStack> loot(ServerLevel level, RaidDens.Den den, RandomSource random) {
		if (den.tier() < 1 || den.tier() > RaidDens.TIERS.size()) {
			return List.of();
		}
		ResourceKey<LootTable> key = ResourceKey.create(Registries.LOOT_TABLE,
			ResourceLocation.fromNamespaceAndPath(RaidDens.DEN.getNamespace(), "raid/tier/" + RaidDens.TIERS.get(den.tier() - 1)));
		try {
			return RaidDenLoot.EXTENSION.call(l -> l.roll(level, key, random), List.<ItemStack>of());
		} catch (RuntimeException e) { // a newer Raid Dens whose tables read something else: no loot, but no crash
			AliveWorkplace.LOG.warn("Alive Workplace: can't roll {} for a den challenge: {}", key.location(), e.toString());
			return List.of();
		}
	}

	/** The den goes quiet: {@code is_active} false (its own reset cycle brings it back; {@code can_reset} untouched). */
	static void quiet(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Property<?> active = state.getBlock().getStateDefinition().getProperty("is_active");
		if (active != null) {
			level.setBlockAndUpdate(pos, set(state, active, "false"));
		}
	}

	private static <T extends Comparable<T>> BlockState set(BlockState state, Property<T> property, String value) {
		return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
	}

	/** Tells the players in the village or by the den. */
	static void tell(ServerLevel level, BlockPos hall, BlockPos den, Component message) {
		var village = VillageHalls.area(hall);
		for (ServerPlayer player : level.players()) {
			if (village.contains(player.position()) || player.position().closerThan(Vec3.atCenterOf(den), 32)) {
				player.sendSystemMessage(message);
			}
		}
	}

	/** Whether {@code trainer} is home tired from a lost challenge. */
	public static boolean tired(ServerLevel level, UUID trainer) {
		return Data.get(level).tired.getOrDefault(trainer, 0L) > level.getGameTime();
	}

	/** Each village's last challenge day and its tired trainers ({@code aliveworkplace_den_challenges}). */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_den_challenges";
		final Map<Long, Long> last = new HashMap<>();
		final Map<UUID, Long> tired = new HashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		/** The day the village round {@code hall} last challenged a den, or -1. */
		public long last(BlockPos hall) {
			return last.getOrDefault(hall.asLong(), -1L);
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag halls = new ListTag();
			last.forEach((hall, day) -> {
				CompoundTag h = new CompoundTag();
				h.putLong("hall", hall);
				h.putLong("lastDenChallenge", day);
				halls.add(h);
			});
			tag.put("halls", halls);
			ListTag tiredList = new ListTag();
			tired.forEach((id, until) -> {
				CompoundTag t = new CompoundTag();
				Nbt.putUuid(t, "trainer", id);
				t.putLong("tiredUntil", until);
				tiredList.add(t);
			});
			tag.put("tired", tiredList);
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag halls = Nbt.getList(tag, "halls", Tag.TAG_COMPOUND);
			for (int i = 0; i < halls.size(); i++) {
				CompoundTag h = Nbt.compoundAt(halls, i);
				data.last.put(Nbt.getLong(h, "hall"), Nbt.getLong(h, "lastDenChallenge"));
			}
			ListTag tiredList = Nbt.getList(tag, "tired", Tag.TAG_COMPOUND);
			for (int i = 0; i < tiredList.size(); i++) {
				CompoundTag t = Nbt.compoundAt(tiredList, i);
				if (Nbt.hasUuid(t, "trainer")) {
					data.tired.put(Nbt.getUuid(t, "trainer"), Nbt.getLong(t, "tiredUntil"));
				}
			}
			return data;
		}
	}
}

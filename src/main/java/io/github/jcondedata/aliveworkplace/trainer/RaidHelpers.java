package io.github.jcondedata.aliveworkplace.trainer;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.cup.CupBattlers;
import io.github.jcondedata.aliveworkplace.cup.CupBout;
import io.github.jcondedata.aliveworkplace.guard.RaidDens;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Walker;
import io.github.jcondedata.aliveworkplace.work.Words;
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
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Raid helpers (ROADMAP 38.1a, replacing the village's solo den challenges of 38.4 and the Den Wardens of 38.3; owner,
 * 2026-10-19: "raid dens must not be done by villagers alone; the player can hire villagers to assist in the raid").
 * <ul>
 * <li>On the hall's Defence page, next to an active den the village knows ({@link RaidDens}), a player hires the
 * village's free trainers one at a time ({@link #PRICE} emeralds each, or CobbleDollars), the best first, at most
 * {@link #CAP} at once. They follow the player.</li>
 * <li>When the player joins a raid by that den (Raid Dens' own {@code raids_joined} or {@code raids_hosted} statistic,
 * awarded every raid, within {@link #NEAR} blocks of it or a minute after being there), each helper cheers once: one of
 * the raid mod's cheer items. They then stand by the den, and, when {@link RaidAssist} reaches the raid, their Pokémon
 * wear the boss down every {@link #STRIKE_EVERY} ticks, by rank and team against the den's tier, never below
 * {@link #FLOOR} of its health (the last blows are the player's). A helper more than one rank under the den's tier
 * only cheers.</li>
 * <li>A won raid (the {@code tier_<n>_completed} statistic, or the raid's own state) goes in the chronicle; won, lost or
 * a day after the hiring, the helpers walk home.</li>
 * </ul>
 */
public final class RaidHelpers {
	/** Switched by {@code denHelpers} (behind M38's gate). */
	public static boolean ENABLED = false;
	/** Emeralds a helper costs ({@code denHelperPrice}). */
	public static int PRICE = 5;

	/** What a helper costs in CobbleDollars, when those are the money in use ({@link #PRICE} emeralds' worth). */
	public static long dollars() {
		return (long) PRICE * Money.DOLLARS_PER_EMERALD;
	}

	/**
	 * A helper's price as the hire charges it: "500 CobbleDollars" or "5 emeralds" (B414: the Defence page's button and the
	 * hire both read this, so the button never names a currency the click doesn't take).
	 */
	public static Component price() {
		return Money.describe(dollars(), PRICE);
	}
	/** Helpers a player may have at once. */
	public static final int CAP = 3;
	/** A raid joined this close to the den is that den's. */
	public static final double NEAR = 16;
	/** ...or this long after the player was by it (Raid Dens can take the raiders to a den of its own). */
	public static final long RECENT = 1200;
	/** A hiring lasts a day at most. */
	public static final long STAY = 24000;
	/** The helpers' Pokémon strike this often. */
	public static final int STRIKE_EVERY = 100;
	/** The helpers look for the player's raid this often. */
	static final int LOOK_EVERY = 20;
	/** What one helper's strike takes off a boss of their own rank's tier, as a share of its full health. */
	public static final float BASE = 0.006f;
	/** Helpers never bring the boss below this share of its health. */
	public static final float FLOOR = 0.25f;
	/** Home is reached this close. */
	static final double HOME = 4;
	/** A helper walking home that hasn't arrived after this long is moved there. */
	static final long HOMEWARD_MAX = 2400;
	static final float PACE = 0.7f;
	public static final List<String> CHEERS = List.of("cheer_attack", "cheer_defense", "cheer_heal");
	public static final String JOINED = "raids_joined";
	public static final String HOSTED = "raids_hosted";

	private RaidHelpers() {
	}

	public static void init() {
		Platform.get().onServerTick(RaidHelpers::tick);
	}

	// ---- hiring -----------------------------------------------------------------------------------------------------

	/** The players' hirings of every level. */
	public static Optional<Hire> hireOf(MinecraftServer server, UUID player) {
		for (ServerLevel level : server.getAllLevels()) {
			Hire hire = Data.get(level).hires.get(player);
			if (hire != null) {
				return Optional.of(hire);
			}
		}
		return Optional.empty();
	}

	/** Whether {@code villager} is hired by a player now, or walking home from it. */
	public static boolean busy(Villager villager) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return false;
		}
		Data data = Data.get(level);
		return data.homeward.containsKey(villager.getUUID()) || data.hires.values().stream().anyMatch(h -> h.helpers.contains(villager.getUUID()));
	}

	/** The village's trainers free to help, the best first. */
	public static List<Villager> free(ServerLevel level, BlockPos hall) {
		Data data = Data.get(level);
		Set<UUID> taken = new HashSet<>(data.homeward.keySet());
		data.hires.values().forEach(h -> taken.addAll(h.helpers));
		List<Villager> free = new ArrayList<>(level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall),
			v -> v.isAlive() && Trainers.isTrainer(v) && !taken.contains(v.getUUID())));
		free.sort(Comparator.comparingInt(Trainers::tier).reversed().thenComparing(Villager::getUUID));
		return free;
	}

	/** {@code player} hires one more of the village's trainers for the den at {@code denPos}: what to tell them. */
	public static Component hire(ServerLevel level, BlockPos hall, BlockPos denPos, ServerPlayer player) {
		if (!ENABLED) {
			return Component.translatable("message.aliveworkplace.raid_helpers.off").withStyle(ChatFormatting.YELLOW);
		}
		Optional<RaidDens.Den> den = RaidDens.near(level, hall).stream().filter(d -> d.pos().equals(denPos)).findFirst();
		if (den.isEmpty() || !den.get().active()) {
			return Component.translatable("message.aliveworkplace.raid_helpers.resting").withStyle(ChatFormatting.YELLOW);
		}
		Data data = Data.get(level);
		Hire hire = data.hires.get(player.getUUID());
		if (hire == null && hireOf(level.getServer(), player.getUUID()).isPresent() || hire != null && !hire.den.equals(denPos)) {
			return Component.translatable("message.aliveworkplace.raid_helpers.other_den").withStyle(ChatFormatting.YELLOW);
		}
		if (hire != null && hire.helpers.size() >= CAP) {
			return Component.translatable("message.aliveworkplace.raid_helpers.cap", CAP).withStyle(ChatFormatting.YELLOW);
		}
		if (hire != null && hire.joined) {
			return Component.translatable("message.aliveworkplace.raid_helpers.under_way").withStyle(ChatFormatting.YELLOW);
		}
		List<Villager> free = free(level, hall);
		if (free.isEmpty()) {
			return Component.translatable("message.aliveworkplace.raid_helpers.none").withStyle(ChatFormatting.YELLOW);
		}
		if (!Money.charge(player, dollars(), PRICE)) {
			return Component.translatable("message.aliveworkplace.raid_helpers.cant_afford", price()).withStyle(ChatFormatting.RED);
		}
		if (hire == null) {
			hire = new Hire(player.getUUID(), hall.immutable(), denPos.immutable(), den.get().tier(), den.get().type(), level.getGameTime() + STAY);
			data.hires.put(player.getUUID(), hire);
		}
		Villager helper = free.get(0);
		hire.helpers.add(helper.getUUID());
		data.setDirty();
		if (helper.isSleeping()) {
			helper.stopSleeping();
		}
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, helper.getX(), helper.getY() + 1.2, helper.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, helper.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1f);
		return Component.translatable("message.aliveworkplace.raid_helpers.hired", Trainers.title(helper), denName(hire),
			VillageHallScreen.where(hall, denPos), hire.helpers.size(), CAP).withStyle(ChatFormatting.GREEN);
	}

	static Component denName(Hire hire) {
		return Component.translatable("message.aliveworkplace.raid_helpers.den", hire.tier, RaidDens.typeName(hire.type));
	}

	// ---- the raid ---------------------------------------------------------------------------------------------------

	/** A statistic Raid Dens gave {@code player}: a raid joined or hosted, or a tier's raid won (every raid, unlike its advancements). */
	public static void statAwarded(ServerPlayer player, ResourceLocation stat) {
		if (!ENABLED || !stat.getNamespace().equals(RaidDens.DEN.getNamespace()) || player.getServer() == null) {
			return;
		}
		Optional<Hire> found = hireOf(player.getServer(), player.getUUID());
		if (found.isEmpty()) {
			return;
		}
		Hire hire = found.get();
		ServerLevel level = levelOf(player.getServer(), hire);
		if (level == null) {
			return;
		}
		String path = stat.getPath();
		if ((path.equals(JOINED) || path.equals(HOSTED)) && (nearDen(player, level, hire) || level.getGameTime() - hire.lastNear <= RECENT)) {
			// B476: Raid Dens awards this as the player enters, so the raid is usually running already; keep its id, or the
			// helpers never strike its boss nor see it end (their own look below keeps trying when it isn't found yet).
			joined(level, hire, player, raidOf(player).map(RaidAssist.Raid::id).orElse(null));
		} else if (path.endsWith("_completed") && hire.joined) {
			int tier = RaidDens.TIERS.indexOf(path.substring(0, path.length() - "_completed".length())) + 1;
			if (tier > 0) {
				won(level, hire);
			}
		}
	}

	private static Optional<RaidAssist.Raid> raidOf(ServerPlayer player) {
		return RaidAssist.EXTENSION.call(a -> a.raidOf(player), Optional.<RaidAssist.Raid>empty());
	}

	private static ServerLevel levelOf(MinecraftServer server, Hire hire) {
		for (ServerLevel level : server.getAllLevels()) {
			if (Data.get(level).hires.get(hire.player) == hire) {
				return level;
			}
		}
		return null;
	}

	static boolean nearDen(ServerPlayer player, ServerLevel level, Hire hire) {
		return player.level() == level && player.position().distanceToSqr(Vec3.atCenterOf(hire.den)) <= NEAR * NEAR;
	}

	/** The hiring player joined a raid at the den: each helper cheers, once a raid; {@code raid} is the raid's id when known. */
	public static void joined(ServerLevel level, Hire hire, ServerPlayer player, UUID raid) {
		Data data = Data.get(level);
		if (raid != null && hire.raid == null) {
			hire.raid = raid;
			data.setDirty();
		}
		if (hire.joined) {
			return;
		}
		hire.joined = true;
		hire.nextStrike = level.getGameTime() + STRIKE_EVERY;
		data.setDirty();
		int i = 0;
		for (Villager helper : helpers(level, hire)) {
			String pick = CHEERS.get(i++ % CHEERS.size());
			Optional<Item> cheer = BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath(RaidDens.DEN.getNamespace(), pick));
			if (cheer.isEmpty()) {
				continue;
			}
			ItemStack stack = new ItemStack(cheer.get());
			player.sendSystemMessage(Words.articled("message.aliveworkplace.raid_helpers.cheer", 1, Trainers.title(helper), stack.getHoverName())
				.withStyle(ChatFormatting.LIGHT_PURPLE));
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			level.sendParticles(ParticleTypes.NOTE, helper.getX(), helper.getY() + 2.2, helper.getZ(), 3, 0.3, 0.2, 0.3, 0.5);
		}
		player.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6f, 1.4f);
		player.sendSystemMessage(Component.translatable(RaidAssist.EXTENSION.present()
			? "message.aliveworkplace.raid_helpers.joined_strike" : "message.aliveworkplace.raid_helpers.joined").withStyle(ChatFormatting.AQUA));
	}

	/** How hard {@code helper}'s Pokémon hit a boss of {@code denTier}: a share of its full health a strike (0: too strong for them). */
	public static float strength(Villager helper, int denTier) {
		int tier = Trainers.tier(helper);
		if (tier + 1 < denTier) {
			return 0f;
		}
		List<CupBout.Fighter> team = CupBattlers.EXTENSION.call(x -> x.homeTeam(helper.getUUID(), tier), List.of());
		double levels = team.stream().mapToInt(CupBout.Fighter::level).average().orElse(0);
		return (float) (BASE * tier / Math.max(1, denTier) * (1 + levels / 100.0));
	}

	/** The helpers' Pokémon strike the boss of {@code raid}: what share of its full health came off. */
	public static float strike(ServerLevel level, Hire hire, RaidAssist.Raid raid) {
		if (raid.state() != RaidAssist.State.RUNNING || raid.maxHealth() <= 0) {
			return 0f;
		}
		float share = 0f;
		List<Villager> helpers = helpers(level, hire);
		for (Villager helper : helpers) {
			share += strength(helper, hire.tier);
		}
		share = Math.min(share, raid.health() / raid.maxHealth() - FLOOR);
		if (share <= 0f) {
			return 0f;
		}
		raid.hurt(share);
		hire.dealt += share;
		Data.get(level).setDirty();
		for (Villager helper : helpers) {
			helper.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			level.sendParticles(ParticleTypes.CRIT, helper.getX(), helper.getY() + 1.5, helper.getZ(), 6, 0.4, 0.4, 0.4, 0.2);
		}
		return share;
	}

	/**
	 * The chronicle's "Ash and Trainer Mira's band beat the tier 3 Fire raid den", or "Ash and the village's trainers
	 * beat ..." when none of the helpers is found (B608: never "trainers's band").
	 */
	public static Component wonLine(Component who, @org.jetbrains.annotations.Nullable Component leader, int tier, Component type) {
		return leader == null ? Component.translatable("chronicle.aliveworkplace.raid_helpers.won_no_band", who, tier, type)
			: Component.translatable("chronicle.aliveworkplace.raid_helpers.won", who, leader, tier, type);
	}

	/** The raid was won: the chronicle tells it, the helpers get their battle XP and walk home. */
	public static void won(ServerLevel level, Hire hire) {
		List<Villager> helpers = helpers(level, hire);
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(hire.player);
		Component who = player != null ? player.getDisplayName() : Component.translatable("message.aliveworkplace.raid_helpers.a_player");
		Chronicle.atHall(level, hire.hall, Chronicle.Kind.CUP, wonLine(who, helpers.isEmpty() ? null : Trainers.title(helpers.get(0)), hire.tier,
			RaidDens.typeName(hire.type)));
		for (Villager helper : helpers) {
			ModAttachments.TRAINER_BATTLES.set(helper, ModAttachments.TRAINER_BATTLES.getOrElse(helper, 0) + 1);
			BuilderLevels.addXp(level, helper, Trainers.XP_PER_BATTLE + Trainers.XP_FOR_WIN, null);
		}
		if (player != null) {
			player.sendSystemMessage(Component.translatable("message.aliveworkplace.raid_helpers.won", denName(hire)).withStyle(ChatFormatting.GOLD));
		}
		release(level, hire);
	}

	/** The hiring is over (a lost raid, or the day is up): the helpers walk home; {@code key} is what the player is told. */
	public static void over(ServerLevel level, Hire hire, String key) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(hire.player);
		if (player != null) {
			player.sendSystemMessage(Component.translatable(key, denName(hire)).withStyle(ChatFormatting.YELLOW));
		}
		release(level, hire);
	}

	static void release(ServerLevel level, Hire hire) {
		Data data = Data.get(level);
		data.hires.remove(hire.player);
		long now = level.getGameTime();
		for (UUID id : hire.helpers) {
			data.homeward.put(id, now);
			if (level.getEntity(id) instanceof Villager v) {
				v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			}
		}
		data.setDirty();
	}

	// ---- every tick -------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 5 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Data data = Data.get(level);
			if (data.hires.isEmpty() && data.homeward.isEmpty()) {
				continue;
			}
			if (!ENABLED) { // switched off: everyone goes home now
				List.copyOf(data.hires.values()).forEach(h -> release(level, h));
			}
			for (Hire hire : List.copyOf(data.hires.values())) {
				tick(level, hire);
			}
			homeward(level);
		}
	}

	/** One look at a hiring (public for tests that drive it). */
	public static void tick(ServerLevel level, Hire hire) {
		// B415: a helper that isn't loaded (its chunk unloaded, or the server just started) stays hired; only one that is
		// here and no longer a trainer (or dying) leaves the hiring. The hiring itself ends on the raid, the day or the switch.
		if (hire.helpers.removeIf(id -> level.getEntity(id) instanceof Villager v && (!v.isAlive() || !Trainers.isTrainer(v)))) {
			Data.get(level).setDirty();
		}
		if (hire.helpers.isEmpty()) {
			Data.get(level).hires.remove(hire.player);
			Data.get(level).setDirty();
			return;
		}
		List<Villager> helpers = helpers(level, hire);
		long now = level.getGameTime();
		if (now >= hire.until) {
			over(level, hire, "message.aliveworkplace.raid_helpers.day_over");
			return;
		}
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(hire.player);
		if (player != null && nearDen(player, level, hire)) {
			hire.lastNear = now;
		}
		// The helpers look for the player's raid once a second: before the join (by the den, or a minute after), and after a
		// join by the statistic that found no raid yet. B476: timed by the hiring's own clock, since this look runs only
		// every fifth server tick and a gameTime % 20 test then never fires in a world whose two counters are out of step.
		boolean looking = hire.joined ? hire.raid == null : now - hire.lastNear <= RECENT;
		if (looking && player != null && now >= hire.nextLook) {
			hire.nextLook = now + LOOK_EVERY;
			Optional<RaidAssist.Raid> raid = raidOf(player);
			if (raid.isPresent()) {
				joined(level, hire, player, raid.get().id());
			}
		}
		if (hire.joined) {
			if (hire.raid != null && now >= hire.nextStrike) {
				hire.nextStrike = now + STRIKE_EVERY;
				Optional<RaidAssist.Raid> raid = RaidAssist.EXTENSION.call(a -> a.raid(level.getServer(), hire.raid), Optional.<RaidAssist.Raid>empty());
				if (raid.isEmpty()) {
					over(level, hire, "message.aliveworkplace.raid_helpers.raid_over");
					return;
				}
				switch (raid.get().state()) {
					case WON -> {
						won(level, hire);
						return;
					}
					case LOST -> {
						over(level, hire, "message.aliveworkplace.raid_helpers.lost");
						return;
					}
					default -> strike(level, hire, raid.get());
				}
			}
			for (int i = 0; i < helpers.size(); i++) {
				stand(level, helpers.get(i), hire, i);
			}
			return;
		}
		if (player != null && player.level() == level && player.isAlive() && !player.isSpectator()) {
			for (Villager helper : helpers) {
				follow(level, helper, player);
			}
		}
	}

	/** Before the raid: the helper keeps up with the player, and catches up when left far behind. */
	static void follow(ServerLevel level, Villager helper, ServerPlayer player) {
		if (helper.isSleeping()) {
			helper.stopSleeping();
		}
		WorkerStatus.set(helper, Trainers.title(helper), -1f, Component.translatable("message.aliveworkplace.raid_helpers.state.following",
			player.getDisplayName()).withStyle(ChatFormatting.AQUA));
		double d = helper.distanceTo(player);
		if (d > 24) {
			BlockPos spot = Walker.standingSpot(level, player.blockPosition(), player.blockPosition(), 4.0);
			BlockPos at = spot != null ? spot : player.blockPosition();
			level.sendParticles(ParticleTypes.CLOUD, helper.getX(), helper.getY() + 0.5, helper.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
			helper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
			helper.getNavigation().stop();
			helper.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
			return;
		}
		if (d > 3) {
			helper.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(player, false), d > 10 ? 0.8f : 0.6f, 2));
		}
	}

	/** During the raid: the helper stands by the den, a few blocks off it, facing it. */
	static void stand(ServerLevel level, Villager helper, Hire hire, int index) {
		if (helper.isSleeping()) {
			helper.stopSleeping();
		}
		WorkerStatus.set(helper, Trainers.title(helper), -1f, Component.translatable("message.aliveworkplace.raid_helpers.state.raiding")
			.withStyle(ChatFormatting.LIGHT_PURPLE));
		BlockPos spot = spot(hire.den, index);
		if (flat(helper.blockPosition(), spot) > 4) {
			if (flat(helper.blockPosition(), hire.den) > 48 * 48) {
				BlockPos at = Walker.standingSpot(level, spot, spot, 3.0);
				BlockPos to = at != null ? at : spot;
				helper.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
			} else {
				Walker.requestWalk(helper, Scouts.waypoint(level, helper.blockPosition(), spot, 0), PACE, 1, 0);
			}
			return;
		}
		helper.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		helper.getNavigation().stop();
		helper.getLookControl().setLookAt(hire.den.getX() + 0.5, hire.den.getY() + 0.5, hire.den.getZ() + 0.5);
	}

	/** Where helper {@code index} stands: three blocks off the crystal, round it. */
	static BlockPos spot(BlockPos den, int index) {
		int[][] round = {{3, 0}, {-3, 0}, {0, 3}, {0, -3}};
		int[] at = round[index % round.length];
		return den.offset(at[0], 0, at[1]);
	}

	/** Released helpers walk home: their work station, or the hall. */
	static void homeward(ServerLevel level) {
		Data data = Data.get(level);
		long now = level.getGameTime();
		for (Map.Entry<UUID, Long> e : List.copyOf(data.homeward.entrySet())) {
			if (!(level.getEntity(e.getKey()) instanceof Villager helper) || !helper.isAlive()) {
				if (now - e.getValue() > HOMEWARD_MAX) { // not loaded for long: they'll be home by now
					data.homeward.remove(e.getKey());
					data.setDirty();
				}
				continue;
			}
			BlockPos home = home(level, helper);
			if (flat(helper.blockPosition(), home) <= HOME * HOME) {
				data.homeward.remove(e.getKey());
				data.setDirty();
				continue;
			}
			if (now - e.getValue() > HOMEWARD_MAX) {
				BlockPos at = Walker.standingSpot(level, home, home, 4.0);
				BlockPos to = at != null ? at : home.above();
				level.sendParticles(ParticleTypes.CLOUD, helper.getX(), helper.getY() + 0.5, helper.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
				helper.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
				data.homeward.remove(e.getKey());
				data.setDirty();
				continue;
			}
			WorkerStatus.set(helper, Trainers.title(helper), -1f, Component.translatable("message.aliveworkplace.raid_helpers.state.home")
				.withStyle(ChatFormatting.GRAY));
			Walker.requestWalk(helper, Scouts.waypoint(level, helper.blockPosition(), home, 0), PACE, 2, 0);
		}
	}

	/** Where a helper lives and works: their Training Post, or the nearest hall. */
	public static BlockPos home(ServerLevel level, Villager helper) {
		return Builders.benchPos(helper).or(() -> VillageHalls.nearest(level, helper.blockPosition())).orElse(helper.blockPosition());
	}

	/** The hiring's helpers that are here and still trainers. */
	public static List<Villager> helpers(ServerLevel level, Hire hire) {
		List<Villager> out = new ArrayList<>();
		for (UUID id : hire.helpers) {
			if (level.getEntity(id) instanceof Villager v && v.isAlive() && Trainers.isTrainer(v)) {
				out.add(v);
			}
		}
		return out;
	}

	/** Whether {@code helper} is walking home from a hiring. */
	public static boolean homeward(ServerLevel level, UUID helper) {
		return Data.get(level).homeward.containsKey(helper);
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	// ---- saved ------------------------------------------------------------------------------------------------------

	/** One player's hiring: for which den, who, until when, and how the raid goes. */
	public static final class Hire {
		public final UUID player;
		public final BlockPos hall;
		public final BlockPos den;
		public final int tier;
		public final String type;
		public final List<UUID> helpers = new ArrayList<>();
		public long until;
		public boolean joined;
		public UUID raid;
		public long lastNear = Long.MIN_VALUE / 2;
		public long nextStrike;
		/** When the helpers next look for the player's raid (not saved: after a restart they look at once). */
		public long nextLook;
		public float dealt;

		Hire(UUID player, BlockPos hall, BlockPos den, int tier, String type, long until) {
			this.player = player;
			this.hall = hall;
			this.den = den;
			this.tier = tier;
			this.type = type;
			this.until = until;
		}
	}

	/**
	 * The hirings of a level and the helpers walking home ({@code aliveworkplace_raid_helpers}). The saved data of the
	 * features this replaced ({@code aliveworkplace_den_wardens}, {@code aliveworkplace_den_challenges}) is left on disk
	 * unread: a warden posted then is simply a guard again, and nothing in it is needed.
	 */
	public static final class Data extends SavedData {
		private static final String NAME = "aliveworkplace_raid_helpers";
		final Map<UUID, Hire> hires = new LinkedHashMap<>();
		final Map<UUID, Long> homeward = new HashMap<>();

		public static Data get(ServerLevel level) {
			return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Data::new, Data::load, null), NAME);
		}

		public java.util.Collection<Hire> hires() {
			return hires.values();
		}

		@Override
		public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
			ListTag list = new ListTag();
			for (Hire h : hires.values()) {
				CompoundTag t = new CompoundTag();
				Nbt.putUuid(t, "player", h.player);
				t.putLong("hall", h.hall.asLong());
				t.putLong("den", h.den.asLong());
				t.putInt("tier", h.tier);
				t.putString("type", h.type);
				ListTag ids = new ListTag();
				h.helpers.forEach(id -> ids.add(StringTag.valueOf(id.toString())));
				t.put("helpers", ids);
				t.putLong("until", h.until);
				t.putBoolean("joined", h.joined);
				if (h.raid != null) {
					Nbt.putUuid(t, "raid", h.raid);
				}
				t.putFloat("dealt", h.dealt);
				list.add(t);
			}
			tag.put("hires", list);
			ListTag home = new ListTag();
			homeward.forEach((id, since) -> {
				CompoundTag t = new CompoundTag();
				Nbt.putUuid(t, "helper", id);
				t.putLong("since", since);
				home.add(t);
			});
			tag.put("homeward", home);
			return tag;
		}

		public static Data load(CompoundTag tag, HolderLookup.Provider registries) {
			Data data = new Data();
			ListTag list = Nbt.getList(tag, "hires", Tag.TAG_COMPOUND);
			for (int i = 0; i < list.size(); i++) {
				CompoundTag t = Nbt.compoundAt(list, i);
				if (!Nbt.hasUuid(t, "player")) {
					continue;
				}
				Hire h = new Hire(Nbt.getUuid(t, "player"), BlockPos.of(Nbt.getLong(t, "hall")), BlockPos.of(Nbt.getLong(t, "den")),
					Nbt.getInt(t, "tier"), Nbt.getString(t, "type"), Nbt.getLong(t, "until"));
				ListTag ids = Nbt.getList(t, "helpers", Tag.TAG_STRING);
				for (int j = 0; j < ids.size(); j++) {
					try {
						h.helpers.add(UUID.fromString(Nbt.stringAt(ids, j)));
					} catch (IllegalArgumentException ignored) {
						// not a villager id
					}
				}
				h.joined = Nbt.getBoolean(t, "joined");
				if (Nbt.hasUuid(t, "raid")) {
					h.raid = Nbt.getUuid(t, "raid");
				}
				h.dealt = Nbt.getFloat(t, "dealt");
				data.hires.put(h.player, h);
			}
			ListTag home = Nbt.getList(tag, "homeward", Tag.TAG_COMPOUND);
			for (int i = 0; i < home.size(); i++) {
				CompoundTag t = Nbt.compoundAt(home, i);
				if (Nbt.hasUuid(t, "helper")) {
					data.homeward.put(Nbt.getUuid(t, "helper"), Nbt.getLong(t, "since"));
				}
			}
			return data;
		}
	}
}

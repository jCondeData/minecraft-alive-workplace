package io.github.jcondedata.aliveworkplace.story;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.mc.Players;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Quest tracking (ROADMAP 31.3): each player may track one quest, kept in the overworld's {@code aliveworkplace_stories}
 * so it survives a restart and a change of dimension. A tracked quest is a vanilla boss bar for that player only: the
 * quest's name and its current objective ("Follow the tracks (2/5)"), filling as it goes, gone when the quest ends or is
 * untracked. The bars aren't saved; they're rebuilt from the saved tracking once a second. The same pass checks the
 * {@code reach} objectives (every 2 seconds, one distance check per player and open reach quest of their dimension).
 */
public final class QuestTracker {
	/** One player's tracked quest: where its village is and which quest. */
	public record Track(String dimension, BlockPos hall, UUID quest) {
		CompoundTag save(UUID player) {
			CompoundTag t = new CompoundTag();
			Nbt.putUuid(t, "player", player);
			t.putString("dimension", dimension);
			t.putLong("hall", hall.asLong());
			Nbt.putUuid(t, "quest", quest);
			return t;
		}

		static Track load(CompoundTag t) {
			return new Track(Nbt.getString(t, "dimension"), BlockPos.of(Nbt.getLong(t, "hall")), Nbt.getUuid(t, "quest"));
		}
	}

	/** A tracked quest found again: its level, hall and the quest. */
	public record Found(ServerLevel level, BlockPos hall, Quest quest) {
	}

	private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

	public static void init() {
		Platform.get().onServerTick(server -> {
			int tick = server.getTickCount();
			if (tick % 20 == 0) {
				for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
					refresh(player);
					if (tick % 40 == 0) {
						checkReach(player);
					}
				}
			}
		});
		Platform.get().onPlayerLeave(player -> {
			ServerBossEvent bar = BARS.remove(player.getUUID());
			if (bar != null) {
				bar.removeAllPlayers();
			}
		});
	}

	/** Whether tracking (and the journal's tabs) is on: milestone 31's gate (B76). */
	public static boolean enabled() {
		return Expansions.on(Expansions.M31);
	}

	private static Stories.Data data(MinecraftServer server) {
		return Stories.Data.get(server.overworld());
	}

	/** The quest {@code player} tracks, if it's still open. */
	public static Optional<Found> tracked(ServerPlayer player) {
		Track t = data(player.getServer()).tracked.get(player.getUUID());
		if (t == null) {
			return Optional.empty();
		}
		ResourceLocation dim = ResourceLocation.tryParse(t.dimension());
		ServerLevel level = dim == null ? null : player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim));
		if (level == null) {
			return Optional.empty();
		}
		Stories.Entry e = Stories.Data.get(level).halls.get(t.hall());
		return e == null ? Optional.empty() : e.quests.stream().filter(q -> q.id.equals(t.quest())).findFirst().map(q -> new Found(level, t.hall(), q));
	}

	/** Whether {@code player} tracks the quest {@code quest}. */
	public static boolean isTracked(ServerPlayer player, UUID quest) {
		Track t = data(player.getServer()).tracked.get(player.getUUID());
		return t != null && t.quest().equals(quest);
	}

	/** Tracks {@code quest} of the village round {@code hall} for {@code player} (instead of what they tracked). */
	public static void track(ServerPlayer player, ServerLevel level, BlockPos hall, UUID quest) {
		Stories.Data data = data(player.getServer());
		data.tracked.put(player.getUUID(), new Track(level.dimension().location().toString(), hall.immutable(), quest));
		data.setDirty();
		refresh(player);
	}

	public static void untrack(ServerPlayer player) {
		Stories.Data data = data(player.getServer());
		if (data.tracked.remove(player.getUUID()) != null) {
			data.setDirty();
		}
		refresh(player);
	}

	/** Tracks the quest, or untracks it when it's the one tracked. Returns whether it's tracked now. */
	public static boolean toggle(ServerPlayer player, ServerLevel level, BlockPos hall, UUID quest) {
		if (isTracked(player, quest)) {
			untrack(player);
			return false;
		}
		track(player, level, hall, quest);
		return true;
	}

	/** A quest moved on or ended: the bars of whoever tracks it follow now. */
	static void changed(@Nullable MinecraftServer server, UUID quest) {
		if (server == null) {
			return;
		}
		Stories.Data data = data(server);
		for (Map.Entry<UUID, Track> e : List.copyOf(data.tracked.entrySet())) {
			if (e.getValue().quest().equals(quest)) {
				ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
				if (player != null) {
					refresh(player);
				}
			}
		}
	}

	/** Brings {@code player}'s bar up to date: shown while they track an open quest (and the gate is open), else gone. */
	public static void refresh(ServerPlayer player) {
		Optional<Found> found = enabled() ? tracked(player) : Optional.empty();
		if (found.isEmpty() || found.get().quest().done()) {
			if (enabled() && data(player.getServer()).tracked.containsKey(player.getUUID()) && found.isEmpty()) {
				// The quest ended, expired or was removed: the tracking goes with it.
				Stories.Data data = data(player.getServer());
				data.tracked.remove(player.getUUID());
				data.setDirty();
			}
			ServerBossEvent bar = BARS.remove(player.getUUID());
			if (bar != null) {
				bar.removeAllPlayers();
			}
			return;
		}
		Quest quest = found.get().quest();
		ServerBossEvent bar = BARS.computeIfAbsent(player.getUUID(),
			k -> new ServerBossEvent(Component.empty(), BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS));
		bar.setName(barName(quest));
		bar.setProgress(fill(quest));
		if (!bar.getPlayers().contains(player)) {
			bar.removeAllPlayers(); // a player who rejoined is a new object
			bar.addPlayer(player);
		}
	}

	/** "Bring 16 Bread: Bring 16 Bread (4/16)": the quest's name, its current objective and how far it's got. */
	public static Component barName(Quest quest) {
		int i = Math.max(0, quest.current());
		Objectives.Objective o = quest.objectives.get(i);
		return quest.name.isPresent() && quest.objectives.size() > 1
			? Component.translatable("bossbar.aliveworkplace.quest", quest.title(), o.line(), quest.progress[i], o.need())
			: Component.translatable("bossbar.aliveworkplace.quest_one", quest.name.orElseGet(o::line), quest.progress[i], o.need());
	}

	/** How full the bar is: the share of every objective's steps done. */
	public static float fill(Quest quest) {
		int need = 0;
		int done = 0;
		for (int i = 0; i < quest.objectives.size(); i++) {
			need += quest.objectives.get(i).need();
			done += Math.min(quest.progress[i], quest.objectives.get(i).need());
		}
		return need == 0 ? 1f : Math.max(0f, Math.min(1f, done / (float) need));
	}

	/** {@code player}'s bar, or null with none (tests). */
	@Nullable
	public static ServerBossEvent bar(ServerPlayer player) {
		return BARS.get(player.getUUID());
	}

	/** Moves on the open {@code reach} quests of {@code player}'s dimension whose place they stand within. */
	public static void checkReach(ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}
		ServerLevel level = Players.level(player);
		Stories.Data data = Stories.Data.get(level);
		for (Map.Entry<BlockPos, Stories.Entry> en : List.copyOf(data.halls.entrySet())) {
			for (Quest q : List.copyOf(en.getValue().quests)) {
				int i = q.current();
				if (i >= 0 && q.objectives.get(i) instanceof Objectives.Reach reach && reach.inside(player.blockPosition())
					&& level.getBlockEntity(en.getKey()) instanceof VillageHallBlockEntity entity && Stories.mayHelp(level, en.getKey(), q, player)) {
					Stories.progress(level, en.getKey(), entity, q, i, 1, player);
				}
			}
		}
	}

	private QuestTracker() {
	}
}

package io.github.jcondedata.aliveworkplace.threat;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/**
 * Sieges III: the morning after (ROADMAP 32.6). While a siege lasts every raider's death is counted in its saved data
 * ({@link #onDeath}): to a guard (and which), to a player (who then "fought"), or to anything else. When the siege
 * lifts, at the first dawn after the raid, {@link #write} puts the <b>siege report</b> in the chronicle (kind SIEGE):
 * how many came, how many fell and to whom, whether the gate held, and the hero, the guard with the most kills (the
 * first to get there when two have as many). If the raid was fought off (not fled from at dawn), the players who
 * fought get Hero of the Village for {@link #HERO_TICKS} and the villagers are {@link #HELD_MOOD} happier for
 * {@link #HELD_TICKS} ("we held"). Won or not, for {@link #MENDING_TICKS} (and while a siege lasts) the builders mend
 * defence builds before any other repair ({@code build/Upkeep} asks {@link #defencesFirst}).
 */
public final class SiegeReport {
	public static final int HERO_TICKS = 24000;
	public static final int HELD_TICKS = 24000;
	public static final int HELD_MOOD = 10;
	public static final int MENDING_TICKS = 48000;
	/** A raider that dies this far beyond the village's edge still counts for its siege. */
	private static final int BEYOND = 64;

	/** A living thing died: if it was a raider of a siege under way, the siege counts it. */
	public static void onDeath(ServerLevel level, LivingEntity dead, DamageSource source) {
		if (!dead.getTags().contains(Threats.RAIDER_TAG)) {
			return;
		}
		ThreatData data = ThreatData.get(level);
		double reach = VillageHalls.RADIUS + BEYOND;
		ThreatData.Siege siege = null;
		for (ThreatData.Siege s : data.sieges()) {
			if (!s.over && s.hall.distSqr(dead.blockPosition()) <= reach * reach
				&& (siege == null || s.hall.distSqr(dead.blockPosition()) < siege.hall.distSqr(dead.blockPosition()))) {
				siege = s;
			}
		}
		if (siege == null) {
			return;
		}
		Entity killer = source.getEntity();
		if (killer instanceof Villager guard && !guard.isBaby() && guard.getVillagerData().getProfession() == ModVillagers.GUARD) {
			siege.toGuards++;
			siege.kills.merge(guard.getUUID(), 1, Integer::sum);
			siege.names.put(guard.getUUID(), guard.getName().getString());
		} else if (killer instanceof ServerPlayer player) {
			siege.toPlayers++;
			siege.players.add(player.getUUID());
		} else {
			siege.toOthers++;
		}
		data.setDirty();
	}

	/** The guard with the most kills in {@code siege} (the first to get there on a tie), or null when no guard made one. */
	@Nullable
	public static UUID hero(ThreatData.Siege siege) {
		UUID hero = null;
		int most = 0;
		for (Map.Entry<UUID, Integer> e : siege.kills.entrySet()) {
			if (e.getValue() > most) {
				most = e.getValue();
				hero = e.getKey();
			}
		}
		return hero;
	}

	/** The report of {@code siege} as the chronicle has it. */
	public static Component text(ServerLevel level, ThreatData.Siege siege) {
		int fell = siege.toGuards + siege.toPlayers + siege.toOthers;
		Component gate = Component.translatable(siege.breached ? "chronicle.aliveworkplace.siege_gate_broken" : "chronicle.aliveworkplace.siege_gate_held");
		UUID hero = hero(siege);
		Component best;
		if (hero == null) {
			best = Component.translatable("chronicle.aliveworkplace.siege_no_hero");
		} else {
			Entity living = level.getEntity(hero);
			Component name = living != null ? living.getName() : Component.literal(siege.names.getOrDefault(hero, "?"));
			int kills = siege.kills.get(hero);
			best = Component.translatable(kills == 1 ? "chronicle.aliveworkplace.siege_hero_one" : "chronicle.aliveworkplace.siege_hero", name, kills);
		}
		List<ThreatData.Past> past = ThreatData.get(level).past(siege.hall);
		Optional<Culture> culture = past.isEmpty() ? Optional.empty() : Threats.get(past.get(0).culture());
		String key = culture.map(c -> c.chronicle().key("siege", "chronicle.aliveworkplace.siege")).orElse("chronicle.aliveworkplace.siege");
		return Component.translatable(key, Math.max(siege.came, fell), fell, siege.toGuards, siege.toPlayers, gate, best);
	}

	/**
	 * The morning after {@code siege}: the report goes in the chronicle, the players who fought a siege that was won are
	 * Heroes of the Village for a day, the villagers are glad for a day, and the builders see to the defences first for two.
	 */
	public static void write(ServerLevel level, ThreatData.Siege siege) {
		Chronicle.atHall(level, siege.hall, Chronicle.Kind.SIEGE, text(level, siege));
		long now = level.getGameTime();
		boolean won = !siege.fled;
		if (won) {
			Component name = VillageHalls.name(level, siege.hall);
			for (UUID id : siege.players) {
				ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
				if (player != null) {
					player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, HERO_TICKS, 0, true, true));
					Chat.chat(player, Component.translatable("message.aliveworkplace.siege.heroes", name).withStyle(ChatFormatting.GREEN));
				}
			}
		}
		ThreatData.get(level).after(siege.hall, won ? now + HELD_TICKS : 0, now + MENDING_TICKS);
		Moods.forget();
	}

	/** The mood of a villager whose village held against a siege in the last day ("we held"), or null. */
	@Nullable
	public static LegendPowers.MoodReason heldMood(ServerLevel level, Villager villager) {
		Map<BlockPos, Long> held = ThreatData.get(level).held();
		if (held.isEmpty()) {
			return null;
		}
		long now = level.getGameTime();
		for (Map.Entry<BlockPos, Long> e : held.entrySet()) {
			if (e.getValue() > now && VillageHalls.area(e.getKey()).contains(villager.position())) {
				return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.we_held"), HELD_MOOD);
			}
		}
		return null;
	}

	/** Whether a builder whose bench is at {@code bench} mends defence builds first: their village is under siege, or was in the last two days. */
	public static boolean defencesFirst(ServerLevel level, BlockPos bench) {
		ThreatData data = ThreatData.get(level);
		long now = level.getGameTime();
		for (ThreatData.Siege siege : data.sieges()) {
			if (VillageHalls.area(siege.hall).contains(bench.getCenter())) {
				return true;
			}
		}
		for (Map.Entry<BlockPos, Long> e : data.mending().entrySet()) {
			if (e.getValue() > now && VillageHalls.area(e.getKey()).contains(bench.getCenter())) {
				return true;
			}
		}
		return false;
	}

	private SiegeReport() {
	}
}

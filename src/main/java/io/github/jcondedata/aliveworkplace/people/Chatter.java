package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.Nullable;

/**
 * Chatter: now and then a villager near a player says something, in a line over their head — what their mood is made of
 * (hungry, no bed of their own, a varied diet, a job they like), what's going on in the village (a festival, bandits
 * camped nearby, a raid, illness), or just hello. At most one line every {@link #EVERY} ticks near each player, only
 * from villagers off work (a worker's line shows what they're doing), and only in villages with a Village Hall.
 * {@code villagerChatter} in the config turns it off.
 */
public final class Chatter {
	public static boolean ENABLED = true;
	static final int CHECK_EVERY = 40;
	/** Ticks between lines near one player. */
	public static final int EVERY = 400;
	static final double NEAR = 10;
	/** How many ways there are to say each thing (the lines are {@code chatter.aliveworkplace.<topic>.<n>}). */
	static final Map<String, Integer> VARIANTS = Map.ofEntries(Map.entry("hello", 3), Map.entry("festival_on", 2),
		Map.entry("festival_today", 2), Map.entry("festival_after", 2), Map.entry("bandits", 2), Map.entry("raid", 2),
		Map.entry("child", 2), Map.entry("ill", 2), Map.entry("hungry", 2), Map.entry("fed", 2), Map.entry("no_bed", 2),
		Map.entry("bed", 1), Map.entry("job", 2), Map.entry("no_job", 2), Map.entry("varied_diet", 1), Map.entry("same_food", 2),
		Map.entry("decorations", 2), Map.entry("company", 1), Map.entry("cheerful", 1),
		Map.entry("married", 2), Map.entry("courting", 2), Map.entry("mourning", 1),
		Map.entry("legend_here", 2), Map.entry("legend_guest", 2), Map.entry("legend_strike", 1), Map.entry("legend_self", 1),
		// The village's edicts in force and reformed, a rush, a tonic, a guild and the village's colours (30.21)
		Map.entry("edict_long_shifts", 2), Map.entry("edict_free_bread", 2), Map.entry("edict_large_families", 2),
		Map.entry("edict_open_gates", 2), Map.entry("edict_festival_season", 2), Map.entry("edict_tithe", 2),
		Map.entry("edict_curfew", 2), Map.entry("edict_conscription", 2),
		Map.entry("reformed_long_shifts", 2), Map.entry("reformed_free_bread", 2), Map.entry("reformed_large_families", 2),
		Map.entry("reformed_open_gates", 2), Map.entry("reformed_festival_season", 2), Map.entry("reformed_tithe", 2),
		Map.entry("reformed_curfew", 2), Map.entry("reformed_conscription", 2),
		Map.entry("rush", 2), Map.entry("tonic", 2), Map.entry("guild", 2), Map.entry("colours", 2),
		// A household's rise or fall (34.6), from the mood reason
		Map.entry("class_rose", 3), Map.entry("class_fell", 3));
	private static final Map<UUID, Long> LAST = new HashMap<>();

	public static void init() {
		Platform.get().onLevelTick(level -> {
			if (ENABLED && level.getGameTime() % CHECK_EVERY == 0) {
				tick(level);
			}
		});
	}

	/** Whether a villager has said something to {@code player} since the server started. */
	public static boolean spokeTo(ServerPlayer player) {
		return LAST.containsKey(player.getUUID());
	}

	static void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			Long last = LAST.get(player.getUUID());
			if (last != null && now - last < EVERY || level.random.nextFloat() > 0.35f) {
				continue;
			}
			List<Villager> near = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(NEAR, 4, NEAR),
				v -> v.isAlive() && !v.isSleeping() && offWork(v, now) && player.hasLineOfSight(v));
			if (near.isEmpty()) {
				continue;
			}
			Villager speaker = near.get(level.random.nextInt(near.size()));
			Optional<BlockPos> hall = VillageHalls.nearest(level, speaker.blockPosition());
			if (hall.isEmpty()) {
				continue;
			}
			Component line = line(level, speaker, hall.get(), player);
			if (line != null) {
				say(level, speaker, player, line);
				LAST.put(player.getUUID(), now);
			}
		}
	}

	/** Off work: not showing a work line, and not working, hiding or fleeing. */
	static boolean offWork(Villager villager, long now) {
		if (WorkerStatus.get(villager, now) != null) {
			return false;
		}
		Activity activity = villager.getBrain().getActiveNonCoreActivity().orElse(null);
		return activity != Activity.WORK && activity != Activity.PANIC && activity != Activity.HIDE && activity != Activity.PRE_RAID
			&& activity != Activity.RAID;
	}

	/** What the villager talks about now: village news first (twice as likely), then their mood, then hello. */
	public static List<String> topics(ServerLevel level, Villager villager, BlockPos hall) {
		List<String> topics = new ArrayList<>();
		List<String> news = new ArrayList<>();
		if (Festivals.isOn(level, hall)) {
			news.add("festival_on");
		} else if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.festivalDay() == Chronicle.day(level)) {
			news.add("festival_today");
		} else if (Festivals.enjoyedLately(level, villager)) {
			news.add("festival_after");
		}
		if (BanditCamps.near(level, hall).isPresent()) {
			news.add("bandits");
		}
		if (VillageRaids.active(hall).isPresent()) {
			news.add("raid");
		}
		if (villager.isBaby()) {
			news.add("child");
		}
		if (Sickness.isIll(villager)) {
			news.add("ill");
		}
		news.addAll(legendTopics(level, villager, hall));
		news.addAll(civicTopics(level, villager, hall));
		topics.addAll(news);
		topics.addAll(news);
		Moods.Mood mood = Moods.of(villager);
		if (mood != null) {
			for (Component reason : mood.bad()) {
				topicOf(reason).ifPresent(topics::add);
			}
			for (Component reason : mood.good()) {
				topicOf(reason).ifPresent(topics::add);
			}
		}
		topics.add("hello");
		return topics;
	}

	/**
	 * Talk of the village's Legends and guests (29.4): a Legend speaks of their own title, others of the Legend living
	 * here, a Legend visiting as a guest, or one on strike.
	 */
	static List<String> legendTopics(ServerLevel level, Villager villager, BlockPos hall) {
		List<String> out = new ArrayList<>();
		if (io.github.jcondedata.aliveworkplace.legend.Legends.of(villager).isPresent()) {
			out.add("legend_self");
			return out;
		}
		for (Villager legend : io.github.jcondedata.aliveworkplace.legend.LegendsPage.villagers(level, hall)) {
			io.github.jcondedata.aliveworkplace.legend.LegendData data = io.github.jcondedata.aliveworkplace.registry.ModAttachments.LEGEND.get(legend);
			String topic = data.guest() ? "legend_guest" : data.onStrike() ? "legend_strike" : "legend_here";
			if (!out.contains(topic)) {
				out.add(topic);
			}
		}
		return out;
	}

	/**
	 * Talk of the village's civic life (30.21): each of our edicts in force ({@code edict_<id>}, or {@code reformed_<id>}
	 * once reformed), a rush of the Work Horn, the tonic working in the speaker, the speaker's founded guild and the
	 * village's colours. A pack's edicts have no lines, so they aren't talked of.
	 */
	public static List<String> civicTopics(ServerLevel level, Villager villager, BlockPos hall) {
		List<String> out = new ArrayList<>();
		if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
			return out;
		}
		if (io.github.jcondedata.aliveworkplace.hall.Edicts.ENABLED) {
			for (io.github.jcondedata.aliveworkplace.hall.Edicts.InForce f : entity.edicts()) {
				net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(f.id());
				if (id == null || !id.getNamespace().equals(io.github.jcondedata.aliveworkplace.AliveWorkplace.MOD_ID)) {
					continue;
				}
				String topic = (io.github.jcondedata.aliveworkplace.hall.Reforms.reformed(entity, f.id()) ? "reformed_" : "edict_") + id.getPath();
				if (VARIANTS.containsKey(topic)) {
					out.add(topic);
				}
			}
		}
		if (!villager.isBaby() && io.github.jcondedata.aliveworkplace.hall.WorkHorn.rushing(level, entity)) {
			out.add("rush");
		}
		if (Tonics.active(villager) != null) {
			out.add("tonic");
		}
		if (guildOf(level, villager, entity) != null) {
			out.add("guild");
		}
		if (entity.colours() != null) {
			out.add("colours");
		}
		return out;
	}

	/** The founded guild of the village that gathers {@code villager}'s trade, or null. */
	@Nullable
	static io.github.jcondedata.aliveworkplace.hall.Guilds.Guild guildOf(ServerLevel level, Villager villager, VillageHallBlockEntity entity) {
		if (!io.github.jcondedata.aliveworkplace.hall.Guilds.ENABLED) {
			return null;
		}
		for (io.github.jcondedata.aliveworkplace.hall.Guilds.Charter c : entity.guilds()) {
			io.github.jcondedata.aliveworkplace.hall.Guilds.Guild g = io.github.jcondedata.aliveworkplace.hall.Guilds.get(c.id());
			if (g != null && g.gathers(villager) && io.github.jcondedata.aliveworkplace.hall.Guilds.founded(level, entity, c.id())) {
				return g;
			}
		}
		return null;
	}

	/** Who a Legend topic is about: "Ada, Master Architect" (the speaker's own title for {@code legend_self}). */
	static Component legendArg(ServerLevel level, Villager villager, BlockPos hall, String topic) {
		if (topic.equals("legend_self")) {
			return io.github.jcondedata.aliveworkplace.legend.Legends.of(villager).map(l -> l.titleText()).orElse(Component.empty());
		}
		for (Villager legend : io.github.jcondedata.aliveworkplace.legend.LegendsPage.villagers(level, hall)) {
			io.github.jcondedata.aliveworkplace.legend.LegendData data = io.github.jcondedata.aliveworkplace.registry.ModAttachments.LEGEND.get(legend);
			String about = data.guest() ? "legend_guest" : data.onStrike() ? "legend_strike" : "legend_here";
			if (about.equals(topic)) {
				Component name = io.github.jcondedata.aliveworkplace.legend.LegendText.hallName(legend);
				return name != null ? name : legend.getDisplayName();
			}
		}
		return Component.empty();
	}

	private static Optional<String> topicOf(Component reason) {
		if (reason.getContents() instanceof TranslatableContents t && t.getKey().startsWith("mood.aliveworkplace.reason.")) {
			String topic = t.getKey().substring("mood.aliveworkplace.reason.".length());
			return VARIANTS.containsKey(topic) ? Optional.of(topic) : Optional.empty();
		}
		return Optional.empty();
	}

	/** Something for {@code villager} to say to {@code player} now, or null. */
	@Nullable
	public static Component line(ServerLevel level, Villager villager, BlockPos hall, ServerPlayer player) {
		List<String> topics = topics(level, villager, hall);
		if (topics.isEmpty()) {
			return null;
		}
		String topic = topics.get(level.random.nextInt(topics.size()));
		int variant = level.random.nextInt(VARIANTS.getOrDefault(topic, 1));
		Object arg = switch (topic) {
			case "hello" -> player.getDisplayName();
			case "bandits" -> BanditCamps.near(level, hall).map(camp -> VillageHallScreen.where(hall, camp.pos())).orElse(Component.empty());
			case "married", "courting" -> Couples.partner(villager) != null ? Couples.partner(villager).name() : Component.empty();
			case "legend_here", "legend_guest", "legend_strike", "legend_self" -> legendArg(level, villager, hall, topic);
			case "tonic" -> Tonics.active(villager) != null ? Tonics.active(villager).item().getDescription() : Component.empty();
			case "guild" -> level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && guildOf(level, villager, entity) != null
				? guildOf(level, villager, entity).name() : Component.empty();
			default -> "";
		};
		return Component.translatable("chatter.aliveworkplace." + topic + "." + variant, arg).withStyle(ChatFormatting.ITALIC);
	}

	/** {@code villager} says line {@code variant} of {@code topic} to {@code player} (a showcase scene picks the line). */
	public static Component say(ServerLevel level, Villager villager, ServerPlayer player, String topic, int variant, Object arg) {
		Component line = Component.translatable("chatter.aliveworkplace." + topic + "." + variant, arg).withStyle(ChatFormatting.ITALIC);
		say(level, villager, player, line);
		return line;
	}

	/** {@code villager} turns to {@code player} and says {@code line} (over their head, for a few seconds). */
	static void say(ServerLevel level, Villager villager, ServerPlayer player, Component line) {
		Component name = villager.hasCustomName() ? villager.getCustomName() : villager.getType().getDescription();
		WorkerStatus.set(villager, name.copy().withStyle(ChatFormatting.GRAY), -1f, line);
		villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
		villager.playSound(villager.isBaby() ? SoundEvents.VILLAGER_CELEBRATE : SoundEvents.VILLAGER_AMBIENT, 0.6f, villager.isBaby() ? 1.5f : 1f);
	}

	private Chatter() {
	}
}

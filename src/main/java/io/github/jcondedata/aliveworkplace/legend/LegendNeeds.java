package io.github.jcondedata.aliveworkplace.legend;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Homes;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A settled Legend's needs and strikes (ROADMAP 29.5), checked once a day in the hall's round:
 * <ul>
 *   <li>a home of their own: their bed is in a finished building of tier III or higher, and no bed in it is anyone's but
 *       theirs and their spouse's;</li>
 *   <li>their luxury: every {@link #LUXURY_DAYS} days they take one item of {@code aliveworkplace:luxury/<kind>} from a
 *       chest in their home, else from the village store, and are {@link #LUXURY_MOOD} happier while it lasts;</li>
 *   <li>a happy village: the grown-ups' average mood is {@link #HAPPY_MOOD} or more (wellbeing {@link #HAPPY_WELLBEING}
 *       with moods off).</li>
 * </ul>
 * A need unmet {@link #STRIKE_DAYS} days running starts a strike, but not in the {@link #GRACE} days after they settle
 * (the grace holds the strike off; the days are still counted). On strike their powers stop ({@link LegendPowers}), their
 * WORK activity is a picket by the Village Hall ({@link Picket}), a red line over their head says what they want, and the
 * chronicle records it. While on strike every round looks again, so the day every need is met they go back to work.
 * Legends never leave: they are kept from despawning, inn departures and the hall's call-home pass them by.
 * {@code legendNeeds} in the config switches it off: nothing is checked and strikes are called off at the next round.
 */
public final class LegendNeeds {
	public static boolean ENABLED = true;
	/** Days after settling in which no strike begins. */
	public static final int GRACE = 3;
	/** Days running a need goes unmet before a strike. */
	public static final int STRIKE_DAYS = 2;
	/** Days a luxury lasts. */
	public static final int LUXURY_DAYS = 7;
	/** How much a luxury lifts the Legend's mood while it lasts. */
	public static final int LUXURY_MOOD = 10;
	/** The grown-ups' average mood a happy village has, and its wellbeing with moods off. */
	public static final int HAPPY_MOOD = 60;
	public static final float HAPPY_WELLBEING = 0.6f;
	/** The lowest tier of a home of their own. */
	public static final int HOME_TIER = 3;

	/** {@code aliveworkplace:luxury/<kind>}: what a Legend who likes {@code kind} (wine, jewels, books, clothes) takes. */
	public static TagKey<Item> luxuryTag(String kind) {
		return TagKey.create(Registries.ITEM, AliveWorkplace.id("luxury/" + kind));
	}

	/** Whether {@code villager} is a settled Legend (they never move on: no inn departure, no despawning). */
	public static boolean staying(Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		return data != null && data.settled();
	}

	/** Whether {@code villager} is a Legend on strike (their WORK activity is a picket). */
	public static boolean striking(Villager villager) {
		if (!Legends.ENABLED || !ENABLED) {
			return false;
		}
		LegendData data = ModAttachments.LEGEND.get(villager);
		return data != null && data.settled() && data.onStrike();
	}

	/** The hall's round: every settled Legend of the village is kept from despawning and has their needs checked. */
	public static void round(ServerLevel level, BlockPos hall) {
		if (!Legends.ENABLED) {
			return;
		}
		long today = Chronicle.day(level);
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && ModAttachments.LEGEND.has(v))) {
			LegendData data = ModAttachments.LEGEND.get(v);
			if (data == null || !data.settled() || !data.hall().equals(Optional.of(hall))) {
				continue;
			}
			v.setPersistenceRequired();
			if (!ENABLED) {
				if (data.onStrike() || !data.unmet().isEmpty()) {
					ModAttachments.LEGEND.set(v, data.checkedOn(data.checked(), Map.of(), -1, data.lastLuxury()));
					LegendPowers.forget();
				}
				continue;
			}
			check(level, hall, v, today);
		}
	}

	/**
	 * Checks {@code villager}'s needs on {@code today} (a {@link Chronicle#day}): once a day, and on every round while
	 * they're on strike. Counts the days each need has gone unmet, takes their luxury when it's due, starts or ends a
	 * strike, and writes the chronicle. Returns the Legend's data afterwards (null for no settled Legend).
	 */
	@Nullable
	public static LegendData check(ServerLevel level, BlockPos hall, Villager villager, long today) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		Legend legend = data == null ? null : Legends.get(data.id()).orElse(null);
		if (legend == null || !data.settled()) {
			return null;
		}
		boolean newDay = today > data.checked();
		if (!newDay && !data.onStrike()) {
			return data;
		}
		Map<String, Boolean> met = new LinkedHashMap<>();
		met.put(LegendText.HOME, home(level, hall, villager));
		long luxury = data.lastLuxury();
		if (legend.luxury().isPresent()) {
			boolean due = luxury < 0 || today - luxury >= LUXURY_DAYS;
			if (due && takeLuxury(level, hall, villager, legend.luxury().get())) {
				luxury = today;
				due = false;
			}
			met.put(LegendText.LUXURY, !due);
		}
		met.put(LegendText.HAPPY, happy(level, hall));
		Map<String, Integer> unmet = new LinkedHashMap<>();
		boolean all = true;
		boolean overdue = false;
		for (Map.Entry<String, Boolean> need : met.entrySet()) {
			int before = data.unmet().getOrDefault(need.getKey(), 0);
			int days = need.getValue() ? 0 : newDay ? before + 1 : Math.max(1, before);
			if (days > 0) {
				unmet.put(need.getKey(), days);
				all = false;
				overdue |= days >= STRIKE_DAYS;
			}
		}
		long strike = data.strikeSince();
		LegendData now = data.checkedOn(newDay ? today : data.checked(), unmet, strike, luxury);
		if (data.onStrike() && all) {
			now = now.checkedOn(now.checked(), unmet, -1, luxury);
			Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.legend.strike_over",
				villager.getDisplayName(), legend.titleText()));
		} else if (!data.onStrike() && newDay && overdue && today >= data.settledDay() + GRACE) {
			now = now.checkedOn(now.checked(), unmet, today, luxury);
			Chronicle.record(level, hall, Chronicle.Kind.LEGEND, Component.translatable("chronicle.aliveworkplace.legend.strike",
				villager.getDisplayName(), legend.titleText(), wants(legend, now)));
		}
		ModAttachments.LEGEND.set(villager, now);
		if (now.onStrike() != data.onStrike()) {
			LegendPowers.forget(); // their powers stop (or start again) at once
			villager.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
		}
		return now;
	}

	/**
	 * A home of their own: their bed is in a finished building of tier {@link #HOME_TIER} or more, and nobody else's bed
	 * in the village is in it but their spouse's.
	 */
	public static boolean home(ServerLevel level, BlockPos hall, Villager legend) {
		BlockPos bed = VillageNeeds.bed(level, legend);
		Homes.Building building = bed == null ? null : Homes.building(level, bed).orElse(null);
		if (building == null || building.home().tier() < HOME_TIER) {
			return false;
		}
		Couples.Partner partner = Couples.partner(legend);
		UUID spouse = partner != null && partner.married() ? partner.id() : null;
		for (Villager other : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Villager::isAlive)) {
			if (other == legend || other.getUUID().equals(spouse)) {
				continue;
			}
			BlockPos theirs = VillageNeeds.bed(level, other);
			if (theirs != null && building.box().isInside(theirs)) {
				return false;
			}
		}
		return true;
	}

	/** Takes one luxury of {@code kind} from a chest in the Legend's home, else from the village store; false if there's none. */
	public static boolean takeLuxury(ServerLevel level, BlockPos hall, Villager legend, String kind) {
		TagKey<Item> tag = luxuryTag(kind);
		ItemStack taken = ItemStack.EMPTY;
		BlockPos bed = VillageNeeds.bed(level, legend);
		Optional<Homes.Building> building = bed == null ? Optional.empty() : Homes.building(level, bed);
		if (building.isPresent()) {
			taken = SupplyContainers.takeOne(level, SupplyContainers.inside(level, building.get().box()), s -> s.is(tag));
		}
		if (taken.isEmpty()) {
			taken = SupplyContainers.takeOne(level, VillageNeeds.store(level, hall), s -> s.is(tag));
		}
		if (taken.isEmpty()) {
			return false;
		}
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, taken), legend.getX(), legend.getEyeY() - 0.2, legend.getZ(), 6, 0.2, 0.1, 0.2, 0.05);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, legend.getX(), legend.getY() + 1.2, legend.getZ(), 6, 0.3, 0.4, 0.3, 0);
		Moods.forget(legend);
		return true;
	}

	/**
	 * A happy village: the grown-ups' average mood (those whose nearest hall is this one) is {@link #HAPPY_MOOD} or more, or
	 * with moods off its wellbeing {@link #HAPPY_WELLBEING}.
	 */
	public static boolean happy(ServerLevel level, BlockPos hall) {
		if (Moods.ENABLED) {
			int sum = 0;
			int n = 0;
			for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), v -> v.isAlive() && !v.isBaby()
				&& VillageHalls.nearest(level, v.blockPosition()).equals(Optional.of(hall)))) {
				Moods.Mood mood = Moods.of(v);
				if (mood != null) {
					sum += mood.score();
					n++;
				}
			}
			if (n > 0) {
				return sum >= HAPPY_MOOD * n;
			}
		}
		VillageNeeds.Needs needs = level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.needs() != null
			? entity.needs() : VillageNeeds.count(level, hall);
		return needs.wellbeing() >= HAPPY_WELLBEING - 1e-4f;
	}

	/** The mood a Legend's luxury gives while it lasts (0 for anyone else), as a mood reason. */
	@Nullable
	public static LegendPowers.MoodReason luxuryMood(ServerLevel level, Villager villager) {
		LegendData data = ModAttachments.LEGEND.get(villager);
		if (data == null || !ENABLED || data.lastLuxury() < 0 || Chronicle.day(level) - data.lastLuxury() >= LUXURY_DAYS) {
			return null;
		}
		Legend legend = Legends.get(data.id()).orElse(null);
		if (legend == null || legend.luxury().isEmpty()) {
			return null;
		}
		return new LegendPowers.MoodReason(Component.translatable("mood.aliveworkplace.reason.luxury", LegendText.luxury(legend.luxury().get())), LUXURY_MOOD);
	}

	/** What a Legend on strike wants, in their words: "a home of my own and wine once a week". */
	public static Component wants(Legend legend, LegendData data) {
		List<Component> parts = new java.util.ArrayList<>();
		if (!LegendText.met(data, LegendText.HOME)) {
			parts.add(Component.translatable("legend.aliveworkplace.want.home"));
		}
		if (legend.luxury().isPresent() && !LegendText.met(data, LegendText.LUXURY)) {
			parts.add(Component.translatable("legend.aliveworkplace.want.luxury", LegendText.luxury(legend.luxury().get())));
		}
		if (!LegendText.met(data, LegendText.HAPPY)) {
			parts.add(Component.translatable("legend.aliveworkplace.want.happy"));
		}
		if (parts.isEmpty()) {
			return Component.translatable("legend.aliveworkplace.want.nothing");
		}
		MutableComponent out = Component.empty();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				out.append(Component.translatable(i == parts.size() - 1 ? "legend.aliveworkplace.and" : "legend.aliveworkplace.comma"));
			}
			out.append(parts.get(i));
		}
		return out;
	}

	/** "On strike: a home of my own", in red: the line over a striking Legend's head. */
	public static Component overhead(Legend legend, LegendData data) {
		return Component.translatable("legend.aliveworkplace.overhead.strike", wants(legend, data)).withStyle(ChatFormatting.RED);
	}

	/** Every second of a Legend's life: on strike and awake, the red line over their head. */
	static void tick(Villager villager) {
		if (villager.isSleeping() || !striking(villager)) {
			return;
		}
		LegendData data = ModAttachments.LEGEND.get(villager);
		Legend legend = Legends.get(data.id()).orElse(null);
		if (legend != null) {
			WorkerStatus.set(villager, overhead(legend, data), -1f,
				Component.translatable("legend.aliveworkplace.overhead.picket").withStyle(ChatFormatting.GRAY));
		}
	}

	private LegendNeeds() {
	}
}

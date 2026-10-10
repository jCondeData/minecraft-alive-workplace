package io.github.jcondedata.aliveworkplace.people;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;

/**
 * Moods: in a village with a Village Hall every grown villager has a mood, 0 to 100, from their own day — fed or hungry,
 * a bed of their own or not (and how fine a house it's in, see {@link Homes}), a job, ill or well, cheerful by nature,
 * decorations near home, company, a quiet old age (an elder, fed and housed). The hall's list
 * shows it with the reasons. Unhappy villagers (under {@link #UNHAPPY}) work 15% slower; happy ones ({@link #HAPPY} and
 * up) 7% faster. {@code villagerMoods} in the config turns it off.
 */
public final class Moods {
	public static boolean ENABLED = true;
	public static final int UNHAPPY = 30;
	public static final int HAPPY = 75;
	/** How long a mood is remembered before it's worked out again. */
	private static final long KEEP_TICKS = 200;
	/** How near home decorations count, and how near others count as company. */
	static final int DECORATION_RANGE = 16;
	static final int COMPANY_RANGE = 6;

	/** A mood, with what makes it better and worse. */
	public record Mood(int score, List<Component> good, List<Component> bad) {
		public Component title() {
			String key = score >= HAPPY ? "happy" : score < UNHAPPY ? "unhappy" : "content";
			return Component.translatable("mood.aliveworkplace." + key, score);
		}
	}

	private record Kept(Mood mood, long until) {
	}

	private static final Map<Villager, Kept> KEPT = new WeakHashMap<>();

	/** {@code villager}'s mood, or null outside a village with a hall (or with moods off, or for a child). */
	@org.jetbrains.annotations.Nullable
	public static Mood of(Villager villager) {
		if (!ENABLED || villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
			return null;
		}
		long now = level.getGameTime();
		Kept kept = KEPT.get(villager);
		if (kept != null && now < kept.until()) {
			return kept.mood();
		}
		Mood mood = VillageHalls.nearest(level, villager.blockPosition()).isPresent() ? work(level, villager) : null;
		KEPT.put(villager, new Kept(mood, now + KEEP_TICKS));
		return mood;
	}

	/**
	 * {@code villager}'s mood worked out this moment (null as for {@link #of}), neither read from what is remembered nor
	 * remembered: for a reading that is kept elsewhere (the day's count of happy days), which must not decide what the
	 * mood reads as for the next {@link #KEEP_TICKS} ticks.
	 */
	@org.jetbrains.annotations.Nullable
	public static Mood fresh(Villager villager) {
		if (!ENABLED || villager.isBaby() || !(villager.level() instanceof ServerLevel level)) {
			return null;
		}
		return VillageHalls.nearest(level, villager.blockPosition()).isPresent() ? work(level, villager) : null;
	}

	/** Works out {@code villager}'s mood now. */
	public static Mood work(ServerLevel level, Villager villager) {
		long now = level.getGameTime();
		int score = 50;
		List<Component> good = new ArrayList<>();
		List<Component> bad = new ArrayList<>();
		boolean fed = false;
		if (VillageNeeds.isHungry(villager, now)) {
			score -= 20;
			bad.add(reason("hungry"));
		} else if (ModAttachments.LAST_MEAL.has(villager)) {
			score += 15;
			good.add(reason("fed"));
			fed = true;
		}
		switch (Diet.of(villager)) {
			case VARIED -> {
				score += 10;
				good.add(reason("varied_diet"));
			}
			case SAME -> {
				score -= 10;
				bad.add(reason("same_food"));
			}
			default -> {
			}
		}
		BlockPos bed = VillageNeeds.bed(level, villager);
		if (bed != null) {
			score += 15;
			good.add(reason("bed"));
			Homes.Home home = Homes.at(level, bed).orElse(null);
			if (home != null && home.mood() > 0) {
				score += home.mood();
				good.add(reason(home.tier() >= 3 ? "grand_home" : "fine_home"));
			}
		} else {
			score -= 15;
			bad.add(reason("no_bed"));
		}
		// An elder who is fed and housed (34.19): first of the good reasons, so the hall's card shows it.
		if (LifeStages.quietOldAge(villager, io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level), fed, bed != null)) {
			score += LifeStages.QUIET_OLD_AGE;
			good.add(0, reason("quiet_old_age"));
		}
		VillagerProfession job = villager.getVillagerData().getProfession();
		if (job == VillagerProfession.NONE) {
			score -= 5;
			bad.add(reason("no_job"));
		} else if (job != VillagerProfession.NITWIT) {
			score += 10;
			good.add(reason("job"));
		}
		if (Sickness.isIll(villager)) {
			score -= 20;
			bad.add(reason("ill"));
		}
		if (Traits.has(villager, Traits.Trait.CHEERFUL)) {
			score += 10;
			good.add(reason("cheerful"));
		}
		BlockPos home = bed != null ? bed : villager.blockPosition();
		int beauty = 0;
		for (BuildSiteManager.Finished f : BuildSiteManager.get(level).finishedNear(level, home, DECORATION_RANGE)) {
			beauty += Decorations.points(f.structure());
		}
		if (beauty > 0) {
			score += Math.min(15, 5 * beauty);
			good.add(reason("decorations"));
		}
		Couples.Partner partner = Couples.partner(villager);
		if (partner != null && level.getEntity(partner.id()) instanceof Villager other && other.isAlive() && other.distanceToSqr(villager) < 32 * 32) {
			score += partner.married() ? 10 : 5;
			good.add(reason(partner.married() ? "married" : "courting"));
		}
		if (Couples.isMourning(level, villager)) {
			score -= 15;
			bad.add(reason("mourning"));
		}
		int questMood = io.github.jcondedata.aliveworkplace.story.Stories.mood(level, villager); // village_mood rewards (31.2)
		if (questMood != 0) {
			score += questMood;
			(questMood > 0 ? good : bad).add(reason("quest"));
		}
		if (io.github.jcondedata.aliveworkplace.hall.Festivals.enjoyedLately(level, villager)) {
			int festival = io.github.jcondedata.aliveworkplace.hall.Festivals.mood(level, villager);
			score += festival;
			good.add(reason(festival > io.github.jcondedata.aliveworkplace.hall.Festivals.MOOD ? "festival_banner" : "festival"));
		}
		if (io.github.jcondedata.aliveworkplace.hall.Festivals.disappointed(level, villager)) {
			score -= io.github.jcondedata.aliveworkplace.hall.Festivals.DISAPPOINTED;
			bad.add(reason("disappointed"));
		}
		if (io.github.jcondedata.aliveworkplace.hall.WorkHorn.wornOut(level, villager)) {
			score -= io.github.jcondedata.aliveworkplace.hall.WorkHorn.WORN_OUT;
			bad.add(reason("worn_out"));
		}
		// The edicts in force (and later tonics and guilds): Long Shifts' "long shifts", listed first.
		int civic = 0;
		for (io.github.jcondedata.aliveworkplace.hall.CivicEffects.Mood effect : io.github.jcondedata.aliveworkplace.hall.CivicEffects.of(villager).moods(villager, now)) {
			score += effect.points();
			if (effect.points() < 0) {
				bad.add(civic++, effect.reason());
			} else if (effect.points() > 0) {
				good.add(effect.reason());
			}
		}
		// Legends near the villager (29.2), each its own reason, after the edicts.
		for (io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason legend : io.github.jcondedata.aliveworkplace.legend.LegendPowers.moods(level, villager)) {
			score += legend.points();
			(legend.points() >= 0 ? good : bad).add(legend.reason());
		}
		// Beloved neighbours (29.7): a Gifted villager whose bed is near this one's.
		for (io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason beloved : io.github.jcondedata.aliveworkplace.legend.GiftedAuras.moods(level, villager)) {
			score += beloved.points();
			(beloved.points() >= 0 ? good : bad).add(beloved.reason());
		}
		// A Legend's luxury (29.5), for the week it lasts.
		io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason luxury = io.github.jcondedata.aliveworkplace.legend.LegendNeeds.luxuryMood(level, villager);
		if (luxury != null) {
			score += luxury.points();
			good.add(luxury.reason());
		}
		// Sulking after a strange mood that failed (29.10), for the week it lasts.
		io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason sulk = io.github.jcondedata.aliveworkplace.legend.StrangeMoods.sulkMood(level, villager);
		if (sulk != null) {
			score += sulk.points();
			bad.add(sulk.reason());
		}
		// A wedding the Seer blessed (29.16), for the days it lasts.
		io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason blessed = io.github.jcondedata.aliveworkplace.legend.Seer.blessedMood(level, villager);
		if (blessed != null) {
			score += blessed.points();
			good.add(blessed.reason());
		}
		// A Bard Laureate's work song heard today (29.19).
		io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason song = io.github.jcondedata.aliveworkplace.legend.BardLaureate.heardMood(level, villager);
		if (song != null) {
			score += song.points();
			good.add(song.reason());
		}
		// The Founder's statue (29.23), while it stands.
		io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason statue = io.github.jcondedata.aliveworkplace.legend.Founder.statueMood(level, villager);
		if (statue != null) {
			score += statue.points();
			good.add(statue.reason());
		}
		// A Grand Chef's banquet (29.18), for the days it lasts.
		io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason banquet = io.github.jcondedata.aliveworkplace.hall.Banquets.mood(level, villager);
		if (banquet != null) {
			score += banquet.points();
			good.add(banquet.reason());
		}
		// The Festival Cup their village holds (28.21).
		io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason cup = io.github.jcondedata.aliveworkplace.cup.CupChampions.mood(level, villager);
		if (cup != null) {
			score += cup.points();
			good.add(cup.reason());
		}
		// Their class (34.6): a rise or fall of the last days, and the needs of their class had or lacking.
		for (io.github.jcondedata.aliveworkplace.legend.LegendPowers.MoodReason c : SocialClasses.moods(villager, io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level))) {
			score += c.points();
			(c.points() >= 0 ? good : bad).add(c.reason());
		}
		if (!level.getEntitiesOfClass(Villager.class, villager.getBoundingBox().inflate(COMPANY_RANGE), v -> v != villager && v.isAlive()).isEmpty()) {
			score += 5;
			good.add(reason("company"));
		}
		return new Mood(Math.max(0, Math.min(100, score)), List.copyOf(good), List.copyOf(bad));
	}

	private static Component reason(String key) {
		return Component.translatable("mood.aliveworkplace.reason." + key);
	}

	/** Work delay multiplier from the mood: unhappy 15% slower, happy 7% faster. */
	public static float pace(Villager villager) {
		Mood mood = of(villager);
		if (mood == null) {
			return 1f;
		}
		return mood.score() < UNHAPPY ? 1.15f : mood.score() >= HAPPY ? 0.93f : 1f;
	}

	/** Forget the remembered moods (tests). */
	public static void forget() {
		KEPT.clear();
	}

	/** Forget {@code villager}'s remembered mood (something just changed it: a Legend's luxury). */
	public static void forget(Villager villager) {
		KEPT.remove(villager);
	}

	private Moods() {
	}
}

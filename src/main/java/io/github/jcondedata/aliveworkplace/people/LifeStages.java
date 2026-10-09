package io.github.jcondedata.aliveworkplace.people;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Life stages (ROADMAP 34.19 and 34.19a, docs/design/M34.md): a grown villager counts their days from
 * {@code adult_since} (a {@code Chronicle.day}; set when a child grows up in a village with a hall). After
 * {@link #ELDER_DAYS} grown days they are an <b>elder</b>, and with {@code elderPassing} an elder passes away in the
 * night after {@link #PASSING_DAYS} elder days and leaves a grave.
 *
 * <p>The <b>Evergreen Charm</b> (34.19a) keeps one: sneak-right-click an elder with it and they become <b>Ageless</b>
 * (the {@code ageless} attachment) and never pass. Only an elder with a good trait takes it: a Master of their trade,
 * a Gifted villager, a Legend, or one whose mood has been {@link #HAPPY_MOOD} or more for the last {@link #HAPPY_DAYS}
 * days (the {@code happy_streak} attachment, counted once a day in the hall's round). Anyone else refuses with the
 * reason and the player keeps the charm.
 *
 * <p>An elder walks 15% slower ({@link #walk} and {@link #ELDER_WALK}, asked by {@code work/Walker}), has "a quiet old age"
 * ({@link #QUIET_OLD_AGE +5}) in their mood when fed and housed ({@code Moods}), four chatter lines of their own
 * ({@code Chatter}'s topic {@code elder}), and the chronicle notes the day they became one ("Bram is an elder now",
 * once: the {@code elder_noted} attachment).
 *
 * <p>Whether someone is an elder is never saved: it is worked out from {@code adult_since}. The elder <b>look</b> is
 * 34.18's (a client render layer): it should draw it for whoever {@link #stage} calls an {@link Stage#ELDER}, and send
 * its packet from {@link #becameElder}, the one place a villager's stage is seen to change.
 */
public final class LifeStages {
	/** {@code villagerAges} in the config. Off: nobody is an elder (so nobody passes); {@code adult_since} is still kept. */
	public static boolean AGES = true;
	/** {@code villagerElderDays}: grown days before a villager is an elder. */
	public static int ELDER_DAYS = 120;
	/** {@code elderPassing}. Off: elders never die of old age. */
	public static boolean PASSING = true;
	/** {@code agelessElders}. Off: Evergreen Charms are refused; those already ageless stay so. */
	public static boolean AGELESS = true;
	/**
	 * What an elder's walk target is multiplied by, for a walk 15% slower over the ground: a mob's pace goes with the
	 * square of its speed ({@code Mob.setSpeed} also sets how hard it pushes forward), and 0.922 squared is 0.85.
	 */
	public static final float ELDER_WALK = 0.922f;
	/** What "a quiet old age" adds to the mood of an elder who is fed and housed. */
	public static final int QUIET_OLD_AGE = 5;
	/** Elder days before an elder passes. */
	public static final int PASSING_DAYS = 40;
	/** The mood, and the days running, that make an elder worth a charm. */
	public static final int HAPPY_MOOD = 80;
	public static final int HAPPY_DAYS = 20;
	/** The hall's list warns this many days before an elder's time. */
	public static final int WARN_DAYS = 10;
	/** Night on the day clock: an elder with no bed to sleep in passes in these hours. */
	private static final long NIGHT_FROM = 13000;
	private static final long NIGHT_TO = 23000;

	public static final ResourceKey<DamageType> OLD_AGE = ResourceKey.create(Registries.DAMAGE_TYPE, AliveWorkplace.id("old_age"));

	/** Days running a villager's mood has been {@link #HAPPY_MOOD} or more, and the last day it was counted. */
	public record Streak(int days, long day) {
		public static final Codec<Streak> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("days", 0).forGetter(Streak::days),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Streak::day)
		).apply(i, Streak::new));
	}

	/** What makes an elder worth an Evergreen Charm. */
	public enum Trait {
		MASTER, GIFTED, LEGEND, HAPPY;

		public Component text() {
			return Component.translatable("message.aliveworkplace.evergreen.trait." + name().toLowerCase(java.util.Locale.ROOT));
		}
	}

	public enum Outcome {
		ACCEPTED, DISABLED, NOT_NEEDED, NOT_AN_ELDER, ALREADY, NO_GOOD_TRAIT
	}

	/** What came of offering a charm: the outcome, what the player is told, and the trait that earned it (accepted only). */
	public record Offer(Outcome outcome, Component message, @Nullable Trait trait) {
	}

	public static void init() {
		Platform.get().onUseEntity((player, level, hand, entity, hit) -> use(player, level, hand, entity));
	}

	// --- Ages -----------------------------------------------------------------------------------------------------

	/** The day {@code villager} grew up, or null when nobody knows (never counted: an old save, or no hall). */
	@Nullable
	public static Long adultSince(Villager villager) {
		return ModAttachments.ADULT_SINCE.get(villager);
	}

	/** Notes that {@code villager} grew up on {@code day}. */
	public static void setAdultSince(Villager villager, long day) {
		ModAttachments.ADULT_SINCE.set(villager, day);
	}

	/** Days since {@code villager} grew up, on {@code day}; -1 for a child or when nobody knows. */
	public static long grownDays(Villager villager, long day) {
		Long since = adultSince(villager);
		return since == null || villager.isBaby() ? -1 : Math.max(0, day - since);
	}

	public static boolean isElder(Villager villager, long day) {
		return AGES && grownDays(villager, day) >= ELDER_DAYS;
	}

	public static boolean isElder(Villager villager) {
		return villager.level() instanceof ServerLevel level && isElder(villager, Chronicle.day(level));
	}

	/** A villager's stage of life. */
	public enum Stage {
		CHILD, GROWN, ELDER
	}

	/** {@code villager}'s stage of life on {@code day} (what 34.18's elder look is drawn from). */
	public static Stage stage(Villager villager, long day) {
		return villager.isBaby() ? Stage.CHILD : isElder(villager, day) ? Stage.ELDER : Stage.GROWN;
	}

	/** Walk target multiplier ({@link #ELDER_WALK}): an elder, ageless or not, walks 15% slower. */
	public static float walk(Villager villager) {
		return isElder(villager) ? ELDER_WALK : 1f;
	}

	/** Whether {@code villager} has the mood reason "a quiet old age" on {@code day}: an elder who is fed and has a bed. */
	public static boolean quietOldAge(Villager villager, long day, boolean fed, boolean housed) {
		return fed && housed && isElder(villager, day);
	}

	/** Days {@code villager} has been an elder, on {@code day}; -1 for anyone else. */
	public static long elderDays(Villager villager, long day) {
		return isElder(villager, day) ? grownDays(villager, day) - ELDER_DAYS : -1;
	}

	public static boolean isAgeless(Villager villager) {
		return ModAttachments.AGELESS.getOrElse(villager, false);
	}

	/**
	 * Days until {@code villager}'s time comes, on {@code day} (0: tonight); -1 for anyone who won't pass: not an elder,
	 * ageless, or with {@code elderPassing} off. An elder an Undertaker brought back has {@link #PASSING_DAYS} more.
	 */
	public static long daysLeft(Villager villager, long day) {
		long elder = elderDays(villager, day);
		if (!PASSING || elder < 0 || isAgeless(villager)) {
			return -1;
		}
		long left = PASSING_DAYS - elder;
		Long back = ModAttachments.PASSED_DAY.get(villager);
		if (back != null) {
			left = Math.max(left, PASSING_DAYS - (day - back));
		}
		return Math.max(0, left);
	}

	// --- The hall's round -----------------------------------------------------------------------------------------

	/**
	 * The hall's round: each grown villager's happy days are counted once a day, the chronicle notes who has become an
	 * elder, and an elder whose time has come passes in the night.
	 */
	public static void round(ServerLevel level, BlockPos hall, List<Villager> grown) {
		long day = Chronicle.day(level);
		for (Villager villager : List.copyOf(grown)) {
			if (!villager.isAlive() || villager.isBaby()) {
				continue;
			}
			countMood(villager, day);
			if (isElder(villager, day) && !ModAttachments.ELDER_NOTED.getOrElse(villager, false)) {
				becameElder(level, hall, villager);
			}
			if (daysLeft(villager, day) == 0 && resting(level, villager)) {
				pass(level, villager);
			}
		}
	}

	/**
	 * {@code villager} is an elder from today (seen once, in the hall's round; with the village not loaded that day, the
	 * next time it is): the chronicle notes it, unless an Evergreen Charm already wrote them in as one. 34.18's packet
	 * with the elder look goes out from here.
	 */
	private static void becameElder(ServerLevel level, BlockPos hall, Villager villager) {
		ModAttachments.ELDER_NOTED.set(villager, true);
		if (!isAgeless(villager)) {
			Chronicle.atHall(level, hall, Chronicle.Kind.LIFE, Component.translatable("chronicle.aliveworkplace.elder", villager.getDisplayName()));
		}
	}

	/** Asleep, or (with no bed to sleep in, or kept awake) the night has come. */
	private static boolean resting(ServerLevel level, Villager villager) {
		long time = Math.floorMod(level.getDayTime(), VillageNeeds.DAY);
		return villager.isSleeping() || (time >= NIGHT_FROM && time < NIGHT_TO);
	}

	/**
	 * Counts {@code day} towards {@code villager}'s happy days, once: a mood of {@link #HAPPY_MOOD} or more adds a day, a
	 * lower one starts again from 0. Days nobody counted (the village wasn't loaded, moods were off) neither add nor
	 * break the run.
	 */
	public static void countMood(Villager villager, long day) {
		Streak streak = ModAttachments.HAPPY_STREAK.get(villager);
		if (streak != null && streak.day() >= day) {
			return;
		}
		Moods.Mood mood = Moods.of(villager);
		if (mood == null) {
			return;
		}
		int days = mood.score() >= HAPPY_MOOD ? (streak == null ? 0 : streak.days()) + 1 : 0;
		ModAttachments.HAPPY_STREAK.set(villager, new Streak(days, day));
	}

	/** Days running {@code villager}'s mood has been {@link #HAPPY_MOOD} or more. */
	public static int happyDays(Villager villager) {
		Streak streak = ModAttachments.HAPPY_STREAK.get(villager);
		return streak == null ? 0 : streak.days();
	}

	/**
	 * {@code villager} passes away of old age: the village is told, and they die as any villager does (so their work is
	 * handed back, the chronicle notes it, and they leave a grave an Undertaker can bring them back from).
	 */
	public static void pass(ServerLevel level, Villager villager) {
		Component who = villager.getDisplayName();
		BlockPos at = villager.blockPosition();
		DamageSource source = Lookup.holder(Lookup.registry(level.registryAccess(), Registries.DAMAGE_TYPE), OLD_AGE)
			.map(DamageSource::new).orElseGet(() -> level.damageSources().generic());
		villager.stopSleeping();
		villager.setHealth(0f);
		villager.die(source);
		VillageHalls.nearest(level, at).ifPresent(hall -> {
			Component told = Component.translatable(AGELESS ? "message.aliveworkplace.elder.passed_charm" : "message.aliveworkplace.elder.passed", who)
				.withStyle(ChatFormatting.GRAY);
			for (ServerPlayer p : level.getPlayers(p -> p.blockPosition().distSqr(hall) <= (double) VillageHalls.RADIUS * VillageHalls.RADIUS)) {
				Chat.chat(p, told);
			}
		});
	}

	/** An Undertaker brought {@code villager} back: an elder whose time had come has {@link #PASSING_DAYS} more days. */
	public static void onRevived(ServerLevel level, Villager villager) {
		long day = Chronicle.day(level);
		if (daysLeft(villager, day) == 0) {
			ModAttachments.PASSED_DAY.set(villager, day);
		}
	}

	// --- The Evergreen Charm --------------------------------------------------------------------------------------

	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity) {
		if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || player.isSpectator() || !(entity instanceof Villager villager)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(ModItems.EVERGREEN_CHARM)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		offer((ServerPlayer) player, villager, held);
		return InteractionResult.SUCCESS;
	}

	/** {@code player} offers {@code villager} a charm: used up (unless in creative) when taken, else refused with the reason and kept. */
	public static Offer offer(ServerPlayer player, Villager villager, ItemStack stack) {
		Offer offer = makeAgeless((ServerLevel) villager.level(), villager, player.getDisplayName());
		if (offer.outcome() == Outcome.ACCEPTED) {
			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
		} else {
			villager.playSound(SoundEvents.VILLAGER_NO, 1f, villager.getVoicePitch());
		}
		Chat.actionBar(player, offer.message());
		return offer;
	}

	/** The first good trait {@code villager} has, or null: a Legend, Gifted, a Master of their trade, happy for {@link #HAPPY_DAYS} days. */
	@Nullable
	public static Trait goodTrait(Villager villager) {
		if (io.github.jcondedata.aliveworkplace.legend.Legends.of(villager).isPresent()) {
			return Trait.LEGEND;
		}
		if (io.github.jcondedata.aliveworkplace.legend.Gifted.of(villager) != null) {
			return Trait.GIFTED;
		}
		VillagerProfession job = villager.getVillagerData().getProfession();
		if (job != VillagerProfession.NONE && job != VillagerProfession.NITWIT && villager.getVillagerData().getLevel() >= VillagerData.MAX_VILLAGER_LEVEL) {
			return Trait.MASTER;
		}
		return happyDays(villager) >= HAPPY_DAYS ? Trait.HAPPY : null;
	}

	/** Makes {@code villager} ageless (the charm given by {@code giver}), or says why not. */
	public static Offer makeAgeless(ServerLevel level, Villager villager, Component giver) {
		Component who = villager.getDisplayName();
		long day = Chronicle.day(level);
		if (!AGELESS) {
			return refused(Outcome.DISABLED, Component.translatable("message.aliveworkplace.evergreen.disabled"));
		}
		if (isAgeless(villager)) {
			return refused(Outcome.ALREADY, Component.translatable("message.aliveworkplace.evergreen.already", who));
		}
		if (!isElder(villager, day)) {
			long grown = AGES ? grownDays(villager, day) : -1;
			return refused(Outcome.NOT_AN_ELDER, grown < 0 ? Component.translatable("message.aliveworkplace.evergreen.not_elder", who)
				: Component.translatable("message.aliveworkplace.evergreen.not_elder_days", who, grown, ELDER_DAYS));
		}
		if (!PASSING) {
			return refused(Outcome.NOT_NEEDED, Component.translatable("message.aliveworkplace.evergreen.not_needed"));
		}
		Trait trait = goodTrait(villager);
		if (trait == null) {
			return refused(Outcome.NO_GOOD_TRAIT, Component.translatable("message.aliveworkplace.evergreen.no_trait", who, HAPPY_MOOD, HAPPY_DAYS, happyDays(villager)));
		}
		ModAttachments.AGELESS.set(villager, true);
		ModAttachments.PASSED_DAY.remove(villager);
		Chronicle.record(level, villager.blockPosition(), Chronicle.Kind.LIFE,
			Component.translatable("chronicle.aliveworkplace.ageless", who, giver, trait.text()));
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, villager.getX(), villager.getY() + 1.0, villager.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 1.2, villager.getZ(), 12, 0.4, 0.6, 0.4, 0.0);
		level.playSound(null, villager.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.NEUTRAL, 0.5f, 1.3f);
		level.playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1f, villager.getVoicePitch());
		return new Offer(Outcome.ACCEPTED, Component.translatable("message.aliveworkplace.evergreen.accepted", who, trait.text()).withStyle(ChatFormatting.GREEN), trait);
	}

	private static Offer refused(Outcome outcome, Component message) {
		return new Offer(outcome, message.copy().withStyle(ChatFormatting.RED), null);
	}

	// --- The hall's list ------------------------------------------------------------------------------------------

	/** One line of a villager's card on the hall's list, and whether it is the gold badge. */
	public record HallLine(Component text, boolean gold) {
	}

	/**
	 * An elder's lines on the hall's list: "Elder · grown 131 days"; for an ageless one the gold leaf badge
	 * ("❦ Ageless: will never leave us"); for one whose time is near, how many days are left and what would keep them.
	 */
	public static List<HallLine> hallLines(ServerLevel level, Villager villager) {
		long day = Chronicle.day(level);
		List<HallLine> lines = new ArrayList<>();
		boolean elder = isElder(villager, day);
		if (elder) {
			lines.add(new HallLine(Component.translatable("screen.aliveworkplace.hall.elder", grownDays(villager, day)), false));
		}
		if (isAgeless(villager)) {
			lines.add(new HallLine(Component.translatable("screen.aliveworkplace.hall.ageless"), true));
		} else if (elder) {
			long left = daysLeft(villager, day);
			if (left >= 0 && left <= WARN_DAYS) {
				lines.add(new HallLine(Component.translatable(left == 0 ? "screen.aliveworkplace.hall.elder_tonight"
					: AGELESS ? "screen.aliveworkplace.hall.elder_days_left_charm" : "screen.aliveworkplace.hall.elder_days_left", left), false));
			}
		}
		return lines;
	}

	private LifeStages() {
	}
}

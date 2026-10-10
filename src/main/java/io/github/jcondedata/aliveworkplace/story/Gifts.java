package io.github.jcondedata.aliveworkplace.story;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Gifts (ROADMAP 31.6). A {@code Gift} is Gift Wrap round any one item ({@link GiftItem.Recipe}); the item is kept in
 * the {@code gift} data component ({@link Wrapped}), with who wrapped it. Right-click a named villager with one: they
 * unwrap it (the item's particles, a paper rustle), say how they like it over their head and in the giver's chat, and
 * their friendship with the giver changes by the taste band's points ({@link Tastes}): loved +80, liked +45, neutral
 * +20, disliked −20, hated −40. The item goes into their chests ({@link #chests}).
 *
 * <p>Each player can give each villager one gift a day and {@link #PER_WEEK} a week (kept in the {@code friendship}
 * attachment, so it survives a restart); a refused gift stays with the player. On the villager's name day (every
 * {@link #NAME_DAY_EVERY} days, from their UUID: {@link #nameDay}) a gift counts {@link #NAME_DAY_FACTOR} times.
 * Villagers without a name (or without a hall, who keep no friendships) shake their head. A Bottle o' Enchanting is
 * liked unless a taste file says otherwise, and gives a worker {@link #BOTTLE_XP} XP. With {@code friendship} off a
 * Gift is handed back unopened.
 */
public final class Gifts {
	/** Gifts a player can give one villager in a week (of seven days, counted from the world's first day). */
	public static final int PER_WEEK = 2;
	public static final int WEEK = 7;
	/** A villager's name day comes round every this many days, and a gift then counts this many times. */
	public static final int NAME_DAY_EVERY = 28;
	public static final int NAME_DAY_FACTOR = 3;
	/** What a Bottle o' Enchanting gives a worker. */
	public static final int BOTTLE_XP = 15;
	/** How many lines a villager has for each taste band ({@code gift.aliveworkplace.<band>.<n>}). */
	public static final int LINES = 2;

	/** What a Gift holds: the wrapped item, and the name of who wrapped it ("" when no player did). */
	public record Wrapped(ItemStack item, String from) {
		public static final Codec<Wrapped> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.CODEC.fieldOf("item").forGetter(Wrapped::item),
			Codec.STRING.optionalFieldOf("from", "").forGetter(Wrapped::from)
		).apply(i, Wrapped::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Wrapped> STREAM_CODEC = StreamCodec.composite(
			ItemStack.STREAM_CODEC, Wrapped::item, ByteBufCodecs.STRING_UTF8, Wrapped::from, Wrapped::new);

		public Wrapped withFrom(String from) {
			return new Wrapped(item, from);
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Wrapped w && from.equals(w.from) && ItemStack.matches(item, w.item);
		}

		@Override
		public int hashCode() {
			return 31 * ItemStack.hashItemAndComponents(item) + from.hashCode();
		}
	}

	/** How giving a gift went. */
	public enum Outcome {
		/** Unwrapped: friendship changed and the item was put away. */
		GIVEN,
		/** {@code friendship} is off: handed back unopened. */
		OFF,
		/** The villager has no name, or no hall to keep friendships in: they shake their head. */
		NOT_NAMED,
		/** The Gift holds nothing (it wasn't made by wrapping). */
		EMPTY,
		/** This player already gave them a gift today. */
		DAY_LIMIT,
		/** This player already gave them {@link #PER_WEEK} gifts this week. */
		WEEK_LIMIT
	}

	/**
	 * What {@link #give} did: the outcome, the taste band and the friendship change (for {@link Outcome#GIVEN}), whether it
	 * was their name day, where the item went, and what the giver was told (the villager's line, or why not).
	 */
	public record Result(Outcome outcome, @Nullable Tastes.Band band, int change, boolean nameDay, @Nullable Stored stored, Component line) {
	}

	/** Where an unwrapped item ended up. */
	public enum Stored {
		/** The supply chests by their workstation. */
		CHESTS,
		/** The village's store (the kitchens' and Storehouses' chests). */
		STORE,
		/** Their own pockets: no chest had room. */
		POCKETS,
		/** On the ground at their feet: nowhere else had room. */
		DROPPED,
		/** Used up (a Bottle o' Enchanting a worker learnt from). */
		USED
	}

	public static void init() {
		Platform.get().onUseEntity(Gifts::use);
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, @Nullable net.minecraft.world.phys.EntityHitResult hit) {
		if (player.isSpectator() || !(entity instanceof Villager villager)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		if (!held.is(ModItems.GIFT)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		give((ServerPlayer) player, villager, held);
		return InteractionResult.SUCCESS;
	}

	// --- The gift item --------------------------------------------------------------------------------------------

	/** A Gift holding one of {@code item}, wrapped by {@code from} ("" when nobody is named). */
	public static ItemStack wrap(ItemStack item, String from) {
		ItemStack gift = new ItemStack(ModItems.GIFT);
		gift.set(ModComponents.GIFT, new Wrapped(item.copyWithCount(1), from));
		return gift;
	}

	/** What {@code gift} holds (empty when it holds nothing). */
	public static ItemStack contents(ItemStack gift) {
		Wrapped wrapped = gift.get(ModComponents.GIFT);
		return wrapped == null ? ItemStack.EMPTY : wrapped.item().copy();
	}

	/** Who wrapped {@code gift}, or "". */
	public static String from(ItemStack gift) {
		Wrapped wrapped = gift.get(ModComponents.GIFT);
		return wrapped == null ? "" : wrapped.from();
	}

	// --- Name days ------------------------------------------------------------------------------------------------

	/** Which day of every {@link #NAME_DAY_EVERY} is the name day of whoever has this UUID: 0 to 27. */
	public static int nameDay(UUID id) {
		return (int) Math.floorMod(id.getMostSignificantBits() * 31 + id.getLeastSignificantBits(), (long) NAME_DAY_EVERY);
	}

	/** How many days from {@code day} (as {@link Chronicle#day} counts) to {@code id}'s next name day; 0 on the day itself. */
	public static int daysToNameDay(UUID id, long day) {
		return (int) Math.floorMod(nameDay(id) - (day - 1), (long) NAME_DAY_EVERY);
	}

	public static boolean isNameDay(Villager villager, long day) {
		return daysToNameDay(villager.getUUID(), day) == 0;
	}

	/** The hall tooltip's line: "Name day: today", "tomorrow" or "in 12 days". */
	public static Component nameDayLine(Villager villager, long day) {
		int days = daysToNameDay(villager.getUUID(), day);
		return days == 0 ? Component.translatable("screen.aliveworkplace.hall.name_day.today")
			: days == 1 ? Component.translatable("screen.aliveworkplace.hall.name_day.tomorrow")
			: Component.translatable("screen.aliveworkplace.hall.name_day.in_days", days);
	}

	// --- Giving ---------------------------------------------------------------------------------------------------

	/** The week {@code day} is in (weeks of {@link #WEEK} days from the world's first day). */
	public static long week(long day) {
		return Math.floorDiv(day - 1, (long) WEEK);
	}

	/** Whether {@code villager} works a trade (a profession other than none and nitwit). */
	public static boolean isWorker(Villager villager) {
		VillagerProfession profession = villager.getVillagerData().getProfession();
		return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT && !villager.isBaby();
	}

	/**
	 * What {@code villager} thinks of {@code item} as a gift: their tastes ({@link Tastes}); a Bottle o' Enchanting
	 * that no taste file names is liked.
	 */
	public static Tastes.Band band(Villager villager, ItemStack item) {
		Tastes.Band named = Tastes.named(villager, item);
		if (named != null) {
			return named;
		}
		return item.is(Items.EXPERIENCE_BOTTLE) ? Tastes.Band.LIKED : Tastes.Band.NEUTRAL;
	}

	/**
	 * {@code player} gives {@code villager} the Gift {@code stack} (the stack in their hand: one is taken when the gift
	 * is accepted, unless they're in creative). The entry point of the right-click.
	 */
	public static Result give(ServerPlayer player, Villager villager, ItemStack stack) {
		ServerLevel level = (ServerLevel) villager.level();
		Component who = villager.getDisplayName();
		if (!Friendship.ENABLED) {
			return refuse(player, villager, Outcome.OFF, Component.translatable("message.aliveworkplace.gift.off", who), false);
		}
		if (!villager.hasCustomName()) {
			return refuse(player, villager, Outcome.NOT_NAMED, Component.translatable("message.aliveworkplace.gift.unnamed"), true);
		}
		if (!Friendship.eligible(villager)) {
			return refuse(player, villager, Outcome.NOT_NAMED, Component.translatable("message.aliveworkplace.gift.no_hall", who), true);
		}
		ItemStack item = contents(stack);
		if (item.isEmpty()) {
			return refuse(player, villager, Outcome.EMPTY, Component.translatable("message.aliveworkplace.gift.empty"), false);
		}
		long today = Chronicle.day(level);
		long week = week(today);
		Friendship.Bond bond = Friendship.of(villager).bond(player.getUUID());
		int thisWeek = bond.giftWeek() == week ? bond.giftsWeek() : 0;
		if (bond.giftDay() == today) {
			return say(level, villager, player, Outcome.DAY_LIMIT, Component.translatable("gift.aliveworkplace.refuse.day", player.getDisplayName()));
		}
		if (thisWeek >= PER_WEEK) {
			return say(level, villager, player, Outcome.WEEK_LIMIT, Component.translatable("gift.aliveworkplace.refuse.week", player.getDisplayName()));
		}

		// Unwrapped: the paper rustles and bits of the item fly.
		Tastes.Band band = band(villager, item);
		boolean nameDay = isNameDay(villager, today);
		level.playSound(null, villager, SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1f, 0.8f);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, item), villager.getX(), villager.getEyeY() - 0.3, villager.getZ(), 12, 0.25, 0.15, 0.25, 0.05);
		if (band.points < 0) {
			villager.setUnhappyCounter(40);
			villager.playSound(SoundEvents.VILLAGER_NO, 1f, villager.getVoicePitch());
		} else {
			villager.playSound(SoundEvents.VILLAGER_YES, 1f, villager.getVoicePitch());
		}
		int change = Friendship.add(villager, player, band.points * (nameDay ? NAME_DAY_FACTOR : 1));
		Friendship.gifted(villager, player, today, week, thisWeek + 1);

		Component line = Component.translatable("gift.aliveworkplace." + band.id() + "." + level.random.nextInt(LINES), item.getHoverName(), player.getDisplayName());
		if (nameDay) {
			line = Component.translatable("gift.aliveworkplace.name_day", line);
		}
		Stored stored = putAway(level, villager, item);
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		Chatter.say(level, villager, player, line.copy().withStyle(ChatFormatting.ITALIC));
		Chat.chat(player, Component.translatable("message.aliveworkplace.gift.said", who, line));
		return new Result(Outcome.GIVEN, band, change, nameDay, stored, line);
	}

	/** Not taken, with a word in the action bar; {@code shake}: the villager shakes their head. The gift stays with the player. */
	private static Result refuse(ServerPlayer player, Villager villager, Outcome outcome, Component line, boolean shake) {
		if (shake) {
			villager.setUnhappyCounter(40);
			villager.playSound(SoundEvents.VILLAGER_NO, 1f, villager.getVoicePitch());
		}
		Chat.actionBar(player, line.copy().withStyle(ChatFormatting.YELLOW));
		return new Result(outcome, null, 0, false, null, line);
	}

	/** Not taken, said by the villager (over their head and in the giver's chat). The gift stays with the player. */
	private static Result say(ServerLevel level, Villager villager, ServerPlayer player, Outcome outcome, Component line) {
		villager.setUnhappyCounter(40);
		villager.playSound(SoundEvents.VILLAGER_NO, 1f, villager.getVoicePitch());
		Chatter.say(level, villager, player, line.copy().withStyle(ChatFormatting.ITALIC));
		Chat.chat(player, Component.translatable("message.aliveworkplace.gift.said", villager.getDisplayName(), line));
		return new Result(outcome, null, 0, false, null, line);
	}

	// --- Where the item goes --------------------------------------------------------------------------------------

	/** The chests an unwrapped item goes into: the supply chests by {@code villager}'s workstation, then the village's store. */
	public static List<List<BlockPos>> chests(ServerLevel level, Villager villager) {
		List<List<BlockPos>> out = new ArrayList<>();
		Optional<GlobalPos> site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE);
		out.add(site.isPresent() && site.get().dimension().equals(level.dimension())
			? SupplyContainers.find(level, site.get().pos(), null) : List.of());
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		out.add(hall == null ? List.of() : VillageNeeds.store(level, hall.getBlockPos()));
		return out;
	}

	/**
	 * Puts an unwrapped {@code item} away: a worker learns from a Bottle o' Enchanting ({@link #BOTTLE_XP} XP, the bottle
	 * used up); anything else goes into their supply chests, else the village's store, else their own pockets, and is
	 * dropped at their feet only when none of those has room.
	 */
	static Stored putAway(ServerLevel level, Villager villager, ItemStack item) {
		if (item.is(Items.EXPERIENCE_BOTTLE) && isWorker(villager)) {
			BuilderLevels.addXp(level, villager, BOTTLE_XP, null);
			return Stored.USED;
		}
		List<List<BlockPos>> chests = chests(level, villager);
		ItemStack left = item.copy();
		if (!chests.get(0).isEmpty()) {
			left = SupplyContainers.insert(level, chests.get(0), left);
			if (left.isEmpty()) {
				return Stored.CHESTS;
			}
		}
		if (!chests.get(1).isEmpty()) {
			left = SupplyContainers.insert(level, chests.get(1), left);
			if (left.isEmpty()) {
				return Stored.STORE;
			}
		}
		left = villager.getInventory().addItem(left);
		if (left.isEmpty()) {
			return Stored.POCKETS;
		}
		villager.spawnAtLocation(left);
		return Stored.DROPPED;
	}

	private Gifts() {
	}
}

package io.github.jcondedata.aliveworkplace.story;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mail.Mail;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.shop.Shops;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jetbrains.annotations.Nullable;

/**
 * What friendship and titles are worth (ROADMAP 31.12).
 *
 * <ul>
 * <li><b>Cheaper trades</b> ({@link #prices}, at the end of {@code Villager.updateSpecialPrices}, on top of vanilla's
 * gossip and Hero of the Village): {@value #FIRST}% off at {@value #DISCOUNT_HEARTS} hearts and {@value #PER_HEART}%
 * more a heart, {@value #HEART_CAP}% at most; the title held in the villager's village adds 5% (Friend), 10% (Hero) or
 * 15% (Lord); {@value #CAP}% at most together. No price goes under 1; players' shops aren't touched.</li>
 * <li><b>Festival gifts</b> ({@link #festivalGifts}): at a festival each villager with {@value #GIFT_HEARTS} hearts or
 * more with a player there brings them their family's gift ({@link #gift}), once a festival. Heroes and Lords get Hero
 * of the Village II at festivals ({@link #heroLevel}).</li>
 * <li><b>Named after you</b> ({@link #nameBaby}): the first baby born to a couple where a parent has
 * {@value #BABY_HEARTS} hearts with a player is named after them, once per player per village, with a chronicle line.</li>
 * <li><b>Letters from friends</b> ({@link #parcels}): at {@value #PARCEL_HEARTS} hearts, once a week, the villager sends
 * the player a parcel through the village's post: a letter and their family's festival gift.</li>
 * </ul>
 */
public final class FriendPerks {
	public static final int DISCOUNT_HEARTS = 4;
	public static final int FIRST = 5;
	public static final int PER_HEART = 5;
	public static final int HEART_CAP = 30;
	public static final int CAP = 40;
	public static final int GIFT_HEARTS = 6;
	public static final int BABY_HEARTS = 8;
	public static final int PARCEL_HEARTS = 10;

	private static final ResourceLocation POKE_BALL = ResourceLocation.fromNamespaceAndPath("cobblemon", "poke_ball");
	private static final List<Item> FLOWERS = List.of(Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.ALLIUM, Items.OXEYE_DAISY);

	// --- Cheaper trades -------------------------------------------------------------------------------------------

	/** The percent off from hearts alone: 0 under {@value #DISCOUNT_HEARTS}, then 5% a heart from 5%, {@value #HEART_CAP}% at most. */
	public static int heartsPercent(int hearts) {
		return hearts < DISCOUNT_HEARTS ? 0 : Math.min(HEART_CAP, FIRST + PER_HEART * (hearts - DISCOUNT_HEARTS));
	}

	/** What a title adds: Friend 5%, Hero 10%, Lord 15%. */
	public static int titlePercent(Reputation.Title title) {
		return switch (title) {
			case FRIEND -> 5;
			case HERO -> 10;
			case LORD -> 15;
			default -> 0;
		};
	}

	/** The whole discount for {@code hearts} and {@code title}: {@value #CAP}% at most. */
	public static int percent(int hearts, Reputation.Title title) {
		return Math.min(CAP, heartsPercent(hearts) + titlePercent(title));
	}

	/** The discount {@code player} gets from {@code villager}: their hearts with them and their title in the villager's village. */
	public static int percent(ServerLevel level, Villager villager, Player player) {
		int hearts = Friendship.ENABLED && Friendship.eligible(villager) ? Friendship.hearts(Friendship.points(villager, player.getUUID())) : 0;
		VillageHallBlockEntity hall = CivicEffects.hallOf(villager);
		Reputation.Title title = hall == null ? Reputation.Title.STRANGER : Reputation.title(level, hall.getBlockPos(), player.getUUID());
		return percent(hearts, title);
	}

	/** At the end of {@code updateSpecialPrices}: every first price {@link #percent} cheaper (rounded, never under 1). */
	public static void prices(Villager villager, Player player) {
		if (!(villager.level() instanceof ServerLevel level) || Shops.isShopkeeper(villager)) {
			return;
		}
		int percent = percent(level, villager, player);
		if (percent <= 0) {
			return;
		}
		for (MerchantOffer offer : villager.getOffers()) {
			int off = off(offer.getBaseCostA().getCount(), percent);
			int room = offer.getCostA().getCount() - 1; // never under 1
			if (Math.min(off, room) > 0) {
				offer.addToSpecialPriceDiff(-Math.min(off, room));
			}
		}
	}

	/** How much {@code percent} takes off a price of {@code base}, rounded to the nearest. */
	public static int off(int base, int percent) {
		return (base * percent + 50) / 100;
	}

	// --- Festival gifts -------------------------------------------------------------------------------------------

	/** The gift a villager of {@code villager}'s job family brings. */
	public static ItemStack gift(Villager villager) {
		return gift(JobFamilies.of(villager), villager.getUUID());
	}

	/** The gift of {@code family} (the flowers picked by {@code who}). */
	public static ItemStack gift(JobFamilies.Family family, UUID who) {
		return switch (family) {
			case BUILDING -> new ItemStack(Items.STONE_BRICKS, 16);
			case MINING -> new ItemStack(Items.IRON_INGOT, 4);
			case LAND -> new ItemStack(Items.APPLE, 6);
			case ANIMALS -> new ItemStack(Items.COOKED_SALMON, 4);
			case KITCHEN -> new ItemStack(Items.PUMPKIN_PIE, 2);
			case LEARNING -> new ItemStack(Items.BOOK, 2);
			case HEALING -> PotionContents.createItemStack(Items.POTION, Potions.REGENERATION);
			case ARMS -> new ItemStack(Items.ARROW, 16);
			case TRADE -> new ItemStack(Items.EMERALD, 3);
			case MUSIC -> new ItemStack(Items.NOTE_BLOCK);
			case POKEMON -> BuiltInRegistries.ITEM.getOptional(POKE_BALL).map(i -> new ItemStack(i, 3)).orElseGet(() -> new ItemStack(Items.EMERALD, 3));
			case NONE -> new ItemStack(FLOWERS.get(Math.floorMod(who.hashCode(), FLOWERS.size())), 3);
		};
	}

	/** The Hero of the Village level (0 for I) {@code player} gets at a festival of the village round {@code hall}: II for Heroes and Lords. */
	public static int heroLevel(ServerLevel level, BlockPos hall, Player player) {
		return Reputation.title(level, hall, player.getUUID()).atLeast(Reputation.Title.HERO) ? 1 : 0;
	}

	/**
	 * While a festival is on: each named villager of the village with {@value #GIFT_HEARTS} hearts or more with
	 * {@code player} who hasn't yet today brings them their family's gift (at their feet, with a line in chat). Returns
	 * the gifts brought.
	 */
	public static int festivalGifts(ServerLevel level, BlockPos hall, ServerPlayer player) {
		if (!Friendship.ENABLED) {
			return 0;
		}
		long today = Chronicle.day(level);
		int brought = 0;
		for (Villager v : level.getEntitiesOfClass(Villager.class, VillageHalls.area(hall), Friendship::eligible)) {
			Friendship.Data data = Friendship.of(v);
			Friendship.Bond bond = data.bond(player.getUUID());
			if (bond.hearts() < GIFT_HEARTS || bond.festivalGift() == today) {
				continue;
			}
			ModAttachments.FRIENDSHIP.set(v, data.with(player.getUUID(), bond.withFestivalGift(today)));
			ItemStack gift = gift(v);
			ItemEntity item = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), gift.copy());
			item.setPickUpDelay(10);
			level.addFreshEntity(item);
			level.sendParticles(ParticleTypes.HEART, v.getX(), v.getEyeY() + 0.5, v.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
			level.playSound(null, v.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 0.8f, 1.1f);
			Chat.chat(player, Component.translatable("message.aliveworkplace.festival_gift", v.getDisplayName(), gift.getCount(), gift.getHoverName())
				.withStyle(ChatFormatting.LIGHT_PURPLE));
			brought++;
		}
		return brought;
	}

	// --- Named after you ------------------------------------------------------------------------------------------

	/**
	 * {@code baby} was just born to {@code mother} and {@code father}: if a parent has {@value #BABY_HEARTS} hearts with a
	 * player no baby of the village was named after yet (the best friend first), the baby takes that player's name, with
	 * a chronicle line. Returns the player, if any.
	 */
	public static Optional<UUID> nameBaby(Villager baby, Villager mother, Villager father) {
		if (!Friendship.ENABLED || !(baby.level() instanceof ServerLevel level)) {
			return Optional.empty();
		}
		VillageHallBlockEntity entity = CivicEffects.hallOf(mother);
		if (entity == null) {
			entity = CivicEffects.hallOf(father);
		}
		if (entity == null) {
			return Optional.empty();
		}
		BlockPos hall = entity.getBlockPos();
		List<Map.Entry<UUID, Friendship.Bond>> friends = new ArrayList<>();
		for (Villager parent : List.of(mother, father)) {
			for (Map.Entry<UUID, Friendship.Bond> e : Friendship.of(parent).players().entrySet()) {
				if (e.getValue().hearts() >= BABY_HEARTS && !e.getValue().name().isBlank()) {
					friends.add(e);
				}
			}
		}
		friends.sort(Comparator.comparingInt((Map.Entry<UUID, Friendship.Bond> e) -> e.getValue().points()).reversed()
			.thenComparing(e -> e.getValue().name()));
		for (Map.Entry<UUID, Friendship.Bond> e : friends) {
			if (Reputation.namedBaby(level, hall, e.getKey())) {
				continue;
			}
			String name = e.getValue().name();
			baby.setCustomName(Component.literal(name));
			Reputation.babyNamed(level, hall, e.getKey(), name);
			Chronicle.record(level, hall, Chronicle.Kind.BIRTH, Component.translatable("chronicle.aliveworkplace.named_after",
				mother.getDisplayName(), father.getDisplayName(), name));
			ServerPlayer online = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (online != null) {
				Chat.chat(online, Component.translatable("message.aliveworkplace.named_after", mother.getDisplayName(), father.getDisplayName(),
					VillageHalls.name(level, hall)).withStyle(ChatFormatting.LIGHT_PURPLE));
			}
			return Optional.of(e.getKey());
		}
		return Optional.empty();
	}

	// --- Letters from friends -------------------------------------------------------------------------------------

	/**
	 * The hall's round for {@code villager}: to every player they have {@value #PARCEL_HEARTS} hearts with and haven't
	 * written to this week, a parcel through the post (a letter and their festival gift), arriving at the next dawn at
	 * the player's mailbox, or waiting at the post office. Returns the parcels sent.
	 */
	public static int parcels(ServerLevel level, Villager villager) {
		if (!Friendship.ENABLED || !Friendship.eligible(villager)) {
			return 0;
		}
		long week = Gifts.week(Chronicle.day(level));
		Friendship.Data data = Friendship.of(villager);
		int sent = 0;
		for (Map.Entry<UUID, Friendship.Bond> e : data.players().entrySet()) {
			Friendship.Bond bond = e.getValue();
			if (bond.hearts() >= PARCEL_HEARTS && bond.parcelWeek() != week) {
				send(level, villager, e.getKey(), bond.name());
				data = data.with(e.getKey(), bond.withParcel(week));
				sent++;
			}
		}
		if (sent > 0) {
			ModAttachments.FRIENDSHIP.set(villager, data);
		}
		return sent;
	}

	/** Posts {@code villager}'s parcel to {@code player}: "Thought you'd like these. Dara" and their gift. */
	@Nullable
	static Parcel send(ServerLevel level, Villager villager, UUID player, String playerName) {
		String from = villager.getDisplayName().getString();
		ItemStack letter = Mail.letter(from, Component.translatable("letter.aliveworkplace.friend", from).getString());
		PostOffice office = PostOffice.get(level.getServer());
		Parcel parcel = office.post(villager.getUUID(), from, player, playerName, GlobalPos.of(level.dimension(), villager.blockPosition()),
			level.getGameTime(), new ArrayList<>(List.of(letter, gift(villager))));
		parcel.setStatus(Parcel.Status.IN_TRANSIT);
		office.changed();
		ServerPlayer online = level.getServer().getPlayerList().getPlayer(player);
		if (online != null) {
			Chat.chat(online, Component.translatable("message.aliveworkplace.friend_parcel", villager.getDisplayName()).withStyle(ChatFormatting.GOLD));
		}
		return parcel;
	}

	private FriendPerks() {
	}
}

package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.EdictProposals;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendGuests;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.mail.MailboxBlockEntity;
import io.github.jcondedata.aliveworkplace.mail.Parcel;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.mixin.VillagerAccessor;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.story.FriendPerks;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import io.github.jcondedata.aliveworkplace.story.JobFamilies;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Reputation.Title;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;

/**
 * What friendship and titles are worth (ROADMAP 31.12): the trade discount at every heart and title step and its caps,
 * through vanilla's own {@code updateSpecialPrices}; the festival gift once per villager per festival (and saved), each
 * family's gift, Hero of the Village II for Heroes through the festival's round; the baby named after a friend once; the
 * weekly parcel arriving in the mailbox (or waiting at the post office); {@code title_at_least} and the Legends' visit
 * ways; a Lord's and a Hero's say in edicts; the sentences a player reads; and friendship off.
 */
public class FriendPerksGameTests implements FabricGameTest {
	static final String AREA = HeartEventGameTests.AREA;
	static final BlockPos HALL = HeartEventGameTests.HALL;
	static final String ME = HeartEventGameTests.ME;

	/** A stand-in player of the village round {@code hall} holding {@code points} of standing (offline), for tests whose Legends need a titled player there. */
	static UUID titled(ServerLevel level, BlockPos hall, int points) {
		UUID ally = UUID.nameUUIDFromBytes(("ally" + hall).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		Reputation.add(level, hall, ally, "Ally", points - Reputation.points(level, hall, ally));
		return ally;
	}

	private static Villager worker(GameTestHelper helper, BlockPos at, String name, VillagerProfession job) {
		Villager v = HeartEventGameTests.villager(helper, at, name);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		return v;
	}

	/** Exactly {@code hearts} hearts between {@code v} and {@code player}. */
	private static void hearts(Villager v, ServerPlayer player, int hearts) {
		Friendship.add(v, player, hearts * Friendship.PER_HEART - Friendship.points(v, player.getUUID()));
	}

	// --- Cheaper trades -------------------------------------------------------------------------------------------

	/**
	 * 5% at 4 hearts and 5% more a heart to 30%; a title adds 5, 10 or 15; 40% at most. Through the trade screen's own
	 * price update: a 20-emerald trade at every heart step and title, a 1-emerald one never under 1.
	 */
	//$ gametest_ticks_batch AREA '60' '"friendPerksPrices"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "friendPerksPrices")
	public void tradesCheaperAtEveryStep(GameTestHelper helper) {
		int[] byHearts = {0, 0, 0, 0, 5, 10, 15, 20, 25, 30, 30};
		for (int h = 0; h <= 10; h++) {
			helper.assertTrue(FriendPerks.heartsPercent(h) == byHearts[h], h + " hearts: " + FriendPerks.heartsPercent(h));
			for (Title t : Title.values()) {
				int want = Math.min(40, byHearts[h] + FriendPerks.titlePercent(t));
				helper.assertTrue(FriendPerks.percent(h, t) == want, h + " hearts, " + t + ": " + FriendPerks.percent(h, t));
			}
		}
		helper.assertTrue(FriendPerks.titlePercent(Title.FRIEND) == 5 && FriendPerks.titlePercent(Title.HERO) == 10
			&& FriendPerks.titlePercent(Title.LORD) == 15 && FriendPerks.titlePercent(Title.STRANGER) == 0, "the titles' share");
		helper.assertTrue(FriendPerks.percent(10, Title.LORD) == 40 && FriendPerks.percent(10, Title.FRIEND) == 35, "the 40% cap");
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ReputationGameTests.reputation(helper, HALL, "Thornholm");
			ServerPlayer me = ReputationGameTests.player(helper, new BlockPos(5, 2, 5));
			Villager dara = worker(helper, new BlockPos(4, 2, 9), "Dara", VillagerProfession.FARMER);
			Villager shop = worker(helper, new BlockPos(13, 2, 9), "Odo", io.github.jcondedata.aliveworkplace.registry.ModVillagers.SHOPKEEPER);
			for (Villager v : List.of(dara, shop)) {
				v.getOffers().clear();
				v.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.BREAD), 12, 1, 0.05f));
				v.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.APPLE), 12, 1, 0.05f));
			}
			for (int h = 0; h <= 10; h++) {
				hearts(dara, me, h);
				int want = 20 - (20 * byHearts[h] + 50) / 100;
				helper.assertTrue(price(dara, me, 0) == want, h + " hearts: " + price(dara, me, 0) + ", not " + want);
				helper.assertTrue(price(dara, me, 1) == 1, "a 1-emerald trade at " + h + " hearts: " + price(dara, me, 1));
			}
			// With 10 hearts (30%): a Friend 35% (13), a Hero 40% (12), a Lord still 40%.
			Reputation.add(level, hall, me, 50);
			helper.assertTrue(price(dara, me, 0) == 13, "a Friend with 10 hearts: " + price(dara, me, 0));
			Reputation.add(level, hall, me, 250);
			helper.assertTrue(price(dara, me, 0) == 12, "a Hero with 10 hearts: " + price(dara, me, 0));
			entity.setRank(VillageRanks.Rank.TOWN);
			Reputation.add(level, hall, me, 700);
			helper.assertTrue(Reputation.title(level, hall, me.getUUID()) == Title.LORD && price(dara, me, 0) == 12, "a Lord with 10 hearts: " + price(dara, me, 0));
			// A Lord with no hearts: 15% (17).
			hearts(dara, me, 0);
			helper.assertTrue(price(dara, me, 0) == 17, "a Lord with no hearts: " + price(dara, me, 0));
			// A player's shop isn't touched, friends or not.
			hearts(shop, me, 10);
			helper.assertTrue(price(shop, me, 0) == 20, "a shopkeeper's price: " + price(shop, me, 0));
			// Friendship off: only the title counts.
			hearts(dara, me, 10);
			boolean was = Friendship.ENABLED;
			Friendship.ENABLED = false;
			try {
				helper.assertTrue(price(dara, me, 0) == 17, "friendship off: " + price(dara, me, 0));
			} finally {
				Friendship.ENABLED = was;
			}
			helper.succeed();
		});
	}

	/** The price of {@code v}'s offer {@code i} for {@code player}, as the trade screen sets it (and resets it after). */
	private static int price(Villager v, ServerPlayer player, int i) {
		MerchantOffer offer = v.getOffers().get(i);
		v.getOffers().forEach(MerchantOffer::resetSpecialPriceDiff);
		((VillagerAccessor) v).aliveworkplace$updateSpecialPrices(player);
		int price = offer.getCostA().getCount();
		v.getOffers().forEach(MerchantOffer::resetSpecialPriceDiff);
		return price;
	}

	// --- Festival gifts -------------------------------------------------------------------------------------------

	/** Each family's gift, as the spec lists them. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachFamilysGift(GameTestHelper helper) {
		UUID who = UUID.randomUUID();
		check(helper, FriendPerks.gift(JobFamilies.Family.BUILDING, who), Items.STONE_BRICKS, 16);
		check(helper, FriendPerks.gift(JobFamilies.Family.MINING, who), Items.IRON_INGOT, 4);
		check(helper, FriendPerks.gift(JobFamilies.Family.LAND, who), Items.APPLE, 6);
		check(helper, FriendPerks.gift(JobFamilies.Family.ANIMALS, who), Items.COOKED_SALMON, 4);
		check(helper, FriendPerks.gift(JobFamilies.Family.KITCHEN, who), Items.PUMPKIN_PIE, 2);
		check(helper, FriendPerks.gift(JobFamilies.Family.LEARNING, who), Items.BOOK, 2);
		ItemStack potion = FriendPerks.gift(JobFamilies.Family.HEALING, who);
		helper.assertTrue(potion.is(Items.POTION) && potion.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.REGENERATION),
			"healing's gift: " + potion);
		check(helper, FriendPerks.gift(JobFamilies.Family.ARMS, who), Items.ARROW, 16);
		check(helper, FriendPerks.gift(JobFamilies.Family.TRADE, who), Items.EMERALD, 3);
		check(helper, FriendPerks.gift(JobFamilies.Family.MUSIC, who), Items.NOTE_BLOCK, 1);
		ItemStack balls = FriendPerks.gift(JobFamilies.Family.POKEMON, who);
		helper.assertTrue(balls.getCount() == 3, "Pokémon's gift: " + balls);
		ItemStack flowers = FriendPerks.gift(JobFamilies.Family.NONE, who);
		helper.assertTrue(flowers.getCount() == 3 && flowers.is(net.minecraft.tags.ItemTags.SMALL_FLOWERS), "no trade's gift: " + flowers);
		helper.succeed();
	}

	private static void check(GameTestHelper helper, ItemStack stack, net.minecraft.world.item.Item item, int count) {
		helper.assertTrue(stack.is(item) && stack.getCount() == count, "a gift of " + stack + ", not " + count + " " + item);
	}

	/**
	 * At a festival a villager with 6 hearts brings the player their family's gift, once a festival (and that is saved);
	 * one with 5 hearts doesn't; with friendship off nobody does.
	 */
	//$ gametest_ticks_batch AREA '60' '"friendPerksGifts"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "friendPerksGifts")
	public void oneFestivalGiftPerVillager(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ReputationGameTests.reputation(helper, HALL, "Thornholm");
			ServerPlayer me = ReputationGameTests.player(helper, new BlockPos(5, 2, 5));
			Villager mason = worker(helper, new BlockPos(4, 2, 9), "Dara", VillagerProfession.MASON);
			Villager farmer = worker(helper, new BlockPos(13, 2, 9), "Bo", VillagerProfession.FARMER);
			hearts(mason, me, 6);
			hearts(farmer, me, 5);
			boolean was = Friendship.ENABLED;
			Friendship.ENABLED = false;
			try {
				helper.assertTrue(FriendPerks.festivalGifts(level, hall, me) == 0, "a gift with friendship off");
			} finally {
				Friendship.ENABLED = was;
			}
			int first = FriendPerks.festivalGifts(level, hall, me);
			int again = FriendPerks.festivalGifts(level, hall, me);
			helper.assertTrue(first == 1 && again == 0, "gifts: " + first + " then " + again);
			List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(me.blockPosition()).inflate(2));
			helper.assertTrue(items.size() == 1 && items.get(0).getItem().is(Items.STONE_BRICKS) && items.get(0).getItem().getCount() == 16,
				"at my feet: " + items.stream().map(ItemEntity::getItem).toList());
			items.forEach(ItemEntity::discard);
			long today = Chronicle.day(level);
			Friendship.Bond bond = Friendship.of(mason).bond(me.getUUID());
			helper.assertTrue(bond.festivalGift() == today, "the day kept: " + bond.festivalGift());
			// Saved and read back: still given today.
			Tag saved = Friendship.Data.CODEC.encodeStart(NbtOps.INSTANCE, Friendship.of(mason)).getOrThrow();
			Friendship.Data back = Friendship.Data.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
			helper.assertTrue(back.bond(me.getUUID()).festivalGift() == today && back.bond(me.getUUID()).parcelWeek() == -1, "read back: " + back);
			// An older save without the fields: none given yet.
			net.minecraft.nbt.CompoundTag old = new net.minecraft.nbt.CompoundTag();
			old.putInt("points", 700);
			Friendship.Bond legacy = Friendship.Bond.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
			helper.assertTrue(legacy.festivalGift() == -1 && legacy.parcelWeek() == -1, "an old save: " + legacy);
			helper.succeed();
		});
	}

	/** Through the festival's own round: a Hero is a Hero of the Village II, a Stranger I; and the friend's gift comes too. */
	//$ gametest_ticks_batch AREA '60' '"friendPerksHero"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "friendPerksHero")
	public void heroesGetHeroOfTheVillageTwo(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ReputationGameTests.reputation(helper, HALL, "Thornholm");
			long time = level.getDayTime();
			long festivalDay = entity.festivalDay();
			Leftovers.after(helper, () -> {
				level.setDayTime(time);
				entity.setFestivalDay(festivalDay);
			});
			ServerPlayer hero = ReputationGameTests.player(helper, new BlockPos(5, 2, 5));
			ServerPlayer stranger = ReputationGameTests.player(helper, new BlockPos(13, 2, 5));
			Reputation.add(level, hall, hero, 300);
			Villager chef = worker(helper, new BlockPos(4, 2, 9), "Dara", VillagerProfession.LIBRARIAN);
			hearts(chef, hero, 6);
			level.setDayTime((level.getDayTime() / 24000L) * 24000L + Festivals.START + 500);
			entity.setFestivalDay(Chronicle.day(level));
			helper.assertTrue(Festivals.isOn(level, hall), "no festival on");
			Festivals.round(level, hall, entity, 6);
			MobEffectInstance mine = hero.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
			MobEffectInstance theirs = stranger.getEffect(MobEffects.HERO_OF_THE_VILLAGE);
			helper.assertTrue(mine != null && mine.getAmplifier() == 1, "the Hero's: " + mine);
			helper.assertTrue(theirs != null && theirs.getAmplifier() == 0, "the Stranger's: " + theirs);
			List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, new AABB(hero.blockPosition()).inflate(2));
			helper.assertTrue(items.size() == 1 && items.get(0).getItem().is(Items.BOOK) && items.get(0).getItem().getCount() == 2,
				"the librarian's gift: " + items.stream().map(ItemEntity::getItem).toList());
			items.forEach(ItemEntity::discard);
			Festivals.round(level, hall, entity, 6);
			level.setDayTime(time); // the clock is shared with the batches running beside this one: put it back at once
			entity.setFestivalDay(festivalDay);
			helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, new AABB(hero.blockPosition()).inflate(2)).isEmpty(), "a second gift");
			helper.assertTrue(new ItemStack(Items.BOOK, 2).getHoverName().getString().equals("Book")
				&& net.minecraft.network.chat.Component.translatable("message.aliveworkplace.festival_gift", "Dara", 2, "Book").getString()
				.equals("Dara brings you a festival gift: Book (2)."), "the gift's line");
			helper.succeed();
		});
	}

	// --- Named after you ------------------------------------------------------------------------------------------

	/** The first baby of a couple where a parent has 8 hearts with a player takes their name, once per player per village; 7 hearts isn't enough. */
	//$ gametest_ticks_batch AREA '60' '"friendPerksBaby"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "friendPerksBaby")
	public void babyNamedAfterYouOnce(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ReputationGameTests.reputation(helper, HALL, "Thornholm");
			ServerPlayer me = ReputationGameTests.player(helper, new BlockPos(5, 2, 5));
			Villager mother = HeartEventGameTests.villager(helper, new BlockPos(4, 2, 9), "Ana");
			Villager father = HeartEventGameTests.villager(helper, new BlockPos(5, 2, 9), "Bo");
			hearts(mother, me, 7);
			Villager first = born(helper, mother, father);
			helper.assertTrue(!first.hasCustomName(), "named at 7 hearts: " + first.getCustomName());
			hearts(father, me, 8);
			Villager second = born(helper, mother, father);
			helper.assertTrue(second.hasCustomName() && second.getCustomName().getString().equals(ME), "named: " + second.getCustomName());
			helper.assertTrue(Reputation.namedBaby(level, hall, me.getUUID()), "not noted");
			List<String> lines = entity.chronicle().stream().map(e -> e.text().getString()).toList();
			helper.assertTrue(lines.contains("Ana and Bo named their baby " + ME + ", after a dear friend"), "the chronicle: " + lines);
			Villager third = born(helper, mother, father);
			helper.assertTrue(!third.hasCustomName(), "named after me twice: " + third.getCustomName());
			List.of(first, second, third).forEach(Villager::discard);
			helper.succeed();
		});
	}

	private static Villager born(GameTestHelper helper, Villager mother, Villager father) {
		Villager baby = EntityType.VILLAGER.create(helper.getLevel());
		baby.moveTo(mother.getX(), mother.getY(), mother.getZ(), 0, 0);
		baby.setAge(-24000);
		baby.setNoAi(true);
		Families.born(baby, mother, father);
		helper.getLevel().addFreshEntity(baby);
		return baby;
	}

	// --- Letters from friends -------------------------------------------------------------------------------------

	/**
	 * At 10 hearts a villager sends a parcel once a week: a letter ("Thought you'd like these. Dara") and their gift, in the
	 * mailbox at dawn; 9 hearts sends nothing; without a mailbox it waits at the post office.
	 */
	//$ gametest_ticks_batch AREA '60' '"friendPerksParcel"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "friendPerksParcel")
	public void weeklyParcelArrives(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			ReputationGameTests.reputation(helper, HALL, "Thornholm");
			ServerPlayer me = ReputationGameTests.player(helper, new BlockPos(5, 2, 5));
			PostOffice office = PostOffice.get(level.getServer());
			helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.MAILBOX);
			MailboxBlockEntity box = helper.getBlockEntity(new BlockPos(2, 2, 2));
			box.setOwner(me.getUUID(), ME);
			GlobalPos at = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(2, 2, 2)));
			office.register(me.getUUID(), at);
			Leftovers.after(helper, () -> {
				office.unregister(me.getUUID(), at);
				office.parcels().removeIf(p -> p.to().equals(me.getUUID()));
			});
			Villager dara = worker(helper, new BlockPos(4, 2, 9), "Dara", VillagerProfession.LIBRARIAN);
			hearts(dara, me, 9);
			helper.assertTrue(FriendPerks.parcels(level, dara) == 0, "a parcel at 9 hearts");
			hearts(dara, me, 10);
			helper.assertTrue(FriendPerks.parcels(level, dara) == 1, "no parcel at 10 hearts");
			helper.assertTrue(FriendPerks.parcels(level, dara) == 0, "a second parcel the same week");
			helper.assertTrue(Friendship.of(dara).bond(me.getUUID()).parcelWeek() >= 0, "the week isn't kept");
			office.dawn(level.getServer());
			List<ItemStack> inBox = new ArrayList<>();
			for (int i = 0; i < box.getContainerSize(); i++) {
				if (!box.getItem(i).isEmpty()) {
					inBox.add(box.getItem(i));
				}
			}
			ItemStack letter = inBox.stream().filter(s -> s.is(Items.WRITTEN_BOOK)).findFirst().orElse(ItemStack.EMPTY);
			WrittenBookContent text = letter.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(text != null && text.author().equals("Dara") && text.pages().get(0).get(false).getString().equals("Thought you'd like these. Dara"),
				"the letter: " + text);
			helper.assertTrue(inBox.stream().anyMatch(s -> s.is(Items.BOOK) && s.getCount() == 2), "the gift: " + inBox);
			// No mailbox: it waits at the post office.
			office.unregister(me.getUUID(), at);
			hearts(dara, me, 10);
			Friendship.Data data = Friendship.of(dara);
			io.github.jcondedata.aliveworkplace.registry.ModAttachments.FRIENDSHIP.set(dara, data.with(me.getUUID(), data.bond(me.getUUID()).withParcel(-1)));
			helper.assertTrue(FriendPerks.parcels(level, dara) == 1, "no parcel the next week");
			office.dawn(level.getServer());
			helper.assertTrue(office.parcels().stream().anyMatch(p -> p.to().equals(me.getUUID()) && p.status() == Parcel.Status.IN_TRANSIT),
				"not waiting at the post office");
			helper.assertTrue(io.github.jcondedata.aliveworkplace.mail.Mail.collectAtDesk(me, true) == 1, "not collected at the desk");
			helper.succeed();
		});
	}

	// --- Legends --------------------------------------------------------------------------------------------------

	/**
	 * {@code title_at_least}: some player's title in the village, or one player's; a bad title fails the file; with
	 * reputation off it holds. The Legends' visit ways: the Architect waits for a Hero, the Chef for a Friend.
	 */
	//$ gametest_ticks_batch AREA '60' '"friendPerksTitleRule"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "friendPerksTitleRule")
	public void titleAtLeast(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			ReputationGameTests.reputation(helper, HALL, "Thornholm");
			ServerPlayer me = ReputationGameTests.player(helper, new BlockPos(5, 2, 5));
			ServerPlayer other = ReputationGameTests.player(helper, new BlockPos(13, 2, 5));
			Condition friend = Conditions.parse(JsonParser.parseString("{\"type\": \"title_at_least\", \"title\": \"friend\"}").getAsJsonObject());
			Condition hero = Conditions.parse(JsonParser.parseString("{\"type\": \"title_at_least\", \"title\": \"Hero\"}").getAsJsonObject());
			boolean threw = false;
			try {
				Conditions.parse(JsonParser.parseString("{\"type\": \"title_at_least\", \"title\": \"king\"}").getAsJsonObject());
			} catch (IllegalArgumentException e) {
				threw = true;
			}
			helper.assertTrue(threw, "a bad title read");
			helper.assertTrue(!friend.met(level, hall) && !hero.met(level, hall), "met with nobody known");
			helper.assertTrue(friend.progress(level, hall).line().getString().equals("A player who is at least Friend of the village (the best now: Stranger)"),
				"the line: " + friend.progress(level, hall).line().getString());
			Legend architect = Legends.get(AliveWorkplace.id("master_architect")).orElseThrow();
			Legend chef = Legends.get(AliveWorkplace.id("grand_chef")).orElseThrow();
			helper.assertTrue(!LegendGuests.wayMet(level, hall, architect.ways("visit").get(0)) && !LegendGuests.wayMet(level, hall, chef.ways("visit").get(0)),
				"a Legend's visit with nobody known");
			Reputation.add(level, hall, me, 50);
			helper.assertTrue(friend.met(level, hall) && !hero.met(level, hall), "a Friend: " + friend.met(level, hall) + ", " + hero.met(level, hall));
			helper.assertTrue(friend.met(level, hall, null, me) && !friend.met(level, hall, null, other), "per player");
			helper.assertTrue(LegendGuests.wayMet(level, hall, chef.ways("visit").get(0)) && !LegendGuests.wayMet(level, hall, architect.ways("visit").get(0)),
				"a Friend: the Chef may come, the Architect may not");
			Reputation.add(level, hall, me, 250);
			helper.assertTrue(hero.met(level, hall) && LegendGuests.wayMet(level, hall, architect.ways("visit").get(0)), "a Hero");
			Reputation.add(level, hall, me, -300);
			Reputation.ENABLED = false;
			helper.assertTrue(friend.met(level, hall) && hero.met(level, hall, null, other), "reputation off: titles can't be earned, so they don't count");
			Reputation.ENABLED = true;
			// Every shipped Legend that visits has the title condition on its visit way.
			for (Legend l : Legends.all()) {
				if (!l.id().getNamespace().equals(AliveWorkplace.MOD_ID)) {
					continue;
				}
				for (var way : l.ways("visit")) {
					String want = l.rarity().name().equalsIgnoreCase("rare") ? "friend" : "hero";
					helper.assertTrue(way.has("conditions") && way.toString().contains("\"title\":\"" + want + "\""), l.id() + "'s visit: " + way);
				}
			}
			helper.succeed();
		});
	}

	// --- A say in edicts ------------------------------------------------------------------------------------------

	/**
	 * A Lord may enact and lift edicts as the owner can; a Hero may only propose one, which the owner gets with [Approve]
	 * and [Refuse]; approved, it is proclaimed. A Stranger is refused.
	 */
	//$ gametest_ticks_batch AREA '60' '"friendPerksEdicts"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "friendPerksEdicts")
	public void lordsSetEdictsHeroesPropose(GameTestHelper helper) {
		HeartEventGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			VillageHallBlockEntity entity = ReputationGameTests.reputation(helper, HALL, "Thornholm");
			ServerPlayer owner = ReputationGameTests.player(helper, new BlockPos(3, 2, 3));
			ServerPlayer hero = ReputationGameTests.player(helper, new BlockPos(5, 2, 5));
			ServerPlayer lord = ReputationGameTests.player(helper, new BlockPos(13, 2, 5));
			ServerPlayer stranger = ReputationGameTests.player(helper, new BlockPos(13, 2, 13));
			boolean edicts = Edicts.ENABLED;
			Edicts.ENABLED = true;
			Leftovers.after(helper, () -> {
				Edicts.ENABLED = edicts;
				entity.setOwner(null, "");
				entity.setEdicts(List.of());
			});
			entity.setOwner(owner.getUUID(), "Jesse");
			entity.setRank(VillageRanks.Rank.TOWN);
			entity.setEdicts(List.of());
			Reputation.add(level, hall, hero, 300);
			Reputation.add(level, hall, lord, 1000);
			helper.assertTrue(Reputation.maySetEdicts(level, hall, owner) && Reputation.maySetEdicts(level, hall, lord), "the owner and the Lord");
			helper.assertTrue(!Reputation.maySetEdicts(level, hall, hero) && Reputation.mayProposeEdicts(level, hall, hero), "the Hero");
			helper.assertTrue(!Reputation.maySetEdicts(level, hall, stranger) && !Reputation.mayProposeEdicts(level, hall, stranger), "the Stranger");
			List<Edicts.Edict> all = Edicts.all();
			helper.assertTrue(all.size() >= 2, "edicts: " + all);
			Edicts.Edict first = all.get(0);
			Edicts.Result refused = Edicts.proclaim(level, hall, stranger, first);
			helper.assertTrue(!refused.done() && refused.message().getString().equals("Only Jesse, their friends and the village's Lords can proclaim edicts in Thornholm."),
				"the Stranger: " + refused.message().getString());
			helper.assertTrue(!Edicts.proclaim(level, hall, hero, first).done(), "the Hero proclaimed it");
			Edicts.Result byLord = Edicts.proclaim(level, hall, lord, first);
			helper.assertTrue(byLord.done(), "the Lord: " + byLord.message().getString());
			// The Hero proposes the next one that doesn't clash; the owner approves.
			Edicts.Edict next = all.stream().filter(e -> !e.id().equals(first.id()) && !e.excludes(first) && !first.excludes(e)).findFirst().orElseThrow();
			Optional<EdictProposals.Proposal> proposal = EdictProposals.propose(level, hall, hero, next);
			helper.assertTrue(proposal.isPresent() && EdictProposals.pending().containsKey(proposal.get().id()), "no proposal");
			helper.assertTrue(!EdictProposals.answer(hero, proposal.get().id(), true), "the Hero approved their own");
			helper.assertTrue(EdictProposals.answer(owner, proposal.get().id(), true), "not proclaimed on approval");
			helper.assertTrue(entity.edicts().stream().anyMatch(f -> f.id().equals(next.id().toString())), "not in force: " + entity.edicts());
			helper.assertTrue(!EdictProposals.answer(owner, proposal.get().id(), true), "answered twice");
			// A refusal: nothing changes.
			entity.setEdicts(List.of());
			Optional<EdictProposals.Proposal> again = EdictProposals.propose(level, hall, hero, next);
			helper.assertTrue(again.isPresent() && !EdictProposals.answer(owner, again.get().id(), false) && entity.edicts().isEmpty(), "refused yet proclaimed");
			// The lines a player reads.
			helper.assertTrue(net.minecraft.network.chat.Component.translatable("message.aliveworkplace.edict.proposal", "Ann", "Curfew", "Thornholm", "[Approve]", "[Refuse]")
				.getString().equals("Ann, a Hero of Thornholm, proposes the edict Curfew. [Approve] [Refuse]"), "the proposal's line");
			helper.succeed();
		});
	}
}

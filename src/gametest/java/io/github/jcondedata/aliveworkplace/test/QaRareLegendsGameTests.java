package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Anthems;
import io.github.jcondedata.aliveworkplace.hall.Banquets;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.PlayerBank;
import io.github.jcondedata.aliveworkplace.hall.TradeFairs;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.BanquetPower;
import io.github.jcondedata.aliveworkplace.legend.BardLaureate;
import io.github.jcondedata.aliveworkplace.legend.Beastmaster;
import io.github.jcondedata.aliveworkplace.legend.Founder;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.StrangeMoods;
import io.github.jcondedata.aliveworkplace.legend.TradeFairPower;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;

/**
 * Independent QA for the rare Legends, written from the ROADMAP specs (29.17 Merchant Prince, 29.18 Grand Chef, 29.19
 * Bard Laureate, 29.20 Beastmaster, 29.23 Founder) rather than from the code: the boundaries from both sides (a week's
 * interest, a fair's 10 days, 3 bones, 16 blocks of song, a Master's level), per-player accounts, never more than 10
 * stacks, the village's edge, the Legends switch, and the Legend gone.
 */
public class QaRareLegendsGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final long DAY = VillageNeeds.DAY;
	private static final ResourceLocation PRINCE = AliveWorkplace.id("merchant_prince");
	private static final ResourceLocation CHEF = AliveWorkplace.id("grand_chef");

	// --- Shared setup ------------------------------------------------------------------------------------------------

	private static VillageHallBlockEntity entity(GameTestHelper helper) {
		return (VillageHallBlockEntity) helper.getBlockEntity(HALL);
	}

	private static BlockPos hall(GameTestHelper helper) {
		return helper.absolutePos(HALL);
	}

	/** A hall at {@link #HALL}, a village of radius 14; afterwards the villagers, the records and the caches are gone. */
	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 14;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Anthems.forget();
		Leftovers.after(helper, () -> {
			ServerLevel level = helper.getLevel();
			VillageHalls.RADIUS = radius;
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(32), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			level.getEntitiesOfClass(WanderingTrader.class, helper.getBounds().inflate(32), t -> true).forEach(net.minecraft.world.entity.Entity::discard);
			Leftovers.clear(helper);
			Leftovers.players(helper);
			level.setBlockAndUpdate(hall(helper), Blocks.AIR.defaultBlockState());
			Anthems.forget();
			Banquets.forget();
			VillageNeeds.forget();
			LegendPowers.forget();
			Moods.forget();
			Founder.forgetStands();
		});
	}

	/** Runs {@code body} with only {@code ids} on the roster, moods and needs off, Legends on; then puts it all back. */
	private static void staged(GameTestHelper helper, List<ResourceLocation> ids, Consumer<List<Legend>> body) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		boolean strange = StrangeMoods.ENABLED;
		boolean legends = Legends.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.ENABLED = true;
			Legends.reload(level.getServer().getResourceManager());
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			List<Legend> list = new ArrayList<>();
			for (ResourceLocation id : ids) {
				Legend l = Legends.get(id).orElseThrow(() -> new AssertionError(id + " didn't load"));
				map.put(id, l);
				list.add(l);
			}
			Legends.setForTest(map);
			LegendPowers.forget();
			body.accept(list);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
			StrangeMoods.ENABLED = strange;
			Legends.ENABLED = legends;
			level.setDayTime(time);
			Moods.forget();
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
			Founder.forgetStands();
		}
	}

	private static void staged(GameTestHelper helper, ResourceLocation id, Consumer<Legend> body) {
		staged(helper, List.of(id), l -> body.accept(l.get(0)));
	}

	/** Runs {@code check} with the Legends switch off, then on again. */
	private static void legendsOff(Runnable check) {
		boolean on = Legends.ENABLED;
		try {
			Legends.ENABLED = false;
			LegendPowers.forget();
			check.run();
		} finally {
			Legends.ENABLED = on;
			LegendPowers.forget();
		}
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession trade, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(trade).setLevel(level));
		return v;
	}

	private static Villager legend(GameTestHelper helper, Legend legend, BlockPos at, VillagerProfession trade) {
		Villager v = villager(helper, at, trade, 5);
		Legends.make(helper.getLevel(), v, legend, "test");
		LegendPowers.forget();
		return v;
	}

	private static void gone(Villager v) {
		LegendRecord.get((ServerLevel) v.level()).forget(v.getUUID());
		v.discard();
		LegendPowers.forget();
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer p = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(p));
		return p;
	}

	// --- 29.17 The Merchant Prince -----------------------------------------------------------------------------------

	/**
	 * Accounts are kept per player: a full account (exactly 10 stacks) takes no more and doesn't block another player's;
	 * a week's interest never takes a full account past 10 stacks; each takes out exactly what they hold; with Legends
	 * switched off the bank takes no deposits.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPrinceAccounts"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPrinceAccounts")
	public void princeAccountsArePerPlayerAndNeverPastTenStacks(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer ann = player(helper);
		ServerPlayer bo = player(helper);
		for (int i = 0; i < 11; i++) {
			ann.getInventory().add(new ItemStack(Items.EMERALD, 64));
		}
		bo.getInventory().add(new ItemStack(Items.EMERALD, 64));
		staged(helper, PRINCE, legend -> {
			Villager prince = legend(helper, legend, new BlockPos(17, 2, 15), VillagerProfession.NONE);
			VillageHallBlockEntity e = entity(helper);
			Component in = PlayerBank.deposit(level, hall, ann, 640);
			helper.assertTrue(key(in).equals("message.aliveworkplace.bank.deposited") && e.playerBank().emeralds(ann.getUUID()) == 640,
				"exactly 10 stacks: " + key(in) + " " + e.playerBank().emeralds(ann.getUUID()));
			helper.assertTrue(ann.getInventory().countItem(Items.EMERALD) == 64, "Ann's emeralds left " + ann.getInventory().countItem(Items.EMERALD));
			Component more = PlayerBank.deposit(level, hall, ann, 1);
			helper.assertTrue(key(more).equals("message.aliveworkplace.bank.full") && ann.getInventory().countItem(Items.EMERALD) == 64,
				"a full account took one more: " + key(more));
			helper.assertTrue(more.getString().contains("10"), "the full message doesn't say 10 stacks: " + more.getString());
			Component bos = PlayerBank.deposit(level, hall, bo, 64);
			helper.assertTrue(key(bos).equals("message.aliveworkplace.bank.deposited") && e.playerBank().emeralds(bo.getUUID()) == 64,
				"Ann's full account blocked Bo's: " + key(bos) + " " + e.playerBank().emeralds(bo.getUUID()));
			helper.assertTrue(e.playerBank().emeralds(ann.getUUID()) == 640, "Bo's deposit changed Ann's account");
			// A week's interest: Bo's 64 earn 5%, Ann's full account stays at 10 stacks.
			e.playerBank().setWeekStart(Chronicle.day(level) - 7);
			PlayerBank.round(level, hall, e);
			int bob = e.playerBank().emeralds(bo.getUUID());
			helper.assertTrue(bob == 67, "Bo's 64 after a week: " + bob + ", not 67 (64 and 5%, whole emeralds)");
			helper.assertTrue(e.playerBank().emeralds(ann.getUUID()) == 640, "interest took a full account past 10 stacks: " + e.playerBank().emeralds(ann.getUUID()));
			// Each takes out what they hold, no more.
			int before = bo.getInventory().countItem(Items.EMERALD);
			PlayerBank.withdraw(level, hall, bo, 1000);
			helper.assertTrue(bo.getInventory().countItem(Items.EMERALD) - before == bob && e.playerBank().emeralds(bo.getUUID()) == 0,
				"Bo took out " + (bo.getInventory().countItem(Items.EMERALD) - before) + " of " + bob);
			helper.assertTrue(e.playerBank().emeralds(ann.getUUID()) == 640, "Bo's withdrawal touched Ann's account");
			// The Legends switch off: no deposits (the account is still there to take out).
			legendsOff(() -> {
				helper.assertTrue(key(PlayerBank.deposit(level, hall, bo, 1)).equals("message.aliveworkplace.bank.closed"), "deposits with Legends off");
				helper.assertTrue(e.playerBank().emeralds(ann.getUUID()) == 640, "Ann's account lost with Legends off");
			});
			gone(prince);
		});
		helper.succeed();
	}

	/** Deposits earn 5% after a full week and not a day sooner; and nothing without the Prince. */
	//$ gametest_ticks_batch AREA '100' '"qaPrinceWeek"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPrinceWeek")
	public void princeInterestComesAfterSevenDaysNotSix(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer ann = player(helper);
		ann.getInventory().add(new ItemStack(Items.EMERALD, 64));
		ann.getInventory().add(new ItemStack(Items.EMERALD, 36));
		staged(helper, PRINCE, legend -> {
			legend(helper, legend, new BlockPos(17, 2, 15), VillagerProfession.NONE);
			VillageHallBlockEntity e = entity(helper);
			PlayerBank.deposit(level, hall, ann, 100);
			long today = Chronicle.day(level);
			e.playerBank().setWeekStart(today - 6);
			PlayerBank.round(level, hall, e);
			helper.assertTrue(e.playerBank().emeralds(ann.getUUID()) == 100, "interest after 6 days: " + e.playerBank().emeralds(ann.getUUID()));
			e.playerBank().setWeekStart(today - 7);
			PlayerBank.round(level, hall, e);
			helper.assertTrue(e.playerBank().emeralds(ann.getUUID()) == 105, "after 7 days: " + e.playerBank().emeralds(ann.getUUID()) + ", not 105");
		});
		helper.succeed();
	}

	/**
	 * A fair every 10 days: none 9 days after the last, one (6 traders, no linked village) on the 10th; none with the
	 * Legends switch off.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPrinceFairDays"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPrinceFairDays")
	public void princeFairComesOnTheTenthDayNotTheNinth(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		VillageHallBlockEntity[] held = {null};
		Leftovers.after(helper, () -> {
			if (held[0] != null) {
				TradeFairs.takeDownBunting(level, held[0]); // part of it stands outside the area
			}
		});
		helper.runAfterDelay(2, () -> {
			held[0] = entity(helper);
			staged(helper, PRINCE, legend -> {
				legend(helper, legend, new BlockPos(17, 2, 15), VillagerProfession.NONE);
				VillageHallBlockEntity e = entity(helper);
				level.setDayTime(40 * DAY + 2000); // mid-morning of day 40, so 10 days back is a real day (a negative fair day means none yet)
				long today = Chronicle.day(level);
				e.setFairDay(today - 9);
				helper.assertTrue(TradeFairs.round(level, hall, e).isEmpty() && e.fairDay() == today - 9, "a fair 9 days after the last");
				e.setFairDay(today - 10);
				legendsOff(() -> helper.assertTrue(TradeFairs.round(level, hall, e).isEmpty() && e.fairDay() == today - 10, "a fair with Legends off"));
				List<WanderingTrader> traders = TradeFairs.round(level, hall, e);
				helper.assertTrue(traders.size() == 6 && e.fairDay() == today, "the 10th day's fair: " + traders.size() + " traders, fair day " + e.fairDay());
				helper.assertTrue(entity(helper).chronicle().stream().anyMatch(x -> key(x.text()).equals("chronicle.aliveworkplace.trade_fair")), "the chronicle doesn't note it");
				TradeFairs.takeDownBunting(level, e);
			});
			helper.succeed();
		});
	}

	// --- 29.18 The Grand Chef ----------------------------------------------------------------------------------------

	private static final List<Item> MEALS = List.of(Items.BREAD, Items.BAKED_POTATO, Items.COOKED_BEEF, Items.PUMPKIN_PIE, Items.COOKED_SALMON,
		Items.COOKED_CHICKEN, Items.COOKIE, Items.COOKED_MUTTON);
	private static final BlockPos STORE = new BlockPos(17, 2, 13);

	private static Container store(GameTestHelper helper, int each) {
		helper.setBlock(new BlockPos(17, 2, 12), Blocks.SMOKER);
		helper.setBlock(STORE, Blocks.CHEST);
		Container c = (Container) helper.getBlockEntity(STORE);
		c.clearContent();
		for (int i = 0; i < MEALS.size(); i++) {
			c.setItem(i, new ItemStack(MEALS.get(i), each));
		}
		return c;
	}

	private static int meals(Container c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (VillageNeeds.isMeal(c.getItem(i))) {
				n += c.getItem(i).getCount();
			}
		}
		return n;
	}

	/** The banquet is the village's: a villager beyond the village's edge neither comes, nor eats, nor gets the mood. */
	//$ gametest_ticks_batch AREA '100' '"qaChefEdge"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChefEdge")
	public void chefBanquetIsOnlyForTheVillage(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		VillageHalls.RADIUS = 6; // setUp puts the old radius back
		staged(helper, CHEF, legend -> {
			Container store = store(helper, 4);
			Villager near = villager(helper, new BlockPos(13, 2, 15), VillagerProfession.FARMER, 2);
			Villager far = villager(helper, new BlockPos(2, 2, 2), VillagerProfession.FARMER, 2);
			legend(helper, legend, new BlockPos(15, 2, 17), ModVillagers.CHEF);
			level.setDayTime(39 * DAY + Banquets.SUPPER);
			Banquets.Feast feast = Banquets.feast(level, hall, entity(helper), legend.powers(BanquetPower.class).get(0));
			helper.assertTrue(feast.came() == 2 && feast.fed() == 2 && feast.meals() == 4, "the village's banquet: " + feast + " (2 in the village, 1 outside)");
			helper.assertTrue(meals(store) == 32 - 4, "meals eaten " + (32 - meals(store)));
			helper.assertTrue(ModAttachments.BANQUET.has(near) && !ModAttachments.BANQUET.has(far), "who came: near " + ModAttachments.BANQUET.has(near)
				+ ", far " + ModAttachments.BANQUET.has(far));
			helper.assertTrue(Banquets.mood(level, near) != null && Banquets.mood(level, near).points() == 20 && Banquets.mood(level, far) == null,
				"the banquet's mood outside the village");
		});
		helper.succeed();
	}

	/** With the Legends switch off, or the Grand Chef gone, no banquet is called and chefs cook at their usual pace. */
	//$ gametest_ticks_batch AREA '100' '"qaChefOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChefOff")
	public void chefPowersStopWithLegendsOffOrTheChefGone(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		staged(helper, CHEF, legend -> {
			Container store = store(helper, 4);
			villager(helper, new BlockPos(13, 2, 15), VillagerProfession.FARMER, 2);
			Villager cook = villager(helper, new BlockPos(18, 2, 12), ModVillagers.CHEF, 3);
			Villager chef = legend(helper, legend, new BlockPos(15, 2, 17), ModVillagers.CHEF);
			helper.assertTrue(Math.abs(LegendPowers.pace(cook) - 1.25f) < 1e-4, "setup: the cook isn't faster with the Grand Chef: " + LegendPowers.pace(cook));
			VillageHallBlockEntity e = entity(helper);
			legendsOff(() -> {
				helper.assertTrue(LegendPowers.pace(cook) == 1f, "a cook faster with Legends off: " + LegendPowers.pace(cook));
				level.setDayTime(39 * DAY + 9000);
				Banquets.round(level, hall, e);
				level.setDayTime(39 * DAY + Banquets.SUPPER);
				Banquets.round(level, hall, e);
				helper.assertTrue(e.banquetDay() < 0 && meals(store) == 32, "a banquet with Legends off: day " + e.banquetDay() + ", eaten " + (32 - meals(store)));
			});
			gone(chef);
			level.setDayTime(40 * DAY + 9000);
			Banquets.round(level, hall, e);
			helper.assertTrue(e.banquetDay() < 0 && LegendPowers.pace(cook) == 1f, "a banquet or a faster cook with the Grand Chef gone");
		});
		helper.succeed();
	}

	/** The Grand Chef's 25% and a Laureate's song add up for a chef who hears it, but only through the shared cap. */
	//$ gametest_ticks_batch AREA '100' '"qaChefAndBard"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaChefAndBard")
	public void chefAndBardPaceGoThroughTheSharedCap(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		staged(helper, List.of(CHEF, BardLaureate.ID), legends -> {
			Villager cook = villager(helper, new BlockPos(18, 2, 12), ModVillagers.CHEF, 3);
			legend(helper, legends.get(0), new BlockPos(15, 2, 17), ModVillagers.CHEF);
			Villager bard = legend(helper, legends.get(1), new BlockPos(20, 2, 12), ModVillagers.BARD);
			float chefOnly = LegendPowers.pace(cook);
			helper.assertTrue(Math.abs(chefOnly - 1.25f) < 1e-4, "the Grand Chef alone: " + chefOnly);
			level.setDayTime(40 * DAY + 3100);
			BardLaureate.sing(level, bard, legends.get(1).powers(BardLaureate.WorkSongsPower.class).get(0), 0);
			bard.moveTo(cook.getX() + 3, cook.getY(), cook.getZ());
			helper.assertTrue(BardLaureate.singing(bard), "setup: the Laureate isn't singing");
			float both = LegendPowers.pace(cook);
			helper.assertTrue(both > 1.25f + 0.01f && both <= LegendPowers.PACE_CAP, "the chef with the song too: " + both + " (cap " + LegendPowers.PACE_CAP + ")");
		});
		helper.succeed();
	}

	// --- 29.19 The Bard Laureate -------------------------------------------------------------------------------------

	/** Workers within 16 blocks work faster while the Laureate sings and hear it; one 17 blocks off neither. */
	//$ gametest_ticks_batch AREA '100' '"qaBardRadius"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBardRadius")
	public void bardSongReachesSixteenBlocksNotSeventeen(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		staged(helper, BardLaureate.ID, legend -> {
			BardLaureate.WorkSongsPower power = legend.powers(BardLaureate.WorkSongsPower.class).get(0);
			Villager bard = legend(helper, legend, new BlockPos(5, 2, 5), ModVillagers.BARD);
			Villager at16 = villager(helper, new BlockPos(21, 2, 5), VillagerProfession.FARMER, 2);
			Villager at17 = villager(helper, new BlockPos(5, 2, 22), VillagerProfession.MASON, 2);
			level.setDayTime(40 * DAY + 3100);
			BardLaureate.sing(level, bard, power, 0);
			BlockPos spot = helper.absolutePos(new BlockPos(5, 2, 5));
			bard.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
			helper.assertTrue(Math.abs(bard.distanceTo(at16) - 16) < 0.01 && Math.abs(bard.distanceTo(at17) - 17) < 0.01,
				"setup: distances " + bard.distanceTo(at16) + ", " + bard.distanceTo(at17));
			helper.assertTrue(BardLaureate.singing(bard), "setup: not singing");
			helper.assertTrue(Math.abs(LegendPowers.pace(at16) - 1.25f) < 1e-3, "a worker 16 blocks off works " + LegendPowers.pace(at16) + "×");
			helper.assertTrue(LegendPowers.pace(at17) == 1f, "a worker 17 blocks off works " + LegendPowers.pace(at17) + "×");
			BardLaureate.hear(level, bard, power);
			helper.assertTrue(ModAttachments.SONG_HEARD.has(at16) && !ModAttachments.SONG_HEARD.has(at17),
				"who heard: 16 " + ModAttachments.SONG_HEARD.has(at16) + ", 17 " + ModAttachments.SONG_HEARD.has(at17));
			LegendPowers.MoodReason mood = BardLaureate.heardMood(level, at16);
			helper.assertTrue(mood != null && mood.points() == 5 && BardLaureate.heardMood(level, at17) == null, "the song's mood");
			helper.assertTrue(mood.reason().getString().equals(Component.translatable("mood.aliveworkplace.reason.work_song").getString())
				&& !mood.reason().getString().startsWith("mood."), "the reason reads " + mood.reason().getString());
		});
		helper.succeed();
	}

	/**
	 * The owner's book writes out all 16 notes in order and names the instrument; with the Legends switch off the
	 * Laureate composes nothing, and the anthem comes once it is on again.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBardBook"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBardBook")
	public void bardBookWritesOutEverySixteenNotes(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		ServerPlayer owner = player(helper);
		staged(helper, BardLaureate.ID, legend -> {
			VillageHallBlockEntity e = entity(helper);
			e.setCustomName(Component.literal("Brightwater"));
			e.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			legend(helper, legend, new BlockPos(12, 2, 12), ModVillagers.BARD);
			legendsOff(() -> {
				Anthems.round(level, hall, e);
				helper.assertTrue(e.anthem().isEmpty() && owner.getInventory().countItem(Items.WRITTEN_BOOK) == 0, "composed with Legends off");
			});
			Anthems.round(level, hall, e);
			Anthems.Anthem anthem = e.anthem().orElseThrow(() -> new AssertionError("no anthem once Legends were on"));
			helper.assertTrue(anthem.sameAs(Anthems.compose("Brightwater")), "not Brightwater's anthem");
			ItemStack book = ItemStack.EMPTY;
			for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
				if (owner.getInventory().getItem(i).is(Items.WRITTEN_BOOK)) {
					book = owner.getInventory().getItem(i);
				}
			}
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content != null && content.title().raw().equals("The Anthem of Brightwater"), "the owner's book: " + content);
			StringBuilder all = new StringBuilder();
			for (Filterable<Component> p : content.pages()) {
				all.append(p.raw().getString()).append('\n');
			}
			String text = all.toString();
			helper.assertTrue(!text.contains("aliveworkplace."), "an untranslated key in the book: " + text);
			String instrument = Component.translatable("anthem.aliveworkplace.instrument." + anthem.instrument()).getString();
			helper.assertTrue(text.contains(instrument), "the book doesn't name the " + instrument + ": " + text);
			int from = text.indexOf("sixteen notes");
			helper.assertTrue(from >= 0, "no notes heading: " + text);
			for (int i = 0; i < 16; i++) {
				String note = Anthems.noteName(anthem.notes()[i]);
				int at = text.indexOf(note, from);
				helper.assertTrue(at >= 0, "note " + (i + 1) + " (" + note + ") isn't written out in order: " + text);
				from = at + note.length();
			}
		});
		helper.succeed();
	}

	/** 200 village names: each always the same 16 notes on one of the six instruments; a one-letter change another tune. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"qaBardNames"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "qaBardNames")
	public void bardAnthemsAreFixedPerNameAcrossManyNames(GameTestHelper helper) {
		RandomSource random = RandomSource.create(1729L);
		Set<String> allowed = Set.of("harp", "flute", "bell", "chime", "guitar", "xylophone");
		helper.assertTrue(allowed.containsAll(Anthems.INSTRUMENTS) && Anthems.INSTRUMENTS.size() == 6, "instruments " + Anthems.INSTRUMENTS);
		Set<String> tunes = new HashSet<>();
		Set<String> used = new HashSet<>();
		String letters = "abcdefghijklmnopqrstuvwxyz";
		for (int n = 0; n < 200; n++) {
			StringBuilder name = new StringBuilder();
			name.append(Character.toUpperCase(letters.charAt(random.nextInt(26))));
			for (int i = 0, len = 4 + random.nextInt(8); i < len; i++) {
				name.append(letters.charAt(random.nextInt(26)));
			}
			Anthems.Anthem a = Anthems.compose(name.toString());
			helper.assertTrue(a.sameAs(Anthems.compose(name.toString())), name + "'s anthem changed");
			helper.assertTrue(a.notes().length == 16 && allowed.contains(a.instrument()), name + ": " + a.notes().length + " notes on " + a.instrument());
			tunes.add(a.instrument() + ":" + a.written());
			used.add(a.instrument());
		}
		helper.assertTrue(tunes.size() >= 195, "only " + tunes.size() + " tunes for 200 names");
		helper.assertTrue(used.size() == 6, "not every instrument is ever used: " + used);
		helper.assertFalse(Anthems.compose("Thornholm").sameAs(Anthems.compose("Thornholn")), "a one-letter change gave the same anthem");
		helper.succeed();
	}

	// --- 29.20 The Beastmaster ---------------------------------------------------------------------------------------

	private static final BlockPos TROUGH = new BlockPos(5, 2, 5);
	private static final BlockPos CHEST = new BlockPos(5, 2, 7);

	private static Villager beastmaster(GameTestHelper helper, Legend legend) {
		helper.setBlock(TROUGH, ModBlocks.FEED_TROUGH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager v = villager(helper, TROUGH.east(), ModVillagers.RANCHER, 2);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(TROUGH), ModVillagers.FEED_TROUGH_POI, ModVillagers.RANCHER);
		v.setVillagerData(v.getVillagerData().setLevel(5));
		Legends.make(helper.getLevel(), v, legend, "test");
		LegendPowers.forget();
		return v;
	}

	private static Villager guard(GameTestHelper helper, BlockPos post) {
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, post.west());
		v.setNoAi(true);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return v;
	}

	private static Wolf wolf(GameTestHelper helper, BlockPos at) {
		Wolf w = helper.spawn(EntityType.WOLF, at);
		w.setNoAi(true);
		return w;
	}

	/** Two bones are not enough for a war dog (and aren't taken); three are; five scutes make no armour and stay in the chest. */
	//$ gametest_ticks_batch AREA '100' '"qaBeastBones"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBeastBones")
	public void beastmasterNeedsThreeBonesAndSixScutes(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		staged(helper, Beastmaster.ID, legend -> {
			level.setDayTime(40 * DAY + 1000);
			beastmaster(helper, legend);
			Villager guard = guard(helper, new BlockPos(24, 2, 6));
			Wolf wolf = wolf(helper, new BlockPos(10, 2, 10));
			Container chest = (Container) helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.BONE, 2));
			chest.setItem(1, new ItemStack(Items.ARMADILLO_SCUTE, 5));
			Beastmaster.round(level, hall);
			helper.assertTrue(!wolf.isTame() && Beastmaster.dog(level, guard) == null, "a war dog from 2 bones");
			helper.assertTrue(chest.countItem(Items.BONE) == 2, "bones taken for no dog: " + chest.countItem(Items.BONE));
			chest.setItem(0, new ItemStack(Items.BONE, 3));
			Beastmaster.round(level, hall);
			helper.assertTrue(Beastmaster.dog(level, guard) == wolf && Beastmaster.isWarDog(wolf), "no war dog from 3 bones");
			helper.assertTrue(chest.countItem(Items.BONE) == 0, "bones left " + chest.countItem(Items.BONE));
			helper.assertTrue(!wolf.hasArmor(), "wolf armour from 5 scutes");
			helper.assertTrue(chest.countItem(Items.ARMADILLO_SCUTE) == 5, "scutes taken for no armour: " + chest.countItem(Items.ARMADILLO_SCUTE));
			helper.assertTrue(wolf.getCustomName() != null && !wolf.getCustomName().getString().contains("aliveworkplace"), "the dog's name " + wolf.getCustomName());
		});
		helper.succeed();
	}

	/** No war dogs and no foals with the Legends switch off, or once the Beastmaster is gone, whatever the chest holds. */
	//$ gametest_ticks_batch AREA '100' '"qaBeastOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBeastOff")
	public void beastmasterPowersStopWithLegendsOffOrGone(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper);
		staged(helper, Beastmaster.ID, legend -> {
			level.setDayTime(40 * DAY + 1000);
			Villager bm = beastmaster(helper, legend);
			Villager guard = guard(helper, new BlockPos(24, 2, 6));
			Wolf wolf = wolf(helper, new BlockPos(10, 2, 10));
			Container chest = (Container) helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.BONE, 9));
			legendsOff(() -> {
				Beastmaster.round(level, hall);
				helper.assertTrue(!wolf.isTame() && Beastmaster.dog(level, guard) == null && chest.countItem(Items.BONE) == 9, "a war dog with Legends off");
			});
			gone(bm);
			Beastmaster.round(level, hall);
			helper.assertTrue(!wolf.isTame() && Beastmaster.dog(level, guard) == null && chest.countItem(Items.BONE) == 9, "a war dog with the Beastmaster gone");
		});
		helper.succeed();
	}

	/**
	 * Foals stat by stat: each of speed, jump and health at least the better parent's even when the best comes from
	 * different parents, and with parents a hair under vanilla's best the foal stops at the best, over several days.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBeastFoals"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBeastFoals")
	public void beastmasterFoalsNeverDropBelowTheBestParentNorPassVanilla(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		staged(helper, Beastmaster.ID, legend -> {
			Villager bm = beastmaster(helper, legend);
			Beastmaster.HorseBreedingPower power = legend.powers(Beastmaster.HorseBreedingPower.class).get(0);
			Container chest = (Container) helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(Items.GOLDEN_CARROT, 64));
			double[][] pairs = {
				{0.12, 0.95, 16, 0.33, 0.41, 29}, // the best speed and health from one parent, the jump from the other
				{Beastmaster.MAX_SPEED - 1e-4, Beastmaster.MAX_JUMP - 1e-4, Beastmaster.MAX_HEALTH - 0.1, 0.2, 0.5, 18},
				{0.2, 0.5, 18, 0.2, 0.5, 18}};
			for (int i = 0; i < pairs.length; i++) {
				double[] p = pairs[i];
				level.setDayTime((50 + i) * DAY + 1000);
				Horse a = horse(helper, new BlockPos(8, 2, 9 + i * 3), p[0], p[1], p[2]);
				Horse b = horse(helper, new BlockPos(10, 2, 9 + i * 3), p[3], p[4], p[5]);
				AbstractHorse foal = Beastmaster.breedHorses(level, bm, power);
				helper.assertTrue(foal != null, "pair " + i + ": no foal");
				double speed = foal.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
				double jump = foal.getAttributeBaseValue(Attributes.JUMP_STRENGTH);
				double health = foal.getAttributeBaseValue(Attributes.MAX_HEALTH);
				helper.assertTrue(speed >= Math.max(p[0], p[3]) && speed <= Beastmaster.MAX_SPEED + 1e-9, "pair " + i + ": speed " + speed);
				helper.assertTrue(jump >= Math.max(p[1], p[4]) && jump <= Beastmaster.MAX_JUMP + 1e-9, "pair " + i + ": jump " + jump);
				helper.assertTrue(health >= Math.max(p[2], p[5]) && health <= Beastmaster.MAX_HEALTH + 1e-9, "pair " + i + ": health " + health);
				a.discard();
				b.discard();
				foal.discard();
			}
		});
		helper.succeed();
	}

	private static Horse horse(GameTestHelper helper, BlockPos at, double speed, double jump, double health) {
		Horse h = helper.spawn(EntityType.HORSE, at);
		h.setNoAi(true);
		h.setAge(0);
		h.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
		h.getAttribute(Attributes.JUMP_STRENGTH).setBaseValue(jump);
		h.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		h.setHealth(h.getMaxHealth());
		return h;
	}

	// --- 29.23 The Founder -------------------------------------------------------------------------------------------

	/** The Founder's mood takes a Master: a level-4 villager with far more XP is passed over, and alone gets no mood. */
	//$ gametest_ticks_batch AREA '200' '"qaFounderMaster"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFounderMaster")
	public void founderMoodTakesOnlyAMaster(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		Villager expert = villager(helper, new BlockPos(8, 2, 8), ModVillagers.BUILDER, 4);
		expert.setVillagerXp(5000);
		helper.setBlock(new BlockPos(9, 2, 8), Blocks.CHEST);
		helper.runAfterDelay(5, () -> staged(helper, Founder.ID, legend -> {
			StrangeMoods.ENABLED = true;
			VillageHallBlockEntity e = entity(helper);
			e.setRank(VillageRanks.Rank.CITY);
			long today = Chronicle.day(level);
			helper.assertTrue(Founder.mood(level, hall(helper), today).isEmpty(), "a level-4 villager was seized by the Founder's mood");
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(expert), "the level-4 villager is in a mood");
			Villager master = villager(helper, new BlockPos(8, 2, 11), ModVillagers.BUILDER, 5);
			master.setVillagerXp(260);
			helper.setBlock(new BlockPos(9, 2, 11), Blocks.CHEST);
			Optional<Villager> seized = Founder.mood(level, hall(helper), today);
			helper.assertTrue(seized.equals(Optional.of(master)), "not the Master: " + seized.map(Villager::getVillagerXp) + " " + Founder.blocked(level, hall(helper)));
			helper.assertTrue(!ModAttachments.STRANGE_MOOD.has(expert), "the level-4 villager was seized too");
			helper.succeed();
		}));
	}

	/**
	 * The wagon goes to the hall's owner: none while they're offline (and the week isn't spent on them), one when they're
	 * back; none with the Legends switch off or the Founder gone, even a week on.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaFounderWagon"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFounderWagon")
	public void founderWagonWaitsForTheOwnerAndTheFounder(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = player(helper);
		helper.runAfterDelay(5, () -> staged(helper, Founder.ID, legend -> {
			BlockPos hall = hall(helper);
			VillageHallBlockEntity e = entity(helper);
			e.setCustomName(Component.literal("Oakbrook"));
			Villager founder = legend(helper, legend, new BlockPos(17, 2, 17), ModVillagers.BUILDER);
			long day = Chronicle.day(level);
			e.setOwner(UUID.randomUUID(), "Away");
			helper.assertTrue(Founder.wagon(level, hall, day).isEmpty() && e.founderWagonDay() == 0, "a wagon for an owner who isn't online");
			e.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			legendsOff(() -> helper.assertTrue(Founder.wagon(level, hall, day).isEmpty() && owner.getInventory().countItem(ModItems.FOUNDERS_WAGON) == 0,
				"a wagon with Legends off"));
			ItemStack wagon = Founder.wagon(level, hall, day);
			helper.assertTrue(wagon.is(ModItems.FOUNDERS_WAGON) && owner.getInventory().countItem(ModItems.FOUNDERS_WAGON) == 1,
				"no wagon once the owner was online: " + wagon);
			helper.assertTrue(e.chronicle().stream().anyMatch(x -> key(x.text()).equals("chronicle.aliveworkplace.founder.wagon")), "the chronicle doesn't note the wagon");
			gone(founder);
			helper.assertTrue(Founder.wagon(level, hall, day + 7).isEmpty() && owner.getInventory().countItem(ModItems.FOUNDERS_WAGON) == 1,
				"a wagon a week on with the Founder gone");
			helper.succeed();
		}));
	}

	/** A Charter from a bare chronicle still has its title, its cover and the Founder's signature, with no key showing. */
	//$ gametest_ticks_batch AREA '100' '"qaFounderBareCharter"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaFounderBareCharter")
	public void founderCharterFromABareChronicle(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(2, () -> staged(helper, Founder.ID, legend -> {
			BlockPos hall = hall(helper);
			entity(helper).setCustomName(Component.literal("Elmstead"));
			Villager founder = villager(helper, new BlockPos(8, 2, 8), ModVillagers.BUILDER, 5);
			founder.setCustomName(Component.literal("Wilma"));
			helper.assertTrue(Founder.story(level, hall).isEmpty(), "setup: the chronicle isn't bare: " + Founder.story(level, hall).size());
			ItemStack book = Founder.charter(level, hall, founder, legend, 12);
			WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(book.is(Items.WRITTEN_BOOK) && content != null, "no Charter: " + book);
			helper.assertTrue(content.title().raw().equals("The Charter of Elmstead") && content.author().equals("Wilma"), "title " + content.title().raw() + " by " + content.author());
			List<String> pages = content.pages().stream().map(p -> p.raw().getString()).toList();
			helper.assertTrue(!pages.isEmpty() && pages.get(0).startsWith("The Charter of Elmstead"), "cover " + pages);
			helper.assertTrue(pages.get(pages.size() - 1).contains("Signed, Wilma,\nFounder of Elmstead"), "signature " + pages);
			helper.assertTrue(pages.stream().noneMatch(p -> p.contains("aliveworkplace.")), "a key in the Charter: " + pages);
			helper.succeed();
		}));
	}
}

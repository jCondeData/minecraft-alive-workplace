package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.PlayerBank;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.legend.BankPower;
import io.github.jcondedata.aliveworkplace.legend.CaravanPayPower;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendNeeds;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * 29.17, the Merchant Prince: the real file (a Legendary who likes wine, a castaway by a shipwreck once the treasury
 * has taken in 500 emeralds and the village sends caravans on 3 routes); the bank: 2% a day on the treasury and twice
 * the cap; players' deposits on the hall's bank page, at most 10 stacks, 5% a week, through a reload, out any time; the
 * caravan pay; and every sentence a player reads.
 */
public class MerchantPrinceGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);
	private static final ResourceLocation ID = AliveWorkplace.id("merchant_prince");

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 6;
		LegendRecord stale = LegendRecord.get(level);
		stale.entries().stream().filter(e -> e.id().equals(ID)).map(LegendRecord.Entry::villager).toList().forEach(stale::forget);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			LegendRecord record = LegendRecord.get(level);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(32), v -> true)) {
				record.forget(v.getUUID());
				v.discard();
			}
			Leftovers.players(helper);
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
	}

	/** Only the real Merchant Prince loaded, moods and needs off while {@code body} runs. */
	private static void staged(GameTestHelper helper, java.util.function.Consumer<Legend> body) {
		ServerLevel level = helper.getLevel();
		boolean moods = Moods.ENABLED;
		boolean needs = LegendNeeds.ENABLED;
		try {
			Moods.ENABLED = false;
			LegendNeeds.ENABLED = false;
			Legends.reload(level.getServer().getResourceManager());
			Legend prince = Legends.get(ID).orElseThrow(() -> new AssertionError("merchant_prince.json didn't load"));
			Map<ResourceLocation, Legend> map = new LinkedHashMap<>();
			map.put(ID, prince);
			Legends.setForTest(map);
			LegendPowers.forget();
			body.accept(prince);
		} finally {
			Moods.ENABLED = moods;
			LegendNeeds.ENABLED = needs;
		}
	}

	private static Villager prince(GameTestHelper helper, Legend legend, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		Legends.make(helper.getLevel(), v, legend, "test");
		LegendPowers.forget();
		return v;
	}

	private static void gone(Villager prince) {
		LegendRecord.get((ServerLevel) prince.level()).forget(prince.getUUID());
		prince.discard();
		LegendPowers.forget();
	}

	private static String key(Component c) {
		return c.getContents() instanceof TranslatableContents t ? t.getKey() : "";
	}

	/** The file: Legendary, likes wine, a castaway by a shipwreck once 500 emeralds and 3 routes; the bank and the caravan pay. */
	//$ gametest_ticks_batch AREA '100' '"princeFile"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "princeFile")
	public void theFileAndItsConditions(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		Caravans.Data data = Caravans.Data.get(level);
		List<BlockPos> others = List.of(hall.offset(200, 0, 0), hall.offset(0, 0, 200), hall.offset(-200, 0, 0));
		Leftovers.after(helper, () -> others.forEach(o -> {
			if (data.routesFrom(hall).contains(o)) {
				data.toggleRoute(hall, o);
			}
		}));
		staged(helper, legend -> {
			helper.assertTrue(legend.rarity() == Rarity.LEGENDARY, "not Legendary");
			helper.assertTrue(legend.job().equals(AliveWorkplace.id("legend")), "trade " + legend.job());
			helper.assertTrue(legend.luxury().equals(Optional.of("wine")), "likes " + legend.luxury());
			helper.assertTrue(legend.ways("found").stream().anyMatch(w -> "shipwreck".equals(w.get("site").getAsString())), "not found at a shipwreck");
			helper.assertTrue(legend.powers(BankPower.class).equals(List.of(new BankPower(2, 2))), "bank " + legend.powers(BankPower.class));
			helper.assertTrue(legend.powers(CaravanPayPower.class).equals(List.of(new CaravanPayPower(1))), "pay " + legend.powers(CaravanPayPower.class));
			helper.assertTrue(legend.powers(io.github.jcondedata.aliveworkplace.legend.TradeFairPower.class).size() == 1, "no trade fair");
			helper.assertFalse(legend.conditions().stream().allMatch(c -> c.met(level, hall)), "met by a new village");
			entity.addTreasuryTotal(499 * 100L);
			others.forEach(o -> data.toggleRoute(hall, o));
			helper.assertFalse(legend.conditions().stream().allMatch(c -> c.met(level, hall)), "met at 499 emeralds");
			entity.addTreasuryTotal(100);
			for (Condition c : legend.conditions()) {
				helper.assertTrue(c.met(level, hall), "not met at 500 emeralds and 3 routes: " + c.type());
			}
			data.toggleRoute(hall, others.get(0));
			helper.assertFalse(legend.conditions().stream().allMatch(c -> c.met(level, hall)), "met with 2 routes");
			Language en = Language.getInstance();
			for (String k : List.of(key(legend.powers(BankPower.class).get(0).describe()), key(legend.powers(CaravanPayPower.class).get(0).describe()),
				key(legend.powers(io.github.jcondedata.aliveworkplace.legend.TradeFairPower.class).get(0).describe()), "entity.aliveworkplace.fair_stall",
				"chronicle.aliveworkplace.trade_fair", "message.aliveworkplace.trade_fair", "legend.aliveworkplace.power.keeps_to",
				"legend.aliveworkplace.merchant_prince.title", "legend.aliveworkplace.merchant_prince.lore", "legend.aliveworkplace.merchant_prince.name.1")) {
				helper.assertTrue(en.has(k), "no text for " + k);
			}
			helper.assertTrue(legend.powers(BankPower.class).get(0).describe().getString().contains("2% a day"), legend.powers(BankPower.class).get(0).describe().getString());
		});
		helper.succeed();
	}

	/** The bank: a day's 2% on what the treasury holds, the cap doubled; gone with the Prince (and with Legends off). */
	//$ gametest_ticks_batch AREA '100' '"princeBank"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "princeBank")
	public void aDaysInterestAndTheDoubledCap(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		long today = Chronicle.day(level);
		int base = Treasury.cap(entity.rank());
		staged(helper, legend -> {
			helper.assertTrue(Treasury.cap(entity) == base, "a bigger cap without the Prince");
			Villager prince = prince(helper, legend, new BlockPos(5, 2, 3));
			helper.assertTrue(BankPower.of(level, hall).isPresent(), "no bank with the Prince settled here");
			helper.assertTrue(Treasury.cap(entity) == 2 * base, "cap " + Treasury.cap(entity) + ", not twice " + base);
			entity.setTreasury(5000);
			entity.setLastTaxDay(today - 1);
			long total = entity.treasuryTotal();
			Treasury.round(level, hall, entity, 0);
			helper.assertTrue(entity.treasury() == 5100, "50 emeralds after a day: " + entity.treasury() + " hundredths, not 5100");
			helper.assertTrue(entity.treasuryTotal() == total + 100, "the interest isn't counted as taken in");
			Treasury.round(level, hall, entity, 0);
			helper.assertTrue(entity.treasury() == 5100, "interest twice in a day");
			entity.setTreasury(2 * base * 100 - 50);
			entity.setLastTaxDay(today - 1);
			Treasury.round(level, hall, entity, 10);
			helper.assertTrue(entity.treasury() == 2 * base * 100, "fills to the doubled cap: " + entity.treasury());
			boolean on = Legends.ENABLED;
			try {
				Legends.ENABLED = false;
				LegendPowers.forget();
				helper.assertTrue(Treasury.cap(entity) == base && BankPower.of(level, hall).isEmpty(), "the bank works with Legends off");
			} finally {
				Legends.ENABLED = on;
				LegendPowers.forget();
			}
			gone(prince);
			helper.assertTrue(Treasury.cap(entity) == base, "the cap stays doubled after the Prince is gone");
			entity.setTreasury(5000);
			entity.setLastTaxDay(today - 1);
			Treasury.round(level, hall, entity, 0);
			helper.assertTrue(entity.treasury() == 5000, "interest without the Prince: " + entity.treasury());
			entity.setTreasury(2 * base * 100);
			entity.setLastTaxDay(today - 1);
			Treasury.round(level, hall, entity, 10);
			helper.assertTrue(entity.treasury() == 2 * base * 100, "a full treasury lost emeralds when the cap went back down");
		});
		helper.succeed();
	}

	/** Deposits on the bank page: at most 10 stacks, 5% a week, kept through a reload, out any time (even without the Prince). */
	//$ gametest_ticks_batch AREA '100' '"princeDeposits"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "princeDeposits")
	public void depositsWithdrawalsAndInterest(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		for (int i = 0; i < 12; i++) {
			player.getInventory().add(new ItemStack(Items.EMERALD, 64));
		}
		staged(helper, legend -> {
			helper.assertTrue(key(PlayerBank.deposit(level, hall, player, 10)).equals("message.aliveworkplace.bank.closed"), "deposits without a bank");
			Villager prince = prince(helper, legend, new BlockPos(5, 2, 3));
			ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
			int tab = PlayerBank.BUTTON;
			helper.assertTrue(menu.icon(tab).is(Items.GOLD_INGOT), "no bank button on the hall");
			menu.press(tab, player);
			menu.press(PlayerBank.DEPOSIT + 2, player); // put in 64
			VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
			helper.assertTrue(entity.playerBank().emeralds(player.getUUID()) == 64, "the page's button put in " + entity.playerBank().emeralds(player.getUUID()));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 12 * 64 - 64, "emeralds left " + player.getInventory().countItem(Items.EMERALD));
			Component full = PlayerBank.deposit(level, hall, player, 700);
			helper.assertTrue(entity.playerBank().emeralds(player.getUUID()) == PlayerBank.MAX_EMERALDS, "more than 10 stacks: " + entity.playerBank().emeralds(player.getUUID()));
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 12 * 64 - 640, "took more than fit: " + player.getInventory().countItem(Items.EMERALD));
			helper.assertTrue(key(full).equals("message.aliveworkplace.bank.deposited"), key(full));
			helper.assertTrue(key(PlayerBank.deposit(level, hall, player, 1)).equals("message.aliveworkplace.bank.full"), "a full account takes more");
			PlayerBank.withdraw(level, hall, player, 540);
			helper.assertTrue(entity.playerBank().emeralds(player.getUUID()) == 100, "after taking out 540: " + entity.playerBank().emeralds(player.getUUID()));
			entity.playerBank().setWeekStart(Chronicle.day(level) - 7);
			PlayerBank.round(level, hall, entity);
			helper.assertTrue(entity.playerBank().emeralds(player.getUUID()) == 105, "5% a week: " + entity.playerBank().emeralds(player.getUUID())
				+ " (bank open " + PlayerBank.open(level, hall) + ", week " + entity.playerBank().weekStart() + ", today " + Chronicle.day(level) + ")");
			PlayerBank.round(level, hall, entity);
			helper.assertTrue(entity.playerBank().emeralds(player.getUUID()) == 105, "interest twice in a week");

			CompoundTag saved = entity.saveWithFullMetadata(level.registryAccess());
			helper.setBlock(HALL, Blocks.AIR);
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity reloaded = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
			reloaded.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue(reloaded.playerBank().emeralds(player.getUUID()) == 105, "lost in a reload: " + reloaded.playerBank().emeralds(player.getUUID()));
			helper.assertTrue(reloaded.playerBank().emeralds(java.util.UUID.randomUUID()) == 0, "someone else has an account");

			gone(prince);
			reloaded.playerBank().setWeekStart(Chronicle.day(level) - 7);
			PlayerBank.round(level, hall, reloaded);
			helper.assertTrue(reloaded.playerBank().emeralds(player.getUUID()) == 105, "interest without the Prince");
			helper.assertTrue(key(PlayerBank.deposit(level, hall, player, 1)).equals("message.aliveworkplace.bank.closed"), "deposits after the Prince left");
			int before = player.getInventory().countItem(Items.EMERALD);
			ChoiceMenu again = VillageHallScreen.forTest(player, hall);
			again.press(tab, player);
			again.press(PlayerBank.WITHDRAW + 2, player); // take out everything
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == before + 105, "took out " + (player.getInventory().countItem(Items.EMERALD) - before));
			helper.assertTrue(reloaded.playerBank().emeralds(player.getUUID()) == 0, "still holds " + reloaded.playerBank().emeralds(player.getUUID()));
			helper.assertTrue(key(PlayerBank.withdraw(level, hall, player, 1)).equals("message.aliveworkplace.bank.empty"), "an empty account pays");
		});
		helper.succeed();
	}

	/** By day the Prince keeps to the hall (and the square, when there is one): a straying Prince is sent back; at night he isn't. */
	//$ gametest_ticks_batch AREA '100' '"princeKeepsTo"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "princeKeepsTo")
	public void theDayAtTheHall(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		long time = level.getDayTime();
		Leftovers.after(helper, () -> level.setDayTime(time));
		staged(helper, legend -> {
			helper.assertTrue(legend.powers(io.github.jcondedata.aliveworkplace.legend.KeepsToPower.class).size() == 1, "no keeps_to");
			Villager prince = prince(helper, legend, new BlockPos(5, 2, 3));
			var data = io.github.jcondedata.aliveworkplace.registry.ModAttachments.LEGEND.get(prince);
			prince.teleportTo(hall.getX() + 22.5, hall.getY(), hall.getZ() + 22.5);
			level.setDayTime(time / 24000L * 24000L + 2000L);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.legend.KeepsToPower.place(level, prince, data).equals(Optional.of(hall)), "by day: not the hall");
			prince.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
			io.github.jcondedata.aliveworkplace.legend.KeepsToPower.tick(prince);
			helper.assertTrue(prince.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
				.map(w -> w.getTarget().currentBlockPosition().equals(hall)).orElse(false), "a straying Prince isn't sent back to the hall");
			level.setDayTime(time / 24000L * 24000L + 14000L);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.legend.KeepsToPower.place(level, prince, data).isEmpty(), "kept to the hall at night");
		});
		helper.succeed();
	}

	/** A fair: 6 traders plus a stall for each village this one trades with (named after it, selling its spare goods), 10% off. */
	//$ gametest_ticks_batch AREA '100' '"princeFair"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "princeFair")
	public void aFairWithSixTradersAndAStallPerLinkedVillage(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL);
		helper.setBlock(new BlockPos(18, 2, 18), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(16, 2, 18), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(16, 2, 20), Blocks.CHEST);
		((Container) helper.getBlockEntity(new BlockPos(16, 2, 20))).setItem(0, new ItemStack(Items.OAK_LOG, 64));
		helper.setBlock(new BlockPos(25, 2, 3), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(3, 2, 25), ModBlocks.VILLAGE_HALL);
		BlockPos b = helper.absolutePos(new BlockPos(18, 2, 18));
		BlockPos c = helper.absolutePos(new BlockPos(25, 2, 3));
		BlockPos d = helper.absolutePos(new BlockPos(3, 2, 25));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			List.of(a, b, c, d).forEach(data::remove);
			io.github.jcondedata.aliveworkplace.hall.TradeFairs.takeDownBunting(level, entity); // part of it stands outside the area
			level.getEntitiesOfClass(net.minecraft.world.entity.npc.WanderingTrader.class, helper.getBounds().inflate(16), t -> true)
				.forEach(net.minecraft.world.entity.Entity::discard);
		});
		helper.runAfterDelay(2, () -> staged(helper, legend -> {
			io.github.jcondedata.aliveworkplace.legend.TradeFairPower power = legend.powers(io.github.jcondedata.aliveworkplace.legend.TradeFairPower.class).get(0);
			helper.assertTrue(power.equals(new io.github.jcondedata.aliveworkplace.legend.TradeFairPower(10, 6, 10)), "fair " + power);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.TradeFairs.round(level, a, entity).isEmpty(), "a fair without the Prince");
			prince(helper, legend, new BlockPos(5, 2, 3));
			data.setWants(a, Component.literal("Ashford"), List.of());
			data.setWants(b, Component.literal("Bramble"), List.of());
			data.setWants(c, Component.literal("Coldwater"), List.of());
			data.setWants(d, Component.literal("Dunmore"), List.of());
			helper.assertTrue(data.toggleRoute(a, b) && data.toggleRoute(c, a), "no routes");
			long today = Chronicle.day(level);
			entity.setFairDay(today);
			helper.assertTrue(io.github.jcondedata.aliveworkplace.hall.TradeFairs.round(level, a, entity).isEmpty(), "a fair before 10 days");
			var traders = io.github.jcondedata.aliveworkplace.hall.TradeFairs.hold(level, a, power);
			helper.assertTrue(traders.size() == 6 + 2, "traders at the fair: " + traders.size() + ", not 6 and a stall each for Bramble and Coldwater");
			var stall = traders.stream().filter(t -> t.getCustomName() != null && t.getCustomName().getString().equals("Stall of Bramble")).findFirst();
			helper.assertTrue(stall.isPresent(), "no stall named after Bramble");
			helper.assertTrue(stall.get().getOffers().stream().anyMatch(o -> o.getResult().is(Items.OAK_LOG) && o.getResult().getCount() == 48),
				"Bramble's stall doesn't sell its 48 spare logs");
			helper.assertTrue(traders.stream().noneMatch(t -> t.getCustomName() != null && t.getCustomName().getString().contains("Dunmore")), "a stall for a village we don't trade with");
			helper.assertTrue(traders.stream().allMatch(t -> t.getOffers().stream().allMatch(o -> o.getSpecialPriceDiff() < 0)), "a trade at the fair isn't cheaper");
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> key(e.text()).equals("chronicle.aliveworkplace.trade_fair")), "not in the chronicle");
			Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 7));
			farmer.setNoAi(true);
			farmer.setVillagerData(farmer.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.FARMER).setLevel(1));
			helper.assertTrue(!farmer.getOffers().isEmpty(), "the farmer has no trades");
			farmer.getOffers().forEach(o -> o.setSpecialPriceDiff(0));
			io.github.jcondedata.aliveworkplace.hall.TradeFairs.discount(farmer);
			helper.assertTrue(farmer.getOffers().stream().allMatch(o -> o.getSpecialPriceDiff() < 0), "the village's own trades aren't cheaper on fair day");
			entity.setFairDay(today - 1);
			farmer.getOffers().forEach(o -> o.setSpecialPriceDiff(0));
			io.github.jcondedata.aliveworkplace.hall.TradeFairs.discount(farmer);
			helper.assertTrue(farmer.getOffers().stream().allMatch(o -> o.getSpecialPriceDiff() == 0), "cheaper the day after the fair");
			helper.succeed();
		}));
	}

	/**
	 * The fair's bunting: red and yellow banners round the square, put up on free ground only (a player's block on the
	 * ring stays), saved in the hall through a reload, up all the fair's day and taken down the day after, without drops
	 * and leaving a player's block put where a banner was.
	 */
	//$ gametest_ticks_batch AREA '100' '"princeBunting"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "princeBunting")
	public void theFairsBuntingGoesUpAndComesDown(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		BlockPos center = new BlockPos(15, 2, 15);
		helper.setBlock(HALL, Blocks.AIR);
		helper.setBlock(center, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(center);
		int r = io.github.jcondedata.aliveworkplace.hall.TradeFairs.BUNTING_RING;
		BlockPos players = center.offset(-r, 0, -r); // the ring's first spot: a player's planks stand there
		helper.setBlock(players, Blocks.OAK_PLANKS);
		Leftovers.after(helper, () -> level.getEntitiesOfClass(net.minecraft.world.entity.npc.WanderingTrader.class, helper.getBounds().inflate(16), t -> true)
			.forEach(net.minecraft.world.entity.Entity::discard));
		helper.runAfterDelay(2, () -> staged(helper, legend -> {
			io.github.jcondedata.aliveworkplace.legend.TradeFairPower power = legend.powers(io.github.jcondedata.aliveworkplace.legend.TradeFairPower.class).get(0);
			prince(helper, legend, center.offset(2, 0, 0));
			VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(center);
			helper.assertTrue(entity.fairBunting().isEmpty(), "bunting before any fair");
			var traders = io.github.jcondedata.aliveworkplace.hall.TradeFairs.hold(level, hall, power);
			helper.assertTrue(traders.size() == 6, "traders " + traders.size());
			List<BlockPos> bunting = List.copyOf(entity.fairBunting());
			int expected = 8 * r / io.github.jcondedata.aliveworkplace.hall.TradeFairs.BUNTING_EVERY;
			helper.assertTrue(bunting.size() == expected, "banners up: " + bunting.size() + ", not " + expected);
			long red = bunting.stream().filter(p -> level.getBlockState(p).is(Blocks.RED_BANNER)).count();
			long yellow = bunting.stream().filter(p -> level.getBlockState(p).is(Blocks.YELLOW_BANNER)).count();
			helper.assertTrue(red + yellow == bunting.size() && red > 0 && yellow > 0, "not red and yellow banners: " + red + " red, " + yellow + " yellow of " + bunting.size());
			helper.assertTrue(bunting.stream().allMatch(p -> Math.max(Math.abs(p.getX() - hall.getX()), Math.abs(p.getZ() - hall.getZ())) == r),
				"a banner off the ring round the square");
			helper.assertBlockPresent(Blocks.OAK_PLANKS, players);
			helper.assertTrue(bunting.contains(helper.absolutePos(players.above())), "the banner at the player's planks isn't on top of them");

			CompoundTag saved = entity.saveWithFullMetadata(level.registryAccess());
			helper.setBlock(center, Blocks.AIR);
			helper.setBlock(center, ModBlocks.VILLAGE_HALL);
			VillageHallBlockEntity reloaded = (VillageHallBlockEntity) helper.getBlockEntity(center);
			reloaded.loadWithComponents(saved, level.registryAccess());
			helper.assertTrue(reloaded.fairBunting().equals(bunting), "the bunting lost in a reload: " + reloaded.fairBunting().size());

			long today = Chronicle.day(level);
			io.github.jcondedata.aliveworkplace.hall.TradeFairs.round(level, hall, reloaded);
			helper.assertTrue(bunting.stream().allMatch(p -> level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.BannerBlock),
				"bunting down on the fair's own day");
			BlockPos replaced = bunting.get(1); // a player took a banner and put a lantern there
			level.setBlockAndUpdate(replaced, Blocks.LANTERN.defaultBlockState());
			reloaded.setFairDay(today - 1); // the fair's day is over
			io.github.jcondedata.aliveworkplace.hall.TradeFairs.round(level, hall, reloaded);
			helper.assertTrue(reloaded.fairBunting().isEmpty(), "bunting still listed after the day: " + reloaded.fairBunting().size());
			helper.assertTrue(bunting.stream().filter(p -> !p.equals(replaced)).allMatch(p -> level.getBlockState(p).isAir()), "a banner left up after the fair");
			helper.assertTrue(level.getBlockState(replaced).is(Blocks.LANTERN), "the player's lantern was taken down with the bunting");
			helper.assertBlockPresent(Blocks.OAK_PLANKS, players);
			helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(4),
				e -> e.getItem().is(net.minecraft.tags.ItemTags.BANNERS)).isEmpty(), "the bunting dropped banners");
			helper.succeed();
		}));
	}

	/** The caravan pay: a stack brought to a village waiting for it earns the Prince's treasury an emerald; nothing without him. */
	//$ gametest_ticks_batch AREA '200' '"princeCaravanPay"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "princeCaravanPay")
	public void caravansEarnTheTreasury(GameTestHelper helper) {
		setUp(helper);
		long travel = Caravans.MIN_TRAVEL;
		Caravans.MIN_TRAVEL = 0;
		Leftovers.after(helper, () -> Caravans.MIN_TRAVEL = travel);
		// The halls' own rounds (every CHECK_EVERY ticks, at a time set by the game time and the hall's position) would
		// replace Bramble's wants with its census's and unload the caravan themselves, so the treasury would be paid
		// before `before` is read: hold them off while the test drives the rounds itself.
		int every = VillageNeeds.CHECK_EVERY;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		Leftovers.after(helper, () -> VillageNeeds.CHECK_EVERY = every);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(5, 2, 3), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(5, 2, 5), Blocks.CHEST);
		Container ours = helper.getBlockEntity(new BlockPos(5, 2, 5));
		helper.setBlock(new BlockPos(18, 2, 18), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(16, 2, 18), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(16, 2, 20), Blocks.CHEST);
		Container theirs = helper.getBlockEntity(new BlockPos(16, 2, 20));
		BlockPos a = helper.absolutePos(HALL);
		BlockPos b = helper.absolutePos(new BlockPos(18, 2, 18));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getBlockEntity(HALL);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			data.remove(a);
			data.remove(b);
		});
		staged(helper, legend -> {
			Villager prince = prince(helper, legend, new BlockPos(3, 2, 6));
			helper.runAfterDelay(2, () -> {
				ours.setItem(0, new ItemStack(Items.OAK_LOG, 64));
				ours.setItem(1, new ItemStack(Items.BREAD, 64));
				data.setWants(a, Component.literal("Ashford"), List.of());
				data.setWants(b, Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32), new Caravans.Want(Items.BREAD, 16)));
				helper.assertTrue(data.toggleRoute(a, b), "no route");
				ServerPlayer player = helper.makeMockServerPlayerInLevel();
				Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
				ChoiceMenu menu = VillageHallScreen.forTest(player, a);
				VillageHallScreen.renderRoutes(menu, level, a);
				boolean shown = false;
				for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
					var lore = menu.icon(s).get(net.minecraft.core.component.DataComponents.LORE);
					shown |= lore != null && lore.lines().stream().anyMatch(l -> l.getString().equals("Pays our treasury 1 emerald a stack for what it's waiting for"));
				}
				helper.assertTrue(shown, "the trade routes page doesn't say what Bramble pays");
				Caravans.round(level, a, null);
			});
			helper.runAfterDelay(40, () -> {
				int before = entity.treasury();
				Caravans.round(level, b, null);
				helper.assertTrue(theirs.countItem(Items.OAK_LOG) == 32 && theirs.countItem(Items.BREAD) == 16, "didn't arrive");
				helper.assertTrue(entity.treasury() - before == 200, "two stacks earned " + (entity.treasury() - before) + " hundredths, not 200");
				gone(prince);
				ours.setItem(0, new ItemStack(Items.OAK_LOG, 64));
				data.setWants(b, Component.literal("Bramble"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
				data.sent(a, -1);
				Caravans.round(level, a, null);
			});
			helper.runAfterDelay(80, () -> {
				int before = entity.treasury();
				Caravans.round(level, b, null);
				helper.assertTrue(theirs.countItem(Items.OAK_LOG) == 64, "the second caravan didn't arrive: " + theirs.countItem(Items.OAK_LOG));
				helper.assertTrue(entity.treasury() == before, "paid without the Prince");
				helper.succeed();
			});
		});
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA: config settings at the ends of their ranges ({@code WorkplaceConfig.RANGES}), checked by what the game does, as
 * {@code docs/wiki/config-switches.md} promises it: {@code villageGrowthCap} 0 ("villages don't grow") and its
 * neighbours, {@code realmMaxMembers} at its least (2), {@code treasuryPerWorker} at 0 and at 500, and
 * {@code maxWorkersPerVillage} 0 ("no cap") in a village that already has workers. Every static is put back after.
 */
public class QaConfigExtremesGameTests {
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos CHEST = new BlockPos(17, 2, 19);

	/** A hall's village: two grown villagers, three free beds and {@code bread} in a storehouse chest. */
	private static Container growingVillage(GameTestHelper helper, int bread) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		int cap = VillageGrowth.CAP;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			VillageGrowth.CAP = cap;
			VillageNeeds.forget();
		});
		helper.setBlock(new BlockPos(17, 2, 17), ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.BREAD, bread));
		helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 8));
		for (int x : new int[] {3, 5, 7}) {
			helper.setBlock(new BlockPos(x, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
			helper.setBlock(new BlockPos(x, 2, 16), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return chest;
	}

	private static List<Villager> babies(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds(), v -> v.isAlive() && v.isBaby());
	}

	/** The hall screen's growth line: the People (emerald) icon's last line, as the player reads it. */
	private static String growthLine(GameTestHelper helper, ServerPlayer player) {
		VillageNeeds.forget();
		ItemStack people = VillageHallScreen.forTest(player, helper.absolutePos(HALL)).icon(1);
		helper.assertTrue(people.is(Items.EMERALD), "no People icon on the hall screen: " + people);
		List<String> lore = RealmGameTests.lore(people);
		helper.assertTrue(!lore.isEmpty(), "the People icon has no lines");
		return lore.get(lore.size() - 1);
	}

	/**
	 * villageGrowthCap 0, "villages don't grow": a village with free beds, 24 meals and a happy mood has no baby (the
	 * store keeps its food), not even as a City (the rank's +10 a step doesn't bring growth back); the hall's growth line
	 * doesn't promise a baby or ask for something that would bring one.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaExtGrowthZero"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaExtGrowthZero")
	public void growthCapZeroMeansNoBabies(GameTestHelper helper) {
		Container chest = growingVillage(helper, 24);
		VillageGrowth.CAP = 0;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		VillageNeeds.Needs happy = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, 0) == VillageGrowth.Blocker.FULL, "cap 0: " + VillageGrowth.blocker(level, hall, happy, 0));
			helper.assertTrue(VillageGrowth.grow(level, hall, happy, 0) == null, "cap 0: a baby was born");
			for (VillageRanks.Rank rank : VillageRanks.Rank.values()) {
				entity.setRank(rank);
				helper.assertTrue(VillageRanks.growthCap(rank) == 0, "cap 0 at " + rank + ": " + VillageRanks.growthCap(rank));
				helper.assertTrue(VillageGrowth.grow(level, hall, happy, 0) == null, "cap 0: a baby was born at " + rank);
			}
			entity.setRank(VillageRanks.Rank.HAMLET);
			helper.assertTrue(babies(helper).isEmpty() && entity.births() == 0, "babies: " + babies(helper).size() + ", births " + entity.births());
			helper.assertTrue(chest.countItem(Items.BREAD) == 24, "the store lost food with no baby: " + chest.countItem(Items.BREAD));
			String line = growthLine(helper, player);
			helper.assertTrue(!line.equals("A baby is on the way") && !line.startsWith("To grow:"), "cap 0, the hall says: " + line);
			// the neighbour, 1: a village of two is already over it
			VillageGrowth.CAP = 1;
			helper.assertTrue(VillageGrowth.blocker(level, hall, happy, 0) == VillageGrowth.Blocker.FULL, "cap 1, two villagers: " + VillageGrowth.blocker(level, hall, happy, 0));
			helper.assertTrue(VillageGrowth.grow(level, hall, happy, 0) == null && babies(helper).isEmpty(), "cap 1: a baby in a village of two");
			helper.succeed();
		});
	}

	/**
	 * The cap's other side: at 3, a village of two has exactly one baby (eating 8 meals), and then it's full, as the hall
	 * says: "The village is full (3 villagers)".
	 */
	//$ gametest_ticks_batch BIG '100' '"qaExtGrowthThree"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaExtGrowthThree")
	public void growthCapThreeAllowsExactlyOneBaby(GameTestHelper helper) {
		Container chest = growingVillage(helper, 40);
		VillageGrowth.CAP = 3;
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.HAMLET);
			VillageNeeds.Needs two = new VillageNeeds.Needs(2, 2, 2, 2, 2, 0, 0, 1f);
			helper.assertTrue(VillageGrowth.blocker(level, hall, two, 0) == VillageGrowth.Blocker.NONE, "cap 3, two villagers: " + VillageGrowth.blocker(level, hall, two, 0));
			Villager baby = VillageGrowth.grow(level, hall, two, 0);
			helper.assertTrue(baby != null && baby.isBaby(), "cap 3: no baby in a village of two");
			helper.assertTrue(chest.countItem(Items.BREAD) == 32, "the family ate: " + (40 - chest.countItem(Items.BREAD)));
			VillageNeeds.forget();
			int counted = VillageNeeds.count(level, hall).villagers();
			helper.assertTrue(counted == 3, "the village counts " + counted + " villagers with the baby");
			// a day later, still food and beds: full
			VillageNeeds.Needs three = new VillageNeeds.Needs(2, 2, 3, 3, 2, 0, 0, 1f);
			helper.assertTrue(VillageGrowth.blocker(level, hall, three, 0) == VillageGrowth.Blocker.FULL, "cap 3, three villagers: " + VillageGrowth.blocker(level, hall, three, 0));
			helper.assertTrue(VillageGrowth.grow(level, hall, three, 0) == null && babies(helper).size() == 1, "a second baby past the cap: " + babies(helper).size());
			helper.assertTrue(chest.countItem(Items.BREAD) == 32, "food eaten with no baby");
			String line = growthLine(helper, player);
			helper.assertTrue(line.equals("The village is full (3 villagers)"), "the hall says: " + line);
			helper.succeed();
		});
	}

	/**
	 * realmMaxMembers at its least, 2: the capital adds one village through the Realm tab; then the tab offers no more,
	 * reads "Villages: 2 of 2", adding the third is refused with the full sentence, and a colony of the capital founded
	 * now stays out. At 3 the same colony joins by itself.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaExtRealmTwo"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaExtRealmTwo")
	public void realmOfTwoTakesNoThird(GameTestHelper helper) {
		RealmGameTests.Land land = RealmGameTests.land(helper, VillageRanks.Rank.TOWN, VillageRanks.Rank.HAMLET, VillageRanks.Rank.HAMLET);
		Realms.MAX_MEMBERS = 2; // land() puts the old value back after
		ChoiceMenu menu = RealmGameTests.found(helper, land);
		ItemStack add = menu.icon(Realms.MEMBERS + 1);
		helper.assertTrue(add.getHoverName().getString().equals("Add " + land.name(1)), "a realm of one of two offers no village: " + add.getHoverName().getString());
		menu.press(Realms.MEMBERS + 1, land.owner());
		helper.assertTrue(land.data().realmOf(land.at(1)) != null, "the second village didn't join a realm of two");
		String title = "the Barony of " + land.name(0);
		helper.assertTrue(RealmGameTests.heard(land, land.name(1) + " has joined " + title + "."), "no joined line: " + land.heard());
		// full: nothing more offered, and the title says so
		ItemStack next = menu.icon(Realms.MEMBERS + 2);
		helper.assertTrue(!next.getHoverName().getString().startsWith("Add "), "a full realm still offers " + next.getHoverName().getString());
		List<String> titleLore = RealmGameTests.lore(menu.icon(Realms.TITLE));
		helper.assertTrue(titleLore.contains("Villages: 2 of 2"), "the realm's title lines: " + titleLore);
		Realms.Result result = Realms.add(land.level(), land.hall(0), land.at(2), land.owner());
		helper.assertTrue(result.refusal() == Realms.Refusal.FULL, "a third village in a realm of two: " + result);
		// (its first letter's case: QaConfigExtremesBugTests.realmFullSentenceStartsWithACapital)
		helper.assertTrue(result.message().getString().equalsIgnoreCase(title + " already holds 2 villages, as many as a realm may."), "the refusal: " + result.message().getString());
		helper.assertTrue(land.data().realmOf(land.at(2)) == null, "the third village joined anyway");
		// a colony of the capital, founded while it's full, stays out
		Realms.colonyFounded(land.level(), land.at(0), land.at(2));
		helper.assertTrue(land.data().realmOf(land.at(2)) == null, "a colony joined a full realm of two");
		helper.assertTrue(land.data().realmOf(land.at(0)).members().size() == 2, "members: " + land.data().realmOf(land.at(0)).members().size());
		// the neighbour, 3: room for the colony
		Realms.MAX_MEMBERS = 3;
		Realms.colonyFounded(land.level(), land.at(0), land.at(2));
		helper.assertTrue(land.data().realmOf(land.at(2)) != null && land.data().realmOf(land.at(0)).members().size() == 3, "at 3 the colony didn't join");
		helper.succeed();
	}

	/**
	 * treasuryPerWorker 0: a day's round with 25 workers puts nothing by, and the hall's name tag (the collect button)
	 * says there's nothing to collect; at 1 the same day puts something by.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaExtTreasuryZero"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaExtTreasuryZero")
	public void treasuryPerWorkerZeroEarnsNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		int cents = Treasury.CENTS_PER_WORKER;
		Leftovers.after(helper, () -> Treasury.CENTS_PER_WORKER = cents);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, heard, "qa-ext-tax");
		helper.runAfterDelay(3, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			long day = Chronicle.day(level);
			Treasury.CENTS_PER_WORKER = 0;
			entity.setTreasury(0);
			entity.setLastTaxDay(day - 1);
			Treasury.round(level, hall, entity, 25);
			helper.assertTrue(entity.lastTaxDay() == day, "the day's round didn't run: last " + entity.lastTaxDay() + ", today " + day);
			helper.assertTrue(entity.treasury() == 0, "treasuryPerWorker 0 put by " + entity.treasury() + " hundredths");
			VillageHallScreen.forTest(player, hall).press(VillageHallScreen.NAME, player);
			helper.assertTrue(heard.stream().anyMatch(c -> c.getString().equals("The treasury has nothing to collect yet: a village puts by its takings each morning.")),
				"collecting said: " + heard.stream().map(Component::getString).toList());
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 0, "emeralds from an empty treasury");
			// the neighbour, 1: 25 workers bring something in
			Treasury.CENTS_PER_WORKER = 1;
			entity.setLastTaxDay(day - 1);
			Treasury.round(level, hall, entity, 25);
			helper.assertTrue(entity.treasury() > 0 && entity.treasury() <= 38, "treasuryPerWorker 1, 25 workers: " + entity.treasury());
			helper.succeed();
		});
	}

	/**
	 * treasuryPerWorker 500 (the most): a day of 40 workers (at least 100 emeralds) comes to far more than a Hamlet's chest holds, so
	 * the treasury stops at its cap of 64 emeralds, and collecting gives exactly those 64 and empties it.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaExtTreasuryMax"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaExtTreasuryMax")
	public void treasuryPerWorkerMaxStopsAtTheCap(GameTestHelper helper) {
		Leftovers.clear(helper);
		int cents = Treasury.CENTS_PER_WORKER;
		Leftovers.after(helper, () -> Treasury.CENTS_PER_WORKER = cents);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = RealmGameTests.listener(helper, heard, "qa-ext-taxmax");
		helper.runAfterDelay(3, () -> {
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			entity.setRank(VillageRanks.Rank.HAMLET);
			long day = Chronicle.day(level);
			Treasury.CENTS_PER_WORKER = 500;
			entity.setTreasury(0);
			entity.setLastTaxDay(day - 1); // (day - 3 can be before day 0 on a fresh test world: then it's a new hall's first round)
			Treasury.round(level, hall, entity, 40);
			helper.assertTrue(entity.lastTaxDay() == day, "the day's round didn't run: last " + entity.lastTaxDay() + ", today " + day);
			helper.assertTrue(Treasury.cap(entity) == 64, "a Hamlet's cap: " + Treasury.cap(entity));
			helper.assertTrue(entity.treasury() == 6400, "treasuryPerWorker 500, 40 workers, a day: " + entity.treasury() + " hundredths (cap 6400)");
			VillageHallScreen.forTest(player, hall).press(VillageHallScreen.NAME, player);
			String expected = "You collect 64 emeralds from the treasury of " + VillageHalls.name(level, hall).getString() + ".";
			helper.assertTrue(heard.stream().anyMatch(c -> c.getString().equals(expected)), "collecting said: " + heard.stream().map(Component::getString).toList());
			helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 64, "emeralds collected: " + player.getInventory().countItem(Items.EMERALD));
			helper.assertTrue(entity.treasury() == 0, "left in the treasury: " + entity.treasury());
			helper.succeed();
		});
	}

	/**
	 * maxWorkersPerVillage 0, "no cap": a village that already has three workers still gives a jobless villager the free
	 * fletching table by itself (the cap-1 village in {@code ServerControlsGameTests} keeps them jobless).
	 */
	//$ gametest_ticks_batch BIG '1200' '"qaExtNoWorkerCap"'
	@GameTest(template = BIG, timeoutTicks = 1200, batch = "qaExtNoWorkerCap")
	public void workerCapZeroMeansNoCap(GameTestHelper helper) {
		Leftovers.clear(helper);
		int max = WorkerLimits.MAX_PER_VILLAGE;
		int radius = WorkerLimits.RADIUS;
		WorkerLimits.MAX_PER_VILLAGE = 0;
		WorkerLimits.RADIUS = 8;
		Leftovers.after(helper, () -> {
			WorkerLimits.MAX_PER_VILLAGE = max;
			WorkerLimits.RADIUS = radius;
		});
		ServerLevel level = helper.getLevel();
		helper.setDayTime(1000);
		for (int z : new int[] {2, 4, 6}) {
			BlockPos table = new BlockPos(4, 2, z);
			helper.setBlock(table, Blocks.SMITHING_TABLE);
			Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, z));
			Jobs.employ(level, worker, helper.absolutePos(table), PoiTypes.TOOLSMITH, VillagerProfession.TOOLSMITH);
		}
		BlockPos free = new BlockPos(8, 2, 4);
		helper.setBlock(free, Blocks.FLETCHING_TABLE);
		Villager jobless = helper.spawn(EntityType.VILLAGER, free.south(6));
		helper.succeedWhen(() -> helper.assertTrue(jobless.getVillagerData().getProfession() != VillagerProfession.NONE
			&& io.github.jcondedata.aliveworkplace.work.Stations.at(Blocks.FLETCHING_TABLE).orElseThrow().has(jobless.getVillagerData().getProfession()),
			"cap 0, three workers: still " + jobless.getVillagerData().getProfession()));
	}
}

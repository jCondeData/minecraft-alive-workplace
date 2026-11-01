package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes.MadeDay;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.PersonalRequests;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-01): independent checks of six landed fixes in villagers' homes and life, written from the bug texts and
 * the specs (village-hall.md "everyone within 64 blocks", 31.10 "a cat or a tamed wolf within 10 blocks of their bed at
 * dawn", 34.8 / 34.20 / 34.21 family trades and retirement, 34.9 "Vintage Wine is a Berry Wine at least 3 days old"),
 * not from the code. Through what the hall itself runs and shows: its census and card, its round
 * ({@link VillageNeeds#check}, {@link Stories#round}, {@link Retirement#round} with the census' grown villagers), and the
 * trade screen a client opens.
 */
public class QaVillageHomesLife1101GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** en_us: screen.aliveworkplace.hall.no_bed. */
	private static final String NO_BED = "No bed of their own";
	/** en_us: screen.aliveworkplace.hall.retire_waiting, for Bram. */
	private static final String WAITING = "Bram would like to retire: the village needs a jobless villager to take over";
	/** QaClassesGameTests' hall (setUpForBugs). */
	private static final BlockPos CLASS_HALL = new BlockPos(14, 2, 14);

	// --- B361: one circle for villagers and beds ------------------------------------------------------------------

	/**
	 * B361 (village-hall.md: "everyone within [the reach] belongs to it", a circle; 16 here). Three villagers, each beside
	 * his own bed, near the corner of the reach: one pair just inside (bed 15.6, villager 15.6 away), one on the edge
	 * itself (bed exactly 16 east), one just outside (bed 16.4, villager 16.3). Both of the first two pairs are in the
	 * village and the third pair is out: two villagers, two beds, nobody a bed short and no "beds" tip.
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaHomes1101Corner"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaHomes1101Corner")
	public void villagersAndTheirBedsAtTheCornerAreInOrOutTogether(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(2, 2, 2);
		QaHallHomesGameTests.hall(helper, hallRel);
		// just inside: bed head (12, 10) -> 244, villager (11, 11) -> 242 (16^2 = 256)
		QaHallHomesGameTests.bed(helper, hallRel.offset(12, 0, 10));
		QaHallHomesGameTests.adult(helper, hallRel.offset(11, 0, 11));
		// on the edge: bed head (16, 0) -> 256, villager (15, 2) -> 229
		QaHallHomesGameTests.bed(helper, hallRel.offset(16, 0, 0));
		QaHallHomesGameTests.adult(helper, hallRel.offset(15, 0, 2));
		// just outside: bed head (10, 13) -> 269, villager (11, 12) -> 265 (both inside the old square of +-16)
		QaHallHomesGameTests.bed(helper, hallRel.offset(10, 0, 13));
		QaHallHomesGameTests.adult(helper, hallRel.offset(11, 0, 12));
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(hallRel);
			VillageHalls.Census census = VillageHalls.census(level, hall);
			int villagers = census.workers().size() + census.jobless().size() + census.children();
			List<String> tips = VillageAdvice.tips(level, hall).stream().filter(t -> t.key().equals("beds")).map(t -> t.title().getString()).toList();
			helper.assertTrue(villagers == 2 && census.beds() == 2 && VillageAdvice.bedsShort(census) == 0 && tips.isEmpty(),
				"near the corner of a reach of 16: " + villagers + " villager(s) and " + census.beds() + " bed(s) counted (expected 2 and 2), "
					+ VillageAdvice.bedsShort(census) + " short, beds tips " + tips);
			int housed = VillageNeeds.count(level, hall).villagers();
			helper.assertTrue(housed == 2, "the village's needs count " + housed + " villagers (the census counts " + villagers + ")");
			helper.succeed();
		});
	}

	// --- B362: a broken bed houses nobody at once; a bed put back houses him again -------------------------------

	/**
	 * B362: Tom has a bed of his own (a real bed he claimed). At late morning the player breaks the bed's FOOT (the half
	 * the existing test doesn't break): within a few ticks the hall's card reads "No bed of their own", he isn't housed
	 * and the census is a bed short, hours before bedtime. The player puts a bed back on the same spot: the card shows
	 * his home again and he's housed.
	 */
	//$ gametest_ticks_batch HUGE '120' '"qaHomes1101BedBack"'
	@GameTest(template = HUGE, timeoutTicks = 120, batch = "qaHomes1101BedBack")
	public void aBedBrokenByDayUnhousesAtOnceAndOnePutBackHousesAgain(GameTestHelper helper) {
		BlockPos hallRel = new BlockPos(15, 2, 15);
		QaHallHomesGameTests.hall(helper, hallRel);
		ServerLevel level = helper.getLevel();
		level.setDayTime((level.getDayTime() / 24000L + 1) * 24000L + 5000L);
		BlockPos head = new BlockPos(10, 2, 10);
		QaHallHomesGameTests.bed(helper, head); // foot to the south, at (10, 2, 11)
		Villager tom = QaHallHomesGameTests.adult(helper, new BlockPos(12, 2, 12));
		tom.setCustomName(Component.literal("Tom"));
		BlockPos hall = helper.absolutePos(hallRel);
		helper.runAfterDelay(3, () -> {
			Beds.home(tom, helper.absolutePos(head));
			List<String> before = lore(level, hall, tom);
			int housedBefore = VillageNeeds.count(level, hall).housed();
			helper.assertTrue(housedBefore == 1 && !before.contains(NO_BED) && QaHallHomesGameTests.bedsShort(helper, hallRel) == 0,
				"setup: with his bed, housed " + housedBefore + ", card " + before);
			helper.destroyBlock(head.south()); // the foot; the head goes with it
			helper.runAfterDelay(3, () -> {
				helper.assertTrue(level.getBlockState(helper.absolutePos(head)).isAir(), "the bed's head is still there after breaking its foot");
				List<String> broken = lore(level, hall, tom);
				int housed = VillageNeeds.count(level, hall).housed();
				int short1 = QaHallHomesGameTests.bedsShort(helper, hallRel);
				helper.assertTrue(broken.contains(NO_BED) && housed == 0 && short1 == 1 && VillageNeeds.bed(level, tom) == null,
					"3 ticks after his bed was broken at day time " + level.getDayTime() % 24000L + ": housed " + housed + ", a bed short " + short1
						+ ", card " + broken);
				QaHallHomesGameTests.bed(helper, head);
				helper.runAfterDelay(3, () -> {
					List<String> back = lore(level, hall, tom);
					int housedBack = VillageNeeds.count(level, hall).housed();
					helper.assertTrue(!back.contains(NO_BED) && housedBack == 1 && QaHallHomesGameTests.bedsShort(helper, hallRel) == 0,
						"a bed put back where his was: housed " + housedBack + ", a bed short " + QaHallHomesGameTests.bedsShort(helper, hallRel)
							+ ", card " + back);
					helper.assertTrue(back.stream().anyMatch(l -> l.startsWith("Lives in the ") || l.equals("Sleeps in a house no builder put up")),
						"the card names no home for him: " + back);
					helper.succeed();
				});
			});
		});
	}

	// --- B397: a grown child takes up a parent's trade on a block whose first job is vanilla -----------------------

	/**
	 * B397 (34.8 classes may take only their own jobs and those below): Nia, an Artisan, is the grown daughter of a Nurse
	 * (an Artisan job). The only free block is a brewing stand, which is a Cleric's first. The hall's round
	 * ({@link VillageNeeds#check}) makes her a Nurse at that stand, and the chronicle reads "Nia took up the family trade:
	 * Nurse" (not Cleric).
	 */
	//$ gametest_ticks_batch BIG '80' '"qaHomes1101Nurse"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qaHomes1101Nurse")
	public void anArtisanDaughterOfANurseTakesTheBrewingStandAsANurse(GameTestHelper helper) {
		QaClassesGameTests.setUpForBugs(helper);
		BlockPos stand = new BlockPos(8, 2, 4);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(CLASS_HALL);
			Villager nia = QaClassesGameTests.spawn(helper, new BlockPos(6, 2, 10), "Nia", "artisan");
			ModAttachments.PARENTS.set(nia, new Families.Parents(Component.literal("Mara"), Component.literal("Tom"), "aliveworkplace:nurse", "", true));
			VillageNeeds.check(level, hall);
			helper.assertTrue(nia.getVillagerData().getProfession() == ModVillagers.NURSE,
				"Nia, an Artisan whose mother is a Nurse, by a free brewing stand: her job is " + QaClassesGameTests.jobIdOf(nia));
			helper.assertTrue(nia.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(stand)::equals).isPresent(),
				"Nia's job site is " + nia.getBrain().getMemory(MemoryModuleType.JOB_SITE) + ", not the brewing stand");
			List<String> lines = chronicle(level, hall);
			helper.assertTrue(lines.contains("Nia took up the family trade: Nurse"), "the chronicle: " + lines);
			helper.succeed();
		});
	}

	/**
	 * B397 both sides of the class gate on one block: Pip, a Peasant whose mother is a Nurse, waits (a Nurse is an Artisan's
	 * job) and the brewing stand stays free. Cass, a Peasant whose mother is a Nurse and father a Cleric, takes the stand
	 * as a Cleric (the father's vanilla trade), not as a Nurse.
	 */
	//$ gametest_ticks_batch BIG '80' '"qaHomes1101NurseCleric"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qaHomes1101NurseCleric")
	public void aPeasantChildOfANurseWaitsButOneWithAClericFatherTakesTheClericsTrade(GameTestHelper helper) {
		QaClassesGameTests.setUpForBugs(helper);
		BlockPos stand = new BlockPos(8, 2, 4);
		helper.setBlock(stand, Blocks.BREWING_STAND);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(CLASS_HALL);
			Villager pip = QaClassesGameTests.spawn(helper, new BlockPos(4, 2, 10), "Pip", "peasant");
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Mara"), Component.literal("Tom"), "aliveworkplace:nurse", "", true));
			VillageNeeds.check(level, hall);
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE && pip.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"Pip, a Peasant, took up the Nurse's trade: " + QaClassesGameTests.jobIdOf(pip) + " " + pip.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(VillageHalls.freeStations(level, hall).stream().anyMatch(f -> f.pos().equals(helper.absolutePos(stand))),
				"the brewing stand isn't free after Pip waited");
			Villager cass = QaClassesGameTests.spawn(helper, new BlockPos(6, 2, 10), "Cass", "peasant");
			ModAttachments.PARENTS.set(cass, new Families.Parents(Component.literal("Ilse"), Component.literal("Oren"), "aliveworkplace:nurse", "minecraft:cleric", true));
			VillageNeeds.check(level, hall);
			helper.assertTrue(cass.getVillagerData().getProfession() == VillagerProfession.CLERIC,
				"Cass, a Peasant whose father is a Cleric, by a free brewing stand: her job is " + QaClassesGameTests.jobIdOf(cass));
			helper.assertTrue(cass.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(stand)::equals).isPresent(),
				"Cass's job site is " + cass.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE, "Pip has a job now: " + QaClassesGameTests.jobIdOf(pip));
			List<String> lines = chronicle(level, hall);
			helper.assertTrue(lines.contains("Cass took up the family trade: Cleric") && lines.stream().noneMatch(l -> l.startsWith("Pip took up")),
				"the chronicle: " + lines);
			helper.succeed();
		});
	}

	// --- B423: "A cat by the door", 10 blocks at dawn -------------------------------------------------------------

	/**
	 * B423 (31.10: "a cat or a tamed wolf within 10 blocks of their bed at dawn"): a cat sitting exactly 10 blocks from
	 * Dara's bed along the floor, diagonally (6 east, 8 south), when the hall's round sees the new day: the request is
	 * done.
	 */
	//$ gametest_ticks_batch BIG '80' '"qaHomes1101CatDiag"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qaHomes1101CatDiag")
	public void aCatExactlyTenBlocksDiagonallyAtDawnCounts(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Request r = request(helper);
			Cat cat = helper.spawn(EntityType.CAT, Vec3.atBottomCenterOf(r.bed).add(6, 0, 8));
			cat.setNoAi(true);
			dawn(helper);
			double d = Math.sqrt(cat.distanceToSqr(Vec3.atBottomCenterOf(helper.absolutePos(r.bed))));
			helper.assertTrue(r.quest.progress[0] >= 1, "a cat " + d + " blocks from the bed along the floor at dawn didn't count (progress " + r.quest.progress[0] + ")");
			helper.succeed();
		});
	}

	/**
	 * B423 the other side, and "at dawn": at dawn a cat sits 10.05 blocks from the bed and an untamed wolf 5 blocks away;
	 * neither counts. The cat walks up to exactly 10 blocks later that day: still nothing until the next dawn, when it
	 * counts.
	 */
	//$ gametest_ticks_batch BIG '80' '"qaHomes1101CatPast"'
	@GameTest(template = BIG, timeoutTicks = 80, batch = "qaHomes1101CatPast")
	public void aCatJustPastTenBlocksOrAfterDawnDoesNotCountUntilTheNextDawn(GameTestHelper helper) {
		PersonalRequestGameTests.village(helper, () -> {
			ServerLevel level = helper.getLevel();
			Request r = request(helper);
			Vec3 floor = Vec3.atBottomCenterOf(r.bed);
			Cat cat = helper.spawn(EntityType.CAT, floor.add(6, 0, 8.06));
			cat.setNoAi(true);
			Wolf wolf = helper.spawn(EntityType.WOLF, floor.add(5, 0, 0));
			wolf.setNoAi(true);
			dawn(helper);
			helper.assertTrue(r.quest.progress[0] == 0, "a cat 10.05 blocks away and an untamed wolf 5 away counted at dawn (progress " + r.quest.progress[0] + ")");
			Vec3 ten = helper.absoluteVec(floor.add(6, 0, 8));
			cat.moveTo(ten.x, ten.y, ten.z, 0, 0);
			Stories.round(level, helper.absolutePos(PersonalRequestGameTests.HALL), PersonalRequestGameTests.hallEntity(helper));
			helper.assertTrue(r.quest.progress[0] == 0, "a cat that came after dawn counted the same day");
			dawn(helper);
			helper.assertTrue(r.quest.progress[0] >= 1, "a cat 10 blocks away at the next dawn didn't count");
			helper.succeed();
		});
	}

	// --- B442: the waiting line and the class of the jobless villager ---------------------------------------------

	/**
	 * B442 (34.20: "else any jobless grown villager whose class the job allows"; the card's line is up only while nobody
	 * could take over). Bram, an elder Scholar (a Burgher's job), holds a building site. Pia, a jobless Peasant, can't
	 * take a Scholar's trade, so the card keeps "Bram would like to retire: the village needs a jobless villager to take
	 * over". Ida, a jobless Burgher, arrives: the line goes at the hall's next round, though nobody may start learning yet
	 * (Odo retired yesterday: one a village every 3 days). The hall's census runs first, as in the real round.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaHomes1101ElderClass"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaHomes1101ElderClass")
	public void aJoblessPeasantCantTakeOverAScholarButAJoblessBurgherClearsTheLine(GameTestHelper helper) {
		QaClassesGameTests.setUpForBugs(helper);
		boolean retirement = Retirement.ENABLED;
		boolean ages = LifeStages.AGES;
		int elderDays = LifeStages.ELDER_DAYS;
		Leftovers.after(helper, () -> {
			Retirement.ENABLED = retirement;
			LifeStages.AGES = ages;
			LifeStages.ELDER_DAYS = elderDays;
		});
		Retirement.ENABLED = true;
		LifeStages.AGES = true;
		LifeStages.ELDER_DAYS = 1200;
		Villager bram = QaClassesGameTests.spawn(helper, new BlockPos(5, 2, 6), "Bram", "burgher");
		bram.setVillagerData(bram.getVillagerData().setProfession(ModVillagers.SCHOLAR).setLevel(3));
		bram.setVillagerXp(1);
		Villager pia = QaClassesGameTests.spawn(helper, new BlockPos(7, 2, 6), "Pia", "peasant");
		Villager odo = QaClassesGameTests.spawn(helper, new BlockPos(11, 2, 6), "Odo", "peasant");
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(CLASS_HALL);
			long today = Chronicle.day(level);
			LifeStages.setAdultSince(bram, today - 1250);
			LifeStages.setAdultSince(pia, today - 20);
			LifeStages.setAdultSince(odo, today - 1300);
			ModAttachments.RETIRED.set(odo, new Retirement.Retired(today - 1, "minecraft:farmer", 3));
			retirementRound(level, hall);
			helper.assertTrue(QaElderLifeGameTests.lore(level, hall, bram).contains(WAITING),
				"only a jobless Peasant (who may not be a Scholar) is there, yet the card says: " + QaElderLifeGameTests.lore(level, hall, bram));
			Villager ida = QaClassesGameTests.spawn(helper, new BlockPos(9, 2, 6), "Ida", "burgher");
			LifeStages.setAdultSince(ida, today - 20);
			retirementRound(level, hall);
			List<String> card = QaElderLifeGameTests.lore(level, hall, bram);
			helper.assertTrue(!card.contains(WAITING), "Ida, a jobless Burgher, is here, yet the card says: " + card);
			helper.assertTrue(Retirement.shadow(ida) == null && Retirement.shadow(pia) == null, "someone started learning a day after Odo retired");
			helper.succeed();
		});
	}

	// --- B443: Berry Wine bought from a Vintner carries its pressed day --------------------------------------------

	/**
	 * B443 (34.9: "Vintage Wine is a Berry Wine at least 3 days old"): a player opens an Apprentice Vintner's trade screen,
	 * picks the Berry Wine and shift-clicks the result. Every bottle in the inventory is pressed today; the tooltip reads
	 * "Pressed on day N · vintage in 3 days", "vintage in 1 day" two days later and "Pressed on day N · vintage" on the
	 * third. It is too young for Vintage Wine on days N+2, old enough on N+3, and the day survives the stack's save.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHomes1101WineBought"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHomes1101WineBought")
	public void aBerryWineBoughtOnTheTradeScreenIsPressedToday(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager vintner = vintner(helper, new BlockPos(3, 2, 3));
		int index = wineOffer(vintner);
		List<ItemStack> wines = buy(helper, vintner, index, "qaWine");
		ServerLevel level = helper.getLevel();
		long today = LuxuryRecipes.today(level);
		helper.assertTrue(!wines.isEmpty(), "the player bought no Berry Wine");
		for (ItemStack wine : wines) {
			MadeDay made = wine.get(ModComponents.MADE_DAY);
			helper.assertTrue(made != null && made.day() == today, "a Berry Wine bought on day " + today + " is stamped " + made);
		}
		ItemStack wine = wines.get(0);
		MadeDay made = wine.get(ModComponents.MADE_DAY);
		String now = LuxuryRecipes.tooltip(made, today).getString();
		String two = LuxuryRecipes.tooltip(made, today + 2).getString();
		String three = LuxuryRecipes.tooltip(made, today + 3).getString();
		helper.assertTrue(now.equals("Pressed on day " + today + " · vintage in 3 days") && two.equals("Pressed on day " + today + " · vintage in 1 day")
			&& three.equals("Pressed on day " + today + " · vintage"), "the tooltips: '" + now + "', '" + two + "', '" + three + "'");
		helper.assertTrue(!LuxuryRecipes.agedFor(wine, 3, today) && !LuxuryRecipes.agedFor(wine, 3, today + 2) && LuxuryRecipes.agedFor(wine, 3, today + 3),
			"aged 3 days: today " + LuxuryRecipes.agedFor(wine, 3, today) + ", +2 " + LuxuryRecipes.agedFor(wine, 3, today + 2) + ", +3 "
				+ LuxuryRecipes.agedFor(wine, 3, today + 3));
		Tag saved = wine.save(level.registryAccess());
		ItemStack loaded = ItemStack.parse(level.registryAccess(), saved).orElse(ItemStack.EMPTY);
		helper.assertTrue(made.equals(loaded.get(ModComponents.MADE_DAY)), "the pressed day after a save and load: " + loaded.get(ModComponents.MADE_DAY));
		helper.succeed();
	}

	/**
	 * B443's fix stamps at sale time "so it also covers offers already saved on villagers": a Vintner whose Berry Wine
	 * offer was saved unstamped is saved and loaded (as when the chunk reloads) and the player buys from the loaded
	 * villager: the wine is pressed today. Bread bought from a Farmer the same way is plain bread, with no day, and stacks
	 * with bread from anywhere else.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaHomes1101WineReload"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaHomes1101WineReload")
	public void aReloadedVintnersWineIsStampedAndBoughtBreadIsNot(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager old = vintner(helper, new BlockPos(3, 2, 3));
		int index = wineOffer(old);
		helper.assertTrue(old.getOffers().get(index).getResult().get(ModComponents.MADE_DAY) == null, "setup: the saved offer's wine already has a day");
		CompoundTag tag = old.saveWithoutId(new CompoundTag());
		tag.putString("id", "minecraft:villager");
		tag.remove("UUID");
		old.discard();
		Entity loaded = EntityType.create(tag, level).orElse(null);
		helper.assertTrue(loaded instanceof Villager, "the saved Vintner didn't load: " + loaded);
		Villager vintner = (Villager) loaded;
		level.addFreshEntity(vintner);
		helper.assertTrue(vintner.getVillagerData().getProfession() == ModVillagers.VINTNER && vintner.getOffers().size() > index
			&& vintner.getOffers().get(index).getResult().is(ModItems.BERRY_WINE), "the loaded Vintner lost his offers: " + vintner.getOffers().size());
		List<ItemStack> wines = buy(helper, vintner, index, "qaWineRe");
		long today = LuxuryRecipes.today(level);
		helper.assertTrue(!wines.isEmpty() && wines.stream().allMatch(w -> w.get(ModComponents.MADE_DAY) != null && w.get(ModComponents.MADE_DAY).day() == today),
			"wine bought from the reloaded Vintner on day " + today + ": " + wines.stream().map(w -> String.valueOf(w.get(ModComponents.MADE_DAY))).toList());

		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		farmer.setNoAi(true);
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
		MerchantOffers bread = new MerchantOffers();
		bread.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.BREAD, 6), 12, 1, 0.05f));
		farmer.setOffers(bread);
		List<ItemStack> loaves = buy(helper, farmer, 0, "qaBread");
		helper.assertTrue(!loaves.isEmpty(), "the player bought no bread");
		for (ItemStack loaf : loaves) {
			helper.assertTrue(!loaf.has(ModComponents.MADE_DAY) && ItemStack.isSameItemSameComponents(loaf, new ItemStack(Items.BREAD)),
				"bought bread isn't plain bread: " + loaf.getComponentsPatch());
		}
		helper.succeed();
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	private static List<String> lore(ServerLevel level, BlockPos hall, Villager villager) {
		ItemLore lore = io.github.jcondedata.aliveworkplace.hall.VillageHallScreen.person(level, hall, villager).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}

	private static List<String> chronicle(ServerLevel level, BlockPos hall) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		return entity.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	/** The hall's own retirement round: the census' grown villagers, as VillageHallBlockEntity passes them. */
	private static void retirementRound(ServerLevel level, BlockPos hall) {
		VillageHalls.Census census = VillageHalls.census(level, hall);
		Retirement.round(level, hall, Stream.concat(census.workers().stream(), census.jobless().stream()).toList());
	}

	private record Request(Villager dara, Quest quest, BlockPos bed) {
	}

	/** Dara (3 hearts with the player) asked for and the player took "A cat by the door"; her bed (a real one) at (2, 2, 2). */
	private static Request request(GameTestHelper helper) {
		Villager dara = PersonalRequestGameTests.worker(helper, new BlockPos(3, 2, 5), "Dara", "minecraft:farmer", 2);
		ServerPlayer me = PersonalRequestGameTests.friend(helper, new BlockPos(5, 2, 6), dara, 300);
		BlockPos bed = new BlockPos(2, 2, 2);
		Beds.home(dara, helper.absolutePos(bed));
		Quest quest = PersonalRequestGameTests.accepted(helper, "cat_by_the_door", dara, me);
		helper.assertTrue(quest.progress[0] == 0, "setup: the request starts done");
		return new Request(dara, quest, bed);
	}

	/** The next day's dawn, and the hall's round on it (its first round of the day is the one that looks at dawn). */
	private static void dawn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.setDayTime((level.getDayTime() / 24000L + 1) * 24000L + 10L);
		Stories.round(level, helper.absolutePos(PersonalRequestGameTests.HALL), PersonalRequestGameTests.hallEntity(helper));
	}

	/** An Apprentice Vintner with his level-2 offers drawn with a fixed die. */
	private static Villager vintner(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.VINTNER).setLevel(2));
		MerchantOffers offers = new MerchantOffers();
		for (VillagerTrades.ItemListing listing : VillagerTrades.TRADES.get(ModVillagers.VINTNER).get(2)) {
			MerchantOffer offer = listing.getOffer(v, RandomSource.create(3));
			if (offer != null) {
				offers.add(offer);
			}
		}
		v.setOffers(offers);
		return v;
	}

	private static int wineOffer(Villager vintner) {
		MerchantOffers offers = vintner.getOffers();
		for (int i = 0; i < offers.size(); i++) {
			if (offers.get(i).getResult().is(ModItems.BERRY_WINE)) {
				return i;
			}
		}
		throw new net.minecraft.gametest.framework.GameTestAssertException("the Apprentice Vintner sells no Berry Wine: " + offers.size() + " offers");
	}

	/**
	 * A client player right-clicks {@code seller}, picks offer {@code index} on the trade screen (which fills the payment
	 * from the inventory, as the client's select-trade packet does) and shift-clicks the result. Returns every stack of the
	 * offer's result item in the player's inventory afterwards.
	 */
	private static List<ItemStack> buy(GameTestHelper helper, Villager seller, int index, String who) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name(who));
		ServerPlayer p = c.player();
		MerchantOffer offer = seller.getOffers().get(index);
		Item result = offer.getResult().getItem();
		p.getInventory().add(offer.getCostA().copyWithCount(Math.min(64, offer.getCostA().getCount() * 2)));
		if (!offer.getCostB().isEmpty()) {
			p.getInventory().add(offer.getCostB().copyWithCount(Math.min(64, offer.getCostB().getCount() * 2)));
		}
		QaStageFourGameTests.pressOn(p, seller, c.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open: " + p.containerMenu);
		MerchantMenu menu = (MerchantMenu) p.containerMenu;
		menu.setSelectionHint(index);
		menu.tryMoveItems(index);
		ItemStack shown = menu.getSlot(2).getItem().copy();
		helper.assertTrue(shown.is(result), "the trade screen shows " + shown + " for " + offer.getResult());
		menu.clicked(2, 0, ClickType.QUICK_MOVE, p);
		p.closeContainer();
		List<ItemStack> got = new ArrayList<>();
		for (ItemStack s : p.getInventory().items) {
			if (s.is(result)) {
				got.add(s.copy());
			}
		}
		return got;
	}
}

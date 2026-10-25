package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.jeweller.Jewellers;
import io.github.jcondedata.aliveworkplace.people.ClassJobs;
import io.github.jcondedata.aliveworkplace.people.ClassNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.printer.PrinterWork;
import io.github.jcondedata.aliveworkplace.printer.Printers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.tailor.TailorWork;
import io.github.jcondedata.aliveworkplace.tailor.Tailors;
import io.github.jcondedata.aliveworkplace.vintner.VintnerWork;
import io.github.jcondedata.aliveworkplace.vintner.Vintners;
import io.github.jcondedata.aliveworkplace.work.Requests;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-c-1025-0653): the four luxury trades (ROADMAP 34.9-34.12) with 34.4/34.5/34.8, written from the spec at the
 * gaps the earlier suites leave: the refusals a player meets when hiring (no block nearby, the block taken, a baby, the
 * job's switch off) through the real sneak-right-click and read with their arguments filled; each recipe's numbers from
 * both sides (3 apples, 6 sweet or 4 glow berries never mixed, a bottle, 3 wool, 3 paper making 2, the Journeyman's
 * Illuminated Book); a reload mid re-corking keeping the young wine's day; the made-day tooltip on its first and last day
 * and through a save; 34.8's "whoever makes a luxury a class needs is open to the class below it"; and the Vintner's
 * trades at every level.
 */
public class QaLuxuryTradesGameTests implements FabricGameTest {
	static final String AREA = QaLuxuryJobsGameTests.AREA;
	private static final BlockPos STATION = QaLuxuryJobsGameTests.STATION;
	private static final BlockPos STANDING = new BlockPos(3, 2, 3);
	private static final BlockPos BESIDE = new BlockPos(1, 2, 3);

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	private static Villager jobless(GameTestHelper helper, BlockPos at, String called) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(called));
		return v;
	}

	private static List<String> keys(List<Component> seen) {
		return seen.stream().map(QaLuxuryJobsGameTests::key).toList();
	}

	private static Component said(List<Component> seen, String key) {
		return seen.stream().filter(c -> QaLuxuryJobsGameTests.key(c).equals(key)).findFirst().orElse(null);
	}

	private static boolean worksAt(GameTestHelper helper, Villager v, BlockPos rel) {
		return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(rel)::equals).isPresent();
	}

	private static int stamped(Container c, Item item, long day) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			LuxuryRecipes.MadeDay d = s.get(ModComponents.MADE_DAY);
			if (s.is(item) && d != null && d.day() == day) {
				n += s.getCount();
			}
		}
		return n;
	}

	private static boolean asksFor(GameTestHelper helper, Villager v, Item item) {
		return Requests.of(helper.getLevel(), v).stream().anyMatch(r -> r.accepts().test(new ItemStack(item)));
	}

	private static List<String> asked(GameTestHelper helper, Villager v) {
		return Requests.of(helper.getLevel(), v).stream().map(r -> r.what().getString()).toList();
	}

	// ---- hiring: the refusals ----

	record Want(Item item, VillagerProfession job, Block block, String english) {
	}

	/**
	 * 34.10-34.12: "stand a villager by a loom / cartography table / stonecutter". With none of the job's blocks in
	 * reach, a sneak-right-click with the job's item gives no job and tells the player which block the job needs, the
	 * job's name and the block filled in; a baby by the right block takes no job at all.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLuxTrNoBlock"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLuxTrNoBlock")
	public void withNoBlockInReachTheClickSaysWhichOneTheJobNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaLuxuryJobsGameTests.listener(helper, seen);
			for (Want want : List.of(
					new Want(Items.STRING, ModVillagers.TAILOR, Blocks.LOOM, "Tailor"),
					new Want(Items.INK_SAC, ModVillagers.PRINTER, Blocks.CARTOGRAPHY_TABLE, "Printer"),
					new Want(Items.GOLD_NUGGET, ModVillagers.JEWELLER, Blocks.STONECUTTER, "Jeweler"))) {
				Villager v = jobless(helper, STANDING, "Wren");
				QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(want.item(), 2));
				seen.clear();
				QaLuxuryJobsGameTests.click(player, v, true);
				helper.assertTrue(job(v) == VillagerProfession.NONE, want.item() + " with no " + want.block() + " near: became a " + name(job(v)));
				Component needs = said(seen, "message.aliveworkplace.job.needs_station");
				helper.assertTrue(needs != null, want.item() + " with no block near: the player wasn't told which block (said " + keys(seen) + ")");
				Object[] args = ((TranslatableContents) needs.getContents()).getArgs();
				helper.assertTrue(args.length == 2 && args[1] instanceof Component b && b.getContents() instanceof TranslatableContents t
					&& t.getKey().equals(want.block().getDescriptionId()), want.item() + ": the block named isn't the " + want.block() + ": " + needs);
				String english = QaLuxuryJobsGameTests.english(needs);
				helper.assertTrue(english.startsWith("The " + want.english() + " job needs a ") && !english.contains("%"),
					want.item() + ": \"" + english + "\"");
				helper.assertTrue(said(seen, "message.aliveworkplace.job.chosen") == null, want.item() + ": told a job was taken");
				v.discard();
			}
			// A baby by a loom, sneak-clicked with string: no job (only grown villagers take a trade).
			helper.setBlock(STATION, Blocks.LOOM);
			Villager baby = jobless(helper, STANDING, "Pip");
			baby.setAge(-24000);
			QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.STRING, 2));
			QaLuxuryJobsGameTests.click(player, baby, true);
			helper.assertTrue(job(baby) == VillagerProfession.NONE, "a baby became a " + name(job(baby)));
			helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(helper.absolutePos(STATION)) > 0, "the baby's click took the loom");
			helper.succeed();
		});
	}

	/**
	 * The loom already has its Tailor: a second villager sneak-clicked with string beside it takes no job, the player is
	 * told someone works at each loom here (the words filled in), and the first Tailor keeps the loom.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLuxTrTaken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLuxTrTaken")
	public void aTakenLoomSaysSoAndKeepsItsTailor(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		helper.setBlock(STATION, Blocks.LOOM);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaLuxuryJobsGameTests.listener(helper, seen);
			Villager first = jobless(helper, STANDING, "Wren");
			QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.STRING, 2));
			QaLuxuryJobsGameTests.click(player, first, true);
			helper.assertTrue(job(first) == ModVillagers.TAILOR && worksAt(helper, first, STATION), "the first villager isn't the loom's Tailor: " + name(job(first)));
			Villager second = jobless(helper, BESIDE, "Ash");
			seen.clear();
			QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(Items.STRING, 2));
			QaLuxuryJobsGameTests.click(player, second, true);
			helper.assertTrue(job(second) == VillagerProfession.NONE, "a second villager at a taken loom became a " + name(job(second)));
			Component taken = said(seen, "message.aliveworkplace.job.station_taken");
			helper.assertTrue(taken != null, "the player wasn't told the loom is taken: " + keys(seen));
			String english = QaLuxuryJobsGameTests.english(taken);
			helper.assertTrue(english.startsWith("The Tailor job needs a free ") && english.endsWith("someone already works at each one here.")
				&& !english.contains("%"), "\"" + english + "\"");
			helper.assertTrue(job(first) == ModVillagers.TAILOR && worksAt(helper, first, STATION), "the first Tailor lost the loom");
			helper.succeed();
		});
	}

	record Switch(Block block, Item item, VillagerProfession job, Consumer<Boolean> set) {
	}

	/**
	 * Each job's config switch off ("Off: no Vintner job"): the real sneak-click with its item at its block gives no such
	 * job and leaves the block free; switched back on, the same click hires them.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaLuxTrOff"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaLuxTrOff")
	public void switchedOffTheClickHiresNobodyAndLeavesTheBlockFree(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.after(helper, () -> {
			Vintners.ENABLED = true;
			Tailors.ENABLED = true;
			Printers.ENABLED = true;
			Jewellers.ENABLED = true;
		});
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = QaLuxuryJobsGameTests.listener(helper, seen);
			for (Switch s : List.of(
					new Switch(Blocks.CAULDRON, Items.GLOW_BERRIES, ModVillagers.VINTNER, on -> Vintners.ENABLED = on),
					new Switch(Blocks.LOOM, Items.STRING, ModVillagers.TAILOR, on -> Tailors.ENABLED = on),
					new Switch(Blocks.CARTOGRAPHY_TABLE, Items.INK_SAC, ModVillagers.PRINTER, on -> Printers.ENABLED = on),
					new Switch(Blocks.STONECUTTER, Items.GOLD_NUGGET, ModVillagers.JEWELLER, on -> Jewellers.ENABLED = on))) {
				helper.setBlock(STATION, s.block());
				BlockPos at = helper.absolutePos(STATION);
				s.set().accept(false);
				Villager v = jobless(helper, STANDING, "Wren");
				QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(s.item(), 2));
				seen.clear();
				QaLuxuryJobsGameTests.click(player, v, true);
				helper.assertTrue(job(v) != s.job(), name(s.job()) + " switched off: the click still hired one");
				helper.assertTrue(said(seen, "message.aliveworkplace.job.chosen") == null || job(v) != VillagerProfession.NONE,
					name(s.job()) + " off: told a job was taken while they have none");
				if (job(v) == VillagerProfession.NONE) {
					helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(at) > 0, name(s.job()) + " off: the refused click took the " + s.block());
				}
				v.discard();
				helper.getLevel().getPoiManager().release(at);
				s.set().accept(true);
				Villager w = jobless(helper, STANDING, "Ash");
				QaLuxuryJobsGameTests.hold(player, InteractionHand.MAIN_HAND, new ItemStack(s.item(), 2));
				QaLuxuryJobsGameTests.click(player, w, true);
				helper.assertTrue(job(w) == s.job(), name(s.job()) + " back on: the click gave " + name(job(w)));
				w.discard();
				helper.getLevel().getPoiManager().release(at);
			}
			helper.succeed();
		});
	}

	// ---- the recipes' numbers, from both sides ----

	/** 34.9: Cider is 3 apples and a bottle. 5 apples and 2 bottles make exactly one, leaving 2 apples and a bottle. */
	//$ gametest_ticks_batch AREA '1000' '"qaLuxTrCider"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "qaLuxTrCider")
	public void ciderNeedsAllThreeApples(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.CAULDRON);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(Items.APPLE, 5));
		c.setItem(1, new ItemStack(Items.GLASS_BOTTLE, 2));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.VINTNER, 1);
		QaLuxuryJobsGameTests.run(helper, v, new VintnerWork());
		helper.runAfterDelay(800, () -> {
			int cider = QaLuxuryJobsGameTests.anywhere(helper, v, ModItems.CIDER);
			helper.assertTrue(cider == 1, cider + " Cider from 5 apples, expected 1");
			int apples = QaLuxuryJobsGameTests.anywhere(helper, v, Items.APPLE);
			int bottles = QaLuxuryJobsGameTests.anywhere(helper, v, Items.GLASS_BOTTLE);
			helper.assertTrue(apples == 2 && bottles == 1, apples + " apples and " + bottles + " bottles left, expected 2 and 1");
			helper.succeed();
		});
	}

	/**
	 * 34.9: Berry Wine is "6 sweet berries or 4 glow berries": 5 sweet and 3 glow (8 berries, but neither enough) make
	 * none and are left alone; one more glow berry makes exactly one wine from the 4 glow, the 5 sweet untouched.
	 */
	//$ gametest_ticks_batch AREA '1600' '"qaLuxTrBerries"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaLuxTrBerries")
	public void berryWineIsSixSweetOrFourGlowNeverAMix(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.CAULDRON);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(Items.SWEET_BERRIES, 5));
		c.setItem(1, new ItemStack(Items.GLOW_BERRIES, 3));
		c.setItem(2, new ItemStack(Items.GLASS_BOTTLE, 2));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.VINTNER, 2);
		QaLuxuryJobsGameTests.run(helper, v, new VintnerWork());
		boolean[] added = {false};
		helper.runAfterDelay(500, () -> {
			int wine = QaLuxuryJobsGameTests.anywhere(helper, v, ModItems.BERRY_WINE);
			helper.assertTrue(wine == 0, wine + " Berry Wine from 5 sweet and 3 glow berries");
			helper.assertTrue(QaLuxuryJobsGameTests.anywhere(helper, v, Items.SWEET_BERRIES) == 5
				&& QaLuxuryJobsGameTests.anywhere(helper, v, Items.GLOW_BERRIES) == 3, "berries lost while making nothing");
			c.setItem(3, new ItemStack(Items.GLOW_BERRIES, 1));
			added[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(added[0], "the fourth glow berry isn't in yet");
			helper.assertTrue(c.countItem(ModItems.BERRY_WINE) == 1, c.countItem(ModItems.BERRY_WINE) + " Berry Wine in the chest, expected 1");
			helper.assertTrue(c.countItem(Items.GLOW_BERRIES) == 0 && c.countItem(Items.SWEET_BERRIES) == 5 && c.countItem(Items.GLASS_BOTTLE) == 1,
				c.countItem(Items.GLOW_BERRIES) + " glow, " + c.countItem(Items.SWEET_BERRIES) + " sweet, " + c.countItem(Items.GLASS_BOTTLE)
					+ " bottles left; expected 0, 5, 1");
		});
	}

	/** 34.9/34.5: no glass bottle, no wine: 12 sweet berries stay berries, and the Vintner asks for a bottle on the board. */
	//$ gametest_ticks_batch AREA '800' '"qaLuxTrBottle"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaLuxTrBottle")
	public void noBottleNoWineAndTheBoardAsksForOne(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.CAULDRON);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(Items.SWEET_BERRIES, 12));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.VINTNER, 2);
		QaLuxuryJobsGameTests.run(helper, v, new VintnerWork());
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(QaLuxuryJobsGameTests.anywhere(helper, v, ModItems.BERRY_WINE) == 0, "Berry Wine with no bottle");
			helper.assertTrue(QaLuxuryJobsGameTests.anywhere(helper, v, Items.SWEET_BERRIES) == 12, "berries lost");
			helper.assertTrue(asksFor(helper, v, Items.GLASS_BOTTLE), "no request for a glass bottle: " + asked(helper, v));
			helper.succeed();
		});
	}

	/** 34.10: Work Clothes take 3 wool: 2 wool, 2 leather and a string sew nothing; a third wool (another colour) sews one. */
	//$ gametest_ticks_batch AREA '1600' '"qaLuxTrWool"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaLuxTrWool")
	public void workClothesNeedThreeWool(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.LOOM);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(Items.WHITE_WOOL, 2));
		c.setItem(1, new ItemStack(Items.LEATHER, 2));
		c.setItem(2, new ItemStack(Items.STRING, 1));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.TAILOR, 1);
		QaLuxuryJobsGameTests.run(helper, v, new TailorWork());
		boolean[] added = {false};
		helper.runAfterDelay(500, () -> {
			helper.assertTrue(QaLuxuryJobsGameTests.anywhere(helper, v, ModItems.WORK_CLOTHES) == 0, "Work Clothes from 2 wool");
			helper.assertTrue(QaLuxuryJobsGameTests.anywhere(helper, v, Items.WHITE_WOOL) == 2 && QaLuxuryJobsGameTests.anywhere(helper, v, Items.LEATHER) == 2
				&& QaLuxuryJobsGameTests.anywhere(helper, v, Items.STRING) == 1, "makings lost while sewing nothing");
			c.setItem(3, new ItemStack(Items.CYAN_WOOL, 1));
			added[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(added[0], "the third wool isn't in yet");
			helper.assertTrue(c.countItem(ModItems.WORK_CLOTHES) == 1, c.countItem(ModItems.WORK_CLOTHES) + " Work Clothes, expected 1");
			helper.assertTrue(c.countItem(Items.WHITE_WOOL) == 0 && c.countItem(Items.CYAN_WOOL) == 0 && c.countItem(Items.LEATHER) == 0
				&& c.countItem(Items.STRING) == 0, "makings left over");
		});
	}

	/**
	 * 34.11: "3 paper and an ink sac make 2 copies". 5 paper and 2 ink sacs make exactly 2 Gazettes (2 paper and a sac
	 * left); a sixth paper makes 2 more.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaLuxTrGazette"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaLuxTrGazette")
	public void theGazetteIsThreePaperForTwoCopies(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.CARTOGRAPHY_TABLE);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(Items.PAPER, 5));
		c.setItem(1, new ItemStack(Items.INK_SAC, 2));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.PRINTER, 1);
		QaLuxuryJobsGameTests.run(helper, v, new PrinterWork());
		boolean[] added = {false};
		helper.runAfterDelay(700, () -> {
			int gazettes = QaLuxuryJobsGameTests.anywhere(helper, v, ModItems.GAZETTE);
			helper.assertTrue(gazettes == 2, gazettes + " Gazettes from 5 paper, expected 2");
			int paper = QaLuxuryJobsGameTests.anywhere(helper, v, Items.PAPER);
			int ink = QaLuxuryJobsGameTests.anywhere(helper, v, Items.INK_SAC);
			helper.assertTrue(paper == 2 && ink == 1, paper + " paper and " + ink + " ink sacs left, expected 2 and 1");
			c.setItem(5, new ItemStack(Items.PAPER, 1));
			added[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(added[0], "the sixth paper isn't in yet");
			helper.assertTrue(c.countItem(ModItems.GAZETTE) == 4, c.countItem(ModItems.GAZETTE) + " Gazettes in the chest, expected 4");
			helper.assertTrue(c.countItem(Items.PAPER) == 0 && c.countItem(Items.INK_SAC) == 0, "makings left over");
		});
	}

	/**
	 * 34.11: the Illuminated Book is the Journeyman's. An Apprentice with a book, 2 gold nuggets, lapis and a glow ink sac
	 * makes nothing of them; the same Printer made a Journeyman binds exactly one.
	 */
	//$ gametest_ticks_batch AREA '1800' '"qaLuxTrIllum"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaLuxTrIllum")
	public void anApprenticeIlluminatesNothingAJourneymanDoes(GameTestHelper helper) {
		QaLuxuryJobsGameTests.shop(helper, Blocks.CARTOGRAPHY_TABLE);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		c.setItem(0, new ItemStack(Items.BOOK, 1));
		c.setItem(1, new ItemStack(Items.GOLD_NUGGET, 2));
		c.setItem(2, new ItemStack(Items.LAPIS_LAZULI, 1));
		c.setItem(3, new ItemStack(Items.GLOW_INK_SAC, 1));
		Villager v = QaLuxuryJobsGameTests.worker(helper, ModVillagers.PRINTER, 2);
		QaLuxuryJobsGameTests.run(helper, v, new PrinterWork());
		boolean[] promoted = {false};
		helper.runAfterDelay(600, () -> {
			helper.assertTrue(QaLuxuryJobsGameTests.anywhere(helper, v, ModItems.ILLUMINATED_BOOK) == 0, "an Apprentice made an Illuminated Book");
			helper.assertTrue(QaLuxuryJobsGameTests.anywhere(helper, v, Items.BOOK) == 1 && QaLuxuryJobsGameTests.anywhere(helper, v, Items.GLOW_INK_SAC) == 1,
				"the Apprentice used the Journeyman's makings");
			v.setVillagerData(v.getVillagerData().setLevel(3));
			promoted[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(promoted[0], "not a Journeyman yet");
			helper.assertTrue(c.countItem(ModItems.ILLUMINATED_BOOK) == 1, c.countItem(ModItems.ILLUMINATED_BOOK) + " Illuminated Books, expected 1");
			helper.assertTrue(c.countItem(Items.BOOK) == 0 && c.countItem(Items.GOLD_NUGGET) == 0 && c.countItem(Items.LAPIS_LAZULI) == 0
				&& c.countItem(Items.GLOW_INK_SAC) == 0, "makings left over");
		});
	}

	// ---- save and reload ----

	/**
	 * Saved and loaded mid re-corking: a Journeyman has fetched the one Berry Wine 3 days old when the game saves and
	 * reloads. Afterwards that one is Vintage Wine, and the 2 pressed today are still Berry Wine stamped today: none
	 * lost its day (and none turned Vintage early).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qaLuxTrReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaLuxTrReload")
	public void aReloadMidRecorkingKeepsTheYoungWinesDay(GameTestHelper helper) {
		ServerLevel world = helper.getLevel();
		long dayTime = world.getDayTime();
		Leftovers.after(helper, () -> world.setDayTime(dayTime));
		world.setDayTime(dayTime + 10 * 24000L);
		QaLuxuryJobsGameTests.shop(helper, Blocks.CAULDRON);
		Container c = QaLuxuryJobsGameTests.chest(helper);
		long today = LuxuryRecipes.today(world);
		ItemStack old = new ItemStack(ModItems.BERRY_WINE, 1);
		old.set(ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(today - 3, 3));
		ItemStack young = new ItemStack(ModItems.BERRY_WINE, 2);
		young.set(ModComponents.MADE_DAY, new LuxuryRecipes.MadeDay(today, 3));
		c.setItem(0, old);
		c.setItem(1, young);
		Villager first = QaLuxuryJobsGameTests.worker(helper, ModVillagers.VINTNER, 3);
		QaLuxuryJobsGameTests.run(helper, first, new VintnerWork());
		Villager[] copy = new Villager[1];
		VintnerWork after = new VintnerWork();
		helper.onEachTick(() -> {
			if (copy[0] == null && first.isAlive() && !ModAttachments.BUILDER_BAG.getOrCreate(first).isEmpty()) {
				CompoundTag tag = first.saveWithoutId(new CompoundTag());
				first.discard();
				Villager v = EntityType.VILLAGER.create(world);
				v.load(tag);
				v.setUUID(UUID.randomUUID());
				world.addFreshEntity(v);
				v.setNoAi(true);
				copy[0] = v;
			} else if (copy[0] != null && copy[0].isAlive()) {
				long now = world.getGameTime();
				if (after.getStatus() == Behavior.Status.STOPPED) {
					after.tryStart(world, copy[0], now);
				} else {
					after.tickOrStop(world, copy[0], now);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(copy[0] != null, "the Vintner never fetched anything");
			helper.assertTrue(c.countItem(ModItems.VINTAGE_WINE) == 1, c.countItem(ModItems.VINTAGE_WINE) + " Vintage Wine, expected 1 (bag "
				+ ModAttachments.BUILDER_BAG.getOrCreate(copy[0]).stacks() + ")");
			helper.assertTrue(stamped(c, ModItems.BERRY_WINE, today) == 2 && c.countItem(ModItems.BERRY_WINE) == 2,
				c.countItem(ModItems.BERRY_WINE) + " Berry Wine in the chest, " + stamped(c, ModItems.BERRY_WINE, today) + " stamped today; expected 2 and 2");
		});
	}

	// ---- the words, the data and the trades ----

	/**
	 * 34.5's tooltip on its edges: on the day it was pressed "vintage in 3 days", on the third day "· vintage"; a good
	 * that never ages says only when it was made. Only goods some recipe ages are stamped (Berry Wine, not Cider or
	 * Vintage Wine), a stamp is never moved to a later day, and the stamp survives a save and load of the stack.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theMadeDayOnItsFirstAndLastDaysAndThroughASave(GameTestHelper helper) {
		LuxuryRecipes.MadeDay made = new LuxuryRecipes.MadeDay(42, 3);
		String first = QaLuxuryJobsGameTests.english(LuxuryRecipes.tooltip(made, 42));
		helper.assertTrue(first.equals("Pressed on day 42 · vintage in 3 days"), "on its day: \"" + first + "\"");
		String last = QaLuxuryJobsGameTests.english(LuxuryRecipes.tooltip(made, 45));
		helper.assertTrue(last.equals("Pressed on day 42 · vintage"), "3 days on: \"" + last + "\"");
		String plain = QaLuxuryJobsGameTests.english(LuxuryRecipes.tooltip(new LuxuryRecipes.MadeDay(7, 0), 9));
		helper.assertTrue(plain.equals("Made on day 7"), "a good that doesn't age: \"" + plain + "\"");

		helper.assertTrue(!LuxuryRecipes.stamp(new ItemStack(ModItems.CIDER), 10).has(ModComponents.MADE_DAY), "Cider was stamped: nothing ages it");
		helper.assertTrue(!LuxuryRecipes.stamp(new ItemStack(ModItems.VINTAGE_WINE), 10).has(ModComponents.MADE_DAY), "Vintage Wine was stamped");
		ItemStack wine = LuxuryRecipes.stamp(new ItemStack(ModItems.BERRY_WINE, 3), 10);
		helper.assertTrue(new LuxuryRecipes.MadeDay(10, 3).equals(wine.get(ModComponents.MADE_DAY)), "Berry Wine stamped " + wine.get(ModComponents.MADE_DAY));
		LuxuryRecipes.stamp(wine, 14);
		helper.assertTrue(wine.get(ModComponents.MADE_DAY).day() == 10, "re-stamping moved the day to " + wine.get(ModComponents.MADE_DAY));

		var registries = helper.getLevel().registryAccess();
		Tag saved = wine.save(registries);
		ItemStack loaded = ItemStack.parse(registries, saved).orElseThrow();
		helper.assertTrue(loaded.is(ModItems.BERRY_WINE) && loaded.getCount() == 3 && new LuxuryRecipes.MadeDay(10, 3).equals(loaded.get(ModComponents.MADE_DAY)),
			"after a save: " + loaded + " " + loaded.get(ModComponents.MADE_DAY));
		helper.succeed();
	}

	/**
	 * 34.8: "whoever makes a luxury a class needs is open to the class below it". For every luxury each class's file
	 * needs, some luxury recipe makes it, and the job of each such recipe is open to the class one below (else nobody
	 * could ever rise into that class).
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyNeededLuxuryIsMadeByAJobTheClassBelowMayTake(GameTestHelper helper) {
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(helper.getLevel().getServer().getResourceManager()));
		ClassPerks.forget();
		Leftovers.after(helper, () -> {
			new WorkplaceConfig().apply();
			ClassPerks.forget();
		});
		List<String> checked = new ArrayList<>();
		for (SocialClasses.SocialClass c : SocialClasses.ladder()) {
			for (ClassNeeds.Need need : c.needs()) {
				if (!(need instanceof ClassNeeds.Luxury l)) {
					continue;
				}
				Luxuries.Luxury luxury = Luxuries.get(l.id());
				if (luxury == null || luxury.item().isEmpty()) {
					continue; // an optional mod's luxury, not loaded here
				}
				Item item = BuiltInRegistries.ITEM.get(luxury.item().get());
				List<LuxuryRecipes.Recipe> makers = LuxuryRecipes.making(item);
				helper.assertTrue(!makers.isEmpty(), c.id() + " needs " + l.id() + " but no luxury recipe makes it");
				for (LuxuryRecipes.Recipe r : makers) {
					SocialClasses.SocialClass gate = ClassJobs.needed(r.job());
					if (gate != null && gate.tier() >= c.tier()) {
						helper.fail(c.id() + " needs " + l.id() + ", made by " + r.job() + ", which only a " + gate.id() + " or better may take: nobody below "
							+ c.id() + " can make it");
					}
					checked.add(c.id().getPath() + ":" + l.id().getPath() + "<-" + r.job().getPath());
				}
			}
		}
		helper.assertTrue(checked.size() >= 7, "only checked " + checked);
		helper.succeed();
	}

	/**
	 * 34.9: "Trades at every level (buys apples, berries and bottles; sells the wines)": each of the five levels buys one
	 * of those makings for emeralds and sells one of the three drinks for emeralds; over the levels apples, berries and
	 * bottles are all bought and all three drinks sold.
	 */
	//$ gametest_batch AREA '"qaLuxTrTrades"'
	@GameTest(template = AREA, batch = "qaLuxTrTrades")
	public void theVintnerTradesAtEveryLevel(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		v.setNoAi(true);
		var byLevel = VillagerTrades.TRADES.get(ModVillagers.VINTNER);
		helper.assertTrue(byLevel != null, "the Vintner has no trades");
		Set<Item> makings = Set.of(Items.APPLE, Items.SWEET_BERRIES, Items.GLOW_BERRIES, Items.GLASS_BOTTLE);
		Set<Item> drinks = Set.of(ModItems.CIDER, ModItems.BERRY_WINE, ModItems.VINTAGE_WINE);
		java.util.Set<Item> bought = new java.util.HashSet<>();
		java.util.Set<Item> sold = new java.util.HashSet<>();
		for (int level = 1; level <= 5; level++) {
			VillagerTrades.ItemListing[] listings = byLevel.get(level);
			helper.assertTrue(listings != null && listings.length > 0, "level " + level + " has no trades");
			boolean buys = false;
			boolean sells = false;
			for (VillagerTrades.ItemListing listing : listings) {
				MerchantOffer offer = listing.getOffer(v, RandomSource.create(7));
				if (offer == null) {
					continue;
				}
				ItemStack cost = offer.getBaseCostA();
				if (offer.getResult().is(Items.EMERALD) && makings.contains(cost.getItem())) {
					buys = true;
					bought.add(cost.getItem() == Items.GLOW_BERRIES ? Items.SWEET_BERRIES : cost.getItem());
				}
				if (cost.is(Items.EMERALD) && drinks.contains(offer.getResult().getItem())) {
					sells = true;
					sold.add(offer.getResult().getItem());
				}
			}
			helper.assertTrue(buys && sells, "level " + level + ": buys a making " + buys + ", sells a drink " + sells);
		}
		helper.assertTrue(bought.containsAll(Set.of(Items.APPLE, Items.SWEET_BERRIES, Items.GLASS_BOTTLE)), "makings bought over the levels: " + bought);
		helper.assertTrue(sold.containsAll(drinks), "drinks sold over the levels: " + sold);
		v.discard();
		helper.succeed();
	}

	static ResourceLocation id(String path) {
		return AliveWorkplace.id(path);
	}
}

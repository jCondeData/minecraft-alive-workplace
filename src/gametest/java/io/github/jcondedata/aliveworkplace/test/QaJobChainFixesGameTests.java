package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1024-1537) on the two job-chain fixes, written from their ROADMAP entries: B352 (a Mason a coal short names
 * the coal and takes nothing; a coal for every eight fired) and B353 (a Carpenter's Bench broken mid-chain lets the job
 * go and loses no wood). Helpers are QaCrafterFurnitureGameTests's.
 */
public class QaJobChainFixesGameTests {
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	static final String AREA = QaCrafterFurnitureGameTests.AREA;
	static final BlockPos BENCH = QaCrafterFurnitureGameTests.BENCH;
	static final BlockPos CHEST = QaCrafterFurnitureGameTests.CHEST;
	private static final BlockPos STATION = QaCrafterFurnitureGameTests.STATION;
	static final BlockPos ORIGIN = new BlockPos(6, 2, 9);
	/** Six oak logs in half-planks: a log 8, a plank 2, a slab 1, a barrel 14 (six planks and two slabs). */
	private static final int WOOD = 6 * 8;

	// --- B352: the kiln's fuel --------------------------------------------------------------------------------------

	/**
	 * The boundary from the enough side: twelve clay and exactly two coal (a coal for every eight fired), the coal in two
	 * separate stacks of one: the Mason goes ahead, the three brick stairs are built, the clay and coal are used.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFixMasonExactCoal"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFixMasonExactCoal")
	public void aMasonWithExactlyEnoughCoalInTwoStacksMakesTheStairs(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BRICK_STAIRS,
			new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 1), new ItemStack(Items.COAL, 1));
		Villager mason = mason(helper);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 0 && chest.countItem(Items.COAL) == 0, "left in the chest: "
				+ chest.countItem(Items.CLAY_BALL) + " clay, " + chest.countItem(Items.COAL) + " coal");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == 3, "the mason made " + ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0));
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(mason) == null, "the finished job is still saved on the mason");
		});
	}

	/**
	 * The boundary from the short side, at zero: twelve clay and no coal at all. The line is the en_us.json sentence with
	 * "2 Coal" in it, compared character for character, and nothing is taken or fired.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaFixMasonNoCoal"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFixMasonNoCoal")
	public void aMasonWithNoCoalNamesTwoCoalFromTheLangFile(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BRICK_STAIRS,
			new ItemStack(Items.CLAY_BALL, 12));
		Villager mason = mason(helper);
		helper.runAfterDelay(700, () -> {
			JsonObject lang = lang();
			String expected = String.format(lang.get("message.aliveworkplace.job_chain.cant_make").getAsString(),
				String.format(lang.get("message.aliveworkplace.job_chain.lacks").getAsString(), "2 Coal", "3", "Brick Stairs"));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals(expected), "the builder's line with twelve clay and no coal: '"
				+ (line == null ? null : line.getString()) + "', en_us.json renders '" + expected + "'");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 12, "the mason took clay: " + chest.countItem(Items.CLAY_BALL) + " left");
			assertNothingTaken(helper, a, mason);
			helper.succeed();
		});
	}

	/**
	 * Short of clay AND coal (eight clay, no coal, for three brick stairs that fire twelve): nothing is taken, and the line
	 * names something that's really missing (the clay, the coal, or the brick blocks that would skip the kiln), never a
	 * raw key or an empty list.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaFixMasonBothShort"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFixMasonBothShort")
	public void aMasonShortOfClayAndCoalTakesNothingAndNamesWhatsMissing(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BRICK_STAIRS,
			new ItemStack(Items.CLAY_BALL, 8));
		Villager mason = mason(helper);
		helper.runAfterDelay(700, () -> {
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			String text = line == null ? "" : line.getString();
			String head = "Can't be made yet: short of ";
			String tail = " to make 3 Brick Stairs";
			helper.assertTrue(text.startsWith(head) && text.endsWith(tail), "the builder's line with eight clay and no coal: '" + text + "'");
			String what = text.substring(head.length(), text.length() - tail.length());
			helper.assertTrue(what.contains("Clay Ball") || what.contains("Coal") || what.matches("3 Bricks"),
				"the line names something that isn't what's missing: '" + what + "'");
			helper.assertTrue(!what.contains("%") && !what.contains("aliveworkplace."), "a raw key or placeholder in the line: '" + text + "'");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 8, "the mason took clay: " + chest.countItem(Items.CLAY_BALL) + " left");
			assertNothingTaken(helper, a, mason);
			LOG.info("both short: '" + text + "'");
			helper.succeed();
		});
	}

	// --- B353: the Carpenter's Bench broken mid-chain -----------------------------------------------------------------

	/**
	 * The bench broken at the chain's first step (the logs fetched into the bag, before or while they're sawn; the fix's
	 * test breaks it at the second): the job is let go, the bag is emptied, and every log's worth of wood is in the chest,
	 * a bag or on the ground (counted in half-planks: 48 before, 48 after).
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFixBenchBrokenFirstStep"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFixBenchBrokenFirstStep")
	public void theCarpentersBenchBrokenAtTheFirstStepLosesNoWood(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, STATION);
		AtomicInteger since = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			if (since.get() < 0 && job != null && job.phase() == CrafterJob.Phase.CRAFTING && job.step() == 0) {
				Container chest = helper.getBlockEntity(a.chest());
				helper.assertTrue(chest.countItem(Items.OAK_LOG) == 0, "the logs weren't all fetched before the first step");
				helper.setBlock(STATION, Blocks.AIR); // the player breaks the bench
				since.set(0);
			} else if (since.get() >= 0) {
				since.incrementAndGet();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(since.get() >= 0, "the carpenter never got to the chain's first step");
			helper.assertTrue(since.get() > 1200, "giving the carpenter a minute without a bench");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(carpenter) == null, "a job is still saved on the carpenter with no bench: " + ModAttachments.CRAFTER_JOB.get(carpenter));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty(), "still in the bag: " + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).stacks());
			int wood = wood(helper, a, carpenter);
			helper.assertTrue(wood == WOOD, "wood before the bench was broken: " + WOOD + " half-planks, after: " + wood + " (" + woodDetail(helper, a, carpenter) + ")");
		});
	}

	/**
	 * Saved and loaded mid-chain (at the second step or later, the bench intact, as a chunk unload would): the chain
	 * resumes and the three barrels are built, made once, with the spare planks back in the chest and no wood doubled.
	 */
	//$ gametest_ticks_batch AREA '4000' '"qaFixReloadMidChain"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaFixReloadMidChain")
	public void aCarpenterReloadedMidChainFinishesWithNothingDoubled(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		AtomicReference<Villager> carpenter = new AtomicReference<>(QaCrafterFurnitureGameTests.carpenter(helper, STATION));
		AtomicInteger reloadedAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			if (reloadedAt.get() >= 0) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter.get());
			if (job != null && job.phase() == CrafterJob.Phase.CRAFTING && job.step() >= 1) {
				int before = wood(helper, a, carpenter.get());
				Villager copy = QaCrafterFurnitureGameTests.reload(helper.getLevel(), carpenter.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.phase() == CrafterJob.Phase.CRAFTING && after.step() == job.step(),
					"the job mid-chain (step " + (job.step() + 1) + ") after a reload: " + after);
				int then = wood(helper, a, copy);
				helper.assertTrue(then == before, "wood across the reload: " + before + " half-planks before, " + then + " after");
				carpenter.set(copy);
				reloadedAt.set((int) helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt.get() >= 0, "the carpenter never got to the chain's second step");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Villager c = carpenter.get();
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(c) == null, "the finished job is still saved");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(c).isEmpty(), "left in the bag: " + ModAttachments.BUILDER_BAG.getOrCreate(c).stacks());
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(c, 0) == 3, "the carpenter's count says " + ModAttachments.ITEMS_CRAFTED.getOrElse(c, 0) + " made");
			int wood = wood(helper, a, c);
			helper.assertTrue(wood == WOOD, "wood: " + WOOD + " half-planks of logs, " + wood + " after the build (" + woodDetail(helper, a, c) + ")");
		});
	}

	/**
	 * The bench broken at the chain's last step, the job let go (bag emptied), then a bench put back: whatever is made
	 * after that comes from the returned wood, so there are never more than three barrels and the wood adds up to the six
	 * logs exactly (nothing duplicated from a job that was already let go).
	 */
	//$ gametest_ticks_batch AREA '6000' '"qaFixBenchPutBack"'
	@GameTest(template = AREA, timeoutTicks = 6000, batch = "qaFixBenchPutBack")
	public void aBenchPutBackAfterTheJobWasLetGoDuplicatesNothing(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, BENCH, CHEST, ORIGIN, Blocks.BARREL, new ItemStack(Items.OAK_LOG, 6));
		Villager carpenter = QaCrafterFurnitureGameTests.carpenter(helper, STATION);
		AtomicInteger stage = new AtomicInteger(); // 0 working, 1 bench broken, 2 job let go and bench back
		AtomicInteger since = new AtomicInteger();
		helper.onEachTick(() -> {
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(carpenter);
			if (stage.get() == 0 && job != null && job.phase() == CrafterJob.Phase.CRAFTING && job.step() >= 2) {
				helper.setBlock(STATION, Blocks.AIR);
				stage.set(1);
			} else if (stage.get() == 1 && job == null && ModAttachments.BUILDER_BAG.getOrCreate(carpenter).isEmpty()) {
				int let = wood(helper, a, carpenter);
				helper.assertTrue(let == WOOD, "wood when the job was let go: " + let + " half-planks of " + WOOD + " (" + woodDetail(helper, a, carpenter) + ")");
				helper.setBlock(STATION, ModBlocks.CARPENTERS_BENCH); // the player puts a bench back
				stage.set(2);
			} else if (stage.get() == 2) {
				since.incrementAndGet();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(stage.get() >= 1, "the carpenter never got to the chain's last step");
			helper.assertTrue(stage.get() == 2, "the job was never let go after the bench was broken: " + ModAttachments.CRAFTER_JOB.get(carpenter));
			helper.assertTrue(since.get() > 1500, "giving the bench back time to be used");
			int barrels = barrels(helper, a, carpenter);
			helper.assertTrue(barrels <= 3, barrels + " barrels for a builder short of three");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) <= 3, "the carpenter's count says " + ModAttachments.ITEMS_CRAFTED.getOrElse(carpenter, 0) + " made");
			int wood = wood(helper, a, carpenter);
			helper.assertTrue(wood == WOOD, "wood: " + WOOD + " half-planks of logs, " + wood + " after the bench came back (" + woodDetail(helper, a, carpenter) + ")");
			LOG.info("bench put back: " + woodDetail(helper, a, carpenter));
		});
	}

	// --- helpers ------------------------------------------------------------------------------------------------------

	static Villager mason(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.STONECUTTER);
		helper.setBlock(STATION.east(), Blocks.FURNACE);
		Villager mason = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), mason, helper.absolutePos(STATION), PoiTypes.MASON, VillagerProfession.MASON);
		return mason;
	}

	private static void assertNothingTaken(GameTestHelper helper, QaCrafterFurnitureGameTests.Asker a, Villager mason) {
		Container chest = helper.getBlockEntity(a.chest());
		helper.assertTrue(chest.countItem(Items.BRICK) == 0 && chest.countItem(Items.BRICKS) == 0 && QaCrafterFurnitureGameTests.loose(helper, Items.BRICK) == 0
			&& QaCrafterFurnitureGameTests.loose(helper, Items.CLAY_BALL) == 0, "bricks were fired or clay dropped");
		helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(mason).isEmpty(), "the mason carries " + ModAttachments.BUILDER_BAG.getOrCreate(mason).stacks());
		helper.assertTrue(ModAttachments.CRAFTER_JOB.get(mason) == null, "the mason took a job it can't finish: " + ModAttachments.CRAFTER_JOB.get(mason));
	}

	private static JsonObject lang() {
		try (InputStream in = QaJobChainFixesGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			if (in == null) {
				throw new GameTestAssertException("en_us.json isn't on the classpath");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("en_us.json: " + e);
		}
	}

	/** An item anywhere a player could get it back: the builder's chest, either villager's bag, or on the ground. */
	private static int count(GameTestHelper helper, QaCrafterFurnitureGameTests.Asker a, Villager carpenter, Item item) {
		Container chest = helper.getBlockEntity(a.chest());
		return chest.countItem(item) + ModAttachments.BUILDER_BAG.getOrCreate(carpenter).count(item)
			+ ModAttachments.BUILDER_BAG.getOrCreate(a.builder()).count(item) + QaCrafterFurnitureGameTests.loose(helper, item);
	}

	/** Barrels anywhere, the ones built into the row too. */
	private static int barrels(GameTestHelper helper, QaCrafterFurnitureGameTests.Asker a, Villager carpenter) {
		int placed = 0;
		for (int x = 0; x < 3; x++) {
			if (helper.getBlockState(ORIGIN.offset(x, 0, 0)).is(Blocks.BARREL)) {
				placed++;
			}
		}
		return placed + count(helper, a, carpenter, Items.BARREL);
	}

	private static int wood(GameTestHelper helper, QaCrafterFurnitureGameTests.Asker a, Villager carpenter) {
		return 8 * count(helper, a, carpenter, Items.OAK_LOG) + 2 * count(helper, a, carpenter, Items.OAK_PLANKS)
			+ count(helper, a, carpenter, Items.OAK_SLAB) + 14 * barrels(helper, a, carpenter);
	}

	private static String woodDetail(GameTestHelper helper, QaCrafterFurnitureGameTests.Asker a, Villager carpenter) {
		return count(helper, a, carpenter, Items.OAK_LOG) + " logs, " + count(helper, a, carpenter, Items.OAK_PLANKS) + " planks, "
			+ count(helper, a, carpenter, Items.OAK_SLAB) + " slabs, " + barrels(helper, a, carpenter) + " barrels";
	}
}

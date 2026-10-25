package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1025-0937) on B396 from its text and fix commit: "both fuels count together" and "coal first and charcoal for
 * the rest, from what's spare of both; glass uses it too". The fixer's tests cover charcoal only, one coal short and a
 * mixed job reloaded; these add what a mix takes (only what's needed, coal first, the rest left in the chest), a chest
 * with no fuel at all (the line counts every coal missing), and the glass path (sand and crushed cobblestone) on
 * charcoal. Helpers are QaJobChainFixesGameTests's and QaCrafterFurnitureGameTests's.
 */
public class QaMixedFuelGameTests {
	private static final String AREA = QaJobChainFixesGameTests.AREA;

	private static QaCrafterFurnitureGameTests.Asker asker(GameTestHelper helper, net.minecraft.world.level.block.Block block, ItemStack... items) {
		QaCrafterFurnitureGameTests.prepare(helper);
		return QaCrafterFurnitureGameTests.asker(helper, QaJobChainFixesGameTests.BENCH, QaJobChainFixesGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, block, items);
	}

	/**
	 * Twelve clay (two fuels' worth of firing), one coal and five charcoal: the coal and one charcoal are burnt, the stairs
	 * are built, and the four charcoal not needed stay in the chest.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFuelOnlyWhatsNeeded"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFuelOnlyWhatsNeeded")
	public void aMixTakesOnlyTheFuelItNeedsCoalFirst(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.Asker a = asker(helper, Blocks.BRICK_STAIRS, new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 1),
			new ItemStack(Items.CHARCOAL, 5));
		Villager mason = QaJobChainFixesGameTests.mason(helper);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.COAL) == 0 && chest.countItem(Items.CHARCOAL) == 4 && chest.countItem(Items.CLAY_BALL) == 0,
				"left in the chest: " + chest.countItem(Items.CLAY_BALL) + " clay, " + chest.countItem(Items.COAL) + " coal, "
					+ chest.countItem(Items.CHARCOAL) + " charcoal (expected 0, 0, 4)");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(mason).isEmpty(), "left in the bag: " + ModAttachments.BUILDER_BAG.getOrCreate(mason).stacks());
		});
	}

	/** Twelve clay and three coal: two coal are burnt and the third stays (enough of one kind takes no more than needed). */
	//$ gametest_ticks_batch AREA '3600' '"qaFuelSpareCoalStays"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFuelSpareCoalStays")
	public void aSpareCoalStaysInTheChest(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.Asker a = asker(helper, Blocks.BRICK_STAIRS, new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 3),
			new ItemStack(Items.CHARCOAL, 2));
		QaJobChainFixesGameTests.mason(helper);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.COAL) == 1 && chest.countItem(Items.CHARCOAL) == 2,
				"left in the chest: " + chest.countItem(Items.COAL) + " coal, " + chest.countItem(Items.CHARCOAL) + " charcoal (expected 1 and 2)");
		});
	}

	/**
	 * Twelve clay and no fuel at all: the builder's line is the en_us.json sentence naming "2 Coal" (both fuels missing
	 * are counted), and the clay stays where it is.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaFuelNone"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFuelNone")
	public void withNoFuelTheLineCountsTwoCoal(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.Asker a = asker(helper, Blocks.BRICK_STAIRS, new ItemStack(Items.CLAY_BALL, 12));
		Villager mason = QaJobChainFixesGameTests.mason(helper);
		helper.runAfterDelay(700, () -> {
			JsonObject lang = lang();
			String expected = String.format(lang.get("message.aliveworkplace.job_chain.cant_make").getAsString(),
				String.format(lang.get("message.aliveworkplace.job_chain.lacks").getAsString(), "2 Coal", "3", "Brick Stairs"));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals(expected), "the builder's line with twelve clay and no fuel: '"
				+ (line == null ? null : line.getString()) + "', expected '" + expected + "'");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 12, "clay left: " + chest.countItem(Items.CLAY_BALL));
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(mason) == null, "the mason took a job it can't finish: " + ModAttachments.CRAFTER_JOB.get(mason));
			helper.succeed();
		});
	}

	/** "Glass uses it too": three sand and one charcoal (no coal) by a Mason with a furnace: the glass row is built. */
	//$ gametest_ticks_batch AREA '3600' '"qaFuelGlassCharcoal"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFuelGlassCharcoal")
	public void glassIsFiredOnCharcoal(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.Asker a = asker(helper, Blocks.GLASS, new ItemStack(Items.SAND, 3), new ItemStack(Items.CHARCOAL, 1));
		QaJobChainFixesGameTests.mason(helper);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == 0 && chest.countItem(Items.CHARCOAL) == 0,
				"left in the chest: " + chest.countItem(Items.SAND) + " sand, " + chest.countItem(Items.CHARCOAL) + " charcoal");
		});
	}

	/**
	 * Three sand, one coal and one charcoal: one fuel fires three glass, so the coal is burnt (coal first) and the charcoal
	 * stays in the chest.
	 */
	//$ gametest_ticks_batch AREA '3600' '"qaFuelGlassCoalFirst"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFuelGlassCoalFirst")
	public void glassBurnsTheCoalAndLeavesTheCharcoal(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.Asker a = asker(helper, Blocks.GLASS, new ItemStack(Items.SAND, 3), new ItemStack(Items.COAL, 1),
			new ItemStack(Items.CHARCOAL, 1));
		QaJobChainFixesGameTests.mason(helper);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.COAL) == 0 && chest.countItem(Items.CHARCOAL) == 1,
				"left in the chest: " + chest.countItem(Items.COAL) + " coal, " + chest.countItem(Items.CHARCOAL) + " charcoal (expected 0 and 1)");
		});
	}

	/**
	 * Glass all the way from cobblestone (crushed to gravel, then sand, then fired) on charcoal: three cobblestone and one
	 * charcoal make the glass row, and nothing is left over.
	 */
	//$ gametest_ticks_batch AREA '4800' '"qaFuelGlassFromCobble"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "qaFuelGlassFromCobble")
	public void glassFromCobblestoneOnCharcoal(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.Asker a = asker(helper, Blocks.GLASS, new ItemStack(Items.COBBLESTONE, 3), new ItemStack(Items.CHARCOAL, 1));
		Villager mason = QaJobChainFixesGameTests.mason(helper);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.COBBLESTONE) == 0 && chest.countItem(Items.CHARCOAL) == 0 && chest.countItem(Items.GRAVEL) == 0
				&& chest.countItem(Items.SAND) == 0, "left in the chest: " + chest.countItem(Items.COBBLESTONE) + " cobblestone, "
				+ chest.countItem(Items.GRAVEL) + " gravel, " + chest.countItem(Items.SAND) + " sand, " + chest.countItem(Items.CHARCOAL) + " charcoal");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(mason).isEmpty(), "left in the bag: " + ModAttachments.BUILDER_BAG.getOrCreate(mason).stacks());
		});
	}

	/**
	 * Three sand and no fuel, a Mason with a furnace there: the builder's line names the missing coal ("short of 1 Coal to
	 * make 3 Glass", as the kiln's does since B352), not "Nobody here makes", and the sand stays.
	 */
	//$ gametest_ticks_batch AREA '1200' '"qaFuelGlassNone"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "qaFuelGlassNone")
	public void glassWithNoFuelNamesTheCoal(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.Asker a = asker(helper, Blocks.GLASS, new ItemStack(Items.SAND, 3));
		Villager mason = QaJobChainFixesGameTests.mason(helper);
		helper.runAfterDelay(700, () -> {
			JsonObject lang = lang();
			String expected = String.format(lang.get("message.aliveworkplace.job_chain.cant_make").getAsString(),
				String.format(lang.get("message.aliveworkplace.job_chain.lacks").getAsString(), "1 Coal", "3", "Glass"));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals(expected), "the builder's line with three sand and no fuel: '"
				+ (line == null ? null : line.getString()) + "', expected '" + expected + "'");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.SAND) == 3, "sand left: " + chest.countItem(Items.SAND));
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(mason) == null, "the mason took a job it can't finish: " + ModAttachments.CRAFTER_JOB.get(mason));
			helper.succeed();
		});
	}

	/**
	 * The wiki (crafter.md): "The same goes for a Tinkerer's forge." Three cauldrons (twenty-one iron) from twenty-one raw
	 * iron, fired in the forge at eight per fuel (three fuels), with two coal and one charcoal: neither kind alone is
	 * enough, both are burnt, the cauldrons are built and no ore or ingot is left over.
	 */
	//$ gametest_ticks_batch AREA '4800' '"qaFuelTinkererMixed"'
	@GameTest(template = AREA, timeoutTicks = 4800, batch = "qaFuelTinkererMixed")
	public void aTinkerersForgeBurnsCoalAndCharcoalTogether(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.Asker a = asker(helper, Blocks.CAULDRON, new ItemStack(Items.RAW_IRON, 21), new ItemStack(Items.COAL, 2),
			new ItemStack(Items.CHARCOAL, 1));
		helper.setBlock(TINKER, io.github.jcondedata.aliveworkplace.registry.ModBlocks.TINKERS_BENCH);
		Villager tinkerer = helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, TINKER.south());
		io.github.jcondedata.aliveworkplace.work.Jobs.employ(helper.getLevel(), tinkerer, helper.absolutePos(TINKER),
			io.github.jcondedata.aliveworkplace.registry.ModVillagers.TINKERS_BENCH_POI, io.github.jcondedata.aliveworkplace.registry.ModVillagers.TINKERER);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.RAW_IRON) == 0 && chest.countItem(Items.IRON_INGOT) == 0 && chest.countItem(Items.COAL) == 0
				&& chest.countItem(Items.CHARCOAL) == 0, "left in the chest: " + chest.countItem(Items.RAW_IRON) + " raw iron, "
				+ chest.countItem(Items.IRON_INGOT) + " ingots, " + chest.countItem(Items.COAL) + " coal, " + chest.countItem(Items.CHARCOAL) + " charcoal");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(tinkerer) == null, "the finished job is still saved on the tinkerer");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).isEmpty(), "left in the bag: " + ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).stacks());
		});
	}

	private static final net.minecraft.core.BlockPos TINKER = new net.minecraft.core.BlockPos(14, 2, 2);

	private static JsonObject lang() {
		try (InputStream in = QaMixedFuelGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			if (in == null) {
				throw new GameTestAssertException("en_us.json isn't on the classpath");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("en_us.json: " + e);
		}
	}
}

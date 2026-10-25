package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.craft.CrafterJob;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
 * B396: the kiln's fuel is coal and charcoal together (a fuel for every eight fired). Charcoal alone still works, a real
 * shortage still counts both kinds, and a mixed-fuel job saved and loaded mid-way finishes with both burnt. The bug's own
 * test is {@link QaJobChainFixesBugTests}; helpers are QaJobChainFixesGameTests's and QaCrafterFurnitureGameTests's.
 */
public class MasonMixedFuelGameTests {
	private static final String AREA = QaJobChainFixesGameTests.AREA;

	/** Twelve clay and exactly two charcoal (no coal): the stairs are built and the charcoal is used. */
	//$ gametest_ticks_batch AREA '3600' '"b396CharcoalOnly"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "b396CharcoalOnly")
	public void aMasonWithTwoCharcoalMakesTheStairs(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaJobChainFixesGameTests.BENCH, QaJobChainFixesGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.BRICK_STAIRS, new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.CHARCOAL, 2));
		Villager mason = QaJobChainFixesGameTests.mason(helper);
		helper.succeedWhen(() -> {
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 0 && chest.countItem(Items.CHARCOAL) == 0, "left in the chest: "
				+ chest.countItem(Items.CLAY_BALL) + " clay, " + chest.countItem(Items.CHARCOAL) + " charcoal");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0) == 3, "the mason made " + ModAttachments.ITEMS_CRAFTED.getOrElse(mason, 0));
		});
	}

	/**
	 * Twelve clay and one coal (two fuels needed): the line is the en_us.json sentence with "1 Coal", and nothing is taken.
	 * With one coal and one charcoal it would be enough, so the count must be of both kinds together, not the larger.
	 */
	//$ gametest_ticks_batch AREA '1200' '"b396OneCoalShort"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "b396OneCoalShort")
	public void aMasonWithOneCoalForTwelveClayIsShortOfOneCoal(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaJobChainFixesGameTests.BENCH, QaJobChainFixesGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.BRICK_STAIRS, new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 1));
		Villager mason = QaJobChainFixesGameTests.mason(helper);
		helper.runAfterDelay(700, () -> {
			JsonObject lang = lang();
			String expected = String.format(lang.get("message.aliveworkplace.job_chain.cant_make").getAsString(),
				String.format(lang.get("message.aliveworkplace.job_chain.lacks").getAsString(), "1 Coal", "3", "Brick Stairs"));
			Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
			helper.assertTrue(line != null && line.getString().equals(expected), "the builder's line with twelve clay and one coal: '"
				+ (line == null ? null : line.getString()) + "', expected '" + expected + "'");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.CLAY_BALL) == 12 && chest.countItem(Items.COAL) == 1, "the mason took some: "
				+ chest.countItem(Items.CLAY_BALL) + " clay, " + chest.countItem(Items.COAL) + " coal left");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(mason) == null, "the mason took a job it can't finish: " + ModAttachments.CRAFTER_JOB.get(mason));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(mason).isEmpty(), "the mason carries " + ModAttachments.BUILDER_BAG.getOrCreate(mason).stacks());
			helper.succeed();
		});
	}

	/**
	 * One coal and one charcoal, the mason saved and loaded mid-job (as a chunk unload would): the saved job still takes
	 * both fuels, the stairs are built once and both the coal and the charcoal are burnt, none left in the bag.
	 */
	//$ gametest_ticks_batch AREA '4000' '"b396MixedReload"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "b396MixedReload")
	public void aMixedFuelJobReloadedMidWayFinishes(GameTestHelper helper) {
		QaCrafterFurnitureGameTests.prepare(helper);
		QaCrafterFurnitureGameTests.Asker a = QaCrafterFurnitureGameTests.asker(helper, QaJobChainFixesGameTests.BENCH, QaJobChainFixesGameTests.CHEST,
			QaJobChainFixesGameTests.ORIGIN, Blocks.BRICK_STAIRS, new ItemStack(Items.CLAY_BALL, 12), new ItemStack(Items.COAL, 1), new ItemStack(Items.CHARCOAL, 1));
		AtomicReference<Villager> mason = new AtomicReference<>(QaJobChainFixesGameTests.mason(helper));
		AtomicInteger reloadedAt = new AtomicInteger(-1);
		helper.onEachTick(() -> {
			if (reloadedAt.get() >= 0) {
				return;
			}
			CrafterJob job = ModAttachments.CRAFTER_JOB.get(mason.get());
			if (job != null && job.phase() == CrafterJob.Phase.CRAFTING) {
				Villager copy = QaCrafterFurnitureGameTests.reload(helper.getLevel(), mason.get());
				CrafterJob after = ModAttachments.CRAFTER_JOB.get(copy);
				helper.assertTrue(after != null && after.phase() == CrafterJob.Phase.CRAFTING && after.step() == job.step(), "the job after a reload: " + after);
				helper.assertTrue(after.takes().getOrDefault(Items.COAL, 0) == 1 && after.takes().getOrDefault(Items.CHARCOAL, 0) == 1,
					"the saved job's fuel: " + after.takes());
				mason.set(copy);
				reloadedAt.set((int) helper.getTick());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt.get() >= 0, "the mason never started the job");
			QaCrafterFurnitureGameTests.assertBuilt(helper, a);
			Villager m = mason.get();
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.COAL) == 0 && chest.countItem(Items.CHARCOAL) == 0 && chest.countItem(Items.CLAY_BALL) == 0,
				"left in the chest: " + chest.countItem(Items.CLAY_BALL) + " clay, " + chest.countItem(Items.COAL) + " coal, " + chest.countItem(Items.CHARCOAL) + " charcoal");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(m) == null, "the finished job is still saved");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(m).isEmpty(), "left in the bag: " + ModAttachments.BUILDER_BAG.getOrCreate(m).stacks());
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(m, 0) == 3, "the mason made " + ModAttachments.ITEMS_CRAFTED.getOrElse(m, 0));
		});
	}

	private static JsonObject lang() {
		try (InputStream in = MasonMixedFuelGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
			if (in == null) {
				throw new GameTestAssertException("en_us.json isn't on the classpath");
			}
			return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (java.io.IOException e) {
			throw new GameTestAssertException("en_us.json: " + e);
		}
	}
}

package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.craft.JobShortages;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane-b-1025-1838), B408 from its spec (wiki crafter.md): a Tinkerer's forge counts its coal as a raw material.
 * "Six raw iron for iron bars and no coal is 'short of 1 Coal', not 'Nobody here makes: Iron Bars', and the ore stays in
 * the chest." The boundaries round it from both sides: the exact line a player reads (built from en_us.json) and that
 * it never turns into "Nobody here makes" while the builder waits; coal but no ore; neither; one raw iron short;
 * exactly enough on charcoal; and no Tinkerer at all, where "Nobody here makes: Iron Bars" is right. Three iron bars
 * take one batch: six ingots (six raw iron fired, one coal for up to eight).
 */
public class QaTinkererShortGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos STATION = new BlockPos(14, 2, 2);
	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("aliveworkplace-qa");

	/** Ore, no coal: from 300 ticks on the line is never "Nobody here makes", and at the end it reads exactly the spec's. */
	//$ gametest_ticks_batch AREA '1800' '"qaTinkerNoCoalExact"'
	@GameTest(template = AREA, timeoutTicks = 1800, batch = "qaTinkerNoCoalExact")
	public void oreButNoCoalReadsTheSpecsLineAndNeverNobodyMakesIt(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.IRON_BARS, new ItemStack(Items.RAW_IRON, 6));
		Villager tinkerer = tinkerer(helper);
		List<String> seen = new ArrayList<>();
		for (int t = 300; t <= 1500; t += 100) {
			helper.runAfterDelay(t, () -> {
				String text = line(helper, a);
				seen.add(text);
				helper.assertTrue(text == null || !text.startsWith(lang("message.aliveworkplace.job_chain.nobody_makes").replace("%s", "")),
					"the builder's line says nobody makes the bars though a Tinkerer is there: " + text);
			});
		}
		helper.runAfterDelay(1600, () -> {
			String expected = cantMake("1", "Coal", "3", "Iron Bars");
			String text = line(helper, a);
			helper.assertTrue(expected.equals(text), "the builder's line: '" + text + "', the spec's: '" + expected + "'");
			assertTookNothing(helper, a, tinkerer, 6, 0);
			LOG.info("[QA B408] ore, no coal: '{}' (seen {})", text, seen);
			helper.succeed();
		});
	}

	/** Coal, no ore: the line names the iron, not the coal (that's there) and not "Nobody here makes"; the coal stays. */
	//$ gametest_ticks_batch AREA '1400' '"qaTinkerNoOre"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaTinkerNoOre")
	public void coalButNoOreNamesTheIronAndTakesNothing(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.IRON_BARS, new ItemStack(Items.COAL, 1));
		Villager tinkerer = tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = line(helper, a);
			String prefix = String.format(lang("message.aliveworkplace.job_chain.cant_make"), "short of");
			helper.assertTrue(text != null && text.startsWith(prefix) && text.contains(" to make"), "the builder's line should say what's short: " + text);
			String lacking = text.substring(0, text.indexOf(" to make"));
			helper.assertTrue(lacking.contains("Iron"), "the line should name the missing iron: " + text);
			helper.assertTrue(!lacking.contains("Coal"), "the line names the coal that's in the chest: " + text);
			helper.assertTrue(text.endsWith("to make 3 Iron Bars"), "the line should say it's for 3 Iron Bars: " + text);
			assertTookNothing(helper, a, tinkerer, 0, 1);
			LOG.info("[QA B408] coal, no ore: '{}'", text);
			helper.succeed();
		});
	}

	/** Neither ore nor coal: still a "short of" line naming the iron (the Tinkerer makes bars), never "Nobody here makes". */
	//$ gametest_ticks_batch AREA '1400' '"qaTinkerNeither"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaTinkerNeither")
	public void neitherOreNorCoalNamesTheIronNotNobody(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.IRON_BARS, new ItemStack(Items.DIRT, 1));
		Villager tinkerer = tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = line(helper, a);
			helper.assertTrue(text != null && text.startsWith("Can't be made yet: short of") && text.contains("Iron") && text.endsWith("to make 3 Iron Bars"),
				"with neither ore nor coal the builder's line should name the iron for the bars: " + text);
			assertTookNothing(helper, a, tinkerer, 0, 0);
			LOG.info("[QA B408] neither: '{}'", text);
			helper.succeed();
		});
	}

	/** Six raw iron and one charcoal (coal and charcoal count together): the bars are made, the charcoal burnt, 13 spare back. */
	//$ gametest_ticks_batch AREA '4000' '"qaTinkerCharcoal"'
	@GameTest(template = AREA, timeoutTicks = 4000, batch = "qaTinkerCharcoal")
	public void exactlyEnoughOnCharcoalMakesTheBars(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.IRON_BARS, new ItemStack(Items.RAW_IRON, 6), new ItemStack(Items.CHARCOAL, 1));
		Villager tinkerer = tinkerer(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "still building: missing " + a.site().missing()
				+ ", line " + line(helper, a));
			helper.assertTrue(a.plan().unfinished(helper.getLevel()).isEmpty(), "bars missing from the row");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertValueEqual(chest.countItem(Items.RAW_IRON), 0, "raw iron left");
			helper.assertValueEqual(chest.countItem(Items.CHARCOAL), 0, "charcoal left");
			helper.assertValueEqual(chest.countItem(Items.IRON_INGOT), 0, "half-made ingots back");
			helper.assertValueEqual(chest.countItem(Items.IRON_BARS), 13, "spare bars back");
			helper.assertTrue(ModAttachments.CRAFTER_JOB.get(tinkerer) == null, "the finished job is still on the tinkerer");
			LOG.info("[QA B408] exactly enough on charcoal: built, 13 spare bars back");
		});
	}

	/** No Tinkerer at all: after the builder's waited 600 ticks the line is "Nobody here makes: Iron Bars", and the ore stays. */
	//$ gametest_ticks_batch AREA '1600' '"qaTinkerNobody"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaTinkerNobody")
	public void withNoTinkererNobodyMakesIronBars(GameTestHelper helper) {
		QaJobChainsGameTests.prepare(helper);
		QaJobChainsGameTests.Asker a = QaJobChainsGameTests.asker(helper, Blocks.IRON_BARS, new ItemStack(Items.RAW_IRON, 6), new ItemStack(Items.COAL, 1));
		helper.runAfterDelay(200, () -> line(helper, a)); // the builder's status asks while he waits: his wait starts about now
		helper.runAfterDelay(1400, () -> {
			String expected = String.format(lang("message.aliveworkplace.job_chain.nobody_makes"), "Iron Bars");
			String text = line(helper, a);
			helper.assertTrue(expected.equals(text), "with no Tinkerer the builder's line: '" + text + "', expected '" + expected + "'");
			Container chest = helper.getBlockEntity(a.chest());
			helper.assertTrue(chest.countItem(Items.RAW_IRON) == 6 && chest.countItem(Items.COAL) == 1, "the chest changed with nobody to take it: "
				+ chest.countItem(Items.RAW_IRON) + " raw iron, " + chest.countItem(Items.COAL) + " coal");
			LOG.info("[QA B408] no tinkerer: '{}'", text);
			helper.succeed();
		});
	}

	// ---- helpers ----------------------------------------------------------------------------------------------------

	static Villager tinkerer(GameTestHelper helper) {
		helper.setBlock(STATION, ModBlocks.TINKERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, STATION.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		return v;
	}

	static String line(GameTestHelper helper, QaJobChainsGameTests.Asker a) {
		Component line = JobShortages.line(helper.getLevel(), a.site().id(), a.site().missing());
		return line == null ? null : line.getString();
	}

	/** Nothing taken: the chest as stocked, the Tinkerer's bag empty and no job saved on him. */
	static void assertTookNothing(GameTestHelper helper, QaJobChainsGameTests.Asker a, Villager tinkerer, int ore, int coal) {
		Container chest = helper.getBlockEntity(a.chest());
		helper.assertTrue(chest.countItem(Items.RAW_IRON) == ore && chest.countItem(Items.COAL) == coal, "the chest should be as it was (" + ore
			+ " raw iron, " + coal + " coal): " + chest.countItem(Items.RAW_IRON) + " raw iron, " + chest.countItem(Items.COAL) + " coal, "
			+ chest.countItem(Items.IRON_INGOT) + " ingots");
		helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).isEmpty(), "the tinkerer is carrying " + ModAttachments.BUILDER_BAG.getOrCreate(tinkerer).stacks());
		helper.assertTrue(ModAttachments.CRAFTER_JOB.get(tinkerer) == null, "the tinkerer holds a job it can't finish");
	}

	/** The line a player reads, put together from en_us.json: "Can't be made yet: short of <n> <item> to make <m> <part>". */
	static String cantMake(String n, String item, String m, String part) {
		String lack = String.format(lang("message.aliveworkplace.job_chain.lack_item"), n, item);
		String lacks = String.format(lang("message.aliveworkplace.job_chain.lacks"), lack, m, part);
		return String.format(lang("message.aliveworkplace.job_chain.cant_make"), lacks);
	}

	private static JsonObject lang;

	static String lang(String key) {
		if (lang == null) {
			try (InputStream in = QaTinkererShortGameTests.class.getResourceAsStream("/assets/aliveworkplace/lang/en_us.json")) {
				if (in == null) {
					throw new GameTestAssertException("en_us.json isn't on the classpath");
				}
				lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (java.io.IOException e) {
				throw new GameTestAssertException("en_us.json: " + e);
			}
		}
		if (!lang.has(key)) {
			throw new GameTestAssertException("no lang key " + key);
		}
		return lang.get(key).getAsString();
	}
}

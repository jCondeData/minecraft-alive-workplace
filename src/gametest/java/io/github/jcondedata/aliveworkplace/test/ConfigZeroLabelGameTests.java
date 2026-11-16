package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;

/**
 * B1325: a settings slider whose 0 means something of its own reads that at 0 (its {@code .zero} label, which the
 * settings screen shows instead of the number), as Workers per Village reads "No Limit". Villages per Wonder read
 * "Villages per Wonder: 0" though 0 means no limit; Lost Caravans and Gifted Villagers read "1 in: 0" though 0 means
 * never. B1326: money sums are written with thousands marked ("6,400").
 */
public class ConfigZeroLabelGameTests implements FabricGameTest {
	private static String zero(String name) {
		return Language.getInstance().getOrDefault("aliveworkplace.config." + name + ".zero", "(none)");
	}

	/** The three sliders' own words at 0, exactly as the screen reads them after "label: ". */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void aSliderWhoseZeroMeansNoLimitOrNeverSaysSo(GameTestHelper helper) {
		helper.assertValueEqual(zero("wonderServerLimit"), "No Limit", "Villages per Wonder at 0");
		helper.assertValueEqual(zero("lostCaravanChance"), "Never", "Lost Caravans: 1 in, at 0");
		helper.assertValueEqual(zero("giftedChance"), "Nobody", "Gifted Villagers, 1 in, at 0");
		helper.assertValueEqual(zero("maxWorkersPerVillage"), "No Limit", "(control) Workers per Village at 0");
		helper.succeed();
	}

	/**
	 * Every slider that can be 0 and either says "0: no limit" or reads "1 in" a number has its own 0 label: "1 in: 0" and
	 * "Villages per Wonder: 0" say the opposite of what 0 does.
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void everySliderWithASpecialZeroHasItsLabel(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> missing = new ArrayList<>();
		for (var e : WorkplaceConfig.RANGES.entrySet()) {
			String key = "aliveworkplace.config." + e.getKey();
			String label = lang.getOrDefault(key, key);
			String tip = lang.getOrDefault(key + ".tooltip", "").toLowerCase(Locale.ROOT);
			boolean special = tip.contains("0: no limit") || label.endsWith("1 in");
			if (e.getValue().min() == 0 && special && !lang.has(key + ".zero")) {
				missing.add(e.getKey() + " reads \"" + label + ": 0\"");
			}
		}
		helper.assertTrue(missing.isEmpty(), "sliders whose 0 means no limit or never, but read 0: " + missing);
		helper.succeed();
	}

	/** B1326: a money sum reads with its thousands marked, as every money line does ("6,400", not "6400"). */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void moneySumsHaveTheirThousandsMarked(GameTestHelper helper) {
		helper.assertValueEqual(Money.grouped(6400), "6,400", "64 emeralds' worth");
		helper.assertValueEqual(Money.grouped(100), "100", "one emerald's worth");
		helper.assertValueEqual(Money.grouped(150_000), "150,000", "a big sum");
		helper.succeed();
	}
}

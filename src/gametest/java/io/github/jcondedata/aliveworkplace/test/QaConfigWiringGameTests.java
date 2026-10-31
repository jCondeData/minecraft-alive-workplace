package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.explore.ExplorerWork;
import io.github.jcondedata.aliveworkplace.fish.FisherWork;
import io.github.jcondedata.aliveworkplace.fish.FishermanCap;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.mine.QuarryBuilds;
import io.github.jcondedata.aliveworkplace.orchard.OrchardWork;
import io.github.jcondedata.aliveworkplace.wood.LumberjackWork;
import io.github.jcondedata.aliveworkplace.work.Money;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.WorkerLimits;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * QA (lane E, config extremes): the whole-number settings no test set through the file yet (config-switches.md: each
 * "takes effect at the next server start", clamped to its range) reach the code that uses them. Each is given its own
 * value, inside its range and then past both ends, read through {@link WorkplaceConfig#parse} and applied, as a server
 * does with the file; the default file brings each back. Pure logic: applied and undone within one tick.
 */
public class QaConfigWiringGameTests implements FabricGameTest {
	/** Each setting, and where the game reads it. */
	private static Map<String, IntSupplier> wired() {
		Map<String, IntSupplier> m = new LinkedHashMap<>();
		m.put("supplyRadius", () -> SupplyContainers.RADIUS);
		m.put("lumberjackRadius", () -> LumberjackWork.RADIUS);
		m.put("orchardRadius", () -> OrchardWork.RADIUS);
		m.put("fisherRadius", () -> FisherWork.RADIUS);
		m.put("explorerRange", () -> ExplorerWork.RANGE);
		m.put("partnerRadius", () -> Partners.RADIUS);
		m.put("postmanRange", () -> PostOffice.ROUND);
		m.put("maxWorkersPerVillage", () -> WorkerLimits.MAX_PER_VILLAGE);
		m.put("quarryRegrowth", () -> QuarryBuilds.PER_DAY);
		m.put("fishermenPerVillage", () -> FishermanCap.BASE);
		m.put("villagersPerFisherman", () -> FishermanCap.PER_VILLAGERS);
		m.put("villageHallRadius", () -> VillageHalls.RADIUS);
		m.put("villageGrowthCap", () -> VillageGrowth.CAP);
		m.put("treasuryPerWorker", () -> Treasury.CENTS_PER_WORKER);
		m.put("dollarsPerEmerald", () -> Money.DOLLARS_PER_EMERALD);
		return m;
	}

	/** Applies {@code json}, reads every setting's value as the game sees it, then puts the default file back. */
	private static Map<String, Integer> applied(String json) {
		Map<String, Integer> out = new LinkedHashMap<>();
		try {
			WorkplaceConfig.parse(json).apply();
			wired().forEach((name, read) -> out.put(name, read.getAsInt()));
			out.put("villageRadius:workers", WorkerLimits.RADIUS);
			out.put("villageRadius:fishermen", FishermanCap.RADIUS);
		} finally {
			new WorkplaceConfig().apply();
		}
		return out;
	}

	private static String file(Map<String, Integer> values) {
		StringBuilder json = new StringBuilder("{\"configVersion\": 5");
		values.forEach((k, v) -> json.append(", \"").append(k).append("\": ").append(v));
		return json.append('}').toString();
	}

	/** A value of its own inside each range (none an end, and no two alike) reaches the game. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void everyWholeNumberSettingReachesTheGame(GameTestHelper helper) {
		Map<String, Integer> want = new LinkedHashMap<>();
		int i = 1;
		for (String name : wired().keySet()) {
			WorkplaceConfig.Range r = WorkplaceConfig.RANGES.get(name);
			helper.assertTrue(r != null, name + " has no range");
			want.put(name, Math.min(r.max() - 1, r.min() + i++)); // a different number for each, so two crossed wires show
		}
		want.put("villageRadius", 77);
		Map<String, Integer> got = applied(file(want));
		List<String> wrong = new ArrayList<>();
		wired().keySet().forEach(name -> {
			if (!got.get(name).equals(want.get(name))) {
				wrong.add(name + " " + want.get(name) + " gave " + got.get(name));
			}
		});
		if (got.get("villageRadius:workers") != 77 || got.get("villageRadius:fishermen") != 77) {
			wrong.add("villageRadius 77 gave the worker cap " + got.get("villageRadius:workers") + " and the fisherman cap " + got.get("villageRadius:fishermen"));
		}
		helper.assertTrue(wrong.isEmpty(), "settings that didn't reach the game: " + wrong);
		helper.succeed();
	}

	/** Past each end, the game gets the end of the range, never the number in the file. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void settingsPastTheirEndsReachTheGameClamped(GameTestHelper helper) {
		Map<String, Integer> low = new LinkedHashMap<>();
		Map<String, Integer> high = new LinkedHashMap<>();
		for (String name : wired().keySet()) {
			WorkplaceConfig.Range r = WorkplaceConfig.RANGES.get(name);
			low.put(name, r.min() - 1000);
			high.put(name, r.max() + 1_000_000);
		}
		Map<String, Integer> gotLow = applied(file(low));
		Map<String, Integer> gotHigh = applied(file(high));
		List<String> wrong = new ArrayList<>();
		for (String name : wired().keySet()) {
			WorkplaceConfig.Range r = WorkplaceConfig.RANGES.get(name);
			if (gotLow.get(name) != r.min()) {
				wrong.add(name + " " + low.get(name) + " gave " + gotLow.get(name) + ", not " + r.min());
			}
			if (gotHigh.get(name) != r.max()) {
				wrong.add(name + " " + high.get(name) + " gave " + gotHigh.get(name) + ", not " + r.max());
			}
		}
		helper.assertTrue(wrong.isEmpty(), "settings past their ends: " + wrong);
		helper.succeed();
	}

	/** The default file brings every one of them back to its default. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void theDefaultFileBringsEachBack(GameTestHelper helper) {
		Map<String, Integer> defaults = applied("{\"configVersion\": 5}");
		Map<String, Integer> want = Map.ofEntries(Map.entry("supplyRadius", 8), Map.entry("lumberjackRadius", 16), Map.entry("orchardRadius", 16),
			Map.entry("fisherRadius", 16), Map.entry("explorerRange", 48), Map.entry("partnerRadius", 16), Map.entry("postmanRange", 64),
			Map.entry("maxWorkersPerVillage", 0), Map.entry("quarryRegrowth", 100), Map.entry("fishermenPerVillage", 1),
			Map.entry("villagersPerFisherman", 10), Map.entry("villageHallRadius", 64), Map.entry("villageGrowthCap", 40),
			Map.entry("treasuryPerWorker", 20), Map.entry("dollarsPerEmerald", 100));
		List<String> wrong = new ArrayList<>();
		want.forEach((name, v) -> {
			if (!defaults.get(name).equals(v)) {
				wrong.add(name + " is " + defaults.get(name) + ", not " + v);
			}
		});
		helper.assertTrue(wrong.isEmpty(), "defaults (config-switches.md): " + wrong);
		helper.succeed();
	}
}

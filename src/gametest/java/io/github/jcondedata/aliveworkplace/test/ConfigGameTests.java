package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.locale.Language;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import io.github.jcondedata.aliveworkplace.explore.ExplorerWork;
import io.github.jcondedata.aliveworkplace.fish.FisherWork;
import io.github.jcondedata.aliveworkplace.hall.Treasury;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mail.PostOffice;
import io.github.jcondedata.aliveworkplace.wood.LumberjackWork;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Village;

/** config/aliveworkplace.json. */
public class ConfigGameTests implements FabricGameTest {
	/** Values are read, missing ones take their default, silly ones are clamped, and applying puts them into effect. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void configIsReadClampedAndApplied(GameTestHelper helper) {
		WorkplaceConfig config = WorkplaceConfig.parse("{\"supplyRadius\": 12, \"guardRadius\": 500, \"dollarsPerEmerald\": 0}");
		helper.assertTrue(config.supplyRadius == 12, "supplyRadius " + config.supplyRadius);
		helper.assertTrue(config.guardRadius == 64, "guardRadius should be clamped to 64, not " + config.guardRadius);
		helper.assertTrue(config.dollarsPerEmerald == 1, "dollarsPerEmerald should be at least 1, not " + config.dollarsPerEmerald);
		helper.assertTrue(config.maxSiteDistance == 48 && config.orchardRadius == 16, "missing values should take their defaults");
		helper.assertTrue(WorkplaceConfig.parse("").supplyRadius == 8, "an empty file means the defaults");

		// Applying (and putting the defaults back straight away: other tests run on the same server).
		config.apply();
		boolean applied = SupplyContainers.RADIUS == 12 && Guards.RADIUS == 64 && Money.DOLLARS_PER_EMERALD == 1;
		new WorkplaceConfig().apply();
		helper.assertTrue(applied, "apply() didn't change the values in use");
		helper.assertTrue(SupplyContainers.RADIUS == 8 && Guards.RADIUS == 24 && Money.DOLLARS_PER_EMERALD == 100, "defaults not restored");
		helper.succeed();
	}

	/**
	 * The distances and village numbers no other test read from the file (found by the full check's inventory,
	 * ROADMAP 21.2): each is read, clamped at both ends of its range, defaults when missing, and put into effect.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyRadiusAndVillageNumberIsReadClampedAndApplied(GameTestHelper helper) {
		WorkplaceConfig defaults = WorkplaceConfig.parse("{}");
		helper.assertTrue(defaults.lumberjackRadius == 16 && defaults.fisherRadius == 16 && defaults.partnerRadius == 16 && defaults.explorerRange == 48
			&& defaults.postmanRange == 64 && defaults.villageRadius == 48 && defaults.villageHallRadius == 64 && defaults.villageGrowthCap == 40
			&& defaults.treasuryPerWorker == 20, "missing values should take their defaults");

		WorkplaceConfig mid = WorkplaceConfig.parse("{\"lumberjackRadius\": 20, \"fisherRadius\": 21, \"partnerRadius\": 22, \"explorerRange\": 100,"
			+ " \"postmanRange\": 200, \"villageRadius\": 90, \"villageHallRadius\": 120, \"villageGrowthCap\": 300, \"treasuryPerWorker\": 45}");
		helper.assertTrue(mid.lumberjackRadius == 20 && mid.fisherRadius == 21 && mid.partnerRadius == 22 && mid.explorerRange == 100 && mid.postmanRange == 200
			&& mid.villageRadius == 90 && mid.villageHallRadius == 120 && mid.villageGrowthCap == 300 && mid.treasuryPerWorker == 45,
			"values inside their range should be read as written");

		WorkplaceConfig low = WorkplaceConfig.parse("{\"lumberjackRadius\": -5, \"fisherRadius\": 0, \"partnerRadius\": 3, \"explorerRange\": 15,"
			+ " \"postmanRange\": 1, \"villageRadius\": -1, \"villageHallRadius\": 15, \"villageGrowthCap\": -10, \"treasuryPerWorker\": -1}");
		helper.assertTrue(low.lumberjackRadius == 4 && low.fisherRadius == 4 && low.partnerRadius == 4 && low.explorerRange == 16 && low.postmanRange == 16
			&& low.villageRadius == 0 && low.villageHallRadius == 16 && low.villageGrowthCap == 0 && low.treasuryPerWorker == 0,
			"values below their range should be raised to the minimum: lumberjack " + low.lumberjackRadius + ", fisher " + low.fisherRadius + ", partner "
				+ low.partnerRadius + ", explorer " + low.explorerRange + ", postman " + low.postmanRange + ", village " + low.villageRadius + ", hall "
				+ low.villageHallRadius + ", growth " + low.villageGrowthCap + ", treasury " + low.treasuryPerWorker);

		WorkplaceConfig high = WorkplaceConfig.parse("{\"lumberjackRadius\": 49, \"fisherRadius\": 1000, \"partnerRadius\": 49, \"explorerRange\": 129,"
			+ " \"postmanRange\": 257, \"villageRadius\": 129, \"villageHallRadius\": 161, \"villageGrowthCap\": 501, \"treasuryPerWorker\": 501}");
		helper.assertTrue(high.lumberjackRadius == 48 && high.fisherRadius == 48 && high.partnerRadius == 48 && high.explorerRange == 128
			&& high.postmanRange == 256 && high.villageRadius == 128 && high.villageHallRadius == 160 && high.villageGrowthCap == 500
			&& high.treasuryPerWorker == 500,
			"values above their range should be lowered to the maximum: lumberjack " + high.lumberjackRadius + ", fisher " + high.fisherRadius + ", partner "
				+ high.partnerRadius + ", explorer " + high.explorerRange + ", postman " + high.postmanRange + ", village " + high.villageRadius + ", hall "
				+ high.villageHallRadius + ", growth " + high.villageGrowthCap + ", treasury " + high.treasuryPerWorker);

		// Applying (and putting the defaults back straight away: other tests run on the same server).
		mid.apply();
		String inUse = "lumberjack " + LumberjackWork.RADIUS + ", fisher " + FisherWork.RADIUS + ", partner " + Partners.RADIUS + ", explorer " + ExplorerWork.RANGE
			+ ", postman " + PostOffice.ROUND + ", village " + Village.RADIUS + ", hall " + VillageHalls.RADIUS + ", growth " + VillageGrowth.CAP + ", treasury "
			+ Treasury.CENTS_PER_WORKER;
		boolean applied = LumberjackWork.RADIUS == 20 && FisherWork.RADIUS == 21 && Partners.RADIUS == 22 && ExplorerWork.RANGE == 100 && PostOffice.ROUND == 200
			&& VillageHalls.RADIUS == 120 && VillageGrowth.CAP == 300 && Treasury.CENTS_PER_WORKER == 45
			// Village sharing stays off in gametests (tests side by side would share chests); the village tests switch it on themselves.
			&& Village.RADIUS == 0;
		new WorkplaceConfig().apply();
		helper.assertTrue(applied, "apply() didn't put the values into effect: " + inUse);
		helper.assertTrue(LumberjackWork.RADIUS == 16 && FisherWork.RADIUS == 16 && Partners.RADIUS == 16 && ExplorerWork.RANGE == 48 && PostOffice.ROUND == 64
			&& VillageHalls.RADIUS == 64 && VillageGrowth.CAP == 40 && Treasury.CENTS_PER_WORKER == 20, "defaults not restored");
		helper.succeed();
	}

	/**
	 * The settings screen (Mod Menu, ROADMAP 26.3) lists every option: each has a label and a tooltip in the language
	 * file, and every number has a range its default sits in, which is also what the file is clamped to.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everySettingHasItsWordsAndRange(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> problems = new ArrayList<>();
		for (String key : List.of("aliveworkplace.config.title", "aliveworkplace.config.note")) {
			if (!lang.has(key)) {
				problems.add(key);
			}
		}
		WorkplaceConfig defaults = new WorkplaceConfig();
		List<String> names = WorkplaceConfig.optionNames();
		for (String name : names) {
			for (String key : List.of("aliveworkplace.config." + name, "aliveworkplace.config." + name + ".tooltip")) {
				if (!lang.has(key)) {
					problems.add("untranslated " + key);
				}
			}
			if (!WorkplaceConfig.isSwitch(name)) {
				WorkplaceConfig.Range range = WorkplaceConfig.RANGES.get(name);
				if (range == null) {
					problems.add(name + " has no range");
				} else if (defaults.getInt(name) < range.min() || defaults.getInt(name) > range.max()) {
					problems.add(name + "'s default " + defaults.getInt(name) + " is outside " + range);
				}
			}
		}
		for (String name : WorkplaceConfig.RANGES.keySet()) {
			if (!names.contains(name) || WorkplaceConfig.isSwitch(name)) {
				problems.add("range for " + name + ", which isn't a number setting");
			}
		}
		helper.assertTrue(names.size() == 56, "expected 56 settings on the screen (30.14 added harvestIdols, 29.10 added strangeMoods, 28.12 added daycareKeepers, 30.15 added tonics, 30.13 added villageBanners, 29.9 added legendSites, 30.12 added cradles, 30.11 added workHorns, 29.6 added giftedChance, 29.5 legendNeeds, 27.8 stewardSelfRun, 28.11 gemGrowers, 28.10 habitatKeepers and habitatSightings, 30.3 villageEdicts and edictMinDays, 30.2 maxWorkPace, 29.2 legends, 28.9 berryBreeders, 28.8 campCooks, 27.5 steward and stewardMaxOpenBuilds;"
			+ " 29.3's mythicLegendCap list is in the file only), found " + names.size() + ": " + names);
		helper.assertTrue(!names.contains("mythicLegendCap"), "a list on the settings screen");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * What the settings screen does when it closes: the edited values are written to the file (clamped) and read back
	 * the same; a broken file gives the defaults and is replaced by a complete one.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void savedSettingsAreReadBack(GameTestHelper helper) {
		Path dir;
		try {
			dir = Files.createTempDirectory("aliveworkplace-config");
		} catch (IOException e) {
			throw new GameTestAssertException("no temp dir: " + e);
		}
		WorkplaceConfig config = WorkplaceConfig.load(dir);
		helper.assertTrue(config.supplyRadius == 8 && config.festivals, "no file means the defaults");
		config.setBoolean("festivals", false);
		config.setInt("seasonDays", 12);
		config.setInt("guardRadius", 1000);
		config.save(dir);
		WorkplaceConfig read = WorkplaceConfig.load(dir);
		helper.assertTrue(!read.festivals && read.seasonDays == 12, "saved values should be read back: festivals " + read.festivals
			+ ", seasonDays " + read.seasonDays);
		helper.assertTrue(read.guardRadius == 64, "an out-of-range value should be saved clamped, not " + read.guardRadius);
		helper.assertTrue(read.villagerNames && read.dollarsPerEmerald == 100, "values not edited keep their defaults");

		try {
			Files.writeString(dir.resolve(WorkplaceConfig.FILE), "{ not json");
			WorkplaceConfig broken = WorkplaceConfig.loadAndApply(dir);
			new WorkplaceConfig().apply();
			helper.assertTrue(broken.festivals && broken.seasonDays == 16, "a broken file should give the defaults");
			helper.assertTrue(Files.readString(dir.resolve(WorkplaceConfig.FILE)).contains("\"villageProtection\""),
				"a broken file should be rewritten with every setting");
			helper.assertTrue(Files.readString(dir.resolve(WorkplaceConfig.FILE)).contains("\"mythicLegendCap\""), "the Mythic Legend caps aren't in the file");
		} catch (IOException e) {
			throw new GameTestAssertException("file trouble: " + e);
		}
		helper.succeed();
	}
}

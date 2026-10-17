package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * ROADMAP 37.1: Milestone 37's switches ({@code furnishedBuilds}, {@code furnitureMods}, {@code moddedStorageNetworks})
 * behind {@code Expansions.M37}, and the furniture mods' order as a file gives it.
 */
public class FurnishingConfigGameTests implements FabricGameTest {
	/** The mods' order: the owner's default, cleaned of case, blanks and repeats, an empty list kept, a missing one defaulted. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void furnitureModsKeepTheirOrderAndAreCleaned(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		try {
			WorkplaceConfig fresh = new WorkplaceConfig();
			if (!fresh.furnitureMods.equals(List.of("handcrafted", "cozyhome", "cobblefurnies", "beautify"))) {
				problems.add("default order " + fresh.furnitureMods);
			}
			WorkplaceConfig mixed = WorkplaceConfig.parse("{\"furnitureMods\": [\" CobbleFurnies \", \"\", null, \"cobblefurnies\", \"handcrafted\"]}");
			if (!mixed.furnitureMods.equals(List.of("cobblefurnies", "handcrafted"))) {
				problems.add("cleaned order " + mixed.furnitureMods);
			}
			mixed.apply();
			if (!Furnishings.MODS.equals(List.of("cobblefurnies", "handcrafted"))) {
				problems.add("applied order " + Furnishings.MODS);
			}
			WorkplaceConfig none = WorkplaceConfig.parse("{\"furnitureMods\": []}");
			if (!none.furnitureMods.isEmpty()) {
				problems.add("an empty list (no mods: vanilla) became " + none.furnitureMods);
			}
			WorkplaceConfig broken = WorkplaceConfig.parse("{\"furnitureMods\": null}");
			if (!broken.furnitureMods.equals(Furnishings.DEFAULT_MODS)) {
				problems.add("a null list should take the defaults, got " + broken.furnitureMods);
			}
			if (WorkplaceConfig.optionNames().contains("furnitureMods")) {
				problems.add("a list is on the settings screen");
			}
			String json = new com.google.gson.GsonBuilder().create().toJson(new WorkplaceConfig());
			if (!json.contains("\"furnitureMods\":[\"handcrafted\",\"cozyhome\",\"cobblefurnies\",\"beautify\"]")) {
				problems.add("a saved file lacks the list");
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/** The gate: with M37 unfinished a player's file can't turn furniture or storage networks on; in tests it can, and off is obeyed. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"expansionGate"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "expansionGate")
	public void theM37GateHoldsItsSwitches(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		String on = "{\"furnishedBuilds\": true, \"moddedStorageNetworks\": true}";
		ExpansionGateGameTests.asPlayer(() -> {
			WorkplaceConfig.parse(on).apply();
			if (Furnishings.ENABLED || StorageNetworks.ENABLED) {
				problems.add("a player's file turned an unfinished M37 switch on");
			}
			if (new WorkplaceConfig().furnishedBuilds || new WorkplaceConfig().moddedStorageNetworks) {
				problems.add("M37's switches default on with its gate closed");
			}
			List<String> screen = WorkplaceConfig.optionNames();
			if (screen.contains("furnishedBuilds") || screen.contains("moddedStorageNetworks")) {
				problems.add("M37's switches are on the settings screen with its gate closed");
			}
		});
		try {
			WorkplaceConfig.parse(on).apply();
			if (!Furnishings.ENABLED || !StorageNetworks.ENABLED) {
				problems.add("with the gate open the switches stayed off");
			}
			WorkplaceConfig.parse("{\"expansionsOpen\": [\"M37\"], \"furnishedBuilds\": false, \"moddedStorageNetworks\": false}").apply();
			if (Furnishings.ENABLED || StorageNetworks.ENABLED) {
				problems.add("switched off with the gate open, still on");
			}
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.assertTrue(!Expansions.M37, "M37 marked complete: move its switches out of this test");
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}
}

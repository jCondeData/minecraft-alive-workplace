package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 37.12 without the building mods (plain Fabric): every home and workshop has a modded library copy, but none
 * can be built here, so every build is the vanilla build exactly as before; the choice is saved and read back; the
 * copies are not blueprints of their own in the library; the switch is read from the config.
 */
public class ModdedLibraryGameTests implements FabricGameTest {
	private static final ResourceLocation COTTAGE = AliveWorkplace.id("starter_cottage");
	/** Open-air builds with nothing the building mods dress (no walls, floors, chests or shelves): the same in both libraries. */
	private static final java.util.Set<String> OPEN_AIR = java.util.Set.of("habitat_garden");

	/** Every home and workshop the Blueprint Table or a villager offers. */
	static List<ResourceLocation> homesAndWorkshops() {
		return Stream.of(StarterBlueprints.ALL, StarterBlueprints.WORKPLACES, StarterBlueprints.COBBLEMON_WORKPLACES, StarterBlueprints.ONE_TIER,
				StarterBlueprints.ANSWERING_HOUSES, StarterBlueprints.JOB_BUILDS, StarterBlueprints.VANILLA_JOB_BUILDS)
			.flatMap(List::stream).map(StarterBlueprints.Entry::id).filter(id -> !OPEN_AIR.contains(id.getPath())).distinct().toList();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyHomeAndWorkshopHasAModdedCopy(GameTestHelper helper) {
		List<ResourceLocation> without = new ArrayList<>();
		for (ResourceLocation id : homesAndWorkshops()) {
			if (!ModdedLibrary.hasCopy(id)) {
				without.add(id);
			}
		}
		helper.assertTrue(without.isEmpty(), "no modded copy of " + without);
		helper.assertTrue(ModdedLibrary.builds().size() >= homesAndWorkshops().size(), "only " + ModdedLibrary.builds().size() + " copies");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void withoutTheModsEveryBuildIsTheVanillaBuild(GameTestHelper helper) {
		try {
			new WorkplaceConfig().apply();
			helper.assertTrue(ModdedLibrary.ENABLED, "the switch is off in GameTests by default");
			helper.assertTrue(!ModdedLibrary.missing(COTTAGE).isEmpty(), "the copy's mods count as installed in plain Fabric");
			helper.assertTrue(ModdedLibrary.copy(COTTAGE).isEmpty(), "an incomplete copy was handed out");
			for (ResourceLocation id : homesAndWorkshops()) {
				FurnitureChoice choice = FurnitureChoice.forNewBuild(id);
				helper.assertTrue(!choice.modded(), id + " chose the modded library without its mods");
				Blueprint base = BlueprintLibrary.get(helper.getLevel(), id).orElse(null);
				helper.assertTrue(base != null, id + " has no vanilla build");
				helper.assertTrue(choice.apply(base, id).blocks().equals(base.blocks()), id + " isn't the vanilla build without the mods");
			}
			BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
				Rotation.NONE, Mirror.NONE);
			BuildSite site = new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "test", COTTAGE, placement);
			BuildPlan plan = site.plan(helper.getLevel());
			boolean modded = Stream.of(BuildPlan.Stage.values()).flatMap(s -> plan.steps(s).stream())
				.anyMatch(s -> !BuiltInRegistries.BLOCK.getKey(s.state().getBlock()).getNamespace().equals("minecraft"));
			helper.assertTrue(!modded, "the cottage plans a modded block in plain Fabric");
			helper.assertTrue(site.furniture().isVanilla(), "the site saved a modded choice: " + site.furniture());
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theModdedChoiceIsSavedAndRead(GameTestHelper helper) {
		FurnitureChoice modded = FurnitureChoice.VANILLA.withLibrary(true);
		helper.assertTrue(modded.modded() && !modded.isVanilla(), "a modded-library choice counts as vanilla");
		FurnitureChoice read = FurnitureChoice.load(modded.save());
		helper.assertTrue(read.modded() && read.spots().isEmpty(), "the modded library was lost on a save: " + read);
		helper.assertTrue(FurnitureChoice.load(FurnitureChoice.VANILLA.save()) == FurnitureChoice.VANILLA, "a vanilla choice came back modded");
		helper.assertTrue(modded.withLibrary(false) == FurnitureChoice.VANILLA, "taking the library off didn't give the vanilla choice");
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theCopiesAreNotBlueprintsOfTheirOwn(GameTestHelper helper) {
		List<ResourceLocation> listed = BlueprintLibrary.list(helper.getLevel().getServer(), false).stream()
			.filter(id -> id.getPath().startsWith(ModdedLibrary.PREFIX)).toList();
		helper.assertTrue(listed.isEmpty(), "modded copies in the Blueprint Table: " + listed);
		helper.succeed();
	}

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSwitchIsReadFromTheConfig(GameTestHelper helper) {
		try {
			WorkplaceConfig.parse("{\"moddedLibrary\": false}").apply();
			helper.assertTrue(!ModdedLibrary.ENABLED, "moddedLibrary false in the file left the modded library on");
			new WorkplaceConfig().apply();
			helper.assertTrue(ModdedLibrary.ENABLED, "the default isn't the modded library (where its mods are)");
			helper.assertTrue(WorkplaceConfig.optionNames().contains("moddedLibrary"),
				"moddedLibrary isn't on the settings screen");
		} finally {
			new WorkplaceConfig().apply();
		}
		helper.succeed();
	}
}

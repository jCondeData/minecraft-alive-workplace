package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 28.7, the parts that need no Cobblemon (this suite runs without it): the Pokémon Center's two tiers are left
 * out of the Blueprint Table, the second keeps most of the first, the hall's "What next?" rule, its names and its place
 * on the Village Map. With Cobblemon: {@code PokemonCenterCompatTests}.
 */
public class PokemonCenterGameTests implements FabricGameTest {
	/** Without Cobblemon the Pokémon Center isn't in the Blueprint Table (most of it is Cobblemon's blocks). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonCenterNeedsCobblemon(GameTestHelper helper) {
		List<ResourceLocation> listed = TableServer.listing(helper.getLevel().getServer()).stream().map(e -> e.id()).toList();
		for (StarterBlueprints.Entry entry : StarterBlueprints.COBBLEMON_ONLY) {
			helper.assertFalse(listed.contains(entry.id()), entry.id() + " is in the Blueprint Table without Cobblemon");
			helper.assertFalse(BlueprintLibrary.list(helper.getLevel().getServer(), false).contains(entry.id()), entry.id() + " is in the library");
			helper.assertTrue(BlueprintLibrary.list(helper.getLevel().getServer(), true).contains(entry.id()), entry.id() + " should still load for operators");
		}
		helper.assertTrue(listed.contains(StarterBlueprints.HEALING_CENTER.id()), "the other blueprints should still be listed");
		helper.succeed();
	}

	/** Pokémon Center II is built over a finished Pokémon Center: it keeps most of its blocks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void pokemonCenterIIKeepsTheHall(GameTestHelper helper) {
		Blueprint base = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.POKEMON_CENTER.id()).orElseThrow();
		Blueprint upgrade = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.POKEMON_CENTER_2.id()).orElseThrow();
		Map<BlockPos, BlockState> up = new HashMap<>();
		upgrade.blocks().forEach(e -> up.put(e.pos(), e.state()));
		long solid = base.blocks().stream().filter(e -> !e.state().isAir()).count();
		long kept = base.blocks().stream().filter(e -> !e.state().isAir() && e.state().equals(up.get(e.pos()))).count();
		helper.assertTrue(solid > 300, "the hall is only " + solid + " blocks");
		helper.assertTrue(kept >= solid * 0.6, "Pokémon Center II keeps only " + kept + " of the hall's " + solid + " blocks");
		helper.succeed();
	}

	/** "What next?" suggests a Pokémon Center to a Cobblemon village of Village rank or more that has none. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theHallSuggestsAPokemonCenter(GameTestHelper helper) {
		ResourceLocation cottage = StarterBlueprints.STARTER_COTTAGE.id();
		helper.assertTrue(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.VILLAGE, List.of(cottage)), "a Village without one");
		helper.assertTrue(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.CITY, List.of()), "a City without one");
		helper.assertFalse(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.HAMLET, List.of()), "a Hamlet is too small");
		helper.assertFalse(VillageAdvice.wantsPokemonCenter(false, VillageRanks.Rank.TOWN, List.of()), "no Cobblemon, no Pokémon Center");
		helper.assertFalse(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.TOWN, List.of(cottage, StarterBlueprints.POKEMON_CENTER.id())),
			"it has one");
		helper.assertFalse(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.TOWN, List.of(StarterBlueprints.POKEMON_CENTER_2.id())),
			"it has the second tier");
		helper.assertTrue(VillageMaps.kindOf(StarterBlueprints.POKEMON_CENTER_2.id()).orElse(null) == VillageMaps.Kind.CARE,
			"on the Village Map with the Healing Center: " + VillageMaps.kindOf(StarterBlueprints.POKEMON_CENTER_2.id()));
		helper.succeed();
	}

	/** Every sentence and name a player reads for the Pokémon Center is in English. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonCenterIsTranslated(GameTestHelper helper) {
		Language english = Language.getInstance();
		for (String key : List.of("blueprint.aliveworkplace.pokemon_center", "blueprint.aliveworkplace.pokemon_center_2",
				"advice.aliveworkplace.pokemon_center", "advice.aliveworkplace.pokemon_center.how",
				"message.aliveworkplace.nurse.healed_machine", "message.aliveworkplace.nurse.machine_busy",
				"aliveworkplace.config.nurseHealingMachine", "aliveworkplace.config.nurseHealingMachine.tooltip")) {
			helper.assertTrue(english.has(key), "no English for " + key);
		}
		helper.assertTrue(english.getOrDefault("blueprint.aliveworkplace.pokemon_center_2").equals("Pokémon Center II"),
			english.getOrDefault("blueprint.aliveworkplace.pokemon_center_2"));
		helper.succeed();
	}
}

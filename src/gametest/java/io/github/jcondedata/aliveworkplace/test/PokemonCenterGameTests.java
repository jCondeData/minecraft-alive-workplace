package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLooks;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
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

	/**
	 * ROADMAP 28.7a: the Pokémon Center's other looks (the Mountain Lodge, the Sunny Plaza) load, each tier II keeps most
	 * of its own tier I, and they count as a Pokémon Center everywhere: the hall's advice, the steward's rules (its
	 * family), the Village Map. Each has its English name.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonCenterLooksCountAsPokemonCenters(GameTestHelper helper) {
		Language english = Language.getInstance();
		List<StarterBlueprints.Entry> ones = StarterBlueprints.POKEMON_CENTER_LOOKS;
		List<StarterBlueprints.Entry> twos = StarterBlueprints.POKEMON_CENTER_2_LOOKS;
		helper.assertTrue(ones.size() == 3 && twos.size() == 3, "three looks of each tier: " + ones.size() + ", " + twos.size());
		for (int i = 1; i < ones.size(); i++) {
			StarterBlueprints.Entry one = ones.get(i);
			StarterBlueprints.Entry two = twos.get(i);
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), one.id()).orElse(null);
			Blueprint upgrade = BlueprintLibrary.get(helper.getLevel(), two.id()).orElse(null);
			helper.assertTrue(base != null && upgrade != null, one.id() + " or " + two.id() + " doesn't load");
			helper.assertTrue(BlueprintUpgrades.baseOf(two.id()).orElseThrow().equals(one.id()), two.id() + " should upgrade " + one.id());
			Map<BlockPos, BlockState> up = new HashMap<>();
			upgrade.blocks().forEach(e -> up.put(e.pos(), e.state()));
			long solid = base.blocks().stream().filter(e -> !e.state().isAir()).count();
			long kept = base.blocks().stream().filter(e -> !e.state().isAir() && e.state().equals(up.get(e.pos()))).count();
			helper.assertTrue(solid > 300, one.id() + " is only " + solid + " blocks");
			helper.assertTrue(kept >= solid * 0.6, two.id() + " keeps only " + kept + " of " + solid + " blocks");
			for (StarterBlueprints.Entry e : List.of(one, two)) {
				helper.assertTrue(StewardConditions.family(e.id()).equals(StarterBlueprints.POKEMON_CENTER.id()), e.id() + "'s family is " + StewardConditions.family(e.id()));
				helper.assertFalse(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.TOWN, List.of(e.id())), "a village with " + e.id() + " has one");
				helper.assertTrue(VillageMaps.kindOf(e.id()).orElse(null) == VillageMaps.Kind.CARE, e.id() + " on the map as " + VillageMaps.kindOf(e.id()));
				helper.assertTrue(StarterBlueprints.COBBLEMON_ONLY.contains(e), e.id() + " needs Cobblemon");
				String key = "blueprint." + e.id().getNamespace() + "." + e.id().getPath().replace('/', '.');
				helper.assertTrue(english.has(key), "no English for " + key);
			}
		}
		helper.assertTrue(english.getOrDefault("blueprint.aliveworkplace.looks.lodge.pokemon_center_2").equals("Pokémon Center II: Mountain Lodge"),
			english.getOrDefault("blueprint.aliveworkplace.looks.lodge.pokemon_center_2"));
		helper.succeed();
	}

	/**
	 * ROADMAP 28.7a: a steward builds his village's own look of the Pokémon Center, the same one every time, villages
	 * differ (all three looks turn up over a few villages), and the other looks follow it in his plot request for spots
	 * where it doesn't fit; an upgrade keeps the look ({@code looks/lodge/pokemon_center}'s next tier is its own).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aStewardBuildsHisVillagesLook(GameTestHelper helper) {
		ResourceLocation center = StarterBlueprints.POKEMON_CENTER.id();
		java.util.Set<ResourceLocation> seen = new java.util.HashSet<>();
		for (int i = 0; i < 40; i++) {
			BlockPos hall = new BlockPos(i * 173 - 2000, 64, i * 311 + 500);
			ResourceLocation look = BlueprintLooks.pick(center, hall);
			helper.assertTrue(look.equals(BlueprintLooks.pick(center, hall)), "the same village picks the same look");
			seen.add(look);
			StewardWishes.Wish wish = new StewardWishes.Wish(ResourceLocation.fromNamespaceAndPath("aliveworkplace", "pokemon_center"),
				new StewardRules.Effect(StewardRules.Kind.BUILD, java.util.Optional.of(center), java.util.Optional.of("civic"), java.util.Optional.empty(),
					java.util.Optional.empty(), false, java.util.Optional.empty()), 50, "why", List.of());
			List<ResourceLocation> tried = StewardWishes.plotFor(helper.getLevel(), hall, wish).orElseThrow().blueprints();
			helper.assertTrue(tried.size() == 3 && tried.get(0).equals(look) && tried.containsAll(BlueprintLooks.of(center)),
				"the plot request tries the village's look first, then the others: " + tried);
		}
		List<ResourceLocation> all = StarterBlueprints.POKEMON_CENTER_LOOKS.stream().map(StarterBlueprints.Entry::id).toList();
		helper.assertTrue(seen.containsAll(all), "every look turns up in some village: " + seen);
		helper.assertTrue(BlueprintUpgrades.upgradeOf(StarterBlueprints.POKEMON_CENTER_LODGE.id()).equals(StarterBlueprints.POKEMON_CENTER_LODGE_2.id()),
			"the lodge upgrades to the lodge: " + BlueprintUpgrades.upgradeOf(StarterBlueprints.POKEMON_CENTER_LODGE.id()));
		helper.assertTrue(BlueprintLooks.of(StarterBlueprints.HEALING_CENTER.id()).equals(List.of(StarterBlueprints.HEALING_CENTER.id())),
			"a building with one look is tried as it is");
		helper.succeed();
	}
}

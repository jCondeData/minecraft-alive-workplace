package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * ROADMAP 28.13, the parts that need no Cobblemon (this suite runs without it): the Pokémon jobs' builds are left out
 * of the Blueprint Table, each upgrade keeps most of its first tier, their names and their place on the Village Map.
 * With Cobblemon (a builder builds each, job block and all): {@code PokemonBuildsCompatTests}.
 */
public class PokemonBuildsGameTests implements FabricGameTest {
	/** Without Cobblemon none of them is in the Blueprint Table (their job blocks are Cobblemon's or for its jobs). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonJobBuildsNeedCobblemon(GameTestHelper helper) {
		List<ResourceLocation> listed = TableServer.listing(helper.getLevel().getServer()).stream().map(e -> e.id()).toList();
		helper.assertTrue(StarterBlueprints.JOB_BUILDS.size() >= 6, "only " + StarterBlueprints.JOB_BUILDS.size() + " builds");
		for (StarterBlueprints.Entry entry : StarterBlueprints.JOB_BUILDS) {
			helper.assertTrue(StarterBlueprints.COBBLEMON_ONLY.contains(entry), entry.id() + " isn't Cobblemon-only");
			helper.assertFalse(listed.contains(entry.id()), entry.id() + " is in the Blueprint Table without Cobblemon");
			helper.assertTrue(BlueprintLibrary.list(helper.getLevel().getServer(), true).contains(entry.id()), entry.id() + " should still load for operators");
		}
		helper.succeed();
	}

	/** Each build comes in two tiers, and the second is drawn over the first: it keeps at least 60% of its blocks. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void eachPokemonJobUpgradeKeepsItsBase(GameTestHelper helper) {
		int upgrades = 0;
		for (StarterBlueprints.Entry entry : StarterBlueprints.JOB_BUILDS) {
			Optional<ResourceLocation> baseId = BlueprintUpgrades.baseOf(entry.id());
			if (baseId.isEmpty()) {
				helper.assertTrue(StarterBlueprints.JOB_BUILDS.stream().anyMatch(e -> e.id().equals(BlueprintUpgrades.upgradeOf(entry.id()))),
					entry.id() + " has no upgrade");
				continue;
			}
			upgrades++;
			Blueprint base = BlueprintLibrary.get(helper.getLevel(), baseId.get()).orElseThrow();
			Blueprint upgrade = BlueprintLibrary.get(helper.getLevel(), entry.id()).orElseThrow();
			helper.assertTrue(upgrade.size().getX() >= base.size().getX() && upgrade.size().getY() >= base.size().getY()
				&& upgrade.size().getZ() >= base.size().getZ(), entry.id() + " is smaller than " + baseId.get());
			Map<BlockPos, BlockState> up = new HashMap<>();
			upgrade.blocks().forEach(e -> up.put(e.pos(), e.state()));
			long solid = base.blocks().stream().filter(e -> !e.state().isAir()).count();
			long kept = base.blocks().stream().filter(e -> !e.state().isAir() && e.state().equals(up.get(e.pos()))).count();
			helper.assertTrue(solid > 150, baseId.get() + " is only " + solid + " blocks");
			helper.assertTrue(kept >= solid * 0.6, entry.id() + " keeps only " + kept + " of " + baseId.get() + "'s " + solid + " blocks");
		}
		helper.assertTrue(upgrades * 2 == StarterBlueprints.JOB_BUILDS.size(), upgrades + " upgrades for " + StarterBlueprints.JOB_BUILDS.size() + " builds");
		helper.succeed();
	}

	/** On the Village Map with the farms (the Daycare with the care buildings), and none of them counts as a Pokémon Center. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonJobBuildsAreOnTheMap(GameTestHelper helper) {
		for (StarterBlueprints.Entry entry : StarterBlueprints.JOB_BUILDS) {
			VillageMaps.Kind kind = VillageMaps.kindOf(entry.id()).orElse(null);
			helper.assertTrue(kind == VillageMaps.Kind.FARMS || kind == VillageMaps.Kind.CARE, entry.id() + " is on the map as " + kind);
		}
		helper.assertTrue(VillageMaps.kindOf(StarterBlueprints.DAYCARE_2.id()).orElse(null) == VillageMaps.Kind.CARE, "the Daycare cares");
		helper.assertTrue(VillageAdvice.wantsPokemonCenter(true, VillageRanks.Rank.TOWN,
			StarterBlueprints.JOB_BUILDS.stream().map(StarterBlueprints.Entry::id).toList()), "a Camp Kitchen isn't a Pokémon Center");
		helper.succeed();
	}

	/** Every name a player reads for them is in English. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void thePokemonJobBuildsAreTranslated(GameTestHelper helper) {
		Language english = Language.getInstance();
		for (StarterBlueprints.Entry entry : StarterBlueprints.JOB_BUILDS) {
			String key = "blueprint." + entry.id().getNamespace() + "." + entry.id().getPath();
			helper.assertTrue(english.has(key), "no English for " + key);
		}
		helper.assertTrue(english.getOrDefault("blueprint.aliveworkplace.camp_kitchen_2").equals("Camp Kitchen II"),
			english.getOrDefault("blueprint.aliveworkplace.camp_kitchen_2"));
		helper.succeed();
	}
}

package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.habitat.VillageHabitats;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.PokemonFeatures;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane d, 2026-10-25) for ROADMAP 28.14 (Cobblemon 1.8.1; on 1.7.3 these pass at once, as the earlier tests do):
 * founding is an Expert's, not a Journeyman's, and needs the garden finished; tending reaches natural Habitat Blocks
 * exactly {@link VillageHabitats#TEND_RANGE} (64) blocks from the keeper's pasture and not one block further, and never
 * a Habitat Block that isn't in natural mode.
 */
public class QaVillageHabitatCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CENTRE = new BlockPos(6, 2, 6);
	private static final BlockPos HALL = new BlockPos(1, 2, 8);

	/** One natural-mode Habitat Block at {@code pos} (its chunk loaded), taken away when the test ends. */
	static void natural(GameTestHelper helper, BlockPos pos, ResourceLocation pool) {
		ServerLevel level = helper.getLevel();
		level.getChunkAt(pos);
		helper.assertTrue(VillageHabitats.place(level, pos, pool), "couldn't place a Habitat Block at " + pos);
		PartnerShowsCompatTests.after(helper, () -> {
			level.removeBlockEntity(pos);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
		});
	}

	/** A Journeyman by a finished garden founds nothing; the same keeper made Expert would; an unfinished garden, nothing. */
	//$ gametest_batch AREA '"qa_village_habitat_rank"'
	@GameTest(template = AREA, batch = "qa_village_habitat_rank")
	public void onlyAnExpertByAFinishedGardenFoundsTheHabitat(GameTestHelper helper) {
		if (!PokemonFeatures.HABITATS.available()) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(CENTRE, Blocks.MOSSY_COBBLESTONE);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		BlockPos pasture = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos centre = helper.absolutePos(CENTRE);
		keeper.setVillagerData(keeper.getVillagerData().setLevel(VillageHabitats.EXPERT));
		helper.assertTrue(VillageHabitats.foundingSpot(level, keeper, pasture) == null, "an Expert founded on a garden not yet finished");
		BlockPos origin = centre.subtract(StarterBlueprints.HABITAT_GARDEN_CENTRE);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.HABITAT_GARDEN.id(), placement, UUID.randomUUID());
		PartnerShowsCompatTests.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		keeper.setVillagerData(keeper.getVillagerData().setLevel(VillageHabitats.EXPERT - 1));
		helper.assertTrue(VillageHabitats.foundingSpot(level, keeper, pasture) == null, "a Journeyman would found the village's habitat");
		keeper.setVillagerData(keeper.getVillagerData().setLevel(VillageHabitats.EXPERT));
		BlockPos spot = VillageHabitats.foundingSpot(level, keeper, pasture);
		helper.assertTrue(centre.equals(spot), "the Expert's founding spot is " + spot + ", not the centre stone " + centre);
		helper.assertTrue(level.getBlockState(centre).is(Blocks.MOSSY_COBBLESTONE), "asking placed something: " + level.getBlockState(centre));
		helper.succeed();
	}

	/** Natural Habitat Blocks 64 blocks from the pasture are tended, 65 are not; one in another mode never is. */
	//$ gametest_batch AREA '"qa_village_habitat_reach"'
	@GameTest(template = AREA, batch = "qa_village_habitat_reach")
	public void tendingReachesSixtyFourBlocksAndNoFurther(GameTestHelper helper) {
		if (!PokemonFeatures.HABITATS.available()) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		BlockPos pasture = helper.absolutePos(new BlockPos(2, 2, 2));
		ResourceLocation pool = VillageHabitats.poolFor(level, pasture);
		helper.assertTrue(pool != null, "no village_habitats pool for the biome here");
		BlockPos at = pasture.above(VillageHabitats.TEND_RANGE);
		BlockPos past = pasture.above(VillageHabitats.TEND_RANGE + 1);
		natural(helper, at, pool);
		natural(helper, past, pool);
		List<BlockPos> tended = VillageHabitats.tended(level, pasture);
		helper.assertTrue(tended.contains(at), "a natural Habitat Block 64 blocks off isn't tended: " + tended);
		helper.assertFalse(tended.contains(past), "a natural Habitat Block 65 blocks off is tended");
		// the same block taken out of natural mode (a player's own habitat) isn't the keeper's
		BlockPos own = pasture.above(10);
		natural(helper, own, pool);
		helper.assertTrue(VillageHabitats.tended(level, pasture).contains(own), "setup: the near block isn't tended while natural");
		var state = level.getBlockState(own);
		var activated = state.getBlock().getStateDefinition().getProperty("activated_style");
		helper.assertTrue(activated instanceof net.minecraft.world.level.block.state.properties.BooleanProperty, "setup: the Habitat Block has no activated_style");
		level.setBlock(own, state.setValue((net.minecraft.world.level.block.state.properties.BooleanProperty) activated, true), 2);
		helper.assertFalse(VillageHabitats.tended(level, pasture).contains(own), "a Habitat Block in its activated style is tended");
		helper.succeed();
	}
}

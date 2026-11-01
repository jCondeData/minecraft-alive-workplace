package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane D, 2026-11-01), 41.1 from the spec: "a small waystone plaza ... in each village culture's style ... the stone
 * is the mod's own waystone block, named after the village, so players who visit can discover it". The other tests
 * raise only the plains plaza's plain waystone. These raise each other culture's own stone (sandy, mud brick, deepslate,
 * mossy) on the hall's round: it is a working waystone named after the village, a player can discover it, and it is
 * still the culture's own block, both halves. (A mutant that registered every stone with Waystones' default style
 * survived the waystone suite: WaystonesCompat:109.)
 */
public class QaWaystoneStyles1101dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(1, 2, 12);
	private static final BlockPos ORIGIN = new BlockPos(9, 2, 3);

	private static BlueprintData.Placement placement(GameTestHelper helper) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
	}

	/** Sets every block of the plaza {@code id} as a finished one stands, and records it finished. */
	private static void paste(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no blueprint " + id));
		BuildPlan plan = BuildPlan.create(blueprint, placement(helper));
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(id, placement(helper), UUID.randomUUID());
	}

	private static void raisesItsOwnStone(GameTestHelper helper, String culture, String block, String name) {
		RaidHelperCompatTests.clearAround(helper);
		VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setCustomName(Component.literal(name));
		BlockPos hall = helper.absolutePos(HALL);
		ResourceLocation plaza = VillageWaystones.plazaFor(culture);
		paste(helper, plaza);
		VillageWaystones.round(level, hall);
		// (only this test's plaza: other batches' plazas within the hall's radius stay recorded)
		List<BlockPos> stones = VillageWaystones.stones(level, hall).stream().filter(p -> helper.getBounds().contains(p.getCenter())).toList();
		helper.assertTrue(stones.size() == 1, "the " + culture + " plaza's stones: " + stones);
		BlockPos stone = stones.get(0);
		ResourceLocation own = ResourceLocation.fromNamespaceAndPath("waystones", block);
		helper.assertTrue(BuiltInRegistries.BLOCK.getKey(level.getBlockState(stone).getBlock()).equals(own)
				&& BuiltInRegistries.BLOCK.getKey(level.getBlockState(stone.above()).getBlock()).equals(own),
			"the " + culture + " stone isn't its own " + own + " any more: " + level.getBlockState(stone) + " / " + level.getBlockState(stone.above()));
		Waystone waystone = WaystonesAPI.getWaystoneAt(level, stone).filter(Waystone::isValid)
			.orElseThrow(() -> new GameTestAssertException("the " + culture + " plaza's " + block + " isn't a working waystone: "
				+ WaystonesAPI.getWaystoneAt(level, stone).map(w -> "valid=" + w.isValid() + " pos=" + w.getPos()).orElse("none")));
		helper.assertTrue(waystone.getName().getString().equals(name), "the " + culture + " stone is called '" + waystone.getName().getString() + "'");
		Optional<String> told = VillageWaystones.EXTENSION.call(s -> s.nameAt(level, stone), Optional.<String>empty());
		helper.assertTrue(told.equals(Optional.of(name)), "the mod reads the " + culture + " stone as " + told);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		WaystonesAPI.activateWaystone(player, waystone);
		boolean discovered = VillageWaystones.EXTENSION.call(s -> s.discovered(player, level, stone), false);
		level.getServer().getPlayerList().remove(player);
		BuildSiteManager.get(level).forgetFinished(placement(helper)); // (so later batches' halls don't count this plaza)
		helper.assertTrue(discovered, "a player who activated the " + culture + " stone hasn't discovered it");
		helper.succeed();
	}

	//$ gametest_batch AREA '"qa1101dStyleDesert"'
	@GameTest(template = AREA, batch = "qa1101dStyleDesert")
	public void theDesertPlazasSandyStoneIsAWorkingNamedWaystone(GameTestHelper helper) {
		raisesItsOwnStone(helper, "desert", "sandy_waystone", "Dunemark");
	}

	//$ gametest_batch AREA '"qa1101dStyleSavanna"'
	@GameTest(template = AREA, batch = "qa1101dStyleSavanna")
	public void theSavannaPlazasMudBrickStoneIsAWorkingNamedWaystone(GameTestHelper helper) {
		raisesItsOwnStone(helper, "savanna", "mud_bricks_waystone", "Acacia Rest");
	}

	//$ gametest_batch AREA '"qa1101dStyleSnowy"'
	@GameTest(template = AREA, batch = "qa1101dStyleSnowy")
	public void theSnowyPlazasDeepslateStoneIsAWorkingNamedWaystone(GameTestHelper helper) {
		raisesItsOwnStone(helper, "snowy", "deepslate_waystone", "Frostholm");
	}

	//$ gametest_batch AREA '"qa1101dStyleTaiga"'
	@GameTest(template = AREA, batch = "qa1101dStyleTaiga")
	public void theTaigaPlazasMossyStoneIsAWorkingNamedWaystone(GameTestHelper helper) {
		raisesItsOwnStone(helper, "taiga", "mossy_waystone", "Pinewatch");
	}
}

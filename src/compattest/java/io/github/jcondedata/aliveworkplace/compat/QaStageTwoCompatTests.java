package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.habitat.VillageHabitats;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA stage 2 (2026-10-23), the config matrix with the real Cobblemon: the one switch no test had turned off,
 * {@code villageHabitats} (ROADMAP 28.14; its comment in the config: "Off: none founded").
 */
public class QaStageTwoCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CENTRE = new BlockPos(6, 2, 6);
	private static final BlockPos HALL = new BlockPos(1, 2, 8);

	/**
	 * {@code villageHabitats} off in the config file: an Expert Habitat Keeper by a finished Habitat Garden founds no
	 * habitat (the centre stone stays moss for 300 ticks on any Cobblemon), and her page doesn't promise one for a
	 * newer Cobblemon either.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa2_habitats_off"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa2_habitats_off")
	public void villageHabitatsOffFoundsNone(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorkplaceConfig.parse("{\"villageHabitats\": false}").apply();
		PartnerShowsCompatTests.after(helper, () -> new WorkplaceConfig().apply());
		BlockPos origin = helper.absolutePos(CENTRE).subtract(StarterBlueprints.HABITAT_GARDEN_CENTRE);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		helper.setBlock(CENTRE, Blocks.MOSSY_COBBLESTONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.HABITAT_GARDEN.id(), placement, UUID.randomUUID());
		PartnerShowsCompatTests.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BlockPos centre = helper.absolutePos(CENTRE);
		PartnerShowsCompatTests.after(helper, () -> {
			level.removeBlockEntity(centre);
			level.setBlock(centre, Blocks.AIR.defaultBlockState(), 3);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		Villager keeper = HabitatKeeperCompatTests.keeper(helper);
		keeper.setVillagerData(keeper.getVillagerData().setLevel(VillageHabitats.EXPERT));
		helper.assertTrue(VillageHabitats.foundingSpot(level, keeper, helper.absolutePos(new BlockPos(2, 2, 2))) == null,
			"a founding spot with villageHabitats off");
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(level.getBlockState(centre).is(Blocks.MOSSY_COBBLESTONE),
				"villageHabitats off: the centre stone became " + level.getBlockState(centre));
			List<Component> lines = VillageHabitats.hallLines(keeper);
			helper.assertTrue(lines.stream().noneMatch(l -> l.getString().contains("Cobblemon 1.8")),
				"villageHabitats off: her page still says " + lines);
			helper.succeed();
		});
	}
}

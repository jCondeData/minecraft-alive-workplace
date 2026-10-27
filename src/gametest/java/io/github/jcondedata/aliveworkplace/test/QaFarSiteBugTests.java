package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.SupplyReport;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA (lane E, 2026-10-27), 36.20: "a spot is in reach when it is inside the village's claimed area ... wherever the
 * worker's block stands in it". The builder takes a build 150 blocks from his Blueprint Table across a big village, but
 * the Blueprint's own supply report (its tooltip, "No Blueprint Table within 128 blocks of this spot") and its material
 * list (Book and Quill) still look for a table only within 128 blocks of the spot (BlueprintSupplies.check/checklist).
 */
public class QaFarSiteBugTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos WORK = new BlockPos(3, 2, 3);
	private static final BlockPos AT = new BlockPos(4, 2, 4);

	//$ gametest_ticks_batch AREA '200' '"qaFarSiteSupplies"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaFarSiteSupplies")
	public void aBuildTheBuilderTakesAcrossABigVillageFindsHisTableForItsSupplies(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			VillageHalls.forgetNearest();
		});
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.RADIUS = 160; // the largest villageHallRadius the config allows
		VillageHalls.forgetNearest();
		helper.setBlock(WORK, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, AT);
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(WORK);
		Builders.employ(level, builder, bench);
		Leftovers.after(helper, () -> {
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (builder.getUUID().equals(site.builder())) {
					sites.remove(site.id());
				}
			}
		});
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("farS"));
		ItemStack blueprint = QaBuilderLimitsGameTests.placedHut(level, bench, new Vec3i(150, 0, 0));
		var data = BlueprintItem.data(blueprint).orElseThrow();

		// what the Blueprint says before it is handed over
		Optional<SupplyReport> report = BlueprintSupplies.check(level, data);
		helper.assertTrue(report.isPresent(), "no supply report for the placed Blueprint");
		helper.assertTrue(report.get().bench().equals(Optional.of(bench)),
			"the Blueprint placed 150 blocks from the Blueprint Table, inside the village (radius 160), reports the table " + report.get().bench()
				+ " (the tooltip then says 'No Blueprint Table within " + Builders.MAX_SITE_DISTANCE + " blocks of this spot')");
		BlueprintSupplies.Checklist list = BlueprintSupplies.checklist(level, data).orElseThrow();
		helper.assertTrue(list.bench().equals(Optional.of(bench)), "the material list for it counts no table's chests: " + list.bench());

		// and the builder does take it
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		helper.assertTrue(Builders.activeSite(level, builder) != null, "the builder refused the build 150 off in the village: "
			+ c.seen().stream().map(Component::getString).toList());
		helper.succeed();
	}
}

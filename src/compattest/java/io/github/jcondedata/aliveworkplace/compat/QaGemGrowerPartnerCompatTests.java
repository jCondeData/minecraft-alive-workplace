package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.Partners;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane d, 2026-10-27) of ROADMAP 28.11 with the real Cobblemon: "Partner types: Rock and Steel; show: a Rock partner
 * taps the ripe cluster loose". Not covered before: the Gem Grower's partner types, and a pastured Geodude (Rock) playing
 * the cluster-tapping show when she picks a full amethyst cluster, the budding block left standing.
 */
public class QaGemGrowerPartnerCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos CUTTER = new BlockPos(2, 2, 2);
	private static final BlockPos BUDDING = new BlockPos(6, 2, 6);

	/** A Geodude partner taps the ripe cluster loose as she picks it; the budding block stays. */
	//$ gametest_ticks_batch AREA '2400' '"qaGemPartnerTap"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qaGemPartnerTap")
	public void qaARockPartnerTapsTheRipeClusterLoose(GameTestHelper helper) {
		helper.assertTrue(Partners.types(ModVillagers.GEM_GROWER).equals(Set.of("rock", "steel")),
			"the Gem Grower's partner types: " + Partners.types(ModVillagers.GEM_GROWER));
		PartnerShowsCompatTests.showsOn(helper);
		helper.setDayTime(2000);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		helper.setBlock(BUDDING, Blocks.BUDDING_AMETHYST);
		Villager grower = helper.spawn(EntityType.VILLAGER, CUTTER.offset(1, 0, 1));
		Jobs.employ(helper.getLevel(), grower, helper.absolutePos(CUTTER), PoiTypes.MASON, ModVillagers.GEM_GROWER);
		PartnersAtWorkCompatTests.partner(helper, new BlockPos(13, 2, 3), "geodude");
		Set<ResourceLocation> started = PartnersAtWorkCompatTests.watch(helper, grower, "geodude", new HashSet<>());
		// the cluster ripens once her partner is out of its ball, so the pick can't come before it
		helper.runAfterDelay(80, () -> helper.setBlock(BUDDING.above(),
			Blocks.AMETHYST_CLUSTER.defaultBlockState().setValue(AmethystClusterBlock.FACING, Direction.UP)));
		ResourceLocation tap = AliveWorkplace.id("gem_grower_rock_taps_the_cluster_loose");
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 80, "too early");
			helper.assertTrue(started.contains(tap), "no tapping show: " + started + " (" + PartnerShows.lastRefusal() + ")");
			helper.assertTrue(helper.getBlockState(BUDDING).is(Blocks.BUDDING_AMETHYST), "the budding block was broken");
			helper.assertTrue(!helper.getBlockState(BUDDING.above()).is(Blocks.AMETHYST_CLUSTER), "the cluster wasn't picked");
		});
	}
}

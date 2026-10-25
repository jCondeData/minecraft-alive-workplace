package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * Bug test (lane-b-1025-0337), from 37.7's spec ("the Spruce style builds Carved Wood's spruce ... lanterns") and the
 * builder's promise that a build with its materials comes out whole: a builder builds the Starter Cottage in the Spruce
 * style, with Carved Wood installed and his chests holding exactly the plan's materials. Every block must stand and none
 * be skipped, as the Spruce Library (CarvedStylesCompatTests) and the Grand cottage are. Found: the two Carved Wood
 * spruce lanterns standing on the flower boxes by the door (blueprint 3,2,1 and 7,2,1, on spruce trapdoors) are skipped
 * and the cottage is finished without them.
 */
public class QaSpruceCottageBugTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";

	//$ gametest_ticks_batch HUGE_AREA '24000' '"qa_spruce_cottage"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "qa_spruce_cottage")
	public void theSpruceCottageIsBuiltWholeWithItsLanterns(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		new WorkplaceConfig().apply();
		ResourceLocation id = BlueprintStyles.styled(AliveWorkplace.id("starter_cottage"), "spruce");
		Blueprint styled = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no " + id));
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos chest = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(chest, Blocks.CHEST);
			Container container = helper.getBlockEntity(chest);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		BlockPos origin = placement.origin();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty() && site.skipped() == 0, "the Spruce cottage was finished with " + site.skipped() + " skipped and "
				+ unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3).map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p).getBlock()
					+ " (wants " + styled.blocks().stream().filter(e -> origin.offset(e.pos()).equals(p)).map(e -> e.state().toString()).findFirst().orElse("?")
					+ " on " + level.getBlockState(p.below()) + ")").toList());
		});
	}
}

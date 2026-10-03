package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B31: in a village of builders, a builder short of something took it from another builder's chests, which held exactly
 * what that builder's own build needed (the soak's inn was left waiting for 3 glass panes), and counted another builder's
 * whole stock as its own when working out what it was missing (waiting with nothing missing). A builder takes only what
 * the other builder's builds can spare.
 */
public class BuildersShareSpareGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos HUT = new BlockPos(6, 2, 6);
	private static final BlockPos OTHER_BENCH = new BlockPos(26, 2, 2);
	private static final BlockPos OTHER_CHEST = new BlockPos(26, 2, 4);
	private static final BlockPos OTHER_HUT = new BlockPos(18, 2, 18);

	private record Pair(BuildSite site, BuildSite otherSite, Container otherChest) {
	}

	/** The other builder's chest holds exactly its own hut's materials: our builder, one cobblestone short, waits for it. */
	//$ gametest_ticks_batch HUGE_AREA '3000' '"b31_reserved"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 3000, batch = "b31_reserved")
	public void b31ABuilderLeavesWhatAnotherBuildersHutNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Pair p = setup(helper, 25);
		helper.succeedWhen(() -> {
			helper.assertTrue(p.otherChest().countItem(Items.COBBLESTONE) == 25,
				"took the other builder's cobblestone: " + p.otherChest().countItem(Items.COBBLESTONE) + " of 25 left");
			helper.assertTrue(p.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS,
				"not waiting yet: " + p.site().status() + " " + p.site().stage() + " placed " + p.site().placed());
			helper.assertTrue(Integer.valueOf(1).equals(p.site().missing().get(Items.COBBLESTONE)),
				"should be missing exactly 1 cobblestone, missing " + p.site().missing());
		});
	}

	/** Five cobblestone more than the other builder's hut needs: our builder takes its one from that spare and finishes. */
	//$ gametest_ticks_batch HUGE_AREA '3000' '"b31_spare"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 3000, batch = "b31_spare")
	public void b31ABuilderTakesWhatAnotherBuilderCanSpare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Pair p = setup(helper, 30);
		helper.succeedWhen(() -> {
			helper.assertTrue(p.otherChest().countItem(Items.COBBLESTONE) >= 25,
				"took what the other builder's hut needs: " + p.otherChest().countItem(Items.COBBLESTONE) + " left");
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(p.site().id()) == null,
				"still building: " + p.site().status() + " " + p.site().stage() + " missing " + p.site().missing());
		});
	}

	/**
	 * Our builder has the hut's materials but one cobblestone; another builder, kept still (no AI) so its build stays
	 * unstarted, has its own hut to build and {@code otherCobblestone} cobblestone with the rest of its materials.
	 */
	private static Pair setup(GameTestHelper helper, int otherCobblestone) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());

		helper.setBlock(OTHER_BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(OTHER_CHEST, Blocks.CHEST);
		Container otherChest = helper.getBlockEntity(OTHER_CHEST);
		fill(otherChest, new ItemStack(Items.COBBLESTONE, otherCobblestone), new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH));
		Villager other = helper.spawn(EntityType.VILLAGER, OTHER_BENCH.south().east());
		Builders.employ(level, other, helper.absolutePos(OTHER_BENCH));
		BuildSite otherSite = Builders.start(level, other, null, TEST_HUT, placement(helper, OTHER_HUT));
		other.setNoAi(true);

		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		fill(helper.getBlockEntity(CHEST), new ItemStack(Items.COBBLESTONE, 24), new ItemStack(Items.OAK_PLANKS, 55),
			new ItemStack(Items.OAK_DOOR), new ItemStack(Items.TORCH));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, TEST_HUT, placement(helper, HUT));
		helper.assertTrue(otherSite != null && site != null, "a build didn't start");
		return new Pair(site, otherSite, otherChest);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}

	private static void fill(Container container, ItemStack... items) {
		for (int i = 0; i < items.length; i++) {
			container.setItem(i, items[i].copy());
		}
	}
}

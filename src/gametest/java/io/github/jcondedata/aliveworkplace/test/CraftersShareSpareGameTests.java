package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B33: a crafter helping one builder took the ingredients from another builder's chests, even what that builder's own
 * build still needed (B31's cause in CrafterWork). From another builder's chests a crafter takes only what is spare.
 */
public class CraftersShareSpareGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos CUTTER = new BlockPos(8, 2, 2);
	private static final BlockPos WALL = new BlockPos(6, 2, 12);
	private static final BlockPos OTHER_BENCH = new BlockPos(26, 2, 2);
	private static final BlockPos OTHER_CHEST = new BlockPos(26, 2, 4);
	private static final BlockPos OTHER_WALL = new BlockPos(18, 2, 20);
	private static final BlockPos TEMPLATE_SOURCE = new BlockPos(12, 2, 26);

	private record Setup(BuildSite site, Container otherChest, Villager mason) {
	}

	/** The other builder's chest holds exactly the andesite its own wall needs: the mason leaves it, and our builder waits. */
	//$ gametest_ticks_batch HUGE_AREA '1600' '"b33_reserved"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1600, batch = "b33_reserved")
	public void b33AMasonLeavesTheAndesiteAnotherBuildersWallNeeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Setup s = setup(helper, 3);
		helper.runAtTickTime(1200, () -> {
			helper.assertTrue(s.otherChest().countItem(Items.ANDESITE) == 3,
				"the mason took the other builder's andesite: " + s.otherChest().countItem(Items.ANDESITE) + " of 3 left");
			helper.assertTrue(ModAttachments.ITEMS_CRAFTED.getOrElse(s.mason(), 0) == 0,
				"the mason cut " + ModAttachments.ITEMS_CRAFTED.getOrElse(s.mason(), 0) + " from reserved andesite");
			helper.assertTrue(s.site().status() == BuildSite.Status.WAITING_FOR_MATERIALS,
				"not waiting: " + s.site().status() + " " + s.site().stage() + " missing " + s.site().missing());
			helper.succeed();
		});
	}

	/** Three andesite more than the other builder's wall needs: the mason cuts our builder's polished andesite from that spare. */
	//$ gametest_ticks_batch HUGE_AREA '2400' '"b33_spare"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 2400, batch = "b33_spare")
	public void b33AMasonCutsFromWhatAnotherBuilderCanSpare(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.village(helper, 48);
		Setup s = setup(helper, 6);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(s.site().id()) == null,
				"still building: " + s.site().status() + " " + s.site().stage() + " missing " + s.site().missing());
			helper.assertTrue(s.otherChest().countItem(Items.ANDESITE) == 3,
				"the other builder's wall needs 3 andesite, " + s.otherChest().countItem(Items.ANDESITE) + " left");
		});
	}

	/**
	 * Our builder builds a wall of 3 polished andesite with none in its chest; a mason works a stonecutter by its bench.
	 * Another builder, kept still (no AI) so its build stays unstarted, builds a wall of 3 andesite and has
	 * {@code otherAndesite} andesite in its chest.
	 */
	private static Setup setup(GameTestHelper helper, int otherAndesite) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		String suffix = Long.toHexString(level.getGameTime()) + "_" + otherAndesite;
		ResourceLocation wall = wall(helper, Blocks.POLISHED_ANDESITE, "b33_polished_" + suffix);
		ResourceLocation otherWall = wall(helper, Blocks.ANDESITE, "b33_andesite_" + suffix);

		helper.setBlock(OTHER_BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(OTHER_CHEST, Blocks.CHEST);
		Container otherChest = helper.getBlockEntity(OTHER_CHEST);
		otherChest.setItem(0, new ItemStack(Items.ANDESITE, otherAndesite));
		Villager other = helper.spawn(EntityType.VILLAGER, OTHER_BENCH.south().east());
		Builders.employ(level, other, helper.absolutePos(OTHER_BENCH));
		BuildSite otherSite = Builders.start(level, other, null, otherWall, placement(helper, OTHER_WALL));
		other.setNoAi(true);

		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, wall, placement(helper, WALL));
		helper.assertTrue(otherSite != null && site != null, "a build didn't start");

		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, CUTTER.south());
		Jobs.employ(level, mason, helper.absolutePos(CUTTER), PoiTypes.MASON, VillagerProfession.MASON);
		return new Setup(site, otherChest, mason);
	}

	/** A 3-block wall of {@code block}, saved as a template from the corner of the area and cleared again. */
	private static ResourceLocation wall(GameTestHelper helper, Block block, String name) {
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 3; x++) {
			helper.setBlock(TEMPLATE_SOURCE.offset(x, 0, 0), block);
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", name);
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(TEMPLATE_SOURCE), new Vec3i(3, 1, 1), false,
			Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			helper.setBlock(TEMPLATE_SOURCE.offset(x, 0, 0), Blocks.AIR);
		}
		return id;
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos origin) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
	}
}

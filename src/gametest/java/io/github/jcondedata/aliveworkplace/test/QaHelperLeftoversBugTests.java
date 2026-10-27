package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaHelperLeftoversGameTests.bagEmpty;
import static io.github.jcondedata.aliveworkplace.test.QaHelperLeftoversGameTests.count;
import static io.github.jcondedata.aliveworkplace.test.QaHelperLeftoversGameTests.in;
import static io.github.jcondedata.aliveworkplace.test.QaHelperLeftoversGameTests.prepare;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
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
 * BUG B645 (QA lane E, 2026-10-27; fails on main 9a2b57f2a, 2 runs of 2): helpers leave a lead's materials in their
 * own chest. Found by the builder soak on a real pack server (a builder stuck for good waiting for 2 lanterns, 5 in two
 * other builders' chests); the wiki (builder.md) says a helper "empties the bag at the chests when it finishes or stops
 * helping", and {@code Builders.stopHelping} "hands back anything fetched for the site". Fails with: "the helpers' own
 * chest got 14 of the lead's materials ({lantern=1, spruce_trapdoor=11, flower_pot=1, fern=1})". Lives on
 * tests/helper-leftovers-1027 until the fix lands with it.
 */
public class QaHelperLeftoversBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/**
	 * The soak's own build: the Fisher's Hut (13 x 13, two lanterns), the lead's chest holding twice its materials, two
	 * helpers with their own chest 23 blocks off. Once it's built, the spare is all in the lead's chest, none in theirs.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:huge_area"' '12000' '"qaHelperLeftoversFishersHut"'
	@GameTest(template = "aliveworkplace_test:huge_area", timeoutTicks = 12000, batch = "qaHelperLeftoversFishersHut")
	public void theFishersHutsSpareLanternsStayInTheLeadsChest(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(2, 2, 2);
		BlockPos[] chests = {new BlockPos(2, 2, 4), new BlockPos(4, 2, 2)};
		BlockPos[] mateTables = {new BlockPos(25, 2, 2), new BlockPos(27, 2, 2)};
		BlockPos matesChestAt = new BlockPos(26, 2, 4);
		helper.setBlock(table, ModBlocks.BUILDERS_BENCH);
		for (BlockPos c : chests) {
			helper.setBlock(c, Blocks.CHEST);
		}
		helper.setBlock(matesChestAt, Blocks.CHEST);
		Villager lead = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, lead, helper.absolutePos(table));
		Villager[] mates = new Villager[mateTables.length];
		for (int i = 0; i < mates.length; i++) {
			helper.setBlock(mateTables[i], ModBlocks.BUILDERS_BENCH);
			mates[i] = helper.spawn(EntityType.VILLAGER, mateTables[i].south());
			Builders.employ(level, mates[i], helper.absolutePos(mateTables[i]));
		}
		ResourceLocation hut = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "fishers_hut");
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(8, 2, 8)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, lead, null, hut, placement);
		helper.assertTrue(site != null, "setup: the Fisher's Hut didn't start");
		BuildPlan plan = site.plan(level);
		helper.assertTrue(plan != null, "setup: no plan for the Fisher's Hut");
		Map<Item, Integer> materials = plan.materials();
		helper.assertTrue(materials.getOrDefault(net.minecraft.world.item.Items.LANTERN, 0) > 0, "setup: the Fisher's Hut needs no lantern: " + materials);
		Container[] leadChests = {helper.getBlockEntity(chests[0]), helper.getBlockEntity(chests[1])};
		Container matesChest = helper.getBlockEntity(matesChestAt);
		int slot = 0;
		for (Map.Entry<Item, Integer> e : materials.entrySet()) {
			int left = e.getValue() * 2;
			while (left > 0) {
				int n = Math.min(left, e.getKey().getDefaultMaxStackSize());
				leadChests[slot / 27].setItem(slot % 27, new ItemStack(e.getKey(), n));
				slot++;
				left -= n;
			}
		}
		helper.assertTrue(slot <= 54, "setup: twice the hut's materials don't fit in two chests (" + slot + " stacks)");
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		boolean[] helped = {false};
		int[] most = {0};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			for (Villager mate : mates) {
				helped[0] |= Builders.isHelping(mate);
			}
			most[0] = Math.max(most[0], count(matesChest, materials));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "the hut isn't finished: stage " + site.stage() + ", status "
				+ site.status() + ", missing " + site.missing());
			for (Villager v : mates) {
				helper.assertTrue(!Builders.isHelping(v) && bagEmpty(v), "a helper is still helping or carrying");
			}
			helper.assertTrue(bagEmpty(lead), "the lead's bag isn't empty");
			helper.assertTrue(helped[0], "no idle builder ever helped");
			helper.assertTrue(most[0] == 0, "the helpers' own chest got " + most[0] + " of the lead's materials (" + in(matesChest, materials)
				+ "), out of the lead's reach");
			int lanterns = leadChests[0].countItem(net.minecraft.world.item.Items.LANTERN) + leadChests[1].countItem(net.minecraft.world.item.Items.LANTERN);
			helper.assertTrue(lanterns == materials.get(net.minecraft.world.item.Items.LANTERN), "the lead's chests hold " + lanterns
				+ " lanterns, not the spare " + materials.get(net.minecraft.world.item.Items.LANTERN));
			LOGGER.info("qa helper leftovers: the Fisher's Hut built, the spare {} lanterns in the lead's chests", lanterns);
		});
	}
}

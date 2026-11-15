package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.compat.QaCrafterOwnModdedStore1111eCompatTests.Asker;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-15), Create loaded (the owner's 2.3 pack): chains for a builder. Create adds a second chain recipe
 * (two Zinc Nuggets and a Zinc Ingot, create:crafting/appliances/chain_from_zinc), and the Tinkerer's forge plans chains
 * (crafts/tinkerer.json lists minecraft:chain). The spec (crafter.md, B408/B489): the line names what the chest is short
 * of, the raw metal a player mines, not some other way to make it; the Tinkerer makes "raw iron fired into ingots, then
 * bars or a lantern". The line when the iron is short is B1268's QaCreateChain1115eBugCompatTests.
 */
public class QaCreateChain1115eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private static Villager tinkerer(GameTestHelper helper) {
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.TINKERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(station), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		return v;
	}

	/** Exactly enough iron for 3 chains (3 ingots and 6 nuggets) in the builder's chest: the Tinkerer makes them, all used. */
	//$ gametest_ticks_batch AREA '5000' '"qaCreateChain1115eMade"'
	@GameTest(template = AREA, timeoutTicks = 5000, batch = "qaCreateChain1115eMade")
	public void withCreateTheTinkererMakesChainsFromIron(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.CHAIN, 3, 1, "chainmade"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.IRON_INGOT, 3), new ItemStack(Items.IRON_NUGGET, 6));
		Villager t = tinkerer(helper);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(helper.getLevel()).get(a.site().id()) == null, "still building; line '"
				+ QaCrafterOwnModdedStore1111eCompatTests.line(helper, a) + "'; the Tinkerer made " + ModAttachments.ITEMS_CRAFTED.getOrElse(t, 0));
			Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 0 && chest.countItem(Items.IRON_NUGGET) == 0, "the iron should be used up: "
				+ chest.countItem(Items.IRON_INGOT) + " ingots, " + chest.countItem(Items.IRON_NUGGET) + " nuggets");
		});
	}
}

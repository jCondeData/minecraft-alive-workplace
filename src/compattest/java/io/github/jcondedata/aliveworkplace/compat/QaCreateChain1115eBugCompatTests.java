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
 * bars or a lantern". Failing: with Create the line is empty (null) while the builder waits for 4
 * chains; the plain suite names the Raw Iron (QaTinkererChain1115eGameTests). Moves to main with the fix.
 */
public class QaCreateChain1115eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	private static Villager tinkerer(GameTestHelper helper) {
		BlockPos station = QaCrafterOwnModdedStore1111eCompatTests.STATION;
		helper.setBlock(station, ModBlocks.TINKERS_BENCH);
		Villager v = helper.spawn(EntityType.VILLAGER, station.south());
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(station), ModVillagers.TINKERS_BENCH_POI, ModVillagers.TINKERER);
		return v;
	}

	/** A coal and no iron, 4 chains short: the builder's line names Raw Iron, never Zinc or a Crushed ore; nothing taken. */
	//$ gametest_ticks_batch AREA '1400' '"qaCreateChain1115eLine"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCreateChain1115eLine")
	public void withCreateAChainShortNamesRawIronNotZinc(GameTestHelper helper) {
		QaCrafterOwnModdedStore1111eCompatTests.prepare(helper);
		Asker a = QaCrafterOwnModdedStore1111eCompatTests.asker(helper, QaCrafterOwnModdedStore1111eCompatTests.area(helper, Blocks.CHAIN, 2, 2, "chainline"),
			Blocks.CHEST.defaultBlockState(), new ItemStack(Items.COAL, 1));
		Villager t = tinkerer(helper);
		helper.runAfterDelay(1200, () -> {
			String text = QaCrafterOwnModdedStore1111eCompatTests.line(helper, a);
			helper.assertTrue(text != null && text.contains("Raw Iron") && !text.contains("Zinc") && !text.contains("Crushed"),
				"the builder's line: '" + text + "', expected it to name the Raw Iron short (no Zinc, no Crushed ore); site "
				+ site(helper, a));
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(t).isEmpty(), "the Tinkerer took something");
			helper.succeed();
		});
	}

	private static String site(GameTestHelper helper, Asker a) {
		io.github.jcondedata.aliveworkplace.build.BuildSite live = BuildSiteManager.get(helper.getLevel()).get(a.site().id());
		return live == null ? "done" : live.stage() + "/" + live.status() + " missing " + live.missing();
	}

}

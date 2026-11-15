package io.github.jcondedata.aliveworkplace.compat;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane E, 2026-11-15), a bug: in the owner's pack (Cobbleverse 1.7.42, Carved Wood 1.9.7-B with its pack config)
 * no shaped recipe makes a plain minecraft:chest. Planks make a carved chest of their wood (8 oak planks: a
 * carved_wood:oak_chest), and Carved Wood's special chest_from_modded_chests recipe turns any carved chest into a plain
 * one, so a player at a crafting table gets a plain chest from 8 oak planks in two crafts. A builder whose blueprint
 * asks for a chest, with 8 oak planks in his chest and a Carpenter hired, waits forever and reads "Nobody here makes:
 * Chest" (crafter.md: a builder short of something that can be crafted gets it from the Carpenter; builder.md: the
 * builder tells you what is missing). Expected: the chest (plain, or the carved oak one a player would make) is built.
 * A carved oak chest given to the builder isn't taken for it either. See QaCarvedChest1115eCompatTests for the barrel, which works.
 */
public class QaCarvedChest1115eBugCompatTests implements FabricGameTest {
	private static final String AREA = QaCarvedChest1115eCompatTests.AREA;

	/**
	 * Nor is there a workaround short of the player's own second craft: a carved oak chest (what 8 oak planks make in the
	 * pack) put in the builder's chest isn't taken for the plain chest the blueprint asks for; same line, waits forever.
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaCarvedChest1115eCarvedGiven"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaCarvedChest1115eCarvedGiven")
	public void aCarvedOakChestInTheSupplyIsBuiltForAPlainChest(GameTestHelper helper) {
		net.minecraft.world.level.block.Block carved = QaCarvedChest1115eCompatTests.block("carved_wood:oak_chest");
		QaCarvedChest1115eCompatTests.run(helper, Blocks.CHEST, carved, "carvedgiven", new ItemStack(carved.asItem(), 1));
	}

	/** A plain chest asked, 8 oak planks in the builder's chest, a Carpenter hired: a chest (plain or carved oak) is built. */
	//$ gametest_ticks_batch AREA '3000' '"qaCarvedChest1115eChest"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "qaCarvedChest1115eChest")
	public void aPlainChestAskedWithOakPlanksAndACarpenterIsBuilt(GameTestHelper helper) {
		QaCarvedChest1115eCompatTests.run(helper, Blocks.CHEST, QaCarvedChest1115eCompatTests.block("carved_wood:oak_chest"), "chest",
			new ItemStack(Items.OAK_PLANKS, 8));
	}
}

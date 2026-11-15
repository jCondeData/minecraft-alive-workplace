package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.SPOT;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.block;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverContents1110eCompatTests.set;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateMoverCrateToolbox1114e2CompatTests.onACrate;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane e, 2026-11-14 evening), the fix for B1246/B1252 (748f0f194) with Create: when a packed block's new spot holds a
 * Mover's Crate boxed in by containers (no spot to put it up for a moment), MoveSite.dropPacked's last resort drops "every
 * item saved anywhere in the data". Create saves ghost items there too: a Toolbox's {@code filters} (a 1-count copy of what
 * each compartment holds) and a FilteringBehaviour's {@code Filter} (a Smart Chute or funnel set to an item, which a player
 * sets by clicking with that item and never puts in). Breaking those blocks by hand drops no ghost (Create's
 * FilteringBehaviour.destroy drops only a real filter item). So the last resort makes items from nothing: a toolbox with 8
 * iron ingots ends as 9, an empty Smart Chute set to diamonds drops a diamond nobody put in. B1172: nothing packed is deleted, and
 * a move must not make anything either.
 */
public class QaCreateMoverCrateGhost1114e2BugCompatTests implements FabricGameTest {
	static final String AREA = "aliveworkplace_compat:huge_area";

	/** A Toolbox holding 8 iron ingots, its crate in a storage room: the toolbox and 8 ingots, not 9 (its compartment's filter). */
	//$ gametest_ticks_batch AREA '400' '"qa_e1114b_crate_toolbox_storeroom"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1114b_crate_toolbox_storeroom")
	public void aToolboxOnACrateAmongContainersIsDroppedWithItsPartsOnce(GameTestHelper helper) {
		onACrate(helper, "toolbox_storeroom", block("create:brown_toolbox").defaultBlockState(), "create:brown_toolbox", Items.IRON_INGOT, 8, true);
	}

	/**
	 * A Smart Chute set to diamonds (its filter a ghost), empty, its crate in a storage room: the chute and no diamond.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1114b_crate_chute_storeroom"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1114b_crate_chute_storeroom")
	public void aSmartChuteSetToDiamondsOnACrateAmongContainersMakesNoDiamond(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockState chute = block("create:smart_chute").defaultBlockState();
		BlockPos at = helper.absolutePos(SPOT);
		set(level, at, chute);
		BlockEntity be = level.getBlockEntity(at);
		helper.assertTrue(be != null, "setup: no smart chute block entity at " + at);
		CompoundTag data = be.saveWithoutMetadata(level.registryAccess());
		data.put("Filter", new ItemStack(Items.DIAMOND).save(level.registryAccess()));
		be.loadWithComponents(data, level.registryAccess());
		be.setChanged();
		helper.assertTrue(be.saveWithoutMetadata(level.registryAccess()).getCompound("Filter").getString("id").equals("minecraft:diamond"),
			"setup: the chute's filter isn't diamonds: " + be.saveWithoutMetadata(level.registryAccess()));
		onACrate(helper, "chute_storeroom", chute, "create:smart_chute", Items.DIAMOND, 0, true);
	}
}

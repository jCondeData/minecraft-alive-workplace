package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane D, 2026-11-10): 41.1's "the stone ... named after the village (renamed when the village is)" with "the stone
 * broken (the builder repairs it like any build)", together: the village is renamed while its stone lies broken, so
 * there is no stone to rename then; the stone the builder puts back carries the village's new name, not the old one.
 * Uses {@link WaystonesCompatTests}' plaza and hall.
 */
public class QaWaystoneRenamedBroken1110dCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	//$ gametest_ticks_batch AREA '1600' '"qaWaystoneRenamedBroken1110d"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qaWaystoneRenamedBroken1110d")
	public void aStoneRepairedAfterTheVillageWasRenamedCarriesTheNewName(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper); // B541: halls and stones earlier batches left nearby
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean was = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		PartnerShowsCompatTests.after(helper, () -> {
			// The plaza recorded finished here and its stone would hold the next batch's plaza rule (WaystonesCompatTests).
			io.github.jcondedata.aliveworkplace.build.BuildSiteManager.get(level).forgetFinished(WaystonesCompatTests.placement(helper));
			level.setBlock(WaystonesCompatTests.stone(helper).above(), Blocks.AIR.defaultBlockState(), 3);
			level.setBlock(WaystonesCompatTests.stone(helper), Blocks.AIR.defaultBlockState(), 3);
			level.removeBlock(helper.absolutePos(WaystonesCompatTests.HALL), false);
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			Upkeep.ENABLED = was;
			Upkeep.ASK = asks;
		});
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		VillageHallBlockEntity hall = WaystonesCompatTests.hall(helper, "Ashford");
		helper.setBlock(WaystonesCompatTests.BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new net.minecraft.core.BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(WaystonesCompatTests.BENCH));
		WaystonesCompatTests.paste(helper, VillageWaystones.PLAZA, builder.getUUID());
		VillageWaystones.round(level, helper.absolutePos(WaystonesCompatTests.HALL));
		helper.assertTrue(WaystonesCompatTests.nameAt(helper).equals(Optional.of("Ashford")), "setup: the stone before it was broken: "
			+ WaystonesCompatTests.nameAt(helper) + " " + WaystonesCompatTests.debug(helper));
		BlockState lower = level.getBlockState(WaystonesCompatTests.stone(helper));
		level.setBlock(WaystonesCompatTests.stone(helper).above(), Blocks.AIR.defaultBlockState(), 3);
		level.setBlock(WaystonesCompatTests.stone(helper), Blocks.AIR.defaultBlockState(), 3);
		helper.assertTrue(WaystonesCompatTests.nameAt(helper).isEmpty(), "setup: a broken stone is still a waystone");
		hall.setCustomName(Component.literal("Brightwater")); // renamed while the stone lies broken
		VillageWaystones.round(level, helper.absolutePos(WaystonesCompatTests.HALL));
		helper.setBlock(WaystonesCompatTests.CHEST, Blocks.CHEST);
		Container chest = helper.getBlockEntity(WaystonesCompatTests.CHEST);
		chest.setItem(0, new ItemStack(lower.getBlock().asItem()));
		Upkeep.ENABLED = true;
		Upkeep.ASK = false; // 36.15: repairs without asking
		helper.startSequence()
			.thenExecuteAfter(2, () -> {
				BuildSite site = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
				helper.assertTrue(site != null && site.isRepair(), "setup: no repair started");
			})
			.thenWaitUntil(() -> {
				helper.assertTrue(VillageWaystones.isStone(level.getBlockState(WaystonesCompatTests.stone(helper))), "no stone yet");
				VillageWaystones.round(level, helper.absolutePos(WaystonesCompatTests.HALL));
				helper.assertTrue(WaystonesCompatTests.nameAt(helper).equals(Optional.of("Brightwater")),
					"the stone put back after the village was renamed Brightwater is called " + WaystonesCompatTests.nameAt(helper));
			})
			.thenSucceed();
	}
}

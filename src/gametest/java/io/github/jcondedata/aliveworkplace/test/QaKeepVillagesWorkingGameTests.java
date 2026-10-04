package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.KeepLoaded;
import io.github.jcondedata.aliveworkplace.work.WorkSites;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * QA for 23.6 (qa-1004-0833), from the spec: a config option, default "keep working", so a server owner can still
 * choose to pause; the game rule {@code workplaceKeepWorkLoaded false} keeps nothing loaded (README); and no
 * chunk-loading surprises.
 */
public class QaKeepVillagesWorkingGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** The option's default is to keep working, a config file without it keeps working, and false pauses. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theConfigOptionDefaultsToKeepWorking(GameTestHelper helper) {
		helper.assertTrue(new WorkplaceConfig().keepVillagesWorking, "a new config doesn't keep villages working");
		helper.assertTrue(WorkplaceConfig.parse("{}").keepVillagesWorking, "a config file without the option doesn't keep villages working");
		helper.assertTrue(WorkplaceConfig.parse("{\"maxBuilders\": 3}").keepVillagesWorking, "an older config file doesn't keep villages working");
		helper.assertFalse(WorkplaceConfig.parse("{\"keepVillagesWorking\": false}").keepVillagesWorking, "false in the file doesn't pause villages");
		helper.assertTrue(WorkplaceConfig.parse("{\"keepVillagesWorking\": true}").keepVillagesWorking, "true in the file doesn't keep villages working");
		helper.succeed();
	}

	/**
	 * With the game rule off, a far village from the saved list is not loaded, though the config keeps villages working
	 * and a player is online: the README says the rule keeps nothing loaded. Its control twin is
	 * {@code KeepVillagesWorkingGameTests.aFarVillageIsLoadedFromTheSavedList}.
	 */
	//$ gametest_ticks_batch AREA '360' '"qaKeepVillagesRuleOff"'
	@GameTest(template = AREA, timeoutTicks = 360, batch = "qaKeepVillagesRuleOff")
	public void theGameRuleOffKeepsNoVillageLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		boolean before = KeepLoaded.VILLAGES;
		KeepLoaded.VILLAGES = true;
		level.getGameRules().getRule(ModGameRules.KEEP_WORK_LOADED).set(false, level.getServer());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ChunkPos here = new ChunkPos(helper.absolutePos(new BlockPos(3, 2, 3)));
		ChunkPos far = new ChunkPos(here.x - 70, here.z + 70);
		helper.assertFalse(level.hasChunk(far.x, far.z), "the far chunk is loaded already");
		UUID worker = UUID.randomUUID();
		WorkSites.get(level).see(worker, far.toLong(), far.toLong(), level.getGameTime());
		Leftovers.after(helper, () -> {
			WorkSites.get(level).forget(worker);
			KeepLoaded.VILLAGES = before;
			level.getGameRules().getRule(ModGameRules.KEEP_WORK_LOADED).set(true, level.getServer());
			player.connection.disconnect(Component.literal("test over"));
		});
		// Three ticket rounds (one every 100 ticks): the village still counts, but nothing is loaded for it.
		helper.runAfterDelay(320, () -> {
			helper.assertTrue(KeepLoaded.villageChunks(level).contains(far), "the far village isn't on the list at all");
			helper.assertFalse(level.hasChunk(far.x, far.z), "the far village was loaded with workplaceKeepWorkLoaded off");
			level.getGameRules().getRule(ModGameRules.KEEP_WORK_LOADED).set(true, level.getServer());
			helper.succeed();
		});
	}
}

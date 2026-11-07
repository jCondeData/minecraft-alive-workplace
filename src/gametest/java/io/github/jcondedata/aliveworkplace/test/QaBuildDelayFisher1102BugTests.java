package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (2026-11-02, B822's sibling), from docs/wiki/builder.md ("A bigger delay is never faster, up to the rule's largest
 * value") and FisherWork's own contract ("How many steps of the fisher's delay a bite takes: 15 to 60"): a fisherman's
 * bite is workplaceBuildDelay times 15 to 60 steps, so at the default 8 a bite takes 120 to 480 ticks. An admin who
 * sets a huge delay to all but stop his Builders must not get fishermen who catch a fish every second: the product
 * overflows an int and the bite then comes after the 20-tick floor. Six fishermen at 2^30 (where 3 of every 4 casts
 * wrapped to 0 or below) so the outcome does not hang on one random cast.
 */
public class QaBuildDelayFisher1102BugTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	//$ gametest_ticks_batch HUGE '1600' '"qaDelayBugFisher"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaDelayBugFisher")
	public void aHugeBuildDelayNeverMakesFishermenCatchFaster(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		int was = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(was, level.getServer()));
		int rule = 1 << 30;
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(rule, level.getServer());
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 4), new BlockPos(20, 1, 24))) {
			helper.setBlock(p, Blocks.WATER);
		}
		List<Villager> fishers = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			BlockPos barrel = new BlockPos(2, 2, 3 + i * 4);
			helper.setBlock(barrel, Blocks.BARREL);
			Villager v = helper.spawn(EntityType.VILLAGER, barrel.east(2));
			Jobs.employ(level, v, helper.absolutePos(barrel), PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN);
			Fishers.start(level, v, new ItemStack(Items.FISHING_ROD));
			fishers.add(v);
		}
		helper.runAtTickTime(1500, () -> {
			int caught = 0;
			for (Villager v : fishers) {
				caught += ModAttachments.FISH_CAUGHT.getOrElse(v, 0);
			}
			helper.assertTrue(caught == 0, "with workplaceBuildDelay " + rule + " six fishermen caught " + caught + " fish in 1500 ticks; a bite takes "
				+ rule + " x 15 to 60 ticks, so none was expected (at the default 8 a bite takes 120 to 480 ticks)");
			helper.succeed();
		});
	}
}

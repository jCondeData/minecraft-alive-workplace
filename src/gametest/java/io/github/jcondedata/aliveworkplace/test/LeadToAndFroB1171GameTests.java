package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B1171: a lead builder with a helper on his build, whose parts come in a few at a time from the Carpenter and the
 * Mason, went to and fro for good between the block within reach he had nothing for and his own next block, laying
 * neither (BuilderWork.workInReach). {@link QaCrafterLoop1111cBugTests} holds the owner's village with its crafters;
 * this is the cause by itself, saved and loaded mid-job.
 */
public class LeadToAndFroB1171GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = QaCrafterLoop1111cGameTests.HUGE;

	/**
	 * The cause by itself, no crafters: the lead waits with empty chests, a village builder comes to help, and only then
	 * the 8 oak planks are put in the chest by his table. Once the lead is out at the build carrying planks, the stairs
	 * and bricks are put in the chest behind him (as the Carpenter and the Mason bring them). Every plank is laid: the
	 * lead used to turn from his next plank to the stairs within reach, walk off to the chest for them, and turn back
	 * once they were out of reach, for good. With the planks laid everyone is saved and loaded, and the build is finished.
	 */
	//$ gametest_ticks_batch HUGE '4000' '"b1171ToAndFroPlanks"'
	@GameTest(template = HUGE, timeoutTicks = 4000, batch = "b1171ToAndFroPlanks")
	public void withAHelperAndOnlyThePlanksInTheChestEveryPlankIsLaidAndThenTheRest(GameTestHelper helper) {
		QaCrafterLoop1111cGameTests.Run run = QaCrafterLoop1111cGameTests.village(helper, "b1171planks", QaCrafterLoop1111cGameTests.mixedRow(), false, false);
		ServerLevel level = helper.getLevel();
		for (BlockPos p : List.of(QaCrafterLoop1111cGameTests.BENCH_CHEST, QaCrafterLoop1111cGameTests.BENCH_CHEST_2,
			QaCrafterLoop1111cGameTests.STORE_CHEST, QaCrafterLoop1111cGameTests.STORE_CHEST_2)) {
			helper.setBlock(p, Blocks.CHEST);
		}
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(true, level.getServer());
		helper.setBlock(QaCrafterLoop1111cGameTests.BENCH_B, ModBlocks.BLUEPRINT_TABLE);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(QaCrafterLoop1111cGameTests.BENCH_B));
		Villager mate = helper.spawn(EntityType.VILLAGER, QaCrafterLoop1111cGameTests.BENCH_B.north());
		Builders.employ(level, mate, helper.absolutePos(QaCrafterLoop1111cGameTests.BENCH_B));
		run.others.add(mate.getUUID());
		AtomicBoolean planksIn = new AtomicBoolean();
		AtomicBoolean restIn = new AtomicBoolean();
		AtomicBoolean reloaded = new AtomicBoolean();
		helper.runAfterDelay(3998, () -> {
			throw new GameTestAssertException("at tick " + run.tick() + (!planksIn.get() ? " the second builder never came to help"
				: !restIn.get() ? " the lead never came to the build with planks" : !reloaded.get() ? " the 8 planks aren't all laid ("
				+ laid(helper, run, true) + " are)" : " the build isn't finished after the reload")
				+ QaCrafterLoop1111cGameTests.state(helper, run));
		});
		helper.onEachTick(() -> {
			Villager v = run.villager(mate.getUUID());
			if (!planksIn.get() && v != null && Builders.isHelping(v)) {
				planksIn.set(true);
				QaCrafterLoop1111cGameTests.put(helper, QaCrafterLoop1111cGameTests.BENCH_CHEST, new ItemStack(Items.OAK_PLANKS, 8));
			}
			Villager lead = run.villager(run.builders.get(0));
			if (planksIn.get() && !restIn.get() && lead != null && ModAttachments.BUILDER_BAG.getOrCreate(lead).count(Items.OAK_PLANKS) > 0
				&& lead.blockPosition().closerThan(helper.absolutePos(QaCrafterLoop1111cGameTests.SITE), 8)) {
				restIn.set(true);
				QaCrafterLoop1111cGameTests.put(helper, QaCrafterLoop1111cGameTests.BENCH_CHEST, new ItemStack(Items.OAK_STAIRS, 4),
					new ItemStack(Items.STONE_BRICKS, 4), new ItemStack(Items.STONE_BRICK_STAIRS, 4));
			}
			if (restIn.get() && !reloaded.get() && laid(helper, run, true) == 8) {
				reloaded.set(true);
				run.reloadAll();
			}
			if (reloaded.get() && laid(helper, run, false) == run.blocks.size() && BuildSiteManager.get(level).get(run.sites.get(0)) == null) {
				helper.succeed();
			}
		});
	}

	/** How many of the drawing's blocks stand ({@code planksOnly}: of its oak planks). */
	private static int laid(GameTestHelper helper, QaCrafterLoop1111cGameTests.Run run, boolean planksOnly) {
		int n = 0;
		for (var e : run.blocks.entrySet()) {
			if ((!planksOnly || e.getValue().is(Blocks.OAK_PLANKS))
				&& helper.getBlockState(QaCrafterLoop1111cGameTests.SITE.offset(e.getKey())).is(e.getValue().getBlock())) {
				n++;
			}
		}
		return n;
	}
}

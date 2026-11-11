package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.MaterialFamilies;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (qa-lane-b-1111-0937), failing on purpose until fixed: what a builder's HELPER carries for that builder's build is
 * kept back in the builder's chests as well ({@code Builders.reservedAt} takes off only the builders' own bags, not the
 * crew's, which {@code computeMissing} and B1151's {@code needFromChests} count). Another builder then waits beside logs
 * that build doesn't need. The control (the builder himself carrying the same logs) is in
 * {@link QaSharedStock1111bGameTests#theOtherBuilderTakesTheSpareWhenTheBuilderHimselfCarries}.
 */
public class QaSharedStock1111bBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	static final String HUGE = "aliveworkplace_test:huge_area";

	/**
	 * B's crew is B and a helper; the helper carries 2 of the 3 logs B's row still needs. The chest by B's table keeps
	 * back from A's build only the 1 log the chests still owe B's build, as it does when B himself carries the 2.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaShared1111bHelperBag"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaShared1111bHelperBag")
	public void aHelpersBagIsNotKeptBackInTheChestToo(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chest = new BlockPos(3, 2, 6);
		ResourceLocation planks = QaSharedStock1111bGameTests.row(helper, "hb_a", new BlockPos(4, 2, 24), 3, Blocks.SPRUCE_PLANKS);
		ResourceLocation logs = QaSharedStock1111bGameTests.row(helper, "hb_b", new BlockPos(4, 2, 27), 3, Blocks.SPRUCE_LOG);
		helper.setBlock(chest, Blocks.CHEST);
		Villager a = QaSharedStock1111bGameTests.idleBuilder(helper, new BlockPos(2, 2, 2));
		Villager b = QaSharedStock1111bGameTests.idleBuilder(helper, chest.offset(4, 0, 0));
		Villager mate = QaSharedStock1111bGameTests.idleBuilder(helper, new BlockPos(12, 2, 2));
		BuildSite siteA = QaSharedStock1111bGameTests.start(helper, a, planks, new BlockPos(4, 2, 24));
		BuildSite siteB = QaSharedStock1111bGameTests.start(helper, b, logs, new BlockPos(4, 2, 27));
		List<BlockPos> chests = List.of(helper.absolutePos(chest));
		int before = Builders.reservedIn(level, chests, siteA).getOrDefault(MaterialFamilies.key(Items.SPRUCE_LOG), 0);
		helper.assertTrue(before == 3, "B's row of 3 logs keeps " + before + " back before anyone carries any");
		ModAttachments.BUILDER_BAG.set(b, BuilderBag.of(List.of(new ItemStack(Items.SPRUCE_LOG, 2))));
		int byBuilder = Builders.reservedIn(level, chests, siteA).getOrDefault(MaterialFamilies.key(Items.SPRUCE_LOG), 0);
		ModAttachments.BUILDER_BAG.set(b, BuilderBag.of(List.of()));
		ModAttachments.BUILDER_BAG.set(mate, BuilderBag.of(List.of(new ItemStack(Items.SPRUCE_LOG, 2))));
		siteB.seen(mate.getUUID(), level.getGameTime());
		helper.assertTrue(siteB.helpers(level.getGameTime()).contains(mate.getUUID()), "the helper isn't on B's crew");
		int byHelper = Builders.reservedIn(level, chests, siteA).getOrDefault(MaterialFamilies.key(Items.SPRUCE_LOG), 0);
		helper.assertTrue(byBuilder == 1, "B carries 2 of his 3 logs, but " + byBuilder + " are kept back (expected 1)");
		helper.assertTrue(byHelper == 1, "B's helper carries 2 of B's 3 logs, but " + byHelper + " are kept back in the chest (expected 1, as when B carries them)");
		helper.succeed();
	}

	/**
	 * The same in play: A's row takes 2 spruce logs and A has no chest; the only logs in the village are the 3 in B's
	 * chest and the 2 B's helper carries for B's row of 3. B's crew is idle (night, a break), the helper still on it.
	 * The village holds exactly enough for both rows, and 2 of the chest's logs are spare: A takes them and builds his
	 * row. Fails: all 3 are kept back for B, and A waits beside them.
	 */
	//$ gametest_ticks_batch HUGE '1600' '"qaShared1111bHelperPlay"'
	@GameTest(template = HUGE, timeoutTicks = 1600, batch = "qaShared1111bHelperPlay")
	public void theOtherBuilderTakesTheSpareWhileAHelperCarriesTheRest(GameTestHelper helper) {
		QaSharedStock1111bGameTests.SpareRun run = QaSharedStock1111bGameTests.spareRun(helper, "hp", true);
		helper.onEachTick(() -> run.siteB().seen(run.mate().getUUID(), run.level().getGameTime()));
		QaSharedStock1111bGameTests.aBuildsFromTheSpare(helper, run);
	}
}

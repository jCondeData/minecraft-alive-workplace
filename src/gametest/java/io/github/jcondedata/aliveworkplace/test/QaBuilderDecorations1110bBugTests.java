package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BUG TEST (fails on main as of 89207e025, QA lane-b-1110-1837): while a finished build waits for an item frame nobody in
 * the village makes (B1148's end wait), the builder's overhead "more" lines are empty: BuilderStatusSync.supplyLines
 * returns nothing once the stage is DONE, so 46.1's "Hire a Leatherworker to make Item Frames" (and the sources line)
 * never shows during the wait; only "Needs: 1× Item Frame" does. Register in fabric.mod.json together with the fix.
 */
public class QaBuilderDecorations1110bBugTests {
	private static final Logger LOG = LoggerFactory.getLogger("QaBuilderDecorations1110b");
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * No Leatherworker in the village: while the finished build waits for its frame (past the jobs' 600 ticks to answer),
	 * the builder's overhead lines say "Needs: 1× Item Frame" and name the job to hire, "Hire a Leatherworker to make
	 * Item Frames" (46.1's line for a crafted part nobody here makes; the spec: named on the shortage line).
	 */
	//$ gametest_ticks_batch AREA '2400' '"qa1110bHire"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa1110bHire")
	public void withNoLeatherworkerTheWaitingBuilderSaysWhoToHire(GameTestHelper helper) {
		QaBuilderDecorations1110bGameTests.prepare(helper);
		QaBuilderDecorations1110bGameTests.shortWait(helper, 2000);
		ResourceLocation id = QaBuilderDecorations1110bGameTests.frameBlueprint(helper, false);
		QaBuilderDecorations1110bGameTests.chest(helper, new ItemStack(Items.OAK_PLANKS, 4));
		Villager builder = QaBuilderDecorations1110bGameTests.builder(helper);
		BuildSite site = QaBuilderDecorations1110bGameTests.start(helper, builder, id);
		ServerLevel level = helper.getLevel();
		AtomicReference<String> line = new AtomicReference<>();
		AtomicReference<List<String>> more = new AtomicReference<>(List.of());
		String hire = String.format(QaTinkererShortGameTests.lang("message.aliveworkplace.job_chain.hire"), "Leatherworker", "Item Frames");
		helper.onEachTick(() -> {
			BuildSite open = BuildSiteManager.get(level).get(site.id());
			if (open == null || !open.isGettingDecorations() || open.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
				return;
			}
			if (level.getGameTime() % 20 == 0) { // as often as the overhead status is sent
				BuilderStatusSync.Status status = BuilderStatusSync.status(level, open, builder);
				line.set(status.line().getString());
				more.set(status.more().stream().map(Component::getString).toList());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue("Needs: 1× Item Frame".equals(line.get()), "the builder's overhead line while waiting" + ": " + line.get() + ", expected " + "Needs: 1× Item Frame");
			helper.assertTrue(more.get().contains(hire), "while waiting for the frame with no Leatherworker, the lines don't say '" + hire + "': " + more.get());
			LOG.info("[QA B1148] hire line shown: {}", more.get());
		});
	}
}

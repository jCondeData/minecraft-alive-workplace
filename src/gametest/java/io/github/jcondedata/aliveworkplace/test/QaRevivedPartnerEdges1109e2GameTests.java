package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.grave.Graves;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-e-1109-0722, saves), B1082's fix from its spec ("after a revival both sides agree: married to each other
 * again when the widow(er) is still alone, else the revived one single"; the wiki: "one who has married someone else
 * since, or mourns someone else, stays with them"): the mourning branch, which neither B1082's tests nor
 * {@code QaConvertRevive1109cGameTests} reach. A hand mutant dropping the "still mourns them" check fails it.
 */
public class QaRevivedPartnerEdges1109e2GameTests implements FabricGameTest {
	private static final String AREA = QaCuredVillager1109eGameTests.AREA;

	/** Ada dies; Bea marries Carl, and Carl dies too; Ada is brought back: Bea mourns Carl and Ada is single. */
	//$ gametest_ticks_batch AREA '100' '"qaRevived1109e2Mourns"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRevived1109e2Mourns")
	public void aRevivedWifeWhoseWidowMournsSomeoneElseIsSingle(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		Villager carl = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 6), "Carl");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		helper.runAfterDelay(2, () -> {
			ada.kill();
			QaCuredVillager1109eGameTests.marry(bea, carl, 20);
			carl.kill();
			helper.assertTrue(ModAttachments.LATE_PARTNER.has(bea) && ModAttachments.LATE_PARTNER.get(bea).id().equals(carl.getUUID()),
				"setup: Bea doesn't mourn Carl");
			Villager back = revive(helper, level, new BlockPos(2, 2, 2));
			Couples.Partner hers = ModAttachments.PARTNER.get(back);
			Couples.Partner his = ModAttachments.PARTNER.get(bea);
			Couples.LatePartner mourns = ModAttachments.LATE_PARTNER.get(bea);
			helper.assertTrue(hers == null && his == null && mourns != null && mourns.id().equals(carl.getUUID()),
				"revived Ada, whose widow Bea married Carl and lost him: Ada's partner " + hers + ", Bea's " + his
					+ ", Bea mourns " + (mourns == null ? "nobody" : mourns.name().getString()));
			helper.succeed();
		});
	}

	/** The one grave within a block of {@code at}, revived as an Undertaker does it. */
	private static Villager revive(GameTestHelper helper, ServerLevel level, BlockPos at) {
		List<BlockPos> graves = Graves.near(level, helper.absolutePos(at), 1);
		helper.assertTrue(graves.size() == 1, "setup: graves by " + at + ": " + graves.size());
		Villager back = Graves.revive(level, graves.get(0));
		helper.assertTrue(back != null && back.isAlive(), "setup: nobody was brought back from the grave by " + at);
		return back;
	}
}

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
 * QA (lane-e-1109-0115, saves): a married villager dies, leaves a grave, and an Undertaker brings them back. The wiki
 * (graveyards.md): "the Undertaker brings the villager back ... same name, job, level, trades and partner". The grave
 * keeps the villager's whole saved data, partner included, but the death already widowed the other one
 * ({@code Couples.onDeath}); only the "Bring back my love" request marries them again. So the two must agree after the
 * revival: married to each other again, or neither claiming the other.
 */
public class QaRevivedPartner1109eBugTests implements FabricGameTest {
	private static final String AREA = QaCuredVillager1109eGameTests.AREA;

	/** Ada (married to Bea) dies and is brought back: Ada and Bea agree on whether they're married. */
	//$ gametest_ticks_batch AREA '100' '"qaRevived1109eAgree"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRevived1109eAgree")
	public void aRevivedWifeAndHerWidowerAgreeWhetherTheyAreMarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		helper.runAfterDelay(2, () -> {
			Villager back = dieAndRevive(helper, level, ada);
			Couples.Partner hers = ModAttachments.PARTNER.get(back);
			Couples.Partner his = ModAttachments.PARTNER.get(bea);
			boolean married = hers != null && hers.id().equals(bea.getUUID()) && his != null && his.id().equals(back.getUUID());
			boolean apart = (hers == null || !hers.id().equals(bea.getUUID())) && (his == null || !his.id().equals(back.getUUID()));
			helper.assertTrue(married || apart, "revived Ada and Bea disagree: Ada's partner " + describe(hers, bea, back)
				+ ", Bea's partner " + describe(his, bea, back) + ", Bea widowed: " + ModAttachments.WIDOWED_DAY.has(bea)
				+ ", Bea's late partner " + (ModAttachments.LATE_PARTNER.has(bea) ? ModAttachments.LATE_PARTNER.get(bea).name().getString() : "none"));
			helper.succeed();
		});
	}

	/** Ada dies, Bea marries Carl, Ada is brought back: Ada doesn't claim Bea, who is Carl's wife. */
	//$ gametest_ticks_batch AREA '100' '"qaRevived1109eRemarried"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaRevived1109eRemarried")
	public void aRevivedWifeWhoseWidowerRemarriedIsNotStillHisWife(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		Villager carl = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 6), "Carl");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		helper.runAfterDelay(2, () -> {
			ada.kill();
			helper.assertTrue(ModAttachments.PARTNER.get(bea) == null, "setup: Bea wasn't widowed by Ada's death");
			QaCuredVillager1109eGameTests.marry(bea, carl, 20);
			Villager back = revive(helper, level);
			Couples.Partner hers = ModAttachments.PARTNER.get(back);
			helper.assertTrue(hers == null || !hers.id().equals(bea.getUUID()),
				"revived Ada is still married to Bea, who has married Carl since: Ada's partner " + describe(hers, bea, back)
					+ ", Bea's " + describe(ModAttachments.PARTNER.get(bea), bea, back));
			helper.succeed();
		});
	}

	private static Villager dieAndRevive(GameTestHelper helper, ServerLevel level, Villager v) {
		v.kill();
		helper.assertTrue(!v.isAlive(), "setup: Ada didn't die");
		return revive(helper, level);
	}

	/** The grave near the farmers revived as an Undertaker does it. */
	private static Villager revive(GameTestHelper helper, ServerLevel level) {
		List<BlockPos> graves = Graves.near(level, helper.absolutePos(new BlockPos(2, 2, 2)), 6);
		helper.assertTrue(graves.size() == 1, "setup: graves by Ada: " + graves.size());
		Villager back = Graves.revive(level, graves.get(0));
		helper.assertTrue(back != null && back.isAlive(), "setup: Ada wasn't brought back");
		return back;
	}

	private static String describe(Couples.Partner p, Villager bea, Villager ada) {
		if (p == null) {
			return "none";
		}
		String who = p.id().equals(bea.getUUID()) ? "Bea" : p.id().equals(ada.getUUID()) ? "Ada" : p.name().getString();
		return who + (p.married() ? " (married)" : " (courting)");
	}
}

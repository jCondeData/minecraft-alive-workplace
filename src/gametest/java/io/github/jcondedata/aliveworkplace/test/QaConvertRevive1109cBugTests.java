package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (qa-lane-c-1109-0655), the failing half of {@link QaConvertRevive1109cGameTests}: couples where the other one dies
 * while the first is away (a zombie villager, or in a grave). B1082 asks that "after a revival both sides agree", and
 * B1081 that a cured villager is married to their partner, "not widowed" only because the partner still has them.
 */
public class QaConvertRevive1109cBugTests implements FabricGameTest {
	private static final String AREA = QaConvertRevive1109cGameTests.AREA;

	/**
	 * Ada is a zombie villager when her husband Bea is killed. Cured, Ada isn't married to a dead man: she is widowed (or
	 * at least single, free to court), not still married to Bea forever.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaConv1109cDiedMeanwhile"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaConv1109cDiedMeanwhile")
	public void aVillagerCuredAfterTheirSpouseDiedIsNotStillMarriedToTheDead(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		QaConvertRevive1109cGameTests.turn(helper, ada, zombie -> {
			bea.kill();
			helper.runAfterDelay(30, () -> { // Bea's body gone
				QaConvertRevive1109cGameTests.cure(helper, zombie, "Ada", cured -> {
					Couples.Partner hers = ModAttachments.PARTNER.get(cured);
					helper.assertTrue(hers == null || !hers.id().equals(bea.getUUID()),
						"cured Ada is still " + (hers.married() ? "married" : "courting") + " to Bea, who was killed while she was a zombie villager"
							+ " (widowed " + ModAttachments.WIDOWED_DAY.has(cured) + ", can court " + QaConvertRevive1109cGameTests.single(cured) + ")");
					helper.succeed();
				});
			});
		});
	}

	/**
	 * Ada and Bea, married, both die, and an Undertaker brings back both, Ada first: the two agree, married to each other
	 * again, or neither naming the other.
	 */
	//$ gametest_ticks_batch AREA '140' '"qaRev1109cBothDied"'
	@GameTest(template = AREA, timeoutTicks = 140, batch = "qaRev1109cBothDied")
	public void aCoupleWhoBothDiedAndAreBothBroughtBackAgree(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.graves(helper);
		ServerLevel level = helper.getLevel();
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		helper.runAfterDelay(2, () -> {
			ada.kill();
			bea.kill();
			helper.runAfterDelay(30, () -> { // both bodies gone; the Undertaker comes
				Villager adaBack = QaConvertRevive1109cGameTests.revive(helper, level, new BlockPos(2, 2, 2));
				Villager beaBack = QaConvertRevive1109cGameTests.revive(helper, level, new BlockPos(6, 2, 2));
				Couples.Partner hers = ModAttachments.PARTNER.get(adaBack);
				Couples.Partner his = ModAttachments.PARTNER.get(beaBack);
				boolean married = hers != null && his != null && hers.id().equals(beaBack.getUUID()) && his.id().equals(adaBack.getUUID());
				boolean apart = (hers == null || !(hers.id().equals(beaBack.getUUID()) || hers.id().equals(bea.getUUID())))
					&& (his == null || !(his.id().equals(adaBack.getUUID()) || his.id().equals(ada.getUUID())));
				helper.assertTrue(married || apart, "both brought back, Ada and Bea disagree: Ada's partner " + describe(hers, ada, bea, adaBack, beaBack)
					+ ", Bea's " + describe(his, ada, bea, adaBack, beaBack) + "; Bea widowed " + ModAttachments.WIDOWED_DAY.has(beaBack)
					+ ", can court: Ada " + QaConvertRevive1109cGameTests.single(adaBack) + ", Bea " + QaConvertRevive1109cGameTests.single(beaBack));
				helper.succeed();
			});
		});
	}

	/**
	 * B1081's report names "guild master" among what a cured villager keeps ("parents, gifted, guild master, retired,
	 * schooled, job counts, adult_since... go the same way"). Ada leads the farmers' guild, is turned and cured: still
	 * its master.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaConv1109cGuild"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaConv1109cGuild")
	public void aCuredGuildMasterStillLeadsTheGuild(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		io.github.jcondedata.aliveworkplace.hall.Guilds.Master master = new io.github.jcondedata.aliveworkplace.hall.Guilds.Master(
			io.github.jcondedata.aliveworkplace.AliveWorkplace.id("farmers"), helper.absolutePos(new BlockPos(4, 2, 4)));
		ModAttachments.GUILD_MASTER.set(ada, master);
		QaConvertRevive1109cGameTests.turn(helper, ada, zombie -> QaConvertRevive1109cGameTests.cure(helper, zombie, "Ada", cured -> {
			helper.assertTrue(master.equals(ModAttachments.GUILD_MASTER.get(cured)),
				"cured, Ada no longer leads her guild: guild_master " + ModAttachments.GUILD_MASTER.get(cured)
					+ " (zombie villager had " + ModAttachments.GUILD_MASTER.get(zombie) + ")");
			helper.succeed();
		}));
	}

	private static String describe(Couples.Partner p, Villager ada, Villager bea, Villager adaBack, Villager beaBack) {
		if (p == null) {
			return "none";
		}
		String who = p.id().equals(beaBack.getUUID()) ? "Bea" : p.id().equals(bea.getUUID()) ? "Bea (her old id)"
			: p.id().equals(adaBack.getUUID()) ? "Ada" : p.id().equals(ada.getUUID()) ? "Ada (her old id)" : p.name().getString();
		return who + (p.married() ? " (married)" : " (courting)");
	}
}

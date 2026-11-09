package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.story.Friendship;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-e-1109-0115, saves), the failing half of {@link QaCuredVillager1109eGameTests}: a villager a zombie turns and
 * a player cures comes back as vanilla's same villager, but what the mod saved on them (Fabric attachments) stays behind
 * with the zombie villager: their social class, family name and players' friendship, and their marriage.
 */
public class QaCuredVillager1109eBugTests implements FabricGameTest {
	private static final String AREA = QaCuredVillager1109eGameTests.AREA;

	/** A Burgher of family 7 with 5 hearts for a player, turned and cured: still a Burgher of family 7 with 5 hearts. */
	//$ gametest_ticks_batch AREA '200' '"qaCured1109eSelf"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCured1109eSelf")
	public void aCuredVillagerKeepsHisClassFamilyNameAndFriends(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		UUID player = UUID.fromString("00000000-0000-0000-0000-000000001109");
		ModAttachments.SOCIAL_CLASS.set(ada, QaCuredVillager1109eGameTests.BURGHER);
		ModAttachments.FAMILY_NAME.set(ada, 7);
		QaCuredVillager1109eGameTests.befriend(ada, player, 500);
		QaCuredVillager1109eGameTests.turnAndCure(helper, ada, cured -> {
			List<String> lost = new ArrayList<>();
			if (!QaCuredVillager1109eGameTests.BURGHER.equals(ModAttachments.SOCIAL_CLASS.get(cured))) {
				lost.add("class burgher -> " + ModAttachments.SOCIAL_CLASS.get(cured));
			}
			Integer family = ModAttachments.FAMILY_NAME.get(cured);
			if (family == null || family != 7) {
				lost.add("family name 7 -> " + family);
			}
			int points = Friendship.points(cured, player);
			if (points != 500) {
				lost.add("the player's friendship 500 points (5 hearts) -> " + points);
			}
			helper.assertTrue(lost.isEmpty(), "cured, the villager lost what the mod knew of him: " + String.join("; ", lost));
			helper.succeed();
		});
	}

	/** Ada married to Bea; Ada turned and cured: the two are still married to each other, and Bea isn't a widow. */
	//$ gametest_ticks_batch AREA '200' '"qaCured1109eMarried"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCured1109eMarried")
	public void aCuredWifeIsStillMarried(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager ada = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Ada");
		Villager bea = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(6, 2, 2), "Bea");
		QaCuredVillager1109eGameTests.marry(ada, bea, 3);
		QaCuredVillager1109eGameTests.turnAndCure(helper, ada, cured -> {
			Couples.Partner hers = ModAttachments.PARTNER.get(cured);
			Couples.Partner his = ModAttachments.PARTNER.get(bea);
			helper.assertTrue(hers != null && hers.married() && hers.id().equals(bea.getUUID())
					&& his != null && his.married() && his.id().equals(cured.getUUID()) && !ModAttachments.WIDOWED_DAY.has(bea),
				"cured Ada and Bea aren't married to each other any more: Ada's partner " + (hers == null ? "none" : hers.id() + " married " + hers.married())
					+ ", Bea's partner " + (his == null ? "none" : his.id() + " (cured Ada is " + cured.getUUID() + ")")
					+ ", Bea widowed on day " + ModAttachments.WIDOWED_DAY.get(bea));
			helper.succeed();
		});
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

/**
 * QA bug tests for ROADMAP 35.5 (B467, fixed: WonderCrews.handOn now hands the lead on too). The spec names the crew's lead on the stone
 * and the hall; when the lead dies or is retrained mid-stage the part is handed to a member (WonderCrews.handOn), but the
 * Wonder's own lead (WonderSite.builder, which WonderCrews.leadName shows) isn't, so for the rest of the stage the stone
 * and the hall's card name "?" (the lead died) or a villager who is now a farmer (retrained).
 */
public class QaWonderCrewBugTests {
	private static final String TALL = "aliveworkplace_test:tall_area";

	private static QaWonderCrewGameTests.Village crew(GameTestHelper helper, String[] names) {
		QaWonderCrewGameTests.Village v = QaWonderCrewGameTests.village(helper, 3, 4);
		for (int i = 0; i < 3; i++) {
			v.builders().get(i).setCustomName(Component.literal(names[i]));
		}
		return v;
	}

	private static BuildSite startAndJoin(GameTestHelper helper, QaWonderCrewGameTests.Village v) {
		ServerLevel level = v.level();
		BuildSite part = Builders.activeSite(level, v.builders().get(0));
		helper.assertTrue(part != null, "the lead didn't start stage I");
		for (int i = 1; i < 3; i++) {
			v.builders().get(i).tickCount = 20;
			helper.assertTrue(Builders.activeSite(level, v.builders().get(i)) != null, "builder " + i + " didn't join");
		}
		return part;
	}

	private static void namesALivingMember(GameTestHelper helper, QaWonderCrewGameTests.Village v, WonderSite site, BuildSite part, String how) {
		ServerLevel level = v.level();
		BuildSite after = BuildSiteManager.get(level).get(part.id());
		helper.assertTrue(after != null && after.builder() != null && !after.builder().equals(v.builders().get(0).getUUID()), "the part wasn't handed on");
		String named = QaWonderCrewGameTests.stoneLead(level, QaWonderCrewGameTests.now(level, site.id()));
		helper.assertTrue(named.equals("Builder1") || named.equals("Builder2"), "after the lead " + how + " the stone names '" + named + "' as the crew's lead");
		String card = QaWonderCrewGameTests.hallCard(level, v.hall());
		helper.assertTrue(card.contains("Builder1") || card.contains("Builder2"), "after the lead " + how + " the hall's card names no living lead: " + card);
	}

	/** The lead of stage I dies with two members on its part: the stone and the hall should name a living crew builder. */
	//$ gametest_ticks_batch TALL '100' '"qaCrewLeadDiesNamed"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaCrewLeadDiesNamed")
	public void aDeadLeadIsNoLongerNamedOnTheStone(GameTestHelper helper) {
		QaWonderCrewGameTests.Village v = crew(helper, new String[] {"Builder0", "Builder1", "Builder2"});
		ServerLevel level = v.level();
		WonderSite site = QaWonderCrewGameTests.begin(helper, v, v.builders().get(0));
		BuildSite part = startAndJoin(helper, v);
		helper.assertTrue(QaWonderCrewGameTests.stoneLead(level, QaWonderCrewGameTests.now(level, site.id())).equals("Builder0"), "the stone doesn't name the lead before");
		v.builders().get(0).kill();
		helper.runAfterDelay(40, () -> { // the body gone
			namesALivingMember(helper, v, site, part, "died");
			helper.succeed();
		});
	}

	/** The lead of stage I is retrained as a farmer: the stone and the hall should name a builder of the crew, not the farmer. */
	//$ gametest_ticks_batch TALL '100' '"qaCrewLeadRetrainedNamed"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaCrewLeadRetrainedNamed")
	public void aRetrainedLeadIsNoLongerNamedOnTheStone(GameTestHelper helper) {
		QaWonderCrewGameTests.Village v = crew(helper, new String[] {"Builder0", "Builder1", "Builder2"});
		ServerLevel level = v.level();
		WonderSite site = QaWonderCrewGameTests.begin(helper, v, v.builders().get(0));
		BuildSite part = startAndJoin(helper, v);
		BlockPos composter = new BlockPos(5, 2, 22);
		helper.setBlock(composter, Blocks.COMPOSTER);
		Villager lead = v.builders().get(0);
		io.github.jcondedata.aliveworkplace.work.Retraining.retrain(level, v.owner(), lead, helper.absolutePos(composter), VillagerProfession.FARMER);
		helper.assertTrue(!Builders.isBuilder(lead), "the lead wasn't retrained");
		helper.runAfterDelay(2, () -> {
			namesALivingMember(helper, v, site, part, "was retrained");
			helper.succeed();
		});
	}
}

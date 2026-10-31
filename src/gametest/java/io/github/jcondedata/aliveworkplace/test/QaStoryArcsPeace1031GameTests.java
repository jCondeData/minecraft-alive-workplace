package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;

/**
 * QA (lane-d-1031-0408), ROADMAP 31.4 and 32.21: only arcs marked {@code threat} wait for peace. A story that is no
 * threat (the test pack's Test of Three) still starts in a village set At peace and at threat level 0, so keeping
 * threat arcs out never keeps every story out.
 */
public class QaStoryArcsPeace1031GameTests implements FabricGameTest {
	static final String TALL = "aliveworkplace_test:tall_area";
	static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/three_chapters");

	/** Only {@code keep} may start; no cooldown, no server cap; the old settings come back after the test. */
	static void onlyArc(GameTestHelper helper, ResourceLocation keep) {
		Set<String> disabled = Arcs.DISABLED;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		int threat = Threats.LEVEL;
		Set<String> others = new HashSet<>();
		for (Arcs.Arc a : Arcs.all()) {
			if (!a.id().equals(keep)) {
				others.add(a.id().toString());
			}
		}
		Arcs.DISABLED = others;
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 1000;
		Arcs.ENABLED = true;
		Leftovers.after(helper, () -> {
			Arcs.DISABLED = disabled;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
			Threats.LEVEL = threat;
		});
	}

	static ArcState roll(ServerLevel level, BlockPos hall, RandomSource random, int times) {
		ArcState s = null;
		for (int i = 0; i < times && s == null; i++) {
			s = Arcs.tryStart(level, hall, random, 1);
		}
		return s;
	}

	//$ gametest_ticks_batch TALL '200' '"qaStoryArcPeace"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaStoryArcPeace")
	public void aStoryThatIsNoThreatStillStartsAtPeaceAndAtLevelZero(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		VillageHallBlockEntity entity = QaSicknessGameTests.hall(helper);
		onlyArc(helper, THREE);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(QaSicknessGameTests.HALL);
		entity.setAtPeace(true, Chronicle.day(level));
		Threats.LEVEL = 0;
		ArcState s = roll(level, hall, RandomSource.create(3106), 5);
		helper.assertTrue(s != null && s.id.equals(THREE.toString()), "a story that is no threat didn't start At peace at threat level 0");
		helper.succeed();
	}
}

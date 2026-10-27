package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.Mourning;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.phys.Vec3;

/**
 * QA findings for B548/B549 (the village is the circle {@link VillageHalls#contains} draws: RADIUS across, HEIGHT up or
 * down) that fail on the mod as it is; kept off the main suite until fixed.
 */
public class QaVillageCircleBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/**
	 * A villager dies inside the village, on its rim and up a hill: RADIUS - 2 blocks out and 10 up (in the circle and
	 * well within HEIGHT, but 17.2 blocks from the hall in a straight line). Her son by the hall grieves, a player by the
	 * hall is told, and her chronicle line has her job and age, as for any death in the village. (On a live 64-block
	 * village the same happens 60 out and 25 up.)
	 */
	//$ gametest_ticks_batch AREA '100' '"qaCircleMourningRim"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCircleMourningRim")
	public void aDeathOnTheVillagesRimUpAHillIsMourned(GameTestHelper helper) {
		QaVillageCircleGameTests.mourningVillage(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			QaVillageCircleGameTests.mourningReady(helper);
			long today = Chronicle.day(level);
			BlockPos h = helper.absolutePos(HALL);
			int r = VillageHalls.RADIUS;
			BlockPos rim = h.offset(r - 2, 10, 0);
			QaVillageCircleGameTests.staged(helper, h, List.of(rim), List.of());
			helper.assertTrue(rim.distSqr(h) > (double) r * r, "staging: the rim isn't farther than RADIUS in a straight line");
			Villager mira = QaVillageCircleGameTests.worker(helper, rim, "Mira", VillagerProfession.FARMER);
			Villager pip = QaVillageCircleGameTests.worker(helper, h.offset(3, 0, 3), "Pip", VillagerProfession.FLETCHER);
			ModAttachments.PARENTS.set(pip, new Families.Parents(Component.literal("Mira"), Component.literal("Tom"), "", "", true));
			List<Component> heard = new ArrayList<>();
			QaClassMigrationGameTests.listener(helper, heard, Vec3.atBottomCenterOf(h.offset(2, 0, -2)), "qa-mourner");
			Component line = Mourning.onDeath(level, mira, Component.literal("Mira fell"), null);
			List<String> wrong = new ArrayList<>();
			if (!Mourning.grieving(pip, today)) {
				wrong.add("her son by the hall doesn't grieve");
			}
			if (heard.stream().noneMatch(c -> c.getString().contains("Mira"))) {
				wrong.add("the player by the hall wasn't told: " + heard.stream().map(Component::getString).toList());
			}
			if (line.getString().equals("Mira fell")) {
				wrong.add("the chronicle line is just the cause, as for a death outside any village");
			}
			mira.discard();
			pip.discard();
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		});
	}
}

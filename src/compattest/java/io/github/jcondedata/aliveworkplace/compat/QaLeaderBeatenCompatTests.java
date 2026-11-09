package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.trainer.Trainers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * B1102 (ROADMAP 43.1, village battle quests): pressing Challenge on a Trainer Leader counted as a beaten trainer toward
 * the village's battle quest, before the once-a-day check and before any battle, so a press refused for no team, or
 * answered "Come back tomorrow!", counted too. Only a won battle counts, once.
 */
public class QaLeaderBeatenCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(9, 2, 9);

	/** A hall (radius 16) whose own rounds post nothing, with a "beat 5 trainers" quest on its board. */
	private static Quest battleQuest(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		PartnerShowsCompatTests.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		ServerLevel level = helper.getLevel();
		long now = level.getGameTime();
		Quest quest = new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/battle"), "hall", Optional.empty(), "Ana", now,
			now + VillageQuests.LASTS, List.of(new Objectives.Battle(5)), new int[1], List.of());
		Stories.post(level, helper.absolutePos(HALL), quest);
		return quest;
	}

	/**
	 * B (no Pokémon) presses Challenge on the Leader twice: refused, nothing counts. A battles and wins: one. A presses
	 * again ("Come back tomorrow!"): still one.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaLeaderBeatenCompat"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaLeaderBeatenCompat")
	public void aLeaderCountsOnlyWhenBeatenInBattle(GameTestHelper helper) {
		QaPokemonScreens1109dCompatTests.Fight f = QaPokemonScreens1109dCompatTests.trainer(helper, true, "qa1102ldrA");
		Quest quest = battleQuest(helper);
		ServerPlayer b = QaPokemonScreens1109dCompatTests.joiner(helper, f.players(), new ArrayList<>(), "qa1102ldrB");
		b.moveTo(f.trainer().getX() - 1.5, f.trainer().getY(), f.trainer().getZ());

		Trainers.challenge(b, f.trainer());
		Trainers.challenge(b, f.trainer());
		helper.assertFalse(QaPokemonScreens1109dCompatTests.battling(b), "B battled with no Pokémon");
		helper.assertValueEqual(quest.progress[0], 0, "battle quest after two refused Challenge presses");

		Trainers.challenge(f.player(), f.trainer());
		helper.assertTrue(QaPokemonScreens1109dCompatTests.battling(f.player()), "setup: A's battle with the Leader didn't start");
		helper.assertValueEqual(quest.progress[0], 0, "battle quest when the battle has only started");
		QaPokemonScreens1109dCompatTests.finish(f.player(), true);
		helper.assertValueEqual(quest.progress[0], 1, "battle quest after beating the Leader");

		Trainers.challenge(f.player(), f.trainer());
		helper.assertFalse(QaPokemonScreens1109dCompatTests.battling(f.player()), "A battled the Leader twice in a day");
		helper.assertValueEqual(quest.progress[0], 1, "battle quest after a 'Come back tomorrow' press");
		helper.succeed();
	}
}

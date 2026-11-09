package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;

/**
 * QA bug test (lane E, 2026-11-09, 42.3): the list screen sends a click on a gray row too ("a click still says it",
 * list-screen.md), so a player who clicks Swift Hands at its top, a row that reads "Level 3 of 3 · Fully researched",
 * is told "Swift Hands can't be researched yet". A Legend's tree (ResearchTrees.whyNot: "Fully researched") and the
 * realm's ("... is fully researched.") say it's done; the scholars' own topics fall through to the locked line.
 */
public class QaResearchDoneClick1109eBugTests implements FabricGameTest {
	private static final String BIG = "aliveworkplace_test:big_area";

	//$ gametest_ticks_batch BIG '100' '"qaResearchDone1109e"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaResearchDone1109e")
	public void aClickOnAFullyResearchedTopicDoesntSayItCantBeResearchedYet(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Research.forget();
		});
		helper.setDayTime(2000);
		ServerLevel level = helper.getLevel();
		BlockPos hallAt = new BlockPos(14, 2, 14);
		BlockPos desk = new BlockPos(8, 2, 8);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		helper.setBlock(desk, ModBlocks.SCHOLARS_DESK);
		Villager scholar = helper.spawn(EntityType.VILLAGER, new BlockPos(9, 2, 9));
		Jobs.employ(level, scholar, helper.absolutePos(desk), ModVillagers.SCHOLARS_DESK_POI, ModVillagers.SCHOLAR);
		scholar.setNoAi(true);
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(hallAt));
		helper.runAfterDelay(2, () -> {
			Research.Topic swift = Research.Topic.SWIFT_HANDS;
			hall.setResearch(new Research.State(Map.of(swift.key(), swift.maxLevel), Optional.empty(), 0, false));
			Research.forget();
			QaStageFiveGameTests.Client client = QaStageFiveGameTests.client(helper, GameType.SURVIVAL);
			ChoiceMenu menu = JobListMenusGameTests.open(helper, client.player(), scholar, true);
			int slot = ResearchScreen.TOPIC_SLOTS[swift.ordinal()];
			helper.assertTrue(JobListMenusGameTests.row(helper, menu, slot).off(), "setup: the fully researched row isn't gray");
			client.seen().clear();
			JobListMenusGameTests.click(client.player(), menu, slot);
			String told = client.seen().stream().map(Component::getString).toList().toString();
			helper.assertTrue(client.seen().stream().noneMatch(c -> JobListMenusGameTests.key(c).equals("message.aliveworkplace.research.locked")),
				"a click on Swift Hands, fully researched, told the player it can't be researched yet: " + told
					+ " (expected: that it's fully researched, as a Legend's tree and the realm's say)");
			helper.succeed();
		});
	}
}

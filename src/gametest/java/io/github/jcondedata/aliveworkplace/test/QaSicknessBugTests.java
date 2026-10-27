package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.herb.HerbBlock;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.SicknessStory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * QA bug tests for The Sickness (ROADMAP 31.17 and 31.18), each to be committed with its fix.
 */
public class QaSicknessBugTests implements FabricGameTest {
	private static final String TALL = QaSicknessGameTests.TALL;
	private static final BlockPos HALL = QaSicknessGameTests.HALL;

	/**
	 * Only two herbs' biomes are within reach (the rest passed over): the healer names two, and the words say so. The
	 * chat, the chronicle and the healer's thanks mustn't speak of "three far herbs" while naming two.
	 */
	//$ gametest_ticks_batch TALL '100' '"qaSickBugTwoHerbs"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaSickBugTwoHerbs")
	public void twoHerbsFoundTheWordsDontSayThree(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		SicknessStory.LOCATOR = QaSicknessGameTests.stub(Map.of("sea_lavender", 200, "mirewort", 400));
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		QaSicknessGameTests.village(helper, 8);
		Villager nurse = QaSicknessGameTests.nurse(helper, new BlockPos(20, 2, 20));
		List<Component> heard = new ArrayList<>();
		ServerPlayer player = QaStageThreeGameTests.listener(helper, heard);
		QaSicknessGameTests.setDay(level, Chronicle.day(level) + 1, 1000);
		ArcState s = Arcs.start(level, hall, QaSicknessGameTests.arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		List<HerbBlock> herbs = SicknessStory.herbs(s);
		helper.assertTrue(herbs.size() == 2, "named: " + herbs);
		for (HerbBlock herb : herbs) {
			player.getInventory().add(new ItemStack(herb, 3));
		}
		SicknessStory.onUse(player, level, InteractionHand.MAIN_HAND, nurse);
		helper.assertTrue(s.chapter == 2, "two herbs handed in, the brew didn't begin: chapter " + s.chapter);
		List<String> words = new ArrayList<>(heard.stream().map(Component::getString).toList());
		words.addAll(QaSicknessGameTests.chronicle(entity));
		List<String> three = words.stream().filter(l -> l.contains("Sea Lavender and Mirewort") || l.contains("all three"))
			.filter(l -> l.contains("three")).toList();
		helper.assertTrue(three.isEmpty(), "two herbs named, but the words say three: " + three);
		helper.succeed();
	}

	/**
	 * Doctor Wren is killed during the brew (a zombie at night, a stray arrow): the village still gets its cure. As when
	 * a Nurse dies, someone takes up the healer's part (another Doctor Wren is sent for), and the tonic goes round within
	 * a few days, not only by the cough running its course after 15.
	 */
	//$ gametest_ticks_batch TALL '300' '"qaSickBugWrenKilled"'
	@GameTest(template = TALL, timeoutTicks = 300, batch = "qaSickBugWrenKilled")
	public void doctorWrenKilledMidBrewTheCureStillComes(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaSicknessGameTests.settings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		QaSicknessGameTests.hall(helper);
		BlockPos hall = helper.absolutePos(HALL);
		List<Villager> people = QaSicknessGameTests.village(helper, 8);
		ServerPlayer player = QaSicknessGameTests.player(helper);
		long day = Chronicle.day(level) + 1;
		QaSicknessGameTests.setDay(level, day, 1000);
		ArcState s = Arcs.start(level, hall, QaSicknessGameTests.arc(helper));
		helper.assertTrue(s != null, "the arc didn't start");
		QaSicknessGameTests.setDay(level, day + 1, 1000);
		SicknessStory.tick(level);
		Villager wren = SicknessStory.healer(level, s);
		helper.assertTrue(wren != null, "no Doctor Wren");
		QaSicknessGameTests.gather(helper, level, s, player, wren);
		Arcs.NEAR = 96; // a player is about (the herb patches are 200 blocks and more away)
		wren.kill();
		for (int d = 1; d <= 4 && Arcs.running(level, hall) == s; d++) {
			QaSicknessGameTests.setDay(level, day + 1 + d, 1000);
			QaSicknessGameTests.runRound(level, hall, s, 40);
		}
		helper.assertTrue(s.flags.containsKey(SicknessStory.CURED) && QaSicknessGameTests.grey(people) == 0,
			"Doctor Wren was killed during the brew and nobody took his place: after 4 days the tonic hadn't gone round (coughing "
				+ QaSicknessGameTests.grey(people) + ", healer " + SicknessStory.healer(level, s) + ", chapter " + s.chapter + ")");
		helper.succeed();
	}
}

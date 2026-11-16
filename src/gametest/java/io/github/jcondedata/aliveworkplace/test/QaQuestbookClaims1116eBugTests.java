package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.compat.ftbquests.FtbQuestsBook;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * QA (lane-e-1116) for 26.8: questbook sentences the mod disagrees with, found by
 * {@link QaQuestbookClaims1116eGameTests}'s approach (the text is the spec). Fixed (B1318 in the mod, B1319 and B1320 in
 * the book, each test now checking the book against the mod). Each is alone in its batch.
 */
public class QaQuestbookClaims1116eBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * "Build a Starter Cottage": "this quest's reward puts Starter Cottage II in your Blueprint Table." The reward is
	 * {@code /workplace unlock {p} aliveworkplace:starter_cottage_2}, run when the player claims it, wherever they stand
	 * (the quest completes when the builder finishes, the owner anywhere online, and the book can be opened anywhere).
	 * Claimed by the hall's owner while away from the village, the build should still be on the village's Table.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaQb1116eBugRewardAway"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaQb1116eBugRewardAway")
	public void qaARewardClaimedAwayFromTheVillageStillReachesItsTable(GameTestHelper helper) {
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = was);
		VillageHallBlockEntity entity = QaQuestbookClaims1116eGameTests.hall(helper);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = entity.getBlockPos();
			VillageHalls.forgetNearest();
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.STARTER_COTTAGE_2.id()) == BlueprintUnlocks.State.LOCKED,
				"setup: Starter Cottage II isn't locked in a new village");
			BlockPos away = null;
			for (int blocks : new int[] {200, 300, 500, 800, 1200}) {
				BlockPos spot = hall.offset(blocks, 0, 0);
				if (VillageHalls.nearest(level, spot).isEmpty()) {
					away = spot;
					break;
				}
			}
			helper.assertTrue(away != null, "setup: no spot away from every village");
			ServerPlayer owner = QaQuestbookClaims1116eGameTests.playerAt(helper, away.getCenter());
			entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
			QaQuestbookClaims1116eGameTests.reward(level, owner, StarterBlueprints.STARTER_COTTAGE_2.id());
			helper.assertTrue(BlueprintUnlocks.state(level, hall, StarterBlueprints.STARTER_COTTAGE_2.id()) == BlueprintUnlocks.State.UNLOCKED,
				"the owner claimed the Starter Cottage's reward " + (away.getX() - hall.getX()) + " blocks from the hall and Starter Cottage II isn't on the "
				+ "village's Table: " + BlueprintUnlocks.state(level, hall, StarterBlueprints.STARTER_COTTAGE_2.id()));
			helper.succeed();
		});
	}

	/**
	 * B1319, fixed in the book: "Music: a Bandstand" said "Its name is gray at the Blueprint Table at first: it's locked"
	 * while the Bandstand is a rare build (rare.json), a dark ??? with no name. The quest now says so, and where its
	 * Blueprint is found: in a new village the Bandstand is HIDDEN with the rare hint, the quest's text says "???" and
	 * "rare" and never "gray", and every chest it names has the Bandstand's Blueprint in its loot table.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaQb1116eBugBandstand"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaQb1116eBugBandstand")
	public void qaTheBandstandQuestSaysItIsARareQuestionMark(GameTestHelper helper) {
		boolean was = BlueprintUnlocks.ENABLED;
		BlueprintUnlocks.ENABLED = true;
		Leftovers.after(helper, () -> BlueprintUnlocks.ENABLED = was);
		VillageHallBlockEntity entity = QaQuestbookClaims1116eGameTests.hall(helper);
		helper.runAfterDelay(3, () -> {
			BlueprintUnlocks.State state = BlueprintUnlocks.state(helper.getLevel(), entity.getBlockPos(), StarterBlueprints.BANDSTAND.id());
			helper.assertTrue(state == BlueprintUnlocks.State.HIDDEN, "the Bandstand at a new village's Table is " + state + ", not HIDDEN (???)");
			helper.assertTrue(BlueprintUnlocks.hint(StarterBlueprints.BANDSTAND.id()).endsWith(".rare"),
				"the Bandstand's hint is " + BlueprintUnlocks.hint(StarterBlueprints.BANDSTAND.id()));
			String quest = quest("Music: a Bandstand");
			helper.assertTrue(quest.contains("dark ???") && quest.contains("rare build") && !quest.contains("gray"),
				"the Bandstand quest doesn't say it's a rare ???: " + quest);
			String[][] chests = {{"pirate_ship", "pirate ship"}, {"war_camp", "war camp"}, {"coven_hut", "coven hut"}, {"piglin_hoard", "piglin hoard"},
				{"bandit_camp", "bandit camp"}, {"pharaohs_tomb", "pharaoh's tomb"}};
			for (String[] chest : chests) {
				helper.assertTrue(quest.contains(chest[1]), "the Bandstand quest doesn't name the " + chest[1]);
				helper.assertTrue(resource(helper, "loot_table/chests/" + chest[0] + ".json").contains("\"aliveworkplace:bandstand\""),
					"the quest says the " + chest[1] + " has the Bandstand's Blueprint; its loot table doesn't");
			}
			helper.succeed();
		});
	}

	/**
	 * B1320, fixed in the book: "Guards: the Barracks" (fill it with guards) said "3 raiders, plus one for every 4
	 * villagers", but guards add their share. The quest now names it: one for every 2 guards, up to 4 more, never more
	 * than 16. A village of 12 draws 6 with no guards, 7 with 2, 10 with 8 and with 20; one of 60 with 8 draws 16.
	 */
	//$ gametest_batch AREA '"qaQb1116eBugRaidGuards"'
	@GameTest(template = AREA, batch = "qaQb1116eBugRaidGuards")
	public void qaTheBarracksQuestGivesTheRaidSizeWithItsGuards(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(2, 2, 2));
		helper.assertTrue(Lairs.near(level, hall).isEmpty() && Threats.LEVEL < 3, "setup: a lair near, or a hard threat level");
		String quest = quest("Guards: the Barracks");
		helper.assertTrue(quest.contains("3 raiders, plus one for every 4 villagers and one for every 2 guards (up to 4 more, in iron), never more than 16"),
			"the Barracks quest doesn't give the guards' share of a raid: " + quest);
		for (int[] c : new int[][] {{12, 0, 6}, {12, 2, 7}, {12, 3, 7}, {12, 8, 10}, {12, 20, 10}, {60, 8, 16}}) {
			int n = VillageRaids.expected(level, hall, c[0], c[1], Threats.MONSTERS);
			helper.assertTrue(n == c[2], c[0] + " villagers and " + c[1] + " guards draw " + n + " raiders; the quest gives " + c[2]);
		}
		helper.succeed();
	}

	/** The text of the quest titled {@code title} in the questbook's chapters (from its title to the next quest's). */
	static String quest(String title) {
		String key = "title: \"" + title + "\"";
		try {
			for (FtbQuestsBook.Chapter chapter : FtbQuestsBook.manifest()) {
				String text;
				try (InputStream in = FtbQuestsBook.resource(chapter.file())) {
					text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}
				int at = text.indexOf(key);
				if (at >= 0) {
					int next = text.indexOf("title: \"", at + key.length());
					return text.substring(at, next < 0 ? text.length() : next);
				}
			}
		} catch (IOException e) {
			throw new AssertionError("can't read the questbook", e);
		}
		throw new AssertionError("no quest titled " + title + " in the questbook");
	}

	/** A data file of the mod, as text. */
	static String resource(GameTestHelper helper, String path) {
		try (InputStream in = helper.getLevel().getServer().getResourceManager().getResourceOrThrow(AliveWorkplace.id(path)).open()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new AssertionError("can't read " + path, e);
		}
	}
}

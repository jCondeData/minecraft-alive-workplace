package io.github.jcondedata.aliveworkplace.test;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA lane E, 2026-10-27 (player-facing sentences, B497's and B608's kind): {@code chronicle.aliveworkplace.someone},
 * "Someone", is the stand-in for a name that's gone. It's written for the start of a sentence, but the chronicle puts it
 * mid-sentence too: a caravan or train from a village no longer on the caravans' list (Caravans and CaravanRoads'
 * nameOf), and the Gray Cough cured with no gatherer named (SicknessStory). Each line is rendered with the stand-in, as
 * the mod passes it, and must not read "from Someone" or "thanks to Someone".
 */
public class QaSomeoneMidSentenceBugTests implements FabricGameTest {
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaSomeoneIsNeverCapitalizedMidSentence(GameTestHelper helper) {
		// the stand-ins the mod passes now (B611): Chronicle.otherVillage for an unknown village, someone_mid for no gatherer
		Component someone = io.github.jcondedata.aliveworkplace.hall.Chronicle.otherVillage(null);
		helper.assertTrue(someone.getString().equals("another village"), "the unknown village reads " + someone.getString());
		Component nobody = Component.translatable("chronicle.aliveworkplace.someone_mid");
		Component goods = Component.literal("12 Wheat");
		List<Component> lines = List.of(
			Component.translatable("chronicle.aliveworkplace.caravan_came", someone, goods),
			Component.translatable("chronicle.aliveworkplace.train_came", someone, goods),
			Component.translatable("chronicle.aliveworkplace.waystone_came", someone, goods),
			Component.translatable("chronicle.aliveworkplace.caravan_unsold", someone, goods),
			Component.translatable("chronicle.aliveworkplace.train_walked", someone),
			Component.translatable("chronicle.aliveworkplace.waystone_walked", someone),
			Component.translatable("chronicle.aliveworkplace.caravan_road_finished", someone),
			Component.translatable("chronicle.aliveworkplace.sickness.cured", 4, nobody));
		List<String> wrong = new ArrayList<>();
		for (Component line : lines) {
			String text = line.getString();
			if (text.matches(".*[a-z,] (Someone|Another village).*")) {
				wrong.add("\"" + text + "\"");
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + lines.size() + " chronicle lines put \"Someone\" mid-sentence: " + wrong);
		String came = lines.get(0).getString();
		helper.assertTrue(came.equals("A caravan came from another village with 12 Wheat"), "the caravan line reads " + came);
		String cured = lines.get(lines.size() - 1).getString();
		helper.assertTrue(cured.endsWith("well again, thanks to someone."), "the cure line reads " + cured);
		helper.succeed();
	}

	/**
	 * The other way round: {@code steward.aliveworkplace.jobs.someone}, "a villager", written for mid-sentence use, starts
	 * the chronicle's road, wall and train lines when the builder isn't loaded (Roads, Walls, CaravanTrains pass it as
	 * the line's first word): "a villager finished the village's Stone wall". A chronicle line starts with a capital.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaAChronicleLineNeverStartsWithALowerCaseStandIn(GameTestHelper helper) {
		Component villager = io.github.jcondedata.aliveworkplace.hall.Chronicle.lineStarter(null); // what the mod passes now (B611)
		helper.assertTrue(villager.getString().equals("A villager"), "the stand-in reads " + villager.getString());
		Component stone = Component.translatable("wall_kit.aliveworkplace.stone");
		List<Component> lines = List.of(
			io.github.jcondedata.aliveworkplace.city.Roads.builtLine(villager, 40, io.github.jcondedata.aliveworkplace.city.RoadStyles.forBlueprintStyle("")),
			Component.translatable("chronicle.aliveworkplace.wall_built", villager, stone),
			Component.translatable("chronicle.aliveworkplace.wall_started", villager, stone),
			Component.translatable("chronicle.aliveworkplace.train_built", villager),
			Component.translatable("chronicle.aliveworkplace.train_track_laid", villager, Component.literal("Ashdale")));
		List<String> wrong = new ArrayList<>();
		for (Component line : lines) {
			String text = line.getString();
			if (!text.isEmpty() && Character.isLowerCase(text.charAt(0))) {
				wrong.add("\"" + text + "\"");
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + lines.size() + " chronicle lines start in lower case: " + wrong);
		helper.succeed();
	}
}

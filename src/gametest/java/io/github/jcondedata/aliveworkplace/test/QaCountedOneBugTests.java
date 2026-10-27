package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.wood.TreeFarms;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA lane E, 2026-10-27 (player-facing sentences: a count of one reads in the singular, as Words.counted's .one lines do
 * everywhere else). Lines whose count is often 1 have no .one form and read "1 children", "1 trees felled": the
 * Teacher's status with one pupil (TeacherWork passes inClass/called when above 0), the Lumberjack's tree-farm count
 * (TreeFarms.sendStatus, from 0 up) and the drill day's chronicle line (ReformStories passes the drillers).
 */
public class QaCountedOneBugTests implements FabricGameTest {
	private static final Pattern ONE_PLURAL = Pattern.compile("\\b1 (children|trees|saplings|villagers)\\b");

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaACountOfOneReadsInTheSingular(GameTestHelper helper) {
		List<Component> lines = List.of(
			Words.counted("message.aliveworkplace.teacher.state.teaching", 1, 1),
			Words.counted("message.aliveworkplace.teacher.state.calling", 1, 1),
			TreeFarms.countsLine(1, 1),
			TreeFarms.countsLine(1, 0),
			TreeFarms.countsLine(0, 1),
			Words.counted("chronicle.aliveworkplace.reform_story.drilled", 1, "Ashdale", 1));
		List<String> wrong = new ArrayList<>();
		for (Component line : lines) {
			String text = line.getString();
			if (ONE_PLURAL.matcher(text).find()) {
				wrong.add("\"" + text + "\"");
			}
		}
		String two = Component.translatable("message.aliveworkplace.teacher.state.teaching", 2).getString();
		helper.assertTrue(two.equals("teaching 2 children"), "setup: two pupils read " + two);
		String mixed = TreeFarms.countsLine(1, 3).getString();
		helper.assertTrue(mixed.equals("1 tree felled, 3 saplings planted"), "the tree farm reads " + mixed);
		String drilled = Words.counted("chronicle.aliveworkplace.reform_story.drilled", 1, "Ashdale", 1).getString();
		helper.assertTrue(drilled.equals("Drill day in Ashdale: 1 villager sparred at the dummies."), "the drill day reads " + drilled);
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + lines.size() + " lines read a plural after 1: " + wrong);
		helper.succeed();
	}
}

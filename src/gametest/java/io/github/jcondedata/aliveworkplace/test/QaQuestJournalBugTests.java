package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.story.Objectives;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA bug tests for 31.2/31.3 (not registered: they fail on main until the text is fixed). Each is named after its bug.
 */
public class QaQuestJournalBugTests implements FabricGameTest {
	/** A kill objective for more than one reads "Defeat 2 Zombie" in the journal, the bar and the chat list. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void killLineForSeveralIsNotSingular(GameTestHelper helper) {
		String line = new Objectives.Kill("minecraft:zombie", 2, false).line().getString();
		helper.assertTrue(!line.equals("Defeat 2 Zombie"), "the kill line reads: " + line);
		helper.succeed();
	}

	/** "That quest isn't open any more." is British; American English (owner, 2026-10-26) says "anymore". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void questGoneMessageIsAmericanEnglish(GameTestHelper helper) {
		String text = Component.translatable("command.aliveworkplace.quests.gone").getString();
		helper.assertTrue(!text.contains("any more"), "the message reads: " + text);
		helper.succeed();
	}
}

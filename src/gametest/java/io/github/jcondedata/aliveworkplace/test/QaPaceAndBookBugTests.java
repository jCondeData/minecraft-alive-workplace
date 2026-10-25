package io.github.jcondedata.aliveworkplace.test;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;

/**
 * QA (lane-c-1025-1853), a failing test for a bug: 30.2's status line reads its sources mid-sentence ("25% slower
 * (held back by being ill, ...)"), but a disaster's label starts with a capital: "held back by A disaster in the
 * village". Every other source is lower case or a proper name.
 */
public class QaPaceAndBookBugTests implements FabricGameTest {
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaADisasterReadsLowerCaseInTheStatusLine(GameTestHelper helper) {
		String line = Component.translatable("message.aliveworkplace.pace.held_back",
			Component.translatable("pace.aliveworkplace.source.disaster")).getString();
		helper.assertTrue(line.equals("held back by a disaster in the village"), "the status line reads: " + line);
		helper.succeed();
	}
}

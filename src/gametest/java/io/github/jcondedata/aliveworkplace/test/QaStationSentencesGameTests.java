package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

/**
 * QA lane E, 2026-10-27 (player-facing sentences): a job item handed to a jobless villager with no free workstation near
 * says "The &lt;job&gt; job needs a &lt;block&gt;: stand them next to one, then hand them this again." (Stations.choose
 * names each station's first job and its block). For every station in the plain suite the sentence names a block (not
 * air, no raw key) and reads as English ("an" before a block that starts with a vowel).
 */
public class QaStationSentencesGameTests implements FabricGameTest {
	private static final Pattern A_BEFORE_VOWEL = Pattern.compile("\\b[aA] (?:[AEIOaeio]|U(?!ni|s[eu]))");

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyStationsNeedsSentenceNamesItsBlockInEnglish(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		int read = 0;
		for (Stations.Station station : Stations.ALL) {
			if (station.block() == Blocks.AIR || station.jobs().isEmpty()) {
				continue; // another mod's station, not installed here
			}
			read++;
			Component job = Stations.name(station.jobs().get(0).profession().get());
			String text = Component.translatable("message.aliveworkplace.job.needs_station", job, station.block().getName()).getString();
			if (A_BEFORE_VOWEL.matcher(text).find() || text.contains("block.") || text.contains("entity.") || text.contains("Air")) {
				wrong.add("\"" + text + "\"");
			}
		}
		helper.assertTrue(read >= 15, "setup: only " + read + " stations read");
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " of " + read + " stations' sentences read wrong: " + wrong);
		helper.succeed();
	}
}

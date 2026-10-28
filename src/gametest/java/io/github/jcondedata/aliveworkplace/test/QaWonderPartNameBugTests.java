package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.TALL;
import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.begin;
import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.strings;
import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.village;

import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;

/**
 * QA bug test (fails on 7b804ab4e): a Wonder part's raw name reaches the owner when the builder takes the part up,
 * "Builder is starting on Stage 1 0 next." (Builders.startNext, message.aliveworkplace.queue.next). Register this class
 * in src/gametest/resources/fabric.mod.json together with the fix.
 */
public class QaWonderPartNameBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	/**
	 * B138 named the part's raw-looking name ("Stage 1 0") as part of the bug: no line the owner reads while the builder
	 * takes up and raises a Wonder part names it that way (the builder starting on the part included).
	 */
	//$ gametest_ticks_batch TALL '600' '"qaWonderPartRawName"'
	@GameTest(template = TALL, timeoutTicks = 600, batch = "qaWonderPartRawName")
	public void theOwnerNeverReadsAPartsRawStageName(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		ServerLevel level = v.level();
		WonderSite site = begin(helper, v);
		UUID partId = site.parts().get(0);
		java.util.regex.Pattern raw = java.util.regex.Pattern.compile("Stage \\d+ \\d+");
		helper.succeedWhen(() -> {
			BuilderJob job = ModAttachments.BUILDER_JOB.get(v.builder());
			helper.assertTrue(job != null && job.siteId().equals(partId), "the builder hasn't taken the part yet: " + job);
			List<String> lines = strings(v.heard());
			List<String> rawLines = lines.stream().filter(l -> raw.matcher(l).find()).toList();
			helper.assertTrue(rawLines.isEmpty(), "the owner read the part's raw name: " + rawLines);
		});
	}
}

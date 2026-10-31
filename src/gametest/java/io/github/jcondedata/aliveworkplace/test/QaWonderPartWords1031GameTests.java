package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.TALL;
import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.begin;
import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.strings;
import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.village;

import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1031-0637) for B138 and B678 from their spec: the owner never reads a Wonder part's raw-looking name
 * ("Stage 1 0"): not when the builder takes the part up (B678), and an ordinary queued build is still announced by its
 * own name (the line B678 kept for every other build).
 */
public class QaWonderPartWords1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final Pattern RAW = Pattern.compile("Stage \\d+ \\d+");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/**
	 * B678's other branch: a builder with nothing to do who takes up an ordinary queued build still tells its owner
	 * "&lt;builder&gt; is starting on &lt;the build's name&gt; next.", word for word (the Wonder line is for Wonder parts only).
	 */
	//$ gametest_ticks_batch TALL '200' '"qaWonderWordsOrdinary"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaWonderWordsOrdinary")
	public void anOrdinaryQueuedBuildIsStillAnnouncedByItsName(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		ServerLevel level = v.level();
		BuildSite hut = Builders.enqueue(level, v.builder(), v.owner().getUUID(), "Owner", TEST_HUT,
			new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(),
				helper.absolutePos(new BlockPos(12, 2, 4)), Rotation.NONE, Mirror.NONE));
		BuildSite taken = Builders.activeSite(level, v.builder());
		try {
			helper.assertTrue(taken != null && taken.id().equals(hut.id()), "the builder didn't take up the queued hut: " + taken);
			List<String> lines = strings(v.heard());
			String expected = v.builder().getDisplayName().getString() + " is starting on "
				+ io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(TEST_HUT).getString() + " next.";
			helper.assertTrue(lines.contains(expected), "the owner should read '" + expected + "', but read: " + lines);
			helper.assertTrue(lines.stream().noneMatch(l -> l.contains("part of stage")), "an ordinary build was told as a Wonder part: " + lines);
		} finally {
			BuildSiteManager.get(level).remove(hut.id());
			ModAttachments.BUILDER_JOB.remove(v.builder());
		}
		helper.succeed();
	}

	/** Takes up the Wonder's first part the way the builder's own loop does, and returns it. */
	private static BuildSite takeUpPart(GameTestHelper helper, QaWonderPartsGameTests.Village v, WonderSite site) {
		BuildSite part = Builders.activeSite(v.level(), v.builder());
		BuilderJob job = ModAttachments.BUILDER_JOB.get(v.builder());
		helper.assertTrue(part != null && job != null && site.parts().contains(part.id()) && job.siteId().equals(part.id()),
			"the builder didn't take up the Wonder's part: " + part + ", job " + job);
		return part;
	}

	/**
	 * B678 with the owner logged off when the builder takes up the Wonder's part: no line is sent to anyone (nobody to
	 * tell), nothing breaks, and the builder still takes the part up.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaWonderWordsOffline"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaWonderWordsOffline")
	public void aPartTakenUpWithTheOwnerAwayStillStarts(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		WonderSite site = begin(helper, v);
		v.level().getServer().getPlayerList().remove(v.owner());
		v.heard().clear();
		takeUpPart(helper, v, site);
		helper.assertTrue(v.heard().isEmpty(), "the logged-off owner was told: " + strings(v.heard()));
		helper.succeed();
	}

	/**
	 * A part left queued on a Wonder whose stage is out of range (stage 0, not started, or past its last: finished) is
	 * taken up without a stage line, and without breaking the builder's take-up (no "stage 0", no stage past the last).
	 */
	//$ gametest_ticks_batch TALL '200' '"qaWonderWordsOutOfRange"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaWonderWordsOutOfRange")
	public void aPartOfAWonderPastItsLastStageGetsNoStageLine(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		WonderSite site = begin(helper, v);
		BuildSite part = BuildSiteManager.get(v.level()).get(site.parts().get(0));
		int stages = io.github.jcondedata.aliveworkplace.wonder.Wonders.loaded().get(site.wonder()).stages().size();
		for (int stage : new int[] {0, stages + 1}) {
			io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(v.level()).setStage(site.id(), stage);
			var line = io.github.jcondedata.aliveworkplace.wonder.WonderStages.partNextLine(v.level(), part, v.builder().getDisplayName());
			helper.assertTrue(line.isEmpty(), "a part of a Wonder at stage " + stage + " of " + stages + " got a stage line: " + line.map(c -> c.getString()));
		}
		io.github.jcondedata.aliveworkplace.wonder.WonderManager.get(v.level()).setStage(site.id(), stages);
		var last = io.github.jcondedata.aliveworkplace.wonder.WonderStages.partNextLine(v.level(), part, v.builder().getDisplayName());
		helper.assertTrue(last.isPresent() && last.get().getString().contains("stage " + stages + " of the"),
			"a part of the last stage (" + stages + ") should be told as that stage: " + last.map(c -> c.getString()));
		helper.succeed();
	}
}

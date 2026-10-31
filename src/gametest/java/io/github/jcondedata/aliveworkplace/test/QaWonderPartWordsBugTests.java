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
 * Bug tests (lane-b-1031-0637, B138's expectation "never 'Stage 1 0'"): with B678 fixed, the owner still reads a Wonder
 * part's raw structure name ("Stage 1 0", from wonder/<id>/stage_1_0 with no lang name) in three places while a builder
 * raises the part: the builder's status when sneak-right-clicked, the status over his head, and the Village Hall's list
 * of buildings going up. Expected: the Wonder and its stage (as WonderStages.partNextLine words it), never "Stage 1 0".
 */
public class QaWonderPartWordsBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final Pattern RAW = Pattern.compile("Stage \\d+ \\d+");
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** Takes up the Wonder's first part the way the builder's own loop does, and returns it. */
	private static BuildSite takeUpPart(GameTestHelper helper, QaWonderPartsGameTests.Village v, WonderSite site) {
		BuildSite part = Builders.activeSite(v.level(), v.builder());
		BuilderJob job = ModAttachments.BUILDER_JOB.get(v.builder());
		helper.assertTrue(part != null && job != null && site.parts().contains(part.id()) && job.siteId().equals(part.id()),
			"the builder didn't take up the Wonder's part: " + part + ", job " + job);
		return part;
	}

	/**
	 * B138's expectation ("a Wonder line ... never 'Stage 1 0'") from the player's own entry point: the owner sneaks and
	 * right-clicks (empty hand) the builder raising stage I's part to ask what he's doing; no line names "Stage 1 0".
	 */
	//$ gametest_ticks_batch TALL '200' '"qaWonderWordsStatus"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaWonderWordsStatus")
	public void askingTheBuilderOfAWonderPartNeverNamesItsRawStage(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		WonderSite site = begin(helper, v);
		takeUpPart(helper, v, site);
		v.heard().clear();
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		v.owner().setShiftKeyDown(true);
		v.owner().teleportTo(v.builder().getX() + 1, v.builder().getY(), v.builder().getZ());
		// the game's own use-entity event, as the server fires it for a right-click on an entity
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(v.owner(), v.level(), InteractionHand.MAIN_HAND, v.builder(), null);
		v.owner().setShiftKeyDown(false);
		List<String> lines = strings(v.heard());
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA B138 status: {}", lines);
		helper.assertTrue(!lines.isEmpty(), "sneak-right-clicking the builder told the owner nothing");
		List<String> raw = lines.stream().filter(l -> RAW.matcher(l).find()).toList();
		helper.assertTrue(raw.isEmpty(), "the owner read the part's raw name when asking the builder: " + raw);
		helper.succeed();
	}

	/** The words over the head of a builder raising a Wonder part (the overhead status) never name "Stage 1 0" (B138). */
	//$ gametest_ticks_batch TALL '200' '"qaWonderWordsOverhead"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaWonderWordsOverhead")
	public void theStatusOverAWonderBuildersHeadNeverNamesItsRawStage(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		WonderSite site = begin(helper, v);
		BuildSite part = takeUpPart(helper, v, site);
		var status = io.github.jcondedata.aliveworkplace.build.BuilderStatusSync.status(v.level(), part, v.builder());
		helper.assertTrue(status != null, "a builder raising a Wonder part shows no status over his head");
		String title = status.title().getString();
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA B138 overhead: {} / {}", title, status.line().getString());
		helper.assertTrue(!RAW.matcher(title).find() && !RAW.matcher(status.line().getString()).find(),
			"the status over the builder's head reads the part's raw name: '" + title + "' / '" + status.line().getString() + "'");
		helper.succeed();
	}

	/** The Village Hall's list of the buildings going up names a Wonder part by its Wonder, never "Stage 1 0" (B138). */
	//$ gametest_ticks_batch TALL '200' '"qaWonderWordsHall"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaWonderWordsHall")
	public void theHallsBuildsListNeverNamesAPartsRawStage(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		WonderSite site = begin(helper, v);
		takeUpPart(helper, v, site);
		List<String> names = io.github.jcondedata.aliveworkplace.hall.VillageHalls.census(v.level(), v.hall()).builds().stream()
			.map(b -> b.name().getString()).toList();
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("QA B138 hall builds: {}", names);
		List<String> raw = names.stream().filter(n -> RAW.matcher(n).find()).toList();
		helper.assertTrue(raw.isEmpty(), "the hall's builds list names the part by its raw name: " + raw);
		helper.succeed();
	}
}

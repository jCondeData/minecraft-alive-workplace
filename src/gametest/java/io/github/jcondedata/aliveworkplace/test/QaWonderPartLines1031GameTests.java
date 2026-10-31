package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.TALL;
import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.begin;
import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.strings;
import static io.github.jcondedata.aliveworkplace.test.QaWonderPartsGameTests.village;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (qa-lane-b-1031-1238) for B703 and B138: a Wonder's part is never named by its raw structure name ("Stage 1 0")
 * and no line tells of a blueprint for it. Its tests cover the sneak-click status, the overhead status, the hall's list
 * and the waiting line; these cover the places they don't: the owner's {@code /workplace sites} list, a part waiting in
 * a busy builder's queue (the "Next up" and "waiting until" lines), and the builder of a part dying. Expected wording
 * (en_us.json, message.aliveworkplace.wonder.part_name): "Stage 1 part of the Test Folly".
 */
public class QaWonderPartLines1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final Pattern RAW = Pattern.compile("Stage \\d+ \\d+");
	private static final String PART = "Stage 1 part of the Test Folly";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** Every message the owner reads after running {@code command} as himself. */
	private static List<String> run(QaWonderPartsGameTests.Village v, String command) {
		v.heard().clear();
		v.level().getServer().getCommands().performPrefixedCommand(v.owner().createCommandSourceStack(), command);
		return strings(v.heard());
	}

	/**
	 * The owner runs /workplace sites while his builder raises stage I's part: the part's line opens with the builder,
	 * "Stage 1 part of the Test Folly" and its percentage (message.aliveworkplace.status.header: "%s — %s — %s%%"), and
	 * no line names "Stage 1 0".
	 */
	//$ gametest_ticks_batch TALL '200' '"qaPartLines1031Sites"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaPartLines1031Sites")
	public void theOwnersSiteListNamesAPartByItsWonder(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		WonderSite site = begin(helper, v);
		BuildSite part = Builders.activeSite(v.level(), v.builder());
		helper.assertTrue(part != null && site.parts().contains(part.id()), "the builder didn't take up the Wonder's part: " + part);
		List<String> lines = run(v, "workplace sites");
		AliveWorkplace.LOG.info("QA B703 /workplace sites: {}", lines);
		helper.assertTrue(lines.stream().noneMatch(l -> RAW.matcher(l).find()), "/workplace sites names the part's raw stage: " + lines);
		String who = v.builder().getDisplayName().getString();
		Pattern header = Pattern.compile("^" + Pattern.quote(who + " — " + PART + " — ") + "\\d+%$");
		helper.assertTrue(lines.stream().anyMatch(l -> header.matcher(l.split("\n")[0]).matches()),
			"no /workplace sites line opens '" + who + " — " + PART + " — N%': " + lines);
		helper.succeed();
	}

	/**
	 * The builder is busy with a Test Hut when stage I's part comes up. Wherever the part waits, it is named by its
	 * Wonder: in the builder's sneak-click status and in /workplace sites, every line that mentions it says
	 * "Stage 1 part of the Test Folly" and none says "Stage 1 0".
	 */
	//$ gametest_ticks_batch TALL '200' '"qaPartLines1031Queued"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaPartLines1031Queued")
	public void aPartWaitingBehindAnotherBuildIsNamedByItsWonder(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		BuildSite hut = Builders.start(v.level(), v.builder(), v.owner(), TEST_HUT, new BlueprintData.Placement(v.level().dimension().location(),
			helper.absolutePos(new BlockPos(20, 2, 2)), Rotation.NONE, Mirror.NONE));
		helper.assertTrue(hut != null && hut.plan(v.level()) != null, "setup: no Test Hut site");
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(v.level()).get(hut.id()) != null) {
				BuildSiteManager.get(v.level()).remove(hut.id());
			}
		});
		WonderSite site = begin(helper, v);
		BuildSite part = BuildSiteManager.get(v.level()).get(site.parts().get(0));
		helper.assertTrue(part != null, "stage I's part has no site");
		// the builder's own status, as the owner asks for it (sneak, empty hand, right-click)
		v.heard().clear();
		v.owner().setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		v.owner().setShiftKeyDown(true);
		v.owner().teleportTo(v.builder().getX() + 1, v.builder().getY(), v.builder().getZ());
		net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(v.owner(), v.level(), InteractionHand.MAIN_HAND, v.builder(), null);
		v.owner().setShiftKeyDown(false);
		List<String> lines = new ArrayList<>(strings(v.heard()));
		lines.addAll(run(v, "workplace sites"));
		AliveWorkplace.LOG.info("QA B703 queued part (part builder {}, queued {}, hut active {}): {}", part.builder(), part.isQueued(),
			Builders.activeSite(v.level(), v.builder()) == hut, lines);
		helper.assertTrue(lines.stream().noneMatch(l -> RAW.matcher(l).find()), "a line names the waiting part's raw stage: " + lines);
		helper.assertTrue(lines.stream().anyMatch(l -> l.contains(PART)), "no line names the waiting part as '" + PART + "': " + lines);
		String hutName = Blueprints.displayName(TEST_HUT).getString();
		if (part.isQueued() && v.builder().getUUID().equals(part.builder())) {
			// in his queue: the hut's status lists it next, and its own line waits for the hut (status.next, status.queued)
			helper.assertTrue(lines.stream().anyMatch(l -> l.contains("Next up: " + PART)), "the status doesn't say 'Next up: " + PART + "': " + lines);
			String who = v.builder().getDisplayName().getString();
			helper.assertTrue(lines.stream().anyMatch(l -> l.contains(who + " — " + PART + " — waiting until " + hutName + " is finished")),
				"no line says the part waits until the " + hutName + " is finished: " + lines);
		}
		helper.succeed();
	}

	/**
	 * The builder raising stage I's part dies. The owner reads no line naming "Stage 1 0", no "Your builder died while
	 * building ... the blueprint dropped" (a Wonder part has no blueprint to drop, B138), and no blueprint of the part lies
	 * on the ground.
	 */
	//$ gametest_ticks_batch TALL '200' '"qaPartLines1031Died"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "qaPartLines1031Died")
	public void aPartsBuilderDyingDropsAndTellsNoBlueprint(GameTestHelper helper) {
		QaWonderPartsGameTests.Village v = village(helper);
		WonderSite site = begin(helper, v);
		BuildSite part = Builders.activeSite(v.level(), v.builder());
		helper.assertTrue(part != null && site.parts().contains(part.id()), "the builder didn't take up the Wonder's part: " + part);
		v.heard().clear();
		v.builder().kill();
		helper.runAfterDelay(5, () -> {
			List<String> lines = strings(v.heard());
			AliveWorkplace.LOG.info("QA B703 part builder died: {}", lines);
			helper.assertFalse(v.builder().isAlive(), "setup: the builder didn't die");
			helper.assertTrue(lines.stream().noneMatch(l -> RAW.matcher(l).find()), "after the death the owner reads the part's raw stage: " + lines);
			List<String> died = v.heard().stream().filter(c -> c.getContents() instanceof TranslatableContents t
				&& t.getKey().equals("message.aliveworkplace.builder_died")).map(Component::getString).toList();
			helper.assertTrue(died.isEmpty(), "the owner was told a blueprint dropped for a Wonder part: " + died);
			List<String> blueprints = new ArrayList<>();
			for (ItemEntity e : v.level().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
				BlueprintItem.data(e.getItem()).ifPresent(d -> blueprints.add(e.getItem().getHoverName().getString()));
			}
			helper.assertTrue(blueprints.isEmpty(), "a blueprint dropped where the part's builder died: " + blueprints);
			helper.assertTrue(BuildSiteManager.get(v.level()).get(part.id()) != null, "the part's site went with its builder (a part whose site is gone counts as built)");
			helper.succeed();
		});
	}
}

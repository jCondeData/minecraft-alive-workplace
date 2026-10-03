package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The build site's bookkeeping at the edges of a stage: the material look-ahead while the builder retries the steps it
 * put off, and the progress shown on the bench and the blueprint at each stage. Found by the full check's mutants
 * (ROADMAP 21.2): {@code BuildSite.upcoming} reading one deferred step too many, and {@code BuildSite.progress} at the
 * start and end of the build, went unnoticed.
 */
public class BuildSiteStepsGameTests implements FabricGameTest {
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	private record Setup(BuildSite site, BuildPlan plan) {
	}

	/** A test-hut site and its plan, without a world to look at (so no foundation or landscaping). */
	private static Setup setup(GameTestHelper helper) {
		BlueprintData.Placement placement = new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = new BuildSite(UUID.fromString("0f1e2d3c-4b5a-6978-8796-a5b4c3d2e1f1"), UUID.fromString("12345678-9abc-def0-1234-56789abcdef1"),
			"Jesse", TEST_HUT, placement);
		BuildPlan plan = BuildPlan.create(BlueprintLibrary.get(helper.getLevel(), TEST_HUT).orElseThrow(), placement);
		helper.assertTrue(plan.steps(BuildPlan.Stage.STRUCTURE).size() >= 4, "the test hut has too few structure steps: " + plan.steps(BuildPlan.Stage.STRUCTURE).size());
		helper.assertTrue(plan.steps(BuildPlan.Stage.FOUNDATION).isEmpty(), "a plan without a world has a foundation");
		return new Setup(site, plan);
	}

	/** Steps the site through to the structure stage, the way the builder does (clearing needs nothing here). */
	private static void toStructure(GameTestHelper helper, Setup s) {
		while (s.site().stage() != BuildPlan.Stage.STRUCTURE) {
			while (s.site().current(s.plan()) != null) {
				s.site().advance();
			}
			s.site().finishList();
			helper.assertTrue(s.site().stage() != BuildPlan.Stage.DONE, "the site skipped the structure stage");
		}
	}

	/**
	 * While the builder retries the steps it put off, the material look-ahead lists exactly those steps (from the one
	 * it is on), then the next stage's; it never reads past the deferred list.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void retryingLooksAheadAtOnlyTheDeferredSteps(GameTestHelper helper) {
		Setup s = setup(helper);
		toStructure(helper, s);
		List<BuildPlan.Step> structure = s.plan().steps(BuildPlan.Stage.STRUCTURE);
		List<BuildPlan.Step> decoration = s.plan().steps(BuildPlan.Stage.DECORATION);
		// Put off the 1st and 3rd steps, place the rest.
		for (int i = 0; s.site().current(s.plan()) != null; i++) {
			if (i == 0 || i == 2) {
				s.site().defer();
			} else {
				s.site().markPlaced();
			}
		}
		s.site().finishList();
		helper.assertTrue(s.site().stage() == BuildPlan.Stage.STRUCTURE, "the site left the structure stage with steps put off: " + s.site().stage());
		helper.assertTrue(s.site().current(s.plan()) == structure.get(0), "the retry didn't start at the first step put off");

		List<BuildPlan.Step> expected = new ArrayList<>(List.of(structure.get(0), structure.get(2)));
		expected.addAll(decoration);
		List<BuildPlan.Step> upcoming;
		try {
			upcoming = s.site().upcoming(s.plan(), 10_000);
		} catch (RuntimeException e) {
			throw new AssertionError("the look-ahead while retrying crashed: " + e, e);
		}
		helper.assertTrue(upcoming.equals(expected), "while retrying, the look-ahead should be the 2 steps put off then " + decoration.size()
			+ " decoration steps; got " + upcoming.size() + " steps, first " + (upcoming.isEmpty() ? "none" : upcoming.get(0).pos()));

		s.site().advance(); // the first one is placed now
		List<BuildPlan.Step> rest = s.site().upcoming(s.plan(), 10_000);
		helper.assertTrue(!rest.isEmpty() && rest.get(0) == structure.get(2) && rest.size() == 1 + decoration.size(),
			"after one retried step, the look-ahead should start at the other one: " + rest.size() + " steps");
		helper.assertTrue(s.site().upcoming(s.plan(), 1).equals(List.of(structure.get(2))), "a look-ahead of 1 should be just the step being retried");
		helper.succeed();
	}

	/**
	 * Progress is 0 while clearing, counts each placed block during the structure stage, counts a whole stage while its
	 * put-off steps are retried, and is complete once the house stands (landscaping) and when the site is done.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void progressFollowsTheStages(GameTestHelper helper) {
		Setup s = setup(helper);
		BuildPlan plan = s.plan();
		int total = plan.placeableCount();
		int structure = plan.steps(BuildPlan.Stage.STRUCTURE).size();
		helper.assertTrue(total == structure + plan.steps(BuildPlan.Stage.DECORATION).size(), "placeable count: " + total);
		helper.assertTrue(!plan.steps(BuildPlan.Stage.DECORATION).isEmpty(), "the test hut has no decoration steps");
		helper.assertTrue(s.site().stage() == BuildPlan.Stage.CLEAR && s.site().progress(plan) == 0f, "clearing should show 0%, not "
			+ s.site().progress(plan));

		toStructure(helper, s);
		helper.assertTrue(s.site().progress(plan) == 0f, "nothing placed yet should be 0%, not " + s.site().progress(plan));
		s.site().defer(); // the first step is put off
		s.site().markPlaced();
		s.site().markPlaced();
		helper.assertTrue(s.site().progress(plan) == 3f / total, "three steps through the structure should be 3/" + total + ", not "
			+ s.site().progress(plan) * total + "/" + total);
		while (s.site().current(plan) != null) {
			s.site().markPlaced();
		}
		s.site().finishList(); // retrying the one put off
		helper.assertTrue(s.site().stage() == BuildPlan.Stage.STRUCTURE && s.site().current(plan) != null, "the site should be retrying");
		helper.assertTrue(s.site().progress(plan) == structure / (float) total, "retrying should count the whole structure (" + structure + "/" + total
			+ "), not " + s.site().progress(plan) * total);
		s.site().markPlaced();
		s.site().finishList();
		helper.assertTrue(s.site().stage() == BuildPlan.Stage.DECORATION && s.site().progress(plan) == structure / (float) total,
			"the start of decorating should be " + structure + "/" + total + ", not " + s.site().progress(plan) * total);
		s.site().markPlaced();
		helper.assertTrue(s.site().progress(plan) == (structure + 1) / (float) total, "one decoration placed should be " + (structure + 1) + "/" + total);
		while (s.site().current(plan) != null) {
			s.site().markPlaced();
		}
		s.site().finishList();
		helper.assertTrue(s.site().stage() == BuildPlan.Stage.LANDSCAPE && s.site().progress(plan) == 1f, "landscaping should show 100%, not "
			+ s.site().progress(plan));
		s.site().finishList();
		helper.assertTrue(s.site().stage() == BuildPlan.Stage.DONE && s.site().isDone() && s.site().progress(plan) == 1f, "done should show 100%");
		helper.succeed();
	}
}

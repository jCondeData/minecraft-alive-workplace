package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

import java.util.UUID;

/**
 * B1049: a site whose blueprint was replaced since it was saved (a schematic uploaded again under its name) starts its
 * lists again instead of keeping a cursor and deferred steps that point into the old blueprint's lists, and a save's
 * deferred steps past the end of today's list never make the builder read past it.
 */
public class ReplacedBlueprintGameTests implements FabricGameTest {
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	private static BuildSite site(GameTestHelper helper) {
		return new BuildSite(UUID.randomUUID(), UUID.randomUUID(), "Jesse", TEST_HUT,
			new BlueprintData.Placement(helper.getLevel().dimension().location(), new BlockPos(1, 2, 3), Rotation.NONE, Mirror.NONE));
	}

	/** A save of {@code site} retrying its walls, with a deferred step far past the end of the list, and {@code hash} as its fingerprint (null: none). */
	private static CompoundTag retryingWalls(BuildSite site, Integer hash) {
		CompoundTag tag = site.save();
		tag.putString("stage", BuildPlan.Stage.STRUCTURE.name());
		tag.putBoolean("retrying", true);
		tag.putInt("cursor", 0);
		tag.putIntArray("deferred", new int[] {9999, 0});
		tag.remove("blueprint_hash");
		if (hash != null) {
			tag.putInt("blueprint_hash", hash);
		}
		return tag;
	}

	/** The fingerprint is saved with the site once it has a plan, and the same blueprint loads with its cursor kept. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theSameBlueprintKeepsItsPlaceAfterALoad(GameTestHelper helper) {
		BuildSite site = site(helper);
		helper.assertTrue(site.plan(helper.getLevel()) != null, "test hut blueprint missing");
		CompoundTag saved = site.save();
		helper.assertTrue(saved.contains("blueprint_hash"), "the blueprint's fingerprint wasn't saved: " + saved);
		saved.putString("stage", BuildPlan.Stage.STRUCTURE.name());
		saved.putInt("cursor", 3);
		BuildSite loaded = BuildSite.load(saved);
		helper.assertTrue(loaded != null && loaded.plan(helper.getLevel()) != null, "the site didn't load or plan");
		helper.assertTrue(loaded.stage() == BuildPlan.Stage.STRUCTURE && loaded.cursor() == 3,
			"an unchanged blueprint lost its place: " + loaded.stage() + " " + loaded.cursor());
		helper.succeed();
	}

	/** A blueprint changed since the save: back to the first list, nothing deferred from the old plan. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aChangedBlueprintStartsItsListsAgain(GameTestHelper helper) {
		BuildSite site = site(helper);
		helper.assertTrue(site.plan(helper.getLevel()) != null, "test hut blueprint missing");
		int hash = site.save().getInt("blueprint_hash");
		CompoundTag saved = retryingWalls(site, hash + 1);
		saved.putInt("cursor", 1);
		BuildSite loaded = BuildSite.load(saved);
		helper.assertTrue(loaded != null, "the site didn't load");
		BuildPlan plan = loaded.plan(helper.getLevel());
		helper.assertTrue(plan != null, "the site didn't plan");
		helper.assertTrue(loaded.stage() == BuildPlan.Stage.CLEAR && loaded.cursor() == 0 && !loaded.isRetrying() && loaded.retryLeft() == 0,
			"a replaced blueprint kept the old one's place: " + loaded.stage() + " cursor " + loaded.cursor() + " retrying " + loaded.isRetrying());
		helper.assertTrue(loaded.save().getInt("blueprint_hash") == hash, "the new blueprint's fingerprint wasn't recorded");
		helper.succeed();
	}

	/** An older save (no fingerprint) whose deferred steps point past today's list: they are dropped, never read. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void deferredStepsPastTheListAreDroppedNotRead(GameTestHelper helper) {
		BuildSite site = site(helper);
		helper.assertTrue(site.plan(helper.getLevel()) != null, "test hut blueprint missing");
		BuildSite loaded = BuildSite.load(retryingWalls(site, null));
		helper.assertTrue(loaded != null, "the site didn't load");
		BuildPlan plan = loaded.plan(helper.getLevel());
		helper.assertTrue(plan != null, "the site didn't plan");
		helper.assertTrue(loaded.stage() == BuildPlan.Stage.STRUCTURE, "an older save without a fingerprint restarted: " + loaded.stage());
		BuildPlan.Step step;
		try {
			step = loaded.current(plan);
		} catch (IndexOutOfBoundsException e) {
			throw new net.minecraft.gametest.framework.GameTestAssertException("a deferred step past the list was read: " + e);
		}
		helper.assertTrue(step != null && step.pos().equals(plan.steps(BuildPlan.Stage.STRUCTURE).get(0).pos()),
			"the valid deferred step isn't next: " + step);
		helper.assertTrue(loaded.retryLeft() == 1, "the stale deferred step is still counted: " + loaded.retryLeft());
		helper.succeed();
	}
}

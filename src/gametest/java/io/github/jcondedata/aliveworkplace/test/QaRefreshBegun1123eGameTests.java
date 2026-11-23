package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane E, 2026-11-23): B1500's {@code refresh_begun}, the older drawing a refresh of a finished building was begun
 * from, against the nightly mutation survivor in {@code BuildSiteManager.recordFinished} (forgetting it when another
 * building takes the spot). The spec (B1500's fix, docs/wiki/builder.md "Per finished building"): it is kept "from the
 * moment a Builder takes it up until a refresh of it finishes or the building is taken down", "kept with it" as
 * {@code repair_ask}, which is cleared "when the building is taken down or built again on its spot"; "absent: none begun,
 * as in every older save". The code's own reading of "built again": only another building (such as the next tier) drops
 * it. So: a Stone House left half refreshed and then upgraded to a Stone House II no longer has a refresh begun, and a
 * later refresh of the Stone House II starts from the drawing it stands as, never the old house's, across a save too.
 */
public class QaRefreshBegun1123eGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TIER_2 = AliveWorkplace.id("stone_house_2");
	/** Two older drawings of the Stone House family's day, both shipped for the Stone House and the Stone House II. */
	private static final String OCT_18 = "42864073a";
	private static final String OCT_20 = "aec09cb26";

	private static void reload(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		BuildSiteManager manager = BuildSiteManager.get(helper.getLevel());
		helper.getLevel().getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(manager.save(new CompoundTag(), registries), registries));
	}

	/** A new Builder at the first one's bench, working for the house's owner. */
	private static Villager newBuilder(GameTestHelper helper, QaJobFixes1122cGameTests.Stage s) {
		BlockPos bench = s.corner().offset(2, 0, 4);
		if (!s.level().getBlockState(helper.absolutePos(bench)).is(ModBlocks.BUILDERS_BENCH)) {
			helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		}
		Villager next = helper.spawn(EntityType.VILLAGER, s.corner().offset(2, 0, 2));
		Builders.employ(s.level(), next, helper.absolutePos(bench));
		ModAttachments.BUILDER_EMPLOYER.set(next, new Employer(s.owner().getUUID(), s.owner().getGameProfile().getName()));
		return next;
	}

	/**
	 * The furnished old Stone House: the owner presses Refresh, its Builder clears for {@code killAfter} ticks and is
	 * killed, and once the refresh site is dropped {@code then} runs with the drawing the refresh was begun from.
	 */
	private static void halfRefreshedThen(GameTestHelper helper, int killAfter, java.util.function.BiConsumer<QaJobFixes1122cGameTests.Stage, String> then) {
		QaJobFixes1122cGameTests.Stage s = QaJobFixes1122cGameTests.house(helper, Rotation.NONE);
		String[] begun = {null};
		boolean[] killed = {false};
		boolean[] done = {false};
		int[] waited = {0};
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(QaJobFixes1122cGameTests.pressRefresh(helper, s), "the old Stone House has no Refresh on the Repairs page");
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null, "no Builder took the refresh up");
			begun[0] = site.refreshFrom();
			helper.assertTrue(begun[0].equals(BuildSiteManager.get(s.level()).refreshBegun(s.placement())),
				"begun from " + begun[0] + " but remembered as " + BuildSiteManager.get(s.level()).refreshBegun(s.placement()));
		});
		helper.runAfterDelay(2 + killAfter, () -> {
			s.builder().kill();
			killed[0] = true;
		});
		helper.onEachTick(() -> {
			if (!killed[0] || done[0]) {
				return;
			}
			BuildSite left = Refreshes.siteOf(s.level(), s.placement());
			if (left != null && ++waited[0] < 200) {
				return; // (the dead Builder's site is dropped on his death)
			}
			helper.assertTrue(left == null, "the dead Builder's refresh site is still there 200 ticks after his death");
			helper.assertTrue(begun[0].equals(BuildSiteManager.get(s.level()).refreshBegun(s.placement())),
				"the half-done refresh isn't remembered as begun from " + begun[0] + " after the kill");
			done[0] = true;
			then.accept(s, begun[0]);
		});
	}

	/**
	 * (a) The half-refreshed Stone House upgraded to a Stone House II on the same spot (the old house taken away, the
	 * new tier standing, recorded finished as a Builder's finished build is): no refresh is begun any more, after a save
	 * and load too, and a later refresh of the Stone House II (once a mod update redrew it, so it stands as an older
	 * drawing) is offered and starts from the drawing the Stone House II stands as, not the old house's begun one.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaRefreshBegun1123eUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaRefreshBegun1123eUpgrade")
	public void qaAHalfRefreshedHouseUpgradedToItsNextTierForgetsTheBegunDrawing(GameTestHelper helper) {
		boolean[] checked = {false};
		halfRefreshedThen(helper, 300, (s, begun) -> {
			// The upgrade: the old house goes, the Stone House II stands where it stood (as a drawing of another day than begun).
			String standsAs = OCT_18.equals(begun) ? OCT_20 : OCT_18;
			Blueprint tier2 = Refreshes.olderDrawing(s.level().getServer(), TIER_2, standsAs)
				.orElseThrow(() -> new IllegalStateException("the Stone House II's " + standsAs + " drawing isn't shipped"));
			for (BlockPos pos : BuildPlan.create(s.asBuilt(), s.placement()).targets().keySet()) {
				s.level().setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(tier2, s.placement()).targets().entrySet()) {
				s.level().setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			}
			BuildSiteManager.get(s.level()).recordFinished(TIER_2, s.placement(), s.owner().getUUID());
			helper.assertTrue(BuildSiteManager.get(s.level()).refreshBegun(s.placement()) == null,
				"the Stone House II on the spot still has the old house's refresh begun from " + BuildSiteManager.get(s.level()).refreshBegun(s.placement()));
			reload(helper);
			helper.assertTrue(BuildSiteManager.get(s.level()).refreshBegun(s.placement()) == null,
				"after a save and load the Stone House II has a refresh begun from " + BuildSiteManager.get(s.level()).refreshBegun(s.placement()));
			String stands = Refreshes.builtFrom(s.level(), TIER_2, s.placement(), FurnitureChoice.VANILLA);
			helper.assertTrue(stands != null, "the Stone House II standing as its " + standsAs + " drawing doesn't read as an older drawing");
			Villager next = newBuilder(helper, s);
			helper.assertTrue(QaJobFixes1122cGameTests.pressRefresh(helper, s), "the Stone House II standing as an older drawing has no Refresh on the Repairs page");
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null && TIER_2.equals(site.structure()), "no Builder took the Stone House II's refresh up: " + site);
			helper.assertTrue(stands.equals(site.refreshFrom()), "the Stone House II (standing as " + stands + ") is refreshed from "
				+ site.refreshFrom() + "; the old house's refresh was begun from " + begun);
			helper.assertTrue(stands.equals(BuildSiteManager.get(s.level()).refreshBegun(s.placement())),
				"the Stone House II's refresh is remembered as begun from " + BuildSiteManager.get(s.level()).refreshBegun(s.placement()));
			AliveWorkplace.LOG.info("QA refresh begun 1123e: house begun from {}, Stone House II stands as {} (placed {}), refreshed from {}",
				begun, stands, standsAs, site.refreshFrom());
			next.kill();
			BuildSiteManager.get(s.level()).setRefreshAsked(s.placement(), false);
			checked[0] = true;
		});
		helper.succeedWhen(() -> helper.assertTrue(checked[0], "not checked yet"));
	}

	/**
	 * (b) The half-refreshed Stone House recorded finished again as the same building on the same spot (its furniture
	 * set again after, as a finished build sets it): the refresh is still begun from the same drawing ("kept until a
	 * refresh of it finishes or the building is taken down"), after a save and load too, and a new Builder takes it up
	 * from that drawing.
	 */
	//$ gametest_ticks_batch HUGE '3000' '"qaRefreshBegun1123eSame"'
	@GameTest(template = HUGE, timeoutTicks = 3000, batch = "qaRefreshBegun1123eSame")
	public void qaAHalfRefreshedHouseRecordedFinishedAgainKeepsTheBegunDrawing(GameTestHelper helper) {
		boolean[] checked = {false};
		halfRefreshedThen(helper, 300, (s, begun) -> {
			BuildSiteManager manager = BuildSiteManager.get(s.level());
			FurnitureChoice furniture = manager.furniture(s.placement());
			manager.recordFinished(s.structure(), s.placement(), s.owner().getUUID());
			manager.setFurniture(s.placement(), furniture);
			helper.assertTrue(begun.equals(manager.refreshBegun(s.placement())),
				"the same house recorded again forgot the refresh begun from " + begun + ": " + manager.refreshBegun(s.placement()));
			reload(helper);
			helper.assertTrue(begun.equals(BuildSiteManager.get(s.level()).refreshBegun(s.placement())),
				"after a save and load the refresh isn't begun from " + begun + ": " + BuildSiteManager.get(s.level()).refreshBegun(s.placement()));
			Villager next = newBuilder(helper, s);
			helper.assertTrue(QaJobFixes1122cGameTests.pressRefresh(helper, s), "the half-refreshed house has no Refresh on the Repairs page");
			BuildSite site = Refreshes.siteOf(s.level(), s.placement());
			helper.assertTrue(site != null, "the new Builder didn't take the refresh up");
			helper.assertTrue(begun.equals(site.refreshFrom()), "begun from " + begun + ", taken up again from " + site.refreshFrom());
			next.kill();
			BuildSiteManager.get(s.level()).setRefreshAsked(s.placement(), false);
			checked[0] = true;
		});
		helper.succeedWhen(() -> helper.assertTrue(checked[0], "not checked yet"));
	}

	/**
	 * (c) Saved data: an older save's finished building (no {@code refresh_begun}) loads with no refresh begun, a begun
	 * one is written and read back, another building recorded on the spot drops it in the save too, and taking the
	 * building down forgets it. A manager of its own, so no other test's buildings are touched.
	 */
	//$ gametest_batch EMPTY_STRUCTURE '"qaRefreshBegun1123eSaves"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaRefreshBegun1123eSaves")
	public void qaRefreshBegunSavesLoadsAndIsForgotten(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		BuildSiteManager manager = BuildSiteManager.load(new CompoundTag(), registries);
		ResourceLocation house = AliveWorkplace.id("stone_house");
		BlueprintData.Placement here = new BlueprintData.Placement(helper.getLevel().dimension().location(), new BlockPos(1000, 70, 1000), Rotation.NONE, Mirror.NONE);
		BlueprintData.Placement there = new BlueprintData.Placement(helper.getLevel().dimension().location(), new BlockPos(1040, 70, 1000), Rotation.CLOCKWISE_90, Mirror.NONE);
		UUID owner = UUID.randomUUID();
		manager.recordFinished(house, here, owner);
		manager.recordFinished(house, there, owner);
		manager.setRefreshBegun(here, OCT_20);

		// written and read back; the other building has none
		CompoundTag saved = manager.save(new CompoundTag(), registries);
		BuildSiteManager loaded = BuildSiteManager.load(saved, registries);
		helper.assertTrue(OCT_20.equals(loaded.refreshBegun(here)), "the begun drawing didn't survive a save: " + loaded.refreshBegun(here));
		helper.assertTrue(loaded.refreshBegun(there) == null, "a building with no refresh begun loads with one: " + loaded.refreshBegun(there));

		// an older save: the same entries without refresh_begun
		ListTag finished = saved.getList("finished", Tag.TAG_COMPOUND);
		int stripped = 0;
		for (int i = 0; i < finished.size(); i++) {
			if (finished.getCompound(i).contains("refresh_begun")) {
				finished.getCompound(i).remove("refresh_begun");
				stripped++;
			}
		}
		helper.assertTrue(stripped == 1, "the save held " + stripped + " refresh_begun entries, not 1");
		BuildSiteManager older = BuildSiteManager.load(saved, registries);
		helper.assertTrue(older.finishedIn(helper.getLevel()).size() == 2, "the older save's buildings didn't load: " + older.finishedIn(helper.getLevel()).size());
		helper.assertTrue(older.refreshBegun(here) == null && older.refreshBegun(there) == null,
			"an older save loads with a refresh begun: " + older.refreshBegun(here) + ", " + older.refreshBegun(there));

		// another building (the next tier) recorded on the spot: dropped, and not written back
		loaded.recordFinished(TIER_2, here, owner);
		helper.assertTrue(loaded.refreshBegun(here) == null, "the Stone House II on the spot keeps the house's refresh begun from " + loaded.refreshBegun(here));
		CompoundTag afterUpgrade = loaded.save(new CompoundTag(), registries);
		helper.assertTrue(BuildSiteManager.load(afterUpgrade, registries).refreshBegun(here) == null, "the save after the upgrade still holds a refresh begun");
		helper.assertTrue(!afterUpgrade.toString().contains("refresh_begun"), "the save after the upgrade still writes refresh_begun: " + afterUpgrade);

		// taken down: forgotten
		loaded.setRefreshBegun(there, OCT_18);
		loaded.forgetFinished(there);
		helper.assertTrue(loaded.refreshBegun(there) == null, "a building taken down keeps its refresh begun from " + loaded.refreshBegun(there));
		helper.assertTrue(!loaded.save(new CompoundTag(), registries).toString().contains("refresh_begun"), "a taken-down building's refresh_begun is still saved");
		helper.succeed();
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * QA (lane-b-1101-0938) for ROADMAP 37.3, from its spec and furniture-and-storage.md: "The choice is saved on the
 * site, so a restart doesn't change a build in progress"; "a restart, a changed order or a newly added mod never changes
 * a build going up". Played end to end with a real builder: the furniture order changes and the server restarts (every
 * site saved and loaded) after the build has begun and before its chair goes down. The test "mod"
 * {@code aliveworkplace_test_pieces} (its chair an upturned spruce stair) is the furniture mod.
 */
public class QaFurnitureOrderMidBuild1101GameTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	/** Starts the nook with {@code mods} as the order, stocked for its plan and two spare stairs; returns the site. */
	private static BuildSite start(GameTestHelper helper, List<String> mods, Villager builder, BlueprintData.Placement placement) {
		Furnishings.MODS = mods;
		BuildSite site = Builders.start(helper.getLevel(), builder, null, QaFurnishedGameTests.NOOK, placement);
		BuildPlan plan = site.plan(helper.getLevel());
		helper.assertTrue(plan != null, "no plan for the nook");
		QaFurnishedGameTests.stock(helper, plan);
		((Container) helper.getBlockEntity(QaFurnishedGameTests.CHEST)).setItem(20, new ItemStack(Items.SPRUCE_STAIRS, 2));
		return site;
	}

	/**
	 * Restarts the server with {@code then} as the order the first tick the build has placed something and its chair
	 * spot is still empty; then the nook must finish, nothing skipped, with {@code wantTop} deciding which chair stands.
	 */
	private static void run(GameTestHelper helper, List<String> first, List<String> then, boolean wantTop) {
		Leftovers.clear(helper);
		QaFurnishedGameTests.resetAfter(helper);
		List<String> mods = Furnishings.MODS;
		Leftovers.after(helper, () -> Furnishings.MODS = mods);
		ServerLevel level = helper.getLevel();
		Villager builder = QaFurnishedGameTests.builderWithChest(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(5, 2, 5)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = start(helper, first, builder, placement);
		helper.assertTrue(site.furniture().isVanilla() == first.isEmpty(), "the nook chose " + site.furniture() + " with the order " + first);
		BlockPos chairAt = placement.origin().offset(QaFurnishedGameTests.CHAIR);
		String[] restarted = {null};
		helper.onEachTick(() -> {
			if (restarted[0] != null) {
				return;
			}
			BuildSite live = BuildSiteManager.get(level).get(site.id());
			if (live == null) {
				restarted[0] = "missed: the nook finished before anything could change";
				return;
			}
			if (live.placed() == 0) {
				return;
			}
			if (!level.getBlockState(chairAt).isAir()) {
				restarted[0] = "missed: the chair went down with the first blocks";
				return;
			}
			var registries = level.registryAccess();
			Furnishings.MODS = then;
			level.getDataStorage().set(BuildSiteManager.NAME,
				BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
			restarted[0] = "at " + live.stage() + " cursor " + live.cursor() + ", " + live.placed() + " placed";
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0] != null, "the build never placed a block");
			helper.assertTrue(!restarted[0].startsWith("missed"), restarted[0]);
			BuildSiteManager manager = BuildSiteManager.get(level);
			BuildSite live = manager.get(site.id());
			helper.assertTrue(live == null, "still building (restarted " + restarted[0] + "): " + (live == null ? "" : live.stage() + " "
				+ live.status() + " missing " + live.missing()));
			BlockState chair = level.getBlockState(chairAt);
			boolean top = chair.is(Blocks.SPRUCE_STAIRS) && chair.getValue(StairBlock.HALF) == Half.TOP;
			boolean bottom = chair.is(Blocks.SPRUCE_STAIRS) && chair.getValue(StairBlock.HALF) == Half.BOTTOM;
			helper.assertTrue(wantTop ? top : bottom, "the order changed from " + first + " to " + then + " and the server restarted "
				+ restarted[0] + ": the finished nook's chair is " + chair + ", wanted the " + (wantTop ? "test mod's upturned" : "vanilla") + " stair it was begun with");
			helper.assertTrue(manager.furniture(placement).isVanilla() != wantTop, "the finished nook records " + manager.furniture(placement));
			org.slf4j.LoggerFactory.getLogger("QaFurnitureOrderMidBuild").info("order {} then {}, restarted {}: chair {} by tick {}",
				first, then, restarted[0], chair, helper.getTick());
		});
	}

	/** Begun with the test mod first; the order then lists no mod and the server restarts: the nook still gets the mod's chair. */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnitureOrderDropped"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnitureOrderDropped")
	public void aModTakenOutOfTheOrderMidBuildKeepsTheChairItWasBegunWith(GameTestHelper helper) {
		run(helper, List.of(QaFurnishedGameTests.PIECES), List.of(), true);
	}

	/**
	 * A site saved before it was ever planned (a blueprint handed over just before the server stopped) has chosen nothing
	 * yet: loaded on a server whose order lists the test mod, its first plan chooses the mod's chair, as a new build does;
	 * one saved after planning vanilla stays vanilla.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aSiteSavedBeforeItsFirstPlanChoosesWhenItIsPlanned(GameTestHelper helper) {
		List<String> mods = Furnishings.MODS;
		Leftovers.after(helper, () -> Furnishings.MODS = mods);
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(1, 2, 1)),
			Rotation.NONE, Mirror.NONE);
		BlockPos chairAt = placement.origin().offset(QaFurnishedGameTests.CHAIR);
		Furnishings.MODS = List.of();
		BuildSite fresh = new BuildSite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "test", QaFurnishedGameTests.NOOK, placement);
		CompoundTag unplanned = fresh.save();
		BuildSite planned = new BuildSite(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), "test", QaFurnishedGameTests.NOOK, placement);
		helper.assertTrue(planned.plan(level) != null && planned.furniture().isVanilla(), "with no mod in the order the nook chose " + planned.furniture());
		CompoundTag plannedVanilla = planned.save();

		Furnishings.MODS = List.of(QaFurnishedGameTests.PIECES);
		BuildSite loaded = BuildSite.load(unplanned);
		BuildPlan plan = loaded == null ? null : loaded.plan(level);
		helper.assertTrue(plan != null, "the site saved before its first plan has no plan after loading");
		BlockState chair = QaFurnishedGameTests.stepStates(plan).get(chairAt);
		helper.assertTrue(!loaded.furniture().isVanilla() && chair != null && chair.getValue(StairBlock.HALF) == Half.TOP,
			"the site saved before its first plan didn't choose the test mod's chair once planned: " + chair + ", " + loaded.furniture());
		BuildSite vanilla = BuildSite.load(plannedVanilla);
		BlockState kept = QaFurnishedGameTests.stepStates(vanilla.plan(level)).get(chairAt);
		helper.assertTrue(vanilla.furniture().isVanilla() && kept != null && kept.getValue(StairBlock.HALF) == Half.BOTTOM,
			"the site planned vanilla, saved and loaded with the mod in the order, plans " + kept + ", " + vanilla.furniture());
		helper.succeed();
	}

	/** Begun with no mod in the order; the test mod is then added and the server restarts: the nook stays vanilla. */
	//$ gametest_ticks_batch AREA '3600' '"qaFurnitureOrderAdded"'
	@GameTest(template = AREA, timeoutTicks = 3600, batch = "qaFurnitureOrderAdded")
	public void aModAddedMidBuildDoesntChangeAVanillaBuildGoingUp(GameTestHelper helper) {
		run(helper, List.of(), List.of(QaFurnishedGameTests.PIECES), false);
	}
}

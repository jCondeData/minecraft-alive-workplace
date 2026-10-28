package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA 2026-10-28 (lane E, saves and upgrades), B661/B662 and 36.21 "Refresh to the latest look" across the release: the
 * owner's live server runs 0.151.0, so every house standing there was built from 0.151.0's drawing. Each blueprint a
 * player can have built that was redrawn since v0.151.0 (worked out with git when this was written: the structure file
 * at the tag differs from today's, and its content is shipped in blueprint_history under the key below) is put up here
 * exactly as 0.151.0 drew it. Through the Village Hall's Repairs page as the owner presses it: the house is offered the
 * refresh, a Builder takes it up from that very drawing, and the refresh's plan carried out leaves the house equal to a
 * fresh build of today's drawing (chests, beds and job blocks aside), nothing of the old drawing left over or doubled.
 * The Wonder stages and the Bandit Fort (also redrawn) aren't refreshed from the hall, Guildhall III is wider and
 * Townhouse IV taller than the test area allows.
 */
public class QaReleaseRefresh1028GameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HOUSE = new BlockPos(7, 2, 2);
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos BENCH = new BlockPos(3, 2, 27);

	/** The blueprints redrawn since v0.151.0, with the blueprint_history key that holds 0.151.0's drawing. */
	static final List<String[]> REDRAWN = List.of(
		new String[] {"artisans_house_2", "cd1557d8c"}, new String[] {"artisans_house_3", "cd1557d8c"},
		new String[] {"barracks_3", "cd1557d8c"}, new String[] {"clinic", "2f10c0ccc"},
		new String[] {"clock_tower", "cd1557d8c"}, new String[] {"daycare", "cd1557d8c"},
		new String[] {"daycare_2", "cd1557d8c"}, new String[] {"jewellers_workshop", "cd1557d8c"},
		new String[] {"jewellers_workshop_2", "cd1557d8c"}, new String[] {"jewellers_workshop_3", "cd1557d8c"},
		new String[] {"lookout_tower_4", "cd1557d8c"}, new String[] {"research/town_hall", "cd1557d8c"},
		new String[] {"starter_cottage_3", "cd1557d8c"}, new String[] {"starter_cottage_4", "cd1557d8c"},
		new String[] {"storehouse", "cd1557d8c"}, new String[] {"storehouse_2", "cd1557d8c"},
		new String[] {"storehouse_3", "cd1557d8c"}, new String[] {"supply_shop_3", "cd1557d8c"},
		new String[] {"supply_shop_4", "cd1557d8c"}, new String[] {"terrace", "11b48d517"},
		new String[] {"terrace_2", "11b48d517"}, new String[] {"townhouse", "cd1557d8c"},
		new String[] {"townhouse_2", "cd1557d8c"}, new String[] {"townhouse_3", "cd1557d8c"},
		new String[] {"village/desert_clinic", "2f10c0ccc"}, new String[] {"village/plains_clinic", "2f10c0ccc"},
		new String[] {"village/savanna_clinic", "2f10c0ccc"}, new String[] {"village/snowy_clinic", "2f10c0ccc"},
		new String[] {"village/taiga_clinic", "2f10c0ccc"}, new String[] {"village/desert_gem_grotto", "cd1557d8c"},
		new String[] {"village/plains_gem_grotto", "cd1557d8c"}, new String[] {"village/savanna_gem_grotto", "cd1557d8c"},
		new String[] {"village/snowy_gem_grotto", "cd1557d8c"}, new String[] {"village/taiga_gem_grotto", "cd1557d8c"},
		new String[] {"village/desert_mortuary", "cd1557d8c"}, new String[] {"village/plains_mortuary", "cd1557d8c"},
		new String[] {"village/savanna_mortuary", "cd1557d8c"}, new String[] {"village/snowy_mortuary", "cd1557d8c"},
		new String[] {"village/taiga_mortuary", "cd1557d8c"});

	@GameTestGenerator
	public Collection<TestFunction> releaseRefreshes() {
		List<TestFunction> out = new ArrayList<>();
		for (String[] r : REDRAWN) {
			String name = "qaReleaseRefresh_" + r[0].replace('/', '_');
			out.add(new TestFunction(name, name, HUGE, 200, 0, true, helper -> refreshesToAFreshBuild(helper, AliveWorkplace.id(r[0]), r[1])));
		}
		return out;
	}

	static void refreshesToAFreshBuild(GameTestHelper helper, ResourceLocation id, String key) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		Leftovers.buildSites(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 40;
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		Blueprint old = Refreshes.olderDrawing(level.getServer(), id, key)
			.orElseThrow(() -> new IllegalStateException(id + ": 0.151.0's drawing (" + key + ") isn't shipped in blueprint_history"));
		Blueprint current = BlueprintLibrary.get(level, id).orElseThrow(() -> new IllegalStateException(id + " isn't in the blueprint library"));
		Map<BlockPos, BlockState> was = BuildPlan.create(old, placement).targets();
		Set<BlockPos> touched = new HashSet<>(was.keySet());
		touched.addAll(BuildPlan.create(current, placement).targets().keySet());
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getServer().getPlayerList().remove(owner);
			BuildSiteManager.get(level).forgetFinished(placement);
			BuildSite site = Refreshes.siteOf(level, placement);
			if (site != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
			// (a basement reaches under the area's floor, which the next batch's structure doesn't clear)
			touched.forEach(p -> level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
		});
		for (Map.Entry<BlockPos, BlockState> e : was.entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BENCH.east(), Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.north());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager.get(level).recordFinished(id, placement, owner.getUUID());
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!RefreshMatchesFreshBuildGameTests.differences(level, placement, old, current).isEmpty(),
				id + ": 0.151.0's drawing already equals today's (the list above is out of date)");
			ChoiceMenu menu = VillageHallScreen.forTest(owner, helper.absolutePos(HALL));
			menu.press(HallPages.slot(RepairsPage.PAGE), owner);
			int slot = -1;
			for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE && slot < 0; i++) {
				if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
					slot = i;
				}
			}
			helper.assertTrue(slot >= 0, id + " built as 0.151.0 drew it has no 'Refresh to the latest look' on the hall's Repairs page");
			menu.press(slot, owner);
			menu.press(RepairsPage.REFRESH, owner);
			BuildSite site = Refreshes.siteOf(level, placement);
			helper.assertTrue(site != null, id + ": the refresh was pressed but no Builder took it up");
			helper.assertTrue(key.equals(site.refreshFrom()), id + " was built from 0.151.0's drawing " + key + " but is refreshed from " + site.refreshFrom());
			BuildPlan plan = site.plan(level);
			Map<BlockPos, BlockState> now = BuildPlan.create(current, placement).targets();
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
					if (step.state().getBlock() instanceof BedBlock) { // (as the Builder puts a bed: both halves at once)
						BlockPos other = step.pos().relative(BedBlock.getConnectedDirection(step.state()));
						if (now.containsKey(other)) {
							level.setBlock(other, now.get(other), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
						}
					}
				}
			}
			List<String> left = RefreshMatchesFreshBuildGameTests.differences(level, placement, old, current);
			helper.assertTrue(left.isEmpty(), id + " refreshed from 0.151.0's drawing: " + left.size() + " spots differ from a fresh build: "
				+ left.subList(0, Math.min(8, left.size())));
			helper.succeed();
		});
	}
}

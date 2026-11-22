package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
 * QA (qa-lane-b-1122-1838), B671 from its spec: "a modded Clinic furnished on 0.151.0 is offered 'Refresh to the latest
 * look'" (0.151.0's modded Clinic shipped as blueprint_history/modded/clinic/2f10c0ccc.nbt). ModdedClinicRefreshCompatTests
 * and QaModdedRefresh1115cCompatTests stage the build facing north and keep the build's record in memory; on the owner's
 * live server the record of a Clinic built on 0.151.0 has been saved and loaded many times since, and builds stand
 * turned and mirrored. These add: the modded Clinic of 0.151.0 turned and mirrored, its record saved and loaded (the
 * modded-library mark must survive, or the lookup goes to the vanilla history), offered the refresh from the hall from
 * 2f10c0ccc; and the other side of the boundary: once refreshed to today's look, it is offered nothing more.
 */
public class QaModdedClinic1122bCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HOUSE = new BlockPos(9, 2, 9);
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos BENCH = new BlockPos(3, 2, 26);
	private static final ResourceLocation CLINIC = AliveWorkplace.id("clinic");
	private static final ResourceLocation MODDED_CLINIC = AliveWorkplace.id(ModdedLibrary.PREFIX + "clinic");
	private static final String V0_151_0 = "2f10c0ccc";
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_compat");

	private record Stage(ServerLevel level, ServerPlayer owner, BlueprintData.Placement placement) {
	}

	/** 0.151.0's modded Clinic standing turned and mirrored, recorded finished from the modded library, a hall and a Builder. */
	private static Stage stage(GameTestHelper helper, Blueprint drawing, Rotation rotation, Mirror mirror) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), rotation, mirror);
		Map<BlockPos, BlockState> targets = BuildPlan.create(drawing, placement).targets();
		BlockPos lo = helper.absolutePos(BlockPos.ZERO);
		for (BlockPos p : targets.keySet()) {
			BlockPos rel = p.subtract(lo);
			helper.assertTrue(rel.getX() >= 0 && rel.getX() < 30 && rel.getZ() >= 0 && rel.getZ() < 30 && rel.getY() < 30
				&& !(rel.getX() <= 4 && (rel.getZ() <= 2 || rel.getZ() >= 23)), "setup: the turned Clinic leaves the area or covers the hall or bench at " + rel);
		}
		targets.forEach((p, s) -> level.setBlock(p, s, FLAGS));
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BENCH.east(), Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.north(2).west());
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(CLINIC, placement, owner.getUUID());
		manager.setFurniture(placement, new FurnitureChoice(Map.of(), true));
		return new Stage(level, owner, placement);
	}

	/** A server restart for the build records: every site and finished build saved and loaded again. */
	private static void restart(ServerLevel level) {
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
	}

	private static void cleanUp(Stage s) {
		BuildSiteManager manager = BuildSiteManager.get(s.level());
		BuildSite site = Refreshes.siteOf(s.level(), s.placement());
		if (site != null) {
			manager.remove(site.id());
		}
		manager.forgetFinished(s.placement());
		s.level().getServer().getPlayerList().remove(s.owner());
	}

	/** The owner opens the hall's Repairs page: the slot of the build's Refresh entry, or -1. */
	private static int refreshEntry(Stage s, ChoiceMenu menu) {
		menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE; i++) {
			if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
				return i;
			}
		}
		return -1;
	}

	private static Optional<Refreshes.Change> change(Stage s) {
		BuildSiteManager.Finished f = BuildSiteManager.get(s.level()).finishedIn(s.level()).stream()
			.filter(b -> b.placement().equals(s.placement())).findFirst().orElseThrow(() -> new IllegalStateException("the Clinic isn't recorded finished"));
		return Refreshes.change(s.level(), f);
	}

	/**
	 * 0.151.0's modded Clinic, turned a quarter clockwise and mirrored, its record saved and loaded twice: the record still
	 * says modded library, the hall's Repairs page offers the refresh, and pressing it starts the refresh from 0.151.0's
	 * modded drawing. Carried out (the plan's blocks laid, as ModdedClinicRefreshCompatTests does) and the site done with,
	 * the build stands as today's modded Clinic: no change is left and the Repairs page offers no refresh any more.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"qaModdedClinic1122bTurned"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "qaModdedClinic1122bTurned")
	public void aTurnedModdedClinicFrom0151IsOfferedTheRefreshAfterARestartAndNothingOnceRefreshed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ModdedLibrary.complete(CLINIC), "the modded Clinic can't be built in the compat run: missing " + ModdedLibrary.missing(CLINIC));
		Blueprint old = Refreshes.olderDrawing(level.getServer(), MODDED_CLINIC, V0_151_0)
			.orElseThrow(() -> new IllegalStateException("0.151.0's modded Clinic (" + V0_151_0 + ") isn't shipped in blueprint_history/modded/clinic"));
		Stage s = stage(helper, old, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT);
		helper.runAfterDelay(2, () -> {
			int radius = VillageHalls.RADIUS;
			try {
				VillageHalls.RADIUS = 32;
				restart(level);
				restart(level);
				FurnitureChoice furniture = BuildSiteManager.get(level).furniture(s.placement());
				helper.assertTrue(furniture.modded(), "after a restart the Clinic's record no longer says modded library: " + furniture);
				Optional<Refreshes.Change> change = change(s);
				helper.assertTrue(change.isPresent() && V0_151_0.equals(change.get().from()), "after a restart the turned modded Clinic of 0.151.0 is offered "
					+ change.map(c -> "a refresh from " + c.from()).orElse("no refresh"));
				ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), helper.absolutePos(HALL));
				int slot = refreshEntry(s, menu);
				helper.assertTrue(slot >= 0, "the turned modded Clinic of 0.151.0 has no 'Refresh to the latest look' on the hall's Repairs page after a restart");
				menu.press(slot, s.owner());
				helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button on the Clinic's entry: " + menu.icon(RepairsPage.REFRESH));
				menu.press(RepairsPage.REFRESH, s.owner());
				BuildSite site = Refreshes.siteOf(level, s.placement());
				helper.assertTrue(site != null, "the refresh was pressed but no Builder took it up");
				helper.assertTrue(V0_151_0.equals(site.refreshFrom()), "the modded Clinic was refreshed from " + site.refreshFrom() + ", not " + V0_151_0);
				BuildPlan plan = site.plan(level);
				Blueprint current = Refreshes.refreshedFurniture(level, CLINIC, furniture).apply(
					io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, CLINIC).orElseThrow(), CLINIC);
				Map<BlockPos, BlockState> now = BuildPlan.create(current, s.placement()).targets();
				for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
					for (BuildPlan.Step step : plan.steps(stage)) {
						level.setBlock(step.pos(), step.state(), FLAGS);
						BlockPos other = step.state().getBlock() instanceof BedBlock
							? step.pos().relative(BedBlock.getConnectedDirection(step.state()))
							: io.github.jcondedata.aliveworkplace.build.MaterialRules.secondaryPos(step.pos(), step.state());
						if (other != null && now.containsKey(other)) {
							level.setBlock(other, now.get(other), FLAGS);
						}
					}
				}
				BuildSiteManager.get(level).remove(site.id());
				restart(level);
				Optional<Refreshes.Change> after = change(s);
				int again = refreshEntry(s, VillageHallScreen.forTest(s.owner(), helper.absolutePos(HALL)));
				helper.assertTrue(after.isEmpty(), "the refreshed modded Clinic is still offered a refresh from "
					+ after.map(c -> c.from() + " (" + c.total() + " pieces)").orElse(""));
				helper.assertTrue(again < 0, "the refreshed modded Clinic still has a Refresh entry on the Repairs page");
				LOGGER.info("qa modded clinic 1122b: turned and mirrored, after two restarts offered {} pieces from {}; refreshed, offered nothing",
					change.get().total(), change.get().from());
			} finally {
				VillageHalls.RADIUS = radius;
				cleanUp(s);
			}
			helper.succeed();
		});
	}

	/**
	 * The other release drawing shipped for the modded Clinic (v0.151.1), standing unturned and recorded modded: offered
	 * the refresh from that key if it differs from today's modded copy, and never from 0.151.0's (it doesn't stand as that).
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qaModdedClinic1122b1511"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qaModdedClinic1122b1511")
	public void aModdedClinicFrom01511IsOfferedFromItsOwnDrawingNot0151(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ModdedLibrary.complete(CLINIC), "the modded Clinic can't be built in the compat run: missing " + ModdedLibrary.missing(CLINIC));
		Blueprint old = Refreshes.olderDrawing(level.getServer(), MODDED_CLINIC, "v0.151.1")
			.orElseThrow(() -> new IllegalStateException("v0.151.1's modded Clinic isn't shipped in blueprint_history/modded/clinic"));
		Stage s = stage(helper, old, Rotation.NONE, Mirror.NONE);
		helper.runAfterDelay(2, () -> {
			try {
				restart(level);
				Map<BlockPos, BlockState> today = BuildPlan.create(ModdedLibrary.copy(CLINIC).orElseThrow(), s.placement()).targets();
				Map<BlockPos, BlockState> was = BuildPlan.create(old, s.placement()).targets();
				Optional<Refreshes.Change> change = change(s);
				LOGGER.info("qa modded clinic 1122b: v0.151.1's modded Clinic {} today's; offered {}", today.equals(was) ? "draws as" : "differs from",
					change.map(c -> c.total() + " pieces from " + c.from()).orElse("nothing"));
				if (today.equals(was)) {
					helper.assertTrue(change.isEmpty(), "v0.151.1's modded Clinic draws as today's, yet is offered a refresh from "
						+ change.map(Refreshes.Change::from).orElse(""));
				} else {
					helper.assertTrue(change.isPresent() && "v0.151.1".equals(change.get().from()), "a modded Clinic of v0.151.1 is offered "
						+ change.map(c -> "a refresh from " + c.from()).orElse("no refresh"));
				}
			} finally {
				cleanUp(s);
			}
			helper.succeed();
		});
	}
}

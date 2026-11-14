package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Employer;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * B680: a Clinic built from the MODDED library (37.12, the owner's pack has its mods) as 0.151.0 drew it, birch-slab and
 * white-carpet cots in its ward. Its older drawings are looked up under {@code blueprint_history/modded/clinic}, which
 * tools/blueprints/history.py never filled (it skipped every modded copy), so the build was never offered the refresh.
 * Through the Village Hall's Repairs page as the owner presses it: the refresh is offered, a Builder takes it up from
 * 0.151.0's modded drawing, and its plan carried out leaves today's modded Clinic, white beds where the cots were. A
 * modded Clinic built from today's drawing is offered nothing.
 */
public class ModdedClinicRefreshCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HOUSE = new BlockPos(9, 2, 9);
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos BENCH = new BlockPos(3, 2, 26);
	private static final ResourceLocation CLINIC = AliveWorkplace.id("clinic");
	private static final ResourceLocation MODDED_CLINIC = AliveWorkplace.id(ModdedLibrary.PREFIX + "clinic");
	/** The key under which tools/blueprints/history.py ships v0.151.0's modded Clinic (the commit before B639's redraw). */
	private static final String V0_151_0 = "2f10c0ccc";
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

	private record Stage(ServerLevel level, ServerPlayer owner, BlueprintData.Placement placement, FurnitureChoice furniture) {
	}

	/** {@code drawing} standing at {@link #HOUSE}, recorded as a finished Clinic from the modded library, a hall and a Builder. */
	private static Stage stage(GameTestHelper helper, Blueprint drawing) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(drawing, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), FLAGS);
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BENCH.west(2), Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.north(2));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		// as the build's save reads back a Clinic built from the modded library (its furniture spots left vanilla)
		FurnitureChoice furniture = new FurnitureChoice(Map.of(), true);
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(CLINIC, placement, owner.getUUID());
		manager.setFurniture(placement, furniture);
		return new Stage(level, owner, placement, furniture);
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

	/** The slot of the Refresh entry on the hall's Repairs page as the owner opens it, or -1 when there is none. */
	private static int refreshEntry(Stage s, ChoiceMenu menu) {
		menu.press(HallPages.slot(RepairsPage.PAGE), s.owner());
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE; i++) {
			if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
				return i;
			}
		}
		return -1;
	}

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	//$ gametest_ticks_batch HUGE_AREA '400' '"b680_moddedClinic"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "b680_moddedClinic")
	public void aModdedClinicFrom0151IsRefreshedToWhiteBeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ModdedLibrary.complete(CLINIC), "the modded Clinic can't be built in the compat run: missing " + ModdedLibrary.missing(CLINIC));
		Blueprint old = Refreshes.olderDrawing(level.getServer(), MODDED_CLINIC, V0_151_0)
			.orElseThrow(() -> new IllegalStateException("0.151.0's modded Clinic (" + V0_151_0 + ") isn't shipped in blueprint_history/modded/clinic"));
		Stage s = stage(helper, old);
		helper.runAfterDelay(2, () -> {
			int radius = VillageHalls.RADIUS;
			BuildSite site;
			try {
				VillageHalls.RADIUS = 32;
				ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), helper.absolutePos(HALL));
				int slot = refreshEntry(s, menu);
				helper.assertTrue(slot >= 0, "a modded Clinic built as 0.151.0 drew it has no 'Refresh to the latest look' on the hall's Repairs page");
				menu.press(slot, s.owner());
				menu.press(RepairsPage.REFRESH, s.owner());
				site = Refreshes.siteOf(level, s.placement());
			} finally {
				VillageHalls.RADIUS = radius;
			}
			helper.assertTrue(site != null, "the modded Clinic's refresh was pressed but no Builder took it up");
			helper.assertTrue(V0_151_0.equals(site.refreshFrom()), "the modded Clinic was refreshed from " + site.refreshFrom() + ", not 0.151.0's " + V0_151_0);
			Blueprint current = s.furniture().apply(BlueprintLibrary.get(level, CLINIC).orElseThrow(), CLINIC);
			Map<BlockPos, BlockState> now = BuildPlan.create(current, s.placement()).targets();
			Map<BlockPos, BlockState> was = BuildPlan.create(old, s.placement()).targets();
			long whiteBeds = now.values().stream().filter(b -> b.is(Blocks.WHITE_BED)).count();
			helper.assertTrue(whiteBeds > was.values().stream().filter(b -> b.is(Blocks.WHITE_BED)).count(),
				"setup: today's modded Clinic has no more white bed blocks (" + whiteBeds + ") than 0.151.0's");
			BuildPlan plan = site.plan(level);
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					level.setBlock(step.pos(), step.state(), FLAGS);
					if (step.state().getBlock() instanceof BedBlock) { // (as the Builder puts a bed: both halves at once)
						BlockPos other = step.pos().relative(BedBlock.getConnectedDirection(step.state()));
						if (now.containsKey(other)) {
							level.setBlock(other, now.get(other), FLAGS);
						}
					}
				}
			}
			Set<BlockPos> spots = new HashSet<>(was.keySet());
			spots.addAll(now.keySet());
			List<String> left = new ArrayList<>();
			List<String> noBed = new ArrayList<>();
			for (BlockPos pos : spots) {
				BlockState world = level.getBlockState(pos);
				BlockState wanted = now.getOrDefault(pos, Blocks.AIR.defaultBlockState());
				if (wanted.is(Blocks.WHITE_BED) && !world.is(Blocks.WHITE_BED)) {
					noBed.add(pos.toShortString() + " " + id(world));
				}
				if (world.getBlock() instanceof ChestBlock) {
					continue;
				}
				boolean ok = wanted.isAir() ? world.isAir() || world.canBeReplaced() && world.getFluidState().isEmpty() : MaterialRules.matches(world, wanted);
				if (!ok) {
					left.add(pos.toShortString() + " " + id(world) + " (wanted " + id(wanted) + ")");
				}
			}
			cleanUp(s);
			helper.assertTrue(noBed.isEmpty(), "the refreshed modded Clinic has no white bed where today's drawing has one: " + noBed);
			helper.assertTrue(left.isEmpty(), "the refreshed modded Clinic: " + left.size() + " spots differ from a fresh build: "
				+ left.subList(0, Math.min(10, left.size())));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '200' '"b680_todaysModdedClinic"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "b680_todaysModdedClinic")
	public void aModdedClinicBuiltFromTodaysDrawingIsNotOfferedARefresh(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ModdedLibrary.complete(CLINIC), "the modded Clinic can't be built in the compat run: missing " + ModdedLibrary.missing(CLINIC));
		Blueprint today = ModdedLibrary.copy(CLINIC).orElseThrow();
		Stage s = stage(helper, today);
		helper.runAfterDelay(2, () -> {
			BuildSiteManager.Finished f = BuildSiteManager.get(level).finishedIn(level).stream()
				.filter(b -> b.placement().equals(s.placement())).findFirst().orElseThrow(() -> new IllegalStateException("the Clinic isn't recorded finished"));
			var change = Refreshes.change(level, f);
			int radius = VillageHalls.RADIUS;
			int slot;
			try {
				VillageHalls.RADIUS = 32;
				slot = refreshEntry(s, VillageHallScreen.forTest(s.owner(), helper.absolutePos(HALL)));
			} finally {
				VillageHalls.RADIUS = radius;
			}
			cleanUp(s);
			helper.assertTrue(change.isEmpty(), "a modded Clinic built from today's drawing is offered a refresh from "
				+ change.map(c -> c.from() + " (" + c.total() + " pieces)").orElse(""));
			helper.assertTrue(slot < 0, "the hall's Repairs page shows a Refresh entry for a modded Clinic built from today's drawing");
			helper.succeed();
		});
	}
}

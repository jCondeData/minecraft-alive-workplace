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
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.RepairsPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
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
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA (lane c, 2026-11-15), verifying B680 from its spec: "every modded copy changed since 1.0 ships its released drawings
 * under blueprint_history/modded/, and a modded Clinic built from 0.151.0 is offered 'Refresh to the latest look' and
 * gets real beds". The modded library needs the pack's mods (Chipped, Rechiseled, Supplementaries, ...), so these run in
 * the compat suite. ModdedClinicRefreshCompatTests (the fix's own) lays the plan's blocks by hand; here a real Builder
 * carries the refresh out from the hall's button, other redrawn modded copies are offered from their shipped drawings
 * and never at today's look, and a vanilla Clinic of the same release is refreshed from the vanilla drawing, never
 * the modded one shipped under the same key.
 */
public class QaModdedRefresh1115cCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HOUSE = new BlockPos(9, 2, 9);
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos BENCH = new BlockPos(3, 2, 26);
	private static final ResourceLocation CLINIC = AliveWorkplace.id("clinic");
	/** v0.151.0's Clinic, shipped as blueprint_history/(modded/)clinic/2f10c0ccc.nbt (the same bytes as the tag's file). */
	private static final String V0_151_0 = "2f10c0ccc";
	private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
	private static final List<String> MODDED_NAMESPACES = List.of("chipped", "rechiseled", "supplementaries", "carved_wood");

	private record Stage(ServerLevel level, ServerPlayer owner, BlueprintData.Placement placement, Villager builder) {
	}

	private static Stage stage(GameTestHelper helper, ResourceLocation structure, Blueprint drawing, FurnitureChoice furniture) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearHalls(helper);
		PartnerShowsCompatTests.clearAirAbove(helper, 0, 4);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		place(level, drawing, placement);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(BENCH.west(2), Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.north(2));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		ModAttachments.BUILDER_EMPLOYER.set(builder, new Employer(owner.getUUID(), owner.getGameProfile().getName()));
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(structure, placement, owner.getUUID());
		manager.setFurniture(placement, furniture);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			BuildSite site = Refreshes.siteOf(level, placement);
			if (site != null) {
				manager.remove(site.id());
			}
			manager.forgetFinished(placement);
			level.getServer().getPlayerList().remove(owner);
		});
		return new Stage(level, owner, placement, builder);
	}

	private static void place(ServerLevel level, Blueprint drawing, BlueprintData.Placement placement) {
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(drawing, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), FLAGS);
		}
	}

	private static void clear(ServerLevel level, Blueprint drawing, BlueprintData.Placement placement) {
		for (BlockPos pos : BuildPlan.create(drawing, placement).targets().keySet()) {
			level.removeBlockEntity(pos);
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
		}
	}

	/** The Refresh entry on the hall's Repairs page as the owner opens it, or -1. */
	private static int refreshEntry(ServerPlayer owner, ChoiceMenu menu) {
		menu.press(HallPages.slot(RepairsPage.PAGE), owner);
		for (int i = VillageHallScreen.FIRST_ROW; i < ChoiceMenu.SIZE; i++) {
			if (menu.icon(i).is(Items.PAINTING) || menu.icon(i).is(Items.SCAFFOLDING)) {
				return i;
			}
		}
		return -1;
	}

	/** Opens the hall as the owner, the build's entry, and presses "Refresh to the latest look" (checking its words). */
	private static void pressRefresh(GameTestHelper helper, Stage s, String what) {
		ChoiceMenu menu = VillageHallScreen.forTest(s.owner(), helper.absolutePos(HALL));
		int slot = refreshEntry(s.owner(), menu);
		helper.assertTrue(slot >= 0, what + " has no entry on the hall's Repairs page to refresh it");
		menu.press(slot, s.owner());
		helper.assertTrue(menu.icon(RepairsPage.REFRESH).is(Items.BRUSH), "no Refresh button for " + what + ": " + menu.icon(RepairsPage.REFRESH));
		String label = menu.icon(RepairsPage.REFRESH).getHoverName().getString();
		helper.assertTrue("Refresh to the latest look".equals(label), "the button for " + what + " reads '" + label + "', expected 'Refresh to the latest look'");
		menu.press(RepairsPage.REFRESH, s.owner());
	}

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private static Set<String> namespaces(Blueprint drawing, BlueprintData.Placement placement) {
		Set<String> out = new HashSet<>();
		for (BlockState b : BuildPlan.create(drawing, placement).targets().values()) {
			out.add(BuiltInRegistries.BLOCK.getKey(b.getBlock()).getNamespace());
		}
		return out;
	}

	private static void setRule(GameTestHelper helper, net.minecraft.world.level.GameRules.Key<net.minecraft.world.level.GameRules.BooleanValue> key, boolean value) {
		var server = helper.getLevel().getServer();
		boolean was = helper.getLevel().getGameRules().getBoolean(key);
		helper.getLevel().getGameRules().getRule(key).set(value, server);
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getGameRules().getRule(key).set(was, server));
	}

	/**
	 * A modded Clinic standing as 0.151.0's modded drawing drew it (birch-slab and white-carpet cots): the owner presses
	 * "Refresh to the latest look" on the hall's Repairs page, a real Builder (free materials, as on a creative server) does
	 * the work, and every spot where today's modded drawing has a white bed ends with a white bed, the cots' spots among
	 * them; the modded walls are left modded.
	 */
	//$ gametest_ticks_batch HUGE_AREA '9000' '"qaModdedRefresh1115cBuilder"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 9000, batch = "qaModdedRefresh1115cBuilder")
	public void aRealBuilderRefreshesTheModdedClinicToWhiteBeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(ModdedLibrary.complete(CLINIC), "setup: the modded Clinic can't be built here: missing " + ModdedLibrary.missing(CLINIC));
		ResourceLocation moddedClinic = AliveWorkplace.id(ModdedLibrary.PREFIX + "clinic");
		Blueprint old = Refreshes.olderDrawing(level.getServer(), moddedClinic, V0_151_0)
			.orElseThrow(() -> new GameTestAssertException("0.151.0's modded Clinic isn't shipped in blueprint_history/modded/clinic"));
		setRule(helper, ModGameRules.FREE_MATERIALS, true);
		setRule(helper, ModGameRules.BUILDERS_HELP, false);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		PartnerShowsCompatTests.after(helper, () -> level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer()));
		FurnitureChoice modded = new FurnitureChoice(Map.of(), true);
		Stage s = stage(helper, CLINIC, old, modded);
		Blueprint today = ModdedLibrary.copy(CLINIC).orElseThrow();
		Map<BlockPos, BlockState> now = BuildPlan.create(modded.apply(today, CLINIC), s.placement()).targets();
		Map<BlockPos, BlockState> was = BuildPlan.create(old, s.placement()).targets();
		List<BlockPos> cots = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : now.entrySet()) {
			BlockState before = was.get(e.getKey());
			if (e.getValue().is(Blocks.WHITE_BED) && (before == null || !before.is(Blocks.WHITE_BED))) {
				cots.add(e.getKey());
			}
		}
		helper.assertTrue(cots.size() >= 6, "setup: today's modded Clinic puts white beds on only " + cots.size() + " spots 0.151.0's had none (the spec: 6 cots)");
		long beds = now.values().stream().filter(b -> b.is(Blocks.WHITE_BED)).count();
		helper.runAfterDelay(2, () -> {
			pressRefresh(helper, s, "a modded Clinic of 0.151.0");
			BuildSite site = Refreshes.siteOf(level, s.placement());
			helper.assertTrue(site != null, "the refresh was pressed but no Builder took it up");
			helper.assertTrue(V0_151_0.equals(site.refreshFrom()), "refreshed from " + site.refreshFrom() + ", not 0.151.0's " + V0_151_0);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(Refreshes.siteOf(level, s.placement()) == null, "the refresh is still going");
			List<String> noBed = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : now.entrySet()) {
				if (e.getValue().is(Blocks.WHITE_BED) && !level.getBlockState(e.getKey()).is(Blocks.WHITE_BED)) {
					noBed.add(e.getKey().toShortString() + " " + id(level.getBlockState(e.getKey())));
				}
			}
			helper.assertTrue(noBed.isEmpty(), "refreshed by the Builder, " + noBed.size() + " of " + beds + " white bed spots have no white bed: " + noBed);
			int moddedLeft = 0;
			for (BlockPos pos : now.keySet()) {
				if (MODDED_NAMESPACES.contains(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getNamespace())) {
					moddedLeft++;
				}
			}
			helper.assertTrue(moddedLeft > 0, "the refreshed modded Clinic has no modded blocks left: it was rebuilt as the vanilla Clinic");
			helper.assertTrue(Refreshes.change(level, new BuildSiteManager.Finished(CLINIC, s.placement(), s.owner().getUUID())).isEmpty(),
				"once refreshed, the modded Clinic is still offered a refresh");
		});
	}

	/**
	 * Other modded copies the B680 text names as redrawn since 1.0, each standing as its shipped older drawing drew it and
	 * recorded as a modded build: offered a refresh from that drawing; the same build standing as today's modded copy draws
	 * it: offered none. One after another on the same spot.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"qaModdedRefresh1115cOthers"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "qaModdedRefresh1115cOthers")
	public void redrawnModdedCopiesAreOfferedFromTheirShippedDrawingOnly(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		PartnerShowsCompatTests.clearAirAbove(helper, 0, 4);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		BuildSiteManager manager = BuildSiteManager.get(level);
		PartnerShowsCompatTests.after(helper, () -> {
			manager.forgetFinished(placement);
			level.getServer().getPlayerList().remove(owner);
		});
		// (name, a shipped key): from the bug text's list and blueprint_history/modded/<name>/
		String[][] builds = {{"farmstead", "a12542b71"}, {"healing_center_3", "a12542b71"}, {"map_room", "a12542b71"}, {"smithy_2", "a12542b71"},
			{"storage_hall", "eaa1c261a"}, {"warehouse", "eaa1c261a"}, {"inn", "v0.145.0"}, {"starter_cottage", "v0.145.0"}};
		FurnitureChoice modded = new FurnitureChoice(Map.of(), true);
		List<String> wrong = new ArrayList<>();
		int checked = 0;
		for (String[] b : builds) {
			ResourceLocation id = AliveWorkplace.id(b[0]);
			helper.assertTrue(ModdedLibrary.complete(id), "setup: the modded " + b[0] + " can't be built here: missing " + ModdedLibrary.missing(id));
			Optional<Blueprint> shipped = Refreshes.olderDrawing(level.getServer(), AliveWorkplace.id(ModdedLibrary.PREFIX + b[0]), b[1]);
			if (shipped.isEmpty()) {
				wrong.add(b[0] + ": no drawing " + b[1] + " shipped under blueprint_history/modded");
				continue;
			}
			Blueprint today = ModdedLibrary.copy(id).orElseThrow();
			helper.assertTrue(!BuildPlan.create(shipped.get(), placement).targets().equals(BuildPlan.create(today, placement).targets()),
				"setup: the shipped " + b[0] + " " + b[1] + " is block for block today's");
			// standing as the shipped older drawing drew it
			place(level, shipped.get(), placement);
			manager.recordFinished(id, placement, owner.getUUID());
			manager.setFurniture(placement, modded);
			Optional<Refreshes.Change> older = Refreshes.change(level, new BuildSiteManager.Finished(id, placement, owner.getUUID()));
			if (older.isEmpty()) {
				wrong.add(b[0] + " built from " + b[1] + " is offered no refresh");
			}
			clear(level, shipped.get(), placement);
			// standing as today's modded copy draws it
			place(level, today, placement);
			Optional<Refreshes.Change> latest = Refreshes.change(level, new BuildSiteManager.Finished(id, placement, owner.getUUID()));
			if (latest.isPresent()) {
				wrong.add(b[0] + " built from today's modded copy is offered a refresh from " + latest.get().from());
			}
			clear(level, today, placement);
			manager.forgetFinished(placement);
			checked++;
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " wrong: " + wrong);
		org.slf4j.LoggerFactory.getLogger("QaModdedRefresh1115c").info("QaModdedRefresh1115c: {} modded copies offered from their shipped drawing, none at today's look", checked);
		helper.succeed();
	}

	/**
	 * The vanilla and the modded Clinic of 0.151.0 ship under the same key (2f10c0ccc). A Clinic built from the vanilla
	 * library (vanilla furniture) standing as 0.151.0's vanilla drawing drew it, in a world where the modded library could
	 * be used: offered the refresh from the hall, from that key, and the refresh's plan leaves today's vanilla Clinic, not
	 * one modded block in it. The two shipped drawings are not the same drawing.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"qaModdedRefresh1115cVanilla"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "qaModdedRefresh1115cVanilla")
	public void aVanillaClinicIsRefreshedFromTheVanillaDrawing(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Blueprint vanillaOld = Refreshes.olderDrawing(level.getServer(), CLINIC, V0_151_0)
			.orElseThrow(() -> new GameTestAssertException("0.151.0's vanilla Clinic isn't shipped in blueprint_history/clinic"));
		Blueprint moddedOld = Refreshes.olderDrawing(level.getServer(), AliveWorkplace.id(ModdedLibrary.PREFIX + "clinic"), V0_151_0)
			.orElseThrow(() -> new GameTestAssertException("0.151.0's modded Clinic isn't shipped in blueprint_history/modded/clinic"));
		Stage s = stage(helper, CLINIC, vanillaOld, FurnitureChoice.VANILLA);
		Set<String> vanillaNs = namespaces(vanillaOld, s.placement());
		Set<String> moddedNs = namespaces(moddedOld, s.placement());
		helper.assertTrue(vanillaNs.stream().noneMatch(MODDED_NAMESPACES::contains), "0.151.0's vanilla Clinic drawing has modded blocks: " + vanillaNs);
		helper.assertTrue(moddedNs.stream().anyMatch(MODDED_NAMESPACES::contains), "0.151.0's modded Clinic drawing has no modded blocks: " + moddedNs);
		helper.runAfterDelay(2, () -> {
			pressRefresh(helper, s, "a vanilla Clinic of 0.151.0");
			BuildSite site = Refreshes.siteOf(level, s.placement());
			helper.assertTrue(site != null, "the vanilla Clinic's refresh was pressed but no Builder took it up");
			helper.assertTrue(V0_151_0.equals(site.refreshFrom()), "the vanilla Clinic was refreshed from " + site.refreshFrom() + ", not " + V0_151_0);
			Blueprint current = FurnitureChoice.VANILLA.apply(BlueprintLibrary.get(level, CLINIC).orElseThrow(), CLINIC);
			Map<BlockPos, BlockState> now = BuildPlan.create(current, s.placement()).targets();
			BuildPlan plan = site.plan(level);
			List<String> moddedSteps = new ArrayList<>();
			for (BuildPlan.Stage stage : BuildPlan.Stage.values()) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					if (MODDED_NAMESPACES.contains(BuiltInRegistries.BLOCK.getKey(step.state().getBlock()).getNamespace())) {
						moddedSteps.add(step.pos().toShortString() + " " + id(step.state()));
					}
				}
			}
			helper.assertTrue(moddedSteps.isEmpty(), "the vanilla Clinic's refresh puts modded blocks in: " + moddedSteps.subList(0, Math.min(8, moddedSteps.size())));
			for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.CLEAR, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
				for (BuildPlan.Step step : plan.steps(stage)) {
					level.setBlock(step.pos(), step.state(), FLAGS);
					if (step.state().getBlock() instanceof BedBlock) {
						BlockPos other = step.pos().relative(BedBlock.getConnectedDirection(step.state()));
						if (now.containsKey(other)) {
							level.setBlock(other, now.get(other), FLAGS);
						}
					}
				}
			}
			List<String> noBed = new ArrayList<>();
			for (Map.Entry<BlockPos, BlockState> e : now.entrySet()) {
				if (e.getValue().is(Blocks.WHITE_BED) && !level.getBlockState(e.getKey()).is(Blocks.WHITE_BED)) {
					noBed.add(e.getKey().toShortString() + " " + id(level.getBlockState(e.getKey())));
				}
			}
			helper.assertTrue(now.values().stream().anyMatch(b -> b.is(Blocks.WHITE_BED)), "setup: today's vanilla Clinic has no white beds");
			helper.assertTrue(noBed.isEmpty(), "the refreshed vanilla Clinic lacks white beds at " + noBed);
			helper.succeed();
		});
	}
}

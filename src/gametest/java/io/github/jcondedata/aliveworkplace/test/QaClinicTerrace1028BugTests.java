package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import java.io.InputStream;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * QA 2026-10-28, B639's "existing Clinics can be refreshed via 36.21": a Clinic the builder put up before the fix (its
 * ward of one bed and three slab-and-carpet cots, the owner's live one) must be offered "Refresh to the latest look" on
 * the Village Hall's Repairs page, so the owner can get the real beds. The drawing before B639 is
 * {@code qa_b639/clinic_pre_b639.nbt} (git 2f10c0ccc^).
 */
public class QaClinicTerrace1028BugTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	//$ gametest_batch HUGE_AREA '"qaClinicRefresh"'
	@GameTest(template = HUGE_AREA, batch = "qaClinicRefresh")
	public void qaB639AClinicBuiltWithCotsCanBeRefreshedToRealBeds(GameTestHelper helper) throws Exception {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation clinic = AliveWorkplace.id("clinic");
		CompoundTag tag;
		try (InputStream in = QaClinicTerrace1028BugTests.class.getResourceAsStream("/qa_b639/clinic_pre_b639.nbt")) {
			helper.assertTrue(in != null, "the pre-B639 Clinic drawing is missing from the test resources");
			tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
		}
		Blueprint old = StarterBlueprints.withBasement(StarterBlueprints.withFeatures(
			Blueprint.fromStructureNbt(clinic, tag, Lookup.lookup(BuiltInRegistries.BLOCK))));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(8, 2, 8)),
			Rotation.NONE, Mirror.NONE);
		int placed = 0;
		for (Map.Entry<BlockPos, BlockState> e : BuildPlan.create(old, placement).targets().entrySet()) {
			level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
			placed++;
		}
		BuildSiteManager.get(level).recordFinished(clinic, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			Refreshes.removeHistory(clinic);
		});
		BuildSiteManager.Finished finished = new BuildSiteManager.Finished(clinic, placement, UUID.randomUUID());
		Optional<Refreshes.Change> shipped = Refreshes.change(level, finished);
		// The control: with that drawing known, the same standing Clinic is offered the refresh (the setup is sound).
		Refreshes.addHistory(clinic, "qa_pre_b639", old);
		Optional<Refreshes.Change> known = Refreshes.change(level, finished);
		Refreshes.removeHistory(clinic);
		helper.assertTrue(known.isPresent(), "setup: even with its older drawing added, the pre-B639 Clinic (" + placed + " blocks) has nothing to refresh");
		helper.assertTrue(shipped.isPresent(), "a Clinic built before B639 (one bed, three slab-and-carpet cots) is offered no 'Refresh to the latest look': "
			+ "the mod ships no older drawing of aliveworkplace:clinic (data/aliveworkplace/blueprint_history/clinic/ is missing), so the owner's "
			+ "live Clinic can't get its real beds; with that drawing shipped it would change " + known.get().total() + " pieces");
		helper.succeed();
	}
}

package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageWaystones;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-10-27) of 41.2's rule for whose network a hall's Map shows, with the real Waystones mod: "the villages
 * of the hall's owner (the viewer's own when the hall has none)". The nightly mutation run of 2026-10-27 found nothing
 * failed when the viewer was dropped for a hall with no owner ({@code VillageWaystones.network}, line 350): these do.
 */
public class QaWaystoneUnownedHallCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL_A = new BlockPos(1, 2, 1);
	private static final BlockPos HALL_B = new BlockPos(28, 2, 28);
	private static final BlockPos PLAZA_A = new BlockPos(3, 2, 3);
	private static final BlockPos PLAZA_B = new BlockPos(19, 2, 19);

	/** A hall named {@code name}, owned by {@code owner} (null: nobody's), as the hall's round records it. */
	private static void hall(GameTestHelper helper, BlockPos at, String name, ServerPlayer owner) {
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(at);
		hall.setCustomName(Component.literal(name));
		if (owner != null) {
			hall.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		}
		ServerLevel level = helper.getLevel();
		RealmData.get(level.getServer()).putKnown(new RealmData.Known(GlobalPos.of(level.dimension(), helper.absolutePos(at)), Component.literal(name),
			owner == null ? Optional.empty() : Optional.of(owner.getUUID()), owner == null ? "" : owner.getGameProfile().getName(), "hamlet", 0, 0,
			level.getGameTime()));
	}

	/** Every block of the plaza at {@code origin} set as a finished one stands, and recorded finished. */
	private static void plaza(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = BlueprintLibrary.get(level, VillageWaystones.PLAZA).orElseThrow(() -> new GameTestAssertException("no plaza blueprint"));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(blueprint, placement);
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		BuildSiteManager.get(level).recordFinished(VillageWaystones.PLAZA, placement, UUID.randomUUID());
	}

	private static List<BlockPos> halls(List<VillageWaystones.Village> network) {
		return network.stream().map(VillageWaystones.Village::hall).toList();
	}

	private static void cleanUp(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (BlockPos at : List.of(HALL_A, HALL_B)) {
			RealmData.get(level.getServer()).forgetKnown(GlobalPos.of(level.dimension(), helper.absolutePos(at)));
			VillageWaystones.Data.get(level).forget(helper.absolutePos(at));
		}
	}

	/** Two villages with a raised stone each: {@code a} owned by {@code ownerA}, {@code b} by {@code ownerB}. */
	private static void twoVillages(GameTestHelper helper, ServerPlayer ownerA, ServerPlayer ownerB) {
		ServerLevel level = helper.getLevel();
		hall(helper, HALL_A, "Ashford", ownerA);
		hall(helper, HALL_B, "Brookmere", ownerB);
		plaza(helper, PLAZA_A);
		plaza(helper, PLAZA_B);
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		VillageWaystones.round(level, a);
		VillageWaystones.round(level, b);
		helper.assertTrue(VillageWaystones.standing(level, a) && VillageWaystones.standing(level, b),
			"the stones aren't standing: " + VillageWaystones.stoneOf(level, a) + ", " + VillageWaystones.stoneOf(level, b));
	}

	/**
	 * A hall nobody owns, looked at by a player who owns another village with a stone: the Map shows the player's own
	 * network, this village first, then theirs. With nobody looking (no viewer) it lists this village alone.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneUnowned"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneUnowned")
	public void aHallWithNoOwnerShowsTheViewersOwnNetwork(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			twoVillages(helper, null, player);
			BlockPos a = helper.absolutePos(HALL_A);
			BlockPos b = helper.absolutePos(HALL_B);
			List<VillageWaystones.Village> seen = VillageWaystones.network(level, a, player);
			helper.assertTrue(halls(seen).equals(List.of(a, b)), "the unowned hall's network for the player: " + seen);
			helper.assertTrue(seen.get(0).here() && !seen.get(1).here(), "this village first: " + seen);
			List<VillageWaystones.Village> nobody = VillageWaystones.network(level, a, null);
			helper.assertTrue(halls(nobody).equals(List.of(a)), "the unowned hall's network with no viewer: " + nobody);
			// A stranger who owns nothing sees only this village.
			ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
			List<VillageWaystones.Village> theirs = VillageWaystones.network(level, a, stranger);
			helper.assertTrue(halls(theirs).equals(List.of(a)), "a stranger's network: " + theirs);
			helper.getLevel().getServer().sendSystemMessage(Component.literal("[test] unowned hall: player " + seen.size()
				+ ", nobody " + nobody.size() + ", stranger " + theirs.size()));
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}

	/**
	 * A hall someone else owns: the Map shows its owner's network, never the viewer's, so the viewer's own village with a
	 * stone isn't listed there.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaWaystoneOthers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaWaystoneOthers")
	public void anotherOwnersHallShowsItsOwnersNetworkNotTheViewers(GameTestHelper helper) {
		RaidHelperCompatTests.clearAround(helper);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		ServerPlayer visitor = helper.makeMockServerPlayerInLevel();
		try {
			twoVillages(helper, owner, visitor);
			BlockPos a = helper.absolutePos(HALL_A);
			BlockPos b = helper.absolutePos(HALL_B);
			List<VillageWaystones.Village> seen = VillageWaystones.network(level, a, visitor);
			helper.assertTrue(halls(seen).equals(List.of(a)), "the visitor at the owner's hall: " + seen);
			List<VillageWaystones.Village> own = VillageWaystones.network(level, b, visitor);
			helper.assertTrue(halls(own).equals(List.of(b)), "the visitor at their own hall: " + own);
		} finally {
			cleanUp(helper);
		}
		helper.succeed();
	}
}

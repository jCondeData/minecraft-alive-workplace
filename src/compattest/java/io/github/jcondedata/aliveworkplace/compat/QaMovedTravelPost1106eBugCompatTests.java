package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.Direction;

/**
 * QA (lane-e-1106-2212, Create and saves): create.md's known limits say what a moved Travel Post keeps of its place in
 * the travel network is not tested. A Travel Post a player named "Riverside" and found, pushed along by Create's
 * Mechanical Piston (or vanilla's: the post has no block entity, so a plain piston moves it too): it should still be
 * the same post on the network, "Riverside", known to the player who found it (travel.md: posts are named when placed;
 * a player travels between the posts they've found).
 */
public class QaMovedTravelPost1106eBugCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TARGET = new BlockPos(7, 2, 8);

	private static void createPiston(GameTestHelper helper) {
		BlockPos piston = TARGET.east();
		helper.setBlock(piston.east(2), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston.east(), MechanicCompatTests.with(MechanicCompatTests.create("piston_extension_pole").defaultBlockState(), "facing", "west"));
		helper.setBlock(piston, MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("mechanical_piston").defaultBlockState(), "facing", "west"),
			"axis_along_first", "true"));
		helper.setBlock(piston.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
	}

	private static float speed(GameTestHelper helper) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(TARGET.east()));
		return be == null ? 0 : Nbt.getFloat(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Speed");
	}

	private static GlobalPos at(GameTestHelper helper, BlockPos pos) {
		return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos));
	}

	/** A Travel Post at {@link #TARGET} on the network as "Riverside", found by a player; returns that player. */
	private static UUID post(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		for (int i = 0; i <= 4; i++) {
			network.remove(at(helper, TARGET.west(i)));
		}
		helper.setBlock(TARGET, ModBlocks.TRAVEL_POST);
		TravelNetwork.Post post = network.add(at(helper, TARGET), "Riverside");
		UUID player = UUID.randomUUID();
		network.visit(player, post);
		return player;
	}

	private static void check(GameTestHelper helper, UUID player, BlockPos moved, String how) {
		helper.assertTrue(helper.getBlockState(moved).is(ModBlocks.TRAVEL_POST), "setup: the " + how + " didn't move the Travel Post to " + moved);
		TravelNetwork network = TravelNetwork.get(helper.getLevel().getServer());
		TravelNetwork.Post there = network.atOrAdd(helper.getLevel(), helper.absolutePos(moved));
		TravelNetwork.Post old = network.at(at(helper, TARGET));
		boolean known = there != null && network.known(player, null).stream().anyMatch(p -> p.id().equals(there.id()));
		helper.assertTrue(there != null && "Riverside".equals(there.name()) && known,
			"a Travel Post named Riverside, moved by a " + how + ", is on the network as " + (there == null ? "nothing" : "'" + there.name() + "'")
				+ " (known to the player who found it: " + known + "; the old spot: " + (old == null ? "nothing" : "'" + old.name() + "'") + ")");
	}

	/** Pushed one block by vanilla's piston (a redstone block powers it). */
	//$ gametest_ticks_batch AREA '100' '"qa_moved_travel_post_vanilla"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qa_moved_travel_post_vanilla")
	public void aTravelPostAVanillaPistonPushedIsStillRiverside(GameTestHelper helper) {
		UUID player = post(helper);
		helper.setBlock(TARGET.east(), Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.WEST));
		helper.setBlock(TARGET.east(2), Blocks.REDSTONE_BLOCK);
		helper.runAfterDelay(20, () -> {
			check(helper, player, TARGET.west(), "vanilla piston");
			helper.succeed();
		});
	}

	/** Pushed two blocks by Create's Mechanical Piston. */
	//$ gametest_ticks_batch AREA '400' '"qa_moved_travel_post_create"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_moved_travel_post_create")
	public void aTravelPostACreatePistonMovedIsStillRiverside(GameTestHelper helper) {
		UUID player = post(helper);
		createPiston(helper);
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(speed(helper) != 0, "the piston never turned, so the test proves nothing");
			check(helper, player, TARGET.west(2), "Create piston");
			helper.succeed();
		});
	}
}

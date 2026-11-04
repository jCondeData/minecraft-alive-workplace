package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.Cavalry;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane C, 2026-11-04): B950's fix from the guide's side (docs/guide/guard.md: a guard rides a tame, saddled horse
 * "within 24 blocks of the post"): both sides of 24 blocks, straight and on the diagonal, and how far up or down.
 */
public class QaGuardHorseReach1104cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos POST = new BlockPos(2, 2, 2);

	private record Post(GameTestHelper helper, Villager guard, BlockPos post, Vec3 middle) {
		/** A tame, saddled, still horse at {@code dx, dy, dz} from the post's middle (relative to its bottom). */
		AbstractHorse horse(double dx, double dy, double dz) {
			Vec3 at = middle.add(dx, dy, dz);
			AbstractHorse horse = EntityType.HORSE.create(helper.getLevel());
			horse.moveTo(at.x, at.y, at.z, 0, 0);
			horse.setTamed(true);
			horse.equipSaddle(new ItemStack(Items.SADDLE), null);
			horse.setNoAi(true);
			horse.setNoGravity(true);
			helper.getLevel().addFreshEntity(horse);
			return horse;
		}

		AbstractHorse free() {
			return Cavalry.freeHorse(helper.getLevel(), guard, post);
		}
	}

	private static Post post(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		Villager guard = helper.spawn(EntityType.VILLAGER, POST.south());
		guard.setNoAi(true);
		BlockPos post = helper.absolutePos(POST);
		Jobs.employ(helper.getLevel(), guard, post, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return new Post(helper, guard, post, Vec3.atBottomCenterOf(post));
	}

	/** A horse exactly 24 blocks off along the ground is within reach; one 24.5 off isn't. */
	//$ gametest_ticks_batch AREA '60' '"qa1104cHorseStraight"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1104cHorseStraight")
	public void aHorseTwentyFourBlocksOffIsTakenAndOneFurtherIsNot(GameTestHelper helper) {
		Post p = post(helper);
		AbstractHorse far = p.horse(24.5, 0, 0);
		helper.assertTrue(p.free() == null, "a horse 24.5 blocks from the post is the guard's");
		AbstractHorse near = p.horse(0, 0, 24);
		helper.assertTrue(p.free() == near, "a horse 24 blocks from the post isn't the guard's (got " + p.free() + ")");
		far.discard();
		near.discard();
		helper.succeed();
	}

	/** On the diagonal the reach is a circle, not a square: 17 by 17 (24.04 blocks) is out, 16 by 17 (23.35) is in. */
	//$ gametest_ticks_batch AREA '60' '"qa1104cHorseDiagonal"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1104cHorseDiagonal")
	public void onTheDiagonalTheReachIsStillTwentyFourBlocks(GameTestHelper helper) {
		Post p = post(helper);
		AbstractHorse corner = p.horse(17, 0, 17);
		helper.assertTrue(p.free() == null, "a horse 24.04 blocks off on the diagonal is the guard's");
		AbstractHorse inside = p.horse(16, 0, 17);
		helper.assertTrue(p.free() == inside, "a horse 23.35 blocks off on the diagonal isn't the guard's");
		corner.discard();
		inside.discard();
		helper.succeed();
	}

	/** Up or down: 8 blocks over the post (on a ledge) is in reach, 9 isn't; the nearer of two usable horses is chosen. */
	//$ gametest_ticks_batch AREA '60' '"qa1104cHorseHeight"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qa1104cHorseHeight")
	public void aHorseNineBlocksUpIsOutOfReach(GameTestHelper helper) {
		Post p = post(helper);
		helper.setBlock(POST.offset(4, 8, 4), Blocks.STONE);
		AbstractHorse high = p.horse(4, 9, 4);
		helper.assertTrue(p.free() == null, "a horse 9 blocks above the post is the guard's");
		AbstractHorse ledge = p.horse(10, 8, 0);
		helper.assertTrue(p.free() == ledge, "a horse 8 blocks above the post isn't the guard's");
		AbstractHorse ground = p.horse(3, 0, 0);
		helper.assertTrue(p.free() == ground, "the guard didn't choose the nearer of two horses");
		high.discard();
		ledge.discard();
		ground.discard();
		helper.succeed();
	}
}

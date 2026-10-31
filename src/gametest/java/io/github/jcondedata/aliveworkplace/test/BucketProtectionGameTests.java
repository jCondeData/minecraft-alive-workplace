package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Friends;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * B719: a bucket in a protected village is judged where it empties or fills, not where its user stands. The hall is at
 * (11, 2, 11) with a reach of 16, so x = 27 is the village's last column and x = 28 is outside. The bucket goes through
 * ServerPlayerGameMode.useItem, as the client's use-item packet does.
 */
public class BucketProtectionGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Stands {@code player} at {@code feet}, looking at {@code target}, and uses {@code held}. */
	static void pour(GameTestHelper helper, ServerPlayer player, BlockPos feet, Vec3 target, ItemStack held) {
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(feet));
		player.moveTo(at.x, at.y, at.z, 90f, 0f);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItem(player, helper.getLevel(), held, InteractionHand.MAIN_HAND);
	}

	static Vec3 top(GameTestHelper helper, BlockPos floor) {
		return Vec3.atCenterOf(helper.absolutePos(floor)).add(0, 0.5, 0);
	}

	/** A stranger outside can't pour into the village and is told whose it is; the owner, a friend and an operator can. */
	//$ gametest_ticks_batch AREA '60' '"bucketPouredInFromOutside"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "bucketPouredInFromOutside")
	public void onlyTheVillagesPeoplePourIntoItFromOutside(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		BlockPos ground = new BlockPos(26, 1, 11);
		BlockPos poured = ground.above();
		BlockPos outside = new BlockPos(28, 2, 11);
		List<QaLostCaravanGameTests.Seen> seen = new ArrayList<>();
		ServerPlayer stranger = QaLostCaravanGameTests.listener(helper, seen, 0, 0, 0, "Stranger");

		pour(helper, stranger, outside, top(helper, ground), new ItemStack(Items.LAVA_BUCKET));
		helper.assertTrue(helper.getBlockState(poured).isAir(), "a stranger outside poured lava into the village: " + helper.getBlockState(poured));
		helper.assertTrue(stranger.getMainHandItem().is(Items.LAVA_BUCKET), "the stranger's lava bucket was emptied: " + stranger.getMainHandItem());
		List<String> bar = QaLostCaravanGameTests.texts(seen, true);
		helper.assertTrue(bar.stream().anyMatch(t -> t.endsWith("is Owner's village: only they and their friends can change things here")),
			"the stranger wasn't told whose village it is: " + bar);
		QaLostCaravanGameTests.english(helper, bar);

		pour(helper, owner, outside, top(helper, ground), new ItemStack(Items.WATER_BUCKET));
		helper.assertTrue(helper.getBlockState(poured).is(Blocks.WATER), "the owner couldn't pour into his own village: " + helper.getBlockState(poured));
		helper.setBlock(poured, Blocks.AIR);

		ServerPlayer friend = ProtectionSpecGameTests.player(helper);
		Friends friends = Friends.get(helper.getLevel().getServer());
		friends.add(owner.getUUID(), friend.getUUID(), "Friend");
		try {
			pour(helper, friend, outside, top(helper, ground), new ItemStack(Items.WATER_BUCKET));
			helper.assertTrue(helper.getBlockState(poured).is(Blocks.WATER), "the owner's friend couldn't pour into the village: " + helper.getBlockState(poured));
		} finally {
			friends.remove(owner.getUUID(), friend.getUUID());
		}
		helper.setBlock(poured, Blocks.AIR);

		ServerPlayer op = ProtectionSpecGameTests.player(helper);
		var ops = helper.getLevel().getServer().getPlayerList().getOps();
		ops.add(new ServerOpListEntry(op.getGameProfile(), 4, false));
		try {
			pour(helper, op, outside, top(helper, ground), new ItemStack(Items.WATER_BUCKET));
			helper.assertTrue(helper.getBlockState(poured).is(Blocks.WATER), "an operator couldn't pour into the village: " + helper.getBlockState(poured));
		} finally {
			ops.remove(op.getGameProfile());
		}
		helper.setBlock(poured, Blocks.AIR);
		helper.succeed();
	}

	/** A stranger outside can't scoop water out of the village (the owner can), but may still pour outside it. */
	//$ gametest_ticks_batch AREA '60' '"bucketScoopedFromOutside"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "bucketScoopedFromOutside")
	public void aStrangerOutsideCantScoopButMayPourOutside(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		BlockPos pool = new BlockPos(26, 2, 11);
		BlockPos outside = new BlockPos(28, 2, 11);
		helper.setBlock(pool, Blocks.WATER);

		pour(helper, stranger, outside, Vec3.atCenterOf(helper.absolutePos(pool)), new ItemStack(Items.BUCKET));
		helper.assertTrue(helper.getBlockState(pool).is(Blocks.WATER), "a stranger outside scooped water out of the village");
		helper.assertTrue(stranger.getMainHandItem().is(Items.BUCKET), "the stranger's bucket was filled: " + stranger.getMainHandItem());

		pour(helper, owner, outside, Vec3.atCenterOf(helper.absolutePos(pool)), new ItemStack(Items.BUCKET));
		helper.assertTrue(owner.getMainHandItem().is(Items.WATER_BUCKET), "the owner couldn't scoop his own water: " + owner.getMainHandItem());
		helper.assertTrue(!helper.getBlockState(pool).is(Blocks.WATER), "the owner's scoop left the water: " + helper.getBlockState(pool));

		// Outside the village a stranger may still pour, from outside or from inside its edge
		BlockPos far = new BlockPos(29, 1, 11);
		pour(helper, stranger, outside, top(helper, far), new ItemStack(Items.WATER_BUCKET));
		helper.assertTrue(helper.getBlockState(far.above()).is(Blocks.WATER), "a stranger couldn't pour outside the village: " + helper.getBlockState(far.above()));
		helper.setBlock(far.above(), Blocks.AIR);
		BlockPos justOut = new BlockPos(28, 1, 11);
		pour(helper, stranger, new BlockPos(26, 2, 11), top(helper, justOut), new ItemStack(Items.WATER_BUCKET));
		helper.assertTrue(helper.getBlockState(justOut.above()).is(Blocks.WATER),
			"a stranger inside the edge couldn't pour just outside the village: " + helper.getBlockState(justOut.above()));
		helper.setBlock(justOut.above(), Blocks.AIR);
		helper.succeed();
	}
}

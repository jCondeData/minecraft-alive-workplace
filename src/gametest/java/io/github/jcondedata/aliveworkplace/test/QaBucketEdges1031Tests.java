package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-31, lane-c-1031-2153) of B719 from its spec and the guide (village-protection.md: only the owner, friends
 * and operators may "empty or fill buckets ... what counts is where the water or lava would go"). Beyond B719's own tests:
 * a bucket of fish (its own item kind), a waterlogged slab, and lava at the edge from both sides. Each refused use has a
 * control with protection off that lands exactly there, so the aim is proven. Hall at (11, 2, 11), reach 16.
 */
public class QaBucketEdges1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static ServerPlayer strangerAt(GameTestHelper helper, BlockPos feet, Vec3 aim) {
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		Vec3 f = Vec3.atBottomCenterOf(helper.absolutePos(feet));
		stranger.moveTo(f.x, f.y, f.z, 90f, 0f);
		stranger.lookAt(EntityAnchorArgument.Anchor.EYES, aim);
		return stranger;
	}

	private static ItemStack use(GameTestHelper helper, ServerPlayer player, Item item) {
		ItemStack stack = new ItemStack(item);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		player.gameMode.useItem(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND);
		return player.getMainHandItem();
	}

	private static int cods(GameTestHelper helper, BlockPos at) {
		return helper.getLevel().getEntities(EntityType.COD, new AABB(helper.absolutePos(at)).inflate(2), e -> true).size();
	}

	/** A bucket of cod poured from 17 blocks out onto the floor 15 blocks from the hall: no water and no fish inside. */
	//$ gametest_ticks_batch AREA '40' '"qaBucketEdgesFish1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaBucketEdgesFish1031")
	public void qaAStrangersBucketOfCodCantBePouredIn(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		BlockPos ground = new BlockPos(26, 1, 11);
		BlockPos poured = ground.above();
		ServerPlayer stranger = strangerAt(helper, new BlockPos(28, 2, 11), Vec3.atCenterOf(helper.absolutePos(ground)).add(0, 0.5, 0));

		VillageProtection.ENABLED = false; // control: the aim lands there
		use(helper, stranger, Items.COD_BUCKET);
		VillageProtection.ENABLED = true;
		helper.assertTrue(helper.getBlockState(poured).is(Blocks.WATER) && cods(helper, poured) == 1,
			"setup: the aimed bucket of cod didn't land at the target: " + helper.getBlockState(poured) + ", cod " + cods(helper, poured));
		helper.setBlock(poured, Blocks.AIR);
		helper.getLevel().getEntities(EntityType.COD, new AABB(helper.absolutePos(poured)).inflate(2), e -> true).forEach(e -> e.discard());

		ItemStack held = use(helper, stranger, Items.COD_BUCKET);
		helper.assertTrue(!helper.getBlockState(poured).is(Blocks.WATER) && cods(helper, poured) == 0,
			"a stranger outside poured a bucket of cod into the protected village: " + helper.getBlockState(poured) + ", cod " + cods(helper, poured));
		helper.assertTrue(held.is(Items.COD_BUCKET), "the refused stranger's bucket of cod was used up: he holds " + held);
		helper.succeed();
	}

	/** A slab inside the edge, aimed at from outside: a water bucket can't waterlog it. */
	//$ gametest_ticks_batch AREA '40' '"qaBucketEdgesSlab1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaBucketEdgesSlab1031")
	public void qaAStrangerOutsideCantWaterlogASlabInside(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		BlockPos slab = new BlockPos(26, 2, 11); // 15 blocks from the hall: inside
		helper.setBlock(slab, Blocks.OAK_SLAB);
		ServerPlayer stranger = strangerAt(helper, new BlockPos(29, 2, 11), Vec3.atCenterOf(helper.absolutePos(slab)).add(0, -0.25, 0));

		VillageProtection.ENABLED = false;
		use(helper, stranger, Items.WATER_BUCKET);
		VillageProtection.ENABLED = true;
		helper.assertTrue(helper.getBlockState(slab).getValue(BlockStateProperties.WATERLOGGED),
			"setup: the aimed water bucket didn't waterlog the slab: " + helper.getBlockState(slab));
		helper.setBlock(slab, Blocks.OAK_SLAB);

		ItemStack held = use(helper, stranger, Items.WATER_BUCKET);
		helper.assertTrue(helper.getBlockState(slab).getBlock() instanceof SlabBlock && !helper.getBlockState(slab).getValue(BlockStateProperties.WATERLOGGED),
			"a stranger outside waterlogged a slab inside the protected village: " + helper.getBlockState(slab));
		helper.assertTrue(held.is(Items.WATER_BUCKET), "the refused stranger's water bucket was emptied: he holds " + held);
		helper.succeed();
	}

	/** Lava at the edge from both sides: aimed at the floor just inside (16 out) refused; just outside (17 out) poured. */
	//$ gametest_ticks_batch AREA '40' '"qaBucketEdgesLava1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaBucketEdgesLava1031")
	public void qaLavaAtTheEdgeIsRefusedInsideAndPouredOutside(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		BlockPos insideFloor = new BlockPos(27, 1, 11); // its air above: 16 from the hall, the last cell inside
		BlockPos outsideFloor = new BlockPos(28, 1, 11); // 17: the first cell outside
		ServerPlayer stranger = strangerAt(helper, new BlockPos(29, 2, 11), Vec3.atCenterOf(helper.absolutePos(insideFloor)).add(0, 0.5, 0));

		VillageProtection.ENABLED = false; // control: the aim lands on the last cell inside
		use(helper, stranger, Items.LAVA_BUCKET);
		VillageProtection.ENABLED = true;
		helper.assertTrue(helper.getBlockState(insideFloor.above()).is(Blocks.LAVA),
			"setup: the aimed lava didn't land on the last cell inside: " + helper.getBlockState(insideFloor.above()));
		helper.setBlock(insideFloor.above(), Blocks.AIR);

		ItemStack held = use(helper, stranger, Items.LAVA_BUCKET);
		helper.assertTrue(!helper.getBlockState(insideFloor.above()).is(Blocks.LAVA),
			"a stranger outside poured lava onto the last cell inside the protected village (16 from the hall)");
		helper.assertTrue(held.is(Items.LAVA_BUCKET), "the refused lava bucket was emptied: he holds " + held);

		stranger.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(outsideFloor)).add(0, 0.5, 0));
		held = use(helper, stranger, Items.LAVA_BUCKET);
		helper.assertTrue(helper.getBlockState(outsideFloor.above()).is(Blocks.LAVA),
			"a stranger couldn't pour lava just outside the protected village (17 from the hall): " + helper.getBlockState(outsideFloor.above()));
		helper.setBlock(outsideFloor.above(), Blocks.AIR);
		helper.succeed();
	}

	/**
	 * A stranger visiting inside aims at the inward face of a post just outside the edge: the water would go beside that
	 * face, onto the last cell inside, so it's refused though the block it hits is outside.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaBucketEdgesFace1031"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaBucketEdgesFace1031")
	public void qaAPourBesideAnOutsideBlocksInwardFaceIsRefused(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		BlockPos post = new BlockPos(28, 2, 11); // 17 from the hall: outside
		BlockPos beside = post.west(); // 16: the last cell inside
		helper.setBlock(post, Blocks.OAK_LOG);
		ServerPlayer stranger = strangerAt(helper, new BlockPos(25, 2, 11), Vec3.atCenterOf(helper.absolutePos(post)).add(-0.5, 0, 0));

		VillageProtection.ENABLED = false; // control: the pour goes beside the post's west face
		use(helper, stranger, Items.WATER_BUCKET);
		VillageProtection.ENABLED = true;
		helper.assertTrue(helper.getBlockState(beside).is(Blocks.WATER), "setup: the aimed pour didn't land beside the post: " + helper.getBlockState(beside));
		helper.setBlock(beside, Blocks.AIR);

		ItemStack held = use(helper, stranger, Items.WATER_BUCKET);
		helper.assertTrue(!helper.getBlockState(beside).is(Blocks.WATER),
			"a stranger poured water onto the last cell inside the protected village by aiming at the inward face of a block just outside it");
		helper.assertTrue(held.is(Items.WATER_BUCKET), "the refused water bucket was emptied: he holds " + held);
		helper.succeed();
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.GlowItemFrame;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariants;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-01, lane-c-1101-0353): the B741, B742 and B743 fixes, tested from the guide (village-protection.md) and
 * the wiki (village-hall.md), not the code. Inside a protected village's area only the owner, friends and operators may
 * break or place blocks ("what counts is where the block or fire would go"), trample the fields ("a stranger who jumps
 * on the village's farmland (on foot or on a horse they ride) leaves it and its crops alone") and hurt item frames and
 * paintings "by hand or with arrows, tridents and potions". Protection is about players (raids and mobs aren't kept
 * out), and with the setting off nobody is protected. Hall at (11, 2, 11), reach 16: along z = 11, x = 27 is the last
 * cell inside and x = 28 the first outside (each test checks that with VillageProtection.keeper first).
 */
public class QaProtectionFixes1101Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final int ROW = 11;

	private static void click(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction face, ItemStack held) {
		BlockPos at = helper.absolutePos(pos);
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.gameMode.useItemOn(player, helper.getLevel(), held, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(at).relative(face, 0.5), face, at, false));
	}

	private static ServerPlayer strangerOutside(GameTestHelper helper, float yaw) {
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		Vec3 feet = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(31, 2, ROW + 1)));
		stranger.moveTo(feet.x, feet.y, feet.z, yaw, 0f);
		return stranger;
	}

	private static boolean inside(GameTestHelper helper, ServerPlayer who, BlockPos pos) {
		return VillageProtection.keeper(helper.getLevel(), who, helper.absolutePos(pos)).isPresent();
	}

	private static void edgeSetup(GameTestHelper helper, ServerPlayer stranger) {
		helper.assertTrue(inside(helper, stranger, new BlockPos(27, 2, ROW)), "setup: x = 27 should be inside the protected village");
		helper.assertTrue(!inside(helper, stranger, new BlockPos(28, 2, ROW)), "setup: x = 28 should be outside the protected village");
	}

	private static void op(GameTestHelper helper, ServerPlayer player) {
		var ops = helper.getLevel().getServer().getPlayerList().getOps();
		ops.add(new ServerOpListEntry(player.getGameProfile(), 4, false));
		Leftovers.after(helper, () -> ops.remove(player.getGameProfile()));
	}

	// ---- B741: placing at the edge counts where the block goes ----

	/**
	 * The edge from both sides, for a stranger outside: a post at x = 29 clicked on its west face places at x = 28
	 * (outside: allowed); a post at x = 28 clicked on its top or north face places at x = 28 (outside: allowed), on its
	 * west face at x = 27 (inside: refused); one at x = 28 two blocks up, clicked on its west face (inside "at any height":
	 * refused).
	 */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionFixes1101Edge"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionFixes1101Edge")
	public void qaPlacingAtTheEdgeCountsWhereTheBlockGoes(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = strangerOutside(helper, 90f);
		edgeSetup(helper, stranger);
		BlockPos far = new BlockPos(29, 3, ROW);
		BlockPos post = new BlockPos(28, 2, ROW);
		BlockPos high = new BlockPos(28, 6, ROW);
		helper.setBlock(far, Blocks.OAK_LOG);
		helper.setBlock(post, Blocks.OAK_LOG);
		helper.setBlock(high, Blocks.OAK_LOG);

		click(helper, stranger, far, Direction.WEST, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertTrue(helper.getBlockState(far.west()).is(Blocks.COBBLESTONE),
			"a stranger's block for x = 28 (the first cell outside) was refused: " + helper.getBlockState(far.west()));
		click(helper, stranger, post, Direction.NORTH, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertTrue(helper.getBlockState(post.north()).is(Blocks.COBBLESTONE),
			"a stranger's block on the north face of a post outside (landing outside) was refused: " + helper.getBlockState(post.north()));
		click(helper, stranger, post, Direction.WEST, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertTrue(helper.getBlockState(post.west()).isAir(),
			"a stranger outside placed a block at x = 27, inside the village: " + helper.getBlockState(post.west()));
		click(helper, stranger, high, Direction.WEST, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertTrue(helper.getBlockState(high.west()).isAir(),
			"a stranger outside placed a block inside the village 4 blocks up: " + helper.getBlockState(high.west()));
		helper.succeed();
	}

	/**
	 * A click on a replaceable block (short grass, a snow layer) places in the clicked cell itself, whatever face is
	 * clicked: grass at x = 28 clicked on its west face puts the block at x = 28, outside, so a stranger may.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionFixes1101Grass"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionFixes1101Grass")
	public void qaAStrangerMayReplaceGrassJustOutsideTheEdge(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = strangerOutside(helper, 90f);
		edgeSetup(helper, stranger);
		BlockPos grass = new BlockPos(28, 2, ROW);
		BlockPos snow = new BlockPos(28, 4, ROW);
		helper.setBlock(grass.below(), Blocks.GRASS_BLOCK);
		helper.setBlock(grass, Blocks.SHORT_GRASS);
		helper.setBlock(snow.below(), Blocks.STONE);
		helper.setBlock(snow, Blocks.SNOW);
		click(helper, stranger, grass, Direction.WEST, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertTrue(helper.getBlockState(grass).is(Blocks.COBBLESTONE),
			"a stranger's block replacing grass outside the village (clicked on its inward face) didn't land there: " + helper.getBlockState(grass));
		helper.assertTrue(helper.getBlockState(grass.west()).isAir(), "a block landed inside the village: " + helper.getBlockState(grass.west()));
		click(helper, stranger, snow, Direction.WEST, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertTrue(helper.getBlockState(snow).is(Blocks.COBBLESTONE),
			"a stranger's block replacing a snow layer outside the village didn't land there: " + helper.getBlockState(snow));
		helper.succeed();
	}

	/** A bed is two blocks: a stranger's bed whose foot is outside but whose head would reach inside is refused. */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionFixes1101Bed"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionFixes1101Bed")
	public void qaAStrangersBedCantReachIntoTheVillage(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer west = strangerOutside(helper, 90f);
		edgeSetup(helper, west);
		BlockPos ground = new BlockPos(28, 1, ROW);
		BlockPos upper = new BlockPos(28, 5, ROW); // a second row up, for the control
		helper.setBlock(ground, Blocks.STONE);
		helper.setBlock(upper, Blocks.STONE);
		// facing west: foot at x = 28 (outside), head at x = 27 (inside)
		click(helper, west, ground, Direction.UP, new ItemStack(Items.RED_BED));
		helper.assertTrue(!(helper.getBlockState(ground.above()).getBlock() instanceof BedBlock)
				&& !(helper.getBlockState(ground.above().west()).getBlock() instanceof BedBlock),
			"a stranger's bed reached into the village: " + helper.getBlockState(ground.above()) + " / " + helper.getBlockState(ground.above().west()));
		// control: facing east, foot at 28 and head at 29, both outside
		ServerPlayer east = strangerOutside(helper, -90f);
		click(helper, east, upper, Direction.UP, new ItemStack(Items.RED_BED));
		helper.assertTrue(helper.getBlockState(upper.above()).getBlock() instanceof BedBlock
				&& helper.getBlockState(upper.above().east()).getBlock() instanceof BedBlock,
			"setup: a stranger's bed wholly outside the village wasn't placed: " + helper.getBlockState(upper.above()) + " / " + helper.getBlockState(upper.above().east()));
		helper.setBlock(upper.above().east(), Blocks.AIR);
		helper.setBlock(upper.above(), Blocks.AIR);
		helper.succeed();
	}

	/** An operator outside may place just inside the edge; with Village Protection off in the settings, so may a stranger. */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionFixes1101OpOff"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionFixes1101OpOff")
	public void qaAnOperatorAndAnyoneWithProtectionOffPlaceJustInside(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer operator = strangerOutside(helper, 90f);
		op(helper, operator);
		ServerPlayer stranger = strangerOutside(helper, 90f);
		edgeSetup(helper, stranger);
		BlockPos byOp = new BlockPos(28, 2, ROW);
		BlockPos whenOff = new BlockPos(28, 5, ROW);
		helper.setBlock(byOp, Blocks.OAK_LOG);
		helper.setBlock(whenOff, Blocks.OAK_LOG);
		click(helper, operator, byOp, Direction.WEST, new ItemStack(Items.COBBLESTONE, 4));
		helper.assertTrue(helper.getBlockState(byOp.west()).is(Blocks.COBBLESTONE),
			"an operator outside couldn't place just inside a protected village: " + helper.getBlockState(byOp.west()));
		VillageProtection.ENABLED = false;
		click(helper, stranger, whenOff, Direction.WEST, new ItemStack(Items.COBBLESTONE, 4));
		VillageProtection.ENABLED = true;
		helper.assertTrue(helper.getBlockState(whenOff.west()).is(Blocks.COBBLESTONE),
			"with Village Protection off, a stranger outside still couldn't place just inside: " + helper.getBlockState(whenOff.west()));
		helper.succeed();
	}

	// ---- B742: trampling ----

	private static void land(GameTestHelper helper, Entity who, BlockPos farmland) {
		BlockPos at = helper.absolutePos(farmland);
		Vec3 feet = Vec3.atBottomCenterOf(at.above());
		who.moveTo(feet.x, feet.y, feet.z, 0f, 0f);
		helper.getLevel().getBlockState(at).getBlock().fallOn(helper.getLevel(), helper.getLevel().getBlockState(at), at, who, 1.5f);
	}

	private static void field(GameTestHelper helper, BlockPos f) {
		helper.setBlock(f, Blocks.FARMLAND);
		helper.setBlock(f.above(), Blocks.WHEAT);
	}

	/** At the edge: a stranger's landing on the last field inside (x = 27) leaves it; on the first outside (x = 28) tramples. */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionFixes1101TrampleEdge"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionFixes1101TrampleEdge")
	public void qaAStrangersLandingAtTheEdgeTramplesOnlyOutside(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		edgeSetup(helper, stranger);
		BlockPos in = new BlockPos(27, 1, ROW);
		BlockPos out = new BlockPos(28, 1, ROW);
		field(helper, in);
		field(helper, out);
		land(helper, stranger, in);
		land(helper, stranger, out);
		helper.assertTrue(helper.getBlockState(in).is(Blocks.FARMLAND) && helper.getBlockState(in.above()).is(Blocks.WHEAT),
			"a stranger trampled the last field inside the protected village: " + helper.getBlockState(in) + " under " + helper.getBlockState(in.above()));
		helper.assertTrue(helper.getBlockState(out).is(Blocks.DIRT),
			"a stranger's landing on a field just outside the village should trample it, as in vanilla: " + helper.getBlockState(out));
		helper.succeed();
	}

	/**
	 * Protection is about players: in a protected village a zombie and a riderless horse landing on farmland trample it
	 * as in vanilla; a villager doesn't (the mod spares villagers everywhere); an operator who isn't the owner tramples.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionFixes1101TrampleMobs"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionFixes1101TrampleMobs")
	public void qaMobsAndOperatorsTrampleInAProtectedVillageVillagersDont(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		helper.assertTrue(helper.getLevel().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING), "setup: mobGriefing should be on");
		ServerPlayer operator = ProtectionSpecGameTests.player(helper);
		op(helper, operator);
		BlockPos byZombie = new BlockPos(16, 1, 14);
		BlockPos byHorse = new BlockPos(14, 1, 16);
		BlockPos byVillager = new BlockPos(8, 1, 14);
		BlockPos byOp = new BlockPos(14, 1, 6);
		for (BlockPos f : new BlockPos[] {byZombie, byHorse, byVillager, byOp}) {
			field(helper, f);
		}
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, byZombie.above());
		zombie.setBaby(false);
		var horse = helper.spawn(EntityType.HORSE, byHorse.above());
		Villager villager = helper.spawn(EntityType.VILLAGER, byVillager.above());
		land(helper, zombie, byZombie);
		land(helper, horse, byHorse);
		land(helper, villager, byVillager);
		land(helper, operator, byOp);
		helper.assertTrue(helper.getBlockState(byZombie).is(Blocks.DIRT), "a zombie's landing in a protected village should trample as in vanilla: " + helper.getBlockState(byZombie));
		helper.assertTrue(helper.getBlockState(byHorse).is(Blocks.DIRT), "a riderless horse's landing should trample as in vanilla: " + helper.getBlockState(byHorse));
		helper.assertTrue(helper.getBlockState(byVillager).is(Blocks.FARMLAND), "a villager trampled a field: " + helper.getBlockState(byVillager));
		helper.assertTrue(helper.getBlockState(byOp).is(Blocks.DIRT), "an operator's landing should trample, as the owner's does: " + helper.getBlockState(byOp));
		zombie.discard();
		horse.discard();
		villager.discard();
		helper.succeed();
	}

	// ---- B743: frames and paintings ----

	private static ItemFrame frame(GameTestHelper helper, BlockPos wall, boolean glow) {
		helper.setBlock(wall, Blocks.OAK_PLANKS);
		BlockPos at = helper.absolutePos(wall.east());
		ItemFrame frame = glow ? new GlowItemFrame(helper.getLevel(), at, Direction.EAST) : new ItemFrame(helper.getLevel(), at, Direction.EAST);
		frame.setItem(new ItemStack(Items.DIAMOND), false);
		helper.getLevel().addFreshEntity(frame);
		Leftovers.after(helper, frame::discard);
		return frame;
	}

	private static Painting painting(GameTestHelper helper, BlockPos wall) {
		helper.setBlock(wall, Blocks.OAK_PLANKS);
		Painting painting = new Painting(helper.getLevel(), helper.absolutePos(wall.east()), Direction.EAST,
			helper.getLevel().registryAccess().registryOrThrow(Registries.PAINTING_VARIANT).getHolderOrThrow(PaintingVariants.KEBAB));
		helper.getLevel().addFreshEntity(painting);
		Leftovers.after(helper, painting::discard);
		return painting;
	}

	private static void trident(GameTestHelper helper, ServerPlayer thrower, HangingEntity target) {
		ThrownTrident trident = new ThrownTrident(EntityType.TRIDENT, helper.getLevel());
		trident.setOwner(thrower);
		trident.setPos(target.position().add(1, 0, 0));
		target.hurt(helper.getLevel().damageSources().trident(trident, thrower), 8f);
	}

	private static void arrow(GameTestHelper helper, ServerPlayer shooter, HangingEntity target) {
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setOwner(shooter);
		arrow.setPos(target.position().add(1, 0, 0));
		target.hurt(helper.getLevel().damageSources().arrow(arrow, shooter), 6f);
	}

	/** A stranger's trident leaves a glow item frame's item and a painting inside; the owner's trident knocks the item out. */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionFixes1101Trident"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionFixes1101Trident")
	public void qaAStrangersTridentLeavesGlowFramesAndPaintings(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ItemFrame glow = frame(helper, new BlockPos(15, 3, 14), true);
		Painting art = painting(helper, new BlockPos(15, 3, 8));
		ItemFrame ownersGlow = frame(helper, new BlockPos(7, 3, 14), true);
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, glow.blockPosition()).isPresent(), "setup: the glow frame should be inside");
		trident(helper, stranger, glow);
		trident(helper, stranger, art);
		helper.assertTrue(glow.isAlive() && glow.getItem().is(Items.DIAMOND),
			"a stranger's trident knocked the item out of a glow item frame inside: alive " + glow.isAlive() + ", holds " + glow.getItem());
		helper.assertTrue(art.isAlive(), "a stranger's trident broke a painting inside the protected village");
		trident(helper, owner, ownersGlow);
		helper.assertTrue(ownersGlow.getItem().isEmpty(), "the owner's trident should knock the item out of their glow frame: " + ownersGlow.getItem());
		helper.succeed();
	}

	/**
	 * The frame's place counts, not the shooter's: a stranger standing outside can't shoot the item out of a frame just
	 * inside the edge (x = 27); a frame just outside (x = 28 and up) is fair game. An operator's arrow knocks it out inside.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionFixes1101FrameEdge"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionFixes1101FrameEdge")
	public void qaAStrangerOutsideCantShootAFrameJustInside(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = strangerOutside(helper, 90f);
		edgeSetup(helper, stranger);
		ServerPlayer operator = ProtectionSpecGameTests.player(helper);
		op(helper, operator);
		ItemFrame in = frame(helper, new BlockPos(26, 3, ROW), false); // hangs at x = 27
		ItemFrame out = frame(helper, new BlockPos(27, 6, ROW), false); // hangs at x = 28
		ItemFrame byOp = frame(helper, new BlockPos(15, 3, 14), false);
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, in.blockPosition()).isPresent(), "setup: frame at " + in.blockPosition() + " should be inside");
		helper.assertTrue(VillageProtection.keeper(helper.getLevel(), stranger, out.blockPosition()).isEmpty(), "setup: frame at " + out.blockPosition() + " should be outside");
		arrow(helper, stranger, in);
		arrow(helper, stranger, out);
		arrow(helper, operator, byOp);
		helper.assertTrue(in.isAlive() && in.getItem().is(Items.DIAMOND),
			"a stranger outside shot the item out of a frame just inside the village: holds " + in.getItem());
		helper.assertTrue(out.getItem().isEmpty(), "a stranger's arrow at a frame just outside the village should knock its item out: " + out.getItem());
		helper.assertTrue(byOp.getItem().isEmpty(), "an operator's arrow should knock the item out of a frame inside: " + byOp.getItem());
		helper.succeed();
	}

	/** By hand: a stranger punching a glow item frame or a painting inside leaves them; the owner's punch takes the item out. */
	//$ gametest_ticks_batch AREA '40' '"qaProtectionFixes1101Punch"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaProtectionFixes1101Punch")
	public void qaAStrangersPunchLeavesGlowFramesAndPaintings(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ItemFrame glow = frame(helper, new BlockPos(15, 3, 14), true);
		Painting art = painting(helper, new BlockPos(15, 3, 8));
		ItemFrame ownersGlow = frame(helper, new BlockPos(7, 3, 14), true);
		stranger.moveTo(glow.getX() + 1.5, glow.getY() - 1, glow.getZ(), 90f, 0f);
		punch(stranger, glow);
		stranger.moveTo(art.getX() + 1.5, art.getY() - 1, art.getZ(), 90f, 0f);
		punch(stranger, art);
		helper.assertTrue(glow.isAlive() && glow.getItem().is(Items.DIAMOND),
			"a stranger's punch took the item out of a glow frame inside: holds " + glow.getItem());
		helper.assertTrue(art.isAlive(), "a stranger's punch broke a painting inside the protected village");
		owner.moveTo(ownersGlow.getX() + 1.5, ownersGlow.getY() - 1, ownersGlow.getZ(), 90f, 0f);
		punch(owner, ownersGlow);
		helper.assertTrue(ownersGlow.getItem().isEmpty(), "the owner's punch should take the item out of their glow frame: " + ownersGlow.getItem());
		helper.succeed();
	}

	/** A left click on an entity, as the client sends it (the attack packet through the player's connection). */
	private static void punch(ServerPlayer player, Entity target) {
		player.connection.handleInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket.createAttackPacket(target, false));
	}

	/**
	 * A real arrow in flight (shot west at the frame and left to the game's own physics): a stranger's arrow leaves the
	 * framed diamond inside; the owner's, shot the same way at another frame, knocks it out.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaProtectionFixes1101Flight"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaProtectionFixes1101Flight")
	public void qaAStrangersArrowInFlightLeavesTheFrame(GameTestHelper helper) {
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner);
		ServerPlayer stranger = ProtectionSpecGameTests.player(helper);
		ItemFrame kept = frame(helper, new BlockPos(15, 3, 14), false);
		ItemFrame ownersFrame = frame(helper, new BlockPos(15, 3, 8), false);
		fly(helper, stranger, kept);
		fly(helper, owner, ownersFrame);
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(ownersFrame.getItem().isEmpty(), "setup: the owner's arrow in flight didn't knock the item out of the frame: " + ownersFrame.getItem());
			helper.assertTrue(kept.isAlive() && kept.getItem().is(Items.DIAMOND),
				"a stranger's arrow in flight knocked the diamond out of a frame inside the protected village: holds " + kept.getItem());
			helper.succeed();
		});
	}

	private static void fly(GameTestHelper helper, ServerPlayer shooter, ItemFrame target) {
		Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
		arrow.setOwner(shooter);
		Vec3 aim = target.getBoundingBox().getCenter();
		arrow.setPos(aim.x + 4, aim.y, aim.z);
		arrow.shoot(-1, 0.02, 0, 2.5f, 0f);
		helper.getLevel().addFreshEntity(arrow);
		Leftovers.after(helper, arrow::discard);
	}
}

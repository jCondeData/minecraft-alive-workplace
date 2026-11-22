package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.done;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.hall;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateIntoProtectedVillage1107eCompatTests.speed;

import com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlockEntity;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.StandIns;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane E, 2026-11-22): the other side of B808, B843, B844, B993, B994, B1015 and B1033 (fixed 2026-11-18): a
 * protected village must keep working for its owner. village-hall.md: with protection on "only the owner, their friends
 * and operators may break or place blocks, open chests" in the hall's area, and machines outside stop at its edge
 * (B745); create.md and village-hall.md's notes: machines inside a village work as before, a Deployer acts for the
 * player who placed it, and a stopped contraption's blocks that can't be set down are dropped at its machine.
 *
 * <p>Written from those promises: the owner's own machines inside (a funnel between two chests, a Mechanical Arm between
 * two depots, a saw, a bearing set down), his own and his friend's Deployers (also after the deployer and the hall are
 * saved and loaded again), two villages side by side, the mod's own Builder putting up a Create build and the Mechanic
 * mending a belt in a protected village, and a stranger's stopped bearing outside whose block is dropped, not deleted.
 *
 * <p>The small village is {@link QaCreateIntoProtectedVillage1107eCompatTests}'s: the hall at (3, 2, 15), 8 columns
 * across (x = 11 is the last column on the hall's row). Each test also checks the protection really is on (a machine
 * just outside would be refused at the spot), so a pass never comes from protection being off.
 */
public class QaCreateOwnVillage1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = QaCreateIntoProtectedVillage1107eCompatTests.HALL;
	private static final UUID STRANGER = UUID.fromString("7c0ffee0-1122-4e00-9a00-00000000beef");
	private static final UUID GINA = UUID.fromString("61a1a000-1122-4e00-9a00-00000000f00d");

	/** Calibration: a machine just outside the village (x = 13 on the hall's row) may not change {@code inside}. */
	private static void assertGuarded(GameTestHelper helper, VillageHallBlockEntity hall, BlockPos inside) {
		ServerLevel level = helper.getLevel();
		BlockPos outside = helper.absolutePos(new BlockPos(HALL.getX() + VillageHalls.RADIUS + 2, 2, HALL.getZ()));
		if (VillageProtection.mayMachineChange(level, outside, helper.absolutePos(inside))) {
			done(helper, hall);
			helper.fail("set-up: the village isn't protected (a machine outside may change " + inside.toShortString() + "), so the test proves nothing");
		}
	}

	private static int itemsOnTheGround(GameTestHelper helper, Item item) {
		int n = 0;
		for (ItemEntity e : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(item))) {
			n += e.getItem().getCount();
		}
		return n;
	}

	private static int count(Container c, Item item) {
		int n = 0;
		for (int s = 0; s < c.getContainerSize(); s++) {
			if (c.getItem(s).is(item)) {
				n += c.getItem(s).getCount();
			}
		}
		return n;
	}

	// ---------------------------------------------------------------- 1. the owner's machines inside

	/**
	 * A brass funnel pulling from the bottom of a chest inside the village drops the diamonds onto an andesite funnel on a
	 * chest below, also inside: the diamonds move from one to the other, none lost.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_own_funnel"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_own_funnel")
	public void aFunnelInsideAProtectedVillageMovesItemsBetweenItsChests(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		BlockPos from = new BlockPos(6, 6, 12);
		BlockPos to = new BlockPos(6, 2, 12);
		helper.setBlock(to, Blocks.CHEST);
		helper.setBlock(to.above(), MechanicCompatTests.with(MechanicCompatTests.create("andesite_funnel").defaultBlockState(), "facing", "up"));
		helper.setBlock(from, Blocks.CHEST);
		Container a = helper.getBlockEntity(from);
		a.setItem(0, new ItemStack(Items.DIAMOND, 16));
		helper.setBlock(from.below(), MechanicCompatTests.with(MechanicCompatTests.with(MechanicCompatTests.create("brass_funnel").defaultBlockState(),
			"facing", "down"), "extracting", "true"));
		assertGuarded(helper, hall, from);
		helper.runAfterDelay(300, () -> {
			Container b = helper.getBlockEntity(to);
			int left = count(a, Items.DIAMOND);
			int moved = count(b, Items.DIAMOND);
			int lying = itemsOnTheGround(helper, Items.DIAMOND);
			done(helper, hall);
			helper.assertTrue(moved == 16, "the owner's funnels inside his protected village moved " + moved + " of 16 diamonds into the lower chest ("
				+ left + " left in the upper one, " + lying + " on the ground)");
			helper.succeed();
		});
	}

	private static CompoundTag armPoint(ServerLevel level, BlockPos pos, BlockPos arm, boolean take) {
		ArmInteractionPoint point = ArmInteractionPoint.create(level, pos, level.getBlockState(pos));
		if (point == null) {
			throw new GameTestAssertException("set-up: the arm can't use the block at " + pos + ": " + level.getBlockState(pos));
		}
		if ((point.getMode() == ArmInteractionPoint.Mode.TAKE) != take) {
			point.cycleMode();
		}
		return point.serialize(arm);
	}

	/** A Mechanical Arm inside the village takes the diamonds off one depot inside and puts them on another inside. */
	//$ gametest_ticks_batch AREA '500' '"qa_e1122_own_arm"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qa_e1122_own_arm")
	public void aMechanicalArmInsideAProtectedVillageMovesItemsBetweenItsDepots(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		ServerLevel level = helper.getLevel();
		BlockPos inDepot = new BlockPos(5, 2, 12);
		BlockPos arm = new BlockPos(7, 2, 12);
		BlockPos outDepot = new BlockPos(9, 2, 12);
		helper.setBlock(inDepot, MechanicCompatTests.create("depot"));
		helper.setBlock(outDepot, MechanicCompatTests.create("depot"));
		long in = QaCreateDeployerOnOurThings1102eCompatTests.insert(level, helper.absolutePos(inDepot), new ItemStack(Items.DIAMOND, 16));
		if (in != 16) {
			done(helper, hall);
			helper.fail("set-up: the depot took " + in + " of 16 diamonds");
		}
		helper.setBlock(arm, MechanicCompatTests.create("mechanical_arm"));
		helper.setBlock(arm.north().below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
		helper.setBlock(arm.north(), MechanicCompatTests.with(MechanicCompatTests.create("cogwheel").defaultBlockState(), "axis", "y"));
		ListTag points = new ListTag();
		points.add(armPoint(level, helper.absolutePos(inDepot), helper.absolutePos(arm), true));
		points.add(armPoint(level, helper.absolutePos(outDepot), helper.absolutePos(arm), false));
		BlockEntity be = helper.getBlockEntity(arm);
		CompoundTag tag = be.saveWithoutMetadata(level.registryAccess());
		tag.put("InteractionPoints", points);
		be.loadWithComponents(tag, level.registryAccess());
		assertGuarded(helper, hall, inDepot);
		helper.runAfterDelay(400, () -> {
			long left = QaCreateMoreMachinesProtection1107eCompatTests.diamonds(helper, inDepot);
			long moved = QaCreateMoreMachinesProtection1107eCompatTests.diamonds(helper, outDepot);
			float turning = speed(helper, arm);
			done(helper, hall);
			helper.assertTrue(turning != 0, "the arm never turned");
			helper.assertTrue(moved > 0 && left < 16, "the owner's Mechanical Arm inside his protected village moved nothing: the first depot holds "
				+ left + " of 16 diamonds, the second " + moved);
			helper.succeed();
		});
	}

	/** A Mechanical Saw inside the village cuts the log in front of it, inside too. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_own_saw"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_own_saw")
	public void aMechanicalSawInsideAProtectedVillageCutsALogThere(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		BlockPos log = new BlockPos(6, 2, 12);
		helper.setBlock(log, Blocks.OAK_LOG);
		QaCreateMoreMachinesProtection1107eCompatTests.saw(helper, log.east());
		assertGuarded(helper, hall, log);
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper, log.east());
			BlockState there = helper.getBlockState(log);
			done(helper, hall);
			helper.assertTrue(turning != 0, "the saw never turned");
			helper.assertTrue(!there.is(Blocks.OAK_LOG), "the owner's Mechanical Saw inside his protected village didn't cut the log in front of it: " + there);
			helper.succeed();
		});
	}

	/** The bearing inside the village (x = 6, z = 12) and its arm reaching east; half a turn puts the plank at x = 4. */
	private static final BlockPos OWN_BEARING = new BlockPos(6, 2, 12);
	private static final BlockPos OWN_LANDING = new BlockPos(4, 3, 12);

	private static float angle(MechanicalBearingBlockEntity bearing) {
		return ((bearing.getInterpolatedAngle(1f) % 360) + 360) % 360;
	}

	/**
	 * A bearing at {@code bearing} facing up (a Creative Motor under it) with an arm of slime, slime and an oak plank to its
	 * east; when the arm is half way round it is stopped (Create's {@code disassemble}), and 200 ticks later {@code check}
	 * runs with the angle.
	 */
	private static void halfTurn(GameTestHelper helper, BlockPos at, Runnable undo, Consumer<Float> check) {
		helper.setBlock(at.below(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "up"));
		helper.setBlock(at, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_bearing").defaultBlockState(), "facing", "up"));
		helper.setBlock(at.above(), Blocks.SLIME_BLOCK);
		helper.setBlock(at.above().east(), Blocks.SLIME_BLOCK);
		helper.setBlock(at.above().east(2), Blocks.OAK_PLANKS);
		float[] stoppedAt = {Float.NaN};
		boolean[] ran = {false};
		helper.onEachTick(() -> {
			if (!Float.isNaN(stoppedAt[0])) {
				return;
			}
			if (helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof MechanicalBearingBlockEntity bearing && bearing.isRunning()) {
				ran[0] = true;
				float a = angle(bearing);
				if (a >= 170 && a <= 190) {
					stoppedAt[0] = a;
					bearing.disassemble();
				}
			}
		});
		helper.runAfterDelay(400, () -> {
			undo.run();
			helper.assertTrue(ran[0], "the bearing never turned its arm");
			helper.assertTrue(!Float.isNaN(stoppedAt[0]), "the arm never got half way round");
			check.accept(stoppedAt[0]);
			helper.succeed();
		});
	}

	/** The owner's bearing inside his protected village turns its arm and, stopped, sets every block down inside, none dropped. */
	//$ gametest_ticks_batch AREA '600' '"qa_e1122_own_bearing"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1122_own_bearing")
	public void aMechanicalBearingInsideAProtectedVillageSetsItsBlocksDownThere(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		assertGuarded(helper, hall, OWN_LANDING);
		halfTurn(helper, OWN_BEARING, () -> done(helper, hall), angle -> {
			StringBuilder row = new StringBuilder();
			for (int x = 3; x <= 9; x++) {
				row.append(x).append(": ").append(helper.getBlockState(new BlockPos(x, 3, 12)).getBlock().getDescriptionId()).append("; ");
			}
			int planks = itemsOnTheGround(helper, Items.OAK_PLANKS);
			int slime = itemsOnTheGround(helper, Items.SLIME_BLOCK);
			helper.assertTrue(helper.getBlockState(OWN_LANDING).is(Blocks.OAK_PLANKS), "the owner's bearing inside his protected village (stopped at "
				+ angle + " degrees) didn't set its plank down at x = 4: " + row + "; on the ground " + planks + " planks, " + slime + " slime blocks");
			helper.assertTrue(helper.getBlockState(OWN_LANDING.east()).is(Blocks.SLIME_BLOCK) && helper.getBlockState(OWN_BEARING.above()).is(Blocks.SLIME_BLOCK),
				"the owner's bearing didn't set its slime blocks down: " + row);
			helper.assertTrue(planks == 0 && slime == 0, "the owner's bearing dropped its blocks as items inside his own village: " + planks
				+ " planks, " + slime + " slime blocks on the ground");
		});
	}

	// ---------------------------------------------------------------- 2. Deployers: whom they act for, saved and loaded

	/** Saves the block entity at {@code pos} and loads it again in its place, as a chunk unloaded and loaded does. */
	private static void reload(GameTestHelper helper, BlockPos pos) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		BlockEntity be = level.getBlockEntity(abs);
		if (be == null) {
			throw new GameTestAssertException("reload: no block entity at " + pos.toShortString());
		}
		CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
		BlockState state = level.getBlockState(abs);
		level.removeBlockEntity(abs);
		BlockEntity again = BlockEntity.loadStatic(abs, state, tag, level.registryAccess());
		if (again == null) {
			throw new GameTestAssertException("reload: the block entity at " + pos.toShortString() + " didn't load from " + tag);
		}
		level.setBlockEntity(again);
	}

	/** Whom the deployer at {@code at}'s stand-in player acts for (StandIns), or "?" if it has none. */
	private static String actsFor(GameTestHelper helper, BlockPos at) {
		try {
			BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(at));
			Object player = be.getClass().getMethod("getPlayer").invoke(be);
			return player instanceof Player p ? String.valueOf(StandIns.person(p)) : "?";
		} catch (ReflectiveOperationException e) {
			return "? (" + e + ")";
		}
	}

	private static final BlockPos OWN_DEPLOYER = new BlockPos(8, 2, 13);
	private static final BlockPos STRANGER_DEPLOYER = new BlockPos(8, 2, 18);

	/**
	 * In the owner's protected village his own Deployer places its cobblestone and a stranger's places nothing. Then both
	 * deployers and the hall are saved and loaded again (a restart): the owner's deployer still acts for him and places its
	 * next cobblestone, the stranger's still places nothing, and the village is still protected.
	 */
	//$ gametest_ticks_batch AREA '700' '"qa_e1122_own_deployer_reload"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qa_e1122_own_deployer_reload")
	public void theOwnersDeployerPlacesInHisProtectedVillageBeforeAndAfterAReload(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		UUID owner = hall.owner();
		BlockPos own = OWN_DEPLOYER.west(2);
		BlockPos theirs = STRANGER_DEPLOYER.west(2);
		QaCreateDeployerOnOurThings1102eCompatTests.deployer(helper, OWN_DEPLOYER, owner, new ItemStack(Items.COBBLESTONE, 1));
		QaCreateDeployerOnOurThings1102eCompatTests.deployer(helper, STRANGER_DEPLOYER, STRANGER, new ItemStack(Items.COBBLESTONE, 1));
		assertGuarded(helper, hall, own);
		helper.runAfterDelay(200, () -> {
			if (!helper.getBlockState(own).is(Blocks.COBBLESTONE) || !helper.getBlockState(theirs).isAir()) {
				BlockState a = helper.getBlockState(own);
				BlockState b = helper.getBlockState(theirs);
				float sa = speed(helper, OWN_DEPLOYER);
				done(helper, hall);
				helper.assertTrue(a.is(Blocks.COBBLESTONE), "the owner's own Deployer in his protected village didn't place its cobblestone: " + a
					+ " (speed " + sa + ", acts for " + actsFor(helper, OWN_DEPLOYER) + ", owner " + owner + ")");
				helper.assertTrue(b.isAir(), "a stranger's Deployer in the protected village placed " + b);
			}
			ItemStack kept = QaCreateDeployerOnOurThings1102eCompatTests.held(helper, STRANGER_DEPLOYER);
			if (!kept.is(Items.COBBLESTONE)) {
				done(helper, hall);
				helper.fail("the stranger's deployer lost its cobblestone though it placed nothing: it holds " + kept);
			}
			// a restart: the deployers and the hall saved and loaded again
			helper.setBlock(own, Blocks.AIR);
			reload(helper, OWN_DEPLOYER);
			reload(helper, STRANGER_DEPLOYER);
			reload(helper, HALL);
		});
		helper.runAfterDelay(206, () -> {
			QaCreateDeployerOnOurThings1102eCompatTests.insert(helper.getLevel(), helper.absolutePos(OWN_DEPLOYER), new ItemStack(Items.COBBLESTONE, 1));
			QaCreateDeployerOnOurThings1102eCompatTests.insert(helper.getLevel(), helper.absolutePos(STRANGER_DEPLOYER), new ItemStack(Items.COBBLESTONE, 1));
		});
		helper.runAfterDelay(500, () -> {
			VillageHallBlockEntity loaded = helper.getBlockEntity(HALL);
			boolean stillProtected = loaded.isProtected();
			boolean guarded = !VillageProtection.mayMachineChange(helper.getLevel(),
				helper.absolutePos(new BlockPos(HALL.getX() + VillageHalls.RADIUS + 2, 2, HALL.getZ())), helper.absolutePos(own));
			BlockState a = helper.getBlockState(own);
			BlockState b = helper.getBlockState(theirs);
			String ownFor = actsFor(helper, OWN_DEPLOYER);
			String theirFor = actsFor(helper, STRANGER_DEPLOYER);
			float sa = speed(helper, OWN_DEPLOYER);
			ItemStack held = QaCreateDeployerOnOurThings1102eCompatTests.held(helper, OWN_DEPLOYER);
			done(helper, loaded);
			helper.assertTrue(stillProtected && guarded, "after the reload the village isn't protected any more (hall says " + stillProtected
				+ ", machines outside refused: " + guarded + ")");
			helper.assertTrue(a.is(Blocks.COBBLESTONE), "after a reload the owner's Deployer didn't place in his protected village: " + a
				+ " (speed " + sa + ", holding " + held + ", acts for " + ownFor + ", owner " + owner + ")");
			helper.assertTrue(ownFor.equals(String.valueOf(owner)), "after a reload the owner's Deployer acts for " + ownFor + ", not " + owner);
			helper.assertTrue(b.isAir(), "after a reload a stranger's Deployer placed " + b + " in the protected village (acts for " + theirFor + ")");
			helper.succeed();
		});
	}

	/** A Deployer placed by the owner's friend (/workplace friend add) places in the owner's protected village, as the friend may. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_friend_deployer"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_friend_deployer")
	public void aFriendsDeployerPlacesInAProtectedVillage(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		UUID owner = hall.owner();
		Friends friends = Friends.get(helper.getLevel().getServer());
		friends.add(owner, GINA, "Gina");
		BlockPos target = OWN_DEPLOYER.west(2);
		QaCreateDeployerOnOurThings1102eCompatTests.deployer(helper, OWN_DEPLOYER, GINA, new ItemStack(Items.COBBLESTONE, 1));
		assertGuarded(helper, hall, target);
		helper.runAfterDelay(240, () -> {
			BlockState there = helper.getBlockState(target);
			float turning = speed(helper, OWN_DEPLOYER);
			String forWhom = actsFor(helper, OWN_DEPLOYER);
			friends.remove(owner, GINA);
			done(helper, hall);
			helper.assertTrue(turning != 0, "the deployer never turned");
			helper.assertTrue(there.is(Blocks.COBBLESTONE), "the owner's friend's Deployer didn't place its cobblestone in his protected village: "
				+ there + " (acts for " + forWhom + ")");
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- 3. two protected villages side by side

	/** Gina's hall 17 columns east of Frank's: on the hall's row Frank's village ends at x = 11, Gina's starts at x = 12. */
	private static final BlockPos GINAS_HALL = new BlockPos(HALL.getX() + 17, 2, HALL.getZ());

	/**
	 * A drill in Frank's protected village at its last column, facing east into Gina's protected village, breaks nothing
	 * there; the same kind of drill in Frank's village facing into Frank's own area breaks its block.
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_two_villages"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_two_villages")
	public void aMachineInOneProtectedVillageCantReachIntoTheNextButWorksInItsOwn(GameTestHelper helper) {
		VillageHallBlockEntity franks = hall(helper, true);
		helper.setBlock(GINAS_HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity ginas = helper.getBlockEntity(GINAS_HALL);
		ginas.setOwner(GINA, "Gina");
		ginas.setProtected(true);
		BlockPos drill = new BlockPos(11, 2, HALL.getZ()); // Frank's last column
		BlockPos ginasEdge = drill.east(); // x = 12: Gina's first column
		// the second drill stands at x = 10, z = 18 (inside Frank's: 49 + 9 = 58), facing west at x = 9 (inside Frank's too)
		BlockPos ownDrill = new BlockPos(10, 2, HALL.getZ() + 3);
		BlockPos ownTarget = ownDrill.west();
		helper.setBlock(ginasEdge, Blocks.OAK_PLANKS);
		helper.setBlock(ownTarget, Blocks.OAK_PLANKS);
		helper.setBlock(drill.west(), MechanicCompatTests.with(MechanicCompatTests.create("creative_motor").defaultBlockState(), "facing", "east"));
		helper.setBlock(drill, MechanicCompatTests.with(MechanicCompatTests.create("mechanical_drill").defaultBlockState(), "facing", "east"));
		QaCreateIntoProtectedVillage1107eCompatTests.drill(helper, ownDrill);
		ServerLevel level = helper.getLevel();
		boolean ginasStartsThere = !VillageProtection.mayMachineChange(level, helper.absolutePos(drill), helper.absolutePos(ginasEdge));
		boolean drillInFranks = VillageProtection.mayMachineChange(level, helper.absolutePos(drill), helper.absolutePos(drill.west(3)));
		if (!ginasStartsThere || !drillInFranks) {
			ginas.setProtected(false);
			done(helper, franks);
			helper.fail("set-up: Gina's village doesn't start at x = 12 (" + ginasStartsThere + ") or the drill at x = 11 isn't in Frank's (" + drillInFranks + ")");
		}
		final BlockPos own = ownDrill;
		helper.runAfterDelay(300, () -> {
			float turning = speed(helper, drill);
			float ownTurning = speed(helper, own);
			BlockState ginasBlock = helper.getBlockState(ginasEdge);
			BlockState franksBlock = helper.getBlockState(ownTarget);
			ginas.setProtected(false);
			done(helper, franks);
			helper.assertTrue(turning != 0 && ownTurning != 0, "a drill never turned (" + turning + ", " + ownTurning + ")");
			helper.assertTrue(ginasBlock.is(Blocks.OAK_PLANKS), "a drill in Frank's protected village broke Gina's block in her protected village: "
				+ ginasBlock);
			helper.assertTrue(franksBlock.isAir(), "a drill in Frank's protected village didn't break the block in front of it in his own village: "
				+ franksBlock);
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- 5. a stranger's contraption stopped over the village

	/**
	 * The stranger's bearing outside the protected village ({@link QaCreateBearingProtection1108eCompatTests}'s, stopped
	 * half way round with its plank over the village's last column): the plank isn't set down inside, and it isn't lost
	 * either: it lies as an item at the machine, and the slime blocks outside are set down where they stopped.
	 */
	//$ gametest_ticks_batch AREA '600' '"qa_e1122_stranger_bearing_drops"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qa_e1122_stranger_bearing_drops")
	public void aStrangersStoppedBearingDropsTheBlockItCantSetDownInside(GameTestHelper helper) {
		VillageHallBlockEntity hall = hall(helper, true);
		BlockPos landing = QaCreateBearingProtection1108eCompatTests.LANDING;
		BlockPos bearing = QaCreateBearingProtection1108eCompatTests.BEARING;
		assertGuarded(helper, hall, landing);
		QaCreateBearingProtection1108eCompatTests.halfTurn(helper, hall, angle -> {
			int planks = itemsOnTheGround(helper, Items.OAK_PLANKS);
			int slimeItems = itemsOnTheGround(helper, Items.SLIME_BLOCK);
			String around = QaCreateBearingProtection1108eCompatTests.around(helper);
			helper.assertTrue(helper.getBlockState(landing).isAir(), "the stranger's bearing set its plank down inside: " + around);
			helper.assertTrue(planks == 1, "the plank the stranger's bearing couldn't set down inside the village should lie as 1 item; "
				+ planks + " oak planks lie on the ground (stopped at " + angle + " degrees): " + around);
			ItemEntity drop = helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.OAK_PLANKS)).get(0);
			double dist = Math.sqrt(drop.blockPosition().distSqr(helper.absolutePos(bearing)));
			helper.assertTrue(dist <= 3, "the dropped plank lies " + dist + " blocks from the bearing, at " + helper.relativePos(drop.blockPosition()));
			helper.assertTrue(slimeItems == 0 && helper.getBlockState(bearing.above()).is(Blocks.SLIME_BLOCK)
				&& helper.getBlockState(bearing.above().west()).is(Blocks.SLIME_BLOCK),
				"the stranger's bearing didn't set its slime blocks down outside where they stopped (" + slimeItems + " as items): " + around);
		});
	}

	// ---------------------------------------------------------------- 4. the mod's own workers in a protected village

	private static final BlockPos BIG_HALL = new BlockPos(1, 2, 26);
	private static final UUID OWNER = UUID.fromString("0a1e1122-0000-4e00-9a00-0000000000aa");

	/**
	 * A protected hall at {@link #BIG_HALL} with the server's own village size (its area covers the whole test area); a
	 * watchdog turns its protection off just before {@code timeout} so a failing test leaves no protected village behind.
	 */
	private static VillageHallBlockEntity bigHall(GameTestHelper helper, int timeout) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(BIG_HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(BIG_HALL);
		hall.setOwner(OWNER, "Ada");
		hall.setProtected(true);
		helper.assertTrue(VillageHalls.RADIUS >= 40, "set-up: the village size is " + VillageHalls.RADIUS + ", too small to cover the build");
		helper.onEachTick(() -> {
			if (helper.getTick() >= timeout - 5 && hall.isProtected()) {
				hall.setProtected(false);
			}
		});
		return hall;
	}

	private record Run(ServerLevel level, BuildSite site, BuildPlan plan, BlueprintData.Placement placement) {
	}

	/** A builder at a bench, the build at (9, 2, 3) and barrels holding exactly its materials (as CreateBuildsCompatTests). */
	private static Run startBuild(GameTestHelper helper, StarterBlueprints.Entry entry) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlockPos origin = new BlockPos(Math.min(9, 29 - entry.size().getX()), 2, Math.min(3, 29 - entry.size().getZ()));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + entry.id());
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 0);
			helper.setBlock(barrel, Blocks.BARREL);
			Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		helper.assertTrue(!VillageProtection.mayMachineChange(level, helper.absolutePos(BIG_HALL).offset(VillageHalls.RADIUS + 5, 0, 0),
			placement.origin()), "set-up: the build site isn't in the protected village");
		return new Run(level, site, plan, placement);
	}

	private static boolean isCreate(BlockState state) {
		return "create".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace());
	}

	/**
	 * A Builder puts up the Windmill (its bearing assembles a contraption) inside a protected village, from barrels with
	 * exactly its materials: done, nothing skipped, every block right (the started sails excepted, as in
	 * CreateBuildsCompatTests), the bearing standing and the millstone turning.
	 */
	//$ gametest_ticks_batch AREA '30000' '"qa_e1122_protected_windmill"'
	@GameTest(template = AREA, timeoutTicks = 30000, batch = "qa_e1122_protected_windmill")
	public void aBuilderBuildsTheWindmillInsideAProtectedVillage(GameTestHelper helper) {
		VillageHallBlockEntity hall = bigHall(helper, 30000);
		Run run = startBuild(helper, StarterBlueprints.WINDMILL);
		ServerLevel level = run.level();
		BlockPos origin = run.placement().origin();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(run.site().id()) == null,
				"still building in the protected village: " + run.site().stage() + " " + run.site().status() + " missing " + run.site().missing());
			List<BlockPos> unfinished = run.plan().unfinished(level).stream()
				.filter(p -> !(p.subtract(origin).getZ() == 0 && p.subtract(origin).getY() >= 5)).toList();
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " blocks wrong in the protected village, e.g. " + unfinished.stream().limit(12)
				.map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(run.site().skipped() == 0, run.site().skipped() + " steps skipped in the protected village");
			BlockState bearing = level.getBlockState(origin.offset(7, 11, 1));
			helper.assertTrue(BuiltInRegistries.BLOCK.getKey(bearing.getBlock()).toString().equals("create:windmill_bearing"),
				"no windmill bearing at 7 11 1: " + bearing);
			BlockEntity mill = level.getBlockEntity(origin.offset(9, 1, 7));
			float speed = mill == null ? 0 : io.github.jcondedata.aliveworkplace.mc.Nbt.getFloat(mill.saveWithoutMetadata(level.registryAccess()), "Speed");
			helper.assertTrue(Math.abs(speed) > 0, "the Windmill's millstone doesn't turn (Speed " + speed + ")");
			hall.setProtected(false);
		});
	}

	/** The Crushing Mill's belt, in template coordinates (as QaBeltMendRebuilt1121eCompatTests). */
	private static final List<BlockPos> MILL_BELT = List.of(new BlockPos(6, 3, 6), new BlockPos(6, 3, 7), new BlockPos(6, 3, 8));

	private static boolean isBelt(BlockState state) {
		return "create:belt".equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
	}

	/**
	 * In a protected village a Builder puts up the Crushing Mill with every Create block in place; a player breaks its belt
	 * and mines the pulley shafts; the village's Mechanic, with shafts and belt connectors in his store, lays it again
	 * where the blueprint draws it (as QaBeltMendRebuilt1121eCompatTests, with the village protected).
	 */
	//$ gametest_ticks_batch AREA '40000' '"qa_e1122_protected_belt_mend"'
	@GameTest(template = AREA, timeoutTicks = 40000, batch = "qa_e1122_protected_belt_mend")
	public void theMechanicMendsABeltInAProtectedVillage(GameTestHelper helper) {
		VillageHallBlockEntity hall = bigHall(helper, 40000);
		Run run = startBuild(helper, StarterBlueprints.CRUSHING_MILL);
		ServerLevel level = run.level();
		BlockPos origin = run.placement().origin();
		List<BlockPos> segments = MILL_BELT.stream().map(origin::offset).toList();
		BlockPos toolbox = new BlockPos(2, 2, 10);
		BlockPos store = new BlockPos(2, 2, 12);
		Item connector = MechanicCompatTests.createItem("belt_connector");
		Item shaft = MechanicCompatTests.createItem("shaft");
		long[] doneAt = {-1};
		long[] readyAt = {-1};
		Villager[] mechanic = new Villager[1];
		AABB around = new AABB(origin).inflate(24);
		helper.onEachTick(() -> {
			if (readyAt[0] >= 0) {
				return;
			}
			long now = level.getGameTime();
			if (BuildSiteManager.get(level).get(run.site().id()) != null) {
				return;
			}
			if (doneAt[0] < 0) {
				doneAt[0] = now;
				// built in the protected village: every block right, every Create block among them, nothing skipped
				List<BlockPos> unfinished = run.plan().unfinished(level);
				List<String> create = new ArrayList<>();
				for (BlockPos p : unfinished) {
					create.add(p.subtract(origin).toShortString() + "=" + level.getBlockState(p));
				}
				if (!unfinished.isEmpty() || run.site().skipped() != 0) {
					hall.setProtected(false);
					throw new GameTestAssertException("the Crushing Mill built in the protected village has " + unfinished.size() + " blocks wrong ("
						+ create.stream().limit(12).toList() + ") and " + run.site().skipped() + " skipped");
				}
				for (BlockPos p : segments) {
					if (!isBelt(level.getBlockState(p))) {
						hall.setProtected(false);
						throw new GameTestAssertException("as built in the protected village there's no belt at " + p.subtract(origin).toShortString());
					}
				}
				return;
			}
			if (now < doneAt[0] + 100) {
				return;
			}
			// the builder's barrels emptied; a player breaks the belt and mines its pulley shafts
			for (BlockPos p : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 1, 0)), helper.absolutePos(new BlockPos(7, 8, 0)))) {
				if (level.getBlockEntity(p) instanceof Container c) {
					c.clearContent();
				}
			}
			level.destroyBlock(segments.get(1), false);
			for (BlockPos p : segments) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
			for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, around)) {
				drop.discard();
			}
			helper.setBlock(store, Blocks.CHEST);
			Container chest = helper.getBlockEntity(store);
			chest.setItem(0, new ItemStack(connector, 2));
			chest.setItem(1, new ItemStack(shaft, 8));
			helper.setBlock(toolbox, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
			mechanic[0] = helper.spawn(EntityType.VILLAGER, toolbox.offset(1, 0, 1));
			Stations.assign(level, mechanic[0], helper.absolutePos(toolbox), ModVillagers.MECHANIC);
			helper.setDayTime(2000);
			readyAt[0] = now;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(readyAt[0] >= 0, "still building in the protected village: " + run.site().stage() + " " + run.site().status()
				+ " missing " + run.site().missing());
			helper.assertTrue(MechanicWork.isMechanic(mechanic[0]), "no Mechanic at the toolbox");
			List<String> wrong = new ArrayList<>();
			for (BlockPos p : segments) {
				if (!isBelt(level.getBlockState(p))) {
					wrong.add(p.subtract(origin).toShortString() + "=" + level.getBlockState(p));
				}
			}
			helper.assertTrue(wrong.isEmpty(), "the Mechanic hasn't mended the belt in the protected village: " + wrong + "; the store holds "
				+ count((Container) helper.getBlockEntity(store), connector) + " belt connectors");
			helper.assertTrue(count((Container) helper.getBlockEntity(store), connector) == 1, "the mend took "
				+ (2 - count((Container) helper.getBlockEntity(store), connector)) + " belt connectors, not one");
			hall.setProtected(false);
		});
	}
}

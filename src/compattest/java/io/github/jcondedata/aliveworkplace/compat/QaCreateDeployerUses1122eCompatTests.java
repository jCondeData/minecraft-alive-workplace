package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.deployer;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.held;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.speed;

import com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

/**
 * QA (lane-e-1122-1613, Create and the pack): a stranger's Create Deployer at the edge of a protected village doing
 * things other than placing a block (placing is covered by QaCreateMachinesProtection1102 and QaCreateMoreMachines
 * Protection1107e), tested from VillageProtection's promise: inside a protected village "only the hall's owner, their
 * friends and operators may break or place blocks, open chests and other blocks, empty buckets, or hurt the villagers,
 * golems, animals, armor stands and item frames", and (StandIns) a machine acts as the player who placed it, so a
 * stranger's machine is a stranger.
 *
 * <p>Frank's hall stands at {@link #HALL} with the village shrunk to {@link #REACH} columns, so on the hall's row
 * (z = 15) x = 11 is the village's last column and x = 12 is outside. The stranger's deployer stands at x = 13 facing
 * west (outside) and acts two blocks ahead, on {@link #TARGET} (x = 11, inside). Every case has a control: the same
 * deployer with the village open (protection off) really punches, breaks, pours, scoops, lights or strips. The guard
 * against over-protection: Frank's own deployer inside his protected village still hurts and strips.
 */
public class QaCreateDeployerUses1122eCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	static final BlockPos HALL = new BlockPos(3, 2, 15);
	static final int REACH = 8;
	/** The stranger's deployer, outside, facing west; it acts on {@link #TARGET}, the village's last column. */
	static final BlockPos DEPLOYER = new BlockPos(13, 2, 15);
	static final BlockPos TARGET = DEPLOYER.west(2);
	/** Frank's own deployer, inside his village, acting on {@link #OWN_TARGET}. */
	static final BlockPos OWN_DEPLOYER = new BlockPos(9, 2, 15);
	static final BlockPos OWN_TARGET = OWN_DEPLOYER.west(2);
	static final UUID FRANK = UUID.fromString("f4a11122-0000-4e00-9a00-0000000e1613");
	static final UUID STRANGER = UUID.fromString("5a0e1122-0000-4e00-9a00-0000000e1613");

	private static final Map<GameTestHelper, Runnable> UNDO = new ConcurrentHashMap<>();

	/** Frank's hall at {@link #HALL}, the village {@link #REACH} columns wide, protected or open; {@link #undo} puts it back. */
	static VillageHallBlockEntity hall(GameTestHelper helper, boolean protect) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		int radius = VillageHalls.RADIUS;
		boolean enabled = VillageProtection.ENABLED;
		VillageHalls.RADIUS = REACH;
		VillageProtection.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity hall = helper.getBlockEntity(HALL);
		hall.setOwner(FRANK, "Frank");
		hall.setProtected(protect);
		UNDO.put(helper, () -> {
			hall.setProtected(false);
			VillageHalls.RADIUS = radius;
			VillageProtection.ENABLED = enabled;
		});
		return hall;
	}

	static void undo(GameTestHelper helper) {
		Runnable r = UNDO.remove(helper);
		if (r != null) {
			r.run();
		}
	}

	/** The deployer's saved mode ("PUNCH" or "USE"). */
	static String mode(GameTestHelper helper, BlockPos at) {
		BlockEntity be = helper.getLevel().getBlockEntity(helper.absolutePos(at));
		return be == null ? "none" : Nbt.getString(be.saveWithoutMetadata(helper.getLevel().registryAccess()), "Mode");
	}

	/** A deployer as {@link QaCreateDeployerOnOurThings1102eCompatTests#deployer}, switched to punch mode (a wrench's click). */
	static void puncher(GameTestHelper helper, BlockPos at, UUID owner, ItemStack held) {
		deployer(helper, at, owner, held);
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof DeployerBlockEntity be)) {
			throw new GameTestAssertException("set-up: no Create deployer block entity at " + at);
		}
		if (!"PUNCH".equalsIgnoreCase(mode(helper, at))) {
			be.changeMode();
		}
		if (!"PUNCH".equalsIgnoreCase(mode(helper, at))) {
			throw new GameTestAssertException("set-up: the deployer won't switch to punch mode: " + mode(helper, at));
		}
	}

	/** Where the deployer's fake player looks from and to, in the test's frame (for failure messages). */
	static String eye(GameTestHelper helper, BlockPos at) {
		if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof DeployerBlockEntity be) || be.getPlayer() == null) {
			return "no player";
		}
		net.minecraft.world.phys.Vec3 e = be.getPlayer().getEyePosition().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)));
		net.minecraft.world.phys.Vec3 v = be.getPlayer().getViewVector(1.0f);
		return String.format(java.util.Locale.ROOT, "eye %.2f,%.2f,%.2f looking %.2f,%.2f,%.2f", e.x, e.y, e.z, v.x, v.y, v.z);
	}

	/** A mob that stays where it is put (no AI), at {@code at}. */
	static <T extends Mob> T still(GameTestHelper helper, EntityType<T> type, BlockPos at) {
		T mob = helper.spawn(type, at);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		return mob;
	}

	/** Runs {@code check} {@code ticks} from now, after putting the village back. */
	static void thenCheck(GameTestHelper helper, int ticks, Runnable check) {
		helper.runAfterDelay(ticks, () -> {
			undo(helper);
			check.run();
			helper.succeed();
		});
	}

	// ---------------------------------------------------------------- 1. punch mode with a sword: villagers and animals

	private static void swordAt(GameTestHelper helper, boolean protect, EntityType<? extends Mob> type, boolean expectHurt) {
		hall(helper, protect);
		Mob mob = still(helper, type, TARGET);
		float max = mob.getMaxHealth();
		puncher(helper, DEPLOYER, STRANGER, new ItemStack(Items.IRON_SWORD));
		thenCheck(helper, 300, () -> {
			float turning = speed(helper, DEPLOYER);
			boolean hurt = !mob.isAlive() || mob.getHealth() < max;
			String what = type.getDescription().getString();
			helper.assertTrue(turning != 0, "the deployer never turned, so the test proves nothing");
			if (expectHurt) {
				helper.assertTrue(hurt, "the control: the stranger's punching deployer with an iron sword didn't hurt the " + what + " in the open village"
					+ " (health " + mob.getHealth() + "/" + max + ", mode " + mode(helper, DEPLOYER) + ", holding " + held(helper, DEPLOYER) + ")");
			} else {
				helper.assertTrue(!hurt, "a stranger's Deployer in punch mode with an iron sword, outside Frank's protected village, hurt the " + what
					+ " standing in its last column: health " + mob.getHealth() + "/" + max + (mob.isAlive() ? "" : " (dead)"));
			}
		});
	}

	/** A stranger's punching deployer with a sword outside a protected village doesn't hurt the villager at its edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_sword_villager_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_sword_villager_protected")
	public void aStrangersPunchingDeployerDoesntHurtAProtectedVillager(GameTestHelper helper) {
		swordAt(helper, true, EntityType.VILLAGER, false);
	}

	/** The control: with the village open the same deployer hurts the villager. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_sword_villager_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_sword_villager_open")
	public void aStrangersPunchingDeployerHurtsAVillagerInAnOpenVillage(GameTestHelper helper) {
		swordAt(helper, false, EntityType.VILLAGER, true);
	}

	/** A stranger's punching deployer with a sword outside a protected village doesn't hurt the cow at its edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_sword_cow_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_sword_cow_protected")
	public void aStrangersPunchingDeployerDoesntHurtAProtectedCow(GameTestHelper helper) {
		swordAt(helper, true, EntityType.COW, false);
	}

	/** The control: with the village open the same deployer hurts the cow. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_sword_cow_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_sword_cow_open")
	public void aStrangersPunchingDeployerHurtsACowInAnOpenVillage(GameTestHelper helper) {
		swordAt(helper, false, EntityType.COW, true);
	}

	// ---------------------------------------------------------------- 2. punch mode with a pickaxe, at a block

	/** (A deployer punching bare-handed breaks nothing at all, Create's DeployerHandler returns on an empty hand, so a pickaxe.) */
	private static void punchStone(GameTestHelper helper, boolean protect) {
		hall(helper, protect);
		helper.setBlock(TARGET, Blocks.STONE);
		puncher(helper, DEPLOYER, STRANGER, new ItemStack(Items.IRON_PICKAXE));
		thenCheck(helper, 300, () -> {
			float turning = speed(helper, DEPLOYER);
			boolean kept = helper.getBlockState(TARGET).is(Blocks.STONE);
			helper.assertTrue(turning != 0, "the deployer never turned, so the test proves nothing");
			if (protect) {
				helper.assertTrue(kept, "a stranger's Deployer in punch mode with an iron pickaxe, outside Frank's protected village, broke the stone in its"
					+ " last column: now " + helper.getBlockState(TARGET));
			} else {
				helper.assertTrue(!kept, "the control: the stranger's punching deployer didn't break the stone in the open village in 300 ticks (mode "
					+ mode(helper, DEPLOYER) + ", holding " + held(helper, DEPLOYER) + ")");
			}
		});
	}

	/** A stranger's punching deployer with a pickaxe outside a protected village leaves the stone at its edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_punch_stone_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_punch_stone_protected")
	public void aStrangersPunchingDeployerBreaksNothingInAProtectedVillage(GameTestHelper helper) {
		punchStone(helper, true);
	}

	/** The control: with the village open the same deployer mines the stone away. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_punch_stone_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_punch_stone_open")
	public void aStrangersPunchingDeployerBreaksStoneInAnOpenVillage(GameTestHelper helper) {
		punchStone(helper, false);
	}

	// ---------------------------------------------------------------- 3. buckets

	/**
	 * A bucket aims with the deployer's fake player's own look, not Create's ray: from its eye, which in these tests sits
	 * 1.62 above the hand (y = 4.12 here, Create's "eyes on his feet" not applying), looking west. So each bucket case
	 * has its target at the hand's height (y = 2) and at the eye's (y = 4), and either counts.
	 */
	static final BlockPos EYE_TARGET = TARGET.above(2);

	/** Where a water source stands inside the village near the target (x 4..11, y 2..5, z 13..17), or "". */
	private static String sourcesInside(GameTestHelper helper) {
		StringBuilder at = new StringBuilder();
		for (int x = HALL.getX() + 1; x <= TARGET.getX(); x++) {
			for (int y = 2; y <= 5; y++) {
				for (int z = TARGET.getZ() - 2; z <= TARGET.getZ() + 2; z++) {
					BlockPos p = new BlockPos(x, y, z);
					if (helper.getLevel().getFluidState(helper.absolutePos(p)).isSourceOfType(Fluids.WATER)) {
						at.append(' ').append(x).append(',').append(y).append(',').append(z);
					}
				}
			}
		}
		return at.toString();
	}

	static void pour(GameTestHelper helper, boolean protect) {
		hall(helper, protect);
		// a wall just behind the village's last column, at the hand's and the eye's height: the bucket pours against it
		for (int y = TARGET.getY(); y <= EYE_TARGET.getY(); y++) {
			helper.setBlock(new BlockPos(TARGET.getX() - 1, y, TARGET.getZ()), Blocks.STONE);
		}
		puncherless(helper, new ItemStack(Items.WATER_BUCKET));
		AtomicReference<String> seen = new AtomicReference<>("");
		AtomicBoolean emptied = new AtomicBoolean(false);
		helper.onEachTick(() -> {
			String inside = sourcesInside(helper);
			if (!inside.isEmpty() && seen.get().isEmpty()) {
				seen.set(inside + " (tick " + helper.getTick() + ")");
			}
			if (held(helper, DEPLOYER).is(Items.BUCKET)) {
				emptied.set(true);
			}
		});
		thenCheck(helper, 300, () -> {
			float turning = speed(helper, DEPLOYER);
			helper.assertTrue(turning != 0, "the deployer never turned, so the test proves nothing");
			if (protect) {
				helper.assertTrue(seen.get().isEmpty(), "a stranger's Deployer with a water bucket, outside Frank's protected village, poured water inside it:"
					+ " a source at x,y,z" + seen.get() + " (bucket emptied: " + emptied.get() + ")");
			} else {
				helper.assertTrue(!seen.get().isEmpty(), "the control: the stranger's deployer poured no water into the open village (bucket emptied: "
					+ emptied.get() + ", holding " + held(helper, DEPLOYER) + ", at the target " + helper.getBlockState(TARGET) + "; " + eye(helper, DEPLOYER) + ")");
			}
		});
	}

	/** The stranger's deployer in its default (use) mode holding {@code held}. */
	private static void puncherless(GameTestHelper helper, ItemStack stack) {
		deployer(helper, DEPLOYER, STRANGER, stack);
		if (!"USE".equalsIgnoreCase(mode(helper, DEPLOYER))) {
			throw new GameTestAssertException("set-up: a new deployer isn't in use mode: " + mode(helper, DEPLOYER));
		}
	}


	/** The control: with the village open the same deployer pours a water source into its last column. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_pour_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_pour_open")
	public void aStrangersDeployerPoursWaterIntoAnOpenVillage(GameTestHelper helper) {
		pour(helper, false);
	}

	static void scoop(GameTestHelper helper, boolean protect) {
		hall(helper, protect);
		// water sources in the village's last column at the hand's and the eye's height, each walled in on every side but
		// the east one, which faces the deployer
		for (BlockPos source : new BlockPos[] {TARGET, EYE_TARGET}) {
			helper.setBlock(source.west(), Blocks.GLASS);
			helper.setBlock(source.north(), Blocks.GLASS);
			helper.setBlock(source.south(), Blocks.GLASS);
			helper.setBlock(source.above(), Blocks.GLASS);
		}
		helper.setBlock(TARGET, Blocks.WATER);
		helper.setBlock(EYE_TARGET, Blocks.WATER);
		puncherless(helper, new ItemStack(Items.BUCKET));
		AtomicReference<String> lost = new AtomicReference<>("");
		AtomicBoolean filled = new AtomicBoolean(false);
		helper.onEachTick(() -> {
			for (BlockPos source : new BlockPos[] {TARGET, EYE_TARGET}) {
				if (!helper.getLevel().getFluidState(helper.absolutePos(source)).isSourceOfType(Fluids.WATER) && lost.get().isEmpty()) {
					lost.set(helper.getBlockState(source) + " at " + source.toShortString() + ", tick " + helper.getTick());
				}
			}
			if (held(helper, DEPLOYER).is(Items.WATER_BUCKET)) {
				filled.set(true);
			}
		});
		thenCheck(helper, 300, () -> {
			float turning = speed(helper, DEPLOYER);
			helper.assertTrue(turning != 0, "the deployer never turned, so the test proves nothing");
			if (protect) {
				helper.assertTrue(lost.get().isEmpty() && !filled.get(), "a stranger's Deployer with an empty bucket, outside Frank's protected village,"
					+ " scooped the water source in its last column: the source became " + (lost.get().isEmpty() ? "-" : lost.get())
					+ ", the bucket filled: " + filled.get());
			} else {
				helper.assertTrue(filled.get(), "the control: the stranger's deployer didn't scoop the water in the open village (the source: "
					+ (lost.get().isEmpty() ? "never gone" : lost.get()) + ", holding " + held(helper, DEPLOYER) + "; " + eye(helper, DEPLOYER) + ")");
			}
		});
	}


	/** The control: with the village open the same deployer fills its bucket from the source. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_scoop_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_scoop_open")
	public void aStrangersDeployerScoopsWaterFromAnOpenVillage(GameTestHelper helper) {
		scoop(helper, false);
	}

	// ---------------------------------------------------------------- 4. flint and steel

	/** Where fire stands inside the village around the target (x 4..11, y 2..4, z 14..16), or "". */
	private static String fireInside(GameTestHelper helper) {
		StringBuilder at = new StringBuilder();
		for (int x = HALL.getX() + 1; x <= TARGET.getX(); x++) {
			for (int y = 2; y <= 4; y++) {
				for (int z = TARGET.getZ() - 1; z <= TARGET.getZ() + 1; z++) {
					if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.FIRE)) {
						at.append(' ').append(x).append(',').append(y).append(',').append(z);
					}
				}
			}
		}
		return at.toString();
	}

	private static void light(GameTestHelper helper, boolean protect) {
		hall(helper, protect);
		// netherrack burns forever and a village takes no notice of it (so nobody puts it out)
		helper.setBlock(TARGET, Blocks.NETHERRACK);
		puncherless(helper, new ItemStack(Items.FLINT_AND_STEEL));
		AtomicReference<String> seen = new AtomicReference<>("");
		helper.onEachTick(() -> {
			String fire = fireInside(helper);
			if (!fire.isEmpty() && seen.get().isEmpty()) {
				seen.set(fire + " (tick " + helper.getTick() + ")");
			}
		});
		thenCheck(helper, 300, () -> {
			float turning = speed(helper, DEPLOYER);
			helper.assertTrue(turning != 0, "the deployer never turned, so the test proves nothing");
			if (protect) {
				helper.assertTrue(seen.get().isEmpty(), "a stranger's Deployer with flint and steel, outside Frank's protected village, lit a fire inside it"
					+ " at x,y,z" + seen.get());
			} else {
				helper.assertTrue(!seen.get().isEmpty(), "the control: the stranger's deployer lit no fire in the open village (above the netherrack: "
					+ helper.getBlockState(TARGET.above()) + ", just outside: " + helper.getBlockState(TARGET.east()) + ", holding " + held(helper, DEPLOYER) + ")");
			}
		});
	}

	/** A stranger's deployer with flint and steel outside a protected village lights no fire inside it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_fire_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_fire_protected")
	public void aStrangersDeployerLightsNoFireInAProtectedVillage(GameTestHelper helper) {
		light(helper, true);
	}

	/** The control: with the village open the same deployer sets the netherrack at its edge alight. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_fire_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_fire_open")
	public void aStrangersDeployerLightsAFireInAnOpenVillage(GameTestHelper helper) {
		light(helper, false);
	}

	// ---------------------------------------------------------------- 5. an axe on a log

	private static void strip(GameTestHelper helper, boolean protect) {
		hall(helper, protect);
		helper.setBlock(TARGET, Blocks.OAK_LOG);
		puncherless(helper, new ItemStack(Items.IRON_AXE));
		thenCheck(helper, 300, () -> {
			float turning = speed(helper, DEPLOYER);
			helper.assertTrue(turning != 0, "the deployer never turned, so the test proves nothing");
			if (protect) {
				helper.assertTrue(helper.getBlockState(TARGET).is(Blocks.OAK_LOG), "a stranger's Deployer with an iron axe, outside Frank's protected village,"
					+ " changed the oak log in its last column: now " + helper.getBlockState(TARGET));
			} else {
				helper.assertTrue(helper.getBlockState(TARGET).is(Blocks.STRIPPED_OAK_LOG), "the control: the stranger's deployer didn't strip the log in the"
					+ " open village: " + helper.getBlockState(TARGET) + " (holding " + held(helper, DEPLOYER) + ")");
			}
		});
	}

	/** A stranger's deployer with an axe outside a protected village doesn't strip the log at its edge. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_strip_protected"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_strip_protected")
	public void aStrangersDeployerStripsNoLogInAProtectedVillage(GameTestHelper helper) {
		strip(helper, true);
	}

	/** The control: with the village open the same deployer strips the log. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_strip_open"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_strip_open")
	public void aStrangersDeployerStripsALogInAnOpenVillage(GameTestHelper helper) {
		strip(helper, false);
	}

	// ---------------------------------------------------------------- 6. the owner's own deployer still works

	/** Frank's own punching deployer with a sword, inside his protected village, still hurts the cow in front of it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_owner_sword"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_owner_sword")
	public void theOwnersPunchingDeployerStillHurtsInHisProtectedVillage(GameTestHelper helper) {
		hall(helper, true);
		Mob cow = still(helper, EntityType.COW, OWN_TARGET);
		float max = cow.getMaxHealth();
		puncher(helper, OWN_DEPLOYER, FRANK, new ItemStack(Items.IRON_SWORD));
		thenCheck(helper, 300, () -> {
			float turning = speed(helper, OWN_DEPLOYER);
			helper.assertTrue(turning != 0, "the deployer never turned, so the test proves nothing");
			helper.assertTrue(!cow.isAlive() || cow.getHealth() < max, "Frank's own punching deployer with a sword, inside his protected village, didn't"
				+ " hurt the cow in front of it (health " + cow.getHealth() + "/" + max + ", mode " + mode(helper, OWN_DEPLOYER) + ")");
		});
	}

	/** Frank's own deployer with an axe, inside his protected village, still strips the log in front of it. */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_duse_owner_axe"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_duse_owner_axe")
	public void theOwnersDeployerStillStripsInHisProtectedVillage(GameTestHelper helper) {
		hall(helper, true);
		helper.setBlock(OWN_TARGET, Blocks.OAK_LOG);
		deployer(helper, OWN_DEPLOYER, FRANK, new ItemStack(Items.IRON_AXE));
		thenCheck(helper, 300, () -> {
			float turning = speed(helper, OWN_DEPLOYER);
			helper.assertTrue(turning != 0, "the deployer never turned, so the test proves nothing");
			helper.assertTrue(helper.getBlockState(OWN_TARGET).is(Blocks.STRIPPED_OAK_LOG), "Frank's own deployer with an axe, inside his protected village,"
				+ " didn't strip the log in front of it: " + helper.getBlockState(OWN_TARGET) + " (holding " + held(helper, OWN_DEPLOYER) + ")");
		});
	}
}

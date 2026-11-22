package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.GuardCombat;
import io.github.jcondedata.aliveworkplace.guard.GuardRally;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane D (2026-11-22, lane-d-1122-2208): closes the mutation gaps lane-d-1122-1909 left in the guards' combat (3 of 11
 * mutants survived: the old 4-block rule and the fight/rally rules). Written from raids.md: "any other monster, and anything
 * outside a raid, he still has to see (or have within 4 blocks)", and from the rally: a rung bell sends the guards to a
 * rally at the bell, where they stay (GuardRally.RALLY_TICKS), and a guard in a fight fights rather than patrols.
 *
 * <p>No raid in any of these tests. Guard posts are well inside the huge area (a guard over its edge stands in an unticked
 * chunk and freezes).
 */
public class QaGuardCombatMutants1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos POST = new BlockPos(14, 2, 14);
	private static final BlockPos STAND = new BlockPos(13, 2, 13);

	private static Villager guard(GameTestHelper helper, BlockPos post, BlockPos stand) {
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, stand);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		Leftovers.after(helper, v::discard);
		return v;
	}

	private static void time(GameTestHelper helper, long dayTime) {
		ServerLevel level = helper.getLevel();
		long was = level.getDayTime();
		helper.setDayTime((int) dayTime);
		Leftovers.after(helper, () -> level.setDayTime(was));
	}

	/** A stone cell round {@code at} (walls and a roof round a space two tall): nobody outside sees into it. */
	private static void cell(GameTestHelper helper, BlockPos at, boolean on) {
		for (BlockPos p : BlockPos.betweenClosed(at.offset(-1, 0, -1), at.offset(1, 2, 1))) {
			if (p.getX() != at.getX() || p.getZ() != at.getZ() || p.getY() == at.getY() + 2) {
				helper.setBlock(p, on ? Blocks.STONE : Blocks.AIR);
			}
		}
	}

	/** A still zombie at exactly {@code (x, y, z)} relative to the test's origin. */
	private static Mob zombie(GameTestHelper helper, double x, double y, double z) {
		ServerLevel level = helper.getLevel();
		Vec3 pos = helper.absoluteVec(new Vec3(x, y, z));
		Mob mob = EntityType.ZOMBIE.create(level);
		mob.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		mob.setSilent(true);
		level.addFreshEntity(mob);
		Leftovers.after(helper, mob::discard);
		return mob;
	}

	/** The guard's own foe search (findFoe), called right now: what he would take for his foe from where he stands. */
	private static LivingEntity wouldPick(GameTestHelper helper, Villager guard) {
		try {
			java.lang.reflect.Method m = GuardCombat.class.getDeclaredMethod("findFoe", ServerLevel.class, Villager.class);
			m.setAccessible(true);
			return (LivingEntity) m.invoke(null, helper.getLevel(), guard);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String state(GameTestHelper helper, Villager guard) {
		LivingEntity foe = GuardCombat.foe(guard);
		return "guard at " + helper.relativeVec(guard.position()) + ", fighting " + GuardCombat.isFighting(guard) + ", rallying "
			+ GuardRally.isRallying(guard) + ", foe " + (foe == null ? "none" : foe.getType().toShortString() + " at " + helper.relativeVec(foe.position()))
			+ ", doing " + guard.getBrain().getRunningBehaviors().stream().map(b -> b.debugString()).toList();
	}

	// ---- "or have within 4 blocks" ----

	/**
	 * No raid, night: a zombie shut in a stone cell two blocks from the guard (he can't see it) is right beside him, so he
	 * takes it for his foe at once (raids.md: monsters he has to see, "or have within 4 blocks").
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuardCombatMutants1122dBeside"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardCombatMutants1122dBeside")
	public void qaOutsideARaidAHiddenZombieRightBesideTheGuardIsHisFoe(GameTestHelper helper) {
		Leftovers.clear(helper);
		time(helper, 14000);
		BlockPos at = STAND.offset(0, 0, -2);
		cell(helper, at, true);
		Mob zombie = zombie(helper, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Villager guard = guard(helper, POST, STAND);
		helper.onEachTick(() -> {
			helper.assertTrue(zombie.isAlive() && guard.isAlive(), "(setup) the zombie or the guard is gone");
			helper.assertFalse(guard.hasLineOfSight(zombie) && helper.getTick() < 5, "(setup) the guard sees the zombie through its cell");
			if (GuardCombat.foe(guard) == zombie) {
				helper.succeed();
			} else if (helper.getTick() > 120) {
				helper.fail("a hidden zombie " + String.format("%.1f", Math.sqrt(guard.distanceToSqr(zombie))) + " blocks from the guard (within 4) isn't his foe after 120 ticks: "
					+ state(helper, guard));
			}
		});
	}

	/**
	 * The edge of "within 4 blocks", checked with the guard held still (no AI) and his own foe search: hidden zombies 3.0
	 * and 3.9 blocks off are foes; 4.1 and 4.6 off are not (he can't see them); 4.6 off with the cell gone (in sight) is.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGuardCombatMutants1122dEdge"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGuardCombatMutants1122dEdge")
	public void qaTheRightBesideRuleReachesFourBlocksAndNoFurther(GameTestHelper helper) {
		Leftovers.clear(helper);
		time(helper, 6000);
		Villager guard = guard(helper, POST, STAND);
		guard.setNoAi(true);
		BlockPos at = STAND.offset(0, 0, -4); // the cell's hole: z from 9.3 to 9.7 relative; the guard stands at z 13.5
		cell(helper, at, true);
		double gx = STAND.getX() + 0.5;
		double gz = STAND.getZ() + 0.5;
		Mob zombie = zombie(helper, gx, at.getY(), at.getZ() + 0.5);
		double[][] cases = {{3.0, 1}, {3.9, 1}, {4.1, 0}, {4.6, 0}};
		StringBuilder wrong = new StringBuilder();
		helper.runAtTickTime(5, () -> {
			for (double[] c : cases) {
				// The guard moved instead of the zombie (the zombie's hole is one block): he stays outside the cell.
				Vec3 g = helper.absoluteVec(new Vec3(gx, STAND.getY(), at.getZ() + 0.5 + c[0]));
				guard.teleportTo(g.x, g.y, g.z);
				helper.assertFalse(guard.hasLineOfSight(zombie), "(setup) the guard sees the zombie in its cell from " + c[0] + " off");
				LivingEntity foe = wouldPick(helper, guard);
				boolean picked = foe == zombie;
				if (picked != (c[1] == 1)) {
					wrong.append(String.format(" a hidden zombie %.1f blocks off (%.2f): %s;", c[0], Math.sqrt(guard.distanceToSqr(zombie)),
						picked ? "his foe, though beyond 4 blocks and out of sight" : "no foe, though within 4 blocks"));
				}
			}
			helper.assertTrue(wrong.length() == 0, "the 4-block rule:" + wrong);
			cell(helper, at, false);
		});
		helper.runAtTickTime(8, () -> {
			helper.assertTrue(guard.hasLineOfSight(zombie), "(setup) the guard doesn't see the zombie with its cell gone");
			helper.assertTrue(wouldPick(helper, guard) == zombie, "a zombie 4.6 off in plain sight isn't his foe (the setup can't tell)");
			helper.succeed();
		});
	}

	// ---- Fighting and rallying come before the patrol ----

	/**
	 * At night (a guard's shift: his patrol runs) a zombie stands still in plain sight 17 blocks from the Guard Post (inside guardRadius,
	 * far outside his patrol's square of 6), and can't be hurt, so the fight goes on: the guard goes to it, and once he is
	 * at it he stays at it (never more than 4 blocks off) for 400 ticks; his patrol waits while he fights.
	 */
	//$ gametest_ticks_batch AREA '900' '"qaGuardCombatMutants1122dFight"'
	@GameTest(template = AREA, timeoutTicks = 900, batch = "qaGuardCombatMutants1122dFight")
	public void qaAGuardInAFightStaysAtItInsteadOfPatrolling(GameTestHelper helper) {
		Leftovers.clear(helper);
		time(helper, 14000);
		Villager guard = guard(helper, new BlockPos(14, 2, 22), new BlockPos(13, 2, 21));
		Mob zombie = zombie(helper, 14.5, 2, 5.5);
		zombie.setInvulnerable(true);
		long[] at = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(guard.isAlive() && zombie.isAlive(), "(setup) the guard or the zombie is gone");
			double d = Math.sqrt(guard.distanceToSqr(zombie));
			if (at[0] < 0) {
				if (d <= 3) {
					at[0] = tick;
				} else if (tick > 400) {
					helper.fail("a zombie in plain sight 17 blocks from his post: 400 ticks on he hasn't come to it (" + String.format("%.1f", d) + " off): " + state(helper, guard));
				}
				return;
			}
			if (d > 4) {
				helper.fail((tick - at[0]) + " ticks after he came to the zombie he fights, the guard is " + String.format("%.1f", d) + " blocks from it: " + state(helper, guard));
			}
			if (tick >= at[0] + 400) {
				helper.succeed();
			}
		});
	}

	/**
	 * At night (his patrol running) a player rings a bell 12 blocks from the Guard Post: the guard comes to it (within 4 blocks) and stays at it,
	 * never more than 6 blocks off, for the next 600 ticks of the rally (it lasts 1800).
	 */
	//$ gametest_ticks_batch AREA '1400' '"qaGuardCombatMutants1122dRally"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaGuardCombatMutants1122dRally")
	public void qaAfterTheBellTheGuardStaysAtTheRally(GameTestHelper helper) {
		Leftovers.clear(helper);
		time(helper, 14000);
		// The post near the area's far side and the bell 12 blocks in, so neither he nor his patrol nears an edge.
		Villager guard = guard(helper, new BlockPos(14, 2, 21), new BlockPos(13, 2, 20));
		BlockPos bell = helper.absolutePos(new BlockPos(14, 2, 9));
		long[] rung = {-1};
		long[] there = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			helper.assertTrue(guard.isAlive(), "(setup) the guard died");
			if (rung[0] < 0) {
				if (tick == 20) {
					helper.getLevel().setBlockAndUpdate(bell, Blocks.BELL.defaultBlockState());
					helper.assertTrue(((BellBlock) Blocks.BELL).attemptToRing(null, helper.getLevel(), bell, Direction.NORTH), "(setup) the bell didn't ring");
					rung[0] = tick;
				}
				return;
			}
			double dx = guard.getX() - (bell.getX() + 0.5);
			double dz = guard.getZ() - (bell.getZ() + 0.5);
			double d = Math.sqrt(dx * dx + dz * dz);
			if (there[0] < 0) {
				if (d <= 4) {
					there[0] = tick;
				} else if (tick > rung[0] + 500) {
					helper.fail("500 ticks after the bell rang the guard hasn't come to it: " + String.format("%.1f", d) + " off, " + state(helper, guard));
				}
				return;
			}
			if (d > 6) {
				helper.fail((tick - there[0]) + " ticks after he came to the rung bell the guard is " + String.format("%.1f", d) + " blocks from it (the rally lasts "
					+ GuardRally.RALLY_TICKS + " ticks): " + state(helper, guard));
			}
			if (tick >= there[0] + 600) {
				helper.succeed();
			}
		});
	}
}

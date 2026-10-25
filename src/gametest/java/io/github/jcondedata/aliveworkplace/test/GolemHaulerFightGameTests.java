package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.legend.GolemSmith;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.HaulerGolems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * B329: a Hauler Golem (29.15) goes back to hauling after a fight. The legend_golem_smith scene once timed out with
 * its hauler standing still among the hall's villagers from the moment it killed a zombie. These cover the ways a
 * fight can end: the golem kills the zombie, the zombie vanishes mid-fight, the zombie dies of something else while
 * the golem carries a load, and a fight in a crowd of villagers who stand still.
 */
public class GolemHaulerFightGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos STOREHOUSE = new BlockPos(22, 2, 22);
	private static final BlockPos STORE_CHEST = new BlockPos(23, 2, 22);

	/** The Storehouse with its chest and a Drop Box at {@code boxPos} holding {@code stacks} stacks of cobblestone. */
	private static Container round(GameTestHelper helper, BlockPos boxPos, int stacks) {
		strayStorehouses(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(STORE_CHEST, Blocks.CHEST);
		helper.setBlock(boxPos, ModBlocks.DROP_BOX);
		Container box = helper.getBlockEntity(boxPos);
		for (int i = 0; i < stacks; i++) {
			box.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		return box;
	}

	/**
	 * Removes every Storehouse outside the test area that a hauler anywhere in it could take for the nearest
	 * ({@link HaulerGolems#STOREHOUSE_RANGE} from the golem). One an earlier batch built outside its own area is never
	 * cleared: the nightly repeats once had this test's hauler fill up at the Drop Box, then head for such a Storehouse
	 * 11 blocks off across the area's edge, which it can't reach, and stand there with its load (2026-10-24). The test's
	 * own Storehouse is the only one its hauler should know. Only for tests alone in their batch.
	 */
	private static void strayStorehouses(GameTestHelper helper) {
		net.minecraft.world.phys.AABB box = helper.getBounds();
		int reach = HaulerGolems.STOREHOUSE_RANGE + (int) Math.ceil(Math.hypot(box.getXsize(), box.getZsize()) / 2) + 1;
		for (BlockPos stray : helper.getLevel().getPoiManager().findAll(h -> h.is(io.github.jcondedata.aliveworkplace.registry.ModVillagers.STOREHOUSE_POI),
			p -> !box.contains(net.minecraft.world.phys.Vec3.atCenterOf(p)), BlockPos.containing(box.getCenter()), reach,
			net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			helper.getLevel().removeBlock(stray, false);
		}
	}

	private static IronGolem hauler(GameTestHelper helper, BlockPos near) {
		IronGolem golem = GolemSmith.build(helper.getLevel(), helper.absolutePos(near), GolemSmith.Role.HAULER);
		helper.assertTrue(golem != null, "a hauler");
		Leftovers.after(helper, golem::discard);
		return golem;
	}

	/** A zombie in a helmet (no burning in the sun) at {@code at}, after the golem, and the golem after it. */
	private static Zombie zombie(GameTestHelper helper, BlockPos at, IronGolem golem) {
		Zombie zombie = helper.spawn(EntityType.ZOMBIE, at);
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		zombie.setTarget(golem);
		golem.setTarget(zombie);
		Leftovers.after(helper, zombie::discard);
		return zombie;
	}

	/** The golem's running goals, for a failure message. */
	private static String running(IronGolem golem) {
		return ((io.github.jcondedata.aliveworkplace.mixin.MobAccessor) golem).aliveworkplace$goals().getAvailableGoals().stream()
			.filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning).map(w -> w.getGoal().getClass().getSimpleName()).toList().toString();
	}

	private static int carried(IronGolem golem) {
		return HaulerGolems.load(golem).values().stream().mapToInt(Integer::intValue).sum();
	}

	/** Done: the zombie is gone, every stack is in the Storehouse's chest and the golem carries nothing. */
	private static void hauledAfterTheFight(GameTestHelper helper, Zombie zombie, Container box, int stacks, IronGolem golem) {
		helper.succeedWhen(() -> {
			helper.assertTrue(!zombie.isAlive(), "the zombie still stands");
			Container store = helper.getBlockEntity(STORE_CHEST);
			helper.assertTrue(store.countItem(Items.COBBLESTONE) == stacks * 64, "the store has " + store.countItem(Items.COBBLESTONE) + " of "
				+ stacks * 64 + " cobblestone, the box " + box.countItem(Items.COBBLESTONE) + ", the golem carries " + carried(golem)
				+ " and targets " + golem.getTarget() + " at " + golem.position() + " running " + running(golem) + " path " + golem.getNavigation().getPath()
				+ " nav done " + golem.getNavigation().isDone() + " storehouse " + HaulerGolems.storehouse(helper.getLevel(), golem.blockPosition())
				+ " (the test's own is at " + helper.absolutePos(STOREHOUSE) + ")");
			helper.assertTrue(carried(golem) == 0, "the golem still carries " + carried(golem));
		});
	}

	/** The hauler kills a zombie that comes at it, then empties the Drop Box into the Storehouse's chest. */
	//$ gametest_ticks_batch AREA '1200' '"haulerFightKill"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "haulerFightKill")
	public void haulerHaulsAgainAfterKillingAZombie(GameTestHelper helper) {
		Leftovers.clear(helper);
		Container box = round(helper, new BlockPos(6, 2, 6), 9);
		IronGolem golem = hauler(helper, new BlockPos(14, 1, 14));
		Zombie zombie = zombie(helper, new BlockPos(16, 2, 14), golem);
		hauledAfterTheFight(helper, zombie, box, 9, golem);
	}

	/** The zombie the hauler is fighting vanishes (despawned, unloaded) mid-fight: the hauler goes back to work. */
	//$ gametest_ticks_batch AREA '1200' '"haulerFightGone"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "haulerFightGone")
	public void haulerHaulsAgainWhenItsFoeVanishes(GameTestHelper helper) {
		Leftovers.clear(helper);
		Container box = round(helper, new BlockPos(6, 2, 6), 9);
		IronGolem golem = hauler(helper, new BlockPos(14, 1, 14));
		Zombie zombie = zombie(helper, new BlockPos(18, 2, 14), golem);
		helper.runAfterDelay(15, zombie::discard);
		hauledAfterTheFight(helper, zombie, box, 9, golem);
	}

	/** Mid-carry, the hauler turns on a zombie, which dies of something else: it carries its load on and fetches the rest. */
	//$ gametest_ticks_batch AREA '1200' '"haulerFightCarry"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "haulerFightCarry")
	public void haulerCarriesOnWhenItsFoeDiesOfSomethingElse(GameTestHelper helper) {
		Leftovers.clear(helper);
		Container box = round(helper, new BlockPos(6, 2, 6), 3);
		IronGolem golem = hauler(helper, new BlockPos(14, 1, 14));
		ModAttachments.HAULER_LOAD.set(golem, Map.of(Items.COBBLESTONE, 9 * 64));
		Zombie zombie = zombie(helper, new BlockPos(10, 2, 14), golem);
		helper.runAfterDelay(15, () -> zombie.kill());
		hauledAfterTheFight(helper, zombie, box, 12, golem);
	}

	/**
	 * The scene's case: the hauler fights a zombie that went for a row of villagers who stand still (2 blocks apart, as
	 * round the scene's hall), on the far side of the row from the Drop Box; it gets out and hauls.
	 */
	//$ gametest_ticks_batch AREA '1200' '"haulerFightCrowd"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "haulerFightCrowd")
	public void haulerHaulsAgainAfterAFightInACrowd(GameTestHelper helper) {
		Leftovers.clear(helper);
		Container box = round(helper, new BlockPos(10, 2, 4), 9);
		List<Villager> crowd = new ArrayList<>();
		for (int x = 4; x <= 16; x += 2) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, 12));
			v.setNoAi(true);
			crowd.add(v);
		}
		Leftovers.after(helper, () -> crowd.forEach(Villager::discard));
		IronGolem golem = hauler(helper, new BlockPos(9, 1, 14));
		net.minecraft.world.phys.Vec3 at = helper.absoluteVec(new net.minecraft.world.phys.Vec3(9.5, 2, 13.2));
		golem.moveTo(at.x, at.y, at.z, 0f, 0f); // in among the row, as the scene's hauler stood
		Zombie zombie = zombie(helper, new BlockPos(9, 2, 15), golem);
		zombie.setTarget(crowd.get(3));
		hauledAfterTheFight(helper, zombie, box, 9, golem);
	}

	/**
	 * B329's squeeze is only for a held-up walk: a golem is pushable as vanilla's is until it has walked
	 * {@link io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal#STUCK_TICKS} without headway, then not, and
	 * pushable again once its role goal stops (as it does when a fight starts) without the golem having arrived.
	 */
	//$ gametest_ticks_batch '"aliveworkplace_test:build_area"' '200' '"haulerSqueezeEnds"'
	@GameTest(template = "aliveworkplace_test:build_area", timeoutTicks = 200, batch = "haulerSqueezeEnds")
	public void aHeldUpGolemSqueezesOnlyUntilItsWalkEnds(GameTestHelper helper) {
		Leftovers.clear(helper);
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(2, 2, 2));
		golem.setNoAi(true); // it never gets anywhere: every walk below is held up
		Leftovers.after(helper, golem::discard);
		BlockPos far = helper.absolutePos(new BlockPos(6, 2, 6));
		int walk = io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal.STUCK_TICKS + 10;
		boolean[] walking = {false};
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(golem.isPushable(), "a golem not held up on a walk isn't pushable");
			walking[0] = true;
		});
		helper.onEachTick(() -> {
			if (walking[0]) {
				io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal.walkTo(golem, far, 1.0);
			}
		});
		helper.runAfterDelay(2 + walk, () -> {
			walking[0] = false;
			helper.assertTrue(io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal.squeezing(golem), "a golem held up "
				+ walk + " ticks isn't squeezing");
			helper.assertFalse(golem.isPushable(), "a golem squeezing past is still pushable");
			new io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal(golem).stop();
			helper.assertFalse(io.github.jcondedata.aliveworkplace.legend.GolemRoleGoal.squeezing(golem), "a golem whose role goal "
				+ "stopped still squeezes");
			helper.assertTrue(golem.isPushable(), "a golem whose walk ended isn't pushable");
			helper.succeed();
		});
	}
}

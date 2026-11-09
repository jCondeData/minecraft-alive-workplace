package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderBag;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-e-1109-0722, saves), B1081's fix from its spec ("only the work they held is let go when they're bitten, as a
 * death lets it go"): a builder's bag is dropped once at the bite, not carried into the cured villager as well. The
 * zombie villager that leaves for good without dying is {@link QaCuredVillagerEdges1109e2BugTests}.
 */
public class QaCuredVillagerEdges1109e2GameTests implements FabricGameTest {
	private static final String AREA = QaCuredVillager1109eGameTests.AREA;

	/**
	 * A builder carrying 10 cobblestone in his bag is bitten: the bag is dropped at the bite (as at a death), and the
	 * cured builder doesn't have the 10 as well: 10 cobblestone in all, never 20.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaCured1109e2Bag"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaCured1109e2Bag")
	public void aBittenBuildersBagIsDroppedOnceNotCarriedIntoTheCuredOne(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager bob = QaCuredVillager1109eGameTests.farmer(helper, new BlockPos(2, 2, 2), "Bob");
		BuilderBag bag = BuilderBag.of(List.of(new ItemStack(Items.COBBLESTONE, 10)));
		ModAttachments.BUILDER_BAG.set(bob, bag);
		bite(helper, bob, zv -> {
			int inZombie = bagCount(ModAttachments.BUILDER_BAG.get(zv));
			cure(helper, zv, "Bob", cured -> {
				int onGround = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(3), i -> i.isAlive() && i.getItem().is(Items.COBBLESTONE))
					.stream().mapToInt(i -> i.getItem().getCount()).sum();
				int inCured = bagCount(ModAttachments.BUILDER_BAG.get(cured));
				helper.assertTrue(onGround + inCured == 10 && inZombie + onGround == 10,
					"a bitten builder's 10 cobblestone came out as " + onGround + " on the ground, " + inZombie + " in the zombie villager's bag and "
						+ inCured + " in the cured builder's bag");
				helper.succeed();
			});
		});
	}

	// ---- helpers ------------------------------------------------------------------------------------------------

	private static int bagCount(BuilderBag bag) {
		return bag == null ? 0 : bag.count(Items.COBBLESTONE);
	}

	/** {@code victim} killed by a zombie on Hard (vanilla turns every such villager); {@code then} gets the zombie villager. */
	static void bite(GameTestHelper helper, Villager victim, java.util.function.Consumer<ZombieVillager> then) {
		ServerLevel level = helper.getLevel();
		Difficulty was = level.getDifficulty();
		level.getServer().setDifficulty(Difficulty.HARD, true);
		Leftovers.after(helper, () -> level.getServer().setDifficulty(was, true));
		helper.runAfterDelay(2, () -> {
			Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(5, 2, 5));
			zombie.setNoAi(true);
			zombie.setCanPickUpLoot(false);
			victim.hurt(level.damageSources().mobAttack(zombie), 1000f);
			zombie.discard();
			List<ZombieVillager> turned = level.getEntitiesOfClass(ZombieVillager.class, helper.getBounds().inflate(2), z -> z.isAlive());
			helper.assertTrue(turned.size() == 1, "setup: the villager didn't become one zombie villager: " + turned.size());
			ZombieVillager zv = turned.get(0);
			zv.setNoAi(true);
			helper.runAfterDelay(2, () -> then.accept(zv));
		});
	}

	/** Cures {@code zv} as a golden apple does, the wait cut to a tick; {@code then} gets the villager named {@code name}. */
	static void cure(GameTestHelper helper, ZombieVillager zv, String name, java.util.function.Consumer<Villager> then) {
		ServerLevel level = helper.getLevel();
		try {
			var start = ZombieVillager.class.getDeclaredMethod("startConverting", UUID.class, int.class);
			start.setAccessible(true);
			start.invoke(zv, null, 1);
		} catch (ReflectiveOperationException e) {
			throw new GameTestAssertException("can't start the cure: " + e);
		}
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(zv.isRemoved(), "setup: the zombie villager wasn't cured");
			List<Villager> back = level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(2),
				v -> v.isAlive() && v.hasCustomName() && v.getCustomName().getString().equals(name));
			helper.assertTrue(back.size() == 1, "setup: cured villagers named " + name + ": " + back.size());
			then.accept(back.get(0));
		});
	}
}

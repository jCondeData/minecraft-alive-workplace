package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Guards on a real (headless) server. Each test runs in a batch of its own: guards look for foes 24 blocks around and
 * for gear 8 blocks around, further than the 5-block gap between test areas, so neighbouring tests got in the way.
 */
public class GuardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos POST = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	private static Villager guard(GameTestHelper helper, ItemStack... chest) {
		Leftovers.clear(helper); // each guard test runs alone
		helper.setDayTime(2000); // all tests share the clock; guards fight at any hour anyway
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(CHEST, Blocks.CHEST);
		Container c = helper.getBlockEntity(CHEST);
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		return villager;
	}

	/** A guard gears up from the chest, takes on a husk and wins, without ever panicking. */
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "guardDefeatsAHusk")
	public void guardDefeatsAHusk(GameTestHelper helper) {
		Villager guard = guard(helper, new ItemStack(Items.WOODEN_SWORD), new ItemStack(Items.IRON_SWORD), new ItemStack(Items.IRON_CHESTPLATE));
		helper.runAfterDelay(80, () -> helper.spawn(EntityType.HUSK, new BlockPos(12, 2, 12)));
		helper.onEachTick(() -> {
			if (guard.getBrain().isActive(Activity.PANIC)) {
				helper.fail("the guard panicked");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertEntityNotPresent(EntityType.HUSK);
			helper.assertTrue(guard.isAlive(), "the guard died");
			helper.assertTrue(guard.getAttachedOrElse(ModAttachments.GUARD_KILLS, 0) == 1, "kill not counted");
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD), "not holding the best sword: " + guard.getItemBySlot(EquipmentSlot.MAINHAND)
				+ ", chest: " + helper.<net.minecraft.world.level.block.entity.ChestBlockEntity>getBlockEntity(CHEST).getItem(0) + " " + helper.<net.minecraft.world.level.block.entity.ChestBlockEntity>getBlockEntity(CHEST).getItem(1) + " " + helper.<net.minecraft.world.level.block.entity.ChestBlockEntity>getBlockEntity(CHEST).getItem(2));
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE), "no chestplate");
			helper.assertTrue(guard.getMaxHealth() == 40f, "guards should have 40 health");
		});
	}

	/** When the village bell rings, a guard heads for the bell instead of hiding with everyone else. */
	@GameTest(template = AREA, timeoutTicks = 600, batch = "guardAnswersTheBell")
	public void guardAnswersTheBell(GameTestHelper helper) {
		Villager guard = guard(helper, new ItemStack(Items.IRON_SWORD));
		BlockPos bell = new BlockPos(18, 2, 18);
		helper.setBlock(bell, Blocks.BELL);
		helper.runAfterDelay(5, () -> ((net.minecraft.world.level.block.BellBlock) Blocks.BELL)
			.attemptToRing(helper.getLevel(), helper.absolutePos(bell), net.minecraft.core.Direction.NORTH));
		helper.succeedWhen(() -> {
			helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.GuardRally.isRallying(guard), "not answering the bell");
			helper.assertTrue(guard.blockPosition().closerThan(helper.absolutePos(bell), 5), "the guard is at " + helper.relativePos(guard.blockPosition()));
			helper.assertFalse(guard.getBrain().isActive(Activity.HIDE), "the guard went to hide");
		});
	}

	/** Animals are nobody's enemy. */
	@GameTest(template = AREA, timeoutTicks = 400, batch = "guardLeavesAnimalsAlone")
	public void guardLeavesAnimalsAlone(GameTestHelper helper) {
		guard(helper, new ItemStack(Items.IRON_SWORD));
		Cow cow = helper.spawn(EntityType.COW, new BlockPos(5, 2, 5));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(cow.isAlive() && cow.getHealth() == cow.getMaxHealth(), "the cow was attacked");
			helper.succeed();
		});
	}

	private static final java.util.Map<java.util.UUID, String> HURT_BY = new java.util.concurrent.ConcurrentHashMap<>();

	static {
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Villager) {
				HURT_BY.merge(entity.getUUID(), source.getMsgId() + (source.getEntity() != null ? "/" + source.getEntity().getType().toShortString() : "")
					+ " " + taken + " at " + entity.level().getGameTime(), (a, b) -> a + ", " + b);
			}
		});
	}

	/** A guard with a bow from the chest shoots a creeper from a safe distance (and it never blows up). */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "guardShootsACreeper")
	public void guardShootsACreeper(GameTestHelper helper) {
		Villager guard = guard(helper, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BOW));
		boolean[] exploded = {false};
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.BOW), "the guard didn't take the bow");
			var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(15, 2, 15));
			creeper.setPersistenceRequired();
		});
		helper.onEachTick(() -> {
			if (!helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class, new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(40),
				c -> c.getSwellDir() > 0 && c.getSwelling(1f) > 0.9f).isEmpty()) {
				exploded[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(helper.getTick() > 100, "not yet");
			helper.assertEntityNotPresent(EntityType.CREEPER);
			helper.assertFalse(exploded[0], "the creeper got close enough to go off");
			helper.assertTrue(guard.isAlive() && guard.getHealth() > 30, "the guard got hurt: " + guard.getHealth() + " (" + HURT_BY.get(guard.getUUID()) + ")");
			helper.assertTrue(guard.getAttachedOrElse(ModAttachments.GUARD_KILLS, 0) == 1, "the arrow kill wasn't counted");
		});
	}
}

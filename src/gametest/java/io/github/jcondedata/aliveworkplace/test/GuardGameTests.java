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

	/** A crossbow beats a bow: the guard takes the crossbow, and shoots a creeper with it before it can go off. */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "guardUsesACrossbow")
	public void guardUsesACrossbow(GameTestHelper helper) {
		Villager guard = guard(helper, new ItemStack(Items.IRON_SWORD), new ItemStack(Items.BOW), new ItemStack(Items.CROSSBOW));
		boolean[] exploded = {false};
		helper.runAfterDelay(100, () -> {
			helper.assertTrue(guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.CROSSBOW), "the guard didn't take the crossbow: " + guard.getItemBySlot(EquipmentSlot.OFFHAND));
			helper.spawn(EntityType.CREEPER, new BlockPos(15, 2, 15)).setPersistenceRequired();
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
			helper.assertTrue(guard.getAttachedOrElse(ModAttachments.GUARD_KILLS, 0) == 1, "the kill wasn't counted");
			Container chest = helper.getBlockEntity(CHEST);
			helper.assertTrue(chest.countItem(Items.BOW) == 1, "the bow should still be in the chest");
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

	/** With nothing to fight, a guard spars with the Training Dummy by the post and gains experience; an Expert doesn't. */
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "guardTrainsAtTheDummy")
	public void guardTrainsAtTheDummy(GameTestHelper helper) {
		Villager guard = guard(helper);
		helper.setBlock(new BlockPos(8, 2, 8), ModBlocks.TRAINING_DUMMY);
		helper.setBlock(new BlockPos(14, 2, 2), ModBlocks.GUARD_POST);
		Villager expert = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 3));
		Jobs.employ(helper.getLevel(), expert, helper.absolutePos(new BlockPos(14, 2, 2)), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		expert.setVillagerData(expert.getVillagerData().setLevel(4));
		helper.succeedWhen(() -> {
			helper.assertTrue(guard.getAttachedOrElse(ModAttachments.DUMMY_HITS, 0) >= io.github.jcondedata.aliveworkplace.guard.GuardPatrol.HITS_PER_XP,
				"hits: " + guard.getAttachedOrElse(ModAttachments.DUMMY_HITS, 0));
			helper.assertTrue(guard.getVillagerXp() >= 1, "no experience from training");
			helper.assertTrue(expert.getAttachedOrElse(ModAttachments.DUMMY_HITS, 0) == 0, "the expert trained");
		});
	}

	private static ItemStack healing() {
		return net.minecraft.world.item.alchemy.PotionContents.createItemStack(Items.POTION, net.minecraft.world.item.alchemy.Potions.HEALING);
	}

	/** What a guard holds in their off hand makes their kind; a knight or a medic keeps what they hold when a bow turns up. */
	@GameTest(template = AREA, batch = "guardKindsByGear")
	public void guardKindsByGear(GameTestHelper helper) {
		Villager guard = guard(helper);
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Guards.kind(guard) == io.github.jcondedata.aliveworkplace.guard.Guards.Kind.GUARD, "plain");
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Guards.kind(guard) == io.github.jcondedata.aliveworkplace.guard.Guards.Kind.ARCHER, "archer");
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Guards.kind(guard) == io.github.jcondedata.aliveworkplace.guard.Guards.Kind.KNIGHT, "knight");
		guard.setItemSlot(EquipmentSlot.OFFHAND, healing());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Guards.kind(guard) == io.github.jcondedata.aliveworkplace.guard.Guards.Kind.MEDIC, "medic");
		// Gearing up: a knight keeps the shield though there's a bow; a plain guard with only a shield there becomes a knight.
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		Container chest = helper.getBlockEntity(CHEST);
		chest.setItem(0, new ItemStack(Items.BOW));
		io.github.jcondedata.aliveworkplace.guard.GuardPatrol.equipBestForTest(helper.getLevel(), guard, java.util.List.of(helper.absolutePos(CHEST)));
		helper.assertTrue(guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), "the knight took the bow");
		guard.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
		chest.setItem(0, new ItemStack(Items.SHIELD));
		io.github.jcondedata.aliveworkplace.guard.GuardPatrol.equipBestForTest(helper.getLevel(), guard, java.util.List.of(helper.absolutePos(CHEST)));
		helper.assertTrue(guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD), "the guard didn't take the shield");
		helper.succeed();
	}

	/** A knight blocks some blows from in front, none from behind, and never a fall. */
	@GameTest(template = AREA, batch = "aKnightBlocks")
	public void aKnightBlocks(GameTestHelper helper) {
		Villager guard = guard(helper);
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		guard.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(10.5, 2, 10.5)));
		guard.setYRot(0f);
		guard.setYHeadRot(0f);
		guard.setYBodyRot(0f);
		guard.setXRot(0f);
		net.minecraft.world.phys.Vec3 look = guard.getViewVector(1f);
		BlockPos frontPos = BlockPos.containing(guard.position().add(look.scale(3)));
		BlockPos backPos = BlockPos.containing(guard.position().subtract(look.scale(3)));
		var front = EntityType.ZOMBIE.create(helper.getLevel());
		front.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(frontPos));
		var back = EntityType.ZOMBIE.create(helper.getLevel());
		back.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(backPos));
		int blocked = 0;
		for (int i = 0; i < 200; i++) {
			guard.getItemBySlot(EquipmentSlot.OFFHAND).setDamageValue(0);
			if (io.github.jcondedata.aliveworkplace.guard.Guards.block(guard, helper.getLevel().damageSources().mobAttack(front), 2f)) {
				blocked++;
			}
			helper.assertFalse(io.github.jcondedata.aliveworkplace.guard.Guards.block(guard, helper.getLevel().damageSources().mobAttack(back), 2f), "blocked from behind");
			helper.assertFalse(io.github.jcondedata.aliveworkplace.guard.Guards.block(guard, helper.getLevel().damageSources().fall(), 2f), "blocked a fall");
		}
		helper.assertTrue(blocked > 80 && blocked < 160, "blocked " + blocked + " of 200 (60% expected)");
		helper.succeed();
	}

	/** A medic gives the most hurt villager nearby a healing potion from their bag, keeping the one in hand. */
	@GameTest(template = AREA, batch = "aMedicTendsTheWounded")
	public void aMedicTendsTheWounded(GameTestHelper helper) {
		Villager medic = guard(helper);
		medic.setItemSlot(EquipmentSlot.OFFHAND, healing());
		var bag = medic.getAttachedOrCreate(ModAttachments.BUILDER_BAG);
		bag.add(healing());
		Villager patient = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 8));
		patient.setHealth(4f);
		net.minecraft.world.entity.LivingEntity treated = io.github.jcondedata.aliveworkplace.guard.Guards.tendWounded(helper.getLevel(), medic);
		helper.assertTrue(treated == patient && patient.getHealth() > 4f, "not treated: " + patient.getHealth());
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Guards.kind(medic) == io.github.jcondedata.aliveworkplace.guard.Guards.Kind.MEDIC, "used the potion in hand");
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Guards.tendWounded(helper.getLevel(), medic) == null, "treated without potions");
		helper.succeed();
	}

	/** A Patrol Map handed to a guard: by day they walk its points, the far corner of the area and back. */
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "guardPatrolRoute")
	public void guardWalksThePatrolRoute(GameTestHelper helper) {
		Villager guard = guard(helper);
		BlockPos a = new BlockPos(18, 2, 18);
		BlockPos b = new BlockPos(18, 2, 4);
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack map = new ItemStack(io.github.jcondedata.aliveworkplace.registry.ModItems.PATROL_MAP);
		map.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.PATROL, new io.github.jcondedata.aliveworkplace.guard.PatrolMapItem.Route(
			java.util.Optional.of(helper.getLevel().dimension().location()), java.util.List.of(helper.absolutePos(a), helper.absolutePos(b))));
		io.github.jcondedata.aliveworkplace.guard.PatrolMapItem.giveTo(player, guard, map);
		helper.getLevel().getServer().getPlayerList().remove(player);
		helper.assertTrue(guard.getAttachedOrElse(io.github.jcondedata.aliveworkplace.registry.ModAttachments.PATROL_ROUTE, java.util.List.of()).size() == 2,
			"the route wasn't handed over");
		boolean[] reached = {false, false};
		helper.onEachTick(() -> {
			if (guard.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(a))) < 3) {
				reached[0] = true;
			}
			if (reached[0] && guard.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(helper.absolutePos(b))) < 3) {
				reached[1] = true;
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(reached[0] && reached[1], "reached the first point: " + reached[0] + ", the second: " + reached[1]));
	}
}

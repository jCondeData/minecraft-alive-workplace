package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Risings;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-02), from the Pharaoh's curse spec (32.9, {@link Risings}): "a mob of the raised kind that falls
 * within the radius of him rises once more after the delay, where it fell ... the same mob as it was (its gear, its
 * raid, its role) ... so it never rises twice". The existing tests cover the reach, the kinds, the delay, half health and a helmet;
 * these try what they don't:
 * <ul>
 * <li>A sword the husk picked up (a guaranteed drop) falls to the ground once: the risen husk doesn't come up holding a
 * second one, and killed again drops nothing more. Gear that never drops (drop chance 0) stays on it.</li>
 * <li>A risen husk saved and loaded again (a chunk unloaded and back) still never rises a second time.</li>
 * <li>Its name and its lair-band tag come back with it.</li>
 * </ul>
 */
public class QaRisings1102dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation DESERT = AliveWorkplace.id("desert_raiders");
	private static final BlockPos HALL = new BlockPos(15, 2, 15);

	private static Culture desert(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(DESERT);
		helper.assertTrue(culture.isPresent(), "setup: expected desert_raiders.json loaded, found none");
		return culture.get();
	}

	private static void setUp(GameTestHelper helper) {
		Leftovers.clear(helper);
		Risings.forget();
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, box.inflate(48), m -> m instanceof Husk || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, box.inflate(4), e -> true).forEach(ItemEntity::discard);
			Risings.forget();
		});
	}

	private static Husk pharaoh(GameTestHelper helper, BlockPos at) {
		Husk pharaoh = helper.spawn(EntityType.HUSK, at);
		pharaoh.setNoAi(true);
		pharaoh.addTag(VillageRaids.TAG);
		Risings.mark(pharaoh, desert(helper).captain().orElseThrow());
		helper.assertTrue(pharaoh.getTags().contains(Risings.RAISER_TAG), "setup: expected the Pharaoh marked as a raiser");
		return pharaoh;
	}

	private static Husk husk(GameTestHelper helper, BlockPos at) {
		Husk husk = helper.spawn(EntityType.HUSK, at);
		husk.setNoAi(true);
		husk.addTag(VillageRaids.TAG);
		return husk;
	}

	private static List<Husk> risen(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Husk.class, helper.getBounds().inflate(2), h -> h.isAlive() && h.getTags().contains(Risings.RISEN_TAG));
	}

	private static int swords(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2), e -> e.getItem().is(Items.DIAMOND_SWORD))
			.stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	/**
	 * A husk holding a sword it picked up (vanilla drops picked-up gear for sure) falls beside the Pharaoh: one sword
	 * on the ground, and the husk rises without it (else every kill near him makes another sword), and without the fire
 * and the slowness it fell with. Its golden helmet
	 * with drop chance 0 never drops, so it rises still wearing it; killed again, no second sword and no helmet drop.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRisingsSword1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRisingsSword1102d")
	public void aPickedUpSwordFallsOnceAndTheRisenHuskComesUpWithoutIt(GameTestHelper helper) {
		setUp(helper);
		pharaoh(helper, new BlockPos(4, 2, 4));
		Husk husk = husk(helper, new BlockPos(10, 2, 10));
		husk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIAMOND_SWORD));
		husk.setGuaranteedDrop(EquipmentSlot.MAINHAND);
		husk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		husk.setDropChance(EquipmentSlot.HEAD, 0f);
		husk.setRemainingFireTicks(400);
		husk.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 2400));
		husk.kill();
		helper.assertTrue(Risings.pending() == 1, "expected the husk waiting to rise beside the living Pharaoh, waiting: " + Risings.pending());
		helper.runAfterDelay(80, () -> {
			List<Husk> up = risen(helper);
			helper.assertTrue(up.size() == 1, "expected one risen husk, got " + up.size());
			Husk again = up.get(0);
			helper.assertTrue(swords(helper) == 1, "expected the picked-up sword on the ground once, found " + swords(helper));
			helper.assertTrue(again.getMainHandItem().isEmpty(),
				"the risen husk came up holding a second copy of the sword it dropped: " + again.getMainHandItem() + " (a sword duplicated by every kill near the Pharaoh)");
			helper.assertTrue(again.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "expected the risen husk still in its golden helmet (drop chance 0), wearing "
				+ again.getItemBySlot(EquipmentSlot.HEAD));
			helper.assertTrue(again.getRemainingFireTicks() <= 0, "the husk rose still burning (" + again.getRemainingFireTicks() + " fire ticks), as it fell");
			helper.assertTrue(again.getActiveEffects().isEmpty(), "the husk rose with the effects it fell with: " + again.getActiveEffects());
			again.kill();
			helper.assertTrue(Risings.pending() == 0, "the risen husk is waiting to rise a second time");
		});
		helper.runAfterDelay(90, () -> {
			helper.assertTrue(swords(helper) == 1, "expected still one sword after the second kill, found " + swords(helper));
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2), e -> e.getItem().is(Items.GOLDEN_HELMET)).isEmpty(),
				"the golden helmet (drop chance 0) dropped");
			helper.assertTrue(risen(helper).isEmpty(), "a husk is up again after its second death");
			helper.succeed();
		});
	}

	/**
	 * Its name and its role come back: a husk named "Sandwalker" of the lair's band rises with both. Then it is saved
	 * and loaded again (as a chunk that unloads and comes back): killed beside the still-living Pharaoh, it stays dead.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaRisingsReload1102d"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaRisingsReload1102d")
	public void aRisenHuskSavedAndLoadedStillNeverRisesTwice(GameTestHelper helper) {
		setUp(helper);
		ServerLevel level = helper.getLevel();
		pharaoh(helper, new BlockPos(4, 2, 4));
		Husk husk = husk(helper, new BlockPos(10, 2, 10));
		husk.setCustomName(Component.literal("Sandwalker"));
		husk.addTag(io.github.jcondedata.aliveworkplace.threat.Lairs.TAG);
		husk.kill();
		helper.runAfterDelay(80, () -> {
			List<Husk> up = risen(helper);
			helper.assertTrue(up.size() == 1, "expected one risen husk, got " + up.size());
			Husk again = up.get(0);
			helper.assertTrue(again.getCustomName() != null && again.getCustomName().getString().equals("Sandwalker"),
				"expected the risen husk still named Sandwalker, named " + again.getCustomName());
			helper.assertTrue(again.getTags().contains(VillageRaids.TAG) && again.getTags().contains(io.github.jcondedata.aliveworkplace.threat.Lairs.TAG),
				"expected the risen husk still a raider of the lair's band, tags " + again.getTags());
			CompoundTag saved = again.saveWithoutId(new CompoundTag());
			saved.remove("UUID");
			again.discard();
			Husk loaded = EntityType.HUSK.create(level);
			helper.assertTrue(loaded != null, "setup: no husk made");
			loaded.load(saved);
			level.addFreshEntity(loaded);
			helper.assertTrue(loaded.getTags().contains(Risings.RISEN_TAG), "the risen husk forgot it rose once after a save and load: tags " + loaded.getTags());
			loaded.kill();
			helper.assertTrue(Risings.pending() == 0, "the risen husk, saved and loaded, is waiting to rise a second time");
		});
		helper.runAfterDelay(160, () -> {
			helper.assertTrue(risen(helper).isEmpty(), "the reloaded risen husk rose a second time");
			helper.succeed();
		});
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Piglins;
import io.github.jcondedata.aliveworkplace.threat.Plunder;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane D, 2026-11-02), bug test for the piglins' plunder (32.10, {@link Plunder}: "kept on the raider ... Killed, it
 * drops what it carried where it fell") meeting the Warlord's fall (32.10: his death "zombifies the band", and "a raider
 * stays one of its raid", {@link Piglins#zombify}). A piglin of the band carrying twelve gold ingots from a village chest
 * is turned zombified (the Warlord killed); killed after, it drops nothing: the gold is gone, and the zombified piglin
 * wears an ingot on its head that never drops. Fails on main 9e0535007.
 */
public class QaPlunder1102dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	/**
	 * A plundering piglin of the Warlord's band, carrying 12 gold ingots, is zombified as his death does; killed, the
	 * zombified piglin drops the 12 ingots where it fell.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderZombified1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderZombified1102d")
	public void aPlunderingPiglinZombifiedByTheWarlordsFallStillDropsItsGold(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		Optional<Culture> found = Threats.get(PIGLINS);
		helper.assertTrue(found.isPresent(), "setup: expected piglin_incursion.json loaded");
		Culture culture = found.get();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.PIGLIN))).findFirst().orElseThrow();
		Mob piglin = Threats.create(level, member);
		helper.assertTrue(piglin != null, "setup: no piglin made");
		BlockPos p = helper.absolutePos(new BlockPos(12, 2, 12));
		piglin.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		piglin.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, piglin, culture, member);
		piglin.addTag(Lairs.TAG);
		piglin.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		level.addFreshEntityWithPassengers(piglin);
		piglin.setNoAi(true);
		VillageRaids.track(level, hall, culture, List.of(piglin));
		Plunder.carry(level, piglin, new ItemStack(Items.GOLD_INGOT, 12));
		helper.assertTrue(Plunder.carried(piglin).getCount() == 12, "setup: expected the piglin carrying 12 gold ingots, carries " + Plunder.carried(piglin));
		Mob turned = Piglins.zombify(level, piglin);
		helper.assertTrue(turned != null && turned.getType() == EntityType.ZOMBIFIED_PIGLIN, "setup: expected the piglin turned zombified, got " + turned);
		helper.assertTrue(turned.getTags().contains(VillageRaids.TAG), "setup: expected the zombified piglin still a raider");
		turned.setNoAi(true);
		BlockPos where = turned.blockPosition();
		ItemStack head = turned.getItemBySlot(EquipmentSlot.HEAD).copy();
		ItemStack carried = Plunder.carried(turned).copy();
		turned.kill();
		helper.runAfterDelay(2, () -> {
			int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3), e -> e.getItem().is(Items.GOLD_INGOT))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(dropped == 12, "the zombified piglin, killed, dropped " + dropped + " of the 12 gold ingots it carried off as a piglin (it carried "
				+ carried + " once turned, wearing " + head + " on its head)");
			helper.succeed();
		});
	}
	/**
	 * B829's edge: the zombified plunderer is saved and loaded again (a chunk unloaded, a restart). It still carries
	 * the 12 ingots and shows one on its head; killed, it drops the 12 and not the shown ingot as a 13th.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderZombifiedReload1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderZombifiedReload1102d")
	public void aZombifiedPlundererSavedAndLoadedStillDropsItsGold(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
		});
		Mob piglin = EntityType.PIGLIN.create(level);
		helper.assertTrue(piglin != null, "setup: no piglin made");
		BlockPos p = helper.absolutePos(new BlockPos(12, 2, 12));
		piglin.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		piglin.addTag(Threats.RAIDER_TAG);
		level.addFreshEntity(piglin);
		piglin.setNoAi(true);
		Plunder.carry(level, piglin, new ItemStack(Items.GOLD_INGOT, 12));
		Mob turned = Piglins.zombify(level, piglin);
		helper.assertTrue(turned != null && turned.getType() == EntityType.ZOMBIFIED_PIGLIN, "setup: expected the piglin turned zombified, got " + turned);
		CompoundTag saved = turned.saveWithoutId(new CompoundTag());
		turned.discard();
		Mob loaded = EntityType.ZOMBIFIED_PIGLIN.create(level);
		loaded.load(saved);
		level.addFreshEntity(loaded);
		loaded.setNoAi(true);
		helper.assertTrue(Plunder.carried(loaded).is(Items.GOLD_INGOT) && Plunder.carried(loaded).getCount() == 12,
			"the zombified plunderer, saved and loaded, carries " + Plunder.carried(loaded) + " instead of the 12 gold ingots");
		helper.assertTrue(loaded.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLD_INGOT) && loaded.getTags().contains(Plunder.SHOWN_TAG),
			"expected the loaded plunderer showing an ingot on its head, wears " + loaded.getItemBySlot(EquipmentSlot.HEAD));
		BlockPos where = loaded.blockPosition();
		loaded.kill();
		helper.runAfterDelay(2, () -> {
			int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3), e -> e.getItem().is(Items.GOLD_INGOT))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(dropped == 12, "the reloaded zombified plunderer, killed, dropped " + dropped + " gold ingots, expected the 12 it carried");
			helper.succeed();
		});
	}
}

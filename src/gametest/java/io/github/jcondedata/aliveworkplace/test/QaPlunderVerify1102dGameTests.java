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
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
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
 * QA (lane D, 2026-11-02), verifying B829 (f3f3cea52: a plunderer turned zombified by the Warlord's fall keeps its
 * plunder) from 32.10's text, beyond the bug tests (killed after turning, and saved and loaded):
 * <ul>
 * <li>at dawn the zombified plunderer carries the gold home: the hall counts all 12 lost, and the ingot it showed on
 *     its head comes off with the plunder;</li>
 * <li>a plunderer whose head wasn't bare (its own golden helmet, so the plunder isn't shown) keeps its helmet once
 *     turned, carries the 12, and killed drops the 12 ingots and no extra shown ingot.</li>
 * </ul>
 */
public class QaPlunderVerify1102dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	private static Culture piglins(GameTestHelper helper) {
		Optional<Culture> found = Threats.get(PIGLINS);
		helper.assertTrue(found.isPresent(), "setup: expected piglin_incursion.json loaded");
		return found.get();
	}

	private static BlockPos village(GameTestHelper helper) {
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
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		ThreatData.get(level).forgetPlunder(hall);
		return hall;
	}

	/** A piglin of the band, as a raid makes it, at {@code at}, its head bare. */
	private static Mob piglin(GameTestHelper helper, Culture culture, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.PIGLIN))).findFirst().orElseThrow();
		Mob piglin = Threats.create(level, member);
		helper.assertTrue(piglin != null, "setup: no piglin made");
		BlockPos p = helper.absolutePos(at);
		piglin.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		piglin.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, piglin, culture, member);
		piglin.addTag(Lairs.TAG);
		piglin.addTag(VillageRaids.TAG);
		piglin.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		level.addFreshEntityWithPassengers(piglin);
		piglin.setNoAi(true);
		return piglin;
	}

	/**
	 * Dawn: the zombified plunderer leaves with the band and carries its 12 ingots home. The hall counts the 12 lost,
	 * and the raider carries nothing and shows nothing on its head after.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderVerifyDawn1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderVerifyDawn1102d")
	public void aZombifiedPlundererCarriesItsGoldHomeAtDawnAndTheHallCountsIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = village(helper);
		Culture culture = piglins(helper);
		Mob piglin = piglin(helper, culture, new BlockPos(12, 2, 12));
		Plunder.carry(level, piglin, new ItemStack(Items.GOLD_INGOT, 12));
		Mob turned = Piglins.zombify(level, piglin);
		helper.assertTrue(turned != null && turned.getType() == EntityType.ZOMBIFIED_PIGLIN, "setup: expected the piglin turned zombified, got " + turned);
		turned.setNoAi(true);
		Threats.tactics(culture).forEach(t -> t.leave(level, hall, culture, turned));
		int lost = ThreatData.get(level).plundered(hall);
		helper.assertTrue(lost == 12, "the zombified plunderer went home at dawn and the hall counts " + lost + " gold lost, expected the 12 it carried");
		helper.assertTrue(Plunder.carried(turned).isEmpty(), "after going home it still carries " + Plunder.carried(turned));
		helper.assertTrue(turned.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !turned.getTags().contains(Plunder.SHOWN_TAG),
			"after going home it still shows " + turned.getItemBySlot(EquipmentSlot.HEAD) + " on its head");
		helper.succeed();
	}

	/**
	 * A plunderer in its own golden helmet (so its plunder isn't shown) is zombified: it keeps the helmet and the 12
	 * ingots; killed, exactly the 12 ingots drop where it fell.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderVerifyHelmet1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderVerifyHelmet1102d")
	public void aHelmetedPlundererZombifiedKeepsItsHelmetAndDropsJustItsGold(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		village(helper);
		Culture culture = piglins(helper);
		Mob piglin = piglin(helper, culture, new BlockPos(12, 2, 12));
		piglin.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
		piglin.setDropChance(EquipmentSlot.HEAD, 0f);
		Plunder.carry(level, piglin, new ItemStack(Items.GOLD_INGOT, 12));
		helper.assertTrue(!piglin.getTags().contains(Plunder.SHOWN_TAG), "setup: a helmeted plunderer shouldn't show its plunder");
		Mob turned = Piglins.zombify(level, piglin);
		helper.assertTrue(turned != null && turned.getType() == EntityType.ZOMBIFIED_PIGLIN, "setup: expected the piglin turned zombified, got " + turned);
		turned.setNoAi(true);
		helper.assertTrue(turned.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "the zombified plunderer wears "
			+ turned.getItemBySlot(EquipmentSlot.HEAD) + " instead of its own golden helmet");
		helper.assertTrue(Plunder.carried(turned).is(Items.GOLD_INGOT) && Plunder.carried(turned).getCount() == 12,
			"the zombified plunderer carries " + Plunder.carried(turned) + " instead of the 12 gold ingots");
		BlockPos where = turned.blockPosition();
		turned.kill();
		helper.runAfterDelay(2, () -> {
			int ingots = level.getEntitiesOfClass(ItemEntity.class, new AABB(where).inflate(3), e -> e.getItem().is(Items.GOLD_INGOT))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(ingots == 12, "the helmeted zombified plunderer, killed, dropped " + ingots + " gold ingots, expected the 12 it carried");
			helper.succeed();
		});
	}
}

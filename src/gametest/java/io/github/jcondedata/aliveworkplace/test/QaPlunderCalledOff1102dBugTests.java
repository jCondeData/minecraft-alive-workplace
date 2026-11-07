package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.Plunder;
import io.github.jcondedata.aliveworkplace.threat.ThreatCommand;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.List;
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
 * QA (lane D, 2026-11-02), bug test for the piglins' plunder (32.10: what a raider carries is "kept on the raider";
 * killed it drops it, at dawn it carries it home and "the hall keeps the count") meeting an operator's
 * {@code /workplace threat end} (32.21, {@link VillageRaids#callOff}: "its tactics end as when raiders leave at dawn").
 * A piglin of a raid under way carries 12 gold ingots from a village chest; the operator ends the raid; the piglin is
 * gone and so is the gold: nothing drops in the village, the hall counts nothing plundered (so no chronicle or siege
 * report says it), and no lair gets it. callOff discards the raiders without the tactics' {@code leave}.
 */
public class QaPlunderCalledOff1102dBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	/**
	 * A plundering piglin carrying 12 gold ingots; {@code /workplace threat end} at the hall. The 12 ingots are still
	 * accounted for: counted as plundered by the hall (as at dawn) or dropped in the village.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPlunderCalledOff1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPlunderCalledOff1102d")
	public void aRaidEndedByTheOperatorDoesNotMakeThePlunderedGoldVanish(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(48), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		ThreatData.get(level).forgetPlunder(hall);
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
		piglin.addTag(VillageRaids.TAG);
		piglin.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		level.addFreshEntityWithPassengers(piglin);
		piglin.setNoAi(true);
		VillageRaids.track(level, hall, culture, List.of(piglin));
		helper.assertTrue(ThreatData.get(level).raid(hall).isPresent(), "setup: expected a raid under way at the hall");
		Plunder.carry(level, piglin, new ItemStack(Items.GOLD_INGOT, 12));
		helper.assertTrue(Plunder.carried(piglin).getCount() == 12, "setup: expected the piglin carrying 12 gold ingots, carries " + Plunder.carried(piglin));
		int ended = ThreatCommand.end(level, hall);
		helper.assertTrue(ended == 1, "setup: expected /workplace threat end to end the one raid, ended " + ended);
		helper.assertTrue(!piglin.isAlive() || piglin.isRemoved(), "setup: expected the piglin gone once the raid was called off");
		helper.runAfterDelay(2, () -> {
			int counted = ThreatData.get(level).plundered(hall);
			int dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(hall).inflate(48), e -> e.getItem().is(Items.GOLD_INGOT))
				.stream().mapToInt(e -> e.getItem().getCount()).sum();
			helper.assertTrue(counted + dropped == 12, "after /workplace threat end the 12 gold ingots the piglin carried are gone: the hall counts "
				+ counted + " plundered and " + dropped + " lie in the village (at dawn the hall would count all 12)");
			helper.succeed();
		});
	}
}

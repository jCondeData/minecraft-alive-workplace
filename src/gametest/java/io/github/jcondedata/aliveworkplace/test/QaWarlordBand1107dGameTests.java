package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane-d-1107-0108), from the piglin incursion spec (32.10, the Warlord's fall): "his band left in the Overworld,
 * at the lair and out raiding, turns zombified at once". A piglin raiding the same village that is not of his outpost
 * (no lair's tag: a raid of the culture's own, not out of his portal) is not his band and stays a piglin. From a
 * survivor of a mutation run on Piglins (:209, the outpost's tag check on the village's raiders).
 */
public class QaWarlordBand1107dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");

	//$ gametest_ticks_batch AREA '60' '"qaWarlordBand1107d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWarlordBand1107d")
	public void theWarlordsFallTurnsOnlyHisOwnBand(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Optional<Culture> found = Threats.get(PIGLINS);
		helper.assertTrue(found.isPresent(), "piglin_incursion.json didn't load");
		Culture culture = found.get();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		BlockPos hall = helper.absolutePos(new BlockPos(1, 2, 1));
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Lairs.forget(level);
			Lairs.ENABLED = lairs;
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null && lair.culture().equals(PIGLINS), "(setup) no outpost: " + lair);
			Mob warlord = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(warlord != null, "(setup) no Warlord");
			List<Mob> band = Lairs.band(level, lair);
			helper.assertTrue(!band.isEmpty(), "(setup) the outpost has no band");
			// A piglin of the culture raiding the village, not out of his outpost (as a raid makes one, no lair's tag).
			Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.PIGLIN))).findFirst().orElseThrow();
			Mob stray = Threats.create(level, member);
			BlockPos p = helper.absolutePos(new BlockPos(4, 2, 8));
			stray.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
			stray.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
			Threats.outfit(level, stray, culture, member);
			stray.addTag(VillageRaids.TAG);
			stray.setNoAi(true);
			level.addFreshEntityWithPassengers(stray);
			helper.assertTrue(stray instanceof Piglin && stray.getTags().contains(Threats.RAIDER_TAG) && !stray.getTags().contains(Lairs.TAG),
				"(setup) the stray isn't a raiding piglin of no lair: " + stray.getTags());
			warlord.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "(setup) his death didn't break the outpost");
			long turned = level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(32),
				m -> m.isAlive() && (m instanceof ZombifiedPiglin || m instanceof Zoglin)).size();
			helper.assertTrue(turned >= band.size() - 1, "(setup) his band didn't turn: " + turned + " zombified of " + band.size());
			helper.assertTrue(stray.isAlive() && !stray.isRemoved() && stray.getType() == EntityType.PIGLIN,
				"a raiding piglin not of his outpost turned zombified at the Warlord's fall");
			helper.succeed();
		});
	}
}

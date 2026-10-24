package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;

/**
 * QA (lane D, 2026-10-24), ROADMAP 32.11 from the spec: "Breaking the coven: kill her, or break her cauldron."
 * CovenGameTests breaks the cauldron with {@code level.destroyBlock}; the nightly sweep found the player's own way of
 * breaking it (Covens.java:128, the block-break hook) had no test. Here a survival player mines the Coven Mother's
 * cauldron: the coven is broken at once, every hex lifts, and the village's chronicle names the player who broke it.
 */
public class QaCovenGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	//$ gametest_ticks_batch AREA '100' '"qaCovenCauldronByHand"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaCovenCauldronByHand")
	public void aPlayerMiningHerCauldronBreaksTheCovenAndIsNamed(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean lairs = Lairs.ENABLED;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 20;
		Lairs.ENABLED = true;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> {
			level.getServer().getPlayerList().remove(player);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ThrownPotion.class, helper.getBounds().inflate(48), p -> true).forEach(ThrownPotion::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetPast(hall);
			Lairs.forget(level);
			Covens.forget();
			Covens.Data.get(level).forget(hall);
			Lairs.ENABLED = lairs;
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3 + 2 * i, 2, 3));
			v.setNoAi(true);
			people.add(v);
		}
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 18)), COVEN);
			helper.assertTrue(lair != null && lair.culture().equals(COVEN), "no coven hut: " + lair);
			people.forEach(v -> Sickness.hex(level, v));
			helper.assertTrue(people.stream().allMatch(Sickness::isIll), "the hex didn't take");
			BlockPos cauldron = Covens.cauldron(level, lair);
			helper.assertTrue(cauldron != null && level.getBlockState(cauldron).getBlock() instanceof AbstractCauldronBlock, "no cauldron at the hut: " + cauldron);
			player.setGameMode(GameType.SURVIVAL);
			player.teleportTo(cauldron.getX() + 0.5, cauldron.getY() + 1, cauldron.getZ() + 1.5);
			helper.assertTrue(player.gameMode.destroyBlock(cauldron), "the player couldn't mine the cauldron");
			helper.assertTrue(!(level.getBlockState(cauldron).getBlock() instanceof AbstractCauldronBlock), "the cauldron still stands");
			// At once: the coven is broken, its hexes lifted, the player named.
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "mining her cauldron didn't break the coven");
			helper.assertTrue(people.stream().noneMatch(Sickness::isIll), "still hexed: " + people.stream().filter(Sickness::isIll).count());
			List<Chronicle.Entry> chronicle = ((VillageHallBlockEntity) level.getBlockEntity(hall)).chronicle();
			String last = chronicle.isEmpty() ? "" : chronicle.get(chronicle.size() - 1).text().getString();
			String name = player.getName().getString();
			helper.assertTrue(last.startsWith(name + " broke the coven") || last.equals(name + " broke the witch coven and lifted its hexes"),
				"the chronicle doesn't name the player who broke it: '" + last + "'");
			helper.succeed();
		});
	}
}

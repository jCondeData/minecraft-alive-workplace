package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-d-1031-0408): a story arc's {@code spawn} (31.4: "named mobs with gear, extra health and a boss bar") on a
 * server whose mob blocklist refuses every hostile mob at {@code addEntity}, as Cobbleverse's MobsBeGone does (B208,
 * B211). The Bandit King's captains and the test pack's Grubb the Rotten come this way. With {@code forceThreatSpawns}
 * on, the refused mob still stands there, once, with its name; with it off, nothing stands, and once it's back on the
 * next 40-tick check puts the mob in (once, not twice), so a refused spawn never strands an arc on a kill.
 */
public class QaRefusedArcMobs1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final ResourceLocation THREE = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/three_chapters");

	/** A hall with a player beside it, the test arc moved on to Grubb's chapter, every hostile mob refused; all undone after. */
	private static BlockPos toGrubbRefused(GameTestHelper helper, boolean force) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		VillageHalls.RADIUS = 8;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, hall);
		HostileSpawns.forget();
		HostileSpawns.FORCE = force;
		HostileSpawns.REFUSED_FOR_TESTS = e -> e instanceof Enemy;
		Leftovers.after(helper, () -> {
			HostileSpawns.REFUSED_FOR_TESTS = null;
			HostileSpawns.FORCE = true;
			HostileSpawns.forget();
			Arcs.stopAll(level, hall);
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
			grubbs(level, hall).forEach(Zombie::discard);
		});
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(HALL.south(2));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		level.setDayTime(Chronicle.day(level) * VillageNeeds.DAY + 1000);
		Arcs.ENABLED = true;
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = 1000;
		Arcs.Arc arc = Arcs.get(THREE).orElse(null);
		helper.assertTrue(arc != null, "the test pack's arc didn't load");
		helper.assertTrue(Arcs.start(level, hall, arc) != null, "the test arc didn't start");
		while (Arcs.running(level, hall) != null && Arcs.running(level, hall).chapter < 2) {
			Arcs.next(level, hall);
		}
		helper.assertTrue(Arcs.running(level, hall) != null && Arcs.running(level, hall).chapter == 2, "not at Grubb's chapter");
		return hall;
	}

	private static List<Zombie> grubbs(ServerLevel level, BlockPos hall) {
		return level.getEntitiesOfClass(Zombie.class, new AABB(hall).inflate(24), z -> z.isAlive() && z.getTags().contains(ArcEffects.roleTag("grubb")));
	}

	//$ gametest_ticks_batch AREA '60' '"qaRefusedArcForced"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRefusedArcForced")
	public void anArcsMobRefusedByABlocklistStandsWithForceThreatSpawns(GameTestHelper helper) {
		BlockPos hall = toGrubbRefused(helper, true);
		ServerLevel level = helper.getLevel();
		ArcEffects.check(level);
		ArcEffects.check(level);
		List<Zombie> g = grubbs(level, hall);
		helper.assertTrue(g.size() == 1, "Grubb, refused by the blocklist, isn't there once with forceThreatSpawns on: " + g.size());
		helper.assertTrue(g.get(0).getCustomName() != null && g.get(0).getCustomName().getString().equals("Grubb the Rotten"),
			"the forced Grubb lost his name: " + g.get(0).getCustomName());
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '60' '"qaRefusedArcLater"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaRefusedArcLater")
	public void anArcsMobRefusedWithTheSwitchOffComesAtTheNextCheckOnceItIsOn(GameTestHelper helper) {
		BlockPos hall = toGrubbRefused(helper, false);
		ServerLevel level = helper.getLevel();
		ArcEffects.check(level);
		helper.assertTrue(grubbs(level, hall).isEmpty(), "Grubb stands though the blocklist refused him and forceThreatSpawns is off");
		helper.assertTrue(Arcs.running(level, hall) != null && Arcs.running(level, hall).chapter == 2, "the arc moved on without its mob");
		HostileSpawns.FORCE = true;
		ArcEffects.check(level);
		ArcEffects.check(level);
		helper.assertTrue(grubbs(level, hall).size() == 1, "after forceThreatSpawns came on, Grubb isn't there once: " + grubbs(level, hall).size());
		helper.succeed();
	}
}

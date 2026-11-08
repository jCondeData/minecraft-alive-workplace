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
import io.github.jcondedata.aliveworkplace.threat.Piglins;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.ZombifiedPiglin;
import net.minecraft.world.entity.monster.Zoglin;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-d-1108-1910), from the piglin incursion spec (32.10, {@link Piglins}), two lines a mutation run on Piglins
 * left unchecked:
 * <ul>
 * <li>"They go for villagers and guards (their anger set on them)": a raiding piglin with nothing to fight is set on a
 * villager in sight, but one already fighting something (a golem that came at it) is left to that fight, not turned
 * away to the villager every second (Piglins.java:98, the "already fighting" check).</li>
 * <li>"His death puts the outpost's portal out, and the band left in the Overworld turns zombified": when the portal is
 * already out (its frame mined) before the Warlord falls, his fall still breaks the outpost and turns the band, with no
 * error (Piglins.java:187, the hiss only when blocks went out).</li>
 * <li>"They go for villagers": a raiding piglin with no villager in sight walks on toward the village's nearest
 * villager (not the hall, not too far in one go for its path) until it sees him and goes for him (Piglins.java:117-123).</li>
 * <li>"His death puts the outpost's portal out": only a lair with a portal puts one out. A witch coven broken beside a
 * player's own lit nether portal leaves that portal lit (Piglins.java:154, the lair-has-a-portal check).</li>
 * </ul>
 */
public class QaPiglins1108dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PIGLINS = AliveWorkplace.id("piglin_incursion");

	private static Culture piglins(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(PIGLINS);
		helper.assertTrue(culture.isPresent(), "setup: piglin_incursion.json didn't load");
		return culture.get();
	}

	/** A village round {@code at} of radius 20, put back when the test ends (as PiglinGameTests). */
	private static BlockPos village(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		boolean lairs = Lairs.ENABLED;
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> true).forEach(ItemEntity::discard);
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetPlunder(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Lairs.forget(level);
			Lairs.ENABLED = lairs;
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		return hall;
	}

	/** One of the culture's raiders, as a raid makes it: the first of the roster of kind {@code entity}. */
	private static Mob raider(GameTestHelper helper, Culture culture, EntityType<?> entity, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(entity))).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "setup: no " + member.entity());
		BlockPos p = helper.absolutePos(at);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	private static LivingEntity attacking(Mob mob) {
		return mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
	}

	/**
	 * Two raiding piglins 6 blocks from a villager (well within the 16 they see). One is already fighting an iron golem
	 * beside it; the other has nothing to fight. Over three seconds of steering the idle one is set on the villager
	 * (the steering runs here), and the one fighting the golem is still on the golem, never turned to the villager.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPiglinKeepsItsFight1108d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPiglinKeepsItsFight1108d")
	public void aRaidingPiglinAlreadyFightingIsNotTurnedToAVillager(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(15, 2, 15));
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(15, 2, 15));
		villager.setNoAi(true);
		villager.setInvulnerable(true);
		Mob fighter = raider(helper, culture, EntityType.PIGLIN, new BlockPos(21, 2, 15));
		Mob idle = raider(helper, culture, EntityType.PIGLIN, new BlockPos(15, 2, 21));
		IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(23, 2, 15));
		golem.setNoAi(true);
		golem.setInvulnerable(true);
		fighter.setNoAi(true);
		idle.setNoAi(true);
		VillageRaids.track(level, hall, culture, List.of(fighter, idle));
		Piglins.anger(fighter, golem);
		helper.assertTrue(attacking(fighter) == golem, "setup: the piglin isn't fighting the golem");
		helper.assertTrue(attacking(idle) == null, "setup: the idle piglin is already fighting " + attacking(idle));
		String[] turned = {null};
		helper.onEachTick(() -> {
			LivingEntity on = attacking(fighter);
			if (on != golem && turned[0] == null) {
				turned[0] = (on == null ? "nothing" : on.getType().toShortString()) + " at tick " + helper.getTick();
			}
		});
		helper.runAfterDelay(80, () -> {
			helper.assertTrue(attacking(idle) == villager, "(the steering didn't run) the piglin with nothing to fight wasn't set on the villager 6 blocks off: "
				+ attacking(idle));
			helper.assertTrue(turned[0] == null, "a raiding piglin fighting an iron golem was turned off it, onto " + turned[0]);
			helper.succeed();
		});
	}

	/**
	 * The outpost by a Town; its portal is put out first (the frame mined: every portal block gone). Then the Warlord
	 * falls: the outpost is broken and every piglin and hoglin of his band is turned zombified, with no error, as when
	 * his death puts the portal out itself.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWarlordPortalOut1108d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWarlordPortalOut1108d")
	public void theWarlordFallingAfterHisPortalWentOutStillTurnsTheBand(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(1, 2, 1));
		ServerLevel level = helper.getLevel();
		piglins(helper);
		Lairs.ENABLED = true;
		helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.TOWN);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 15)), PIGLINS);
			helper.assertTrue(lair != null && lair.culture().equals(PIGLINS), "setup: no outpost: " + lair);
			List<BlockPos> portal = Piglins.portalBlocks(level, lair);
			helper.assertTrue(!portal.isEmpty(), "setup: the outpost's portal isn't lit");
			for (BlockPos p : portal) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2 | 16);
			}
			helper.assertTrue(Piglins.portalBlocks(level, lair).isEmpty(), "setup: the portal is still lit");
			Mob warlord = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(warlord != null && warlord.isAlive(), "setup: no Warlord at the outpost");
			int band = Lairs.band(level, lair).size();
			helper.assertTrue(band > 0, "setup: the outpost has no band");
			try {
				warlord.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			} catch (RuntimeException e) {
				throw new net.minecraft.gametest.framework.GameTestAssertException("the Warlord's fall with his portal already out threw " + e);
			}
			helper.assertTrue(!warlord.isAlive(), "setup: the Warlord didn't die");
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "the Warlord's fall didn't break the outpost when its portal was already out");
			AABB around = helper.getBounds().inflate(32);
			List<LivingEntity> left = level.getEntitiesOfClass(LivingEntity.class, around, e -> e.isAlive() && Piglins.piglinKind(e));
			helper.assertTrue(left.isEmpty(), "with the portal already out, " + left.size() + " of the band of " + band + " weren't turned zombified: " + left);
			long turned = level.getEntitiesOfClass(Mob.class, around, m -> m.isAlive() && (m instanceof ZombifiedPiglin || m instanceof Zoglin)).size();
			helper.assertTrue(turned >= band - 1, "expected the band of " + band + " (less the Warlord, if counted) zombified, found " + turned);
			helper.succeed();
		});
	}
	/**
	 * A raiding piglin 21 blocks from the village's one villager (past the 16 it sees), who stands off to the side of
	 * the hall (going for the hall takes it no nearer than 19 blocks). Within 20 seconds it has come within 16 blocks of
	 * him and is set on him.
	 */
	//$ gametest_ticks_batch AREA '440' '"qaPiglinWalksOn1108d"'
	@GameTest(template = AREA, timeoutTicks = 440, batch = "qaPiglinWalksOn1108d")
	public void aRaidingPiglinOutOfSightOfTheVillagersWalksOnToThem(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(3, 2, 3));
		ServerLevel level = helper.getLevel();
		Culture culture = piglins(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(22, 2, 22));
		villager.setNoAi(true);
		villager.setInvulnerable(true);
		Mob piglin = raider(helper, culture, EntityType.PIGLIN, new BlockPos(2, 2, 27));
		helper.assertTrue(piglin.distanceTo(villager) > Piglins.SIGHT + 4, "setup: expected the piglin out of sight of the villager: " + piglin.distanceTo(villager));
		VillageRaids.track(level, hall, culture, List.of(piglin));
		double[] nearest = {Double.MAX_VALUE};
		boolean[] on = {false};
		helper.onEachTick(() -> {
			if (piglin.isAlive()) {
				nearest[0] = Math.min(nearest[0], piglin.distanceTo(villager));
				if (attacking(piglin) == villager) {
					on[0] = true;
				}
			}
		});
		helper.runAfterDelay(420, () -> {
			helper.assertTrue(nearest[0] <= Piglins.SIGHT, "the raiding piglin never came within 16 blocks of the village's villager in 20 seconds: nearest "
				+ String.format("%.1f", nearest[0]) + ", now at " + piglin.blockPosition().toShortString() + " (" + String.format("%.1f", piglin.distanceTo(villager)) + " off)");
			helper.assertTrue(on[0], "the raiding piglin came within sight of the villager but was never set on him");
			helper.succeed();
		});
	}
	/**
	 * A witch coven's hut by a village, and a player's nether portal (obsidian frame, lit) 10 blocks from the hut's
	 * middle. The Coven Mother is killed and the coven broken: the player's portal is still lit, every block of it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCovenLeavesPortal1108d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCovenLeavesPortal1108d")
	public void breakingACovenLeavesAPlayersNetherPortalLit(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(1, 2, 1));
		ServerLevel level = helper.getLevel();
		ResourceLocation coven = AliveWorkplace.id("witch_coven");
		helper.assertTrue(Threats.get(coven).isPresent(), "setup: witch_coven.json didn't load");
		Lairs.ENABLED = true;
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(18, 1, 20)), coven);
			helper.assertTrue(lair != null && lair.culture().equals(coven), "setup: expected a coven hut, got " + lair);
			// The frame: 4 wide (x 4..7), 5 tall (y 2..6), at z 8; lit inside (x 5..6, y 3..5).
			java.util.List<BlockPos> lit = new java.util.ArrayList<>();
			for (int x = 4; x <= 7; x++) {
				for (int y = 2; y <= 6; y++) {
					boolean edge = x == 4 || x == 7 || y == 2 || y == 6;
					if (edge) {
						helper.setBlock(new BlockPos(x, y, 8), Blocks.OBSIDIAN);
					}
				}
			}
			for (int x = 5; x <= 6; x++) {
				for (int y = 3; y <= 5; y++) {
					BlockPos p = helper.absolutePos(new BlockPos(x, y, 8));
					level.setBlock(p, Blocks.NETHER_PORTAL.defaultBlockState(), 2 | 16);
					lit.add(p);
				}
			}
			BlockPos c = lair.pos();
			helper.assertTrue(lit.stream().allMatch(p -> Math.abs(p.getX() - c.getX()) <= 16 && Math.abs(p.getZ() - c.getZ()) <= 16
				&& p.getY() - c.getY() >= -6 && p.getY() - c.getY() <= 12), "setup: the portal isn't within 16 blocks of the hut's middle " + c.toShortString());
			helper.assertTrue(lit.stream().allMatch(p -> level.getBlockState(p).is(Blocks.NETHER_PORTAL)), "setup: the portal isn't lit");
			Mob mother = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(mother != null, "setup: no Coven Mother at the hut");
			mother.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(Lairs.near(level, hall).isEmpty(), "setup: the Coven Mother's death didn't break the coven");
			long out = lit.stream().filter(p -> !level.getBlockState(p).is(Blocks.NETHER_PORTAL)).count();
			helper.assertTrue(out == 0, "breaking a witch coven put out " + out + " of the 6 blocks of a player's nether portal beside it");
			helper.succeed();
		});
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.threat.Covens;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-02), from the witch coven spec (32.11, {@link Covens}), the corners its tests don't reach:
 * <ul>
 * <li>The Coven Mother blinks "after that many hits in a row (none more than IN_A_ROW ticks after the last)": hits 101
 * ticks apart never add up; 100 apart do, and the third blinks her 8 to 12 blocks off.</li>
 * <li>The nightly hex takes "one to three of the village's grown villagers who aren't yet": never a child, never one
 * already hexed, never more than are left, every count from one to three seen over many nights, nobody when all are.</li>
 * <li>In a raid "a witch of the raid that is hurt gets a potion of healing from another instead".</li>
 * <li>"A village without walls: they come within UNWALLED blocks of a villager and throw from there".</li>
 * </ul>
 */
public class QaCovens1102dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");
	private static final BlockPos HALL = new BlockPos(1, 2, 1);

	private static Culture coven(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(COVEN);
		helper.assertTrue(culture.isPresent(), "setup: expected witch_coven.json loaded, found none");
		return culture.get();
	}

	/** A village round {@code at} of radius 20, lairs on; everything put back after (as CovenGameTests). */
	private static BlockPos village(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		boolean lairs = Lairs.ENABLED;
		Lairs.ENABLED = true;
		Set<String> off = Threats.DISABLED;
		long time = level.getDayTime();
		BlockPos hall = helper.absolutePos(at);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> !(m instanceof Villager)).forEach(Mob::discard);
			level.getEntitiesOfClass(ThrownPotion.class, helper.getBounds().inflate(48), p -> true).forEach(ThrownPotion::discard);
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(4), v -> true).forEach(v -> Sickness.recover(level, v));
			VillageRaids.forget(hall);
			ThreatData.get(level).end(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Lairs.forget(level);
			Covens.forget();
			Covens.Data.get(level).forget(hall);
			Lairs.ENABLED = lairs;
			Threats.DISABLED = off;
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		return hall;
	}

	private static Lairs.Lair hut(GameTestHelper helper, BlockPos hall, BlockPos ground) {
		Lairs.Lair lair = Lairs.found(helper.getLevel(), hall, helper.absolutePos(ground), COVEN);
		helper.assertTrue(lair != null && lair.culture().equals(COVEN), "setup: expected a coven hut, got " + lair);
		return lair;
	}

	/**
	 * Hits 101 ticks apart are never in a row: three of them leave her where she stood. Then two more, each exactly 100
	 * ticks after the one before, make three in a row and she blinks 8 to 12 blocks off.
	 */
	//$ gametest_ticks_batch AREA '460' '"qaCovenBlinkRow1102d"'
	@GameTest(template = AREA, timeoutTicks = 460, batch = "qaCovenBlinkRow1102d")
	public void hitsMoreThanAHundredTicksApartNeverMakeHerBlinkAndAHundredApartDo(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		coven(helper);
		Mob[] mother = new Mob[1];
		Vec3[] at = new Vec3[1];
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = hut(helper, hall, new BlockPos(15, 1, 15));
			mother[0] = (Mob) level.getEntity(lair.captain());
			helper.assertTrue(mother[0] != null, "setup: expected the Coven Mother at her hut");
			mother[0].setNoAi(true);
			at[0] = mother[0].position();
		});
		long[] hits = {10, 111, 212, 312, 412};
		for (int i = 0; i < hits.length; i++) {
			int n = i;
			helper.runAfterDelay(hits[i], () -> {
				helper.assertTrue(mother[0].hurt(level.damageSources().generic(), 1f), "setup: hit " + (n + 1) + " didn't land");
				double moved = Math.hypot(mother[0].getX() - at[0].x, mother[0].getZ() - at[0].z);
				if (n < 4) {
					helper.assertTrue(moved < 1, "she blinked at hit " + (n + 1) + " (hits at ticks 10, 111, 212 are 101 apart, never in a row; 312 is the second in a row): moved "
						+ String.format("%.1f", moved));
				} else {
					helper.assertTrue(mother[0].isAlive() && moved >= 7.5 && moved <= 12.5,
						"expected her to blink 8 to 12 blocks off at the third hit in a row (212, 312, 412: each 100 after the last), moved " + String.format("%.1f", moved));
				}
			});
		}
		helper.runAfterDelay(420, helper::succeed);
	}

	/**
	 * Fifty nights, each from a clean village of four grown villagers, one child and one hexed already: the hex takes one
	 * to three, never the child, never the hexed one again, and over the nights takes one, two and three. With only two
	 * left open it takes at most two; with none, nobody.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCovenHexCount1102d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCovenHexCount1102d")
	public void theNightlyHexTakesOneToThreeGrownVillagersNotYetHexed(GameTestHelper helper) {
		BlockPos hall = village(helper, HALL);
		ServerLevel level = helper.getLevel();
		coven(helper);
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = hut(helper, hall, new BlockPos(15, 1, 18));
			List<Villager> grown = new ArrayList<>();
			for (int i = 0; i < 4; i++) {
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + 2 * i, 2, 3));
				v.setNoAi(true);
				grown.add(v);
			}
			Villager child = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 6));
			child.setNoAi(true);
			child.setAge(-24000 * 10);
			Villager already = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 6));
			already.setNoAi(true);
			List<Villager> all = new ArrayList<>(grown);
			all.add(child);
			all.add(already);
			Set<Integer> counts = new TreeSet<>();
			for (long seed = 0; seed < 50; seed++) {
				all.forEach(v -> Sickness.recover(level, v));
				Sickness.hex(level, already);
				helper.assertTrue(Sickness.isHexed(already) && grown.stream().noneMatch(Sickness::isHexed) && !Sickness.isHexed(child),
					"setup: expected only the one villager hexed before night " + seed);
				List<Villager> hexed = Covens.hexNight(level, lair, RandomSource.create(seed));
				helper.assertTrue(hexed.size() >= 1 && hexed.size() <= 3, "night " + seed + ": expected one to three hexed, got " + hexed.size());
				helper.assertFalse(hexed.contains(child) || Sickness.isHexed(child), "night " + seed + ": the hex took a child");
				helper.assertFalse(hexed.contains(already), "night " + seed + ": the hex took again a villager hexed already");
				helper.assertTrue(grown.stream().filter(Sickness::isHexed).count() == hexed.size(),
					"night " + seed + ": expected the " + hexed.size() + " named hexed among the grown, found " + grown.stream().filter(Sickness::isHexed).count());
				counts.add(hexed.size());
			}
			helper.assertTrue(counts.equals(new TreeSet<>(List.of(1, 2, 3))), "expected every count from one to three over 50 nights, saw " + counts);
			for (long seed = 0; seed < 20; seed++) {
				all.forEach(v -> Sickness.recover(level, v));
				Sickness.hex(level, already);
				Sickness.hex(level, grown.get(0));
				Sickness.hex(level, grown.get(1));
				List<Villager> hexed = Covens.hexNight(level, lair, RandomSource.create(seed));
				helper.assertTrue(hexed.size() >= 1 && hexed.size() <= 2 && !hexed.contains(grown.get(0)) && !hexed.contains(grown.get(1)),
					"night " + seed + " with two grown left open: hexed " + hexed.size());
			}
			grown.forEach(v -> Sickness.hex(level, v));
			Sickness.hex(level, already);
			List<Villager> none = Covens.hexNight(level, lair, RandomSource.create(7L));
			helper.assertTrue(none.isEmpty() && !Sickness.isHexed(child), "with every grown villager hexed the hex still took " + none.size());
			helper.succeed();
		});
	}

	private static Mob witch(GameTestHelper helper, Culture culture, BlockPos at, Villager target) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.WITCH))).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob instanceof Witch, "setup: expected a witch, got " + mob);
		BlockPos p = helper.absolutePos(at);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		mob.addTag(VillageRaids.TAG);
		if (target != null) {
			mob.setTarget(target);
		}
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/**
	 * Two witches of a raid on an unwalled village, side by side and far from its one villager; one is hurt to half her
	 * health. Within five throws' time the other throws her a potion of healing.
	 */
	//$ gametest_ticks_batch AREA '360' '"qaCovenHeal1102d"'
	@GameTest(template = AREA, timeoutTicks = 360, batch = "qaCovenHeal1102d")
	public void aHurtWitchOfTheRaidGetsAPotionOfHealingFromHerSister(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(3, 2, 3));
		ServerLevel level = helper.getLevel();
		Culture culture = coven(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		villager.setNoAi(true);
		Mob hurt = witch(helper, culture, new BlockPos(24, 2, 26), null);
		Mob sister = witch(helper, culture, new BlockPos(27, 2, 26), null);
		List<Mob> raiders = new ArrayList<>(List.of(hurt, sister));
		VillageRaids.track(level, hall, culture, raiders);
		hurt.setHealth(hurt.getMaxHealth() * 0.5f);
		boolean[] healed = {false};
		helper.onEachTick(() -> {
			for (ThrownPotion p : level.getEntitiesOfClass(ThrownPotion.class, helper.getBounds().inflate(16), p -> p.getOwner() == sister)) {
				PotionContents contents = p.getItem().get(net.minecraft.core.component.DataComponents.POTION_CONTENTS);
				if (contents != null && contents.is(Potions.HEALING)) {
					healed[0] = true;
				}
			}
		});
		helper.runAfterDelay(320, () -> {
			helper.assertTrue(healed[0], "in 16 seconds the sister never threw the hurt witch (" + hurt.getHealth() + " of " + hurt.getMaxHealth()
				+ ", " + String.format("%.1f", hurt.distanceTo(sister)) + " blocks off) a potion of healing");
			helper.succeed();
		});
	}

	/**
	 * An unwalled village: a raiding witch 30 blocks from its one villager comes to within 10 blocks of him and no
	 * nearer than 6, and throws at him from there.
	 */
	//$ gametest_ticks_batch AREA '500' '"qaCovenUnwalled1102d"'
	@GameTest(template = AREA, timeoutTicks = 500, batch = "qaCovenUnwalled1102d")
	public void aWitchRaidingAnUnwalledVillageComesWithinTenBlocksAndThrowsFromThere(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(3, 2, 3));
		ServerLevel level = helper.getLevel();
		Culture culture = coven(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		villager.setNoAi(true);
		Mob witch = witch(helper, culture, new BlockPos(24, 2, 24), villager);
		VillageRaids.track(level, hall, culture, new ArrayList<>(List.of(witch)));
		helper.assertTrue(witch.distanceTo(villager) > 28, "setup: expected the witch about 30 blocks off, " + witch.distanceTo(villager));
		boolean[] thrown = {false};
		double[] nearest = {Double.MAX_VALUE};
		helper.onEachTick(() -> {
			if (!level.getEntitiesOfClass(ThrownPotion.class, helper.getBounds().inflate(16), p -> p.getOwner() == witch).isEmpty()) {
				thrown[0] = true;
			}
			if (witch.isAlive()) {
				nearest[0] = Math.min(nearest[0], witch.distanceTo(villager));
			}
		});
		helper.runAfterDelay(460, () -> {
			double d = witch.distanceTo(villager);
			helper.assertTrue(witch.isAlive() && d <= Covens.UNWALLED + 0.5, "expected the witch within 10 blocks of the villager after 23 s, she is "
				+ String.format("%.1f", d) + " off at " + witch.blockPosition().toShortString());
			helper.assertTrue(nearest[0] >= 6, "the witch came " + String.format("%.1f", nearest[0]) + " blocks from the villager (expected to stop about 8 off)");
			helper.assertTrue(thrown[0], "the witch never threw a potion at the villager");
			helper.succeed();
		});
	}
}

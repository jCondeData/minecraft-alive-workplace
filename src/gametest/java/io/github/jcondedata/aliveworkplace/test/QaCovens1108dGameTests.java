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
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
 * QA (lane-d-1108-1910), from the witch coven spec (32.11, {@link Covens}), two lines a mutation run on Covens left
 * unchecked:
 * <ul>
 * <li>"Throw splash potions over them (slowness, weakness and poison at villagers, harming at guards) and heal each
 * other": healing goes only to a sister who is hurt. A raiding witch at full health, alone, throws villager potions at the
 * villagers and never a potion of healing (Covens.java:435, the hurt-sister check).</li>
 * <li>"Witches stand 12–20 blocks outside the walls": where a witch stands when the village's walls on her side end just
 * level with the hall (the outermost wall on her side is exactly abreast of it) is 12 to 20 blocks from the walls, not
 * reckoned from the far side of the village (Covens.java:384).</li>
 * <li>"It ends when the coven is broken": breaking one village's coven cures that village's hexed, but not a hexed
 * villager of the next village whose own coven still stands, though he lives within reach of the first (Covens.java:181).</li>
 * <li>"Every night it stands, the coven hexes one to three of the village's villagers": through the game's own clock, a
 * night hexes one to three of six, and later checks the same night hex no more (Covens.java:236).</li>
 * <li>"Harming at guards": a raiding witch whose only villager in reach is a guard throws him potions of harming, never
 * the villagers' slowness, weakness or poison (Covens.java:447).</li>
 * <li>"No hexes ... in a quiet village" (32.21): a village set At peace by its hall's owner is not hexed at night though
 * a coven hut stands by it (Covens.java:235).</li>
 * </ul>
 */
public class QaCovens1108dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation COVEN = AliveWorkplace.id("witch_coven");

	private static Culture coven(GameTestHelper helper) {
		Optional<Culture> culture = Threats.get(COVEN);
		helper.assertTrue(culture.isPresent(), "setup: expected witch_coven.json loaded, found none");
		return culture.get();
	}

	/** A village round {@code at} of radius 20; everything put back after (as QaCovens1102dGameTests). */
	private static BlockPos village(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 20;
		boolean lairs = Lairs.ENABLED;
		Set<String> off = Threats.DISABLED;
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
			Lairs.ENABLED = lairs;
			Threats.DISABLED = off;
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
		});
		return hall;
	}

	private static Mob witch(GameTestHelper helper, Culture culture, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.entity().equals(EntityType.getKey(EntityType.WITCH))).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob instanceof Witch, "setup: expected a witch, got " + mob);
		BlockPos p = helper.absolutePos(at);
		mob.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 180f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		mob.addTag(VillageRaids.TAG);
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/**
	 * An unwalled village with one villager; one raiding witch at full health 7 blocks from him (within the 10 she comes
	 * to). In 12 seconds (four throws' time) she throws at him, and every potion she throws is slowness, weakness or
	 * poison: none is healing, for no sister of hers is hurt (and she is not, either).
	 */
	//$ gametest_ticks_batch AREA '300' '"qaCovenNoHealing1108d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCovenNoHealing1108d")
	public void aWitchAtFullHealthThrowsVillagerPotionsNeverHealing(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(5, 2, 5));
		ServerLevel level = helper.getLevel();
		Culture culture = coven(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		villager.setNoAi(true);
		villager.setInvulnerable(true);
		Mob witch = witch(helper, culture, new BlockPos(12, 2, 5));
		VillageRaids.track(level, hall, culture, new ArrayList<>(List.of(witch)));
		List<String> healing = new ArrayList<>();
		List<String> other = new ArrayList<>();
		Set<java.util.UUID> seen = new java.util.HashSet<>();
		helper.onEachTick(() -> {
			for (ThrownPotion p : level.getEntitiesOfClass(ThrownPotion.class, helper.getBounds().inflate(16), p -> p.getOwner() == witch)) {
				if (!seen.add(p.getUUID())) {
					continue;
				}
				PotionContents contents = p.getItem().get(DataComponents.POTION_CONTENTS);
				String name = contents == null ? "none" : contents.potion().map(h -> h.unwrapKey().map(k -> k.location().getPath()).orElse("?")).orElse("none");
				if (contents != null && (contents.is(Potions.HEALING) || contents.is(Potions.STRONG_HEALING))) {
					healing.add(name + " at tick " + helper.getTick() + " (witch " + witch.getHealth() + "/" + witch.getMaxHealth() + ")");
				} else {
					other.add(name);
				}
			}
		});
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(healing.isEmpty(), "a witch at full health, with no hurt sister, threw potions of healing: " + healing + " (and others: " + other + ")");
			helper.assertTrue(!other.isEmpty(), "in 12 seconds the witch 7 blocks from the villager threw him nothing");
			helper.assertTrue(other.stream().allMatch(n -> n.contains("slowness") || n.contains("weakness") || n.contains("poison")),
				"expected only slowness, weakness or poison at a villager, got " + other);
			helper.succeed();
		});
	}

	/**
	 * A village walled on its west side, with the wall's north stretch ending exactly abreast of the hall. A witch due east
	 * of the hall has the wall's end as the outermost wall on her side: she stands 12 to 20 blocks from the walls (16
	 * beyond it, about 19 from its nearest block), not 16 beyond the far side of the whole village (over 30 off, out of
	 * the spec's band and past the 28 she throws).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void aWitchBesideAWallEndingAbreastOfTheHallStandsTwelveToTwentyFromIt(GameTestHelper helper) {
		BlockPos hall = new BlockPos(1000, 64, 1000);
		List<BlockPos> walls = new ArrayList<>();
		for (int z = -10; z <= 10; z++) {
			walls.add(hall.offset(-10, 0, z)); // the west wall
		}
		for (int x = -9; x <= 0; x++) {
			walls.add(hall.offset(x, 0, 10)); // its north stretch, ending abreast of the hall
		}
		Vec3 at = new Vec3(hall.getX() + 30.5, 64, hall.getZ() + 0.5);
		Vec3 stand = Covens.standing(hall, at, walls);
		helper.assertTrue(stand != null, "a walled village gave the witch nowhere to stand");
		double from = Covens.wallDistance(stand.x, stand.z, walls);
		helper.assertTrue(from >= Covens.NEAREST && from <= Covens.FARTHEST, "expected the witch east of the hall to stand 12 to 20 blocks from the walls, she stands "
			+ String.format("%.1f", from) + " off, " + String.format("%.1f", stand.x - hall.getX() - 0.5) + " east of the hall");
		helper.assertTrue(stand.z == at.z && stand.x > hall.getX() + 0.5, "expected her to stand on her own side, due east of the hall: " + stand);
		helper.succeed();
	}
	/**
	 * Two villages side by side, 40 blocks apart, each with its own coven hut. A villager of each is hexed; the second's
	 * stands 26 blocks from the first village's hall (inside the ground the first coven's fall reaches). The first
	 * Coven Mother is killed: her village's hexed villager is cured at once, but the next village's stays hexed, for
	 * its own coven still stands.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaCovenNeighbourHex1108d"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaCovenNeighbourHex1108d")
	public void breakingOneCovenLeavesTheNextVillagesHexWhileItsCovenStands(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(1, 2, 1));
		BlockPos next = helper.absolutePos(new BlockPos(41, 2, 1));
		ServerLevel level = helper.getLevel();
		coven(helper);
		Lairs.ENABLED = true;
		Leftovers.after(helper, () -> {
			Covens.Data.get(level).forget(hall);
			Covens.Data.get(level).forget(next);
		});
		helper.runAfterDelay(2, () -> {
			Lairs.Lair ours = Lairs.found(level, hall, helper.absolutePos(new BlockPos(6, 1, 20)), COVEN);
			helper.assertTrue(ours != null && ours.culture().equals(COVEN), "setup: expected a coven hut by the first village, got " + ours);
			Lairs.Lair theirs = Lairs.found(level, next, helper.absolutePos(new BlockPos(23, 1, 20)), COVEN);
			helper.assertTrue(theirs != null && theirs.culture().equals(COVEN) && theirs.hall().equals(next),
				"setup: expected a coven hut by the next village, got " + theirs);
			Villager mine = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
			mine.setNoAi(true);
			Villager neighbour = helper.spawn(EntityType.VILLAGER, new BlockPos(27, 2, 3));
			neighbour.setNoAi(true);
			Sickness.hex(level, mine);
			Sickness.hex(level, neighbour);
			helper.assertTrue(Sickness.isHexed(mine) && Sickness.isHexed(neighbour), "setup: expected both villagers hexed");
			Mob mother = (Mob) level.getEntity(ours.captain());
			helper.assertTrue(mother instanceof Witch, "setup: expected the first Coven Mother at her hut, got " + mother);
			mother.hurt(level.damageSources().fellOutOfWorld(), 1000f);
			helper.assertTrue(!mother.isAlive(), "setup: the Coven Mother didn't die");
			helper.assertFalse(Sickness.isHexed(mine), "the first village's hexed villager wasn't cured when its Coven Mother died");
			helper.assertTrue(Lairs.all(level).stream().anyMatch(l -> l.hall().equals(next)), "setup: the next village's coven was broken too");
			helper.assertTrue(Sickness.isHexed(neighbour), "the next village's hexed villager was cured when the first village's coven broke, though his own coven still stands");
			helper.succeed();
		});
	}
	/**
	 * A coven hut by a village of six grown villagers; it is night (15000) and no night has been hexed. The covens' own
	 * check (every 2 seconds) hexes one to three of them; over the next 12 seconds of the same night, six more checks,
	 * nobody else is hexed and the night is remembered as today.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCovenOncePerNight1108d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCovenOncePerNight1108d")
	public void aCovenHexesOneToThreeOnceANightThroughTheClock(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(1, 2, 1));
		ServerLevel level = helper.getLevel();
		coven(helper);
		Lairs.ENABLED = true;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			Covens.Data.get(level).forget(hall);
			level.setDayTime(time);
		});
		List<Villager> people = new ArrayList<>();
		int[] first = {-1};
		helper.runAfterDelay(2, () -> {
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 20)), COVEN);
			helper.assertTrue(lair != null && lair.culture().equals(COVEN), "setup: expected a coven hut, got " + lair);
			for (int i = 0; i < 6; i++) {
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2 + 2 * i, 2, 3));
				v.setNoAi(true);
				v.setInvulnerable(true);
				people.add(v);
			}
			Covens.Data.get(level).forget(hall);
			level.setDayTime(level.getDayTime() / 24000 * 24000 + 15000);
		});
		helper.runAfterDelay(130, () -> {
			first[0] = (int) people.stream().filter(Sickness::isHexed).count();
			helper.assertTrue(first[0] >= 1 && first[0] <= 3, "expected the night's hex to take one to three of six within 6 seconds of nightfall, it took " + first[0]);
		});
		helper.runAfterDelay(380, () -> {
			long now = people.stream().filter(Sickness::isHexed).count();
			helper.assertTrue(now == first[0], "the coven hexed again the same night: " + first[0] + " hexed at first, " + now + " twelve seconds later");
			helper.assertTrue(Covens.Data.get(level).lastHexed(hall) == io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level),
				"tonight isn't remembered as hexed: " + Covens.Data.get(level).lastHexed(hall));
			helper.succeed();
		});
	}
	/**
	 * An unwalled village whose one villager is a guard (standing still); one raiding witch at full health 7 blocks from
	 * him. In 12 seconds she throws at him, and every potion she throws is harming.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaCovenHarmsGuards1108d"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaCovenHarmsGuards1108d")
	public void aWitchThrowsHarmingAtAGuard(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(5, 2, 5));
		ServerLevel level = helper.getLevel();
		Culture culture = coven(helper);
		Villager guard = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 5));
		guard.setNoAi(true);
		guard.setInvulnerable(true);
		guard.setVillagerData(guard.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD));
		helper.assertTrue(io.github.jcondedata.aliveworkplace.guard.Guards.isGuard(guard), "setup: the villager isn't a guard");
		Mob witch = witch(helper, culture, new BlockPos(12, 2, 5));
		VillageRaids.track(level, hall, culture, new ArrayList<>(List.of(witch)));
		List<String> thrown = new ArrayList<>();
		Set<java.util.UUID> seen = new java.util.HashSet<>();
		helper.onEachTick(() -> {
			for (ThrownPotion p : level.getEntitiesOfClass(ThrownPotion.class, helper.getBounds().inflate(16), p -> p.getOwner() == witch)) {
				if (seen.add(p.getUUID())) {
					PotionContents contents = p.getItem().get(DataComponents.POTION_CONTENTS);
					thrown.add(contents == null ? "none" : contents.potion().map(h -> h.unwrapKey().map(k -> k.location().getPath()).orElse("?")).orElse("none"));
				}
			}
		});
		helper.runAfterDelay(250, () -> {
			helper.assertTrue(!thrown.isEmpty(), "in 12 seconds the witch 7 blocks from the guard threw him nothing");
			helper.assertTrue(thrown.stream().allMatch(n -> n.contains("harming")), "expected only potions of harming at a guard, got " + thrown);
			helper.succeed();
		});
	}
	/**
	 * As {@link #aCovenHexesOneToThreeOnceANightThroughTheClock}, but the village's hall is set At peace: a whole night's
	 * checks (15 seconds) hex no one, and no night is remembered as hexed.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaCovenAtPeace1108d"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaCovenAtPeace1108d")
	public void aVillageAtPeaceIsNotHexedAtNight(GameTestHelper helper) {
		BlockPos hall = village(helper, new BlockPos(1, 2, 1));
		ServerLevel level = helper.getLevel();
		coven(helper);
		Lairs.ENABLED = true;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			Covens.Data.get(level).forget(hall);
			level.setDayTime(time);
		});
		helper.setBlock(new BlockPos(1, 2, 1), io.github.jcondedata.aliveworkplace.registry.ModBlocks.VILLAGE_HALL);
		List<Villager> people = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(level.getBlockEntity(hall) instanceof io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity, "setup: no village hall");
			((io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity) level.getBlockEntity(hall)).setAtPeace(true,
				io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level));
			helper.assertTrue(Threats.quiet(level, hall), "setup: the village isn't quiet when At peace");
			Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(new BlockPos(15, 1, 20)), COVEN);
			helper.assertTrue(lair != null && lair.culture().equals(COVEN), "setup: expected a coven hut, got " + lair);
			for (int i = 0; i < 6; i++) {
				Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(4 + 2 * i, 2, 3));
				v.setNoAi(true);
				v.setInvulnerable(true);
				people.add(v);
			}
			Covens.Data.get(level).forget(hall);
			level.setDayTime(level.getDayTime() / 24000 * 24000 + 15000);
		});
		helper.runAfterDelay(320, () -> {
			long hexed = people.stream().filter(Sickness::isHexed).count();
			helper.assertTrue(hexed == 0, "a village At peace was hexed at night: " + hexed + " of 6");
			helper.assertTrue(Covens.Data.get(level).lastHexed(hall) == -1, "a night was hexed in a village At peace");
			helper.succeed();
		});
	}
}

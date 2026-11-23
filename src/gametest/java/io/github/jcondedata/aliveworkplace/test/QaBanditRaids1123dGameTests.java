package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.HostileSpawns;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * QA (lane-d-1123-0408), from the spec only (docs/wiki/raids.md and docs/wiki/bandit-king.md), the claims no test
 * checked yet: the nightly chance's cap of 35% at its edge; the clock's hour (19:30 to midnight) and its rest days over
 * a thousand fixed seeds; the Bandit King, killed by a player, drops none of his gear (the crown is in the chest); his
 * raiders "half again as big" (sized as for 6 + 1.5 times the villagers) and still capped at 16; and a culture file's
 * {@code gear} with {@code ominous_banner}, worn and never dropped, an unknown key ignored and wrong values refused.
 */
public class QaBanditRaids1123dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final ResourceLocation BANNERMEN = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_bannermen_1123d");

	/** 15% at 8 villagers, +1% a villager, at most 35%: 34% at 27, the cap reached at 28 and never passed. */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBanditRaids1123dChance"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBanditRaids1123dChance")
	public void qaNightlyChanceReachesItsCapAt28AndStaysThere(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		int[][] cases = {{9, 16}, {10, 17}, {20, 27}, {27, 34}, {28, 35}, {29, 35}, {40, 35}, {200, 35}};
		for (int[] c : cases) {
			float got = VillageRaids.nightlyChance(c[0]);
			if (Math.abs(got - c[1] / 100f) > 1e-4) {
				problems.add(c[0] + " villagers: " + got + ", the spec says " + c[1] + "%");
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * The clock over 1000 fixed seeds for a village of 28 (a 35% night): the hour always falls between 19:30 (day time
	 * 13500) and midnight (18000), reaching near both ends; the side is always one of the eight; within the rest days
	 * (the last raid 1 or 2 days ago) no night is ever set, and on the third day after nights are set about as often as
	 * the chance says.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBanditRaids1123dClock"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBanditRaids1123dClock")
	public void qaTheClocksHourAndRestDaysOverAThousandSeeds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(new BlockPos(0, 1, 0));
		float chance = VillageRaids.chance(level, hall, 28);
		helper.assertTrue(chance > 0.1f && Lairs.near(level, hall).isEmpty(), "setup: no chance of a raid here (" + chance + "), or a lair near");
		long today = io.github.jcondedata.aliveworkplace.hall.Chronicle.day(level);
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		int third = 0;
		List<String> problems = new ArrayList<>();
		// (Fresh seeds one apart give a java.util.Random almost the same first float, so each seed comes from one fixed stream.)
		RandomSource seeds = RandomSource.create(112300L);
		boolean raidsOn = VillageRaids.ENABLED;
		VillageRaids.ENABLED = true; // (the config turns raids off under GameTests; put back below, before any tick)
		for (int seed = 0; seed < 1000; seed++) {
			long s = seeds.nextLong();
			for (long ago : new long[] {1, 2}) {
				VillageRaids.Night night = VillageRaids.rollNight(level, hall, 28, today - ago, RandomSource.create(s));
				if (night.raid() && problems.size() < 5) {
					problems.add("seed " + seed + ": a raid set " + ago + " day(s) after the last");
				}
			}
			VillageRaids.Night night = VillageRaids.rollNight(level, hall, 28, today - 3, RandomSource.create(s));
			if (night.raid()) {
				third++;
				min = Math.min(min, night.at());
				max = Math.max(max, night.at());
				if (night.at() < 13500 || night.at() >= 18000) {
					problems.add("seed " + seed + ": the hour " + night.at() + " isn't between 19:30 and midnight");
				}
				double eighths = night.angle() / (Math.PI / 4);
				if (Math.abs(eighths - Math.round(eighths)) > 1e-6) {
					problems.add("seed " + seed + ": the side " + night.angle() + " isn't one of the eight");
				}
			}
		}
		VillageRaids.ENABLED = raidsOn;
		float share = third / 1000f;
		if (Math.abs(share - chance) > 0.06f) {
			problems.add("on the third day " + third + " of 1000 nights were set, the chance is " + chance);
		}
		if (third > 0 && (min > 13600 || max < 17900)) {
			problems.add("the hours only spread from " + min + " to " + max);
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	/**
	 * "The King wears his crown but never drops it, nor the rest of his gear: it's in the chest." Killed by a player he
	 * drops no golden helmet, no iron armor and no diamond axe; a plain vindicator in an iron chestplate set to drop it,
	 * killed the same way beside him, does (so drops happen here at all).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBanditRaids1123dKingDrops"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBanditRaids1123dKingDrops")
	public void qaTheKingKilledByAPlayerDropsNoneOfHisGear(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
		Leftovers.after(helper, () -> level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard));
		BlockPos at = helper.absolutePos(new BlockPos(4, 2, 4));
		Mob king = BanditKing.spawn(level, at, at, at);
		helper.assertTrue(king != null, "no King");
		level.addFreshEntity(king);
		helper.assertTrue(king.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET), "setup: the King has no crown");
		Mob control = helper.spawn(EntityType.VINDICATOR, new BlockPos(12, 2, 12));
		control.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		control.setDropChance(EquipmentSlot.CHEST, 2f);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.moveTo(at.getX() + 2.5, at.getY(), at.getZ() + 0.5);
		helper.runAfterDelay(2, () -> {
			king.hurt(level.damageSources().playerAttack(player), 10000f);
			control.hurt(level.damageSources().playerAttack(player), 10000f);
		});
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(!king.isAlive() && !control.isAlive(), "setup: the King or the plain vindicator didn't die");
			List<ItemEntity> nearKing = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(at).inflate(4));
			List<String> gear = new ArrayList<>();
			for (ItemEntity item : nearKing) {
				ItemStack s = item.getItem();
				if (s.is(Items.GOLDEN_HELMET) || s.is(Items.IRON_CHESTPLATE) || s.is(Items.IRON_LEGGINGS) || s.is(Items.IRON_BOOTS) || s.is(Items.DIAMOND_AXE)) {
					gear.add(s.toString());
				}
			}
			helper.assertTrue(gear.isEmpty(), "the King dropped his gear: " + gear);
			BlockPos c = helper.absolutePos(new BlockPos(12, 2, 12));
			boolean controlDropped = level.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(c).inflate(3))
				.stream().anyMatch(i -> i.getItem().is(Items.IRON_CHESTPLATE));
			helper.assertTrue(controlDropped, "setup: a vindicator set to drop its chestplate didn't, so this test proves nothing");
			helper.succeed();
		});
	}

	/** A hall with {@code villagers} villagers and nothing left over from earlier batches; undone at the end (as QaRaidSize1116d). */
	private static BlockPos village(GameTestHelper helper, int villagers) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		Lairs.forget(level);
		VillageRaids.forget();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		ThreatData.get(level).forgetClock(hall);
		HostileSpawns.forget();
		Leftovers.after(helper, () -> {
			HostileSpawns.forget();
			cleanRaid(helper, hall);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
		});
		for (int i = 0; i < villagers; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i % 12, 2, 3 + i / 12 * 2));
		}
		level.setDayTime(39 * 24000L + 15000);
		helper.assertTrue(Lairs.near(level, hall).isEmpty() && Threats.LEVEL < 3, "setup: a lair near, or a hard threat level");
		return hall;
	}

	private static void cleanRaid(GameTestHelper helper, BlockPos hall) {
		helper.getLevel().getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
			m -> m.getTags().contains(VillageRaids.TAG) || m.getTags().contains(Threats.EXTRA_TAG)).forEach(Mob::discard);
		VillageRaids.forget();
		ThreatData.get(helper.getLevel()).forgetPast(hall);
	}

	/** Starts the King's raid on {@code hall} and checks its size; then a plain bandit raid of the same village for comparison. */
	private static void kingsRaid(GameTestHelper helper, BlockPos hall, int villagers, int want, int plainWant) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(VillageHalls.villagers(level, hall, v -> v.isAlive()).size() == villagers,
			"setup: the hall counts " + VillageHalls.villagers(level, hall, v -> v.isAlive()).size() + " villagers, not " + villagers);
		VillageRaids.Raid raid = BanditKingStory.raid(level, hall);
		helper.assertTrue(raid != null, "the King's raid didn't start");
		long band = VillageRaids.raiders(level, hall).stream().filter(m -> !m.getTags().contains(Threats.EXTRA_TAG)).count();
		helper.assertTrue(raid.raiders() == want && band == want,
			"the King's raid on " + villagers + " villagers: " + raid.raiders() + " recorded, " + band + " came; half again as big is " + want);
		cleanRaid(helper, hall);
		VillageRaids.Raid plain = VillageRaids.start(level, hall, villagers, 0, Double.NaN, Threats.get(Threats.BANDITS).orElseThrow(), RandomSource.create(1123L));
		helper.assertTrue(plain != null && plain.raiders() == plainWant,
			"setup: the plain bandit raid on " + villagers + " villagers brought " + (plain == null ? "nothing" : plain.raiders()) + ", not " + plainWant);
		cleanRaid(helper, hall);
	}

	/**
	 * "Half again as big": sized as a raid on a village of 6 + 1.5 times its villagers (the plain raid is 3 + villagers /
	 * 4). 8 villagers: the plain bandits are 5, the King's 3 + 18 / 4 = 7.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBanditRaids1123dKingRaid"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBanditRaids1123dKingRaid")
	public void qaTheKingsRaidIsHalfAgainAsBig(GameTestHelper helper) {
		BlockPos hall = village(helper, 8);
		helper.runAfterDelay(2, () -> {
			kingsRaid(helper, hall, 8, 7, 5);
			helper.succeed();
		});
	}

	/** "Still capped as every raid": 36 villagers would be 3 + 60 / 4 = 18, but no more than 16 come (the plain raid, 12). */
	//$ gametest_ticks_batch AREA '100' '"qaBanditRaids1123dKingCap"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBanditRaids1123dKingCap")
	public void qaTheKingsRaidIsStillCappedAt16(GameTestHelper helper) {
		BlockPos hall = village(helper, 36);
		helper.runAfterDelay(2, () -> {
			kingsRaid(helper, hall, 36, 16, 12);
			helper.succeed();
		});
	}

	private static Culture read(String json) {
		return Culture.read(BANNERMEN, com.google.gson.JsonParser.parseString(json).getAsJsonObject());
	}

	/**
	 * A culture file's {@code gear}: {@code "head": "ominous_banner"} puts the raid captain's banner on every raider's
	 * head, beside a plain chestplate, and neither drops when a player kills one ("its role's gear, which never drops").
	 * The file's unknown key is ignored (it still reads); the extras past the plain raid keep the roster's own head.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaBanditRaids1123dGear"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaBanditRaids1123dGear")
	public void qaAnOminousBannerInGearIsWornAndNeverDrops(GameTestHelper helper) {
		BlockPos hall = village(helper, 4);
		ServerLevel level = helper.getLevel();
		Culture culture = read("{\"where\": {\"min_villagers\": 100000}, \"colour\": \"red\", \"roster\": [{\"entity\": \"minecraft:zombie\", \"share\": 10,"
			+ " \"gear\": {\"head\": \"ominous_banner\", \"chest\": \"minecraft:iron_chestplate\"}}]}");
		helper.assertTrue(culture.weight() == 10, "a file without a weight should weigh 10, weighs " + culture.weight());
		level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(24)).forEach(Entity::discard);
		Leftovers.after(helper, () -> level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(24)).forEach(Entity::discard));
		ItemStack banner = Raid.getLeaderBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<Mob> band = new ArrayList<>();
		helper.runAfterDelay(2, () -> {
			// 4 villagers and 4 guards: the plain 4 and 2 extras past them.
			VillageRaids.Raid raid = VillageRaids.start(level, hall, 4, 4, Double.NaN, culture, RandomSource.create(11231L));
			helper.assertTrue(raid != null && raid.raiders() == 6, "the raid: " + (raid == null ? "none" : raid.raiders() + " raiders, not 6"));
			band.addAll(VillageRaids.raiders(level, hall).stream().filter(m -> !m.getTags().contains(Threats.EXTRA_TAG)).toList());
			List<String> wrong = new ArrayList<>();
			for (Mob m : band) {
				if (!ItemStack.isSameItemSameComponents(m.getItemBySlot(EquipmentSlot.HEAD), banner)) {
					wrong.add(m.getName().getString() + " wears " + m.getItemBySlot(EquipmentSlot.HEAD) + " on its head");
				}
				if (!m.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)) {
					wrong.add(m.getName().getString() + " wears " + m.getItemBySlot(EquipmentSlot.CHEST));
				}
			}
			helper.assertTrue(band.size() == 6 && wrong.isEmpty(), band.size() + " raiders; " + String.join("; ", wrong));
			for (Mob m : band) {
				m.hurt(level.damageSources().playerAttack(player), 10000f);
			}
		});
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(band.stream().noneMatch(Mob::isAlive), "setup: a raider survived");
			Set<String> dropped = new HashSet<>();
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(24)).forEach(i -> {
				if (i.getItem().is(Items.WHITE_BANNER) || i.getItem().is(Items.IRON_CHESTPLATE)) {
					dropped.add(i.getItem().toString());
				}
			});
			helper.assertTrue(dropped.isEmpty(), "a raider's gear dropped: " + dropped);
			helper.succeed();
		});
	}

	/**
	 * "A file that can't be read (... a wrong role, arrival, hours, slot ...) is skipped": each of these is refused when
	 * read, while the same file with right values reads.
	 */
	//$ gametest_batch 'FabricGameTest.EMPTY_STRUCTURE' '"qaBanditRaids1123dRefused"'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "qaBanditRaids1123dRefused")
	public void qaWrongValuesInACultureFileAreRefused(GameTestHelper helper) {
		String good = "{\"arrival\": \"edge\", \"hours\": \"night\", \"roster\": [{\"entity\": \"minecraft:zombie\", \"share\": 10, \"role\": \"melee\", \"gear\": {\"head\": \"minecraft:iron_helmet\"}}]}";
		read(good);
		String[][] bad = {
			{"a wrong slot", good.replace("\"head\"", "\"hat\"")},
			{"a wrong role", good.replace("\"melee\"", "\"tank\"")},
			{"a wrong arrival", good.replace("\"edge\"", "\"sky\"")},
			{"a wrong hours", good.replace("\"night\"", "\"dawn\"")},
			{"an unknown item", good.replace("minecraft:iron_helmet", "minecraft:no_such_helmet")},
			{"an unknown mob", good.replace("minecraft:zombie", "minecraft:no_such_mob")},
			{"an empty roster", "{\"roster\": []}"},
		};
		List<String> read = new ArrayList<>();
		for (String[] b : bad) {
			try {
				read(b[1]);
				read.add(b[0]);
			} catch (IllegalArgumentException expected) {
				// refused, as the spec says
			}
		}
		helper.assertTrue(read.isEmpty(), "a culture file was read despite " + String.join(", ", read));
		helper.succeed();
	}
}

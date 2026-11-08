package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Sickness;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.threat.Plague;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (lane D, 2026-11-08) for the plague's carriers (ROADMAP 32.18: "a coven's hex or a traveller from far away at the
 * inn can bring it", {@link Plague#carried}: by a 1-in-10 chance, "where the plague could start today (switched on, its
 * conditions holding, no disaster under way or just past)", the carrier its first case). A local mutation run of Plague
 * (lane-d-1108-0108) found carried() untested: a disaster under way not stopping it, the week's rest after a disaster
 * moved by a day, the switch-off check and its chance check never passing all survived (GameTests switch the daily rolls off, so nothing
 * called it). These call it as the coven and the inn do, with the rolls switched on for the test and a random whose
 * first roll is known.
 */
public class QaPlagueCarrier1108dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(16, 2, 16);
	/** Day 61 begins here, away from the other plague tests' days. */
	private static final long BASE = 60 * VillageNeeds.DAY;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, List<Villager> people) {
		VillageHalls.Census census() {
			return VillageHalls.census(level, hall);
		}
	}

	/** A Town of three villagers with a bed each (the plague's 3% a day holds), no disaster ever. */
	private static Village town(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long was = level.getDayTime();
		boolean rolling = Disasters.ROLLING;
		VillageHalls.RADIUS = 20;
		level.setDayTime(BASE + 1000);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity e) {
				e.setDisaster(null, -1, -1, -100);
			}
			Disasters.ROLLING = rolling;
			Disasters.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(was);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		helper.assertTrue(entity != null, "no hall");
		entity.setDisaster(null, -1, -1, -100);
		List<Villager> people = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new net.minecraft.world.phys.Vec3(4.5 + 3 * i, 2, 4.5));
			v.setNoAi(true);
			Beds.home(v, helper.absolutePos(new BlockPos(4 + 3 * i, 2, 8)));
			people.add(v);
		}
		return new Village(level, hall, entity, people);
	}

	/**
	 * Plague.carried with the daily rolls switched on for that call only (left on, the hall's own daily round would roll
	 * the test datapack's disasters in between).
	 */
	private static boolean carried(Village v, Villager carrier, RandomSource random) {
		boolean was = Disasters.ROLLING;
		Disasters.ROLLING = true;
		try {
			return Plague.carried(v.level(), carrier, random);
		} finally {
			Disasters.ROLLING = was;
		}
	}

	/** A fresh random whose first nextFloat is below {@code under} (lucky) or at least {@code atLeast} (unlucky). */
	private static RandomSource rolling(float under, float atLeast) {
		for (long seed = 1; seed < 100_000; seed++) {
			float f = RandomSource.create(seed).nextFloat();
			if (f < under && f >= atLeast) {
				return RandomSource.create(seed);
			}
		}
		throw new IllegalStateException("no seed rolls in [" + atLeast + ", " + under + ")");
	}

	private static RandomSource lucky() {
		return rolling(0.05f, 0f);
	}

	private static RandomSource unlucky() {
		return rolling(1f, 0.5f);
	}

	/**
	 * In a Town where the plague could start: an unlucky carrier (a roll of 0.5 or more against 1 in 10) brings nothing,
	 * a lucky one (under 0.05) brings it: carried() says so, the plague is under way at the hall and the carrier is its
	 * first case. (Kills the chance check's {@code <= 0} moved to {@code <= 1}, which never let a carrier bring it.)
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPlagueCarrierLucky1108d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPlagueCarrierLucky1108d")
	public void aLuckyCarrierBringsThePlagueToATownAndAnUnluckyOneDoesnt(GameTestHelper helper) {
		Village v = town(helper);
		helper.runAfterDelay(5, () -> {
			v.entity().setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(v.census().villagers() == 3 && v.census().beds() == 3, "setup: not 3 villagers and 3 beds: " + v.census().villagers() + ", " + v.census().beds());
			helper.assertTrue(Disasters.chance(v.level(), v.hall(), Disasters.get(Plague.ID).orElseThrow(), v.census()) > 0, "setup: the plague couldn't start in this Town");
			Villager carrier = v.people().get(0);
			helper.assertFalse(carried(v, carrier, unlucky()), "an unlucky carrier (a roll over 1 in 10) brought the plague");
			helper.assertTrue(v.entity().disaster() == null && !Sickness.isPlagueIll(carrier), "an unlucky carrier started something: " + v.entity().disaster());
			helper.assertTrue(carried(v, carrier, lucky()), "a lucky carrier (a roll under 1 in 10) in a Town where the plague could start didn't bring it");
			helper.assertTrue(Plague.ID.equals(v.entity().disaster()), "the carrier brought it but the hall's disaster is " + v.entity().disaster());
			helper.assertTrue(Sickness.isPlagueIll(carrier), "the carrier isn't the plague's first case");
			helper.succeed();
		});
	}

	/**
	 * Where the plague couldn't start today even a lucky carrier brings nothing: in a Village (the plague is Town and up),
	 * with config {@code plague} false, with another disaster under way, and in the week after the last disaster (6 days on); exactly 7 days on it can
	 * again. (Kills the disaster-under-way check returning true, the week's rest {@code <} turned into {@code <=}, and
	 * the switched-on check's {@code ||} turned into {@code &&}.)
	 */
	//$ gametest_ticks_batch AREA '100' '"qaPlagueCarrierRest1108d"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaPlagueCarrierRest1108d")
	public void noCarrierBringsItWhereThePlagueCouldntStart(GameTestHelper helper) {
		Village v = town(helper);
		helper.runAfterDelay(5, () -> {
			Villager carrier = v.people().get(1);
			long today = Chronicle.day(v.level());
			v.entity().setRank(VillageRanks.Rank.VILLAGE);
			helper.assertFalse(carried(v, carrier, lucky()), "a lucky carrier brought the plague to a Village (it is Town and up)");
			v.entity().setRank(VillageRanks.Rank.TOWN);
			WorkplaceConfig off = new WorkplaceConfig();
			off.disasters.put("plague", false);
			off.apply();
			helper.assertFalse(carried(v, carrier, lucky()), "a lucky carrier brought the plague with config plague false");
			helper.assertTrue(v.entity().disaster() == null && !Sickness.isPlagueIll(carrier), "with the plague switched off a carrier started something: " + v.entity().disaster());
			new WorkplaceConfig().apply();
			ResourceLocation drought = ResourceLocation.fromNamespaceAndPath("aliveworkplace", "drought");
			v.entity().setDisaster(drought, today, today + 5, today);
			helper.assertFalse(carried(v, carrier, lucky()), "a lucky carrier brought the plague while a drought was under way");
			helper.assertTrue(drought.equals(v.entity().disaster()), "the drought under way was replaced by " + v.entity().disaster());
			v.entity().setDisaster(null, -1, -1, today - (Disasters.REST - 1));
			helper.assertFalse(carried(v, carrier, lucky()), "a lucky carrier brought the plague " + (Disasters.REST - 1) + " days after the last disaster");
			helper.assertTrue(v.entity().disaster() == null && !Sickness.isPlagueIll(carrier), "a refused carrier started something: " + v.entity().disaster());
			v.entity().setDisaster(null, -1, -1, today - Disasters.REST);
			helper.assertTrue(carried(v, carrier, lucky()), "a lucky carrier didn't bring the plague " + Disasters.REST + " days after the last disaster (the week's rest is over)");
			helper.assertTrue(Plague.ID.equals(v.entity().disaster()) && Sickness.isPlagueIll(carrier), "the plague didn't start with the carrier as its first case");
			helper.succeed();
		});
	}
}

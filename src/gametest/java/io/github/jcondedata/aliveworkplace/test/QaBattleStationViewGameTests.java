package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (2026-11-05) for B252 and B320, from their Expected and fix notes: during a siege an archer takes the station that
 * sees the most of the ground in front of the breach (on a Gatehouse a crenel over the gate, x 4 or 6 of the blueprint,
 * not the merlon spot (5, 6, 2) chosen before), steps back to its middle when he drifts more than 0.2 off it, picks his
 * station again after a restart (nothing saved), and shoots a raider coming at the gate before it is under the walkway.
 * BattleStationGameTests proves he sees and hits a raider standing 12 out and turns from a hidden foe; these prove the
 * rest. Each test runs alone in its batch.
 */
public class QaBattleStationViewGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_view_besiegers");
	/** The hall north of the Gatehouse; the Gatehouse's corner, its gate in the middle of its south face (z 12 + 1). */
	private static final BlockPos HALL = new BlockPos(13, 2, 2);
	private static final BlockPos GATEHOUSE = new BlockPos(8, 2, 8);

	private record Village(ServerLevel level, BlockPos hall, Culture culture, BlueprintData.Placement gatehouse) {
	}

	/** A hall with a finished Gatehouse to its south; the raiders are husks that ram gates. All undone after. */
	private static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		helper.setDayTime(6000);
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			BattleStations.forget();
			Moods.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(GATEHOUSE);
		level.getStructureManager().get(StarterBlueprints.GATEHOUSE.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.GATEHOUSE.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		return new Village(level, hall, culture, placement);
	}

	/** An archer with a Guard Post of his own two blocks south of him. */
	private static Villager archer(GameTestHelper helper, int x, int z) {
		BlockPos post = new BlockPos(x, 2, z + 2);
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(x, 2, z));
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		return v;
	}

	/** A raider held still at (x, 2, z) relative to the area. */
	private static Mob raider(GameTestHelper helper, double x, double z) {
		ServerLevel level = helper.getLevel();
		Mob mob = EntityType.HUSK.create(level);
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + 2, base.getZ() + z, 0f, 0f);
		mob.addTag(VillageRaids.TAG);
		mob.setNoAi(true);
		mob.setPersistenceRequired();
		level.addFreshEntity(mob);
		return mob;
	}

	private static ThreatData.Siege siege(GameTestHelper helper, Village village) {
		Optional<ThreatData.Siege> siege = Sieges.siege(village.level(), village.hall());
		helper.assertTrue(siege.isPresent(), "no siege on the village");
		return siege.get();
	}

	private static double offMiddle(Villager v, BlockPos station) {
		double dx = v.getX() - (station.getX() + 0.5);
		double dz = v.getZ() - (station.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * The station chosen over the gate sees the most of the ground in front of the breach of any he may take: at least as
	 * much as every other free station within his post's reach, and more than the merlon spot (5, 6, 2) chosen before
	 * B252; it is a crenel over the gate, x 4 or 6 of the blueprint at z 2.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaStationMostView"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaStationMostView")
	public void theStationOverTheGateIsTheOneThatSeesMost(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		Villager archer = archer(helper, 10, 3);
		Mob foe = raider(helper, 13.5, 27.5); // out beyond his reach: he is not drawn off by a fight
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		ThreatData.Siege siege = siege(helper, village);
		helper.assertTrue(siege.breach != null && siege.out == Direction.SOUTH, "the breach is the Gatehouse's gate, its outside the south: " + siege.breach + ", " + siege.out);
		helper.onEachTick(() -> {
			if (!BattleStations.onStation(archer)) {
				if (helper.getTick() > 450) {
					helper.fail("the archer isn't on the Gatehouse within 450 ticks: at " + archer.position() + ", his place " + BattleStations.post(archer));
				}
				return;
			}
			BlockPos station = BattleStations.post(archer).orElseThrow();
			BlockPos o = village.gatehouse().origin();
			BlockPos rel = station.subtract(o);
			int seen = BattleStations.view(level, siege, station, false);
			int merlon = BattleStations.view(level, siege, o.offset(5, 6, 2), false);
			helper.assertTrue(rel.getY() == 6 && rel.getZ() == 2 && (rel.getX() == 4 || rel.getX() == 6),
				"his station is " + rel + " of the Gatehouse (view " + seen + "), not a crenel over the gate (4 or 6, 6, 2)");
			helper.assertTrue(seen > merlon, "his station sees " + seen + " spots of the ground, the old merlon spot " + merlon);
			BlockPos home = helper.absolutePos(new BlockPos(10, 2, 5));
			for (BlockPos s : BattleStations.stations(level, village.hall())) {
				double dx = s.getX() - home.getX();
				double dz = s.getZ() - home.getZ();
				if (dx * dx + dz * dz <= (double) BattleStations.POST_REACH * BattleStations.POST_REACH && BattleStations.standable(level, s)) {
					int other = BattleStations.view(level, siege, s, false);
					helper.assertTrue(seen >= other, "the station " + s.subtract(o) + " sees " + other + " spots, more than his " + rel + " (" + seen + ")");
				}
			}
			helper.succeed();
		});
	}

	/**
	 * Drift: on his station he is pushed half a block to the side (still on it, behind the merlon beside his crenel, where
	 * B252 found him at 0.47); he steps back to its middle (within 0.2, a little slack for the step) and stays on it.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaStationDrift"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaStationDrift")
	public void anArcherPushedOffHisStationsMiddleStepsBack(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		Villager archer = archer(helper, 10, 3);
		Mob foe = raider(helper, 13.5, 27.5);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		siege(helper, village);
		long[] pushed = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (pushed[0] < 0) {
				if (BattleStations.onStation(archer)) {
					BlockPos station = BattleStations.post(archer).orElseThrow();
					// give him a few ticks to settle, then push him 0.5 east, along the walkway
					if (offMiddle(archer, station) <= 0.25) {
						archer.getNavigation().stop();
						archer.teleportTo(station.getX() + 1.0, archer.getY(), station.getZ() + 0.5);
						helper.assertTrue(BattleStations.onStation(archer) && offMiddle(archer, station) >= 0.45, "setup: the push left him "
							+ offMiddle(archer, station) + " off, on his station " + BattleStations.onStation(archer));
						pushed[0] = tick;
					}
				} else if (tick > 450) {
					helper.fail("the archer isn't on the Gatehouse within 450 ticks: at " + archer.position() + ", his place " + BattleStations.post(archer));
				}
				return;
			}
			BlockPos station = BattleStations.post(archer).orElse(null);
			helper.assertTrue(station != null && BattleStations.onStation(archer), "pushed half a block, he left his station: at " + archer.position()
				+ ", his place " + station);
			if (offMiddle(archer, station) <= 0.25) {
				helper.succeed();
			} else if (tick > pushed[0] + 100) {
				helper.fail("100 ticks after the push he still stands " + offMiddle(archer, station) + " off his station's middle " + station);
			}
		});
	}

	/**
	 * A restart in mid-siege (BattleStations forgets every place, as a server stop does: nothing is saved): the archer
	 * picks his station again and is back on the same one, the one that sees most.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaStationRestart"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaStationRestart")
	public void afterARestartHeTakesTheSameStationAgain(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		Villager archer = archer(helper, 10, 3);
		Mob foe = raider(helper, 13.5, 27.5);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		siege(helper, village);
		BlockPos[] first = {null};
		long[] restart = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (first[0] == null) {
				if (BattleStations.onStation(archer)) {
					first[0] = BattleStations.post(archer).orElseThrow();
					BattleStations.forget();
					restart[0] = tick;
					helper.assertTrue(BattleStations.post(archer).isEmpty(), "setup: his place survived the forgetting");
				} else if (tick > 450) {
					helper.fail("the archer isn't on the Gatehouse within 450 ticks: at " + archer.position());
				}
				return;
			}
			if (BattleStations.onStation(archer)) {
				helper.assertTrue(BattleStations.post(archer).equals(Optional.of(first[0])),
					"after the restart he took " + BattleStations.post(archer) + ", not the station he had, " + first[0]);
				helper.succeed();
			} else if (tick > restart[0] + 200) {
				helper.fail("200 ticks after a restart he isn't on a station: at " + archer.position() + ", his place " + BattleStations.post(archer));
			}
		});
	}

	/**
	 * B320's Expected: the archer shoots raiders coming at the gate. The raider is held 19 blocks out from the gate until
	 * the archer is on his station (as the scene now does), then comes on, slowed; he is hit while still at least 2 blocks
	 * out from the gate's face, before he is under the walkway where no station sees.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qaStationApproach"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaStationApproach")
	public void aRaiderComingAtTheGateIsShotOnTheWayIn(GameTestHelper helper) {
		Village village = village(helper);
		ServerLevel level = village.level();
		Villager archer = archer(helper, 10, 3);
		Mob foe = raider(helper, 13.5, 29.5);
		VillageRaids.track(level, village.hall(), village.culture(), List.of(foe));
		ThreatData.Siege siege = siege(helper, village);
		double face = village.gatehouse().origin().getZ() + 5; // the Gatehouse's south face (it is 5 deep)
		long[] released = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (released[0] < 0) {
				helper.assertTrue(foe.getHealth() == foe.getMaxHealth(), "the raider was hurt before the archer was on his station");
				if (BattleStations.onStation(archer)) {
					foe.setNoAi(false);
					foe.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 2000, 1));
					foe.getNavigation().moveTo(siege.breach.getX() + 0.5, foe.getY(), siege.breach.getZ() + 1.5, 1.0);
					released[0] = tick;
				} else if (tick > 450) {
					helper.fail("the archer isn't on the Gatehouse within 450 ticks: at " + archer.position());
				}
				return;
			}
			double out = foe.getZ() - face;
			if (foe.getHealth() < foe.getMaxHealth()) {
				helper.assertTrue(out >= 2, "he was first hit only " + out + " blocks out from the gate's face");
				helper.assertTrue(BattleStations.onStation(archer), "he came down off his station to hit the raider: at " + archer.position());
				helper.succeed();
			} else if (out < 2) {
				helper.fail("the raider reached " + out + " blocks from the gate's face unhurt, " + (tick - released[0]) + " ticks after coming on; the archer at "
					+ archer.position() + " on " + BattleStations.post(archer).orElse(null) + ", in sight " + archer.hasLineOfSight(foe));
			} else if (tick > released[0] + 600) {
				helper.fail("600 ticks after coming on the raider is still " + out + " out, unhurt: in sight " + archer.hasLineOfSight(foe));
			}
		});
	}
}

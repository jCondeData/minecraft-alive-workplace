package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane D (2026-11-22), B1427 written from the spec, not the fix. gatehouse.md, "Battle stations": every station is
 * "Reachable within 450 ticks by a guard starting on the village side", and "Level II: at least one station with
 * z >= 4 (on the barbican)"; the Gatehouse stands with its row z = 3 in a wall, the village side at z < 0, the outside
 * past the barbican (z 9). The existing tests stand Gatehouse II as drawn with four archers. These turn and mirror it
 * inside a stone wall, post six archers at once, lose the stations' memory mid-siege (as a server restart does), and
 * upgrade a manned Gatehouse I to II during the siege: every archer must end standing on a station of the build within
 * 450 ticks, none two on one station.
 */
public class QaGatehouseReach1122dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_gatehouse_reach_1122d");
	private static final int LIMIT = 450;
	/** Where the drawing's (5, 0, 3) (the middle of the wall row) stands, relative to the area. */
	private static final BlockPos CENTRE = new BlockPos(15, 2, 15);

	/** A Gatehouse stood in the area turned and mirrored: drawing positions to world ones. */
	private record Gate(ServerLevel level, BlockPos origin, Rotation rotation, Mirror mirror, BlockPos hall, Culture culture) {
		BlockPos world(int x, int y, int z) {
			return origin.offset(StructureTemplate.transform(new BlockPos(x, y, z), mirror, rotation, BlockPos.ZERO));
		}

		String drawing(BlockPos world) {
			BlockPos d = world.subtract(origin);
			// Undo the turn and the mirror (both are their own kind of inverse: turn back, then mirror again).
			Rotation back = switch (rotation) {
				case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
				case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
				default -> rotation;
			};
			BlockPos unturned = StructureTemplate.transform(d, Mirror.NONE, back, BlockPos.ZERO);
			return StructureTemplate.transform(unturned, mirror, Rotation.NONE, BlockPos.ZERO).toShortString();
		}

		/** Where one stands on every station of {@code build} at this placement (world). */
		Set<BlockPos> stations(StarterBlueprints.Entry build) {
			Set<BlockPos> out = new HashSet<>();
			for (BlockPos s : BattleStations.spots(BlueprintLibrary.get(level, build.id()).orElseThrow())) {
				out.add(origin.offset(StructureTemplate.transform(s, mirror, rotation, BlockPos.ZERO)).above());
			}
			return out;
		}
	}

	/**
	 * A village hall on the village side, the ram culture loaded, {@code build} stood finished turned and mirrored in a
	 * stone wall 4 high along its row z = 3 (the wall runs to the area's edges), and a raider 7 out past the barbican.
	 */
	private static Gate gate(GameTestHelper helper, StarterBlueprints.Entry build, Rotation rotation, Mirror mirror) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		helper.setDayTime(6000);
		BlockPos centre = helper.absolutePos(CENTRE);
		BlockPos origin = centre.subtract(StructureTemplate.transform(new BlockPos(5, 0, 3), mirror, rotation, BlockPos.ZERO));
		Gate g0 = new Gate(level, origin, rotation, mirror, null, null);
		BlockPos hall = g0.world(5, 0, -9);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}], \"tactics\": [\"ram_gates\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		List<BlueprintData.Placement> placed = new ArrayList<>();
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
			placed.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		Gate gate = new Gate(level, origin, rotation, mirror, hall, culture);
		placed.add(stand(gate, build));
		Leftovers.after(helper, () -> {
			// a later upgrade's placement is recorded at the same origin: forget it too
			BuildSiteManager.get(level).forgetFinished(new BlueprintData.Placement(level.dimension().location(), origin, rotation, mirror));
		});
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		int sx = build.size().getX();
		for (int x = -30; x < sx + 30; x++) {
			if (x >= 0 && x < sx) {
				continue;
			}
			for (int y = 0; y <= 3; y++) {
				BlockPos p = gate.world(x, y, 3);
				BlockPos r = p.subtract(base);
				if (r.getX() >= 0 && r.getX() < 30 && r.getZ() >= 0 && r.getZ() < 30) {
					level.setBlockAndUpdate(p, Blocks.STONE_BRICKS.defaultBlockState());
				}
			}
		}
		return gate;
	}

	/** Stands {@code build} at the gate's origin, turn and mirror, and records it finished. */
	private static BlueprintData.Placement stand(Gate gate, StarterBlueprints.Entry build) {
		ServerLevel level = gate.level();
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, gate.origin(), gate.origin(),
			new StructurePlaceSettings().setRotation(gate.rotation()).setMirror(gate.mirror()), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), gate.origin(), gate.rotation(), gate.mirror());
		BuildSiteManager.get(level).recordFinished(build.id(), placement, UUID.randomUUID());
		return placement;
	}

	/** Archer guards (bow in the off hand), their posts on the village side at drawing (x, 0, -5), standing at z = -6. */
	private static List<Villager> archers(GameTestHelper helper, Gate gate, int... xs) {
		List<Villager> out = new ArrayList<>();
		for (int x : xs) {
			BlockPos post = gate.world(x, 0, -5);
			gate.level().setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
			Villager v = helper.spawn(EntityType.VILLAGER, gate.world(x, 0, -6).subtract(helper.absolutePos(BlockPos.ZERO)));
			Jobs.employ(gate.level(), v, post, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
			v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
			out.add(v);
		}
		return out;
	}

	/** A still raider 7 out past the barbican (drawing (5, 0, 16)), tracked as the village's raid: the siege starts. */
	private static Mob siege(GameTestHelper helper, Gate gate) {
		ServerLevel level = gate.level();
		Mob foe = EntityType.HUSK.create(level);
		BlockPos at = gate.world(5, 0, 16);
		foe.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		foe.addTag(VillageRaids.TAG);
		foe.setNoAi(true);
		foe.setPersistenceRequired();
		level.addFreshEntity(foe);
		VillageRaids.track(level, gate.hall(), gate.culture(), List.of(foe));
		helper.assertTrue(Sieges.siege(level, gate.hall()).isPresent(), "(setup) no siege on the village");
		return foe;
	}

	/** Problems with the archers now: each must stand on a station of {@code stations} he can stand on, none sharing one. */
	private static List<String> off(Gate gate, List<Villager> archers, Set<BlockPos> stations) {
		List<String> out = new ArrayList<>();
		Set<BlockPos> taken = new HashSet<>();
		for (Villager a : archers) {
			Optional<BlockPos> post = BattleStations.post(a);
			String where = "at " + gate.drawing(a.blockPosition()) + " (area " + a.blockPosition().subtract(gate.hall()).toShortString() + " from the hall)" + ", sent to " + post.map(gate::drawing).orElse("nowhere");
			if (!BattleStations.onStation(a) || post.isEmpty()) {
				out.add("not on his station: " + where);
			} else if (!stations.contains(post.get())) {
				out.add("his station isn't the Gatehouse's: " + where);
			} else if (!BattleStations.standable(gate.level(), post.get())) {
				out.add("his station can't be stood on: " + where);
			} else if (a.getY() < post.get().getY() - 0.6) {
				out.add("below his station (y " + a.getY() + "): " + where);
			} else if (!taken.add(post.get())) {
				out.add("shares his station: " + where);
			}
		}
		return out;
	}

	/** Every archer is on a station of {@code build} within {@link #LIMIT} ticks of {@code from} (a tick). */
	private static void allUpBy(GameTestHelper helper, Gate gate, StarterBlueprints.Entry build, List<Villager> archers, long from, String what) {
		Set<BlockPos> stations = gate.stations(build);
		List<String> now = off(gate, archers, stations);
		if (now.isEmpty()) {
			helper.succeed();
		} else if (helper.getTick() > from + LIMIT) {
			helper.fail(what + ": archers not on a " + build.id().getPath() + " station within " + LIMIT + " ticks (drawing coordinates): " + now);
		}
	}

	private static void turned(GameTestHelper helper, Rotation rotation, Mirror mirror) {
		Gate gate = gate(helper, StarterBlueprints.GATEHOUSE_2, rotation, mirror);
		List<Villager> archers = archers(helper, gate, -2, 2, 8, 12);
		siege(helper, gate);
		helper.onEachTick(() -> allUpBy(helper, gate, StarterBlueprints.GATEHOUSE_2, archers, 0, "turned " + rotation + ", mirror " + mirror));
	}

	//$ gametest_ticks_batch AREA '600' '"qaGatehouseReach1122dCw90"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaGatehouseReach1122dCw90")
	public void qaGatehouseIITurnedClockwiseGetsFourArchersOntoStations(GameTestHelper helper) {
		turned(helper, Rotation.CLOCKWISE_90, Mirror.NONE);
	}

	//$ gametest_ticks_batch AREA '600' '"qaGatehouseReach1122d180"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaGatehouseReach1122d180")
	public void qaGatehouseIITurnedRoundGetsFourArchersOntoStations(GameTestHelper helper) {
		turned(helper, Rotation.CLOCKWISE_180, Mirror.NONE);
	}

	//$ gametest_ticks_batch AREA '600' '"qaGatehouseReach1122dCcw90Mirror"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaGatehouseReach1122dCcw90Mirror")
	public void qaGatehouseIITurnedAndMirroredGetsFourArchersOntoStations(GameTestHelper helper) {
		turned(helper, Rotation.COUNTERCLOCKWISE_90, Mirror.FRONT_BACK);
	}

	//$ gametest_ticks_batch AREA '600' '"qaGatehouseReach1122dMirror"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaGatehouseReach1122dMirror")
	public void qaGatehouseIIMirroredGetsFourArchersOntoStations(GameTestHelper helper) {
		turned(helper, Rotation.NONE, Mirror.FRONT_BACK);
	}

	/** Six archers at once on Gatehouse II as drawn: all six on stations they stand on, none sharing, within 450 ticks. */
	//$ gametest_ticks_batch AREA '600' '"qaGatehouseReach1122dSix"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaGatehouseReach1122dSix")
	public void qaSixArchersAllGetOntoGatehouseIIStations(GameTestHelper helper) {
		Gate gate = gate(helper, StarterBlueprints.GATEHOUSE_2, Rotation.NONE, Mirror.NONE);
		int stations = gate.stations(StarterBlueprints.GATEHOUSE_2).size();
		helper.assertTrue(stations >= 6, "(setup) Gatehouse II has only " + stations + " stations");
		List<Villager> archers = archers(helper, gate, -4, -1, 2, 8, 11, 14);
		siege(helper, gate);
		helper.onEachTick(() -> allUpBy(helper, gate, StarterBlueprints.GATEHOUSE_2, archers, 0, "six archers"));
	}

	/**
	 * A server restart mid-siege (the stations' memory and the siege directors' orders lost, the archers where they
	 * were): 60 ticks in, everything held in memory is forgotten; within 450 ticks after, all four are on stations.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaGatehouseReach1122dRestart"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaGatehouseReach1122dRestart")
	public void qaArchersRetakeGatehouseIIStationsAfterARestartMidSiege(GameTestHelper helper) {
		Gate gate = gate(helper, StarterBlueprints.GATEHOUSE_2, Rotation.CLOCKWISE_90, Mirror.NONE);
		List<Villager> archers = archers(helper, gate, -2, 2, 8, 12);
		siege(helper, gate);
		long restart = 60;
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick == restart) {
				BattleStations.forget();
				Sieges.forget();
				helper.assertTrue(archers.stream().noneMatch(BattleStations::stationed), "(setup) the restart left a station in memory");
			} else if (tick > restart) {
				allUpBy(helper, gate, StarterBlueprints.GATEHOUSE_2, archers, restart, "after the restart");
			}
		});
	}

	/**
	 * gatehouse.md: II is "an upgrade by name, built by a builder over a finished Gatehouse at the same origin and
	 * rotation". Two archers man Gatehouse I in a siege; once both are up, the Gatehouse turns into II where it stands
	 * (as the builder finishing the upgrade leaves it): within 450 ticks both are on stations of Gatehouse II.
	 */
	//$ gametest_ticks_batch AREA '1100' '"qaGatehouseReach1122dUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 1100, batch = "qaGatehouseReach1122dUpgrade")
	public void qaArchersOnGatehouseIStayOnStationsWhenItBecomesIIMidSiege(GameTestHelper helper) {
		Gate gate = gate(helper, StarterBlueprints.GATEHOUSE, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		List<Villager> archers = archers(helper, gate, 2, 8);
		siege(helper, gate);
		long[] upgraded = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (upgraded[0] < 0) {
				if (off(gate, archers, gate.stations(StarterBlueprints.GATEHOUSE)).isEmpty()) {
					upgraded[0] = tick;
					BuildSiteManager.get(gate.level()).forgetFinished(new BlueprintData.Placement(gate.level().dimension().location(), gate.origin(),
						gate.rotation(), gate.mirror()));
					stand(gate, StarterBlueprints.GATEHOUSE_2);
				} else if (tick > LIMIT) {
					helper.fail("(Gatehouse I) archers not on its stations within " + LIMIT + " ticks: " + off(gate, archers, gate.stations(StarterBlueprints.GATEHOUSE)));
				}
				return;
			}
			if (tick > upgraded[0] + 40) { // the station checks run every second; give them two
				allUpBy(helper, gate, StarterBlueprints.GATEHOUSE_2, archers, upgraded[0], "after the upgrade to II");
			}
		});
	}
}

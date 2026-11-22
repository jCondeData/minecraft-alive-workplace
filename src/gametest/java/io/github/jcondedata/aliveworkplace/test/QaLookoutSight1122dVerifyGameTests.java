package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (lane-d-1122-1609) verifying B1494, from the spec (sieges.md, "Battle stations"): an archer is sent to the best
 * station he can get onto; on the way he shoots only at what comes within 8 blocks and keeps walking while he does; a
 * station whose path stops nearer than 12 blocks but out of reach is passed over and he gets another (B1427: within 450
 * ticks). Village as QaLookoutSight1122dBugTests (hall north-west at (1, ground, 1), a Palisade at (0, ground, 25)), a
 * still raider near the tower's foot (6 out from its south face) but off the archer's walk. Covers the rotations and approaches the
 * filed tests don't: Lookout Tower I turned clockwise and half round, II approached from the far side of the area, III
 * half round from the south-west, and Lookout Tower I walled off so its stations can't be reached.
 */
public class QaLookoutSight1122dVerifyGameTests implements FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");

	private static void done(GameTestHelper helper, String what) {
		LOGGER.info("qa 1122d verify: {}", what);
		helper.succeed();
	}

	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_lookout_verify_1122d");
	private static final int BACK = 4;
	/** By when an archer passed over for a station he can't reach must stand on another (B1427). */
	private static final int REPLACED_BY = 450;

	private record Tower(ServerLevel level, BlockPos origin, int ground, int left, int width, int depth, int face, Set<BlockPos> top, BlockPos hall,
		Culture culture) {
	}

	/** As QaLookoutSight1122dBugTests#tower: {@code build} finished, turned by {@code rotation}, footprint from z = BACK, centred in x. */
	private static Tower tower(GameTestHelper helper, StarterBlueprints.Entry build, Rotation rotation) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int lift = build.basement();
		int ground = 2 + lift;
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 40;
		helper.setDayTime(6000);
		BlockPos hall = helper.absolutePos(new BlockPos(1, ground, 1));
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
			placed.forEach(p -> BuildSiteManager.get(level).forgetFinished(p));
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		BlockPos a = StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotation, BlockPos.ZERO);
		BlockPos b = StructureTemplate.transform(new BlockPos(build.size().getX() - 1, 0, build.size().getZ() - 1), Mirror.NONE, rotation, BlockPos.ZERO);
		int width = Math.abs(a.getX() - b.getX()) + 1;
		int depth = Math.abs(a.getZ() - b.getZ()) + 1;
		int left = (30 - width) / 2;
		BlockPos origin = helper.absolutePos(new BlockPos(left - Math.min(a.getX(), b.getX()), ground, BACK - Math.min(a.getZ(), b.getZ())));
		BlockPos file = origin.below(lift);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, file, file, new StructurePlaceSettings().setRotation(rotation),
			RandomSource.create(1L), 2);
		if (lift > 0) {
			BlockPos base = helper.absolutePos(BlockPos.ZERO);
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					boolean inside = x >= left && x < left + width && z >= BACK && z < BACK + depth;
					for (int y = 2; y < ground && !inside; y++) {
						level.setBlockAndUpdate(base.offset(x, y, z), Blocks.DIRT.defaultBlockState());
					}
				}
			}
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, rotation, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(build.id(), placement, UUID.randomUUID());
		placed.add(placement);
		BlockPos wall = helper.absolutePos(new BlockPos(0, ground, 25));
		level.getStructureManager().get(StarterBlueprints.PALISADE.id()).orElseThrow().placeInWorld(level, wall, wall, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BlueprintData.Placement palisade = new BlueprintData.Placement(level.dimension().location(), wall, Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.PALISADE.id(), palisade, UUID.randomUUID());
		placed.add(palisade);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		Set<BlockPos> top = new HashSet<>();
		for (BlockPos s : BattleStations.spots(BlueprintLibrary.get(level, build.id()).orElseThrow())) {
			top.add(origin.offset(StructureTemplate.transform(s, Mirror.NONE, rotation, BlockPos.ZERO)).above());
		}
		helper.assertTrue(!top.isEmpty(), "(setup) " + build.id().getPath() + " has no stations");
		return new Tower(level, origin, ground, left, width, depth, BACK + depth - 1, top, hall, culture);
	}

	/** One archer guard (bow in the off hand) employed at a Guard Post at {@code post}, standing at {@code at}. */
	private static Villager archer(GameTestHelper helper, Tower tower, BlockPos post, BlockPos at) {
		helper.setBlock(post, ModBlocks.GUARD_POST);
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		Jobs.employ(tower.level(), v, helper.absolutePos(post), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		return v;
	}

	/** A still raider with 500 health {@code out} south of the tower's south face, in line with its stations' middle: the siege starts. */
	private static Mob raider(GameTestHelper helper, Tower tower, int out) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		return raider(helper, tower, (int) Math.round(tower.top().stream().mapToInt(BlockPos::getX).average().orElseThrow()) - base.getX(), tower.face() + out);
	}

	/**
	 * A still raider at relative ({@code fx}, ground, {@code fz}). Placed off the archer's walk: a foe within 4.5 blocks
	 * is fought with the sword (archers defend themselves up close), and a raider with 500 health standing in the
	 * tower's doorway would hold him there all test, which no real raider does.
	 */
	private static Mob raider(GameTestHelper helper, Tower tower, int fx, int fz) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		helper.assertTrue(fz <= 29 && fx >= 0 && fx <= 29, "(setup) the raider at " + fx + ", " + fz + " is off the area");
		ServerLevel level = tower.level();
		Mob foe = EntityType.HUSK.create(level);
		foe.moveTo(base.getX() + fx + 0.5, base.getY() + tower.ground(), base.getZ() + fz + 0.5, 0f, 0f);
		foe.addTag(VillageRaids.TAG);
		foe.setNoAi(true);
		foe.setPersistenceRequired();
		foe.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
		foe.setHealth(500);
		level.addFreshEntity(foe);
		VillageRaids.track(level, tower.hall(), tower.culture(), List.of(foe));
		helper.assertTrue(Sieges.siege(level, tower.hall()).isPresent(), "(setup) no siege on the village");
		return foe;
	}

	private static String rel(GameTestHelper helper, BlockPos pos) {
		return pos.subtract(helper.absolutePos(BlockPos.ZERO)).toShortString();
	}

	/**
	 * Within 500 ticks the archer stands on a station of the tower's top; then, when {@code hit}, within 600 more he hits
	 * the raider from there, else he is still on the tower's top 100 ticks later. (Which station he takes goes by its view
	 * of the breach, not of this raider, so from far ends of the deck the raider can be out of his sight.)
	 */
	private static void upAndHits(GameTestHelper helper, Tower tower, Villager archer, Mob foe, boolean hit, String what) {
		long[] up = {-1};
		float[] health = {foe.getHealth()};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (up[0] < 0) {
				if (BattleStations.onStation(archer)) {
					up[0] = tick;
					BlockPos post = BattleStations.post(archer).orElseThrow();
					helper.assertTrue(tower.top().contains(post), what + ": the station " + rel(helper, post) + " isn't on the tower's top");
				} else if (tick > 500) {
					helper.fail(what + ": the archer isn't on the tower's top within 500 ticks: at " + helper.relativeVec(archer.position()) + ", sent to "
						+ BattleStations.post(archer).map(p -> rel(helper, p)));
				}
				return;
			}
			if (!hit) {
				helper.assertTrue(BattleStations.onStation(archer) && tower.top().contains(BattleStations.post(archer).orElseThrow()),
					what + ": the archer left the tower's top at tick " + tick + ", at " + helper.relativeVec(archer.position()));
				if (tick >= up[0] + 100) {
					done(helper, what + ": on " + BattleStations.post(archer).map(p -> rel(helper, p)).orElse("?") + " from tick " + up[0] + " to " + tick);
				}
				return;
			}
			if (foe.getHealth() < health[0]) {
				LivingEntity by = foe.getLastHurtByMob();
				if (by == archer) {
					helper.assertTrue(BattleStations.onStation(archer), what + ": the archer hit the raider off his station, at " + helper.relativeVec(archer.position()));
					done(helper, what + ": on " + BattleStations.post(archer).map(p -> rel(helper, p)).orElse("?") + " at tick " + up[0] + ", hit at " + tick);
				}
				health[0] = foe.getHealth();
			}
			if (tick > up[0] + 600) {
				helper.fail(what + ": the raider at " + helper.relativeVec(foe.position()) + " was never hit from "
					+ BattleStations.post(archer).map(p -> rel(helper, p)).orElse("none") + String.format(" (%.1f off, in sight %s)",
					Math.sqrt(archer.distanceToSqr(foe)), archer.hasLineOfSight(foe)));
			}
		});
	}

	private static void live(GameTestHelper helper, StarterBlueprints.Entry build, Rotation rotation, BlockPos post, BlockPos at, BlockPos foeAt, boolean hit, String what) {
		Tower tower = tower(helper, build, rotation);
		Villager archer = archer(helper, tower, new BlockPos(post.getX(), tower.ground(), post.getZ()), new BlockPos(at.getX(), tower.ground(), at.getZ()));
		Mob foe = foeAt == null ? raider(helper, tower, 6) : raider(helper, tower, foeAt.getX(), foeAt.getZ());
		upAndHits(helper, tower, archer, foe, hit, build.id().getPath() + " turned " + rotation + ", " + what);
	}

	/** Lookout Tower I turned clockwise, the archer's post south-east as in the filed case, a raider 6 out. */
	//$ gametest_ticks_batch AREA '1300' '"qaLookoutVerify1122dIcw"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaLookoutVerify1122dIcw")
	public void qaAnArcherGetsUpLookoutTowerITurnedClockwise(GameTestHelper helper) {
		live(helper, StarterBlueprints.LOOKOUT_TOWER, Rotation.CLOCKWISE_90, new BlockPos(27, 0, 22), new BlockPos(27, 0, 23), null, true, "post south-east, a raider 6 out");
	}

	/** Lookout Tower I turned half round, the archer's post south-west (he comes at it from the other side), a raider 6 out at the south-east corner. */
	//$ gametest_ticks_batch AREA '1300' '"qaLookoutVerify1122dI180"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaLookoutVerify1122dI180")
	public void qaAnArcherFromTheSouthWestGetsUpLookoutTowerITurnedHalfRound(GameTestHelper helper) {
		live(helper, StarterBlueprints.LOOKOUT_TOWER, Rotation.CLOCKWISE_180, new BlockPos(9, 0, 22), new BlockPos(9, 0, 23), new BlockPos(24, 0, 18), true, "post south-west, a raider 6 out at the south-east corner");
	}

	/**
	 * Lookout Tower II, the archer's post in the south-east corner but the archer himself standing at the far south-west
	 * (8, 28), so he crosses the whole area to the tower; a raider 6 out at the east end of the tower's foot.
	 * He must be on the tower's top within 500 ticks and still there 100 ticks later.
	 */
	//$ gametest_ticks_batch AREA '1300' '"qaLookoutVerify1122dIIfar"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaLookoutVerify1122dIIfar")
	public void qaAnArcherFromFarAwayGetsUpLookoutTowerII(GameTestHelper helper) {
		live(helper, StarterBlueprints.LOOKOUT_TOWER_2, Rotation.NONE, new BlockPos(27, 0, 27), new BlockPos(8, 0, 28), new BlockPos(24, 0, 18), false, "from the far south-west, a raider 6 out to the east");
	}

	/** Lookout Tower III turned half round, the archer's post south-west, starting at the area's
	 * south-east corner: on the top within 500 ticks and still there 100 ticks later. */
	//$ gametest_ticks_batch AREA '1300' '"qaLookoutVerify1122dIII"'
	@GameTest(template = AREA, timeoutTicks = 1300, batch = "qaLookoutVerify1122dIII")
	public void qaAnArcherFromFarAwayGetsUpLookoutTowerIIITurnedHalfRound(GameTestHelper helper) {
		live(helper, StarterBlueprints.LOOKOUT_TOWER_3, Rotation.CLOCKWISE_180, new BlockPos(9, 0, 22), new BlockPos(28, 0, 28), new BlockPos(3, 0, 20), false, "from the far south-east, a raider off the west end");
	}

	/**
	 * Lookout Tower I walled round (stone bricks five high, two out from its footprint, no way in), so its stations are out
	 * of reach and a path to them stops at the ring, under 12 blocks off. The archer, posted between the tower and the
	 * Palisade, must be passed over for them and stand on another station (the Palisade's) within 450 ticks.
	 */
	//$ gametest_ticks_batch AREA '700' '"qaLookoutVerify1122dWalledOff"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "qaLookoutVerify1122dWalledOff")
	public void qaAnArcherWhoCantReachTheTowerTakesAnotherStationWithin450Ticks(GameTestHelper helper) {
		Tower tower = tower(helper, StarterBlueprints.LOOKOUT_TOWER, Rotation.NONE);
		int west = tower.left() - 2;
		int east = tower.left() + tower.width() + 1;
		int north = BACK - 2;
		int south = tower.face() + 2;
		for (int y = 2; y <= 6; y++) {
			for (int x = west; x <= east; x++) {
				helper.setBlock(new BlockPos(x, y, north), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(x, y, south), Blocks.STONE_BRICKS);
			}
			for (int z = north; z <= south; z++) {
				helper.setBlock(new BlockPos(west, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(east, y, z), Blocks.STONE_BRICKS);
			}
		}
		Villager archer = archer(helper, tower, new BlockPos(9, 2, 21), new BlockPos(9, 2, 22));
		raider(helper, tower, 25, 20);
		Set<String> sent = new HashSet<>();
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			Optional<BlockPos> post = BattleStations.post(archer);
			post.ifPresent(p -> sent.add((tower.top().contains(p) ? "tower " : "") + rel(helper, p)));
			if (BattleStations.onStation(archer)) {
				BlockPos on = post.orElseThrow();
				helper.assertTrue(!tower.top().contains(on), "the archer stands on the walled-off tower's station " + rel(helper, on));
				done(helper, "on " + rel(helper, on) + " at tick " + tick + "; sent to " + sent);
			} else if (tick > REPLACED_BY) {
				helper.fail("not on any station within " + REPLACED_BY + " ticks: at " + helper.relativeVec(archer.position()) + ", sent to " + post.map(p -> rel(helper, p))
					+ "; all sent to " + sent);
			}
		});
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Ladders;
import io.github.jcondedata.aliveworkplace.threat.SandRamps;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import java.util.LinkedHashMap;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-11-03), verifying B330 and B331 from their text with a ramp laid by hand (as QaSandRampEdgeGameTests
 * does) and husks standing still, so where each stands is exact: "a raider standing on an inner step is let go to fight
 * inside" (a bottom slab or a stair's lower step at foot+1, its top at foot+1.5); "a raider past the wall on any inner
 * block is let go or pushed somewhere it can reach" (a top slab, and B331's first text: a full block at foot+1 in the
 * first column past the wall). The control: a husk held to the ramp but still outside is never counted over.
 */
public class QaSandRampSteps1103d2GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_sand_steps_pilers");
	private static final BlockPos HALL = new BlockPos(14, 2, 8);
	private static final int WEST = 5;
	private static final int EAST = 23;
	private static final int BACK = 4;
	private static final int FRONT = 15;

	private static Culture pilers(GameTestHelper helper) {
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 60, \"role\": \"climber\"}, {\"entity\": \"minecraft:husk\", \"share\": 40, \"role\": \"melee\"}],"
			+ " \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> Threats.setForTest(loaded));
		return culture;
	}

	/** The desert tests' yard: stone brick walls four high, a finished Stone Wall in the front one, a villager shut in a cell. */
	private static Villager yard(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		boolean griefing = level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
		VillageHalls.RADIUS = 20;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Ladders.forget();
			SandRamps.forget();
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(griefing, level.getServer());
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				if (x < 11 || x > 17) {
					helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
				}
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
		BlockPos origin = helper.absolutePos(new BlockPos(11, 2, FRONT - 2));
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin,
			new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		for (int x = 9; x <= 11; x++) {
			for (int z = 6; z <= 8; z++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(x, y, z), x == 10 && z == 7 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		return helper.spawn(EntityType.VILLAGER, new BlockPos(10, 2, 7));
	}

	/** A melee husk of the culture standing still at (x, 2, z): it never walks, so where it stands is exact. */
	private static Mob still(GameTestHelper helper, Culture culture, Villager villager, double x, double y, double z) {
		ServerLevel level = helper.getLevel();
		Culture.Member member = culture.roster().stream().filter(m -> m.role() == Culture.Role.MELEE).findFirst().orElseThrow();
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x, base.getY() + y, base.getZ() + z, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, culture, member);
		if (mob.isBaby()) {
			((net.minecraft.world.entity.monster.Zombie) mob).setBaby(false);
		}
		mob.ejectPassengers();
		mob.stopRiding();
		mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
		mob.setTarget(villager);
		level.addFreshEntity(mob);
		return mob;
	}

	/**
	 * The ramp of the edge test against the front wall at x = 7 (its foot outside at z = 16, four high, the column it
	 * drops into z = 14), {@code blocks} laid inside, and a still husk of the culture at (x, y, z) held to it. With
	 * {@code over}, it must be let go as over the wall by tick 150 while standing at {@code y}; without, never by tick 120.
	 */
	private static void standing(GameTestHelper helper, Map<BlockPos, BlockState> blocks, double x, double y, double z, boolean over, String what) {
		Culture culture = pilers(helper);
		Villager villager = yard(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		blocks.forEach((pos, state) -> helper.setBlock(pos, state));
		Mob husk = still(helper, culture, villager, x, y, z);
		VillageRaids.track(level, hall, culture, List.of(husk));
		Optional<ThreatData.Siege> laid = Sieges.siege(level, hall);
		helper.assertTrue(laid.isPresent(), "no siege on the yard");
		ThreatData.Siege siege = laid.get();
		BlockPos foot = helper.absolutePos(new BlockPos(7, 2, 16));
		for (int s = 1; s <= 4; s++) {
			for (int d = s; d >= 1; d--) {
				BlockPos sand = foot.relative(Direction.NORTH, -(d - 1)).above(s - d);
				level.setBlock(sand, Blocks.SAND.defaultBlockState(), 3);
				siege.sand.add(sand);
			}
		}
		siege.rampFeet.add(foot);
		siege.rampDirs.add(Direction.NORTH.get3DDataValue());
		siege.ramped = true;
		SandRamps.forget();
		helper.onEachTick(() -> {
			BlockPos base = helper.absolutePos(BlockPos.ZERO);
			String where = "the husk " + what + " at " + husk.position().subtract(base.getX(), base.getY(), base.getZ())
				+ (husk.onGround() ? " on the ground" : " in the air") + " at tick " + helper.getTick();
			boolean let = SandRamps.over(level, hall, husk.getUUID());
			if (over) {
				if (let) {
					helper.assertTrue(Math.abs(husk.getY() - (base.getY() + y)) < 1.0E-6,
						"let go, but not standing where it was put (test setup?): " + where);
					helper.succeed();
				} else if (helper.getTick() >= 150) {
					helper.fail("never let go as over the wall: " + where);
				}
			} else {
				helper.assertFalse(let, "counted over the wall by the ramp: " + where);
				if (helper.getTick() >= 120) {
					helper.assertTrue(husk.isAlive(), "the husk is gone: " + where);
					helper.succeed();
				}
			}
		});
	}

	private static Map<BlockPos, BlockState> fill(BlockState state, int zFrom, int zTo, int y) {
		Map<BlockPos, BlockState> out = new LinkedHashMap<>();
		for (int x = 6; x <= 8; x++) {
			for (int z = zFrom; z <= zTo; z++) {
				out.put(new BlockPos(x, y, z), state);
			}
		}
		return out;
	}

	/** B330: standing still on a bottom slab one above the ramp's foot (its top at the foot + 1.5), 3 in: let go. */
	//$ gametest_ticks_batch AREA '200' '"qa1103d2Slab"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1103d2Slab")
	public void aHuskStandingOnAnInnerBottomSlabIsLetGo(GameTestHelper helper) {
		standing(helper, fill(Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState(), 12, 14, 3), 7.5, 3.5, 13.5, true, "on a bottom slab");
	}

	/**
	 * B330's "a stair's lower step at foot+1": standing on the low half of stairs one above the foot (rising away from the
	 * wall, the step's top at the foot + 1.5): let go.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1103d2Stair"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1103d2Stair")
	public void aHuskStandingOnAStairsLowerStepIsLetGo(GameTestHelper helper) {
		Map<BlockPos, BlockState> blocks = fill(Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState(), 14, 14, 3);
		blocks.putAll(fill(Blocks.SANDSTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM), 13, 13, 3));
		standing(helper, blocks, 7.5, 3.5, 13.85, true, "on a stair's lower step");
	}

	/** The step's other side: a top slab one above the foot (its top at the foot + 2), 3 in: let go too. */
	//$ gametest_ticks_batch AREA '200' '"qa1103d2TopSlab"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1103d2TopSlab")
	public void aHuskStandingOnAnInnerTopSlabIsLetGo(GameTestHelper helper) {
		standing(helper, fill(Blocks.SMOOTH_SANDSTONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), 12, 14, 3), 7.5, 4, 13.5, true,
			"on a top slab");
	}

	/** B331 as first written: a full block at foot+1 in the first column past the wall alone, the husk standing on it: let go. */
	//$ gametest_ticks_batch AREA '200' '"qa1103d2FirstBlock"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1103d2FirstBlock")
	public void aHuskStandingOnAFullBlockInTheFirstColumnPastTheWallIsLetGo(GameTestHelper helper) {
		standing(helper, fill(Blocks.SANDSTONE.defaultBlockState(), 14, 14, 3), 7.5, 4, 14.5, true, "on a full block in the first column past the wall");
	}

	/** The control: a husk held to the ramp but still outside, on the ground past the ramp's foot, is never counted over. */
	//$ gametest_ticks_batch AREA '200' '"qa1103d2Outside"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1103d2Outside")
	public void aHuskStillOutsideTheWallIsNotLetGo(GameTestHelper helper) {
		standing(helper, new LinkedHashMap<>(), 7.5, 2, 21.5, false, "outside the wall");
	}
}

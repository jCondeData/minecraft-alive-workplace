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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane D, 2026-11-21) for B1419, written from the bug's text: "only the village's own wall piece beside the husk (the
 * one the ramp leans on, inside its span) counts"; the fix "follows the wall along its line from the block the ramp leans
 * on to the one beside the raider (at most 32 blocks) and needs it unbroken (foot height or one above)".
 *
 * <p>Each test fixes the ramp where it is asked about: the siege's two ramp feet are put in its saved data before the
 * first step (as after a load), the first outside the yard's west wall, so every climber goes to it. Each husk waits in
 * a player's closed glass cell (a pen it can't leave, so its way to the hall ends there and it is a climber at that ramp)
 * in from the ramp's foot, along the wall's line where the test puts it. A husk in from the ramp with the ramp's wall
 * unbroken beside it is behind the wall and is counted over; one with a gap in the wall between, or farther along than
 * 32 blocks, is not, until the wall is mended.
 */
public class QaWallRamps1121dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation PILERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_wall_ramps_1121d");
	/** The yard: walls four high at x 8 and 26, z 1 (the back) and 19 (the front), closed all round. */
	private static final int WEST = 8;
	private static final int EAST = 26;
	private static final int BACK = 1;
	private static final int FRONT = 19;
	private static final BlockPos HALL = new BlockPos(20, 2, 10);
	/** The ramp everyone uses: its foot outside the west wall, the wall to the east. */
	private static final BlockPos FOOT = new BlockPos(WEST - 1, 2, 4);
	/** The siege's second ramp (so a climber is never given a new one of its own): outside the east wall. */
	private static final BlockPos OTHER = new BlockPos(EAST + 1, 2, 10);
	/** How long a husk that must not be counted over is watched. */
	private static final int HOLD = 200;
	/** By when one that must be counted over is. */
	private static final int OVER_BY = 200;

	private record Yard(ServerLevel level, BlockPos hall, Culture culture) {
	}

	/** Clears the area, sets a small village radius and a culture of husks piling sand, and puts it all back at the end. */
	private static Yard village(GameTestHelper helper, BlockPos hallAt) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(hallAt);
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 20;
		Culture culture = Culture.read(PILERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"climber\"}], \"hours\": \"until_noon\", \"tactics\": [\"sand_ramps\"]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new LinkedHashMap<>(loaded);
		with.put(PILERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			Sieges.forget();
			Ladders.forget();
			SandRamps.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		return new Yard(level, hall, culture);
	}

	/** The closed yard, stone brick walls four high. */
	private static void walls(GameTestHelper helper) {
		for (int y = 2; y <= 5; y++) {
			for (int x = WEST; x <= EAST; x++) {
				helper.setBlock(new BlockPos(x, y, BACK), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(x, y, FRONT), Blocks.STONE_BRICKS);
			}
			for (int z = BACK; z <= FRONT; z++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				helper.setBlock(new BlockPos(EAST, y, z), Blocks.STONE_BRICKS);
			}
		}
	}

	private static final Map<GameTestHelper, java.util.Set<BlockPos>> KEPT = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	/** Sets {@code block} at helper {@code pos} (outside the area too), keeping what was there to put back when the test ends. */
	private static void setKept(GameTestHelper helper, BlockPos pos, Block block) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(pos);
		BlockState was = level.getBlockState(abs);
		if (KEPT.computeIfAbsent(helper, h -> new java.util.HashSet<>()).add(abs)) {
			Leftovers.after(helper, () -> level.setBlock(abs, was, 2));
		}
		level.setBlock(abs, block.defaultBlockState(), 2);
	}

	/** A player's glass cell round the husk's block at ({@code x}, 2, {@code z}): its four sides two high and a roof; blocks already there stay. */
	private static void cell(GameTestHelper helper, int x, int z, boolean keep) {
		BlockPos[] sides = {new BlockPos(x - 1, 2, z), new BlockPos(x + 1, 2, z), new BlockPos(x, 2, z - 1), new BlockPos(x, 2, z + 1)};
		for (BlockPos side : sides) {
			for (BlockPos p : new BlockPos[] {side, side.above()}) {
				if (helper.getLevel().getBlockState(helper.absolutePos(p)).isAir()) {
					if (keep) {
						setKept(helper, p, Blocks.GLASS);
					} else {
						helper.setBlock(p, Blocks.GLASS);
					}
				}
			}
		}
		if (keep) {
			setKept(helper, new BlockPos(x, 4, z), Blocks.GLASS);
		} else {
			helper.setBlock(new BlockPos(x, 4, z), Blocks.GLASS);
		}
	}

	/** A husk of the siege's culture in its cell at ({@code x}, 2, {@code z}). */
	private static Mob husk(GameTestHelper helper, Yard yard, int x, int z) {
		ServerLevel level = yard.level();
		Culture.Member member = yard.culture().roster().get(0);
		Mob mob = Threats.create(level, member);
		helper.assertTrue(mob != null, "no " + member.entity());
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		mob.moveTo(base.getX() + x + 0.5, base.getY() + 2, base.getZ() + z + 0.5, 0f, 0f);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.EVENT, null);
		Threats.outfit(level, mob, yard.culture(), member);
		// B1528: finalizeSpawn makes one zombie-kind in 20 a baby (the level's random), now and then on a chicken: a baby
		// walks in under the walls or rides off on its chicken instead of piling a ramp. Always an adult on foot.
		if (mob instanceof net.minecraft.world.entity.monster.Zombie zombie && zombie.isBaby()) {
			zombie.setBaby(false);
		}
		net.minecraft.world.entity.Entity vehicle = mob.getVehicle();
		if (vehicle != null) {
			mob.stopRiding();
			vehicle.discard();
			mob.moveTo(base.getX() + x + 0.5, base.getY() + 2, base.getZ() + z + 0.5, 0f, 0f);
		}
		mob.setPersistenceRequired();
		level.addFreshEntityWithPassengers(mob);
		return mob;
	}

	/** Starts the siege with {@code husks} and puts the two ramps' feet in its saved data, {@link #FOOT} (wall to the east) first. */
	private static ThreatData.Siege siege(GameTestHelper helper, Yard yard, List<Mob> husks, BlockPos foot, BlockPos other) {
		// The village's one villager, in a closed stone cell by the hall (two blocks east of it).
		BlockPos hall = yard.hall().subtract(helper.absolutePos(BlockPos.ZERO));
		BlockPos v = hall.east(2);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				for (int y = 2; y <= 4; y++) {
					helper.setBlock(new BlockPos(v.getX() + dx, y, v.getZ() + dz), dx == 0 && dz == 0 && y < 4 ? Blocks.AIR : Blocks.STONE_BRICKS);
				}
			}
		}
		helper.spawn(net.minecraft.world.entity.EntityType.VILLAGER, v);
		// A finished defence (a siege comes only to a village with one): a Stone Wall standing in the yard's front wall,
		// or out in the open by the long wall.
		defence(helper, other.getX() > 26 && other.getZ() == 15 ? new BlockPos(14, 2, 24) : new BlockPos(14, 2, FRONT - 2));
		VillageRaids.track(yard.level(), yard.hall(), yard.culture(), husks);
		ThreatData.Siege siege = Sieges.siege(yard.level(), yard.hall()).orElse(null);
		helper.assertTrue(siege != null, "no siege on the village");
		siege.rampFeet.add(helper.absolutePos(foot));
		siege.rampDirs.add(Direction.EAST.get3DDataValue());
		siege.rampFeet.add(helper.absolutePos(other));
		siege.rampDirs.add(Direction.WEST.get3DDataValue());
		ThreatData.get(yard.level()).setDirty();
		return siege;
	}

	/** A finished Stone Wall with its corner at helper {@code at}, recorded as built. */
	private static void defence(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(StarterBlueprints.STONE_WALL.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(),
			RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(StarterBlueprints.STONE_WALL.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	private static boolean over(Yard yard, Mob husk) {
		return SandRamps.over(yard.level(), yard.hall(), husk.getUUID());
	}

	private static String where(GameTestHelper helper, Mob mob) {
		BlockPos base = helper.absolutePos(BlockPos.ZERO);
		Vec3 p = mob.position().subtract(base.getX(), base.getY(), base.getZ());
		return String.format("(%.2f, %.2f, %.2f)", p.x, p.y, p.z);
	}

	/** Every husk in {@code over} is counted over by {@link #OVER_BY}, every one in {@code notOver} never in {@link #HOLD} ticks. */
	private static void expect(GameTestHelper helper, Yard yard, String what, List<Mob> over, List<Mob> notOver) {
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			for (Mob m : notOver) {
				helper.assertTrue(!over(yard, m), what + ": the husk at " + where(helper, m) + " is counted over the wall at tick " + tick);
			}
			for (Mob m : over) {
				helper.assertTrue(m.isAlive(), what + ": the husk at " + where(helper, m) + " died");
			}
			boolean all = over.stream().allMatch(m -> over(yard, m));
			if (all && tick >= HOLD) {
				helper.succeed();
			} else if (tick > Math.max(OVER_BY, HOLD)) {
				List<String> not = new ArrayList<>();
				over.stream().filter(m -> !over(yard, m)).forEach(m -> not.add(where(helper, m)));
				helper.fail(what + ": not counted over by tick " + tick + ", the husks at " + not);
			}
		});
	}

	/**
	 * The happy side: husks in from the ramp, behind the village's own wall, which stands unbroken beside them: 8 blocks
	 * along it from the ramp (z 12), right by the yard's front corner (z 18, 14 along) and 2 the other way (z 2, past the
	 * ramp toward the back corner). Each is counted over.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWallRamps1121dBehind"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWallRamps1121dBehind")
	public void qaHusksBehindTheRampsOwnUnbrokenWallAreCountedOver(GameTestHelper helper) {
		Yard yard = village(helper, HALL);
		walls(helper);
		List<Mob> husks = new ArrayList<>();
		for (int z : new int[] {12, 18, 2}) {
			cell(helper, WEST + 2, z, false);
			husks.add(husk(helper, yard, WEST + 2, z));
		}
		siege(helper, yard, husks, FOOT, OTHER);
		expect(helper, yard, "behind the ramp's own wall", husks, List.of());
	}

	/**
	 * A gap of one block in the ramp's wall between the ramp and the husk (z 8, the full height of the wall: a doorway
	 * left open) means the husk beside the wall farther on is beside another piece of wall, not behind the ramp's: it
	 * isn't counted over, not even across a save and load of the siege (tick 100). A gap beyond the husk (z 15) changes
	 * nothing. Once the player fills the gap at the foot (tick 220), the wall is unbroken again and it is counted over.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaWallRamps1121dGap"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaWallRamps1121dGap")
	public void qaAOneBlockGapBetweenTheRampAndTheHuskKeepsItFromBeingCountedOverUntilMended(GameTestHelper helper) {
		Yard yard = village(helper, HALL);
		walls(helper);
		for (int y = 2; y <= 5; y++) {
			helper.setBlock(new BlockPos(WEST, y, 8), Blocks.AIR);
			helper.setBlock(new BlockPos(WEST, y, 15), Blocks.AIR);
		}
		cell(helper, WEST + 2, 12, false);
		Mob husk = husk(helper, yard, WEST + 2, 12);
		siege(helper, yard, List.of(husk), FOOT, OTHER);
		long[] mended = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (tick == 100) {
				ThreatData.get(yard.level()).roundTrip(yard.level().registryAccess());
				SandRamps.forget();
			}
			if (mended[0] < 0) {
				helper.assertTrue(!over(yard, husk), "the husk at " + where(helper, husk) + " beside the wall past the open gap at z 8 is counted over at tick " + tick
					+ (tick > 100 ? " (after a save and load)" : ""));
				if (tick >= 220) {
					helper.setBlock(new BlockPos(WEST, 2, 8), Blocks.COBBLESTONE);
					mended[0] = tick;
				}
				return;
			}
			if (over(yard, husk)) {
				helper.succeed();
			} else if (tick > mended[0] + 200) {
				helper.fail("the gap at z 8 was filled at the foot at tick " + mended[0] + " and the husk at " + where(helper, husk) + " still isn't counted over at tick " + tick);
			}
		});
	}

	/**
	 * The wall unbroken from the ramp up to the husk but open right beside it (z 12, the husk's own row: a doorway it
	 * stands in from): the wall doesn't reach where the husk is, so it isn't counted over; once the doorway is filled at
	 * the foot (tick 220) it is.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaWallRamps1121dBeside"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaWallRamps1121dBeside")
	public void qaAGapRightBesideTheHuskKeepsItFromBeingCountedOverUntilFilled(GameTestHelper helper) {
		Yard yard = village(helper, HALL);
		walls(helper);
		for (int y = 2; y <= 5; y++) {
			helper.setBlock(new BlockPos(WEST, y, 12), Blocks.AIR);
		}
		cell(helper, WEST + 2, 12, false);
		Mob husk = husk(helper, yard, WEST + 2, 12);
		siege(helper, yard, List.of(husk), FOOT, OTHER);
		long[] filled = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (filled[0] < 0) {
				helper.assertTrue(!over(yard, husk), "the husk at " + where(helper, husk) + " is counted over at tick " + tick + " with the wall open right beside it (z 12)");
				if (tick >= 220) {
					helper.setBlock(new BlockPos(WEST, 2, 12), Blocks.STONE_BRICKS);
					filled[0] = tick;
				}
				return;
			}
			if (over(yard, husk)) {
				helper.succeed();
			} else if (tick > filled[0] + 200) {
				helper.fail("the doorway beside the husk was filled at tick " + filled[0] + " and the husk at " + where(helper, husk) + " still isn't counted over at tick " + tick);
			}
		});
	}

	/**
	 * The ramp's wall made of whatever the player had: from the ramp to the husk, an oak fence, a cobblestone wall, a
	 * piece open at the foot with stone one above (a hole under a lintel), a bottom slab, a glass pane and a single stone
	 * block, each only that high. Every piece stands at the foot's height or one above, so the wall is unbroken and the
	 * husk beside it is counted over.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWallRamps1121dMixed"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWallRamps1121dMixed")
	public void qaAWallOfMixedBlocksAtTheFootOrOneAboveCountsAsUnbroken(GameTestHelper helper) {
		Yard yard = village(helper, HALL);
		walls(helper);
		Block[] foot = {Blocks.OAK_FENCE, Blocks.COBBLESTONE_WALL, Blocks.AIR, Blocks.STONE_SLAB, Blocks.GLASS_PANE, Blocks.STONE};
		for (int i = 0; i < foot.length; i++) {
			int z = 5 + i;
			for (int y = 2; y <= 5; y++) {
				helper.setBlock(new BlockPos(WEST, y, z), Blocks.AIR);
			}
			helper.setBlock(new BlockPos(WEST, 2, z), foot[i]);
			if (foot[i] == Blocks.AIR) {
				helper.setBlock(new BlockPos(WEST, 3, z), Blocks.STONE);
			}
		}
		cell(helper, WEST + 2, 12, false);
		Mob husk = husk(helper, yard, WEST + 2, 12);
		siege(helper, yard, List.of(husk), FOOT, OTHER);
		expect(helper, yard, "a wall of fence, wall, lintel, slab, pane and stone", List.of(husk), List.of());
	}

	/**
	 * A piece of the wall between the ramp and the husk standing only two above the foot and higher (z 8: open at the
	 * foot and one above, an arch a mob walks through) is a gap: the husk isn't counted over. With stone put one above
	 * the foot there (tick 220), the wall is unbroken and it is.
	 */
	//$ gametest_ticks_batch AREA '600' '"qaWallRamps1121dArch"'
	@GameTest(template = AREA, timeoutTicks = 600, batch = "qaWallRamps1121dArch")
	public void qaAPieceOnlyTwoAboveTheFootIsAGapUntilOneAboveIsFilled(GameTestHelper helper) {
		Yard yard = village(helper, HALL);
		walls(helper);
		helper.setBlock(new BlockPos(WEST, 2, 8), Blocks.AIR);
		helper.setBlock(new BlockPos(WEST, 3, 8), Blocks.AIR);
		cell(helper, WEST + 2, 12, false);
		Mob husk = husk(helper, yard, WEST + 2, 12);
		siege(helper, yard, List.of(husk), FOOT, OTHER);
		long[] filled = {-1};
		helper.onEachTick(() -> {
			long tick = helper.getTick();
			if (filled[0] < 0) {
				helper.assertTrue(!over(yard, husk), "the husk at " + where(helper, husk) + " is counted over at tick " + tick + " past an arch open at the foot and one above (z 8)");
				if (tick >= 220) {
					helper.setBlock(new BlockPos(WEST, 3, 8), Blocks.STONE);
					filled[0] = tick;
				}
				return;
			}
			if (over(yard, husk)) {
				helper.succeed();
			} else if (tick > filled[0] + 200) {
				helper.fail("stone was put one above the foot in the arch at tick " + filled[0] + " and the husk at " + where(helper, husk) + " still isn't counted over at tick " + tick);
			}
		});
	}

	/**
	 * A long straight wall (two high, x 8, from the ramp at z -4 to z 31, past the area's edges both ways, its ground laid
	 * out there): husks beside it 29 and 32 blocks along from the block the ramp leans on (z 25 and 28) are within the 32
	 * the wall is followed and are counted over; one 33 along (z 29) is past it and is not.
	 *
	 * <p>B1449: the wall ran from z 0, so the husks stood at z 29, 32 and 33, the last two outside the 30-block area,
	 * where whether an entity ticks depends on the machine's chunk borders (it failed 3 of 3 in one container). The whole
	 * setup moved 4 north, so every husk is inside the area; the ramp's foot and the wall's ends are blocks outside it.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaWallRamps1121dLong"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaWallRamps1121dLong")
	public void qaAlongALongWallHusksWithin32BlocksAreCountedOverAndOnePastItIsNot(GameTestHelper helper) {
		Yard yard = village(helper, new BlockPos(22, 2, 15));
		int start = -4;
		for (int z = start; z <= 31; z++) {
			boolean outside = z < 0 || z >= 30;
			for (int x = 6; x <= 13; x++) {
				if (outside) {
					setKept(helper, new BlockPos(x, 1, z), Blocks.SMOOTH_STONE);
					for (int y = 2; y <= 5; y++) {
						if (x != WEST || y > 3) {
							setKept(helper, new BlockPos(x, y, z), Blocks.AIR);
						}
					}
				}
			}
			for (int y = 2; y <= 3; y++) {
				if (outside) {
					setKept(helper, new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				} else {
					helper.setBlock(new BlockPos(WEST, y, z), Blocks.STONE_BRICKS);
				}
			}
		}
		cell(helper, WEST + 2, start + 29, true);
		Mob at29 = husk(helper, yard, WEST + 2, start + 29);
		cell(helper, WEST + 1, start + 32, true);
		Mob at32 = husk(helper, yard, WEST + 1, start + 32);
		cell(helper, WEST + 3, start + 33, true);
		Mob at33 = husk(helper, yard, WEST + 3, start + 33);
		siege(helper, yard, List.of(at29, at32, at33), new BlockPos(WEST - 1, 2, start), new BlockPos(27, 2, 15));
		expect(helper, yard, "a long straight wall", List.of(at29, at32), List.of(at33));
	}

	/** The joined garden wall case (see {@link QaWallRamps1121dBugTests}): the husk outside the yard at (10, 2, 24). */
	static void joinedGarden(GameTestHelper helper) {
		Yard yard = village(helper, HALL);
		walls(helper);
		for (int z = FRONT + 1; z <= 26; z++) {
			helper.setBlock(new BlockPos(WEST, 2, z), Blocks.COBBLESTONE);
			helper.setBlock(new BlockPos(WEST, 3, z), Blocks.COBBLESTONE);
		}
		cell(helper, WEST + 2, 24, false);
		Mob husk = husk(helper, yard, WEST + 2, 24);
		cell(helper, WEST + 2, 12, false);
		Mob inside = husk(helper, yard, WEST + 2, 12); // (the control: it is counted over, so the one outside was asked about)
		siege(helper, yard, List.of(husk, inside), FOOT, OTHER);
		expect(helper, yard, "outside, beside a garden wall joined to the yard's corner", List.of(inside), List.of(husk));
	}
}

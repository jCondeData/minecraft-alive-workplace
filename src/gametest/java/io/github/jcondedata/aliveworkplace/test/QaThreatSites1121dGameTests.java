package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.CaravanTrail;
import io.github.jcondedata.aliveworkplace.story.LostCaravanStory;
import io.github.jcondedata.aliveworkplace.threat.CampCleanup;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * QA (lane d's 52.5 threat sites, rebuilt 2026-11-18), written from the briefs and the player wiki: the Raiders' Stockade
 * works in every turn the arc may give it (the carter caged, the goods in the chest, the lever on the jamb opens the iron
 * gate and he can walk out the front, and not before), the Bandit King's fort can be climbed as the wiki tells it (the
 * tower decks up their ladders, the King's chamber up a stair, the war loft up a ladder), no fort level has a bed, the
 * wreck lies cleanly in every turn, nothing of the four sites floats, and a camp of the drawing before last still comes
 * all the way down across a save and reload halfway.
 */
public class QaThreatSites1121dGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos MIDDLE = new BlockPos(15, 3, 15);

	// ---------- shared ground ----------

	/** Grass at helper y 3 over dirt, nothing above it up to the top, and no barrier roof (the height map must find the ground). */
	private static void flatGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 29; y >= 4; y--) {
					BlockPos p = new BlockPos(x, y, z);
					if (!helper.getBlockState(p).isAir()) {
						helper.getLevel().removeBlockEntity(helper.absolutePos(p));
						helper.getLevel().setBlock(helper.absolutePos(p), Blocks.AIR.defaultBlockState(), 2 | 16);
					}
				}
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	private static void humpAndHole(GameTestHelper helper) {
		for (int x = 11; x <= 12; x++) {
			for (int z = 11; z <= 12; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 5, z), Blocks.GRASS_BLOCK);
			}
		}
		for (int x = 18; x <= 19; x++) {
			for (int z = 17; z <= 18; z++) {
				helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
			}
		}
	}

	private static void arcsNear(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int near = Arcs.NEAR;
		Arcs.NEAR = 0;
		Leftovers.after(helper, () -> Arcs.NEAR = near);
	}

	private static StructurePlaceSettings turn(int r) {
		return new StructurePlaceSettings().setRotation(Rotation.values()[r]);
	}

	/** A template cell in the world, for a build whose zero corner is {@code origin}, turned {@code r}. */
	private static BlockPos cell(BlockPos origin, int r, BlockPos local) {
		return origin.offset(StructureTemplate.calculateRelativePosition(turn(r), local));
	}

	private static Blueprint drawing(ServerLevel level, ResourceLocation id) {
		StructureTemplate template = level.getStructureManager().get(id).orElseThrow(() -> new IllegalStateException("no template " + id));
		return Blueprint.fromTemplate(id, template, Lookup.lookup(BuiltInRegistries.BLOCK));
	}

	// ---------- the Raiders' Stockade ----------

	/**
	 * The stockade as the running Lost Caravan sets it down, turned {@code r} (the arc turns it at random): the carter is
	 * caged two blocks behind the shut iron gate with room over him, the chest holds the lost shipment's goods, he can't
	 * walk out while the gate is shut; a player flips the lever on the jamb, the gate opens (both halves), and he can walk
	 * out of the stockade to the ground in front of its gates. Flipped back, the gate shuts again.
	 */
	/** A survival player standing in front of the lever right-clicks it with an empty hand. */
	@SuppressWarnings("removal")
	private static void flip(GameTestHelper helper, BlockPos lever) {
		ServerLevel level = helper.getLevel();
		net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, net.minecraft.world.InteractionHand.MAIN_HAND,
			new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(lever), Direction.UP, lever, false));
		player.discard();
	}

	private static void stockadeTurn(GameTestHelper helper, int r) {
		flatGround(helper);
		BlockPos[] halls = QaLostCaravanGameTests.begin(helper);
		ServerLevel level = helper.getLevel();
		ArcState s = QaLostCaravanGameTests.lose(helper, halls[0], halls[1]);
		ArcState.ArcPlace p = s.place(LostCaravanStory.STOCKADE);
		helper.assertTrue(p != null && p.placed == 0, "the arc has no stockade to build: " + p);
		p.spot = helper.absolutePos(MIDDLE);
		p.rotation = r;
		helper.assertTrue(ArcEffects.build(level, s, p) && p.placed == 1 && !p.skipped && p.origin != null, "turn " + r + ": the stockade wasn't built");
		helper.runAfterDelay(2, () -> {
			List<String> problems = new ArrayList<>();
			BlockPos door = cell(p.origin, r, CaravanTrail.CAGE_DOOR);
			BlockPos lever = cell(p.origin, r, new BlockPos(15, 2, 9));
			BlockPos chest = cell(p.origin, r, CaravanTrail.LOOT_CHEST);
			BlockPos inside = cell(p.origin, r, CaravanTrail.CAGE_DOOR.offset(0, 0, 2));
			BlockPos front = cell(p.origin, r, new BlockPos(9, 1, -3));
			helper.assertTrue(level.getBlockState(door).is(Blocks.IRON_DOOR) && !level.getBlockState(door).getValue(DoorBlock.OPEN),
				"turn " + r + ": no shut iron gate at the cage door: " + level.getBlockState(door));
			helper.assertTrue(level.getBlockState(lever).is(Blocks.LEVER), "turn " + r + ": no lever on the jamb: " + level.getBlockState(lever));
			if (!(level.getBlockEntity(chest) instanceof Container box) || box.countItem(Items.OAK_LOG) != 32 || box.countItem(Items.BREAD) != 12) {
				problems.add("the stockade's chest doesn't hold the lost goods");
			}
			Villager carter = LostCaravanStory.carter(level, s);
			helper.assertTrue(carter != null, "turn " + r + ": no carter was caged");
			if (!carter.blockPosition().equals(inside)) {
				problems.add("the carter stands at " + helper.relativePos(carter.blockPosition()) + ", not two behind the gate at " + helper.relativePos(inside));
			}
			if (!level.getBlockState(inside).getCollisionShape(level, inside).isEmpty() || !level.getBlockState(inside.above()).getCollisionShape(level, inside.above()).isEmpty()) {
				problems.add("the carter's cell isn't open: " + level.getBlockState(inside) + " / " + level.getBlockState(inside.above()));
			}
			Path shut = carter.getNavigation().createPath(front, 0);
			if (shut != null && shut.canReach()) {
				problems.add("the carter can walk out with the gate shut (" + shut.getNodeCount() + " steps)");
			}
			flip(helper, lever);
			helper.assertTrue(level.getBlockState(lever).getValue(net.minecraft.world.level.block.LeverBlock.POWERED), "turn " + r + ": the lever didn't flip");
			boolean open =level.getBlockState(door).getValue(DoorBlock.OPEN) && level.getBlockState(door.above()).getValue(DoorBlock.OPEN);
			if (!open) {
				problems.add("flipping the lever doesn't open the iron gate (lever " + level.getBlockState(lever) + ")");
			} else {
				Path out = carter.getNavigation().createPath(front, 0);
				if (out == null || !out.canReach()) {
					problems.add("with the gate open the carter has no way out to the front (" + (out == null ? "no path" : "ends at " + helper.relativePos(out.getTarget())) + ")");
				}
				flip(helper, lever);
				if (level.getBlockState(door).getValue(DoorBlock.OPEN)) {
					problems.add("flipping the lever back doesn't shut the gate");
				}
			}
			helper.assertTrue(problems.isEmpty(), "turn " + r + ": " + String.join("; ", problems));
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch TALL '100' '"qaStockadeTurn0_1121d"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaStockadeTurn0_1121d")
	public void qaStockadeLeverFreesTheCarterUnturned(GameTestHelper helper) {
		stockadeTurn(helper, 0);
	}

	//$ gametest_ticks_batch TALL '100' '"qaStockadeTurn1_1121d"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaStockadeTurn1_1121d")
	public void qaStockadeLeverFreesTheCarterTurnedOnce(GameTestHelper helper) {
		stockadeTurn(helper, 1);
	}

	//$ gametest_ticks_batch TALL '100' '"qaStockadeTurn2_1121d"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaStockadeTurn2_1121d")
	public void qaStockadeLeverFreesTheCarterTurnedTwice(GameTestHelper helper) {
		stockadeTurn(helper, 2);
	}

	//$ gametest_ticks_batch TALL '100' '"qaStockadeTurn3_1121d"'
	@GameTest(template = TALL, timeoutTicks = 100, batch = "qaStockadeTurn3_1121d")
	public void qaStockadeLeverFreesTheCarterTurnedThrice(GameTestHelper helper) {
		stockadeTurn(helper, 3);
	}

	// ---------- the Bandit King's fort ----------

	private static boolean climbable(BlockState s) {
		return s.is(BlockTags.CLIMBABLE);
	}

	/** Can a player's body pass through this cell (a wooden door, gate or trapdoor he opens by hand; an iron door only open)? */
	private static boolean open(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		if (climbable(s)) {
			return true;
		}
		if (s.getBlock() instanceof DoorBlock) {
			return !s.is(Blocks.IRON_DOOR) || s.getValue(DoorBlock.OPEN);
		}
		if (s.getBlock() instanceof FenceGateBlock) {
			return true;
		}
		if (s.getBlock() instanceof TrapDoorBlock) {
			return !s.is(Blocks.IRON_TRAPDOOR) || s.getValue(TrapDoorBlock.OPEN);
		}
		return s.getCollisionShape(level, p).isEmpty();
	}

	/** The height a player standing with his feet in {@code p} stands at, or NaN when he can't stand there (no floor, or no room). */
	private static double floor(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		double f;
		if (climbable(s)) {
			f = p.getY();
		} else {
			VoxelShape own = s.getCollisionShape(level, p);
			if (!open(level, p)) {
				if (own.max(Direction.Axis.Y) > 0.5) {
					return Double.NaN;
				}
				f = p.getY() + own.max(Direction.Axis.Y);
			} else {
				BlockPos b = p.below();
				BlockState bs = level.getBlockState(b);
				VoxelShape under = bs.getCollisionShape(level, b);
				if (under.isEmpty()) {
					if (!climbable(bs)) {
						return Double.NaN;
					}
					f = p.getY();
				} else if (under.max(Direction.Axis.Y) <= 0.5 && !open(level, b)) {
					return Double.NaN; // (a carpet or a low slab: his feet are in that cell, not this one)
				} else {
					f = b.getY() + Math.min(1.5, under.max(Direction.Axis.Y));
				}
			}
		}
		for (int y = p.getY() + 1; y < f + 1.8; y++) {
			if (!open(level, new BlockPos(p.getX(), y, p.getZ()))) {
				return Double.NaN;
			}
		}
		return f;
	}

	/** Every cell a player can stand in, walking, jumping a block, dropping and climbing ladders from {@code start}, inside {@code area}. */
	private static Set<BlockPos> reach(ServerLevel level, BlockPos start, BlockPos min, BlockPos max) {
		Set<BlockPos> seen = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		seen.add(start);
		todo.add(start);
		while (!todo.isEmpty()) {
			BlockPos at = todo.poll();
			double f = floor(level, at);
			if (Double.isNaN(f)) {
				continue;
			}
			List<BlockPos> next = new ArrayList<>();
			if (climbable(level.getBlockState(at))) {
				next.add(at.above());
				next.add(at.below());
			}
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos side = at.relative(d);
				for (int dy = 1; dy >= -8; dy--) {
					BlockPos to = side.above(dy);
					double g = floor(level, to);
					if (Double.isNaN(g)) {
						continue;
					}
					boolean ok;
					if (g > f + 0.6) { // a jump: no higher than a block and a quarter, with room over his head where he stands
						ok = g - f <= 1.25;
						for (int y = at.getY() + 1; ok && y < g + 1.8; y++) {
							ok = open(level, new BlockPos(at.getX(), y, at.getZ()));
						}
					} else if (dy < 0) { // a drop: the column he falls down is open
						ok = true;
						for (int y = to.getY() + 1; ok && y <= at.getY() + 1; y++) {
							ok = open(level, new BlockPos(to.getX(), y, to.getZ()));
						}
					} else {
						ok = true;
					}
					if (ok) {
						next.add(to);
					}
					break; // (the highest place he can stand in that column, as he walks there)
				}
			}
			for (BlockPos n : next) {
				if (n.getX() >= min.getX() && n.getX() <= max.getX() && n.getY() >= min.getY() && n.getY() <= max.getY() && n.getZ() >= min.getZ() && n.getZ() <= max.getZ()
					&& !Double.isNaN(floor(level, n)) && seen.add(n.immutable())) {
					todo.add(n.immutable());
				}
			}
		}
		return seen;
	}

	/**
	 * The fort's three stages, turned a quarter as the arc may: from the yard's middle a player can walk and climb to every
	 * place the wiki names: each watchtower's deck up its ladder (where the pillagers stand), the King's place before his
	 * throne through the keep's door, his chamber "up a stair" and the war loft "up a ladder under the roof" (the critic's
	 * round-1 BACK: the keep's upper floors couldn't be reached).
	 */
	//$ gametest_ticks_batch TALL '60' '"qaFortClimb1121d"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaFortClimb1121d")
	public void qaEveryFloorOfTheFortIsReachableOnFoot(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		flatGround(helper);
		ServerLevel level = helper.getLevel();
		BanditFort.Fort fort = BanditFort.at(helper.absolutePos(MIDDLE), Rotation.CLOCKWISE_90);
		for (int stage = 0; stage < 3; stage++) {
			helper.assertTrue(BanditFort.place(level, fort, stage, false), "stage " + (stage + 1) + " missing");
		}
		level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(16)).forEach(Entity::discard); // (the men are not the walls)
		BlockPos a = fort.at(new BlockPos(-2, -1, -2));
		BlockPos b = fort.at(new BlockPos(30, 18, 30));
		BlockPos min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
		BlockPos max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
		BlockPos yard = fort.at(new BlockPos(14, 1, 14));
		helper.assertFalse(Double.isNaN(floor(level, yard)), "a player can't stand in the yard's middle: " + level.getBlockState(yard));
		Set<BlockPos> reached = reach(level, yard, min, max);
		List<String> missed = new ArrayList<>();
		String[] names = {"the west tower's deck", "the east tower's deck", "the King's place before his throne", "the King's chamber (up a stair)", "the war loft (up a ladder)"};
		BlockPos[] spots = {new BlockPos(2, 6, 2), new BlockPos(26, 6, 2), new BlockPos(14, 1, 21), new BlockPos(14, 6, 21), new BlockPos(14, 10, 21)};
		for (int i = 0; i < spots.length; i++) {
			BlockPos w = fort.at(spots[i]);
			if (Double.isNaN(floor(level, w))) {
				missed.add(names[i] + " " + spots[i] + " is no place to stand (" + level.getBlockState(w) + " on " + level.getBlockState(w.below()) + ")");
			} else if (!reached.contains(w)) {
				missed.add(names[i] + " " + spots[i] + " can't be reached from the yard");
			}
		}
		helper.assertTrue(missed.isEmpty(), String.join("; ", missed) + " (" + reached.size() + " cells reached)");
		// The wiki's "a war loft up a ladder": take the ladder away and the loft can't be reached (nor is the walk above cheating through walls).
		for (int y = 6; y <= 9; y++) {
			BlockPos rung = fort.at(new BlockPos(18, y, 19));
			helper.assertTrue(level.getBlockState(rung).is(Blocks.LADDER), "no ladder to the loft at (18, " + y + ", 19): " + level.getBlockState(rung));
			level.setBlock(rung, Blocks.AIR.defaultBlockState(), 2 | 16);
		}
		helper.assertFalse(reach(level, yard, min, max).contains(fort.at(new BlockPos(14, 10, 21))), "the war loft is reached without its ladder");
		helper.succeed();
	}

	/** The wiki and the critic: the fort's tents have bedrolls (carpets), and no stage of the fort has a bed. */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void qaNoFortStageHasABedButItsTentsHaveBedrolls(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (ResourceLocation id : BanditFort.STAGES) {
			Blueprint d = drawing(level, id);
			long beds = d.blocks().stream().filter(e -> e.state().is(BlockTags.BEDS)).count();
			helper.assertTrue(beds == 0, id + " has " + beds + " bed blocks");
			long carpets = d.blocks().stream().filter(e -> e.state().is(BlockTags.WOOL_CARPETS)).count();
			helper.assertTrue(carpets > 0, id + " has no bedroll (no carpet)");
		}
		helper.succeed();
	}

	// ---------- nothing floats ----------

	private static List<String> floating(Blueprint d) {
		Set<BlockPos> solid = new HashSet<>();
		for (Blueprint.Entry e : d.blocks()) {
			if (!e.state().isAir() && e.pos().getY() >= 1) {
				solid.add(e.pos().immutable());
			}
		}
		Set<BlockPos> held = new HashSet<>();
		ArrayDeque<BlockPos> todo = new ArrayDeque<>();
		for (BlockPos p : solid) {
			if (p.getY() == 1) { // (y 0 is the ground itself, drawn or the world's)
				held.add(p);
				todo.add(p);
			}
		}
		while (!todo.isEmpty()) {
			BlockPos p = todo.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 0 || Math.abs(dx) + Math.abs(dy) + Math.abs(dz) == 3) {
							continue; // (sides and edges hold, as a stepped roof's stairs do; a lone corner doesn't)
						}
						BlockPos n = p.offset(dx, dy, dz);
						if (solid.contains(n) && held.add(n)) {
							todo.add(n);
						}
					}
				}
			}
		}
		List<String> out = new ArrayList<>();
		for (Blueprint.Entry e : d.blocks()) {
			if (!e.state().isAir() && e.pos().getY() >= 1 && !held.contains(e.pos())) {
				out.add(e.pos().toShortString() + " " + BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).getPath());
			}
		}
		return out;
	}

	/**
	 * Every one of the four rebuilt sites (the stockade, the fort's three stages, the camp, the wreck) stands on its ground:
	 * no block of it hangs in the air cut off from the rest (the wiki: "it lies on the land as it is").
	 */
	//$ gametest_ticks EMPTY_STRUCTURE '20'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20)
	public void qaNoBlockOfTheThreatSitesFloats(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<ResourceLocation> ids = new ArrayList<>(List.of(CaravanTrail.STOCKADE, CaravanTrail.WRECK, Lairs.BANDIT_CAMP));
		ids.addAll(BanditFort.STAGES);
		List<String> problems = new ArrayList<>();
		for (ResourceLocation id : ids) {
			List<String> f = floating(drawing(level, id));
			if (!f.isEmpty()) {
				problems.add(id + ": " + f.size() + " floating, e.g. " + f.subList(0, Math.min(6, f.size())));
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems));
		helper.succeed();
	}

	// ---------- the Caravan Wreck ----------

	/**
	 * The wreck in each of the three turns the existing test doesn't try (the arc turns it at random), on a hump and a
	 * hole: it is set down at the spot, the ground under each of its 13 x 13 columns is solid two deep, the hump is cut
	 * away, and its canvas and lantern stand where the drawing has them.
	 */
	//$ gametest_ticks_batch TALL '120' '"qaWreckTurns1121d"'
	@GameTest(template = TALL, timeoutTicks = 120, batch = "qaWreckTurns1121d")
	public void qaTheWreckLiesCleanlyInEveryTurn(GameTestHelper helper) {
		arcsNear(helper);
		ServerLevel level = helper.getLevel();
		List<String> problems = new ArrayList<>();
		for (int r = 1; r < 4; r++) {
			flatGround(helper);
			humpAndHole(helper);
			ArcState s = new ArcState(LostCaravanStory.ARC.toString());
			ArcState.ArcPlace p = new ArcState.ArcPlace("wreck", helper.absolutePos(MIDDLE), List.of(CaravanTrail.WRECK.toString()), r);
			s.places.add(p);
			helper.assertTrue(ArcEffects.build(level, s, p) && p.placed == 1 && !p.skipped && p.origin != null, "turn " + r + ": the wreck wasn't built at its spot");
			for (int x = 0; x < 13; x++) {
				for (int z = 0; z < 13; z++) {
					BlockPos ground = cell(p.origin, r, new BlockPos(x, 0, z));
					if (level.getBlockState(ground).isAir() || level.getBlockState(ground.below()).isAir()) {
						problems.add("turn " + r + ": open ground under (" + x + ", " + z + ")");
					}
					for (int y = 1; y <= 2; y++) {
						BlockState st = level.getBlockState(ground.above(y));
						if (st.is(Blocks.DIRT) || st.is(Blocks.GRASS_BLOCK)) {
							problems.add("turn " + r + ": the hump still stands at (" + x + ", " + y + ", " + z + ")");
						}
					}
				}
			}
			if (!level.getBlockState(cell(p.origin, r, new BlockPos(2, 1, 5))).is(Blocks.WHITE_WOOL)) {
				problems.add("turn " + r + ": no canvas at (2, 1, 5)");
			}
			if (!level.getBlockState(cell(p.origin, r, new BlockPos(9, 1, 10))).is(Blocks.LANTERN)) {
				problems.add("turn " + r + ": no lantern at (9, 1, 10): " + level.getBlockState(cell(p.origin, r, new BlockPos(9, 1, 10))));
			}
		}
		helper.assertTrue(problems.isEmpty(), String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
		helper.succeed();
	}

	// ---------- the Bandit Camp ----------

	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos CAMP = new BlockPos(15, 1, 15);
	private static final BlockPos FIRE_SPOT = new BlockPos(7, 1, 6);
	/** The drawing before last (B537): camps set down before 0.150.0 may still stand on the owner's server. */
	private static final String OLDER = "a12542b71";
	private static final String OLD = "v0.150.0";

	private static BlockPos campOrigin(BlockPos middle, int r) {
		return middle.subtract(StructureTemplate.calculateRelativePosition(turn(r), FIRE_SPOT));
	}

	private static List<Blueprint.Entry> standing(Blueprint d) {
		return d.blocks().stream().filter(e -> e.pos().getY() >= 1 && !e.state().isAir()).toList();
	}

	private static int asDrawn(ServerLevel level, Blueprint d, BlockPos middle, int r) {
		int n = 0;
		for (Blueprint.Entry e : standing(d)) {
			if (MaterialRules.matches(level.getBlockState(cell(campOrigin(middle, r), r, e.pos())), e.state().mirror(Mirror.NONE).rotate(Rotation.values()[r]))) {
				n++;
			}
		}
		return n;
	}

	private static int[] count(ServerLevel level, BlockPos middle) {
		int[] n = new int[4];
		for (BlockPos p : BlockPos.betweenClosed(middle.offset(-9, 0, -9), middle.offset(9, 10, 9))) {
			BlockState s = level.getBlockState(p);
			if (s.getBlock() instanceof CampfireBlock) {
				n[0]++;
			} else if (s.is(Blocks.CHEST)) {
				n[1]++;
			} else if (s.is(BlockTags.WOOL)) {
				n[2]++;
			} else if (s.is(BlockTags.BEDS)) {
				n[3]++;
			}
		}
		return n;
	}

	/**
	 * A camp of the drawing before last (as camps set down before 0.150.0 may still stand on the owner's server), with its
	 * lair's record: its chief falls, the game is saved and loaded before it starts coming down and again halfway, and
	 * still every block of it comes down, known all along as an older drawing, not today's.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaOlderCampReload1121d"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaOlderCampReload1121d")
	public void qaAnOlderDrawingsCampComesDownAcrossTwoReloads(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ThreatData.get(level).forgetPast(hall);
		BanditCamps.forget(level);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG)).forEach(Mob::discard);
			level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8)).forEach(Entity::discard);
			BanditCamps.forget(level);
			ThreatData.get(level).forgetPast(hall);
			VillageHalls.RADIUS = radius;
		});
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(BanditCamps.found(level, hall, helper.absolutePos(CAMP)) != null, "no camp was made");
			Lairs.Lair lair = Lairs.near(level, hall).orElseThrow();
			BlockPos middle = lair.pos();
			Blueprint today = drawing(level, Lairs.BANDIT_CAMP);
			int r = -1;
			for (int t = 0; t < 4 && r < 0; t++) {
				if (asDrawn(level, today, middle, t) == standing(today).size()) {
					r = t;
				}
			}
			helper.assertTrue(r >= 0, "setup: today's camp doesn't stand whole");
			int turn = r;
			Blueprint older = Refreshes.olderDrawing(level.getServer(), Lairs.BANDIT_CAMP, OLDER).orElseThrow();
			int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
			for (Blueprint.Entry e : standing(today)) {
				level.setBlock(cell(campOrigin(middle, turn), turn, e.pos()), Blocks.AIR.defaultBlockState(), flags);
			}
			for (Blueprint.Entry e : older.blocks()) {
				if (!e.state().isAir()) {
					level.setBlock(cell(campOrigin(middle, turn), turn, e.pos()), e.state().mirror(Mirror.NONE).rotate(Rotation.values()[turn]), flags);
				}
			}
			int whole = standing(older).size();
			helper.assertTrue(asDrawn(level, older, middle, turn) == whole, "setup: the older camp doesn't stand whole");
			int[] before = count(level, middle);
			if (level.getEntity(lair.captain()) instanceof LivingEntity chief) {
				chief.kill();
			}
			Lairs.Data.get(level).roundTrip(level.registryAccess());
			helper.assertTrue(Lairs.near(level, hall).isEmpty() && CampCleanup.clearing(level).size() == 1, "after a reload before it comes down: " + CampCleanup.clearing(level));
			helper.runAfterDelay(CampCleanup.DELAY + CampCleanup.CLEAR_TICKS / 2, () -> {
				int[] half = count(level, middle);
				helper.assertTrue(half[2] < before[2], "halfway, nothing of the older camp came down: wool " + half[2] + " of " + before[2]);
				Lairs.Data.get(level).roundTrip(level.registryAccess());
				List<CampCleanup.Clearing> going = CampCleanup.clearing(level);
				helper.assertTrue(going.size() == 1 && going.get(0).rotation() == turn && (OLDER.equals(going.get(0).drawing()) || OLD.equals(going.get(0).drawing())),
					"after a reload halfway the camp isn't known as the older drawing turned " + turn + ": " + going);
				helper.runAfterDelay(CampCleanup.CLEAR_TICKS / 2 + 15, () -> {
					int left = asDrawn(level, older, middle, turn);
					helper.assertTrue(left == 0, left + " of the older camp's " + whole + " blocks still stand");
					int[] after = count(level, middle);
					helper.assertTrue(after[0] == 0 && after[1] == 0 && after[2] == 0 && after[3] == 0,
						"the older camp still stands: fires " + after[0] + ", chests " + after[1] + ", wool " + after[2] + ", beds " + after[3]);
					helper.assertTrue(CampCleanup.clearing(level).isEmpty(), "the clearing never ended");
					helper.succeed();
				});
			});
		});
	}
}

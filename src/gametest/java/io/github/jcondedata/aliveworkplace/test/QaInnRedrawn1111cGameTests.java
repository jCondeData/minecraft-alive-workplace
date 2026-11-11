package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Refreshes;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureChoice;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane c, 2026-11-11) for the redrawn Inn (commit 92b57580e), from its spec: "a dark oak bar down the east side with
 * the Shop Counter in its middle and a walk-in space behind it", "upstairs three guest rooms (six beds)", "the ground
 * windows' sills and shutters stand on a stone-brick skirt, the upper ones on a moulding under the jetty", "the banner
 * faces the street (rotation 8)", "Inn II drops its old chimney through the stable roof", "the Inn II and III get the
 * same rooms". Played as a player would on plain Fabric (no furniture mods): the Inn put up block for block as the
 * builder leaves it, a jobless villager handed a bed behind the bar, and the inn's mornings filling its beds.
 */
public class QaInnRedrawn1111cGameTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);
	/** The Inn III's cellar lies 4 under its ground floor: its ground floor goes higher so the cellar is inside the area. */
	private static final BlockPos ORIGIN_3 = new BlockPos(3, 7, 3);
	private static final List<StarterBlueprints.Entry> LEVELS = List.of(StarterBlueprints.INN, StarterBlueprints.INN_2, StarterBlueprints.INN_3);

	// --- the drawings ---------------------------------------------------------------------------------------------

	private static Map<BlockPos, BlockState> cells(Blueprint blueprint) {
		Map<BlockPos, BlockState> at = new HashMap<>();
		blueprint.blocks().forEach(e -> at.put(e.pos(), e.state()));
		return at;
	}

	private static boolean air(BlockState state) {
		return state == null || state.isAir();
	}

	private static boolean openTrapdoor(BlockState state) {
		return state != null && state.getBlock() instanceof TrapDoorBlock && state.getValue(TrapDoorBlock.OPEN);
	}

	private static boolean topStairs(BlockState state) {
		return state != null && state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.TOP;
	}

	/**
	 * A sill (upside-down stairs) holds what stands on it when a block is under it, or when it is part of a moulding: a
	 * run of at least 9 upside-down stairs along the wall it backs on to (the Inn's jetty moulding runs the 11-block front).
	 */
	private static boolean sillHeld(Map<BlockPos, BlockState> at, BlockPos sill, int minY) {
		if (sill.getY() <= minY || !air(at.get(sill.below()))) {
			return true;
		}
		Direction along = at.get(sill).getValue(StairBlock.FACING).getClockWise();
		int run = 1;
		for (Direction d : new Direction[] {along, along.getOpposite()}) {
			for (BlockPos p = sill.relative(d); topStairs(at.get(p)); p = p.relative(d)) {
				run++;
			}
		}
		return run >= 9;
	}

	/**
	 * What the owner found: shutters, sills and flower boxes hanging in the air. Every open shutter stands (through the
	 * shutters under it) on a block or on a held sill; every flower pot stands on something (or hangs from a chain); a
	 * standing banner or lantern stands on something and a hanging lantern hangs from something.
	 */
	static List<String> hanging(Blueprint blueprint) {
		Map<BlockPos, BlockState> at = cells(blueprint);
		int minY = at.keySet().stream().mapToInt(BlockPos::getY).min().orElse(0);
		List<String> out = new ArrayList<>();
		for (Map.Entry<BlockPos, BlockState> e : at.entrySet()) {
			BlockPos pos = e.getKey();
			BlockState state = e.getValue();
			Block block = state.getBlock();
			String what = blueprint.id().getPath() + " " + BuiltIn.name(state) + " at " + pos.toShortString();
			if (openTrapdoor(state)) {
				BlockPos under = pos.below();
				while (openTrapdoor(at.get(under))) {
					under = under.below();
				}
				if (under.getY() < minY) {
					continue;
				}
				BlockState u = at.get(under);
				if (air(u)) {
					out.add(what + " (a shutter on air)");
				} else if (topStairs(u) && !sillHeld(at, under, minY)) {
					out.add(what + " (a shutter on a sill hanging in the air at " + under.toShortString() + ")");
				}
			} else if (block instanceof FlowerPotBlock && pos.getY() > minY) {
				if (at.get(pos.above()) != null && at.get(pos.above()).getBlock() instanceof ChainBlock) {
					continue;
				}
				BlockState u = at.get(pos.below());
				if (air(u)) {
					out.add(what + " (a pot on air)");
				} else if (topStairs(u) && !sillHeld(at, pos.below(), minY)) {
					out.add(what + " (a pot on a sill hanging in the air)");
				}
			} else if (block instanceof BannerBlock && !(block instanceof net.minecraft.world.level.block.WallBannerBlock) && pos.getY() > minY
				&& air(at.get(pos.below()))) {
				out.add(what + " (a standing banner on air)");
			} else if (block instanceof LanternBlock) {
				boolean hangs = state.getValue(LanternBlock.HANGING);
				if (hangs ? air(at.get(pos.above())) : pos.getY() > minY && air(at.get(pos.below()))) {
					out.add(what + (hangs ? " (a hanging lantern under air)" : " (a lantern on air)"));
				}
			}
		}
		out.sort(String::compareTo);
		return out;
	}

	/** Block names for messages. */
	private static final class BuiltIn {
		static String name(BlockState state) {
			return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		}
	}

	private static List<BlockPos> find(Map<BlockPos, BlockState> at, Block block) {
		return at.entrySet().stream().filter(e -> e.getValue().is(block)).map(Map.Entry::getKey).sorted().toList();
	}

	private static Blueprint drawing(GameTestHelper helper, ResourceLocation id) {
		return BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(() -> new GameTestAssertException("no blueprint " + id));
	}

	/**
	 * The redrawn Inn and its II and III: nothing hangs in the air (and the check finds what hung in the drawing the owner
	 * played, v0.154.0); one Shop Counter and no old Inn Counter, with a walk-in space behind it and the room in front;
	 * six guest beds; the banner on its post faces the street; and the Inn II and III keep no chimney through the stable.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theInnsDrawingsHangNothingInTheAir(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		for (StarterBlueprints.Entry entry : LEVELS) {
			Blueprint blueprint = drawing(helper, entry.id());
			Map<BlockPos, BlockState> at = cells(blueprint);
			String name = entry.id().getPath();
			problems.addAll(hanging(blueprint));
			List<BlockPos> shops = find(at, ModBlocks.SHOP_COUNTER);
			if (shops.size() != 1) {
				problems.add(name + ": " + shops.size() + " Shop Counters " + shops);
			} else {
				BlockPos counter = shops.get(0);
				Direction front = at.get(counter).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
				BlockPos behind = counter.relative(front.getOpposite());
				if (!air(at.get(behind)) || !air(at.get(behind.above()))) {
					problems.add(name + ": no walk-in space behind the Shop Counter at " + behind.toShortString() + ": " + at.get(behind) + " / " + at.get(behind.above()));
				}
				if (!air(at.get(counter.relative(front))) && !(at.get(counter.relative(front)).getBlock() instanceof net.minecraft.world.level.block.CarpetBlock)) {
					problems.add(name + ": the Shop Counter's front faces " + at.get(counter.relative(front)));
				}
				if (front != Direction.WEST) {
					problems.add(name + ": the bar is down the east side, its counter facing west into the room; it faces " + front);
				}
				// the bar either side of it: dark oak counter pieces
				for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH}) {
					BlockState side = at.get(counter.relative(d));
					if (side == null || !side.is(net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS)) {
						problems.add(name + ": the bar " + d + " of the Shop Counter is " + side);
					}
				}
			}
			if (!find(at, ModBlocks.INN_COUNTER).isEmpty()) {
				problems.add(name + ": an old Inn Counter (can't be crafted) at " + find(at, ModBlocks.INN_COUNTER));
			}
			long beds = at.values().stream().filter(s -> s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD).count();
			if (beds != 6) {
				problems.add(name + ": " + beds + " beds, not the six guest beds");
			}
			List<BlockPos> banners = at.entrySet().stream().filter(e -> e.getValue().getBlock() instanceof BannerBlock
				&& !(e.getValue().getBlock() instanceof net.minecraft.world.level.block.WallBannerBlock)).map(Map.Entry::getKey).toList();
			for (BlockPos b : banners) {
				int rotation = at.get(b).getValue(BannerBlock.ROTATION);
				if (rotation != 8) {
					problems.add(name + ": the standing banner at " + b.toShortString() + " has rotation " + rotation + ", not 8 (the street)");
				}
				if (!(at.get(b.below()).getBlock() instanceof FenceBlock)) {
					problems.add(name + ": the banner stands on " + at.get(b.below()) + ", not its post");
				}
			}
			if (banners.size() != 1) {
				problems.add(name + ": " + banners.size() + " standing banners, not the one on the corner post");
			}
			// Inn II's old chimney stood at x 12 through the stable roof: every fire (the hearth, the stack's top) is in the
			// inn's own 13 columns now
			for (Map.Entry<BlockPos, BlockState> e : at.entrySet()) {
				if (e.getValue().getBlock() instanceof CampfireBlock && e.getKey().getX() > 11) {
					problems.add(name + ": a chimney's fire at " + e.getKey().toShortString() + ", past the inn's east wall");
				}
			}
		}
		// The check is worth something: the drawing the owner played had shutters and sills in the air
		Blueprint old = Refreshes.olderDrawing(helper.getLevel().getServer(), StarterBlueprints.INN.id(), "v0.154.0")
			.orElseThrow(() -> new GameTestAssertException("no v0.154.0 drawing of the Inn in blueprint_history"));
		List<String> before = hanging(old);
		helper.assertTrue(problems.isEmpty(), problems.size() + " problem(s) with the redrawn Inn: "
			+ String.join("; ", problems.subList(0, Math.min(10, problems.size()))));
		helper.assertTrue(!before.isEmpty(), "the check finds nothing hanging in the Inn the owner played (v0.154.0), so it proves nothing");
		io.github.jcondedata.aliveworkplace.AliveWorkplace.LOG.info("[qa inn] the redrawn Inn, II and III: nothing hanging; v0.154.0's Inn: {} hanging, e.g. {}",
			before.size(), before.subList(0, Math.min(4, before.size())));
		helper.succeed();
	}

	// --- the Inn put up in the world ----------------------------------------------------------------------------------

	/** Sets every block of the build as a finished build stands (plain Fabric: vanilla furniture), as the builder leaves it. */
	static BuildPlan paste(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		Blueprint blueprint = drawing(helper, entry.id());
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildPlan plan = BuildPlan.create(FurnitureChoice.forNewBuild(entry.id()).apply(blueprint, entry.id()), placement);
		// (in the builder's order: CLEAR empties each cell, top down, then the stages put up the build)
		for (BuildPlan.Step step : plan.steps(BuildPlan.Stage.CLEAR)) {
			level.setBlock(step.pos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
		}
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION, BuildPlan.Stage.LANDSCAPE)) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), 2);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), 2);
				}
			}
		}
		List<BlockPos> unfinished = plan.unfinished(level);
		helper.assertTrue(unfinished.isEmpty(), "the pasted " + entry.id() + " has " + unfinished.size() + " wrong block(s)");
		return plan;
	}

	static BoundingBox box(BuildPlan plan) {
		List<BlockPos> all = new ArrayList<>();
		for (BuildPlan.Stage stage : List.of(BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION)) {
			plan.steps(stage).forEach(s -> all.add(s.pos()));
		}
		return BoundingBox.encapsulatingPositions(all).orElseThrow();
	}

	static List<BlockPos> inBox(ServerLevel level, BoundingBox box, Block block) {
		List<BlockPos> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockState(p).is(block)) {
				out.add(p.immutable());
			}
		}
		return out;
	}

	static void clean(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.homes(helper);
		Leftovers.finished(helper);
	}

	static BlockPos counter(GameTestHelper helper, BoundingBox box) {
		ServerLevel level = helper.getLevel();
		List<BlockPos> shops = inBox(level, box, ModBlocks.SHOP_COUNTER);
		helper.assertTrue(shops.size() == 1, "the built Inn has " + shops.size() + " Shop Counters: " + shops);
		helper.assertTrue(inBox(level, box, ModBlocks.INN_COUNTER).isEmpty(), "the built Inn has an old Inn Counter");
		BlockPos counter = shops.get(0);
		helper.assertTrue(level.getPoiManager().getType(counter).map(h -> h.is(ModVillagers.SHOP_COUNTER_POI)).orElse(false),
			"the built Inn's Shop Counter at " + counter + " is no Shop Counter workstation (point of interest: " + level.getPoiManager().getType(counter) + ")");
		return counter;
	}

	static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** {@code abs} relative to the test area (vanilla's relativePos turns it half round for an unrotated test). */
	static BlockPos rel(GameTestHelper helper, BlockPos abs) {
		return abs.subtract(helper.absolutePos(BlockPos.ZERO));
	}

	/** A jobless villager standing behind the bar, handed a bed, as a player does (sneak-right-click). */
	static Villager innkeeper(GameTestHelper helper, BlockPos counter) {
		BlockPos behind = counter.east();
		Villager v = helper.spawn(EntityType.VILLAGER, rel(helper, behind));
		helper.assertTrue(v.getVillagerData().getProfession() == net.minecraft.world.entity.npc.VillagerProfession.NONE, "setup: not jobless");
		InteractionResult result = Stations.choose(player(helper), v, new ItemStack(Items.RED_BED));
		helper.assertTrue(result.consumesAction(), "a bed handed to a jobless villager behind the Inn's bar did nothing: " + result);
		helper.assertTrue(Innkeepers.isInnkeeper(v), "handed a bed behind the Inn's bar, the villager became a "
			+ v.getVillagerData().getProfession() + ", not the Innkeeper (" + result + "; class refusal: "
			+ io.github.jcondedata.aliveworkplace.people.ClassJobs.refusal(helper.getLevel(), v, ModVillagers.INNKEEPER) + "; hall: "
			+ io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(helper.getLevel(), v.blockPosition()) + "; retired: "
			+ io.github.jcondedata.aliveworkplace.people.Retirement.refuses(v, v.getVillagerData().setProfession(ModVillagers.INNKEEPER))
			+ "; visitor: " + io.github.jcondedata.aliveworkplace.hall.HallPeople.visitor(v) + "; counter's free tickets: "
			+ helper.getLevel().getPoiManager().getFreeTickets(counter) + "; at " + v.blockPosition() + ")");
		Optional<BlockPos> site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
		helper.assertTrue(site.equals(Optional.of(counter)), "the Innkeeper works at " + site + ", not the Inn's Shop Counter " + counter);
		return v;
	}

	/**
	 * The built Inn's bar works as a bar: one Shop Counter (a workstation), the walk-in space behind it reached on foot from
	 * the front door round the bar's back end, a jobless villager there handed a bed takes the Innkeeper's job at it, the
	 * job stays through 200 ticks of his own life, and the inn lookups find the counter as the inn.
	 */
	//$ gametest_ticks_batch HUGE_AREA '400' '"qaInnRedrawn1111cBar"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 400, batch = "qaInnRedrawn1111cBar")
	public void theBuiltInnsBarTakesAnInnkeeper(GameTestHelper helper) {
		clean(helper);
		helper.setDayTime(2000);
		BuildPlan plan = paste(helper, StarterBlueprints.INN, ORIGIN);
		BoundingBox box = box(plan);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(5, () -> {
			BlockPos counter = counter(helper, box);
			BlockPos behind = counter.east();
			Villager keeper = innkeeper(helper, counter);
			helper.assertTrue(Innkeepers.counters(level, counter, 16).equals(List.of(counter)),
				"the inn lookups find " + Innkeepers.counters(level, counter, 16) + ", not the Inn's Shop Counter " + counter);
			// on foot from just inside the front door (the door is in the middle of the front, on the street side)
			BlockPos inside = new BlockPos(box.minX() + 6, counter.getY(), box.minZ() + 3);
			helper.assertTrue(level.getBlockState(inside.north()).getBlock() instanceof net.minecraft.world.level.block.DoorBlock,
				"setup: the front door isn't at " + inside.north() + ": " + level.getBlockState(inside.north()));
			Villager walker = helper.spawn(EntityType.VILLAGER, rel(helper, inside));
			walker.setVillagerData(walker.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.NITWIT));
			helper.runAfterDelay(20, () -> { // (a mob plans a walk only once it stands on the ground)
				Path path = walker.getNavigation().createPath(behind, 0);
				helper.assertTrue(path != null && path.canReach(), "no walk from the front door to the space behind the bar at " + behind + ": "
					+ (path == null ? "no path (walker on ground: " + walker.onGround() + " at " + walker.blockPosition() + ")"
						: "ends at " + path.getEndNode() + " after " + path.getNodeCount() + " steps"));
				walker.discard();
				helper.runAfterDelay(200, () -> {
					helper.assertTrue(Innkeepers.isInnkeeper(keeper) && keeper.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos)
						.equals(Optional.of(counter)), "after 200 ticks the Innkeeper is a " + keeper.getVillagerData().getProfession() + " at "
						+ keeper.getBrain().getMemory(MemoryModuleType.JOB_SITE));
					helper.assertTrue(Innkeepers.counters(level, counter, 16).equals(List.of(counter)), "after 200 ticks the inn lookups find "
						+ Innkeepers.counters(level, counter, 16));
					helper.succeed();
				});
			});
		});
	}

	/**
	 * The Inn's six guest beds, through the inn's own mornings: one traveller a morning, each in a bed of the Inn's own
	 * guest floor (never the same bed twice); "full" at the inn's usual two; with room for more (as the Open Gates effect
	 * gives) the six beds fill one by one, and the seventh morning is "no_bed".
	 */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qaInnRedrawn1111cBeds"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qaInnRedrawn1111cBeds")
	public void theBuiltInnsSixBedsFillWithTravellers(GameTestHelper helper) {
		clean(helper);
		ServerLevel level = helper.getLevel();
		int maxGuests = Innkeepers.MAX_GUESTS;
		boolean legends = Legends.ENABLED;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			Innkeepers.MAX_GUESTS = maxGuests;
			Legends.ENABLED = legends;
			level.setDayTime(time);
		});
		BuildPlan plan = paste(helper, StarterBlueprints.INN, ORIGIN);
		BoundingBox box = box(plan);
		helper.runAfterDelay(5, () -> {
			BlockPos counter = counter(helper, box);
			Villager keeper = innkeeper(helper, counter);
			keeper.setNoAi(true); // (his own bed is vanilla's business: here only the travellers take the Inn's beds)
			helper.assertTrue(Innkeepers.hasFreeBed(level, counter), "the built Inn has no free bed for a traveller");
			Legends.ENABLED = false; // the day's traveller, not a Legend instead
			Innkeepers.MAX_GUESTS = 2;
			Set<BlockPos> taken = new HashSet<>();
			List<String> log = new ArrayList<>();
			int guestFloor = counter.getY() + 5;
			try {
				for (int day = 1; day <= 7; day++) {
					level.setDayTime(day * 24000L + 1000);
					if (day == 3) {
						String full = Innkeepers.tend(level, keeper, counter);
						helper.assertTrue("full".equals(full), "two travellers at the inn's usual two and the morning says " + full);
						helper.assertTrue(Innkeepers.guests(level, counter).size() == 2, "a third traveller came to a full inn");
						Innkeepers.MAX_GUESTS = 10; // room for more: now the beds decide
					}
					int before = Innkeepers.guests(level, counter).size();
					String state = Innkeepers.tend(level, keeper, counter);
					List<Villager> guests = Innkeepers.guests(level, counter);
					log.add("day " + day + ": " + state + ", " + guests.size() + " guests");
					if (day == 7) {
						helper.assertTrue("no_bed".equals(state), "with the Inn's six beds taken the seventh morning says " + state + " (" + log + ")");
						helper.assertTrue(guests.size() == 6, "a seventh traveller came with no bed: " + guests.size());
						break;
					}
					helper.assertTrue("hosting".equals(state) && guests.size() == before + 1, "morning " + day + ": " + state + ", "
						+ before + " -> " + guests.size() + " guests (" + log + ")");
					String again = Innkeepers.tend(level, keeper, counter);
					helper.assertTrue(Innkeepers.guests(level, counter).size() == before + 1, "two travellers came on morning " + day + " (" + again + ")");
					for (Villager g : guests) {
						Optional<BlockPos> home = g.getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos);
						helper.assertTrue(home.isPresent(), "a traveller on morning " + day + " has no bed");
						BlockPos bed = home.get();
						helper.assertTrue(box.isInside(bed) && bed.getY() == guestFloor && level.getBlockState(bed).getBlock() instanceof BedBlock,
							"a traveller's bed " + bed + " isn't one of the Inn's guest beds (guest floor y " + guestFloor + ", box " + box + "): "
								+ level.getBlockState(bed));
						taken.add(bed);
					}
					helper.assertTrue(taken.size() == guests.size(), guests.size() + " travellers in " + taken.size() + " beds: " + taken);
				}
				helper.assertTrue(!Innkeepers.hasFreeBed(level, counter), "every guest bed is taken, yet the Inn has a free bed");
			} finally {
				Innkeepers.MAX_GUESTS = maxGuests;
				Legends.ENABLED = legends;
				level.setDayTime(time);
			}
			for (Villager g : Innkeepers.guests(level, counter)) {
				g.discard();
			}
			helper.succeed();
		});
	}

	/**
	 * Nothing falls off the built Inn: every block of it can stay where it is, and after every block is told its
	 * neighbors changed and 40 ticks pass, nothing has dropped and every block is still as planned.
	 */
	static void standsAfterUpdates(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos origin) {
		clean(helper);
		ServerLevel level = helper.getLevel();
		BuildPlan plan = paste(helper, entry, origin);
		BoundingBox box = box(plan);
		List<String> cannot = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(p);
			if (!state.isAir() && !state.canSurvive(level, p)) {
				cannot.add(BuiltIn.name(state) + " at " + p.subtract(helper.absolutePos(origin)).toShortString());
			}
		}
		helper.assertTrue(cannot.isEmpty(), entry.id() + ": " + cannot.size() + " block(s) that can't stay where they are: " + cannot);
		List<ItemEntity> early = level.getEntitiesOfClass(ItemEntity.class, AABB.of(box).inflate(2), e -> true);
		helper.assertTrue(early.isEmpty(), entry.id() + ": " + early.size() + " thing(s) dropped while it was put up: "
			+ early.stream().limit(6).map(d -> d.getItem() + " at " + d.blockPosition().subtract(helper.absolutePos(origin)).toShortString()).toList());
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState state = level.getBlockState(p);
			if (!state.isAir()) {
				BlockPos at = p.immutable();
				Block.updateOrDestroy(state, Block.updateFromNeighbourShapes(state, level, at), level, at, Block.UPDATE_ALL);
				level.updateNeighborsAt(at, state.getBlock());
			}
		}
		helper.runAfterDelay(40, () -> {
			AABB around = AABB.of(box).inflate(2);
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, around, e -> true);
			helper.assertTrue(drops.isEmpty(), entry.id() + ": " + drops.size() + " thing(s) fell off: "
				+ drops.stream().limit(6).map(d -> d.getItem() + " at " + d.blockPosition().subtract(helper.absolutePos(origin)).toShortString()).toList());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), entry.id() + ": " + unfinished.size() + " block(s) changed after the updates, e.g. "
				+ unfinished.stream().limit(6).map(p -> p.subtract(helper.absolutePos(origin)).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '200' '"qaInnRedrawn1111cStands1"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qaInnRedrawn1111cStands1")
	public void theBuiltInnStandsAfterBlockUpdates(GameTestHelper helper) {
		standsAfterUpdates(helper, StarterBlueprints.INN, ORIGIN);
	}

	//$ gametest_ticks_batch HUGE_AREA '200' '"qaInnRedrawn1111cStands2"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qaInnRedrawn1111cStands2")
	public void theBuiltInnIIStandsAfterBlockUpdates(GameTestHelper helper) {
		standsAfterUpdates(helper, StarterBlueprints.INN_2, ORIGIN);
	}

	//$ gametest_ticks_batch HUGE_AREA '200' '"qaInnRedrawn1111cStands3"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qaInnRedrawn1111cStands3")
	public void theBuiltInnIIIStandsAfterBlockUpdates(GameTestHelper helper) {
		standsAfterUpdates(helper, StarterBlueprints.INN_3, ORIGIN_3);
	}

	/** The Inn III (its cellar dug under it) keeps a working bar: a bed behind it makes the Innkeeper, the beds take a traveller. */
	//$ gametest_ticks_batch HUGE_AREA '100' '"qaInnRedrawn1111cInn3"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 100, batch = "qaInnRedrawn1111cInn3")
	public void theBuiltInnIIIKeepsItsBarAndBeds(GameTestHelper helper) {
		clean(helper);
		ServerLevel level = helper.getLevel();
		boolean legends = Legends.ENABLED;
		long time = level.getDayTime();
		Leftovers.after(helper, () -> {
			Legends.ENABLED = legends;
			level.setDayTime(time);
		});
		BuildPlan plan = paste(helper, StarterBlueprints.INN_3, ORIGIN_3);
		BoundingBox box = box(plan);
		helper.runAfterDelay(5, () -> {
			BlockPos counter = counter(helper, box);
			Villager keeper = innkeeper(helper, counter);
			keeper.setNoAi(true);
			try {
				Legends.ENABLED = false;
				level.setDayTime(24000L + 1000);
				String state = Innkeepers.tend(level, keeper, counter);
				List<Villager> guests = Innkeepers.guests(level, counter);
				helper.assertTrue("hosting".equals(state) && guests.size() == 1, "the Inn III's morning: " + state + ", " + guests.size() + " guests");
				Optional<BlockPos> bed = guests.get(0).getBrain().getMemory(MemoryModuleType.HOME).map(GlobalPos::pos);
				helper.assertTrue(bed.isPresent() && box.isInside(bed.get()) && bed.get().getY() == counter.getY() + 5,
					"the Inn III's traveller sleeps at " + bed + ", not in its guest rooms");
			} finally {
				Legends.ENABLED = legends;
				level.setDayTime(time);
			}
			Innkeepers.guests(level, counter).forEach(Villager::discard);
			helper.succeed();
		});
	}
}

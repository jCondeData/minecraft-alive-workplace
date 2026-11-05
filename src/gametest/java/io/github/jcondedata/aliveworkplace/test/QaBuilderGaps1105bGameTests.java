package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.BENCH;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.LOGGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ORIGIN;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.TALL_AREA;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.VILLAGER;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.fill;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.ground;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.oldBuild;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.rules;
import static io.github.jcondedata.aliveworkplace.test.QaBasementGround1104GameTests.stacks;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Cellars;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import java.util.ArrayDeque;
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
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-11-05, second pass), adversarial gaps written from the wiki: docs/wiki/cellars.md ("Dig the cellar" for the
 * Storehouse and the Kitchen, not only the Smithy: the workers' chests and their contents, a player's block in the room
 * above, the way down a closed hatch over a ladder a player can climb, the builder's sentence) and docs/wiki/builder.md
 * (a struck builder's bag "on the nearest open ground at least 4 blocks away", from a player's view: on ground a player
 * can walk to, not in a sealed cave or room the search found under it). Setup and helpers are
 * {@link QaBasementGround1104GameTests}'s and {@link QaBuilderGaps1105GameTests}'s.
 */
public class QaBuilderGaps1105bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** The struck tests' ground: earth from the area's floor up to y 8, the surface a builder stands on at y 9. */
	static final int SURFACE = 9;
	private static final int DIAMONDS = 3;

	// ------------------------------------------------------------------------------- Dig the cellar (Storehouse, Kitchen)

	/**
	 * A Storehouse finished before it had a cellar: the village's builder sells "Dig the cellar: Storehouse"; the player
	 * gives it back to him (their hand-over). When done: the whole Storehouse stands as drawn, every chest above the
	 * ground still holds what a player put in it, a player's gold block in the room above stays, the way down is a closed
	 * hatch with a climbable ladder under it to the cellar floor, and the player is told "... dug the cellar under the
	 * Storehouse and fitted it out."
	 */
	//$ gametest_ticks_batch TALL_AREA '20000' '"qaGapsBStorehouse"'
	@GameTest(template = TALL_AREA, timeoutTicks = 20000, batch = "qaGapsBStorehouse")
	public void aStorehousesDigTheCellarKeepsItsChestsAndLeavesAWalkableWayDown(GameTestHelper helper) {
		digTheCellar(helper, StarterBlueprints.STOREHOUSE, (h, b, p) -> {
		});
	}

	/** As the Storehouse's, for the Kitchen (11 by 11, its cellar under a bigger room). */
	//$ gametest_ticks_batch TALL_AREA '20000' '"qaGapsBKitchen"'
	@GameTest(template = TALL_AREA, timeoutTicks = 20000, batch = "qaGapsBKitchen")
	public void aKitchensDigTheCellarKeepsItsChestsAndLeavesAWalkableWayDown(GameTestHelper helper) {
		digTheCellar(helper, StarterBlueprints.KITCHEN, (h, b, p) -> {
		});
	}

	/**
	 * A Storehouse whose cellar a player half dug by hand in their own shape: just over half of the open spots still solid
	 * (so still offered, per the wiki), and two holes dug where the drawing has the cellar's walls. Dug by the
	 * builder: offered, and the whole Storehouse stands as drawn, the holes filled, with all the rest as for the old one.
	 */
	//$ gametest_ticks_batch TALL_AREA '20000' '"qaGapsBHalfDug"'
	@GameTest(template = TALL_AREA, timeoutTicks = 20000, batch = "qaGapsBHalfDug")
	public void aStorehouseHalfDugByHandInAnotherShapeIsStillOfferedAndDugAsDrawn(GameTestHelper helper) {
		digTheCellar(helper, StarterBlueprints.STOREHOUSE, QaBuilderGaps1105bGameTests::halfDug);
	}

	/** Just over half of the open spots left solid, the rest dug out from the top; and holes in the drawing's wall on two sides. */
	private static void halfDug(GameTestHelper helper, Blueprint whole, BlueprintData.Placement placement) {
		ServerLevel level = helper.getLevel();
		int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
		List<BlockPos> open = new ArrayList<>();
		Map<BlockPos, BlockState> drawn = new HashMap<>();
		for (Blueprint.Entry e : whole.blocks()) {
			drawn.put(e.pos(), e.state());
			if (e.pos().getY() < 0 && e.state().isAir()) {
				open.add(e.pos());
			}
		}
		// dug from the top down, as a player digs: the highest spots out first
		open.sort(java.util.Comparator.comparingInt((BlockPos p) -> -p.getY()).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
		int keep = open.size() / 2 + 1; // just over half still solid
		List<BlockPos> out = open.subList(0, open.size() - keep);
		out.forEach(p -> level.setBlock(placement.origin().offset(p), Blocks.AIR.defaultBlockState(), flags));
		// their own shape: holes in the drawing's wall on two sides, opening onto what they dug
		Set<Direction> sides = new HashSet<>();
		int dug = 0;
		for (BlockPos p : out) {
			for (Direction d : Direction.Plane.HORIZONTAL) {
				BlockPos n = p.relative(d);
				BlockState want = drawn.get(n);
				if (dug < 2 && !sides.contains(d) && want != null && !want.isAir() && want.isCollisionShapeFullBlock(level, BlockPos.ZERO)) {
					level.setBlock(placement.origin().offset(n), Blocks.AIR.defaultBlockState(), flags);
					sides.add(d);
					dug++;
				}
			}
		}
		helper.assertTrue(dug == 2, "setup: dug only " + dug + " hole(s) in the drawing's wall");
		helper.assertTrue(Cellars.needsDigging(level, whole.id(), placement), "setup: just over half solid (" + keep + " of " + open.size() + ") doesn't need digging");
		LOGGER.info("qa gaps b half dug: {} of {} open spots left solid, holes in the wall on {}", keep, open.size(), sides);
	}

	interface ByHand {
		void dig(GameTestHelper helper, Blueprint whole, BlueprintData.Placement placement);
	}

	private static void digTheCellar(GameTestHelper helper, StarterBlueprints.Entry entry, ByHand byHand) {
		String name = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(entry.id()).getString();
		Leftovers.clear(helper);
		Leftovers.buildSites(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		ground(helper, 30);
		ResourceLocation id = entry.id();
		BlueprintData.Placement placement = oldBuild(helper, entry, ORIGIN);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		Blueprint whole = BlueprintLibrary.get(level, id).orElseThrow();
		byHand.dig(helper, whole, placement);
		helper.assertTrue(Cellars.needsDigging(level, id, placement), "setup: the old " + name + " doesn't need its cellar dug");
		// every container of the building above the ground, with a player's diamonds in it
		List<BlockPos> boxes = new ArrayList<>();
		for (Blueprint.Entry e : whole.blocks()) {
			BlockPos p = placement.origin().offset(e.pos());
			if (e.pos().getY() >= 0 && (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity
					|| level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity) && level.getBlockEntity(p) instanceof Container c) {
				c.setItem(c.getContainerSize() - 1, new ItemStack(Items.DIAMOND, DIAMONDS));
				boxes.add(p);
			}
		}
		helper.assertFalse(boxes.isEmpty(), "setup: the " + name + " has no chest or barrel above the ground");
		BlockPos marker = playerBlockOverTheCellar(helper, whole);
		rules(helper);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		ModTrades.builderHomes(villager); // the player opens his trades
		MerchantOffer trade = villager.getOffers().stream().filter(o -> Cellars.isJob(o.getResult()) && BlueprintItem.data(o.getResult())
				.map(d -> d.structure().equals(id) && d.placement().equals(Optional.of(placement))).orElse(false))
			.findFirst().orElseThrow(() -> new GameTestAssertException("no \"Dig the cellar\" trade for the old " + name));
		String title = trade.getResult().getHoverName().getString();
		helper.assertTrue(title.equals("Dig the cellar: " + name), "the trade's blueprint is called \"" + title + "\"");
		helper.assertTrue(trade.getCostA().is(Items.EMERALD) && trade.getCostA().getCount() == 4, "the dig costs " + trade.getCostA());
		List<ItemStack> stock = new ArrayList<>();
		BuildPlan.create(Cellars.cellarOnly(whole), placement).materials().forEach((item, count) -> stock.addAll(stacks(item, count)));
		fill(helper, stock);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.CREATIVE, QaStageSixGameTests.name("qaGB"));
		c.player().moveTo(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 10, 1))));
		InteractionResult given = Builders.assign(c.player(), villager, trade.getResult().copy());
		BuildSite site = BuildSiteManager.get(level).all().stream().filter(s -> s.placement().equals(placement)).findFirst()
			.orElseThrow(() -> new GameTestAssertException("the job given to the builder started nothing (" + given + "): "
				+ QaBuilderEdges1104bGameTests.texts(c.seen())));
		helper.assertTrue(site.isCellarOnly(), "the " + name + "'s job didn't start a cellar dig");
		BuildPlan plan = site.plan(level);
		QaBasementGround1104GameTests.Setup s = new QaBasementGround1104GameTests.Setup(level, villager, site, plan, whole);
		QaBasementGround1104GameTests.watchDeath(helper, s);
		helper.succeedWhen(() -> {
			QaBasementGround1104GameTests.assertBuilt(helper, s);
			BlockPos markerAt = helper.absolutePos(marker);
			List<BlockPos> wrong = BuildPlan.create(whole, placement).unfinished(level).stream().filter(p -> !p.equals(markerAt)).toList();
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the whole " + name + " wrong after the dig, e.g. "
				+ wrong.stream().limit(4).map(p -> p.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(p)).toList());
			for (BlockPos p : boxes) {
				int n = level.getBlockEntity(p) instanceof Container box ? box.countItem(Items.DIAMOND) : -1;
				helper.assertTrue(n == DIAMONDS, "the " + name + "'s container at " + p.subtract(placement.origin()).toShortString() + " holds "
					+ n + " diamond(s) after the dig, not the player's " + DIAMONDS + " (" + level.getBlockState(p).getBlock() + ")");
			}
			helper.assertBlockPresent(Blocks.GOLD_BLOCK, marker);
			String way = assertWayDownWalkable(helper, whole, placement);
			List<String> said = QaBuilderEdges1104bGameTests.texts(c.seen()).stream().filter(t -> t.contains("dug the cellar")).toList();
			helper.assertTrue(said.size() == 1 && said.get(0).endsWith("dug the cellar under the " + name + " and fitted it out."),
				"the player wasn't told once \"... dug the cellar under the " + name + " and fitted it out.\": " + QaBuilderEdges1104bGameTests.texts(c.seen()));
			helper.assertFalse(Cellars.needsDigging(level, id, placement), "dug, the " + name + " still needs digging");
			ModTrades.builderHomes(villager);
			helper.assertTrue(villager.getOffers().stream().noneMatch(o -> Cellars.isJob(o.getResult())), "dug, the builder still sells \"Dig the cellar\"");
			helper.assertTrue(name.length() > 0 && !name.contains("aliveworkplace"), "the build's name is untranslated: " + name);
			LOGGER.info("qa gaps b {}: dug at tick {}, {} container(s) kept, way down {}, told {}", name, helper.getTick(), boxes.size(), way, said);
		});
	}

	/** A gold block on an open spot of the room above the cellar (y 1), off the way down's columns. */
	private static BlockPos playerBlockOverTheCellar(GameTestHelper helper, Blueprint blueprint) {
		Set<Long> columns = new HashSet<>();
		blueprint.blocks().stream().filter(e -> e.pos().getY() < 0).forEach(e -> columns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
		Set<Long> wayDown = new HashSet<>();
		blueprint.blocks().stream().filter(e -> e.pos().getY() >= 0 && (e.state().is(Blocks.LADDER) || e.state().is(BlockTags.TRAPDOORS)))
			.forEach(e -> wayDown.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
		Map<BlockPos, BlockState> drawn = new HashMap<>();
		blueprint.blocks().forEach(e -> drawn.put(e.pos(), e.state()));
		Blueprint.Entry spot = blueprint.blocks().stream()
			.filter(e -> e.pos().getY() == 1 && e.state().isAir())
			.filter(e -> Optional.ofNullable(drawn.get(e.pos().below())).map(b -> !b.isAir()).orElse(false))
			.filter(e -> columns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())) && !wayDown.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())))
			.findFirst().orElseThrow(() -> new GameTestAssertException("setup: no open floor over the cellar"));
		BlockPos rel = ORIGIN.offset(spot.pos());
		helper.setBlock(rel, Blocks.GOLD_BLOCK);
		return rel;
	}

	/**
	 * The way down per the wiki: a closed hatch in the floor (y 0) over the basement, clear two blocks above it, a ladder
	 * under it that holds (each rung on its wall) all the way to the cellar's floor. Returns what was found.
	 */
	static String assertWayDownWalkable(GameTestHelper helper, Blueprint whole, BlueprintData.Placement placement) {
		ServerLevel level = helper.getLevel();
		Set<Long> columns = new HashSet<>();
		whole.blocks().stream().filter(e -> e.pos().getY() < 0).forEach(e -> columns.add(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ())));
		int bottom = whole.blocks().stream().filter(e -> e.pos().getY() < 0).mapToInt(e -> e.pos().getY()).min().orElse(0);
		List<String> found = new ArrayList<>();
		for (Blueprint.Entry e : whole.blocks()) {
			if (e.pos().getY() != 0 || !e.state().is(BlockTags.TRAPDOORS) || !columns.contains(BlockPos.asLong(e.pos().getX(), 0, e.pos().getZ()))) {
				continue;
			}
			BlockPos hatch = placement.origin().offset(e.pos());
			BlockState h = level.getBlockState(hatch);
			String at = e.pos().toShortString();
			helper.assertTrue(h.is(BlockTags.TRAPDOORS), "no hatch at " + at + ": " + h);
			helper.assertFalse(h.getValue(BlockStateProperties.OPEN), "the hatch at " + at + " is left open");
			for (int up = 1; up <= 2; up++) {
				BlockPos a = hatch.above(up);
				helper.assertTrue(level.getBlockState(a).getCollisionShape(level, a).isEmpty() || level.getBlockState(a).is(Blocks.LADDER), "the hatch at " + at + " is blocked " + up + " above: " + level.getBlockState(a));
			}
			BlockPos p = hatch.below();
			int rungs = 0;
			while (level.getBlockState(p).is(Blocks.LADDER)) {
				helper.assertTrue(level.getBlockState(p).canSurvive(level, p), "the ladder at " + p.subtract(placement.origin()).toShortString() + " hangs on nothing");
				rungs++;
				p = p.below();
			}
			helper.assertTrue(rungs >= 2, "under the hatch at " + at + " only " + rungs + " rung(s), then " + level.getBlockState(p));
			// the ladder ends on the floor, or a step over it
			BlockPos floor = level.getBlockState(p).getCollisionShape(level, p).isEmpty() ? p.below() : p;
			helper.assertTrue(level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP),
				"under the ladder from " + at + " no floor to stand on: " + level.getBlockState(floor) + " at " + floor.subtract(placement.origin()).toShortString());
			helper.assertTrue(floor.getY() - placement.origin().getY() <= bottom + 1, "the ladder from " + at + " stops at "
				+ floor.subtract(placement.origin()).toShortString() + ", above the cellar's floor (y " + bottom + ")");
			found.add(at + ": " + rungs + " rungs to " + floor.subtract(placement.origin()).toShortString());
		}
		helper.assertTrue(!found.isEmpty(), "no hatch in the floor over the cellar");
		return found.toString();
	}


	// ------------------------------------------------------------------------------------ struck builders (B952, B964)

	/**
	 * A builder struck on flat grass, his Table 3 blocks east (in the bolt's reach), with a sealed cave (no way in) 4 to 6
	 * blocks under the surface beside and under the Table: everything lies on the surface (y 9), where a player can walk
	 * to it, not in the cave.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsBCave"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsBCave")
	public void aBuilderStruckOnGrassOverASealedCaveGetsHisBagOnTheSurface(GameTestHelper helper) {
		QaBuilderGaps1105GameTests.struck(helper, new BlockPos(13, SURFACE, 10), new BlockPos(10, SURFACE, 10), h -> {
			earth(h, (x, z) -> Blocks.GRASS_BLOCK.defaultBlockState(), null);
			cave(h);
		}, h -> {
		}, (told, lying) -> onSurface(helper, told));
	}


	/**
	 * A builder struck at the end of a 3-wide bridge 6 above open ground, his Table on the bridge's last block 3 east of
	 * him, a sealed stone room (flat roof one under the deck) under the bridge's end: it all lies where a player can get
	 * to it (on the bridge, the room's roof or the ground), never inside the room.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaGapsBBridge"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaGapsBBridge")
	public void aBuilderStruckOnABridgeOverASealedRoomGetsHisBagWhereAPlayerCanReachIt(GameTestHelper helper) {
		QaBuilderGaps1105GameTests.struck(helper, new BlockPos(13, 8, 10), new BlockPos(10, 8, 10), h -> bridgeAndRoom(h, false), h -> {
		}, (told, lying) -> {
			reachable(helper, told);
			helper.assertFalse(inRoom(rel(helper, told)), "the message names " + rel(helper, told).toShortString() + ", inside the sealed room");
		});
	}


	// ------------------------------------------------------------------------------------------------------ helpers

	interface Surface {
		BlockState at(int x, int z);
	}

	/** Stone from y 2 to 6, dirt at 7, {@code top} at y 8 and {@code over} (when not null) at y 9, the whole area. */
	static void earth(GameTestHelper h, Surface top, Surface over) {
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				for (int y = 2; y < SURFACE - 1; y++) {
					h.setBlock(new BlockPos(x, y, z), y < SURFACE - 2 ? Blocks.STONE : Blocks.DIRT);
				}
				h.setBlock(new BlockPos(x, SURFACE - 1, z), top.at(x, z));
				BlockState o = over == null ? null : over.at(x, z);
				if (o != null) {
					h.setBlock(new BlockPos(x, SURFACE, z), o);
				}
			}
		}
	}

	static boolean field(int x, int z) {
		return x >= 4 && x <= 19 && z >= 3 && z <= 17;
	}

	/** A sealed cave, three high (y 3 to 5: 4 to 6 under the surface), x 8 to 18, z 6 to 14; stone all round. */
	static void cave(GameTestHelper h) {
		for (int x = 8; x <= 18; x++) {
			for (int z = 6; z <= 14; z++) {
				for (int y = 3; y <= 5; y++) {
					h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
	}

	static boolean inRoom(BlockPos rel) {
		return rel.getX() >= 13 && rel.getX() <= 17 && rel.getZ() >= 8 && rel.getZ() <= 12 && rel.getY() >= 2 && rel.getY() <= 5;
	}

	/** An oak deck at y 7 (x 2 to 13, z 9 to 11), and a sealed stone-brick room under its end: walls x 12-18, z 7-13, y 2-5. */
	static void bridgeAndRoom(GameTestHelper h, boolean stairRoof) {
		for (int x = 12; x <= 18; x++) {
			for (int z = 7; z <= 13; z++) {
				for (int y = 2; y <= 5; y++) {
					boolean wall = x == 12 || x == 18 || z == 7 || z == 13;
					h.setBlock(new BlockPos(x, y, z), wall ? Blocks.STONE_BRICKS : Blocks.AIR);
				}
				h.setBlock(new BlockPos(x, 6, z), stairRoof ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH)
					: Blocks.STONE_BRICKS.defaultBlockState());
			}
		}
		for (int x = 2; x <= 13; x++) {
			for (int z = 9; z <= 11; z++) {
				h.setBlock(new BlockPos(x, 7, z), Blocks.OAK_PLANKS);
			}
		}
	}

	/** {@code abs} from the area's corner (as {@link GameTestHelper#absolutePos} counts, unturned). */
	static BlockPos rel(GameTestHelper helper, BlockPos abs) {
		return abs.subtract(helper.absolutePos(BlockPos.ZERO));
	}

	/** The spot is on the surface (y 9) over solid ground a player walks on. */
	static void onSurface(GameTestHelper helper, BlockPos told) {
		BlockPos rel = rel(helper, told);
		helper.assertTrue(rel.getY() == SURFACE, "the message names " + rel.toShortString() + ", not the surface (y " + SURFACE + ")"
			+ (rel.getY() < SURFACE - 1 ? ": in the sealed cave under it" : ""));
		reachable(helper, told);
	}

	/**
	 * A player can walk or climb to {@code told} without breaking a block: through spots with nothing solid (or a door), it
	 * joins the open sky within the area.
	 */
	static void reachable(GameTestHelper helper, BlockPos told) {
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds().inflate(10);
		ArrayDeque<BlockPos> queue = new ArrayDeque<>(List.of(told));
		Set<BlockPos> seen = new HashSet<>(List.of(told));
		while (!queue.isEmpty() && seen.size() < 40000) {
			BlockPos p = queue.poll();
			if (level.canSeeSky(p)) {
				return;
			}
			for (Direction d : Direction.values()) {
				BlockPos n = p.relative(d);
				if (!box.contains(Vec3.atCenterOf(n)) || seen.contains(n)) {
					continue;
				}
				BlockState s = level.getBlockState(n);
				if (s.getCollisionShape(level, n).isEmpty() || s.getBlock() instanceof DoorBlock) {
					seen.add(n);
					queue.add(n);
				}
			}
		}
		throw new GameTestAssertException("the message names " + rel(helper, told).toShortString() + " (" + level.getBlockState(told)
			+ " on " + level.getBlockState(told.below()).getBlock().getName().getString() + "), shut in: a player can't get there without digging ("
			+ seen.size() + " open spot(s) round it, none under the sky)");
	}
}

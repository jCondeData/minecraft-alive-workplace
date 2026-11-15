package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.EXTRA;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertFinished;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.assertNoFluid;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.loose;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.prepare;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.reload;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.start;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderTerrain1102GameTests.watchDeath;
import static io.github.jcondedata.aliveworkplace.test.QaBuilderWater1114bGameTests.fill;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Upkeep;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA (2026-11-15, lane-b-1115-0337's tester) of B1243's drain at the end of CLEAR, for builds placed turned and
 * mirrored, and for the jobs the drain is not for, written from the spec, not the code: docs/wiki/builder.md "Stages"
 * ("A build clicked onto the bed of a pond or a lake ... ends dry: at the end of CLEAR the water ... standing in its rooms
 * ... is taken away at once ... and the water just beside or above that would run back in is plugged with dirt"), B1243's
 * fix note ("at the end of CLEAR (not for a dig-out, repair or take-down)"), builder.md's turned and mirrored builds and
 * its B869 dig-out. QaBuilderDrainEdges1115b covers the unturned edge cases; these add:
 * <ul>
 * <li>a shut room 7 x 4 (not square, so a wrong turn shows) sunk in a lake and placed the way a player turns a blueprint
 * (anchored at its front's middle) a quarter, a half, three quarters, and mirrored and turned: the turned box is dry and
 * drawn exactly there, the lake beyond it untouched, only the spots right beside its sides plugged;</li>
 * <li>the same turned a quarter, saved and loaded mid-CLEAR: still dry;</li>
 * <li>a repair of a standing hut in a pond, two wall blocks knocked out: repaired, the pond never drained or dammed;</li>
 * <li>a take-down (a sneak-given blueprint) of a hut standing in a pond: down, no dirt left in the pond;</li>
 * <li>sand fallen into a sunk hut's room mid-build is dug out (B869's dig-out) with no new dam round it.</li>
 * </ul>
 */
public class QaBuilderDrainTurned1115bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	/** The shut room: 7 wide (x), 4 tall, 4 deep (z). */
	private static final Vec3i ROOM = new Vec3i(7, 4, 4);
	/** The lake: x and z 4-26, y 2-5 (four deep), cut into a stone plateau four high over the whole area. */
	private static final int LAKE_MIN = 4;
	private static final int LAKE_MAX = 26;
	private static final int LAKE_TOP = 5;
	/** Where the player clicks: the middle of the room's front, on the lake's bed. */
	private static final BlockPos ANCHOR = new BlockPos(15, 2, 15);

	// ---------------------------------------------------------------------------------------------- setup

	/**
	 * Scans the 7 x 4 x 4 shut room into a fresh blueprint: a cobblestone floor, cobblestone walls two high round its
	 * edge and a cobblestone roof, so its 5 x 2 x 2 inside is shut on every side (no door to swim through).
	 */
	static ResourceLocation room(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos at = new BlockPos(20, 14, 20);
		for (int x = 0; x < ROOM.getX(); x++) {
			for (int z = 0; z < ROOM.getZ(); z++) {
				boolean edge = x == 0 || z == 0 || x == ROOM.getX() - 1 || z == ROOM.getZ() - 1;
				helper.setBlock(at.offset(x, 0, z), Blocks.COBBLESTONE);
				helper.setBlock(at.offset(x, 1, z), edge ? Blocks.COBBLESTONE : Blocks.AIR);
				helper.setBlock(at.offset(x, 2, z), edge ? Blocks.COBBLESTONE : Blocks.AIR);
				helper.setBlock(at.offset(x, 3, z), Blocks.COBBLESTONE);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1115b_turned_room_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(at), ROOM, false, Blocks.STRUCTURE_VOID);
		fill(helper, 20, 20 + ROOM.getX() - 1, 14, 17, 20, 20 + ROOM.getZ() - 1, Blocks.AIR);
		return id;
	}

	/** The plateau and the lake. */
	static void lake(GameTestHelper helper) {
		fill(helper, 0, 29, 2, LAKE_TOP, 0, 29, Blocks.STONE);
		fill(helper, LAKE_MIN, LAKE_MAX, 2, LAKE_TOP, LAKE_MIN, LAKE_MAX, Blocks.WATER);
	}

	/**
	 * The room placed as a player places a turned or mirrored blueprint ({@link BlueprintItem#placementAt}: its front's
	 * middle at {@link #ANCHOR}), the bench, chest and barrels on the plateau (ground y 6), the room's list plus EXTRA of
	 * each stocked, the builder hired.
	 */
	static QaBuilderTerrain1102GameTests.Run startTurned(GameTestHelper helper, ResourceLocation id, Rotation rotation, Mirror mirror) {
		ServerLevel level = helper.getLevel();
		int g = LAKE_TOP + 1;
		BlockPos table = new BlockPos(2, g, 2);
		BlockPos chest = new BlockPos(2, g, 0);
		List<BlockPos> supplies = List.of(chest, new BlockPos(1, g, 0), new BlockPos(3, g, 0), new BlockPos(0, g, 0), new BlockPos(0, g, 2));
		helper.setBlock(table, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(table));
		for (BlockPos p : supplies) {
			helper.setBlock(p, p.equals(chest) ? Blocks.CHEST : Blocks.BARREL);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, g, 3));
		Builders.employ(level, builder, helper.absolutePos(table));
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), ROOM, helper.absolutePos(ANCHOR), rotation, mirror);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		if (site == null) {
			throw new GameTestAssertException(id + " didn't start turned " + rotation + " mirrored " + mirror);
		}
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		UUID siteId = site.id();
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(siteId) != null) {
				BuildSiteManager.get(level).remove(siteId);
			}
		});
		List<ItemStack> stock = new ArrayList<>();
		plan.materials().forEach((item, n) -> {
			int left = n + EXTRA;
			while (left > 0) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		for (int i = 0; i < stock.size(); i++) {
			Container c = helper.getBlockEntity(supplies.get(supplies.size() - 1 - i / 27));
			c.setItem(i % 27, stock.get(i));
		}
		LOGGER.info("qa 1115b turned: {} {} {} origin {}, materials {}, bounds {}", id, rotation, mirror, placement.origin(),
			plan.materials(), plan.bounds());
		return new QaBuilderTerrain1102GameTests.Run(level, new Villager[] {builder}, siteId, plan, g, supplies, new String[1]);
	}

	/** {@code rel} (blueprint coordinates) in the test's coordinates for a placement at {@code origin}, turned and mirrored. */
	static BlockPos turnedFrom(BlockPos origin, Rotation rotation, Mirror mirror, BlockPos rel) {
		return origin.offset(StructureTemplate.transform(rel, mirror, rotation, BlockPos.ZERO));
	}

	/**
	 * The turned room's box in world coordinates, worked out from its two corners as vanilla turns a template (world
	 * coordinates throughout: GameTestHelper.relativePos doesn't undo absolutePos in these runs).
	 */
	static BoundingBox expectedBox(GameTestHelper helper, Rotation rotation, Mirror mirror) {
		BlueprintData.Placement p = BlueprintItem.placementAt(helper.getLevel().dimension().location(), ROOM, helper.absolutePos(ANCHOR), rotation, mirror);
		return BoundingBox.fromCorners(turnedFrom(p.origin(), rotation, mirror, BlockPos.ZERO),
			turnedFrom(p.origin(), rotation, mirror, new BlockPos(ROOM.getX() - 1, ROOM.getY() - 1, ROOM.getZ() - 1)));
	}

	/**
	 * The turned room stands exactly in {@code box} (world coordinates): every spot of its shell cobblestone, every spot
	 * of its inside air with no fluid; the plan's own box is the same; the lake more than one block from the box is still
	 * all water sources (an unturned or wrongly turned room would have dug into it or left it plugged), and the spots right
	 * beside the box's sides are water or a dirt plug, its corner columns water.
	 */
	static void assertTurnedRoom(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r, BoundingBox box) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(r.plan().bounds().equals(box), "the plan's box " + r.plan().bounds() + " isn't the turned outline " + box);
		List<String> wrong = new ArrayList<>();
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int y = box.minY(); y <= box.maxY(); y++) {
				for (int z = box.minZ(); z <= box.maxZ(); z++) {
					boolean shell = x == box.minX() || x == box.maxX() || y == box.minY() || y == box.maxY() || z == box.minZ() || z == box.maxZ();
					BlockState s = level.getBlockState(new BlockPos(x, y, z));
					if (shell ? !s.is(Blocks.COBBLESTONE) : !s.isAir()) {
						wrong.add("(" + (x - box.minX()) + "," + (y - box.minY()) + "," + (z - box.minZ()) + " in the box)=" + s.getBlock().getDescriptionId());
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " spot(s) of the turned room " + box + " not as drawn (shell cobblestone, inside dry air): "
			+ wrong.stream().limit(12).toList());
		List<String> lake = new ArrayList<>();
		for (int rx = LAKE_MIN; rx <= LAKE_MAX; rx++) {
			for (int rz = LAKE_MIN; rz <= LAKE_MAX; rz++) {
				for (int ry = 2; ry <= LAKE_TOP; ry++) {
					BlockPos at = helper.absolutePos(new BlockPos(rx, ry, rz));
					int x = at.getX();
					int z = at.getZ();
					boolean inX = x >= box.minX() && x <= box.maxX();
					boolean inZ = z >= box.minZ() && z <= box.maxZ();
					if (inX && inZ) {
						continue;
					}
					boolean side = inX && (z == box.minZ() - 1 || z == box.maxZ() + 1) || inZ && (x == box.minX() - 1 || x == box.maxX() + 1);
					BlockState s = level.getBlockState(at);
					boolean water = s.is(Blocks.WATER) && s.getFluidState().isSource();
					if (!(water || side && s.is(Blocks.DIRT))) {
						lake.add("(" + rx + "," + ry + "," + rz + ")=" + s.getBlock().getDescriptionId() + (side ? " (beside)" : ""));
					}
				}
			}
		}
		helper.assertTrue(lake.isEmpty(), lake.size() + " spot(s) of the lake round the turned room " + box
			+ " changed (only the spots right beside its sides may be plugged): " + lake.stream().limit(12).toList());
	}

	/** Every one of the room's materials left loose: at least the EXTRA stocked (cobblestone), none lost. */
	static void materialsAddUp(GameTestHelper helper, QaBuilderTerrain1102GameTests.Run r) {
		Map<Item, Integer> off = new HashMap<>();
		for (Map.Entry<Item, Integer> e : r.plan().materials().entrySet()) {
			int got = loose(helper, r, e.getKey());
			if (got < EXTRA) {
				off.put(e.getKey(), got);
			}
		}
		helper.assertTrue(off.isEmpty(), "materials lost: left loose " + off + ", wanted at least " + EXTRA + " of each over " + r.plan().materials());
	}

	private void turnedRoomEndsDry(GameTestHelper helper, Rotation rotation, Mirror mirror) {
		prepare(helper);
		ResourceLocation id = room(helper);
		lake(helper);
		BoundingBox box = expectedBox(helper, rotation, mirror);
		QaBuilderTerrain1102GameTests.Run r = startTurned(helper, id, rotation, mirror);
		watchDeath(helper, r);
		helper.succeedWhen(() -> {
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			assertTurnedRoom(helper, r, box);
			materialsAddUp(helper, r);
			LOGGER.info("qa 1115b turned {} {}: room {} dry at tick {}", rotation, mirror, box, helper.getTick());
		});
	}

	// ---------------------------------------------------------------------------------------------- turned and mirrored

	/** The 7 x 4 shut room sunk in the lake, turned a quarter (clockwise): dry, exactly in its turned box, the lake kept. */
	//$ gametest_ticks_batch HUGE '16000' '"qa1115bTurn90"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1115bTurn90")
	public void aSunkRoomTurnedAQuarterDrainsOnlyItsTurnedBox(GameTestHelper helper) {
		turnedRoomEndsDry(helper, Rotation.CLOCKWISE_90, Mirror.NONE);
	}

	/** The same turned a half. */
	//$ gametest_ticks_batch HUGE '16000' '"qa1115bTurn180"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1115bTurn180")
	public void aSunkRoomTurnedAHalfDrainsOnlyItsTurnedBox(GameTestHelper helper) {
		turnedRoomEndsDry(helper, Rotation.CLOCKWISE_180, Mirror.NONE);
	}

	/** The same turned three quarters (a quarter counterclockwise). */
	//$ gametest_ticks_batch HUGE '16000' '"qa1115bTurn270"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1115bTurn270")
	public void aSunkRoomTurnedThreeQuartersDrainsOnlyItsTurnedBox(GameTestHelper helper) {
		turnedRoomEndsDry(helper, Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
	}

	/** The same mirrored (the mirror button: left to right) and turned a quarter. */
	//$ gametest_ticks_batch HUGE '16000' '"qa1115bTurnMirror"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1115bTurnMirror")
	public void aSunkRoomMirroredAndTurnedDrainsOnlyItsTurnedBox(GameTestHelper helper) {
		turnedRoomEndsDry(helper, Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT);
	}

	// ---------------------------------------------------------------------------------------------- save and reload

	/**
	 * The room turned a quarter and sunk in the lake, with six stone blocks on the bed inside its turned box (placed with
	 * a fixed random) so CLEAR has digging to do; saved and loaded (sites and builder) once CLEAR has dug some but not all
	 * of them: the room still ends dry and exactly in its turned box, the lake kept.
	 */
	//$ gametest_ticks_batch HUGE '16000' '"qa1115bTurnReload"'
	@GameTest(template = HUGE, timeoutTicks = 16000, batch = "qa1115bTurnReload")
	public void aTurnedSunkRoomSavedAndLoadedMidClearEndsDry(GameTestHelper helper) {
		prepare(helper);
		ResourceLocation id = room(helper);
		lake(helper);
		Rotation rotation = Rotation.CLOCKWISE_90;
		BoundingBox box = expectedBox(helper, rotation, Mirror.NONE);
		RandomSource random = RandomSource.create(1115L);
		Set<BlockPos> stones = new HashSet<>();
		while (stones.size() < 6) {
			// inside the room (not its shell), on the bed and one up
			BlockPos p = new BlockPos(box.minX() + 1 + random.nextInt(box.getXSpan() - 2), box.minY() + 1 + random.nextInt(2),
				box.minZ() + 1 + random.nextInt(box.getZSpan() - 2));
			stones.add(p);
		}
		for (BlockPos p : stones) {
			helper.getLevel().setBlockAndUpdate(p, Blocks.STONE.defaultBlockState());
		}
		QaBuilderTerrain1102GameTests.Run r = startTurned(helper, id, rotation, Mirror.NONE);
		watchDeath(helper, r);
		int[] reloadedAt = {-1};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (reloadedAt[0] >= 0 || s == null || s.stage() != BuildPlan.Stage.CLEAR) {
				return;
			}
			long left = stones.stream().filter(p -> helper.getLevel().getBlockState(p).is(Blocks.STONE)).count();
			if (left > 0 && left < stones.size()) {
				r.builder()[0] = reload(helper, r.level(), r.builder()[0]);
				reloadedAt[0] = (int) helper.getTick();
				LOGGER.info("qa 1115b turned reload: saved and loaded mid-CLEAR at tick {}, {} of {} stones left", helper.getTick(), left, stones.size());
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(reloadedAt[0] >= 0, "setup: never saved and loaded mid-CLEAR (stage " + (r.site() == null ? "done" : r.site().stage()) + ")");
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			assertTurnedRoom(helper, r, box);
			LOGGER.info("qa 1115b turned reload: dry at tick {}", helper.getTick());
		});
	}

	// ---------------------------------------------------------------------------------------------- not for a repair

	/** A stone plateau two high with a pond (x and z 6-18, two deep) in it, the test hut standing on its bed at (10, 2, 10). */
	private static BlockPos hutInPond(GameTestHelper helper) {
		fill(helper, 0, 29, 2, 3, 0, 29, Blocks.STONE);
		fill(helper, 6, 18, 2, 3, 6, 18, Blocks.WATER);
		ServerLevel level = helper.getLevel();
		StructureTemplate hut = level.getStructureManager().get(TEST_HUT).orElseThrow();
		BlockPos origin = helper.absolutePos(new BlockPos(10, 2, 10));
		hut.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1115L), net.minecraft.world.level.block.Block.UPDATE_ALL);
		return new BlockPos(10, 2, 10);
	}

	/** The pond outside the hut's box (x and z 10-14) is still all water sources, with no dirt anywhere in the pond. */
	private static void assertPondKept(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (int x = 6; x <= 18; x++) {
			for (int z = 6; z <= 18; z++) {
				boolean inBox = x >= 10 && x <= 14 && z >= 10 && z <= 14;
				for (int y = 2; y <= 5; y++) {
					BlockState s = helper.getBlockState(new BlockPos(x, y, z));
					if (s.is(Blocks.DIRT) || !inBox && y <= 3 && !(s.is(Blocks.WATER) && s.getFluidState().isSource())) {
						wrong.add("(" + x + "," + y + "," + z + ")=" + s.getBlock().getDescriptionId());
					}
				}
			}
		}
		helper.assertTrue(wrong.isEmpty(), wrong.size() + " spot(s) of the pond drained or plugged: " + wrong.stream().limit(12).toList());
	}

	/** The bench, a chest stocked with {@code stock} and the builder, on the plateau (ground y 4). */
	private static Villager hire(GameTestHelper helper, ItemStack... stock) {
		ServerLevel level = helper.getLevel();
		BlockPos table = new BlockPos(2, 4, 2);
		helper.setBlock(table, ModBlocks.BUILDERS_BENCH);
		QaBuilderDisruptionGameTests.ensurePoi(level, helper.absolutePos(table));
		helper.setBlock(new BlockPos(2, 4, 4), Blocks.CHEST);
		Container chest = helper.getBlockEntity(new BlockPos(2, 4, 4));
		for (int i = 0; i < stock.length; i++) {
			chest.setItem(i, stock[i]);
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 4, 3));
		Builders.employ(level, builder, helper.absolutePos(table));
		return builder;
	}

	/**
	 * A finished test hut stands on the bed of a pond two deep, water round it up to its walls' first course; two blocks
	 * of its east wall in that course are knocked out, so the pond runs into the room. The builder repairs it (repairs on,
	 * no asking): both blocks are back, the job ends, the pond round the hut is never drained or plugged with dirt (the
	 * drain is not for a repair), and 100 ticks after the job no water stands in the hut's room.
	 */
	//$ gametest_ticks_batch HUGE '6000' '"qa1115bTurnRepair"'
	@GameTest(template = HUGE, timeoutTicks = 6000, batch = "qa1115bTurnRepair")
	public void aRepairOfAHutInAPondNeverDrainsOrDamsThePond(GameTestHelper helper) {
		prepare(helper);
		boolean was = Upkeep.ENABLED;
		boolean asks = Upkeep.ASK;
		Upkeep.ENABLED = true;
		Upkeep.ASK = false;
		Leftovers.after(helper, () -> {
			Upkeep.ENABLED = was;
			Upkeep.ASK = asks;
		});
		ServerLevel level = helper.getLevel();
		BlockPos origin = hutInPond(helper);
		List<BlockPos> knocked = List.of(new BlockPos(14, 3, 12), new BlockPos(14, 3, 13));
		List<BlockState> drawn = new ArrayList<>();
		for (BlockPos p : knocked) {
			BlockState s = helper.getBlockState(p);
			helper.assertTrue(!s.isAir() && s.getFluidState().isEmpty(), "setup: the hut's east wall at " + p.toShortString() + " is " + s);
			drawn.add(s);
			helper.setBlock(p, Blocks.AIR);
		}
		Item item = drawn.get(0).getBlock().asItem();
		Villager builder = hire(helper, new ItemStack(item, 8));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(TEST_HUT, placement, builder.getUUID());
		boolean[] repairing = {false};
		int[] doneAt = {-1};
		helper.runAfterDelay(2, () -> {
			BuildSite site = ModAttachments.BUILDER_JOB.has(builder) ? Builders.activeSite(level, builder) : Upkeep.look(level, builder);
			helper.assertTrue(site != null && site.isRepair(), "no repair started: " + site);
			repairing[0] = true;
		});
		helper.onEachTick(() -> {
			if (repairing[0] && doneAt[0] < 0 && !ModAttachments.BUILDER_JOB.has(builder)) {
				doneAt[0] = (int) helper.getTick();
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(doneAt[0] >= 0, "still repairing");
			for (int i = 0; i < knocked.size(); i++) {
				helper.assertTrue(helper.getBlockState(knocked.get(i)).is(drawn.get(i).getBlock()),
					"the knocked-out wall at " + knocked.get(i).toShortString() + " is " + helper.getBlockState(knocked.get(i)) + ", drawn " + drawn.get(i));
			}
			assertPondKept(helper);
			helper.assertTrue(helper.getTick() - doneAt[0] >= 100, "waiting 100 ticks after the repair");
			List<String> wet = new ArrayList<>();
			for (int x = 11; x <= 13; x++) {
				for (int z = 11; z <= 13; z++) {
					for (int y = 3; y <= 4; y++) {
						if (!helper.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) {
							wet.add("(" + x + "," + y + "," + z + ")");
						}
					}
				}
			}
			helper.assertTrue(wet.isEmpty(), "water still in the repaired hut's room 100 ticks after: " + wet);
			LOGGER.info("qa 1115b turned repair: repaired at tick {}, pond kept", doneAt[0]);
		});
	}

	// ---------------------------------------------------------------------------------------------- not for a take-down

	/**
	 * The same hut standing in the same pond is taken down the way a player asks (a blueprint placed over it, sneak-given
	 * to the builder): the hut is gone, and no dirt plug is left anywhere in the pond or where the hut stood; the pond
	 * round it is all water sources still (the drain is not for a take-down).
	 */
	//$ gametest_ticks_batch HUGE '8000' '"qa1115bTurnTakeDown"'
	@GameTest(template = HUGE, timeoutTicks = 8000, batch = "qa1115bTurnTakeDown")
	public void aTakeDownOfAHutInAPondLeavesNoDirtPlugs(GameTestHelper helper) {
		prepare(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = hutInPond(helper);
		Villager builder = hire(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, new Vec3i(5, 4, 5));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(origin), Rotation.NONE, Mirror.NONE);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(placement)));
		Builders.assign(player, builder, blueprint, true);
		BuildSite site = Builders.activeSite(level, builder);
		helper.assertTrue(site != null && site.isDeconstruction(), "the sneak-given blueprint didn't start a take-down: " + site);
		UUID siteId = site.id();
		BuildPlan plan = site.plan(level);
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(siteId) != null) {
				BuildSiteManager.get(level).remove(siteId);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(siteId) == null, "still taking it down: " + site.stage());
			helper.assertTrue(plan.unfinished(level).isEmpty(), plan.unfinished(level).size() + " block(s) of the hut still standing");
			assertPondKept(helper);
			LOGGER.info("qa 1115b turned take-down: down at tick {}, pond kept", helper.getTick());
		});
	}

	// ---------------------------------------------------------------------------------------------- the dig-out

	/**
	 * The test hut sunk in a pond its footprint (two deep, x and z 6-10) in a stone plateau; once it is in DECORATION,
	 * two sand blocks drop into its room. The builder digs them out (B869's dig-out, back over CLEAR) and finishes: dry
	 * and as drawn, no sand in the room, both sand kept, and no dirt plug added round the hut since the first CLEAR ended
	 * (the drain runs at the end of CLEAR, not of a dig-out).
	 */
	//$ gametest_ticks_batch HUGE '12000' '"qa1115bTurnDigOut"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "qa1115bTurnDigOut")
	public void sandDugOutOfASunkHutAddsNoDam(GameTestHelper helper) {
		prepare(helper);
		fill(helper, 0, 29, 2, 3, 0, 29, Blocks.STONE);
		fill(helper, 6, 10, 2, 3, 6, 10, Blocks.WATER);
		QaBuilderTerrain1102GameTests.Run r = start(helper, 4, new BlockPos(6, 2, 6));
		watchDeath(helper, r);
		Set<BlockPos> dirtAfterClear = new HashSet<>();
		boolean[] cleared = {false};
		boolean[] dropped = {false};
		boolean[] dugOut = {false};
		helper.onEachTick(() -> {
			BuildSite s = r.site();
			if (s == null) {
				return;
			}
			if (!cleared[0] && s.stage() != BuildPlan.Stage.CLEAR) {
				cleared[0] = true;
				for (int x = 0; x <= 29; x++) {
					for (int z = 0; z <= 29; z++) {
						for (int y = 2; y <= 6; y++) {
							if (helper.getBlockState(new BlockPos(x, y, z)).is(Blocks.DIRT)) {
								dirtAfterClear.add(new BlockPos(x, y, z));
							}
						}
					}
				}
			}
			if (!dropped[0] && s.stage() == BuildPlan.Stage.DECORATION) {
				dropped[0] = true;
				helper.setBlock(new BlockPos(8, 4, 8), Blocks.SAND);
				helper.setBlock(new BlockPos(8, 3, 8), Blocks.SAND);
			}
			if (s.digOut()) {
				dugOut[0] = true;
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(dropped[0], "setup: the sand was never dropped");
			assertFinished(helper, r);
			assertNoFluid(helper, r);
			helper.assertTrue(dugOut[0], "the builder never went back for the sand (no dig-out)");
			int sandLeft = 0;
			BoundingBox box = r.plan().bounds();
			for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
				if (r.level().getBlockState(p).is(Blocks.SAND)) {
					sandLeft++;
				}
			}
			helper.assertTrue(sandLeft == 0, sandLeft + " sand left in the finished hut");
			int sand = loose(helper, r, Items.SAND);
			helper.assertTrue(sand == 2, sand + " sand loose after digging out 2");
			List<String> added = new ArrayList<>();
			for (int x = 0; x <= 29; x++) {
				for (int z = 0; z <= 29; z++) {
					for (int y = 2; y <= 6; y++) {
						BlockPos p = new BlockPos(x, y, z);
						if (helper.getBlockState(p).is(Blocks.DIRT) && !dirtAfterClear.contains(p)) {
							added.add(p.toShortString());
						}
					}
				}
			}
			helper.assertTrue(added.isEmpty(), "dirt added round the hut after the first CLEAR (the dig-out dammed): " + added);
			LOGGER.info("qa 1115b turned dig-out: dug out and dry at tick {}, {} dirt plugs from the first CLEAR", helper.getTick(), dirtAfterClear.size());
		});
	}
}

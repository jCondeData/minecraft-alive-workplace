package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.build.MaterialRules;
import io.github.jcondedata.aliveworkplace.mc.Lookup;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.CaravanTrail;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

/**
 * QA, qa-lane-b-1122-1538, ROADMAP 52.5's rebuilt Lost Caravan sites (the five trail marks, the Raiders' Stockade and
 * the Caravan Wreck), written from the blind briefs (tools/blueprints/briefs/{trail,raiders_stockade,caravan_wreck}.md),
 * not the code. The arc sets each one down turned a random quarter ("everything must work in all four rotations"), so
 * each is placed through the arc's own entry point ({@link ArcEffects#build}) in every turn on uneven ground, and then:
 * every block of the drawing is where the drawing has it, every block in the box can stay where it stands (a hung rag,
 * lantern, banner, tripwire hook or dirt path has its hold: a player's first block update would otherwise pop it), the
 * ground layer has no hole, nothing has dropped as an item, and the containers hold what the brief says (the
 * stockade's one loot chest the raiders' table, the wreck none).
 */
public class QaRebuiltSites1122bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:tall_area";
	/** Where the stockade or wreck goes (grass at y 3). */
	private static final BlockPos MIDDLE = new BlockPos(15, 3, 15);
	/** Five marks' spots, 10 or more apart so no two boxes (the brief: at most about 8 deep) meet. */
	private static final List<BlockPos> MARK_SPOTS = List.of(new BlockPos(5, 3, 5), new BlockPos(15, 3, 5), new BlockPos(25, 3, 6),
		new BlockPos(6, 3, 21), new BlockPos(21, 3, 22));

	private static void ground(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		int near = Arcs.NEAR;
		Arcs.NEAR = 0; // (the tests build the places themselves)
		Leftovers.after(helper, () -> Arcs.NEAR = near);
		ServerLevel level = helper.getLevel();
		AABB box = helper.getBounds();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2); // (the height map must find the ground)
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	/** A two-step hump near the front left of the middle and a hole two deep near the back right. */
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

	/** A step up every other column, and one hole two deep beside each mark's spot. */
	private static void steps(GameTestHelper helper) {
		for (int x = 0; x < 30; x += 2) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 4, z), Blocks.GRASS_BLOCK);
			}
		}
		for (BlockPos s : MARK_SPOTS) {
			helper.setBlock(new BlockPos(s.getX() + 1, 3, s.getZ() + 1), Blocks.AIR);
			helper.setBlock(new BlockPos(s.getX() + 1, 2, s.getZ() + 1), Blocks.AIR);
			helper.setBlock(new BlockPos(s.getX() + 1, 4, s.getZ() + 1), Blocks.AIR);
		}
	}

	private static StructurePlaceSettings turn(int r) {
		return new StructurePlaceSettings().setRotation(Rotation.values()[r]);
	}

	private static BlockPos cell(BlockPos origin, int r, BlockPos local) {
		return origin.offset(StructureTemplate.calculateRelativePosition(turn(r), local));
	}

	private static StructureTemplate template(ServerLevel level, ResourceLocation id) {
		return level.getStructureManager().get(id).orElseThrow(() -> new IllegalStateException("no template " + id));
	}

	/** The arc's own placing, turned {@code r}. */
	private static ArcState.ArcPlace build(GameTestHelper helper, ResourceLocation id, BlockPos middle, int r) {
		ArcState s = new ArcState("aliveworkplace:lost_caravan");
		ArcState.ArcPlace p = new ArcState.ArcPlace("piece", helper.absolutePos(middle), List.of(id.toString()), r);
		s.places.add(p);
		helper.assertTrue(ArcEffects.build(helper.getLevel(), s, p), "turn " + r + ": the build waited for ground already loaded: " + id);
		helper.assertTrue(p.placed == 1 && !p.skipped && p.origin != null, "turn " + r + ": " + id + " wasn't built: placed " + p.placed + ", skipped " + p.skipped);
		return p;
	}

	/**
	 * What is wrong with a placed site: a drawn block not at its cell, a block in the box that can't stay where it stands,
	 * a hole in (or under) the ground layer.
	 */
	private static List<String> whole(GameTestHelper helper, ResourceLocation id, BlockPos origin, int r) {
		ServerLevel level = helper.getLevel();
		StructureTemplate t = template(level, id);
		Blueprint d = Blueprint.fromTemplate(id, t, Lookup.lookup(BuiltInRegistries.BLOCK));
		List<String> out = new ArrayList<>();
		for (Blueprint.Entry e : d.blocks()) {
			if (e.state().isAir() || e.state().is(Blocks.STRUCTURE_VOID)) {
				continue;
			}
			BlockState wanted = e.state().mirror(Mirror.NONE).rotate(Rotation.values()[r]);
			BlockPos at = cell(origin, r, e.pos());
			BlockState actual = level.getBlockState(at);
			if (!MaterialRules.matches(actual, wanted)) {
				out.add("drawn " + e.pos().toShortString() + " " + name(wanted) + " is " + name(actual));
			}
		}
		BoundingBox box = t.getBoundingBox(turn(r), origin);
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			BlockState s = level.getBlockState(p);
			if (!s.isAir() && !s.canSurvive(level, p)) {
				out.add(name(s) + " at " + helper.relativePos(p).toShortString() + " has no hold");
			}
		}
		for (int x = box.minX(); x <= box.maxX(); x++) {
			for (int z = box.minZ(); z <= box.maxZ(); z++) {
				BlockPos g = new BlockPos(x, box.minY(), z);
				if (level.getBlockState(g).isAir() || level.getBlockState(g.below()).isAir()) {
					out.add("a hole in the ground layer at " + helper.relativePos(g).toShortString());
				}
			}
		}
		return out;
	}

	private static String name(BlockState s) {
		return BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath();
	}

	/** Nothing has popped off and dropped in the box (a block with no hold breaks on its first update). */
	private static void noDrops(GameTestHelper helper, BoundingBox box, String what) {
		AABB a = new AABB(box.minX() - 1, box.minY() - 1, box.minZ() - 1, box.maxX() + 2, box.maxY() + 2, box.maxZ() + 2);
		List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, a);
		helper.assertTrue(drops.isEmpty(), what + ": dropped " + drops.stream().map(i -> i.getItem().toString()).toList());
	}

	/** Every container in the box that carries a loot table, as "x,y,z table". */
	private static List<String> looted(GameTestHelper helper, BoundingBox box) {
		ServerLevel level = helper.getLevel();
		List<String> out = new ArrayList<>();
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (level.getBlockEntity(p) instanceof RandomizableContainerBlockEntity c && c.getLootTable() != null) {
				out.add(helper.relativePos(p).toShortString() + " " + c.getLootTable().location());
			}
		}
		return out;
	}

	// ---------- the five trail marks ----------

	/**
	 * The five marks, each turned {@code r}, on ground stepping up and down every other column with a hole two deep at
	 * each spot: each is built, whole (the brief: "everything must work in all four rotations"; mark 1's lantern, mark 3's
	 * banner, mark 4's dirt path, mark 5's tripwire hook, mark 2's rags each have their hold), its ground layer has no hole
	 * ("y = 0 drawn as ground in every cell" or left to the world's ground), no mark carries a loot table ("no loot table
	 * is set or expected"), and 20 ticks on nothing has dropped.
	 */
	private static void marks(GameTestHelper helper, int r) {
		ground(helper);
		steps(helper);
		List<String> problems = new ArrayList<>();
		List<BoundingBox> boxes = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			ResourceLocation id = CaravanTrail.MARKS.get(i);
			ArcState.ArcPlace p = build(helper, id, MARK_SPOTS.get(i), r);
			BoundingBox box = template(helper.getLevel(), id).getBoundingBox(turn(r), p.origin);
			boxes.add(box);
			for (String s : whole(helper, id, p.origin, r)) {
				problems.add("mark " + (i + 1) + ": " + s);
			}
			for (String s : looted(helper, box)) {
				problems.add("mark " + (i + 1) + " has a loot table: " + s);
			}
		}
		helper.assertTrue(problems.isEmpty(), "turn " + r + ": " + problems.size() + " problems, e.g. " + problems.subList(0, Math.min(8, problems.size())));
		helper.runAfterDelay(20, () -> {
			for (int i = 0; i < 5; i++) {
				noDrops(helper, boxes.get(i), "turn " + r + ", mark " + (i + 1));
			}
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '80' '"qaTrailMarks1122b0"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaTrailMarks1122b0")
	public void qaTheTrailMarksLieWholeUnturned(GameTestHelper helper) {
		marks(helper, 0);
	}

	//$ gametest_ticks_batch AREA '80' '"qaTrailMarks1122b1"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaTrailMarks1122b1")
	public void qaTheTrailMarksLieWholeTurnedOnce(GameTestHelper helper) {
		marks(helper, 1);
	}

	//$ gametest_ticks_batch AREA '80' '"qaTrailMarks1122b2"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaTrailMarks1122b2")
	public void qaTheTrailMarksLieWholeTurnedTwice(GameTestHelper helper) {
		marks(helper, 2);
	}

	//$ gametest_ticks_batch AREA '80' '"qaTrailMarks1122b3"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaTrailMarks1122b3")
	public void qaTheTrailMarksLieWholeTurnedThrice(GameTestHelper helper) {
		marks(helper, 3);
	}

	// ---------- the Raiders' Stockade ----------

	/**
	 * The stockade turned {@code r} on a hump and a hole: whole (every drawn block at its cell, every block with its hold,
	 * no hole in its ground), exactly one container carries a loot table and it is the plain chest at the drawing's
	 * (7, 1, 14) with the raiders' table `chests/bandit_camp`, the cage's iron door is shut in both halves, and 20 ticks
	 * on nothing has dropped.
	 */
	private static void stockade(GameTestHelper helper, int r) {
		ground(helper);
		humpAndHole(helper);
		ServerLevel level = helper.getLevel();
		ArcState.ArcPlace p = build(helper, CaravanTrail.STOCKADE, MIDDLE, r);
		List<String> problems = new ArrayList<>(whole(helper, CaravanTrail.STOCKADE, p.origin, r));
		BoundingBox box = template(level, CaravanTrail.STOCKADE).getBoundingBox(turn(r), p.origin);
		BlockPos chest = cell(p.origin, r, CaravanTrail.LOOT_CHEST);
		ResourceKey<LootTable> raiders = ResourceKey.create(Registries.LOOT_TABLE, AliveWorkplace.id("chests/bandit_camp"));
		List<String> looted = looted(helper, box);
		if (!(level.getBlockEntity(chest) instanceof ChestBlockEntity c && level.getBlockState(chest).is(Blocks.CHEST) && raiders.equals(c.getLootTable()))) {
			problems.add("the loot chest at " + helper.relativePos(chest).toShortString() + " isn't a plain chest with the raiders' table: " + level.getBlockState(chest));
		}
		if (looted.size() != 1) {
			problems.add(looted.size() + " containers with a loot table, not 1: " + looted);
		}
		BlockPos door = cell(p.origin, r, CaravanTrail.CAGE_DOOR);
		if (!(level.getBlockState(door).is(Blocks.IRON_DOOR) && level.getBlockState(door.above()).is(Blocks.IRON_DOOR)
			&& !level.getBlockState(door).getValue(DoorBlock.OPEN))) {
			problems.add("the cage's iron door isn't there and shut: " + level.getBlockState(door) + " / " + level.getBlockState(door.above()));
		}
		helper.assertTrue(problems.isEmpty(), "turn " + r + ": " + problems.size() + " problems, e.g. " + problems.subList(0, Math.min(8, problems.size())));
		helper.runAfterDelay(20, () -> {
			noDrops(helper, box, "turn " + r);
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '80' '"qaStockadeWhole1122b0"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaStockadeWhole1122b0")
	public void qaTheStockadeLiesWholeUnturned(GameTestHelper helper) {
		stockade(helper, 0);
	}

	//$ gametest_ticks_batch AREA '80' '"qaStockadeWhole1122b1"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaStockadeWhole1122b1")
	public void qaTheStockadeLiesWholeTurnedOnce(GameTestHelper helper) {
		stockade(helper, 1);
	}

	//$ gametest_ticks_batch AREA '80' '"qaStockadeWhole1122b2"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaStockadeWhole1122b2")
	public void qaTheStockadeLiesWholeTurnedTwice(GameTestHelper helper) {
		stockade(helper, 2);
	}

	//$ gametest_ticks_batch AREA '80' '"qaStockadeWhole1122b3"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaStockadeWhole1122b3")
	public void qaTheStockadeLiesWholeTurnedThrice(GameTestHelper helper) {
		stockade(helper, 3);
	}

	// ---------- the Caravan Wreck ----------

	/**
	 * The wreck turned {@code r} on a hump and a hole: whole (every drawn block at its cell, every block with its hold:
	 * the brief's lantern "hangs or stands on a stand", no hole in its ground), no container carries a loot table ("none
	 * filled, no loot table is set"), and 20 ticks on nothing has dropped.
	 */
	private static void wreck(GameTestHelper helper, int r) {
		ground(helper);
		humpAndHole(helper);
		ServerLevel level = helper.getLevel();
		ArcState.ArcPlace p = build(helper, CaravanTrail.WRECK, MIDDLE, r);
		List<String> problems = new ArrayList<>(whole(helper, CaravanTrail.WRECK, p.origin, r));
		BoundingBox box = template(level, CaravanTrail.WRECK).getBoundingBox(turn(r), p.origin);
		for (String s : looted(helper, box)) {
			problems.add("a loot table in the wreck: " + s);
		}
		helper.assertTrue(problems.isEmpty(), "turn " + r + ": " + problems.size() + " problems, e.g. " + problems.subList(0, Math.min(8, problems.size())));
		helper.runAfterDelay(20, () -> {
			noDrops(helper, box, "turn " + r);
			helper.succeed();
		});
	}

	//$ gametest_ticks_batch AREA '80' '"qaWreckWhole1122b0"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaWreckWhole1122b0")
	public void qaTheWreckLiesWholeUnturned(GameTestHelper helper) {
		wreck(helper, 0);
	}

	//$ gametest_ticks_batch AREA '80' '"qaWreckWhole1122b1"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaWreckWhole1122b1")
	public void qaTheWreckLiesWholeTurnedOnce(GameTestHelper helper) {
		wreck(helper, 1);
	}

	//$ gametest_ticks_batch AREA '80' '"qaWreckWhole1122b2"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaWreckWhole1122b2")
	public void qaTheWreckLiesWholeTurnedTwice(GameTestHelper helper) {
		wreck(helper, 2);
	}

	//$ gametest_ticks_batch AREA '80' '"qaWreckWhole1122b3"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "qaWreckWhole1122b3")
	public void qaTheWreckLiesWholeTurnedThrice(GameTestHelper helper) {
		wreck(helper, 3);
	}
}

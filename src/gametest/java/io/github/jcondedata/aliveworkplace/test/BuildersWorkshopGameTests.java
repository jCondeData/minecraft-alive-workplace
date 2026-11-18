package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.pathfinder.Path;

/**
 * ROADMAP 50.1: the Builder's Workshop rebuilt from scratch (Version B, the owner's pick: a rubble workshop under a
 * jettied brick-and-timber loft, a catslide roof over the store, a jib crane in the materials yard;
 * tools/blueprints/js/builders_workshop_b). Its blueprint keeps the brief's rules (one Blueprint Table and no other job
 * block, a bed, a chest within the builder's supply reach of the table, the crane that says "builder" from the street);
 * a villager walks in by the front door to the table and its chest, and the loft's bed and chest are reached by the
 * ladder; a builder builds it to DONE within 12,000 ticks; and its village piece, in all five styles, keeps the village
 * contract's fixed cells (the street's jigsaw and the front row, the door, the table, the supply chest, the reference
 * bed, the villager spawn) with the table its only job block.
 */
public class BuildersWorkshopGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final StarterBlueprints.Entry WORKSHOP = StarterBlueprints.BUILDERS_WORKSHOP;
	// The blueprint's cells (the file: plan.mjs's coordinates moved one block east, file x = plan x + 1)
	private static final BlockPos DOOR = new BlockPos(5, 1, 2);
	private static final BlockPos TABLE = new BlockPos(3, 1, 7);
	private static final BlockPos CHEST = new BlockPos(3, 1, 6);
	private static final BlockPos LOFT_BED_HEAD = new BlockPos(3, 6, 7);
	/** The crane's mast foot (plan.mjs OUTSIDE.crane, 11 logs tall), its jib running west to the hook's chain. */
	private static final BlockPos MAST = new BlockPos(16, 1, 3);
	private static final BlockPos HOOK = new BlockPos(14, 10, 3);

	private static Blueprint blueprint(GameTestHelper helper) {
		return BlueprintLibrary.get(helper.getLevel(), WORKSHOP.id()).orElseThrow(() -> new GameTestAssertException("no blueprint " + WORKSHOP.id()));
	}

	private static Map<BlockPos, BlockState> cells(Blueprint b) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		for (Blueprint.Entry e : b.blocks()) {
			out.put(e.pos(), e.state());
		}
		return out;
	}

	private static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return cells.getOrDefault(pos, Blocks.AIR.defaultBlockState());
	}

	/** A block a villager of any job takes as its job site (a workstation). */
	private static boolean jobSite(BlockState state) {
		return PoiTypes.forState(state).filter(h -> BuiltInRegistries.VILLAGER_PROFESSION.stream()
			.anyMatch(p -> p != VillagerProfession.NONE && p.heldJobSite().test(h))).isPresent();
	}

	private static boolean bedHead(BlockState state) {
		return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD;
	}

	/**
	 * The blueprint: its declared size (18 x 16 x 11, within the 18 x 18 x 20 plot), the front door on the z = 0 side,
	 * exactly one job block (the Blueprint Table) and no other of any job, a bed, the table's chest within the builder's
	 * supply reach (8 across, 4 up or down), no calcite, jigsaw, structure void or entity, and the crane standing.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theWorkshopHasOneTableABedAChestAndItsCrane(GameTestHelper helper) {
		Blueprint b = blueprint(helper);
		helper.assertTrue(b.size().equals(WORKSHOP.size()) && b.size().equals(new Vec3i(18, 16, 11)), "the Builder's Workshop is " + b.size());
		Map<BlockPos, BlockState> cells = cells(b);
		BlockState door = at(cells, DOOR);
		helper.assertTrue(door.getBlock() instanceof DoorBlock && door.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER, "no front door at " + DOOR + ": " + door);
		List<String> jobs = cells.entrySet().stream().filter(e -> jobSite(e.getValue()))
			.map(e -> BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock()).getPath() + " " + e.getKey().toShortString()).toList();
		helper.assertTrue(jobs.size() == 1 && at(cells, TABLE).is(ModBlocks.BLUEPRINT_TABLE), "job blocks: " + jobs + " (one Blueprint Table at " + TABLE + ")");
		helper.assertTrue(cells.values().stream().noneMatch(s -> s.is(ModBlocks.BUILDERS_BENCH) || s.is(Blocks.CRAFTING_TABLE) || s.is(Blocks.BARREL)),
			"a Builder's Bench, crafting table or barrel in the workshop");
		helper.assertTrue(cells.values().stream().filter(BuildersWorkshopGameTests::bedHead).count() >= 1, "no bed");
		helper.assertTrue(bedHead(at(cells, LOFT_BED_HEAD)), "the loft's bed isn't at " + LOFT_BED_HEAD + ": " + at(cells, LOFT_BED_HEAD));
		helper.assertTrue(at(cells, CHEST).is(Blocks.CHEST), "no chest beside the table at " + CHEST);
		long nearTable = cells.entrySet().stream().filter(e -> e.getValue().is(Blocks.CHEST) && Math.abs(e.getKey().getX() - TABLE.getX()) <= 8
			&& Math.abs(e.getKey().getZ() - TABLE.getZ()) <= 8 && Math.abs(e.getKey().getY() - TABLE.getY()) <= 4).count();
		helper.assertTrue(nearTable >= 1, "no chest within the builder's reach of the table");
		helper.assertTrue(cells.values().stream().noneMatch(s -> s.is(Blocks.CALCITE) || s.is(Blocks.JIGSAW) || s.is(Blocks.STRUCTURE_VOID)),
			"calcite, a jigsaw or a structure void in the buildable");
		helper.assertTrue(b.entities().isEmpty(), "the blueprint comes with an entity: " + b.entities());
		// the crane: the mast 11 logs tall, a slab cap, the jib out west, the hook's chain hanging from its tip
		for (int y = 0; y < 11; y++) {
			BlockState log = at(cells, MAST.above(y));
			helper.assertTrue(log.is(Blocks.DARK_OAK_LOG) && log.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y, "the crane's mast is broken at " + MAST.above(y) + ": " + log);
		}
		helper.assertTrue(at(cells, MAST.above(11)).is(Blocks.DARK_OAK_SLAB), "the mast has no cap");
		helper.assertTrue(at(cells, MAST.offset(-1, 10, 0)).is(Blocks.DARK_OAK_LOG) && at(cells, MAST.offset(-2, 10, 0)).is(Blocks.DARK_OAK_LOG),
			"the crane has no jib");
		helper.assertTrue(at(cells, HOOK).is(Blocks.CHAIN), "no hook chain under the jib's tip at " + HOOK);
		helper.succeed();
	}

	/**
	 * Walked from the ground outside (QaBasementGameTests.walk: a block at a time, doors opened, ladders climbed), the
	 * Blueprint Table, every chest and the bed's head are each within a player's reach of a cell a villager reaches: the
	 * table and its chest by the front door, the loft's bed and chest by the ladder.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theTableTheChestsAndTheBedCanBeWalkedTo(GameTestHelper helper) {
		Blueprint b = blueprint(helper);
		Map<BlockPos, BlockState> blocks = QaBasementGameTests.blocks(b);
		int sx = b.size().getX();
		int sz = b.size().getZ();
		List<BlockPos> outside = new ArrayList<>();
		for (int x = -2; x <= sx + 1; x++) {
			for (int z = -2; z <= sz + 1; z++) {
				if (x < -1 || x > sx || z < -1 || z > sz) {
					outside.add(new BlockPos(x, 0, z));
				}
			}
		}
		Set<BlockPos> reached = QaBasementGameTests.walk(blocks, outside, sx, sz, b.size().getY(), -b.basement());
		List<String> cutOff = new ArrayList<>();
		int targets = 0;
		for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet()) {
			BlockState s = e.getValue();
			if (!(s.is(Blocks.CHEST) || s.is(ModBlocks.BLUEPRINT_TABLE) || bedHead(s))) {
				continue;
			}
			targets++;
			BlockPos c = e.getKey();
			boolean near = false;
			for (BlockPos p : BlockPos.betweenClosed(c.offset(-2, -1, -2), c.offset(2, 1, 2))) {
				if (reached.contains(p)) {
					near = true;
					break;
				}
			}
			if (!near) {
				cutOff.add(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath() + " " + c.toShortString());
			}
		}
		helper.assertTrue(targets >= 7, "only " + targets + " tables, chests and beds checked");
		helper.assertTrue(cutOff.isEmpty(), "nobody can walk to: " + cutOff);
		// the loft is reached by the ladder: a cell on the loft floor beside the bed is walked to
		helper.assertTrue(reached.stream().anyMatch(p -> p.getY() == LOFT_BED_HEAD.getY() && p.getX() >= 3 && p.getX() <= 7 && p.getZ() >= 2 && p.getZ() <= 8),
			"nobody walks the loft floor: the ladder doesn't reach it");
		helper.succeed();
	}

	private static final BlockPos PLACED = new BlockPos(6, 2, 10);

	/**
	 * The workshop placed as it stands once built: a villager three blocks before the front door finds a path in by the
	 * door (villagers open doors) to the Blueprint Table and to its chest, each reached, not stopped short.
	 */
	//$ gametest_ticks_batch HUGE '200' '"buildersWorkshopWalkIn"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "buildersWorkshopWalkIn")
	public void aVillagerWalksInByTheDoorToTheTableAndItsChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager().get(WORKSHOP.id()).orElseThrow(() -> new GameTestAssertException("missing " + WORKSHOP.id()));
		BlockPos origin = helper.absolutePos(PLACED);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(501L), 2);
		helper.assertTrue(level.getBlockState(origin.offset(TABLE)).is(ModBlocks.BLUEPRINT_TABLE), "setup: no table at " + TABLE);
		Villager villager = helper.spawn(EntityType.VILLAGER, PLACED.offset(DOOR.getX(), 0, -3));
		// only its pathfinding is asked, from where it stands (asked again each tick until it stands on the ground)
		helper.succeedWhen(() -> {
			for (BlockPos target : List.of(TABLE, CHEST)) {
				// the floor cell in front of it, on the room side (the table and chest stand against the west wall)
				BlockPos stand = origin.offset(target.east());
				Path path = villager.getNavigation().createPath(stand, 0);
				helper.assertTrue(path != null && path.canReach(), "no path in to the cell before the " + (target.equals(TABLE) ? "table" : "chest")
					+ " at " + target.east() + ": " + (path == null ? "none" : "ends at " + helper.relativePos(path.getTarget()).toShortString()
					+ ", " + path.getNodeCount() + " nodes"));
				boolean byTheDoor = false;
				for (int i = 0; i < path.getNodeCount(); i++) {
					if (path.getNode(i).asBlockPos().equals(origin.offset(DOOR))) {
						byTheDoor = true;
					}
				}
				helper.assertTrue(byTheDoor, "the way to " + target + " doesn't come in by the front door");
			}
		});
	}

	// --- built by a builder ------------------------------------------------------------------------------------------

	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos[] CHESTS = {new BlockPos(3, 2, 6), new BlockPos(3, 2, 8), new BlockPos(3, 2, 10), new BlockPos(3, 2, 12)};
	private static final BlockPos BUILDER = new BlockPos(5, 2, 3);
	private static final BlockPos HOUSE = new BlockPos(10, 2, 10);

	/**
	 * A builder with the supplies in his chests builds the workshop to DONE within 12,000 ticks (build delay 2, no help):
	 * its door, the one Blueprint Table, the bed, the table's chest and the crane's mast and hook stand as drawn.
	 */
	//$ gametest_ticks_batch HUGE '12000' '"buildersWorkshopBuilt"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "buildersWorkshopBuilt")
	public void aBuilderBuildsTheWorkshopToDone(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Blueprint blueprint = blueprint(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		for (BlockPos c : CHESTS) {
			helper.setBlock(c, Blocks.CHEST);
		}
		var report = BlueprintSupplies.check(level, new BlueprintData(WORKSHOP.id(), Optional.of(blueprint.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> stacks = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		// the brief's material budget: at most 81 stacks (WorkplacesGameTests stocks three chests of 27)
		helper.assertTrue(!stacks.isEmpty() && stacks.size() <= 81, "the Builder's Workshop needs " + stacks.size() + " stacks");
		for (int i = 0; i < stacks.size(); i++) {
			((Container) helper.getBlockEntity(CHESTS[i / 27])).setItem(i % 27, stacks.get(i));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, WORKSHOP.id(), placement);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage());
			BlockState door = helper.getBlockState(HOUSE.offset(DOOR));
			helper.assertTrue(door.getBlock() instanceof DoorBlock, "no front door once built: " + door);
			helper.assertTrue(helper.getBlockState(HOUSE.offset(TABLE)).is(ModBlocks.BLUEPRINT_TABLE), "no Blueprint Table once built");
			helper.assertTrue(helper.getBlockState(HOUSE.offset(CHEST)).is(Blocks.CHEST), "no chest by the table once built");
			helper.assertTrue(bedHead(helper.getBlockState(HOUSE.offset(LOFT_BED_HEAD))), "no bed in the loft once built: "
				+ helper.getBlockState(HOUSE.offset(LOFT_BED_HEAD)));
			helper.assertTrue(helper.getBlockState(HOUSE.offset(MAST.above(10))).is(Blocks.DARK_OAK_LOG)
				&& helper.getBlockState(HOUSE.offset(HOOK)).is(Blocks.CHAIN), "the crane isn't up once built");
		});
	}

	// --- the village piece -------------------------------------------------------------------------------------------

	private record Cell(BlockState state, CompoundTag nbt) {
	}

	/** A template's stored cells (structure voids are not stored), with their block entities' data. */
	private static Map<BlockPos, Cell> cells(StructureTemplate template) {
		CompoundTag tag = template.save(new CompoundTag());
		ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
		List<BlockState> byIndex = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			byIndex.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
		}
		Map<BlockPos, Cell> out = new HashMap<>();
		ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < blocks.size(); i++) {
			CompoundTag b = blocks.getCompound(i);
			ListTag pos = b.getList("pos", Tag.TAG_INT);
			out.put(new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2)),
				new Cell(byIndex.get(b.getInt("state")), b.contains("nbt", Tag.TAG_COMPOUND) ? b.getCompound("nbt") : null));
		}
		return out;
	}

	private static BlockState state(Map<BlockPos, Cell> cells, BlockPos pos) {
		Cell c = cells.get(pos);
		return c == null ? Blocks.STRUCTURE_VOID.defaultBlockState() : c.state();
	}

	/**
	 * Each style's village piece keeps the contract the Java reads by template coordinate (VillagePieces,
	 * VillageGameTests): within 18 x 17 x 18; the street's jigsaw at (4, 1, 0) and nothing placed in the front row at
	 * y = 0, x 0..8; the door at (4, 1, 2) facing south; the Blueprint Table at (2, 1, 7) facing east and the only job
	 * block (no crafting table: a Carpenter's); the supply chest at (2, 1, 6) with its loot; its one bed, foot (6, 1, 6)
	 * head (6, 1, 7) facing south (the reference bed every village house of ours is found by); the worker pool's jigsaw at
	 * (4, 0, 5) with (3, 1, 5), (4, 1, 5) and (4, 2, 5) clear for the villagers who stand there; no villager stored.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyVillageStyleKeepsTheWorkshopsFixedCells(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String style : VillageHouses.STYLES) {
			var id = VillageHouses.workshop(style);
			StructureTemplate template = level.getStructureManager().get(id).orElseThrow(() -> new GameTestAssertException("missing " + id));
			Vec3i size = template.getSize();
			helper.assertTrue(size.getX() <= 18 && size.getY() <= 17 && size.getZ() <= 18, id + " is " + size);
			Map<BlockPos, Cell> cells = cells(template);
			Cell street = cells.get(new BlockPos(4, 1, 0));
			helper.assertTrue(street != null && street.state().is(Blocks.JIGSAW) && street.nbt() != null
				&& street.nbt().getString("name").equals("minecraft:building_entrance"), id + " has no street jigsaw at (4, 1, 0)");
			for (int x = 0; x <= 8; x++) {
				BlockState s = state(cells, new BlockPos(x, 0, 0));
				helper.assertTrue(s.is(Blocks.STRUCTURE_VOID), id + " places " + s + " in the front row at (" + x + ", 0, 0)");
			}
			BlockState door = state(cells, new BlockPos(4, 1, 2));
			helper.assertTrue(door.getBlock() instanceof DoorBlock && door.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
				&& door.getValue(DoorBlock.FACING) == Direction.SOUTH, id + "'s door at (4, 1, 2) is " + door);
			BlockState table = state(cells, new BlockPos(2, 1, 7));
			helper.assertTrue(table.is(ModBlocks.BLUEPRINT_TABLE) && table.getValue(BlockStateProperties.HORIZONTAL_FACING) == Direction.EAST,
				id + "'s table at (2, 1, 7) is " + table);
			List<String> jobs = cells.entrySet().stream().filter(e -> jobSite(e.getValue().state()))
				.map(e -> BuiltInRegistries.BLOCK.getKey(e.getValue().state().getBlock()).getPath() + " " + e.getKey().toShortString()).toList();
			helper.assertTrue(jobs.size() == 1, id + "'s job blocks: " + jobs);
			Cell chest = cells.get(new BlockPos(2, 1, 6));
			helper.assertTrue(chest != null && chest.state().is(Blocks.CHEST) && chest.nbt() != null
				&& chest.nbt().getString("LootTable").equals("aliveworkplace:chests/village_builders_workshop"), id + " has no supply chest at (2, 1, 6)");
			List<BlockPos> heads = cells.entrySet().stream().filter(e -> bedHead(e.getValue().state())).map(Map.Entry::getKey).toList();
			helper.assertTrue(heads.equals(List.of(new BlockPos(6, 1, 7))), id + "'s bed heads: " + heads);
			BlockState head = state(cells, new BlockPos(6, 1, 7));
			BlockState foot = state(cells, new BlockPos(6, 1, 6));
			helper.assertTrue(head.getValue(BedBlock.FACING) == Direction.SOUTH && foot.getBlock() instanceof BedBlock
				&& foot.getValue(BedBlock.PART) == BedPart.FOOT, id + "'s bed: foot " + foot + ", head " + head);
			Cell spawn = cells.get(new BlockPos(4, 0, 5));
			helper.assertTrue(spawn != null && spawn.state().is(Blocks.JIGSAW) && spawn.nbt() != null
				&& spawn.nbt().getString("pool").equals("aliveworkplace:village/" + style + "/workers"), id + " spawns no villager at (4, 0, 5)");
			for (BlockPos clear : List.of(new BlockPos(3, 1, 5), new BlockPos(4, 1, 5), new BlockPos(4, 2, 5), new BlockPos(3, 2, 5))) {
				helper.assertTrue(state(cells, clear).isAir(), id + " isn't clear at " + clear.toShortString() + ": " + state(cells, clear));
			}
			for (BlockPos path : List.of(new BlockPos(0, 1, 6), new BlockPos(0, 1, 3), new BlockPos(0, 1, 1))) {
				BlockState s = state(cells, path);
				helper.assertTrue(s.isAir() || s.is(Blocks.STRUCTURE_VOID), id + " blocks the way round outside the west wall at " + path.toShortString() + ": " + s);
			}
			helper.assertTrue(template.save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND).isEmpty(), id + " stores a villager");
		}
		helper.succeed();
	}
}

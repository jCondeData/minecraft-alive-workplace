package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.furnish.Resting;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA 2026-10-28, written from the bug texts of B639 (the Clinic's ward: one real bed and three "fake beds" of slab and
 * carpet, nobody can sleep in them, and an open block in the ceiling where the roof's stair runs meet) and B641 (the
 * Terrace's west kitchen kept the spruce stair chair, the slab-and-fence table and crockery in the air while the east
 * kitchen got the furniture mod's pieces).
 */
public class QaClinicTerrace1028GameTests implements FabricGameTest {
	private static final Logger LOGGER = LoggerFactory.getLogger("aliveworkplace-qa");
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	/** The test pack's one-block tables and upside-down stair chairs, and its two-block beds (no optional mods needed). */
	private static final List<String> TEST_MODS = List.of(FurnishingTableGameTests.TEST_TABLE, "aliveworkplace_test_beds");

	/** Every Clinic look, with the beds its ward has to have: the spec's "1 real bed + 3 cots" (village looks: home bed + 2 cots). */
	private static Map<ResourceLocation, Integer> clinics() {
		Map<ResourceLocation, Integer> out = new LinkedHashMap<>();
		out.put(AliveWorkplace.id("clinic"), 4);
		out.put(AliveWorkplace.id("modded/clinic"), 4);
		VillageHouses.STYLES.forEach(style -> out.put(AliveWorkplace.id("village/" + style + "_clinic"), 3));
		return out;
	}

	private static Map<BlockPos, BlockState> template(GameTestHelper helper, ResourceLocation id) {
		Map<BlockPos, BlockState> out = new HashMap<>();
		BlueprintLibrary.get(helper.getLevel(), id).orElseThrow(() -> new AssertionError("missing blueprint " + id)).blocks()
			.forEach(b -> out.put(b.pos(), b.state()));
		return out;
	}

	private static BlockState at(Map<BlockPos, BlockState> map, BlockPos pos) {
		return map.getOrDefault(pos, Blocks.AIR.defaultBlockState());
	}

	/** A carpet on a bottom slab lying on a floor, with room to lie above it: a stand-in bed, even a single one. */
	private static boolean standInBed(Map<BlockPos, BlockState> map, BlockPos pos) {
		BlockState carpet = at(map, pos);
		BlockState slab = at(map, pos.below());
		BlockState floor = at(map, pos.below(2));
		return carpet.getBlock() instanceof CarpetBlock && slab.getBlock() instanceof SlabBlock && slab.getValue(SlabBlock.TYPE) == SlabType.BOTTOM
			&& !floor.isAir() && at(map, pos.above()).isAir()
			&& !(floor.getBlock() instanceof FenceBlock || floor.getBlock() instanceof WallBlock || floor.getBlock() instanceof SlabBlock
				|| floor.getBlock() instanceof StairBlock || floor.getBlock() instanceof RotatedPillarBlock);
	}

	/**
	 * B639, "any other build using slab+carpet stand-ins for beds": no build of the mod has even one carpet on a floor
	 * slab with headroom over it, and in every Clinic no carpet lies on any slab at all.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaNoBuildHasASingleSlabAndCarpetStandInBed(GameTestHelper helper) {
		List<ResourceLocation> ids = BlueprintLibrary.list(helper.getLevel().getServer(), true).stream()
			.filter(id -> id.getNamespace().equals(AliveWorkplace.MOD_ID)).toList();
		helper.assertTrue(ids.containsAll(clinics().keySet()), "not every Clinic look is in the library: " + clinics().keySet());
		List<String> problems = new ArrayList<>();
		for (ResourceLocation id : ids) {
			Map<BlockPos, BlockState> map = template(helper, id);
			boolean clinic = clinics().containsKey(id);
			map.forEach((pos, state) -> {
				if (standInBed(map, pos)) {
					problems.add(id.getPath() + " stand-in bed at " + pos.toShortString());
				} else if (clinic && state.getBlock() instanceof CarpetBlock && at(map, pos.below()).getBlock() instanceof SlabBlock) {
					problems.add(id.getPath() + " carpet on a slab in the Clinic at " + pos.toShortString());
				}
			});
		}
		helper.assertTrue(ids.size() > 100, "only " + ids.size() + " builds looked at");
		helper.assertTrue(problems.isEmpty(), problems.size() + " slab-and-carpet stand-in(s), e.g. " + problems.subList(0, Math.min(8, problems.size())));
		LOGGER.info("qa B639: {} builds have no slab-and-carpet stand-in bed", ids.size());
		helper.succeed();
	}

	/**
	 * B639: every Clinic look has at least as many whole beds (head and foot, the same colour and facing) as the spec's
	 * ward had sleeping places (1 bed + 3 cots in the builder's Clinic and its modded copy; the home bed + 2 cots in the
	 * village looks), and its ward beds are white (STYLE.md, B260).
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryClinicLookHasTheSpecsCountOfWholeBeds(GameTestHelper helper) {
		List<String> problems = new ArrayList<>();
		StringBuilder counts = new StringBuilder();
		for (Map.Entry<ResourceLocation, Integer> c : clinics().entrySet()) {
			Map<BlockPos, BlockState> map = template(helper, c.getKey());
			int whole = 0;
			int white = 0;
			for (Map.Entry<BlockPos, BlockState> e : map.entrySet()) {
				BlockState state = e.getValue();
				if (!(state.getBlock() instanceof BedBlock)) {
					continue;
				}
				Direction facing = state.getValue(BedBlock.FACING);
				boolean head = state.getValue(BedBlock.PART) == BedPart.HEAD;
				BlockState other = at(map, head ? e.getKey().relative(facing.getOpposite()) : e.getKey().relative(facing));
				boolean ok = other.getBlock() == state.getBlock() && other.getValue(BedBlock.FACING) == facing
					&& other.getValue(BedBlock.PART) != state.getValue(BedBlock.PART);
				if (!ok) {
					problems.add(c.getKey().getPath() + ": half a bed at " + e.getKey().toShortString() + " (" + state + ", other half " + other + ")");
				} else if (head) {
					whole++;
					if (state.is(Blocks.WHITE_BED)) {
						white++;
					}
				}
			}
			int wardBeds = c.getValue() == 4 ? 4 : 2;
			if (whole < c.getValue()) {
				problems.add(c.getKey().getPath() + " has " + whole + " whole beds, the spec's ward has " + c.getValue());
			}
			if (white < wardBeds) {
				problems.add(c.getKey().getPath() + " has " + white + " white ward beds, wants " + wardBeds);
			}
			counts.append(c.getKey().getPath()).append('=').append(whole).append(' ');
		}
		helper.assertTrue(problems.isEmpty(), problems.toString());
		LOGGER.info("qa B639: whole beds per Clinic look: {}", counts);
		helper.succeed();
	}

	/** A builder with a bench, free materials and no delay; the rules put back after the test. */
	private static Villager builder(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
		});
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		BlockPos bench = new BlockPos(1, 2, 1);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 1));
		Builders.employ(level, builder, helper.absolutePos(bench));
		return builder;
	}

	private static boolean climbsTo(BlockState state, Direction facing) {
		return state.getBlock() instanceof StairBlock && state.getValue(StairBlock.HALF) == Half.BOTTOM && state.getValue(StairBlock.FACING) == facing;
	}

	/**
	 * B639 through the player's entry point: a builder builds the Clinic turned and mirrored. Afterwards its ward has
	 * four whole beds, heads to a wall, each a free home a villager can take (the game's bed point of interest) and sleep
	 * in; no carpet lies on a slab; and the roof has no open block under its ridge, as built in the world.
	 */
	//$ gametest_ticks_batch HUGE_AREA '24000' '"qaClinicBuilt"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "qaClinicBuilt")
	public void qaABuiltTurnedMirroredClinicHasFourBedsVillagersCanSleepIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Villager builder = builder(helper);
		ResourceLocation id = AliveWorkplace.id("clinic");
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(14, 2, 6)),
			Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		helper.assertTrue(site != null, "the builder didn't take the Clinic");
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		List<BlockPos> cells = new ArrayList<>(BuildPlan.create(blueprint, placement).targets().keySet());
		String[] verdict = {null};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			Map<BlockPos, BlockState> world = new HashMap<>();
			for (BlockPos p : cells) {
				world.put(p, level.getBlockState(p));
			}
			List<String> problems = new ArrayList<>();
			List<BlockPos> heads = new ArrayList<>();
			int ridges = 0;
			for (BlockPos p : cells) {
				BlockState state = world.get(p);
				if (state.getBlock() instanceof CarpetBlock && level.getBlockState(p.below()).getBlock() instanceof SlabBlock) {
					problems.add("carpet on a slab at " + helper.relativePos(p).toShortString());
				}
				if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD) {
					Direction facing = state.getValue(BedBlock.FACING);
					BlockState foot = level.getBlockState(p.relative(facing.getOpposite()));
					if (!foot.is(state.getBlock()) || foot.getValue(BedBlock.PART) != BedPart.FOOT || foot.getValue(BedBlock.FACING) != facing) {
						problems.add("bed head at " + helper.relativePos(p).toShortString() + " without its foot: " + foot);
						continue;
					}
					BlockState behind = level.getBlockState(p.relative(facing));
					if (!behind.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, facing.getOpposite())) {
						problems.add("bed at " + helper.relativePos(p).toShortString() + " has " + behind + " at its head, not a wall");
					}
					heads.add(p);
				}
				if (state.getBlock() instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM) {
					BlockPos under = p.below();
					for (Direction up : new Direction[] {Direction.EAST, Direction.SOUTH}) {
						if (climbsTo(level.getBlockState(under.relative(up.getOpposite())), up) && climbsTo(level.getBlockState(under.relative(up)), up.getOpposite())) {
							ridges++;
							if (level.getBlockState(under).isAir()) {
								problems.add("open under the ridge at " + helper.relativePos(under).toShortString());
							}
						}
					}
				}
			}
			helper.assertTrue(ridges > 5, "only " + ridges + " ridge cells found in the built Clinic");
			helper.assertTrue(heads.size() >= 4, "the built Clinic has " + heads.size() + " whole beds, not the ward's 4 (skipped " + site.skipped() + ")");
			helper.assertTrue(problems.isEmpty(), "the built Clinic: " + problems);
			PoiManager pois = level.getPoiManager();
			for (BlockPos head : heads) {
				helper.assertTrue(pois.existsAtPosition(PoiTypes.HOME, head), "the bed at " + helper.relativePos(head).toShortString() + " is no home to the game");
			}
			// A villager takes each bed as his home (the game's claim; the builder may have taken one already) and lies
			// down in it. Done once (a retry would find its own claims); what it found is asserted on every pass.
			if (verdict[0] == null) {
				List<String> claims = new ArrayList<>();
				for (BlockPos head : heads) {
					String where = helper.relativePos(head).toShortString();
					boolean free = pois.findAll(h -> h.is(PoiTypes.HOME), head::equals, head, 1, PoiManager.Occupancy.HAS_SPACE).findAny().isPresent();
					boolean takenByAVillager = level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(head).inflate(64)).stream()
						.anyMatch(v -> v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME)
							.map(g -> g.pos().equals(head)).orElse(false));
					if (!(free ? pois.take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(head), head, 1).isPresent() : takenByAVillager)) {
						claims.add("nobody can claim the bed at " + where + " (free " + free + ")");
					}
					Villager patient = helper.spawn(EntityType.VILLAGER, helper.relativePos(head.relative(level.getBlockState(head).getValue(BedBlock.FACING).getOpposite(), 2)));
					patient.startSleeping(head);
					if (!(patient.isSleeping() && patient.getSleepingPos().map(head::equals).orElse(false))) {
						claims.add("a villager can't lie in the bed at " + where);
					} else if (!level.getBlockState(head).getValue(BedBlock.OCCUPIED)) {
						claims.add("the bed at " + where + " doesn't show him in it");
					}
				}
				verdict[0] = String.join("; ", claims);
			}
			helper.assertTrue(verdict[0].isEmpty(), "the built Clinic's ward: " + verdict[0]);
			LOGGER.info("qa B639: the turned, mirrored Clinic built ({} skipped): {} whole beds claimed and slept in, {} ridge cells closed",
				site.skipped(), heads.size(), ridges);
		});
	}

	/** One kitchen of a Terrace, as built: what stands at the table, on it, above that, and at the chair. */
	private static String kitchen(ServerLevel level, BlueprintData.Placement placement, FurnitureSpot table, FurnitureSpot chair) {
		BlockPos t = placement.origin().offset(StructureTemplate.transform(table.pos(), placement.mirror(), placement.rotation(), BlockPos.ZERO));
		BlockPos c = placement.origin().offset(StructureTemplate.transform(chair.pos(), placement.mirror(), placement.rotation(), BlockPos.ZERO));
		BlockState chairState = level.getBlockState(c);
		String chairLook = chairState.getBlock().getDescriptionId() + (chairState.getBlock() instanceof StairBlock ? "/" + chairState.getValue(StairBlock.HALF) : "");
		return "table " + level.getBlockState(t).getBlock().getDescriptionId() + ", on it " + level.getBlockState(t.above()).getBlock().getDescriptionId()
			+ ", above " + level.getBlockState(t.above(2)).getBlock().getDescriptionId() + ", chair " + chairLook;
	}

	/**
	 * B641 through the player's entry point: with furniture pieces installed (the test pack's, which need no optional
	 * mod), a builder builds {@code name} turned and mirrored. Every kitchen comes out the same (the pieces at table and
	 * chair, nothing left of the vanilla stand-ins: no fence-and-plate table, no stair-bottom chair), nothing in the
	 * build rests on nothing, and the builder's last look has nothing to set down.
	 */
	private static void terrace(GameTestHelper helper, String name, Rotation rotation, Mirror mirror, int homes) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> new WorkplaceConfig().apply());
		Furnishings.MODS = TEST_MODS;
		Villager builder = builder(helper);
		ResourceLocation id = AliveWorkplace.id(name);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(16, 2, 6)), rotation, mirror);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		helper.assertTrue(site != null, "the builder didn't take the " + name);
		List<FurnitureSpot> spots = BlueprintFurniture.of(id);
		List<FurnitureSpot> tables = spots.stream().filter(s -> s.kind() == FurnitureSpot.Kind.TABLE).toList();
		List<FurnitureSpot> chairs = spots.stream().filter(s -> s.kind() == FurnitureSpot.Kind.CHAIR).toList();
		helper.assertTrue(tables.size() == homes && chairs.size() == homes, name + " has " + tables.size() + " tables and " + chairs.size() + " chairs, not " + homes);
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow();
		List<BlockPos> cells = new ArrayList<>(BuildPlan.create(blueprint, placement).targets().keySet());
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage() + " " + site.status());
			List<String> looks = new ArrayList<>();
			for (int i = 0; i < homes; i++) {
				// the chair belonging to this table: the nearest one
				FurnitureSpot table = tables.get(i);
				FurnitureSpot chair = chairs.stream().min(java.util.Comparator.comparingDouble(ch -> ch.pos().distSqr(table.pos()))).orElseThrow();
				looks.add(kitchen(level, placement, table, chair));
			}
			List<String> problems = new ArrayList<>();
			for (int i = 0; i < homes; i++) {
				String look = looks.get(i);
				if (!look.equals(looks.get(0))) {
					problems.add("kitchen " + i + " (table at " + tables.get(i).pos().toShortString() + ") is [" + look + "] but kitchen 0 is [" + looks.get(0) + "]");
				}
				BlockPos t = placement.origin().offset(StructureTemplate.transform(tables.get(i).pos(), mirror, rotation, BlockPos.ZERO));
				BlockState table = level.getBlockState(t);
				if (table.getBlock() instanceof FenceBlock || level.getBlockState(t.above()).getBlock() instanceof PressurePlateBlock) {
					problems.add("kitchen " + i + " kept the fence-and-plate stand-in: " + look);
				}
			}
			for (BlockPos p : cells) {
				BlockState state = level.getBlockState(p);
				if (Resting.unsupported(level, p, state)) {
					problems.add(state.getBlock().getDescriptionId() + " rests on nothing at " + helper.relativePos(p).toShortString());
				}
			}
			helper.assertTrue(problems.isEmpty(), name + " " + rotation + "/" + mirror + ": " + problems);
			LOGGER.info("qa B641: {} {}/{} built ({} skipped), all {} kitchens [{}], nothing floating", name, rotation, mirror, site.skipped(), homes, looks.get(0));
		});
	}

	//$ gametest_ticks_batch HUGE_AREA '24000' '"qaTerraceBuilt"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "qaTerraceBuilt")
	public void qaABuiltTurnedMirroredTerraceFurnishesBothKitchensAlike(GameTestHelper helper) {
		terrace(helper, "terrace", Rotation.CLOCKWISE_90, Mirror.LEFT_RIGHT, 2);
	}

	//$ gametest_ticks_batch HUGE_AREA '24000' '"qaTerrace2Built"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "qaTerrace2Built")
	public void qaABuiltTurnedMirroredTerraceIIFurnishesEveryKitchenAlike(GameTestHelper helper) {
		terrace(helper, "terrace_2", Rotation.COUNTERCLOCKWISE_90, Mirror.FRONT_BACK, 3);
	}
}

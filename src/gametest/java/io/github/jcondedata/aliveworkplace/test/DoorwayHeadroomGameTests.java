package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * B173: B170's doorway trap in other blueprints. A villager is 1.95 tall; under a lintel two blocks up it fits with 0.05
 * to spare, so a carpet or a step right inside the door put its head into the lintel and it stood in the doorway for
 * good. The Healing Center's red carpet began right inside its front door, and the Manor's grand stair starts right
 * inside its front door. Each blueprint is placed from its own file, and a villager outside is walked in by the front
 * door the way vanilla walks it (a walk target in its brain). tools/blueprints/check.py now refuses the pattern in every
 * blueprint.
 */
public class DoorwayHeadroomGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";

	/** Places one of our blueprints with its template origin at {@code at} (its y = 0 layer on the area's floor). */
	private static void place(GameTestHelper helper, String name, BlockPos at) {
		ServerLevel level = helper.getLevel();
		StructureTemplate template = level.getStructureManager()
			.get(ResourceLocation.fromNamespaceAndPath("aliveworkplace", name)).orElseThrow();
		BlockPos origin = helper.absolutePos(at);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(173L), 2);
	}

	/**
	 * Makes {@code villager} a nitwit, with a nitwit's brain: no job site to go after, so only the walk it's given moves it.
	 * B198: the profession alone wasn't enough. A villager's brain is built for the profession it spawned with (none), and
	 * an unemployed brain looks for a free job site and walks to it: every run, the Healing Center's villager was sent
	 * off to its brewing stand, and the two walk targets fought over its path.
	 */
	static void nitwit(GameTestHelper helper, Villager villager) {
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
		villager.refreshBrain(helper.getLevel());
	}

	/**
	 * Opens every wooden door in the test area, the front door propped open as a shop's is. B198: these tests are about
	 * room overhead in the doorway, and vanilla's door opening raced the walk. A villager waits a second at a shut door,
	 * then opens it, and the opening makes its path be worked out again; in 4 runs of 21 that new path came out as
	 * "can't get there" and led away round the building, the villager shut the door behind it, and it walked round the
	 * outside (falling into the blueprint's ground-layer pits by the porch) until the test ran out.
	 */
	private static void propDoorsOpen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		net.minecraft.world.phys.AABB box = helper.getBounds();
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door && net.minecraft.world.level.block.DoorBlock.isWoodenDoor(state)
				&& state.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) {
				door.setOpen(null, level, state, pos.immutable(), true);
			}
		}
	}

	/**
	 * A villager at {@code from} (outside the front door) walks to {@code goal} inside; the test passes once its middle is in
	 * the cell at {@code insideZ} (the step's), its feet at least {@code minFeet} over the floor: up on
	 * the step, out of the doorway. Stuck, it stands in the doorway's cell with its feet on the floor.
	 */
	private static void walkIn(GameTestHelper helper, BlockPos from, BlockPos goal, int insideZ, double minFeet) {
		propDoorsOpen(helper);
		walkIn(helper, from, goal, insideZ, minFeet, null);
	}

	/**
	 * The same walk through whatever doors the blueprint has (shut, as built). With {@code lookedAt}, the villager also
	 * works out a path to that spot every tick and doesn't follow it, as vanilla's villagers do to see whether they could
	 * reach a bed, a bell or a workstation (B203).
	 */
	private static void walkIn(GameTestHelper helper, BlockPos from, BlockPos goal, int insideZ, double minFeet, @org.jetbrains.annotations.Nullable BlockPos lookedAt) {
		Villager villager = helper.spawn(EntityType.VILLAGER, from);
		BlockPos target = helper.absolutePos(goal);
		nitwit(helper, villager);
		if (lookedAt != null) {
			BlockPos spot = helper.absolutePos(lookedAt);
			helper.onEachTick(() -> villager.getNavigation().createPath(spot, 1));
		}
		double past = helper.absolutePos(new BlockPos(0, 0, insideZ)).getZ();
		double floor = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		helper.onEachTick(() -> {
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.6f, 0));
		});
		helper.succeedWhen(() -> helper.assertTrue(villager.getZ() > past && villager.getY() >= floor + minFeet,
			"the villager hasn't come in by the front door: at " + villager.position() + " (in at z > " + past
				+ ", feet over " + (floor + minFeet) + ")"));
	}

	/** The Healing Center: in by the front door (template 5,1,2) and up its red carpet, which starts a block in. */
	//$ gametest_ticks_batch AREA '400' '"b173HealingCenter"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b173HealingCenter")
	public void aVillagerWalksInByTheHealingCentersFrontDoor(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		place(helper, "healing_center", new BlockPos(8, 1, 6));
		// template (5, 1, 0) is the walk in front of the door; (5, 1, 5) the carpet's end by the counter
		walkIn(helper, new BlockPos(13, 2, 6), new BlockPos(13, 2, 11), 10, 0.05);
	}

	/**
	 * B203: the Healing Center's front door shut, as built: the villager opens it and walks in. Opening it changes the
	 * door's shape, so vanilla works the villager's path out again, and it aimed that path at the last spot any path was
	 * worked out for: with beds and bells about, often one the villager had only looked at, so the new path led away round
	 * the building (2 of 62 copies among the Workplaces, Village and Warband tests); it shut the door behind it and never
	 * got in. Here it looks at a spot outside every tick, as a villager in a village looks at its beds and bell.
	 */
	//$ gametest_ticks_batch AREA '400' '"b203HealingCenter"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b203HealingCenter")
	public void aVillagerOpensTheHealingCentersShutFrontDoorAndWalksIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		place(helper, "healing_center", new BlockPos(8, 1, 6));
		walkIn(helper, new BlockPos(13, 2, 6), new BlockPos(13, 2, 11), 10, 0.05, new BlockPos(2, 2, 2));
	}

	/** B203: the Manor's front door shut, as built, opened by the villager on its way onto the grand stair. */
	//$ gametest_ticks_batch AREA '400' '"b203Manor"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b203Manor")
	public void aVillagerOpensTheManorsShutFrontDoorAndClimbsItsStair(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		place(helper, "manor", new BlockPos(5, 1, 6));
		walkIn(helper, new BlockPos(14, 2, 9), new BlockPos(14, 2, 14), 14, 0.45, new BlockPos(2, 2, 2));
	}

	/**
	 * B203, as a player sees it: a jobless villager outside the Healing Center (door shut, as built) opens the door, walks
	 * in to the brewing stand and takes it as a cleric, with nothing steering it but vanilla's own brain.
	 */
	//$ gametest_ticks_batch AREA '1200' '"b203HealingCenterJob"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "b203HealingCenterJob")
	public void aJoblessVillagerOpensTheHealingCentersShutDoorAndTakesItsBrewingStand(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		// villagers look for a job by day and go to bed at dusk (B258): at whatever hour the suite had reached, this one
		// failed whenever it began at night. It starts in the morning.
		helper.setDayTime(2000);
		place(helper, "healing_center", new BlockPos(8, 1, 6));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(13, 2, 4));
		villager.getRandom().setSeed(203L);
		// the brewing stand is template (3, 1, 7), behind the door (5, 1, 2)
		double past = helper.absolutePos(new BlockPos(0, 0, 10)).getZ();
		helper.succeedWhen(() -> helper.assertTrue(villager.getZ() > past && villager.getVillagerData().getProfession() == VillagerProfession.CLERIC,
			"the villager hasn't come in and taken the brewing stand: at " + villager.position() + ", "
				+ villager.getVillagerData().getProfession()));
	}

	/**
	 * The Manor: in by the front door (template 9,1,7) and onto the grand stair right inside it, under a fanlight now. A
	 * stair's back half is a full block up, and a villager's back is still in the doorway when it climbs onto it: under
	 * the old lintel, and under a slab lintel tried first, it stood in the doorway with its feet on the floor.
	 */
	//$ gametest_ticks_batch AREA '400' '"b173Manor"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b173Manor")
	public void aVillagerWalksInByTheManorsFrontDoorOntoItsStair(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		place(helper, "manor", new BlockPos(5, 1, 6));
		// template (9, 1, 3) is the gravel walk in front; (9, 1, 8) the stair's first step, just inside the door
		walkIn(helper, new BlockPos(14, 2, 9), new BlockPos(14, 2, 14), 14, 0.45);
	}

	/**
	 * B178: the War Camp's tents. Each canvas tent is two high only down its middle, under the ridge, and a fence pole
	 * stood right in that middle at the tent's mouth, so nobody got in to the bed at the back; the Warlord's tent had
	 * red carpet under its two-high sides (1/16 up: 1.94 under the black wool), where a villager stood at the carpet's
	 * edge for good. The camp is placed from its own file and three villagers are walked from the yard (vanilla's walk
	 * target): two up to the beds of the first two tents, one into the Warlord's tent up to its barrel.
	 */
	//$ gametest_ticks_batch AREA '400' '"b178WarCamp"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b178WarCamp")
	public void villagersWalkIntoTheWarCampsTents(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		BlockPos camp = new BlockPos(3, 1, 3);
		place(helper, "camp/war_camp", camp);
		// template cells (y = 1 stands on the ground layer): a start in the yard and a goal inside a tent
		int[][] walks = {
			{8, 4, 4, 4},    // the first white tent: its bed's foot is (3, 4), the goal the cell in front of it
			{8, 9, 4, 9},    // the light grey tent: bed foot (3, 9)
			{8, 10, 10, 15}, // the Warlord's tent: beside the barrel (9, 16), on what was red carpet under the black wool
		};
		java.util.List<Villager> villagers = new java.util.ArrayList<>();
		java.util.List<BlockPos> goals = new java.util.ArrayList<>();
		for (int[] w : walks) {
			Villager villager = helper.spawn(EntityType.VILLAGER, camp.offset(w[0], 1, w[1]));
			nitwit(helper, villager);
			villagers.add(villager);
			goals.add(helper.absolutePos(camp.offset(w[2], 1, w[3])));
		}
		helper.onEachTick(() -> {
			for (int i = 0; i < villagers.size(); i++) {
				villagers.get(i).getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(goals.get(i)));
				villagers.get(i).getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(goals.get(i), 0.6f, 0));
			}
		});
		String[] tents = {"the white tent's bed", "the grey tent's bed", "the Warlord's barrel"};
		helper.succeedWhen(() -> {
			StringBuilder out = new StringBuilder();
			for (int i = 0; i < villagers.size(); i++) {
				BlockPos at = villagers.get(i).blockPosition();
				if (at.getX() != goals.get(i).getX() || at.getZ() != goals.get(i).getZ()) {
					BlockPos rel = at.subtract(helper.absolutePos(camp));
					out.append(out.isEmpty() ? "" : "; ").append("not up to ").append(tents[i]).append(" (camp cell ")
						.append(walks[i][2]).append(",").append(walks[i][3]).append("): at camp cell ")
						.append(rel.getX()).append(",").append(rel.getZ()).append(" (")
						.append(villagers.get(i).position()).append(")");
				}
			}
			helper.assertTrue(out.isEmpty(), out.toString());
		});
	}

	/**
	 * B197: B178's trap away from doorways. A villager stands where the two cells its body is in are open, so a step that
	 * leaves less than its 1.95 under whatever is over it is a place it walks to and can't get onto. check.py now flags
	 * every such step a villager gets to; in the Starter Cottage (every tier) the chair against the front wall had the
	 * window's blinds (a shut trapdoor) over it, 1.81 above the seat. The cottage is placed from its own file and a
	 * villager in the room is walked onto that chair (vanilla's walk target).
	 */
	//$ gametest_ticks_batch AREA '300' '"b197Cottage"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "b197Cottage")
	public void aVillagerSitsDownOnTheStarterCottagesChairUnderTheWindow(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		BlockPos cottage = new BlockPos(5, 1, 5);
		place(helper, "starter_cottage", cottage);
		// template cells (y = 1 is the ground floor): the room by the rug, and the chair (stairs) at the table's north side
		Villager villager = helper.spawn(EntityType.VILLAGER, cottage.offset(5, 1, 4));
		nitwit(helper, villager);
		BlockPos chair = helper.absolutePos(cottage.offset(3, 1, 3));
		double floor = helper.absolutePos(cottage.offset(0, 1, 0)).getY();
		helper.onEachTick(() -> {
			villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(chair));
			villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(chair, 0.6f, 0));
		});
		helper.succeedWhen(() -> {
			BlockPos at = villager.blockPosition();
			helper.assertTrue(at.getX() == chair.getX() && at.getZ() == chair.getZ() && villager.getY() >= floor + 0.45,
				"the villager hasn't got up onto the chair under the front window: at " + villager.position()
					+ " (the chair's cell " + chair.getX() + ", " + chair.getZ() + ", feet over " + (floor + 0.45) + ")");
		});
	}
}

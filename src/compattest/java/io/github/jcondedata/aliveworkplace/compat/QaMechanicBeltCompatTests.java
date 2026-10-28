package io.github.jcondedata.aliveworkplace.compat;

import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.mechanic.MechanicWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane E (2026-10-28) for 39.2, end to end with the real Create: "a shaft, cogwheel or belt that's gone from a
 * finished build is put back from the build's blueprint" (docs/design/M39.md), "a belt costs one belt connector, on its
 * controller segment". A belt a player laid with a belt connector between two shafts on a finished build, then gone
 * whole: the Mechanic puts every segment back, takes exactly one connector from the store, and the belt is one belt
 * again (every segment knows its controller). The existing belt test only checks the order the parts are planned in.
 */
public class QaMechanicBeltCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TOOLBOX = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** The belt's first pulley; the belt runs 4 blocks along x on a stone-brick footing. */
	private static final BlockPos BELT = new BlockPos(5, 3, 6);
	private static final int LENGTH = 4;

	private static int count(Container container, Item item) {
		int n = 0;
		for (int i = 0; i < container.getContainerSize(); i++) {
			if (container.getItem(i).is(item)) {
				n += container.getItem(i).getCount();
			}
		}
		return n;
	}

	/** What a test checks once the belt is gone and the Mechanic is at work. */
	private interface Then {
		void run(Container chest, Villager mechanic, Item connector, List<BlockPos> segments);
	}

	private static int connectorsAround(GameTestHelper helper, Item connector) {
		return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, helper.getBounds().inflate(8),
			e -> e.getItem().is(connector)).stream().mapToInt(e -> e.getItem().getCount()).sum();
	}

	/**
	 * A finished build with a belt a player laid (a connector between two shafts on a footing), the store holding 2 belt
	 * connectors, then the belt broken by hand (one middle segment; Create takes the rest of the belt with it, and nothing
	 * drops), then the Mechanic at their toolbox; {@code then} runs once all that's done.
	 */
	private static void aBrokenBelt(GameTestHelper helper, Then then) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		// Other tests' finished lines (their areas cleared, their records kept) would be holes within this Mechanic's reach.
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedIn(level)) {
			String path = f.structure().getPath();
			if (path.startsWith("compat_test/mechanic_line_") || path.startsWith("compat_test/qa_mechanic_belt_")) {
				sites.forgetFinished(f.placement());
			}
		}
		BlockState shaft = MechanicCompatTests.with(MechanicCompatTests.create("shaft").defaultBlockState(), "axis", "z");
		for (int x = 0; x < LENGTH; x++) {
			helper.setBlock(BELT.offset(x, -1, 0), Blocks.STONE_BRICKS);
		}
		helper.setBlock(BELT, shaft);
		helper.setBlock(BELT.offset(LENGTH - 1, 0, 0), shaft);
		BlockPos start = helper.absolutePos(BELT);
		BlockPos end = helper.absolutePos(BELT.offset(LENGTH - 1, 0, 0));
		helper.assertTrue(BeltConnectorItem.canConnect(level, start, end), "setup: Create won't connect the two shafts");
		BeltConnectorItem.createBelts(level, start, end);
		List<BlockPos> segments = new ArrayList<>();
		for (int x = 0; x < LENGTH; x++) {
			segments.add(BELT.offset(x, 0, 0));
		}
		// A few ticks for Create to settle the new belt (each segment's controller), as a player would see it; then saved.
		helper.runAfterDelay(5, () -> {
			int controllers = 0;
			for (BlockPos p : segments) {
				helper.assertTrue(helper.getBlockState(p).is(MechanicCompatTests.create("belt")), "setup: no belt segment at " + p + ": " + helper.getBlockState(p));
				controllers += helper.getBlockEntity(p) instanceof BeltBlockEntity laid && laid.isController() ? 1 : 0;
			}
			helper.assertTrue(controllers == 1, "setup: the laid belt has " + controllers + " controller segments, not 1");
			ResourceLocation id = AliveWorkplace.id("compat_test/qa_mechanic_belt_" + UUID.randomUUID().toString().substring(0, 8));
			StructureTemplate template = level.getStructureManager().getOrCreate(id);
			template.fillFromWorld(level, helper.absolutePos(BELT.below()), new Vec3i(LENGTH, 2, 1), true, Blocks.STRUCTURE_VOID);
			helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "setup: could not register the test blueprint");
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(BELT.below()),
				Rotation.NONE, Mirror.NONE);
			sites.recordFinished(id, placement, UUID.randomUUID());
			PartnerShowsCompatTests.after(helper, () -> sites.forgetFinished(placement));
			Item connector = MechanicCompatTests.createItem("belt_connector");
			helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
			Container chest = (Container) helper.getBlockEntity(CHEST);
			chest.setItem(0, new ItemStack(connector, 2));
			level.destroyBlock(helper.absolutePos(segments.get(1)), false);
			helper.runAfterDelay(5, () -> {
				for (BlockPos p : List.of(segments.get(1), segments.get(2))) {
					helper.assertTrue(helper.getBlockState(p).isAir(), "setup: the broken belt left " + helper.getBlockState(p) + " at " + p);
				}
				helper.assertTrue(connectorsAround(helper, connector) == 0, "setup: breaking the belt dropped " + connectorsAround(helper, connector) + " connectors");
				helper.setBlock(TOOLBOX, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
				Villager mechanic = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
				Stations.assign(level, mechanic, helper.absolutePos(TOOLBOX), ModVillagers.MECHANIC);
				helper.assertTrue(MechanicWork.isMechanic(mechanic), "setup: no Mechanic at the toolbox");
				helper.setDayTime(2000);
				then.run(chest, mechanic, connector, segments);
			});
		});
	}

	/** Bug test: the belt broken by hand comes back whole, for one belt connector from the store. */
	//$ gametest_ticks_batch AREA '2400' '"qa_mechanic_belt_whole"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "qa_mechanic_belt_whole")
	public void aBeltBrokenOnAFinishedBuildIsPutBackWholeForOneConnector(GameTestHelper helper) {
		aBrokenBelt(helper, (chest, mechanic, connector, segments) -> helper.succeedWhen(() -> {
			for (BlockPos p : segments) {
				helper.assertTrue(helper.getBlockState(p).is(MechanicCompatTests.create("belt")), "the belt segment at " + p + " isn't back: "
					+ helper.getBlockState(p) + "; still missing " + MechanicWork.missingParts(helper.getLevel(), helper.absolutePos(TOOLBOX)).size()
					+ " parts, " + count(chest, connector) + " connectors in the store, " + connectorsAround(helper, connector) + " on the ground");
			}
			helper.assertTrue(mechanic.getMainHandItem().isEmpty(), "the Mechanic still holds " + mechanic.getMainHandItem());
			helper.assertTrue(count(chest, connector) == 1, "the belt took " + (2 - count(chest, connector)) + " belt connectors from the store, not 1");
			BlockPos controller = null;
			for (BlockPos p : segments) {
				helper.assertTrue(helper.getBlockEntity(p) instanceof BeltBlockEntity, "the segment at " + p + " has no belt data");
				BeltBlockEntity belt = (BeltBlockEntity) helper.getBlockEntity(p);
				helper.assertTrue(belt.getControllerBE() != null, "the segment at " + p + " belongs to no belt: the belt isn't whole");
				helper.assertTrue(controller == null || controller.equals(belt.getController()), "the segments name two controllers");
				controller = belt.getController();
			}
		}));
	}

	/**
	 * Bug test: mending a belt never makes belt connectors. The store had 2; a minute of the Mechanic's work later there
	 * are never more than 2 anywhere (store, ground, the Mechanic's hand and pockets).
	 */
	//$ gametest_ticks_batch AREA '1600' '"qa_mechanic_belt_no_dupe"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "qa_mechanic_belt_no_dupe")
	public void mendingABeltNeverMakesBeltConnectors(GameTestHelper helper) {
		aBrokenBelt(helper, (chest, mechanic, connector, segments) -> helper.runAfterDelay(1200, () -> {
			int store = count(chest, connector);
			int ground = connectorsAround(helper, connector);
			int held = (mechanic.getMainHandItem().is(connector) ? mechanic.getMainHandItem().getCount() : 0) + mechanic.getInventory().countItem(connector);
			helper.assertTrue(store + ground + held <= 2, "2 belt connectors became " + (store + ground + held) + ": " + store + " in the store, "
				+ ground + " on the ground, " + held + " with the Mechanic");
			helper.succeed();
		}));
	}
}

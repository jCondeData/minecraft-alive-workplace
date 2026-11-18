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
import io.github.jcondedata.aliveworkplace.work.WorkerStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * B669 (39.2 Mechanic, with Create): a belt a player broke on a finished build is mended whole, the way a player lays
 * one: one belt connector from the store between the two pulley shafts, through Create's own belt laying. Never a
 * segment at a time (a lone segment isn't a belt: Create broke each into a belt connector, 59 from nothing in a minute),
 * so belt connectors are counted everywhere (the store, the ground, the Mechanic) every tick, and never grow. With no
 * connector in the store nothing is placed; with the pulley shafts mined too, the shafts come from the store first; and
 * the Mechanic saved and loaded with the connector in hand lays the belt once.
 */
public class MechanicBeltMendB669CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos TOOLBOX = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	/** The belt's first pulley; the belt runs 4 blocks along x on a stone-brick footing. */
	private static final BlockPos BELT = new BlockPos(5, 3, 6);
	private static final int LENGTH = 4;

	private record Setup(Container chest, Villager[] mechanic, Item connector, Item shaft, List<BlockPos> segments, long readyAt) {
	}

	private interface Then {
		void run(Setup setup);
	}

	private static int ground(GameTestHelper helper, Item item) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(item)).stream()
			.mapToInt(e -> e.getItem().getCount()).sum();
	}

	private static int held(Villager villager, Item item) {
		return (villager.getMainHandItem().is(item) ? villager.getMainHandItem().getCount() : 0) + villager.getInventory().countItem(item);
	}

	private static boolean beltBack(GameTestHelper helper, List<BlockPos> segments) {
		return segments.stream().allMatch(p -> helper.getBlockState(p).is(MechanicCompatTests.create("belt")));
	}

	/** Every belt connector there is: in the store, on the ground, with the Mechanic, and the one in the laid belt. */
	private static int connectors(GameTestHelper helper, Setup s) {
		return MechanicCompatTests.count(s.chest(), s.connector()) + ground(helper, s.connector()) + held(s.mechanic()[0], s.connector())
			+ (beltBack(helper, s.segments()) ? 1 : 0);
	}

	private static String said(GameTestHelper helper, Villager villager) {
		WorkerStatus.Entry e = WorkerStatus.get(villager, helper.getLevel().getGameTime());
		return e == null ? "" : e.line().getString();
	}

	/**
	 * A finished build holding a belt a player laid between two shafts (Create's own laying), saved as its blueprint;
	 * then the belt broken by hand (Create takes the whole belt down and leaves the pulleys as shafts), the shafts mined
	 * too when {@code mineShafts}; a store holding {@code connectors} belt connectors and {@code shafts} shafts; and a
	 * Mechanic at their toolbox. Every tick after that, the belt connectors never grow in number.
	 */
	private static Setup[] aBrokenBelt(GameTestHelper helper, boolean mineShafts, int connectors, int shafts, @Nullable Then perTick) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		BuildSiteManager sites = BuildSiteManager.get(level);
		for (BuildSiteManager.Finished f : sites.finishedIn(level)) {
			if (f.placement().origin().distManhattan(helper.absolutePos(BELT)) < 96) {
				sites.forgetFinished(f.placement()); // other tests' finished builds here would be holes in this Mechanic's reach
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
		Item connector = MechanicCompatTests.createItem("belt_connector");
		Item shaftItem = MechanicCompatTests.createItem("shaft");
		Setup[] ready = {null};
		int[] tick = {0};
		Container[] chest = {null};
		// One tick handler for the whole setup (scheduling more from inside a scheduled task upsets the test's own timer)
		helper.onEachTick(() -> {
			tick[0]++;
			if (tick[0] == 5) {
				// The laid belt settled, as a player would see it: saved as the build's blueprint, then broken by hand
				helper.assertTrue(beltBack(helper, segments), "setup: the laid belt isn't whole");
				ResourceLocation id = AliveWorkplace.id("compat_test/b669_belt_" + UUID.randomUUID().toString().substring(0, 8));
				StructureTemplate template = level.getStructureManager().getOrCreate(id);
				template.fillFromWorld(level, helper.absolutePos(BELT.below()), new Vec3i(LENGTH, 2, 1), true, Blocks.STRUCTURE_VOID);
				helper.assertTrue(BlueprintLibrary.get(level, id).isPresent(), "setup: could not register the test blueprint");
				BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(BELT.below()),
					Rotation.NONE, Mirror.NONE);
				sites.recordFinished(id, placement, UUID.randomUUID());
				PartnerShowsCompatTests.after(helper, () -> sites.forgetFinished(placement));
				helper.setBlock(CHEST, Blocks.CHEST.defaultBlockState());
				chest[0] = (Container) helper.getBlockEntity(CHEST);
				chest[0].setItem(0, new ItemStack(Blocks.COBBLESTONE, 3));
				if (connectors > 0) {
					chest[0].setItem(1, new ItemStack(connector, connectors));
				}
				if (shafts > 0) {
					chest[0].setItem(2, new ItemStack(shaftItem, shafts));
				}
				level.destroyBlock(helper.absolutePos(segments.get(1)), false);
			} else if (tick[0] == 10) {
				for (BlockPos p : segments.subList(1, LENGTH - 1)) {
					helper.assertTrue(helper.getBlockState(p).isAir(), "setup: the broken belt left " + helper.getBlockState(p) + " at " + p);
				}
				for (BlockPos p : List.of(segments.get(0), segments.get(LENGTH - 1))) {
					helper.assertTrue(helper.getBlockState(p).is(MechanicCompatTests.create("shaft")), "setup: no pulley shaft left at " + p);
					if (mineShafts) {
						helper.setBlock(p, Blocks.AIR);
					}
				}
				for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
					drop.discard(); // whatever the break dropped: the Mechanic takes his from the store
				}
				helper.setBlock(TOOLBOX, MechanicCompatTests.create("brown_toolbox").defaultBlockState());
				Villager[] mechanic = {helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3))};
				Stations.assign(level, mechanic[0], helper.absolutePos(TOOLBOX), ModVillagers.MECHANIC);
				helper.assertTrue(MechanicWork.isMechanic(mechanic[0]), "setup: no Mechanic at the toolbox");
				helper.setDayTime(2000);
				ready[0] = new Setup(chest[0], mechanic, connector, shaftItem, segments, level.getGameTime());
			} else if (ready[0] != null) {
				// Every tick: the belt connectors never grow in number (B669)
				int now = connectors(helper, ready[0]);
				if (now > connectors) {
					throw new GameTestAssertException(connectors + " belt connectors became " + now + ": " + MechanicCompatTests.count(chest[0], connector)
						+ " in the store, " + ground(helper, connector) + " on the ground, " + held(ready[0].mechanic()[0], connector)
						+ " with the Mechanic, belt back " + beltBack(helper, segments) + "; he says '" + said(helper, ready[0].mechanic()[0]) + "'");
				}
				if (perTick != null) {
					perTick.run(ready[0]);
				}
			}
		});
		return ready;
	}

	/** The belt checked whole: every segment back, all on one controller, the Mechanic's hands empty. */
	private static void assertWhole(GameTestHelper helper, Setup s) {
		BlockPos controller = null;
		for (BlockPos p : s.segments()) {
			helper.assertTrue(helper.getBlockState(p).is(MechanicCompatTests.create("belt")), "the belt segment at " + p + " isn't back: "
				+ helper.getBlockState(p) + "; " + MechanicCompatTests.count(s.chest(), s.connector()) + " connectors in the store, "
				+ ground(helper, s.connector()) + " on the ground; he says '" + said(helper, s.mechanic()[0]) + "'");
			helper.assertTrue(helper.getBlockEntity(p) instanceof BeltBlockEntity belt && belt.getControllerBE() != null,
				"the segment at " + p + " belongs to no belt: the belt isn't whole");
			BlockPos named = ((BeltBlockEntity) helper.getBlockEntity(p)).getController();
			helper.assertTrue(controller == null || controller.equals(named), "the segments name two controllers");
			controller = named;
		}
		helper.assertTrue(s.mechanic()[0].getMainHandItem().isEmpty(), "the Mechanic still holds " + s.mechanic()[0].getMainHandItem());
	}

	/** The bug: the two pulley shafts left standing, the belt between them comes back whole for one connector of two. */
	//$ gametest_ticks_batch AREA '2400' '"b669_belt_shafts_left"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b669_belt_shafts_left")
	public void aBrokenBeltComesBackWholeForOneConnector(GameTestHelper helper) {
		Setup[] ready = aBrokenBelt(helper, false, 2, 0, null);
		helper.succeedWhen(() -> {
			helper.assertTrue(ready[0] != null, "setting up");
			Setup s = ready[0];
			assertWhole(helper, s);
			helper.assertTrue(MechanicCompatTests.count(s.chest(), s.connector()) == 1, "the belt took "
				+ (2 - MechanicCompatTests.count(s.chest(), s.connector())) + " belt connectors from the store, not 1");
			helper.assertTrue(ground(helper, s.connector()) == 0, ground(helper, s.connector()) + " belt connectors on the ground");
		});
	}

	/** No belt connector in the store: nothing is placed, nothing is made, and the Mechanic says what he waits for. */
	//$ gametest_ticks_batch AREA '1200' '"b669_belt_no_connector"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "b669_belt_no_connector")
	public void withNoConnectorInTheStoreNothingIsPlaced(GameTestHelper helper) {
		Setup[] ready = aBrokenBelt(helper, false, 0, 0, s -> {
			for (BlockPos p : s.segments().subList(1, LENGTH - 1)) {
				helper.assertTrue(helper.getBlockState(p).isAir(), "with no belt connector something was put at " + p + ": " + helper.getBlockState(p));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ready[0] != null && helper.getLevel().getGameTime() >= ready[0].readyAt() + 900, "waiting");
			Setup s = ready[0];
			helper.assertTrue(ground(helper, s.connector()) == 0, ground(helper, s.connector()) + " belt connectors made from nothing");
			helper.assertTrue(MechanicWork.missingParts(helper.getLevel(), helper.absolutePos(TOOLBOX)).stream()
				.filter(MechanicWork.Part::belt).count() == 1, "the broken belt isn't one part waiting for a belt connector");
			String line = said(helper, s.mechanic()[0]);
			helper.assertTrue(line.contains(s.connector().getDescription().getString()), "the Mechanic doesn't say he needs a belt connector: '" + line + "'");
		});
	}

	/** The pulley shafts mined too: two shafts from the store first, then the belt for one connector. */
	//$ gametest_ticks_batch AREA '2400' '"b669_belt_whole_gone"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b669_belt_whole_gone")
	public void aBeltGoneWithItsShaftsComesBackForTwoShaftsAndOneConnector(GameTestHelper helper) {
		Setup[] ready = aBrokenBelt(helper, true, 2, 4, null);
		helper.succeedWhen(() -> {
			helper.assertTrue(ready[0] != null, "setting up");
			Setup s = ready[0];
			assertWhole(helper, s);
			helper.assertTrue(MechanicCompatTests.count(s.chest(), s.connector()) == 1, "the belt took "
				+ (2 - MechanicCompatTests.count(s.chest(), s.connector())) + " belt connectors from the store, not 1");
			helper.assertTrue(MechanicCompatTests.count(s.chest(), s.shaft()) == 2, "the pulleys took "
				+ (4 - MechanicCompatTests.count(s.chest(), s.shaft())) + " shafts from the store, not 2");
		});
	}

	/** Saved and loaded with the belt connector in hand: the belt is laid once, for that one connector. */
	//$ gametest_ticks_batch AREA '2400' '"b669_belt_saved_mid_mend"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b669_belt_saved_mid_mend")
	public void savedWithTheConnectorInHandTheBeltIsLaidOnce(GameTestHelper helper) {
		boolean[] reloaded = {false};
		Setup[] ready = aBrokenBelt(helper, false, 2, 0, s -> {
			if (!reloaded[0] && s.mechanic()[0].getMainHandItem().is(s.connector())) {
				reloaded[0] = true;
				s.mechanic()[0] = reload(helper.getLevel(), s.mechanic()[0]);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ready[0] != null && reloaded[0], "the Mechanic never carried the connector");
			Setup s = ready[0];
			assertWhole(helper, s);
			helper.assertTrue(MechanicCompatTests.count(s.chest(), s.connector()) == 1, "the belt took "
				+ (2 - MechanicCompatTests.count(s.chest(), s.connector())) + " belt connectors from the store, not 1");
		});
	}

	/** The villager saved and loaded in his place, as when the server restarts or his chunk unloads. */
	private static Villager reload(ServerLevel level, Villager old) {
		CompoundTag tag = new CompoundTag();
		if (!old.save(tag)) {
			throw new GameTestAssertException("the Mechanic wasn't saved");
		}
		old.discard();
		Entity back = EntityType.loadEntityRecursive(tag, level, e -> e);
		if (!(back instanceof Villager villager)) {
			throw new GameTestAssertException("the Mechanic didn't load as a villager: " + back);
		}
		level.addFreshEntity(villager);
		return villager;
	}
}

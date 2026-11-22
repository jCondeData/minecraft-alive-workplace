package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeat;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSeats;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.store.StorageNetworks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA lane-c 2026-11-22 (lane-c-1122-1553), written from the spec in docs/wiki/furniture-and-storage.md round three fixes:
 * <ul>
 * <li>B580: a villager without a bed claims a free bed of the Terrace next door, Handcrafted's fancy beds included, from
 * wherever he stands (behind the house too), and never a bed that is someone else's;</li>
 * <li>B1337: the furnishings switch turned off sends nobody to a chair and stands a seated villager up; turned on again,
 * the chair is free to sit on, whenever in the walk it was flicked;</li>
 * <li>B1025: a chest on a Tom's Simple Storage network counts once in the Storehouse and in Tom's own storage, whether
 * reached through the inventory connector, a cable connector or both, and gives out only what it holds.</li>
 * </ul>
 */
public class QaHomesStorage1122cCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HOUSE = new BlockPos(8, 2, 8);
	private static final BlockPos CHAIR = new BlockPos(13, 2, 10);
	private static final BlockPos STOREHOUSE = QaTomsNetworks1108cCompatTests.STOREHOUSE;
	private static final BlockPos CONNECTOR = new BlockPos(6, 2, 12);
	private static final BlockPos CABLE = new BlockPos(6, 2, 13);

	// ---------------------------------------------------------------- B580: the Terrace's fancy beds

	/** The Terrace, each bed a Handcrafted fancy bed; its four homes (bed heads) sorted west to east. */
	private record Terrace(BlockPos origin, BoundingBox box, List<BlockPos> beds) {
	}

	private static Terrace fancyTerrace(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Block fancy = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("handcrafted:dark_oak_fancy_bed"));
		helper.assertTrue(fancy instanceof BedBlock && fancy.defaultBlockState().is(BlockTags.BEDS), "Handcrafted's fancy bed isn't a bed");
		StructureTemplate house = level.getStructureManager().get(AliveWorkplace.id("terrace")).orElseThrow();
		BlockPos origin = helper.absolutePos(HOUSE);
		house.placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1122L), 2);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(house.getSize()).offset(-1, -1, -1));
		List<BlockPos> halves = new ArrayList<>();
		BlockPos.betweenClosedStream(box).filter(p -> level.getBlockState(p).getBlock() instanceof BedBlock).forEach(p -> halves.add(p.immutable()));
		helper.assertTrue(halves.size() == 8, "setup: the Terrace has " + halves.size() + " bed halves, not 8");
		for (BlockPos p : halves) {
			BlockState was = level.getBlockState(p);
			level.setBlock(p, fancy.defaultBlockState().setValue(BedBlock.FACING, was.getValue(BedBlock.FACING)).setValue(BedBlock.PART,
				was.getValue(BedBlock.PART)), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
		}
		List<BlockPos> beds = level.getPoiManager().findAll(h -> h.is(PoiTypes.HOME), box::isInside, box.getCenter(), 16, PoiManager.Occupancy.ANY)
			.map(BlockPos::immutable).sorted(Comparator.comparingInt(BlockPos::getX)).toList();
		helper.assertTrue(beds.size() == 4 && beds.stream().allMatch(b -> level.getBlockState(b).is(fancy)), "setup: the Terrace's homes are "
			+ beds.stream().map(b -> BuiltInRegistries.BLOCK.getKey(level.getBlockState(b).getBlock())).toList());
		return new Terrace(origin, box, beds);
	}

	private static void take(ServerLevel level, List<BlockPos> beds) {
		for (BlockPos taken : beds) {
			level.getPoiManager().take(h -> h.is(PoiTypes.HOME), (h, p) -> p.equals(taken), taken, 1); // someone else's
		}
	}

	/** Bedtime (13000) from now on; the returned runnable puts the clock back as if the test had never touched it. */
	private static Runnable night(ServerLevel level) {
		long day = level.getDayTime();
		long game = level.getGameTime();
		long now = level.getDayTime() % 24000;
		level.setDayTime((now <= 13000 ? level.getDayTime() / 24000 : level.getDayTime() / 24000 + 1) * 24000 + 13000);
		return () -> level.setDayTime(day + level.getGameTime() - game);
	}

	private static Villager villagerAt(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, helper.relativePos(at));
		villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		return villager;
	}

	private static String home(GameTestHelper helper, Villager v) {
		return "at " + helper.relativePos(v.blockPosition()).toShortString() + ", home "
			+ v.getBrain().getMemory(MemoryModuleType.HOME).map(g -> helper.relativePos(g.pos()).toShortString()).orElse("none")
			+ ", activity " + v.getBrain().getActiveNonCoreActivity();
	}

	/**
	 * B580's root cause with fancy beds: a villager without a bed stands on the grass five blocks behind the fancy-bed
	 * Terrace at bedtime (30 blocks' walk round to its door), the east home's beds someone else's. He still claims a free
	 * fancy bed of the west home, walks round, climbs to it and sleeps.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa1122c_fancy_behind"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa1122c_fancy_behind")
	public void aVillagerBehindTheTerraceTakesAFreeFancyBedAndSleeps(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Terrace t = fancyTerrace(helper);
		take(level, t.beds().subList(2, 4));
		List<BlockPos> free = t.beds().subList(0, 2);
		Runnable restore = night(level);
		PartnerShowsCompatTests.after(helper, restore);
		BlockPos at = t.origin().offset(7, 0, 14); // behind the back wall, five blocks out
		helper.assertTrue(level.getBlockState(at).isAir() && level.canSeeSky(at), "setup: the spot behind the Terrace isn't open ground: "
			+ level.getBlockState(at));
		Villager villager = villagerAt(helper, at);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> free.contains(g.pos())).isPresent(),
				"the villager behind the Terrace hasn't claimed a free fancy bed of the west home: " + home(helper, villager));
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(free::contains).isPresent(),
				"not asleep in a free fancy bed of the west home: " + home(helper, villager));
			villager.discard();
			restore.run();
		});
	}

	/**
	 * Every fancy bed of the Terrace someone else's: the villager without a bed claims none of them in 400 ticks of night
	 * (his home stays empty), and each bed still has its one owner, not a second.
	 */
	//$ gametest_ticks_batch HUGE_AREA '600' '"qa1122c_fancy_all_taken"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 600, batch = "qa1122c_fancy_all_taken")
	public void aVillagerWithoutABedTakesNoFancyBedThatIsSomeoneElses(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Terrace t = fancyTerrace(helper);
		take(level, t.beds());
		Runnable restore = night(level);
		PartnerShowsCompatTests.after(helper, restore);
		Villager villager = villagerAt(helper, t.origin().offset(9, 1, 5)); // the east kitchen beside its ladder
		String[] wrong = {null};
		helper.onEachTick(() -> {
			if (wrong[0] == null && villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent()) {
				wrong[0] = "with every bed someone else's, the villager claimed one " + helper.getTick() + " ticks in: " + home(helper, villager);
			}
			if (wrong[0] == null && villager.isSleeping()) {
				wrong[0] = "with every bed someone else's, the villager sleeps in one: " + home(helper, villager);
			}
		});
		helper.runAfterDelay(400, () -> {
			helper.assertTrue(wrong[0] == null, wrong[0]);
			for (BlockPos bed : t.beds()) {
				long freeTickets = level.getPoiManager().getType(bed).isPresent()
					? level.getPoiManager().getInSquare(h -> h.is(PoiTypes.HOME), bed, 0, PoiManager.Occupancy.HAS_SPACE).count() : -1;
				helper.assertTrue(freeTickets == 0, "the bed at " + helper.relativePos(bed).toShortString() + " shows free room again ("
					+ freeTickets + "), wanted it still taken by its owner");
			}
			villager.discard();
			restore.run();
			helper.succeed();
		});
	}

	/**
	 * Three of the four fancy beds someone else's (the east home's and one of the west home's): the villager in the east
	 * kitchen claims exactly the one bed left, the far one next door, and sleeps in it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1200' '"qa1122c_fancy_last_bed"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1200, batch = "qa1122c_fancy_last_bed")
	public void aVillagerWithoutABedTakesTheOneFancyBedLeftNextDoor(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Terrace t = fancyTerrace(helper);
		take(level, t.beds().subList(1, 4));
		BlockPos last = t.beds().get(0);
		Runnable restore = night(level);
		PartnerShowsCompatTests.after(helper, restore);
		Villager villager = villagerAt(helper, t.origin().offset(9, 1, 5));
		String[] wrong = {null};
		helper.onEachTick(() -> {
			if (wrong[0] == null && villager.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> !g.pos().equals(last)).isPresent()) {
				wrong[0] = "the villager claimed a bed that is someone else's: " + home(helper, villager);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(wrong[0] == null, wrong[0]);
			helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).filter(g -> g.pos().equals(last)).isPresent(),
				"the villager hasn't claimed the one free fancy bed at " + helper.relativePos(last).toShortString() + ": " + home(helper, villager));
			helper.assertTrue(villager.isSleeping() && villager.getSleepingPos().filter(last::equals).isPresent(),
				"not asleep in the one free fancy bed: " + home(helper, villager));
			villager.discard();
			restore.run();
		});
	}

	// ---------------------------------------------------------------- B1337: the chair and the furnishings switch

	private static Block oakChair(GameTestHelper helper) {
		Block chair = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("handcrafted:oak_chair"));
		helper.assertTrue(chair.defaultBlockState().is(FurnitureSeats.SEATS), "Handcrafted's oak chair isn't a villager seat");
		return chair;
	}

	/** Supper (sitting time); the returned runnable puts the clock back. */
	private static Runnable supper(ServerLevel level) {
		long day = level.getDayTime();
		long game = level.getGameTime();
		long time = FurnitureSeats.SUPPER + 300;
		long now = level.getDayTime() % 24000;
		level.setDayTime((now <= time ? level.getDayTime() / 24000 : level.getDayTime() / 24000 + 1) * 24000 + time);
		return () -> level.setDayTime(day + level.getGameTime() - game);
	}

	private static BlockPos seatPos(Villager v) {
		return v.getVehicle() instanceof FurnitureSeat seat ? seat.blockPosition() : null;
	}

	/** Back where he started and standing still, so an idle stroll can't take him out of the 8 blocks a seat is looked for in. */
	private static void hold(Villager v, Vec3 spot) {
		v.getNavigation().stop();
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.teleportTo(spot.x, spot.y, spot.z);
	}

	/** Sets up the chair and a villager 6 blocks west of it at supper, with the switch put back afterwards. */
	private static Villager chairScene(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(CHAIR, oakChair(helper).defaultBlockState());
		Villager v = helper.spawn(EntityType.VILLAGER, CHAIR.west(6));
		boolean wasOn = Furnishings.ENABLED;
		helper.assertTrue(wasOn, "setup: furnishings are off before the test");
		Runnable restore = supper(level);
		PartnerShowsCompatTests.after(helper, () -> {
			Furnishings.ENABLED = wasOn;
			restore.run();
		});
		return v;
	}

	/**
	 * The switch off before the villager ever picks the chair: for 200 ticks of supper he never sets off for it, never
	 * sits and nothing holds it. Turned on again, he sits on it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '900' '"qa1122c_seat_off_first"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 900, batch = "qa1122c_seat_off_first")
	public void withTheSwitchOffFromTheStartNobodyGoesToTheChairTillItIsOn(GameTestHelper helper) {
		Villager v = chairScene(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chair = helper.absolutePos(CHAIR);
		Vec3 spot = v.position();
		Furnishings.ENABLED = false;
		long[] onAt = {-1};
		String[] broken = {null};
		helper.onEachTick(() -> {
			if (onAt[0] < 0) {
				if (broken[0] == null && (v.isPassenger() || chair.equals(FurnitureSeats.walkingTo(v)))) {
					broken[0] = "with the switch off, " + helper.getTick() + " ticks in: " + FurnitureSeats.describe(level, v);
				}
				hold(v, spot);
			} else if (!v.isPassenger() && FurnitureSeats.walkingTo(v) == null) {
				hold(v, spot);
			}
		});
		helper.runAfterDelay(200, () -> {
			Furnishings.ENABLED = true;
			onAt[0] = helper.getTick();
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(broken[0] == null, broken[0]);
			helper.assertTrue(onAt[0] >= 0, "waiting with the switch off");
			helper.assertTrue(chair.equals(seatPos(v)), "the villager hasn't sat on the chair " + (helper.getTick() - onAt[0])
				+ " ticks after the switch came on; " + FurnitureSeats.describe(level, v));
			v.discard();
		});
	}

	/**
	 * A villager seated on the chair when the switch goes off stands up within 40 ticks, the chair free (no seat left in
	 * it), and he doesn't sit again while it is off; turned on again, he sits on it again.
	 */
	//$ gametest_ticks_batch HUGE_AREA '1000' '"qa1122c_seat_off_seated"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 1000, batch = "qa1122c_seat_off_seated")
	public void aSeatedVillagerStandsUpWhenTheSwitchGoesOffAndSitsAgainWhenOn(GameTestHelper helper) {
		Villager v = chairScene(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chair = helper.absolutePos(CHAIR);
		Vec3 spot = v.position();
		int[] step = {0};
		long[] at = {-1, -1};
		String[] broken = {null};
		helper.onEachTick(() -> {
			if (step[0] == 0) {
				if (chair.equals(seatPos(v))) {
					step[0] = 1;
					at[0] = helper.getTick();
					Furnishings.ENABLED = false;
					helper.runAfterDelay(160, () -> {
						Furnishings.ENABLED = true;
						step[0] = 2;
						at[1] = helper.getTick();
					});
				} else if (!v.isPassenger() && FurnitureSeats.walkingTo(v) == null) {
					hold(v, spot);
				}
			} else if (step[0] == 1) {
				long since = helper.getTick() - at[0];
				if (broken[0] == null && since > 40) {
					if (v.isPassenger()) {
						broken[0] = "the villager still sits " + since + " ticks after the switch went off; " + FurnitureSeats.describe(level, v);
					} else if (!level.getEntitiesOfClass(FurnitureSeat.class, new AABB(chair)).isEmpty()) {
						broken[0] = "a seat is still in the chair " + since + " ticks after the switch went off";
					} else if (chair.equals(FurnitureSeats.walkingTo(v))) {
						broken[0] = "the villager is walking back to the chair " + since + " ticks after the switch went off";
					}
				}
				if (!v.isPassenger()) {
					hold(v, spot);
				}
			} else if (!v.isPassenger() && FurnitureSeats.walkingTo(v) == null) {
				hold(v, spot);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] >= 1, "setup: the villager never sat on the chair; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(broken[0] == null, broken[0]);
			helper.assertTrue(step[0] >= 2, "waiting with the switch off");
			helper.assertTrue(chair.equals(seatPos(v)), "the villager hasn't sat on the chair again " + (helper.getTick() - at[1])
				+ " ticks after the switch came back on; " + FurnitureSeats.describe(level, v));
			v.discard();
		});
	}

	/**
	 * The switch flicked off for 30 ticks (one and a half seat checks) as the villager sets off for the chair, then on
	 * again: the short switch-off isn't counted as him giving the chair up, so he picks it again and sits on it.
	 */
	//$ gametest_ticks_batch HUGE_AREA '900' '"qa1122c_seat_flick"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 900, batch = "qa1122c_seat_flick")
	public void aSwitchFlickedOffBrieflyMidWalkStillLetsHimSit(GameTestHelper helper) {
		Villager v = chairScene(helper);
		ServerLevel level = helper.getLevel();
		BlockPos chair = helper.absolutePos(CHAIR);
		Vec3 spot = v.position();
		int[] step = {0};
		long[] onAt = {-1};
		helper.onEachTick(() -> {
			if (step[0] == 0 && chair.equals(FurnitureSeats.walkingTo(v)) && !v.isPassenger()) {
				step[0] = 1;
				Furnishings.ENABLED = false;
				helper.runAfterDelay(30, () -> {
					Furnishings.ENABLED = true;
					step[0] = 2;
					onAt[0] = helper.getTick();
				});
			} else if (step[0] != 1 && !v.isPassenger() && FurnitureSeats.walkingTo(v) == null) {
				hold(v, spot);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(step[0] >= 1, "setup: the villager never set off for the chair; " + FurnitureSeats.describe(level, v));
			helper.assertTrue(step[0] >= 2, "waiting with the switch off");
			helper.assertTrue(chair.equals(seatPos(v)), "the villager hasn't sat on the chair " + (helper.getTick() - onAt[0])
				+ " ticks after the switch was flicked back on; " + FurnitureSeats.describe(level, v));
			v.discard();
		});
	}

	// ---------------------------------------------------------------- B1025: a Tom's chest reached two ways

	/** The Storehouse, Tom's inventory connector and one cable south of it. */
	private static void network(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CONNECTOR, TomsStorageCompatTests.block("inventory_connector"));
		helper.setBlock(CABLE, TomsStorageCompatTests.block("inventory_cable"));
	}

	/** The store list: the connector alone (every chest here is on its network). */
	private static List<BlockPos> store(GameTestHelper helper) {
		List<BlockPos> store = SupplyContainers.find(helper.getLevel(), helper.absolutePos(STOREHOUSE), null);
		helper.assertTrue(store.equals(List.of(helper.absolutePos(CONNECTOR))),
			"the store: " + QaTomsNetworks1108cCompatTests.rel(helper, store) + ", wanted the connector alone");
		return store;
	}

	private static void shown(GameTestHelper helper, List<BlockPos> store, long wanted, String what) {
		long counted = SupplyContainers.count(helper.getLevel(), store, Items.COBBLESTONE);
		long toms = QaTomsNetworks1108cCompatTests.toms(helper, CONNECTOR, Items.COBBLESTONE);
		helper.assertTrue(counted == wanted && toms == wanted, "the Storehouse counts " + counted + " and Tom's connector shows " + toms
			+ " cobblestone, wanted " + wanted + " (" + what + ")");
	}

	private static long inChest(GameTestHelper helper, BlockPos rel) {
		Container c = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(rel));
		return c.countItem(Items.COBBLESTONE);
	}

	/** A chest that only touches the connector, holding a single cobblestone: counted 1, taken once, then 0. */
	//$ gametest_ticks_batch AREA '200' '"qa1122c_toms_touch_only"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1122c_toms_touch_only")
	public void aChestOnlyTouchingTheConnectorCountsItsOneItemOnce(GameTestHelper helper) {
		network(helper);
		BlockPos chest = new BlockPos(7, 2, 12);
		helper.setBlock(chest, Blocks.CHEST);
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE);
		QaTomsNetworks1108cCompatTests.put(helper, chest, new ItemStack(Items.COBBLESTONE, 1));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			shown(helper, store, 1, "one cobblestone in a chest touching the connector");
			int free = SupplyContainers.freeSlots(helper.getLevel(), store);
			helper.assertTrue(free == 26, "the store has " + free + " free slots, wanted the chest's 26");
			int first = SupplyContainers.extract(helper.getLevel(), store, Items.COBBLESTONE, 5);
			int second = SupplyContainers.extract(helper.getLevel(), store, Items.COBBLESTONE, 5);
			helper.assertTrue(first == 1 && second == 0, "took " + first + " then " + second + " of the chest's 1 cobblestone");
			shown(helper, store, 0, "after taking the one");
			helper.succeed();
		});
	}

	/** A chest reached only by a cable connector (it touches nothing else), holding one full stack: counted 64 once. */
	//$ gametest_ticks_batch AREA '200' '"qa1122c_toms_cable_only"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1122c_toms_cable_only")
	public void aChestOnlyOnACableConnectorCountsAFullStackOnce(GameTestHelper helper) {
		network(helper);
		BlockPos cableConnector = new BlockPos(6, 2, 14);
		BlockPos chest = new BlockPos(6, 2, 15);
		helper.setBlock(cableConnector, QaTomsNetworks1108cCompatTests.cableConnector(Direction.SOUTH));
		helper.setBlock(chest, Blocks.CHEST);
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, cableConnector);
		QaTomsNetworks1108cCompatTests.put(helper, chest, new ItemStack(Items.COBBLESTONE, 64));
		helper.runAfterDelay(40, () -> {
			Set<BlockPos> members = StorageNetworks.members(helper.getLevel(), helper.absolutePos(CONNECTOR));
			helper.assertTrue(members.equals(Set.of(helper.absolutePos(chest))), "the connector joins "
				+ QaTomsNetworks1108cCompatTests.rel(helper, List.copyOf(members)) + ", wanted the chest on the cable");
			List<BlockPos> store = store(helper);
			shown(helper, store, 64, "a full stack in a chest on the cable only");
			int free = SupplyContainers.freeSlots(helper.getLevel(), store);
			helper.assertTrue(free == 26, "the store has " + free + " free slots, wanted the chest's 26");
			helper.succeed();
		});
	}

	/**
	 * Two chests, each touching the connector and each faced by its own cable connector on the connector's cable: each
	 * counted once (10 + 20 = 30, not 60), with the two chests' room (52 free slots), and taking 50 gives exactly 30.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1122c_toms_two_chests"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1122c_toms_two_chests")
	public void twoChestsEachReachedTwoWaysCountOnceEach(GameTestHelper helper) {
		network(helper);
		BlockPos east = new BlockPos(7, 2, 12);
		BlockPos west = new BlockPos(5, 2, 12);
		BlockPos eastFacer = new BlockPos(7, 2, 13);
		BlockPos westFacer = new BlockPos(5, 2, 13);
		helper.setBlock(east, Blocks.CHEST);
		helper.setBlock(west, Blocks.CHEST);
		helper.setBlock(eastFacer, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		helper.setBlock(westFacer, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, eastFacer, westFacer);
		QaTomsNetworks1108cCompatTests.put(helper, east, new ItemStack(Items.COBBLESTONE, 10));
		QaTomsNetworks1108cCompatTests.put(helper, west, new ItemStack(Items.COBBLESTONE, 20));
		helper.runAfterDelay(40, () -> {
			Set<BlockPos> members = StorageNetworks.members(helper.getLevel(), helper.absolutePos(CONNECTOR));
			helper.assertTrue(members.equals(Set.of(helper.absolutePos(east), helper.absolutePos(west))), "the connector joins "
				+ QaTomsNetworks1108cCompatTests.rel(helper, List.copyOf(members)) + ", wanted the two chests");
			List<BlockPos> store = store(helper);
			shown(helper, store, 30, "10 and 20 in two chests, each reached two ways");
			int free = SupplyContainers.freeSlots(helper.getLevel(), store);
			helper.assertTrue(free == 52, "the store has " + free + " free slots, wanted the two chests' 52");
			int taken = SupplyContainers.extract(helper.getLevel(), store, Items.COBBLESTONE, 50);
			long left = inChest(helper, east) + inChest(helper, west);
			helper.assertTrue(taken == 30 && left == 0, "asked for 50 of the chests' 30: took " + taken + ", " + left + " left in them");
			shown(helper, store, 0, "after taking them all");
			helper.succeed();
		});
	}

	/**
	 * A chest reached two ways with room for only 5 more cobblestone: counted 1723 once, one free slot (not two), and a
	 * delivery of 10 puts 5 in and hands back the other 5: there is no second chest's room to put them in.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1122c_toms_nearly_full"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1122c_toms_nearly_full")
	public void aNearlyFullChestReachedTwoWaysHasRoomForItsLastFiveOnly(GameTestHelper helper) {
		network(helper);
		BlockPos chest = new BlockPos(7, 2, 12);
		BlockPos facer = new BlockPos(7, 2, 13);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(facer, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, facer);
		Container box = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(chest));
		for (int slot = 0; slot < 26; slot++) {
			box.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
		}
		box.setItem(26, new ItemStack(Items.COBBLESTONE, 59)); // room for 5
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			shown(helper, store, 26 * 64 + 59, "a chest 5 short of full, reached two ways");
			int free = SupplyContainers.freeSlots(helper.getLevel(), store);
			helper.assertTrue(free == 0, "the store has " + free + " free slots, wanted none (the one chest has no empty slot)");
			ItemStack rest = SupplyContainers.insert(helper.getLevel(), store, new ItemStack(Items.COBBLESTONE, 10));
			helper.assertTrue(rest.getCount() == 5, "a delivery of 10 into room for 5 left " + rest.getCount() + " over, wanted 5");
			long now = inChest(helper, chest);
			helper.assertTrue(now == 27 * 64, "the chest holds " + now + " cobblestone, wanted it full (1728)");
			shown(helper, store, 27 * 64, "the chest full, reached two ways");
			helper.succeed();
		});
	}

	/**
	 * A chest of 10 reached two ways: a builder takes 4 through the Storehouse (6 left, counted once), then a player takes
	 * through Tom's own storage (as from its terminal) and asking 15 gets the 6 that are there: the chest empty, nothing
	 * made out of nothing.
	 */
	//$ gametest_ticks_batch AREA '200' '"qa1122c_toms_take_both"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa1122c_toms_take_both")
	public void takingFromAChestReachedTwoWaysGivesEachItemOnce(GameTestHelper helper) {
		network(helper);
		BlockPos chest = new BlockPos(7, 2, 12);
		BlockPos facer = new BlockPos(7, 2, 13);
		helper.setBlock(chest, Blocks.CHEST);
		helper.setBlock(facer, QaTomsNetworks1108cCompatTests.cableConnector(Direction.NORTH));
		QaTomsNetworks1108cCompatTests.joinUp(helper, CONNECTOR, CABLE, facer);
		QaTomsNetworks1108cCompatTests.put(helper, chest, new ItemStack(Items.COBBLESTONE, 10));
		helper.runAfterDelay(40, () -> {
			List<BlockPos> store = store(helper);
			int taken = SupplyContainers.extract(helper.getLevel(), store, Items.COBBLESTONE, 4);
			helper.assertTrue(taken == 4, "took " + taken + " of 4 asked through the Storehouse");
			shown(helper, store, 6, "6 left after the builder took 4");
			helper.assertTrue(inChest(helper, chest) == 6, "the chest holds " + inChest(helper, chest) + ", wanted 6");
			Storage<ItemVariant> toms = ItemStorage.SIDED.find(helper.getLevel(), helper.absolutePos(CONNECTOR), null);
			helper.assertTrue(toms != null, "Tom's connector has no item storage");
			long got;
			try (Transaction tx = Transaction.openOuter()) {
				got = toms.extract(ItemVariant.of(Items.COBBLESTONE), 15, tx);
				tx.commit();
			}
			helper.assertTrue(got == 6, "a player asking Tom's storage for 15 of the chest's 6 got " + got);
			helper.assertTrue(inChest(helper, chest) == 0, "the chest still holds " + inChest(helper, chest));
			shown(helper, store, 0, "after the player took the rest");
			helper.succeed();
		});
	}
}

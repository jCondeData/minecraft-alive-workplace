package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.city.CityZones;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/** ROADMAP 27.2: a village's plan on its hall, the City Plan item and the zone kinds. */
public class CityPlanGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	private static BitSet cells(int... cells) {
		BitSet out = new BitSet();
		for (int c : cells) {
			out.set(c);
		}
		return out;
	}

	/** A plan with every field: two zones (one with the renew switch), a road, the wall line, the mode. */
	private static CityPlan fullPlan() {
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes 1", "cherry");
		plan = plan.addZone("keep_clear", "The old oak", "");
		plan = plan.paint(0, cells(0, 33, 1023)).paint(1, cells(500, 501));
		plan = plan.editZone(0, "homes", "Homes 1", "cherry", true);
		plan = plan.addRoad(new CityPlan.Road(List.of(new BlockPos(-20, 0, 0), new BlockPos(20, 0, 4)), 3, "stonework"));
		plan = plan.withWall(new CityPlan.Wall(List.of(new BlockPos(-30, 0, -30), new BlockPos(30, 0, -30), new BlockPos(30, 0, 30)), true));
		return plan.withMode(CityPlan.Mode.RUN);
	}

	//$ gametest_ticks_batch AREA '20' '"cityPlanSaved"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanSaved")
	public void aPlanSurvivesSaveAndReloadAndBreakingThenPlacingTheHall(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		CityPlan plan = fullPlan();
		helper.assertTrue(plan.zones().size() == 2 && plan.roads().size() == 1 && plan.wall().isPresent(), "setup: " + plan);
		entity.setPlan(plan);

		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
		helper.assertTrue(copy != null && copy.plan().equals(plan), "not saved: " + (copy == null ? null : copy.plan()));

		// Broken: the hall item carries the plan (as it carries the name); put down elsewhere, it is the same plan there.
		List<ItemStack> drops = Block.getDrops(level.getBlockState(hall), level, hall, entity);
		ItemStack item = drops.stream().filter(s -> s.is(ModBlocks.VILLAGE_HALL.asItem())).findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(plan.equals(item.get(ModComponents.CITY_PLAN)), "the hall item lost the plan: " + item.getComponents());
		helper.setBlock(HALL, net.minecraft.world.level.block.Blocks.AIR);
		BlockPos moved = new BlockPos(3, 2, 12);
		helper.setBlock(moved, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity placed = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(moved));
		placed.applyComponentsFromItemStack(item);
		helper.assertTrue(placed.plan().equals(plan), "placed again, the plan is " + placed.plan());
		BlockPos at = helper.absolutePos(moved);
		BlockPos homesCell = CityPlan.cellCentre(at, 33);
		helper.assertTrue(placed.plan().zoneAt(at, homesCell).map(CityPlan.Zone::name).equals(Optional.of("Homes 1")),
			"the plan isn't centred on the new spot: " + placed.plan().zoneAt(at, homesCell));
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '20' '"cityPlanOldHall"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanOldHall")
	public void aHallSavedBefore11LoadsWithAnEmptyPlan(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		CompoundTag tag = level.getBlockEntity(hall).saveWithFullMetadata(level.registryAccess());
		tag.remove("plan"); // as a hall saved by 1.0
		VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
		helper.assertTrue(copy != null && copy.plan().equals(CityPlan.EMPTY), "an old hall's plan: " + (copy == null ? null : copy.plan()));
		helper.succeed();
	}

	/** zoneAt in all four quarters round the hall, at negative coordinates, and with a hall radius of 128. */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"cityPlanZoneAt"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "cityPlanZoneAt")
	public void zoneAtFindsTheRightZoneEverywhere(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		for (int r : new int[] {64, 128}) {
			VillageHalls.RADIUS = r;
			int size = CityPlan.cellSize();
			helper.assertTrue(size == r / 16, "radius " + r + ": a cell is " + size + " blocks");
			for (BlockPos hall : new BlockPos[] {new BlockPos(100, 70, 100), new BlockPos(-1000, 64, -3333), new BlockPos(5, 64, -7)}) {
				int[][] quarters = {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
				CityPlan plan = CityPlan.EMPTY;
				for (int q = 0; q < 4; q++) {
					plan = plan.addZone("homes", "Q" + q, "");
					BlockPos p = hall.offset(quarters[q][0] * (r - 3), 0, quarters[q][1] * (r - 3));
					int cell = CityPlan.cellAt(hall, p);
					helper.assertTrue(cell >= 0, "radius " + r + ", hall " + hall + ": " + p + " is outside the grid");
					plan = plan.paint(q, cells(cell));
				}
				for (int q = 0; q < 4; q++) {
					BlockPos p = hall.offset(quarters[q][0] * (r - 3), 0, quarters[q][1] * (r - 3));
					helper.assertTrue(plan.zoneAt(hall, p).map(CityPlan.Zone::name).equals(Optional.of("Q" + q)),
						"radius " + r + ", hall " + hall + ": at " + p + " found " + plan.zoneAt(hall, p));
					// The block next to it, across the cell's edge towards the hall, is not that zone.
					BlockPos inward = p.offset(-quarters[q][0] * size, 0, -quarters[q][1] * size);
					helper.assertTrue(plan.zoneAt(hall, inward).isEmpty(), "radius " + r + ": " + inward + " is in " + plan.zoneAt(hall, inward));
				}
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(r + size, 0, 0)) == -1, "past the grid's edge is a cell");
				helper.assertTrue(CityPlan.cellAt(hall, hall.offset(-r, 0, -r)) == 0, "the north-west corner isn't cell 0");
			}
		}
		helper.succeed();
	}

	/** Painting goes through the packet the screen sends: a cell nearer another hall is refused, the rest painted. */
	//$ gametest_ticks_batch AREA '40' '"cityPlanOtherHall"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "cityPlanOtherHall")
	public void aCellNearerAnotherHallIsRefused(GameTestHelper helper) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16; // cells of one block, so both halls fit the test area
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(3, 2, 8), ModBlocks.VILLAGE_HALL);
		helper.setBlock(new BlockPos(13, 2, 8), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(3, 2, 8));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		CityPlans.apply(player, CityPlans.Edit.addZone(hall, "homes", "Homes", ""));
		int mine = CityPlan.cellAt(hall, hall.east(2));
		int theirs = CityPlan.cellAt(hall, hall.east(8)); // 8 from this hall, 2 from the other
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(CityPlans.apply(player, CityPlans.Edit.paint(hall, 0, cells(mine, theirs))), "the paint packet changed nothing");
			CityPlan plan = ((VillageHallBlockEntity) level.getBlockEntity(hall)).plan();
			helper.assertTrue(plan.zones().get(0).has(mine), "its own cell wasn't painted");
			helper.assertFalse(plan.zones().get(0).has(theirs), "a cell nearer the other hall was painted");
			helper.assertFalse(CityPlans.apply(player, CityPlans.Edit.paint(hall, 0, cells(theirs))), "painting only the other village's cell changed the plan");
			helper.succeed();
		});
	}

	/** A stranger's packets for someone else's village are refused; the owner's and a hall nobody owned go through. */
	//$ gametest_ticks_batch AREA '20' '"cityPlanStranger"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanStranger")
	public void aStrangerCantChangeSomeoneElsesPlan(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(entity.owner() == null, "setup: the hall has an owner");
		helper.assertTrue(CityPlans.apply(first, CityPlans.Edit.addZone(hall, "homes", "Homes", "")), "the first painter was refused");
		helper.assertTrue(first.getUUID().equals(entity.owner()), "a hall nobody owned didn't become the first painter's");
		entity.setProtected(true);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!stranger.getUUID().equals(first.getUUID()), "setup: the same mock player twice");
		int near = CityPlan.cellAt(hall, hall.east(2)); // nearer this hall than any other test's
		helper.assertFalse(CityPlans.apply(stranger, CityPlans.Edit.paint(hall, 0, cells(near))), "a stranger painted someone else's plan");
		helper.assertFalse(CityPlans.apply(stranger, CityPlans.Edit.removeZone(hall, 0)), "a stranger deleted a zone");
		helper.assertTrue(entity.plan().zones().size() == 1 && entity.plan().zones().get(0).cells().isEmpty(), "the plan changed: " + entity.plan());
		helper.assertTrue(CityPlans.apply(first, CityPlans.Edit.paint(hall, 0, cells(near))), "the owner couldn't paint");
		helper.assertFalse(CityPlans.apply(first, CityPlans.Edit.addZone(hall, "no_such_kind", "?", "")), "a zone of an unknown kind was added");
		helper.succeed();
	}

	/** The item binds to a hall and names its village; right-clicked in a zone, it says which. */
	//$ gametest_ticks_batch AREA '20' '"cityPlanItem"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanItem")
	public void theCityPlanBindsAndNamesItsVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack stack = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, stack, hall);
		var bound = stack.get(ModComponents.CITY_PLAN_HALL);
		helper.assertTrue(bound != null && bound.hall().pos().equals(hall), "not bound: " + bound);
		helper.assertTrue(bound.name().getString().equals(VillageHalls.name(level, hall).getString()), "names " + bound.name().getString());
		List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
		stack.getItem().appendHoverText(stack, net.minecraft.world.item.Item.TooltipContext.of(level), tooltip, net.minecraft.world.item.TooltipFlag.NORMAL);
		helper.assertTrue(tooltip.stream().anyMatch(c -> c.getString().contains(VillageHalls.name(level, hall).getString())), "tooltip: " + tooltip);
		helper.succeed();
	}

	/** The shipped kinds load in order; a pack's kind is read; a broken file is refused (and skipped by the loader). */
	//$ gametest_ticks_batch EMPTY_STRUCTURE '20' '"cityZoneKinds"'
	@GameTest(template = EMPTY_STRUCTURE, timeoutTicks = 20, batch = "cityZoneKinds")
	public void zoneKindsAreData(GameTestHelper helper) {
		List<String> ids = CityZones.all().stream().map(CityZones.Kind::id).toList();
		helper.assertTrue(ids.equals(List.of("homes", "workshops", "farms", "market", "civic", "gardens", "defences", "keep_clear")),
			"the shipped kinds: " + ids);
		helper.assertFalse(CityZones.get("keep_clear").orElseThrow().buildable(), "Keep Clear is buildable");
		var pack = CityZones.read("mypack:orchards", JsonParser.parseString(
			"{\"color\": \"green\", \"map_tint\": \"#226622\", \"icon\": \"minecraft:apple\", \"order\": 9}").getAsJsonObject());
		helper.assertTrue(pack.mapTint() == 0x226622 && pack.buildable() && pack.order() == 9, "a pack's kind: " + pack);
		boolean refused = false;
		try {
			CityZones.read("broken", JsonParser.parseString("{\"color\": \"no_such_colour\", \"icon\": \"minecraft:apple\"}").getAsJsonObject());
		} catch (RuntimeException e) {
			refused = true;
		}
		helper.assertTrue(refused, "a kind with a bad colour was read");
		helper.succeed();
	}
}

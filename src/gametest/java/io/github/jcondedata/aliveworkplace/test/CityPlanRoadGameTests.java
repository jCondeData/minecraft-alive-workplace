package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanGround;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.city.CityZones;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** ROADMAP 27.4: roads and the wall line on the plan, the plan on the ground and on the hall's map. */
public class CityPlanRoadGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(8, 2, 8);

	/** An edit as the screen sends it: written to a packet and read back, as the server receives it. */
	private static CityPlans.Edit sent(ServerLevel level, CityPlans.Edit edit) {
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
		CityPlans.Edit.CODEC.encode(buf, edit);
		return CityPlans.Edit.CODEC.decode(buf);
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper) {
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
	}

	private static BlockPos p(int dx, int dz) {
		return new BlockPos(dx, 0, dz);
	}

	/** The cells covering offsets x0..x1, z0..z1 from the hall. */
	private static BitSet rect(BlockPos hall, int x0, int z0, int x1, int z1) {
		BitSet out = new BitSet();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				out.set(CityPlan.cellAt(hall, hall.offset(x, 0, z)));
			}
		}
		return out;
	}

	/** A road and the wall line drawn through the packets survive save and reload; the road is approved and takes its zone's style. */
	//$ gametest_ticks_batch AREA '20' '"cityPlanRoadSaved"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanRoadSaved")
	public void roadsAndTheWallLineSurviveSaveAndReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addZone(hall, "homes", "Homes", "cherry"))), "no zone added");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.paint(hall, 0, rect(hall, -8, -8, 7, 7)))), "no cells painted");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(-2, 0), p(20, 0), p(20, 30)), CityPlan.Road.AVENUE, ""))),
			"the road was refused");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(-40, -40), p(-30, -40)), CityPlan.Road.LANE, "stonework"))),
			"the lane was refused");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.wall(hall, List.of(p(-50, -50), p(50, -50), p(50, 50), p(-50, 50)), false))),
			"the wall line was refused");
		CityPlan plan = entity.plan();
		helper.assertTrue(plan.roads().size() == 2, "roads: " + plan.roads());
		CityPlan.Road avenue = plan.roads().get(0);
		helper.assertTrue(avenue.width() == 5 && avenue.approved() && avenue.style().equals("cherry") && avenue.points().size() == 3,
			"the avenue (approved, in the Homes zone's style): " + avenue);
		CityPlan.Road lane = plan.roads().get(1);
		helper.assertTrue(lane.width() == 1 && lane.approved() && lane.style().equals("stonework"), "the lane keeps its own style: " + lane);
		helper.assertTrue(plan.wall().isPresent() && !plan.wall().get().closed() && plan.wall().get().points().size() == 4, "the wall line: " + plan.wall());

		CompoundTag tag = entity.saveWithFullMetadata(level.registryAccess());
		VillageHallBlockEntity copy = (VillageHallBlockEntity) BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
		helper.assertTrue(copy != null && copy.plan().equals(plan), "not saved: " + (copy == null ? null : copy.plan()));

		// a road saved before 27.4 (no "approved") loads, unapproved; and undo takes the wall line off again
		CompoundTag old = (CompoundTag) CityPlan.Road.CODEC.encodeStart(NbtOps.INSTANCE, lane).getOrThrow();
		old.remove("approved");
		CityPlan.Road loaded = CityPlan.Road.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
		helper.assertTrue(!loaded.approved() && loaded.points().equals(lane.points()), "an old road: " + loaded);
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.undo(hall))), "undo was refused");
		helper.assertTrue(entity.plan().wall().isEmpty() && entity.plan().roads().size() == 2, "undo didn't take the wall line off: " + entity.plan());
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.removeRoad(hall, 0))), "taking a road off was refused");
		helper.assertTrue(entity.plan().roads().size() == 1 && entity.plan().roads().get(0).equals(lane), "after taking the avenue off: " + entity.plan().roads());
		helper.succeed();
	}

	/** A road of 65 points, a 25th road, a point off the map, a width of 2 and a stranger's road are all refused. */
	//$ gametest_ticks_batch AREA '20' '"cityPlanRoadRefused"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanRoadRefused")
	public void tooManyPointsOrRoadsAreRefused(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = entity.getBlockPos();
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		List<BlockPos> long64 = new ArrayList<>();
		for (int i = 0; i < CityPlan.MAX_ROAD_POINTS + 1; i++) {
			long64.add(p(-60 + i, (i % 2) * 3));
		}
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, long64, 3, ""))), "a road of 65 points went on");
		helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, long64.subList(0, 64), 3, ""))), "a road of 64 points was refused");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.wall(hall, long64, true))), "a wall line of 65 points went on");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(0, 0), p(CityPlan.half() + 5, 0)), 3, ""))),
			"a road off the map went on");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(0, 0), p(10, 0)), 2, ""))), "a road 2 wide went on");
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(0, 0)), 3, ""))), "a road of one point went on");
		for (int i = 1; i < CityPlan.MAX_ROADS; i++) {
			helper.assertTrue(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(-60, -60 + i * 4), p(60, -60 + i * 4)), 1, ""))),
				"road " + (i + 1) + " was refused");
		}
		helper.assertTrue(entity.plan().roads().size() == CityPlan.MAX_ROADS, "roads: " + entity.plan().roads().size());
		helper.assertFalse(CityPlans.apply(player, sent(level, CityPlans.Edit.addRoad(hall, List.of(p(0, 10), p(10, 10)), 3, ""))), "a 25th road went on");
		helper.assertTrue(entity.plan().roads().size() == CityPlan.MAX_ROADS, "roads after the 25th: " + entity.plan().roads().size());
		helper.assertTrue(entity.plan().roads().stream().allMatch(CityPlan.Road::approved), "a player's road isn't approved");

		entity.setProtected(true);
		ServerPlayer stranger = helper.makeMockServerPlayerInLevel();
		CityPlan before = entity.plan();
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.removeRoad(hall, 0))), "a stranger took a road off");
		helper.assertFalse(CityPlans.apply(stranger, sent(level, CityPlans.Edit.wall(hall, List.of(p(0, 0), p(9, 9)), true))), "a stranger drew a wall line");
		helper.assertTrue(entity.plan().equals(before), "a stranger changed the plan");
		helper.succeed();
	}

	/** The hall's map of a planned village: zone tints on the zone's pixels (the tint itself on its edge), roads and wall over them. */
	//$ gametest_ticks_batch AREA '20' '"cityPlanMap"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanMap")
	public void theMapOfAPlannedVillageHasItsZoneTints(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = entity.getBlockPos();
		// no plan: the map is the land alone
		byte[] land = VillageMaps.colors(level, hall, 64, 1);
		helper.assertTrue(java.util.Arrays.equals(colors(level, VillageMaps.map(level, hall)), land), "a village with no plan has a changed map");

		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes", "").addZone("gardens", "Gardens", "");
		plan = plan.paint(0, rect(hall, 8, 8, 15, 15)).paint(1, rect(hall, -16, 8, -9, 15));
		plan = plan.addRoad(new CityPlan.Road(List.of(p(-30, -20), p(30, -20)), 3, "", true));
		plan = plan.withWall(new CityPlan.Wall(List.of(p(-40, -40), p(40, -40), p(40, -30)), true));
		entity.setPlan(plan);
		byte[] map = colors(level, VillageMaps.map(level, hall));
		int homes = CityZones.get("homes").orElseThrow().mapTint();
		int gardens = CityZones.get("gardens").orElseThrow().mapTint();
		helper.assertTrue(homes != gardens, "setup: Homes and Gardens share a tint");
		// a pixel a block, the hall at 64, 64
		int inside = px(11, 11), edge = px(8, 11), gardenInside = px(-12, 12), gardenEdge = px(-9, 12), outside = px(25, 25);
		helper.assertTrue(map[inside] == VillageMaps.tinted(land[inside], homes), "inside Homes: " + map[inside] + " not " + VillageMaps.tinted(land[inside], homes));
		helper.assertTrue(map[edge] == VillageMaps.nearest(homes), "Homes' edge: " + map[edge] + " not " + VillageMaps.nearest(homes));
		helper.assertTrue(map[gardenInside] == VillageMaps.tinted(land[gardenInside], gardens), "inside Gardens: " + map[gardenInside]);
		helper.assertTrue(map[gardenEdge] == VillageMaps.nearest(gardens), "Gardens' edge: " + map[gardenEdge]);
		helper.assertTrue(map[edge] != map[gardenEdge], "the two zones' edges are the same colour");
		helper.assertTrue(map[outside] == land[outside], "a pixel outside every zone changed: " + map[outside] + " / " + land[outside]);
		helper.assertTrue(map[px(0, -20)] == VillageMaps.ROAD && map[px(0, -21)] == VillageMaps.ROAD && map[px(0, -19)] == VillageMaps.ROAD
			&& map[px(0, -23)] != VillageMaps.ROAD, "the street isn't 3 pixels wide on the map");
		helper.assertTrue(map[px(0, -40)] == VillageMaps.WALL && map[px(40, -35)] == VillageMaps.WALL && map[px(0, -35)] == VillageMaps.WALL,
			"the closed wall line isn't on the map with its closing side");
		helper.succeed();
	}

	/** While holding the plan: dots on the ground along the zone's edge, the road and the wall line, in their colours, and only within 24 blocks. */
	//$ gametest_ticks_batch AREA '20' '"cityPlanGround"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "cityPlanGround")
	public void thePlanShowsOnTheGroundRoundThePlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper);
		BlockPos hall = entity.getBlockPos();
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes", "").paint(0, rect(hall, 4, 4, 7, 7));
		plan = plan.addRoad(new CityPlan.Road(List.of(p(-10, 0), p(10, 0)), 3, "", true));
		plan = plan.withWall(new CityPlan.Wall(List.of(p(-12, -12), p(12, -12)), false));
		int homes = CityZones.get("homes").orElseThrow().color().getTextureDiffuseColor() & 0xFFFFFF;
		List<CityPlanGround.Dot> dots = CityPlanGround.dots(level, hall, plan, hall);
		int floor = helper.absolutePos(new BlockPos(0, 1, 0)).getY() + 1;
		helper.assertTrue(dots.stream().anyMatch(d -> d.color() == homes && Math.abs(d.x() - (hall.getX() + 4.25)) < 0.01),
			"no dust on the Homes zone's west edge");
		helper.assertTrue(dots.stream().anyMatch(d -> d.color() == CityPlanGround.ROAD_COLOR && Math.abs(d.z() - (hall.getZ() + 0.5 - 1.5)) < 0.01)
			&& dots.stream().anyMatch(d -> d.color() == CityPlanGround.ROAD_COLOR && Math.abs(d.z() - (hall.getZ() + 0.5 + 1.5)) < 0.01),
			"the street's two sides aren't on the ground");
		helper.assertTrue(dots.stream().anyMatch(d -> d.color() == CityPlanGround.WALL_COLOR), "the wall line isn't on the ground");
		helper.assertTrue(dots.stream().filter(d -> d.color() == homes).allMatch(d -> d.y() >= floor && d.y() < floor + 2),
			"the dust isn't on the ground: " + dots.stream().filter(d -> d.color() == homes).findFirst().map(CityPlanGround.Dot::y) + " vs floor " + floor);
		helper.assertTrue(dots.stream().allMatch(d -> Math.hypot(d.x() - hall.getX() - 0.5, d.z() - hall.getZ() - 0.5) <= CityPlanGround.REACH),
			"dust further than 24 blocks");
		helper.assertTrue(CityPlanGround.dots(level, hall, plan, hall.offset(200, 0, 0)).isEmpty(), "dust shows for a player 200 blocks off");
		helper.succeed();
	}

	private static int px(int dx, int dz) {
		return (dx + 64) + (dz + 64) * 128;
	}

	private static byte[] colors(ServerLevel level, ItemStack map) {
		MapItemSavedData data = level.getMapData(map.get(DataComponents.MAP_ID));
		return data.colors;
	}
}

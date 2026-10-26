package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.city.Roads;
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (27.19): a lane the Steward makes by himself when a house finishes ({@link Roads#joinNearest}) is not one the owner
 * approved by hand, so it must not be built through a section of the player ledger.
 */
public class QaLaneLedgerBugTests {
	private static final String AREA = "aliveworkplace_test:huge_area";

	private static BlockPos p(GameTestHelper helper, BlockPos hall, int x, int z) {
		BlockPos at = helper.absolutePos(new BlockPos(x, 2, z));
		return new BlockPos(at.getX() - hall.getX(), 0, at.getZ() - hall.getZ());
	}

	//$ gametest_ticks_batch AREA '60' '"qaLaneLedger"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLaneLedger")
	public void aSelfMadeLaneIsNotBuiltThroughAPlayersSection(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.stewardSites(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite s : new ArrayList<>(manager.all())) {
			if (near.isInside(s.placement().origin())) {
				manager.remove(s.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		PlayerBuilt.get(level).forget(near);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 14; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		BlockPos hallAt = new BlockPos(26, 2, 26);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BlockPos table = new BlockPos(20, 2, 20);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(table.west(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(table.west());
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 64));
		chest.setItem(1, new ItemStack(Items.CRACKED_STONE_BRICKS, 32));
		chest.setItem(2, new ItemStack(Items.COBBLESTONE, 64));
		chest.setItem(3, new ItemStack(Items.STONE_BRICK_STAIRS, 16));
		Villager builder = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, builder, helper.absolutePos(table));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, hallAt.north(2)));
		ItemStack planItem = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, planItem, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, planItem) == InteractionResult.SUCCESS, "setup: Steward not appointed");

		// The owner's street, routed and already laid.
		entity.setPlan(CityPlan.EMPTY.addRoad(new CityPlan.Road(List.of(p(helper, hall, 2, 4), p(helper, hall, 27, 4)), CityPlan.Road.STREET, "stonework", true)));
		Roads.routeNow(level, hall);
		CityPlan.Road street = entity.plan().roads().get(0);
		for (int n = 0; n < street.segments(); n++) {
			street = entity.plan().roads().get(0);
			Optional<Roads.Segment> seg = Roads.segment(level, hall, street, n);
			helper.assertTrue(seg.isPresent(), "setup: street segment " + n + " has no blueprint");
			for (Blueprint.Entry e : seg.get().blueprint().blocks()) {
				level.setBlock(seg.get().placement().origin().offset(e.pos()), e.state(), Block.UPDATE_CLIENTS);
			}
			BuildSite s = manager.create(owner.getUUID(), "", seg.get().blueprint().id(), seg.get().placement());
			Roads.save(level, seg.get().blueprint());
			Roads.segmentBuilt(level, s, null);
			manager.remove(s.id());
		}

		// A house finished: the Steward joins its door to the street with a lane by himself.
		ResourceLocation well = AliveWorkplace.id("well");
		BlueprintData.Placement placement = new BlueprintData.Placement(Ids.of(level.dimension()), helper.absolutePos(new BlockPos(12, 2, 16)), Rotation.NONE, Mirror.NONE);
		BuildSite house = manager.create(owner.getUUID(), "", well, placement);
		boolean was = Roads.ENABLED;
		try {
			Roads.ENABLED = true;
			helper.assertTrue(Roads.joinNearest(level, house), "setup: no lane made");
			manager.remove(house.id());
			Roads.routeNow(level, hall);
			helper.assertTrue(entity.plan().roads().size() == 2, "setup: no lane on the plan");
			CityPlan.Road lane = entity.plan().roads().get(1);
			helper.assertTrue(lane.lane() && lane.routed() && lane.route().size() >= 5, "setup: the lane wasn't routed: " + lane);

			// A player places a block beside the lane's middle, in the same 16x16x16 section: that section is marked.
			BlockPos mid = lane.route().get(lane.route().size() / 2).offset(hall);
			BlockPos spot = null;
			for (BlockPos c : List.of(mid.east(2), mid.west(2), mid.north(2), mid.south(2), mid.east(), mid.west())) {
				BlockPos rel = c.subtract(hall);
				boolean onRoute = lane.route().stream().anyMatch(o -> o.getX() == rel.getX() && o.getZ() == rel.getZ())
					|| entity.plan().roads().get(0).route().stream().anyMatch(o -> o.getX() == rel.getX() && o.getZ() == rel.getZ());
				if (!onRoute && SectionPos.asLong(c) == SectionPos.asLong(mid) && level.getBlockState(c).isAir()) {
					spot = c;
					break;
				}
			}
			helper.assertTrue(spot != null, "setup: no free spot beside the lane in its section");
			helper.assertTrue(StewardSafetyGameTests.place(helper, owner, spot, Blocks.OAK_PLANKS), "setup: the player couldn't place");
			helper.assertTrue(PlayerBuilt.get(level).marked(mid), "setup: the ledger didn't mark the lane's section");

			int throughMarked = 0;
			for (int n = 0; n < lane.segments(); n++) {
				Optional<Roads.Segment> seg = Roads.segment(level, hall, lane, n);
				if (seg.isPresent() && StewardSafety.check(level, hall, seg.get().box(), false).orElse(null) == StewardSafety.Refusal.PLAYER_BUILT) {
					throughMarked++;
				}
			}
			helper.assertTrue(throughMarked > 0, "setup: no lane segment reaches the marked section");

			Roads.round(level, hall, entity);
			Roads.round(level, hall, entity);
			Roads.round(level, hall, entity);
			lane = entity.plan().roads().get(1);
			List<String> opened = new ArrayList<>();
			for (int n = 0; n < lane.segments(); n++) {
				ResourceLocation id = Roads.segmentId(hall, lane, n);
				Optional<Roads.Segment> seg = Roads.segment(level, hall, lane, n);
				if (Roads.openSegments(level, hall).stream().anyMatch(s -> s.structure().equals(id))
					&& seg.isPresent() && PlayerBuilt.get(level).marked(seg.get().box())) {
					opened.add("segment " + n + " " + seg.get().box());
				}
			}
			helper.assertTrue(opened.isEmpty(), "the Steward's own lane (not approved by hand) was opened through the player's marked section: "
				+ opened + " (player block at " + spot + ", lane " + lane.route().size() + " nodes, open sites "
				+ Roads.openSegments(level, hall).size() + ")");
		} finally {
			Roads.ENABLED = was;
			for (BuildSite s : new ArrayList<>(Roads.openSegments(level, hall))) {
				manager.remove(s.id());
			}
		}
		helper.succeed();
	}
}

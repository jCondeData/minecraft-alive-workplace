package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.city.Walls;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderPlans;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (lane-b-1101-0338), from 27.18 ("walls along the wall line", the Steward's sites careful on their own, 27.19) and
 * B714's rule that a Wonder holds its ground like any build: an approved wall line drawn across a finished building, a
 * finished Wonder or a Wonder laid out and waiting for its goods must not get a wall site opened through it. Each test
 * has its control: the same line, with nothing there, opens its first piece on that spot.
 */
public class QaStewardWalls1101Tests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	/** The Test Folly's stage I footprint, 9 by 9. */
	private static final int FOLLY_SIZE = 9;

	private record Village(BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner, Villager builder, Villager steward) {
	}

	/** Flat grass, old sites, finished builds and Wonders round the area forgotten, and a hall put down. */
	private static BlockPos ground(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 80, level.getMinBuildHeight(), a.getZ() - 80, a.getX() + 110, level.getMaxBuildHeight(), a.getZ() + 110);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 16; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		// the area's barrier lid would be the wall's ground (a piece stands on the highest ground near the hall's height)
		BlockPos.betweenClosed(helper.absolutePos(new BlockPos(0, 2, 0)), helper.absolutePos(new BlockPos(29, 29, 29))).forEach(at -> {
			if (level.getBlockState(at).is(Blocks.BARRIER)) {
				level.setBlock(at, Blocks.AIR.defaultBlockState(), 2);
			}
		});
		// B859: a hall an earlier batch left just outside the area is nearer the wall's corner piece than this one, so the
		// Steward rightly skips that piece as another village's ground (2 of 10 nightly repeats opened the second piece)
		Leftovers.halls(helper);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.wonderGround(helper, hall);
		return hall;
	}

	/** A hall with an owner, a Steward, one builder with a stocked bench, and an approved palisade 10 blocks out. */
	private static Village village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = ground(helper);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BlockPos table = HALL.offset(-4, 0, 0);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		helper.setBlock(table.west(), Blocks.CHEST);
		Container chest = helper.getBlockEntity(table.west());
		chest.setItem(0, new ItemStack(Items.SPRUCE_LOG, 64));
		chest.setItem(1, new ItemStack(Items.STRIPPED_SPRUCE_LOG, 64));
		chest.setItem(2, new ItemStack(Items.SPRUCE_FENCE, 64));
		chest.setItem(3, new ItemStack(Items.SPRUCE_PLANKS, 64));
		chest.setItem(4, new ItemStack(Items.LADDER, 16));
		chest.setItem(5, new ItemStack(Items.DIRT, 64));
		Villager builder = helper.spawn(EntityType.VILLAGER, table.east());
		Builders.employ(level, builder, helper.absolutePos(table));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.north(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		int half = 10;
		CityPlan.Wall wall = new CityPlan.Wall(List.of(new BlockPos(-half, 0, -half), new BlockPos(half, 0, -half),
			new BlockPos(half, 0, half), new BlockPos(-half, 0, half)), true).approvedWith("palisade");
		entity.setPlan(entity.plan().withWall(wall));
		return new Village(hall, entity, owner, builder, steward);
	}

	private static boolean footprintsMeet(BoundingBox one, BoundingBox two) {
		return one.minX() <= two.maxX() && one.maxX() >= two.minX() && one.minZ() <= two.maxZ() && one.maxZ() >= two.minZ();
	}

	/** The wall's first piece still to build: the one the Steward opens first on open ground. */
	private static Walls.Piece first(GameTestHelper helper, Village v) {
		List<Walls.Piece> unbuilt = Walls.unbuilt(helper.getLevel(), v.hall());
		helper.assertTrue(unbuilt.size() >= 4, "setup: the approved wall has only " + unbuilt.size() + " pieces to build");
		helper.assertTrue(unbuilt.stream().allMatch(p -> p.box().minY() == v.hall().getY()),
			"setup: the pieces aren't on the area's floor: " + unbuilt.get(0).box() + " (hall y " + v.hall().getY() + ")");
		return unbuilt.get(0);
	}

	/** The wall sites the Steward's round opens now. */
	private static List<BuildSite> round(GameTestHelper helper, Village v) {
		Walls.round(helper.getLevel(), v.hall(), v.entity());
		return Walls.openSites(helper.getLevel(), v.hall());
	}

	private static BoundingBox box(GameTestHelper helper, BuildSite site) {
		return io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(helper.getLevel(), site.structure())
			.map(b -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(site.placement(), b.size())).orElseThrow();
	}

	/** Runs {@code body} with walls on, restoring the switch after. */
	private static void withWalls(Runnable body) {
		boolean was = Walls.ENABLED;
		Walls.ENABLED = true;
		try {
			body.run();
		} finally {
			Walls.ENABLED = was;
		}
	}

	/** Control: with nothing on the line, the Steward's round opens a wall site on the first piece's spot. */
	//$ gametest_ticks_batch AREA '60' '"qaStewardWallsControl1101"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaStewardWallsControl1101")
	public void qaControlTheFirstPieceOpensOnOpenGround(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			List<BuildSite> open = round(helper, v);
			helper.assertTrue(open.size() == 1, open.size() + " wall sites opened on open ground with one builder, not 1");
			helper.assertTrue(open.get(0).placement().equals(piece.placement()), "the first wall site isn't the first piece: " + box(helper, open.get(0)) + " vs " + piece.box());
			helper.succeed();
		}));
	}

	/**
	 * (a) A finished house (a Well, finished by a builder) stands where the wall line's first piece goes. Expected: the
	 * Steward opens no wall site through it (he skips to a piece on open ground).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaStewardWallsBuilding1101"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaStewardWallsBuilding1101")
	public void qaNoWallPieceThroughAFinishedBuilding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Village v = village(helper);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			ResourceLocation well = AliveWorkplace.id("well");
			BlueprintData.Placement at = new BlueprintData.Placement(Ids.of(level.dimension()),
				new BlockPos(piece.box().minX(), v.hall().getY(), piece.box().minZ()), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(well, at, UUID.randomUUID());
			BoundingBox wellBox = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, well)
				.map(b -> io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(at, b.size())).orElseThrow();
			helper.assertTrue(footprintsMeet(wellBox, piece.box()), "setup: the Well " + wellBox + " isn't on the piece " + piece.box());
			List<BuildSite> open = round(helper, v);
			for (BuildSite site : open) {
				helper.assertTrue(!footprintsMeet(box(helper, site), wellBox), "the Steward opened a " + site.structure() + " wall site at "
					+ box(helper, site) + " through the finished Well at " + wellBox + "; expected the piece skipped");
			}
			helper.assertTrue(open.size() == 1, open.size() + " wall sites opened, not 1 on the open ground beside the Well");
			helper.succeed();
		}));
	}

	/** Lays the Test Folly's stage I out over {@code piece}'s spot: begun for the hall, its stone set outside. */
	private static WonderSite follyOver(GameTestHelper helper, Village v, Walls.Piece piece, boolean finished) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		WonderSite site = manager.begin(FOLLY, v.hall(), v.owner().getUUID(), v.owner().getGameProfile().getName(), 1);
		Leftovers.after(helper, () -> manager.remove(site.id()));
		int cx = (piece.box().minX() + piece.box().maxX()) / 2;
		int cz = (piece.box().minZ() + piece.box().maxZ()) / 2;
		BlockPos origin = new BlockPos(cx - FOLLY_SIZE / 2, v.hall().getY(), cz - FOLLY_SIZE / 2);
		manager.place(site.id(), new BlueprintData.Placement(Ids.of(level.dimension()), origin, Rotation.NONE, Mirror.NONE), origin.offset(-1, 0, -1));
		if (finished) {
			manager.finish(site.id(), 2);
		}
		WonderSite now = manager.site(site.id()).orElseThrow();
		helper.assertTrue(finished ? now.done() : (now.goingUp() && !now.started() && now.parts().isEmpty()),
			"setup: the Wonder isn't " + (finished ? "finished" : "laid out and waiting for its goods"));
		helper.assertTrue(WonderPlans.wonderOver(level, Ids.of(level.dimension()), piece.box()).isPresent(),
			"setup: the Wonder's layout isn't over the piece " + piece.box());
		return now;
	}

	/**
	 * (b) A finished Wonder stands across the wall line. Expected: no wall site opened through it.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaStewardWallsWonderDone1101"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaStewardWallsWonderDone1101")
	public void qaNoWallPieceThroughAFinishedWonder(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			follyOver(helper, v, piece, true);
			List<BuildSite> open = round(helper, v);
			for (BuildSite site : open) {
				helper.assertTrue(WonderPlans.wonderOver(helper.getLevel(), Ids.of(helper.getLevel().dimension()), box(helper, site)).isEmpty(),
					"the Steward opened a " + site.structure() + " wall site at " + box(helper, site) + " through the finished Test Folly; expected the piece skipped");
			}
			helper.assertTrue(open.size() == 1, open.size() + " wall sites opened, not 1 on the open ground beside the Wonder");
			helper.succeed();
		}));
	}

	/**
	 * (c) A Wonder begun and laid out across the wall line, waiting for stage I's goods (no build sites yet). Expected:
	 * its ground is held, no wall site opened there (the control shows the same piece opens with no Wonder).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaStewardWallsWonderWaiting1101"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaStewardWallsWonderWaiting1101")
	public void qaNoWallPieceThroughAWonderWaitingForItsGoods(GameTestHelper helper) {
		Village v = village(helper);
		helper.runAfterDelay(2, () -> withWalls(() -> {
			Walls.Piece piece = first(helper, v);
			follyOver(helper, v, piece, false);
			List<BuildSite> open = round(helper, v);
			for (BuildSite site : open) {
				helper.assertTrue(WonderPlans.wonderOver(helper.getLevel(), Ids.of(helper.getLevel().dimension()), box(helper, site)).isEmpty(),
					"the Steward opened a " + site.structure() + " wall site at " + box(helper, site) + " where the waiting Test Folly's stage I will stand; expected the piece skipped");
			}
			helper.assertTrue(open.size() == 1, open.size() + " wall sites opened, not 1 on the open ground beside the Wonder");
			helper.succeed();
		}));
	}
}

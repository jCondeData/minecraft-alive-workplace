package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for ROADMAP 27.20, old houses found and measured, written from the spec: the 85% share from both sides, the
 * abandoned blocks (cobwebs, mossy, cracked) counted as village blocks, the block entities an old house may hold and the
 * ones it may not, the 2000-block and 20×16×20 limits from both sides, a bed in a finished builder build, the ledger's
 * section edges and a block a player broke, and the desk's counts (several, zero, renew off). The village is OldHousesGameTests' (a hall on flat grass, a Homes zone east of it with "renew old houses" on); each
 * test runs alone in its batch.
 */
public class QaOldHousesGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	private static final ResourceLocation SMALL_HOUSE = ResourceLocation.withDefaultNamespace("village/plains/houses/plains_small_house_1");
	private static final int SMALL_HOUSE_BLOCKS = 153;

	record Village(ServerLevel level, BlockPos hall, ServerPlayer owner) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	/** OldHousesGameTests' village, cleared up to the area's top (the size tests build 17 high). */
	static Village village(GameTestHelper helper) {
		Leftovers.clear(helper);
		// B530: a hall an earlier batch left nearby is nearer the far houses' corners than this one, so they're its village
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
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
				for (int y = 2; y < 29; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 0; z <= 29; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		CityPlan plan = CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells);
		entity.setPlan(plan.editZone(0, "homes", "Homes 1", "", true));
		OldHouses.forget(level, hall);
		return new Village(level, hall, owner);
	}

	static void surveyThen(GameTestHelper helper, Village v, Consumer<List<OldHouses.House>> check) {
		helper.assertTrue(OldHouses.request(v.level(), v.hall()).isPresent(), "no survey: the renew zone isn't seen");
		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(v.level(), v.hall()).isPresent(), "the survey hasn't finished");
			check.accept(OldHouses.result(v.level(), v.hall()).get());
		});
	}

	/** A floor of {@code block} at y = 2, {@code w} along x and {@code d} along z from {@code corner}. */
	private static void floor(GameTestHelper helper, BlockPos corner, int w, int d, Block block) {
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				helper.setBlock(corner.offset(x, 0, z), block);
			}
		}
	}

	/** A red bed, foot at {@code foot}, head toward {@code facing}: a home point of interest. */
	private static void bed(GameTestHelper helper, BlockPos foot, Direction facing) {
		BlockState state = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, facing);
		helper.setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.relative(facing), state.setValue(BedBlock.PART, BedPart.HEAD));
	}

	/** The house whose box holds the helper position {@code at}, or null. */
	private static OldHouses.House at(GameTestHelper helper, List<OldHouses.House> houses, BlockPos at) {
		BlockPos abs = helper.absolutePos(at);
		return houses.stream().filter(h -> h.box().isInside(abs)).findFirst().orElse(null);
	}

	/** For a failure message: the zone and nearest hall of each corner of {@code house}'s box, the two things OUTSIDE_ZONE checks. */
	static String why(Village v, OldHouses.House house) {
		BoundingBox b = house.box();
		CityPlan plan = v.entity().plan();
		StringBuilder out = new StringBuilder(" (hall " + v.hall().toShortString() + "; corners:");
		for (BlockPos c : List.of(new BlockPos(b.minX(), b.minY(), b.minZ()), new BlockPos(b.maxX(), b.minY(), b.minZ()),
			new BlockPos(b.minX(), b.minY(), b.maxZ()), new BlockPos(b.maxX(), b.minY(), b.maxZ()))) {
			out.append(' ').append(c.toShortString()).append(" zone ").append(plan.zoneAt(v.hall(), c).map(z -> z.name() + (z.renew() ? " renew" : "")).orElse("none"))
				.append(" nearest hall ").append(io.github.jcondedata.aliveworkplace.hall.VillageHalls.nearest(v.level(), c).map(BlockPos::toShortString).orElse("none")).append(';');
		}
		return out.append(')').toString();
	}

	private static BoundingBox box(GameTestHelper helper, BlockPos from, BlockPos to) {
		return BoundingBox.fromCorners(helper.absolutePos(from), helper.absolutePos(to));
	}

	/**
	 * The 85% share from both sides. A: 20 blocks, 17 of them village blocks (oak planks and a bed), 3 deepslate bricks:
	 * exactly 85%, so renewable. B: 100 blocks, 84 village blocks: 84%, so not an old village house.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaOldHouseShare"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaOldHouseShare")
	public void exactlyEightyFivePercentCountsAndJustUnderDoesNot(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos a = new BlockPos(12, 2, 2);
		floor(helper, a, 6, 3, Blocks.OAK_PLANKS);
		for (int i = 0; i < 3; i++) {
			helper.setBlock(a.offset(i, 0, 2), Blocks.DEEPSLATE_BRICKS);
		}
		bed(helper, a.offset(1, 1, 1), Direction.EAST);
		BlockPos b = new BlockPos(12, 2, 14);
		floor(helper, b, 14, 7, Blocks.OAK_PLANKS);
		for (int i = 0; i < 16; i++) {
			helper.setBlock(b.offset(i % 14, 0, 6 - i / 14), Blocks.DEEPSLATE_BRICKS);
		}
		bed(helper, b.offset(1, 1, 1), Direction.EAST);
		surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 2, "expected the two houses, found " + houses);
			OldHouses.House exact = at(helper, houses, a);
			helper.assertTrue(exact != null && exact.blocks() == 20 && exact.villageBlocks() == 17,
				"house A (20 blocks, 17 village) measured as " + exact);
			helper.assertTrue(exact.renewable(), "a house of exactly 85% village blocks counted as " + exact.verdict());
			OldHouses.House under = at(helper, houses, b);
			helper.assertTrue(under != null && under.blocks() == 100 && under.villageBlocks() == 84,
				"house B (100 blocks, 84 village) measured as " + under);
			helper.assertTrue(under.verdict() == OldHouses.Verdict.NOT_VILLAGE, "a house of 84% village blocks counted as " + under.verdict());
		});
	}

	/** An abandoned house: a floor of mossy cobblestone, cracked and mossy stone bricks, cobwebs on it: all village blocks. */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseAbandoned"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseAbandoned")
	public void cobwebsMossyAndCrackedBlocksAreVillageBlocks(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos c = new BlockPos(12, 2, 4);
		Block[] rows = {Blocks.MOSSY_COBBLESTONE, Blocks.CRACKED_STONE_BRICKS, Blocks.MOSSY_STONE_BRICKS};
		for (int z = 0; z < 3; z++) {
			for (int x = 0; x < 3; x++) {
				helper.setBlock(c.offset(x, 0, z), rows[z]);
			}
		}
		helper.setBlock(c.offset(0, 1, 2), Blocks.COBWEB);
		helper.setBlock(c.offset(2, 1, 2), Blocks.COBWEB);
		helper.setBlock(c.offset(2, 1, 0), Blocks.COBWEB);
		bed(helper, c.offset(1, 1, 0), Direction.SOUTH);
		surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "expected the one house, found " + houses);
			OldHouses.House house = houses.get(0);
			helper.assertTrue(house.blocks() == 14, "measured " + house.blocks() + " blocks, the house has 9 floor, 3 cobwebs and a bed");
			helper.assertTrue(house.villageBlocks() == 14, "only " + house.villageBlocks() + " of 14 counted as village blocks");
			helper.assertTrue(house.renewable(), "an abandoned house counted as " + house.verdict());
			helper.assertTrue(house.box().equals(box(helper, c, c.offset(2, 1, 2))), "measured " + house.box());
		});
	}

	/**
	 * The block entities an old house may hold: a bed, bell, sign, banner, campfire and job blocks (lectern, blast furnace,
	 * smoker, brewing stand). Still renewable, and one house, not one per bed and workstation.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseAllowed"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseAllowed")
	public void bedsBellsSignsBannersCampfiresAndJobBlocksAreAllowed(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos c = new BlockPos(12, 2, 4);
		floor(helper, c, 8, 3, Blocks.OAK_PLANKS);
		bed(helper, c.offset(0, 1, 0), Direction.SOUTH);
		Block[] front = {Blocks.BELL, Blocks.OAK_SIGN, Blocks.WHITE_BANNER, Blocks.CAMPFIRE};
		Block[] back = {Blocks.LECTERN, Blocks.BLAST_FURNACE, Blocks.SMOKER, Blocks.BREWING_STAND};
		for (int i = 0; i < 4; i++) {
			helper.setBlock(c.offset(2 + i, 1, 0), front[i]);
			helper.setBlock(c.offset(2 + i, 1, 2), back[i]);
		}
		for (int i = 0; i < 4; i++) {
			helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(c.offset(2 + i, 1, 0))) != null
				&& helper.getLevel().getBlockEntity(helper.absolutePos(c.offset(2 + i, 1, 2))) != null, "setup: a block without its block entity");
		}
		surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "one house with a bed and four job blocks listed as " + houses);
			OldHouses.House house = houses.get(0);
			helper.assertTrue(house.blocks() == 34, "measured " + house.blocks() + " blocks, the house has 34");
			helper.assertTrue(house.renewable(), "a house with a bell, sign, banner, campfire and job blocks counted as " + house.verdict());
		});
	}

	/** A furnace (no villager's job block), a hopper and an ender chest: each house holding one is kept. */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseContainers"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseContainers")
	public void aFurnaceAHopperOrAnEnderChestKeepsTheHouse(GameTestHelper helper) {
		Village v = village(helper);
		Block[] kept = {Blocks.FURNACE, Blocks.HOPPER, Blocks.ENDER_CHEST};
		BlockPos[] corners = {new BlockPos(12, 2, 2), new BlockPos(12, 2, 10), new BlockPos(12, 2, 18)};
		for (int i = 0; i < 3; i++) {
			floor(helper, corners[i], 5, 3, Blocks.OAK_PLANKS);
			bed(helper, corners[i].offset(0, 1, 0), Direction.SOUTH);
			helper.setBlock(corners[i].offset(3, 1, 1), kept[i]);
		}
		surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 3, "expected the three houses, found " + houses);
			for (int i = 0; i < 3; i++) {
				OldHouses.House house = at(helper, houses, corners[i]);
				helper.assertTrue(house != null && house.blocks() == 18, "the house with a " + kept[i] + " measured as " + house);
				helper.assertTrue(house.verdict() == OldHouses.Verdict.CONTAINER, "a house with a " + kept[i] + " counted as " + house.verdict());
			}
		});
	}

	/**
	 * At most 2000 blocks, from both sides: a solid block of oak planks 20×10×10 (2000 blocks, 20 wide) with a smithing
	 * table in it is a house; the same with one block more on top is not.
	 */
	//$ gametest_ticks_batch AREA '800' '"qaOldHouse2000"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qaOldHouse2000")
	public void twoThousandBlocksIsAHouseAndOneMoreIsNot(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos a = new BlockPos(8, 2, 1);
		BlockPos b = new BlockPos(8, 2, 16);
		for (BlockPos corner : List.of(a, b)) {
			for (int y = 0; y < 10; y++) {
				floor(helper, corner.above(y), 20, 10, Blocks.OAK_PLANKS);
			}
			helper.setBlock(corner.offset(0, 9, 0), Blocks.SMITHING_TABLE);
		}
		helper.setBlock(b.offset(5, 10, 5), Blocks.OAK_PLANKS);
		surveyThen(helper, v, houses -> {
			OldHouses.House at2000 = at(helper, houses, a);
			helper.assertTrue(at2000 != null && at2000.blocks() == OldHouses.MAX_BLOCKS, "the 2000-block house measured as " + at2000);
			helper.assertTrue(at2000.box().equals(box(helper, a, a.offset(19, 9, 9))), "measured " + at2000.box());
			helper.assertTrue(at2000.renewable(), "a 2000-block house counted as " + at2000.verdict() + why(v, at2000));
			OldHouses.House at2001 = at(helper, houses, b);
			helper.assertTrue(at2001 == null || at2001.verdict() == OldHouses.Verdict.TOO_BIG, "a 2001-block house counted as " + at2001);
			helper.assertTrue(houses.size() == 1, "expected only the 2000-block house listed, found " + houses);
		});
	}

	/**
	 * At most 20×16×20, from both sides: lines of oak planks with a smithing table at one end, 20 and 21 long along x, 20
	 * and 21 along z, and columns 16 and 17 high. The 20s and the 16 are houses; the 21s and the 17 are not.
	 */
	//$ gametest_ticks_batch AREA '400' '"qaOldHouseSize"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaOldHouseSize")
	public void twentyBySixteenByTwentyIsTheLargestBox(GameTestHelper helper) {
		Village v = village(helper);
		for (int x = 0; x < 21; x++) {
			if (x < 20) {
				helper.setBlock(new BlockPos(6 + x, 2, 2), x == 0 ? Blocks.SMITHING_TABLE : Blocks.OAK_PLANKS);
			}
			helper.setBlock(new BlockPos(6 + x, 2, 5), x == 0 ? Blocks.SMITHING_TABLE : Blocks.OAK_PLANKS);
		}
		for (int z = 0; z < 21; z++) {
			if (z < 20) {
				helper.setBlock(new BlockPos(28, 2, 8 + z), z == 0 ? Blocks.SMITHING_TABLE : Blocks.OAK_PLANKS);
			}
			helper.setBlock(new BlockPos(26, 2, 8 + z), z == 0 ? Blocks.SMITHING_TABLE : Blocks.OAK_PLANKS);
		}
		for (int y = 0; y < 17; y++) {
			if (y < 16) {
				helper.setBlock(new BlockPos(10, 2 + y, 15), y == 0 ? Blocks.SMITHING_TABLE : Blocks.OAK_PLANKS);
			}
			helper.setBlock(new BlockPos(14, 2 + y, 15), y == 0 ? Blocks.SMITHING_TABLE : Blocks.OAK_PLANKS);
		}
		surveyThen(helper, v, houses -> {
			BoundingBox x20 = box(helper, new BlockPos(6, 2, 2), new BlockPos(25, 2, 2));
			BoundingBox z20 = box(helper, new BlockPos(28, 2, 8), new BlockPos(28, 2, 27));
			BoundingBox y16 = box(helper, new BlockPos(10, 2, 15), new BlockPos(10, 17, 15));
			for (BoundingBox want : List.of(x20, z20, y16)) {
				helper.assertTrue(houses.stream().anyMatch(h -> h.box().equals(want) && h.renewable()),
					"no renewable house measured " + want + " among " + houses);
			}
			for (BlockPos over : List.of(new BlockPos(6, 2, 5), new BlockPos(26, 2, 8), new BlockPos(14, 2, 15))) {
				OldHouses.House house = at(helper, houses, over);
				helper.assertTrue(house == null || house.verdict() == OldHouses.Verdict.TOO_BIG, "a structure over 20×16×20 counted as " + house);
			}
			helper.assertTrue(houses.size() == 3, "expected exactly the three houses within the limits, found " + houses);
		});
	}

	/**
	 * A bed in a finished builder build is not an old house: two plains houses, one recorded as a finished build. Only the
	 * other is listed.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseFinished"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseFinished")
	public void aHouseABuilderBuiltIsNotAnOldHouse(GameTestHelper helper) {
		Village v = village(helper);
		OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		BlockPos other = new BlockPos(12, 2, 17);
		BoundingBox otherBox = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, other);
		BuildSiteManager sites = BuildSiteManager.get(v.level());
		BlueprintData.Placement placement = new BlueprintData.Placement(v.level().dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		sites.recordFinished(SMALL_HOUSE, placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
		surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "expected only the house no builder built, found " + houses);
			helper.assertTrue(houses.get(0).box().equals(otherBox), "listed " + houses.get(0).box() + ", the old house fills " + otherBox);
		});
	}

	/**
	 * Placed leaves (persistent, a hedge on the house) are built blocks, not a tree: the leaves and the log under them are
	 * part of the house. 15 planks, a bed, a log and 2 leaves: 20 blocks.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseHedge"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseHedge")
	public void placedLeavesAndTheirLogArePartOfTheHouse(GameTestHelper helper) {
		Village v = village(helper);
		BlockPos c = new BlockPos(12, 2, 4);
		floor(helper, c, 5, 3, Blocks.OAK_PLANKS);
		bed(helper, c.offset(0, 1, 0), Direction.SOUTH);
		BlockState placed = Blocks.OAK_LEAVES.defaultBlockState().setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true);
		helper.setBlock(c.offset(3, 1, 1), Blocks.OAK_LOG);
		helper.setBlock(c.offset(3, 2, 1), placed);
		helper.setBlock(c.offset(4, 1, 1), placed);
		surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "expected the one house, found " + houses);
			OldHouses.House house = houses.get(0);
			helper.assertTrue(house.blocks() == 20, "measured " + house.blocks() + " blocks, the house has 20 with its log and placed leaves");
			helper.assertTrue(house.box().equals(box(helper, c, c.offset(4, 2, 2))), "measured " + house.box());
			helper.assertTrue(house.renewable(), "a house with a hedge counted as " + house.verdict());
		});
	}

	/** The ledger's edge:a block a player put down 17 above the house's roof (another section) leaves it renewable. */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseLedgerFar"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseLedgerFar")
	public void aPlayerBlockInASectionTheHouseDoesNotTouchLeavesItRenewable(GameTestHelper helper) {
		Village v = village(helper);
		BoundingBox box = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		BlockPos far = new BlockPos(box.minX() + 3, box.maxY() + 17, box.minZ() + 3);
		helper.assertTrue(StewardSafetyGameTests.place(helper, v.owner(), far, Blocks.STONE), "setup: the player's block wasn't placed");
		helper.assertTrue(PlayerBuilt.get(v.level()).marked(far), "setup: the player's block left its section unmarked");
		surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "expected the one house, found " + houses);
			helper.assertTrue(houses.get(0).renewable(), "a player's block in another section kept the house: " + houses.get(0).verdict());
		});
	}

	/** A player broke one of the house's blocks (the ledger marks breaks too): kept. */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseBroken"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseBroken")
	public void aHouseAPlayerBrokeABlockOfIsKept(GameTestHelper helper) {
		Village v = village(helper);
		BoundingBox box = OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		BlockPos broken = null;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.maxY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			if (!v.level().getBlockState(p).isAir() && !v.level().getBlockState(p).is(net.minecraft.tags.BlockTags.BEDS)) {
				broken = p.immutable();
				break;
			}
		}
		helper.assertTrue(broken != null, "setup: no roof block to break");
		helper.assertTrue(v.owner().gameMode.destroyBlock(broken), "setup: the player couldn't break the block");
		BlockPos at = broken;
		surveyThen(helper, v, houses -> {
			helper.assertTrue(houses.size() == 1, "expected the one house, found " + houses);
			helper.assertTrue(houses.get(0).verdict() == OldHouses.Verdict.PLAYER_BUILT,
				"a house a player broke a block of (" + at + ") counted as " + houses.get(0).verdict());
		});
	}

	private static ChoiceMenu desk(GameTestHelper helper, Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
		menu.press(VillageHallScreen.ADVICE, v.owner());
		return menu;
	}

	private static void appoint(GameTestHelper helper, Village v) {
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(v.level(), v.owner(), plan, v.hall());
		helper.assertTrue(Stewards.appoint(v.owner(), steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
	}

	/** The desk counts every house and the renewable ones: a plains house and one with a chest, "Old houses: 2, 1 can be renewed". */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseDeskTwo"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseDeskTwo")
	public void theDeskCountsTwoHousesOneRenewable(GameTestHelper helper) {
		Village v = village(helper);
		OldHousesGameTests.placeVanilla(helper, SMALL_HOUSE, HOUSE);
		BlockPos kept = new BlockPos(12, 2, 20);
		floor(helper, kept, 5, 3, Blocks.OAK_PLANKS);
		bed(helper, kept.offset(0, 1, 0), Direction.SOUTH);
		helper.setBlock(kept.offset(3, 1, 1), Blocks.CHEST);
		appoint(helper, v);
		desk(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(v.level(), v.hall()).isPresent(), "the survey hasn't finished");
			ChoiceMenu menu = desk(helper, v);
			Component name = menu.icon(StewardDeskPage.OLD_HOUSES).getHoverName();
			helper.assertTrue(key(name).equals("screen.aliveworkplace.desk.old_houses_count") && args(name).equals("[2, 1]"), "the desk says " + name);
			helper.assertTrue(name.getString().equals("Old houses: 2, 1 can be renewed"), "the desk reads \"" + name.getString() + "\"");
			String lore = String.valueOf(menu.icon(StewardDeskPage.OLD_HOUSES).get(DataComponents.LORE));
			helper.assertTrue(lore.contains("verdict.renewable") && lore.contains("verdict.container"), "the desk doesn't list both houses: " + lore);
		});
	}

	/** A renew zone with no old house: "Old houses: 0, 0 can be renewed" and the none hint; the renew switch off: no line at all. */
	//$ gametest_ticks_batch AREA '200' '"qaOldHouseDeskNone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaOldHouseDeskNone")
	public void theDeskSaysNoneThenNothingWithRenewOff(GameTestHelper helper) {
		Village v = village(helper);
		appoint(helper, v);
		desk(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(OldHouses.result(v.level(), v.hall()).isPresent(), "the survey hasn't finished");
			ChoiceMenu menu = desk(helper, v);
			Component name = menu.icon(StewardDeskPage.OLD_HOUSES).getHoverName();
			helper.assertTrue(key(name).equals("screen.aliveworkplace.desk.old_houses_count") && args(name).equals("[0, 0]"), "the desk says " + name);
			String lore = String.valueOf(menu.icon(StewardDeskPage.OLD_HOUSES).get(DataComponents.LORE));
			helper.assertTrue(lore.contains("screen.aliveworkplace.desk.old_houses.none_hint"), "no none hint: " + lore);
			v.entity().setPlan(v.entity().plan().editZone(0, "homes", "Homes 1", "", false));
			ChoiceMenu off = desk(helper, v);
			Component offName = off.icon(StewardDeskPage.OLD_HOUSES).getHoverName();
			helper.assertTrue(!key(offName).startsWith("screen.aliveworkplace.desk.old_houses"), "with renew off the desk still says " + offName);
		});
	}

	private static String key(Component text) {
		return text.getContents() instanceof TranslatableContents t ? t.getKey() : text.getString();
	}

	private static String args(Component text) {
		return text.getContents() instanceof TranslatableContents t ? java.util.Arrays.toString(t.getArgs()) : "";
	}
}

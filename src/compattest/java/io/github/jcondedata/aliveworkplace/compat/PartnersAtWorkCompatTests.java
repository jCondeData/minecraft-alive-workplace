package io.github.jcondedata.aliveworkplace.compat;

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 28.4, partners at work on building and the land: each show starts when the worker reaches its moment in real
 * work (the builder fetching and placing, the porter's haul, the carpenter's craft, the farmer tending and tilling, the
 * lumberjack chopping and replanting, the orchard keeper picking), played by a pastured partner of the show's type.
 */
public class PartnersAtWorkCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	/** A pastured partner of {@code species} at {@code pastureAt}, and a player near so shows may run. */
	private static void partner(GameTestHelper helper, BlockPos pastureAt, String species) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos by = helper.absolutePos(new BlockPos(1, 2, 8)); // a mock player starts at the world spawn: bring it to the test
		player.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
		BlockPos pasture = PastureCompatTests.pasture(helper, pastureAt);
		PastureCompatTests.pastured(helper, pasture, player, species, Direction.NORTH);
		// The partner's entity appears a tick or so later: see pokemon(). The player leaves with the test.
		PartnerShowsCompatTests.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
	}

	private static PokemonEntity pokemon(GameTestHelper helper, String species) {
		List<PokemonEntity> found = helper.getLevel().getEntitiesOfClass(PokemonEntity.class, helper.getBounds().inflate(4),
			e -> e.getPokemon().getSpecies().getName().equalsIgnoreCase(species));
		helper.assertTrue(found.size() == 1, "pastured " + species + ": " + found.size());
		return found.get(0);
	}

	/** Every show the worker's cues start, as they start; carried items seen on the partner, by show. */
	private static Set<ResourceLocation> watch(GameTestHelper helper, Villager worker, String species, Set<Item> carried) {
		Set<ResourceLocation> started = new HashSet<>();
		helper.onEachTick(() -> {
			ResourceLocation last = PartnerShows.lastShow(worker);
			if (last != null) {
				started.add(last);
			}
			if (helper.getTick() > 5) {
				PartnerShows.carrying(pokemon(helper, species)).ifPresent(d -> carried.add(d.getSlot(0).get().getItem()));
			}
		});
		return started;
	}

	private static ResourceLocation show(String name) {
		return AliveWorkplace.id(name);
	}

	private static ResourceLocation row(GameTestHelper helper, String name, BlockState block, int length) {
		Map<BlockPos, BlockState> design = new java.util.HashMap<>();
		for (int x = 0; x < length; x++) {
			design.put(new BlockPos(x, 0, 0), block);
		}
		return CompatGameTests.blueprintFrom(helper, name, design, Map.of());
	}

	// --- the builder -----------------------------------------------------------------------------

	/** A Machop by the bench punches each block home as it's placed; it carries no stone (wood only). */
	//$ gametest_ticks_batch AREA '1200' '"partners_place"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "partners_place")
	public void aMachopPunchesTheBuildersBlocksHome(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		ResourceLocation wall = row(helper, "partner_place", Blocks.COBBLESTONE.defaultBlockState(), 4);
		CompatGameTests.Setup s = CompatGameTests.setup(helper, wall, new ItemStack(Items.COBBLESTONE, 4));
		partner(helper, new BlockPos(13, 2, 2), "machop");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, s.villager(), "machop", carried);
		helper.succeedWhen(() -> {
			CompatGameTests.assertBuilt(helper, s);
			helper.assertTrue(started.contains(show("builder_punches_blocks_home")), "the Machop never punched a block home: " + started
				+ " (" + PartnerShows.lastRefusal() + ")");
			helper.assertFalse(started.contains(show("builder_carries_planks")), "the Machop shouldered cobblestone with the wood show");
			helper.assertTrue(carried.isEmpty(), "the Machop carried " + carried);
		});
	}

	/** A Geodude carries the stone the builder fetches. */
	//$ gametest_ticks_batch AREA '1200' '"partners_rock"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "partners_rock")
	public void aGeodudeCarriesTheBuildersStone(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		ResourceLocation wall = row(helper, "partner_rock", Blocks.STONE_BRICKS.defaultBlockState(), 4);
		CompatGameTests.Setup s = CompatGameTests.setup(helper, wall, new ItemStack(Items.STONE_BRICKS, 4));
		partner(helper, new BlockPos(13, 2, 2), "geodude");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, s.villager(), "geodude", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("builder_carries_stone")), "no stone show: " + started + " (" + PartnerShows.lastRefusal() + ")");
			helper.assertTrue(carried.contains(Items.STONE_BRICKS), "the Geodude carried " + carried + ", not the stone bricks");
			CompatGameTests.assertBuilt(helper, s);
		});
	}

	/** A Magnemite carries the iron parts (bars here) the builder fetches. */
	//$ gametest_ticks_batch AREA '1200' '"partners_steel"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "partners_steel")
	public void aMagnemiteCarriesTheBuildersIronBars(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		ResourceLocation bars = row(helper, "partner_steel", Blocks.IRON_BARS.defaultBlockState(), 3);
		CompatGameTests.Setup s = CompatGameTests.setup(helper, bars, new ItemStack(Items.IRON_BARS, 3));
		partner(helper, new BlockPos(13, 2, 2), "magnemite");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, s.villager(), "magnemite", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("builder_carries_iron_parts")), "no iron show: " + started + " (" + PartnerShows.lastRefusal() + ")");
			helper.assertTrue(carried.contains(Items.IRON_BARS), "the Magnemite carried " + carried + ", not the iron bars");
		});
	}

	// --- the porter ------------------------------------------------------------------------------

	/** A Machop by the storehouse hauls a barrel along when the porter collects a worker's goods. */
	//$ gametest_ticks_batch AREA '1600' '"partners_haul"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_haul")
	public void aMachopHaulsABarrelOnThePortersRound(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.MINERS_BENCH);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container minersChest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		minersChest.setItem(0, new ItemStack(Items.RAW_IRON, 20));
		minersChest.setItem(1, new ItemStack(Items.COAL, 10));
		Villager miner = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		io.github.jcondedata.aliveworkplace.mine.Miners.employ(helper.getLevel(), miner, helper.absolutePos(new BlockPos(2, 2, 2)));
		BlockPos storehouse = new BlockPos(13, 2, 13);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(13, 2, 11), Blocks.CHEST);
		Villager porter = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Porters.employ(helper.getLevel(), porter, helper.absolutePos(storehouse));
		partner(helper, new BlockPos(9, 2, 14), "machop");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, porter, "machop", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("porter_carries_a_barrel")), "no haul show: " + started + " (" + PartnerShows.lastRefusal() + ")");
			helper.assertTrue(carried.contains(Items.BARREL), "the Machop carried " + carried + ", not a barrel");
		});
	}

	// --- the carpenter ---------------------------------------------------------------------------

	/** A Machop by the carpenter's bench holds the work while the carpenter makes the door the builder needs. */
	//$ gametest_ticks_batch AREA '2400' '"partners_craft"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "partners_craft")
	public void aMachopHoldsTheCarpentersWork(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		Village.RADIUS = 48;
		PartnerShowsCompatTests.after(helper, () -> Village.RADIUS = 0);
		ResourceLocation door = CompatGameTests.blueprintFrom(helper, "partner_door", Map.of(
			BlockPos.ZERO, Blocks.OAK_DOOR.defaultBlockState(),
			BlockPos.ZERO.above(), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
				net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER)), Map.of());
		CompatGameTests.Setup s = CompatGameTests.setup(helper, door, new ItemStack(Items.OAK_PLANKS, 12));
		BlockPos bench = new BlockPos(14, 2, 2);
		helper.setBlock(bench, ModBlocks.CARPENTERS_BENCH);
		Villager carpenter = helper.spawn(EntityType.VILLAGER, new BlockPos(14, 2, 3));
		Jobs.employ(helper.getLevel(), carpenter, helper.absolutePos(bench), ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
		partner(helper, new BlockPos(13, 2, 9), "machop");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, carpenter, "machop", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("crafter_holds_the_work")), "no craft show: " + started + " (" + PartnerShows.lastRefusal() + ")");
			helper.assertTrue(carried.contains(Items.OAK_PLANKS), "the Machop held " + carried + ", not the planks the door is made of");
			CompatGameTests.assertBuilt(helper, s);
		});
	}

	// --- the farmer ------------------------------------------------------------------------------

	private static Villager farmer(GameTestHelper helper, BlockPos corner1, BlockPos corner2, ItemStack... chest) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		helper.setBlock(new BlockPos(2, 2, 2), Blocks.COMPOSTER);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container c = helper.getBlockEntity(new BlockPos(2, 2, 4));
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(new BlockPos(2, 2, 2)), PoiTypes.FARMER, VillagerProfession.FARMER);
		Fields.start(level, villager, BoundingBox.fromCorners(helper.absolutePos(corner1), helper.absolutePos(corner2)));
		return villager;
	}

	/** A Squirtle waters the patch the farmer is harvesting: the farmland round it ends fully moist (7). */
	//$ gametest_ticks_batch AREA '1600' '"partners_tend"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_tend")
	public void aSquirtleWatersThePatchTheFarmerTends(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		// Dry farmland, no water near: only the show can wet it.
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 10))) {
			helper.setBlock(p, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
			helper.setBlock(p.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
		}
		Villager villager = farmer(helper, new BlockPos(8, 1, 8), new BlockPos(10, 1, 10));
		partner(helper, new BlockPos(13, 2, 3), "squirtle");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, villager, "squirtle", carried);
		// The most farmland seen fully moist at once: with no water near, it dries again a step at a time afterwards.
		int[] mostWet = {0};
		helper.onEachTick(() -> {
			int wet = 0;
			for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 10))) {
				BlockState state = helper.getBlockState(p);
				if (state.is(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) == FarmBlock.MAX_MOISTURE) {
					wet++;
				}
			}
			mostWet[0] = Math.max(mostWet[0], wet);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("farmer_water_waters_the_field")), "no watering show: " + started + " (" + PartnerShows.lastRefusal() + ")");
			helper.assertTrue(PartnerShows.running(helper.getLevel()) == 0, "the show is still on");
			// The 3x3 round the crop being tended: 4 to 9 of this 3x3 field, by where that crop is.
			helper.assertTrue(mostWet[0] >= 4, "at most " + mostWet[0] + " farmland at moisture 7 from the watering show");
		});
	}

	/** A Diglett walks the furrow the farmer is tilling. */
	//$ gametest_ticks_batch AREA '1600' '"partners_till"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_till")
	public void aDiglettWalksTheFurrowTheFarmerTills(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		for (BlockPos p : BlockPos.betweenClosed(new BlockPos(8, 1, 8), new BlockPos(10, 1, 10))) {
			helper.setBlock(p, Blocks.GRASS_BLOCK);
		}
		helper.setBlock(new BlockPos(11, 1, 9), Blocks.WATER);
		Villager villager = farmer(helper, new BlockPos(8, 1, 8), new BlockPos(10, 1, 10), new ItemStack(Items.STONE_HOE), new ItemStack(Items.CARROT, 16));
		partner(helper, new BlockPos(13, 2, 3), "diglett");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, villager, "diglett", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("farmer_ground_walks_the_furrow")), "no furrow show: " + started + " (" + PartnerShows.lastRefusal() + ")");
			helper.assertBlockPresent(Blocks.FARMLAND, new BlockPos(9, 1, 9));
		});
	}

	// --- the lumberjack --------------------------------------------------------------------------

	private static Villager lumberjack(GameTestHelper helper, ItemStack... chest) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.CHOPPING_BLOCK);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		Container c = helper.getBlockEntity(new BlockPos(2, 2, 4));
		for (int i = 0; i < chest.length; i++) {
			c.setItem(i, chest[i]);
		}
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, villager, helper.absolutePos(new BlockPos(2, 2, 2)), ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK);
		return villager;
	}

	/** A small oak, built by hand so it fits under the area's 8 blocks: 4 logs and a crown of natural leaves. */
	private static void smallOak(GameTestHelper helper, BlockPos base) {
		helper.setBlock(base.below(), Blocks.GRASS_BLOCK);
		BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false).setValue(LeavesBlock.DISTANCE, 1);
		for (int y = 3; y <= 5; y++) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					if (dx != 0 || dz != 0 || y == 5) {
						helper.setBlock(base.offset(dx, y, dz), leaves);
					}
				}
			}
		}
		for (int y = 0; y < 5; y++) {
			helper.setBlock(base.above(y), Blocks.OAK_LOG);
		}
	}

	/** A Machop lends its fists at the trunk when the lumberjack starts chopping. */
	//$ gametest_ticks_batch AREA '1600' '"partners_chop"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_chop")
	public void aMachopPunchesTheTrunkTheLumberjackChops(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		smallOak(helper, new BlockPos(11, 2, 11));
		Villager villager = lumberjack(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.OAK_SAPLING));
		partner(helper, new BlockPos(13, 2, 3), "machop");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, villager, "machop", carried);
		helper.succeedWhen(() -> {
			helper.assertTrue(started.contains(show("lumberjack_fighting_chops")), "no chopping show: " + started + " (" + PartnerShows.lastRefusal() + ")");
			helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(11, 3, 11));
		});
	}

	/** A Bulbasaur brings the sapling to the stump and plants it with the lumberjack. */
	//$ gametest_ticks_batch AREA '1600' '"partners_replant"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_replant")
	public void aBulbasaurBringsTheSaplingToTheStump(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		smallOak(helper, new BlockPos(11, 2, 11));
		Villager villager = lumberjack(helper, new ItemStack(Items.STONE_AXE), new ItemStack(Items.OAK_SAPLING));
		partner(helper, new BlockPos(13, 2, 3), "bulbasaur");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, villager, "bulbasaur", carried);
		helper.succeedWhen(() -> {
			helper.assertBlockPresent(Blocks.OAK_SAPLING, new BlockPos(11, 2, 11));
			helper.assertTrue(started.contains(show("lumberjack_partner_plants_the_sapling")), "no planting show: " + started + " (" + PartnerShows.lastRefusal() + ")");
			helper.assertTrue(carried.contains(Items.OAK_SAPLING), "the Bulbasaur carried " + carried + ", not the sapling");
		});
	}

	// --- the orchard keeper ----------------------------------------------------------------------

	/** A Pidgey flutters through the bush the orchard keeper picks. */
	//$ gametest_ticks_batch AREA '1600' '"partners_pick"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "partners_pick")
	public void aPidgeyFluttersThroughTheBushBeingPicked(GameTestHelper helper) {
		PartnerShowsCompatTests.showsOn(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.FRUIT_BASKET);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.CHEST);
		for (int x = 9; x <= 11; x++) {
			helper.setBlock(new BlockPos(x, 1, 8), Blocks.GRASS_BLOCK);
			helper.setBlock(new BlockPos(x, 2, 8), Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3));
		}
		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(level, keeper, helper.absolutePos(new BlockPos(2, 2, 2)), ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER);
		partner(helper, new BlockPos(13, 2, 3), "pidgey");
		Set<Item> carried = new HashSet<>();
		Set<ResourceLocation> started = watch(helper, keeper, "pidgey", carried);
		helper.succeedWhen(() -> helper.assertTrue(started.contains(show("orchard_partner_flutters_through_the_tree")),
			"no orchard show: " + started + " (" + PartnerShows.lastRefusal() + ")"));
	}
}

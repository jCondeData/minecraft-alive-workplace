package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Fewer job blocks (ROADMAP 21.1a, the owner's plan): each job starts at its block when the player sneak-right-clicks a
 * villager standing by it with the job's item; vanilla jobs still take their blocks by themselves; the old job blocks
 * still work where they're placed.
 */
public class StationsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos STANDING = new BlockPos(4, 2, 4);

	/** The owner's plan, row by row: the block, the item handed over, the job it gives (Cobblemon's rows: CobblemonCompatTests). */
	private record Row(Block block, Item item, VillagerProfession job) {
	}

	private static List<Row> plan() {
		return List.of(
			new Row(Blocks.COMPOSTER, Items.WHEAT, VillagerProfession.FARMER),
			new Row(Blocks.COMPOSTER, Items.SWEET_BERRIES, ModVillagers.ORCHARD_KEEPER),
			new Row(Blocks.COMPOSTER, Items.POPPY, ModVillagers.FLORIST),
			new Row(Blocks.COMPOSTER, Items.BONE_MEAL, ModVillagers.COMPOSTER),
			new Row(Blocks.BLAST_FURNACE, Items.IRON_PICKAXE, ModVillagers.MINER),
			new Row(Blocks.BLAST_FURNACE, Items.COAL, VillagerProfession.ARMORER),
			new Row(Blocks.FLETCHING_TABLE, Items.IRON_AXE, ModVillagers.LUMBERJACK),
			new Row(Blocks.FLETCHING_TABLE, Items.FLINT, VillagerProfession.FLETCHER),
			new Row(Blocks.SMITHING_TABLE, Items.REDSTONE, ModVillagers.TINKERER),
			new Row(Blocks.SMITHING_TABLE, Items.IRON_INGOT, VillagerProfession.TOOLSMITH),
			new Row(Blocks.CAULDRON, Items.GRAVEL, ModVillagers.SIFTER),
			new Row(Blocks.CAULDRON, Items.LEATHER, VillagerProfession.LEATHERWORKER),
			new Row(Blocks.CAULDRON, Items.APPLE, ModVillagers.VINTNER),
			new Row(Blocks.LECTERN, Items.PAPER, ModVillagers.SCHOLAR),
			new Row(Blocks.LECTERN, Items.BOOK, ModVillagers.TEACHER),
			new Row(Blocks.LECTERN, Items.LAPIS_LAZULI, VillagerProfession.LIBRARIAN),
			new Row(Blocks.CARTOGRAPHY_TABLE, Items.NETHERRACK, ModVillagers.NETHERWORKER),
			new Row(Blocks.CARTOGRAPHY_TABLE, Items.COMPASS, VillagerProfession.CARTOGRAPHER),
			new Row(Blocks.BREWING_STAND, Items.HONEY_BOTTLE, ModVillagers.NURSE),
			new Row(Blocks.BREWING_STAND, Items.GOLDEN_APPLE, ModVillagers.UNDERTAKER),
			new Row(Blocks.BREWING_STAND, Items.GLASS_BOTTLE, VillagerProfession.CLERIC),
			new Row(Blocks.SMOKER, Items.BEEF, ModVillagers.CHEF),
			new Row(Blocks.SMOKER, Items.SADDLE, ModVillagers.RANCHER),
			new Row(Blocks.SMOKER, Items.LEAD, VillagerProfession.BUTCHER),
			new Row(Blocks.GRINDSTONE, Items.IRON_SWORD, ModVillagers.GUARD),
			new Row(Blocks.GRINDSTONE, Items.IRON_INGOT, VillagerProfession.WEAPONSMITH),
			new Row(Blocks.CRAFTING_TABLE, Items.OAK_PLANKS, ModVillagers.CARPENTER),
			new Row(Blocks.BEEHIVE, Items.GLASS_BOTTLE, ModVillagers.BEEKEEPER),
			new Row(Blocks.BEE_NEST, Items.SHEARS, ModVillagers.BEEKEEPER),
			new Row(Blocks.JUKEBOX, Items.MUSIC_DISC_CAT, ModVillagers.BARD),
			new Row(ModBlocks.MAILBOX, Items.PAPER, ModVillagers.POSTMAN),
			new Row(ModBlocks.SHOP_COUNTER, Items.RED_BED, ModVillagers.INNKEEPER),
			new Row(ModBlocks.SHOP_COUNTER, Items.EMERALD, ModVillagers.SHOPKEEPER),
			new Row(ModBlocks.TRAINING_POST, Items.GOLD_BLOCK, ModVillagers.TRAINER_LEADER),
			new Row(ModBlocks.TRAINING_POST, Items.BOOK, ModVillagers.TUTOR));
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static Optional<BlockPos> site(Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos);
	}

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	/** Every row of the plan: a jobless villager by the block, handed the item, takes the job there. */
	//$ gametest_batch AREA '"stationsPlan"'
	@GameTest(template = AREA, batch = "stationsPlan")
	public void everyItemGivesItsJobAtItsBlock(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerPlayer player = player(helper);
		for (Row row : plan()) {
			helper.setBlock(STATION, row.block());
			Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
			InteractionResult result = Stations.choose(player, villager, new ItemStack(row.item()));
			String what = row.item() + " at " + BuiltInRegistries.BLOCK.getKey(row.block());
			helper.assertTrue(result.consumesAction(), what + ": nothing happened (" + result + ")");
			helper.assertTrue(villager.getVillagerData().getProfession() == row.job(),
				what + " gave " + name(villager.getVillagerData().getProfession()) + ", not " + name(row.job()));
			helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), what + ": works at " + site(villager));
			villager.discard();
			helper.setBlock(STATION, Blocks.AIR);
		}
		helper.succeed();
	}

	/** A job picked with an item stays: vanilla's checks on the workstation don't take it away again. */
	//$ gametest_ticks_batch AREA '400' '"stationsStay"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "stationsStay")
	public void aPickedJobStays(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		Stations.choose(player(helper), villager, new ItemStack(Items.SWEET_BERRIES));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER,
				"now a " + name(villager.getVillagerData().getProfession()));
			helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "works at " + site(villager));
			helper.succeed();
		});
	}

	/** The same villager switches jobs at their block, and back to the vanilla one with its item; their own job's item passes. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void switchingJobsAtTheSameBlock(GameTestHelper helper) {
		helper.setBlock(STATION, Blocks.BLAST_FURNACE);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(STATION), net.minecraft.world.entity.ai.village.poi.PoiTypes.ARMORER,
			VillagerProfession.ARMORER);
		ServerPlayer player = player(helper);
		helper.assertTrue(Stations.choose(player, villager, new ItemStack(Items.COAL)) == InteractionResult.PASS,
			"coal for an armorer should pass on (it hires them)");
		Stations.choose(player, villager, new ItemStack(Items.DIAMOND_PICKAXE));
		helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.MINER, "a pickaxe gave " + name(villager.getVillagerData().getProfession()));
		helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "the miner works at " + site(villager));
		Stations.choose(player, villager, new ItemStack(Items.CHARCOAL));
		helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.ARMORER, "charcoal gave " + name(villager.getVillagerData().getProfession()));
		helper.succeed();
	}

	/** An item with no block of its kind nearby gives no job; neither does it for a child or a nitwit. */
	//$ gametest AREA
	@GameTest(template = AREA)
	public void noJobWithoutItsBlockNearby(GameTestHelper helper) {
		helper.setBlock(new BlockPos(14, 2, 14), Blocks.FLETCHING_TABLE); // too far
		ServerPlayer player = player(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.assertTrue(Stations.choose(player, villager, new ItemStack(Items.IRON_AXE)) == InteractionResult.CONSUME, "no answer");
		helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE, "jobless became " + name(villager.getVillagerData().getProfession()));
		helper.setBlock(STATION, Blocks.FLETCHING_TABLE);
		Villager child = helper.spawn(EntityType.VILLAGER, STANDING);
		child.setAge(-24000);
		Stations.choose(player, child, new ItemStack(Items.IRON_AXE));
		helper.assertTrue(child.getVillagerData().getProfession() == VillagerProfession.NONE, "a child became " + name(child.getVillagerData().getProfession()));
		Villager nitwit = helper.spawn(EntityType.VILLAGER, STANDING);
		nitwit.setVillagerData(nitwit.getVillagerData().setProfession(VillagerProfession.NITWIT));
		Stations.choose(player, nitwit, new ItemStack(Items.IRON_AXE));
		helper.assertTrue(nitwit.getVillagerData().getProfession() == VillagerProfession.NITWIT, "a nitwit became " + name(nitwit.getVillagerData().getProfession()));
		helper.assertTrue(Stations.choose(player, villager, new ItemStack(Items.DIRT)) == InteractionResult.PASS, "dirt picks no job");
		helper.succeed();
	}

	/** By itself a jobless villager takes the vanilla job at a shared block, never one of ours. */
	//$ gametest_ticks_batch AREA '1200' '"stationsVanillaFirst"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "stationsVanillaFirst")
	public void byThemselvesVillagersTakeTheVanillaJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.COMPOSTER);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.succeedWhen(() -> helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.FARMER,
			"by the composter: " + name(villager.getVillagerData().getProfession())));
	}

	/** A crafting table (the carpenter's since 21.1a) never takes a jobless villager by itself. */
	//$ gametest_ticks_batch AREA '1000' '"stationsNotByThemselves"'
	@GameTest(template = AREA, timeoutTicks = 1000, batch = "stationsNotByThemselves")
	public void aCraftingTableTakesNobodyByItself(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.CRAFTING_TABLE);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.runAfterDelay(900, () -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == VillagerProfession.NONE,
				"by the crafting table: " + name(villager.getVillagerData().getProfession()));
			helper.succeed();
		});
	}

	/** Builders take a Blueprint Table by themselves, as they took the bench. */
	//$ gametest_ticks_batch AREA '1200' '"stationsBlueprintTable"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "stationsBlueprintTable")
	public void aBuilderTakesABlueprintTableByThemselves(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.BLUEPRINT_TABLE);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.BUILDER, "now: " + name(villager.getVillagerData().getProfession()));
			helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "works at " + site(villager));
		});
	}

	/**
	 * A worker who lost track of their block (it was broken and put back, or not yet filed when they were given it) takes
	 * a free one of its kind again by themselves, as vanilla workers do: here a lumberjack, at a fletching table.
	 */
	//$ gametest_ticks_batch AREA '1200' '"stationsRetake"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "stationsRetake")
	public void aWorkerTakesTheirBlockAgain(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, Blocks.FLETCHING_TABLE);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.LUMBERJACK));
		villager.setVillagerXp(1);
		villager.refreshBrain(helper.getLevel());
		helper.succeedWhen(() -> {
			helper.assertTrue(villager.getVillagerData().getProfession() == ModVillagers.LUMBERJACK, "now a " + name(villager.getVillagerData().getProfession()));
			helper.assertTrue(site(villager).equals(Optional.of(helper.absolutePos(STATION))), "works at " + site(villager));
		});
	}

	/** Worlds from before 21.1a: an old job block still gives its job by itself, and a worker at one keeps it. */
	//$ gametest_ticks_batch AREA '1200' '"stationsOldBlocks"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "stationsOldBlocks")
	public void theOldJobBlocksStillWork(GameTestHelper helper) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		helper.setBlock(STATION, ModBlocks.FRUIT_BASKET);
		Villager keeper = helper.spawn(EntityType.VILLAGER, STANDING);
		BlockPos bench = new BlockPos(12, 2, 12);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 11));
		Jobs.employ(helper.getLevel(), builder, helper.absolutePos(bench), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		helper.succeedWhen(() -> {
			helper.assertTrue(keeper.getVillagerData().getProfession() == ModVillagers.ORCHARD_KEEPER,
				"by an old Fruit Basket: " + name(keeper.getVillagerData().getProfession()));
			helper.assertTrue(builder.getVillagerData().getProfession() == ModVillagers.BUILDER && site(builder).isPresent(),
				"the builder at an old bench lost the job");
		});
	}

	/**
	 * Every workstation's tooltip can say which item picks each of its jobs, and finds its station from the block: a bee
	 * nest is a beehive's, a Fruit Basket (gone) is nobody's.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void everyJobsItemIsNamed(GameTestHelper helper) {
		for (Stations.Station station : Stations.ALL) {
			// (another mod's block, such as Cobblemon's Fossil Analyzer, is air while that mod isn't installed, as here)
			helper.assertTrue(station.block() == Blocks.AIR || Stations.at(station.block()).orElse(null) == station,
				"no station found for " + station.block());
			for (Stations.Job job : station.jobs()) {
				helper.assertTrue(net.minecraft.locale.Language.getInstance().has(Stations.itemKey(job)), "untranslated: " + Stations.itemKey(job));
			}
		}
		helper.assertTrue(Stations.at(Blocks.BEE_NEST).map(s -> s.block() == Blocks.BEEHIVE).orElse(false), "a bee nest isn't a beehive's station");
		helper.assertTrue(Stations.at(ModBlocks.FRUIT_BASKET).isEmpty(), "the Fruit Basket still shows jobs");
		helper.assertTrue(!Stations.at(Blocks.CRAFTING_TABLE).orElseThrow().byItself() && Stations.at(Blocks.COMPOSTER).orElseThrow().byItself(),
			"which blocks take a villager by themselves");
		helper.succeed();
	}

	/** The job blocks 21.1a replaced can't be crafted any more (they stay registered, so worlds keep them). */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theReplacedBlocksCantBeCrafted(GameTestHelper helper) {
		List<Block> gone = List.of(ModBlocks.BUILDERS_BENCH, ModBlocks.MINERS_BENCH, ModBlocks.CHOPPING_BLOCK, ModBlocks.FRUIT_BASKET,
			ModBlocks.APIARY, ModBlocks.FLOWER_STAND, ModBlocks.SCHOLARS_DESK, ModBlocks.SIEVE, ModBlocks.TINKERS_BENCH, ModBlocks.COMPOST_BIN,
			ModBlocks.NETHER_BRAZIER, ModBlocks.UNDERTAKERS_TABLE, ModBlocks.INN_COUNTER, ModBlocks.TEACHERS_DESK, ModBlocks.FEED_TROUGH,
			ModBlocks.CARPENTERS_BENCH, ModBlocks.KITCHEN_STOVE, ModBlocks.POSTAL_DESK, ModBlocks.GUARD_POST, ModBlocks.NURSE_STATION,
			ModBlocks.MUSIC_STAND, ModBlocks.LEADERS_PODIUM, ModBlocks.TUTORS_DESK, ModBlocks.BALL_WORKBENCH, ModBlocks.TRADE_BOARD,
			ModBlocks.FOSSIL_LAB);
		var access = helper.getLevel().registryAccess();
		for (var recipe : helper.getLevel().getRecipeManager().getRecipes()) {
			Item made = recipe.value().getResultItem(access).getItem();
			helper.assertFalse(gone.stream().anyMatch(b -> b.asItem() == made), recipe.id() + " still makes " + made);
		}
		helper.succeed();
	}
}

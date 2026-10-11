package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageAdvice;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Disasters;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * ROADMAP 32.19, houses for the answering jobs: the Fire Station (the Firewarden's grindstone), the Well House (the Well
 * Keeper's cauldron) and the Physician's House (the brewing stand) are in the Blueprint Table, drawn with what the item
 * asks of each and no other job block; a builder builds each from chests, and a jobless villager there handed the job's
 * item takes the job at that block. What next? suggests each house when the village lacks its job and its disaster can
 * come there, and stops once the village has one, or with the disaster switched off.
 */
public class AnsweringHousesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	/** Every vanilla job block a passing villager could take. */
	private static final Set<Block> JOB_BLOCKS = Set.of(Blocks.BLAST_FURNACE, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.STONECUTTER,
		Blocks.FLETCHING_TABLE, Blocks.CARTOGRAPHY_TABLE, Blocks.BARREL, Blocks.LECTERN, Blocks.COMPOSTER, Blocks.CAULDRON, Blocks.WATER_CAULDRON,
		Blocks.SMOKER, Blocks.BREWING_STAND, Blocks.LOOM, Blocks.CRAFTING_TABLE, Blocks.BEEHIVE, Blocks.JUKEBOX);

	/** A house, its job, the job's block, the item that picks it, and where (in the house) a jobless villager stands by it. */
	record House(StarterBlueprints.Entry entry, VillagerProfession job, Block block, ItemStack item, BlockPos standing) {
	}

	static List<House> houses() {
		return List.of(
			new House(StarterBlueprints.FIRE_STATION, ModVillagers.FIREWARDEN, Blocks.GRINDSTONE, new ItemStack(Items.WATER_BUCKET), new BlockPos(5, 1, 6)),
			new House(StarterBlueprints.WELL_HOUSE, ModVillagers.WELL_KEEPER, Blocks.CAULDRON, new ItemStack(Items.IRON_SHOVEL), new BlockPos(7, 3, 8)),
			new House(StarterBlueprints.PHYSICIANS_HOUSE, ModVillagers.PHYSICIAN, Blocks.BREWING_STAND, new ItemStack(Items.GLISTERING_MELON_SLICE), new BlockPos(6, 1, 8)));
	}

	/** The three are in the Blueprint Table and the creative tab's one-tier list, named, and on the Village Map. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void answeringHousesAreInTheBlueprintTable(GameTestHelper helper) {
		List<ResourceLocation> library = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		for (StarterBlueprints.Entry e : StarterBlueprints.ANSWERING_HOUSES) {
			helper.assertTrue(library.contains(e.id()), e.id() + " isn't in the Blueprint Table");
			helper.assertTrue(StarterBlueprints.ONE_TIER.contains(e), e.id() + " isn't a starter blueprint");
			helper.assertTrue(Language.getInstance().has("blueprint.aliveworkplace." + e.id().getPath()), "no name for " + e.id());
			Blueprint b = BlueprintLibrary.get(helper.getLevel(), e.id()).orElseThrow();
			helper.assertTrue(b.size().equals(e.size()), e.id() + " is " + b.size());
		}
		helper.assertTrue(VillageMaps.kindOf(StarterBlueprints.FIRE_STATION.id()).orElse(null) == VillageMaps.Kind.DEFENCE, "the Fire Station isn't a defence on the map");
		helper.assertTrue(VillageMaps.kindOf(StarterBlueprints.WELL_HOUSE.id()).orElse(null) == VillageMaps.Kind.CARE, "the Well House isn't care on the map");
		helper.assertTrue(VillageMaps.kindOf(StarterBlueprints.PHYSICIANS_HOUSE.id()).orElse(null) == VillageMaps.Kind.CARE, "the Physician's House isn't care on the map");
		helper.succeed();
	}

	/**
	 * What the item asks of the drawings. Each has its job's block once and no other job block. The Fire Station: a stone
	 * ground floor, a water basin, buckets (pails) on racks, a timber loft over it with windows. The Well House: a roof, a
	 * well shaft from the kerb down to water, a trough and a bench. The Physician's House: four beds with white screens
	 * either side of each, and a herb garden.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theHousesHaveWhatTheirJobsNeed(GameTestHelper helper) {
		for (House h : houses()) {
			Blueprint b = BlueprintLibrary.get(helper.getLevel(), h.entry().id()).orElseThrow();
			for (Block block : JOB_BLOCKS) {
				long have = count(b, block);
				helper.assertTrue(have == (block == h.block() ? 1 : 0), h.entry().id() + " has " + have + " " + block);
			}
			helper.assertTrue(b.entities().isEmpty(), h.entry().id() + " comes with an entity");
		}
		Blueprint fire = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.FIRE_STATION.id()).orElseThrow();
		helper.assertTrue(fire.blocks().stream().filter(e -> e.pos().getY() >= 1 && e.pos().getY() <= 4
			&& (e.state().is(Blocks.STONE_BRICKS) || e.state().is(Blocks.CRACKED_STONE_BRICKS))).count() >= 80, "the Fire Station's ground floor isn't stone");
		long basin = fire.blocks().stream().filter(e -> e.state().is(Blocks.WATER) && e.pos().getY() == 1).count();
		helper.assertTrue(basin >= 2, "the Fire Station's basin holds " + basin + " water");
		long pails = fire.blocks().stream().filter(e -> e.state().is(Blocks.FLOWER_POT) && below(fire, e.pos()).filter(s -> s.is(Blocks.SPRUCE_SLAB)
			&& s.getValue(net.minecraft.world.level.block.SlabBlock.TYPE) == SlabType.TOP).isPresent()).count();
		helper.assertTrue(pails >= 6, "only " + pails + " buckets on racks");
		long loft = fire.blocks().stream().filter(e -> e.pos().getY() >= 6 && e.pos().getY() <= 7 && e.state().is(Blocks.SPRUCE_PLANKS)).count();
		long loftWindows = fire.blocks().stream().filter(e -> e.pos().getY() >= 6 && e.pos().getY() <= 7 && e.state().is(Blocks.GLASS_PANE)).count();
		helper.assertTrue(loft >= 20 && loftWindows >= 8, "the watch loft has " + loft + " planks and " + loftWindows + " panes");
		helper.assertTrue(count(fire, Blocks.LADDER) >= 5, "no ladder up to the loft");

		Blueprint well = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.WELL_HOUSE.id()).orElseThrow();
		BlockPos lantern = well.blocks().stream().filter(e -> e.state().is(Blocks.CHAIN)).map(Blueprint.Entry::pos).findFirst().orElseThrow();
		BlockPos bottom = new BlockPos(lantern.getX(), 0, lantern.getZ());
		helper.assertTrue(at(well, bottom).is(Blocks.WATER), "no water at the bottom of the shaft: " + at(well, bottom));
		helper.assertTrue(at(well, bottom.above()).isAir() && at(well, bottom.above(2)).isAir(), "the shaft isn't open above the water");
		long kerb = well.blocks().stream().filter(e -> e.pos().getY() == 3 && Math.abs(e.pos().getX() - bottom.getX()) <= 1
			&& Math.abs(e.pos().getZ() - bottom.getZ()) <= 1 && e.state().is(net.minecraft.tags.BlockTags.STONE_BRICKS)).count();
		helper.assertTrue(kerb == 8, "the kerb has " + kerb + " stones");
		long trough = well.blocks().stream().filter(e -> e.state().is(Blocks.WATER) && e.pos().getY() == 3).count();
		helper.assertTrue(trough >= 3, "the trough holds " + trough + " water");
		helper.assertTrue(count(well, Blocks.SPRUCE_STAIRS) >= 6, "no benches by the well");
		helper.assertTrue(well.blocks().stream().anyMatch(e -> e.state().is(Blocks.DARK_OAK_STAIRS) && e.pos().getY() >= 7), "the well has no roof");

		Blueprint phys = BlueprintLibrary.get(helper.getLevel(), StarterBlueprints.PHYSICIANS_HOUSE.id()).orElseThrow();
		List<BlockPos> beds = phys.blocks().stream().filter(e -> e.state().getBlock() instanceof BedBlock && e.state().getValue(BedBlock.PART) == BedPart.FOOT)
			.map(Blueprint.Entry::pos).toList();
		helper.assertTrue(beds.size() == 4, "the ward has " + beds.size() + " beds");
		for (BlockPos foot : beds) {
			helper.assertTrue(at(phys, foot.north()).is(Blocks.WHITE_WOOL) || at(phys, foot.north(2)).is(Blocks.WHITE_WOOL), "no screen before the bed at " + foot);
			helper.assertTrue(at(phys, foot.south()).is(Blocks.WHITE_WOOL) || at(phys, foot.south(2)).is(Blocks.WHITE_WOOL), "no screen after the bed at " + foot);
		}
		long herbs = phys.blocks().stream().filter(e -> e.state().is(net.minecraft.tags.BlockTags.FLOWERS) || e.state().is(Blocks.FERN)).count();
		helper.assertTrue(herbs >= 8, "the herb garden has " + herbs + " herbs");
		helper.succeed();
	}

	private static Optional<BlockState> below(Blueprint b, BlockPos pos) {
		return Optional.of(at(b, pos.below()));
	}

	private static BlockState at(Blueprint b, BlockPos pos) {
		return b.blocks().stream().filter(e -> e.pos().equals(pos)).map(Blueprint.Entry::state).findFirst().orElse(Blocks.AIR.defaultBlockState());
	}

	private static long count(Blueprint b, Block block) {
		return b.blocks().stream().filter(e -> e.state().is(block)).count();
	}

	// --- each built by a builder, a jobless villager there taking the job with its item -----------------------------

	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos[] CHESTS = {new BlockPos(3, 2, 6), new BlockPos(3, 2, 8), new BlockPos(3, 2, 10)};
	private static final BlockPos BUILDER = new BlockPos(5, 2, 3);
	private static final BlockPos HOUSE = new BlockPos(12, 2, 8);

	@GameTestGenerator
	public Collection<TestFunction> builds() {
		List<TestFunction> out = new ArrayList<>();
		for (House h : houses()) {
			String name = "answeringHouseBuilt_" + h.entry().id().getPath();
			out.add(new TestFunction(name, name, HUGE_AREA, 12000, 0, true, helper -> build(helper, h)));
		}
		return out;
	}

	static void build(GameTestHelper helper, House h) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		ResourceLocation id = h.entry().id();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("missing " + id));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		for (BlockPos c : CHESTS) {
			helper.setBlock(c, Blocks.CHEST);
		}
		var report = BlueprintSupplies.check(level, new BlueprintData(id, Optional.of(blueprint.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> stacks = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(stacks.size() <= 27 * CHESTS.length, id + " needs " + stacks.size() + " stacks");
		for (int i = 0; i < stacks.size(); i++) {
			((Container) helper.getBlockEntity(CHESTS[i / 27])).setItem(i % 27, stacks.get(i));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, id, placement);
		BlockPos block = helper.absolutePos(HOUSE).offset(blueprint.blocks().stream().filter(e -> e.state().is(h.block()))
			.map(Blueprint.Entry::pos).findFirst().orElseThrow());
		Villager[] worker = {null};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage());
			if (worker[0] == null) {
				helper.assertTrue(level.getBlockState(block).is(h.block()), "no " + h.block() + " at " + block + " once built: " + level.getBlockState(block));
				// A jobless villager by the block, handed the job's item by a player: he takes the job there.
				Villager v = helper.spawn(EntityType.VILLAGER, HOUSE.offset(h.standing()).above());
				v.setNoAi(true);
				v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.NONE));
				ServerPlayer player = helper.makeMockServerPlayerInLevel();
				player.setGameMode(GameType.SURVIVAL);
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, h.item().copy());
				Stations.choose(player, v, player.getMainHandItem());
				worker[0] = v;
			}
			Villager v = worker[0];
			helper.assertTrue(v.getVillagerData().getProfession() == h.job(), "the villager is a " + v.getVillagerData().getProfession() + ", not a " + h.job());
			BlockPos at = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
			helper.assertTrue(block.equals(at), "the " + h.job() + " works at " + at + ", not the house's " + h.block() + " at " + block);
		});
	}

	// --- What next? -----------------------------------------------------------------------------------------------

	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/**
	 * A hamlet with no Well Keeper is told to build the Well House (drought can come anywhere); not the Physician's House
	 * (the plague comes only to a town) until it's a town; neither once it has those jobs; and nothing for a disaster
	 * that's switched off.
	 */
	//$ gametest_batch AREA '"answeringHouseTips"'
	@GameTest(template = AREA, batch = "answeringHouseTips")
	public void whatNextSuggestsTheHouseOfAMissingAnsweringJob(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 10;
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Disasters.DISABLED = Set.of();
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setRank(VillageRanks.Rank.HAMLET);
		Villager farmer = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		farmer.setNoAi(true);
		farmer.setVillagerData(farmer.getVillagerData().setProfession(VillagerProfession.FARMER));

		List<String> houses = houses(level, hall);
		helper.assertTrue(houses.contains("blueprint.aliveworkplace.well_house"), "no Well House tip: " + houses);
		helper.assertFalse(houses.contains("blueprint.aliveworkplace.physicians_house"), "the Physician's House for a hamlet: " + houses);
		helper.assertTrue(VillageAdvice.tips(level, hall).stream().anyMatch(t -> t.key().equals("disaster_house")), "What next? doesn't show the house tip");
		VillageAdvice.Tip tip = Disasters.houseTips(level, hall, VillageHalls.census(level, hall)).stream()
			.filter(t -> key(t.args()[1]).equals("blueprint.aliveworkplace.well_house")).findFirst().orElseThrow();
		helper.assertTrue(tip.icon() == Items.CAULDRON, "the Well House tip shows " + tip.icon());
		String how = tip.how().getString();
		helper.assertTrue(how.contains("Well Keeper") && how.contains("Well House") && how.contains("Cauldron") && how.contains("shovel"), "the tip reads: " + how);
		helper.assertTrue(tip.title().getString().equals("Drought can come here: build a Well House"), "the tip's title reads: " + tip.title().getString());

		entity.setRank(VillageRanks.Rank.TOWN);
		helper.assertTrue(houses(level, hall).contains("blueprint.aliveworkplace.physicians_house"), "no Physician's House for a town: " + houses(level, hall));

		Villager keeper = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 6));
		keeper.setNoAi(true);
		keeper.setVillagerData(keeper.getVillagerData().setProfession(ModVillagers.WELL_KEEPER));
		Villager physician = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 6));
		physician.setNoAi(true);
		physician.setVillagerData(physician.getVillagerData().setProfession(ModVillagers.PHYSICIAN));
		houses = houses(level, hall);
		helper.assertFalse(houses.contains("blueprint.aliveworkplace.well_house") || houses.contains("blueprint.aliveworkplace.physicians_house"),
			"still told to build with a Well Keeper and a Physician: " + houses);

		keeper.discard();
		Disasters.DISABLED = Set.of("drought");
		helper.assertFalse(houses(level, hall).contains("blueprint.aliveworkplace.well_house"), "the Well House with drought switched off");
		Disasters.DISABLED = Set.of();
		helper.assertTrue(houses(level, hall).contains("blueprint.aliveworkplace.well_house"), "no Well House once the keeper is gone");
		helper.succeed();
	}

	private static List<String> houses(ServerLevel level, BlockPos hall) {
		return Disasters.houseTips(level, hall, VillageHalls.census(level, hall)).stream().map(t -> key(t.args()[1])).toList();
	}

	private static String key(Object arg) {
		return arg instanceof Component c && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(arg);
	}

	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyNewSentenceHasItsText(GameTestHelper helper) {
		for (String key : List.of("advice.aliveworkplace.disaster_house", "advice.aliveworkplace.disaster_house.how", "blueprint.aliveworkplace.fire_station",
			"blueprint.aliveworkplace.well_house", "blueprint.aliveworkplace.physicians_house")) {
			helper.assertTrue(Language.getInstance().has(key), "no text for " + key);
		}
		helper.succeed();
	}
}

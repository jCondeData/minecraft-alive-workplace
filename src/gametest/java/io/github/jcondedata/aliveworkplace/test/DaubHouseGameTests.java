package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.ModdedLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.furnish.BlueprintFurniture;
import io.github.jcondedata.aliveworkplace.furnish.FurnitureSpot;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mixin.StructureTemplatePoolAccessor;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * ROADMAP 49.2: the Daub House (36.23, the owner's pick, with 36.23a-c's changes) is a Level I home in the villages. Its
 * blueprint loads with four beds, a front door and every furniture piece a furniture spot; the owner's three changes
 * hold (the loft railing joined at its corner, the short lamp on its side table, the plates only in the kitchen). It is
 * in the Blueprint Table and on the Village Map as a home, has its modded copy, grows in vanilla villages of all five
 * styles with a jobless villager, the Steward plans one for a hamlet three beds short, and a builder builds it to DONE.
 */
public class DaubHouseGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final StarterBlueprints.Entry DAUB = StarterBlueprints.DAUB_HOUSE;
	/** The kitchen's walls (tools/blueprints/js/house_t1_b/plan.mjs, MASSES.kitchen): x 8..11, z 3..9. */
	private static final int KITCHEN_X0 = 8;
	/** The loft railing's corner post (plan.mjs: the joined railing of 36.23b), with runs going east and south from it. */
	private static final BlockPos RAILING_CORNER = new BlockPos(5, 5, 5);

	private static Blueprint blueprint(GameTestHelper helper) {
		return BlueprintLibrary.get(helper.getLevel(), DAUB.id()).orElseThrow(() -> new GameTestAssertException("no blueprint " + DAUB.id()));
	}

	private static BlockState at(Blueprint b, BlockPos pos) {
		return b.blocks().stream().filter(e -> e.pos().equals(pos)).map(Blueprint.Entry::state).findFirst().orElse(Blocks.AIR.defaultBlockState());
	}

	private static long bedHeads(List<BlockState> states) {
		return states.stream().filter(s -> s.getBlock() instanceof BedBlock && s.getValue(BedBlock.PART) == BedPart.HEAD).count();
	}

	/** The blueprint: its declared size, a front door at (4, 1, 2) on the z = 0 side, four beds, a hearth's campfire. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theDaubHouseLoadsWithItsDoorAndFourBeds(GameTestHelper helper) {
		Blueprint b = blueprint(helper);
		helper.assertTrue(b.size().equals(DAUB.size()) && b.size().equals(new Vec3i(13, 14, 13)), "the Daub House is " + b.size());
		helper.assertTrue(DAUB.basement() == 0, "the Daub House has a basement: " + DAUB.basement());
		BlockState door = at(b, new BlockPos(4, 1, 2));
		helper.assertTrue(door.getBlock() instanceof DoorBlock && door.getValue(DoorBlock.HALF).name().equals("LOWER"), "no front door at (4, 1, 2): " + door);
		long beds = bedHeads(b.blocks().stream().map(Blueprint.Entry::state).toList());
		helper.assertTrue(beds == 4, "the Daub House has " + beds + " beds, not 4");
		helper.assertTrue(b.blocks().stream().anyMatch(e -> e.state().is(Blocks.CAMPFIRE)), "no hearth fire");
		helper.assertTrue(b.entities().isEmpty(), "the blueprint comes with an entity: " + b.entities());
		helper.succeed();
	}

	/**
	 * Every furniture piece is a furniture spot (beds, chairs, table, counters, lamps, crockery...), and the owner's three
	 * changes hold: the loft railing joins at its corner, the one standing lamp stands on a side table, and every plate is
	 * on the kitchen's counters or shelves.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyPieceIsASpotAndTheOwnersChangesHold(GameTestHelper helper) {
		Blueprint b = blueprint(helper);
		List<FurnitureSpot> spots = BlueprintFurniture.of(DAUB.id());
		helper.assertTrue(!spots.isEmpty(), "the Daub House has no furniture spots");
		java.util.Map<FurnitureSpot.Kind, Long> kinds = spots.stream().collect(Collectors.groupingBy(FurnitureSpot::kind, Collectors.counting()));
		helper.assertTrue(kinds.getOrDefault(FurnitureSpot.Kind.BED, 0L) == 4, "bed spots: " + kinds);
		for (FurnitureSpot.Kind k : List.of(FurnitureSpot.Kind.TABLE, FurnitureSpot.Kind.CHAIR, FurnitureSpot.Kind.COUNTER, FurnitureSpot.Kind.LAMP,
				FurnitureSpot.Kind.CROCKERY, FurnitureSpot.Kind.SIDE_TABLE, FurnitureSpot.Kind.NIGHTSTAND, FurnitureSpot.Kind.CUPBOARD)) {
			helper.assertTrue(kinds.getOrDefault(k, 0L) >= 1, "no " + k + " spot: " + kinds);
		}
		// every bed and every lamp of the drawing (the one over the front door is the porch's, outside) is in a spot
		Set<BlockPos> spotted = spots.stream().flatMap(s -> s.positions().stream()).collect(Collectors.toSet());
		List<BlockPos> loose = b.blocks().stream().filter(e -> e.state().getBlock() instanceof BedBlock || e.state().is(Blocks.LANTERN))
			.map(Blueprint.Entry::pos).filter(p -> p.getZ() > 1 && !spotted.contains(p)).toList();
		helper.assertTrue(loose.isEmpty(), "pieces that aren't furniture spots: " + loose);

		// 36.23b: the loft railing joins at its corner
		BlockState corner = at(b, RAILING_CORNER);
		helper.assertTrue(corner.is(Blocks.SPRUCE_FENCE) && corner.getValue(CrossCollisionBlock.EAST) && corner.getValue(CrossCollisionBlock.SOUTH),
			"the loft railing's corner doesn't join: " + corner);
		// 36.23b: a lamp that isn't hanging stands on a side table (a top slab), never on the floor
		for (Blueprint.Entry e : b.blocks()) {
			if (e.state().is(Blocks.LANTERN) && !e.state().getValue(LanternBlock.HANGING)) {
				BlockState under = at(b, e.pos().below());
				helper.assertTrue(under.getBlock() instanceof SlabBlock && under.getValue(SlabBlock.TYPE) == SlabType.TOP,
					"a lamp stands on " + under + " at " + e.pos());
				helper.assertTrue(spots.stream().anyMatch(s -> s.kind() == FurnitureSpot.Kind.SIDE_TABLE && s.positions().contains(e.pos().below())),
					"the lamp at " + e.pos() + " isn't on a side table");
			}
		}
		// 36.23c: the plates are in the kitchen, none by the door
		for (FurnitureSpot s : spots) {
			if (s.kind() == FurnitureSpot.Kind.CROCKERY) {
				helper.assertTrue(s.pos().getX() >= KITCHEN_X0, "plates outside the kitchen at " + s.pos());
			}
		}
		helper.assertTrue(spots.stream().noneMatch(s -> s.kind() == FurnitureSpot.Kind.SHELF && s.pos().getZ() <= 3 && s.pos().getX() < KITCHEN_X0),
			"shelves by the entryway again");
		helper.succeed();
	}

	/** In the Blueprint Table (one tier, offered), named "Daub House", a home on the Village Map, with its modded copy. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theDaubHouseIsInTheBlueprintTable(GameTestHelper helper) {
		List<ResourceLocation> library = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		helper.assertTrue(library.contains(DAUB.id()), "the Daub House isn't in the Blueprint Table");
		helper.assertTrue(StarterBlueprints.ONE_TIER.contains(DAUB) && StarterBlueprints.offered(DAUB.id()), "the Daub House isn't an offered one-tier build");
		helper.assertTrue(Language.getInstance().getOrDefault("blueprint.aliveworkplace." + DAUB.id().getPath()).equals("Daub House"),
			"its name: " + Language.getInstance().getOrDefault("blueprint.aliveworkplace." + DAUB.id().getPath()));
		helper.assertTrue(VillageMaps.kindOf(DAUB.id()).orElse(null) == VillageMaps.Kind.HOMES, "the Daub House isn't a home on the map");
		// the modded copy: listed, and without the building mods (as here) the plain Daub House is built
		helper.assertTrue(ModdedLibrary.hasCopy(DAUB.id()), "the Daub House has no modded copy");
		helper.assertTrue(!ModdedLibrary.missing(DAUB.id()).isEmpty() && ModdedLibrary.copy(DAUB.id()).isEmpty(),
			"the modded copy needs none of the building mods: " + ModdedLibrary.missing(DAUB.id()));
		helper.assertTrue(helper.getLevel().getServer().getAdvancements().get(AliveWorkplace.id("built/" + DAUB.id().getPath())) != null,
			"no built/house_t1_b advancement");
		helper.succeed();
	}

	/**
	 * Villages of all five styles grow it: in each house pool, the street's jigsaw before the door, a jobless villager from
	 * the worker pool, four beds, at most one job block (as village houses), no stored villager.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void everyVillageStyleGrowsADaubHouse(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		var pools = level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
		helper.assertTrue(VillageHouses.HOMES.containsKey(DAUB.id().getPath()) && VillageHouses.HOMES.get(DAUB.id().getPath()) > 0, "not a village home");
		for (String style : VillageHouses.STYLES) {
			ResourceLocation id = VillageHouses.home(style, DAUB.id().getPath());
			StructureTemplatePool pool = pools.get(VillageHouses.housePool(style));
			helper.assertTrue(pool != null, "no house pool for " + style);
			boolean listed = ((StructureTemplatePoolAccessor) pool).aliveworkplace$rawTemplates().stream()
				.anyMatch(p -> p.getFirst().toString().matches(".*" + java.util.regex.Pattern.quote(id.toString()) + "(?![a-z_0-9]).*"));
			helper.assertTrue(listed, style + " villages can't grow a Daub House");
			StructureTemplate template = level.getStructureManager().get(id).orElseThrow(() -> new GameTestAssertException("missing " + id));
			helper.assertTrue(template.getSize().equals(DAUB.size()), id + " is " + template.getSize());
			var jigsaws = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.JIGSAW);
			helper.assertTrue(jigsaws.stream().anyMatch(j -> j.pos().equals(new BlockPos(4, 1, 0)) && j.nbt() != null
				&& j.nbt().getString("name").equals("minecraft:building_entrance")), id + " has no street connection before its door");
			helper.assertTrue(jigsaws.stream().anyMatch(j -> j.nbt() != null && j.nbt().getString("pool").equals("aliveworkplace:village/" + style + "/workers")),
				id + " spawns no villager");
			List<BlockState> states = states(template);
			helper.assertTrue(bedHeads(states) == 4, id + " has " + bedHeads(states) + " beds");
			long jobs = states.stream().filter(DaubHouseGameTests::jobSite).count();
			helper.assertTrue(jobs <= 1, id + " has " + jobs + " job blocks");
			CompoundTag saved = template.save(new CompoundTag());
			helper.assertTrue(saved.getList("entities", Tag.TAG_COMPOUND).isEmpty(), id + " stores a villager: the worker pool gives it one");
			helper.assertTrue(template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.AIR).stream()
				.noneMatch(i -> i.pos().getZ() == 0 && i.pos().getY() == 0), id + "'s front row cuts the street");
		}
		// each style in its own materials: no two styles' pieces alike
		Set<Set<String>> palettes = new java.util.HashSet<>();
		for (String style : VillageHouses.STYLES) {
			StructureTemplate t = level.getStructureManager().get(VillageHouses.home(style, DAUB.id().getPath())).orElseThrow();
			palettes.add(states(t).stream().map(s -> BuiltInRegistries.BLOCK.getKey(s.getBlock()).getPath()).collect(Collectors.toSet()));
		}
		helper.assertTrue(palettes.size() == VillageHouses.STYLES.size(), "some styles share their blocks");
		helper.succeed();
	}

	private static List<BlockState> states(StructureTemplate template) {
		CompoundTag tag = template.save(new CompoundTag());
		ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
		List<BlockState> byIndex = new ArrayList<>();
		for (int i = 0; i < palette.size(); i++) {
			byIndex.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
		}
		List<BlockState> out = new ArrayList<>();
		ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
		for (int i = 0; i < blocks.size(); i++) {
			out.add(byIndex.get(blocks.getCompound(i).getInt("state")));
		}
		return out;
	}

	private static boolean jobSite(BlockState state) {
		return PoiTypes.forState(state).filter(h -> BuiltInRegistries.VILLAGER_PROFESSION.stream()
			.anyMatch(p -> p != VillagerProfession.NONE && p.heldJobSite().test(h))).isPresent();
	}

	// --- the Steward's plan ----------------------------------------------------------------------------------------

	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	private static BlockPos hall(GameTestHelper helper, VillageRanks.Rank rank) {
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> helper.getLevel().removeBlock(hall, false));
		((VillageHallBlockEntity) helper.getLevel().getBlockEntity(hall)).setRank(rank);
		return hall;
	}

	private static void villagers(GameTestHelper helper, int n) {
		for (int i = 0; i < n; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3 + 2 * i, 2, 20));
			v.setNoAi(true);
		}
	}

	private static List<String> wishes(GameTestHelper helper, BlockPos hall) {
		ServerLevel level = helper.getLevel();
		return StewardWishes.rank(StewardRules.all(), StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, StewardWishes.day(level))
			.stream().map(w -> w.rule().getPath()).toList();
	}

	/**
	 * A hamlet three beds short: the Steward plans a Daub House (four beds) in a Homes zone, before a Stone House; two
	 * short, a Stone House as before; a Village three short, the Terrace as before (no Daub House).
	 */
	//$ gametest_ticks_batch AREA '40' '"daubHouseSteward"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "daubHouseSteward")
	public void aHamletThreeBedsShortPlansADaubHouse(GameTestHelper helper) {
		BlockPos hall = hall(helper, VillageRanks.Rank.HAMLET);
		villagers(helper, 2);
		List<String> two = wishes(helper, hall);
		helper.assertTrue(two.contains("homes_stone_house") && !two.contains("homes_house_t1_b"), "two beds short: " + two);
		villagers(helper, 1);
		ServerLevel level = helper.getLevel();
		List<StewardWishes.Wish> wishes = StewardWishes.rank(StewardRules.all(), StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY,
			StewardWishes.day(level));
		List<String> ids = wishes.stream().map(w -> w.rule().getPath()).toList();
		Optional<StewardWishes.Wish> daub = wishes.stream().filter(w -> w.rule().getPath().equals("homes_house_t1_b")).findFirst();
		helper.assertTrue(daub.isPresent(), "three beds short, no Daub House: " + ids);
		StewardRules.Effect e = daub.get().effect();
		helper.assertTrue(e.kind() == StewardRules.Kind.BUILD && e.blueprint().equals(Optional.of(DAUB.id())) && e.zone().equals(Optional.of("homes")),
			"the wish doesn't build a Daub House in a Homes zone: " + e);
		helper.assertTrue(StewardWishes.plotFor(daub.get()).map(r -> r.zoneKind().equals("homes")).orElse(false), "no plot asked in a Homes zone");
		helper.assertTrue(ids.indexOf("homes_house_t1_b") < ids.indexOf("homes_stone_house"), "the Daub House isn't first: " + ids);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.VILLAGE);
		List<String> village = wishes(helper, hall);
		helper.assertTrue(village.contains("homes_terrace") && !village.contains("homes_house_t1_b"), "a Village three short: " + village);
		helper.succeed();
	}

	// --- built by a builder ------------------------------------------------------------------------------------------

	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos[] CHESTS = {new BlockPos(3, 2, 6), new BlockPos(3, 2, 8), new BlockPos(3, 2, 10), new BlockPos(3, 2, 12)};
	private static final BlockPos BUILDER = new BlockPos(5, 2, 3);
	private static final BlockPos HOUSE = new BlockPos(12, 2, 8);

	/** A builder with the supplies in his chests builds the Daub House to DONE: its door, four beds, the joined railing. */
	//$ gametest_ticks_batch HUGE '12000' '"daubHouseBuilt"'
	@GameTest(template = HUGE, timeoutTicks = 12000, batch = "daubHouseBuilt")
	public void aBuilderBuildsTheDaubHouse(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		Blueprint blueprint = blueprint(helper);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(HOUSE), Rotation.NONE, Mirror.NONE);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		for (BlockPos c : CHESTS) {
			helper.setBlock(c, Blocks.CHEST);
		}
		var report = BlueprintSupplies.check(level, new BlueprintData(DAUB.id(), Optional.of(blueprint.size()), Optional.of(placement)))
			.orElseThrow(() -> new GameTestAssertException("no supply report"));
		List<ItemStack> stacks = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(!stacks.isEmpty() && stacks.size() <= 27 * CHESTS.length, "the Daub House needs " + stacks.size() + " stacks");
		for (int i = 0; i < stacks.size(); i++) {
			((Container) helper.getBlockEntity(CHESTS[i / 27])).setItem(i % 27, stacks.get(i));
		}
		Villager builder = helper.spawn(EntityType.VILLAGER, BUILDER);
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		BuildSite site = Builders.start(level, builder, null, DAUB.id(), placement);
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage());
			BlockState door = helper.getBlockState(HOUSE.offset(4, 1, 2));
			helper.assertTrue(door.getBlock() instanceof DoorBlock, "no front door once built: " + door);
			List<BlockState> placed = new ArrayList<>();
			for (Blueprint.Entry e : blueprint.blocks()) {
				if (e.state().getBlock() instanceof BedBlock) {
					placed.add(helper.getBlockState(HOUSE.offset(e.pos())));
				}
			}
			helper.assertTrue(bedHeads(placed) == 4, "built with " + bedHeads(placed) + " beds of 4");
			BlockState corner = helper.getBlockState(HOUSE.offset(RAILING_CORNER));
			helper.assertTrue(corner.is(Blocks.SPRUCE_FENCE) && corner.getValue(CrossCollisionBlock.EAST) && corner.getValue(CrossCollisionBlock.SOUTH),
				"the built railing's corner doesn't join: " + corner);
		});
	}
}

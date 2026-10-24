package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.furnish.Furnishings;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;

/**
 * ROADMAP 37.7: the Spruce style builds Carved Wood's spruce chests, bookshelves, lecterns, ladders and lanterns (each
 * job block only where Carved Wood registers it as the same villager job site), and the Grand style's floors and wall
 * panels are Rechiseled's patterned planks and andesite; both under {@code furnishedBuilds} (gated by M37). A Carved
 * Wood chest or barrel is a supply chest.
 */
public class CarvedStylesCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final String AREA = CompatGameTests.AREA;

	/**
	 * The styles as data: the Spruce style's chest, shelf, lectern, ladder and lantern are Carved Wood's, its lectern the
	 * Librarian's job site and Carved Wood's barrels the Fisherman's, as the vanilla ones are; Grand's planks, infill and
	 * polished andesite are Rechiseled's. With {@code furnishedBuilds} off the Spruce style isn't offered and Grand is
	 * today's Grand, block for block.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void theStylesUseCarvedWoodAndRechiseled(GameTestHelper helper) {
		boolean was = Furnishings.ENABLED;
		try {
			Furnishings.ENABLED = true;
			BlueprintStyles.Style spruce = BlueprintStyles.get("spruce").orElseThrow(() -> new GameTestAssertException("no spruce style"));
			BlueprintStyles.Style grand = BlueprintStyles.get("grand").orElseThrow(() -> new GameTestAssertException("no grand style"));
			helper.assertTrue(BlueprintStyles.all().contains(spruce), "the Spruce style isn't offered with the switch on");
			Map<Block, String> spruceWants = Map.of(Blocks.CHEST, "carved_wood:spruce_chest", Blocks.BOOKSHELF, "carved_wood:spruce_bookshelf",
				Blocks.LECTERN, "carved_wood:spruce_lectern", Blocks.LADDER, "carved_wood:spruce_ladder", Blocks.LANTERN, "carved_wood:spruce_lantern",
				Blocks.CRAFTING_TABLE, "carved_wood:spruce_crafting_table", Blocks.OAK_PLANKS, "minecraft:spruce_planks",
				Blocks.DARK_OAK_LOG, "minecraft:spruce_log", Blocks.BARREL, "minecraft:barrel");
			for (Map.Entry<Block, String> e : spruceWants.entrySet()) {
				String got = id(BlueprintStyles.apply(spruce, e.getKey().defaultBlockState()));
				helper.assertTrue(got.equals(e.getValue()), "spruce: " + id(e.getKey().defaultBlockState()) + " -> " + got + ", not " + e.getValue());
			}
			BlockState lectern = BlueprintStyles.apply(spruce, Blocks.LECTERN.defaultBlockState());
			helper.assertTrue(isPoi(lectern, PoiTypes.LIBRARIAN), "Carved Wood's spruce lectern isn't the Librarian's job site");
			helper.assertTrue(isPoi(block("carved_wood:oak_barrel").defaultBlockState(), PoiTypes.FISHERMAN), "Carved Wood's oak barrel isn't the Fisherman's job site");
			BlockState hanging = BlueprintStyles.apply(spruce, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
			helper.assertTrue(hanging.getValues().entrySet().stream().anyMatch(p -> p.getKey().getName().equals("shape") && p.getValue().toString().equals("hanging")),
				"a hanging lantern isn't hanging in spruce: " + hanging);
			// Only the hanging flag hangs it: a standing lantern that is waterlogged (another true flag) still stands.
			BlockState standing = BlueprintStyles.apply(spruce, Blocks.LANTERN.defaultBlockState()
				.setValue(net.minecraft.world.level.block.LanternBlock.WATERLOGGED, true));
			helper.assertTrue(standing.getValues().entrySet().stream().noneMatch(p -> p.getKey().getName().equals("shape") && p.getValue().toString().equals("hanging")),
				"a standing waterlogged lantern hangs in spruce: " + standing);
			Map<Block, String> grandWants = Map.of(Blocks.SPRUCE_PLANKS, "rechiseled:dark_oak_planks_flooring", Blocks.BIRCH_PLANKS, "rechiseled:andesite_squares",
				Blocks.WHITE_TERRACOTTA, "rechiseled:andesite_squares", Blocks.POLISHED_ANDESITE, "rechiseled:andesite_tiles",
				Blocks.COBBLESTONE, "minecraft:stone_bricks");
			for (Map.Entry<Block, String> e : grandWants.entrySet()) {
				String got = id(BlueprintStyles.apply(grand, e.getKey().defaultBlockState()));
				helper.assertTrue(got.equals(e.getValue()), "grand: " + id(e.getKey().defaultBlockState()) + " -> " + got + ", not " + e.getValue());
			}

			Furnishings.ENABLED = false;
			helper.assertTrue(!BlueprintStyles.all().contains(spruce), "the Spruce style is offered with the switch off");
			Map<Block, String> grandOff = Map.of(Blocks.SPRUCE_PLANKS, "minecraft:dark_oak_planks", Blocks.BIRCH_PLANKS, "minecraft:polished_andesite",
				Blocks.POLISHED_ANDESITE, "minecraft:polished_andesite");
			for (Map.Entry<Block, String> e : grandOff.entrySet()) {
				String got = id(BlueprintStyles.apply(grand, e.getKey().defaultBlockState()));
				helper.assertTrue(got.equals(e.getValue()), "grand, switch off: " + id(e.getKey().defaultBlockState()) + " -> " + got + ", not " + e.getValue());
			}
			ResourceLocation cottage = AliveWorkplace.id("starter_cottage");
			Blueprint grandOffCottage = BlueprintLibrary.get(helper.getLevel(), BlueprintStyles.styled(cottage, "grand")).orElseThrow();
			helper.assertTrue(grandOffCottage.blocks().stream().noneMatch(b -> id(b.state()).startsWith("rechiseled:")), "a Rechiseled block in Grand with the switch off");
		} finally {
			Furnishings.ENABLED = was;
		}
		helper.succeed();
	}

	/**
	 * A builder takes his stone bricks out of a Carved Wood spruce chest, and a Carved Wood oak barrel beside it is listed as
	 * a supply chest too: both are stores for the bench, and the chest is empty once the build is done.
	 */
	//$ gametest_ticks_batch AREA '1200' '"carved_supply_chest"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "carved_supply_chest")
	public void aCarvedWoodChestIsASupplyChest(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> design = new java.util.HashMap<>();
		for (int x = 0; x < 3; x++) {
			design.put(new BlockPos(x, 0, 0), Blocks.STONE_BRICKS.defaultBlockState());
		}
		ResourceLocation id = CompatGameTests.blueprintFrom(helper, "carved_chest", design, Map.of());
		CompatGameTests.Setup s = CompatGameTests.setup(helper, id);
		BlockPos chestRel = new BlockPos(3, 2, 4);
		BlockPos barrelRel = new BlockPos(1, 2, 4);
		helper.setBlock(chestRel, block("carved_wood:spruce_chest"));
		helper.setBlock(barrelRel, block("carved_wood:oak_barrel"));
		Container chest = helper.getBlockEntity(chestRel);
		chest.setItem(0, new ItemStack(Items.STONE_BRICKS, 3));
		BlockPos bench = helper.absolutePos(CompatGameTests.BENCH);
		List<BlockPos> stores = SupplyContainers.find(level, bench, null);
		helper.assertTrue(stores.contains(helper.absolutePos(chestRel)) && stores.contains(helper.absolutePos(barrelRel)),
			"the bench's stores: " + stores.stream().map(p -> helper.relativePos(p).toShortString()).toList());
		helper.assertTrue(SupplyContainers.insert(level, List.of(helper.absolutePos(barrelRel)), new ItemStack(Items.OAK_LOG, 5)).isEmpty()
			&& SupplyContainers.count(level, List.of(helper.absolutePos(barrelRel)), Items.OAK_LOG) == 5, "the Carved Wood barrel took no logs");
		helper.succeedWhen(() -> {
			CompatGameTests.assertBuilt(helper, s);
			helper.assertTrue(chest.isEmpty(), "the Carved Wood chest still holds " + chest.getItem(0));
		});
	}

	/**
	 * A builder builds the Library in the Spruce style with Carved Wood (and the pack's furniture mods): every block is
	 * right, nothing skipped, its shelves are Carved Wood's spruce bookshelves (no vanilla one left), its lectern Carved
	 * Wood's (a Librarian's job site), and a villager without a job walks up to that lectern and takes one of its jobs
	 * (36.17: a Librarian, a Scholar or a Teacher, drawn at random).
	 */
	//$ gametest_ticks_batch HUGE_AREA '32000' '"carved_spruce_library"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 32000, batch = "carved_spruce_library")
	public void aSpruceLibraryHasCarvedShelvesAndALibrarian(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = BlueprintStyles.styled(AliveWorkplace.id("library"), "spruce");
		Blueprint styled = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no " + id));
		long shelves = styled.blocks().stream().filter(e -> id(e.state()).equals("carved_wood:spruce_bookshelf")).count();
		helper.assertTrue(shelves >= 30, "the spruce Library has " + shelves + " Carved Wood bookshelves");
		BuildSite site = start(helper, id);
		BuildPlan plan = site.plan(level);
		BlockPos origin = site.placement().origin();
		List<BlockPos> lecterns = styled.blocks().stream().filter(e -> id(e.state()).equals("carved_wood:spruce_lectern")).map(e -> origin.offset(e.pos())).toList();
		helper.assertTrue(lecterns.size() == 1, "lecterns in the spruce Library: " + lecterns.size());
		BlockPos lectern = lecterns.get(0);
		BoundingBox box = BoundingBox.fromCorners(origin, origin.offset(styled.size()).offset(-1, -1, -1));
		AtomicReference<Villager> librarian = new AtomicReference<>();
		helper.succeedWhen(() -> {
			if (librarian.get() == null) {
				helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
					"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
				List<BlockPos> unfinished = plan.unfinished(level);
				helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
					.map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
				helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
				int carved = 0;
				int vanilla = 0;
				for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
					BlockState there = level.getBlockState(p);
					carved += id(there).equals("carved_wood:spruce_bookshelf") ? 1 : 0;
					vanilla += there.is(Blocks.BOOKSHELF) ? 1 : 0;
				}
				helper.assertTrue(carved == shelves && vanilla == 0, "built: " + carved + " Carved Wood bookshelves of " + shelves + ", " + vanilla + " vanilla");
				helper.assertTrue(id(level.getBlockState(lectern)).equals("carved_wood:spruce_lectern")
					&& level.getPoiManager().getType(lectern).map(h -> h.is(PoiTypes.LIBRARIAN)).orElse(false), "the lectern is no Librarian's job site");
				clearStrays(helper, site, lectern);
				helper.assertTrue(level.getPoiManager().getFreeTickets(lectern) > 0, "the lectern is taken before the villager comes");
				Villager v = EntityType.VILLAGER.create(level);
				BlockPos stand = standNear(level, lectern);
				v.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0, 0);
				level.addFreshEntity(v);
				librarian.set(v);
				throw new GameTestAssertException("a villager without a job is in the Library");
			}
			Villager v = librarian.get();
			// 36.17: the lectern is shared, so its villager takes one of the lectern's jobs at random (a Librarian, a
			// Scholar or a Teacher, B266: the Scholar was missing here), read from Stations so a new lectern job counts too
			List<VillagerProfession> lecternJobs = Stations.ALL.stream().filter(s -> s.poi().test(level.registryAccess()
					.registryOrThrow(net.minecraft.core.registries.Registries.POINT_OF_INTEREST_TYPE).getHolderOrThrow(PoiTypes.LIBRARIAN)))
				.findFirst().orElseThrow().jobs().stream().map(j -> j.profession().get()).toList();
			helper.assertTrue(lecternJobs.contains(VillagerProfession.LIBRARIAN), "the lectern's jobs: " + lecternJobs);
			helper.assertTrue(lecternJobs.contains(v.getVillagerData().getProfession()),
				"the villager is " + v.getVillagerData().getProfession() + " (job site " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE) + ")"
					+ diagnose(helper, v, lectern));
			helper.assertTrue(v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).map(lectern::equals).orElse(false),
				"the Librarian's job site isn't the Carved Wood lectern: " + v.getBrain().getMemory(MemoryModuleType.JOB_SITE));
		});
	}

	/**
	 * B339: Carved Wood's lantern calls itself passable to a land path (vanilla's doesn't), so a villager's path ran
	 * through one standing on a desk in the spruce Library: up onto the desk, where the villager stood at the edge, walking
	 * on the spot. Here a wall across the villager's way has one gap, a block (the desk) with a Carved Wood lantern on it:
	 * the path goes round the wall's end, not up over the desk through the lantern.
	 */
	//$ gametest_batch AREA '"b339PathLantern"'
	@GameTest(template = AREA, batch = "b339PathLantern")
	public void aVillagersPathGoesRoundACarvedWoodLanternOnADesk(GameTestHelper helper) {
		BlockState lantern = block("carved_wood:spruce_lantern").defaultBlockState();
		helper.assertTrue(lantern.isPathfindable(net.minecraft.world.level.pathfinder.PathComputationType.LAND),
			"Carved Wood's lantern no longer calls itself passable: this test checks nothing");
		Path path = pathThroughGap(helper, Blocks.SPRUCE_PLANKS.defaultBlockState(), lantern);
		helper.assertTrue(!passes(helper, path, new BlockPos(6, 3, 6)) && !passes(helper, path, new BlockPos(6, 2, 6)),
			"the path runs over the desk through the Carved Wood lantern: " + nodes(helper, path));
		helper.succeed();
	}

	/** What a villager steps onto stays walkable: the same gap with a carpet in it is walked through. */
	//$ gametest_batch AREA '"b339PathCarpet"'
	@GameTest(template = AREA, batch = "b339PathCarpet")
	public void aVillagersPathStillCrossesACarpet(GameTestHelper helper) {
		Path path = pathThroughGap(helper, Blocks.RED_CARPET.defaultBlockState(), Blocks.AIR.defaultBlockState());
		helper.assertTrue(passes(helper, path, new BlockPos(6, 2, 6)), "the path doesn't cross the carpet in the gap: " + nodes(helper, path));
		helper.succeed();
	}

	/**
	 * A wall three high across the area (x 1 to 11 at z 6), its gap at x = 6 holding {@code feet} and {@code above} it,
	 * and a nitwit's path (nothing to walk to of its own) from (6, 2, 3) to (6, 2, 9): through the gap is the short way.
	 */
	private static Path pathThroughGap(GameTestHelper helper, BlockState feet, BlockState above) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		for (int x = 1; x <= 11; x++) {
			for (int y = 2; y <= 4; y++) {
				helper.setBlock(new BlockPos(x, y, 6), Blocks.STONE_BRICKS);
			}
		}
		helper.setBlock(new BlockPos(6, 2, 6), feet);
		helper.setBlock(new BlockPos(6, 3, 6), above);
		helper.setBlock(new BlockPos(6, 4, 6), Blocks.AIR);
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 3));
		villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.NITWIT));
		villager.refreshBrain(helper.getLevel());
		villager.setOnGround(true);
		Path path = villager.getNavigation().createPath(helper.absolutePos(new BlockPos(6, 2, 9)), 0);
		helper.assertTrue(path != null && path.canReach(), "no path to the far side of the wall: " + path);
		return path;
	}

	private static boolean passes(GameTestHelper helper, Path path, BlockPos cell) {
		BlockPos abs = helper.absolutePos(cell);
		for (int i = 0; i < path.getNodeCount(); i++) {
			if (path.getNodePos(i).equals(abs)) {
				return true;
			}
		}
		return false;
	}

	private static String nodes(GameTestHelper helper, @org.jetbrains.annotations.Nullable Path path) {
		if (path == null) {
			return "none";
		}
		List<String> out = new ArrayList<>();
		for (int i = 0; i < path.getNodeCount(); i++) {
			out.add(helper.relativePos(path.getNodePos(i)).toShortString());
		}
		return "next " + path.getNextNodeIndex() + " of " + out;
	}

	/**
	 * A builder builds the Starter Cottage in the Grand style with Rechiseled: its floors and wall panels are Rechiseled's
	 * patterned dark oak planks and andesite, every block is right and nothing is skipped.
	 */
	//$ gametest_ticks_batch HUGE_AREA '24000' '"rechiseled_grand_cottage"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 24000, batch = "rechiseled_grand_cottage")
	public void theGrandStyleWithRechiseledBuildsWithNothingSkipped(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		ResourceLocation id = BlueprintStyles.styled(AliveWorkplace.id("starter_cottage"), "grand");
		Blueprint styled = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("no " + id));
		for (String want : List.of("rechiseled:dark_oak_planks_flooring", "rechiseled:andesite_squares")) {
			helper.assertTrue(styled.blocks().stream().anyMatch(e -> id(e.state()).equals(want)), "the Grand cottage has no " + want);
		}
		BuildSite site = start(helper, id);
		BuildPlan plan = site.plan(level);
		BlockPos origin = site.placement().origin();
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null,
				"still building: " + site.stage() + " " + site.status() + " missing " + site.missing());
			List<BlockPos> unfinished = plan.unfinished(level);
			helper.assertTrue(unfinished.isEmpty(), unfinished.size() + " wrong, e.g. " + unfinished.stream().limit(3)
				.map(p -> p.subtract(origin).toShortString() + "=" + level.getBlockState(p)).toList());
			helper.assertTrue(site.skipped() == 0, site.skipped() + " skipped");
			long patterned = styled.blocks().stream().filter(e -> id(e.state()).startsWith("rechiseled:"))
				.filter(e -> id(level.getBlockState(origin.offset(e.pos()))).equals(id(e.state()))).count();
			helper.assertTrue(patterned > 0, "no Rechiseled block stands");
		});
	}

	/**
	 * A builder on {@code id} at (9, 2, 3), his chests holding exactly what the plan costs (as in 37.5's tests). Chests,
	 * not barrels (B202): a barrel is the Fisherman's job site, and the Library test's jobless villager once took one of
	 * these supply barrels, a dozen blocks away, instead of the Library's lectern (because a stray villager had taken the
	 * lectern first, B245: see {@link #clearStrays}).
	 */
	private static BuildSite start(GameTestHelper helper, ResourceLocation id) {
		ServerLevel level = helper.getLevel();
		new WorkplaceConfig().apply();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, null, id, placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException("no blueprint " + id);
		}
		List<ItemStack> stock = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : plan.materials().entrySet()) {
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos chest = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(chest, Blocks.CHEST);
			Container container = helper.getBlockEntity(chest);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}
		return site;
	}

	/** An open floor spot within two blocks of {@code pos}. */
	private static BlockPos standNear(ServerLevel level, BlockPos pos) {
		for (BlockPos p : BlockPos.withinManhattan(pos, 2, 1, 2)) {
			if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isSolidRender(level, p.below())) {
				return p.immutable();
			}
		}
		throw new GameTestAssertException("no room beside the lectern at " + pos);
	}

	/**
	 * B245 (and B202): removes every villager but the builder from around the finished Library, letting go of the lectern
	 * first if one holds it. A villager left by an earlier batch (one the clear at the start missed, or that walked in
	 * from farther off during the build) took the lectern as soon as it was built, and the test's villager then found no
	 * free lectern: it stayed jobless (B245) or took a supply barrel (B202). In a world that's vanilla: a free lectern goes
	 * to the first jobless villager who finds it. This test is about the one it spawns beside the lectern.
	 */
	private static void clearStrays(GameTestHelper helper, BuildSite site, BlockPos lectern) {
		for (Villager other : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48),
				o -> !o.getUUID().equals(site.builder()))) {
			for (MemoryModuleType<GlobalPos> memory : List.of(MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE)) {
				if (other.getBrain().getMemory(memory).map(g -> g.pos().equals(lectern)).orElse(false)) {
					other.releasePoi(memory);
				}
			}
			other.discard();
		}
	}

	/** B245: why a villager hasn't become the Librarian: where they are, what they picked, who else holds the lectern. */
	private static String diagnose(GameTestHelper helper, Villager v, BlockPos lectern) {
		ServerLevel level = helper.getLevel();
		StringBuilder s = new StringBuilder("; villager ").append(v.isRemoved() ? "REMOVED " + v.getRemovalReason() : "alive")
			.append(" at ").append(helper.relativePos(v.blockPosition()).toShortString()).append(", lectern at ").append(helper.relativePos(lectern).toShortString())
			.append(", lectern tickets free ").append(level.getPoiManager().getFreeTickets(lectern))
			.append(", picked ").append(v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(g -> helper.relativePos(g.pos()).toShortString()).orElse("-"))
			.append(", walk ").append(v.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> helper.relativePos(w.getTarget().currentBlockPosition()).toShortString()).orElse("-"))
			.append(", activity ").append(v.getBrain().getActiveNonCoreActivity().map(Object::toString).orElse("-"))
			.append(", baby ").append(v.isBaby()).append(", day time ").append(level.getDayTime() % 24000)
			.append(", pos ").append(String.format("%.3f %.3f %.3f", v.getX(), v.getY(), v.getZ())).append(" on ground ").append(v.onGround())
			.append(" on ").append(id(level.getBlockState(v.getBlockPosBelowThatAffectsMyMovement())))
			.append(", path ").append(nodes(helper, v.getNavigation().getPath())); // B339: where the walk runs, if it is stuck
		for (Villager other : level.getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(64), o -> o != v)) {
			s.append("; other villager ").append(other.getVillagerData().getProfession()).append(" at ").append(helper.relativePos(other.blockPosition()).toShortString())
				.append(" job ").append(other.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> helper.relativePos(g.pos()).toShortString()).orElse("-"))
				.append(" picked ").append(other.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(g -> helper.relativePos(g.pos()).toShortString()).orElse("-"));
		}
		return s.toString();
	}

	private static boolean isPoi(BlockState state, ResourceKey<PoiType> type) {
		return PoiTypes.forState(state).map(h -> h.is(type)).orElse(false);
	}

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private static Block block(String id) {
		return BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(id)).orElseThrow(() -> new GameTestAssertException("no block " + id));
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.RandomJobs;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * QA lane B (2026-11-23), the shops rebuilt in Milestone 50 (50.4 Butcher's Shop and Winery I-III; 50.5 Ferry House and
 * the Watermill, its Create half left to the compat suite), written from their blind briefs
 * ({@code tools/blueprints/briefs/butchers_shop.md}, {@code winery.md}, {@code ferry_house.md}, {@code watermill.md}),
 * not from the designers' recipes. The walk and the build helpers are {@link QaRebuiltWorkshops1121bGameTests}'s.
 */
public class QaRebuiltShops1123bGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";

	private static Blueprint blueprint(GameTestHelper helper, StarterBlueprints.Entry entry) {
		return QaRebuiltWorkshops1121bGameTests.blueprint(helper, entry);
	}

	private static Map<BlockPos, BlockState> cells(Blueprint blueprint) {
		return QaRebuiltWorkshops1121bGameTests.cells(blueprint);
	}

	private static BlockState at(Map<BlockPos, BlockState> cells, BlockPos pos) {
		return QaRebuiltWorkshops1121bGameTests.at(cells, pos);
	}

	private static List<BlockPos> where(Map<BlockPos, BlockState> cells, Block block) {
		return QaRebuiltWorkshops1121bGameTests.where(cells, block);
	}

	private static String name(BlockState s) {
		return QaRebuiltWorkshops1121bGameTests.name(s);
	}

	// ---- the drawings, from the briefs ------------------------------------------------------------------------------

	/**
	 * ferry_house.md, "Must have": exactly one Travel Post and no other job block (no barrel, bell, ...); a villager walks
	 * from the street in front to a spot beside the post (doors open, gates shut); at least one bed, walked to; every
	 * chest within a player's reach of a walked spot; no trapdoor in the front walk row (z 1, y 0-1); no calcite; the plot
	 * at most 18 x 18 and 10 or 11 deep, the size as declared; no basement.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheFerryHouseHasOnePostABedAndAWayInToBoth(GameTestHelper helper) {
		List<String> p = new ArrayList<>();
		Blueprint bp = blueprint(helper, StarterBlueprints.FERRY_HOUSE);
		Map<BlockPos, BlockState> c = cells(bp);
		List<BlockPos> posts = where(c, ModBlocks.TRAVEL_POST);
		if (posts.size() != 1) {
			p.add(posts.size() + " travel posts " + posts + ", the brief says exactly 1");
		}
		Map<BlockPos, BlockState> others = QaRebuiltWorkshops1121bGameTests.jobSites(c);
		others.values().removeIf(s -> s.is(ModBlocks.TRAVEL_POST));
		if (!others.isEmpty()) {
			List<String> o = new ArrayList<>();
			others.forEach((pos, s) -> o.add(name(s) + " " + pos.toShortString()));
			p.add("other job blocks " + o);
		}
		Set<BlockPos> reached = QaRebuiltWorkshops1121bGameTests.walk(bp, c);
		for (BlockPos post : posts) {
			if (!QaRebuiltWorkshops1121bGameTests.beside(reached, post)) {
				p.add("the travel post at " + post.toShortString() + " has no spot beside it a villager walks to from the street");
			}
		}
		List<BlockPos> beds = QaRebuiltWorkshops1121bGameTests.bedHeads(c);
		if (beds.isEmpty()) {
			p.add("no bed (the brief: at least one, the Ferryman's)");
		}
		for (BlockPos h : beds) {
			if (!QaRebuiltWorkshops1121bGameTests.beside(reached, h) && !QaRebuiltWorkshops1121bGameTests.beside(reached, QaRebuiltWorkshops1121bGameTests.bedFoot(c, h))) {
				p.add("the bed at " + h.toShortString() + " can't be walked to from the street");
			}
		}
		Set<BlockPos> player = QaRebuiltWorkshops1121bGameTests.walk(bp, c, true);
		for (BlockPos chest : QaRebuiltWorkshops1121bGameTests.chests(c)) {
			if (!QaRebuiltWorkshops1121bGameTests.near(player, chest, 2)) {
				p.add("the chest at " + chest.toShortString() + " can't be walked up to");
			}
		}
		c.forEach((pos, s) -> {
			if (s.is(BlockTags.TRAPDOORS) && pos.getZ() == 1 && pos.getY() >= 0 && pos.getY() <= 1) {
				p.add("a trapdoor in the front walk row at " + pos.toShortString());
			}
			if (s.is(Blocks.CALCITE) || s.is(Blocks.BARREL) || s.is(Blocks.JIGSAW) || s.is(Blocks.STRUCTURE_VOID)) {
				p.add(name(s) + " at " + pos.toShortString());
			}
		});
		Vec3i size = bp.size();
		if (!size.equals(StarterBlueprints.FERRY_HOUSE.size()) || size.getX() > 18 || size.getY() > 18 || size.getZ() < 10 || size.getZ() > 11) {
			p.add("size " + size.toShortString() + " (declared " + StarterBlueprints.FERRY_HOUSE.size().toShortString() + "), the brief: at most 18 x 18, 10 or 11 deep");
		}
		if (bp.basement() != 0) {
			p.add("a basement " + bp.basement() + " deep, the brief: none");
		}
		helper.assertTrue(p.isEmpty(), "ferry_house: " + String.join("; ", p));
		helper.succeed();
	}

	/**
	 * butchers_shop.md, "The freezer": nothing under the ground that melts or flows (no plain or frosted ice, no snow
	 * layer, nothing waterlogged, no water or lava), at least one packed ice and a blue ice on the floor layer (y -4), a
	 * lantern down there; and the smoker "upstairs" (y 0 or higher: "a cellar never hands a villager a job"), exactly one,
	 * with a chest within 8 across and 4 up or down of it (without one the Butcher does no herding at all), no barrel or
	 * crafting table anywhere, and the plot at most 21 x 13 x 21 with the basement exactly 4.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheButchersFreezerHoldsNothingThatMeltsAndTheSmokerIsUpstairsWithItsChest(GameTestHelper helper) {
		List<String> p = new ArrayList<>();
		Blueprint bp = blueprint(helper, StarterBlueprints.BUTCHERS_SHOP);
		Map<BlockPos, BlockState> c = cells(bp);
		int packed = 0;
		boolean blueFloor = false;
		boolean lantern = false;
		for (Map.Entry<BlockPos, BlockState> e : c.entrySet()) {
			BlockPos pos = e.getKey();
			BlockState s = e.getValue();
			if (pos.getY() >= 0) {
				continue;
			}
			if (s.is(Blocks.ICE) || s.is(Blocks.FROSTED_ICE) || s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW) || !s.getFluidState().isEmpty()
				|| s.hasProperty(BlockStateProperties.WATERLOGGED) && s.getValue(BlockStateProperties.WATERLOGGED)) {
				p.add("the freezer has " + s + " at " + pos.toShortString() + " (it melts or flows)");
			}
			packed += s.is(Blocks.PACKED_ICE) ? 1 : 0;
			blueFloor |= s.is(Blocks.BLUE_ICE) && pos.getY() == -4;
			lantern |= s.is(Blocks.LANTERN);
		}
		if (packed == 0 || !blueFloor || !lantern) {
			p.add("the freezer: packed ice " + packed + " (want 1+), blue ice on y -4 " + blueFloor + ", a lantern " + lantern);
		}
		List<BlockPos> smokers = where(c, Blocks.SMOKER);
		List<BlockPos> chests = QaRebuiltWorkshops1121bGameTests.chests(c);
		if (smokers.size() != 1) {
			p.add(smokers.size() + " smokers " + smokers + ", want 1");
		}
		for (BlockPos s : smokers) {
			if (s.getY() < 0) {
				p.add("the smoker at " + s.toShortString() + " is in the cellar, the brief: above the ground");
			}
			if (chests.stream().noneMatch(ch -> QaRebuiltWorkshops1121bGameTests.within(ch, s, 8, 4))) {
				p.add("no chest within 8 across / 4 up or down of the smoker at " + s.toShortString());
			}
		}
		if (!where(c, Blocks.BARREL).isEmpty() || !where(c, Blocks.CRAFTING_TABLE).isEmpty()) {
			p.add("barrels " + where(c, Blocks.BARREL) + ", crafting tables " + where(c, Blocks.CRAFTING_TABLE));
		}
		Vec3i size = bp.size();
		if (size.getX() > 21 || size.getY() > 13 || size.getZ() > 21 || bp.basement() != 4) {
			p.add("size " + size.toShortString() + " basement " + bp.basement() + ", the brief: at most 21 x 13 x 21, basement exactly 4");
		}
		helper.assertTrue(p.isEmpty(), "butchers_shop: " + String.join("; ", p));
		helper.succeed();
	}

	/**
	 * winery.md, every level: the vat is the one job site in the file and a plain empty cauldron, at y 3 or higher; at least
	 * one chest within 8 across / 4 up or down of it (with none the Vintner makes nothing); at least 4 chests and 12
	 * spruce-log casks lying on their side below the vat's y - 1; no barrel; I without sweet berry bushes and II, III with
	 * 16 or more and 6 or more cave vines bearing berries; the vat and every chest in the same cell in the next level; III
	 * above the ground II's blocks except a trapdoor; III's cellar 4 deep and no job site in it.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaEveryWineryLevelHasItsVatCasksChestsAndGardenAndKeepsThemUp(GameTestHelper helper) {
		List<String> p = new ArrayList<>();
		List<StarterBlueprints.Entry> levels = List.of(StarterBlueprints.WINERY, StarterBlueprints.WINERY_2, StarterBlueprints.WINERY_3);
		Map<BlockPos, BlockState> lower = null;
		for (StarterBlueprints.Entry e : levels) {
			String n = e.id().getPath();
			Blueprint bp = blueprint(helper, e);
			Map<BlockPos, BlockState> c = cells(bp);
			Map<BlockPos, BlockState> sites = QaRebuiltWorkshops1121bGameTests.jobSites(c);
			List<BlockPos> vats = new ArrayList<>(sites.keySet());
			if (sites.size() != 1 || !sites.values().iterator().next().is(Blocks.CAULDRON)) {
				List<String> o = new ArrayList<>();
				sites.forEach((pos, s) -> o.add(name(s) + " " + pos.toShortString()));
				p.add(n + ": job sites " + o + ", want exactly one plain cauldron");
				continue;
			}
			BlockPos vat = vats.get(0);
			List<BlockPos> chests = QaRebuiltWorkshops1121bGameTests.chests(c);
			long below = chests.stream().filter(ch -> ch.getY() < vat.getY() - 1).count();
			long casks = c.entrySet().stream().filter(x -> x.getKey().getY() < vat.getY() - 1 && x.getValue().is(Blocks.SPRUCE_LOG)
				&& x.getValue().getValue(BlockStateProperties.AXIS) != net.minecraft.core.Direction.Axis.Y).count();
			if (vat.getY() < 3 || below < 4 || casks < 12) {
				p.add(n + ": vat at " + vat.toShortString() + " (y 3+), " + below + " chests (4+) and " + casks + " casks (12+) under it");
			}
			if (chests.stream().noneMatch(ch -> QaRebuiltWorkshops1121bGameTests.within(ch, vat, 8, 4))) {
				p.add(n + ": no chest within 8 / 4 of the vat " + vat.toShortString());
			}
			if (!where(c, Blocks.BARREL).isEmpty()) {
				p.add(n + ": barrels " + where(c, Blocks.BARREL));
			}
			long bushes = where(c, Blocks.SWEET_BERRY_BUSH).size();
			long vines = c.values().stream().filter(s -> s.is(Blocks.CAVE_VINES) && s.getValue(CaveVines.BERRIES)).count();
			if (e == StarterBlueprints.WINERY ? bushes != 0 : bushes < 16 || vines < 6) {
				p.add(n + ": " + bushes + " sweet berry bushes and " + vines + " glow-berry vines");
			}
			if (lower != null) {
				Map<BlockPos, BlockState> up = c;
				List<String> lost = new ArrayList<>();
				lower.forEach((pos, s) -> {
					if ((s.getBlock() instanceof ChestBlock || s.is(Blocks.CAULDRON)) && !at(up, pos).is(s.getBlock())) {
						lost.add(name(s) + " " + pos.toShortString() + " -> " + name(at(up, pos)));
					}
				});
				if (!lost.isEmpty()) {
					p.add(n + " moves or drops the level below's " + lost);
				}
			}
			if (e == StarterBlueprints.WINERY_3) {
				Map<BlockPos, BlockState> two = lower;
				List<String> diff = new ArrayList<>();
				for (BlockPos pos : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(bp.size().getX() - 1, bp.size().getY() - 1, bp.size().getZ() - 1))) {
					BlockState a = at(two, pos);
					BlockState b = at(c, pos);
					if (!a.equals(b) && !(pos.getY() == 0 && b.is(BlockTags.TRAPDOORS))) {
						diff.add(pos.toShortString() + " " + name(a) + " -> " + name(b));
					}
				}
				if (!diff.isEmpty()) {
					p.add("winery_3 above the ground isn't winery_2: " + diff.stream().limit(6).toList() + (diff.size() > 6 ? " and " + (diff.size() - 6) + " more" : ""));
				}
				if (bp.basement() != 4 || vat.getY() < 0) {
					p.add("winery_3: basement " + bp.basement() + " (want 4), vat at " + vat.toShortString());
				}
			}
			lower = c;
		}
		helper.assertTrue(p.isEmpty(), String.join("; ", p));
		helper.succeed();
	}

	/**
	 * watermill.md without Create (the main suite: Create's blocks are air): exactly one composter (the Farmer's job
	 * block) and no other job site, a spot beside it a villager walks to from the street, a chest within 8 across / 4 up
	 * or down of it (the store he mills from), every chest walked up to; the plot at most 17 x 16 x 17, as declared.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheWatermillWithoutCreateKeepsItsComposterAndStoreChestOnFoot(GameTestHelper helper) {
		List<String> p = new ArrayList<>();
		Blueprint bp = blueprint(helper, StarterBlueprints.WATERMILL);
		Map<BlockPos, BlockState> c = cells(bp);
		Map<BlockPos, BlockState> sites = QaRebuiltWorkshops1121bGameTests.jobSites(c);
		List<BlockPos> composters = where(c, Blocks.COMPOSTER);
		if (composters.size() != 1 || sites.size() != 1) {
			List<String> o = new ArrayList<>();
			sites.forEach((pos, s) -> o.add(name(s) + " " + pos.toShortString()));
			p.add("job sites " + o + ", the brief: one composter and nothing else");
		}
		Set<BlockPos> reached = QaRebuiltWorkshops1121bGameTests.walk(bp, c);
		Set<BlockPos> player = QaRebuiltWorkshops1121bGameTests.walk(bp, c, true);
		List<BlockPos> chests = QaRebuiltWorkshops1121bGameTests.chests(c);
		for (BlockPos k : composters) {
			if (!QaRebuiltWorkshops1121bGameTests.beside(reached, k)) {
				p.add("the composter at " + k.toShortString() + " has no spot beside it a villager walks to");
			}
			if (chests.stream().noneMatch(ch -> QaRebuiltWorkshops1121bGameTests.within(ch, k, 8, 4))) {
				p.add("no chest within 8 / 4 of the composter at " + k.toShortString());
			}
		}
		for (BlockPos ch : chests) {
			if (!QaRebuiltWorkshops1121bGameTests.near(player, ch, 2)) {
				p.add("the chest at " + ch.toShortString() + " can't be walked up to");
			}
		}
		Vec3i size = bp.size();
		if (!size.equals(StarterBlueprints.WATERMILL.size()) || size.getX() > 17 || size.getY() > 16 || size.getZ() > 17) {
			p.add("size " + size.toShortString() + " (declared " + StarterBlueprints.WATERMILL.size().toShortString() + "), the brief: at most 17 x 16 x 17");
		}
		helper.assertTrue(p.isEmpty(), "watermill: " + String.join("; ", p));
		helper.succeed();
	}

	// ---- live -------------------------------------------------------------------------------------------------------

	/** Places {@code entry} turned by {@code rotation} with its ground floor at helper y 2 + basement (stone under it); returns the ground floor's origin. */
	private static BlockPos placeTurned(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos at, Rotation rotation) {
		ServerLevel level = helper.getLevel();
		int b = entry.basement();
		for (int y = 2; y < 2 + b; y++) {
			for (int x = 0; x < 30; x++) {
				for (int z = 0; z < 30; z++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
				}
			}
		}
		StructureTemplate template = level.getStructureManager().get(entry.id()).orElseThrow();
		BlockPos origin = helper.absolutePos(at);
		template.placeInWorld(level, origin, origin, new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE),
			net.minecraft.util.RandomSource.create(1123L), 2);
		return origin.above(b);
	}

	/** Every {@code block} in the turned footprint of {@code entry} at {@code origin} (its ground floor). */
	private static List<BlockPos> inTurned(ServerLevel level, StarterBlueprints.Entry entry, BlockPos origin, Rotation rotation, Block block) {
		Vec3i s = entry.size();
		BlockPos far = StructureTemplate.transform(new BlockPos(s.getX() - 1, 0, s.getZ() - 1), Mirror.NONE, rotation, BlockPos.ZERO);
		BlockPos a = origin.offset(Math.min(0, far.getX()), -entry.basement(), Math.min(0, far.getZ()));
		BlockPos z = origin.offset(Math.max(0, far.getX()), s.getY() - 1, Math.max(0, far.getZ()));
		return BlockPos.betweenClosedStream(a, z).filter(p -> level.getBlockState(p).is(block)).map(BlockPos::immutable).toList();
	}

	/**
	 * The drawing turned by {@code rotation}, one villager in the street three blocks before its turned front at the
	 * brief's template x: jobless ({@code arrives} null) or of the job; within the time he holds the turned build's
	 * {@code block} as his JOB_SITE and his job is {@code job}. Random station jobs off.
	 */
	private static void takesTurned(GameTestHelper helper, StarterBlueprints.Entry entry, BlockPos at, Rotation rotation, int x, Block block,
		VillagerProfession arrives, VillagerProfession job) {
		Leftovers.clear(helper);
		boolean random = RandomJobs.ENABLED;
		RandomJobs.ENABLED = false;
		Leftovers.after(helper, () -> RandomJobs.ENABLED = random);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos origin = placeTurned(helper, entry, at, rotation);
		List<BlockPos> sites = inTurned(level, entry, origin, rotation, block);
		helper.assertTrue(sites.size() == 1, "(setup) " + sites.size() + " " + block.getName().getString() + " placed turned");
		BlockPos street = origin.offset(StructureTemplate.transform(new BlockPos(x, 0, -3), Mirror.NONE, rotation, BlockPos.ZERO));
		Villager v = helper.spawn(EntityType.VILLAGER, street.subtract(helper.absolutePos(BlockPos.ZERO)));
		v.setVillagerData(v.getVillagerData().setProfession(arrives == null ? VillagerProfession.NONE : arrives));
		if (arrives != null) {
			v.setVillagerXp(1);
			v.refreshBrain(level);
		}
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 9000) {
				helper.setDayTime(2000);
			}
		});
		String n = entry.id().getPath() + " turned " + rotation;
		helper.succeedWhen(() -> {
			helper.assertTrue(v.isAlive(), "the villager died");
			BlockPos site = v.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
			if (site == null || !sites.contains(site)) {
				var path = v.getNavigation().createPath(sites.get(0), 1);
				helper.assertTrue(false, n + ": the villager holds no " + block.getName().getString() + " yet: job "
					+ v.getVillagerData().getProfession() + ", site " + site + ", potential " + v.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE)
					+ ", at (template) " + v.blockPosition().subtract(origin).toShortString() + ", block at " + sites.get(0).subtract(origin).toShortString()
					+ ", free tickets " + level.getPoiManager().getFreeTickets(sites.get(0)) + ", poi " + level.getPoiManager().getType(sites.get(0)).map(h -> h.unwrapKey().toString()).orElse("none")
					+ ", path " + (path == null ? "none" : (path.canReach() ? "reaches" : "ends at " + path.getEndNode().asBlockPos().subtract(origin).toShortString())));
			}
			helper.assertTrue(v.getVillagerData().getProfession() == job, n + ": holds it as " + v.getVillagerData().getProfession() + ", wanted " + job);
			v.discard();
		});
	}

	/** ferry_house.md, turned as the Steward turns it on a shore (front to the west): a jobless villager in front takes the post. */
	//$ gametest_ticks_batch HUGE '2400' '"qaShops1123bFerryTurned"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaShops1123bFerryTurned")
	public void qaAJoblessVillagerTakesTheTurnedFerryHousesPost(GameTestHelper helper) {
		takesTurned(helper, StarterBlueprints.FERRY_HOUSE, new BlockPos(8, 2, 22), Rotation.COUNTERCLOCKWISE_90,
			(StarterBlueprints.FERRY_HOUSE.size().getX() - 1) / 2, ModBlocks.TRAVEL_POST, null, ModVillagers.FERRYMAN);
	}

	/** winery.md, turned a quarter clockwise (front to the east): the Vintner from the brief's spot claims the vat upstairs. */
	//$ gametest_ticks_batch HUGE '2400' '"qaShops1123bWineryTurned"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaShops1123bWineryTurned")
	public void qaTheVintnerClaimsTheTurnedWineryIsVat(GameTestHelper helper) {
		takesTurned(helper, StarterBlueprints.WINERY, new BlockPos(20, 2, 6), Rotation.CLOCKWISE_90, 1, Blocks.CAULDRON,
			ModVillagers.VINTNER, ModVillagers.VINTNER);
	}

	/**
	 * ferry_house.md, "What it is for": the post is "both his held and his acquirable job site, so a jobless villager
	 * takes a free post by himself". Unturned, a jobless villager waiting at the brief's spot (6, 0, -3) takes it and
	 * becomes the Ferryman (the existing build test sends a Ferryman already in the job).
	 */
	//$ gametest_ticks_batch HUGE '2400' '"qaShops1123bFerryJobless"'
	@GameTest(template = HUGE, timeoutTicks = 2400, batch = "qaShops1123bFerryJobless")
	public void qaAJoblessVillagerInFrontBecomesTheFerrymanAtTheFerryHouse(GameTestHelper helper) {
		takesTurned(helper, StarterBlueprints.FERRY_HOUSE, new BlockPos(8, 2, 8), Rotation.NONE, 6, ModBlocks.TRAVEL_POST, null, ModVillagers.FERRYMAN);
	}

	/**
	 * butchers_shop.md, turned half round (front to the north), cellar and all: from the street in front of the turned
	 * front, the game's pathfinder (as a villager claiming a job site: to within one block) reaches the smoker upstairs,
	 * and (to within two) a chest in its supply reach. (A villager left to claim it by himself strolled off to the far
	 * corner of the area in two of four runs and found no path back to it within 2,400 ticks; reported, not asserted.)
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaShops1123bButcherTurned"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaShops1123bButcherTurned")
	public void qaAVillagerInFrontOfTheTurnedButchersShopFindsAPathToItsSmokerAndChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		StarterBlueprints.Entry entry = StarterBlueprints.BUTCHERS_SHOP;
		Rotation rotation = Rotation.CLOCKWISE_180;
		BlockPos origin = placeTurned(helper, entry, new BlockPos(24, 2, 22), rotation);
		List<BlockPos> smokers = inTurned(level, entry, origin, rotation, Blocks.SMOKER);
		helper.assertTrue(smokers.size() == 1, "(setup) " + smokers.size() + " smokers placed turned");
		BlockPos smoker = smokers.get(0);
		List<BlockPos> chests = inTurned(level, entry, origin, rotation, Blocks.CHEST).stream()
			.filter(c -> QaRebuiltWorkshops1121bGameTests.within(c, smoker, 8, 4)).toList();
		BlockPos street = origin.offset(StructureTemplate.transform(new BlockPos((entry.size().getX() - 1) / 2, 0, -3), Mirror.NONE, rotation, BlockPos.ZERO));
		Villager walker = helper.spawn(EntityType.VILLAGER, street.subtract(helper.absolutePos(BlockPos.ZERO)));
		walker.setNoAi(true);
		helper.runAfterDelay(10, () -> {
			walker.setOnGround(true);
			var path = walker.getNavigation().createPath(Set.of(smoker), 1);
			helper.assertTrue(path != null && path.canReach(), "turned butchers_shop: no path from the street to the smoker (world offset "
				+ smoker.subtract(origin).toShortString() + ")" + (path == null ? " (none)" : " (ends at " + path.getEndNode().asBlockPos().subtract(origin).toShortString() + ")"));
			boolean any = chests.stream().map(c -> walker.getNavigation().createPath(Set.of(c), 2)).anyMatch(p -> p != null && p.canReach());
			helper.assertTrue(any, "turned butchers_shop: no path to any of the " + chests.size() + " chests in the smoker's reach");
			walker.discard();
			helper.succeed();
		});
	}

	/**
	 * ferry_house.md, "Room round the post": an arriving passenger is put on the first standable cell within 3 across and 2
	 * down to 3 up of the post ({@code Ferrymen.landing}'s search, repeated here with {@link Walker#canStand}) in the
	 * placed house; that cell must be one a player walks to from the street (not a roof, a wall cavity or under the floor).
	 */
	//$ gametest_ticks_batch HUGE '200' '"qaShops1123bFerryLanding"'
	@GameTest(template = HUGE, timeoutTicks = 200, batch = "qaShops1123bFerryLanding")
	public void qaAPassengerLandingAtTheFerryHouseCanWalkOutToTheStreet(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = placeTurned(helper, StarterBlueprints.FERRY_HOUSE, new BlockPos(9, 2, 9), Rotation.NONE);
		List<BlockPos> posts = inTurned(level, StarterBlueprints.FERRY_HOUSE, origin, Rotation.NONE, ModBlocks.TRAVEL_POST);
		helper.assertTrue(posts.size() == 1, "(setup) " + posts.size() + " travel posts placed");
		BlockPos post = posts.get(0);
		BlockPos landing = null;
		for (int r = 1; r <= 3 && landing == null; r++) {
			for (BlockPos q : BlockPos.betweenClosed(post.offset(-r, -2, -r), post.offset(r, 3, r))) {
				if (Walker.canStand(level, q)) {
					landing = q.immutable();
					break;
				}
			}
		}
		helper.assertTrue(landing != null, "no cell within 3 of the post a passenger can stand on: he lands on top of the post");
		Blueprint bp = blueprint(helper, StarterBlueprints.FERRY_HOUSE);
		Set<BlockPos> player = QaRebuiltWorkshops1121bGameTests.walk(bp, cells(bp), true);
		BlockPos t = landing.subtract(origin);
		helper.assertTrue(player.contains(t), "a passenger lands at " + t.toShortString() + " (template; post at " + post.subtract(origin).toShortString()
			+ "), a cell nobody walks to from the street");
		helper.succeed();
	}

	/**
	 * winery.md, Level II's garden: "each must be able to survive where it stands, since the build is pasted and
	 * updated". Winery II placed in the world: every sweet berry bush and every cave vine can survive in its cell, and
	 * after 100 ticks (block updates, random ticks) at least 16 bushes and 6 vines bearing berries still stand.
	 */
	//$ gametest_ticks_batch HUGE '400' '"qaShops1123bWineryGarden"'
	@GameTest(template = HUGE, timeoutTicks = 400, batch = "qaShops1123bWineryGarden")
	public void qaWineryIIsBerryGardenStaysWhereItIsPlanted(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos origin = placeTurned(helper, StarterBlueprints.WINERY_2, new BlockPos(6, 2, 6), Rotation.NONE);
		List<String> dead = new ArrayList<>();
		List<BlockPos> plants = new ArrayList<>(inTurned(level, StarterBlueprints.WINERY_2, origin, Rotation.NONE, Blocks.SWEET_BERRY_BUSH));
		plants.addAll(inTurned(level, StarterBlueprints.WINERY_2, origin, Rotation.NONE, Blocks.CAVE_VINES));
		plants.addAll(inTurned(level, StarterBlueprints.WINERY_2, origin, Rotation.NONE, Blocks.CAVE_VINES_PLANT));
		for (BlockPos q : plants) {
			if (!level.getBlockState(q).canSurvive(level, q)) {
				dead.add(name(level.getBlockState(q)) + " " + q.subtract(origin).toShortString());
			}
		}
		helper.assertTrue(dead.isEmpty(), "winery_2: plants that can't survive where they're drawn " + dead);
		helper.runAfterDelay(100, () -> {
			int bushes = inTurned(level, StarterBlueprints.WINERY_2, origin, Rotation.NONE, Blocks.SWEET_BERRY_BUSH).size();
			long vines = inTurned(level, StarterBlueprints.WINERY_2, origin, Rotation.NONE, Blocks.CAVE_VINES).stream()
				.filter(q -> level.getBlockState(q).getValue(CaveVines.BERRIES)).count();
			helper.assertTrue(bushes >= 16 && vines >= 6, "winery_2 after 100 ticks: " + bushes + " bushes (16+), " + vines + " vines with berries (6+)");
			helper.succeed();
		});
	}

	/**
	 * ferry_house.md in the world: a real Builder builds the Ferry House from exactly its material list (the plan's), saved
	 * and loaded mid-STRUCTURE; DONE, every block right, nothing left over; one Travel Post, a job site, and a bed built.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaShops1123bFerryBuild"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaShops1123bFerryBuild")
	public void qaABuilderBuildsTheFerryHouseAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaRebuiltWorkshops1121bGameTests.buildAcrossAReload(helper, StarterBlueprints.FERRY_HOUSE, new BlockPos(9, 2, 9), placement -> {
			Vec3i size = StarterBlueprints.FERRY_HOUSE.size();
			List<BlockPos> posts = QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, ModBlocks.TRAVEL_POST);
			helper.assertTrue(posts.size() == 1, "the built Ferry House has " + posts.size() + " travel posts");
			helper.assertTrue(level.getPoiManager().getType(posts.get(0)).isPresent(), "the built travel post is no job site");
			boolean bed = false;
			for (BlockPos q : BlockPos.betweenClosed(placement.origin(), placement.origin().offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1))) {
				bed |= level.getBlockState(q).is(BlockTags.BEDS);
			}
			helper.assertTrue(bed, "the built Ferry House has no bed");
		});
	}

	/**
	 * winery.md in the world: a real Builder builds Winery I from exactly its material list across a save and reload;
	 * DONE, every block right, nothing left over; one cauldron, a job site, with a built chest within 8 / 4 of it.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaShops1123bWineryBuild"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaShops1123bWineryBuild")
	public void qaABuilderBuildsWineryIAcrossAReloadFromExactlyItsMaterials(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		QaRebuiltWorkshops1121bGameTests.buildAcrossAReload(helper, StarterBlueprints.WINERY, new BlockPos(9, 2, 9), placement -> {
			Vec3i size = StarterBlueprints.WINERY.size();
			List<BlockPos> vats = QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.CAULDRON);
			helper.assertTrue(vats.size() == 1, "the built Winery has " + vats.size() + " cauldrons");
			helper.assertTrue(level.getPoiManager().getType(vats.get(0)).isPresent(), "the built cauldron is no job site");
			helper.assertTrue(QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), size, Blocks.CHEST).stream()
				.anyMatch(c -> QaRebuiltWorkshops1121bGameTests.within(c, vats.get(0), 8, 4)), "no built chest within 8 / 4 of the vat");
		});
	}

	/**
	 * ferry_house.md, a missing material: a Builder stocked with every material but the Travel Post builds the rest, then
	 * waits for the post (WAITING_FOR_MATERIALS, the post in his missing list) instead of finishing without the job block;
	 * once a player puts the post in his chest he finishes, every block right, the post placed.
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaShops1123bFerryMissing"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaShops1123bFerryMissing")
	public void qaAFerryHouseShortOfItsTravelPostWaitsForItThenFinishes(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		QaRebuiltWorkshops1121bGameTests.rules(helper);
		StarterBlueprints.Entry entry = StarterBlueprints.FERRY_HOUSE;
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 9)), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		BuildPlan plan = BuildPlan.create(blueprint(helper, entry), placement);
		Item post = ModBlocks.TRAVEL_POST.asItem();
		Map<Item, Integer> needs = new LinkedHashMap<>(plan.materials());
		helper.assertTrue(needs.getOrDefault(post, 0) == 1, "(setup) the plan wants " + needs.get(post) + " travel posts");
		List<ItemStack> stock = new ArrayList<>();
		needs.forEach((item, count) -> {
			if (item == post) {
				return;
			}
			for (int left = count; left > 0; ) {
				int k = Math.min(left, item.getDefaultMaxStackSize());
				stock.add(new ItemStack(item, k));
				left -= k;
			}
		});
		QaRebuiltWorkshops1121bGameTests.stock(helper, stock);
		helper.setBlock(QaRebuiltWorkshops1121bGameTests.BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(QaRebuiltWorkshops1121bGameTests.CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(QaRebuiltWorkshops1121bGameTests.BENCH));
		BuildSite site = Builders.start(level, builder, null, entry.id(), placement);
		helper.assertTrue(site != null, "the Ferry House didn't start");
		UUID id = site.id();
		boolean[] waited = {false};
		String[] early = {null};
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
			BuildSite s = BuildSiteManager.get(level).get(id);
			if (s == null) {
				if (!waited[0] && early[0] == null) {
					early[0] = "the site ended without ever waiting for the travel post; post placed: "
						+ !QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), entry.size(), ModBlocks.TRAVEL_POST).isEmpty();
				}
				return;
			}
			if (!waited[0] && s.status() == BuildSite.Status.WAITING_FOR_MATERIALS && s.missing().containsKey(post)) {
				waited[0] = true;
				((Container) helper.getBlockEntity(QaRebuiltWorkshops1121bGameTests.CHEST)).setItem(0, new ItemStack(post));
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(early[0] == null, early[0] == null ? "" : early[0]);
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(waited[0], s == null ? "gone" : "never waited for the post yet: " + s.stage() + " " + s.status() + ", missing " + s.missing());
			helper.assertTrue(s == null, s == null ? "" : "still building: " + s.stage() + " " + s.status() + ", missing " + s.missing());
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) wrong after the build, e.g. "
				+ wrong.stream().limit(5).map(q -> q.subtract(placement.origin()).toShortString() + "=" + level.getBlockState(q)).toList());
			helper.assertTrue(QaRebuiltWorkshops1121bGameTests.built(level, placement.origin(), entry.size(), ModBlocks.TRAVEL_POST).size() == 1,
				"the travel post wasn't placed");
		});
	}

	/**
	 * winery.md, "Kept cells": "every chest stays in the same cell ... and the vat stays where it is". Winery I stands
	 * finished, a Vintner holds its cauldron (the POI ticket taken, his JOB_SITE on it) and a player's apples sit in a
	 * chest within reach of the vat. A Builder stocked with exactly what the upgrade still needs builds Level II over it.
	 * DONE: every block of II right; the vat still there and still taken (never broken and re-placed), the Vintner still a
	 * Vintner holding it; the chest still holds the apples; and II's garden is up (16+ sweet berry bushes).
	 */
	//$ gametest_ticks_batch HUGE '40000' '"qaShops1123bWineryUpgrade"'
	@GameTest(template = HUGE, timeoutTicks = 40000, batch = "qaShops1123bWineryUpgrade")
	public void qaUpgradingAWineryToIIKeepsTheClaimedVatAndThePlayersChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		QaRebuiltWorkshops1121bGameTests.rules(helper);
		BlockPos origin = helper.absolutePos(new BlockPos(9, 2, 8));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		level.getStructureManager().get(StarterBlueprints.WINERY.id()).orElseThrow()
			.placeInWorld(level, origin, origin, new StructurePlaceSettings(), net.minecraft.util.RandomSource.create(1123L), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.WINERY.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(placement));
		List<BlockPos> vats = QaRebuiltWorkshops1121bGameTests.built(level, origin, StarterBlueprints.WINERY.size(), Blocks.CAULDRON);
		helper.assertTrue(vats.size() == 1, "(setup) Winery I placed with " + vats.size() + " cauldrons");
		BlockPos vat = vats.get(0);
		Optional<BlockPos> near = QaRebuiltWorkshops1121bGameTests.built(level, origin, StarterBlueprints.WINERY.size(), Blocks.CHEST).stream()
			.filter(c -> QaRebuiltWorkshops1121bGameTests.within(c, vat, 8, 4)).findFirst();
		helper.assertTrue(near.isPresent(), "(setup) no chest within 8 / 4 of the vat");
		BlockPos chest = near.get();
		helper.assertTrue(level.getPoiManager().take(h -> true, (h, q) -> q.equals(vat), vat, 1).isPresent(), "(setup) the cauldron couldn't be claimed");
		Villager vintner = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		vintner.setNoAi(true);
		vintner.setVillagerData(vintner.getVillagerData().setProfession(ModVillagers.VINTNER));
		vintner.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), vat));
		((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.APPLE, 37));
		helper.setBlock(QaRebuiltWorkshops1121bGameTests.BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(QaRebuiltWorkshops1121bGameTests.CHEST, Blocks.CHEST);
		var report = BlueprintSupplies.check(level, new BlueprintData(StarterBlueprints.WINERY_2.id(), Optional.of(StarterBlueprints.WINERY_2.size()),
			Optional.of(placement))).orElseThrow(() -> new AssertionError("no supply report for the upgrade"));
		List<ItemStack> stock = new ArrayList<>();
		for (var m : report.missing()) {
			for (int left = m.count(); left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stock.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(!stock.isEmpty(), "(setup) the upgrade needs nothing: " + report);
		QaRebuiltWorkshops1121bGameTests.stock(helper, stock);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Builders.employ(level, builder, helper.absolutePos(QaRebuiltWorkshops1121bGameTests.BENCH));
		BuildSite site = Builders.start(level, builder, null, StarterBlueprints.WINERY_2.id(), placement);
		helper.assertTrue(site != null, "Winery II didn't start over the finished Level I");
		UUID id = site.id();
		helper.onEachTick(() -> {
			if (level.getDayTime() % 24000 > 11000) {
				helper.setDayTime(2000);
			}
		});
		helper.succeedWhen(() -> {
			BuildSite s = BuildSiteManager.get(level).get(id);
			helper.assertTrue(s == null, s == null ? "" : "still building: " + s.stage() + " " + s.status() + ", " + s.placed() + " placed, missing " + s.missing());
			BuildPlan plan = BuildPlan.create(blueprint(helper, StarterBlueprints.WINERY_2), placement);
			List<BlockPos> wrong = plan.unfinished(level);
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of Winery II wrong, e.g. "
				+ wrong.stream().limit(5).map(q -> q.subtract(origin).toShortString() + "=" + level.getBlockState(q)).toList());
			String v = vat.subtract(origin).toShortString();
			helper.assertTrue(level.getBlockState(vat).is(Blocks.CAULDRON), "the claimed vat at " + v + " is gone after the upgrade");
			helper.assertTrue(level.getPoiManager().getFreeTickets(vat) == 0, "the claimed vat at " + v + " was freed by the upgrade (broken and re-placed)");
			helper.assertTrue(vintner.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(vat::equals).isPresent(),
				"the Vintner lost his job site: " + vintner.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.assertTrue(vintner.getVillagerData().getProfession() == ModVillagers.VINTNER, "the Vintner lost his job");
			ItemStack kept = level.getBlockEntity(chest) instanceof Container box ? box.getItem(0) : ItemStack.EMPTY;
			helper.assertTrue(kept.is(Items.APPLE) && kept.getCount() == 37, "the player's chest at " + chest.subtract(origin).toShortString()
				+ " lost its apples in the upgrade: " + level.getBlockState(chest) + " holds " + kept);
			int bushes = QaRebuiltWorkshops1121bGameTests.built(level, origin, StarterBlueprints.WINERY_2.size(), Blocks.SWEET_BERRY_BUSH).size();
			helper.assertTrue(bushes >= 16, "Winery II built over I has " + bushes + " sweet berry bushes, want 16+");
			vintner.discard();
		});
	}
}

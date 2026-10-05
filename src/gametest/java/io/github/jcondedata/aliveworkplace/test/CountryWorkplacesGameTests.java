package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.fish.Fishers;
import io.github.jcondedata.aliveworkplace.fish.FishingBobber;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 27.14, new workplaces II: the Farmstead, Fisher's Hut and Weaver's Cottage (each with its II) and the Bandstand.
 * Their {@code workplace_*} rules in 27.11's form; each of the seven built by a builder from its Blueprint Table blueprint,
 * every worker of it then taking a job block of his own inside it; the Farmstead's farmer then works its field (the crops
 * ripe, he harvests them into the chest), and the Fisher's Hut is built on a shore, its jetty's posts down to the bed and
 * its deck over open water, and its fisherman fishes from the end of the jetty. The Bandstand adds 3 to the beauty.
 */
public class CountryWorkplacesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";

	static final List<WorkplacesGameTests.Case> CASES = List.of(
		new WorkplacesGameTests.Case("workplace_farmstead", "minecraft:farmer", "farmstead", "farms", VillageRanks.Rank.HAMLET, false),
		new WorkplacesGameTests.Case("workplace_fishers_hut", "minecraft:fisherman", "fishers_hut", "farms", VillageRanks.Rank.HAMLET, false),
		new WorkplacesGameTests.Case("workplace_weavers_cottage", "minecraft:shepherd", "weavers_cottage", "workshops", VillageRanks.Rank.HAMLET, false),
		new WorkplacesGameTests.Case("workplace_bandstand", "aliveworkplace:bard", "bandstand", "gardens", VillageRanks.Rank.HAMLET, false));

	/** Each rule: the worker staged without a workstation, the building asked for in its zone (27.11's checks). */
	@GameTestGenerator
	public Collection<TestFunction> rules() {
		List<TestFunction> out = new ArrayList<>();
		for (WorkplacesGameTests.Case c : CASES) {
			String name = "countryWorkplaceRule_" + c.rule().substring("workplace_".length()) + "_" + c.job().substring(c.job().indexOf(':') + 1);
			out.add(new TestFunction(name, name, AREA, 60, 0, true, helper -> WorkplacesGameTests.rule(helper, c)));
		}
		return out;
	}

	/** The seven are in the Blueprint Table's library, the three with an upgrade drawn as tiers, and on the Village Map. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void countryWorkplacesAreInTheBlueprintTable(GameTestHelper helper) {
		List<ResourceLocation> library = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		Map<StarterBlueprints.Entry, VillageMaps.Kind> kinds = Map.of(
			StarterBlueprints.FARMSTEAD, VillageMaps.Kind.FARMS, StarterBlueprints.FARMSTEAD_2, VillageMaps.Kind.FARMS,
			StarterBlueprints.FISHERS_HUT, VillageMaps.Kind.FARMS, StarterBlueprints.FISHERS_HUT_2, VillageMaps.Kind.FARMS,
			StarterBlueprints.WEAVERS_COTTAGE, VillageMaps.Kind.WORKSHOPS, StarterBlueprints.WEAVERS_COTTAGE_2, VillageMaps.Kind.WORKSHOPS,
			StarterBlueprints.BANDSTAND, VillageMaps.Kind.DECORATIONS);
		kinds.forEach((e, kind) -> {
			helper.assertTrue(library.contains(e.id()), e.id() + " isn't in the Blueprint Table");
			helper.assertTrue(e == StarterBlueprints.BANDSTAND ? StarterBlueprints.ONE_TIER.contains(e) : StarterBlueprints.ALL.contains(e),
				e.id() + " isn't a starter blueprint");
			helper.assertTrue(VillageMaps.kindOf(e.id()).orElse(null) == kind, e.id() + " is on the map as " + VillageMaps.kindOf(e.id()));
		});
		helper.succeed();
	}

	/** A finished Bandstand near a hall adds 3 to its village's beauty (as a Gazebo does); its base counts, not its style. */
	//$ gametest_ticks_batch AREA '20' '"countryBandstandBeauty"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "countryBandstandBeauty")
	public void aBandstandAddsThreeToTheBeauty(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(3, 2, 3), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(3, 2, 3));
		Leftovers.after(helper, () -> level.removeBlock(hall, false));
		helper.assertTrue(Decorations.points(StarterBlueprints.BANDSTAND.id()) == 3, "a Bandstand is worth " + Decorations.points(StarterBlueprints.BANDSTAND.id()));
		int before = Decorations.beauty(level, hall);
		BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(8, 2, 8)), Rotation.NONE, Mirror.NONE);
		BuildSiteManager manager = BuildSiteManager.get(level);
		manager.recordFinished(StarterBlueprints.BANDSTAND.id(), at, UUID.randomUUID());
		Leftovers.after(helper, () -> manager.forgetFinished(at));
		int after = Decorations.beauty(level, hall);
		helper.assertTrue(after == before + 3, "beauty " + before + " -> " + after + " with a Bandstand");
		helper.succeed();
	}

	// --- each built by a builder, its workers taking their blocks -------------------------------------------------

	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos[] CHESTS = {new BlockPos(3, 2, 6), new BlockPos(3, 2, 8), new BlockPos(3, 2, 10)};
	private static final BlockPos BUILDER = new BlockPos(5, 2, 3);
	/** Where builds on flat ground go (the Farmstead II runs back to z 25). */
	private static final BlockPos HOUSE = new BlockPos(12, 2, 4);
	/** Where the Fisher's Huts go: their jetty (z 8-12) over the water, the hut on the shore behind. */
	private static final BlockPos SHORE_HOUSE = new BlockPos(12, 2, 8);
	/** The water: x 9-27, z 1 to the jetty's root, two deep (y 1 and 0) over a stone bed; stone round it holds it in. */
	private static final int WATER_X0 = 9;
	private static final int WATER_X1 = 27;
	private static final int WATER_Z0 = 1;

	/** A worker of a building: his job and the block he works at. */
	record Worker(String job, Block block) {
	}

	/**
	 * A building and its workers, one for each of its job blocks; {@code extra} job blocks it has with no worker of their
	 * own (the Fisher's Hut II's smoker: the fisherman smokes his catch in it); whether it goes on the shore.
	 */
	record Workplace(StarterBlueprints.Entry entry, List<Worker> workers, Map<Block, Integer> extra, boolean shore) {
	}

	static List<Workplace> workplaces() {
		Worker farmer = new Worker("minecraft:farmer", Blocks.COMPOSTER);
		Worker fisherman = new Worker("minecraft:fisherman", Blocks.BARREL);
		Worker shepherd = new Worker("minecraft:shepherd", Blocks.LOOM);
		Worker bard = new Worker("aliveworkplace:bard", Blocks.JUKEBOX);
		return List.of(
			new Workplace(StarterBlueprints.FARMSTEAD, List.of(farmer), Map.of(), false),
			new Workplace(StarterBlueprints.FARMSTEAD_2, List.of(farmer, farmer), Map.of(), false),
			new Workplace(StarterBlueprints.FISHERS_HUT, List.of(fisherman), Map.of(), true),
			new Workplace(StarterBlueprints.FISHERS_HUT_2, List.of(fisherman), Map.of(Blocks.SMOKER, 1), true),
			new Workplace(StarterBlueprints.WEAVERS_COTTAGE, List.of(shepherd), Map.of(), false),
			new Workplace(StarterBlueprints.WEAVERS_COTTAGE_2, List.of(shepherd), Map.of(), false),
			new Workplace(StarterBlueprints.BANDSTAND, List.of(bard), Map.of(), false));
	}

	@GameTestGenerator
	public Collection<TestFunction> builds() {
		List<TestFunction> out = new ArrayList<>();
		for (Workplace w : workplaces()) {
			String name = "countryWorkplaceBuilt_" + w.entry().id().getPath();
			out.add(new TestFunction(name, name, HUGE_AREA, 14000, 0, true, helper -> build(helper, w)));
		}
		return out;
	}

	/**
	 * The shore for a Fisher's Hut at {@link #SHORE_HOUSE}: water two deep in front of the hut, under where its jetty goes
	 * and out past its end, on a stone bed. The two layers under the area's floor are put back as they were afterwards.
	 */
	private static void shore(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Map<BlockPos, BlockState> under = new HashMap<>();
		int z1 = SHORE_HOUSE.getZ() + 4;
		for (int x = WATER_X0; x <= WATER_X1; x++) {
			for (int z = WATER_Z0; z <= z1; z++) {
				for (int y = -1; y <= 0; y++) {
					BlockPos p = helper.absolutePos(new BlockPos(x, y, z));
					under.put(p, level.getBlockState(p));
				}
			}
		}
		// the bed and the banks below the floor first, then the water
		for (int x = WATER_X0 - 1; x <= WATER_X1 + 1; x++) {
			for (int z = WATER_Z0 - 1; z <= z1 + 1; z++) {
				BlockPos p = helper.absolutePos(new BlockPos(x, -1, z));
				under.putIfAbsent(p, level.getBlockState(p));
				level.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
				boolean inside = x >= WATER_X0 && x <= WATER_X1 && z >= WATER_Z0 && z <= z1;
				if (!inside) {
					BlockPos q = helper.absolutePos(new BlockPos(x, 0, z));
					under.putIfAbsent(q, level.getBlockState(q));
					level.setBlock(q, Blocks.STONE.defaultBlockState(), 2);
				}
			}
		}
		for (int x = WATER_X0; x <= WATER_X1; x++) {
			for (int z = WATER_Z0; z <= z1; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.WATER);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
			}
		}
		Leftovers.after(helper, () -> under.forEach((p, s) -> level.setBlock(p, s, 2)));
	}

	static void build(GameTestHelper helper, Workplace w) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		BlockPos house = w.shore() ? SHORE_HOUSE : HOUSE;
		if (w.shore()) {
			shore(helper);
		}
		ResourceLocation id = w.entry().id();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("missing " + id));
		helper.assertTrue(blueprint.size().equals(w.entry().size()), id + " is " + blueprint.size());
		helper.assertTrue(blueprint.entities().isEmpty(), id + " comes with a villager");
		// Exactly one job block per worker (and its extras), and no other vanilla job block a passing villager could take.
		Set<Block> jobBlocks = Set.of(Blocks.BLAST_FURNACE, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.STONECUTTER, Blocks.FLETCHING_TABLE,
			Blocks.CARTOGRAPHY_TABLE, Blocks.BARREL, Blocks.LECTERN, Blocks.COMPOSTER, Blocks.CAULDRON, Blocks.SMOKER, Blocks.BREWING_STAND,
			Blocks.LOOM, Blocks.CRAFTING_TABLE, Blocks.JUKEBOX, Blocks.FURNACE);
		for (Block block : jobBlocks) {
			long want = w.workers().stream().filter(k -> k.block() == block).count() + w.extra().getOrDefault(block, 0);
			long have = blueprint.blocks().stream().filter(b -> b.state().is(block)).count();
			helper.assertTrue(want == have, id + " has " + have + " " + block + ", its workers need " + want);
		}
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(house), Rotation.NONE, Mirror.NONE);
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
		BlockPos min = helper.absolutePos(house);
		BlockPos max = min.offset(blueprint.size().getX() - 1, blueprint.size().getY() - 1, blueprint.size().getZ() - 1);
		List<Villager> workers = new ArrayList<>();
		boolean[] hired = {false};
		long[] hiredAt = {0};
		boolean[] working = {false};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage());
			if (!hired[0]) {
				hired[0] = true;
				hiredAt[0] = helper.getTick();
				if (w.shore()) {
					jettyStands(helper, blueprint, house);
				}
				helper.setDayTime(2000); // a working day ahead of them
				// The workers come once it stands: from the street in front, or for the hut on the shore from the land behind.
				int streetZ = w.shore() ? house.getZ() + blueprint.size().getZ() + 1 : house.getZ() - 2;
				for (int i = 0; i < w.workers().size(); i++) {
					Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(13 + 3 * i, 2, streetZ));
					VillagerProfession job = BuiltInRegistries.VILLAGER_PROFESSION.get(ResourceLocation.parse(w.workers().get(i).job()));
					worker.setVillagerData(worker.getVillagerData().setProfession(job));
					worker.setVillagerXp(1);
					worker.refreshBrain(level); // as when a villager takes a job: its brain looks for that job's blocks
					workers.add(worker);
				}
			}
			Set<BlockPos> taken = new HashSet<>();
			for (int i = 0; i < workers.size(); i++) {
				Villager worker = workers.get(i);
				Worker k = w.workers().get(i);
				helper.assertTrue(worker.isAlive() && BuiltInRegistries.VILLAGER_PROFESSION.getKey(worker.getVillagerData().getProfession()).toString().equals(k.job()),
					"the " + k.job() + " is now " + worker.getVillagerData().getProfession());
				BlockPos site2 = worker.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
				boolean inside = site2 != null && level.getBlockState(site2).is(k.block()) && site2.getX() >= min.getX() && site2.getX() <= max.getX()
					&& site2.getY() >= min.getY() && site2.getY() <= max.getY() && site2.getZ() >= min.getZ() && site2.getZ() <= max.getZ();
				if (!inside) {
					throw new GameTestAssertException("the " + k.job() + " works at " + site2 + " (" + (helper.getTick() - hiredAt[0])
						+ " ticks after it was built at tick " + hiredAt[0] + "; " + whereStuck(worker, blueprint, k.block(), min) + ")");
				}
				helper.assertTrue(taken.add(site2), "two workers share " + site2);
			}
			// Then the work itself, for the Farmstead's farmer and the Fisher's Hut's fisherman.
			if (w.entry() == StarterBlueprints.FARMSTEAD) {
				farmerWorksTheField(helper, workers.get(0), house, working);
			} else if (w.entry() == StarterBlueprints.FISHERS_HUT) {
				fishermanFishesFromTheJetty(helper, workers.get(0), house, working);
			}
		});
	}

	/** Why a worker hasn't reached his block, in the blueprint's frame: where he stands, his path there and what he's doing. */
	private static String whereStuck(Villager worker, Blueprint blueprint, Block block, BlockPos min) {
		BlockPos target = blueprint.blocks().stream().filter(b -> b.state().is(block)).findFirst().map(b -> min.offset(b.pos())).orElse(min);
		var path = worker.getNavigation().getPath();
		List<String> nodes = new ArrayList<>();
		for (int n = 0; path != null && n < path.getNodeCount(); n++) {
			BlockPos q = path.getNodePos(n).subtract(min);
			nodes.add(q.getX() + "," + q.getY() + "," + q.getZ() + (n == path.getNextNodeIndex() ? "*" : ""));
		}
		var fresh = worker.getNavigation().createPath(target, 1);
		return "he's at " + worker.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(min)) + ", eyeing "
			+ worker.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).map(g -> g.pos().subtract(min)).orElse(null)
			+ ", on the path " + nodes + ", a path there now " + (fresh == null || fresh.getEndNode() == null ? "none" :fresh.canReach() + " to " + fresh.getEndNode().asBlockPos().subtract(min))
			+ ", doing " + worker.getBrain().getActiveNonCoreActivity() + ", running "
			+ worker.getBrain().getRunningBehaviors().stream().map(b -> b.getClass().getSimpleName()).toList();
	}

	/** The jetty as built: its log posts taken down to the bed, nothing but water under its deck. */
	private static void jettyStands(GameTestHelper helper, Blueprint blueprint, BlockPos house) {
		int posts = 0;
		int deck = 0;
		for (Blueprint.Entry e : blueprint.blocks()) {
			BlockPos p = e.pos();
			if (p.getY() != 0 || p.getZ() >= 5 || e.state().isAir()) {
				continue;
			}
			BlockPos at = house.offset(p);
			if (e.state().is(BlockTags.LOGS)) {
				posts++;
				for (int y = 1; y >= 0; y--) {
					helper.assertTrue(helper.getBlockState(new BlockPos(at.getX(), y, at.getZ())).is(e.state().getBlock()),
						"the jetty's post at " + at + " doesn't reach the bed: " + helper.getBlockState(new BlockPos(at.getX(), y, at.getZ())) + " at y " + y);
				}
			} else if (e.state().getBlock() instanceof net.minecraft.world.level.block.SlabBlock) {
				deck++;
				helper.assertTrue(helper.getBlockState(at).is(e.state().getBlock()), "no deck at " + at + ": " + helper.getBlockState(at));
				helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(at.below())).is(FluidTags.WATER),
					"under the deck at " + at + ": " + helper.getBlockState(at.below()));
			}
		}
		helper.assertTrue(posts >= 4 && deck >= 9, "a jetty of " + posts + " posts and " + deck + " deck slabs");
	}

	/**
	 * The farmer at the Farmstead's composter takes on its field by himself (a chest by the composter for the harvest);
	 * once its crops are ripe he harvests them, and the harvest goes into the chests.
	 */
	private static void farmerWorksTheField(GameTestHelper helper, Villager farmer, BlockPos house, boolean[] ripened) {
		helper.assertTrue(Fields.hasField(farmer), "the farmer hasn't taken on the field");
		BoundingBox field = ModAttachments.FARM_FIELD.get(farmer).box();
		BlockPos corner = helper.absolutePos(house.offset(9, 0, 2));
		BlockPos far = helper.absolutePos(house.offset(13, 0, 10));
		helper.assertTrue(field.isInside(corner) && field.isInside(far), "the farmer's field is " + field + ", not the Farmstead's");
		if (!ripened[0]) {
			ripened[0] = true;
			// The days go by: the crops ripen.
			for (BlockPos p : BlockPos.betweenClosed(house.offset(9, 1, 2), house.offset(13, 1, 10))) {
				BlockState crop = helper.getBlockState(p);
				if (crop.getBlock() instanceof CropBlock c) {
					helper.setBlock(p, c.getStateForAge(c.getMaxAge()));
				}
			}
		}
		int harvested = ModAttachments.FARM_HARVESTED.getOrElse(farmer, 0);
		helper.assertTrue(harvested >= 6, "the farmer harvested " + harvested);
		int stored = 0;
		for (BlockPos p : BlockPos.betweenClosed(house.offset(0, 1, 0), house.offset(14, 1, 12))) {
			if (helper.getLevel().getBlockEntity(helper.absolutePos(p)) instanceof Container c) {
				stored += c.countItem(Items.WHEAT) + c.countItem(Items.CARROT) + c.countItem(Items.POTATO);
			}
		}
		helper.assertTrue(stored > 0, "no harvest in the Farmstead's chests (harvested " + harvested + ")");
	}

	/** The fisherman at the hut's barrel, handed a rod, fishes from the end of the jetty: he stands on its deck to cast. */
	private static void fishermanFishesFromTheJetty(GameTestHelper helper, Villager fisherman, BlockPos house, boolean[] started) {
		if (!started[0]) {
			started[0] = true;
			Fishers.start(helper.getLevel(), fisherman, new ItemStack(Items.FISHING_ROD));
		}
		var bobbers = helper.getLevel().getEntitiesOfClass(FishingBobber.class, helper.getBounds(), b -> b.owner() == fisherman);
		if (bobbers.size() != 1) {
			// the water past the jetty's end is the water a fisher fishes off a jetty
			boolean offTheEnd = io.github.jcondedata.aliveworkplace.fish.FisherWork.offAJetty(helper.getLevel(), helper.absolutePos(house.offset(5, -1, -1)));
			throw new GameTestAssertException("the fisherman has " + bobbers.size() + " bobbers out; he's at "
				+ fisherman.position().subtract(net.minecraft.world.phys.Vec3.atLowerCornerOf(helper.absolutePos(house))) + ", holding "
				+ fisherman.getMainHandItem() + ", caught " + ModAttachments.FISH_CAUGHT.getOrElse(fisherman, 0) + ", doing "
				+ fisherman.getBrain().getActiveNonCoreActivity() + "; past the jetty's end is water off a jetty: " + offTheEnd);
		}
		helper.assertTrue(helper.getLevel().getFluidState(bobbers.get(0).blockPosition()).is(FluidTags.WATER)
			|| helper.getLevel().getFluidState(bobbers.get(0).blockPosition().below()).is(FluidTags.WATER), "the bobber isn't on the water");
		BlockPos feet = fisherman.blockPosition().subtract(helper.absolutePos(house)); // in the blueprint's frame
		boolean onJetty = feet.getY() == 1 && feet.getX() >= 4 && feet.getX() <= 6 && feet.getZ() >= 0 && feet.getZ() <= 4;
		helper.assertTrue(onJetty, "the fisherman casts from " + feet + ", not the jetty");
		int caught = ModAttachments.FISH_CAUGHT.getOrElse(fisherman, 0);
		helper.assertTrue(caught >= 1, "nothing caught from the jetty yet");
	}
}

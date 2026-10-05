package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.StewardConditions;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageMaps;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * ROADMAP 27.13, new workplaces I: the Smithy, Mason's Yard, Fletcher's Lodge (each with its II) and Map Room. Their
 * {@code workplace_*} rules in 27.11's form (a worker of the trade without a workstation, or a job the village wants with
 * no free block, asks for the building in its zone), and each of the seven built by a builder from its Blueprint Table
 * blueprint, every worker of it then taking a job block of his own inside it.
 */
public class CraftWorkplacesGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE_AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	static final List<WorkplacesGameTests.Case> CASES = List.of(
		new WorkplacesGameTests.Case("workplace_smithy", "minecraft:armorer", "smithy", "workshops", VillageRanks.Rank.HAMLET, false),
		new WorkplacesGameTests.Case("workplace_smithy", "aliveworkplace:miner", "smithy", "workshops", VillageRanks.Rank.HAMLET, false),
		new WorkplacesGameTests.Case("workplace_masons_yard", "minecraft:mason", "masons_yard", "workshops", VillageRanks.Rank.HAMLET, false),
		new WorkplacesGameTests.Case("workplace_fletchers_lodge", "minecraft:fletcher", "fletchers_lodge", "workshops", VillageRanks.Rank.HAMLET, false),
		new WorkplacesGameTests.Case("workplace_fletchers_lodge", "aliveworkplace:lumberjack", "fletchers_lodge", "workshops", VillageRanks.Rank.HAMLET, false),
		new WorkplacesGameTests.Case("workplace_map_room", "minecraft:cartographer", "map_room", "civic", VillageRanks.Rank.HAMLET, false));

	/** Each rule: the worker staged without a workstation, the building asked for in its zone (27.11's checks). */
	@GameTestGenerator
	public Collection<TestFunction> rules() {
		List<TestFunction> out = new ArrayList<>();
		for (WorkplacesGameTests.Case c : CASES) {
			String name = "craftWorkplaceRule_" + c.rule().substring("workplace_".length()) + "_" + c.job().substring(c.job().indexOf(':') + 1);
			out.add(new TestFunction(name, name, AREA, 60, 0, true, helper -> WorkplacesGameTests.rule(helper, c)));
		}
		return out;
	}

	private static StewardRules.Rule ruleOf(String id) {
		return StewardRules.all().stream().filter(r -> r.id().equals(AliveWorkplace.id(id))).findFirst()
			.orElseThrow(() -> new GameTestAssertException("rule " + id + " didn't load"));
	}

	/**
	 * A mason who works at a stonecutter doesn't make the village want a Mason's Yard; the same mason with his block gone
	 * does. And the jobs that have a workplace of their own elsewhere (a toolsmith, a weaponsmith) never ask for a Smithy.
	 */
	//$ gametest_ticks_batch AREA '60' '"craftWorkplaceHasBlock"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "craftWorkplaceHasBlock")
	public void aWorkerAtHisBlockWantsNoWorkplace(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		ServerLevel level = helper.getLevel();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> level.removeBlock(hall, false));
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setRank(VillageRanks.Rank.HAMLET);
		long day = StewardWishes.day(level);
		helper.setBlock(new BlockPos(6, 2, 20), Blocks.STONECUTTER);
		Villager mason = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 20));
		mason.setNoAi(true);
		mason.setVillagerData(mason.getVillagerData().setProfession(VillagerProfession.MASON));
		mason.setVillagerXp(1);
		mason.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(6, 2, 20))));
		StewardRules.Rule yard = ruleOf("workplace_masons_yard");
		StewardWishes.Verdict working = StewardWishes.judge(yard, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day);
		helper.assertTrue(working.status() != StewardWishes.Status.HELD && working.checks().get(0).value() == 0,
			"a Mason's Yard for a mason at his stonecutter: " + working.status() + " " + working.checks());
		mason.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		StewardWishes.Verdict lost = StewardWishes.judge(yard, StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day);
		helper.assertTrue(lost.status() == StewardWishes.Status.HELD, "no Mason's Yard for a mason who lost his block: " + lost.status());
		// A toolsmith and a weaponsmith without a block: their own rules (Tinker's Workshop, Guard House), not the Smithy's.
		for (VillagerProfession job : List.of(VillagerProfession.TOOLSMITH, VillagerProfession.WEAPONSMITH)) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(job == VillagerProfession.TOOLSMITH ? 8 : 10, 2, 20));
			v.setNoAi(true);
			v.setVillagerData(v.getVillagerData().setProfession(job));
			v.setVillagerXp(1);
		}
		StewardWishes.Verdict smithy = StewardWishes.judge(ruleOf("workplace_smithy"), StewardConditions.Facts.of(level, hall), StewardWishes.State.EMPTY, day);
		helper.assertTrue(smithy.status() != StewardWishes.Status.HELD, "a Smithy for a toolsmith or a weaponsmith: " + smithy.checks());
		helper.succeed();
	}

	/** The seven are in the Blueprint Table's library, the six with an upgrade drawn as tiers of one building, and on the Village Map. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void craftWorkplacesAreInTheBlueprintTable(GameTestHelper helper) {
		List<ResourceLocation> library = BlueprintLibrary.list(helper.getLevel().getServer(), false);
		for (StarterBlueprints.Entry e : List.of(StarterBlueprints.SMITHY, StarterBlueprints.SMITHY_2, StarterBlueprints.MASONS_YARD,
			StarterBlueprints.MASONS_YARD_2, StarterBlueprints.FLETCHERS_LODGE, StarterBlueprints.FLETCHERS_LODGE_2, StarterBlueprints.MAP_ROOM)) {
			helper.assertTrue(library.contains(e.id()), e.id() + " isn't in the Blueprint Table");
			helper.assertTrue(StarterBlueprints.ALL.contains(e) || StarterBlueprints.ONE_TIER.contains(e), e.id() + " isn't a starter blueprint");
			helper.assertTrue(VillageMaps.kindOf(e.id()).orElse(null) == (e == StarterBlueprints.MAP_ROOM ? VillageMaps.Kind.LEARNING : VillageMaps.Kind.WORKSHOPS),
				e.id() + " is on the map as " + VillageMaps.kindOf(e.id()));
		}
		helper.succeed();
	}

	// --- each built by a builder, its workers taking their blocks -------------------------------------------------

	private static final BlockPos BENCH = new BlockPos(3, 2, 3);
	private static final BlockPos[] CHESTS = {new BlockPos(3, 2, 6), new BlockPos(3, 2, 8), new BlockPos(3, 2, 10)};
	private static final BlockPos BUILDER = new BlockPos(5, 2, 3);
	private static final BlockPos HOUSE = new BlockPos(12, 2, 8);

	/** A worker of a building: his job and the block he works at. */
	record Worker(String job, Block block) {
	}

	/** A building and its workers, one for each of its job blocks. */
	record Workplace(StarterBlueprints.Entry entry, List<Worker> workers) {
	}

	static List<Workplace> workplaces() {
		Worker armorer = new Worker("minecraft:armorer", Blocks.BLAST_FURNACE);
		Worker miner = new Worker("aliveworkplace:miner", Blocks.BLAST_FURNACE);
		Worker toolsmith = new Worker("minecraft:toolsmith", Blocks.SMITHING_TABLE);
		Worker weaponsmith = new Worker("minecraft:weaponsmith", Blocks.GRINDSTONE);
		Worker mason = new Worker("minecraft:mason", Blocks.STONECUTTER);
		Worker fletcher = new Worker("minecraft:fletcher", Blocks.FLETCHING_TABLE);
		Worker lumberjack = new Worker("aliveworkplace:lumberjack", Blocks.FLETCHING_TABLE);
		return List.of(
			new Workplace(StarterBlueprints.SMITHY, List.of(miner, toolsmith, weaponsmith)),
			new Workplace(StarterBlueprints.SMITHY_2, List.of(armorer, miner, toolsmith, weaponsmith)),
			new Workplace(StarterBlueprints.MASONS_YARD, List.of(mason)),
			new Workplace(StarterBlueprints.MASONS_YARD_2, List.of(mason, mason)),
			new Workplace(StarterBlueprints.FLETCHERS_LODGE, List.of(lumberjack)),
			new Workplace(StarterBlueprints.FLETCHERS_LODGE_2, List.of(fletcher, lumberjack)),
			new Workplace(StarterBlueprints.MAP_ROOM, List.of(new Worker("minecraft:cartographer", Blocks.CARTOGRAPHY_TABLE))));
	}

	@GameTestGenerator
	public Collection<TestFunction> builds() {
		List<TestFunction> out = new ArrayList<>();
		for (Workplace w : workplaces()) {
			String name = "craftWorkplaceBuilt_" + w.entry().id().getPath();
			out.add(new TestFunction(name, name, HUGE_AREA, 12000, 0, true, helper -> build(helper, w)));
		}
		return out;
	}

	static void build(GameTestHelper helper, Workplace w) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		ResourceLocation id = w.entry().id();
		Blueprint blueprint = BlueprintLibrary.get(level, id).orElseThrow(() -> new GameTestAssertException("missing " + id));
		helper.assertTrue(blueprint.size().equals(w.entry().size()), id + " is " + blueprint.size());
		helper.assertTrue(blueprint.entities().isEmpty(), id + " comes with a villager");
		// Exactly one job block per worker, and no other vanilla job block a passing villager could take.
		Set<Block> jobBlocks = Set.of(Blocks.BLAST_FURNACE, Blocks.SMITHING_TABLE, Blocks.GRINDSTONE, Blocks.STONECUTTER, Blocks.FLETCHING_TABLE,
			Blocks.CARTOGRAPHY_TABLE, Blocks.BARREL, Blocks.LECTERN, Blocks.COMPOSTER, Blocks.CAULDRON, Blocks.SMOKER, Blocks.BREWING_STAND,
			Blocks.LOOM, Blocks.CRAFTING_TABLE);
		for (Block block : jobBlocks) {
			long want = w.workers().stream().filter(k -> k.block() == block).count();
			long have = blueprint.blocks().stream().filter(b -> b.state().is(block)).count();
			helper.assertTrue(want == have, id + " has " + have + " " + block + ", its workers need " + want);
		}
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
		BlockPos min = helper.absolutePos(HOUSE);
		BlockPos max = min.offset(blueprint.size().getX() - 1, blueprint.size().getY() - 1, blueprint.size().getZ() - 1);
		List<Villager> workers = new ArrayList<>();
		boolean[] hired = {false};
		long[] hiredAt = {0};
		helper.succeedWhen(() -> {
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: " + site.stage());
			if (!hired[0]) {
				// The workers come once it stands, as they would to a finished workplace: from the street in front.
				hired[0] = true;
				hiredAt[0] = helper.getTick();
				for (int i = 0; i < w.workers().size(); i++) {
					Villager worker = helper.spawn(EntityType.VILLAGER, new BlockPos(13 + 3 * i, 2, 5)); // in the street in front
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
				helper.assertTrue(site2 != null && level.getBlockState(site2).is(k.block()) && site2.getX() >= min.getX() && site2.getX() <= max.getX()
					&& site2.getY() >= min.getY() && site2.getY() <= max.getY() && site2.getZ() >= min.getZ() && site2.getZ() <= max.getZ(),
					"the " + k.job() + " works at " + site2 + " (" + (helper.getTick() - hiredAt[0]) + " ticks after it was built at tick " + hiredAt[0] + ")");
				helper.assertTrue(taken.add(site2), "two workers share " + site2);
			}
		});
	}
}

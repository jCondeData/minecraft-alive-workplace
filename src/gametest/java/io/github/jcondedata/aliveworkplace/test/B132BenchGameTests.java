package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B132, the soak's farmstead that waited forever with all its materials in the chests by its builder's bench: a
 * neighbouring build cleared that bench (it stood where the build wanted air), its builder lost his job site, took
 * another builder's free-looking bench, and his site's bench moved with him, out of reach of his chests.
 */
public class B132BenchGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos BENCH = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos ORIGIN = new BlockPos(6, 2, 6);
	/** The neighbour's bench, inside the build's box where the blueprint wants air. */
	private static final BlockPos NEIGHBOUR_BENCH = new BlockPos(7, 3, 7);

	/**
	 * A build whose box takes in another builder's bench leaves that bench standing: the neighbour keeps his job site
	 * and his place at it, and the build still finishes round it.
	 */
	//$ gametest_ticks_batch AREA '2400' '"b132NeighbourBench"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "b132NeighbourBench")
	public void b132ABuildNeverBreaksANeighboursBench(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		// A 3 x 2 x 3 blueprint: a plank floor with air above it.
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(ORIGIN.offset(x, 0, z), Blocks.OAK_PLANKS);
			}
		}
		ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "b132_floor_" + Long.toHexString(level.getGameTime()));
		level.getStructureManager().getOrCreate(id).fillFromWorld(level, helper.absolutePos(ORIGIN), new Vec3i(3, 2, 3), false, Blocks.STRUCTURE_VOID);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				helper.setBlock(ORIGIN.offset(x, 0, z), Blocks.AIR);
			}
		}
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(CHEST, Blocks.CHEST);
		((Container) helper.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.OAK_PLANKS, 9));
		helper.setBlock(NEIGHBOUR_BENCH, ModBlocks.BUILDERS_BENCH);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.east());
		Villager neighbour = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 11));
		Builders.employ(level, builder, helper.absolutePos(BENCH));
		Builders.employ(level, neighbour, helper.absolutePos(NEIGHBOUR_BENCH));
		BuildSite site = Builders.start(level, builder, null, id,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE));
		BuildPlan plan = site == null ? null : site.plan(level);
		if (plan == null) {
			throw new GameTestAssertException(id + " didn't start");
		}
		BlockPos bench = helper.absolutePos(NEIGHBOUR_BENCH);
		GlobalPos his = GlobalPos.of(level.dimension(), bench);
		String[] lost = {null};
		helper.onEachTick(() -> {
			if (lost[0] == null && !level.getBlockState(bench).is(ModBlocks.BUILDERS_BENCH)) {
				lost[0] = "B132: at tick " + helper.getTick() + " the build (stage " + site.stage() + ") turned the neighbour's bench into "
					+ level.getBlockState(bench).getBlock();
			} else if (lost[0] == null && !neighbour.getBrain().getMemory(MemoryModuleType.JOB_SITE).equals(Optional.of(his))) {
				lost[0] = "B132: at tick " + helper.getTick() + " the neighbour's job site is "
					+ neighbour.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().toShortString()).orElse("none");
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(lost[0] == null, lost[0] == null ? "" : lost[0]);
			helper.assertTrue(BuildSiteManager.get(level).get(site.id()) == null, "still building: stage " + site.stage() + ", " + site.detail());
			helper.assertTrue(level.getPoiManager().getFreeTickets(bench) == 0, "the neighbour's bench is free for anyone to take");
			for (int x = 0; x < 3; x++) {
				for (int z = 0; z < 3; z++) {
					helper.assertBlockPresent(Blocks.OAK_PLANKS, ORIGIN.offset(x, 0, z));
				}
			}
		});
	}

	/**
	 * A builder employed at a bench put down in the same piece of work (an admin command, the soak) holds its place: a
	 * builder without a bench beside it never takes it from him. Run from a command or any other queued server task, the
	 * level files a new workstation only after that task, so the take found no record and the bench looked free; in the
	 * soak a builder whose bench a build broke took another's that way, and the two fought over it.
	 */
	//$ gametest_ticks_batch AREA '400' '"b132FreshBench"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "b132FreshBench")
	public void b132ABenchPutDownAndEmployedAtOnceIsHeld(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		Villager owner = helper.spawn(EntityType.VILLAGER, new BlockPos(8, 2, 12));
		// A builder who lost his bench (it was broken), standing right by where this one goes.
		Villager homeless = helper.spawn(EntityType.VILLAGER, BENCH.east());
		homeless.setVillagerData(homeless.getVillagerData().setProfession(ModVillagers.BUILDER));
		homeless.setVillagerXp(1);
		BlockPos bench = helper.absolutePos(BENCH);
		GlobalPos his = GlobalPos.of(level.dimension(), bench);
		boolean[] employed = {false};
		// As a command does it: the bench put down and the builder employed in one queued server task.
		level.getServer().tell(new net.minecraft.server.TickTask(level.getServer().getTickCount(), () -> {
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			Builders.employ(level, owner, bench);
			employed[0] = true;
		}));
		helper.onEachTick(() -> {
			if (!employed[0] || helper.getTick() >= 340) {
				return; // (from 340 the test is being wound up)
			}
			helper.assertTrue(homeless.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty()
					&& homeless.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
				"B132: at tick " + helper.getTick() + " the builder without a bench went for the employed builder's (its free places: "
					+ level.getPoiManager().getFreeTickets(bench) + ")");
			helper.assertTrue(owner.getBrain().getMemory(MemoryModuleType.JOB_SITE).equals(Optional.of(his)),
				"B132: at tick " + helper.getTick() + " the employed builder's job site is "
					+ owner.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(g -> g.pos().toShortString()).orElse("none"));
		});
		helper.runAtTickTime(340, () -> {
			helper.assertTrue(employed[0], "the task never ran");
			helper.assertTrue(level.getPoiManager().getFreeTickets(bench) == 0, "B132: the employed builder's bench is free for anyone to take");
			helper.succeed();
		});
	}
}

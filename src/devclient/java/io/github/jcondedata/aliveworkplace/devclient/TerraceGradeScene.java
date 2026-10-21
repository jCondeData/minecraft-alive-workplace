package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=terrace_grade (B280): the Terrace standing on a bump one block above a lawn with flowers and a pit beside it.
 * The house goes up at once; its builder does the landscaping. Before B280 he filled the ring round it up to the
 * floor: a raised, jagged band of dirt on the grass, holes where flowers stood. Now he fills the pit and nothing else.
 */
final class TerraceGradeScene {
	/** The superflat world's grass. */
	private static final int LAWN = -61;
	private static final BlockPos ORIGIN = new BlockPos(8, LAWN + 2, 10);
	private static final BlockPos PIT = new BlockPos(12, LAWN, 8);
	private static final int SIZE = 30;
	private static final int LIMIT = 1800;

	private int tick;
	private int doneAt = -1;
	private BuildSite site;
	private final int[][] oldTop = new int[SIZE][SIZE];
	private final List<String> wrong = java.util.Collections.synchronizedList(new ArrayList<>());

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> ground(server.overworld()));
		}
		if (tick == 30) {
			look(server, new Vec3(14.5, LAWN + 9, -6), new Vec3(14.5, LAWN + 2, 10));
		}
		if (tick % 20 == 0) {
			// Slimes from the superflat world's slime chunks hop into the shots.
			server.execute(() -> server.overworld().getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class,
				new net.minecraft.world.phys.AABB(BlockPos.ZERO).inflate(60)).forEach(net.minecraft.world.entity.Entity::discard));
		}
		if (tick == 90) {
			ScreenshotHarness.shot(mc, "01_before");
		}
		if (tick == 95) {
			server.execute(() -> start(server.overworld()));
		}
		if (tick > 100 && doneAt < 0 && site != null && tick % 20 == 0) {
			server.execute(() -> {
				if (BuildSiteManager.get(server.overworld()).get(site.id()) == null && doneAt < 0) {
					doneAt = tick;
				}
			});
		}
		if (doneAt < 0 && tick == LIMIT) {
			doneAt = tick;
			wrong.add("the builder didn't finish the landscaping in time: " + (site == null ? "no site" : site.stage() + ", " + site.status()));
		}
		if (doneAt > 0) {
			int t = tick - doneAt;
			if (t == 5) {
				look(server, new Vec3(14.5, LAWN + 9, -6), new Vec3(14.5, LAWN + 2, 10));
			}
			if (t == 20) {
				ScreenshotHarness.shot(mc, "02_front");
			}
			if (t == 25) {
				look(server, new Vec3(-4, LAWN + 7, 4), new Vec3(10, LAWN + 1, 12));
			}
			if (t == 40) {
				ScreenshotHarness.shot(mc, "03_corner");
				server.execute(() -> check(server.overworld()));
			}
			if (t == 50) {
				Showcase.check(wrong.isEmpty(), wrong.isEmpty()
					? "round the finished Terrace the lawn stayed level: the pit was filled, nothing was raised above the grass and no sand or gravel was left"
					: String.join("; ", wrong));
				mc.stop();
			}
		}
	}

	/** The lawn, the bump the house will stand on, flowers and a pit. */
	private void ground(ServerLevel level) {
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		level.setDayTime(3000);
		RandomSource random = RandomSource.create(280L);
		int sx = StarterBlueprints.TERRACE.size().getX();
		int sz = StarterBlueprints.TERRACE.size().getZ();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				boolean under = x >= ORIGIN.getX() && x < ORIGIN.getX() + sx && z >= ORIGIN.getZ() && z < ORIGIN.getZ() + sz;
				for (int y = LAWN + 1; y < LAWN + 20; y++) {
					level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
				}
				level.setBlock(new BlockPos(x, LAWN, z), (under ? Blocks.DIRT : Blocks.GRASS_BLOCK).defaultBlockState(), 2);
				if (under) {
					level.setBlock(new BlockPos(x, LAWN + 1, z), Blocks.GRASS_BLOCK.defaultBlockState(), 2); // the bump
				} else if (random.nextInt(5) == 0) {
					level.setBlock(new BlockPos(x, LAWN + 1, z), (random.nextBoolean() ? Blocks.POPPY : Blocks.DANDELION).defaultBlockState(), 2);
				}
				oldTop[x][z] = LAWN + (level.getBlockState(new BlockPos(x, LAWN + 1, z)).isAir() ? 0 : 1);
			}
		}
		level.setBlock(PIT, Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(PIT.below(), Blocks.AIR.defaultBlockState(), 2);
		level.setBlock(PIT.above(), Blocks.AIR.defaultBlockState(), 2);
		oldTop[PIT.getX()][PIT.getZ()] = LAWN;
	}

	/** The builder and his site; the house put up at once, the landscaping left to him. */
	private void start(ServerLevel level) {
		BlockPos bench = new BlockPos(2, LAWN + 1, 2);
		BlockPos barrel = new BlockPos(2, LAWN + 1, 4);
		level.setBlock(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState(), 3);
		level.setBlock(barrel, Blocks.BARREL.defaultBlockState(), 3);
		if (level.getBlockEntity(barrel) instanceof BaseContainerBlockEntity container) {
			container.setItem(0, new ItemStack(Items.DIRT, 64));
		}
		Villager villager = EntityType.VILLAGER.spawn(level, new BlockPos(3, LAWN + 1, 3), MobSpawnType.COMMAND);
		if (villager == null) {
			wrong.add("no villager");
			return;
		}
		Builders.employ(level, villager, bench);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE);
		site = Builders.start(level, villager, null, StarterBlueprints.TERRACE.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			wrong.add("no Terrace blueprint");
			return;
		}
		for (BuildPlan.Stage stage : new BuildPlan.Stage[] {BuildPlan.Stage.FOUNDATION, BuildPlan.Stage.STRUCTURE, BuildPlan.Stage.DECORATION}) {
			for (BuildPlan.Step step : plan.steps(stage)) {
				level.setBlock(step.pos(), step.state(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				if (step.secondaryPos() != null && step.secondaryState() != null) {
					level.setBlock(step.secondaryPos(), step.secondaryState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
				}
			}
		}
		for (int i = 0; i < 20 && site.stage() != BuildPlan.Stage.LANDSCAPE; i++) {
			site.finishList();
		}
	}

	private void check(ServerLevel level) {
		int sx = StarterBlueprints.TERRACE.size().getX();
		int sz = StarterBlueprints.TERRACE.size().getZ();
		for (int x = 0; x < SIZE; x++) {
			for (int z = 0; z < SIZE; z++) {
				boolean under = x >= ORIGIN.getX() && x < ORIGIN.getX() + sx && z >= ORIGIN.getZ() && z < ORIGIN.getZ() + sz;
				if (under || x <= 4 && z <= 6) {
					continue;
				}
				for (int y = LAWN; y < LAWN + 10; y++) {
					BlockState state = level.getBlockState(new BlockPos(x, y, z));
					if (state.getBlock() instanceof FallingBlock || y > oldTop[x][z] && !state.isAir()) {
						wrong.add(state.getBlock().getName().getString() + " at " + x + ", " + y + ", " + z);
						return;
					}
				}
			}
		}
		if (level.getBlockState(PIT).isAir()) {
			wrong.add("the pit beside the house wasn't filled");
		}
	}

	private static void look(MinecraftServer server, Vec3 eye, Vec3 target) {
		server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), eye, target));
	}
}

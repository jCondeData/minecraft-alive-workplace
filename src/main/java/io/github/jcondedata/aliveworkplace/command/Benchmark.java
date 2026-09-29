package io.github.jcondedata.aliveworkplace.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintUpgrades;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.Porters;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /workplace benchmark <plots>}: fills the area east and south of where it's run with busy workers for a
 * performance test — on every plot a builder putting up a starter build (materials free), and next to it by turns a
 * miner with a quarry, a lumberjack, a porter at a storehouse, a carpenter or a mason. Only registered when the server
 * runs with {@code -Daliveworkplace.benchmark=true} (the pack test's performance mode), never on a normal server.
 */
public final class Benchmark {
	/** Plots are this far apart (the biggest starter build is 15 wide, a quarry 10). */
	static final int SPACING = 32;
	static final int COLUMNS = 5;

	public static void init() {
		if (!Boolean.getBoolean("aliveworkplace.benchmark")) {
			return;
		}
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
			Commands.literal("workplace").then(Commands.literal("benchmark")
				.requires(s -> s.hasPermission(4))
				.then(Commands.argument("plots", IntegerArgumentType.integer(1, 100))
					.executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "plots")))))));
	}

	static int run(CommandSourceStack source, int plots) {
		ServerLevel level = source.getLevel();
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, source.getServer());
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, source.getServer());
		level.setDayTime(1500);
		BlockPos origin = BlockPos.containing(source.getPosition());
		List<StarterBlueprints.Entry> builds = StarterBlueprints.ALL.stream()
			.filter(e -> BlueprintUpgrades.baseOf(e.id()).isEmpty()).toList();
		int workers = 0;
		for (int i = 0; i < plots; i++) {
			int x = origin.getX() + (i % COLUMNS) * SPACING;
			int z = origin.getZ() + (i / COLUMNS) * SPACING;
			BlockPos bench = ground(level, x, z);
			level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
			level.setBlockAndUpdate(bench.east(), Blocks.CHEST.defaultBlockState());
			Villager builder = villager(level, bench.south());
			if (builder != null) {
				Builders.employ(level, builder, bench);
				StarterBlueprints.Entry build = builds.get(i % builds.size());
				Builders.start(level, builder, null, build.id(),
					new BlueprintData.Placement(level.dimension().location(), ground(level, x + 3, z + 3), Rotation.NONE, Mirror.NONE));
				workers++;
			}
			BlockPos side = ground(level, x + 20, z + 2);
			workers += switch (i % 5) {
				case 0 -> miner(level, side, x + 18, z + 14);
				case 1 -> station(level, side, ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK,
					new ItemStack(Items.IRON_AXE), new ItemStack(Items.IRON_AXE));
				case 2 -> porter(level, side);
				case 3 -> station(level, side, ModBlocks.CARPENTERS_BENCH, ModVillagers.CARPENTERS_BENCH_POI, ModVillagers.CARPENTER);
				default -> station(level, side, Blocks.STONECUTTER, PoiTypes.MASON, VillagerProfession.MASON);
			};
		}
		int made = workers;
		source.sendSuccess(() -> Component.literal("Benchmark: " + made + " workers on " + plots + " plots"), true);
		return made;
	}

	private static int miner(ServerLevel level, BlockPos bench, int qx, int qz) {
		level.setBlockAndUpdate(bench, ModBlocks.MINERS_BENCH.defaultBlockState());
		chest(level, bench.east(), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_PICKAXE), new ItemStack(Items.IRON_PICKAXE),
			new ItemStack(Items.TORCH, 64));
		Villager miner = villager(level, bench.south());
		if (miner == null) {
			return 0;
		}
		Miners.employ(level, miner, bench);
		BlockPos top = ground(level, qx, qz).below();
		Miners.start(level, miner, null, BoundingBox.fromCorners(top.offset(0, -7, 0), top.offset(9, 0, 9)), 8);
		return 1;
	}

	private static int porter(ServerLevel level, BlockPos storehouse) {
		level.setBlockAndUpdate(storehouse, ModBlocks.STOREHOUSE.defaultBlockState());
		chest(level, storehouse.east());
		chest(level, storehouse.west());
		Villager porter = villager(level, storehouse.south());
		if (porter == null) {
			return 0;
		}
		Porters.employ(level, porter, storehouse);
		return 1;
	}

	private static int station(ServerLevel level, BlockPos pos, Block block, net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi,
			VillagerProfession job, ItemStack... supplies) {
		level.setBlockAndUpdate(pos, block.defaultBlockState());
		chest(level, pos.east(), supplies);
		Villager villager = villager(level, pos.south());
		if (villager == null) {
			return 0;
		}
		Jobs.employ(level, villager, pos, poi, job);
		return 1;
	}

	private static void chest(ServerLevel level, BlockPos pos, ItemStack... items) {
		level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof Container chest) {
			for (int i = 0; i < items.length; i++) {
				chest.setItem(i, items[i]);
			}
		}
	}

	@Nullable
	private static Villager villager(ServerLevel level, BlockPos pos) {
		return EntityType.VILLAGER.spawn(level, pos, MobSpawnType.COMMAND);
	}

	/** The first free block above the ground (leaves and water don't count as ground to build on, so it's above them). */
	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private Benchmark() {
	}
}

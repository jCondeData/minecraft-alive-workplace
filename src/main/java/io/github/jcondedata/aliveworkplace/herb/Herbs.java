package io.github.jcondedata.aliveworkplace.herb;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * The eight far herbs (ROADMAP 31.17) and the {@code herb_patch} a story arc plants. Each herb grows in the biomes of
 * its tag {@code aliveworkplace:herb_biomes/<herb>} (where an arc looks for it) on the ground of its block tag
 * {@code aliveworkplace:herb_ground/<herb>}.
 */
public final class Herbs {
	/** How far from its spot a patch spreads. */
	public static final int RADIUS = 6;
	/** A patch's size: 5 to 9 herbs, fewer only when there isn't that much fit ground. */
	public static final int LEAST = 5;
	public static final int MOST = 9;

	/** Every herb, in the order of the creative tab and the guide. */
	public static List<HerbBlock> all() {
		return List.of(ModBlocks.FROSTBLOOM, ModBlocks.SUNTHISTLE, ModBlocks.MIREWORT, ModBlocks.PEAKMOSS,
			ModBlocks.JUNGLE_ORCHID, ModBlocks.SEA_LAVENDER, ModBlocks.GLOWCAP, ModBlocks.PETAL_BALM);
	}

	/** The herb with this id ({@code aliveworkplace:frostbloom}, or just {@code frostbloom}); null if it's no herb. */
	@Nullable
	public static HerbBlock byId(String id) {
		ResourceLocation rl = ResourceLocation.tryParse(id.contains(":") ? id : AliveWorkplace.MOD_ID + ":" + id);
		if (rl == null) {
			return null;
		}
		return BuiltInRegistries.BLOCK.getOptional(rl).orElse(null) instanceof HerbBlock herb ? herb : null;
	}

	/** Where the herb grows: {@code aliveworkplace:herb_biomes/<herb>}. */
	public static TagKey<Biome> biomes(HerbBlock herb) {
		return TagKey.create(Registries.BIOME, AliveWorkplace.id("herb_biomes/" + BuiltInRegistries.BLOCK.getKey(herb).getPath()));
	}

	/** Whether the herb could stand at {@code pos} now: free (air, or grass to replace, never water) on fit ground. */
	public static boolean fits(ServerLevel level, BlockPos pos, HerbBlock herb) {
		BlockState here = level.getBlockState(pos);
		boolean free = here.isAir() || here.canBeReplaced() && here.getFluidState().isEmpty() && !(here.getBlock() instanceof HerbBlock);
		return free && herb.defaultBlockState().canSurvive(level, pos);
	}

	/**
	 * Plants a patch of 5 to 9 {@code herb} on fit ground within {@link #RADIUS} blocks of {@code spot} (on the surface
	 * of each column, under leaves); returns how many it planted, fewer where there isn't the ground.
	 */
	public static int patch(ServerLevel level, BlockPos spot, HerbBlock herb, RandomSource random) {
		int want = LEAST + random.nextInt(MOST - LEAST + 1);
		List<BlockPos> fit = new ArrayList<>();
		for (int dx = -RADIUS; dx <= RADIUS; dx++) {
			for (int dz = -RADIUS; dz <= RADIUS; dz++) {
				if (dx * dx + dz * dz > RADIUS * RADIUS) {
					continue;
				}
				BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spot.offset(dx, 0, dz));
				if (fits(level, pos, herb)) {
					fit.add(pos);
				}
			}
		}
		net.minecraft.Util.shuffle(fit, random);
		int planted = 0;
		for (BlockPos pos : fit) {
			if (planted >= want) {
				break;
			}
			level.setBlock(pos, herb.defaultBlockState(), Block.UPDATE_ALL);
			planted++;
		}
		return planted;
	}

	private Herbs() {
	}
}

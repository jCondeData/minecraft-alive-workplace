package io.github.jcondedata.aliveworkplace.work;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Blocks that became workstations in ROADMAP 21.1a (a crafting table, a jukebox, a Mailbox, a Blueprint Table) and were
 * placed before it, in a world saved by an older version: the game files a block's workstation when it's placed, and when
 * a chunk loads it only looks at a section's blocks again if the section had no workstation or bed records at all. So
 * when a section is loaded, the blocks of these kinds it holds that have no record yet get one (called from the game's
 * own check, mixin/PoiManagerMixin).
 */
public final class NewWorkstations {
	private static final Set<Block> ADDED = Set.of(Blocks.CRAFTING_TABLE, Blocks.JUKEBOX, ModBlocks.MAILBOX, ModBlocks.BLUEPRINT_TABLE);

	public static void fillMissing(PoiManager poi, SectionPos section, LevelChunkSection blocks) {
		if (blocks.hasOnlyAir() || !blocks.maybeHas(state -> ADDED.contains(state.getBlock()))) {
			return; // (the palette says no such block here: nearly every section)
		}
		for (int y = 0; y < 16; y++) {
			for (int z = 0; z < 16; z++) {
				for (int x = 0; x < 16; x++) {
					BlockState state = blocks.getBlockState(x, y, z);
					if (!ADDED.contains(state.getBlock())) {
						continue;
					}
					BlockPos pos = section.origin().offset(x, y, z);
					if (poi.getType(pos).isEmpty()) {
						PoiTypes.forState(state).ifPresent(type -> poi.add(pos, type));
					}
				}
			}
		}
	}

	private NewWorkstations() {
	}
}

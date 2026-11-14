package io.github.jcondedata.aliveworkplace.test;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Tests that stand a village far from every player and then "bring someone back" force-load its chunk and wait for it to
 * become entity-ticking. A forced chunk only ticks entities once every chunk within two of it is generated in full, and
 * far from spawn nothing is: on a busy CI runner that generation sometimes took longer than the tests' setup deadline
 * (B1209's night, 2026-11-14 nightly repeats: QaFarSiege1114d, QaFarLegendSite1114d 1 of 10, QaFarHall1114c 3 of 10,
 * "the force-loaded chunk never became entity-ticking"). Generating them on the spot, before the ticket, makes the wait a
 * few ticks every time. Test-only: the mod's own code is never called here.
 */
final class FarChunks {
	private FarChunks() {
	}

	/** Force-loads the chunk with every chunk within two of it generated first, so it ticks entities within a few ticks. */
	static void bringBack(ServerLevel level, ChunkPos chunk) {
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				level.getChunk(chunk.x + dx, chunk.z + dz);
			}
		}
		level.setChunkForced(chunk.x, chunk.z, true);
	}
}

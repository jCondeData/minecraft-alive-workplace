package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendPowers;
import io.github.jcondedata.aliveworkplace.legend.LegendSites;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

/**
 * Lane D QA (lane-d-1114-0708), B1222 from its fix decision: "a hall whose chunk isn't entity-ticking is skipped (its
 * villagers aren't loaded either)"; "it qualifies once someone is there". So a far, unloaded hall nearer the site must
 * neither be loaded nor stop the search: the player's next-nearest hall that is loaded still gets the Legend; and once
 * the far hall's chunk ticks again it is the nearest and gets it.
 */
public class QaFarLegendSite1114dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static boolean loaded(ServerLevel level, BlockPos pos) {
		return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
	}

	private static Legend rare() {
		return Legends.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa1114d_found_portal"), JsonParser.parseString(
			"{\"rarity\": \"rare\", \"job\": \"minecraft:cartographer\", \"title\": \"Found portal\", \"lore\": \"the lore\","
				+ " \"names\": [\"Wren of the portal\"], \"conditions\": [],"
				+ " \"arrive\": [{\"way\": \"found\", \"site\": \"ruined_portal\"}]}").getAsJsonObject());
	}

	private static void own(ServerLevel level, BlockPos hall, ServerPlayer owner) {
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getName().getString());
		entity.setCustomName(Component.literal("Oakvale"));
	}

	//$ gametest_ticks_batch AREA '800' '"qa1114dFarLegendSite"'
	@GameTest(template = AREA, timeoutTicks = 800, batch = "qa1114dFarLegendSite")
	public void aFarUnloadedHallIsSkippedNotLoadedAndQualifiesOnceItTicks(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = helper.makeMockServerPlayerInLevel();
		BlockPos near = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos far = near.offset(1100, 0, 0);
		BlockPos site = near.offset(700, 0, 0); // 700 from the near hall, 400 from the far one
		ChunkPos farChunk = new ChunkPos(far);
		Caravans.Data caravans = Caravans.Data.get(level);
		helper.assertTrue(!loaded(level, far), "setup: the far hall's chunk is already loaded");
		Leftovers.after(helper, () -> {
			caravans.remove(near);
			caravans.remove(far);
			if (loaded(level, far) && level.getBlockState(far).is(ModBlocks.VILLAGE_HALL)) {
				level.setBlock(far, Blocks.AIR.defaultBlockState(), 3);
			}
			level.setChunkForced(farChunk.x, farChunk.z, false);
			Legends.reload(level.getServer().getResourceManager());
			LegendPowers.forget();
		});
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.VILLAGE_HALL);
		own(level, near, owner);
		caravans.setWants(near, Component.literal("Near"), List.of());
		caravans.setWants(far, Component.literal("Far"), List.of());
		Legend legend = rare();
		Legends.setForTest(new java.util.LinkedHashMap<>(Map.of(legend.id(), legend)));

		Optional<LegendSites.Match> first = LegendSites.qualifying(level, owner, "ruined_portal", site);
		helper.assertTrue(!loaded(level, far), "the site check loaded the far hall's chunk");
		helper.assertTrue(first.map(LegendSites.Match::hall).equals(Optional.of(near)),
			"with the nearer hall unloaded, the player's loaded hall 700 blocks off should get the Legend: " + first);

		// Someone goes to the far village: its chunk ticks again, with the player's hall standing there.
		level.setChunkForced(farChunk.x, farChunk.z, true);
		level.setBlock(far, ModBlocks.VILLAGE_HALL.defaultBlockState(), 3);
		own(level, far, owner);
		int[] ticks = {0};
		helper.onEachTick(() -> {
			if (!level.isPositionEntityTicking(far)) {
				helper.assertTrue(++ticks[0] < 700, "setup: the force-loaded far chunk never became entity-ticking");
				return;
			}
			Optional<LegendSites.Match> then = LegendSites.qualifying(level, owner, "ruined_portal", site);
			helper.assertTrue(then.map(LegendSites.Match::hall).equals(Optional.of(far)),
				"once the far village ticks again it is the nearest hall and gets the Legend: " + then);
			helper.succeed();
		});
	}
}

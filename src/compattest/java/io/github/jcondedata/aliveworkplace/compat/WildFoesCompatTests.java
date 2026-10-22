package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonWildFoes;
import io.github.jcondedata.aliveworkplace.hall.Foes;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageQuests;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * B301 with the real Cobblemon: its integration says wild Pokémon count as the village's foes, and where no monster can
 * come (Cobbleverse's MobsBeGone) a wild Pokémon beaten or caught by the hall counts towards "Defeat 8 wild Pokémon".
 */
public class WildFoesCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";
	private static final BlockPos HALL = new BlockPos(3, 2, 3);

	//$ gametest_ticks_batch AREA '60' '"wild_foes"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "wild_foes")
	public void wildPokemonDefeatedCount(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Foes.WILD_POKEMON.present() && Foes.wild(), "Cobblemon's integration didn't fill the wild Pokémon foes");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 10;
		Foes.HOSTILES_FOR_TESTS = false;
		PartnerShowsCompatTests.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Foes.HOSTILES_FOR_TESTS = null;
			level.setBlockAndUpdate(helper.absolutePos(HALL), Blocks.AIR.defaultBlockState());
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) helper.getBlockEntity(HALL)).setLastQuestDay(Long.MAX_VALUE / 2);
		BlockPos hall = helper.absolutePos(HALL);
		long now = level.getGameTime();
		Quest quest = new Quest(UUID.randomUUID(), AliveWorkplace.id("daily/slay"), "hall", Optional.empty(), "", now, now + VillageQuests.LASTS,
			List.of(new Objectives.Kill("monster", 8, false)), new int[1], List.of());
		Stories.post(level, hall, quest);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 2.5);
		CobblemonWildFoes.defeated(player, 3);
		helper.assertTrue(quest.progress[0] == 3, "the wild Pokémon didn't count: " + quest.progress[0]);
		helper.assertTrue(quest.title().getString().equals("Defeat 8 wild Pokémon"), "title: " + quest.title().getString());
		helper.succeed();
	}
}

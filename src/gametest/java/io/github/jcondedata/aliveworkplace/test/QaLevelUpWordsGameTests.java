package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.level.GameType;

/**
 * QA (lane-b-1025-0937): a builder's level-up tells their owner en_us.json's level_up, "%s reached %s level! They work
 * %s%% faster than a novice and have new trades.", with the speed of the wiki's table (builder.md "Speed": 100%, 85%,
 * 70%, 55%, 40% of the delay for levels 1 to 5): a builder reaching Apprentice reads 18% faster (100 / 85), one reaching
 * Master 150% (100 / 40), with the builder's name and the level's name in place.
 */
public class QaLevelUpWordsGameTests {
	private static final String AREA = "aliveworkplace_test:build_area";

	//$ gametest_batch AREA '"qaLevelUpWords"'
	@GameTest(template = AREA, batch = "qaLevelUpWords")
	public void theLevelUpLineGivesTheSpeedOfTheNewLevel(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean traits = io.github.jcondedata.aliveworkplace.people.Traits.ENABLED;
		io.github.jcondedata.aliveworkplace.people.Traits.ENABLED = false; // a clever builder's extra XP would skip a level
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.people.Traits.ENABLED = traits);
		BlockPos table = new BlockPos(2, 2, 2);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		builder.setNoAi(true);
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(table));
		List<Component> seen = new ArrayList<>();
		ServerPlayer owner = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		helper.assertTrue(builder.getVillagerData().getLevel() == 1, "not a novice: " + builder.getVillagerData().getLevel());
		String name = builder.getDisplayName().getString();
		String[][] steps = {{"2", "Apprentice", "18"}, {"3", "Journeyman", "43"}, {"4", "Expert", "82"}, {"5", "Master", "150"}};
		for (String[] step : steps) {
			int now = builder.getVillagerData().getLevel();
			seen.clear();
			BuilderLevels.addXp(helper.getLevel(), builder, VillagerData.getMaxXpPerLevel(now) - builder.getVillagerXp(), owner.getUUID());
			helper.assertTrue(builder.getVillagerData().getLevel() == Integer.parseInt(step[0]), "didn't reach " + step[1] + ": " + builder.getVillagerData().getLevel());
			String expected = name + " reached " + step[1] + " level! They work " + step[2] + "% faster than a novice and have new trades.";
			List<String> lines = seen.stream().map(Component::getString).toList();
			helper.assertTrue(lines.contains(expected), "reaching " + step[1] + " the owner read " + lines + ", expected \"" + expected + "\"");
		}
		helper.succeed();
	}
}

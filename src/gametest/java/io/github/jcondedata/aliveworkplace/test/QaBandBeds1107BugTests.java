package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * QA (lane-c-1107-0052) of hired mercenaries against the village's own count: "they don't count as the village's own (no
 * babies, no beds kept)" (guard/Mercenaries) and B889 ("a hired mercenary ... works here without being the village's").
 * A village whose two villagers each sleep in their own bed hires a band at the hall: the hall's "Sleeping in a bed"
 * line and the village's wellbeing (and so its work pace) should stay what they were.
 */
public class QaBandBeds1107BugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	private static BlockPos bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	/**
	 * Two villagers in their own beds, lit, at noon; a band of three hired at the hall. Before: sleeping in a bed 2 of 2.
	 * After: still 2 of 2 (the band isn't the village's own and keeps no beds), and the wellbeing no lower than before.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaBandBeds1107"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaBandBeds1107")
	public void qaAHiredBandDoesntLeaveTheVillageShortOfBeds(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Expansions.openForTests = true;
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(16), Mercenaries::isMercenary).forEach(Villager::discard);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.setDayTime(time);
		});
		level.setDayTime(6000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		List<BlockPos> beds = List.of(bed(helper, new BlockPos(1, 2, 9)), bed(helper, new BlockPos(3, 2, 9)));
		for (int i = 0; i < 2; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1 + 2 * i, 2, 12));
			v.setNoAi(true);
			helper.assertTrue(CampBeds.claim(level, v, beds.get(i)), "setup: villager " + i + " couldn't take a bed");
		}
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 0.5);
		Leftovers.after(helper, () -> {
			player.getInventory().clearContent();
			level.getServer().getPlayerList().remove(player);
		});
		helper.runAfterDelay(3, () -> {
			VillageNeeds.Needs before = VillageNeeds.count(level, hall);
			helper.assertTrue(before.villagers() == 2 && before.housed() == 2, "setup: " + before);
			player.getInventory().add(new ItemStack(Items.EMERALD, Mercenaries.PRICE_EMERALDS));
			Component said = Mercenaries.hire(level, hall, player);
			helper.assertTrue(Mercenaries.near(level, hall).size() == Mercenaries.BAND, "setup: no band hired: " + said.getString());
			Mercenaries.near(level, hall).forEach(v -> v.setNoAi(true));
			VillageNeeds.Needs after = VillageNeeds.count(level, hall);
			String housed = Component.translatable("screen.aliveworkplace.hall.housed", after.housed(), after.villagers()).getString();
			helper.assertTrue(after.housed() == after.villagers(),
				"with a hired band of " + Mercenaries.BAND + " the hall reads '" + housed + "' (before: 2 of 2): the band, who keep no beds, count as villagers without one");
			helper.assertTrue(after.wellbeing() >= before.wellbeing() - 0.001f,
				"hiring the band lowered the village's wellbeing from " + Math.round(before.wellbeing() * 100) + "% to " + Math.round(after.wellbeing() * 100)
					+ "% (pace " + before.pacePercent() + "% -> " + after.pacePercent() + "%)");
			helper.succeed();
		});
	}
}

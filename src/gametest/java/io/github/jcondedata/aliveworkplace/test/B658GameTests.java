package io.github.jcondedata.aliveworkplace.test;

import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.AREA;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.BLOCK;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.PIP;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.assertFreeLine;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.jobDropdown;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.jobless;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.oneClickJob;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.openPeople;
import static io.github.jcondedata.aliveworkplace.test.QaHallSharedStationsGameTests.village;
import static io.github.jcondedata.aliveworkplace.test.StationsSpecGameTests.listeningPlayer;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;

/**
 * B658: Job ▾'s free line names the free blocks themselves (40.3's "Mason · 1 free Stonecutter"), not the job's first
 * block, for every job whose own block shares a job with a vanilla one; with both kinds free, each is counted.
 */
public class B658GameTests {
	/** A free Music Stand is named as one: "Bard · 1 free Music Stand" (not a Jukebox), and one click makes the Bard. */
	//$ gametest_ticks_batch AREA '200' '"b658MusicStand"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b658MusicStand")
	public void aFreeMusicStandIsNamedAsSuch(GameTestHelper helper) {
		oneClickJob(helper, ModBlocks.MUSIC_STAND, ModVillagers.BARD, "Bard · 1 free Music Stand", "Pip is now a Bard");
	}

	/** A free Postal Desk: "Postman · 1 free Postal Desk" (not a Mailbox). */
	//$ gametest_ticks_batch AREA '200' '"b658PostalDesk"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b658PostalDesk")
	public void aFreePostalDeskIsNamedAsSuch(GameTestHelper helper) {
		oneClickJob(helper, ModBlocks.POSTAL_DESK, ModVillagers.POSTMAN, "Postman · 1 free Postal Desk", "Pip is now a Postman");
	}

	/** A free Apiary: "Beekeeper · 1 free Apiary" (not a Beehive). */
	//$ gametest_ticks_batch AREA '200' '"b658Apiary"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b658Apiary")
	public void aFreeApiaryIsNamedAsSuch(GameTestHelper helper) {
		oneClickJob(helper, ModBlocks.APIARY, ModVillagers.BEEKEEPER, "Beekeeper · 1 free Apiary", "Pip is now a Beekeeper");
	}

	/** A free Carpenter's Bench (the nearer) and a free crafting table: each kind counted, the nearest first. */
	//$ gametest_ticks_batch AREA '200' '"b658BothKinds"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "b658BothKinds")
	public void bothKindsFreeAreEachCounted(GameTestHelper helper) {
		village(helper, 8);
		helper.setBlock(BLOCK, ModBlocks.CARPENTERS_BENCH);
		helper.setBlock(new BlockPos(BLOCK.getX(), BLOCK.getY(), BLOCK.getZ() - 2), Blocks.CRAFTING_TABLE);
		Villager pip = jobless(helper, PIP, "Pip");
		ServerPlayer player = listeningPlayer(helper, new ArrayList<>());
		helper.runAfterDelay(3, () -> {
			ChoiceMenu menu = openPeople(helper, player);
			assertFreeLine(helper, jobDropdown(helper, player, menu, pip), ModVillagers.CARPENTER,
				"Carpenter · 1 free Carpenter's Bench, 1 free Crafting Table");
			helper.succeed();
		});
	}
}

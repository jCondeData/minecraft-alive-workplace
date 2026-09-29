package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;

/** Innkeepers take in travellers, who can be hired; the ones nobody hires move on. */
public class InnGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos COUNTER = new BlockPos(11, 2, 11);

	/** In the morning, with a free bed, one traveller a day comes to stay; when their stay is over they move on. */
	@GameTest(template = AREA, timeoutTicks = 900, batch = "innkeeperTakesInATraveller")
	public void innkeeperTakesInATraveller(GameTestHelper helper) {
		Leftovers.clear(helper);
		long stay = Innkeepers.STAY;
		Leftovers.after(helper, () -> Innkeepers.STAY = stay);
		helper.setDayTime(1000);
		ServerLevel level = helper.getLevel();
		helper.setBlock(COUNTER, ModBlocks.INN_COUNTER);
		for (int x : new int[] {4, 6}) {
			helper.setBlock(new BlockPos(x, 2, 15), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
			helper.setBlock(new BlockPos(x, 2, 16), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		}
		Villager innkeeper = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 12));
		Jobs.employ(level, innkeeper, helper.absolutePos(COUNTER), ModVillagers.INN_COUNTER_POI, ModVillagers.INNKEEPER);
		helper.runAfterDelay(450, () -> {
			List<Villager> guests = Innkeepers.guests(level, helper.absolutePos(COUNTER));
			helper.assertTrue(guests.size() == 1, guests.size() + " guests after a morning (one a day)");
			Villager guest = guests.get(0);
			helper.assertTrue(guest.getVillagerData().getProfession() == VillagerProfession.NITWIT, "a traveller takes no job");
			helper.assertTrue(Innkeepers.traveller(guest).level() >= 2, "level " + Innkeepers.traveller(guest).level());
			helper.assertTrue(ModAttachments.GUESTS_HOSTED.getOrElse(innkeeper, 0) == 1, "hosted: "
				+ ModAttachments.GUESTS_HOSTED.getOrElse(innkeeper, 0));
			Innkeepers.STAY = 10; // their stay is over
			helper.runAfterDelay(250, () -> {
				helper.assertTrue(!guest.isAlive(), "the traveller didn't move on");
				helper.succeed();
			});
		});
	}

	/** Hiring a Journeyman traveller takes 16 emeralds; they take a free job as a Journeyman with the trades on the way. */
	@GameTest(template = AREA, timeoutTicks = 100)
	public void aHiredTravellerStartsAtTheirLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(new BlockPos(5, 2, 5), ModBlocks.BUILDERS_BENCH);
		Villager guest = helper.spawn(EntityType.VILLAGER, new BlockPos(6, 2, 6));
		guest.setVillagerData(guest.getVillagerData().setProfession(VillagerProfession.NITWIT));
		ModAttachments.TRAVELLER.set(guest, new Traveller(level.getGameTime(), 3));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // creative players pay nothing
		player.getInventory().add(new ItemStack(Items.EMERALD, 10));
		helper.assertTrue(!Innkeepers.hire(player, guest), "hired for 10 emeralds");
		player.getInventory().add(new ItemStack(Items.EMERALD, 10));
		helper.assertTrue(Innkeepers.hire(player, guest), "not hired with 20 emeralds");
		helper.assertTrue(player.getInventory().countItem(Items.EMERALD) == 4, "emeralds left: " + player.getInventory().countItem(Items.EMERALD));
		helper.assertTrue(!Innkeepers.isTraveller(guest) && guest.getVillagerData().getProfession() == VillagerProfession.NONE, "still a traveller");
		helper.runAfterDelay(2, () -> {
			Jobs.employ(level, guest, helper.absolutePos(new BlockPos(5, 2, 5)), ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
			helper.assertTrue(guest.getVillagerData().getLevel() == 3, "level " + guest.getVillagerData().getLevel());
			helper.assertTrue(guest.getOffers().size() >= 5, "offers: " + guest.getOffers().size());
			helper.succeed();
		});
	}
}

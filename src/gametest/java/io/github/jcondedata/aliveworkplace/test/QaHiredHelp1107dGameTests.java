package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.camp.CampBeds;
import io.github.jcondedata.aliveworkplace.guard.Mercenaries;
import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
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
 * QA (lane-d-1107-0708) of B990 from its spec, past the fixed test: "hired bands and relief riders leave the hall's
 * villager, housed and fed counts and the wellbeing as they were", and the growth cap's FULL. Relief riders (33.17) come
 * in through {@link Relief#arrive}, the band through the hall's hire.
 */
public class QaHiredHelp1107dGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);

	private static BlockPos bed(GameTestHelper helper, BlockPos foot) {
		helper.setBlock(foot, Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
		helper.setBlock(foot.south(), Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
		return helper.absolutePos(foot.south());
	}

	/** A hall at noon with two own villagers each in their own bed; resets the switches it turns. */
	static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		int edge = Relief.EDGE;
		int cap = VillageGrowth.CAP;
		VillageHalls.RADIUS = 16;
		Relief.EDGE = 5;
		Expansions.openForTests = true;
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48), v -> Mercenaries.isMercenary(v) || Relief.isRider(v)).forEach(v -> {
				if (v.getVehicle() != null) {
					v.getVehicle().discard();
				}
				v.discard();
			});
			Relief.forget();
			VillageHalls.RADIUS = radius;
			Relief.EDGE = edge;
			VillageGrowth.CAP = cap;
			Expansions.openForTests = open;
			level.setDayTime(time);
		});
		level.setDayTime(6000);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		List<BlockPos> beds = List.of(bed(helper, new BlockPos(1, 2, 9)), bed(helper, new BlockPos(3, 2, 9)));
		for (int i = 0; i < 2; i++) {
			Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(1 + 2 * i, 2, 12));
			v.setNoAi(true);
			helper.assertTrue(CampBeds.claim(level, v, beds.get(i)), "setup: villager " + i + " couldn't take a bed");
		}
		return helper.absolutePos(HALL);
	}

	static ServerPlayer payer(GameTestHelper helper, BlockPos hall) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.moveTo(hall.getX() + 0.5, hall.getY() + 1, hall.getZ() + 0.5);
		Leftovers.after(helper, () -> {
			player.getInventory().clearContent();
			helper.getLevel().getServer().getPlayerList().remove(player);
		});
		player.getInventory().add(new ItemStack(Items.EMERALD, 64));
		return player;
	}

	private static String housed(VillageNeeds.Needs n) {
		return Component.translatable("screen.aliveworkplace.hall.housed", n.housed(), n.villagers()).getString();
	}

	/**
	 * Three relief riders from an allied village arrive at a village of two, each in their own bed. The hall still reads
	 * 'Sleeping in a bed: 2 of 2', counts 2 villagers and 2 fed adults as before, and the wellbeing doesn't drop; the
	 * riders (Guards) stand guard (the B990 decision: hired Guards count among the guards).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHiredHelp1107dRiders"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiredHelp1107dRiders")
	public void reliefRidersLeaveTheVillagesBedsMealsAndWellbeingAsTheyWere(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.runAfterDelay(3, () -> {
			VillageNeeds.Needs before = VillageNeeds.count(level, hall);
			helper.assertTrue(before.villagers() == 2 && before.housed() == 2 && before.guards() == 0, "setup: " + before);
			Relief.arrive(level, new Relief.Band(GlobalPos.of(level.dimension(), hall), GlobalPos.of(level.dimension(), hall.offset(200, 0, 0)),
				Component.literal("Oakford"), 3, level.getGameTime(), level.getGameTime(), false));
			List<Villager> riders = Relief.riders(level, hall);
			helper.assertTrue(riders.size() == 3, "setup: " + riders.size() + " relief riders arrived, not 3");
			for (Villager r : riders) {
				r.stopRiding();
				r.setNoAi(true);
				r.teleportTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 2.5); // well inside the circle
			}
			VillageNeeds.Needs now = VillageNeeds.count(level, hall);
			String expected = Component.translatable("screen.aliveworkplace.hall.housed", 2, 2).getString();
			helper.assertTrue(housed(now).equals(expected), "with 3 relief riders the hall reads '" + housed(now) + "', not '" + expected + "'");
			helper.assertTrue(now.villagers() == 2 && now.adults() == 2 && now.fed() == before.fed(),
				"with 3 relief riders the village counts " + now.villagers() + " villagers, " + now.adults() + " adults, " + now.fed() + " fed (before 2, 2, " + before.fed() + ")");
			helper.assertTrue(now.wellbeing() >= before.wellbeing() - 0.001f,
				"relief riders lowered the wellbeing from " + Math.round(before.wellbeing() * 100) + "% to " + Math.round(now.wellbeing() * 100) + "%");
			helper.assertTrue(now.guards() == 3, "3 relief riders (Guards) count as " + now.guards() + " guards, not 3 (the B990 decision)");
			helper.succeed();
		});
	}

	/**
	 * The growth cap: a village of two whose cap is three hires a band of three. It isn't full (its own villagers are two),
	 * so the hall doesn't say 'the village is full' while the band is there; it is full once a third own villager comes.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaHiredHelp1107dCap"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaHiredHelp1107dCap")
	public void aHiredBandDoesntFillTheVillageUpToItsGrowthCap(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = payer(helper, hall);
		helper.runAfterDelay(3, () -> {
			VillageGrowth.CAP = 3; // a Hamlet's cap: CAP + 0
			helper.assertTrue(VillageRanks.of(level, hall) == VillageRanks.Rank.HAMLET, "setup: the hall isn't a Hamlet");
			Component said = Mercenaries.hire(level, hall, player);
			List<Villager> band = Mercenaries.near(level, hall);
			helper.assertTrue(band.size() == Mercenaries.BAND, "setup: no band hired: " + said.getString());
			band.forEach(v -> v.setNoAi(true));
			VillageNeeds.Needs now = VillageNeeds.count(level, hall);
			VillageGrowth.Blocker blocker = VillageGrowth.blocker(level, hall, now, 0);
			helper.assertTrue(blocker != VillageGrowth.Blocker.FULL,
				"a village of 2 (cap 3) with a hired band of 3 is held back as FULL (counted " + now.villagers() + " villagers)");
			// One more of its own: now it is full.
			Villager third = helper.spawn(EntityType.VILLAGER, new BlockPos(5, 2, 12));
			third.setNoAi(true);
			VillageNeeds.Needs full = VillageNeeds.count(level, hall);
			helper.assertTrue(full.villagers() == 3 && VillageGrowth.blocker(level, hall, full, 0) == VillageGrowth.Blocker.FULL,
				"a third own villager (cap 3) didn't make the village FULL: " + full.villagers() + " villagers, " + VillageGrowth.blocker(level, hall, full, 0));
			helper.succeed();
		});
	}
}

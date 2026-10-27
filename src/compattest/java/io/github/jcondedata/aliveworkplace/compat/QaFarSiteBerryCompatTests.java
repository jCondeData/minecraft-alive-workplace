package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.berry.BerryBreeders;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.AreaJobs;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Reach;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane E, 2026-10-27) for 36.20 with Cobblemon: the berry breeder's plot follows the village-wide rule through her
 * own right-click with a Field Marker (a plot 150 blocks off in a village of radius 160 is taken; one 100 outside the
 * village is taken; one 140 out is refused; {@code berryPlotReach} 16 refuses 20), and her refusal reads as the others do.
 */
public class QaFarSiteBerryCompatTests {
	private static final String AREA = CompatGameTests.AREA;
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos COMPOSTER = new BlockPos(3, 2, 3);

	private static boolean offer(GameTestHelper helper, ServerPlayer player, Villager breeder, BlockPos centre) {
		ServerLevel level = helper.getLevel();
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(centre.offset(-2, 0, -2)), Optional.of(centre.offset(2, 0, 2))));
		player.setItemInHand(InteractionHand.MAIN_HAND, marker);
		player.moveTo(breeder.getX() + 1.5, breeder.getY(), breeder.getZ(), 90f, 0f);
		player.connection.handleInteract(net.minecraft.network.protocol.game.ServerboundInteractPacket.createInteractionPacket(breeder, false,
			InteractionHand.MAIN_HAND, new Vec3(0, breeder.getBbHeight() / 2, 0)));
		boolean taken = ModAttachments.BERRY_PLOT.has(breeder);
		ModAttachments.BERRY_PLOT.remove(breeder);
		player.closeContainer();
		return taken;
	}

	//$ gametest_ticks_batch AREA '200' '"qa_far_site_berry"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_far_site_berry")
	public void theBerryBreederTakesAPlotAnywhereInTheVillageAndFarOutsideIt(GameTestHelper helper) {
		PartnerShowsCompatTests.clearLeftovers(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = VillageProtection.ENABLED;
		int radius = VillageHalls.RADIUS;
		int reach = AreaJobs.BERRY_PLOT_REACH;
		VillageProtection.ENABLED = false;
		PartnerShowsCompatTests.after(helper, () -> {
			VillageProtection.ENABLED = protection;
			VillageHalls.RADIUS = radius;
			AreaJobs.BERRY_PLOT_REACH = reach;
			VillageHalls.forgetNearest();
		});
		helper.assertTrue(BerryBreeders.available(), "no berry breeders with Cobblemon here");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
		Villager breeder = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		breeder.setNoAi(true);
		BlockPos composter = helper.absolutePos(COMPOSTER);
		Jobs.employ(level, breeder, composter, PoiTypes.FARMER, ModVillagers.BERRY_BREEDER);
		helper.assertTrue(BerryBreeders.isBreeder(breeder), "not a berry breeder");
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		player.setGameMode(GameType.SURVIVAL);
		helper.assertTrue(AreaJobs.BERRY_PLOT_REACH == 128, "berryPlotReach isn't 128 but " + AreaJobs.BERRY_PLOT_REACH);

		VillageHalls.RADIUS = 160;
		VillageHalls.forgetNearest();
		helper.assertTrue(offer(helper, player, breeder, composter.east(150)), "a plot 150 off in a village of radius 160 was refused");
		VillageHalls.RADIUS = 16;
		VillageHalls.forgetNearest();
		helper.assertTrue(offer(helper, player, breeder, composter.east(100)), "a plot 100 outside the village was refused");
		helper.assertTrue(!offer(helper, player, breeder, composter.east(140)), "a plot 140 outside the village was taken");
		String village = VillageHalls.name(level, helper.absolutePos(HALL)).getString();
		String line = Reach.refusal(level, composter, "message.aliveworkplace.berry_plot.too_far", 140, AreaJobs.workBlock(level, composter), 128).getString();
		String want = "That spot is outside " + village + " and 140 blocks from the Composter the berry breeder works at — keep it within 128.";
		helper.assertTrue(line.equals(want), "her refusal reads '" + line + "', not '" + want + "'");
		AreaJobs.BERRY_PLOT_REACH = 16;
		helper.assertTrue(!offer(helper, player, breeder, composter.east(20)), "a plot 20 out was taken with berryPlotReach 16");
		helper.succeed();
	}
}

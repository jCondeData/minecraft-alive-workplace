package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.berry.BerryBreederWork;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Pace;
import io.github.jcondedata.aliveworkplace.work.Partners;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;

/**
 * QA (B53, from its Expected): a Berry Breeder's partners count once and the bonus stays under {@code maxWorkPace}.
 * With the cap set to 125% (work at most 1.25x the usual pace, so no less than 80% of the time), two partners that
 * would bring her to 70% are held at 80%: 16 of 20 ticks. The old code (the capped pace times the partners again) gave
 * 11, past the cap.
 */
public class QaBerryPaceCompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;

	//$ gametest_ticks_batch AREA '200' '"qa_berry_pace_cap"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qa_berry_pace_cap")
	public void aBerryBreedersPartnersStayUnderTheWorkPaceCap(GameTestHelper helper) {
		int max = Pace.MAX_PERCENT;
		PartnerShowsCompatTests.after(helper, () -> Pace.MAX_PERCENT = max);
		ServerLevel level = helper.getLevel();
		BlockPos composter = new BlockPos(2, 2, 2);
		helper.setBlock(composter, net.minecraft.world.level.block.Blocks.COMPOSTER);
		Villager breeder = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		breeder.setNoAi(true);
		Jobs.employ(level, breeder, helper.absolutePos(composter), net.minecraft.world.entity.ai.village.poi.PoiTypes.FARMER, ModVillagers.BERRY_BREEDER);
		BlockPos pasture = PastureCompatTests.pasture(helper, new BlockPos(10, 2, 10));
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		PastureCompatTests.pastured(helper, pasture, player, "bulbasaur", Direction.NORTH);
		PastureCompatTests.pastured(helper, pasture, player, "caterpie", Direction.WEST);
		helper.runAfterDelay(10, () -> {
			Partners.forget(breeder);
			helper.assertTrue(Partners.helpers(breeder).size() == 2, "partners: " + Partners.helpers(breeder));
			Pace.MAX_PERCENT = 200;
			int free = BerryBreederWork.mulchTicks(breeder);
			Pace.MAX_PERCENT = 125;
			int capped = BerryBreederWork.mulchTicks(breeder);
			Pace.MAX_PERCENT = max;
			helper.assertTrue(free == 14, "two partners at the default cap: " + free + " of 20 ticks, expected 14");
			helper.assertTrue(capped == 16, "two partners with maxWorkPace 125: " + capped + " of 20 ticks, expected 16 (the cap)");
			breeder.discard();
			helper.succeed();
		});
	}
}

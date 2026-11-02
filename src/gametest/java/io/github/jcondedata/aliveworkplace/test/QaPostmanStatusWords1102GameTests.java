package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * QA (player-visible text, kin of the B413 counting sweep): the postman's status, sneak-right-clicked with an empty
 * hand, counts in the singular for one ("1 parcel delivered") and in the plural for two.
 */
public class QaPostmanStatusWords1102GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	private static String status(GameTestHelper helper, int delivered) {
		Leftovers.clear(helper);
		Villager postman = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		postman.setNoAi(true);
		postman.setVillagerData(postman.getVillagerData().setProfession(ModVillagers.POSTMAN));
		ModAttachments.MAIL_DELIVERED.set(postman, delivered);
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, "PostQa" + delivered);
		client.seen().clear();
		// the sneak-right-click with an empty hand, through the server's own packet handler
		client.player().setShiftKeyDown(true);
		StationsSpecGameTests.rightClick(client.player(), postman, ItemStack.EMPTY, true);
		return client.seen().stream().map(Component::getString).filter(s -> s.contains("delivered")).findFirst().orElse("(no status)");
	}

	/** One parcel delivered: "1 parcel delivered", never "1 parcels delivered". */
	//$ gametest_ticks_batch AREA '40' '"qaPostmanOneParcel1102"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaPostmanOneParcel1102")
	public void qaOneParcelReadsInTheSingular(GameTestHelper helper) {
		String line = status(helper, 1);
		helper.assertTrue(line.contains("1 parcel delivered"), "the postman's status for one parcel: '" + line + "'");
		helper.succeed();
	}

	/** Two parcels: "2 parcels delivered". */
	//$ gametest_ticks_batch AREA '40' '"qaPostmanTwoParcels1102"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaPostmanTwoParcels1102")
	public void qaTwoParcelsReadInThePlural(GameTestHelper helper) {
		String line = status(helper, 2);
		helper.assertTrue(line.contains("2 parcels delivered"), "the postman's status for two parcels: '" + line + "'");
		helper.succeed();
	}
}

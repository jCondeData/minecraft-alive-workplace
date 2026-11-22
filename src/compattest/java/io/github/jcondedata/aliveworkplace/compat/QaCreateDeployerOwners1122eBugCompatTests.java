package io.github.jcondedata.aliveworkplace.compat;

import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.DEPLOYER;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.TARGET;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.deployer;
import static io.github.jcondedata.aliveworkplace.compat.QaCreateDeployerOnOurThings1102eCompatTests.start;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane E, 2026-11-22) BUG test, unregistered: B843's other half. create.md: "A Deployer acts as a fake player: one a
 * player placed acts for him (his hall set down is his)". The hall is his (owner and owner name, see
 * {@link QaCreateDeployerOwners1122eCompatTests}), but VillageHallBlock.setPlacedBy writes the chronicle's founding line
 * with the placer's display name, which for Create's DeployerFakePlayer is "a rogue Deployer": the village's chronicle
 * reads "a rogue Deployer founded the Village Hall of Sunmere" while the hall says the player owns it.
 */
public class QaCreateDeployerOwners1122eBugCompatTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_compat:huge_area";

	/**
	 * The founding line of a hall a player's deployer set down: it names the player (or nobody), never the machine
	 * ("a rogue Deployer founded the Village Hall of ..." while the hall says the player owns it).
	 */
	//$ gametest_ticks_batch AREA '400' '"qa_e1122_downers_player_hall_chronicle"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qa_e1122_downers_player_hall_chronicle")
	public void theChronicleOfAHallAPlayersDeployerSetsDownNamesNoDeployer(GameTestHelper helper) {
		start(helper);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		deployer(helper, DEPLOYER, player.getUUID(), new ItemStack(ModBlocks.VILLAGE_HALL.asItem()));
		helper.runAfterDelay(240, () -> {
			QaCreateDeployerOwners1122eCompatTests.logOut(helper, player);
			helper.assertTrue(helper.getBlockState(TARGET).is(ModBlocks.VILLAGE_HALL), "the player's deployer hasn't placed the hall: " + helper.getBlockState(TARGET));
			VillageHallBlockEntity hall = helper.getBlockEntity(TARGET);
			List<String> founded = QaCreateDeployerOwners1122eCompatTests.founders(hall);
			helper.assertTrue(!founded.isEmpty(), "the hall's chronicle has no founding line: " + hall.chronicle());
			helper.assertTrue(!QaCreateDeployerOwners1122eCompatTests.namesADeployer(founded), "the chronicle of the hall " + player.getGameProfile().getName()
				+ "'s deployer set down (owner \"" + hall.ownerName() + "\") names a Deployer as its founder: " + founded);
			helper.succeed();
		});
	}
}

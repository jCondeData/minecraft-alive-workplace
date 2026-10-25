package io.github.jcondedata.aliveworkplace.test;

import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * QA (lane-b-1024-1537) bug tests found while verifying B366: B393, the builder's status list in the singular (fixed by B402).
 * B394's off-hand Book and Quill test stays on tests/builder-bugfixes-1024 until that bug is fixed.
 */
public class QaBuilderBugFixesBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";

	/**
	 * B366's "a .one form, as the mod's other counted lines have", for the builder's status: his chest holds everything
	 * for the test hut but the torch. A sneak-right-click on him with an empty hand heads the list "Still to bring for
	 * the Test Hut: 1 item of 1 kind", not "1 items of 1 kinds" (message.aliveworkplace.site_list.chat_heading has no
	 * .one form).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaFixBugStatusOne"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaFixBugStatusOne")
	public void theBuildersStatusListWithOneThingToBringReadsInTheSingular(GameTestHelper helper) {
		QaBuilderBugFixesGameTests.OneShort s = QaBuilderBugFixesGameTests.oneThingShort(helper, "qafxH");
		ServerPlayer p = s.client().player();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.setShiftKeyDown(true);
		QaStageSixGameTests.clickVillager(p, s.builder(), s.client().seen());
		p.setShiftKeyDown(false);
		String hut = io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(
			net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut")).getString();
		String heading = "Still to bring for the " + hut + ": 1 item of 1 kind";
		List<String> seen = QaBuilderBugFixesGameTests.texts(s.client().seen());
		helper.assertTrue(seen.stream().anyMatch(t -> t.startsWith("Still to bring for the ")), "no list in the builder's status: " + seen);
		helper.assertTrue(seen.stream().anyMatch(t -> t.startsWith(heading + "\n") || t.equals(heading)),
			"the builder's status heads the list: " + seen.stream().filter(t -> t.startsWith("Still to bring")).map(t -> t.split("\n")[0]).toList()
				+ ", expected \"" + heading + "\"");
		helper.succeed();
	}
}

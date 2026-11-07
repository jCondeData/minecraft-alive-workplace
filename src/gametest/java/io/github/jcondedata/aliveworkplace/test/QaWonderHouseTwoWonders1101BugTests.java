package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-b-1101-0038), B714's builder side ("a house blueprint placed inside such a Wonder" is refused): the fix asks
 * WonderPlans.wonderOver for the first Wonder whose layout meets the house and refuses only if that one is going up. A
 * house straddling a finished Wonder (listed first) and a Wonder next to it that waits for its goods is therefore taken,
 * and goes up where the waiting Wonder's stage I will.
 */
public class QaWonderHouseTwoWonders1101BugTests {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final ResourceLocation FOLLY = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_folly");
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/**
	 * A finished Test Folly on x 2..10 and a second one begun on x 11..19 (laid out, waiting for its bread); the player
	 * hands the builder a Test Hut (5 wide) on x 8..12, over both. Expected: refused ("overlaps another build in
	 * progress"), as when it lies inside the waiting one alone (QaWonderPlanBug1031Tests).
	 */
	//$ gametest_ticks_batch HUGE '60' '"qaWonderHouseTwoWonders1101"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "qaWonderHouseTwoWonders1101")
	public void qaABuilderRefusesAHouseOverAFinishedAndAWaitingWonder(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection);
		BlockPos hallAt = new BlockPos(24, 2, 24);
		helper.setBlock(hallAt, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(hallAt);
		Leftovers.wonderGround(helper, hall);
		helper.assertTrue(Wonders.loaded().get(FOLLY) != null, "the test folly isn't loaded: " + Wonders.problems());
		WonderManager manager = WonderManager.get(level);
		BlockPos doneAt = helper.absolutePos(new BlockPos(2, 2, 2));
		WonderSite done = manager.begin(FOLLY, hall, null, "", 1);
		Leftovers.after(helper, () -> manager.remove(done.id()));
		manager.place(done.id(), new BlueprintData.Placement(Ids.of(level.dimension()), doneAt, Rotation.NONE, Mirror.NONE), doneAt.offset(-1, 0, -1));
		manager.finish(done.id(), 2);
		BlockPos waitingAt = helper.absolutePos(new BlockPos(11, 2, 2));
		WonderSite waiting = manager.begin(FOLLY, hall, null, "", 3);
		Leftovers.after(helper, () -> manager.remove(waiting.id()));
		manager.place(waiting.id(), new BlueprintData.Placement(Ids.of(level.dimension()), waitingAt, Rotation.NONE, Mirror.NONE), waitingAt.offset(0, 0, -1));
		helper.assertTrue(manager.site(done.id()).map(WonderSite::done).orElse(false), "set-up: the first Wonder isn't finished");
		helper.assertTrue(manager.site(waiting.id()).map(s -> s.goingUp() && !s.started() && s.parts().isEmpty()).orElse(false), "set-up: a waiting Wonder");

		BlockPos benchAt = new BlockPos(26, 2, 4);
		helper.setBlock(benchAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchAt.south());
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(benchAt));
		Vec3i size = BlueprintLibrary.get(level, HUT).orElseThrow().size();
		ItemStack blueprint = BlueprintItem.create(HUT, size);
		BlueprintData.Placement over = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(8, 2, 4)), Rotation.NONE, Mirror.NONE);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(over)));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaW2"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		BuildSite taken = Builders.activeSite(level, builder);
		if (taken != null) {
			Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(taken.id()));
		}
		helper.assertTrue(taken == null, "the builder took a house over a finished Test Folly and the waiting one beside it; he said: "
			+ c.seen().stream().map(Component::getString).toList());
		helper.succeed();
	}
}

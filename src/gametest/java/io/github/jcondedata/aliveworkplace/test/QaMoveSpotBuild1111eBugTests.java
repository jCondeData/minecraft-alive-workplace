package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane E, 2026-11-11), the other side of B1174: a building's planned new spot against a new build. 45.2's fit check
 * keeps a move off the village's buildings "finished or going up" and (B1174) off another move's new spot, so a Mover
 * never rebuilds onto something. The same must hold the other way round: with A's move to S confirmed, a player handing
 * a builder a blueprint placed on S is refused, as the builder refuses a spot over another build in progress, and a spot
 * clear of S is taken (the control). Otherwise the builder raises a house on S and the Mover then puts A up over it.
 * The village from QaMoveSpotSaves1111eGameTests; the builder's side from QaWonderOverlap1101GameTests.
 */
public class QaMoveSpotBuild1111eBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final BlockPos BENCH = new BlockPos(18, 2, 4);

	/** The player hands the builder a Test Hut placed unturned at {@code rel}; the site he took, or null; what he said in {@code said}. */
	private static BuildSite handHut(GameTestHelper helper, QaMoveSpotSaves1111eGameTests.Village v, Villager builder,
		QaStageFiveGameTests.Client client, BlockPos rel) {
		ItemStack blueprint = BlueprintItem.create(HUT, BlueprintLibrary.get(v.level(), HUT).orElseThrow().size());
		BlueprintData.Placement at = new BlueprintData.Placement(v.level().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow().withPlacement(Optional.of(at)));
		client.seen().clear();
		client.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(client.player(), builder, client.seen());
		BuildSite taken = Builders.activeSite(v.level(), builder);
		if (taken != null) {
			Leftovers.after(helper, () -> BuildSiteManager.get(v.level()).remove(taken.id()));
		}
		return taken;
	}

	/** A's move to S confirmed: a Test Hut handed to the builder on S is refused; one clear of S is taken. */
	//$ gametest_ticks_batch AREA '100' '"qaMoveBuild1111eOverS"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaMoveBuild1111eOverS")
	public void aBuildOnAPlannedMovesSpotIsRefused(GameTestHelper helper) {
		QaMoveSpotSaves1111eGameTests.Village v = QaMoveSpotSaves1111eGameTests.stage(helper);
		boolean protection = VillageProtection.ENABLED;
		VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> VillageProtection.ENABLED = protection);
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, BENCH.south());
		builder.setNoAi(true);
		Builders.employ(v.level(), builder, helper.absolutePos(BENCH));
		QaStageFiveGameTests.Client client = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaMB"));
		helper.runAfterDelay(2, () -> {
			QaMoveSpotSaves1111eGameTests.planA(helper, v);
			BuildSite over = handHut(helper, v, builder, client, new BlockPos(11, 2, 3)); // S: x 11-15, z 3-7
			List<String> said = client.seen().stream().map(Component::getString).toList();
			helper.assertTrue(over == null, "with A's move to S confirmed, the builder took a Test Hut on S (x 11-15, z 3-7): the Mover will "
				+ "put A up over it; he said " + said);
			// B1185's fix: he's told the spot is kept for A's move (en_us message.aliveworkplace.assign.move_spot)
			String kept = "That spot is kept for " + io.github.jcondedata.aliveworkplace.blueprint.Blueprints.displayName(HUT).getString() + ", which is moving there.";
			helper.assertTrue(said.contains(kept), "the refusal over S should read \"" + kept + "\"; he said " + said);
			BuildSite clear = handHut(helper, v, builder, client, new BlockPos(11, 2, 15));
			helper.assertTrue(clear != null, "control: a Test Hut clear of S (x 11-15, z 15-19) wasn't taken; he said "
				+ client.seen().stream().map(Component::getString).toList());
			helper.succeed();
		});
	}
}

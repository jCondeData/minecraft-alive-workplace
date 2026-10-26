package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/**
 * Bug test (lane-b-1024-0938), the builder's reach as B355 is the Storehouse's: a placed blueprint whose middle is 48.5
 * blocks from the builder's table (past the 48-block reach) is refused, as it should be, but the player reads "That spot
 * is 48 blocks from the builder's Blueprint Table — keep it within 48.": the numbers say it is within the reach.
 * Builders.assign truncates the distance ((int) distance). Expected: the number shown is past the reach whenever the
 * spot is refused (round up, or one decimal).
 */
public class QaBuilderLimitsBugTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:build_area";
	private static final BlockPos TABLE = new BlockPos(2, 2, 2);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 3);
	private static final Pattern TOO_FAR = Pattern.compile("That spot is (?:outside .+ and )?([0-9.,]+) blocks from the builder's Blueprint Table — keep it within ([0-9]+)\\.");

	private static final net.minecraft.resources.ResourceLocation TEST_HUT = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** A placed test-hut blueprint whose outline's centre is {@code offset} from {@code bench}. */
	private static ItemStack placedHut(ServerLevel level, BlockPos bench, Vec3i offset) {
		Vec3i size = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		var atBench = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), bench,
			net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
		BlockPos centre = io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(atBench, size).getCenter();
		BlockPos origin = bench.offset(bench.subtract(centre)).offset(offset);
		var placement = new io.github.jcondedata.aliveworkplace.blueprint.BlueprintData.Placement(level.dimension().location(), origin,
			net.minecraft.world.level.block.Rotation.NONE, net.minecraft.world.level.block.Mirror.NONE);
		ItemStack blueprint = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.create(TEST_HUT, size);
		blueprint.set(io.github.jcondedata.aliveworkplace.registry.ModComponents.BLUEPRINT,
			io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(blueprint).orElseThrow().withPlacement(java.util.Optional.of(placement)));
		return blueprint;
	}

	private static double distance(ServerLevel level, ItemStack blueprint, BlockPos bench) {
		var placement = io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem.data(blueprint).orElseThrow().placement().orElseThrow();
		Vec3i size = io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		return Math.sqrt(io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline.bounds(placement, size).getCenter().distSqr(bench));
	}

	//$ gametest_ticks_batch AREA '60' '"qaLimitsReachBug"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLimitsReachBug")
	public void aSpotJustPastTheReachIsRefusedWithANumberPastTheReach(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean enabled = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		Leftovers.after(helper, () -> io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = enabled);
		ServerLevel level = helper.getLevel();
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, VILLAGER);
		builder.setNoAi(true);
		BlockPos bench = helper.absolutePos(TABLE);
		Builders.employ(level, builder, bench);
		int reach = Builders.MAX_SITE_DISTANCE;
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qalB"));
		ServerPlayer p = c.player();
		ItemStack spot = placedHut(level, bench, new Vec3i(reach, 0, 7));
		double distance = distance(level, spot, bench);
		helper.assertTrue(distance > reach && distance < reach + 1, "the spot is " + distance + " blocks off");
		p.setItemInHand(InteractionHand.MAIN_HAND, spot);
		QaStageSixGameTests.clickVillager(p, builder, c.seen());
		helper.assertTrue(Builders.activeSite(level, builder) == null, "a spot " + distance + " blocks off was taken");
		helper.assertTrue(p.getMainHandItem().is(ModItems.BLUEPRINT), "the refused blueprint went");
		List<String> said = c.seen().stream().map(Component::getString).toList();
		Matcher m = said.stream().map(TOO_FAR::matcher).filter(Matcher::matches).findFirst().orElse(null);
		helper.assertTrue(m != null, "no too-far line: " + said);
		double shown = Double.parseDouble(m.group(1).replace(',', '.'));
		helper.assertTrue(shown > Integer.parseInt(m.group(2)),
			"a spot " + String.format("%.2f", distance) + " blocks off was refused with \"" + m.group(0) + "\": the numbers say it is within reach");
		helper.succeed();
	}
}

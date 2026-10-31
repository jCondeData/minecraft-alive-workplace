package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-31), village protection against a stranger's builder. Guide village-protection.md: inside a protected
 * village "only the owner, their friends and operators may break or place blocks". A stranger whose own builder works
 * just outside places a blueprint whose footprint reaches into the protected village (the click itself outside it) and
 * hands it over: the builder takes it, and clearing the site breaks the owner's blocks inside the village. Nothing in
 * Builders' hand-over or the builder's work asks VillageProtection.
 */
public class QaProtectedBuildBug1031Tests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** A stranger's builder outside a protected village must not take a build that reaches into it, nor break the owner's wall there. */
	//$ gametest_ticks_batch AREA '1400' '"qaProtectedBuild1031"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "qaProtectedBuild1031")
	public void qaAStrangersBuilderKeepsOutOfAProtectedVillage(GameTestHelper helper) {
		var owner = ProtectionSpecGameTests.player(helper);
		ProtectionSpecGameTests.protectedHall(helper, owner); // hall at (11, 2, 11), reach 16
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		helper.setDayTime(2000);
		// the owner's wall inside his village, 14 blocks from the hall
		BlockPos wall = new BlockPos(25, 3, 11);
		helper.setBlock(wall, Blocks.COBBLESTONE);

		// the stranger's builder works 17 blocks out, outside the village
		BlockPos benchAt = new BlockPos(28, 2, 18);
		helper.setBlock(benchAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchAt.south());
		Builders.employ(level, builder, helper.absolutePos(benchAt));
		// his hut: x 24..28 (13 to 17 blocks from the hall), its front edge outside, its back over the owner's wall
		Vec3i size = BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size();
		BlockPos origin = helper.absolutePos(new BlockPos(24, 2, 9));
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, size);
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow()
			.withPlacement(Optional.of(new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE))));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPB"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		BuildSite taken = Builders.activeSite(level, builder);
		if (taken == null) {
			helper.succeed(); // refused: nothing of the owner's is touched
			return;
		}
		Leftovers.after(helper, () -> {
			if (BuildSiteManager.get(level).get(taken.id()) != null) {
				BuildSiteManager.get(level).remove(taken.id());
			}
		});
		helper.runAfterDelay(1200, () -> {
			boolean broken = !helper.getBlockState(wall).is(Blocks.COBBLESTONE);
			helper.fail("a stranger's builder took a build reaching 3 blocks into a protected village (he said "
				+ c.seen().stream().map(Component::getString).toList() + "); 1200 ticks later the owner's cobblestone inside the village is "
				+ (broken ? "gone (" + helper.getBlockState(wall).getBlock().getName().getString() + ")" : "still there") + ", the build " + taken.stage());
		});
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * B718: a build, quarry or field handed to a villager is checked against village protection over its whole footprint,
 * not just where it was clicked. The village is {@link ProtectionSpecGameTests#protectedHall}'s: hall at (11, 2, 11),
 * reach 16, owned by "Owner". The refusals of a stranger's build and quarry are QA's (QaProtectedBuildBug1031Tests,
 * QaProtectedQuarryBug1031Tests); these are the field's refusal and the hand-overs that must still be taken.
 */
public class ProtectedHandOverGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	/** The test hut placed at {@code at} (relative), for {@code builder} employed at a table at {@code benchAt}. */
	private static BuildSite handHut(GameTestHelper helper, QaStageFiveGameTests.Client c, BlockPos benchAt, BlockPos at) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(benchAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchAt.south());
		builder.setNoAi(true);
		Builders.employ(level, builder, helper.absolutePos(benchAt));
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size());
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow()
			.withPlacement(Optional.of(new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE))));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		if (site != null) {
			BuildSiteManager.get(level).remove(site.id());
		}
		return site;
	}

	private static List<String> said(QaStageFiveGameTests.Client c) {
		return c.seen().stream().map(Component::getString).toList();
	}

	/** The village's owner hands over the same build QA's stranger did (its back 3 blocks into his village): it's taken. */
	//$ gametest_ticks_batch AREA '60' '"protectedHandOverOwnersBuild"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "protectedHandOverOwnersBuild")
	public void theOwnersBuildReachingIntoHisVillageIsTaken(GameTestHelper helper) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("phoA"));
		ProtectionSpecGameTests.protectedHall(helper, c.player());
		helper.setBlock(new BlockPos(25, 3, 11), Blocks.COBBLESTONE);
		BuildSite site = handHut(helper, c, new BlockPos(28, 2, 18), new BlockPos(24, 2, 9));
		helper.assertTrue(site != null, "the village's owner's build reaching into his own protected village was refused: " + said(c));
		helper.succeed();
	}

	/** A stranger's build whose whole footprint (x 22..26, z 24..28) lies outside a protected village is taken. */
	//$ gametest_ticks_batch AREA '60' '"protectedHandOverOutside"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "protectedHandOverOutside")
	public void aStrangersBuildOutsideAProtectedVillageIsTaken(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("phoB"));
		// the footprint's corner nearest the hall, (22, 24), is 17.03 blocks from it: just outside a reach of 16
		BuildSite site = handHut(helper, c, new BlockPos(16, 2, 27), new BlockPos(22, 2, 24));
		helper.assertTrue(site != null, "a stranger's build entirely outside a protected village was refused: " + said(c));
		helper.succeed();
	}

	/** A farmer at a composter at (22, 2, 26), outside the village, handed a field from (25, 2) to (28, 20). */
	private static boolean handField(GameTestHelper helper, QaStageFiveGameTests.Client c) {
		ServerLevel level = helper.getLevel();
		BlockPos composterAt = new BlockPos(22, 2, 26);
		helper.setBlock(composterAt, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, composterAt.west());
		farmer.setNoAi(true);
		Jobs.employ(level, farmer, helper.absolutePos(composterAt), PoiTypes.FARMER, VillagerProfession.FARMER);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(helper.absolutePos(new BlockPos(25, 1, 2))),
			Optional.of(helper.absolutePos(new BlockPos(28, 1, 20)))));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(c.player(), farmer, c.seen());
		boolean taken = Fields.hasField(farmer);
		farmer.discard();
		return taken;
	}

	/**
	 * A stranger's Field Marker with both corners clicked just outside a protected village, the field between them running
	 * through its edge (column x = 25, z = 11 is 14 from the hall): his farmer doesn't take it, and he's told whose village it is.
	 */
	//$ gametest_ticks_batch AREA '60' '"protectedHandOverStrangersField"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "protectedHandOverStrangersField")
	public void aStrangersFieldReachingIntoAProtectedVillageIsRefused(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("phoC"));
		helper.assertTrue(!handField(helper, c), "a stranger's farmer took a field reaching into a protected village: " + said(c));
		helper.assertTrue(said(c).stream().anyMatch(s -> s.contains("Owner's village")), "the stranger wasn't told whose village it is: " + said(c));
		helper.succeed();
	}

	/** The village's owner hands the same field to the same farmer: it's taken. */
	//$ gametest_ticks_batch AREA '60' '"protectedHandOverOwnersField"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "protectedHandOverOwnersField")
	public void theOwnersFieldReachingIntoHisVillageIsTaken(GameTestHelper helper) {
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("phoD"));
		ProtectionSpecGameTests.protectedHall(helper, c.player());
		helper.assertTrue(handField(helper, c), "the village's owner's field reaching into his own protected village was refused: " + said(c));
		helper.succeed();
	}

	/** With {@code villageProtection: false} on the server, QA's stranger's quarry through the village's edge is taken. */
	//$ gametest_ticks_batch AREA '60' '"protectedHandOverProtectionOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "protectedHandOverProtectionOff")
	public void aStrangersQuarryIsTakenWithProtectionOff(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		VillageProtection.ENABLED = false; // put back by protectedHall
		ServerLevel level = helper.getLevel();
		BlockPos benchAt = new BlockPos(28, 2, 26);
		helper.setBlock(benchAt, ModBlocks.MINERS_BENCH);
		Villager miner = helper.spawn(EntityType.VILLAGER, benchAt.west());
		miner.setNoAi(true);
		Jobs.employ(level, miner, helper.absolutePos(benchAt), ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(helper.absolutePos(new BlockPos(25, 1, 3))),
			Optional.of(helper.absolutePos(new BlockPos(29, 1, 19))), 4));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("phoE"));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(c.player(), miner, c.seen());
		QuarrySite site = Miners.activeSite(level, miner);
		if (site != null) {
			Miners.cancel(level, site);
		}
		helper.assertTrue(site != null, "with village protection off on the server, a stranger's quarry was refused: " + said(c));
		helper.succeed();
	}
}

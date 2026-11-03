package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.UpgradesPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane-b-1103-1238) failures against docs/wiki/builder.md section 2, "Who may order": "With
 * {@code workplaceBuilderOwnership} on, a Builder takes orders only from the player who hired it, their friends
 * (/workplace friend add|remove|list) and operators." Not registered: each fails until the mod is fixed.
 */
public class QaBuilderSpec1103bBugTests implements FabricGameTest {
	static final String BIG = "aliveworkplace_test:big_area";
	static final ResourceLocation HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	static final BlockPos U_HALL = new BlockPos(15, 2, 11);
	static final BlockPos U_HUT = new BlockPos(3, 2, 3);
	static final BlockPos U_BENCH = new BlockPos(13, 2, 3);
	static final BlockPos U_CHEST = new BlockPos(13, 2, 5);

	/**
	 * The village's hall is owned by one player; the only Builder in it was hired by another, who never named the hall's
	 * owner a friend. Ownership is on. The hall's owner clicks the Test Hut's row on the Upgrades page: the Builder is
	 * someone else's, so he must not take the order (no site of his, the hired Builder's work stays his employer's).
	 * Today the page hands the upgrade to the other player's Builder (UpgradesPage.builderFor never asks
	 * Friends.mayCommand).
	 */
	//$ gametest_ticks_batch BIG '100' '"qaSpec1103bUpgradeHired"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaSpec1103bUpgradeHired")
	public void anUpgradeOrderedAtTheHallIsNotHandedToAnotherPlayersHiredBuilder(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		boolean ownership = level.getGameRules().getBoolean(ModGameRules.BUILDER_OWNERSHIP);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(true, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		helper.setDayTime(2000);
		BuildSiteManager sites = BuildSiteManager.get(level);
		BlueprintData.Placement hut = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(U_HUT), Rotation.NONE, Mirror.NONE);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			level.getGameRules().getRule(ModGameRules.BUILDER_OWNERSHIP).set(ownership, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			for (BuildSite site : List.copyOf(sites.all())) {
				if (site.placement().origin().closerThan(helper.absolutePos(U_HUT), 40)) {
					sites.remove(site.id());
				}
			}
			Leftovers.finished(helper);
		});
		helper.setBlock(U_HALL, ModBlocks.VILLAGE_HALL);
		BlockPos origin = helper.absolutePos(U_HUT);
		level.getStructureManager().get(HUT).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		sites.recordFinished(HUT, hut, UUID.randomUUID());
		helper.setBlock(U_BENCH, ModBlocks.BUILDERS_BENCH);
		helper.setBlock(U_CHEST, Blocks.CHEST);
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 4));
		Builders.employ(level, builder, helper.absolutePos(U_BENCH));
		List<Component> seen = new ArrayList<>();
		ServerPlayer leader = WonderFinishedGameTests.listener(helper, seen, "QaSpecLeader");
		ServerPlayer hirer = WonderFinishedGameTests.listener(helper, new ArrayList<>(), "QaSpecHirer");
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(leader.hasPermissions(2) || hirer.hasPermissions(2), "setup: an operator");
			Friends.hire(hirer, builder);
			helper.assertTrue(ModAttachments.BUILDER_EMPLOYER.get(builder).id().equals(hirer.getUUID()), "setup: not hired");
			((VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(U_HALL))).setOwner(leader.getUUID(), "QaSpecLeader");
			helper.assertFalse(Friends.mayCommand(leader, builder), "setup: the hall's owner may command the other player's builder");
			ChoiceMenu menu = VillageHallScreen.forTest(leader, helper.absolutePos(U_HALL));
			menu.press(HallPages.slot(UpgradesPage.PAGE), leader);
			helper.assertTrue(menu.icon(4).is(Items.BRICKS), "not on the Upgrades page: " + menu.icon(4));
			helper.assertTrue(menu.icon(VillageHallScreen.FIRST_ROW).is(Items.BRICKS), "setup: the hut's row isn't free: " + menu.icon(VillageHallScreen.FIRST_ROW));
			menu.press(VillageHallScreen.FIRST_ROW, leader);
			BuildSite taken = Builders.activeSite(level, builder);
			helper.assertTrue(taken == null && Builders.queue(level, builder).isEmpty(),
				"the hall's owner gave another player's hired builder an order: he now builds " + (taken == null ? null : taken.structure())
					+ " for " + (taken == null ? null : taken.ownerName()) + "; the page said: " + seen.stream().map(Component::getString).toList());
			helper.succeed();
		});
	}
}

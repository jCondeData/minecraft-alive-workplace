package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardSafety;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.BitSet;
import java.util.Map;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=steward_safety (ROADMAP 27.19): a Village Hall whose Steward has two builds waiting for materials, one of them
 * with a player's block in the way. The desk shows one shopping list for both and says new builds wait; the build says
 * "a player's block is in the way"; the Storehouse's requests board shows the same shopping list.
 */
final class StewardSafetyScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private static final BlockPos STOREHOUSE = HALL.offset(-3, 0, 3);
	private int tick;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 60) {
			server.execute(() -> openDesk(server));
		}
		if (tick == 80) {
			ScreenshotHarness.pointAt(mc, StewardDeskPage.STEWARD);
		}
		if (tick == 100) {
			ScreenshotHarness.shot(mc, "01_shopping_list_on_the_desk");
		}
		if (tick == 110) {
			ScreenshotHarness.pointAt(mc, StewardDeskPage.FIRST_OPEN);
		}
		if (tick == 130) {
			ScreenshotHarness.shot(mc, "02_players_block_in_the_way");
			server.execute(() -> {
				ServerPlayer player = player(server);
				player.closeContainer();
				StorehouseBoard.open(player, STOREHOUSE);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					String lore = String.valueOf(menu.icon(StorehouseBoard.SHOPPING_SLOT).get(DataComponents.LORE));
					Showcase.check(lore.contains("50"), "the Storehouse board shows the shopping list (" + lore + ")");
				}
			});
		}
		if (tick == 150) {
			ScreenshotHarness.pointAt(mc, StorehouseBoard.SHOPPING_SLOT);
		}
		if (tick == 170) {
			ScreenshotHarness.shot(mc, "03_shopping_list_on_the_storehouse_board");
		}
		if (tick == 190) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static void openDesk(MinecraftServer server) {
		ServerPlayer player = player(server);
		VillageHallScreen.open(player, HALL);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(VillageHallScreen.ADVICE, player);
			String head = String.valueOf(menu.icon(StewardDeskPage.STEWARD).get(DataComponents.LORE));
			Showcase.check(head.contains("screen.aliveworkplace.desk.shopping"), "the desk shows the shopping list");
			String site = String.valueOf(menu.icon(StewardDeskPage.FIRST_OPEN).get(DataComponents.LORE))
				+ menu.icon(StewardDeskPage.FIRST_OPEN + 1).get(DataComponents.LORE);
			Showcase.check(site.contains("screen.aliveworkplace.desk.player_block"), "the build says a player's block is in the way");
		}
	}

	/** The player's hall with a Homes zone, a Builder, a Steward, a Storehouse and two of his builds waiting for materials. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2000);
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		level.setBlockAndUpdate(STOREHOUSE, ModBlocks.STOREHOUSE.defaultBlockState());
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 40; x++) {
			for (int z = -16; z <= 16; z++) {
				cells.set(CityPlan.cellAt(HALL, HALL.offset(x, 0, z)));
			}
		}
		hall.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		BlockPos table = HALL.offset(3, 0, -5);
		level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		Villager builder = EntityType.VILLAGER.spawn(level, table.south(), MobSpawnType.COMMAND);
		Builders.employ(level, builder, table);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, HALL);
		Villager steward = EntityType.VILLAGER.spawn(level, HALL.south(2), MobSpawnType.COMMAND);
		steward.setVillagerData(steward.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER)
			.setLevel(Stewards.MIN_BUILDER_LEVEL));
		steward.setVillagerXp(70);
		Stewards.appoint(player, steward, plan);
		ModAttachments.STEWARD_ROUND_DAY.set(steward, StewardWishes.day(level));
		int n = 0;
		for (Map<Item, Integer> missing : java.util.List.of(Map.of(Items.GLASS, 40, Items.OAK_PLANKS, 12, Items.STONE_BRICKS, 20),
			Map.of(Items.GLASS, 10, Items.OAK_LOG, 6, Items.LANTERN, 2))) {
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), HALL.offset(12 + 10 * n, 0, 4),
				Rotation.NONE, Mirror.NONE);
			BuildSite site = BuildSiteManager.get(level).create(player.getUUID(), player.getGameProfile().getName(),
				AliveWorkplace.id(n == 0 ? "stone_house" : "well"), placement);
			site.setStewardHall(HALL);
			site.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
			site.setMissing(new java.util.HashMap<>(missing));
			site.setWaitingSince(level.getGameTime() - StewardSafety.PAUSE_TICKS - 1);
			if (n == 0) {
				site.playerBlockInTheWay();
			}
			n++;
		}
		ScreenshotHarness.hover(player, Vec3.atCenterOf(HALL).add(-3, 2, 4), 200, 20);
	}
}

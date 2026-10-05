package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.OldHouses;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=old_houses (ROADMAP 27.20): a Village Hall whose Homes zone has "renew old houses" on, with three vanilla plains
 * houses placed from their templates as a village places them; one of them keeps a chest. The desk says "Old houses: 3,
 * 2 can be renewed" and lists them; Show me outlines each in the world, orange for the two that can be renewed.
 */
final class OldHousesScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private static final List<House> HOUSES = List.of(new House("plains_small_house_1", HALL.offset(10, 0, -9)),
		new House("plains_medium_house_1", HALL.offset(10, 0, 3)), new House("plains_small_house_7", HALL.offset(24, 0, -9)));
	private int tick;

	private record House(String template, BlockPos at) {
	}

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
		if (tick == 100) {
			server.execute(() -> openDesk(server));
		}
		if (tick == 120) {
			ScreenshotHarness.pointAt(mc, StewardDeskPage.OLD_HOUSES);
		}
		if (tick == 140) {
			ScreenshotHarness.shot(mc, "01_old_houses_on_the_desk");
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(StewardDeskPage.OLD_HOUSES, player); // closes the screen: the outlines glow
				}
				Showcase.check(BlueprintOutline.glowingBoxes(player) == 3, "Show me outlined the 3 old houses ("
					+ BlueprintOutline.glowingBoxes(player) + ")");
				Vec3 at = Vec3.atCenterOf(HALL.offset(18, 3, 0));
				ScreenshotHarness.hoverLookingAt(player, at.add(-14, 16, 4), at);
			});
		}
		if (tick == 200) {
			ScreenshotHarness.shot(mc, "02_show_me_outlines");
		}
		if (tick == 220) {
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
			var name = menu.icon(StewardDeskPage.OLD_HOUSES).getHoverName();
			boolean counted = name.getContents() instanceof TranslatableContents t && t.getKey().equals("screen.aliveworkplace.desk.old_houses_count")
				&& java.util.Arrays.toString(t.getArgs()).equals("[3, 2]");
			Showcase.check(counted, "the desk says Old houses: 3, 2 can be renewed (" + name.getString() + ")");
		}
	}

	/** The player's hall with a Homes zone that renews old houses, a Steward, and three vanilla plains houses. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2000);
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 40; x++) {
			for (int z = -16; z <= 16; z++) {
				cells.set(CityPlan.cellAt(HALL, HALL.offset(x, 0, z)));
			}
		}
		hall.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells).editZone(0, "homes", "Homes 1", "", true));
		for (House house : HOUSES) {
			StructurePlaceSettings settings = new StructurePlaceSettings();
			settings.addProcessor(BlockIgnoreProcessor.STRUCTURE_AND_AIR);
			settings.addProcessor(JigsawReplacementProcessor.INSTANCE);
			level.getStructureManager().get(ResourceLocation.withDefaultNamespace("village/plains/houses/" + house.template())).orElseThrow()
				.placeInWorld(level, house.at(), house.at(), settings, net.minecraft.util.RandomSource.create(27_20L), 2);
		}
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, HALL);
		Villager steward = EntityType.VILLAGER.spawn(level, HALL.south(2), MobSpawnType.COMMAND);
		steward.setVillagerData(steward.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER)
			.setLevel(Stewards.MIN_BUILDER_LEVEL));
		steward.setVillagerXp(70);
		Stewards.appoint(player, steward, plan);
		ModAttachments.STEWARD_ROUND_DAY.set(steward, StewardWishes.day(level));
		OldHouses.request(level, HALL);
		ScreenshotHarness.hover(player, Vec3.atCenterOf(HALL).add(-3, 2, 4), 200, 20);
	}
}

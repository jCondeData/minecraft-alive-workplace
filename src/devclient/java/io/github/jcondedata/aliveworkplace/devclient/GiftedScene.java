package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.legend.Gifted;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=gifted (ROADMAP 29.6): Wren, a Night Owl builder, building a market stall by moonlight at midnight (four stills
 * for the GIF), then the hall's list with her gift in gold under her traits.
 */
final class GiftedScene {
	private static final BlockPos ANCHOR = new BlockPos(-8, -60, -3);
	private static final BlockPos HALL = new BlockPos(4, -60, 8);
	private static final net.minecraft.resources.ResourceLocation NIGHT_OWL = io.github.jcondedata.aliveworkplace.AliveWorkplace.id("night_owl");
	private int tick;
	private volatile UUID site;
	private volatile UUID wren;
	private volatile float progress;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(18000); // midnight
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		for (int i = 0; i < 4; i++) {
			if (tick == 200 + i * 150) {
				if (i == 3) {
					Showcase.check(progress > 0, "Wren placed blocks at midnight (" + Math.round(progress * 100) + "% built)");
				}
				ScreenshotHarness.shot(mc, "0" + (i + 1) + "_night_owl_building");
			}
			if (tick == 190 + i * 150) {
				server.execute(() -> {
					ServerLevel level = server.overworld();
					BuildSite s = site == null ? null : BuildSiteManager.get(level).get(site);
					progress = s == null ? 1f : s.progress(s.plan(level));
				});
			}
		}
		if (tick == 700) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Showcase.check(level.getEntity(wren) instanceof Villager v && Gifted.of(v) != null && Gifted.of(v).id().equals(NIGHT_OWL)
					&& v.getBrain().getSchedule() == ModVillagers.NIGHT_OWL_SCHEDULE, "Wren is a Night Owl, on the night schedule");
				VillageHallScreen.open(server.getPlayerList().getPlayers().get(0), HALL);
			});
		}
		if (tick == 715) {
			ScreenshotHarness.pointAt(mc, VillageHallScreen.FIRST_PERSON);
		}
		if (tick == 730) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.containerMenu instanceof ChoiceMenu m) {
					var lore = m.icon(VillageHallScreen.FIRST_PERSON).get(DataComponents.LORE);
					boolean gold = lore != null && lore.lines().stream().anyMatch(l -> l.getString().startsWith("Gifted: Night Owl")
						&& l.getStyle().getColor() != null && l.getStyle().getColor().getValue() == 0xFFAA00);
					Showcase.check(gold, "the hall's list shows her gift in gold");
				}
			});
			ScreenshotHarness.shot(mc, "05_gifted_list");
		}
		if (tick == 750) {
			mc.stop();
		}
	}

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(4, server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		site = JobScenes.builderSite(level, StarterBlueprints.MARKET_STALL, ANCHOR, List.of(), Map.of());
		BuildSite s = site == null ? null : BuildSiteManager.get(level).get(site);
		if (s != null && s.builder() != null && level.getEntity(s.builder()) instanceof Villager v) {
			wren = v.getUUID();
			v.setCustomName(Component.literal("Wren"));
			Gifted.set(v, NIGHT_OWL);
		}
		Showcase.check(wren != null, "Wren the builder was staged");
		ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), Vec3.atBottomCenterOf(ANCHOR).add(10, 6, 14),
			Vec3.atBottomCenterOf(ANCHOR).add(2, 1, 2));
	}
}

package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupDays;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.CupThemes;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * SCENE=cup_themes (ROADMAP 28.22): the eight Cup themes in order. For each, a Town host's Cup page shows that theme's
 * card with its rules, then a fair trader opens their trades: the theme's wares, for emeralds.
 */
final class CupThemesScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 3);
	static final List<String> THEMES = List.of("blossom_cup", "little_cup", "sun_cup", "workers_cup", "harvest_cup", "lantern_cup", "frost_cup", "grand_cup");
	private static final int START = 90;
	private static final int EACH = 60;
	private int tick;
	private WanderingTrader trader;

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
				level.setDayTime(3000);
			});
		}
		if (tick == 60) {
			server.execute(() -> stage(server));
		}
		if (tick < START) {
			return;
		}
		int i = (tick - START) / EACH;
		int t = (tick - START) % EACH;
		if (i >= THEMES.size()) {
			if (t == 5) {
				mc.stop();
			}
			return;
		}
		String name = THEMES.get(i);
		String prefix = String.format("%02d_%s", i + 1, name);
		CupThemes.Theme theme = CupThemes.get(AliveWorkplace.id(name));
		if (t == 0) {
			Showcase.check(theme != null, name + " is loaded");
			if (theme == null) {
				return;
			}
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.closeContainer();
				CupData.Cup cup = CupData.get(server.overworld()).cup(HALL);
				cup.theme = theme.id();
				cup.closed = false;
				VillageHallScreen.open(player, HALL);
				if (player.containerMenu instanceof ChoiceMenu m) {
					m.press(HallPages.slot(CupPage.PAGE), player);
					String card = m.icon(CupPage.CARD).getHoverName().getString();
					Showcase.check(card.equals(Component.translatable(theme.name()).getString()), "the Cup page shows the " + name + " card (" + card + ")");
				}
			});
		}
		if (t == 12) {
			ScreenshotHarness.pointAt(mc, CupPage.CARD);
		}
		if (t == 25) {
			ScreenshotHarness.shot(mc, prefix + "_page");
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				player.closeContainer();
				ServerLevel level = server.overworld();
				if (trader != null) {
					trader.discard();
				}
				trader = EntityType.WANDERING_TRADER.spawn(level, HALL.offset(0, 0, 3), MobSpawnType.COMMAND);
				trader.setNoAi(true);
				trader.getOffers().clear();
				trader.getOffers().addAll(CupDays.offers(theme));
				Showcase.check(trader.getOffers().size() == theme.wares().size(), "the " + name + " fair sells " + trader.getOffers().size() + " of its "
					+ theme.wares().size() + " wares");
				trader.setTradingPlayer(player);
				trader.openTradingScreen(player, Component.translatable("screen.aliveworkplace.cup.fair", Component.translatable(theme.name())), 1);
			});
		}
		if (t == 45) {
			ScreenshotHarness.shot(mc, prefix + "_fair");
		}
	}

	/** The host: a Town with an Arena II on record, its Leader Mira, and one trade partner with a Leader on record. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setRank(VillageRanks.Rank.TOWN);
		hall.setCustomName(Component.literal("Thornholm"));
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.ARENA_2.id(),
			new BlueprintData.Placement(level.dimension().location(), HALL.offset(8, 0, 4), Rotation.NONE, Mirror.NONE), UUID.randomUUID());
		Cups.COBBLEMON = true;
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(HALL, Component.literal("Thornholm"), List.of());
		BlockPos ashford = new BlockPos(600, -60, -200);
		data.setWants(ashford, Component.literal("Ashford"), List.of());
		data.toggleRoute(HALL, ashford, 5);
		data.setLeader(ashford, new Caravans.Leader(UUID.nameUUIDFromBytes("oren".getBytes()), "Oren", 5, 420, true));
		Villager mira = EntityType.VILLAGER.spawn(level, HALL.offset(-2, 0, 3), MobSpawnType.COMMAND);
		mira.setVillagerData(mira.getVillagerData().setProfession(ModVillagers.TRAINER_LEADER).setLevel(4));
		mira.setCustomName(Component.literal("Mira"));
		mira.setNoAi(true);
		Cups.round(level, HALL, hall);
		CupData.Cup cup = CupData.get(level).cup(HALL);
		Showcase.check(AliveWorkplace.id("blossom_cup").equals(cup.theme), "a host's first Cup is the Blossom Cup (" + cup.theme + ")");
		CupData.get(level).setDirty();
	}
}

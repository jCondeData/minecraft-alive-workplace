package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.cup.CupChampions;
import io.github.jcondedata.aliveworkplace.cup.CupData;
import io.github.jcondedata.aliveworkplace.cup.CupPage;
import io.github.jcondedata.aliveworkplace.cup.Cups;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=cup_champions (ROADMAP 28.21): Thornholm (a Village with Arena III) won its Grand Cup: the Cup banner, a gold cup
 * on red, flies on the Arena's champion's pole. Then Ashford, on its circuit, wins the next one: the pole's banner comes
 * down and the banner stands on top of Ashford's Village Hall. The host's Cup page shows the roll of champions, and
 * Ashford's hall names it "Holders of the Thornholm Cup".
 */
final class CupChampionsScene {
	private static final BlockPos ORIGIN = new BlockPos(0, -60, 0);
	private static final BlockPos HALL = new BlockPos(12, -60, -14);
	private static final BlockPos ASHFORD = new BlockPos(-20, -60, -14);
	private static final ResourceLocation GRAND = AliveWorkplace.id("grand_cup");
	private int tick;
	private volatile boolean roll;
	private volatile boolean named;

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
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (tick == 60) {
			// the champion's pole before the Arena's gate, from the street
			BlockPos pole = pole();
			server.execute(() -> ScreenshotHarness.hoverLookingAt(player(server), Vec3.atCenterOf(pole).add(-5, -1, -8), Vec3.atCenterOf(pole).add(0, -1, 0)));
		}
		if (tick == 100) {
			ScreenshotHarness.shot(mc, "01_pole_banner");
			server.execute(() -> pass(server));
		}
		if (tick == 120) {
			server.execute(() -> ScreenshotHarness.hoverLookingAt(player(server), Vec3.atCenterOf(ASHFORD).add(-3, 2, -5), Vec3.atCenterOf(ASHFORD).add(0, 1, 0)));
		}
		if (tick == 155) {
			ScreenshotHarness.shot(mc, "02_hall_banner");
			server.execute(() -> {
				VillageHallScreen.open(player(server), HALL);
				if (player(server).containerMenu instanceof ChoiceMenu m) {
					m.press(HallPages.slot(CupPage.PAGE), player(server));
					roll = m.icon(CupPage.CHAMPIONS).getHoverName().getString().equals("Roll of champions");
				}
			});
		}
		if (tick == 165) {
			Showcase.check(roll, "the host's Cup page shows the roll of champions");
			ScreenshotHarness.pointAt(mc, CupPage.CHAMPIONS);
		}
		if (tick == 180) {
			ScreenshotHarness.shot(mc, "03_roll_of_champions");
			server.execute(() -> {
				player(server).closeContainer();
				VillageHallScreen.open(player(server), ASHFORD);
				if (player(server).containerMenu instanceof ChoiceMenu m) {
					named = String.valueOf(m.icon(VillageHallScreen.NAME).get(net.minecraft.core.component.DataComponents.LORE)).contains("Holders of the Thornholm Cup");
				}
			});
		}
		if (tick == 190) {
			Showcase.check(named, "Ashford's hall names it holder of the Thornholm Cup");
			ScreenshotHarness.pointAt(mc, VillageHallScreen.NAME);
		}
		if (tick == 205) {
			ScreenshotHarness.shot(mc, "04_holders_tooltip");
		}
		if (tick == 220) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static BlueprintData.Placement placement(ServerLevel level) {
		return new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE);
	}

	private static BlockPos pole() {
		return ORIGIN.offset(io.github.jcondedata.aliveworkplace.hall.Arenas.CHAMPION_POLE);
	}

	/** Arena III built and on record, Thornholm's hall (a Village), Ashford on its trade routes, and Thornholm's Grand Cup won. */
	private static void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ResourceLocation arena3 = StarterBlueprints.ARENAS.get(2).id();
		level.getStructureManager().get(arena3).orElseThrow().placeInWorld(level, ORIGIN, ORIGIN, new StructurePlaceSettings(), level.getRandom(), 2);
		BuildSiteManager.get(level).recordFinished(arena3, placement(level), UUID.randomUUID());
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.NORTH));
		level.setBlockAndUpdate(ASHFORD, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.NORTH));
		VillageHallBlockEntity host = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		host.setRank(VillageRanks.Rank.VILLAGE);
		host.setCustomName(Component.literal("Thornholm"));
		((VillageHallBlockEntity) level.getBlockEntity(ASHFORD)).setCustomName(Component.literal("Ashford"));
		Cups.COBBLEMON = true; // the scene runs with Cobblemon; this only makes sure a run without it still shows the Cup
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(HALL, Component.literal("Thornholm"), List.of());
		data.setWants(ASHFORD, Component.literal("Ashford"), List.of());
		data.toggleRoute(HALL, ASHFORD, 5);
		CupData.Cup cup = CupData.get(level).cup(HALL);
		cup.champions.add(new CupData.Champion(9, GRAND, "Mira", HALL, UUID.nameUUIDFromBytes("mira".getBytes())));
		CupData.get(level).setDirty();
		CupChampions.fly(level, HALL);
		BlockPos pole = pole();
		Showcase.check(CupChampions.isCupBanner(level, pole.north()) || CupChampions.isCupBanner(level, pole.south()),
			"the Cup banner flies on Arena III's champion's pole");
	}

	/** Ashford wins the next Cup: the pole's banner comes down, Ashford's hall gets one. */
	private static void pass(MinecraftServer server) {
		ServerLevel level = server.overworld();
		CupData.Cup cup = CupData.get(level).cup(HALL);
		cup.champions.add(new CupData.Champion(17, GRAND, "Oren", ASHFORD, UUID.nameUUIDFromBytes("oren".getBytes())));
		CupData.get(level).setDirty();
		CupChampions.fly(level, HALL);
		CupChampions.fly(level, ASHFORD);
		BlockPos pole = pole();
		Showcase.check(!CupChampions.isCupBanner(level, pole.north()) && !CupChampions.isCupBanner(level, pole.south()),
			"the pole's banner came down when the title passed");
		Showcase.check(CupChampions.isCupBanner(level, ASHFORD.above()), "the Cup banner stands on top of Ashford's Village Hall");
	}
}

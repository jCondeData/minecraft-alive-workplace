package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.WorkHorn;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=work_horn (ROADMAP 30.11): two builders at work on market stalls for 20 seconds, then the hall's owner blows the
 * Work Horn and they rush, with sparks over them, for 20 seconds more; then the Book of Edicts' last row shows the horn
 * used. Its check: more blocks placed a minute during the rush than before it.
 */
final class WorkHornScene {
	private static final BlockPos HALL = new BlockPos(4, -60, 10);
	private static final BlockPos[] ANCHORS = {new BlockPos(-12, -60, -4), new BlockPos(0, -60, -4)};
	/** The two windows the builders are timed in, in client ticks: before the horn and during the rush. */
	private static final int BEFORE_FROM = 300;
	private static final int HORN = 700;
	private static final int DURING_FROM = 720;
	private static final int WINDOW = 400;
	private int tick;
	private final List<UUID> sites = new ArrayList<>();
	private volatile int beforeStart;
	private volatile int beforeEnd;
	private volatile int duringStart;
	private volatile int duringEnd;

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
				level.setDayTime(1000); // morning: the builders at work
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (tick == BEFORE_FROM) {
			server.execute(() -> beforeStart = placed(server));
		}
		if (tick == 500) {
			ScreenshotHarness.shot(mc, "01_work_horn_before");
		}
		if (tick == BEFORE_FROM + WINDOW) {
			server.execute(() -> beforeEnd = placed(server));
		}
		if (tick == HORN) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				ItemStack horn = new ItemStack(ModItems.WORK_HORN);
				player.setItemInHand(InteractionHand.MAIN_HAND, horn);
				ModItems.WORK_HORN.finishUsingItem(horn, level, player); // held up, and it sounds
				VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
				Showcase.check(WorkHorn.rushing(level, hall), "the horn called a rush");
			});
		}
		if (tick == HORN + 10) {
			ScreenshotHarness.shot(mc, "02_work_horn_blown");
		}
		if (tick == DURING_FROM) {
			server.execute(() -> duringStart = placed(server));
		}
		if (tick == 960) {
			ScreenshotHarness.shot(mc, "03_work_horn_rush");
		}
		if (tick == DURING_FROM + WINDOW) {
			server.execute(() -> {
				duringEnd = placed(server);
				int before = beforeEnd - beforeStart;
				int during = duringEnd - duringStart;
				float perMinute = 1200f / WINDOW;
				Showcase.check(during > before, "more blocks placed a minute during the rush: " + Math.round(during * perMinute)
					+ " a minute, against " + Math.round(before * perMinute) + " before");
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				EdictBook.open(player, HALL);
			});
		}
		if (tick == DURING_FROM + WINDOW + 15) {
			ScreenshotHarness.pointAt(mc, EdictBook.HORN, 6);
		}
		if (tick == DURING_FROM + WINDOW + 30) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Showcase.check(player.containerMenu instanceof ChoiceMenu m && m.icon(EdictBook.HORN).is(ModItems.WORK_HORN),
					"the Book's last row shows the horn");
			});
			ScreenshotHarness.shot(mc, "04_work_horn_book");
		}
		if (tick == DURING_FROM + WINDOW + 50) {
			mc.stop();
		}
	}

	private int placed(MinecraftServer server) {
		ServerLevel level = server.overworld();
		int n = 0;
		for (UUID id : sites) {
			BuildSite s = BuildSiteManager.get(level).get(id);
			n += s == null ? 0 : s.placed();
		}
		return n;
	}

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(20, server); // the pace shows in the time between blocks
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		for (BlockPos anchor : ANCHORS) {
			UUID site = JobScenes.builderSite(level, StarterBlueprints.MARKET_STALL, anchor, List.of(), Map.of());
			if (site != null) {
				sites.add(site);
			}
		}
		Showcase.check(sites.size() == 2, "two builders were staged");
		ScreenshotHarness.hoverLookingAt(player, new Vec3(-2, -52, 14), new Vec3(-2, -59, -1));
	}
}

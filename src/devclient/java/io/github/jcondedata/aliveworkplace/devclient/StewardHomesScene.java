package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.BitSet;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=steward_homes (ROADMAP 27.10): a Village with no beds, a Homes zone east of its hall, three Builders at their
 * tables and a Steward set to Run the village. Each morning (three of them) he reads the shipped homes rules, starts the
 * builds himself, and the builders put them up in the zone; between mornings more villagers move in. The camera hangs
 * over the zone, so the GIF shows it filling up day by day.
 */
final class StewardHomesScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private static final int DAYS = 3;
	/** Client ticks a day may take at most: the builders usually finish sooner. */
	private static final int DAY_TICKS = 1500;
	private int tick;
	private int day;
	private int dayStart;
	private volatile boolean started;
	private volatile boolean built;
	private volatile Villager steward;

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
		if (tick < 40 || day > DAYS) {
			return;
		}
		if (day == 0 || built || tick - dayStart >= DAY_TICKS) {
			if (day > 0) {
				ScreenshotHarness.shot(mc, String.format("%02d_steward_homes_day_%d", day, day));
				int done = day;
				server.execute(() -> Showcase.check(started, "day " + done + ": the Steward started a home build himself"));
			}
			day++;
			dayStart = tick;
			started = false;
			built = false;
			if (day > DAYS) {
				server.execute(() -> {
					ServerLevel level = server.overworld();
					long homes = BuildSiteManager.get(level).finishedNear(level, HALL, 48).size();
					Showcase.check(homes >= 2, "the Homes zone filled over three days (" + homes + " finished buildings)");
				});
				mc.stop();
				return;
			}
			int morning = day;
			server.execute(() -> morning(server, morning));
			return;
		}
		if (tick % 10 == 0) {
			server.execute(() -> round(server));
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** A new morning: more villagers have moved in, and the Steward's round starts again. */
	private void morning(MinecraftServer server, int n) {
		ServerLevel level = server.overworld();
		level.setDayTime(n * 24000L + 1000);
		if (n > 1) {
			for (int i = 0; i < 2; i++) {
				EntityType.VILLAGER.spawn(level, HALL.offset(-4 + 2 * i, 0, 4 + n), MobSpawnType.COMMAND);
			}
		}
	}

	/**
	 * His morning at the hall, as {@code StewardWork}'s planner does it: today's wishes from the rules, a plot for each
	 * build in its zone, then the desk, which in Run the village approves the builds itself once the searches are done.
	 */
	private void round(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (!started) {
			StewardWishes.rankIfDue(level, HALL);
			for (StewardWishes.Wish wish : StewardWishes.of(level, HALL).wishes()) {
				StewardWishes.plotFor(wish).ifPresent(request -> Plots.request(level, HALL, request));
			}
			StewardDesk.plan(level, steward, HALL);
			started = !StewardDesk.openSites(level, HALL).isEmpty();
			return;
		}
		built = StewardDesk.openSites(level, HALL).isEmpty();
	}

	/** The hall (the player's, a Village) with a Homes zone east of it, three Builders, a Steward in Run the village, no beds. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		GameRules rules = level.getGameRules();
		rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		rules.getRule(ModGameRules.FREE_MATERIALS).set(true, server);
		rules.getRule(ModGameRules.BUILD_DELAY).set(1, server);
		rules.getRule(ModGameRules.BUILDERS_HELP).set(true, server);
		level.setDayTime(1000);
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		hall.setRank(VillageRanks.Rank.VILLAGE);
		BitSet cells = new BitSet();
		for (int x = 6; x <= 40; x++) {
			for (int z = -16; z <= 16; z++) {
				cells.set(CityPlan.cellAt(HALL, HALL.offset(x, 0, z)));
			}
		}
		hall.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells).withMode(CityPlan.Mode.RUN));
		for (int i = 0; i < 3; i++) {
			BlockPos table = HALL.offset(-6 + 3 * i, 0, -6);
			level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
			Villager builder = EntityType.VILLAGER.spawn(level, table.south(), MobSpawnType.COMMAND);
			Builders.employ(level, builder, table);
		}
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, HALL);
		steward = EntityType.VILLAGER.spawn(level, HALL.south(2), MobSpawnType.COMMAND);
		steward.setVillagerData(steward.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(Stewards.MIN_BUILDER_LEVEL));
		steward.setVillagerXp(70); // a seasoned Builder (27.1a)
		Stewards.appoint(player, steward, plan);
		Showcase.check(StewardDesk.mode(level, HALL) == CityPlan.Mode.RUN, "the Steward runs the village");
		ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(HALL).add(4, 22, 26), Vec3.atCenterOf(HALL.offset(22, 0, 0)));
	}
}

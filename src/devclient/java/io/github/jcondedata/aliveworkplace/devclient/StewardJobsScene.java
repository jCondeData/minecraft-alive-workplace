package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=steward_jobs (ROADMAP 27.9): a Village Hall with its Steward, a Builder at his table and three jobless villagers
 * by the hall; a composter, a grindstone and a fletching table a little way off. The Steward's morning puts "Give 3
 * villagers jobs" on his desk; approving it makes them a farmer (food is short), a guard (guards are short) and a
 * fletcher (the nearest block left), and they walk to their new workstations.
 */
final class StewardJobsScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private int tick;
	private final List<Villager> jobless = new ArrayList<>();
	private Villager steward;

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
		if (tick == 40) {
			server.execute(() -> {
				StewardDesk.plan(server.overworld(), steward, HALL); // his morning at the hall
				List<StewardDesk.Proposal> proposals = StewardDesk.of(server.overworld(), HALL).proposals();
				Showcase.check(proposals.size() == 1 && proposals.get(0).isJobs() && proposals.get(0).jobs().size() == 3,
					"the morning's jobs are one proposal for three villagers (" + proposals.size() + " proposals)");
			});
		}
		if (tick == 60) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				VillageHallScreen.open(player, HALL);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(VillageHallScreen.ADVICE, player);
				}
			});
		}
		if (tick == 80) {
			ScreenshotHarness.pointAt(mc, StewardDeskPage.FIRST_PROPOSAL);
		}
		if (tick == 100) {
			ScreenshotHarness.shot(mc, "01_steward_jobs_desk");
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(StewardDeskPage.FIRST_PROPOSAL, player);
				}
			});
		}
		if (tick == 115) {
			ScreenshotHarness.pointAt(mc, 4);
		}
		if (tick == 130) {
			ScreenshotHarness.shot(mc, "02_steward_jobs_proposal");
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(StewardDeskPage.APPROVE, player);
				}
				player.closeContainer();
				List<VillagerProfession> jobs = jobless.stream().map(v -> v.getVillagerData().getProfession()).toList();
				Showcase.check(jobs.equals(List.of(VillagerProfession.FARMER, io.github.jcondedata.aliveworkplace.registry.ModVillagers.GUARD,
					VillagerProfession.FLETCHER)), "approving gave a farmer, a guard and a fletcher: " + jobs);
				ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(HALL).add(-9, 9, 12), Vec3.atCenterOf(HALL.offset(0, 0, 4)));
			});
		}
		if (tick == 230) {
			ScreenshotHarness.shot(mc, "03_jobs_walking");
		}
		if (tick == 520) {
			ScreenshotHarness.shot(mc, "04_jobs_at_work");
			server.execute(() -> {
				long there = jobless.stream().filter(v -> v.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE)
					.map(g -> g.pos().distToCenterSqr(v.position()) <= 4 * 4).orElse(false)).count();
				Showcase.check(there == 3, "all three walked to their new workstations (" + there + " there)");
			});
		}
		if (tick == 540) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** The hall (the player's) with a Steward, a Builder at his table, three jobless villagers and three free blocks. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2500); // villagers' working hours
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		BlockPos table = HALL.offset(-6, 0, -6);
		level.setBlockAndUpdate(table, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		Villager builder = EntityType.VILLAGER.spawn(level, table.south(), MobSpawnType.COMMAND);
		Builders.employ(level, builder, table);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, HALL);
		steward = EntityType.VILLAGER.spawn(level, HALL.south(2), MobSpawnType.COMMAND);
		Stewards.appoint(player, steward, plan);
		ModAttachments.STEWARD_ROUND_DAY.set(steward, StewardWishes.day(level));
		level.setBlockAndUpdate(HALL.offset(-7, 0, 9), Blocks.COMPOSTER.defaultBlockState());
		level.setBlockAndUpdate(HALL.offset(8, 0, 10), Blocks.GRINDSTONE.defaultBlockState());
		level.setBlockAndUpdate(HALL.offset(1, 0, 12), Blocks.FLETCHING_TABLE.defaultBlockState());
		level.setBlockAndUpdate(HALL.offset(-6, 0, 10), Blocks.HAY_BLOCK.defaultBlockState()); // a little farm look by the composter
		level.setBlockAndUpdate(HALL.offset(8, 0, 11), Blocks.TARGET.defaultBlockState());
		for (int i = 0; i < 3; i++) {
			Villager v = EntityType.VILLAGER.spawn(level, HALL.offset(-2 + 2 * i, 0, 3 + i), MobSpawnType.COMMAND);
			jobless.add(v);
		}
		hall.setStewardWishes(new StewardWishes.State(StewardWishes.day(level), List.of(new StewardWishes.Wish(AliveWorkplace.id("jobs"),
			new StewardRules.Effect(StewardRules.Kind.ASSIGN_JOBS, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
			75, "steward.aliveworkplace.why.jobless", List.of(3L))), Map.of()));
		ScreenshotHarness.hover(player, Vec3.atCenterOf(HALL).add(-3, 2, 4), 200, 20);
	}
}

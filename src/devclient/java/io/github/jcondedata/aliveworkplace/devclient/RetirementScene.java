package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * SCENE=retirement (ROADMAP 34.20): Bram, an elder Expert farmer at his composter, his jobless son Tom and Ida, a Novice
 * farmer, by a Village Hall. The hall's round picks Tom: "learning from Bram" over his head (still 1). Two days on, the
 * hand-over: Tom has the composter and the job as a Journeyman, Bram is retired, the chronicle says both; Bram's card on
 * the hall's list (still 2). Then Bram mentors Ida: "taught by Bram" over her head (still 3). Each step is checked from
 * the game.
 */
final class RetirementScene {
	private static final BlockPos HALL = new BlockPos(0, -60, -3);
	private static final BlockPos COMPOSTER = new BlockPos(-3, -60, -1);
	private Villager bram;
	private Villager tom;
	private Villager ida;
	private int tick;
	private int bramSlot = -1;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
			mc.options.hideGui = false;
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(1000);
				Chatter.ENABLED = false;
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (tick > 40 && bram != null) {
			server.execute(() -> {
				for (Villager v : List.of(bram, tom, ida)) {
					Retirement.tick(v); // their own tick (they stand still for the pictures)
				}
			});
		}
		if (tick == 60) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Retirement.round(level, HALL, all());
				Retirement.Shadow shadow = Retirement.shadow(tom);
				Showcase.check(shadow != null && shadow.elder().equals(bram.getUUID()) && Retirement.shadow(ida) == null,
					"the hall's round picked Tom, Bram's jobless son, to learn from him");
			});
		}
		if (tick == 100) {
			ScreenshotHarness.shot(mc, "01_retirement_learning");
		}
		if (tick == 120) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.setDayTime(level.getDayTime() + 2 * VillageNeeds.DAY);
				Retirement.round(level, HALL, all());
				Showcase.check(Retirement.isRetired(bram) && bram.getVillagerData().getProfession() == VillagerProfession.NONE && !bram.getOffers().isEmpty(),
					"two days on Bram is retired, and keeps his trades for players");
				Showcase.check(tom.getVillagerData().getProfession() == VillagerProfession.FARMER && tom.getVillagerData().getLevel() == 3
					&& tom.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(g -> g.pos().equals(COMPOSTER)).isPresent(),
					"Tom took Bram's composter and the job one level below his: " + tom.getVillagerData());
				VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
				List<String> chronicle = hall.chronicle().stream().map(e -> e.text().getString()).toList();
				Showcase.check(chronicle.stream().anyMatch(l -> l.startsWith("Bram retired from the Farmer trade"))
					&& chronicle.stream().anyMatch(l -> l.startsWith("Tom took over the Farmer trade from Bram")), "the chronicle has the retirement and the hand-over");
				Showcase.check(Retirement.teacher(ida) != null, "Bram mentors Ida, a Novice farmer");
				VillageHallScreen.open(player(server), HALL);
			});
		}
		if (tick == 140) {
			server.execute(() -> {
				List<String> card = findBram(server);
				Showcase.check(card.contains("Retired Farmer · mentors the young"), "Bram's card says he is retired (" + String.join(" | ", card) + ")");
			});
		}
		if (tick == 155) {
			ScreenshotHarness.pointAt(mc, bramSlot);
		}
		if (tick == 170) {
			ScreenshotHarness.shot(mc, "02_retirement_card");
		}
		if (tick == 180) {
			server.execute(() -> player(server).closeContainer());
		}
		if (tick == 220) {
			server.execute(() -> Showcase.check(Retirement.extraXp(ida, 10, server.overworld().random) == 5, "taught by Bram, Ida learns 50% faster"));
			ScreenshotHarness.shot(mc, "03_retirement_taught");
		}
		if (tick == 250) {
			mc.stop();
		}
	}

	private List<Villager> all() {
		return List.of(bram, tom, ida);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		level.setBlockAndUpdate(COMPOSTER, Blocks.COMPOSTER.defaultBlockState());
		long today = Chronicle.day(level);
		bram = villager(level, "Bram", -1.5, VillagerProfession.NONE);
		Jobs.employ(level, bram, COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
		bram.setVillagerData(bram.getVillagerData().setLevel(4));
		bram.setVillagerXp(160);
		LifeStages.setAdultSince(bram, today - 130);
		tom = villager(level, "Tom", 0.5, VillagerProfession.NONE);
		LifeStages.setAdultSince(tom, today - 20);
		io.github.jcondedata.aliveworkplace.registry.ModAttachments.PARENTS.set(tom,
			new Families.Parents(Component.literal("Ann"), Component.literal("Bram"), "", "minecraft:farmer", true));
		ida = villager(level, "Ida", 2.5, VillagerProfession.FARMER);
		LifeStages.setAdultSince(ida, today - 25);
		player.getInventory().clearContent();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
		player.teleportTo(level, 0.5, -60, 6.5, 180f, 8f);
		Showcase.check(LifeStages.isElder(bram, today) && bram.getVillagerData().getProfession() == VillagerProfession.FARMER, "Bram was staged an elder farmer");
	}

	private static Villager villager(ServerLevel level, String name, double x, VillagerProfession job) {
		Villager v = EntityType.VILLAGER.create(level);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(1));
		if (job != VillagerProfession.NONE) {
			v.setVillagerXp(1);
		}
		v.setCustomName(Component.literal(name));
		v.setNoAi(true);
		v.moveTo(x, -60, 0.5, 0f, 0f);
		v.setYHeadRot(0f);
		v.setYBodyRot(0f);
		level.addFreshEntity(v);
		return v;
	}

	private List<String> findBram(MinecraftServer server) {
		if (!(player(server).containerMenu instanceof ChoiceMenu m)) {
			Showcase.check(false, "the hall's list opened");
			return List.of();
		}
		for (int s = VillageHallScreen.FIRST_PERSON; s < ChoiceMenu.SIZE; s++) {
			ItemStack icon = m.icon(s);
			if (icon.getHoverName().getString().startsWith("Bram")) {
				bramSlot = s;
				ItemLore lore = icon.get(DataComponents.LORE);
				return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
			}
		}
		Showcase.check(false, "Bram is on the hall's list");
		return List.of();
	}
}

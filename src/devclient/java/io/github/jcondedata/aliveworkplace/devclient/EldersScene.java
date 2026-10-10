package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Chatter;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Walker;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * SCENE=elders (ROADMAP 34.19): Bram, a farmer one day short of 120 grown days, and Tom, a young one, by a Village
 * Hall. A day passes: the chronicle notes "Bram is an elder now". Stills: Bram's card on the hall's list ("Elder · grown
 * 120 days", and "a quiet old age" first in his mood: he is fed and has a bed); Bram saying "In my day this was all
 * fields."; the two walking the same way side by side, Bram behind. Its checks: each of those read from the game, and
 * Bram covering about 15% less ground than Tom in the same time. (The elder look is 34.18's and is not drawn yet.)
 */
final class EldersScene {
	private static final BlockPos HALL = new BlockPos(0, -60, -3);
	private static final BlockPos BED = new BlockPos(-4, -60, -3);
	private static final int WALK_FROM = 250;
	private Villager bram;
	private Villager tom;
	private int tick;
	private int bramSlot = -1;
	private double bramFrom;
	private double tomFrom;

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
				Chatter.ENABLED = false; // the scene picks the line
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		// The next morning Bram has been grown 120 days: the hall's round notes it, once.
		if (tick == 60) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.setDayTime(level.getDayTime() + VillageNeeds.DAY);
				long today = Chronicle.day(level);
				LifeStages.round(level, HALL, List.of(bram, tom));
				LifeStages.round(level, HALL, List.of(bram, tom));
				Showcase.check(LifeStages.isElder(bram, today) && LifeStages.grownDays(bram, today) == LifeStages.ELDER_DAYS && !LifeStages.isElder(tom, today),
					"a day on Bram is an elder (grown " + LifeStages.grownDays(bram, today) + " days) and Tom is not");
				VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
				long noted = hall.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.LIFE && e.text().getString().equals("Bram is an elder now")).count();
				Showcase.check(noted == 1, "the chronicle says \"Bram is an elder now\", once (" + noted + ")");
				Moods.forget();
				VillageHallScreen.open(player(server), HALL);
			});
		}
		if (tick == 80) {
			server.execute(() -> {
				List<String> card = findBram(server);
				Showcase.check(card.contains("Elder · grown 120 days"), "Bram's card says he is an elder (" + String.join(" | ", card) + ")");
				Moods.Mood mood = Moods.work(server.overworld(), bram);
				Showcase.check(!mood.good().isEmpty() && mood.good().get(0).getString().equals("a quiet old age")
					&& card.stream().anyMatch(l -> l.contains("a quiet old age")), "fed and housed, his mood has \"a quiet old age\" and his card shows it");
				Moods.Mood young = Moods.work(server.overworld(), tom);
				Showcase.check(young.good().stream().noneMatch(r -> r.getString().equals("a quiet old age")), "Tom, a young villager, has no such reason");
			});
		}
		if (tick == 95) {
			ScreenshotHarness.pointAt(mc, bramSlot);
		}
		if (tick == 110) {
			ScreenshotHarness.shot(mc, "01_elders_card");
		}
		if (tick == 120) {
			server.execute(() -> player(server).closeContainer());
		}
		// An elder's own talk.
		if (tick == 150) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Showcase.check(Chatter.topics(level, bram, HALL).contains("elder") && !Chatter.topics(level, tom, HALL).contains("elder"),
					"Bram talks as elders do, Tom doesn't: " + Chatter.topics(level, bram, HALL));
				Component line = Chatter.say(level, bram, player(server), "elder", 0, "");
				bram.setYHeadRot(0f);
				Showcase.check(line.getString().equals("In my day this was all fields."), "Bram says: " + line.getString());
				AliveWorkplace.LOG.info("[elders] Bram: {}", line.getString());
			});
		}
		if (tick == 180) {
			ScreenshotHarness.shot(mc, "02_elders_talk");
		}
		// The same walk, side by side: Bram falls behind.
		if (tick == 215) {
			server.execute(() -> {
				bram.moveTo(-6.5, -60, 1.5, -90f, 0f);
				tom.moveTo(-6.5, -60, 3.5, -90f, 0f);
				bram.setNoAi(false);
				tom.setNoAi(false);
			});
		}
		if (tick >= 220 && tick < WALK_FROM + 75) {
			server.execute(() -> {
				Walker.requestWalk(bram, new BlockPos(10, -60, 1), 0.5f, 0, 0);
				Walker.requestWalk(tom, new BlockPos(10, -60, 3), 0.5f, 0, 0);
			});
		}
		if (tick == WALK_FROM) {
			server.execute(() -> {
				bramFrom = bram.getX();
				tomFrom = tom.getX();
				float elder = bram.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getSpeedModifier()).orElse(0f);
				float young = tom.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(t -> t.getSpeedModifier()).orElse(0f);
				Showcase.check(young == 0.5f && Math.abs(elder - 0.5f * LifeStages.ELDER_WALK) < 0.001f, "the elder's walk is set slower: " + elder + " against " + young);
			});
		}
		if (tick == WALK_FROM + 60) {
			server.execute(() -> {
				double elder = bram.getX() - bramFrom;
				double young = tom.getX() - tomFrom;
				Showcase.check(young > 3 && elder < young * 0.92 && elder > young * 0.78,
					"in the same 3 seconds Bram walked " + Math.round(100 * elder / young) + "% as far as Tom (" + elder + " and " + young + " blocks)");
			});
			ScreenshotHarness.shot(mc, "03_elders_walk");
		}
		if (tick == WALK_FROM + 90) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** A Village Hall, Bram (grown 119 days, fed, with a bed of his own) and Tom (grown 30 days) in front of it facing the player. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		level.setBlockAndUpdate(BED, Blocks.RED_BED.defaultBlockState());
		long today = Chronicle.day(level);
		bram = villager(level, "Bram", -1.5);
		LifeStages.setAdultSince(bram, today - LifeStages.ELDER_DAYS + 1);
		ModAttachments.LAST_MEAL.set(bram, level.getGameTime());
		bram.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), BED));
		tom = villager(level, "Tom", 2.5);
		LifeStages.setAdultSince(tom, today - 30);
		ModAttachments.LAST_MEAL.set(tom, level.getGameTime());
		player.getInventory().clearContent();
		player.setGameMode(GameType.SURVIVAL);
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
		player.teleportTo(level, 0.5, -60, 7.5, 180f, 8f);
		Showcase.check(!LifeStages.isElder(bram, today) && LifeStages.grownDays(bram, today) == LifeStages.ELDER_DAYS - 1,
			"Bram was staged a day short of an elder (grown " + LifeStages.grownDays(bram, today) + " days)");
	}

	private static Villager villager(ServerLevel level, String name, double x) {
		Villager v = EntityType.VILLAGER.create(level);
		v.setVillagerData(v.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(2));
		v.setVillagerXp(10);
		v.setCustomName(Component.literal(name));
		v.setNoAi(true); // they stay in the picture until the walk
		v.moveTo(x, -60, 0.5, 0f, 0f);
		v.setYHeadRot(0f);
		v.setYBodyRot(0f);
		level.addFreshEntity(v);
		return v;
	}

	/** Finds Bram's card on the hall's list (its slot is kept for the pointer) and returns its lines. */
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

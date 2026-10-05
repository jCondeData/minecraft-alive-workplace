package io.github.jcondedata.aliveworkplace.devclient;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.client.LegendLookLayer;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.legend.Legend;
import io.github.jcondedata.aliveworkplace.legend.LegendLook;
import io.github.jcondedata.aliveworkplace.legend.LegendRecord;
import io.github.jcondedata.aliveworkplace.legend.Legends;
import io.github.jcondedata.aliveworkplace.legend.LegendsPage;
import io.github.jcondedata.aliveworkplace.legend.Rarity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=legends_hall (ROADMAP 29.4): a Legend of the scene's own (a Master Builder, "Ada Stonewright") in the stand-in
 * Legend outfit over the builder's, her name in gold and the end-rod sparkle, then the hall's list with her first and
 * the Legends page with a card for each Legend of a small staged roster, at GUI scales 2 and 4. The roster's titles are
 * plain words (no lang keys of their own: the twelve real Legends bring theirs).
 */
final class LegendsHallScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 3);
	private static final BlockPos ADA = new BlockPos(2, -60, 8);
	private int tick;
	private int lookAt = -1;
	private volatile int adaId = -1;
	private volatile int sparkAge = -1;
	private volatile int cardSlot = VillageHallScreen.FIRST_ROW;

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
		if (tick > 120 && lookAt < 0) {
			// the still waits for a sparkle: shot a few ticks after one (every 200 ticks of her life), or gives up at 500
			server.execute(() -> {
				if (server.overworld().getEntity(adaId) instanceof Villager ada) {
					sparkAge = ada.tickCount % 200;
				}
			});
			if (sparkAge >= 8 && sparkAge <= 30 || tick > 500) {
				lookAt = tick;
			}
		}
		if (lookAt > 0 && tick == lookAt) {
			var seen = mc.level == null ? null : mc.level.getEntity(adaId);
			Showcase.check(seen != null && LegendLookLayer.isLegend(seen), "the client was told Ada is a Legend (her outfit and gold name)");
			Showcase.check(sparkAge >= 0 && sparkAge <= 30, "the still is taken just after her sparkle (" + sparkAge + " ticks after)");
			ScreenshotHarness.shot(mc, "01_legend_look");
			server.execute(() -> ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0),
				Vec3.atBottomCenterOf(ADA).add(-2.6, 1.6, -2.6), Vec3.atBottomCenterOf(ADA).add(0, 1.0, 0)));
		}
		if (lookAt > 0 && tick == lookAt + 20) {
			ScreenshotHarness.shot(mc, "02_legend_cape");
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				VillageHallScreen.open(player, HALL);
			});
		}
		if (lookAt > 0 && tick == lookAt + 35) {
			ScreenshotHarness.pointAt(mc, VillageHallScreen.FIRST_PERSON);
		}
		if (lookAt > 0 && tick == lookAt + 50) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.containerMenu instanceof ChoiceMenu m) {
					var first = m.icon(VillageHallScreen.FIRST_PERSON);
					Showcase.check(first.getHoverName().getString().startsWith("Ada Stonewright, Master Architect"),
						"the Legend is first on the hall's list, name and title (" + first.getHoverName().getString() + ")");
				}
			});
			ScreenshotHarness.shot(mc, "03_legend_list");
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (player.containerMenu instanceof ChoiceMenu m) {
					m.press(HallPages.slot(LegendsPage.PAGE), player);
					Showcase.check(m.icon(4).is(Items.NETHER_STAR), "the Legends page opened from its nether star");
					for (int s = VillageHallScreen.FIRST_ROW; s < ChoiceMenu.SIZE; s++) {
						if (m.icon(s).getHoverName().getString().equals("Grand Chef")) {
							cardSlot = s;
						}
					}
				}
			});
		}
		if (lookAt > 0 && tick == lookAt + 65) {
			ScreenshotHarness.pointAt(mc, cardSlot);
		}
		if (lookAt > 0 && tick == lookAt + 80) {
			Showcase.check(cardSlot != VillageHallScreen.FIRST_ROW, "the Grand Chef has a card after the village's own Legend");
			ScreenshotHarness.shot(mc, "04_legends_page_scale2");
			// GUI scale 4 needs a window at least 1280x960 (as the hall scene does): resize first, then set the scale.
			org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
		}
		if (lookAt > 0 && tick == lookAt + 90) {
			mc.options.guiScale().set(4);
			mc.resizeDisplay();
		}
		if (lookAt > 0 && tick == lookAt + 100) {
			ScreenshotHarness.pointAt(mc, VillageHallScreen.FIRST_ROW);
		}
		if (lookAt > 0 && tick == lookAt + 115) {
			Showcase.check(mc.getWindow().getGuiScale() == 4, "the Legends page at GUI scale 4 (now " + mc.getWindow().getGuiScale() + ")");
			ScreenshotHarness.shot(mc, "05_legends_page_scale4");
		}
		if (lookAt > 0 && tick == lookAt + 130) {
			mc.stop();
		}
	}

	private static Legend legend(String id, String rarity, String job, String title, String rest) {
		return Legends.read(io.github.jcondedata.aliveworkplace.AliveWorkplace.id(id), JsonParser.parseString("{\"rarity\": \"" + rarity
			+ "\", \"job\": \"" + job + "\", \"title\": \"" + title + "\", \"lore\": \"" + title + "\"" + rest + "}").getAsJsonObject());
	}

	/** The village: its hall (a Town), Ada the Legend in a fenced garden by it, two neighbours, and a roster of five Legends. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setRank(VillageRanks.Rank.TOWN);
		hall.addTreasuryTotal(6400); // 64 emeralds taken in
		Map<ResourceLocation, Legend> roster = new LinkedHashMap<>();
		Legend architect = legend("showcase_architect", "legendary", "aliveworkplace:builder", "Master Architect",
			", \"conditions\": [{\"type\": \"rank_at_least\", \"rank\": \"town\"}, {\"type\": \"finished\", \"styles\": 3}],"
				+ " \"arrive\": [{\"way\": \"visit\", \"place\": \"inn\"}], \"needs\": {\"luxury\": \"jewels\"},"
				+ " \"powers\": [{\"type\": \"pace\", \"trades\": [\"aliveworkplace:builder\"], \"radius\": 32, \"factor\": 2.0}]");
		Legend chef = legend("showcase_chef", "rare", "aliveworkplace:chef", "Grand Chef",
			", \"conditions\": [{\"type\": \"villagers\", \"count\": 8}, {\"type\": \"treasury_total\", \"emeralds\": 100}],"
				+ " \"arrive\": [{\"way\": \"visit\", \"place\": \"inn\"}, {\"way\": \"born\", \"trades\": [\"aliveworkplace:chef\"]}],"
				+ " \"needs\": {\"luxury\": \"wine\"}, \"powers\": [{\"type\": \"mood\", \"points\": 20, \"radius\": 0}]");
		Legend pathfinder = legend("showcase_pathfinder", "rare", "minecraft:cartographer", "Pathfinder",
			", \"conditions\": [{\"type\": \"rank_at_least\", \"rank\": \"village\"}],"
				+ " \"arrive\": [{\"way\": \"found\", \"site\": \"ruined_portal\"}], \"needs\": {\"luxury\": \"clothes\"}");
		Legend merchant = legend("showcase_merchant", "legendary", "aliveworkplace:legend", "Merchant Prince",
			", \"conditions\": [{\"type\": \"treasury_total\", \"emeralds\": 500}], \"arrive\": [{\"way\": \"found\", \"site\": \"shipwreck\"}]");
		Legend founder = legend("showcase_founder", "mythic", "aliveworkplace:legend", "Founder",
			", \"conditions\": [{\"type\": \"first_city\"}], \"arrive\": [{\"way\": \"inspired\", \"founder\": true}]");
		for (Legend l : new Legend[]{architect, chef, pathfinder, merchant, founder}) {
			if (l != null) {
				roster.put(l.id(), l);
			}
		}
		Legends.setForTest(roster);
		// the Merchant Prince already lives in another village of the world
		LegendRecord.get(level).settled(merchant.id(), UUID.nameUUIDFromBytes("showcase_merchant".getBytes()), level.dimension(),
			Optional.of(new BlockPos(900, -60, -400)), Rarity.LEGENDARY, "Corin", 1);

		for (int x = 0; x <= 4; x++) { // a little fenced garden for Ada, so she stays in the picture
			for (int z = 6; z <= 10; z++) {
				if (x == 0 || x == 4 || z == 6 || z == 10) {
					level.setBlockAndUpdate(new BlockPos(x, -60, z), Blocks.OAK_FENCE.defaultBlockState());
				}
			}
		}
		level.setBlockAndUpdate(new BlockPos(1, -60, 7), Blocks.POPPY.defaultBlockState());
		level.setBlockAndUpdate(new BlockPos(3, -60, 9), Blocks.CORNFLOWER.defaultBlockState());
		Villager ada = EntityType.VILLAGER.spawn(level, ADA, MobSpawnType.COMMAND);
		ada.setVillagerData(ada.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(2));
		ada.setCustomName(Component.literal("Ada Stonewright"));
		ada.setYRot(0); // facing south, to the camera
		ada.setYHeadRot(0);
		Legends.make(level, ada, architect, "showcase");
		adaId = ada.getId();
		Villager bo = EntityType.VILLAGER.spawn(level, new BlockPos(-4, -60, 6), MobSpawnType.COMMAND);
		bo.setCustomName(Component.literal("Bo"));
		Villager cy = EntityType.VILLAGER.spawn(level, new BlockPos(-6, -60, 8), MobSpawnType.COMMAND);
		cy.setCustomName(Component.literal("Cy"));
		Showcase.check(LegendLook.look(ada) != null, "Ada is a Legend, with a look to send");
		ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), Vec3.atBottomCenterOf(ADA).add(0, 1.4, 3.6),
			Vec3.atBottomCenterOf(ADA).add(0, 1.2, 0));
	}
}

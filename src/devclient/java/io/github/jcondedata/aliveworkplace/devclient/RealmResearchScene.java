package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmResearch;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * SCENE=realm_research (ROADMAP 33.15): the Barony of Thornholm, with Ashford a member. Thornholm's scholars know Drill
 * II, Swift Hands III and Hearth I; the realm has Royal Roads I and is researching Common Coin, paid from the
 * treasury and 40% done. The capital's research screen, then its Realm tab with the realm topics; a realm topic's
 * tooltip; last, Ashford's research screen, where Drill is "From the capital".
 */
final class RealmResearchScene {
	private int tick;
	private volatile BlockPos hall;
	private volatile BlockPos ashford;
	private volatile Villager scholar;
	private volatile Villager ashfordScholar;

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
				level.setDayTime(6000);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -40 -40 120 40");
			});
		}
		if (tick == 380) {
			server.execute(() -> {
				stage(server);
				ResearchScreen.open(player(server), scholar);
			});
		}
		// The capital's own research screen: the Realm tab on the bottom row.
		if (tick == 410) {
			ScreenshotHarness.pointAt(mc, ResearchScreen.REALM_TAB);
		}
		if (tick == 430) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_the_realm_tab");
			server.execute(() -> {
				boolean tab = player(server).containerMenu instanceof ChoiceMenu menu && menu.icon(ResearchScreen.REALM_TAB).is(Items.GOLDEN_HELMET);
				Showcase.check(tab, "the capital's research screen has the Realm tab");
				if (player(server).containerMenu instanceof ChoiceMenu menu) {
					menu.press(ResearchScreen.REALM_TAB, player(server));
					menu.broadcastChanges();
				}
			});
		}
		// The Realm row: the eight realm topics (33.16), Royal Roads I done, Common Coin being researched.
		if (tick == 450) {
			ScreenshotHarness.pointAt(mc, 4);
		}
		if (tick == 470) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_the_realm_row");
			server.execute(() -> {
				boolean row = player(server).containerMenu instanceof ChoiceMenu menu
					&& RealmResearch.all().size() == 8
					&& menu.icon(ResearchScreen.TOPIC_SLOTS[slotOf("common_coin")]).is(Items.GOLD_NUGGET)
					&& menu.icon(ResearchScreen.TOPIC_SLOTS[slotOf("royal_roads")]).is(Items.DIRT_PATH)
					&& menu.icon(ResearchScreen.TOPIC_SLOTS[slotOf("granaries")]).is(Items.HAY_BLOCK);
				Showcase.check(row, "the Realm tab lists the eight realm topics");
			});
		}
		if (tick == 490) {
			ScreenshotHarness.pointAt(mc, ResearchScreen.TOPIC_SLOTS[slotOf("granaries")]);
		}
		if (tick == 510) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "03_a_realm_topic");
			server.execute(() -> {
				ServerPlayer player = player(server);
				player.closeContainer();
				ServerLevel level = server.overworld();
				int z = ashford.getZ() + 3;
				player.teleportTo(level, ashford.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ashford.getX(), z), z + 0.5, 180, 10);
			});
		}
		if (tick == 540) {
			server.execute(() -> ResearchScreen.open(player(server), ashfordScholar));
		}
		// A member's screen: Drill from the capital.
		if (tick == 560) {
			ScreenshotHarness.pointAt(mc, ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()]);
		}
		if (tick == 580) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "04_from_the_capital");
			server.execute(() -> {
				boolean shared = player(server).containerMenu instanceof ChoiceMenu menu
					&& lore(menu.icon(ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()])).stream().anyMatch(l -> l.startsWith("From the capital"));
				Showcase.check(shared, "Ashford's Drill comes from the capital");
			});
		}
		if (tick == 600) {
			mc.stop();
		}
	}

	private static int slotOf(String key) {
		List<String> keys = new ArrayList<>();
		RealmResearch.all().forEach(t -> keys.add(t.key()));
		return Math.max(0, keys.indexOf(key));
	}

	private static List<String> lore(ItemStack stack) {
		return stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines().stream().map(Component::getString).toList();
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Thornholm, a Town with its realm and research, and Ashford a member, each with a scholar by the hall. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
			"place structure minecraft:village_plains 0 -60 0");
		int x = 4;
		int zc = 4;
		hall = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, zc), zc);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		ServerPlayer player = player(server);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		int z = hall.getZ() + 3;
		player.teleportTo(level, hall.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, hall.getX(), z), z + 0.5, 180, 10);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.TOWN);
		entity.setResearch(new Research.State(Map.of("drill", 2, "swift_hands", 3, "hearth", 1), Optional.empty(), 0, false));
		Realms.round(level, hall, entity, 24);
		ashford = new BlockPos(70, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 70, 4), 4);
		level.setBlockAndUpdate(ashford, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity member = (VillageHallBlockEntity) level.getBlockEntity(ashford);
		member.setCustomName(Component.literal("Ashford"));
		member.setOwner(player.getUUID(), player.getGameProfile().getName());
		member.setRank(VillageRanks.Rank.VILLAGE);
		Realms.round(level, ashford, member, 12);
		Realms.found(level, hall, player);
		Realms.add(level, hall, GlobalPos.of(level.dimension(), ashford), player);
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(GlobalPos.of(level.dimension(), hall));
		if (realm != null) {
			data.putRealm(realm.withTreasury(18_400, realm.ledger()));
			Research.State realmState = new Research.State(Map.of("royal_roads", 1), Optional.of("common_coin"),
				RealmResearch.get("common_coin").map(t -> t.points() * 2 / 5).orElse(0), true);
			data.putStudy(data.study(realm.id()).withResearch(realmState));
		}
		// the halls' records now, not in vanilla's next task: ResearchScreen.open finds the scholar's hall by them, in
		// this same task, and found none, so the capital's screen never opened (B128)
		ScreenshotHarness.fileRecords(level, hall, 2);
		ScreenshotHarness.fileRecords(level, ashford, 2);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		scholar = scholarAt(level, hall.offset(-2, 0, 2));
		ashfordScholar = scholarAt(level, ashford.offset(-2, 0, 2));
	}

	private static Villager scholarAt(ServerLevel level, BlockPos near) {
		BlockPos at = new BlockPos(near.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()), near.getZ());
		Villager villager = EntityType.VILLAGER.create(level);
		villager.setVillagerData(villager.getVillagerData().setProfession(ModVillagers.SCHOLAR).setLevel(2));
		villager.setNoAi(true);
		villager.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		level.addFreshEntity(villager);
		return villager;
	}
}

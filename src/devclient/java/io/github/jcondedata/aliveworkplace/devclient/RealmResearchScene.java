package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.RealmResearch;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ResearchScreen;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * SCENE=realm_research (ROADMAP 33.15): Thornholm, a Town, founds a realm and adds Ashford. The realm has Common Law II
 * and the capital's scholars are on Guild Exchange I, paid from the treasury; the capital's research screen shows its
 * Realm row (the label, then the realm topics). Last, Ashford's own research screen: Drill II "from the capital".
 */
final class RealmResearchScene {
	private int tick;
	private volatile BlockPos hall;
	private volatile BlockPos ashford;
	private volatile BlockPos farholt;

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
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -120 -120 120 120");
			});
		}
		if (tick == 380) {
			server.execute(() -> {
				stage(server);
				found(server);
				openResearch(server, hall);
			});
		}
		// The capital's Realm row: its label first.
		if (tick == 420) {
			ScreenshotHarness.pointAt(mc, RealmResearch.LABEL);
		}
		if (tick == 440) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_realm_row");
			server.execute(() -> {
				boolean shown = player(server).containerMenu instanceof ChoiceMenu menu && menu.icon(RealmResearch.LABEL).is(Items.GOLDEN_HELMET)
					&& !menu.icon(RealmResearch.SLOTS[0]).isEmpty();
				Showcase.check(shown, "the capital's research screen shows the Realm row");
			});
		}
		// A realm topic: the one being researched.
		if (tick == 460) {
			ScreenshotHarness.pointAt(mc, RealmResearch.SLOTS[2]);
		}
		if (tick == 480) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_realm_topic");
		}
		// Ashford's own screen: Drill from the capital.
		if (tick == 500) {
			server.execute(() -> {
				player(server).closeContainer();
				ServerLevel level = server.overworld();
				ServerPlayer player = player(server);
				int z = ashford.getZ() + 3;
				player.teleportTo(level, ashford.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ashford.getX(), z), z + 0.5, 180, 10);
				openResearch(server, ashford);
			});
		}
		if (tick == 540) {
			ScreenshotHarness.pointAt(mc, ResearchScreen.TOPIC_SLOTS[Research.Topic.DRILL.ordinal()]);
		}
		if (tick == 560) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "03_from_the_capital");
			server.execute(() -> {
				boolean shared = Research.at(server.overworld(), ashford).level(Research.Topic.DRILL) == 2
					&& player(server).containerMenu instanceof ChoiceMenu menu && menu.icon(RealmResearch.LABEL).isEmpty();
				Showcase.check(shared, "Ashford uses the capital's Drill II, and has no Realm row");
			});
		}
		if (tick == 580) {
			mc.stop();
		}
	}

	/** The realm founded with Ashford in it; the capital's levels, the realm's research and its treasury set. */
	private void found(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		Realms.found(level, hall, player);
		Realms.add(level, hall, GlobalPos.of(level.dimension(), ashford), player);
		Realms.add(level, hall, GlobalPos.of(level.dimension(), farholt), player);
		if (level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
			entity.setResearch(new Research.State(Map.of("swift_hands", 2, "hearth", 1, "drill", 2), Optional.empty(), 0, false));
		}
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(GlobalPos.of(level.dimension(), hall));
		if (realm != null) {
			data.putRealm(realm.withTreasury(18000, realm.ledger())
				.withResearch(new Research.State(Map.of("common_law", 2), Optional.of("guild_exchange"), 1900, true)));
		}
	}

	/** The research screen of the village at {@code at}, from a scholar standing by the player. */
	private static void openResearch(MinecraftServer server, BlockPos at) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		Villager scholar = EntityType.VILLAGER.create(level);
		if (scholar == null) {
			return;
		}
		scholar.moveTo(player.getX() + 1, player.getY(), player.getZ() - 1, 0, 0);
		scholar.setVillagerData(new VillagerData(VillagerType.PLAINS, ModVillagers.SCHOLAR, 2));
		scholar.setNoAi(true);
		level.addFreshEntity(scholar);
		ResearchScreen.open(player, scholar);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.broadcastChanges();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Thornholm's hall in a plains village, a Town owned by the player; Ashford and Farholt, the player's too, a little way off. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
			"place structure minecraft:village_plains 0 -60 0");
		BlockPos spot = null;
		for (int r = 2; r <= 16 && spot == null; r++) {
			for (int dx = -r; dx <= r && spot == null; dx++) {
				for (int dz = -r; dz <= r && spot == null; dz++) {
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dx, dz);
					BlockPos below = new BlockPos(dx, top - 1, dz);
					if (top <= -59 && (level.getBlockState(below).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)
						|| level.getBlockState(below).is(net.minecraft.world.level.block.Blocks.DIRT_PATH))) {
						spot = below.above();
					}
				}
			}
		}
		hall = spot != null ? spot : new BlockPos(4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 4, 4), 4);
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
		Realms.round(level, hall, entity, 24);
		ashford = village(level, player, hall.offset(80, 0, -40), "Ashford", VillageRanks.Rank.VILLAGE, 12);
		farholt = village(level, player, hall.offset(-90, 0, 60), "Farholt", VillageRanks.Rank.HAMLET, 6);
	}

	/** Another village of the player's: its hall, named, of {@code rank}, seen by a round with {@code villagers}. */
	private static BlockPos village(ServerLevel level, ServerPlayer player, BlockPos at, String name, VillageRanks.Rank rank, int villagers) {
		BlockPos pos = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
		level.setBlockAndUpdate(pos, ModBlocks.VILLAGE_HALL.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof VillageHallBlockEntity entity) {
			entity.setCustomName(Component.literal(name));
			entity.setOwner(player.getUUID(), player.getGameProfile().getName());
			entity.setRank(rank);
			Realms.round(level, pos, entity, villagers);
		}
		return pos;
	}
}

package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.realm.Pacts;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * SCENE=pacts (ROADMAP 33.18): the player owns Ashford, a Town in a plains village; Rowan, another player, owns
 * Thornholm next door. Rowan offers Ashford an alliance: the letter lands in the player's hands and they read it;
 * Ashford's Pacts tab shows "Accept an alliance with Thornholm" and its Decline; the player accepts, and the tab shows
 * the alliance with its gifts switch and its End button.
 */
final class PactsScene {
	private static final UUID ROWAN = UUID.nameUUIDFromBytes("showcase:rowan".getBytes(java.nio.charset.StandardCharsets.UTF_8));
	private static final int FIRST = VillageHallScreen.FIRST_ROW;

	private int tick;
	private volatile BlockPos ashford;
	private volatile BlockPos thornholm;

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
			server.execute(() -> stage(server));
		}
		// Rowan offers Ashford an alliance: the letter comes to the player, who reads it.
		if (tick == 420) {
			server.execute(() -> {
				ServerPlayer rowan = new ServerPlayer(server, server.overworld(), new com.mojang.authlib.GameProfile(ROWAN, "Rowan"), ClientInformation.createDefault());
				Pacts.Result sent = Pacts.offer(server.overworld(), thornholm, ashford, true, rowan);
				ServerPlayer player = player(server);
				int slot = -1;
				for (int i = 0; i < player.getInventory().getContainerSize() && slot < 0; i++) {
					if (player.getInventory().getItem(i).is(Items.WRITTEN_BOOK)) {
						slot = i;
					}
				}
				if (slot > 0) {
					ItemStack letter = player.getInventory().removeItemNoUpdate(slot);
					player.getInventory().setItem(0, letter);
				}
				player.getInventory().selected = 0;
				Showcase.check(sent.ok() && player.getInventory().getItem(0).is(Items.WRITTEN_BOOK), "Rowan's offer sent a letter to Ashford's owner: " + sent.message().getString());
			});
		}
		if (tick == 440) {
			ItemStack book = mc.player.getMainHandItem();
			if (book.is(Items.WRITTEN_BOOK)) {
				mc.setScreen(new BookViewScreen(BookViewScreen.BookAccess.fromItem(book)));
			}
		}
		if (tick == 460) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "01_the_letter");
			Showcase.check(mc.screen instanceof BookViewScreen, "the letter opens like a book");
			mc.setScreen(null);
		}
		// Ashford's Pacts tab: the offer, Accept or Decline.
		if (tick == 470) {
			server.execute(() -> openTab(server));
		}
		if (tick == 490) {
			ScreenshotHarness.pointAt(mc, FIRST);
		}
		if (tick == 510) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_the_offer");
			server.execute(() -> {
				ServerPlayer player = player(server);
				boolean shown = player.containerMenu instanceof ChoiceMenu menu && menu.icon(FIRST).getHoverName().getString().equals("Accept an alliance with Thornholm")
					&& menu.icon(FIRST + 1).is(Items.BARRIER);
				Showcase.check(shown, "Ashford's Pacts tab offers to accept the alliance with Thornholm, or decline");
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(FIRST, player); // accept: the real button
					menu.broadcastChanges();
				}
				Pacts.Pact pact = Pacts.Data.get(server).pact(at(server, ashford), at(server, thornholm));
				Showcase.check(pact != null && pact.alliance(), "Ashford accepted: an alliance with Thornholm");
			});
		}
		if (tick == 530) {
			ScreenshotHarness.pointAt(mc, FIRST);
		}
		if (tick == 550) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "03_the_alliance");
			server.execute(() -> {
				ServerPlayer player = player(server);
				boolean shown = player.containerMenu instanceof ChoiceMenu menu && menu.icon(FIRST).getHoverName().getString().equals("Alliance with Thornholm")
					&& menu.icon(FIRST + 1).is(Items.FLINT);
				Showcase.check(shown, "the tab shows the alliance with Thornholm and its End button");
			});
		}
		if (tick == 570) {
			mc.stop();
		}
	}

	private void openTab(MinecraftServer server) {
		ServerPlayer player = player(server);
		VillageHallScreen.open(player, ashford);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(VillageHallScreen.ROUTES, player);
			menu.press(TradePage.Tab.PACTS.slot(), player);
			menu.broadcastChanges();
		}
	}

	private static GlobalPos at(MinecraftServer server, BlockPos pos) {
		return GlobalPos.of(server.overworld().dimension(), pos);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Ashford's hall in a plains village, the player's Town; Rowan's Thornholm a little way off. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
			"place structure minecraft:village_plains 0 -60 0");
		BlockPos spot = VillageGround.hallSpot(level); // open ground in front, where the player stands (B293)
		ashford = spot != null ? spot : new BlockPos(4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 4, 4), 4);
		level.setBlockAndUpdate(ashford, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		ServerPlayer player = player(server);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		int z = ashford.getZ() + VillageGround.FRONT;
		player.teleportTo(level, ashford.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ashford.getX(), z), z + 0.5, 180, 10);
		Showcase.check(player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(ashford)) <= 64,
			"the player stands by Ashford's hall, within reach of its screen");
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(ashford);
		entity.setCustomName(Component.literal("Ashford"));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.TOWN);
		Realms.round(level, ashford, entity, 18);
		thornholm = village(level, ashford.offset(80, 0, -40), "Thornholm", VillageRanks.Rank.TOWN, 24);
		// both on the caravans' list, as their halls' rounds put them
		Caravans.Data caravans = Caravans.Data.get(level);
		caravans.setWants(ashford, Component.literal("Ashford"), List.of());
		caravans.setWants(thornholm, Component.literal("Thornholm"), List.of());
	}

	/** One of Rowan's villages: its hall, named, of {@code rank}, seen by a round with {@code villagers}. */
	private static BlockPos village(ServerLevel level, BlockPos at, String name, VillageRanks.Rank rank, int villagers) {
		BlockPos pos = new BlockPos(at.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()), at.getZ());
		level.setBlockAndUpdate(pos, ModBlocks.VILLAGE_HALL.defaultBlockState());
		if (level.getBlockEntity(pos) instanceof VillageHallBlockEntity entity) {
			entity.setCustomName(Component.literal(name));
			entity.setOwner(ROWAN, "Rowan");
			entity.setRank(rank);
			Realms.round(level, pos, entity, villagers);
		}
		return pos;
	}
}

package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.CapitalVotes;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.RealmInvites;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.Optional;
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
 * SCENE=realm_invite (ROADMAP 33.12): the player owns Ashford, a Town in a plains village; Rowan, another player, rules
 * the Barony of Thornholm next door (Thornholm and Farholt). Rowan invites Ashford: the letter lands in the player's
 * hands and they read it; Ashford's Realm tab shows "Join the Barony of Thornholm" and its Decline; the player accepts,
 * then puts Ashford forward as capital: Rowan owns the other two villages, so the members' owners vote for a day, and
 * the tab shows the vote, its count and the two buttons.
 */
final class RealmInviteScene {
	private static final UUID ROWAN = UUID.nameUUIDFromBytes("showcase:rowan".getBytes(java.nio.charset.StandardCharsets.UTF_8));

	private int tick;
	private volatile BlockPos ashford;
	private volatile BlockPos thornholm;
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
			server.execute(() -> stage(server));
		}
		// Rowan invites Ashford: the letter comes to the player, who reads it.
		if (tick == 420) {
			server.execute(() -> {
				ServerPlayer rowan = new ServerPlayer(server, server.overworld(), new com.mojang.authlib.GameProfile(ROWAN, "Rowan"), ClientInformation.createDefault());
				Realms.Result sent = RealmInvites.invite(server.overworld(), thornholm, at(server, ashford), rowan);
				ServerPlayer player = player(server);
				int slot = player.getInventory().findSlotMatchingItem(new ItemStack(Items.WRITTEN_BOOK));
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
				Showcase.check(sent.ok() && player.getInventory().getItem(0).is(Items.WRITTEN_BOOK), "Rowan's invitation sent a letter to Ashford's owner: " + sent.message().getString());
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
		// Ashford's Realm tab: the invitation, Join or Decline.
		if (tick == 470) {
			server.execute(() -> openTab(server));
		}
		if (tick == 490) {
			ScreenshotHarness.pointAt(mc, Realms.INVITES);
		}
		if (tick == 510) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_the_invitation");
			server.execute(() -> {
				ServerPlayer player = player(server);
				boolean shown = player.containerMenu instanceof ChoiceMenu menu && menu.icon(Realms.INVITES).getHoverName().getString().equals("Join the Barony of Thornholm")
					&& menu.icon(Realms.INVITES + 1).is(Items.BARRIER);
				Showcase.check(shown, "Ashford's Realm tab offers to join the Barony of Thornholm, or decline");
				towns(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(Realms.INVITES, player); // accept: the real button
					menu.press(Realms.PUT_FORWARD, player); // and put Ashford forward as capital
					menu.broadcastChanges();
				}
				RealmData data = RealmData.get(server);
				RealmData.Realm realm = data.realmOf(at(server, ashford));
				Showcase.check(realm != null && realm.members().size() == 3, "Ashford accepted and joined the realm");
				RealmData.Vote vote = realm == null ? null : data.vote(realm.id());
				Showcase.check(vote != null && vote.candidate().equals(at(server, ashford)), "putting Ashford forward opened a vote (two owners)");
			});
		}
		if (tick == 530) {
			ScreenshotHarness.pointAt(mc, Realms.VOTE);
		}
		if (tick == 550) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "03_the_vote");
			server.execute(() -> {
				ServerPlayer player = player(server);
				boolean shown = player.containerMenu instanceof ChoiceMenu menu && menu.icon(Realms.VOTE).getHoverName().getString().equals("Vote: move the capital to Ashford?")
					&& menu.icon(Realms.VOTE_FOR).is(Items.LIME_WOOL) && menu.icon(Realms.VOTE_AGAINST).is(Items.RED_WOOL);
				Showcase.check(shown, "the tab shows the vote on Ashford as capital, with its two buttons");
			});
		}
		if (tick == 570) {
			ScreenshotHarness.pointAt(mc, Realms.VOTE_FOR);
		}
		if (tick == 590) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "04_vote_for");
			server.execute(() -> {
				RealmData data = RealmData.get(server);
				RealmData.Realm realm = data.realmOf(at(server, ashford));
				RealmData.Vote vote = realm == null ? null : data.vote(realm.id());
				Showcase.check(vote != null && CapitalVotes.tally(realm, vote).equals(new CapitalVotes.Tally(1, 2)),
					"the count: Ashford for, Rowan's two villages counted for Thornholm until they vote");
			});
		}
		if (tick == 610) {
			mc.stop();
		}
	}

	private void openTab(MinecraftServer server) {
		ServerPlayer player = player(server);
		VillageHallScreen.open(player, ashford);
		if (player.containerMenu instanceof ChoiceMenu menu) {
			menu.press(VillageHallScreen.ROUTES, player);
			menu.press(TradePage.Tab.REALM.slot(), player);
			menu.broadcastChanges();
		}
	}

	/** Ashford and Thornholm Towns (staged by fiat: a hall's round counts a real village's rank again). */
	private void towns(MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (BlockPos pos : List.of(ashford, thornholm)) {
			if (level.getBlockEntity(pos) instanceof VillageHallBlockEntity entity) {
				entity.setRank(VillageRanks.Rank.TOWN);
				Realms.round(level, pos, entity, -1);
			}
		}
	}

	private static GlobalPos at(MinecraftServer server, BlockPos pos) {
		return GlobalPos.of(server.overworld().dimension(), pos);
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** Ashford's hall in a plains village, the player's Town; Rowan's Barony of Thornholm (Thornholm, a Town, and Farholt) a little way off. */
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
		ashford = spot != null ? spot : new BlockPos(4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 4, 4), 4);
		level.setBlockAndUpdate(ashford, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		ServerPlayer player = player(server);
		player.setGameMode(GameType.SURVIVAL);
		player.getInventory().clearContent();
		int z = ashford.getZ() + 3;
		player.teleportTo(level, ashford.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ashford.getX(), z), z + 0.5, 180, 10);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(ashford);
		entity.setCustomName(Component.literal("Ashford"));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(VillageRanks.Rank.TOWN);
		Realms.round(level, ashford, entity, 18);
		thornholm = village(level, ashford.offset(80, 0, -40), "Thornholm", VillageRanks.Rank.TOWN, 24);
		farholt = village(level, ashford.offset(-90, 0, 60), "Farholt", VillageRanks.Rank.VILLAGE, 11);
		// Rowan's realm, founded before the scene: Thornholm the capital, Farholt a member
		GlobalPos capital = at(server, thornholm);
		RealmData.get(server).putRealm(new RealmData.Realm(UUID.randomUUID(), Optional.empty(), capital,
			List.of(new RealmData.Member(capital, 1), new RealmData.Member(at(server, farholt), 2)), Optional.empty(), Realms.Rank.BARONY.id(), 1));
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

package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.Feuds;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * SCENE=feud (ROADMAP 33.20): the player owns Ashford, a Town in a plains village; Rowan, another player, owns Thornholm
 * next door. Rowan declares a feud on Ashford: the letter lands in the player's hands and they read it; Ashford's Pacts
 * tab shows the feud with its Offer peace button. A day later, on this PvP server, Rowan raises it to war; at dawn the
 * player walks into Thornholm and its guards go for them (a GIF frame every half second).
 */
final class FeudScene {
	private static final UUID ROWAN = UUID.nameUUIDFromBytes("showcase:rowan".getBytes(java.nio.charset.StandardCharsets.UTF_8));
	private static final int FIRST = VillageHallScreen.FIRST_ROW;

	private int tick;
	private volatile BlockPos ashford;
	private volatile BlockPos thornholm;
	private final List<Villager> guards = new ArrayList<>();
	private volatile float startHealth;

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
				server.setPvpAllowed(true);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -120 -120 120 120");
			});
		}
		if (tick == 380) {
			server.execute(() -> stage(server));
		}
		// Rowan declares a feud on Ashford: the letter comes to the player, who reads it.
		if (tick == 420) {
			server.execute(() -> {
				var sent = Feuds.declare(server.overworld(), thornholm, ashford, rowan(server));
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
				Showcase.check(sent.ok() && player.getInventory().getItem(0).is(Items.WRITTEN_BOOK), "Rowan's feud sent a letter to Ashford's owner: " + sent.message().getString());
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
			Showcase.check(mc.screen instanceof BookViewScreen, "the feud's letter opens like a book");
			mc.setScreen(null);
		}
		// Ashford's Pacts tab: the feud, and Offer peace.
		if (tick == 470) {
			server.execute(() -> openTab(server));
		}
		if (tick == 490) {
			ScreenshotHarness.pointAt(mc, FIRST);
		}
		if (tick == 510) {
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, "02_the_feud");
			server.execute(() -> {
				ServerPlayer player = player(server);
				boolean shown = player.containerMenu instanceof ChoiceMenu menu && menu.icon(FIRST).getHoverName().getString().equals("Feud with Thornholm")
					&& menu.icon(FIRST + 1).getHoverName().getString().equals("Offer Thornholm peace");
				Showcase.check(shown, "Ashford's Pacts tab shows the feud with Thornholm and Offer peace");
				player.closeContainer();
			});
		}
		// A day on, Rowan raises it to war; at dawn the player walks into Thornholm and its guards come for them.
		if (tick == 530) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				GlobalPos a = at(server, ashford);
				GlobalPos t = at(server, thornholm);
				Feuds.Feud f = Feuds.feud(server, a, t);
				long now = level.getGameTime();
				Feuds.Data.get(server).put(new Feuds.Feud(f.by(), f.on(), now - Feuds.WAR_AFTER, -1, -1, false, 0, 0, 0));
				var war = Feuds.raiseWar(level, thornholm, a, rowan(server));
				Showcase.check(war.ok(), "Rowan raised the feud to war with PvP on: " + war.message().getString());
				Feuds.Feud raised = Feuds.feud(server, a, t);
				Feuds.Data.get(server).put(new Feuds.Feud(raised.by(), raised.on(), raised.since(), now - 1, now - 1 + Feuds.WAR_DAYS * 24000L, false, 0, 0, 0));
				for (int i = 0; i < 2; i++) {
					guards.add(guard(level, thornholm.offset(-3 + 6 * i, 0, 6)));
				}
				ServerPlayer player = player(server);
				player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 2400, 3, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 2400, 2, false, false));
				startHealth = player.getHealth();
				int z = thornholm.getZ() + 10;
				player.teleportTo(level, thornholm.getX() + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, thornholm.getX(), z), z + 0.5, 180, 15);
				Showcase.check(Guards.isFoe(player, guards.get(0)), "in the war, Thornholm's guard counts Ashford's owner a foe");
			});
		}
		if (tick >= 560 && tick <= 760 && (tick - 560) % 10 == 0) {
			int frame = 3 + (tick - 560) / 10;
			ScreenshotHarness.shot(mc, String.format("%02d_war", frame));
		}
		if (tick == 770) {
			server.execute(() -> {
				Feuds.Feud f = Feuds.feud(server, at(server, ashford), at(server, thornholm));
				ServerPlayer player = player(server);
				boolean hit = player.getLastHurtByMob() instanceof Villager v && Guards.isGuard(v);
				Showcase.check(f != null && Feuds.fighting(server, f) && hit, "a Thornholm guard fought Ashford's owner in the war (hurt by: " + player.getLastHurtByMob() + ")");
			});
		}
		if (tick == 790) {
			mc.stop();
		}
	}

	private static ServerPlayer rowan(MinecraftServer server) {
		return new ServerPlayer(server, server.overworld(), new com.mojang.authlib.GameProfile(ROWAN, "Rowan"), ClientInformation.createDefault());
	}

	/** A Thornholm guard with a sword, its brain a guard's (B117). */
	private static Villager guard(ServerLevel level, BlockPos near) {
		BlockPos at = new BlockPos(near.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, near.getX(), near.getZ()), near.getZ());
		Villager v = EntityType.VILLAGER.create(level);
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		v.finalizeSpawn(level, level.getCurrentDifficultyAt(at), MobSpawnType.EVENT, null);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(3));
		v.setVillagerXp(70);
		v.refreshBrain(level);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		level.addFreshEntity(v);
		return v;
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
		Caravans.Data caravans = Caravans.Data.get(level);
		caravans.setWants(ashford, Component.literal("Ashford"), List.of());
		caravans.setWants(thornholm, Component.literal("Thornholm"), List.of());
	}

	/** Rowan's village: its hall, named, of {@code rank}, seen by a round with {@code villagers}. */
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

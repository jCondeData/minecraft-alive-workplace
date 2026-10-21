package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=builder_table_move (B290): a builder at a workshop's Blueprint Table, its chest holding only planks, with the
 * village Storehouse's chests (the stone) farther off: the line over his head counts both and names the Storehouse. The
 * player, standing by him, sneak-right-clicks a second table with an empty hand: he works there now, both tables
 * standing, and his status says which table. Checks the move and that both tables stand.
 */
final class BuilderTableMoveScene {
	private static final BlockPos TABLE = new BlockPos(-2, -60, 2);
	private static final BlockPos CHEST = new BlockPos(-3, -60, 2);
	private static final BlockPos SECOND = new BlockPos(3, -60, 1);
	private static final BlockPos STOREHOUSE = new BlockPos(-14, -60, -12);
	private static final BlockPos[] STORE_CHESTS = {new BlockPos(-15, -60, -12), new BlockPos(-13, -60, -12)};
	private static final BlockPos BUILDER = new BlockPos(0, -60, 3);
	private static final BlockPos ANCHOR = new BlockPos(0, -60, 10);

	private int tick;
	private volatile Villager builder;
	private volatile String problem = "";
	private volatile boolean moved;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = false;
		}
		if (tick == 20) {
			server.execute(() -> setUp(server));
		}
		if (tick == 120) {
			ScreenshotHarness.shot(mc, "00_counts_the_storehouse");
		}
		if (tick == 130) {
			server.execute(() -> sneakClickSecondTable(server));
		}
		if (tick == 145) {
			mc.setScreen(new ChatScreen(""));
		}
		if (tick == 160) {
			ScreenshotHarness.shot(mc, "01_moved");
			mc.setScreen(null);
			mc.gui.getChat().clearMessages(false);
		}
		if (tick == 170) {
			server.execute(() -> status(server));
		}
		if (tick == 185) {
			mc.setScreen(new ChatScreen(""));
		}
		if (tick == 205) {
			ScreenshotHarness.shot(mc, "02_status_names_the_table");
			Showcase.check(moved && problem.isEmpty(), problem.isEmpty()
				? "a sneak-click on the second Blueprint Table moved the builder there, both tables still standing" : problem);
		}
		if (tick == 215) {
			mc.stop();
		}
	}

	private void setUp(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2500);
		level.setBlockAndUpdate(TABLE, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		level.setBlockAndUpdate(SECOND, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
		level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(CHEST) instanceof Container chest) {
			chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
		}
		level.setBlockAndUpdate(STOREHOUSE, ModBlocks.STOREHOUSE.defaultBlockState());
		for (BlockPos pos : STORE_CHESTS) {
			level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
		}
		if (level.getBlockEntity(STORE_CHESTS[0]) instanceof Container store) {
			store.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
			store.setItem(1, new ItemStack(Items.COBBLESTONE, 64));
			store.setItem(2, new ItemStack(Items.TORCH, 16));
		}
		var blueprint = BlueprintLibrary.get(level, StarterBlueprints.STARTER_COTTAGE.id()).orElse(null);
		if (blueprint == null) {
			problem = "no Starter Cottage blueprint";
			return;
		}
		BlueprintData.Placement placement = BlueprintItem.placementAt(level.dimension().location(), blueprint.size(), ANCHOR,
			net.minecraft.world.level.block.Rotation.NONE);
		Villager v = EntityType.VILLAGER.spawn(level, BUILDER, MobSpawnType.COMMAND);
		if (v == null) {
			problem = "no builder spawned";
			return;
		}
		v.setNoAi(true); // he stands for the pictures
		Builders.employ(level, v, TABLE);
		BuildSite site = Builders.start(level, v, player, StarterBlueprints.STARTER_COTTAGE.id(), placement);
		if (site.plan(level) == null) {
			problem = "no plan for the cottage";
			return;
		}
		builder = v;
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, 0.5, -60, 0.5, 0, 15);
		player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
	}

	/** The player's sneak-right-click on the second table with an empty hand, as the client sends it. */
	private void sneakClickSecondTable(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		ServerLevel level = server.overworld();
		if (builder == null) {
			return;
		}
		player.setShiftKeyDown(true);
		player.gameMode.useItemOn(player, level, ItemStack.EMPTY, InteractionHand.MAIN_HAND,
			new BlockHitResult(Vec3.atCenterOf(SECOND), Direction.UP, SECOND, false));
		player.setShiftKeyDown(false);
		moved = Builders.benchPos(builder).filter(SECOND::equals).isPresent()
			&& level.getBlockState(TABLE).is(ModBlocks.BLUEPRINT_TABLE) && level.getBlockState(SECOND).is(ModBlocks.BLUEPRINT_TABLE);
		if (!moved) {
			problem = "the builder still works at " + Builders.benchPos(builder);
		}
	}

	/** A sneak-right-click on the builder with an empty hand: his status, naming the table he works at. */
	private void status(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		if (builder == null) {
			return;
		}
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(builder, true, InteractionHand.MAIN_HAND,
			new Vec3(0, builder.getBbHeight() / 2, 0)));
		player.setShiftKeyDown(false);
	}
}

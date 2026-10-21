package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.SiteList;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=builder_list (B279): a builder halfway through a Starter Cottage, a chest by his bench holding some of what it
 * needs. The player right-clicks him with a Book and Quill: the book is the whole rest of the build, with what the chest
 * holds checked off; then a sneak-right-click with an empty hand puts the whole list in chat. Checks both.
 */
final class BuilderListScene {
	private static final BlockPos BENCH = new BlockPos(-2, -60, 2);
	private static final BlockPos CHEST = new BlockPos(-3, -60, 2);
	private static final BlockPos BUILDER = new BlockPos(0, -60, 3);
	private static final BlockPos ANCHOR = new BlockPos(0, -60, 10);

	private int tick;
	private volatile Villager builder;
	private volatile String problem = "";
	private volatile int chatLines;

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
		if (tick == 100) {
			ScreenshotHarness.shot(mc, "00_half_built");
		}
		if (tick == 110) {
			server.execute(() -> rightClick(server, false));
		}
		if (tick == 130) {
			ItemStack book = mc.player.getMainHandItem();
			if (book.is(Items.WRITTEN_BOOK)) {
				mc.setScreen(new BookViewScreen(BookViewScreen.BookAccess.fromItem(book)));
			}
		}
		if (tick == 145) {
			ScreenshotHarness.shot(mc, "01_list_book");
			if (mc.screen instanceof BookViewScreen view) {
				view.setPage(1);
			}
		}
		if (tick == 160) {
			ScreenshotHarness.shot(mc, "02_list_page");
			WrittenBookContent content = mc.player.getMainHandItem().get(DataComponents.WRITTEN_BOOK_CONTENT);
			boolean listed = content != null && content.pages().size() > 1 && content.pages().get(0).raw().getString().contains("%")
				&& content.pages().get(1).raw().getString().contains("×");
			Showcase.check(mc.screen instanceof BookViewScreen && listed && problem.isEmpty(),
				problem.isEmpty() ? "a Book and Quill used on the builder became the rest of his build's list, with what's in the chest taken off" : problem);
			mc.setScreen(null);
			mc.gui.getChat().clearMessages(false);
		}
		if (tick == 170) {
			server.execute(() -> rightClick(server, true));
		}
		if (tick == 185) {
			mc.setScreen(new ChatScreen(""));
		}
		if (tick == 205) {
			ScreenshotHarness.shot(mc, "03_chat_list");
			Showcase.check(chatLines > 4, "a sneak-right-click with an empty hand put the whole list in chat: " + chatLines + " lines");
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
		level.setBlockAndUpdate(BENCH, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(CHEST) instanceof Container chest) {
			chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 64));
			chest.setItem(1, new ItemStack(Items.COBBLESTONE, 40));
			chest.setItem(2, new ItemStack(Items.TORCH, 8));
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
		v.setNoAi(true); // he stands for the picture: the list is of the build as it is
		Builders.employ(level, v, BENCH);
		BuildSite site = Builders.start(level, v, player, StarterBlueprints.STARTER_COTTAGE.id(), placement);
		BuildPlan plan = site.plan(level);
		if (plan == null) {
			problem = "no plan for the cottage";
			return;
		}
		// Halfway: the foundation and the lower half of the walls stand.
		for (BuildPlan.Step step : plan.steps(BuildPlan.Stage.FOUNDATION)) {
			level.setBlock(step.pos(), step.state(), 2 | 16);
		}
		List<BuildPlan.Step> walls = plan.steps(BuildPlan.Stage.STRUCTURE);
		for (BuildPlan.Step step : walls.subList(0, walls.size() / 2)) {
			level.setBlock(step.pos(), step.state(), 2 | 16);
		}
		for (int i = 0; i < 4 && site.stage() != BuildPlan.Stage.STRUCTURE; i++) {
			site.finishList(); // clearing and the foundation done: the builder is on the walls
		}
		builder = v;
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, 0.5, -60, 0.5, 0, 15);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WRITABLE_BOOK));
	}

	/** The player's right-click on the builder, as the client sends it. */
	private void rightClick(MinecraftServer server, boolean sneaking) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		if (builder == null) {
			return;
		}
		if (sneaking) {
			ItemStack held = player.getMainHandItem();
			player.getInventory().add(held.copy());
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			BuildSite site = Builders.activeSite(server.overworld(), builder);
			chatLines = site == null ? 0 : SiteList.chat(server.overworld(), site, builder).getString().split("\n").length;
		}
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(builder, sneaking, InteractionHand.MAIN_HAND,
			new Vec3(0, builder.getBbHeight() / 2, 0)));
		player.setShiftKeyDown(false);
	}
}

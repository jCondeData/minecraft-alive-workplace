package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.client.BlueprintTableScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.table.PreviewView;
import io.github.jcondedata.aliveworkplace.table.SafeRender;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=table_preview_safe (QA for B1020, a client crash on the live server): another mod's furniture must never crash
 * the game when we draw it. Cozy Home's tinted chairs and sofas refuse a null level in their color provider; this scene
 * gives two of the Starter Cottage's own blocks providers like theirs (spruce stairs, brown carpet: throw on a null level
 * or position, otherwise read the block at that position) and two others a provider that always throws (spruce fence for
 * the held blueprint's ghosts, glass pane for the Table). Then, through the player's own entry points: holding a placed
 * cottage blueprint draws its ghosts, and the Blueprint Table's library draws the cottage in 3D. Checks: the game keeps
 * running, the Cozy-like providers are only ever asked with a level that holds that very block, the always-throwing
 * ones are logged (drawn untinted) and the Cozy-like ones never are, and the preview still has the cottage's blocks.
 */
final class TablePreviewSafeScene {
	private static final BlockPos TABLE = new BlockPos(0, -60, -3);
	private static final BlockPos HOUSE = new BlockPos(-4, -60, 8);
	private static final ResourceLocation COTTAGE = StarterBlueprints.STARTER_COTTAGE.id();

	private final AtomicInteger cozyAsked = new AtomicInteger();
	private final AtomicInteger cozyNullAsked = new AtomicInteger();
	private final AtomicInteger cozyWrongBlock = new AtomicInteger();
	private final AtomicInteger cozyAskedInGhosts = new AtomicInteger();
	private final AtomicInteger cozyAskedInPreview = new AtomicInteger();
	private int tick;

	/** Like Cozy Home's ModRenderLayers provider: a null level or position is a NullPointerException. */
	private BlockColor cozyLike() {
		return (state, view, pos, tintIndex) -> {
			if (view == null || pos == null) {
				cozyNullAsked.incrementAndGet();
				throw new NullPointerException("view is null");
			}
			cozyAsked.incrementAndGet();
			if (!(view instanceof PreviewView)) {
				// the held blueprint's ghosts, asked with the real world (a frame or two may still draw them after
				// the plan leaves the hand, so these are counted apart from the Table's)
				cozyAskedInGhosts.incrementAndGet();
			} else {
				cozyAskedInPreview.incrementAndGet();
				if (view.getBlockState(pos).getBlock() != state.getBlock()) {
					// the Table's preview: its little level should hold this very block where the provider is asked
					cozyWrongBlock.incrementAndGet();
				}
			}
			return 0xA0602D;
		};
	}

	private static BlockColor alwaysThrows(String what) {
		return (state, view, pos, tintIndex) -> {
			throw new IllegalStateException(what + " exploded");
		};
	}

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(4);
			mc.options.hideGui = true;
			mc.getBlockColors().register(cozyLike(), Blocks.SPRUCE_STAIRS, Blocks.BROWN_CARPET);
			mc.getBlockColors().register(alwaysThrows("the ghost's fence color"), Blocks.SPRUCE_FENCE);
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(6000);
				level.setBlockAndUpdate(TABLE, ModBlocks.BLUEPRINT_TABLE.defaultBlockState());
				ServerPlayer player = player(server);
				player.setGameMode(GameType.CREATIVE);
				player.getInventory().clearContent();
				player.getInventory().selected = 0;
				// a placed cottage blueprint in hand: its ghosts are drawn in the world (BlueprintPreviewRenderer)
				ItemStack plan = BlueprintItem.create(COTTAGE, null);
				BlueprintData data = BlueprintItem.data(plan).orElseThrow();
				plan.set(ModComponents.BLUEPRINT, data.withPlacement(Optional.of(
					new BlueprintData.Placement(level.dimension().location(), HOUSE, Rotation.NONE, Mirror.NONE))));
				player.getInventory().setItem(0, plan);
				ScreenshotHarness.hoverLookingAt(player, Vec3.atCenterOf(HOUSE).add(4, 6, -10), Vec3.atCenterOf(HOUSE).add(4, 2, 4));
			});
		}
		if (tick == 120) {
			Showcase.check(cozyAskedInGhosts.get() > 0, "the held cottage's ghosts asked the Cozy-like provider for its color with a level ("
				+ cozyAskedInGhosts.get() + " times)");
			Showcase.check(SafeRender.warned(Blocks.SPRUCE_FENCE),
				"the ghosts' fence, whose color provider always throws, was logged and drawn untinted, and the game kept running");
			ScreenshotHarness.shot(mc, "01_ghosts_with_a_throwing_provider");
			mc.getBlockColors().register(alwaysThrows("the preview's glass color"), Blocks.GLASS_PANE);
			server.execute(() -> {
				ServerPlayer player = player(server);
				player.getInventory().clearContent();
				player.teleportTo(server.overworld(), 0.5, -60, 0.5, 180, 30);
			});
		}
		if (tick == 160) {
			server.execute(() -> TableServer.open(player(server), TABLE));
		}
		BlueprintTableScreen screen = mc.screen instanceof BlueprintTableScreen s ? s : null;
		if (tick == 190 && screen != null) {
			screen.select(COTTAGE);
		}
		if (tick == 250) {
			Showcase.check(screen != null, "the Blueprint Table screen opened and stayed open");
			Showcase.check(screen != null && screen.previewCells() > 0, "the cottage's 3D preview arrived ("
				+ (screen == null ? -1 : screen.previewCells()) + " blocks)");
			int inPreview = cozyAskedInPreview.get();
			Showcase.check(inPreview > 0 && cozyWrongBlock.get() == 0,
				"the preview asked the Cozy-like provider with its own level, holding that very block (" + inPreview + " asks, "
					+ cozyWrongBlock.get() + " with another block there)");
			Showcase.check(cozyNullAsked.get() == 0 && !SafeRender.warned(Blocks.SPRUCE_STAIRS) && !SafeRender.warned(Blocks.BROWN_CARPET),
				"the Cozy-like provider was never asked with a null level (" + cozyNullAsked.get() + " times), so the stairs and carpet keep their tint");
			Showcase.check(SafeRender.warned(Blocks.GLASS_PANE), "the preview's glass, whose provider always throws, was logged and drawn untinted");
			ScreenshotHarness.shot(mc, "02_preview_with_throwing_providers");
		}
		if (tick > 255 && tick <= 270 && screen != null) {
			screen.dragPreview(-8, 3); // turned round: every block drawn again from other sides
		}
		if (tick == 280) {
			Showcase.check(screen != null && screen.previewCells() > 0, "dragging the preview round kept it drawing");
			ScreenshotHarness.shot(mc, "03_preview_dragged");
		}
		if (tick == 300) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}
}

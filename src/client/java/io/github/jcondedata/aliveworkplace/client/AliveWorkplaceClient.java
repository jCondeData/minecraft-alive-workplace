package io.github.jcondedata.aliveworkplace.client;

import io.github.jcondedata.aliveworkplace.table.TablePayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

/** Client entry point: the Blueprint Table screen and its packets. */
public class AliveWorkplaceClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlueprintPreviewRenderer.init();
		BuilderStatusRenderer.init();
		BlueprintTooltip.init();
		StationTooltip.init();
		io.github.jcondedata.aliveworkplace.guide.GuideBookItem.open = () -> Minecraft.getInstance()
			.setScreen(new io.github.jcondedata.aliveworkplace.client.guide.GuideScreen());
		net.minecraft.client.gui.screens.MenuScreens.register(io.github.jcondedata.aliveworkplace.registry.ModBlocks.MAILBOX_MENU, MailboxScreen::new);
		net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(io.github.jcondedata.aliveworkplace.registry.ModEntities.FISHING_BOBBER,
			BobberRenderer::new);
		// Guards' armor, drawn on the villager model.
		net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof net.minecraft.client.renderer.entity.VillagerRenderer villagers) {
				helper.register(new GuardArmorLayer(villagers, context.getModelSet()));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(TablePayloads.Open.TYPE, (payload, context) -> {
			Minecraft mc = context.client();
			if (mc.screen instanceof BlueprintTableScreen screen && screen.table().equals(payload.table())) {
				screen.refresh(payload);
			} else {
				mc.setScreen(new BlueprintTableScreen(payload));
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(TablePayloads.Details.TYPE, (payload, context) -> {
			if (context.client().screen instanceof BlueprintTableScreen screen) {
				screen.setDetails(payload);
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(TablePayloads.UploadResult.TYPE, (payload, context) -> {
			if (context.client().screen instanceof BlueprintTableScreen screen) {
				screen.onUploadResult(payload);
			} else if (context.client().player != null) {
				context.client().player.displayClientMessage(payload.message(), false);
			}
		});
	}
}

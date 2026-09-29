package io.github.jcondedata.aliveworkplace;

import io.github.jcondedata.aliveworkplace.build.BuilderEvents;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.command.WorkplaceCommand;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alive Workplace — villagers that do real jobs. Entry point: registers content and hooks.
 * See ROADMAP.md for what is planned and CLAUDE.md for how the codebase is organised.
 */
public class AliveWorkplace implements ModInitializer {
	public static final String MOD_ID = "aliveworkplace";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		WorkplaceConfig.loadAndApply(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir());
		ModComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModVillagers.init();
		io.github.jcondedata.aliveworkplace.registry.ModEntities.init();
		ModAttachments.init();
		ModGameRules.init();
		ModTrades.init();

		BuilderEvents.init();
		io.github.jcondedata.aliveworkplace.shop.ShopLedger.init();
		TableServer.init();
		PreviewNetworking.init();
		BuilderStatusSync.init();
		io.github.jcondedata.aliveworkplace.world.VillageHouses.init();
		io.github.jcondedata.aliveworkplace.farm.Fields.init();
		io.github.jcondedata.aliveworkplace.fish.Fishers.init();
		io.github.jcondedata.aliveworkplace.mail.Mail.init();
		io.github.jcondedata.aliveworkplace.work.KeepLoaded.init();
		io.github.jcondedata.aliveworkplace.guard.GuardPartners.init();
		io.github.jcondedata.aliveworkplace.guard.Escorts.init();
		io.github.jcondedata.aliveworkplace.hall.Festivals.init();
		io.github.jcondedata.aliveworkplace.people.Chatter.init();
		io.github.jcondedata.aliveworkplace.ranch.PokemonChores.init();
		io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.init();
		if (io.github.jcondedata.aliveworkplace.trainer.Trainers.COBBLEMON) {
			io.github.jcondedata.aliveworkplace.compat.cobblemon.CobblemonTrainers.init();
		}
		io.github.jcondedata.aliveworkplace.build.MaterialFamilies.init();
		BlueprintOutline.init();
		WorkplaceCommand.init();
		io.github.jcondedata.aliveworkplace.command.Benchmark.init();
		LOG.info("Alive Workplace ready — go hire a builder.");
	}
}

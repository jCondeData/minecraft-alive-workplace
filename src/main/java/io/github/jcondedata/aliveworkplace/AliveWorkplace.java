package io.github.jcondedata.aliveworkplace;

import io.github.jcondedata.aliveworkplace.build.BuilderEvents;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.PreviewNetworking;
import io.github.jcondedata.aliveworkplace.build.BuilderStatusSync;
import io.github.jcondedata.aliveworkplace.command.WorkplaceCommand;
import io.github.jcondedata.aliveworkplace.platform.Platform;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModTrades;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.table.TableServer;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Alive Workplace — villagers that do real jobs. Registers content and hooks; the loader's entrypoint
 * ({@code platform/fabric/AliveWorkplaceFabric}) calls {@link #init}. Nothing here may touch the loader or another mod
 * directly: that goes through {@link Platform} and {@code compat/}.
 * See ROADMAP.md for what is planned and CLAUDE.md for how the codebase is organised.
 */
public final class AliveWorkplace {
	public static final String MOD_ID = "aliveworkplace";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	public static ResourceLocation id(String path) {
		return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
	}

	/** Starts the mod; {@code integrations} turns on other mods' integrations (only those installed). */
	public static void init(Runnable integrations) {
		WorkplaceConfig.loadAndApply(Platform.get().configDir());
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
		io.github.jcondedata.aliveworkplace.travel.FerryRides.init();
		io.github.jcondedata.aliveworkplace.guard.Cavalry.init();
		io.github.jcondedata.aliveworkplace.mail.Mail.init();
		io.github.jcondedata.aliveworkplace.work.KeepLoaded.init();
		io.github.jcondedata.aliveworkplace.guard.GuardPartners.init();
		io.github.jcondedata.aliveworkplace.guard.Escorts.init();
		io.github.jcondedata.aliveworkplace.hall.Festivals.init();
		io.github.jcondedata.aliveworkplace.hall.Seasons.init();
		io.github.jcondedata.aliveworkplace.people.Chatter.init();
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.init();
		io.github.jcondedata.aliveworkplace.city.CityPlans.init();
		io.github.jcondedata.aliveworkplace.ranch.PokemonChores.init();
		io.github.jcondedata.aliveworkplace.legend.Legends.init();
		io.github.jcondedata.aliveworkplace.work.PartnerShows.init();
		io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles.init();
		integrations.run();
		io.github.jcondedata.aliveworkplace.build.MaterialFamilies.init();
		io.github.jcondedata.aliveworkplace.build.StallWatch.init();
		BlueprintOutline.init();
		WorkplaceCommand.init();
		io.github.jcondedata.aliveworkplace.command.Benchmark.init();
		io.github.jcondedata.aliveworkplace.command.Soak.init();
		LOG.info("Alive Workplace ready — go hire a builder.");
	}

	private AliveWorkplace() {
	}
}

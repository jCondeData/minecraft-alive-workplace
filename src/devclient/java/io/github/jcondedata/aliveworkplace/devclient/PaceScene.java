package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.build.BuilderLevels;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Traits;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.Partners;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=pace (ROADMAP 30.2, with Cobblemon): a builder past the speed cap. Three Pokémon partners, a village kept 100%,
 * Swift Hands III, Diligent and a happy mood would make them well over twice as fast; the status (sneak-right-click)
 * says "100% faster: at the cap" with the sources behind it. Then the builder falls ill: half that pace, held back.
 */
final class PaceScene {
	private static final BlockPos BENCH = new BlockPos(0, -60, 0);
	private static final BlockPos HALL = new BlockPos(-6, -60, -4);
	private static final BlockPos PASTURE = new BlockPos(6, -60, -3);

	private int tick;
	private volatile Villager builder;
	private volatile String capped = "";
	private volatile String ill = "";

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
			server.execute(() -> {
				keepWell(server.overworld());
				capped = BuilderLevels.describe(builder).getString();
				mc.execute(() -> mc.gui.getChat().clearMessages(false));
				Builders.sendStatus(server.getPlayerList().getPlayers().get(0), builder);
			});
		}
		if (tick == 130) {
			mc.setScreen(new ChatScreen(""));
		}
		if (tick == 150) {
			ScreenshotHarness.shot(mc, "01_pace_capped");
			mc.setScreen(null);
			mc.gui.getChat().clearMessages(false);
			server.execute(() -> {
				ModAttachments.ILL_SINCE.set(builder, server.overworld().getGameTime());
				keepWell(server.overworld());
				ill = BuilderLevels.describe(builder).getString();
				Builders.sendStatus(server.getPlayerList().getPlayers().get(0), builder);
			});
		}
		if (tick == 165) {
			mc.setScreen(new ChatScreen(""));
		}
		if (tick == 185) {
			ScreenshotHarness.shot(mc, "02_pace_ill");
			Showcase.check(capped.contains("100% faster: at the cap"), "the capped builder's status line says \"at the cap\": " + capped);
			Showcase.check(ill.contains("at the cap") && ill.contains("held back by being ill"), "ill, the same builder is held back: " + ill);
			mc.setScreen(null);
			mc.stop();
		}
	}

	/** The village kept 100% with Swift Hands III and Kinship II, the builder fed, every cache asked again. */
	private void keepWell(ServerLevel level) {
		if (level.getBlockEntity(HALL) instanceof VillageHallBlockEntity hall) {
			hall.setResearch(new Research.State(Map.of("swift_hands", 3, "kinship", 2), Optional.empty(), 0, false));
			hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 1f));
		}
		ModAttachments.LAST_MEAL.set(builder, level.getGameTime());
		VillageNeeds.forget();
		Research.forget();
		Moods.forget();
		Partners.forget(builder);
	}

	private void setUp(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2500);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		level.setBlockAndUpdate(BENCH, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		Villager v = EntityType.VILLAGER.create(level);
		v.setUUID(diligent());
		v.moveTo(BENCH.getX() + 0.5, BENCH.getY(), BENCH.getZ() + 1.5, 180, 0);
		level.addFreshEntity(v);
		Jobs.employ(level, v, BENCH, ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		v.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.HOME, GlobalPos.of(level.dimension(), BENCH.west(2)));
		v.setNoAi(true); // stands by the bench for the pictures
		v.setYRot(160);
		v.setYHeadRot(160);
		builder = v;
		level.setBlockAndUpdate(PASTURE, ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
			"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.BOTTOM));
		level.setBlockAndUpdate(PASTURE.above(), ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
			"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.TOP));
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		for (String species : List.of("machop level=30", "geodude level=30", "onix level=30")) {
			var pokemon = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(species, " ", "=").create();
			com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
			if (level.getBlockEntity(PASTURE) instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity pen) {
				pen.tether(player, pokemon, Direction.WEST);
			}
		}
		ScreenshotHarness.hover(player, new Vec3(2.5, -57.5, 6.5), 160, 20);
	}

	/** A villager id that is Diligent and not Lazy (traits come from the id). */
	private static UUID diligent() {
		RandomSource random = RandomSource.create(302);
		for (int i = 0; i < 100000; i++) {
			UUID id = new UUID(random.nextLong(), random.nextLong());
			if (Traits.of(id).contains(Traits.Trait.DILIGENT)) {
				return id;
			}
		}
		return UUID.randomUUID();
	}
}

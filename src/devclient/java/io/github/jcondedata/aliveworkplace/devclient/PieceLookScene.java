package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.hall.PieceLooks;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.world.VillageHouses;
import io.github.jcondedata.aliveworkplace.world.VillagePieces;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=piece_look (ROADMAP 23.10a): a plains Guard House as a village grows it, the player's Village Hall and a Builder
 * at his bench. The hall's Builds button opens House looks with the house on it; the house's view shows the five styles'
 * outsides; choosing the desert one sends the builder, who rebuilds the outside over the house, its room left as it was.
 */
final class PieceLookScene {
	private static final BlockPos HALL = new BlockPos(0, -60, 0);
	private static final BlockPos HOUSE = HALL.offset(6, 0, -12);
	private int tick;
	private int done = -1;
	private volatile Villager builder;
	private volatile BlueprintData.Placement placement;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 50) {
			server.execute(() -> {
				ServerPlayer player = player(server);
				ScreenshotHarness.hoverLookingAt(player, center().add(-9, 6, 11), center());
			});
		}
		if (tick == 80) {
			ScreenshotHarness.shot(mc, "01_plains_house");
			server.execute(() -> {
				ServerPlayer player = player(server);
				VillageHallScreen.open(player, HALL);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(VillageHallScreen.BUILDS, player);
					Showcase.check(menu.icon(VillageHallScreen.FIRST_ROW).getHoverName().getString().equals("Guard House"),
						"the hall's Builds button opened House looks with the Guard House on it");
				}
			});
		}
		if (tick == 95) {
			ScreenshotHarness.pointAt(mc, VillageHallScreen.FIRST_ROW);
		}
		if (tick == 110) {
			ScreenshotHarness.shot(mc, "02_house_looks");
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(VillageHallScreen.FIRST_ROW, player);
				}
			});
		}
		if (tick == 125) {
			ScreenshotHarness.pointAt(mc, PieceLooks.FIRST_LOOK + VillageHouses.STYLES.indexOf("desert"));
		}
		if (tick == 140) {
			ScreenshotHarness.shot(mc, "03_look_picker");
			server.execute(() -> {
				ServerPlayer player = player(server);
				if (player.containerMenu instanceof ChoiceMenu menu) {
					menu.press(PieceLooks.FIRST_LOOK + VillageHouses.STYLES.indexOf("desert"), player);
				}
				BuildSite site = PieceLooks.rebuild(server.overworld(), piece(server));
				Showcase.check(site != null && builder.getUUID().equals(site.builder()), "choosing the desert outside sent the village's builder");
				player.closeContainer();
				ScreenshotHarness.hoverLookingAt(player, center().add(-9, 6, 11), center());
			});
		}
		if (tick > 160 && done < 0 && tick % 20 == 0) {
			server.execute(() -> {
				if (done < 0 && Builders.activeSite(server.overworld(), builder) == null) {
					done = tick;
				}
			});
		}
		if (done > 0 && tick == done + 40 || done < 0 && tick == 2600) {
			ScreenshotHarness.shot(mc, "04_new_outside");
			server.execute(() -> Showcase.check(VillagePieces.lookAt(server.overworld(), placement).equals(Optional.of("desert")),
				"the Guard House has the desert outside, its room as it was"));
		}
		if (done > 0 && tick == done + 60 || tick == 2620) {
			mc.stop();
		}
	}

	private Vec3 center() {
		return Vec3.atCenterOf(HOUSE.offset(4, 3, 5));
	}

	private VillagePieces.Piece piece(MinecraftServer server) {
		return PieceLooks.pieces(server.overworld(), HALL).stream().filter(p -> p.placement().origin().equals(HOUSE)).findFirst()
			.orElse(new VillagePieces.Piece("guard_house", "plains", "plains", placement));
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** The player's hall, a plains Guard House placed as a village places it, and a Builder at his bench by the hall. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, server); // the scene shows the look, not a supply run
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, server);
		level.setDayTime(2000);
		ServerPlayer player = player(server);
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState());
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		placement = new BlueprintData.Placement(level.dimension().location(), HOUSE, Rotation.NONE, Mirror.NONE);
		level.getStructureManager().get(VillagePieces.template("plains", "guard_house")).orElseThrow()
			.placeInWorld(level, HOUSE, HOUSE, new StructurePlaceSettings().setIgnoreEntities(true).addProcessor(JigsawReplacementProcessor.INSTANCE),
				RandomSource.create(7), 2);
		BlockPos bench = HALL.offset(-3, 0, -6);
		level.setBlockAndUpdate(bench, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		builder = EntityType.VILLAGER.spawn(level, bench.south(), MobSpawnType.COMMAND);
		Builders.employ(level, builder, bench);
		ScreenshotHarness.hover(player, Vec3.atCenterOf(HALL).add(-3, 2, 4), 200, 20);
	}
}

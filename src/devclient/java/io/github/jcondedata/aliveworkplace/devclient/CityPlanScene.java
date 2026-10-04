package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.CityPlans;
import io.github.jcondedata.aliveworkplace.client.CityPlanScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import java.util.BitSet;
import java.util.List;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=city_plan (ROADMAP 27.3): a real plains village with a Village Hall in it; the City Plan opened on it with Homes,
 * Workshops and Gardens zones in three styles and a Keep Clear patch; stills at GUI scales 2 and 4, each checked for
 * clipped or overlapping text as 24.4 checks the other screens.
 */
final class CityPlanScene {
	private int tick;
	private volatile BlockPos hall;
	private volatile List<String> styles = List.of();

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
				level.setDayTime(3000);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -80 -80 80 80");
			});
		}
		if (tick == 380) {
			server.execute(() -> stage(server));
		}
		if (tick == 440) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				if (hall != null && server.overworld().getBlockEntity(hall) instanceof VillageHallBlockEntity entity) {
					CityPlans.open(player, entity);
				}
			});
		}
		if (tick == 480) {
			Showcase.check(mc.screen instanceof CityPlanScreen, "the City Plan screen opened on the village's hall");
			checkLayout(mc, "01_city_plan_start");
			ScreenshotHarness.shot(mc, "01_city_plan_start");
			server.execute(() -> paintTheRest(server));
		}
		if (tick == 520) {
			checkLayout(mc, "02_city_plan_scale2");
			ScreenshotHarness.shot(mc, "02_city_plan_scale2");
			// GUI scale 4 needs a window at least 1280x960 (as the hall scene does): resize first, then set the scale.
			org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
		}
		if (tick == 530) {
			mc.options.guiScale().set(4);
			mc.resizeDisplay();
		}
		if (tick == 550) {
			Showcase.check(mc.getWindow().getGuiScale() == 4, "the plan screen at GUI scale 4 (now " + mc.getWindow().getGuiScale() + ")");
			checkLayout(mc, "03_city_plan_scale4");
			ScreenshotHarness.shot(mc, "03_city_plan_scale4");
			server.execute(() -> {
				VillageHallBlockEntity entity = (VillageHallBlockEntity) server.overworld().getBlockEntity(hall);
				CityPlan plan = entity.plan();
				long painted = plan.zones().stream().filter(z -> !z.cells().isEmpty()).count();
				long styled = plan.zones().stream().map(CityPlan.Zone::style).filter(s -> !s.isEmpty()).distinct().count();
				Showcase.check(painted >= 4 && styled >= Math.min(3, styles.size()),
					"Homes, Workshops and Gardens painted in three styles, and a Keep Clear patch (" + painted + " zones painted, " + styled + " styles)");
			});
			if (mc.screen instanceof CityPlanScreen screen) {
				server.execute(() -> {
					VillageHallBlockEntity entity = (VillageHallBlockEntity) server.overworld().getBlockEntity(hall);
					Showcase.check(entity.plan().equals(screen.plan()), "the screen shows the plan saved on the hall");
				});
			}
		}
		if (tick == 580) {
			mc.stop();
		}
	}

	/** The village, its hall (bound to a City Plan in the player's hand) and the first zone painted. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
			"place structure minecraft:village_plains 0 -60 0");
		// the hall on open ground (grass or a path) nearest the village's middle, not on a roof
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
		hall = spot != null ? spot : new BlockPos(4, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 4, 4), 4);
		int y = hall.getY();
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		player.setGameMode(GameType.CREATIVE);
		ScreenshotHarness.hover(player, new Vec3(hall.getX() + 0.5, y + 12, hall.getZ() + 10.5), 180, 40);
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, player, plan, hall);
		player.setItemInHand(InteractionHand.MAIN_HAND, plan);
		styles = BlueprintStyles.all().stream().map(BlueprintStyles.Style::name).limit(3).toList();
		String homes = styles.isEmpty() ? "" : styles.get(0);
		CityPlans.apply(player, CityPlans.Edit.addZone(hall, "homes", "Homes", homes));
		CityPlans.apply(player, CityPlans.Edit.paint(hall, 0, rect(-6, -6, -1, 2)));
	}

	/** Workshops and Gardens in the two other styles, and a Keep Clear patch round the well. */
	private void paintTheRest(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		String workshops = styles.size() > 1 ? styles.get(1) : "";
		String gardens = styles.size() > 2 ? styles.get(2) : "";
		CityPlans.apply(player, CityPlans.Edit.addZone(hall, "workshops", "Smithy Row", workshops));
		CityPlans.apply(player, CityPlans.Edit.paint(hall, 1, rect(1, -6, 6, -1)));
		CityPlans.apply(player, CityPlans.Edit.addZone(hall, "gardens", "Gardens", gardens));
		CityPlans.apply(player, CityPlans.Edit.paint(hall, 2, rect(1, 1, 6, 5)));
		CityPlans.apply(player, CityPlans.Edit.addZone(hall, "keep_clear", "Old Well", ""));
		CityPlans.apply(player, CityPlans.Edit.paint(hall, 3, rect(-1, 3, 0, 5)));
	}

	/** The cells from {@code x0},{@code z0} to {@code x1},{@code z1}, counted in cells from the hall's own. */
	private BitSet rect(int x0, int z0, int x1, int z1) {
		int size = CityPlan.cellSize();
		BitSet out = new BitSet();
		for (int z = z0; z <= z1; z++) {
			for (int x = x0; x <= x1; x++) {
				int cell = CityPlan.cellAt(hall, hall.offset(x * size, 0, z * size));
				if (cell >= 0) {
					out.set(cell);
				}
			}
		}
		return out;
	}

	/** ROADMAP 24.4: no clipped or overlapping text on the screen at this GUI scale. */
	private static void checkLayout(Minecraft mc, String shot) {
		if (mc.screen instanceof CityPlanScreen screen) {
			List<String> problems = screen.layoutProblems();
			Showcase.check(problems.isEmpty(), shot + ": no clipped or overlapping text at GUI scale " + (int) mc.getWindow().getGuiScale()
				+ (problems.isEmpty() ? "" : " " + problems));
		}
	}
}

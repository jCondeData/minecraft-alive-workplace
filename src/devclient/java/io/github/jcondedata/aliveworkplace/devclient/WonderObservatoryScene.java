package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.HallPages;
import io.github.jcondedata.aliveworkplace.hall.Seasons;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.wonder.ObservatoryPowers;
import io.github.jcondedata.aliveworkplace.wonder.SkyChart;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=wonder_observatory (ROADMAP 35.18): the Observatory's time-lapse and its festival's fireworks, then the Sky
 * Chart in hand on the terrace (the hall's search run through every kind of structure in the land round the village),
 * then the hall's calendar page with the Coming days: a festival, a wedding and a caravan staged, the market day, the
 * full moon and the night's raid risk. Its checks: the powers are on, the chart has a marker for each structure found,
 * and the Coming days list the festival, the wedding and the caravan.
 */
final class WonderObservatoryScene {
	static WonderTimelapse create() {
		return new WonderTimelapse(AliveWorkplace.id("observatory"), "wonder_observatory").finale(new Chart()).then(new Coming());
	}

	/** The Sky Chart in hand, looking down at it on the terrace. */
	private static final class Chart implements WonderTimelapse.Finale {
		private volatile ItemStack chart = ItemStack.EMPTY;
		private volatile int found;
		private volatile BlockPos hall;

		@Override
		public String name() {
			return "sky_chart";
		}

		@Override
		public void ready(ServerLevel level, ServerPlayer player, BlueprintData.Placement placement, BlockPos hall) {
			this.hall = hall;
			if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
				return;
			}
			// the week's search, every kind at once (on the server it takes an in-game hour a kind). The scene's superflat
			// world is made with structures off, so it has none to find: the land round the village is the one below,
			// standing in for the world's structure check the way the Observatory's GameTests do.
			ObservatoryPowers.finder = new Land(LAND.stream().map(s -> new SkyChart.Found(s.kind(), s.structure(), hall.offset(s.pos()))).toList());
			ObservatoryPowers.kindsForTest = LAND.stream().map(SkyChart.Found::kind).distinct().toList();
			try {
				long t = 0;
				for (int i = 0; i < 40_000 && !entity.skyChart().done(); i++, t += SkyChart.STEP_TICKS) {
					ObservatoryPowers.tick(level, hall, entity, t, true);
				}
			} finally {
				ObservatoryPowers.finder = SkyChart.VANILLA;
				ObservatoryPowers.kindsForTest = null;
			}
			found = entity.skyChart().found().size();
			chart = ObservatoryPowers.chart(level, hall);
			player.setItemInHand(InteractionHand.MAIN_HAND, chart.copy());
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			Vec3 stand = Vec3.atBottomCenterOf(placement.origin()).add(4, 1.6, 4);
			ScreenshotHarness.hoverLookingAt(player, stand, stand.add(0.3, -1.4, 1.0));
		}

		@Override
		public int point() {
			return 0;
		}

		@Override
		public void check(ServerLevel level, ServerPlayer player) {
			Showcase.check(hall != null && ObservatoryPowers.charts(level, hall) && ObservatoryPowers.foretells(level, hall), "the finished Observatory's powers are on");
			MapDecorations marks = chart.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
			Showcase.check(found > 0 && marks.decorations().size() >= found, "the Sky Chart marks the " + found + " structures found (" + marks.decorations().size() + " markers)");
		}
	}

	private static ResourceLocation mc(String path) {
		return ResourceLocation.withDefaultNamespace(path);
	}

	/** The land round the village (each start's offset from the hall), as a world with structures on would have it. */
	private static final List<SkyChart.Found> LAND = List.of(
		new SkyChart.Found(mc("villages"), mc("village_plains"), new BlockPos(820, 0, -610)),
		new SkyChart.Found(mc("villages"), mc("village_taiga"), new BlockPos(-1340, 0, -1720)),
		new SkyChart.Found(mc("villages"), mc("village_desert"), new BlockPos(2240, 0, 1450)),
		new SkyChart.Found(mc("villages"), mc("village_savanna"), new BlockPos(-2050, 0, 980)),
		new SkyChart.Found(mc("pillager_outposts"), mc("pillager_outpost"), new BlockPos(1180, 0, -1890)),
		new SkyChart.Found(mc("desert_pyramids"), mc("desert_pyramid"), new BlockPos(1890, 0, 2210)),
		new SkyChart.Found(mc("jungle_temples"), mc("jungle_pyramid"), new BlockPos(-2610, 0, 2380)),
		new SkyChart.Found(mc("swamp_huts"), mc("swamp_hut"), new BlockPos(-770, 0, 1540)),
		new SkyChart.Found(mc("igloos"), mc("igloo"), new BlockPos(-1620, 0, -2540)),
		new SkyChart.Found(mc("ocean_monuments"), mc("monument"), new BlockPos(2570, 0, -1150)),
		new SkyChart.Found(mc("woodland_mansions"), mc("mansion"), new BlockPos(-2830, 0, -930)),
		new SkyChart.Found(mc("shipwrecks"), mc("shipwreck"), new BlockPos(2010, 0, -380)),
		new SkyChart.Found(mc("ruined_portals"), mc("ruined_portal"), new BlockPos(-460, 0, -1050)),
		new SkyChart.Found(mc("strongholds"), mc("stronghold"), new BlockPos(1500, -40, 2700)));

	/** A stand-in for the world's structure check: these starts, each of its kind, reported only for the square asked about. */
	private record Land(List<SkyChart.Found> starts) implements SkyChart.Finder {
		@Override
		public void find(ServerLevel level, ResourceLocation kind, ChunkPos min, int chunks, BiConsumer<ResourceLocation, BlockPos> found) {
			for (SkyChart.Found f : starts) {
				int cx = f.pos().getX() >> 4;
				int cz = f.pos().getZ() >> 4;
				if (f.kind().equals(kind) && cx >= min.x && cz >= min.z && cx < min.x + chunks && cz < min.z + chunks) {
					found.accept(f.structure(), f.pos());
				}
			}
		}
	}

	/** The hall's calendar page with the Coming days, a festival, a wedding and a caravan staged. */
	private static final class Coming implements WonderTimelapse.Finale {
		private volatile BlockPos hall;

		@Override
		public String name() {
			return "coming_days";
		}

		@Override
		public void ready(ServerLevel level, ServerPlayer player, BlueprintData.Placement placement, BlockPos hall) {
			this.hall = hall;
			player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			if (!(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity)) {
				return;
			}
			long today = Chronicle.day(level);
			entity.setFestivalDay(today + 3);
			Villager a = couple(level, hall.offset(3, 1, 3), "Maren");
			Villager b = couple(level, hall.offset(4, 1, 3), "Tobin");
			ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), today - 1, false));
			ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), today - 1, false));
			Caravans.sendGranary(level, hall.offset(1200, 0, -400), hall, List.of(new ItemStack(Items.BREAD, 8)));
			VillageHallScreen.openRemote(player, hall);
			if (player.containerMenu instanceof ChoiceMenu menu) {
				HallPages.all().stream().filter(p -> p.id().equals(Seasons.PAGE)).findFirst()
					.ifPresent(page -> VillageHallScreen.renderPage(menu, level, hall, page, player));
				menu.broadcastChanges();
			}
		}

		private static Villager couple(ServerLevel level, BlockPos at, String name) {
			Villager v = EntityType.VILLAGER.create(level);
			v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
			v.setCustomName(Component.literal(name));
			level.addFreshEntity(v);
			return v;
		}

		@Override
		public int point() {
			return ObservatoryPowers.FIRST + 1;
		}

		@Override
		public void check(ServerLevel level, ServerPlayer player) {
			List<ObservatoryPowers.Coming> coming = hall == null ? List.of() : ObservatoryPowers.comingDays(level, hall);
			String all = String.join(", ", coming.stream().map(c -> c.what().getString()).toList());
			Showcase.check(all.toLowerCase(java.util.Locale.ROOT).contains("festival") && all.contains("Maren") && all.toLowerCase(java.util.Locale.ROOT).contains("caravan"), "the Coming days list the festival, the wedding and the caravan: " + all);
			Showcase.check(player.containerMenu instanceof ChoiceMenu menu && !menu.icon(ObservatoryPowers.HEADER).isEmpty(), "the calendar page shows the Coming days");
		}
	}

	private WonderObservatoryScene() {
	}
}

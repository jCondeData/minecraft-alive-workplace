package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.VillageBannerItem;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=village_banner (ROADMAP 30.13): the hall's owner gives the village its colours with a Village Banner; three
 * builders finish a street of stone houses and each hangs the colours over the front door (a blue banner in each one's
 * barrels); a knight gears up and his plain shield comes out painted in them; the hall's trade routes page shows a
 * neighbour by its own banner, and the Book of Edicts the village by its own. Its checks: a banner in the colours over
 * every house, the knight's shield in the colours, the routes page's and the Book's banners.
 */
final class VillageBannerScene {
	private static final BlockPos HALL = new BlockPos(-3, -60, 8);
	private static final BlockPos[] ANCHORS = {new BlockPos(-24, -60, -6), new BlockPos(-10, -60, -6), new BlockPos(4, -60, -6)};
	private static final BlockPos POST = new BlockPos(12, -60, 6);
	/** The neighbours on the routes page: one with colours of its own, one without. */
	private static final BlockPos REDFIELD = new BlockPos(-40, -60, 40);
	private static final BlockPos MOSSGATE = new BlockPos(40, -60, 48);
	/** Client ticks to wait for the street at most. */
	private static final int LIMIT = 7000;
	private int tick;
	private final List<UUID> sites = new ArrayList<>();
	private final List<BoundingBox> houses = new ArrayList<>();
	private volatile boolean built;
	private int builtAt = -1;
	private volatile int redfieldSlot = -1;
	private Villager knight;
	private VillageBanners.Colours colours;

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
				level.setDayTime(1000); // morning: the builders at work
			});
		}
		if (tick == 40) {
			server.execute(() -> stage(server));
		}
		if (builtAt < 0) {
			if (tick > 60 && tick % 20 == 0) {
				server.execute(() -> built = sites.stream().allMatch(id -> BuildSiteManager.get(server.overworld()).get(id) == null));
			}
			if (built || tick > LIMIT) {
				builtAt = tick;
				server.execute(() -> street(server));
			}
			return;
		}
		int t = tick - builtAt;
		if (t == 40) {
			ScreenshotHarness.shot(mc, "01_village_banner_street");
		}
		if (t == 60) {
			server.execute(() -> knight(server));
		}
		if (t == 90) {
			ScreenshotHarness.shot(mc, "02_village_banner_knight");
		}
		if (t == 110) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				VillageHallScreen.open(player, HALL);
				if (player.containerMenu instanceof ChoiceMenu m) {
					m.press(VillageHallScreen.ROUTES, player);
					for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
						Component name = m.icon(slot).get(DataComponents.CUSTOM_NAME);
						if (name != null && name.getString().equals("Redfield")) {
							redfieldSlot = slot;
						}
					}
				}
			});
		}
		if (t == 125 && redfieldSlot >= 0) {
			ScreenshotHarness.pointAt(mc, redfieldSlot, 6);
		}
		if (t == 140) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				VillageBanners.Colours red = VillageBanners.of(server.overworld(), REDFIELD);
				Showcase.check(player.containerMenu instanceof ChoiceMenu m && redfieldSlot >= 0 && red != null
					&& m.icon(redfieldSlot).is(Items.RED_BANNER) && red.patterns().equals(m.icon(redfieldSlot).get(DataComponents.BANNER_PATTERNS)),
					"the routes page shows Redfield by its banner");
			});
			ScreenshotHarness.shot(mc, "03_village_banner_routes");
		}
		if (t == 160) {
			server.execute(() -> EdictBook.open(server.getPlayerList().getPlayers().get(0), HALL));
		}
		if (t == 175) {
			ScreenshotHarness.pointAt(mc, EdictBook.BANNER, 6);
		}
		if (t == 190) {
			server.execute(() -> {
				ServerPlayer player = server.getPlayerList().getPlayers().get(0);
				Showcase.check(player.containerMenu instanceof ChoiceMenu m && m.icon(EdictBook.HEADER).is(Items.BLUE_BANNER)
					&& colours.patterns().equals(m.icon(EdictBook.HEADER).get(DataComponents.BANNER_PATTERNS)),
					"the Book of Edicts shows the village by its own banner");
			});
			ScreenshotHarness.shot(mc, "04_village_banner_book");
		}
		if (t == 210) {
			mc.stop();
		}
	}

	private static BannerPatternLayers layers(ServerLevel level, Object... colourAndPattern) {
		var patterns = level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN);
		BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
		for (int i = 0; i < colourAndPattern.length; i += 2) {
			@SuppressWarnings("unchecked")
			ResourceKey<BannerPattern> pattern = (ResourceKey<BannerPattern>) colourAndPattern[i];
			builder.add(patterns.getOrThrow(pattern), (DyeColor) colourAndPattern[i + 1]);
		}
		return builder.build();
	}

	private static VillageHallBlockEntity hall(ServerLevel level, BlockPos pos, String name) {
		level.setBlockAndUpdate(pos, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(pos);
		hall.setCustomName(Component.literal(name));
		return hall;
	}

	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(1, server);
		VillageHallBlockEntity hall = hall(level, HALL, "Bluebrook");
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		// The owner gives the village its colours with a Village Banner: blue, a gold border and points, a white diamond, a blue flower.
		ItemStack banner = VillageBannerItem.of(DyeColor.BLUE, layers(level, BannerPatterns.TRIANGLES_BOTTOM, DyeColor.YELLOW,
			BannerPatterns.RHOMBUS_MIDDLE, DyeColor.WHITE, BannerPatterns.FLOWER, DyeColor.BLUE, BannerPatterns.BORDER, DyeColor.YELLOW));
		VillageBannerItem.setColours(level, player, banner, HALL);
		colours = VillageBanners.of(hall);
		Showcase.check(colours != null && colours.base() == DyeColor.BLUE, "the hall took the Village Banner's colours");
		// Two neighbours for the routes page: Redfield has colours of its own, Mossgate none.
		VillageHallBlockEntity redfield = hall(level, REDFIELD, "Redfield");
		redfield.setColours(new VillageBanners.Colours(DyeColor.RED, layers(level, BannerPatterns.STRAIGHT_CROSS, DyeColor.WHITE,
			BannerPatterns.CIRCLE_MIDDLE, DyeColor.YELLOW)));
		hall(level, MOSSGATE, "Mossgate");
		Caravans.Data data = Caravans.Data.get(level);
		data.setWants(HALL, Component.literal("Bluebrook"), List.of());
		data.setWants(REDFIELD, Component.literal("Redfield"), List.of(new Caravans.Want(Items.BREAD, 16)));
		data.setWants(MOSSGATE, Component.literal("Mossgate"), List.of(new Caravans.Want(Items.OAK_LOG, 32)));
		// The street: three stone houses, each builder with a blue banner in the barrels (they never ask for one).
		for (BlockPos anchor : ANCHORS) {
			UUID site = JobScenes.builderSite(level, StarterBlueprints.STONE_HOUSE, anchor, List.of(), Map.of(Items.BLUE_BANNER, 1));
			BuildSite s = site == null ? null : BuildSiteManager.get(level).get(site);
			if (s != null && s.plan(level) != null) {
				sites.add(site);
				houses.add(s.plan(level).bounds());
			}
		}
		Showcase.check(sites.size() == 3, "three builders were staged");
		// A knight with a plain shield waiting in his chest.
		knight = JobScenes.guard(level, POST, new ItemStack(Items.IRON_SWORD));
		((Container) level.getBlockEntity(POST.east(2))).setItem(0, new ItemStack(Items.SHIELD));
		ScreenshotHarness.hoverLookingAt(player, new Vec3(-10, -51, 16), new Vec3(-10, -56, -10));
	}

	private void street(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Showcase.check(built, "the builders finished all three houses");
		int flying = 0;
		for (BoundingBox box : houses) {
			BoundingBox around = box.inflatedBy(1);
			boolean found = false;
			for (BlockPos p : BlockPos.betweenClosed(around.minX(), around.minY(), around.minZ(), around.maxX(), around.maxY(), around.maxZ())) {
				BlockEntity entity = level.getBlockEntity(p);
				if (entity instanceof BannerBlockEntity banner && colours.on(banner)) {
					found = true;
					break;
				}
			}
			flying += found ? 1 : 0;
		}
		Showcase.check(flying == houses.size() && flying == 3, "the colours hang over every house's door: " + flying + " of " + houses.size());
		ScreenshotHarness.hoverLookingAt(server.getPlayerList().getPlayers().get(0), new Vec3(-10, -53, 12), new Vec3(-10, -55, -8));
	}

	private void knight(MinecraftServer server) {
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		ItemStack shield = knight.getItemBySlot(EquipmentSlot.OFFHAND);
		Showcase.check(shield.is(Items.SHIELD) && shield.get(DataComponents.BASE_COLOR) == DyeColor.BLUE
			&& colours.patterns().equals(shield.get(DataComponents.BANNER_PATTERNS)), "the knight's plain shield came out in the colours");
		// He stands for his picture in the street, in front of the houses, turned to the camera.
		knight.setNoAi(true);
		knight.moveTo(-3.5, -60, 2.5, -14f, 0f);
		knight.setYHeadRot(-14f);
		knight.setYBodyRot(-14f);
		ScreenshotHarness.hoverLookingAt(player, new Vec3(-2.5, -58.2, 6.5), new Vec3(-3.5, -58.6, 2.5));
	}
}

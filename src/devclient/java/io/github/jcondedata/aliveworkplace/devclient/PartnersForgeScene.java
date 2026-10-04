package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.build.SupplyContainers;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Furnaces;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=partners_forge (ROADMAP 28.5, with Cobblemon): partners at work in the post and the forge. A Pidgeotto pastured
 * by the Postal Desk takes the air mail: it takes off with a bundle, climbs out of sight and lands back empty-handed. A
 * Charmander pastured by the armorer's blast furnace breathes fire into it as it smelts 8 raw iron on the spot.
 */
final class PartnersForgeScene {
	private static final BlockPos DESK = new BlockPos(0, -60, 0);
	private static final BlockPos POST_PASTURE = new BlockPos(3, -60, 2);
	private static final BlockPos FURNACE = new BlockPos(-7, -60, 0);
	private static final BlockPos FIRE_PASTURE = new BlockPos(-10, -60, 3);
	private static final int CUE = 140;

	private int tick;
	private volatile Villager postman;
	private volatile Villager armorer;
	private volatile Entity pidgeotto;
	private volatile Entity charmander;
	private int cueAt = -1;
	/** What the air mail show showed: the highest the Pidgeotto got above where it took off, and the bundle. */
	private volatile double highest;
	private volatile double takeOffY = Double.NaN;
	private volatile boolean bundle;
	private volatile boolean done;
	/** How high the Pidgeotto is now, and the stills taken as it takes off and climbs. */
	private volatile double liftNow;
	private boolean tookOff;
	private boolean climbed;

	void tick(Minecraft mc, MinecraftServer server) {
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> setUp(server));
		}
		// The partners come out of their pastures a moment after setting up: wait for both (up to 10 s).
		if (tick >= CUE - 100 && cueAt < 0 && tick % 5 == 0) {
			server.execute(() -> {
				pidgeotto = pastured(server.overworld(), "pidgeotto");
				charmander = pastured(server.overworld(), "charmander");
			});
			if (pidgeotto != null && charmander != null) {
				cueAt = Math.max(tick + 5, CUE);
			} else if (tick >= CUE + 200) {
				Showcase.check(false, "the Pidgeotto and the Charmander came out of their pastures");
				ScreenshotHarness.shot(mc, "99_timeout");
				mc.stop();
			}
		}
		if (cueAt > 0 && tick == cueAt) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				// The postman hands in a parcel for far away: the air mail goes up from the desk.
				boolean air = PartnerShows.cue(postman, "air_mail", DESK);
				Showcase.check(air, "the air mail cued the Pidgeotto" + (air ? "" : ": " + PartnerShows.lastRefusal()));
				// The armorer tends the blast furnace: the Charmander smelts 8 on the spot, breathing fire into it.
				Furnaces.tend(level, FURNACE, SupplyContainers.find(level, FURNACE, null), Furnaces::isOre, armorer);
				Showcase.check(PartnerShows.lastShow(armorer) != null, "the blast furnace cued the Charmander's fire"
					+ (PartnerShows.lastShow(armorer) != null ? "" : ": " + PartnerShows.lastRefusal()));
			});
		}
		if (cueAt > 0 && tick > cueAt && !done) {
			server.execute(() -> {
				if (pidgeotto == null) {
					return;
				}
				double up = PartnerShows.flying(pidgeotto);
				if (up > 0 && Double.isNaN(takeOffY)) {
					takeOffY = pidgeotto.getY() - up;
				}
				highest = Math.max(highest, up);
				liftNow = up;
				bundle |= PartnerShows.carrying(pidgeotto).map(d -> d.getSlot(0).get().is(Items.BUNDLE)).orElse(false);
			});
		}
		// Stills (the GIF has the whole of it): the Charmander at the furnace and the Pidgeotto taking off, then the
		// Pidgeotto climbing, then both back.
		if (cueAt > 0 && !tookOff && liftNow > 0.5) {
			tookOff = true;
			ScreenshotHarness.shot(mc, "01_fire_and_take_off");
		}
		if (cueAt > 0 && !climbed && liftNow >= 10) {
			climbed = true;
			ScreenshotHarness.shot(mc, "02_air_mail_climbs");
		}
		if (cueAt > 0 && tick > cueAt + 100 && tick % 5 == 0 && !done) {
			server.execute(() -> {
				if (!PartnerShows.busy(pidgeotto) && !PartnerShows.busy(charmander)) {
					done = true;
				}
			});
		}
		if (done && tick > cueAt + 100 && tick % 5 == 0) {
			ScreenshotHarness.shot(mc, "03_back_down");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				Showcase.check(bundle, "the Pidgeotto took off with the air mail's bundle");
				Showcase.check(highest >= 6, "the Pidgeotto climbed out of sight (" + Math.round(highest) + " blocks up)");
				Showcase.check(PartnerShows.flying(pidgeotto) == 0 && Math.abs(pidgeotto.getY() - takeOffY) < 1.5,
					"the Pidgeotto landed back where it took off (y " + Math.round(pidgeotto.getY()) + ", took off at " + Math.round(takeOffY) + ")");
				Container chest = (Container) level.getBlockEntity(FURNACE.south());
				int ingots = chest == null ? 0 : chest.countItem(Items.IRON_INGOT);
				Showcase.check(ingots == 8, "the Charmander smelted 8 raw iron on the spot (" + ingots + " ingots in the chest)");
				boolean leftover = !level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, new AABB(DESK).inflate(40),
					d -> d.getTags().contains(PartnerShows.DISPLAY_TAG)).isEmpty();
				Showcase.check(!leftover, "no bundle display left behind after the show");
			});
			done = false;
			tick = -100000; // stop here: quit a moment later
		}
		if (tick == -100000 + 20) {
			mc.stop();
		}
		if (tick == CUE + 1100) {
			Showcase.check(false, "the shows ended (gave up at the time limit)");
			ScreenshotHarness.shot(mc, "99_timeout");
			mc.stop();
		}
	}

	private static Entity pastured(ServerLevel level, String species) {
		return level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, new AABB(DESK).inflate(40),
			e -> e.getTethering() != null && e.getPokemon().getSpecies().getName().equalsIgnoreCase(species)).stream().findFirst().orElse(null);
	}

	private void setUp(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2500);
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		// The post: the Postal Desk, its postman, and a Pidgeotto pastured beside it.
		level.setBlockAndUpdate(DESK, ModBlocks.POSTAL_DESK.defaultBlockState());
		postman = EntityType.VILLAGER.spawn(level, DESK.south(), MobSpawnType.COMMAND);
		Jobs.employ(level, postman, DESK, ModVillagers.POSTAL_DESK_POI, ModVillagers.POSTMAN);
		postman.setNoAi(true); // stands by the desk for the pictures
		pasture(level, player, POST_PASTURE, "pidgeotto level=30");
		// The forge: a blast furnace full of raw iron, a chest beside it, the armorer and a Charmander.
		level.setBlockAndUpdate(FURNACE, Blocks.BLAST_FURNACE.defaultBlockState());
		level.setBlockAndUpdate(FURNACE.south(), Blocks.CHEST.defaultBlockState());
		if (level.getBlockEntity(FURNACE) instanceof AbstractFurnaceBlockEntity furnace) {
			furnace.setItem(0, new ItemStack(Items.RAW_IRON, 24));
		}
		armorer = EntityType.VILLAGER.spawn(level, FURNACE.east(), MobSpawnType.COMMAND);
		Jobs.employ(level, armorer, FURNACE, PoiTypes.ARMORER, VillagerProfession.ARMORER);
		armorer.setNoAi(true);
		pasture(level, player, FIRE_PASTURE, "charmander level=20");
		ScreenshotHarness.hover(player, new Vec3(-3.0, -57.0, 9.5), 180, 8); // low, so the sky over the desk is in view
	}

	private static void pasture(ServerLevel level, ServerPlayer player, BlockPos at, String species) {
		level.setBlockAndUpdate(at, ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
			"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.BOTTOM));
		level.setBlockAndUpdate(at.above(), ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
			"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.TOP));
		var pokemon = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(species, " ", "=").create();
		com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
		if (level.getBlockEntity(at) instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity pen) {
			pen.tether(player, pokemon, Direction.WEST);
		}
	}
}

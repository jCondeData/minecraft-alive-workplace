package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=partners_land (ROADMAP 28.4, with Cobblemon): partners at work on building and the land. A Machamp pastured by
 * the Builder's Bench shoulders the oak beams the builder fetches over to the frame; a Wartortle pastured by the
 * farmer's composter waters the dry patch the farmer tends, and the farmland turns fully moist.
 */
final class PartnersLandScene {
	private static final BlockPos BENCH = new BlockPos(0, -60, 0);
	private static final BlockPos BUILD_PASTURE = new BlockPos(6, -60, 2);
	private static final BlockPos FRAME = new BlockPos(2, -60, 6);
	private static final BlockPos COMPOSTER = new BlockPos(-8, -60, 0);
	private static final BlockPos FARM_PASTURE = new BlockPos(-13, -60, 3);
	/** The middle of the 3×3 wheat patch the farmer tends (the farmland is one lower). */
	private static final BlockPos PATCH = new BlockPos(-8, -60, 6);
	private static final int CUE = 140;

	private int tick;
	private volatile Villager builder;
	private volatile Villager farmer;
	private volatile Entity machamp;
	private volatile Entity wartortle;
	private volatile boolean done;
	private int cueAt = -1;
	/** The most farmland of the patch seen fully moist at once (it dries again slowly, with no water near). */
	private volatile int mostWet;

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
				machamp = pastured(server.overworld(), "machamp");
				wartortle = pastured(server.overworld(), "wartortle");
			});
			if (machamp != null && wartortle != null) {
				cueAt = Math.max(tick + 5, CUE);
			} else if (tick >= CUE + 200) {
				Showcase.check(false, "the Machamp and the Wartortle came out of their pastures");
				ScreenshotHarness.shot(mc, "99_timeout");
				mc.stop();
			}
		}
		if (cueAt > 0 && tick == cueAt) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				boolean beams = PartnerShows.cue(builder, "fetch", FRAME, new ItemStack(Items.STRIPPED_OAK_LOG));
				Showcase.check(beams, "the builder's fetch cued the Machamp" + (beams ? "" : ": " + PartnerShows.lastRefusal()));
				Showcase.check(PartnerShows.carrying(machamp).map(d -> d.getSlot(0).get().is(Items.STRIPPED_OAK_LOG)).orElse(false),
					"the Machamp shouldered the oak beams");
				boolean water = PartnerShows.cue(farmer, "tend", PATCH);
				Showcase.check(water, "the farmer's tending cued the Wartortle" + (water ? "" : ": " + PartnerShows.lastRefusal()));
			});
		}
		if (cueAt > 0 && tick == cueAt + 15) {
			ScreenshotHarness.shot(mc, "01_partners_carry");
		}
		if (cueAt > 0 && tick > cueAt && !done) {
			server.execute(() -> mostWet = Math.max(mostWet, wet(server.overworld(), null)));
		}
		if (cueAt > 0 && tick > cueAt + 20 && tick % 5 == 0 && !done) {
			server.execute(() -> {
				if (machamp != null && wartortle != null && !PartnerShows.busy(machamp) && !PartnerShows.busy(wartortle)) {
					done = true;
				}
			});
		}
		if (cueAt > 0 && tick == cueAt + 60) {
			ScreenshotHarness.shot(mc, "02_partners_at_work");
		}
		if (done && tick > cueAt + 60 && tick % 5 == 0) {
			ScreenshotHarness.shot(mc, "03_watered_field");
			server.execute(() -> {
				ServerLevel level = server.overworld();
				StringBuilder dry = new StringBuilder();
				int now = wet(level, dry);
				Showcase.check(mostWet == 9, "the Wartortle watered the whole patch (" + mostWet + " of 9 farmland at moisture 7; now " + now
					+ (dry.isEmpty() ? "" : ", drying:" + dry) + ")");
				boolean leftover = !level.getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, new AABB(BENCH).inflate(32),
					d -> d.getTags().contains(PartnerShows.DISPLAY_TAG)).isEmpty();
				Showcase.check(!leftover, "no beam display left behind after the show");
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

	/** How much of the patch's farmland is fully moist; the rest named in {@code dry} if given. */
	private static int wet(ServerLevel level, StringBuilder dry) {
		int wet = 0;
		for (BlockPos p : BlockPos.betweenClosed(PATCH.offset(-1, -1, -1), PATCH.offset(1, -1, 1))) {
			BlockState state = level.getBlockState(p);
			boolean moist = state.is(Blocks.FARMLAND) && state.getValue(FarmBlock.MOISTURE) == FarmBlock.MAX_MOISTURE;
			wet += moist ? 1 : 0;
			if (!moist && dry != null) {
				dry.append(' ').append(p.getX() - PATCH.getX()).append(',').append(p.getZ() - PATCH.getZ()).append('=').append(state);
			}
		}
		return wet;
	}

	private static Entity pastured(ServerLevel level, String species) {
		return level.getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class, new AABB(BENCH).inflate(40),
			e -> e.getTethering() != null && e.getPokemon().getSpecies().getName().equalsIgnoreCase(species)).stream().findFirst().orElse(null);
	}

	private void setUp(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2500);
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		// The builder: its bench, a chest of beams and a half-built frame of oak posts.
		level.setBlockAndUpdate(BENCH, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		level.setBlockAndUpdate(BENCH.east(), Blocks.CHEST.defaultBlockState());
		builder = EntityType.VILLAGER.spawn(level, BENCH.south(), MobSpawnType.COMMAND);
		Jobs.employ(level, builder, BENCH, ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		builder.setNoAi(true); // stands by the bench for the pictures
		for (int y = 0; y < 3; y++) {
			level.setBlockAndUpdate(FRAME.offset(-1, y, 0), Blocks.STRIPPED_OAK_LOG.defaultBlockState());
			level.setBlockAndUpdate(FRAME.offset(2, y, 0), Blocks.STRIPPED_OAK_LOG.defaultBlockState());
		}
		pasture(level, player, BUILD_PASTURE, "machamp level=40");
		// The farmer: a composter, and a dry 3×3 patch of ripening wheat with no water near.
		level.setBlockAndUpdate(COMPOSTER, Blocks.COMPOSTER.defaultBlockState());
		for (BlockPos p : BlockPos.betweenClosed(PATCH.offset(-1, -1, -1), PATCH.offset(1, -1, 1))) {
			level.setBlockAndUpdate(p, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 0));
			level.setBlockAndUpdate(p.above(), Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 5));
		}
		farmer = EntityType.VILLAGER.spawn(level, COMPOSTER.south(), MobSpawnType.COMMAND);
		Jobs.employ(level, farmer, COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER);
		farmer.setNoAi(true);
		pasture(level, player, FARM_PASTURE, "wartortle level=30");
		ScreenshotHarness.hover(player, new Vec3(-3.5, -55.0, 15.5), 180, 32);
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

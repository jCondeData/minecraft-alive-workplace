package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import io.github.jcondedata.aliveworkplace.work.PartnerShows;
import java.util.List;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=partners_engine (ROADMAP 28.3, with Cobblemon): a builder fetches planks and the Machop pastured by the
 * Builder's Bench shoulders them over to the work, punches the job home, and walks back without them.
 */
final class PartnersScene {
	private static final BlockPos BENCH = new BlockPos(0, -60, 0);
	private static final BlockPos PASTURE = new BlockPos(7, -60, 6);
	private static final BlockPos WORK = new BlockPos(-1, -60, 5);
	private static final int CUE = 120;

	private int tick;
	private volatile Villager builder;
	private volatile Entity machop;
	private volatile Vec3 machopStart;
	private volatile boolean cued;
	private volatile boolean carried;
	private volatile double nearest = Double.MAX_VALUE;
	private volatile boolean done;

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
		if (tick == CUE) {
			server.execute(() -> {
				machop = server.overworld().getEntitiesOfClass(com.cobblemon.mod.common.entity.pokemon.PokemonEntity.class,
					new AABB(PASTURE).inflate(16), e -> e.getTethering() != null).stream().findFirst().orElse(null);
				if (machop == null || builder == null) {
					Showcase.check(false, "the Machop and the builder are there");
					return;
				}
				// start the walk from the pasture: a Machop that wandered over to the work before the cue has no walk
				// left to measure (B45)
				machop.teleportTo(PASTURE.getX() - 0.5, PASTURE.getY(), PASTURE.getZ() + 0.5);
				machop.setDeltaMovement(Vec3.ZERO);
				if (machop instanceof net.minecraft.world.entity.Mob mob) {
					mob.getNavigation().stop();
				}
				machopStart = machop.position();
				cued = PartnerShows.cue(builder, "fetch", WORK, new ItemStack(Items.OAK_PLANKS));
				Showcase.check(cued, "the builder's fetch cued the Machop" + (cued ? "" : ": " + PartnerShows.lastRefusal()));
				carried = PartnerShows.carrying(machop).map(d -> d.getSlot(0).get().is(Items.OAK_PLANKS)).orElse(false);
				Showcase.check(carried, "the Machop picked up the planks");
			});
		}
		if (tick > CUE && tick % 5 == 0 && !done) {
			server.execute(() -> {
				if (machop != null) {
					nearest = Math.min(nearest, machop.position().distanceTo(Vec3.atBottomCenterOf(WORK)));
				}
				if (cued && machop != null && !PartnerShows.busy(machop)) {
					done = true;
				}
			});
		}
		if (tick == CUE + 15) {
			ScreenshotHarness.shot(mc, "01_partner_carries");
		}
		if (tick == CUE + 45) {
			ScreenshotHarness.shot(mc, "02_partner_at_work");
		}
		if (done && tick > CUE + 45 && tick % 5 == 0) {
			ScreenshotHarness.shot(mc, "03_partner_back");
			server.execute(() -> {
				double from = machopStart.distanceTo(Vec3.atBottomCenterOf(WORK));
				Showcase.check(nearest < from - 1.5, String.format("the Machop walked toward the work (from %.1f to %.1f blocks)", from, nearest));
				boolean leftover = !server.overworld().getEntitiesOfClass(net.minecraft.world.entity.Display.ItemDisplay.class, new AABB(BENCH).inflate(24),
					d -> d.getTags().contains(PartnerShows.DISPLAY_TAG)).isEmpty();
				Showcase.check(!leftover, "no plank display left behind after the show");
			});
			done = false;
			tick = -100000; // stop here: quit a moment later
		}
		if (tick == -100000 + 20) {
			mc.stop();
		}
		if (tick == CUE + 900) {
			Showcase.check(false, "the show ended (gave up at the time limit)");
			ScreenshotHarness.shot(mc, "99_timeout");
			mc.stop();
		}
	}

	private void setUp(MinecraftServer server) {
		ServerLevel level = server.overworld();
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(2500);
		level.setBlockAndUpdate(BENCH, ModBlocks.BUILDERS_BENCH.defaultBlockState());
		builder = EntityType.VILLAGER.spawn(level, BENCH.south(), MobSpawnType.COMMAND);
		Jobs.employ(level, builder, BENCH, ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER);
		builder.setNoAi(true); // stands by the bench for the pictures
		level.setBlockAndUpdate(PASTURE, ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
			"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.BOTTOM));
		level.setBlockAndUpdate(PASTURE.above(), ScreenshotHarness.with(ScreenshotHarness.with(ScreenshotHarness.cobblemonBlock("pasture"), "waterlogged", false),
			"part", com.cobblemon.mod.common.block.PastureBlock.PasturePart.TOP));
		ServerPlayer player = server.getPlayerList().getPlayers().get(0);
		for (String species : List.of("machop level=30")) {
			var pokemon = com.cobblemon.mod.common.api.pokemon.PokemonProperties.Companion.parse(species, " ", "=").create();
			com.cobblemon.mod.common.Cobblemon.INSTANCE.getStorage().getPC(player).add(pokemon);
			if (level.getBlockEntity(PASTURE) instanceof com.cobblemon.mod.common.block.entity.PokemonPastureBlockEntity pen) {
				pen.tether(player, pokemon, Direction.WEST);
			}
		}
		ScreenshotHarness.hover(player, new Vec3(3.5, -56.5, 12.5), 180, 30);
	}
}

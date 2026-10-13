package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.wonder.LighthousePowers;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * SCENE=wonder_lighthouse (ROADMAP 35.12): the Lighthouse's time-lapse beside a sea the harness digs and fills behind the
 * plan (under the jetty and the mole's far side, three deep, and on out), its festival's fireworks, then midnight with
 * the lantern's redstone lamps lit; its powers on (twice the caravans' reach) and the jetty's post on the network.
 */
final class WonderLighthouseScene {
	/** The template's size (25 x 46 x 33), its front at z 0; the jetty runs out along x 22-24 to z 32. */
	private static final int WIDTH = 25;
	private static final int DEPTH = 33;
	/** The sea begins this far into the template (behind the keeper's house) and runs past its back and right side. */
	private static final int SEA_FROM_Z = 11;
	private static final int SEA_FROM_X = 15;
	private static final int OUT = 14;

	static WonderTimelapse create() {
		return new WonderTimelapse(AliveWorkplace.id("lighthouse"), "wonder_lighthouse")
			.ground(WonderLighthouseScene::sea)
			.night(WonderLighthouseScene::nightChecks);
	}

	/** Digs the sea (y -63 to -61 on the superflat, so the template's y 0 stands at its edge) and fills it with water. */
	static void sea(ServerLevel level, BlueprintData.Placement placement) {
		int filled = 0;
		for (int x = 0; x < WIDTH + OUT; x++) {
			for (int z = 0; z < DEPTH + OUT; z++) {
				boolean sea = z >= DEPTH || x >= SEA_FROM_X && z >= SEA_FROM_Z;
				if (!sea) {
					continue;
				}
				BlockPos column = placement.origin().offset(StructureTemplate.transform(new BlockPos(x, 0, z), placement.mirror(), placement.rotation(), BlockPos.ZERO));
				for (int y = -63; y <= -61; y++) {
					level.setBlock(new BlockPos(column.getX(), y, column.getZ()), Blocks.WATER.defaultBlockState(), 2);
				}
				filled++;
			}
		}
		Showcase.check(filled > 400, "the harness dug and filled the sea beside the Lighthouse (" + filled + " columns)");
	}

	/** Midnight: the lamps lit, the powers on, the jetty's post named for the village. */
	static void nightChecks(ServerLevel level) {
		WonderSite site = WonderManager.get(level).sites().stream().filter(s -> s.wonder().equals(AliveWorkplace.id("lighthouse")) && s.done())
			.findFirst().orElse(null);
		Showcase.check(site != null, "the Lighthouse finished");
		if (site == null) {
			return;
		}
		int lamps = 0;
		int lit = 0;
		BlockPos o = site.placement().map(BlueprintData.Placement::origin).orElse(site.hall());
		for (BlockPos p : BlockPos.betweenClosed(o.offset(-40, 0, -40), o.offset(40, 60, 40))) {
			BlockState state = level.getBlockState(p);
			if (state.is(Blocks.REDSTONE_LAMP)) {
				lamps++;
				lit += state.getValue(RedstoneLampBlock.LIT) ? 1 : 0;
			}
		}
		Showcase.check(lamps > 0 && lit * 2 >= lamps, "at midnight the lantern's lamps are lit (" + lit + " of " + lamps + ")");
		Showcase.check(LighthousePowers.caravanRange(level, site.hall()) == 2 * Caravans.RANGE && Wonders.power(level, site.hall(), LighthousePowers.FERRY_FARES).isPresent()
			&& Wonders.power(level, site.hall(), LighthousePowers.EARLY_WARNING).isPresent(), "its powers are on: caravans reach " + LighthousePowers.caravanRange(level, site.hall()));
		TravelNetwork.Post post = LighthousePowers.jetty(level, site);
		Showcase.check(post != null && post.name().endsWith(" Lighthouse"), "the jetty's post joined the network: " + (post == null ? "none" : post.name()));
	}

	private WonderLighthouseScene() {
	}
}

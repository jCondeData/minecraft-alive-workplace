package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.guard.Gates;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallPowers;
import io.github.jcondedata.aliveworkplace.wonder.GreatWallSentries;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.block.FenceGateBlock;

/**
 * SCENE=wonder_great_wall (ROADMAP 35.16): the Great Wall's time-lapse, the Great Gate first, then the west and east
 * runs and the walk with the towers' crowns; its festival's fireworks; then midnight, a night raid gathering out on the
 * open side of the village while the Wall's four sentries watch it from the walk, seen from high over the village. Its
 * checks: the sentries took up their posts, the raid gathered outside the arc the Wall covers, the sentries stand on
 * the walk, the Great Gate is shut, and its powers are on.
 */
final class WonderGreatWallScene {
	static final ResourceLocation GREAT_WALL = AliveWorkplace.id("great_wall");

	static WonderTimelapse create() {
		WonderTimelapse[] lapse = new WonderTimelapse[1];
		lapse[0] = new WonderTimelapse(GREAT_WALL, "wonder_great_wall")
			.ground((level, placement) -> {
				// the open side, where the raid gathers (38 out from the hall): kept loaded with the rest
				BlockPos hall = lapse[0].hall();
				for (int cx = (hall.getX() - 64) >> 4; cx <= (hall.getX() + 64) >> 4; cx++) {
					for (int cz = (hall.getZ() + 16) >> 4; cz <= (hall.getZ() + 64) >> 4; cz++) {
						level.setChunkForced(cx, cz, true);
					}
				}
			})
			// from high over the village, behind the hall, looking across it to the Wall: the raid below, the sentries beyond
			.nightView(new double[] {48.5, 36, -86}, new double[] {48.5, 6, -6})
			.nightfall(level -> nightfall(level, lapse[0]))
			.night(level -> nightChecks(level, lapse[0]));
		return lapse[0];
	}

	static WonderSite site(ServerLevel level) {
		return WonderManager.get(level).sites().stream().filter(s -> s.wonder().equals(GREAT_WALL) && s.done()).findFirst().orElse(null);
	}

	/** Midnight: the gates' round, and a night raid sent at the village. */
	static void nightfall(ServerLevel level, WonderTimelapse lapse) {
		BlockPos hall = lapse.hall();
		Gates.round(level, hall, 0);
		VillageRaids.lastGathering = null;
		VillageRaids.Raid raid = VillageRaids.start(level, hall, 16, 0);
		Showcase.check(raid != null, "a night raid set out for the village");
	}

	/** The raid out on the open side, the sentries on the walk watching it, the gate shut, the powers on. */
	static void nightChecks(ServerLevel level, WonderTimelapse lapse) {
		WonderSite site = site(level);
		Showcase.check(site != null, "the Great Wall finished");
		if (site == null) {
			return;
		}
		BlockPos hall = site.hall();
		List<GreatWallPowers.Arc> arcs = GreatWallPowers.arcs(level, hall);
		BlockPos gathered = VillageRaids.lastGathering;
		double bearing = gathered == null ? Double.NaN : Math.atan2(gathered.getZ() - hall.getZ(), gathered.getX() - hall.getX());
		Showcase.check(arcs.size() == 1 && gathered != null && !GreatWallPowers.walled(arcs, bearing),
			"the raid gathered on the open side, outside the " + (arcs.isEmpty() ? 0 : Math.round(Math.toDegrees(arcs.get(0).width()))) + " degrees the Wall covers (at "
				+ gathered + ")");
		List<GreatWallSentries.Post> posts = GreatWallSentries.posts(level, hall);
		int onWalk = 0;
		for (GreatWallSentries.Post post : posts) {
			IronGolem golem = GreatWallSentries.sentry(level, post).orElse(null);
			if (golem != null && golem.getY() >= post.at().getY() - 0.5 && golem.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(post.walk())) < 4) {
				onWalk++;
			}
		}
		Showcase.check(posts.size() == 4 && onWalk == 4, "its four sentries stand on the walk watching (" + onWalk + " of " + posts.size() + ")");
		List<BlockPos> gates = GreatWallPowers.gates(level, site);
		long open = gates.stream().map(level::getBlockState).filter(s -> s.getBlock() instanceof FenceGateBlock && s.getValue(FenceGateBlock.OPEN)).count();
		Showcase.check(!gates.isEmpty() && open == 0, "the Great Gate is shut for the night (" + gates.size() + " fence gates, " + open + " open)");
	}

	private WonderGreatWallScene() {
	}
}

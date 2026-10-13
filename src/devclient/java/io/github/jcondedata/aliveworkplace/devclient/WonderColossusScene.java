package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.wonder.ColossusPlaque;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.wonder.powers.BeautyPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.MoodPower;
import io.github.jcondedata.aliveworkplace.wonder.powers.TravellersPower;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * SCENE=wonder_colossus (ROADMAP 35.22): the Colossus's time-lapse, the Founder's statue rising over the road through
 * its arch, the scaffolding going up beside the robe, the arm and the head and coming down as each stage is done; its
 * festival's fireworks; then midnight seen from the road, the lantern lit over it and the plaque over the arch written
 * in glowing letters (the village's name, its founding day). Its checks: scaffolding went up and none is left, the
 * plaque reads the village and its founding day, the lantern's glowstone stands, and its powers are on.
 */
final class WonderColossusScene {
	/** The village's name, for the plaque. */
	static final String VILLAGE = "Testholm";
	/** How many scaffolding blocks stood round it at most while it went up. */
	private static volatile int mostScaffolding;
	private static int seconds;

	static WonderTimelapse create() {
		WonderTimelapse[] lapse = new WonderTimelapse[1];
		lapse[0] = new WonderTimelapse(ColossusPlaque.COLOSSUS, "wonder_colossus")
			.village((level, entity) -> {
				entity.setCustomName(Component.literal(VILLAGE));
				Chronicle.atHall(level, entity.getBlockPos(), Chronicle.Kind.FOUNDED, Component.translatable("chronicle.aliveworkplace.founded_by",
					VILLAGE, entity.ownerName()), 1);
			})
			.watching(level -> scaffolding(level, lapse[0]))
			// from the road 22 out in front of the arch, looking up at the lantern raised over it (y 43-45, x 22)
			.nightView(new double[] {14.5, 4, -22}, new double[] {16.5, 20, 12})
			.night(level -> nightChecks(level, lapse[0]));
		return lapse[0];
	}

	/** Every fifth second: the scaffolding standing round the Wonder, the most seen kept. */
	private static void scaffolding(ServerLevel level, WonderTimelapse lapse) {
		if (++seconds % 5 != 0 || lapse.box() == null) {
			return;
		}
		mostScaffolding = Math.max(mostScaffolding, count(level, lapse.box()));
	}

	private static int count(ServerLevel level, BoundingBox box) {
		int n = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX() - 3, box.minY(), box.minZ() - 3, box.maxX() + 3, box.maxY(), box.maxZ() + 3)) {
			if (level.getBlockState(p).is(Blocks.SCAFFOLDING)) {
				n++;
			}
		}
		return n;
	}

	/** Midnight: the scaffolding gone, the plaque written, the lantern standing, the powers on. */
	static void nightChecks(ServerLevel level, WonderTimelapse lapse) {
		WonderSite site = WonderManager.get(level).sites().stream().filter(s -> s.wonder().equals(ColossusPlaque.COLOSSUS) && s.done())
			.findFirst().orElse(null);
		Showcase.check(site != null, "the Colossus finished");
		if (site == null || lapse.box() == null) {
			return;
		}
		int left = count(level, lapse.box());
		Showcase.check(mostScaffolding > 0 && left == 0, "scaffolding went up as it rose (" + mostScaffolding + " blocks at most) and came down ("
			+ left + " left)");
		List<BlockPos> signs = ColossusPlaque.signs(level, site);
		String village = signs.size() == 7 ? line(level, signs.get(3), 1) : "";
		String founded = signs.size() == 7 ? line(level, signs.get(4), 1) : "";
		Showcase.check(village.equals(VillageHalls.name(level, site.hall()).getString()) && founded.equals("on day 1"),
			"the plaque over the arch reads the village and its founding day (" + signs.size() + " signs: \"" + village + "\", \"" + founded + "\")");
		BoundingBox box = lapse.box();
		int glow = 0;
		for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.maxY() - 4, box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
			glow += level.getBlockState(p).is(Blocks.GLOWSTONE) ? 1 : 0;
		}
		Showcase.check(glow >= 3, "the lantern's glowstone shines at midnight (" + glow + " blocks)");
		BlockPos hall = site.hall();
		Showcase.check(Wonders.power(level, hall, MoodPower.TYPE).isPresent() && Wonders.power(level, hall, BeautyPower.TYPE).isPresent()
			&& TravellersPower.expertFactor(level, hall) == 2 && TravellersPower.more(level, hall, 1) == 2,
			"its powers are on: the realm happier, travellers twice as often and twice as likely Experts, 8 beauty");
	}

	private static String line(ServerLevel level, BlockPos pos, int i) {
		return level.getBlockEntity(pos) instanceof SignBlockEntity sign ? sign.getText(true).getMessage(i, false).getString() : "(no sign)";
	}

	private WonderColossusScene() {
	}
}

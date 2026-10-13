package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.VillageGrowth;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.wonder.GardensPowers;
import io.github.jcondedata.aliveworkplace.wonder.WonderManager;
import io.github.jcondedata.aliveworkplace.wonder.WonderSite;
import io.github.jcondedata.aliveworkplace.wonder.WonderUpkeep;
import io.github.jcondedata.aliveworkplace.wonder.Wonders;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=wonder_hanging_gardens (ROADMAP 35.20): the Hanging Gardens' time-lapse and its festival's fireworks, then the
 * next morning with the falls running from the crown's cistern down the east side into the pool, and four farmers on the
 * terraces: one at each farm corner's composter, each taking on that terrace's fields by themselves and harvesting
 * them. Its checks: the falls' water stands, all four took on a terrace's farm, and both powers are on.
 */
final class WonderGardensScene {
	private static final ResourceLocation GARDENS = AliveWorkplace.id("hanging_gardens");
	/** The next morning, the farmers' working hours. */
	private static final long MORNING = 24000 + 2500;
	/** Client ticks the farmers have to take on their farms and start (a farmer looks at their composter's farm every 200 ticks). */
	private static final int WAIT = 330;
	private static final String[] NAMES = {"Ada", "Fen", "Rook", "Tilly"};

	private static final List<Villager> FARMERS = new ArrayList<>();

	static WonderTimelapse create() {
		return new WonderTimelapse(GARDENS, "wonder_hanging_gardens").finale("terraces", MORNING, WAIT, WonderGardensScene::farmers, WonderGardensScene::checks);
	}

	private static WonderSite site(ServerLevel level) {
		return WonderManager.get(level).sites().stream().filter(s -> s.wonder().equals(GARDENS) && s.done()).findFirst().orElse(null);
	}

	/** A point of the Gardens' own blueprint, placed as the Wonder was. */
	private static Vec3 placed(BlueprintData.Placement placement, double x, double y, double z) {
		BlockPos at = placement.origin().offset(StructureTemplate.transform(BlockPos.containing(x, y, z), placement.mirror(), placement.rotation(), BlockPos.ZERO));
		return Vec3.atBottomCenterOf(at);
	}

	/** The next morning: a farmer at each terrace's composter, the camera south-east of the falls. */
	static void farmers(ServerLevel level) {
		WonderSite site = site(level);
		Showcase.check(site != null && site.placement().isPresent(), "the Hanging Gardens finished");
		if (site == null || site.placement().isEmpty()) {
			return;
		}
		List<BlockPos> composters = WonderUpkeep.blocks(level, site).stream().filter(b -> b.state().is(Blocks.COMPOSTER)).map(WonderUpkeep.Block::pos).toList();
		for (int i = 0; i < composters.size(); i++) {
			BlockPos c = composters.get(i);
			Villager farmer = EntityType.VILLAGER.create(level);
			farmer.moveTo(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5, 0, 0);
			farmer.setCustomName(Component.literal(NAMES[i % NAMES.length]));
			level.addFreshEntity(farmer);
			Jobs.employ(level, farmer, c, PoiTypes.FARMER, VillagerProfession.FARMER);
			FARMERS.add(farmer);
		}
		BlueprintData.Placement placement = site.placement().get();
		ScreenshotHarness.hoverLookingAt(level.getServer().getPlayerList().getPlayers().get(0), placed(placement, 64, 26, 44), placed(placement, 25, 12, 22));
	}

	/** The falls run, the four farmers took on the terraces' farms, and both powers are on. */
	static void checks(ServerLevel level) {
		WonderSite site = site(level);
		if (site == null) {
			return;
		}
		List<WonderUpkeep.Block> water = WonderUpkeep.blocks(level, site).stream().filter(b -> b.state().is(Blocks.WATER)).toList();
		long wet = water.stream().filter(b -> level.getFluidState(b.pos()).is(FluidTags.WATER)).count();
		Showcase.check(!water.isEmpty() && wet * 10 >= water.size() * 9, "the falls run from the cistern to the pool (" + wet + " of " + water.size() + " water blocks)");
		long farming = FARMERS.stream().filter(f -> {
			FieldJob job = ModAttachments.FARM_FIELD.get(f);
			return job != null && job.adopted();
		}).count();
		int harvested = FARMERS.stream().mapToInt(f -> ModAttachments.FARM_HARVESTED.getOrElse(f, 0)).sum();
		Showcase.check(FARMERS.size() == 4 && farming == 4, "farmers on the terraces: " + farming + " of " + FARMERS.size() + " took on a terrace's farm by themselves ("
			+ harvested + " harvested, " + FARMERS.stream().mapToInt(GardensPowers::extras).sum() + " more brought in)");
		Showcase.check(Wonders.power(level, site.hall(), GardensPowers.HARVEST).isPresent() && Wonders.power(level, site.hall(), GardensPowers.BIRTHS).isPresent(),
			"its powers are on: a quarter more harvest, a baby on " + VillageGrowth.foodNeeded(level, site.hall()) + " meals, " + VillageGrowth.every(level, site.hall()) + " ticks apart");
	}

	private WonderGardensScene() {
	}
}

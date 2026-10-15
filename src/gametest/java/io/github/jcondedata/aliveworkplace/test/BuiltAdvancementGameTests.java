package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.BuiltAdvancements;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * A finished build awards its hidden {@code aliveworkplace:built/<blueprint>} advancement, which the questbook's build
 * quests tick off on: to the player who started the site, or to the players nearby when they're away. A Wonder's part and
 * an imported blueprint award nothing (and don't crash).
 */
public class BuiltAdvancementGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";

	private static boolean done(ServerPlayer player, AdvancementHolder advancement) {
		return player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	private static BuildSite site(GameTestHelper helper, UUID owner, ResourceLocation structure) {
		return new BuildSite(UUID.randomUUID(), owner, "Jesse", structure, new BlueprintData.Placement(helper.getLevel().dimension().location(),
			helper.absolutePos(new BlockPos(1, 2, 1)), Rotation.NONE, Mirror.NONE));
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos pos = helper.absolutePos(at);
		player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** A builder finishing a Starter Cottage gives its owner built/starter_cottage, and only its owner while they're online. */
	//$ gametest_batch AREA '"builtStarterCottage"'
	@GameTest(template = AREA, batch = "builtStarterCottage")
	public void finishedStarterCottageAwardsItsOwner(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder cottage = level.getServer().getAdvancements().get(AliveWorkplace.id("built/starter_cottage"));
		helper.assertTrue(cottage != null, "no advancement aliveworkplace:built/starter_cottage");
		helper.assertTrue(cottage.value().display().map(d -> d.isHidden() && !d.shouldShowToast() && !d.shouldAnnounceChat()).orElse(false),
			"built/starter_cottage isn't hidden and quiet");
		ServerPlayer owner = player(helper, new BlockPos(6, 2, 6));
		ServerPlayer neighbour = player(helper, new BlockPos(8, 2, 8));
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		builder.setNoAi(true);
		builder.setCustomName(Component.literal("Dara"));
		BuildSite site = site(helper, owner.getUUID(), StarterBlueprints.STARTER_COTTAGE.id());
		Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(site.placement()));
		helper.assertTrue(!done(owner, cottage), "the owner had built/starter_cottage before the build");

		Builders.finish(level, builder, site);

		helper.assertTrue(done(owner, cottage), "the owner didn't get built/starter_cottage when the cottage was finished");
		helper.assertTrue(!done(neighbour, cottage), "a player who didn't start the build got it while the owner was online");
		AdvancementHolder second = level.getServer().getAdvancements().get(AliveWorkplace.id("built/starter_cottage_2"));
		helper.assertTrue(second != null && !done(owner, second), "the cottage's upgrade was awarded by the first tier");
		helper.succeed();
	}

	/**
	 * With the owner away, the players within a village's range get it; a Wonder's part, an imported blueprint and an
	 * uploaded one award nothing.
	 */
	//$ gametest_batch AREA '"builtNothing"'
	@GameTest(template = AREA, batch = "builtNothing")
	public void wonderPartsAndImportsAwardNothing(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder cottage = level.getServer().getAdvancements().get(AliveWorkplace.id("built/starter_cottage"));
		helper.assertTrue(cottage != null, "no advancement aliveworkplace:built/starter_cottage");
		ServerPlayer nearby = player(helper, new BlockPos(6, 2, 6));

		BuildSite part = site(helper, nearby.getUUID(), StarterBlueprints.STARTER_COTTAGE.id());
		part.setWonder(UUID.randomUUID());
		helper.assertTrue(BuiltAdvancements.award(level, part) == 0 && !done(nearby, cottage), "a Wonder's part awarded built/starter_cottage");
		for (String imported : new String[] {"imported/my_house", "uploads/jesse/my_house", "scans/jesse/my_house"}) {
			BuildSite site = site(helper, nearby.getUUID(), AliveWorkplace.id(imported));
			helper.assertTrue(BuiltAdvancements.advancementFor(level, site.structure()).isEmpty(), imported + " has a built advancement");
			helper.assertTrue(BuiltAdvancements.award(level, site) == 0, imported + " awarded something");
		}
		helper.assertTrue(BuiltAdvancements.award(level, site(helper, nearby.getUUID(), ResourceLocation.fromNamespaceAndPath("other", "starter_cottage"))) == 0,
			"another mod's blueprint awarded something");

		// the owner isn't online: the player next to the build gets it
		BuildSite away = site(helper, UUID.randomUUID(), StarterBlueprints.STARTER_COTTAGE.id());
		helper.assertTrue(BuiltAdvancements.award(level, away) >= 1 && done(nearby, cottage), "with the owner away, the player beside the build didn't get it");
		helper.succeed();
	}

	/**
	 * A Hamlet that reaches Village rank gives the players in the village the hidden village/rank_village advancement
	 * (the questbook's rank quest), and not the next rank's.
	 */
	//$ gametest_batch AREA '"rankUpMoment"'
	@GameTest(template = AREA, batch = "rankUpMoment")
	public void aRankUpAwardsItsAdvancement(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder village = level.getServer().getAdvancements().get(AliveWorkplace.id("village/rank_village"));
		AdvancementHolder town = level.getServer().getAdvancements().get(AliveWorkplace.id("village/rank_town"));
		helper.assertTrue(village != null && town != null, "no advancement aliveworkplace:village/rank_village or rank_town");
		helper.assertTrue(village.value().display().map(d -> d.isHidden() && !d.shouldShowToast() && !d.shouldAnnounceChat()).orElse(false),
			"village/rank_village isn't hidden and quiet");
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BuildSiteManager sites = BuildSiteManager.get(level);
		List<BlueprintData.Placement> placed = new ArrayList<>();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			placed.forEach(sites::forgetFinished);
		});
		helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(new BlockPos(2, 2, 2));
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		ServerPlayer nearby = player(helper, new BlockPos(6, 2, 6));
		for (int i = 0; i < 5; i++) {
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(),
				helper.absolutePos(new BlockPos(1 + i * 2, 2, 10)), Rotation.NONE, Mirror.NONE);
			placed.add(placement);
			sites.recordFinished(StarterBlueprints.PARK_BENCH.id(), placement, UUID.randomUUID());
		}
		helper.assertTrue(entity.rank() == VillageRanks.Rank.HAMLET && !done(nearby, village), "the village started above a Hamlet");

		VillageRanks.round(level, hall, entity, 10);

		helper.assertTrue(entity.rank() == VillageRanks.Rank.VILLAGE, "10 villagers and 5 buildings made a " + entity.rank());
		helper.assertTrue(done(nearby, village), "the player in the village didn't get village/rank_village");
		helper.assertTrue(!done(nearby, town), "a Village rank awarded village/rank_town");
		helper.succeed();
	}
}

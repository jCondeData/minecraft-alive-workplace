package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.BuiltAdvancements;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-11-16b): the questbook's hidden {@code aliveworkplace:built/<blueprint>} advancements, through a real build
 * finishing ({@link Builders#finish}), from the spec: a look or style counts as its blueprint; the owner gets it, or,
 * when they're offline or no player started it, the players within {@link VillageHalls#RADIUS} of the site's origin;
 * repairs, refreshes and deconstructions award nothing; finishing twice is harmless; a reloaded site still awards; and
 * every shipped blueprint has its advancement.
 */
public class QaBuiltAdvancements1116bGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");

	private static AdvancementHolder adv(GameTestHelper helper, String path) {
		AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(AliveWorkplace.id(path));
		if (holder == null) {
			throw new GameTestAssertException("no advancement aliveworkplace:" + path);
		}
		return holder;
	}

	private static boolean done(ServerPlayer player, AdvancementHolder advancement) {
		return player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	private static BlueprintData.Placement placement(GameTestHelper helper, BlockPos rel) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(rel), Rotation.NONE, Mirror.NONE);
	}

	/** A site at {@code rel} whose finished record is forgotten after the test. */
	private static BuildSite site(GameTestHelper helper, UUID owner, ResourceLocation structure, BlockPos rel) {
		BuildSite site = new BuildSite(UUID.randomUUID(), owner, "Jesse", structure, placement(helper, rel));
		Leftovers.after(helper, () -> BuildSiteManager.get(helper.getLevel()).forgetFinished(site.placement()));
		return site;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos absolute) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	private static Villager builder(GameTestHelper helper) {
		Villager builder = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 4));
		builder.setVillagerData(builder.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(1));
		builder.setNoAi(true);
		builder.setCustomName(Component.literal("Dara"));
		return builder;
	}

	private static void begin(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.finished(helper);
	}

	/** A style of a shipped blueprint, a look of one and a style of a look each award the blueprint as drawn, and only it. */
	//$ gametest_batch AREA '"qaBuilt1116bVariants"'
	@GameTest(template = AREA, batch = "qaBuilt1116bVariants")
	public void styleAndLookVariantsAwardTheirBlueprint(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder cottage = adv(helper, "built/starter_cottage");
		AdvancementHolder cottage2 = adv(helper, "built/starter_cottage_2");
		AdvancementHolder center = adv(helper, "built/pokemon_center");
		AdvancementHolder center2 = adv(helper, "built/pokemon_center_2");
		ServerPlayer owner = player(helper, helper.absolutePos(new BlockPos(6, 2, 6)));
		Villager builder = builder(helper);
		String style = BlueprintStyles.all().get(0).name();

		ResourceLocation styled = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), style);
		helper.assertTrue(!styled.equals(StarterBlueprints.STARTER_COTTAGE.id()), "styling gave the cottage's own id");
		Builders.finish(level, builder, site(helper, owner.getUUID(), styled, new BlockPos(1, 2, 1)));
		helper.assertTrue(done(owner, cottage), "finishing " + styled + " didn't award built/starter_cottage");
		helper.assertTrue(!done(owner, cottage2), "finishing " + styled + " awarded built/starter_cottage_2");
		helper.assertTrue(!done(owner, center), "finishing " + styled + " awarded built/pokemon_center");

		ResourceLocation look = StarterBlueprints.POKEMON_CENTER_LODGE.id();
		Builders.finish(level, builder, site(helper, owner.getUUID(), look, new BlockPos(1, 2, 9)));
		helper.assertTrue(done(owner, center), "finishing the look " + look + " didn't award built/pokemon_center");
		helper.assertTrue(!done(owner, center2), "finishing the look " + look + " awarded built/pokemon_center_2");

		ResourceLocation styledLook = BlueprintStyles.styled(StarterBlueprints.POKEMON_CENTER_2_LOOKS.get(1).id(), style);
		Builders.finish(level, builder, site(helper, owner.getUUID(), styledLook, new BlockPos(9, 2, 1)));
		helper.assertTrue(done(owner, center2), "finishing the styled look " + styledLook + " didn't award built/pokemon_center_2");

		// a style of an import, or of another mod's blueprint, is still not ours
		for (ResourceLocation other : List.of(BlueprintStyles.styled(AliveWorkplace.id("imported/my_house"), style),
			BlueprintStyles.styled(ResourceLocation.fromNamespaceAndPath("other", "starter_cottage"), style))) {
			helper.assertTrue(BuiltAdvancements.award(level, site(helper, owner.getUUID(), other, new BlockPos(9, 2, 9))) == 0,
				other + " awarded a built advancement");
		}
		LOGGER.info("qa built 1116b variants: {} -> cottage, {} -> pokemon_center, {} -> pokemon_center_2, styled imports award 0",
			styled, look, styledLook);
		helper.succeed();
	}

	/**
	 * With the owner offline, a player exactly {@link VillageHalls#RADIUS} from the origin gets it, one a block further
	 * doesn't, and neither does one on a diagonal just past the radius; one on a diagonal just inside does.
	 */
	//$ gametest_batch AREA '"qaBuilt1116bRadius"'
	@GameTest(template = AREA, batch = "qaBuilt1116bRadius")
	public void ownerOfflineRadiusBoundary(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = radius);
		AdvancementHolder cottage = adv(helper, "built/starter_cottage");
		BuildSite site = site(helper, UUID.randomUUID(), StarterBlueprints.STARTER_COTTAGE.id(), new BlockPos(1, 2, 1));
		BlockPos origin = site.placement().origin();
		ServerPlayer edge = player(helper, origin.offset(16, 0, 0));
		ServerPlayer past = player(helper, origin.offset(17, 0, 0));
		ServerPlayer edgeZ = player(helper, origin.offset(0, 0, -16));
		ServerPlayer diagonalIn = player(helper, origin.offset(11, 0, 11));
		ServerPlayer diagonalOut = player(helper, origin.offset(12, 0, 12));

		Builders.finish(level, builder(helper), site);

		helper.assertTrue(done(edge, cottage), "with the owner offline, a player 16 blocks east of the origin (radius 16) didn't get it");
		helper.assertTrue(done(edgeZ, cottage), "with the owner offline, a player 16 blocks north of the origin (radius 16) didn't get it");
		helper.assertTrue(done(diagonalIn, cottage), "with the owner offline, a player 15.6 blocks away (radius 16) didn't get it");
		helper.assertTrue(!done(past, cottage), "a player 17 blocks away got it with a radius of 16");
		helper.assertTrue(!done(diagonalOut, cottage), "a player 17.0 blocks away on a diagonal got it with a radius of 16");
		LOGGER.info("qa built 1116b radius: 16 east/north and 15.6 diagonal got it; 17 east and 17.0 diagonal didn't");
		helper.succeed();
	}

	/** A build no player started (Builders.start without a player, as the Steward's) goes to the players nearby, not far ones. */
	//$ gametest_batch AREA '"qaBuilt1116bNoOwner"'
	@GameTest(template = AREA, batch = "qaBuilt1116bNoOwner")
	public void buildWithNoPlayerOwnerAwardsNearby(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder stall = adv(helper, "built/market_stall");
		Villager builder = builder(helper);
		BlueprintData.Placement placement = placement(helper, new BlockPos(1, 2, 1));
		BuildSite site = Builders.start(level, builder, (Player) null, StarterBlueprints.MARKET_STALL.id(), placement);
		Leftovers.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			if (BuildSiteManager.get(level).get(site.id()) != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
		});
		BlockPos origin = placement.origin();
		ServerPlayer nearby = player(helper, origin.offset(5, 0, 5));
		ServerPlayer far = player(helper, origin.offset(VillageHalls.RADIUS + 20, 0, 0));
		helper.assertTrue(level.getServer().getPlayerList().getPlayer(site.owner()) == null, "the playerless site's owner is an online player");

		Builders.finish(level, builder, site);

		helper.assertTrue(done(nearby, stall), "a player beside a build no player started didn't get built/market_stall");
		helper.assertTrue(!done(far, stall), "a player " + (VillageHalls.RADIUS + 20) + " blocks away got built/market_stall");
		LOGGER.info("qa built 1116b no owner: owner {} offline, nearby got it, far didn't", site.owner());
		helper.succeed();
	}

	/** With the owner online far away, only the owner gets it, not the other player standing at the build. */
	//$ gametest_batch AREA '"qaBuilt1116bOwnerFar"'
	@GameTest(template = AREA, batch = "qaBuilt1116bOwnerFar")
	public void ownerOnlineFarAwayIsTheOnlyOneAwarded(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder tower = adv(helper, "built/lookout_tower");
		ServerPlayer owner = player(helper, helper.absolutePos(new BlockPos(1, 2, 1)).offset(VillageHalls.RADIUS * 3, 0, 0));
		BuildSite site = site(helper, owner.getUUID(), StarterBlueprints.LOOKOUT_TOWER.id(), new BlockPos(1, 2, 1));
		ServerPlayer bystander = player(helper, helper.absolutePos(new BlockPos(6, 2, 6)));

		Builders.finish(level, builder(helper), site);

		helper.assertTrue(done(owner, tower), "the owner online " + VillageHalls.RADIUS * 3 + " blocks away didn't get built/lookout_tower");
		helper.assertTrue(!done(bystander, tower), "a bystander at the build got built/lookout_tower while its owner was online");
		LOGGER.info("qa built 1116b owner far: owner got it, bystander didn't");
		helper.succeed();
	}

	/** A repair and a refresh finishing award nothing, to the owner or the players nearby; a plain build of it then does. */
	//$ gametest_batch AREA '"qaBuilt1116bRepair"'
	@GameTest(template = AREA, batch = "qaBuilt1116bRepair")
	public void repairsAndRefreshesAwardNothing(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder shop = adv(helper, "built/supply_shop");
		ServerPlayer owner = player(helper, helper.absolutePos(new BlockPos(6, 2, 6)));
		ServerPlayer nearby = player(helper, helper.absolutePos(new BlockPos(8, 2, 8)));
		Villager builder = builder(helper);

		BuildSite repair = site(helper, owner.getUUID(), StarterBlueprints.SUPPLY_SHOP.id(), new BlockPos(1, 2, 1));
		repair.setRepair();
		Builders.finish(level, builder, repair);
		helper.assertTrue(!done(owner, shop), "a repair finishing awarded its owner built/supply_shop");

		BuildSite away = site(helper, UUID.randomUUID(), StarterBlueprints.SUPPLY_SHOP.id(), new BlockPos(1, 2, 1));
		away.setRepair();
		Builders.finish(level, builder, away);
		helper.assertTrue(!done(nearby, shop) && !done(owner, shop), "a repair with its owner offline awarded the players nearby built/supply_shop");

		BuildSite refresh = site(helper, owner.getUUID(), StarterBlueprints.SUPPLY_SHOP.id(), new BlockPos(1, 2, 1));
		refresh.setRefresh("old");
		Builders.finish(level, builder, refresh);
		helper.assertTrue(!done(owner, shop), "a refresh (a repair to the latest look) finishing awarded built/supply_shop");

		Builders.finish(level, builder, site(helper, owner.getUUID(), StarterBlueprints.SUPPLY_SHOP.id(), new BlockPos(1, 2, 1)));
		helper.assertTrue(done(owner, shop), "a plain build of the supply shop didn't award built/supply_shop (the setup can't award)");
		LOGGER.info("qa built 1116b repair: repair, offline-owner repair and refresh awarded nothing; a plain build did");
		helper.succeed();
	}

	/** A deconstruction finishing awards nothing, owner online or not; a plain build of it then does. */
	//$ gametest_batch AREA '"qaBuilt1116bTakeDown"'
	@GameTest(template = AREA, batch = "qaBuilt1116bTakeDown")
	public void deconstructionsAwardNothing(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder farm = adv(helper, "built/berry_farm");
		ServerPlayer owner = player(helper, helper.absolutePos(new BlockPos(6, 2, 6)));
		ServerPlayer nearby = player(helper, helper.absolutePos(new BlockPos(8, 2, 8)));
		Villager builder = builder(helper);

		BuildSite takeDown = site(helper, owner.getUUID(), StarterBlueprints.BERRY_FARM.id(), new BlockPos(1, 2, 1));
		takeDown.setDeconstruction();
		Builders.finish(level, builder, takeDown);
		helper.assertTrue(!done(owner, farm), "taking down a berry farm awarded its owner built/berry_farm");

		BuildSite away = site(helper, UUID.randomUUID(), StarterBlueprints.BERRY_FARM.id(), new BlockPos(1, 2, 1));
		away.setDeconstruction();
		Builders.finish(level, builder, away);
		helper.assertTrue(!done(nearby, farm) && !done(owner, farm), "a take-down with its owner offline awarded the players nearby built/berry_farm");

		Builders.finish(level, builder, site(helper, owner.getUUID(), StarterBlueprints.BERRY_FARM.id(), new BlockPos(1, 2, 1)));
		helper.assertTrue(done(owner, farm), "a plain build of the berry farm didn't award built/berry_farm (the setup can't award)");
		LOGGER.info("qa built 1116b take-down: two deconstructions awarded nothing; a plain build did");
		helper.succeed();
	}

	/** The same blueprint finished twice: no error, still earned, and the second award gives it to nobody new. */
	//$ gametest_batch AREA '"qaBuilt1116bTwice"'
	@GameTest(template = AREA, batch = "qaBuilt1116bTwice")
	public void finishingTheSameBlueprintTwiceIsHarmless(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder lab = adv(helper, "built/research_lab");
		ServerPlayer owner = player(helper, helper.absolutePos(new BlockPos(6, 2, 6)));
		Villager builder = builder(helper);
		BuildSite first = site(helper, owner.getUUID(), StarterBlueprints.RESEARCH_LAB.id(), new BlockPos(1, 2, 1));
		BuildSite second = site(helper, owner.getUUID(), StarterBlueprints.RESEARCH_LAB.id(), new BlockPos(1, 2, 9));

		Builders.finish(level, builder, first);
		helper.assertTrue(done(owner, lab), "the first research lab didn't award built/research_lab");
		Builders.finish(level, builder, second);
		helper.assertTrue(done(owner, lab), "finishing a second research lab took built/research_lab away");
		helper.assertTrue(owner.getAdvancements().getOrStartProgress(lab).getCriterion(BuiltAdvancements.CRITERION).isDone(),
			"built/research_lab's 'built' criterion isn't done");
		int again = BuiltAdvancements.award(level, second);
		helper.assertTrue(again == 0, "awarding built/research_lab a third time counted " + again + " players");
		LOGGER.info("qa built 1116b twice: two finishes, still done, a third award counted {}", again);
		helper.succeed();
	}

	/**
	 * A site saved mid-job (to bytes and back, as a world save) still awards its owner when it finishes; a repair saved
	 * and reloaded still awards nothing.
	 */
	//$ gametest_batch AREA '"qaBuilt1116bReload"'
	@GameTest(template = AREA, batch = "qaBuilt1116bReload")
	public void reloadedSiteStillAwardsOnFinish(GameTestHelper helper) {
		begin(helper);
		ServerLevel level = helper.getLevel();
		AdvancementHolder storehouse = adv(helper, "built/storehouse");
		ServerPlayer owner = player(helper, helper.absolutePos(new BlockPos(6, 2, 6)));
		Villager builder = builder(helper);

		BuildSite repair = site(helper, owner.getUUID(), StarterBlueprints.STOREHOUSE.id(), new BlockPos(1, 2, 1));
		repair.setRepair();
		BuildSite repairLoaded = BuildSite.load(roundTrip(repair.save()));
		helper.assertTrue(repairLoaded.isRepair(), "a repair site forgot it was a repair through a save");
		Builders.finish(level, builder, repairLoaded);
		helper.assertTrue(!done(owner, storehouse), "a reloaded repair finishing awarded built/storehouse");

		BuildSite site = site(helper, owner.getUUID(), StarterBlueprints.STOREHOUSE.id(), new BlockPos(1, 2, 1));
		BuildSite loaded = BuildSite.load(roundTrip(site.save()));
		helper.assertTrue(owner.getUUID().equals(loaded.owner()) && loaded.structure().equals(StarterBlueprints.STOREHOUSE.id()),
			"the reloaded site lost its owner or blueprint: " + loaded.owner() + " " + loaded.structure());
		Builders.finish(level, builder, loaded);
		helper.assertTrue(done(owner, storehouse), "a site saved and reloaded mid-job didn't award built/storehouse when it finished");
		LOGGER.info("qa built 1116b reload: reloaded repair awarded nothing, reloaded build awarded its owner");
		helper.succeed();
	}

	private static CompoundTag roundTrip(CompoundTag tag) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			NbtIo.write(tag, new DataOutputStream(bytes));
			return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())), NbtAccounter.unlimitedHeap());
		} catch (IOException e) {
			throw new GameTestAssertException("couldn't round-trip a site's save: " + e);
		}
	}

	/** Every blueprint StarterBlueprints ships (and every look) has its built advancement: hidden, quiet, one "built" criterion. */
	//$ gametest_batch EMPTY_STRUCTURE '"qaBuilt1116bFiles"'
	@GameTest(template = EMPTY_STRUCTURE, batch = "qaBuilt1116bFiles")
	public void everyShippedBlueprintHasItsAdvancement(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Set<ResourceLocation> ids = new LinkedHashSet<>();
		for (Field f : StarterBlueprints.class.getDeclaredFields()) {
			if (!Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			try {
				f.setAccessible(true);
				Object value = f.get(null);
				if (value instanceof StarterBlueprints.Entry e) {
					ids.add(e.id());
				} else if (value instanceof List<?> list) {
					for (Object o : list) {
						if (o instanceof StarterBlueprints.Entry e) {
							ids.add(e.id());
						}
					}
				}
			} catch (ReflectiveOperationException e) {
				throw new GameTestAssertException("can't read StarterBlueprints." + f.getName() + ": " + e);
			}
		}
		helper.assertTrue(ids.size() > 50, "only " + ids.size() + " StarterBlueprints entries found");
		List<String> bad = new ArrayList<>();
		for (ResourceLocation id : ids) {
			var holder = BuiltAdvancements.advancementFor(level, id);
			if (holder.isEmpty()) {
				bad.add(id + ": none");
				continue;
			}
			var adv = holder.get().value();
			boolean quiet = adv.display().map(d -> d.isHidden() && !d.shouldShowToast() && !d.shouldAnnounceChat()).orElse(false);
			if (!quiet) {
				bad.add(id + ": not hidden and quiet");
			}
			if (!adv.criteria().containsKey(BuiltAdvancements.CRITERION) || adv.criteria().size() != 1) {
				bad.add(id + ": criteria " + adv.criteria().keySet());
			}
		}
		helper.assertTrue(bad.isEmpty(), bad.size() + " shipped blueprints without a proper built advancement: " + bad);
		LOGGER.info("qa built 1116b files: {} StarterBlueprints ids each have a hidden, quiet built advancement", ids.size());
		helper.succeed();
	}
}

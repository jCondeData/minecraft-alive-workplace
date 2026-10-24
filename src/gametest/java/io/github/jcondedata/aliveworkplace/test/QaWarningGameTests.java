package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.guard.BattleStations;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.guard.Warnings;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Culture;
import io.github.jcondedata.aliveworkplace.threat.Sieges;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane D, 2026-10-24) for a day's warning (ROADMAP 32.14), from its spec: "a guard on the night watch whose post
 * is within 24 blocks of a finished Lookout Tower or Wall Tower"; "players in the village, and the hall's owner
 * wherever they are online, are told at once". WarningGameTests builds the message and uses a Wall Tower with the guard
 * close by; these use a Lookout Tower (and its upgrade, still a Lookout Tower), put the guard's post exactly 24 blocks
 * from the tower's middle and then 25, and check who actually hears the line: a player in the village and the owner far
 * off do, once each; a stranger far off doesn't.
 */
public class QaWarningGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation BESIEGERS = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa_warned_besiegers");
	private static final BlockPos HALL = new BlockPos(15, 2, 12);
	private static final long BASE = 10 * VillageNeeds.DAY;
	private static final double NORTH = -Math.PI / 2;

	/** A hall (radius 24) whose clock is clear, with the test's culture loaded; all put back at the end. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 24;
		VillageRaids.ENABLED = true; // (the after() puts the config back)
		BlockPos hall = helper.absolutePos(HALL);
		Culture culture = Culture.read(BESIEGERS, com.google.gson.JsonParser.parseString("{\"where\": {\"min_villagers\": 100000}, \"roster\": ["
			+ "{\"entity\": \"minecraft:husk\", \"share\": 100, \"role\": \"melee\"}]}").getAsJsonObject());
		Map<ResourceLocation, Culture> loaded = new java.util.LinkedHashMap<>();
		Threats.all().forEach(c -> loaded.put(c.id(), c));
		Map<ResourceLocation, Culture> with = new java.util.LinkedHashMap<>(loaded);
		with.put(BESIEGERS, culture);
		Threats.setForTest(with);
		Leftovers.after(helper, () -> {
			Threats.setForTest(loaded);
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(48), m -> m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			Sieges.siege(level, hall).ifPresent(s -> Sieges.lift(level, s));
			VillageRaids.forget(hall);
			ThreatData.get(level).forgetSiege(hall);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			Sieges.forget();
			BattleStations.forget();
			VillageHalls.RADIUS = radius;
			level.setDayTime(time);
			new WorkplaceConfig().apply();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity, "no hall");
		ThreatData.get(level).forgetClock(hall);
		return hall;
	}

	/** {@code build} placed and recorded finished at {@code at}, as a builder leaves it. */
	private static void finished(GameTestHelper helper, StarterBlueprints.Entry build, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(at);
		level.getStructureManager().get(build.id()).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), origin, Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		sites.recordFinished(build.id(), placement, UUID.randomUUID());
		Leftovers.after(helper, () -> sites.forgetFinished(placement));
	}

	/** An archer whose own Guard Post is at {@code post} (absolute), standing two blocks north of it. */
	private static Villager archer(GameTestHelper helper, BlockPos post) {
		ServerLevel level = helper.getLevel();
		level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
		Villager v = EntityType.VILLAGER.create(level);
		v.moveTo(post.getX() + 0.5, post.getY(), post.getZ() - 1.5, 0f, 0f);
		level.addFreshEntity(v);
		Leftovers.after(helper, v::discard);
		Jobs.employ(level, v, post, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		v.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		v.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BOW));
		return v;
	}

	/** Dusk of day 11: the clock sets tomorrow night's attack from the north. */
	private static void setAttack(ServerLevel level, BlockPos hall) {
		level.setDayTime(BASE + 12100);
		ThreatData.get(level).rolled(hall, 11, new ThreatData.Attack(12, BESIEGERS, NORTH, 13600));
	}

	/** The middle of the village's only tower, as the warning measures from it. */
	private static BlockPos middle(GameTestHelper helper, BlockPos hall) {
		List<BlockPos> towers = Warnings.towers(helper.getLevel(), hall);
		helper.assertTrue(towers.size() == 1, "the village's towers: " + towers);
		return towers.get(0);
	}

	/**
	 * A Lookout Tower with an archer whose post is exactly 24 blocks from its middle: a source (the reach is "within 24
	 * blocks"). The same with the post 25 blocks off: none, and the round warns nobody.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWarningReach"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWarningReach")
	public void aPostTwentyFourBlocksFromALookoutTowerWatchesTwentyFiveDoesNot(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		finished(helper, StarterBlueprints.LOOKOUT_TOWER, new BlockPos(0, 2, 0));
		BlockPos middle = middle(helper, hall);
		setAttack(level, hall);
		BlockPos at = new BlockPos(middle.getX() + 24, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), middle.getZ());
		Villager archer = archer(helper, at);
		helper.assertTrue(Warnings.source(level, hall).isPresent(), "an archer posted 24 blocks from the Lookout Tower isn't a source");
		helper.assertTrue(Warnings.source(level, hall).get().who().getString().equals(archer.getName().getString()),
			"the source isn't the archer: " + Warnings.source(level, hall).get().who().getString());
		// Moved one block further: out of reach.
		level.setBlockAndUpdate(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		archer.discard();
		BlockPos far = at.east();
		archer(helper, far);
		helper.assertTrue(Warnings.source(level, hall).isEmpty(), "an archer posted 25 blocks from the Lookout Tower is a source: "
			+ Warnings.source(level, hall).map(s -> s.who().getString()));
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(hall) < 12, "warned with the post out of reach: " + ThreatData.get(level).warned(hall));
		helper.succeed();
	}

	/** The Lookout Tower's upgrade is still a Lookout Tower: an archer posted by it watches. */
	//$ gametest_ticks_batch AREA '60' '"qaWarningUpgrade"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWarningUpgrade")
	public void anUpgradedLookoutTowerStillWatches(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		finished(helper, StarterBlueprints.LOOKOUT_TOWER_2, new BlockPos(0, 2, 0));
		BlockPos middle = middle(helper, hall);
		setAttack(level, hall);
		archer(helper, new BlockPos(middle.getX() + 8, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), middle.getZ()));
		helper.assertTrue(Warnings.source(level, hall).isPresent(), "an archer by an upgraded Lookout Tower isn't a source");
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "not warned: " + ThreatData.get(level).warned(hall));
		helper.succeed();
	}

	/**
	 * Who hears the warning: a player in the village and the hall's owner 300 blocks off are each told once, in gold,
	 * the line the warning makes; a stranger 300 blocks off the other way hears nothing.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaWarningHeard"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaWarningHeard")
	public void thePlayersInTheVillageAndTheFarOwnerAreToldAStrangerFarOffIsNot(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		finished(helper, StarterBlueprints.LOOKOUT_TOWER, new BlockPos(0, 2, 0));
		BlockPos middle = middle(helper, hall);
		archer(helper, new BlockPos(middle.getX() + 8, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), middle.getZ()));
		List<Component> ownerHeard = new ArrayList<>();
		List<Component> nearHeard = new ArrayList<>();
		List<Component> strangerHeard = new ArrayList<>();
		ServerPlayer owner = RealmGameTests.listener(helper, ownerHeard, "qa-warn-owner");
		ServerPlayer near = RealmGameTests.listener(helper, nearHeard, "qa-warn-near");
		ServerPlayer stranger = RealmGameTests.listener(helper, strangerHeard, "qa-warn-far");
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setOwner(owner.getUUID(), owner.getGameProfile().getName());
		owner.setPos(hall.getX() + 300.5, hall.getY(), hall.getZ() + 0.5);
		near.setPos(hall.getX() + 3.5, hall.getY(), hall.getZ() + 3.5);
		stranger.setPos(hall.getX() - 300.5, hall.getY(), hall.getZ() + 0.5);
		setAttack(level, hall);
		ownerHeard.clear();
		nearHeard.clear();
		strangerHeard.clear();
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(ThreatData.get(level).warned(hall) == 12, "not warned: " + ThreatData.get(level).warned(hall));
		String line = Warnings.message(level, hall, ThreatData.get(level).attacks(hall).get(0), 0).getString();
		helper.assertTrue(line.startsWith("Smoke on the north road: ") && line.endsWith("tomorrow night."), "the warning: " + line);
		for (var who : List.of(java.util.Map.entry("the player in the village", nearHeard), java.util.Map.entry("the owner 300 blocks off", ownerHeard))) {
			List<Component> said = who.getValue().stream().filter(c -> c.getString().equals(line)).toList();
			helper.assertTrue(said.size() == 1, who.getKey() + " was told " + said.size() + " times: " + who.getValue().stream().map(Component::getString).toList());
			helper.assertTrue(said.get(0).getStyle().getColor() != null && said.get(0).getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.GOLD)),
				who.getKey() + "'s line isn't gold: " + said.get(0).getStyle());
		}
		helper.assertTrue(strangerHeard.stream().noneMatch(c -> c.getString().equals(line)), "a stranger 300 blocks off was told: " + strangerHeard);
		// A second round tells nobody again.
		Warnings.round(level, hall, 10, 1);
		helper.assertTrue(ownerHeard.stream().filter(c -> c.getString().equals(line)).count() == 1, "the owner was told twice");
		helper.succeed();
	}
}

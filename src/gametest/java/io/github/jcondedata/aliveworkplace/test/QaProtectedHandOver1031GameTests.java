package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.farm.FieldData;
import io.github.jcondedata.aliveworkplace.farm.Fields;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.mc.Ids;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (2026-10-31), B718 from the spec. Guide village-protection.md: inside a protected village "only the owner, their
 * friends and operators may break or place blocks", and "a blueprint, Quarry Marker or Field Marker whose area reaches
 * into the village is refused, even when its corners were clicked outside it. Anyone else is told whose village it is."
 * The area is "the Village Hall's reach, at any height". The village is {@link ProtectionSpecGameTests#protectedHall}'s:
 * hall at (11, 2, 11), reach 16, owned by "Owner". Everything goes through the player's own clicks and commands.
 */
public class QaProtectedHandOver1031GameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");
	private static final String DENIED_TAIL = "'s village: only they and their friends can change things here";

	private static List<String> said(QaStageFiveGameTests.Client c) {
		return c.seen().stream().map(Component::getString).toList();
	}

	/** A builder (no AI unless {@code ai}) employed at a table at {@code benchAt}. */
	private static Villager builder(GameTestHelper helper, BlockPos benchAt, boolean ai) {
		helper.setBlock(benchAt, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = helper.spawn(EntityType.VILLAGER, benchAt.south());
		builder.setNoAi(!ai);
		Builders.employ(helper.getLevel(), builder, helper.absolutePos(benchAt));
		return builder;
	}

	/** The test hut (5 x 5) placed with its origin at {@code at} (relative), put in {@code c}'s hand. */
	private static ItemStack hutInHand(GameTestHelper helper, QaStageFiveGameTests.Client c, BlockPos at) {
		ServerLevel level = helper.getLevel();
		ItemStack blueprint = BlueprintItem.create(TEST_HUT, BlueprintLibrary.get(level, TEST_HUT).orElseThrow().size());
		blueprint.set(ModComponents.BLUEPRINT, BlueprintItem.data(blueprint).orElseThrow()
			.withPlacement(Optional.of(new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(at), Rotation.NONE, Mirror.NONE))));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, blueprint);
		return blueprint;
	}

	/** Hands the hut at {@code at} to a fresh builder at a table at {@code benchAt}; the site taken (removed again), or null. */
	private static BuildSite handHut(GameTestHelper helper, QaStageFiveGameTests.Client c, BlockPos benchAt, BlockPos at) {
		ServerLevel level = helper.getLevel();
		Villager builder = builder(helper, benchAt, false);
		hutInHand(helper, c, at);
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		BuildSite site = Builders.activeSite(level, builder);
		if (site != null) {
			BuildSiteManager.get(level).remove(site.id());
		}
		builder.discard();
		return site;
	}

	/** A farmer at a composter at (22, 2, 26) handed a field from (25, 2) to (28, 20) (its column x = 25, z = 11 is 14 from the hall). */
	private static boolean handField(GameTestHelper helper, QaStageFiveGameTests.Client c) {
		ServerLevel level = helper.getLevel();
		BlockPos composterAt = new BlockPos(22, 2, 26);
		helper.setBlock(composterAt, Blocks.COMPOSTER);
		Villager farmer = helper.spawn(EntityType.VILLAGER, composterAt.west());
		farmer.setNoAi(true);
		Jobs.employ(level, farmer, helper.absolutePos(composterAt), PoiTypes.FARMER, VillagerProfession.FARMER);
		ItemStack marker = new ItemStack(ModItems.FIELD_MARKER);
		marker.set(ModComponents.FIELD, new FieldData(Optional.of(Ids.of(level.dimension())), Optional.of(helper.absolutePos(new BlockPos(25, 1, 2))),
			Optional.of(helper.absolutePos(new BlockPos(28, 1, 20)))));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(c.player(), farmer, c.seen());
		boolean taken = Fields.hasField(farmer);
		farmer.discard();
		return taken;
	}

	/** A miner at a bench at (28, 2, 26) given a quarry marker from (25, 3) to (29, 19); the marker stack in hand. */
	private static Villager handQuarry(GameTestHelper helper, QaStageFiveGameTests.Client c, boolean ai) {
		ServerLevel level = helper.getLevel();
		BlockPos benchAt = new BlockPos(28, 2, 26);
		helper.setBlock(benchAt, ModBlocks.MINERS_BENCH);
		Villager miner = helper.spawn(EntityType.VILLAGER, benchAt.west());
		miner.setNoAi(!ai);
		Jobs.employ(level, miner, helper.absolutePos(benchAt), ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER);
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()), Optional.of(helper.absolutePos(new BlockPos(25, 1, 3))),
			Optional.of(helper.absolutePos(new BlockPos(29, 1, 19))), 4));
		c.player().setItemInHand(InteractionHand.MAIN_HAND, marker);
		QaStageSixGameTests.clickVillager(c.player(), miner, c.seen());
		return miner;
	}

	/**
	 * Guide: "only the owner, their friends and operators". The owner types {@code /workplace friend add} for a survival
	 * player who isn't an operator; that friend's build reaching 3 blocks into the village (x 24..28, z 9..13) is taken.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPho1031Friend"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPho1031Friend")
	public void qaAFriendsBuildReachingIntoTheVillageIsTaken(GameTestHelper helper) {
		QaStageFiveGameTests.Client owner = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoOwn"));
		ProtectionSpecGameTests.protectedHall(helper, owner.player());
		QaStageFiveGameTests.Client friend = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoFr"));
		helper.assertFalse(friend.player().hasPermissions(2), "the friend is an operator; the check would prove nothing");
		Leftovers.after(helper, () -> Friends.get(helper.getLevel().getServer()).remove(owner.player().getUUID(), friend.player().getUUID()));
		QaStageSixGameTests.profileCache(helper, owner.player(), friend.player());
		// before: as a stranger he's refused
		helper.assertTrue(handHut(helper, friend, new BlockPos(28, 2, 18), new BlockPos(24, 2, 9)) == null,
			"before being made a friend, a stranger's build reaching into the village was taken: " + said(friend));
		QaStageSixGameTests.command(owner, "/workplace friend add " + friend.player().getGameProfile().getName());
		helper.assertTrue(Friends.get(helper.getLevel().getServer()).mayDirect(owner.player().getUUID(), friend.player().getUUID()),
			"/workplace friend add didn't make him a friend: " + said(owner));
		friend.seen().clear();
		BuildSite site = handHut(helper, friend, new BlockPos(28, 2, 18), new BlockPos(24, 2, 9));
		helper.assertTrue(site != null, "the owner's friend's build reaching into the protected village was refused: " + said(friend));
		helper.succeed();
	}

	/** Guide: operators may build there. An operator who isn't the owner hands the same build: taken. */
	//$ gametest_ticks_batch AREA '60' '"qaPho1031Op"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPho1031Op")
	public void qaAnOperatorsBuildReachingIntoTheVillageIsTaken(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client op = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoOp"));
		var ops = helper.getLevel().getServer().getPlayerList().getOps();
		ops.add(new ServerOpListEntry(op.player().getGameProfile(), 4, false));
		Leftovers.after(helper, () -> ops.remove(op.player().getGameProfile()));
		helper.assertTrue(op.player().hasPermissions(2), "the operator isn't one");
		BuildSite site = handHut(helper, op, new BlockPos(28, 2, 18), new BlockPos(24, 2, 9));
		helper.assertTrue(site != null, "an operator's build reaching into a protected village was refused: " + said(op));
		helper.succeed();
	}

	/**
	 * The edge, from both sides. The area is the hall's reach (16) "across the map": the block 16 east of the hall,
	 * (27, 2, 11), is protected (a stranger can't break it) and the one 17 east, (28, 2, 11), isn't. A stranger's hut
	 * whose west edge is x = 27 (x 27..31, z 9..13) reaches that last protected column: refused. One block further out
	 * (x 28..32): taken.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPho1031Edge"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPho1031Edge")
	public void qaTheEdgeOfTheVillageIsTheSameForBuildsAsForBlocks(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoEdge"));
		// the block rule's edge, as the stranger finds it with his own hands
		BlockPos last = new BlockPos(27, 2, 11);
		BlockPos outside = new BlockPos(28, 2, 11);
		helper.setBlock(last, Blocks.STONE);
		helper.setBlock(outside, Blocks.STONE);
		c.player().gameMode.destroyBlock(helper.absolutePos(last));
		c.player().gameMode.destroyBlock(helper.absolutePos(outside));
		helper.assertBlockPresent(Blocks.STONE, last);
		helper.assertBlockPresent(Blocks.AIR, outside);
		c.seen().clear();

		BuildSite onEdge = handHut(helper, c, new BlockPos(29, 2, 20), new BlockPos(27, 2, 9));
		helper.assertTrue(onEdge == null, "a stranger's build reaching the village's last protected column (16 from the hall) was taken: " + said(c));
		helper.assertTrue(said(c).stream().anyMatch(s -> s.endsWith("Owner" + DENIED_TAIL)), "the refusal at the edge didn't say whose village: " + said(c));
		c.seen().clear();
		BuildSite justOut = handHut(helper, c, new BlockPos(29, 2, 20), new BlockPos(28, 2, 9));
		helper.assertTrue(justOut != null, "a stranger's build starting one block outside the village (17 from the hall) was refused: " + said(c));
		helper.succeed();
	}

	/**
	 * A refused stranger loses nothing and nothing is touched: he still holds his blueprint (with its placement), and his
	 * builder, left to work for 200 ticks with free materials, has no site and the owner's wall inside the village stands.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaPho1031KeepsBlueprint"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPho1031KeepsBlueprint")
	public void qaARefusedStrangerKeepsHisBlueprintAndNothingIsBroken(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		ServerLevel level = helper.getLevel();
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(true, level.getServer());
		Leftovers.after(helper, () -> {
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
		});
		helper.setDayTime(2000);
		BlockPos wall = new BlockPos(25, 3, 11);
		helper.setBlock(wall, Blocks.COBBLESTONE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoKeep"));
		Villager builder = builder(helper, new BlockPos(28, 2, 18), true);
		ItemStack blueprint = hutInHand(helper, c, new BlockPos(24, 2, 9));
		QaStageSixGameTests.clickVillager(c.player(), builder, c.seen());
		ItemStack held = c.player().getMainHandItem();
		helper.assertTrue(held.is(ModItems.BLUEPRINT) && held.getCount() == 1, "the refused stranger's blueprint is gone: he holds " + held);
		helper.assertTrue(BlueprintItem.data(held).flatMap(BlueprintData::placement).equals(BlueprintItem.data(blueprint).flatMap(BlueprintData::placement)),
			"the refused blueprint lost its placement");
		helper.runAfterDelay(200, () -> {
			BuildSite site = Builders.activeSite(level, builder);
			if (site != null) {
				BuildSiteManager.get(level).remove(site.id());
			}
			helper.assertTrue(site == null, "200 ticks after the refusal the stranger's builder has a site (" + (site == null ? "" : site.stage()) + "): " + said(c));
			helper.assertBlockPresent(Blocks.COBBLESTONE, wall);
			helper.succeed();
		});
	}

	/** The same for a refused Quarry Marker: he keeps it (with both corners), his miner has no quarry, the wall stands. */
	//$ gametest_ticks_batch AREA '300' '"qaPho1031KeepsQuarry"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaPho1031KeepsQuarry")
	public void qaARefusedStrangerKeepsHisQuarryMarkerAndNothingIsDug(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos wall = new BlockPos(26, 2, 11);
		helper.setBlock(wall, Blocks.COBBLESTONE);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoKeepQ"));
		Villager miner = handQuarry(helper, c, true);
		ItemStack held = c.player().getMainHandItem();
		helper.assertTrue(held.is(ModItems.QUARRY_MARKER) && held.getCount() == 1, "the refused stranger's Quarry Marker is gone: he holds " + held);
		QuarryData data = held.get(ModComponents.QUARRY);
		helper.assertTrue(data != null && data.first().isPresent() && data.second().isPresent(), "the refused Quarry Marker lost its corners: " + data);
		helper.runAfterDelay(200, () -> {
			QuarrySite site = Miners.activeSite(level, miner);
			if (site != null) {
				Miners.cancel(level, site);
			}
			helper.assertTrue(site == null, "200 ticks after the refusal the stranger's miner has a quarry: " + said(c));
			helper.assertBlockPresent(Blocks.COBBLESTONE, wall);
			helper.succeed();
		});
	}

	/**
	 * The words the refused stranger reads, for a build and a quarry: exactly en_us's "%s is %s's village: only they and
	 * their friends can change things here" with the village's name and "Owner", in red, once per refusal.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPho1031Message"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPho1031Message")
	public void qaTheRefusalNamesTheVillageAndItsOwner(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		ServerLevel level = helper.getLevel();
		String village = VillageHalls.name(level, helper.absolutePos(new BlockPos(11, 2, 11))).getString();
		helper.assertFalse(village.isBlank(), "the village has no name");
		String expected = village + " is Owner" + DENIED_TAIL;
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoMsg"));
		handHut(helper, c, new BlockPos(28, 2, 18), new BlockPos(24, 2, 9));
		helper.assertTrue(said(c).equals(List.of(expected)), "the build's refusal read " + said(c) + ", expected [" + expected + "]");
		helper.assertTrue(c.seen().get(0).getStyle().getColor() != null
			&& c.seen().get(0).getStyle().getColor().equals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.RED)), "the refusal isn't red");
		c.seen().clear();
		Villager miner = handQuarry(helper, c, false);
		QuarrySite site = Miners.activeSite(level, miner);
		if (site != null) {
			Miners.cancel(level, site);
		}
		helper.assertTrue(said(c).equals(List.of(expected)), "the quarry's refusal read " + said(c) + ", expected [" + expected + "]");
		helper.succeed();
	}

	/** Guide: "with Village Protection off in the settings, nobody can protect a village": a stranger's field reaching in is taken. */
	//$ gametest_ticks_batch AREA '60' '"qaPho1031FieldConfigOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPho1031FieldConfigOff")
	public void qaAStrangersFieldIsTakenWithProtectionOffOnTheServer(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		VillageProtection.ENABLED = false; // put back by protectedHall
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoCfg"));
		helper.assertTrue(handField(helper, c), "with village protection off on the server, a stranger's field was refused: " + said(c));
		helper.succeed();
	}

	/**
	 * Guide: "Shift-click again to open it up". The owner turns his village's protection off (the hall's toggle); a
	 * stranger's field reaching in is then taken, and after the owner turns it on again the same field is refused.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPho1031FieldHallOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPho1031FieldHallOff")
	public void qaAStrangersFieldIsTakenOnceTheOwnerOpensHisVillage(GameTestHelper helper) {
		QaStageFiveGameTests.Client owner = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoTog"));
		VillageHallBlockEntity hall = ProtectionSpecGameTests.protectedHall(helper, owner.player());
		ServerLevel level = helper.getLevel();
		BlockPos hallAt = helper.absolutePos(new BlockPos(11, 2, 11));
		VillageProtection.toggle(level, hallAt, owner.player());
		helper.assertFalse(hall.isProtected(), "the owner's toggle didn't open the village");
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoTogS"));
		helper.assertTrue(handField(helper, c), "with the village opened by its owner, a stranger's field was refused: " + said(c));
		VillageProtection.toggle(level, hallAt, owner.player());
		helper.assertTrue(hall.isProtected(), "the owner's toggle didn't protect the village again");
		c.seen().clear();
		helper.assertFalse(handField(helper, c), "with the village protected again, a stranger's field was taken: " + said(c));
		helper.succeed();
	}

	/**
	 * Two villages: an unprotected one whose hall stands by the field's far corner, (27, 2, 24), and the protected one
	 * the field reaches into. The stranger's field is refused, naming the protected village's owner.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPho1031TwoVillages"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPho1031TwoVillages")
	public void qaAnOpenVillageNearTheClickDoesntLetAFieldIntoAProtectedOne(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoTwo"));
		BlockPos openAt = new BlockPos(27, 2, 24);
		helper.setBlock(openAt, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity open = helper.getBlockEntity(openAt);
		open.setOwner(c.player().getUUID(), c.player().getGameProfile().getName());
		helper.assertFalse(open.isProtected(), "a new hall starts protected");
		helper.assertFalse(handField(helper, c), "a stranger's field reaching into a protected village was taken because an open village stands by its corner: " + said(c));
		helper.assertTrue(said(c).stream().anyMatch(s -> s.endsWith("Owner" + DENIED_TAIL)), "the refusal didn't name the protected village's owner: " + said(c));
		helper.succeed();
	}

	/**
	 * Two protected villages: the stranger's own, whose hall stands by the field's far corner, and Owner's, which the
	 * field reaches into. His own village doesn't let him into Owner's: refused, naming Owner.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaPho1031OwnVillage"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaPho1031OwnVillage")
	public void qaHisOwnProtectedVillageNearTheClickDoesntLetAFieldIntoAnothers(GameTestHelper helper) {
		ProtectionSpecGameTests.protectedHall(helper, ProtectionSpecGameTests.player(helper));
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPhoOwnV"));
		BlockPos ownAt = new BlockPos(27, 2, 24);
		helper.setBlock(ownAt, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity own = helper.getBlockEntity(ownAt);
		own.setOwner(c.player().getUUID(), c.player().getGameProfile().getName());
		own.setProtected(true);
		Leftovers.after(helper, () -> own.setProtected(false));
		helper.assertFalse(handField(helper, c), "a stranger's field reaching into Owner's protected village was taken because his own village stands by its corner: " + said(c));
		helper.assertTrue(said(c).stream().anyMatch(s -> s.endsWith("Owner" + DENIED_TAIL)), "the refusal didn't name Owner: " + said(c));
		helper.succeed();
	}
}

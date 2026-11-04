package io.github.jcondedata.aliveworkplace.compat;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprint;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintLibrary;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.Arenas;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.Decorations;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trainer.MegaBracelets;
import io.github.jcondedata.aliveworkplace.trainer.MegaShrines;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;

/**
 * QA from the player guide's Mega Shrine page (docs/guide/mega-shrine.md, ROADMAP 38.7) with the real Mega Showdown,
 * lane-d-1104-2209: what the page promises that no test checked yet. 7 by 6 by 7 with the blocks it names and beauty 2;
 * the show only in the festival's first hour and a half (both edges); a trainer who can Mega Evolve first, else the
 * nearest; the players near read it in chat (named), far ones don't; within 12 blocks of the pedestal, not farther; the
 * mood lasts as long as a festival's; the crowd stays for the show's hours, then the square has it again; villageMegas
 * off leaves a built shrine as it is.
 */
public class QaMegaShrine1104d4CompatTests implements FabricGameTest {
	private static final String AREA = CompatGameTests.AREA;
	/** The shrine's origin: its pedestal at (5, 4, 8), its trainer's spot at (5, 4, 7), the crowd's at (5, 2, 1). */
	private static final BlockPos ORIGIN = new BlockPos(2, 1, 5);
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos STAGE = new BlockPos(5, 4, 7);
	private static final BlockPos CROWD = new BlockPos(5, 2, 2);

	private static BlueprintData.Placement shrine(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(ORIGIN), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.MEGA_SHRINE.id(), placement, UUID.randomUUID());
		level.setBlockAndUpdate(Arenas.world(placement, MegaShrines.PEDESTAL_SPOT), BuiltInRegistries.BLOCK.get(MegaShrines.PEDESTAL).defaultBlockState());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		long time = level.getDayTime();
		Festivals.forget();
		MegaShrines.forgetShows(level);
		level.setDayTime(base(level) + Festivals.START + 100);
		PartnerShowsCompatTests.after(helper, () -> {
			BuildSiteManager.get(level).forgetFinished(placement);
			VillageHalls.RADIUS = radius;
			MegaBracelets.ENABLED = true;
			Festivals.forget();
			MegaShrines.forgetShows(level);
			level.setDayTime(time);
			for (Villager v : level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(20))) {
				v.discard();
			}
		});
		return placement;
	}

	/** The start of today (its tick 0). */
	private static long base(ServerLevel level) {
		return (Chronicle.day(level) - 1) * 24000L;
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, int trainerLevel) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		if (trainerLevel > 0) {
			v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.TRAINER).setLevel(trainerLevel));
		}
		v.setNoAi(true);
		v.setNoGravity(true);
		return v;
	}

	private static void moveTo(GameTestHelper helper, Villager v, BlockPos at) {
		v.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(at)));
	}

	/** A player whose chat lines are kept in {@code seen}, at {@code at} (absolute); removed after the test. */
	private static ServerPlayer listener(GameTestHelper helper, String name, Vec3 at, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(level, at.x, at.y, at.z, 0, 0);
		PartnerShowsCompatTests.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	private static boolean sawShow(ServerLevel level, Villager v) {
		return Long.valueOf(Chronicle.day(level)).equals(ModAttachments.MEGA_SHOW_DAY.get(v));
	}

	/**
	 * The build: 7 by 6 by 7; Mega Showdown's pedestal on a chiseled meteorite plinth, Mega meteorite bricks, deepslate
	 * brick steps and walls, chiseled meteorite caps crowned with amethyst, soul lanterns; a decoration of beauty 2.
	 */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theShrineIsSevenBySixBySevenWithItsBlocksAndBeautyTwo(GameTestHelper helper) {
		Blueprint bp = BlueprintLibrary.get(helper.getLevel(), MegaShrines.SHRINE).orElse(null);
		helper.assertTrue(bp != null, "the Mega Shrine doesn't load");
		helper.assertTrue(bp.size().getX() == 7 && bp.size().getY() == 6 && bp.size().getZ() == 7, "the shrine's size: " + bp.size());
		Set<String> ids = bp.blocks().stream().map(e -> BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).toString()).collect(Collectors.toSet());
		List<String> missing = new ArrayList<>();
		for (String want : List.of("mega_showdown:pedestal", "mega_showdown:chiseled_mega_meteorid_block", "mega_showdown:mega_meteorid_brick",
			"minecraft:deepslate_brick_stairs", "minecraft:deepslate_brick_wall", "minecraft:amethyst_cluster", "minecraft:soul_lantern")) {
			if (!ids.contains(want)) {
				missing.add(want);
			}
		}
		helper.assertTrue(missing.isEmpty(), "the guide's blocks missing from the shrine: " + missing + " (it has " + ids + ")");
		// The pedestal stands on the chiseled meteorite plinth.
		BlockPos pedestal = bp.blocks().stream().filter(e -> BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).toString().equals("mega_showdown:pedestal"))
			.map(Blueprint.Entry::pos).findFirst().orElseThrow();
		String under = bp.blocks().stream().filter(e -> e.pos().equals(pedestal.below())).map(e -> BuiltInRegistries.BLOCK.getKey(e.state().getBlock()).toString())
			.findFirst().orElse("air");
		helper.assertTrue(under.equals("mega_showdown:chiseled_mega_meteorid_block"), "under the pedestal: " + under);
		helper.assertTrue(Decorations.points(MegaShrines.SHRINE) == 2, "the shrine's beauty: " + Decorations.points(MegaShrines.SHRINE));
		helper.succeed();
	}

	/**
	 * For the festival's first hour and a half only: no show a tick before the festival begins, a show on its 1,499th
	 * tick, and none from tick 1,500 on (the crowd goes back to the square then).
	 */
	//$ gametest_batch AREA '"qa_shrine_hours_1104d4"'
	@GameTest(template = AREA, batch = "qa_shrine_hours_1104d4")
	public void theShowIsOnlyInTheFestivalsFirstHourAndAHalf(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, STAGE, 3);
		Villager watcher = villager(helper, CROWD, 0);
		level.setDayTime(base(level) + Festivals.START - 1);
		helper.assertTrue(!MegaShrines.tick(level, hall), "the shrine took the crowd before the festival began");
		helper.assertTrue(!sawShow(level, watcher), "a show before the festival began");
		level.setDayTime(base(level) + Festivals.START + 1500);
		helper.assertTrue(!MegaShrines.tick(level, hall), "the shrine still has the crowd an hour and a half in");
		helper.assertTrue(!sawShow(level, watcher), "a show an hour and a half in");
		level.setDayTime(base(level) + Festivals.START + 1499);
		helper.assertTrue(MegaShrines.tick(level, hall), "the shrine hasn't the crowd at the end of the hour and a half");
		helper.assertTrue(sawShow(level, watcher) && MegaShrines.mood(level, watcher) == 3, "no show at the end of the hour and a half");
		helper.succeed();
	}

	/**
	 * The trainer: one who can Mega Evolve (a Master) is called to the pedestal over a nearer plain trainer, and holds the
	 * show; the players near read it with that trainer's name, one far off doesn't. With no Master, the nearest climbs.
	 */
	//$ gametest_batch AREA '"qa_shrine_trainer_1104d4"'
	@GameTest(template = AREA, batch = "qa_shrine_trainer_1104d4")
	public void aTrainerWhoCanMegaEvolveFirstElseTheNearest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos pedestal = MegaShrines.pedestal(level, hall).orElseThrow();
		Villager plain = villager(helper, new BlockPos(6, 2, 3), 3); // nearer the pedestal, in the crowd
		plain.setCustomName(Component.literal("Bram"));
		Villager master = villager(helper, new BlockPos(4, 2, 0), 5); // farther, in the crowd
		master.setCustomName(Component.literal("Ace"));
		helper.assertTrue(plain.distanceToSqr(Vec3.atCenterOf(pedestal)) < master.distanceToSqr(Vec3.atCenterOf(pedestal)), "the plain trainer isn't the nearer");
		helper.assertTrue(MegaBracelets.canMegaEvolve(master) && !MegaBracelets.canMegaEvolve(plain), "the Master can't Mega Evolve, or the plain one can");
		Villager watcher = villager(helper, CROWD, 0);
		List<Component> near = new ArrayList<>();
		List<Component> far = new ArrayList<>();
		listener(helper, "Near", helper.absoluteVec(new Vec3(5.5, 2, 3.5)), near);
		listener(helper, "Far", Vec3.atBottomCenterOf(pedestal).add(100, 0, 0), far);
		MegaShrines.tick(level, hall);
		helper.assertTrue(!sawShow(level, watcher), "a show before the trainer was at the pedestal");
		var target = master.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
		helper.assertTrue(target.isPresent() && target.get().getTarget().currentBlockPosition().equals(helper.absolutePos(STAGE)),
			"the Master wasn't called to the pedestal: " + target + "; the plain one: " + plain.getBrain().getMemory(MemoryModuleType.WALK_TARGET));
		moveTo(helper, master, STAGE);
		MegaShrines.tick(level, hall);
		helper.assertTrue(sawShow(level, watcher), "the Master at the pedestal held no show");
		String line = "Ace Mega Evolves their ace at the Mega Shrine, and the village cheers!";
		helper.assertTrue(near.stream().anyMatch(c -> c.getString().equals(line)), "a player near read " + near.stream().map(Component::getString).toList());
		helper.assertTrue(far.stream().noneMatch(c -> c.getString().contains("Mega Shrine")), "a player 100 blocks off read " + far.stream().map(Component::getString).toList());
		// The next festival: no Master (Ace is a plain trainer now, in the crowd): the nearest, Bram, climbs.
		Festivals.forget();
		MegaShrines.forgetShows(level);
		ModAttachments.MEGA_SHOW_DAY.remove(watcher);
		near.clear();
		master.setVillagerData(master.getVillagerData().setLevel(3));
		moveTo(helper, master, new BlockPos(4, 2, 0));
		master.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		MegaShrines.tick(level, hall);
		var bram = plain.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
		helper.assertTrue(bram.isPresent() && bram.get().getTarget().currentBlockPosition().equals(helper.absolutePos(STAGE)),
			"the nearest trainer wasn't called to the pedestal: " + bram + "; the other: " + master.getBrain().getMemory(MemoryModuleType.WALK_TARGET));
		moveTo(helper, plain, STAGE);
		MegaShrines.tick(level, hall);
		helper.assertTrue(sawShow(level, watcher), "the nearest trainer held no show");
		helper.assertTrue(near.stream().anyMatch(c -> c.getString().startsWith("Bram Mega Evolves")), "a player near read " + near.stream().map(Component::getString).toList());
		helper.succeed();
	}

	/**
	 * Every villager watching within 12 blocks of the pedestal gets +3 mood: one off work 11 blocks off who never stood in
	 * the crowd does, one 14 blocks off doesn't. The mood lasts as long as a festival's good mood: still +3 on its last
	 * day, gone the day after.
	 */
	//$ gametest_batch AREA '"qa_shrine_range_1104d4"'
	@GameTest(template = AREA, batch = "qa_shrine_range_1104d4")
	public void withinTwelveBlocksOfThePedestalAndForAFestivalsMood(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		BlockPos pedestal = MegaShrines.pedestal(level, hall).orElseThrow();
		Villager trainer = villager(helper, STAGE, 3);
		Villager inside = villager(helper, new BlockPos(5, 2, 0), 0);
		Villager outside = villager(helper, new BlockPos(5, 2, 0), 0);
		inside.moveTo(Vec3.atBottomCenterOf(pedestal).add(0, -2, -11)); // 11 blocks in front of the pedestal
		outside.moveTo(Vec3.atBottomCenterOf(pedestal).add(0, -2, -14)); // 14 blocks
		MegaShrines.show(level, hall, pedestal, trainer, Set.of());
		helper.assertTrue(sawShow(level, inside) && MegaShrines.mood(level, inside) == 3, "a villager 11 blocks from the pedestal didn't watch: " + MegaShrines.mood(level, inside));
		helper.assertTrue(!sawShow(level, outside) && MegaShrines.mood(level, outside) == 0, "a villager 14 blocks from the pedestal watched");
		helper.assertTrue(!sawShow(level, trainer), "the trainer watched their own show");
		long today = Chronicle.day(level);
		ModAttachments.MEGA_SHOW_DAY.set(inside, today - Festivals.MOOD_DAYS);
		helper.assertTrue(MegaShrines.mood(level, inside) == 3, "the show's mood gone on a festival mood's last day");
		ModAttachments.MEGA_SHOW_DAY.set(inside, today - Festivals.MOOD_DAYS - 1);
		helper.assertTrue(MegaShrines.mood(level, inside) == 0, "the show's mood outlasts a festival's");
		helper.succeed();
	}

	/**
	 * Held once a festival; afterwards, for the rest of the show's hours, the shrine keeps the crowd (no second show); once
	 * the hour and a half is over the square has it again. A trainer off the pedestal later doesn't bring a second show.
	 */
	//$ gametest_batch AREA '"qa_shrine_after_1104d4"'
	@GameTest(template = AREA, batch = "qa_shrine_after_1104d4")
	public void afterTheShowTheSquareHasTheCrowdAgain(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		Villager trainer = villager(helper, STAGE, 3);
		Villager watcher = villager(helper, CROWD, 0);
		helper.assertTrue(MegaShrines.tick(level, hall) && sawShow(level, watcher), "no show");
		ModAttachments.MEGA_SHOW_DAY.remove(watcher);
		level.setDayTime(base(level) + Festivals.START + 1000);
		moveTo(helper, trainer, new BlockPos(6, 2, 4));
		helper.assertTrue(MegaShrines.tick(level, hall), "the crowd left the shrine before the show's hours were over");
		moveTo(helper, trainer, STAGE);
		MegaShrines.tick(level, hall);
		helper.assertTrue(!sawShow(level, watcher), "the show was held twice in one festival");
		level.setDayTime(base(level) + Festivals.START + MegaShrines.SHOW_TICKS);
		helper.assertTrue(!MegaShrines.tick(level, hall), "the crowd isn't sent back to the square after the show's hours");
		helper.succeed();
	}

	/**
	 * villageMegas off: the shrine isn't listed and holds no show, and a shrine already built stays as it is (its pedestal
	 * and its place among the finished builds); switched on again, it holds its show.
	 */
	//$ gametest_batch AREA '"qa_shrine_switch_1104d4"'
	@GameTest(template = AREA, batch = "qa_shrine_switch_1104d4")
	public void switchedOffABuiltShrineStaysAsItIs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlueprintData.Placement placement = shrine(helper);
		BlockPos hall = helper.absolutePos(HALL);
		villager(helper, STAGE, 3);
		Villager watcher = villager(helper, CROWD, 0);
		BlockPos pedestal = Arenas.world(placement, MegaShrines.PEDESTAL_SPOT);
		MegaBracelets.ENABLED = false;
		for (int i = 0; i < 3; i++) {
			helper.assertTrue(!MegaShrines.tick(level, hall), "the shrine took the crowd with villageMegas off");
		}
		helper.assertTrue(!sawShow(level, watcher), "a show with villageMegas off");
		helper.assertTrue(!BlueprintLibrary.list(level.getServer(), false).contains(MegaShrines.SHRINE), "the shrine is listed with villageMegas off");
		helper.assertTrue(MegaShrines.PEDESTAL.equals(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pedestal).getBlock())), "the built shrine's pedestal went with the switch");
		helper.assertTrue(BuildSiteManager.get(level).finishedInVillage(level, hall).stream().anyMatch(f -> f.placement().origin().equals(placement.origin())),
			"the built shrine was forgotten with the switch off");
		MegaBracelets.ENABLED = true;
		helper.assertTrue(MegaShrines.tick(level, hall) && sawShow(level, watcher), "switched on again, the built shrine held no show");
		helper.succeed();
	}
}

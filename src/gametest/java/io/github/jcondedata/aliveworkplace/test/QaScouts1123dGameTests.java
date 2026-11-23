package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mc.Nbt;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * QA lane D, 2026-11-23: Scouts (ROADMAP 32.12, B368, docs/wiki/scouts.md), the promises the earlier tests
 * (ScoutGameTests, QaScoutGameTests, QaScoutReportGameTests, B891's) leave open, written from the wiki page:
 * <ul>
 * <li>"A scout not home within a day (24,000 ticks) is lost: gone, with a line in the chronicle and chat", on both sides
 * of the day, and for a scout who fell on the way (lost once the day is out, under the name they set out with);</li>
 * <li>"If the camp was broken up while they were out they come home with nothing ('the camp was gone')", the camp gone
 * on the way out and on the way back with a look already taken;</li>
 * <li>"No room in the chest: it drops at the scout's feet";</li>
 * <li>"Nobody goes by night", on later days too (the clock past the first day);</li>
 * <li>"A click sends the nearest free scout ... one out already is named", through the page's own send, with the
 * action bar's words;</li>
 * <li>"never goes for a scout more than 8 blocks off", just past the 8, and only for a lair's band;</li>
 * <li>"Missing keys read as defaults" in {@code aliveworkplace_scouts}, and a look under way survives a load;</li>
 * <li>"whatever they held there comes back to you (or drops at your feet)", with a full inventory, and the action
 * bar's "... is a Scout now".</li>
 * </ul>
 */
public class QaScouts1123dGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos POST = new BlockPos(4, 2, 4);
	private static final BlockPos CHEST = new BlockPos(4, 2, 6);
	/** By the post (within the 3.5 blocks that count as home), so a scout coming home is home at once. */
	private static final BlockPos BY_POST = new BlockPos(5, 2, 4);

	/** A hall, a guard post and its chest, by day; what the test changes goes when it ends. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
		Expansions.openForTests = true;
		Scouts.forget(level);
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(POST, ModBlocks.GUARD_POST);
		helper.setBlock(CHEST, Blocks.CHEST);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(Mob.class, helper.getBounds().inflate(80),
				m -> m.getTags().contains(Lairs.TAG) || m.getTags().contains(BanditCamps.TAG) || m.getTags().contains(VillageRaids.TAG)).forEach(Mob::discard);
			VillageRaids.forget();
			Lairs.forget(level);
			Scouts.forget(level);
			ThreatData.get(level).forgetPast(hall);
			ThreatData.get(level).forgetClock(hall);
			VillageHalls.RADIUS = radius;
			Expansions.openForTests = open;
			level.setDayTime(time);
		});
		level.setDayTime(1000);
		return hall;
	}

	/** A guard of the post, named, a sword in hand; a scout with {@code spyglass}. Standing still. */
	private static Villager guard(GameTestHelper helper, String name, BlockPos at, boolean spyglass) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		villager.setNoAi(true);
		villager.setCustomName(Component.literal(name));
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		if (spyglass) {
			villager.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		}
		return villager;
	}

	/** A player near the hall whose chat and action bar lines go into {@code seen}; gone when the test ends. */
	private static ServerPlayer listener(GameTestHelper helper, List<String> seen) {
		ServerPlayer player = StationsSpecGameTests.listeningPlayer(helper, seen);
		BlockPos hall = helper.absolutePos(HALL);
		player.teleportTo(hall.getX() + 2.5, hall.getY(), hall.getZ() + 0.5);
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** A bandits' lair {@code dx} blocks east of the hall, on the ground; its captain (no AI) stands there. */
	private static Lairs.Lair lair(GameTestHelper helper, BlockPos hall, int dx) {
		ServerLevel level = helper.getLevel();
		int x = hall.getX() + dx;
		int z = hall.getZ();
		level.getChunk(x >> 4, z >> 4);
		BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
		Vindicator captain = EntityType.VINDICATOR.create(level);
		captain.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		captain.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
		captain.setNoAi(true);
		captain.setPersistenceRequired();
		captain.setInvulnerable(true);
		captain.addTag(Lairs.TAG);
		captain.addTag(Lairs.CAPTAIN_TAG);
		level.addFreshEntity(captain);
		Lairs.Lair lair = new Lairs.Lair(pos, hall, captain.getUUID(), Chronicle.day(level), Threats.BANDITS, 7, Chronicle.day(level), 0, 0, 3);
		Lairs.putForTest(level, lair);
		return lair;
	}

	/** A mission as {@code aliveworkplace_scouts} saves it. */
	private static CompoundTag mission(UUID scout, BlockPos hall, BlockPos lair, long started, String phase, long lookUntil, boolean seen, String name) {
		CompoundTag c = new CompoundTag();
		Nbt.putUuid(c, "scout", scout);
		c.putLong("hall", hall.asLong());
		c.putLong("lair", lair.asLong());
		c.putLong("started", started);
		c.putString("phase", phase);
		c.putLong("look_until", lookUntil);
		c.putBoolean("seen", seen);
		c.putString("name", name);
		return c;
	}

	/** Puts the one mission {@code c} in the level's saved scouts, as a world loaded with it would hold it. */
	private static Scouts.Mission load(ServerLevel level, CompoundTag c) {
		CompoundTag tag = new CompoundTag();
		ListTag list = new ListTag();
		list.add(c);
		tag.put("missions", list);
		Scouts.Data.get(level).read(tag);
		return Scouts.missionOf(level, Nbt.getUuid(c, "scout")).orElseThrow();
	}

	private static List<String> chronicle(GameTestHelper helper) {
		List<String> lines = new ArrayList<>();
		if (helper.getLevel().getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity hall) {
			hall.chronicle().forEach(e -> lines.add(e.text().getString()));
		}
		return lines;
	}

	private static boolean holdsMap(Container chest) {
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(Items.FILLED_MAP)) {
				return true;
			}
		}
		return false;
	}

	// --- Lost: not home within a day ------------------------------------------------------------------------------------

	/**
	 * Rowan set out 23,999 ticks ago: still out, still in the world. Set out 24,001 ticks ago: lost, gone from the world
	 * (even alive: "lost means lost"), the mission over, and the chronicle and chat say "Rowan went to scout the lair and
	 * never came back".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaScouts1123dLost"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaScouts1123dLost")
	public void aScoutNotHomeWithinADayIsLostAndGone(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		listener(helper, seen);
		Villager rowan = guard(helper, "Rowan", new BlockPos(6, 2, 6), true);
		Lairs.Lair lair = lair(helper, hall, 40);
		helper.runAfterDelay(2, () -> {
			long now = level.getGameTime();
			Scouts.Mission early = load(level, mission(rowan.getUUID(), hall, lair.pos(), now - Scouts.DAY + 1, "OUT", 0, false, "Rowan"));
			Scouts.tick(level, early);
			helper.assertTrue(Scouts.out(rowan) && rowan.isAlive() && !rowan.isRemoved(),
				"Rowan, out for 23,999 ticks (within the day), is lost already: out " + Scouts.out(rowan) + ", removed " + rowan.isRemoved());
			Scouts.Mission late = load(level, mission(rowan.getUUID(), hall, lair.pos(), now - Scouts.DAY - 1, "OUT", 0, false, "Rowan"));
			Scouts.tick(level, late);
			helper.assertFalse(Scouts.out(rowan), "Rowan, out for 24,001 ticks, is still on the mission: " + Scouts.missionOf(level, rowan.getUUID()));
			helper.assertTrue(rowan.isRemoved(), "the lost Rowan is still in the world at " + rowan.blockPosition());
			String line = "Rowan went to scout the lair and never came back";
			helper.assertTrue(chronicle(helper).contains(line), "the chronicle: " + chronicle(helper));
			helper.assertTrue(seen.contains(line), "chat: " + seen);
			helper.succeed();
		});
	}

	/**
	 * Wren fell on the way out: the mission waits out the day (a scout not home within a day is lost, not sooner), then
	 * she is lost under the name she set out with, in the chronicle and chat.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaScouts1123dFallen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaScouts1123dFallen")
	public void aScoutWhoFellOnTheWayIsLostOnceTheDayIsOut(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		listener(helper, seen);
		Villager wren = guard(helper, "Wren", new BlockPos(6, 2, 6), true);
		Lairs.Lair lair = lair(helper, hall, 40);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.SENT, "setup: Wren wasn't sent");
			wren.kill();
			helper.runAfterDelay(2, () -> {
				long now = level.getGameTime();
				helper.assertTrue(Scouts.missionOf(level, wren.getUUID()).isPresent(),
					"the fallen Wren's mission ended before the day was out; chronicle " + chronicle(helper));
				helper.assertFalse(chronicle(helper).stream().anyMatch(l -> l.contains("never came back")), "lost before the day was out: " + chronicle(helper));
				Scouts.Mission late = load(level, mission(wren.getUUID(), hall, lair.pos(), now - Scouts.DAY - 1, "OUT", 0, false, "Wren"));
				Scouts.tick(level, late);
				helper.assertTrue(Scouts.missionOf(level, wren.getUUID()).isEmpty(), "the fallen Wren's mission goes on past the day");
				String line = "Wren went to scout the lair and never came back";
				helper.assertTrue(chronicle(helper).contains(line), "the chronicle: " + chronicle(helper));
				helper.assertTrue(seen.contains(line), "chat: " + seen);
				helper.succeed();
			});
		});
	}

	// --- The camp gone while they were out ------------------------------------------------------------------------------

	/** Checks that {@code name} came home with nothing: no report, nothing known, the camp-gone lines, still alive. */
	private static void cameHomeWithNothing(GameTestHelper helper, Villager scout, String name, List<String> seen) {
		ServerLevel level = helper.getLevel();
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertFalse(Scouts.out(scout), name + " is still out: " + Scouts.missionOf(level, scout.getUUID()));
		helper.assertTrue(chronicle(helper).contains(name + " came back from scouting: the camp was gone"), "the chronicle: " + chronicle(helper));
		helper.assertTrue(seen.contains(name + " came back: the camp was gone"), "chat: " + seen);
		helper.assertFalse(holdsMap((Container) level.getBlockEntity(helper.absolutePos(CHEST))), "a report of a camp that was gone is in the chest");
		helper.assertTrue(level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8), e -> e.getItem().is(Items.FILLED_MAP)).isEmpty(),
			"a report of a camp that was gone lies on the ground");
		helper.assertTrue(Scouts.known(level, hall).isEmpty(), "the village knows a camp that was gone: " + Scouts.known(level, hall));
		helper.assertTrue(scout.isAlive() && !scout.isRemoved(), name + " didn't come home alive");
		helper.assertTrue(scout.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SPYGLASS), name + "'s spyglass isn't put away: " + scout.getItemBySlot(EquipmentSlot.OFFHAND));
	}

	/** The camp broken up while Ash is on the way out: he turns home and comes back with nothing ("the camp was gone"). */
	//$ gametest_ticks_batch AREA '200' '"qaScouts1123dGoneOut"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaScouts1123dGoneOut")
	public void theCampGoneOnTheWayOutSendsTheScoutHomeWithNothing(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		listener(helper, seen);
		Villager ash = guard(helper, "Ash", BY_POST, true);
		lair(helper, hall, 40);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.SENT, "setup: Ash wasn't sent");
			Lairs.forget(level);
			helper.succeedWhen(() -> cameHomeWithNothing(helper, ash, "Ash", seen));
		});
	}

	/**
	 * Fern has looked the camp over and is on the way back when it is broken up: she comes home with nothing, no report
	 * of a camp that is gone (B209: the scouts' report goes with the camp).
	 */
	//$ gametest_ticks_batch AREA '200' '"qaScouts1123dGoneBack"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaScouts1123dGoneBack")
	public void theCampGoneOnTheWayBackLeavesNoReport(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		listener(helper, seen);
		Villager fern = guard(helper, "Fern", new BlockPos(14, 2, 14), true);
		Lairs.Lair lair = lair(helper, hall, 40);
		helper.runAfterDelay(2, () -> {
			load(level, mission(fern.getUUID(), hall, lair.pos(), level.getGameTime() - 600, "BACK", 0, true, "Fern"));
			Lairs.forget(level);
			fern.teleportTo(helper.absolutePos(BY_POST).getX() + 0.5, helper.absolutePos(BY_POST).getY(), helper.absolutePos(BY_POST).getZ() + 0.5);
			helper.succeedWhen(() -> cameHomeWithNothing(helper, fern, "Fern", seen));
		});
	}

	// --- Home with no room in the chest ---------------------------------------------------------------------------------

	/**
	 * Moss comes home with a report and the chest by the post is full: the Scouting Report ("Scouting Report: Bandit
	 * camp") drops at Moss's feet, the chest keeps what it held, the village knows the lair (found by Moss), and chat says
	 * so.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaScouts1123dFullChest"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaScouts1123dFullChest")
	public void aReportWithNoRoomInTheChestDropsAtTheScoutsFeet(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		listener(helper, seen);
		Container chest = (Container) level.getBlockEntity(helper.absolutePos(CHEST));
		for (int i = 0; i < chest.getContainerSize(); i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
		}
		Villager moss = guard(helper, "Moss", BY_POST, true);
		Lairs.Lair lair = lair(helper, hall, 40);
		helper.runAfterDelay(2, () -> {
			load(level, mission(moss.getUUID(), hall, lair.pos(), level.getGameTime() - 600, "BACK", 0, true, "Moss"));
			helper.succeedWhen(() -> {
				helper.assertFalse(Scouts.out(moss), "Moss is still out: " + Scouts.missionOf(level, moss.getUUID()));
				List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, moss.getBoundingBox().inflate(2), e -> e.getItem().is(Items.FILLED_MAP));
				helper.assertTrue(dropped.size() == 1, "no report at Moss's feet: " + dropped.size() + " maps by " + moss.blockPosition());
				String name = dropped.get(0).getItem().getHoverName().getString();
				helper.assertTrue(name.equals("Scouting Report: Bandit camp"), "the report is called " + name);
				for (int i = 0; i < chest.getContainerSize(); i++) {
					helper.assertTrue(chest.getItem(i).is(Items.COBBLESTONE) && chest.getItem(i).getCount() == 64, "the full chest changed at slot " + i + ": " + chest.getItem(i));
				}
				Optional<Scouts.Known> known = Scouts.known(level, hall);
				helper.assertTrue(known.isPresent() && known.get().lair().equals(lair.pos()) && known.get().by().equals("Moss") && !known.get().trailed(),
					"what the village knows: " + known);
				helper.assertTrue(seen.contains("Moss is back from scouting the lair (Bandit camp): the report is in the chest by their post"), "chat: " + seen);
			});
		});
	}

	// --- Nobody goes by night, any day ----------------------------------------------------------------------------------

	/**
	 * By night nobody goes, on the first night and on the fourth (the clock past 72,000); by day the scout goes, on the
	 * first day and the fourth (morning and late afternoon).
	 */
	//$ gametest_batch AREA '"qaScouts1123dNight"'
	@GameTest(template = AREA, batch = "qaScouts1123dNight")
	public void nobodyGoesByNightOnAnyDayAndTheScoutGoesByDay(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		guard(helper, "Lark", new BlockPos(6, 2, 6), true);
		lair(helper, hall, 40);
		helper.runAfterDelay(2, () -> {
			for (long time : new long[] {13000, 18000, 3 * Scouts.DAY + 13000, 3 * Scouts.DAY + 18000, 3 * Scouts.DAY + 22000}) {
				level.setDayTime(time);
				Scouts.Sent sent = Scouts.send(level, hall);
				helper.assertTrue(sent == Scouts.Sent.NIGHT, "at day time " + time + " (night): " + sent);
			}
			for (long time : new long[] {1000, 3 * Scouts.DAY + 1000, 3 * Scouts.DAY + 11000}) {
				level.setDayTime(time);
				Scouts.Sent sent = Scouts.send(level, hall);
				helper.assertTrue(sent == Scouts.Sent.SENT, "at day time " + time + " (day): " + sent);
				Scouts.forget(level);
			}
			helper.succeed();
		});
	}

	// --- The nearest free scout, and one out already named ---------------------------------------------------------------

	/**
	 * Two scouts and a plain guard: "Scout the lair" sends the scout nearest the hall (not the plain guard, though nearer
	 * still), and the action bar says so ("Near sets out to scout the lair: Bandit camp"); a second click sends nobody and
	 * names the one out ("Near is already out scouting").
	 */
	//$ gametest_ticks_batch AREA '100' '"qaScouts1123dNearest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaScouts1123dNearest")
	public void theNearestFreeScoutGoesAndASecondClickNamesTheOneOut(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		Villager far = guard(helper, "Far", new BlockPos(12, 2, 12), true);
		Villager plain = guard(helper, "Plain", new BlockPos(2, 2, 3), false);
		Villager near = guard(helper, "Near", new BlockPos(5, 2, 2), true);
		lair(helper, hall, 40);
		helper.runAfterDelay(2, () -> {
			Scouts.send(player, level, hall);
			helper.assertTrue(Scouts.out(near) && !Scouts.out(far) && !Scouts.out(plain),
				"who went: Near " + Scouts.out(near) + ", Far " + Scouts.out(far) + ", Plain " + Scouts.out(plain));
			helper.assertTrue(seen.contains("Near sets out to scout the lair: Bandit camp"), "the action bar on sending: " + seen);
			seen.clear();
			Scouts.send(player, level, hall);
			helper.assertTrue(!Scouts.out(far) && Scouts.out(near), "a second click: Far " + Scouts.out(far));
			helper.assertTrue(seen.contains("Near is already out scouting"), "the action bar on a second click: " + seen);
			helper.succeed();
		});
	}

	// --- Unseen past 8 blocks, and only by a lair's band ------------------------------------------------------------------

	/**
	 * A lair's bandit and a scout 8.5 blocks apart: the scout is unseen (more than 8 blocks off). A vindicator of no lair
	 * 20 blocks off may go for a scout (the rule is the lair's band's), and the lair's bandit may go for a plain guard 20
	 * blocks off.
	 */
	//$ gametest_ticks_batch HUGE '100' '"qaScouts1123dUnseen"'
	@GameTest(template = HUGE, timeoutTicks = 100, batch = "qaScouts1123dUnseen")
	public void aScoutJustPastEightBlocksIsUnseenButOnlyByALairsBand(GameTestHelper helper) {
		Leftovers.clear(helper);
		boolean open = Expansions.openForTests;
		Expansions.openForTests = true;
		Leftovers.after(helper, () -> Expansions.openForTests = open);
		ServerLevel level = helper.getLevel();
		BlockPos base = helper.absolutePos(new BlockPos(3, 2, 3));
		Vindicator bandit = helper.spawn(EntityType.VINDICATOR, new BlockPos(3, 2, 3));
		bandit.setNoAi(true);
		bandit.addTag(Lairs.TAG);
		Vindicator stray = helper.spawn(EntityType.VINDICATOR, new BlockPos(3, 2, 5));
		stray.setNoAi(true);
		Villager scout = helper.spawn(EntityType.VILLAGER, new BlockPos(12, 2, 3));
		scout.setNoAi(true);
		Jobs.employ(level, scout, helper.absolutePos(new BlockPos(20, 2, 20)), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		helper.setBlock(new BlockPos(20, 2, 20), ModBlocks.GUARD_POST);
		scout.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		scout.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		Villager plain = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 23));
		plain.setNoAi(true);
		Jobs.employ(level, plain, helper.absolutePos(new BlockPos(20, 2, 20)), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		plain.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		helper.assertTrue(Scouts.isScout(scout) && !Scouts.isScout(plain), "setup: who is a scout");
		bandit.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
		scout.moveTo(base.getX() + 9.0, base.getY(), base.getZ() + 0.5);
		helper.assertTrue(Scouts.unseen(bandit, scout), "a scout 8.5 blocks off is seen by the lair's bandit: " + bandit.distanceTo(scout));
		helper.assertTrue(bandit.canAttack(scout) == false, "the lair's bandit can attack a scout 8.5 blocks off");
		stray.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 20.5);
		scout.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5 + 40);
		helper.assertTrue(stray.distanceTo(scout) > 15, "setup: the stray is near the scout: " + stray.distanceTo(scout));
		helper.assertFalse(Scouts.unseen(stray, scout), "a vindicator of no lair doesn't go for a scout 20 blocks off");
		plain.moveTo(base.getX() + 0.5, base.getY(), base.getZ() + 20.5);
		helper.assertFalse(Scouts.unseen(bandit, plain), "the lair's bandit doesn't go for a plain guard 20 blocks off");
		helper.succeed();
	}

	// --- Saved data -----------------------------------------------------------------------------------------------------

	/**
	 * {@code aliveworkplace_scouts}: a look under way (its end, whether seen) and what a village knows survive a load; a
	 * mission saved with no phase reads as on the way out, one with no scout is dropped, and a known lair with no
	 * "trailed" or "by" reads as scouted by nobody named; an empty file has no missions and knows nothing.
	 */
	//$ gametest_batch AREA '"qaScouts1123dSaved"'
	@GameTest(template = AREA, batch = "qaScouts1123dSaved")
	public void savedScoutsReadMissingKeysAsDefaultsAndKeepALookUnderWay(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Scouts.Data data = Scouts.Data.get(level);
		BlockPos lairAt = hall.east(40);
		UUID looking = UUID.randomUUID();
		load(level, mission(looking, hall, lairAt, 500, "LOOK", 777, true, "Sage"));
		Scouts.Mission before = Scouts.missionOf(level, looking).orElseThrow();
		data.roundTrip(level.registryAccess());
		Optional<Scouts.Mission> after = Scouts.missionOf(level, looking);
		helper.assertTrue(after.isPresent() && after.get().equals(before) && after.get().phase() == Scouts.Phase.LOOK
			&& after.get().lookUntil() == 777 && after.get().seen(), "a look under way after a load: " + after + ", was " + before);

		UUID noPhase = UUID.randomUUID();
		CompoundTag partial = new CompoundTag();
		Nbt.putUuid(partial, "scout", noPhase);
		partial.putLong("hall", hall.asLong());
		partial.putLong("lair", lairAt.asLong());
		CompoundTag noScout = new CompoundTag();
		noScout.putLong("hall", hall.asLong());
		noScout.putString("phase", "BACK");
		CompoundTag knownPartial = new CompoundTag();
		knownPartial.putLong("hall", hall.asLong());
		knownPartial.putLong("lair", lairAt.asLong());
		ListTag missions = new ListTag();
		missions.add(partial);
		missions.add(noScout);
		ListTag known = new ListTag();
		known.add(knownPartial);
		CompoundTag tag = new CompoundTag();
		tag.put("missions", missions);
		tag.put("known", known);
		data.read(tag);
		Optional<Scouts.Mission> read = Scouts.missionOf(level, noPhase);
		helper.assertTrue(read.isPresent() && read.get().phase() == Scouts.Phase.OUT && read.get().lookUntil() == 0 && !read.get().seen()
			&& read.get().name().isEmpty() && read.get().started() == 0, "a mission with no phase: " + read);
		helper.assertTrue(Scouts.mission(level, hall).map(m -> m.scout().equals(noPhase)).orElse(false), "the village's mission isn't the one with a scout");
		CompoundTag saved = data.save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.getList("missions", 10).size() == 1, "a mission with no scout was kept: " + saved);
		CompoundTag knownSaved = saved.getList("known", 10).getCompound(0);
		helper.assertTrue(BlockPos.of(knownSaved.getLong("lair")).equals(lairAt) && !knownSaved.getBoolean("trailed") && knownSaved.getString("by").isEmpty()
			&& knownSaved.getLong("day") == 0, "a known lair with keys missing: " + knownSaved);

		data.read(new CompoundTag());
		helper.assertTrue(Scouts.mission(level, hall).isEmpty() && Scouts.Data.get(level).save(new CompoundTag(), level.registryAccess())
			.getList("known", 10).isEmpty(), "an empty file holds something");
		helper.succeed();
	}

	// --- Making a scout with a full inventory ---------------------------------------------------------------------------

	/**
	 * A knight given a spyglass by a player whose inventory is full: the shield drops at the player's feet (none lost,
	 * none made), one spyglass goes in the scout's off hand, and the action bar says "Thorn is a Scout now".
	 */
	//$ gametest_ticks_batch AREA '100' '"qaScouts1123dFullHands"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaScouts1123dFullHands")
	public void theOldItemDropsAtThePlayersFeetWhenTheirInventoryIsFull(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		List<String> seen = new ArrayList<>();
		ServerPlayer player = listener(helper, seen);
		Villager thorn = guard(helper, "Thorn", new BlockPos(6, 2, 6), false);
		thorn.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		helper.assertTrue(Guards.kind(thorn) == Guards.Kind.KNIGHT, "setup: not a knight: " + Guards.kind(thorn));
		for (int i = 0; i < player.getInventory().items.size(); i++) {
			player.getInventory().items.set(i, new ItemStack(Items.DIRT, 64));
		}
		player.getInventory().selected = 0;
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS, 2));
		player.getInventory().offhand.set(0, new ItemStack(Items.DIRT, 64));
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		helper.assertTrue(Guards.makesKind(held), "a spyglass isn't taken");
		Guards.hand(player, thorn, held); // what BuilderEvents does on a sneak-right-click with it
		helper.assertTrue(Scouts.isScout(thorn), "Thorn isn't a scout: " + Guards.kind(thorn));
		helper.assertTrue(thorn.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SPYGLASS) && thorn.getItemBySlot(EquipmentSlot.OFFHAND).getCount() == 1,
			"Thorn's off hand: " + thorn.getItemBySlot(EquipmentSlot.OFFHAND));
		helper.assertTrue(player.getMainHandItem().is(Items.SPYGLASS) && player.getMainHandItem().getCount() == 1, "the player's spyglasses: " + player.getMainHandItem());
		helper.assertTrue(seen.contains("Thorn is a Scout now"), "the action bar: " + seen);
		helper.runAfterDelay(1, () -> {
			List<ItemEntity> shields = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(4), e -> e.getItem().is(Items.SHIELD));
			int shieldsHeld = player.getInventory().countItem(Items.SHIELD);
			helper.assertTrue(shields.size() == 1 && shields.get(0).getItem().getCount() == 1 && shieldsHeld == 0,
				"the shield: " + shields.size() + " on the ground by the player, " + shieldsHeld + " in the inventory");
			helper.succeed();
		});
	}
}

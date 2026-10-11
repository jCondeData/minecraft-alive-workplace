package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.Expansions;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.GuardCombat;
import io.github.jcondedata.aliveworkplace.guard.Guards;
import io.github.jcondedata.aliveworkplace.guard.Scouts;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.DefencePage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.threat.Lairs;
import io.github.jcondedata.aliveworkplace.threat.ThreatData;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.MapDecorations;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Scouts (ROADMAP 32.12): a guard handed a spyglass is a Scout and the player gets back what they held (not before
 * Milestone 32 opens); sent from the Defence page to a lair 60 blocks off, the scout gets within 24 blocks of it and is
 * home within 2400 ticks with a Scouting Report in the chest by their post, the report's red X on the lair and the page
 * showing its exact place; a lair's bandit goes for a plain guard 10 blocks off but never for a scout as far; a mission
 * and what the village knows survive a save and load; a scout sent with no lair, by night or with none in the village
 * isn't; and raiders who flee at dawn are trailed home by a scout.
 */
public class ScoutGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(1, 2, 1);
	private static final BlockPos POST = new BlockPos(4, 2, 4);
	private static final BlockPos CHEST = new BlockPos(4, 2, 6);
	private static final BlockPos CAMP = new BlockPos(12, 1, 12);

	/** A hall, a guard post and its chest; what the test changes goes when it ends. */
	private static BlockPos village(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		boolean open = Expansions.openForTests;
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 32;
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

	private static Villager guard(GameTestHelper helper, BlockPos at) {
		Villager villager = helper.spawn(EntityType.VILLAGER, at);
		Jobs.employ(helper.getLevel(), villager, helper.absolutePos(POST), ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		return villager;
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		Leftovers.after(helper, () -> helper.getLevel().getServer().getPlayerList().remove(player));
		return player;
	}

	/** A lair of the bandits {@code dx} blocks east of the hall, on the ground, with no camp built; its captain stands there. */
	private static Lairs.Lair farLair(GameTestHelper helper, BlockPos hall, int dx) {
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

	/**
	 * Open ground for a long walk, away from every test area (the framework walls each one in with barriers, and they
	 * stand in rows): 5000 blocks east of this one. Only the chunks the walk crosses are kept loaded, a strip {@code length}
	 * blocks east and a chunk to either side, and every one is let go when the test ends (with whatever stood on them):
	 * a wider block of forced ground once ran the full suite out of memory. Returns the spot on the ground there.
	 */
	private static BlockPos openGround(GameTestHelper helper, int length) {
		ServerLevel level = helper.getLevel();
		BlockPos here = helper.absolutePos(HALL);
		int x0 = here.getX() + 5000;
		int z0 = here.getZ();
		List<long[]> forced = new ArrayList<>();
		for (int cx = (x0 - 16) >> 4; cx <= (x0 + length) >> 4; cx++) {
			for (int cz = (z0 - 16) >> 4; cz <= (z0 + 16) >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz); // loaded now, not some ticks later: the village goes on it straight away
				forced.add(new long[] {cx, cz});
			}
		}
		Leftovers.after(helper, () -> {
			level.getEntitiesOfClass(net.minecraft.world.entity.Entity.class, new net.minecraft.world.phys.AABB(new BlockPos(x0, here.getY(), z0)).inflate(length + 32, 32, 40),
				e -> !(e instanceof ServerPlayer)).forEach(net.minecraft.world.entity.Entity::discard);
			forced.forEach(c -> level.setChunkForced((int) c[0], (int) c[1], false));
		});
		return new BlockPos(x0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0), z0);
	}

	private static List<String> lines(ItemStack icon) {
		List<String> lines = new ArrayList<>();
		lines.add(icon.getHoverName().getString());
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(line -> lines.add(line.getString()));
		}
		return lines;
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * A knight sneak-right-clicked with a spyglass: one spyglass goes in their off hand, they are a Scout ("Scout" over
	 * their head) and the player has their shield back. With Milestone 32 closed a spyglass makes no kind.
	 */
	//$ gametest_batch AREA '"scoutSpyglass"'
	@GameTest(template = AREA, batch = "scoutSpyglass")
	public void aGuardGivenASpyglassIsAScoutAndThePlayerGetsTheOldItemBack(GameTestHelper helper) {
		village(helper);
		Villager guard = guard(helper, new BlockPos(6, 2, 6));
		guard.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		helper.assertTrue(Guards.kind(guard) == Guards.Kind.KNIGHT, "not a knight first");
		ServerPlayer player = player(helper);
		player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SPYGLASS, 2));
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		helper.assertTrue(Guards.makesKind(held), "a spyglass isn't taken");
		Guards.hand(player, guard, held); // what BuilderEvents does on a sneak-right-click with it
		helper.assertTrue(Guards.kind(guard) == Guards.Kind.SCOUT && Scouts.isScout(guard), "not a scout: " + Guards.kind(guard));
		helper.assertTrue(guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SPYGLASS) && guard.getItemBySlot(EquipmentSlot.OFFHAND).getCount() == 1,
			"the off hand: " + guard.getItemBySlot(EquipmentSlot.OFFHAND));
		helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "the player's spyglasses: " + player.getItemInHand(InteractionHand.MAIN_HAND));
		helper.assertTrue(player.getInventory().countItem(Items.SHIELD) == 1, "the shield wasn't given back");
		String title = GuardCombat.title(guard).getString();
		helper.assertTrue(title.contains("Scout"), "over their head: " + title);
		// A bow handed to the scout makes them an archer again, the spyglass back to the player.
		ItemStack bow = new ItemStack(Items.BOW);
		Guards.hand(player, guard, bow);
		helper.assertTrue(Guards.kind(guard) == Guards.Kind.ARCHER && player.getInventory().countItem(Items.SPYGLASS) == 2, "the spyglass back");
		Expansions.openForTests = false;
		boolean takes = Guards.makesKind(new ItemStack(Items.SPYGLASS));
		Expansions.openForTests = true;
		helper.assertFalse(takes, "a spyglass makes a kind before Milestone 32 opens");
		helper.succeed();
	}

	/**
	 * The trip: "Scout the lair" on the Defence page sends the scout to a lair 60 blocks east; they get within 24 blocks of
	 * it, raise the spyglass and are home within 2400 ticks; the chest by their post holds the Scouting Report, a filled map
	 * named for the lair with a red X on it and the culture, captain and strength in its tooltip; the page shows the
	 * lair's exact place and who found it; the chronicle says so.
	 */
	//$ gametest_ticks_batch AREA '2400' '"scoutTrip"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "scoutTrip")
	public void aScoutSentToALair60BlocksOffGetsWithin24AndIsHomeWithin2400Ticks(GameTestHelper helper) {
		village(helper);
		ServerLevel level = helper.getLevel();
		// The village out on open ground: its hall, the scout's post and chest, the scout.
		// The walk reaches no further than the lookout, 24 blocks short of the lair, and a leg (16) past it.
		BlockPos hall = openGround(helper, 60 - Scouts.SIGHT + 16);
		BlockPos post = hall.offset(3, 0, 3);
		BlockPos chestAt = hall.offset(3, 0, 5);
		level.setBlockAndUpdate(hall, ModBlocks.VILLAGE_HALL.defaultBlockState());
		level.setBlockAndUpdate(post, ModBlocks.GUARD_POST.defaultBlockState());
		level.setBlockAndUpdate(chestAt, Blocks.CHEST.defaultBlockState());
		Villager scout = EntityType.VILLAGER.create(level);
		scout.moveTo(hall.getX() + 4.5, hall.getY(), hall.getZ() + 4.5);
		level.addFreshEntity(scout);
		Jobs.employ(level, scout, post, ModVillagers.GUARD_POST_POI, ModVillagers.GUARD);
		scout.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		scout.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		Lairs.Lair lair = farLair(helper, hall, 60);
		ServerPlayer player = player(helper);
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.GUARDS, player);
		List<String> before = lines(menu.icon(DefencePage.SCOUT));
		helper.assertTrue(before.get(0).equals("Scout the lair") && before.contains("1 scout in the village"), "the scout slot: " + before);
		helper.assertTrue(lines(menu.icon(DefencePage.WHERE)).stream().noneMatch(l -> l.startsWith("At ")), "the exact place known before scouting");
		menu.press(DefencePage.SCOUT, player);
		helper.assertTrue(Scouts.out(scout), "the scout wasn't sent");
		helper.assertTrue(lines(menu.icon(DefencePage.SCOUT)).get(0).contains("is out scouting"), "the page while out: " + lines(menu.icon(DefencePage.SCOUT)));
		menu.press(DefencePage.SCOUT, player); // a second click sends nobody else
		double[] nearest = {Double.MAX_VALUE};
		boolean[] raised = {false};
		StringBuilder marks = new StringBuilder();
		helper.onEachTick(() -> {
			double was = nearest[0];
			nearest[0] = Math.min(nearest[0], flat(scout.blockPosition(), lair.pos()));
			if ((int) (was / 5) != (int) (nearest[0] / 5)) {
				marks.append((int) nearest[0]).append("@").append(helper.getTick()).append(' ');
			}
			raised[0] |= scout.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.SPYGLASS);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(scout.isAlive(), "the scout died");
			helper.assertFalse(Scouts.out(scout), "still out; nearest to the lair " + (int) nearest[0] + ", at " + scout.blockPosition()
				+ ", mission " + Scouts.missionOf(level, scout.getUUID()) + ", " + Scouts.describe(scout) + ", marks " + marks);
			helper.assertTrue(nearest[0] <= Scouts.SIGHT, "never within 24 blocks: " + nearest[0]);
			helper.assertTrue(raised[0], "never raised the spyglass");
			helper.assertTrue(flat(scout.blockPosition(), post) <= 4, "not home: " + scout.blockPosition());
			helper.assertTrue(scout.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SPYGLASS) && scout.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD),
				"the hands after: " + scout.getItemBySlot(EquipmentSlot.MAINHAND) + " / " + scout.getItemBySlot(EquipmentSlot.OFFHAND));
			Container chest = (Container) level.getBlockEntity(chestAt);
			ItemStack report = ItemStack.EMPTY;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).is(Items.FILLED_MAP)) {
					report = chest.getItem(i);
				}
			}
			helper.assertFalse(report.isEmpty(), "no report in the chest");
			MapDecorations decorations = report.getOrDefault(DataComponents.MAP_DECORATIONS, MapDecorations.EMPTY);
			MapDecorations.Entry x = decorations.decorations().get("+");
			helper.assertTrue(x != null && x.type().value() == net.minecraft.world.level.saveddata.maps.MapDecorationTypes.RED_X.value()
				&& (int) x.x() == lair.pos().getX() && (int) x.z() == lair.pos().getZ(), "the red X: " + decorations);
			helper.assertTrue(report.get(DataComponents.MAP_ID) != null, "the map has no id");
			List<String> tip = lines(report);
			helper.assertTrue(tip.get(0).equals("Scouting Report: Bandit camp"), "the report's name: " + tip);
			helper.assertTrue(tip.contains("Raiders: Bandits") && tip.stream().anyMatch(l -> l.startsWith("Captain: ")) && tip.contains("Strength: 7"),
				"the report's tooltip: " + tip);
			// The page: the exact place, who found it, the captain and strength.
			DefencePage.render(menu, level, hall);
			String exact = "At " + lair.pos().getX() + ", " + lair.pos().getY() + ", " + lair.pos().getZ();
			helper.assertTrue(lines(menu.icon(DefencePage.WHERE)).contains(exact), "the exact place: " + lines(menu.icon(DefencePage.WHERE)));
			List<String> found = lines(menu.icon(DefencePage.SCOUT));
			helper.assertTrue(found.get(0).startsWith("Scouted by") && found.contains(exact) && found.contains("Strength: 7")
				&& found.stream().anyMatch(l -> l.startsWith("Captain: ")), "the scout slot: " + found);
			for (int slot = 0; slot < ChoiceMenu.SIZE; slot++) {
				for (String line : lines(menu.icon(slot))) {
					helper.assertFalse(line.contains("aliveworkplace.") || line.contains("%"), "slot " + slot + " shows a raw line: " + line);
				}
			}
			helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.chronicle().stream()
				.anyMatch(e -> e.text().getString().contains("came back from scouting the lair")), "the chronicle");
		});
	}

	/** A lair's bandit goes for a plain guard 10 blocks off, never for a scout as far the other way. */
	//$ gametest_ticks_batch HUGE '300' '"scoutUnseen"'
	@GameTest(template = HUGE, timeoutTicks = 300, batch = "scoutUnseen")
	public void aBanditDoesntGoForAScout10BlocksAway(GameTestHelper helper) {
		village(helper);
		Villager scout = guard(helper, new BlockPos(15, 2, 4));
		scout.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		Villager plain = guard(helper, new BlockPos(15, 2, 24));
		scout.setNoAi(true);
		plain.setNoAi(true);
		// (not invulnerable: nothing goes for one who is)
		Vindicator bandit = helper.spawn(EntityType.VINDICATOR, new BlockPos(15, 2, 14));
		bandit.addTag(Lairs.TAG);
		bandit.addTag(Lairs.BANDIT_TAG);
		helper.assertTrue(Scouts.unseen(bandit, scout) && !Scouts.unseen(bandit, plain), "who is unseen");
		helper.onEachTick(() -> {
			if (bandit.getTarget() == scout) {
				helper.fail("the bandit went for the scout " + (int) bandit.distanceTo(scout) + " blocks off");
			}
		});
		helper.succeedWhen(() -> helper.assertTrue(bandit.getTarget() == plain, "the bandit went for nobody: " + bandit.getTarget()));
	}

	/** A mission and what the village knows survive a save and load; sent with no lair, by night or with no scout, nobody goes. */
	//$ gametest_batch AREA '"scoutSave"'
	@GameTest(template = AREA, batch = "scoutSave")
	public void aMissionSurvivesASaveAndLoadAndNobodyGoesWithoutALairByNightOrAScout(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.NO_LAIR, "sent with no lair");
		Lairs.Lair lair = farLair(helper, hall, 60);
		helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.NO_SCOUT, "sent with no scout");
		Villager scout = guard(helper, new BlockPos(5, 2, 5));
		scout.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		level.setDayTime(14000);
		helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.NIGHT, "sent by night");
		level.setDayTime(1000);
		helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.SENT, "not sent by day");
		Scouts.Mission before = Scouts.mission(level, hall).orElseThrow();
		Scouts.Data.get(level).roundTrip(level.registryAccess());
		Optional<Scouts.Mission> after = Scouts.missionOf(level, scout.getUUID());
		helper.assertTrue(after.isPresent() && after.get().equals(before), "the mission after a load: " + after + " was " + before);
		helper.assertTrue(Scouts.send(level, hall) == Scouts.Sent.ALREADY_OUT, "sent twice");
		// What the village knows: by trailing raiders, kept through a load; a lair that moved is unknown.
		Scouts.forget(level);
		helper.assertTrue(Scouts.trail(level, hall, Threats.BANDITS), "not trailed");
		Scouts.Data.get(level).roundTrip(level.registryAccess());
		Optional<Scouts.Known> known = Scouts.known(level, hall);
		helper.assertTrue(known.isPresent() && known.get().trailed() && known.get().lair().equals(lair.pos()), "known after a load: " + known);
		Lairs.putForTest(level, new Lairs.Lair(lair.pos().east(5), hall, lair.captain(), lair.day(), lair.culture(), 7, lair.grown(), 0, 0, 3));
		helper.assertTrue(Scouts.known(level, hall).isEmpty(), "a new lair is known");
		helper.succeed();
	}

	/**
	 * Raiders who flee at dawn are trailed home: with a scout in the village the lair's place becomes known (the chronicle
	 * says who found it) without anyone being sent; with Milestone 32 closed, nothing is.
	 */
	//$ gametest_ticks_batch AREA '1400' '"scoutTrail"'
	@GameTest(template = AREA, timeoutTicks = 1400, batch = "scoutTrail")
	public void raidersFleeingAtDawnAreTrailedHomeByAScout(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Villager scout = guard(helper, new BlockPos(5, 2, 5));
		scout.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SPYGLASS));
		scout.setInvulnerable(true);
		scout.setNoAi(true);
		for (int i = 0; i < 4; i++) {
			helper.spawn(EntityType.VILLAGER, new BlockPos(2 + i, 2, 3)).setNoAi(true);
		}
		Lairs.Lair lair = Lairs.found(level, hall, helper.absolutePos(CAMP), Threats.BANDITS);
		helper.assertTrue(Scouts.known(level, hall).isEmpty(), "known before");
		level.setDayTime(40 * 24000L + 15000);
		VillageRaids.Raid raid = VillageRaids.start(level, hall, 8, 0);
		helper.assertTrue(raid != null, "no raid");
		helper.runAfterDelay(1250, () -> {
			level.setDayTime(41 * 24000L + 500);
			VillageRaids.tick(level, hall, 8, 0, 41, day -> { });
			helper.assertTrue(VillageRaids.active(hall).isEmpty(), "the raid didn't end");
			Optional<Scouts.Known> known = Scouts.known(level, hall);
			helper.assertTrue(known.isPresent() && known.get().trailed() && known.get().lair().equals(lair.pos()), "not trailed: " + known);
			helper.assertTrue(level.getBlockEntity(hall) instanceof VillageHallBlockEntity entity && entity.chronicle().stream()
				.anyMatch(e -> e.text().getString().contains("trailed the fleeing raiders")), "the chronicle");
			Scouts.forget(level);
			Expansions.openForTests = false;
			boolean trailed = Scouts.trail(level, hall, Threats.BANDITS);
			Expansions.openForTests = true;
			helper.assertFalse(trailed, "trailed with Milestone 32 closed");
			helper.succeed();
		});
	}
}

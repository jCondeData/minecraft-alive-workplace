package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import io.github.jcondedata.aliveworkplace.guard.RallyBannerItem;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.rules.Condition;
import io.github.jcondedata.aliveworkplace.rules.Conditions;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.story.Reputation;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** The Bandit King's story (ROADMAP 31.16): the arc walked from the three banners to the King's fall, his tribute, its trigger. */
public class BanditKingStoryGameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	/** The fort's middle on the test ground (grass at y 3). */
	private static final BlockPos MIDDLE = new BlockPos(15, 3, 15);
	/** The hall and a neighbour's, just outside the fort's footprint. */
	private static final BlockPos HALL = new BlockPos(0, 4, 0);
	private static final BlockPos OTHER_HALL = new BlockPos(0, 4, 29);
	/** Where the test puts the arc's three camps. */
	private static final List<BlockPos> CAMPS = List.of(new BlockPos(5, 4, 5), new BlockPos(25, 4, 5), new BlockPos(5, 4, 25));
	private static final long DAY = VillageNeeds.DAY;

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		VillageHalls.RADIUS = 8;
		Arcs.NEAR = 0; // (nothing far is built or spawned unless the test asks)
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
		});
	}

	private static void flatGround(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.GRASS_BLOCK);
			}
		}
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return entity;
	}

	private static ServerPlayer player(GameTestHelper helper, BlockPos at) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos abs = helper.absolutePos(at);
		player.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
		return player;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().filter(e -> e.kind() == Chronicle.Kind.STORY).map(e -> e.text().getString()).toList();
	}

	private static Arcs.Arc arc(GameTestHelper helper) {
		Arcs.Arc arc = Arcs.find("bandit_king").orElse(null);
		helper.assertTrue(arc != null && arc.threat() && arc.chapters().size() == 3, "the Bandit King's arc didn't load as a threat of three chapters");
		return arc;
	}

	/**
	 * The arc walked through: three camps fly the war banner and each taken counts (dropping a named banner); the next
	 * dawn the fort's chapter; all three allies won (a quest at the neighbours', 24 emeralds at the hall, Pell's 16 bread);
	 * at the storm 3 guards and 6 mercenaries stand at the hall, the postern is open and they follow a Rally Banner raised
	 * by the fort; the first night of the storm the raiders come; the King falls and the ending pays (+150, Kingslayer, a
	 * festival, no camps for 10 days, the treasure chest left), Pell joins the village and the allies go home.
	 */
	//$ gametest_ticks_batch TALL '200' '"banditKingStoryWalk"'
	@GameTest(template = TALL, timeoutTicks = 200, batch = "banditKingStoryWalk")
	public void banditKingStoryWalk(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		flatGround(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, HALL);
		hall(helper, OTHER_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer player = player(helper, new BlockPos(2, 4, 2));
		long day = Chronicle.day(level) + 1;
		setDay(level, day);

		// Chapter 1: three camps, each with its captain and three or four bandits, and the war banners.
		ArcState s = Arcs.start(level, hall, arc(helper));
		helper.assertTrue(s != null && s.chapter == 0 && s.started, "the arc didn't start");
		List<ArcState.ArcPlace> camps = s.places.stream().filter(p -> p.stages.get(0).equals("aliveworkplace:camp/bandit_camp")).toList();
		helper.assertTrue(camps.size() == 3, "camps: " + camps.size());
		for (ArcState.ArcPlace p : camps) {
			int d = (int) Math.round(Math.sqrt(p.spot.distSqr(new BlockPos(hall.getX(), p.spot.getY(), hall.getZ()))));
			helper.assertTrue(d >= 80 && d <= 104, "a camp " + d + " blocks out");
		}
		long captains = s.mobs.stream().filter(m -> m.key.equals("captain")).count();
		long bandits = s.mobs.stream().filter(m -> m.key.startsWith("bandit")).count();
		helper.assertTrue(captains == 3 && bandits >= 9 && bandits <= 12, "captains " + captains + ", bandits " + bandits);
		helper.assertTrue(Arcs.chatter(level, hall).contains("chatter.aliveworkplace.bandit_king.banner"), "no chatter: " + Arcs.chatter(level, hall));
		s.mobs.forEach(m -> m.dead = true); // (the camps are far off; the test stands them in its own area)
		for (int i = 0; i < 3; i++) {
			ArcState.ArcPlace p = camps.get(i);
			p.spot = helper.absolutePos(CAMPS.get(i));
			p.placed = 1;
			BlockPos banner = BanditKingStory.raiseBanner(level, p.spot);
			helper.assertTrue(banner != null && BanditKingStory.isWarBanner(level, banner), "no war banner at camp " + (i + 1));
			BlockState state = level.getBlockState(banner);
			BanditKingStory.onBreak(level, player, banner, state);
			level.destroyBlock(banner, true, player);
			if (i < 2) {
				helper.assertTrue(s.chapter == 0, "the chapter ended after " + (i + 1) + " banners");
			}
		}
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.getItem().is(Items.BLACK_BANNER));
		helper.assertTrue(drops.size() == 3 && drops.stream().allMatch(e -> e.getItem().getHoverName().getString().equals("The Bandit King's War Banner")
			&& !e.getItem().getOrDefault(DataComponents.BANNER_PATTERNS, net.minecraft.world.level.block.entity.BannerPatternLayers.EMPTY).layers().isEmpty()),
			"the named banners dropped: " + drops.size());
		drops.forEach(ItemEntity::discard);
		helper.assertTrue(s.chapter == 1 && !s.started && s.nextChapterDay == day + 1, "chapter 1 didn't end: " + s.chapter);

		// Chapter 2, the next dawn: the fort's place, Pell, and the allies' quests.
		setDay(level, day + 1);
		Arcs.round(level, hall, entity);
		helper.assertTrue(s.chapter == 1 && s.started && s.place("fort") != null, "chapter 2 didn't begin");
		BanditFort.Fort fort = BanditFort.at(helper.absolutePos(MIDDLE), Rotation.NONE);
		for (int stage = 0; stage < 3; stage++) {
			helper.assertTrue(BanditFort.place(level, fort, stage, false), "stage " + (stage + 1) + " wasn't placed");
		}
		ArcState.ArcPlace fortPlace = s.place("fort");
		fortPlace.spot = helper.absolutePos(MIDDLE);
		fortPlace.origin = fort.origin();
		fortPlace.rotation = 0;
		fortPlace.placed = 3;
		helper.assertTrue(!level.getBlockState(fort.at(BanditFort.POSTERN)).getValue(DoorBlock.OPEN), "the postern was open before Pell spoke");
		Mob king = BanditFort.men(level, fort.yard(), 30).stream().filter(m -> m.getTags().contains(BanditKing.TAG)).findFirst().orElse(null);
		helper.assertTrue(king != null, "no King");
		BanditFort.men(level, fort.yard(), 30).stream().filter(m -> m != king).forEach(Mob::discard);

		// The deserter: Pell comes by the first camp's ashes and wants his bread.
		Arcs.NEAR = 96;
		player.teleportTo(camps.get(0).spot.getX() + 0.5, camps.get(0).spot.getY(), camps.get(0).spot.getZ() + 0.5);
		ArcEffects.check(level);
		Villager pell = level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8), v -> v.getTags().contains(ArcEffects.roleTag("pell"))).stream().findFirst().orElse(null);
		helper.assertTrue(pell != null && pell.getCustomName() != null && pell.getCustomName().getString().equals("Pell"), "no Pell");
		helper.assertTrue(BanditKingStory.onUse(player, level, InteractionHand.MAIN_HAND, pell) == InteractionResult.SUCCESS
			&& !s.flags.containsKey(BanditKingStory.ALLY_PELL), "Pell told without his bread");
		player.getInventory().add(new ItemStack(Items.BREAD, 20));
		helper.assertTrue(BanditKingStory.onUse(player, level, InteractionHand.MAIN_HAND, pell) == InteractionResult.PASS, "Pell didn't take the bread");
		helper.assertTrue(player.getInventory().countItem(Items.BREAD) == 4, "bread left: " + player.getInventory().countItem(Items.BREAD));
		helper.assertTrue(ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, pell) == InteractionResult.SUCCESS, "Pell's talk didn't count");
		helper.assertTrue(s.flags.containsKey(BanditKingStory.ALLY_PELL) && s.flags.containsKey(BanditFort.POSTERN_FLAG), "Pell's help isn't noted");

		// Swords for hire: 24 emeralds handed in at the hall.
		player.getInventory().add(new ItemStack(Items.EMERALD, 24));
		ChoiceMenu menu = ChoiceMenu.detached(player, m -> QuestJournal.render(m, level, hall, player, QuestJournal.Tab.STORY));
		menu.press(QuestJournal.STORY_QUEST_SLOTS[0], player);
		helper.assertTrue(s.flags.containsKey(BanditKingStory.ALLY_MERCENARIES) && player.getInventory().countItem(Items.EMERALD) == 0,
			"the mercenaries weren't paid: emeralds left " + player.getInventory().countItem(Items.EMERALD));

		// The neighbours' pledge: a quest finished at the village next door.
		BanditKingStory.questFinished(level, helper.absolutePos(OTHER_HALL));
		helper.assertTrue(s.flags.containsKey(BanditKingStory.ALLY_NEIGHBOURS), "no pledge");

		// The keep stands: the storm.
		s.flags.put("fort_done", ArcState.FOREVER);
		Arcs.round(level, hall, entity);
		helper.assertTrue(s.chapter == 2 && s.started, "the storm didn't begin: chapter " + s.chapter);
		List<Villager> allies = BanditKingStory.allies(level, hall);
		long guards = allies.stream().filter(v -> v.getCustomName() != null && v.getCustomName().getString().startsWith("Guard of ")).count();
		long mercs = allies.stream().filter(v -> v.getCustomName() != null && v.getCustomName().getString().equals("Mercenary")).count();
		helper.assertTrue(guards == 3 && mercs == 6, "allies at the storm: " + guards + " guards, " + mercs + " mercenaries");
		helper.assertTrue(level.getBlockState(fort.at(BanditFort.POSTERN)).getValue(DoorBlock.OPEN), "the postern isn't open");
		ItemStack rally = new ItemStack(ModItems.RALLY_BANNER);
		rally.set(ModComponents.RALLY, new RallyBannerItem.Rally(List.of(), true));
		player.getInventory().add(rally);
		player.teleportTo(fort.yard().getX() + 0.5, fort.yard().getY(), fort.yard().getZ() + 0.5);
		BanditKingStory.tick(level);
		List<java.util.UUID> following = new java.util.ArrayList<>();
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(ModItems.RALLY_BANNER)) {
				following.addAll(RallyBannerItem.rally(player.getInventory().getItem(i)).guards());
			}
		}
		helper.assertTrue(following.size() == 9 && allies.stream().allMatch(v -> following.contains(v.getUUID())), "following the banner: " + following.size());

		// The storm's first night: the King's raiders come (raids and festivals are off in game tests: on for this one).
		boolean raids = io.github.jcondedata.aliveworkplace.guard.VillageRaids.ENABLED;
		boolean festivals = io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED;
		Leftovers.after(helper, () -> {
			io.github.jcondedata.aliveworkplace.guard.VillageRaids.ENABLED = raids;
			io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = festivals;
		});
		io.github.jcondedata.aliveworkplace.guard.VillageRaids.ENABLED = true;
		io.github.jcondedata.aliveworkplace.hall.Festivals.ENABLED = true;
		level.setDayTime(day * DAY + 13000);
		BanditKingStory.tick(level);
		helper.assertTrue(s.flag("raid_night", day + 1), "no raid on the storm's first night");
		io.github.jcondedata.aliveworkplace.guard.VillageRaids.ENABLED = raids;

		// The King falls: the ending pays.
		king.hurt(level.damageSources().playerAttack(player), 10000f);
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc didn't end");
		helper.assertTrue(Reputation.points(level, hall, player.getUUID()) >= 150, "reputation: " + Reputation.points(level, hall, player.getUUID()));
		helper.assertTrue(Reputation.honours(level, hall, player.getUUID()).contains("kingslayer"), "no Kingslayer: " + Reputation.honours(level, hall, player.getUUID()));
		helper.assertTrue(Stories.villageFlag(level, hall, "no_bandit_camps"), "bandit camps may come");
		helper.assertTrue(entity.festivalDay() == day + 2, "no festival tomorrow: " + entity.festivalDay());
		helper.assertTrue(level.getBlockState(fort.at(BanditFort.TREASURE)).is(Blocks.CHEST), "the treasure chest is gone");
		helper.assertTrue(pell.isAlive() && pell.getTags().stream().noneMatch(t -> t.startsWith(Arcs.TAG) || t.startsWith("aliveworkplace_role_"))
			&& pell.getVillagerData().getProfession() == VillagerProfession.NONE && !pell.hasRestriction(), "Pell didn't join: " + pell.getTags());
		helper.assertTrue(allies.stream().allMatch(v -> ModAttachments.MERCENARY_UNTIL.get(v) <= level.getGameTime()), "the allies stay on");
		List<String> told = chronicle(entity);
		for (String line : List.of("The Bandit King, chapter 1: Smoke on Three Hills", "The Bandit King, chapter 2: The King's Fort",
			"The Bandit King, chapter 3: The Storm", "Pell the deserter told of the postern in the King's keep.",
			"The storm on the Bandit King's fort began, with 9 allies.", "The Bandit King's band is gone, and his treasure is the village's.",
			"The tale of The Bandit King came to its end.")) {
			helper.assertTrue(told.contains(line), "the chronicle lacks \"" + line + "\": " + told);
		}
		helper.assertTrue(told.stream().anyMatch(l -> l.startsWith("The Bandit King fell to ")), "no line for the King's fall");
		helper.succeed();
	}

	/** The King outlasts the storm: after 10 days he takes a third of the treasury, moods drop 10 for 3 days, and the arc fails. */
	//$ gametest_ticks_batch HUGE '60' '"banditKingTribute"'
	@GameTest(template = HUGE, timeoutTicks = 60, batch = "banditKingTribute")
	public void banditKingTribute(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, new BlockPos(5, 2, 5));
		BlockPos hall = helper.absolutePos(new BlockPos(5, 2, 5));
		Villager villager = helper.spawn(EntityType.VILLAGER, new BlockPos(7, 2, 5));
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		ArcState s = Arcs.start(level, hall, arc(helper));
		Arcs.next(level, hall);
		Arcs.next(level, hall);
		helper.assertTrue(s.chapter == 2 && s.started, "the storm didn't begin: chapter " + s.chapter);
		entity.setTreasury(3000);
		setDay(level, day + 9);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == s && entity.treasury() == 3000, "the tribute came early");
		setDay(level, day + 10);
		Arcs.round(level, hall, entity);
		helper.assertTrue(Arcs.running(level, hall) == null, "the arc didn't end");
		helper.assertTrue(entity.treasury() == 2000, "the treasury after the tribute: " + entity.treasury());
		helper.assertTrue(Stories.mood(level, villager) == -10, "the mood after the tribute: " + Stories.mood(level, villager));
		List<String> told = chronicle(entity);
		helper.assertTrue(told.contains("The Bandit King took 10 emeralds in tribute and left his fort a ruin.")
			&& told.contains("The tale of The Bandit King ended badly."), "the chronicle: " + told);
		setDay(level, day + 14);
		helper.assertTrue(Stories.mood(level, villager) == 0, "the mood lasts past 3 days: " + Stories.mood(level, villager));
		helper.succeed();
	}

	/** The trigger: a Town with 2 bandit camps broken up, with banditCamps on; the count is saved, and an old save reads 0. */
	//$ gametest_ticks_batch HUGE '20' '"banditKingTrigger"'
	@GameTest(template = HUGE, timeoutTicks = 20, batch = "banditKingTrigger")
	public void banditKingTrigger(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		hall(helper, new BlockPos(5, 2, 5));
		BlockPos hall = helper.absolutePos(new BlockPos(5, 2, 5));
		Arcs.Arc arc = arc(helper);
		helper.assertTrue(arc.chance() == 0.08f && arc.conditions().stream().anyMatch(c -> c.type().equals("rank_at_least"))
			&& arc.conditions().stream().anyMatch(c -> c.type().equals("bandit_camps_broken")), "the trigger: " + arc.conditions());
		Condition broken = Conditions.parse(JsonParser.parseString("{\"type\": \"bandit_camps_broken\", \"count\": 2}").getAsJsonObject());
		int before = Stories.banditCampsBroken(level, hall);
		BanditCamps.ENABLED = true;
		while (Stories.banditCampsBroken(level, hall) < 1) {
			Stories.banditCampBroken(level, hall);
		}
		helper.assertTrue(before > 1 || !broken.met(level, hall), "one camp broken was enough");
		Stories.banditCampBroken(level, hall);
		helper.assertTrue(broken.met(level, hall), "two camps broken weren't enough");
		BanditCamps.ENABLED = false;
		helper.assertTrue(!broken.met(level, hall), "met with banditCamps off");
		BanditCamps.ENABLED = true;
		// Saved, and an old save without the count reads 0.
		CompoundTag saved = Stories.Data.get(level).save(new CompoundTag(), level.registryAccess());
		int count = -1;
		ListTag halls = saved.getList("halls", Tag.TAG_COMPOUND);
		for (int i = 0; i < halls.size(); i++) {
			if (halls.getCompound(i).getLong("hall") == hall.asLong()) {
				count = halls.getCompound(i).getInt("bandit_camps_broken");
			}
		}
		helper.assertTrue(count == Stories.banditCampsBroken(level, hall) && count >= 2, "saved count " + count);
		CompoundTag old = new CompoundTag();
		ListTag oldHalls = new ListTag();
		CompoundTag h = new CompoundTag();
		h.putLong("hall", hall.asLong());
		oldHalls.add(h);
		old.put("halls", oldHalls);
		CompoundTag again = Stories.Data.load(old, level.registryAccess()).save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(!again.getList("halls", Tag.TAG_COMPOUND).getCompound(0).contains("bandit_camps_broken"), "an old save got a count");
		helper.succeed();
	}

	/**
	 * B205: a {@code ring} place (the Bandit King's camps, 80 to 104 out) never lies past the ring's edges. Each axis was
	 * rounded on its own, so a diagonal spot drawn 104 out could land 104.6 out, and the walk once found "a camp 105
	 * blocks out". 20,000 spots from a fixed seed, and the arc's own rule read from its JSON through {@code spot}.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void campRingStaysWithinItsEdges(GameTestHelper helper) {
		BlockPos hall = new BlockPos(1000, 64, -2000);
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(205);
		for (int i = 0; i < 20000; i++) {
			BlockPos p = ArcEffects.ring(hall, 80, 104, random);
			double d = Math.sqrt(p.distSqr(new BlockPos(hall.getX(), p.getY(), hall.getZ())));
			helper.assertTrue(p.getY() == hall.getY() && d >= 80 && d <= 104, "a spot " + d + " blocks out: " + p.subtract(hall));
		}
		com.google.gson.JsonObject rule = JsonParser.parseString("{\"ring\": [80, 104]}").getAsJsonObject();
		for (int i = 0; i < 2000; i++) {
			BlockPos p = ArcEffects.spot(helper.getLevel(), hall, null, rule);
			double d = Math.sqrt(p.distSqr(hall));
			helper.assertTrue(d >= 80 && d <= 104, "the arc's ring rule put a camp " + d + " blocks out");
		}
		helper.succeed();
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.guard.BanditCamps;
import io.github.jcondedata.aliveworkplace.guard.BanditFort;
import io.github.jcondedata.aliveworkplace.guard.BanditKing;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.BanditKingStory;
import io.github.jcondedata.aliveworkplace.story.Stories;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane qa-lane-d-1028-0109): The Bandit King, 31.15 and 31.16, tested from the spec. Adds what
 * {@link BanditFortGameTests} and {@link BanditKingStoryGameTests} leave out: the fort rule's 110-150 ring, the boss bar's
 * 32-block edge, the horn at exactly half health and after a reload, war banners broken by a player away from and at a
 * camp, the Town rank of the trigger, the arc saved mid-fort, every sentence a player reads, and the chest's weapon.
 */
public class QaBanditKing1028GameTests implements FabricGameTest {
	private static final String TALL = "aliveworkplace_test:tall_area";
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final BlockPos MIDDLE = new BlockPos(15, 3, 15);

	// ---- shared helpers --------------------------------------------------------------------------------------------

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

	private static void settings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean camps = BanditCamps.ENABLED;
		VillageHalls.RADIUS = 8;
		Arcs.NEAR = 0;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			BanditCamps.ENABLED = camps;
		});
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

	private static ServerPlayer survivor(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static Mob king(GameTestHelper helper, BlockPos door) {
		BlockPos at = helper.absolutePos(new BlockPos(10, 2, 10));
		Mob king = BanditKing.spawn(helper.getLevel(), at, at, helper.absolutePos(door));
		helper.assertTrue(king != null, "no King");
		return king;
	}

	private static List<Mob> called(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Mob.class, helper.getBounds().inflate(8), m -> m.isAlive() && m.getTags().contains(BanditKing.CALLED_TAG));
	}

	private static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	// ---- 31.15: the fort's ring ----------------------------------------------------------------------------------------

	/**
	 * Spec: 110 to 150 out. Where the ground isn't loaded, the arc's {@code fort} rule still picks a spot in that ring
	 * (2000 fixed draws round a hall nobody stands near), never nearer than 110 nor past 150.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaFortRuleOnUnloadedGroundStaysInRing(GameTestHelper helper) {
		BlockPos hall = new BlockPos(-90000, 70, 90000);
		com.google.gson.JsonObject rule = com.google.gson.JsonParser.parseString("{\"fort\": true}").getAsJsonObject();
		for (int i = 0; i < 2000; i++) {
			BlockPos p = ArcEffects.spot(helper.getLevel(), hall, null, rule);
			helper.assertTrue(p != null, "no spot for the fort");
			double d = flat(p, hall);
			helper.assertTrue(d >= 110 && d <= 150, "the fort rule put the fort " + d + " blocks out");
		}
		helper.succeed();
	}

	// ---- 31.15: the King -------------------------------------------------------------------------------------------

	/**
	 * Spec: a boss bar "for players within 32 blocks". A player 31.5 blocks off sees it, 32.5 off doesn't; walking out
	 * takes it away and walking back gives it again; it shows his health (half at 75 of 150).
	 */
	//$ gametest_ticks_batch TALL '40' '"qaBanditKingBarEdge"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaBanditKingBarEdge")
	public void qaKingsBarAtThe32BlockEdge(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		Mob king = king(helper, new BlockPos(12, 2, 10));
		king.setNoAi(true);
		ServerPlayer in = survivor(helper);
		ServerPlayer out = survivor(helper);
		in.teleportTo(king.getX() + 31.5, king.getY(), king.getZ());
		out.teleportTo(king.getX(), king.getY(), king.getZ() + 32.5);
		BanditFort.tick(level);
		ServerBossEvent bar = BanditKing.bar(king.getUUID());
		helper.assertTrue(bar != null, "no boss bar");
		helper.assertTrue(bar.getPlayers().contains(in), "a player 31.5 blocks off doesn't see the bar");
		helper.assertFalse(bar.getPlayers().contains(out), "a player 32.5 blocks off sees the bar");
		in.teleportTo(king.getX() + 40, king.getY(), king.getZ());
		out.teleportTo(king.getX(), king.getY(), king.getZ() + 10);
		BanditFort.tick(level);
		helper.assertFalse(bar.getPlayers().contains(in), "the bar stayed with a player who walked off to 40 blocks");
		helper.assertTrue(bar.getPlayers().contains(out), "a player who walked up to 10 blocks doesn't get the bar");
		king.setHealth(75);
		king.addTag(BanditKing.HORN_TAG); // (the horn is another test's)
		BanditFort.tick(level);
		helper.assertTrue(Math.abs(bar.getProgress() - 0.5f) < 0.01f, "the bar at 75 of 150 health shows " + bar.getProgress());
		king.discard();
		BanditFort.tick(level);
		in.discard();
		out.discard();
		helper.succeed();
	}

	/**
	 * Spec: "at half health he blows a goat horn and 4 bandits come out of the keep, once". At 76 of 150 nothing; at
	 * exactly 75 the horn, 4 bandits at the keep's door. Saved and loaded again (a restart), the King keeps his 150 max
	 * health, his crown, his fort, and doesn't call again when hurt to 30; his leash comes back.
	 */
	//$ gametest_ticks_batch TALL '40' '"qaBanditKingHornReload"'
	@GameTest(template = TALL, timeoutTicks = 40, batch = "qaBanditKingHornReload")
	public void qaHornAtExactlyHalfAndOnceAfterReload(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		BlockPos door = new BlockPos(14, 2, 10);
		Mob king = king(helper, door);
		king.setNoAi(true);
		king.setHealth(76);
		BanditFort.tick(level);
		helper.assertTrue(called(helper).isEmpty(), "he blew his horn at 76 of 150");
		king.setHealth(75);
		BanditFort.tick(level);
		List<Mob> called = called(helper);
		helper.assertTrue(called.size() == 4, "bandits called at exactly half health: " + called.size());
		BlockPos doorAbs = helper.absolutePos(door);
		for (Mob man : called) {
			helper.assertTrue(man.blockPosition().distManhattan(doorAbs) <= 3, "a called bandit came out away from the keep's door: " + man.blockPosition());
		}
		// The restart: the King saved and loaded again.
		CompoundTag saved = new CompoundTag();
		helper.assertTrue(king.save(saved), "the King didn't save");
		king.discard();
		BanditFort.tick(level);
		Entity loaded = EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(loaded instanceof Mob, "the King didn't load: " + loaded);
		Mob again = (Mob) loaded;
		helper.assertTrue(level.addFreshEntity(again), "the loaded King wasn't added");
		helper.assertTrue(Math.abs(again.getMaxHealth() - 150) < 0.01 && Math.abs(again.getHealth() - 75) < 0.01,
			"after the reload his health is " + again.getHealth() + "/" + again.getMaxHealth());
		helper.assertTrue(again.getItemBySlot(EquipmentSlot.HEAD).is(Items.GOLDEN_HELMET)
			&& "The King's Crown".equals(again.getItemBySlot(EquipmentSlot.HEAD).getHoverName().getString()), "his crown after the reload");
		again.clearRestriction();
		BanditFort.tick(level);
		helper.assertTrue(again.hasRestriction(), "after the reload he roams free");
		again.setHealth(30);
		BanditFort.tick(level);
		BanditFort.tick(level);
		helper.assertTrue(called(helper).size() == 4, "after a reload he called again: " + called(helper).size());
		again.discard();
		called(helper).forEach(Entity::discard);
		BanditFort.tick(level);
		helper.succeed();
	}

	// ---- 31.16: the war banners ------------------------------------------------------------------------------------

	/**
	 * Spec: "Take the three war banners: breaking one drops a named banner that counts." A player breaking (survival, the
	 * real break) a plain black banner by a camp counts nothing; a war banner far from every camp counts nothing; the
	 * camp's war banner counts once (a second one by the same camp doesn't count again); each break drops exactly one
	 * banner, the war banner named.
	 */
	//$ gametest_ticks_batch TALL '60' '"qaBanditKingBanners"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaBanditKingBanners")
	public void qaWarBannersCountOnlyByTheirCampAndOnce(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		Leftovers.players(helper);
		flatGround(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hallPos = new BlockPos(15, 4, 0);
		hall(helper, hallPos);
		BlockPos hall = helper.absolutePos(hallPos);
		ArcState s = Arcs.start(level, hall, Arcs.find("bandit_king").orElseThrow());
		helper.assertTrue(s != null && s.chapter == 0 && s.started, "the arc didn't start");
		s.mobs.forEach(m -> m.dead = true);
		List<ArcState.ArcPlace> camps = s.places.stream().filter(p -> p.stages.get(0).equals("aliveworkplace:camp/bandit_camp")).toList();
		helper.assertTrue(camps.size() == 3, "camps: " + camps.size());
		ArcState.ArcPlace first = camps.get(0);
		first.spot = helper.absolutePos(new BlockPos(4, 4, 4));
		first.placed = 1;
		for (int i = 1; i < 3; i++) {
			camps.get(i).spot = helper.absolutePos(new BlockPos(4, 4, 4)).offset(300 * i, 0, 0); // (far away, not built here)
			camps.get(i).placed = 1;
		}
		ServerPlayer player = survivor(helper);
		player.teleportTo(helper.absolutePos(new BlockPos(15, 4, 15)).getX(), helper.absolutePos(new BlockPos(15, 4, 15)).getY(),
			helper.absolutePos(new BlockPos(15, 4, 15)).getZ());

		// A plain black banner by the camp.
		BlockPos plain = helper.absolutePos(new BlockPos(6, 4, 4));
		level.setBlock(plain, Blocks.BLACK_BANNER.defaultBlockState(), 3);
		player.gameMode.destroyBlock(plain);
		helper.assertFalse(s.flags.containsKey("banner_" + first.key), "a plain black banner counted as a war banner");
		helper.assertTrue(banners(helper).size() == 1 && !named(banners(helper).get(0)), "the plain banner's drop: " + banners(helper).size());
		banners(helper).forEach(ItemEntity::discard);

		// A war banner far from every camp.
		BlockPos far = BanditKingStory.raiseBanner(level, helper.absolutePos(new BlockPos(26, 4, 26)));
		helper.assertTrue(far != null && BanditKingStory.isWarBanner(level, far), "no war banner raised far off");
		player.gameMode.destroyBlock(far);
		helper.assertFalse(s.flags.containsKey("banner_" + first.key), "a war banner 30 blocks from the camp counted");
		helper.assertTrue(banners(helper).size() == 1 && named(banners(helper).get(0)), "the far war banner's drop: " + banners(helper).size());
		banners(helper).forEach(ItemEntity::discard);

		// The camp's own war banner, broken by the player: it counts, once.
		BlockPos own = BanditKingStory.raiseBanner(level, first.spot);
		helper.assertTrue(own != null, "no war banner at the camp");
		player.gameMode.destroyBlock(own);
		helper.assertTrue(s.flags.containsKey("banner_" + first.key), "the camp's war banner didn't count");
		helper.assertTrue(banners(helper).size() == 1 && named(banners(helper).get(0)), "the war banner's drop: " + banners(helper).size());
		banners(helper).forEach(ItemEntity::discard);
		BlockPos second = BanditKingStory.raiseBanner(level, first.spot);
		player.gameMode.destroyBlock(second);
		banners(helper).forEach(ItemEntity::discard);
		long taken = camps.stream().filter(p -> s.flags.containsKey("banner_" + p.key)).count();
		helper.assertTrue(taken == 1 && s.chapter == 0, "after two banners at one camp: " + taken + " counted, chapter " + s.chapter);
		player.discard();
		helper.succeed();
	}

	private static List<ItemEntity> banners(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(4), e -> e.isAlive() && e.getItem().is(Items.BLACK_BANNER));
	}

	private static boolean named(ItemEntity e) {
		return "The Bandit King's War Banner".equals(e.getItem().getHoverName().getString()) && e.getItem().getCount() == 1;
	}

	// ---- 31.16: the trigger ----------------------------------------------------------------------------------------

	/**
	 * Spec: "It starts in a Town or bigger that has broken up at least 2 bandit camps ... with banditCamps on". The arc's
	 * own trigger conditions: a Village with 2 camps broken isn't enough; a Town is; a City is; with banditCamps off
	 * neither is.
	 */
	//$ gametest_ticks_batch HUGE '20' '"qaBanditKingRank"'
	@GameTest(template = HUGE, timeoutTicks = 20, batch = "qaBanditKingRank")
	public void qaTriggerNeedsATownOrBigger(GameTestHelper helper) {
		Leftovers.clear(helper);
		settings(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = hall(helper, new BlockPos(9, 2, 9));
		BlockPos hall = helper.absolutePos(new BlockPos(9, 2, 9));
		Arcs.Arc arc = Arcs.find("bandit_king").orElseThrow();
		BanditCamps.ENABLED = true;
		while (Stories.banditCampsBroken(level, hall) < 2) {
			Stories.banditCampBroken(level, hall);
		}
		entity.setRank(VillageRanks.Rank.VILLAGE);
		helper.assertFalse(arc.conditions().stream().allMatch(c -> c.met(level, hall)), "the King's arc may start in a Village");
		entity.setRank(VillageRanks.Rank.TOWN);
		helper.assertTrue(arc.conditions().stream().allMatch(c -> c.met(level, hall)), "the King's arc may not start in a Town with 2 camps broken");
		entity.setRank(VillageRanks.Rank.CITY);
		helper.assertTrue(arc.conditions().stream().allMatch(c -> c.met(level, hall)), "the King's arc may not start in a City with 2 camps broken");
		BanditCamps.ENABLED = false;
		helper.assertFalse(arc.conditions().stream().allMatch(c -> c.met(level, hall)), "the King's arc may start with banditCamps off");
		helper.succeed();
	}

	// ---- 31.15 + 31.16: saved mid-fort --------------------------------------------------------------------------------

	/**
	 * Save and reload mid-arc: two stages of the fort stand, then the arc's state is saved and loaded again (a restart),
	 * Pell tells of the postern, and the next dawn's stage goes on the same corner from the loaded state: the keep with
	 * its treasure chest, its postern open, one King, and no second lot of the earlier stages' men.
	 */
	//$ gametest_ticks_batch TALL '60' '"qaBanditFortSaved"'
	@GameTest(template = TALL, timeoutTicks = 60, batch = "qaBanditFortSaved")
	public void qaFortResumesFromASaveMidArc(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		flatGround(helper);
		ServerLevel level = helper.getLevel();
		ArcState s = new ArcState("aliveworkplace:bandit_king");
		ArcState.ArcPlace p = new ArcState.ArcPlace("fort", helper.absolutePos(MIDDLE), BanditFort.STAGES.stream().map(Object::toString).toList(), 0);
		s.places.add(p);
		helper.assertTrue(ArcEffects.build(level, s, p) && ArcEffects.build(level, s, p) && p.placed == 2, "two stages weren't built: " + p.placed);
		BanditFort.Fort fort = new BanditFort.Fort(p.origin, Rotation.NONE);
		int menBefore = BanditFort.men(level, fort.yard(), 24).size();
		helper.assertTrue(menBefore == 6, "men after two stages: " + menBefore);

		ArcState loaded = ArcState.load(s.save());
		ArcState.ArcPlace q = loaded.place("fort");
		helper.assertTrue(q != null && q.placed == 2 && p.origin.equals(q.origin) && q.nextDay == p.nextDay && !q.skipped,
			"the fort's place after a reload: " + (q == null ? "gone" : q.placed + " at " + q.origin + " due " + q.nextDay));
		loaded.flags.put(BanditFort.POSTERN_FLAG, ArcState.FOREVER);
		helper.assertTrue(ArcEffects.build(level, loaded, q) && q.placed == 3 && q.origin.equals(p.origin), "the keep wasn't built after the reload");
		helper.assertTrue(level.getBlockEntity(fort.at(BanditFort.TREASURE)) instanceof ChestBlockEntity chest && BanditFort.LOOT.equals(chest.getLootTable()),
			"no treasure chest in the keep");
		helper.assertTrue(level.getBlockState(fort.at(BanditFort.POSTERN)).getBlock() instanceof DoorBlock
			&& level.getBlockState(fort.at(BanditFort.POSTERN)).getValue(DoorBlock.OPEN), "the postern isn't open after Pell told of it");
		List<Mob> men = BanditFort.men(level, fort.yard(), 24);
		long kings = men.stream().filter(m -> m.getTags().contains(BanditKing.TAG)).count();
		helper.assertTrue(kings == 1 && men.size() == 7, "after the keep: " + men.size() + " men, " + kings + " kings");
		ArcState twice = ArcState.load(loaded.save());
		helper.assertTrue(twice.place("fort").placed == 3 && !twice.place("fort").waiting(), "the finished fort reads as unfinished after a reload");
		helper.succeed();
	}

	// ---- every sentence a player reads -------------------------------------------------------------------------------

	private static final Pattern BRITISH = Pattern.compile("(?i)\\b(honour|neighbour|colour|armour|defence|grey|centre|cancelled|favour|harbour|jewell)");

	/**
	 * Every Bandit King sentence a player reads has its text (no raw key), renders its arguments, reads as the spec says
	 * (the chatter, the captains' title, the King's name and the crown's), and is in American English.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaBanditKingTextReads(GameTestHelper helper) {
		Language lang = Language.getInstance();
		List<String> keys = new ArrayList<>(List.of("entity.aliveworkplace.bandit_king", "item.aliveworkplace.kings_crown", "honour.aliveworkplace.kingslayer",
			"arc.aliveworkplace.bandit_king", "entity.aliveworkplace.king_captain", "entity.aliveworkplace.pell", "entity.aliveworkplace.king_ally.neighbour",
			"entity.aliveworkplace.mercenary", "entity.aliveworkplace.bandit", "block.aliveworkplace.war_banner", "mood.aliveworkplace.reason.tribute",
			"arc.aliveworkplace.bandit_king.swords", "arc.aliveworkplace.bandit_king.deserter", "arc.aliveworkplace.bandit_king.pell_says",
			"arc.aliveworkplace.bandit_king.pell_asks", "chronicle.aliveworkplace.someone"));
		for (int c = 1; c <= 3; c++) {
			keys.add("arc.aliveworkplace.bandit_king." + c);
			keys.add("arc.aliveworkplace.bandit_king." + c + ".intro.1");
			keys.add("arc.aliveworkplace.bandit_king." + c + ".intro.2");
		}
		for (String k : List.of("banner", "fort", "storm")) {
			keys.add("chatter.aliveworkplace.bandit_king." + k);
		}
		for (String k : List.of("banner_taken", "ally_neighbours", "ally_mercenaries", "ally_pell", "storm", "allies_follow", "raid", "tribute", "pell_joins")) {
			keys.add("message.aliveworkplace.bandit_king." + k);
		}
		for (String k : List.of("banner_taken", "ally_neighbours", "ally_mercenaries", "ally_pell", "storm", "raid", "king_falls", "tribute", "ending", "pell_joins")) {
			keys.add("chronicle.aliveworkplace.bandit_king." + k);
		}
		for (String key : keys) {
			helper.assertTrue(lang.has(key), "no text for " + key);
			String text = lang.getOrDefault(key);
			helper.assertFalse(BRITISH.matcher(text).find(), "British spelling in " + key + ": " + text);
		}
		// Rendered with their real arguments.
		check(helper, Component.translatable("entity.aliveworkplace.bandit_king", "Grimwald"), "Grimwald, the Bandit King");
		check(helper, BanditKing.name(0), null);
		helper.assertTrue(BanditKing.name(0).getString().endsWith(", the Bandit King"), "the King's name: " + BanditKing.name(0).getString());
		check(helper, BanditKing.crown(helper.getLevel()).getHoverName(), "The King's Crown");
		check(helper, BanditKingStory.warBanner(helper.getLevel()).getHoverName(), "The Bandit King's War Banner");
		check(helper, Component.translatable("entity.aliveworkplace.king_captain"), "Captain of the Bandit King");
		check(helper, Component.translatable("chatter.aliveworkplace.bandit_king.banner"), "All the camps fly the same banner now.");
		check(helper, Component.translatable("arc.aliveworkplace.bandit_king.pell_asks", BanditKingStory.BREAD),
			"I've had enough of the King. Bring me 16 bread and I'll tell you a way into his keep.");
		check(helper, Component.translatable("message.aliveworkplace.bandit_king.banner_taken", 2, 3), "A war banner of the Bandit King is taken (2 of 3).");
		check(helper, Component.translatable("message.aliveworkplace.bandit_king.ally_neighbours", "Oakbrook", BanditKingStory.NEIGHBOUR_GUARDS),
			"Oakbrook pledges 3 guards for the storm on the King's fort.");
		check(helper, Component.translatable("message.aliveworkplace.bandit_king.ally_mercenaries", BanditKingStory.MERCENARIES), "6 mercenaries are paid to fight in the storm.");
		check(helper, Component.translatable("entity.aliveworkplace.king_ally.neighbour", "Oakbrook"), "Guard of Oakbrook");
		check(helper, Component.translatable("chronicle.aliveworkplace.bandit_king.banner_taken", "Steve", 1, 3), "Steve took a war banner of the Bandit King (1 of 3).");
		check(helper, Component.translatable("chronicle.aliveworkplace.bandit_king.pell_joins", "Pell"), "Pell, once a bandit, made a home in the village.");
		check(helper, Component.translatable("honour.aliveworkplace.kingslayer"), "Kingslayer");
		helper.succeed();
	}

	private static void check(GameTestHelper helper, Component c, String expected) {
		String text = c.getString();
		helper.assertFalse(text.contains("%") || text.contains("aliveworkplace."), "unrendered text: " + text);
		if (expected != null) {
			helper.assertTrue(expected.equals(text), "reads \"" + text + "\", expected \"" + expected + "\"");
		}
	}

	// ---- 31.15: the treasure chest's weapon ----------------------------------------------------------------------------

	/**
	 * Spec: the chest holds "an enchanted iron sword or crossbow" (one of them, enchanted): over 200 fixed rolls each
	 * holds exactly one weapon, enchanted, and both kinds turn up; the crown is there every time.
	 */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTreasureHasOneEnchantedSwordOrCrossbow(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		LootParams params = new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(helper.absolutePos(new BlockPos(0, 1, 0))))
			.create(LootContextParamSets.CHEST);
		var table = level.getServer().reloadableRegistries().getLootTable(BanditFort.LOOT);
		int swords = 0;
		int crossbows = 0;
		for (int i = 0; i < 200; i++) {
			List<ItemStack> items = table.getRandomItems(params, 1028L + i);
			List<ItemStack> weapons = items.stream().filter(s -> s.is(Items.IRON_SWORD) || s.is(Items.CROSSBOW)).toList();
			helper.assertTrue(weapons.size() == 1 && weapons.get(0).getCount() == 1 && weapons.get(0).isEnchanted(), "roll " + i + ": weapons " + weapons);
			if (weapons.get(0).is(Items.IRON_SWORD)) {
				swords++;
			} else {
				crossbows++;
			}
			helper.assertTrue(items.stream().filter(s -> s.is(Items.GOLDEN_HELMET)).count() == 1, "roll " + i + ": no crown");
		}
		helper.assertTrue(swords > 0 && crossbows > 0, "200 rolls gave " + swords + " swords and " + crossbows + " crossbows");
		helper.succeed();
	}
}

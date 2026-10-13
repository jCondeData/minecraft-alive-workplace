package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.bounty.Bounties;
import io.github.jcondedata.aliveworkplace.bounty.BountyBoardBlock;
import io.github.jcondedata.aliveworkplace.bounty.BountyPage;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.QuestJournal;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.threat.Threats;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The bounty board and bandit captains (ROADMAP 31.13). */
public class BountyGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final BlockPos CAMP = new BlockPos(18, 2, 18);

	/** A Village-rank hall with its quests quiet, the bounties forgotten, and the settings put back at the end. */
	private static BlockPos village(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		int radius = VillageHalls.RADIUS;
		int near = Arcs.NEAR;
		boolean enabled = Bounties.ENABLED;
		int threat = Threats.LEVEL;
		long time = level.getDayTime();
		VillageHalls.RADIUS = 16;
		Bounties.forget(level);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.NEAR = near;
			Bounties.ENABLED = enabled;
			Threats.LEVEL = threat;
			if (level.getBlockEntity(helper.absolutePos(HALL)) instanceof VillageHallBlockEntity e) {
				e.setAtPeace(false, -1);
			}
			Bounties.forget(level);
			level.setDayTime(time);
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.VILLAGE);
		return helper.absolutePos(HALL);
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
	}

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	private static Bounties.Bounty bounty(GameTestHelper helper, BlockPos hall, String quirk) {
		ResourceLocation id = AliveWorkplace.id(quirk);
		helper.assertTrue(Bounties.quirks().containsKey(id), "the quirk file didn't load: " + Bounties.quirks().keySet());
		return Bounties.post(helper.getLevel(), hall, id, 0, 0, hall.offset(160, 0, 0));
	}

	private static Mob captain(GameTestHelper helper, Bounties.Bounty b) {
		helper.assertTrue(Bounties.raise(helper.getLevel(), b, helper.absolutePos(CAMP), helper.absolutePos(CAMP.north(4))), "he didn't stand");
		Mob captain = helper.getLevel().getEntity(b.captain) instanceof Mob m ? m : null;
		helper.assertTrue(captain != null && captain.getTags().contains(Bounties.CAPTAIN_TAG), "no captain");
		helper.assertTrue(captain.getCustomName() != null && captain.getCustomName().getString().equals("Grimwald the Red"), "his name: " + captain.getCustomName());
		return captain;
	}

	/** Posted at Village rank and not at Hamlet; at most one every 3 days and 2 open; off with the config. */
	//$ gametest_ticks_batch AREA '20' '"bountyPosting"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyPosting")
	public void postedFromVillageRankTwoAtMostThreeDaysApart(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		RandomSource random = RandomSource.create(31);
		setDay(level, 100);
		entity.setRank(VillageRanks.Rank.HAMLET);
		helper.assertTrue(Bounties.post(level, hall, random) == null, "posted at Hamlet rank");
		entity.setRank(VillageRanks.Rank.VILLAGE);
		Bounties.ENABLED = false;
		helper.assertTrue(Bounties.post(level, hall, random) == null, "posted with bountyBoard off");
		Bounties.ENABLED = true;
		Bounties.Bounty first = Bounties.post(level, hall, random);
		helper.assertTrue(first != null, "nothing posted at Village rank");
		double far = Math.sqrt(first.hideout.spot.distSqr(hall));
		helper.assertTrue(far >= Bounties.NEAR - 2 && far <= Bounties.FAR + 2, "his hideout is " + far + " blocks out");
		helper.assertTrue(first.reward == 15, "12 emeralds times the Village factor: " + first.reward);
		helper.assertTrue(Bounties.post(level, hall, random) == null, "a second the same day");
		setDay(level, 102);
		helper.assertTrue(Bounties.post(level, hall, random) == null, "a second two days later");
		setDay(level, 103);
		helper.assertTrue(Bounties.post(level, hall, random) != null, "nothing three days later");
		setDay(level, 106);
		helper.assertTrue(Bounties.post(level, hall, random) == null, "a third while two are open");
		helper.assertTrue(Bounties.open(level, hall).size() == 2, "open: " + Bounties.open(level, hall).size());
		boolean chronicled = entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BOUNTY && e.text().getString().contains("Wanted"));
		helper.assertTrue(chronicled, "no BOUNTY line in the chronicle");
		helper.succeed();
	}

	private void quirk(GameTestHelper helper, String quirk, BiConsumer<Bounties.Bounty, Mob> check) {
		BlockPos hall = village(helper);
		Bounties.Bounty b = bounty(helper, hall, quirk);
		Mob captain = captain(helper, b);
		check.accept(b, captain);
		helper.assertTrue(Bounties.band(helper.getLevel(), b).size() >= 3, "no band: " + Bounties.band(helper.getLevel(), b).size());
		helper.succeed();
	}

	//$ gametest_ticks_batch AREA '20' '"bountyIronhide"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyIronhide")
	public void ironhideWearsIronAndHasTwentyMoreHealth(GameTestHelper helper) {
		quirk(helper, "ironhide", (b, captain) -> {
			helper.assertTrue(captain instanceof Vindicator && captain.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
				&& captain.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(Items.IRON_BOOTS), "his armour");
			helper.assertTrue(captain.getMaxHealth() == 44f && captain.getHealth() == 44f, "health " + captain.getMaxHealth());
			int band = b.band.size();
			helper.assertTrue(band == 2 || band == 3, "a band of 2 or 3: " + band);
		});
	}

	//$ gametest_ticks_batch AREA '20' '"bountyFleetfoot"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyFleetfoot")
	public void fleetfootHasSpeedTwo(GameTestHelper helper) {
		quirk(helper, "fleetfoot", (b, captain) -> {
			var speed = captain.getEffect(MobEffects.MOVEMENT_SPEED);
			helper.assertTrue(speed != null && speed.getAmplifier() == 1, "speed: " + speed);
		});
	}

	//$ gametest_ticks_batch AREA '20' '"bountyCrossbowAce"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyCrossbowAce")
	public void crossbowAceIsAPillagerWithMultishotAndQuickCharge(GameTestHelper helper) {
		quirk(helper, "crossbow_ace", (b, captain) -> {
			ItemStack bow = captain.getMainHandItem();
			var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			helper.assertTrue(captain instanceof Pillager && bow.is(Items.CROSSBOW), "a pillager with a crossbow: " + captain + " " + bow);
			helper.assertTrue(EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.MULTISHOT), bow) == 1
				&& EnchantmentHelper.getItemEnchantmentLevel(enchantments.getOrThrow(Enchantments.QUICK_CHARGE), bow) == 2, "enchantments: " + bow.getEnchantments());
		});
	}

	//$ gametest_ticks_batch AREA '20' '"bountyBeastRider"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyBeastRider")
	public void beastRiderRidesARavager(GameTestHelper helper) {
		quirk(helper, "beast_rider", (b, captain) -> helper.assertTrue(captain.getVehicle() instanceof Ravager, "riding " + captain.getVehicle()));
	}

	//$ gametest_ticks_batch AREA '20' '"bountyNightStalker"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyNightStalker")
	public void nightStalkerIsInvisibleAtNightUntilHit(GameTestHelper helper) {
		quirk(helper, "night_stalker", (b, captain) -> {
			ServerLevel level = helper.getLevel();
			level.setDayTime(1000);
			Bounties.stalk(level, captain);
			helper.assertTrue(!captain.hasEffect(MobEffects.INVISIBILITY), "invisible by day");
			level.setDayTime(18000);
			Bounties.stalk(level, captain);
			helper.assertTrue(captain.hasEffect(MobEffects.INVISIBILITY), "seen at night");
			captain.hurt(level.damageSources().generic(), 2f);
			Bounties.stalk(level, captain);
			helper.assertTrue(!captain.hasEffect(MobEffects.INVISIBILITY) && captain.getTags().contains(Bounties.SEEN_TAG), "still invisible once hit");
			captain.heal(100f);
			Bounties.stalk(level, captain);
			helper.assertTrue(!captain.hasEffect(MobEffects.INVISIBILITY), "hid again after healing");
		});
	}

	//$ gametest_ticks_batch AREA '20' '"bountyWarlord"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyWarlord")
	public void warlordHasABandOfFive(GameTestHelper helper) {
		quirk(helper, "warlord", (b, captain) -> helper.assertTrue(b.band.size() == 5, "band: " + b.band.size()));
	}

	//$ gametest_ticks_batch AREA '20' '"bountyHexer"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyHexer")
	public void hexerIsAnEvoker(GameTestHelper helper) {
		quirk(helper, "hexer", (b, captain) -> helper.assertTrue(captain instanceof Evoker, "an evoker: " + captain));
	}

	/**
	 * The hideout waits until a player comes near; then the arc engine builds the camp, with his banner on a pole by the
	 * fire, him and his band in it. (The land next to the test area stands in for the land 160 blocks out.)
	 */
	//$ gametest_ticks_batch AREA '200' '"bountyHideout"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "bountyHideout")
	public void theHideoutIsBuiltOnceAPlayerIsNear(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.players(helper);
		Arcs.NEAR = 6;
		BlockPos spot = hall.offset(38, -2, 0);
		for (ServerPlayer p : List.copyOf(level.players())) {
			if (p.blockPosition().distSqr(spot) < 64 * 64) {
				level.getServer().getPlayerList().remove(p); // an earlier batch's mock player standing by the spot
			}
		}
		Bounties.Bounty b = Bounties.post(level, hall, AliveWorkplace.id("ironhide"), 3, 4, spot);
		BlockPos[] built = new BlockPos[1];
		Leftovers.after(helper, () -> {
			if (built[0] != null) {
				for (BlockPos q : BlockPos.betweenClosed(built[0], built[0].offset(20, 9, 20))) {
					level.setBlock(q, q.getY() == built[0].getY() ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
				}
			}
			level.getEntitiesOfClass(Mob.class, new AABB(spot).inflate(40), m -> m.getTags().contains(Bounties.TAG)).forEach(Mob::discard);
		});
		ServerPlayer player = player(helper);
		helper.runAfterDelay(60, () -> {
			helper.assertTrue(b.hideout.waiting() && b.captain == null, "built with nobody near");
			player.teleportTo(spot.getX() + 0.5, spot.getY() + 2, spot.getZ() + 0.5);
		});
		helper.runAfterDelay(160, () -> {
			built[0] = b.hideout.spot.offset(-10, -1, -10);
			helper.assertTrue(!b.hideout.waiting() && b.hideout.origin != null, "the hideout isn't built: skipped " + b.hideout.skipped);
			helper.assertTrue(b.captain != null && level.getEntity(b.captain) instanceof Vindicator v && v.getTags().contains(Bounties.CAPTAIN_TAG),
				"no captain at the hideout");
			helper.assertTrue(BlockPos.betweenClosedStream(new AABB(b.hideout.spot).inflate(12, 6, 12)).anyMatch(q -> level.getBlockState(q).is(net.minecraft.tags.BlockTags.BANNERS)),
				"no banner at the hideout");
			helper.succeed();
		});
	}

	/** Taking a bounty in the journal gives the poster (name, quirk, where, reward) and a map, once; villagers gossip. */
	//$ gametest_ticks_batch AREA '20' '"bountyTake"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyTake")
	public void takingABountyGivesThePosterAndAMap(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = bounty(helper, hall, "fleetfoot");
		ServerPlayer me = player(helper);
		ChoiceMenu menu = ChoiceMenu.detached(me, m -> QuestJournal.render(m, level, hall, me, QuestJournal.Tab.BOUNTIES));
		helper.assertTrue(menu.icon(BountyPage.ROW).is(ModItems.WANTED_POSTER), "the tab: " + menu.icon(BountyPage.ROW));
		menu.press(BountyPage.ROW, me);
		ItemStack poster = me.getInventory().items.stream().filter(s -> s.is(ModItems.WANTED_POSTER)).findFirst().orElse(ItemStack.EMPTY);
		helper.assertTrue(!poster.isEmpty() && me.getInventory().contains(s -> s.is(Items.FILLED_MAP)), "no poster and map");
		List<String> lore = poster.get(DataComponents.LORE).lines().stream().map(c -> c.getString()).toList();
		helper.assertTrue(lore.size() == 4 && lore.get(0).contains("Grimwald the Red") && lore.get(1).contains("Fleetfoot")
			&& lore.get(2).contains("160 blocks east") && lore.get(3).contains("15"), "the poster: " + lore);
		menu.press(BountyPage.ROW, me);
		helper.assertTrue(me.getInventory().items.stream().filter(s -> s.is(ModItems.WANTED_POSTER)).count() == 1, "a second poster");
		var gossip = Bounties.gossip(level, hall, 0);
		helper.assertTrue(gossip != null && gossip.getString().contains("Grimwald the Red") && gossip.getString().contains("160 blocks east"), "gossip: " + gossip);
		helper.succeed();
	}

	/** The insignia drops when he dies and pays once, to whoever hands it in; a second can't claim it again. */
	//$ gametest_ticks_batch AREA '40' '"bountyInsignia"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "bountyInsignia")
	public void theInsigniaDropsAndPaysOnce(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = bounty(helper, hall, "hexer");
		Mob captain = captain(helper, b);
		ServerPlayer killer = player(helper);
		ServerPlayer other = player(helper);
		captain.hurt(level.damageSources().playerAttack(killer), 1000f);
		helper.runAfterDelay(2, () -> {
			ItemEntity drop = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CAMP)).inflate(4),
				e -> e.getItem().is(ModItems.CAPTAINS_INSIGNIA)).stream().findFirst().orElse(null);
			helper.assertTrue(drop != null && b.fallen, "no insignia dropped");
			ItemStack insignia = drop.getItem().copy();
			ItemStack copy = insignia.copy();
			drop.discard();
			other.getInventory().add(insignia);
			helper.assertTrue(Bounties.claimAny(other, level, hall) == Bounties.Claim.PAID, "not paid");
			helper.assertTrue(other.getInventory().countItem(Items.EMERALD) == 15 && other.getInventory().countItem(ModItems.CAPTAINS_INSIGNIA) == 0,
				"paid " + other.getInventory().countItem(Items.EMERALD));
			killer.getInventory().add(copy);
			helper.assertTrue(Bounties.claimAny(killer, level, hall) == Bounties.Claim.ALREADY && killer.getInventory().countItem(Items.EMERALD) == 0, "paid twice");
			VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(entity.chronicle().stream().anyMatch(e -> e.kind() == Chronicle.Kind.BOUNTY && e.text().getString().contains(other.getName().getString())
				&& e.text().getString().contains("Grimwald the Red")), "the chronicle doesn't say who");
			helper.assertTrue(Bounties.open(level, hall).isEmpty(), "still open");
			helper.succeed();
		});
	}

	/** An unclaimed bounty comes down after 7 days and its band leaves. */
	//$ gametest_ticks_batch AREA '20' '"bountyExpiry"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyExpiry")
	public void theBountyComesDownAfterSevenDaysWithItsBand(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		setDay(level, 200);
		Bounties.Bounty b = bounty(helper, hall, "warlord");
		captain(helper, b);
		helper.assertTrue(Bounties.band(level, b).size() == 6, "band and captain: " + Bounties.band(level, b).size());
		setDay(level, 206);
		Bounties.round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).size() == 1, "down after 6 days");
		setDay(level, 207);
		Bounties.round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "still up after 7 days");
		helper.assertTrue(Bounties.band(level, b).isEmpty(), "the band stayed");
		helper.succeed();
	}

	/** The board shows as many posters as open bounties (up to 4), and opens the journal's Bounties tab. */
	//$ gametest_ticks_batch AREA '20' '"bountyBoardPosters"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyBoardPosters")
	public void theBoardShowsAPosterPerOpenBounty(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		BlockPos board = new BlockPos(8, 2, 5);
		helper.setBlock(board, ModBlocks.BOUNTY_BOARD);
		helper.assertTrue(helper.getBlockState(board).getValue(BountyBoardBlock.POSTERS) == 0, "posters with none open");
		Bounties.Bounty one = bounty(helper, hall, "hexer");
		helper.assertTrue(helper.getBlockState(board).getValue(BountyBoardBlock.POSTERS) == 1, "one open: " + helper.getBlockState(board));
		setDay(level, Chronicle.day(level) + 3);
		bounty(helper, hall, "fleetfoot");
		helper.assertTrue(helper.getBlockState(board).getValue(BountyBoardBlock.POSTERS) == 2, "two open: " + helper.getBlockState(board));
		Bounties.takeDown(level, one);
		helper.assertTrue(helper.getBlockState(board).getValue(BountyBoardBlock.POSTERS) == 1, "one taken down: " + helper.getBlockState(board));
		helper.succeed();
	}

	/** A save and a load keep every bounty as it stood (who took it, his band, the hideout); a hall's round goes on from it. */
	//$ gametest_ticks_batch AREA '20' '"bountySave"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountySave")
	public void bountiesSurviveASaveAndLoad(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.Bounty b = bounty(helper, hall, "beast_rider");
		captain(helper, b);
		b.takers.add(java.util.UUID.randomUUID());
		CompoundTag saved = Bounties.Data.get(level).save(new CompoundTag(), level.registryAccess());
		Bounties.Data loaded = Bounties.Data.load(saved, level.registryAccess());
		CompoundTag again = loaded.save(new CompoundTag(), level.registryAccess());
		helper.assertTrue(saved.equals(again), "changed by a reload:\n" + saved + "\n" + again);
		Bounties.Bounty back = loaded.bounties.get(b.id);
		helper.assertTrue(back != null && back.captain.equals(b.captain) && back.band.equals(b.band) && back.takers.equals(b.takers)
			&& back.hideout.spot.equals(b.hideout.spot) && back.reward == b.reward, "the bounty came back as " + back);
		// An old save without the newer fields still loads (defaults).
		CompoundTag bare = new CompoundTag();
		Bounties.Data empty = Bounties.Data.load(bare, level.registryAccess());
		helper.assertTrue(empty.bounties.isEmpty() && empty.boards.isEmpty(), "an empty save");
		helper.succeed();
	}

	/** With {@code bountyBoard} off the tab says so and nothing is posted by the hall's round. */
	//$ gametest_ticks_batch AREA '20' '"bountyOff"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyOff")
	public void configOffPostsNothing(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		Bounties.ENABLED = false;
		boolean auto = Bounties.AUTO;
		Bounties.AUTO = true;
		try {
			Bounties.round(level, hall);
		} finally {
			Bounties.AUTO = auto;
		}
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "posted while off");
		ServerPlayer me = player(helper);
		ChoiceMenu menu = ChoiceMenu.detached(me, m -> QuestJournal.render(m, level, hall, me, QuestJournal.Tab.BOUNTIES));
		String lore = menu.icon(22).get(DataComponents.LORE).lines().toString();
		helper.assertTrue(lore.contains("screen.aliveworkplace.journal.bounties.off"), "the tab: " + lore);
		helper.succeed();
	}

	/** The hall's round with its posting switched on, as the server runs it. */
	private static void round(ServerLevel level, BlockPos hall) {
		boolean auto = Bounties.AUTO;
		Bounties.AUTO = true;
		try {
			Bounties.round(level, hall);
		} finally {
			Bounties.AUTO = auto;
		}
	}

	/** A Village-rank village set At peace posts no captain, by its round or by hand, and the tab says why (B134). */
	//$ gametest_ticks_batch AREA '20' '"bountyAtPeace"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyAtPeace")
	public void anAtPeaceVillagePostsNoBounty(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		setDay(level, 300);
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setAtPeace(true, 300);
		helper.assertTrue(Bounties.quiet(level, hall) && !Bounties.mayPost(level, hall), "an At peace village may post");
		round(level, hall);
		helper.assertTrue(Bounties.post(level, hall, RandomSource.create(134)) == null, "posted by hand while At peace");
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "posted while At peace: " + Bounties.open(level, hall).size());
		ServerPlayer me = player(helper);
		ChoiceMenu menu = ChoiceMenu.detached(me, m -> QuestJournal.render(m, level, hall, me, QuestJournal.Tab.BOUNTIES));
		String lore = menu.icon(22).get(DataComponents.LORE).lines().toString();
		helper.assertTrue(lore.contains("screen.aliveworkplace.journal.bounties.quiet"), "the tab: " + lore);
		helper.succeed();
	}

	/** At threat level 0 no village posts a captain (B134). */
	//$ gametest_ticks_batch AREA '20' '"bountyThreatZero"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyThreatZero")
	public void threatLevelZeroPostsNoBounty(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		setDay(level, 300);
		Threats.LEVEL = 0;
		helper.assertTrue(Bounties.quiet(level, hall) && !Bounties.mayPost(level, hall), "a village may post at threat level 0");
		round(level, hall);
		helper.assertTrue(Bounties.post(level, hall, RandomSource.create(134)) == null, "posted by hand at threat level 0");
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "posted at threat level 0: " + Bounties.open(level, hall).size());
		helper.succeed();
	}

	/** A village that isn't quiet (threat level 1 to 3, not At peace) still posts one by its round (B134). */
	//$ gametest_ticks_batch AREA '20' '"bountyNotQuiet"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyNotQuiet")
	public void aNormalVillageStillPostsOne(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		setDay(level, 300);
		for (int threat = 1; threat <= 3; threat++) {
			Threats.LEVEL = threat;
			helper.assertTrue(!Bounties.quiet(level, hall) && Bounties.mayPost(level, hall), "quiet at threat level " + threat);
		}
		Threats.LEVEL = 2;
		round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).size() == 1, "the round posted " + Bounties.open(level, hall).size());
		helper.succeed();
	}

	/** An open bounty stays when the village turns At peace, comes down when its days run out, and none follows (B134). */
	//$ gametest_ticks_batch AREA '20' '"bountyPeaceKeepsOpen"'
	@GameTest(template = AREA, timeoutTicks = 20, batch = "bountyPeaceKeepsOpen")
	public void anOpenBountySurvivesTheVillageTurningPeaceful(GameTestHelper helper) {
		BlockPos hall = village(helper);
		ServerLevel level = helper.getLevel();
		setDay(level, 300);
		Bounties.Bounty b = bounty(helper, hall, "fleetfoot");
		((VillageHallBlockEntity) level.getBlockEntity(hall)).setAtPeace(true, 300);
		setDay(level, 304);
		round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).size() == 1 && Bounties.open(level, hall).get(0).id.equals(b.id),
			"the open bounty didn't stay alone: " + Bounties.open(level, hall).size());
		ServerPlayer me = player(helper);
		helper.assertTrue(Bounties.take(me, level, b) && b.takers.contains(me.getUUID()), "can't take the open bounty At peace");
		setDay(level, 307);
		round(level, hall);
		helper.assertTrue(Bounties.open(level, hall).isEmpty(), "still up after 7 days, or a new one posted: " + Bounties.open(level, hall).size());
		helper.succeed();
	}
}

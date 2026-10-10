package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.phys.Vec3;

/**
 * ROADMAP 33.7, caravans you can see: when a caravan leaves or arrives in a village with a player within 96 blocks, a
 * carter in the porter's outfit ("Thornholm's caravan") leads two llamas with chests and carpets in the village's
 * colour from the Storehouse to the edge of the village (and is gone there), or in from the edge to the Storehouse,
 * where the llamas stand while the goods are unloaded, and back out. They are only a sight: the goods travel the saved
 * way, the same with the switch on or off. Also what's likeliest to break: nobody near, the switch off, a second
 * caravan while one is out, the two-minute limit, a party left by a restart, and everything a player or the village
 * might try with them (trading, riding, feeding, hurting, hiring, counting them as villagers).
 * Two halls stand in one test area as in {@code CaravanTradeGameTests}: Thornholm (A) sends Ashford (B) the bread it
 * waits for.
 */
public class CaravanSightGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL_A = new BlockPos(3, 2, 3);
	private static final BlockPos STORE_A = new BlockPos(5, 2, 3);
	private static final BlockPos CHEST_A = new BlockPos(5, 2, 5);
	private static final BlockPos HALL_B = new BlockPos(18, 2, 18);
	private static final BlockPos STORE_B = new BlockPos(16, 2, 18);
	private static final BlockPos CHEST_B = new BlockPos(16, 2, 20);

	/** The two villages of a test: their halls, their Storehouses' chests and the caravans' list. */
	private record Two(ServerLevel level, BlockPos a, BlockPos b, Container ours, Container theirs, Caravans.Data data) {
		VillageHallBlockEntity hallA() {
			return (VillageHallBlockEntity) level.getBlockEntity(a);
		}
	}

	/**
	 * Builds both villages, each with a hall, a Storehouse and a chest, alone in the batch, with a small village radius,
	 * caravans that arrive at once, no hall rounds of their own during the test, and caravan sights on or off. Everything
	 * is put back when the test ends, and any party still out is taken away.
	 */
	private static Two build(GameTestHelper helper, boolean sights) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		long travel = Caravans.MIN_TRAVEL;
		int every = VillageNeeds.CHECK_EVERY;
		boolean enabled = CaravanSights.ENABLED;
		VillageHalls.RADIUS = 8;
		Caravans.MIN_TRAVEL = 0;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		CaravanSights.ENABLED = sights;
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(HALL_A);
		BlockPos b = helper.absolutePos(HALL_B);
		Caravans.Data data = Caravans.Data.get(level);
		Leftovers.after(helper, () -> {
			CaravanSights.clear(level, a);
			CaravanSights.clear(level, b);
			VillageHalls.RADIUS = radius;
			Caravans.MIN_TRAVEL = travel;
			VillageNeeds.CHECK_EVERY = every;
			CaravanSights.ENABLED = enabled;
			data.remove(a);
			data.remove(b);
			data.clearRoad(a);
			data.clearRoad(b);
		});
		helper.setBlock(HALL_A, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STORE_A, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_A, Blocks.CHEST);
		helper.setBlock(HALL_B, ModBlocks.VILLAGE_HALL);
		helper.setBlock(STORE_B, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST_B, Blocks.CHEST);
		return new Two(level, a, b, helper.getBlockEntity(CHEST_A), helper.getBlockEntity(CHEST_B), data);
	}

	/** Names both villages on the caravans' list, Ashford waiting for 16 bread, with 40 bread in Thornholm's chest and a route from A to B. */
	private static void open(GameTestHelper helper, Two two) {
		two.data().setWants(two.a(), Component.literal("Thornholm"), List.of());
		two.data().setWants(two.b(), Component.literal("Ashford"), List.of(new Caravans.Want(Items.BREAD, 16)));
		two.ours().setItem(0, new ItemStack(Items.BREAD, 40));
		helper.assertTrue(two.data().routesFrom(two.a()).contains(two.b()) || two.data().toggleRoute(two.a(), two.b()), "no route");
	}

	/** The caravans on the road to Ashford come in now (the road takes a few ticks even between these two), and its hall's round unloads them. */
	private static void arrive(Two two) {
		two.data().hurry(two.b());
		Caravans.round(two.level(), two.b(), null);
	}

	/** A player standing in the test area, logged out when the test ends. */
	private static ServerPlayer playerAt(GameTestHelper helper, BlockPos relative) {
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BlockPos at = helper.absolutePos(relative);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		return player;
	}

	/** Every caravan party member anywhere near the test area. */
	private static List<Entity> tagged(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Entity.class, helper.getBounds().inflate(48), CaravanSights::isParty);
	}

	/** Items lying anywhere near the test area (a lead, a chest, a carpet: nothing may be left behind). */
	private static List<String> dropped(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(48), e -> true).stream()
			.map(e -> e.getItem().toString()).toList();
	}

	private static double flat(Vec3 from, BlockPos to) {
		double dx = from.x - (to.getX() + 0.5);
		double dz = from.z - (to.getZ() + 0.5);
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** What a client sends when its player right-clicks {@code mob}: the click at a spot on it, then the plain click. */
	private static void rightClick(ServerPlayer player, Mob mob, ItemStack held) {
		player.setItemInHand(InteractionHand.MAIN_HAND, held);
		player.moveTo(mob.getX() + 1.5, mob.getY(), mob.getZ(), 90f, 0f);
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(mob, false, InteractionHand.MAIN_HAND, new Vec3(0, mob.getBbHeight() / 2, 0)));
		player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(mob, false, InteractionHand.MAIN_HAND));
	}

	/** A fence round Thornholm's hall, Storehouse and chest: a party there can't walk off, so it stays to be looked at. */
	private static void pen(GameTestHelper helper) {
		for (int i = 1; i <= 8; i++) {
			helper.setBlock(new BlockPos(i, 2, 1), Blocks.OAK_FENCE);
			helper.setBlock(new BlockPos(i, 2, 8), Blocks.OAK_FENCE);
			helper.setBlock(new BlockPos(1, 2, i), Blocks.OAK_FENCE);
			helper.setBlock(new BlockPos(8, 2, i), Blocks.OAK_FENCE);
		}
	}

	/**
	 * The item's first test. A caravan leaves Thornholm with a player there: the bread is on the road the saved way, and
	 * a carter in the porter's outfit named "Thornholm's caravan" stands by the Storehouse with two llamas on leads, each
	 * with a chest and a carpet in the village's colour (no banner: the one picked from the hall's position). They walk
	 * to the edge of the village toward Ashford and are gone there, long before the two minutes, leaving nothing behind.
	 */
	//$ gametest_ticks_batch AREA '400' '"caravanSightLeaves"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "caravanSightLeaves")
	public void aLeavingCaravanIsSeenWalkingOffAndIsGone(GameTestHelper helper) {
		Two two = build(helper, true);
		playerAt(helper, new BlockPos(10, 2, 3));
		Vec3[] last = new Vec3[1];
		CaravanSights.Party[] seen = new CaravanSights.Party[1];
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 24, "loaded: " + two.ours().countItem(Items.BREAD));
			List<Caravans.Shipment> road = two.data().onTheRoad().stream().filter(s -> s.to().equals(two.b())).toList();
			helper.assertTrue(road.size() == 1 && road.get(0).goods().size() == 1 && road.get(0).goods().get(0).is(Items.BREAD)
				&& road.get(0).goods().get(0).getCount() == 16, "on the road: " + road);

			CaravanSights.Party party = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(party != null, "no party was seen leaving");
			seen[0] = party;
			helper.assertTrue(party.phase() == CaravanSights.Phase.LEAVING && party.llamas().size() == 2, "the party: " + party.phase() + ", " + party.llamas().size() + " llamas");
			Villager carter = party.carter();
			helper.assertTrue(carter.getVillagerData().getProfession() == ModVillagers.PORTER, "the carter's outfit: " + carter.getVillagerData().getProfession());
			helper.assertTrue(carter.getCustomName() != null && carter.getCustomName().getString().equals("Thornholm's caravan"),
				"the carter's name: " + (carter.getCustomName() == null ? null : carter.getCustomName().getString()));
			helper.assertTrue(flat(carter.position(), helper.absolutePos(STORE_A)) <= 6.5, "the carter starts " + flat(carter.position(), helper.absolutePos(STORE_A)) + " from the Storehouse");
			helper.assertTrue(flat(Vec3.atBottomCenterOf(party.edge()), two.a()) >= 6 && party.edge().distSqr(two.b()) < party.yard().distSqr(two.b()),
				"the edge should be at the village's rim, toward Ashford: " + party.edge() + " from yard " + party.yard());
			DyeColor colour = CaravanSights.picked(two.a());
			for (Llama llama : party.llamas()) {
				helper.assertTrue(llama.hasChest(), "a llama without a chest");
				helper.assertTrue(llama.getBodyArmorItem().is(CaravanSights.carpet(colour)), "the carpet should be " + colour + ": " + llama.getBodyArmorItem());
				helper.assertTrue(llama.getLeashHolder() == carter, "a llama isn't on the carter's lead");
				helper.assertTrue(llama.isAlive() && !llama.isRemoved() && CaravanSights.isParty(llama), "a llama isn't in the world");
			}
			helper.assertTrue(tagged(helper).size() == 3, "the party in the world: " + tagged(helper));
			last[0] = carter.position();
		});
		helper.onEachTick(() -> {
			if (seen[0] != null && !seen[0].carter().isRemoved()) {
				last[0] = seen[0].carter().position();
			}
		});
		helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
			CaravanSights.Party party = seen[0];
			helper.assertTrue(party != null && CaravanSights.party(two.level(), two.a()) == null, "the party is still out");
			helper.assertTrue(party.carter().isRemoved() && party.llamas().stream().allMatch(Entity::isRemoved) && tagged(helper).isEmpty(),
				"someone was left behind: " + tagged(helper));
			helper.assertTrue(flat(last[0], party.edge()) <= 2.5, "the carter was last seen " + flat(last[0], party.edge()) + " from the edge, at " + last[0]);
			helper.assertTrue(dropped(helper).isEmpty(), "left on the ground: " + dropped(helper));
			helper.assertTrue(two.data().onTheRoad().stream().anyMatch(s -> s.to().equals(two.b())), "the goods left the road with the party");
		}));
	}

	/**
	 * A caravan arrives in Ashford with a player there: the bread is in its chest the saved way, and Thornholm's carter
	 * (by name, with carpets in Thornholm's banner colour, red) comes in from the edge toward Thornholm to the
	 * Storehouse, where both llamas stand still while the goods are unloaded; then they walk back out to the edge and
	 * are gone, leaving nothing behind.
	 */
	//$ gametest_ticks_batch AREA '700' '"caravanSightArrives"'
	@GameTest(template = AREA, timeoutTicks = 700, batch = "caravanSightArrives")
	public void anArrivingCaravanComesInUnloadsAndGoesBack(GameTestHelper helper) {
		Two two = build(helper, true);
		playerAt(helper, new BlockPos(10, 2, 18));
		boolean banners = VillageBanners.ENABLED;
		VillageBanners.ENABLED = true;
		Leftovers.after(helper, () -> VillageBanners.ENABLED = banners);
		CaravanSights.Party[] seen = new CaravanSights.Party[1];
		Vec3[] last = new Vec3[1];
		List<Vec3> standing = new ArrayList<>();
		double[] moved = new double[1];
		int[] unloading = new int[1];
		boolean[] returned = new boolean[1];
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			two.hallA().setColours(new VillageBanners.Colours(DyeColor.RED, BannerPatternLayers.EMPTY));
			Caravans.round(two.level(), two.a(), null);
			CaravanSights.clear(two.level(), two.a()); // the one leaving Thornholm isn't this test's
			arrive(two);
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16, "arrived: " + two.theirs().countItem(Items.BREAD));
			CaravanSights.Party party = CaravanSights.party(two.level(), two.b());
			helper.assertTrue(party != null, "no party was seen arriving");
			seen[0] = party;
			Villager carter = party.carter();
			helper.assertTrue(party.phase() == CaravanSights.Phase.ARRIVING, "the party: " + party.phase());
			helper.assertTrue(carter.getCustomName() != null && carter.getCustomName().getString().equals("Thornholm's caravan"),
				"it is Thornholm's caravan: " + (carter.getCustomName() == null ? null : carter.getCustomName().getString()));
			helper.assertTrue(flat(carter.position(), party.edge()) <= 1 && party.edge().distSqr(two.a()) < party.yard().distSqr(two.a()),
				"it should come in at the edge toward Thornholm: carter " + carter.position() + ", edge " + party.edge() + ", yard " + party.yard());
			for (Llama llama : party.llamas()) {
				helper.assertTrue(llama.hasChest() && llama.getBodyArmorItem().is(Items.RED_CARPET), "Thornholm's colour is red: " + llama.getBodyArmorItem());
			}
			last[0] = carter.position();
		});
		helper.onEachTick(() -> {
			CaravanSights.Party party = seen[0];
			if (party == null || party.carter().isRemoved()) {
				return;
			}
			last[0] = party.carter().position();
			if (party.phase() == CaravanSights.Phase.UNLOADING) {
				unloading[0]++;
				if (unloading[0] == 1) {
					helper.assertTrue(flat(party.carter().position(), party.yard()) <= 2, "unloading " + flat(party.carter().position(), party.yard()) + " from the Storehouse's yard");
					helper.assertTrue(flat(party.carter().position(), helper.absolutePos(STORE_B)) <= 6.5, "the yard isn't by the Storehouse: " + party.yard());
				}
				// from the 45th tick on both llamas have come up and stand
				if (unloading[0] == 45) {
					party.llamas().forEach(l -> standing.add(l.position()));
				} else if (unloading[0] > 45) {
					for (int i = 0; i < 2; i++) {
						moved[0] = Math.max(moved[0], party.llamas().get(i).position().distanceTo(standing.get(i)));
					}
				}
			} else if (party.phase() == CaravanSights.Phase.RETURNING) {
				returned[0] = true;
			}
		});
		helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
			CaravanSights.Party party = seen[0];
			helper.assertTrue(party != null && CaravanSights.party(two.level(), two.b()) == null, "the party is still out");
			helper.assertTrue(unloading[0] >= CaravanSights.UNLOAD_TICKS, "it unloaded for " + unloading[0] + " ticks");
			helper.assertTrue(standing.size() == 2 && moved[0] < 0.05, "the llamas should stand while the goods are unloaded: moved " + moved[0]);
			helper.assertTrue(returned[0] && flat(last[0], party.edge()) <= 2.5, "it should walk back out: returning " + returned[0] + ", last seen " + flat(last[0], party.edge()) + " from the edge");
			helper.assertTrue(tagged(helper).isEmpty() && dropped(helper).isEmpty(), "left behind: " + tagged(helper) + " " + dropped(helper));
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16, "the bread: " + two.theirs().countItem(Items.BREAD));
		}));
	}

	/**
	 * Only where someone can see it: with the nearest player 100 blocks from the hall a caravan leaves and arrives the
	 * saved way and nobody walks; with the player 90 blocks away the next one is seen.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanSightNobody"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanSightNobody")
	public void nothingIsSeenWithNoPlayerNear(GameTestHelper helper) {
		Two two = build(helper, true);
		// earlier batches' players standing about are logged out (this test is alone in its batch)
		var list = two.level().getServer().getPlayerList();
		for (ServerPlayer p : List.copyOf(two.level().players())) {
			if (p.blockPosition().distSqr(two.a()) <= 200.0 * 200 || p.blockPosition().distSqr(two.b()) <= 200.0 * 200) {
				list.remove(p);
			}
		}
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		// west of Thornholm, so further still from Ashford
		player.moveTo(two.a().getX() - 100 + 0.5, two.a().getY(), two.a().getZ() + 0.5, 0f, 0f);
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 24 && two.data().onTheRoad().stream().anyMatch(s -> s.to().equals(two.b())),
				"the caravan should leave all the same: " + two.ours().countItem(Items.BREAD) + " bread left");
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null && tagged(helper).isEmpty(), "seen leaving with nobody near: " + tagged(helper));
			arrive(two);
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16, "arrived: " + two.theirs().countItem(Items.BREAD));
			helper.assertTrue(CaravanSights.party(two.level(), two.b()) == null && tagged(helper).isEmpty(), "seen arriving with nobody near: " + tagged(helper));

			// the next day's caravan, with the player just inside the 96 blocks
			player.moveTo(two.a().getX() - 90 + 0.5, two.a().getY(), two.a().getZ() + 0.5, 0f, 0f);
			two.data().sent(two.a(), -1);
			two.data().setWants(two.b(), Component.literal("Ashford"), List.of(new Caravans.Want(Items.BREAD, 8)));
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 16, "the second caravan: " + two.ours().countItem(Items.BREAD));
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) != null && tagged(helper).size() == 3, "not seen with a player 90 blocks away: " + tagged(helper));
			helper.succeed();
		});
	}

	/**
	 * Left by a restart: a carter and a llama written to the save and read back after the server forgot its parties are
	 * taken away as they load (they carry the party's tag), as is a tagged one from any older save.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanSightRestart"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanSightRestart")
	public void aSavedAndReloadedCarterIsTakenAway(GameTestHelper helper) {
		Two two = build(helper, true);
		playerAt(helper, new BlockPos(10, 2, 3));
		pen(helper);
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			Caravans.round(two.level(), two.a(), null);
			CaravanSights.Party party = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(party != null, "no party");
			CompoundTag carter = new CompoundTag();
			CompoundTag llama = new CompoundTag();
			helper.assertTrue(party.carter().save(carter) && party.llamas().get(0).save(llama), "the party wasn't saved");
			// the server stops: the world keeps what was saved, and nothing is out walking when it starts again
			CaravanSights.clear(two.level(), two.a());
			helper.assertTrue(tagged(helper).isEmpty(), "still there: " + tagged(helper));
			for (CompoundTag saved : List.of(carter, llama)) {
				Entity again = EntityType.loadEntityRecursive(saved, two.level(), e -> e);
				helper.assertTrue(again != null && CaravanSights.isParty(again), "the saved one lost its tag: " + saved);
				two.level().addFreshEntity(again);
				helper.assertTrue(again.isRemoved(), "a reloaded " + again.getType().toShortString() + " stayed");
			}
		});
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(tagged(helper).isEmpty(), "left by the restart: " + tagged(helper));
			helper.assertTrue(dropped(helper).isEmpty(), "left on the ground: " + dropped(helper));
			// and the next caravan is seen as usual
			two.data().sent(two.a(), -1);
			two.data().setWants(two.b(), Component.literal("Ashford"), List.of(new Caravans.Want(Items.BREAD, 8)));
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) != null && tagged(helper).size() == 3, "no party after the restart: " + tagged(helper));
			helper.succeed();
		});
	}

	/** What a caravan run leaves behind: on the road after leaving, in both chests after arriving, and in both chronicles. */
	private static String run(GameTestHelper helper, Two two) {
		two.ours().clearContent();
		two.theirs().clearContent();
		two.data().clearRoad(two.a());
		two.data().sent(two.a(), -1);
		int linesA = two.hallA().chronicle().size();
		open(helper, two);
		Caravans.round(two.level(), two.a(), null);
		String road = two.data().onTheRoad().stream().map(s -> s.goods() + " " + s.sale() + " " + s.back()).toList().toString();
		String left = two.ours().countItem(Items.BREAD) + " bread at home";
		arrive(two);
		return road + "; " + left + "; " + two.theirs().countItem(Items.BREAD) + " bread arrived; " + two.data().onTheRoad().size() + " still on the road; "
			+ (two.hallA().chronicle().size() - linesA) + " lines";
	}

	/**
	 * The item's last test, and the switch. A caravan run with sights on (a party at each end) and the same run with
	 * them off (no party anywhere) leave exactly the same goods on the road, at home and in the other village's chest;
	 * switching them off also takes away a party that is out. {@code visibleCaravans} defaults on and reads from the
	 * config file.
	 */
	//$ gametest_ticks_batch AREA '200' '"caravanSightSwitch"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "caravanSightSwitch")
	public void theGoodsAreTheSameWithTheSwitchOnOrOff(GameTestHelper helper) {
		Two two = build(helper, true);
		playerAt(helper, new BlockPos(10, 2, 10));
		pen(helper);
		helper.assertTrue(new WorkplaceConfig().getBoolean("visibleCaravans") && !WorkplaceConfig.parse("{\"visibleCaravans\": false}").getBoolean("visibleCaravans"),
			"the switch should default on and read from the file");
		String[] on = new String[1];
		helper.runAfterDelay(2, () -> {
			on[0] = run(helper, two);
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) != null && CaravanSights.party(two.level(), two.b()) != null && tagged(helper).size() == 6,
				"with the switch on a party should be out in each village: " + tagged(helper));
			helper.assertTrue(on[0].contains("16 bread arrived") && on[0].contains("24 bread at home"), "the run with sights on: " + on[0]);
		});
		// The switch itself, not the config's apply(): that would put every other setting back to its default under the
		// tests that run after this one (and in GameTests it leaves the sights off whatever the file says).
		helper.runAfterDelay(20, () -> CaravanSights.ENABLED = false);
		helper.runAfterDelay(22, () -> {
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null && tagged(helper).isEmpty(), "switched off, the parties out should be gone: " + tagged(helper));
			String off = run(helper, two);
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null && CaravanSights.party(two.level(), two.b()) == null && tagged(helper).isEmpty(),
				"a party with the switch off: " + tagged(helper));
			helper.assertTrue(off.equals(on[0]), "the goods differ: on " + on[0] + ", off " + off);
			helper.assertTrue(dropped(helper).isEmpty(), "left on the ground: " + dropped(helper));
			helper.succeed();
		});
	}

	/**
	 * One party per village at a time, and two minutes at most. Penned in by a fence the party can't reach the edge: a
	 * second caravan leaving meanwhile carries its goods but adds nobody; just before the 2400th tick the same three are
	 * still there, and just after they are gone, wherever they were, with nothing left behind.
	 */
	//$ gametest_ticks_batch AREA '2700' '"caravanSightLimit"'
	@GameTest(template = AREA, timeoutTicks = 2700, batch = "caravanSightLimit")
	public void onePartyPerVillageAndGoneAfterTwoMinutes(GameTestHelper helper) {
		Two two = build(helper, true);
		playerAt(helper, new BlockPos(10, 2, 3));
		pen(helper);
		CaravanSights.Party[] seen = new CaravanSights.Party[1];
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			Caravans.round(two.level(), two.a(), null);
			seen[0] = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(seen[0] != null && tagged(helper).size() == 3, "no party: " + tagged(helper));
		});
		helper.runAfterDelay(100, () -> {
			two.data().sent(two.a(), -1);
			two.data().setWants(two.b(), Component.literal("Ashford"), List.of(new Caravans.Want(Items.BREAD, 8)));
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 16 && two.data().onTheRoad().stream().filter(s -> s.to().equals(two.b())).count() == 2,
				"the second caravan should carry its goods: " + two.ours().countItem(Items.BREAD) + " bread left, " + two.data().onTheRoad());
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == seen[0] && tagged(helper).size() == 3, "a second party in the same village: " + tagged(helper));
			helper.assertTrue(CaravanSights.parties(two.level()).stream().filter(p -> p.hall().equals(two.a())).count() == 1, "parties here: " + CaravanSights.parties(two.level()).size());
		});
		helper.runAfterDelay(2 + CaravanSights.LIFE_TICKS - 20, () -> {
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == seen[0] && !seen[0].carter().isRemoved() && tagged(helper).size() == 3,
				"penned in, the party should still be out just before two minutes: " + tagged(helper));
			helper.assertTrue(flat(seen[0].carter().position(), seen[0].edge()) > 2.5, "the pen didn't hold the carter: " + seen[0].carter().position());
		});
		helper.runAfterDelay(2 + CaravanSights.LIFE_TICKS + 5, () -> {
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null && seen[0].carter().isRemoved() && tagged(helper).isEmpty(),
				"still out after two minutes: " + tagged(helper));
			helper.assertTrue(dropped(helper).isEmpty(), "left on the ground: " + dropped(helper));
			helper.succeed();
		});
	}

	/**
	 * Only a sight. Penned in beside a free Storehouse for 200 ticks, the carter keeps his outfit, takes no job (the
	 * Storehouse's place stays free), isn't counted among the village's villagers, can't start a family and doesn't pick
	 * up bread at his feet; a player's right-click (an emerald in hand) opens no trade; a right-click on a llama neither
	 * mounts it, nor opens its chest, nor feeds it hay; and a player's sword hurts none of them.
	 */
	//$ gametest_ticks_batch AREA '400' '"caravanSightOnlyASight"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "caravanSightOnlyASight")
	public void thePartyNeverTradesTakesAJobOrBreeds(GameTestHelper helper) {
		Two two = build(helper, true);
		ServerPlayer player = playerAt(helper, new BlockPos(10, 2, 3));
		pen(helper);
		CaravanSights.Party[] seen = new CaravanSights.Party[1];
		ItemEntity[] bread = new ItemEntity[1];
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			Caravans.round(two.level(), two.a(), null);
			seen[0] = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(seen[0] != null, "no party");
			Villager carter = seen[0].carter();
			bread[0] = new ItemEntity(two.level(), carter.getX(), carter.getY() + 0.2, carter.getZ(), new ItemStack(Items.BREAD, 12));
			bread[0].setNoPickUpDelay();
			bread[0].setDeltaMovement(Vec3.ZERO);
			two.level().addFreshEntity(bread[0]);
		});
		helper.runAfterDelay(200, () -> {
			CaravanSights.Party party = seen[0];
			Villager carter = party.carter();
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == party && carter.isAlive(), "the party left the pen");
			// no job, no place in the village
			helper.assertTrue(carter.getVillagerData().getProfession() == ModVillagers.PORTER, "the carter lost his outfit: " + carter.getVillagerData().getProfession());
			helper.assertTrue(carter.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty() && carter.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty()
				&& carter.getBrain().getMemory(MemoryModuleType.HOME).isEmpty(), "the carter took a job or a bed");
			helper.assertTrue(two.level().getPoiManager().getCountInRange(h -> h.is(ModVillagers.STOREHOUSE_POI), helper.absolutePos(STORE_A), 1, PoiManager.Occupancy.HAS_SPACE) == 1,
				"the Storehouse's place was taken");
			VillageHalls.Census census = VillageHalls.census(two.level(), two.a());
			helper.assertTrue(census.villagers() == 0 && census.workers().isEmpty() && census.jobless().isEmpty(), "the carter was counted: " + census.villagers());
			// no family, nothing in his pockets
			helper.assertTrue(!carter.canBreed() && carter.getInventory().isEmpty(), "the carter could start a family, or carries something");
			helper.assertTrue(bread[0].isAlive() && bread[0].getItem().getCount() == 12, "the carter picked up the bread");
			// no trade
			helper.assertTrue(carter.getOffers().isEmpty(), "the carter has trades: " + carter.getOffers().size());
			rightClick(player, carter, new ItemStack(Items.EMERALD, 8));
			helper.assertTrue(!carter.isTrading() && player.containerMenu == player.inventoryMenu, "a click on the carter opened a trade");
			helper.assertTrue(carter.getCustomName().getString().equals("Thornholm's caravan"), "the carter's name changed");
			// the llamas: no ride, no look in the chest, no hay, no love
			Llama llama = party.llamas().get(0);
			rightClick(player, llama, ItemStack.EMPTY);
			helper.assertTrue(!player.isPassenger() && llama.getPassengers().isEmpty(), "the player mounted a llama");
			player.setShiftKeyDown(true);
			player.connection.handleInteract(ServerboundInteractPacket.createInteractionPacket(llama, true, InteractionHand.MAIN_HAND));
			player.setShiftKeyDown(false);
			helper.assertTrue(player.containerMenu == player.inventoryMenu, "a sneak-click opened the llama's chest");
			rightClick(player, llama, new ItemStack(Items.HAY_BLOCK, 4));
			helper.assertTrue(player.getMainHandItem().getCount() == 4 && !llama.isInLove(), "the llama ate the hay: " + player.getMainHandItem());
			helper.assertTrue(llama.getAge() > 0 && party.llamas().get(1).getAge() > 0 && carter.getAge() > 0, "someone is ready for a family");
			helper.assertTrue(llama.getLeashHolder() == carter && party.llamas().get(1).getLeashHolder() == carter, "a llama is off its lead");
			// no harm
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
			for (Mob mob : List.of(carter, party.llamas().get(0), party.llamas().get(1))) {
				float health = mob.getHealth();
				mob.hurt(two.level().damageSources().playerAttack(player), 10f);
				mob.hurt(two.level().damageSources().generic(), 10f);
				helper.assertTrue(mob.getHealth() == health && mob.isAlive(), "a " + mob.getType().toShortString() + " was hurt");
			}
			helper.assertTrue(tagged(helper).size() == 3 && dropped(helper).equals(List.of(bread[0].getItem().toString())), "the party or the ground changed: " + tagged(helper) + " " + dropped(helper));
			bread[0].discard();
			helper.succeed();
		});
	}
}

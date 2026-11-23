package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.hall.CaravanSights;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * QA (lane-c-1123-0953), ROADMAP 33.7, caravans you can see, written from docs/wiki/visible-caravans.md: what the
 * existing tests leave out. A spectator is no one to see it; breaking a hall takes away only its own village's party
 * (leaving or unloading), and the goods stay where they were; the banner's color on a leaving party and the
 * {@code villageBanners} switch; the saved {@code colour} (round trip, older saves, a far village); a Storehouse
 * indoors loads under the open sky; a caravan going the other way (north-west) leaves and arrives on that side, at the
 * village's rim; and the carter's name sentence.
 */
public class QaVisibleCaravans1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL_A = new BlockPos(3, 2, 3);
	private static final BlockPos STORE_A = new BlockPos(5, 2, 3);
	private static final BlockPos CHEST_A = new BlockPos(5, 2, 5);
	private static final BlockPos HALL_B = new BlockPos(18, 2, 18);
	private static final BlockPos STORE_B = new BlockPos(16, 2, 18);
	private static final BlockPos CHEST_B = new BlockPos(16, 2, 20);

	private record Two(ServerLevel level, BlockPos a, BlockPos b, Container ours, Container theirs, Caravans.Data data) {
		VillageHallBlockEntity hallA() {
			return (VillageHallBlockEntity) level.getBlockEntity(a);
		}
	}

	/** Thornholm (A) and Ashford (B), each with a hall, a Storehouse and a chest; radius 8, no rounds of their own; all put back after. */
	private static Two build(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		long travel = Caravans.MIN_TRAVEL;
		int every = VillageNeeds.CHECK_EVERY;
		boolean enabled = CaravanSights.ENABLED;
		boolean banners = VillageBanners.ENABLED;
		VillageHalls.RADIUS = 8;
		Caravans.MIN_TRAVEL = 0;
		VillageNeeds.CHECK_EVERY = Integer.MAX_VALUE;
		CaravanSights.ENABLED = true;
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
			VillageBanners.ENABLED = banners;
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

	/** Thornholm sends Ashford the 16 bread it waits for (40 in Thornholm's chest). */
	private static void open(GameTestHelper helper, Two two) {
		two.data().setWants(two.a(), Component.literal("Thornholm"), List.of());
		two.data().setWants(two.b(), Component.literal("Ashford"), List.of(new Caravans.Want(Items.BREAD, 16)));
		two.ours().setItem(0, new ItemStack(Items.BREAD, 40));
		helper.assertTrue(two.data().routesFrom(two.a()).contains(two.b()) || two.data().toggleRoute(two.a(), two.b()), "no route");
	}

	/** The next day's caravan from Thornholm (8 more bread for Ashford). */
	private static void again(Two two) {
		two.data().sent(two.a(), -1);
		two.data().setWants(two.b(), Component.literal("Ashford"), List.of(new Caravans.Want(Items.BREAD, 8)));
		Caravans.round(two.level(), two.a(), null);
	}

	private static ServerPlayer playerAt(GameTestHelper helper, BlockPos relative) {
		ServerPlayer player = ProtectionSpecGameTests.player(helper);
		BlockPos at = helper.absolutePos(relative);
		player.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
		return player;
	}

	/** Logs out players other tests left standing within 200 blocks of either hall. */
	private static void nobodyNear(Two two) {
		var list = two.level().getServer().getPlayerList();
		for (ServerPlayer p : List.copyOf(two.level().players())) {
			if (p.blockPosition().distSqr(two.a()) <= 200.0 * 200 || p.blockPosition().distSqr(two.b()) <= 200.0 * 200) {
				list.remove(p);
			}
		}
	}

	private static List<Entity> tagged(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(Entity.class, helper.getBounds().inflate(48), CaravanSights::isParty);
	}

	private static List<String> dropped(GameTestHelper helper) {
		return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(48), e -> true).stream()
			.map(e -> e.getItem().toString()).toList();
	}

	private static double flat(BlockPos from, BlockPos to) {
		double dx = from.getX() - to.getX();
		double dz = from.getZ() - to.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** A fence round Thornholm's hall, Storehouse and chest: its party can't walk off. */
	private static void pen(GameTestHelper helper) {
		for (int i = 1; i <= 8; i++) {
			helper.setBlock(new BlockPos(i, 2, 1), Blocks.OAK_FENCE);
			helper.setBlock(new BlockPos(i, 2, 8), Blocks.OAK_FENCE);
			helper.setBlock(new BlockPos(1, 2, i), Blocks.OAK_FENCE);
			helper.setBlock(new BlockPos(8, 2, i), Blocks.OAK_FENCE);
		}
	}

	private static String carpets(CaravanSights.Party party) {
		return party.llamas().stream().map(l -> l.getBodyArmorItem().toString()).toList().toString();
	}

	/**
	 * "A player (not a spectator) within 96 blocks": with only a spectator standing 7 blocks from Thornholm's hall a
	 * caravan leaves the saved way and nobody walks; the same player back in survival sees the next one.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVis1123cSpectator"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVis1123cSpectator")
	public void aSpectatorStandingByIsNoOneToSeeIt(GameTestHelper helper) {
		Two two = build(helper);
		nobodyNear(two);
		// a player on a real connection: the GameTest mock player always says it isn't a spectator
		ServerPlayer player = QaStageSixGameTests.client(helper, GameType.SPECTATOR, QaStageSixGameTests.name("qavis")).player();
		BlockPos stand = helper.absolutePos(new BlockPos(10, 2, 3));
		player.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5, 0f, 0f);
		helper.assertTrue(player.isSpectator(), "setup: the player isn't a spectator");
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 24, "the caravan should leave all the same: " + two.ours().countItem(Items.BREAD));
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null && tagged(helper).isEmpty(), "a spectator saw it: " + tagged(helper));
			player.setGameMode(GameType.SURVIVAL);
			again(two);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 16, "the second caravan: " + two.ours().countItem(Items.BREAD));
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) != null && tagged(helper).size() == 3, "not seen by a survival player: " + tagged(helper));
			helper.succeed();
		});
	}

	/**
	 * "The hall taken away": Thornholm's party is out leaving (penned) and Thornholm's carter is unloading in Ashford.
	 * Breaking Ashford's hall takes away only Ashford's party (Thornholm's stays), the bread already in Ashford's chest
	 * stays; breaking Thornholm's then takes away its party too, and nothing is left on the ground.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaVis1123cHallGone"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaVis1123cHallGone")
	public void breakingAHallTakesAwayOnlyItsOwnPartyAndKeepsTheGoods(GameTestHelper helper) {
		Two two = build(helper);
		playerAt(helper, new BlockPos(10, 2, 10));
		pen(helper);
		CaravanSights.Party[] seen = new CaravanSights.Party[2];
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			Caravans.round(two.level(), two.a(), null);
			two.data().hurry(two.b());
			Caravans.round(two.level(), two.b(), null);
			seen[0] = CaravanSights.party(two.level(), two.a());
			seen[1] = CaravanSights.party(two.level(), two.b());
			helper.assertTrue(seen[0] != null && seen[1] != null && tagged(helper).size() == 6, "a party in each village: " + tagged(helper));
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16, "arrived: " + two.theirs().countItem(Items.BREAD));
		});
		helper.runAfterDelay(30, () -> {
			helper.setBlock(HALL_B, Blocks.AIR);
			helper.assertTrue(CaravanSights.party(two.level(), two.b()) == null && seen[1].carter().isRemoved()
				&& seen[1].llamas().stream().allMatch(Entity::isRemoved), "Ashford's party outlived its hall");
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == seen[0] && !seen[0].carter().isRemoved() && tagged(helper).size() == 3,
				"Thornholm's party went with Ashford's hall: " + tagged(helper));
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16, "the bread left Ashford's chest: " + two.theirs().countItem(Items.BREAD));
		});
		helper.runAfterDelay(40, () -> {
			helper.setBlock(HALL_A, Blocks.AIR);
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null && seen[0].carter().isRemoved()
				&& seen[0].llamas().stream().allMatch(Entity::isRemoved), "Thornholm's party outlived its hall");
		});
		helper.runAfterDelay(50, () -> {
			helper.assertTrue(tagged(helper).isEmpty(), "left behind: " + tagged(helper));
			helper.assertTrue(dropped(helper).isEmpty(), "left on the ground: " + dropped(helper));
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 24, "Thornholm's chest: " + two.ours().countItem(Items.BREAD));
			helper.succeed();
		});
	}

	/**
	 * "The color": a party leaving a village with a Village Banner wears the banner's base color (blue); with
	 * {@code villageBanners} off the next one wears the color picked from the hall's position.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVis1123cBanner"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVis1123cBanner")
	public void aLeavingPartyWearsTheBannerColorAndThePickedOneWithBannersOff(GameTestHelper helper) {
		Two two = build(helper);
		playerAt(helper, new BlockPos(10, 2, 3));
		pen(helper);
		VillageBanners.ENABLED = true;
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			DyeColor picked = CaravanSights.picked(two.a());
			DyeColor banner = picked == DyeColor.BLUE ? DyeColor.ORANGE : DyeColor.BLUE;
			two.hallA().setColours(new VillageBanners.Colours(banner, BannerPatternLayers.EMPTY));
			Caravans.round(two.level(), two.a(), null);
			CaravanSights.Party party = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(party != null, "no party");
			for (Llama llama : party.llamas()) {
				helper.assertTrue(llama.getBodyArmorItem().is(CaravanSights.carpet(banner)), "the banner is " + banner + ": " + carpets(party));
			}
			CaravanSights.clear(two.level(), two.a());

			VillageBanners.ENABLED = false;
			again(two);
			CaravanSights.Party next = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(next != null, "no second party");
			for (Llama llama : next.llamas()) {
				helper.assertTrue(llama.getBodyArmorItem().is(CaravanSights.carpet(picked)), "banners off: the picked color " + picked + ": " + carpets(next));
			}
			helper.succeed();
		});
	}

	/**
	 * Saved data, {@code colour}: the hall's round writes its banner's color to the caravans' file, which survives a save
	 * and load; a far village whose hall isn't loaded wears its saved color; a village saved without one (an older save)
	 * wears the color picked from its position, and so does every village with {@code villageBanners} off.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaVis1123cSaved"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaVis1123cSaved")
	public void theBannerColorIsSavedForFarVillagesAndOlderSavesPickOne(GameTestHelper helper) {
		Two two = build(helper);
		VillageBanners.ENABLED = true;
		BlockPos far = two.a().offset(20000, 0, 20000);
		helper.assertTrue(!two.level().isLoaded(far), "the far village's hall is loaded");
		Leftovers.after(helper, () -> two.data().remove(far));
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			two.hallA().setColours(new VillageBanners.Colours(DyeColor.LIME, BannerPatternLayers.EMPTY));
			Caravans.round(two.level(), two.a(), VillageHalls.census(two.level(), two.a())); // the hall's own round, with its census
			helper.assertTrue(two.data().colour(two.a()) == DyeColor.LIME, "the round didn't save the banner's color: " + two.data().colour(two.a()));

			var registries = two.level().registryAccess();
			CompoundTag saved = two.data().save(new CompoundTag(), registries);
			Caravans.Data loaded = Caravans.Data.load(saved.copy(), registries);
			helper.assertTrue(loaded.colour(two.a()) == DyeColor.LIME, "lost in the save: " + loaded.colour(two.a()));

			// an older save: no colour on the village
			CompoundTag old = saved.copy();
			ListTag villages = old.getList("villages", Tag.TAG_COMPOUND);
			for (int i = 0; i < villages.size(); i++) {
				villages.getCompound(i).remove("colour");
			}
			Caravans.Data older = Caravans.Data.load(old, registries);
			helper.assertTrue(older.colour(two.a()) == null && older.village(two.a()) != null, "an older save: " + older.colour(two.a()));

			// a far village, its hall not loaded: its saved color, or the picked one without
			loaded.setWants(far, Component.literal("Farhold"), List.of());
			helper.assertTrue(CaravanSights.colour(two.level(), loaded, far) == CaravanSights.picked(far), "no banner known: " + CaravanSights.colour(two.level(), loaded, far));
			loaded.setColour(far, DyeColor.PURPLE);
			helper.assertTrue(CaravanSights.colour(two.level(), loaded, far) == DyeColor.PURPLE, "the far village's saved color: " + CaravanSights.colour(two.level(), loaded, far));
			VillageBanners.ENABLED = false;
			helper.assertTrue(CaravanSights.colour(two.level(), loaded, far) == CaravanSights.picked(far) && CaravanSights.colour(two.level(), two.data(), two.a()) == CaravanSights.picked(two.a()),
				"banners off should pick from the position: far " + CaravanSights.colour(two.level(), loaded, far) + ", Thornholm " + CaravanSights.colour(two.level(), two.data(), two.a()));
			// one of the sixteen, and always the same for the same spot
			helper.assertTrue(CaravanSights.picked(far) == CaravanSights.picked(far.immutable()) && CaravanSights.carpet(CaravanSights.picked(far)) != Items.AIR, "the picked color");
			helper.succeed();
		});
	}

	/**
	 * "The nearest one under open sky, when the Storehouse is indoors": Thornholm's Storehouse stands under a stone roof
	 * (8 by 8, open walls), so the party loads at a spot outside it under the sky, still near the Storehouse.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVis1123cIndoors"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVis1123cIndoors")
	public void anIndoorStorehouseLoadsUnderTheOpenSky(GameTestHelper helper) {
		Two two = build(helper);
		VillageHalls.RADIUS = 16; // room for a walk from a yard outside the roof to the village's rim (put back by build's cleanup)
		playerAt(helper, new BlockPos(14, 2, 3));
		for (int x = 1; x <= 8; x++) {
			for (int z = 0; z <= 7; z++) {
				helper.setBlock(new BlockPos(x, 5, z), Blocks.STONE);
			}
		}
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(!two.level().canSeeSky(helper.absolutePos(STORE_A).above()), "the roof doesn't cover the Storehouse");
			open(helper, two);
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 24, "the caravan should leave: " + two.ours().countItem(Items.BREAD));
			CaravanSights.Party party = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(party != null, "no party with the Storehouse indoors");
			helper.assertTrue(two.level().canSeeSky(party.yard()), "the party loads indoors at " + party.yard() + " with open sky a few blocks away");
			helper.assertTrue(flat(party.yard(), helper.absolutePos(STORE_A)) <= 6.5, "the yard is far from the Storehouse: " + party.yard());
			helper.succeed();
		});
	}

	/**
	 * The other way round: Ashford sends Thornholm (to its north-west) bread. Ashford's party leaves toward Thornholm to
	 * an edge at the village's radius from its hall, and in Thornholm Ashford's carter ("Ashford's caravan") comes in at
	 * the edge on Ashford's side.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVis1123cNorthWest"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVis1123cNorthWest")
	public void aCaravanGoingNorthWestLeavesAndArrivesOnThatSide(GameTestHelper helper) {
		Two two = build(helper);
		playerAt(helper, new BlockPos(10, 2, 10));
		helper.runAfterDelay(2, () -> {
			two.data().setWants(two.a(), Component.literal("Thornholm"), List.of(new Caravans.Want(Items.BREAD, 16)));
			two.data().setWants(two.b(), Component.literal("Ashford"), List.of());
			two.theirs().setItem(0, new ItemStack(Items.BREAD, 40));
			helper.assertTrue(two.data().routesFrom(two.b()).contains(two.a()) || two.data().toggleRoute(two.b(), two.a()), "no route");
			Caravans.round(two.level(), two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 24, "Ashford's caravan should leave: " + two.theirs().countItem(Items.BREAD));
			CaravanSights.Party out = CaravanSights.party(two.level(), two.b());
			helper.assertTrue(out != null && out.phase() == CaravanSights.Phase.LEAVING, "no party leaving Ashford");
			helper.assertTrue("Ashford's caravan".equals(out.carter().getCustomName().getString()), "the carter: " + out.carter().getCustomName().getString());
			helper.assertTrue(out.edge().distSqr(two.a()) < out.yard().distSqr(two.a()), "Ashford's edge isn't toward Thornholm: edge " + out.edge() + ", yard " + out.yard());
			double rim = flat(out.edge(), two.b());
			helper.assertTrue(rim >= VillageHalls.RADIUS - 2 && rim <= VillageHalls.RADIUS + 2, "the edge should be at the village's radius (8) from the hall: " + rim);
			helper.assertTrue(out.edge().getX() < two.b().getX() && out.edge().getZ() < two.b().getZ(), "the edge isn't north-west of Ashford's hall: " + out.edge());

			two.data().hurry(two.a());
			Caravans.round(two.level(), two.a(), null);
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 16, "arrived in Thornholm: " + two.ours().countItem(Items.BREAD));
			CaravanSights.Party in = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(in != null && in.phase() == CaravanSights.Phase.ARRIVING, "no party arriving in Thornholm");
			helper.assertTrue("Ashford's caravan".equals(in.carter().getCustomName().getString()), "the arriving carter: " + in.carter().getCustomName().getString());
			helper.assertTrue(in.edge().distSqr(two.b()) < in.yard().distSqr(two.b()) && in.edge().getX() > two.a().getX() && in.edge().getZ() > two.a().getZ(),
				"it should come in on Ashford's (south-east) side: edge " + in.edge() + ", yard " + in.yard());
			BlockPos carter = in.carter().blockPosition();
			helper.assertTrue(flat(carter, in.edge()) <= 1.5, "the carter starts at " + carter + ", not the edge " + in.edge());
			helper.succeed();
		});
	}

	/**
	 * "/kill ... the party is simply gone": an operator's {@code /kill} on the carter alone (the llamas' leads tied to
	 * him) leaves no lead, chest, carpet or leather on the ground, takes the llamas away with him within a few ticks, and
	 * the bread still arrives.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaVis1123cKillCarter"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaVis1123cKillCarter")
	public void killingOnlyTheCarterTakesTheWholePartyAndDropsNothing(GameTestHelper helper) {
		Two two = build(helper);
		playerAt(helper, new BlockPos(10, 2, 3));
		pen(helper);
		CaravanSights.Party[] seen = new CaravanSights.Party[1];
		helper.runAfterDelay(2, () -> {
			open(helper, two);
			Caravans.round(two.level(), two.a(), null);
			seen[0] = CaravanSights.party(two.level(), two.a());
			helper.assertTrue(seen[0] != null, "no party");
			var server = two.level().getServer();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "kill " + seen[0].carter().getStringUUID());
			helper.assertTrue(!seen[0].carter().isAlive(), "/kill didn't kill the carter");
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(CaravanSights.party(two.level(), two.a()) == null, "the party is still out without its carter");
			helper.assertTrue(seen[0].llamas().stream().allMatch(Entity::isRemoved) && tagged(helper).isEmpty(), "llamas left behind: " + tagged(helper));
			helper.assertTrue(dropped(helper).isEmpty(), "left on the ground: " + dropped(helper));
			helper.assertTrue(two.ours().countItem(Items.BREAD) == 24, "the goods should be on the road: " + two.ours().countItem(Items.BREAD));
			two.data().hurry(two.b());
			Caravans.round(two.level(), two.b(), null);
			helper.assertTrue(two.theirs().countItem(Items.BREAD) == 16, "the bread should arrive whole: " + two.theirs().countItem(Items.BREAD));
			helper.succeed();
		});
	}

	/**
	 * The carter's name, the one sentence a player reads: "%s's caravan" in en_us.json, with the village's name and
	 * nothing raw left over.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaVis1123cName"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaVis1123cName")
	public void theCartersNameReadsAsTheVillagesCaravan(GameTestHelper helper) {
		String key = "entity.aliveworkplace.caravan_carter";
		helper.assertTrue(Language.getInstance().has(key), "no " + key + " in en_us.json");
		for (String village : List.of("Thornholm", "Ashford", "Stone Ford")) {
			String text = Component.translatable(key, Component.literal(village)).getString();
			helper.assertTrue(text.equals(village + "'s caravan"), "the carter's name: " + text);
		}
		helper.succeed();
	}
}

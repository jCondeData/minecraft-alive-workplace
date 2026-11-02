package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.camp.SettlersWagonItem;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * QA (lane-c-1102-1853) of ROADMAP 33.10, "A colony is born", written from its spec and the sides the builder's tests
 * ({@link ColonyArrivalGameTests}) and the earlier QA ({@link QaColonyCharterGameTests}) leave out: a spot in water
 * camps on the nearest open ground; no room with a part load and a full Storehouse loses and duplicates nothing; a
 * married couple steps out married, with their trades and levels (and still so after a save and reload); the sister
 * route with the mother already at the route cap; no level ground beside the camp leaves both blueprints in the chest
 * (and the letter says so); the letter's direction words, north-east and due south.
 */
public class QaColonyArrival1102GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:colony_area";
	private static final String SMALL = "aliveworkplace_test:huge_area";
	private static final int COST = Colonies.COST * 100;

	private record Mother(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner) {
		GlobalPos at() {
			return GlobalPos.of(level.dimension(), hall);
		}

		RealmData realms() {
			return RealmData.get(level.getServer());
		}
	}

	/** Thornholm at {@code at}, owned by a survival player, village shrunk to 8 blocks, no hall rounds; cleaned up after. */
	private static Mother mother(GameTestHelper helper, BlockPos at) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean wasEnabled = Colonies.ENABLED;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		int room = ColonyArrival.ROOM;
		Colonies.ENABLED = true;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		RealmData.get(level.getServer()).clearColonies();
		openSky(helper);
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(at);
		ServerPlayer owner = ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(at);
		entity.setCustomName(Component.literal("Thornholm"));
		entity.setOwner(owner.getUUID(), "Owner");
		AABB area = helper.getBounds();
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = wasEnabled;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			ColonyArrival.ROOM = room;
			RealmData realms = RealmData.get(level.getServer());
			Caravans.Data caravans = Caravans.Data.get(level);
			for (RealmData.Founded f : realms.founded()) {
				caravans.remove(f.hall().pos());
				TravelNetwork network = TravelNetwork.get(level.getServer());
				for (BlockPos p : BlockPos.betweenClosed(f.hall().pos().offset(-8, -2, -8), f.hall().pos().offset(8, 2, 8))) {
					network.remove(GlobalPos.of(level.dimension(), p.immutable()));
				}
			}
			caravans.remove(hall);
			caravans.clearRoad(hall);
			realms.clearColonies();
			BuildSiteManager sites = BuildSiteManager.get(level);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (area.inflate(16).contains(site.placement().origin().getCenter())) {
					sites.remove(site.id());
				}
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, area.inflate(16))) {
				v.discard();
			}
		});
		return new Mother(level, hall, entity, owner);
	}

	private static void openSky(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	private static Villager settler(GameTestHelper helper, String name, VillagerProfession job, int jobLevel, int xp) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(jobLevel));
		v.setVillagerXp(xp);
		return v;
	}

	/** Saved the way the road keeps a settler, and taken out of the world. */
	private static CompoundTag saved(Villager v) {
		CompoundTag tag = new CompoundTag();
		v.saveWithoutId(tag);
		v.discard();
		return tag;
	}

	private static Map<Item, Integer> supplies() {
		Map<Item, Integer> out = new LinkedHashMap<>();
		out.put(Items.OAK_LOG, 64);
		out.put(Items.OAK_PLANKS, 64);
		out.put(Items.COBBLESTONE, 64);
		out.put(Items.BREAD, 32);
		out.put(Items.TORCH, 16);
		out.put(Items.GLASS_PANE, 12);
		out.put(Items.WHITE_BED, 3);
		return out;
	}

	private static RealmData.Order order(GameTestHelper helper, Mother m, BlockPos spot, Map<Item, Integer> supplies, CompoundTag tomas, CompoundTag dara) {
		long now = m.level().getGameTime();
		return new RealmData.Order(m.at(), helper.absolutePos(spot), Optional.of(Component.literal("Newbrook")), RealmData.ON_ROAD, now - 100, now, COST,
			now - 200, List.of(), Optional.of(tomas.getUUID("UUID")), supplies, List.of(tomas, dara));
	}

	/** The usual pair: Tomas (a Journeyman builder) and Dara (a Novice farmer). */
	private static RealmData.Order order(GameTestHelper helper, Mother m, BlockPos spot) {
		return order(helper, m, spot, supplies(), saved(settler(helper, "Tomas", ModVillagers.BUILDER, 2, 20)),
			saved(settler(helper, "Dara", VillagerProfession.FARMER, 1, 0)));
	}

	private static void sandstoneVillage(GameTestHelper helper, Mother m) {
		ResourceLocation sandstone = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "sandstone");
		BlueprintData.Placement at = new BlueprintData.Placement(m.level().dimension().location(), m.hall().offset(2, 0, 2), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(m.level()).recordFinished(sandstone, at, m.owner().getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(m.level()).forgetFinished(at));
	}

	private static List<Villager> named(GameTestHelper helper, String name) {
		List<Villager> out = new ArrayList<>();
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(48))) {
			if (v.isAlive() && v.getDisplayName().getString().equals(name)) {
				out.add(v);
			}
		}
		return out;
	}

	private static Villager one(GameTestHelper helper, String name) {
		List<Villager> all = named(helper, name);
		return all.size() == 1 ? all.get(0) : null;
	}

	private static String letter(ServerPlayer owner) {
		String letter = null;
		for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
			WrittenBookContent book = owner.getInventory().getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
			if (book != null) {
				letter = book.pages().get(0).raw().getString();
			}
		}
		return letter;
	}

	private static List<String> chronicle(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.chronicle().stream().map(x -> x.text().getString()).toList() : List.of();
	}

	private static BlockPos colony(Mother m) {
		return m.realms().foundedBy(m.at()).get(0).hall().pos();
	}

	private static void founded(GameTestHelper helper, Mother m) {
		helper.assertTrue(m.realms().orders().isEmpty() && m.realms().foundedBy(m.at()).size() == 1, "not founded yet: " + m.realms().orders()
			+ " founded " + m.realms().founded());
	}

	// ------------------------------------------------------------------------------------------ the camp's ground

	/**
	 * "The settlers make camp on the nearest open ground within 48 blocks": the spot itself is in the middle of an 11 by
	 * 11 pond. The colony is founded off the pond (no water under its hall, every pond block still water), close by.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaColArr1102Pond"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColArr1102Pond")
	public void qaASpotInAPondCampsOnTheNearestDryGround(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(3, 2, 22));
		List<BlockPos> pond = new ArrayList<>();
		for (int x = 18; x <= 28; x++) {
			for (int z = 17; z <= 27; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
				pond.add(new BlockPos(x, 2, z));
			}
		}
		helper.runAfterDelay(2, () -> m.realms().putOrder(order(helper, m, new BlockPos(23, 2, 22))));
		helper.succeedWhen(() -> {
			founded(helper, m);
			BlockPos hall = colony(m);
			BlockPos spot = helper.absolutePos(new BlockPos(23, 2, 22));
			ServerLevel level = m.level();
			for (BlockPos p : BlockPos.betweenClosed(hall.offset(-2, -2, -2), hall.offset(2, 0, 2))) {
				helper.assertTrue(level.getFluidState(p).isEmpty(), "water by the colony's hall at " + p.toShortString() + " (hall " + hall.toShortString() + ")");
			}
			for (BlockPos p : pond) {
				helper.assertTrue(level.getBlockState(helper.absolutePos(p)).is(Blocks.WATER), "the camp was put in the pond at " + p.toShortString());
			}
			double dx = hall.getX() - spot.getX();
			double dz = hall.getZ() - spot.getZ();
			helper.assertTrue(dx * dx + dz * dz <= 30 * 30, "the camp isn't on the nearest ground: hall " + hall.toShortString() + ", spot " + spot.toShortString());
			helper.assertTrue(one(helper, "Tomas") != null && one(helper, "Dara") != null, "the settlers at the camp: Tomas " + named(helper, "Tomas").size()
				+ ", Dara " + named(helper, "Dara").size());
		});
	}

	// ------------------------------------------------------------------------------------------ no room

	/**
	 * No room (a lake), with a part load (10 bread, 3 beds, 5 torches) and a Storehouse chest with room for only 4 more
	 * bread: everything comes home exactly once, what doesn't fit by the hall: 70 bread in all, 3 beds, 5 torches; one
	 * Tomas and one Dara; the cost back once, and nothing more 40 ticks later.
	 */
	//$ gametest_ticks_batch SMALL '300' '"qaColArr1102NoRoomPart"'
	@GameTest(template = SMALL, timeoutTicks = 300, batch = "qaColArr1102NoRoomPart")
	public void qaNoRoomWithAPartLoadAndAFullStorehouseLosesAndDuplicatesNothing(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(1, 2, 1));
		ColonyArrival.ROOM = 4;
		m.entity().setTreasury(500);
		helper.setBlock(new BlockPos(1, 2, 3), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(2, 2, 3), Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 2, 3));
		for (int i = 0; i < chest.getContainerSize() - 1; i++) {
			chest.setItem(i, new ItemStack(Items.STONE, 64));
		}
		chest.setItem(chest.getContainerSize() - 1, new ItemStack(Items.BREAD, 60));
		for (int x = 4; x <= 26; x++) {
			for (int z = 4; z <= 26; z++) {
				boolean rim = x == 4 || x == 26 || z == 4 || z == 26;
				helper.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE_BRICKS : Blocks.WATER);
			}
		}
		Map<Item, Integer> part = new LinkedHashMap<>();
		part.put(Items.BREAD, 10);
		part.put(Items.WHITE_BED, 3);
		part.put(Items.TORCH, 5);
		boolean[] given = new boolean[1];
		long[] home = {-1};
		helper.runAfterDelay(2, () -> {
			m.realms().putOrder(order(helper, m, new BlockPos(15, 2, 15), part, saved(settler(helper, "Tomas", ModVillagers.BUILDER, 2, 20)),
				saved(settler(helper, "Dara", VillagerProfession.FARMER, 1, 0))));
			given[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(given[0] && m.realms().orders().isEmpty(), "the order is still out: " + m.realms().orders());
			long now = m.level().getGameTime();
			if (home[0] < 0) {
				home[0] = now;
			}
			helper.assertTrue(now - home[0] >= 40, "waiting 40 ticks after they came home");
			helper.assertTrue(m.realms().founded().isEmpty(), "a colony was founded: " + m.realms().founded());
			Predicate<ItemStack> bread = s -> s.is(Items.BREAD);
			Predicate<ItemStack> beds = s -> s.is(ItemTags.BEDS);
			Predicate<ItemStack> torches = s -> s.is(Items.TORCH);
			helper.assertTrue(everywhere(helper, m, chest, bread) == 70 && everywhere(helper, m, chest, beds) == 3 && everywhere(helper, m, chest, torches) == 5,
				"what came home (chest, by the hall, in the settlers' pockets or the owner's hands): bread " + everywhere(helper, m, chest, bread) + " (expected 70), beds "
					+ everywhere(helper, m, chest, beds) + " (3), torches " + everywhere(helper, m, chest, torches) + " (5)");
			helper.assertTrue(named(helper, "Tomas").size() == 1 && named(helper, "Dara").size() == 1, "settlers home: Tomas " + named(helper, "Tomas").size()
				+ ", Dara " + named(helper, "Dara").size());
			helper.assertTrue(m.entity().treasury() == 500 + COST, "the treasury: " + m.entity().treasury() + " (expected " + (500 + COST) + ")");
			long lines = chronicle(m.level(), m.hall()).stream().filter(l -> l.equals("Tomas and Dara found no room for Newbrook and came home")).count();
			helper.assertTrue(lines == 1, "the chronicle's no-room line, " + lines + " times: " + chronicle(m.level(), m.hall()));
		});
	}

	private static String site(GameTestHelper helper, BuildSite site) {
		return site == null ? "none" : site.structure() + " at " + site.placement().origin().toShortString() + " " + site.placement().rotation();
	}

	private static int everywhere(GameTestHelper helper, Mother m, ChestBlockEntity chest, Predicate<ItemStack> test) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (test.test(chest.getItem(i))) {
				n += chest.getItem(i).getCount();
			}
		}
		for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(8))) {
			if (item.isAlive() && test.test(item.getItem())) {
				n += item.getItem().getCount();
			}
		}
		// (villagers pick up bread lying by the hall, as vanilla's do: in their pockets it's still home)
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds().inflate(8))) {
			for (int i = 0; i < v.getInventory().getContainerSize(); i++) {
				if (test.test(v.getInventory().getItem(i))) {
					n += v.getInventory().getItem(i).getCount();
				}
			}
		}
		for (int i = 0; i < m.owner().getInventory().getContainerSize(); i++) {
			if (test.test(m.owner().getInventory().getItem(i))) {
				n += m.owner().getInventory().getItem(i).getCount();
			}
		}
		return n;
	}

	// ------------------------------------------------------------------------------------------ the settlers as they were

	/**
	 * "The settlers step out as they were (names, jobs, levels, trades, partners)": Tomas and Dara, married, Dara an
	 * Apprentice farmer with 70 experience and a cake for 3 emeralds among her trades. At the colony both are still
	 * married to each other, Dara still an Apprentice with 70 experience and her cake trade, Tomas the builder employed at
	 * the camp's Blueprint Table. Saved and read back just after (the hall, and the two settlers), all of it holds.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaColArr1102Couple"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColArr1102Couple")
	public void qaAMarriedCoupleStepsOutMarriedWithTheirTradesAndLevels(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(3, 2, 22));
		helper.runAfterDelay(2, () -> {
			Villager tomas = settler(helper, "Tomas", ModVillagers.BUILDER, 2, 20);
			Villager dara = settler(helper, "Dara", VillagerProfession.FARMER, 2, 70);
			MerchantOffers offers = new MerchantOffers();
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 3), new ItemStack(Items.CAKE), 5, 10, 0.05f));
			dara.setOffers(offers);
			ModAttachments.PARTNER.set(tomas, new Couples.Partner(dara.getUUID(), dara.getDisplayName(), 3, true));
			ModAttachments.PARTNER.set(dara, new Couples.Partner(tomas.getUUID(), tomas.getDisplayName(), 3, true));
			m.realms().putOrder(order(helper, m, new BlockPos(22, 2, 22), supplies(), saved(tomas), saved(dara)));
		});
		helper.succeedWhen(() -> {
			founded(helper, m);
			ServerLevel level = m.level();
			Villager tomas = one(helper, "Tomas");
			Villager dara = one(helper, "Dara");
			helper.assertTrue(tomas != null && dara != null, "one Tomas and one Dara: " + named(helper, "Tomas").size() + " " + named(helper, "Dara").size());
			helper.assertTrue(Builders.activeSite(level, tomas) != null, "Tomas isn't at work yet");
			checkCouple(helper, tomas, dara, "at the colony");
			BlockPos table = tomas.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
			helper.assertTrue(table != null && level.getBlockState(table).is(ModBlocks.BLUEPRINT_TABLE), "Tomas's workstation: " + table);

			// saved and read back just after the arrival
			Villager tomas2 = reread(level, tomas);
			Villager dara2 = reread(level, dara);
			checkCouple(helper, tomas2, dara2, "after a save and reload");
			helper.assertTrue(tomas2.getVillagerData().getProfession() == ModVillagers.BUILDER && tomas2.getVillagerData().getLevel() == 2,
				"Tomas after a reload: " + tomas2.getVillagerData());
			BlockPos hall = colony(m);
			BlockEntity be = level.getBlockEntity(hall);
			CompoundTag tag = be.saveWithFullMetadata(level.registryAccess());
			BlockEntity back = BlockEntity.loadStatic(hall, level.getBlockState(hall), tag, level.registryAccess());
			helper.assertTrue(back instanceof VillageHallBlockEntity e && m.owner().getUUID().equals(e.owner())
				&& e.getCustomName() != null && e.getCustomName().getString().equals("Newbrook")
				&& e.chronicle().stream().anyMatch(x -> x.text().getString().startsWith("Founded on day ")),
				"the colony's hall after a save and reload: " + tag);
		});
	}

	private static Villager reread(ServerLevel level, Villager v) {
		CompoundTag tag = new CompoundTag();
		v.saveWithoutId(tag);
		Villager copy = EntityType.VILLAGER.create(level);
		copy.load(tag);
		return copy;
	}

	private static void checkCouple(GameTestHelper helper, Villager tomas, Villager dara, String when) {
		Couples.Partner his = Couples.partner(tomas);
		Couples.Partner hers = Couples.partner(dara);
		helper.assertTrue(his != null && his.married() && his.id().equals(dara.getUUID()) && hers != null && hers.married() && hers.id().equals(tomas.getUUID()),
			"Tomas and Dara aren't married to each other " + when + ": his " + his + " (Dara " + dara.getUUID() + "), hers " + hers + " (Tomas " + tomas.getUUID() + ")");
		helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.FARMER && dara.getVillagerData().getLevel() == 2 && dara.getVillagerXp() == 70,
			"Dara " + when + ": " + dara.getVillagerData() + " xp " + dara.getVillagerXp());
		helper.assertTrue(dara.getOffers().stream().anyMatch(o -> o.getResult().is(Items.CAKE) && o.getBaseCostA().is(Items.EMERALD) && o.getBaseCostA().getCount() == 3),
			"Dara's cake trade " + when + ": " + dara.getOffers().stream().map(o -> o.getBaseCostA() + " -> " + o.getResult()).toList());
	}

	// ------------------------------------------------------------------------------------------ the sister route

	/**
	 * "A sister route both ways that doesn't count against the route cap", with Thornholm already at its cap of 3 trade
	 * routes: the sister route still opens both ways, Thornholm keeps its 3 routes (still at the cap, a fourth refused),
	 * and the colony can open 3 trade routes of its own.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaColArr1102Cap"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColArr1102Cap")
	public void qaTheSisterRouteOpensWithTheMotherAtTheRouteCap(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(3, 2, 22));
		Caravans.Data caravans = Caravans.Data.get(m.level());
		List<BlockPos> others = new ArrayList<>();
		for (int i = 1; i <= Caravans.MAX_ROUTES + 4; i++) {
			others.add(m.hall().offset(200 + 50 * i, 0, 300));
		}
		for (int i = 0; i < Caravans.MAX_ROUTES; i++) {
			helper.assertTrue(caravans.toggleRoute(m.hall(), others.get(i)), "setup: route " + i);
		}
		helper.assertTrue(caravans.tradeRoutes(m.hall()) == Caravans.MAX_ROUTES, "setup: Thornholm at the cap");
		Leftovers.after(helper, () -> others.forEach(caravans::remove));
		helper.runAfterDelay(2, () -> m.realms().putOrder(order(helper, m, new BlockPos(22, 2, 22))));
		helper.succeedWhen(() -> {
			founded(helper, m);
			BlockPos hall = colony(m);
			helper.assertTrue(caravans.isSister(m.hall(), hall) && caravans.isSister(hall, m.hall()) && caravans.routesFrom(m.hall()).contains(hall)
				&& caravans.routesFrom(hall).contains(m.hall()), "the sister route both ways: from Thornholm " + caravans.routesFrom(m.hall()) + ", from the colony "
				+ caravans.routesFrom(hall));
			helper.assertTrue(caravans.tradeRoutes(m.hall()) == Caravans.MAX_ROUTES && caravans.routesFrom(m.hall()).containsAll(others.subList(0, Caravans.MAX_ROUTES)),
				"Thornholm's trade routes: " + caravans.tradeRoutes(m.hall()) + " " + caravans.routesFrom(m.hall()));
			helper.assertTrue(!caravans.toggleRoute(m.hall(), others.get(Caravans.MAX_ROUTES)), "Thornholm opened a fourth trade route past the cap");
			for (int i = 0; i < Caravans.MAX_ROUTES; i++) {
				helper.assertTrue(caravans.toggleRoute(hall, others.get(Caravans.MAX_ROUTES + 1 + i)),
					"the colony can't open trade route " + (i + 1) + " of " + Caravans.MAX_ROUTES + " (the sister route counted?): " + caravans.tradeRoutes(hall));
			}
			helper.assertTrue(caravans.tradeRoutes(hall) == Caravans.MAX_ROUTES && caravans.isSister(hall, m.hall()), "the colony's routes: " + caravans.routesFrom(hall));
		});
	}

	// ------------------------------------------------------------------------------------------ the chest and the builds

	/**
	 * "The builder starts at once on that Starter Cottage and Storehouse, placed on either side of the camp where the
	 * ground allows; otherwise the blueprints wait in the chest": the camp's spot is an island in a lake just big enough
	 * for the camp. Tomas is employed at the camp's table with no site, the chest holds the supplies and both Sandstone
	 * blueprints, and the letter says the blueprints wait in the chest.
	 */
	//$ gametest_ticks_batch AREA '300' '"qaColArr1102Island"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColArr1102Island")
	public void qaWithNoGroundBesideTheCampTheBlueprintsWaitInTheChest(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(1, 2, 1));
		sandstoneVillage(helper, m);
		ColonyArrival.ROOM = 3;
		BlockPos spot = new BlockPos(22, 2, 22);
		// the camp as it would stand at the spot, facing Thornholm (as the spec has it: the hall's front looks home)
		BlockPos at = helper.absolutePos(spot);
		int dx = m.hall().getX() - at.getX();
		int dz = m.hall().getZ() - at.getZ();
		Direction facing = Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? Direction.EAST : Direction.WEST) : (dz > 0 ? Direction.SOUTH : Direction.NORTH);
		BoundingBox camp = SettlersWagonItem.campBox(m.level(), ColonyArrival.COLONY_CAMP, helper.absolutePos(spot), BlueprintItem.rotationFacing(facing));
		helper.assertTrue(camp != null, "setup: no Colony Camp template");
		BoundingBox island = camp.inflatedBy(1);
		// a lake over the whole area (in the world's coordinates: the area may be turned), but for the island and Thornholm's corner
		AABB area = helper.getBounds();
		int y = helper.absolutePos(new BlockPos(0, 2, 0)).getY();
		for (int x = (int) area.minX + 1; x < (int) area.maxX - 1; x++) {
			for (int z = (int) area.minZ + 1; z < (int) area.maxZ - 1; z++) {
				boolean onIsland = x >= island.minX() && x <= island.maxX() && z >= island.minZ() && z <= island.maxZ();
				boolean home = Math.abs(x - m.hall().getX()) <= 3 && Math.abs(z - m.hall().getZ()) <= 3;
				if (!onIsland && !home) {
					m.level().setBlock(new BlockPos(x, y, z), Blocks.WATER.defaultBlockState(), 2);
				}
			}
		}
		ResourceLocation cottage = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "sandstone");
		ResourceLocation storehouse = BlueprintStyles.styled(StarterBlueprints.STOREHOUSE.id(), "sandstone");
		helper.runAfterDelay(2, () -> m.realms().putOrder(order(helper, m, spot)));
		helper.succeedWhen(() -> {
			founded(helper, m);
			ServerLevel level = m.level();
			Villager tomas = one(helper, "Tomas");
			helper.assertTrue(tomas != null, "Tomas at the colony: " + named(helper, "Tomas").size());
			BlockPos table = tomas.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null);
			helper.assertTrue(table != null && level.getBlockState(table).is(ModBlocks.BLUEPRINT_TABLE), "Tomas isn't employed at the camp's table: " + table);
			helper.assertTrue(Builders.activeSite(level, tomas) == null && Builders.queue(level, tomas).isEmpty(),
				"Tomas has a site with no ground beside the camp: " + site(helper, Builders.activeSite(level, tomas)) + " then "
					+ Builders.queue(level, tomas).stream().map(b -> site(helper, b)).toList() + "; the camp " + camp + ", the hall " + colony(m).toShortString());
			ChestBlockEntity chest = null;
			for (BlockPos p : BlockPos.betweenClosed(table.offset(-12, -3, -12), table.offset(12, 3, 12))) {
				if (level.getBlockEntity(p) instanceof ChestBlockEntity c) {
					chest = c;
				}
			}
			helper.assertTrue(chest != null, "no camp chest");
			List<ResourceLocation> blueprints = new ArrayList<>();
			int bread = 0;
			int logs = 0;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				ItemStack s = chest.getItem(i);
				BlueprintItem.data(s).ifPresent(d -> blueprints.add(d.structure()));
				bread += s.is(Items.BREAD) ? s.getCount() : 0;
				logs += s.is(ItemTags.LOGS) ? s.getCount() : 0;
			}
			helper.assertTrue(blueprints.contains(cottage) && blueprints.contains(storehouse) && blueprints.size() == 2,
				"the chest's blueprints: " + blueprints + " (expected " + cottage + " and " + storehouse + ")");
			helper.assertTrue(bread == 32 && logs == 64, "the chest's supplies: bread " + bread + ", logs " + logs);
			String letter = letter(m.owner());
			helper.assertTrue(letter != null && letter.contains("There's no level ground beside the camp, so the blueprints wait in the chest."), "the letter: " + letter);
		});
	}

	// ------------------------------------------------------------------------------------------ the letter's direction

	/** The owner's letter names where the colony is from the mother: a spot up and to the right on the map is "north-east". */
	//$ gametest_ticks_batch AREA '300' '"qaColArr1102NorthEast"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColArr1102NorthEast")
	public void qaTheLetterSaysNorthEastForASpotNorthEast(GameTestHelper helper) {
		letterSays(helper, new BlockPos(4, 2, 40), new BlockPos(26, 2, 18), "north-east");
	}

	/** A spot straight south of the mother is "south", in the letter, the chat line and the mother's chronicle. */
	//$ gametest_ticks_batch AREA '300' '"qaColArr1102South"'
	@GameTest(template = AREA, timeoutTicks = 300, batch = "qaColArr1102South")
	public void qaTheLetterSaysSouthForASpotDueSouth(GameTestHelper helper) {
		letterSays(helper, new BlockPos(22, 2, 1), new BlockPos(22, 2, 24), "south");
	}

	private static void letterSays(GameTestHelper helper, BlockPos motherAt, BlockPos spot, String direction) {
		Mother m = mother(helper, motherAt);
		helper.runAfterDelay(2, () -> m.realms().putOrder(order(helper, m, spot)));
		helper.succeedWhen(() -> {
			founded(helper, m);
			BlockPos hall = colony(m);
			String letter = letter(m.owner());
			helper.assertTrue(letter != null && letter.matches("(?s)We've arrived at Newbrook, \\d+ blocks " + direction + " of Thornholm, and made camp by the new hall and Travel Post\\. .*"),
				"the letter (colony hall " + hall.toShortString() + ", Thornholm " + m.hall().toShortString() + "): " + letter);
			List<String> ours = chronicle(m.level(), m.hall());
			helper.assertTrue(ours.stream().anyMatch(l -> l.matches("Our settlers Tomas and Dara founded Newbrook, \\d+ blocks " + direction)), "the mother's chronicle: " + ours);
		});
	}
}

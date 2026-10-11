package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintOutline;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintStyles;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.colony.ColonyArrival;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.travel.TravelNetwork;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * ROADMAP 33.10, a colony is born: when the settlers' journey is over (travel time 0 here) they make camp at the spot,
 * with a Village Hall named from the charter and owned by the mother's owner and a Travel Post on the network, step
 * out as they were (the builder at the camp's Blueprint Table, the farmer still a farmer), and the builder starts the
 * Starter Cottage and Storehouse in the mother's style (Sandstone) on either side of the camp. Mother and colony get a
 * sister route outside the cap, both chronicles their lines, the owner a letter. With no room (all water) everyone and
 * everything goes home and the cost back into the treasury. The order survives a reload mid-arrival, and arrives even
 * with colonies switched off.
 *
 * <p>The camp with a build on either side is 35 blocks across, too wide for {@code huge_area}'s 30: the arrival tests
 * use {@code colony_area} (44 by 44).
 */
public class ColonyArrivalGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:colony_area";
	private static final String SMALL = "aliveworkplace_test:huge_area";
	private static final int COST = Colonies.COST * 100;

	/** The mother village: its hall, owner and treasury. */
	private record Mother(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer owner) {
		GlobalPos at() {
			return GlobalPos.of(level.dimension(), hall);
		}

		RealmData realms() {
			return RealmData.get(level.getServer());
		}
	}

	/**
	 * Thornholm at {@code at}, owned by a survival player, its village shrunk to 8 blocks, no hall rounds, the colony
	 * records empty and colonies {@code enabled}; everything the arrival leaves behind (sites, posts, routes, records) is
	 * cleared when the test ends.
	 */
	private static Mother mother(GameTestHelper helper, BlockPos at, boolean enabled) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		boolean wasEnabled = Colonies.ENABLED;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		int room = ColonyArrival.ROOM;
		Colonies.ENABLED = enabled;
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

	/**
	 * Takes the barrier roof off the test's area: settlers find the ground as the world's height map has it, and under
	 * a roof that is the roof. (The walls stay; the area is put back with its roof for the next batch.)
	 */
	private static void openSky(GameTestHelper helper) {
		AABB box = helper.getBounds();
		ServerLevel level = helper.getLevel();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.maxY - 3, box.minZ), BlockPos.containing(box.maxX, box.maxY + 3, box.maxZ))) {
			if (level.getBlockState(p).is(Blocks.BARRIER)) {
				level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	/** A settler as the road keeps them: made, saved the way {@code Settlers.depart} saves them, and taken out of the world. */
	private static CompoundTag saved(GameTestHelper helper, String name, VillagerProfession job, int jobLevel, int xp) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(2, 2, 2));
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(jobLevel));
		v.setVillagerXp(xp);
		CompoundTag tag = new CompoundTag();
		v.saveWithoutId(tag);
		v.discard();
		return tag;
	}

	/** Everything the settlers took (64 logs, 64 planks, 64 cobblestone, 32 bread, 16 torches, 12 panes, 3 beds). */
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

	/** Thornholm's order for Newbrook at {@code spot}, Tomas (a Journeyman builder) and Dara (a Novice farmer) on the road, there now. */
	private static RealmData.Order order(GameTestHelper helper, Mother m, BlockPos spot, String state) {
		CompoundTag tomas = saved(helper, "Tomas", ModVillagers.BUILDER, 2, 20);
		CompoundTag dara = saved(helper, "Dara", VillagerProfession.FARMER, 1, 0);
		long now = m.level().getGameTime();
		return new RealmData.Order(m.at(), helper.absolutePos(spot), Optional.of(Component.literal("Newbrook")), state, now - 100, now, COST,
			now - 200, List.of(), Optional.of(tomas.getUUID("UUID")), supplies(), List.of(tomas, dara));
	}

	/** Thornholm builds in Sandstone: a finished Sandstone cottage on its record. */
	private static void sandstoneVillage(GameTestHelper helper, Mother m) {
		ResourceLocation sandstone = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "sandstone");
		BlueprintData.Placement at = new BlueprintData.Placement(m.level().dimension().location(), m.hall().offset(2, 0, 2), Rotation.NONE, Mirror.NONE);
		BuildSiteManager.get(m.level()).recordFinished(sandstone, at, m.owner().getUUID());
		Leftovers.after(helper, () -> BuildSiteManager.get(m.level()).forgetFinished(at));
	}

	private static int count(ChestBlockEntity chest, Predicate<ItemStack> test) {
		int n = 0;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (test.test(chest.getItem(i))) {
				n += chest.getItem(i).getCount();
			}
		}
		return n;
	}

	private static List<String> chronicle(ServerLevel level, BlockPos hall) {
		return level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e.chronicle().stream().map(x -> x.text().getString()).toList() : List.of();
	}

	/** The first block of {@code block} in the test's area, or null. */
	private static BlockPos find(GameTestHelper helper, net.minecraft.world.level.block.Block block) {
		AABB box = helper.getBounds();
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX - 1, box.maxY - 1, box.maxZ - 1))) {
			if (helper.getLevel().getBlockState(p).is(block)) {
				return p.immutable();
			}
		}
		return null;
	}

	private static Villager named(GameTestHelper helper, String name) {
		for (Villager v : helper.getLevel().getEntitiesOfClass(Villager.class, helper.getBounds())) {
			if (v.isAlive() && v.getDisplayName().getString().equals(name)) {
				return v;
			}
		}
		return null;
	}

	/**
	 * Settlers from a Sandstone-built village arrive at once: the camp, the hall (Newbrook, owned by Thornholm's owner)
	 * and the post (on the network, known to the owner and a friend) are there; Tomas is still a builder and Dara a
	 * farmer; Tomas works on a Sandstone cottage with a Sandstone storehouse queued, one either side of the camp; the
	 * sister route runs both ways outside the cap (shown so on the routes page, and Thornholm's caravan brings Newbrook
	 * its wants with nothing for sale); both chronicles have their lines and the owner has the letter.
	 */
	//$ gametest_ticks_batch AREA '200' '"colonyArrives"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "colonyArrives")
	public void settlersArriveAndFoundTheColony(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(3, 2, 22), true);
		sandstoneVillage(helper, m);
		UUID friend = UUID.randomUUID();
		Friends.get(m.level().getServer()).add(m.owner().getUUID(), friend, "Mira");
		Leftovers.after(helper, () -> Friends.get(m.level().getServer()).remove(m.owner().getUUID(), friend));
		// Thornholm's Storehouse, with sandstone to spare for Newbrook
		helper.setBlock(new BlockPos(3, 2, 25), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(4, 2, 25), Blocks.CHEST);
		ChestBlockEntity store = helper.getBlockEntity(new BlockPos(4, 2, 25));
		store.setItem(0, new ItemStack(Items.SANDSTONE, 64));
		helper.runAfterDelay(2, () -> m.realms().putOrder(order(helper, m, new BlockPos(22, 2, 22), RealmData.ON_ROAD)));
		ResourceLocation cottage = BlueprintStyles.styled(StarterBlueprints.STARTER_COTTAGE.id(), "sandstone");
		ResourceLocation storehouse = BlueprintStyles.styled(StarterBlueprints.STOREHOUSE.id(), "sandstone");
		helper.succeedWhen(() -> {
			ServerLevel level = m.level();
			helper.assertTrue(m.realms().orders().isEmpty() && m.realms().foundedBy(m.at()).size() == 1, "not founded yet: " + m.realms().orders());
			BlockPos hall = m.realms().foundedBy(m.at()).get(0).hall().pos();
			Villager tomas = named(helper, "Tomas");
			helper.assertTrue(tomas != null && Builders.activeSite(level, tomas) != null, "Tomas isn't at work yet: " + tomas + " " + level.getEntitiesOfClass(Villager.class, helper.getBounds().inflate(20)).stream().map(v -> v.getDisplayName().getString() + "@" + v.blockPosition()).toList() + " hall " + hall);

			// the camp, the hall, the post
			helper.assertTrue(level.getBlockState(hall).is(ModBlocks.VILLAGE_HALL) && hall.distManhattan(helper.absolutePos(new BlockPos(22, 2, 22))) <= 8,
				"the hall isn't by the spot: " + hall);
			VillageHallBlockEntity colony = (VillageHallBlockEntity) level.getBlockEntity(hall);
			helper.assertTrue(VillageHalls.name(level, hall).getString().equals("Newbrook") && m.owner().getUUID().equals(colony.owner()),
				"the hall's name and owner: " + VillageHalls.name(level, hall).getString() + " " + colony.owner());
			BlockPos table = find(helper, ModBlocks.BLUEPRINT_TABLE);
			BlockPos post = find(helper, ModBlocks.TRAVEL_POST);
			helper.assertTrue(table != null && post != null && post.distManhattan(hall) <= 8, "the camp's table or the post is missing: " + table + " " + post);
			TravelNetwork network = TravelNetwork.get(level.getServer());
			TravelNetwork.Post onNetwork = network.at(GlobalPos.of(level.dimension(), post));
			helper.assertTrue(onNetwork != null && onNetwork.name().equals("Newbrook"), "the post on the network: " + onNetwork);
			helper.assertTrue(network.known(m.owner().getUUID(), null).contains(onNetwork) && network.known(friend, null).contains(onNetwork),
				"the post should be known to the owner and their friend");
			BlockPos chestAt = find(helper, Blocks.CHEST);
			ChestBlockEntity chest = null;
			AABB camp = new AABB(table).inflate(10);
			for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(camp.minX, camp.minY, camp.minZ), BlockPos.containing(camp.maxX, camp.maxY, camp.maxZ))) {
				if (level.getBlockEntity(p) instanceof ChestBlockEntity c && !p.equals(helper.absolutePos(new BlockPos(4, 2, 25)))) {
					chest = c;
				}
			}
			helper.assertTrue(chest != null, "no camp chest (first chest " + chestAt + ")");
			helper.assertTrue(count(chest, s -> s.is(ItemTags.LOGS)) == 64 && count(chest, s -> s.is(Items.BREAD)) == 32 && count(chest, s -> s.is(ItemTags.BEDS)) == 3
				&& count(chest, s -> s.is(Items.COBBLESTONE)) == 64 && count(chest, s -> s.is(ModBlocks.VILLAGE_HALL.asItem())) == 0,
				"the supplies in the camp's chest");
			helper.assertTrue(count(chest, s -> BlueprintItem.data(s).isPresent()) == 0, "the two blueprints are in use, not left in the chest");

			// the settlers as they were
			Villager dara = named(helper, "Dara");
			helper.assertTrue(dara != null && dara.getVillagerData().getProfession() == VillagerProfession.FARMER, "Dara should still be a farmer: "
				+ (dara == null ? null : dara.getVillagerData()));
			helper.assertTrue(tomas.getVillagerData().getProfession() == ModVillagers.BUILDER && tomas.getVillagerData().getLevel() == 2,
				"Tomas should still be a Journeyman builder: " + tomas.getVillagerData());
			helper.assertTrue(tomas.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).map(GlobalPos::pos).orElse(null) != null
				&& tomas.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.JOB_SITE).get().pos().equals(table), "Tomas works at the camp's table");

			// two Sandstone sites, one either side of the camp
			BuildSite first = Builders.activeSite(level, tomas);
			List<BuildSite> queued = Builders.queue(level, tomas);
			helper.assertTrue(first.structure().equals(cottage) && queued.size() == 1 && queued.get(0).structure().equals(storehouse),
				"Tomas's sites: " + first.structure() + " then " + queued.stream().map(BuildSite::structure).toList());
			helper.assertTrue(m.owner().getUUID().equals(first.owner()), "the builds are the owner's");
			double middle = (hall.getZ() + post.getZ()) / 2.0; // (the camp faces west, toward Thornholm: its sides are north and south)
			double side1 = BlueprintOutline.bounds(first.placement(), StarterBlueprints.STARTER_COTTAGE.size()).getCenter().getZ() - middle;
			double side2 = BlueprintOutline.bounds(queued.get(0).placement(), StarterBlueprints.STOREHOUSE.size()).getCenter().getZ() - middle;
			helper.assertTrue(Math.abs(side1) >= 10 && Math.abs(side2) >= 9 && Math.signum(side1) != Math.signum(side2),
				"the builds should stand either side of the camp: " + side1 + " " + side2);

			// mother and colony: the sister route, outside the cap, free
			Caravans.Data caravans = Caravans.Data.get(level);
			helper.assertTrue(caravans.isSister(m.hall(), hall) && caravans.isSister(hall, m.hall()) && caravans.routesFrom(m.hall()).contains(hall)
				&& caravans.routesFrom(hall).contains(m.hall()) && caravans.tradeRoutes(m.hall()) == 0, "the sister route");
			helper.assertTrue(caravans.toggleRoute(m.hall(), hall, 0) && caravans.isSister(m.hall(), hall), "the sister route can't be turned off");
			caravans.setWants(m.hall(), Component.literal("Thornholm"), List.of());
			ChoiceMenu routes = VillageHallScreen.forTest(m.owner(), m.hall());
			routes.press(VillageHallScreen.ROUTES, m.owner());
			routes.press(TradePage.Tab.ROUTES.slot(), m.owner());
			List<String> lore = null;
			for (int slot = VillageHallScreen.FIRST_ROW; slot < ChoiceMenu.SIZE; slot++) {
				if (routes.icon(slot).getHoverName().getString().equals("Newbrook")) {
					lore = BoardTradeGameTests.lore(routes.icon(slot));
				}
			}
			helper.assertTrue(lore != null && lore.contains("Sister village: caravans both ways, free, outside the limit"), "the routes page: " + lore);
			// Newbrook waits for sandstone: Thornholm's caravan brings it, with nothing to sell
			caravans.setWants(hall, Component.literal("Newbrook"), List.of(new Caravans.Want(Items.SANDSTONE, 20)));
			caravans.clearRoad(m.hall());
			Caravans.round(level, m.hall(), null);
			List<Caravans.Shipment> sent = caravans.onTheRoad().stream().filter(s -> s.from().equals(m.hall()) && s.to().equals(hall)).toList();
			helper.assertTrue(sent.size() == 1 && sent.get(0).goods().size() == 1 && sent.get(0).goods().get(0).is(Items.SANDSTONE)
				&& sent.get(0).goods().get(0).getCount() == 20 && sent.get(0).sale().isEmpty(), "Thornholm's caravan to Newbrook: " + sent);

			// both chronicles
			long day = Chronicle.day(level);
			List<String> theirs = chronicle(level, hall);
			helper.assertTrue(theirs.contains("Founded on day " + day + " by settlers from Thornholm: Tomas and Dara"), "the colony's chronicle: " + theirs);
			List<String> ours = chronicle(level, m.hall());
			helper.assertTrue(ours.stream().anyMatch(l -> l.matches("Our settlers Tomas and Dara founded Newbrook, \\d+ blocks east")), "the mother's chronicle: " + ours);

			// the letter
			String letter = null;
			for (int i = 0; i < m.owner().getInventory().getContainerSize(); i++) {
				WrittenBookContent book = m.owner().getInventory().getItem(i).get(DataComponents.WRITTEN_BOOK_CONTENT);
				if (book != null) {
					letter = book.pages().get(0).raw().getString();
				}
			}
			helper.assertTrue(letter != null && letter.matches("We've arrived at Newbrook, \\d+ blocks east of Thornholm, and made camp by the new hall and Travel Post\\. "
				+ "Tomas is building the Starter Cottage \\(Sandstone\\) and Storehouse Shed \\(Sandstone\\)\\. Send what we're short of: the caravans know the way\\.\n\nTomas and Dara"),
				"the letter: " + letter);
		});
	}

	/**
	 * No room within reach of the spot (all water): nothing is built, and everyone and everything is back home: Tomas
	 * and Dara by Thornholm's hall as they were, the supplies in its Storehouse, the 32 emeralds in its treasury, a line
	 * in its chronicle, and no colony counted.
	 */
	//$ gametest_ticks_batch SMALL '200' '"colonyNoRoom"'
	@GameTest(template = SMALL, timeoutTicks = 200, batch = "colonyNoRoom")
	public void withNoRoomEveryoneAndEverythingComesHome(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(1, 2, 1), true);
		ColonyArrival.ROOM = 4;
		m.entity().setTreasury(500);
		helper.setBlock(new BlockPos(1, 2, 3), ModBlocks.STOREHOUSE);
		helper.setBlock(new BlockPos(2, 2, 3), Blocks.CHEST);
		// a lake round the spot, walled in
		for (int x = 4; x <= 26; x++) {
			for (int z = 4; z <= 26; z++) {
				boolean rim = x == 4 || x == 26 || z == 4 || z == 26;
				helper.setBlock(new BlockPos(x, 2, z), rim ? Blocks.STONE_BRICKS : Blocks.WATER);
			}
		}
		RealmData.Order[] given = new RealmData.Order[1];
		helper.runAfterDelay(2, () -> {
			given[0] = order(helper, m, new BlockPos(15, 2, 15), RealmData.ON_ROAD);
			String line = Settlers.status(m.level(), given[0].withState(RealmData.RETURNING, 0, 0)).getString();
			helper.assertTrue(line.equals("No room for Newbrook: the settlers are coming home"), "the tab's line: " + line);
			m.realms().putOrder(given[0]);
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(given[0] != null && m.realms().orders().isEmpty(), "the order is still out: " + m.realms().orders());
			helper.assertTrue(m.realms().founded().isEmpty() && m.realms().cooldown(m.at()).founded() == 0, "a colony was counted: " + m.realms().founded() + " spot " + given[0].spot() + " lake " + helper.getLevel().getBlockState(given[0].spot()));
			helper.assertTrue(find(helper, ModBlocks.BLUEPRINT_TABLE) == null && find(helper, ModBlocks.TRAVEL_POST) == null, "something was built on the lake");
			Villager tomas = named(helper, "Tomas");
			Villager dara = named(helper, "Dara");
			helper.assertTrue(tomas != null && dara != null && tomas.distanceToSqr(m.hall().getCenter()) <= 6 * 6 && dara.distanceToSqr(m.hall().getCenter()) <= 6 * 6,
				"Tomas and Dara should be back by the hall");
			helper.assertTrue(tomas.getVillagerData().getProfession() == ModVillagers.BUILDER && dara.getVillagerData().getProfession() == VillagerProfession.FARMER,
				"their trades: " + tomas.getVillagerData() + " " + dara.getVillagerData());
			ChestBlockEntity chest = helper.getBlockEntity(new BlockPos(2, 2, 3));
			helper.assertTrue(count(chest, s -> s.is(Items.OAK_LOG)) == 64 && count(chest, s -> s.is(Items.BREAD)) == 32 && count(chest, s -> s.is(ItemTags.BEDS)) == 3,
				"the supplies aren't back in the Storehouse");
			helper.assertTrue(m.entity().treasury() == 500 + COST, "the treasury: " + m.entity().treasury());
			helper.assertTrue(chronicle(m.level(), m.hall()).contains("Tomas and Dara found no room for Newbrook and came home"),
				"the chronicle: " + chronicle(m.level(), m.hall()));
		});
	}

	/**
	 * Saved and read back mid-arrival (the spot reached, the camp not yet made), the order is the same, and it arrives
	 * even with colonies switched off: nobody is left on the road. The Colonies tab's line says they're arriving.
	 */
	//$ gametest_ticks_batch AREA '200' '"colonyArrivalReload"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "colonyArrivalReload")
	public void anArrivalSurvivesAReloadAndTheSwitch(GameTestHelper helper) {
		Mother m = mother(helper, new BlockPos(3, 2, 22), false);
		RealmData.Order order = order(helper, m, new BlockPos(22, 2, 22), RealmData.ARRIVING);
		m.realms().putOrder(order);
		RealmData back = RealmData.load(m.realms().save(new CompoundTag(), m.level().registryAccess()), m.level().registryAccess());
		helper.assertTrue(order.equals(back.orderOf(m.at())), "the arriving order after a reload: " + back.orders());
		String line = Settlers.status(m.level(), order).getString();
		helper.assertTrue(line.equals("On the road to Newbrook, arriving"), "the tab: " + line);
		helper.succeedWhen(() -> {
			helper.assertTrue(m.realms().orders().isEmpty() && m.realms().foundedBy(m.at()).size() == 1, "not founded: " + m.realms().orders());
			BlockPos hall = m.realms().foundedBy(m.at()).get(0).hall().pos();
			helper.assertTrue(VillageHalls.name(m.level(), hall).getString().equals("Newbrook") && named(helper, "Tomas") != null && named(helper, "Dara") != null,
				"the colony and its settlers");
			RealmData after = RealmData.load(m.realms().save(new CompoundTag(), m.level().registryAccess()), m.level().registryAccess());
			helper.assertTrue(after.foundedBy(m.at()).equals(m.realms().foundedBy(m.at())), "the founded colony after a reload");
			Caravans.Data caravans = Caravans.Data.get(m.level());
			Caravans.Data reread = Caravans.Data.load(caravans.save(new CompoundTag(), m.level().registryAccess()), m.level().registryAccess());
			helper.assertTrue(reread.isSister(m.hall(), hall) && reread.isSister(hall, m.hall()), "the sister route after a reload");
		});
	}
}

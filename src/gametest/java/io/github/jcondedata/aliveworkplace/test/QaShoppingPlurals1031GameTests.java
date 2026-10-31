package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.PlayerBuilt;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.store.StorehouseBoard;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA (qa-lane-b-1031-1238) on B698, the Steward's shopping list in the plural, from its text: "Expected: '12 Oak Logs,
 * 10 Torches', '1 Glass'" in the owner's daily message, the Steward's desk and the Storehouse board. The existing tests
 * (QaStewardSafetyGameTests) name each item at one count only; these put the same item at 1 and at 2 (the boundary) on
 * all three, an item named once on its own, and a plural with an irregular name (Hay Bales, Bowls of Rabbit Stew). The
 * village is QaStewardSafetyGameTests' (hall, table and builder, a Steward, Homes east).
 */
public class QaShoppingPlurals1031GameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(2, 2, 14);
	private static final BlockPos TABLE = new BlockPos(2, 2, 10);
	private static final BlockPos STOREHOUSE = new BlockPos(4, 2, 18);

	record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	static Village village(GameTestHelper helper, ServerPlayer owner) {
		Leftovers.clear(helper);
		Leftovers.stewardSites(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		BlockPos a = helper.absolutePos(BlockPos.ZERO);
		BoundingBox near = new BoundingBox(a.getX() - 40, level.getMinBuildHeight(), a.getZ() - 40, a.getX() + 70, level.getMaxBuildHeight(), a.getZ() + 70);
		BuildSiteManager manager = BuildSiteManager.get(level);
		for (BuildSite site : new ArrayList<>(manager.all())) {
			if (near.isInside(site.placement().origin())) {
				manager.remove(site.id());
			}
		}
		for (BuildSiteManager.Finished f : manager.finishedIn(level)) {
			if (near.isInside(f.placement().origin())) {
				manager.forgetFinished(f.placement());
			}
		}
		PlayerBuilt.get(level).forget(near);
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		Villager builder = helper.spawn(EntityType.VILLAGER, TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(TABLE));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new Village(level, hall, owner, steward);
	}

	/** One waiting build missing {@code missing}, in that order where counts tie. */
	static BuildSite waitingSite(GameTestHelper helper, Village v, Map<Item, Integer> missing) {
		BlueprintData.Placement placement = new BlueprintData.Placement(v.level().dimension().location(),
			helper.absolutePos(new BlockPos(20, 2, 24)), Rotation.NONE, Mirror.NONE);
		BuildSite site = BuildSiteManager.get(v.level()).create(v.owner().getUUID(), "", AliveWorkplace.id("stone_house"), placement);
		site.setStewardHall(v.hall());
		site.setStatus(BuildSite.Status.WAITING_FOR_MATERIALS);
		site.setMissing(new LinkedHashMap<>(missing));
		return site;
	}

	static List<String> loreLines(ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (Component c : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
			out.add(c.getString());
		}
		return out;
	}

	static List<String> deskLines(Village v) {
		ChoiceMenu menu = VillageHallScreen.forTest(v.owner(), v.hall());
		menu.press(VillageHallScreen.ADVICE, v.owner());
		return loreLines(menu.icon(StewardDeskPage.STEWARD));
	}

	static List<String> boardLines(GameTestHelper helper, Village v) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		ChoiceMenu board = StorehouseBoard.boardForTest(v.owner(), helper.absolutePos(STOREHOUSE));
		return loreLines(board.icon(StorehouseBoard.SHOPPING_SLOT));
	}

	/** A real player on the server whose chat lines are kept, for the daily message. */
	static ServerPlayer listener(GameTestHelper helper, List<Component> seen, String name) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer owner = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void sendSystemMessage(Component message, boolean overlay) {
				seen.add(message);
				super.sendSystemMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, owner, cookie);
		long dayTime = level.getDayTime();
		Leftovers.after(helper, () -> {
			server.getPlayerList().remove(owner);
			level.setDayTime(dayTime);
		});
		return owner;
	}

	static List<String> shoppingLines(List<Component> seen) {
		return seen.stream().filter(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("message.aliveworkplace.steward.shopping"))
			.map(Component::getString).toList();
	}

	/**
	 * The boundary on one item, on all three places: 1 Torch reads in the singular, then the same build missing 2 reads
	 * "2 Torches" (the daily message the next day, the desk and the board each read again).
	 */
	//$ gametest_ticks_batch AREA '60' '"qaShopPluralOneTwo"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaShopPluralOneTwo")
	public void oneTorchThenTwoTorchesOnTheMessageDeskAndBoard(GameTestHelper helper) {
		List<Component> seen = new ArrayList<>();
		Village v = village(helper, listener(helper, seen, "qaPlurals1"));
		ServerLevel level = v.level();
		level.setDayTime(StewardWishes.day(level) * 24000L + 1000L);
		BuildSite site = waitingSite(helper, v, Map.of(Items.TORCH, 1));
		String who = v.steward().getDisplayName().getString() + " of " + VillageHalls.name(level, v.hall()).getString() + " needs these for his builds: ";
		StewardDesk.plan(level, v.steward(), v.hall());
		helper.assertTrue(shoppingLines(seen).equals(List.of(who + "1 Torch")), "the owner read " + shoppingLines(seen) + ", not \"" + who + "1 Torch\"");
		helper.assertTrue(deskLines(v).contains("Shopping list: 1 Torch"), "the desk doesn't read \"Shopping list: 1 Torch\": " + deskLines(v));
		helper.assertTrue(boardLines(helper, v).equals(List.of("1 Torch")), "the Storehouse's board lists " + boardLines(helper, v));
		site.setMissing(new LinkedHashMap<>(Map.of(Items.TORCH, 2)));
		level.setDayTime(level.getDayTime() + 24000L);
		StewardDesk.plan(level, v.steward(), v.hall());
		List<String> told = shoppingLines(seen);
		helper.assertTrue(told.size() == 2 && told.get(1).equals(who + "2 Torches"), "the next day the owner read " + told + ", not \"" + who + "2 Torches\"");
		helper.assertTrue(deskLines(v).contains("Shopping list: 2 Torches"), "the desk doesn't read \"Shopping list: 2 Torches\": " + deskLines(v));
		helper.assertTrue(boardLines(helper, v).equals(List.of("2 Torches")), "the Storehouse's board lists " + boardLines(helper, v));
		helper.succeed();
	}

	/**
	 * Plurals that aren't the name plus an s: 40 Hay Bales (not "Hay Bale"s), 3 Bowls of Rabbit Stew and 2 Spyglasses,
	 * each 1 of them in the singular on the other build; a large count (1000) still in the plural; the board one per line.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaShopPluralIrregular"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaShopPluralIrregular")
	public void irregularPluralsAndALargeCountOnTheDeskAndBoard(GameTestHelper helper) {
		Village v = village(helper, helper.makeMockServerPlayerInLevel());
		Map<Item, Integer> missing = new LinkedHashMap<>();
		missing.put(Items.LANTERN, 1000);
		missing.put(Items.HAY_BLOCK, 40);
		missing.put(Items.RABBIT_STEW, 3);
		missing.put(Items.SPYGLASS, 2);
		missing.put(Items.IRON_INGOT, 1);
		waitingSite(helper, v, missing);
		String line = "Shopping list: 1000 Lanterns, 40 Hay Bales, 3 Bowls of Rabbit Stew, 2 Spyglasses, 1 Iron Ingot";
		helper.assertTrue(deskLines(v).contains(line), "the desk doesn't read \"" + line + "\": " + deskLines(v));
		List<String> expected = List.of("1000 Lanterns", "40 Hay Bales", "3 Bowls of Rabbit Stew", "2 Spyglasses", "1 Iron Ingot");
		helper.assertTrue(boardLines(helper, v).equals(expected), "the Storehouse's board lists " + boardLines(helper, v) + ", not " + expected);
		helper.succeed();
	}
}

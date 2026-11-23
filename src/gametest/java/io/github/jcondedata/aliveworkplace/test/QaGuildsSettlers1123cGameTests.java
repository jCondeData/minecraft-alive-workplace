package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.colony.Colonies;
import io.github.jcondedata.aliveworkplace.colony.ColonyCharterItem;
import io.github.jcondedata.aliveworkplace.colony.Settlers;
import io.github.jcondedata.aliveworkplace.hall.Caravans;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Couples;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.trade.TradePage;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * QA (lane-c-1123-0653), written from docs/wiki/guilds.md and docs/wiki/colony-settlers.md for what no other test
 * checks. Guilds: the charter's recipe and stack size; the oldest chartered guild claims the first Guildhall and the next
 * waits for its own; the village hears the chartering, the founding and the succession in chat; with the switch off the
 * charter is refused and kept and the perks stop, and on again everything comes back. Settlers: a renamed bed isn't
 * taken and the sentence counts the one missing; an order given before the switch goes off goes on; the hall broken
 * while they get ready calls it off and drops the charter; a volunteer who dies isn't replaced; a friend of the owner
 * may call the colony off.
 */
public class QaGuildsSettlers1123cGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos STOREHOUSE = new BlockPos(13, 2, 9);
	private static final BlockPos CHEST = new BlockPos(14, 2, 9);
	private static final float EPSILON = 1e-4f;

	// --- Guilds ------------------------------------------------------------------------------------------------------

	/** "3 paper, an emerald, a gold ingot and red dye", and "stacks to 16". */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void qaTheCharterIsCraftedAsTheSpecSaysAndStacksToSixteen(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<RecipeHolder<?>> found = new ArrayList<>();
		for (RecipeHolder<?> h : level.getRecipeManager().getRecipes()) {
			if (h.value().getResultItem(level.registryAccess()).is(ModItems.GUILD_CHARTER)) {
				found.add(h);
			}
		}
		helper.assertTrue(found.size() == 1, "recipes making a Guild Charter: " + found.size());
		RecipeHolder<?> recipe = found.get(0);
		helper.assertTrue(recipe.value().getResultItem(level.registryAccess()).getCount() == 1, "makes " + recipe.value().getResultItem(level.registryAccess()).getCount());
		int paper = 0, emerald = 0, gold = 0, dye = 0, other = 0;
		for (Ingredient i : recipe.value().getIngredients()) {
			if (i.isEmpty()) {
				continue;
			}
			if (i.test(new ItemStack(Items.PAPER))) paper++;
			else if (i.test(new ItemStack(Items.EMERALD))) emerald++;
			else if (i.test(new ItemStack(Items.GOLD_INGOT))) gold++;
			else if (i.test(new ItemStack(Items.RED_DYE))) dye++;
			else other++;
		}
		helper.assertTrue(paper == 3 && emerald == 1 && gold == 1 && dye == 1 && other == 0,
			"ingredients: " + paper + " paper, " + emerald + " emerald, " + gold + " gold, " + dye + " red dye, " + other + " other");
		ItemStack charter = new ItemStack(ModItems.GUILD_CHARTER);
		helper.assertTrue(charter.getMaxStackSize() == 16, "stacks to " + charter.getMaxStackSize());
		helper.assertTrue(charter.getHoverName().getString().equals("Guild Charter"), "its name: " + charter.getHoverName().getString());
		helper.succeed();
	}

	/**
	 * A City with the Builders' Guild chartered before the Miners' Guild: the first Guildhall finished is the Builders'
	 * (the oldest), the miners wait; the second founds the Miners' Guild; neither steals the other's.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGs1123cOldest"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGs1123cOldest")
	public void qaTheOldestGuildClaimsTheFirstGuildhallAndTheNextWaits(GameTestHelper helper) {
		Leftovers.clear(helper);
		guildVillage(helper);
		Villager dara = guildVillager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		Villager mo = guildVillager(helper, new BlockPos(7, 2, 6), "Mo", ModVillagers.MINER, 5);
		Villager bram = guildVillager(helper, new BlockPos(8, 2, 6), "Bram", ModVillagers.CARPENTER, 2);
		Villager sid = guildVillager(helper, new BlockPos(9, 2, 6), "Sid", ModVillagers.SIFTER, 2);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			hall.setRank(VillageRanks.Rank.CITY);
			helper.assertTrue(Guilds.grant(level, dara).outcome() == Guilds.Outcome.GRANTED, "the builders' charter");
			helper.assertTrue(Guilds.grant(level, mo).outcome() == Guilds.Outcome.GRANTED, "the miners' charter");
			helper.assertTrue(Guilds.pace(bram) == 1f && Guilds.pace(sid) == 1f, "no Guildhall yet: perks");

			BlueprintData.Placement first = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 14)), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), first, UUID.randomUUID());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(first));
			Guilds.round(level, hallPos, hall);
			Guilds.Charter builders = charter(hall, "builders");
			Guilds.Charter miners = charter(hall, "miners");
			helper.assertTrue(builders.guildhall().equals(Optional.of(first.origin())) && miners.guildhall().isEmpty(), "one Guildhall: " + hall.guilds());
			helper.assertTrue(Math.abs(Guilds.pace(bram) * 1.15f - 1f) < EPSILON, "the Builders' Guild founded: a carpenter " + Guilds.pace(bram));
			helper.assertTrue(Guilds.pace(sid) == 1f, "the miners have no Guildhall of their own yet: a sifter " + Guilds.pace(sid));
			Guilds.round(level, hallPos, hall);
			helper.assertTrue(charter(hall, "miners").guildhall().isEmpty(), "a second round gave the miners the builders' Guildhall");

			BlueprintData.Placement second = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(14, 2, 2)), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL_2.id(), second, UUID.randomUUID());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(second));
			Guilds.round(level, hallPos, hall);
			helper.assertTrue(charter(hall, "miners").guildhall().equals(Optional.of(second.origin()))
				&& charter(hall, "builders").guildhall().equals(Optional.of(first.origin())), "two Guildhalls: " + hall.guilds());
			helper.assertTrue(Math.abs(Guilds.pace(sid) * 1.15f - 1f) < EPSILON && Math.abs(Guilds.pace(bram) * 1.15f - 1f) < EPSILON,
				"both founded: sifter " + Guilds.pace(sid) + ", carpenter " + Guilds.pace(bram));
			helper.succeed();
		});
	}

	/**
	 * "Everyone is told": a player standing in the village hears the chartering, the founding and, when the Guild Master
	 * dies, the succession, word for word as the spec writes them.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGs1123cTold"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGs1123cTold")
	public void qaTheVillageHearsTheCharterTheFoundingAndTheSuccession(GameTestHelper helper) {
		Leftovers.clear(helper);
		guildVillage(helper);
		Villager ada = guildVillager(helper, new BlockPos(6, 2, 6), "Ada", ModVillagers.BUILDER, 5);
		Villager cole = guildVillager(helper, new BlockPos(7, 2, 6), "Cole", VillagerProfession.MASON, 4);
		List<Component> heard = new ArrayList<>();
		ServerPlayer listener = RealmGameTests.listener(helper, heard, "qa-guild-listener");
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			listener.moveTo(Vec3.atCenterOf(hallPos.offset(2, 0, 2)));
			VillageHallBlockEntity hall = ready(helper);
			hall.setRank(VillageRanks.Rank.VILLAGE);
			String village = VillageHalls.name(level, hallPos).getString();
			ServerPlayer giver = ProtectionSpecGameTests.player(helper);
			giver.setShiftKeyDown(true);
			ItemStack charters = new ItemStack(ModItems.GUILD_CHARTER, 2);
			helper.assertTrue(Guilds.offer(giver, ada, charters).outcome() == Guilds.Outcome.GRANTED && charters.getCount() == 1, "granted, one charter used");
			helper.assertTrue(lines(heard).contains("Ada is now the Guild Master of the Builders' Guild in " + village + "!"), "the chartering: " + lines(heard));

			BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 14)), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), at, UUID.randomUUID());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(at));
			Guilds.round(level, hallPos, hall);
			helper.assertTrue(lines(heard).stream().anyMatch(l -> l.startsWith("The Builders' Guild is founded in its Guildhall")), "the founding: " + lines(heard));
			for (String l : lines(heard)) {
				helper.assertTrue(!l.contains("%s") && !l.contains("%d") && !l.contains("aliveworkplace."), "a raw line: " + l);
			}

			ada.kill();
			helper.assertTrue(lines(heard).contains("Cole takes over as Guild Master of the Builders' Guild."), "the succession: " + lines(heard));
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(cole) != null, "Cole leads now");
			helper.succeed();
		});
	}

	/**
	 * `guilds` off: a sneak-click with a charter is refused and the charter kept, the perks stop, nothing is erased; on
	 * again, the master and the founded guild's perks are back without a new charter or Guildhall.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGs1123cSwitch"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGs1123cSwitch")
	public void qaGuildsOffRefusesAndPausesThenOnBringsEverythingBack(GameTestHelper helper) {
		Leftovers.clear(helper);
		guildVillage(helper);
		Villager dara = guildVillager(helper, new BlockPos(6, 2, 6), "Dara", ModVillagers.BUILDER, 5);
		Villager bram = guildVillager(helper, new BlockPos(7, 2, 6), "Bram", ModVillagers.CARPENTER, 2);
		Villager mo = guildVillager(helper, new BlockPos(8, 2, 6), "Mo", ModVillagers.MINER, 5);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			VillageHallBlockEntity hall = ready(helper);
			hall.setRank(VillageRanks.Rank.TOWN);
			helper.assertTrue(Guilds.grant(level, dara).outcome() == Guilds.Outcome.GRANTED, "granted");
			BlueprintData.Placement at = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(2, 2, 14)), Rotation.NONE, Mirror.NONE);
			BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), at, UUID.randomUUID());
			Leftovers.after(helper, () -> BuildSiteManager.get(level).forgetFinished(at));
			Guilds.round(level, hallPos, hall);
			helper.assertTrue(Math.abs(Guilds.pace(bram) * 1.15f - 1f) < EPSILON, "founded: " + Guilds.pace(bram));

			ServerPlayer player = ProtectionSpecGameTests.player(helper);
			player.setShiftKeyDown(true);
			ItemStack charters = new ItemStack(ModItems.GUILD_CHARTER, 2);
			Guilds.ENABLED = false;
			try {
				Guilds.Offer off = Guilds.offer(player, mo, charters);
				helper.assertTrue(off.outcome() == Guilds.Outcome.DISABLED && off.message().getString().equals("Guilds are switched off on this server."),
					"off: " + off.outcome() + " " + off.message().getString());
				helper.assertTrue(charters.getCount() == 2, "a refused charter was used up: " + charters.getCount());
				helper.assertTrue(Guilds.pace(bram) == 1f, "off: the perks still work");
				helper.assertTrue(hall.guilds().size() == 1 && ModAttachments.GUILD_MASTER.get(dara) != null, "off: the guild or its master was erased");
				helper.assertTrue(ModAttachments.GUILD_MASTER.get(mo) == null, "off: Mo was chartered");
			} finally {
				Guilds.ENABLED = true;
			}
			helper.assertTrue(Math.abs(Guilds.pace(bram) * 1.15f - 1f) < EPSILON, "on again: the perks didn't come back: " + Guilds.pace(bram));
			helper.assertTrue(ModAttachments.GUILD_MASTER.get(dara).guild().getPath().equals("builders"), "on again: Dara's guild");
			Guilds.Offer on = Guilds.offer(player, mo, charters);
			helper.assertTrue(on.outcome() == Guilds.Outcome.GRANTED && charters.getCount() == 1, "on again, a Town's second guild: " + on.message().getString());
			helper.succeed();
		});
	}

	// --- Settlers ----------------------------------------------------------------------------------------------------

	/**
	 * "Only plain items count": a renamed bed stays in the Storehouse's chest, the order holds the two plain beds and the
	 * owner is told, in the singular, that 1 bed is missing.
	 */
	//$ gametest_ticks_batch AREA '100' '"qaGs1123cRenamedBed"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGs1123cRenamedBed")
	public void qaARenamedBedIsNotTakenAndOneBedIsMissing(GameTestHelper helper) {
		List<Component> heard = new ArrayList<>();
		Village v = village(helper, -1, heard);
		plainVillage(helper);
		ItemStack named = new ItemStack(Items.BLUE_BED);
		named.set(DataComponents.CUSTOM_NAME, Component.literal("Grandma's Bed"));
		ChestBlockEntity chest = storehouse(helper, new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.SPRUCE_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12), new ItemStack(Items.RED_BED),
			new ItemStack(Items.WHITE_BED), named);
		ItemStack held = click(v, v.player(), charter(v));
		RealmData.Order order = v.order();
		helper.assertTrue(order != null && held.isEmpty(), "not sent: " + held);
		helper.assertTrue(Settlers.held(order, Settlers.Supply.BEDS) == 2 && Settlers.missing(order).equals(Map.of(Settlers.Supply.BEDS, 1)),
			"beds held " + Settlers.held(order, Settlers.Supply.BEDS) + ", missing " + Settlers.missing(order));
		helper.assertTrue(count(chest, s -> s.is(Items.BLUE_BED) && s.has(DataComponents.CUSTOM_NAME)) == 1, "the renamed bed was taken");
		String want = "Bram and Tomas will set out for Newbrook. The Storehouses still miss 1 bed: bring it, or they leave in 3 days with what there is.";
		helper.assertTrue(lines(heard).contains(want), "the owner heard: " + lines(heard));
		helper.succeed();
	}

	/** "An order already given goes on": colonies switched off after the click, the settlers still take to the road. */
	//$ gametest_ticks_batch AREA '400' '"qaGs1123cOffGoesOn"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaGs1123cOffGoesOn")
	public void qaAnOrderGivenBeforeTheSwitchGoesOffStillLeaves(GameTestHelper helper) {
		Village v = village(helper, 0, null);
		Settlers.MUSTER_TICKS = 20;
		Settlers.LEAVE_TICKS = 40;
		plainVillage(helper);
		storehouse(helper, allSupplies());
		click(v, v.player(), charter(v));
		helper.assertTrue(v.order() != null, "not sent");
		Colonies.ENABLED = false;
		helper.succeedWhen(() -> {
			RealmData.Order order = v.order();
			helper.assertTrue(order != null && order.state().equals(RealmData.ON_ROAD), "not on the road: " + (order == null ? null : order.state()));
			helper.assertTrue(order.settlers().size() == 2 && Settlers.missing(order).isEmpty(), "on the road with " + order.settlers().size() + " settlers");
		});
	}

	/**
	 * "If the hall is broken before the bell, the order is called off by itself and the charter drops there": broken
	 * while the settlers still get ready (before the muster), everyone stays and no supply is lost.
	 */
	//$ gametest_ticks_batch AREA '200' '"qaGs1123cHallGone"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "qaGs1123cHallGone")
	public void qaTheHallBrokenWhileTheyGetReadyCallsItOffAndDropsTheCharter(GameTestHelper helper) {
		Village v = village(helper, -1, null);
		List<Villager> all = plainVillage(helper);
		ChestBlockEntity chest = storehouse(helper, allSupplies());
		click(v, v.player(), charter(v));
		v.player().setPos(v.hall().getX() + 0.5, v.hall().getY(), v.hall().getZ() + 30.5); // out of reach of what drops
		helper.assertTrue(v.order() != null && v.order().state().equals(RealmData.GATHERING), "not getting ready: " + v.order());
		helper.runAfterDelay(5, () -> helper.setBlock(HALL, Blocks.AIR));
		helper.runAfterDelay(70, () -> {
			helper.assertTrue(v.order() == null, "the order outlived its hall: " + v.order());
			for (Villager stays : all) {
				helper.assertTrue(stays.isAlive() && v.level().getEntity(stays.getUUID()) == stays, stays.getDisplayName().getString() + " left");
			}
			int logs = count(chest, s -> s.is(ItemTags.LOGS)) + dropped(v, s -> s.is(ItemTags.LOGS));
			int beds = count(chest, s -> s.is(ItemTags.BEDS)) + dropped(v, s -> s.is(ItemTags.BEDS));
			int bread = count(chest, s -> s.is(Items.BREAD)) + dropped(v, s -> s.is(Items.BREAD));
			helper.assertTrue(logs == 64 && beds == 3 && bread == 32, "the supplies aren't all back: " + logs + " logs, " + beds + " beds, " + bread + " bread");
			List<ItemStack> charters = droppedStacks(v, s -> s.is(ModItems.COLONY_CHARTER));
			helper.assertTrue(charters.size() == 1, "charters lying at the hall: " + charters.size());
			ColonyCharterItem.Charter data = charters.get(0).get(ModComponents.COLONY_CHARTER);
			helper.assertTrue(data != null && data.spot().orElseThrow().equals(v.hall().offset(400, 0, 0)), "the dropped charter lost its spot: " + data);
			helper.succeed();
		});
	}

	/** "A volunteer who dies while the village gets ready isn't replaced: the others go without them." */
	//$ gametest_ticks_batch AREA '400' '"qaGs1123cOneDies"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "qaGs1123cOneDies")
	public void qaAVolunteerWhoDiesIsNotReplacedAndTheOthersGo(GameTestHelper helper) {
		Village v = village(helper, 0, null);
		Settlers.MUSTER_TICKS = 20;
		Settlers.LEAVE_TICKS = 40;
		List<Villager> folk = plainVillage(helper);
		Villager tomas = folk.get(2);
		Villager dara = villager(helper, new BlockPos(11, 2, 14), "Dara", VillagerProfession.NONE, 1);
		marry(tomas, dara);
		storehouse(helper, allSupplies());
		click(v, v.player(), charter(v));
		RealmData.Order order = v.order();
		helper.assertTrue(order != null && order.volunteers().size() == 3 && order.volunteers().contains(dara.getUUID()), "Bram, Tomas and his wife: " + order);
		dara.kill();
		helper.succeedWhen(() -> {
			RealmData.Order now = v.order();
			helper.assertTrue(now != null && now.state().equals(RealmData.ON_ROAD), "not on the road: " + (now == null ? null : now.state()));
			List<String> names = new ArrayList<>();
			for (var tag : now.settlers()) {
				names.add(tag.contains("CustomName") ? Component.Serializer.fromJson(tag.getString("CustomName"), v.level().registryAccess()).getString() : "?");
			}
			helper.assertTrue(names.size() == 2 && names.contains("Bram") && names.contains("Tomas"), "the settlers on the road: " + names);
			helper.assertTrue(folk.get(3).isAlive() && v.level().getEntity(folk.get(3).getUUID()) != null, "Finn went in Dara's place");
		});
	}

	/** "The owner and friends only": a friend of the owner calls the colony off; the supplies go back and nobody leaves. */
	//$ gametest_ticks_batch AREA '100' '"qaGs1123cFriendOff"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaGs1123cFriendOff")
	public void qaAFriendOfTheOwnerCallsTheColonyOff(GameTestHelper helper) {
		Village v = village(helper, -1, null);
		List<Villager> all = plainVillage(helper);
		ChestBlockEntity chest = storehouse(helper, allSupplies());
		click(v, v.player(), charter(v));
		helper.assertTrue(v.order() != null && count(chest, s -> !s.isEmpty()) == 0, "the order didn't take everything");
		ServerPlayer friend = ProtectionSpecGameTests.player(helper);
		Friends friends = Friends.get(v.level().getServer());
		friends.add(v.player().getUUID(), friend.getUUID(), "Mira");
		Leftovers.after(helper, () -> friends.remove(v.player().getUUID(), friend.getUUID()));
		ChoiceMenu tab = coloniesTab(v, friend);
		helper.assertTrue(tab.icon(Settlers.CALL_OFF).getHoverName().getString().equals("Call the colony off"), "the friend sees no call-off: " + tab.icon(Settlers.CALL_OFF));
		tab.press(Settlers.CALL_OFF, friend);
		helper.assertTrue(v.order() == null, "a friend couldn't call it off");
		helper.assertTrue(count(chest, s -> s.is(ItemTags.LOGS)) == 64 && count(chest, s -> s.is(ItemTags.BEDS)) == 3 && count(chest, s -> s.is(Items.GLASS_PANE)) == 12,
			"the supplies aren't all back");
		for (Villager stays : all) {
			helper.assertTrue(stays.isAlive() && v.level().getEntity(stays.getUUID()) == stays, stays.getDisplayName().getString() + " is gone");
		}
		int charters = 0;
		for (ServerPlayer p : List.of(v.player(), friend)) {
			for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
				if (p.getInventory().getItem(i).is(ModItems.COLONY_CHARTER)) {
					charters += p.getInventory().getItem(i).getCount();
				}
			}
		}
		charters += dropped(v, s -> s.is(ModItems.COLONY_CHARTER));
		helper.assertTrue(charters == 1, "charters back: " + charters);
		helper.succeed();
	}

	// --- Helpers (guilds) ----------------------------------------------------------------------------------------------

	private static void guildVillage(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Guilds.ENABLED = true;
			VillageNeeds.forget();
			CivicEffects.forget();
			Guilds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static Villager guildVillager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.HAMLET);
		hall.setGuilds(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Guilds.forget();
		Moods.forget();
		return hall;
	}

	private static Guilds.Charter charter(VillageHallBlockEntity hall, String path) {
		return hall.guilds().stream().filter(c -> c.id().getPath().equals(path)).findFirst().orElseThrow();
	}

	private static List<String> lines(List<Component> heard) {
		return heard.stream().map(Component::getString).toList();
	}

	// --- Helpers (settlers) --------------------------------------------------------------------------------------------

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, ServerPlayer player, String name) {
		GlobalPos mother() {
			return GlobalPos.of(level.dimension(), hall);
		}

		RealmData.Order order() {
			return RealmData.get(level.getServer()).orderOf(mother());
		}
	}

	/** As ColonySettlersGameTests' village; with {@code heard} the owner is a player whose chat is kept. */
	private static Village village(GameTestHelper helper, int musterDelay, List<Component> heard) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		ServerLevel level = helper.getLevel();
		boolean enabled = Colonies.ENABLED;
		VillageRanks.Rank needed = Colonies.RANK;
		int every = VillageNeeds.CHECK_EVERY;
		int radius = VillageHalls.RADIUS;
		int wait = Settlers.WAIT_TICKS;
		int delay = Settlers.MUSTER_DELAY;
		int muster = Settlers.MUSTER_TICKS;
		int leave = Settlers.LEAVE_TICKS;
		Colonies.ENABLED = true;
		Colonies.RANK = VillageRanks.Rank.CITY;
		VillageNeeds.CHECK_EVERY = 1_000_000;
		VillageHalls.RADIUS = 8;
		Settlers.MUSTER_DELAY = musterDelay;
		RealmData.get(level.getServer()).clearColonies();
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		BlockPos hall = helper.absolutePos(HALL);
		Leftovers.after(helper, () -> {
			Colonies.ENABLED = enabled;
			Colonies.RANK = needed;
			VillageNeeds.CHECK_EVERY = every;
			VillageHalls.RADIUS = radius;
			Settlers.WAIT_TICKS = wait;
			Settlers.MUSTER_DELAY = delay;
			Settlers.MUSTER_TICKS = muster;
			Settlers.LEAVE_TICKS = leave;
			RealmData.get(level.getServer()).clearColonies();
			Caravans.Data.get(level).remove(hall);
		});
		ServerPlayer player = heard != null ? RealmGameTests.listener(helper, heard, "qa-colony-owner") : ProtectionSpecGameTests.player(helper);
		VillageHallBlockEntity entity = helper.getBlockEntity(HALL);
		entity.setOwner(player.getUUID(), "Owner");
		entity.setRank(VillageRanks.Rank.CITY);
		return new Village(level, hall, entity, player, VillageHalls.name(level, hall).getString());
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int jobLevel) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		if (job != VillagerProfession.NONE) {
			v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(jobLevel));
			v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos)));
			v.refreshBrain(helper.getLevel());
		}
		return v;
	}

	private static void marry(Villager a, Villager b) {
		ModAttachments.PARTNER.set(a, new Couples.Partner(b.getUUID(), b.getDisplayName(), 1, true));
		ModAttachments.PARTNER.set(b, new Couples.Partner(a.getUUID(), a.getDisplayName(), 1, true));
	}

	/** Two builders (Bram the lower) and the jobless Tomas and Finn: Bram and Tomas go. */
	private static List<Villager> plainVillage(GameTestHelper helper) {
		return List.of(villager(helper, new BlockPos(9, 2, 9), "Bram", ModVillagers.BUILDER, 1),
			villager(helper, new BlockPos(9, 2, 13), "Hale", ModVillagers.BUILDER, 3),
			villager(helper, new BlockPos(10, 2, 11), "Tomas", VillagerProfession.NONE, 1),
			villager(helper, new BlockPos(13, 2, 13), "Finn", VillagerProfession.NONE, 1));
	}

	private static ChestBlockEntity storehouse(GameTestHelper helper, ItemStack... stacks) {
		helper.setBlock(STOREHOUSE, ModBlocks.STOREHOUSE);
		helper.setBlock(CHEST, Blocks.CHEST);
		ChestBlockEntity chest = helper.getBlockEntity(CHEST);
		for (int i = 0; i < stacks.length; i++) {
			chest.setItem(i, stacks[i]);
		}
		return chest;
	}

	private static ItemStack[] allSupplies() {
		return new ItemStack[] {new ItemStack(Items.OAK_LOG, 64), new ItemStack(Items.SPRUCE_PLANKS, 64), new ItemStack(Items.COBBLESTONE, 64),
			new ItemStack(Items.BREAD, 32), new ItemStack(Items.TORCH, 16), new ItemStack(Items.GLASS_PANE, 12), new ItemStack(Items.RED_BED),
			new ItemStack(Items.RED_BED), new ItemStack(Items.WHITE_BED)};
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

	private static List<ItemStack> droppedStacks(Village v, Predicate<ItemStack> test) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemEntity e : v.level().getEntitiesOfClass(ItemEntity.class, new AABB(v.hall()).inflate(4))) {
			if (e.isAlive() && test.test(e.getItem())) {
				out.add(e.getItem());
			}
		}
		return out;
	}

	private static int dropped(Village v, Predicate<ItemStack> test) {
		return droppedStacks(v, test).stream().mapToInt(ItemStack::getCount).sum();
	}

	/** The village's charter, the spot 400 blocks east, named Newbrook. */
	private static ItemStack charter(Village v) {
		ItemStack stack = ColonyCharterItem.of(v.level(), v.hall());
		stack.set(ModComponents.COLONY_CHARTER, stack.get(ModComponents.COLONY_CHARTER).withSpot(v.hall().offset(400, 0, 0)));
		stack.set(DataComponents.CUSTOM_NAME, Component.literal("Newbrook"));
		return stack;
	}

	/** {@code player} right-clicks the hall with {@code stack} in the main hand. */
	private static ItemStack click(Village v, ServerPlayer player, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
		player.gameMode.useItemOn(player, v.level(), held, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(v.hall()), Direction.UP, v.hall(), false));
		return player.getItemInHand(InteractionHand.MAIN_HAND);
	}

	private static ChoiceMenu coloniesTab(Village v, ServerPlayer player) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, v.hall());
		menu.press(VillageHallScreen.ROUTES, player);
		menu.press(TradePage.Tab.COLONIES.slot(), player);
		return menu;
	}
}

package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mend.MendingWork;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * The Miners', Smiths' and Woodsmen's Guilds (ROADMAP 30.18): their data, and each guild's numbers with the guild
 * founded and not: the durability a pickaxe loses over 20 blocks dug, an axe over 20 logs cut, a rod over 10 fish
 * caught (through {@link Guilds#hurt}, which the miner's, lumberjack's and fisherman's work wear their tools with), the
 * netherworker's gear after a trip, and what one ingot puts back when a weaponsmith mends.
 */
public class GuildPerkGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);
	private static final BlockPos GUILDHALL = new BlockPos(2, 2, 14);
	private static final BlockPos GRINDSTONE = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);

	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void threeGuildsLoadFromData(GameTestHelper helper) {
		check(helper, "miners", "Miners' Guild", List.of(AliveWorkplace.id("miner"), AliveWorkplace.id("sifter"), AliveWorkplace.id("netherworker")),
			"Miners, Sifters and Netherworkers work 15% faster; their pickaxes and nether gear wear half as fast.");
		check(helper, "smiths", "Smiths' Guild", List.of(mc("armorer"), mc("toolsmith"), mc("weaponsmith"), AliveWorkplace.id("tinkerer"), AliveWorkplace.id("ball_smith")),
			"Armorers, Toolsmiths, Weaponsmiths, Tinkerers and Ball Smiths work 15% faster; each ingot a Weaponsmith mends with puts back a third of the durability (not a quarter).");
		check(helper, "woodsmen", "Woodsmen's Guild", List.of(AliveWorkplace.id("lumberjack"), mc("fletcher"), mc("fisherman")),
			"Lumberjacks, Fletchers and Fishermen work 15% faster; their axes and fishing rods wear half as fast.");
		helper.assertTrue(Guilds.get(AliveWorkplace.id("miners")).perks().get(1) instanceof Guilds.ToolWear w && w.percent() == -50 && w.factor() == 0.5f, "miners' wear");
		helper.assertTrue(Guilds.get(AliveWorkplace.id("woodsmen")).perks().get(1) instanceof Guilds.ToolWear w && w.percent() == -50, "woodsmen's wear");
		helper.assertTrue(Guilds.get(AliveWorkplace.id("smiths")).perks().get(1) instanceof Guilds.MendPerUnit m && Math.abs(m.share() - 1 / 3f) < 0.001f, "smiths' share");
		String bad = "";
		try {
			Guilds.read(AliveWorkplace.id("bad"), com.google.gson.JsonParser.parseString(
				"{\"name\": \"X\", \"trades\": [], \"perks\": [{\"type\": \"aliveworkplace:mend_per_unit\", \"share\": 2}]}"));
		} catch (IllegalArgumentException e) {
			bad = e.getMessage();
		}
		helper.assertTrue(bad.contains("share") || bad.contains("2"), "a share over 1 is refused: " + bad);
		helper.succeed();
	}

	/** A miner's pickaxe over 20 blocks dug: 20 durability, 10 in a founded Miners' Guild; a netherworker's gear half as worn; 15% faster. */
	//$ gametest_ticks_batch AREA '100' '"guildMiners"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "guildMiners")
	public void minersGuildPickaxesWearHalfAsFast(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager master = villager(helper, new BlockPos(6, 2, 6), "Brokk", ModVillagers.MINER, 5);
		Villager ida = villager(helper, new BlockPos(7, 2, 6), "Ida", ModVillagers.MINER, 2);
		Villager nell = villager(helper, new BlockPos(8, 2, 6), "Nell", ModVillagers.NETHERWORKER, 2);
		Villager tam = villager(helper, new BlockPos(9, 2, 6), "Tam", ModVillagers.LUMBERJACK, 2);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = chartered(helper, master);
			helper.assertTrue(lost(ida, Items.IRON_PICKAXE, 20, 1) == 20, "not founded: 20 blocks, 20 durability");
			helper.assertTrue(Guilds.wear(nell, 24) == 24, "not founded: the nether gear's full wear");
			found(helper, hall);
			helper.assertTrue(Math.abs(Guilds.pace(ida) * 1.15f - 1f) < 1e-4f, "a member works 15% faster: " + Guilds.pace(ida));
			int dug = lost(ida, Items.IRON_PICKAXE, 20, 1);
			helper.assertTrue(dug == 10, "founded: 20 blocks, 10 durability, got " + dug);
			helper.assertTrue(lost(ida, Items.IRON_PICKAXE, 1, 1) + lost(ida, Items.IRON_PICKAXE, 1, 1) == 1, "every other block wears it");
			int trip = Guilds.wear(nell, 24) + Guilds.wear(nell, 13);
			helper.assertTrue(trip == 18, "founded: the netherworker's gear wears half (24 + 13 -> 18), got " + trip);
			helper.assertTrue(lost(tam, Items.IRON_AXE, 20, 1) == 20, "a lumberjack isn't a miner: full wear");
			try {
				Guilds.ENABLED = false;
				helper.assertTrue(lost(ida, Items.IRON_PICKAXE, 20, 1) == 20, "guilds off: full wear");
			} finally {
				Guilds.ENABLED = true;
			}
			helper.succeed();
		});
	}

	/** A lumberjack's axe over 20 logs, a fisherman's rod over 10 fish: half in a founded Woodsmen's Guild. */
	//$ gametest_ticks_batch AREA '100' '"guildWoodsmen"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "guildWoodsmen")
	public void woodsmensGuildAxesAndRodsWearHalfAsFast(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager master = villager(helper, new BlockPos(6, 2, 6), "Rowan", ModVillagers.LUMBERJACK, 5);
		Villager tam = villager(helper, new BlockPos(7, 2, 6), "Tam", ModVillagers.LUMBERJACK, 2);
		Villager finn = villager(helper, new BlockPos(8, 2, 6), "Finn", VillagerProfession.FISHERMAN, 2);
		Villager ida = villager(helper, new BlockPos(9, 2, 6), "Ida", ModVillagers.MINER, 2);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = chartered(helper, master);
			helper.assertTrue(lost(tam, Items.IRON_AXE, 20, 1) == 20, "not founded: 20 logs, 20 durability");
			helper.assertTrue(lost(finn, Items.FISHING_ROD, 10, 1) == 10, "not founded: 10 fish, 10 durability");
			helper.assertTrue(Guilds.pace(finn) == 1f, "not founded: the usual pace");
			found(helper, hall);
			int cut = lost(tam, Items.IRON_AXE, 20, 1);
			int caught = lost(finn, Items.FISHING_ROD, 10, 1);
			helper.assertTrue(cut == 10, "founded: 20 logs, 10 durability, got " + cut);
			helper.assertTrue(caught == 5, "founded: 10 fish, 5 durability, got " + caught);
			helper.assertTrue(lost(tam, Items.IRON_AXE, 2, 8) == 8, "stripping 32 logs (2 x 8): 8, not 16");
			helper.assertTrue(Math.abs(Guilds.pace(finn) * 1.15f - 1f) < 1e-4f, "a fisherman works 15% faster");
			helper.assertTrue(lost(ida, Items.IRON_PICKAXE, 20, 1) == 20, "a miner isn't a woodsman: full wear");
			helper.succeed();
		});
	}

	/** What one iron ingot puts back on an iron sword (250): a quarter (62), a third (83) in a founded Smiths' Guild. */
	//$ gametest_ticks_batch AREA '100' '"guildSmiths"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "guildSmiths")
	public void smithsGuildMendsAThirdPerIngot(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		Villager master = villager(helper, new BlockPos(6, 2, 6), "Hilde", VillagerProfession.WEAPONSMITH, 5);
		Villager toby = villager(helper, new BlockPos(7, 2, 6), "Toby", VillagerProfession.TOOLSMITH, 2);
		Villager tam = villager(helper, new BlockPos(8, 2, 6), "Tam", ModVillagers.LUMBERJACK, 2);
		helper.runAfterDelay(5, () -> {
			VillageHallBlockEntity hall = chartered(helper, master);
			helper.assertTrue(ingot(master) == 62, "not founded: one ingot puts back 62 of 250, got " + ingot(master));
			found(helper, hall);
			helper.assertTrue(ingot(master) == 83, "founded: one ingot puts back 83 of 250 (a third), got " + ingot(master));
			helper.assertTrue(Math.abs(Guilds.pace(toby) * 1.15f - 1f) < 1e-4f, "a toolsmith works 15% faster");
			helper.assertTrue(lost(tam, Items.IRON_AXE, 20, 1) == 20, "the Smiths' Guild doesn't spare a lumberjack's axe");
			try {
				Guilds.ENABLED = false;
				helper.assertTrue(ingot(master) == 62, "guilds off: a quarter");
			} finally {
				Guilds.ENABLED = true;
			}
			helper.succeed();
		});
	}

	/** A Guild Master weaponsmith in a founded Smiths' Guild mends a worn pickaxe at the grindstone: one ingot, a third back. */
	//$ gametest_ticks_batch AREA '1200' '"guildSmithsMend"'
	@GameTest(template = AREA, timeoutTicks = 1200, batch = "guildSmithsMend")
	public void smithsGuildWeaponsmithMendsAThird(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.setDayTime(2000);
		helper.setBlock(GRINDSTONE, Blocks.GRINDSTONE);
		helper.setBlock(CHEST, Blocks.CHEST);
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		Jobs.employ(helper.getLevel(), smith, helper.absolutePos(GRINDSTONE), PoiTypes.WEAPONSMITH, VillagerProfession.WEAPONSMITH);
		smith.setVillagerData(smith.getVillagerData().setLevel(5));
		smith.setCustomName(Component.literal("Hilde"));
		Container chest = helper.getBlockEntity(CHEST);
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		pickaxe.setDamageValue(200);
		chest.setItem(0, pickaxe);
		helper.runAfterDelay(5, () -> {
			found(helper, chartered(helper, smith));
			chest.setItem(1, new ItemStack(Items.IRON_INGOT, 1));
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(ModAttachments.ITEMS_MENDED.getOrElse(smith, 0) == 1, "mended " + ModAttachments.ITEMS_MENDED.getOrElse(smith, 0));
			int damage = -1;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).is(Items.IRON_PICKAXE)) {
					damage = chest.getItem(i).getDamageValue();
				}
			}
			helper.assertTrue(damage == 200 - 83, "one ingot, a third of 250 back: damage " + damage);
			helper.assertTrue(chest.countItem(Items.IRON_INGOT) == 0, "iron left: " + chest.countItem(Items.IRON_INGOT));
		});
	}

	// --- Helpers ---------------------------------------------------------------------------------------------------

	private static ResourceLocation mc(String path) {
		return ResourceLocation.withDefaultNamespace(path);
	}

	private static void check(GameTestHelper helper, String id, String name, List<ResourceLocation> trades, String perk) {
		Guilds.Guild g = Guilds.get(AliveWorkplace.id(id));
		helper.assertTrue(g != null, "the " + name + " didn't load: " + Guilds.all().keySet());
		helper.assertTrue(g.name().getString().equals(name), "name: " + g.name().getString());
		helper.assertTrue(g.trades().equals(trades), name + " trades: " + g.trades());
		helper.assertTrue(g.perk().getString().equals(perk), name + " perk: " + g.perk().getString());
		helper.assertTrue(g.perks().get(0) instanceof CivicEffects.WorkPace pace && pace.percent() == 15, name + " 15% faster: " + g.perks());
	}

	/** The durability a fresh {@code tool} in {@code who}'s hand loses over {@code uses} uses of {@code amount} wear each. */
	private static int lost(Villager who, Item tool, int uses, int amount) {
		ItemStack stack = new ItemStack(tool);
		who.setItemSlot(EquipmentSlot.MAINHAND, stack);
		for (int i = 0; i < uses; i++) {
			Guilds.hurt(who, stack, amount, EquipmentSlot.MAINHAND);
		}
		int lost = stack.getDamageValue();
		who.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		return lost;
	}

	/** What one unit of iron puts back on an iron sword when {@code smith} mends (as the grindstone work reckons it). */
	private static int ingot(Villager smith) {
		return Math.max(1, (int) (Items.IRON_SWORD.getDefaultInstance().getMaxDamage() * MendingWork.perUnit(smith)));
	}

	/** A Village Hall (the hall's radius 16 for the test); what the test changed is put back when it ends. */
	private static void village(GameTestHelper helper) {
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		BlueprintData.Placement at = placement(helper);
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Guilds.ENABLED = true;
			BuildSiteManager.get(helper.getLevel()).forgetFinished(at);
			VillageNeeds.forget();
			CivicEffects.forget();
			Guilds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	private static BlueprintData.Placement placement(GameTestHelper helper) {
		return new BlueprintData.Placement(helper.getLevel().dimension().location(), helper.absolutePos(GUILDHALL), Rotation.NONE, Mirror.NONE);
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		return v;
	}

	/** A City hall whose {@code master} was just chartered (no Guildhall yet: the guild waits). */
	private static VillageHallBlockEntity chartered(GameTestHelper helper, Villager master) {
		ServerLevel level = helper.getLevel();
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		hall.setRank(VillageRanks.Rank.CITY);
		hall.setGuilds(List.of());
		VillageNeeds.forget();
		CivicEffects.forget();
		Guilds.forget();
		Moods.forget();
		Guilds.Offer offer = Guilds.grant(level, master);
		helper.assertTrue(offer.outcome() == Guilds.Outcome.GRANTED, "chartered: " + offer.message().getString());
		helper.assertTrue(!Guilds.founded(level, hall, hall.guilds().get(0).id()), "not founded without a Guildhall");
		return hall;
	}

	/** A Guildhall is finished: the guild claims it and is founded. */
	private static void found(GameTestHelper helper, VillageHallBlockEntity hall) {
		ServerLevel level = helper.getLevel();
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL.id(), placement(helper), UUID.randomUUID());
		Guilds.round(level, helper.absolutePos(HALL), hall);
		helper.assertTrue(Guilds.founded(level, hall, hall.guilds().get(0).id()), "founded: " + hall.guilds());
	}
}

package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.craft.LuxuryRecipes;
import io.github.jcondedata.aliveworkplace.craft.LuxuryWork;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.jeweller.JewellerWork;
import io.github.jcondedata.aliveworkplace.jeweller.Jewellers;
import io.github.jcondedata.aliveworkplace.people.ClassJobs;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.Luxuries;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.store.StockOrders;
import io.github.jcondedata.aliveworkplace.work.Partners;
import io.github.jcondedata.aliveworkplace.work.Requests;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Village;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The Jeweller (ROADMAP 34.12): picked at a stonecutter with a gold nugget (the Mason back with a clay ball, the Gem
 * Grower with an amethyst shard: the roadmap's own shard already picks her, see the Notes); makes the Amethyst Ring
 * (Novice), the Emerald Brooch (Apprentice) and the Gold Circlet (Journeyman) through the luxury workshop engine, from
 * the chest by their stonecutter; a Burgher-only job in a village with a hall (34.8). The making tests drive
 * {@link JewellerWork} tick by tick on a NoAI Jeweller.
 */
public class JewellerGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos CUTTER = new BlockPos(2, 2, 2);
	private static final BlockPos CHEST = new BlockPos(2, 2, 4);
	private static final BlockPos STANDING = new BlockPos(3, 2, 3);
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos SECOND = new BlockPos(4, 2, 12);

	private static ServerPlayer player(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	/** A stonecutter with a chest beside it, and no store nearby but the chest. */
	private static void shop(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int range = StockOrders.RANGE;
		StockOrders.RANGE = 4;
		Leftovers.after(helper, () -> StockOrders.RANGE = range);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		helper.setBlock(CHEST, Blocks.CHEST);
	}

	/** A Jeweller of {@code level} at the stonecutter (NoAI), not yet working. */
	private static Villager jeweller(GameTestHelper helper, int level) {
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.JEWELLER).setLevel(level));
		v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(CUTTER)));
		return v;
	}

	/** Runs a Jeweller's shift on {@code v} every tick, as the WORK activity would. */
	private static JewellerWork work(GameTestHelper helper, Villager v) {
		ServerLevel world = helper.getLevel();
		JewellerWork work = new JewellerWork();
		helper.onEachTick(() -> {
			if (!v.isAlive() || v.isRemoved()) {
				return;
			}
			long now = world.getGameTime();
			if (work.getStatus() == Behavior.Status.STOPPED) {
				work.tryStart(world, v, now);
			} else {
				work.tickOrStop(world, v, now);
			}
		});
		return work;
	}

	private static Container chest(GameTestHelper helper) {
		return helper.getBlockEntity(CHEST);
	}

	private static int count(Container c, Item item) {
		return c.countItem(item);
	}

	private static String name(VillagerProfession job) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(job).toString();
	}

	private static VillagerProfession job(Villager villager) {
		return villager.getVillagerData().getProfession();
	}

	private static boolean atTheCutter(GameTestHelper helper, Villager villager) {
		return villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(CUTTER)::equals).isPresent();
	}

	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? "" : key(c.getSiblings().get(0));
	}

	/**
	 * Sneak-right-clicking a jobless villager by a stonecutter with a gold nugget makes them its Jeweller; a clay ball
	 * brings the Mason back at the same stonecutter, an amethyst shard still picks the Gem Grower, and a nugget the
	 * Jeweller again. A plain right-click with a nugget picks nothing.
	 */
	//$ gametest_batch AREA '"jewellerPicked"'
	@GameTest(template = AREA, batch = "jewellerPicked")
	public void pickedWithAGoldNuggetAtAStonecutter(GameTestHelper helper) {
		shop(helper);
		ServerPlayer player = player(helper);
		Villager villager = helper.spawn(EntityType.VILLAGER, STANDING);
		villager.setNoAi(true);
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.GOLD_NUGGET), false);
		helper.assertTrue(job(villager) == VillagerProfession.NONE, "a plain right-click with a gold nugget gave " + name(job(villager)));
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.GOLD_NUGGET), true);
		helper.assertTrue(job(villager) == ModVillagers.JEWELLER, "a gold nugget at a stonecutter gave " + name(job(villager)) + ", not the Jeweller");
		helper.assertTrue(atTheCutter(helper, villager), "the Jeweller isn't working at the stonecutter");
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.CLAY_BALL), true);
		helper.assertTrue(job(villager) == VillagerProfession.MASON, "a clay ball gave " + name(job(villager)) + ", not the Mason back");
		helper.assertTrue(atTheCutter(helper, villager), "the Mason left the stonecutter");
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.AMETHYST_SHARD), true);
		helper.assertTrue(job(villager) == ModVillagers.GEM_GROWER, "an amethyst shard gave " + name(job(villager)) + ", not the Gem Grower it has picked since 28.11");
		StationsSpecGameTests.rightClick(player, villager, new ItemStack(Items.GOLD_NUGGET), true);
		helper.assertTrue(job(villager) == ModVillagers.JEWELLER && atTheCutter(helper, villager), "a nugget didn't make the Gem Grower a Jeweller");
		helper.assertTrue(Stations.of(ModVillagers.JEWELLER).map(s -> s.block() == Blocks.STONECUTTER).orElse(false), "the Jeweller's station isn't the stonecutter");
		helper.assertTrue(Stations.of(VillagerProfession.MASON).map(s -> s.block() == Blocks.STONECUTTER && s.byItself()).orElse(false),
			"a jobless villager no longer takes a stonecutter as a Mason");
		helper.assertTrue(Village.takesPart(villager), "porters wouldn't visit a Jeweller");
		helper.succeed();
	}

	/**
	 * A Novice makes Amethyst Rings from 2 amethyst shards and 2 copper ingots each (the stonecutter ringing), and
	 * leaves the emerald and the nuggets alone: no Emerald Brooch at their level.
	 */
	//$ gametest_ticks_batch AREA '1600' '"jewellerRing"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "jewellerRing")
	public void aNoviceMakesAmethystRings(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 4));
		c.setItem(1, new ItemStack(Items.COPPER_INGOT, 4));
		c.setItem(2, new ItemStack(Items.EMERALD, 1));
		c.setItem(3, new ItemStack(Items.GOLD_NUGGET, 3));
		int before = JewellerWork.cuts;
		Villager v = jeweller(helper, 1);
		work(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(count(c, ModItems.AMETHYST_RING) == 2, count(c, ModItems.AMETHYST_RING) + " amethyst rings, expected 2");
			helper.assertTrue(count(c, Items.AMETHYST_SHARD) == 0 && count(c, Items.COPPER_INGOT) == 0,
				count(c, Items.AMETHYST_SHARD) + " shards and " + count(c, Items.COPPER_INGOT) + " copper ingots left");
			helper.assertTrue(count(c, ModItems.EMERALD_BROOCH) == 0 && count(c, Items.EMERALD) == 1 && count(c, Items.GOLD_NUGGET) == 3,
				"a Novice made an Emerald Brooch");
			helper.assertTrue(JewellerWork.cuts > before, "the stonecutter never rang");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).isEmpty(), "things left in the bag");
		});
	}

	/** An Apprentice makes Emerald Brooches from an emerald and 3 gold nuggets each. */
	//$ gametest_ticks_batch AREA '1600' '"jewellerBrooch"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "jewellerBrooch")
	public void anApprenticeMakesEmeraldBrooches(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.EMERALD, 2));
		c.setItem(1, new ItemStack(Items.GOLD_NUGGET, 6));
		Villager v = jeweller(helper, 2);
		work(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(count(c, ModItems.EMERALD_BROOCH) == 2, count(c, ModItems.EMERALD_BROOCH) + " emerald brooches, expected 2");
			helper.assertTrue(count(c, Items.EMERALD) == 0 && count(c, Items.GOLD_NUGGET) == 0,
				count(c, Items.EMERALD) + " emeralds and " + count(c, Items.GOLD_NUGGET) + " nuggets left");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).isEmpty(), "things left in the bag");
		});
	}

	/**
	 * A Journeyman makes a Gold Circlet from 2 gold ingots, an emerald and an amethyst shard. The store already holds
	 * its eight brooches (what the engine keeps of each good), so the emerald and the gold go into the circlet.
	 */
	//$ gametest_ticks_batch AREA '1600' '"jewellerCirclet"'
	@GameTest(template = AREA, timeoutTicks = 1600, batch = "jewellerCirclet")
	public void aJourneymanMakesAGoldCirclet(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.GOLD_INGOT, 2));
		c.setItem(1, new ItemStack(Items.EMERALD, 1));
		c.setItem(2, new ItemStack(Items.AMETHYST_SHARD, 1));
		c.setItem(3, new ItemStack(ModItems.EMERALD_BROOCH, LuxuryWork.KEEP));
		Villager v = jeweller(helper, 3);
		work(helper, v);
		helper.succeedWhen(() -> {
			helper.assertTrue(count(c, ModItems.GOLD_CIRCLET) == 1, count(c, ModItems.GOLD_CIRCLET) + " gold circlets, expected 1");
			helper.assertTrue(count(c, Items.GOLD_INGOT) == 0 && count(c, Items.EMERALD) == 0 && count(c, Items.AMETHYST_SHARD) == 0
				&& count(c, Items.GOLD_NUGGET) == 0, count(c, Items.GOLD_INGOT) + " ingots, " + count(c, Items.EMERALD) + " emeralds, "
				+ count(c, Items.AMETHYST_SHARD) + " shards and " + count(c, Items.GOLD_NUGGET) + " nuggets left");
			helper.assertTrue(count(c, ModItems.EMERALD_BROOCH) == LuxuryWork.KEEP, count(c, ModItems.EMERALD_BROOCH) + " brooches, there were " + LuxuryWork.KEEP);
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(v).isEmpty(), "things left in the bag");
		});
	}

	/**
	 * Missing materials: with an empty chest a Novice asks for amethyst shards on the requests board; given the shards
	 * they ask for the copper; given the copper they make the ring, and nothing before that.
	 */
	//$ gametest_ticks_batch AREA '3000' '"jewellerAsks"'
	@GameTest(template = AREA, timeoutTicks = 3000, batch = "jewellerAsks")
	public void asksForWhatIsMissing(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		Villager v = jeweller(helper, 1);
		work(helper, v);
		int[] step = new int[1];
		helper.succeedWhen(() -> {
			List<Requests.Request> asked = Requests.of(helper.getLevel(), v);
			String said = asked.stream().map(r -> r.what().getString()).toList().toString();
			if (step[0] == 0) {
				helper.assertTrue(asked.stream().anyMatch(r -> r.accepts().test(new ItemStack(Items.AMETHYST_SHARD))), "no request for amethyst shards: " + said);
				helper.assertTrue(count(c, ModItems.AMETHYST_RING) == 0, "a ring out of nothing");
				c.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 2));
				step[0] = 1;
			}
			if (step[0] == 1) {
				helper.assertTrue(asked.stream().anyMatch(r -> r.accepts().test(new ItemStack(Items.COPPER_INGOT))), "no request for copper ingots: " + said);
				helper.assertTrue(count(c, ModItems.AMETHYST_RING) == 0 && count(c, Items.AMETHYST_SHARD) == 2, "a ring without copper, or the shards were taken");
				c.setItem(1, new ItemStack(Items.COPPER_INGOT, 2));
				step[0] = 2;
			}
			helper.assertTrue(count(c, ModItems.AMETHYST_RING) == 1, count(c, ModItems.AMETHYST_RING) + " rings, expected 1");
			helper.assertTrue(count(c, Items.AMETHYST_SHARD) == 0 && count(c, Items.COPPER_INGOT) == 0, "makings left over");
		});
	}

	/**
	 * Saved and loaded mid-job (the makings already in the bag): the reloaded Jeweller puts back or finishes what they
	 * had, and all the shards and copper end up as Amethyst Rings.
	 */
	//$ gametest_ticks_batch AREA '2400' '"jewellerReload"'
	@GameTest(template = AREA, timeoutTicks = 2400, batch = "jewellerReload")
	public void aReloadMidJobLosesNothing(GameTestHelper helper) {
		shop(helper);
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 4));
		c.setItem(1, new ItemStack(Items.COPPER_INGOT, 4));
		Villager first = jeweller(helper, 1);
		work(helper, first);
		Villager[] copy = new Villager[1];
		JewellerWork after = new JewellerWork(); // the reloaded Jeweller's shift: a new one, as after a restart
		ServerLevel world = helper.getLevel();
		helper.onEachTick(() -> {
			if (copy[0] == null && first.isAlive() && !ModAttachments.BUILDER_BAG.getOrCreate(first).isEmpty()) {
				CompoundTag tag = first.saveWithoutId(new CompoundTag());
				first.discard();
				Villager v = EntityType.VILLAGER.create(world);
				v.load(tag);
				v.setUUID(UUID.randomUUID());
				world.addFreshEntity(v);
				v.setNoAi(true);
				copy[0] = v;
			} else if (copy[0] != null && copy[0].isAlive()) {
				long now = world.getGameTime();
				if (after.getStatus() == Behavior.Status.STOPPED) {
					after.tryStart(world, copy[0], now);
				} else {
					after.tickOrStop(world, copy[0], now);
				}
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(copy[0] != null, "the Jeweller never fetched anything");
			helper.assertTrue(job(copy[0]) == ModVillagers.JEWELLER, "the job was lost on reload");
			helper.assertTrue(count(c, ModItems.AMETHYST_RING) == 2 && count(c, Items.AMETHYST_SHARD) == 0 && count(c, Items.COPPER_INGOT) == 0,
				count(c, ModItems.AMETHYST_RING) + " rings, " + count(c, Items.AMETHYST_SHARD) + " shards, " + count(c, Items.COPPER_INGOT)
					+ " copper ingots; expected 2, 0, 0");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(copy[0]).isEmpty(), "things left in the bag");
		});
	}

	/**
	 * Config {@code jewellers} off: a gold nugget picks no job at a stonecutter (a clay ball still a Mason), the
	 * stonecutter's tooltip no longer offers the Jeweller, and a Jeweller already hired makes nothing.
	 */
	//$ gametest_ticks_batch AREA '400' '"jewellerOff"'
	@GameTest(template = AREA, timeoutTicks = 400, batch = "jewellerOff")
	public void switchedOffMakesNothing(GameTestHelper helper) {
		shop(helper);
		Jewellers.ENABLED = false;
		Leftovers.after(helper, () -> Jewellers.ENABLED = true);
		ServerPlayer player = player(helper);
		Villager jobless = helper.spawn(EntityType.VILLAGER, new BlockPos(1, 2, 3));
		jobless.setNoAi(true);
		StationsSpecGameTests.rightClick(player, jobless, new ItemStack(Items.GOLD_NUGGET), true);
		helper.assertTrue(job(jobless) == VillagerProfession.NONE, "a gold nugget gave " + name(job(jobless)) + " with the switch off");
		StationsSpecGameTests.rightClick(player, jobless, new ItemStack(Items.CLAY_BALL), true);
		helper.assertTrue(job(jobless) == VillagerProfession.MASON, "a clay ball gave " + name(job(jobless)) + " with the switch off, not the Mason");
		jobless.discard();
		helper.getLevel().getPoiManager().release(helper.absolutePos(CUTTER));
		Stations.Station cutter = Stations.at(Blocks.STONECUTTER).orElseThrow();
		helper.assertTrue(cutter.jobs().stream().filter(Stations::available).map(j -> j.profession().get()).toList()
			.equals(List.of(VillagerProfession.MASON, ModVillagers.GEM_GROWER)), "the stonecutter's tooltip still offers the Jeweller");
		Container c = chest(helper);
		c.setItem(0, new ItemStack(Items.AMETHYST_SHARD, 4));
		c.setItem(1, new ItemStack(Items.COPPER_INGOT, 4));
		work(helper, jeweller(helper, 1));
		helper.runAfterDelay(300, () -> {
			helper.assertTrue(count(c, ModItems.AMETHYST_RING) == 0 && count(c, Items.AMETHYST_SHARD) == 4 && count(c, Items.COPPER_INGOT) == 4,
				"made a ring with the switch off");
			helper.succeed();
		});
	}

	/**
	 * A Burgher-only job (34.8): in a village with a hall, a Peasant handed a gold nugget by a stonecutter is refused in
	 * so many words and stays jobless, and so is an Artisan; the Peasant may still be its Mason (a vanilla job is never
	 * gated); a Burgher takes the job.
	 */
	//$ gametest_ticks_batch AREA '60' '"jewellerClass"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "jewellerClass")
	public void aBurgherOnlyJob(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(level.getServer().getResourceManager()));
		ClassPerks.forget();
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			new WorkplaceConfig().apply();
			ClassPerks.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(CUTTER, Blocks.STONECUTTER);
		helper.setBlock(SECOND, Blocks.STONECUTTER);
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(ClassJobs.needed(ModVillagers.JEWELLER) == SocialClasses.get(AliveWorkplace.id("burgher")),
				"the Jeweller needs " + ClassJobs.needed(ModVillagers.JEWELLER) + ", not the Burgher class");
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, seen);
			Villager dara = classed(helper, STANDING, "Dara", "peasant");
			StationsSpecGameTests.rightClick(player, dara, new ItemStack(Items.GOLD_NUGGET), true);
			helper.assertTrue(job(dara) == VillagerProfession.NONE, "the Peasant became a " + name(job(dara)));
			Component said = seen.stream().filter(c -> key(c).equals("message.aliveworkplace.job.class_needed")).findFirst().orElse(null);
			helper.assertTrue(said != null, "not told why: " + seen.stream().map(JewellerGameTests::key).toList());
			Object[] args = ((TranslatableContents) said.getContents()).getArgs();
			helper.assertTrue(args.length == 4 && ((Component) args[0]).getString().equals("Dara") && key((Component) args[1]).equals("class.aliveworkplace.peasant")
				&& key((Component) args[2]).equals("entity.minecraft.villager.jeweller") && key((Component) args[3]).equals("class.aliveworkplace.burgher"),
				"the refusal names the wrong people: " + said);

			Villager ann = classed(helper, STANDING.east(), "Ann", "artisan");
			StationsSpecGameTests.rightClick(player, ann, new ItemStack(Items.GOLD_NUGGET), true);
			helper.assertTrue(job(ann) == VillagerProfession.NONE, "the Artisan became a " + name(job(ann)));
			ann.discard();

			StationsSpecGameTests.rightClick(player, dara, new ItemStack(Items.CLAY_BALL), true);
			helper.assertTrue(job(dara) == VillagerProfession.MASON, "the Peasant isn't a Mason: " + name(job(dara)));

			Villager bram = classed(helper, SECOND.south(), "Bram", "burgher");
			StationsSpecGameTests.rightClick(player, bram, new ItemStack(Items.GOLD_NUGGET), true);
			helper.assertTrue(job(bram) == ModVillagers.JEWELLER, "the Burgher isn't a Jeweller: " + name(job(bram)));
			helper.succeed();
		});
	}

	private static Villager classed(GameTestHelper helper, BlockPos at, String name, String cls) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
		return v;
	}

	/** A player who keeps every chat and action-bar line they're shown. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "jewel-listener"), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(message);
				super.displayClientMessage(message, overlay);
			}
		};
		net.minecraft.network.Connection connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return player;
	}

	/** The luxury files, the recipes and their levels, the Pokémon that help, the trades and the art's files. */
	//$ gametest_batch AREA '"jewellerData"'
	@GameTest(template = AREA, batch = "jewellerData")
	public void theLuxuryFilesRecipesAndTrades(GameTestHelper helper) {
		check(helper, "amethyst_ring", ModItems.AMETHYST_RING, 16);
		check(helper, "emerald_brooch", ModItems.EMERALD_BROOCH, 16);
		check(helper, "gold_circlet", ModItems.GOLD_CIRCLET, 32);
		helper.assertTrue(LuxuryRecipes.forJob(ModVillagers.JEWELLER, 1).size() == 1 && LuxuryRecipes.forJob(ModVillagers.JEWELLER, 2).size() == 2
			&& LuxuryRecipes.forJob(ModVillagers.JEWELLER, 3).size() == 3 && LuxuryRecipes.forJob(ModVillagers.JEWELLER, 5).size() == 3,
			"recipes by level: " + LuxuryRecipes.forJob(ModVillagers.JEWELLER, 5));
		recipe(helper, "amethyst_ring", ModItems.AMETHYST_RING, 1, new ItemStack(Items.AMETHYST_SHARD, 2), new ItemStack(Items.COPPER_INGOT, 2));
		recipe(helper, "emerald_brooch", ModItems.EMERALD_BROOCH, 2, new ItemStack(Items.EMERALD, 1), new ItemStack(Items.GOLD_NUGGET, 3));
		recipe(helper, "gold_circlet", ModItems.GOLD_CIRCLET, 3, new ItemStack(Items.GOLD_INGOT, 2), new ItemStack(Items.EMERALD, 1),
			new ItemStack(Items.AMETHYST_SHARD, 1));
		helper.assertTrue(Partners.types(ModVillagers.JEWELLER).equals(Set.of("rock", "steel", "fairy")), "the Jeweller's partners: " + Partners.types(ModVillagers.JEWELLER));
		helper.assertTrue(LuxuryRecipes.isMaker(ModVillagers.JEWELLER), "the Jeweller isn't a luxury maker");
		// Trades at every level: one buys a making (amethyst, copper or gold), one sells the level's jewellery; over the
		// five levels all three makings are bought.
		Villager v = helper.spawn(EntityType.VILLAGER, STANDING);
		List<Item> sold = List.of(ModItems.AMETHYST_RING, ModItems.EMERALD_BROOCH, ModItems.GOLD_CIRCLET, ModItems.EMERALD_BROOCH, ModItems.GOLD_CIRCLET);
		Set<Item> bought = new HashSet<>();
		var byLevel = VillagerTrades.TRADES.get(ModVillagers.JEWELLER);
		helper.assertTrue(byLevel != null, "the Jeweller has no trades");
		for (int level = 1; level <= 5; level++) {
			VillagerTrades.ItemListing[] listings = byLevel.get(level);
			helper.assertTrue(listings != null && listings.length == 2, "level " + level + ": " + (listings == null ? 0 : listings.length) + " trades, expected 2");
			boolean buys = false;
			boolean sells = false;
			for (VillagerTrades.ItemListing listing : listings) {
				MerchantOffer offer = listing.getOffer(v, RandomSource.create(1));
				ItemStack cost = offer.getBaseCostA();
				if (offer.getResult().is(Items.EMERALD) && (cost.is(Items.AMETHYST_SHARD) || cost.is(Items.COPPER_INGOT) || cost.is(Items.GOLD_INGOT)
					|| cost.is(Items.GOLD_NUGGET))) {
					buys = true;
					bought.add(cost.getItem());
				}
				sells |= cost.is(Items.EMERALD) && offer.getResult().is(sold.get(level - 1));
			}
			helper.assertTrue(buys && sells, "level " + level + ": buys a making " + buys + ", sells " + sold.get(level - 1) + " " + sells);
		}
		helper.assertTrue(bought.contains(Items.AMETHYST_SHARD) && bought.contains(Items.COPPER_INGOT)
			&& (bought.contains(Items.GOLD_INGOT) || bought.contains(Items.GOLD_NUGGET)), "amethyst, copper and gold aren't all bought: " + bought);
		v.discard();
		var mod = FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow();
		for (String file : List.of("textures/entity/villager/profession/jeweller.png", "textures/entity/zombie_villager/profession/jeweller.png",
				"textures/item/amethyst_ring.png", "textures/item/emerald_brooch.png", "textures/item/gold_circlet.png",
				"models/item/amethyst_ring.json", "models/item/emerald_brooch.json", "models/item/gold_circlet.json")) {
			helper.assertTrue(mod.findPath("assets/aliveworkplace/" + file).isPresent(), "missing " + file);
		}
		helper.succeed();
	}

	private static void check(GameTestHelper helper, String id, Item item, int everyDays) {
		Luxuries.Luxury luxury = Luxuries.get(AliveWorkplace.id(id));
		helper.assertTrue(luxury != null && luxury.everyDays() == everyDays && luxury.matches(new ItemStack(item)), id + ": " + luxury);
	}

	/** The recipe {@code id}: made by a Jeweller of {@code level}, one {@code output} from exactly {@code inputs}. */
	private static void recipe(GameTestHelper helper, String id, Item output, int level, ItemStack... inputs) {
		LuxuryRecipes.Recipe r = LuxuryRecipes.all().get(AliveWorkplace.id(id));
		helper.assertTrue(r != null && r.level() == level && r.output() == output && r.count() == 1 && r.inputs().size() == inputs.length, "the recipe " + id + ": " + r);
		for (ItemStack want : inputs) {
			helper.assertTrue(r.inputs().stream().anyMatch(i -> i.matches(want) && i.count() == want.getCount() && i.item().isPresent()),
				id + " doesn't take " + want.getCount() + " " + want.getItem() + ": " + r);
		}
	}

	/** Every new sentence a player reads. */
	//$ gametest EMPTY_STRUCTURE
	@GameTest(template = EMPTY_STRUCTURE)
	public void theText(GameTestHelper helper) {
		expect(helper, Component.translatable("message.aliveworkplace.jeweller.title", 5), "Jeweller · 5 made");
		Stations.Station cutter = Stations.of(ModVillagers.JEWELLER).orElseThrow();
		expect(helper, Component.translatable(Stations.itemKey(cutter.jobs().stream()
			.filter(j -> j.profession().get() == ModVillagers.JEWELLER).findFirst().orElseThrow())), "A gold nugget");
		expect(helper, Component.translatable(Stations.itemKey(cutter.jobs().stream()
			.filter(j -> j.profession().get() == VillagerProfession.MASON).findFirst().orElseThrow())), "A clay ball");
		expect(helper, Component.translatable(Stations.itemKey(cutter.jobs().stream()
			.filter(j -> j.profession().get() == ModVillagers.GEM_GROWER).findFirst().orElseThrow())), "An amethyst shard");
		expect(helper, Component.translatable("entity.minecraft.villager.jeweller"), "Jeweller");
		expect(helper, Component.translatable("entity.minecraft.zombie_villager.jeweller"), "Zombie Jeweller");
		expect(helper, Component.translatable("item.aliveworkplace.amethyst_ring"), "Amethyst Ring");
		expect(helper, Component.translatable("item.aliveworkplace.emerald_brooch"), "Emerald Brooch");
		expect(helper, Component.translatable("item.aliveworkplace.gold_circlet"), "Gold Circlet");
		expect(helper, Component.translatable("aliveworkplace.config.jewellers"), "Jewellers");
		helper.assertTrue(english(Component.translatable("aliveworkplace.config.jewellers.tooltip")).startsWith("Sneak-right-click a villager by a stonecutter with a gold nugget"),
			"the setting's tooltip");
		helper.succeed();
	}

	private static void expect(GameTestHelper helper, Component text, String want) {
		String got = english(text);
		helper.assertTrue(got.equals(want), "\"" + got + "\", expected \"" + want + "\"");
	}

	/** {@code text} in English, from the mod's own en_us.json (the server has no mod language). */
	private static String english(Component text) {
		JsonObject lang;
		try (InputStream in = Files.newInputStream(FabricLoader.getInstance().getModContainer(AliveWorkplace.MOD_ID).orElseThrow()
				.findPath("assets/aliveworkplace/lang/en_us.json").orElseThrow())) {
			lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
		} catch (Exception e) {
			throw new GameTestAssertException("can't read en_us.json: " + e);
		}
		TranslatableContents contents = (TranslatableContents) text.getContents();
		if (!lang.has(contents.getKey())) {
			throw new GameTestAssertException("no English for " + contents.getKey());
		}
		String out = lang.get(contents.getKey()).getAsString();
		for (Object arg : contents.getArgs()) {
			out = out.replaceFirst("%s", String.valueOf(arg instanceof Component c ? c.getString() : arg));
		}
		return out;
	}
}

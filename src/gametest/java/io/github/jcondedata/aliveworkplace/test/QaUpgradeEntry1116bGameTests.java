package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BlueprintSupplies;
import io.github.jcondedata.aliveworkplace.build.BuildPlan;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.UpgradeOffers;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/**
 * QA (lane b, 2026-11-16, session qa-lane-b-1116-1538) of building upgrades through the entry points the earlier QA
 * (QaBuilderUpgrade1116bGameTests: the hall's Upgrades page, Starter Cottage only) left out, from the spec, not the code:
 * CHANGELOG "Builders sell upgrades: once a builder finishes a build that has an upgrade, they add its blueprint to their
 * trades", "Right-click the cottage with it and it lines up exactly; the builder only takes the old roof off and builds
 * what's new"; en_us "Lined up over the %s: give the %s to a builder to upgrade it."; blueprint-library.md "a Blueprint
 * ... used on a Table: its build unlocks there", a villager's sale of a Blueprint unlocks its build; builder.md "Progress
 * never drops after a restart". The upgrade here is the Market Stall to Market Stall II (a second stall beside it).
 */
public class QaUpgradeEntry1116bGameTests {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_test");
	private static final String BIG_AREA = "aliveworkplace_test:big_area";
	private static final ResourceLocation STALL = StarterBlueprints.MARKET_STALL.id();
	private static final ResourceLocation STALL_2 = StarterBlueprints.MARKET_STALL_2.id();
	/** The stall (15 wide, 6 deep as Market Stall II) stands at x 1-15, z 1-6. */
	private static final BlockPos STALL_REL = new BlockPos(1, 2, 1);
	private static final BlockPos BENCH = new BlockPos(3, 2, 10);
	private static final BlockPos CHEST = new BlockPos(5, 2, 10);
	private static final BlockPos CHEST_B = new BlockPos(7, 2, 10);
	private static final BlockPos HALL = new BlockPos(16, 2, 16);
	private static final BlockPos VILLAGER = new BlockPos(3, 2, 12);

	private record Up(ServerLevel level, Villager[] builder, BlueprintData.Placement placement, List<BlockPos> broken) {
		Villager villager() {
			return builder[0];
		}

		BuildSite live() {
			return BuildSiteManager.get(level).all().stream().filter(s -> s.placement().equals(placement) && !s.isDone()).findFirst().orElse(null);
		}
	}

	private static List<String> texts(List<Component> seen) {
		return seen.stream().map(Component::getString).toList();
	}

	private static ServerLevel rules(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		Leftovers.halls(helper);
		Leftovers.finished(helper);
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		boolean free = level.getGameRules().getBoolean(ModGameRules.FREE_MATERIALS);
		int delay = level.getGameRules().getInt(ModGameRules.BUILD_DELAY);
		boolean help = level.getGameRules().getBoolean(ModGameRules.BUILDERS_HELP);
		level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(false, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(2, level.getServer());
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		boolean unlocks = BlueprintUnlocks.ENABLED;
		boolean protection = io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED;
		io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = false;
		java.util.Set<ResourceLocation> wildBefore = java.util.Set.copyOf(BlueprintUnlocks.unlocked(level, null));
		Leftovers.after(helper, () -> {
			restoreWild(level, wildBefore);
			VillageHalls.RADIUS = radius;
			BlueprintUnlocks.ENABLED = unlocks;
			io.github.jcondedata.aliveworkplace.hall.VillageProtection.ENABLED = protection;
			level.getGameRules().getRule(ModGameRules.FREE_MATERIALS).set(free, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILD_DELAY).set(delay, level.getServer());
			level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(help, level.getServer());
			VillageHalls.forgetNearest();
		});
		return level;
	}

	/**
	 * The server-wide unlocks (Tables far from any hall) put back as they were before the test. With the village radius
	 * cut to 16 the builder can level up from his trade a step outside it (16.2 blocks from the hall), and his level-up's
	 * three builds then went into the server-wide set, which every later village takes in: QaBlueprintLibraryVerify1108b's
	 * starting-set test and five other Table tests failed after this class in a full build.
	 */
	@SuppressWarnings("unchecked")
	private static void restoreWild(ServerLevel level, java.util.Set<ResourceLocation> before) {
		try {
			Class<?> wild = Class.forName("io.github.jcondedata.aliveworkplace.table.BlueprintUnlocks$Wild");
			java.lang.reflect.Method get = wild.getDeclaredMethod("get", net.minecraft.server.MinecraftServer.class);
			get.setAccessible(true);
			Object set = get.invoke(null, level.getServer());
			java.lang.reflect.Field ids = wild.getDeclaredField("ids");
			ids.setAccessible(true);
			java.util.Set<ResourceLocation> now = (java.util.Set<ResourceLocation>) ids.get(set);
			if (now.retainAll(before)) {
				((net.minecraft.world.level.saveddata.SavedData) set).setDirty();
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("couldn't put the server-wide unlocks back: " + e);
		}
	}

	/** A finished Market Stall at {@link #STALL_REL}, recorded as built; {@code breaks} of its blocks taken out by a player. */
	private static Up stall(GameTestHelper helper, ServerLevel level, int breaks) {
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(STALL_REL), Rotation.NONE, Mirror.NONE);
		BuildSiteManager sites = BuildSiteManager.get(level);
		Runnable forget = () -> {
			sites.forgetFinished(placement);
			for (BuildSite site : List.copyOf(sites.all())) {
				if (site.placement().equals(placement)) {
					sites.remove(site.id());
				}
			}
		};
		forget.run();
		Leftovers.after(helper, forget);
		BlockPos origin = helper.absolutePos(STALL_REL);
		level.getStructureManager().get(STALL).orElseThrow().placeInWorld(level, origin, origin, new StructurePlaceSettings(), RandomSource.create(1L), 2);
		sites.recordFinished(STALL, placement, UUID.randomUUID());
		List<BlockPos> broken = new ArrayList<>();
		// the old stall partly broken: solid blocks of its lowest layers (posts, counter), never a container
		for (BlockPos p : BlockPos.betweenClosed(origin, origin.offset(StarterBlueprints.MARKET_STALL.size()).offset(-1, -4, -1))) {
			if (broken.size() >= breaks) {
				break;
			}
			if (level.getBlockState(p).isSolidRender(level, p) && level.getBlockEntity(p) == null) {
				broken.add(p.immutable());
			}
		}
		for (BlockPos p : broken) {
			level.destroyBlock(p, false);
		}
		helper.assertTrue(broken.size() == breaks, "setup: found only " + broken.size() + " solid blocks of the stall to break");
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		VillageHalls.forgetNearest();
		return new Up(level, new Villager[1], placement, broken);
	}

	/** The chests stocked with what the Market Stall II still needs, less {@code withhold} of an item the old stall has none of. */
	private static Item stock(GameTestHelper helper, Up u, int withhold, int[] withheld) {
		ServerLevel level = u.level();
		helper.setBlock(CHEST, Blocks.CHEST);
		helper.setBlock(CHEST_B, Blocks.CHEST);
		var report = BlueprintSupplies.check(level, new BlueprintData(STALL_2, Optional.of(StarterBlueprints.MARKET_STALL_2.size()), Optional.of(u.placement())))
			.orElseThrow(() -> new GameTestAssertException("no supply report for the Market Stall II"));
		java.util.Set<Item> inStall = new java.util.HashSet<>();
		BlockPos origin = u.placement().origin();
		BlockPos.betweenClosedStream(origin, origin.offset(StarterBlueprints.MARKET_STALL_2.size()).offset(-1, -1, -1))
			.forEach(p -> inStall.add(level.getBlockState(p).getBlock().asItem()));
		var biggest = report.missing().stream().filter(m -> !inStall.contains(m.item())).max((x, y) -> Integer.compare(x.count(), y.count()))
			.orElseThrow(() -> new GameTestAssertException("setup: the Market Stall II needs nothing the stall hasn't got: " + report.missing()));
		withheld[0] = Math.min(withhold, biggest.count());
		List<ItemStack> stacks = new ArrayList<>();
		for (var m : report.missing()) {
			int count = m.count() - (m.item() == biggest.item() ? withheld[0] : 0);
			for (int left = count; left > 0; left -= m.item().getDefaultMaxStackSize()) {
				stacks.add(new ItemStack(m.item(), Math.min(left, m.item().getDefaultMaxStackSize())));
			}
		}
		stacks.add(new ItemStack(Items.SCAFFOLDING, 16));
		helper.assertTrue(stacks.size() <= 54, "setup: the Market Stall II needs " + stacks.size() + " stacks: more than two chests");
		Container a = helper.getBlockEntity(CHEST);
		Container b = helper.getBlockEntity(CHEST_B);
		for (int i = 0; i < stacks.size(); i++) {
			(i < 27 ? a : b).setItem(i % 27, stacks.get(i).copy());
		}
		return biggest.item();
	}

	/** A server restart: every build site and the Builder saved and loaded again (the Builder as a fresh entity). */
	private static void restart(GameTestHelper helper, Up u) {
		ServerLevel level = u.level();
		var registries = level.registryAccess();
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(BuildSiteManager.get(level).save(new CompoundTag(), registries), registries));
		Villager old = u.builder()[0];
		CompoundTag tag = new CompoundTag();
		helper.assertTrue(old.save(tag), "the builder wasn't saved");
		old.discard();
		Entity loaded = EntityType.loadEntityRecursive(tag, level, e -> e);
		helper.assertTrue(loaded instanceof Villager, "the builder didn't load as a villager: " + loaded);
		level.addFreshEntity(loaded);
		u.builder()[0] = (Villager) loaded;
	}

	private static String where(Up u) {
		BuildSite s = u.live();
		return s == null ? "(no site)" : s.stage() + " " + s.status() + " cursor " + s.cursor() + " placed " + s.placed() + " skipped " + s.skipped()
			+ " missing " + s.missing();
	}

	private static int count(ServerPlayer p, Item item) {
		int n = 0;
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).is(item)) {
				n += p.getInventory().getItem(i).getCount();
			}
		}
		return n + (p.containerMenu.getCarried().is(item) ? p.containerMenu.getCarried().getCount() : 0);
	}

	private static Optional<ItemStack> blueprintOf(ServerPlayer p, ResourceLocation id) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (BlueprintItem.data(s).map(d -> d.structure().equals(id)).orElse(false)) {
				return Optional.of(s);
			}
		}
		return Optional.empty();
	}

	/** Right-click on a block of the old stall with the upgrade in hand: it lines up over it, saying so. */
	private static void lineUp(GameTestHelper helper, Up u, QaStageFiveGameTests.Client c) {
		ServerPlayer p = c.player();
		BlockPos at = u.placement().origin().offset(3, 0, 3);
		c.seen().clear();
		QaStageFourGameTests.press(helper, p, at.subtract(helper.absolutePos(BlockPos.ZERO)), Direction.UP);
		Optional<BlueprintData.Placement> placed = BlueprintItem.data(p.getMainHandItem()).flatMap(BlueprintData::placement);
		helper.assertTrue(placed.equals(Optional.of(u.placement())), "the Market Stall II clicked on the stall went to " + placed + ", the stall stands at "
			+ u.placement() + "; saw " + texts(c.seen()));
		String lined = "Lined up over the " + Blueprints.displayName(STALL).getString() + ": give the " + Blueprints.displayName(STALL_2).getString()
			+ " to a builder to upgrade it.";
		helper.assertTrue(texts(c.seen()).contains(lined), "lining up said " + texts(c.seen()));
	}

	/** The upgrade handed to the Builder with a right-click: he takes it at the stall's spot. */
	private static void handOver(GameTestHelper helper, Up u, QaStageFiveGameTests.Client c) {
		c.seen().clear();
		QaStageSixGameTests.clickVillager(c.player(), u.villager(), c.seen());
		BuildSite site = u.live();
		helper.assertTrue(site != null && site.structure().equals(STALL_2), "handed the lined-up Market Stall II the builder's site is " + site
			+ "; saw " + texts(c.seen()));
		helper.assertTrue(u.villager().getUUID().equals(site.builder()), "the upgrade's builder is " + site.builder());
		helper.assertTrue(blueprintOf(c.player(), STALL_2).isEmpty(), "the player still holds the Market Stall II after handing it over");
	}

	// ------------------------------------------------------------------------------------------------- the tests

	/**
	 * The Builder sells the Market Stall II (the trade a builder adds after finishing a Market Stall). The player buys it
	 * through the trade screen: emeralds spent, the blueprint in the bag, and the build unlocked in the village (a sale
	 * unlocks it). Three blocks of the old stall are broken first. Clicked on the stall it lines up; handed to the Builder,
	 * the upgrade starts, short of 8 of a block the stall hasn't got. He waits; the server restarts while he waits and the
	 * site keeps its stage, cursor and blocks placed; topped up, he finishes: recorded as Market Stall II, every block as
	 * drawn (the three broken ones too), his bag empty and no scaffolding left standing.
	 */
	//$ gametest_ticks_batch BIG_AREA '9000' '"qaUpgE1116bTrade"'
	@GameTest(template = BIG_AREA, timeoutTicks = 9000, batch = "qaUpgE1116bTrade")
	public void anUpgradeBoughtFromTheBuilderOverABrokenStallWaitsRestartsAndFinishes(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		BlueprintUnlocks.ENABLED = true;
		Up u = stall(helper, level, 3);
		helper.setBlock(BENCH, ModBlocks.BUILDERS_BENCH);
		int[] withheld = {0};
		Item shortItem = stock(helper, u, 8, withheld);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		u.builder()[0] = villager;
		BlockPos hall = helper.absolutePos(HALL);
		helper.assertTrue(BlueprintUnlocks.state(level, hall, STALL_2) == BlueprintUnlocks.State.LOCKED, "setup: the Market Stall II isn't locked in a new village: "
			+ BlueprintUnlocks.state(level, hall, STALL_2));
		helper.assertTrue(UpgradeOffers.offer(level, villager, STALL).equals(Optional.of(STALL_2)), "the builder didn't add the Market Stall II trade");

		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaUeT"));
		ServerPlayer p = c.player();
		p.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		p.getInventory().setItem(9, new ItemStack(Items.EMERALD, 64));
		QaStageFourGameTests.pressOn(p, villager, c.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "a right-click on the builder didn't open his trades: " + p.containerMenu + "; saw " + texts(c.seen()));
		int index = -1;
		for (int i = 0; i < villager.getOffers().size(); i++) {
			if (BlueprintItem.data(villager.getOffers().get(i).getResult()).map(d -> d.structure().equals(STALL_2)).orElse(false)) {
				index = i;
			}
		}
		helper.assertTrue(index >= 0, "the trade screen has no Market Stall II");
		MerchantOffer offer = villager.getOffers().get(index);
		int price = offer.getCostA().getCount();
		p.connection.handleSelectTrade(new ServerboundSelectTradePacket(index));
		MerchantMenu menu = (MerchantMenu) p.containerMenu;
		String slots = "payment " + menu.getSlot(0).getItem() + ", result " + menu.getSlot(2).getItem() + ", offer " + offer.getCostA() + " -> " + offer.getResult()
			+ " uses " + offer.getUses() + "/" + offer.getMaxUses();
		QaStageSixGameTests.click(p, 2, 0, ClickType.PICKUP); // one purchase (a shift-click buys as many as the trade allows)
		p.closeContainer();
		helper.assertTrue(blueprintOf(p, STALL_2).isPresent(), "buying the Market Stall II gave no blueprint (" + slots + "; seen " + texts(c.seen()) + ")");
		helper.assertTrue(blueprintOf(p, STALL_2).isPresent(), "buying the Market Stall II gave no blueprint");
		helper.assertTrue(count(p, Items.EMERALD) == 64 - price, "the Market Stall II costs " + price + " emeralds, the player has " + count(p, Items.EMERALD) + " of 64 left");
		helper.assertTrue(BlueprintUnlocks.state(level, hall, STALL_2) == BlueprintUnlocks.State.UNLOCKED, "buying the Market Stall II didn't unlock it in the village: "
			+ BlueprintUnlocks.state(level, hall, STALL_2));

		ItemStack bought = blueprintOf(p, STALL_2).orElseThrow();
		p.setItemInHand(InteractionHand.MAIN_HAND, bought.copy());
		bought.setCount(0);
		lineUp(helper, u, c);
		handOver(helper, u, c);
		BuildPlan plan = u.live().plan(level);
		for (BlockPos b : u.broken()) {
			helper.assertTrue(plan.unfinished(level).contains(b) || level.getBlockState(b).isAir(), "setup: broken " + b);
		}

		int[] waited = {0};
		int[] progress = {-1};
		boolean[] restarted = {false};
		boolean[] supplied = {false};
		helper.onEachTick(() -> {
			helper.setDayTime(2000);
			BuildSite site = u.live();
			if (supplied[0] || site == null) {
				return;
			}
			if (site.status() != BuildSite.Status.WAITING_FOR_MATERIALS) {
				waited[0] = 0;
				return;
			}
			if (++waited[0] < 100) {
				return;
			}
			if (!restarted[0]) {
				int missing = site.missing().getOrDefault(shortItem, 0);
				helper.assertTrue(missing > 0 && missing <= withheld[0], "short of " + withheld[0] + " " + shortItem + ", the site is missing " + site.missing());
				progress[0] = site.placed();
				int cursor = site.cursor();
				BuildPlan.Stage stage = site.stage();
				UUID id = site.id();
				restart(helper, u);
				BuildSite back = u.live();
				helper.assertTrue(back != null && back.id().equals(id) && back.structure().equals(STALL_2) && back.stage() == stage && back.cursor() == cursor
					&& back.placed() == progress[0], "a restart while waiting moved the upgrade from " + stage + " cursor " + cursor + " placed " + progress[0]
					+ " to " + where(u));
				helper.assertTrue(u.villager().getUUID().equals(back.builder()), "after the restart the upgrade's builder is " + back.builder());
				restarted[0] = true;
				waited[0] = 0;
				return;
			}
			helper.assertTrue(site.placed() >= progress[0], "after the restart the upgrade's progress dropped from " + progress[0] + " to " + site.placed());
			Container chest = helper.getBlockEntity(CHEST_B);
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).isEmpty()) {
					chest.setItem(i, new ItemStack(shortItem, withheld[0]));
					break;
				}
			}
			supplied[0] = true;
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(restarted[0], "the upgrade never waited for its " + shortItem + ": " + where(u));
			helper.assertTrue(supplied[0], "the upgrade never waited again after the restart: " + where(u));
			helper.assertTrue(u.live() == null, "still upgrading: " + where(u));
			helper.assertTrue(BuildSiteManager.get(level).finishedIn(level).stream().anyMatch(f -> f.placement().equals(u.placement()) && f.structure().equals(STALL_2)),
				"the stall isn't recorded as its Market Stall II");
			List<BlockPos> wrong = plan.unfinished(level);
			BlockPos origin = u.placement().origin();
			helper.assertTrue(wrong.isEmpty(), wrong.size() + " block(s) of the Market Stall II wrong, e.g. " + wrong.stream().limit(6)
				.map(b -> b.subtract(origin).toShortString() + "=" + level.getBlockState(b)).collect(Collectors.joining(", ")));
			int[] scaffolds = {0};
			BlockPos.betweenClosedStream(helper.getBounds()).forEach(b -> scaffolds[0] += level.getBlockState(b).is(Blocks.SCAFFOLDING) ? 1 : 0);
			helper.assertTrue(scaffolds[0] == 0, scaffolds[0] + " scaffolding still standing after the upgrade");
			helper.assertTrue(ModAttachments.BUILDER_BAG.getOrCreate(u.villager()).stacks().stream().allMatch(ItemStack::isEmpty),
				"the finished builder still carries " + ModAttachments.BUILDER_BAG.getOrCreate(u.villager()).stacks());
			LOGGER.info("qa upgrade entry 1116b: bought for {} emeralds, lined up, waited for {} {}, restarted at placed {}, finished; broken {} rebuilt",
				price, withheld[0], shortItem, progress[0], u.broken());
		});
	}

	/**
	 * The Market Stall II's Blueprint used on the Blueprint Table its Builder works at: with the unlocks on, the village's
	 * library gets it ("The village's library has a new blueprint: Market Stall II") and the blueprint stays in the
	 * hand; a second use says nothing new. With the unlocks switched off, a use on the Table says nothing. The same
	 * blueprint then lines up over the stall and, handed to that Builder, starts the upgrade at the stall's spot.
	 */
	//$ gametest_ticks_batch BIG_AREA '100' '"qaUpgE1116bTable"'
	@GameTest(template = BIG_AREA, timeoutTicks = 100, batch = "qaUpgE1116bTable")
	public void anUpgradeBlueprintUsedOnTheTableUnlocksItThenStartsTheUpgrade(GameTestHelper helper) {
		ServerLevel level = rules(helper);
		Up u = stall(helper, level, 0);
		helper.setBlock(BENCH, ModBlocks.BLUEPRINT_TABLE);
		Villager villager = helper.spawn(EntityType.VILLAGER, VILLAGER);
		villager.setNoAi(true);
		Builders.employ(level, villager, helper.absolutePos(BENCH));
		u.builder()[0] = villager;
		BlockPos hall = helper.absolutePos(HALL);
		QaStageFiveGameTests.Client c = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaUeB"));
		ServerPlayer p = c.player();
		String line = "The village's library has a new blueprint: " + Blueprints.displayName(STALL_2).getString();

		BlueprintUnlocks.ENABLED = false;
		p.setItemInHand(InteractionHand.MAIN_HAND, BlueprintItem.create(STALL_2, StarterBlueprints.MARKET_STALL_2.size()));
		c.seen().clear();
		QaStageFourGameTests.press(helper, p, BENCH, Direction.UP);
		p.closeContainer();
		helper.assertFalse(texts(c.seen()).contains(line), "with the unlocks switched off the Table said " + texts(c.seen()));

		BlueprintUnlocks.ENABLED = true;
		helper.assertTrue(BlueprintUnlocks.state(level, hall, STALL_2) == BlueprintUnlocks.State.LOCKED, "setup: the Market Stall II isn't locked in a new village: "
			+ BlueprintUnlocks.state(level, hall, STALL_2));
		c.seen().clear();
		QaStageFourGameTests.press(helper, p, BENCH, Direction.UP);
		p.closeContainer();
		helper.assertTrue(BlueprintUnlocks.state(level, hall, STALL_2) == BlueprintUnlocks.State.UNLOCKED, "the Market Stall II used on the Table isn't unlocked: "
			+ BlueprintUnlocks.state(level, hall, STALL_2));
		helper.assertTrue(texts(c.seen()).contains(line), "the Table should say '" + line + "', said " + texts(c.seen()));
		helper.assertTrue(BlueprintItem.data(p.getMainHandItem()).map(d -> d.structure().equals(STALL_2)).orElse(false),
			"using the Market Stall II on the Table took it from the hand: " + p.getMainHandItem());
		c.seen().clear();
		QaStageFourGameTests.press(helper, p, BENCH, Direction.UP);
		p.closeContainer();
		helper.assertFalse(texts(c.seen()).contains(line), "a second use on the Table announced it again: " + texts(c.seen()));

		lineUp(helper, u, c);
		handOver(helper, u, c);
		LOGGER.info("qa upgrade entry 1116b: table unlock (switch off silent, on once), lined up and handed over at {}", u.placement());
		helper.succeed();
	}
}

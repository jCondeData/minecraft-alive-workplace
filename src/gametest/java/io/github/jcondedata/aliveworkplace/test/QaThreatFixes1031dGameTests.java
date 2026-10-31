package io.github.jcondedata.aliveworkplace.test;

import com.google.gson.JsonParser;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.ArcState;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.threat.CampGround;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * QA (2026-10-31, d), written from the specs, not the code:
 * <ul>
 * <li>31.4/31.20: an event arc starts in the village on its event; an id in {@code disabledArcs} never starts (kills the
 * nightly mutation survivor in {@code Arcs.onEvent}, {@code !disabled} to {@code disabled});</li>
 * <li>B702: anyone or anything half inside camp land that comes back is set on top of it (a villager across the edge of
 * two-block-high land, an item across a one- and a two-block column);</li>
 * <li>B710: a player's shop takes the owner's price from a Hero of the Village into the owner's chest, every trade; a
 * plain villager still gives the hero vanilla's discount;</li>
 * <li>B711: in one village, a side arc's villager cast in a part of the same name is no stand-in for the main arc's.</li>
 * </ul>
 */
public class QaThreatFixes1031dGameTests implements FabricGameTest {
	private static final String HUGE = "aliveworkplace_test:huge_area";
	private static final String BIG = "aliveworkplace_test:big_area";
	private static final String SMALL = "aliveworkplace_test:build_area";
	private static final BlockPos HALL = new BlockPos(5, 2, 5);
	private static final String EVENT = "qa_threat_fixes_1031d";
	private static final ResourceLocation TALK = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test/talk");

	// --- 1. Event arcs and disabledArcs (31.4, 31.20) -----------------------------------------------------------------

	/** Two event arcs on the same event, both waiting for a flag nobody sets (so they keep running till stopped). */
	private static Arcs.Arc[] eventArcs() {
		Arcs.Arc[] out = new Arcs.Arc[2];
		String[] names = {"first", "second"};
		for (int i = 0; i < 2; i++) {
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/event_" + names[i] + "_1031d");
			Arcs.Arc arc = Arcs.read(id, JsonParser.parseString("{\"name\": \"QA event arc " + names[i] + "\", \"trigger\": {\"event\": \"" + EVENT
				+ "\"}, \"chapters\": [{\"name\": \"Waiting\", \"needs_flags\": [\"never_set\"]}]}").getAsJsonObject());
			Arcs.add(arc);
			out[i] = arc;
		}
		return out;
	}

	private static void arcSettings(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		int cooldown = Arcs.COOLDOWN_DAYS;
		int atOnce = Arcs.AT_ONCE;
		boolean enabled = Arcs.ENABLED;
		Set<String> disabled = Arcs.DISABLED;
		VillageHalls.RADIUS = 8;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.COOLDOWN_DAYS = cooldown;
			Arcs.AT_ONCE = atOnce;
			Arcs.ENABLED = enabled;
			Arcs.DISABLED = disabled;
		});
	}

	private static BlockPos hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		((VillageHallBlockEntity) level.getBlockEntity(abs)).setLastQuestDay(Long.MAX_VALUE / 2);
		Arcs.stopAll(level, abs);
		return abs;
	}

	/** The tag an arc puts on its cast (the story tag and the arc's path, '/' as '.'), as the wiki's story-arcs page shows it. */
	private static String arcTag(ResourceLocation arc) {
		return Arcs.TAG + "_" + arc.getPath().replace('/', '.');
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * VillageNeeds.DAY + 1000);
	}

	/**
	 * An enabled event arc starts on its event (twice in a row: the second time right after the first ended, so the
	 * cooldown doesn't hold it); listed in {@code disabledArcs}, the event starts nothing and nothing is counted.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaEventArcStarts1031d"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaEventArcStarts1031d")
	public void anEnabledEventArcStartsOnItsEventADisabledOneNever(GameTestHelper helper) {
		Leftovers.clear(helper);
		arcSettings(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, HALL);
		Arcs.Arc[] arcs = eventArcs();
		Set<String> ours = Set.of(arcs[0].id().toString(), arcs[1].id().toString());
		setDay(level, Chronicle.day(level) + 1);
		Arcs.ENABLED = true;
		Arcs.AT_ONCE = Arcs.runningCount(level.getServer()) + 5;
		try {
			Arcs.DISABLED = Set.of();
			for (int round = 1; round <= 2; round++) {
				ArcState s = Arcs.onEvent(level, hall, EVENT);
				helper.assertTrue(s != null && ours.contains(s.id) && Arcs.running(level, hall) == s,
					"round " + round + ": the event started no arc of its own, with neither disabled: " + (s == null ? null : s.id) + ", running " + Arcs.running(level, hall));
				Arcs.stopAll(level, hall);
			}
			helper.assertTrue(Arcs.onEvent(level, hall, "qa_no_such_event_1031d") == null && Arcs.running(level, hall) == null, "another event started an arc");

			Arcs.DISABLED = ours;
			int started = Arcs.STARTED.get();
			ArcState s = Arcs.onEvent(level, hall, EVENT);
			helper.assertTrue(s == null && Arcs.running(level, hall) == null, "an event arc in disabledArcs started on its event: " + (s == null ? Arcs.running(level, hall) : s.id));
			helper.assertTrue(Arcs.STARTED.get() == started, "a disabled event arc was counted as started: " + (Arcs.STARTED.get() - started));
		} finally {
			Arcs.DISABLED = Set.of();
			Arcs.stopAll(level, hall);
		}
		helper.succeed();
	}

	/** Two arcs on one event, one disabled: the event passes over it and starts the other, whichever of the two it is. */
	//$ gametest_ticks_batch HUGE '40' '"qaEventArcSkips1031d"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaEventArcSkips1031d")
	public void theEventSkipsTheDisabledArcAndStartsTheOther(GameTestHelper helper) {
		Leftovers.clear(helper);
		arcSettings(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, HALL);
		Arcs.Arc[] arcs = eventArcs();
		setDay(level, Chronicle.day(level) + 1);
		Arcs.ENABLED = true;
		Arcs.AT_ONCE = Arcs.runningCount(level.getServer()) + 5;
		try {
			for (int off = 0; off < 2; off++) {
				Arcs.Arc disabled = arcs[off];
				Arcs.Arc other = arcs[1 - off];
				Arcs.DISABLED = Set.of(disabled.id().toString());
				ArcState s = Arcs.onEvent(level, hall, EVENT);
				helper.assertTrue(s != null && s.id.equals(other.id().toString()) && Arcs.running(level, hall) == s,
					"with " + disabled.id() + " disabled the event started " + (s == null ? "nothing" : s.id) + ", not " + other.id());
				Arcs.stopAll(level, hall);
			}
		} finally {
			Arcs.DISABLED = Set.of();
			Arcs.stopAll(level, hall);
		}
		helper.succeed();
	}

	// --- 2. B702: land that comes back lifts what is half in it ------------------------------------------------------

	/**
	 * B702 from the other side of the fix's own test: a villager (wider and taller than an item) standing with its middle
	 * over open ground but its side in a column of land that comes back two blocks high is set on top of that column,
	 * not left in it, and takes no suffocation damage; an item across a one-block and a two-block column lands on the
	 * higher one.
	 */
	//$ gametest_ticks_batch SMALL '60' '"qaCampLiftVillager1031d"'
	@GameTest(template = SMALL, timeoutTicks = 60, batch = "qaCampLiftVillager1031d")
	public void aVillagerAcrossTheEdgeOfTwoHighLandIsSetOnItsTop(GameTestHelper helper) {
		Leftovers.clear(helper);
		ServerLevel level = helper.getLevel();
		Leftovers.after(helper, () -> level.getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2)).forEach(Entity::discard));
		// row z=2: x=2 open, x=3 two high, x=4 open; row z=4: x=2 one high, x=3 two high, x=4 open
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 4; z++) {
				helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
				helper.setBlock(new BlockPos(x, 3, z), Blocks.AIR);
			}
		}
		helper.setBlock(new BlockPos(3, 2, 2), Blocks.DIRT);
		helper.setBlock(new BlockPos(3, 3, 2), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(2, 2, 4), Blocks.GRASS_BLOCK);
		helper.setBlock(new BlockPos(3, 2, 4), Blocks.DIRT);
		helper.setBlock(new BlockPos(3, 3, 4), Blocks.GRASS_BLOCK);
		BlockPos from = helper.absolutePos(new BlockPos(2, 2, 2));
		BlockPos to = helper.absolutePos(new BlockPos(4, 3, 4));
		CampGround ground = CampGround.take(level, new BoundingBox(from.getX(), from.getY(), from.getZ(), to.getX(), to.getY(), to.getZ()), 0);
		for (BlockPos p : List.of(new BlockPos(3, 2, 2), new BlockPos(3, 3, 2), new BlockPos(2, 2, 4), new BlockPos(3, 2, 4), new BlockPos(3, 3, 4))) {
			helper.setBlock(p, Blocks.AIR); // (the camp cut the land away)
		}
		BlockPos land = helper.absolutePos(new BlockPos(3, 2, 2));
		BlockPos step = helper.absolutePos(new BlockPos(3, 2, 4));
		double floor = land.getY();
		Villager villager = EntityType.VILLAGER.create(level);
		villager.moveTo(land.getX() - 0.05, floor, land.getZ() + 0.5, 0f, 0f); // (its middle over x=2, its side 0.25 into x=3)
		villager.setNoAi(true);
		level.addFreshEntity(villager);
		ItemEntity item = new ItemEntity(level, step.getX() - 0.01, floor, step.getZ() + 0.5, new ItemStack(Items.DIAMOND, 5)); // (middle over the one-high column)
		item.setDeltaMovement(Vec3.ZERO);
		level.addFreshEntity(item);
		float health = villager.getHealth();

		ground.restore(level, Map.of(), Set.of());
		helper.assertTrue(helper.getBlockState(new BlockPos(3, 3, 2)).is(Blocks.GRASS_BLOCK) && helper.getBlockState(new BlockPos(2, 2, 4)).is(Blocks.GRASS_BLOCK)
			&& helper.getBlockState(new BlockPos(3, 3, 4)).is(Blocks.GRASS_BLOCK), "(setup) the land didn't come back");
		helper.assertTrue(villager.getY() == floor + 2 && level.noCollision(villager),
			"the villager across the edge of two-high land is at y " + (villager.getY() - floor) + " over the floor, in the land: " + !level.noCollision(villager));
		helper.assertTrue(item.getY() == floor + 2 && level.noCollision(item),
			"the diamond across a one-high and a two-high column is at y " + (item.getY() - floor) + " over the floor, in the land: " + !level.noCollision(item));
		helper.runAfterDelay(40, () -> {
			helper.assertTrue(villager.isAlive() && level.noCollision(villager) && !villager.isInWall() && villager.getHealth() >= health,
				"the villager is in the land 40 ticks on: y " + (villager.getY() - floor) + ", health " + villager.getHealth() + " of " + health);
			helper.assertTrue(item.isAlive() && level.noCollision(item) && item.getY() >= floor + 1, "the diamond sank: y " + (item.getY() - floor) + " over the floor");
			helper.succeed();
		});
	}

	// --- 3. B710: a player's shop asks the owner's price from a hero --------------------------------------------------

	/**
	 * B710 ("the owner's chest gets 14"): a Hero of the Village with 40 emeralds buys at a shop priced 20 an item; every
	 * trade he makes pays the full 20 into the owner's chest, and he keeps nothing he shouldn't.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaShopHeroPays1031d"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaShopHeroPays1031d")
	public void aHerosEveryTradePaysTheOwnersFullPriceIntoTheChest(GameTestHelper helper) {
		Leftovers.clear(helper);
		QaShopPaymentSaves1031GameTests.shop(helper, new ItemStack(Items.EMERALD, 20));
		Container chest = helper.getBlockEntity(new BlockPos(2, 2, 4));
		Villager keeper = QaShopPaymentSaves1031GameTests.keeper(helper);
		QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaHeroPays"));
		ServerPlayer p = buyer.player();
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 24000, 1)); // Hero II: a bigger discount
		p.setShiftKeyDown(Money.cobbleDollars());
		QaStageFourGameTests.pressOn(p, keeper, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the trade screen didn't open: " + p.containerMenu);
		p.containerMenu.getSlot(0).set(new ItemStack(Items.EMERALD, 40));
		QaStageSixGameTests.click(p, 2, 0, ClickType.QUICK_MOVE);
		p.closeContainer();
		int bought = p.getInventory().countItem(Items.DIAMOND_BLOCK);
		int kept = p.getInventory().countItem(Items.EMERALD);
		int paid = chest.countItem(Items.EMERALD) + 9 * chest.countItem(Items.EMERALD_BLOCK);
		helper.assertTrue(bought >= 1, "the hero bought nothing with 40 emeralds at a 20-emerald shop");
		helper.assertTrue(paid == 20 * bought && kept == 40 - 20 * bought,
			"a Hero of the Village bought " + bought + " at 20 each: the owner's chest got " + paid + " emeralds (expected " + 20 * bought + ") and he kept " + kept);
		helper.succeed();
	}

	/**
	 * B710's other side: the shop fix touches only players' shops. A plain villager selling a diamond for 20 emeralds
	 * still gives a Hero of the Village vanilla's discount.
	 */
	//$ gametest_ticks_batch BIG '100' '"qaPlainHeroDiscount1031d"'
	@GameTest(template = BIG, timeoutTicks = 100, batch = "qaPlainHeroDiscount1031d")
	public void aPlainVillagerStillGivesAHeroTheDiscount(GameTestHelper helper) {
		Leftovers.clear(helper);
		Villager smith = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 2, 2));
		smith.setNoAi(true);
		smith.setVillagerData(smith.getVillagerData().setProfession(VillagerProfession.TOOLSMITH).setLevel(1));
		smith.setVillagerXp(1);
		MerchantOffers offers = new MerchantOffers();
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 20), new ItemStack(Items.DIAMOND), 12, 1, 0.05f));
		smith.setOffers(offers);
		QaStageFiveGameTests.Client buyer = QaStageSixGameTests.client(helper, GameType.SURVIVAL, QaStageSixGameTests.name("qaPlainHero"));
		ServerPlayer p = buyer.player();
		p.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 24000, 0));
		QaStageFourGameTests.pressOn(p, smith, buyer.seen());
		helper.assertTrue(p.containerMenu instanceof MerchantMenu, "the plain villager's trade screen didn't open: " + p.containerMenu);
		MerchantOffer offer = smith.getOffers().stream().filter(o -> o.getBaseCostA().is(Items.EMERALD) && o.getBaseCostA().getCount() == 20).findFirst().orElse(null);
		helper.assertTrue(offer != null, "the plain villager's 20-emerald offer is gone: " + smith.getOffers().size() + " offers");
		int asked = offer.getCostA().getCount();
		p.closeContainer();
		helper.assertTrue(asked < 20, "a plain villager asks a Hero of the Village the full " + asked + " emeralds (vanilla's discount gone)");
		helper.succeed();
	}

	// --- 4. B711: a talk step counts only its own arc's villager, also within one village ------------------------------

	/**
	 * B711 inside one village: the main arc names Ilse as its elder; a side arc in the same village casts Otto in a part
	 * of the same name. Right-clicking Otto leaves the main arc's talk open; Ilse finishes it.
	 */
	//$ gametest_ticks_batch HUGE '40' '"qaTalkSideArc1031d"'
	@GameTest(template = HUGE, timeoutTicks = 40, batch = "qaTalkSideArc1031d")
	public void aSideArcsElderInTheSameVillageIsNoStandIn(GameTestHelper helper) {
		Leftovers.clear(helper);
		arcSettings(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		BlockPos hall = hall(helper, HALL);
		Arcs.DISABLED = Set.of();
		Arcs.ENABLED = true;
		Arcs.Arc side = Arcs.read(ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "qa/side_elder_1031d"), JsonParser.parseString("""
			{"name": "The QA Side Elder", "side": true, "trigger": {"chance": 0}, "roles": {"elder": {"villager": "any"}},
			 "chapters": [{"name": "Waiting", "needs_flags": ["never_set"]}]}""").getAsJsonObject());
		Arcs.add(side);
		Arcs.Arc talk = Arcs.get(TALK).orElse(null);
		helper.assertTrue(talk != null, "the test pack's talk arc didn't load");
		setDay(level, Chronicle.day(level) + 1);
		Arcs.COOLDOWN_DAYS = 0;
		Arcs.AT_ONCE = Arcs.runningCount(level.getServer()) + 5;
		Villager ilse = helper.spawn(EntityType.VILLAGER, HALL.east(2));
		ilse.setCustomName(Component.literal("Ilse"));
		ilse.setNoAi(true);
		ArcState main = Arcs.start(level, hall, talk);
		Villager otto = helper.spawn(EntityType.VILLAGER, HALL.west(2));
		otto.setCustomName(Component.literal("Otto"));
		otto.setNoAi(true);
		ArcState sideState = Arcs.start(level, hall, side);
		helper.assertTrue(main != null && sideState != null && ilse.getTags().contains(arcTag(TALK))
			&& otto.getTags().contains(arcTag(side.id())) && otto.getTags().contains(ArcEffects.roleTag("elder")),
			"(setup) the elders weren't cast: Ilse " + ilse.getTags() + " Otto " + otto.getTags());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		player.teleportTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 2.5);
		ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, otto);
		long open = Stories.open(level, hall).stream().filter(q -> TALK.toString().equals(q.arc)).count();
		helper.assertTrue(Arcs.running(level, hall) == main && open == 1,
			"right-clicking Otto (the side arc's elder, same village) counted for the main arc's talk with Ilse: running " + Arcs.running(level, hall) + ", open " + open);
		InteractionResult r = ArcEffects.onTalk(player, level, InteractionHand.MAIN_HAND, ilse);
		helper.assertTrue(r == InteractionResult.SUCCESS && Arcs.running(level, hall) == null, "Ilse herself didn't count: " + r);
		helper.assertTrue(Arcs.side(level, hall).stream().anyMatch(s -> s.id.equals(side.id().toString())), "the side arc ended with the main one's talk");
		helper.succeed();
	}
}

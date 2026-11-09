package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.card.JobCards;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.card.StatusCardMenu;
import io.github.jcondedata.aliveworkplace.farm.FieldJob;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.mail.RouteData;
import io.github.jcondedata.aliveworkplace.mine.Miners;
import io.github.jcondedata.aliveworkplace.mine.QuarryData;
import io.github.jcondedata.aliveworkplace.mine.QuarrySite;
import io.github.jcondedata.aliveworkplace.mine.QuarrySiteManager;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModComponents;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Jobs;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * ROADMAP 42.5: a sneak-right-click with an empty hand on a Builder, Miner, Fisher, Farmer, Lumberjack, Orchard keeper
 * or Postman opens their status card (name, job and level, what they're doing, their work in numbers, what they need,
 * Hall page, In chat, Close) instead of the status lines in chat. In chat sends the old lines, word for word; Hall page
 * opens the Village Hall on their page, and is grayed with the reason when no hall is near. The card closes when the
 * villager dies, reads the same after a save and reload, and a plain click still trades. Every click goes through the
 * server's packet handler, as a player's does.
 */
public class JobCardGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos STATION = new BlockPos(3, 2, 3);
	private static final BlockPos AT = new BlockPos(4, 2, 4);
	private static final BlockPos HALL = new BlockPos(9, 2, 3);
	private static final ResourceLocation TEST_HUT = ResourceLocation.fromNamespaceAndPath("aliveworkplace_test", "test_hut");

	record Setup(Villager villager, ServerPlayer player, List<Component> seen) {
	}

	/** A worker at {@code block} named {@code name}, a player beside them, and (when {@code hall}) a Village Hall nearby. */
	private static Setup worker(GameTestHelper helper, net.minecraft.world.level.block.Block block,
								net.minecraft.resources.ResourceKey<net.minecraft.world.entity.ai.village.poi.PoiType> poi, VillagerProfession job,
								String name, boolean hall) {
		Leftovers.clear(helper);
		helper.setDayTime(2000);
		if (hall) {
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		}
		VillageHalls.forgetNearest();
		helper.setBlock(STATION, block);
		Villager v = helper.spawn(EntityType.VILLAGER, AT);
		v.setNoAi(true);
		Jobs.employ(helper.getLevel(), v, helper.absolutePos(STATION), poi, job);
		v.setCustomName(Component.literal(name));
		List<Component> seen = new ArrayList<>();
		ServerPlayer player = QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL);
		return new Setup(v, player, seen);
	}

	/** A sneak-right-click with an empty hand, as the client sends it; the card it opened. */
	static StatusCardMenu open(GameTestHelper helper, Setup s) {
		NurseCardGameTests.click(s.player(), s.villager(), true);
		helper.assertTrue(s.player().containerMenu instanceof StatusCardMenu, "no card opened: " + s.player().containerMenu.getClass().getSimpleName()
			+ ", chat " + QaStageFourGameTests.keys(s.seen()));
		return (StatusCardMenu) s.player().containerMenu;
	}

	/**
	 * After a sneak-right-click with an empty hand that opened a status card: its first button, In chat, pressed as the
	 * client sends it, so the old status lines arrive in chat (for the tests written against them; 43.3's way).
	 */
	static void inChat(ServerPlayer player) {
		if (player.containerMenu instanceof StatusCardMenu card) {
			player.connection.handleContainerButtonClick(new net.minecraft.network.protocol.game.ServerboundContainerButtonClickPacket(
				card.containerId, card.card().buttons().get(0).id()));
		}
	}

	/** "Label: value" of each line. */
	static List<String> lines(StatusCard card) {
		List<String> out = new ArrayList<>();
		for (StatusCard.Line line : card.lines()) {
			out.add(line.label().getString() + ": " + line.value().getString());
		}
		return out;
	}

	/** The card's buttons and hint, the same for every job; In chat sends the old status line whose first key is {@code chatKey}. */
	private static void buttonsAndChat(GameTestHelper helper, Setup s, StatusCardMenu menu, String chatKey) {
		StatusCard card = menu.card();
		helper.assertValueEqual(card.heading().getString(), "Their work", "the heading");
		helper.assertValueEqual(card.button(JobCards.HALL).label().getString(), "Hall page", "Hall page's label");
		helper.assertValueEqual(card.button(JobCards.CHAT).label().getString(), "In chat", "In chat's label");
		helper.assertTrue(card.button(StatusCardMenu.CLOSE) != null && card.button(JobCards.CHAT).usable(), "no Close, or In chat grayed");
		helper.assertValueEqual(card.hint().getString(), "In chat: every detail, with the Stop link.", "the hint");
		helper.assertTrue(QaStageFourGameTests.keys(s.seen()).stream().noneMatch(k -> k.startsWith(chatKey)), "the status line came with the card: "
			+ QaStageFourGameTests.keys(s.seen()));
		NurseCardGameTests.press(s.player(), menu, JobCards.CHAT);
		helper.assertTrue(s.player().containerMenu == s.player().inventoryMenu, "the card stayed open after In chat");
		// (a counted line's key may end in .one or .rest_one)
		helper.assertTrue(QaStageFourGameTests.keys(s.seen()).stream().anyMatch(k -> k.startsWith(chatKey)), "In chat didn't send " + chatKey + ": "
			+ QaStageFourGameTests.keys(s.seen()));
	}

	private static void tag(GameTestHelper helper, StatusCard card, String name, String tag) {
		helper.assertValueEqual(card.title().getString(), name, "the title");
		helper.assertValueEqual(card.tag().getString(), tag, "the tag");
		helper.assertFalse(card.subtitle().getString().isBlank(), "no line saying what they're doing");
	}

	/**
	 * A Builder raising the test hut with empty chests: the card names the hut with its progress and a bar, Experience,
	 * and the materials short as "Needs …: ×N" in orange (three, then "and N more"); Hall page opens the Village Hall on
	 * his page; In chat sends the old status and material list.
	 */
	//$ gametest_batch AREA '"jobCardBuilder"'
	@GameTest(template = AREA, batch = "jobCardBuilder")
	public void builderCardShowsTheSiteAndWhatIsShort(GameTestHelper helper) {
		Setup s = worker(helper, ModBlocks.BUILDERS_BENCH, ModVillagers.BUILDERS_BENCH_POI, ModVillagers.BUILDER, "Bob", true);
		ServerLevel level = helper.getLevel();
		BuildSite site = Builders.start(level, s.villager(), s.player(), TEST_HUT,
			new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(12, 1, 12)), Rotation.NONE, Mirror.NONE));
		Leftovers.after(helper, () -> BuildSiteManager.get(level).remove(site.id()));
		StatusCardMenu menu = open(helper, s);
		StatusCard card = menu.card();
		tag(helper, card, "Bob", "Builder · Novice");
		List<String> lines = lines(card);
		helper.assertValueEqual(lines.get(0), Blueprints.siteName(level, site).getString() + ": 0%", "the site's line");
		helper.assertTrue(card.lines().get(0).hasBar() && card.lines().get(0).icon().is(ModItems.BLUEPRINT), "the site's line has no bar or blueprint");
		helper.assertValueEqual(lines.get(1), "Experience: " + s.villager().getVillagerXp() + " / 10 XP", "the experience line");
		List<StatusCard.Line> needs = card.lines().stream().filter(l -> l.label().getString().startsWith("Needs ")).toList();
		helper.assertTrue(!needs.isEmpty() && needs.size() <= 3 && needs.stream().allMatch(l -> l.color() == StatusCard.ORANGE
			&& l.value().getString().startsWith("×") && !l.icon().isEmpty()), "the materials short: " + lines);
		if (site.missing().size() > 3) {
			helper.assertValueEqual(lines.get(lines.size() - 1), "and " + (site.missing().size() - 3) + " more: ", "the last line");
		}
		helper.assertTrue(card.button(JobCards.HALL).usable(), "Hall page grayed: " + card.button(JobCards.HALL).refusal().getString());
		buttonsAndChat(helper, s, menu, "message.aliveworkplace.status.header");
		helper.assertTrue(QaStageFourGameTests.find(s.seen(), "message.aliveworkplace.site_list.chat_heading") != null,
			"In chat didn't send the material list: " + QaStageFourGameTests.keys(s.seen()));
		StatusCardMenu again = open(helper, s);
		NurseCardGameTests.press(s.player(), again, JobCards.HALL);
		helper.assertTrue(s.player().containerMenu instanceof ChoiceMenu hall && hall.getType() == ModBlocks.VILLAGE_HALL_MENU,
			"Hall page didn't open the hall: " + s.player().containerMenu.getClass().getSimpleName());
		helper.assertTrue(((ChoiceMenu) s.player().containerMenu).icon(4).getHoverName().getString().contains("Bob"),
			"the hall isn't on Bob's page: " + ((ChoiceMenu) s.player().containerMenu).icon(4).getHoverName().getString());
		s.player().closeContainer();
		helper.succeed();
	}

	/**
	 * A Builder with no bench and no hall about: the card says what the chat said ("… has no Builder's Bench …"), Needs:
	 * Nothing, and Hall page is grayed with "There's no Village Hall near Dara."; pressed anyway, it opens nothing and says
	 * so. A plain click (not sneaking) doesn't open the card.
	 */
	//$ gametest_batch AREA '"jobCardNoHall"'
	@GameTest(template = AREA, batch = "jobCardNoHall")
	public void builderCardWithoutABenchOrAHall(GameTestHelper helper) {
		Leftovers.clear(helper);
		// a Village Hall left near this spot by an earlier batch would count: they go
		ServerLevel level = helper.getLevel();
		for (int i = 0; i < 20; i++) {
			VillageHalls.forgetNearest();
			Optional<BlockPos> left = VillageHalls.nearest(level, helper.absolutePos(AT));
			if (left.isEmpty()) {
				break;
			}
			level.setBlockAndUpdate(left.get(), Blocks.AIR.defaultBlockState());
		}
		VillageHalls.forgetNearest();
		Villager dara = helper.spawn(EntityType.VILLAGER, AT);
		dara.setNoAi(true);
		dara.setVillagerData(dara.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(5));
		dara.setCustomName(Component.literal("Dara"));
		List<Component> seen = new ArrayList<>();
		Setup s = new Setup(dara, QaStageFourGameTests.player(helper, seen, GameType.SURVIVAL), seen);
		StatusCardMenu menu = open(helper, s);
		StatusCard card = menu.card();
		tag(helper, card, "Dara", "Builder · Master");
		helper.assertTrue(card.subtitle().getString().startsWith(Component.translatable("message.aliveworkplace.status.no_bench", dara.getDisplayName()).getString()),
			"the subtitle isn't the old no-bench line: " + card.subtitle().getString());
		helper.assertValueEqual(lines(card), List.of("Experience: Top level", "Needs: Nothing"), "the lines");
		helper.assertValueEqual(card.button(JobCards.HALL).refusal().getString(), "There's no Village Hall near Dara.", "Hall page's reason");
		NurseCardGameTests.press(s.player(), menu, JobCards.HALL);
		helper.assertTrue(s.player().containerMenu == s.player().inventoryMenu, "something opened with no hall: " + s.player().containerMenu);
		helper.assertTrue(seen.stream().anyMatch(c -> c.getString().equals("There's no Village Hall near Dara.")), "pressed anyway, no reason: "
			+ QaStageFourGameTests.keys(seen));
		buttonsAndChat(helper, s, open(helper, s), "message.aliveworkplace.status.no_bench");
		NurseCardGameTests.click(s.player(), dara, false);
		helper.assertFalse(s.player().containerMenu instanceof StatusCardMenu, "a plain click opened the card");
		s.player().closeContainer();
		helper.succeed();
	}

	/** A Miner given a 3 × 3 quarry: "Quarry 3 × 3 × 2: 0%" with a bar and "Blocks mined: 0"; In chat sends the quarry's status. */
	//$ gametest_batch AREA '"jobCardMiner"'
	@GameTest(template = AREA, batch = "jobCardMiner")
	public void minerCardShowsTheQuarry(GameTestHelper helper) {
		Setup s = worker(helper, ModBlocks.MINERS_BENCH, ModVillagers.MINERS_BENCH_POI, ModVillagers.MINER, "Gimli", true);
		ServerLevel level = helper.getLevel();
		ItemStack marker = new ItemStack(ModItems.QUARRY_MARKER);
		marker.set(ModComponents.QUARRY, new QuarryData(Optional.of(level.dimension().location()),
			Optional.of(helper.absolutePos(new BlockPos(14, 1, 14))), Optional.of(helper.absolutePos(new BlockPos(16, 1, 16))), 2));
		Miners.assign(s.player(), s.villager(), marker);
		QuarrySite site = Miners.activeSite(level, s.villager());
		helper.assertTrue(site != null, "the miner didn't take the marker: " + QaStageFourGameTests.keys(s.seen()));
		Leftovers.after(helper, () -> QuarrySiteManager.get(level).remove(site.id()));
		StatusCard card = open(helper, s).card();
		tag(helper, card, "Gimli", "Miner · Novice");
		List<String> lines = lines(card);
		helper.assertValueEqual(lines.get(0), "Quarry 3 × 3 × " + site.box().getYSpan() + ": 0%", "the quarry's line");
		helper.assertTrue(card.lines().get(0).hasBar(), "the quarry's line has no bar");
		helper.assertValueEqual(lines.get(1), "Blocks mined: 0", "the mined line");
		helper.assertTrue(lines.contains("Experience: " + s.villager().getVillagerXp() + " / 10 XP"), "no experience line: " + lines);
		s.player().closeContainer();
		buttonsAndChat(helper, s, open(helper, s), "message.aliveworkplace.quarry.header");
		helper.succeed();
	}

	/** A hired Fisher with 7 fish: "Fish caught: 7"; In chat sends the fisher's status. */
	//$ gametest_batch AREA '"jobCardFisher"'
	@GameTest(template = AREA, batch = "jobCardFisher")
	public void fisherCardShowsTheCatch(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.BARREL, PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN, "Finn", true);
		ModAttachments.FISHER_JOB.set(s.villager(), true);
		ModAttachments.FISH_CAUGHT.set(s.villager(), 7);
		StatusCard card = open(helper, s).card();
		tag(helper, card, "Finn", "Fisherman · Novice");
		helper.assertValueEqual(lines(card).get(0), "Fish caught: 7", "the catch");
		helper.assertTrue(card.lines().get(0).icon().is(Items.COD), "no fish icon");
		s.player().closeContainer();
		buttonsAndChat(helper, s, open(helper, s), "message.aliveworkplace.fisher.header");
		helper.succeed();
	}

	/** A Farmer with a 5 × 4 field and 12 crops harvested: "Field: 5 × 4", "Crops harvested: 12"; In chat sends the field's status. */
	//$ gametest_batch AREA '"jobCardFarmer"'
	@GameTest(template = AREA, batch = "jobCardFarmer")
	public void farmerCardShowsTheField(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER, "Bram", true);
		BlockPos o = helper.absolutePos(new BlockPos(12, 1, 12));
		ModAttachments.FARM_FIELD.set(s.villager(), new FieldJob(new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 4, o.getY() + 1, o.getZ() + 3)));
		ModAttachments.FARM_HARVESTED.set(s.villager(), 12);
		StatusCard card = open(helper, s).card();
		tag(helper, card, "Bram", "Farmer · Novice");
		helper.assertValueEqual(lines(card).subList(0, 2), List.of("Field: 5 × 4", "Crops harvested: 12"), "the field's lines");
		s.player().closeContainer();
		buttonsAndChat(helper, s, open(helper, s), "message.aliveworkplace.field.header");
		helper.succeed();
	}

	/** A Lumberjack with a tree farm: "Tree farm: 4 × 5", "Trees felled: 3", "Saplings planted: 4"; In chat sends its status. */
	//$ gametest_batch AREA '"jobCardLumberjack"'
	@GameTest(template = AREA, batch = "jobCardLumberjack")
	public void lumberjackCardShowsTheTreeFarm(GameTestHelper helper) {
		Setup s = worker(helper, ModBlocks.CHOPPING_BLOCK, ModVillagers.CHOPPING_BLOCK_POI, ModVillagers.LUMBERJACK, "Jack", true);
		BlockPos o = helper.absolutePos(new BlockPos(12, 1, 12));
		ModAttachments.TREE_FARM.set(s.villager(), new FieldJob(new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 3, o.getY() + 3, o.getZ() + 4)));
		ModAttachments.TREES_FELLED.set(s.villager(), 3);
		ModAttachments.SAPLINGS_PLANTED.set(s.villager(), 4);
		StatusCard card = open(helper, s).card();
		tag(helper, card, "Jack", "Lumberjack · Novice");
		helper.assertValueEqual(lines(card).subList(0, 3), List.of("Tree farm: 4 × 5", "Trees felled: 3", "Saplings planted: 4"), "the farm's lines");
		s.player().closeContainer();
		buttonsAndChat(helper, s, open(helper, s), "message.aliveworkplace.tree_farm.header");
		helper.succeed();
	}

	/** An Orchard keeper: "Orchard: 5 × 4", "Fruit picked: 9", "Saplings planted: 2"; In chat sends the orchard's status. */
	//$ gametest_batch AREA '"jobCardOrchard"'
	@GameTest(template = AREA, batch = "jobCardOrchard")
	public void orchardCardShowsTheOrchard(GameTestHelper helper) {
		Setup s = worker(helper, ModBlocks.FRUIT_BASKET, ModVillagers.FRUIT_BASKET_POI, ModVillagers.ORCHARD_KEEPER, "Pomona", true);
		BlockPos o = helper.absolutePos(new BlockPos(12, 1, 12));
		ModAttachments.ORCHARD.set(s.villager(), new FieldJob(new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 4, o.getY() + 3, o.getZ() + 3), true));
		ModAttachments.FRUIT_PICKED.set(s.villager(), 9);
		ModAttachments.SAPLINGS_PLANTED.set(s.villager(), 2);
		StatusCard card = open(helper, s).card();
		tag(helper, card, "Pomona", "Orchard Keeper · Novice");
		helper.assertValueEqual(lines(card).subList(0, 3), List.of("Orchard: 5 × 4", "Fruit picked: 9", "Saplings planted: 2"), "the orchard's lines");
		s.player().closeContainer();
		buttonsAndChat(helper, s, open(helper, s), "message.aliveworkplace.orchard_area.header");
		helper.succeed();
	}

	/** A Postman with a route and 5 parcels delivered: "Parcels delivered: 5", "Courier routes: 1"; In chat sends the routes. */
	//$ gametest_batch AREA '"jobCardPostman"'
	@GameTest(template = AREA, batch = "jobCardPostman")
	public void postmanCardShowsTheRoutes(GameTestHelper helper) {
		Setup s = worker(helper, ModBlocks.MAILBOX, ModVillagers.MAILBOX_POI, ModVillagers.POSTMAN, "Pat", true);
		ModAttachments.COURIER_ROUTES.set(s.villager(), List.of(new RouteData(Optional.of(helper.absolutePos(new BlockPos(1, 2, 1))),
			Optional.of(helper.absolutePos(new BlockPos(1, 2, 8))), List.of())));
		ModAttachments.MAIL_DELIVERED.set(s.villager(), 5);
		StatusCard card = open(helper, s).card();
		tag(helper, card, "Pat", "Postman · Novice");
		helper.assertValueEqual(lines(card).subList(0, 2), List.of("Parcels delivered: 5", "Courier routes: 1"), "the postman's lines");
		s.player().closeContainer();
		buttonsAndChat(helper, s, open(helper, s), "message.aliveworkplace.route.header");
		helper.succeed();
	}

	/** The villager dies while the card is open: Hall page does nothing, and the card closes on the next ticks. */
	//$ gametest_ticks_batch AREA '60' '"jobCardDied"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "jobCardDied")
	public void cardClosesWhenTheVillagerDies(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.BARREL, PoiTypes.FISHERMAN, VillagerProfession.FISHERMAN, "Finn", true);
		ModAttachments.FISHER_JOB.set(s.villager(), true);
		StatusCardMenu menu = open(helper, s);
		s.villager().kill();
		helper.assertFalse(menu.stillValid(s.player()), "the card is still valid for a dead villager");
		NurseCardGameTests.press(s.player(), menu, JobCards.HALL);
		helper.assertFalse(s.player().containerMenu instanceof ChoiceMenu, "the hall opened for a dead villager");
		helper.runAfterDelay(5, () -> {
			helper.assertTrue(s.player().containerMenu == s.player().inventoryMenu, "the card stayed open: " + s.player().containerMenu);
			helper.succeed();
		});
	}

	/** A Journeyman farmer saved and loaded: the card names the level and shows the same field and count. */
	//$ gametest_batch AREA '"jobCardReload"'
	@GameTest(template = AREA, batch = "jobCardReload")
	public void cardAfterASaveAndReload(GameTestHelper helper) {
		Setup s = worker(helper, Blocks.COMPOSTER, PoiTypes.FARMER, VillagerProfession.FARMER, "Bram", true);
		BlockPos o = helper.absolutePos(new BlockPos(12, 1, 12));
		ModAttachments.FARM_FIELD.set(s.villager(), new FieldJob(new BoundingBox(o.getX(), o.getY(), o.getZ(), o.getX() + 4, o.getY() + 1, o.getZ() + 3)));
		ModAttachments.FARM_HARVESTED.set(s.villager(), 30);
		s.villager().setVillagerData(s.villager().getVillagerData().setLevel(3));
		s.villager().setVillagerXp(80);
		CompoundTag saved = new CompoundTag();
		s.villager().saveWithoutId(saved);
		s.villager().discard();
		Villager copy = EntityType.VILLAGER.create(helper.getLevel());
		copy.load(saved);
		helper.assertTrue(helper.getLevel().addFreshEntity(copy), "couldn't put the loaded farmer back");
		Setup t = new Setup(copy, s.player(), s.seen());
		StatusCard card = open(helper, t).card();
		tag(helper, card, "Bram", "Farmer · Journeyman");
		helper.assertValueEqual(lines(card).subList(0, 3), List.of("Field: 5 × 4", "Crops harvested: 30", "Experience: 80 / 150 XP"), "the lines after a reload");
		s.player().closeContainer();
		helper.succeed();
	}
}

package io.github.jcondedata.aliveworkplace.test;

import com.mojang.authlib.GameProfile;
import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.city.CityPlan;
import io.github.jcondedata.aliveworkplace.city.CityPlanItem;
import io.github.jcondedata.aliveworkplace.city.Plots;
import io.github.jcondedata.aliveworkplace.city.StewardDesk;
import io.github.jcondedata.aliveworkplace.city.StewardJobs;
import io.github.jcondedata.aliveworkplace.city.StewardResearch;
import io.github.jcondedata.aliveworkplace.city.StewardRules;
import io.github.jcondedata.aliveworkplace.city.StewardWishes;
import io.github.jcondedata.aliveworkplace.city.Stewards;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.StewardDeskPage;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.research.Research;
import io.github.jcondedata.aliveworkplace.research.ScholarWork;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.work.Stations;
import io.github.jcondedata.aliveworkplace.work.Words;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * QA for ROADMAP 27.8 (the Steward's desk) and 27.9 (his {@code assign_jobs} and {@code research}), written from the
 * spec: every boundary from both sides (a decline stays away and a proposal lapses at exactly 3 days, 9 proposals and
 * never a 10th), strangers against the owner and a friend on every desk button (Approve all, Decline, Cancel, modes),
 * Rest and {@code stewardSelfRun: false}, the owner's one morning line, proposals answered after a reload, and the jobs
 * and research orders against a spec oracle, counted from a real village (store, raid, the ill).
 */
public class QaStewardDeskJobsGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final long DAY = 24000L;

	// ---- players that hear what they're told -------------------------------------------------------------------

	/** One line a player was shown: in chat, or above the hotbar ({@code overlay}). */
	record Seen(Component text, boolean overlay) {
		String key() {
			return text.getContents() instanceof TranslatableContents t ? t.getKey() : "";
		}
	}

	static ServerPlayer listener(GameTestHelper helper, String name, List<Seen> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
			@Override
			public void displayClientMessage(Component message, boolean overlay) {
				seen.add(new Seen(message, overlay));
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

	static List<Seen> lines(List<Seen> seen, String key, boolean overlay) {
		return seen.stream().filter(s -> s.overlay() == overlay && s.key().equals(key)).toList();
	}

	static List<String> keys(List<Seen> seen) {
		return seen.stream().map(Seen::key).toList();
	}

	static String text(String key) {
		return Language.getInstance().getOrDefault(key);
	}

	/** The desk through the hall's screen, as {@code player} opens it: the hall, then "What next?". */
	static ChoiceMenu desk(ServerPlayer player, BlockPos hall) {
		ChoiceMenu menu = VillageHallScreen.forTest(player, hall);
		menu.press(VillageHallScreen.ADVICE, player);
		return menu;
	}

	static String lore(ItemStack stack) {
		return String.valueOf(stack.get(DataComponents.LORE));
	}

	// ---- the desk village (27.8): a hall, a builder at his table, a Steward and a Homes zone east ----------------

	private static final BlockPos DESK_HALL = new BlockPos(2, 2, 14);
	private static final BlockPos DESK_TABLE = new BlockPos(2, 2, 10);
	private static final BlockPos SPOT = new BlockPos(11, 2, 13);
	private static final ResourceLocation WELL = AliveWorkplace.id("well");

	private record DeskVillage(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}
	}

	private static DeskVillage deskVillage(GameTestHelper helper, List<Seen> heard) {
		Leftovers.clear(helper);
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
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 12; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(DESK_HALL, ModBlocks.VILLAGE_HALL);
		helper.setBlock(DESK_TABLE, ModBlocks.BLUEPRINT_TABLE);
		BlockPos hall = helper.absolutePos(DESK_HALL);
		ServerPlayer owner = listener(helper, "qa-desk-owner", heard);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BitSet cells = new BitSet();
		for (int x = 6; x <= 29; x++) {
			for (int z = 2; z <= 25; z++) {
				cells.set(CityPlan.cellAt(hall, helper.absolutePos(new BlockPos(x, 2, z))));
			}
		}
		entity.setPlan(CityPlan.EMPTY.addZone("homes", "Homes 1", "").paint(0, cells));
		Villager builder = helper.spawn(EntityType.VILLAGER, DESK_TABLE.east());
		Builders.employ(level, builder, helper.absolutePos(DESK_TABLE));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, DESK_HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		return new DeskVillage(level, hall, owner, steward);
	}

	private static StewardWishes.Wish buildWish(ResourceLocation rule) {
		return new StewardWishes.Wish(rule, new StewardRules.Effect(StewardRules.Kind.BUILD, Optional.of(WELL), Optional.of("homes"),
			Optional.empty(), Optional.empty()), 50, "steward.aliveworkplace.why.beds", List.of(3L));
	}

	/** The Well's plot at {@link #SPOT}, which the 27.7 check accepts. */
	private static Plots.Plot plot(GameTestHelper helper, DeskVillage v) {
		Plots.Verdict verdict = Plots.check(v.level(), v.hall(), v.entity().plan(), "homes", WELL, helper.absolutePos(SPOT),
			Rotation.COUNTERCLOCKWISE_90, Mirror.NONE);
		helper.assertTrue(verdict.plot().isPresent(), "setup: the spot doesn't fit: " + verdict);
		return verdict.plot().get();
	}

	private static Optional<StewardDesk.Proposal> offer(DeskVillage v, ResourceLocation rule, Plots.Plot plot) {
		return StewardDesk.offer(v.level(), v.hall(), buildWish(rule), plot.blueprint(), plot.placement(), plot.zone(), false);
	}

	/** The start of the next world day (so the test's days don't depend on the hour it began). */
	private static long nextDawn(ServerLevel level) {
		return (level.getDayTime() / DAY + 1) * DAY;
	}

	/** Declined at a dawn: still away on the last tick of the third day, back (and the decline forgotten) at the third dawn. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskDeclineEdge"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskDeclineEdge")
	public void aDeclineHoldsToTheLastTickOfTheThirdDay(GameTestHelper helper) {
		DeskVillage v = deskVillage(helper, new ArrayList<>());
		ServerLevel level = v.level();
		long time = level.getDayTime();
		ResourceLocation rule = AliveWorkplace.id("qa/decline_edge");
		try {
			long dawn = nextDawn(level);
			level.setDayTime(dawn);
			Plots.Plot plot = plot(helper, v);
			StewardDesk.Proposal p = offer(v, rule, plot).orElseThrow(() -> new AssertionError("setup: not proposed"));
			helper.assertTrue(StewardDesk.decline(level, v.hall(), v.owner(), p.id()), "the owner's decline refused");
			level.setDayTime(dawn + 3 * DAY - 1);
			helper.assertTrue(offer(v, rule, plot).isEmpty(), "proposed again a tick before 3 days had passed");
			StewardDesk.lapse(level, v.hall());
			helper.assertTrue(StewardDesk.of(level, v.hall()).declined().containsKey(rule.toString()), "the decline forgotten a tick before 3 days");
			level.setDayTime(dawn + 3 * DAY);
			StewardDesk.lapse(level, v.hall());
			helper.assertTrue(!StewardDesk.of(level, v.hall()).declined().containsKey(rule.toString()), "the decline still kept after 3 days");
			helper.assertTrue(offer(v, rule, plot).isPresent(), "not proposed again once 3 days had passed");
		} finally {
			level.setDayTime(time);
		}
		helper.succeed();
	}

	/** Offered at a dawn and never answered: on the desk on the last tick of its third day, gone at the third dawn; others stay. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskLapseEdge"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskLapseEdge")
	public void aProposalLapsesAtExactlyThreeDays(GameTestHelper helper) {
		DeskVillage v = deskVillage(helper, new ArrayList<>());
		ServerLevel level = v.level();
		long time = level.getDayTime();
		try {
			long dawn = nextDawn(level);
			level.setDayTime(dawn);
			Plots.Plot plot = plot(helper, v);
			StewardDesk.Proposal old = offer(v, AliveWorkplace.id("qa/lapse_old"), plot).orElseThrow(() -> new AssertionError("setup: not proposed"));
			level.setDayTime(dawn + DAY);
			StewardDesk.Proposal young = offer(v, AliveWorkplace.id("qa/lapse_young"), plot).orElseThrow(() -> new AssertionError("setup: not proposed"));
			level.setDayTime(dawn + 3 * DAY - 1);
			StewardDesk.lapse(level, v.hall());
			helper.assertTrue(StewardDesk.of(level, v.hall()).get(old.id()).isPresent(), "lapsed a tick before 3 days");
			level.setDayTime(dawn + 3 * DAY);
			StewardDesk.lapse(level, v.hall());
			helper.assertTrue(StewardDesk.of(level, v.hall()).get(old.id()).isEmpty(), "still on the desk after 3 days");
			helper.assertTrue(StewardDesk.of(level, v.hall()).get(young.id()).isPresent(), "a 2-day-old proposal lapsed with it");
			// a lapsed proposal is not a decline: it may be proposed again at once
			helper.assertTrue(offer(v, AliveWorkplace.id("qa/lapse_old"), plot).isPresent(), "a lapsed proposal kept away like a declined one");
		} finally {
			level.setDayTime(time);
		}
		helper.succeed();
	}

	/** Nine proposals fill the desk's row and Approve all counts them; a tenth waits until one is answered. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskNine"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskNine")
	public void nineProposalsAndNeverATenth(GameTestHelper helper) {
		DeskVillage v = deskVillage(helper, new ArrayList<>());
		Plots.Plot plot = plot(helper, v);
		List<StewardDesk.Proposal> made = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			Optional<StewardDesk.Proposal> p = offer(v, AliveWorkplace.id("qa/cap_" + i), plot);
			helper.assertTrue(p.isPresent(), "proposal " + (i + 1) + " refused");
			made.add(p.get());
		}
		helper.assertTrue(offer(v, AliveWorkplace.id("qa/cap_9"), plot).isEmpty(), "a 10th proposal on the desk");
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().size() == 9, "desk holds " + StewardDesk.of(v.level(), v.hall()).proposals().size());
		ChoiceMenu menu = desk(v.owner(), v.hall());
		for (int i = 0; i < 9; i++) {
			helper.assertTrue(menu.icon(StewardDeskPage.FIRST_PROPOSAL + i).is(ModItems.BLUEPRINT), "proposal " + (i + 1) + " not shown on the desk");
		}
		ItemStack all = menu.icon(StewardDeskPage.APPROVE_ALL);
		helper.assertTrue(all.is(Items.EMERALD_BLOCK) && all.getHoverName().getString().contains("9"), "Approve all: " + all.getHoverName().getString());
		helper.assertTrue(StewardDesk.decline(v.level(), v.hall(), v.owner(), made.get(4).id()), "decline refused");
		helper.assertTrue(offer(v, AliveWorkplace.id("qa/cap_9"), plot).isPresent(), "the 10th not proposed once a place came free");
		helper.assertTrue(offer(v, AliveWorkplace.id("qa/cap_10"), plot).isEmpty(), "an 11th proposal on the desk");
		helper.succeed();
	}

	/**
	 * In a protected village a stranger can't cancel a build (shift-click on the desk), decline, approve all, ask for another
	 * spot or style or change the mode; a friend of the owner can, and a plain click (no shift) cancels nothing.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaDeskRights"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskRights")
	public void aStrangerCantAnswerAnythingButAFriendCan(GameTestHelper helper) {
		DeskVillage v = deskVillage(helper, new ArrayList<>());
		ServerLevel level = v.level();
		v.entity().setProtected(true);
		Plots.Plot plot = plot(helper, v);
		StewardDesk.Proposal built = offer(v, AliveWorkplace.id("qa/rights_a"), plot).orElseThrow(() -> new AssertionError("setup: not proposed"));
		StewardDesk.Proposal other = offer(v, AliveWorkplace.id("qa/rights_b"), plot).orElseThrow(() -> new AssertionError("setup: not proposed"));
		helper.assertTrue(StewardDesk.approve(level, v.hall(), v.owner(), built.id()).ok(), "setup: the owner's approval refused");
		helper.assertTrue(StewardDesk.openSites(level, v.hall()).size() == 1, "setup: no site");
		UUID site = StewardDesk.openSites(level, v.hall()).get(0).id();

		List<Seen> heard = new ArrayList<>();
		ServerPlayer stranger = listener(helper, "qa-stranger", heard);
		ChoiceMenu menu = desk(stranger, v.hall());
		helper.assertTrue(menu.icon(StewardDeskPage.FIRST_OPEN).is(Items.BRICKS), "the open build isn't on the desk");
		menu.clicked(StewardDeskPage.FIRST_OPEN, 0, ClickType.QUICK_MOVE, stranger);
		helper.assertTrue(StewardDesk.openSites(level, v.hall()).size() == 1, "a stranger cancelled the build from the desk");
		helper.assertTrue(!lines(heard, "message.aliveworkplace.steward.desk.not_allowed", true).isEmpty(), "the stranger wasn't told why: " + keys(heard));
		helper.assertTrue(!StewardDesk.cancel(level, v.hall(), stranger, site), "a stranger's cancel accepted");
		helper.assertTrue(!StewardDesk.decline(level, v.hall(), stranger, other.id()), "a stranger's decline accepted");
		helper.assertTrue(StewardDesk.approveAll(level, v.hall(), stranger).isEmpty(), "Approve all worked for a stranger");
		helper.assertTrue(!StewardDesk.anotherSpot(level, v.hall(), stranger, other.id()), "a stranger asked for another spot");
		helper.assertTrue(!StewardDesk.anotherStyle(level, v.hall(), stranger, other.id()), "a stranger asked for another style");
		helper.assertTrue(!StewardDesk.setMode(level, v.hall(), stranger, CityPlan.Mode.REST), "a stranger set Rest");
		StewardDesk.Proposal still = StewardDesk.of(level, v.hall()).get(other.id()).orElse(null);
		helper.assertTrue(still != null && !still.searching() && still.placement().equals(other.placement()), "a stranger changed the proposal: " + still);
		helper.assertTrue(StewardDesk.mode(level, v.hall()) == CityPlan.Mode.ASK, "the mode changed: " + StewardDesk.mode(level, v.hall()));

		ServerPlayer friend = helper.makeMockServerPlayerInLevel();
		Friends friends = Friends.get(level.getServer());
		friends.add(v.owner().getUUID(), friend.getUUID(), "qa-friend");
		try {
			ChoiceMenu theirs = desk(friend, v.hall());
			theirs.clicked(StewardDeskPage.FIRST_OPEN, 0, ClickType.PICKUP, friend);
			helper.assertTrue(StewardDesk.openSites(level, v.hall()).size() == 1, "a plain click cancelled the build");
			theirs.clicked(StewardDeskPage.FIRST_OPEN, 0, ClickType.QUICK_MOVE, friend);
			helper.assertTrue(StewardDesk.openSites(level, v.hall()).isEmpty(), "the owner's friend couldn't cancel from the desk");
			helper.assertTrue(StewardDesk.decline(level, v.hall(), friend, other.id()), "the owner's friend couldn't decline");
		} finally {
			friends.remove(v.owner().getUUID(), friend.getUUID());
		}
		helper.succeed();
	}

	// ---- the jobs village (27.9): flat grass round a hall in the middle, counted within RADIUS ---------------------

	private static final int RADIUS = 14;
	private static final BlockPos HALL = new BlockPos(15, 2, 15);
	private static final ResourceLocation JOBS_RULE = AliveWorkplace.id("jobs");
	private static final ResourceLocation RESEARCH_RULE = AliveWorkplace.id("research");

	private static ServerLevel ground(GameTestHelper helper) {
		Leftovers.clear(helper);
		int before = VillageHalls.RADIUS;
		VillageHalls.RADIUS = RADIUS;
		Leftovers.after(helper, () -> VillageHalls.RADIUS = before);
		ServerLevel level = helper.getLevel();
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
				for (int y = 2; y < 8; y++) {
					helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
				}
			}
		}
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		return level;
	}

	static Villager jobless(GameTestHelper helper, BlockPos at) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true); // stays put: vanilla's own job search mustn't race the Steward
		return v;
	}

	record Village(ServerLevel level, BlockPos hall, ServerPlayer owner, Villager steward) {
		VillageHallBlockEntity entity() {
			return (VillageHallBlockEntity) level.getBlockEntity(hall);
		}

		void wish(StewardWishes.Wish... wishes) {
			entity().setStewardWishes(new StewardWishes.State(StewardWishes.day(level), List.of(wishes), Map.of()));
		}
	}

	/** A village with its owner (who hears what he's told), a builder at work and a Steward; today's wishes. */
	static Village village(GameTestHelper helper, List<Seen> heard, StewardWishes.Wish... wishes) {
		ServerLevel level = ground(helper);
		BlockPos hall = helper.absolutePos(HALL);
		ServerPlayer owner = listener(helper, "qa-jobs-owner", heard);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		entity.setOwner(owner.getUUID(), owner.getGameProfile().getName());
		BlockPos table = new BlockPos(24, 2, 24);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = jobless(helper, table.north());
		Builders.employ(level, builder, helper.absolutePos(table));
		Villager steward = StewardGameTests.seasoned(helper.spawn(EntityType.VILLAGER, HALL.south(2)));
		ItemStack plan = new ItemStack(ModItems.CITY_PLAN);
		CityPlanItem.bind(level, owner, plan, hall);
		helper.assertTrue(Stewards.appoint(owner, steward, plan) == InteractionResult.SUCCESS, "setup: Steward not appointed");
		Village v = new Village(level, hall, owner, steward);
		v.wish(wishes);
		return v;
	}

	static StewardWishes.Wish jobsWish() {
		return new StewardWishes.Wish(JOBS_RULE, new StewardRules.Effect(StewardRules.Kind.ASSIGN_JOBS, Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty()), 75, "steward.aliveworkplace.why.jobless", List.of(1L));
	}

	static StewardWishes.Wish researchWish() {
		return new StewardWishes.Wish(RESEARCH_RULE, new StewardRules.Effect(StewardRules.Kind.RESEARCH, Optional.empty(), Optional.empty(),
			Optional.empty(), Optional.empty()), 40, "steward.aliveworkplace.why.research", List.of(3L));
	}

	/** His morning: the planning round, then a scholar at work with nothing researched. */
	static void morning(Village v) {
		StewardDesk.plan(v.level(), v.steward(), v.hall());
		ScholarWork.IDLE.idle(v.level(), v.steward(), v.hall());
	}

	private static VillagerProfession job(Villager v) {
		return v.getVillagerData().getProfession();
	}

	/** Approve all on the desk: a stranger's press does nothing; the owner's gives the jobs and sets the research, with one line. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskApproveAll"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskApproveAll")
	public void approveAllAnswersEveryProposalForTheOwnerOnly(GameTestHelper helper) {
		List<Seen> heard = new ArrayList<>();
		Village v = village(helper, heard, jobsWish(), researchWish());
		helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
		Villager dara = jobless(helper, new BlockPos(12, 2, 15));
		morning(v);
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().size() == 2, "proposals: " + StewardDesk.of(v.level(), v.hall()).proposals());

		List<Seen> theirs = new ArrayList<>();
		ServerPlayer stranger = listener(helper, "qa-stranger", theirs);
		ChoiceMenu menu = desk(stranger, v.hall());
		helper.assertTrue(menu.icon(StewardDeskPage.APPROVE_ALL).is(Items.EMERALD_BLOCK), "no Approve all with two proposals");
		menu.press(StewardDeskPage.APPROVE_ALL, stranger);
		helper.assertTrue(job(dara) == VillagerProfession.NONE && v.entity().research().currentTopic() == null
			&& StewardDesk.of(v.level(), v.hall()).proposals().size() == 2, "a stranger's Approve all did something");
		helper.assertTrue(!lines(theirs, "message.aliveworkplace.steward.desk.not_allowed", true).isEmpty(), "the stranger wasn't told: " + keys(theirs));

		ChoiceMenu mine = desk(v.owner(), v.hall());
		mine.press(StewardDeskPage.APPROVE_ALL, v.owner());
		helper.assertTrue(job(dara) == VillagerProfession.FARMER, "Dara is " + job(dara));
		helper.assertTrue(v.entity().research().currentTopic() == Research.Topic.SWIFT_HANDS, "researching " + v.entity().research().currentTopic());
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "left on the desk: " + StewardDesk.of(v.level(), v.hall()).proposals());
		List<Seen> approved = lines(heard, "message.aliveworkplace.steward.desk.approved_all", true);
		helper.assertTrue(approved.size() == 1, "the owner's lines: " + keys(heard));
		String line = approved.get(0).text().getString();
		helper.assertTrue(line.startsWith("Approved: ") && line.contains("Give 1 villager a job") && line.contains(" and ") && line.contains("Research Swift Hands"),
			"its line: " + line);
		helper.succeed();
	}

	/**
	 * {@code stewardSelfRun: false}: a hall saved in Run the village keeps asking (the desk's Run button says it's off, a
	 * proposal and the owner's "new proposals" line, no job given, no topic chosen); with it on again he does it himself,
	 * with one morning line.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaSelfRunOff"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaSelfRunOff")
	public void selfRunOffKeepsARunVillageAsking(GameTestHelper helper) {
		boolean before = StewardDesk.SELF_RUN;
		List<Seen> heard = new ArrayList<>();
		try {
			StewardDesk.SELF_RUN = true;
			Village v = village(helper, heard, jobsWish(), researchWish());
			helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "setup: Run refused");
			StewardDesk.SELF_RUN = false;
			helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
			Villager dara = jobless(helper, new BlockPos(12, 2, 15));
			ChoiceMenu menu = desk(v.owner(), v.hall());
			helper.assertTrue(lore(menu.icon(StewardDeskPage.RUN)).contains(text("screen.aliveworkplace.desk.mode.run.off"))
				|| lore(menu.icon(StewardDeskPage.RUN)).contains("screen.aliveworkplace.desk.mode.run.off"), "the Run button doesn't say it's off: " + lore(menu.icon(StewardDeskPage.RUN)));
			morning(v);
			StewardDesk.State desk = StewardDesk.of(v.level(), v.hall());
			helper.assertTrue(desk.proposals().size() == 2, "not asked: " + desk.proposals());
			helper.assertTrue(job(dara) == VillagerProfession.NONE, "a job given with stewardSelfRun off");
			helper.assertTrue(v.entity().research().currentTopic() == null, "a topic chosen with stewardSelfRun off");
			helper.assertTrue(lines(heard, "message.aliveworkplace.steward.desk.new", false).size() == 1, "the owner wasn't told once: " + keys(heard));
			helper.assertTrue(lines(heard, "message.aliveworkplace.steward.desk.ran", false).isEmpty()
				&& lines(heard, "message.aliveworkplace.steward.research.chosen", false).isEmpty(), "told he ran it himself: " + keys(heard));
			StewardDesk.SELF_RUN = true;
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(job(dara) == VillagerProfession.FARMER, "Dara is " + job(dara));
			helper.assertTrue(v.entity().research().currentTopic() == Research.Topic.SWIFT_HANDS, "researching " + v.entity().research().currentTopic());
			List<Seen> ran = lines(heard, "message.aliveworkplace.steward.desk.ran", false);
			helper.assertTrue(ran.size() == 1, "morning lines: " + ran);
			String line = ran.get(0).text().getString();
			helper.assertTrue(line.startsWith("This morning in ") && line.contains("Farmer at the Composter") && line.contains("Research Swift Hands"), "its line: " + line);
		} finally {
			StewardDesk.SELF_RUN = before;
		}
		helper.succeed();
	}

	/** Ask me first: no line on a morning with nothing new; one line (rendered right) with new proposals, never a second that day or the next. */
	//$ gametest_ticks_batch AREA '40' '"qaMorningLine"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaMorningLine")
	public void oneMorningLineOnlyForNewProposals(GameTestHelper helper) {
		List<Seen> heard = new ArrayList<>();
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		try {
			level.setDayTime(nextDawn(level) + 1000);
			Village v = village(helper, heard);
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(lines(heard, "message.aliveworkplace.steward.desk.new", false).isEmpty(), "told with nothing proposed: " + keys(heard));
			v.wish(jobsWish(), researchWish());
			helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
			jobless(helper, new BlockPos(12, 2, 15));
			morning(v);
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().size() == 2, "proposals: " + StewardDesk.of(level, v.hall()).proposals());
			List<Seen> told = lines(heard, "message.aliveworkplace.steward.desk.new", false);
			helper.assertTrue(told.size() == 1, "lines this morning: " + told.size());
			String expected = Component.translatable("message.aliveworkplace.steward.desk.new", v.steward().getDisplayName(),
				VillageHalls.name(level, v.hall()), 1).getString();
			helper.assertTrue(told.get(0).text().getString().equals(expected) && expected.startsWith("New proposals on ") && !expected.contains("%"),
				"the line: " + told.get(0).text().getString() + ", wanted " + expected);
			// the next morning, nothing new: no line
			level.setDayTime(level.getDayTime() + DAY);
			v.wish(jobsWish(), researchWish());
			morning(v);
			helper.assertTrue(lines(heard, "message.aliveworkplace.steward.desk.new", false).size() == 1, "told again with nothing new: " + keys(heard));
		} finally {
			level.setDayTime(time);
		}
		helper.succeed();
	}

	/** Rest chosen on the desk: with jobs and research wished, a jobless villager and a free block, he proposes and says nothing. */
	//$ gametest_ticks_batch AREA '40' '"qaRestNothing"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaRestNothing")
	public void restChosenOnTheDeskProposesNothing(GameTestHelper helper) {
		boolean before = StewardDesk.SELF_RUN;
		List<Seen> heard = new ArrayList<>();
		try {
			StewardDesk.SELF_RUN = true;
			Village v = village(helper, heard, jobsWish(), researchWish());
			helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
			Villager dara = jobless(helper, new BlockPos(12, 2, 15));
			heard.clear(); // the appointment's own lines
			ChoiceMenu menu = desk(v.owner(), v.hall());
			menu.press(StewardDeskPage.REST, v.owner());
			helper.assertTrue(StewardDesk.mode(v.level(), v.hall()) == CityPlan.Mode.REST, "Rest not chosen from the desk");
			morning(v);
			morning(v);
			helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "proposed while resting: " + StewardDesk.of(v.level(), v.hall()).proposals());
			helper.assertTrue(job(dara) == VillagerProfession.NONE && v.entity().research().currentTopic() == null, "acted while resting");
			helper.assertTrue(heard.stream().noneMatch(s -> !s.overlay()), "chat while resting: "
				+ heard.stream().filter(s -> !s.overlay()).map(Seen::key).toList());
			ChoiceMenu again = desk(v.owner(), v.hall());
			helper.assertTrue(again.icon(StewardDeskPage.FIRST_PROPOSAL + 4).getHoverName().getString().equals(text("screen.aliveworkplace.desk.resting")),
				"the desk doesn't say he rests: " + again.icon(StewardDeskPage.FIRST_PROPOSAL + 4).getHoverName().getString());
		} finally {
			StewardDesk.SELF_RUN = before;
		}
		helper.succeed();
	}

	/** Proposals saved and loaded with the hall are still answered by their ids; the next id goes on counting. */
	//$ gametest_ticks_batch AREA '40' '"qaDeskReloadAnswer"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaDeskReloadAnswer")
	public void proposalsAnsweredAfterSaveAndReload(GameTestHelper helper) {
		Village v = village(helper, new ArrayList<>(), jobsWish(), researchWish());
		helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
		Villager dara = jobless(helper, new BlockPos(12, 2, 15));
		morning(v);
		StewardDesk.State saved = StewardDesk.of(v.level(), v.hall());
		StewardDesk.Proposal jobs = saved.proposals().stream().filter(StewardDesk.Proposal::isJobs).findFirst().orElse(null);
		StewardDesk.Proposal research = saved.proposals().stream().filter(p -> p.researchTopic().isPresent()).findFirst().orElse(null);
		helper.assertTrue(jobs != null && research != null, "setup: proposals " + saved.proposals());
		CompoundTag tag = v.entity().saveWithoutMetadata(v.level().registryAccess());
		v.entity().setStewardDesk(StewardDesk.State.EMPTY);
		v.entity().setResearch(Research.State.EMPTY);
		v.entity().loadWithComponents(tag, v.level().registryAccess());
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).next() == saved.next(), "the next id after reload: " + StewardDesk.of(v.level(), v.hall()).next());
		helper.assertTrue(StewardDesk.approve(v.level(), v.hall(), v.owner(), jobs.id()) == StewardDesk.Outcome.STARTED, "the jobs not approved after reload");
		helper.assertTrue(job(dara) == VillagerProfession.FARMER, "Dara is " + job(dara));
		helper.assertTrue(StewardDesk.decline(v.level(), v.hall(), v.owner(), research.id()), "the research not declined after reload");
		CompoundTag again = v.entity().saveWithoutMetadata(v.level().registryAccess());
		v.entity().setStewardDesk(StewardDesk.State.EMPTY);
		v.entity().loadWithComponents(again, v.level().registryAccess());
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().isEmpty(), "answered proposals back after reload");
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).declined().containsKey(RESEARCH_RULE.toString()), "the decline lost on reload");
		helper.succeed();
	}

	/** Declined jobs and research stay away for the next two mornings and come back on the third (Ask me first). */
	//$ gametest_ticks_batch AREA '40' '"qaJobsDeclineDays"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaJobsDeclineDays")
	public void declinedJobsAndResearchComeBackOnTheThirdMorning(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long time = level.getDayTime();
		try {
			long dawn = nextDawn(level);
			level.setDayTime(dawn + 1000);
			Village v = village(helper, new ArrayList<>(), jobsWish(), researchWish());
			helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
			Villager dara = jobless(helper, new BlockPos(12, 2, 15));
			morning(v);
			for (StewardDesk.Proposal p : StewardDesk.of(level, v.hall()).proposals()) {
				ChoiceMenu menu = desk(v.owner(), v.hall());
				menu.press(StewardDeskPage.FIRST_PROPOSAL, v.owner());
				helper.assertTrue(menu.icon(StewardDeskPage.DECLINE).is(Items.BARRIER), "no Decline on " + p.name().getString());
				menu.press(StewardDeskPage.DECLINE, v.owner());
			}
			helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().isEmpty(), "declined, still on the desk");
			for (int d = 1; d <= 2; d++) {
				level.setDayTime(dawn + d * DAY + 1000);
				v.wish(jobsWish(), researchWish());
				morning(v);
				helper.assertTrue(StewardDesk.of(level, v.hall()).proposals().isEmpty(), "back " + d + " days after the decline: "
					+ StewardDesk.of(level, v.hall()).proposals());
			}
			level.setDayTime(dawn + 3 * DAY + 1000);
			v.wish(jobsWish(), researchWish());
			morning(v);
			List<StewardDesk.Proposal> back = StewardDesk.of(level, v.hall()).proposals();
			helper.assertTrue(back.stream().anyMatch(StewardDesk.Proposal::isJobs) && back.stream().anyMatch(p -> p.researchTopic().isPresent()),
				"not back on the third morning: " + back);
			helper.assertTrue(job(dara) == VillagerProfession.NONE, "a job given without approval");
		} finally {
			level.setDayTime(time);
		}
		helper.succeed();
	}

	/** Approving jobs a villager no longer needs: he keeps the work he found, the desk says why, the proposal goes. */
	//$ gametest_ticks_batch AREA '40' '"qaJobsTaken"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaJobsTaken")
	public void approvingJobsAlreadyTakenChangesNobody(GameTestHelper helper) {
		List<Seen> heard = new ArrayList<>();
		Village v = village(helper, heard, jobsWish());
		helper.setBlock(new BlockPos(6, 2, 22), Blocks.COMPOSTER);
		BlockPos fletching = new BlockPos(22, 2, 8);
		helper.setBlock(fletching, Blocks.FLETCHING_TABLE);
		Villager dara = jobless(helper, new BlockPos(12, 2, 15));
		StewardDesk.plan(v.level(), v.steward(), v.hall());
		StewardDesk.Proposal p = StewardDesk.of(v.level(), v.hall()).proposals().stream().filter(StewardDesk.Proposal::isJobs).findFirst().orElse(null);
		helper.assertTrue(p != null && p.jobs().size() == 1, "setup: " + StewardDesk.of(v.level(), v.hall()).proposals());
		Stations.assign(v.level(), dara, helper.absolutePos(fletching), VillagerProfession.FLETCHER); // she found work herself
		ChoiceMenu menu = desk(v.owner(), v.hall());
		menu.press(StewardDeskPage.FIRST_PROPOSAL, v.owner());
		menu.press(StewardDeskPage.APPROVE, v.owner());
		helper.assertTrue(job(dara) == VillagerProfession.FLETCHER, "Dara is " + job(dara));
		helper.assertTrue(!lines(heard, "message.aliveworkplace.steward.desk.outcome.jobs_taken", true).isEmpty(), "not told why: " + keys(heard));
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).get(p.id()).isEmpty(), "the stale proposal stayed");
		helper.succeed();
	}

	/**
	 * Guards only while guards are short: the first jobless becomes the guard at a grindstone though a fletching table is
	 * nearer him; with one guard enough for the village, the second takes the nearest block (the fletching table), not
	 * the other grindstone.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaJobsGuardsShort"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaJobsGuardsShort")
	public void guardsOnlyWhileGuardsAreShort(GameTestHelper helper) {
		ServerLevel level = ground(helper);
		BlockPos table = new BlockPos(24, 2, 24);
		helper.setBlock(table, ModBlocks.BLUEPRINT_TABLE);
		Villager builder = jobless(helper, table.north());
		Builders.employ(level, builder, helper.absolutePos(table));
		BlockPos fletching = new BlockPos(12, 2, 13);
		BlockPos post = new BlockPos(12, 2, 20);
		BlockPos farPost = new BlockPos(19, 2, 24);
		helper.setBlock(fletching, Blocks.FLETCHING_TABLE);
		helper.setBlock(post, Blocks.GRINDSTONE);
		helper.setBlock(farPost, Blocks.GRINDSTONE);
		Villager first = jobless(helper, new BlockPos(12, 2, 15));
		Villager second = jobless(helper, new BlockPos(19, 2, 15));
		StewardJobs.Plan plan = StewardJobs.plan(level, helper.absolutePos(HALL));
		Map<UUID, StewardJobs.Job> by = new HashMap<>();
		plan.jobs().forEach(j -> by.put(j.villager(), j));
		StewardJobs.Job a = by.get(first.getUUID());
		StewardJobs.Job b = by.get(second.getUUID());
		helper.assertTrue(a != null && a.job().orElseThrow() == ModVillagers.GUARD && a.station().equals(helper.absolutePos(post)),
			"guards short: the first got " + a);
		helper.assertTrue(b != null && b.job().orElseThrow() == VillagerProfession.FLETCHER && b.station().equals(helper.absolutePos(fletching)),
			"guards enough: the second got " + b + " (plan " + plan + ")");
		helper.succeed();
	}

	/** Run the village: a baby a tick from growing up gets nothing, a grown villager beside him does, the Steward keeps his post. */
	//$ gametest_ticks_batch AREA '40' '"qaJobsBaby"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaJobsBaby")
	public void aBabyGetsNothingAGrownVillagerDoes(GameTestHelper helper) {
		boolean before = StewardDesk.SELF_RUN;
		try {
			StewardDesk.SELF_RUN = true;
			Village v = village(helper, new ArrayList<>(), jobsWish());
			helper.assertTrue(StewardDesk.setMode(v.level(), v.hall(), v.owner(), CityPlan.Mode.RUN), "Run refused");
			helper.setBlock(new BlockPos(12, 2, 12), Blocks.COMPOSTER);
			helper.setBlock(new BlockPos(18, 2, 12), Blocks.FLETCHING_TABLE);
			Villager baby = jobless(helper, new BlockPos(12, 2, 14));
			baby.setAge(-1);
			Villager grown = jobless(helper, new BlockPos(18, 2, 14));
			grown.setAge(0);
			StewardDesk.plan(v.level(), v.steward(), v.hall());
			helper.assertTrue(job(baby) == VillagerProfession.NONE, "the baby is " + job(baby));
			helper.assertTrue(job(grown) != VillagerProfession.NONE, "the grown villager got nothing");
			helper.assertTrue(job(v.steward()) == ModVillagers.STEWARD, "the Steward is now " + job(v.steward()));
		} finally {
			StewardDesk.SELF_RUN = before;
		}
		helper.succeed();
	}

	// ---- research (27.9) -----------------------------------------------------------------------------------------

	private static Research.State levels(Map<Research.Topic, Integer> levels) {
		Map<String, Integer> out = new HashMap<>();
		levels.forEach((t, n) -> out.put(t.key(), n));
		return new Research.State(out, Optional.empty(), 0, false);
	}

	private static Optional<Research.Topic> pick(Research.State state, StewardResearch.Village village) {
		return StewardResearch.pick(state, village, Optional.empty()).map(StewardResearch.Pick::topic);
	}

	/** The spec's order, written out: what the Steward should pick. A raid exactly 7 days ago is left out (the spec's edge). */
	private static Optional<Research.Topic> oracle(Research.State s, StewardResearch.Village v) {
		List<Research.Topic> order = new ArrayList<>();
		if (v.daysSinceRaid() >= 0 && v.daysSinceRaid() < 7) {
			order.add(Research.Topic.FORTIFICATION);
		}
		if (v.ill() >= 2) {
			order.add(Research.Topic.MEDICINE);
		}
		if (v.foodShort()) {
			order.add(Research.Topic.GREEN_THUMB);
		}
		if (v.storePercent() >= 80) {
			order.add(Research.Topic.LOGISTICS);
		}
		order.addAll(List.of(Research.Topic.SWIFT_HANDS, Research.Topic.HEARTH, Research.Topic.KINSHIP));
		return order.stream().filter(s::available).findFirst();
	}

	/** The topic's precedence and every edge: raid today and 6 days ago but not 8, 2 ill not 1, 80% not 79%, each only when available. */
	//$ gametest 'FabricGameTest.EMPTY_STRUCTURE'
	@GameTest(template = FabricGameTest.EMPTY_STRUCTURE)
	public void researchTopicEdgesAndPrecedence(GameTestHelper helper) {
		Research.State all = levels(Map.of(Research.Topic.DRILL, 1, Research.Topic.HEARTH, 1, Research.Topic.SWIFT_HANDS, 1));
		record Case(String what, Research.State state, StewardResearch.Village village, Research.Topic want) {
		}
		List<Case> cases = List.of(
			new Case("a raid today", all, new StewardResearch.Village(0, 0, false, 0), Research.Topic.FORTIFICATION),
			new Case("a raid 6 days ago", all, new StewardResearch.Village(6, 0, false, 0), Research.Topic.FORTIFICATION),
			new Case("a raid 8 days ago", all, new StewardResearch.Village(8, 0, false, 0), Research.Topic.SWIFT_HANDS),
			new Case("a raid beats everything", all, new StewardResearch.Village(3, 5, true, 100), Research.Topic.FORTIFICATION),
			new Case("no Drill: the raid passes to the ill", levels(Map.of(Research.Topic.HEARTH, 1, Research.Topic.SWIFT_HANDS, 1)),
				new StewardResearch.Village(3, 5, true, 100), Research.Topic.MEDICINE),
			new Case("2 ill beat food and store", all, new StewardResearch.Village(100, 2, true, 100), Research.Topic.MEDICINE),
			new Case("1 ill isn't a sickness", all, new StewardResearch.Village(100, 1, false, 0), Research.Topic.SWIFT_HANDS),
			new Case("food short beats a full store", all, new StewardResearch.Village(100, 0, true, 100), Research.Topic.GREEN_THUMB),
			new Case("no Hearth: Medicine and Green Thumb pass to Logistics", levels(Map.of(Research.Topic.DRILL, 1, Research.Topic.SWIFT_HANDS, 1)),
				new StewardResearch.Village(100, 5, true, 80), Research.Topic.LOGISTICS),
			new Case("store 80%", all, new StewardResearch.Village(100, 0, false, 80), Research.Topic.LOGISTICS),
			new Case("store 79%", all, new StewardResearch.Village(100, 0, false, 79), Research.Topic.SWIFT_HANDS),
			new Case("Swift Hands before Hearth", levels(Map.of(Research.Topic.SWIFT_HANDS, 2)), new StewardResearch.Village(100, 0, false, 0),
				Research.Topic.SWIFT_HANDS),
			new Case("Hearth until it's done", levels(Map.of(Research.Topic.SWIFT_HANDS, 3, Research.Topic.HEARTH, 1)),
				new StewardResearch.Village(100, 0, false, 0), Research.Topic.HEARTH));
		for (Case c : cases) {
			Optional<Research.Topic> got = pick(c.state(), c.village());
			helper.assertTrue(got.equals(Optional.of(c.want())), c.what() + ": picked " + got + ", wanted " + c.want());
		}
		// Against the spec's order on many villages and research states (a fixed seed): the same pick, never an unavailable topic.
		RandomSource random = RandomSource.create(2709L);
		Research.Topic[] topics = {Research.Topic.DRILL, Research.Topic.HEARTH, Research.Topic.SWIFT_HANDS, Research.Topic.KINSHIP,
			Research.Topic.FORTIFICATION, Research.Topic.MEDICINE, Research.Topic.GREEN_THUMB, Research.Topic.LOGISTICS};
		int checked = 0;
		for (int i = 0; i < 600; i++) {
			Map<Research.Topic, Integer> lv = new HashMap<>();
			for (Research.Topic t : topics) {
				lv.put(t, random.nextInt(t.maxLevel + 1));
			}
			Research.State state = levels(lv);
			StewardResearch.Village village = new StewardResearch.Village(random.nextInt(12), random.nextInt(4), random.nextBoolean(), 75 + random.nextInt(10));
			if (village.daysSinceRaid() == 7) {
				continue;
			}
			Optional<Research.Topic> got = pick(state, village);
			got.ifPresent(t -> helper.assertTrue(state.available(t), "picked " + t + ", not available, in " + state + " / " + village));
			helper.assertTrue(got.equals(oracle(state, village)), "picked " + got + ", the spec's order says " + oracle(state, village) + " in " + state + " / " + village);
			checked++;
		}
		helper.assertTrue(checked > 400, "only " + checked + " cases checked");
		helper.succeed();
	}

	/**
	 * The village as the Steward counts it from the world: a Storehouse chest 22 slots of 27 full is past 80% (Logistics,
	 * "the store is 81% full"), 21 isn't; a raid 6 days ago counts and 8 doesn't; one ill villager isn't a sickness, two are.
	 */
	//$ gametest_ticks_batch AREA '40' '"qaResearchCounted"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaResearchCounted")
	public void theVillageIsCountedFromTheWorld(GameTestHelper helper) {
		ServerLevel level = ground(helper);
		BlockPos hall = helper.absolutePos(HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(hall);
		BlockPos storehouse = new BlockPos(5, 2, 23);
		helper.setBlock(storehouse, ModBlocks.STOREHOUSE);
		helper.setBlock(storehouse.east(), Blocks.CHEST);
		Container chest = (Container) helper.getBlockEntity(storehouse.east());
		for (int i = 0; i < 22; i++) {
			chest.setItem(i, new ItemStack(Items.COBBLESTONE));
		}
		Research.State swift = levels(Map.of(Research.Topic.SWIFT_HANDS, 1)); // Logistics open; Medicine, Green Thumb, Fortification not
		StewardResearch.Village counted = StewardResearch.village(level, hall);
		helper.assertTrue(counted.storePercent() >= 80, "22 of 27 slots counted as " + counted.storePercent() + "%");
		Optional<StewardResearch.Pick> logistics = StewardResearch.pick(swift, counted, Optional.empty());
		helper.assertTrue(logistics.map(StewardResearch.Pick::topic).equals(Optional.of(Research.Topic.LOGISTICS)), "a store 81% full: " + logistics);
		String why = Words.countedFirst(logistics.get().why(), logistics.get().number()).getString();
		helper.assertTrue(why.equals("the store is 81% full"), "its why: " + why);
		chest.setItem(21, ItemStack.EMPTY);
		counted = StewardResearch.village(level, hall);
		helper.assertTrue(pick(swift, counted).equals(Optional.of(Research.Topic.SWIFT_HANDS)), "21 of 27 slots (77%) picked " + pick(swift, counted));

		Research.State drill = levels(Map.of(Research.Topic.DRILL, 1));
		entity.setLastRaidDay(Chronicle.day(level) - 6);
		helper.assertTrue(pick(drill, StewardResearch.village(level, hall)).equals(Optional.of(Research.Topic.FORTIFICATION)),
			"a raid 6 days ago: " + StewardResearch.village(level, hall));
		entity.setLastRaidDay(Chronicle.day(level) - 8);
		helper.assertTrue(!pick(drill, StewardResearch.village(level, hall)).equals(Optional.of(Research.Topic.FORTIFICATION)),
			"a raid 8 days ago still counted: " + StewardResearch.village(level, hall));

		Research.State hearth = levels(Map.of(Research.Topic.HEARTH, 1));
		Villager one = jobless(helper, new BlockPos(20, 2, 8));
		ModAttachments.ILL_SINCE.set(one, level.getGameTime());
		counted = StewardResearch.village(level, hall);
		helper.assertTrue(counted.ill() == 1 && !pick(hearth, counted).equals(Optional.of(Research.Topic.MEDICINE)), "one ill: " + counted + " picked " + pick(hearth, counted));
		Villager two = jobless(helper, new BlockPos(21, 2, 8));
		ModAttachments.ILL_SINCE.set(two, level.getGameTime());
		counted = StewardResearch.village(level, hall);
		helper.assertTrue(counted.ill() == 2 && pick(hearth, counted).equals(Optional.of(Research.Topic.MEDICINE)), "two ill: " + counted + " picked " + pick(hearth, counted));
		helper.succeed();
	}

	/** A research proposal approved after the scholars took up a topic meanwhile: refused (the desk says why), their topic kept. */
	//$ gametest_ticks_batch AREA '40' '"qaResearchBusy"'
	@GameTest(template = AREA, timeoutTicks = 40, batch = "qaResearchBusy")
	public void researchApprovedWhileBusyKeepsTheirTopic(GameTestHelper helper) {
		List<Seen> heard = new ArrayList<>();
		Village v = village(helper, heard, researchWish());
		ScholarWork.IDLE.idle(v.level(), v.steward(), v.hall());
		StewardDesk.Proposal p = StewardDesk.of(v.level(), v.hall()).proposals().stream().filter(x -> x.researchTopic().isPresent()).findFirst().orElse(null);
		helper.assertTrue(p != null, "setup: no research proposal");
		v.entity().setResearch(Research.State.EMPTY.choose(Research.Topic.HEARTH)); // chosen by the owner at the lectern
		ChoiceMenu menu = desk(v.owner(), v.hall());
		menu.press(StewardDeskPage.FIRST_PROPOSAL, v.owner());
		menu.press(StewardDeskPage.APPROVE, v.owner());
		helper.assertTrue(v.entity().research().currentTopic() == Research.Topic.HEARTH, "their topic replaced by " + v.entity().research().currentTopic());
		helper.assertTrue(!lines(heard, "message.aliveworkplace.steward.desk.outcome.researching", true).isEmpty(), "not told why: " + keys(heard));
		ScholarWork.IDLE.idle(v.level(), v.steward(), v.hall());
		helper.assertTrue(StewardDesk.of(v.level(), v.hall()).proposals().stream().filter(x -> x.researchTopic().isPresent()).count() <= 1,
			"a second research proposal while busy");
		helper.succeed();
	}
}

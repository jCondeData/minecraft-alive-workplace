package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintItem;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.CivicEffects;
import io.github.jcondedata.aliveworkplace.hall.Edicts;
import io.github.jcondedata.aliveworkplace.hall.Festivals;
import io.github.jcondedata.aliveworkplace.hall.Reforms;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.story.ArcEffects;
import io.github.jcondedata.aliveworkplace.story.Arcs;
import io.github.jcondedata.aliveworkplace.story.Objectives;
import io.github.jcondedata.aliveworkplace.story.Quest;
import io.github.jcondedata.aliveworkplace.story.ReformStories;
import io.github.jcondedata.aliveworkplace.story.Stories;
import io.github.jcondedata.aliveworkplace.work.Money;
import java.util.List;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * The eight reforms as stories (ROADMAP 31.22), each through the player's own entry points: the edict in force, the
 * fitting villager asks (a right-click), M30's three steps handed in (or monsters slain) a morning apart, the ending,
 * and the edict reformed (its boost kept, its cost gone) in that village only, while a second village with the same
 * edict keeps its cost. Reform arcs are side arcs: they start with {@code arcsAtOnce} at 0 and inside the cooldown.
 * With {@code storyArcs} off, M30's own step goes up on the quest page instead.
 */
public class ReformStoryGameTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:huge_area";
	private static final BlockPos HALL = new BlockPos(7, 2, 7);
	private static final BlockPos OTHER = new BlockPos(25, 2, 25);
	private static final long DAY = VillageNeeds.DAY;

	private record Village(ServerLevel level, BlockPos hall, VillageHallBlockEntity entity, VillageHallBlockEntity other, ServerPlayer player, long day) {
	}

	/** Two halls on grass, story arcs on with no room for a main arc, a player at the first; reset when the test ends. */
	private static Village village(GameTestHelper helper, boolean arcs) {
		Leftovers.clear(helper);
		Leftovers.players(helper);
		ServerLevel level = helper.getLevel();
		int radius = VillageHalls.RADIUS;
		boolean enabled = Arcs.ENABLED;
		int atOnce = Arcs.AT_ONCE;
		boolean edicts = Edicts.ENABLED;
		VillageHalls.RADIUS = 10;
		Arcs.ENABLED = arcs;
		Arcs.AT_ONCE = 0; // side arcs don't count against arcsAtOnce
		Edicts.ENABLED = true;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Arcs.ENABLED = enabled;
			Arcs.AT_ONCE = atOnce;
			Edicts.ENABLED = edicts;
			CivicEffects.forget();
			Moods.forget();
		});
		for (int x = 0; x < 30; x++) {
			for (int z = 0; z < 30; z++) {
				helper.setBlock(new BlockPos(x, 1, z), Blocks.GRASS_BLOCK);
			}
		}
		VillageHallBlockEntity entity = hall(helper, HALL);
		VillageHallBlockEntity other = hall(helper, OTHER);
		long day = Chronicle.day(level) + 1;
		setDay(level, day);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(HALL.south(2));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		Leftovers.after(helper, () -> level.getServer().getPlayerList().remove(player));
		return new Village(level, helper.absolutePos(HALL), entity, other, player, day);
	}

	private static VillageHallBlockEntity hall(GameTestHelper helper, BlockPos at) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(at);
		Leftovers.after(helper, () -> Arcs.stopAll(level, abs));
		helper.setBlock(at, ModBlocks.VILLAGE_HALL);
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(abs);
		Arcs.stopAll(level, abs);
		Stories.Data.get(level).remove(abs);
		entity.setLastQuestDay(Long.MAX_VALUE / 2);
		entity.setRank(VillageRanks.Rank.HAMLET);
		entity.setEdicts(List.of());
		entity.setReforms(List.of());
		entity.setQuests(List.of());
		return entity;
	}

	private static void setDay(ServerLevel level, long day) {
		level.setDayTime((day - 1) * DAY + 1000);
	}

	/** A grown villager of {@code job} who keeps it (an Apprentice, standing still). */
	private static Villager villager(GameTestHelper helper, BlockPos at, VillagerProfession job) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(2));
		v.setVillagerXp(10);
		return v;
	}

	private static List<Quest> arcQuests(Village v) {
		return Stories.open(v.level, v.hall).stream().filter(q -> q.arc != null).toList();
	}

	private static List<String> chronicle(VillageHallBlockEntity entity) {
		return entity.chronicle().stream().map(e -> e.text().getString()).toList();
	}

	/**
	 * The story of the reform of {@code edict} from the ask to the end: proclaimed in both villages, {@code asker} is
	 * asked to talk, the three steps are done (items handed in from the player's pack, or monsters slain round the
	 * village), and the edict is reformed here only. Returns the emeralds paid.
	 */
	private static int story(GameTestHelper helper, Village v, String edict, Villager asker, List<Item> items) {
		ResourceLocation id = AliveWorkplace.id(edict);
		Edicts.Edict e = Edicts.get(id).orElseThrow();
		Reforms.Reform reform = e.reform().orElseThrow();
		ResourceLocation arcId = ResourceLocation.parse(reform.arc().orElseThrow());
		v.entity.setEdicts(List.of(new Edicts.InForce(id.toString(), v.day)));
		v.other.setEdicts(List.of(new Edicts.InForce(id.toString(), v.day)));
		Reforms.round(v.level, v.hall, v.entity);
		helper.assertTrue(ReformStories.runs(v.level, v.hall, arcId) && Arcs.running(v.level, v.hall) == null, edict + ": the side arc didn't start");
		helper.assertTrue(Reforms.shown(v.entity).isEmpty(), edict + ": M30's step went up beside the story");
		helper.assertTrue(asker.getTags().contains(ArcEffects.roleTag("asker")), edict + ": " + asker.getVillagerData().getProfession() + " wasn't asked to ask");
		List<Quest> quests = arcQuests(v);
		helper.assertTrue(quests.size() == 1 && quests.get(0).objectives.get(0) instanceof Objectives.Talk, edict + ": the ask: " + quests);
		helper.assertTrue(ArcEffects.onTalk(v.player, v.level, InteractionHand.MAIN_HAND, asker) == InteractionResult.SUCCESS, edict + ": the talk didn't count");
		helper.assertTrue(!Arcs.chatter(v.level, v.hall).isEmpty(), edict + ": no chatter");
		int emeralds = v.player.getInventory().countItem(Items.EMERALD);
		Zombie monster = helper.spawn(EntityType.ZOMBIE, HALL.east(3));
		monster.setNoAi(true);
		for (int i = 0; i < 3; i++) {
			Reforms.Step step = reform.steps().get(i).resolve(false);
			quests = arcQuests(v);
			helper.assertTrue(!quests.isEmpty(), edict + ": step " + (i + 1) + " isn't up");
			Quest q = quests.stream().filter(x -> x.objectives.get(0) instanceof Objectives.Kill || x.objectives.get(0) instanceof Objectives.Bring)
				.findFirst().orElse(null);
			helper.assertTrue(q != null, edict + ": step " + (i + 1) + ": " + quests.stream().map(x -> x.objectives.toString()).toList());
			if (step.kind() == io.github.jcondedata.aliveworkplace.hall.VillageQuests.Kind.BRING) {
				Objectives.Bring bring = (Objectives.Bring) q.objectives.get(0);
				helper.assertTrue(bring.item().equals(step.item()) && bring.count() == step.count(), edict + ": step " + (i + 1) + " asks " + bring + ", not M30's");
				helper.assertTrue(BuiltIn.id(items.get(i)).equals(step.item()), edict + ": the test brings the wrong item");
				for (int n = step.count(); n > 0; n -= 64) {
					v.player.getInventory().add(new ItemStack(items.get(i), Math.min(64, n)));
				}
				helper.assertTrue(Stories.handIn(v.player, v.hall, q.id) == step.count(), edict + ": the hand-in");
			} else {
				Objectives.Kill kill = (Objectives.Kill) q.objectives.get(0);
				helper.assertTrue(kill.count() == step.count(), edict + ": step " + (i + 1) + " asks " + kill.count() + ", not M30's " + step.count());
				for (int n = 0; n < step.count(); n++) {
					Stories.onKill(v.level, monster, v.player);
				}
			}
			if (i < 2) {
				helper.assertTrue(!Reforms.reformed(v.entity, id.toString()), edict + ": reformed too soon");
				Arcs.round(v.level, v.hall, v.entity);
				helper.assertTrue(arcQuests(v).isEmpty(), edict + ": the next step went up the same day");
				setDay(v.level, v.day + i + 1);
				Arcs.round(v.level, v.hall, v.entity);
			}
		}
		monster.discard();
		helper.assertTrue(!ReformStories.runs(v.level, v.hall, arcId), edict + ": the story didn't end");
		helper.assertTrue(Reforms.reformed(v.entity, id.toString()) && !Reforms.reformed(v.other, id.toString()), edict + ": reformed here only? "
			+ v.entity.reforms() + " / " + v.other.reforms());
		CivicEffects.forget();
		List<CivicEffects.Effect> here = CivicEffects.of(v.entity).all().stream().map(CivicEffects.Active::effect).toList();
		List<CivicEffects.Effect> there = CivicEffects.of(v.other).all().stream().map(CivicEffects.Active::effect).toList();
		helper.assertTrue(here.containsAll(e.boost()) && e.cost().stream().noneMatch(c -> here.contains(c) && !reform.effects().contains(c))
			&& here.containsAll(reform.effects()), edict + ": boost kept and cost gone? " + here);
		helper.assertTrue(there.containsAll(e.boost()) && there.containsAll(e.cost()), edict + ": the other village lost its cost: " + there);
		List<String> told = chronicle(v.entity);
		String name = reform.name().getString();
		helper.assertTrue(told.contains("The edict " + e.name().getString() + " was reformed: " + name)
			&& told.contains(Component.translatable("chronicle.aliveworkplace.reform_story." + arcId.getPath()).getString())
			&& told.stream().anyMatch(t -> t.startsWith(name + ", chapter 4")), edict + ": the chronicle: " + told);
		return v.player.getInventory().countItem(Items.EMERALD) - emeralds;
	}

	private static final class BuiltIn {
		static String id(Item item) {
			return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
		}
	}

	/** The Shift Bell: the Toolsmith asks; a bell on a post beside the hall rings at the start and the end of the work day. */
	//$ gametest_ticks_batch AREA '80' '"reformStoryShiftBell"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reformStoryShiftBell")
	public void theShiftBell(GameTestHelper helper) {
		Village v = village(helper, true);
		Villager smith = villager(helper, HALL.east(2), VillagerProfession.TOOLSMITH);
		int paid = story(helper, v, "long_shifts", smith, List.of(Items.CLOCK, Items.GOLD_INGOT, Items.BREAD));
		if (!Money.cobbleDollars()) {
			helper.assertTrue(paid == 14, "5 + 5 + 4: " + paid);
		}
		long at = ReformStories.mark(v.level, v.hall, ReformStories.BELL).orElse(Long.MIN_VALUE);
		helper.assertTrue(at != Long.MIN_VALUE, "no bell was put up");
		BlockPos bell = BlockPos.of(at);
		helper.assertTrue(v.level.getBlockState(bell).is(Blocks.BELL) && bell.distManhattan(v.hall) <= 24, "the bell: " + v.level.getBlockState(bell) + " at " + bell);
		helper.assertTrue(v.level.getBlockState(bell.above()).is(Blocks.STRIPPED_SPRUCE_LOG), "the bell hangs from no beam");
		int rung = ReformStories.RUNG.get();
		ReformStories.run(v.level, v.hall, ReformStories.WORK_START);
		ReformStories.run(v.level, v.hall, ReformStories.WORK_START + 200);
		helper.assertTrue(ReformStories.RUNG.get() == rung + 1, "the morning bell: " + (ReformStories.RUNG.get() - rung));
		ReformStories.run(v.level, v.hall, 5000);
		helper.assertTrue(ReformStories.RUNG.get() == rung + 1, "a bell at midday");
		ReformStories.run(v.level, v.hall, ReformStories.WORK_END);
		helper.assertTrue(ReformStories.RUNG.get() == rung + 2, "the evening bell");
		// Broken, it rings no more.
		v.level.setBlockAndUpdate(bell, Blocks.AIR.defaultBlockState());
		setDay(v.level, v.day + 3);
		ReformStories.run(v.level, v.hall, ReformStories.WORK_START);
		helper.assertTrue(ReformStories.RUNG.get() == rung + 2, "a broken bell rang");
		helper.succeed();
	}

	/** The Common Granary: a Farmer asks; the next evening a harvest supper at the hall, without fireworks. */
	//$ gametest_ticks_batch AREA '80' '"reformStoryGranary"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reformStoryGranary")
	public void theCommonGranary(GameTestHelper helper) {
		Village v = village(helper, true);
		Villager farmer = villager(helper, HALL.east(2), VillagerProfession.FARMER);
		story(helper, v, "free_bread", farmer, List.of(Items.WHEAT, Items.HAY_BLOCK, Items.BARREL));
		long today = Chronicle.day(v.level);
		helper.assertTrue(ReformStories.mark(v.level, v.hall, ReformStories.SUPPER).orElse(-1L) == today + 1, "no supper set for tomorrow");
		ReformStories.run(v.level, v.hall, ReformStories.SUPPER_AT);
		helper.assertTrue(chronicle(v.entity).stream().noneMatch(t -> t.startsWith("The harvest supper")), "the supper came a day early");
		setDay(v.level, today + 1);
		int rockets = v.level.getEntitiesOfClass(net.minecraft.world.entity.projectile.FireworkRocketEntity.class,
			new net.minecraft.world.phys.AABB(v.hall).inflate(24)).size(); // (the reform's own, over the hall)
		ReformStories.run(v.level, v.hall, ReformStories.SUPPER_AT);
		helper.assertTrue(chronicle(v.entity).stream().anyMatch(t -> t.startsWith("The harvest supper: ")), "no supper: " + chronicle(v.entity));
		helper.assertTrue(Long.valueOf(today + 1).equals(ModAttachments.FESTIVAL_DAY.get(farmer)), "the farmer wasn't at the supper");
		helper.assertTrue(v.level.getEntitiesOfClass(net.minecraft.world.entity.projectile.FireworkRocketEntity.class,
			new net.minecraft.world.phys.AABB(v.hall).inflate(24)).size() == rockets, "fireworks at the supper");
		helper.assertTrue(ReformStories.mark(v.level, v.hall, ReformStories.SUPPER).isEmpty(), "the supper is held twice");
		helper.succeed();
	}

	/** The Midwives: the Nurse asks; a midwife comes and becomes a Nurse at Journeyman. */
	//$ gametest_ticks_batch AREA '80' '"reformStoryMidwives"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reformStoryMidwives")
	public void theMidwives(GameTestHelper helper) {
		Village v = village(helper, true);
		Villager nurse = villager(helper, HALL.east(2), ModVillagers.NURSE);
		story(helper, v, "large_families", nurse, List.of(Items.HONEY_BOTTLE, Items.WHITE_WOOL, Items.GOLDEN_CARROT));
		List<Villager> midwives = v.level.getEntitiesOfClass(Villager.class, VillageHalls.area(v.hall), x -> x.getTags().contains(ReformStories.NEWCOMER));
		helper.assertTrue(midwives.size() == 1 && midwives.get(0).getVillagerData().getProfession() == ModVillagers.NURSE
			&& midwives.get(0).getVillagerData().getLevel() == 3 && midwives.get(0).isPersistenceRequired(), "the midwife: " + midwives);
		midwives.forEach(Villager::discard);
		helper.succeed();
	}

	/** The Watchful Gate: a Guard asks; an old soldier comes, a Guard at Expert, in iron. */
	//$ gametest_ticks_batch AREA '80' '"reformStoryGate"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reformStoryGate")
	public void theWatchfulGate(GameTestHelper helper) {
		Village v = village(helper, true);
		Villager guard = villager(helper, HALL.east(2), ModVillagers.GUARD);
		story(helper, v, "open_gates", guard, List.of(Items.IRON_INGOT, Items.ARROW, Items.AIR));
		List<Villager> soldiers = v.level.getEntitiesOfClass(Villager.class, VillageHalls.area(v.hall), x -> x.getTags().contains(ReformStories.NEWCOMER));
		helper.assertTrue(soldiers.size() == 1, "the old soldier: " + soldiers);
		Villager s = soldiers.get(0);
		helper.assertTrue(s.getVillagerData().getProfession() == ModVillagers.GUARD && s.getVillagerData().getLevel() == 4
			&& s.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE) && s.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET),
			"not an Expert Guard in iron: " + s.getVillagerData() + " " + s.getItemBySlot(EquipmentSlot.CHEST));
		s.discard();
		helper.succeed();
	}

	/** The Festival Fund: the Bard asks; the next festival is a grand one: twice the fireworks, the good mood a day longer. */
	//$ gametest_ticks_batch AREA '80' '"reformStoryFestival"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reformStoryFestival")
	public void theFestivalFund(GameTestHelper helper) {
		Village v = village(helper, true);
		Villager bard = villager(helper, HALL.east(2), ModVillagers.BARD);
		story(helper, v, "festival_season", bard, List.of(Items.CAKE, Items.FIREWORK_ROCKET, Items.NOTE_BLOCK));
		helper.assertTrue(ReformStories.mark(v.level, v.hall, ReformStories.GRAND).isPresent(), "no grand festival waiting");
		long today = Chronicle.day(v.level);
		Festivals.feast(v.level, v.hall);
		helper.assertTrue(ReformStories.grand(v.level, v.hall) && ReformStories.mark(v.level, v.hall, ReformStories.GRAND).isEmpty(), "this festival isn't the grand one");
		helper.assertTrue(chronicle(v.entity).stream().anyMatch(t -> t.startsWith("The grand festival of ")), "the chronicle: " + chronicle(v.entity));
		helper.assertTrue(Long.valueOf(today).equals(ModAttachments.FESTIVAL_DAY.get(bard)), "the bard wasn't at the feast");
		CivicEffects.forget();
		setDay(v.level, today + Festivals.MOOD_DAYS + 1);
		helper.assertTrue(Festivals.enjoyedLately(v.level, bard), "the good mood didn't last a day longer");
		setDay(v.level, today + Festivals.MOOD_DAYS + 2);
		helper.assertTrue(!Festivals.enjoyedLately(v.level, bard), "the good mood lasted two days longer");
		helper.succeed();
	}

	/** The Fair Ledger: a Librarian asks; the battle falls back to slaying; a written book of the takings for the hall's owner. */
	//$ gametest_ticks_batch AREA '80' '"reformStoryLedger"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reformStoryLedger")
	public void theFairLedger(GameTestHelper helper) {
		Village v = village(helper, true);
		v.entity.setOwner(v.player.getUUID(), v.player.getGameProfile().getName());
		Villager clerk = villager(helper, HALL.east(2), VillagerProfession.LIBRARIAN);
		v.entity.addTreasuryTotal(1250);
		story(helper, v, "tithe", clerk, List.of(Items.WRITABLE_BOOK, Items.GOLD_INGOT, Items.AIR));
		ItemStack book = ItemStack.EMPTY;
		for (int i = 0; i < v.player.getInventory().getContainerSize(); i++) {
			if (v.player.getInventory().getItem(i).is(Items.WRITTEN_BOOK)) {
				book = v.player.getInventory().getItem(i);
			}
		}
		WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
		helper.assertTrue(content != null && content.title().raw().startsWith("The Fair Ledger of "), "no Fair Ledger: " + book);
		String days = content.pages().get(1).raw().getString();
		helper.assertTrue(days.contains("Day " + v.day + ": 12.50 emeralds") && days.split("\n").length == 1 + ReformStories.LEDGER_DAYS, "the takings: " + days);
		helper.succeed();
	}

	/** The Lamplighters: a Porter asks; two Street Lamp blueprints for whoever finished it, and the first lamplit night. */
	//$ gametest_ticks_batch AREA '80' '"reformStoryLamps"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reformStoryLamps")
	public void theLamplighters(GameTestHelper helper) {
		Village v = village(helper, true);
		Villager porter = villager(helper, HALL.east(2), ModVillagers.PORTER);
		story(helper, v, "curfew", porter, List.of(Items.LANTERN, Items.GLOWSTONE, Items.AIR));
		int lamps = 0;
		for (int i = 0; i < v.player.getInventory().getContainerSize(); i++) {
			ItemStack s = v.player.getInventory().getItem(i);
			if (BlueprintItem.data(s).map(d -> d.structure().equals(StarterBlueprints.STREET_LAMP.id())).orElse(false)) {
				lamps += s.getCount();
			}
		}
		helper.assertTrue(lamps == 2, "Street Lamp blueprints: " + lamps);
		ReformStories.run(v.level, v.hall, 6000);
		helper.assertTrue(chronicle(v.entity).stream().noneMatch(t -> t.startsWith("The first lamplit night")), "a lamplit night at noon");
		ReformStories.run(v.level, v.hall, ReformStories.NIGHT);
		helper.assertTrue(chronicle(v.entity).stream().filter(t -> t.startsWith("The first lamplit night in ")).count() == 1, "the chronicle: " + chronicle(v.entity));
		ReformStories.run(v.level, v.hall, ReformStories.NIGHT + 100);
		helper.assertTrue(chronicle(v.entity).stream().filter(t -> t.startsWith("The first lamplit night in ")).count() == 1, "two first nights");
		helper.succeed();
	}

	/** The Militia Drill: a Guard asks; at noon the next day the grown villagers spar at the Training Dummy, then the chronicle. */
	//$ gametest_ticks_batch AREA '80' '"reformStoryDrill"'
	@GameTest(template = AREA, timeoutTicks = 80, batch = "reformStoryDrill")
	public void theMilitiaDrill(GameTestHelper helper) {
		Village v = village(helper, true);
		Villager guard = villager(helper, HALL.east(2), ModVillagers.GUARD);
		story(helper, v, "conscription", guard, List.of(Items.IRON_SWORD, Items.SHIELD, Items.AIR));
		long today = Chronicle.day(v.level);
		helper.assertTrue(ReformStories.mark(v.level, v.hall, ReformStories.DRILL).orElse(-1L) == today + 1, "no drill set for tomorrow");
		helper.setBlock(HALL.west(3), ModBlocks.TRAINING_DUMMY);
		Villager farmer = villager(helper, HALL.west(3).north(), VillagerProfession.FARMER);
		guard.teleportTo(guard.getX() - 4, guard.getY(), guard.getZ() + 1); // beside the dummy
		setDay(v.level, today + 1);
		ReformStories.run(v.level, v.hall, ReformStories.DRILL_FROM - 100);
		helper.assertTrue(ReformStories.mark(v.level, v.hall, ReformStories.DRILL).isPresent(), "the drill began before noon");
		ReformStories.run(v.level, v.hall, ReformStories.DRILL_FROM);
		ReformStories.run(v.level, v.hall, ReformStories.DRILL_FROM + 20);
		ReformStories.run(v.level, v.hall, ReformStories.DRILL_UNTIL);
		List<String> told = chronicle(v.entity);
		helper.assertTrue(told.stream().anyMatch(t -> t.startsWith("Drill day: ") && t.contains("Training Dummies (1 of them)") && !t.contains(" 0 blows")),
			"the drill: " + told);
		helper.assertTrue(ReformStories.mark(v.level, v.hall, ReformStories.DRILL).isEmpty(), "the drill goes on");
		farmer.discard();
		helper.succeed();
	}

	/** storyArcs off: proclaimed with a Toolsmith there, M30's own step goes up on the quest page and no story starts. */
	//$ gametest_ticks_batch AREA '60' '"reformStoryOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reformStoryOff")
	public void withStoryArcsOffTheStepsWorkAsBefore(GameTestHelper helper) {
		Village v = village(helper, false);
		Villager smith = villager(helper, HALL.east(2), VillagerProfession.TOOLSMITH);
		v.entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("long_shifts").toString(), v.day)));
		Reforms.round(v.level, v.hall, v.entity);
		helper.assertTrue(!ReformStories.runs(v.level, v.hall, AliveWorkplace.id("shift_bell")) && arcQuests(v).isEmpty(), "a story started with storyArcs off");
		helper.assertTrue(Reforms.shown(v.entity).size() == 1 && Reforms.shown(v.entity).get(0).item().equals("minecraft:clock"), "M30's step: " + Reforms.shown(v.entity));
		helper.assertTrue(!smith.getTags().contains(ArcEffects.roleTag("asker")), "the smith was cast");
		// Turned on with a plain step up, the village finishes the plain steps.
		Arcs.ENABLED = true;
		Reforms.round(v.level, v.hall, v.entity);
		helper.assertTrue(!ReformStories.runs(v.level, v.hall, AliveWorkplace.id("shift_bell")) && Reforms.shown(v.entity).size() == 1, "the story took over a step begun");
		helper.succeed();
	}

	/** No Toolsmith nor any smith: nobody asks, so the plain steps go up (the bell needs a smith). */
	//$ gametest_ticks_batch AREA '60' '"reformStoryNobody"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "reformStoryNobody")
	public void withNobodyToAskThePlainStepsGoUp(GameTestHelper helper) {
		Village v = village(helper, true);
		villager(helper, HALL.east(2), VillagerProfession.FARMER);
		v.entity.setEdicts(List.of(new Edicts.InForce(AliveWorkplace.id("long_shifts").toString(), v.day)));
		Reforms.round(v.level, v.hall, v.entity);
		helper.assertTrue(!ReformStories.runs(v.level, v.hall, AliveWorkplace.id("shift_bell")) && Reforms.shown(v.entity).size() == 1, "with no smith: "
			+ Reforms.shown(v.entity));
		helper.succeed();
	}
}

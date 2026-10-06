package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.inn.Innkeepers;
import io.github.jcondedata.aliveworkplace.inn.Traveller;
import io.github.jcondedata.aliveworkplace.people.ClassJobs;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.Stations;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

/**
 * Higher jobs need higher classes (ROADMAP 34.8): each way of taking a job (its item, the hall's free-workstation list, a
 * grown child taking up a parent's trade, a hired traveller who arrives with the class of their level), vanilla jobs never
 * gated, nobody fired (a worker below their job's class keeps it across a reload, marked at the hall), and nothing gated
 * outside a village with a hall or with {@code villageClasses} off.
 */
public class ClassJobGameTests implements net.fabricmc.fabric.api.gametest.v1.FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(14, 2, 14);
	private static final BlockPos LECTERN = new BlockPos(4, 2, 4);
	private static final BlockPos COUNTER = new BlockPos(8, 2, 4);
	private static final BlockPos SECOND = new BlockPos(4, 2, 12);

	/** Our shipped class files, classes on, a hall at {@link #HALL} when {@code hall}; everything put back afterwards. */
	private static void setUp(GameTestHelper helper, boolean hall) {
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
		if (hall) {
			helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
		}
	}

	private static Villager villager(GameTestHelper helper, BlockPos at, String name, String cls) {
		Villager v = helper.spawn(EntityType.VILLAGER, at);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		if (cls != null) {
			SocialClasses.seed(v, SocialClasses.get(AliveWorkplace.id(cls)));
		}
		return v;
	}

	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? "" : key(c.getSiblings().get(0));
	}

	/** A player who keeps every chat and action-bar line they're shown. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "class-listener"), false);
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

	private static String job(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().getProfession()).toString();
	}

	private static List<String> lore(ItemStack icon) {
		List<String> out = new ArrayList<>();
		ItemLore lore = icon.get(DataComponents.LORE);
		if (lore != null) {
			lore.lines().forEach(l -> out.add(key(l)));
		}
		return out;
	}

	/**
	 * Picking a job with its item: a Peasant handed paper by a lectern is refused in so many words ("Dara is Peasant
	 * class; Scholar needs Burgher or better") and stays jobless; a Burgher takes it; an Artisan is refused too; a vanilla
	 * job (lapis: Librarian) is open to the Peasant.
	 */
	//$ gametest_ticks_batch AREA '60' '"classJobItem"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "classJobItem")
	public void theItemPicksAJobOnlyForTheRightClass(GameTestHelper helper) {
		setUp(helper, true);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.setBlock(SECOND, Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, seen);
			BlockPos by = LECTERN.south();
			Villager dara = villager(helper, by, "Dara", "peasant");
			InteractionResult r = Stations.choose(player, dara, new ItemStack(Items.PAPER));
			helper.assertTrue(r.consumesAction(), "the paper did nothing: " + r);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE, "the Peasant became a " + job(dara));
			Component said = seen.stream().filter(c -> key(c).equals("message.aliveworkplace.job.class_needed")).findFirst().orElse(null);
			helper.assertTrue(said != null, "not told why: " + seen.stream().map(ClassJobGameTests::key).toList());
			Object[] args = ((TranslatableContents) said.getContents()).getArgs();
			helper.assertTrue(args.length == 4 && ((Component) args[0]).getString().equals("Dara") && key((Component) args[1]).equals("class.aliveworkplace.peasant")
				&& key((Component) args[3]).equals("class.aliveworkplace.burgher"), "the refusal names the wrong people: " + said);

			Villager ann = villager(helper, by.east(), "Ann", "artisan");
			Stations.choose(player, ann, new ItemStack(Items.PAPER));
			helper.assertTrue(ann.getVillagerData().getProfession() == VillagerProfession.NONE, "the Artisan became a " + job(ann));
			ann.discard();

			// A vanilla job is never gated.
			Stations.choose(player, dara, new ItemStack(Items.LAPIS_LAZULI));
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN, "the Peasant isn't a Librarian: " + job(dara));
			// A Burgher by a second lectern takes it.
			Villager bram = villager(helper, SECOND.south(), "Bram", "burgher");
			Stations.choose(player, bram, new ItemStack(Items.PAPER));
			helper.assertTrue(bram.getVillagerData().getProfession() == ModVillagers.SCHOLAR, "the Burgher isn't a Scholar: " + job(bram));
			helper.succeed();
		});
	}

	/** Outside a village with a hall nothing is gated: a Peasant takes the Scholar's paper. */
	//$ gametest_ticks_batch AREA '60' '"classJobNoHall"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "classJobNoHall")
	public void noHallNoGate(GameTestHelper helper) {
		setUp(helper, false);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			Villager dara = villager(helper, LECTERN.south(), "Dara", "peasant");
			Stations.choose(helper.makeMockServerPlayerInLevel(), dara, new ItemStack(Items.PAPER));
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.SCHOLAR, "gated without a hall: " + job(dara));
			helper.succeed();
		});
	}

	/** With {@code villageClasses} off nothing is gated, in a village with a hall too. */
	//$ gametest_ticks_batch AREA '60' '"classJobOff"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "classJobOff")
	public void classesOffNoGate(GameTestHelper helper) {
		setUp(helper, true);
		SocialClasses.ENABLED = false;
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			Villager dara = villager(helper, LECTERN.south(), "Dara", "peasant");
			Stations.choose(helper.makeMockServerPlayerInLevel(), dara, new ItemStack(Items.PAPER));
			helper.assertTrue(dara.getVillagerData().getProfession() == ModVillagers.SCHOLAR, "gated with classes off: " + job(dara));
			helper.assertTrue(ClassJobs.needed(ModVillagers.SCHOLAR) == null, "a job needs a class with classes off");
			Villager tom = villager(helper, COUNTER.south(), "Tom", "peasant");
			helper.assertTrue(ClassJobs.may(helper.absolutePos(HALL), tom, ModVillagers.SHOPKEEPER), "the hall gates with classes off");
			helper.assertTrue(ClassJobs.above(dara) == null, "marked with classes off");
			// A hired Expert gets no class with classes off.
			Villager guest = villager(helper, new BlockPos(10, 2, 10), "Guest", null);
			ClassJobs.hired(helper.getLevel(), guest, 4);
			helper.assertTrue(!ModAttachments.SOCIAL_CLASS.has(guest), "a class with classes off");
			helper.succeed();
		});
	}

	/**
	 * The hall's free-workstation list: for a Peasant the Shop Counter's job is greyed with the class it needs, and a click
	 * gives nothing; for an Artisan the same entry gives the job.
	 */
	//$ gametest_ticks_batch AREA '60' '"classJobHall"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "classJobHall")
	public void theHallsListGreysJobsAboveTheirClass(GameTestHelper helper) {
		setUp(helper, true);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			List<VillageHalls.FreeStation> free = VillageHalls.freeStations(level, hall);
			helper.assertTrue(free.size() == 1 && ClassJobs.needed(free.get(0).profession()) == SocialClasses.get(AliveWorkplace.id("artisan")),
				"the counter's free job isn't an Artisan's: " + free);
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, seen);
			Villager dara = villager(helper, new BlockPos(4, 2, 10), "Dara", "peasant");
			ChoiceMenu menu = ChoiceMenu.detached(player, m -> VillageHallScreen.renderJobs(m, level, hall, dara, 0));
			ItemStack entry = menu.icon(VillageHallScreen.FIRST_ROW);
			helper.assertTrue(lore(entry).contains("screen.aliveworkplace.hall.job_needs_class"), "not greyed: " + lore(entry));
			menu.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(dara.getVillagerData().getProfession() == VillagerProfession.NONE, "the Peasant took it: " + job(dara));
			helper.assertTrue(seen.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.job.class_needed")),
				"not told why: " + seen.stream().map(ClassJobGameTests::key).toList());

			Villager ann = villager(helper, new BlockPos(6, 2, 10), "Ann", "artisan");
			ChoiceMenu theirs = ChoiceMenu.detached(player, m -> VillageHallScreen.renderJobs(m, level, hall, ann, 0));
			helper.assertTrue(!lore(theirs.icon(VillageHallScreen.FIRST_ROW)).contains("screen.aliveworkplace.hall.job_needs_class"), "greyed for an Artisan");
			theirs.press(VillageHallScreen.FIRST_ROW, player);
			helper.assertTrue(ann.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(COUNTER)::equals).isPresent(),
				"the Artisan wasn't given the counter: " + ann.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			helper.succeed();
		});
	}

	/**
	 * A grown child takes up a parent's trade only if their class may: a Peasant child of a Shopkeeper stays jobless by a
	 * free counter; an Artisan child takes it.
	 */
	//$ gametest_ticks_batch AREA '60' '"classJobFamily"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "classJobFamily")
	public void aGrownChildTakesAParentsTradeOnlyIfTheirClassMay(GameTestHelper helper) {
		setUp(helper, true);
		helper.setBlock(COUNTER, ModBlocks.SHOP_COUNTER);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hall = helper.absolutePos(HALL);
			Families.Parents parents = new Families.Parents(Component.literal("Mara"), Component.literal("Tom"), "aliveworkplace:shopkeeper", "", true, false, false);
			Villager pip = villager(helper, new BlockPos(4, 2, 10), "Pip", "peasant");
			ModAttachments.PARENTS.set(pip, parents);
			Families.round(level, hall, pip, RandomSource.create(1));
			helper.assertTrue(pip.getVillagerData().getProfession() == VillagerProfession.NONE
				&& pip.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "the Peasant child took the trade: " + pip.getBrain().getMemory(MemoryModuleType.JOB_SITE));
			Villager wren = villager(helper, new BlockPos(6, 2, 10), "Wren", "artisan");
			ModAttachments.PARENTS.set(wren, parents);
			Families.round(level, hall, wren, RandomSource.create(1));
			helper.assertTrue(wren.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(COUNTER)::equals).isPresent(),
				"the Artisan child didn't take the trade");
			helper.succeed();
		});
	}

	/**
	 * Nobody is fired: a Peasant who was a Scholar before the rule keeps the job, across a save and reload, at the usual
	 * pace; the hall's people list marks them ("Kept on as Scholar, a job for Burgher class or better").
	 */
	//$ gametest_ticks_batch AREA '200' '"classJobKept"'
	@GameTest(template = AREA, timeoutTicks = 200, batch = "classJobKept")
	public void aWorkerBelowTheirJobsClassKeepsItAcrossAReload(GameTestHelper helper) {
		setUp(helper, true);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			Villager old = villager(helper, LECTERN.south(), "Oswin", "peasant");
			old.setNoAi(false);
			Stations.assign(level, old, helper.absolutePos(LECTERN), ModVillagers.SCHOLAR); // as in a save from before the rule
			helper.assertTrue(old.getVillagerData().getProfession() == ModVillagers.SCHOLAR, "not a Scholar to start with: " + job(old));
			CompoundTag saved = old.saveWithoutId(new CompoundTag());
			old.discard();
			Villager back = EntityType.VILLAGER.create(level);
			back.load(saved);
			helper.assertTrue(level.addFreshEntity(back), "the reloaded villager didn't go back into the world");
			helper.runAfterDelay(150, () -> {
				helper.assertTrue(back.isAlive() && back.getVillagerData().getProfession() == ModVillagers.SCHOLAR, "lost the job: " + job(back));
				helper.assertTrue(SocialClasses.of(back) == SocialClasses.get(AliveWorkplace.id("peasant")), "class after the reload: " + SocialClasses.of(back));
				helper.assertTrue(ClassJobs.above(back) == SocialClasses.get(AliveWorkplace.id("burgher")), "not marked as above their class");
				helper.assertTrue(ClassPerks.pace(back) == 1f, "not the usual pace: " + ClassPerks.pace(back));
				ItemStack person = VillageHallScreen.person(level, helper.absolutePos(HALL), back);
				helper.assertTrue(lore(person).contains("screen.aliveworkplace.hall.job_above_class"), "the hall doesn't mark them: " + lore(person));
				// A Burgher Scholar isn't marked.
				SocialClasses.seed(back, SocialClasses.get(AliveWorkplace.id("burgher")));
				helper.assertTrue(ClassJobs.above(back) == null && !lore(VillageHallScreen.person(level, helper.absolutePos(HALL), back))
					.contains("screen.aliveworkplace.hall.job_above_class"), "a Burgher Scholar is marked");
				back.discard();
				helper.succeed();
			});
		});
	}

	/**
	 * A hired traveller arrives with the class of their level: an Expert a Burgher (and may then take the Scholar's paper),
	 * a Journeyman an Artisan, an Apprentice a Peasant; one hired where there's no hall gets no class.
	 */
	//$ gametest_ticks_batch AREA '60' '"classJobTraveller"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "classJobTraveller")
	public void aHiredTravellerArrivesWithTheClassOfTheirLevel(GameTestHelper helper) {
		setUp(helper, true);
		helper.setBlock(LECTERN, Blocks.LECTERN);
		helper.runAfterDelay(2, () -> {
			ServerLevel level = helper.getLevel();
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.CREATIVE); // pays nothing
			String[] expected = {"", "", "peasant", "artisan", "burgher"};
			Villager expert = null;
			for (int lvl = 2; lvl <= 4; lvl++) {
				Villager guest = villager(helper, new BlockPos(2 + 3 * lvl, 2, 10), "Guest " + lvl, null);
				guest.setVillagerData(guest.getVillagerData().setProfession(VillagerProfession.NITWIT));
				ModAttachments.TRAVELLER.set(guest, new Traveller(level.getGameTime(), lvl));
				helper.assertTrue(Innkeepers.hire(player, guest), "level " + lvl + " not hired");
				helper.assertTrue(SocialClasses.of(guest) == SocialClasses.get(AliveWorkplace.id(expected[lvl])),
					"a level " + lvl + " traveller arrived as " + SocialClasses.of(guest));
				expert = guest;
			}
			BlockPos by = helper.absolutePos(LECTERN.south());
			expert.teleportTo(by.getX() + 0.5, by.getY(), by.getZ() + 0.5);
			Stations.choose(player, expert, new ItemStack(Items.PAPER));
			helper.assertTrue(expert.getVillagerData().getProfession() == ModVillagers.SCHOLAR, "the hired Expert can't be a Scholar: " + job(expert));

			// No hall, no class.
			helper.setBlock(HALL, Blocks.AIR);
			helper.runAfterDelay(2, () -> {
				Villager far = villager(helper, new BlockPos(14, 2, 4), "Far", null);
				ClassJobs.hired(level, far, 4);
				helper.assertTrue(!ModAttachments.SOCIAL_CLASS.has(far), "a class without a hall: " + SocialClasses.of(far));
				helper.succeed();
			});
		});
	}
}

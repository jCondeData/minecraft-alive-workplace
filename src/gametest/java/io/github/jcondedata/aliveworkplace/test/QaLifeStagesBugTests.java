package io.github.jcondedata.aliveworkplace.test;

import io.github.jcondedata.aliveworkplace.AliveWorkplace;
import io.github.jcondedata.aliveworkplace.WorkplaceConfig;
import io.github.jcondedata.aliveworkplace.build.BuilderJob;
import io.github.jcondedata.aliveworkplace.hall.Chronicle;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageNeeds;
import io.github.jcondedata.aliveworkplace.people.ClassMigration;
import io.github.jcondedata.aliveworkplace.people.ClassPerks;
import io.github.jcondedata.aliveworkplace.people.FamilyBook;
import io.github.jcondedata.aliveworkplace.people.Families;
import io.github.jcondedata.aliveworkplace.people.LifeStages;
import io.github.jcondedata.aliveworkplace.people.Moods;
import io.github.jcondedata.aliveworkplace.people.Retirement;
import io.github.jcondedata.aliveworkplace.people.SocialClasses;
import io.github.jcondedata.aliveworkplace.registry.ModAttachments;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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
 * QA (lane-c, 2026-10-24): bug tests for {@link QaLifeStagesGameTests}' area that FAIL on the mod as it is; not registered
 * in fabric.mod.json. The builder registers each with its fix (sessions.py bugs filed by lane-c-1024-0353).
 */
public class QaLifeStagesBugTests implements FabricGameTest {
	private static final String AREA = "aliveworkplace_test:big_area";
	private static final BlockPos HALL = new BlockPos(11, 2, 11);

	/**
	 * Their own grown child takes over, known by id (34.21): Bram renamed with a Name Tag after his son was born is still
	 * his son's father, so the son is chosen over a jobless stranger (whose UUID comes first).
	 */
	//$ gametest_ticks_batch AREA '100' '"qaLifeChildById"'
	@GameTest(template = AREA, timeoutTicks = 100, batch = "qaLifeChildById")
	public void qaTheirOwnChildIsKnownByIdAfterANameTag(GameTestHelper helper) {
		Leftovers.clear(helper);
		village(helper);
		helper.runAfterDelay(5, () -> {
			ServerLevel level = helper.getLevel();
			BlockPos hallPos = helper.absolutePos(HALL);
			ready(helper);
			long today = Chronicle.day(level);
			Villager bram = villager(helper, new BlockPos(5, 2, 6), "Bram", VillagerProfession.FARMER, 3);
			Villager stranger = withId(helper, new BlockPos(7, 2, 6), "Eli", new UUID(0L, 1L));
			Villager son = withId(helper, new BlockPos(9, 2, 6), "Tom", new UUID(Long.MAX_VALUE, 1L));
			helper.assertTrue(stranger.getUUID().compareTo(son.getUUID()) < 0, "the stranger doesn't come first");
			ModAttachments.PARENTS.set(son, new Families.Parents(Component.literal("Ann"), Component.literal("Bram"), "", "minecraft:farmer", true,
				false, false, UUID.nameUUIDFromBytes("ann".getBytes()), bram.getUUID()));
			LifeStages.setAdultSince(bram, today - 1230);
			LifeStages.setAdultSince(stranger, today - 20);
			LifeStages.setAdultSince(son, today - 20);
			// The player renames Bram with a Name Tag.
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			player.setGameMode(GameType.SURVIVAL);
			ItemStack tag = new ItemStack(Items.NAME_TAG);
			tag.set(DataComponents.CUSTOM_NAME, Component.literal("Grandpa"));
			player.setItemInHand(InteractionHand.MAIN_HAND, tag);
			tag.interactLivingEntity(player, bram, InteractionHand.MAIN_HAND);
			helper.assertTrue(bram.getName().getString().equals("Grandpa"), "the Name Tag didn't rename Bram: " + bram.getName().getString());
			// Eli comes first by UUID, so only knowing Tom as Bram's son by id picks him.
			Retirement.round(level, hallPos, List.of(bram, stranger, son));
			helper.assertTrue(Retirement.shadow(stranger) == null && Retirement.shadow(son) != null && bram.getUUID().equals(Retirement.shadow(son).elder()),
				"not his own son: Tom " + Retirement.shadow(son) + ", Eli " + Retirement.shadow(stranger));
			helper.succeed();
		});
	}
	/**
	 * A retired elder never takes a job again, from a player's item either: a sneak-click with lapis in the main hand,
	 * then in the off hand, leaves Bram jobless, with no lectern of his and no "is now a Librarian" said; the lectern
	 * stays free, so a young jobless villager takes it with the same lapis.
	 */
	//$ gametest_ticks_batch AREA '60' '"qaLifeRetiredItem"'
	@GameTest(template = AREA, timeoutTicks = 60, batch = "qaLifeRetiredItem")
	public void qaARetiredElderTakesNoJobFromAnItemInEitherHand(GameTestHelper helper) {
		Leftovers.clear(helper);
		Leftovers.halls(helper);
		village(helper);
		BlockPos lectern = new BlockPos(4, 2, 4);
		helper.setBlock(lectern, Blocks.LECTERN);
		helper.runAfterDelay(3, () -> {
			ServerLevel level = helper.getLevel();
			ready(helper);
			long today = Chronicle.day(level);
			List<Component> seen = new ArrayList<>();
			ServerPlayer player = listener(helper, seen);
			Villager bram = villager(helper, lectern.south(), "Bram", VillagerProfession.NONE, 1);
			LifeStages.setAdultSince(bram, today - 1240);
			ModAttachments.RETIRED.set(bram, new Retirement.Retired(today - 5, "minecraft:farmer"));
			player.setShiftKeyDown(true);
			for (InteractionHand hand : InteractionHand.values()) {
				player.setItemInHand(InteractionHand.MAIN_HAND, hand == InteractionHand.MAIN_HAND ? new ItemStack(Items.LAPIS_LAZULI) : ItemStack.EMPTY);
				player.setItemInHand(InteractionHand.OFF_HAND, hand == InteractionHand.OFF_HAND ? new ItemStack(Items.LAPIS_LAZULI) : ItemStack.EMPTY);
				click(player, bram);
				helper.assertTrue(bram.getVillagerData().getProfession() == VillagerProfession.NONE, "the retired elder took a job (lapis in the " + hand + "): " + bram.getVillagerData());
				helper.assertTrue(bram.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(), "the retired elder holds a lectern (" + hand + ")");
			}
			List<String> wrong = new ArrayList<>();
			List<String> said = seen.stream().map(Component::getString).toList();
			if (seen.stream().anyMatch(c -> key(c).equals("message.aliveworkplace.job.chosen"))) {
				wrong.add("told the retired elder took the job: " + said);
			}
			int free = level.getPoiManager().getFreeTickets(helper.absolutePos(lectern));
			Villager tom = villager(helper, lectern.east(), "Tom", VillagerProfession.NONE, 1);
			LifeStages.setAdultSince(tom, today - 20);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAPIS_LAZULI));
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			bram.discard(); // (so Tom is the one by the lectern)
			seen.clear();
			click(player, tom);
			player.setShiftKeyDown(false);
			if (!(tom.getVillagerData().getProfession() == VillagerProfession.LIBRARIAN
				&& tom.getBrain().getMemory(MemoryModuleType.JOB_SITE).map(GlobalPos::pos).filter(helper.absolutePos(lectern)::equals).isPresent())) {
				wrong.add("the lectern wasn't left free (free tickets " + free + "): Tom " + tom.getVillagerData().getProfession() + ", told "
					+ seen.stream().map(Component::getString).toList());
			}
			helper.assertTrue(wrong.isEmpty(), String.join("; ", wrong));
			helper.succeed();
		});
	}

	// --- Helpers ----------------------------------------------------------------------------------------------------

	private static void village(GameTestHelper helper) {
		int radius = VillageHalls.RADIUS;
		VillageHalls.RADIUS = 16;
		Leftovers.after(helper, () -> {
			VillageHalls.RADIUS = radius;
			Retirement.ENABLED = true;
			VillageNeeds.forget();
			Moods.forget();
		});
		helper.setBlock(HALL, ModBlocks.VILLAGE_HALL);
	}

	/** Our shipped class files, classes on; put back afterwards. */
	private static void classesOn(GameTestHelper helper) {
		SocialClasses.ENABLED = true;
		SocialClasses.load(SocialClasses.files(helper.getLevel().getServer().getResourceManager()));
		ClassPerks.forget();
		Leftovers.after(helper, () -> {
			new WorkplaceConfig().apply();
			ClassPerks.forget();
		});
	}

	private static Villager villager(GameTestHelper helper, BlockPos pos, String name, VillagerProfession job, int lvl) {
		Villager v = helper.spawn(EntityType.VILLAGER, pos);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		v.setVillagerData(v.getVillagerData().setProfession(job).setLevel(lvl));
		if (job != VillagerProfession.NONE) {
			v.setVillagerXp(1);
		}
		return v;
	}

	/** A jobless villager with a chosen UUID, so the order among candidates is known. */
	private static Villager withId(GameTestHelper helper, BlockPos pos, String name, UUID id) {
		ServerLevel level = helper.getLevel();
		Villager v = EntityType.VILLAGER.create(level);
		BlockPos at = helper.absolutePos(pos);
		v.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
		v.setUUID(id);
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		helper.assertTrue(level.addFreshEntity(v), "the villager " + name + " wasn't added");
		return v;
	}

	private static void nameTag(ServerPlayer player, Villager villager, String name) {
		ItemStack tag = new ItemStack(Items.NAME_TAG);
		tag.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		player.setItemInHand(InteractionHand.MAIN_HAND, tag);
		tag.interactLivingEntity(player, villager, InteractionHand.MAIN_HAND);
	}

	/** A right-click as the game sends it: the main hand's turn, then the off hand's if the first passed. */
	private static void click(ServerPlayer player, Villager villager) {
		net.minecraft.world.InteractionResult r = UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.MAIN_HAND, villager, null);
		if (r == net.minecraft.world.InteractionResult.PASS) {
			UseEntityCallback.EVENT.invoker().interact(player, player.level(), InteractionHand.OFF_HAND, villager, null);
		}
	}

	private static VillageHallBlockEntity ready(GameTestHelper helper) {
		VillageHallBlockEntity hall = (VillageHallBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(HALL));
		hall.setNeeds(new VillageNeeds.Needs(1, 1, 1, 1, 1, 1, 0, 0.5f));
		VillageNeeds.forget();
		Moods.forget();
		return hall;
	}

	/** A player who keeps every chat and action-bar line they're shown. */
	private static ServerPlayer listener(GameTestHelper helper, List<Component> seen) {
		ServerLevel level = helper.getLevel();
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
			new com.mojang.authlib.GameProfile(UUID.randomUUID(), "qa-life-listener"), false);
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

	private static String key(Component c) {
		if (c.getContents() instanceof TranslatableContents t) {
			return t.getKey();
		}
		return c.getSiblings().isEmpty() ? "" : key(c.getSiblings().get(0));
	}

	private static String name(ServerLevel level, BlockPos hall, Villager villager) {
		return VillageHallScreen.person(level, hall, villager).getHoverName().getString();
	}

	private static List<String> lore(ServerLevel level, BlockPos hall, Villager villager) {
		ItemLore lore = VillageHallScreen.person(level, hall, villager).get(DataComponents.LORE);
		return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
	}
}

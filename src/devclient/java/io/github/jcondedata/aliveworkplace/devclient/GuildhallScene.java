package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.hall.EdictBook;
import io.github.jcondedata.aliveworkplace.hall.Guilds;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=guildhall (ROADMAP 30.17): the Guildhall II stands finished by a Village Hall; the player grants a Master builder
 * a Guild Charter, and the Builders' Guild is founded in it. Stills: the Guildhall II from the front, its Guild Master
 * inside at the head of the long table, then the Book of Edicts with the Builders' Guild on its last row. Its checks: the
 * charter was granted, the guild was founded, and the Book's last row shows it.
 */
final class GuildhallScene {
	private static final BlockPos ORIGIN = new BlockPos(0, -60, 0);
	private static final BlockPos HALL = new BlockPos(-8, -60, -6);
	private int tick;
	private volatile Villager master;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
			mc.options.hideGui = true;
		}
		if (tick == 20) {
			server.execute(() -> stage(server));
		}
		if (tick == 40) {
			server.execute(() -> charter(server));
		}
		if (tick == 90) {
			ScreenshotHarness.shot(mc, "01_guildhall_front");
			server.execute(() -> ScreenshotHarness.hoverLookingAt(player(server), new Vec3(4.5, -57.4, 10.5), new Vec3(15.5, -58.6, 7.5)));
		}
		if (tick == 150) {
			ScreenshotHarness.shot(mc, "02_guildhall_master");
			mc.options.hideGui = false;
			server.execute(() -> EdictBook.open(player(server), HALL));
		}
		if (tick == 170) {
			ScreenshotHarness.pointAt(mc, EdictBook.FIRST_GUILD, 6);
		}
		if (tick == 185) {
			server.execute(() -> Showcase.check(player(server).containerMenu instanceof ChoiceMenu m && m.icon(EdictBook.FIRST_GUILD).is(Items.BRICKS),
				"the Book's last row shows the Builders' Guild"));
			ScreenshotHarness.shot(mc, "03_guildhall_book");
		}
		if (tick == 210) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	/** The Guildhall II finished by a Village Hall, a Master builder at the head of its long table. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
		level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
		level.setDayTime(5000);
		level.getStructureManager().get(StarterBlueprints.GUILDHALL_2.id()).orElseThrow()
			.placeInWorld(level, ORIGIN, ORIGIN, new StructurePlaceSettings(), level.getRandom(), 2);
		BuildSiteManager.get(level).recordFinished(StarterBlueprints.GUILDHALL_2.id(),
			new BlueprintData.Placement(level.dimension().location(), ORIGIN, Rotation.NONE, Mirror.NONE), player.getUUID());
		level.setBlockAndUpdate(HALL, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setOwner(player.getUUID(), player.getGameProfile().getName());
		master = EntityType.VILLAGER.spawn(level, ORIGIN.offset(15, 1, 8), MobSpawnType.COMMAND);
		master.setVillagerData(master.getVillagerData().setProfession(ModVillagers.BUILDER).setLevel(5));
		master.setCustomName(Component.literal("Dara"));
		master.setNoAi(true);
		master.setYRot(90);
		master.setYHeadRot(90);
		ScreenshotHarness.hoverLookingAt(player, new Vec3(13, -51, -16), new Vec3(13, -54, 6));
	}

	/** The player grants Dara the charter; the guild claims the finished Guildhall. */
	private void charter(MinecraftServer server) {
		ServerLevel level = server.overworld();
		VillageHallBlockEntity hall = (VillageHallBlockEntity) level.getBlockEntity(HALL);
		hall.setRank(VillageRanks.Rank.VILLAGE);
		Guilds.Offer offer = Guilds.offer(player(server), master, new ItemStack(ModItems.GUILD_CHARTER));
		Showcase.check(offer.outcome() == Guilds.Outcome.GRANTED, "Dara was chartered Guild Master: " + offer.message().getString());
		Guilds.round(level, HALL, hall);
		Showcase.check(Guilds.founded(level, hall, io.github.jcondedata.aliveworkplace.AliveWorkplace.id("builders")),
			"the Builders' Guild was founded in the Guildhall II");
	}
}

package io.github.jcondedata.aliveworkplace.devclient;

import io.github.jcondedata.aliveworkplace.guard.Relief;
import io.github.jcondedata.aliveworkplace.guard.VillageRaids;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlock;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.realm.RealmData;
import io.github.jcondedata.aliveworkplace.realm.Realms;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModVillagers;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * SCENE=relief (ROADMAP 33.17): the Barony of Thornholm (red arms) with Ashford and Brookfield members. Zombies raid
 * Ashford at night; Thornholm (from the west) and Brookfield (6 guards, from the east) send riders, who come on
 * horseback to Ashford's edge, named "Brookfield's guard", with red shields, and ride into the raiders. The ride is
 * shortened to 3 seconds for the film (it is 30 at least in game). A frame every half second for the GIF.
 */
final class ReliefScene {
	private int tick;
	private volatile BlockPos ashford;

	void tick(Minecraft mc) {
		MinecraftServer server = mc.getSingleplayerServer();
		tick++;
		if (tick == 1) {
			mc.options.renderDistance().set(6);
			mc.options.cloudStatus().set(CloudStatus.OFF);
			mc.options.guiScale().set(2);
			mc.resizeDisplay();
		}
		if (tick == 20) {
			server.execute(() -> {
				ServerLevel level = server.overworld();
				level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
				level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
				level.setDayTime(14000);
				server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), "forceload add -20 -40 160 40");
			});
		}
		if (tick == 380) {
			server.execute(() -> stage(server));
		}
		// The riders arrive 60 ticks after the call, then ride into the zombies: a frame every 10 ticks.
		if (tick >= 420 && tick <= 560 && (tick - 420) % 10 == 0) {
			int frame = (tick - 420) / 10 + 1;
			mc.getToasts().clear();
			ScreenshotHarness.shot(mc, String.format("%02d_relief", frame));
		}
		if (tick == 480) {
			server.execute(() -> {
				List<Villager> riders = Relief.riders(server.overworld(), ashford);
				Showcase.check(riders.size() == 3, "three riders came to Ashford (" + riders.size() + ")");
				Showcase.check(riders.stream().allMatch(r -> r.getVehicle() instanceof AbstractHorse), "every rider is on horseback");
				Showcase.check(riders.stream().anyMatch(r -> r.getCustomName() != null && r.getCustomName().getString().equals("Brookfield's guard")),
					"a rider is named Brookfield's guard");
			});
		}
		if (tick == 580) {
			mc.stop();
		}
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static BlockPos ground(ServerLevel level, int x, int z) {
		return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
	}

	private static BlockPos hall(ServerLevel level, ServerPlayer player, int x, String name, VillageRanks.Rank rank, int villagers) {
		BlockPos at = ground(level, x, 4);
		level.setBlockAndUpdate(at, ModBlocks.VILLAGE_HALL.defaultBlockState().setValue(VillageHallBlock.FACING, Direction.SOUTH));
		ScreenshotHarness.fileRecords(level, at, 1); // the hall's record now, not in vanilla's next task, so its guards find it (B130)
		VillageHallBlockEntity entity = (VillageHallBlockEntity) level.getBlockEntity(at);
		entity.setCustomName(Component.literal(name));
		entity.setOwner(player.getUUID(), player.getGameProfile().getName());
		entity.setRank(rank);
		Realms.round(level, at, entity, villagers);
		return at;
	}

	/** Thornholm, Ashford and Brookfield in one realm; Brookfield's guards; zombies at Ashford; the call. */
	private void stage(MinecraftServer server) {
		ServerLevel level = server.overworld();
		ServerPlayer player = player(server);
		Relief.MIN_RIDE = 60;
		Relief.EDGE = 14;
		BlockPos thornholm = hall(level, player, 0, "Thornholm", VillageRanks.Rank.TOWN, 24);
		ashford = hall(level, player, 70, "Ashford", VillageRanks.Rank.VILLAGE, 12);
		BlockPos brookfield = hall(level, player, 140, "Brookfield", VillageRanks.Rank.VILLAGE, 14);
		// B130: the halls' records now, not a task later: Relief counts a village's guards by the hall nearest them
		ScreenshotHarness.fileRecords(level, ashford, 80);
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		Realms.found(level, thornholm, player);
		Realms.add(level, thornholm, GlobalPos.of(level.dimension(), ashford), player);
		Realms.add(level, thornholm, GlobalPos.of(level.dimension(), brookfield), player);
		RealmData data = RealmData.get(server);
		RealmData.Realm realm = data.realmOf(GlobalPos.of(level.dimension(), thornholm));
		if (realm != null) {
			data.putRealm(realm.withTreasury(1_000, realm.ledger()).withArms(Optional.of(new VillageBanners.Colours(DyeColor.RED, BannerPatternLayers.EMPTY))));
		}
		for (int i = 0; i < 6; i++) {
			BlockPos at = ground(level, brookfield.getX() - 2 + i, brookfield.getZ() + 3);
			Villager guard = EntityType.VILLAGER.create(level);
			guard.setVillagerData(guard.getVillagerData().setProfession(ModVillagers.GUARD).setLevel(2));
			guard.setVillagerXp(10);
			guard.setNoAi(true);
			guard.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
			level.addFreshEntity(guard);
		}
		List<Mob> raiders = new java.util.ArrayList<>();
		for (int i = 0; i < 4; i++) {
			BlockPos at = ground(level, ashford.getX() + (i % 2 == 0 ? -7 : 7), ashford.getZ() + 2 + i);
			Zombie zombie = EntityType.ZOMBIE.create(level);
			zombie.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0f, 0f);
			zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
			zombie.addTag(VillageRaids.TAG);
			zombie.setPersistenceRequired();
			level.addFreshEntity(zombie);
			raiders.add(zombie);
		}
		io.github.jcondedata.aliveworkplace.hall.CivicEffects.forget(); // (the halls were filed above: look them up again)
		io.github.jcondedata.aliveworkplace.hall.VillageHalls.forgetNearest();
		VillageRaids.track(level, ashford, raiders.size());
		Relief.call(level, ashford);
		Vec3 eye = new Vec3(ashford.getX() + 0.5, ashford.getY() + 12, ashford.getZ() + 26);
		ScreenshotHarness.hoverLookingAt(player, eye, Vec3.atCenterOf(ashford).add(0, 1, 2));
	}
}

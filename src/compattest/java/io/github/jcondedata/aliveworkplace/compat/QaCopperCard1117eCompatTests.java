package io.github.jcondedata.aliveworkplace.compat;

import com.mojang.authlib.GameProfile;
import io.github.jcondedata.aliveworkplace.blueprint.BlueprintData;
import io.github.jcondedata.aliveworkplace.blueprint.Blueprints;
import io.github.jcondedata.aliveworkplace.blueprint.StarterBlueprints;
import io.github.jcondedata.aliveworkplace.build.BuildSite;
import io.github.jcondedata.aliveworkplace.build.BuildSiteManager;
import io.github.jcondedata.aliveworkplace.build.Builders;
import io.github.jcondedata.aliveworkplace.card.CardToasts;
import io.github.jcondedata.aliveworkplace.card.JobCards;
import io.github.jcondedata.aliveworkplace.card.StatusCard;
import io.github.jcondedata.aliveworkplace.registry.ModBlocks;
import io.github.jcondedata.aliveworkplace.registry.ModGameRules;
import io.github.jcondedata.aliveworkplace.registry.ModItems;
import io.github.jcondedata.aliveworkplace.ui.StatusCardLayout;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon;
import net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.BundlePacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

/**
 * QA (lane-e-1117-0412), ROADMAP 48.3 with Create loaded: the status card of a builder on a Create build (its site line
 * names the build, its "Needs" lines name Create's parts by their English names with the item as the line's icon, what
 * the Copperline tooltip shows) and the same card after a save and reload (the sites' saved data and the villager
 * written and read back); the card's packet carrying Create items whole; a Create build finishing sends its owner the
 * toast "<Create build> / <Name> finished it." with the chat line once. The plain suite's card tests use vanilla items.
 */
public class QaCopperCard1117eCompatTests implements FabricGameTest {
	private static final String HUGE_AREA = "aliveworkplace_compat:huge_area";
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("aliveworkplace_compat");

	private static StarterBlueprints.Entry createBuild() {
		return StarterBlueprints.CREATE_ONLY.get(0);
	}

	/** A builder at a bench with {@code entry} started at (9, 2, 3) and nothing in any chest. */
	private static BuildSite start(GameTestHelper helper, Villager builder, StarterBlueprints.Entry entry, UUID owner) {
		ServerLevel level = helper.getLevel();
		helper.setDayTime(2000);
		BlockPos bench = new BlockPos(2, 2, 2);
		helper.setBlock(bench, ModBlocks.BUILDERS_BENCH);
		Builders.employ(level, builder, helper.absolutePos(bench));
		BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)),
			Rotation.NONE, Mirror.NONE);
		BuildSite site = Builders.start(level, builder, owner, "QaCardOwner", entry.id(), placement);
		if (site == null || site.plan(level) == null) {
			throw new GameTestAssertException("setup: " + entry.id() + " didn't start");
		}
		return site;
	}

	private static Villager villager(GameTestHelper helper, String name) {
		Villager v = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 2, 3));
		v.setNoAi(true);
		v.setCustomName(Component.literal(name));
		return v;
	}

	private static List<StatusCard.Line> createNeeds(StatusCard card) {
		return card.lines().stream().filter(l -> !l.icon().isEmpty() && BuiltInRegistries.ITEM.getKey(l.icon().getItem()).getNamespace().equals("create")).toList();
	}

	private static List<String> texts(StatusCard card) {
		List<String> out = new ArrayList<>();
		for (StatusCard.Line l : card.lines()) {
			out.add(BuiltInRegistries.ITEM.getKey(l.icon().getItem()) + "|" + l.label().getString() + "|" + l.value().getString());
		}
		return out;
	}

	/** A builder's card on a Create build: the build's line, Create's parts in "Needs" lines, and the card fits at 320x180. */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qaCopperCard1117eCreateCard"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qaCopperCard1117eCreateCard")
	public void createBuildCardNamesCreatePartsAndSurvivesReload(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getGameRules().getRule(ModGameRules.BUILDERS_HELP).set(false, level.getServer());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.setGameMode(GameType.SURVIVAL);
		Villager mira = villager(helper, "Mira");
		StarterBlueprints.Entry entry = createBuild();
		BuildSite site = start(helper, mira, entry, player.getUUID());
		// Everything but Create's parts in barrels by the bench: what's left to need is Create's.
		java.util.List<net.minecraft.world.item.ItemStack> stock = new ArrayList<>();
		java.util.Set<String> createParts = new java.util.TreeSet<>();
		for (java.util.Map.Entry<net.minecraft.world.item.Item, Integer> e : site.plan(level).materials().entrySet()) {
			if (BuiltInRegistries.ITEM.getKey(e.getKey()).getNamespace().equals("create")) {
				createParts.add(BuiltInRegistries.ITEM.getKey(e.getKey()).toString());
				continue;
			}
			for (int left = e.getValue(); left > 0; left -= e.getKey().getDefaultMaxStackSize()) {
				stock.add(new net.minecraft.world.item.ItemStack(e.getKey(), Math.min(left, e.getKey().getDefaultMaxStackSize())));
			}
		}
		helper.assertTrue(!createParts.isEmpty(), "setup: " + entry.id() + " has no Create parts");
		for (int i = 0; i * 27 < stock.size(); i++) {
			BlockPos barrel = new BlockPos(1 + i % 6, 2 + i / 6, 5);
			helper.setBlock(barrel, net.minecraft.world.level.block.Blocks.BARREL);
			net.minecraft.world.Container container = helper.getBlockEntity(barrel);
			for (int slot = 0; slot < 27 && i * 27 + slot < stock.size(); slot++) {
				container.setItem(slot, stock.get(i * 27 + slot));
			}
		}

		StatusCard card = JobCards.card(player, mira, JobCards.Kind.BUILDER);
		helper.assertTrue(card.title().getString().equals("Mira"), "the card's name is " + card.title().getString());
		StatusCard.Line first = card.lines().get(0);
		String siteName = Blueprints.siteName(level, site).getString();
		helper.assertTrue(first.icon().is(ModItems.BLUEPRINT) && first.label().getString().equals(siteName),
			"the first line isn't the Create build's site: " + texts(card));
		List<StatusCard.Line> needs = createNeeds(card);
		helper.assertTrue(!needs.isEmpty(), "no line names a Create part though only Create's " + createParts + " are missing: " + texts(card));
		for (StatusCard.Line l : card.lines()) {
			helper.assertTrue(!l.label().getString().startsWith("Needs ") || needs.contains(l),
				"a need line for something in the barrels: " + l.label().getString() + " (" + texts(card) + ")");
		}
		for (StatusCard.Line need : needs) {
			String item = need.icon().getHoverName().getString();
			helper.assertTrue(!item.contains("create.") && !item.contains("block.") && !item.contains("item."),
				"a Create item's name is an untranslated key: " + item);
			String label = need.label().getString();
			helper.assertTrue(label.startsWith("Needs ") && !label.contains("create.") && !label.contains("%"),
				"a Create need's line reads \"" + label + "\"");
			helper.assertTrue(need.value().getString().matches("×\\d+"), "a Create need's count reads \"" + need.value().getString() + "\"");
		}

		// The card goes to the client whole, Create items and all.
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), level.registryAccess());
		StatusCard.STREAM_CODEC.encode(buf, card);
		StatusCard back = StatusCard.STREAM_CODEC.decode(buf);
		buf.release();
		helper.assertTrue(texts(back).equals(texts(card)), "the card's packet changed its lines: " + texts(back) + " vs " + texts(card));

		// Laid out at 320x180 (the screen's body height: the heading and its lines), the buttons stay in the card.
		int bodyH = 12 + card.lines().size() * 18;
		StatusCardLayout.Boxes boxes = StatusCardLayout.of(320, 180, 2, bodyH, 1, true, new int[]{48, 62});
		for (int[] b : boxes.buttons()) {
			helper.assertTrue(b[1] >= boxes.card()[1] && b[1] + b[3] <= boxes.card()[1] + boxes.card()[3], "a button left the card at 320x180");
		}

		// A save and reload: the sites written and read back, the builder unloaded and loaded again; the same card.
		CompoundTag sites = BuildSiteManager.get(level).save(new CompoundTag(), level.registryAccess());
		level.getDataStorage().set(BuildSiteManager.NAME, BuildSiteManager.load(sites, level.registryAccess()));
		CompoundTag saved = new CompoundTag();
		helper.assertTrue(mira.save(saved), "setup: the builder couldn't be saved");
		mira.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
		Entity loaded = EntityType.loadEntityRecursive(saved, level, e -> e);
		helper.assertTrue(loaded instanceof Villager && level.addFreshEntity(loaded), "setup: the builder couldn't be loaded again");
		Villager again = (Villager) loaded;
		StatusCard after = JobCards.card(player, again, JobCards.Kind.BUILDER);
		helper.assertTrue(after.lines().size() > 0 && after.lines().get(0).label().getString().equals(siteName),
			"after a reload the card lost the build: " + texts(after));
		helper.assertTrue(createNeeds(after).size() == needs.size(), "after a reload the card's Create needs changed: " + texts(after) + " vs " + texts(card));
		helper.assertTrue(after.title().getString().equals("Mira") && after.tag().getString().equals(card.tag().getString()),
			"after a reload the card's name or job changed: " + after.title().getString() + " / " + after.tag().getString());
		LOGGER.info("qa copper card 1117e compat: {} card, {} Create needs, e.g. {}; same after a reload", entry.id(), needs.size(),
			needs.get(0).label().getString());
		cleanUp(helper, site);
		helper.succeed();
	}

	private static void cleanUp(GameTestHelper helper, BuildSite site) {
		ServerLevel level = helper.getLevel();
		BuildSite live = BuildSiteManager.get(level).get(site.id());
		if (live != null) {
			Builders.cancel(level, live);
		}
		BuildSiteManager.get(level).forgetFinished(site.placement());
	}

	/** A Create build finishing: its owner (a modded client) gets "<Create build> / Mira finished it." once, the chat line once. */
	//$ gametest_ticks_batch HUGE_AREA '200' '"qaCopperCard1117eCreateToast"'
	@GameTest(template = HUGE_AREA, timeoutTicks = 200, batch = "qaCopperCard1117eCreateToast")
	public void createBuildFinishedToast(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		GameProfile profile = new GameProfile(UUID.randomUUID(), "QaCreateOwner");
		var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
		ServerPlayer owner = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		EmbeddedChannel channel = new EmbeddedChannel(connection);
		level.getServer().getPlayerList().placeNewPlayer(connection, owner, cookie);
		BlockPos abs = helper.absolutePos(new BlockPos(25, 2, 25));
		owner.teleportTo(level, abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0f, 0f);
		try {
			try {
				Field f = AbstractChanneledNetworkAddon.class.getDeclaredField("sendableChannels");
				f.setAccessible(true);
				@SuppressWarnings("unchecked")
				Set<ResourceLocation> channels = (Set<ResourceLocation>) f.get(ServerNetworkingImpl.getAddon(owner.connection));
				channels.add(CardToasts.Show.TYPE.id());
			} catch (ReflectiveOperationException e) {
				throw new GameTestAssertException("can't register the toast channel: " + e);
			}
			while (channel.readOutbound() != null) {
				// what joining sent
			}
			StarterBlueprints.Entry entry = createBuild();
			Villager mira = villager(helper, "Mira");
			mira.setVillagerData(mira.getVillagerData().setProfession(io.github.jcondedata.aliveworkplace.registry.ModVillagers.BUILDER).setLevel(1));
			BlueprintData.Placement placement = new BlueprintData.Placement(level.dimension().location(), helper.absolutePos(new BlockPos(9, 2, 3)),
				Rotation.NONE, Mirror.NONE);
			BuildSite site = new BuildSite(UUID.randomUUID(), owner.getUUID(), "QaCreateOwner", entry.id(), placement);
			Builders.finish(level, mira, site);
			BuildSiteManager.get(level).forgetFinished(placement);

			List<CardToasts.Show> toasts = new ArrayList<>();
			List<String> chat = new ArrayList<>();
			Object o;
			while ((o = channel.readOutbound()) != null) {
				List<Packet<?>> ps = new ArrayList<>();
				if (o instanceof BundlePacket<?> bundle) {
					bundle.subPackets().forEach(ps::add);
				} else if (o instanceof Packet<?> p) {
					ps.add(p);
				}
				for (Packet<?> p : ps) {
					if (p instanceof ClientboundCustomPayloadPacket c && c.payload() instanceof CardToasts.Show s) {
						toasts.add(s);
					} else if (p instanceof ClientboundSystemChatPacket c && !c.overlay()) {
						chat.add(c.content().getString());
					}
				}
			}
			String build = Blueprints.displayName(entry.id()).getString();
			helper.assertTrue(!build.contains(":") && !build.contains("_"), "the Create build's name is an id: " + build);
			helper.assertTrue(toasts.size() == 1, "the owner got " + toasts.size() + " toasts for " + entry.id());
			helper.assertTrue(toasts.get(0).title().getString().equals(build) && toasts.get(0).line().getString().equals("Mira finished it."),
				"the toast reads \"" + toasts.get(0).title().getString() + " / " + toasts.get(0).line().getString() + "\"");
			String line = Component.translatable("message.aliveworkplace.finished", "Mira", build).getString();
			long n = chat.stream().filter(line::equals).count();
			helper.assertTrue(n == 1, "the chat line \"" + line + "\" went " + n + " times: " + chat);
			LOGGER.info("qa copper card 1117e compat: toast \"{} / {}\", chat once", build, toasts.get(0).line().getString());
		} finally {
			level.getServer().getPlayerList().remove(owner);
		}
		helper.succeed();
	}
}

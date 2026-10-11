package io.github.jcondedata.aliveworkplace.realm;

import io.github.jcondedata.aliveworkplace.build.Friends;
import io.github.jcondedata.aliveworkplace.hall.VillageBanners;
import io.github.jcondedata.aliveworkplace.hall.VillageHallBlockEntity;
import io.github.jcondedata.aliveworkplace.hall.VillageHallScreen;
import io.github.jcondedata.aliveworkplace.hall.VillageHalls;
import io.github.jcondedata.aliveworkplace.hall.VillageProtection;
import io.github.jcondedata.aliveworkplace.hall.VillageRanks;
import io.github.jcondedata.aliveworkplace.mc.Chat;
import io.github.jcondedata.aliveworkplace.work.ChoiceMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * The Trade page's Realm tab (ROADMAP 33.11): outside a realm, "Found a realm" (a Town or better); in one, its title
 * (click with a named Name Tag to rename it), its arms (click with a banner to set them), the way out, the members
 * (the capital with a crown; rank, villagers, wellbeing, distance and owner; a click lets one go) and, for those who
 * rule the capital, the villages within reach they may add with one click. Everyone who may open the hall may read it.
 */
final class RealmTab {
	/** A gold helmet: the realm's crown. */
	static ItemStack icon(ServerLevel level, BlockPos hall) {
		RealmData.Realm realm = Realms.realmOf(level, hall);
		Component name = realm == null ? Component.translatable("screen.aliveworkplace.realm.tab_none")
			: Realms.title(level.getServer(), realm);
		return VillageHallScreen.icon(Items.GOLDEN_HELMET, name, ChatFormatting.GOLD,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.about", Realms.FOUNDING_RANK.title()), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.about_add", Realms.RANGE), ChatFormatting.GRAY));
	}

	static void fill(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer, Runnable again) {
		MinecraftServer server = level.getServer();
		RealmData.Realm realm = Realms.realmOf(level, hall);
		if (realm == null) {
			foundButton(menu, level, hall, viewer, again);
			return;
		}
		GlobalPos here = GlobalPos.of(level.dimension(), hall);
		boolean atCapital = realm.capital().equals(here);
		boolean rules = viewer != null && Realms.mayRule(server, realm.capital(), viewer);
		Realms.Rank rank = Realms.Rank.parse(realm.rank());

		// the realm's title: its rank, members and next rank; a named Name Tag renames it
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.rank", rank.title()), ChatFormatting.AQUA));
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.members", realm.members().size(), Realms.MAX_MEMBERS), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.capital", Realms.name(server, realm.capital())), ChatFormatting.GRAY));
		Realms.Rank next = rank.ordinal() + 1 < Realms.Rank.values().length ? Realms.Rank.values()[rank.ordinal() + 1] : null;
		if (next != null) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.next." + next.id(), next.members), ChatFormatting.YELLOW));
		}
		lore.add(VillageHallScreen.line("screen.aliveworkplace.realm.ranks", ChatFormatting.DARK_GRAY));
		lore.add(VillageHallScreen.line(rules && atCapital ? "screen.aliveworkplace.realm.rename_how" : "screen.aliveworkplace.realm.rename_capital", ChatFormatting.DARK_GRAY));
		menu.button(Realms.TITLE, VillageHallScreen.icon(Items.GOLDEN_HELMET, Realms.title(server, realm), ChatFormatting.GOLD, lore.toArray(Component[]::new)), p -> {
			ItemStack carried = menu.getCarried();
			if (carried.isEmpty()) {
				return; // a plain click: the tooltip says what to click with
			}
			Realms.Result result = Realms.rename(level, hall, p, carried);
			said(level, p, result);
			again.run();
		});

		// the arms: click with a banner
		VillageBanners.Colours arms = realm.arms().orElse(null);
		ItemStack armsIcon = arms == null ? new ItemStack(Items.WHITE_BANNER) : arms.banner();
		menu.button(Realms.ARMS, VillageHallScreen.icon(armsIcon, Component.translatable(arms == null ? "screen.aliveworkplace.realm.no_arms"
				: "screen.aliveworkplace.realm.arms"), ChatFormatting.WHITE,
			VillageHallScreen.line("screen.aliveworkplace.realm.arms_about", ChatFormatting.GRAY),
			VillageHallScreen.line(rules && atCapital ? "screen.aliveworkplace.realm.arms_how" : "screen.aliveworkplace.realm.arms_capital", ChatFormatting.DARK_GRAY)),
			p -> {
				if (menu.getCarried().isEmpty()) {
					return;
				}
				Realms.Result result = Realms.setArms(level, hall, p, menu.getCarried());
				said(level, p, result);
				again.run();
			});

		// the way out: leave (a member), give up the crown (the capital)
		boolean mayLeave = viewer == null || Realms.mayRule(server, here, viewer);
		menu.button(Realms.LEAVE, VillageHallScreen.icon(Items.BARRIER, Component.translatable(atCapital ? "screen.aliveworkplace.realm.abdicate"
				: "screen.aliveworkplace.realm.leave"), mayLeave ? ChatFormatting.RED : ChatFormatting.GRAY,
			VillageHallScreen.line(Component.translatable(atCapital ? "screen.aliveworkplace.realm.abdicate_how" : "screen.aliveworkplace.realm.leave_how",
				VillageHalls.name(level, hall), Realms.title(server, realm)), ChatFormatting.GRAY)), p -> {
			said(level, p, Realms.leave(level, hall, p));
			again.run();
		});

		// the members, the capital first
		int slot = Realms.MEMBERS;
		for (RealmData.Member m : realm.members()) {
			if (slot >= ChoiceMenu.SIZE) {
				break;
			}
			boolean capital = m.hall().equals(realm.capital());
			menu.button(slot++, member(server, level, hall, realm, m, capital, rules && atCapital), capital || !(rules && atCapital) ? null : p -> {
				said(level, p, Realms.letGo(level, hall, m.hall(), p));
				again.run();
			});
		}

		// villages the capital's ruler may add
		if (rules && atCapital && viewer != null && realm.members().size() < Realms.MAX_MEMBERS) {
			for (GlobalPos other : candidates(server, realm, viewer)) {
				if (slot >= ChoiceMenu.SIZE) {
					break;
				}
				menu.button(slot++, candidate(server, hall, other), p -> {
					said(level, p, Realms.add(level, hall, other, p));
					again.run();
				});
			}
		}
	}

	/** Not in a realm: the button that founds one, with what it takes and why not. */
	static void foundButton(ChoiceMenu menu, ServerLevel level, BlockPos hall, @Nullable ServerPlayer viewer, Runnable again) {
		VillageHallBlockEntity entity = level.getBlockEntity(hall) instanceof VillageHallBlockEntity e ? e : null;
		Component village = VillageHalls.name(level, hall);
		List<Component> lore = new ArrayList<>();
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.found_about", Realms.FOUNDING_RANK.title(), Realms.Rank.BARONY.title(village)),
			ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line("screen.aliveworkplace.realm.ranks", ChatFormatting.GRAY));
		Component why = entity == null ? null : entity.owner() == null ? Component.translatable("message.aliveworkplace.realm.no_owner", village)
			: viewer != null && !VillageProtection.mayRule(level, entity, viewer) ? Component.translatable("message.aliveworkplace.realm.found_not_yours", entity.ownerName(), village)
			: entity.rank().ordinal() < Realms.FOUNDING_RANK.ordinal() ? Component.translatable("message.aliveworkplace.realm.rank", village, Realms.FOUNDING_RANK.title(), entity.rank().title())
			: null;
		lore.add(why != null ? VillageHallScreen.line(why, ChatFormatting.RED) : VillageHallScreen.line("screen.aliveworkplace.realm.found_how", ChatFormatting.DARK_GRAY));
		menu.button(Realms.FOUND, VillageHallScreen.icon(Items.GOLDEN_HELMET, Component.translatable("screen.aliveworkplace.realm.found"),
			why == null ? ChatFormatting.GREEN : ChatFormatting.GRAY, lore.toArray(Component[]::new)), p -> {
			said(level, p, Realms.found(level, hall, p));
			again.run();
		});
	}

	/** A member's icon: the capital a gold helmet, the rest the realm's banner; its rank, villagers, wellbeing, where and whose. */
	static ItemStack member(MinecraftServer server, ServerLevel level, BlockPos hall, RealmData.Realm realm, RealmData.Member m, boolean capital, boolean mayLetGo) {
		List<Component> lore = new ArrayList<>();
		if (capital) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.realm.the_capital", ChatFormatting.GOLD));
		}
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.member_rank", Realms.rankOf(server, m.hall()).title()), ChatFormatting.AQUA));
		RealmData.Known known = RealmData.get(server).known(m.hall());
		if (known != null) {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.member_villagers", known.villagers(), known.wellbeing()), ChatFormatting.GRAY));
		} else {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.realm.member_unseen", ChatFormatting.DARK_GRAY));
		}
		if (m.hall().pos().equals(hall) && m.hall().dimension().equals(level.dimension())) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.realm.member_here", ChatFormatting.GRAY));
		} else {
			lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.member_where", VillageHallScreen.where(hall, m.hall().pos())), ChatFormatting.GRAY));
		}
		String owner = Realms.ownerName(server, m.hall());
		lore.add(VillageHallScreen.line(owner.isEmpty() ? Component.translatable("screen.aliveworkplace.realm.member_no_owner")
			: Component.translatable("screen.aliveworkplace.realm.member_owner", owner), ChatFormatting.GRAY));
		lore.add(VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.member_joined", m.joined()), ChatFormatting.DARK_GRAY));
		if (mayLetGo && !capital) {
			lore.add(VillageHallScreen.line("screen.aliveworkplace.realm.let_go_how", ChatFormatting.DARK_GRAY));
		}
		ItemStack shown = capital ? new ItemStack(Items.GOLDEN_HELMET) : realm.arms().map(VillageBanners.Colours::banner).orElseGet(() -> new ItemStack(Items.WHITE_BANNER));
		return VillageHallScreen.icon(shown, Realms.name(server, m.hall()), capital ? ChatFormatting.GOLD : ChatFormatting.WHITE, lore.toArray(Component[]::new));
	}

	/** Villages {@code viewer} may add: seen by a round, in the capital's dimension within reach, in no realm, theirs or a friend's; nearest first. */
	static List<GlobalPos> candidates(MinecraftServer server, RealmData.Realm realm, ServerPlayer viewer) {
		RealmData data = RealmData.get(server);
		BlockPos capital = realm.capital().pos();
		List<GlobalPos> out = new ArrayList<>();
		for (RealmData.Known k : data.knownVillages()) {
			GlobalPos at = k.hall();
			if (!at.dimension().equals(realm.capital().dimension()) || data.realmOf(at) != null || Realms.gone(server, at)) {
				continue;
			}
			double dx = at.pos().getX() - capital.getX();
			double dz = at.pos().getZ() - capital.getZ();
			UUID owner = Realms.ownerOf(server, at);
			if (dx * dx + dz * dz <= (double) Realms.RANGE * Realms.RANGE && owner != null && Friends.get(server).mayDirect(owner, viewer.getUUID())) {
				out.add(at);
			}
		}
		out.sort(java.util.Comparator.comparingDouble(g -> g.pos().distSqr(capital)));
		return out;
	}

	/** A village to add: a lime banner, where it is, its rank and owner, and what a click does. */
	static ItemStack candidate(MinecraftServer server, BlockPos hall, GlobalPos other) {
		VillageRanks.Rank rank = Realms.rankOf(server, other);
		return VillageHallScreen.icon(Items.LIME_BANNER, Component.translatable("screen.aliveworkplace.realm.add", Realms.name(server, other)), ChatFormatting.GREEN,
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.member_rank", rank.title()), ChatFormatting.AQUA),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.member_where", VillageHallScreen.where(hall, other.pos())), ChatFormatting.GRAY),
			VillageHallScreen.line(Component.translatable("screen.aliveworkplace.realm.member_owner", Realms.ownerName(server, other)), ChatFormatting.GRAY),
			VillageHallScreen.line("screen.aliveworkplace.realm.add_how", ChatFormatting.DARK_GRAY));
	}

	/** Tells the player what came of a click, with a "no" sound for a refusal. */
	static void said(ServerLevel level, ServerPlayer player, Realms.Result result) {
		if (!result.ok()) {
			level.playSound(null, player.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.6f, 1f);
		}
		Chat.chat(player, result.message());
	}

	private RealmTab() {
	}
}

package org.CobbleUtils.cobbleroguelike.ui;

import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

import java.util.ArrayList;
import java.util.List;

/**
 * What the client-side run screen shows: a {@link Menu}'s buttons plus the run around it (party,
 * floor tower, stats). Sent to players who have the mod installed; everyone else gets the chest menu.
 */
public final class RogueView {

    /** Matches clicks to the view they were made on. */
    public int id;
    public Text title = Text.empty();
    /** Block id tiled behind the run panels (the biome's block), or "" for none. */
    public String theme = "";
    /** Whether the content panel is themed too (e.g. a wild encounter). */
    public boolean themedContent;
    /** Top-right badge, e.g. coins. */
    public Text badge = Text.empty();

    /** Set when the menu belongs to a run: then the party, stats and tower are shown. */
    public boolean run;
    public List<Text> stats = new ArrayList<>();
    /** Tower rows, bottom to top; the top one is the boss. */
    public List<String> tower = new ArrayList<>();
    public int towerCurrent = -1;
    public List<Member> party = new ArrayList<>();
    /** Co-op: the partner's team, shown under yours. */
    public List<Member> partnerParty = new ArrayList<>();

    /** The menu's own buttons and icons, in slot order. */
    public List<Entry> content = new ArrayList<>();
    /** The nav bar (Shop, Bag, Tutor...), shown along the bottom. */
    public List<Entry> actions = new ArrayList<>();

    /** A party slot: model icon (named, with details as lore), level, HP fraction and held item. */
    public record Member(ItemStack icon, int level, float health, ItemStack held) {
    }

    /** A menu slot: its icon carries the name and lore; only clickable entries send clicks. */
    public record Entry(int slot, ItemStack stack, boolean clickable) {
    }

    public void write(RegistryByteBuf buf) {
        buf.writeVarInt(id);
        writeText(buf, title);
        buf.writeString(theme);
        buf.writeBoolean(themedContent);
        writeText(buf, badge);
        buf.writeBoolean(run);
        buf.writeVarInt(stats.size());
        stats.forEach(line -> writeText(buf, line));
        buf.writeVarInt(tower.size());
        tower.forEach(buf::writeString);
        buf.writeVarInt(towerCurrent);
        writeMembers(buf, party);
        writeMembers(buf, partnerParty);
        writeEntries(buf, content);
        writeEntries(buf, actions);
    }

    public static RogueView read(RegistryByteBuf buf) {
        RogueView view = new RogueView();
        view.id = buf.readVarInt();
        view.title = readText(buf);
        view.theme = buf.readString();
        view.themedContent = buf.readBoolean();
        view.badge = readText(buf);
        view.run = buf.readBoolean();
        int stats = buf.readVarInt();
        for (int i = 0; i < stats; i++) {
            view.stats.add(readText(buf));
        }
        int tower = buf.readVarInt();
        for (int i = 0; i < tower; i++) {
            view.tower.add(buf.readString());
        }
        view.towerCurrent = buf.readVarInt();
        view.party = readMembers(buf);
        view.partnerParty = readMembers(buf);
        view.content = readEntries(buf);
        view.actions = readEntries(buf);
        return view;
    }

    private static void writeText(RegistryByteBuf buf, Text text) {
        TextCodecs.REGISTRY_PACKET_CODEC.encode(buf, text);
    }

    private static Text readText(RegistryByteBuf buf) {
        return TextCodecs.REGISTRY_PACKET_CODEC.decode(buf);
    }

    private static void writeStack(RegistryByteBuf buf, ItemStack stack) {
        ItemStack.OPTIONAL_PACKET_CODEC.encode(buf, stack);
    }

    private static ItemStack readStack(RegistryByteBuf buf) {
        return ItemStack.OPTIONAL_PACKET_CODEC.decode(buf);
    }

    private static void writeMembers(RegistryByteBuf buf, List<Member> members) {
        buf.writeVarInt(members.size());
        for (Member member : members) {
            writeStack(buf, member.icon());
            buf.writeVarInt(member.level());
            buf.writeFloat(member.health());
            writeStack(buf, member.held());
        }
    }

    private static List<Member> readMembers(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<Member> members = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            members.add(new Member(readStack(buf), buf.readVarInt(), buf.readFloat(), readStack(buf)));
        }
        return members;
    }

    private static void writeEntries(RegistryByteBuf buf, List<Entry> entries) {
        buf.writeVarInt(entries.size());
        for (Entry entry : entries) {
            buf.writeVarInt(entry.slot());
            writeStack(buf, entry.stack());
            buf.writeBoolean(entry.clickable());
        }
    }

    private static List<Entry> readEntries(RegistryByteBuf buf) {
        int size = buf.readVarInt();
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(buf.readVarInt(), readStack(buf), buf.readBoolean()));
        }
        return entries;
    }
}

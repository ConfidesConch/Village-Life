package io.github.confidesconch.villagelife.client.ui.page;

import java.util.List;

import io.github.confidesconch.villagelife.client.map.ClientFriends;
import io.github.confidesconch.villagelife.client.ui.component.ScrollPanel;
import io.github.confidesconch.villagelife.client.ui.core.Ui;
import io.github.confidesconch.villagelife.client.ui.core.UiRect;
import io.github.confidesconch.villagelife.client.ui.core.UiTheme;

import net.minecraft.network.chat.Component;

/**
 * Tab "Friends" of "Player" (M21b10a, the book's friends chapter since M11g): invitations to you
 * (✓ / ✕), your friends (online dot, remove with a second click), players online to invite and your
 * open invitations. Friends see each other on the map and can become managers.
 */
final class PlayerFriends {
    private PlayerFriends() {}

    private static final int LINE = 16;

    static void render(Ui ui, UiRect body, ScrollPanel scroll, PlayerPage page) {
        int half = (body.w() - 6) / 2;
        UiRect left = body.left(half);
        UiRect right = body.rightOf(half + 6);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, left);
        UiTheme.draw(ui.graphics(), UiTheme.PANEL_DARK, right);

        UiRect l = left.inset(6);
        int w = ScrollPanel.contentWidth(l);
        int top = scroll.begin(ui, l);
        int y = top;
        List<ClientFriends.Person> invites = ClientFriends.invitesIn();
        if (!invites.isEmpty()) {
            ui.text(Component.translatable("villagelife.book.friends.invites_in"), l.x(), y, UiTheme.GOLD, w);
            y += 14;
            for (ClientFriends.Person p : invites) {
                ui.text(Component.literal(p.name()), l.x(), y + 4, UiTheme.TEXT, w - 40);
                ui.button(new UiRect(l.x() + w - 36, y, 16, 16), Component.literal("✓"), true, () -> ClientFriends.send("accept", p.id()));
                ui.button(new UiRect(l.x() + w - 16, y, 16, 16), Component.literal("✕"), true, () -> ClientFriends.send("decline", p.id()));
                y += LINE + 2;
            }
            y += 6;
        }
        List<ClientFriends.Person> friends = ClientFriends.friends();
        ui.text(Component.translatable("villagelife.book.friends.list", friends.size(), ClientFriends.limit()), l.x(), y, UiTheme.GOLD, w);
        y += 14;
        if (friends.isEmpty()) {
            y += ui.paragraph(Component.translatable("villagelife.book.friends.none"), l.x(), y, w, UiTheme.TEXT_DIM);
        }
        for (ClientFriends.Person p : friends) {
            ui.graphics().fill(l.x() + 1, y + 6, l.x() + 5, y + 10, p.online() ? UiTheme.GOOD : 0xFF666666);
            boolean confirming = p.id().equals(page.confirmRemove());
            int buttonWidth = confirming ? Math.min(90, w / 2) : 16;
            ui.text(Component.literal(p.name()), l.x() + 9, y + 4, p.online() ? UiTheme.TEXT : UiTheme.TEXT_DIM, w - buttonWidth - 14);
            UiRect remove = new UiRect(l.x() + w - buttonWidth, y, buttonWidth, 16);
            if (confirming) {
                ui.button(remove, Component.translatable("villagelife.book.friends.remove_sure"), true, () -> {
                    ClientFriends.send("remove", p.id());
                    page.setConfirmRemove(null);
                });
            } else {
                ui.button(remove, Component.literal("✕"), true, () -> page.setConfirmRemove(p.id()));
                ui.tooltip(remove, Component.translatable("villagelife.ui.player.remove_friend"));
            }
            y += LINE + 2;
        }
        scroll.end(ui, y - top);

        UiRect r = right.inset(6);
        int ry = r.y();
        ui.text(Component.translatable("villagelife.book.friends.invite"), r.x(), ry, UiTheme.GOLD, r.w());
        ry += 14;
        ry += ui.paragraph(Component.translatable("villagelife.book.friends.invite_hint"), r.x(), ry, r.w(), UiTheme.TEXT_DIM) + 4;
        List<ClientFriends.Person> invitable = ClientFriends.invitable();
        if (invitable.isEmpty()) {
            ry += ui.paragraph(Component.translatable("villagelife.book.friends.nobody_online"), r.x(), ry, r.w(), UiTheme.TEXT_DIM);
        }
        for (ClientFriends.Person p : invitable) {
            if (ry + 16 > r.bottom()) {
                break;
            }
            ui.button(new UiRect(r.x(), ry, r.w(), 16), Component.literal("+ " + p.name()), true, () -> ClientFriends.send("invite", p.id()));
            ry += LINE + 2;
        }
        List<ClientFriends.Person> out = ClientFriends.invitesOut();
        if (!out.isEmpty() && ry + 30 < r.bottom()) {
            ui.text(Component.translatable("villagelife.book.friends.invites_out"), r.x(), ry + 6, UiTheme.GOLD, r.w());
            ry += 20;
            for (ClientFriends.Person p : out) {
                if (ry + 16 > r.bottom()) {
                    break;
                }
                ui.text(Component.literal(p.name()), r.x(), ry + 4, UiTheme.TEXT_DIM, r.w() - 20);
                ui.button(new UiRect(r.right() - 16, ry, 16, 16), Component.literal("✕"), true, () -> ClientFriends.send("remove", p.id()));
                ry += LINE + 2;
            }
        }
    }
}

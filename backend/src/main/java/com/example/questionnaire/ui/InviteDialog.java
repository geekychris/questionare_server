package com.example.questionnaire.ui;

import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.lists.RecipientListService;
import com.example.questionnaire.mail.InviteService;
import com.example.questionnaire.mail.MailException;
import com.example.questionnaire.security.CurrentUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.MultiSelectComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.server.VaadinServletRequest;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

/** "Email link": send a campaign's share link to pasted addresses and/or recipient lists, then show each address's result. */
class InviteDialog extends Dialog {

    InviteDialog(CampaignDTO campaign, InviteService invites, RecipientListService lists, CurrentUser user, int maxRecipients) {
        setHeaderTitle("Email the link: " + campaign.getTitle());
        setWidth("min(640px, 95vw)");

        TextArea addresses = new TextArea("Email addresses");
        addresses.setHelperText("One per line, or separated by commas. At most " + maxRecipients + ".");
        addresses.setWidthFull();
        addresses.setMinHeight("8em");
        MultiSelectComboBox<RecipientListService.Summary> fromLists = new MultiSelectComboBox<>("Recipient lists");
        List<RecipientListService.Summary> mine = lists.list(user);
        fromLists.setItems(mine);
        fromLists.setItemLabelGenerator(l -> l.name() + " (" + l.size() + ")");
        fromLists.setWidthFull();
        fromLists.setVisible(!mine.isEmpty());
        TextArea note = new TextArea("Note (optional)");
        note.setHelperText("Added above the link.");
        note.setMaxLength(InviteService.MAX_NOTE);
        note.setWidthFull();
        Paragraph about = new Paragraph("Each person gets their own email with the survey's link"
                + (campaign.isRequireLogin() ? " (they'll need to sign in to answer)." : "."));

        Grid<InviteService.Result> results = new Grid<>();
        results.addColumn(InviteService.Result::to).setHeader("Address").setFlexGrow(1);
        results.addComponentColumn(r -> {
            Span s = new Span(r.sent() ? "sent" : r.error());
            s.getElement().getThemeList().add(r.sent() ? "badge success" : "badge error");
            return s;
        }).setHeader("Result").setAutoWidth(true);
        results.setAllRowsVisible(true);
        results.setVisible(false);

        VerticalLayout body = new VerticalLayout(about, addresses, fromLists, note, results);
        body.setPadding(false);
        add(body);

        Button send = new Button("Send");
        send.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        send.addClickListener(e -> {
            try {
                List<String> all = new ArrayList<>(InviteService.splitAddresses(addresses.getValue()));
                all.addAll(lists.addresses(fromLists.getValue().stream().map(RecipientListService.Summary::id).toList(), user));
                List<String> to = InviteService.splitAddresses(String.join("\n", all));
                List<InviteService.Result> out = invites.invite(campaign.getId(), to, note.getValue(), user, origin());
                results.setItems(out);
                results.setVisible(true);
                long ok = out.stream().filter(InviteService.Result::sent).count();
                about.setText(ok + " of " + out.size() + " sent.");
                addresses.setValue(String.join("\n", out.stream().filter(r -> !r.sent()).map(InviteService.Result::to).toList()));
                fromLists.clear();
            } catch (MailException ex) {
                about.setText(ex.getMessage());
            } catch (org.springframework.web.server.ResponseStatusException ex) {
                about.setText(ex.getReason());
            }
        });
        Button close = new Button("Close", e -> close());
        getFooter().add(close, send);
    }

    private static String origin() {
        VaadinServletRequest req = VaadinServletRequest.getCurrent();
        return req == null ? null : ServletUriComponentsBuilder.fromContextPath(req.getHttpServletRequest()).build().toUriString();
    }
}

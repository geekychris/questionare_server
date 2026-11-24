package com.example.questionnaire.ui;

import com.example.questionnaire.dto.CampaignDTO;
import com.example.questionnaire.mail.InviteService;
import com.example.questionnaire.mail.MailService;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.service.CampaignService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

/** Campaign list: share link (copy/rotate/close, email when mail is set up), edit, results, delete (admin only). */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Campaigns | Questionnaire")
@RolesAllowed({"ADMIN", "CREATOR"})
public class CampaignsView extends VerticalLayout {

    private final CampaignService service;
    private final InviteService invites;
    private final MailService mail;
    private final CurrentUser user;
    private final Grid<CampaignDTO> grid = new Grid<>(CampaignDTO.class, false);

    public CampaignsView(CampaignService service, InviteService invites, MailService mail) {
        this.service = service;
        this.invites = invites;
        this.mail = mail;
        this.user = CurrentUser.current();
        setSizeFull();

        Button create = new Button("New campaign", VaadinIcon.PLUS.create(),
                e -> UI.getCurrent().navigate(CampaignEditorView.class));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout top = new HorizontalLayout(new H2(user.isAdmin() ? "All campaigns" : "My campaigns"), create);
        top.setAlignItems(Alignment.BASELINE);

        grid.addColumn(CampaignDTO::getTitle).setHeader("Title").setFlexGrow(1);
        grid.addColumn(c -> c.isAcceptingResponses() ? "open" : "closed").setHeader("Status").setAutoWidth(true);
        grid.addColumn(c -> c.isRequireLogin() ? "sign-in" : "anyone with link").setHeader("Respondents").setAutoWidth(true);
        grid.addColumn(CampaignDTO::getResponseCount).setHeader("Responses").setAutoWidth(true);
        if (user.isAdmin()) {
            grid.addColumn(CampaignDTO::getOwnerName).setHeader("Owner").setAutoWidth(true);
        }
        grid.addComponentColumn(this::shareCell).setHeader("Share link").setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(this::actions).setHeader("").setAutoWidth(true);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        add(top, grid);
        refresh();
    }

    private void refresh() {
        grid.setItems(service.getAllCampaigns(user));
    }

    private HorizontalLayout shareCell(CampaignDTO c) {
        String url = ShareLinks.absolute(c.getShareUrl());
        Anchor link = new Anchor(url, url);
        link.setTarget("_blank");
        Button copy = new Button(VaadinIcon.COPY.create(), e -> {
            UI.getCurrent().getPage().executeJs("navigator.clipboard.writeText($0)", url);
            Notification.show("Link copied");
        });
        copy.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        copy.setTooltipText("Copy link");
        Button rotate = new Button(VaadinIcon.REFRESH.create(), e -> confirm(
                "Rotate link?", "The current link stops working immediately.", "Rotate",
                () -> service.rotateShareToken(c.getId(), user)));
        rotate.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        rotate.setTooltipText("Rotate link (new token)");
        HorizontalLayout h = new HorizontalLayout(link, copy, rotate);
        if (mail.isEnabled() && c.isAcceptingResponses()) {
            Button email = new Button(VaadinIcon.ENVELOPE_O.create(), e -> new InviteDialog(
                    c, invites, user, mail.properties().getMaxRecipients()).open());
            email.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
            email.setTooltipText("Email the link");
            email.setAriaLabel("Email the link");
            h.add(email);
        }
        h.setAlignItems(Alignment.CENTER);
        return h;
    }

    private HorizontalLayout actions(CampaignDTO c) {
        Button edit = new Button("Edit", e -> UI.getCurrent().navigate(CampaignEditorView.class, c.getId()));
        Button results = new Button("Results", e -> UI.getCurrent().navigate(ResultsView.class, c.getId()));
        Button toggle = new Button(c.isActive() ? "Close" : "Reopen", e -> {
            service.setOpen(c.getId(), !c.isActive(), user);
            refresh();
        });
        HorizontalLayout h = new HorizontalLayout(edit, results, toggle);
        if (user.isAdmin()) {
            Button delete = new Button("Delete", e -> confirm("Delete \"" + c.getTitle() + "\"?",
                    "The campaign and all its responses are deleted permanently.", "Delete",
                    () -> service.deleteCampaign(c.getId(), user)));
            delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
            h.add(delete);
        }
        for (var b : h.getChildren().toList()) {
            ((Button) b).addThemeVariants(ButtonVariant.LUMO_SMALL);
        }
        return h;
    }

    private void confirm(String header, String text, String action, Runnable run) {
        ConfirmDialog d = new ConfirmDialog(header, text, action, e -> {
            run.run();
            refresh();
        });
        d.setCancelable(true);
        d.setConfirmButtonTheme("error primary");
        d.open();
    }
}

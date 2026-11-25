package com.example.questionnaire.ui;

import com.example.questionnaire.model.Campaign;
import com.example.questionnaire.security.CurrentUser;
import com.example.questionnaire.webhook.WebhookDelivery;
import com.example.questionnaire.webhook.WebhookService;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * The campaign editor's webhook section: where to POST signed events when someone answers
 * (README "Webhooks"), a "Send test" button, and the recent deliveries.
 */
class WebhookPanel extends VerticalLayout {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebhookService webhooks;
    private final CurrentUser user;
    private final Long campaignId;
    private final TextField url = new TextField("Webhook URL");
    private final CheckboxGroup<String> events = new CheckboxGroup<>("Send on");
    private final Grid<WebhookDelivery> grid = new Grid<>();

    WebhookPanel(WebhookService webhooks, CurrentUser user, Long campaignId) {
        this.webhooks = webhooks;
        this.user = user;
        this.campaignId = campaignId;
        setPadding(false);
        add(new H2("Webhook"));
        if (!webhooks.enabled()) {
            add(new Paragraph("Webhooks are off on this site: an administrator sets WEBHOOK_ALLOWED_HOSTS and "
                    + "WEBHOOK_SECRET to turn them on."));
            return;
        }
        add(new Paragraph("Optional. When set, each response (and, if chosen, closing the survey) is POSTed as "
                + "signed JSON to this address, for example a FaaS function's route. Allowed hosts: "
                + String.join(", ", webhooks.allowedHosts()) + "."));
        Campaign c = webhooks.campaign(campaignId, user);
        url.setWidthFull();
        url.setMaxLength(512);
        url.setPlaceholder("http://" + webhooks.allowedHosts().get(0).replaceFirst("^\\.", "faas.") + ":30883/fn/survey-on-response");
        url.setClearButtonVisible(true);
        url.setValue(c.getWebhookUrl() == null ? "" : c.getWebhookUrl());
        events.setItems(WebhookService.EVENTS);
        events.setItemLabelGenerator(e -> WebhookService.RESPONSE_CREATED.equals(e) ? "Each response" : "Survey closed");
        events.setValue(new LinkedHashSet<>(c.getWebhookUrl() == null ? List.of(WebhookService.RESPONSE_CREATED) : WebhookService.events(c)));

        Button save = new Button("Save webhook", e -> save());
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button test = new Button("Send test", VaadinIcon.PAPERPLANE.create(), e -> test());
        Button refresh = new Button("Refresh", VaadinIcon.REFRESH.create(), e -> refresh());
        refresh.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        grid.addColumn(d -> d.getCreatedAt().format(TIME)).setHeader("Queued").setAutoWidth(true);
        grid.addColumn(WebhookDelivery::getEvent).setHeader("Event").setAutoWidth(true);
        grid.addColumn(d -> d.getStatus().name().toLowerCase()).setHeader("Status").setAutoWidth(true);
        grid.addColumn(WebhookDelivery::getAttempts).setHeader("Tries").setAutoWidth(true);
        grid.addColumn(d -> d.getLastStatusCode() == null ? "" : String.valueOf(d.getLastStatusCode())).setHeader("HTTP").setAutoWidth(true);
        grid.addColumn(d -> d.getLastError() == null ? "" : d.getLastError()).setHeader("Last error").setFlexGrow(1);
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);

        add(url, events, new HorizontalLayout(save, test, refresh), grid);
        refresh();
    }

    private void save() {
        try {
            Campaign c = webhooks.update(campaignId, user, url.getValue().trim(), List.copyOf(events.getValue()));
            url.setValue(c.getWebhookUrl() == null ? "" : c.getWebhookUrl());
            Notification.show(c.getWebhookUrl() == null ? "Webhook removed" : "Webhook saved");
        } catch (ResponseStatusException e) {
            error(e);
        }
    }

    private void test() {
        try {
            webhooks.ping(campaignId, user);
            Notification.show("Test queued; it is sent within a few seconds (Refresh to see it)");
            refresh();
        } catch (ResponseStatusException e) {
            error(e);
        }
    }

    private void refresh() {
        grid.setItems(webhooks.recent(campaignId, 20));
    }

    private static void error(ResponseStatusException e) {
        Notification n = Notification.show(e.getReason() == null ? "Could not save" : e.getReason());
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}

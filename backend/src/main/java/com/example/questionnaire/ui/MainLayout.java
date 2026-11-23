package com.example.questionnaire.ui;

import com.example.questionnaire.security.CurrentUser;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.spring.security.AuthenticationContext;

/** Header with the app name, the signed-in user and a sign-out button (local + RP-initiated logout). */
public class MainLayout extends AppLayout {

    public MainLayout(AuthenticationContext authenticationContext) {
        H1 logo = new H1("Questionnaire");
        logo.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");
        RouterLink home = new RouterLink();
        home.add(logo);
        home.setRoute(CampaignsView.class);

        CurrentUser user = CurrentUser.current();
        Span who = new Span(user == null ? "" : user.name() + " (" + String.join(", ", user.roles()) + ")");
        who.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Button logout = new Button("Sign out", e -> authenticationContext.logout());
        logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout header = new HorizontalLayout(home, who, logout);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(who);
        header.setWidthFull();
        header.getStyle().set("padding", "0 var(--lumo-space-m)");
        who.getStyle().set("text-align", "right");
        addToNavbar(header);
    }
}

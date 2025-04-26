package com.example.questionnaire.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;

@Route(value = "welcome", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Welcome | Questionnaire App")
public class WelcomeView extends VerticalLayout {

    public WelcomeView() {
        addClassName("welcome-view");
        setSizeFull();
        setAlignItems(Alignment.CENTER);
        setJustifyContentMode(JustifyContentMode.CENTER);

        add(
            createHeader(),
            createDescription(),
            createStartButton()
        );
    }

    private H1 createHeader() {
        H1 header = new H1("Welcome to the Questionnaire");
        header.addClassName("welcome-header");
        return header;
    }

    private Paragraph createDescription() {
        Paragraph description = new Paragraph(
            "This questionnaire will help us understand your preferences and needs better. " +
            "Your responses will be kept confidential and will be used to improve our services."
        );
        description.addClassName("welcome-description");
        return description;
    }

    private Button createStartButton() {
        Button startButton = new Button("Start Questionnaire", 
            e -> getUI().ifPresent(ui -> ui.navigate("questionnaire")));
        startButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_LARGE);
        startButton.addClassName("welcome-start-button");
        return startButton;
    }
}


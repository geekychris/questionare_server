package com.example.questionnaire.ui;

import com.example.questionnaire.lists.RecipientListService;
import com.example.questionnaire.lists.RecipientListService.Detail;
import com.example.questionnaire.lists.RecipientListService.Summary;
import com.example.questionnaire.security.CurrentUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;

/** Recipient lists: named address lists to email survey links to (README "Recipient lists"). */
@Route(value = "lists", layout = MainLayout.class)
@PageTitle("Recipient lists | Questionnaire")
@RolesAllowed({"ADMIN", "CREATOR"})
public class RecipientListsView extends VerticalLayout {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final RecipientListService lists;
    private final CurrentUser user;
    private final Grid<Summary> grid = new Grid<>();

    public RecipientListsView(RecipientListService lists) {
        this.lists = lists;
        this.user = CurrentUser.current();
        setSizeFull();

        Button create = new Button("New list", VaadinIcon.PLUS.create(), e -> edit(null));
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout top = new HorizontalLayout(new H2(user.isAdmin() ? "All recipient lists" : "My recipient lists"), create);
        top.setAlignItems(Alignment.BASELINE);

        grid.addColumn(Summary::name).setHeader("Name").setFlexGrow(1);
        grid.addColumn(Summary::size).setHeader("Addresses").setAutoWidth(true);
        if (user.isAdmin()) {
            grid.addColumn(Summary::ownerName).setHeader("Owner").setAutoWidth(true);
        }
        grid.addColumn(s -> s.updatedAt() == null ? "" : WHEN.format(s.updatedAt())).setHeader("Updated").setAutoWidth(true);
        grid.addComponentColumn(this::actions).setAutoWidth(true).setFlexGrow(0);
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        add(top, new Paragraph("Use a list from a campaign's \"Email the link\" dialog, or from the API with listIds."), grid);
        refresh();
    }

    private void refresh() {
        grid.setItems(lists.list(user));
    }

    private HorizontalLayout actions(Summary s) {
        Button edit = new Button("Edit", e -> edit(s.id()));
        Button delete = new Button("Delete", e -> {
            ConfirmDialog d = new ConfirmDialog("Delete \"" + s.name() + "\"?",
                    "The list is deleted. Surveys already sent are not affected.", "Delete", ev -> {
                lists.delete(s.id(), user);
                refresh();
            });
            d.setCancelable(true);
            d.setConfirmButtonTheme("error primary");
            d.open();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR);
        edit.addThemeVariants(ButtonVariant.LUMO_SMALL);
        delete.addThemeVariants(ButtonVariant.LUMO_SMALL);
        return new HorizontalLayout(edit, delete);
    }

    /** Create (id null) or edit a list: a name and one address per line. */
    private void edit(Long id) {
        Detail current = id == null ? null : lists.get(id, user);
        Dialog d = new Dialog();
        d.setHeaderTitle(current == null ? "New recipient list" : "Edit " + current.name());
        d.setWidth("min(640px, 95vw)");
        TextField name = new TextField("Name");
        name.setMaxLength(RecipientListService.MAX_NAME);
        name.setWidthFull();
        TextArea members = new TextArea("Addresses");
        members.setHelperText("One per line: name@example.com or Sam Smith <sam@example.com>. At most "
                + RecipientListService.MAX_ENTRIES + ".");
        members.setWidthFull();
        members.setMinHeight("12em");
        if (current != null) {
            name.setValue(current.name());
            members.setValue(RecipientListService.format(current.members()));
        }
        Paragraph error = new Paragraph();
        error.getStyle().set("color", "var(--lumo-error-text-color)");
        VerticalLayout body = new VerticalLayout(name, members, error);
        body.setPadding(false);
        d.add(body);

        Button save = new Button("Save", e -> {
            try {
                var parsed = RecipientListService.parse(members.getValue());
                if (current == null) {
                    lists.create(name.getValue(), parsed, user);
                } else {
                    lists.update(current.id(), name.getValue(), parsed, user);
                }
                d.close();
                refresh();
            } catch (ResponseStatusException ex) {
                error.setText(ex.getReason());
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        d.getFooter().add(new Button("Cancel", e -> d.close()), save);
        d.open();
    }
}

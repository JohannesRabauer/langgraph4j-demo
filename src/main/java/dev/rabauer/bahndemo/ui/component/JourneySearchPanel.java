package dev.rabauer.bahndemo.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.client.dto.LocationDto;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

/**
 * From/to station search with autocomplete backed by DbApiClient#searchLocations, plus a Search
 * button. MainView wires onSearch(...) to trigger a journey search once both fields are set.
 *
 * Prototype-scoped: MainView (one per browser UI) injects this via constructor, and a Vaadin
 * component can only ever belong to one UI's state tree - a singleton here would break on the
 * second browser tab/reload with "Can't move a node from one state tree to another".
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class JourneySearchPanel extends HorizontalLayout {

    private final DbApiClient dbApiClient;
    private final ComboBox<LocationDto> fromField = new ComboBox<>("From");
    private final ComboBox<LocationDto> toField = new ComboBox<>("To");
    private final Button searchButton = new Button("Search connections");

    private BiConsumer<LocationDto, LocationDto> searchListener = (from, to) -> { };

    public JourneySearchPanel(DbApiClient dbApiClient) {
        this.dbApiClient = dbApiClient;
        fromField.setItemLabelGenerator(LocationDto::toString);
        toField.setItemLabelGenerator(LocationDto::toString);
        fromField.setItems(query -> dbApiClient.searchLocations(query.getFilter().orElse("")).stream()
                .skip(query.getOffset()).limit(query.getLimit()));
        toField.setItems(query -> dbApiClient.searchLocations(query.getFilter().orElse("")).stream()
                .skip(query.getOffset()).limit(query.getLimit()));
        searchButton.addClickListener(e -> searchListener.accept(fromField.getValue(), toField.getValue()));
        add(fromField, toField, searchButton);
    }

    public void onSearch(BiConsumer<LocationDto, LocationDto> listener) {
        this.searchListener = listener;
    }
}

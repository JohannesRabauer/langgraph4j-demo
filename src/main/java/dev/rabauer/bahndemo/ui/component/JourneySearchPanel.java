package dev.rabauer.bahndemo.ui.component;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import dev.rabauer.bahndemo.client.DbApiClient;
import dev.rabauer.bahndemo.client.dto.LocationDto;
import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

/**
 * From/to station search with autocomplete backed by DbApiClient#searchLocations, plus a Search
 * button. MainView wires onSearch(...) to trigger a journey search once both fields are set.
 *
 * TODO(stream): wire ComboBox filtering to dbApiClient.searchLocations(filterString) - e.g. via
 * setItems(ComboBox.ItemFilter) or an equivalent lazy data provider. Currently unwired/empty.
 */
@Component
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
        searchButton.addClickListener(e -> searchListener.accept(fromField.getValue(), toField.getValue()));
        add(fromField, toField, searchButton);
    }

    public void onSearch(BiConsumer<LocationDto, LocationDto> listener) {
        this.searchListener = listener;
    }
}

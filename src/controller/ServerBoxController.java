package controller;

import Dao.ListUserDao;
import entity.User;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Callback;

import java.util.Comparator;
import java.util.function.Predicate;

public class ServerBoxController {
    @FXML
    public TextField tfSearch;
    @FXML
    private ListView<User> lvClients;

    private FilteredList<User> filteredList;
    private Predicate<User> predicate;

    public ListView<User> getLvClients() {
        return lvClients;
    }


    @FXML
    void initialize() {
        ObservableList<User> userObservableList = ListUserDao.getInstance().getUserList();

        filteredList = userObservableList.filtered(null);
        SortedList<User> sortedList = filteredList.sorted(new Comparator<User>() {
            @Override
            public int compare(User o1, User o2) {
                if (!o1.isConnecting() && o2.isConnecting()) {
                    return 1;
                } else if (o1.isConnecting() && !o2.isConnecting()) {
                    return -1;
                }
                return 0;
            }
        });

        // cho clients phụ thuộc vào ObservableList lstClients
        this.lvClients.setItems(sortedList);

        setCellFactory();


    }

    private void setCellFactory() {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                lvClients.setCellFactory(new Callback<ListView<User>, ListCell<User>>() {
                    @Override
                    public ListCell<User> call(ListView<User> userListView) {
                        ListCell<User> listCell = new ListCell<User>() {
                            @Override
                            protected void updateItem(User user, boolean b) {
                                super.updateItem(user, b);

                                if (b) {
                                    setText(null);
                                    setStyle("");
                                } else {
                                    setText(user.getUsername());
                                    setTextFill(Color.WHITE);
                                    setBorder(new Border(new BorderStroke(Color.YELLOW, BorderStrokeStyle.SOLID, CornerRadii.EMPTY,
                                            BorderWidths.DEFAULT)));

                                    setFont(Font.font("Time new roman", FontWeight.BOLD, 16));

                                    if (user.isConnecting()) {
                                        if (isSelected()) {
                                            setStyle("-fx-background-color: blue");
                                        } else {
                                            setStyle("-fx-background-color: green;");
                                        }
                                    } else {
                                        if (isSelected()) {
                                            setStyle("-fx-background-color: blue");
                                        } else {
                                            setStyle("-fx-background-color: gray;");
                                        }
                                    }

                                }
                            }
                        };
                        return listCell;
                    }
                });
            }
        });

    }

    /**
     * xử lý sự kiện nhập từ tìm kiếm trên ô search
     * xử lý list lọc hiển thị những tên có chứa từ tìm kiếm
     *
     * @param keyEvent
     */
    public void handleSearch(KeyEvent keyEvent) {
        predicate = new Predicate<User>() {
            @Override
            public boolean test(User user) {
                return user.getUsername().contains(tfSearch.getText());
            }
        };

        filteredList.setPredicate(predicate);
    }
}

package controller;

import Dao.ListUserDao;
import business.ServerThread;
import com.entity.Server;
import javafx.application.Application;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class Main extends Application {

    public final String SERVER_NAME = "localhost";
    public final int PORT = 1234;

    @Override
    public void init() throws Exception {
        super.init();
        // đọc danh sách users lưu vào list dùng chung khi ứng dụng khởi động
        ListUserDao.getInstance().loadUserFromFile();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/ui/ServerBox.fxml"));
        Parent root = loader.load();
        ServerBoxController controller = loader.getController();
        primaryStage.setTitle("Server Application");
        primaryStage.setScene(new Scene(root));
        primaryStage.show();

        // đóng ứng dụng khi cửa sổ này đóng
        primaryStage.setOnHiding(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                System.exit(0);
            }
        });

        // khởi tạo Server
        Server server = new Server(SERVER_NAME, PORT);

        // khởi tạo ServerThread truyền Server vào hàm khởi tạo
        ServerThread serverThread = new ServerThread(server, controller);

        // chạy thread ServerThread để quản lý client, mỗi khi có kết nối sẽ tạo 1 thread mới
        // và truyền cho thread đó socket của kết nối đó
        new Thread(serverThread).start();


    }


    public static void main(String[] args) {
        launch(args);
    }
}

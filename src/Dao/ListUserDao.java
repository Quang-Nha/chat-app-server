package Dao;

import business.ClientHandler;
import entity.User;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

public class ListUserDao {
    private static ListUserDao instance;
    private final ObservableList<User> userList = FXCollections.observableArrayList();
    private StringBuilder userListString = new StringBuilder();

    private final Path DIR_NAME = FileSystems.getDefault().getPath("userList");
    private final Path FILE_NAME = FileSystems.getDefault().getPath(DIR_NAME.toAbsolutePath().toString(), "userList.txt");

    private ListUserDao() {

    }

    public static synchronized ListUserDao getInstance() {
        if (instance == null) {
            instance = new ListUserDao();
        }
        return instance;
    }

    public ObservableList<User> getUserList() {
        return userList;
    }

    public StringBuilder getUserListString() {
        return userListString;
    }

    public void loadUserFromFile() {
        try {
            if (Files.notExists(DIR_NAME)) {
                Files.createDirectory(DIR_NAME);
            }
            if (Files.notExists(FILE_NAME)) {
                Files.createFile(FILE_NAME);
            }

            BufferedReader reader = Files.newBufferedReader(FILE_NAME);
            String line;

            userList.clear();
            userListString = new StringBuilder();

            // nếu dòng đọc được ko null và ko rỗng thì mới lưu user vào list
            while ((line = reader.readLine()) != null && !line.trim().isEmpty()) {

                // nếu dòng đúng định dạng thì tạo mảng chứa user và pass phải có size = 2
                // nếu ko ko lưu ra list
                String[] strings = line.split(":");
                if (strings.length == 2) {
                    String userName = strings[0].trim();
                    String password = strings[1].trim();
                    boolean connecting = ClientHandler.clientsConnectingMap.containsKey(userName);

                    User user = new User(userName, password, connecting);

                    userList.add(user);
                    userListString.append(user.toString1()).append("/./");
                }
            }

            reader.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void updateUserListStringFromUserList() {
        userListString = new StringBuilder();
        for (User user : userList) {
            userListString.append(user.toString1()).append("/./");
        }
    }

    public void saveUserToFile() {
        try(BufferedWriter writer = Files.newBufferedWriter(FILE_NAME)) {
            for (User user : userList) {
                writer.write(user.toString2() + "\n");
//                writer.newLine();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

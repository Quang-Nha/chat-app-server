package business;

import Dao.ChatHistoryDao;
import Dao.ListUserDao;
import controller.ServerBoxController;
import entity.User;
import javafx.application.Platform;
import javafx.collections.ObservableList;

import java.io.*;
import java.net.Socket;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;

public class ClientHandler implements Runnable {

    // map chứa các ClientHandler quản lý các kết nối đang mở đến client
    public static HashMap<String, ClientHandler> clientsConnectingMap = new HashMap<>();

    // map quản lý các tin nhắn chờ chưa được gửi đi
    public static HashMap<String, String> alertHaveSmsMap = new HashMap<>();
    private final Socket socket;

    ServerBoxController controller;

    private static final String HEADER_REPLACE_LOGIN = "REPLACE_LOGIN";
    private static final String HEADER_SEND_FROM = "SEND_FROM";
    public static int count = 0;
    public static final String HEADER_NAME = "NAME";
    public static final String HEADER_USERS = "USERS";
    private static final String HEADER_CHAT_HISTORY = "CHAT_HISTORY";
    private static final String HEADER_SEND_TO = "SEND_TO";
    private static final String HEADER_CHANGE_PASSWORD = "CHANGE_PASSWORD";
    private static final String HEADER_RENAME = "RENAME";
    private static final String HEADER_USER_RENAME = "USER_RENAME";

    private final DataInputStream dis;
    private final DataOutputStream dos;
    private String userName; // tên của kết nối
    private final String tempUserName;
    private User connectUser;

    public Socket getSocket() {
        return socket;
    }

    public ClientHandler(Socket socket, ServerBoxController controller) throws IOException {
        this.socket = socket;

        this.controller = controller;
        // tạo luồng đọc, ghi theo socket
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());

        // tăng biến class lên 1
        count++;

        // gán biến class cho tên tạm, mỗi đối tượng mới sẽ có 1 tên tạm duy nhất
        tempUserName = String.valueOf(count);
        // truyền đối tượng này cho map các kết nối đang hoạt động theo key là tên tạm
        // để khi có sự kiện kết nối nào thay đổi list users nó sẽ gửi lại list cho tất cả các kết nối
        // nên cần thêm ngay đối tượng này vào với key là tên tạm vì có thể client của kết nối này chưa đặt xong tên
        // nên chưa gửi tên lên và vẫn cần phải cập nhật sự kiện thay đổi list users
        clientsConnectingMap.put(tempUserName, ClientHandler.this);
        System.out.println(clientsConnectingMap.size() + "from temp name");


    }

    /**
     * đọc dữ liệu từ 1 client gửi
     */
    public void run() {
        try {
            // đầu tiên gửi tin nhắn danh sách User cho clients để nó cập nhật list
            send(HEADER_USERS, ListUserDao.getInstance().getUserListString().toString());

            while (true) {
                String line;
                line = this.dis.readUTF();

                // nếu nhận được tin nhắn có header là HEADER_NAME là kiểu tin gửi tên user, tên này sẽ được đặt
                // luôn cho tên kết nối này, gọi hàm xử lý tin nhắn loại này
                if (line.startsWith(HEADER_NAME)) {
                    handleCliendSendName(line.trim());
                }

                // tin nhắn yêu user yêu cầu lịch sử chat
                if (line.startsWith(HEADER_CHAT_HISTORY)) {
                    handleClientSendRequestHistory(line.trim());
                }

                // tin nhắn user yêu cầu gửi tin nhắn cho 1 user khác
                if (line.startsWith(HEADER_SEND_TO)) {
                    handleSendTo(line);
                }

                // tin nhắn user thay đổi mật khẩu
                if (line.startsWith(HEADER_CHANGE_PASSWORD)) {
                    handleChangePass(line);
                }

                // tin nhắn user đã đổi sang tên mới
                if (line.startsWith(HEADER_RENAME)) {
                    handleRename(line);
                }
            }

        } catch (Exception var2) {
            // nếu kết nối bị mất thì in ra thông báo
            System.out.println("No connection");
            // xóa khỏi map các client đang kết nối nếu đã nhận được tên thì xóa theo tên
            // còn chưa nhận được tên client gửi lên thì xóa theo têm tạm trong map
            if (userName != null) {
                clientsConnectingMap.remove(userName);
            } else {
                clientsConnectingMap.remove(tempUserName);
            }
            controller.getLvClients().refresh();
            System.out.println(clientsConnectingMap.size() + "from connect error");

            // set connect của trong list users là false nếu nó đã được gán(ko null) khi client gửi tên lên
            // ghi lại cập nhật list vào từ userList vào userListString
            // và gửi lại userListString cho tất cả các client đang hoạt động để nó update lại list
            if (connectUser != null) {
                connectUser.setConnecting(false);

            }
            ListUserDao.getInstance().updateUserListStringFromUserList();
            for (ClientHandler handler : clientsConnectingMap.values()) {
                handler.send(HEADER_USERS, ListUserDao.getInstance().getUserListString().toString());
            }

            // làm mới lại list view để nó cập nhật lại
            controller.getLvClients().refresh();

            var2.printStackTrace();
        }
    }

    /**
     * client đổi tên và gửi tên mới lên để cập nhật
     * @param line
     */
    private void handleRename(String line) {
        // lấy tên mới được gửi lên sau khi tách header
        String newName = line.split("/./")[1];

        // lưu tên biến cũ
        String oldName = userName;
        // gán tên kết nối là tên mới
        userName = newName;

        // lấy đường dẫn tới thư mục chứa các file lịch sử chat
        Path directory = FileSystems.getDefault().getPath("chatHistory");

        // nếu chưa tồn tại thư mục lịch sử thì tạo nó rồi thoát
        if (Files.notExists(directory)) {
            try {
                Files.createDirectory(directory);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        // duyệt qua thư mục chứa lịch chat lấy ra các file
        // file nào có chứa tên cũ thì đổi tên file sang tên mới, đổi nội dung bên trong nơi nào có
        // chứa đầu đề tên cũ + : thì đổi sang tên mới + :
        try (DirectoryStream<Path> fileList = Files.newDirectoryStream(directory, "*.txt")) {

            for (Path path : fileList) {

                // lấy tên của path
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT).trim();

                // nếu tên path chứa tên cũ kết nối theo dạng _oldName_ hoặc _oldName_.txt thì đổi nó sang tên mới
                // thay nội dung trong lịch sử chat
                if (fileName.startsWith("_" + oldName.toLowerCase(Locale.ROOT) + "_") ||
                        fileName.endsWith("_" + oldName.toLowerCase(Locale.ROOT) + "_" + ".txt")) {
                    // tạo path của tên cũ
                    Path pathOldName = path;

                    // tạo tên mới cho file bằng cách đổi phần tên cũ sang tên mới
                    String fileNewName = fileName.replace(oldName, newName);
                    // tạo path của tên mới
                    Path pathNewName = FileSystems.getDefault().getPath("chatHistory", fileNewName);

                    // lấy nội dung lịch sử chat từ file của tên cũ
                    StringBuilder oldChatHistory = ChatHistoryDao.readHistoryFromFile(pathOldName);
                    // đổi nội dung lịch sử gồm tất cả tiêu đề tên cũ dạng oldName: sang newName:
                    // xóa phần /./ phân cách các dòng khi đọc từ file ra chuỗi, phần này chỉ để gửi cho
                    // các user khác để nó tách các dòng rồi hiển thị nội dung chat
                    String newChat = oldChatHistory.toString().replace(oldName + ":", newName + ":")
                            .replace("/./", "\n");

                    // đổi tên file lịch sử chat từ tên cũ sang tên mới
                    Files.move(pathOldName, pathNewName);
                    // ghi đè lại nội dung lịch sử chat đã đổi sang tên mới vào file tên mới
                    ChatHistoryDao.saveSmsToFile(pathNewName, newChat, false);

                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // đổi key tên cũ bằng key tên mới trong map các kết nối đang mở
        clientsConnectingMap.remove(oldName);
        clientsConnectingMap.put(newName, this);

        // lấy list các user
        ObservableList<User> userObservableList = ListUserDao.getInstance().getUserList();

        // lặp list xem user nào tên giống tên cũ thì đổi sang tên mới
        for (User user1 : userObservableList) {
            if (user1.getUsername().equalsIgnoreCase(oldName)) {
                user1.setUsername(newName);
                break;
            }
        }

        // cập nhật lại danh dách dạng chuỗi theo list trên
        // lưu lại danh sách vào file
        ListUserDao.getInstance().updateUserListStringFromUserList();
        ListUserDao.getInstance().saveUserToFile();

        // gửi lại danh sách cho tất cả các user đang kết nối
        // gửi lại tên cũ và mới cho các user đang kết nối để nó đổi tên cửa sổ chat thành tên mới
        // nếu nó đang mở cưa sổ chat với user vừa đổi tên
        for (ClientHandler clientHandler : clientsConnectingMap.values()) {
            clientHandler.send(HEADER_USERS, ListUserDao.getInstance().getUserListString().toString());
            clientHandler.send(HEADER_USER_RENAME, oldName + ":" + newName);
        }

        // làm mới lại list hiển thị các user
        controller.getLvClients().refresh();


    }

    /**
     * xử lý sự kiện user báo thay đổi mật khẩu và gửi lên tên với mật khẩu mới
     * cập nhật lại trong các list, lưu vào file và gửi lại list user cho các user khác
     * để các user đó cập nhật lại mật khẩu để dùng trong trường hợp đăng nhập, đổi mật khẩu
     *
     * @param line
     */
    private void handleChangePass(String line) {
        // tách hearder và user
        String[] strings = line.split("/./");
        // lấy user
        String user = strings[1];
        // tách username ra mật khẩu và pass
        String[] userArr = user.split(":");

        // gán username và pass cho 2 biến
        String userName = userArr[0];
        String pass = userArr[1];

        // lấy list các user
        ObservableList<User> userObservableList = ListUserDao.getInstance().getUserList();

        // lặp list xem user nào cùng tên thì thay lại mật khẩu của user đó và thoát lặp
        for (User user1 : userObservableList) {
            if (user1.getUsername().equalsIgnoreCase(userName)) {
                user1.setPassword(pass);
                break;
            }
        }

        // cập nhật lại danh dách dạng chuỗi theo list trên
        // lưu lại danh sách vào file
        ListUserDao.getInstance().updateUserListStringFromUserList();
        ListUserDao.getInstance().saveUserToFile();

        // gửi lại danh sách cho tất cả các user đang kết nối
        for (ClientHandler clientHandler : clientsConnectingMap.values()) {
            clientHandler.send(HEADER_USERS, ListUserDao.getInstance().getUserListString().toString());
        }
    }

    /**
     * khi nhận tin nhắn từ client yêu cầu gửi đến client khác
     * lưu tin nhắn vào file lịch sử chat giữa 2 client
     * gửi tin nhắn cho client cần gửi nếu client này đang có kết nối
     *
     * @param line
     */
    private void handleSendTo(String line) {
        // tách các đoạn thông tin của tin nhắn
        String[] strings = line.split("/./");
        // lấy tên client cần gửi đến
        String toName = strings[1].trim();

        // lấy nội dung tin nhắn
        String sms = strings[2];

        // lấy đường dẫn tới thư mục chứa các file lịch sử
        Path directory = FileSystems.getDefault().getPath("chatHistory");

        // duyệt qua các tệp trong thư mục, nếu có file nào giống tên cần tìm phải dạng _thisUserName_toUserName_.txt
        // hoặc _toUserName_thisUserName_.txt
        // thì gán tên file đó cho file cần tìm là fileChatHistory
        try (DirectoryStream<Path> fileList = Files.newDirectoryStream(directory, "*.txt")) {

            String fileChatHistory = null;
            for (Path path : fileList) {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT).trim();

                if (fileName.equalsIgnoreCase("_" + toName + "_" + userName + "_" + ".txt") ||
                        fileName.equalsIgnoreCase("_" + userName + "_" + toName + "_" + ".txt")) {
                    fileChatHistory = fileName;
                    break;
                }
            }

            if (fileChatHistory != null) {
                // lấy path là đường dẫn tới file cần lưu được xác định ở trên
                Path fileChat = FileSystems.getDefault().getPath(directory.toAbsolutePath().toString(), fileChatHistory);
                // nối đoạn tin nhắn vào file qua hàm lưu
                ChatHistoryDao.saveSmsToFile(fileChat, sms, true);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // nếu client cần gửi tới đang kết nối bằng cách xem tên client có trong map ko nếu có
        // thì lấy ClientHandler kết nối với client đó và gửi đoạn tin nhắn kèm tên của người gửi là tên của kết nối này
        // hay tên của ClientHandler này
        // nếu ko thì lưu vào map chứa các đoạn tin nhắn chưa gửi được với key là tên client muốn gửi
        // value có đầu là tên chính là tên ClientHandler kết nối với client gửi tin này
        // đến khi có kết nối đến client muốn gửi và nhận diện được tên tức là sau khi client đó gửi tên lên
        // thì xem có tin nhắn nào đang chờ của client đó ko trong map và gửi tin nhắn đó để client đó mở cửa sổ chat luôn
        if (clientsConnectingMap.containsKey(toName)) {
            ClientHandler toClientHandler = clientsConnectingMap.get(toName);
            toClientHandler.send(HEADER_SEND_FROM, userName + "/./" + sms);
        } else {
            alertHaveSmsMap.put(toName, userName + "/./" + sms);
        }

    }

    /**
     * xử lý client gửi tin nhắn lấy lịch sử chat với client khác
     *
     * @param line
     */
    private void handleClientSendRequestHistory(String line) {
        // lấy đường dẫn tới thư mục chứa các file lịch sử
        Path directory = FileSystems.getDefault().getPath("chatHistory");

        // nếu chưa tồn tại thư mục lịch sử thì tạo nó
        if (Files.notExists(directory)) {
            try {
                Files.createDirectory(directory);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // lấy tên của client khác sau header
        String[] strings = line.split("/./");
        String chatWith = strings[1].trim();

        // hoặc toUserName_thisUserName.txt
        // duyệt thư mục lấy tất cả các file path có đuôi .txt
        // nếu có file nào giống tên cần tìm phải dạng _thisUserName_toUserName_.txt
        // hoặc _toUserName_thisUserName_.txt thì gán tên đó cho biến tên file cần tìm rồi thoát vòng lặp
        // tạo luồng đọc tới file này, đọc nội dung rồi gửi lại cho client
        // nếu ko có file nào thì chưa có lịch sử chat, tạo file đó rồi thoát
        try (DirectoryStream<Path> fileList = Files.newDirectoryStream(directory, "*.txt")) {

            String fileChatHistory = null;
            // duyệt qua các tệp trong thư mục, nếu có file nào chứa cả tên của user và tên user muốn chat cùng
            // thì gán tên file đó cho file cần tìm là fileChatHistory
            for (Path path : fileList) {
                String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT).trim();

                if (fileName.equalsIgnoreCase("_" + chatWith + "_" + userName + "_" + ".txt") ||
                        fileName.equalsIgnoreCase("_" + userName + "_" + chatWith + "_" + ".txt")) {
                    fileChatHistory = fileName;
                    break;
                }
            }

            // nếu chưa tồn tại file lịch sử thì tạo file rồi thoát
            if (fileChatHistory == null) {
                Path fileChat = FileSystems.getDefault().getPath(directory.toAbsolutePath().toString(),
                        "_" + userName + "_" + chatWith + "_" + ".txt");
                Files.createFile(fileChat);
                return;
            }

            // nếu file đã tồn tại thì tạo luồng đọc tới file này và lấy nội dung chat
            Path fileChat = FileSystems.getDefault().getPath(directory.toAbsolutePath().toString(), fileChatHistory);
            StringBuilder contentChatHistory = ChatHistoryDao.readHistoryFromFile(fileChat);

            // gửi nội dung cho client với header nhận dạng lịch sử chat + tên client muốn chat
            send(HEADER_CHAT_HISTORY, chatWith + "/./" + (contentChatHistory));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * tạo 1 luồng xử lý nhận được tên và pass từ client
     * đổi đối tượng này bằng key là tên mới thay cho tên tạm
     * xem tên đã có trong list tên chưa
     * nếu ko có thì thêm vào list, ghi vào file, gửi lại danh sách tên dạng string cho các kết nối đang mở
     * xem trong map chứa tin nhắn chờ chưa gửi có tin nhắn nào cho client này ko thì gửi luôn cho nó để nó mở cửa sổ chat
     *
     * @param line
     * @throws IOException
     */
    private void handleCliendSendName(String line) throws IOException {
        Platform.runLater(new Runnable() {
            @Override
            public void run() {

                // lấy tên và pass kết nối bằng cách tách header ra
                String[] strings = line.split("/./");

                // đoạn strings[1] bao gồm tên và pass
                String user = strings[1];
                // tách tên và pass thành mảng
                String[] userArray = user.split(":");
                // lấy ra tên và pass gán cho các biến
                userName = userArray[0];
                String password = userArray[1];

                // nếu trong map ClientHandler chứa các kết nối đang mở có kết nối trùng với tên/key vừa nhận
                // thì có nghĩa là có nơi khác cùng tài khoản đang kết nối
                // lấy ra ClientHandler của kết nối đó rồi lấy Socket của nó
                // dùng ClientHandler đó gửi tín hiệu sẽ bị ngắt kết nối cho kết nối đó rồi
                // ngắt socket của kết nối đó, khi socket đó bị ngắt sẽ tự gọi ngoại lệ ngắt và xóa
                // ClientHandler của kết nối đó khỏi map các kết nối theo key là tên của kết nối
                // mà ko cần xóa ở ngay tại đây
                // có nghĩa là kết nối này sẽ đóng kết nối cùng tài khoản kia
                // đăng nhập thay thế cho kết nối đang đang nhập cùng tài khoản kia
                if (clientsConnectingMap.containsKey(userName)) {
                    ClientHandler clientHandler = clientsConnectingMap.get(userName);

                    clientHandler.send(HEADER_REPLACE_LOGIN, "");

                    try {
                        clientHandler.getSocket().close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                // tạo user lấy được từ tin nhắn để có thể thêm vào list
                connectUser = new User(userName, password, true);

                // trường hợp có đăng nhập nơi khác thì đoạn xử lý trên sẽ đóng kết nối đó
                // kết nối đó xử lý ngoại lệ và xóa nó khỏi map các kết nối đang mở

                // nếu trong map các kết nối vẫn còn kết nối cùng tài khoản ở nơi khác
                // dù đã xử lý đóng kết nối ở đoạn code trên, có nghĩa đã đóng kết nối đó
                // nhưng đoạn xử lý ngoại lệ đóng kết nối của nó chưa kịp chạy để xóa kết nối đó khỏi map
                // vì mỗi kết nối chạy trên 1 luồng khác nhau nên ko thể chắc chắn thứ tự chạy code giữa các luồng

                // nếu đoạn xử lý ngoại lệ đó mà chạy sau khi đoạn code thêm kết nối này vào
                // map bằng key là tên ở đoạn code ngay dưới thì do kết nối nơi khác và kết nối này cùng tên/key
                // trong map nên
                // đoạn xử lý ngoại lệ của kết nối kia chạy sau cùng xóa kết nối của nó khỏi map bằng key là tên kết
                // nối đó. mà kết nối này cũng cùng tên mà đã thêm vào rồi thì lệnh xóa đó cũng xóa luôn kết nối này
                // trong map dẫn đến tình trạng trong map ko còn phần tử nào của kết nối này gây chạy sai

                // để xử lý lỗi này thì cần đợi đến khi kêt nối cùng tên kia bị xóa khỏi map(đoạn ngoại
                // lệ xử lý ngắt kết nối của kết nối đó đã chạy và xóa kết nối đó khỏi map)
                // rồi mới thêm kết nối này vào map bằng cách sử dụng vòng lặp không thoát ra để chạy đoạn code
                // thêm kết nối này vào map cho đến khi kết nối kia đã bị xóa khỏi map
                while (clientsConnectingMap.containsKey(userName)) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // xóa phần tử chứa đối tượng này với tên tạm và thêm lại đối tượng này vào với tên đã nhận
                clientsConnectingMap.remove(tempUserName);
                clientsConnectingMap.put(userName, ClientHandler.this);
                System.out.println(clientsConnectingMap.size() + "from user name");

                // kiểm tra xem tên đã tồn tại trong list User chưa, tức là
                // tên có giống với thuộc tính tên của 1 User trong list ko
                // nếu rồi xóa đi rồi thêm lại user mới với là connectUser vừa tạo của kết nối này có connecting là true
                boolean check = true;
                for (User s : ListUserDao.getInstance().getUserList()) {
                    if (s.getUsername().equalsIgnoreCase(userName.trim())) {
                        ListUserDao.getInstance().getUserList().remove(s);
                        ListUserDao.getInstance().getUserList().add(connectUser);
                        s.setConnecting(true);
                        check = false;
                        break;
                    }
                }

                // nếu chưa tồn tại thì thêm vào list, ghi vào file, gửi lại danh sách tên dạng string cho các kết nối đang mở
                if (check) {
                    ListUserDao.getInstance().getUserList().add(connectUser);
                    ListUserDao.getInstance().saveUserToFile();
                    ListUserDao.getInstance().getUserListString().append(userName).append(":");
                }

                // update lại danh sách trong userListString dùng để gửi list user cho các client
                // lấy trong map danh sách ClientHandler quản lý các kết nối đang mở
                // gọi hàm gửi của nó để gửi lại danh sách users đã thay đổi cho các client của kết nối đó
                // để các client cập nhật lại danh sách có thêm user này đang kết nối
                ListUserDao.getInstance().updateUserListStringFromUserList();
                for (ClientHandler handler : clientsConnectingMap.values()) {
                    handler.send(HEADER_USERS, ListUserDao.getInstance().getUserListString().toString());
                }

                // làm mới lại list view của ServerBoxController đề hiển thị lại
                controller.getLvClients().refresh();

                // xem trong map chứa tin nhắn chờ chưa gửi có tin nhắn nào cho tên client này ko
                // thông qua key là tên client cần gửi
                // thì gửi luôn cho nó để nó mở cửa sổ chat thông báo cho người dùng
                // rồi xóa nó khỏi map vì ko còn tin nhắn chờ, ko xóa nó sẽ gửi tin nhắn liên tục
                if (alertHaveSmsMap.containsKey(userName)) {
                    String sms = alertHaveSmsMap.get(userName);
                    send(HEADER_SEND_FROM, sms);
                }

            }
        });

    }

    /**
     * gửi tin nhắn cho client theo chuỗi truyền vào có bao gồm tiêu đề ở đầu
     *
     * @param line chuỗi cần gửi
     */
    public void send(String header, String line) {
        try {
            this.dos.writeUTF(header + "/./" + line);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

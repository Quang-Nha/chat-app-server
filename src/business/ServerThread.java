package business;

import com.entity.Server;
import controller.ServerBoxController;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;

public class ServerThread implements Runnable {
    private ServerSocket serverSocket;
    ServerBoxController controller;

    /**
     * khởi tạo cho chatServer và ServerSocket
     *
     * @param chatServer là Server của ứng dụng
     */
    public ServerThread(Server chatServer, ServerBoxController controller) {
        this.controller = controller;

        // lấy ServerSocket theo port của chatServer
        try {
            this.serverSocket = new ServerSocket(chatServer.getPort());
        } catch (IOException var3) {
            var3.printStackTrace();
        }

    }

    /**
     * mỗi khi có 1 kết nối với client lấy socket của kết nối đó
     * tạo mới 1 ClientHandler quản lý nhận và gửi tin nhắn của kết nối đó và truyền cho nó socket của kết nối
     * Tạo thread mới chạy luôn hàm run của ClientHandler để chờ tin nhắn từ client
     */
    public void run() {
        try {
            while (true) {

                // tạo socket chờ kết nối với client
                Socket socket = this.serverSocket.accept();

                // tạo ClientHandler và truyền socket và client
                ClientHandler ch = new ClientHandler(socket, controller);

                new Thread(ch).start();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

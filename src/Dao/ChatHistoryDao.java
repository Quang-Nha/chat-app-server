package Dao;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ChatHistoryDao {

    public static StringBuilder readHistoryFromFile(Path path) {
        StringBuilder contentChatHistory = new StringBuilder();

        try (BufferedReader reader = Files.newBufferedReader(path)) {
            String temp = "";

            // đọc nội dung
            while ((temp = reader.readLine()) != null) {
                contentChatHistory.append(temp).append("/./");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return contentChatHistory;
    }


    public static void saveSmsToFile(Path path, String sms, boolean appen) {

        try {
            BufferedWriter writer;
            if (appen) {
                // ghi nối thêm vào file chứ ko ghi đè
                writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            } else {
                writer = Files.newBufferedWriter(path);
            }

            writer.write(sms);

            if (appen) {
                writer.newLine();
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}

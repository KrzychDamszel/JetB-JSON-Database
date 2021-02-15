package client;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.google.gson.Gson;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {
    @Parameter(names = "-t", description = "the type of request")
    String type;
    @Parameter(names = "-k", description = "the index of the cell")
    String index;
    @Parameter(names = "-v", description = "the value to save in the database, ONLY for 'set'")
    String value;
    @Parameter(names = "-in", description = "the request from that file")
    String clientFile;

    private static final String SERVER_ADDRESS = "127.0.0.1";
    private static final int SERVER_PORT = 23456;

    public static final String dbFilePath = System.getProperty("user.dir") + File.separator +
            "src" + File.separator +
            "client" + File.separator +
            "data";

    public static void main(String[] args) {

        Main main = new Main();
        JCommander.newBuilder()
                .addObject(main)
                .build()
                .parse(args);

        try (
                Socket socket = new Socket(InetAddress.getByName(SERVER_ADDRESS), SERVER_PORT);
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())
        ) {
            System.out.println("Client started!");
            String send;
            if (main.clientFile != null) {
                String fileName = dbFilePath + File.separator + main.clientFile;
                send = readFromFile(fileName);
            } else {
                Map<String, String> argsMap = new LinkedHashMap<>();
                argsMap.put("type", main.type);
                if (main.index != null) {
                    argsMap.put("key", main.index);
                }
                if (main.value != null) {
                    argsMap.put("value", main.value);
                }
                Gson gson = new Gson();
                send = gson.toJson(argsMap);
            }
            output.writeUTF(send);
            System.out.printf("Sent: %s\n", send);
            String received = input.readUTF();
            System.out.printf("Received: %s\n", received);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static String readFromFile(String clientFile) {
        try (FileInputStream fis = new FileInputStream(clientFile);
             BufferedReader br = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            return br.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }
}

package server;

import com.google.gson.*;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Main {
    private static final String ADDRESS = "127.0.0.1";
    private static final int PORT = 23456;
    static AtomicBoolean[] stopServer = {new AtomicBoolean(true)};

    public static void main(String[] args) {

        try (ServerSocket server = new ServerSocket(PORT, 50, InetAddress.getByName(ADDRESS))) {
            System.out.println("Server started!");
            ExecutorService executor = Executors.newFixedThreadPool(4);
            while (true) {
                executor.submit(new Session(server.accept(), stopServer));
                Thread.sleep(100);
                if (!stopServer[0].get()) {
                    executor.shutdown();
                    break;
                }
            }
        } catch (IOException | InterruptedException e) {
//            e.printStackTrace();
        }
    }
}

class Session implements Runnable {
    private final Socket socket;
    private final AtomicBoolean[] stopServer;

    private static final String fileName = "db.json";
    public static final String dbFilePathDir = System.getProperty("user.dir") + File.separator +
            "src" + File.separator +
            "server" + File.separator +
            "data";
    private static final String dbFilePath = dbFilePathDir + File.separator + fileName;
    private static final Path path = Paths.get(dbFilePath);

    public Session(Socket socketForClient, AtomicBoolean[] stopServer) {
        this.socket = socketForClient;
        this.stopServer = stopServer;
    }

    JsonObject databaseObject;
    Gson gson = new Gson();
    ReadWriteLock lock = new ReentrantReadWriteLock();
    Lock writeLock = lock.writeLock();
    Lock readLock = lock.readLock();

    @Override
    public synchronized void run() {
        try (
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream())
        ) {
            JsonElement databaseTree = readFromFile(path, readLock);
            databaseObject = databaseTree.getAsJsonObject();

            String inputJson = input.readUTF();
            JsonElement clientElement = JsonParser.parseString(inputJson);
            JsonObject clientObject = clientElement.getAsJsonObject();
            JsonElement taskFromClient = clientObject.get("type");
            String kind = taskFromClient.getAsString();
            Map<String, String> outputMap = new LinkedHashMap<>();

            String str = "{\"response\":\"OK\"}";
            JsonElement elem = JsonParser.parseString(str);
            JsonObject value = elem.getAsJsonObject();


            if (!"exit".equals(kind)) {
                if ("set".equals(kind)) {
                    if (!clientObject.get("key").isJsonArray()) {
                        String newKey = clientObject.get("key").getAsString();
                        JsonElement newValue = clientObject.get("value");
                        databaseObject.add(newKey, newValue);
                        writeToFile(gson, databaseObject, dbFilePath, writeLock);
                        outputMap.put("response", "OK");
                    } else {
                        JsonArray clientArray = clientObject.get("key").getAsJsonArray();
                        JsonObject tempObject = databaseObject.getAsJsonObject();
                        for (int i = 0; i < clientArray.size(); i++) {
                            if (tempObject.has(clientArray.get(i).getAsString())) {
                                tempObject = tempObject.get(clientArray.get(i).getAsString()).getAsJsonObject();
                                if (i == clientArray.size() - 2) {
                                    tempObject.add(clientArray.get(i + 1).getAsString(), clientObject.get("value"));
                                    writeToFile(gson, databaseObject, dbFilePath, writeLock);
                                    outputMap.put("response", "OK");
                                    break;
                                }
                            } else {
                                outputMap.put("response", "ERROR");
                                outputMap.put("reason", "No such key");
                            }
                        }
                    }
                }
                if ("get".equals(kind)) {
                    if (clientObject.get("key").isJsonArray()) {
                        JsonArray clientArray = clientObject.get("key").getAsJsonArray();
                        JsonObject tempDatabase = databaseObject.getAsJsonObject();
                        for (int i = 0; i < clientArray.size(); i++) {
                            if (tempDatabase.has(clientArray.get(i).getAsString())) {
                                if (tempDatabase.get(clientArray.get(i).getAsString()).isJsonObject()) {
                                    tempDatabase = tempDatabase.get(clientArray.get(i).getAsString()).getAsJsonObject();
                                    if (i == clientArray.size() - 1) {
                                        value.add("value", tempDatabase);
                                        break;
                                    }
                                } else {
                                    value.add("value", tempDatabase.get(clientArray.get(i).getAsString()));
                                    break;
                                }
                            } else {
                                value.addProperty("response", "ERROR");
                                value.addProperty("reason", "No such key");
                            }
                        }
                    } else {
                        if (databaseObject.has(clientObject.get("key").getAsString())) {
                            value.addProperty("response", "OK");
                            value.add("value", databaseObject.get(clientObject.get("key").getAsString()));
                        } else {
                            value.addProperty("response", "ERROR");
                            value.addProperty("reason", "No such key");
                        }
                    }
                }
                if ("delete".equals(kind)) {
                    if (clientObject.get("key").isJsonArray()) {
                        JsonArray clientArray = clientObject.get("key").getAsJsonArray();
                        JsonObject tempObject = databaseObject.getAsJsonObject();
                        for (int i = 0; i < clientArray.size(); i++) {
                            if (tempObject.has(clientArray.get(i).getAsString())) {
                                tempObject = tempObject.get(clientArray.get(i).getAsString()).getAsJsonObject();
                                if (i == clientArray.size() - 2) {
                                    tempObject.remove(clientArray.get(i + 1).getAsString());
                                    writeToFile(gson, databaseObject, dbFilePath, writeLock);
                                    outputMap.put("response", "OK");
                                    break;
                                }
                            } else {
                                outputMap.put("response", "ERROR");
                                outputMap.put("reason", "No such key");
                            }
                        }
                    } else {
                        if (databaseObject.has(clientObject.get("key").getAsString())) {
                            databaseObject.remove(clientObject.get("key").getAsString());
                            writeToFile(gson, databaseObject, dbFilePath, writeLock);
                            outputMap.put("response", "OK");
                        } else {
                            outputMap.put("response", "ERROR");
                            outputMap.put("reason", "No such key");
                        }
                    }
                }
            } else {
                stopServer[0] = new AtomicBoolean(false);
                outputMap.put("response", "OK");
            }
            if (!kind.equals("get")) {
                output.writeUTF(gson.toJson(outputMap));
            } else {
                output.writeUTF(value.toString());
            }
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static JsonElement readFromFile(Path path, Lock readLock) {
        JsonElement databaseTree = null;
        readLock.lock();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            databaseTree = JsonParser.parseReader(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
        readLock.unlock();
        return databaseTree;
    }

    static void writeToFile(Gson gson, JsonObject databaseObject, String dbFilePath, Lock writeLock) {
        writeLock.lock();
        try (FileOutputStream fos = new FileOutputStream(dbFilePath);
             OutputStreamWriter osr = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
            gson.toJson(databaseObject, osr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        writeLock.unlock();
    }
}
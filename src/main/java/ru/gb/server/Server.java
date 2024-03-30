package ru.gb.server;


import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.stream.Collectors;

public class Server {
    public static final int PORT = 8181;

    public static void main(String[] args) {
        final Map<String, ClientHandler> clients = new HashMap<>();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Сервер запущен на порту " + PORT);
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Подключился новый клиент: " + clientSocket);

                    PrintWriter clientOut = new PrintWriter(clientSocket.getOutputStream(), true);
                    clientOut.println("Подключение успешно. Пришлите свой идентификатор");

                    Scanner clientIn = new Scanner(clientSocket.getInputStream());
                    String clientId = clientIn.nextLine();
                    System.out.println("Идентификатор клиента " + clientSocket + ": " + clientId);

                    String allClients = clients.entrySet().stream()
                            .map(it -> "id = " + it.getKey() + ", client = " + it.getValue().getClientSocket())
                            .collect(Collectors.joining("\n"));
                    clientOut.println("Список доступных клиентов: \n" + allClients);

                    ClientHandler clientHandler = new ClientHandler(clientSocket, clients);
                    new Thread(clientHandler).start();

                    for (ClientHandler client : clients.values()) {
                        client.send("Подключился новый клиент: " + clientSocket + ", id = " + clientId);
                    }
                    clients.put(clientId, clientHandler);
                } catch (IOException e) {
                    System.err.println("Произошла ошибка при взаимодействии с клиентом: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Не удалось начать прослушивать порт " + PORT, e);
        }
    }

}

class ClientHandler implements Runnable {
    private final static List<String> commandList = new ArrayList<>();

    static {
        commandList.add("/all");
    }

    private final Socket clientSocket;
    private final PrintWriter out;
    private final Map<String, ClientHandler> clients;

    public ClientHandler(Socket clientSocket, Map<String, ClientHandler> clients) throws IOException {
        this.clientSocket = clientSocket;
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        this.clients = clients;
    }

    public Socket getClientSocket() {
        return clientSocket;
    }

    @Override
    public void run() {
        try (Scanner in = new Scanner(clientSocket.getInputStream())) {
            while (true) {
                if (clientSocket.isClosed()) {
                    System.out.println("Клиент " + clientSocket + "отключился");
                    break;
                }

                String input = in.nextLine();
                System.out.println("Получено сообщение от клиента " + clientSocket + ": " + input);

                String toClientId = null;
                if (input.startsWith("@")) {
                    String[] parts = input.split("\\s+");
                    if (parts.length > 0) {
                        toClientId = parts[0].substring(1);
                    }
                }

                Optional<String> cmd = ClientHandler.commandList.stream().filter(command -> command.equals(input)).findFirst();


                if (toClientId == null) {

                    if (cmd.isPresent()) {
                        System.out.println("This is system command");
                        if (cmd.get().equals("/all")) {
                            ClientHandler initiatorCmdClinent = clients.get(getKeyFromMap());
                            initiatorCmdClinent.send(clients.entrySet().stream()
                                    .map(it -> "id = " + it.getKey() + ", client = " + it.getValue().getClientSocket())
                                    .collect(Collectors.joining("\n")));
                        }

                    } else {
                        clients.values().forEach(it -> it.send(input));
                    }
                } else {
                    ClientHandler toClient = clients.get(toClientId);
                    if (toClient != null) {
                        toClient.send(input.replace("@" + toClientId + " ", ""));
                    } else {
                        System.err.println("Не найден клиент с идентфиикатором: " + toClientId);
                    }
                }

                out.println("Cообщение [" + input + "] получено");
                if (Objects.equals("exit", input)) {
                    System.out.println("Клиент отключился");
                    String key = getKeyFromMap();
                    clients.remove(key);
                    out.println("Клиент отключился" + key + " " + clientSocket);
                    break;
                }
            }
        } catch (IOException e) {
            System.err.println("Произошла ошибка при взаимодействии с клиентом " + clientSocket + ": " + e.getMessage());
        }

        // FIXME: При отключении клиента нужно удалять его из Map и оповещать остальных
        try {
            clientSocket.close();
        } catch (IOException e) {
            System.err.println("Ошибка при отключении клиента " + clientSocket + ": " + e.getMessage());
        }
    }

    public void send(String msg) {
        out.println(msg);
    }

    public String getKeyFromMap() {
        return clients
                .entrySet()
                .stream()
                .filter(pair -> pair.getValue().equals(this))
                .map(Map.Entry::getKey)
                .findFirst().get();
    }

}

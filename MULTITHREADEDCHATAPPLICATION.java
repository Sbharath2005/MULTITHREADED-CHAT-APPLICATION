import java.io.*;
import java.net.*;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Multithreaded chat server.
 * - Accepts multiple clients
 * - Broadcasts messages to everyone
 * - Type '/quit' in client to exit
 */
public class ChatServer {
    private final int port;
    // Thread-safe collection of connected clients
    private final Set<ClientHandler> clients = ConcurrentHashMap.newKeySet();

    public ChatServer(int port) { this.port = port; }

    public void start() {
        System.out.println("[SERVER] Listening on port " + port);
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                clients.add(handler);
                new Thread(handler, "client-" + socket.getPort()).start();
            }
        } catch (IOException e) {
            System.err.println("[SERVER] Error: " + e.getMessage());
        }
    }

    // Send message to all except the sender
    void broadcast(String msg, ClientHandler from) {
        for (ClientHandler c : clients) if (c != from) c.send(msg);
    }

    void remove(ClientHandler handler) { clients.remove(handler); }

    public static void main(String[] args) {
        int port = (args.length > 0) ? Integer.parseInt(args[0]) : 5000;
        new ChatServer(port).start();
    }

    // ------------------ Inner class ------------------
    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final ChatServer server;
        private PrintWriter out;
        private String name = "Anonymous";

        ClientHandler(Socket socket, ChatServer server) {
            this.socket = socket; this.server = server;
        }

        @Override public void run() {
            System.out.println("[SERVER] Connected: " + socket.getRemoteSocketAddress());
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"))) {
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                out.println("Welcome! Enter your name:");
                String n = in.readLine();
                if (n != null && !n.trim().isEmpty()) name = n.trim();
                out.println("Hi " + name + "! Type messages. Use /quit to exit.");
                server.broadcast("🔔 " + name + " joined the chat.", this);

                String line;
                while ((line = in.readLine()) != null) {
                    if ("/quit".equalsIgnoreCase(line.trim())) break;
                    String msg = "🗨️ " + name + ": " + line;
                    System.out.println(msg);
                    server.broadcast(msg, this);
                }
            } catch (IOException e) {
                System.err.println("[SERVER] Client error: " + e.getMessage());
            } finally {
                server.remove(this);
                server.broadcast("👋 " + name + " left the chat.", this);
                try { socket.close(); } catch (IOException ignored) {}
                System.out.println("[SERVER] Disconnected: " + name);
            }
        }

        void send(String message) { if (out != null) out.println(message); }
    }
}

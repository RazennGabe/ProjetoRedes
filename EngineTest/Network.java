package EngineTest;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.SwingUtilities;

public class Network {

    private ServerSocket serverSocket;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;

    private ConcurrentLinkedQueue<NetworkCommand> commandBuffer = new ConcurrentLinkedQueue<>();
    public boolean isServer = false;

    // Callback para mandar mensagens para o Terminal do Painel
    private ConfigPanel uiCallback;

    public Network(ConfigPanel ui) {
        this.uiCallback = ui;
    }

    // --- MODO SERVIDOR ---
    public void startServer(int port) {

        isServer = true;

        new Thread(() -> {
            try {
                uiCallback.log(">> [Rede] Iniciando servidor na porta " + port + "...");
                serverSocket = new ServerSocket(port);

                uiCallback.log(">> [Rede] Aguardando cliente...");
                socket = serverSocket.accept(); // bloqueia até alguém conectar

                setupStreams();
                uiCallback.log(">> [Rede] Cliente conectado: " + socket.getInetAddress());

                // Notify UI connected
                SwingUtilities.invokeLater(() -> uiCallback.onConnected());

                // Inicia loop de escuta
                startListening();

            } catch (IOException e) {
                uiCallback.log(">> [Erro] Servidor: " + e.getMessage());
                close();
                SwingUtilities.invokeLater(() -> uiCallback.onConnectionFailed(e.getMessage()));
            }
        }).start();
    }

    // --- MODO CLIENTE ---
    public void connect(String ip, int port) {

        isServer = false;

        new Thread(() -> {
            try {
                uiCallback.log(">> [Rede] Conectando a " + ip + ":" + port + "...");
                // use explicit Socket and connect with timeout to fail fast
                socket = new Socket();
                socket.connect(new InetSocketAddress(ip, port), 3000); // 3s timeout

                setupStreams();
                uiCallback.log(">> [Rede] Conectado ao Servidor!");

                // Notify UI connected
                SwingUtilities.invokeLater(() -> uiCallback.onConnected());

                // Inicia loop de escuta
                startListening();

            } catch (IOException e) {
                uiCallback.log(">> [Erro] Falha ao conectar: " + e.getMessage());
                close();
                SwingUtilities.invokeLater(() -> uiCallback.onConnectionFailed(e.getMessage()));
            }
        }).start();
    }


    public void send(NetworkCommand cmd) {
        if (out != null) {
            out.println(cmd.serialize());
        }
    }

    public void broadcast(NetworkCommand cmd) {
        send(cmd); // For now, 1 client. In future, iterate list of clients.
    }

    // Loop que fica ouvindo mensagens chegando
    private void startListening() {
        listenerThread = new Thread(() -> {
            try {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    try {
                        if (inputLine.equals("HANDSHAKE_INIT"))
                            continue;
                        if (inputLine.equals("PING") || inputLine.equals("PONG"))
                            continue;

                        NetworkCommand cmd = NetworkCommand.parse(inputLine);
                        if (cmd != null) {
                            commandBuffer.add(cmd);
                        }

                    } catch (Exception ex) {
                        System.out.println("Parse Error: " + ex.getMessage());
                    }
                    final String msg = inputLine;
                    // Atualiza UI na Thread do Swing
                    SwingUtilities.invokeLater(() -> {

                        // Exemplo: Se receber "PING", responde "PONG"
                        if (msg.equals("PING"))
                            sendMessage("PONG");
                    });
                }

                // Remote side closed connection (EOF)
                SwingUtilities.invokeLater(() -> {
                    uiCallback.log(">> [Rede] Conexão encerrada pelo par remoto.");
                    uiCallback.onConnectionClosed();
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    uiCallback.log(">> [Rede] Conexão encerrada.");
                    uiCallback.onConnectionClosed();
                });
            }
        });
        listenerThread.start();
    }

    // --- NEW: The Network Step ---
    // This is called by the Game Loop (main.java)
    public void processCommands(Scene scene) {
        while (!commandBuffer.isEmpty()) {
            NetworkCommand cmd = commandBuffer.poll(); // Remove from queue
            if (cmd != null) {
                // Execute logic (Spawn, Sync, etc)
                cmd.execute(scene, isServer, this);
            }
        }
    }

    // ... keep setupStreams and other helpers ...
    private void setupStreams() throws IOException {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    }

    public void sendMessage(String msg) {
        if (out != null)
            out.println(msg);
    }
  
    public synchronized void close() {
        try {
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.interrupt();
            }
            if (in != null) in.close();
        } catch (IOException ignored) {}
        if (out != null) out.close();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignored) {}

        // Notify UI that connection is closed (safe to call on EDT)
        SwingUtilities.invokeLater(() -> uiCallback.onConnectionClosed());
    }
}
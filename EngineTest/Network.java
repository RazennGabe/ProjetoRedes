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
                socket = serverSocket.accept(); // TRAVA AQUI até alguém conectar

                setupStreams();
                uiCallback.log(">> [Rede] Cliente conectado: " + socket.getInetAddress());

                // Inicia loop de escuta
                startListening();

            } catch (IOException e) {
                uiCallback.log(">> [Erro] Servidor: " + e.getMessage());
            }
        }).start();
    }

    // --- MODO CLIENTE ---
    public void connect(String ip, int port) {

        isServer = false;

        new Thread(() -> {
            try {
                uiCallback.log(">> [Rede] Conectando a " + ip + ":" + port + "...");
                socket = new Socket(ip, port);

                setupStreams();
                uiCallback.log(">> [Rede] Conectado ao Servidor!");

                // Inicia loop de escuta
                startListening();

            } catch (IOException e) {
                uiCallback.log(">> [Erro] Falha ao conectar: " + e.getMessage());
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
            } catch (IOException e) {
                uiCallback.log(">> [Rede] Conexão encerrada.");
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

}
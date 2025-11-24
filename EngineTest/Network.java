package EngineTest;

import java.io.*;
import java.net.*;
import javax.swing.SwingUtilities;

public class Network {

    private ServerSocket serverSocket;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Thread listenerThread;

    // Callback para mandar mensagens para o Terminal do Painel
    private ConfigPanel uiCallback;

    public Network(ConfigPanel ui) {
        this.uiCallback = ui;
    }

    // --- MODO SERVIDOR ---
    public void startServer(int port) {
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

    private void startListening() {
        listenerThread = new Thread(() -> {
            try {
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    final String msg = inputLine;
                    // Atualiza UI na Thread do Swing
                    SwingUtilities.invokeLater(() -> {
                        uiCallback.log(">> [Recebido]: " + msg);

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

    // Configura entrada e saída de dados
    private void setupStreams() throws IOException {
        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Manda um "Oi" assim que conecta
        sendMessage("HANDSHAKE_INIT");
    }

    // Enviar mensagem
    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    // Fecha conexões e threads com segurança
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
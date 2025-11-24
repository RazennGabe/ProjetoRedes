package EngineTest;

import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.swing.SwingUtilities;

public class Network {

    // --- TCP (Confiável) ---
    private ServerSocket serverSocket;
    private Socket tcpSocket;
    private PrintWriter out;
    private BufferedReader in;

    // --- UDP (Rápido) ---
    private DatagramSocket udpSocket;
    private InetAddress targetIP; // IP do outro lado
    private int targetPort; // Porta do outro lado

    private Thread tcpListener;
    private Thread udpListener;

    public boolean isServer = false;
    private ConfigPanel uiCallback;

    // Buffer Compartilhado (TCP e UDP jogam comandos aqui)
    private ConcurrentLinkedQueue<NetworkCommand> commandBuffer = new ConcurrentLinkedQueue<>();

    public Network(ConfigPanel ui) {
        this.uiCallback = ui;
    }

    // --- MODO SERVIDOR ---
    public void startServer(int port) {
        isServer = true;
        new Thread(() -> {
            try {
                // 1. Inicia TCP
                serverSocket = new ServerSocket(port);
                uiCallback.log(">> [TCP] Aguardando...");

                // TRAVA AQUI ATÉ CONECTAR
                tcpSocket = serverSocket.accept();

                setupTCPStreams();
                uiCallback.log(">> [TCP] Conectado!");

                // --- CORREÇÃO: Avisa a UI para abrir o jogo ---
                uiCallback.onConnected();
                // ---------------------------------------------

                startTCPListening();

                // 2. Inicia UDP
                udpSocket = new DatagramSocket(port);
                startUDPListening();
                uiCallback.log(">> [UDP] Ouvindo na porta " + port);

            } catch (IOException e) {
                uiCallback.log("Erro Server: " + e.getMessage());
                uiCallback.onConnectionFailed(e.getMessage()); // Avisa se falhar
            }
        }).start();
    }

    // --- MODO CLIENTE ---
    public void connect(String ip, int port) {
        isServer = false;
        new Thread(() -> {
            try {
                // 1. Conecta TCP
                tcpSocket = new Socket(ip, port);
                setupTCPStreams();
                uiCallback.log(">> [TCP] Conectado!");

                // --- CORREÇÃO: Avisa a UI para abrir o jogo ---
                uiCallback.onConnected();
                // ---------------------------------------------

                startTCPListening();

                // 2. Configura UDP
                targetIP = InetAddress.getByName(ip);
                targetPort = port;
                udpSocket = new DatagramSocket();
                startUDPListening();

                // 3. Handshake UDP
                sendUDP("UDP_HELLO");
                uiCallback.log(">> [UDP] Canal aberto.");

            } catch (IOException e) {
                uiCallback.log("Erro Client: " + e.getMessage());
                uiCallback.onConnectionFailed(e.getMessage()); // Avisa se falhar
            }
        }).start();
    }

    // --- ENVIAR DADOS ---

    // TCP: Para SPAWN e INPUT
    public void sendTCP(NetworkCommand cmd) {
        if (out != null)
            out.println(cmd.serialize());
    }

    // UDP: Para SYNC (Novo!)
    public void sendUDP(NetworkCommand cmd) {
        if (targetIP != null && udpSocket != null) {
            sendUDP(cmd.serialize());
        }
    }

    // Helper interno para mandar String via UDP
    private void sendUDP(String msg) {
        try {
            byte[] data = msg.getBytes();
            DatagramPacket packet = new DatagramPacket(data, data.length, targetIP, targetPort);
            udpSocket.send(packet);
        } catch (IOException e) {
            System.out.println("Erro UDP Send: " + e.getMessage());
        }
    }

    // Broadcast genérico (escolhe o melhor protocolo)
    public void broadcast(NetworkCommand cmd) {
        if (cmd.type == NetworkCommand.Type.SYNC) {
            sendUDP(cmd); // Sync vai rápido!
        } else {
            sendTCP(cmd); // O resto vai seguro!
        }
    }

    // --- RECEBIMENTO ---

    private void startTCPListening() {
        tcpListener = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    processIncomingLine(line);
                }
            } catch (IOException e) {
                uiCallback.log("TCP Caiu.");
            }
        });
        tcpListener.start();
    }

    private void startUDPListening() {
        udpListener = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024]; // 1KB buffer
                while (true) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    udpSocket.receive(packet);

                    String line = new String(packet.getData(), 0, packet.getLength());

                    // Lógica para o Servidor descobrir quem é o Cliente UDP
                    if (isServer) {
                        targetIP = packet.getAddress();
                        targetPort = packet.getPort();
                        if (line.equals("UDP_HELLO")) {
                            uiCallback.log(">> [UDP] Cliente registrado: " + targetIP + ":" + targetPort);
                            continue;
                        }
                    }

                    processIncomingLine(line);
                }
            } catch (IOException e) {
                uiCallback.log("UDP Erro: " + e.getMessage());
            }
        });
        udpListener.start();
    }

    // Processa texto (vindo de TCP ou UDP) e joga no buffer
    private void processIncomingLine(String line) {
        if (line.startsWith("HANDSHAKE") || line.equals("UDP_HELLO"))
            return;

        try {
            NetworkCommand cmd = NetworkCommand.parse(line);
            if (cmd != null) {
                commandBuffer.add(cmd);
            }
        } catch (Exception e) {
            System.out.println("Parse Erro: " + e.getMessage());
        }
    }

    // --- SETUP ---
    public void processCommands(Scene scene) {
        while (!commandBuffer.isEmpty()) {
            NetworkCommand cmd = commandBuffer.poll();
            if (cmd != null)
                cmd.execute(scene, isServer, this);
        }
    }

    private void setupTCPStreams() throws IOException {
        out = new PrintWriter(tcpSocket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream()));
    }

    public synchronized void close() {
        // 1. Parar Threads de Escuta
        if (tcpListener != null && tcpListener.isAlive()) {
            tcpListener.interrupt();
        }
        if (udpListener != null && udpListener.isAlive()) {
            udpListener.interrupt();
        }

        // 2. Fechar Sockets UDP
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }

        // 3. Fechar Streams e Sockets TCP
        try {
            if (in != null)
                in.close();
        } catch (IOException ignored) {
        }

        if (out != null)
            out.close(); // PrintWriter não lança IOException no close

        try {
            if (tcpSocket != null && !tcpSocket.isClosed())
                tcpSocket.close();
        } catch (IOException ignored) {
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed())
                serverSocket.close();
        } catch (IOException ignored) {
        }

        // 4. Limpar Estado Interno
        commandBuffer.clear();
        isServer = false;
        targetIP = null;
        targetPort = 0;

        // 5. Notificar UI (Resetar botões)
        if (uiCallback != null) {
            SwingUtilities.invokeLater(() -> {
                uiCallback.log(">> Conexão finalizada.");
                uiCallback.onConnectionClosed(); // Precisamos criar esse método no ConfigPanel
            });
        }
    }
}
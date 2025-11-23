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

    // Loop que fica ouvindo mensagens chegando
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
            } catch (IOException e) {
                uiCallback.log(">> [Rede] Conexão encerrada.");
            }
        });
        listenerThread.start();
    }
}
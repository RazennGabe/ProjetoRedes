package EngineTest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ConfigPanel extends JPanel {

    private JTextField ipField;
    private JComboBox<String> modeBox;
    private JButton connectButton;
    private JButton stopButton;
    private JTextArea terminal;
    private JLabel pingLabel;

    // Lambda hook para iniciar simulação
    private Runnable initSimulation;

    // Permite que outras classes definam a lambda
    public void setInitSimulation(Runnable initSimulation) {
        this.initSimulation = initSimulation;
    }

    // Invoca a lambda (no EDT)
    public void invokeInitSimulation() {
        if (initSimulation != null) {
            SwingUtilities.invokeLater(initSimulation);
        }
    }

    // Referência à classe de rede
    public Network network;

    public ConfigPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(10, 10, 10, 10));
        setBackground(new Color(30, 30, 30));

        JPanel topPanel = new JPanel(new GridBagLayout());
        topPanel.setBackground(new Color(30, 30, 30));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel modeLabel = createLabel("Modo:");
        String[] modes = { "Cliente", "Servidor" };
        modeBox = new JComboBox<>(modes);
        gbc.gridx = 0;
        gbc.gridy = 0;
        topPanel.add(modeLabel, gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        topPanel.add(modeBox, gbc);

        JLabel ipLabel = createLabel("IP:");
        ipField = new JTextField("127.0.0.1");
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0;
        topPanel.add(ipLabel, gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        topPanel.add(ipField, gbc);

        // Buttons panel (Connect / Stop)
        connectButton = new JButton("Conectar ao Host");
        connectButton.setBackground(new Color(0, 120, 200));
        connectButton.setForeground(Color.WHITE);

        stopButton = new JButton("Parar");
        stopButton.setBackground(new Color(180, 40, 40));
        stopButton.setForeground(Color.WHITE);
        stopButton.setEnabled(false);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        btnPanel.setBackground(new Color(30, 30, 30));
        btnPanel.add(connectButton);
        btnPanel.add(stopButton);

        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        topPanel.add(btnPanel, gbc);
        gbc.gridwidth = 1;

        pingLabel = createLabel("Status: Offline");
        pingLabel.setForeground(Color.ORANGE);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        topPanel.add(pingLabel, gbc);

        add(topPanel, BorderLayout.NORTH);

        terminal = new JTextArea();
        terminal.setEditable(false);
        terminal.setBackground(new Color(10, 10, 10));
        terminal.setForeground(new Color(0, 255, 0));
        add(new JScrollPane(terminal), BorderLayout.CENTER);

        // Inicializa a rede e lógica
        this.network = new Network(this); // Passa 'this' para o Network poder escrever no log
        setupLogic();
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.WHITE);
        return l;
    }

    private void setupLogic() {

        modeBox.addActionListener(e -> {
            String selected = (String) modeBox.getSelectedItem();

            if ("Servidor".equals(selected)) {
                ipField.setEnabled(false);
                connectButton.setText("Iniciar Servidor");
                connectButton.setBackground(new Color(0, 150, 50)); // Verde
            } else {
                ipField.setEnabled(true);
                connectButton.setText("Conectar ao Host");
                connectButton.setBackground(new Color(0, 120, 200)); // Azul
            }
        });

        connectButton.addActionListener(e -> {
            String selected = (String) modeBox.getSelectedItem();
            int port = 7777; // Porta fixa para facilitar

            // Disable connect button and enable stop while attempting/hosting/connected
            connectButton.setEnabled(false);
            stopButton.setEnabled(true);

            if ("Servidor".equals(selected)) {
                // Iniciar Server
                network.startServer(port);
                pingLabel.setText("Status: Hospedando (Porta " + port + ")");
                pingLabel.setForeground(Color.ORANGE);
            } else {
                // Conectar Client
                String ip = ipField.getText().trim();
                network.connect(ip, port);
                pingLabel.setText("Status: Tentando Conectar...");
                pingLabel.setForeground(Color.ORANGE);
            }

            // invokeInitSimulation() moved to onConnected() so simulation only starts after
            // success
        });

        stopButton.addActionListener(e -> {
            // User requested to stop server or disconnect client
            stopButton.setEnabled(false); // avoid double clicks
            pingLabel.setText("Status: Parando...");
            pingLabel.setForeground(Color.ORANGE);

            // Close network (this will trigger onConnectionClosed via Network.close())
            if (network != null) {
                network.close();
            }
        });

    }

    // UI callbacks used by Network to update controls/state
    public void onConnected() {
        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(false);
            stopButton.setEnabled(true);
            modeBox.setEnabled(false);
            ipField.setEnabled(false);
            pingLabel.setText("Status: Conectado");
            pingLabel.setForeground(Color.GREEN);

            // Start simulation only once connected
            invokeInitSimulation();
        });
    }

    public void onConnectionFailed(String reason) {
        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(true);
            stopButton.setEnabled(false);
            modeBox.setEnabled(true);
            // restore IP field state depending on mode
            String selected = (String) modeBox.getSelectedItem();
            ipField.setEnabled(!("Servidor".equals(selected)));

            pingLabel.setText("Status: Falha - " + reason);
            pingLabel.setForeground(Color.RED);

            // restore button text/color according to mode
            if ("Servidor".equals(selected)) {
                connectButton.setText("Iniciar Servidor");
                connectButton.setBackground(new Color(0, 150, 50));
            } else {
                connectButton.setText("Conectar ao Host");
                connectButton.setBackground(new Color(0, 120, 200));
            }
        });
    }

    public void onConnectionClosed() {
        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(true);
            stopButton.setEnabled(false);
            modeBox.setEnabled(true);
            // restore IP field state depending on mode
            String selected = (String) modeBox.getSelectedItem();
            ipField.setEnabled(!("Servidor".equals(selected)));

            pingLabel.setText("Status: Offline");
            pingLabel.setForeground(Color.ORANGE);

            // restore button text/color according to mode
            if ("Servidor".equals(selected)) {
                connectButton.setText("Iniciar Servidor");
                connectButton.setBackground(new Color(0, 150, 50));
            } else {
                connectButton.setText("Conectar ao Host");
                connectButton.setBackground(new Color(0, 120, 200));
            }
        });
    }

    // Método público para a classe Network escrever aqui
    public void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            terminal.append(msg + "\n");
            terminal.setCaretPosition(terminal.getDocument().getLength());
        });
    }
}
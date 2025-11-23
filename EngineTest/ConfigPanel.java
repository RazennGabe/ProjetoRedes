package EngineTest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class ConfigPanel extends JPanel {

    private JTextField ipField;
    private JComboBox<String> modeBox;
    private JButton connectButton;
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

    // --- ADICIONE ISTO ---
    public Network network; // Referência à classe de rede
    // ---------------------

    public ConfigPanel() {
        // ... (Todo o seu código de layout visual UI continua IGUAL) ...
        // ... Copie o layout do código anterior ...

        // Apenas recriando a inicialização visual rápida para contexto:
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

        connectButton = new JButton("Conectar");
        connectButton.setBackground(new Color(0, 120, 200));
        connectButton.setForeground(Color.WHITE);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        topPanel.add(connectButton, gbc);

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

        // --- INICIALIZA A REDE ---
        this.network = new Network(this); // Passa 'this' para o Network poder escrever no log

        setupLogic();
    }

    private JLabel createLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(Color.WHITE);
        return l;
    }

    // --- LÓGICA ATUALIZADA ---
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

            if ("Servidor".equals(selected)) {
                // Iniciar Server
                connectButton.setEnabled(false);
                network.startServer(port);
                pingLabel.setText("Status: Hospedando (Porta " + port + ")");
            } else {
                // Conectar Client
                String ip = ipField.getText();
                connectButton.setEnabled(false);
                network.connect(ip, port);
                pingLabel.setText("Status: Tentando Conectar...");
            }

            invokeInitSimulation();
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
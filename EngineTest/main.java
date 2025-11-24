package EngineTest;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class main extends JPanel {

    private Scene scene;
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final double SCALE = 40.0; // 40 pixels = 1 metro

    Network network;

    public main() {
        // 1. Configuração Inicial da Cena
        scene = new Scene();
        scene.deltaTime = Engine.dt;
        scene.iterations = 10; // Precisão da resolução de colisão

        // 2. Criar Objetos (Chão e Caixas)
        initDemo();

        // 3. Configurar Janela e Inputs
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.BLACK);

        // Clique do mouse para criar caixas na posição do cursor
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // Converter Tela (Pixels) -> Mundo (Física)
                double worldX = e.getX() / SCALE;
                double worldY = (HEIGHT - e.getY()) / SCALE;

                // Verifica se estamos conectados
                if (network == null) {
                    // Modo Offline (Teste local)
                    if (SwingUtilities.isLeftMouseButton(e))
                        createRandomPoly(scene, worldX, worldY);
                    else
                        createCircle(scene, worldX, worldY, 0.6);
                    return;
                }

                // --- LÓGICA DE REDE ---

                // Define o tipo baseado no botão (Esq = POLY/BOX, Dir = CIRCLE)
                String type = SwingUtilities.isRightMouseButton(e) ? "CIRCLE" : "POLY";

                // Cria o comando de Input
                NetworkCommand.InputCommand cmd = new NetworkCommand.InputCommand(type, worldX, worldY);

                if (network.isServer) {
                    cmd.execute(scene, true, network);
                } else {
                    // Cliente pede via TCP (Seguro, garante que o pedido chegue)
                    network.sendTCP(cmd);
                }
            }
        });
    }

    private void initDemo() {
        // --- Chão (Estático) ---
        RigidBody floor = createBox(scene, 10, 1, 20, 1); // x, y, w, h
        floor.invMass = 0; // Massa infinita (não se move)
        floor.invInertia = 0;
        floor.restitution = 0.2f; // Chão pouco elástico
    }

    private RigidBody createRandomPoly(Scene scene, double x, double y) {
        PolygonShape poly = new PolygonShape();
        int vertexCount = 3 + (int) (Math.random() * 5); // Entre 3 e 7 vértices
        Vector2[] vertices = new Vector2[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            double angle = i * 2 * Math.PI / vertexCount;
            double radius = 0.5 + Math.random() * 0.5; // Raio entre 0.5 e 1.0
            vertices[i] = new Vector2(radius * Math.cos(angle), radius * Math.sin(angle));
        }
        poly.set(vertices, vertexCount);

        RigidBody body = new RigidBody(poly, (int) x, (int) y);
        poly.initialize(); // Calcula massa automaticamente

        scene.bodies.add(body);
        return body;
    }

    // Helper para criar Caixas
    private RigidBody createBox(Scene scene, double x, double y, double w, double h) {
        PolygonShape poly = new PolygonShape();
        poly.setBox(w / 2.0, h / 2.0); // setBox usa half-width (metade da largura)

        RigidBody body = new RigidBody(poly, (int) x, (int) y);
        poly.initialize(); // Calcula massa automaticamente

        scene.bodies.add(body);
        return body;
    }

    // Helper para criar Círculos
    private RigidBody createCircle(Scene scene, double x, double y, double r) {
        Circle circle = new Circle((float) r);

        RigidBody body = new RigidBody(circle, (int) x, (int) y);
        circle.initialize(); // Calcula massa automaticamente

        scene.bodies.add(body);
        return body;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        // Ativa Antialiasing para ficar bonito
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Chama o render da sua engine
        scene.render(g2d, SCALE, HEIGHT);

        // Instruções na tela
        g2d.setColor(Color.WHITE);
        g2d.drawString("Clique para criar objetos", 10, 20);
        g2d.drawString("Corpos: " + scene.bodies.size(), 10, 40);
    }

    public static void openConfigWindow() {
        JFrame configFrame = new JFrame("Configuração de Rede");
        ConfigPanel panel = new ConfigPanel();

        configFrame.add(panel);
        configFrame.setSize(400, 500); // Tamanho bom para ver o terminal
        configFrame.setLocationRelativeTo(null);
        configFrame.setVisible(true);

        panel.setInitSimulation(() -> {
            // Abre a janela de simulação com a rede configurada
            openSimulationWindow(panel.network);
        });

        // Dica: Você pode fazer com que o jogo só inicie depois de configurar aqui
    }

    public static void openSimulationWindow(Network network) {
        JFrame frame = new JFrame("Java Physics Engine");
        main simulation = new main();
        frame.add(simulation);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        simulation.network = network;

        Timer timer = new Timer(16, e -> {
            if (network != null) {

                network.processCommands(simulation.scene);

                if (network.isServer) {
                    simulation.scene.step();

                    for (RigidBody b : simulation.scene.bodies) {
                        if (b.invMass != 0 && b.id != -1) {
                            NetworkCommand sync = new NetworkCommand.SyncCommand(
                                    b.id, b.position.x, b.position.y, b.angle);
                            network.broadcast(sync);
                        }
                    }
                }

            }

            simulation.repaint();
        });
        timer.start();
    }

    // --- ENTRY POINT ---
    public static void main(String[] args) {
        // ... (seu código de LookAndFeel ou setup) ...

        // Abre o Configurador
        SwingUtilities.invokeLater(() -> {
            openConfigWindow();

        });
    }
}
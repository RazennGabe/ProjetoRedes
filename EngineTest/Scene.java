
package EngineTest;

import java.util.ArrayList;
import java.util.List;
import java.awt.Graphics2D; // Necessário para o render
import java.awt.Color; // Necessário para cores de debug

public class Scene {

    List<RigidBody> bodies = new ArrayList<>();
    List<Manifold> contacts = new ArrayList<>();

    float deltaTime;
    float iterations;

    // Contador Global de IDs (Só o Servidor usa isso)
    private int nextIdCounter = 0;

    // Acceleration
    // F = mA
    // => A = F * 1/m

    // Explicit Euler
    // x += v * dt
    // v += (1/m * F) * dt

    // Semi-Implicit (Symplectic) Euler
    // v += (1/m * F) * dt
    // x += v * dt

    // Método para encontrar um corpo pelo ID (O(n) - simples para agora)
    public RigidBody findBodyById(int id) {
        for (RigidBody b : bodies) {
            if (b.id == id)
                return b;
        }
        return null;
    }

    // Método para registrar um corpo novo (Servidor chama isso)
    public void addBodyServer(RigidBody b) {
        b.id = nextIdCounter++; // Atribui ID único
        bodies.add(b);
    }

    void IntegrateForces(RigidBody b, double dt) {
        if (b.invMass == 0.0)
            return;

        // a = F * invMass + GRAVIDADE
        double ax = b.force.x * b.invMass + Engine.gravity.x;
        double ay = b.force.y * b.invMass + Engine.gravity.y;

        b.velocity.x += ax * dt;
        b.velocity.y += ay * dt;

        // Angular
        b.angularVelocity += (b.torque * b.invInertia * dt);
    }

    void IntegrateVelocity(RigidBody b, double dt) {
        if (b.invMass == 0.0)
            return;

        // x += v * dt
        b.position.x += b.velocity.x * dt;
        b.position.y += b.velocity.y * dt;

        // Angular
        b.angle += b.angularVelocity * dt;

        b.shape.setOrient(b.angle);
        IntegrateForces(b, dt);
    }

    public void step() {
        contacts.clear();

        // Manifold Generation
        // Bruteforce Collision Detection O(n^2)
        for (int i = 0; i < bodies.size(); i++) {
            RigidBody A = bodies.get(i);

            for (int j = i + 1; j < bodies.size(); j++) {
                RigidBody B = bodies.get(j);

                if (A.invMass == 0 && B.invMass == 0)
                    continue;

                Manifold m = new Manifold(A, B);

                m.Solve();

                if (m.contactCount > 0) {
                    contacts.add(m);
                }
            }
        }

        // Integrate Forces
        for (int i = 0; i < bodies.size(); i++) {
            IntegrateForces(bodies.get(i), deltaTime);
        }

        // Initialize collisions
        for (int i = 0; i < contacts.size(); i++) {
            Manifold m = contacts.get(i);

            m.Initialize();
        }

        // Solve collisions
        for (int i = 0; i < iterations; i++) {
            for (int j = 0; j < contacts.size(); j++) {
                Manifold m = contacts.get(j);
                m.ApplyImpulse();
            }
        }

        // Integrate Velocities
        for (int i = 0; i < bodies.size(); i++) {
            IntegrateVelocity(bodies.get(i), deltaTime);
        }

        // Correct Positions
        for (int i = 0; i < contacts.size(); i++) {
            Manifold m = contacts.get(i);
            m.PositionalCorrection();
        }

        // Clear all forces
        for (int i = 0; i < bodies.size(); i++) {
            RigidBody b = bodies.get(i);
            b.force = new Vector2(0, 0);
            b.torque = 0;
        }

        String estadoCena = "SCENE:";
        for (RigidBody b : bodies) {
            estadoCena += "BODY:" + b.position.x + ":" + b.position.y + ";";
        }

    }

    public void render(Graphics2D g, double scale, int screenHeight) {

        // 1. Desenhar todos os Corpos
        for (RigidBody b : bodies) {
            // Delegamos o desenho para o Shape (que você já configurou)
            // Passamos o contexto gráfico, o zoom (scale) e a altura para inverter o Y
            if (b.shape != null) {
                b.shape.draw(g, scale, screenHeight);
            }
        }

        // 2. Desenhar Informações de Colisão (Debug Draw)
        // Isso é útil para ver onde os contatos estão acontecendo
        for (Manifold m : contacts) {

            // Cor dos pontos de contato: Vermelho
            g.setColor(Color.RED);
            for (int i = 0; i < m.contactCount; i++) {
                Vector2 c = m.contacts[i];

                // Converter Mundo -> Tela (Manual, pois Manifold não é um Shape)
                int cx = (int) (c.x * scale);
                int cy = (int) (screenHeight - (c.y * scale));

                // Desenha um quadradinho ou ponto
                g.fillRect(cx - 2, cy - 2, 4, 4);
            }

            // Cor das Normais: Verde
            g.setColor(Color.GREEN);
            Vector2 n = m.normal;
            for (int i = 0; i < m.contactCount; i++) {
                Vector2 c = m.contacts[i];

                int cx = (int) (c.x * scale);
                int cy = (int) (screenHeight - (c.y * scale));

                // Projetar a linha da normal
                // n * 0.75f (tamanho visual da linha)
                double lineLen = 0.75 * scale;

                int nx = (int) (cx + (n.x * lineLen));
                // Subtrai no Y porque a tela é invertida
                int ny = (int) (cy - (n.y * lineLen));

                g.drawLine(cx, cy, nx, ny);
            }
        }
    }
}
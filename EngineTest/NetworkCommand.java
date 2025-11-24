package EngineTest;

public abstract class NetworkCommand {

    public enum Type {
        SPAWN, SYNC, INPUT
    }

    public Type type;

    public NetworkCommand(Type type) {
        this.type = type;
    }

    // Converts the object to "TYPE:DATA:DATA"
    public abstract String serialize();

    // What happens when this command runs?
    public abstract void execute(Scene scene, boolean isServer, Network net);

    // --- FACTORY: Converts String back to Object ---
    public static NetworkCommand parse(String line) {
        String[] parts = line.split(":");
        Type t = Type.valueOf(parts[0]);

        switch (t) {
            case SPAWN:
                return new SpawnCommand(parts);
            case SYNC:
                return new SyncCommand(parts);
            case INPUT:
                return new InputCommand(parts);
            default:
                return null;
        }
    }

    // ==========================================
    // COMMAND: SPAWN (Server tells Client to create object)
    // ==========================================
    public static class SpawnCommand extends NetworkCommand {
        public int id;
        public String shapeType;
        public double x, y, size;
        public Vector2[] vertices; // NOVO: Guarda os vértices se for POLY

        // Construtor servidor (geração interna)
        public SpawnCommand(int id, String shapeType, double x, double y, double size, Vector2[] vertices) {
            super(Type.SPAWN);
            this.id = id;
            this.shapeType = shapeType;
            this.x = x;
            this.y = y;
            this.size = size;
            this.vertices = vertices;
        }

        // Construtor cliente (parse da string)
        public SpawnCommand(String[] p) {
            super(Type.SPAWN);
            this.id = Integer.parseInt(p[1]);
            this.shapeType = p[2];
            this.x = Double.parseDouble(p[3].replace(",", "."));
            this.y = Double.parseDouble(p[4].replace(",", "."));
            this.size = Double.parseDouble(p[5].replace(",", "."));

            // NOVO: Se for POLY, lemos os vértices extras
            if (shapeType.equals("POLY")) {
                int vCount = Integer.parseInt(p[6]);
                this.vertices = new Vector2[vCount];
                int index = 7; // Começa a ler depois do contador
                for (int i = 0; i < vCount; i++) {
                    double vx = Double.parseDouble(p[index++].replace(",", "."));
                    double vy = Double.parseDouble(p[index++].replace(",", "."));
                    this.vertices[i] = new Vector2(vx, vy);
                }
            }
        }

        @Override
        public String serialize() {
            // Base: SPAWN:ID:TYPE:X:Y:SIZE
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(java.util.Locale.US, "SPAWN:%d:%s:%.2f:%.2f:%.2f", id, shapeType, x, y, size));

            // Extensão: :COUNT:VX:VY:VX:VY...
            if (shapeType.equals("POLY") && vertices != null) {
                sb.append(":").append(vertices.length);
                for (Vector2 v : vertices) {
                    sb.append(String.format(java.util.Locale.US, ":%.2f:%.2f", v.x, v.y));
                }
            }
            return sb.toString();
        }

        @Override
        public void execute(Scene scene, boolean isServer, Network net) {
            if (isServer)
                return;

            RigidBody b;
            if (shapeType.equals("CIRCLE")) {
                Circle c = new Circle((float) size);
                b = new RigidBody(c, (int) x, (int) y);
                c.initialize();
            } else {
                PolygonShape poly = new PolygonShape();
                if (vertices != null) {
                    // Cliente usa os vértices exatos que o servidor mandou
                    poly.set(vertices, vertices.length);
                } else {
                    poly.setBox(size / 2.0, size / 2.0); // Fallback
                }
                b = new RigidBody(poly, (int) x, (int) y);
                poly.initialize();
            }

            b.id = id;
            b.invMass = 0;
            scene.bodies.add(b);
        }
    }

    // ==========================================
    // COMMAND: SYNC (Server updates position)
    // ==========================================
    public static class SyncCommand extends NetworkCommand {
        public int id;
        public double x, y;
        public float angle;

        public SyncCommand(int id, double x, double y, float angle) {
            super(Type.SYNC);
            this.id = id;
            this.x = x;
            this.y = y;
            this.angle = angle;
        }

        public SyncCommand(String[] p) {
            super(Type.SYNC);
            this.id = Integer.parseInt(p[1]);
            this.x = Double.parseDouble(p[2].replace(",", "."));
            this.y = Double.parseDouble(p[3].replace(",", "."));
            this.angle = Float.parseFloat(p[4].replace(",", "."));
        }

        @Override
        public String serialize() {
            return String.format("SYNC:%d:%.2f:%.2f:%.2f", id, x, y, angle);
        }

        @Override
        public void execute(Scene scene, boolean isServer, Network net) {
            if (isServer)
                return; // Server is the authority, ignore syncs

            RigidBody b = scene.findBodyById(id);
            if (b != null) {
                b.position.set(x, y);
                b.setAngle(angle);
                b.velocity.set(0, 0); // Prevent client prediction fighting
            }
        }
    }

    // ==========================================
    // COMMAND: INPUT (Client requests creation)
    // ==========================================
    public static class InputCommand extends NetworkCommand {
        public String shapeType;
        public double x, y;

        public InputCommand(String shapeType, double x, double y) {
            super(Type.INPUT);
            this.shapeType = shapeType;
            this.x = x;
            this.y = y;
        }

        public InputCommand(String[] p) {
            super(Type.INPUT);
            this.shapeType = p[1];
            this.x = Double.parseDouble(p[2].replace(",", "."));
            this.y = Double.parseDouble(p[3].replace(",", "."));
        }

        @Override
        public String serialize() {
            return String.format(java.util.Locale.US, "INPUT:%s:%.2f:%.2f", shapeType, x, y);
        }

        @Override
        public void execute(Scene scene, boolean isServer, Network net) {
            if (!isServer) return; // Só o servidor processa INPUT

            RigidBody b;
            double size = 1.0;
            Vector2[] generatedVertices = null;

            if (shapeType.equals("CIRCLE")) {
                Circle c = new Circle(0.6f);
                b = new RigidBody(c, (int) x, (int) y);
                c.initialize();
                size = 0.6f;
            } else {
                // LOGICA DE ALEATORIEDADE (Rodando apenas no Servidor)
                PolygonShape poly = new PolygonShape();
                
                // Gera vértices aleatórios
                int count = (int)(Math.random() * 3) + 3; // 3 a 5 vértices
                generatedVertices = new Vector2[count];
                
                for(int i = 0; i < count; i++) {
                    double angle = Math.random() * Math.PI * 2;
                    double radius = Math.random() * 0.5 + 0.5;
                    generatedVertices[i] = new Vector2(
                        Math.cos(angle) * radius, 
                        Math.sin(angle) * radius
                    );
                }
                
                // O método .set calcula o Convex Hull e organiza os vértices
                poly.set(generatedVertices, count);
                
                // Importante: Pegar os vértices FINAIS processados pelo Convex Hull
                // (O Hull pode reduzir a contagem de vértices se algum ficar dentro)
                generatedVertices = new Vector2[poly.m_vertexCount];
                for(int i=0; i<poly.m_vertexCount; i++) {
                    // Copia para garantir que o cliente receba a forma final limpa
                    generatedVertices[i] = new Vector2(poly.m_vertices[i].x, poly.m_vertices[i].y);
                }
                
                b = new RigidBody(poly, (int) x, (int) y);
                poly.initialize();
            }

            scene.addBodyServer(b);

            // Broadcast: Manda o ID e também os VÉRTICES gerados
            SpawnCommand spawnCmd = new SpawnCommand(b.id, shapeType, x, y, size, generatedVertices);
            net.broadcast(spawnCmd);
        }
    }
}
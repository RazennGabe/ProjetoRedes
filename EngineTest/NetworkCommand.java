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
        public String shapeType; // "CIRCLE" or "BOX"
        public double x, y, size;

        // Constructor for creating internally
        public SpawnCommand(int id, String shapeType, double x, double y, double size) {
            super(Type.SPAWN);
            this.id = id;
            this.shapeType = shapeType;
            this.x = x;
            this.y = y;
            this.size = size;
        }

        // Constructor for parsing from network
        public SpawnCommand(String[] p) {
            super(Type.SPAWN);
            this.id = Integer.parseInt(p[1]);
            this.shapeType = p[2];
            this.x = Double.parseDouble(p[3]);
            this.y = Double.parseDouble(p[4]);
            this.size = Double.parseDouble(p[5]);
        }

        @Override
        public String serialize() {
            return String.format("SPAWN:%d:%s:%.2f:%.2f:%.2f", id, shapeType, x, y, size);
        }

        @Override
        public void execute(Scene scene, boolean isServer, Network net) {
            if (isServer)
                return; // Server already has this object

            // Logic to create object on CLIENT
            RigidBody b;
            if (shapeType.equals("CIRCLE")) {
                Circle c = new Circle((float) size);
                b = new RigidBody(c, (int) x, (int) y);
                c.initialize();
            } else {
                PolygonShape poly = new PolygonShape();
                poly.setBox(size / 2.0, size / 2.0);
                b = new RigidBody(poly, (int) x, (int) y);
                poly.initialize();
            }

            b.id = id;
            b.invMass = 0; // Client objects are ghosts (infinite mass)
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
            this.x = Double.parseDouble(p[2]);
            this.y = Double.parseDouble(p[3]);
        }

        @Override
        public String serialize() {
            return String.format("INPUT:%s:%.2f:%.2f", shapeType, x, y);
        }

        @Override
        public void execute(Scene scene, boolean isServer, Network net) {
            if (!isServer)
                return; // Clients don't process inputs from others directly

            // SERVER LOGIC: Create the real object
            RigidBody b;
            double size = 1.0; // Default size

            // Logic copied from main.createBox/Circle but adapted
            if (shapeType.equals("CIRCLE")) {
                Circle c = new Circle(0.6f);
                b = new RigidBody(c, (int) x, (int) y);
                c.initialize();
            } else {
                PolygonShape poly = new PolygonShape();
                poly.setBox(0.5, 0.5);
                b = new RigidBody(poly, (int) x, (int) y);
                poly.initialize();
            }

            // Assign ID and add to Server Scene
            scene.addBodyServer(b);

            // Broadcast SPAWN to all clients so they see it too
            SpawnCommand spawnCmd = new SpawnCommand(b.id, shapeType, x, y, size);
            net.broadcast(spawnCmd);
        }
    }
}
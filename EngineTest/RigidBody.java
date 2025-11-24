package EngineTest;

class RigidBody {
    public int id = -1;
    Shape shape;

    // Linear Components
    Vector2 position;
    Vector2 velocity;

    // Angular Components\
    float angle; // in radians
    float angularVelocity;
    float torque;

    Vector2 force;

    // Mass Properties
    float mass;
    float invMass; // Inverse Mass
    float inertia; // Moment of Inertia
    float invInertia; // Inverse Moment of Inertia

    float staticFriction;
    float dynamicFriction;
    float restitution; // Bounciness

    public RigidBody(Shape shape, int x, int y) {
        this.shape = shape;
        this.position = new Vector2(x, y);
        this.velocity = new Vector2(0, 0);
        this.force = new Vector2(0, 0);
        this.shape.body = this; // Linka o shape de volta ao corpo

        // Valores padrão
        this.staticFriction = 0.5f;
        this.dynamicFriction = 0.3f;
        this.restitution = 0.5f;
    }

    void ApplyImpulse(Vector2 impulse, Vector2 contactVector) {
        if (invMass == 0)
            return;

        // Linear velocity
        velocity.addI(Vector2.Multiply(impulse, invMass));

        // Angular velocity
        angularVelocity += invInertia * Vector2.Cross(contactVector, impulse);
    }

    void setAngle(float angle) {
        this.angle = angle;
        this.shape.setOrient(angle);
    }

}
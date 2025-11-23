package EngineTest;

public class Manifold {

        RigidBody A, B;

        double penetration; // Depth of penetration from collision
        Vector2 normal; // From A to B
        Vector2[] contacts; // Points of contact during collision
        int contactCount; // Number of contacts

        float e; // Restitution
        float sf; // Static Friction
        float df; // Dynamic Friction

        Manifold(RigidBody a, RigidBody b) {
                this.A = a;
                this.B = b;

                normal = new Vector2(0, 0);

                contacts = new Vector2[2];
                contacts[0] = new Vector2();
                contacts[1] = new Vector2();
                contactCount = 0;
        }

        private static final CollisionCallback[][] dispatch = {
                        // LINHA 0: Se o Objeto A for CIRCLE
                        {
                                        Colision::circleToCircle, // vs CIRCLE (0)
                                        Colision::circleToPolygon // vs BOX (1)
                        },

                        // LINHA 1: Se o Objeto A for BOX
                        {
                                        Colision::polygonToCircle, // vs CIRCLE (0)
                                        Colision::polygonToPolygon // vs BOX (1)
                        }
        };

        void Solve() {
                // Chama a função correta usando a tabela de despacho
                dispatch[A.shape.getType().ordinal()][B.shape.getType().ordinal()].resolve(this, A, B);
        }

        void Initialize() {
                // Combine Restitution
                e = Math.min(A.restitution, B.restitution);

                // Combine Friction
                sf = (float) Math.sqrt(A.staticFriction * B.staticFriction);
                df = (float) Math.sqrt(A.dynamicFriction * B.dynamicFriction);

                for (int i = 0; i < contactCount; i++) {
                        // Calculate radii from COM to contact
                        Vector2 ra = contacts[i].subtract(A.position);
                        Vector2 rb = contacts[i].subtract(B.position);

                        // Relative velocity
                        Vector2 rv = Vector2.Add(
                                        Vector2.Add(B.velocity, Vector2.Cross(B.angularVelocity, rb)),
                                        Vector2.Negate(Vector2.Add(A.velocity, Vector2.Cross(A.angularVelocity, ra))));

                        // Determine if we should perform a resting collision or not
                        if (rv.LengthSquared() < (Engine.gravity.multiply(Engine.dt).LengthSquared()
                                        + Engine.EPSILON)) {
                                e = 0.0f;
                        }
                }
        }

        void ApplyImpulse() {
                // If both objects have infinite mass, do nothing
                if (A.invMass + B.invMass == 0) {
                        InfiniteMassCorrection();
                        return;
                }

                for (int i = 0; i < contactCount; i++) {
                        // Calculate radii from COM to contact
                        Vector2 ra = contacts[i].subtract(A.position);
                        Vector2 rb = contacts[i].subtract(B.position);

                        // Relative velocity
                        Vector2 rv = Vector2.Add(
                                        Vector2.Add(B.velocity, Vector2.Cross(B.angularVelocity, rb)),
                                        Vector2.Negate(Vector2.Add(A.velocity, Vector2.Cross(A.angularVelocity, ra))));

                        // Relative velocity along the normal
                        double contactVel = Vector2.Dot(rv, normal);

                        // Do not resolve if velocities are separating
                        if (contactVel > 0)
                                return;

                        double raCrossN = Vector2.Cross(ra, normal);
                        double rbCrossN = Vector2.Cross(rb, normal);
                        double invMassSum = A.invMass + B.invMass + (raCrossN * raCrossN) * A.invInertia
                                        + (rbCrossN * rbCrossN) * B.invInertia;

                        // Calculate impulse scalar
                        double j = -(1.0 + e) * contactVel;
                        j /= invMassSum;
                        j /= contactCount;

                        // Apply impulse
                        Vector2 impulse = Vector2.Multiply(normal, j);
                        A.ApplyImpulse(Vector2.Negate(impulse), ra);
                        B.ApplyImpulse(impulse, rb);

                        // Friction Impulse
                        rv = Vector2.Add(
                                        Vector2.Add(B.velocity, Vector2.Cross(B.angularVelocity, rb)),
                                        Vector2.Negate(Vector2.Add(A.velocity, Vector2.Cross(A.angularVelocity, ra))));

                        Vector2 t = rv.subtract(Vector2.Multiply(normal, Vector2.Dot(rv, normal)));
                        t = t.normalize();

                        // j tangent magnitude
                        double jt = -Vector2.Dot(rv, t);
                        jt /= invMassSum;
                        jt /= contactCount;

                        // Don't apply tiny friction impulses
                        if (jt == 0)
                                return;

                        // Coulumb's law
                        Vector2 tangentImpulse;
                        if (Math.abs(jt) < j * sf) {
                                tangentImpulse = Vector2.Multiply(t, jt);
                        } else {
                                tangentImpulse = Vector2.Multiply(t, -j * df);
                        }

                        // Apply friction impulse
                        A.ApplyImpulse(Vector2.Negate(tangentImpulse), ra);
                        B.ApplyImpulse(tangentImpulse, rb);
                }
        }

        void PositionalCorrection() {
                final double k_slop = 0.05; // Penetração permitida
                final double percent = 0.4; // Correção percentual
                Vector2 correction = Vector2.Multiply(normal,
                                Math.max(penetration - k_slop, 0.0) / (A.invMass + B.invMass) * percent);
                A.position = A.position.subtract(Vector2.Multiply(correction, A.invMass));
                B.position = B.position.add(Vector2.Multiply(correction, B.invMass));
        }

        void InfiniteMassCorrection() {
                A.velocity = new Vector2(0, 0);
                B.velocity = new Vector2(0, 0);
        }

}

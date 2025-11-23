package EngineTest;

public class Colision {
    // ===========================================================
    // CIRCLE VS CIRCLE
    // ===========================================================
    public static void circleToCircle(Manifold m, RigidBody a, RigidBody b) {
        Circle A = (Circle) a.shape;
        Circle B = (Circle) b.shape;

        // Calculate translational vector, which is normal
        Vector2 normal = b.position.subtract(a.position);

        double dist_sqr = normal.LengthSquared();
        double radius = A.radius + B.radius;

        // Not in contact
        if (dist_sqr >= radius * radius) {
            m.contactCount = 0;
            return;
        }

        double distance = Math.sqrt(dist_sqr);

        m.contactCount = 1;

        if (distance == 0.0f) {
            // Caso Raro: Círculos exatamente na mesma posição
            m.penetration = A.radius;
            m.normal.set(1.0f, 0.0f);
            m.contacts[0].set(a.position);
        } else {
            m.penetration = radius - distance;

            // m->normal = normal / distance
            m.normal.set(normal.x / distance, normal.y / distance);

            // m->contacts[0] = m->normal * A->radius + a->position;
            Vector2 contactPoint = m.normal.multiply(A.radius).add(a.position);
            m.contacts[0].set(contactPoint);
        }
    }

    // ===========================================================
    // CIRCLE VS POLYGON
    // ===========================================================
    public static void circleToPolygon(Manifold m, RigidBody a, RigidBody b) {
        Circle A = (Circle) a.shape;
        PolygonShape B = (PolygonShape) b.shape;

        m.contactCount = 0;

        // 1. Transform circle center to Polygon model space
        // A mágica acontece aqui: Em vez de girar o polígono, giramos o círculo "ao
        // contrário"
        // para que ele fique alinhado com o polígono local (eixo 0,0).
        // Vector2 center = B->u.Transpose( ) * (center - b->position);

        Vector2 center = a.position.subtract(b.position);
        center = B.u.transpose().mul(center);

        // 2. Find edge with minimum penetration
        double separation = -Double.MAX_VALUE;
        int faceNormal = 0;

        for (int i = 0; i < B.m_vertexCount; ++i) {
            // s = Dot( n, center - v )
            double s = B.m_normals[i].dot(center.subtract(B.m_vertices[i]));

            if (s > A.radius) {
                return; // Separated
            }

            if (s > separation) {
                separation = s;
                faceNormal = i;
            }
        }

        // 3. Grab face's vertices
        Vector2 v1 = B.m_vertices[faceNormal];
        int i2 = (faceNormal + 1 < B.m_vertexCount) ? faceNormal + 1 : 0;
        Vector2 v2 = B.m_vertices[i2];

        // 4. Check to see if center is within polygon
        if (separation < Engine.EPSILON) {
            m.contactCount = 1;

            // Normal deve apontar do Poligono para o Círculo.
            // No espaço local é a normal da face. Precisamos rotacionar para o Mundo.
            // m->normal = -(B->u * B->m_normals[faceNormal]);
            Vector2 polyNormalWorld = B.u.mul(B.m_normals[faceNormal]);
            m.normal.set(-polyNormalWorld.x, -polyNormalWorld.y);

            // m->contacts[0] = m->normal * A->radius + a->position;
            m.contacts[0].set(m.normal.multiply(A.radius).add(a.position));

            m.penetration = A.radius;
            return;
        }

        // 5. Determine which voronoi region of the edge center of circle lies within
        double dot1 = center.subtract(v1).dot(v2.subtract(v1)); // Dot( center - v1, v2 - v1 )
        double dot2 = center.subtract(v2).dot(v1.subtract(v2)); // Dot( center - v2, v1 - v2 )

        m.penetration = A.radius - separation;

        // --- Região 1: Closest to v1 (Colisão com o Canto V1) ---
        if (dot1 <= 0.0f) {
            if (center.distSqr(v1) > A.radius * A.radius) {
                return;
            }

            m.contactCount = 1;

            // Calcular normal: v1 -> center
            Vector2 n = v1.subtract(center);

            // Transformar para Mundo
            n = B.u.mul(n);
            n.normalize();
            m.normal.set(n);

            // Ponto de contato é o próprio V1 (transformado para mundo)
            // v1 = B->u * v1 + b->position;
            Vector2 v1World = B.u.mul(v1).add(b.position);
            m.contacts[0].set(v1World);
        }

        // --- Região 2: Closest to v2 (Colisão com o Canto V2) ---
        else if (dot2 <= 0.0f) {
            if (center.distSqr(v2) > A.radius * A.radius) {
                return;
            }

            m.contactCount = 1;

            // Calcular normal: v2 -> center
            Vector2 n = v2.subtract(center);

            // Ponto de contato é o próprio V2 (transformado para mundo)
            // v2 = B->u * v2 + b->position;
            Vector2 v2World = B.u.mul(v2).add(b.position);
            m.contacts[0].set(v2World);

            // Transformar Normal para Mundo
            n = B.u.mul(n);
            n.normalize();
            m.normal.set(n);
        }

        // --- Região 3: Closest to face (Colisão com a Aresta) ---
        else {
            Vector2 n = B.m_normals[faceNormal];

            if (center.subtract(v1).dot(n) > A.radius) {
                return;
            }

            // Transformar Normal para Mundo
            n = B.u.mul(n);

            // A normal no manifold deve apontar de A para B (ou vice versa, dependendo da
            // convenção).
            // O código original usa m->normal = -n
            m.normal.set(-n.x, -n.y);

            // Ponto de contato na superfície do círculo
            // m->contacts[0] = m->normal * A->radius + a->position;
            m.contacts[0].set(m.normal.multiply(A.radius).add(a.position));

            m.contactCount = 1;
        }
    }

    // ===========================================================
    // POLYGON VS CIRCLE (Apenas inverte a chamada)
    // ===========================================================
    public static void polygonToCircle(Manifold m, RigidBody a, RigidBody b) {
        circleToPolygon(m, b, a);

        // Inverter a normal porque trocamos A e B de lugar
        m.normal.set(-m.normal.x, -m.normal.y);
    }

    // ===========================================================
    // POLYGON VS POLYGON
    // ===========================================================
    public static void polygonToPolygon(Manifold m, RigidBody a, RigidBody b) {
        PolygonShape A = (PolygonShape) a.shape;
        PolygonShape B = (PolygonShape) b.shape;

        m.contactCount = 0;

        // 1. Testar separação nos eixos de A (Face de A empurrando B)
        float[] penetrationA = { 0 }; // Hack para passar float por referência
        int faceA = findAxisLeastPenetration(faceIndexRef, A, B, penetrationA);
        if (faceA == -1)
            return; // Separado!

        // 2. Testar separação nos eixos de B (Face de B empurrando A)
        float[] penetrationB = { 0 };
        int faceB = findAxisLeastPenetration(faceIndexRef, B, A, penetrationB);
        if (faceB == -1)
            return; // Separado!

        int referenceIndex;
        boolean flip; // Indica se invertemos A e B
        PolygonShape RefPoly; // Quem tem a face de referência
        PolygonShape IncPoly; // Quem está batendo (Incidente)

        // 3. Decidir qual face é a de Referência (a que tem MENOS penetração)
        // Adicionamos um viés (bias) para evitar que a referência fique trocando
        // loucamente (flicker) quando as penetrações são quase iguais.
        final float k_bias = 0.95f;
        final float k_rel_tol = 0.01f;

        if (penetrationA[0] >= penetrationB[0] * k_bias + penetrationA[0] * k_rel_tol) {
            RefPoly = A;
            IncPoly = B;
            referenceIndex = faceA;
            flip = false;
        } else {
            RefPoly = B;
            IncPoly = A;
            referenceIndex = faceB;
            flip = true;
        }

        // 4. Encontrar a Face Incidente (a face do outro polígono mais "anti-paralela")
        Vector2[] incidentFace = new Vector2[2];

        // Normal da face de referência no MUNDO
        Vector2 referenceNormalLocal = RefPoly.m_normals[referenceIndex];
        Vector2 referenceNormalWorld = RefPoly.u.mul(referenceNormalLocal);

        findIncidentFace(incidentFace, RefPoly, IncPoly, referenceNormalWorld);

        // 5. Configuração para o Clipping (Corte)
        // Precisamos dos vértices da face de referência no Mundo
        Vector2 v1 = RefPoly.m_vertices[referenceIndex];
        int i2 = (referenceIndex + 1 < RefPoly.m_vertexCount) ? referenceIndex + 1 : 0;
        Vector2 v2 = RefPoly.m_vertices[i2];

        // Transformar para Mundo
        v1 = RefPoly.u.mul(v1).add(RefPoly.body.position);
        v2 = RefPoly.u.mul(v2).add(RefPoly.body.position);

        // Vetor Tangente (lado da face)
        Vector2 sidePlaneNormal = v2.subtract(v1);
        sidePlaneNormal.normalize();

        // Vetor Ortogonal ao lado (para fazer o clip das laterais)
        // É basicamente a tangente rotacionada 90 graus? Não, podemos usar float
        // offset.
        // O algoritmo Sutherland-Hodgman usa distâncias para planos.

        // Distância do plano à origem (produto escalar)
        double refFaceOffset = referenceNormalWorld.dot(v1);
        double negSide = -sidePlaneNormal.dot(v1);
        double posSide = sidePlaneNormal.dot(v2);

        // 6. Clip (Cortar) a face incidente contra as laterais da face de referência
        // Passo 1: Cortar contra a tangente negativa (lado esquerdo)
        if (clip(sidePlaneNormal.multiply(-1), negSide, incidentFace) < 2)
            return;

        // Passo 2: Cortar contra a tangente positiva (lado direito)
        if (clip(sidePlaneNormal, posSide, incidentFace) < 2)
            return;

        // 7. Considerar apenas pontos que estão "abaixo" da face de referência
        // (penetração real)
        // Inverter normal se houve flip, para garantir que aponta sempre de A para B
        m.normal = flip ? referenceNormalWorld.multiply(-1) : referenceNormalWorld;

        int cp = 0; // Contact points count
        double penetration = 0; // Acumulador para média (opcional) ou usar o maior

        for (int i = 0; i < 2; i++) {
            double separation = referenceNormalWorld.dot(incidentFace[i]) - refFaceOffset;

            if (separation <= 0.0f) {
                m.contacts[cp].set(incidentFace[i]);
                m.penetration = (float) -separation; // Guarda a penetração deste ponto
                cp++;
            }
        }

        m.contactCount = cp;

        // Nota: Em colisões complexas, a penetração armazenada no manifold
        // geralmente é a máxima ou a média. O código original sobrescreve.
    }

    // -------------------------------------------------------------------
    // HELPER: Encontrar eixo de menor penetração (SAT)
    // Retorna o índice da face ou -1 se separado
    // -------------------------------------------------------------------
    // Usamos um array int[] de tamanho 1 apenas para "simular" um ponteiro,
    // mas aqui nem precisei passar o índice, apenas retorno ele.
    // O array float[] penetration serve para retornar o valor da penetração.
    private static int[] faceIndexRef = { 0 }; // Placeholder se precisasse passar por ref

    private static int findAxisLeastPenetration(int[] faceIndex, PolygonShape A, PolygonShape B,
            float[] bestPenetration) {
        double bestDistance = -Double.MAX_VALUE;
        int bestIndex = -1;

        for (int i = 0; i < A.m_vertexCount; ++i) {
            // Pegar a normal da face de A no espaço do MUNDO
            Vector2 n = A.m_normals[i];
            Vector2 nw = A.u.mul(n);

            // Transformar a face de A para o mundo também (para saber o offset)
            // Mas podemos fazer a matemática relativa.
            // Estratégia: Projetar o "Support Point" de B na normal de A.

            // Matriz de rotação do B transposta (inversa)
            Mat2 buT = B.u.transpose();

            // Converter a normal de A (Mundo) para o espaço local de B
            Vector2 nLocalB = buT.mul(nw);

            // Pegar o ponto extremo de B na direção oposta da normal
            // (Support Point é o ponto mais "fundo" na direção da colisão)
            Vector2 s = B.getSupport(nLocalB.multiply(-1));

            // Agora calcular a distância no Mundo
            Vector2 v = B.u.mul(s).add(B.body.position); // Ponto de suporte no mundo

            // Ponto na face de A (qualquer vértice da face serve)
            Vector2 p = A.u.mul(A.m_vertices[i]).add(A.body.position);

            // Distância = dot(normal, suporte - facePonto)
            double d = nw.dot(v.subtract(p));

            if (d > 0) {
                return -1; // Separado!
            }

            if (d > bestDistance) {
                bestDistance = d;
                bestIndex = i;
            }
        }

        bestPenetration[0] = (float) bestDistance;
        return bestIndex;
    }

    // -------------------------------------------------------------------
    // HELPER: Encontrar a Face Incidente (Clipping)
    // -------------------------------------------------------------------
    private static void findIncidentFace(Vector2[] v, PolygonShape RefPoly, PolygonShape IncPoly,
            Vector2 referenceNormal) {
        // Precisamos da normal de Referência no espaço local do Polígono Incidente
        Vector2 referenceNormalIncLocal = IncPoly.u.transpose().mul(referenceNormal);

        // Achar qual face do Incidente é mais "Anti-Paralela" (dot product próximo de
        // -1)
        double minDot = Double.MAX_VALUE;
        int incidentFace = 0;

        for (int i = 0; i < IncPoly.m_vertexCount; ++i) {
            double dot = referenceNormalIncLocal.dot(IncPoly.m_normals[i]);
            if (dot < minDot) {
                minDot = dot;
                incidentFace = i;
            }
        }

        // Pegar os vértices da face incidente e transformar para Mundo
        Vector2 v1 = IncPoly.m_vertices[incidentFace];
        int i2 = (incidentFace + 1 < IncPoly.m_vertexCount) ? incidentFace + 1 : 0;
        Vector2 v2 = IncPoly.m_vertices[i2];

        v[0] = IncPoly.u.mul(v1).add(IncPoly.body.position);
        v[1] = IncPoly.u.mul(v2).add(IncPoly.body.position);
    }

    // -------------------------------------------------------------------
    // HELPER: Clipping (Sutherland-Hodgman)
    // Corta um segmento de reta (vIn) baseado em um plano (normal + offset)
    // -------------------------------------------------------------------
    private static int clip(Vector2 n, double c, Vector2[] face) {
        int sp = 0;
        Vector2[] out = {
                new Vector2(face[0].x, face[0].y),
                new Vector2(face[1].x, face[1].y)
        };

        // Distâncias dos dois pontos ao plano
        double d1 = n.dot(face[0]) - c;
        double d2 = n.dot(face[1]) - c;

        // Se ponto 1 está dentro/atrás do plano
        if (d1 <= 0.0f)
            out[sp++].set(face[0]);

        // Se ponto 2 está dentro/atrás do plano
        if (d2 <= 0.0f)
            out[sp++].set(face[1]);

        // Se os pontos estão em lados opostos do plano, precisamos calcular a
        // intersecção
        if (d1 * d2 < 0.0f) {
            double alpha = d1 / (d1 - d2);

            // Interpolação linear (Lerp)
            // out[sp] = face[0] + alpha * (face[1] - face[0])
            Vector2 delta = face[1].subtract(face[0]);
            Vector2 intersection = face[0].add(delta.multiply(alpha));

            out[sp++].set(intersection);
        }

        // Atualiza o array original com os novos pontos cortados
        face[0].set(out[0]);
        face[1].set(out[1]);

        return sp; // Retorna quantos pontos sobraram (geralmente 2)
    }
}

@FunctionalInterface
interface CollisionCallback {
    void resolve(Manifold m, RigidBody a, RigidBody b);
}
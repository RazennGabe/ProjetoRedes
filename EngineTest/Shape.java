package EngineTest;

import java.awt.*;
import java.util.Vector;

public abstract class Shape {
    public enum Type {
        CIRCLE, POLY
    }

    public RigidBody body;
    public float radius; // Para círculos
    public Mat2 u = new Mat2(); // Matriz de Orientação (para Polígonos)

    // Configurações para Polígonos
    public static final int MAX_POLY_VERTEX_COUNT = 64;

    public Shape() {
    }

    public abstract Shape clone();

    public abstract void initialize();

    public abstract void computeMass(float density);

    public abstract void setOrient(double radians);

    public abstract Type getType();

    // Adicionamos 'scale' e medidas da tela para converter Física -> Pixels
    public abstract void draw(Graphics2D g, double scale, int screenHeight);
}

class Circle extends Shape {

    public Circle(float r) {
        this.radius = r;
    }

    @Override
    public Shape clone() {
        return new Circle(radius);
    }

    @Override
    public void initialize() {
        computeMass(1.0f);
    }

    @Override
    public void computeMass(float density) {
        body.mass = Engine.PI * radius * radius * density;
        body.invMass = (body.mass != 0.0f) ? 1.0f / body.mass : 0.0f;
        body.inertia = body.mass * radius * radius;
        body.invInertia = (body.inertia != 0.0f) ? 1.0f / body.inertia : 0.0f;
    }

    @Override
    public void setOrient(double radians) {
        // Círculos não mudam geometricamente com orientação,
        // mas o body guarda a orientação.
    }

    @Override
    public Type getType() {
        return Type.CIRCLE;
    }

    @Override
    public void draw(Graphics2D g, double scale, int screenHeight) {
        // 1. Calcular posição na tela
        int x = (int) (body.position.x * scale);
        // Inverter Y do Swing
        int y = (int) (screenHeight - (body.position.y * scale));
        int r = (int) (radius * scale);

        // 2. Desenhar o contorno
        g.setColor(new Color(200, 200, 200)); // Cor cinza claro
        g.drawOval(x - r, y - r, r * 2, r * 2);

        // 3. Desenhar linha de rotação (para ver a bola girando)
        Vector2 rVec = new Vector2(0, 1.0f); // Vetor apontando pra cima

        // Rotacionar manualmente (Matemática de Rotação 2D)
        double c = Math.cos(body.angle);
        double s = Math.sin(body.angle);

        // r.x * c - r.y * s, ...
        double rx = rVec.x * c - rVec.y * s;
        double ry = rVec.x * s + rVec.y * c;

        // Transformar vetor rotação para pixels
        int endX = (int) (x + (rx * scale * radius)); // * radius pois o vetor era unitário
        int endY = (int) (y - (ry * scale * radius)); // - ry pois Y é invertido

        g.drawLine(x, y, endX, endY);
    }
}

class PolygonShape extends Shape {
    public int m_vertexCount;
    public Vector2[] m_vertices = new Vector2[MAX_POLY_VERTEX_COUNT];
    public Vector2[] m_normals = new Vector2[MAX_POLY_VERTEX_COUNT];

    public PolygonShape() {
        // Inicializa arrays para evitar NullPointerException
        for (int i = 0; i < MAX_POLY_VERTEX_COUNT; i++) {
            m_vertices[i] = new Vector2();
            m_normals[i] = new Vector2();
        }
    }

    @Override
    public void initialize() {
        computeMass(1.0f);
    }

    @Override
    public Shape clone() {
        PolygonShape poly = new PolygonShape();
        poly.u = new Mat2(this.u.m00, this.u.m01, this.u.m10, this.u.m11); // Cópia manual da matriz
        for (int i = 0; i < m_vertexCount; i++) {
            poly.m_vertices[i] = new Vector2(m_vertices[i].x, m_vertices[i].y);
            poly.m_normals[i] = new Vector2(m_normals[i].x, m_normals[i].y);
        }
        poly.m_vertexCount = m_vertexCount;
        return poly;
    }

    @Override
    public void computeMass(float density) {
        // Calcula o centróide e momento de inércia
        Vector2 c = new Vector2(0.0f, 0.0f);
        double area = 0.0f;
        double I = 0.0f;
        final double k_inv3 = 1.0f / 3.0f;

        for (int i1 = 0; i1 < m_vertexCount; ++i1) {
            Vector2 p1 = m_vertices[i1];
            int i2 = (i1 + 1 < m_vertexCount) ? i1 + 1 : 0;
            Vector2 p2 = m_vertices[i2];

            double D = p1.cross(p2);
            double triangleArea = 0.5f * D;

            area += triangleArea;

            // Peso do centróide baseado na área
            // c += triangleArea * k_inv3 * (p1 + p2)
            c.addI(p1.add(p2).multiply(triangleArea * k_inv3));

            double intx2 = p1.x * p1.x + p2.x * p1.x + p2.x * p2.x;
            double inty2 = p1.y * p1.y + p2.y * p1.y + p2.y * p2.y;
            I += (0.25f * k_inv3 * D) * (intx2 + inty2);
        }

        c.multI(1.0f / area);

        // Translada os vértices para que o centróide seja (0,0) no espaço local
        for (int i = 0; i < m_vertexCount; ++i) {
            m_vertices[i].subI(c);
        }

        body.mass = density * (float) area;
        body.invMass = (body.mass != 0.0f) ? 1.0f / body.mass : 0.0f;
        body.inertia = (float) I * density;
        body.invInertia = (body.inertia != 0.0f) ? 1.0f / body.inertia : 0.0f;
    }

    @Override
    public void setOrient(double radians) {
        u.set(radians);
    }

    @Override
    public Type getType() {
        return Type.POLY;
    }

    // Cria um retângulo
    public void setBox(double hw, double hh) {
        m_vertexCount = 4;
        m_vertices[0].set(-hw, -hh);
        m_vertices[1].set(hw, -hh);
        m_vertices[2].set(hw, hh);
        m_vertices[3].set(-hw, hh);

        m_normals[0].set(0.0f, -1.0f);
        m_normals[1].set(1.0f, 0.0f);
        m_normals[2].set(0.0f, 1.0f);
        m_normals[3].set(-1.0f, 0.0f);
    }

    // ALGORITMO CONVEX HULL (Jarvis March / Gift Wrapping)
    public void set(Vector2[] vertices, int count) {
        assert count > 2 && count <= MAX_POLY_VERTEX_COUNT;
        count = Math.min(count, MAX_POLY_VERTEX_COUNT);

        // 1. Achar o ponto mais à direita (Rightmost)
        int rightMost = 0;
        double highestXCoord = vertices[0].x;

        for (int i = 1; i < count; ++i) {
            double x = vertices[i].x;
            if (x > highestXCoord) {
                highestXCoord = x;
                rightMost = i;
            } else if (x == highestXCoord) {
                if (vertices[i].y < vertices[rightMost].y)
                    rightMost = i;
            }
        }

        int[] hull = new int[MAX_POLY_VERTEX_COUNT];
        int outCount = 0;
        int indexHull = rightMost;

        while (true) {
            hull[outCount] = indexHull;

            int nextHullIndex = 0;
            for (int i = 1; i < count; ++i) {
                if (nextHullIndex == indexHull) {
                    nextHullIndex = i;
                    continue;
                }

                Vector2 e1 = vertices[nextHullIndex].subtract(vertices[hull[outCount]]);
                Vector2 e2 = vertices[i].subtract(vertices[hull[outCount]]);

                double c = e1.cross(e2); // Produto vetorial 2D

                // Se c < 0, o ponto 'i' está mais à esquerda ("counter-clockwise")
                if (c < 0.0f) {
                    nextHullIndex = i;
                }

                // Se for colinear (c == 0), pega o mais longe
                if (c == 0.0f && e2.LengthSquared() > e1.LengthSquared()) {
                    nextHullIndex = i;
                }
            }

            outCount++;
            indexHull = nextHullIndex;

            if (nextHullIndex == rightMost) {
                m_vertexCount = outCount;
                break;
            }
        }

        // Copia vértices para o array da classe
        for (int i = 0; i < m_vertexCount; ++i) {
            m_vertices[i].set(vertices[hull[i]].x, vertices[hull[i]].y);
        }

        // Calcula as normais das faces
        for (int i1 = 0; i1 < m_vertexCount; ++i1) {
            int i2 = (i1 + 1 < m_vertexCount) ? i1 + 1 : 0;
            Vector2 face = m_vertices[i2].subtract(m_vertices[i1]);

            assert face.LengthSquared() > 0.0001f; // Garante que não é aresta zero

            m_normals[i1].set(face.y, -face.x); // Normal ortogonal
            m_normals[i1].normalize();
        }
    }

    // Suporte para GJK (colisão avançada)
    public Vector2 getSupport(Vector2 dir) {
        double bestProjection = -Double.MAX_VALUE;
        Vector2 bestVertex = null;

        for (int i = 0; i < m_vertexCount; ++i) {
            Vector2 v = m_vertices[i];
            double projection = v.dot(dir);

            if (projection > bestProjection) {
                bestVertex = v;
                bestProjection = projection;
            }
        }
        return bestVertex;
    }

    @Override
    public void draw(Graphics2D g, double scale, int screenHeight) {
        g.setColor(new Color(150, 255, 150)); // Verde claro para poligonos

        // Polígono do Java Swing (path)
        Polygon poly = new Polygon();

        for (int i = 0; i < m_vertexCount; i++) {
            // Transformação: Local -> Mundo
            // v = position + (Orientacao * vertexLocal)
            Vector2 vLocal = m_vertices[i];
            Vector2 vWorld = body.position.add(u.mul(vLocal));

            // Transformação: Mundo -> Tela
            int x = (int) (vWorld.x * scale);
            int y = (int) (screenHeight - (vWorld.y * scale));

            poly.addPoint(x, y);
        }

        g.drawPolygon(poly);

        // Desenha linha do primeiro vértice ao centro para ver orientação
        Vector2 center = body.position;
        int cx = (int) (center.x * scale);
        int cy = (int) (screenHeight - (center.y * scale));
        // g.drawLine(cx, cy, poly.xpoints[0], poly.ypoints[0]);
    }
}

class Mat2 {
    // Em C++ era uma union. Em Java, usamos variáveis diretas para performance.
    // m00 = Linha 0, Coluna 0
    // m01 = Linha 0, Coluna 1
    public double m00, m01;
    public double m10, m11;

    // Construtor Vazio
    public Mat2() {
        m00 = 1.0;
        m01 = 0.0;
        m10 = 0.0;
        m11 = 1.0;
    }

    // Construtor de Rotação (Recebe radianos)
    public Mat2(double radians) {
        set(radians);
    }

    // Construtor Explícito
    public Mat2(double a, double b, double c, double d) {
        this.m00 = a;
        this.m01 = b;
        this.m10 = c;
        this.m11 = d;
    }

    // Define a matriz baseada num ângulo (Rotação 2D padrão)
    public void set(double radians) {
        double c = Math.cos(radians);
        double s = Math.sin(radians);

        m00 = c;
        m01 = -s;
        m10 = s;
        m11 = c;
    }

    // Retorna uma nova matriz com valores absolutos (útil para AABB check)
    public Mat2 abs() {
        return new Mat2(
                Math.abs(m00), Math.abs(m01),
                Math.abs(m10), Math.abs(m11));
    }

    // Extrai o Eixo X (Primeira Coluna da matriz)
    // Usado para pegar a direção "direita" do objeto rotacionado
    public Vector2 axisX() {
        return new Vector2(m00, m10);
    }

    // Extrai o Eixo Y (Segunda Coluna da matriz)
    // Usado para pegar a direção "cima" do objeto rotacionado
    public Vector2 axisY() {
        return new Vector2(m01, m11);
    }

    // Transposta (Troca linhas por colunas)
    // Em matriz de rotação, a Transposta é igual a Inversa.
    // Usado para transformar do Mundo -> Local
    public Mat2 transpose() {
        return new Mat2(m00, m10, m01, m11);
    }

    // Multiplicação Matriz * Vetor (Rotaciona o vetor)
    // const Vector2 operator*( const Vector2& rhs ) const
    public Vector2 mul(Vector2 rhs) {
        return new Vector2(
                m00 * rhs.x + m01 * rhs.y,
                m10 * rhs.x + m11 * rhs.y);
    }

    // Multiplicação Matriz * Matriz (Combina rotações)
    // const Mat2 operator*( const Mat2& rhs ) const
    public Mat2 mul(Mat2 rhs) {
        return new Mat2(
                m00 * rhs.m00 + m01 * rhs.m10, // Linha 0 * Coluna 0
                m00 * rhs.m01 + m01 * rhs.m11, // Linha 0 * Coluna 1
                m10 * rhs.m00 + m11 * rhs.m10, // Linha 1 * Coluna 0
                m10 * rhs.m01 + m11 * rhs.m11 // Linha 1 * Coluna 1
        );
    }

    @Override
    public String toString() {
        return String.format("|%.2f %.2f|\n|%.2f %.2f|", m00, m01, m10, m11);
    }
}

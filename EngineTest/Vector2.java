package EngineTest;

public class Vector2 {
    public double x, y;

    public Vector2() {
        this(0, 0);
    }

    public Vector2(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void set(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public void set(Vector2 v) {
        this.x = v.x;
        this.y = v.y;
    }

    // --- Operações Básicas (Retornam novo vetor para não alterar o original) ---
    public Vector2 add(Vector2 v) {
        return new Vector2(x + v.x, y + v.y);
    }

    public static Vector2 Add(Vector2 v1, Vector2 v2) {
        return new Vector2(v1.x + v2.x, v1.y + v2.y);
    }

    //

    public Vector2 subtract(Vector2 v) {
        return new Vector2(x - v.x, y - v.y);
    }

    public static Vector2 Subtract(Vector2 v1, Vector2 v2) {
        return new Vector2(v1.x - v2.x, v1.y - v2.y);
    }

    //

    public Vector2 multiply(double s) {
        return new Vector2(x * s, y * s);
    }

    public static Vector2 Multiply(Vector2 v, double s) {
        return new Vector2(v.x * s, v.y * s);
    }

    //

    public Vector2 Negate() {
        return new Vector2(-x, -y);
    }

    public static Vector2 Negate(Vector2 v) {
        return new Vector2(-v.x, -v.y);
    }

    // --- Operações "In Place" (Modificam este vetor - melhor para performance/GC)
    // ---
    public void addI(Vector2 v) {
        this.x += v.x;
        this.y += v.y;
    }

    public void subI(Vector2 v) {
        this.x -= v.x;
        this.y -= v.y;
    }

    public void multI(double s) {
        this.x *= s;
        this.y *= s;
    }

    // --- Produtos ---

    // Dot Product (Produto Escalar): Retorna cos(angulo) * mag
    // Útil para: Calcular projeção, iluminação, reflexão
    public double dot(Vector2 v) {
        return x * v.x + y * v.y;
    }

    public static double Dot(Vector2 v1, Vector2 v2) {
        return v1.x * v2.x + v1.y * v2.y;
    }

    // Cross Product 2D (Produto Vetorial): Retorna um Escalar (Z)
    // Fórmula: x1*y2 - y1*x2
    // Útil para: Calcular torque rotacional, saber se ponto está à esq/dir de linha
    public double cross(Vector2 v) {
        return x * v.y - y * v.x;
    }

    public static double Cross(Vector2 v1, Vector2 v2) {
        return v1.x * v2.y - v1.y * v2.x;
    }

    public Vector2 cross(double s) {
        return new Vector2(-s * y, s * x);
    }

    public static Vector2 Cross(double s, Vector2 v) {
        return new Vector2(-s * v.y, s * v.x);
    }

    public static Vector2 Cross(Vector2 v, double s) {
        return new Vector2(s * v.y, -s * v.x);
    }

    // Magnitude (Tamanho do vetor)
    public double Length() {
        return Math.sqrt(x * x + y * y);
    }

    public double LengthSquared() {
        return x * x + y * y;
    } // Mais rápido (evita raiz quadrada)

    // Normalizar (Tornar tamanho 1, mantendo direção)
    public Vector2 normalize() {
        double l = Length();
        if (l == 0)
            return new Vector2(0, 0);
        return new Vector2(x / l, y / l);
    }

    // Distance entre dois vetores (pontos)
    public double distance(Vector2 v) {
        double dx = v.x - this.x;
        double dy = v.y - this.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public double distSqr(Vector2 v) {
        double dx = v.x - this.x;
        double dy = v.y - this.y;
        return dx * dx + dy * dy;
    }

    // Refletir (Bater na parede)
    // Fórmula: V - 2 * (V . N) * N
    public Vector2 reflect(Vector2 normal) {
        double dot = this.dot(normal);
        return new Vector2(
                this.x - 2 * dot * normal.x,
                this.y - 2 * dot * normal.y);
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f)", x, y);
    }
}

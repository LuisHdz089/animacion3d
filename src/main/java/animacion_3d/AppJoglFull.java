package animacion_3d;

import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.*;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.gl2.GLUT;
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Clase principal que implementa la lógica de renderizado y control de eventos.
 * Simula una arena de estilo "Clash Royale" con animaciones, iluminación y texturas.
 */
public class AppJoglFull implements GLEventListener, KeyListener, MouseListener {

    // --- VARIABLES DE VENTANA Y ANIMACIÓN ---
    private static GLWindow window;
    private static FPSAnimator animator;
    private final GLUT glut = new GLUT();

    // --- CÁMARA ---
    private float camX = 0.0f;
    private float camY = 2.5f;
    private float camZ = 6.0f;
    private float camYaw = -90.0f;
    private float camPitch = -25.0f;

    // --- INPUT & CONTROL ---
    private final boolean[] keys = new boolean[512]; // Buffer para teclas
    private boolean godMode = false;                 // Modo vuelo/atravesar paredes
    private boolean lightAtCenter = true;            // Control de la luz dinámica
    private boolean spaceKeyWasDown = false;         // Toggle para la tecla espacio
    private float currentLightX = 0.0f;              // Posición actual X de la luz (interpolación)
    private float currentLightZ = 0.0f;              // Posición actual Z de la luz (interpolación)
    private long lastTimeNs = System.nanoTime();     // Delta time para movimiento
    private float mouseSensitivity = 0.12f;          // Sensibilidad del ratón
    private boolean mouseCaptured = false;           // Estado del cursor

    // --- TEXTURAS ---
    private Texture grassTex, stoneTex, woodTex, waterTex, goldTex, cardTex;
    private final float TEX_SCALE = 0.5f;

    // --- DIMENSIONES DEL ESCENARIO ---
    private final float PLAYER_RADIUS = 0.4f;
    private final float PLAYABLE_WIDTH = 14.0f;
    private final float PLAYABLE_LENGTH = 24.0f;
    private final float TOTAL_WIDTH = 40.0f;
    private final float TOTAL_LENGTH = 48.0f;
    private final float RIVER_WIDTH = 4.0f;
    private final float BRIDGE_WIDTH = 3.0f;
    private final float BRIDGE_CENTER_X = 3.5f;
    private final float TOWER_KING_Z = 13.5f;
    private final float TOWER_PRINCESS_Z = 8.5f;
    private final float TOWER_PRINCESS_X = 5.0f;
    private final float TOWER_COLLISION_SIZE = 1.8f;
    private final float WATER_HEIGHT = -0.5f;

    // --- CLASES INTERNAS (OBJETOS DECORATIVOS) ---

    // Datos para los árboles del bosque circundante
    private static class TreeData {
        float x, z, scale, rot;

        public TreeData(float x, float z, float scale, float rot) {
            this.x = x;
            this.z = z;
            this.scale = scale;
            this.rot = rot;
        }
    }

    // Mariposa animada
    private static class Butterfly {
        float anchorX, anchorZ;
        float y, phase, colorR, colorG, colorB, speed, radius;

        public Butterfly(float x, float z) {
            this.anchorX = x;
            this.anchorZ = z;
            this.y = 1.5f + (float) Math.random() * 2.0f;
            this.phase = (float) Math.random() * 100f; // Fase inicial aleatoria para desincronizar
            this.speed = 0.5f + (float) Math.random() * 0.5f;
            this.radius = 1.0f + (float) Math.random() * 1.5f;
            // Color aleatorio (tonos naranjas o azules)
            if (Math.random() > 0.5) {
                this.colorR = 1f; this.colorG = 0.6f; this.colorB = 0.2f;
            } else {
                this.colorR = 0.2f; this.colorG = 0.8f; this.colorB = 1f;
            }
        }
    }

    // Nubes cúbicas estilo Minecraft
    private static class Cloud {
        float x, y, z, scale;
        List<float[]> blocks = new ArrayList<>();

        public Cloud(float x, float y, float z, float scale) {
            this.x = x; this.y = y; this.z = z; this.scale = scale;
            generateBlocks();
        }

        // Genera la forma de la nube aleatoriamente
        private void generateBlocks() {
            int width = 3 + (int) (Math.random() * 3);
            int depth = 2 + (int) (Math.random() * 2);
            for (int i = -width / 2; i <= width / 2; i++) {
                for (int k = -depth / 2; k <= depth / 2; k++) {
                    if (Math.random() > 0.2) {
                        blocks.add(new float[]{i, 0, k});
                        if (Math.random() > 0.85) blocks.add(new float[]{i, 1, k}); // Segundo nivel
                    }
                }
            }
            if (blocks.isEmpty()) blocks.add(new float[]{0, 0, 0});
        }
    }

    // Partículas de confeti
    private static class ConfettiParticle {
        float x, y, z;
        float vy, driftX, driftZ; // Velocidad vertical y deriva horizontal
        float r, g, b;
        boolean isBlueSide;

        public ConfettiParticle(boolean isBlueSide) {
            this.isBlueSide = isBlueSide;
            // Color según el lado del equipo
            if (isBlueSide) { r = 0.2f; g = 0.4f; b = 1.0f; } 
            else { r = 1.0f; g = 0.2f; b = 0.2f; }
            reset(false); // Inicio distribuido en el espacio
        }

        void reset(boolean startAtTop) {
            // Definir áreas de las gradas (aprox basado en dimensiones)
            float halfW = 14.0f / 2f;
            float sideW = 5.0f;
            float minX = -halfW - sideW;
            float maxX = halfW + sideW;
            // Z negativo = lado azul, Z positivo = lado rojo
            float minZ = isBlueSide ? -15f : 2f;
            float maxZ = isBlueSide ? -2f : 15f;

            this.x = minX + (float) Math.random() * (maxX - minX);
            this.z = minZ + (float) Math.random() * (maxZ - minZ);
            // Si empieza arriba (reset) o distribuido al inicio
            this.y = startAtTop ? 12.0f + (float) Math.random() * 5.0f : 1.0f + (float) Math.random() * 15.0f;

            // Velocidad de caída y deriva
            this.vy = -0.03f - (float) Math.random() * 0.04f;
            this.driftX = (float) (Math.random() - 0.5) * 0.02f;
            this.driftZ = (float) (Math.random() - 0.5) * 0.02f;
        }

        void update() {
            x += driftX;
            z += driftZ;
            y += vy;
            // Pequeña turbulencia
            driftX += (Math.random() - 0.5) * 0.002f;
            driftZ += (Math.random() - 0.5) * 0.002f;
            // Reset si toca el suelo
            if (y < 0.05f) reset(true);
        }
    }

    // --- LISTAS DE OBJETOS ---
    private final List<TreeData> forest = new ArrayList<>();
    private final List<Butterfly> butterflies = new ArrayList<>();
    private final List<Cloud> clouds = new ArrayList<>();
    private final List<ConfettiParticle> confettiList = new ArrayList<>();

    // --- VARIABLES DE TIEMPO GLOBAL ---
    private float time = 0f;
    private float waterOffset = 0.0f;

    // ==========================================
    // MÉTODO MAIN
    // ==========================================
    public static void main(String[] args) {
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities caps = new GLCapabilities(profile);
        caps.setStencilBits(8); // Bits para sombras (Stencil buffer)
        caps.setDoubleBuffered(true);

        window = GLWindow.create(caps);
        window.setTitle("Clash Arena 3D");
        window.setSize(1280, 960);
        window.setVisible(true);

        AppJoglFull app = new AppJoglFull();
        window.addGLEventListener(app);
        window.addKeyListener(app);
        window.addMouseListener(app);

        // Configuración inicial del puntero
        window.setPointerVisible(true);
        window.confinePointer(false);

        // Bucle de animación a 60 FPS
        animator = new FPSAnimator(window, 60);
        animator.start();

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowDestroyed(WindowEvent e) {
                new Thread(() -> {
                    animator.stop();
                    System.exit(0);
                }).start();
            }
        });
    }

    // ==========================================
    // INICIALIZACIÓN (OpenGL init)
    // ==========================================
    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        // Configuración básica
        gl.glClearColor(0.5f, 0.7f, 1.0f, 1f); // Color cielo
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_TEXTURE_2D);
        gl.glEnable(GL2.GL_NORMALIZE);
        gl.glShadeModel(GL2.GL_SMOOTH);

        // --- CARGA DE TEXTURAS ---
        grassTex = loadTexture(gl, "D:\\Proyectos VS Code\\Graficacion\\animacion3d\\grass.jpg");
        stoneTex = loadTexture(gl, "D:\\Proyectos VS Code\\Graficacion\\animacion3d\\stone.png");
        woodTex  = loadTexture(gl, "D:\\Proyectos VS Code\\Graficacion\\animacion3d\\wood.png");
        waterTex = loadTexture(gl, "D:\\Proyectos VS Code\\Graficacion\\animacion3d\\water.png");
        goldTex  = loadTexture(gl, "D:\\Proyectos VS Code\\Graficacion\\animacion3d\\gold.png");
        cardTex  = loadTexture(gl, "D:\\Proyectos VS Code\\Graficacion\\animacion3d\\card.png");
        // Fallback si no encuentra cardTex
        if (cardTex == null) cardTex = woodTex;

        // --- ILUMINACIÓN ---
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0); // Luz principal (Sol/Foco)
        gl.glEnable(GL2.GL_LIGHT1); // Luz ambiental de relleno
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE);

        // Propiedades de Luz 0
        float[] ambient0 = { 0.35f, 0.35f, 0.36f, 1f };
        float[] diffuse0 = { 1.0f, 1.0f, 0.95f, 1f };
        float[] spec0 = { 0.4f, 0.4f, 0.4f, 1f };

        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, ambient0, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, diffuse0, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPECULAR, spec0, 0);

        // Propiedades de Luz 1 (Ambiente oscura)
        float[] ambient1 = { 0.12f, 0.12f, 0.14f, 1f };
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_AMBIENT, ambient1, 0);

        // Configuración tipo Spotlight para Luz 0
        float[] spotDir = { 0f, -1f, 0f };
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_SPOT_DIRECTION, spotDir, 0);
        gl.glLightf(GL2.GL_LIGHT0, GL2.GL_SPOT_CUTOFF, 120.0f);
        gl.glLightf(GL2.GL_LIGHT0, GL2.GL_SPOT_EXPONENT, 1.0f);

        // --- GENERACIÓN DE DATOS ---
        generateForestData();
        generateButterflies();
        generateClouds();
        generateConfetti();
        
        lastTimeNs = System.nanoTime();
    }

    // --- MÉTODOS DE GENERACIÓN PROCEDURAL ---

    private void generateForestData() {
        forest.clear();
        float halfTW = TOTAL_WIDTH / 2.0f;
        float halfTL = TOTAL_LENGTH / 2.0f;
        float step = 7.0f;
        // Árboles laterales
        for (float z = -halfTL + step / 2; z <= halfTL - step / 2; z += step) {
            addRandomTree(-halfTW + 2.0f, z);
            addRandomTree(halfTW - 2.0f, z);
        }
        // Árboles superior e inferior
        for (float x = -halfTW + step; x < halfTW - step; x += step) {
            addRandomTree(x, -halfTL + 2.0f);
            addRandomTree(x, halfTL - 2.0f);
        }
    }

    private void addRandomTree(float baseX, float baseZ) {
        float x = baseX + (float) (Math.random() * 1.5);
        float z = baseZ + (float) (Math.random() * 1.5);
        float scale = 0.8f + (float) Math.random() * 0.4f;
        float rot = (float) Math.random() * 360;
        forest.add(new TreeData(x, z, scale, rot));
    }

    private void generateButterflies() {
        butterflies.clear();
        for (int i = 0; i < 20; i++) {
            float x = (float) (Math.random() * TOTAL_WIDTH - TOTAL_WIDTH / 2);
            float z = (float) (Math.random() * TOTAL_LENGTH - TOTAL_LENGTH / 2);
            // Evitar que aparezcan en el centro de la arena
            if (Math.abs(x) < PLAYABLE_WIDTH / 2 && Math.abs(z) < PLAYABLE_LENGTH / 2) {
                x = (x > 0) ? x + 10 : x - 10;
            }
            butterflies.add(new Butterfly(x, z));
        }
    }

    private void generateClouds() {
        clouds.clear();
        for (int i = 0; i < 10; i++) {
            float x = (float) (Math.random() * TOTAL_WIDTH * 2.5 - TOTAL_WIDTH * 1.25);
            float z = (float) (Math.random() * TOTAL_LENGTH * 2.5 - TOTAL_LENGTH * 1.25);
            float y = 35f + (float) Math.random() * 15f;
            float scale = 4f + (float) Math.random() * 2f;
            clouds.add(new Cloud(x, y, z, scale));
        }
    }

    private void generateConfetti() {
        confettiList.clear();
        int numConfetti = 600; // Total de partículas
        for (int i = 0; i < numConfetti; i++) {
            confettiList.add(new ConfettiParticle(i % 2 == 0)); // Alternar rojo y azul
        }
    }

    // --- CARGA DE TEXTURAS ---
    private Texture loadTexture(GL2 gl, String path) {
        try {
            File f = new File(path);
            if (!f.exists()) return null;
            Texture t = TextureIO.newTexture(f, true);
            t.setTexParameteri(gl, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR_MIPMAP_LINEAR);
            t.setTexParameteri(gl, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
            t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
            t.setTexParameteri(gl, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
            return t;
        } catch (Exception e) {
            return null;
        }
    }

    private void bindTexture(GL2 gl, Texture t) {
        if (t != null) {
            gl.glEnable(GL2.GL_TEXTURE_2D);
            t.bind(gl);
            gl.glColor3f(1f, 1f, 1f);
        } else {
            gl.glDisable(GL2.GL_TEXTURE_2D);
        }
    }

    // ==========================================
    // REDIMENSIONADO DE VENTANA
    // ==========================================
    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();
        if (height <= 0) height = 1;
        float aspect = (float) width / height;
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        gl.glFrustum(-aspect * 0.1f, aspect * 0.1f, -0.1f, 0.1f, 0.1f, 500f);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    // ==========================================
    // BUCLE DE RENDERIZADO (DISPLAY)
    // ==========================================
    @Override
    public void display(GLAutoDrawable drawable) {
        // Manejo de mouse y movimiento
        if (mouseCaptured) {
            updateMovement();
            try {
                window.warpPointer(window.getWidth() / 2, window.getHeight() / 2);
            } catch (Exception ignored) {}
        }

        // Lógica de luz dinámica con tecla Espacio
        boolean spaceDown = isKeyDown(KeyEvent.VK_SPACE);
        if (spaceDown && !spaceKeyWasDown) lightAtCenter = !lightAtCenter;
        spaceKeyWasDown = spaceDown;

        // Interpolación de la luz
        float targetX = lightAtCenter ? 0.0f : 12.0f;
        float targetZ = lightAtCenter ? 0.0f : 12.0f;
        currentLightX += (targetX - currentLightX) * 0.05f;
        currentLightZ += (targetZ - currentLightZ) * 0.05f;

        // Actualizar física del confeti
        for (ConfettiParticle p : confettiList) p.update();

        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT | GL.GL_STENCIL_BUFFER_BIT);
        gl.glLoadIdentity();

        // Aplicar Cámara
        gl.glRotatef(-camPitch, 1f, 0f, 0f);
        gl.glRotatef(-camYaw, 0f, 1f, 0f);
        gl.glTranslatef(-camX, -camY, -camZ);

        // Posicionar Luz
        float[] lightPos = { currentLightX, 50f, currentLightZ, 1.0f };
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPos, 0);

        // Variables de animación
        time += 0.02f;
        waterOffset += 0.008f;
        float wave = (float) Math.sin(time) * 0.03f;

        // ----------------------------------------------------
        // 1) DIBUJAR SUELO Y AGUA (Con Blending)
        // ----------------------------------------------------
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        
        drawFloorOnly(gl);
        
        bindTexture(gl, waterTex);
        gl.glMaterialf(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, 90.0f);
        gl.glColor4f(0.12f, 0.28f + wave, 0.82f, 0.64f);
        drawWaterSurface(gl, true);
        
        gl.glMaterialf(GL2.GL_FRONT_AND_BACK, GL2.GL_SHININESS, 0.0f);
        gl.glDisable(GL2.GL_BLEND);

        // ----------------------------------------------------
        // 2) SOMBRAS PLANAS (Matriz de proyección)
        // ----------------------------------------------------
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_COLOR_MATERIAL);
        
        float[] blackColor = { 0f, 0f, 0f, 1f };
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE, blackColor, 0);
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, blackColor, 0);
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_EMISSION, blackColor, 0);
        
        gl.glEnable(GL2.GL_POLYGON_OFFSET_FILL);
        gl.glPolygonOffset(-2.0f, -2.0f); // Evitar Z-fighting
        
        gl.glPushMatrix();
            float[] floorPlane = { 0.0f, 1.0f, 0.0f, 0.0f };
            float[] shadowMat = new float[16];
            shadowMatrix(shadowMat, floorPlane, lightPos);
            gl.glMultMatrixf(shadowMat, 0);
            drawObjects(gl); // Dibuja la geometría aplastada como sombra
        gl.glPopMatrix();
        
        gl.glDisable(GL2.GL_POLYGON_OFFSET_FILL);

        // Restaurar materiales normales
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        float[] whiteColor = { 1f, 1f, 1f, 1f };
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE, whiteColor, 0);
        float[] noShininess = { 0.0f, 0.0f, 0.0f, 1f };
        gl.glMaterialfv(GL2.GL_FRONT_AND_BACK, GL2.GL_SPECULAR, noShininess, 0);

        // ----------------------------------------------------
        // 3) DIBUJAR OBJETOS REALES
        // ----------------------------------------------------
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glColor4f(1f, 1f, 1f, 1f);
        drawObjects(gl);

        // Dibujar Confeti
        drawConfetti(gl);

        // Dibujar Nubes (Traslúcidas)
        drawClouds(gl);

        // ----------------------------------------------------
        // 4) INTERFAZ (HUD)
        // ----------------------------------------------------
        drawHUD(gl);
    }

    // ==========================================
    // MÉTODOS DE DIBUJADO ESPECÍFICOS
    // ==========================================

    private void drawConfetti(GL2 gl) {
        // Optimización: Desactivar luces y texturas para partículas simples
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glDisable(GL2.GL_LIGHTING);

        float s = 0.07f; // Tamaño del papelito

        gl.glBegin(GL2.GL_QUADS);
        for (ConfettiParticle p : confettiList) {
            gl.glColor3f(p.r, p.g, p.b);
            // Cuadrado plano horizontal
            gl.glVertex3f(p.x - s, p.y, p.z - s);
            gl.glVertex3f(p.x + s, p.y, p.z - s);
            gl.glVertex3f(p.x + s, p.y, p.z + s);
            gl.glVertex3f(p.x - s, p.y, p.z + s);
        }
        gl.glEnd();

        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_TEXTURE_2D);
    }

    private void drawHUD(GL2 gl) {
        // Cambiar a proyección ortogonal para 2D
        gl.glPushAttrib(GL2.GL_ALL_ATTRIB_BITS);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        int w = window.getWidth(), h = window.getHeight();
        gl.glOrtho(0, w, 0, h, -1, 1);
        
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor3f(1f, 1f, 1f);
        
        if (godMode) {
            gl.glRasterPos2i(10, h - 20);
            String s = "GOD MODE";
            for (char c : s.toCharArray()) glut.glutBitmapCharacter(GLUT.BITMAP_HELVETICA_18, c);
        }
        
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPopAttrib();
    }

    // Matriz matemática para aplastar geometría sobre un plano (Sombras)
    private void shadowMatrix(float[] m, float[] plane, float[] light) {
        float dot = plane[0] * light[0] + plane[1] * light[1] + plane[2] * light[2] + plane[3] * light[3];
        m[0] = dot - light[0] * plane[0]; m[4] = 0f - light[0] * plane[1]; m[8] = 0f - light[0] * plane[2]; m[12] = 0f - light[0] * plane[3];
        m[1] = 0f - light[1] * plane[0]; m[5] = dot - light[1] * plane[1]; m[9] = 0f - light[1] * plane[2]; m[13] = 0f - light[1] * plane[3];
        m[2] = 0f - light[2] * plane[0]; m[6] = 0f - light[2] * plane[1]; m[10] = dot - light[2] * plane[2]; m[14] = 0f - light[2] * plane[3];
        m[3] = 0f - light[3] * plane[0]; m[7] = 0f - light[3] * plane[1]; m[11] = 0f - light[3] * plane[2]; m[15] = dot - light[3] * plane[3];
    }

    // Dibuja solo el terreno plano (césped y laterales del río)
    private void drawFloorOnly(GL2 gl) {
        float halfTotalW = TOTAL_WIDTH / 2f;
        float halfTotalL = TOTAL_LENGTH / 2f;
        float riverHalf = RIVER_WIDTH / 2f;
        
        bindTexture(gl, grassTex);
        // Terreno lado 1
        drawCheckerboardFloor(gl, -halfTotalW, halfTotalW, -halfTotalL, -riverHalf);
        // Terreno lado 2
        drawCheckerboardFloor(gl, -halfTotalW, halfTotalW, riverHalf, halfTotalL);
        
        bindTexture(gl, stoneTex);
        // Paredes del río
        drawWall(gl, -halfTotalW, halfTotalW, -riverHalf, -riverHalf, WATER_HEIGHT, 0);
        drawWall(gl, -halfTotalW, halfTotalW, riverHalf, riverHalf, WATER_HEIGHT, 0);
    }

    // Método maestro que llama a todos los objetos sólidos
    private void drawObjects(GL2 gl) {
        float halfW = PLAYABLE_WIDTH / 2f;
        float halfL = PLAYABLE_LENGTH / 2f;
        float riverHW = RIVER_WIDTH / 2f;

        drawAqueduct(gl);
        drawArenaWall(gl);

        // Decoración (Árboles y Mariposas)
        gl.glDisable(GL2.GL_TEXTURE_2D);
        for (TreeData t : forest) drawTree(gl, t);
        for (Butterfly b : butterflies) drawButterfly(gl, b);
        gl.glEnable(GL2.GL_TEXTURE_2D);

        // Puentes
        bindTexture(gl, woodTex);
        float bHW = BRIDGE_WIDTH / 2f;
        drawBlockSolid(gl, BRIDGE_CENTER_X - bHW, BRIDGE_CENTER_X + bHW, -riverHW - 0.5f, riverHW + 0.5f, 0.15f, 0.3f, 1f, 1f);
        drawBlockSolid(gl, -BRIDGE_CENTER_X - bHW, -BRIDGE_CENTER_X + bHW, -riverHW - 0.5f, riverHW + 0.5f, 0.15f, 0.3f, 1f, 1f);

        // Gradas
        float sideW = 5f;
        drawBleachersWithCanopy(gl, -halfW - sideW, -halfW, -halfL, -riverHW, true);
        drawBleachersWithCanopy(gl, halfW, halfW + sideW, -halfL, -riverHW, true);
        drawBleachersWithCanopy(gl, -halfW - sideW, -halfW, riverHW, halfL, false);
        drawBleachersWithCanopy(gl, halfW, halfW + sideW, riverHW, halfL, false);

        // Pilas de oro
        bindTexture(gl, goldTex);
        drawGoldPile(gl, -halfW - 2, -halfL + 2);
        drawGoldPile(gl, halfW + 2, halfL - 2);

        // Torres (Sin textura para usar colores sólidos)
        gl.glDisable(GL2.GL_TEXTURE_2D);
        // Lado Azul
        drawDetailedTower(gl, 0, -TOWER_KING_Z, true, true);
        drawDetailedTower(gl, -TOWER_PRINCESS_X, -TOWER_PRINCESS_Z, false, true);
        drawDetailedTower(gl, TOWER_PRINCESS_X, -TOWER_PRINCESS_Z, false, true);
        // Lado Rojo
        drawDetailedTower(gl, 0, TOWER_KING_Z, true, false);
        drawDetailedTower(gl, -TOWER_PRINCESS_X, TOWER_PRINCESS_Z, false, false);
        drawDetailedTower(gl, TOWER_PRINCESS_X, TOWER_PRINCESS_Z, false, false);
        gl.glEnable(GL2.GL_TEXTURE_2D);

        // Personajes (Caballero y Arquero)
        gl.glPushMatrix(); gl.glTranslatef(0, 0.25f, -10); drawKnight(gl); gl.glPopMatrix();
        gl.glPushMatrix(); gl.glTranslatef(2, 0.25f, 10); drawArcher(gl); gl.glPopMatrix();
    }

    private void drawClouds(GL2 gl) {
        gl.glDisable(GL2.GL_TEXTURE_2D);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false); // No escribir en buffer de profundidad para transparencia
        
        gl.glColor4f(1.0f, 1.0f, 1.0f, 0.55f);
        
        for (Cloud c : clouds) {
            gl.glPushMatrix();
            gl.glTranslatef(c.x, c.y, c.z);
            gl.glScalef(c.scale, c.scale, c.scale);
            for (float[] block : c.blocks) {
                gl.glPushMatrix();
                gl.glTranslatef(block[0], block[1], block[2]);
                glut.glutSolidCube(1.0f);
                gl.glPopMatrix();
            }
            gl.glPopMatrix();
        }
        
        gl.glDepthMask(true);
        gl.glDisable(GL2.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_TEXTURE_2D);
    }

    private void drawArenaWall(GL2 gl) {
        float h = 1f; float w = 0.5f;
        float hw = PLAYABLE_WIDTH / 2f;
        float hl = PLAYABLE_LENGTH / 2f;
        
        bindTexture(gl, stoneTex);
        // Paredes de piedra
        drawSolidBlock(gl, -hw - w, -hw, -hl, hl, 0, h);
        drawSolidBlock(gl, hw, hw + w, -hl, hl, 0, h);
        drawSolidBlock(gl, -hw - w, hw + w, -hl - w, -hl, 0, h);
        drawSolidBlock(gl, -hw - w, hw + w, hl, hl + w, 0, h);
        
        bindTexture(gl, woodTex);
        // Postes de madera en esquinas
        float postH = h + 0.5f; float postW = w + 0.2f;
        drawSolidBlock(gl, -hw - w / 2 - postW / 2, -hw - w / 2 + postW / 2, -hl - w / 2 - postW / 2, -hl - w / 2 + postW / 2, 0, postH);
        drawSolidBlock(gl, hw + w / 2 - postW / 2, hw + w / 2 + postW / 2, -hl - w / 2 - postW / 2, -hl - w / 2 + postW / 2, 0, postH);
        drawSolidBlock(gl, -hw - w / 2 - postW / 2, -hw - w / 2 + postW / 2, hl + w / 2 - postW / 2, hl + w / 2 + postW / 2, 0, postH);
        drawSolidBlock(gl, hw + w / 2 - postW / 2, hw + w / 2 + postW / 2, hl + w / 2 - postW / 2, hl + w / 2 + postW / 2, 0, postH);
    }

    // --- ACUEDUCTOS DECORATIVOS ---
    private void drawAqueduct(GL2 gl) {
        float halfW = TOTAL_WIDTH / 2f;
        float halfL = TOTAL_LENGTH / 2f;
        float wallHeight = 12f;
        float archWidth = 5f;
        float pillarWidth = 2f;
        float depth = 3f;
        
        bindTexture(gl, stoneTex);
        // Dibujar en los 4 lados rotando
        gl.glPushMatrix(); gl.glTranslatef(0, 0, -halfL); drawStraightAqueduct(gl, -halfW, halfW, wallHeight, archWidth, pillarWidth, depth); gl.glPopMatrix();
        gl.glPushMatrix(); gl.glTranslatef(0, 0, halfL); drawStraightAqueduct(gl, -halfW, halfW, wallHeight, archWidth, pillarWidth, depth); gl.glPopMatrix();
        gl.glPushMatrix(); gl.glTranslatef(-halfW, 0, 0); gl.glRotatef(90, 0, 1, 0); drawStraightAqueduct(gl, -halfL, halfL, wallHeight, archWidth, pillarWidth, depth); gl.glPopMatrix();
        gl.glPushMatrix(); gl.glTranslatef(halfW, 0, 0); gl.glRotatef(90, 0, 1, 0); drawStraightAqueduct(gl, -halfL, halfL, wallHeight, archWidth, pillarWidth, depth); gl.glPopMatrix();
        
        // Pilares de esquina
        drawCornerPillar(gl, -halfW, -halfL, pillarWidth, wallHeight, depth);
        drawCornerPillar(gl, halfW, -halfL, pillarWidth, wallHeight, depth);
        drawCornerPillar(gl, -halfW, halfL, pillarWidth, wallHeight, depth);
        drawCornerPillar(gl, halfW, halfL, pillarWidth, wallHeight, depth);
    }

    private void drawStraightAqueduct(GL2 gl, float startX, float endX, float height, float archW, float pillarW, float depth) {
        float totalSegment = archW + pillarW;
        float totalLength = endX - startX;
        int numArches = (int) (totalLength / totalSegment);
        float currentX = startX;
        
        for (int i = 0; i < numArches; i++) {
            float pilarEnd = currentX + pillarW;
            float springLine = height * 0.6f;
            drawSolidBlock(gl, currentX, pilarEnd, -depth / 2, depth / 2, 0, springLine);
            float archCenter = pilarEnd + (archW / 2.0f);
            float radius = archW / 2.0f;
            drawArchGeometry(gl, height, springLine, depth, radius, archCenter);
            currentX += totalSegment;
        }
        drawSolidBlock(gl, currentX, currentX + pillarW, -depth / 2, depth / 2, 0, height * 0.6f);
        drawSolidBlock(gl, startX, currentX + pillarW, -depth / 2 - 0.2f, depth / 2 + 0.2f, height - 1.0f, height);
    }

    private void drawArchGeometry(GL2 gl, float yTop, float yBaseCurve, float depth, float radius, float cx) {
        int segments = 20;
        float angleStep = (float) Math.PI / segments;
        float d2 = depth / 2.0f;
        
        gl.glBegin(GL2.GL_QUADS);
        for (int i = 0; i < segments; i++) {
            float ang1 = (float) Math.PI - (i * angleStep);
            float ang2 = (float) Math.PI - ((i + 1) * angleStep);
            float lx1 = (float) Math.cos(ang1) * radius; float ly1 = (float) Math.sin(ang1) * radius;
            float lx2 = (float) Math.cos(ang2) * radius; float ly2 = (float) Math.sin(ang2) * radius;
            float px1 = cx + lx1; float py1 = yBaseCurve + ly1;
            float px2 = cx + lx2; float py2 = yBaseCurve + ly2;

            gl.glNormal3f(0, 0, 1);
            gl.glTexCoord2f(px1 * TEX_SCALE, py1 * TEX_SCALE); gl.glVertex3f(px1, py1, d2);
            gl.glTexCoord2f(px2 * TEX_SCALE, py2 * TEX_SCALE); gl.glVertex3f(px2, py2, d2);
            gl.glTexCoord2f(px2 * TEX_SCALE, yTop * TEX_SCALE); gl.glVertex3f(px2, yTop, d2);
            gl.glTexCoord2f(px1 * TEX_SCALE, yTop * TEX_SCALE); gl.glVertex3f(px1, yTop, d2);

            gl.glNormal3f(0, 0, -1);
            gl.glTexCoord2f(px1 * TEX_SCALE, py1 * TEX_SCALE); gl.glVertex3f(px1, yTop, -d2);
            gl.glTexCoord2f(px2 * TEX_SCALE, py2 * TEX_SCALE); gl.glVertex3f(px2, yTop, -d2);
            gl.glTexCoord2f(px2 * TEX_SCALE, yTop * TEX_SCALE); gl.glVertex3f(px2, py2, -d2);
            gl.glTexCoord2f(px1 * TEX_SCALE, yTop * TEX_SCALE); gl.glVertex3f(px1, py1, -d2);
        }
        gl.glEnd();
        
        // Llenar el arco por dentro
        gl.glBegin(GL2.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            float ang = (float) Math.PI - (i * angleStep);
            float lx = (float) Math.cos(ang) * radius;
            float ly = (float) Math.sin(ang) * radius;
            float px = cx + lx; float py = yBaseCurve + ly;
            gl.glNormal3f(lx, ly, 0);
            gl.glTexCoord2f(0, (float) i / segments * 2f); gl.glVertex3f(px, py, -d2);
            gl.glTexCoord2f(1, (float) i / segments * 2f); gl.glVertex3f(px, py, d2);
        }
        gl.glEnd();
        drawSolidBlock(gl, cx - radius, cx + radius, -d2, d2, yTop - 0.1f, yTop);
    }

    private void drawCornerPillar(GL2 gl, float x, float z, float w, float h, float d) {
        gl.glPushMatrix();
        gl.glTranslatef(x, 0, z);
        drawSolidBlock(gl, -w / 2 - 0.1f, w / 2 + 0.1f, -d / 2 - 0.1f, d / 2 + 0.1f, 0, h + 0.5f);
        drawSolidBlock(gl, -w / 2 - 0.3f, w / 2 + 0.3f, -d / 2 - 0.3f, d / 2 + 0.3f, h + 0.5f, h + 1.0f);
        gl.glPopMatrix();
    }

    private void drawBleachersWithCanopy(GL2 gl, float minX, float maxX, float minZ, float maxZ, boolean isBlue) {
        float baseHeight = 0f;
        int numSteps = 3;
        float stepH = 0.7f;
        float frontX = (minX > 0) ? minX : maxX;
        float backX = (minX > 0) ? maxX : minX;
        float totalDepth = Math.abs(maxX - minX);
        float stepD = totalDepth / numSteps;
        float direction = (minX > 0) ? 1f : -1f;
        
        bindTexture(gl, stoneTex);
        // Dibujar escalones
        for (int i = 0; i < numSteps; i++) {
            float currentYLow = baseHeight + i * stepH;
            float currentYHigh = baseHeight + (i + 1) * stepH;
            float currentStepFrontX = frontX + (i * stepD * direction);
            drawSolidBlock(gl, Math.min(currentStepFrontX, backX), Math.max(currentStepFrontX, backX), minZ, maxZ, currentYLow, currentYHigh);
            
            // Público (planos 2D texturizados)
            if (gl.glIsEnabled(GL2.GL_TEXTURE_2D)) {
                bindTexture(gl, cardTex);
                float crowdDensity = 1.5f;
                float zStart = minZ + 1f;
                float zEnd = maxZ - 1f;
                float rotAngle = (minX > 0) ? -90f : 90f;
                float jump = (float) Math.sin(time * 5f + i) * 0.05f;
                
                for (float z = zStart; z < zEnd; z += crowdDensity) {
                    gl.glPushMatrix();
                    gl.glTranslatef(currentStepFrontX + (0.2f * direction), currentYHigh + 0.4f + Math.abs(jump), z);
                    gl.glRotatef(rotAngle, 0, 1, 0);
                    gl.glScalef(0.5f, 0.7f, 0.05f);
                    gl.glColor3f(1f, 1f, 1f);
                    gl.glBegin(GL2.GL_QUADS);
                    gl.glNormal3f(0, 0, 1);
                    gl.glTexCoord2f(0, 0); gl.glVertex3f(-0.5f, -0.5f, 0);
                    gl.glTexCoord2f(1, 0); gl.glVertex3f(0.5f, -0.5f, 0);
                    gl.glTexCoord2f(1, 1); gl.glVertex3f(0.5f, 0.5f, 0);
                    gl.glTexCoord2f(0, 1); gl.glVertex3f(-0.5f, 0.5f, 0);
                    gl.glEnd();
                    gl.glPopMatrix();
                }
                bindTexture(gl, stoneTex);
            }
        }
        
        // Postes y techo
        bindTexture(gl, woodTex);
        float poleH = (numSteps + 1) * stepH + 1.5f;
        float poleW = 0.3f;
        drawSolidBlock(gl, minX, minX + poleW, minZ, minZ + poleW, 0, poleH);
        drawSolidBlock(gl, maxX - poleW, maxX, minZ, minZ + poleW, 0, poleH);
        drawSolidBlock(gl, minX, minX + poleW, maxZ - poleW, maxZ, 0, poleH);
        drawSolidBlock(gl, maxX - poleW, maxX, maxZ - poleW, maxZ, 0, poleH);
        
        gl.glDisable(GL2.GL_TEXTURE_2D);
        if (isBlue) gl.glColor3f(0.3f, 0.4f, 1f); else gl.glColor3f(1f, 0.3f, 0.3f);
        
        float canopyLowY = poleH - 0.2f;
        float canopyHighY = poleH + 1.2f;
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0, 1, 0);
        gl.glVertex3f(frontX, canopyLowY, maxZ);
        gl.glVertex3f(frontX, canopyLowY, minZ);
        gl.glVertex3f(backX, canopyHighY, minZ);
        gl.glVertex3f(backX, canopyHighY, maxZ);
        gl.glEnd();
        gl.glEnable(GL2.GL_TEXTURE_2D);
    }

    private void drawButterfly(GL2 gl, Butterfly b) {
        float currentX = b.anchorX + (float) Math.cos(time * b.speed + b.phase) * b.radius;
        float currentZ = b.anchorZ + (float) Math.sin(time * b.speed + b.phase) * b.radius;
        float currentY = b.y + (float) Math.sin(time * 2.0f + b.phase) * 0.3f;
        float wingAngle = 45f * (float) Math.sin(time * 20.0f + b.phase);

        gl.glPushMatrix();
        gl.glTranslatef(currentX, currentY, currentZ);
        float dirAngle = (float) Math.toDegrees(Math.atan2(Math.cos(time * b.speed + b.phase), -Math.sin(time * b.speed + b.phase)));
        gl.glRotatef(dirAngle, 0, 1, 0);
        gl.glScalef(0.3f, 0.3f, 0.3f);

        gl.glColor3f(b.colorR, b.colorG, b.colorB);
        // Ala 1
        gl.glPushMatrix(); gl.glRotatef(wingAngle, 0, 0, 1);
        gl.glBegin(GL2.GL_TRIANGLES); gl.glVertex3f(0, 0, 0); gl.glVertex3f(-0.5f, 0.5f, 0.2f); gl.glVertex3f(-0.5f, -0.2f, 0.2f); gl.glEnd();
        gl.glPopMatrix();
        // Ala 2
        gl.glPushMatrix(); gl.glRotatef(-wingAngle, 0, 0, 1);
        gl.glBegin(GL2.GL_TRIANGLES); gl.glVertex3f(0, 0, 0); gl.glVertex3f(0.5f, 0.5f, 0.2f); gl.glVertex3f(0.5f, -0.2f, 0.2f); gl.glEnd();
        gl.glPopMatrix();

        // Cuerpo
        gl.glColor3f(0.1f, 0.1f, 0.1f);
        gl.glLineWidth(2.0f);
        gl.glBegin(GL2.GL_LINES); gl.glVertex3f(0, 0.3f, 0); gl.glVertex3f(0, -0.3f, 0); gl.glEnd();
        gl.glPopMatrix();
    }

    private void drawDetailedTower(GL2 gl, float x, float z, boolean isKing, boolean isBlueTeam) {
        float s = isKing ? 1.4f : 1f;
        gl.glPushMatrix();
        gl.glTranslatef(x, 0, z);
        gl.glScalef(s, s, s);

        // Base de la torre
        gl.glColor3f(0.20f, 0.20f, 0.25f);
        float baseH = 1f; float baseW = 2.6f;
        drawPyramidBase(gl, 0, baseH, baseW, 2.4f);

        // Cuerpo central
        float bodyW = 2.2f; float bodyH = 2f;
        gl.glColor3f(0.5f, 0.5f, 0.55f);
        drawSolidBlock(gl, -bodyW / 2, bodyW / 2, -bodyW / 2, bodyW / 2, baseH, baseH + bodyH);

        // Logo del equipo (Corona)
        gl.glPushMatrix();
        float zFace = isBlueTeam ? bodyW / 2 + 0.01f : -bodyW / 2 - 0.01f;
        float rot = isBlueTeam ? 0 : 180;
        gl.glTranslatef(0, baseH + bodyH / 2 - 0.2f, zFace);
        gl.glRotatef(rot, 0, 1, 0);
        drawCrownLogo(gl);
        gl.glPopMatrix();

        // Parte superior
        float topY = baseH + bodyH;
        gl.glColor3f(0.20f, 0.20f, 0.25f);
        drawSolidBlock(gl, -bodyW / 2 - 0.1f, bodyW / 2 + 0.1f, -bodyW / 2 - 0.1f, bodyW / 2 + 0.1f, topY, topY + 0.5f);

        if (isKing) {
            // Cañón del Rey
            gl.glColor3f(0.1f, 0.1f, 0.1f);
            drawSolidBlock(gl, -0.4f, 0.4f, -0.4f, 0.8f, topY, topY + 0.8f);
            gl.glColor3f(1f, 0.8f, 0f);
            drawSolidBlock(gl, -0.5f, 0.5f, -0.5f, 0.5f, topY, topY + 0.2f);
        } else {
            // Techo Princesa
            gl.glColor3f(isBlueTeam ? 0.3f : 0.9f, isBlueTeam ? 0.4f : 0.3f, isBlueTeam ? 0.9f : 0.3f);
            drawPyramidBase(gl, topY + 0.5f, topY + 1.2f, 1.5f, 0f);
        }

        // Detalles de color del equipo
        gl.glColor3f(isBlueTeam ? 0.3f : 0.9f, isBlueTeam ? 0.4f : 0.3f, isBlueTeam ? 0.9f : 0.3f);
        drawSolidBlock(gl, -bodyW / 2 - 0.2f, -bodyW / 2, -bodyW / 2 + 0.2f, bodyW / 2 - 0.2f, baseH + 0.2f, baseH + bodyH - 0.5f);
        drawSolidBlock(gl, bodyW / 2, bodyW / 2 + 0.2f, -bodyW / 2 + 0.2f, bodyW / 2 - 0.2f, baseH + 0.2f, baseH + bodyH - 0.5f);
        gl.glPopMatrix();
    }

    private void drawCheckerboardFloor(GL2 gl, float minX, float maxX, float minZ, float maxZ) {
        float tileSize = 1f;
        int tilesX = Math.max(1, (int) ((maxX - minX) / tileSize));
        int tilesZ = Math.max(1, (int) ((maxZ - minZ) / tileSize));
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0, 1, 0);
        for (int i = 0; i < tilesX; i++) {
            for (int j = 0; j < tilesZ; j++) {
                float x1 = minX + i * tileSize;
                float z1 = minZ + j * tileSize;
                if ((i + j) % 2 == 0) gl.glColor3f(1f, 1f, 1f); else gl.glColor3f(0.85f, 0.85f, 0.85f);
                gl.glTexCoord2f(0, 0); gl.glVertex3f(x1, 0, z1 + tileSize);
                gl.glTexCoord2f(1, 0); gl.glVertex3f(x1 + tileSize, 0, z1 + tileSize);
                gl.glTexCoord2f(1, 1); gl.glVertex3f(x1 + tileSize, 0, z1);
                gl.glTexCoord2f(0, 1); gl.glVertex3f(x1, 0, z1);
            }
        }
        gl.glEnd();
    }

    private void drawBlockSolid(GL2 gl, float minX, float maxX, float minZ, float maxZ, float minY, float maxY, float uScale, float vScale) {
        float width = maxX - minX;
        float depth = maxZ - minZ;
        // Cara superior
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0, 1, 0);
        gl.glTexCoord2f(0, 0); gl.glVertex3f(minX, maxY, maxZ);
        gl.glTexCoord2f(width * uScale, 0); gl.glVertex3f(maxX, maxY, maxZ);
        gl.glTexCoord2f(width * uScale, depth * vScale); gl.glVertex3f(maxX, maxY, minZ);
        gl.glTexCoord2f(0, depth * vScale); gl.glVertex3f(minX, maxY, minZ);
        gl.glEnd();
        // Paredes laterales
        drawWall(gl, minX, maxX, maxZ, maxZ, minY, maxY);
        drawWall(gl, minX, maxX, minZ, minZ, minY, maxY);
        drawWall(gl, minX, minX, minZ, maxZ, minY, maxY);
        drawWall(gl, maxX, maxX, minZ, maxZ, minY, maxY);
    }

    private void drawSolidBlock(GL2 gl, float x1, float x2, float z1, float z2, float y1, float y2) {
        drawBlockSolid(gl, x1, x2, z1, z2, y1, y2, TEX_SCALE, TEX_SCALE);
    }

    private void drawWall(GL2 gl, float x1, float x2, float z1, float z2, float yBottom, float yTop) {
        float width = (float) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(z2 - z1, 2));
        float height = yTop - yBottom;
        float uMax = Math.max(1f, width * TEX_SCALE);
        float vMax = Math.max(1f, height * TEX_SCALE);
        gl.glBegin(GL2.GL_QUADS);
        if (x1 == x2) gl.glNormal3f(x1 > 0 ? -1 : 1, 0, 0); else gl.glNormal3f(0, 0, z1 > 0 ? -1 : 1);
        gl.glTexCoord2f(0, 0); gl.glVertex3f(x1, yBottom, z1);
        gl.glTexCoord2f(uMax, 0); gl.glVertex3f(x2, yBottom, z2);
        gl.glTexCoord2f(uMax, vMax); gl.glVertex3f(x2, yTop, z2);
        gl.glTexCoord2f(0, vMax); gl.glVertex3f(x1, yTop, z1);
        gl.glEnd();
    }

    private void drawPyramidBase(GL2 gl, float yBottom, float yTop, float wBottom, float wTop) {
        float b2 = wBottom / 2f;
        float t2 = wTop / 2f;
        gl.glBegin(GL2.GL_QUADS);
        // Frente
        gl.glNormal3f(0, 0.5f, 1); gl.glTexCoord2f(0, 0); gl.glVertex3f(-b2, yBottom, b2); gl.glTexCoord2f(1, 0); gl.glVertex3f(b2, yBottom, b2); gl.glTexCoord2f(0.8f, 1); gl.glVertex3f(t2, yTop, t2); gl.glTexCoord2f(0.2f, 1); gl.glVertex3f(-t2, yTop, t2);
        // Atrás
        gl.glNormal3f(0, 0.5f, -1); gl.glTexCoord2f(0, 0); gl.glVertex3f(b2, yBottom, -b2); gl.glTexCoord2f(1, 0); gl.glVertex3f(-b2, yBottom, -b2); gl.glTexCoord2f(0.8f, 1); gl.glVertex3f(-t2, yTop, -t2); gl.glTexCoord2f(0.2f, 1); gl.glVertex3f(t2, yTop, -t2);
        // Izquierda
        gl.glNormal3f(-1, 0.5f, 0); gl.glTexCoord2f(0, 0); gl.glVertex3f(-b2, yBottom, -b2); gl.glTexCoord2f(1, 0); gl.glVertex3f(-b2, yBottom, b2); gl.glTexCoord2f(0.8f, 1); gl.glVertex3f(-t2, yTop, t2); gl.glTexCoord2f(0.2f, 1); gl.glVertex3f(-t2, yTop, -t2);
        // Derecha
        gl.glNormal3f(1, 0.5f, 0); gl.glTexCoord2f(0, 0); gl.glVertex3f(b2, yBottom, b2); gl.glTexCoord2f(1, 0); gl.glVertex3f(b2, yBottom, -b2); gl.glTexCoord2f(0.8f, 1); gl.glVertex3f(t2, yTop, -t2); gl.glTexCoord2f(0.2f, 1); gl.glVertex3f(t2, yTop, t2);
        // Tapa
        gl.glNormal3f(0, 1, 0); gl.glVertex3f(-t2, yTop, t2); gl.glVertex3f(t2, yTop, t2); gl.glVertex3f(t2, yTop, -t2); gl.glVertex3f(-t2, yTop, -t2);
        gl.glEnd();
    }

    private void drawCrownLogo(GL2 gl) {
        gl.glColor3f(1f, 0.8f, 0f);
        gl.glBegin(GL2.GL_POLYGON);
        gl.glNormal3f(0, 0, 1);
        gl.glVertex3f(-0.6f, -0.5f, 0);
        gl.glVertex3f(0.6f, -0.5f, 0);
        gl.glVertex3f(0.6f, 0.2f, 0);
        gl.glVertex3f(0.3f, 0f, 0);
        gl.glVertex3f(0f, 0.5f, 0);
        gl.glVertex3f(-0.3f, 0f, 0);
        gl.glVertex3f(-0.6f, 0.2f, 0);
        gl.glEnd();
    }

    private void drawTree(GL2 gl, TreeData t) {
        gl.glPushMatrix();
        gl.glTranslatef(t.x, 0, t.z);
        gl.glScalef(t.scale, t.scale, t.scale);
        gl.glRotatef(t.rot, 0, 1, 0);
        // Tronco
        gl.glColor3f(0.4f, 0.25f, 0.1f);
        drawSolidBlock(gl, -0.2f, 0.2f, -0.2f, 0.2f, 0, 1.2f);
        // Hojas (Pirámide)
        gl.glColor3f(0.1f, 0.4f, 0.1f);
        drawPyramidBase(gl, 1f, 2.4f, 1.6f, 0.4f);
        gl.glPopMatrix();
    }

    private void drawGoldPile(GL2 gl, float x, float z) {
        gl.glPushMatrix();
        gl.glTranslatef(x, 0.25f, z);
        gl.glColor3f(1f, 1f, 1f); // Tinte blanco para recibir el color de la textura
        for (int i = 0; i < 5; i++) {
            gl.glRotatef(45f * i, 0, 1, 0);
            drawSolidBlock(gl, -0.3f, 0.3f, -0.3f, 0.3f, 0, 0.3f + (i * 0.1f));
        }
        gl.glPopMatrix();
    }

    private void drawWaterSurface(GL2 gl, boolean visibleColor) {
        if (visibleColor) gl.glColor4f(1f, 1f, 1f, 0.6f);
        float hw = TOTAL_WIDTH / 2f;
        float rw = RIVER_WIDTH / 2f;
        float uMax = hw * TEX_SCALE;
        float vMax = rw * TEX_SCALE;
        float uOffset = waterOffset % (uMax * 2f);
        
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0f, 1f, 0f);
        gl.glTexCoord2f(0 + uOffset, 0); gl.glVertex3f(-hw, WATER_HEIGHT, rw);
        gl.glTexCoord2f(uMax * 2 + uOffset, 0); gl.glVertex3f(hw, WATER_HEIGHT, rw);
        gl.glTexCoord2f(uMax * 2 + uOffset, vMax); gl.glVertex3f(hw, WATER_HEIGHT, -rw);
        gl.glTexCoord2f(0 + uOffset, vMax); gl.glVertex3f(-hw, WATER_HEIGHT, -rw);
        gl.glEnd();
    }

    private void drawKnight(GL2 gl) {
        gl.glPushMatrix();
        gl.glScalef(0.7f, 0.7f, 0.7f);
        gl.glColor3f(0.65f, 0.65f, 0.65f); // Armadura
        gl.glPushMatrix(); gl.glTranslatef(0, 0.5f, 0); glut.glutSolidSphere(0.35, 14, 14); gl.glPopMatrix(); // Cabeza
        gl.glColor3f(0.4f, 0.2f, 0.1f); gl.glPushMatrix(); gl.glTranslatef(0, -0.25f, 0); glut.glutSolidCube(0.7f); gl.glPopMatrix(); // Cuerpo
        gl.glColor3f(0.9f, 0.8f, 0.18f); gl.glPushMatrix(); gl.glTranslatef(-0.45f, -0.25f, 0); gl.glScalef(0.9f, 0.6f, 0.25f); glut.glutSolidCube(0.35f); gl.glPopMatrix(); // Escudo
        gl.glPopMatrix();
    }

    private void drawArcher(GL2 gl) {
        gl.glPushMatrix();
        gl.glScalef(0.6f, 0.6f, 0.6f);
        gl.glColor3f(0.9f, 0.75f, 0.6f); // Piel
        gl.glPushMatrix(); gl.glTranslatef(0, 0.5f, 0); glut.glutSolidSphere(0.32, 12, 12); gl.glPopMatrix(); // Cabeza
        gl.glColor3f(0.3f, 0.1f, 0.1f); gl.glPushMatrix(); gl.glTranslatef(0, 0.3f, -0.1f); glut.glutSolidCube(0.45f); gl.glPopMatrix(); // Gorro
        gl.glColor3f(0.3f, 0.9f, 0.4f); gl.glPushMatrix(); gl.glTranslatef(0, -0.25f, 0); glut.glutSolidCube(0.6f); gl.glPopMatrix(); // Túnica
        gl.glColor3f(0.3f, 0.15f, 0.05f); gl.glPushMatrix(); gl.glTranslatef(0.5f, -0.25f, 0); gl.glRotatef(90, 0, 1, 0); glut.glutSolidTorus(0.04, 0.28, 12, 12); gl.glPopMatrix(); // Arco
        gl.glPopMatrix();
    }

    // ==========================================
    // LÓGICA DE MOVIMIENTO Y COLISIONES
    // ==========================================
    private void updateMovement() {
        long now = System.nanoTime();
        float deltaSec = (now - lastTimeNs) / 1_000_000_000f;
        if (deltaSec > 0.1f) deltaSec = 0.1f;
        lastTimeNs = now;

        float speed = 10f;
        if (isKeyDown(KeyEvent.VK_SHIFT)) speed = 18f;
        float dS = speed * deltaSec;
        float yr = (float) Math.toRadians(camYaw);
        float dx = 0, dz = 0;

        if (isKeyDown(KeyEvent.VK_W)) { dx += Math.cos(yr); dz += Math.sin(yr); }
        if (isKeyDown(KeyEvent.VK_S)) { dx -= Math.cos(yr); dz -= Math.sin(yr); }
        if (isKeyDown(KeyEvent.VK_D)) { dx += Math.cos(yr + Math.PI / 2); dz += Math.sin(yr + Math.PI / 2); }
        if (isKeyDown(KeyEvent.VK_A)) { dx -= Math.cos(yr + Math.PI / 2); dz -= Math.sin(yr + Math.PI / 2); }

        // Aplicar movimiento solo si es válido o si estamos en GodMode
        if (godMode || esPosicionValida(camX + (dx * dS), camZ)) camX += dx * dS;
        if (godMode || esPosicionValida(camX, camZ + (dz * dS))) camZ += dz * dS;
    }

    private boolean esPosicionValida(float x, float z) {
        float r = PLAYER_RADIUS;
        float hw = PLAYABLE_WIDTH / 2f;
        float hl = PLAYABLE_LENGTH / 2f + 4f;

        // Limites de la arena
        if (x < -hw + r || x > hw - r || z < -hl + r || z > hl - r) return false;

        // Colisión con el río
        boolean enRio = (z > -RIVER_WIDTH / 2f - r && z < RIVER_WIDTH / 2f + r);
        if (enRio) {
            float bHW = BRIDGE_WIDTH / 2f;
            boolean enP1 = (x > BRIDGE_CENTER_X - bHW && x < BRIDGE_CENTER_X + bHW);
            boolean enP2 = (x > -BRIDGE_CENTER_X - bHW && x < -BRIDGE_CENTER_X + bHW);
            if (!enP1 && !enP2) return false; // Si está en el río pero no en el puente
        }

        // Colisión con torres
        float tSize = TOWER_COLLISION_SIZE + r;
        if (inBox(x, z, 0, -TOWER_KING_Z, tSize * 1.5f)) return false;
        if (inBox(x, z, -TOWER_PRINCESS_X, -TOWER_PRINCESS_Z, tSize)) return false;
        if (inBox(x, z, TOWER_PRINCESS_X, -TOWER_PRINCESS_Z, tSize)) return false;
        if (inBox(x, z, 0, TOWER_KING_Z, tSize * 1.5f)) return false;
        if (inBox(x, z, -TOWER_PRINCESS_X, TOWER_PRINCESS_Z, tSize)) return false;
        if (inBox(x, z, TOWER_PRINCESS_X, TOWER_PRINCESS_Z, tSize)) return false;

        return true;
    }

    private boolean inBox(float x, float z, float cX, float cZ, float rad) {
        return (x > cX - rad && x < cX + rad && z > cZ - rad && z < cZ + rad);
    }

    // ==========================================
    // LISTENERS DE TECLADO Y RATÓN
    // ==========================================
    
    private boolean isKeyDown(int code) {
        return (code >= 0 && code < keys.length) && keys[code];
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        // Capturar/Liberar cursor
        mouseCaptured = !mouseCaptured;
        window.setPointerVisible(!mouseCaptured);
        window.confinePointer(mouseCaptured);
        window.requestFocus();
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        if (!mouseCaptured) return;
        int cx = window.getWidth() / 2;
        int cy = window.getHeight() / 2;
        camYaw += (e.getX() - cx) * mouseSensitivity;
        camPitch -= (e.getY() - cy) * mouseSensitivity;
        
        // Limitar la vista vertical
        if (camPitch > 89f) camPitch = 89f;
        if (camPitch < -89f) camPitch = -89f;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int kc = e.getKeyCode();
        if (kc >= 0 && kc < keys.length) keys[kc] = true;
        if (kc == KeyEvent.VK_ESCAPE) window.destroy();
        if (kc == KeyEvent.VK_G) godMode = !godMode;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int kc = e.getKeyCode();
        if (kc >= 0 && kc < keys.length) keys[kc] = false;
    }

    @Override public void mouseDragged(MouseEvent e) { mouseMoved(e); }
    @Override public void mouseEntered(MouseEvent e) {}
    @Override public void mouseExited(MouseEvent e) {}
    @Override public void mousePressed(MouseEvent e) {}
    @Override public void mouseReleased(MouseEvent e) {}
    @Override public void mouseWheelMoved(MouseEvent e) {}
    @Override public void dispose(GLAutoDrawable drawable) {}
}
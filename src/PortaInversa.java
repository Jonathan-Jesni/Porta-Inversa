import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.newt.Window;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.opengl.util.FPSAnimator;
import java.awt.Font;
import com.jogamp.opengl.util.awt.TextRenderer;

public class PortaInversa implements GLEventListener, KeyListener, MouseListener {

    private GLU glu = new GLU();
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;

    // --- MAZE ---
    private final int[][] mazeLayout = {
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
            { 1, 0, 0, 0, 1, 0, 0, 0, 0, 1 },
            { 1, 0, 1, 0, 1, 0, 1, 1, 0, 1 },
            { 1, 0, 1, 0, 0, 0, 0, 1, 0, 1 },
            { 1, 0, 1, 1, 1, 1, 0, 1, 0, 1 },
            { 1, 0, 0, 0, 0, 1, 0, 0, 0, 1 },
            { 1, 1, 1, 1, 0, 1, 1, 1, 0, 1 },
            { 1, 0, 0, 0, 0, 0, 0, 1, 0, 1 },
            { 1, 0, 1, 1, 1, 1, 0, 0, 0, 1 },
            { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 }
    };

    private float cubeSize = 2.0f;
    private float startX = -(mazeLayout[0].length * cubeSize) / 2.0f + (cubeSize / 2.0f);
    private float startZ = -(mazeLayout.length * cubeSize) / 2.0f + (cubeSize / 2.0f);

    // --- CAMERA & MOVEMENT ---
    // Start inside the maze at grid [1][1] approx
    private float cameraX = -7.0f;
    private float cameraY = 1.0f;
    private float cameraZ = -7.0f;

    private boolean wPressed, aPressed, sPressed, dPressed;

    private float lookX = 0.0f;
    private float lookY = 1.0f;
    private float lookZ = 0.0f;

    private float cameraAngle = 0.0f;
    private float walkSpeed = 0.08f;
    private float sprintSpeed = 0.18f;

    // Mouse tracking variables
    private float mouseSensitivity = 0.2f;

    private int teleportCooldown = 0;
    private boolean isGravityFlipped = false;
    private int gravityCooldown = 0;

    private boolean shiftPressed, spacePressed;
    private boolean isJumping = false;
    private float verticalVelocity = 0.0f;
    private final float GRAVITY = 0.012f;
    private final float JUMP_STRENGTH = 0.22f;
    private final float PLAYER_HEIGHT = 1.0f;
    private TextRenderer textRenderer;

    private void updateLook() {
        lookX = cameraX + (float) Math.cos(Math.toRadians(cameraAngle));
        lookY = cameraY;
        lookZ = cameraZ + (float) Math.sin(Math.toRadians(cameraAngle));
    }

    private float[] gridTo3D(int row, int col) {
        return new float[]{startX + col * cubeSize, startZ + row * cubeSize};
    }

    // --- EXPLICIT COLLISION ANALYSIS ---
    private boolean isValidPosition(float targetX, float targetZ) {
        int rows = mazeLayout.length;
        int cols = mazeLayout[0].length;

        // Deterministic mapping from 3D coordinates back to the 2D array grid
        // Padding is added (0.4f) to create a "hitbox" around the player so you don't
        // clip your eyes into walls
        float playerHitboxPadding = 0.4f;

        // Check the four corners of the player's bounding box
        float[][] corners = {
                { targetX - playerHitboxPadding, targetZ - playerHitboxPadding },
                { targetX + playerHitboxPadding, targetZ - playerHitboxPadding },
                { targetX - playerHitboxPadding, targetZ + playerHitboxPadding },
                { targetX + playerHitboxPadding, targetZ + playerHitboxPadding }
        };

        for (float[] corner : corners) {
            int c = (int) Math.floor((corner[0] - startX + cubeSize / 2.0f) / cubeSize);
            int r = (int) Math.floor((corner[1] - startZ + cubeSize / 2.0f) / cubeSize);

            // If any corner is out of bounds or hits a wall (1), the move is invalid
            if (r < 0 || r >= rows || c < 0 || c >= cols || mazeLayout[r][c] == 1) {
                return false;
            }
        }
        return true;
    }

    private void drawCube(GL2 gl, float x, float y, float z, float size) {
        float half = size / 2.0f;
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor3f(0.5f, 0.5f, 0.5f);
        gl.glNormal3f(0.0f, 0.0f, 1.0f);
        gl.glVertex3f(x - half, y - half, z + half);
        gl.glVertex3f(x + half, y - half, z + half);
        gl.glVertex3f(x + half, y + half, z + half);
        gl.glVertex3f(x - half, y + half, z + half);
        gl.glColor3f(0.4f, 0.4f, 0.4f);
        gl.glNormal3f(0.0f, 0.0f, -1.0f);
        gl.glVertex3f(x - half, y - half, z - half);
        gl.glVertex3f(x - half, y + half, z - half);
        gl.glVertex3f(x + half, y + half, z - half);
        gl.glVertex3f(x + half, y - half, z - half);
        gl.glColor3f(0.6f, 0.6f, 0.6f);
        gl.glNormal3f(0.0f, 1.0f, 0.0f);
        gl.glVertex3f(x - half, y + half, z - half);
        gl.glVertex3f(x - half, y + half, z + half);
        gl.glVertex3f(x + half, y + half, z + half);
        gl.glVertex3f(x + half, y + half, z - half);
        gl.glColor3f(0.3f, 0.3f, 0.3f);
        gl.glNormal3f(0.0f, -1.0f, 0.0f);
        gl.glVertex3f(x - half, y - half, z - half);
        gl.glVertex3f(x + half, y - half, z - half);
        gl.glVertex3f(x + half, y - half, z + half);
        gl.glVertex3f(x - half, y - half, z + half);
        gl.glColor3f(0.45f, 0.45f, 0.45f);
        gl.glNormal3f(1.0f, 0.0f, 0.0f);
        gl.glVertex3f(x + half, y - half, z - half);
        gl.glVertex3f(x + half, y + half, z - half);
        gl.glVertex3f(x + half, y + half, z + half);
        gl.glVertex3f(x + half, y - half, z + half);
        gl.glColor3f(0.55f, 0.55f, 0.55f);
        gl.glNormal3f(-1.0f, 0.0f, 0.0f);
        gl.glVertex3f(x - half, y - half, z - half);
        gl.glVertex3f(x - half, y - half, z + half);
        gl.glVertex3f(x - half, y + half, z + half);
        gl.glVertex3f(x - half, y + half, z - half);
        gl.glEnd();
    }

    private void drawPortalFrame(GL2 gl, float x, float z, float r, float g, float b) {
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor4f(r, g, b, 0.6f);

        float half = 0.8f;
        // Quad 1: parallel to X
        gl.glVertex3f(x - half, 0.0f, z);
        gl.glVertex3f(x + half, 0.0f, z);
        gl.glVertex3f(x + half, cubeSize, z);
        gl.glVertex3f(x - half, cubeSize, z);
        // Quad 2: parallel to Z
        gl.glVertex3f(x, 0.0f, z - half);
        gl.glVertex3f(x, 0.0f, z + half);
        gl.glVertex3f(x, cubeSize, z + half);
        gl.glVertex3f(x, cubeSize, z - half);

        gl.glEnd();
        gl.glDisable(GL2.GL_BLEND);
    }

    private void drawMaze(GL2 gl) {
        int rows = mazeLayout.length;
        int cols = mazeLayout[0].length;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (mazeLayout[r][c] == 1) {
                    drawCube(gl, startX + c * cubeSize, cubeSize / 2.0f, startZ + r * cubeSize, cubeSize);
                }
            }
        }
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        
        textRenderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 18));
        
        updateLook();
        System.out.println("Portals active: [1][2] <-> [8][7]");
        ((Window)drawable).setPointerVisible(false);
        ((Window)drawable).confinePointer(true);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        
        gl.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        runGameLogic(gl);
        drawMinimap(gl);
    }

    private void runGameLogic(GL2 gl) {

        if (teleportCooldown > 0) teleportCooldown--;
        if (gravityCooldown > 0) gravityCooldown--;

        float nextX = cameraX;
        float nextZ = cameraZ;

        float currentSpeed = shiftPressed ? sprintSpeed : walkSpeed;

        if (wPressed) {
            nextX += Math.cos(Math.toRadians(cameraAngle)) * currentSpeed;
            nextZ += Math.sin(Math.toRadians(cameraAngle)) * currentSpeed;
        }
        if (sPressed) {
            nextX -= Math.cos(Math.toRadians(cameraAngle)) * currentSpeed;
            nextZ -= Math.sin(Math.toRadians(cameraAngle)) * currentSpeed;
        }
        if (aPressed) {
            nextX += Math.cos(Math.toRadians(cameraAngle - 90)) * currentSpeed;
            nextZ += Math.sin(Math.toRadians(cameraAngle - 90)) * currentSpeed;
        }
        if (dPressed) {
            nextX += Math.cos(Math.toRadians(cameraAngle + 90)) * currentSpeed;
            nextZ += Math.sin(Math.toRadians(cameraAngle + 90)) * currentSpeed;
        }

        if (isValidPosition(nextX, cameraZ))
            cameraX = nextX;
        if (isValidPosition(cameraX, nextZ))
            cameraZ = nextZ;

        if (spacePressed && !isJumping) {
            verticalVelocity = JUMP_STRENGTH;
            isJumping = true;
        }
        cameraY += verticalVelocity;
        verticalVelocity -= GRAVITY;
        if (cameraY <= PLAYER_HEIGHT) {
            cameraY = PLAYER_HEIGHT;
            verticalVelocity = 0.0f;
            isJumping = false;
        }

        float[] portalA = gridTo3D(1, 2);
        float[] portalB = gridTo3D(8, 7);

        if (teleportCooldown == 0) {
            double distA = Math.sqrt(Math.pow(cameraX - portalA[0], 2) + Math.pow(cameraZ - portalA[1], 2));
            double distB = Math.sqrt(Math.pow(cameraX - portalB[0], 2) + Math.pow(cameraZ - portalB[1], 2));
            if (distA < 0.8f) {
                cameraX = portalB[0];
                cameraZ = portalB[1];
                teleportCooldown = 80;
            } else if (distB < 0.8f) {
                cameraX = portalA[0];
                cameraZ = portalA[1];
                teleportCooldown = 80;
            }
        }

        float[] gravTrigger = gridTo3D(5, 6);
        if (gravityCooldown == 0) {
            double distG = Math.sqrt(Math.pow(cameraX - gravTrigger[0], 2) + Math.pow(cameraZ - gravTrigger[1], 2));
            if (distG < 0.8f) {
                isGravityFlipped = !isGravityFlipped;
                gravityCooldown = 60;
            }
        }

        updateLook();

        glu.gluLookAt(cameraX, cameraY, cameraZ, lookX, lookY, lookZ, 0.0f, isGravityFlipped ? -1.0f : 1.0f, 0.0f);

        float[] lightPos = {cameraX, cameraY, cameraZ, 1.0f};
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPos, 0);
        gl.glLightf(GL2.GL_LIGHT0, GL2.GL_QUADRATIC_ATTENUATION, 0.02f);

        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0.0f, 1.0f, 0.0f);
        gl.glColor3f(0.15f, 0.15f, 0.15f);
        gl.glVertex3f(-20.0f, 0.0f, -20.0f);
        gl.glColor3f(0.2f, 0.2f, 0.2f);
        gl.glVertex3f(-20.0f, 0.0f, 20.0f);
        gl.glColor3f(0.15f, 0.15f, 0.15f);
        gl.glVertex3f(20.0f, 0.0f, 20.0f);
        gl.glColor3f(0.2f, 0.2f, 0.2f);
        gl.glVertex3f(20.0f, 0.0f, -20.0f);
        gl.glEnd();

        drawMaze(gl);

        // Draw Gravity Trigger
        float[] pG = gridTo3D(5, 6);
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor3f(0.0f, 1.0f, 0.0f);
        gl.glNormal3f(0.0f, 1.0f, 0.0f);
        // Slightly offset Y to avoid z-fighting with the floor
        gl.glVertex3f(pG[0] - 0.8f, 0.01f, pG[1] - 0.8f);
        gl.glVertex3f(pG[0] + 0.8f, 0.01f, pG[1] - 0.8f);
        gl.glVertex3f(pG[0] + 0.8f, 0.01f, pG[1] + 0.8f);
        gl.glVertex3f(pG[0] - 0.8f, 0.01f, pG[1] + 0.8f);
        gl.glEnd();

        // Draw Portal A (Blue)
        float[] pA = gridTo3D(1, 2);
        drawPortalFrame(gl, pA[0], pA[1], 0.0f, 0.5f, 1.0f);

        // Draw Portal B (Orange)
        float[] pB = gridTo3D(8, 7);
        drawPortalFrame(gl, pB[0], pB[1], 1.0f, 0.5f, 0.0f);

        textRenderer.beginRendering(WINDOW_WIDTH, WINDOW_HEIGHT);
        textRenderer.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        textRenderer.draw(String.format("Coords: X=%.2f, Z=%.2f | Flipped: %b", cameraX, cameraZ, isGravityFlipped), WINDOW_WIDTH - 450, WINDOW_HEIGHT - 30);
        textRenderer.endRendering();
    }

    private void drawMinimap(GL2 gl) {
        int w = 150;
        int h = 150;
        
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        glu.gluOrtho2D(0, WINDOW_WIDTH, WINDOW_HEIGHT, 0);
        
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        
        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glDisable(GL2.GL_LIGHTING);
        
        float cellW = (float) w / mazeLayout[0].length;
        float cellH = (float) h / mazeLayout.length;
        
        gl.glBegin(GL2.GL_QUADS);
        for(int r = 0; r < mazeLayout.length; r++) {
            for(int c = 0; c < mazeLayout[0].length; c++) {
                if(mazeLayout[r][c] == 1) {
                    gl.glColor3f(0.5f, 0.5f, 0.5f);
                } else {
                    gl.glColor3f(0.1f, 0.1f, 0.1f);
                }
                gl.glVertex2f(c * cellW, r * cellH);
                gl.glVertex2f((c + 1) * cellW, r * cellH);
                gl.glVertex2f((c + 1) * cellW, (r + 1) * cellH);
                gl.glVertex2f(c * cellW, (r + 1) * cellH);
            }
        }
        gl.glEnd();
        
        float pX = ((cameraX - startX + cubeSize / 2.0f) / cubeSize) * cellW;
        float pZ = ((cameraZ - startZ + cubeSize / 2.0f) / cubeSize) * cellH;
        
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor3f(1.0f, 0.0f, 0.0f);
        gl.glVertex2f(pX - 2, pZ - 2);
        gl.glVertex2f(pX + 2, pZ - 2);
        gl.glVertex2f(pX + 2, pZ + 2);
        gl.glVertex2f(pX - 2, pZ + 2);
        gl.glEnd();
        
        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glColor3f(0.0f, 0.0f, 0.0f);
        gl.glVertex2f(0, 0);
        gl.glVertex2f(w, 0);
        gl.glVertex2f(w, h);
        gl.glVertex2f(0, h);
        gl.glEnd();
        
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_LIGHTING);
        
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();
        if (height == 0)
            height = 1;
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(45.0, (float) width / height, 0.1, 100.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
    }

    // --- KEYBOARD CONTROLS ---
    @Override
    public void keyPressed(KeyEvent e) {
        // Essential: Allow user to escape since the mouse is trapped
        if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
            System.exit(0);
        }

        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:
                wPressed = true;
                break;
            case KeyEvent.VK_S:
                sPressed = true;
                break;
            case KeyEvent.VK_A:
                aPressed = true;
                break;
            case KeyEvent.VK_D:
                dPressed = true;
                break;
            case KeyEvent.VK_SHIFT:
                shiftPressed = true;
                break;
            case KeyEvent.VK_SPACE:
                spacePressed = true;
                break;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        switch (e.getKeyCode()) {
            case KeyEvent.VK_W:
                wPressed = false;
                break;
            case KeyEvent.VK_S:
                sPressed = false;
                break;
            case KeyEvent.VK_A:
                aPressed = false;
                break;
            case KeyEvent.VK_D:
                dPressed = false;
                break;
            case KeyEvent.VK_SHIFT:
                shiftPressed = false;
                break;
            case KeyEvent.VK_SPACE:
                spacePressed = false;
                break;
        }
    }

    // --- MOUSE CONTROLS ---
    @Override
    public void mouseMoved(MouseEvent e) {
        Window window = (Window) e.getSource();
        int centerX = window.getWidth() / 2;
        int centerY = window.getHeight() / 2;

        if (e.getX() == centerX && e.getY() == centerY) {
            return;
        }

        int deltaX = e.getX() - centerX;
        cameraAngle += deltaX * mouseSensitivity;
        updateLook();

        window.warpPointer(centerX, centerY);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseMoved(e);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseWheelMoved(MouseEvent e) {
    }

    public static void main(String[] args) {
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities capabilities = new GLCapabilities(profile);
        GLWindow window = GLWindow.create(capabilities);
        window.setTitle("Porta Inversa - Esc to Exit");
        window.setSurfaceSize(WINDOW_WIDTH, WINDOW_HEIGHT);

        PortaInversa game = new PortaInversa();
        window.addGLEventListener(game);
        window.addKeyListener(game);
        window.addMouseListener(game); // Attach the mouse listener!

        // Hide and confine the pointer for a true FPS feel
        window.setPointerVisible(false);
        window.confinePointer(true);

        window.addWindowListener(new WindowAdapter() {
            @Override
            public void windowDestroyNotify(WindowEvent e) {
                System.exit(0);
            }
        });

        window.setVisible(true);
        FPSAnimator animator = new FPSAnimator(window, 60, true);
        animator.start();
    }
}
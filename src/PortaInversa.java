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
    private float moveSpeed = 0.15f;

    // Mouse tracking variables
    private float mouseSensitivity = 0.2f;

    private void updateLook() {
        lookX = cameraX + (float) Math.cos(Math.toRadians(cameraAngle));
        lookZ = cameraZ + (float) Math.sin(Math.toRadians(cameraAngle));
    }

    // --- EXPLICIT COLLISION ANALYSIS ---
    private boolean isValidPosition(float targetX, float targetZ) {
        float cubeSize = 2.0f;
        int rows = mazeLayout.length;
        int cols = mazeLayout[0].length;
        float startX = -(cols * cubeSize) / 2.0f + (cubeSize / 2.0f);
        float startZ = -(rows * cubeSize) / 2.0f + (cubeSize / 2.0f);

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
        gl.glVertex3f(x - half, y - half, z + half);
        gl.glVertex3f(x + half, y - half, z + half);
        gl.glVertex3f(x + half, y + half, z + half);
        gl.glVertex3f(x - half, y + half, z + half);
        gl.glColor3f(0.4f, 0.4f, 0.4f);
        gl.glVertex3f(x - half, y - half, z - half);
        gl.glVertex3f(x - half, y + half, z - half);
        gl.glVertex3f(x + half, y + half, z - half);
        gl.glVertex3f(x + half, y - half, z - half);
        gl.glColor3f(0.6f, 0.6f, 0.6f);
        gl.glVertex3f(x - half, y + half, z - half);
        gl.glVertex3f(x - half, y + half, z + half);
        gl.glVertex3f(x + half, y + half, z + half);
        gl.glVertex3f(x + half, y + half, z - half);
        gl.glColor3f(0.3f, 0.3f, 0.3f);
        gl.glVertex3f(x - half, y - half, z - half);
        gl.glVertex3f(x + half, y - half, z - half);
        gl.glVertex3f(x + half, y - half, z + half);
        gl.glVertex3f(x - half, y - half, z + half);
        gl.glColor3f(0.45f, 0.45f, 0.45f);
        gl.glVertex3f(x + half, y - half, z - half);
        gl.glVertex3f(x + half, y + half, z - half);
        gl.glVertex3f(x + half, y + half, z + half);
        gl.glVertex3f(x + half, y - half, z + half);
        gl.glColor3f(0.55f, 0.55f, 0.55f);
        gl.glVertex3f(x - half, y - half, z - half);
        gl.glVertex3f(x - half, y - half, z + half);
        gl.glVertex3f(x - half, y + half, z + half);
        gl.glVertex3f(x - half, y + half, z - half);
        gl.glEnd();
    }

    private void drawMaze(GL2 gl) {
        float cubeSize = 2.0f;
        int rows = mazeLayout.length;
        int cols = mazeLayout[0].length;
        float startX = -(cols * cubeSize) / 2.0f + (cubeSize / 2.0f);
        float startZ = -(rows * cubeSize) / 2.0f + (cubeSize / 2.0f);
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
        updateLook();
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        float nextX = cameraX;
        float nextZ = cameraZ;

        if (wPressed) {
            nextX += Math.cos(Math.toRadians(cameraAngle)) * moveSpeed;
            nextZ += Math.sin(Math.toRadians(cameraAngle)) * moveSpeed;
        }
        if (sPressed) {
            nextX -= Math.cos(Math.toRadians(cameraAngle)) * moveSpeed;
            nextZ -= Math.sin(Math.toRadians(cameraAngle)) * moveSpeed;
        }
        if (aPressed) {
            nextX += Math.cos(Math.toRadians(cameraAngle - 90)) * moveSpeed;
            nextZ += Math.sin(Math.toRadians(cameraAngle - 90)) * moveSpeed;
        }
        if (dPressed) {
            nextX += Math.cos(Math.toRadians(cameraAngle + 90)) * moveSpeed;
            nextZ += Math.sin(Math.toRadians(cameraAngle + 90)) * moveSpeed;
        }

        if (isValidPosition(nextX, cameraZ))
            cameraX = nextX;
        if (isValidPosition(cameraX, nextZ))
            cameraZ = nextZ;

        updateLook();

        glu.gluLookAt(cameraX, cameraY, cameraZ, lookX, lookY, lookZ, 0.0f, 1.0f, 0.0f);

        gl.glBegin(GL2.GL_QUADS);
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
        window.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);

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
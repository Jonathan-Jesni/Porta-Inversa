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
import com.jogamp.opengl.util.texture.Texture;
import com.jogamp.opengl.util.texture.TextureIO;
import java.io.File;
import java.io.IOException;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

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
    private final float wallHeight = 4.0f;
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
    private boolean onGravityPad = false;

    private boolean shiftPressed, spacePressed;
    private boolean isJumping = false;
    private float verticalVelocity = 0.0f;
    private final float GRAVITY = 0.012f;
    private final float JUMP_STRENGTH = 0.22f;
    private final float PLAYER_HEIGHT = 1.0f;
    private TextRenderer textRenderer;
    private Texture wallTexture;
    private Texture floorTexture;
    private Texture ceilingTexture;

    // --- PHASE 2: FBO (one per portal for bidirectional views) ---
    private int[] fboIdA       = new int[1];   // captures view from B → displayed on A
    private int[] texIdA       = new int[1];
    private int[] depthIdA     = new int[1];

    private int[] fboIdB       = new int[1];   // captures view from A → displayed on B
    private int[] texIdB       = new int[1];
    private int[] depthIdB     = new int[1];

    private final int FBO_WIDTH  = 512;
    private final int FBO_HEIGHT = 800;

    /** Drives the pulsing portal border brightness (0 → 2π loop). */
    private float glowPhase = 0.0f;

    private void updateLook() {
        lookX = cameraX + (float) Math.cos(Math.toRadians(cameraAngle));
        lookY = cameraY;
        lookZ = cameraZ + (float) Math.sin(Math.toRadians(cameraAngle));
    }

    private float[] gridTo3D(int row, int col) {
        return new float[] { startX + col * cubeSize, startZ + row * cubeSize };
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
        if (wallTexture != null) {
            wallTexture.bind(gl);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
        }
        gl.glBegin(GL2.GL_QUADS);
        gl.glColor3f(0.5f, 0.5f, 0.5f);
        gl.glNormal3f(0.0f, 0.0f, 1.0f);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(x - half, 0.0f, z + half);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(x + half, 0.0f, z + half);
        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(x + half, wallHeight, z + half);
        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(x - half, wallHeight, z + half);
        gl.glColor3f(0.4f, 0.4f, 0.4f);
        gl.glNormal3f(0.0f, 0.0f, -1.0f);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(x - half, 0.0f, z - half);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(x - half, wallHeight, z - half);
        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(x + half, wallHeight, z - half);
        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(x + half, 0.0f, z - half);
        gl.glColor3f(0.6f, 0.6f, 0.6f);
        gl.glNormal3f(0.0f, 1.0f, 0.0f);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(x - half, wallHeight, z - half);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(x - half, wallHeight, z + half);
        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(x + half, wallHeight, z + half);
        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(x + half, wallHeight, z - half);
        gl.glColor3f(0.3f, 0.3f, 0.3f);
        gl.glNormal3f(0.0f, -1.0f, 0.0f);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(x - half, 0.0f, z - half);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(x + half, 0.0f, z - half);
        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(x + half, 0.0f, z + half);
        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(x - half, 0.0f, z + half);
        gl.glColor3f(0.45f, 0.45f, 0.45f);
        gl.glNormal3f(1.0f, 0.0f, 0.0f);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(x + half, 0.0f, z - half);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(x + half, wallHeight, z - half);
        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(x + half, wallHeight, z + half);
        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(x + half, 0.0f, z + half);
        gl.glColor3f(0.55f, 0.55f, 0.55f);
        gl.glNormal3f(-1.0f, 0.0f, 0.0f);
        gl.glTexCoord2f(0.0f, 0.0f);
        gl.glVertex3f(x - half, 0.0f, z - half);
        gl.glTexCoord2f(1.0f, 0.0f);
        gl.glVertex3f(x - half, 0.0f, z + half);
        gl.glTexCoord2f(1.0f, 1.0f);
        gl.glVertex3f(x - half, wallHeight, z + half);
        gl.glTexCoord2f(0.0f, 1.0f);
        gl.glVertex3f(x - half, wallHeight, z - half);
        gl.glEnd();
    }

    /**
     * Draws the portal opening — a textured quad using an FBO render
     * plus a bright glowing border outline.
     *
     * FBO textures are stored bottom-up in OpenGL, so Y tex-coords are
     * flipped (1→0 bottom, 0→1 top) to correct the upside-down image.
     *
     * @param fboTexId  GL texture id from an FBO (>0), or 0 to use tint fallback
     */
    private void drawPortalFrame(GL2 gl, float x, float z,
                                  float r, float g, float b, int fboTexId, boolean isZAligned) {
        float radiusX = 0.5f;
        float radiusY = 1.0f;
        float centerY = 2.0f;
        int segments = 32;
        boolean hasTexture = (fboTexId > 0);

        // ── Portal surface ────────────────────────────────────────────────
        if (hasTexture) {
            gl.glEnable(GL2.GL_TEXTURE_2D);
            gl.glBindTexture(GL2.GL_TEXTURE_2D, fboTexId);
            gl.glColor3f(1.0f, 1.0f, 1.0f);   // no tint — show raw texture
        } else {
            gl.glDisable(GL2.GL_TEXTURE_2D);
            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
            gl.glColor4f(r, g, b, 0.7f);
        }

        // X-Aligned Face
        if (!isZAligned) {
            gl.glBegin(GL2.GL_POLYGON);
            for (int i = 0; i < segments; i++) {
                double theta = 2.0 * Math.PI * i / segments;
                float dx = (float)(radiusX * Math.cos(theta));
                float dy = (float)(radiusY * Math.sin(theta));
                float u = 0.5f + 0.5f * (float)Math.cos(theta);
                float v = 0.5f - 0.5f * (float)Math.sin(theta);
                gl.glTexCoord2f(u, v);
                gl.glVertex3f(x + dx, centerY + dy, z);
            }
            gl.glEnd();
        }

        // Z-Aligned Face
        if (isZAligned) {
            gl.glBegin(GL2.GL_POLYGON);
            for (int i = 0; i < segments; i++) {
                double theta = 2.0 * Math.PI * i / segments;
                float dx = (float)(radiusX * Math.cos(theta));
                float dy = (float)(radiusY * Math.sin(theta));
                float u = 0.5f + 0.5f * (float)Math.cos(theta);
                float v = 0.5f - 0.5f * (float)Math.sin(theta);
                gl.glTexCoord2f(u, v);
                gl.glVertex3f(x, centerY + dy, z + dx);
            }
            gl.glEnd();
        }

        if (hasTexture) gl.glDisable(GL2.GL_TEXTURE_2D);
        if (!hasTexture) gl.glDisable(GL2.GL_BLEND);

        // ── Quad-Strip Glowing Rings ───────────
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

        float glow = 0.7f + 0.3f * (float) Math.sin(glowPhase);
        float ringThick = 0.1f;
        float glowThick = 0.2f;

        // X-Aligned Rings
        if (!isZAligned) {
            gl.glBegin(GL2.GL_QUAD_STRIP);
            for (int i = 0; i <= segments; i++) {
                double theta = 2.0 * Math.PI * i / segments;
                float cosT = (float) Math.cos(theta);
                float sinT = (float) Math.sin(theta);
                
                gl.glColor4f(r, g, b, 1.0f);
                gl.glVertex3f(x + radiusX * cosT, centerY + radiusY * sinT, z);
                
                gl.glColor4f(r * glow, g * glow, b * glow, 0.9f);
                gl.glVertex3f(x + (radiusX + ringThick) * cosT, centerY + (radiusY + ringThick) * sinT, z);
            }
            gl.glEnd();

            gl.glBegin(GL2.GL_QUAD_STRIP);
            for (int i = 0; i <= segments; i++) {
                double theta = 2.0 * Math.PI * i / segments;
                float cosT = (float) Math.cos(theta);
                float sinT = (float) Math.sin(theta);
                
                gl.glColor4f(r * glow, g * glow, b * glow, 0.9f);
                gl.glVertex3f(x + (radiusX + ringThick) * cosT, centerY + (radiusY + ringThick) * sinT, z);
                
                gl.glColor4f(r, g, b, 0.0f);
                gl.glVertex3f(x + (radiusX + ringThick + glowThick) * cosT, centerY + (radiusY + ringThick + glowThick) * sinT, z);
            }
            gl.glEnd();
        }

        // Z-Aligned Rings
        if (isZAligned) {
            gl.glBegin(GL2.GL_QUAD_STRIP);
            for (int i = 0; i <= segments; i++) {
                double theta = 2.0 * Math.PI * i / segments;
                float cosT = (float) Math.cos(theta);
                float sinT = (float) Math.sin(theta);
                
                gl.glColor4f(r, g, b, 1.0f);
                gl.glVertex3f(x, centerY + radiusY * sinT, z + radiusX * cosT);
                
                gl.glColor4f(r * glow, g * glow, b * glow, 0.9f);
                gl.glVertex3f(x, centerY + (radiusY + ringThick) * sinT, z + (radiusX + ringThick) * cosT);
            }
            gl.glEnd();

            gl.glBegin(GL2.GL_QUAD_STRIP);
            for (int i = 0; i <= segments; i++) {
                double theta = 2.0 * Math.PI * i / segments;
                float cosT = (float) Math.cos(theta);
                float sinT = (float) Math.sin(theta);
                
                gl.glColor4f(r * glow, g * glow, b * glow, 0.9f);
                gl.glVertex3f(x, centerY + (radiusY + ringThick) * sinT, z + (radiusX + ringThick) * cosT);
                
                gl.glColor4f(r, g, b, 0.0f);
                gl.glVertex3f(x, centerY + (radiusY + ringThick + glowThick) * sinT, z + (radiusX + ringThick + glowThick) * cosT);
            }
            gl.glEnd();
        }

        if (hasTexture) {
            gl.glDisable(GL2.GL_BLEND);
        } else {
            gl.glDisable(GL2.GL_BLEND);
        }
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

    /**
     * Renders the static scene geometry (floor, ceiling, maze walls) from
     * whatever camera is already set in the modelview matrix. Used by both
     * the FBO off-screen pass and the main on-screen pass.
     */
    private void renderSceneGeometry(GL2 gl) {
        gl.glEnable(GL2.GL_TEXTURE_2D);

        // Floor
        if (floorTexture != null) {
            floorTexture.bind(gl);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
        }
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0.0f, 1.0f, 0.0f);
        gl.glColor3f(1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.0f,  0.0f); gl.glVertex3f(-50.0f, 0.0f, -50.0f);
        gl.glTexCoord2f(0.0f, 10.0f); gl.glVertex3f(-50.0f, 0.0f,  50.0f);
        gl.glTexCoord2f(10.0f,10.0f); gl.glVertex3f( 50.0f, 0.0f,  50.0f);
        gl.glTexCoord2f(10.0f, 0.0f); gl.glVertex3f( 50.0f, 0.0f, -50.0f);
        gl.glEnd();

        // Ceiling
        if (ceilingTexture != null) {
            ceilingTexture.bind(gl);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_S, GL2.GL_REPEAT);
            gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_WRAP_T, GL2.GL_REPEAT);
        }
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0.0f, -1.0f, 0.0f);
        gl.glColor3f(1.0f, 1.0f, 1.0f);
        gl.glTexCoord2f(0.0f,  0.0f); gl.glVertex3f(-50.0f, wallHeight, -50.0f);
        gl.glTexCoord2f(0.0f, 10.0f); gl.glVertex3f(-50.0f, wallHeight,  50.0f);
        gl.glTexCoord2f(10.0f,10.0f); gl.glVertex3f( 50.0f, wallHeight,  50.0f);
        gl.glTexCoord2f(10.0f, 0.0f); gl.glVertex3f( 50.0f, wallHeight, -50.0f);
        gl.glEnd();

        drawMaze(gl);
        gl.glDisable(GL2.GL_TEXTURE_2D);
    }

    /**
     * Renders the scene from a virtual camera at (exitX, eyeY, exitZ) looking
     * in exitAngle, and captures the result into the given FBO.
     *
     * @param targetFboId  the FBO to render into (fboIdA[0] or fboIdB[0])
     */
    private void renderPortalView(GL2 gl, int targetFboId,
                                   float exitX, float exitZ, float exitAngle) {
        int[] prevViewport = new int[4];
        gl.glGetIntegerv(GL2.GL_VIEWPORT, prevViewport, 0);

        // ── Redirect rendering to the target FBO ──────────────────────────
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, targetFboId);
        gl.glViewport(0, 0, FBO_WIDTH, FBO_HEIGHT);

        gl.glClearColor(0.05f, 0.05f, 0.1f, 1.0f);
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        // ── Projection (same FOV as main camera) ──────────────────────────
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        glu.gluPerspective(45.0, (double) FBO_WIDTH / FBO_HEIGHT, 0.1, 100.0);

        // ── Camera placed at the exit portal, looking in exitAngle ────────
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();

        float eyeY   = cameraY;   // preserve player's vertical height
        float lookAtX = exitX + (float) Math.cos(Math.toRadians(exitAngle));
        float lookAtZ = exitZ + (float) Math.sin(Math.toRadians(exitAngle));
        glu.gluLookAt(exitX, eyeY, exitZ,
                      lookAtX, eyeY, lookAtZ,
                      0.0f, isGravityFlipped ? -1.0f : 1.0f, 0.0f);

        // Light follows the virtual camera
        float[] vLightPos = { exitX, eyeY, exitZ, 1.0f };
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, vLightPos, 0);

        renderSceneGeometry(gl);

        // ── Restore matrices & default framebuffer ────────────────────────
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);

        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
        gl.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
    }

    /**
     * Allocates one complete FBO (color texture + depth renderbuffer).
     * Writes the generated IDs into the supplied arrays.
     */
    private void allocateFBO(GL2 gl, int[] fboOut, int[] texOut, int[] depthOut) {
        // FBO
        gl.glGenFramebuffers(1, fboOut, 0);
        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, fboOut[0]);

        // Color texture
        gl.glGenTextures(1, texOut, 0);
        gl.glBindTexture(GL2.GL_TEXTURE_2D, texOut[0]);
        gl.glTexImage2D(GL2.GL_TEXTURE_2D, 0, GL2.GL_RGB, FBO_WIDTH, FBO_HEIGHT,
                0, GL2.GL_RGB, GL2.GL_UNSIGNED_BYTE, null);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MIN_FILTER, GL2.GL_LINEAR);
        gl.glTexParameteri(GL2.GL_TEXTURE_2D, GL2.GL_TEXTURE_MAG_FILTER, GL2.GL_LINEAR);
        gl.glFramebufferTexture2D(GL2.GL_FRAMEBUFFER, GL2.GL_COLOR_ATTACHMENT0,
                GL2.GL_TEXTURE_2D, texOut[0], 0);

        // Depth renderbuffer
        gl.glGenRenderbuffers(1, depthOut, 0);
        gl.glBindRenderbuffer(GL2.GL_RENDERBUFFER, depthOut[0]);
        gl.glRenderbufferStorage(GL2.GL_RENDERBUFFER, GL2.GL_DEPTH_COMPONENT,
                FBO_WIDTH, FBO_HEIGHT);
        gl.glFramebufferRenderbuffer(GL2.GL_FRAMEBUFFER, GL2.GL_DEPTH_ATTACHMENT,
                GL2.GL_RENDERBUFFER, depthOut[0]);

        // ── Validate the FBO is complete ───────────────────────────────────
        int status = gl.glCheckFramebufferStatus(GL2.GL_FRAMEBUFFER);
        if (status != GL2.GL_FRAMEBUFFER_COMPLETE) {
            System.err.println("[FBO] Incomplete framebuffer! Status: 0x"
                    + Integer.toHexString(status));
        }

        gl.glBindFramebuffer(GL2.GL_FRAMEBUFFER, 0);
    }

    /** Allocates both portal FBOs. Called once from init(). */
    private void setupFBO(GL2 gl) {
        allocateFBO(gl, fboIdA, texIdA, depthIdA);   // view from B → shown on A
        allocateFBO(gl, fboIdB, texIdB, depthIdB);   // view from A → shown on B
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        System.out.println("Working Directory: " + System.getProperty("user.dir"));
        GL2 gl = drawable.getGL().getGL2();
        gl.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_COLOR_MATERIAL);

        textRenderer = new TextRenderer(new Font("SansSerif", Font.BOLD, 18));

        try {
            File wf = new File("wall.png");
            if (wf.exists()) {
                BufferedImage img = ImageIO.read(wf);
                wallTexture = AWTTextureIO.newTexture(drawable.getGLProfile(), img, true);
            }

            File ff = new File("floor.png");
            if (ff.exists()) {
                BufferedImage img = ImageIO.read(ff);
                floorTexture = AWTTextureIO.newTexture(drawable.getGLProfile(), img, false);
            }

            File cf = new File("ceiling.png");
            if (cf.exists()) {
                BufferedImage img = ImageIO.read(cf);
                ceilingTexture = AWTTextureIO.newTexture(drawable.getGLProfile(), img, false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        setupFBO(gl);

        updateLook();
        System.out.println("Portals active: [1][2] <-> [8][7]");
        ((Window) drawable).setPointerVisible(false);
        ((Window) drawable).confinePointer(true);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        // Advance portal glow animation (~1.5 Hz at 60 fps)
        glowPhase += 0.016f;
        if (glowPhase > 2.0f * Math.PI) glowPhase -= (float)(2.0f * Math.PI);

        // ── PHASE 2: Off-screen FBO passes (bidirectional) ───────────────
        float[] portalA = gridTo3D(1, 2);
        float[] portalB = gridTo3D(8, 7);

        // FBO-A: virtual cam at Portal B, looking back → texture shown on Portal A
        renderPortalView(gl, fboIdA[0],
                portalB[0], portalB[1], cameraAngle + 180f);

        // FBO-B: virtual cam at Portal A, looking back → texture shown on Portal B
        renderPortalView(gl, fboIdB[0],
                portalA[0], portalA[1], cameraAngle + 180f);

        // ── Main on-screen pass ───────────────────────────────────────────
        gl.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        runGameLogic(gl);
        drawMinimap(gl);
    }

    private void runGameLogic(GL2 gl) {

        if (teleportCooldown > 0)
            teleportCooldown--;

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
        int flipDir = isGravityFlipped ? -1 : 1;
        if (aPressed) {
            nextX += Math.cos(Math.toRadians(cameraAngle - 90 * flipDir)) * currentSpeed;
            nextZ += Math.sin(Math.toRadians(cameraAngle - 90 * flipDir)) * currentSpeed;
        }
        if (dPressed) {
            nextX += Math.cos(Math.toRadians(cameraAngle + 90 * flipDir)) * currentSpeed;
            nextZ += Math.sin(Math.toRadians(cameraAngle + 90 * flipDir)) * currentSpeed;
        }

        if (isValidPosition(nextX, cameraZ))
            cameraX = nextX;
        if (isValidPosition(cameraX, nextZ))
            cameraZ = nextZ;

        float floorY = isGravityFlipped ? (wallHeight - PLAYER_HEIGHT) : PLAYER_HEIGHT;
        if (spacePressed && !isJumping) {
            verticalVelocity = isGravityFlipped ? -JUMP_STRENGTH : JUMP_STRENGTH;
            isJumping = true;
        }
        cameraY += verticalVelocity;
        verticalVelocity += isGravityFlipped ? GRAVITY : -GRAVITY;
        
        if ((!isGravityFlipped && cameraY <= floorY) || (isGravityFlipped && cameraY >= floorY)) {
            cameraY = floorY;
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
        double distG = Math.sqrt(Math.pow(cameraX - gravTrigger[0], 2) + Math.pow(cameraZ - gravTrigger[1], 2));

        if (distG < 0.8f) {
            if (!onGravityPad) {
                isGravityFlipped = !isGravityFlipped;
                onGravityPad = true; // Lock the trigger so it doesn't fire again
            }
        } else {
            onGravityPad = false; // Reset the trigger once the player walks away
        }

        updateLook();

        glu.gluLookAt(cameraX, cameraY, cameraZ, lookX, lookY, lookZ, 0.0f, isGravityFlipped ? -1.0f : 1.0f, 0.0f);

        float[] lightPos = { cameraX, cameraY, cameraZ, 1.0f };
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPos, 0);
        gl.glLightf(GL2.GL_LIGHT0, GL2.GL_QUADRATIC_ATTENUATION, 0.02f);

        // Draw main scene geometry (floor, ceiling, maze) via shared helper
        renderSceneGeometry(gl);

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

        // Draw Portal A (Blue) — live view from Portal B (texIdA)
        float[] pA = gridTo3D(1, 2);
        drawPortalFrame(gl, pA[0], pA[1], 0.0f, 0.5f, 1.0f, texIdA[0], true);

        // Draw Portal B (Orange) — live view from Portal A (texIdB)
        float[] pB = gridTo3D(8, 7);
        drawPortalFrame(gl, pB[0], pB[1], 1.0f, 0.5f, 0.0f, texIdB[0], true);

        textRenderer.beginRendering(WINDOW_WIDTH, WINDOW_HEIGHT);
        textRenderer.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        textRenderer.draw(String.format("Coords: X=%.2f, Z=%.2f | Flipped: %b", cameraX, cameraZ, isGravityFlipped),
                WINDOW_WIDTH - 450, WINDOW_HEIGHT - 30);
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
        for (int r = 0; r < mazeLayout.length; r++) {
            for (int c = 0; c < mazeLayout[0].length; c++) {
                if (mazeLayout[r][c] == 1) {
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
        GL2 gl = drawable.getGL().getGL2();

        // Release Portal A FBO resources
        gl.glDeleteFramebuffers(1,   fboIdA,   0);
        gl.glDeleteTextures(1,       texIdA,   0);
        gl.glDeleteRenderbuffers(1,  depthIdA, 0);

        // Release Portal B FBO resources
        gl.glDeleteFramebuffers(1,   fboIdB,   0);
        gl.glDeleteTextures(1,       texIdB,   0);
        gl.glDeleteRenderbuffers(1,  depthIdB, 0);

        System.out.println("[FBO] GPU resources released.");
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
        cameraAngle += (isGravityFlipped ? -deltaX : deltaX) * mouseSensitivity;
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
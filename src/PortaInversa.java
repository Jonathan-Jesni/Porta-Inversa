import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.newt.event.WindowAdapter;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.opengl.util.FPSAnimator;

public class PortaInversa implements GLEventListener {

    private GLU glu = new GLU();
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;

    // --- CAMERA VECTORS ---
    // Player position (standing at x=0, y=1 (eye level), z=5 (stepped back))
    private float cameraX = 0.0f;
    private float cameraY = 1.0f;
    private float cameraZ = 5.0f;

    // Where the player is looking (looking straight ahead at the origin)
    private float lookX = 0.0f;
    private float lookY = 1.0f;
    private float lookZ = 0.0f;

    @Override
    public void init(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();
        gl.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        System.out.println("Porta Inversa Phase 1: Camera & Floor initialized.");
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        GL2 gl = drawable.getGL().getGL2();

        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);
        gl.glLoadIdentity();

        // 1. SET UP THE 3D CAMERA
        glu.gluLookAt(
                cameraX, cameraY, cameraZ, // Eye position
                lookX, lookY, lookZ, // Target look position
                0.0f, 1.0f, 0.0f // Up vector (Y is up)
        );

        // 2. DRAW THE MAZE FLOOR
        // We are drawing a dark 3D square plane centered at the origin
        gl.glBegin(GL2.GL_QUADS);
        // We alternate colors slightly to give it a checkerboard/grid feel for
        // perspective
        gl.glColor3f(0.15f, 0.15f, 0.15f);
        gl.glVertex3f(-10.0f, 0.0f, -10.0f); // Top-left

        gl.glColor3f(0.2f, 0.2f, 0.2f);
        gl.glVertex3f(-10.0f, 0.0f, 10.0f); // Bottom-left

        gl.glColor3f(0.15f, 0.15f, 0.15f);
        gl.glVertex3f(10.0f, 0.0f, 10.0f); // Bottom-right

        gl.glColor3f(0.2f, 0.2f, 0.2f);
        gl.glVertex3f(10.0f, 0.0f, -10.0f); // Top-right
        gl.glEnd();
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        GL2 gl = drawable.getGL().getGL2();
        if (height == 0)
            height = 1;
        float aspect = (float) width / height;

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(45.0, aspect, 0.1, 100.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
    }

    public static void main(String[] args) {
        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities capabilities = new GLCapabilities(profile);

        GLWindow window = GLWindow.create(capabilities);
        window.setTitle("Porta Inversa - Phase 1");
        window.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        window.addGLEventListener(new PortaInversa());

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
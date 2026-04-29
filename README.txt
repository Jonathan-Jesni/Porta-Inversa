================================================================================
PROJECT: Porta Inversa
COURSE: Computer Graphics Lab
PROFESSOR: Anupama Arun
SUBMISSION TYPE: Simple 3D Game
================================================================================

DESCRIPTION
-----------
"Porta Inversa" is a 3D first-person survival horror game built entirely from 
scratch using Java and OpenGL (JOGL). The player is trapped in a surreal, 
non-Euclidean maze and must survive for 60 seconds while being hunted by a 
hostile entity known as "The Watcher." To outmaneuver the entity, the player 
must utilize teleportation portals and gravity-flipping mechanics.

CONTROLS
--------
- Space      : Start Game / Restart Game (Menu) / Jump (In-Game)
- W, A, S, D : Move forward, left, backward, and right
- Mouse      : 360-degree First-Person camera (Look around, up, and down)
- Shift      : Sprint
- Esc        : Quit Game (Releases confined mouse pointer)

GAMEPLAY MECHANICS & OBJECTIVES
-------------------------------
- Survive for exactly 60 seconds to win.
- Do not let the glowing red "Watcher" touch you, or it is Game Over.
- Dive through the blue and orange Portals to instantly teleport across the map.
- Step on the green Gravity Pad to rotate the world and walk on the ceiling.
- Arcade Restart: Press Space after a Game Over to instantly try again.

IMPLEMENTED COMPUTER GRAPHICS CONCEPTS
--------------------------------------
This project heavily utilizes foundational computer graphics math and OpenGL 
state machines to achieve its gameplay:

1. 3D Spherical Coordinate Camera (LookAt Matrix): 
   Calculates full 3D pitch and yaw using trigonometric functions (sine/cosine) 
   to generate an accurate directional look vector, allowing true FPS controls.

2. Global Matrix Transformations (Gravity Flipping):
   Utilizes glRotatef and glTranslatef on the global modelview matrix to simulate 
   a 180-degree rotation on the X-axis, seamlessly inverting the player's gravity.

3. Dynamic Vector Normalization (The Watcher AI):
   Calculates the continuous direction vector between the player and the entity 
   using the Pythagorean theorem (dist = sqrt(dx^2 + dz^2)). It normalizes this 
   vector to allow the entity to dynamically track and pursue the player.

4. 2D Orthographic Projections (UI & Minimap):
   Switches the projection matrix from Perspective to Orthographic to render 
   the HUD, survival timer, and a real-time motion-tracking minimap overlaid 
   on the screen.

5. Bounding Box Collision Detection:
   Uses spatial distance checks and positional tracking arrays to trigger portal 
   teleportation, gravity pad state-changes, and entity hitboxes.

HOW TO RUN
----------
1. Ensure the JOGL (Java OpenGL) libraries (.jar files) are added to your 
   Java project's build path/referenced libraries.
2. Compile and run `PortaInversa.java`.
================================================================================
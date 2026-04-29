# Porta Inversa 🌀

**Porta Inversa** is a 3D first-person survival horror game built from scratch using pure Java and OpenGL (JOGL). Developed for the Computer Graphics Lab (Professor Anupama Arun), this project demonstrates practical implementations of complex 3D math, global matrix transformations, and dynamic rendering pipelines.

## 🎯 The Objective
You are trapped in a surreal, non-Euclidean maze. The moment you spawn, **The Watcher**—a hostile, ghostly red entity—will begin dynamically tracking your coordinates. 

**You must survive for exactly 60 seconds.** You cannot outrun it forever. You must use the environment's portals and gravity mechanics to outmaneuver it. 
*(Note: If you die, simply press Space to instantly restart the physics loop).*

## 🎮 Controls
* **`Space`** - Start Game / Restart Game / Jump
* **`W` `A` `S` `D`** - Movement
* **`Mouse`** - 360° FPS Camera (Pitch/Yaw)
* **`Shift`** - Sprint
* **`Esc`** - Quit Game / Free Mouse Pointer

## 🧠 Core Computer Graphics Concepts
This engine was built without a pre-existing game engine (like Unity or Unreal) to explicitly demonstrate foundational CG mathematical concepts:

* **Spherical Coordinate Camera System:** Generates true 3D look-vectors using sine and cosine functions to calculate dynamic pitch and yaw. Includes vertical angle clamping to prevent gimbal lock.
* **Dynamic Vector Normalization:** "The Watcher" AI relies on continuous frame-by-frame distance calculations. It normalizes the vector between itself and the player's camera to create a flawless, relentless pursuit path.
* **Global Matrix Transformations:** The gravity-flipping mechanic applies a continuous 180-degree `glRotatef` over the entire scene's geometry, flawlessly shifting the floor to the ceiling.
* **Orthographic UI Pipelines:** Dynamically swaps the OpenGL projection matrix from `GL_PROJECTION` to Orthographic to render the 2D Heads-Up Display, timer, and a real-time motion-tracking minimap.
* **AABB Collision Geometry:** Implements custom Axis-Aligned Bounding Boxes and positional flag tracking to handle portal teleportation logic and hit-detection.

## 🚀 How to Run
1. Clone the repository.
2. Ensure you have the **JOGL (Java OpenGL)** `.jar` files referenced in your Java workspace.
3. Run `PortaInversa.java`.
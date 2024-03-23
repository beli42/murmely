Copied from https://github.com/forkachild/imagine, with changes for improved compatibility.
MIT copyright notice and permission notice in LICENSE file. Changes to the imagine code are
under GPLv3 or later (like the rest of the library)

Removed most `layout (location = x)` before uniforms etc from the fragment and vertices
definitions, because this wasn't working on some devices and emulators. Code had to be 
adjusted accordingly to restore functionality, using `glBindAttribLocation` for the 
attributes and `glGetUniformLocation`
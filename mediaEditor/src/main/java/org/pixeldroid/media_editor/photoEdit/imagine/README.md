Copied from https://github.com/forkachild/imagine, with changes for improved compatibility.
MIT copyright notice and permission notice in README.md 

Removed most `layout (location = x)` before uniforms etc from the fragment and vertices
definitions, because this wasn't working on some devices and emulators. Code had to be 
adjusted accordingly to restore functionality, using `glBindAttribLocation` for the 
attributes and `glGetUniformLocation`
uniform mat4 u_ModelViewProjection;

attribute vec3 a_Position;
varying lowp vec3 v_Color;

void main() {
    v_Color = a_Position;
    gl_Position = u_ModelViewProjection * vec4(a_Position, 1);
}

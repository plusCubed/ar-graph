precision lowp float;

uniform vec3 u_Min;
uniform vec3 u_Max;
varying vec3 v_Color;

void main(void) {
  gl_FragColor = vec4((v_Color - u_Min) / (u_Max - u_Min), 1);
}

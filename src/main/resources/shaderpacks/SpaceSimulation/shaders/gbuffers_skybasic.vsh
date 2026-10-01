#version 330 compatibility

/*
 * 天空（无纹理部分）：原样输出，由模组自绘的太阳球体（WorldSphereRenderer）叠加在其上。
 */

out vec4 glcolor;

void main() {
    glcolor = gl_Color;
    gl_Position = ftransform();
}

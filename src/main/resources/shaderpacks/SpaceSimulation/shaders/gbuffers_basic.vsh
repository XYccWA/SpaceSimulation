#version 330 compatibility

/*
 * 线框/调试几何（无纹理）：原样输出顶点色，不参与太阳光照。
 */

out vec4 glcolor;

void main() {
    glcolor = gl_Color;
    gl_Position = ftransform();
}

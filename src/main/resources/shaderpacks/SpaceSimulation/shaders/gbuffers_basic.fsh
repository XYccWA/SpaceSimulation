#version 330 compatibility

/*
 * 线框/调试几何（无纹理）：原样输出顶点色，不参与太阳光照。
 */

in vec4 glcolor;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void main() {
    color = glcolor;
}

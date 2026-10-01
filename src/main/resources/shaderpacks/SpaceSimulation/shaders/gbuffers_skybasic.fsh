#version 330 compatibility

/*
 * 天空（无纹理部分）：原样输出，不参与太阳光照。
 */

in vec4 glcolor;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void main() {
    color = glcolor;
}

#version 330 compatibility

/*
 * 天空（带纹理部分，原版太阳/月亮贴图）：原样输出，不参与太阳光照。
 */

uniform sampler2D gtexture;

in vec2 texcoord;
in vec4 glcolor;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void main() {
    color = texture(gtexture, texcoord) * glcolor;
}

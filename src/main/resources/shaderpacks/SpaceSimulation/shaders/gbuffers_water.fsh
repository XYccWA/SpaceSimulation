#version 330 compatibility

#include "/lib/space_light.glsl"

/*
 * 通用几何片元着色器：albedo ×（环境光 + 世界中心太阳的直射光 × 阴影）。
 * 原版 lightmap 在全亮模式下恒为 15，这里完全不使用它——光照全部由本包计算。
 *
 * 选项与常量集中在 lib/space_settings.glsl（经 space_light.glsl 引入），
 * 可在 Iris 的光影设置界面调整。
 */

uniform sampler2D gtexture;

in vec2 texcoord;
in vec4 glcolor;
in vec3 viewNormal;
in vec3 viewPos;

/* RENDERTARGETS: 0 */
layout(location = 0) out vec4 color;

void main() {
    vec4 albedo = texture(gtexture, texcoord) * glcolor;
    if (albedo.a < 0.1) {
        discard;
    }
    vec3 feetPlayerPos = spacePlayerPos(viewPos);
    color = vec4(spaceLighting(albedo.rgb, viewNormal, feetPlayerPos), albedo.a);
}

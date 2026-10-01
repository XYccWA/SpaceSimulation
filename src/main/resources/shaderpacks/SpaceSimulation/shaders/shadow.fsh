#version 330 compatibility

#include "/lib/space_settings.glsl"

/*
 * shadow pass 片元着色器：丢弃完全透明的像素（树叶缝隙等），其余只写深度
 * （Iris 自动以 gl_FragCoord.z 写入 shadowtex0，颜色写入 shadowcolor0 但本包不用）。
 *
 * 阴影图分辨率 shadowMapResolution 与阴影范围 shadowDistance 由
 * lib/space_settings.glsl 提供，可在光影设置界面调整。
 */

uniform sampler2D gtexture;

in vec2 texcoord;
in vec4 glcolor;

layout(location = 0) out vec4 color;

void main() {
    color = texture(gtexture, texcoord) * glcolor;
    if (color.a < 0.1) {
        discard;
    }
}

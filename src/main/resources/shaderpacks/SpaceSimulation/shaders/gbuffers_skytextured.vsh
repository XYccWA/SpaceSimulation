#version 330 compatibility

/*
 * 天空（带纹理部分，原版太阳/月亮贴图）：原样输出。
 * 本模组的太阳是自绘球体，这里的原版天体是否可见由模组自行决定。
 */

out vec2 texcoord;
out vec4 glcolor;

void main() {
    texcoord = gl_MultiTexCoord0.xy;
    glcolor = gl_Color;
    gl_Position = ftransform();
}

#version 330 compatibility

/*
 * 通用几何顶点着色器（地形/实体/方块实体/水/手/粒子/破坏动画共用）。
 *
 * 传递：
 *   texcoord   —— 纹理坐标
 *   glcolor    —— 顶点色（含原版 AO/方向明暗）
 *   viewNormal —— view space 法线（与 Iris 的 sunPosition 同空间）
 *   viewPos    —— view space 位置
 *
 * 注意：这里**不声明任何 Iris 注入的 uniform**（如 gbufferModelViewInverse）。
 * player space 的换算放在片元着色器的 lib 里做，原因见 lib/space_light.glsl 的
 * spacePlayerPos() 注释。gl_* 内置变量由 Iris Patcher 自动转换，无需声明。
 */

out vec2 texcoord;
out vec4 glcolor;
out vec3 viewNormal;
out vec3 viewPos;

void main() {
    // 直接用顶点纹理坐标：MC 1.17+ 的纹理矩阵恒为单位阵，省掉这项依赖可以少一个
    // 与 Iris Patcher 内置名转换相关的风险点。
    texcoord = gl_MultiTexCoord0.xy;
    glcolor = gl_Color;

    // gl_ModelViewMatrix 把 model space 变换到 view space（相机空间）
    viewPos = (gl_ModelViewMatrix * gl_Vertex).xyz;

    // gl_NormalMatrix = 视矩阵法线矩阵（逆转置），得到 view space 法线
    viewNormal = normalize(gl_NormalMatrix * gl_Normal);

    gl_Position = ftransform();
}

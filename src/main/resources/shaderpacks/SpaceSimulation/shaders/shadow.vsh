#version 330 compatibility

/*
 * shadow pass 顶点着色器。
 *
 * 该 pass 由 Iris 在世界几何之前执行，把场景从「世界中心太阳」的方向渲染成深度图。
 * 顶点变换使用 ftransform()，Iris 在这个 pass 会把 gl_ProjectionMatrix /
 * gl_ModelViewMatrix 替换为 shadowProjection / shadowModelView——而后者已由本模组的
 * ShadowRendererMixin 接管为径向光方向，因此阴影方向自动正确。
 */

out vec2 texcoord;
out vec4 glcolor;

void main() {
    texcoord = gl_MultiTexCoord0.xy;
    glcolor = gl_Color;
    gl_Position = ftransform();
}

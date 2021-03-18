#version 300 es 
precision highp float;

in vec2 tex;

out vec4 fragmentColor;

uniform struct {
	sampler2D avaragedFrameTexture;
} scene;

void main(void) {
	fragmentColor = texture(scene.avaragedFrameTexture, tex);
}
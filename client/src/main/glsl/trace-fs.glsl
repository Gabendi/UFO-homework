#version 300 es 
precision highp float;

uniform struct {
  vec3 position;
	mat4 rayDirMatrix;
} camera;

uniform struct Quadric{
  mat4 surface;
  mat4 clipper;
  vec4 brdf; // xyz: brdf params, w: type ()
} quadrics[4];

uniform struct Light{
  vec4 position;
  vec3 powerDensity;
} lights[1];

uniform struct {
  samplerCube envTexture;
  vec4 randoms[64];
  sampler2D previousFrameTexture;
  float iFrame;
} scene;

in vec2 tex;
in vec4 rayDir;

out vec4 fragmentColor;

const float PI   = 3.14159265358979323846264; // PI
const float PHIG = 1.61803398874989484820459 * 00000.1; // Golden Ratio   
const float PIG  = 3.14159265358979323846264 * 00000.1; // PI
const float SQ2G = 1.41421356237309504880169 * 10000.0; // Square Root of Two

float goldRand(in vec3 seed){
    return fract(sin(dot(seed.xy*(seed.z+PHIG), vec2(PHIG, PIG)))*SQ2G);
}


float intersectQuadric(vec4 e, vec4 d, mat4 surface, mat4 clipper) {
  float a = dot(d, surface * d);
  float b = dot(e, surface * d) + dot(d, surface * e);
  float c = dot(e, surface * e);

  if (abs(a) < 0.001) {
    float t = -c / b;
    vec4 h = e + d * t;

    if(dot(h * clipper, h) > 0.0){
      t = -1.0;
    }
    return t;
  }

  float discr = b * b - 4.0 * a * c;

  if (discr < 0.0) {
    return -1.0;
  }

  float t1 = (-b - sqrt(discr)) / (2.0 * a);
  float t2 = (-b + sqrt(discr)) / (2.0 * a);

  vec4 h1 = e + d * t1;
  
  if(dot(h1 * clipper, h1) > 0.0){
    t1 = -1.0;
  }

  vec4 h2 = e + d * t2;
  
  if(dot(h2 * clipper, h2) > 0.0){
    t2 = -1.0;
  }

  return (t1 < 0.0) ? t2 : ((t2 < 0.0) ? t1 : min(t1, t2));
}

bool findBestHit(vec4 e, vec4 d, out float bestT, out int bestIndex) {
  bestT = 1000000.0;
  for(int i = 0; i < quadrics.length(); i++) {
    float t = intersectQuadric(e, d, quadrics[i].surface, quadrics[i].clipper);
    if (t > 0.0 && t < bestT) {
      bestT = t;
      bestIndex = i;
    }
  }

  if (bestT < 999999.0)
    return true;
  else
    return false;
}

vec3 directLighting(vec3 x, vec3 n, vec3 v) { // n is the normal
  vec3 reflectedRadiance = vec3(0, 0, 0);
  for (int i = 0; i < lights.length(); i++) {
    vec3 lightPos = lights[i].position.xyz;
    vec3 lightDiff = lightPos - x * lights[i].position.w;
    float lightDist = length(lightDiff); //how far it is from the light

    vec3 lightDir = lightDiff / lightDist;
    
    vec4 eShadow = vec4(x + n * 0.01, 1);
    vec4 dShadow = vec4(lightDir, 0);
    float shadowT;
    int shadowIndex;
    if(!findBestHit(eShadow, dShadow, shadowT, shadowIndex) ||
         shadowT * lights[i].position.w > lightDist ) {

      vec3 lightPowerDensity = lights[i].powerDensity;
      lightPowerDensity /= lightDist * lightDist;
      vec3 diffuseCoeff = vec3(0.3, 0.3, 0.0);
      vec3 specularCoeff = vec3(0.0, 0.0, 0.0);
      float shininess = 15.0;

      float cosa = dot(n, lightDir); 
      
      if (cosa < 0.0) {
        cosa = 0.0;
      } else {
        reflectedRadiance += lightPowerDensity * cosa * diffuseCoeff;

        vec3 halfway = normalize(v + lightDir);
        float cosb = dot(n, v);
        float cosd = dot(halfway, n);
        if (cosd < 0.0) {
          cosd = 0.0;
        }
        reflectedRadiance += lightPowerDensity * specularCoeff * pow(cosd, shininess) * cosa / max(cosa, cosb);
      }
    }
  }
  return reflectedRadiance;
}

void main(void) {
  vec4 eye = vec4(camera.position, 1);
  vec4 d = vec4(normalize(rayDir.xyz), 0);

  //egyszer a pixelben
  float perPixelNoise = goldRand(vec3(tex * 1024.0, 1.0)) * 6.28318530718;

  fragmentColor = vec4(0, 0, 0, 1);
  vec3 w = vec3(1, 1, 1); //akkumulalt szorodasi valseg

  for(int iBounce = 0; iBounce < 20; iBounce++) {
    float t;
    int objIdx;
    bool isObjectHit = findBestHit(eye, d, t, objIdx);

    if (isObjectHit){
      Quadric objectHit = quadrics[objIdx];
      vec4 hit = eye + t * d;
      vec4 gradient = hit * objectHit.surface + objectHit.surface * hit;
      vec3 normal = normalize(gradient.xyz);
      
      if (dot(normal, d.xyz) > 0.0) {
          normal = -normal;
      }

      eye = hit;
      eye.xyz += normal * 0.001;

      //Trace is different for types of objects
      if (objectHit.brdf.w == 0.0f) { //diffuse monte-carlo brdf
        vec3 randomDir = normalize(scene.randoms[iBounce].xyz);
        // minden random iranyra egy pixelenkent random forgatas
        //d.x = cos(perPixelNoise) * d.x + sin(perPixelNoise) * d.z;
        //d.z =-sin(perPixelNoise) * d.x + cos(perPixelNoise) * d.z;
        d.xyz = normalize(normal + randomDir);
      }

      else if (objectHit.brdf.w == 1.0f) { //ideal mirror
        d.xyz = reflect(d.xyz, normal);
      }

      else if (objectHit.brdf.w == 2.0f) { //ideal refracting
       d.xyz = reflect(d.xyz, normal); 
        //d.xyz = refract(d.xyz, normal, 0.1f);
      }
      
      //FELADAT: berakn iegy pontfenyforrast, s ezt kicommentezni
      //fragmentColor.rgb += w * directLighting(hit.xyz, normal, -d.xyz);

      w *= objectHit.brdf.xyz;
    } 
    //kiolvasni sugariranyban, ha nem talaltunk el semmit
    else {        
      fragmentColor.rgb += w * texture(scene.envTexture, d.xyz).rgb;    
      break;
    }
  }

  fragmentColor = 
      texture(scene.previousFrameTexture, tex) * (1.0 - 1.0 / scene.iFrame) +
      fragmentColor * 1.0 / scene.iFrame;
}
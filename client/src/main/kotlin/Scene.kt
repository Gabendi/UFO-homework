import org.w3c.dom.HTMLCanvasElement
import org.khronos.webgl.WebGLRenderingContext as GL
import vision.gears.webglmath.UniformProvider
import vision.gears.webglmath.*
import org.khronos.webgl.Uint32Array
import org.khronos.webgl.get
import vision.gears.webglmath.Mat4
import vision.gears.webglmath.SamplerCube
import kotlin.js.Date

class Scene (
  val gl : WebGL2RenderingContext) : UniformProvider("scene"){

  val vsQuad = Shader(gl, GL.VERTEX_SHADER, "quad-vs.glsl")
  val fsTrace = Shader(gl, GL.FRAGMENT_SHADER, "trace-fs.glsl")  
  val fsShow = Shader(gl, GL.FRAGMENT_SHADER, "show-fs.glsl")
  val traceProgram = Program(gl, vsQuad, fsTrace, Program.PNT)
  val showProgram = Program(gl, vsQuad, fsShow, Program.PNT)
  val quadGeometry = TexturedQuadGeometry(gl)  

  val timeAtFirstFrame = Date().getTime()
  var timeAtLastFrame =  timeAtFirstFrame

  val camera = PerspectiveCamera(*Program.all)

  val quadrics = ArrayList<Quadric>()
  val lights = ArrayList<Light>()

  val envTexture by SamplerCube()

  val randoms by Vec4Array(64)

  lateinit var defaultFramebuffer : DefaultFramebuffer
  lateinit var framebuffers : Pair<Framebuffer, Framebuffer>

  val previousFrameTexture by Sampler2D()
  val avaragedFrameTexture by Sampler2D()

  val iFrame by Vec1()

  init {
    addComponentsAndGatherUniforms(*Program.all)

    createQuadrics()
    createLights()    
    
    envTexture.set(TextureCube(gl,
      "media/posx512.jpg",
      "media/negx512.jpg",
      "media/posy512.jpg",  
      "media/negy512.jpg",  
      "media/posz512.jpg",  
      "media/negz512.jpg"  
    ))
  }

  fun createQuadrics() {
    quadrics.add(Quadric(0))
    quadrics.add(Quadric(1))  
    quadrics.add(Quadric(2))  
    
    quadrics[0].surface.transform(
      Mat4().translate(5.0f, 1.0f)
    )
    quadrics[0].clipper.transform(
      Mat4().translate(5.0f, 1.0f)
    )
    quadrics[0].brdf.set(Vec4(0.8f, 0.8f, 0.8f, 1.0f))

    quadrics[1].surface.set(Quadric.plane)
    quadrics[1].clipper.set(Quadric.unitSphere)
    quadrics[1].clipper.transform(
      Mat4().scale(10f, 10f, 10f))
    quadrics[1].brdf.set(Vec4(0.5f, 0.5f, 0.5f, 1.0f))

    quadrics[2].surface.set(Quadric.plane)
    quadrics[2].surface.transform(
      Mat4().translate(0f, 13f, 0f))
    quadrics[2].clipper.set(Quadric.unitSphere)
    quadrics[2].clipper.transform(
      Mat4().scale(10f, 10f, 10f).translate(0f, 13f, 0f))
    quadrics[2].brdf.set(Vec4(0.7f, 0.75f, 0.7f, 0.0f))
  } 

  fun createLights() {
    lights.add(Light(0))
    //lights.add(Light(1))

    lights[0].position.set(3.0f, 4.0f, 5.0f)
    lights[0].powerDensity.set(10.0f, 10.0f, 10.0f)
    
    //lights[1].position.set(1.0f, 1.0f, 0.0f, 0.0f)
    //lights[1].position.xyz.normalize()
    //lights[1].powerDensity.set(1.0f, 1.0f, 1.0f)
  }

  fun resize(gl : WebGL2RenderingContext, canvas : HTMLCanvasElement) {
    gl.viewport(0, 0, canvas.width, canvas.height)
    camera.setAspectRatio(canvas.width.toFloat() / canvas.height.toFloat())
  
    defaultFramebuffer = DefaultFramebuffer(canvas.width, canvas.height)
    framebuffers = ( 
      Framebuffer(gl, 1, canvas.width, canvas.height,
                 GL.RGBA32F, GL.RGBA, GL.FLOAT)
      to
      Framebuffer(gl, 1, canvas.width, canvas.height,
                 GL.RGBA32F, GL.RGBA, GL.FLOAT)
    )
  }

  @Suppress("UNUSED_PARAMETER")
  fun update(gl : WebGL2RenderingContext, keysPressed : Set<String>) {

    val timeAtThisFrame = Date().getTime() 
    val dt = (timeAtThisFrame - timeAtLastFrame).toFloat() / 1000.0f
    val t  = (timeAtThisFrame - timeAtFirstFrame).toFloat() / 1000.0f    
    timeAtLastFrame = timeAtThisFrame

    camera.move(dt, keysPressed)

    iFrame.x += 1.0f

    // clear the screen
    gl.clearColor(0.0f, 0.3f, 0.3f, 1.0f)
    gl.clearDepth(1.0f)
    gl.clear(GL.COLOR_BUFFER_BIT or GL.DEPTH_BUFFER_BIT)

    var i = 0
    var j = 0
    val randomInts = Uint32Array(2048)
    crypto.getRandomValues(randomInts)
    while (j < 64 && i < 2048) {
      val x = randomInts[i+0].toFloat() / 4294967295.0f * 2.0f - 1.0f;
      val y = randomInts[i+1].toFloat() / 4294967295.0f * 2.0f - 1.0f;
      val z = randomInts[i+2].toFloat() / 4294967295.0f * 2.0f - 1.0f;
      val w = randomInts[i+3].toFloat() / 4294967295.0f * 2.0f;
      if(x*x+y*y+z+z<1.0f) {
        randoms[j].set(x, y, z, w)
        j++ //accept
      }
      i += 4
    }

    framebuffers.first.bind(gl)

    previousFrameTexture.set( framebuffers.second.targets[0] )

    traceProgram.draw(this, camera,
                      *lights.toTypedArray(),
                      *quadrics.toTypedArray())
    quadGeometry.draw()    

    defaultFramebuffer.bind(gl)
    avaragedFrameTexture.set( framebuffers.first.targets[0] )    

    showProgram.draw()
    quadGeometry.draw()

    framebuffers = framebuffers.second to framebuffers.first
   
  }
}

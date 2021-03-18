import vision.gears.webglmath.UniformProvider
import vision.gears.webglmath.Mat4
import vision.gears.webglmath.Vec4

class Quadric(i : Int) : UniformProvider("""quadrics[${i}]""") {
    val surface by QuadraticMat4(unitSphere.clone())
    val clipper by QuadraticMat4(unitSlab.clone())
    val brdf by Vec4() //xyz : params of surface, w : type of surface (0: diffuse, 1: ideal mirror)

    companion object {
        val unitSphere = 
            Mat4(
                1.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 1.0f, 0.0f,
                0.0f, 0.0f, 0.0f, -1.0f
            )
            
        val unitSlab = 
            Mat4(
                0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 1.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, -1.0f
            )

        val plane = 
            Mat4(
                0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 1.0f,
                0.0f, 0.0f, 0.0f, 0.0f,
                0.0f, 0.0f, 0.0f, 0.0f
            )
    }

}
package net.ndrei.teslacorelib.render.selfrendering

import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.vertex.VertexFormat
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.Vec3d
import net.minecraftforge.client.model.pipeline.UnpackedBakedQuad
import net.minecraftforge.common.model.TRSRTransformation

class RawQuad(
        val p1: Vec3d, val u1: Float, val v1: Float,
        val p2: Vec3d, val u2: Float, val v2: Float,
        val p3: Vec3d, val u3: Float, val v3: Float,
        val p4: Vec3d, val u4: Float, val v4: Float,
        val side: EnumFacing, val sprite: TextureAtlasSprite, val color: Int,
        val transform: TRSRTransformation?) {

    fun Float.u() = sprite.getInterpolatedU(this.toDouble()/* / 16.0 * sprite.iconWidth*/)
    fun Float.v() = sprite.getInterpolatedV(this.toDouble()/* / 16.0 * sprite.iconHeight*/)

    fun bake(format: VertexFormat): BakedQuad {
        // val normal = this.p3.subtract(this.p2).crossProduct(this.p1.subtract(this.p2)).normalize()

        val normal = Vec3d(side.frontOffsetX.toDouble(), side.frontOffsetY.toDouble(), side.frontOffsetZ.toDouble())

        val builder = UnpackedBakedQuad.Builder(format)
        builder.setTexture(this.sprite)
        builder.putVertex(this.sprite, normal, this.p1.x / 32.0, this.p1.y / 32.0, this.p1.z / 32.0, this.u1.u(), this.v1.v(), this.color, this.transform)
        builder.putVertex(this.sprite, normal, this.p2.x / 32.0, this.p2.y / 32.0, this.p2.z / 32.0, this.u2.u(), this.v2.v(), this.color, this.transform)
        builder.putVertex(this.sprite, normal, this.p3.x / 32.0, this.p3.y / 32.0, this.p3.z / 32.0, this.u3.u(), this.v3.v(), this.color, this.transform)
        builder.putVertex(this.sprite, normal, this.p4.x / 32.0, this.p4.y / 32.0, this.p4.z / 32.0, this.u4.u(), this.v4.v(), this.color, this.transform)
        return builder.build()
    }
}
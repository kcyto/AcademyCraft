package cn.academy.ability.vanilla.generic.client.effect

import cn.academy.Resources
import cn.academy.client.CameraPosition
import cn.lambdalib2.render.legacy.Tessellator
import cn.academy.entity.LocalEntity
import cn.lambdalib2.registry.mc.RegEntityRender
import cn.lambdalib2.util.{EntityLook, GameTimer, RenderUtils, VecUtils}
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import net.minecraft.client.renderer.entity.{Render, RenderManager}
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.Vec3d
import net.minecraft.world.World
import org.lwjgl.opengl.GL11._

@RegEntityRender(classOf[SmokeEffect])
class SmokeEffectRenderer(m: RenderManager) extends Render[SmokeEffect](m) {
  private val TEXTURE = Resources.preloadTexture("effects/smokes")
  private val FRAMES_PER_ROW = 2
  private val FRAME_SIZE = 1.0 / FRAMES_PER_ROW

  override def doRender(
                         eff: SmokeEffect,
                         x: Double, y: Double, z: Double,
                         partialTicks: Float,
                         wtf: Float
                       ): Unit = {
    val campos = CameraPosition.getVec3d
    val delta = VecUtils.subtract(new Vec3d(x, y, z), campos)
    val look = new EntityLook(delta)

    glPushMatrix()
    glEnable(GL_BLEND)
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    glDisable(GL_ALPHA_TEST)
    glDisable(GL_CULL_FACE)

    glTranslated(x, y, z)
    glRotated(-look.yaw + 180, 0, 1, 0)
    glRotated(-look.pitch, 1, 0, 0)
    glScaled(eff.size, eff.size, 1)

    val u = (eff.frame % FRAMES_PER_ROW) * FRAME_SIZE
    val v = (eff.frame / FRAMES_PER_ROW) * FRAME_SIZE

    RenderUtils.loadTexture(TEXTURE)
    glColor4f(1, 1, 1, eff.alpha)

    val t = Tessellator.instance
    t.startDrawingQuads()
    t.addVertexWithUV(-1, -1, 0, u,          v)
    t.addVertexWithUV(-1,  1, 0, u,          v + FRAME_SIZE)
    t.addVertexWithUV( 1,  1, 0, u + FRAME_SIZE, v + FRAME_SIZE)
    t.addVertexWithUV( 1, -1, 0, u + FRAME_SIZE, v)
    t.draw()

    glEnable(GL_CULL_FACE)
    glEnable(GL_ALPHA_TEST)
    glPopMatrix()
  }

  override def getEntityTexture(entity: SmokeEffect): ResourceLocation = null
}

@SideOnly(Side.CLIENT)
class SmokeEffect(world: World) extends LocalEntity(world) {
  private val INIT_LIFE_TIME = 4.0f
  private val FRAME_COUNT = 4

  setSize(1, 1)

  val initTime: Double = GameTimer.getTime
  val frame: Int = rand.nextInt(FRAME_COUNT)
  val lifeModifier: Float = 0.5f + rand.nextFloat() * 0.2f
  val rotSpeed: Float = 0.3f * (rand.nextFloat() + 3)

  var size: Float = 1.0f
  var rotation: Float = 0.0f

  override def onUpdate(): Unit = {
    rotation += rotSpeed
    posX += motionX
    posY += motionY
    posZ += motionZ

    if (deltaTime >= INIT_LIFE_TIME) setDead()
  }

  def alpha: Float = {
    val progress = (deltaTime / lifeModifier).toFloat
    progress match {
      case p if p <= 0.3f => p / 0.3f
      case p if p <= 1.5f => 1.0f
      case p if p <= 2.0f => 1 - (p - 1.5f)/0.5f
      case _ => 0.0f
    }
  }

  private def deltaTime: Double = GameTimer.getTime - initTime
  override def shouldRenderInPass(pass: Int): Boolean = pass == 1
}
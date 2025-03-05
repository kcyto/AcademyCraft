package cn.academy.ability.vanilla.generic.client.effect

import cn.academy.Resources
import cn.academy.client.CameraPosition
import cn.lambdalib2.render.legacy.Tessellator
import cn.academy.entity.LocalEntity
import cn.lambdalib2.util.{EntityLook, GameTimer, RenderUtils}
import cn.lambdalib2.util.VecUtils._
import net.minecraftforge.fml.client.registry.RenderingRegistry
import net.minecraft.client.renderer.entity.{Render, RenderManager}
import net.minecraft.entity.Entity
import net.minecraft.util.ResourceLocation
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import cn.lambdalib2.registry.StateEventCallback
import cn.lambdalib2.registry.mc.RegEntityRender
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import org.lwjgl.opengl.GL11._

object SmokeEffectConfig {
  val TEXTURE_ROWS = 2
  val TEXTURE_COLS = 2
  val FRAME_COUNT = TEXTURE_ROWS * TEXTURE_COLS

  val FADE_IN_DURATION = 0.3f
  val FULL_VISIBLE_DURATION = 1.2f
  val FADE_OUT_DURATION = 0.5f
  val TOTAL_LIFETIME = FADE_IN_DURATION + FULL_VISIBLE_DURATION + FADE_OUT_DURATION

  val BASE_LIFE_MODIFIER = 0.5f
  val LIFE_MODIFIER_VARIANCE = 0.2f
  val ROT_SPEED_BASE = 3.0f
  val ROT_SPEED_VARIANCE = 1.0f
}

@RegEntityRender(classOf[SmokeEffect])
class SmokeEffectRenderer(renderManager: RenderManager) extends Render[SmokeEffect](renderManager) {
  import SmokeEffectConfig._

  private val texture = Resources.getTexture("effects/smokes")
  private val tessellator = Tessellator.instance

  override def doRender(
                         effect: SmokeEffect,
                         x: Double,
                         y: Double,
                         z: Double,
                         partialTicks: Float,
                         entityAge: Float
                       ): Unit = {
    val campos = CameraPosition.getVec3d
    val delta = subtract(new Vec3d(x, y, z), campos)
    val look = new EntityLook(delta)

    glEnable(GL_BLEND)
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
    glDisable(GL_ALPHA_TEST)
    glDisable(GL_CULL_FACE)

    try {
      glPushMatrix()
      glTranslated(x, y, z)
      glRotated(-look.yaw + 180, 0, 1, 0)
      glRotated(-look.pitch, 1, 0, 0)
      glScaled(effect.size, effect.size, 1)

      val (u, v) = calculateUV(effect.frame)
      glColor4f(1, 1, 1, effect.alpha)
      RenderUtils.loadTexture(texture)

      tessellator.startDrawingQuads()
      tessellator.addVertexWithUV(-1, -1, 0, u,        v)
      tessellator.addVertexWithUV(-1,  1, 0, u,        v + 0.5)
      tessellator.addVertexWithUV( 1,  1, 0, u + 0.5,  v + 0.5)
      tessellator.addVertexWithUV( 1, -1, 0, u + 0.5,  v)
      tessellator.draw()
    } finally {
      glPopMatrix()
      glEnable(GL_CULL_FACE)
      glEnable(GL_ALPHA_TEST)
      glDisable(GL_BLEND)
    }
  }

  private def calculateUV(frame: Int): (Double, Double) = {
    val col = frame % TEXTURE_COLS
    val row = frame / TEXTURE_COLS
    (col.toDouble / TEXTURE_COLS, row.toDouble / TEXTURE_ROWS)
  }

  override def getEntityTexture(entity: SmokeEffect): ResourceLocation = texture
}

@SideOnly(Side.CLIENT)
class SmokeEffect(world: World) extends LocalEntity(world) {
  import SmokeEffectConfig._

  setSize(1, 1)

  private val initTime = GameTimer.getTime
  private val lifeModifier = BASE_LIFE_MODIFIER + rand.nextFloat() * LIFE_MODIFIER_VARIANCE
  private val rotSpeed = ROT_SPEED_BASE + rand.nextFloat() * ROT_SPEED_VARIANCE
  val frame: Int = rand.nextInt(FRAME_COUNT)

  var rotation: Float = 0.0f
  var size: Float = 1.0f

  override def onUpdate(): Unit = {
    rotation += rotSpeed
    posX += motionX
    posY += motionY
    posZ += motionZ

    if (ageSeconds >= TOTAL_LIFETIME) {
      setDead()
    }
  }

  def alpha: Float = {
    val normalizedAge = ageSeconds / lifeModifier
    normalizedAge match {
      case t if t <= FADE_IN_DURATION          => t / FADE_IN_DURATION
      case t if t <= FADE_IN_DURATION + FULL_VISIBLE_DURATION => 1.0f
      case t if t <= TOTAL_LIFETIME            => 1 - (t - (FADE_IN_DURATION + FULL_VISIBLE_DURATION)) / FADE_OUT_DURATION
      case _                                   => 0.0f
    }
  }

  private def ageSeconds: Float = (GameTimer.getTime - initTime).toFloat

  override def shouldRenderInPass(pass: Int): Boolean = pass == 1
}
package cn.academy.ability.vanilla.generic.client.effect

import cn.academy.Resources
import cn.academy.entity.LocalEntity
import cn.lambdalib2.registry.StateEventCallback
import cn.lambdalib2.registry.mc.RegEntityRender
import cn.lambdalib2.render.Mesh
import cn.lambdalib2.render.legacy.{LegacyMesh, LegacyMeshUtils, SimpleMaterial}
import cn.lambdalib2.util.{Debug, EntityLook, RandUtils, RenderUtils}
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import net.minecraft.client.renderer.entity.{Render, RenderManager}
import net.minecraft.entity.Entity
import net.minecraft.init.Blocks
import net.minecraft.util.{EnumFacing, ResourceLocation}
import net.minecraft.util.math.{BlockPos, Vec3d}
import net.minecraft.world.World
import net.minecraftforge.fml.client.registry.{IRenderFactory, RenderingRegistry}
import net.minecraftforge.fml.common.event.FMLInitializationEvent
import org.lwjgl.opengl.GL11._

@RegEntityRender(classOf[BloodSprayEffect])
class BloodSprayRenderer(manager: RenderManager) extends Render[BloodSprayEffect](manager) {
  private val TEXTURE_GROUND_PATHS = generateTexturePaths("grnd", 3)
  private val TEXTURE_WALL_PATHS = generateTexturePaths("wall", 3)

  private val mesh: LegacyMesh = {
    val m = new LegacyMesh()
    LegacyMeshUtils.createBillboard(m, -0.5, -0.5, 0.5, 0.5)
    m
  }

  private val material = new SimpleMaterial(null)

  private def generateTexturePaths(prefix: String, count: Int): Vector[ResourceLocation] =
    (0 until count).map(i => Resources.getTexture(s"effects/blood_spray/$prefix/$i")).toVector

  override def doRender(
                         eff: BloodSprayEffect,
                         x: Double, y: Double, z: Double,
                         partialTicks: Float,
                         destroyProgress: Float
                       ): Unit = {
    val textures = if (eff.isWall) TEXTURE_WALL_PATHS else TEXTURE_GROUND_PATHS
    val texture = textures(eff.textureID % textures.size)

    material.setTexture(texture)
    RenderUtils.loadTexture(texture)
    glPushMatrix()
    glDisable(GL_CULL_FACE)

    glTranslated(x, y, z)
    glRotatef(-eff.rotationYaw, 0, 1, 0)
    glRotatef(-eff.rotationPitch, 1, 0, 0)
    glTranslated(eff.planeOffset._1, eff.planeOffset._2, 0)
    glScaled(eff.size, eff.size, eff.size)
    glRotated(eff.rotation, 0, 0, 1)

    mesh.draw(material)

    glEnable(GL_CULL_FACE)
    glPopMatrix()
  }

  override def getEntityTexture(entity: BloodSprayEffect): ResourceLocation = null
}

class BloodSprayEffect(world: World, pos: BlockPos, side: Int) extends LocalEntity(world) {
  require(side >= 0 && side < EnumFacing.values().length, s"Invalid side value: $side")

  private val dir = EnumFacing.values()(side)
  val textureID: Int = RandUtils.rangei(0, 10)

  val size: Double = RandUtils.ranged(1.1, 1.4) * (if (dir.getAxis.isVertical) 1.0 else 0.8)
  val rotation: Double = RandUtils.ranged(0, 360)
  val planeOffset: (Double, Double) = (rand.nextGaussian() * 0.15, rand.nextGaussian() * 0.15)

  {
    ignoreFrustumCheck = true
    setSize(1.5f, 2.2f)
    updatePosition()
  }

  private def updatePosition(): Unit = {
    val blockState = world.getBlockState(pos)
    val bounds = blockState.getBoundingBox(world, pos)

    val dx = bounds.maxX - bounds.minX
    val dy = bounds.maxY - bounds.minY
    val dz = bounds.maxZ - bounds.minZ

    val cx = (bounds.minX + bounds.maxX) * 0.5
    val cy = (bounds.minY + bounds.maxY) * 0.5
    val cz = (bounds.minZ + bounds.maxZ) * 0.5

    val offsetFactor = 0.51

    setPosition(
      pos.getX + cx + (dir.getXOffset * offsetFactor * dx).toDouble,
      pos.getY + cy + (dir.getYOffset * offsetFactor * dy).toDouble,
      pos.getZ + cz + (dir.getZOffset * offsetFactor * dz).toDouble
    )
  }

  new EntityLook(dir).applyToEntity(this)

  override def onUpdate(): Unit = {
    if (ticksExisted > 1200 || world.getBlockState(pos).getBlock == Blocks.AIR) {
      setDead()
    }
  }

  override def shouldRenderInPass(pass: Int): Boolean = pass == 1

  def isWall: Boolean = dir.getAxis.isVertical
}
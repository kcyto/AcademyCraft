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
import net.minecraft.util.math.{AxisAlignedBB, BlockPos, Vec3d}
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
    glPushAttrib(GL_ENABLE_BIT)

    glDisable(GL_CULL_FACE)
    glEnable(GL_BLEND)
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

    glTranslated(x, y, z)
    glRotatef(-eff.rotationYaw, 0, 1, 0)
    glRotatef(-eff.rotationPitch, 1, 0, 0)
    glTranslated(eff.planeOffset._1, eff.planeOffset._2, 0)
    glScaled(eff.size, eff.size, eff.size)
    glRotated(eff.rotation, 0, 0, 1)

    mesh.draw(material)

    glPopAttrib()
    glPopMatrix()
  }

  override def getEntityTexture(entity: BloodSprayEffect): ResourceLocation = null
}

class BloodSprayEffect(world: World, pos: BlockPos, side: Int) extends LocalEntity(world) {
  require(side >= 0 && side < EnumFacing.VALUES.length,
    s"Invalid side value: $side (valid range: 0-${EnumFacing.VALUES.length - 1})")

  private val dir = EnumFacing.VALUES(side)
  val textureID: Int = RandUtils.rangei(0, 2)

  val planeOffset: (Double, Double) = {
    val clamp = (v: Double) => v.max(-0.2).min(0.2)
    (clamp(rand.nextGaussian() * 0.15), clamp(rand.nextGaussian() * 0.15))
  }

  val size: Double = RandUtils.ranged(1.1, 1.4) * (if (isWall) 0.8 else 1.0)
  val rotation: Double = RandUtils.ranged(0, 360)

  {
    ignoreFrustumCheck = true
    setSize(1.5f, 2.2f)
    updatePosition()
  }

  private def updatePosition(): Unit = {
    val blockState = world.getBlockState(pos)
    val bounds =
      if (blockState.getBlock == Blocks.AIR)
        new AxisAlignedBB(0,0,0,1,1,1)
      else
        blockState.getBoundingBox(world, pos)

    val (dx, dy, dz) = (bounds.maxX - bounds.minX, bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ)
    val (cx, cy, cz) = (bounds.minX + dx/2, bounds.minY + dy/2, bounds.minZ + dz/2)

    val offsetFactor = 0.51
    val (xOffset, yOffset, zOffset) = (
      dir.getXOffset * offsetFactor * dx,
      dir.getYOffset * offsetFactor * dy,
      dir.getZOffset * offsetFactor * dz
    )

    setPosition(
      pos.getX + cx + xOffset,
      pos.getY + cy + yOffset,
      pos.getZ + cz + zOffset
    )
  }

  new EntityLook(dir).applyToEntity(this)

  override def onUpdate(): Unit = {
    if (ticksExisted > 1200 || !world.isBlockLoaded(pos) || world.getBlockState(pos).getBlock == Blocks.AIR) {
      setDead()
    }
  }

  override def shouldRenderInPass(pass: Int): Boolean = pass == 1

  def isWall: Boolean = dir.getAxis.isHorizontal
}
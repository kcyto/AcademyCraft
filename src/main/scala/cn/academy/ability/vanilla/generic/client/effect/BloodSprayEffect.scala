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

object BloodSprayEffect {
  val MAX_LIFETIME = 1200
  val TEXTURE_VARIANTS = 3
  val PLANE_OFFSET_MULTIPLIER = 0.15
}

@RegEntityRender(classOf[BloodSprayEffect])
class BloodSprayRenderer(manager: RenderManager) extends Render[BloodSprayEffect](manager) {
  import org.lwjgl.opengl.GL11._

  private val texGrnd = loadTextures("grnd")
  private val texWall = loadTextures("wall")

  private val mesh = {
    val m = new LegacyMesh
    LegacyMeshUtils.createBillboard(m, -0.5, -0.5, 0.5, 0.5)
    m
  }
  private val material = new SimpleMaterial(null)

  private def loadTextures(name: String): Vector[ResourceLocation] =
    (0 until BloodSprayEffect.TEXTURE_VARIANTS)
      .map(x => Resources.getTexture(s"effects/blood_spray/$name/$x"))
      .toVector

  override def doRender(eff: BloodSprayEffect, x: Double, y: Double, z: Double, partialTicks: Float, f2: Float): Unit = {
    val textureList = if (eff.isWall) texWall else texGrnd
    val texture = textureList(eff.textureID % textureList.size)

    material.setTexture(texture)
    RenderUtils.loadTexture(texture)

    try {
      glDisable(GL_CULL_FACE)
      glPushMatrix()

      glTranslated(x, y, z)
      glRotatef(-eff.rotationYaw, 0, 1, 0)
      glRotatef(-eff.rotationPitch, 1, 0, 0)
      glTranslated(eff.planeOffsetX, eff.planeOffsetY, 0)
      glScaled(eff.size, eff.size, eff.size)
      glRotated(eff.rotation, 0, 0, 1)

      mesh.draw(material)
    } finally {
      glPopMatrix()
      glEnable(GL_CULL_FACE)
    }
  }

  override def getEntityTexture(entity: BloodSprayEffect): ResourceLocation = null
}

class BloodSprayEffect(world: World, pos: BlockPos, side: Int) extends LocalEntity(world) {
  import BloodSprayEffect._

  private val validSide = if (side < 0 || side >= EnumFacing.values().length) {
    EnumFacing.UP
  } else EnumFacing.values()(side)

  val dir: EnumFacing = validSide
  val textureID: Int = RandUtils.rangei(0, TEXTURE_VARIANTS - 1)

  val size: Double = RandUtils.ranged(1.1, 1.4) * (if (dir.getAxis.isVertical) 1.0 else 0.8)
  val rotation: Double = RandUtils.ranged(0, 360)
  val (planeOffsetX, planeOffsetY) = (
    rand.nextGaussian() * PLANE_OFFSET_MULTIPLIER,
    rand.nextGaussian() * PLANE_OFFSET_MULTIPLIER
  )

  {
    ignoreFrustumCheck = true
    setSize(1.5f, 2.2f)
    validatePosition()
  }

  private def validatePosition(): Unit = {
    if (!world.isBlockLoaded(pos)) {
      setDead()
      return
    }

    val blockState = world.getBlockState(pos)
    if (blockState.getBlock == Blocks.AIR) {
      setDead()
      return
    }

    val bounds = blockState.getBoundingBox(world, pos)
    val (dx, dy, dz) = (bounds.maxX - bounds.minX, bounds.maxY - bounds.minY, bounds.maxZ - bounds.minZ)
    val (xm, ym, zm) = (
      (bounds.minX + bounds.maxX) / 2,
      (bounds.minY + bounds.maxY) / 2,
      (bounds.minZ + bounds.maxZ) / 2
    )

    setPosition(
      pos.getX + xm + dir.getXOffset * 0.51 * dx,
      pos.getY + ym + dir.getYOffset * 0.51 * dy,
      pos.getZ + zm + dir.getZOffset * 0.51 * dz
    )
  }

  new EntityLook(dir).applyToEntity(this)

  override def onUpdate(): Unit = {
    if (ticksExisted > MAX_LIFETIME || !world.isBlockLoaded(pos) || world.getBlockState(pos).getBlock == Blocks.AIR) {
      setDead()
    }
  }

  override def shouldRenderInPass(pass: Int): Boolean = pass == 1

  def isWall: Boolean = dir.getAxis.isVertical
}
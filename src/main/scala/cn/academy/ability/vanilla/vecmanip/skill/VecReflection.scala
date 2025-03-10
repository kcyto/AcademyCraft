package cn.academy.ability.vanilla.vecmanip.skill

import java.util.function.Predicate

import cn.academy.ability.Skill
import cn.academy.ability.context.ClientRuntime.{ActivateHandlers, IActivateHandler}
import cn.academy.ability.context._
import cn.academy.ability.ctrl.KeyDelegates
import cn.academy.client.sound.ACSounds
import cn.academy.ability.vanilla.vecmanip.client.effect.{WaveEffect, WaveEffectUI}
import cn.academy.ability.vanilla.vecmanip.skill.EntityAffection.{Affected, Excluded}
import cn.academy.event.ability.ReflectEvent
import cn.lambdalib2.s11n.network.NetworkMessage.Listener
import cn.lambdalib2.util.MathUtils._
import cn.lambdalib2.util.{Raytrace, SideUtils, WorldUtils}
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.relauncher.{Side, SideOnly}
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.entity.projectile._
import net.minecraft.util.{DamageSource, SoundCategory}
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.living.{LivingAttackEvent, LivingHurtEvent}

object VecReflection extends Skill("vec_reflection", 4) {
  MinecraftForge.EVENT_BUS.register(this)

  @SideOnly(Side.CLIENT)
  override def activate(rt: ClientRuntime, keyid: Int): Unit = {
    rt.addKey(keyid, KeyDelegates.contextActivate(this, new VecReflectionContext(_)))
  }
}

import VecReflectionContext._
import cn.lambdalib2.util.RandUtils._
import cn.academy.ability.api.AbilityAPIExt._
import collection.mutable
import net.minecraft.util.math.Vec3d
import cn.lambdalib2.util.VecUtils._
import VMSkillHelper._

private object VecReflectionContext {
  final val MSG_EFFECT = "effect"
  final val MSG_REFLECT_ENTITY = "reflect_ent"

  def reflect(entity: Entity, player: EntityPlayer, ctx: VecReflectionContext): Unit = {
    val velocity = new Vec3d(entity.motionX, entity.motionY, entity.motionZ)
    val speed = velocity.length()

    if (ctx.ctx.getSkillExp <= 0.25f) {
      val lookVec = player.getLookVec.normalize()
      val dot = velocity.dotProduct(lookVec)
      val reflectedVelocity = velocity.subtract(lookVec.scale(2 * dot))

      val normalized =
        if (reflectedVelocity.lengthSquared() > 1e-6) reflectedVelocity.normalize()
        else lookVec

      val newVelocity = normalized.scale(speed)
      entity.motionX = newVelocity.x
      entity.motionY = newVelocity.y
      entity.motionZ = newVelocity.z
    } else {
      entity.motionX = -entity.motionX
      entity.motionY = -entity.motionY
      entity.motionZ = -entity.motionZ

      if (speed > 0) {
        val horizontalLength = Math.hypot(entity.motionX, entity.motionZ)
        val yaw = Math.toDegrees(Math.atan2(entity.motionZ, entity.motionX)).toFloat - 90.0f
        val pitch = Math.toDegrees(-Math.asin(entity.motionY / speed)).toFloat

        entity.rotationYaw = yaw
        entity.prevRotationYaw = yaw
        entity.rotationPitch = pitch
        entity.prevRotationPitch = pitch
      }
    }

    entity.velocityChanged = true
  }
}

class VecReflectionContext(p: EntityPlayer) extends Context(p, VecReflection) {
  import scala.collection.JavaConverters._
  import VecReflectionContext._

  private val visited = mutable.Set[Entity]()
  @volatile private var _isAttacking = false

  private def isPlayerOwned(entity: Entity): Boolean = entity match {
    case throwable: EntityThrowable => throwable.getThrower == player
    case fireball: EntityFireball => Option(fireball.shootingEntity).contains(player)
    case arrow: EntityArrow => Option(arrow.shootingEntity).contains(player)
    case _ => false
  }

  @Listener(channel=MSG_MADEALIVE, side=Array(Side.SERVER))
  def s_makeAlive(): Unit = {
    MinecraftForge.EVENT_BUS.register(this)
    ctx.consume(overloadToKeep, 0)
    overloadKeep = ctx.cpData.getOverload
  }

  @Listener(channel=MSG_TERMINATED, side=Array(Side.SERVER, Side.CLIENT))
  def g_terminate(): Unit = {
    MinecraftForge.EVENT_BUS.unregister(this)
    visited.clear()
  }

  @Listener(channel=MSG_TICK, side=Array(Side.SERVER))
  def s_tick(): Unit = {
    if(ctx.cpData.getOverload < overloadKeep) ctx.cpData.setOverload(overloadKeep)
    val range = 4
    val entities = WorldUtils.getEntities(player.world, player.posX, player.posY, player.posZ, range,
      new Predicate[Entity] {
        override def test(e: Entity): Boolean =
          !visited.contains(e) && !EntityAffection.isMarked(e)
      }
    ).asScala
    def consumeReflectCost(): Boolean = {
      ctx.consume(0, lerpf(20, 15, ctx.getSkillExp))
    }
    val processedEntities = mutable.Buffer[Entity]()
    var consumedCP = false
    val isMaxExp = ctx.getSkillExp == 1.0f

    entities.foreach { entity =>
      if (!isPlayerOwned(entity)) {
        EntityAffection.getAffectInfo(entity) match {
          case Affected(difficulty) if consumeEntity(difficulty) =>
            VecReflectionContext.reflect(entity, player, this)
            EntityAffection.mark(entity)
            ctx.addSkillExp(difficulty * 0.0008f)
            sendToClient(MSG_REFLECT_ENTITY, entity)
            processedEntities += entity

            if (!consumeReflectCost()) terminate()
            consumedCP = true

          case _ =>
        }
      }
    }

    visited ++= processedEntities

    if (!isMaxExp && !consumeNormal()) terminate()
    else if (processedEntities.nonEmpty && !consumedCP) terminate()
  }

  private def createNewFireball(source: EntityFireball): Unit = {
    Option(source).foreach { src =>
      val reversedVelocity = new Vec3d(-src.motionX, -src.motionY, -src.motionZ)
      val speed = reversedVelocity.length()

      val finalVelocity =
        if (ctx.getSkillExp >= 0.25f) player.getLookVec.normalize().scale(speed)
        else reversedVelocity.normalize().scale(speed)

      val fireball = src match {
        case l: EntityLargeFireball =>
          val fb = new EntityLargeFireball(world(), src.shootingEntity, finalVelocity.x, finalVelocity.y, finalVelocity.z)
          fb.explosionPower = l.explosionPower
          fb
        case _ =>
          new EntitySmallFireball(world(), src.shootingEntity, finalVelocity.x, finalVelocity.y, finalVelocity.z)
      }

      fireball.setPosition(src.posX, src.posY, src.posZ)
      world().spawnEntity(fireball)
      src.setDead()
    }
  }

  @Listener(channel=MSG_TICK, side=Array(Side.CLIENT))
  def c_tick(): Unit = {
    if(!consumeNormal)
      terminate()
  }

  @SubscribeEvent
  def onReflect(evt: ReflectEvent): Unit = {
    if (evt.target.equals(player)) {
      evt.setCanceled(true)

      val dpos = subtract(entityHeadPos(evt.player), entityHeadPos(player))
      sendToClient(MSG_EFFECT, add(add(player.getPositionVector, new Vec3d(0, ranged(0.4, 1.3), 0)), multiply(dpos.normalize(), 0.5)))
    }
  }

  /**
   * Note: Canceling the damage event in `LivingHurtEvent` still causes knockback, so there needs
   *  to be one more pre-testing.
   */
  @SubscribeEvent
  def onLivingAttack(evt: LivingAttackEvent): Unit = {
    if (evt.getEntityLiving.equals(player)) {
      val (performed, _) = handleAttack(evt.getSource, evt.getAmount, passby = true)
      if (performed) {
        handleAttack(evt.getSource, evt.getAmount, passby = false)
        evt.setCanceled(true)
      }
    }
  }

  @SubscribeEvent
  def onLivingHurt(evt: LivingHurtEvent): Unit = {
    if (evt.getEntityLiving.equals(player)  && evt.getAmount <=9999) {
      val (_, dmg) = handleAttack(evt.getSource, evt.getAmount, passby = false)
      evt.setAmount(dmg)
      if(dmg<=0){
        evt.setCanceled(true)
      }
    }
  }

  /**
   * @param passby If passby=true, and this isn't a complete absorb, the action will not perform. Else it will.
   * @return (Whether action had been really performed, processed damage)
   */
  private def handleAttack(dmgSource: DamageSource, dmg: Float, passby: Boolean): (Boolean, Float) = {
    val reflectDamage = lerpf(0.8f, 1.5f, ctx.getSkillExp) * dmg
    if (!passby && !_isAttacking) {
      _isAttacking = true
      consumeDamage(dmg)
      ctx.addSkillExp(dmg * 0.0004f)

      Option(dmgSource.getImmediateSource)
        .filter(_ != player)
        .foreach { sourceEntity =>
          ctx.attack(sourceEntity, reflectDamage)
          if (!SideUtils.isClient) sendToClient(MSG_EFFECT, sourceEntity.getPositionVector)
        }

      _isAttacking = false
      (true, 0)
    } else {
      (reflectDamage > 0, 0)
    }
  }

  private def consumeEntity(difficulty: Float): Boolean = {
    ctx.consume(0, difficulty * lerpf(300, 160, ctx.getSkillExp))
  }

  private def consumeDamage(damage: Float): Unit = ctx.consumeWithForce(0, lerpf(20, 15, ctx.getSkillExp) * damage)

  private def consumeNormal(): Boolean = {
    ctx.consume(0, lerpf(15, 11, ctx.getSkillExp))
  }

  private val overloadToKeep = lerpf(350, 250, ctx.getSkillExp)
  private var overloadKeep = 0f

}

@SideOnly(Side.CLIENT)
@RegClientContext(classOf[VecReflectionContext])
class VecReflectionContextC(par: VecReflectionContext) extends ClientContext(par) {

  private var activateHandler: IActivateHandler = _
  private val ui = new WaveEffectUI(0.4f, 110, 1.6f)

  @Listener(channel=MSG_MADEALIVE, side=Array(Side.CLIENT))
  private def l_alive(): Unit = if (isLocal) {
    activateHandler = ActivateHandlers.terminatesContext(par)
    ClientRuntime.instance.addActivateHandler(activateHandler)
    MinecraftForge.EVENT_BUS.register(this)
  }

  @Listener(channel=MSG_TERMINATED, side=Array(Side.CLIENT))
  private def l_terminate(): Unit = if (isLocal) {
    ClientRuntime.instance.removeActiveHandler(activateHandler)
    MinecraftForge.EVENT_BUS.unregister(this)
  }

  @Listener(channel=MSG_REFLECT_ENTITY, side=Array(Side.CLIENT))
  private def c_reflectEntity(ent: Entity): Unit = {
    VecReflectionContext.reflect(ent, player, par)
    reflectEffect(entityHeadPos(ent))
  }

  @Listener(channel=MSG_EFFECT, side=Array(Side.CLIENT))
  private def reflectEffect(point: Vec3d): Unit = {
    val eff = new WaveEffect(world, 2, 1.1)
    eff.setPosition(point.x, point.y, point.z)
    eff.rotationYaw = player.rotationYawHead
    eff.rotationPitch = player.rotationPitch

    world.spawnEntity(eff)

    playSound(point)
  }

  private def playSound(pos: Vec3d): Unit = {
    ACSounds.playClient(world, pos.x, pos.y, pos.z, "vecmanip.vec_reflection", SoundCategory.AMBIENT, 0.5f, ranged(0.6f, 1.1f).toFloat
    )
  }

  @SubscribeEvent
  def onRenderOverlay(evt: RenderGameOverlayEvent): Unit = {
    if (evt.getType == ElementType.CROSSHAIRS) {
      val r = evt.getResolution
      ui.onFrame(r.getScaledWidth, r.getScaledHeight)
    }
  }

}
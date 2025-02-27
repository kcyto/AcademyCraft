package cn.academy.ability.ctrl

import cn.academy.ability.Skill
import cn.academy.ability.context.{Context, ContextManager, DelegateState, KeyDelegate}
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.ResourceLocation
import org.apache.logging.log4j.LogManager

import java.util.concurrent.atomic.AtomicInteger
import scala.reflect.ClassTag
import scala.util.Try

object KeyDelegates {
  private val logger = LogManager.getLogger("KeyDelegates")
  private val idGenerator = new AtomicInteger(0)

  def contextActivate[S <: Skill, T <: Context[S]](skill: S, contextProvider: EntityPlayer => T)
                                                  (implicit tag: ClassTag[T]): KeyDelegate = {
    require(skill != null, "Skill cannot be null")
    require(contextProvider != null, "Context provider cannot be null")
    val klass = tag.runtimeClass.asInstanceOf[Class[T]]
    new KeyDelegate {
      private final val uniqueId: Int = idGenerator.getAndIncrement()
      override def onKeyDown(): Unit = {
        Try {
          findContext() match {
            case Some(ctx) =>
              ctx.terminate()
            case None =>
              val player = getPlayer
              val ctx = contextProvider(player)
              if (ctx != null) {
                ContextManager.instance.activate(ctx)
              } else {
              }
          }
        }.recover {
          case ex: Exception =>
            logger.error("KeyDown handler failed", ex)
        }
      }

      override def createID(): Int = uniqueId

      override def getIcon: ResourceLocation = skill.getHintIcon

      override def getSkill: Skill = skill

      override def getState: DelegateState = findContext() match {
        case Some(_) => DelegateState.ACTIVE
        case None    => DelegateState.IDLE
      }

      private def findContext(): Option[T] = {
        val opt = ContextManager.instance.findLocal(klass)
        if (opt.isPresent) Some(opt.get) else None
        }
      }
    }
}
package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseReceiverSkill
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_jin_shen_toc
import com.fengsheng.protos.Role.skill_jin_shen_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 金生火技能【谨慎】：你接收双色情报后，可以用一张手牌与该情报面朝上互换。
 */
class JinShen : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.JIN_SHEN

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseReceiverSkill
        if (fsm?.inFrontOfWhom?.findSkill(skillId) == null) return null
        if (fsm.inFrontOfWhom.getSkillUseCount(skillId) > 0) return null
        if (fsm.messageCard.colors.size < 2) return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        return ResolveResult(executeJinShen(fsm), true)
    }

    private data class executeJinShen(val fsm: ReceivePhaseReceiverSkill) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in fsm.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.inFrontOfWhom, 15)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.inFrontOfWhom) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_jin_shen_tos) {
                log.error("错误的协议")
                return null
            }
            val r = fsm.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                log.error("没有这张卡")
                return null
            }
            r.incrSeq()
            log.info("${r}发动了[谨慎]")
            val messageCard = fsm.messageCard
            r.deleteCard(card.id)
            r.deleteMessageCard(messageCard.id)
            fsm.receiveOrder.removePlayerIfNotHaveThreeBlack(r)
            r.messageCards.add(card)
            fsm.receiveOrder.addPlayerIfHasThreeBlack(r)
            r.cards.add(messageCard)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jin_shen_toc.newBuilder()
                    builder.card = card.toPbCard()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeJinShen::class.java)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeJinShen) return false
            val p = fsm0.fsm.inFrontOfWhom
            val card = p.cards.find { !it.colors.contains(color.Black) } ?: return false
            GameExecutor.post(
                p.game!!,
                {
                    val builder = skill_jin_shen_tos.newBuilder()
                    builder.cardId = card.id
                    p.game!!.tryContinueResolveProtocol(p, builder.build())
                },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}
package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnChooseReceiveCard
import com.fengsheng.phase.OnSendCardSkill
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 张一挺技能【强令】：你传出情报后，或你决定接收情报后，可以宣言至多两个卡牌名称。本回合中，所有角色均不能使用被宣言的卡牌。
 */
class QiangLing : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.QIANG_LING

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm
        if (fsm is OnSendCardSkill) {
            askWhom === fsm.sender || return null
            askWhom.getSkillUseCount(skillId) == 0 || return null
            askWhom.addSkillUseCount(skillId)
            return ResolveResult(executeQiangLing(fsm, askWhom), true)
        } else if (fsm is OnChooseReceiveCard) {
            askWhom === fsm.inFrontOfWhom || return null
            askWhom.getSkillUseCount(skillId) <= 1 || return null
            askWhom.addSkillUseCount(skillId, 2)
            return ResolveResult(executeQiangLing(fsm, askWhom), true)
        }
        return null
    }

    private data class executeQiangLing(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_qiang_ling_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq2 = player.seq
                        builder.seq = seq2
                        GameExecutor.post(
                            player.game!!,
                            {
                                if (player.checkSeq(seq2)) {
                                    val builder2 = skill_qiang_ling_tos.newBuilder()
                                    builder2.enable = false
                                    builder2.seq = seq2
                                    player.game!!.tryContinueResolveProtocol(player, builder2.build())
                                }
                            },
                            player.getWaitSeconds(builder.waitingSecond + 2).toLong(),
                            TimeUnit.SECONDS
                        )
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    val result = arrayOf(Jie_Huo, Diao_Bao, Wu_Dao)
                        .filterNot { r.cannotPlayCard(it) }.run {
                            when (size) {
                                0 -> listOf(Po_Yi, Cheng_Qing).filterNot { r.cannotPlayCard(it) }
                                1 -> plus(if (fsm is OnSendCardSkill && !r.cannotPlayCard(Po_Yi)) Po_Yi else Cheng_Qing)
                                2 -> this
                                else -> shuffled().subList(0, 2)
                            }
                        }
                    val builder = skill_qiang_ling_tos.newBuilder()
                    builder.enable = result.isNotEmpty()
                    builder.addAllTypes(result)
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_qiang_ling_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message.typesCount == 0) {
                log.error("enable为true时types不能为空")
                (player as? HumanPlayer)?.sendErrorMessage("[强令]的卡牌类型不能为空")
                return null
            }
            val typesList = message.typesList.toList()
            for (t in typesList) {
                if (t == UNRECOGNIZED || t == null) {
                    log.error("未知的卡牌类型$t")
                    (player as? HumanPlayer)?.sendErrorMessage("未知的卡牌类型$t")
                    return null
                }
            }
            r.incrSeq()
            log.info("${r}发动了[强令]，禁止了${typesList.toTypedArray().contentToString()}")
            r.game!!.players.forEach { it!!.skills += CannotPlayCard(cardType = typesList) }
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_qiang_ling_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.addAllTypes(typesList)
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeQiangLing::class.java)
        }
    }
}
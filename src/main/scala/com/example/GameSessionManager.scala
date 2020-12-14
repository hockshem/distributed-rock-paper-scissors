package com.example

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import RoundManager.{RoundManagerCommands, RoundManagerResponses, GameStatusUnchanged, GameStatusUpdate, RestartRound}

object GameSessionManager {
    trait GameSessionCommands
    final case class GamePartnerSelection(thatPlayer: ActorRef[GameSessionResponses], thatPlayerName: String) extends GameSessionCommands
    final case class GameInvitationResponse(response: Player.PlayerResponses) extends GameSessionCommands
    final case object GameCreated extends GameSessionCommands
    final case class WrappedRoundUpdates(response: RoundManager.RoundManagerResponses) extends GameSessionCommands
    final case object RematchInvitation extends GameSessionCommands
    final case class RematchInvitationResponse(response: Player.PlayerResponses, fromPlayerName: String) extends GameSessionCommands
    
    trait GameSessionResponses
    final case class GameInvitationRequest(session: ActorRef[Player.PlayerResponses], fromPlayerName: String) extends GameSessionResponses
    final case class AccumulatedScoresUpdate(changeInScore: Int) extends GameSessionResponses 
    final case class RematchInvitationRequest(session: ActorRef[GameSessionCommands], opponentName: String) extends GameSessionResponses

    var thatPlayer: Option[ActorRef[GameSessionResponses]] = None
    var thatPlayerName = ""
    var roundManager: Option[ActorRef[RoundManagerCommands]] = None
    var roundCount = 3 
    var rematchIntentionMap: Map[String, Player.PlayerResponses] = Map()
    
    def apply(thisPlayer: ActorRef[GameSessionResponses], thisPlayerName: String): Behavior[GameSessionCommands] = {
        Behaviors.setup { context => 
            new GameSessionManager(context, thisPlayer, thisPlayerName)
        }
    }
}

class GameSessionManager(context: ActorContext[GameSessionManager.GameSessionCommands], val thisPlayer: ActorRef[GameSessionManager.GameSessionResponses], val thisPlayerName: String) extends AbstractBehavior(context) {
    import GameSessionManager._

    override def onMessage(msg: GameSessionCommands): Behavior[GameSessionCommands] = {
        msg match {
            case GamePartnerSelection(opponent, name) => 
                val messageAdapter = context.messageAdapter[Player.PlayerResponses](GameInvitationResponse.apply)
                opponent ! GameInvitationRequest(messageAdapter, thisPlayerName)
                thatPlayer = Some(opponent)
                thatPlayerName = name
                this
            case GameInvitationResponse(response) => 
                response match {
                    case Player.InvitationAccepted => 
                        context.self ! GameCreated
                        this
                    case Player.InvitationRejected | Player.NotResponded =>
                        thatPlayer = None
                        this 
                }
            case GameCreated => 
                val roundManagerAdapter = context.messageAdapter[RoundManager.RoundManagerResponses](WrappedRoundUpdates.apply)
                val players = Array(thisPlayer, thatPlayer.get)
                roundManager = Some(context.spawn(RoundManager(roundManagerAdapter, players), "Round Manager"))
                this
            case WrappedRoundUpdates(response) => 
                response match {
                    case GameStatusUnchanged => 
                        if (roundCount - 1 >= 0) {
                            roundCount -= 1
                            roundManager.get ! RestartRound
                        } else {
                            context.self ! RematchInvitation
                        }
                        this 
                    case GameStatusUpdate(roundWinner, roundLoser) => 
                        if (roundCount - 1 >= 0) {
                            roundCount -= 1
                            roundManager.get ! RestartRound
                            roundWinner ! AccumulatedScoresUpdate(1)
                            roundLoser ! AccumulatedScoresUpdate(-1)
                        } else {
                            context.self ! RematchInvitation
                        }
                        this
                    case _ => 
                        Behaviors.unhandled
                }
            case RematchInvitation => 
                thisPlayer ! RematchInvitationRequest(context.self, thatPlayerName)
                thatPlayer.get ! RematchInvitationRequest(context.self, thisPlayerName)
                this
            case RematchInvitationResponse(response, fromPlayer) => 
                rematchIntentionMap += (fromPlayer -> response)
                if (rematchIntentionMap.size == 2) {
                    if (rematchIntentionMap.valuesIterator.contains(Player.InvitationRejected)) { 
                        thatPlayer = None 
                        thatPlayerName = ""
                        roundManager = None
                    } else {        
                        roundManager.get ! RestartRound
                    }
                    roundCount = 3 
                    rematchIntentionMap = Map()
                }
                this
        }   
    }
}
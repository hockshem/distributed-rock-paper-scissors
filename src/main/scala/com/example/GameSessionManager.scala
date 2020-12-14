package com.example

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import RoundManager.{RoundManagerResponses, GameStatusUnchanged, GameStatusUpdate}

object GameSessionManager {
    trait GameSessionCommands
    final case class GameInvitation(thatPlayer: ActorRef[GameSessionResponses]) extends GameSessionCommands
    final case class GameInvitationResponse(response: Player.PlayerResponses) extends GameSessionCommands
    final case object GameCreated extends GameSessionCommands
    final case class WrappedRoundUpdates(response: RoundManager.RoundManagerResponses) extends GameSessionCommands
    
    trait GameSessionResponses
    final case class PendingInvitation(session: ActorRef[Player.PlayerResponses], fromPlayerName: String) extends GameSessionResponses
    final case class AccumulatedScoresUpdate(changeInScore: Int) extends GameSessionResponses 
    
    def apply(thisPlayer: ActorRef[GameSessionResponses], thisPlayerName: String): Behavior[GameSessionCommands] = {
        Behaviors.setup { context => 
            new GameSessionManager(context, thisPlayer, thisPlayerName)
        }
    }
}

class GameSessionManager(context: ActorContext[GameSessionManager.GameSessionCommands], val thisPlayer: ActorRef[GameSessionManager.GameSessionResponses], val thisPlayerName: String) extends AbstractBehavior(context) {
    import GameSessionManager._

    var thatPlayer: Option[ActorRef[GameSessionResponses]] = None
    var roundCount = 3 
    override def onMessage(msg: GameSessionCommands): Behavior[GameSessionCommands] = {
        msg match {
            case GameInvitation(opponent) => 
                val messageAdapter = context.messageAdapter[Player.PlayerResponses](GameInvitationResponse.apply)
                opponent ! PendingInvitation(messageAdapter, thisPlayerName)
                thatPlayer = Some(opponent)
                this
            case GameInvitationResponse(response) => 
                response match {
                    case Player.InvitationAccepted => 
                        context.self ! GameCreated
                        this
                    case Player.InvitationRejected =>
                        thatPlayer = None
                        this 
                }
            case GameCreated => 
                val roundManagerAdapter = context.messageAdapter[RoundManager.RoundManagerResponses](WrappedRoundUpdates.apply)
                val players = Array(thisPlayer, thatPlayer.get)
                context.spawn(RoundManager(roundManagerAdapter, players), "Round Manager")
                this
            case WrappedRoundUpdates(response) => 
                response match {
                    case GameStatusUnchanged => 
                        roundCount -= 1
                        this 
                    case GameStatusUpdate(roundWinner, roundLoser) => 
                        roundCount -= 1
                        roundWinner ! AccumulatedScoresUpdate(1)
                        roundLoser ! AccumulatedScoresUpdate(-1)
                        this
                    case _ => 
                        Behaviors.unhandled
                }
        }   
    }
}
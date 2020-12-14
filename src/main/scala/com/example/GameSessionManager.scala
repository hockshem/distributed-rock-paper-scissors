package com.example

package com.example

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

object GameSessionManager {
    sealed trait GameSessionRequests
    final case class GameInvitation(thatPlayer: ActorRef[GameSessionResponses]) extends GameSessionRequests
    final case class GameInvitationResponse(response: Player.PlayerResponses) extends GameSessionRequests
    final case object GameCreated extends GameSessionRequests
    
    trait GameSessionResponses
    final case class PendingInvitation(session: ActorRef[Player.PlayerResponses], fromPlayerName: String) extends GameSessionResponses
    
    def apply(thisPlayer: ActorRef[GameSessionResponses], thisPlayerName: String): Behavior[GameSessionRequests] = {
        Behaviors.setup { context => 
            new GameSessionManager(context, thisPlayer, thisPlayerName)
        }
    }
}

class GameSessionManager(context: ActorContext[GameSessionManager.GameSessionRequests], val thisPlayer: ActorRef[GameSessionManager.GameSessionResponses], val thisPlayerName: String) extends AbstractBehavior(context) {
    import GameSessionManager._
    var thatPlayer: Option[ActorRef[GameSessionResponses]] = None
    override def onMessage(msg: GameSessionRequests): Behavior[GameSessionRequests] = {
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
                
                this 
        }   
    }
}
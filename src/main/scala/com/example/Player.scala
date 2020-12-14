package com.example

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import com.example.GameSessionManager._

object Player {
    sealed trait PlayerResponses
    final case object InvitationAccepted extends PlayerResponses 
    final case object InvitationRejected extends PlayerResponses 

    def apply(): Behavior[GameSessionResponses] = {
        Behaviors.setup { context => 
            new Player(context)
        }
    }
}

class Player(context: ActorContext[GameSessionResponses]) extends AbstractBehavior(context) {
    import Player._
    override def onMessage(msg: GameSessionResponses): Behavior[GameSessionResponses] = { 
        msg match {
            case PendingInvitation(session, fromPlayerName) =>
                session ! InvitationAccepted
                this
        }
    }
}
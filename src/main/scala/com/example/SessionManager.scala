package com.example

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.receptionist.ServiceKey
import akka.actor.typed.receptionist.Receptionist

object SessionManager {
    sealed trait SessionRequests
    final case class NameCheckRequest(name: String, actorRef: ActorRef[NameCheckResponses]) extends SessionRequests
    final case class NewPlayerJoined(name: String, players: ActorRef[Set[String]]) extends SessionRequests
    final case class PlayerDisconnected(name: String) extends SessionRequests

    sealed trait NameCheckResponses
    final case class NameAccepted(session: ActorRef[SessionRequests]) extends NameCheckResponses
    final case object NameRejected extends NameCheckResponses

    var onlineMembers: Set[String] = Set()

    def apply(): Behavior[SessionRequests] = {
        Behaviors.setup { context => 
            new SessionManager(context)
        }
    }
}

class SessionManager(context: ActorContext[SessionManager.SessionRequests]) extends AbstractBehavior(context) {
    import SessionManager._
    override def onMessage(msg: SessionRequests): Behavior[SessionRequests] = {
        msg match {
            case NameCheckRequest(name, actorRef) => 
                if (onlineMembers.contains(name)) {
                    actorRef ! NameRejected
                } else {
                    actorRef ! NameAccepted(context.self)
                }
                this 
            case NewPlayerJoined(name, actorRef) => 
                actorRef ! onlineMembers
                onlineMembers += name
                this
            case PlayerDisconnected(name) => 
                onlineMembers -= name
                this
        }
    }
}
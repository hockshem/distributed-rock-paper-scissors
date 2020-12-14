package com.example

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import GameSessionManager.{GameSessionResponses, GameInvitationRequest, AccumulatedScoresUpdate}
import RoundManager.{RockPaperScissorsSelectionRequest, RockPaperScissorsSelection, Rock}
import _root_.com.example.GameSessionManager.RematchInvitationRequest
import _root_.com.example.GameSessionManager.RematchInvitationResponse


object Player {
    sealed trait PlayerResponses
    final case object InvitationAccepted extends PlayerResponses 
    final case object InvitationRejected extends PlayerResponses 
    final case object NotResponded extends PlayerResponses

    def apply(name: String): Behavior[GameSessionResponses] = {
        Behaviors.setup { context => 
            new Player(context, name)
        }
    }
}

class Player(context: ActorContext[GameSessionResponses], val name: String) extends AbstractBehavior(context) {
    import Player._
    
    var accumulatedScores = 0

    override def onMessage(msg: GameSessionResponses): Behavior[GameSessionResponses] = { 
        msg match {
            case GameInvitationRequest(session, fromPlayerName) =>
                session ! InvitationAccepted
                this
            case RockPaperScissorsSelectionRequest(roundManager) => 
                roundManager ! RockPaperScissorsSelection(context.self, Rock)
                this
            case AccumulatedScoresUpdate(change) => 
                if (accumulatedScores + change >= 0) {
                    accumulatedScores +=  change
                } else accumulatedScores = 0
                this
            case RematchInvitationRequest(session, opponentName) => 
                session ! RematchInvitationResponse(Player.InvitationRejected, name)
                this
        }
    }
}
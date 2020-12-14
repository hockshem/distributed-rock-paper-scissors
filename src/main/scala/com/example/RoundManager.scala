package com.example

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import com.example.GameSessionManager
import akka.util.Timeout
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object RoundManager {
    sealed trait GameCommands 
    // Logic specific to Rock-Paper-Scissors
    sealed trait RockPaperScissorsCommands {
        def winAgainst(opponentSelection: RockPaperScissorsCommands): RockPaperScissorsResults
    }

    sealed trait RockPaperScissorsResults
    final case object Lose extends RockPaperScissorsResults
    final case object Victory extends RockPaperScissorsResults
    final case object Tie extends RockPaperScissorsResults
    
    final case object Paper extends RockPaperScissorsCommands {
        def winAgainst(opponentSelection: RockPaperScissorsCommands): RockPaperScissorsResults = {
            opponentSelection match {
                case Rock => Victory
                case Paper => Tie
                case Scissors => Lose
                case NotSelected => Victory
            }
        }
    }

    final case object Scissors extends RockPaperScissorsCommands {
        def winAgainst(opponentSelection: RockPaperScissorsCommands): RockPaperScissorsResults = {
            opponentSelection match {
                case Rock => Lose
                case Paper => Victory
                case Scissors => Tie
                case NotSelected => Victory
            }
        }
    }

    final case object NotSelected extends RockPaperScissorsCommands {
        def winAgainst(opponentSelection: RockPaperScissorsCommands): RockPaperScissorsResults = {
            opponentSelection match {
                case Rock => Lose
                case Paper => Lose
                case Scissors => Lose
                case NotSelected => Lose
            }
        }
    }

    // Rock-paper-scissors game states
    var playerSelectionMap: Map[String, RockPaperScissorsCommands] = Map()
    val winnerSelectionRule: Map[String, RockPaperScissorsCommands] => RockPaperScissorsResults = (selectionMap) => {
        selectionMap.head._2.winAgainst(selectionMap.last._2)
    }

    // Messages that the actor can process
    final case class RockPaperScissorsSelection(fromPlayer: ActorRef[GameSessionManager.GameSessionResponses], selection: RockPaperScissorsCommands) extends GameCommands
    final case object AllPlayersSelected extends GameCommands

    sealed trait RoundManagerResponses
    final case class RockPaperScissorsSelectionRequest(roundManager: ActorRef[GameCommands]) extends RoundManagerResponses
    final case class GameStatusUpdate(roundWinner: ActorRef[GameSessionManager.GameSessionResponses], roundLoser: ActorRef[GameSessionManager.GameSessionResponses]) extends RoundManagerResponses
    final case object GameStatusUnchanged extends RoundManagerResponses
    
    def apply(manager: ActorRef[GameSessionManager.GameSessionRequests], players: Seq[ActorRef[RoundManagerResponses]]): Behavior[GameCommands] = {
        Behaviors.setup { context => new RoundManager(context, manager)}
    }
}

class RoundManager(context: ActorContext[RoundManager.GameCommands], gameSessionManager: ActorRef[GameSessionManager.GameSessionRequests]) extends AbstractBehavior(context) {
    import RoundManager._
    var thisPlayer: Option[ActorRef[GameSessionManager.GameSessionResponses]] = None
    var thatPlayer: Option[ActorRef[GameSessionManager.GameSessionResponses]] = None
    override def onMessage(msg: GameCommands): Behavior[GameCommands] = {
        msg match {
            case PlayersPair(p, o) =>
                thisPlayer = Some(p)
                thatPlayer = Some(o)
                p ! RockPaperScissorsSelectionRequest(context.self)
                o ! RockPaperScissorsSelectionRequest(context.self)
                this
            case RockPaperScissorsSelection(player, selection) =>
                playerSelectionMap += (player.path.toString() -> selection)
                if (playerSelectionMap.contains(thisPlayer.get.path.toString()) && playerSelectionMap.contains(thatPlayer.get.path.toString())) {
                    context.self ! AllPlayersSelected
                }
                this
            case AllPlayersSelected => 
                val result = winnerSelectionRule(playerSelectionMap)
                result match {
                    case Lose => 
                        gameSessionManager ! GameStatusUpdate(thatPlayer.get, thisPlayer.get)
                    case Victory => 
                        gameSessionManager ! GameStatusUpdate(thisPlayer.get, thatPlayer.get)
                    case Tie => 
                        gameSessionManager ! GameStatusUnchanged
                }
                
        }
    }
}




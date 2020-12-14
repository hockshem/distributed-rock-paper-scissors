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
    // Logic specific to Rock-Paper-Scissors
    sealed trait RockPaperScissorsCommands {
        def winAgainst(opponentSelection: RockPaperScissorsCommands): RockPaperScissorsResults
    }

    sealed trait RockPaperScissorsResults
    final case object Lose extends RockPaperScissorsResults
    final case object Victory extends RockPaperScissorsResults
    final case object Tie extends RockPaperScissorsResults

    final case object Rock extends RockPaperScissorsCommands {
        def winAgainst(opponentSelection: RockPaperScissorsCommands): RockPaperScissorsResults = {
            opponentSelection match {
                case Rock => Tie
                case Paper => Lose
                case Scissors => Victory
                case NotSelected => Victory
            }
        }
    }
    
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

    sealed trait RoundManagerCommands
    sealed trait RoundManagerResponses
    
    final case class RockPaperScissorsSelection(fromPlayer: ActorRef[RoundManagerResponses], selection: RockPaperScissorsCommands) extends RoundManagerCommands
    final case class RockPaperScissorsSelectionRequest(roundManager: ActorRef[RoundManagerCommands]) extends RoundManagerResponses

    final case object AllPlayersSelected extends RoundManagerCommands

    final case class GameStatusUpdate(roundWinner: ActorRef[RoundManagerResponses], roundLoser: ActorRef[RoundManagerResponses]) extends RoundManagerResponses
    final case object GameStatusUnchanged extends RoundManagerResponses
    
    def apply(gameSessionManager: ActorRef[RoundManagerResponses], players: Seq[ActorRef[RoundManagerResponses]]): Behavior[RoundManagerCommands] = {
        Behaviors.setup { context => new RoundManager(context, gameSessionManager, players)}
    }
}

class RoundManager(context: ActorContext[RoundManager.RoundManagerCommands], gameSessionManager: ActorRef[RoundManager.RoundManagerResponses], players: Seq[ActorRef[RoundManager.RoundManagerResponses]]) extends AbstractBehavior(context) {
    import RoundManager._
    
    val thisPlayer = players.head
    val thatPlayer = players.last
    playerSelectionMap += (thisPlayer.path.toString() -> NotSelected)
    playerSelectionMap += (thatPlayer.path.toString() -> NotSelected)

    thisPlayer ! RockPaperScissorsSelectionRequest(context.self)
    thatPlayer ! RockPaperScissorsSelectionRequest(context.self)

    override def onMessage(msg: RoundManagerCommands): Behavior[RoundManagerCommands] = {
        msg match {
            // When one of the player has made a selection
            case RockPaperScissorsSelection(player, selection) =>            
                playerSelectionMap += (player.path.toString() -> selection)
                // If both of the players have already responded
                if (!playerSelectionMap.valuesIterator.contains(NotSelected)) {
                    context.self ! AllPlayersSelected
                }
                this
            case AllPlayersSelected => 
                val result = winnerSelectionRule(playerSelectionMap)
                result match {
                    case Lose => 
                        gameSessionManager ! GameStatusUpdate(thatPlayer, thisPlayer)
                    case Victory => 
                        gameSessionManager ! GameStatusUpdate(thisPlayer, thatPlayer)
                    case Tie => 
                        gameSessionManager ! GameStatusUnchanged
                }
                this
        }
    }
}




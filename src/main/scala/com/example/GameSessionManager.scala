package com.example

import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import RoundManager.{RoundManagerCommands, RoundManagerResponses, GameStatusUnchanged, GameStatusUpdate, RestartRound}

/* This actor class manages a whole game session containing several rounds between two players. It also asks the player's intention to rematch.  
    Also, this actor class is bound to a specific player, as it is being created and assigned to him during sucessful name registration. The player 
    then uses this manager to invite another partner for a game.  */
object GameSessionManager {
    trait GameSessionCommands
    // When the player has selected a game partner 
    final case class GamePartnerSelection(thatPlayer: ActorRef[GameSessionResponses], thatPlayerName: String) extends GameSessionCommands
    // When the partner being invited for a game has responded 
    final case class GameInvitationResponse(response: Player.PlayerResponses) extends GameSessionCommands

    final case object GameCreated extends GameSessionCommands
    // Updates fired from the round manager containing the winner and loser
    final case class WrappedRoundUpdates(response: RoundManager.RoundManagerResponses) extends GameSessionCommands

    final case object RematchInvitation extends GameSessionCommands
    // Analogous to the game invitation response, except that this message class is no longer specific to the opponent but this player himself 
    // This is because we need to ask both parties whether to rematch with the current opponent
    final case class RematchInvitationResponse(response: Player.PlayerResponses, opponent: String) extends GameSessionCommands
    
    trait GameSessionResponses
    // Game invitation request issued by this class to the invited player to create a game 
    final case class GameInvitationRequest(session: ActorRef[Player.PlayerResponses], fromPlayerName: String) extends GameSessionResponses
    // Scores update message issued to the player to update their scores 
    final case class AccumulatedScoresUpdate(changeInScore: Int) extends GameSessionResponses 
    // Message fired to collect the rematch invitation response 
    final case class RematchInvitationRequest(session: ActorRef[GameSessionCommands], opponentName: String) extends GameSessionResponses

    // Private states containing opponent information 
    private var thatPlayer: Option[ActorRef[GameSessionResponses]] = None
    private var thatPlayerName = ""
    // Round-specific information
    private var roundManager: Option[ActorRef[RoundManagerCommands]] = None
    private var roundCount = 3 
    // Game rematch intention states
    private var rematchIntentionMap: Map[String, Player.PlayerResponses] = Map()
    
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
package com.example


import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.receptionist.Receptionist
import _root_.com.typesafe.config.ConfigFactory
import scala.io.StdIn
import SessionManager.{NameRejected, OnlineMembersListing, NewPlayerAcknowledgement}

// Client App Runner 
object ClientMain extends App {
    // Load the default configuration from application.conf
    val defaultConfig = ConfigFactory.load()
    // Set the port that the client app should connect to 
    val clientConfig = ConfigFactory.parseString("akka.remote.artery.canonical.port=0").withFallback(defaultConfig)

    val system = ActorSystem(GameClient(), "RockPaperScissors", clientConfig)
    // Look up for the server actor so that the client actor can fire a message to it 
    system ! GameClient.ServerLookup
}

object GameClient {
    trait Command
    // Response message from the server app 
    final case class ServerResponse(message: String, serverRef: ActorRef[SessionManager.SessionRequests]) extends Command
    // Response from the Receptionist after requesting for remote actor reference 
    private case class ListingResponse(listing: Receptionist.Listing) extends Command
    final case object ServerLookup extends Command
    final case object ClientNameRegistration extends Command
    final case class ReceivedGameInvitation(fromPlayerName: String) extends Command
    final case class ReceivedRematchInvitation(opponentName: String) extends Command
    final case object MakeRPSSelection extends Command
    final case object BecomeIdle extends Command

    final case class RoundLost(score: Int) extends Command
    final case class RoundVictory(score: Int) extends Command 
    final case class RoundTie(score: Int) extends Command

    // Factory method for the client actor behavior 
    def apply(): Behavior[Command] = Behaviors.setup { context => 
        new GameClient(context) }
}

class GameClient(context: ActorContext[GameClient.Command]) extends AbstractBehavior(context) {
    import GameClient._

    private var player: Option[ActorRef[GameSessionManager.GameSessionResponses]] = None 
    private var playerName: String = ""
    private var sessionServer: Option[ActorRef[SessionManager.SessionRequests]] = None
    private var gameSessionServer: Option[ActorRef[GameSessionManager.GameSessionCommands]] = None 
    private var onlineMembers: Map[String, ActorRef[GameSessionManager.GameSessionResponses]] = Map()

    private var idle: Boolean = true

    override def onMessage(msg: Command): Behavior[Command] = {
        msg match {
            case ServerLookup => 
                context.log.info("Looking up for the game server.")
                val adapter = context.messageAdapter[Receptionist.Listing](ListingResponse)
                context.system.receptionist ! Receptionist.Subscribe(GameServer.ServerKey, adapter)
                this
            case ServerResponse(message, server) =>
                context.log.info("Got the session server from the server: " + message)
                context.log.info("Session server path: " + server.path)
                sessionServer = Some(server)
                context.self ! ClientNameRegistration
                this
            case ListingResponse(GameServer.ServerKey.Listing(listing)) =>
                context.log.info("Receptionist responded!")
                listing.foreach { serverRef =>
                    context.log.info("Sending a session server lookup request to the server.")
                    serverRef ! GameServer.SessionServerLookup(context.self)
                } 
                this
            case ClientNameRegistration => 
                val clientInput = readClientName()
                context.log.info("Checking whether your name is available...")
                sessionServer.get ! SessionManager.NameCheckRequest(clientInput, context.self)
                this
            case NameRejected => 
                println("Sorry, the name is taken by other players.")
                context.self ! ClientNameRegistration
                this
            case OnlineMembersListing(players) => 
                context.log.info("Member list updated.")
                onlineMembers = players.removed(playerName)
                if (idle && onlineMembers.size >= 1) { showAvailableOpponents() }
                this
            case NewPlayerAcknowledgement(player, playerName, gameSessionManager, players) => 
                context.log.info(s"Successfully registered with name $playerName!")
                this.player = Some(player)
                this.playerName = playerName
                gameSessionServer = Some(gameSessionManager)
                onlineMembers = players.removed(playerName)
                presentGameMenu()
                this
            case ReceivedGameInvitation(from) =>
                onGameInvitation(s"Received game invitation from $from!\nDo you want to play a game with him/her? (Y/N)")
                this
            case ReceivedRematchInvitation(opponentName) => 
                onRematchInvitation(s"Do you want to rematch with $opponentName? (Y/N)")
                this
            case BecomeIdle =>
                context.log.info("It seems like your opponent does not want to play the game...")
                idle = true
                presentGameMenu()
                this
            case MakeRPSSelection => 
                onRPSSelectionRequest()
                this
            case RoundLost(score) => 
                onRoundLost(score)
                this 
            case RoundVictory(score) =>
                onRoundVictory(score)
                this
            case RoundTie(score) => 
                onRoundTie(score)
                this
        }
    }

    private def onRoundLost(score: Int) = {
        context.log.info(s"You lost the last round. Your current score is $score.")
    }

    private def onRoundVictory(score: Int) = {
        context.log.info(s"You won the last round! Congratulations! Your current score is $score." )
    }

    private def onRoundTie(score: Int) = {
        context.log.info(s"It was a tie! Your current score is: $score.")
    }

    private def readClientName(): String = {
        context.log.info("Please register your name: ")
        StdIn.readLine()
    }

    private def presentGameMenu(): Unit = {
        context.log.info("Welcome to the classic Rock-Paper-Scissors game!")
        
        if (onlineMembers.size > 0) { 
            showAvailableOpponents() 
        } else {
            context.log.info("Looks like there are no people online... ")
        }
    }

    private def onGameInvitation(displayMessage: String) = {
        context.log.info(displayMessage)
        val selection = StdIn.readLine()
        selection.toUpperCase match {
            case "Y" => 
                idle = false
                player.get ! Player.ClientGameInvitationResponse(true) 
            case _ => 
                player.get ! Player.ClientGameInvitationResponse(false)
        }
    }

    private def onRematchInvitation(displayMessage: String) = {
        context.log.info(displayMessage)
        val selection = StdIn.readLine()
        selection.toUpperCase match {
            case "Y" => player.get ! Player.ClientRematchInvitationResponse(true)
            case _ => player.get ! Player.ClientRematchInvitationResponse(false) 
        }
    }

    private def onRPSSelectionRequest() = {
        context.log.info(s"Please make a selection:\n1. Rock\n2. Paper\n3. Scissors\n")
        val selection = StdIn.readLine()
        player.get ! Player.ClientRPSSelection(selection)
    }

    private def showAvailableOpponents(): Unit = {
        val memberArray = onlineMembers.keys.toArray
        context.log.info("Online Members")
        for (m <- memberArray) {
            context.log.info(s"${memberArray.indexOf(m)}.$m")
        }
        // TODO: might be in deadlock 
        context.log.info("Enter the player number to play the game with: (-1 to quit)")
        val choice = StdIn.readLine()
        if (choice != "-1") {
            val index = choice.toInt
            val selectedName = memberArray(index)
            gameSessionServer.get ! GameSessionManager.GamePartnerSelection(onlineMembers(selectedName), selectedName)
            idle = false
            context.log.info("Invitation sent! Waiting for the player to accept...")
        }
    }
}
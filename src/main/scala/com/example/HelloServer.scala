//#full-example
package com.example


import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AbstractBehavior
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.receptionist.ServiceKey
import com.typesafe.config.ConfigFactory
import akka.cluster.typed._

// Server App Runner
object ServerMain extends App {
    val defaultConfig = ConfigFactory.load()
    // Set the port that the server should connect to 
    val serverConfig = ConfigFactory.parseString("akka.remote.artery.canonical.port=5020").withFallback(defaultConfig)
    val serverSystem = ActorSystem(HelloServer(), "HelloSystem", serverConfig)
}

// Companion object for the HelloServerBehavior
object HelloServer {
    sealed trait Command
    // Client request message that this server can process 
    final case class ClientRequest(name: String, clientRef: ActorRef[HelloClient.ServerResponse]) extends Command
    
    // Server key to register in the receiptionist service so that the remote client can get the actor for the server 
    val HelloServerKey: ServiceKey[Command] = ServiceKey("HelloServer")

    def apply(): Behavior[Command] = {
        Behaviors.setup { context => 
            context.system.receptionist ! Receptionist.Register(HelloServerKey, context.self)
            new HelloServer(context) 
        }
    } 
}

// HelloServer Behavior to be used to construct the root user actor 
class HelloServer(context: ActorContext[HelloServer.Command]) extends AbstractBehavior(context) {
    import HelloServer._
    override def onMessage(msg: Command): Behavior[Command] = {
        msg match {
            case ClientRequest(name, ref) =>
                context.log.info("Received a message from: " + name)
                val response = s"Hello! $name"
                context.log.info("Sent back a message " + response)
                ref ! HelloClient.ServerResponse(response, context.self)
                this
        }
    }
}
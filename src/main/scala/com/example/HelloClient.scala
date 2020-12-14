// //#full-example
// package com.example


// import akka.actor.typed.ActorRef
// import akka.actor.typed.ActorSystem
// import akka.actor.typed.Behavior
// import akka.actor.typed.scaladsl.Behaviors
// import akka.actor.typed.scaladsl.AbstractBehavior
// import akka.actor.typed.scaladsl.ActorContext
// import akka.actor.typed.receptionist.Receptionist
// import com.typesafe.config.ConfigFactory

// // Client App Runner
// object ClientMain extends App {
//     // Load the default configuration from application.conf
//     val defaultConfig = ConfigFactory.load()
//     // Set the port that the client app should connect to 
//     val clientConfig = ConfigFactory.parseString("akka.remote.artery.canonical.port=6040").withFallback(defaultConfig)

//     val system = ActorSystem(HelloClient(), "HelloSystem", clientConfig)
//     // Look up for the server actor so that the client actor can fire a message to it 
//     system ! HelloClient.ServerLookup
// }

// object HelloClient {
//     sealed trait Command
//     // Response message from the server app 
//     final case class ServerResponse(message: String, ref: ActorRef[HelloServer.ClientRequest]) extends Command
//     // Response from the Receptionist after requesting for remote actor reference 
//     private case class ListingResponse(listing: Receptionist.Listing) extends Command
//     final case object ServerLookup extends Command
//     // Factory method for the client actor behavior 
//     def apply(name: String = "client"): Behavior[Command] = Behaviors.setup { context => new HelloClient(context, name) }
// }

// class HelloClient(context: ActorContext[HelloClient.Command], name: String) extends AbstractBehavior(context) {
//     import HelloClient._
//     override def onMessage(msg: Command): Behavior[Command] = {
//         msg match {
//             case ServerLookup => 
//                 context.log.info("Looking up for the server.")
//                 val adapter = context.messageAdapter[Receptionist.Listing](ListingResponse)
//                 context.system.receptionist ! Receptionist.Subscribe(HelloServer.HelloServerKey, adapter)
//                 this
//             case ServerResponse(message, _) =>
//                 context.log.info("Got a message from the server: " + message)
//                 this
//             case ListingResponse(HelloServer.HelloServerKey.Listing(listing)) =>
//                 context.log.info("Receptionist responded!")
//                 listing.foreach { serverRef =>
//                     context.log.info("Sending a message to server" + serverRef.path.name)
//                     serverRef ! HelloServer.ClientRequest(name, context.self)
//                 } 
//                 this
//         }
//     }
// }
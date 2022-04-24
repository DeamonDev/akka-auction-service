package io.scalac.auction

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}


object UserActor {
  trait Event
  case class UserEnrolledToAuction(auctionId: String) extends Event

  trait Command

  case class EnrollUserToAuction(auctionId: String) extends Command
  case class GetUserAuctions(replyTo: ActorRef[List[String]]) extends Command
  case class GetUser(replyTo: ActorRef[Option[User]]) extends Command
  case class Bid(auctionRef: ActorRef[AuctionActor.Command], lotId: String, offer: Int) extends Command

  case class State(userId: String, enrolledAuctions: List[String])

  def apply(userId: String): Behavior[Command] = Behaviors.setup { ctx =>

    EventSourcedBehavior[Command, Event, State](
      PersistenceId.ofUniqueId("user-actor-" + userId),
      emptyState = State(userId, List()),
      commandHandlerWithContext(ctx),
      eventHandler
    )
  }

  def commandHandlerWithContext(ctx: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, command) =>
      command match {
        case GetUser(replyTo) =>
          replyTo ! Some(User(state.userId, state.enrolledAuctions))
          Effect.unhandled

        case EnrollUserToAuction(auctionId) =>
          enrollUserToAuction(auctionId)

        case GetUserAuctions(replyTo) =>
          replyTo ! state.enrolledAuctions
          Effect.unhandled

        case Bid(auctionRef, lotId, offer) =>
          auctionRef ! AuctionActor.Bid(lotId, offer, state.userId)
          Effect.unhandled
      }
  }

  def eventHandler: (State, Event) => State = {
    (state, event) =>
      event match {
        case UserEnrolledToAuction(auctionId) =>
          State(state.userId, state.enrolledAuctions :+ auctionId)
      }
  }

  private def enrollUserToAuction(auctionId: String): Effect[Event, State] = {
    val evt = UserEnrolledToAuction(auctionId)
    Effect.persist(evt)
  }
}

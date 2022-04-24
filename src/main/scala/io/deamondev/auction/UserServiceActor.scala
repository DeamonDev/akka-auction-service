package io.scalac.auction

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

object UserServiceActor {
  import AuctionServiceActor._

  trait Event
  case class UserRegistered(userId: String) extends Event

  trait Command

  case class GetUser(userId: String, replyTo: ActorRef[Option[User]]) extends Command
  case class GetUsers(replyTo: ActorRef[List[User]]) extends Command
  case class GetUserIds(replyTo: ActorRef[List[String]]) extends Command
  case class RegisterUser(userId: String) extends Command
  case class Bid(auctionRef: ActorRef[AuctionActor.Command], lotId: String, offer: Int, userId: String) extends Command
  case class EnrollUserToAuction(userId: String, auctionId: String) extends Command

  case class State(userIds: List[String])

  def apply: Behavior[Command] = Behaviors.setup { ctx =>

    EventSourcedBehavior[Command, Event, State](
      PersistenceId.ofUniqueId("UserServiceActor"),
      emptyState = State(List()),
      commandHandlerWithContext(ctx),
      eventHandler
    )
  }

  def commandHandlerWithContext(ctx: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, command) =>
      command match {
        case GetUser(userId, replyTo) =>
          if(state.userIds.contains(userId)) {
            val userActorRef = getUserActorRefByUserId(ctx, userId)
            userActorRef ! UserActor.GetUser(replyTo)
            Effect.unhandled
          } else {
            replyTo ! None
            Effect.unhandled
          }
        case RegisterUser(userId) =>
          ctx.log.info(s"Registering User $userId")
          registerUser(ctx: ActorContext[Command], userId: String)
        case GetUserAuctions(userId, replyTo) =>
          val userActorRef = getUserActorRefByUserId(ctx, userId)
          userActorRef ! UserActor.GetUserAuctions(replyTo)
          Effect.unhandled
        case EnrollUserToAuction(userId, auctionId) =>
          val userActorRef = getUserActorRefByUserId(ctx, userId)
          userActorRef ! UserActor.EnrollUserToAuction(auctionId)
          Effect.unhandled
        case Bid(auctionRef, lotId, offer, userId) =>
          val userActorRef = getUserActorRefByUserId(ctx, userId)
          userActorRef ! UserActor.Bid(auctionRef, lotId, offer)
          Effect.unhandled
        case GetUserIds(replyTo) =>
          replyTo ! state.userIds

          Effect.unhandled

        case GetUserAuctions(userId, replyTo) =>
          val userActorRef = getUserActorRefByUserId(ctx, userId)
          userActorRef ! UserActor.GetUserAuctions(replyTo)

          Effect.unhandled
      }
  }

  def eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case UserRegistered(userId) =>
        State(state.userIds :+ userId)
    }
  }


  private def registerUser(ctx: ActorContext[UserServiceActor.Command], userId: String): Effect[Event, State] = {
    val userActorRef = ctx.spawn(UserActor(userId), "user-actor-" + userId)
    ctx.watch(userActorRef)

    val evt = UserRegistered(userId: String)
    Effect.persist(evt)
  }

  //aux methods
  def getUserActorRefByUserId(ctx: ActorContext[Command], userId: String): ActorRef[UserActor.Command] = {
    val uuid = java.util.UUID.randomUUID().toString

    val userActorRef: ActorRef[UserActor.Command] =
      ctx.child(userId)
        .getOrElse(ctx.spawn(UserActor(userId), "undefined-user-actor-" + userId + "-" + uuid))
        .asInstanceOf[ActorRef[UserActor.Command]]

    userActorRef
  }
}

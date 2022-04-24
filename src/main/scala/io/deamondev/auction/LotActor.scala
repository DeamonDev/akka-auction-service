package io.scalac.auction

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

object LotActor {
  trait Event
  case class UpdatedWinningBid(currentlyWinningBid: Bid) extends Event

  trait Command

  case class Bid(offer: Int, userId: String) extends Command
  case class GetCurrentlyWinningBid(replyTo: ActorRef[Bid]) extends Command
  case class GetCurrentlyWinningOffer(replyTo: ActorRef[Int]) extends Command
  case class GetBiddingHistory(replyTo: ActorRef[List[Bid]]) extends Command

  final case class State(winningBid: Bid)

  def apply(lotId: String): Behavior[Command] = Behaviors.setup { ctx =>

    EventSourcedBehavior[Command, Event, State](
      PersistenceId.ofUniqueId("lot-actor-" + lotId),
      emptyState = State(Bid(0, "")),
      commandHandlerWithContext(ctx),
      eventHandlerWithContext(ctx)
    )
  }

  def commandHandlerWithContext(ctx: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, command) =>
      command match {
        case bid @ Bid(offer, userId) =>
          if (state.winningBid.offer < offer) {
            ctx.log.info2("{} you did that! Currently winning offer is {}", userId, offer)
            setNewWinningBid(bid)
          } else {
            ctx.log.info2("{} you have to offer more than {}", userId, state.winningBid.offer)
            Effect.unhandled
          }
        case GetCurrentlyWinningOffer(replyTo) =>
          replyTo ! state.winningBid.offer
          Effect.unhandled
        case GetCurrentlyWinningBid(replyTo) =>
          replyTo ! state.winningBid
          Effect.unhandled
      }
  }


  def eventHandlerWithContext(ctx: ActorContext[Command]): (State, Event) => State = {
    (_, event) =>
      event match {
        case UpdatedWinningBid(bid) =>
          State(bid)
      }
  }

  private def setNewWinningBid(bid: Bid): Effect[Event, State] = {
    val evt = UpdatedWinningBid(bid)
    Effect.persist(evt)
  }
}
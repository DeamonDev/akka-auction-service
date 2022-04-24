package io.scalac.auction

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}

case class Lot(lotId: String)

object AuctionActor {
  trait Event
  final case class AuctionInitialized(lots: List[Lot], lotActorIds: List[String]) extends Event
  final case class ItemAdded(lot: Lot) extends Event
  final case class ItemRemoved(lotId: String) extends Event
  final case class StatusChanged(newAuctionStatus: Status) extends Event

  trait Command

  final case class InitializeAuction(lots: List[Lot]) extends Command
  final case class NumberOfLots(replyTo: ActorRef[Int]) extends Command
  final case class GetCurrentlyWinningBid(lotId: String, replyTo: ActorRef[LotActor.Bid]) extends Command
  final case class GetCurrentlyWinningOffer(lotId: String, replyTo: ActorRef[Int]) extends Command
  final case class Bid(lotId: String, amount: Int, userId: String)
    extends AuctionActor.Command
  final case class AddLot(lot: Lot) extends AuctionActor.Command
  final case class AddLots(lots: List[Lot]) extends AuctionActor.Command
  final case class RemoveLot(lotId: String) extends AuctionActor.Command
  final case class RemoveLots(lotsIds: List[String]) extends AuctionActor.Command
  final case class GetAllLots(replyTo: ActorRef[List[Lot]]) extends AuctionActor.Command
  final case class GetBiddingHistory(lotId: String, replyTo: ActorRef[List[LotActor.Bid]]) extends Command

  trait Status
  final case object Closed extends Status {
    override def toString: String = "Closed"
  }
  final case object InProgress extends Status {
    override def toString: String = "InProgress"
  }
  final case object Finished extends Status {
    override def toString: String = "Finished"
  }

  final case class ChangeStatusTo(state: Status) extends Command
  final case class GetInfo(replyTo: ActorRef[Auction]) extends Command


  case class State(auctionId: String, lots: List[Lot], lotActorIds: List[String], auctionStatus: Status)


  def apply(auctionId: String): Behavior[Command] = Behaviors.setup { ctx =>

    EventSourcedBehavior[Command, Event, State](
      PersistenceId.ofUniqueId("auction-actor-" + auctionId),
      emptyState = State(auctionId, List(), List(), Closed),
      commandHandlerWithContext(ctx),
      eventHandler
    )
  }

  def commandHandlerWithContext(ctx: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, command) =>
      state.auctionStatus match {
        case Closed =>
          command match {
            case InitializeAuction(lots) =>
              initializeAuction(ctx, state.auctionId, lots)
            case Bid(_, _, _) =>
              ctx.log.info("You cannot bid while auction is closed")
              Effect.unhandled
            case GetInfo(replyTo) =>
              replyTo ! Auction(state.auctionId, state.lots, state.auctionStatus.toString)
              Effect.unhandled
            case AddLot(lot) =>
              state.lots.find(_.lotId == lot.lotId) match {
                case None =>
                  ctx.log.info2("Adding {} to auction {}", lot, state.auctionId)
                  addLot(ctx, state.auctionId, lot)
                case Some(_) =>
                  ctx.log.info("{} is in the auction already", lot)
                  Effect.unhandled
              }
              Effect.unhandled
            case RemoveLot(lotId) =>
              state.lots.find(_.lotId == lotId) match {
                case Some(_) =>
                  ctx.log.info("Removed lot with lot id {}", lotId)
                  removeLot(ctx, state.auctionId, lotId)
                case None =>
                  ctx.log.info("Lot with {} not found", lotId)
                  Effect.unhandled
              }
            case GetCurrentlyWinningBid(lotId, replyTo) =>
              val lotActorRef = getLotActorRefByLotId(ctx, state.auctionId, lotId)
              lotActorRef ! LotActor.GetCurrentlyWinningBid(replyTo)
              Effect.unhandled

            case GetCurrentlyWinningOffer(lotId, replyTo) =>
              val lotActorRef = getLotActorRefByLotId(ctx, state.auctionId, lotId)
              lotActorRef ! LotActor.GetCurrentlyWinningOffer(replyTo)
              Effect.unhandled

            case ChangeStatusTo(newAuctionStatus) =>
              changeStatusTo(newAuctionStatus)

            case GetAllLots(replyTo) =>
              replyTo ! state.lots
              Effect.unhandled
          }
        case InProgress =>
          command match {
            case InitializeAuction(_) =>
              ctx.log.info("Cannot initialize auction since auction is InProgress")
              Effect.unhandled
            case Bid(lotId, amount, userId) =>
              val lotActorRef = getLotActorRefByLotId(ctx, state.auctionId, lotId)
              lotActorRef ! LotActor.Bid(amount, userId)
              Effect.unhandled
            case GetInfo(replyTo) =>
              replyTo ! Auction(state.auctionId, state.lots, state.auctionStatus.toString)
              Effect.unhandled
            case AddLot(_) =>
              ctx.log.info("Cannot add lot since auction is InProgress")
              Effect.unhandled
            case RemoveLot(_) =>
              ctx.log.info("Cannot remove lot since auction is InProgress")
              Effect.unhandled
            case GetCurrentlyWinningBid(lotId, replyTo) =>
              val lotActorRef = getLotActorRefByLotId(ctx,state.auctionId, lotId)
              lotActorRef ! LotActor.GetCurrentlyWinningBid(replyTo)
              Effect.unhandled

            case GetCurrentlyWinningOffer(lotId, replyTo) =>
              val lotActorRef = getLotActorRefByLotId(ctx, state.auctionId, lotId)
              lotActorRef ! LotActor.GetCurrentlyWinningOffer(replyTo)
              Effect.unhandled

            case ChangeStatusTo(newAuctionStatus) =>
              changeStatusTo(newAuctionStatus)

            case GetAllLots(replyTo) =>
              replyTo ! state.lots
              Effect.unhandled

          }
        case Finished =>
          command match {
            case InitializeAuction(_) =>
              ctx.log.info("Cannot initialize auction since auction is Finished")
              Effect.unhandled
            case Bid(_, _, _) =>
              ctx.log.info("You cannot bid while auction is finished")
              Effect.unhandled
            case GetInfo(replyTo) =>
              replyTo ! Auction(state.auctionId, state.lots, state.auctionStatus.toString)
              Effect.unhandled
            case AddLot(_) =>
              ctx.log.info("Cannot add lot since auction is Finished")
              Effect.unhandled
            case RemoveLot(_) =>
              ctx.log.info("Cannot remove lot since auction is Finished")
              Effect.unhandled
            case GetCurrentlyWinningBid(lotId, replyTo) =>
              val lotActorRef = getLotActorRefByLotId(ctx, state.auctionId, lotId)
              lotActorRef ! LotActor.GetCurrentlyWinningBid(replyTo)
              Effect.unhandled

            case GetCurrentlyWinningOffer(lotId, replyTo) =>
              val lotActorRef = getLotActorRefByLotId(ctx, state.auctionId, lotId)
              lotActorRef ! LotActor.GetCurrentlyWinningOffer(replyTo)
              Effect.unhandled

            case ChangeStatusTo(_) =>
              ctx.log.info("You cannot change auction status since it is Finished")
              Effect.unhandled

            case GetAllLots(replyTo) =>
              replyTo ! state.lots
              Effect.unhandled
          }
      }
  }

  def eventHandler: (State, Event) => State = { (state, event) =>
    event match {
      case AuctionInitialized(lots, lotActorIds) =>
        State(state.auctionId, lots, lotActorIds, Closed)
      case ItemAdded(lot) =>
        State(state.auctionId, state.lots :+ lot, state.lotActorIds, state.auctionStatus)
      case ItemRemoved(lotId) =>
        val lotActorIdToRemove = state.auctionId + "-" + lotId
        State(state.auctionId, state.lots.filter(_.lotId != lotId), state.lotActorIds.filter(_ != lotActorIdToRemove), state.auctionStatus)
      case StatusChanged(newAuctionStatus) =>
        State(state.auctionId, state.lots, state.lotActorIds, newAuctionStatus)
    }
  }


  // Methods

  private def initializeAuction(ctx: ActorContext[Command], auctionId: String, lots: List[Lot]): Effect[Event, State] = {
    lots.foreach { lot =>
      val lotActorRef = ctx.spawn(LotActor(lot.lotId), auctionId + "-" + lot.lotId)
      ctx.watch(lotActorRef)
    }

    ctx.log.info(s"Initialized $auctionId with $lots")

    val evt = AuctionInitialized(lots, lots.map(lot => auctionId + "-" + lot.lotId))
    Effect.persist(evt)
  }

  private def addLot(ctx: ActorContext[AuctionActor.Command], auctionId: String, lot: Lot): Effect[Event, State] = {
    val lotActorRef = ctx.spawn(LotActor(lot.lotId), "lot-actor-" + lot.lotId)
    ctx.watch(lotActorRef)

    val evt = ItemAdded(lot)
    Effect.persist(evt)
  }

  private def removeLot(ctx: ActorContext[AuctionActor.Command], auctionId: String, lotId: String): Effect[Event, State]  = {
    val lotActorRef = getLotActorRefByLotId(ctx, auctionId, lotId)
    ctx.stop(lotActorRef)

    val evt = ItemRemoved(lotId)
    Effect.persist(evt)
  }

  private def changeStatusTo(newAuctionStatus: AuctionActor.Status): Effect[Event, State] = {
    val evt = StatusChanged(newAuctionStatus)
    Effect.persist(evt)
  }


  // aux methods
  def getLotActorRefByLotId(ctx: ActorContext[Command], auctionId: String, lotId: String): ActorRef[LotActor.Command] = {
    val uuid = java.util.UUID.randomUUID().toString
    val lotActorRef: ActorRef[LotActor.Command] =
      ctx.child(auctionId + "-" + lotId)
        .getOrElse(ctx.spawn(LotActor(lotId), "lot-actor-" + lotId + "-" + uuid))
        .asInstanceOf[ActorRef[LotActor.Command]]

    lotActorRef
  }
}
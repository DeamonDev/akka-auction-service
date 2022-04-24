package io.scalac.auction

import AuctionActor._
import akka.actor.Cancellable
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.{ActorContext, Behaviors, LoggerOps}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}


object AuctionServiceActor {
  trait Event
  case class LotCreated(lotId: String, onAuction: Boolean) extends Event
  case class AuctionCreated(auctionId: String, auctionActorId: String, lots: List[Lot]) extends Event
  case class AuctionStarted(auctionId: String) extends Event
  case class AuctionFinished(auctionId: String) extends Event
  case class AuctionEdited(auctionId: String, newLots: List[Lot], removedLotIds: List[String]) extends Event
  case object UserServiceInitialized extends Event
  case object TransactionsListenerInitialized extends Event

  trait Command

  case class Bid(auctionId: String, lotId: String, offer: Int, userId: String) extends Command
  case class CreateAuction(auctionId: String, lots: List[Lot]) extends Command
  case class GetAuction(auctionId: String, replyTo: ActorRef[Auction]) extends Command
  case class GetAllAuctionIds(replyTo: ActorRef[List[String]]) extends Command
  case class EditAuction(auctionId: String, lotsToAdd: List[Lot], lotsIdsToRemove: List[String]) extends Command
  case class StartAuction(auctionId: String) extends Command
  case class FinishAuction(auctionId: String) extends Command
  case class GetNumberOfAuctions(replyTo: ActorRef[Int]) extends Command
  case class CreateLot(lotId: String, onAuction: Boolean = false) extends Command
  case class CreateLots(lotIds: List[String]) extends Command
  case class GetLot(lotId: String, replyTo: ActorRef[Option[Lot]]) extends Command
  case class GetAllLots(replyTo: ActorRef[List[(Lot, String)]]) extends Command
  case class GetAllLots_v2(replyTo: ActorRef[List[(Lot, String)]]) extends Command
  case class GetAllLotsById(auctionId: String, replyTo: ActorRef[List[Lot]]) extends Command
  case class GetCurrentlyWinningOffer(auctionId: String, lotId: String, replyTo: ActorRef[Int]) extends Command
  case class GetCurrentlyWinningBid(auctionId: String, lotId: String, replyTo: ActorRef[LotActor.Bid]) extends Command
  case class GetBiddingHistory(auctionId: String, lotId: String, replyTo: ActorRef[List[LotActor.Bid]]) extends Command


  // User Service Commands
  case object InitializeUserService extends Command
  case class RegisterUser(userId: String) extends Command
  case class GetUser(userId: String, replyTo: ActorRef[Option[User]]) extends Command
  case class GetUsers(replyTo: ActorRef[List[User]]) extends Command
  case class GetUserAuctions(userId: String, replyTo: ActorRef[List[String]]) extends Command with UserServiceActor.Command
  case class GetUserIds(replyTo: ActorRef[List[String]]) extends Command with UserServiceActor.Command
  case class EnrollUserToAuction(userId: String, auctionId: String) extends Command with UserServiceActor.Command
  case class Log(logMessage: String) extends Command
  case class AddCurrentTransaction(transactionId: String, cancellable: Cancellable) extends Command
  case class StopTransaction(transactionId: String) extends Command

  // Transactions Listener Command
  case object InitializeTransactionsListener extends Command


  case class State(auctionIds: List[String],
                   auctionActorIds: List[String],
                   currentLots: Map[Lot, String], // lot -> auctionId
                   userServiceIdOption: Option[String],
                   transactionsListenerIdOption: Option[String]
                  )


  def apply(): Behavior[Command] = Behaviors.setup { ctx =>

    EventSourcedBehavior[Command, Event, State](
      PersistenceId.ofUniqueId("AuctionServiceActor"),
      emptyState = State(List(), List(), Map(), None, None),
      commandHandlerWithContext(ctx),
      eventHandler
    )
  }

  def commandHandlerWithContext(ctx: ActorContext[Command]): (State, Command) => Effect[Event, State] = {
    (state, command) =>

      command match {
        case CreateLot(lotId, onAuction) =>
          createLot(lotId, onAuction)

        case GetLot(lotId, replyTo) =>
          if (state.currentLots.contains(Lot(lotId))) {
            replyTo ! Some(Lot(lotId))
          } else {
            replyTo ! None
          }

          Effect.unhandled

        case CreateAuction(auctionId, lots) =>
          createAuction(ctx, state.currentLots.keySet.toList, auctionId, lots)

        case EditAuction(auctionId, lotsToAdd, lotIdsToRemove) =>
          editAuction(ctx, state.currentLots.keySet.toList, auctionId, lotsToAdd, lotIdsToRemove)

        case InitializeUserService =>
          initializeUserService(ctx)

        case InitializeTransactionsListener =>
          initializeTransactionsListener(ctx)

        case GetAuction(auctionId, replyTo) =>
          val auctionActorRef = getActorRefByAuctionId(ctx, auctionId)
          auctionActorRef ! AuctionActor.GetInfo(replyTo)
          Effect.unhandled

        case GetAllAuctionIds(replyTo) =>
          replyTo ! state.auctionIds
          Effect.unhandled

        case StartAuction(auctionId) =>
          startAuction(ctx, auctionId)

        case FinishAuction(auctionId) =>
          finishAuction(ctx, auctionId)

        case GetAllLots(replyTo) =>
          replyTo ! state.currentLots.toList
          Effect.unhandled

        case GetAllLots_v2(replyTo) =>
          replyTo ! state.currentLots.toList
          Effect.unhandled

        case RegisterUser(userId) =>
          val userServiceActorRef = getUserService(ctx)
          userServiceActorRef ! UserServiceActor.RegisterUser(userId)
          Effect.unhandled

        case GetUser(userId, replyTo) =>
          val userServiceActorRef = getUserService(ctx)
          userServiceActorRef ! UserServiceActor.GetUser(userId, replyTo)

          Effect.unhandled

        case GetUsers(replyTo) =>
          val userServiceActorRef = getUserService(ctx)
          userServiceActorRef ! UserServiceActor.GetUsers(replyTo)

          Effect.unhandled

        case GetUserIds(replyTo) =>
          val userServiceActorRef = getUserService(ctx)
          userServiceActorRef ! UserServiceActor.GetUserIds(replyTo)

          Effect.unhandled

        case GetUserAuctions(userId, replyTo) =>
          val userServiceActorRef = getUserService(ctx)
          userServiceActorRef ! GetUserAuctions(userId, replyTo)

          Effect.unhandled

        case EnrollUserToAuction(userId, auctionId) =>
          val userServiceActorRef = getUserService(ctx)
          userServiceActorRef ! UserServiceActor.EnrollUserToAuction(userId, auctionId)

          Effect.unhandled

        case Bid(auctionId, lotId, offer, userId) =>
          val userServiceActorRef = getUserService(ctx)
          val auctionActorRef = getActorRefByAuctionId(ctx, auctionId)
          userServiceActorRef ! UserServiceActor.Bid(auctionActorRef, lotId, offer, userId)

          Effect.unhandled

        case GetCurrentlyWinningOffer(auctionId, lotId, replyTo) =>
          val auctionActorRef = getActorRefByAuctionId(ctx, auctionId)
          auctionActorRef ! AuctionActor.GetCurrentlyWinningOffer(lotId, replyTo)

          Effect.unhandled

        case GetCurrentlyWinningBid(auctionId, lotId, replyTo) =>
          val auctionActorRef = getActorRefByAuctionId(ctx, auctionId)
          auctionActorRef ! AuctionActor.GetCurrentlyWinningBid(lotId, replyTo)

          Effect.unhandled

        case AddCurrentTransaction(transactionId, cancellable) =>
          val transactionsListenerActorRef = getTransactionsListener(ctx)
          transactionsListenerActorRef ! TransactionsListenerActor.AddCurrentTransaction(transactionId, cancellable)

          Effect.unhandled

        case StopTransaction(transactionId) =>
          val transactionsListenerActorRef = getTransactionsListener(ctx)
          transactionsListenerActorRef ! TransactionsListenerActor.DeleteCurrentTransaction(transactionId)

          Effect.unhandled
      }
  }

  def eventHandler: (State, Event) => State = { (state, event) =>
    event match {

      case AuctionCreated(auctionId, auctionActorId, lots) =>
        State(
          state.auctionIds :+ auctionId,
          state.auctionActorIds :+ auctionActorId,
          state.currentLots ++ lots.map(lot => lot -> auctionId),
          state.userServiceIdOption,
          state.transactionsListenerIdOption
        )

      case AuctionEdited(auctionId, newLots, removedLotIds) =>
        State(
          state.auctionIds,
          state.auctionActorIds,
          (state.currentLots ++ newLots.map(lot => lot -> auctionId)).filter {
            case (lot, _) => !removedLotIds.contains(lot.lotId)
          },
          state.userServiceIdOption,
          state.transactionsListenerIdOption
        )

      case UserServiceInitialized =>
        State(
          state.auctionIds,
          state.auctionActorIds,
          state.currentLots,
          Some("userService"),
          state.transactionsListenerIdOption
        )

      case TransactionsListenerInitialized =>
        State(
          state.auctionIds,
          state.auctionActorIds,
          state.currentLots,
          state.userServiceIdOption,
          Some("transactionsListener")
        )

      case AuctionStarted(auctionId) =>
        state

      case AuctionFinished(auctionId) =>
        state
    }
  }

  // methods
  def createLot(lotId: String, onAuction: Boolean): Effect[Event, State] = {
    val evt = LotCreated(lotId, onAuction)
    Effect.persist(evt)
  }

  def createAuction(ctx: ActorContext[Command], currentLots: List[Lot], auctionId: String, lots: List[Lot]): Effect[Event, State] = {
    val filteredLots = filterLots(currentLots, lots)

    val auctionActorRef = ctx.spawn(AuctionActor(auctionId), "auction-actor-" + auctionId)
    ctx.watch(auctionActorRef)
    auctionActorRef ! AuctionActor.InitializeAuction(filteredLots)

    val evt = AuctionCreated(auctionId, "auction-actor-" + auctionId, filteredLots)
    Effect.persist(evt)
  }

  def editAuction(
                   ctx: ActorContext[AuctionServiceActor.Command],
                   currentLots: List[Lot],
                   auctionId: String,
                   lotsToAdd: List[Lot],
                   lotIdsToRemove: List[String]): Effect[Event, State] = {

    val filteredLotsToAdd = filterLots(currentLots, lotsToAdd)

    val auctionActorRef = getActorRefByAuctionId(ctx, auctionId)
    filteredLotsToAdd.foreach { lot =>
      ctx.log.info(s"Adding $lot to the auction $auctionId")
      auctionActorRef ! AuctionActor.AddLot(lot)
    }

    lotIdsToRemove.foreach {lotId =>
      ctx.log.info(s"Removing Lot($lotId) from auction $auctionId")
      auctionActorRef ! AuctionActor.RemoveLot(lotId)
    }

    val evt = AuctionEdited(auctionId, filteredLotsToAdd, lotIdsToRemove)
    Effect.persist(evt)
  }

  private def filterLots(currentLots: List[Lot], newLots: List[Lot]): List[Lot] =
    newLots.filter(lot => !currentLots.contains(lot))


  def startAuction(ctx: ActorContext[Command], auctionId: String): Effect[Event, State] = {
    val auctionActorRef = getActorRefByAuctionId(ctx, auctionId)
    auctionActorRef ! AuctionActor.ChangeStatusTo(AuctionActor.InProgress)

    val evt = AuctionStarted(auctionId)
    Effect.persist(evt)
  }

  def finishAuction(ctx: ActorContext[Command], auctionId: String): Effect[Event, State] = {
    val auctionActorRef = getActorRefByAuctionId(ctx, auctionId)
    auctionActorRef ! AuctionActor.ChangeStatusTo(AuctionActor.Finished)

    val evt = AuctionFinished(auctionId)
    Effect.persist(evt)
  }

  def initializeUserService(ctx: ActorContext[Command]): Effect[Event, State] = {
    ctx.log.info("Initializing user service...")
    val userServiceActorRef = ctx.spawn(UserServiceActor.apply, "userService")
    ctx.watch(userServiceActorRef)

    val evt = UserServiceInitialized
    Effect.persist(evt)
  }

  def initializeTransactionsListener(ctx: ActorContext[Command]): Effect[Event, State] = {
    ctx.log.info("Initializing transactions listener...")
    val transactionsListenerActorRef = ctx.spawn(TransactionsListenerActor.apply, "transactionsListener")
    ctx.watch(transactionsListenerActorRef)

    val evt = TransactionsListenerInitialized
    Effect.persist(evt)
  }


  // aux methods
  def getActorRefByAuctionId(ctx: ActorContext[Command], auctionId: String): ActorRef[AuctionActor.Command] = {
    val uuid = java.util.UUID.randomUUID().toString

    val auctionActorRef: ActorRef[AuctionActor.Command] =
      ctx.child(auctionId)
        .getOrElse(ctx.spawn(AuctionActor(auctionId), "auction-actor-" + auctionId + "-" + uuid))
        .asInstanceOf[ActorRef[AuctionActor.Command]]

    auctionActorRef
  }

  def getUserService(ctx: ActorContext[Command]): ActorRef[UserServiceActor.Command] = {
    val uuid = java.util.UUID.randomUUID().toString

    val userServiceActorRef: ActorRef[UserServiceActor.Command] =
      ctx.child("userService")
        .getOrElse(ctx.spawn(UserServiceActor.apply, "user-service-" + uuid))
        .asInstanceOf[ActorRef[UserServiceActor.Command]]

    userServiceActorRef
  }

  def getTransactionsListener(ctx: ActorContext[Command]): ActorRef[TransactionsListenerActor.Command] = {
    val uuid = java.util.UUID.randomUUID().toString

    val transactionsListenerActorRef: ActorRef[TransactionsListenerActor.Command] =
      ctx.child("transactionsListener")
        .getOrElse(ctx.spawn(TransactionsListenerActor.apply, "transactions-listener-" + uuid))
        .asInstanceOf[ActorRef[TransactionsListenerActor.Command]]

    transactionsListenerActorRef
  }
}
package io.scalac.auction

import akka.NotUsed
import akka.actor.Cancellable
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import io.scalac.auction.AuctionServiceActor.{CreateAuction, CreateLot, EditAuction, FinishAuction, GetAllLots, GetAllLotsById, GetAllLots_v2, GetAuction, GetLot, InitializeTransactionsListener, InitializeUserService, StartAuction}
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor, Future}

object AuctionServiceApp extends AuctionService with AuctionAppJsonProtocol {

  implicit val system: ActorSystem[AuctionServiceActor.Command] =
    ActorSystem(
      AuctionServiceActor.apply,
      "service",
      ConfigFactory.load().getConfig("auctionService")
    )
  implicit val executionContext: ExecutionContextExecutor = system.executionContext

  val auctionServiceActor: ActorRef[AuctionServiceActor.Command] = system

  override def getLotById(lotId: String): Future[Option[Lot]] = {
    implicit val timeout: Timeout = 5.seconds

    auctionServiceActor.ask { ref =>
      GetLot(lotId, ref)
    }.mapTo[Option[Lot]]
  }

  override def getLotsByAuctionId(auctionId: String): Future[List[Lot]] = {
    implicit val timeout: Timeout = 60.seconds

    auctionServiceActor.ask { ref =>
      GetAllLotsById(auctionId, ref)
    }.mapTo[List[Lot]]
  }


  override def getLots: Future[List[(Lot, String)]] = {
    implicit val timeout: Timeout = 60.seconds

     auctionServiceActor.ask { ref =>
      GetAllLots(ref)
    }.mapTo[List[(Lot, String)]]
  }

  // (Lot, String) <---> Lot + info about in which auction it lives
  def getLots_v2: Future[List[(Lot, String)]] = {
    implicit val timeout: Timeout = 5.seconds

    auctionServiceActor.ask { ref =>
      GetAllLots_v2(ref)
    }.mapTo[List[(Lot, String)]]
  }

  override def checkLot(lotId: String): Future[Option[Lot]] = {
    implicit val timeout: Timeout = 3.seconds

    auctionServiceActor.ask {
      ref => GetLot(lotId, ref)
    }.mapTo[Option[Lot]]
  }

  override def createLot(lotId: String): Unit = {
    auctionServiceActor ! CreateLot(lotId)
  }

  override def createLots(lotIds: List[String]): Unit = {
    lotIds.foreach(lot => createLot(lot))
  }

  override def createAuction(auctionId: String, lots: List[Lot]): Unit = {
    auctionServiceActor ! CreateAuction(auctionId, lots)
  }

  override def startAuction(auctionId: String): Unit = auctionServiceActor ! StartAuction(auctionId)

  override def endAuction(auctionId: String): Unit = auctionServiceActor ! FinishAuction(auctionId)

  def getAuction(auctionId: String): Future[Auction] = {
    implicit val timeout: Timeout = 60.seconds

    auctionServiceActor.ask { ref =>
      AuctionServiceActor.GetAuction(auctionId, ref)
    }.mapTo[Auction]
  }

  def getAuctionIds: Future[List[String]] = {
    implicit val timeout: Timeout = 3.seconds

    auctionServiceActor.ask {ref =>
      AuctionServiceActor.GetAllAuctionIds(ref)
    }.mapTo[List[String]]
  }

  override def getAuctions: Future[List[Auction]] = {
    implicit val timeout: Timeout = 30.seconds

    val auctionIds = Await.result(getAuctionIds, 5.seconds)
    val listOfFutures = auctionIds.map(auctionId => getAuction(auctionId))

    val futureList = Future.sequence(listOfFutures)

    futureList
  }

  def getAuctionStatus(auctionId: String): Future[String] = getAuction(auctionId).map(_.state )

  override def editAuction(auctionId: String, newLots: List[Lot], lotsIdToRemove: List[String]): Unit =
    auctionServiceActor ! EditAuction(auctionId, newLots, lotsIdToRemove)


  def initializeUserService(): Unit = auctionServiceActor ! InitializeUserService

  def initializeTransactionsListener(): Unit = auctionServiceActor ! InitializeTransactionsListener

  def getUser(userId: String): Future[Option[User]] = {
    implicit val timeout: Timeout = 60.seconds

    auctionServiceActor.ask { ref =>
      AuctionServiceActor.GetUser(userId, ref)
    }.mapTo[Option[User]]
  }

  def getUserIds: Future[List[String]] = {
    implicit val timeout: Timeout = 3.seconds

    auctionServiceActor.ask {ref =>
      AuctionServiceActor.GetUserIds(ref)
    }.mapTo[List[String]]
  }

  def getUsers_v2: Future[List[Option[User]]] = { // not so nice, since uses Await...
    implicit val timeout: Timeout = 30.seconds

    val userIds = Await.result(getUserIds, 5.seconds)
    val listOfUsers = userIds.map(userId => getUser(userId))

    val futureList = Future.sequence(listOfUsers)

    futureList
  }

  def registerUser(userId: String): Unit = auctionServiceActor ! AuctionServiceActor.RegisterUser(userId)

  def getUserAuctions(userId: String): Future[List[String]] = {
    implicit val timeout: Timeout = 3.seconds

    auctionServiceActor.ask { ref =>
      AuctionServiceActor.GetUserAuctions(userId, ref)
    }.mapTo[List[String]]
  }

  def enrollUserToAuction(userId: String, auctionId: String): Unit =
    auctionServiceActor ! AuctionServiceActor.EnrollUserToAuction(userId, auctionId)

  def checkWhetherUserIsEnrolledOnAuction(userId: String, auctionId: String): Future[Boolean] =
    getUserAuctions(userId).map(auctions => auctions.contains(auctionId))

  def checkWhetherAuctionIsInProgress(auctionId: String): Future[Boolean] ={
    implicit val timeout: Timeout = 3.seconds

    auctionServiceActor.ask { ref =>
      AuctionServiceActor.GetAuction(auctionId, ref)
    }.mapTo[Auction].map(auction => auction.state == "InProgress")
  }

  def resolveCurrentOffer(auctionId: String, lotId: String, maxBid: Option[Int]): Future[Option[Int]] = {
    implicit val timeout: Timeout = 3.seconds

    auctionServiceActor.ask { ref =>
      AuctionServiceActor.GetCurrentlyWinningOffer(auctionId, lotId, ref)
    }.mapTo[Int].map { currentlyWinningOffer =>
      maxBid match {
        case None => Some(currentlyWinningOffer + 1)
        case Some(max) => if (currentlyWinningOffer >= max) None else Some(currentlyWinningOffer + 1)
      }
    }
  }

  def startPlaying(auctionId: String, lotId: String, userId: String, maxBid: Option[Int]): Unit = {
    val transactionId: String = auctionId + "#" + lotId + "#" + userId
    val transaction = system.scheduler.scheduleAtFixedRate(Duration.Zero, 1.seconds) (new Runnable {
      override def run(): Unit = {
        val maybeCurrentOffer = Await.result(resolveCurrentOffer(auctionId, lotId, maxBid), 5.seconds)
        val auctionStatus = Await.result(getAuctionStatus(auctionId), 5.seconds)

        auctionStatus match {
          case "InProgress" =>
            maybeCurrentOffer match {
              case Some(currentOffer) =>
                auctionServiceActor ! AuctionServiceActor.Bid(auctionId, lotId, currentOffer, userId)
              case None =>
                auctionServiceActor ! AuctionServiceActor.StopTransaction(transactionId)
            }
          case _ =>
            auctionServiceActor ! AuctionServiceActor.StopTransaction(transactionId)
        }
      }
    })

    auctionServiceActor ! AuctionServiceActor.AddCurrentTransaction(transactionId, transaction)
  }

  def getBiddingHistory(auctionId: String, lotId: String): Future[List[LotActor.Bid]] = {
    implicit val timeout: Timeout = 3.seconds

     auctionServiceActor.ask { ref =>
      AuctionServiceActor.GetBiddingHistory(auctionId, lotId, ref)
    }.mapTo[List[LotActor.Bid]]
  }

  def getCurrentlyWinningBid(auctionId: String, lotId: String): Future[LotActor.Bid] = {
    implicit val timeout: Timeout = 3.seconds

    auctionServiceActor.ask { ref =>
      AuctionServiceActor.GetCurrentlyWinningBid(auctionId, lotId, ref)
    }.mapTo[LotActor.Bid]

  }
}
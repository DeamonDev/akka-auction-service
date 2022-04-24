package io.scalac

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext

import scala.collection.convert.StreamExtensions.StreamShape

package object auction {
  case class Auction(auctionId: String, lots: List[Lot], state: String) {
    def addAndRemoveLots(lotsToAdd: List[Lot], lotIdsToRemove: List[String]): Auction = {
      val newLots = (lots.filter(lot => !lotIdsToRemove.contains(lot.lotId))) ::: lotsToAdd

      Auction(auctionId, newLots, state)
    }

    def changeState(newState: String): Auction = {
      Auction(auctionId, lots, newState)
    }

  }

  case class User(userId: String, enrolledAuctions: List[String])

  case class EditAuctionRequest(auctionId: String, lotsToAdd: List[Lot], lotIdsToRemove: List[String])
  case class CreateAuctionRequest(auctionId: String, lots: List[Lot])
  case class StartAuctionRequest(auctionId: String)
  case class EndAuctionRequest(auctionId: String)
  case class CreateLotRequest(lotId: String)
  case class CreateLotsRequest(lotIds: List[String])
  case class BiddingHistoryRequest(history: List[LotActor.Bid])

  case class RegisterUserRequest(userId: String)
  case class EnrollUserOnAuctionRequest(userId: String, auctionId: String)
  case class StartBiddingRequest(auctionId: String, lotId: String, userId: String, maxBid: Option[Int])

}

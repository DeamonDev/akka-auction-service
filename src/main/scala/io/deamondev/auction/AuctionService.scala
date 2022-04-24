package io.scalac.auction

import akka.actor.typed.ActorRef

import scala.concurrent.Future

trait AuctionService {

  def checkLot(lotId: String): Future[Option[Lot]]

  def createLot(lotId: String): Unit

  def createLots(lotIds: List[String]): Unit

  def createAuction(auctionId: String, lots: List[Lot]): Unit

  def getAuctions: Future[List[Auction]]

  def getLots: Future[List[(Lot, String)]]

  def getLotsByAuctionId(auctionId: String): Future[List[Lot]]

  def getLotById(lotId: String): Future[Option[Lot]]

  def editAuction(auctionId: String, newLots: List[Lot], lotsIdToRemove: List[String]): Unit

  def startAuction(auctionId: String): Unit

  def endAuction(auctionId: String): Unit

  //other state transitions
}


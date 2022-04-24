package io.scalac.auction

import spray.json.DefaultJsonProtocol
import spray.json.DefaultJsonProtocol.{jsonFormat1, jsonFormat2, jsonFormat3, jsonFormat4}

trait AuctionAppJsonProtocol extends DefaultJsonProtocol {

  implicit val lotFormat = jsonFormat1(Lot)
  implicit val actionFormat = jsonFormat3(Auction)
  implicit val editAuctionFormat = jsonFormat3(EditAuctionRequest)
  implicit val auctionRequestFormat = jsonFormat2(CreateAuctionRequest)
  implicit val startAuctionRequestFormat = jsonFormat1(StartAuctionRequest)
  implicit val endAuctionRequestFormat = jsonFormat1(EndAuctionRequest)
  implicit val createLotRequest = jsonFormat1(CreateLotRequest)
  implicit val createLotsRequest = jsonFormat1(CreateLotsRequest)
  implicit val BidRequest = jsonFormat2(LotActor.Bid)
  implicit val lotBiddingHistoryRequest = jsonFormat1(BiddingHistoryRequest)

  implicit val registerUserRequest = jsonFormat1(RegisterUserRequest)
  implicit val userFormat = jsonFormat2(User)
  implicit val enrollUserOnAuctionRequest = jsonFormat2(EnrollUserOnAuctionRequest)
  implicit val startBiddingRequest_v2Format = jsonFormat4(StartBiddingRequest)


}

package io.scalac.auction

import io.scalac.auction.AuctionAppJsonProtocol
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}

import scala.concurrent.{Await, Future}
import io.scalac.auction.AuctionServiceApp._
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Route
import spray.json._

object Routes {

  def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)

  val lotRoute: Route = pathPrefix("api" / "lot") {
    (pathEndOrSingleSlash & get) {
      complete(
        getLots_v2.map(_.toJson.prettyPrint).map(toHttpEntity)
      )
    }
  }

  val auctionRoute: Route = pathPrefix("api" / "auction") {
    (pathEndOrSingleSlash & get) {
      complete(
        getAuctions.map(_.toJson.prettyPrint).map(toHttpEntity)
      )
    } ~
      (path("create") & post) {
        entity(as[CreateAuctionRequest]) { createAuctionRequest =>
          val auctionId = createAuctionRequest.auctionId
          val lots = createAuctionRequest.lots
          createAuction(auctionId, lots)
          complete("auction created")
        }
      } ~
      (path("edit") & post) {
        entity(as[EditAuctionRequest]) { editAuctionRequest =>
          val auctionId = editAuctionRequest.auctionId
          val lotsToAdd = editAuctionRequest.lotsToAdd
          val lotIdsToRemove = editAuctionRequest.lotIdsToRemove
          editAuction(auctionId, lotsToAdd, lotIdsToRemove)
          complete("auction edited")
        }
      } ~
      (path("start") & post) {
        entity(as[StartAuctionRequest]) { startAuctionRequest =>
          val auctionId = startAuctionRequest.auctionId
          startAuction(auctionId)

          complete("auction started")
        }
      } ~
      (path("finish") & post) {
        entity(as[EndAuctionRequest]) { endAuctionRequest =>
          val auctionId = endAuctionRequest.auctionId
          endAuction(auctionId)

          complete("auction finished")
        }

      } ~
      path(Segment) { auctionId =>
        complete(
          getAuction(auctionId).map(_.toJson.prettyPrint).map(toHttpEntity)
        )
      }
  }

  val userRoute: Route = pathPrefix("api" / "user") {
    (pathEndOrSingleSlash & get) {
      complete(
        getUsers_v2.map(_.toJson.prettyPrint).map(toHttpEntity)
      )
    } ~
      post {
        path("register") {
          entity(as[RegisterUserRequest]) { registerUserRequest =>
            val userId = registerUserRequest.userId
            val maybeUser: Future[Option[User]] = getUser(userId)

            onSuccess(maybeUser) {
              case Some(_) =>
                complete(s"User ${userId} is already in the system")
              case None =>
                registerUser(userId)
                complete(s"Registered user ${userId}")
            }
          }
        } ~
        path("enroll") {
          entity(as[EnrollUserOnAuctionRequest]) { enrollUserOnAuctionRequest =>
            val userId = enrollUserOnAuctionRequest.userId
            val auctionId = enrollUserOnAuctionRequest.auctionId

            val maybeOnAuction: Future[Boolean] = {
              checkWhetherUserIsEnrolledOnAuction(userId, auctionId)
            }

            onSuccess(maybeOnAuction) {
              case true =>
                complete(s"User ${userId} is already registered on auction ${auctionId}")
              case false =>
                enrollUserToAuction(userId, auctionId)
                complete(s"Enrolled ${userId} to auction ${auctionId}")
            }
          }
        } ~
        path("play") {
          entity(as[StartBiddingRequest]) { startBiddingRequest =>
            val auctionId = startBiddingRequest.auctionId
            val lotId = startBiddingRequest.lotId
            val userId = startBiddingRequest.userId
            val maxBid = startBiddingRequest.maxBid
            val checkWhetherUserIsEnrolledOnAuctionFuture = checkWhetherUserIsEnrolledOnAuction(userId, auctionId)

            onSuccess(checkWhetherUserIsEnrolledOnAuctionFuture) {
              case true =>
                startPlaying(auctionId, lotId, userId, maxBid)
                complete(s"User $userId joined to Auction $auctionId to auction Lot $lotId")
              case false =>
                complete(s"User $userId is not registered on auction $auctionId")
            }
          }

        }
      }
  }


  val streamRoute: Route = pathPrefix("api" / "stream") {
    parameters(Symbol("auctionId").as[String], Symbol("lotId").as[String]) { (auctionId, lotId) =>
      complete(
        HttpEntity(
          ContentTypes.`text/html(UTF-8)`,
          StreamingService.html(auctionId, lotId)
        )
      )
    } ~
    path("web-socket" / Segment / Segment) { (auctionId, lotId) =>
      get {
        handleWebSocketMessages(StreamingService.flow(auctionId, lotId))
      }
    }
  }

  val route: Route = lotRoute ~ auctionRoute ~ userRoute ~ streamRoute

  def getRoute() = route

}

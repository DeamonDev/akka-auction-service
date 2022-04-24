import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.scalatest.wordspec.AnyWordSpec
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import org.scalatest.matchers.should.Matchers
import io.scalac.auction.{Auction, AuctionAppJsonProtocol, CreateAuctionRequest, CreateLotRequest, Lot}
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper

import scala.concurrent.duration._
import scala.language.postfixOps

class RoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with SprayJsonSupport with AuctionAppJsonProtocol {
  import io.scalac.auction.Routes._

  implicit val timeout = RouteTestTimeout(5.seconds)

  "An auction service" should {


    "create auction" in {
      val newAuctionRequest = CreateAuctionRequest("123", List(Lot("3"), Lot("8")))
      Post("/api/auction/create", newAuctionRequest) ~> route ~> check {
        responseAs[String] shouldEqual "auction created"
      }
    }

    "create auction with lots" in {
      val newAuctionRequest = CreateAuctionRequest("123", List(Lot("3"), Lot("8")))
      Post("/api/auction/create", newAuctionRequest) ~> route
      Get("/api/auction/123") ~> route ~> check {
        responseAs[Auction] shouldEqual Auction("123", List(Lot("3"), Lot("8")), "Closed")
      }
    }

    "add only permitted lots to new auction " in {
      val newAuctionRequest = CreateAuctionRequest("123", List(Lot("3"), Lot("8")))
      val yetAnotherNewAuctionRequest = CreateAuctionRequest("321", List(Lot("3"), Lot("9")))
      Post("/api/auction/create", newAuctionRequest) ~> route
      Post("/api/auction/create", yetAnotherNewAuctionRequest) ~> route
      Get("/api/auction/321") ~> route ~> check {
        responseAs[Auction] shouldEqual Auction("321", List(Lot("9")), "Closed")
      }
    }
  }

}

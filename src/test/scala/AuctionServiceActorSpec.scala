import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike
import io.scalac.auction._

class AuctionServiceActorSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import AuctionServiceActor._

  "Functional Auction Actor " must {

    "add exactly 2 lots after InitializeAuction with list containing 2 lots" in {
      val auctionServiceActor = spawn(AuctionServiceActor())

      val probe = createTestProbe[Int]()

      auctionServiceActor ! CreateAuction("1", List())
      auctionServiceActor ! GetNumberOfAuctions(probe.ref)
      val response = probe.receiveMessage()

      response should ===(1)
    }

    "get auction ..." in {
      val auctionServiceActor = spawn(AuctionServiceActor())

      val probe = createTestProbe[Auction]()

      auctionServiceActor ! CreateAuction("1", List(Lot("a"), Lot("b")))
      auctionServiceActor ! GetAuction("1", probe.ref)
      val response = probe.receiveMessage()

      response.auctionId should ===("1")
      response.lots should ===(List(Lot("a"), Lot("b")))
      response.state should ===("Closed")
    }


    "edit auctions ... " in {
      val auctionServiceActor = spawn(AuctionServiceActor())

      val probe = createTestProbe[Auction]()

      auctionServiceActor ! CreateAuction("1", List(Lot("a"), Lot("b")))
      auctionServiceActor ! EditAuction("1", List(Lot("c"), Lot("d")), List("a"))
      auctionServiceActor ! GetAuction("1", probe.ref)
      val response = probe.receiveMessage()

      response.auctionId should ===("1")
      response.lots should ===(List(Lot("b"), Lot("c"), Lot("d")))
      response.state should ===("Closed")
    }
  }
}

